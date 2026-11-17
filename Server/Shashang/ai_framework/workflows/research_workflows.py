"""
Research workflows: paper analysis, literature synthesis, writing feedback and
lecture summaries. All use flash through the model router.

Writing feedback comments on the student's text and never rewrites it: the
response has no field for rewritten text, quotes are capped at a sentence and
suggestions at a short instruction, and anything longer is trimmed in code.
"""

import re
from typing import Callable, Dict, List, Optional, Sequence

from pydantic import BaseModel, Field

TASK_PAPER = "paper_analysis"
TASK_SYNTHESIS = "literature_synthesis"
TASK_WRITING = "writing_feedback"
TASK_LECTURE = "lecture_summary"

MAX_PAPER_CHARS = 120_000
MAX_QUOTE_CHARS = 200
MAX_SUGGESTION_CHARS = 280
WRITING_KINDS = ("ABSTRACT", "INTRODUCTION", "LIT_REVIEW", "METHODS", "RESULTS", "DISCUSSION", "ESSAY", "OTHER")


# --- models ------------------------------------------------------------------------------------------


class PaperDetails(BaseModel):
    title: Optional[str] = None
    authors: List[str] = Field(default_factory=list, description="Full names in order")
    year: Optional[int] = None
    venue: Optional[str] = Field(None, description="Journal or conference name")
    doi: Optional[str] = None
    type: Optional[str] = Field(None, description="ARTICLE, CONFERENCE, BOOK, THESIS, PREPRINT or OTHER")


class PaperSummary(BaseModel):
    tldr: str = Field("", description="Two sentences: what was done and what was found")
    research_question: str = ""
    methods: str = Field("", description="Design, techniques and analysis")
    data: str = Field("", description="Data, participants or materials used")
    findings: List[str] = Field(default_factory=list)
    limitations: List[str] = Field(default_factory=list)
    contributions: str = ""
    keywords: List[str] = Field(default_factory=list)


class PaperAnalysis(BaseModel):
    details: PaperDetails = Field(default_factory=PaperDetails)
    summary: PaperSummary = Field(default_factory=PaperSummary)


class Synthesis(BaseModel):
    themes: List[str] = Field(default_factory=list)
    agreements: List[str] = Field(default_factory=list)
    disagreements: List[str] = Field(default_factory=list)
    gaps: List[str] = Field(default_factory=list)
    paragraph: str = Field("", description="A short synthesis paragraph citing papers as [1], [2]")


class WritingIssue(BaseModel):
    quote: str = Field("", description="The exact phrase from the student's text (one sentence at most)")
    problem: str = ""
    suggestion: str = Field("", description="What to do, as an instruction; never rewritten text")
    category: str = Field("CLARITY", description="STRUCTURE, ARGUMENT, EVIDENCE, CLARITY, STYLE, CITATION or GRAMMAR")


class WritingFeedback(BaseModel):
    overall: str = ""
    strengths: List[str] = Field(default_factory=list)
    issues: List[WritingIssue] = Field(default_factory=list)
    structure_notes: List[str] = Field(default_factory=list)


class LectureSummary(BaseModel):
    overview: str = ""
    key_points: List[str] = Field(default_factory=list)
    terms: List[Dict[str, str]] = Field(default_factory=list, description="[{term, definition}]")
    exam_questions: List[str] = Field(default_factory=list)


# --- prompts ---------------------------------------------------------------------------------------------

PAPER_PROMPT = """You are helping a Master's student read a research paper.

From the paper text below:
1. details: the title, authors (full names, in order), year, venue (journal or
   conference), DOI and publication type, exactly as stated in the paper. Leave a
   field empty if the text doesn't state it; never guess.
2. summary: a faithful, concise summary for a literature review: a two-sentence
   TL;DR, the research question, methods, data, main findings, limitations
   (including ones the authors state), contributions and 3-6 keywords.
Use the paper's own terminology. Do not add claims that are not in the text.

=== PAPER ===
{text}
"""

SYNTHESIS_PROMPT = """You are helping a Master's student write a literature review{focus}.

Below are structured summaries of {n} papers, numbered [1]..[{n}]. Identify the
common themes, where the papers agree and disagree, and the gaps they leave open.
Then write one synthesis paragraph (120-200 words) that compares them and cites
them as [1], [2], etc. Only use what the summaries say.

{papers}
"""

WRITING_PROMPT = """You are an academic writing tutor giving feedback on a {level} student's {kind}.

Give feedback, not a rewrite:
- Never rewrite sentences or paragraphs and never write new text for the student.
- For each issue, quote the exact phrase (one sentence at most), say what the
  problem is, and give the suggestion as an instruction ("Define X before using it",
  "Add evidence for this claim", "Split this sentence").
- Cover structure, argument, use of evidence and citations, clarity and academic
  style. Point out 2-4 genuine strengths.
- At most 10 issues, most important first.

=== TEXT ===
{text}
"""

LECTURE_PROMPT = """Summarise this lecture material for a {level} student in {course}.

Give a short overview (2-3 sentences), the key points in teaching order, the
important terms with one-line definitions, and 3-5 questions an exam might ask
about it. Only use what the material says; write maths in LaTeX with $...$.

=== MATERIAL: {title} ===
{text}
"""


def level_label(level: Optional[str]) -> str:
    level = (level or "").upper()
    return "Master's" if level == "MASTER" else "Bachelor's" if level == "BACHELOR" else "university"


# --- default model calls -------------------------------------------------------------------------------------


