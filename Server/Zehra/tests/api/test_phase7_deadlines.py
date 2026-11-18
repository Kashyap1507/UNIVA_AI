"""
Phase 7 tests: deadlines, syllabus extraction, iCal import and exam study plans.
Gemini, DNS and HTTP are faked; "now" is pinned to 2026-09-27.
"""

import io
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

import jwt
import pytest

from app.ai_framework.workflows.course_structure_generation import CourseStructure, DeadlineOut
from app.services import deadline_service, exam_plan_service, firebase_service
from app.services.course_service import ServiceError
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures"
NOW = datetime(2026, 9, 27, 12, tzinfo=timezone.utc)


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    monkeypatch.setattr(deadline_service, "_now", lambda: NOW)
    monkeypatch.setattr(exam_plan_service, "_now", lambda: NOW)
    clear_rate_limits()
    return fake


def headers(app, uid):
    token = jwt.encode({"user_id": uid, "exp": datetime.utcnow() + timedelta(hours=1)}, app.config["JWT_SECRET_KEY"], algorithm="HS256")
    return {"Authorization": f"Bearer {token}"}


def user(db, uid="u1"):
    db.collection("users").document(uid).set({"email": f"{uid}@uni.edu", "auth_provider": "email", "is_active": True, "student_profile": {"degree_level": "BACHELOR"}})


def course(client, app, uid="u1", name="Linear Algebra", code="MATH-2250"):
    return client.post("/api/courses", headers=headers(app, uid), json={"name": name, "code": code}).get_json()["data"]["id"]


def post(client, app, url, body, uid="u1"):
    return client.post(url, headers=headers(app, uid), json=body)


# --- deadlines CRUD ---------------------------------------------------------------------------------


class TestDeadlines:
    def test_create_list_update_complete_delete(self, app, client, db):
        user(db)
        la = course(client, app)
        h = headers(app, "u1")
        a = post(client, app, "/api/deadlines", {"title": "Midterm", "type": "EXAM", "due_at": "2026-10-20", "course_id": la, "weight_pct": 25}).get_json()["data"]
        b = post(client, app, "/api/deadlines", {"title": "Reading log", "due_at": "2026-10-02T18:30:00-04:00"}).get_json()["data"]
        assert a["all_day"] is True and b["all_day"] is False and b["due_at"] == "2026-10-02T22:30:00Z" and b["course_id"] is None
        listed = client.get("/api/deadlines", headers=h).get_json()["data"]
        assert [d["title"] for d in listed] == ["Reading log", "Midterm"]
        assert [d["id"] for d in client.get(f"/api/deadlines?course_id={la}", headers=h).get_json()["data"]] == [a["id"]]
        assert [d["id"] for d in client.get("/api/deadlines?from=2026-10-10&to=2026-10-31", headers=h).get_json()["data"]] == [a["id"]]

        done = client.put(f"/api/deadlines/{b['id']}", headers=h, json={"completed": True}).get_json()["data"]
        assert done["completed"] is True
        assert [d["id"] for d in client.get("/api/deadlines?include_completed=false", headers=h).get_json()["data"]] == [a["id"]]
        assert client.delete(f"/api/deadlines/{a['id']}", headers=h).status_code == 200
        assert db.docs("deadlines")[a["id"]]["is_deleted"] is True
        assert [d["id"] for d in client.get("/api/deadlines", headers=h).get_json()["data"]] == [b["id"]]

    def test_deleted_course_hides_its_deadlines(self, app, client, db):
        user(db)
        la = course(client, app)
        post(client, app, "/api/deadlines", {"title": "Quiz", "due_at": "2026-10-01", "course_id": la})
        client.delete(f"/api/courses/{la}", headers=headers(app, "u1"))
        assert client.get("/api/deadlines", headers=headers(app, "u1")).get_json()["data"] == []

    @pytest.mark.parametrize(
        "body",
        [{"due_at": "2026-10-01"}, {"title": "X"}, {"title": "X", "due_at": "next week"}, {"title": "X", "due_at": "2026-13-01"},
         {"title": "X", "due_at": "2026-10-01", "type": "HOMEWORK"}, {"title": "X", "due_at": "2026-10-01", "weight_pct": 120},
         {"title": "X", "due_at": "2026-10-01", "course_id": "nope"}],
    )
    def test_validation(self, app, client, db, body):
        user(db)
        assert post(client, app, "/api/deadlines", body).status_code in (400, 404)

    def test_bulk_skips_duplicates(self, app, client, db):
        user(db)
        la = course(client, app)
        post(client, app, "/api/deadlines", {"title": "Midterm Exam", "due_at": "2026-10-20", "course_id": la})
        items = [{"title": "midterm exam", "due_at": "2026-10-20", "course_id": la}, {"title": "Final", "type": "EXAM", "due_at": "2026-12-10", "course_id": la}, {"title": "Final", "type": "EXAM", "due_at": "2026-12-10", "course_id": la}]
        res = post(client, app, "/api/deadlines/bulk", {"source": "SYLLABUS", "deadlines": items})
        assert res.status_code == 201 and [d["title"] for d in res.get_json()["data"]] == ["Final"]
        assert res.get_json()["data"][0]["source"] == "SYLLABUS"
        assert post(client, app, "/api/deadlines/bulk", {"source": "ICAL", "deadlines": items}).status_code == 400
        assert post(client, app, "/api/deadlines/bulk", {"deadlines": [{"title": "x"}]}).get_json()["error"].startswith("deadlines[0]")

    def test_ownership(self, app, client, db):
        user(db)
        user(db, "u2")
        la = course(client, app)
        d = post(client, app, "/api/deadlines", {"title": "Midterm", "due_at": "2026-10-20", "course_id": la}).get_json()["data"]
        h2 = headers(app, "u2")
        assert client.put(f"/api/deadlines/{d['id']}", headers=h2, json={"completed": True}).status_code == 404
        assert client.delete(f"/api/deadlines/{d['id']}", headers=h2).status_code == 404
        assert client.get("/api/deadlines", headers=h2).get_json()["data"] == []
        assert post(client, app, "/api/deadlines", {"title": "X", "due_at": "2026-10-01", "course_id": la}, uid="u2").status_code == 404
        assert post(client, app, f"/api/courses/{la}/syllabus/extract", {}, uid="u2").status_code == 404


