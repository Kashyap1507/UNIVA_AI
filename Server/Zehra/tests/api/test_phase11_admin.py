"""
Phase 11 tests: token metering, daily AI usage and the university admin API.
"""

import os
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

import jwt
import pytest

from app.services import admin_university_service, ai_usage_service, ai_workflow_logger, firebase_service
from app.services.ai_workflow_logger import with_usage
from app.utils import usage_meter
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore

SECRET = "phase11-test-secret"
NOW = datetime(2026, 9, 28, 15, 0, tzinfo=timezone.utc)


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    monkeypatch.setenv("JWT_SECRET_KEY", SECRET)
    monkeypatch.setattr(admin_university_service, "_now", lambda: NOW)
    clear_rate_limits()
    usage_meter.reset()
    return fake


def token(uid, secret=SECRET):
    return {"Authorization": "Bearer " + jwt.encode({"user_id": uid, "exp": datetime.utcnow() + timedelta(hours=1)}, secret, algorithm="HS256")}


def student(db, uid, name, university="University of Windsor", degree="BACHELOR", program="BSc Computer Science", created=NOW - timedelta(days=2)):
    db.collection("users").document(uid).set(
        {
            "email": f"{uid}@uwindsor.ca",
            "name": name,
            "is_active": True,
            "created_at": created,
            "student_profile": {"university": university, "degree_level": degree, "program": program},
        }
    )


@pytest.fixture
def admin(db):
    db.collection("users").document("admin1").set({"email": "admin@wizly.ai", "name": "Admin", "is_admin": True, "is_active": True})
    return token("admin1")


# --- metering ------------------------------------------------------------------------------------------------------


class TestUsage:
    def test_cost_uses_per_model_prices(self):
        assert usage_meter.cost_usd("gemini-2.5-flash", 1_000_000, 1_000_000) == pytest.approx(2.80)
        assert usage_meter.cost_usd("models/gemini-2.5-pro", 1_000_000, 100_000) == pytest.approx(2.25)
        assert usage_meter.cost_usd("unknown-model", 1_000_000, 0) == pytest.approx(0.30)  # flash default

    def test_meter_adds_responses_and_take_resets(self, db):
        usage_meter.add_response("gemini-2.5-flash", SimpleNamespace(usage_metadata=SimpleNamespace(prompt_token_count=1000, candidates_token_count=200, thoughts_token_count=300)))
        usage_meter.add_response("gemini-2.5-pro", SimpleNamespace(usage_metadata=SimpleNamespace(prompt_token_count=2000, candidates_token_count=100)))
        usage_meter.add_response("gemini-2.5-flash", SimpleNamespace())  # no metadata: ignored
        taken = usage_meter.take()
        assert taken["calls"] == 2 and taken["tokens_in"] == 3000 and taken["tokens_out"] == 600
        assert taken["cost_usd"] == pytest.approx(1000 * 0.30e-6 + 500 * 2.50e-6 + 2000 * 1.25e-6 + 100 * 10e-6)
        assert usage_meter.take() is None

    def test_metrics_prefer_real_usage_then_estimates(self):
        real = with_usage("gemini-2.5-flash", {"latency_ms": 5}, {"calls": 2, "tokens_in": 100, "tokens_out": 50, "cost_usd": 0.001})
        assert real["usage_source"] == "api" and real["input_tokens"] == 100 and real["estimated_cost_usd"] == 0.001 and real["latency_ms"] == 5
        estimate = with_usage("gemini-2.5-flash", {"estimated_input_tokens": 1_000_000, "estimated_output_tokens": 0}, None)
        assert estimate["usage_source"] == "estimate" and estimate["cost_usd"] == pytest.approx(0.30)
        assert with_usage("gemini-2.5-flash", None, None)["usage_source"] == "none"

    def test_daily_rollup_increments_by_task(self, db):
        when = datetime(2026, 9, 27, 23, 30, tzinfo=timezone.utc)
        ai_usage_service.record("u1", "tutor_chat", 1000, 200, 0.0008, when)
        ai_usage_service.record("u1", "tutor_chat", 500, 100, 0.0004, when)
        ai_usage_service.record("u1", "flashcard_generation", 3000, 900, 0.003, when)
        ai_usage_service.record(None, "tutor_chat", 1, 1, 1.0, when)  # no user: skipped
        day = db.docs("ai_usage_daily")["u1_20260927"]
        assert day["date"] == "2026-09-27" and day["calls"] == 3 and day["tokens_in"] == 4500
        assert day["cost_usd"] == pytest.approx(0.0042)
        assert day["by_task"]["tutor_chat"]["calls"] == 2 and day["by_task"]["flashcard_generation"]["tokens_out"] == 900
        assert len(db.docs("ai_usage_daily")) == 1

    def test_logger_writes_metered_usage(self, db, monkeypatch):
        class InlineThread:
            def __init__(self, target, args):
                self.target, self.args = target, args
                self.daemon = False

            def start(self):
                self.target(*self.args)

        monkeypatch.setattr(ai_workflow_logger, "Thread", InlineThread)
        logger = ai_workflow_logger.AIWorkflowLogger()
        logger.enabled = True
        usage_meter.add("gemini-2.5-flash", 2000, 400)
        logger.log_workflow("tutor_chat", "gemini-2.5-flash", {}, {}, {"success": True}, {"latency_ms": 10}, user_id="u9")
        log = next(iter(db.docs("ai_workflow_logs").values()))
        assert log["metrics"]["usage_source"] == "api" and log["metrics"]["input_tokens"] == 2000
        day = next(iter(db.docs("ai_usage_daily").values()))
        assert day["user_id"] == "u9" and day["by_task"]["tutor_chat"]["tokens_out"] == 400
        assert usage_meter.take() is None  # taken by the log, not left for the next run


