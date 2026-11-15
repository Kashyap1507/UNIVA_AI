"""
Course progress and the Today screen.

Progress aggregates what the other features store: concept mastery (practice),
flashcards and their reviews, practice attempts, deadlines and the grade
tracker. Today combines the streak, due cards, deadlines, today's plan tasks
and one focus suggestion.
"""

from datetime import date, datetime, timedelta, timezone
from typing import Dict, List, Optional

from google.cloud.firestore_v1.base_query import FieldFilter

from app.services import (
    course_service,
    deadline_service,
    exam_plan_service,
    flashcard_service,
    grade_tracker_service,
    study_activity_service,
)
from app.services.course_service import ServiceError
from app.services.firebase_service import get_firestore_client

MATURE_DAYS = 21
REVIEW_WINDOW_DAYS = 14


def _db():
    return get_firestore_client()


def _by_user(collection: str, user_id: str) -> List[Dict]:
    rows = [{"id": d.id, **(d.to_dict() or {})} for d in _db().collection(collection).where(filter=FieldFilter("user_id", "==", user_id)).stream()]
    return [r for r in rows if not r.get("is_deleted")]


def _recent_reviews(user_id: str, since: datetime) -> List[Dict]:
    q = _db().collection(flashcard_service.REVIEWS).where(filter=FieldFilter("user_id", "==", user_id)).where(filter=FieldFilter("reviewed_at", ">=", since))
    return [d.to_dict() or {} for d in q.stream()]


def _card_stats(cards: List[Dict], now: datetime) -> Dict:
    active = [c for c in cards if not c.get("suspended")]
    due = 0
    for c in active:
        at = (c.get("srs") or {}).get("due_at")
        if at is None or (at.tzinfo is None and at.replace(tzinfo=timezone.utc) <= now) or (at.tzinfo is not None and at <= now):
            due += 1
    mature = sum(1 for c in active if (c.get("srs") or {}).get("interval_days", 0) >= MATURE_DAYS)
    return {"total": len(active), "due": due, "mature": mature}


def _concept_stats(concepts: List[Dict]) -> Dict:
    practised = [c for c in concepts if (c.get("attempts") or 0) > 0]
    avg = round(sum(c.get("mastery") or 0 for c in practised) / len(practised), 3) if practised else None
    return {"total": len(concepts), "practised": len(practised), "mastery_avg": avg}


def _practice_stats(attempts: List[Dict]) -> Dict:
    scored = [a for a in attempts if a.get("status") == "GRADED" and a.get("score_pct") is not None]
    scored.sort(key=lambda a: a.get("finished_at") or datetime.min.replace(tzinfo=timezone.utc))
    return {
        "attempts": len(scored),
        "average_pct": round(sum(a["score_pct"] for a in scored) / len(scored), 1) if scored else None,
        "last_pct": scored[-1]["score_pct"] if scored else None,
    }


def courses_overview(user_id: str, today: date) -> List[Dict]:
    now = datetime.now(timezone.utc)
    courses = course_service.list_courses(user_id)
    concepts = _by_user("course_concepts", user_id)
    cards = _by_user(flashcard_service.CARDS, user_id)
    attempts = _by_user("practice_attempts", user_id)
    reviews = _recent_reviews(user_id, now - timedelta(days=7))
    deadlines = deadline_service.list_deadlines(user_id, include_completed=False)
    grades = grade_tracker_service.current_pct(user_id)
    out = []
    for c in courses:
        upcoming = [d for d in deadlines if d["course_id"] == c.id and d["due_at"] and d["due_at"][:10] >= today.isoformat()]
        out.append(
            {
                "course_id": c.id,
                "name": c.name,
                "code": c.code,
                "color": c.color,
                "status": c.status,
                "concepts": _concept_stats([x for x in concepts if x.get("course_id") == c.id]),
                "cards": _card_stats([x for x in cards if x.get("course_id") == c.id], now),
                "reviews_7d": sum(1 for r in reviews if r.get("course_id") == c.id),
                "practice": _practice_stats([a for a in attempts if a.get("course_id") == c.id]),
                "next_deadline": upcoming[0] if upcoming else None,
                "grade_pct": grades.get(c.id),
            }
        )
    return out


