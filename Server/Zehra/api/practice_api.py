"""
Practice sets, lecture quizzes and mock exams.

    /api/courses/<id>/practice-sets/generate  POST {kind, count?, module_ids?, time_limit_min?} (202, background)
    /api/practice-sets                        GET ?course_id=
    /api/practice-sets/<id>                   GET (questions without answers once READY), DELETE
    /api/practice-sets/<id>/attempts          POST (start; returns questions and the deadline)
    /api/practice-attempts                    GET ?course_id=
    /api/practice-attempts/<id>               GET (answers and explanations after submission)
    /api/practice-attempts/<id>/submit        POST {answers: [{question_id, response}]}
    /api/practice-attempts/<id>/regrade       POST (retry written answers that weren't marked)
"""

from flask import request
from flask_restful import Resource

from app.api.courses_api import handle_errors
from app.services import practice_service
from app.services.course_service import ServiceError
from app.utils.decorators import get_current_user_id, jwt_required, rate_limit


def _body() -> dict:
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise ServiceError("Request body must be a JSON object")
    return data


def _course_id() -> str:
    course_id = request.args.get("course_id")
    if not course_id:
        raise ServiceError("course_id is required")
    return course_id


class PracticeGenerateAPI(Resource):
    @jwt_required
    @rate_limit(20, 3600)
    @handle_errors
    def post(self, course_id, current_user=None):
        data = _body()
        created = practice_service.create_set(
            get_current_user_id(current_user),
            course_id,
            kind=data.get("kind"),
            count=data.get("count"),
            module_ids=data.get("module_ids"),
            time_limit_min=data.get("time_limit_min"),
        )
        return {"success": True, "data": created}, 202


class PracticeSetsAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        return {"success": True, "data": practice_service.list_sets(get_current_user_id(current_user), _course_id())}, 200


class PracticeSetDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, set_id, current_user=None):
        return {"success": True, "data": practice_service.get_set(get_current_user_id(current_user), set_id)}, 200

    @jwt_required
    @handle_errors
    def delete(self, set_id, current_user=None):
        practice_service.delete_set(get_current_user_id(current_user), set_id)
        return {"success": True}, 200


class PracticeAttemptStartAPI(Resource):
    @jwt_required
    @handle_errors
    def post(self, set_id, current_user=None):
        return {"success": True, "data": practice_service.start_attempt(get_current_user_id(current_user), set_id)}, 201


class PracticeAttemptsAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        return {"success": True, "data": practice_service.list_attempts(get_current_user_id(current_user), _course_id())}, 200


class PracticeAttemptDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, attempt_id, current_user=None):
        return {"success": True, "data": practice_service.get_attempt(get_current_user_id(current_user), attempt_id)}, 200


class PracticeAttemptSubmitAPI(Resource):
    @jwt_required
    @rate_limit(60, 3600)
    @handle_errors
    def post(self, attempt_id, current_user=None):
        result = practice_service.submit_attempt(get_current_user_id(current_user), attempt_id, _body().get("answers"))
        return {"success": True, "data": result}, 200


class PracticeAttemptRegradeAPI(Resource):
    @jwt_required
    @rate_limit(30, 3600)
    @handle_errors
    def post(self, attempt_id, current_user=None):
        return {"success": True, "data": practice_service.regrade_attempt(get_current_user_id(current_user), attempt_id)}, 200
