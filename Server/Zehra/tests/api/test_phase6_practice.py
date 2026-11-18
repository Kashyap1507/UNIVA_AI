"""
Phase 6 tests: practice sets and mock exams. Gemini and retrieval are faked;
generation runs synchronously.
"""

import json
from datetime import datetime, timedelta, timezone

import jwt
import pytest

from app.ai_framework.workflows import exam_generation as eg
from app.services import firebase_service, practice_service, retrieval_service
from app.services.retrieval_service import RetrievedChunk
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore


def chunk(n):
    return RetrievedChunk(chunk_id=f"c{n}", material_id="m1", material_title="Week 2 slides", text=f"Excerpt {n}", page=n, page_end=n, distance=None)


EXAM = eg.ExamOut(
    title="Eigen things",
    questions=[
        eg.QuestionOut(type="MCQ", prompt="Which is an eigenvalue of $I_2$?", options=["0", "1", "2"], correct_option=1, explanation="$Iv = 1v$.", concept="Eigenvalues", source=1),
        eg.QuestionOut(type="NUMERIC", prompt="det of [[2,0],[0,3]]?", numeric_answer=6, explanation="Product of the diagonal.", concept="Determinants", source=2),
        eg.QuestionOut(type="SHORT", prompt="Define a basis.", model_answer="A linearly independent spanning set.", key_points=["independent", "spans"], concept="Bases", points=4),
        # dropped: MCQ with an out-of-range answer, NUMERIC without a value, SHORT without a model answer, duplicate prompt
        eg.QuestionOut(type="MCQ", prompt="Bad MCQ", options=["a", "b"], correct_option=5),
        eg.QuestionOut(type="NUMERIC", prompt="No value"),
        eg.QuestionOut(type="SHORT", prompt="No model answer"),
        eg.QuestionOut(type="MCQ", prompt="which is an eigenvalue of I_2", options=["x", "y"], correct_option=0),
    ],
)


# --- workflow -----------------------------------------------------------------------------------


class TestWorkflow:
    def test_validation_keeps_good_questions_and_numbers_them(self):
        qs = eg.to_questions(EXAM, [chunk(1), chunk(2)], 10, {"Eigenvalues": "k1"})
        assert [q["id"] for q in qs] == ["q1", "q2", "q3"]
        assert [q["type"] for q in qs] == ["MCQ", "NUMERIC", "SHORT"]
        assert qs[0]["concept_id"] == "k1" and qs[0]["source"]["page"] == 1
        assert qs[1]["concept_id"] is None and qs[1]["concept_name"] == "Determinants"
        assert [q["points"] for q in qs] == [1, 2, 4]

    def test_public_question_hides_answers(self):
        q = eg.to_questions(EXAM, [chunk(1)], 10, {})[2]
        public = eg.public_question(q)
        assert "model_answer" not in public and "explanation" not in public and "key_points" not in public
        mcq = eg.public_question(eg.to_questions(EXAM, [chunk(1)], 10, {})[0])
        assert "correct_option" not in mcq and mcq["options"] == ["0", "1", "2"]

    @pytest.mark.parametrize(
        "text,value",
        [("6", 6), (" 6.0 ", 6), ("-2e-3", -0.002), ("1,200", 1200), ("7/2", 3.5), ("−4", -4), ("3.2 m/s", 3.2), ("1.5×10^3", 1500), ("abc", None), ("1/0", None), (True, None), (4, 4.0)],
    )
    def test_parse_number(self, text, value):
        assert eg.parse_number(text) == value

    def test_numeric_tolerance(self):
        assert eg.numeric_correct("6.05", 6, None)  # default 1 %
        assert not eg.numeric_correct("6.1", 6, None)
        assert eg.numeric_correct("6.4", 6, 0.5)
        assert eg.numeric_correct("0", 0, None) and not eg.numeric_correct("0.001", 0, None)

    def test_mock_exam_time_limit(self):
        qs = [{"type": "MCQ"}] * 10 + [{"type": "PROOF"}] * 2
        assert eg.time_limit_minutes(qs) == 35  # 15 + 16 = 31 → 35
        assert eg.time_limit_minutes([{"type": "MCQ"}]) == 10

    def test_prompt_mentions_kind_and_level(self):
        prompt = eg.build_prompt("MOCK_EXAM", [chunk(1)], 20, "Linear Algebra", "MASTER", ["Eigenvalues"], ["Old question?"])
        assert "mock final exam" in prompt and "Master's student" in prompt and "- Old question?" in prompt


# --- API ------------------------------------------------------------------------------------------


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    clear_rate_limits()
    return fake


