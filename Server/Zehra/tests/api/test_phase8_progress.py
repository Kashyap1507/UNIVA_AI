"""
Phase 8 tests: study streaks, the Today screen, course progress and the grade tracker.
"""

from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import jwt
import pytest

from app.services import deadline_service, exam_plan_service, firebase_service, grade_tracker_service, study_activity_service
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore

TORONTO = ZoneInfo("America/Toronto")


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    clear_rate_limits()
    return fake


def headers(app, uid, tz=None):
    token = jwt.encode({"user_id": uid, "exp": datetime.utcnow() + timedelta(hours=1)}, app.config["JWT_SECRET_KEY"], algorithm="HS256")
    h = {"Authorization": f"Bearer {token}"}
    if tz:
        h["X-Timezone"] = tz
    return h


def user(db, uid="u1"):
    db.collection("users").document(uid).set({"email": f"{uid}@uni.edu", "auth_provider": "email", "is_active": True, "student_profile": {"degree_level": "BACHELOR"}})


def course(client, app, uid="u1", name="Linear Algebra", code="MATH-2250", **extra):
    return client.post("/api/courses", headers=headers(app, uid), json={"name": name, "code": code, **extra}).get_json()["data"]["id"]


# --- streaks --------------------------------------------------------------------------------------


class TestStreak:
    def mark(self, db, *days, uid="u1"):
        for d in days:
            study_activity_service.record(uid, "cards", datetime.combine(date.fromisoformat(d), datetime.min.time(), timezone.utc).replace(hour=12), timezone.utc)

    def test_current_best_and_open_today(self, db):
        self.mark(db, "2026-09-20", "2026-09-21", "2026-09-22", "2026-09-25", "2026-09-26", "2026-09-27")
        s = study_activity_service.streak("u1", date(2026, 9, 28))
        assert s["current"] == 3 and s["best"] == 3 and s["studied_today"] is False  # today still open
        self.mark(db, "2026-09-28")
        s = study_activity_service.streak("u1", date(2026, 9, 28))
        assert s["current"] == 4 and s["best"] == 4 and s["studied_today"] is True
        assert [d["active"] for d in s["last_7_days"]] == [True, False, False, True, True, True, True]  # 22nd .. 28th
        assert study_activity_service.streak("u1", date(2026, 9, 30))["current"] == 0  # missed a day

    def test_counts_accumulate_and_bad_kinds_are_ignored(self, db):
        when = datetime(2026, 9, 28, 15, tzinfo=timezone.utc)
        study_activity_service.record("u1", "cards", when, timezone.utc)
        study_activity_service.record("u1", "cards", when, timezone.utc)
        study_activity_service.record("u1", "practice", when, timezone.utc)
        study_activity_service.record("u1", "napping", when, timezone.utc)
        assert db.docs("study_days")["u1_20260928"]["counts"] == {"cards": 2, "practice": 1}

    def test_local_day_uses_timezone(self, db):
        late = datetime(2026, 9, 28, 2, 30, tzinfo=timezone.utc)  # 22:30 on the 27th in Toronto
        study_activity_service.record("u1", "tutor", late, TORONTO)
        assert "u1_20260927" in db.docs("study_days")

    def test_review_through_api_counts_on_the_answer_day(self, app, client, db):
        user(db)
        la = course(client, app)
        card = client.post(f"/api/courses/{la}/flashcards", headers=headers(app, "u1"), json={"front": "Q", "back": "A"}).get_json()["data"]
        res = client.post(
            f"/api/flashcards/{card['id']}/review",
            headers=headers(app, "u1", tz="America/Toronto"),
            json={"grade": 4, "reviewed_at": "2026-09-20T02:30:00Z", "client_review_id": "offline-12345678"},
        )
        assert res.status_code == 200
        assert "u1_20260919" in db.docs("study_days")

    def test_bad_timezone_header_falls_back_to_utc(self, app, client, db):
        user(db)
        la = course(client, app)
        card = client.post(f"/api/courses/{la}/flashcards", headers=headers(app, "u1"), json={"front": "Q", "back": "A"}).get_json()["data"]
        client.post(f"/api/flashcards/{card['id']}/review", headers=headers(app, "u1", tz="Mars/Olympus"), json={"grade": 4, "reviewed_at": "2026-09-20T02:30:00Z"})
        assert "u1_20260920" in db.docs("study_days")


# --- today ------------------------------------------------------------------------------------------