# --- syllabus -----------------------------------------------------------------------------------------


def syllabus(db, course_id, uid="u1", status="READY"):
    db.collection("course_materials").document("syl").set({"user_id": uid, "course_id": course_id, "title": "Syllabus", "kind": "SYLLABUS", "ingestion_status": status})
    for i, t in enumerate(["Midterm (25%): October 20.", "Final exam (40%) during finals week."]):
        db.collection("material_chunks").document(f"syl_{i}").set({"user_id": uid, "course_id": course_id, "material_id": "syl", "text": t, "chunk_index": i})


class TestSyllabus:
    def test_preview_marks_duplicates_and_saves_nothing(self, app, client, db, monkeypatch):
        user(db)
        la = course(client, app)
        syllabus(db, la)
        existing = post(client, app, "/api/deadlines", {"title": "Midterm", "type": "EXAM", "due_at": "2026-10-20", "course_id": la}).get_json()["data"]
        seen = {}

        def extract(prompt):
            seen["prompt"] = prompt
            return CourseStructure(deadlines=[
                DeadlineOut(title="Midterm", type="EXAM", due_date="2026-10-20", weight_pct=25),
                DeadlineOut(title="Final exam", type="EXAM", due_date=None, weight_pct=40),
                DeadlineOut(title="Assignment 1", type="ASSIGNMENT", due_date="2026-10-03"),
            ])

        monkeypatch.setattr(deadline_service, "_default_extract", extract)
        res = post(client, app, f"/api/courses/{la}/syllabus/extract", {})
        data = res.get_json()["data"]
        assert res.status_code == 200 and data["material_title"] == "Syllabus"
        assert [c["title"] for c in data["candidates"]] == ["Assignment 1", "Midterm", "Final exam"]
        assert data["candidates"][1]["duplicate_of"] == existing["id"] and data["candidates"][0]["duplicate_of"] is None
        assert "Midterm (25%): October 20." in seen["prompt"] and "Today is 2026-09-27" in seen["prompt"]
        assert len(db.docs("deadlines")) == 1

    def test_errors(self, app, client, db, monkeypatch):
        user(db)
        la = course(client, app)
        assert post(client, app, f"/api/courses/{la}/syllabus/extract", {}).status_code == 409  # no syllabus
        syllabus(db, la, status="PROCESSING")
        assert "still being processed" in post(client, app, f"/api/courses/{la}/syllabus/extract", {}).get_json()["error"]
        syllabus(db, la)

        def boom(prompt):
            raise RuntimeError("API key not valid")

        monkeypatch.setattr(deadline_service, "_default_extract", boom)
        res = post(client, app, f"/api/courses/{la}/syllabus/extract", {})
        assert res.status_code == 502 and "key" not in res.get_json()["error"]


