"""
FCM Token Registration API
Allows Wizly-App mobile devices to register for push notifications
"""

import logging

from flask_restful import Resource, reqparse
from google.cloud import firestore

from app.services.firebase_service import get_firestore_client
from app.utils.decorators import jwt_required

logger = logging.getLogger(__name__)


class FCMTokenAPI(Resource):
    """Register/unregister FCM device tokens for Wizly-App"""

    def __init__(self):
        self.parser = reqparse.RequestParser()
        self.parser.add_argument(
            "fcm_token", type=str, required=True, help="FCM token is required"
        )
        self.parser.add_argument(
            "device_id", type=str, required=True, help="Device ID is required"
        )
        self.parser.add_argument(
            "platform",
            type=str,
            required=True,
            help="Platform (android/ios) is required",
        )
        self.parser.add_argument("device_model", type=str, required=False)
        self.parser.add_argument("app_version", type=str, required=False)
        self.db = get_firestore_client()

    @jwt_required
    def post(self, current_user):
        """
        Register FCM token for user's device

        Request Body:
        {
            "fcm_token": "abc123...",
            "device_id": "device_unique_id",
            "platform": "android",  # or "ios"
            "device_model": "Pixel 6",  # optional
            "app_version": "1.0.0"  # optional
        }

        Returns:
            201: Token registered successfully
            400: Invalid request
            500: Server error
        """
        try:
            args = self.parser.parse_args()
            user_id = current_user["id"]

            # Validate platform
            platform = args["platform"].lower()
            if platform not in ["android", "ios"]:
                return {
                    "success": False,
                    "message": 'Platform must be either "android" or "ios"',
                }, 400

            # Prepare token data
            token_data = {
                "user_id": user_id,
                "token": args["fcm_token"],
                "device_id": args["device_id"],
                "platform": platform,
                "created_at": firestore.SERVER_TIMESTAMP,
                "last_updated": firestore.SERVER_TIMESTAMP,
                "is_active": True,
            }

            # Add optional fields
            if args.get("device_model"):
                token_data["device_model"] = args["device_model"]
            if args.get("app_version"):
                token_data["app_version"] = args["app_version"]

            # Use device_id as document ID for upsert behavior
            doc_ref = self.db.collection("fcm_tokens").document(args["device_id"])

            # Check if token already exists
            existing_doc = doc_ref.get()
            if existing_doc.exists:
                # Update existing token
                doc_ref.update(
                    {
                        "token": args["fcm_token"],
                        "last_updated": firestore.SERVER_TIMESTAMP,
                        "is_active": True,
                    }
                )
                logger.info(
                    f"✅ FCM token updated for user {user_id}, device {args['device_id']}"
                )
                message = "FCM token updated successfully"
            else:
                # Create new token
                doc_ref.set(token_data)
                logger.info(
                    f"✅ FCM token registered for user {user_id}, device {args['device_id']}"
                )
                message = "FCM token registered successfully"

            return {
                "success": True,
                "message": message,
                "device_id": args["device_id"],
            }, 201

        except Exception as e:
            logger.error(f"❌ FCM token registration failed: {str(e)}")
            return {
                "success": False,
                "message": f"Failed to register FCM token: {str(e)}",
            }, 500

    @jwt_required
    def delete(self, current_user):
        """
        Unregister FCM token (on logout or app uninstall)

        Request Body:
        {
            "device_id": "device_unique_id"
        }

        Returns:
            200: Token removed successfully
            404: Token not found
            500: Server error
        """
        try:
            args = self.parser.parse_args()
            user_id = current_user["id"]
            device_id = args["device_id"]

            # Get token document
            doc_ref = self.db.collection("fcm_tokens").document(device_id)
            doc = doc_ref.get()

            if not doc.exists:
                return {"success": False, "message": "FCM token not found"}, 404

            # Verify ownership
            token_data = doc.to_dict()
            if token_data.get("user_id") != user_id:
                return {
                    "success": False,
                    "message": "Unauthorized to delete this token",
                }, 403

            # Mark as inactive instead of deleting (for audit trail)
            doc_ref.update(
                {"is_active": False, "last_updated": firestore.SERVER_TIMESTAMP}
            )

            logger.info(
                f"🗑️ FCM token deactivated for user {user_id}, device {device_id}"
            )

            return {"success": True, "message": "FCM token removed successfully"}, 200

        except Exception as e:
            logger.error(f"❌ FCM token removal failed: {str(e)}")
            return {
                "success": False,
                "message": f"Failed to remove FCM token: {str(e)}",
            }, 500

    @jwt_required
    def get(self, current_user):
        """
        Get all registered FCM tokens for current user

        Returns:
            200: List of registered devices
            500: Server error
        """
        try:
            user_id = current_user["id"]

            # Query all active tokens for user
            tokens_ref = (
                self.db.collection("fcm_tokens")
                .where("user_id", "==", user_id)
                .where("is_active", "==", True)
            )

            devices = []
            for doc in tokens_ref.stream():
                token_data = doc.to_dict()
                devices.append(
                    {
                        "device_id": doc.id,
                        "platform": token_data.get("platform"),
                        "device_model": token_data.get("device_model", "Unknown"),
                        "app_version": token_data.get("app_version", "Unknown"),
                        "registered_at": token_data.get("created_at"),
                    }
                )

            logger.info(
                f"📱 Retrieved {len(devices)} active devices for user {user_id}"
            )

            return {
                "success": True,
                "devices": devices,
                "total_devices": len(devices),
            }, 200

        except Exception as e:
            logger.error(f"❌ Failed to get FCM tokens: {str(e)}")
            return {
                "success": False,
                "message": f"Failed to retrieve devices: {str(e)}",
            }, 500


class FCMTestAPI(Resource):
    """Test FCM notification system"""

    @jwt_required
    def post(self, current_user):
        """
        Send test notification to user

        Returns:
            200: Test notification sent
            500: Failed to send
        """
        try:
            from app.services.data_change_notifier import test_notification

            user_id = current_user["id"]

            # Send test notification
            test_notification(user_id)

            return {
                "success": True,
                "message": "Test notification sent successfully",
            }, 200

        except Exception as e:
            logger.error(f"❌ Test notification failed: {str(e)}")
            return {
                "success": False,
                "message": f"Failed to send test notification: {str(e)}",
            }, 500
