"""
Phase 0 regression tests: current-user ID helper and JWT on the scan and work-check
endpoints (the homework endpoints they replaced were removed in Phase 13).
"""

from unittest.mock import MagicMock

import pytest

from app.services import firebase_service
from app.utils.decorators import get_current_user_id


class TestGetCurrentUserId:
    def test_reads_id_from_jwt_payload(self):
        assert get_current_user_id({"id": "u1", "email": "a@b.c"}) == "u1"

    def test_falls_back_to_user_id(self):
        assert get_current_user_id({"user_id": "u2"}) == "u2"

    def test_prefers_id_over_user_id(self):
        assert get_current_user_id({"id": "u1", "user_id": "u2"}) == "u1"

    @pytest.mark.parametrize("current_user", [None, {}])
    def test_missing_user_returns_none(self, current_user):
        assert get_current_user_id(current_user) is None


STUDENT_ENDPOINTS = [
    ("get", "/api/documents"),
    ("post", "/api/documents/scan"),
    ("get", "/api/documents/d1"),
    ("delete", "/api/documents/d1"),
    ("post", "/api/uploads/signed-url"),
    ("get", "/api/work/submissions"),
    ("post", "/api/work/submissions"),
    ("get", "/api/work/submissions/s1"),
    ("get", "/api/work/submissions/s1/stream"),
]


class TestStudentEndpointsRequireAuth:
    @pytest.fixture(autouse=True)
    def fake_firestore(self, monkeypatch):
        # Resource constructors open Firestore before the auth decorator runs.
        monkeypatch.setattr(firebase_service, "db", MagicMock())

    @pytest.mark.parametrize("method,path", STUDENT_ENDPOINTS)
    def test_rejects_missing_token(self, client, method, path):
        response = getattr(client, method)(path)
        assert response.status_code == 401

    @pytest.mark.parametrize("method,path", STUDENT_ENDPOINTS)
    def test_rejects_invalid_token(self, client, method, path):
        response = getattr(client, method)(
            path, headers={"Authorization": "Bearer not-a-real-token"}
        )
        assert response.status_code == 401
