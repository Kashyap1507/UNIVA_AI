"""
Phase 5 tests: flashcards and SM-2. Gemini and retrieval are faked.
The SM-2 vectors are shared with the app's Sm2Test.kt.
"""

import json
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path

import jwt
import pytest

from app.ai_framework.workflows import flashcard_generation as fg
from app.services import firebase_service, flashcard_service, retrieval_service, srs
from app.services.retrieval_service import RetrievedChunk
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore

VECTORS = Path(__file__).resolve().parents[1] / "fixtures" / "srs_vectors.json"
KOTLIN_TEST = Path(__file__).resolve().parents[4] / "App/Zehra/tests/common/srs/Sm2Test.kt"

# --- SM-2 ---------------------------------------------------------------------------------


class TestSm2:
    @pytest.mark.parametrize("case", json.loads(VECTORS.read_text()), ids=lambda c: c["name"])
    def test_vectors(self, case):
        state = srs.SrsState(**case["start"])
        for grade, expected in zip(case["grades"], case["expected"]):
            state = srs.next_state(state, grade)
            assert state.to_dict() == expected

    def test_app_uses_the_same_vectors(self):
        match = re.search(r'VECTORS_JSON\s*=\s*"""(.*?)"""', KOTLIN_TEST.read_text(), re.S)
        assert match, "Sm2Test.kt must define VECTORS_JSON"
        assert json.loads(match.group(1)) == json.loads(VECTORS.read_text())

    @pytest.mark.parametrize("grade", [-1, 6, 2.5, True, "4"])
    def test_rejects_bad_grades(self, grade):
        with pytest.raises(ValueError):
            srs.next_state(srs.SrsState(), grade)

    def test_due_after(self):
        t = datetime(2026, 9, 27, 10, tzinfo=timezone.utc)
        assert srs.due_after(t, srs.SrsState(interval_days=6)) == t + timedelta(days=6)


# --- generation workflow (pure) -------------------------------------------------------------


def chunk(n, title="Lecture 2 slides", page=3):
    return RetrievedChunk(chunk_id=f"c{n}", material_id=f"m{n}", material_title=title, text=f"Excerpt {n} about eigenvalues.", page=page, page_end=page, distance=None)


class TestWorkflow:
    def test_to_cards_maps_sources_concepts_and_drops_duplicates(self):
        card_set = fg.CardSet(
            cards=[
                fg.CardOut(front="What is an eigenvalue?", back="A scalar λ with Av = λv.", source=1, concept="Eigenvalues"),
                fg.CardOut(front="what is an EIGENVALUE", back="dup within batch", source=1),
                fg.CardOut(front="Define a basis.", back="exists already", source=2),
                fg.CardOut(front="When is A diagonalisable?", back="When it has n independent eigenvectors.", source=9),
                fg.CardOut(front="  ", back="empty front"),
            ]
        )
        cards = fg.to_cards(card_set, [chunk(1), chunk(2)], 10, {"Eigenvalues": "k1"}, ["Define a basis"])
        assert [c["front"] for c in cards] == ["What is an eigenvalue?", "When is A diagonalisable?"]
        assert cards[0]["source"] == {"chunk_id": "c1", "material_id": "m1", "material_title": "Lecture 2 slides", "page": 3}
        assert cards[0]["concept_id"] == "k1"
        assert cards[1]["source"] is None and cards[1]["concept_id"] is None

    def test_prompt_is_university_level_and_lists_existing_cards(self):
        prompt = fg.build_prompt([chunk(1)], 5, "Linear Algebra", "MASTER", ["Eigenvalues"], ["Define a basis."], "Eigenvalues")
        assert "Master's student" in prompt and "Focus on the concept: Eigenvalues" in prompt
        assert "- Define a basis." in prompt and "[1] Lecture 2 slides (p. 3)" in prompt

    def test_count_is_capped(self):
        many = fg.CardSet(cards=[fg.CardOut(front=f"Q{i}?", back="A") for i in range(50)])
        assert len(fg.run([chunk(1)], 99, "X", None, {}, [], generate=lambda p: many)) == fg.MAX_CARDS