# --- admin API -----------------------------------------------------------------------------------------------------


def seed_activity(db):
    student(db, "s1", "Priya Shah", program="BSc Computer Science")
    student(db, "s2", "Alex Morgan", university=" university of windsor ", degree="MASTER", program="MSc Applied Computing", created=NOW - timedelta(days=40))
    student(db, "s3", "Sam Lee", university="McGill University", program="BSc Computer Science")
    db.collection("courses").document("c1").set({"user_id": "s1", "name": "Operating Systems", "code": "comp-3300", "status": "ACTIVE", "created_at": NOW})
    db.collection("courses").document("c2").set({"user_id": "s2", "name": "Operating Systems", "code": "COMP 3300", "status": "ACTIVE", "created_at": NOW})
    db.collection("courses").document("c3").set({"user_id": "s2", "name": "Machine Learning", "code": "COMP-8740", "created_at": NOW})
    db.collection("courses").document("c4").set({"user_id": "s3", "name": "Old", "code": "X", "is_deleted": True})
    db.collection("course_materials").document("m1").set({"user_id": "s1", "course_id": "c1", "ingestion_status": "READY", "title": "Private lecture notes"})
    db.collection("course_materials").document("m2").set({"user_id": "s2", "course_id": "c3", "ingestion_status": "FAILED"})
    db.collection("study_days").document("s1_20260928").set({"user_id": "s1", "date": "2026-09-28"})
    db.collection("study_days").document("s1_20260925").set({"user_id": "s1", "date": "2026-09-25"})
    db.collection("study_days").document("s2_20260801").set({"user_id": "s2", "date": "2026-08-01"})
    db.collection("flashcard_reviews").document("r1").set({"user_id": "s1", "reviewed_at": NOW - timedelta(hours=2)})
    db.collection("flashcard_reviews").document("r2").set({"user_id": "s1", "reviewed_at": NOW - timedelta(days=20)})
    ai_usage_service.record("s1", "tutor_chat", 1000, 100, 0.01, NOW - timedelta(hours=1), db=db)
    ai_usage_service.record("s2", "work_feedback", 5000, 900, 0.05, NOW - timedelta(days=3), db=db)
    ai_usage_service.record("s2", "paper_analysis", 5000, 900, 0.02, NOW - timedelta(days=45), db=db)


class TestAdminAccess:
    @pytest.mark.parametrize("url", ["/api/admin/overview", "/api/admin/students", "/api/admin/catalog", "/api/admin/moderation", "/api/admin/ai-costs", "/api/admin/students/s1"])
    def test_students_and_anonymous_are_refused(self, client, db, url):
        student(db, "s1", "Priya Shah")
        assert client.get(url).status_code == 401
        assert client.get(url, headers=token("s1")).status_code == 401
        assert client.post("/api/admin/moderation/r1/resolve", headers=token("s1"), json={"action": "DISMISS"}).status_code == 401

    def test_bad_range_is_rejected(self, client, admin):
        res = client.get("/api/admin/overview?days=12", headers=admin)
        assert res.status_code == 400 and "days" in res.get_json()["error"]


