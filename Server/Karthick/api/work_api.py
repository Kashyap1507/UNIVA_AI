"""
"Check my work" API.

    /api/work/submissions              POST {document_id, course_id, is_practice}, GET (?course_id)
    /api/work/submissions/<id>         GET
    /api/work/submissions/<id>/stream  GET text/event-stream: step* then done | error
"""

import os
import time

from flask import Response, request, stream_with_context
from flask_restful import Resource

from app.api.courses_api import handle_errors
from app.api.tutor_api import sse_format
from app.services import work_service
from app.services.course_service import ServiceError
from app.utils.decorators import get_current_user_id, jwt_required, rate_limit

POLL_SECONDS = float(os.getenv("WORK_STREAM_POLL_SECONDS", "0.8"))
STREAM_TIMEOUT_SECONDS = 300


class WorkSubmissionsAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        items = work_service.list_submissions(get_current_user_id(current_user), request.args.get("course_id") or None)
        return {"success": True, "data": items}, 200

    @jwt_required
    @rate_limit(20, 3600)
    @handle_errors
    def post(self, current_user=None):
        data = request.get_json(silent=True)
        if not isinstance(data, dict):
            raise ServiceError("Request body must be a JSON object")
        submission = work_service.create_submission(
            get_current_user_id(current_user),
            document_id=data.get("document_id"),
            course_id=data.get("course_id"),
            is_practice=data.get("is_practice", False),
        )
        return {"success": True, "data": submission}, 202


class WorkSubmissionDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, submission_id, current_user=None):
        data = work_service.get_submission(get_current_user_id(current_user), submission_id)
        return {"success": True, "data": work_service.submission_response(submission_id, data)}, 200


class WorkSubmissionStreamAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, submission_id, current_user=None):
        uid = get_current_user_id(current_user)
        work_service.get_submission(uid, submission_id)  # ownership, before streaming

        def generate():
            last_step = None
            deadline = time.time() + STREAM_TIMEOUT_SECONDS
            while time.time() < deadline:
                data = work_service.get_submission(uid, submission_id)
                step = data.get("step")
                if step != last_step and step not in (None, "done"):
                    yield sse_format("step", {"step": step})
                    last_step = step
                if data.get("status") == "READY":
                    yield sse_format("done", work_service.submission_response(submission_id, data))
                    return
                if data.get("status") == "FAILED":
                    yield sse_format("error", {"message": data.get("error") or "Couldn't check this work."})
                    return
                time.sleep(POLL_SECONDS)
            yield sse_format("error", {"message": "This is taking longer than expected. Check back in a minute."})

        return Response(
            stream_with_context(generate()),
            mimetype="text/event-stream",
            headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        )
