"""
flashcard_generation: course material excerpts → question/answer cards.

The caller picks the excerpts (one concept via retrieval, or a spread across the
course). Each card cites the excerpt it came from, so the app can show
"from Lecture 3 slides, p. 4". Cards that repeat an existing front are dropped.
"""

import re
from typing import Callable, Dict, List, Optional, Sequence

from pydantic import BaseModel, Field

from app.services.retrieval_service import RetrievedChunk

TASK = "flashcard_generation"
MAX_CARDS = 30
MAX_FRONT = 300
MAX_BACK = 1000
MAX_EXCERPT_CHARS = 2500


class CardOut(BaseModel):
    front: str = Field(description="A question or prompt that tests one idea")
    back: str = Field(description="The answer, at most three sentences")
    source: Optional[int] = Field(None, description="Number of the excerpt the card is based on")
    concept: Optional[str] = Field(None, description="Name of the matching course concept, if any, copied exactly")


class CardSet(BaseModel):
    cards: List[CardOut] = Field(default_factory=list)


PROMPT_TEMPLATE = """You write flashcards for a {degree} student studying {course_name}.

Write up to {count} flashcards from the numbered course excerpts below.{focus}

Rules:
- One idea per card. The front is a question or cue; the back answers it in at
  most three sentences.
- Test understanding, not trivia: definitions, why/how questions, conditions and
  assumptions, comparisons, formulas and when to use them, short applications.
- Use precise university-level terminology. Write maths in LaTeX with $...$.
- Only use facts stated in the excerpts. Never ask about page numbers, slide
  layout or the documents themselves.
- Set "source" to the number of the excerpt each card comes from.
- Set "concept" to the course concept the card belongs to, copied exactly from
  the list, or leave it empty.
- Do not repeat or rephrase any of the existing cards.

Course concepts: {concepts}

Existing cards (do not repeat):
{existing}

=== EXCERPTS ===
{excerpts}
"""


def normalise(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", (text or "").lower()).strip()


def build_prompt(
    chunks: Sequence[RetrievedChunk],
    count: int,
    course_name: str,
    degree_level: Optional[str],
    concept_names: Sequence[str],
    existing_fronts: Sequence[str],
    focus_concept: Optional[str] = None,
) -> str:
    level = (degree_level or "").upper()
    degree = "Master's" if level == "MASTER" else "Bachelor's" if level == "BACHELOR" else "university"
    excerpts = "\n\n".join(
        f"[{i}] {c.material_title}{f' (p. {c.page})' if c.page else ''}\n{c.text[:MAX_EXCERPT_CHARS]}"
        for i, c in enumerate(chunks, start=1)
    )
    return PROMPT_TEMPLATE.format(
        degree=degree,
        course_name=course_name,
        count=count,
        focus=f"\nFocus on the concept: {focus_concept}." if focus_concept else "",
        concepts=", ".join(concept_names[:80]) or "(none yet)",
        existing="\n".join(f"- {f}" for f in existing_fronts[:150]) or "(none)",
        excerpts=excerpts,
    )


def to_cards(
    card_set: CardSet,
    chunks: Sequence[RetrievedChunk],
    count: int,
    concept_ids: Dict[str, str],
    existing_fronts: Sequence[str],
    default_concept_id: Optional[str] = None,
) -> List[Dict]:
    """Validate model output: trim, drop empties and duplicates, map sources and concepts."""
    seen = {normalise(f) for f in existing_fronts}
    by_name = {normalise(name): cid for name, cid in concept_ids.items()}
    cards = []
    for card in card_set.cards:
        front, back = (card.front or "").strip(), (card.back or "").strip()
        key = normalise(front)
        if not front or not back or not key or key in seen:
            continue
        seen.add(key)
        source = None
        if card.source and 1 <= card.source <= len(chunks):
            chunk = chunks[card.source - 1]
            source = {
                "chunk_id": chunk.chunk_id,
                "material_id": chunk.material_id,
                "material_title": chunk.material_title,
                "page": chunk.page,
            }
        cards.append(
            {
                "front": front[:MAX_FRONT],
                "back": back[:MAX_BACK],
                "source": source,
                "concept_id": by_name.get(normalise(card.concept or "")) or default_concept_id,
            }
        )
        if len(cards) >= count:
            break
    return cards


def default_generate(prompt: str) -> CardSet:
    from app.ai_framework.utils.model_router import client_for

    return client_for(TASK).generate_structured(response_model=CardSet, prompt=prompt)


def run(
    chunks: Sequence[RetrievedChunk],
    count: int,
    course_name: str,
    degree_level: Optional[str],
    concept_ids: Dict[str, str],
    existing_fronts: Sequence[str],
    focus_concept: Optional[str] = None,
    focus_concept_id: Optional[str] = None,
    generate: Optional[Callable[[str], CardSet]] = None,
) -> List[Dict]:
    if not chunks:
        return []
    count = max(1, min(int(count), MAX_CARDS))
    prompt = build_prompt(chunks, count, course_name, degree_level, list(concept_ids), existing_fronts, focus_concept)
    card_set = (generate or default_generate)(prompt)
    return to_cards(card_set, chunks, count, concept_ids, existing_fronts, focus_concept_id)