class TestAdminViews:
    def test_overview_counts_real_activity(self, client, db, admin):
        seed_activity(db)
        d = client.get("/api/admin/overview?days=7", headers=admin).get_json()["data"]
        assert d["students"] == {"total": 3, "new": 2, "active": 1, "bachelor": 2, "master": 1, "profile_incomplete": 0}
        assert d["content"]["courses"] == 3 and d["content"]["materials"] == 2 and d["content"]["materials_failed"] == 1
        assert d["content"]["flashcard_reviews"] == 1
        assert d["ai"]["cost_usd"] == pytest.approx(0.06) and d["ai"]["calls"] == 2
        assert len(d["daily"]) == 7 and d["daily"][-1] == {"date": "2026-09-28", "active_students": 1, "flashcard_reviews": 1, "ai_cost_usd": 0.01}
        assert d["top_universities"][0] == {"name": "University of Windsor", "students": 2}

    def test_students_list_filters_and_never_leaks_content(self, client, db, admin):
        seed_activity(db)
        d = client.get("/api/admin/students", headers=admin).get_json()["data"]
        assert d["total"] == 3 and "admin1" not in [s["id"] for s in d["students"]]
        s2 = next(s for s in d["students"] if s["id"] == "s2")
        assert s2["course_count"] == 2 and s2["last_active"] == "2026-08-01" and s2["ai_cost_30d"] == pytest.approx(0.05) and s2["degree_level"] == "MASTER"
        assert [s["id"] for s in client.get("/api/admin/students?degree=master", headers=admin).get_json()["data"]["students"]] == ["s2"]
        assert [s["id"] for s in client.get("/api/admin/students?q=mcgill", headers=admin).get_json()["data"]["students"]] == ["s3"]

        detail = client.get("/api/admin/students/s1", headers=admin).get_json()["data"]
        assert detail["courses"][0]["code"] == "comp-3300" and detail["courses"][0]["material_count"] == 1
        assert detail["counts"]["flashcard_reviews"] == 2 and detail["study_days_total"] == 2
        assert detail["ai_usage_30d"]["by_task"][0]["task"] == "tutor_chat"
        assert "Private lecture notes" not in str(detail)
        assert client.get("/api/admin/students/admin1", headers=admin).status_code == 404
        assert client.get("/api/admin/students/nope", headers=admin).status_code == 404

    def test_catalog_groups_universities_programs_and_course_codes(self, client, db, admin):
        seed_activity(db)
        unis = client.get("/api/admin/catalog", headers=admin).get_json()["data"]["universities"]
        windsor = unis[0]
        assert windsor["name"] == "University of Windsor" and windsor["students"] == 2 and windsor["bachelor"] == 1 and windsor["master"] == 1
        assert windsor["courses"][0] == {"code": "COMP 3300", "name": "Operating Systems", "students": 2}
        assert {p["name"] for p in windsor["programs"]} == {"BSc Computer Science", "MSc Applied Computing"}
        assert unis[1]["name"] == "McGill University" and unis[1]["courses"] == []

    def test_ai_costs_by_day_task_and_student(self, client, db, admin):
        seed_activity(db)
        d = client.get("/api/admin/ai-costs?days=30", headers=admin).get_json()["data"]
        assert d["totals"]["cost_usd"] == pytest.approx(0.06) and d["totals"]["students"] == 2 and d["totals"]["cost_per_student"] == pytest.approx(0.03)
        assert [t["task"] for t in d["by_task"]] == ["work_feedback", "tutor_chat"]
        assert d["top_students"][0]["name"] == "Alex Morgan" and len(d["daily"]) == 30
        assert client.get("/api/admin/ai-costs?days=90", headers=admin).get_json()["data"]["totals"]["cost_usd"] == pytest.approx(0.08)


# --- moderation ----------------------------------------------------------------------------------------------------


