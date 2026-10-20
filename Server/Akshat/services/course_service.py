"""
Semesters, courses, modules and concepts for university students.

Every read and write is scoped to the owning user. Lists are filtered and
sorted in memory (one student's data is small), so no composite indexes are
needed beyond the vector index on material_chunks.
"""

import re
from datetime import date, datetime
from typing import Any, Dict, List, Optional

from google.cloud.firestore_v1.base_query import FieldFilter

from app.models.course import (
    COURSE_STATUSES,
    MATERIAL_CHUNKS,
    Course,
    CourseConcept,
    CourseMaterial,
    CourseModule,
    Semester,
)
from app.services.firebase_service import get_firestore_client

MAX_NAME = 120
MAX_CODE = 30
MAX_DESCRIPTION = 2000
MAX_EXAM_DATES = 20
COLOR_RE = re.compile(r"^#[0-9A-Fa-f]{6}$")


class ServiceError(Exception):
    """Raised for client errors; carries the HTTP status to return."""

    def __init__(self, message: str, status: int = 400):
        super().__init__(message)
        self.message = message
        self.status = status


# --- validation helpers -------------------------------------------------------


def _text(data: Dict[str, Any], field: str, max_len: int, required: bool = False) -> Optional[str]:
    value = data.get(field)
    if value is None:
        if required:
            raise ServiceError(f"{field} is required")
        return None
    if not isinstance(value, str):
        raise ServiceError(f"{field} must be a string")
    value = value.strip()
    if required and not value:
        raise ServiceError(f"{field} is required")
    if len(value) > max_len:
        raise ServiceError(f"{field} must be at most {max_len} characters")
    return value or None


def _iso_date(value: Any, field: str) -> Optional[str]:
    if value is None or value == "":
        return None
    if not isinstance(value, str):
        raise ServiceError(f"{field} must be a date string (YYYY-MM-DD)")
    try:
        return date.fromisoformat(value[:10]).isoformat()
    except ValueError:
        raise ServiceError(f"{field} must be a date (YYYY-MM-DD)")


def _now() -> datetime:
    return datetime.now()


# --- generic ownership --------------------------------------------------------


def _get_owned(model_cls, doc_id: str, user_id: str, label: str):
    if not doc_id or not isinstance(doc_id, str):
        raise ServiceError(f"{label} not found", 404)
    obj = model_cls.get_by_id(get_firestore_client(), doc_id)
    if not obj or obj.user_id != user_id or getattr(obj, "is_deleted", False):
        raise ServiceError(f"{label} not found", 404)
    return obj


def _list_owned(model_cls, user_id: str, **equals) -> List[Any]:
    query = get_firestore_client().collection(model_cls.collection_name).where(
        filter=FieldFilter("user_id", "==", user_id)
    )
    for field, value in equals.items():
        query = query.where(filter=FieldFilter(field, "==", value))
    items = [model_cls.from_dict(d.to_dict(), d.id) for d in query.stream()]
    return [i for i in items if not getattr(i, "is_deleted", False)]


def _update(model_cls, doc_id: str, updates: Dict[str, Any]):
    updates["updated_at"] = _now()
    get_firestore_client().collection(model_cls.collection_name).document(doc_id).update(updates)


# --- semesters -----------------------------------------------------------------


def _clear_current_semester(user_id: str, keep_id: Optional[str] = None):
    for sem in _list_owned(Semester, user_id):
        if sem.is_current and sem.id != keep_id:
            _update(Semester, sem.id, {"is_current": False})


def _semester_fields(data: Dict[str, Any], creating: bool) -> Dict[str, Any]:
    fields: Dict[str, Any] = {}
    if creating or "name" in data:
        fields["name"] = _text(data, "name", MAX_NAME, required=True)
    for f in ("start_date", "end_date"):
        if f in data:
            fields[f] = _iso_date(data[f], f)
    if "is_current" in data:
        if not isinstance(data["is_current"], bool):
            raise ServiceError("is_current must be true or false")
        fields["is_current"] = data["is_current"]
    start, end = fields.get("start_date"), fields.get("end_date")
    if start and end and end < start:
        raise ServiceError("end_date must be on or after start_date")
    return fields


def list_semesters(user_id: str) -> List[Semester]:
    return sorted(_list_owned(Semester, user_id), key=lambda s: s.start_date or "", reverse=True)


