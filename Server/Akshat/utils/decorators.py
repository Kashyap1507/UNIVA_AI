"""
Utility decorators for authentication, rate limiting, etc.
Fixed to avoid circular imports
"""

import logging
import os
import time
from datetime import datetime
from functools import wraps

import jwt
from flask import current_app, request

# Simple in-memory rate limiting store (use Redis in production)
rate_limit_store = {}


def get_current_user_id(current_user):
    """Return the user ID from the dict that ``jwt_required`` injects.

    ``jwt_required`` passes ``User.to_api_dict()``, which carries ``id``, not
    ``user_id``. ``user_id`` is accepted as a fallback for older callers.
    """
    if not current_user:
        return None
    return current_user.get("id") or current_user.get("user_id")


def jwt_required(f):
    """Decorator to require JWT authentication"""

    @wraps(f)
    def decorated_function(*args, **kwargs):
        # Get token from Authorization header
        auth_header = request.headers.get("Authorization")

        if not auth_header:
            return {"success": False, "error": "Authorization header is required"}, 401

        # Extract token from "Bearer <token>"
        try:
            token = auth_header.split(" ")[1]
        except IndexError:
            return {
                "success": False,
                "error": "Invalid authorization header format",
            }, 401

        # Verify token directly here to avoid circular import
        try:
            payload = jwt.decode(
                token, current_app.config["JWT_SECRET_KEY"], algorithms=["HS256"]
            )

            # Get user data from Firestore
            from app.models.user import User
            from app.services.firebase_service import get_firestore_client

            db = get_firestore_client()
            user = User.get_by_id(db, payload["user_id"])

            if not user:
                return {"success": False, "error": "User not found"}, 401

            current_user = user.to_api_dict()

        except jwt.ExpiredSignatureError:
            logging.warning("JWT token has expired")
            return {"success": False, "error": "Token has expired"}, 401
        except jwt.InvalidTokenError as e:
            logging.warning(f"Invalid JWT token: {str(e)}")
            return {"success": False, "error": "Invalid token"}, 401
        except Exception as e:
            logging.error(f"Error verifying JWT token: {str(e)}", exc_info=True)
            return {"success": False, "error": "Authentication failed"}, 401

        # Pass current user to the decorated function
        return f(current_user=current_user, *args, **kwargs)

    return decorated_function


def rate_limit(max_requests, window_seconds):
    """Decorator for rate limiting requests - testing friendly"""

    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            try:
                # Check if we're in testing mode
                flask_env = os.environ.get("FLASK_ENV", "").lower()
                testing_mode = flask_env in ["testing", "development"]

                # Skip rate limiting in testing mode or if disabled
                if testing_mode and os.environ.get("DISABLE_RATE_LIMITING") == "true":
                    logging.info("Rate limiting disabled for testing")
                    return f(*args, **kwargs)

                # Get client identifier (IP address)
                client_ip = request.environ.get(
                    "HTTP_X_FORWARDED_FOR", request.environ.get("REMOTE_ADDR")
                )
                endpoint = request.endpoint

                # Create unique key for this client and endpoint
                key = f"{client_ip}:{endpoint}"
                current_time = time.time()

                # Clean up old entries
                if key in rate_limit_store:
                    rate_limit_store[key] = [
                        timestamp
                        for timestamp in rate_limit_store[key]
                        if current_time - timestamp < window_seconds
                    ]
                else:
                    rate_limit_store[key] = []

                # Check if rate limit exceeded
                if len(rate_limit_store[key]) >= max_requests:
                    # In development, log rate limit hits
                    if testing_mode:
                        logging.warning(
                            f"Rate limit hit for {key}: {len(rate_limit_store[key])} requests in {window_seconds} seconds"
                        )

                    return {
                        "error": "Rate limit exceeded",
                        "retry_after": window_seconds,
                        "current_requests": len(rate_limit_store[key]),
                        "max_requests": max_requests,
                    }, 429

                # Add current request timestamp
                rate_limit_store[key].append(current_time)

                return f(*args, **kwargs)

            except Exception as e:
                logging.error(f"Error in rate limiting: {str(e)}")
                # Continue without rate limiting if there's an error
                return f(*args, **kwargs)

        return decorated_function

    return decorator


def clear_rate_limits():
    """Helper function to clear rate limits (useful for testing)"""
    rate_limit_store.clear()
    logging.info("Rate limit store cleared")


def admin_required(permission: str = None):
    """Decorator to require admin authentication with optional permission check"""

    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            try:
                # Get token from Authorization header
                auth_header = request.headers.get("Authorization")

                if not auth_header:
                    return {
                        "success": False,
                        "error": "Authorization header is required",
                    }, 401

                # Extract token from "Bearer <token>"
                try:
                    token = auth_header.split(" ")[1]
                except IndexError:
                    return {
                        "success": False,
                        "error": "Invalid authorization header format",
                    }, 401

                # Verify token
                try:
                    payload = jwt.decode(
                        token,
                        current_app.config["JWT_SECRET_KEY"],
                        algorithms=["HS256"],
                    )

                    # Get user data from Firestore
                    from app.models.user import User
                    from app.services.firebase_service import get_firestore_client

                    db = get_firestore_client()
                    user = User.get_by_id(db, payload["user_id"])

                    if not user:
                        return {"success": False, "error": "User not found"}, 401

                    # Check if user is admin
                    if not user.is_admin_user():
                        return {"success": False, "error": "Admin access required"}, 403

                    # Check specific permission if provided
                    if permission and not user.has_admin_permission(permission):
                        return {
                            "success": False,
                            "error": f'Admin permission "{permission}" required',
                        }, 403

                    # Update admin last login
                    user.update_admin_last_login()
                    user.save(db)

                    current_user = user.to_api_dict()

                except jwt.ExpiredSignatureError:
                    return {"success": False, "error": "Token has expired"}, 401
                except jwt.InvalidTokenError:
                    return {"success": False, "error": "Invalid token"}, 401
                except Exception as e:
                    logging.error(f"Error verifying admin token: {str(e)}")
                    return {"success": False, "error": "Authentication failed"}, 401

                # Pass current user to the decorated function
                return f(current_user=current_user, *args, **kwargs)

            except Exception as e:
                logging.error(f"Error in admin authentication: {str(e)}")
                return {"success": False, "error": "Authentication failed"}, 401

        return decorated_function

    # Allow decorator to be used with or without arguments
    if callable(permission):
        # Called as @admin_required (without parentheses)
        func = permission
        permission = None
        return decorator(func)
    else:
        # Called as @admin_required() or @admin_required('permission')
        return decorator


def get_rate_limit_status():
    """Helper function to check current rate limit status"""
    return {
        "total_keys": len(rate_limit_store),
        "keys": list(rate_limit_store.keys()),
        "details": {k: len(v) for k, v in rate_limit_store.items()},
    }
