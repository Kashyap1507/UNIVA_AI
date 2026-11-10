"""
"Check my work" submissions (work_submissions collection).

    user_id, course_id, document_id, is_practice, mode (HINTS|FULL_SOLUTION),
    status (PENDING|PROCESSING|READY|FAILED), step, error,
    feedback{mode, items[], summary, verified, verifier_notes}, created/updated_at

A submission runs in the background; the SSE endpoint watches its `step`
and `status` fields. Solutions are only ever stored for practice work.
"""

import base64
import logging
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from typing import Callable, Dict, List, Optional, Tuple

from google.cloud.firestore_v1.base_query import FieldFilter

from app.ai_framework.workflows import work_feedback
from app.services import course_service, retrieval_service
from app.services.course_service import ServiceError
from app.services.document_access import user_owns_document
from app.services.firebase_service import get_firestore_client

logger = logging.getLogger(__name__)

SUBMISSIONS = "work_submissions"
_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="work-feedback")


def _iso(value):
    return value.isoformat() if hasattr(value, "isoformat") else value


def submission_response(doc_id: str, data: Dict) -> Dict:
    return {
        "id": doc_id,
        "course_id": data.get("course_id"),
        "document_id": data.get("document_id"),
        "is_practice": data.get("is_practice", False),
        "mode": data.get("mode"),
        "status": data.get("status"),
        "step": data.get("step"),
        "error": data.get("error"),
        "feedback": data.get("feedback"),
        "title": data.get("title"),
        "created_at": _iso(data.get("created_at")),
        "updated_at": _iso(data.get("updated_at")),
    }


def _ref(submission_id: str):
    return get_firestore_client().collection(SUBMISSIONS).document(submission_id)


def get_submission(user_id: str, submission_id: str) -> Dict:
    if not submission_id:
        raise ServiceError("Submission not found", 404)
    snap = _ref(submission_id).get()
    data = snap.to_dict() if snap.exists else None
    if not data or data.get("user_id") != user_id:
        raise ServiceError("Submission not found", 404)
    return data


def list_submissions(user_id: str, course_id: Optional[str] = None) -> List[Dict]:
    query = get_firestore_client().collection(SUBMISSIONS).where(filter=FieldFilter("user_id", "==", user_id))
    if course_id:
        course_service.get_course(user_id, course_id)
        query = query.where(filter=FieldFilter("course_id", "==", course_id))
    items = [(d.id, d.to_dict()) for d in query.stream()]
    items.sort(key=lambda i: i[1].get("created_at") or datetime.min, reverse=True)
    return [submission_response(i, d) for i, d in items]


def create_submission(
    user_id: str,
    document_id: str,
    course_id: str,
    is_practice: bool,
    start: Optional[Callable[[str], None]] = None,
) -> Dict:
    if not isinstance(is_practice, bool):
        raise ServiceError("is_practice must be true or false")
    course = course_service.get_course(user_id, course_id)
    db = get_firestore_client()
    if not document_id or not isinstance(document_id, str):
        raise ServiceError("document_id is required")
    doc_snap = db.collection("documents").document(document_id).get()
    doc = doc_snap.to_dict() if doc_snap.exists else None
    if not doc or doc.get("is_deleted") or not user_owns_document(db, doc, user_id):
        raise ServiceError("Document not found", 404)

    now = datetime.now()
    ref = db.collection(SUBMISSIONS).document()
    data = {
        "user_id": user_id,
        "course_id": course_id,
        "document_id": document_id,
        "is_practice": is_practice,
        "mode": work_feedback.FULL_SOLUTION if is_practice else work_feedback.HINTS,
        "status": "PENDING",
        "step": "queued",
        "error": None,
        "feedback": None,
        "title": doc.get("title") or course.name,
        "created_at": now,
        "updated_at": now,
    }
    ref.set(data)
    # Link the scan to the course (plan: documents gain user_id + course_id)
    db.collection("documents").document(document_id).update({"course_id": course_id, "user_id": user_id})

    from app.services import study_activity_service

    study_activity_service.record(user_id, "work")
    (start or (lambda sid: _executor.submit(run_submission, sid)))(ref.id)
    return submission_response(ref.id, data)


