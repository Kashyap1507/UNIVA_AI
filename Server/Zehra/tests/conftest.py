"""
Pytest configuration and fixtures for Wizly backend tests
"""

import os

import pytest
from dotenv import load_dotenv

# Load test environment variables
load_dotenv(".env.test")

# Set environment to testing before importing app
os.environ["FLASK_ENV"] = "testing"
os.environ["TESTING"] = "true"
# Note: SKIP_FIREBASE_INIT removed - Firebase now provisioned in CI

from app import create_app


@pytest.fixture(scope="session")
def app():
    """
    Create and configure a Flask app for testing
    """
    app = create_app()
    app.config["TESTING"] = True
    app.config["DEBUG"] = False
    app.config["WTF_CSRF_ENABLED"] = False

    # Configure test-specific settings
    app.config["JWT_SECRET_KEY"] = "test-jwt-secret"
    app.config["SECRET_KEY"] = "test-secret-key"

    yield app


@pytest.fixture(scope="function")
def client(app):
    """
    Create a test client for the Flask app
    """
    return app.test_client()


@pytest.fixture(scope="function")
def runner(app):
    """
    Create a CLI runner for the Flask app
    """
    return app.test_cli_runner()


@pytest.fixture(scope="function")
def auth_headers(client):
    """
    Create authentication headers with a valid JWT token

    NOTE: This fixture assumes you have a test user in your database
    or you mock the authentication. Adjust based on your auth setup.
    """
    # Option 1: Login with test credentials (requires test user)
    # response = client.post('/api/auth/login', json={
    #     'email': 'test@example.com',
    #     'password': 'password123'
    # })
    #
    # if response.status_code == 200:
    #     token = response.json.get('token')
    #     return {'Authorization': f'Bearer {token}'}

    # Option 2: Generate test JWT token directly
    from datetime import datetime, timedelta

    import jwt

    payload = {
        "user_id": "test_user_123",
        "email": "test@example.com",
        "exp": datetime.utcnow() + timedelta(hours=1),
    }

    token = jwt.encode(payload, "test-jwt-secret", algorithm="HS256")

    return {"Authorization": f"Bearer {token}"}


# Pytest hooks and configuration


def pytest_configure(config):
    """
    Custom pytest configuration
    """
    # Register custom markers
    config.addinivalue_line(
        "markers", "unit: Unit tests that don't require external dependencies"
    )
    config.addinivalue_line(
        "markers", "integration: Integration tests that may require database/services"
    )
    config.addinivalue_line("markers", "slow: Tests that take a long time to run")


def pytest_collection_modifyitems(config, items):
    """
    Modify test items during collection
    """
    # Auto-mark tests based on location
    for item in items:
        if "integration" in item.nodeid:
            item.add_marker(pytest.mark.integration)
        elif "unit" in item.nodeid or item.nodeid.startswith("tests/api"):
            item.add_marker(pytest.mark.unit)
