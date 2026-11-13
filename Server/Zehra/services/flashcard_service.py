"""
Flashcards with SM-2 scheduling.

    flashcards:        user_id, course_id, concept_id, front, back, origin (AI|MANUAL),
                       source{chunk_id, material_id, material_title, page},
                       srs{ease, interval_days, reps, lapses, due_at, last_reviewed_at},
                       suspended, is_deleted, created_at, updated_at
    flashcard_reviews: user_id, card_id, course_id, grade, reviewed_at,
                       prev_interval, new_interval, client_review_id

Reviews may arrive late from the app's offline queue. The app sends the time the
student answered and a client_review_id, so a replayed review is applied once
and scheduled from when it really happened.
"""

import logging
import re
import time
from datetime import datetime, timezone
from typing import Callable, Dict, List, Optional

from google.cloud.firestore_v1.base_query import FieldFilter

from app.ai_framework.workflows import flashcard_generation
from app.models.course import MATERIAL_CHUNKS
from app.services import course_service, retrieval_service, srs
from app.services.course_service import ServiceError
from app.services.firebase_service import get_firestore_client
from app.services.retrieval_service import RetrievedChunk

logger = logging.getLogger(__name__)

CARDS = "flashcards"
REVIEWS = "flashcard_reviews"
DEFAULT_COUNT = 10
EXCERPTS_PER_RUN = 12
MAX_DUE = 500
CLIENT_ID_RE = re.compile(r"^[A-Za-z0-9_-]{8,64}$")


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(value):
    return value.isoformat() if hasattr(value, "isoformat") else value


def card_response(card_id: str, data: Dict) -> Dict:
    state = data.get("srs") or {}
    return {
        "id": card_id,
        "course_id": data.get("course_id"),
        "concept_id": data.get("concept_id"),
        "front": data.get("front", ""),
        "back": data.get("back", ""),
        "origin": data.get("origin", "MANUAL"),
        "source": data.get("source"),
        "suspended": data.get("suspended", False),
        "srs": {
            "ease": state.get("ease", srs.DEFAULT_EASE),
            "interval_days": state.get("interval_days", 0),
            "reps": state.get("reps", 0),
            "lapses": state.get("lapses", 0),
            "due_at": _iso(state.get("due_at")),
            "last_reviewed_at": _iso(state.get("last_reviewed_at")),
        },
        "created_at": _iso(data.get("created_at")),
        "updated_at": _iso(data.get("updated_at")),
    }


def _new_card(user_id: str, course_id: str, front: str, back: str, origin: str, concept_id=None, source=None) -> Dict:
    now = _now()
    return {
        "user_id": user_id,
        "course_id": course_id,
        "concept_id": concept_id,
        "front": front,
        "back": back,
        "origin": origin,
        "source": source,
        "suspended": False,
        "is_deleted": False,
        # New cards are due straight away
        "srs": {**srs.SrsState().to_dict(), "due_at": now, "last_reviewed_at": None},
        "created_at": now,
        "updated_at": now,
    }


def _get_card(user_id: str, card_id: str):
    if not card_id or not isinstance(card_id, str):
        raise ServiceError("Flashcard not found", 404)
    ref = get_firestore_client().collection(CARDS).document(card_id)
    snap = ref.get()
    data = snap.to_dict() if snap.exists else None
    if not data or data.get("user_id") != user_id or data.get("is_deleted"):
        raise ServiceError("Flashcard not found", 404)
    return ref, data


def _card_text(data: Dict, field: str, max_len: int, required: bool) -> Optional[str]:
    return course_service._text(data, field, max_len, required=required)


def _check_concept(user_id: str, course_id: str, concept_id) -> Optional[str]:
    if concept_id in (None, ""):
        return None
    if not isinstance(concept_id, str) or concept_id not in {c.id for c in course_service.list_concepts(user_id, course_id)}:
        raise ServiceError("concept_id is not a concept of this course")
    return concept_id


# --- CRUD ------------------------------------------------------------------------------


def _course_cards(user_id: str, course_id: str) -> List[tuple]:
    query = (
        get_firestore_client()
        .collection(CARDS)
        .where(filter=FieldFilter("user_id", "==", user_id))
        .where(filter=FieldFilter("course_id", "==", course_id))
    )
    return [(d.id, d.to_dict()) for d in query.stream() if not (d.to_dict() or {}).get("is_deleted")]


def _active_course_ids(user_id: str) -> set:
    return {c.id for c in course_service.list_courses(user_id)}


