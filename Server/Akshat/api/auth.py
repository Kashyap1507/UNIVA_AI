"""
Authentication API endpoints
"""

import logging
from datetime import datetime

from flask import request
from flask_restful import Resource

from app.services.auth_service import AuthService
from app.services.firebase_service import get_firestore_client
from app.utils.datetime_utils import serialize_firestore_data
from app.utils.decorators import rate_limit  # Direct import to avoid circular import
from app.utils.validators import validate_email, validate_verification_code


def get_bootstrap_data(user_id):
    """
    Bootstrap payload returned with every auth response: app settings,
    subscription usage and the student fields. Courses, deadlines and
    cards are loaded by their own endpoints.
    """
    from app.models.app_settings import AppSettings
    from app.models.user import User
    from app.services.student_service import student_payload

    sync_timestamp = datetime.utcnow().isoformat() + "Z"
    db = get_firestore_client()
    user_obj = None
    try:
        user_obj = User.get_by_id(db, user_id)

        app_settings = AppSettings.get_by_user_id(db, user_id)
        if not app_settings:
            logging.info(f"Creating default app settings for user {user_id} during bootstrap")
            app_settings = AppSettings.create_default(db, user_id)
        app_settings_data = app_settings.to_api_dict() if app_settings else None

        from app.models.subscription_plan import SubscriptionPlan  # noqa: F401  (kept for tier lookups)
        from app.services.usage_limit_service import UsageLimitService

        usage_summary = UsageLimitService().get_usage_summary(user_id)
        subscription_data = {
            "tier": user_obj.subscription_tier if user_obj else "free",
            "status": user_obj.subscription_status if user_obj else "active",
            "account_type": user_obj.account_type if user_obj else "full",
            "usage_summary": usage_summary,
            "reset_time": usage_summary.get("last_reset") if isinstance(usage_summary, dict) else None,
        }

        bootstrap_data = {
            "sync_timestamp": sync_timestamp,
            "app_settings": app_settings_data,
            "subscription": subscription_data,
        }
        if user_obj:
            bootstrap_data.update(student_payload(user_obj))
        return serialize_firestore_data(bootstrap_data)

    except Exception as e:
        logging.error(f"Error getting bootstrap data: {str(e)}")
        # Keep the student fields so the app can still route to onboarding correctly
        student = student_payload(user_obj) if user_obj else {}
        return {**student, "sync_timestamp": sync_timestamp, "app_settings": None}


def student_fields(bootstrap_data):
    """Top-level student fields for auth responses, taken from bootstrap data."""
    bootstrap_data = bootstrap_data or {}
    return {
        "student_profile": bootstrap_data.get("student_profile"),
        "needs_onboarding": bootstrap_data.get("needs_onboarding", True),
    }


def _iso(value):
    return value.isoformat() if value and hasattr(value, "isoformat") else value


def format_user_profile(user_obj):
    """The account fields the app caches after sign-in (user_profile)."""
    return {
        "id": user_obj.id,
        "email": user_obj.email,
        "name": user_obj.name if getattr(user_obj, "name", None) else user_obj.email.split("@")[0],
        "auth_provider": user_obj.auth_provider,
        "provider_uid": user_obj.provider_uid,
        "verified": user_obj.verified,
        "is_active": user_obj.is_active,
        "created_at": _iso(user_obj.created_at),
        "updated_at": _iso(user_obj.updated_at),
        "last_login": _iso(user_obj.last_login),
        "avatar_url": user_obj.avatar_url,
        "referral_code": getattr(user_obj, "referral_code", None),
        "referral_limit": getattr(user_obj, "referral_limit", 5),
        "referrals_remaining": getattr(user_obj, "referrals_remaining", 5),
        "account_type": getattr(user_obj, "account_type", "full"),
    }


def get_user_profile(user_id):
    """Profile for the auth response, so the app needs no profile call after login."""
    try:
        from app.models.user import User

        user = User.get_by_id(get_firestore_client(), user_id)
        return format_user_profile(user) if user else None
    except Exception as e:
        logging.error(f"Error getting user profile: {str(e)}")
        return None


class AuthSignup(Resource):
    """Handle user signup with email"""

    def __init__(self):
        self.auth_service = AuthService()

    @rate_limit(5, 3600)  # 5 requests per hour
    def post(self):
        try:
            data = request.get_json()

            if not data:
                return {"error": "No data provided"}, 400

            # Validate required fields
            email = data.get("email", "").strip().lower()
            referral_code = data.get("referral_code")  # Optional

            logging.info(f"📝 Signup request - Referral code: {referral_code}")

            # Validate email
            if not validate_email(email):
                return {"error": "Invalid email format"}, 400

            # Process signup with optional referral code
            result = self.auth_service.signup_with_email(
                email, referral_code=referral_code
            )

            if result["success"]:
                return {
                    "message": result["message"],
                    "requires_verification": result["requires_verification"],
                    "email": result["email"],
                }, 200
            else:
                return {"error": result["message"]}, 400

        except Exception as e:
            logging.error(f"Error in signup: {str(e)}")
            return {"error": "Internal server error"}, 500


