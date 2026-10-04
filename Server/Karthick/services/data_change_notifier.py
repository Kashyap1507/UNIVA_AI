"""
Data change notifications
Push messages that tell the student's devices which data changed so the app
can refresh only that (and, for some changes, show a short notification).

Change types (handled by the app's RealTimeDataSyncManager):
document_added, document_updated, document_deleted, material_ready,
course_updated, deadline_changed, profile_updated, settings_updated
"""

import logging
from datetime import datetime
from typing import Any, Dict, List, Optional

from app.services.fcm_service import FCMService

logger = logging.getLogger(__name__)

# Visible messages; anything not listed is a silent data sync
_MESSAGES = {
    "document_added": ("Scan saved", "Your scan is ready for Check my work."),
    "document_deleted": ("Scan removed", "A scan was deleted on another device."),
    "material_ready": ("Course material ready", "{title} is ready for the tutor, cards and practice."),
    "deadline_changed": ("Deadlines updated", "Your deadlines changed."),
}


def notify_data_change(
    user_id: str,
    change_type: str,
    data: Optional[Dict[str, Any]] = None,
    silent: bool = True,
):
    """
    Tell the student's devices that data changed.

    Args:
        user_id: The student's user id
        change_type: One of the types listed in the module docstring
        data: Extra fields for the app (document_id, course_id, ...)
        silent: True sends a data-only message (no visible notification)
    """
    try:
        data = dict(data or {})
        data.setdefault("timestamp", datetime.utcnow().isoformat() + "Z")

        title, body = ("", "")
        if not silent and change_type in _MESSAGES:
            title, template = _MESSAGES[change_type]
            body = template.format(title=data.get("title") or "A file")

        notification = {"title": title, "body": body, "type": change_type, "data": data}
        logger.info(f"Sending {change_type} to user {user_id} (silent={silent})")
        FCMService.send_to_user(user_id, notification, silent=silent)
    except Exception as e:
        # Never fail the main operation because of a push
        logger.error(f"Notification failed for {change_type}: {str(e)}")


def notify_batch_changes(user_id: str, changes: List[Dict[str, Any]]):
    """Send several silent data-change messages."""
    for change in changes:
        notify_data_change(user_id, change.get("type", "unknown"), change.get("data"), silent=True)


def test_notification(user_id: str):
    """Visible test notification (FCM debugging)."""
    try:
        FCMService.send_to_user(
            user_id,
            {
                "title": "Univa AI",
                "body": "Notifications are working.",
                "type": "test",
                "data": {"timestamp": datetime.utcnow().isoformat() + "Z"},
            },
            silent=False,
        )
        logger.info(f"Test notification sent to user {user_id}")
    except Exception as e:
        logger.error(f"Test notification failed: {e}")
