"""
Exam countdown study plans.

    study_plans: user_id, course_id, deadline_id, exam_date, title, minutes_per_day,
                 days[{date, tasks[{id, type, ref_id, title, minutes, done}]}],
                 is_deleted, created_at, updated_at

The planner is deterministic (no model call): it spreads the course's concepts
over the days before the exam, weakest first (by practice mastery), adds a
short flashcard review each day when the course has cards, a practice quiz
every few days, a mock exam two to three days before the exam and a light
review on the last day. A new plan for the same deadline replaces the old one.

Task types: LEARN (concept or module), REVIEW_CARDS, PRACTICE, MOCK_EXAM, REVIEW.
"""

from datetime import date, datetime, timedelta, timezone
from typing import Dict, List, Optional

from google.cloud.firestore_v1.base_query import FieldFilter

from app.services import course_service, deadline_service
from app.services.course_service import ServiceError
from app.services.firebase_service import get_firestore_client

PLANS = "study_plans"
MAX_DAYS = 60
MIN_MINUTES, MAX_MINUTES, DEFAULT_MINUTES = 15, 480, 60
LEARN_MINUTES = 25
PRACTICE_MINUTES = 30
CARD_MINUTES = 15
PRACTICE_EVERY = 3


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(value):
    return value.isoformat() if hasattr(value, "isoformat") else value


def _db():
    return get_firestore_client()


# --- planning (pure) --------------------------------------------------------------------------------


