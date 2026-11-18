"""
Phase 4 tests: "Check my work". Gemini, page images and retrieval are faked.
The integrity tests use model outputs that deliberately try to leak answers.
"""

import json
from datetime import datetime, timedelta

import jwt
import pytest

from app.ai_framework.workflows import work_feedback as wf
from app.services import firebase_service, work_service
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore

# --- fixtures ------------------------------------------------------------------------

EXTRACTION = wf.Extraction(
    problems=[
        wf.ExtractedProblem(number="1", prompt="Differentiate x^2 sin x", student_work="2x sin x", student_answer="2x\\sin x"),
        wf.ExtractedProblem(number="2", prompt="Solve 3x = 12", student_work="x = 4", student_answer="4"),
    ]
)

LEAKY_EVALUATION = wf.Evaluation(
    summary="One error in problem 1.",
    items=[
        wf.EvaluatedItem(
            problem="1",
            correct=False,
            location="line 1",
            issue="You forgot the product rule",
            concept="product rule",
            hint="The answer is $2x \\sin x + x^2 \\cos x$",  # leaks the answer
            solution="By the product rule, d/dx(x^2 sin x) = 2x sin x + x^2 cos x.",
            correct_answer="2x\\sin x + x^2\\cos x",
        ),
        wf.EvaluatedItem(
            problem="2",
            correct=True,
            hint="Nicely done.",
            solution="x = 12/3 = 4",
            correct_answer="4",
        ),
    ],
)


def calls(extraction=EXTRACTION, evaluation=LEAKY_EVALUATION, verification=None, verify_error=False, log=None):
    log = log if log is not None else {}

    def extract(prompt, images):
        log["extract_images"] = images
        return extraction

    def evaluate(prompt):
        log["evaluate_prompt"] = prompt
        return evaluation

    def verify(prompt):
        log["verify_prompt"] = prompt
        if verify_error:
            raise RuntimeError("verifier down")
        return verification or wf.Verification(items=[wf.VerifiedItem(index=0), wf.VerifiedItem(index=1)])

    return wf.ModelCalls(extract=extract, evaluate=evaluate, verify=verify)


def run(is_practice, **kwargs):
    events = list(wf.run([("aW1n", "image/jpeg")], is_practice, "Calculus I", "BACHELOR", "", calls=calls(**kwargs)))
    return [e["step"] for e in events if "step" in e], events[-1]["result"]


def dump(result) -> str:
    return json.dumps(result)


# --- integrity ------------------------------------------------------------------------


class TestIntegrity:
    def test_graded_output_never_contains_solutions(self):
        steps, result = run(is_practice=False)
        assert steps == ["extract", "evaluate", "verify", "format"]
        assert result["mode"] == "HINTS"
        for item in result["items"]:
            assert "solution" not in item and "correct_answer" not in item
        text = dump(result)
        assert "product rule, d/dx" not in text and "x^2 \\\\cos x" not in text and "x^2 cos x" not in text
        assert result["items"][0]["hint"] == "Revisit how product rule applies at this step and redo it carefully."
        assert "Redacted 1 hint" in result["verifier_notes"]

    def test_answer_in_issue_or_location_is_removed(self):
        evaluation = wf.Evaluation(
            items=[
                wf.EvaluatedItem(problem="1", correct=False, location="should be 2x+3", issue="It should equal 2x+3", hint="Check the constant", correct_answer="2x+3")
            ]
        )
        _, result = run(is_practice=False, evaluation=evaluation)
        item = result["items"][0]
        assert "2x+3" not in dump(item)

    def test_verifier_fixed_hint_is_used_but_rechecked(self):
        verification = wf.Verification(items=[wf.VerifiedItem(index=0, leaks_answer=True, fixed_hint="Which rule applies to a product of two functions?"), wf.VerifiedItem(index=1)])
        _, result = run(is_practice=False, verification=verification)
        assert result["items"][0]["hint"] == "Which rule applies to a product of two functions?"

        bad_fix = wf.Verification(items=[wf.VerifiedItem(index=0, leaks_answer=True, fixed_hint="It is 2x sin x + x^2 cos x")])
        _, result = run(is_practice=False, verification=bad_fix)
        assert "cos" not in result["items"][0]["hint"]

    def test_verifier_failure_still_enforces_rules(self):
        steps, result = run(is_practice=False, verify_error=True)
        assert steps[-1] == "format"
        assert result["verified"] is False
        assert all("solution" not in i for i in result["items"])
        assert "x^2 cos x" not in dump(result)

    def test_graded_prompt_forbids_solutions_and_verifier_never_sees_them(self):
        log = {}
        list(wf.run([("aW1n", "image/jpeg")], False, "Calculus I", "MASTER", "", calls=calls(log=log)))
        assert "HINTS mode" in log["evaluate_prompt"] and "Master's student" in log["evaluate_prompt"]
        assert "Never write essays" in log["evaluate_prompt"]
        assert "By the product rule" not in log["verify_prompt"]

    def test_practice_keeps_worked_solutions(self):
        _, result = run(is_practice=True)
        assert result["mode"] == "FULL_SOLUTION"
        assert result["items"][0]["solution"].startswith("By the product rule")
        assert result["items"][1]["correct_answer"] == "4"

    def test_blank_pages_get_no_feedback(self):
        steps, result = run(is_practice=False, extraction=wf.Extraction(is_blank_or_question_only=True))
        assert steps == ["extract", "format"]
        assert result["items"] == [] and "your own work" in result["summary"]

    @pytest.mark.parametrize(
        "hint,answer,leaks",
        [("The answer is $x = 4$", "x=4", True), ("Try isolating x", "x=4", False), ("It's 4", "4", False), ("Use \\frac{1}{2}", "\\frac{1}{2}", True)],
    )
    def test_leak_detector(self, hint, answer, leaks):
        assert wf.hint_leaks_answer(hint, answer) is leaks


