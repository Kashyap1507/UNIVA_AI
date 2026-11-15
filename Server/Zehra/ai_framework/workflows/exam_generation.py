"""
exam_generation: course excerpts → practice sets, lecture quizzes and mock exams,
plus grading of the answers.

Questions are generated from numbered excerpts, validated here (bad MCQs,
missing answers and unknown types are dropped) and stored with their answers.
MCQ and numeric answers are marked in code; written answers (SHORT, PROOF,
CODE) are marked by one model call against the model answer and key points.
"""

import math
import re
from typing import Callable, Dict, List, Literal, Optional, Sequence

from pydantic import BaseModel, Field

from app.services.retrieval_service import RetrievedChunk

TASK = "exam_generation"
TASK_GRADING = "exam_grading"

KINDS = ("PRACTICE", "LECTURE_QUIZ", "MOCK_EXAM")
TYPES = ("MCQ", "NUMERIC", "SHORT", "PROOF", "CODE")
WRITTEN = ("SHORT", "PROOF", "CODE")
DEFAULT_POINTS = {"MCQ": 1, "NUMERIC": 2, "SHORT": 3, "PROOF": 5, "CODE": 4}
MINUTES_PER_TYPE = {"MCQ": 1.5, "NUMERIC": 3, "SHORT": 5, "PROOF": 8, "CODE": 8}
COUNT_LIMITS = {"PRACTICE": (3, 25, 10), "LECTURE_QUIZ": (3, 15, 5), "MOCK_EXAM": (5, 40, 20)}
MAX_EXCERPT_CHARS = 2500


# --- model output ----------------------------------------------------------------------


class QuestionOut(BaseModel):
    type: Literal["MCQ", "NUMERIC", "SHORT", "PROOF", "CODE"]
    prompt: str = Field(description="The question. Maths in LaTeX with $...$; code in fenced blocks")
    options: List[str] = Field(default_factory=list, description="MCQ only: 3-5 options, no letters in front")
    correct_option: Optional[int] = Field(None, description="MCQ only: 0-based index of the correct option")
    numeric_answer: Optional[float] = Field(None, description="NUMERIC only: the exact value")
    tolerance: Optional[float] = Field(None, description="NUMERIC only: accepted absolute error")
    unit: Optional[str] = Field(None, description="NUMERIC only: unit of the answer, if any")
    model_answer: Optional[str] = Field(None, description="SHORT/PROOF/CODE: a complete model answer")
    key_points: List[str] = Field(default_factory=list, description="SHORT/PROOF/CODE: what a full-marks answer must contain")
    explanation: str = Field("", description="Why the answer is right, shown after submission")
    concept: Optional[str] = Field(None, description="Course concept tested, copied exactly from the list")
    points: Optional[int] = Field(None, description="Marks for this question")
    source: Optional[int] = Field(None, description="Number of the excerpt the question is based on")


class ExamOut(BaseModel):
    title: str = ""
    questions: List[QuestionOut] = Field(default_factory=list)


class GradedOut(BaseModel):
    index: int
    score: float = Field(description="Marks awarded, from 0 to the question's marks")
    feedback: str = Field("", description="One or two sentences: what was right and what was missing")


class GradingOut(BaseModel):
    items: List[GradedOut] = Field(default_factory=list)


# --- prompts ------------------------------------------------------------------------------

KIND_BRIEF = {
    "PRACTICE": "a practice set. Mix difficulties, starting easier, so the student can check understanding.",
    "LECTURE_QUIZ": "a short quiz on this lecture material. Focus on the main ideas.",
    "MOCK_EXAM": (
        "a mock final exam in the style of a university exam. Cover the whole scope evenly, "
        "include several multi-step problems, and order questions from shorter to longer."
    ),
}

GENERATE_TEMPLATE = """You write assessment questions for a {degree} student taking {course_name}.

Write {count} questions for {brief}

Question types:
- MCQ: 3-5 plausible options, exactly one correct; set correct_option (0-based).
- NUMERIC: a calculation with one numeric answer; set numeric_answer, a sensible
  absolute tolerance and the unit if any.
- SHORT: a short written answer (definition, explanation, comparison); give a
  model_answer and 2-5 key_points.
- PROOF: a short proof or derivation; give a model_answer and the key steps as key_points.
- CODE: write or fix a short piece of code; give a model_answer and key_points.
Use the types that suit the material: {type_hint}

Rules:
- Only test content stated in the excerpts. Every question needs a clear, correct answer.
- Write maths in LaTeX with $...$ and code in fenced blocks.
- Set "explanation" to a short worked explanation of the answer.
- Set "concept" to the course concept tested, copied exactly from the list, or leave it empty.
- Set "source" to the excerpt number the question comes from.
- Do not repeat questions from earlier sets (listed below).

Course concepts: {concepts}

Questions in earlier sets (do not repeat):
{existing}

=== EXCERPTS ===
{excerpts}
"""

