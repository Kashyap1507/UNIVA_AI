"""
Usage Limit Service
Handles daily feature usage tracking and limit enforcement for referral_limited accounts
"""

import logging
from datetime import datetime
from typing import Optional, Tuple

from app.config import ReferralConfig
from app.models.user import User
from app.services.firebase_service import db


class UsageLimitService:
    """
    Service for tracking and enforcing daily usage limits

    Features:
    - Check if user has reached daily limit for a feature
    - Increment usage counters
    - Reset daily usage at midnight
    - Get usage summary
    """

    def __init__(self):
        """Initialize Usage Limit Service"""
        self.config = ReferralConfig

    def _reset_if_needed(self, user: User) -> bool:
        """
        Check if daily usage needs to be reset (new day)

        Args:
            user: User object

        Returns:
            True if reset was performed, False otherwise
        """
        today = datetime.now().strftime("%Y-%m-%d")
        last_reset = user.daily_usage.get("last_reset", "")

        if last_reset != today:
            # Reset all counters
            user.daily_usage = {
                "work_checks": 0,
                "practice_sets": 0,
                "flashcard_sets": 0,
                "last_reset": today,
            }
            user.updated_at = datetime.now()
            user.save(db)
            logging.info(f"Reset daily usage for user {user.id} (new day: {today})")
            return True

        return False

    def check_and_increment(
        self, user_id: str, feature_name: str
    ) -> Tuple[bool, int, int]:
        """
        Check if user can use a feature and increment usage counter

        Args:
            user_id: User ID
            feature_name: Feature to check (e.g., "work_checks")

        Returns:
            Tuple of (allowed, remaining, limit)
            - allowed: True if user can use the feature
            - remaining: Number of uses remaining
            - limit: Total daily limit
        """
        try:
            user = User.get_by_id(db, user_id)
            if not user:
                logging.error(f"User not found: {user_id}")
                return False, 0, 0

            # Full accounts have unlimited access
            if user.account_type == "full":
                return True, -1, -1  # -1 indicates unlimited

            # Reset usage if new day
            self._reset_if_needed(user)

            # Get limit for this feature
            limit_key = f"{feature_name}_per_day"
            limit = user.daily_limits.get(feature_name, self.config.REFERRED_USER_LIMITS.get(limit_key, 0))

            # Get current usage
            current_usage = user.daily_usage.get(feature_name, 0)

            # Check if limit reached
            if current_usage >= limit:
                logging.warning(
                    f"Daily limit reached for user {user_id}, feature {feature_name}: "
                    f"{current_usage}/{limit}"
                )
                return False, 0, limit

            # Increment usage
            user.daily_usage[feature_name] = current_usage + 1
            user.updated_at = datetime.now()
            user.save(db)

            remaining = limit - (current_usage + 1)
            logging.info(
                f"Incremented {feature_name} for user {user_id}: "
                f"{current_usage + 1}/{limit} (remaining: {remaining})"
            )

            return True, remaining, limit

        except Exception as e:
            logging.error(f"Error checking usage limit: {str(e)}")
            return False, 0, 0

    def check_limit_without_increment(
        self, user_id: str, feature_name: str
    ) -> Tuple[bool, int, int]:
        """
        Check if user can use a feature WITHOUT incrementing counter

        Args:
            user_id: User ID
            feature_name: Feature to check

        Returns:
            Tuple of (allowed, remaining, limit)
        """
        try:
            user = User.get_by_id(db, user_id)
            if not user:
                return False, 0, 0

            # Full accounts have unlimited access
            if user.account_type == "full":
                return True, -1, -1

            # Reset usage if new day
            self._reset_if_needed(user)

            # Get limit
            limit_key = f"{feature_name}_per_day"
            limit = user.daily_limits.get(feature_name, self.config.REFERRED_USER_LIMITS.get(limit_key, 0))

            # Get current usage
            current_usage = user.daily_usage.get(feature_name, 0)
            remaining = max(0, limit - current_usage)

            return current_usage < limit, remaining, limit

        except Exception as e:
            logging.error(f"Error checking limit: {str(e)}")
            return False, 0, 0

    def get_usage_summary(self, user_id: str) -> dict:
        """
        Get usage summary for all features

        Args:
            user_id: User ID

        Returns:
            Dictionary with usage information for all features
        """
        try:
            user = User.get_by_id(db, user_id)
            if not user:
                return {"error": "User not found"}

            # Full accounts have no limits
            if user.account_type == "full":
                return {
                    "account_type": "full",
                    "unlimited": True,
                    "features": {},
                }

            # Reset if needed
            self._reset_if_needed(user)

            # Build summary for each feature
            features = {}
            for feature in self.config.FEATURES_REQUIRING_LIMITS:
                limit_key = f"{feature}_per_day"
                limit = user.daily_limits.get(feature, self.config.REFERRED_USER_LIMITS.get(limit_key, 0))
                used = user.daily_usage.get(feature, 0)
                remaining = max(0, limit - used)

                features[feature] = {
                    "used": used,
                    "limit": limit,
                    "remaining": remaining,
                    "percentage": int((used / limit * 100)) if limit > 0 else 0,
                }

            return {
                "account_type": "referral_limited",
                "unlimited": False,
                "features": features,
                "last_reset": user.daily_usage.get("last_reset"),
            }

        except Exception as e:
            logging.error(f"Error getting usage summary: {str(e)}")
            return {"error": f"Error: {str(e)}"}

    def reset_daily_usage(self, user_id: str) -> Tuple[bool, str]:
        """
        Manually reset daily usage for a user (admin function)

        Args:
            user_id: User ID

        Returns:
            Tuple of (success, message)
        """
        try:
            user = User.get_by_id(db, user_id)
            if not user:
                return False, "User not found"

            today = datetime.now().strftime("%Y-%m-%d")
            user.daily_usage = {
                "work_checks": 0,
                "practice_sets": 0,
                "flashcard_sets": 0,
                "last_reset": today,
            }
            user.updated_at = datetime.now()
            user.save(db)

            logging.info(f"Manually reset daily usage for user {user_id}")
            return True, "Daily usage reset successfully"

        except Exception as e:
            logging.error(f"Error resetting daily usage: {str(e)}")
            return False, f"Error: {str(e)}"

    def upgrade_to_full_account(self, user_id: str) -> Tuple[bool, str]:
        """
        Upgrade a referral_limited account to full account (removes limits)

        Args:
            user_id: User ID

        Returns:
            Tuple of (success, message)
        """
        try:
            user = User.get_by_id(db, user_id)
            if not user:
                return False, "User not found"

            if user.account_type == "full":
                return True, "User already has full account"

            user.account_type = "full"
            user.updated_at = datetime.now()
            user.save(db)

            logging.info(f"Upgraded user {user_id} to full account")
            return True, "Account upgraded to full access"

        except Exception as e:
            logging.error(f"Error upgrading account: {str(e)}")
            return False, f"Error: {str(e)}"
