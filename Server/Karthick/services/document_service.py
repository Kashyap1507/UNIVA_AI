"""
Document Service for scanned documents (the student's work for "Check my work")
Handles Firestore + Firebase Storage operations for documents and pages.

Ownership: every document carries `user_id`. Pages uploaded from Phase 13 on are
private (users/{uid}/documents/...) and are returned as short-lived signed URLs.
"""

import io
import logging
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Tuple

from PIL import Image

from app.models.document import Document
from app.models.document_page import DocumentPage
from app.services.document_access import user_owns_document
from app.services.firebase_service import get_firestore_client
from app.services.upload_service import UploadService

logger = logging.getLogger(__name__)

SIGNED_URL_MINUTES = 60


class DocumentService:
    """
    Service layer for document operations:
    - Document CRUD (owned by the student)
    - Page management with private storage
    - Sync support for the app
    """

    def __init__(self):
        self.upload_service = UploadService()

    @property
    def db(self):
        """Get Firestore client (ensures it's initialized)"""
        return get_firestore_client()

    @property
    def documents_collection(self):
        """Get documents collection reference (ensures db is initialized)"""
        return self.db.collection("documents")

    # ========================================
    # Ownership
    # ========================================

    def get_owned_snapshot(self, doc_id: str, user_id: str):
        """The document snapshot if it exists and belongs to the user, else None."""
        snapshot = self.documents_collection.document(doc_id).get()
        if not snapshot.exists or not user_owns_document(self.db, snapshot.to_dict() or {}, user_id):
            return None
        return snapshot

    # ========================================
    # Document CRUD Operations
    # ========================================

    def create_document(
        self,
        user_id: str,
        title: str,
        doc_type: str = Document.TYPE_HOMEWORK,
        course_id: Optional[str] = None,
    ) -> Document:
        """
        Create a new document owned by the student.

        Returns:
            Created Document instance with its Firestore id
        """
        now = datetime.utcnow()
        document = Document(
            user_id=user_id,
            title=title,
            type=doc_type,
            course_id=course_id,
            status=Document.STATUS_PENDING,
            page_count=0,
            created_at=now,
            updated_at=now,
        )
        doc_ref = self.documents_collection.add(document.to_dict())
        document.id = doc_ref[1].id
        logger.info(f"Document created: {document.id}")
        return document

    def get_document(self, doc_id: str) -> Optional[Document]:
        """Document by id (no ownership check; callers use get_owned_snapshot first)."""
        snapshot = self.documents_collection.document(doc_id).get()
        if not snapshot.exists:
            return None
        return Document.from_dict(doc_id, snapshot.to_dict())

    def update_document(
        self,
        doc_id: str,
        title: Optional[str] = None,
        status: Optional[str] = None,
        processing_progress: Optional[int] = None,
    ) -> Optional[Document]:
        """Update the given fields; returns the updated document or None if missing."""
        doc_ref = self.documents_collection.document(doc_id)
        if not doc_ref.get().exists:
            return None

        updates: Dict[str, Any] = {"updated_at": datetime.utcnow()}
        if title is not None:
            updates["title"] = title
        if status is not None:
            updates["status"] = status
        if processing_progress is not None:
            updates["processing_progress"] = processing_progress

        doc_ref.update(updates)
        return self.get_document(doc_id)

    def delete_document(self, doc_id: str, user_id: str, hard_delete: bool = False) -> bool:
        """
        Delete document (soft delete by default) and tell the student's other devices.
        """
        doc_ref = self.documents_collection.document(doc_id)
        snapshot = doc_ref.get()
        if not snapshot.exists:
            return False

        if hard_delete:
            for page in self.get_pages(doc_id):
                self.delete_page(doc_id, page.id, hard_delete=True)
            try:
                from app.services.firebase_storage_service import get_firebase_storage_service

                get_firebase_storage_service().delete_document_files(doc_id, owner_id=user_id)
            except Exception as e:
                logger.warning(f"Could not delete files for document {doc_id}: {e}")
            doc_ref.delete()
        else:
            now = datetime.utcnow()
            doc_ref.update({"is_deleted": True, "deleted_at": now, "updated_at": now})

        try:
            from app.services.data_change_notifier import notify_data_change

            notify_data_change(user_id=user_id, change_type="document_deleted", data={"document_id": doc_id})
        except Exception as e:
            logger.warning(f"Could not send the document_deleted push: {e}")

        return True

    def list_documents(
        self,
        user_id: str,
        doc_type: Optional[str] = None,
        include_deleted: bool = False,
        limit: int = 50,
    ) -> List[Document]:
        """
        The student's documents, newest first.
        Equality-only query (no composite index); filtered and sorted in Python.
        """
        documents = []
        for snapshot in self.documents_collection.where("user_id", "==", user_id).stream():
            data = snapshot.to_dict() or {}
            if doc_type and data.get("type") != doc_type:
                continue
            if not include_deleted and data.get("is_deleted", False):
                continue
            documents.append(Document.from_dict(snapshot.id, data))

        documents.sort(key=lambda d: _sort_key(d.created_at), reverse=True)
        return documents[:limit]

    def search_documents(self, user_id: str, query_text: str, limit: int = 20) -> List[Document]:
        """Case-insensitive title match over the student's documents."""
        query_lower = (query_text or "").lower()
        matches = [d for d in self.list_documents(user_id, limit=500) if query_lower in (d.title or "").lower()]
        return matches[:limit]

    # ========================================
    # Document Page Operations
    # ========================================

    def add_page(
        self,
        doc_id: str,
        image_data: bytes,
        page_number: int,
        width: int = 0,
        height: int = 0,
        owner_id: Optional[str] = None,
    ) -> Optional[DocumentPage]:
        """
        Add a page to a document. The image is stored privately under the owner's path:
        the document's user_id, or `owner_id` (the signed-in student, already checked
        by the API) for scans saved before the user_id backfill.
        """
        doc_ref = self.documents_collection.document(doc_id)
        snapshot = doc_ref.get()
        if not snapshot.exists:
            logger.warning(f"Document not found: {doc_id}")
            return None
        doc_data = snapshot.to_dict() or {}
        owner = doc_data.get("user_id") or owner_id
        if not owner:
            logger.warning(f"Document {doc_id} has no owner; page not added")
            return None

        page_ref = doc_ref.collection("pages").document()
        page_id = page_ref.id

        try:
            upload_result = self._upload_page_to_storage(doc_id, page_id, page_number, image_data, owner)

            if width == 0 or height == 0:
                try:
                    width, height = Image.open(io.BytesIO(image_data)).size
                except Exception as e:
                    logger.info(f"Could not read image dimensions: {e}")

            page = DocumentPage(
                id=page_id,
                document_id=doc_id,
                page_number=page_number,
                image_url=upload_result.get("image_url"),
                thumbnail_url=upload_result.get("thumbnail_url"),
                width=width,
                height=height,
                file_size=upload_result.get("file_size", len(image_data)),
                storage_path=upload_result.get("storage_path"),
                thumbnail_path=upload_result.get("thumbnail_path"),
                uploaded_at=datetime.utcnow(),
            )
            page_ref.set(page.to_dict())

            current_page_count = doc_data.get("page_count", 0)
            updates = {"page_count": current_page_count + 1, "updated_at": datetime.utcnow()}
            if current_page_count == 0:
                updates["thumbnail_url"] = upload_result.get("thumbnail_url")
                updates["thumbnail_path"] = upload_result.get("thumbnail_path")
            doc_ref.update(updates)

            return page
        except Exception as e:
            logger.error(f"Failed to add page: {e}")
            return None

    def add_page_from_storage(self, doc_id: str, storage_path: str, file_size: int = 0) -> Optional[DocumentPage]:
        """
        Register a page the app uploaded directly to Storage (signed upload URL).
        The page number is the next free one; dimensions are read from the file.
        """
        doc_ref = self.documents_collection.document(doc_id)
        snapshot = doc_ref.get()
        if not snapshot.exists:
            return None
        doc_data = snapshot.to_dict() or {}

        width = height = 0
        try:
            from app.services.firebase_storage_service import get_firebase_storage_service

            data = get_firebase_storage_service().download_file(storage_path)
            if data:
                width, height = Image.open(io.BytesIO(data)).size
                file_size = file_size or len(data)
        except Exception as e:
            logger.info(f"Could not read uploaded page {storage_path}: {e}")

        page_ref = doc_ref.collection("pages").document()
        page = DocumentPage(
            id=page_ref.id,
            document_id=doc_id,
            page_number=doc_data.get("page_count", 0) + 1,
            width=width,
            height=height,
            file_size=file_size,
            storage_path=storage_path,
            uploaded_at=datetime.utcnow(),
        )
        page_ref.set(page.to_dict())
        doc_ref.update({"page_count": page.page_number, "updated_at": datetime.utcnow()})
        return page

    def get_pages(self, doc_id: str) -> List[DocumentPage]:
        """Pages of a document ordered by page_number (deleted pages left out)."""
        pages_snapshot = self.documents_collection.document(doc_id).collection("pages").order_by("page_number").stream()
        return [
            DocumentPage.from_dict(p.id, data)
            for p in pages_snapshot
            if not (data := p.to_dict() or {}).get("is_deleted", False)
        ]

    def delete_page(self, doc_id: str, page_id: str, hard_delete: bool = False) -> bool:
        """Delete a page from a document."""
        doc_ref = self.documents_collection.document(doc_id)
        page_ref = doc_ref.collection("pages").document(page_id)
        page_snapshot = page_ref.get()
        if not page_snapshot.exists:
            return False

        if hard_delete:
            page_data = page_snapshot.to_dict() or {}
            for path in (page_data.get("storage_path"), page_data.get("thumbnail_path")):
                if path:
                    try:
                        self.upload_service.delete_file(path)
                    except Exception as e:
                        logger.warning(f"Failed to delete page file {path}: {e}")
            page_ref.delete()
        else:
            page_ref.update({"is_deleted": True, "deleted_at": datetime.utcnow()})

        doc_snapshot = doc_ref.get()
        if doc_snapshot.exists:
            current_page_count = (doc_snapshot.to_dict() or {}).get("page_count", 0)
            doc_ref.update({"page_count": max(0, current_page_count - 1), "updated_at": datetime.utcnow()})
        return True

    def reorder_pages(self, doc_id: str, page_order: List[str]) -> bool:
        """Set page_number from the given order of page ids."""
        doc_ref = self.documents_collection.document(doc_id)
        pages_collection = doc_ref.collection("pages")
        try:
            batch = self.db.batch()
            for index, page_id in enumerate(page_order):
                batch.update(pages_collection.document(page_id), {"page_number": index + 1})
            batch.commit()
            doc_ref.update({"updated_at": datetime.utcnow()})
            return True
        except Exception as e:
            logger.error(f"Failed to reorder pages: {e}")
            return False

    # ========================================
    # API dictionaries with signed URLs
    # ========================================

    def document_api_dict(self, document: Document) -> Dict[str, Any]:
        return document.to_api_dict(thumbnail_url=self.signed_url(document.thumbnail_path))

    def page_api_dict(self, page: DocumentPage) -> Dict[str, Any]:
        return page.to_api_dict(
            image_url=self.signed_url(page.storage_path) if not page.image_url else None,
            thumbnail_url=self.signed_url(page.thumbnail_path),
        )

    def signed_url(self, storage_path: Optional[str]) -> Optional[str]:
        """Short-lived read URL for a private Storage object (None when there is no path)."""
        if not storage_path or not storage_path.startswith("users/"):
            return None
        try:
            from app.services.firebase_service import get_storage_bucket

            return get_storage_bucket().blob(storage_path).generate_signed_url(
                expiration=timedelta(minutes=SIGNED_URL_MINUTES), method="GET"
            )
        except Exception as e:
            logger.warning(f"Could not sign {storage_path}: {e}")
            return None

    # ========================================
    # Sync Support Operations
    # ========================================

    def bulk_create_documents(self, user_id: str, documents_data: List[Dict[str, Any]]) -> List[Document]:
        """Create several documents for the student (sync from the app)."""
        created = []
        for doc_data in documents_data or []:
            try:
                created.append(
                    self.create_document(
                        user_id=user_id,
                        title=doc_data["title"],
                        doc_type=doc_data.get("type", Document.TYPE_HOMEWORK),
                        course_id=doc_data.get("course_id"),
                    )
                )
            except Exception as e:
                logger.warning(f"Failed to create document: {e}")
        return created

    def get_documents_modified_since(self, user_id: str, timestamp: datetime) -> List[Document]:
        """The student's documents updated after `timestamp` (incremental sync), oldest first."""
        changed = [
            d
            for d in self.list_documents(user_id, include_deleted=True, limit=10_000)
            if _sort_key(d.updated_at) > _sort_key(timestamp)
        ]
        changed.sort(key=lambda d: _sort_key(d.updated_at))
        return changed

    def cleanup_deleted_documents(self, days_old: int = 30) -> int:
        """Hard delete soft-deleted documents older than `days_old` days."""
        cutoff_date = datetime.utcnow() - timedelta(days=days_old)
        cleanup_count = 0
        query = self.documents_collection.where("is_deleted", "==", True).where("deleted_at", "<", cutoff_date)
        for snapshot in query.stream():
            owner = (snapshot.to_dict() or {}).get("user_id")
            if self.delete_document(snapshot.id, owner, hard_delete=True):
                cleanup_count += 1
        return cleanup_count

    # ========================================
    # Private Helper Methods
    # ========================================

    def _upload_page_to_storage(
        self, doc_id: str, page_id: str, page_number: int, image_data: bytes, owner_id: str
    ) -> Dict[str, Any]:
        """
        Upload a page image (Firebase Storage, private under the owner's path;
        local file storage in development without Firebase).
        """
        if getattr(self.upload_service, "_use_firebase", False):
            from app.services.firebase_storage_service import get_firebase_storage_service

            result = get_firebase_storage_service().upload_document_page(
                doc_id=doc_id,
                page_id=page_id,
                page_number=page_number,
                image_data=image_data,
                generate_thumbnail=True,
                owner_id=owner_id,
            )
            if not result.get("success"):
                raise Exception(f"Firebase Storage upload failed: {result.get('error')}")
            return result
        return self._upload_page_to_local(doc_id, page_id, page_number, image_data)

    def _upload_page_to_local(
        self, doc_id: str, page_id: str, page_number: int, image_data: bytes
    ) -> Dict[str, Any]:
        """Save a page to local file storage (development without Firebase)."""
        import os

        from flask import request

        doc_folder = os.path.join(self.upload_service.general_uploads_folder, "documents", doc_id, "pages")
        thumb_folder = os.path.join(doc_folder, "thumbnails")
        os.makedirs(thumb_folder, exist_ok=True)

        image_filename = f"{page_id}_{page_number:03d}.jpg"
        thumb_filename = f"{page_id}_{page_number:03d}_thumb.jpg"
        with open(os.path.join(doc_folder, image_filename), "wb") as f:
            f.write(image_data)
        with open(os.path.join(thumb_folder, thumb_filename), "wb") as f:
            f.write(self._generate_thumbnail(image_data, size=(300, 300)))

        base_url = request.host_url.rstrip("/")
        return {
            "image_url": f"{base_url}/uploads/general/documents/{doc_id}/pages/{image_filename}",
            "thumbnail_url": f"{base_url}/uploads/general/documents/{doc_id}/pages/thumbnails/{thumb_filename}",
            "storage_path": f"documents/{doc_id}/pages/{image_filename}",
            "file_size": len(image_data),
        }

    def _generate_thumbnail(self, image_data: bytes, size: Tuple[int, int]) -> bytes:
        """JPEG thumbnail of the image."""
        img = Image.open(io.BytesIO(image_data))
        img.thumbnail(size, Image.Resampling.LANCZOS)
        output = io.BytesIO()
        img.convert("RGB").save(output, format="JPEG", quality=85)
        return output.getvalue()


def _sort_key(value) -> str:
    """Comparable key for datetimes stored as datetime or ISO strings."""
    if value is None:
        return ""
    if hasattr(value, "isoformat"):
        return value.replace(tzinfo=None).isoformat() if getattr(value, "tzinfo", None) else value.isoformat()
    return str(value)