GRADE_TEMPLATE = """You are marking a {degree} student's answers in {course_name}.

For each item, compare the student's answer with the model answer and key points.
Award marks from 0 to the maximum, allowing half marks. Give credit for correct
reasoning expressed differently from the model answer; do not give credit for
statements that are vague or wrong. Keep feedback to one or two sentences and
address the student as "you".

{items}
"""


def degree_label(level: Optional[str]) -> str:
    level = (level or "").upper()
    return "Master's" if level == "MASTER" else "Bachelor's" if level == "BACHELOR" else "university"


def build_prompt(
    kind: str,
    chunks: Sequence[RetrievedChunk],
    count: int,
    course_name: str,
    degree_level: Optional[str],
    concept_names: Sequence[str],
    existing_prompts: Sequence[str],
) -> str:
    excerpts = "\n\n".join(
        f"[{i}] {c.material_title}{f' (p. {c.page})' if c.page else ''}\n{c.text[:MAX_EXCERPT_CHARS]}"
        for i, c in enumerate(chunks, start=1)
    )
    type_hint = (
        "about half MCQ, the rest NUMERIC, SHORT and (if the course has proofs or code) PROOF or CODE."
        if kind == "MOCK_EXAM"
        else "mostly MCQ and NUMERIC, with a few SHORT."
    )
    return GENERATE_TEMPLATE.format(
        degree=degree_label(degree_level),
        course_name=course_name,
        count=count,
        brief=KIND_BRIEF[kind],
        type_hint=type_hint,
        concepts=", ".join(concept_names[:80]) or "(none yet)",
        existing="\n".join(f"- {p[:200]}" for p in existing_prompts[:60]) or "(none)",
        excerpts=excerpts,
    )


# --- validation -------------------------------------------------------------------------------


