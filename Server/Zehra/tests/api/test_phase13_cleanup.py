"""
Phase 13 tests: the K-12 code is gone. Removed routes, scans owned by user_id (with the
legacy learner-record read), bootstrap, push and usage payloads without child fields,
admin AI workflow views keyed by task and course, and the one-off migration scripts.
Firestore is faked.
"""

import copy
import re
from datetime import datetime, timedelta
from types import SimpleNamespace

import jwt
import pytest

from app.api.auth import get_bootstrap_data
from app.models.user import User
from app.services import fcm_service as fcm_module
from app.services import firebase_service, usage_limit_service
from app.utils.decorators import clear_rate_limits
from scripts.migration import archive_k12_collections as archive_script
from scripts.migration import migrate_documents_to_user_id as documents_script
from tests.fakes import FakeFirestore

NOW = datetime.utcnow()


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    monkeypatch.setattr(usage_limit_service, "db", fake)
    clear_rate_limits()
    return fake


def make_user(db, email="student@uwindsor.ca", **kwargs):
    user = User(email=email, auth_provider="email", verified=True, **kwargs)
    user.save(db)
    return user


def make_admin(db):
    admin = make_user(db, email="admin@wizly.ai")
    admin.grant_admin_access(["admin:all"])
    db.collection("users").document(admin.id).update({"is_admin": True, "admin_permissions": admin.admin_permissions})
    return admin


def auth_header(app, user_id):
    token = jwt.encode(
        {"user_id": user_id, "exp": datetime.utcnow() + timedelta(hours=1)},
        app.config["JWT_SECRET_KEY"],
        algorithm="HS256",
    )
    return {"Authorization": f"Bearer {token}"}


def add_document(db, doc_id, minutes_ago=0, **fields):
    created = NOW - timedelta(minutes=minutes_ago)
    data = {"title": doc_id, "type": "HOMEWORK", "status": "COMPLETED", "is_deleted": False, "created_at": created, "updated_at": created}
    data.update(fields)
    db.collection("documents").document(doc_id).set(data)


# --- removed K-12 routes ----------------------------------------------------------------------------------------

REMOVED_ROUTES = [
    ("get", "/api/children"),
    ("post", "/api/children"),
    ("delete", "/api/children/c1"),
    ("get", "/api/user_profile"),
    ("get", "/api/parents/profile"),
    ("get", "/api/homework/photos"),
    ("post", "/api/homework/correct/p1"),
    ("post", "/api/worksheets/generate"),
    ("get", "/api/study-collections"),
    ("get", "/api/corrections"),
    ("post", "/api/calendar/scan"),
    ("get", "/api/progress/child_1"),
    ("get", "/api/grades"),
    ("get", "/api/hierarchy"),
    ("get", "/api/questions"),
    ("get", "/api/admin/curriculum"),
    ("get", "/api/admin/users/u1/children"),
    ("post", "/api/documents/d1/correct"),
]


class TestRemovedRoutes:
    @pytest.mark.parametrize("method,path", REMOVED_ROUTES)
    def test_k12_routes_are_gone(self, client, db, method, path):
        assert getattr(client, method)(path).status_code == 404

    def test_route_map_has_no_k12_routes(self, app):
        k12 = re.compile(r"child|parent|homework|worksheet|curriculum|textbook|question|correction|study-collection|/grades(?!-tracker)")
        assert [rule.rule for rule in app.url_map.iter_rules() if k12.search(rule.rule)] == []

    def test_no_child_helpers_exported(self):
        import app.utils as utils

        assert not {"validate_child_data", "calculate_child_age", "get_age_appropriate_content_level"} & set(utils.__all__)


# --- documents (scans) ------------------------------------------------------------------------------------------


