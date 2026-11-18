"""
Admin endpoints for the university app (admin token required).

    /api/admin/overview                        GET ?days=7|30|90
    /api/admin/students                        GET ?q=&degree=BACHELOR|MASTER&limit=
    /api/admin/students/<user_id>              GET
    /api/admin/catalog                         GET
    /api/admin/moderation                      GET ?status=OPEN|RESOLVED
    /api/admin/moderation/<report_id>/resolve  POST {action: DISMISS|REMOVE, note?}
    /api/admin/ai-costs                        GET ?days=7|30|90
"""

from flask import request
from flask_restful import Resource

from app.api.courses_api import handle_errors
from app.services import admin_university_service as admin
from app.services.admin_auth_service import admin_required
from app.services.course_service import ServiceError


def _admin_id():
    user = getattr(request, "admin_user", None) or {}
    return user.get("id")


class AdminOverviewAPI(Resource):
    @admin_required
    @handle_errors
    def get(self):
        return {"success": True, "data": admin.overview(request.args.get("days", 7))}


class AdminStudentsAPI(Resource):
    @admin_required
    @handle_errors
    def get(self):
        data = admin.list_students(request.args.get("q"), request.args.get("degree"), request.args.get("limit", 200))
        return {"success": True, "data": data}


class AdminStudentDetailAPI(Resource):
    @admin_required
    @handle_errors
    def get(self, user_id):
        return {"success": True, "data": admin.student_detail(user_id)}


class AdminCatalogAPI(Resource):
    @admin_required
    @handle_errors
    def get(self):
        return {"success": True, "data": admin.catalog()}


class AdminModerationAPI(Resource):
    @admin_required
    @handle_errors
    def get(self):
        return {"success": True, "data": admin.moderation_queue(request.args.get("status", "OPEN"))}


class AdminModerationResolveAPI(Resource):
    @admin_required
    @handle_errors
    def post(self, report_id):
        body = request.get_json(silent=True)
        if not isinstance(body, dict):
            raise ServiceError("Request body must be a JSON object")
        return {"success": True, "data": admin.resolve(report_id, _admin_id(), body)}


class AdminAICostsAPI(Resource):
    @admin_required
    @handle_errors
    def get(self):
        return {"success": True, "data": admin.ai_costs(request.args.get("days", 30))}
