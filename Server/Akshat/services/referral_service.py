"""
Referral Service
Handles referral code generation, validation, and tracking
"""

import logging
import random
import string
from datetime import datetime
from typing import List, Optional, Tuple

from app.config import ReferralConfig
from app.models.user import User
from app.services.firebase_service import db


class ReferralService:
    """
    Service for managing referral codes and tracking

    Features:
    - Generate unique referral codes
    - Validate referral codes and check capacity
    - Track referral relationships
    - Manage referral statistics
    """

    def __init__(self):
        """Initialize Referral Service"""
        self.config = ReferralConfig

    def generate_unique_code(self) -> str:
        """
        Generate a unique 6-character referral code

        Format: ABC1XY (3 letters + 1 digit + 2 letters)
        Excludes ambiguous characters: 0, O, 1, I, l

        Returns:
            Unique referral code string
        """
        # Available characters (excluding ambiguous ones)
        letters = [c for c in string.ascii_uppercase if c not in self.config.EXCLUDED_CHARS]
        digits = [c for c in string.digits if c not in self.config.EXCLUDED_CHARS]

        max_attempts = 10
        for _ in range(max_attempts):
            # Generate code: 3 letters + 1 digit + 2 letters
            code = (
                "".join(random.choices(letters, k=3))
                + "".join(random.choices(digits, k=1))
                + "".join(random.choices(letters, k=2))
            )

            # Check if code already exists
            existing_user = User.get_by_referral_code(db, code)
            if not existing_user:
                return code

        # Fallback: add random suffix if all attempts failed
        code = (
            "".join(random.choices(letters, k=3))
            + "".join(random.choices(digits, k=1))
            + "".join(random.choices(letters, k=2))
        )
        logging.warning(f"Generated potentially duplicate code: {code}")
        return code

    def validate_referral_code(self, referral_code: str) -> Tuple[bool, Optional[dict], str]:
        """
        Validate a referral code and check if referrer can still refer

        Args:
            referral_code: The referral code to validate

        Returns:
            Tuple of (is_valid, referrer_info, message)
            - is_valid: True if code is valid and has capacity
            - referrer_info: Dict with referrer name and remaining slots
            - message: Status or error message
        """
        if not referral_code or len(referral_code) != self.config.REFERRAL_CODE_LENGTH:
            return False, None, "Invalid referral code format"

        # Get referrer by code
        referrer = User.get_by_referral_code(db, referral_code.upper())
        if not referrer:
            return False, None, "Referral code not found"

        # Check if referrer has capacity
        if referrer.referrals_remaining <= 0:
            return False, None, f"Referral code has reached its limit ({referrer.referral_limit} referrals)"

        # Valid code with capacity
        referrer_info = {
            "user_id": referrer.id,
            "name": referrer.name or referrer.email.split("@")[0],
            "referrals_remaining": referrer.referrals_remaining,
            "referral_limit": referrer.referral_limit,
        }

        return True, referrer_info, f"Valid code from {referrer_info['name']}"

    def check_referral_capacity(self, referrer_user_id: str) -> Tuple[bool, str]:
        """
        Check if a user has remaining referral capacity

        Args:
            referrer_user_id: ID of the referring user

        Returns:
            Tuple of (can_refer, message)
        """
        referrer = User.get_by_id(db, referrer_user_id)
        if not referrer:
            return False, "Referrer not found"

        if referrer.referrals_remaining <= 0:
            return False, f"Referral limit reached ({referrer.referral_limit} referrals)"

        return True, f"{referrer.referrals_remaining} referrals remaining"

    def consume_referral_slot(self, referrer_user_id: str) -> bool:
        """
        Consume one referral slot from the referrer

        Args:
            referrer_user_id: ID of the referring user

        Returns:
            True if successful, False otherwise
        """
        try:
            referrer = User.get_by_id(db, referrer_user_id)
            if not referrer:
                logging.error(f"Referrer not found: {referrer_user_id}")
                return False

            # Decrement remaining referrals
            referrer.referrals_remaining = max(0, referrer.referrals_remaining - 1)

            # Update referral stats
            referrer.referral_stats["total_referrals"] = referrer.referral_stats.get("total_referrals", 0) + 1
            referrer.referral_stats["active_referrals"] = referrer.referral_stats.get("active_referrals", 0) + 1

            # Save to database
            referrer.updated_at = datetime.now()
            referrer.save(db)

            logging.info(
                f"Consumed referral slot for user {referrer_user_id}. "
                f"Remaining: {referrer.referrals_remaining}/{referrer.referral_limit}"
            )
            return True

        except Exception as e:
            logging.error(f"Error consuming referral slot: {str(e)}")
            return False

    def track_referral(
        self, referred_user_id: str, referrer_user_id: str, referral_code: str
    ) -> Tuple[bool, str]:
        """
        Track a referral relationship between users

        Args:
            referred_user_id: ID of the new user
            referrer_user_id: ID of the referrer
            referral_code: Referral code used

        Returns:
            Tuple of (success, message)
        """
        try:
            # Get referred user
            referred_user = User.get_by_id(db, referred_user_id)
            if not referred_user:
                return False, "Referred user not found"

            # Update referred user's referral fields
            referred_user.referred_by_user_id = referrer_user_id
            referred_user.referred_by_code = referral_code  # Fixed: was creating local variable
            referred_user.account_type = "referral_limited"  # Set limited account
            referred_user.updated_at = datetime.now()
            referred_user.save(db)

            logging.info(
                f"Tracked referral: User {referred_user_id} referred by {referrer_user_id} "
                f"using code {referral_code}"
            )
            return True, "Referral tracked successfully"

        except Exception as e:
            logging.error(f"Error tracking referral: {str(e)}")
            return False, f"Error tracking referral: {str(e)}"

    def get_user_referrals(self, user_id: str) -> List[dict]:
        """
        Get list of users referred by this user

        Args:
            user_id: ID of the referring user

        Returns:
            List of referred user dictionaries
        """
        try:
            from google.cloud.firestore_v1 import FieldFilter

            users_ref = db.collection("users")
            query = users_ref.where(filter=FieldFilter("referred_by_user_id", "==", user_id))

            docs = query.stream()
            referred_users = []

            for doc in docs:
                user_data = doc.to_dict()
                referred_users.append(
                    {
                        "id": doc.id,
                        "name": user_data.get("name") or user_data.get("email", "").split("@")[0],
                        "email": user_data.get("email"),
                        "account_type": user_data.get("account_type", "full"),
                        "created_at": (
                            user_data.get("created_at").isoformat()
                            if user_data.get("created_at")
                            else None
                        ),
                        "is_active": user_data.get("is_active", True),
                    }
                )

            return referred_users

        except Exception as e:
            logging.error(f"Error getting user referrals: {str(e)}")
            return []

    def get_referral_stats(self, user_id: str) -> dict:
        """
        Get referral statistics for a user

        Args:
            user_id: User ID

        Returns:
            Dictionary with referral statistics
        """
        try:
            user = User.get_by_id(db, user_id)
            if not user:
                return {
                    "error": "User not found",
                    "referral_code": None,
                    "referral_limit": 0,
                    "referrals_remaining": 0,
                    "referral_stats": {},
                }

            # Auto-generate referral code for existing users without one
            if not user.referral_code:
                logging.info(f"Generating referral code for existing user {user_id}")
                user.referral_code = self.generate_unique_code()
                user.referral_limit = 5
                user.referrals_remaining = 5
                user.referral_stats = {}
                user.save(db)

            # Get list of referred users
            referred_users = self.get_user_referrals(user_id)

            return {
                "referral_code": user.referral_code,
                "referral_limit": user.referral_limit,
                "referrals_remaining": user.referrals_remaining,
                "can_refer": user.referrals_remaining > 0,
                "referral_stats": user.referral_stats or {},
                "referred_users": referred_users,
                "referred_users_count": len(referred_users),
            }

        except Exception as e:
            logging.error(f"Error getting referral stats: {str(e)}", exc_info=True)
            return {
                "error": f"Error: {str(e)}",
                "referral_code": None,
                "referral_limit": 0,
                "referrals_remaining": 0,
                "referral_stats": {},
            }
