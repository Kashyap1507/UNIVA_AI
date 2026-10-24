"""
document_ingestion: course material → searchable chunks.

    download from Storage → extract (PDF / PPTX / text / Gemini OCR)
    → chunk (1000 chars, 200 overlap) → embed → material_chunks (Vector)

Runs in a small background thread pool so uploads return immediately. The
material doc moves PENDING → PROCESSING → READY | FAILED. Re-ingesting a
material replaces its chunks. Each run is logged to ai_workflow_logs. After a
successful background run the course outline is rebuilt (see
course_structure_generation.request_generation).
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from typing import Callable, List, Optional

from google.cloud.firestore_v1.vector import Vector

from app.models.course import MATERIAL_CHUNKS, CourseMaterial
from app.services.material_extraction import (
    ExtractionError,
    chunk_segments,
    extract_segments,
)

logger = logging.getLogger(__name__)

WORKFLOW_TYPE = "document_ingestion"
WRITE_BATCH_SIZE = 200  # Firestore allows 500 writes per batch; vectors make docs ~7 KB
MAX_CHUNKS_PER_MATERIAL = 2000

_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="ingestion")

_DEFAULT = object()  # "use the real implementation" (None for OCR means "no OCR")


class IngestionDeps:
    """Seams for tests: storage download, OCR text generation and embeddings."""

    def __init__(
        self,
        download: Optional[Callable[[str], bytes]] = None,
        ocr=_DEFAULT,
        embed: Optional[Callable[[List[str]], List[List[float]]]] = None,
        db=None,
    ):
        self._download = download
        self._ocr = ocr
        self._embed = embed
        self._db = db

    @property
    def db(self):
        if self._db is None:
            from app.services.firebase_service import get_firestore_client

            self._db = get_firestore_client()
        return self._db

    def download(self, storage_path: str) -> bytes:
        if self._download:
            return self._download(storage_path)
        from app.services.firebase_service import get_storage_bucket

        return get_storage_bucket().blob(storage_path).download_as_bytes()

    def ocr(self) -> Optional[Callable[..., str]]:
        if self._ocr is not _DEFAULT:
            return self._ocr
        try:
            from app.ai_framework.utils.model_router import client_for

            return client_for(WORKFLOW_TYPE).generate_raw
        except Exception as e:  # no API key etc.
            logger.warning(f"OCR unavailable: {e}")
            return None

    def embed(self, texts: List[str]) -> List[List[float]]:
        if self._embed:
            return self._embed(texts)
        from app.ai_framework.utils.embedding_client import EmbeddingClientFactory

        return EmbeddingClientFactory.get_client().embed_batch(texts)


def _set_status(db, material_id: str, **fields):
    fields["updated_at"] = datetime.now()
    db.collection(CourseMaterial.collection_name).document(material_id).update(fields)


def _delete_existing_chunks(db, material: CourseMaterial):
    from google.cloud.firestore_v1.base_query import FieldFilter

    query = (
        db.collection(MATERIAL_CHUNKS)
        .where(filter=FieldFilter("user_id", "==", material.user_id))
        .where(filter=FieldFilter("material_id", "==", material.id))
    )
    for doc in query.stream():
        doc.reference.delete()


def _write_chunks(db, material: CourseMaterial, chunks, vectors):
    for start in range(0, len(chunks), WRITE_BATCH_SIZE):
        batch = db.batch() if hasattr(db, "batch") else None
        for chunk, vector in zip(chunks[start : start + WRITE_BATCH_SIZE], vectors[start : start + WRITE_BATCH_SIZE]):
            ref = db.collection(MATERIAL_CHUNKS).document(f"{material.id}_{chunk.index:05d}")
            data = {
                "user_id": material.user_id,
                "course_id": material.course_id,
                "material_id": material.id,
                "material_title": material.title,
                "material_kind": material.kind,
                "chunk_index": chunk.index,
                "text": chunk.text,
                "page": chunk.page,
                "page_end": chunk.page_end,
                "embedding": Vector(vector),
                "created_at": datetime.now(),
            }
            if batch is not None:
                batch.set(ref, data)
            else:
                ref.set(data)
        if batch is not None:
            batch.commit()


def _log(material: CourseMaterial, success: bool, started: float, chunk_count: int, pages: int, error: Optional[str]):
    try:
        from app.ai_framework.utils.embedding_client import configured_embedding_model
        from app.services.ai_workflow_logger import AIWorkflowLogger

        AIWorkflowLogger().log_workflow(
            workflow_type=WORKFLOW_TYPE,
            model=configured_embedding_model(),
            prompt={"template_version": "1.0"},
            input_data={
                "parameters": {
                    "material_id": material.id,
                    "course_id": material.course_id,
                    "kind": material.kind,
                    "mime": material.mime,
                    "size_bytes": material.size_bytes,
                }
            },
            response={"success": success, "error_message": error},
            metrics={
                "latency_ms": int((time.time() - started) * 1000),
                "output_count": chunk_count,
                "pages": pages,
            },
            user_id=material.user_id,
        )
    except Exception as e:
        logger.warning(f"Could not log ingestion run: {e}")


def ingest_material(material_id: str, deps: Optional[IngestionDeps] = None) -> dict:
    """Run the whole pipeline for one material. Never raises; returns a summary."""
    deps = deps or IngestionDeps()
    db = deps.db
    started = time.time()

    material = CourseMaterial.get_by_id(db, material_id)
    if not material or material.is_deleted:
        return {"success": False, "error": "Material not found"}
    if not material.storage_path:
        _set_status(db, material_id, ingestion_status="FAILED", error="File is missing")
        return {"success": False, "error": "File is missing"}

    _set_status(db, material_id, ingestion_status="PROCESSING", error=None)
    pages = 0
    try:
        data = deps.download(material.storage_path)
        segments = extract_segments(data, material.mime, ocr=deps.ocr())
        pages = max((s.page or 0) for s in segments) if segments else 0
        chunks = chunk_segments(segments)
        if not chunks:
            raise ExtractionError("No readable text found in this file")
        if len(chunks) > MAX_CHUNKS_PER_MATERIAL:
            raise ExtractionError(
                f"This file is too long to index ({len(chunks)} sections, max {MAX_CHUNKS_PER_MATERIAL})"
            )

        vectors = deps.embed([c.text for c in chunks])
        if len(vectors) != len(chunks):
            raise RuntimeError("Embedding count did not match chunk count")

        _delete_existing_chunks(db, material)
        _write_chunks(db, material, chunks, vectors)
        _set_status(
            db,
            material_id,
            ingestion_status="READY",
            chunk_count=len(chunks),
            pages=pages or None,
            error=None,
        )
        _log(material, True, started, len(chunks), pages, None)
        logger.info(f"Ingested material {material_id}: {len(chunks)} chunks")
        return {
            "success": True,
            "chunk_count": len(chunks),
            "pages": pages,
            "user_id": material.user_id,
            "course_id": material.course_id,
        }
    except ExtractionError as e:
        message = str(e)
    except Exception as e:
        logger.error(f"Ingestion failed for {material_id}: {e}", exc_info=True)
        message = "Processing failed. Please try again later."
    _set_status(db, material_id, ingestion_status="FAILED", error=message)
    _log(material, False, started, 0, pages, message)
    return {"success": False, "error": message}


def ingest_and_refresh_outline(material_id: str, deps: Optional[IngestionDeps] = None, refresh=None) -> dict:
    """Ingest, then rebuild the course outline so the new material shows up in it."""
    result = ingest_material(material_id, deps)
    if result.get("success"):
        try:
            if refresh is None:
                from app.ai_framework.workflows.course_structure_generation import request_generation as refresh
            refresh(result["user_id"], result["course_id"])
        except Exception as e:
            logger.warning(f"Could not refresh the outline after ingesting {material_id}: {e}")
    return result


def ingest_material_async(material_id: str):
    """Queue ingestion (and the outline refresh after it) on the background pool."""
    _executor.submit(ingest_and_refresh_outline, material_id)
