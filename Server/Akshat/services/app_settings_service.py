"""
App Settings Service - High-level app settings operations

Handles business logic for app settings CRUD operations.
"""

import logging
from typing import Any, Dict

from app.models.app_settings import AppSettings
from app.services.firebase_service import get_firestore_client


class AppSettingsService:
    """Service for app settings operations"""

    def __init__(self):
        self.db = get_firestore_client()

    def get_user_settings(self, user_id: str) -> Dict[str, Any]:
        """
        Get app settings for a user

        If settings don't exist, creates default settings.

        Args:
            user_id: User ID

        Returns:
            Dict with success status and settings data
        """
        try:
            # Try to get existing settings
            settings = AppSettings.get_by_user_id(self.db, user_id)

            # If no settings exist, create defaults
            if not settings:
                logging.info(f"No settings found for user {user_id}, creating defaults")
                settings = AppSettings.create_default(self.db, user_id)

            return {"success": True, "settings": settings.to_api_dict()}
        except Exception as e:
            logging.error(f"Error getting user settings: {str(e)}")
            raise

    def update_settings(
        self, user_id: str, settings_data: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Update app settings for a user

        Args:
            user_id: User ID
            settings_data: Dictionary with settings to update

        Returns:
            Dict with success status and updated settings
        """
        try:
            # Get existing settings or create new
            settings = AppSettings.get_by_user_id(self.db, user_id)

            if not settings:
                # Create new settings with provided data
                logging.info(f"Creating new settings for user {user_id}")
                settings = AppSettings(user_id=user_id)

            # Update settings attributes
            if "cloud_sync_enabled" in settings_data:
                settings.cloud_sync_enabled = bool(settings_data["cloud_sync_enabled"])

            if "cellular_sync_enabled" in settings_data:
                settings.cellular_sync_enabled = bool(
                    settings_data["cellular_sync_enabled"]
                )

            if "scan_quality" in settings_data:
                settings.scan_quality = settings_data["scan_quality"].upper()

            # Validate and save
            is_valid, error_message = settings.validate()
            if not is_valid:
                return {"success": False, "message": error_message}

            settings.save(self.db)

            logging.info(
                f"Updated settings for user {user_id}: "
                f"cloud_sync={settings.cloud_sync_enabled}, "
                f"cellular_sync={settings.cellular_sync_enabled}, "
                f"scan_quality={settings.scan_quality}"
            )

            return {
                "success": True,
                "message": "Settings updated successfully",
                "settings": settings.to_api_dict(),
            }
        except Exception as e:
            logging.error(f"Error updating settings: {str(e)}")
            raise

    def create_default_settings(self, user_id: str) -> Dict[str, Any]:
        """
        Create default settings for a new user

        Args:
            user_id: User ID

        Returns:
            Dict with success status and created settings
        """
        try:
            # Check if settings already exist
            existing_settings = AppSettings.get_by_user_id(self.db, user_id)

            if existing_settings:
                logging.info(
                    f"Settings already exist for user {user_id}, returning existing"
                )
                return {
                    "success": True,
                    "message": "Settings already exist",
                    "settings": existing_settings.to_api_dict(),
                }

            # Create new default settings
            settings = AppSettings.create_default(self.db, user_id)

            return {
                "success": True,
                "message": "Default settings created successfully",
                "settings": settings.to_api_dict(),
            }
        except Exception as e:
            logging.error(f"Error creating default settings: {str(e)}")
            raise

    def delete_settings(self, user_id: str) -> Dict[str, Any]:
        """
        Delete app settings for a user

        Used during account deletion.

        Args:
            user_id: User ID

        Returns:
            Dict with success status
        """
        try:
            settings = AppSettings.get_by_user_id(self.db, user_id)

            if not settings:
                return {"success": True, "message": "No settings to delete"}

            settings.delete(self.db)
            logging.info(f"Deleted settings for user {user_id}")

            return {"success": True, "message": "Settings deleted successfully"}
        except Exception as e:
            logging.error(f"Error deleting settings: {str(e)}")
            raise

    def reset_to_defaults(self, user_id: str) -> Dict[str, Any]:
        """
        Reset settings to defaults for a user

        Args:
            user_id: User ID

        Returns:
            Dict with success status and reset settings
        """
        try:
            # Delete existing settings
            settings = AppSettings.get_by_user_id(self.db, user_id)
            if settings:
                settings.delete(self.db)

            # Create new default settings
            new_settings = AppSettings.create_default(self.db, user_id)

            logging.info(f"Reset settings to defaults for user {user_id}")

            return {
                "success": True,
                "message": "Settings reset to defaults",
                "settings": new_settings.to_api_dict(),
            }
        except Exception as e:
            logging.error(f"Error resetting settings: {str(e)}")
            raise
