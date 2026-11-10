"""
Documents API endpoints
Scans of the student's work (the input to "Check my work") and their pages.
Every endpoint is limited to the signed-in student's documents.
"""

import base64
import logging
from datetime import datetime

from flask import request
from flask_restful import Resource, reqparse

from app.models.document import Document
from app.services.document_service import DocumentService
from app.utils.decorators import jwt_required

logger = logging.getLogger(__name__)

document_service = DocumentService()

NOT_FOUND = ({"success": False, "error": "Document not found"}, 404)


def _owned(current_user, doc_id):
    return document_service.get_owned_snapshot(doc_id, current_user["id"])


class DocumentScanAPI(Resource):
    """
    POST /api/documents/scan
    Create a new document from scanned pages. The owner is the signed-in student.
    Body: {title, type?, course_id?, pages: [{image_data (base64), page_number, width, height}]}
    """

    @jwt_required
    def post(self, current_user):
        parser = reqparse.RequestParser()
        parser.add_argument("title", type=str, required=True, help="Title is required")
        parser.add_argument("type", type=str, default=Document.TYPE_HOMEWORK)
        parser.add_argument("course_id", type=str)
        parser.add_argument("pages", type=list, location="json")
        args = parser.parse_args()

        course_id = args.get("course_id")
        if course_id:
            from app.services.firebase_service import get_firestore_client

            course = get_firestore_client().collection("courses").document(course_id).get()
            if not course.exists or (course.to_dict() or {}).get("user_id") != current_user["id"]:
                return {"success": False, "error": "Course not found"}, 404

        try:
            document = document_service.create_document(
                user_id=current_user["id"],
                title=args["title"],
                doc_type=args["type"] or Document.TYPE_HOMEWORK,
                course_id=course_id,
            )

            added_pages = []
            for page_data in args.get("pages") or []:
                image_data_base64 = page_data.get("image_data")
                if not image_data_base64:
                    continue
                page = document_service.add_page(
                    doc_id=document.id,
                    image_data=base64.b64decode(image_data_base64),
                    page_number=page_data.get("page_number", len(added_pages) + 1),
                    width=page_data.get("width", 0),
                    height=page_data.get("height", 0),
                    owner_id=current_user["id"],
                )
                if page:
                    added_pages.append(document_service.page_api_dict(page))

            document = document_service.get_document(document.id) or document
            document_dict = document_service.document_api_dict(document)
            document_dict["pages"] = added_pages

            try:
                from app.services.data_change_notifier import notify_data_change

                notify_data_change(current_user["id"], "document_added", {"document_id": document.id})
            except Exception as e:
                logger.info(f"document_added push not sent: {e}")

            return {"success": True, "message": "Document created successfully", "data": document_dict}, 201

        except Exception as e:
            logger.error(f"[DOCUMENT_SCAN] Error: {e}")
            return {"success": False, "error": "Couldn't save the scan"}, 500


class DocumentsListAPI(Resource):
    """
    GET /api/documents?type=&include_deleted=&limit=
    The signed-in student's documents, newest first.
    """

    @jwt_required
    def get(self, current_user):
        include_deleted = request.args.get("include_deleted", "false").lower() == "true"
        try:
            limit = int(request.args.get("limit", 50))
        except ValueError:
            limit = 50

        try:
            documents = document_service.list_documents(
                user_id=current_user["id"],
                doc_type=request.args.get("type"),
                include_deleted=include_deleted,
                limit=limit,
            )
            documents_data = [document_service.document_api_dict(doc) for doc in documents]
            return {"success": True, "data": documents_data, "count": len(documents_data)}, 200

        except Exception as e:
            from app.utils.logging_utils import format_api_error, log_exception_securely

            log_exception_securely("Error listing documents", e)
            return format_api_error("SERVER_ERROR", "Failed to retrieve documents"), 500


class DocumentDetailAPI(Resource):
    """
    GET /api/documents/:id[?include_pages=true]
    PUT /api/documents/:id   {title?, status?, processing_progress?}
    DELETE /api/documents/:id[?hard_delete=true]
    """

    @jwt_required
    def get(self, current_user, doc_id):
        if not _owned(current_user, doc_id):
            return NOT_FOUND
        include_pages = request.args.get("include_pages", "false").lower() == "true"
        try:
            document = document_service.get_document(doc_id)
            document_dict = document_service.document_api_dict(document)
            if include_pages:
                document_dict["pages"] = [document_service.page_api_dict(p) for p in document_service.get_pages(doc_id)]
            return {"success": True, "data": document_dict}, 200
        except Exception as e:
            logger.error(f"Error loading document {doc_id}: {e}")
            return {"success": False, "error": "Failed to load the document"}, 500

    @jwt_required
    def put(self, current_user, doc_id):
        if not _owned(current_user, doc_id):
            return NOT_FOUND
        parser = reqparse.RequestParser()
        parser.add_argument("title", type=str)
        parser.add_argument("status", type=str)
        parser.add_argument("processing_progress", type=int)
        args = parser.parse_args()

        try:
            document = document_service.update_document(
                doc_id=doc_id,
                title=args.get("title"),
                status=args.get("status"),
                processing_progress=args.get("processing_progress"),
            )
            if not document:
                return NOT_FOUND
            return {"success": True, "message": "Document updated successfully", "data": document_service.document_api_dict(document)}, 200
        except Exception as e:
            logger.error(f"Error updating document {doc_id}: {e}")
            return {"success": False, "error": "Failed to update the document"}, 500

    @jwt_required
    def delete(self, current_user, doc_id):
        if not _owned(current_user, doc_id):
            return NOT_FOUND
        hard_delete = request.args.get("hard_delete", "false").lower() == "true"
        try:
            document_service.delete_document(doc_id, current_user["id"], hard_delete=hard_delete)
            return {"success": True, "message": "Document deleted successfully"}, 200
        except Exception as e:
            logger.error(f"Error deleting document {doc_id}: {e}")
            return {"success": False, "error": "Failed to delete the document"}, 500


