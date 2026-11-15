"""
Practice sets, lecture quizzes and mock exams.

    practice_sets:     user_id, course_id, module_ids[], kind, title, status (PROCESSING|READY|FAILED),
                       error, questions[] (with answers, never sent before submission),
                       time_limit_min, question_count, total_points, is_deleted, created/updated_at
    practice_attempts: user_id, set_id, course_id, kind, status (IN_PROGRESS|GRADED),
                       answers[{question_id, response, status, correct, points_awarded, feedback}],
                       points, max_points, score_pct, per_concept{}, ungraded, late,
                       started_at, deadline_at, finished_at

Written answers the grader couldn't mark stay NOT_GRADED (not wrong); they are
left out of the score and can be regraded.
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from typing import Callable, Dict, List, Optional

from google.cloud.firestore_v1.base_query import FieldFilter

from app.ai_framework.workflows import exam_generation as eg
from app.models.course import CourseConcept, CourseModule
from app.services import course_service, flashcard_service, retrieval_service
from app.services.course_service import ServiceError
from app.services.firebase_service import get_firestore_client

logger = logging.getLogger(__name__)

SETS = "practice_sets"
ATTEMPTS = "practice_attempts"
LATE_GRACE = timedelta(seconds=60)
MIN_LIMIT, MAX_LIMIT = 5, 300
_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="exam-generation")


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(value):
    return value.isoformat() if hasattr(value, "isoformat") else value


def _db():
    return get_firestore_client()


# --- responses ---------------------------------------------------------------------------------


def set_response(set_id: str, data: Dict, include_questions: bool = False) -> Dict:
    out = {
        "id": set_id,
        "course_id": data.get("course_id"),
        "module_ids": data.get("module_ids") or [],
        "kind": data.get("kind"),
        "title": data.get("title"),
        "status": data.get("status"),
        "error": data.get("error"),
        "time_limit_min": data.get("time_limit_min"),
        "question_count": data.get("question_count", 0),
        "total_points": data.get("total_points", 0),
        "best_score_pct": data.get("best_score_pct"),
        "attempt_count": data.get("attempt_count", 0),
        "created_at": _iso(data.get("created_at")),
    }
    if include_questions:
        out["questions"] = [eg.public_question(q) for q in data.get("questions") or []]
    return out


def attempt_response(attempt_id: str, data: Dict, questions: Optional[List[Dict]] = None) -> Dict:
    out = {
        "id": attempt_id,
        "set_id": data.get("set_id"),
        "course_id": data.get("course_id"),
        "kind": data.get("kind"),
        "title": data.get("title"),
        "status": data.get("status"),
        "points": data.get("points"),
        "max_points": data.get("max_points"),
        "score_pct": data.get("score_pct"),
        "per_concept": data.get("per_concept") or {},
        "ungraded": data.get("ungraded", 0),
        "late": data.get("late", False),
        "started_at": _iso(data.get("started_at")),
        "deadline_at": _iso(data.get("deadline_at")),
        "finished_at": _iso(data.get("finished_at")),
    }
    if data.get("status") == "GRADED" and questions is not None:
        # After submission the student sees answers and explanations
        answers = {a["question_id"]: a for a in data.get("answers") or []}
        out["review"] = [
            {
                **eg.public_question(q),
                "correct_option": q.get("correct_option"),
                "numeric_answer": q.get("numeric_answer"),
                "model_answer": q.get("model_answer"),
                "key_points": q.get("key_points"),
                "explanation": q.get("explanation"),
                "source": q.get("source"),
                "response": answers.get(q["id"], {}).get("response"),
                "result": {k: answers.get(q["id"], {}).get(k) for k in ("status", "correct", "points_awarded", "feedback")},
            }
            for q in questions
        ]
    return out


# --- sets ----------------------------------------------------------------------------------------


def _get_set(user_id: str, set_id: str):
    if not set_id or not isinstance(set_id, str):
        raise ServiceError("Practice set not found", 404)
    ref = _db().collection(SETS).document(set_id)
    snap = ref.get()
    data = snap.to_dict() if snap.exists else None
    if not data or data.get("user_id") != user_id or data.get("is_deleted"):
        raise ServiceError("Practice set not found", 404)
    return ref, data


def get_set(user_id: str, set_id: str) -> Dict:
    _, data = _get_set(user_id, set_id)
    return set_response(set_id, data, include_questions=data.get("status") == "READY")


def list_sets(user_id: str, course_id: str) -> List[Dict]:
    course_service.get_course(user_id, course_id)
    rows = [
        (d.id, d.to_dict() or {})
        for d in _db().collection(SETS).where(filter=FieldFilter("user_id", "==", user_id)).where(filter=FieldFilter("course_id", "==", course_id)).stream()
    ]
    rows = [r for r in rows if not r[1].get("is_deleted")]
    rows.sort(key=lambda r: r[1].get("created_at") or datetime.min.replace(tzinfo=timezone.utc), reverse=True)
    return [set_response(i, d) for i, d in rows]


def delete_set(user_id: str, set_id: str) -> None:
    ref, _ = _get_set(user_id, set_id)
    ref.update({"is_deleted": True, "updated_at": _now()})


def _count(kind: str, count) -> int:
    lo, hi, default = eg.COUNT_LIMITS[kind]
    if count is None:
        return default
    if not isinstance(count, int) or isinstance(count, bool) or not lo <= count <= hi:
        raise ServiceError(f"count must be between {lo} and {hi} for {kind}")
    return count


def create_set(
    user_id: str,
    course_id: str,
    kind,
    count=None,
    module_ids=None,
    time_limit_min=None,
    start: Optional[Callable[[str], None]] = None,
) -> Dict:
    if kind not in eg.KINDS:
        raise ServiceError(f"kind must be one of: {', '.join(eg.KINDS)}")
    count = _count(kind, count)
    course = course_service.get_course(user_id, course_id)
    if module_ids is None:
        module_ids = []
    if not isinstance(module_ids, list) or not all(isinstance(m, str) for m in module_ids) or len(module_ids) > 20:
        raise ServiceError("module_ids must be a list of module ids")
    if module_ids:
        known = {m.id for m in course_service.list_modules(user_id, course_id)}
        if not set(module_ids) <= known:
            raise ServiceError("module_ids must be modules of this course")
    if time_limit_min is not None:
        if kind != "MOCK_EXAM":
            raise ServiceError("time_limit_min is only for mock exams")
        if not isinstance(time_limit_min, int) or isinstance(time_limit_min, bool) or not MIN_LIMIT <= time_limit_min <= MAX_LIMIT:
            raise ServiceError(f"time_limit_min must be between {MIN_LIMIT} and {MAX_LIMIT}")

    titles = {"PRACTICE": "Practice set", "LECTURE_QUIZ": "Lecture quiz", "MOCK_EXAM": "Mock exam"}
    now = _now()
    data = {
        "user_id": user_id,
        "course_id": course_id,
        "module_ids": module_ids,
        "kind": kind,
        "title": f"{titles[kind]} · {course.code or course.name}",
        "status": "PROCESSING",
        "error": None,
        "questions": [],
        "requested_count": count,
        "requested_time_limit": time_limit_min,
        "time_limit_min": None,
        "question_count": 0,
        "total_points": 0,
        "attempt_count": 0,
        "best_score_pct": None,
        "is_deleted": False,
        "created_at": now,
        "updated_at": now,
    }
    ref = _db().collection(SETS).document()
    ref.set(data)
    (start or (lambda sid: _executor.submit(run_generation, sid)))(ref.id)
    return set_response(ref.id, data)


def _module_chunks(user_id: str, course_id: str, module_ids: List[str], embed_query=None) -> list:
    chunks, seen = [], set()
    for module_id in module_ids:
        module = CourseModule.get_by_id(_db(), module_id)
        if not module:
            continue
        concepts = [c.name for c in course_service.list_concepts(user_id, course_id, module_id)]
        query = f"{module.title}: {', '.join(concepts)}"
        for c in retrieval_service.search(user_id, course_id, query, k=max(4, 12 // len(module_ids)), embed_query=embed_query):
            if c.chunk_id not in seen:
                seen.add(c.chunk_id)
                chunks.append(c)
    return chunks


def run_generation(set_id: str, generate: Optional[Callable] = None, embed_query=None) -> None:
    """Background job. Never raises; records the outcome on the set."""
    ref = _db().collection(SETS).document(set_id)
    data = ref.get().to_dict() or {}
    user_id, course_id, kind = data.get("user_id"), data.get("course_id"), data.get("kind")
    started = time.time()

    def fail(message: str, log_error: str):
        ref.update({"status": "FAILED", "error": message, "updated_at": _now()})
        _log(user_id, course_id, kind, False, started, 0, log_error)

    try:
        course = course_service.get_course(user_id, course_id)
        concepts = course_service.list_concepts(user_id, course_id)
        existing = []
        for d in _db().collection(SETS).where(filter=FieldFilter("user_id", "==", user_id)).where(filter=FieldFilter("course_id", "==", course_id)).stream():
            other = d.to_dict() or {}
            if d.id != set_id and not other.get("is_deleted"):
                existing += [q.get("prompt", "") for q in other.get("questions") or []]
        if data.get("module_ids"):
            chunks = _module_chunks(user_id, course_id, data["module_ids"], embed_query)
        else:
            chunks = flashcard_service.spread_chunks(user_id, course_id, set(), n=16 if kind == "MOCK_EXAM" else 12)
        if not chunks:
            return fail("Upload and process some course materials first.", "no chunks")

        user = _db().collection("users").document(user_id).get().to_dict() or {}
        level = (user.get("student_profile") or {}).get("degree_level")
        count = data.get("requested_count") or eg.COUNT_LIMITS[kind][2]
        prompt = eg.build_prompt(kind, chunks, count, course.name, level, [c.name for c in concepts], existing)
        exam = (generate or eg.default_generate)(prompt)
        questions = eg.to_questions(exam, chunks, count, {c.name: c.id for c in concepts}, existing)
        if not questions:
            return fail("Couldn't write questions from these materials. Try a different scope.", "no valid questions")
        limit = data.get("requested_time_limit") or (eg.time_limit_minutes(questions) if kind == "MOCK_EXAM" else None)
        ref.update(
            {
                "status": "READY",
                "questions": questions,
                "question_count": len(questions),
                "total_points": sum(q["points"] for q in questions),
                "time_limit_min": limit,
                "title": (exam.title or "").strip()[:120] or data.get("title"),
                "updated_at": _now(),
            }
        )
        _log(user_id, course_id, kind, True, started, len(questions), None)
    except Exception as e:
        logger.error(f"Practice set generation failed for {set_id}: {e}", exc_info=True)
        fail("Couldn't create this set right now. Please try again later.", str(e)[:300])


# --- attempts ---------------------------------------------------------------------------------------


def _get_attempt(user_id: str, attempt_id: str):
    if not attempt_id or not isinstance(attempt_id, str):
        raise ServiceError("Attempt not found", 404)
    ref = _db().collection(ATTEMPTS).document(attempt_id)
    snap = ref.get()
    data = snap.to_dict() if snap.exists else None
    if not data or data.get("user_id") != user_id:
        raise ServiceError("Attempt not found", 404)
    return ref, data


def start_attempt(user_id: str, set_id: str) -> Dict:
    _, practice_set = _get_set(user_id, set_id)
    if practice_set.get("status") != "READY":
        raise ServiceError("This set isn't ready yet", 409)
    now = _now()
    limit = practice_set.get("time_limit_min")
    data = {
        "user_id": user_id,
        "set_id": set_id,
        "course_id": practice_set["course_id"],
        "kind": practice_set["kind"],
        "title": practice_set.get("title"),
        "status": "IN_PROGRESS",
        "answers": [],
        "started_at": now,
        "deadline_at": now + timedelta(minutes=limit) if limit else None,
        "finished_at": None,
    }
    ref = _db().collection(ATTEMPTS).document()
    ref.set(data)
    return {**attempt_response(ref.id, data), "time_limit_min": limit, "questions": [eg.public_question(q) for q in practice_set["questions"]]}


def _validate_answers(answers, questions: List[Dict]) -> Dict[str, object]:
    if not isinstance(answers, list):
        raise ServiceError("answers must be a list")
    by_id = {q["id"]: q for q in questions}
    out = {}
    for a in answers:
        if not isinstance(a, dict) or a.get("question_id") not in by_id:
            raise ServiceError("Each answer needs a question_id from this set")
        q, response = by_id[a["question_id"]], a.get("response")
        if response is None or response == "":
            continue
        if q["type"] == "MCQ":
            if not isinstance(response, int) or isinstance(response, bool) or not 0 <= response < len(q["options"]):
                raise ServiceError(f"{q['id']}: choose one of the options")
        elif not isinstance(response, str) or len(response) > 20_000:
            raise ServiceError(f"{q['id']}: the answer must be text of at most 20000 characters")
        out[a["question_id"]] = response
    return out


def _score(questions: List[Dict], results: Dict[str, Dict]) -> Dict:
    points = max_points = 0.0
    ungraded = 0
    per_concept: Dict[str, Dict] = {}
    for q in questions:
        r = results[q["id"]]
        if r["status"] != "GRADED":
            ungraded += 1
            continue
        points += r["points_awarded"]
        max_points += q["points"]
        key = q.get("concept_id") or q.get("concept_name") or "_other"
        c = per_concept.setdefault(key, {"concept_id": q.get("concept_id"), "name": q.get("concept_name") or "Other", "points": 0.0, "max_points": 0.0, "correct": 0, "total": 0})
        c["points"] += r["points_awarded"]
        c["max_points"] += q["points"]
        c["correct"] += 1 if r["correct"] else 0
        c["total"] += 1
    return {
        "points": points,
        "max_points": max_points,
        "score_pct": round(100 * points / max_points, 1) if max_points else None,
        "per_concept": per_concept,
        "ungraded": ungraded,
    }


def _update_mastery(user_id: str, per_concept: Dict[str, Dict]) -> None:
    for c in per_concept.values():
        concept_id = c.get("concept_id")
        if not concept_id or not c["max_points"]:
            continue
        ref = _db().collection(CourseConcept.collection_name).document(concept_id)
        snap = ref.get()
        data = snap.to_dict() if snap.exists else None
        if not data or data.get("user_id") != user_id:
            continue
        pct = c["points"] / c["max_points"]
        old, attempts = data.get("mastery") or 0.0, data.get("attempts") or 0
        mastery = pct if attempts == 0 else 0.7 * old + 0.3 * pct
        ref.update({"mastery": round(mastery, 3), "attempts": attempts + c["total"], "last_practiced_at": _now()})


def _grade(attempt: Dict, questions: List[Dict], responses: Dict[str, object], previous: Optional[Dict[str, Dict]], grade) -> Dict[str, Dict]:
    results: Dict[str, Dict] = {}
    written = []
    for q in questions:
        response = responses.get(q["id"])
        if previous and previous.get(q["id"], {}).get("status") == "GRADED":
            results[q["id"]] = previous[q["id"]]
        elif q["type"] in eg.WRITTEN:
            written.append(q)
        else:
            results[q["id"]] = eg.mark_objective(q, response)
    if written:
        course = course_service.get_course(attempt["user_id"], attempt["course_id"])
        user = _db().collection("users").document(attempt["user_id"]).get().to_dict() or {}
        level = (user.get("student_profile") or {}).get("degree_level")
        try:
            marked = eg.grade_written([(q, responses.get(q["id"])) for q in written], course.name, level, grade)
        except Exception as e:
            logger.error(f"Written answer grading failed: {e}", exc_info=True)
            marked = [
                {"status": "GRADED", "correct": False, "points_awarded": 0, "feedback": "No answer."}
                if not str(responses.get(q["id"]) or "").strip()
                else {"status": "NOT_GRADED", "correct": None, "points_awarded": 0, "feedback": None}
                for q in written
            ]
        for q, r in zip(written, marked):
            results[q["id"]] = r
    return results


def _finish(ref, attempt: Dict, practice_set: Dict, responses: Dict[str, object], results: Dict[str, Dict], late: bool, first: bool) -> Dict:
    questions = practice_set["questions"]
    score = _score(questions, results)
    answers = [{"question_id": q["id"], "response": responses.get(q["id"]), **results[q["id"]]} for q in questions]
    updates = {"status": "GRADED", "answers": answers, "late": late, **score}
    if first:
        updates["finished_at"] = _now()
    ref.update(updates)
    attempt.update(updates)

    set_ref = _db().collection(SETS).document(attempt["set_id"])
    set_updates = {"updated_at": _now()}
    if first:
        set_updates["attempt_count"] = (practice_set.get("attempt_count") or 0) + 1
    if score["score_pct"] is not None and (practice_set.get("best_score_pct") is None or score["score_pct"] > practice_set["best_score_pct"]):
        set_updates["best_score_pct"] = score["score_pct"]
    set_ref.update(set_updates)
    if first:
        _update_mastery(attempt["user_id"], score["per_concept"])
    return attempt_response(ref.id, attempt, questions)


def submit_attempt(user_id: str, attempt_id: str, answers, grade: Optional[Callable] = None) -> Dict:
    ref, attempt = _get_attempt(user_id, attempt_id)
    if attempt.get("status") != "IN_PROGRESS":
        raise ServiceError("This attempt was already submitted", 409)
    _, practice_set = _get_set(user_id, attempt["set_id"])
    responses = _validate_answers(answers, practice_set["questions"])
    deadline = attempt.get("deadline_at")
    late = bool(deadline and _now() > deadline + LATE_GRACE)
    results = _grade(attempt, practice_set["questions"], responses, None, grade)
    from app.services import study_activity_service

    study_activity_service.record(user_id, "practice")
    return _finish(ref, attempt, practice_set, responses, results, late, first=True)


def regrade_attempt(user_id: str, attempt_id: str, grade: Optional[Callable] = None) -> Dict:
    """Retry written answers the grader couldn't mark."""
    ref, attempt = _get_attempt(user_id, attempt_id)
    if attempt.get("status") != "GRADED":
        raise ServiceError("Submit the attempt first", 409)
    _, practice_set = _get_set(user_id, attempt["set_id"])
    previous = {a["question_id"]: a for a in attempt.get("answers") or []}
    if not any(a.get("status") == "NOT_GRADED" for a in previous.values()):
        return attempt_response(attempt_id, attempt, practice_set["questions"])
    responses = {qid: a.get("response") for qid, a in previous.items() if a.get("response") not in (None, "")}
    prev_results = {qid: {k: a.get(k) for k in ("status", "correct", "points_awarded", "feedback")} for qid, a in previous.items()}
    results = _grade(attempt, practice_set["questions"], responses, prev_results, grade)
    return _finish(ref, attempt, practice_set, responses, results, attempt.get("late", False), first=False)