# --- API --------------------------------------------------------------------------------------


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    clear_rate_limits()
    return fake


def headers(app, uid):
    token = jwt.encode({"user_id": uid, "exp": datetime.utcnow() + timedelta(hours=1)}, app.config["JWT_SECRET_KEY"], algorithm="HS256")
    return {"Authorization": f"Bearer {token}"}


def setup_course(client, app, db, uid="u1", chunks=4):
    db.collection("users").document(uid).set({"email": f"{uid}@uni.edu", "auth_provider": "email", "is_active": True, "student_profile": {"degree_level": "BACHELOR"}})
    course_id = client.post("/api/courses", headers=headers(app, uid), json={"name": "Linear Algebra"}).get_json()["data"]["id"]
    for i in range(chunks):
        db.collection("material_chunks").document(f"{course_id}_ch{i}").set(
            {"user_id": uid, "course_id": course_id, "material_id": "mat1", "material_title": "Week 1 notes", "text": f"Chunk {i} text.", "page": i + 1, "chunk_index": i}
        )
    return course_id


def fake_model(monkeypatch, cards=None, error=None):
    log = {}

    def generate(prompt):
        log["prompt"] = prompt
        if error:
            raise error
        return fg.CardSet(cards=cards if cards is not None else [
            fg.CardOut(front="What is a vector space?", back="A set closed under addition and scaling.", source=1),
            fg.CardOut(front="Define linear independence.", back="No vector is a combination of the others.", source=2),
        ])

    monkeypatch.setattr(fg, "default_generate", generate)
    return log


def make_card(client, app, course_id, uid="u1", front="What is a matrix rank?", back="The dimension of its column space."):
    res = client.post(f"/api/courses/{course_id}/flashcards", headers=headers(app, uid), json={"front": front, "back": back})
    assert res.status_code == 201, res.get_json()
    return res.get_json()["data"]


class TestGenerateAPI:
    def test_generates_cards_with_sources_and_they_are_due(self, app, client, db, monkeypatch):
        course_id = setup_course(client, app, db)
        log = fake_model(monkeypatch)
        res = client.post(f"/api/courses/{course_id}/flashcards/generate", headers=headers(app, "u1"), json={"count": 5})
        assert res.status_code == 201, res.get_json()
        cards = res.get_json()["data"]
        assert len(cards) == 2 and cards[0]["origin"] == "AI"
        assert cards[0]["source"]["material_title"] == "Week 1 notes"
        assert "Bachelor's student" in log["prompt"]
        due = client.get(f"/api/flashcards?due=true&course_id={course_id}", headers=headers(app, "u1")).get_json()["data"]
        assert {c["id"] for c in due} == {c["id"] for c in cards}

    def test_repeat_generation_prefers_unused_excerpts_and_skips_existing_fronts(self, app, client, db, monkeypatch):
        course_id = setup_course(client, app, db, chunks=30)
        log = fake_model(monkeypatch)
        h = headers(app, "u1")
        client.post(f"/api/courses/{course_id}/flashcards/generate", headers=h, json={})
        first_prompt = log["prompt"]
        second = client.post(f"/api/courses/{course_id}/flashcards/generate", headers=h, json={}).get_json()["data"]
        assert second == []  # same fronts again → dropped as duplicates
        assert "- What is a vector space?" in log["prompt"]
        assert "Chunk 0 text." in first_prompt and "Chunk 0 text." not in log["prompt"]

    def test_concept_focus_uses_retrieval(self, app, client, db, monkeypatch):
        course_id = setup_course(client, app, db)
        db.collection("course_concepts").document("k1").set({"user_id": "u1", "course_id": course_id, "name": "Eigenvalues", "summary": "Scalars with Av = λv"})
        seen = {}

        def search(user_id, course_id_, query, k=8, **kw):
            seen["query"] = query
            return [chunk(1)]

        monkeypatch.setattr(retrieval_service, "search", search)
        fake_model(monkeypatch, cards=[fg.CardOut(front="What is an eigenvalue?", back="λ with Av = λv.", source=1)])
        cards = client.post(f"/api/courses/{course_id}/flashcards/generate", headers=headers(app, "u1"), json={"concept_id": "k1"}).get_json()["data"]
        assert seen["query"].startswith("Eigenvalues")
        assert cards[0]["concept_id"] == "k1"

    def test_no_materials_and_model_errors(self, app, client, db, monkeypatch):
        course_id = setup_course(client, app, db, chunks=0)
        fake_model(monkeypatch)
        h = headers(app, "u1")
        res = client.post(f"/api/courses/{course_id}/flashcards/generate", headers=h, json={})
        assert res.status_code == 409 and "Upload" in res.get_json()["error"]

        course2 = setup_course(client, app, db, uid="u1")
        fake_model(monkeypatch, error=RuntimeError("API key not valid"))
        res = client.post(f"/api/courses/{course2}/flashcards/generate", headers=h, json={})
        assert res.status_code == 502 and "key" not in res.get_json()["error"]

    @pytest.mark.parametrize("body", [{"count": 0}, {"count": 31}, {"count": "5"}, {"concept_id": "nope"}])
    def test_validation(self, app, client, db, monkeypatch, body):
        course_id = setup_course(client, app, db)
        fake_model(monkeypatch)
        assert client.post(f"/api/courses/{course_id}/flashcards/generate", headers=headers(app, "u1"), json=body).status_code == 400