def headers(app, uid):
    token = jwt.encode({"user_id": uid, "exp": datetime.utcnow() + timedelta(hours=1)}, app.config["JWT_SECRET_KEY"], algorithm="HS256")
    return {"Authorization": f"Bearer {token}"}


class Env:
    def __init__(self, monkeypatch):
        self.exam = EXAM
        self.generate_error = None
        self.grade_error = None
        self.grades = None
        self.prompts = {}

        def generate(prompt):
            self.prompts["generate"] = prompt
            if self.generate_error:
                raise self.generate_error
            return self.exam

        def grade(prompt):
            self.prompts["grade"] = prompt
            if self.grade_error:
                raise self.grade_error
            return self.grades or eg.GradingOut(items=[eg.GradedOut(index=0, score=3.4, feedback="You missed that it spans.")])

        monkeypatch.setattr(eg, "default_generate", generate)
        monkeypatch.setattr(eg, "default_grade", grade)
        original = practice_service.create_set

        def create(*args, **kwargs):
            kwargs["start"] = lambda sid: practice_service.run_generation(sid)
            return original(*args, **kwargs)

        monkeypatch.setattr(practice_service, "create_set", create)


@pytest.fixture
def env(monkeypatch):
    return Env(monkeypatch)


def setup_course(client, app, db, uid="u1", chunks=4):
    db.collection("users").document(uid).set({"email": f"{uid}@uni.edu", "auth_provider": "email", "is_active": True, "student_profile": {"degree_level": "BACHELOR"}})
    course_id = client.post("/api/courses", headers=headers(app, uid), json={"name": "Linear Algebra", "code": "MATH-2250"}).get_json()["data"]["id"]
    for i in range(chunks):
        db.collection("material_chunks").document(f"{course_id}_ch{i}").set(
            {"user_id": uid, "course_id": course_id, "material_id": "m1", "material_title": "Week 2 slides", "text": f"Chunk {i}", "page": i + 1, "chunk_index": i}
        )
    db.collection("course_concepts").document(f"{course_id}_k1").set({"user_id": uid, "course_id": course_id, "name": "Eigenvalues", "mastery": 0.0, "attempts": 0})
    return course_id


def make_set(client, app, course_id, uid="u1", **body):
    body.setdefault("kind", "PRACTICE")
    res = client.post(f"/api/courses/{course_id}/practice-sets/generate", headers=headers(app, uid), json=body)
    assert res.status_code == 202, res.get_json()
    return res.get_json()["data"]["id"]


def start(client, app, set_id, uid="u1"):
    res = client.post(f"/api/practice-sets/{set_id}/attempts", headers=headers(app, uid))
    assert res.status_code == 201, res.get_json()
    return res.get_json()["data"]


def submit(client, app, attempt_id, answers, uid="u1"):
    return client.post(f"/api/practice-attempts/{attempt_id}/submit", headers=headers(app, uid), json={"answers": answers})


