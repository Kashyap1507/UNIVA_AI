"""
tutor_chat: course-grounded tutoring with citations.

    retrieve (course chunks) → numbered sources → Gemini (streamed)
    → map [n] markers to the retrieved chunks → citations

The model is told to answer from the numbered sources and cite them as [n].
Markers that don't match a retrieved source are removed. When nothing in the
course materials is relevant, the answer is marked `grounded: false` and
starts with a clear notice, so the student knows it isn't from their course.
"""

import logging
import os
import re
from dataclasses import dataclass, field
from typing import Callable, Dict, Iterator, List, Optional

from app.services.retrieval_service import RetrievedChunk

logger = logging.getLogger(__name__)

TASK = "tutor_chat"
RETRIEVE_K = 8
HISTORY_TURNS = 8
MAX_SOURCE_CHARS = 1500

NOT_COVERED_NOTICE = (
    "**Not covered by your course materials.** I couldn't find this in the files "
    "uploaded to this course, so the answer below is general knowledge. Check it "
    "against your lecture notes or ask your instructor."
)

CITATION_RE = re.compile(r"\[(\d{1,2})\]")


def min_relevance() -> float:
    """Minimum similarity (1 - cosine distance) for a chunk to count as relevant."""
    return float(os.getenv("TUTOR_MIN_SCORE", "0.35"))


SYSTEM_RULES = """You are Univa AI, a tutor for a {level} student in the course "{course}".

How to answer:
- Teach at university level: precise terminology, correct notation, and reasoning
  the student can follow. Prefer explaining *why* over just stating results.
- Base your answer on the numbered course sources below. After each claim that
  comes from a source, cite it like [1] or [2][3]. Only cite sources that exist.
- {coverage_rule}
- Write mathematics in LaTeX: $...$ inline and $$...$$ for display equations.
  Use fenced code blocks with a language tag for code.
- Use Markdown headings and lists only when they help. Keep answers focused.

Academic integrity (always):
- Never write graded assignments, essays, lab reports or take-home exam answers
  for the student, even if asked directly or indirectly. Offer to explain the
  concepts, outline an approach, or review the student's own attempt instead.
- For problem sets, guide with hints and worked examples of *similar* problems
  unless the student says the material is for practice.
"""

COVERED_RULE = (
    "If the sources only partly answer the question, say which part they cover and "
    "clearly label anything you add from general knowledge."
)
NOT_COVERED_RULE = (
    "None of the course materials are relevant to this question. A notice saying so "
    "is shown to the student automatically, so don't repeat it. Give a careful "
    "general answer without citations and suggest what the student could check."
)


@dataclass
class Source:
    number: int
    chunk: RetrievedChunk

    def to_citation(self) -> Dict:
        return {
            "n": self.number,
            "chunk_id": self.chunk.chunk_id,
            "material_id": self.chunk.material_id,
            "title": self.chunk.material_title,
            "page": self.chunk.page,
            "page_end": self.chunk.page_end,
        }


@dataclass
class TutorAnswer:
    content: str
    citations: List[Dict] = field(default_factory=list)
    grounded: bool = True


def select_sources(chunks: List[RetrievedChunk]) -> List[Source]:
    """Keep chunks above the relevance threshold, numbered from 1."""
    threshold = min_relevance()
    relevant = [c for c in chunks if c.distance is None or (1 - c.distance) >= threshold]
    return [Source(number=i + 1, chunk=c) for i, c in enumerate(relevant)]


def _format_sources(sources: List[Source]) -> str:
    if not sources:
        return "(no relevant course material was found)"
    parts = []
    for s in sources:
        where = ""
        if s.chunk.page:
            where = f", p. {s.chunk.page}" + (f"-{s.chunk.page_end}" if s.chunk.page_end and s.chunk.page_end != s.chunk.page else "")
        parts.append(f"[{s.number}] {s.chunk.material_title}{where}\n{s.chunk.text[:MAX_SOURCE_CHARS]}")
    return "\n\n".join(parts)


def _format_history(history: List[Dict]) -> str:
    lines = []
    for message in history[-HISTORY_TURNS:]:
        role = "Student" if message.get("role") == "user" else "Tutor"
        lines.append(f"{role}: {message.get('content', '')[:2000]}")
    return "\n".join(lines) if lines else "(this is the first question)"


def build_prompt(
    question: str,
    sources: List[Source],
    history: List[Dict],
    course_name: str,
    degree_level: Optional[str],
) -> str:
    level = {"MASTER": "Master's", "BACHELOR": "Bachelor's"}.get((degree_level or "").upper(), "university")
    rules = SYSTEM_RULES.format(
        level=level,
        course=course_name,
        coverage_rule=COVERED_RULE if sources else NOT_COVERED_RULE,
    )
    return (
        f"{rules}\n=== COURSE SOURCES ===\n{_format_sources(sources)}\n\n"
        f"=== CONVERSATION SO FAR ===\n{_format_history(history)}\n\n"
        f"=== STUDENT'S QUESTION ===\n{question}\n\nTutor:"
    )


def finalize(raw_answer: str, sources: List[Source]) -> TutorAnswer:
    """Drop citation markers that don't match a source; list the cited sources in order."""
    valid = {s.number: s for s in sources}
    cited: List[int] = []

    def keep(match: re.Match) -> str:
        n = int(match.group(1))
        if n in valid:
            if n not in cited:
                cited.append(n)
            return match.group(0)
        return ""

    content = CITATION_RE.sub(keep, raw_answer or "").strip()
    grounded = bool(sources)
    if not grounded and not content.startswith("**Not covered"):
        content = f"{NOT_COVERED_NOTICE}\n\n{content}"
    return TutorAnswer(
        content=content,
        citations=[valid[n].to_citation() for n in cited],
        grounded=grounded,
    )


def _default_stream(prompt: str) -> Iterator[str]:
    from app.ai_framework.utils.model_router import client_for

    return client_for(TASK).generate_streaming(prompt)


def run(
    question: str,
    chunks: List[RetrievedChunk],
    history: List[Dict],
    course_name: str,
    degree_level: Optional[str] = None,
    stream: Optional[Callable[[str], Iterator[str]]] = None,
) -> Iterator[Dict]:
    """
    Yields events: {"type": "sources", "grounded": bool},
    {"type": "delta", "text": ...} for each streamed piece, then
    {"type": "final", "answer": TutorAnswer}.
    """
    stream = stream or _default_stream
    sources = select_sources(chunks)
    yield {"type": "sources", "grounded": bool(sources), "count": len(sources)}
    if not sources:
        # Show the notice straight away while the model is still thinking
        yield {"type": "delta", "text": NOT_COVERED_NOTICE + "\n\n"}

    prompt = build_prompt(question, sources, history, course_name, degree_level)
    pieces: List[str] = []
    for piece in stream(prompt):
        if piece:
            pieces.append(piece)
            yield {"type": "delta", "text": piece}
    yield {"type": "final", "answer": finalize("".join(pieces), sources)}