class TestDocuments:
    def test_requires_auth(self, client, db):
        assert client.get("/api/documents").status_code == 401
        assert client.get("/api/documents/d1").status_code == 401
        assert client.delete("/api/documents/d1").status_code == 401
        assert client.post("/api/documents/scan", json={"title": "x"}).status_code == 401

    def test_list_returns_own_documents_newest_first(self, app, client, db):
        me, other = make_user(db), make_user(db, email="other@uwindsor.ca")
        add_document(db, "older", minutes_ago=30, user_id=me.id)
        add_document(db, "newer", minutes_ago=5, user_id=me.id)
        add_document(db, "deleted", minutes_ago=1, user_id=me.id, is_deleted=True)
        add_document(db, "theirs", user_id=other.id)

        res = client.get("/api/documents", headers=auth_header(app, me.id))
        body = res.get_json()
        assert res.status_code == 200, body
        assert [d["id"] for d in body["data"]] == ["newer", "older"]
        assert all("child_id" not in d for d in body["data"])

    def test_other_students_document_is_not_found(self, app, client, db):
        me, other = make_user(db), make_user(db, email="other@uwindsor.ca")
        add_document(db, "theirs", user_id=other.id)
        headers = auth_header(app, me.id)

        assert client.get("/api/documents/theirs", headers=headers).status_code == 404
        assert client.delete("/api/documents/theirs", headers=headers).status_code == 404
        assert db.docs("documents")["theirs"]["is_deleted"] is False

    def test_legacy_scan_is_read_through_the_learner_record(self, app, client, db):
        me, other = make_user(db), make_user(db, email="other@uwindsor.ca")
        db.collection("children").document("learner1").set({"user_id": me.id, "is_self": True})
        add_document(db, "legacy", child_id="learner1")

        assert client.get("/api/documents/legacy", headers=auth_header(app, me.id)).status_code == 200
        assert client.get("/api/documents/legacy", headers=auth_header(app, other.id)).status_code == 404

    def test_scan_is_owned_by_the_signed_in_student(self, app, client, db):
        me = make_user(db)
        res = client.post(
            "/api/documents/scan",
            headers=auth_header(app, me.id),
            json={"title": "Week 3 problem set", "child_id": "someone-else", "pages": []},
        )
        body = res.get_json()
        assert res.status_code == 201, body
        stored = db.docs("documents")[body["data"]["id"]]
        assert stored["user_id"] == me.id
        assert "child_id" not in stored

    def test_upload_rejects_homework_photo_type(self, app, client, db):
        me = make_user(db)
        res = client.post("/api/uploads/signed-url", headers=auth_header(app, me.id), json={"upload_type": "homework_photo"})
        assert res.status_code == 400


# --- bootstrap, usage and push payloads --------------------------------------------------------------------------


class TestPayloads:
    def test_bootstrap_has_only_student_keys(self, db):
        user = make_user(db)
        data = get_bootstrap_data(user.id)
        assert set(data) == {"sync_timestamp", "app_settings", "subscription", "student_profile", "needs_onboarding"}

    def test_usage_summary_uses_university_features(self, db):
        user = make_user(db, account_type="referral_limited")
        summary = usage_limit_service.UsageLimitService().get_usage_summary(user.id)
        assert set(summary["features"]) == {"work_checks", "practice_sets", "flashcard_sets"}
        assert summary["features"]["work_checks"]["limit"] == 3

    def test_new_users_have_university_daily_limits(self):
        user = User(email="new@uwindsor.ca")
        assert set(user.daily_limits) == {"work_checks", "practice_sets", "flashcard_sets"}
        assert set(user.daily_usage) - {"last_reset"} == {"work_checks", "practice_sets", "flashcard_sets"}

    def test_push_data_carries_only_the_fields_given(self, monkeypatch):
        sent = {}

        def send(message):
            sent["data"] = message.data
            return SimpleNamespace(success_count=1, failure_count=0, responses=[])

        monkeypatch.setattr(fcm_module.FCMService, "_get_user_tokens", staticmethod(lambda user_id: ["token-1"]))
        monkeypatch.setattr(fcm_module.messaging, "send_each_for_multicast", send)

        fcm_module.FCMService.send_to_user(
            "u1",
            {"type": "settings_updated", "data": {"updated_fields": ["theme", "language"], "document_id": "d1", "empty": "", "none": None}},
            silent=True,
        )
        assert sent["data"] == {"type": "settings_updated", "updated_fields": "theme,language", "document_id": "d1"}


# --- admin AI workflow views -------------------------------------------------------------------------------------