def _norm(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", (text or "").lower()).strip()


def to_questions(
    exam: ExamOut,
    chunks: Sequence[RetrievedChunk],
    count: int,
    concept_ids: Dict[str, str],
    existing_prompts: Sequence[str] = (),
) -> List[Dict]:
    """Keep only well-formed questions; number them q1..qn."""
    by_name = {_norm(name): cid for name, cid in concept_ids.items()}
    names = {cid: name for name, cid in concept_ids.items()}
    seen = {_norm(p) for p in existing_prompts}
    out: List[Dict] = []
    for q in exam.questions:
        prompt = (q.prompt or "").strip()
        key = _norm(prompt)
        if not prompt or not key or key in seen:
            continue
        item: Dict = {"type": q.type, "prompt": prompt[:4000], "explanation": (q.explanation or "").strip()[:3000]}
        if q.type == "MCQ":
            options = [o.strip()[:500] for o in q.options if o and o.strip()]
            if not 2 <= len(options) <= 6 or q.correct_option is None or not 0 <= q.correct_option < len(options):
                continue
            if len({_norm(o) for o in options}) != len(options):
                continue
            item.update(options=options, correct_option=q.correct_option)
        elif q.type == "NUMERIC":
            if q.numeric_answer is None or not math.isfinite(q.numeric_answer):
                continue
            tol = q.tolerance if q.tolerance is not None and math.isfinite(q.tolerance) and q.tolerance > 0 else None
            item.update(numeric_answer=q.numeric_answer, tolerance=tol, unit=(q.unit or "").strip()[:30] or None)
        else:
            if not (q.model_answer or "").strip():
                continue
            item.update(model_answer=q.model_answer.strip()[:4000], key_points=[k.strip()[:300] for k in q.key_points if k and k.strip()][:8])
        points = q.points if q.points and 1 <= q.points <= 20 else DEFAULT_POINTS[q.type]
        concept_id = by_name.get(_norm(q.concept or ""))
        source = None
        if q.source and 1 <= q.source <= len(chunks):
            c = chunks[q.source - 1]
            source = {"chunk_id": c.chunk_id, "material_id": c.material_id, "material_title": c.material_title, "page": c.page}
        item.update(points=points, concept_id=concept_id, concept_name=names.get(concept_id) or (q.concept or "").strip()[:120] or None, source=source)
        seen.add(key)
        out.append(item)
        if len(out) >= count:
            break
    for i, item in enumerate(out, start=1):
        item["id"] = f"q{i}"
    return out


def time_limit_minutes(questions: Sequence[Dict]) -> int:
    """Suggested mock exam length: per-type minutes, rounded up to 5."""
    total = sum(MINUTES_PER_TYPE.get(q["type"], 3) for q in questions)
    return max(10, int(math.ceil(total / 5.0) * 5))


def public_question(q: Dict) -> Dict:
    """What the student sees before submitting: never the answer or explanation."""
    return {k: q.get(k) for k in ("id", "type", "prompt", "options", "unit", "points", "concept_name")}


# --- marking ----------------------------------------------------------------------------------

_NUMBER = r"[-+]?(?:\d+\.?\d*|\.\d+)(?:e[-+]?\d+)?"
_NUMERIC_RE = re.compile(rf"^({_NUMBER})(?:/({_NUMBER}))?")


def parse_number(text) -> Optional[float]:
    """'3.5', '-2e-3', '1,200', '7/2', '−4', '3.2 m/s' → float; anything else → None."""
    if isinstance(text, bool) or text is None:
        return None
    if isinstance(text, (int, float)):
        return float(text) if math.isfinite(text) else None
    s = str(text).strip().lower().replace("−", "-").replace(",", "").replace(" ", "").replace("×10^", "e").replace("*10^", "e")
    m = _NUMERIC_RE.match(s)
    if not m:
        return None
    try:
        value = float(m.group(1))
        if m.group(2) is not None:
            den = float(m.group(2))
            if den == 0:
                return None
            value /= den
    except ValueError:
        return None
    return value if math.isfinite(value) else None


def numeric_correct(response, answer: float, tolerance: Optional[float]) -> bool:
    value = parse_number(response)
    if value is None:
        return False
    tol = tolerance if tolerance else max(1e-9, abs(answer) * 0.01)
    return abs(value - answer) <= tol + 1e-12


def mark_objective(q: Dict, response) -> Dict:
    """MCQ and NUMERIC: marked in code."""
    points = q["points"]
    if q["type"] == "MCQ":
        chosen = response if isinstance(response, int) and not isinstance(response, bool) else None
        correct = chosen is not None and chosen == q["correct_option"]
    else:
        correct = numeric_correct(response, q["numeric_answer"], q.get("tolerance"))
    answered = response not in (None, "")
    return {
        "status": "GRADED",
        "correct": correct,
        "points_awarded": points if correct else 0,
        "feedback": None if correct else ("No answer." if not answered else None),
    }


def build_grading_prompt(items: Sequence[Dict], course_name: str, degree_level: Optional[str]) -> str:
    blocks = []
    for i, (q, response) in enumerate(items):
        points = "\n".join(f"  - {k}" for k in q.get("key_points") or [])
        blocks.append(
            f"--- Item {i} (max {q['points']} marks, {q['type']}) ---\n"
            f"Question: {q['prompt']}\nModel answer: {q.get('model_answer', '')}\n"
            f"Key points:\n{points or '  (none)'}\nStudent answer: {str(response)[:6000]}"
        )
    return GRADE_TEMPLATE.format(degree=degree_label(degree_level), course_name=course_name, items="\n\n".join(blocks))


def default_generate(prompt: str) -> ExamOut:
    from app.ai_framework.utils.model_router import client_for

    return client_for(TASK).generate_structured(response_model=ExamOut, prompt=prompt)


def default_grade(prompt: str) -> GradingOut:
    from app.ai_framework.utils.model_router import client_for

    return client_for(TASK_GRADING).generate_structured(response_model=GradingOut, prompt=prompt)


def grade_written(
    items: Sequence[tuple],
    course_name: str,
    degree_level: Optional[str],
    grade: Optional[Callable[[str], GradingOut]] = None,
) -> List[Dict]:
    """Mark written answers in one call. Raises if the model call fails."""
    results: List[Optional[Dict]] = [None] * len(items)
    to_model = []
    for i, (q, response) in enumerate(items):
        if not str(response or "").strip():
            results[i] = {"status": "GRADED", "correct": False, "points_awarded": 0, "feedback": "No answer."}
        else:
            to_model.append(i)
    if to_model:
        graded = (grade or default_grade)(build_grading_prompt([items[i] for i in to_model], course_name, degree_level))
        by_index = {g.index: g for g in graded.items}
        for local, i in enumerate(to_model):
            q = items[i][0]
            g = by_index.get(local)
            if g is None:
                results[i] = {"status": "NOT_GRADED", "correct": None, "points_awarded": 0, "feedback": None}
                continue
            score = max(0.0, min(float(q["points"]), round(float(g.score) * 2) / 2))
            results[i] = {
                "status": "GRADED",
                "correct": score >= q["points"] / 2,
                "points_awarded": score,
                "feedback": (g.feedback or "").strip()[:600] or None,
            }
    return results
