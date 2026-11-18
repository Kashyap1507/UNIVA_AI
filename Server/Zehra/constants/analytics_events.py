"""
Backend Analytics Event Constants

This file defines analytics events for the Wizly backend server.
Event names match the mobile app naming convention for consistency.

Architecture:
- Mobile apps: Firebase Analytics
- Backend: Sentry + Google Cloud Error Reporting + Structured Logging

Event Naming Convention:
- Use snake_case (e.g., 'api_user_login')
- Match mobile event names where applicable
- Prefix backend-specific events with 'api_' or 'backend_'
"""

# ==============================================
# API ENDPOINT EVENTS
# ==============================================


class APIEvents:
    """Events for API endpoint calls"""

    # Generic API events
    API_CALL = "api_call"
    API_ERROR = "api_error"
    API_TIMEOUT = "api_timeout"
    API_RATE_LIMIT = "api_rate_limit"

    # Authentication
    API_USER_SIGNUP = "api_user_signup"
    API_USER_LOGIN = "api_user_login"
    API_USER_LOGOUT = "api_user_logout"
    API_AUTH_TOKEN_REFRESH = "api_auth_token_refresh"
    API_AUTH_FAILED = "api_auth_failed"
    API_PASSWORD_RESET_REQUEST = "api_password_reset_request"
    API_PASSWORD_RESET_COMPLETE = "api_password_reset_complete"

    # Bootstrap & Sync
    API_BOOTSTRAP_REQUEST = "api_bootstrap_request"
    API_BOOTSTRAP_SUCCESS = "api_bootstrap_success"
    API_BOOTSTRAP_FAILED = "api_bootstrap_failed"

    # Document Upload & Processing
    API_DOCUMENT_UPLOAD_START = "api_document_upload_start"
    API_DOCUMENT_UPLOAD_SUCCESS = "api_document_upload_success"
    API_DOCUMENT_UPLOAD_FAILED = "api_document_upload_failed"
    API_DOCUMENT_DELETE = "api_document_delete"
    API_DOCUMENT_LIST_FETCHED = "api_document_list_fetched"

    # Progress & Analytics
    API_PROGRESS_REPORT_GENERATED = "api_progress_report_generated"
    API_ANALYTICS_FETCHED = "api_analytics_fetched"

    # Admin
    API_ADMIN_LOGIN = "api_admin_login"
    API_ADMIN_ACTION = "api_admin_action"


# ==============================================
# BACKEND PROCESSING EVENTS
# ==============================================


class BackendEvents:
    """Events for backend processing and services"""

    # AI/Gemini API
    GEMINI_API_CALL = "gemini_api_call"
    GEMINI_API_SUCCESS = "gemini_api_success"
    GEMINI_API_FAILED = "gemini_api_failed"
    GEMINI_RATE_LIMIT = "gemini_rate_limit"

    # OCR Processing
    OCR_PROCESSING_START = "ocr_processing_start"
    OCR_PROCESSING_SUCCESS = "ocr_processing_success"
    OCR_PROCESSING_FAILED = "ocr_processing_failed"

    # Firebase Operations
    FIRESTORE_READ = "firestore_read"
    FIRESTORE_WRITE = "firestore_write"
    FIRESTORE_UPDATE = "firestore_update"
    FIRESTORE_DELETE = "firestore_delete"
    FIRESTORE_QUERY = "firestore_query"
    FIRESTORE_ERROR = "firestore_error"

    # Storage Operations
    FIREBASE_STORAGE_UPLOAD = "firebase_storage_upload"
    FIREBASE_STORAGE_DOWNLOAD = "firebase_storage_download"
    FIREBASE_STORAGE_DELETE = "firebase_storage_delete"
    FIREBASE_STORAGE_ERROR = "firebase_storage_error"

    # Email Operations
    EMAIL_SENT = "email_sent"
    EMAIL_FAILED = "email_failed"
    EMAIL_VERIFICATION_SENT = "email_verification_sent"
    EMAIL_PASSWORD_RESET_SENT = "email_password_reset_sent"

    # Push Notifications
    FCM_NOTIFICATION_SENT = "fcm_notification_sent"
    FCM_NOTIFICATION_FAILED = "fcm_notification_failed"

    # Cache Operations
    CACHE_HIT = "cache_hit"
    CACHE_MISS = "cache_miss"
    CACHE_SET = "cache_set"
    CACHE_INVALIDATE = "cache_invalidate"
    CACHE_ERROR = "cache_error"

    # Background Jobs
    BACKGROUND_JOB_START = "background_job_start"
    BACKGROUND_JOB_SUCCESS = "background_job_success"
    BACKGROUND_JOB_FAILED = "background_job_failed"

    # Server Health
    HEALTH_CHECK = "health_check"
    SERVER_START = "server_start"
    SERVER_SHUTDOWN = "server_shutdown"