class TestReviewAPI:
    def test_review_schedules_with_sm2(self, app, client, db):
        course_id = setup_course(client, app, db)
        card = make_card(client, app, course_id)
        h = headers(app, "u1")
        at = (datetime.now(timezone.utc) - timedelta(hours=2)).replace(microsecond=0)
        res = client.post(f"/api/flashcards/{card['id']}/review", headers=h, json={"grade": 4, "reviewed_at": at.isoformat().replace("+00:00", "Z"), "client_review_id": "rev-00000001"})
        data = res.get_json()["data"]
        assert res.status_code == 200 and data["duplicate"] is False
        assert data["srs"]["interval_days"] == 1 and data["srs"]["reps"] == 1
        assert datetime.fromisoformat(data["srs"]["due_at"]) == at + timedelta(days=1)
        assert client.get(f"/api/flashcards?due=true&course_id={course_id}", headers=h).get_json()["data"] == []

        # Second review builds on the first
        data = client.post(f"/api/flashcards/{card['id']}/review", headers=h, json={"grade": 4}).get_json()["data"]
        assert data["srs"]["interval_days"] == 6 and data["srs"]["reps"] == 2
        reviews = db.docs("flashcard_reviews")
        assert sorted(r["new_interval"] for r in reviews.values()) == [1, 6]

    def test_replayed_review_applies_once(self, app, client, db):
        course_id = setup_course(client, app, db)
        card = make_card(client, app, course_id)
        h = headers(app, "u1")
        body = {"grade": 5, "client_review_id": "offline-abc-123"}
        first = client.post(f"/api/flashcards/{card['id']}/review", headers=h, json=body).get_json()["data"]
        again = client.post(f"/api/flashcards/{card['id']}/review", headers=h, json=body).get_json()["data"]
        assert again["duplicate"] is True and again["srs"] == first["srs"]
        assert len(db.docs("flashcard_reviews")) == 1

    def test_future_review_time_is_clamped(self, app, client, db):
        course_id = setup_course(client, app, db)
        card = make_card(client, app, course_id)
        future = (datetime.now(timezone.utc) + timedelta(days=10)).isoformat()
        data = client.post(f"/api/flashcards/{card['id']}/review", headers=headers(app, "u1"), json={"grade": 4, "reviewed_at": future}).get_json()["data"]
        assert datetime.fromisoformat(data["srs"]["due_at"]) < datetime.now(timezone.utc) + timedelta(days=1, minutes=1)

    @pytest.mark.parametrize(
        "body",
        [{"grade": 6}, {"grade": True}, {"grade": "4"}, {}, {"grade": 4, "reviewed_at": "yesterday"}, {"grade": 4, "client_review_id": "short"}, {"grade": 4, "client_review_id": "bad id with spaces"}],
    )
    def test_review_validation(self, app, client, db, body):
        course_id = setup_course(client, app, db)
        card = make_card(client, app, course_id)
        assert client.post(f"/api/flashcards/{card['id']}/review", headers=headers(app, "u1"), json=body).status_code == 400