class TestToday:
    def test_aggregates_everything_for_the_day(self, app, client, db):
        user(db)
        la = course(client, app)
        h = headers(app, "u1")
        post = lambda url, body: client.post(url, headers=h, json=body).get_json()["data"]  # noqa: E731
        post("/api/deadlines", {"title": "Late lab", "type": "LAB", "due_at": "2026-09-25", "course_id": la})
        post("/api/deadlines", {"title": "Quiz 2", "type": "QUIZ", "due_at": "2026-10-01", "course_id": la})
        post("/api/deadlines", {"title": "Far away", "due_at": "2026-12-01"})
        exam = post("/api/deadlines", {"title": "Midterm", "type": "EXAM", "due_at": "2026-10-20", "course_id": la})
        db.collection("course_concepts").document("k1").set({"user_id": "u1", "course_id": la, "name": "Rank", "mastery": 0.9, "attempts": 3})
        db.collection("course_concepts").document("k2").set({"user_id": "u1", "course_id": la, "name": "Eigenvalues", "mastery": 0.3, "attempts": 2})
        post(f"/api/courses/{la}/flashcards", {"front": "Q", "back": "A"})
        post("/api/study-plans/generate", {"deadline_id": exam["id"], "start_date": "2026-09-28"})

        data = client.get("/api/today?date=2026-09-28", headers=h).get_json()["data"]
        assert data["date"] == "2026-09-28"
        assert [d["title"] for d in data["overdue"]] == ["Late lab"]
        assert [d["title"] for d in data["upcoming"]] == ["Quiz 2"]
        assert data["cards"]["due_total"] == 1
        assert data["plan_tasks"] and all(t["course_id"] == la for t in data["plan_tasks"])
        assert data["focus"] == {"concept_id": "k2", "concept_name": "Eigenvalues", "course_id": la, "mastery": 0.3, "reason": "WEAKEST"}
        assert data["courses"][la]["code"] == "MATH-2250"
        assert data["streak"]["current"] == 0

    def test_focus_falls_back_to_unpractised_concept_before_an_exam(self, app, client, db):
        user(db)
        la = course(client, app)
        client.post("/api/deadlines", headers=headers(app, "u1"), json={"title": "Final", "type": "EXAM", "due_at": "2026-12-10", "course_id": la})
        db.collection("course_concepts").document("k1").set({"user_id": "u1", "course_id": la, "name": "Bases", "mastery": 0, "attempts": 0})
        focus = client.get("/api/today?date=2026-09-28", headers=headers(app, "u1")).get_json()["data"]["focus"]
        assert focus["reason"] == "NOT_PRACTISED" and focus["exam_title"] == "Final"

    def test_empty_and_bad_date(self, app, client, db):
        user(db)
        data = client.get("/api/today", headers=headers(app, "u1")).get_json()["data"]
        assert data["focus"] is None and data["plan_tasks"] == [] and data["overdue"] == []
        assert client.get("/api/today?date=tomorrow", headers=headers(app, "u1")).status_code == 400


# --- progress ------------------------------------------------------------------------------------------


class TestProgress:
    def test_overview_and_detail(self, app, client, db):
        user(db)
        la = course(client, app)
        os_ = course(client, app, name="Operating Systems", code="COMP-3300")
        now = datetime.now(timezone.utc)
        for i, (m, a) in enumerate([(0.9, 3), (0.5, 1), (0.0, 0)]):
            db.collection("course_concepts").document(f"k{i}").set({"user_id": "u1", "course_id": la, "name": f"C{i}", "mastery": m, "attempts": a})
        db.collection("flashcards").document("f1").set({"user_id": "u1", "course_id": la, "srs": {"interval_days": 30, "due_at": now + timedelta(days=5)}})
        db.collection("flashcards").document("f2").set({"user_id": "u1", "course_id": la, "srs": {"interval_days": 1, "due_at": now - timedelta(hours=1)}})
        db.collection("flashcards").document("f3").set({"user_id": "u1", "course_id": la, "suspended": True, "srs": {"due_at": now}})
        db.collection("flashcard_reviews").document("r1").set({"user_id": "u1", "course_id": la, "reviewed_at": now - timedelta(days=1)})
        db.collection("flashcard_reviews").document("r2").set({"user_id": "u1", "course_id": la, "reviewed_at": now - timedelta(days=20)})
        for i, pct in enumerate([60.0, 80.0]):
            db.collection("practice_attempts").document(f"a{i}").set({"user_id": "u1", "course_id": la, "status": "GRADED", "score_pct": pct, "title": f"Set {i}", "finished_at": now - timedelta(days=3 - i)})
        db.collection("practice_attempts").document("open").set({"user_id": "u1", "course_id": la, "status": "IN_PROGRESS"})
        client.post("/api/deadlines", headers=headers(app, "u1"), json={"title": "Quiz", "due_at": (now + timedelta(days=3)).date().isoformat(), "course_id": la})
        client.post("/api/grades-tracker", headers=headers(app, "u1"), json={"course_id": la, "name": "A1", "score": 18, "max": 20, "weight_pct": 10})

        day = now.date().isoformat()
        overview = {c["course_id"]: c for c in client.get(f"/api/progress/courses?date={day}", headers=headers(app, "u1")).get_json()["data"]}
        la_p = overview[la]
        assert la_p["concepts"] == {"total": 3, "practised": 2, "mastery_avg": 0.7}
        assert la_p["cards"] == {"total": 2, "due": 1, "mature": 1}
        assert la_p["reviews_7d"] == 1
        assert la_p["practice"] == {"attempts": 2, "average_pct": 70.0, "last_pct": 80.0}
        assert la_p["next_deadline"]["title"] == "Quiz" and la_p["grade_pct"] == 90.0
        assert overview[os_]["concepts"]["mastery_avg"] is None and overview[os_]["next_deadline"] is None

        detail = client.get(f"/api/progress/courses/{la}?date={day}", headers=headers(app, "u1")).get_json()["data"]
        assert [c["name"] for c in detail["concepts"]] == ["C1", "C0", "C2"]  # practised weakest first, then unpractised
        assert len(detail["reviews_per_day"]) == 14 and sum(d["count"] for d in detail["reviews_per_day"]) == 1
        assert [p["score_pct"] for p in detail["practice_history"]] == [60.0, 80.0]
        assert detail["grades"]["current_pct"] == 90.0

    def test_ownership(self, app, client, db):
        user(db)
        user(db, "u2")
        la = course(client, app)
        assert client.get(f"/api/progress/courses/{la}", headers=headers(app, "u2")).status_code == 404
        assert client.get("/api/progress/courses", headers=headers(app, "u2")).get_json()["data"] == []


