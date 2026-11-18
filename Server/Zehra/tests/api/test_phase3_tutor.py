"""
Phase 3 tests: tutor chat. Retrieval and Gemini streaming are faked.
"""

import json
from datetime import datetime, timedelta

import jwt
import pytest

from app.ai_framework.workflows import tutor_chat
from app.services import firebase_service, tutor_service
from app.services.retrieval_service import RetrievedChunk
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore


def chunk(n, title="Lecture 3 slides", page=4, distance=0.2, text=None):
    return RetrievedChunk(
        chunk_id=f"m{n}_0000{n}",
        material_id=f"m{n}",
        material_title=title,
        text=text or f"Source text {n} about Bayes' theorem.",
        page=page,
        page_end=page,
        distance=distance,
    )


def fake_stream(*pieces):
    def stream(prompt):
        fake_stream.last_prompt = prompt
        yield from pieces

    return stream


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    clear_rate_limits()
    return fake


def headers(app, uid):
    token = jwt.encode({"user_id": uid, "exp": datetime.utcnow() + timedelta(hours=1)}, app.config["JWT_SECRET_KEY"], algorithm="HS256")
    return {"Authorization": f"Bearer {token}"}


def setup_course(client, app, db, uid="u1"):
    db.collection("users").document(uid).set({"email": f"{uid}@uni.edu", "auth_provider": "email", "is_active": True, "student_profile": {"degree_level": "MASTER"}})
    res = client.post("/api/courses", headers=headers(app, uid), json={"name": "Machine Learning"})
    return res.get_json()["data"]["id"]


# --- workflow unit tests ------------------------------------------------------------


class TestTutorWorkflow:
    def test_citations_map_to_real_chunks(self):
        chunks = [chunk(1, page=4), chunk(2, title="Syllabus", page=1)]
        events = list(tutor_chat.run("What is Bayes' theorem?", chunks, [], "ML", "MASTER", stream=fake_stream("Bayes relates priors [1]", " to posteriors [2][1].")))
        answer = events[-1]["answer"]
        assert answer.grounded
        assert [c["n"] for c in answer.citations] == [1, 2]
        by_n = {c["n"]: c for c in answer.citations}
        assert by_n[1]["chunk_id"] == chunks[0].chunk_id and by_n[1]["page"] == 4
        assert by_n[2]["material_id"] == "m2" and by_n[2]["title"] == "Syllabus"

    def test_invalid_markers_are_removed(self):
        answer = tutor_chat.finalize("Claim [1]. Made up [7]. Also [2].", tutor_chat.select_sources([chunk(1)]))
        assert "[7]" not in answer.content and "[2]" not in answer.content and "[1]" in answer.content
        assert [c["n"] for c in answer.citations] == [1]

    def test_uncited_sources_are_not_listed(self):
        answer = tutor_chat.finalize("No citations here.", tutor_chat.select_sources([chunk(1), chunk(2)]))
        assert answer.citations == [] and answer.grounded

    def test_not_covered_when_no_chunks(self):
        events = list(tutor_chat.run("Explain quantum chromodynamics", [], [], "ML", "MASTER", stream=fake_stream("General answer [1].")))
        assert events[0] == {"type": "sources", "grounded": False, "count": 0}
        assert events[1]["text"].startswith("**Not covered by your course materials.**")
        answer = events[-1]["answer"]
        assert not answer.grounded
        assert answer.content.startswith("**Not covered by your course materials.**")
        assert answer.citations == [] and "[1]" not in answer.content
        assert "None of the course materials are relevant" in fake_stream.last_prompt

    def test_irrelevant_chunks_count_as_not_covered(self, monkeypatch):
        monkeypatch.setenv("TUTOR_MIN_SCORE", "0.5")
        events = list(tutor_chat.run("Unrelated", [chunk(1, distance=0.9)], [], "ML", None, stream=fake_stream("x")))
        assert events[-1]["answer"].grounded is False

    def test_prompt_is_university_level_with_integrity_rules(self):
        prompt = tutor_chat.build_prompt("Q?", tutor_chat.select_sources([chunk(1)]), [{"role": "user", "content": "earlier"}], "Machine Learning", "MASTER")
        assert "Master's student" in prompt and '"Machine Learning"' in prompt
        assert "Never write graded assignments" in prompt
        assert "[1] Lecture 3 slides, p. 4" in prompt
        assert "Student: earlier" in prompt
        assert "$...$" in prompt


# --- API tests ------------------------------------------------------------------------