# --- iCal -----------------------------------------------------------------------------------------------


def ics_upload(client, app, name, course_id=None, uid="u1"):
    data = {"file": (io.BytesIO((FIXTURES / name).read_bytes()), name)}
    if course_id:
        data["course_id"] = course_id
    return client.post("/api/deadlines/import-ical", headers=headers(app, uid), data=data, content_type="multipart/form-data")


class TestICal:
    def test_canvas_import_matches_courses_and_skips_meetings_and_past(self, app, client, db):
        user(db)
        la = course(client, app)
        os_ = course(client, app, name="Operating Systems", code="COMP-3300")
        res = ics_upload(client, app, "canvas.ics")
        assert res.status_code == 200, res.get_json()
        assert res.get_json()["data"] == {"created": 4, "updated": 0, "unchanged": 0, "unmatched": 1, "total": 4}
        items = {d["title"]: d for d in client.get("/api/deadlines", headers=headers(app, "u1")).get_json()["data"]}
        assert set(items) == {"Assignment 3: Eigenvalues", "Midterm Exam", "Essay 1", "Project proposal due"}
        assert items["Midterm Exam"]["course_id"] == la and items["Midterm Exam"]["type"] == "EXAM" and items["Midterm Exam"]["due_at"] == "2026-11-03T14:00:00Z"
        assert items["Project proposal due"]["course_id"] == os_ and items["Project proposal due"]["due_at"] == "2026-11-21T04:59:00Z"
        assert items["Essay 1"]["course_id"] is None and items["Essay 1"]["source"] == "ICAL"

    def test_moodle_import_and_reimport_updates_without_uncompleting(self, app, client, db):
        user(db)
        os_ = course(client, app, name="Operating Systems", code="COMP-3300")
        assert ics_upload(client, app, "moodle.ics").get_json()["data"]["created"] == 3
        items = {d["title"]: d for d in client.get("/api/deadlines", headers=headers(app, "u1")).get_json()["data"]}
        assert items["Quiz 2 closes"]["type"] == "QUIZ" and items["Quiz 2 closes"]["course_id"] == os_
        assert items["Lab report 4 is due"]["type"] == "LAB" and items["Final presentation"]["type"] == "PRESENTATION"
        assert "Tutorial session" not in items
        client.put(f"/api/deadlines/{items['Quiz 2 closes']['id']}", headers=headers(app, "u1"), json={"completed": True})

        moved = (FIXTURES / "moodle.ics").read_bytes().replace(b"DTSTART:20261012T220000Z", b"DTSTART:20261013T220000Z")
        res = client.post("/api/deadlines/import-ical", headers=headers(app, "u1"), data={"file": (io.BytesIO(moved), "m.ics")}, content_type="multipart/form-data")
        assert res.get_json()["data"] == {"created": 0, "updated": 1, "unchanged": 2, "unmatched": 1, "total": 3}
        quiz = db.docs("deadlines")[items["Quiz 2 closes"]["id"]]
        assert quiz["due_at"] == "2026-10-13T22:00:00Z" and quiz["completed"] is True

    def test_course_override_and_feed_url(self, app, client, db, monkeypatch):
        user(db)
        la = course(client, app)
        seen = {}

        def fetch(url):
            seen["url"] = url
            return (FIXTURES / "moodle.ics").read_bytes()

        monkeypatch.setattr(deadline_service, "fetch_feed", fetch)
        res = post(client, app, "/api/deadlines/import-ical", {"url": "webcal://moodle.example.edu/calendar/export.ics", "course_id": la})
        assert res.get_json()["data"]["unmatched"] == 0 and seen["url"].startswith("webcal://")
        assert all(d["course_id"] == la for d in client.get("/api/deadlines", headers=headers(app, "u1")).get_json()["data"])

    def test_bad_files(self, app, client, db):
        user(db)
        res = client.post("/api/deadlines/import-ical", headers=headers(app, "u1"), data={"file": (io.BytesIO(b"not a calendar"), "x.ics")}, content_type="multipart/form-data")
        assert res.status_code == 400
        assert post(client, app, "/api/deadlines/import-ical", {}).status_code == 400

    @pytest.mark.parametrize(
        "url,ip",
        [("http://example.com/cal.ics", "93.184.216.34"), ("https://localhost/cal.ics", "127.0.0.1"), ("https://intranet/cal.ics", "10.0.0.5"),
         ("https://metadata/cal.ics", "169.254.169.254"), ("https://user:pw@example.com/c.ics", "93.184.216.34"), ("ftp://example.com/c.ics", "93.184.216.34")],
    )
    def test_feed_url_guard(self, monkeypatch, url, ip):
        monkeypatch.setattr(deadline_service.socket, "getaddrinfo", lambda *a, **k: [(2, 1, 6, "", (ip, 443))])
        with pytest.raises(ServiceError):
            deadline_service.validate_feed_url(url)

    def test_feed_url_guard_allows_public_https(self, monkeypatch):
        monkeypatch.setattr(deadline_service.socket, "getaddrinfo", lambda *a, **k: [(2, 1, 6, "", ("93.184.216.34", 443))])
        assert deadline_service.validate_feed_url("webcal://canvas.example.edu/feeds/calendars/user_abc.ics") == "https://canvas.example.edu/feeds/calendars/user_abc.ics"


