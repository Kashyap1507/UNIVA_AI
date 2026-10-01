"""
Flask Application Factory - Updated with Enhanced Questions API Routes
File: app/__init__.py
"""

import logging
import os
import time
from datetime import date, datetime
from decimal import Decimal
from uuid import UUID

from flask import Flask, g, request
from flask.json.provider import DefaultJSONProvider
from flask_cors import CORS
from flask_restful import Api

from app.config import Config
from app.constants.analytics_events import BackendEvents
from app.services.analytics_service import analytics_service
from app.services.firebase_service import initialize_firebase


class CustomJSONProvider(DefaultJSONProvider):
    """Custom JSON provider to handle Firebase DatetimeWithNanoseconds and other special types"""

    def default(self, o):
        # Handle Firebase DatetimeWithNanoseconds objects
        try:
            from google.cloud.firestore_v1._helpers import DatetimeWithNanoseconds

            if isinstance(o, DatetimeWithNanoseconds):
                logging.info(f"Serializing DatetimeWithNanoseconds: {o}")
                return o.isoformat()
        except ImportError:
            # If google.cloud.firestore is not available, skip this check
            pass
        except Exception as e:
            logging.error(f"Error checking DatetimeWithNanoseconds: {e}")

        # Handle standard datetime objects
        if isinstance(o, (datetime, date)):
            logging.debug(f"Serializing datetime: {o}")
            return o.isoformat()

        # Handle other common types
        if isinstance(o, Decimal):
            return float(o)
        if isinstance(o, UUID):
            return str(o)

        # Check for any other Firebase/Firestore timestamp objects by type name
        type_name = type(o).__name__
        if "Timestamp" in type_name or "DatetimeWithNanoseconds" in type_name:
            logging.info(f"Serializing timestamp-like object {type_name}: {o}")
            if hasattr(o, "isoformat"):
                return o.isoformat()
            else:
                return str(o)

        return super().default(o)


