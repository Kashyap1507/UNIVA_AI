"""
course_structure_generation: course materials → modules, concepts, deadlines.

Uses the course's indexed text (syllabus first, then other materials) and asks
Gemini for a structured outline. Replaces earlier AI-generated modules and
concepts, refreshes syllabus-sourced deadlines and copies exam dates onto the
course. Runs in the background; course.structure_status tracks progress.

Ingestion calls request_generation() whenever a material becomes READY, so new
uploads show up in the outline without the student asking. A request that
arrives mid-run sets course.structure_stale and the run repeats once it ends.
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime
from typing import Callable, List, Literal, Optional

from google.cloud.firestore_v1.base_query import FieldFilter
from pydantic import BaseModel, Field, field_validator

from app.models.course import MATERIAL_CHUNKS, Course, CourseMaterial

logger = logging.getLogger(__name__)

TASK = "course_structure_generation"
MAX_CONTEXT_CHARS = 60_000
MAX_RERUNS = 3  # follow-up runs for uploads that finish while a run is in progress
DEADLINES = "deadlines"
DEADLINE_TYPES = ("EXAM", "ASSIGNMENT", "QUIZ", "LAB", "PRESENTATION", "OTHER")

_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="course-structure")


# --- response schema -------------------------------------------------------------


class ConceptOut(BaseModel):
    name: str = Field(description="Short concept name, e.g. 'Bayes' theorem'")
    summary: Optional[str] = Field(None, description="One or two sentences explaining the concept")
    learning_outcome: Optional[str] = Field(None, description="What the student should be able to do")


class ModuleOut(BaseModel):
    title: str = Field(description="Module or lecture title")
    week: Optional[int] = Field(None, description="Teaching week number if stated")
    summary: Optional[str] = None
    concepts: List[ConceptOut] = Field(default_factory=list)
    source_files: List[str] = Field(
        default_factory=list,
        description="Titles of the materials this module is drawn from, exactly as in the '--- KIND: title ---' headers",
    )


class DeadlineOut(BaseModel):
    title: str
    type: Literal["EXAM", "ASSIGNMENT", "QUIZ", "LAB", "PRESENTATION", "OTHER"] = "OTHER"
    due_date: Optional[str] = Field(None, description="YYYY-MM-DD if the material gives a date")
    weight_pct: Optional[float] = Field(None, description="Share of the final grade, 0-100")

    @field_validator("due_date")
    @classmethod
    def _valid_date(cls, v):
        if not v:
            return None
        try:
            return date.fromisoformat(v[:10]).isoformat()
        except ValueError:
            return None

    @field_validator("weight_pct")
    @classmethod
    def _valid_weight(cls, v):
        return v if v is None or 0 <= v <= 100 else None


class CourseStructure(BaseModel):
    modules: List[ModuleOut] = Field(default_factory=list)
    deadlines: List[DeadlineOut] = Field(default_factory=list)


PROMPT_TEMPLATE = """You are helping a {degree} student organise a university course.

Course: {course_name}{course_code}
From the course materials below, produce:
1. modules: the course's modules or lectures in teaching order (use the syllabus
   schedule when there is one). For each, list the 3-8 key concepts a student must
   master, each with a one-sentence summary and a concrete learning outcome.
2. deadlines: every graded item the materials mention (exams, assignments, quizzes,
   labs, presentations) with its due date (YYYY-MM-DD) and grade weight when stated.
   Do not invent dates or weights; leave them empty if not given.
3. For each module, source_files: the titles of the materials it comes from.

Every material below must be covered by at least one module. Use precise, university-level terminology. Do not include anything that is not
supported by the materials.

