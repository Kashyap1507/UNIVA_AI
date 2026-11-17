"""
Study groups, shared notes and reports.

    /api/groups                               GET (mine), POST {name, description?, course_code?, discoverable?}
    /api/groups/discover                      GET ?course_code=  (same university, discoverable only)
    /api/groups/join                          POST {invite_code}
    /api/groups/<id>                          GET, PUT (owner), DELETE (owner)
    /api/groups/<id>/join                     POST (discoverable groups)
    /api/groups/<id>/leave                    POST
    /api/groups/<id>/invite-code              POST (owner: new code)
    /api/groups/<id>/members/<user_id>        DELETE (owner)
    /api/groups/<id>/notes                    GET, POST {kind: TEXT, body, title?} | {kind: MATERIAL, material_id, body?}
    /api/groups/<id>/notes/<note_id>          DELETE (author or owner)
    /api/groups/<id>/notes/<note_id>/download GET → short signed URL
    /api/reports                              POST {group_id, target_type, target_id, reason, details?}
"""

from flask import request
from flask_restful import Resource

from app.api.courses_api import handle_errors
from app.services import study_group_service as groups
from app.services.course_service import ServiceError
from app.utils.decorators import get_current_user_id, jwt_required, rate_limit


def _body() -> dict:
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise ServiceError("Request body must be a JSON object")
    return data


def _uid(current_user) -> str:
    return get_current_user_id(current_user)


class GroupsAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        return {"success": True, "data": groups.list_my_groups(_uid(current_user))}, 200

    @jwt_required
    @rate_limit(20, 3600)
    @handle_errors
    def post(self, current_user=None):
        return {"success": True, "data": groups.create_group(_uid(current_user), _body())}, 201


class GroupDiscoverAPI(Resource):
    @jwt_required
    @rate_limit(60, 3600)
    @handle_errors
    def get(self, current_user=None):
        return {"success": True, "data": groups.discover(_uid(current_user), request.args.get("course_code"))}, 200


class GroupJoinByCodeAPI(Resource):
    @jwt_required
    @rate_limit(30, 3600)
    @handle_errors
    def post(self, current_user=None):
        return {"success": True, "data": groups.join_by_code(_uid(current_user), _body().get("invite_code"))}, 200


class GroupDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, group_id, current_user=None):
        return {"success": True, "data": groups.get_group(_uid(current_user), group_id)}, 200

    @jwt_required
    @handle_errors
    def put(self, group_id, current_user=None):
        return {"success": True, "data": groups.update_group(_uid(current_user), group_id, _body())}, 200

    @jwt_required
    @handle_errors
    def delete(self, group_id, current_user=None):
        groups.delete_group(_uid(current_user), group_id)
        return {"success": True}, 200


class GroupJoinAPI(Resource):
    @jwt_required
    @rate_limit(30, 3600)
    @handle_errors
    def post(self, group_id, current_user=None):
        return {"success": True, "data": groups.join_discoverable(_uid(current_user), group_id)}, 200


class GroupLeaveAPI(Resource):
    @jwt_required
    @handle_errors
    def post(self, group_id, current_user=None):
        groups.leave_group(_uid(current_user), group_id)
        return {"success": True}, 200


class GroupInviteCodeAPI(Resource):
    @jwt_required
    @handle_errors
    def post(self, group_id, current_user=None):
        return {"success": True, "data": groups.reset_invite_code(_uid(current_user), group_id)}, 200


class GroupMemberAPI(Resource):
    @jwt_required
    @handle_errors
    def delete(self, group_id, member_id, current_user=None):
        groups.remove_member(_uid(current_user), group_id, member_id)
        return {"success": True}, 200


class GroupNotesAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, group_id, current_user=None):
        return {"success": True, "data": groups.list_notes(_uid(current_user), group_id)}, 200

    @jwt_required
    @rate_limit(60, 3600)
    @handle_errors
    def post(self, group_id, current_user=None):
        return {"success": True, "data": groups.create_note(_uid(current_user), group_id, _body())}, 201


class GroupNoteDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def delete(self, group_id, note_id, current_user=None):
        groups.delete_note(_uid(current_user), group_id, note_id)
        return {"success": True}, 200


class GroupNoteDownloadAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, group_id, note_id, current_user=None):
        return {"success": True, "data": groups.note_download(_uid(current_user), group_id, note_id)}, 200


class ReportsAPI(Resource):
    @jwt_required
    @rate_limit(20, 3600)
    @handle_errors
    def post(self, current_user=None):
        return {"success": True, "data": groups.report(_uid(current_user), _body())}, 201