class TestGenerationAPI:
    def test_generated_set_is_ready_without_answers(self, app, client, db, env):
        course_id = setup_course(client, app, db)
        set_id = make_set(client, app, course_id, count=5)
        data = client.get(f"/api/practice-sets/{set_id}", headers=headers(app, "u1")).get_json()["data"]
        assert data["status"] == "READY" and data["title"] == "Eigen things" and data["question_count"] == 3
        body = json.dumps(data)
        assert "correct_option" not in body and "model_answer" not in body and "Product of the diagonal" not in body
        assert data["time_limit_min"] is None
        assert "Bachelor's student" in env.prompts["generate"] and "Write 5 questions" in env.prompts["generate"]
        listed = client.get(f"/api/practice-sets?course_id={course_id}", headers=headers(app, "u1")).get_json()["data"]
        assert [s["id"] for s in listed] == [set_id] and "questions" not in listed[0]

    def test_mock_exam_gets_time_limit_and_module_scope_uses_retrieval(self, app, client, db, env, monkeypatch):
        course_id = setup_course(client, app, db)
        db.collection("course_modules").document("mod1").set({"user_id": "u1", "course_id": course_id, "title": "Spectral theory", "order": 1})
        seen = {}

        def search(user_id, course_id_, query, k=8, **kw):
            seen["query"] = query
            return [chunk(1), chunk(2)]

        monkeypatch.setattr(retrieval_service, "search", search)
        set_id = make_set(client, app, course_id, kind="MOCK_EXAM", module_ids=["mod1"])
        data = client.get(f"/api/practice-sets/{set_id}", headers=headers(app, "u1")).get_json()["data"]
        assert seen["query"].startswith("Spectral theory") and data["time_limit_min"] == 10
        env.exam = eg.ExamOut(questions=[eg.QuestionOut(type="MCQ", prompt="A new question?", options=["a", "b"], correct_option=0)])
        custom = make_set(client, app, course_id, kind="MOCK_EXAM", time_limit_min=90)
        assert client.get(f"/api/practice-sets/{custom}", headers=headers(app, "u1")).get_json()["data"]["time_limit_min"] == 90

    def test_later_sets_are_told_about_earlier_questions(self, app, client, db, env):
        course_id = setup_course(client, app, db)
        make_set(client, app, course_id)
        make_set(client, app, course_id)
        assert "- Define a basis." in env.prompts["generate"]

    def test_failures_are_recorded_on_the_set(self, app, client, db, env):
        course_id = setup_course(client, app, db, chunks=0)
        set_id = make_set(client, app, course_id)
        data = client.get(f"/api/practice-sets/{set_id}", headers=headers(app, "u1")).get_json()["data"]
        assert data["status"] == "FAILED" and "Upload" in data["error"] and "questions" not in data

        course2 = setup_course(client, app, db)
        env.generate_error = RuntimeError("API key not valid")
        data = client.get(f"/api/practice-sets/{make_set(client, app, course2)}", headers=headers(app, "u1")).get_json()["data"]
        assert data["status"] == "FAILED" and "key" not in data["error"]

        env.generate_error, env.exam = None, eg.ExamOut(questions=[eg.QuestionOut(type="NUMERIC", prompt="x")])
        data = client.get(f"/api/practice-sets/{make_set(client, app, course2)}", headers=headers(app, "u1")).get_json()["data"]
        assert data["status"] == "FAILED" and "different scope" in data["error"]
        assert client.post(f"/api/practice-sets/{set_id}/attempts", headers=headers(app, "u1")).status_code == 409

    @pytest.mark.parametrize(
        "body",
        [{"kind": "QUIZ"}, {"kind": "PRACTICE", "count": 2}, {"kind": "MOCK_EXAM", "count": 41}, {"kind": "PRACTICE", "time_limit_min": 30},
         {"kind": "MOCK_EXAM", "time_limit_min": 1}, {"kind": "PRACTICE", "module_ids": ["nope"]}, {"kind": "PRACTICE", "module_ids": "mod1"}],
    )
    def test_validation(self, app, client, db, env, body):
        course_id = setup_course(client, app, db)
        assert client.post(f"/api/courses/{course_id}/practice-sets/generate", headers=headers(app, "u1"), json=body).status_code == 400