def list_cards(user_id: str, course_id: Optional[str] = None, due: bool = False, limit: int = MAX_DUE) -> List[Dict]:
    limit = max(1, min(int(limit), MAX_DUE))
    if course_id:
        course_service.get_course(user_id, course_id)
    if due:
        query = (
            get_firestore_client()
            .collection(CARDS)
            .where(filter=FieldFilter("user_id", "==", user_id))
        )
        if course_id:
            query = query.where(filter=FieldFilter("course_id", "==", course_id))
        query = query.where(filter=FieldFilter("srs.due_at", "<=", _now())).order_by("srs.due_at").limit(limit * 2)
        rows = [(d.id, d.to_dict()) for d in query.stream()]
        active = {course_id} if course_id else _active_course_ids(user_id)
        rows = [
            (i, d) for i, d in rows
            if not d.get("is_deleted") and not d.get("suspended") and d.get("course_id") in active
        ]
        return [card_response(i, d) for i, d in rows[:limit]]
    if not course_id:
        raise ServiceError("course_id is required unless due=true")
    rows = _course_cards(user_id, course_id)
    rows.sort(key=lambda r: r[1].get("created_at") or datetime.min.replace(tzinfo=timezone.utc), reverse=True)
    return [card_response(i, d) for i, d in rows[:limit]]


def create_card(user_id: str, course_id: str, data: Dict) -> Dict:
    course_service.get_course(user_id, course_id)
    front = _card_text(data, "front", flashcard_generation.MAX_FRONT, True)
    back = _card_text(data, "back", flashcard_generation.MAX_BACK, True)
    concept_id = _check_concept(user_id, course_id, data.get("concept_id"))
    card = _new_card(user_id, course_id, front, back, "MANUAL", concept_id)
    ref = get_firestore_client().collection(CARDS).document()
    ref.set(card)
    return card_response(ref.id, card)


def update_card(user_id: str, card_id: str, data: Dict) -> Dict:
    ref, card = _get_card(user_id, card_id)
    updates = {}
    if "front" in data:
        updates["front"] = _card_text(data, "front", flashcard_generation.MAX_FRONT, True)
    if "back" in data:
        updates["back"] = _card_text(data, "back", flashcard_generation.MAX_BACK, True)
    if "suspended" in data:
        if not isinstance(data["suspended"], bool):
            raise ServiceError("suspended must be true or false")
        updates["suspended"] = data["suspended"]
    if "concept_id" in data:
        updates["concept_id"] = _check_concept(user_id, card["course_id"], data["concept_id"])
    if not updates:
        raise ServiceError("Nothing to update")
    updates["updated_at"] = _now()
    ref.update(updates)
    card.update(updates)
    return card_response(card_id, card)


def delete_card(user_id: str, card_id: str) -> None:
    ref, _ = _get_card(user_id, card_id)
    ref.update({"is_deleted": True, "updated_at": _now()})


# --- review ------------------------------------------------------------------------------


def _parse_reviewed_at(value) -> datetime:
    now = _now()
    if value in (None, ""):
        return now
    if not isinstance(value, str):
        raise ServiceError("reviewed_at must be an ISO 8601 timestamp")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        raise ServiceError("reviewed_at must be an ISO 8601 timestamp")
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    # A clock running ahead can't push reviews into the future
    return min(parsed, now)


def review(user_id: str, card_id: str, grade, reviewed_at=None, client_review_id=None) -> Dict:
    if not isinstance(grade, int) or isinstance(grade, bool) or not 0 <= grade <= 5:
        raise ServiceError("grade must be an integer from 0 to 5")
    if client_review_id is not None and (not isinstance(client_review_id, str) or not CLIENT_ID_RE.match(client_review_id)):
        raise ServiceError("client_review_id must be 8-64 letters, digits, - or _")
    when = _parse_reviewed_at(reviewed_at)
    ref, card = _get_card(user_id, card_id)
    db = get_firestore_client()

    review_ref = db.collection(REVIEWS).document(f"{user_id}_{client_review_id}") if client_review_id else db.collection(REVIEWS).document()
    if client_review_id:
        existing = review_ref.get()
        if existing.exists:
            # Replayed from the offline queue: already applied
            return {**card_response(card_id, card), "duplicate": True}

    before = srs.SrsState.from_dict(card.get("srs"))
    after = srs.next_state(before, grade)
    new_srs = {**after.to_dict(), "due_at": srs.due_after(when, after), "last_reviewed_at": when}
    batch = db.batch()
    batch.update(ref, {"srs": new_srs, "updated_at": _now()})
    batch.set(
        review_ref,
        {
            "user_id": user_id,
            "card_id": card_id,
            "course_id": card.get("course_id"),
            "grade": grade,
            "reviewed_at": when,
            "prev_interval": before.interval_days,
            "new_interval": after.interval_days,
            "client_review_id": client_review_id,
        },
    )
    batch.commit()
    # Offline reviews count on the day they were answered
    from app.services import study_activity_service

    study_activity_service.record(user_id, "cards", when)
    card["srs"] = new_srs
    return {**card_response(card_id, card), "duplicate": False}


