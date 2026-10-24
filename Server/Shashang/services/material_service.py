"""
Course materials: upload to private Storage, list, download link, delete and
(re)ingest. Files live under users/{uid}/courses/{course_id}/materials/{id}/.
"""

import logging
import re
from datetime import datetime, timedelta
from typing import Callable, List, Optional

from app.models.course import MATERIAL_KINDS, CourseMaterial
from app.services import course_service
from app.services.course_service import ServiceError
from app.services.material_extraction import mime_for_filename

logger = logging.getLogger(__name__)

MAX_UPLOAD_BYTES = 50 * 1024 * 1024
MAX_TITLE = 200
SIGNED_URL_MINUTES = 15


def storage_path_for(user_id: str, course_id: str, material_id: str, filename: str) -> str:
    safe = re.sub(r"[^A-Za-z0-9._-]+", "_", filename or "file").strip("._") or "file"
    return f"users/{user_id}/courses/{course_id}/materials/{material_id}/{safe[:100]}"


def _default_kind(mime: str) -> str:
    if mime.startswith("image/"):
        return "SCAN"
    if "presentation" in mime:
        return "SLIDES"
    if mime.startswith("text/"):
        return "NOTES"
    return "PDF"


def _upload_blob(storage_path: str, data: bytes, mime: str):
    from app.services.firebase_service import get_storage_bucket

    blob = get_storage_bucket().blob(storage_path)
    blob.upload_from_string(data, content_type=mime)  # private: no make_public()


def _delete_blob(storage_path: str):
    try:
        from app.services.firebase_service import get_storage_bucket

        blob = get_storage_bucket().blob(storage_path)
        if blob.exists():
            blob.delete()
    except Exception as e:
        logger.warning(f"Could not delete blob {storage_path}: {e}")


def create_material(
    user_id: str,
    course_id: str,
    filename: str,
    data: bytes,
    declared_mime: Optional[str] = None,
    kind: Optional[str] = None,
    title: Optional[str] = None,
    upload: Optional[Callable[[str, bytes, str], None]] = None,
    start_ingestion: Optional[Callable[[str], None]] = None,
) -> CourseMaterial:
    course_service.get_course(user_id, course_id)
    upload = upload or _upload_blob

    if not data:
        raise ServiceError("The file is empty")
    if len(data) > MAX_UPLOAD_BYTES:
        raise ServiceError(f"Files must be {MAX_UPLOAD_BYTES // (1024 * 1024)} MB or smaller", 413)
    mime = mime_for_filename(filename, declared_mime)
    if not mime:
        raise ServiceError("Unsupported file type. Upload a PDF, PowerPoint (.pptx), text, Markdown, JPEG or PNG file.")
    if kind is not None and kind not in MATERIAL_KINDS:
        raise ServiceError(f"kind must be one of: {', '.join(MATERIAL_KINDS)}")
    if title is not None and (not isinstance(title, str) or len(title.strip()) > MAX_TITLE):
        raise ServiceError(f"title must be at most {MAX_TITLE} characters")

    from app.services.firebase_service import get_firestore_client

    db = get_firestore_client()
    material = CourseMaterial(
        user_id=user_id,
        course_id=course_id,
        kind=kind or _default_kind(mime),
        title=(title or "").strip() or (filename or "Untitled")[:MAX_TITLE],
        mime=mime,
        size_bytes=len(data),
        ingestion_status="PENDING",
    )
    material.save(db)

    path = storage_path_for(user_id, course_id, material.id, filename)
    try:
        upload(path, data, mime)
    except Exception as e:
        logger.error(f"Upload failed for material {material.id}: {e}")
        db.collection(CourseMaterial.collection_name).document(material.id).update(
            {"ingestion_status": "FAILED", "error": "Upload failed", "is_deleted": True}
        )
        raise ServiceError("Upload failed. Please try again.", 502)
    db.collection(CourseMaterial.collection_name).document(material.id).update(
        {"storage_path": path, "updated_at": datetime.now()}
    )

    if start_ingestion is None:
        from app.ai_framework.workflows.document_ingestion import ingest_material_async

        start_ingestion = ingest_material_async
    start_ingestion(material.id)
    return CourseMaterial.get_by_id(db, material.id)


def list_materials(user_id: str, course_id: str) -> List[CourseMaterial]:
    course_service.get_course(user_id, course_id)
    materials = course_service._list_owned(CourseMaterial, user_id, course_id=course_id)
    return sorted(materials, key=lambda m: m.created_at or datetime.min, reverse=True)


def get_material(user_id: str, material_id: str) -> CourseMaterial:
    return course_service._get_owned(CourseMaterial, material_id, user_id, "Material")


def download_url(material: CourseMaterial) -> Optional[str]:
    if not material.storage_path:
        return None
    try:
        from app.services.firebase_service import get_storage_bucket

        return get_storage_bucket().blob(material.storage_path).generate_signed_url(
            version="v4", expiration=timedelta(minutes=SIGNED_URL_MINUTES), method="GET"
        )
    except Exception as e:
        logger.warning(f"Could not sign URL for material {material.id}: {e}")
        return None


def delete_material(user_id: str, material_id: str, delete_file: Optional[Callable[[str], None]] = None):
    delete_file = delete_file or _delete_blob
    material = get_material(user_id, material_id)
    course_service._update(CourseMaterial, material_id, {"is_deleted": True})
    course_service.delete_chunks(user_id, material_id=material_id)
    if material.storage_path:
        delete_file(material.storage_path)


def reingest_material(user_id: str, material_id: str, start_ingestion: Optional[Callable[[str], None]] = None) -> CourseMaterial:
    material = get_material(user_id, material_id)
    if material.ingestion_status == "PROCESSING":
        raise ServiceError("This file is already being processed", 409)
    course_service._update(CourseMaterial, material_id, {"ingestion_status": "PENDING", "error": None})
    if start_ingestion is None:
        from app.ai_framework.workflows.document_ingestion import ingest_material_async

        start_ingestion = ingest_material_async
    start_ingestion(material_id)
    return get_material(user_id, material_id)