def course_detail(user_id: str, course_id: str, today: date) -> Dict:
    course = course_service.get_course(user_id, course_id)
    now = datetime.now(timezone.utc)
    concepts = [x for x in _by_user("course_concepts", user_id) if x.get("course_id") == course_id]
    concepts.sort(key=lambda c: ((c.get("attempts") or 0) == 0, c.get("mastery") or 0.0, c.get("name", "").lower()))
    attempts = [a for a in _by_user("practice_attempts", user_id) if a.get("course_id") == course_id and a.get("status") == "GRADED"]
    attempts.sort(key=lambda a: a.get("finished_at") or datetime.min.replace(tzinfo=timezone.utc))
    since = now - timedelta(days=REVIEW_WINDOW_DAYS)
    zone = study_activity_service.request_zone()
    per_day: Dict[str, int] = {}
    for r in _recent_reviews(user_id, since):
        if r.get("course_id") == course_id and r.get("reviewed_at"):
            day = study_activity_service.local_date(r["reviewed_at"], zone).isoformat()
            per_day[day] = per_day.get(day, 0) + 1
    days = [(today - timedelta(days=i)).isoformat() for i in range(REVIEW_WINDOW_DAYS - 1, -1, -1)]
    grades = grade_tracker_service.overview(user_id, course_id)["courses"]
    return {
        "course_id": course_id,
        "name": course.name,
        "code": course.code,
        "concepts": [
            {"id": c["id"], "name": c.get("name"), "mastery": c.get("mastery") or 0.0, "attempts": c.get("attempts") or 0, "practised": (c.get("attempts") or 0) > 0}
            for c in concepts
        ],
        "concept_stats": _concept_stats(concepts),
        "cards": _card_stats([x for x in _by_user(flashcard_service.CARDS, user_id) if x.get("course_id") == course_id], now),
        "reviews_per_day": [{"date": d, "count": per_day.get(d, 0)} for d in days],
        "practice": _practice_stats(attempts),
        "practice_history": [
            {"attempt_id": a["id"], "title": a.get("title"), "kind": a.get("kind"), "score_pct": a.get("score_pct"), "finished_at": a["finished_at"].isoformat() if hasattr(a.get("finished_at"), "isoformat") else a.get("finished_at")}
            for a in attempts[-20:]
        ],
        "grades": grades[0] if grades else None,
    }


def _focus(user_id: str, today: date, deadlines: List[Dict]) -> Optional[Dict]:
    """Weakest practised concept; if nothing is practised, an unpractised concept in the course with the nearest exam."""
    courses = {c.id: c for c in course_service.list_courses(user_id)}
    concepts = [c for c in _by_user("course_concepts", user_id) if c.get("course_id") in courses]
    practised = [c for c in concepts if (c.get("attempts") or 0) > 0]
    if practised:
        c = min(practised, key=lambda x: (x.get("mastery") or 0.0, x.get("name", "")))
        if (c.get("mastery") or 0) < 0.8:
            return {"concept_id": c["id"], "concept_name": c.get("name"), "course_id": c["course_id"], "mastery": c.get("mastery") or 0.0, "reason": "WEAKEST"}
    exams = [d for d in deadlines if d["type"] == "EXAM" and d["course_id"] and d["due_at"] and d["due_at"][:10] >= today.isoformat()]
    for exam in exams:
        fresh = [c for c in concepts if c.get("course_id") == exam["course_id"] and not (c.get("attempts") or 0)]
        if fresh:
            c = sorted(fresh, key=lambda x: x.get("name", ""))[0]
            return {"concept_id": c["id"], "concept_name": c.get("name"), "course_id": c["course_id"], "mastery": 0.0, "reason": "NOT_PRACTISED", "exam_title": exam["title"]}
    return None


def today(user_id: str, today_value: Optional[str] = None) -> Dict:
    if today_value:
        due, all_day = deadline_service.parse_due(today_value)
        if not all_day:
            raise ServiceError("date must be YYYY-MM-DD")
        day = date.fromisoformat(due)
    else:
        day = study_activity_service.local_today()
    deadlines = deadline_service.list_deadlines(user_id, include_completed=False)
    week_end = (day + timedelta(days=7)).isoformat()
    overdue = [d for d in deadlines if d["due_at"] and d["due_at"][:10] < day.isoformat()]
    upcoming = [d for d in deadlines if d["due_at"] and day.isoformat() <= d["due_at"][:10] <= week_end]
    courses = {c.id: {"name": c.name, "code": c.code, "color": c.color} for c in course_service.list_courses(user_id)}
    tasks = [t for t in exam_plan_service.today_tasks(user_id, day) if t.get("course_id") in courses]
    return {
        "date": day.isoformat(),
        "streak": study_activity_service.streak(user_id, day),
        "cards": flashcard_service.summary(user_id),
        "overdue": overdue,
        "upcoming": upcoming,
        "plan_tasks": tasks,
        "focus": _focus(user_id, day, deadlines),
        "courses": courses,
    }
