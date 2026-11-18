"""
Constants module for Wizly backend

This module provides centralized constants for the application.
"""

from .analytics_events import (
    EVENT_CATEGORIES,
    APIEvents,
    BackendEvents,
    ErrorEvents,
    Params,
    get_all_api_events,
    get_all_backend_events,
    get_all_error_events,
    get_all_events,
)

__all__ = [
    "APIEvents",
    "BackendEvents",
    "ErrorEvents",
    "Params",
    "get_all_events",
    "get_all_api_events",
    "get_all_backend_events",
    "get_all_error_events",
    "EVENT_CATEGORIES",
]