=== COURSE MATERIALS ===
{context}
"""


# --- helpers ----------------------------------------------------------------------


def _ready_materials(db, user_id: str, course_id: str) -> List[CourseMaterial]:
    """READY materials of the course, syllabus first, then by title."""
    materials = [
        CourseMaterial.from_dict(d.to_dict(), d.id)
        for d in db.collection(CourseMaterial.collection_name)
        .where(filter=FieldFilter("user_id", "==", user_id))
        .where(filter=FieldFilter("course_id", "==", course_id))
        .stream()
    ]
    materials = [m for m in materials if not m.is_deleted and m.ingestion_status == "READY"]
    materials.sort(key=lambda m: (m.kind != "SYLLABUS", m.title.lower()))
    return materials


def _material_text(db, user_id: str, material: CourseMaterial) -> str:
    chunks = [
        c.to_dict()
        for c in db.collection(MATERIAL_CHUNKS)
        .where(filter=FieldFilter("user_id", "==", user_id))
        .where(filter=FieldFilter("material_id", "==", material.id))
        .stream()
    ]
    chunks.sort(key=lambda c: c.get("chunk_index", 0))
    parts, last_end = [], ""
    for chunk in chunks:
        text = chunk.get("text", "")
        # Chunks overlap by ~200 chars; drop the repeated prefix where it matches
        if last_end and text.startswith(last_end[-100:]):
            text = text[len(last_end[-100:]):]
        parts.append(text + "\n")
        last_end = chunk.get("text", "")
    return "".join(parts)


def fair_shares(lengths: List[int], budget: int) -> List[int]:
    """Split `budget` so short texts fit whole and the rest share what is left equally."""
    shares = [0] * len(lengths)
    pending = sorted(range(len(lengths)), key=lambda i: lengths[i])
    while pending:
        share = budget // len(pending)
        i = pending.pop(0)
        shares[i] = min(lengths[i], share)
        budget -= shares[i]
    return shares


def build_context(db, user_id: str, course_id: str, max_chars: int = MAX_CONTEXT_CHARS) -> str:
    """Concatenate each READY material's text, giving every material a fair share of max_chars."""
    materials = _ready_materials(db, user_id, course_id)
    headers = [f"\n--- {m.kind}: {m.title} ---\n" for m in materials]
    texts = [_material_text(db, user_id, m) for m in materials]
    budget = max(0, max_chars - sum(len(h) for h in headers))
    shares = fair_shares([len(t) for t in texts], budget)
    return "".join(h + t[:n] for h, t, n in zip(headers, texts, shares))


def _material_ids_for(source_files: List[str], materials: List[CourseMaterial]) -> List[str]:
    by_title = {m.title.strip().lower(): m.id for m in materials}
    ids = [by_title.get(t.strip().lower()) for t in source_files]
    return list(dict.fromkeys(i for i in ids if i))


def _save_deadlines(db, user_id: str, course_id: str, deadlines: List[DeadlineOut]) -> int:
    # Replace earlier syllabus-sourced deadlines the student hasn't completed
    existing = (
        db.collection(DEADLINES)
        .where(filter=FieldFilter("user_id", "==", user_id))
        .where(filter=FieldFilter("course_id", "==", course_id))
        .stream()
    )
    for doc in existing:
        data = doc.to_dict() or {}
        if data.get("source") == "SYLLABUS" and not data.get("completed"):
            doc.reference.delete()

    count = 0
    now = datetime.now()
    for d in deadlines:
        if not d.title.strip():
            continue
        ref = db.collection(DEADLINES).document()
        ref.set(
            {
                "user_id": user_id,
                "course_id": course_id,
                "title": d.title.strip()[:200],
                "type": d.type,
                "due_at": d.due_date,
                "weight_pct": d.weight_pct,
                "source": "SYLLABUS",
                "completed": False,
                "created_at": now,
                "updated_at": now,
            }
        )
        count += 1
    return count


def _generate_default(prompt: str) -> CourseStructure:
    from app.ai_framework.utils.model_router import client_for

    return client_for(TASK).generate_structured(response_model=CourseStructure, prompt=prompt)


