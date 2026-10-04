"""
Document Page model for Firebase Firestore
Manages individual page metadata within a document
"""

from datetime import datetime
from typing import Any, Dict, Optional

from google.cloud import firestore


class DocumentPage:
    """
    Document Page model representing a single page within a document

    Firestore subcollection: 'documents/{doc_id}/pages'

    Fields align with the app's DocumentPageEntity
    """

    def __init__(
        self,
        id: Optional[str] = None,
        document_id: str = None,
        page_number: int = 1,
        image_url: Optional[str] = None,
        thumbnail_url: Optional[str] = None,
        width: int = 0,
        height: int = 0,
        file_size: int = 0,
        storage_path: Optional[str] = None,
        thumbnail_path: Optional[str] = None,
        uploaded_at: Optional[datetime] = None,
        is_deleted: bool = False,
        deleted_at: Optional[datetime] = None,
    ):

        self.id = id
        self.document_id = document_id
        self.page_number = page_number
        self.image_url = image_url  # public URL on pages from before Phase 13
        self.thumbnail_url = thumbnail_url
        self.width = width
        self.height = height
        self.file_size = file_size
        self.storage_path = storage_path  # Storage path (private files are served through signed URLs)
        self.thumbnail_path = thumbnail_path
        self.uploaded_at = uploaded_at or datetime.utcnow()
        self.is_deleted = is_deleted
        self.deleted_at = deleted_at

    def to_dict(self) -> Dict[str, Any]:
        """
        Convert to dictionary for Firestore storage
        Includes all fields with proper datetime serialization
        """
        return {
            "document_id": self.document_id,
            "page_number": self.page_number,
            "image_url": self.image_url,
            "thumbnail_url": self.thumbnail_url,
            "width": self.width,
            "height": self.height,
            "file_size": self.file_size,
            "storage_path": self.storage_path,
            "thumbnail_path": self.thumbnail_path,
            "uploaded_at": self.uploaded_at,
            "is_deleted": self.is_deleted,
            "deleted_at": self.deleted_at,
        }

    @staticmethod
    def from_dict(page_id: str, data: Dict[str, Any]) -> "DocumentPage":
        """
        Create DocumentPage instance from Firestore document data

        Args:
            page_id: Firestore document ID
            data: Page data dictionary

        Returns:
            DocumentPage instance
        """
        return DocumentPage(
            id=page_id,
            document_id=data.get("document_id"),
            page_number=data.get("page_number", 1),
            image_url=data.get("image_url"),
            thumbnail_url=data.get("thumbnail_url"),
            width=data.get("width", 0),
            height=data.get("height", 0),
            file_size=data.get("file_size", 0),
            storage_path=data.get("storage_path"),
            thumbnail_path=data.get("thumbnail_path"),
            uploaded_at=data.get("uploaded_at"),
            is_deleted=data.get("is_deleted", False),
            deleted_at=data.get("deleted_at"),
        )

    def to_api_dict(self, image_url: Optional[str] = None, thumbnail_url: Optional[str] = None) -> Dict[str, Any]:
        """
        Convert to JSON-serializable dictionary for API responses (camelCase for the app).
        `image_url`/`thumbnail_url` override the stored URLs (signed URLs for private files).
        """
        # Serialize datetime fields (handle Firestore DatetimeWithNanoseconds)
        uploaded_at_str = None
        if self.uploaded_at:
            if hasattr(self.uploaded_at, "isoformat"):
                uploaded_at_str = self.uploaded_at.isoformat()
            else:
                uploaded_at_str = str(self.uploaded_at)

        deleted_at_str = None
        if self.deleted_at:
            if hasattr(self.deleted_at, "isoformat"):
                deleted_at_str = self.deleted_at.isoformat()
            else:
                deleted_at_str = str(self.deleted_at)

        # Use camelCase field names to match mobile app expectations
        result = {
            "id": self.id,
            "documentId": self.document_id,
            "pageNumber": self.page_number,
            "imageUrl": image_url or self.image_url,
            "thumbnailUrl": thumbnail_url or self.thumbnail_url,
            "width": self.width,
            "height": self.height,
            "fileSize": self.file_size,
            "isDeleted": self.is_deleted,
        }

        if uploaded_at_str:
            result["uploadedAt"] = uploaded_at_str
        if deleted_at_str:
            result["deletedAt"] = deleted_at_str

        return result

    def mark_deleted(self):
        """Soft delete the page"""
        self.is_deleted = True
        self.deleted_at = datetime.utcnow()

    def __repr__(self):
        return (
            f"<DocumentPage {self.id}: Page {self.page_number} of {self.document_id}>"
        )