class AuthLogin(Resource):
    """Handle user login with email"""

    def __init__(self):
        self.auth_service = AuthService()

    @rate_limit(10, 3600)  # 10 requests per hour
    def post(self):
        try:
            data = request.get_json()

            if not data:
                return {"error": "No data provided"}, 400

            email = data.get("email", "").strip().lower()

            # Validate email
            if not validate_email(email):
                return {"error": "Invalid email format"}, 400

            # Process login
            result = self.auth_service.login_with_email(email)

            if result["success"]:
                return {
                    "message": result["message"],
                    "requires_verification": result.get("requires_verification", False),
                    "email": result.get("email"),
                }, 200
            else:
                return {"error": result["message"]}, 400

        except Exception as e:
            logging.error(f"Error in login: {str(e)}")
            return {"error": "Internal server error"}, 500


class AuthProvider(Resource):
    """Handle authentication with auth providers (Google, Apple, Facebook)"""

    def __init__(self):
        self.auth_service = AuthService()

    def post(self):
        try:
            data = request.get_json()

            if not data:
                return {"error": "no_data", "message": "No data provided"}, 400

            firebase_token = data.get("firebase_token")
            referral_code = data.get("referral_code")  # Optional

            # Debug logging for referral code tracking
            logging.info(f"🎟️ Provider auth - Referral code: {referral_code}")

            if not firebase_token:
                return {
                    "error": "missing_token",
                    "message": "Firebase token is required",
                }, 400

            # Process provider authentication with optional referral code
            result = self.auth_service.auth_with_provider(
                firebase_token, referral_code=referral_code
            )

            if result["success"]:
                logging.info(f"User ID: {result['user']['id']}")
                logging.info(f"Auth Provider: {result['user']['auth_provider']}")
                logging.info(f"Provider UID: {result['user']['provider_uid']}")

                # Get bootstrap data for initial app setup
                bootstrap_data = get_bootstrap_data(result["user"]["id"])

                user_profile = get_user_profile(result["user"]["id"])

                response_data = {
                    "message": result["message"],
                    "token": result["token"],
                    "user": {
                        "id": result["user"]["id"],
                        "email": result["user"]["email"],
                        "auth_provider": result["user"]["auth_provider"],
                        "name": result["user"].get("name"),
                    },
                    "is_new_user": result.get("is_new_user", False),
                    "bootstrap_data": bootstrap_data,
                    "user_profile": user_profile,
                    **student_fields(bootstrap_data),
                }
                return response_data, 200
            else:
                return {"error": "auth_failed", "message": result["message"]}, 400

        except Exception as e:
            # Security: Use secure logging to prevent traceback leakage in production
            from app.utils.logging_utils import format_api_error, log_exception_securely

            log_exception_securely("Error in provider auth", e)
            # Security: Never expose error details to client in production
            return format_api_error("INTERNAL_ERROR", "Authentication failed"), 500


class AuthVerifyEmail(Resource):
    """Handle email verification with simplified API"""

    def __init__(self):
        self.auth_service = AuthService()

    @rate_limit(3, 300)  # 3 requests per 5 minutes
    def post(self):
        """Simplified email verification - only requires email, code, and action"""
        try:
            data = request.get_json()

            if not data:
                return {"error": "No data provided"}, 400

            # Validate required fields
            email = data.get("email", "").strip().lower()
            code = data.get("code", "").strip()
            action = data.get("action", "signup")  # 'signup' or 'login'

            # Validate email and code
            if not validate_email(email):
                return {"error": "Invalid email format"}, 400

            if not validate_verification_code(code):
                return {"error": "Invalid verification code format"}, 400

            if action not in ["signup", "login"]:
                return {"error": 'Action must be either "signup" or "login"'}, 400

            if action == "signup":
                # Signup verification
                result = self.auth_service.verify_email_and_create_user(email, code)
            else:
                # For login (existing method)
                result = self.auth_service.verify_email_and_login(email, code)

            if result["success"]:
                # Get bootstrap data for initial app setup
                bootstrap_data = get_bootstrap_data(result["user"]["id"])

                user_profile = get_user_profile(result["user"]["id"])

                response_data = {
                    "message": result["message"],
                    "token": result["token"],
                    "user": {
                        "id": result["user"]["id"],
                        "email": result["user"]["email"],
                        "auth_provider": result["user"]["auth_provider"],
                        "name": result["user"].get("name"),
                    },
                    "bootstrap_data": bootstrap_data,
                    "user_profile": user_profile,
                    **student_fields(bootstrap_data),
                }
                return response_data, 200
            else:
                return {"error": result["message"]}, 400

        except Exception as e:
            logging.error(f"Error in email verification: {str(e)}")
            return {"error": "Internal server error"}, 500


class AuthVerifyToken(Resource):
    """Handle token-based email verification (for email links)"""

    def __init__(self):
        self.auth_service = AuthService()

    @rate_limit(10, 3600)  # 10 requests per hour
    def get(self, token):
        """Verify email using token from email link"""
        try:
            if not token:
                return {"error": "Verification token is required"}, 400

            result = self.auth_service.verify_email_with_token(token)

            if result["success"]:
                # Get bootstrap data for initial app setup
                bootstrap_data = get_bootstrap_data(result["user"]["id"])

                user_profile = get_user_profile(result["user"]["id"])

                response_data = {
                    "message": result["message"],
                    "token": result["token"],
                    "user": {
                        "id": result["user"]["id"],
                        "email": result["user"]["email"],
                        "auth_provider": result["user"]["auth_provider"],
                        "name": result["user"].get("name"),
                    },
                    "bootstrap_data": bootstrap_data,
                    "user_profile": user_profile,
                    **student_fields(bootstrap_data),
                }
                return response_data, 200
            else:
                return {"error": result["message"]}, 400

        except Exception as e:
            logging.error(f"Error in token verification: {str(e)}")
            return {"error": "Internal server error"}, 500