def create_app(config_class=Config):
    """Create Flask application with configuration"""
    # Configure logging
    log_level = os.environ.get("LOG_LEVEL", "INFO").upper()
    logging.basicConfig(
        level=getattr(logging, log_level),
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
        handlers=[logging.StreamHandler()],
    )

    app = Flask(__name__)
    app.config.from_object(config_class)

    # Set custom JSON provider to handle DatetimeWithNanoseconds
    app.json = CustomJSONProvider(app)
    logging.info("Custom JSON provider configured to handle Firebase timestamps")

    # Initialize extensions
    CORS(app, origins=app.config["CORS_ORIGINS"])

    # Custom error handler for Flask-RESTful to ensure all errors have 'success' field
    def custom_handle_error(e):
        """Custom error handler to ensure consistent API response format"""
        code = getattr(e, "code", 500)
        data = getattr(e, "data", {})

        # Log the error for debugging
        logging.error(
            f"[CUSTOM_ERROR_HANDLER] Error caught: {e}, Code: {code}, Data: {data}"
        )

        # Handle RequestParser validation errors
        if "message" in data:
            error_msg = data["message"]
            if isinstance(error_msg, dict):
                # Convert dict of field errors to readable string
                error_msg = "; ".join([f"{k}: {v}" for k, v in error_msg.items()])
            logging.error(f"[CUSTOM_ERROR_HANDLER] Validation error: {error_msg}")
            return {"success": False, "error": str(error_msg)}, code

        # Handle other errors
        error_str = str(e)
        logging.error(f"[CUSTOM_ERROR_HANDLER] Generic error: {error_str}")
        return {"success": False, "error": error_str}, code

    api = Api(app)
    api.handle_error = custom_handle_error

    # Initialize cache (Redis/SimpleCache)
    from app.cache import init_cache

    init_cache(app)
    logging.info("Cache system initialized")

    # Initialize Firebase with app context
    with app.app_context():
        initialize_firebase(app)

    # Initialize Analytics Service (Sentry + Google Cloud Error Reporting)
    sentry_dsn = os.environ.get("SENTRY_DSN")
    gcp_project_id = os.environ.get("GOOGLE_CLOUD_PROJECT") or os.environ.get(
        "FIREBASE_PROJECT_ID"
    )
    environment = os.environ.get("FLASK_ENV", "development")

    analytics_service.initialize(
        sentry_dsn=sentry_dsn, gcp_project_id=gcp_project_id, environment=environment
    )
    logging.info("Analytics service initialized")

    # Log server start event
    analytics_service.log_event(
        BackendEvents.SERVER_START,
        {"environment": environment, "version": app.config.get("VERSION", "unknown")},
    )

    # Register API routes via centralized registry
    from app.api.routes import register_routes

    register_routes(api)

    # Health check endpoint
    @app.route("/health")
    def health_check():
        """Health check endpoint"""
        logging.info("Health check endpoint called")
        return {"status": "healthy", "service": "wizly-ai-server"}, 200

    # Static file serving for uploads directory
    @app.route("/uploads/<path:filename>")
    def serve_uploads(filename):
        """Serve static files from uploads directory with path traversal protection"""
        import os

        from flask import abort, send_from_directory
        from werkzeug.security import safe_join

        # Security: Validate filename to prevent path traversal attacks
        if ".." in filename or filename.startswith("/"):
            logging.warning(f"Path traversal attempt blocked: {filename}")
            abort(400, description="Invalid file path")

        uploads_dir = os.path.abspath(os.path.join(app.root_path, "..", "uploads"))

        # Security: Use safe_join to prevent directory traversal
        safe_path = safe_join(uploads_dir, filename)

        if safe_path is None or not safe_path.startswith(uploads_dir):
            logging.warning(f"Path traversal attempt blocked: {filename}")
            abort(403, description="Access denied")

        # Security: Verify file exists and is a file (not directory)
        if not os.path.exists(safe_path) or not os.path.isfile(safe_path):
            abort(404, description="File not found")

        return send_from_directory(uploads_dir, filename)

    # Add request logging middleware
    @app.before_request
    def before_request():
        """Track request start time and extract user context"""
        g.start_time = time.time()

        # Extract user ID from JWT token if present (for analytics)
        auth_header = request.headers.get("Authorization")
        if auth_header and auth_header.startswith("Bearer "):
            try:
                import jwt

                token = auth_header.split(" ")[1]
                decoded = jwt.decode(token, options={"verify_signature": False})
                user_id = decoded.get("user_id")
                if user_id:
                    analytics_service.set_user_context(user_id, decoded.get("email"))
            except Exception:
                pass  # Silently fail - don't block requests

    @app.after_request
    def after_request(response):
        """Track request completion and log analytics"""
        if hasattr(g, "start_time"):
            duration_ms = int((time.time() - g.start_time) * 1000)

            # Log API call analytics (only for API routes)
            if request.path.startswith("/api/"):
                from app.constants.analytics_events import APIEvents, Params

                analytics_service.log_event(
                    APIEvents.API_CALL,
                    {
                        Params.ENDPOINT: request.path,
                        Params.METHOD: request.method,
                        Params.DURATION_MS: duration_ms,
                        Params.STATUS_CODE: response.status_code,
                        Params.SUCCESS: 200 <= response.status_code < 400,
                    },
                )

        return response

    # Error handlers
    @app.errorhandler(404)
    def not_found(error):
        """Handle 404 errors"""
        from app.constants.analytics_events import ErrorEvents

        analytics_service.log_error(
            f"404 Not Found: {request.path}",
            context={"endpoint": request.path, "method": request.method},
        )
        return {"success": False, "error": "Resource not found"}, 404

    @app.errorhandler(500)
    def internal_error(error):
        """Handle 500 errors"""
        from app.constants.analytics_events import ErrorEvents

        analytics_service.log_error(
            f"500 Internal Server Error: {str(error)}",
            exception=error if isinstance(error, Exception) else None,
            context={"endpoint": request.path, "method": request.method},
            level="critical",
        )
        return {"success": False, "error": "Internal server error"}, 500

    @app.errorhandler(400)
    def bad_request(error):
        """Handle 400 errors"""
        analytics_service.log_error(
            f"400 Bad Request: {str(error)}",
            context={"endpoint": request.path, "method": request.method},
            level="warning",
        )
        return {"success": False, "error": "Bad request"}, 400

    # Global exception handler
    @app.errorhandler(Exception)
    def handle_exception(error):
        """Handle unhandled exceptions"""
        from app.constants.analytics_events import ErrorEvents

        analytics_service.log_error(
            f"Unhandled Exception: {str(error)}",
            exception=error,
            context={
                "endpoint": request.path,
                "method": request.method,
                "args": str(request.args),
            },
            level="critical",
        )
        return {"success": False, "error": "An unexpected error occurred"}, 500

    return app