# ==============================================
# ERROR & EXCEPTION EVENTS
# ==============================================


class ErrorEvents:
    """Events for errors and exceptions"""

    # Application Errors
    UNHANDLED_EXCEPTION = "unhandled_exception"
    VALIDATION_ERROR = "validation_error"
    AUTHORIZATION_ERROR = "authorization_error"
    NOT_FOUND_ERROR = "not_found_error"
    RATE_LIMIT_EXCEEDED = "rate_limit_exceeded"

    # Database Errors
    DATABASE_CONNECTION_ERROR = "database_connection_error"
    DATABASE_QUERY_ERROR = "database_query_error"
    DATABASE_TIMEOUT = "database_timeout"

    # External Service Errors
    EXTERNAL_SERVICE_ERROR = "external_service_error"
    EXTERNAL_API_TIMEOUT = "external_api_timeout"
    EXTERNAL_API_RATE_LIMIT = "external_api_rate_limit"

    # File Processing Errors
    FILE_UPLOAD_ERROR = "file_upload_error"
    FILE_TOO_LARGE = "file_too_large"
    FILE_INVALID_FORMAT = "file_invalid_format"
    FILE_PROCESSING_ERROR = "file_processing_error"


# ==============================================
# PARAMETER KEYS
# ==============================================


class Params:
    """Standard parameter keys for analytics events"""

    # User
    USER_ID = "user_id"
    EMAIL = "email"

    # Request Info
    ENDPOINT = "api_endpoint"
    METHOD = "api_method"
    STATUS_CODE = "api_status_code"
    DURATION_MS = "duration_ms"
    SUCCESS = "success"

    # Error Info
    ERROR_CODE = "error_code"
    ERROR_MESSAGE = "error_message"
    ERROR_TYPE = "error_type"
    STACK_TRACE = "stack_trace"

    # Document Info
    DOCUMENT_ID = "document_id"
    DOCUMENT_TYPE = "document_type"
    FILE_SIZE = "file_size"
    FILE_TYPE = "file_type"

    # Content Info
    COURSE_ID = "course_id"
    MATERIAL_ID = "material_id"
    QUESTION_COUNT = "question_count"

    # Performance Metrics
    RESPONSE_TIME_MS = "response_time_ms"
    PROCESSING_TIME_MS = "processing_time_ms"
    QUEUE_TIME_MS = "queue_time_ms"

    # Pagination
    PAGE = "page"
    PAGE_SIZE = "page_size"
    TOTAL_ITEMS = "total_items"

    # AI/Gemini
    GEMINI_MODEL = "gemini_model"
    GEMINI_TOKENS = "gemini_tokens"
    GEMINI_COST = "gemini_cost"

    # Cache
    CACHE_KEY = "cache_key"
    CACHE_HIT = "cache_hit"

    # Platform
    PLATFORM = "platform"  # 'android', 'ios', 'web', 'backend'
    APP_VERSION = "app_version"

    # Session
    SESSION_ID = "session_id"
    REQUEST_ID = "request_id"

    # Metadata
    TIMESTAMP = "timestamp"
    ENVIRONMENT = "environment"  # 'development', 'staging', 'production'


# ==============================================
# HELPER FUNCTIONS
# ==============================================


def get_all_api_events():
    """Get all API event names"""
    return [value for key, value in vars(APIEvents).items() if not key.startswith("_")]


def get_all_backend_events():
    """Get all backend event names"""
    return [
        value for key, value in vars(BackendEvents).items() if not key.startswith("_")
    ]


def get_all_error_events():
    """Get all error event names"""
    return [
        value for key, value in vars(ErrorEvents).items() if not key.startswith("_")
    ]


def get_all_events():
    """Get all event names"""
    return get_all_api_events() + get_all_backend_events() + get_all_error_events()


# ==============================================
# EVENT CATEGORIES
# ==============================================

EVENT_CATEGORIES = {
    "api": APIEvents,
    "backend": BackendEvents,
    "error": ErrorEvents,
}
