"""
Deadlines, syllabus extraction, iCal import and exam study plans.

    /api/deadlines                          GET ?course_id=&from=&to=&include_completed=, POST
    /api/deadlines/bulk                     POST {source: SYLLABUS|DEVICE|MANUAL, deadlines: [...]}
    /api/deadlines/<id>                     PUT, DELETE
    /api/deadlines/import-ical              POST multipart (file) or JSON {url}; optional course_id
    /api/courses/<id>/syllabus/extract      POST {material_id?} → candidates (not saved)
    /api/study-plans/generate               POST {deadline_id | course_id + exam_date, minutes_per_day?, start_date?}
    /api/study-plans                        GET ?course_id=
    /api/study-plans/<id>                   GET, DELETE
    /api/study-plans/<id>/tasks/<task_id>   PUT {done}
"""

from flask import request
from flask_restful import Resource

from app.api.courses_api import handle_errors
from app.services import deadline_service, exam_plan_service
from app.services.course_service import ServiceError
from app.utils.decorators import get_current_user_id, jwt_required, rate_limit


def _body() -> dict:
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise ServiceError("Request body must be a JSON object")
    return data


class DeadlinesAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        items = deadline_service.list_deadlines(
            get_current_user_id(current_user),
            course_id=request.args.get("course_id") or None,
            date_from=request.args.get("from") or None,
            date_to=request.args.get("to") or None,
            include_completed=request.args.get("include_completed", "true").lower() not in ("0", "false", "no"),
        )
        return {"success": True, "data": items}, 200

    @jwt_required
    @handle_errors
    def post(self, current_user=None):
        return {"success": True, "data": deadline_service.create_deadline(get_current_user_id(current_user), _body())}, 201


class DeadlinesBulkAPI(Resource):
    @jwt_required
    @handle_errors
    def post(self, current_user=None):
        data = _body()
        created = deadline_service.create_bulk(get_current_user_id(current_user), data.get("deadlines"), data.get("source", "MANUAL"))
        return {"success": True, "data": created}, 201


class DeadlineDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def put(self, deadline_id, current_user=None):
        return {"success": True, "data": deadline_service.update_deadline(get_current_user_id(current_user), deadline_id, _body())}, 200

    @jwt_required
    @handle_errors
    def delete(self, deadline_id, current_user=None):
        deadline_service.delete_deadline(get_current_user_id(current_user), deadline_id)
        return {"success": True}, 200


class DeadlinesImportICalAPI(Resource):
    @jwt_required
    @rate_limit(20, 3600)
    @handle_errors
    def post(self, current_user=None):
        uid = get_current_user_id(current_user)
        upload = request.files.get("file")
        if upload is not None:
            data = upload.read(deadline_service.MAX_ICS_BYTES + 1)
            result = deadline_service.import_ical(uid, data=data, course_id=request.form.get("course_id") or None)
        else:
            body = _body()
            result = deadline_service.import_ical(uid, url=body.get("url"), course_id=body.get("course_id") or None)
        return {"success": True, "data": result}, 200


class SyllabusExtractAPI(Resource):
    @jwt_required
    @rate_limit(20, 3600)
    @handle_errors
    def post(self, course_id, current_user=None):
        data = request.get_json(silent=True) or {}
        if not isinstance(data, dict):
            raise ServiceError("Request body must be a JSON object")
        result = deadline_service.extract_from_syllabus(get_current_user_id(current_user), course_id, data.get("material_id") or None)
        return {"success": True, "data": result}, 200


class StudyPlanGenerateAPI(Resource):
    @jwt_required
    @handle_errors
    def post(self, current_user=None):
        return {"success": True, "data": exam_plan_service.generate(get_current_user_id(current_user), _body())}, 201


class StudyPlansAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        plans = exam_plan_service.list_plans(get_current_user_id(current_user), request.args.get("course_id") or None)
        return {"success": True, "data": plans}, 200


class StudyPlanDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, plan_id, current_user=None):
        return {"success": True, "data": exam_plan_service.get_plan(get_current_user_id(current_user), plan_id)}, 200

    @jwt_required
    @handle_errors
    def delete(self, plan_id, current_user=None):
        exam_plan_service.delete_plan(get_current_user_id(current_user), plan_id)
        return {"success": True}, 200


class StudyPlanTaskAPI(Resource):
    @jwt_required
    @handle_errors
    def put(self, plan_id, task_id, current_user=None):
        plan = exam_plan_service.set_task_done(get_current_user_id(current_user), plan_id, task_id, _body().get("done"))
        return {"success": True, "data": plan}, 200
