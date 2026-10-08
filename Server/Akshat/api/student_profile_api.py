"""
Student profile API.

GET /api/student/profile  -> the student's profile and onboarding state
PUT /api/student/profile  -> partial update of profile fields (and display name)
"""

import logging

from flask import request
from flask_restful import Resource

from app.models.user import User
from app.services.firebase_service import get_firestore_client
from app.services.student_service import (
    student_payload,
    update_student_profile,
    validate_student_profile,
)
from app.utils.decorators import get_current_user_id, jwt_required

MAX_NAME_LENGTH = 100


def _profile_response(user: User):
    return {
        "success": True,
        "data": {
            "id": user.id,
            "email": user.email,
            "name": user.name or user.email.split("@")[0],
            "avatar_url": user.avatar_url,
            **student_payload(user),
        },
    }


class StudentProfileAPI(Resource):
    @jwt_required
    def get(self, current_user=None):
        try:
            db = get_firestore_client()
            user = User.get_by_id(db, get_current_user_id(current_user))
            if not user:
                return {"success": False, "error": "User not found"}, 404
            return _profile_response(user), 200
        except Exception as e:
            logging.error(f"Error getting student profile: {str(e)}")
            return {"success": False, "error": "Failed to load profile"}, 500

    @jwt_required
    def put(self, current_user=None):
        try:
            data = request.get_json(silent=True)
            if not isinstance(data, dict) or not data:
                return {"success": False, "error": "No data provided"}, 400

            name = data.pop("name", None)
            if name is not None:
                if not isinstance(name, str) or not name.strip():
                    return {"success": False, "error": "name must be a non-empty string"}, 400
                if len(name.strip()) > MAX_NAME_LENGTH:
                    return {
                        "success": False,
                        "error": f"name must be at most {MAX_NAME_LENGTH} characters",
                    }, 400

            ok, message, cleaned = validate_student_profile(data)
            if not ok:
                return {"success": False, "error": message}, 400
            if not cleaned and name is None:
                return {"success": False, "error": "No valid fields provided"}, 400

            db = get_firestore_client()
            user = User.get_by_id(db, get_current_user_id(current_user))
            if not user:
                return {"success": False, "error": "User not found"}, 404

            if name is not None:
                user.name = name.strip()
                db.collection(User.collection_name).document(user.id).update(
                    {"name": user.name}
                )
            update_student_profile(db, user, cleaned)
            return _profile_response(user), 200
        except Exception as e:
            logging.error(f"Error updating student profile: {str(e)}")
            return {"success": False, "error": "Failed to update profile"}, 500
