"""
Admin Authentication Service
Handles admin-specific authentication operations
"""

import logging
import os
from datetime import datetime, timedelta
from functools import wraps
from typing import Any, Dict, Optional

import jwt
from flask import request

from app.models.user import User
from app.services.firebase_service import get_firestore_client


class AdminAuthService:
    """Service for admin authentication operations"""

    def __init__(self):
        self.db = get_firestore_client()
        # Security: Load JWT secret from environment - fail if not set
        self.jwt_secret = os.environ.get("JWT_SECRET_KEY")
        if not self.jwt_secret:
            raise ValueError(
                "JWT_SECRET_KEY must be set in environment for admin authentication"
            )
        self.jwt_expiry_hours = 8  # Admin tokens expire in 8 hours

    def login_admin(self, email: str, password: str = None) -> Dict[str, Any]:
        """
        Admin login with email + password.

        The password hash is set with scripts/create_admin.py. Admins without a
        password set cannot log in.
        """
        try:
            admin_user = User.get_admin_by_email(self.db, email)

            # Same message for unknown email and wrong password
            if not admin_user or not admin_user.check_admin_password(password):
                return {
                    "success": False,
                    "message": "Invalid email or password",
                }

            # Update admin last login
            admin_user.update_admin_last_login()
            admin_user.save(self.db)

            # Generate JWT token
            token = self._generate_admin_token(admin_user.id)

            return {
                "success": True,
                "message": "Admin login successful",
                "token": token,
                "user": admin_user.to_api_dict(),
            }

        except Exception as e:
            logging.error(f"Error in admin login: {str(e)}")
            return {
                "success": False,
                "message": "Internal server error during admin login",
            }

    def verify_admin_token(self, token: str) -> Dict[str, Any]:
        """Verify admin JWT token and return user data"""

        try:
            payload = jwt.decode(token, self.jwt_secret, algorithms=["HS256"])

            # Get user from database
            user = User.get_by_id(self.db, payload["user_id"])

            if not user:
                logging.error(
                    f"[ADMIN AUTH] User not found for ID: {payload['user_id']}"
                )
                return {
                    "success": False,
                    "message": "Invalid admin token or user not found",
                }

            if not user.is_admin_user():
                logging.error(f"[ADMIN AUTH] User is not admin or not active")
                return {
                    "success": False,
                    "message": "Invalid admin token or user not found",
                }

            api_dict = user.to_api_dict()

            return {"success": True, "user": api_dict}

        except jwt.ExpiredSignatureError:
            logging.error(f"[ADMIN AUTH] JWT token expired")
            return {"success": False, "message": "Admin token has expired"}
        except jwt.InvalidTokenError as e:
            logging.error(f"[ADMIN AUTH] Invalid JWT token: {str(e)}")
            return {"success": False, "message": "Invalid admin token"}
        except Exception as e:
            # Security: Use secure logging to prevent traceback leakage in production
            from app.utils.logging_utils import log_exception_securely

            log_exception_securely("[ADMIN AUTH] Error verifying admin token", e)
            return {"success": False, "message": "Token verification failed"}

    def create_admin_user(self, email: str, permissions: list = None) -> Dict[str, Any]:
        """Create a new admin user"""
        try:
            # Check if user already exists
            existing_user = User.get_by_email(self.db, email)

            if existing_user:
                if existing_user.is_admin:
                    return {"success": False, "message": "User is already an admin"}
                else:
                    # Grant admin access to existing user
                    existing_user.grant_admin_access(permissions)
                    existing_user.save(self.db)

                    return {
                        "success": True,
                        "message": "Admin access granted to existing user",
                        "user": existing_user.to_api_dict(),
                    }
            else:
                # Create new admin user
                new_admin = User(
                    email=email,
                    auth_provider="email",
                    verified=True,
                    is_active=True,
                    is_admin=True,
                    admin_permissions=permissions
                    or ["admin:questions", "admin:dashboard"],
                    admin_created_at=datetime.now(),
                )

                # Validate and save
                is_valid, validation_message = new_admin.validate()
                if not is_valid:
                    return {"success": False, "message": validation_message}

                new_admin.save(self.db)

                return {
                    "success": True,
                    "message": "Admin user created successfully",
                    "user": new_admin.to_api_dict(),
                }

        except Exception as e:
            logging.error(f"Error creating admin user: {str(e)}")
            return {"success": False, "message": "Failed to create admin user"}

    def get_admin_users(self, limit: int = 50) -> Dict[str, Any]:
        """Get list of all admin users"""
        try:
            admin_users = User.get_admin_users(self.db, limit=limit)

            # admin_users is now already a list of dictionaries after your changes,
            # so we don't need to call to_api_dict() anymore
            return {
                "success": True,
                "admins": admin_users,  # Already serialized dictionaries
                "count": len(admin_users),
            }

        except Exception as e:
            logging.error(f"Error getting admin users: {str(e)}")
            return {"success": False, "message": "Failed to retrieve admin users"}

    def revoke_admin_access(self, user_id: str) -> Dict[str, Any]:
        """Revoke admin access from a user"""
        try:
            user = User.get_by_id(self.db, user_id)

            if not user:
                return {"success": False, "message": "User not found"}

            if not user.is_admin:
                return {"success": False, "message": "User is not an admin"}

            user.revoke_admin_access()
            user.save(self.db)

            return {"success": True, "message": "Admin access revoked successfully"}

        except Exception as e:
            logging.error(f"Error revoking admin access: {str(e)}")
            return {"success": False, "message": "Failed to revoke admin access"}

    def _generate_admin_token(self, user_id: str) -> str:
        """Generate JWT token for admin user"""
        payload = {
            "user_id": user_id,
            "is_admin": True,
            "exp": datetime.utcnow() + timedelta(hours=self.jwt_expiry_hours),
            "iat": datetime.utcnow(),
        }

        return jwt.encode(payload, self.jwt_secret, algorithm="HS256")


def admin_required(f):
    """Decorator to require admin authentication"""

    @wraps(f)
    def decorated_function(*args, **kwargs):
        try:

            # Get token from Authorization header
            auth_header = request.headers.get("Authorization")

            if not auth_header or not auth_header.startswith("Bearer "):
                logging.error("Missing or invalid authorization header")
                return {
                    "success": False,
                    "error": "Missing or invalid authorization header",
                }, 401

            token = auth_header.split(" ")[1]

            # Verify token
            admin_service = AdminAuthService()
            verification_result = admin_service.verify_admin_token(token)

            if not verification_result.get("success"):
                logging.error(
                    f"Token verification failed: {verification_result.get('message')}"
                )
                return {
                    "success": False,
                    "error": verification_result.get("message", "Invalid admin token"),
                }, 401

            # Add user info to request context if needed
            request.admin_user = verification_result.get("user")

            return f(*args, **kwargs)

        except Exception as e:
            # Security: Use secure logging to prevent traceback leakage in production
            from app.utils.logging_utils import log_exception_securely

            log_exception_securely("Error in admin_required decorator", e)
            return {"success": False, "error": "Authentication failed"}, 401

    return decorated_function