class TestTutorAPI:
    @pytest.fixture(autouse=True)
    def fakes(self, monkeypatch):
        self.chunks = [chunk(1), chunk(2, title="Syllabus", page=1)]
        monkeypatch.setattr(tutor_service, "_default_retrieve", lambda uid, cid, q: self.chunks)
        monkeypatch.setattr(tutor_chat, "_default_stream", fake_stream("Answer part one [1] ", "and two [2]."))

    def test_session_lifecycle_and_json_answer(self, app, client, db):
        course_id = setup_course(client, app, db)
        h = headers(app, "u1")
        created = client.post(f"/api/courses/{course_id}/tutor/sessions", headers=h)
        assert created.status_code == 201
        sid = created.get_json()["data"]["id"]

        res = client.post(f"/api/tutor/sessions/{sid}/messages?stream=false", headers=h, json={"content": "What is Bayes' theorem and why does it matter?"})
        assert res.status_code == 200, res.get_json()
        data = res.get_json()["data"]
        assert data["message"]["content"] == "Answer part one [1] and two [2]."
        assert [c["title"] for c in data["message"]["citations"]] == ["Lecture 3 slides", "Syllabus"]
        assert data["session"]["title"].startswith("What is Bayes")

        detail = client.get(f"/api/tutor/sessions/{sid}", headers=h).get_json()["data"]
        assert [m["role"] for m in detail["messages"]] == ["user", "assistant"]
        assert detail["session"]["message_count"] == 2
        assert client.get(f"/api/courses/{course_id}/tutor/sessions", headers=h).get_json()["data"][0]["id"] == sid

        assert client.delete(f"/api/tutor/sessions/{sid}", headers=h).status_code == 200
        assert client.get(f"/api/tutor/sessions/{sid}", headers=h).status_code == 404

    def test_sse_stream_event_order(self, app, client, db):
        course_id = setup_course(client, app, db)
        h = headers(app, "u1")
        sid = client.post(f"/api/courses/{course_id}/tutor/sessions", headers=h).get_json()["data"]["id"]
        res = client.post(f"/api/tutor/sessions/{sid}/messages", headers=h, json={"content": "Explain priors"})
        assert res.status_code == 200 and res.mimetype == "text/event-stream"
        events = [block for block in res.get_data(as_text=True).split("\n\n") if block.strip()]
        names = [e.split("\n")[0].removeprefix("event: ") for e in events]
        assert names == ["start", "sources", "delta", "delta", "done"]
        done = json.loads(events[-1].split("\n")[1].removeprefix("data: "))
        assert len(done["message"]["citations"]) == 2

    def test_generation_failure_emits_error_and_keeps_question(self, app, client, db, monkeypatch):
        def broken(prompt):
            raise RuntimeError("quota exceeded, key=secret")
            yield  # pragma: no cover

        monkeypatch.setattr(tutor_chat, "_default_stream", broken)
        course_id = setup_course(client, app, db)
        h = headers(app, "u1")
        sid = client.post(f"/api/courses/{course_id}/tutor/sessions", headers=h).get_json()["data"]["id"]
        res = client.post(f"/api/tutor/sessions/{sid}/messages?stream=false", headers=h, json={"content": "Q"})
        assert res.status_code == 502 and "secret" not in res.get_data(as_text=True)
        messages = client.get(f"/api/tutor/sessions/{sid}", headers=h).get_json()["data"]["messages"]
        assert [m["role"] for m in messages] == ["user"]

    def test_retrieval_failure_falls_back_to_not_covered(self, app, client, db, monkeypatch):
        def fail(uid, cid, q):
            raise RuntimeError("index missing")

        monkeypatch.setattr(tutor_service, "_default_retrieve", fail)
        course_id = setup_course(client, app, db)
        h = headers(app, "u1")
        sid = client.post(f"/api/courses/{course_id}/tutor/sessions", headers=h).get_json()["data"]["id"]
        msg = client.post(f"/api/tutor/sessions/{sid}/messages?stream=false", headers=h, json={"content": "Q"}).get_json()["data"]["message"]
        assert msg["grounded"] is False and msg["content"].startswith("**Not covered")

    @pytest.mark.parametrize("body", [{}, {"content": ""}, {"content": "   "}, {"content": 5}, {"content": "x" * 4001}])
    def test_rejects_bad_questions(self, app, client, db, body):
        course_id = setup_course(client, app, db)
        h = headers(app, "u1")
        sid = client.post(f"/api/courses/{course_id}/tutor/sessions", headers=h).get_json()["data"]["id"]
        assert client.post(f"/api/tutor/sessions/{sid}/messages", headers=h, json=body).status_code == 400

    def test_ownership(self, app, client, db):
        course_id = setup_course(client, app, db, "u1")
        setup_course(client, app, db, "u2")
        sid = client.post(f"/api/courses/{course_id}/tutor/sessions", headers=headers(app, "u1")).get_json()["data"]["id"]
        h2 = headers(app, "u2")
        assert client.post(f"/api/courses/{course_id}/tutor/sessions", headers=h2).status_code == 404
        assert client.get(f"/api/tutor/sessions/{sid}", headers=h2).status_code == 404
        assert client.post(f"/api/tutor/sessions/{sid}/messages", headers=h2, json={"content": "hi"}).status_code == 404
        assert client.delete(f"/api/tutor/sessions/{sid}", headers=h2).status_code == 404
