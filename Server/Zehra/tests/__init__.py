"""
Tests package initialization
Test suite for the Whizkids server application
"""

# Test configuration and utilities
import os
import sys

# Add the parent directory to the path so we can import the app
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Test categories
__test_categories__ = [
    "unit",  # Unit tests for individual functions/methods
    "integration",  # Integration tests for API endpoints
    "models",  # Model validation and database tests
    "services",  # Service layer tests
    "auth",  # Authentication and authorization tests
    "api",  # API endpoint tests
    "utils",  # Utility function tests
]

# Test configuration
TEST_CONFIG = {
    "TESTING": True,
    "FLASK_ENV": "testing",
    "SECRET_KEY": "test-secret-key",
    "JWT_SECRET_KEY": "test-jwt-secret-key",
    "DISABLE_RATE_LIMITING": "true",
    "LOG_LEVEL": "ERROR",  # Reduce logging noise during tests
}

__all__ = ["__test_categories__", "TEST_CONFIG"]