def summary(user_id: str) -> Dict:
    """Due counts across the student's courses, for Home and the course list."""
    due = list_cards(user_id, due=True, limit=MAX_DUE)
    by_course: Dict[str, int] = {}
    for card in due:
        by_course[card["course_id"]] = by_course.get(card["course_id"], 0) + 1
    return {"due_total": len(due), "due_by_course": by_course}


# --- generation -------------------------------------------------------------------------


def _chunk_from(doc_id: str, data: Dict) -> RetrievedChunk:
    return RetrievedChunk(
        chunk_id=doc_id,
        material_id=data.get("material_id", ""),
        material_title=data.get("material_title", ""),
        text=data.get("text", ""),
        page=data.get("page"),
        page_end=data.get("page_end"),
        distance=None,
    )


def spread_chunks(user_id: str, course_id: str, used_chunk_ids: set, n: int = EXCERPTS_PER_RUN) -> List[RetrievedChunk]:
    """Evenly spaced excerpts across the course, preferring ones no card came from yet."""
    query = (
        get_firestore_client()
        .collection(MATERIAL_CHUNKS)
        .where(filter=FieldFilter("user_id", "==", user_id))
        .where(filter=FieldFilter("course_id", "==", course_id))
        .select(["material_id", "material_title", "text", "page", "page_end", "chunk_index"])
    )
    rows = [(d.id, d.to_dict() or {}) for d in query.stream()]
    rows = [r for r in rows if (r[1].get("text") or "").strip()]
    rows.sort(key=lambda r: (r[1].get("material_title", ""), r[1].get("material_id", ""), r[1].get("chunk_index", 0)))
    fresh = [r for r in rows if r[0] not in used_chunk_ids] or rows
    if len(fresh) > n:
        step = len(fresh) / n
        fresh = [fresh[int(i * step)] for i in range(n)]
    return [_chunk_from(i, d) for i, d in fresh]


def generate(
    user_id: str,
    course_id: str,
    concept_id: Optional[str] = None,
    count=DEFAULT_COUNT,
    run: Optional[Callable] = None,
    embed_query: Optional[Callable] = None,
) -> List[Dict]:
    if not isinstance(count, int) or isinstance(count, bool) or not 1 <= count <= flashcard_generation.MAX_CARDS:
        raise ServiceError(f"count must be between 1 and {flashcard_generation.MAX_CARDS}")
    course = course_service.get_course(user_id, course_id)
    concept_id = _check_concept(user_id, course_id, concept_id)
    concepts = course_service.list_concepts(user_id, course_id)
    focus = next((c for c in concepts if c.id == concept_id), None)

    existing = _course_cards(user_id, course_id)
    existing_fronts = [d.get("front", "") for _, d in existing]
    used = {(d.get("source") or {}).get("chunk_id") for _, d in existing}

    if focus:
        query = f"{focus.name}: {focus.summary or ''}".strip()
        chunks = retrieval_service.search(user_id, course_id, query, k=8, embed_query=embed_query)
    else:
        chunks = spread_chunks(user_id, course_id, used)
    if not chunks:
        raise ServiceError("Upload and process some course materials first.", 409)

    db = get_firestore_client()
    user = db.collection("users").document(user_id).get().to_dict() or {}
    started = time.time()
    try:
        cards = (run or flashcard_generation.run)(
            chunks,
            count,
            course.name,
            (user.get("student_profile") or {}).get("degree_level"),
            {c.name: c.id for c in concepts},
            existing_fronts,
            focus_concept=focus.name if focus else None,
            focus_concept_id=focus.id if focus else None,
        )
    except Exception as e:
        logger.error(f"Flashcard generation failed for course {course_id}: {e}", exc_info=True)
        _log(user_id, course_id, False, started, 0, str(e)[:300])
        raise ServiceError("Couldn't create flashcards right now. Please try again later.", 502)

    batch = db.batch()
    out = []
    for c in cards:
        data = _new_card(user_id, course_id, c["front"], c["back"], "AI", c.get("concept_id"), c.get("source"))
        ref = db.collection(CARDS).document()
        batch.set(ref, data)
        out.append(card_response(ref.id, data))
    if out:
        batch.commit()
    _log(user_id, course_id, True, started, len(out), None)
    return out


def _log(user_id: str, course_id: str, success: bool, started: float, cards: int, error: Optional[str]):
    try:
        from app.ai_framework.utils.model_router import model_for
        from app.services.ai_workflow_logger import AIWorkflowLogger

        AIWorkflowLogger().log_workflow(
            workflow_type=flashcard_generation.TASK,
            model=model_for(flashcard_generation.TASK),
            prompt={"template_version": "1.0"},
            input_data={"parameters": {"course_id": course_id}},
            response={"success": success, "error_message": error},
            metrics={"latency_ms": int((time.time() - started) * 1000), "cards": cards},
            user_id=user_id,
        )
    except Exception as e:
        logger.warning(f"Could not log flashcard generation: {e}")
