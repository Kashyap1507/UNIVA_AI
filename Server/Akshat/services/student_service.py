"""
Student profile helpers: validation, defaults and the fields every
auth/bootstrap response carries (student_profile, needs_onboarding).
"""

from datetime import datetime
from typing import Any, Dict, Tuple

from app.models.user import User

DEGREE_LEVELS = ("BACHELOR", "MASTER")
PROFILE_TEXT_FIELDS = ("university", "program")
REQUIRED_PROFILE_FIELDS = ("university", "degree_level", "program")
MAX_TEXT_LENGTH = 120
MAX_SEMESTER = 20


def validate_student_profile(data: Dict[str, Any]) -> Tuple[bool, str, Dict[str, Any]]:
    """Validate a (partial) student profile update. Returns (ok, message, cleaned)."""
    if not isinstance(data, dict):
        return False, "Profile must be an object", {}

    cleaned: Dict[str, Any] = {}

    for field in PROFILE_TEXT_FIELDS:
        if field in data:
            value = data[field]
            if value is None:
                cleaned[field] = None
                continue
            if not isinstance(value, str):
                return False, f"{field} must be a string", {}
            value = value.strip()
            if len(value) > MAX_TEXT_LENGTH:
                return False, f"{field} must be at most {MAX_TEXT_LENGTH} characters", {}
            cleaned[field] = value or None

    if "degree_level" in data:
        level = data["degree_level"]
        if level is not None:
            if not isinstance(level, str) or level.upper() not in DEGREE_LEVELS:
                return False, f"degree_level must be one of: {', '.join(DEGREE_LEVELS)}", {}
            level = level.upper()
        cleaned["degree_level"] = level

    if "current_semester" in data:
        semester = data["current_semester"]
        if semester is not None:
            if isinstance(semester, bool) or not isinstance(semester, int):
                return False, "current_semester must be a whole number", {}
            if not 1 <= semester <= MAX_SEMESTER:
                return False, f"current_semester must be between 1 and {MAX_SEMESTER}", {}
        cleaned["current_semester"] = semester

    if "enrolled_course_ids" in data:
        ids = data["enrolled_course_ids"]
        if not isinstance(ids, list) or not all(isinstance(i, str) for i in ids):
            return False, "enrolled_course_ids must be a list of strings", {}
        cleaned["enrolled_course_ids"] = ids

    return True, "Valid", cleaned


def empty_student_profile() -> Dict[str, Any]:
    return {
        "university": None,
        "degree_level": None,
        "program": None,
        "current_semester": None,
        "enrolled_course_ids": [],
    }


def get_student_profile(user: User) -> Dict[str, Any]:
    profile = empty_student_profile()
    profile.update(user.student_profile or {})
    return profile


def is_profile_complete(profile: Dict[str, Any]) -> bool:
    return all(profile.get(field) for field in REQUIRED_PROFILE_FIELDS)


def update_student_profile(db, user: User, updates: Dict[str, Any]) -> Dict[str, Any]:
    """Merge validated updates into the profile and persist it with the onboarding flag."""
    profile = get_student_profile(user)
    profile.update(updates)
    user.student_profile = profile
    user.onboarding_completed = is_profile_complete(profile)
    db.collection(User.collection_name).document(user.id).update(
        {
            "student_profile": profile,
            "onboarding_completed": user.onboarding_completed,
            "updated_at": datetime.now(),
        }
    )
    return profile


def student_payload(user: User) -> Dict[str, Any]:
    """Fields every auth/bootstrap response carries for the student."""
    return {
        "student_profile": get_student_profile(user),
        "needs_onboarding": user.needs_onboarding(),
    }