class DocumentPagesAPI(Resource):
    """
    POST /api/documents/:id/pages  {image_data (base64), page_number, width?, height?}
    GET /api/documents/:id/pages
    """

    @jwt_required
    def post(self, current_user, doc_id):
        if not _owned(current_user, doc_id):
            return NOT_FOUND
        parser = reqparse.RequestParser()
        parser.add_argument("image_data", type=str, required=True, help="Base64 image data is required")
        parser.add_argument("page_number", type=int, required=True)
        parser.add_argument("width", type=int, default=0)
        parser.add_argument("height", type=int, default=0)
        args = parser.parse_args()

        try:
            page = document_service.add_page(
                doc_id=doc_id,
                image_data=base64.b64decode(args["image_data"]),
                page_number=args["page_number"],
                width=args["width"],
                height=args["height"],
                owner_id=current_user["id"],
            )
            if not page:
                return {"success": False, "error": "Failed to add page"}, 500
            return {"success": True, "message": "Page added successfully", "data": document_service.page_api_dict(page)}, 201
        except Exception as e:
            logger.error(f"Error adding a page to {doc_id}: {e}")
            return {"success": False, "error": "Failed to add page"}, 500

    @jwt_required
    def get(self, current_user, doc_id):
        if not _owned(current_user, doc_id):
            return NOT_FOUND
        try:
            pages_data = [document_service.page_api_dict(p) for p in document_service.get_pages(doc_id)]
            return {"success": True, "data": pages_data, "count": len(pages_data)}, 200
        except Exception as e:
            logger.error(f"Error loading pages of {doc_id}: {e}")
            return {"success": False, "error": "Failed to load pages"}, 500


class DocumentPageDetailAPI(Resource):
    """
    DELETE /api/documents/:doc_id/pages/:page_id[?hard_delete=true]
    """

    @jwt_required
    def delete(self, current_user, doc_id, page_id):
        if not _owned(current_user, doc_id):
            return NOT_FOUND
        hard_delete = request.args.get("hard_delete", "false").lower() == "true"
        try:
            if not document_service.delete_page(doc_id, page_id, hard_delete=hard_delete):
                return {"success": False, "error": "Page not found"}, 404
            return {"success": True, "message": "Page deleted successfully"}, 200
        except Exception as e:
            logger.error(f"Error deleting page {page_id}: {e}")
            return {"success": False, "error": "Failed to delete the page"}, 500


class DocumentPagesReorderAPI(Resource):
    """
    PUT /api/documents/:id/pages/reorder  {page_order: [page ids]}
    """

    @jwt_required
    def put(self, current_user, doc_id):
        if not _owned(current_user, doc_id):
            return NOT_FOUND
        parser = reqparse.RequestParser()
        parser.add_argument(
            "page_order", type=list, location="json", required=True, help="Array of page IDs in desired order is required"
        )
        args = parser.parse_args()

        if not document_service.reorder_pages(doc_id, args["page_order"]):
            return {"success": False, "error": "Failed to reorder pages"}, 500
        return {"success": True, "message": "Pages reordered successfully"}, 200


class DocumentSearchAPI(Resource):
    """
    GET /api/documents/search?q=&limit=
    Title search over the student's documents.
    """

    @jwt_required
    def get(self, current_user):
        query = request.args.get("q", "")
        try:
            limit = int(request.args.get("limit", 20))
        except ValueError:
            limit = 20
        try:
            documents = document_service.search_documents(current_user["id"], query, limit)
            documents_data = [document_service.document_api_dict(doc) for doc in documents]
            return {"success": True, "data": documents_data, "count": len(documents_data)}, 200
        except Exception as e:
            logger.error(f"Error searching documents: {e}")
            return {"success": False, "error": "Search failed"}, 500


class DocumentSyncAPI(Resource):
    """
    POST /api/documents/sync  {documents: [{title, type?, course_id?}]}
    Bulk create documents for the student (sync from the app).
    """

    @jwt_required
    def post(self, current_user):
        parser = reqparse.RequestParser()
        parser.add_argument("documents", type=list, location="json", required=True, help="Array of documents to sync is required")
        args = parser.parse_args()

        try:
            created = document_service.bulk_create_documents(current_user["id"], args["documents"])
            return {
                "success": True,
                "message": f"Synced {len(created)} documents",
                "data": [document_service.document_api_dict(doc) for doc in created],
            }, 201
        except Exception as e:
            logger.error(f"Error syncing documents: {e}")
            return {"success": False, "error": "Sync failed"}, 500


class DocumentChangesAPI(Resource):
    """
    GET /api/documents/changes?since=ISO
    The student's documents modified since a timestamp (incremental sync).
    """

    @jwt_required
    def get(self, current_user):
        since = request.args.get("since")
        if not since:
            return {"success": False, "error": "since parameter is required (ISO timestamp)"}, 400
        try:
            timestamp = datetime.fromisoformat(since.replace("Z", "+00:00")).replace(tzinfo=None)
        except ValueError:
            return {"success": False, "error": "Invalid timestamp format. Use ISO 8601 format."}, 400

        try:
            documents = document_service.get_documents_modified_since(current_user["id"], timestamp)
            documents_data = [document_service.document_api_dict(doc) for doc in documents]
            return {"success": True, "data": documents_data, "count": len(documents_data)}, 200
        except Exception as e:
            logger.error(f"Error loading document changes: {e}")
            return {"success": False, "error": "Failed to load changes"}, 500
