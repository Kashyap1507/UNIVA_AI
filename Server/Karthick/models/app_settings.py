"""
App Settings model for storing user app preferences

Stores user-level app settings that sync across devices.
Settings are tied to user_id and persist in Firestore.
"""

import logging
from datetime import datetime
from typing import Any, Dict, Optional

from .base import BaseModel


class AppSettings(BaseModel):
    """App Settings model for user preferences"""

    collection_name = "app_settings"

    def __init__(self, **kwargs):
        # Settings attributes
        self.user_id: str = kwargs.get("user_id", "")
        self.cloud_sync_enabled: bool = kwargs.get("cloud_sync_enabled", False)
        self.cellular_sync_enabled: bool = kwargs.get("cellular_sync_enabled", False)
        self.scan_quality: str = kwargs.get("scan_quality", "LARGE")

        # Call the base class constructor
        super().__init__(**kwargs)

    def validate(self) -> tuple[bool, str]:
        """Validate app settings data"""
        # Validate required fields
        if not self.user_id or not self.user_id.strip():
            return False, "User ID is required"

        # Validate scan quality
        valid_qualities = ["SMALL", "STANDARD", "LARGE", "HD"]
        if self.scan_quality not in valid_qualities:
            return False, f"Scan quality must be one of: {', '.join(valid_qualities)}"

        # Validate cellular sync logic (cellular only makes sense if cloud sync is enabled)
        if self.cellular_sync_enabled and not self.cloud_sync_enabled:
            # Auto-fix: disable cellular sync if cloud sync is off
            self.cellular_sync_enabled = False
            logging.warning(
                f"Cellular sync auto-disabled for user {self.user_id} because cloud sync is off"
            )

        return True, "Valid app settings data"

    def to_api_dict(self) -> Dict[str, Any]:
        """Convert to API-safe dictionary"""

        def serialize_datetime(dt):
            """Helper to serialize datetime objects"""
            if dt is None:
                return None
            try:
                from google.cloud.firestore_v1._helpers import DatetimeWithNanoseconds

                if isinstance(dt, DatetimeWithNanoseconds):
                    return dt.isoformat()
            except ImportError:
                pass
            if isinstance(dt, datetime):
                return dt.isoformat()
            return dt

        return {
            "cloud_sync_enabled": self.cloud_sync_enabled,
            "cellular_sync_enabled": self.cellular_sync_enabled,
            "scan_quality": self.scan_quality,
            "created_at": serialize_datetime(self.created_at),
            "updated_at": serialize_datetime(self.updated_at),
        }

    @classmethod
    def get_by_user_id(cls, db, user_id: str) -> Optional["AppSettings"]:
        """Get app settings by user ID (document ID = user_id)"""
        try:
            doc_ref = db.collection(cls.collection_name).document(user_id)
            doc = doc_ref.get()

            if doc.exists:
                data = doc.to_dict()
                data["user_id"] = user_id  # Ensure user_id is set
                return cls.from_dict(data, user_id)
            return None
        except Exception as e:
            logging.error(f"Error getting app settings for user {user_id}: {str(e)}")
            return None

    @classmethod
    def create_default(cls, db, user_id: str) -> "AppSettings":
        """Create default app settings for a user"""
        try:
            # Create default settings
            settings = cls(
                user_id=user_id,
                cloud_sync_enabled=True,  # Default: ON for cloud storage (Firebase Storage)
                cellular_sync_enabled=False,  # Default: OFF to save data
                scan_quality="LARGE",  # Default: high quality
            )

            # Save to Firestore using user_id as document ID
            from google.cloud.firestore import SERVER_TIMESTAMP

            data = {
                "user_id": user_id,
                "cloud_sync_enabled": settings.cloud_sync_enabled,
                "cellular_sync_enabled": settings.cellular_sync_enabled,
                "scan_quality": settings.scan_quality,
                "created_at": SERVER_TIMESTAMP,
                "updated_at": SERVER_TIMESTAMP,
            }

            doc_ref = db.collection(cls.collection_name).document(user_id)
            doc_ref.set(data)
            settings.id = user_id

            logging.info(f"Created default app settings for user {user_id}")
            return settings
        except Exception as e:
            logging.error(f"Error creating default app settings: {str(e)}")
            raise

    def save(self, db):
        """Save app settings to Firestore (override to use user_id as doc ID)"""
        try:
            # Validate before saving
            is_valid, error_message = self.validate()
            if not is_valid:
                raise ValueError(error_message)

            from google.cloud.firestore import SERVER_TIMESTAMP

            data = {
                "user_id": self.user_id,
                "cloud_sync_enabled": self.cloud_sync_enabled,
                "cellular_sync_enabled": self.cellular_sync_enabled,
                "scan_quality": self.scan_quality,
                "updated_at": SERVER_TIMESTAMP,
            }

            # Use user_id as document ID
            doc_ref = db.collection(self.collection_name).document(self.user_id)
            doc = doc_ref.get()

            if doc.exists:
                # Update existing document
                doc_ref.update(data)
            else:
                # Create new document
                data["created_at"] = SERVER_TIMESTAMP
                doc_ref.set(data)

            self.id = self.user_id
            return True
        except Exception as e:
            logging.error(f"Error saving app settings: {str(e)}")
            raise
