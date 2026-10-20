"""
App Settings API endpoints

Handles REST API endpoints for user app settings.
"""

import logging

from flask import request
from flask_restful import Resource

from app.services.app_settings_service import AppSettingsService
from app.utils.decorators import jwt_required


class AppSettingsAPI(Resource):
    """Handle app settings operations"""

    def __init__(self):
        self.settings_service = AppSettingsService()

    @jwt_required
    def get(self, current_user):
        """
        Get app settings for the current user

        Returns existing settings or creates defaults if none exist.

        Returns:
            200: Settings retrieved successfully
            500: Server error
        """
        try:
            logging.info(
                f"[APP_SETTINGS_API] GET request for user: {current_user.get('id')}"
            )

            result = self.settings_service.get_user_settings(current_user["id"])

            if result["success"]:
                logging.info(
                    f"[APP_SETTINGS_API] Retrieved settings for user {current_user['id']}: "
                    f"cloud_sync={result['settings'].get('cloud_sync_enabled')}, "
                    f"cellular_sync={result['settings'].get('cellular_sync_enabled')}, "
                    f"scan_quality={result['settings'].get('scan_quality')}"
                )

                return {"success": True, "settings": result["settings"]}, 200
            else:
                logging.error(
                    f"[APP_SETTINGS_API] Failed to get settings: {result.get('message')}"
                )
                return {
                    "success": False,
                    "error": result.get("message", "Failed to get settings"),
                }, 500

        except Exception as e:
            logging.error(f"[APP_SETTINGS_API] Error getting settings: {str(e)}")
            return {
                "success": False,
                "error": "Failed to get settings",
                "details": str(e),
            }, 500

    @jwt_required
    def put(self, current_user):
        """
        Update app settings for the current user

        Expected JSON payload:
        {
            "cloud_sync_enabled": bool (optional),
            "cellular_sync_enabled": bool (optional),
            "scan_quality": string (optional) - "SMALL", "STANDARD", "LARGE", or "HD"
        }

        Returns:
            200: Settings updated successfully
            400: Invalid data
            500: Server error
        """
        try:
            logging.info(
                f"[APP_SETTINGS_API] PUT request for user: {current_user.get('id')}"
            )

            data = request.get_json()

            if not data:
                return {"success": False, "error": "No data provided"}, 400

            # Validate scan quality if provided
            if "scan_quality" in data:
                valid_qualities = ["SMALL", "STANDARD", "LARGE", "HD"]
                scan_quality = data["scan_quality"].upper()
                if scan_quality not in valid_qualities:
                    return {
                        "success": False,
                        "error": f'scan_quality must be one of: {", ".join(valid_qualities)}',
                    }, 400
                data["scan_quality"] = scan_quality

            # Update settings
            result = self.settings_service.update_settings(current_user["id"], data)

            if result["success"]:
                logging.info(
                    f"[APP_SETTINGS_API] Updated settings for user {current_user['id']}"
                )

                # Send push notification for settings update
                try:
                    from app.services.data_change_notifier import notify_data_change

                    notify_data_change(
                        user_id=current_user["id"],
                        change_type="settings_updated",
                        data={"updated_fields": list(data.keys())},
                    )
                except Exception as notif_error:
                    logging.warning(
                        f"[APP_SETTINGS_API] Notification failed: {notif_error}"
                    )
                    # Don't fail the request if notification fails

                return {
                    "success": True,
                    "message": result["message"],
                    "settings": result["settings"],
                }, 200
            else:
                logging.error(
                    f"[APP_SETTINGS_API] Failed to update settings: {result.get('message')}"
                )
                return {
                    "success": False,
                    "error": result.get("message", "Failed to update settings"),
                }, 400

        except Exception as e:
            logging.error(f"[APP_SETTINGS_API] Error updating settings: {str(e)}")
            return {
                "success": False,
                "error": "Failed to update settings",
                "details": str(e),
            }, 500

    @jwt_required
    def delete(self, current_user):
        """
        Delete app settings for the current user

        Used during account deletion.

        Returns:
            200: Settings deleted successfully
            500: Server error
        """
        try:
            logging.info(
                f"[APP_SETTINGS_API] DELETE request for user: {current_user.get('id')}"
            )

            result = self.settings_service.delete_settings(current_user["id"])

            if result["success"]:
                logging.info(
                    f"[APP_SETTINGS_API] Deleted settings for user {current_user['id']}"
                )
                return {"success": True, "message": result["message"]}, 200
            else:
                logging.error(
                    f"[APP_SETTINGS_API] Failed to delete settings: {result.get('message')}"
                )
                return {
                    "success": False,
                    "error": result.get("message", "Failed to delete settings"),
                }, 500

        except Exception as e:
            logging.error(f"[APP_SETTINGS_API] Error deleting settings: {str(e)}")
            return {
                "success": False,
                "error": "Failed to delete settings",
                "details": str(e),
            }, 500


class AppSettingsResetAPI(Resource):
    """Handle resetting settings to defaults"""

    def __init__(self):
        self.settings_service = AppSettingsService()

    @jwt_required
    def post(self, current_user):
        """
        Reset app settings to defaults for the current user

        Returns:
            200: Settings reset successfully
            500: Server error
        """
        try:
            logging.info(
                f"[APP_SETTINGS_RESET_API] POST request for user: {current_user.get('id')}"
            )

            result = self.settings_service.reset_to_defaults(current_user["id"])

            if result["success"]:
                logging.info(
                    f"[APP_SETTINGS_RESET_API] Reset settings to defaults for user {current_user['id']}"
                )

                # Send push notification for settings reset
                try:
                    from app.services.data_change_notifier import notify_data_change

                    notify_data_change(
                        user_id=current_user["id"],
                        change_type="settings_reset",
                        data={},
                    )
                except Exception as notif_error:
                    logging.warning(
                        f"[APP_SETTINGS_RESET_API] Notification failed: {notif_error}"
                    )
                    # Don't fail the request if notification fails

                return {
                    "success": True,
                    "message": result["message"],
                    "settings": result["settings"],
                }, 200
            else:
                logging.error(
                    f"[APP_SETTINGS_RESET_API] Failed to reset settings: {result.get('message')}"
                )
                return {
                    "success": False,
                    "error": result.get("message", "Failed to reset settings"),
                }, 500

        except Exception as e:
            logging.error(
                f"[APP_SETTINGS_RESET_API] Error resetting settings: {str(e)}"
            )
            return {
                "success": False,
                "error": "Failed to reset settings",
                "details": str(e),
            }, 500
