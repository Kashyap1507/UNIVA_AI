"""
Today screen, course progress and the grade tracker.

    /api/today                       GET ?date=YYYY-MM-DD (the app's local day)
    /api/progress/courses            GET ?date=
    /api/progress/courses/<id>       GET ?date=
    /api/grades-tracker              GET ?course_id=, POST {course_id, name, score, max, weight_pct, deadline_id?}
    /api/grades-tracker/<id>         PUT, DELETE
"""

from datetime import date

from flask import request
from flask_restful import Resource

from app.api.courses_api import handle_errors
from app.services import course_progress_service, deadline_service, grade_tracker_service, study_activity_service
from app.services.course_service import ServiceError
from app.utils.decorators import get_current_user_id, jwt_required


def _body() -> dict:
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise ServiceError("Request body must be a JSON object")
    return data


def _day() -> date:
    value = request.args.get("date")
    if not value:
        return study_activity_service.local_today()
    due, all_day = deadline_service.parse_due(value)
    if not all_day:
        raise ServiceError("date must be YYYY-MM-DD")
    return date.fromisoformat(due)


class TodayAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        data = course_progress_service.today(get_current_user_id(current_user), request.args.get("date") or None)
        return {"success": True, "data": data}, 200


class ProgressCoursesAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        return {"success": True, "data": course_progress_service.courses_overview(get_current_user_id(current_user), _day())}, 200


class ProgressCourseDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, course_id, current_user=None):
        return {"success": True, "data": course_progress_service.course_detail(get_current_user_id(current_user), course_id, _day())}, 200


class GradeTrackerAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        data = grade_tracker_service.overview(get_current_user_id(current_user), request.args.get("course_id") or None)
        return {"success": True, "data": data}, 200

    @jwt_required
    @handle_errors
    def post(self, current_user=None):
        return {"success": True, "data": grade_tracker_service.create_entry(get_current_user_id(current_user), _body())}, 201


class GradeEntryAPI(Resource):
    @jwt_required
    @handle_errors
    def put(self, entry_id, current_user=None):
        return {"success": True, "data": grade_tracker_service.update_entry(get_current_user_id(current_user), entry_id, _body())}, 200

    @jwt_required
    @handle_errors
    def delete(self, entry_id, current_user=None):
        grade_tracker_service.delete_entry(get_current_user_id(current_user), entry_id)
        return {"success": True}, 200
