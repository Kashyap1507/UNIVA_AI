"""
Phase 1 tests: student auth without child data (no learner record since Phase 13),
student profile API and password-protected admin login. Firestore is faked.
"""

from datetime import datetime, timedelta

import jwt
import pytest

from app.models.user import User
from app.services import auth_service as auth_service_module
from app.services import firebase_service
from app.services import student_service
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore


class CapturingEmailService:
    sent = {}

    def __init__(self, *args, **kwargs):
        pass

    def send_verification_email(self, email, code, *args, **kwargs):
        CapturingEmailService.sent[email] = code
        return True


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    monkeypatch.setattr(auth_service_module, "EmailService", CapturingEmailService)
    monkeypatch.setattr(auth_service_module, "MockEmailService", CapturingEmailService)
    CapturingEmailService.sent = {}
    clear_rate_limits()
    return fake


def make_user(db, **kwargs):
    user = User(email=kwargs.pop("email", "student@uni.edu"), auth_provider="email", verified=True, **kwargs)
    user.save(db)
    return user


def auth_header(app, user_id):
    token = jwt.encode(
        {"user_id": user_id, "exp": datetime.utcnow() + timedelta(hours=1)},
        app.config["JWT_SECRET_KEY"],
        algorithm="HS256",
    )
    return {"Authorization": f"Bearer {token}"}


def assert_no_learner_records(db):
    assert db.docs("children") == {}


# --- profile validation -----------------------------------------------------


class TestProfileValidation:
    def test_accepts_full_profile(self):
        ok, _, cleaned = student_service.validate_student_profile(
            {"university": " Uni of Windsor ", "degree_level": "master", "program": "MAC", "current_semester": 2}
        )
        assert ok
        assert cleaned == {
            "university": "Uni of Windsor",
            "degree_level": "MASTER",
            "program": "MAC",
            "current_semester": 2,
        }

    @pytest.mark.parametrize(
        "data",
        [
            {"degree_level": "PHD"},
            {"current_semester": 0},
            {"current_semester": "2"},
            {"current_semester": True},
            {"university": 5},
            {"program": "x" * 121},
            {"enrolled_course_ids": "c1"},
        ],
    )
    def test_rejects_invalid(self, data):
        assert not student_service.validate_student_profile(data)[0]

    def test_onboarding_completes_with_required_fields(self, db):
        user = make_user(db)
        student_service.update_student_profile(db, user, {"university": "Windsor"})
        assert user.needs_onboarding()
        student_service.update_student_profile(db, user, {"degree_level": "BACHELOR", "program": "CS"})
        assert not user.needs_onboarding()
        assert db.docs("users")[user.id]["onboarding_completed"] is True


# --- auth without child data --------------------------------------------------


class TestAuthWithoutChildData:
    def test_email_signup_and_verify(self, client, db):
        email = "new.student@gmail.com"
        res = client.post("/api/auth/signup", json={"email": email})
        assert res.status_code == 200, res.get_json()

        code = CapturingEmailService.sent[email]
        res = client.post("/api/auth/verify-email", json={"email": email, "code": code, "action": "signup"})
        body = res.get_json()
        assert res.status_code == 200, body
        assert body["needs_onboarding"] is True
        assert "learner_id" not in body
        assert body["student_profile"]["university"] is None

        assert "learner_id" not in db.docs("users")[body["user"]["id"]]
        assert_no_learner_records(db)

    def test_email_signup_ignores_legacy_child_fields(self, client, db):
        res = client.post(
            "/api/auth/signup",
            json={"email": "legacy@gmail.com", "child_name": "X", "child_birth_month": 13, "child_birth_year": 1900},
        )
        assert res.status_code == 200, res.get_json()

    def test_provider_auth_creates_student(self, client, db, monkeypatch):
        monkeypatch.setattr(
            auth_service_module,
            "verify_firebase_token",
            lambda token: {
                "email": "google.student@gmail.com",
                "name": "Google Student",
                "uid": "g-uid-1",
                "firebase": {"sign_in_provider": "google.com"},
            },
        )
        res = client.post("/api/auth/provider", json={"firebase_token": "tok"})
        body = res.get_json()
        assert res.status_code == 200, body
        assert body["is_new_user"] is True
        assert body["needs_onboarding"] is True
        assert_no_learner_records(db)

        # Second sign-in logs in to the same account
        res = client.post("/api/auth/provider", json={"firebase_token": "tok"})
        again = res.get_json()
        assert again["is_new_user"] is False and again["user"]["id"] == body["user"]["id"]
        assert_no_learner_records(db)


# --- student profile API --------------------------------------------------------


class TestStudentProfileAPI:
    def test_requires_auth(self, client, db):
        assert client.get("/api/student/profile").status_code == 401

    def test_get_and_put(self, app, client, db):
        user = make_user(db, name="Asha")
        headers = auth_header(app, user.id)

        res = client.get("/api/student/profile", headers=headers)
        body = res.get_json()
        assert res.status_code == 200, body
        assert body["data"]["needs_onboarding"] is True
        assert "learner_id" not in body["data"]

        res = client.put(
            "/api/student/profile",
            headers=headers,
            json={"university": "University of Windsor", "degree_level": "MASTER", "program": "MAC", "current_semester": 2},
        )
        body = res.get_json()
        assert res.status_code == 200, body
        assert body["data"]["needs_onboarding"] is False
        assert body["data"]["student_profile"]["program"] == "MAC"
        assert db.docs("users")[user.id]["student_profile"]["degree_level"] == "MASTER"

    def test_put_rejects_invalid(self, app, client, db):
        user = make_user(db)
        res = client.put("/api/student/profile", headers=auth_header(app, user.id), json={"degree_level": "PHD"})
        assert res.status_code == 400

    def test_put_updates_name(self, app, client, db):
        user = make_user(db)
        res = client.put("/api/student/profile", headers=auth_header(app, user.id), json={"name": "  Ravi  "})
        assert res.status_code == 200
        assert res.get_json()["data"]["name"] == "Ravi"


# --- admin login -----------------------------------------------------------------


class TestAdminLogin:
    def make_admin(self, db, password=None):
        user = make_user(db, email="admin@uni.edu")
        user.grant_admin_access(["admin:all"])
        if password:
            user.set_admin_password(password)
        db.collection("users").document(user.id).update(
            {
                "is_admin": True,
                "admin_permissions": user.admin_permissions,
                "admin_password_hash": user.admin_password_hash,
            }
        )
        return user

    def test_correct_password_logs_in(self, client, db):
        self.make_admin(db, "correct horse battery")
        res = client.post("/api/admin/auth/login", json={"email": "admin@uni.edu", "password": "correct horse battery"})
        body = res.get_json()
        assert res.status_code == 200, body
        assert body["token"]
        assert "admin_password_hash" not in str(body)

    def test_wrong_password_rejected(self, client, db):
        self.make_admin(db, "correct horse battery")
        res = client.post("/api/admin/auth/login", json={"email": "admin@uni.edu", "password": "wrong"})
        assert res.status_code == 401

    def test_admin_without_password_rejected(self, client, db):
        self.make_admin(db)
        res = client.post("/api/admin/auth/login", json={"email": "admin@uni.edu", "password": "anything"})
        assert res.status_code == 401

    def test_missing_password_rejected(self, client, db):
        self.make_admin(db, "correct horse battery")
        res = client.post("/api/admin/auth/login", json={"email": "admin@uni.edu"})
        assert res.status_code == 400