class TestCardsAPI:
    def test_due_list_skips_suspended_deleted_and_deleted_courses(self, app, client, db):
        course_id = setup_course(client, app, db)
        other_course = setup_course(client, app, db)
        h = headers(app, "u1")
        keep = make_card(client, app, course_id, front="Keep?")
        suspended = make_card(client, app, course_id, front="Suspended?")
        deleted = make_card(client, app, course_id, front="Deleted?")
        gone = make_card(client, app, other_course, front="Course gone?")
        assert client.put(f"/api/flashcards/{suspended['id']}", headers=h, json={"suspended": True}).status_code == 200
        assert client.delete(f"/api/flashcards/{deleted['id']}", headers=h).status_code == 200
        assert client.delete(f"/api/courses/{other_course}", headers=h).status_code == 200

        due = client.get("/api/flashcards?due=true", headers=h).get_json()["data"]
        assert [c["id"] for c in due] == [keep["id"]]
        summary = client.get("/api/flashcards/summary", headers=h).get_json()["data"]
        assert summary == {"due_total": 1, "due_by_course": {course_id: 1}}
        listed = client.get(f"/api/flashcards?course_id={course_id}", headers=h).get_json()["data"]
        assert {c["id"] for c in listed} == {keep["id"], suspended["id"]}
        assert gone["id"] not in {c["id"] for c in due}

    def test_edit_card(self, app, client, db):
        course_id = setup_course(client, app, db)
        db.collection("course_concepts").document("k1").set({"user_id": "u1", "course_id": course_id, "name": "Rank"})
        card = make_card(client, app, course_id)
        data = client.put(f"/api/flashcards/{card['id']}", headers=headers(app, "u1"), json={"back": "dim col(A)", "concept_id": "k1"}).get_json()["data"]
        assert data["back"] == "dim col(A)" and data["concept_id"] == "k1"

    @pytest.mark.parametrize(
        "method,url_suffix,body",
        [("post", "", {"front": "Q?"}), ("post", "", {"front": "x" * 301, "back": "A"}), ("post", "", {"front": "Q?", "back": "A", "concept_id": "zzz"}), ("post", "", [])],
    )
    def test_create_validation(self, app, client, db, method, url_suffix, body):
        course_id = setup_course(client, app, db)
        assert client.post(f"/api/courses/{course_id}/flashcards", headers=headers(app, "u1"), json=body).status_code == 400

    def test_list_needs_course_or_due_and_update_needs_fields(self, app, client, db):
        course_id = setup_course(client, app, db)
        card = make_card(client, app, course_id)
        h = headers(app, "u1")
        assert client.get("/api/flashcards", headers=h).status_code == 400
        assert client.put(f"/api/flashcards/{card['id']}", headers=h, json={}).status_code == 400
        assert client.put(f"/api/flashcards/{card['id']}", headers=h, json={"suspended": "yes"}).status_code == 400

    def test_ownership(self, app, client, db, monkeypatch):
        course_id = setup_course(client, app, db)
        setup_course(client, app, db, uid="u2")
        card = make_card(client, app, course_id)
        fake_model(monkeypatch)
        h2 = headers(app, "u2")
        assert client.post(f"/api/flashcards/{card['id']}/review", headers=h2, json={"grade": 4}).status_code == 404
        assert client.put(f"/api/flashcards/{card['id']}", headers=h2, json={"back": "x"}).status_code == 404
        assert client.delete(f"/api/flashcards/{card['id']}", headers=h2).status_code == 404
        assert client.get(f"/api/flashcards?course_id={course_id}", headers=h2).status_code == 404
        assert client.post(f"/api/courses/{course_id}/flashcards", headers=h2, json={"front": "Q", "back": "A"}).status_code == 404
        assert client.post(f"/api/courses/{course_id}/flashcards/generate", headers=h2, json={}).status_code == 404
        assert client.get("/api/flashcards?due=true", headers=h2).get_json()["data"] == []
        assert client.get("/api/flashcards").status_code == 401
