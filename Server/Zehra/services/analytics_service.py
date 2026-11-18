"""
Analytics Service for Wizly Backend

This service provides analytics tracking, structured logging, and error reporting
for the Wizly backend using Sentry and Google Cloud Error Reporting.

Architecture:
- Sentry: Error tracking, performance monitoring, user tracking
- Google Cloud Error Reporting: GCP-native error tracking
- Structured Logging: JSON-formatted logs for analysis

Usage:
    from app.services.analytics_service import analytics_service

    # Log an event
    analytics_service.log_event('api_user_login', {
        'user_id': '123',
        'success': True,
        'duration_ms': 250
    })

    # Track API call performance
    with analytics_service.track_api_call('/api/auth/login', 'POST'):
        # API logic here
        pass

    # Log an error
    analytics_service.log_error('API Error', exception, {
        'user_id': '123',
        'endpoint': '/api/documents'
    })
"""

import logging
import time
import traceback
from contextlib import contextmanager
from functools import wraps
from typing import Any, Dict, Optional

import sentry_sdk
from flask import g, request
from google.cloud import error_reporting
from google.cloud import logging as cloud_logging

from app.constants.analytics_events import APIEvents, BackendEvents, ErrorEvents, Params

# Configure structured logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


class AnalyticsService:
    """
    Centralized analytics and error tracking service

    Provides:
    - Event logging with structured data
    - API performance tracking
    - Error tracking and reporting
    - User context management
    - Sentry integration
    - Google Cloud Error Reporting integration
    """

    def __init__(self):
        self.enabled = False
        self.sentry_enabled = False
        self.gcp_error_reporting_enabled = False
        self.error_client = None
        self.cloud_logger = None

    def initialize(
        self,
        sentry_dsn: Optional[str] = None,
        gcp_project_id: Optional[str] = None,
        environment: str = "development",
    ):
        """
        Initialize analytics service with Sentry and GCP Error Reporting

        Args:
            sentry_dsn: Sentry DSN for error tracking
            gcp_project_id: Google Cloud Project ID for error reporting
            environment: Environment name ('development', 'staging', 'production')
        """
        try:
            # Initialize Sentry
            if sentry_dsn:
                sentry_sdk.init(
                    dsn=sentry_dsn,
                    environment=environment,
                    traces_sample_rate=1.0 if environment == "development" else 0.1,
                    profiles_sample_rate=1.0 if environment == "development" else 0.1,
                    enable_tracing=True,
                    send_default_pii=False,  # Don't send PII by default
                    before_send=self._before_send_sentry,
                    integrations=[
                        # Flask integration is automatically detected
                    ],
                )
                self.sentry_enabled = True
                logger.info("✅ Sentry initialized successfully")
            else:
                logger.warning("⚠️ Sentry DSN not provided - error tracking disabled")

            # Initialize Google Cloud Error Reporting
            if gcp_project_id:
                try:
                    self.error_client = error_reporting.Client(
                        project=gcp_project_id, service="wizly-backend"
                    )
                    self.cloud_logger = cloud_logging.Client(project=gcp_project_id)
                    self.gcp_error_reporting_enabled = True
                    logger.info(
                        "✅ Google Cloud Error Reporting initialized successfully"
                    )
                except Exception as e:
                    logger.error(
                        f"❌ Failed to initialize GCP Error Reporting: {str(e)}"
                    )

            self.enabled = True
            logger.info(
                f"✅ Analytics Service initialized (Environment: {environment})"
            )

        except Exception as e:
            logger.error(f"❌ Failed to initialize Analytics Service: {str(e)}")
            self.enabled = False

    def _before_send_sentry(self, event, hint):
        """
        Sentry before_send hook to filter/modify events before sending

        Args:
            event: Sentry event dict
            hint: Additional context dict

        Returns:
            Modified event dict or None to drop the event
        """
        # Filter out sensitive data
        if "request" in event:
            request_data = event["request"]

            # Remove sensitive headers
            if "headers" in request_data:
                headers = request_data["headers"]
                sensitive_headers = ["Authorization", "Cookie", "X-API-Key"]
                for header in sensitive_headers:
                    if header in headers:
                        headers[header] = "[REDACTED]"

            # Remove sensitive query params
            if "query_string" in request_data:
                query = request_data["query_string"]
                if "token" in query or "password" in query:
                    request_data["query_string"] = "[REDACTED]"

        return event

    def set_user_context(self, user_id: str, email: Optional[str] = None):
        """
        Set user context for analytics tracking

        Args:
            user_id: User ID
            email: User email (optional)
        """
        if not self.enabled:
            return

        try:
            # Set Sentry user context
            if self.sentry_enabled:
                sentry_sdk.set_user({"id": user_id, "email": email})

            # Store in Flask g for request context
            g.user_id = user_id
            g.user_email = email

        except Exception as e:
            logger.error(f"Failed to set user context: {str(e)}")

    def clear_user_context(self):
        """Clear user context"""
        if not self.enabled:
            return

        try:
            if self.sentry_enabled:
                sentry_sdk.set_user(None)

            if hasattr(g, "user_id"):
                del g.user_id
            if hasattr(g, "user_email"):
                del g.user_email

        except Exception as e:
            logger.error(f"Failed to clear user context: {str(e)}")

    def log_event(self, event_name: str, params: Optional[Dict[str, Any]] = None):
        """
        Log an analytics event

        Args:
            event_name: Name of the event (use constants from analytics_events.py)
            params: Optional dictionary of event parameters
        """
        if not self.enabled:
            return

        try:
            params = params or {}

            # Add standard params
            params[Params.TIMESTAMP] = time.time()
            params[Params.PLATFORM] = "backend"

            # Add user context if available
            if hasattr(g, "user_id"):
                params[Params.USER_ID] = g.user_id

            # Log to structured logger
            logger.info(
                f"📊 Analytics Event: {event_name}",
                extra={"event_name": event_name, "params": params},
            )

            # Send breadcrumb to Sentry
            if self.sentry_enabled:
                sentry_sdk.add_breadcrumb(
                    category="analytics", message=event_name, data=params, level="info"
                )

        except Exception as e:
            logger.error(f"Failed to log event '{event_name}': {str(e)}")

    def log_error(
        self,
        error_message: str,
        exception: Optional[Exception] = None,
        context: Optional[Dict[str, Any]] = None,
        level: str = "error",
    ):
        """
        Log an error with context

        Args:
            error_message: Human-readable error message
            exception: Optional exception object
            context: Optional context dictionary
            level: Error level ('error', 'warning', 'critical')
        """
        if not self.enabled:
            return

        try:
            context = context or {}

            # Add user context if available
            if hasattr(g, "user_id"):
                context[Params.USER_ID] = g.user_id

            # Add request context if available
            if request:
                context[Params.ENDPOINT] = request.path
                context[Params.METHOD] = request.method

            # Log to standard logger
            log_func = getattr(logger, level, logger.error)
            log_func(error_message, extra={"context": context}, exc_info=exception)

            # Send to Sentry
            if self.sentry_enabled:
                with sentry_sdk.push_scope() as scope:
                    # Add context
                    for key, value in context.items():
                        scope.set_extra(key, value)

                    # Capture exception or message
                    if exception:
                        sentry_sdk.capture_exception(exception)
                    else:
                        sentry_sdk.capture_message(error_message, level=level)

            # Send to Google Cloud Error Reporting
            if self.gcp_error_reporting_enabled and self.error_client:
                try:
                    self.error_client.report(
                        error_message,
                        http_context={
                            "method": request.method if request else "UNKNOWN",
                            "url": request.url if request else "UNKNOWN",
                            "user_agent": (
                                request.headers.get("User-Agent", "") if request else ""
                            ),
                        },
                        user=context.get(Params.USER_ID, "unknown"),
                    )
                except Exception as e:
                    logger.error(f"Failed to report to GCP Error Reporting: {str(e)}")

        except Exception as e:
            logger.error(f"Failed to log error: {str(e)}")

    @contextmanager
    def track_api_call(self, endpoint: str, method: str):
        """
        Context manager to track API call performance

        Usage:
            with analytics_service.track_api_call('/api/auth/login', 'POST'):
                # API logic here
                pass

        Args:
            endpoint: API endpoint path
            method: HTTP method
        """
        start_time = time.time()
        success = True
        status_code = 200
        error = None

        # Start Sentry transaction
        transaction = None
        if self.sentry_enabled:
            transaction = sentry_sdk.start_transaction(
                op="http.server", name=f"{method} {endpoint}"
            )
            transaction.__enter__()

        try:
            yield
        except Exception as e:
            success = False
            error = e
            status_code = 500
            raise
        finally:
            duration_ms = int((time.time() - start_time) * 1000)

            # Log API call event
            self.log_event(
                APIEvents.API_CALL,
                {
                    Params.ENDPOINT: endpoint,
                    Params.METHOD: method,
                    Params.DURATION_MS: duration_ms,
                    Params.SUCCESS: success,
                    Params.STATUS_CODE: status_code,
                },
            )

            # End Sentry transaction
            if transaction:
                if error:
                    transaction.set_status("internal_error")
                else:
                    transaction.set_status("ok")
                transaction.__exit__(None, None, None)

    def track_performance(
        self,
        operation_name: str,
        duration_ms: int,
        metadata: Optional[Dict[str, Any]] = None,
    ):
        """
        Track performance metrics for an operation

        Args:
            operation_name: Name of the operation
            duration_ms: Duration in milliseconds
            metadata: Optional metadata dictionary
        """
        if not self.enabled:
            return

        try:
            metadata = metadata or {}
            metadata[Params.DURATION_MS] = duration_ms

            self.log_event(f"performance_{operation_name}", metadata)

        except Exception as e:
            logger.error(f"Failed to track performance: {str(e)}")

    def track_external_api_call(
        self,
        service_name: str,
        operation: str,
        duration_ms: int,
        success: bool,
        error_message: Optional[str] = None,
    ):
        """
        Track external API calls (e.g., Gemini API, SendGrid, etc.)

        Args:
            service_name: Name of the external service
            operation: Operation performed
            duration_ms: Duration in milliseconds
            success: Whether the call was successful
            error_message: Optional error message
        """
        if not self.enabled:
            return

        try:
            event_name = (
                BackendEvents.GEMINI_API_CALL
                if "gemini" in service_name.lower()
                else BackendEvents.EXTERNAL_SERVICE_ERROR
            )

            params = {
                "service": service_name,
                "operation": operation,
                Params.DURATION_MS: duration_ms,
                Params.SUCCESS: success,
            }

            if error_message:
                params[Params.ERROR_MESSAGE] = error_message

            self.log_event(event_name, params)

        except Exception as e:
            logger.error(f"Failed to track external API call: {str(e)}")


