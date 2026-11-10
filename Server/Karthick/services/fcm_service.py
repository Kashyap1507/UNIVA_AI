"""
Firebase Cloud Messaging Service
Sends push notifications to mobile devices for Wizly-App
"""

import logging
from typing import Any, Dict, List, Optional

from firebase_admin import messaging

from app.services.firebase_service import get_firestore_client

logger = logging.getLogger(__name__)


class FCMService:
    """Handle FCM push notifications for Wizly-App"""

    @staticmethod
    def send_to_user(
        user_id: str, notification_data: Dict[str, Any], silent: bool = False
    ) -> Optional[messaging.BatchResponse]:
        """
        Send push notification to all user's registered devices

        Args:
            user_id: The student's user ID
            notification_data: {
                "title": "Course material ready",
                "body": "Week 3 slides is ready for the tutor, cards and practice.",
                "type": "material_ready",
                "data": {"course_id": "...", "material_id": "..."}
            }
            silent: If True, no visible notification (silent data sync only)

        Returns:
            BatchResponse or None if failed
        """
        try:
            # Get user's FCM tokens from Firebase
            tokens = FCMService._get_user_tokens(user_id)

            if not tokens:
                logger.info(f"No FCM tokens found for user {user_id}")
                return None

            # Build notification payload
            notification_payload = None
            if not silent:
                notification_payload = messaging.Notification(
                    title=notification_data.get("title", "Univa AI Update"),
                    body=notification_data.get("body", ""),
                )

            # Data payload (always included for background processing).
            # FCM data values must be strings; lists are sent comma-separated.
            data_payload = {"type": notification_data.get("type", "generic")}
            for key, value in (notification_data.get("data") or {}).items():
                if value is None or value == "" or value == []:
                    continue
                if isinstance(value, (list, tuple)):
                    value = ",".join(str(v) for v in value)
                data_payload[key] = str(value)

            # Build FCM message
            message = messaging.MulticastMessage(
                notification=notification_payload,
                data=data_payload,
                tokens=tokens,
                android=messaging.AndroidConfig(
                    priority="high",
                    notification=messaging.AndroidNotification(
                        channel_id="wizly_updates", sound="default"
                    ),
                ),
            )

            # Send to all devices (updated for firebase-admin 7.x)
            response = messaging.send_each_for_multicast(message)

            logger.info(
                f"📤 FCM sent to user {user_id}: "
                f"{response.success_count} success, "
                f"{response.failure_count} failure"
            )

            # Clean up invalid tokens
            if response.failure_count > 0:
                FCMService._cleanup_invalid_tokens(user_id, tokens, response)

            return response

        except Exception as e:
            logger.error(f"❌ FCM send error: {str(e)}")
            # Don't fail the main operation if FCM fails
            return None

    @staticmethod
    def _get_user_tokens(user_id: str) -> List[str]:
        """
        Get all FCM tokens for user from Firebase

        Args:
            user_id: The student's user ID

        Returns:
            List of FCM tokens
        """
        try:
            db = get_firestore_client()

            # Query fcm_tokens collection
            tokens_ref = db.collection("fcm_tokens").where("user_id", "==", user_id)
            tokens = []

            for doc in tokens_ref.stream():
                token_data = doc.to_dict()
                token = token_data.get("token")
                if token:
                    tokens.append(token)

            logger.debug(f"Found {len(tokens)} FCM tokens for user {user_id}")
            return tokens

        except Exception as e:
            logger.error(f"Error getting FCM tokens for user {user_id}: {str(e)}")
            return []

    @staticmethod
    def _cleanup_invalid_tokens(
        user_id: str, tokens: List[str], response: messaging.BatchResponse
    ):
        """
        Remove invalid FCM tokens from database

        Args:
            user_id: The student's user ID
            tokens: List of tokens that were attempted
            response: BatchResponse from FCM
        """
        try:
            db = get_firestore_client()

            # Remove failed tokens
            for idx, result in enumerate(response.responses):
                if not result.success:
                    invalid_token = tokens[idx]

                    # Delete from database
                    query = db.collection("fcm_tokens").where(
                        "token", "==", invalid_token
                    )
                    for doc in query.stream():
                        doc.reference.delete()
                        logger.info(f"🧹 Removed invalid FCM token for user {user_id}")

        except Exception as e:
            logger.error(f"Error cleaning up invalid tokens: {str(e)}")

    @staticmethod
    def send_test_notification(user_id: str) -> bool:
        """
        Send test notification to verify FCM setup

        Args:
            user_id: The student's user ID

        Returns:
            True if successful, False otherwise
        """
        try:
            test_data = {
                "title": "Univa AI Test Notification",
                "body": "FCM is working correctly!",
                "type": "test",
                "data": {},
            }

            response = FCMService.send_to_user(user_id, test_data)
            return response is not None and response.success_count > 0

        except Exception as e:
            logger.error(f"Test notification failed: {str(e)}")
            return False