# --- API --------------------------------------------------------------------------------


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    clear_rate_limits()
    return fake


def headers(app, uid):
    token = jwt.encode({"user_id": uid, "exp": datetime.utcnow() + timedelta(hours=1)}, app.config["JWT_SECRET_KEY"], algorithm="HS256")
    return {"Authorization": f"Bearer {token}"}


def seed_student(client, app, db, uid):
    db.collection("users").document(uid).set({"email": f"{uid}@uni.edu", "auth_provider": "email", "is_active": True, "student_profile": {"degree_level": "BACHELOR"}})
    db.collection("children").document(f"learner_{uid}").set({"user_id": uid, "name": "Me", "is_self": True})
    course_id = client.post("/api/courses", headers=headers(app, uid), json={"name": "Calculus I"}).get_json()["data"]["id"]
    db.collection("documents").document(f"doc_{uid}").set({"child_id": f"learner_{uid}", "title": "Problem set 3"})
    return course_id, f"doc_{uid}"


class TestWorkAPI:
    @pytest.fixture(autouse=True)
    def sync_runs(self, monkeypatch):
        """Run submissions synchronously with fake pages, context and models."""
        self.fail = False

        def start(submission_id):
            work_service.run_submission(
                submission_id,
                load_images=lambda db, doc_id: [] if self.fail else [("aW1n", "image/jpeg")],
                context=lambda uid, cid, title: "",
                calls=calls(),
            )

        original = work_service.create_submission

        def create(*args, **kwargs):
            kwargs["start"] = start
            return original(*args, **kwargs)

        monkeypatch.setattr(work_service, "create_submission", create)
        monkeypatch.setattr("app.api.work_api.POLL_SECONDS", 0)

    def test_graded_submission_end_to_end(self, app, client, db):
        course_id, doc_id = seed_student(client, app, db, "u1")
        h = headers(app, "u1")
        res = client.post("/api/work/submissions", headers=h, json={"document_id": doc_id, "course_id": course_id, "is_practice": False})
        assert res.status_code == 202, res.get_json()
        sid = res.get_json()["data"]["id"]

        stream = client.get(f"/api/work/submissions/{sid}/stream", headers=h)
        assert stream.mimetype == "text/event-stream"
        blocks = [b for b in stream.get_data(as_text=True).split("\n\n") if b.strip()]
        assert blocks[-1].startswith("event: done")
        done = json.loads(blocks[-1].split("\n", 1)[1].removeprefix("data: "))
        assert done["status"] == "READY" and done["mode"] == "HINTS"
        assert all("solution" not in item for item in done["feedback"]["items"])

        stored = db.docs("work_submissions")[sid]
        assert "x^2 cos x" not in json.dumps(stored["feedback"])
        assert db.docs("documents")[doc_id]["course_id"] == course_id

        listed = client.get(f"/api/work/submissions?course_id={course_id}", headers=h).get_json()["data"]
        assert [s["id"] for s in listed] == [sid]

    def test_practice_submission_has_solutions(self, app, client, db):
        course_id, doc_id = seed_student(client, app, db, "u1")
        h = headers(app, "u1")
        sid = client.post("/api/work/submissions", headers=h, json={"document_id": doc_id, "course_id": course_id, "is_practice": True}).get_json()["data"]["id"]
        detail = client.get(f"/api/work/submissions/{sid}", headers=h).get_json()["data"]
        assert detail["mode"] == "FULL_SOLUTION" and detail["feedback"]["items"][0]["solution"]

    def test_failure_streams_error(self, app, client, db):
        self.fail = True
        course_id, doc_id = seed_student(client, app, db, "u1")
        h = headers(app, "u1")
        sid = client.post("/api/work/submissions", headers=h, json={"document_id": doc_id, "course_id": course_id}).get_json()["data"]["id"]
        body = client.get(f"/api/work/submissions/{sid}/stream", headers=h).get_data(as_text=True)
        assert "event: error" in body and "scan your work again" in body

    def test_ownership(self, app, client, db):
        course1, doc1 = seed_student(client, app, db, "u1")
        course2, doc2 = seed_student(client, app, db, "u2")
        h2 = headers(app, "u2")
        # someone else's document or course
        assert client.post("/api/work/submissions", headers=h2, json={"document_id": doc1, "course_id": course2}).status_code == 404
        assert client.post("/api/work/submissions", headers=h2, json={"document_id": doc2, "course_id": course1}).status_code == 404
        sid = client.post("/api/work/submissions", headers=headers(app, "u1"), json={"document_id": doc1, "course_id": course1}).get_json()["data"]["id"]
        assert client.get(f"/api/work/submissions/{sid}", headers=h2).status_code == 404
        assert client.get(f"/api/work/submissions/{sid}/stream", headers=h2).status_code == 404

    def test_validation(self, app, client, db):
        course_id, doc_id = seed_student(client, app, db, "u1")
        h = headers(app, "u1")
        assert client.post("/api/work/submissions", headers=h, json={"document_id": doc_id, "course_id": course_id, "is_practice": "yes"}).status_code == 400
        assert client.post("/api/work/submissions", headers=h, json={"course_id": course_id}).status_code == 400
        assert client.post("/api/work/submissions", headers=h, json=[]).status_code == 400