# ==============================================
# DECORATORS
# ==============================================


def track_api_endpoint(event_name: Optional[str] = None):
    """
    Decorator to track API endpoint calls

    Usage:
        @track_api_endpoint('api_user_login')
        def login():
            # API logic
            pass

    Args:
        event_name: Optional custom event name
    """

    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            start_time = time.time()
            success = True
            error = None

            try:
                result = func(*args, **kwargs)
                return result
            except Exception as e:
                success = False
                error = e
                raise
            finally:
                duration_ms = int((time.time() - start_time) * 1000)

                # Determine event name
                _event_name = event_name or f"api_{func.__name__}"

                # Log event
                analytics_service.log_event(
                    _event_name,
                    {
                        Params.DURATION_MS: duration_ms,
                        Params.SUCCESS: success,
                        Params.ERROR_MESSAGE: str(error) if error else None,
                    },
                )

        return wrapper

    return decorator


def track_processing_time(operation_name: str):
    """
    Decorator to track processing time for functions

    Usage:
        @track_processing_time('document_ocr')
        def process_document(doc):
            # Processing logic
            pass

    Args:
        operation_name: Name of the operation being tracked
    """

    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            start_time = time.time()

            try:
                result = func(*args, **kwargs)
                return result
            finally:
                duration_ms = int((time.time() - start_time) * 1000)
                analytics_service.track_performance(operation_name, duration_ms)

        return wrapper

    return decorator


# ==============================================
# SINGLETON INSTANCE
# ==============================================

analytics_service = AnalyticsService()