# --- running -------------------------------------------------------------------------


def _load_page_images(db, document_id: str) -> List[Tuple[str, str]]:
    pages = [p.to_dict() for p in db.collection("documents").document(document_id).collection("pages").stream()]
    pages.sort(key=lambda p: p.get("page_number", 0))
    images = []
    for page in pages[: work_feedback.MAX_PAGES]:
        data = None
        if page.get("storage_path"):
            try:
                from app.services.firebase_service import get_storage_bucket

                data = get_storage_bucket().blob(page["storage_path"]).download_as_bytes()
            except Exception as e:
                logger.warning(f"Could not read page {page.get('storage_path')}: {e}")
        if data is None and page.get("image_url"):
            import requests

            response = requests.get(page["image_url"], timeout=30)
            response.raise_for_status()
            data = response.content
        if data:
            mime = "image/png" if data[:8] == b"\x89PNG\r\n\x1a\n" else "image/jpeg"
            images.append((base64.b64encode(data).decode("ascii"), mime))
    return images


def _course_context(user_id: str, course_id: str, title: str) -> str:
    try:
        chunks = retrieval_service.search(user_id, course_id, title or "worked problems", k=4)
    except Exception as e:
        logger.info(f"No course context for work feedback: {e}")
        return ""
    return "\n\n".join(f"[{c.material_title}{', p. ' + str(c.page) if c.page else ''}]\n{c.text[:1200]}" for c in chunks)


def run_submission(
    submission_id: str,
    load_images: Optional[Callable] = None,
    context: Optional[Callable[[str, str, str], str]] = None,
    calls: Optional[work_feedback.ModelCalls] = None,
) -> None:
    """Run the pipeline for a submission and store the outcome. Never raises."""
    db = get_firestore_client()
    ref = db.collection(SUBMISSIONS).document(submission_id)
    snap = ref.get()
    if not snap.exists:
        return
    sub = snap.to_dict()
    started = time.time()

    def update(**fields):
        fields["updated_at"] = datetime.now()
        ref.update(fields)

    update(status="PROCESSING", step="loading")
    try:
        images = (load_images or _load_page_images)(db, sub["document_id"])
        if not images:
            raise ValueError("no_images")
        course = course_service.get_course(sub["user_id"], sub["course_id"])
        user = db.collection("users").document(sub["user_id"]).get().to_dict() or {}
        degree = (user.get("student_profile") or {}).get("degree_level")
        ctx = (context or _course_context)(sub["user_id"], sub["course_id"], sub.get("title") or "")

        result = None
        for event in work_feedback.run(images, sub["is_practice"], course.name, degree, ctx, calls=calls):
            if "step" in event:
                update(step=event["step"])
            else:
                result = event["result"]
        update(status="READY", step="done", feedback=result, error=None)
        _log(sub, True, started, len(result["items"]), None)
    except Exception as e:
        logger.error(f"work_feedback failed for {submission_id}: {e}", exc_info=True)
        message = (
            "The scan has no readable pages. Please scan your work again."
            if str(e) == "no_images"
            else "Couldn't check this work right now. Please try again later."
        )
        update(status="FAILED", step="done", error=message)
        _log(sub, False, started, 0, message)


def _log(sub: Dict, success: bool, started: float, items: int, error: Optional[str]):
    try:
        from app.ai_framework.utils.model_router import model_for
        from app.services.ai_workflow_logger import AIWorkflowLogger

        AIWorkflowLogger().log_workflow(
            workflow_type=work_feedback.TASK_EVALUATE,
            model=model_for(work_feedback.TASK_EVALUATE),
            prompt={"template_version": "1.0"},
            input_data={"parameters": {"course_id": sub.get("course_id"), "mode": sub.get("mode")}},
            response={"success": success, "error_message": error},
            metrics={"latency_ms": int((time.time() - started) * 1000), "output_count": items},
            user_id=sub.get("user_id"),
        )
    except Exception as e:
        logger.warning(f"Could not log work feedback: {e}")
