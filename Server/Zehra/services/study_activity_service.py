"""
Daily study activity and streaks.

    study_days: id "{user_id}_{yyyymmdd}", user_id, date (YYYY-MM-DD, the student's local day),
                counts{cards, practice, tasks, tutor, work}, updated_at

The local day comes from the app's `X-Timezone` header (an IANA name such as
"America/Toronto"); without it, UTC. Recording never raises: a failed write
must not break the review or submission that triggered it.
"""

import logging
from datetime import date, datetime, timedelta, timezone
from typing import Dict, Optional
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from google.cloud.firestore_v1.base_query import FieldFilter

from app.services.firebase_service import get_firestore_client

logger = logging.getLogger(__name__)

DAYS = "study_days"
KINDS = ("cards", "practice", "tasks", "tutor", "work")


def request_zone() -> timezone:
    """The student's time zone from the current request, else UTC."""
    try:
        from flask import has_request_context, request

        if has_request_context():
            name = (request.headers.get("X-Timezone") or "").strip()
            if name and len(name) <= 64:
                return ZoneInfo(name)
    except (ZoneInfoNotFoundError, ValueError):
        pass
    except Exception as e:  # pragma: no cover - defensive
        logger.debug(f"Time zone lookup failed: {e}")
    return timezone.utc


def local_today(zone=None) -> date:
    return datetime.now(zone or request_zone()).date()


def local_date(when: Optional[datetime], zone=None) -> date:
    when = when or datetime.now(timezone.utc)
    if when.tzinfo is None:
        when = when.replace(tzinfo=timezone.utc)
    return when.astimezone(zone or request_zone()).date()


def record(user_id: str, kind: str, when: Optional[datetime] = None, zone=None) -> None:
    """Count one study action on the student's local day."""
    if kind not in KINDS or not user_id:
        return
    try:
        day = local_date(when, zone)
        ref = get_firestore_client().collection(DAYS).document(f"{user_id}_{day.strftime('%Y%m%d')}")
        snap = ref.get()
        counts: Dict[str, int] = dict((snap.to_dict() or {}).get("counts") or {}) if snap.exists else {}
        counts[kind] = counts.get(kind, 0) + 1
        ref.set({"user_id": user_id, "date": day.isoformat(), "counts": counts, "updated_at": datetime.now(timezone.utc)})
    except Exception as e:
        logger.warning(f"Could not record study activity for {user_id}: {e}")


def active_dates(user_id: str) -> Dict[str, Dict[str, int]]:
    return {
        (d.to_dict() or {}).get("date"): (d.to_dict() or {}).get("counts") or {}
        for d in get_firestore_client().collection(DAYS).where(filter=FieldFilter("user_id", "==", user_id)).stream()
    }


def streak(user_id: str, today: date) -> Dict:
    """Current streak counts back from today, or from yesterday while today has no activity yet."""
    days = active_dates(user_id)
    active = {d for d in days if d}

    def run_back(start: date) -> int:
        n, d = 0, start
        while d.isoformat() in active:
            n += 1
            d -= timedelta(days=1)
        return n

    studied_today = today.isoformat() in active
    current = run_back(today) if studied_today else run_back(today - timedelta(days=1))
    best, run, prev = 0, 0, None
    for d in sorted(date.fromisoformat(x) for x in active):
        run = run + 1 if prev and d - prev == timedelta(days=1) else 1
        best = max(best, run)
        prev = d
    week = [(today - timedelta(days=i)).isoformat() for i in range(6, -1, -1)]
    return {
        "current": current,
        "best": max(best, current),
        "studied_today": studied_today,
        "today_counts": days.get(today.isoformat(), {}),
        "last_7_days": [{"date": d, "active": d in active} for d in week],
    }