class TestAttempts:
    def test_scoring_review_per_concept_and_mastery(self, app, client, db, env):
        course_id = setup_course(client, app, db)
        set_id = make_set(client, app, course_id)
        attempt = start(client, app, set_id)
        assert attempt["deadline_at"] is None and len(attempt["questions"]) == 3
        assert "correct_option" not in json.dumps(attempt)

        res = submit(client, app, attempt["id"], [
            {"question_id": "q1", "response": 1},
            {"question_id": "q2", "response": "6.0"},
            {"question_id": "q3", "response": "An independent set."},
        ])
        result = res.get_json()["data"]
        assert res.status_code == 200 and result["status"] == "GRADED"
        # 1 + 2 + 3.5 (3.4 rounded to half marks) of 7
        assert result["points"] == 6.5 and result["max_points"] == 7 and result["score_pct"] == 92.9
        review = {r["id"]: r for r in result["review"]}
        assert review["q1"]["result"]["correct"] and review["q1"]["correct_option"] == 1
        assert review["q3"]["result"]["feedback"] == "You missed that it spans." and review["q3"]["model_answer"]
        assert "Student answer: An independent set." in env.prompts["grade"]
        assert result["per_concept"][f"{course_id}_k1"]["correct"] == 1
        assert result["per_concept"]["Determinants"]["points"] == 2

        concept = db.docs("course_concepts")[f"{course_id}_k1"]
        assert concept["mastery"] == 1.0 and concept["attempts"] == 1
        detail = client.get(f"/api/practice-sets/{set_id}", headers=headers(app, "u1")).get_json()["data"]
        assert detail["best_score_pct"] == 92.9 and detail["attempt_count"] == 1
        assert submit(client, app, attempt["id"], []).status_code == 409

        # A worse second attempt updates mastery gradually and keeps the best score
        second = start(client, app, set_id)
        result = submit(client, app, second["id"], [{"question_id": "q1", "response": 0}]).get_json()["data"]
        assert result["points"] == 0 and review["q1"]["result"]["correct"]
        assert db.docs("course_concepts")[f"{course_id}_k1"]["mastery"] == 0.7
        assert client.get(f"/api/practice-sets/{set_id}", headers=headers(app, "u1")).get_json()["data"]["best_score_pct"] == 92.9
        history = client.get(f"/api/practice-attempts?course_id={course_id}", headers=headers(app, "u1")).get_json()["data"]
        assert [a["id"] for a in history] == [second["id"], attempt["id"]]

    def test_grader_failure_leaves_written_answers_ungraded_then_regrade(self, app, client, db, env):
        course_id = setup_course(client, app, db)
        attempt = start(client, app, make_set(client, app, course_id))
        env.grade_error = RuntimeError("quota")
        result = submit(client, app, attempt["id"], [{"question_id": "q1", "response": 1}, {"question_id": "q3", "response": "Spanning set"}]).get_json()["data"]
        assert result["ungraded"] == 1 and result["max_points"] == 3 and result["points"] == 1
        assert {r["id"]: r for r in result["review"]}["q3"]["result"]["status"] == "NOT_GRADED"

        env.grade_error = None
        result = client.post(f"/api/practice-attempts/{attempt['id']}/regrade", headers=headers(app, "u1")).get_json()["data"]
        assert result["ungraded"] == 0 and result["max_points"] == 7 and result["points"] == 4.5

    def test_blank_written_answer_needs_no_model_call(self, app, client, db, env):
        course_id = setup_course(client, app, db)
        attempt = start(client, app, make_set(client, app, course_id))
        env.grade_error = RuntimeError("should not be called")
        result = submit(client, app, attempt["id"], []).get_json()["data"]
        assert result["ungraded"] == 0 and result["points"] == 0 and result["max_points"] == 7

    def test_mock_exam_deadline_and_late_flag(self, app, client, db, env):
        course_id = setup_course(client, app, db)
        set_id = make_set(client, app, course_id, kind="MOCK_EXAM", time_limit_min=30)
        attempt = start(client, app, set_id)
        started, deadline = (datetime.fromisoformat(attempt[k]) for k in ("started_at", "deadline_at"))
        assert deadline - started == timedelta(minutes=30) and attempt["time_limit_min"] == 30

        on_time = submit(client, app, attempt["id"], []).get_json()["data"]
        assert on_time["late"] is False

        late = start(client, app, set_id)
        db.collection("practice_attempts").document(late["id"]).update({"deadline_at": datetime.now(timezone.utc) - timedelta(minutes=5)})
        assert submit(client, app, late["id"], []).get_json()["data"]["late"] is True

    @pytest.mark.parametrize(
        "answers",
        [[{"question_id": "q9", "response": 1}], [{"question_id": "q1", "response": 7}], [{"question_id": "q1", "response": "1"}], [{"question_id": "q2", "response": 6}], "q1=1", [{"question_id": "q3", "response": "x" * 20001}]],
    )
    def test_answer_validation(self, app, client, db, env, answers):
        course_id = setup_course(client, app, db)
        attempt = start(client, app, make_set(client, app, course_id))
        assert submit(client, app, attempt["id"], answers).status_code == 400

    def test_ownership_and_delete(self, app, client, db, env):
        course_id = setup_course(client, app, db)
        setup_course(client, app, db, uid="u2")
        set_id = make_set(client, app, course_id)
        attempt = start(client, app, set_id)
        h2 = headers(app, "u2")
        assert client.get(f"/api/practice-sets/{set_id}", headers=h2).status_code == 404
        assert client.post(f"/api/practice-sets/{set_id}/attempts", headers=h2).status_code == 404
        assert client.post(f"/api/practice-attempts/{attempt['id']}/submit", headers=h2, json={"answers": []}).status_code == 404
        assert client.get(f"/api/practice-attempts/{attempt['id']}", headers=h2).status_code == 404
        assert client.get(f"/api/practice-sets?course_id={course_id}", headers=h2).status_code == 404
        assert client.post(f"/api/courses/{course_id}/practice-sets/generate", headers=h2, json={"kind": "PRACTICE"}).status_code == 404
        assert client.get("/api/practice-sets", headers=h2).status_code == 400
        assert client.delete(f"/api/practice-sets/{set_id}", headers=h2).status_code == 404
        assert client.delete(f"/api/practice-sets/{set_id}", headers=headers(app, "u1")).status_code == 200
        assert client.get(f"/api/practice-sets?course_id={course_id}", headers=headers(app, "u1")).get_json()["data"] == []