class TestAdminAIWorkflows:
    def add_log(self, db, task, course_id, success=True, minutes_ago=1):
        at = datetime.utcnow() - timedelta(minutes=minutes_ago)
        db.collection("ai_workflow_logs").add(
            {
                "workflow_type": task,
                "model": "gemini-2.5-flash",
                "user_id": "s1",
                "course_id": course_id,
                "timestamp": at,
                "created_at": at,
                "response": {"success": success},
                "metrics": {"latency_ms": 100, "estimated_cost_usd": 0.001},
            }
        )

    def test_logs_filter_by_course(self, app, client, db):
        headers = auth_header(app, make_admin(db).id)
        self.add_log(db, "tutor_chat", "c1")
        self.add_log(db, "tutor_chat", "c2")

        res = client.get("/api/admin/ai-workflows/logs?course_id=c1", headers=headers)
        body = res.get_json()
        assert res.status_code == 200, body
        assert [log["course_id"] for log in body["logs"]] == ["c1"]
        assert "child_id" not in body["logs"][0]

    def test_workflow_list_is_built_from_logged_tasks(self, app, client, db):
        headers = auth_header(app, make_admin(db).id)
        self.add_log(db, "tutor_chat", "c1")
        self.add_log(db, "tutor_chat", "c1", success=False)
        self.add_log(db, "work_feedback", "c1")

        res = client.get("/api/admin/ai-workflows?days=7", headers=headers)
        body = res.get_json()
        assert res.status_code == 200, body
        workflows = {w["type"]: w for w in body["workflows"]}
        assert list(workflows) == ["tutor_chat", "work_feedback"]
        assert workflows["tutor_chat"]["total_executions"] == 2
        assert workflows["tutor_chat"]["success_rate"] == 50.0

    def test_students_cannot_read_logs(self, app, client, db):
        assert client.get("/api/admin/ai-workflows/logs", headers=auth_header(app, make_user(db).id)).status_code == 403


# --- migration scripts -------------------------------------------------------------------------------------------


class TestDocumentsMigration:
    def seed(self, db):
        db.collection("children").document("L1").set({"user_id": "A", "is_self": True})
        db.collection("children").document("L2").set({"parent_id": "P"})  # oldest learner records
        add_document(db, "new_style", user_id="A")
        add_document(db, "legacy", child_id="L1")
        add_document(db, "both", user_id="A", child_id="L1")
        add_document(db, "parent_owned", child_id="L2")
        add_document(db, "orphan", child_id="missing")

    def test_dry_run_writes_nothing(self, db, monkeypatch, capsys):
        self.seed(db)
        before = copy.deepcopy(db.store)
        monkeypatch.setattr(documents_script, "connect", lambda: db)

        assert documents_script.main(["--dry-run"]) == 0
        assert db.store == before
        out = capsys.readouterr().out
        assert "would update documents/legacy" in out and "skip documents/orphan" in out

    def test_moves_scans_to_user_id_and_is_idempotent(self, db, monkeypatch):
        self.seed(db)
        monkeypatch.setattr(documents_script, "connect", lambda: db)
        assert documents_script.main([]) == 0

        docs = db.docs("documents")
        assert docs["legacy"]["user_id"] == "A" and docs["legacy"]["learner_id"] == "L1"
        assert docs["both"]["user_id"] == "A" and docs["both"]["learner_id"] == "L1"
        assert docs["parent_owned"]["user_id"] == "P"
        assert all("child_id" not in docs[d] for d in ("legacy", "both", "parent_owned"))
        assert docs["orphan"]["child_id"] == "missing" and "user_id" not in docs["orphan"]
        assert "learner_id" not in docs["new_style"]

        changes, skipped = documents_script.plan(db)
        assert changes == [] and skipped == ["orphan"]


class TestArchiveK12Collections:
    def seed(self, db):
        db.collection("children").document("L1").set({"user_id": "A", "name": "Self"})
        db.collection("questions").document("q1").set({"text": "2 + 2?"})
        db.collection("questions").document("q1").collection("options").document("o1").set({"text": "4"})
        db.collection("courses").document("c1").set({"user_id": "A", "name": "Algorithms"})

    def test_dry_run_writes_nothing(self, db, monkeypatch):
        self.seed(db)
        before = copy.deepcopy(db.store)
        monkeypatch.setattr(archive_script, "connect", lambda: db)
        assert archive_script.main(["--dry-run"]) == 0
        assert db.store == before

    def test_copies_legacy_collections_and_keeps_originals(self, db):
        self.seed(db)
        report = archive_script.archive(db)

        assert report == {"children": (1, 0), "questions": (2, 0)}
        archived = db.docs("archive_k12_children")["L1"]
        assert archived["name"] == "Self" and archived["_source"] == "children/L1" and archived["_archived_at"]
        assert db.docs("archive_k12_questions/q1/options")["o1"]["text"] == "4"
        assert db.docs("children")["L1"] == {"user_id": "A", "name": "Self"}
        assert db.docs("questions/q1/options")["o1"] == {"text": "4"}
        assert "archive_k12_courses" not in db.store

        assert archive_script.archive(db) == {"children": (0, 1), "questions": (0, 2)}