def build_days(
    start: date,
    exam: date,
    minutes_per_day: int,
    units: List[Dict],
    has_cards: bool,
    course_name: str,
    course_id: str,
) -> List[Dict]:
    """units: [{"ref_id", "title"}] weakest first. Returns [{date, tasks[]}] for start..exam-1."""
    first = max(start, exam - timedelta(days=MAX_DAYS))
    dates = [first + timedelta(days=i) for i in range((exam - first).days)]
    if not dates:
        return []
    n = len(dates)
    mock_index = n - 3 if n >= 5 else (n - 2 if n >= 3 else None)
    days: List[Dict] = []
    # Every unit once (weakest first), then the weakest half again
    queue = list(units) + list(units[: max(1, (len(units) + 1) // 2)]) if units else []
    learned = 0
    for i, d in enumerate(dates):
        tasks: List[Dict] = []
        budget = minutes_per_day

        def add(kind: str, title: str, minutes: int, ref_id: Optional[str]):
            tasks.append({"id": f"d{i + 1}-t{len(tasks) + 1}", "type": kind, "ref_id": ref_id, "title": title, "minutes": minutes, "done": False})

        if has_cards:
            cards = min(CARD_MINUTES, max(5, budget // 4))
            add("REVIEW_CARDS", "Review due flashcards", cards, course_id)
            budget -= cards
        if i == n - 1 and n > 1:
            weakest = ", ".join(u["title"] for u in units[:3]) or "your notes"
            add("REVIEW", f"Light review: {weakest}. Sleep well.", max(15, min(budget, minutes_per_day // 2)), course_id)
        elif mock_index is not None and i == mock_index:
            add("MOCK_EXAM", f"Mock exam: {course_name}", max(45, budget), course_id)
        elif mock_index is not None and i == mock_index + 1:
            add("REVIEW", "Go through your mock exam mistakes", min(budget, 45), course_id)
            budget -= min(budget, 45)
            while budget >= LEARN_MINUTES and units:
                u = units[learned % len(units)]
                add("LEARN", f"Revise: {u['title']}", LEARN_MINUTES, u["ref_id"])
                learned += 1
                budget -= LEARN_MINUTES
        else:
            if i % PRACTICE_EVERY == PRACTICE_EVERY - 1 and budget >= PRACTICE_MINUTES:
                add("PRACTICE", "Practice quiz on what you've covered", PRACTICE_MINUTES, course_id)
                budget -= PRACTICE_MINUTES
            if not units:
                if budget >= 15:
                    add("LEARN", f"Work through your {course_name} materials", budget, course_id)
            else:
                while budget >= LEARN_MINUTES:
                    u = queue[learned % len(queue)] if queue else units[0]
                    verb = "Study" if learned < len(units) else "Revise"
                    add("LEARN", f"{verb}: {u['title']}", LEARN_MINUTES, u["ref_id"])
                    learned += 1
                    budget -= LEARN_MINUTES
                if not any(t["type"] == "LEARN" for t in tasks) and budget >= 10:
                    u = queue[learned % len(queue)]
                    add("LEARN", f"Study: {u['title']}", budget, u["ref_id"])
                    learned += 1
        days.append({"date": d.isoformat(), "tasks": tasks})
    return days


def _units(user_id: str, course_id: str) -> List[Dict]:
    """Concepts weakest first (never practised before practised), else modules in order."""
    concepts = course_service.list_concepts(user_id, course_id)
    if concepts:
        modules = {m.id: m.order for m in course_service.list_modules(user_id, course_id)}
        concepts.sort(key=lambda c: (c.attempts > 0, c.mastery or 0.0, modules.get(c.module_id, 999), c.name.lower()))
        return [{"ref_id": c.id, "title": c.name} for c in concepts]
    return [{"ref_id": m.id, "title": m.title} for m in course_service.list_modules(user_id, course_id)]


def _has_cards(user_id: str, course_id: str) -> bool:
    for d in _db().collection("flashcards").where(filter=FieldFilter("user_id", "==", user_id)).where(filter=FieldFilter("course_id", "==", course_id)).stream():
        data = d.to_dict() or {}
        if not data.get("is_deleted") and not data.get("suspended"):
            return True
    return False


# --- API-facing ---------------------------------------------------------------------------------------


def plan_response(plan_id: str, data: Dict, include_days: bool = True) -> Dict:
    tasks = [t for d in data.get("days") or [] for t in d.get("tasks") or []]
    out = {
        "id": plan_id,
        "course_id": data.get("course_id"),
        "deadline_id": data.get("deadline_id"),
        "exam_date": data.get("exam_date"),
        "title": data.get("title"),
        "minutes_per_day": data.get("minutes_per_day"),
        "tasks_total": len(tasks),
        "tasks_done": sum(1 for t in tasks if t.get("done")),
        "start_date": (data.get("days") or [{}])[0].get("date"),
        "created_at": _iso(data.get("created_at")),
    }
    if include_days:
        out["days"] = data.get("days") or []
    return out


def generate(user_id: str, data: Dict) -> Dict:
    minutes = data.get("minutes_per_day", DEFAULT_MINUTES)
    if not isinstance(minutes, int) or isinstance(minutes, bool) or not MIN_MINUTES <= minutes <= MAX_MINUTES:
        raise ServiceError(f"minutes_per_day must be between {MIN_MINUTES} and {MAX_MINUTES}")
    start_value = data.get("start_date")
    if start_value:
        due, all_day = deadline_service.parse_due(start_value)
        if not all_day:
            raise ServiceError("start_date must be a date (YYYY-MM-DD)")
        start = date.fromisoformat(due)
    else:
        start = _now().date()

    deadline_id = data.get("deadline_id")
    if deadline_id:
        deadline = deadline_service.get_deadline(user_id, deadline_id)
        if not deadline["course_id"]:
            raise ServiceError("Link this deadline to a course first")
        if not deadline["due_at"]:
            raise ServiceError("This deadline has no date")
        course_id, exam = deadline["course_id"], date.fromisoformat(deadline["due_at"][:10])
        title = f"Countdown to {deadline['title']}"
    else:
        course_id = data.get("course_id")
        if not course_id:
            raise ServiceError("Give a deadline_id, or a course_id and exam_date")
        due, _ = deadline_service.parse_due(data.get("exam_date"))
        exam = date.fromisoformat(due[:10])
        title = None
    course = course_service.get_course(user_id, course_id)
    if exam <= start:
        raise ServiceError("The exam date must be after the start date")
    title = title or f"Countdown to the {course.code or course.name} exam"

    days = build_days(start, exam, minutes, _units(user_id, course_id), _has_cards(user_id, course_id), course.code or course.name, course_id)
    now = _now()
    # Replace an earlier plan for the same deadline (or course + exam date)
    for d in _db().collection(PLANS).where(filter=FieldFilter("user_id", "==", user_id)).where(filter=FieldFilter("course_id", "==", course_id)).stream():
        old = d.to_dict() or {}
        same = (deadline_id and old.get("deadline_id") == deadline_id) or (not deadline_id and not old.get("deadline_id") and old.get("exam_date") == exam.isoformat())
        if same and not old.get("is_deleted"):
            d.reference.update({"is_deleted": True, "updated_at": now})
    doc = {
        "user_id": user_id,
        "course_id": course_id,
        "deadline_id": deadline_id,
        "exam_date": exam.isoformat(),
        "title": title[:200],
        "minutes_per_day": minutes,
        "days": days,
        "is_deleted": False,
        "created_at": now,
        "updated_at": now,
    }
    ref = _db().collection(PLANS).document()
    ref.set(doc)
    return plan_response(ref.id, doc)


def _get(user_id: str, plan_id: str):
    if not plan_id or not isinstance(plan_id, str):
        raise ServiceError("Study plan not found", 404)
    ref = _db().collection(PLANS).document(plan_id)
    snap = ref.get()
    data = snap.to_dict() if snap.exists else None
    if not data or data.get("user_id") != user_id or data.get("is_deleted"):
        raise ServiceError("Study plan not found", 404)
    return ref, data


def get_plan(user_id: str, plan_id: str) -> Dict:
    return plan_response(plan_id, _get(user_id, plan_id)[1])


def list_plans(user_id: str, course_id: Optional[str] = None) -> List[Dict]:
    if course_id:
        course_service.get_course(user_id, course_id)
    active = {c.id for c in course_service.list_courses(user_id)}
    rows = []
    for d in _db().collection(PLANS).where(filter=FieldFilter("user_id", "==", user_id)).stream():
        data = d.to_dict() or {}
        if data.get("is_deleted") or data.get("course_id") not in active or (course_id and data.get("course_id") != course_id):
            continue
        rows.append(plan_response(d.id, data, include_days=False))
    rows.sort(key=lambda p: p["exam_date"] or "")
    return rows


def set_task_done(user_id: str, plan_id: str, task_id: str, done) -> Dict:
    if not isinstance(done, bool):
        raise ServiceError("done must be true or false")
    ref, data = _get(user_id, plan_id)
    days = data.get("days") or []
    found = False
    for day in days:
        for task in day.get("tasks") or []:
            if task.get("id") == task_id:
                task["done"] = done
                found = True
    if not found:
        raise ServiceError("Task not found", 404)
    ref.update({"days": days, "updated_at": _now()})
    if done:
        from app.services import study_activity_service

        study_activity_service.record(user_id, "tasks")
    data["days"] = days
    return plan_response(plan_id, data)


def delete_plan(user_id: str, plan_id: str) -> None:
    ref, _ = _get(user_id, plan_id)
    ref.update({"is_deleted": True, "updated_at": _now()})


def today_tasks(user_id: str, today: date) -> List[Dict]:
    """Tasks scheduled for `today` across active plans (for Home / Today)."""
    out = []
    for d in _db().collection(PLANS).where(filter=FieldFilter("user_id", "==", user_id)).stream():
        data = d.to_dict() or {}
        if data.get("is_deleted"):
            continue
        for day in data.get("days") or []:
            if day.get("date") == today.isoformat():
                out += [{**t, "plan_id": d.id, "course_id": data.get("course_id"), "plan_title": data.get("title")} for t in day.get("tasks") or []]
    return out