def get_attempt(user_id: str, attempt_id: str) -> Dict:
    _, attempt = _get_attempt(user_id, attempt_id)
    snap = _db().collection(SETS).document(attempt["set_id"]).get()
    questions = (snap.to_dict() or {}).get("questions") if snap.exists else None
    return attempt_response(attempt_id, attempt, questions or [])


def list_attempts(user_id: str, course_id: str) -> List[Dict]:
    course_service.get_course(user_id, course_id)
    rows = [
        (d.id, d.to_dict() or {})
        for d in _db().collection(ATTEMPTS).where(filter=FieldFilter("user_id", "==", user_id)).where(filter=FieldFilter("course_id", "==", course_id)).stream()
    ]
    rows = [r for r in rows if r[1].get("status") == "GRADED"]
    rows.sort(key=lambda r: r[1].get("finished_at") or datetime.min.replace(tzinfo=timezone.utc), reverse=True)
    return [attempt_response(i, d) for i, d in rows]


def _log(user_id, course_id, kind, success: bool, started: float, questions: int, error: Optional[str]):
    try:
        from app.ai_framework.utils.model_router import model_for
        from app.services.ai_workflow_logger import AIWorkflowLogger

        AIWorkflowLogger().log_workflow(
            workflow_type=eg.TASK,
            model=model_for(eg.TASK),
            prompt={"template_version": "1.0"},
            input_data={"parameters": {"course_id": course_id, "kind": kind}},
            response={"success": success, "error_message": error},
            metrics={"latency_ms": int((time.time() - started) * 1000), "questions": questions},
            user_id=user_id,
        )
    except Exception as e:
        logger.warning(f"Could not log exam generation: {e}")
