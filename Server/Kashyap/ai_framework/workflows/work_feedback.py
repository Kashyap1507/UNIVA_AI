"""
work_feedback ("Check my work"): feedback on a student's own worked attempt.

    extract (flash)  page images → problems with the student's work
    evaluate (pro)   correctness, located issues, concepts, hints (+ solutions)
    verify (pro)     re-checks the evaluation and flags hints that give away answers
    format           enforces the academic-integrity rules in code

Integrity (MIGRATION_PLAN §4): the default mode is HINTS: locate the error,
name the concept, nudge, no final answer. FULL_SOLUTION is used only when
the student marks the work as practice. In HINTS mode `format` removes every
solution and answer field and replaces any hint that still contains the
answer, whatever the models returned, so graded work never leaves the server
with a solution attached.
"""

import logging
import re
from dataclasses import dataclass
from typing import Callable, Dict, Iterator, List, Optional, Tuple

from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)

TASK_EXTRACT = "work_feedback_extract"
TASK_EVALUATE = "work_feedback"
TASK_VERIFY = "work_feedback_verify"
STEPS = ("extract", "evaluate", "verify", "format")
MAX_PAGES = 6

HINTS = "HINTS"
FULL_SOLUTION = "FULL_SOLUTION"


# --- structured model outputs ------------------------------------------------------


class ExtractedProblem(BaseModel):
    number: str = Field(description="Problem label as written, e.g. '2(b)'; or a running number")
    prompt: Optional[str] = Field(None, description="The question text if visible")
    student_work: str = Field(description="Faithful transcription of the student's working (LaTeX for maths)")
    student_answer: Optional[str] = Field(None, description="The student's final answer if they gave one")


class Extraction(BaseModel):
    problems: List[ExtractedProblem] = Field(default_factory=list)
    is_blank_or_question_only: bool = Field(
        False, description="True if the pages contain questions but no attempt by the student"
    )


class EvaluatedItem(BaseModel):
    problem: str
    correct: bool
    location: Optional[str] = Field(None, description="Where the first error is, e.g. 'line 3, differentiating the product'")
    issue: Optional[str] = Field(None, description="What is wrong, without giving the fix")
    concept: Optional[str] = Field(None, description="The concept or rule involved")
    hint: Optional[str] = Field(None, description="A nudge that helps the student fix it without giving the answer")
    solution: Optional[str] = Field(None, description="Full worked solution (only used for practice work)")
    correct_answer: Optional[str] = Field(None, description="Short final answer, used to check hints don't leak it")


class Evaluation(BaseModel):
    items: List[EvaluatedItem] = Field(default_factory=list)
    summary: str = ""


class VerifiedItem(BaseModel):
    index: int
    evaluation_correct: bool = True
    leaks_answer: bool = False
    fixed_hint: Optional[str] = None
    note: Optional[str] = None


class Verification(BaseModel):
    items: List[VerifiedItem] = Field(default_factory=list)
    notes: Optional[str] = None


# --- prompts -------------------------------------------------------------------------

EXTRACT_PROMPT = """Transcribe a university student's handwritten or typed work from these page images.
For each problem the student attempted, give the problem label, the question text if it is visible,
a faithful transcription of the student's working (use LaTeX for mathematics, keep their mistakes),
and their final answer if they wrote one. Do not correct anything. If the pages only contain questions
and no attempt, set is_blank_or_question_only to true."""

EVALUATE_PROMPT = """You are an experienced university marker in {course} giving feedback to a {level} student.

Mode: {mode}
{mode_rules}

For each problem below: decide whether the student's work is correct. If it isn't, identify the
FIRST place it goes wrong (location), what is wrong (issue, without the fix), the concept or rule
involved, and a hint that nudges the student toward fixing it themselves. Always fill
correct_answer with the short final answer so hints can be checked. Be precise and use the
course's notation. Never write essays, reports or graded answers for the student.

Course reference material (may be empty):
{context}

Student's work:
{problems}
"""

HINTS_RULES = """HINTS mode (graded or unmarked work): do NOT include worked solutions or final answers
in hint, issue or location. Leave solution empty."""
FULL_RULES = """FULL_SOLUTION mode (the student marked this as practice): also give a clear,
step-by-step worked solution in `solution`."""

VERIFY_PROMPT = """You are checking another marker's feedback on a {level} student's work in {course}.
For each feedback item (by index): is the correctness judgement right? {leak_check}
If a hint gives the answer away, provide a better fixed_hint that nudges without revealing it.

Student's work:
{problems}

Feedback to check:
{feedback}
"""


def _level(degree_level: Optional[str]) -> str:
    return {"MASTER": "Master's", "BACHELOR": "Bachelor's"}.get((degree_level or "").upper(), "university")


def _format_problems(problems: List[ExtractedProblem]) -> str:
    parts = []
    for p in problems:
        parts.append(
            f"Problem {p.number}:\n"
            + (f"Question: {p.prompt}\n" if p.prompt else "")
            + f"Student's work:\n{p.student_work}\n"
            + (f"Student's answer: {p.student_answer}\n" if p.student_answer else "")
        )
    return "\n".join(parts)


# --- integrity enforcement -----------------------------------------------------------

_NORMALISE = re.compile(r"[\s$\\{}()\[\]]+")


def _normalise(text: Optional[str]) -> str:
    return _NORMALISE.sub("", (text or "").lower())


