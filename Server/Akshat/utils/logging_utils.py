"""
Secure logging utilities for Wizly Server
Prevents sensitive information leakage in production
"""

import logging
import os
import traceback


def is_debug_mode() -> bool:
    """Check if application is in debug mode"""
    return os.environ.get("FLASK_ENV", "").lower() in ["development", "testing"]


def log_exception_securely(
    message: str, exc: Exception = None, logger: logging.Logger = None
):
    """
    Log exceptions securely - full traceback in debug, generic message in production

    Args:
        message: Custom error message to log
        exc: Exception object (optional)
        logger: Logger instance (defaults to root logger)
    """
    if logger is None:
        logger = logging.getLogger(__name__)

    if is_debug_mode():
        # Development: Log full traceback for debugging
        logger.error(f"{message}: {str(exc) if exc else 'Unknown error'}")
        if exc:
            logger.error(f"Full traceback: {traceback.format_exc()}")
    else:
        # Production: Log generic error without traceback
        # Security: Never expose stack traces in production logs
        logger.error(f"{message}: {type(exc).__name__ if exc else 'Error'}")


def format_api_error(
    error_type: str = "SERVER_ERROR", user_message: str = "An error occurred"
) -> dict:
    """
    Format API error response securely

    Args:
        error_type: Error code (e.g., "VALIDATION_ERROR", "SERVER_ERROR")
        user_message: User-friendly error message

    Returns:
        Dictionary with success=False, error type, and message
    """
    return {"success": False, "error": error_type, "message": user_message}
