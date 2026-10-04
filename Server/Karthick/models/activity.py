"""
Activity model for logging user activities
"""

import logging
from datetime import datetime
from typing import Any, Dict, List, Optional

from .base import BaseModel


class Activity(BaseModel):
    """Activity model for logging user actions and events"""

    collection_name = "activities"

    def __init__(self, **kwargs):
        # Activity-specific attributes
        self.user_id: str = kwargs.get("user_id", "")
        self.activity_type: str = kwargs.get("activity_type", "")
        self.activity_data: Dict[str, Any] = kwargs.get("activity_data", {})
        self.ip_address: Optional[str] = kwargs.get("ip_address")
        self.user_agent: Optional[str] = kwargs.get("user_agent")

        # Call the base class constructor
        super().__init__(**kwargs)

    def validate(self) -> tuple[bool, str]:
        """Validate activity data"""
        # Validate required fields
        if not self.user_id:
            return False, "User ID is required"

        if not self.activity_type:
            return False, "Activity type is required"

        return True, "Valid activity data"

    def to_api_dict(self) -> Dict[str, Any]:
        """Convert to API-safe dictionary"""
        return {
            "id": self.id,
            "user_id": self.user_id,
            "activity_type": self.activity_type,
            "activity_data": self.activity_data,
            "created_at": self.created_at,
        }

    @classmethod
    def log_activity(
        cls,
        db,
        user_id: str,
        activity_type: str,
        activity_data: Dict[str, Any] = None,
        ip_address: str = None,
        user_agent: str = None,
    ):
        """Log a new activity"""
        try:
            activity = cls(
                user_id=user_id,
                activity_type=activity_type,
                activity_data=activity_data or {},
                ip_address=ip_address,
                user_agent=user_agent,
            )

            activity.save(db)
            return activity
        except Exception as e:
            logging.error(f"Error logging activity: {str(e)}")
            # Don't raise exception for activity logging failures
            return None

    @classmethod
    def get_user_activities(cls, db, user_id: str, limit: int = 50) -> List["Activity"]:
        """Get activities for a specific user"""
        try:
            activities_ref = db.collection(cls.collection_name)
            query = activities_ref.where("user_id", "==", user_id).order_by(
                "created_at", direction="DESCENDING"
            )

            if limit:
                query = query.limit(limit)

            docs = query.stream()
            activities = [cls.from_dict(doc.to_dict(), doc.id) for doc in docs]

            return activities
        except Exception as e:
            logging.error(f"Error getting user activities: {str(e)}")
            return []

