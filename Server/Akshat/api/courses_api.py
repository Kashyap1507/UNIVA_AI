"""
Semesters, courses, course structure, materials and course search.

    /api/semesters                        GET, POST
    /api/semesters/<id>                   PUT, DELETE
    /api/courses                          GET (?semester_id, ?include_archived), POST
    /api/courses/<id>                     GET, PUT, DELETE
    /api/courses/<id>/materials           GET, POST (multipart: file, kind?, title?)
    /api/courses/<id>/structure/generate  POST (202, runs in background)
    /api/courses/<id>/modules             GET, POST
    /api/courses/<id>/concepts            GET (?module_id)
    /api/courses/<id>/search              GET ?q=&k=
    /api/materials/<id>                   GET (with short-lived download_url), DELETE
    /api/materials/<id>/status            GET
    /api/materials/<id>/reingest          POST
"""

import logging
from functools import wraps

from flask import request
from flask_restful import Resource

from app.services import course_service, material_service, retrieval_service
from app.services.course_service import ServiceError
from app.utils.decorators import get_current_user_id, jwt_required, rate_limit

logger = logging.getLogger(__name__)


def handle_errors(fn):
    """Map ServiceError to its status; hide unexpected errors behind a 500."""

    @wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            return fn(*args, **kwargs)
        except ServiceError as e:
            return {"success": False, "error": e.message}, e.status
        except Exception as e:
            logger.error(f"{fn.__qualname__} failed: {e}", exc_info=True)
            return {"success": False, "error": "Something went wrong"}, 500

    return wrapper


def _json_body() -> dict:
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise ServiceError("Request body must be a JSON object")
    return data


def _uid(current_user) -> str:
    return get_current_user_id(current_user)


def _material_response(material, include_url: bool = False) -> dict:
    data = material.to_response()
    if include_url:
        data["download_url"] = material_service.download_url(material)
    return data


# --- semesters -------------------------------------------------------------------


class SemestersAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        semesters = course_service.list_semesters(_uid(current_user))
        return {"success": True, "data": [s.to_response() for s in semesters]}, 200

    @jwt_required
    @handle_errors
    def post(self, current_user=None):
        semester = course_service.create_semester(_uid(current_user), _json_body())
        return {"success": True, "data": semester.to_response()}, 201


class SemesterDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def put(self, semester_id, current_user=None):
        semester = course_service.update_semester(_uid(current_user), semester_id, _json_body())
        return {"success": True, "data": semester.to_response()}, 200

    @jwt_required
    @handle_errors
    def delete(self, semester_id, current_user=None):
        course_service.delete_semester(_uid(current_user), semester_id)
        return {"success": True}, 200


# --- courses ----------------------------------------------------------------------


class CoursesAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        include_archived = request.args.get("include_archived", "true").lower() != "false"
        courses = course_service.list_courses(
            _uid(current_user),
            semester_id=request.args.get("semester_id") or None,
            include_archived=include_archived,
        )
        return {"success": True, "data": [c.to_response() for c in courses]}, 200

    @jwt_required
    @handle_errors
    def post(self, current_user=None):
        course = course_service.create_course(_uid(current_user), _json_body())
        return {"success": True, "data": course.to_response()}, 201


class CourseDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, course_id, current_user=None):
        uid = _uid(current_user)
        course = course_service.get_course(uid, course_id)
        data = course.to_response()
        data["material_count"] = len(material_service.list_materials(uid, course_id))
        data["module_count"] = len(course_service.list_modules(uid, course_id))
        return {"success": True, "data": data}, 200

    @jwt_required
    @handle_errors
    def put(self, course_id, current_user=None):
        course = course_service.update_course(_uid(current_user), course_id, _json_body())
        return {"success": True, "data": course.to_response()}, 200

    @jwt_required
    @handle_errors
    def delete(self, course_id, current_user=None):
        course_service.delete_course(_uid(current_user), course_id)
        return {"success": True}, 200


# --- structure ---------------------------------------------------------------------


class CourseStructureGenerateAPI(Resource):
    @jwt_required
    @rate_limit(10, 3600)
    @handle_errors
    def post(self, course_id, current_user=None):
        from app.ai_framework.workflows.course_structure_generation import start_generation

        uid = _uid(current_user)
        course = course_service.get_course(uid, course_id)
        if course.structure_status == "PROCESSING":
            raise ServiceError("The course outline is already being generated", 409)
        start_generation(uid, course_id)
        return {"success": True, "data": {"structure_status": "PROCESSING"}}, 202


class CourseModulesAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, course_id, current_user=None):
        modules = course_service.list_modules(_uid(current_user), course_id)
        return {"success": True, "data": [m.to_response() for m in modules]}, 200

    @jwt_required
    @handle_errors
    def post(self, course_id, current_user=None):
        module = course_service.create_module(_uid(current_user), course_id, _json_body())
        return {"success": True, "data": module.to_response()}, 201


class CourseConceptsAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, course_id, current_user=None):
        concepts = course_service.list_concepts(
            _uid(current_user), course_id, module_id=request.args.get("module_id") or None
        )
        return {"success": True, "data": [c.to_response() for c in concepts]}, 200


class CourseSearchAPI(Resource):
    @jwt_required
    @rate_limit(60, 60)
    @handle_errors
    def get(self, course_id, current_user=None):
        uid = _uid(current_user)
        course_service.get_course(uid, course_id)
        query = (request.args.get("q") or "").strip()
        if not query:
            raise ServiceError("q is required")
        try:
            k = int(request.args.get("k", retrieval_service.DEFAULT_K))
        except ValueError:
            raise ServiceError("k must be a number")
        results = retrieval_service.search(uid, course_id, query, k=k)
        return {"success": True, "data": [r.to_response() for r in results]}, 200


# --- materials ---------------------------------------------------------------------


class CourseMaterialsAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, course_id, current_user=None):
        materials = material_service.list_materials(_uid(current_user), course_id)
        return {"success": True, "data": [_material_response(m) for m in materials]}, 200

    @jwt_required
    @rate_limit(60, 3600)
    @handle_errors
    def post(self, course_id, current_user=None):
        upload = request.files.get("file")
        if upload is None or not upload.filename:
            raise ServiceError("Attach a file in the 'file' field")
        data = upload.read(material_service.MAX_UPLOAD_BYTES + 1)
        material = material_service.create_material(
            _uid(current_user),
            course_id,
            filename=upload.filename,
            data=data,
            declared_mime=upload.mimetype,
            kind=request.form.get("kind") or None,
            title=request.form.get("title") or None,
        )
        return {"success": True, "data": _material_response(material)}, 201


class MaterialDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, material_id, current_user=None):
        material = material_service.get_material(_uid(current_user), material_id)
        return {"success": True, "data": _material_response(material, include_url=True)}, 200

    @jwt_required
    @handle_errors
    def delete(self, material_id, current_user=None):
        material_service.delete_material(_uid(current_user), material_id)
        return {"success": True}, 200


class MaterialStatusAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, material_id, current_user=None):
        m = material_service.get_material(_uid(current_user), material_id)
        return {
            "success": True,
            "data": {
                "id": m.id,
                "ingestion_status": m.ingestion_status,
                "error": m.error,
                "chunk_count": m.chunk_count,
                "pages": m.pages,
            },
        }, 200


class MaterialReingestAPI(Resource):
    @jwt_required
    @rate_limit(20, 3600)
    @handle_errors
    def post(self, material_id, current_user=None):
        material = material_service.reingest_material(_uid(current_user), material_id)
        return {"success": True, "data": _material_response(material)}, 202