# --- study plans -----------------------------------------------------------------------------------------


UNITS = [{"ref_id": f"k{i}", "title": f"Concept {i}"} for i in range(1, 7)]


class TestPlanner:
    def test_long_countdown(self):
        days = exam_plan_service.build_days(date(2026, 10, 1), date(2026, 10, 15), 60, UNITS, True, "MATH-2250", "c1")
        assert len(days) == 14 and days[0]["date"] == "2026-10-01" and days[-1]["date"] == "2026-10-14"
        assert all(sum(t["minutes"] for t in d["tasks"]) <= 90 for d in days)  # mock exam day may exceed the budget
        assert all(d["tasks"][0]["type"] == "REVIEW_CARDS" for d in days)
        types = [[t["type"] for t in d["tasks"]] for d in days]
        assert "MOCK_EXAM" in types[11] and "REVIEW" in types[12] and "REVIEW" in types[13]
        assert "PRACTICE" in types[2] and "PRACTICE" in types[5]
        learn = [t["title"] for d in days for t in d["tasks"] if t["type"] == "LEARN"]
        assert learn[:6] == [f"Study: Concept {i}" for i in range(1, 7)]  # weakest first, each once
        assert learn[6].startswith("Revise: Concept 1")
        ids = [t["id"] for d in days for t in d["tasks"]]
        assert len(ids) == len(set(ids))

    def test_short_countdowns_and_no_units(self):
        one = exam_plan_service.build_days(date(2026, 10, 1), date(2026, 10, 2), 60, UNITS, False, "X", "c1")
        assert len(one) == 1 and one[0]["tasks"][0]["type"] == "LEARN"
        three = exam_plan_service.build_days(date(2026, 10, 1), date(2026, 10, 4), 60, [], False, "X", "c1")
        assert [t["type"] for t in three[1]["tasks"]] == ["MOCK_EXAM"] and three[2]["tasks"][0]["type"] == "REVIEW"
        assert three[0]["tasks"][0]["title"] == "Work through your X materials"
        long = exam_plan_service.build_days(date(2026, 1, 1), date(2026, 12, 1), 30, UNITS, False, "X", "c1")
        assert len(long) == exam_plan_service.MAX_DAYS

    def test_generate_from_deadline_uses_weakest_concepts_and_replaces_old_plan(self, app, client, db):
        user(db)
        la = course(client, app)
        for cid, name, mastery, attempts in [("k1", "Rank", 0.9, 3), ("k2", "Eigenvalues", 0.2, 2), ("k3", "Bases", 0.0, 0)]:
            db.collection("course_concepts").document(cid).set({"user_id": "u1", "course_id": la, "name": name, "mastery": mastery, "attempts": attempts})
        db.collection("flashcards").document("f1").set({"user_id": "u1", "course_id": la, "front": "Q", "back": "A"})
        exam = post(client, app, "/api/deadlines", {"title": "Midterm", "type": "EXAM", "due_at": "2026-10-07", "course_id": la}).get_json()["data"]
        res = post(client, app, "/api/study-plans/generate", {"deadline_id": exam["id"], "minutes_per_day": 90})
        plan = res.get_json()["data"]
        assert res.status_code == 201 and plan["exam_date"] == "2026-10-07" and plan["start_date"] == "2026-09-27"
        assert plan["title"] == "Countdown to Midterm" and len(plan["days"]) == 10
        first_learn = [t["title"] for t in plan["days"][0]["tasks"] if t["type"] == "LEARN"]
        assert first_learn[:2] == ["Study: Bases", "Study: Eigenvalues"]  # unpractised, then weakest

        task = plan["days"][0]["tasks"][0]
        updated = client.put(f"/api/study-plans/{plan['id']}/tasks/{task['id']}", headers=headers(app, "u1"), json={"done": True}).get_json()["data"]
        assert updated["tasks_done"] == 1
        again = post(client, app, "/api/study-plans/generate", {"deadline_id": exam["id"]}).get_json()["data"]
        listed = client.get("/api/study-plans", headers=headers(app, "u1")).get_json()["data"]
        assert [p["id"] for p in listed] == [again["id"]] and "days" not in listed[0]
        assert db.docs("study_plans")[plan["id"]]["is_deleted"] is True

        today = exam_plan_service.today_tasks("u1", date(2026, 9, 27))
        assert today and all(t["plan_id"] == again["id"] for t in today)

    @pytest.mark.parametrize(
        "body,status",
        [({}, 400), ({"course_id": "COURSE", "exam_date": "2026-09-20"}, 400), ({"course_id": "COURSE", "exam_date": "2026-10-20", "minutes_per_day": 5}, 400),
         ({"course_id": "COURSE", "exam_date": "2026-10-20", "start_date": "2026-10-01T10:00:00Z"}, 400), ({"deadline_id": "nope"}, 404),
         ({"course_id": "COURSE", "exam_date": "2026-10-20", "start_date": "2026-10-10"}, 201)],
    )
    def test_generate_validation(self, app, client, db, body, status):
        user(db)
        la = course(client, app)
        body = {k: (la if v == "COURSE" else v) for k, v in body.items()}
        assert post(client, app, "/api/study-plans/generate", body).status_code == status

    def test_deadline_without_course_and_ownership(self, app, client, db):
        user(db)
        user(db, "u2")
        loose = post(client, app, "/api/deadlines", {"title": "Exam", "type": "EXAM", "due_at": "2026-10-20"}).get_json()["data"]
        assert "Link this deadline" in post(client, app, "/api/study-plans/generate", {"deadline_id": loose["id"]}).get_json()["error"]
        la = course(client, app)
        plan = post(client, app, "/api/study-plans/generate", {"course_id": la, "exam_date": "2026-10-20"}).get_json()["data"]
        h2 = headers(app, "u2")
        assert client.get(f"/api/study-plans/{plan['id']}", headers=h2).status_code == 404
        assert client.put(f"/api/study-plans/{plan['id']}/tasks/d1-t1", headers=h2, json={"done": True}).status_code == 404
        assert client.delete(f"/api/study-plans/{plan['id']}", headers=h2).status_code == 404
        assert client.put(f"/api/study-plans/{plan['id']}/tasks/nope", headers=headers(app, "u1"), json={"done": True}).status_code == 404
        assert client.delete(f"/api/study-plans/{plan['id']}", headers=headers(app, "u1")).status_code == 200