# --- grades ------------------------------------------------------------------------------------------------


class TestGrades:
    @pytest.mark.parametrize("pct,point,letter", [(95, 4.0, "A+"), (90, 4.0, "A+"), (89.9, 3.9, "A"), (72, 2.7, "B-"), (50, 0.7, "D-"), (49.5, 0.0, "F")])
    def test_grade_points(self, pct, point, letter):
        assert grade_tracker_service.grade_point(pct) == (point, letter)

    def test_summary_needed_and_gpa(self, app, client, db):
        user(db)
        la = course(client, app, credits=4)
        os_ = course(client, app, name="Operating Systems", code="COMP-3300")
        h = headers(app, "u1")
        for body in [
            {"course_id": la, "name": "A1", "score": 18, "max": 20, "weight_pct": 10},
            {"course_id": la, "name": "Midterm", "score": 35, "max": 50, "weight_pct": 30},
            {"course_id": os_, "name": "Lab 1", "score": 10, "max": 10, "weight_pct": 5},
        ]:
            assert client.post("/api/grades-tracker", headers=h, json=body).status_code == 201
        data = client.get("/api/grades-tracker", headers=h).get_json()["data"]
        by = {c["course_id"]: c for c in data["courses"]}
        # (0.9·10 + 0.7·30) / 40 = 75 %
        assert by[la]["current_pct"] == 75.0 and by[la]["remaining_weight"] == 60 and by[la]["letter"] == "B" and by[la]["credits"] == 4
        assert by[os_]["current_pct"] == 100.0 and by[os_]["credits"] == 3.0
        assert data["gpa"] == round((3.0 * 4 + 4.0 * 3) / 7, 2)
        assert grade_tracker_service.needed_on_remaining(by[la], 80) == 83.3  # (80 − 30) / 60
        only = client.get(f"/api/grades-tracker?course_id={la}", headers=h).get_json()["data"]
        assert [c["course_id"] for c in only["courses"]] == [la]

    def test_update_delete_and_validation(self, app, client, db):
        user(db)
        user(db, "u2")
        la = course(client, app)
        h = headers(app, "u1")
        e = client.post("/api/grades-tracker", headers=h, json={"course_id": la, "name": "A1", "score": 18, "max": 20, "weight_pct": 10}).get_json()["data"]
        assert e["percent"] == 90.0
        assert client.put(f"/api/grades-tracker/{e['id']}", headers=h, json={"score": 20}).get_json()["data"]["percent"] == 100.0
        assert client.put(f"/api/grades-tracker/{e['id']}", headers=h, json={"score": 31}).status_code == 400  # > 1.5 × max
        for bad in [{"name": "x", "score": 1, "max": 0, "weight_pct": 5}, {"name": "x", "score": -1, "max": 10, "weight_pct": 5}, {"name": "x", "score": 1, "max": 10, "weight_pct": 101},
                    {"name": "", "score": 1, "max": 10, "weight_pct": 5}, {"name": "x", "score": "9", "max": 10, "weight_pct": 5}]:
            assert client.post("/api/grades-tracker", headers=h, json={"course_id": la, **bad}).status_code == 400, bad
        h2 = headers(app, "u2")
        assert client.put(f"/api/grades-tracker/{e['id']}", headers=h2, json={"score": 1}).status_code == 404
        assert client.delete(f"/api/grades-tracker/{e['id']}", headers=h2).status_code == 404
        assert client.post("/api/grades-tracker", headers=h2, json={"course_id": la, "name": "x", "score": 1, "max": 2, "weight_pct": 5}).status_code == 404
        assert client.delete(f"/api/grades-tracker/{e['id']}", headers=h).status_code == 200
        assert client.get("/api/grades-tracker", headers=h).get_json()["data"]["gpa"] is None

    def test_course_credits_validation(self, app, client, db):
        user(db)
        assert client.post("/api/courses", headers=headers(app, "u1"), json={"name": "X", "credits": 0}).status_code == 400
        assert client.post("/api/courses", headers=headers(app, "u1"), json={"name": "X", "credits": 1.5}).get_json()["data"]["credits"] == 1.5
