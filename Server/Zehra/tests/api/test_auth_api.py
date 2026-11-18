"""
Tests for Authentication API endpoints (passwordless email codes and provider sign-in).
Firestore is faked; provider tokens never reach Firebase.
"""

import pytest

from app.services import auth_service as auth_service_module
from app.services import firebase_service
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore


class CapturingEmailService:
    sent = {}

    def __init__(self, *args, **kwargs):
        pass

    def send_verification_email(self, email, code, *args, **kwargs):
        CapturingEmailService.sent[email] = code
        return True


def reject_token(token):
    # Like firebase_service.verify_firebase_token for a token Firebase rejects
    return None


@pytest.fixture(autouse=True)
def fake_backend(monkeypatch):
    monkeypatch.setattr(firebase_service, "db", FakeFirestore())
    monkeypatch.setattr(auth_service_module, "EmailService", CapturingEmailService)
    monkeypatch.setattr(auth_service_module, "MockEmailService", CapturingEmailService)
    monkeypatch.setattr(auth_service_module, "verify_firebase_token", reject_token)
    CapturingEmailService.sent = {}
    clear_rate_limits()


@pytest.mark.unit
@pytest.mark.api
class TestAuthAPI:
    """Test authentication endpoints"""

    def test_health_check(self, client):
        """Test health check endpoint"""
        response = client.get("/health")  # Health endpoint is at root level, not /api
        assert response.status_code == 200

    def test_login_missing_credentials(self, client):
        """Test login with missing credentials"""
        response = client.post("/api/auth/login", json={})
        assert response.status_code in [400, 422]  # Bad request or validation error

    def test_login_invalid_email(self, client):
        """Test login with invalid email format"""
        response = client.post(
            "/api/auth/login",
            json={"email": "invalid-email", "password": "password123"},
        )
        assert response.status_code in [400, 401, 422]

    def test_login_with_valid_credentials(self, client):
        """
        Test login with valid credentials

        NOTE: This test requires a test user to exist in Firestore
        or Firebase Authentication to be mocked.
        Adjust based on your test setup.
        """
        pytest.skip("Requires Firebase test user setup")

        response = client.post(
            "/api/auth/login",
            json={"email": "test@example.com", "password": "password123"},
        )

        if response.status_code == 200:
            data = response.json
            assert "token" in data
            assert "user" in data
            assert data["user"]["email"] == "test@example.com"

    def test_provider_auth_missing_token(self, client):
        """Test provider authentication with missing token"""
        response = client.post("/api/auth/provider", json={"provider": "google"})
        assert response.status_code in [400, 422]

    def test_provider_auth_invalid_provider(self, client):
        """Test provider authentication with invalid provider"""
        response = client.post(
            "/api/auth/provider",
            json={"firebase_token": "fake_token", "provider": "invalid_provider"},
        )
        assert response.status_code in [400, 422]


@pytest.mark.unit
@pytest.mark.api
class TestAuthValidation:
    """Test authentication input validation"""

    def test_login_empty_email(self, client):
        """Test login with empty email"""
        response = client.post(
            "/api/auth/login", json={"email": "", "password": "password123"}
        )
        assert response.status_code in [400, 422]

    def test_login_empty_password(self, client):
        """Test login with empty password"""
        response = client.post(
            "/api/auth/login", json={"email": "test@example.com", "password": ""}
        )
        assert response.status_code in [400, 422]

    def test_provider_auth_empty_token(self, client):
        """Test provider auth with empty firebase token"""
        response = client.post(
            "/api/auth/provider", json={"firebase_token": "", "provider": "google"}
        )
        assert response.status_code in [400, 422]

    def test_signup_without_email(self, client):
        """Signup needs an email address"""
        response = client.post("/api/auth/signup", json={"name": "Test Student"})
        assert response.status_code in [400, 422]

    def test_signup_with_email_only_sends_a_code(self, client):
        """Students sign up with just an email; no child or profile fields are required"""
        response = client.post("/api/auth/signup", json={"email": "new.student@uwindsor.ca"})
        assert response.status_code == 200, response.get_json()
        assert "new.student@uwindsor.ca" in CapturingEmailService.sent
