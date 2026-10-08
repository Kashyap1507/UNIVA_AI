"""
Admin Authentication API endpoints
"""

import logging

from flask import request
from flask_restful import Resource

from app.services.admin_auth_service import AdminAuthService
from app.utils.decorators import admin_required, rate_limit
from app.utils.validators import validate_email


class AdminLogin(Resource):
    """Handle admin login"""

    def __init__(self):
        self.admin_auth_service = AdminAuthService()

    @rate_limit(5, 3600)  # 5 attempts per hour
    def post(self):
        try:
            data = request.get_json()

            if not data:
                return {"error": "No data provided"}, 400

            email = data.get("email", "").strip().lower()
            password = data.get("password", "")
            if not isinstance(password, str) or not password:
                return {"error": "Email and password are required"}, 400

            # Validate email
            if not validate_email(email):
                return {"error": "Invalid email format"}, 400

            # Process admin login
            result = self.admin_auth_service.login_admin(email, password)

            if result["success"]:
                return {
                    "message": result["message"],
                    "token": result["token"],
                    "user": {
                        "id": result["user"]["id"],
                        "email": result["user"]["email"],
                        "is_admin": result["user"]["is_admin"],
                        "admin_permissions": result["user"]["admin_permissions"],
                    },
                }, 200
            else:
                return {"error": result["message"]}, 401

        except Exception as e:
            logging.error(f"Error in admin login: {str(e)}")
            return {"error": "Internal server error"}, 500


class AdminLogout(Resource):
    """Handle admin logout"""

    @admin_required
    def post(self, current_user=None):
        """
        Admin logout - in a real implementation, you might want to
        invalidate the token on the server side (blacklist)
        """
        try:
            return {"message": "Admin logged out successfully"}, 200

        except Exception as e:
            logging.error(f"Error in admin logout: {str(e)}")
            return {"error": "Internal server error"}, 500


class AdminMe(Resource):
    """Get current admin user info"""

    def __init__(self):
        self.admin_auth_service = AdminAuthService()

    @admin_required
    def get(self, current_user=None):
        """Get current admin user information"""
        try:
            return {
                "user": {
                    "id": current_user["id"],
                    "email": current_user["email"],
                    "is_admin": current_user["is_admin"],
                    "admin_permissions": current_user["admin_permissions"],
                    "admin_last_login": current_user["admin_last_login"],
                    "admin_created_at": current_user["admin_created_at"],
                }
            }, 200

        except Exception as e:
            logging.error(f"Error getting admin user info: {str(e)}")
            return {"error": "Internal server error"}, 500


class AdminVerify(Resource):
    """Verify admin token"""

    def __init__(self):
        self.admin_auth_service = AdminAuthService()

    def post(self):
        try:
            data = request.get_json()

            if not data:
                logging.error(f"[ADMIN VERIFY] No data provided")
                return {"error": "No data provided"}, 400

            token = data.get("token")

            if not token:
                logging.error(f"[ADMIN VERIFY] Token is required")
                return {"error": "Token is required"}, 400

            # Verify token
            result = self.admin_auth_service.verify_admin_token(token)

            if result["success"]:
                user_data = {
                    "id": result["user"]["id"],
                    "email": result["user"]["email"],
                    "is_admin": result["user"]["is_admin"],
                    "admin_permissions": result["user"]["admin_permissions"],
                }
                return {"valid": True, "user": user_data}, 200
            else:
                logging.error(
                    f"[ADMIN VERIFY] Verification failed: {result.get('message')}"
                )
                return {"valid": False, "error": result["message"]}, 401

        except Exception as e:
            # Security: Use secure logging to prevent traceback leakage in production
            from app.utils.logging_utils import log_exception_securely

            log_exception_securely("[ADMIN VERIFY] Error verifying admin token", e)
            return {"error": "Internal server error"}, 500


class AdminUsers(Resource):
    """Manage admin users"""

    def __init__(self):
        self.admin_auth_service = AdminAuthService()

    @admin_required("admin:users")
    def get(self, current_user=None):
        """Get list of admin users - requires admin:users permission"""
        try:
            limit = request.args.get("limit", 50, type=int)

            result = self.admin_auth_service.get_admin_users(limit=limit)

            if result["success"]:
                return {"admins": result["admins"], "count": result["count"]}, 200
            else:
                return {"error": result["message"]}, 500

        except Exception as e:
            logging.error(f"Error getting admin users: {str(e)}")
            return {"error": "Internal server error"}, 500

    @admin_required("admin:users")
    def post(self, current_user=None):
        """Create new admin user - requires admin:users permission"""
        try:
            data = request.get_json()

            if not data:
                return {"error": "No data provided"}, 400

            email = data.get("email", "").strip().lower()
            permissions = data.get(
                "permissions", ["admin:questions", "admin:dashboard"]
            )

            # Validate email
            if not validate_email(email):
                return {"error": "Invalid email format"}, 400

            # Create admin user
            result = self.admin_auth_service.create_admin_user(email, permissions)

            if result["success"]:
                return {"message": result["message"], "user": result["user"]}, 201
            else:
                return {"error": result["message"]}, 400

        except Exception as e:
            logging.error(f"Error creating admin user: {str(e)}")
            return {"error": "Internal server error"}, 500


class AdminUserRevoke(Resource):
    """Revoke admin access from user"""

    def __init__(self):
        self.admin_auth_service = AdminAuthService()

    @admin_required("admin:users")
    def delete(self, user_id, current_user=None):
        """Revoke admin access - requires admin:users permission"""
        try:
            if not user_id:
                return {"error": "User ID is required"}, 400

            # Prevent self-revocation
            if user_id == current_user["id"]:
                return {"error": "Cannot revoke your own admin access"}, 400

            result = self.admin_auth_service.revoke_admin_access(user_id)

            if result["success"]:
                return {"message": result["message"]}, 200
            else:
                return {"error": result["message"]}, 400

        except Exception as e:
            logging.error(f"Error revoking admin access: {str(e)}")
            return {"error": "Internal server error"}, 500