def _client(task: str):
    from app.ai_framework.utils.model_router import client_for

    return client_for(task)


def default_analyse(prompt: str) -> PaperAnalysis:
    return _client(TASK_PAPER).generate_structured(response_model=PaperAnalysis, prompt=prompt)


def default_synthesise(prompt: str) -> Synthesis:
    return _client(TASK_SYNTHESIS).generate_structured(response_model=Synthesis, prompt=prompt)


def default_writing(prompt: str) -> WritingFeedback:
    return _client(TASK_WRITING).generate_structured(response_model=WritingFeedback, prompt=prompt)


def default_lecture(prompt: str) -> LectureSummary:
    return _client(TASK_LECTURE).generate_structured(response_model=LectureSummary, prompt=prompt)


# --- workflows ------------------------------------------------------------------------------------------------


def analyse_paper(text: str, analyse: Optional[Callable] = None) -> PaperAnalysis:
    return (analyse or default_analyse)(PAPER_PROMPT.format(text=text[:MAX_PAPER_CHARS]))


REVIEW_COLUMNS = ("research_question", "methods", "data", "findings", "limitations")


def in_text_citation(p: Dict) -> str:
    """APA-style in-text citation: 'Vaswani et al. (2017)', 'LeCun & Bengio (2015)', or the title."""
    from app.services.citation_service import split_name

    lasts = [split_name(a)[0] for a in p.get("authors") or [] if a and a.strip()]
    year = p.get("year") or "n.d."
    if not lasts:
        return p.get("title") or "Untitled"
    if len(lasts) == 1:
        return f"{lasts[0]} ({year})"
    if len(lasts) == 2:
        return f"{lasts[0]} & {lasts[1]} ({year})"
    return f"{lasts[0]} et al. ({year})"


def review_table(papers: Sequence[Dict]) -> List[Dict]:
    """One row per paper from its stored summary (no model call)."""
    rows = []
    for i, p in enumerate(papers, start=1):
        s = p.get("summary") or {}
        cite = in_text_citation(p)
        rows.append(
            {
                "number": i,
                "paper_id": p.get("id"),
                "citation": cite,
                "title": p.get("title"),
                "research_question": s.get("research_question") or "",
                "methods": s.get("methods") or "",
                "data": s.get("data") or "",
                "findings": "; ".join(s.get("findings") or []),
                "limitations": "; ".join(s.get("limitations") or []),
            }
        )
    return rows


def synthesise(papers: Sequence[Dict], focus: Optional[str], synthesise_fn: Optional[Callable] = None) -> Synthesis:
    blocks = []
    for i, p in enumerate(papers, start=1):
        s = p.get("summary") or {}
        blocks.append(
            f"[{i}] {p.get('title')} ({p.get('year') or 'n.d.'})\n"
            f"Question: {s.get('research_question', '')}\nMethods: {s.get('methods', '')}\nData: {s.get('data', '')}\n"
            f"Findings: {'; '.join(s.get('findings') or [])}\nLimitations: {'; '.join(s.get('limitations') or [])}"
        )
    prompt = SYNTHESIS_PROMPT.format(focus=f" on {focus}" if focus else "", n=len(papers), papers="\n\n".join(blocks))
    return (synthesise_fn or default_synthesise)(prompt)


def _trim(text: str, limit: int) -> str:
    text = (text or "").strip()
    if len(text) <= limit:
        return text
    cut = text[:limit].rsplit(" ", 1)[0]
    return cut + "…"


def guard_feedback(feedback: WritingFeedback, source: str) -> Dict:
    """Keep feedback as feedback: short quotes that exist in the text, short instructions, no rewrites."""
    norm_source = re.sub(r"\s+", " ", source).lower()
    issues = []
    for issue in feedback.issues[:10]:
        quote = _trim(issue.quote, MAX_QUOTE_CHARS)
        if quote and re.sub(r"\s+", " ", quote.rstrip("…")).lower() not in norm_source:
            quote = ""  # the model invented or rewrote the quote
        suggestion = _trim(issue.suggestion, MAX_SUGGESTION_CHARS)
        issues.append({"quote": quote, "problem": _trim(issue.problem, 400), "suggestion": suggestion, "category": (issue.category or "CLARITY").upper()[:20]})
    return {
        "overall": _trim(feedback.overall, 800),
        "strengths": [_trim(s, 300) for s in feedback.strengths[:5]],
        "issues": issues,
        "structure_notes": [_trim(s, 300) for s in feedback.structure_notes[:6]],
    }


def writing_feedback(text: str, kind: str, level: Optional[str], writing_fn: Optional[Callable] = None) -> Dict:
    label = {"LIT_REVIEW": "literature review", "OTHER": "writing"}.get(kind, kind.lower())
    feedback = (writing_fn or default_writing)(WRITING_PROMPT.format(level=level_label(level), kind=label, text=text))
    return guard_feedback(feedback, text)


def lecture_summary(title: str, text: str, course: str, level: Optional[str], lecture_fn: Optional[Callable] = None) -> Dict:
    result = (lecture_fn or default_lecture)(LECTURE_PROMPT.format(level=level_label(level), course=course, title=title, text=text[:MAX_PAPER_CHARS]))
    terms = [{"term": t.get("term", "").strip(), "definition": t.get("definition", "").strip()} for t in result.terms if isinstance(t, dict) and t.get("term")]
    return {"overview": result.overview.strip(), "key_points": [k.strip() for k in result.key_points if k.strip()], "terms": terms, "exam_questions": [q.strip() for q in result.exam_questions if q.strip()]}
