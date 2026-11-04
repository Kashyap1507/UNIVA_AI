"""
Tutor chat API.

    /api/courses/<course_id>/tutor/sessions   GET (list), POST (create)
    /api/tutor/sessions/<session_id>          GET (session + messages), DELETE
    /api/tutor/sessions/<session_id>/messages POST {"content": "..."}
        default: text/event-stream with events start, sources, delta, done | error
        ?stream=false: one JSON response with the final message
"""

import json
import logging

from flask import Response, request, stream_with_context
from flask_restful import Resource

from app.api.courses_api import handle_errors
from app.services import tutor_service
from app.services.course_service import ServiceError
from app.utils.decorators import get_current_user_id, jwt_required, rate_limit

logger = logging.getLogger(__name__)


def sse_format(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


class TutorSessionsAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, course_id, current_user=None):
        sessions = tutor_service.list_sessions(get_current_user_id(current_user), course_id)
        return {"success": True, "data": sessions}, 200

    @jwt_required
    @handle_errors
    def post(self, course_id, current_user=None):
        session = tutor_service.create_session(get_current_user_id(current_user), course_id)
        return {"success": True, "data": session}, 201


class TutorSessionDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, session_id, current_user=None):
        uid = get_current_user_id(current_user)
        session = tutor_service.get_session(uid, session_id)
        return {
            "success": True,
            "data": {
                "session": tutor_service.session_response(session_id, session),
                "messages": tutor_service.get_messages(uid, session_id),
            },
        }, 200

    @jwt_required
    @handle_errors
    def delete(self, session_id, current_user=None):
        tutor_service.delete_session(get_current_user_id(current_user), session_id)
        return {"success": True}, 200


class TutorMessagesAPI(Resource):
    @jwt_required
    @rate_limit(30, 60)
    @handle_errors
    def post(self, session_id, current_user=None):
        data = request.get_json(silent=True)
        if not isinstance(data, dict):
            raise ServiceError("Request body must be a JSON object")
        content = data.get("content")
        if not isinstance(content, str):
            raise ServiceError("content must be a string")

        # Validation (ownership, length) happens here, before any streaming starts
        events = tutor_service.ask(get_current_user_id(current_user), session_id, content)

        if request.args.get("stream", "true").lower() == "false":
            final = None
            for event in events:
                if event["event"] in ("done", "error"):
                    final = event
            if final and final["event"] == "done":
                return {"success": True, "data": final["data"]}, 200
            message = final["data"]["message"] if final else "No answer was produced"
            return {"success": False, "error": message}, 502

        def generate():
            try:
                for event in events:
                    yield sse_format(event["event"], event["data"])
            except Exception as e:
                logger.error(f"Tutor stream failed: {e}", exc_info=True)
                yield sse_format("error", {"message": "The tutor couldn't answer right now. Please try again."})

        return Response(
            stream_with_context(generate()),
            mimetype="text/event-stream",
            headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        )
