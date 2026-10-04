"""
Document model for Firebase Firestore
A multi-page scan of the student's work, used by "Check my work"
"""

from datetime import datetime
from typing import Any, Dict, Optional


def _iso(value) -> Optional[str]:
    if not value:
        return None
    return value.isoformat() if hasattr(value, "isoformat") else str(value)


class Document:
    """
    A scanned document owned by a student.

    Firestore collection: 'documents' (pages in the 'pages' subcollection).
    Fields align with the app's DocumentEntity (Room) and CoreData DocumentEntity.
    """

    # Document types (stored values shared with the app enum). Scans of the student's work are
    # stored as HOMEWORK; WORKSHEET only exists on old records.
    TYPE_HOMEWORK = "HOMEWORK"
    TYPE_WORKSHEET = "WORKSHEET"

    # Processing status (matches mobile app enum)
    STATUS_PENDING = "PENDING"
    STATUS_PROCESSING = "PROCESSING"
    STATUS_COMPLETED = "COMPLETED"
    STATUS_FAILED = "FAILED"

    def __init__(
        self,
        id: Optional[str] = None,
        user_id: Optional[str] = None,
        title: Optional[str] = None,
        type: str = TYPE_HOMEWORK,
        course_id: Optional[str] = None,
        page_count: int = 0,
        thumbnail_url: Optional[str] = None,
        thumbnail_path: Optional[str] = None,
        status: str = STATUS_PENDING,
        processing_progress: int = 0,
        created_at: Optional[datetime] = None,
        updated_at: Optional[datetime] = None,
        is_deleted: bool = False,
        deleted_at: Optional[datetime] = None,
    ):
        self.id = id
        self.user_id = user_id
        self.title = title
        self.type = type
        self.course_id = course_id
        self.page_count = page_count
        self.thumbnail_url = thumbnail_url  # public URL on records from before Phase 13
        self.thumbnail_path = thumbnail_path  # private Storage path (served through a signed URL)
        self.status = status
        self.processing_progress = processing_progress
        self.created_at = created_at or datetime.utcnow()
        self.updated_at = updated_at or datetime.utcnow()
        self.is_deleted = is_deleted
        self.deleted_at = deleted_at

    def to_dict(self) -> Dict[str, Any]:
        """Dictionary for Firestore storage"""
        return {
            "user_id": self.user_id,
            "title": self.title,
            "type": self.type,
            "course_id": self.course_id,
            "page_count": self.page_count,
            "thumbnail_url": self.thumbnail_url,
            "thumbnail_path": self.thumbnail_path,
            "status": self.status,
            "processing_progress": self.processing_progress,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
            "is_deleted": self.is_deleted,
            "deleted_at": self.deleted_at,
        }

    @staticmethod
    def from_dict(doc_id: str, data: Dict[str, Any]) -> "Document":
        """Document from Firestore data"""
        return Document(
            id=doc_id,
            user_id=data.get("user_id"),
            title=data.get("title"),
            type=data.get("type", Document.TYPE_HOMEWORK),
            course_id=data.get("course_id"),
            page_count=data.get("page_count", 0),
            thumbnail_url=data.get("thumbnail_url"),
            thumbnail_path=data.get("thumbnail_path"),
            status=data.get("status", Document.STATUS_PENDING),
            processing_progress=data.get("processing_progress", 0),
            created_at=data.get("created_at"),
            updated_at=data.get("updated_at"),
            is_deleted=data.get("is_deleted", False),
            deleted_at=data.get("deleted_at"),
        )

    def to_api_dict(self, thumbnail_url: Optional[str] = None) -> Dict[str, Any]:
        """
        JSON-serializable dict for API responses (camelCase for the app).
        `thumbnail_url` overrides the stored one (a signed URL for private files).
        """
        result = {
            "id": self.id,
            "userId": self.user_id,
            "title": self.title,
            "type": self.type,
            "pageCount": self.page_count,
            "thumbnailUrl": thumbnail_url or self.thumbnail_url,
            "status": self.status,
            "processingProgress": self.processing_progress,
            "isDeleted": self.is_deleted,
            "createdAt": _iso(self.created_at),
            "updatedAt": _iso(self.updated_at),
        }
        if self.course_id:
            result["courseId"] = self.course_id
        if self.deleted_at:
            result["deletedAt"] = _iso(self.deleted_at)
        return result

    def update_status(self, status: str, progress: int = None):
        """Update processing status and progress"""
        self.status = status
        if progress is not None:
            self.processing_progress = progress
        self.updated_at = datetime.utcnow()

    def mark_deleted(self):
        """Soft delete the document"""
        self.is_deleted = True
        self.deleted_at = datetime.utcnow()
        self.updated_at = datetime.utcnow()

    def __repr__(self):
        return f"<Document {self.id}: {self.title} ({self.type})>"
