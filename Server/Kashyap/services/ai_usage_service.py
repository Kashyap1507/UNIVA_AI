"""
Daily AI usage per student, for the admin cost pages.

    ai_usage_daily: id "{user_id}_{yyyymmdd}" (UTC day); user_id, date "YYYY-MM-DD",
                    calls, tokens_in, tokens_out, cost_usd,
                    by_task{task: {calls, tokens_in, tokens_out, cost_usd}}, updated_at

Written with atomic increments from the AI workflow logger, so concurrent
workflows for the same student and day add up correctly.
"""

import logging
from datetime import datetime, timezone
from typing import Optional

from google.cloud.firestore_v1 import Increment

from app.services.firebase_service import get_firestore_client

logger = logging.getLogger(__name__)

COLLECTION = "ai_usage_daily"


def doc_id(user_id: str, when: datetime) -> str:
    return f"{user_id}_{when.strftime('%Y%m%d')}"


def record(
    user_id: Optional[str],
    task: str,
    tokens_in: int,
    tokens_out: int,
    cost_usd: float,
    when: Optional[datetime] = None,
    db=None,
) -> None:
    """Add one workflow run to the student's usage for the (UTC) day. Never raises."""
    if not user_id:
        return
    try:
        when = when or datetime.now(timezone.utc)
        counts = {
            "calls": Increment(1),
            "tokens_in": Increment(int(tokens_in or 0)),
            "tokens_out": Increment(int(tokens_out or 0)),
            "cost_usd": Increment(float(cost_usd or 0.0)),
        }
        task_key = (task or "other").replace(".", "_")
        (db or get_firestore_client()).collection(COLLECTION).document(doc_id(user_id, when)).set(
            {
                "user_id": user_id,
                "date": when.strftime("%Y-%m-%d"),
                **counts,
                "by_task": {task_key: dict(counts)},
                "updated_at": when,
            },
            merge=True,
        )
    except Exception as e:
        logger.error(f"Failed to record AI usage for {user_id}: {e}")
