"""
Tutor sessions and messages.

    tutor_sessions/{sid}                 user_id, course_id, title, message_count,
                                         last_message_at, is_deleted, created/updated_at
    tutor_sessions/{sid}/messages/{mid}  role (user|assistant), content (markdown),
                                         citations[], grounded, model, created_at

`ask()` runs one tutor turn and yields events for the SSE endpoint:
start → sources → delta* → done (or error).
"""

import logging
import time
from datetime import datetime
from typing import Callable, Dict, Iterator, List, Optional

from google.cloud.firestore_v1.base_query import FieldFilter

from app.ai_framework.workflows import tutor_chat
from app.services import course_service, retrieval_service
from app.services.course_service import ServiceError
from app.services.firebase_service import get_firestore_client

logger = logging.getLogger(__name__)

SESSIONS = "tutor_sessions"
MESSAGES = "messages"
MAX_QUESTION_CHARS = 4000
MAX_TITLE_CHARS = 60
MAX_MESSAGES_PER_SESSION = 400


def _iso(value):
    return value.isoformat() if hasattr(value, "isoformat") else value


def session_response(doc_id: str, data: Dict) -> Dict:
    return {
        "id": doc_id,
        "course_id": data.get("course_id"),
        "title": data.get("title") or "New conversation",
        "message_count": data.get("message_count", 0),
        "last_message_at": _iso(data.get("last_message_at")),
        "created_at": _iso(data.get("created_at")),
        "updated_at": _iso(data.get("updated_at")),
    }


def message_response(doc_id: str, data: Dict) -> Dict:
    return {
        "id": doc_id,
        "role": data.get("role"),
        "content": data.get("content", ""),
        "citations": data.get("citations", []),
        "grounded": data.get("grounded", True),
        "created_at": _iso(data.get("created_at")),
    }


# --- sessions ---------------------------------------------------------------------


def _session_ref(session_id: str):
    return get_firestore_client().collection(SESSIONS).document(session_id)


def get_session(user_id: str, session_id: str) -> Dict:
    if not session_id:
        raise ServiceError("Conversation not found", 404)
    snap = _session_ref(session_id).get()
    data = snap.to_dict() if snap.exists else None
    if not data or data.get("user_id") != user_id or data.get("is_deleted"):
        raise ServiceError("Conversation not found", 404)
    return data


def create_session(user_id: str, course_id: str) -> Dict:
    course_service.get_course(user_id, course_id)
    now = datetime.now()
    ref = get_firestore_client().collection(SESSIONS).document()
    data = {
        "user_id": user_id,
        "course_id": course_id,
        "title": None,
        "message_count": 0,
        "last_message_at": None,
        "is_deleted": False,
        "created_at": now,
        "updated_at": now,
    }
    ref.set(data)
    return session_response(ref.id, data)


def list_sessions(user_id: str, course_id: str) -> List[Dict]:
    course_service.get_course(user_id, course_id)
    docs = (
        get_firestore_client()
        .collection(SESSIONS)
        .where(filter=FieldFilter("user_id", "==", user_id))
        .where(filter=FieldFilter("course_id", "==", course_id))
        .stream()
    )
    sessions = [(d.id, d.to_dict()) for d in docs]
    sessions = [(i, d) for i, d in sessions if not d.get("is_deleted")]
    sessions.sort(key=lambda s: s[1].get("updated_at") or datetime.min, reverse=True)
    return [session_response(i, d) for i, d in sessions]


def get_messages(user_id: str, session_id: str) -> List[Dict]:
    get_session(user_id, session_id)
    docs = _session_ref(session_id).collection(MESSAGES).stream()
    messages = [(d.id, d.to_dict()) for d in docs]
    messages.sort(key=lambda m: (m[1].get("seq", 0), m[1].get("created_at") or datetime.min))
    return [message_response(i, d) for i, d in messages]


def delete_session(user_id: str, session_id: str):
    get_session(user_id, session_id)
    _session_ref(session_id).update({"is_deleted": True, "updated_at": datetime.now()})


# --- asking -------------------------------------------------------------------------


def _save_message(session_id: str, seq: int, role: str, content: str, **extra) -> str:
    ref = _session_ref(session_id).collection(MESSAGES).document()
    ref.set({"role": role, "content": content, "seq": seq, "created_at": datetime.now(), **extra})
    return ref.id