def generate_structure(
    user_id: str,
    course_id: str,
    generate: Callable[[str], CourseStructure] = _generate_default,
    db=None,
    allow_rerun: bool = False,
) -> dict:
    """
    Run synchronously. Never raises; records the outcome on the course. With
    allow_rerun, a run that ends with structure_stale set stays PROCESSING and
    returns rerun=True so the caller runs again.
    """
    from app.services import course_service

    if db is None:
        from app.services.firebase_service import get_firestore_client

        db = get_firestore_client()
    started = time.time()
    course_ref = db.collection(Course.collection_name).document(course_id)

    def finish(status: str, error: Optional[str] = None, **extra):
        _log(user_id, course_id, status == "READY", started, error, extra)
        if allow_rerun and (course_ref.get().to_dict() or {}).get("structure_stale"):
            course_ref.update({"structure_stale": False, "updated_at": datetime.now()})
            return {"success": status == "READY", "error": error, "rerun": True, **extra}
        course_ref.update({"structure_status": status, "structure_error": error, "updated_at": datetime.now()})
        return {"success": status == "READY", "error": error, **extra}

    try:
        course = Course.get_by_id(db, course_id)
        context = build_context(db, user_id, course_id)
        if not context.strip():
            return finish("FAILED", "Upload and process at least one file (ideally the syllabus) first.")

        user = db.collection("users").document(user_id).get().to_dict() or {}
        level = ((user.get("student_profile") or {}).get("degree_level") or "").upper()
        prompt = PROMPT_TEMPLATE.format(
            degree="Master's" if level == "MASTER" else "Bachelor's" if level == "BACHELOR" else "university",
            course_name=course.name,
            course_code=f" ({course.code})" if course.code else "",
            context=context,
        )
        structure = generate(prompt)

        materials = _ready_materials(db, user_id, course_id)
        modules = [
            {**m.model_dump(), "material_ids": _material_ids_for(m.source_files, materials)}
            for m in structure.modules
        ]
        counts = course_service.replace_structure(user_id, course_id, modules)
        deadline_count = _save_deadlines(db, user_id, course_id, structure.deadlines)
        exam_dates = sorted(
            set(course.exam_dates or [])
            | {d.due_date for d in structure.deadlines if d.type == "EXAM" and d.due_date}
        )
        course_ref.update({"exam_dates": exam_dates})
        return finish("READY", None, deadlines=deadline_count, **counts)
    except Exception as e:
        logger.error(f"Course structure generation failed for {course_id}: {e}", exc_info=True)
        return finish("FAILED", "Couldn't build the course outline. Please try again later.")


def _log(user_id: str, course_id: str, success: bool, started: float, error: Optional[str], counts: dict):
    try:
        from app.ai_framework.utils.model_router import model_for
        from app.services.ai_workflow_logger import AIWorkflowLogger

        AIWorkflowLogger().log_workflow(
            workflow_type=TASK,
            model=model_for(TASK),
            prompt={"template_version": "1.0"},
            input_data={"parameters": {"course_id": course_id}},
            response={"success": success, "error_message": error},
            metrics={"latency_ms": int((time.time() - started) * 1000), **counts},
            user_id=user_id,
        )
    except Exception as e:
        logger.warning(f"Could not log structure generation: {e}")


def start_generation(user_id: str, course_id: str, db=None) -> None:
    """Mark the course PROCESSING and generate in the background."""
    if db is None:
        from app.services.firebase_service import get_firestore_client

        db = get_firestore_client()
    db.collection(Course.collection_name).document(course_id).update(
        {"structure_status": "PROCESSING", "structure_error": None, "structure_stale": False, "updated_at": datetime.now()}
    )
    _executor.submit(_run_until_fresh, user_id, course_id)


def _run_until_fresh(user_id: str, course_id: str, db=None) -> None:
    for attempt in range(MAX_RERUNS + 1):
        result = generate_structure(user_id, course_id, db=db, allow_rerun=attempt < MAX_RERUNS)
        if not result.get("rerun"):
            return


def request_generation(user_id: str, course_id: str, db=None) -> None:
    """Rebuild the outline after a material changes; queue a rerun if one is in progress."""
    if db is None:
        from app.services.firebase_service import get_firestore_client

        db = get_firestore_client()
    course_ref = db.collection(Course.collection_name).document(course_id)
    course = course_ref.get().to_dict() or {}
    if not course or course.get("is_deleted"):
        return
    if course.get("structure_status") == "PROCESSING":
        course_ref.update({"structure_stale": True})
    else:
        start_generation(user_id, course_id, db)
