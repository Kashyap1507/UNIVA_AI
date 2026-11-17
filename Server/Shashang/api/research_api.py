"""
Research tools (mainly for Master's students) and lecture summaries.

    /api/research/projects                          GET, POST {title, kind, description?, due_date?}
    /api/research/projects/<id>                     GET, PUT, DELETE
    /api/research/projects/<id>/milestones          GET, POST {title, due_at, status?, notes?}
    /api/research/projects/<id>/milestones/suggest  POST {due_date?, start_date?}
    /api/research/milestones/<id>                   PUT, DELETE
    /api/research/papers                            GET ?project_id=, POST JSON (details) or multipart (file, project_id?)
    /api/research/papers/<id>                       GET, PUT, DELETE
    /api/research/papers/<id>/analyze               POST
    /api/research/papers/<id>/download              GET → signed URL
    /api/research/compare                           POST {paper_ids[], focus?}
    /api/research/export                            GET ?format=bibtex|apa&project_id=&paper_ids=a,b
    /api/research/writing-feedback                  POST {text, kind}
    /api/materials/<id>/summary                     POST (lecture summary for any course material)
"""

from flask import request
from flask_restful import Resource

from app.api.courses_api import handle_errors
from app.services import research_service as research
from app.services.course_service import ServiceError
from app.utils.decorators import get_current_user_id, jwt_required, rate_limit


def _body() -> dict:
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise ServiceError("Request body must be a JSON object")
    return data


def _uid(current_user) -> str:
    return get_current_user_id(current_user)


class ResearchProjectsAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        return {"success": True, "data": research.list_projects(_uid(current_user))}, 200

    @jwt_required
    @handle_errors
    def post(self, current_user=None):
        return {"success": True, "data": research.create_project(_uid(current_user), _body())}, 201


class ResearchProjectDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, project_id, current_user=None):
        return {"success": True, "data": research.get_project(_uid(current_user), project_id)}, 200

    @jwt_required
    @handle_errors
    def put(self, project_id, current_user=None):
        return {"success": True, "data": research.update_project(_uid(current_user), project_id, _body())}, 200

    @jwt_required
    @handle_errors
    def delete(self, project_id, current_user=None):
        research.delete_project(_uid(current_user), project_id)
        return {"success": True}, 200


class ProjectMilestonesAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, project_id, current_user=None):
        return {"success": True, "data": research.list_milestones(_uid(current_user), project_id)}, 200

    @jwt_required
    @handle_errors
    def post(self, project_id, current_user=None):
        return {"success": True, "data": research.create_milestone(_uid(current_user), project_id, _body())}, 201


class ProjectMilestonesSuggestAPI(Resource):
    @jwt_required
    @handle_errors
    def post(self, project_id, current_user=None):
        data = request.get_json(silent=True) or {}
        if not isinstance(data, dict):
            raise ServiceError("Request body must be a JSON object")
        return {"success": True, "data": research.suggest_milestones(_uid(current_user), project_id, data)}, 201


class MilestoneDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def put(self, milestone_id, current_user=None):
        return {"success": True, "data": research.update_milestone(_uid(current_user), milestone_id, _body())}, 200

    @jwt_required
    @handle_errors
    def delete(self, milestone_id, current_user=None):
        research.delete_milestone(_uid(current_user), milestone_id)
        return {"success": True}, 200


class PapersAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        return {"success": True, "data": research.list_papers(_uid(current_user), request.args.get("project_id") or None)}, 200

    @jwt_required
    @rate_limit(60, 3600)
    @handle_errors
    def post(self, current_user=None):
        uid = _uid(current_user)
        upload = request.files.get("file")
        if upload is not None:
            data = upload.read(research.MAX_PAPER_BYTES + 1)
            paper = research.upload_paper(uid, upload.filename or "paper.pdf", data, upload.mimetype, request.form.get("project_id") or None)
        else:
            paper = research.create_paper(uid, _body())
        return {"success": True, "data": paper}, 201


class PaperDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, paper_id, current_user=None):
        return {"success": True, "data": research.get_paper(_uid(current_user), paper_id)}, 200

    @jwt_required
    @handle_errors
    def put(self, paper_id, current_user=None):
        return {"success": True, "data": research.update_paper(_uid(current_user), paper_id, _body())}, 200

    @jwt_required
    @handle_errors
    def delete(self, paper_id, current_user=None):
        research.delete_paper(_uid(current_user), paper_id)
        return {"success": True}, 200


class PaperAnalyzeAPI(Resource):
    @jwt_required
    @rate_limit(40, 3600)
    @handle_errors
    def post(self, paper_id, current_user=None):
        return {"success": True, "data": research.analyse_paper(_uid(current_user), paper_id)}, 200


class PaperDownloadAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, paper_id, current_user=None):
        return {"success": True, "data": research.paper_download(_uid(current_user), paper_id)}, 200


class CompareAPI(Resource):
    @jwt_required
    @rate_limit(30, 3600)
    @handle_errors
    def post(self, current_user=None):
        data = _body()
        return {"success": True, "data": research.compare(_uid(current_user), data.get("paper_ids"), data.get("focus"))}, 200


class ExportAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        ids = [i for i in (request.args.get("paper_ids") or "").split(",") if i]
        data = research.export(_uid(current_user), request.args.get("format", "bibtex"), request.args.get("project_id") or None, ids or None)
        return {"success": True, "data": data}, 200


class WritingFeedbackAPI(Resource):
    @jwt_required
    @rate_limit(30, 3600)
    @handle_errors
    def post(self, current_user=None):
        return {"success": True, "data": research.writing_feedback(_uid(current_user), _body())}, 200


class MaterialSummaryAPI(Resource):
    @jwt_required
    @rate_limit(40, 3600)
    @handle_errors
    def post(self, material_id, current_user=None):
        return {"success": True, "data": research.summarise_material(_uid(current_user), material_id)}, 200