def create_semester(user_id: str, data: Dict[str, Any]) -> Semester:
    fields = _semester_fields(data, creating=True)
    semester = Semester(user_id=user_id, **fields)
    semester.save(get_firestore_client())
    if semester.is_current:
        _clear_current_semester(user_id, keep_id=semester.id)
    return Semester.get_by_id(get_firestore_client(), semester.id)


def update_semester(user_id: str, semester_id: str, data: Dict[str, Any]) -> Semester:
    _get_owned(Semester, semester_id, user_id, "Semester")
    fields = _semester_fields(data, creating=False)
    if not fields:
        raise ServiceError("No valid fields provided")
    _update(Semester, semester_id, fields)
    if fields.get("is_current"):
        _clear_current_semester(user_id, keep_id=semester_id)
    return Semester.get_by_id(get_firestore_client(), semester_id)


def delete_semester(user_id: str, semester_id: str):
    _get_owned(Semester, semester_id, user_id, "Semester")
    _update(Semester, semester_id, {"is_deleted": True, "is_current": False})
    # Courses stay; they just lose their semester link
    for course in _list_owned(Course, user_id, semester_id=semester_id):
        _update(Course, course.id, {"semester_id": None})


# --- courses ----------------------------------------------------------------------


def _course_fields(user_id: str, data: Dict[str, Any], creating: bool) -> Dict[str, Any]:
    fields: Dict[str, Any] = {}
    if creating or "name" in data:
        fields["name"] = _text(data, "name", MAX_NAME, required=True)
    if "code" in data:
        fields["code"] = _text(data, "code", MAX_CODE)
    if "instructor" in data:
        fields["instructor"] = _text(data, "instructor", MAX_NAME)
    if "description" in data:
        fields["description"] = _text(data, "description", MAX_DESCRIPTION)
    if "color" in data:
        color = data["color"]
        if color is not None and (not isinstance(color, str) or not COLOR_RE.match(color)):
            raise ServiceError("color must be a hex colour like #4F46E5")
        fields["color"] = color
    if "status" in data:
        if data["status"] not in COURSE_STATUSES:
            raise ServiceError(f"status must be one of: {', '.join(COURSE_STATUSES)}")
        fields["status"] = data["status"]
    if "exam_dates" in data:
        dates = data["exam_dates"]
        if not isinstance(dates, list) or len(dates) > MAX_EXAM_DATES:
            raise ServiceError(f"exam_dates must be a list of at most {MAX_EXAM_DATES} dates")
        fields["exam_dates"] = sorted({_iso_date(d, "exam_dates") for d in dates if d})
    if "credits" in data:
        credits = data["credits"]
        if credits is not None and (isinstance(credits, bool) or not isinstance(credits, (int, float)) or not 0 < credits <= 30):
            raise ServiceError("credits must be a number between 0 and 30")
        fields["credits"] = credits
    if "semester_id" in data:
        semester_id = data["semester_id"]
        if semester_id is not None:
            _get_owned(Semester, semester_id, user_id, "Semester")
        fields["semester_id"] = semester_id
    return fields


def list_courses(user_id: str, semester_id: Optional[str] = None, include_archived: bool = True) -> List[Course]:
    filters = {"semester_id": semester_id} if semester_id else {}
    courses = _list_owned(Course, user_id, **filters)
    if not include_archived:
        courses = [c for c in courses if c.status == "ACTIVE"]
    return sorted(courses, key=lambda c: (c.status != "ACTIVE", (c.code or c.name).lower()))


def get_course(user_id: str, course_id: str) -> Course:
    return _get_owned(Course, course_id, user_id, "Course")


def create_course(user_id: str, data: Dict[str, Any]) -> Course:
    fields = _course_fields(user_id, data, creating=True)
    course = Course(user_id=user_id, **fields)
    course.save(get_firestore_client())
    _sync_enrolled_courses(user_id)
    return get_course(user_id, course.id)


def update_course(user_id: str, course_id: str, data: Dict[str, Any]) -> Course:
    get_course(user_id, course_id)
    fields = _course_fields(user_id, data, creating=False)
    if not fields:
        raise ServiceError("No valid fields provided")
    _update(Course, course_id, fields)
    if "status" in fields:
        _sync_enrolled_courses(user_id)
    return get_course(user_id, course_id)