def _default_retrieve(user_id: str, course_id: str, query: str):
    return retrieval_service.search(user_id, course_id, query, k=tutor_chat.RETRIEVE_K)


def ask(
    user_id: str,
    session_id: str,
    question: str,
    retrieve: Optional[Callable[[str, str, str], list]] = None,
    stream: Optional[Callable[[str], Iterator[str]]] = None,
) -> Iterator[Dict]:
    """Validate, then yield tutor events. Validation errors raise before streaming starts."""
    question = (question or "").strip()
    if not question:
        raise ServiceError("Ask a question first")
    if len(question) > MAX_QUESTION_CHARS:
        raise ServiceError(f"Questions must be at most {MAX_QUESTION_CHARS} characters")
    session = get_session(user_id, session_id)
    course = course_service.get_course(user_id, session["course_id"])
    if session.get("message_count", 0) >= MAX_MESSAGES_PER_SESSION:
        raise ServiceError("This conversation is full. Start a new one.", 409)

    retrieve = retrieve or _default_retrieve
    from app.services import study_activity_service

    study_activity_service.record(user_id, "tutor")
    return _ask_events(user_id, session_id, session, course, question, retrieve, stream)


def _ask_events(user_id, session_id, session, course, question, retrieve, stream) -> Iterator[Dict]:
    started = time.time()
    history = get_messages(user_id, session_id)
    seq = len(history)
    user_message_id = _save_message(session_id, seq, "user", question)
    yield {"event": "start", "data": {"session_id": session_id, "user_message_id": user_message_id}}

    try:
        chunks = retrieve(user_id, course.id, question)
    except Exception as e:
        logger.warning(f"Tutor retrieval failed for course {course.id}: {e}")
        chunks = []

    user_doc = get_firestore_client().collection("users").document(user_id).get().to_dict() or {}
    degree = (user_doc.get("student_profile") or {}).get("degree_level")

    answer = None
    error = None
    try:
        kwargs = {"stream": stream} if stream else {}
        for event in tutor_chat.run(question, chunks, history, course.name, degree, **kwargs):
            if event["type"] == "sources":
                yield {"event": "sources", "data": {"grounded": event["grounded"], "count": event["count"]}}
            elif event["type"] == "delta":
                yield {"event": "delta", "data": {"text": event["text"]}}
            elif event["type"] == "final":
                answer = event["answer"]
    except Exception as e:
        logger.error(f"Tutor generation failed for session {session_id}: {e}", exc_info=True)
        error = "The tutor couldn't answer right now. Please try again."

    now = datetime.now()
    updates = {"message_count": seq + 1, "last_message_at": now, "updated_at": now}
    if not session.get("title"):
        updates["title"] = question[:MAX_TITLE_CHARS] + ("…" if len(question) > MAX_TITLE_CHARS else "")

    if answer is None:
        _session_ref(session_id).update(updates)
        _log(user_id, course.id, False, started, 0, False, error)
        yield {"event": "error", "data": {"message": error or "No answer was produced"}}
        return

    from app.ai_framework.utils.model_router import model_for

    message_id = _save_message(
        session_id,
        seq + 1,
        "assistant",
        answer.content,
        citations=answer.citations,
        grounded=answer.grounded,
        model=model_for(tutor_chat.TASK),
    )
    updates["message_count"] = seq + 2
    _session_ref(session_id).update(updates)
    _log(user_id, course.id, True, started, len(answer.citations), answer.grounded, None)
    yield {
        "event": "done",
        "data": {
            "message": {
                "id": message_id,
                "role": "assistant",
                "content": answer.content,
                "citations": answer.citations,
                "grounded": answer.grounded,
            },
            "session": {"id": session_id, "title": updates.get("title") or session.get("title")},
        },
    }


def _log(user_id, course_id, success, started, citations, grounded, error):
    try:
        from app.ai_framework.utils.model_router import model_for
        from app.services.ai_workflow_logger import AIWorkflowLogger

        AIWorkflowLogger().log_workflow(
            workflow_type=tutor_chat.TASK,
            model=model_for(tutor_chat.TASK),
            prompt={"template_version": "1.0"},
            input_data={"parameters": {"course_id": course_id}},
            response={"success": success, "error_message": error},
            metrics={
                "latency_ms": int((time.time() - started) * 1000),
                "output_count": citations,
                "grounded": grounded,
            },
            user_id=user_id,
        )
    except Exception as e:
        logger.warning(f"Could not log tutor turn: {e}")