def seed_reports(db):
    student(db, "owner", "Priya Shah")
    student(db, "m1", "Alex Morgan")
    student(db, "m2", "Sam Lee")
    student(db, "m3", "Jo Park")
    db.collection("study_groups").document("g1").set({"name": "OS crew", "owner_id": "owner", "member_count": 4})
    for uid, role in (("owner", "OWNER"), ("m1", "MEMBER"), ("m2", "MEMBER"), ("m3", "MEMBER")):
        db.collection("group_members").document(f"g1_{uid}").set({"group_id": "g1", "user_id": uid, "role": role, "status": "ACTIVE", "display_name": uid})
    db.collection("shared_notes").document("n1").set({"group_id": "g1", "author_id": "m1", "author_name": "Alex M.", "kind": "TEXT", "body": "Buy essays here", "hidden": True, "created_at": NOW})
    for i, reporter in enumerate(("owner", "m2", "m3")):
        db.collection("content_reports").document(f"r{i}").set(
            {"reporter_id": reporter, "group_id": "g1", "target_type": "NOTE", "target_id": "n1", "reason": "SPAM" if i else "ACADEMIC_INTEGRITY", "status": "OPEN", "created_at": NOW - timedelta(minutes=10 - i)}
        )
    db.collection("content_reports").document("r9").set({"reporter_id": "owner", "group_id": "g1", "target_type": "MEMBER", "target_id": "m1", "reason": "OTHER", "status": "OPEN", "created_at": NOW})


class TestModeration:
    def test_queue_groups_reports_by_target_with_preview(self, client, db, admin):
        seed_reports(db)
        items = client.get("/api/admin/moderation", headers=admin).get_json()["data"]["items"]
        assert [i["target_type"] for i in items] == ["NOTE", "MEMBER"]
        note = items[0]
        assert note["report_count"] == 3 and note["reasons"] == {"ACADEMIC_INTEGRITY": 1, "SPAM": 2} and note["id"] == "r0"
        assert note["target"]["body"] == "Buy essays here" and note["target"]["hidden"] and note["target"]["group_name"] == "OS crew"
        assert note["details"][0]["reporter"] == "Priya S." and "@" not in str(note["details"])

    def test_remove_note_resolves_every_report_on_it(self, client, db, admin):
        seed_reports(db)
        res = client.post("/api/admin/moderation/r1/resolve", headers=admin, json={"action": "remove", "note": "Contract cheating"})
        assert res.status_code == 200 and res.get_json()["data"]["resolved_reports"] == 3
        n = db.docs("shared_notes")["n1"]
        assert n["is_deleted"] and n["removed_by_admin"]
        assert all(db.docs("content_reports")[f"r{i}"]["status"] == "RESOLVED" for i in range(3))
        assert db.docs("content_reports")["r0"]["resolution"]["by"] == "admin1"
        assert db.docs("content_reports")["r9"]["status"] == "OPEN"
        assert client.post("/api/admin/moderation/r2/resolve", headers=admin, json={"action": "DISMISS"}).status_code == 409
        resolved = client.get("/api/admin/moderation?status=resolved", headers=admin).get_json()["data"]["items"]
        assert resolved[0]["resolution"]["action"] == "REMOVE" and resolved[0]["target"]["removed"]

    def test_dismiss_unhides_note(self, client, db, admin):
        seed_reports(db)
        assert client.post("/api/admin/moderation/r0/resolve", headers=admin, json={"action": "DISMISS"}).status_code == 200
        assert db.docs("shared_notes")["n1"]["hidden"] is False and not db.docs("shared_notes")["n1"].get("is_deleted")

    def test_remove_member_and_owner_guard(self, client, db, admin):
        seed_reports(db)
        assert client.post("/api/admin/moderation/r9/resolve", headers=admin, json={"action": "REMOVE"}).status_code == 200
        assert db.docs("group_members")["g1_m1"]["status"] == "REMOVED" and db.docs("study_groups")["g1"]["member_count"] == 3
        db.collection("content_reports").document("r10").set({"reporter_id": "m2", "group_id": "g1", "target_type": "MEMBER", "target_id": "owner", "reason": "OTHER", "status": "OPEN"})
        assert client.post("/api/admin/moderation/r10/resolve", headers=admin, json={"action": "REMOVE"}).status_code == 409

    def test_remove_group_and_validation(self, client, db, admin):
        seed_reports(db)
        db.collection("content_reports").document("r11").set({"reporter_id": "m2", "group_id": "g1", "target_type": "GROUP", "target_id": "g1", "reason": "SPAM", "status": "OPEN"})
        assert client.post("/api/admin/moderation/r11/resolve", headers=admin, json={"action": "REMOVE"}).status_code == 200
        assert db.docs("study_groups")["g1"]["is_deleted"]
        assert client.post("/api/admin/moderation/r0/resolve", headers=admin, json={"action": "BAN"}).status_code == 400
        assert client.post("/api/admin/moderation/missing/resolve", headers=admin, json={"action": "DISMISS"}).status_code == 404
        assert client.get("/api/admin/moderation?status=closed", headers=admin).status_code == 400