def delete_course(user_id: str, course_id: str):
    """Soft-delete the course and remove its search index (materials stay soft-deleted)."""
    get_course(user_id, course_id)
    _update(Course, course_id, {"is_deleted": True})
    for material in _list_owned(CourseMaterial, user_id, course_id=course_id):
        _update(CourseMaterial, material.id, {"is_deleted": True})
    delete_chunks(user_id, course_id=course_id)
    _sync_enrolled_courses(user_id)


def _sync_enrolled_courses(user_id: str):
    """Keep users.student_profile.enrolled_course_ids in step with active courses."""
    ids = [c.id for c in list_courses(user_id) if c.status == "ACTIVE"]
    get_firestore_client().collection("users").document(user_id).update(
        {"student_profile.enrolled_course_ids": ids}
    )


# --- modules & concepts ------------------------------------------------------------


def list_modules(user_id: str, course_id: str) -> List[CourseModule]:
    get_course(user_id, course_id)
    return sorted(_list_owned(CourseModule, user_id, course_id=course_id), key=lambda m: (m.order, m.title))


def list_concepts(user_id: str, course_id: str, module_id: Optional[str] = None) -> List[CourseConcept]:
    get_course(user_id, course_id)
    filters = {"course_id": course_id}
    if module_id:
        filters["module_id"] = module_id
    return sorted(_list_owned(CourseConcept, user_id, **filters), key=lambda c: c.name.lower())


def create_module(user_id: str, course_id: str, data: Dict[str, Any]) -> CourseModule:
    get_course(user_id, course_id)
    title = _text(data, "title", MAX_NAME, required=True)
    order = data.get("order")
    if order is not None and (isinstance(order, bool) or not isinstance(order, int)):
        raise ServiceError("order must be a whole number")
    week = data.get("week")
    if week is not None and (isinstance(week, bool) or not isinstance(week, int) or not 0 <= week <= 60):
        raise ServiceError("week must be a whole number between 0 and 60")
    if order is None:
        order = len(_list_owned(CourseModule, user_id, course_id=course_id))
    module = CourseModule(
        user_id=user_id,
        course_id=course_id,
        title=title,
        order=order,
        week=week,
        lecture_date=_iso_date(data.get("lecture_date"), "lecture_date"),
        summary=_text(data, "summary", MAX_DESCRIPTION),
    )
    module.save(get_firestore_client())
    return module


def replace_structure(
    user_id: str,
    course_id: str,
    modules: List[Dict[str, Any]],
) -> Dict[str, int]:
    """
    Replace the AI-generated structure of a course. `modules` items:
    {title, week?, summary?, material_ids?, concepts: [{name, summary?, learning_outcome?}]}.
    Old modules/concepts are soft-deleted so practice history keeps its ids.
    """
    db = get_firestore_client()
    for old in _list_owned(CourseModule, user_id, course_id=course_id):
        _update(CourseModule, old.id, {"is_deleted": True})
    for old in _list_owned(CourseConcept, user_id, course_id=course_id):
        _update(CourseConcept, old.id, {"is_deleted": True})

    module_count = concept_count = 0
    for order, item in enumerate(modules):
        module = CourseModule(
            user_id=user_id,
            course_id=course_id,
            title=(item.get("title") or f"Module {order + 1}")[:MAX_NAME],
            order=order,
            week=item.get("week"),
            summary=item.get("summary"),
            material_ids=item.get("material_ids") or [],
        )
        module.save(db)
        module_count += 1
        for concept in item.get("concepts", []):
            if not concept.get("name"):
                continue
            CourseConcept(
                user_id=user_id,
                course_id=course_id,
                module_id=module.id,
                name=concept["name"][:MAX_NAME],
                summary=concept.get("summary"),
                learning_outcome=concept.get("learning_outcome"),
            ).save(db)
            concept_count += 1
    return {"modules": module_count, "concepts": concept_count}


# --- chunks ------------------------------------------------------------------------


def delete_chunks(user_id: str, course_id: Optional[str] = None, material_id: Optional[str] = None) -> int:
    """Hard-delete search chunks for a course or a material (always scoped to the user)."""
    db = get_firestore_client()
    query = db.collection(MATERIAL_CHUNKS).where(filter=FieldFilter("user_id", "==", user_id))
    if course_id:
        query = query.where(filter=FieldFilter("course_id", "==", course_id))
    if material_id:
        query = query.where(filter=FieldFilter("material_id", "==", material_id))
    count = 0
    for doc in query.stream():
        doc.reference.delete()
        count += 1
    return count