def hint_leaks_answer(hint: Optional[str], correct_answer: Optional[str]) -> bool:
    """True if the hint contains the final answer (ignoring spacing and LaTeX braces)."""
    answer = _normalise(correct_answer)
    if len(answer) < 2:  # single characters/digits match too much by accident
        return False
    return answer in _normalise(hint)


def generic_hint(item: EvaluatedItem) -> str:
    if item.concept:
        return f"Revisit how {item.concept} applies at this step and redo it carefully."
    return "Re-check this step carefully against the method from your course materials."


def format_feedback(
    evaluation: Evaluation,
    verification: Optional[Verification],
    mode: str,
) -> Dict:
    """Build the stored/returned feedback. In HINTS mode no solution or answer can survive."""
    checks = {v.index: v for v in (verification.items if verification else [])}
    items = []
    redacted = 0
    for index, item in enumerate(evaluation.items):
        check = checks.get(index)
        hint = item.hint
        if check and check.fixed_hint and (check.leaks_answer or not check.evaluation_correct):
            hint = check.fixed_hint
        out = {
            "problem": item.problem,
            "correct": item.correct,
            "location": item.location,
            "issue": item.issue,
            "concept": item.concept,
            "hint": hint,
            "verified": bool(check and check.evaluation_correct),
        }
        if mode == FULL_SOLUTION:
            out["solution"] = item.solution
            out["correct_answer"] = item.correct_answer
        else:
            # Code-level guarantee: no solution/answer fields, and no hint that restates the answer
            leaked = (check is not None and check.leaks_answer and not check.fixed_hint) or any(
                hint_leaks_answer(text, item.correct_answer) for text in (out["hint"], out["issue"], out["location"])
            )
            if leaked:
                redacted += 1
                out["hint"] = generic_hint(item)
                if hint_leaks_answer(out["issue"], item.correct_answer):
                    out["issue"] = "There is an error at this step."
                if hint_leaks_answer(out["location"], item.correct_answer):
                    out["location"] = None
        items.append(out)

    verifier_notes = verification.notes if verification else None
    if redacted:
        verifier_notes = ((verifier_notes or "") + f" Redacted {redacted} hint(s) that revealed an answer.").strip()
    return {
        "mode": mode,
        "items": items,
        "summary": evaluation.summary,
        "verified": verification is not None and all(i["verified"] for i in items),
        "verifier_notes": verifier_notes,
    }


# --- pipeline ------------------------------------------------------------------------


@dataclass
class ModelCalls:
    """Seams for tests; defaults call Gemini through the model router."""

    extract: Callable[[str, List[Tuple[str, str]]], Extraction]
    evaluate: Callable[[str], Evaluation]
    verify: Callable[[str], Verification]


def default_calls() -> ModelCalls:
    from app.ai_framework.utils.model_router import client_for

    return ModelCalls(
        extract=lambda prompt, images: client_for(TASK_EXTRACT).generate_structured(
            response_model=Extraction, prompt=prompt, images=images
        ),
        evaluate=lambda prompt: client_for(TASK_EVALUATE).generate_structured(response_model=Evaluation, prompt=prompt),
        verify=lambda prompt: client_for(TASK_VERIFY).generate_structured(response_model=Verification, prompt=prompt),
    )


def run(
    images: List[Tuple[str, str]],
    is_practice: bool,
    course_name: str,
    degree_level: Optional[str] = None,
    context: str = "",
    calls: Optional[ModelCalls] = None,
) -> Iterator[Dict]:
    """Yields {"step": name} as each stage starts, then {"result": feedback}."""
    calls = calls or default_calls()
    mode = FULL_SOLUTION if is_practice else HINTS
    level = _level(degree_level)

    yield {"step": "extract"}
    extraction = calls.extract(EXTRACT_PROMPT, images[:MAX_PAGES])
    if extraction.is_blank_or_question_only or not extraction.problems:
        yield {"step": "format"}
        yield {
            "result": {
                "mode": mode,
                "items": [],
                "summary": (
                    "I couldn't find a worked attempt on these pages. Univa AI gives feedback on your own work: "
                    "try the problems first, then scan your working."
                ),
                "verified": True,
                "verifier_notes": None,
            }
        }
        return

    problems_text = _format_problems(extraction.problems)

    yield {"step": "evaluate"}
    evaluation = calls.evaluate(
        EVALUATE_PROMPT.format(
            course=course_name,
            level=level,
            mode=mode,
            mode_rules=FULL_RULES if is_practice else HINTS_RULES,
            context=context or "(none)",
            problems=problems_text,
        )
    )

    yield {"step": "verify"}
    verification: Optional[Verification]
    try:
        feedback_json = Evaluation(items=evaluation.items, summary=evaluation.summary).model_dump_json(
            exclude={"items": {"__all__": {"solution"}}} if not is_practice else None
        )
        verification = calls.verify(
            VERIFY_PROMPT.format(
                course=course_name,
                level=level,
                leak_check="Does the hint, issue or location reveal the final answer or a full solution?"
                if not is_practice
                else "(practice mode: solutions are allowed; set leaks_answer false)",
                problems=problems_text,
                feedback=feedback_json,
            )
        )
    except Exception as e:
        # The deterministic checks in format_feedback still apply without the verifier
        logger.warning(f"work_feedback verify step failed: {e}")
        verification = None

    yield {"step": "format"}
    yield {"result": format_feedback(evaluation, verification, mode)}
