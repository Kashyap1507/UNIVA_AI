"""
University course models: semesters, courses, modules, concepts and materials.

All collections are top-level and carry `user_id` (plan §1). Dates the student
enters (semester start/end, exam dates) are ISO "YYYY-MM-DD" strings; created/
updated timestamps are Firestore server timestamps. `to_response()` builds a
plain JSON-safe dict for API responses.
"""

from datetime import date, datetime
from typing import Any, Dict, List, Optional

from .base import BaseModel


def _iso(value: Any) -> Any:
    """Timestamps (incl. DatetimeWithNanoseconds) to ISO strings; other values unchanged."""
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    return value


class _UserOwnedModel(BaseModel):
    """Common fields and response building for user-owned course documents."""

    RESPONSE_FIELDS: tuple = ()

    def __init__(self, **kwargs):
        self.user_id: str = kwargs.get("user_id", "")
        self.is_deleted: bool = kwargs.get("is_deleted", False)
        super().__init__(**kwargs)

    def validate(self) -> tuple[bool, str]:
        if not self.user_id:
            return False, "user_id is required"
        return True, "Valid"

    def to_response(self) -> Dict[str, Any]:
        data = {"id": self.id}
        for field in self.RESPONSE_FIELDS:
            data[field] = _iso(getattr(self, field, None))
        data["created_at"] = _iso(self.created_at)
        data["updated_at"] = _iso(self.updated_at)
        return data


class Semester(_UserOwnedModel):
    collection_name = "semesters"
    RESPONSE_FIELDS = ("name", "start_date", "end_date", "is_current")

    def __init__(self, **kwargs):
        self.name: str = kwargs.get("name", "")
        self.start_date: Optional[str] = kwargs.get("start_date")
        self.end_date: Optional[str] = kwargs.get("end_date")
        self.is_current: bool = kwargs.get("is_current", False)
        super().__init__(**kwargs)

    def validate(self) -> tuple[bool, str]:
        ok, msg = super().validate()
        if not ok:
            return ok, msg
        if not self.name.strip():
            return False, "Semester name is required"
        return True, "Valid"


COURSE_STATUSES = ("ACTIVE", "ARCHIVED")


class Course(_UserOwnedModel):
    collection_name = "courses"
    RESPONSE_FIELDS = (
        "semester_id",
        "name",
        "code",
        "instructor",
        "color",
        "exam_dates",
        "status",
        "description",
        "structure_status",
        "structure_error",
        "credits",
    )

    def __init__(self, **kwargs):
        # Credit weight for the GPA (grade tracker); 3 when not set
        self.credits: Optional[float] = kwargs.get("credits")
        self.semester_id: Optional[str] = kwargs.get("semester_id")
        self.name: str = kwargs.get("name", "")
        self.code: Optional[str] = kwargs.get("code")
        self.instructor: Optional[str] = kwargs.get("instructor")
        self.color: Optional[str] = kwargs.get("color")
        self.exam_dates: List[str] = kwargs.get("exam_dates", [])
        self.status: str = kwargs.get("status", "ACTIVE")
        self.description: Optional[str] = kwargs.get("description")
        # AI course structure generation: NONE | PROCESSING | READY | FAILED
        self.structure_status: str = kwargs.get("structure_status", "NONE")
        self.structure_error: Optional[str] = kwargs.get("structure_error")
        super().__init__(**kwargs)

    def validate(self) -> tuple[bool, str]:
        ok, msg = super().validate()
        if not ok:
            return ok, msg
        if not self.name.strip():
            return False, "Course name is required"
        if self.status not in COURSE_STATUSES:
            return False, f"status must be one of: {', '.join(COURSE_STATUSES)}"
        return True, "Valid"


class CourseModule(_UserOwnedModel):
    collection_name = "course_modules"
    RESPONSE_FIELDS = ("course_id", "title", "order", "week", "lecture_date", "summary", "material_ids")

    def __init__(self, **kwargs):
        self.course_id: str = kwargs.get("course_id", "")
        self.title: str = kwargs.get("title", "")
        self.order: int = kwargs.get("order", 0)
        self.week: Optional[int] = kwargs.get("week")
        self.lecture_date: Optional[str] = kwargs.get("lecture_date")
        self.summary: Optional[str] = kwargs.get("summary")
        self.material_ids: List[str] = kwargs.get("material_ids", [])
        super().__init__(**kwargs)


class CourseConcept(_UserOwnedModel):
    collection_name = "course_concepts"
    RESPONSE_FIELDS = (
        "course_id",
        "module_id",
        "name",
        "summary",
        "learning_outcome",
        "mastery",
        "attempts",
        "last_practiced_at",
    )

    def __init__(self, **kwargs):
        self.course_id: str = kwargs.get("course_id", "")
        self.module_id: Optional[str] = kwargs.get("module_id")
        self.name: str = kwargs.get("name", "")
        self.summary: Optional[str] = kwargs.get("summary")
        self.learning_outcome: Optional[str] = kwargs.get("learning_outcome")
        self.mastery: float = kwargs.get("mastery", 0.0)
        self.attempts: int = kwargs.get("attempts", 0)
        self.last_practiced_at: Optional[datetime] = kwargs.get("last_practiced_at")
        super().__init__(**kwargs)


MATERIAL_KINDS = ("SYLLABUS", "SLIDES", "PDF", "NOTES", "TRANSCRIPT", "SCAN", "PAPER")
INGESTION_STATUSES = ("PENDING", "PROCESSING", "READY", "FAILED")


class CourseMaterial(_UserOwnedModel):
    collection_name = "course_materials"
    RESPONSE_FIELDS = (
        "course_id",
        "kind",
        "title",
        "mime",
        "size_bytes",
        "pages",
        "ingestion_status",
        "error",
        "chunk_count",
        "document_id",
        "shared",
        "summary",
    )

    def __init__(self, **kwargs):
        # Lecture summary (Phase 10): {overview, key_points[], terms[], exam_questions[]}
        self.summary: Optional[dict] = kwargs.get("summary")
        self.course_id: str = kwargs.get("course_id", "")
        self.kind: str = kwargs.get("kind", "PDF")
        self.title: str = kwargs.get("title", "")
        self.storage_path: Optional[str] = kwargs.get("storage_path")
        self.mime: Optional[str] = kwargs.get("mime")
        self.size_bytes: int = kwargs.get("size_bytes", 0)
        self.pages: Optional[int] = kwargs.get("pages")
        self.ingestion_status: str = kwargs.get("ingestion_status", "PENDING")
        self.error: Optional[str] = kwargs.get("error")
        self.chunk_count: int = kwargs.get("chunk_count", 0)
        self.document_id: Optional[str] = kwargs.get("document_id")
        self.shared: bool = kwargs.get("shared", False)
        super().__init__(**kwargs)

    def validate(self) -> tuple[bool, str]:
        ok, msg = super().validate()
        if not ok:
            return ok, msg
        if not self.course_id:
            return False, "course_id is required"
        if self.kind not in MATERIAL_KINDS:
            return False, f"kind must be one of: {', '.join(MATERIAL_KINDS)}"
        if self.ingestion_status not in INGESTION_STATUSES:
            return False, "invalid ingestion_status"
        return True, "Valid"


MATERIAL_CHUNKS = "material_chunks"
