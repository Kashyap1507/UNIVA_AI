"""
Flashcards and spaced repetition.

    /api/courses/<id>/flashcards/generate  POST {concept_id?, count?} (AI, synchronous)
    /api/courses/<id>/flashcards           POST {front, back, concept_id?}
    /api/flashcards                        GET ?course_id=&due=true&limit=
    /api/flashcards/summary                GET (due counts per course)
    /api/flashcards/<id>                   PUT {front?, back?, suspended?, concept_id?}, DELETE
    /api/flashcards/<id>/review            POST {grade 0-5, reviewed_at?, client_review_id?}
"""

from flask import request
from flask_restful import Resource

from app.api.courses_api import handle_errors
from app.services import flashcard_service
from app.services.course_service import ServiceError
from app.utils.decorators import get_current_user_id, jwt_required, rate_limit


def _body() -> dict:
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise ServiceError("Request body must be a JSON object")
    return data


class FlashcardGenerateAPI(Resource):
    @jwt_required
    @rate_limit(30, 3600)
    @handle_errors
    def post(self, course_id, current_user=None):
        data = request.get_json(silent=True) or {}
        if not isinstance(data, dict):
            raise ServiceError("Request body must be a JSON object")
        cards = flashcard_service.generate(
            get_current_user_id(current_user),
            course_id,
            concept_id=data.get("concept_id"),
            count=data.get("count", flashcard_service.DEFAULT_COUNT),
        )
        return {"success": True, "data": cards}, 201


class CourseFlashcardsAPI(Resource):
    @jwt_required
    @handle_errors
    def post(self, course_id, current_user=None):
        card = flashcard_service.create_card(get_current_user_id(current_user), course_id, _body())
        return {"success": True, "data": card}, 201


class FlashcardsAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        try:
            limit = int(request.args.get("limit", flashcard_service.MAX_DUE))
        except ValueError:
            raise ServiceError("limit must be a number")
        cards = flashcard_service.list_cards(
            get_current_user_id(current_user),
            course_id=request.args.get("course_id") or None,
            due=request.args.get("due", "").lower() in ("1", "true", "yes"),
            limit=limit,
        )
        return {"success": True, "data": cards}, 200


class FlashcardSummaryAPI(Resource):
    @jwt_required
    @handle_errors
    def get(self, current_user=None):
        return {"success": True, "data": flashcard_service.summary(get_current_user_id(current_user))}, 200


class FlashcardDetailAPI(Resource):
    @jwt_required
    @handle_errors
    def put(self, card_id, current_user=None):
        card = flashcard_service.update_card(get_current_user_id(current_user), card_id, _body())
        return {"success": True, "data": card}, 200

    @jwt_required
    @handle_errors
    def delete(self, card_id, current_user=None):
        flashcard_service.delete_card(get_current_user_id(current_user), card_id)
        return {"success": True}, 200


class FlashcardReviewAPI(Resource):
    @jwt_required
    @handle_errors
    def post(self, card_id, current_user=None):
        data = _body()
        card = flashcard_service.review(
            get_current_user_id(current_user),
            card_id,
            data.get("grade"),
            reviewed_at=data.get("reviewed_at"),
            client_review_id=data.get("client_review_id"),
        )
        return {"success": True, "data": card}, 200
