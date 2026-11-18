"""
Grade tracker: marks the student has received, per course, and a GPA estimate.

    grade_entries: user_id, course_id, name, score, max, weight_pct, deadline_id?,
                   is_deleted, created_at, updated_at

Per course: current % = Σ(score/max × weight) / Σ weight over graded items;
remaining weight = 100 − Σ weight. GPA uses the common Canadian 4.0 table on
each course's current %, weighted by course credits (default 3).
"""

from datetime import datetime, timezone
from typing import Dict, List, Optional, Tuple

from google.cloud.firestore_v1.base_query import FieldFilter

from app.services import course_service
from app.services.course_service import ServiceError
from app.services.firebase_service import get_firestore_client

ENTRIES = "grade_entries"
DEFAULT_CREDITS = 3.0

# (minimum %, grade point, letter)
GPA_TABLE: List[Tuple[float, float, str]] = [
    (90, 4.0, "A+"), (85, 3.9, "A"), (80, 3.7, "A-"), (77, 3.3, "B+"), (73, 3.0, "B"), (70, 2.7, "B-"),
    (67, 2.3, "C+"), (63, 2.0, "C"), (60, 1.7, "C-"), (57, 1.3, "D+"), (53, 1.0, "D"), (50, 0.7, "D-"), (0, 0.0, "F"),
]


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(value):
    return value.isoformat() if hasattr(value, "isoformat") else value


def _db():
    return get_firestore_client()


def grade_point(pct: float) -> Tuple[float, str]:
    for minimum, point, letter in GPA_TABLE:
        if pct >= minimum - 1e-9:
            return point, letter
    return 0.0, "F"


def entry_response(entry_id: str, d: Dict) -> Dict:
    return {
        "id": entry_id,
        "course_id": d.get("course_id"),
        "name": d.get("name"),
        "score": d.get("score"),
        "max": d.get("max"),
        "weight_pct": d.get("weight_pct"),
        "deadline_id": d.get("deadline_id"),
        "percent": round(100 * d["score"] / d["max"], 1) if d.get("max") else None,
        "created_at": _iso(d.get("created_at")),
    }


def _number(data: Dict, field: str, low: float, high: float, low_inclusive: bool = True) -> float:
    v = data.get(field)
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        raise ServiceError(f"{field} must be a number")
    if (v < low if low_inclusive else v <= low) or v > high:
        raise ServiceError(f"{field} must be between {low} and {high}")
    return float(v)


def _fields(user_id: str, data: Dict, creating: bool, current: Optional[Dict] = None) -> Dict:
    out: Dict = {}
    if creating or "course_id" in data:
        course_service.get_course(user_id, data.get("course_id"))
        out["course_id"] = data["course_id"]
    if creating or "name" in data:
        out["name"] = course_service._text(data, "name", 120, required=True)
    if creating or "max" in data:
        out["max"] = _number(data, "max", 0, 100000, low_inclusive=False)
    if creating or "score" in data:
        out["score"] = _number(data, "score", 0, 100000)
    if creating or "weight_pct" in data:
        out["weight_pct"] = _number(data, "weight_pct", 0, 100)
    if "deadline_id" in data:
        out["deadline_id"] = data["deadline_id"] or None
    score = out.get("score", (current or {}).get("score"))
    maximum = out.get("max", (current or {}).get("max"))
    if score is not None and maximum and score > maximum * 1.5:
        raise ServiceError("score can't be more than 1.5 × max (bonus marks included)")
    return out


def _entries(user_id: str) -> List[Tuple[str, Dict]]:
    rows = [(d.id, d.to_dict() or {}) for d in _db().collection(ENTRIES).where(filter=FieldFilter("user_id", "==", user_id)).stream()]
    return [r for r in rows if not r[1].get("is_deleted")]


def _get(user_id: str, entry_id: str):
    if not entry_id or not isinstance(entry_id, str):
        raise ServiceError("Grade not found", 404)
    ref = _db().collection(ENTRIES).document(entry_id)
    snap = ref.get()
    d = snap.to_dict() if snap.exists else None
    if not d or d.get("user_id") != user_id or d.get("is_deleted"):
        raise ServiceError("Grade not found", 404)
    return ref, d


def create_entry(user_id: str, data: Dict) -> Dict:
    fields = _fields(user_id, data, creating=True)
    now = _now()
    doc = {"user_id": user_id, "deadline_id": None, **fields, "is_deleted": False, "created_at": now, "updated_at": now}
    ref = _db().collection(ENTRIES).document()
    ref.set(doc)
    return entry_response(ref.id, doc)


def update_entry(user_id: str, entry_id: str, data: Dict) -> Dict:
    ref, d = _get(user_id, entry_id)
    fields = _fields(user_id, data, creating=False, current=d)
    if not fields:
        raise ServiceError("Nothing to update")
    fields["updated_at"] = _now()
    ref.update(fields)
    d.update(fields)
    return entry_response(entry_id, d)


def delete_entry(user_id: str, entry_id: str) -> None:
    ref, _ = _get(user_id, entry_id)
    ref.update({"is_deleted": True, "updated_at": _now()})


def course_summary(entries: List[Dict], credits: Optional[float]) -> Dict:
    weight = sum(e["weight_pct"] for e in entries)
    earned = sum(e["score"] / e["max"] * e["weight_pct"] for e in entries if e["max"])
    current = round(100 * earned / weight, 1) if weight > 0 else None
    point, letter = grade_point(current) if current is not None else (None, None)
    return {
        "graded_weight": round(weight, 2),
        "remaining_weight": round(max(0.0, 100 - weight), 2),
        "earned_weight": round(earned, 2),
        "current_pct": current,
        "grade_point": point,
        "letter": letter,
        "credits": credits if credits is not None else DEFAULT_CREDITS,
    }


def needed_on_remaining(summary: Dict, target_pct: float) -> Optional[float]:
    """Average % needed on the ungraded weight to finish at `target_pct` (None if nothing is left)."""
    remaining = summary["remaining_weight"]
    if remaining <= 0:
        return None
    return round(100 * (target_pct - summary["earned_weight"]) / remaining, 1)


def overview(user_id: str, course_id: Optional[str] = None) -> Dict:
    courses = {c.id: c for c in course_service.list_courses(user_id)}
    if course_id:
        course_service.get_course(user_id, course_id)
    by_course: Dict[str, List[Dict]] = {}
    for i, d in _entries(user_id):
        if d.get("course_id") in courses and (not course_id or d["course_id"] == course_id):
            by_course.setdefault(d["course_id"], []).append(entry_response(i, d))
    out_courses = []
    total_points = total_credits = 0.0
    for cid, course in courses.items():
        if course_id and cid != course_id:
            continue
        entries = sorted(by_course.get(cid, []), key=lambda e: e["created_at"] or "")
        summary = course_summary(entries, course.credits)
        if summary["grade_point"] is not None:
            total_points += summary["grade_point"] * summary["credits"]
            total_credits += summary["credits"]
        out_courses.append({"course_id": cid, "course_name": course.name, "course_code": course.code, **summary, "entries": entries})
    out_courses.sort(key=lambda c: (c["course_code"] or c["course_name"]).lower())
    return {
        "courses": out_courses,
        "gpa": round(total_points / total_credits, 2) if total_credits else None,
        "gpa_credits": total_credits,
    }


def current_pct(user_id: str) -> Dict[str, Optional[float]]:
    """course_id → current % (for the progress overview)."""
    return {c["course_id"]: c["current_pct"] for c in overview(user_id)["courses"]}
