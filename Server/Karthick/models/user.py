"""
User model for storing user authentication and profile information
"""

import logging
from datetime import datetime
from typing import Any, Dict, List, Optional

from google.cloud.firestore_v1 import FieldFilter

from .base import BaseModel


SENSITIVE_USER_FIELDS = ("admin_password_hash",)


def strip_sensitive_user_fields(data: Dict[str, Any]) -> Dict[str, Any]:
    """Remove secrets from a raw users doc before it leaves the server."""
    for field in SENSITIVE_USER_FIELDS:
        data.pop(field, None)
    return data


class User(BaseModel):
    """User model for storing authentication and profile data"""

    collection_name = "users"

    def __init__(self, **kwargs):
        # User-specific attributes matching client schema
        self.email: str = kwargs.get("email", "")
        self.name: Optional[str] = kwargs.get("name")  # Full name of the student
        self.auth_provider: str = kwargs.get(
            "auth_provider", ""
        )  # google, apple, email
        self.provider_uid: Optional[str] = kwargs.get("provider_uid")
        self.verified: bool = kwargs.get("verified", False)
        self.is_active: bool = kwargs.get("is_active", True)
        self.last_login: Optional[datetime] = kwargs.get("last_login")
        self.avatar_url: Optional[str] = kwargs.get("avatar_url")

        # Admin-specific attributes
        self.is_admin: bool = kwargs.get("is_admin", False)
        self.admin_permissions: List[str] = kwargs.get("admin_permissions", [])
        self.admin_created_at: Optional[datetime] = kwargs.get("admin_created_at")
        self.admin_last_login: Optional[datetime] = kwargs.get("admin_last_login")

        # Referral system attributes
        self.referral_code: Optional[str] = kwargs.get("referral_code")  # User's own unique code
        self.referred_by_user_id: Optional[str] = kwargs.get("referred_by_user_id")  # Who referred them
        self.referred_by_code: Optional[str] = kwargs.get("referred_by_code")  # Code they used
        self.referral_limit: int = kwargs.get("referral_limit", 5)  # Max referrals allowed
        self.referrals_remaining: int = kwargs.get("referrals_remaining", 5)  # Decrements on each referral
        self.referral_stats: Dict[str, int] = kwargs.get("referral_stats", {
            "total_referrals": 0,
            "active_referrals": 0
        })
        self.account_type: str = kwargs.get("account_type", "full")  # "full" or "referral_limited"
        self.daily_limits: Dict[str, int] = kwargs.get("daily_limits", {
            "work_checks": 3,
            "practice_sets": 2,
            "flashcard_sets": 10
        })
        self.daily_usage: Dict[str, Any] = kwargs.get("daily_usage", {
            "work_checks": 0,
            "practice_sets": 0,
            "flashcard_sets": 0,
            "last_reset": datetime.now().strftime("%Y-%m-%d")
        })

        # Subscription system attributes
        self.subscription_tier: str = kwargs.get("subscription_tier", "free")  # free, basic, premium, pro
        self.subscription_status: str = kwargs.get("subscription_status", "active")  # active, expired, cancelled
        self.subscription_start_date: Optional[datetime] = kwargs.get("subscription_start_date")
        self.subscription_end_date: Optional[datetime] = kwargs.get("subscription_end_date")
        self.auto_renew: bool = kwargs.get("auto_renew", False)

        # University student fields
        self.student_profile: Optional[Dict[str, Any]] = kwargs.get("student_profile")
        self.onboarding_completed: bool = kwargs.get("onboarding_completed", False)

        # Admin panel password (werkzeug hash); never returned by the API
        self.admin_password_hash: Optional[str] = kwargs.get("admin_password_hash")

        # Call the base class constructor
        super().__init__(**kwargs)

    def validate(self) -> tuple[bool, str]:
        """Validate user data"""
        # Validate required fields
        if not self.email or not self.email.strip():
            return False, "Email is required"

        # Basic email validation
        import re

        email_pattern = r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"
        if not re.match(email_pattern, self.email):
            return False, "Invalid email format"

        if not self.auth_provider:
            return False, "Authentication provider is required"

        # Validate auth provider
        valid_providers = ["google", "apple", "email", "firebase"]
        if self.auth_provider not in valid_providers:
            return False, f"Auth provider must be one of: {', '.join(valid_providers)}"

        # Validate provider_uid for OAuth providers
        if self.auth_provider in ["google", "apple"] and not self.provider_uid:
            return (
                False,
                f"Provider UID is required for {self.auth_provider} authentication",
            )

        return True, "Valid user data"

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
            "id": self.id,
            "email": self.email,
            "name": (
                self.name if self.name else self.email.split("@")[0]
            ),  # Fallback to email prefix
            "auth_provider": self.auth_provider,
            "provider_uid": self.provider_uid,
            "verified": self.verified,
            "is_active": self.is_active,
            "created_at": serialize_datetime(self.created_at),
            "updated_at": serialize_datetime(self.updated_at),
            "last_login": serialize_datetime(self.last_login),
            "avatar_url": self.avatar_url,
            "is_admin": self.is_admin,
            "admin_permissions": self.admin_permissions,
            "admin_created_at": serialize_datetime(self.admin_created_at),
            "admin_last_login": serialize_datetime(self.admin_last_login),
            # Referral system fields
            "referral_code": self.referral_code,
            "referred_by_user_id": self.referred_by_user_id,
            "referred_by_code": self.referred_by_code,
            "referral_limit": self.referral_limit,
            "referrals_remaining": self.referrals_remaining,
            "referral_stats": self.referral_stats,
            "account_type": self.account_type,
            "daily_limits": self.daily_limits if self.account_type == "referral_limited" else None,
            "daily_usage": self.daily_usage if self.account_type == "referral_limited" else None,
            # Subscription system fields
            "subscription_tier": self.subscription_tier,
            "subscription_status": self.subscription_status,
            "subscription_start_date": serialize_datetime(self.subscription_start_date),
            "subscription_end_date": serialize_datetime(self.subscription_end_date),
            "auto_renew": self.auto_renew,
            # University student fields
            "student_profile": self.student_profile,
            "onboarding_completed": self.onboarding_completed,
            "needs_onboarding": self.needs_onboarding(),
        }

    def needs_onboarding(self) -> bool:
        """True until the student has filled in the required profile fields."""
        return not self.onboarding_completed

    def set_admin_password(self, password: str):
        from werkzeug.security import generate_password_hash

        self.admin_password_hash = generate_password_hash(password)

    def check_admin_password(self, password: Optional[str]) -> bool:
        from werkzeug.security import check_password_hash

        if not self.admin_password_hash or not password:
            return False
        return check_password_hash(self.admin_password_hash, password)

    def update_last_login(self):
        """Update the last login timestamp"""
        self.last_login = datetime.now()

    def verify_user(self):
        """Mark user as verified"""
        self.verified = True

    def soft_delete(self):
        """Soft delete by marking as inactive"""
        self.is_active = False

    def is_admin_user(self) -> bool:
        """Check if user is an admin"""
        return self.is_admin and self.is_active

    def has_admin_permission(self, permission: str) -> bool:
        """Check if admin user has specific permission"""
        if not self.is_admin_user():
            return False
        return (
            permission in self.admin_permissions
            or "admin:all" in self.admin_permissions
        )

    def grant_admin_access(self, permissions: List[str] = None):
        """Grant admin access to user"""
        self.is_admin = True
        self.admin_created_at = datetime.now()
        if permissions:
            self.admin_permissions = permissions
        else:
            self.admin_permissions = ["admin:questions", "admin:dashboard"]

    def revoke_admin_access(self):
        """Revoke admin access from user"""
        self.is_admin = False
        self.admin_permissions = []

    def update_admin_last_login(self):
        """Update the admin last login timestamp"""
        self.admin_last_login = datetime.now()

    def copy(self):
        return User(**self.to_api_dict())

    @classmethod
    def get_by_email(cls, db, email: str) -> Optional["User"]:
        """Get user by email address"""
        try:
            users_ref = db.collection(cls.collection_name)
            query = users_ref.where(filter=FieldFilter("email", "==", email)).where(
                filter=FieldFilter("is_active", "==", True)
            )
            docs = list(query.stream())

            if docs:
                return cls.from_dict(docs[0].to_dict(), docs[0].id)
            return None
        except Exception as e:
            logging.error(f"Error getting user by email: {str(e)}")
            return None

    @classmethod
    def get_by_provider_uid(
        cls, db, provider: str, provider_uid: str
    ) -> Optional["User"]:
        """Get user by authentication provider and UID"""
        try:
            users_ref = db.collection(cls.collection_name)
            query = (
                users_ref.where(filter=FieldFilter("auth_provider", "==", provider))
                .where(filter=FieldFilter("provider_uid", "==", provider_uid))
                .where(filter=FieldFilter("is_active", "==", True))
            )
            docs = list(query.stream())

            if docs:
                return cls.from_dict(docs[0].to_dict(), docs[0].id)
            return None
        except Exception as e:
            logging.error(f"Error getting user by provider UID: {str(e)}")
            return None

    @classmethod
    def get_all_active(cls, db, limit: int = None) -> List["User"]:
        """Get all active users"""
        try:
            users_ref = db.collection(cls.collection_name)
            query = users_ref.where(
                filter=FieldFilter("is_active", "==", True)
            ).order_by("created_at", direction="DESCENDING")

            if limit:
                query = query.limit(limit)

            docs = query.stream()
            users = [cls.from_dict(doc.to_dict(), doc.id) for doc in docs]

            return users
        except Exception as e:
            logging.error(f"Error getting all active users: {str(e)}")
            raise

    @classmethod
    def get_admin_users(cls, db, limit: int = None) -> List["User"]:
        """Get all admin users"""
        try:
            users_ref = db.collection(cls.collection_name)
            # Simplified query to avoid Firebase index requirement
            query = users_ref.where(filter=FieldFilter("is_admin", "==", True))

            docs = query.stream()
            all_admin_users = [cls.from_dict(doc.to_dict(), doc.id) for doc in docs]

            # Filter active users and sort in memory
            active_admin_users = [user for user in all_admin_users if user.is_active]

            # Sort by admin_created_at if available, otherwise by created_at
            active_admin_users.sort(
                key=lambda x: x.admin_created_at or x.created_at or datetime.min,
                reverse=True,
            )
            # Serialize each admin user using auto_serialize_document
            from app.utils.datetime_utils import auto_serialize_document

            # Format response with proper date serialization
            formatted_admin_users = []
            for admin_user in active_admin_users:
                # Convert User object to dictionary first, then serialize datetime fields
                user_dict = strip_sensitive_user_fields(admin_user.to_dict())
                # Serialize the user dictionary to handle DatetimeWithNanoseconds
                serialized_admin_user = auto_serialize_document(user_dict)
                formatted_admin_users.append(serialized_admin_user)

            # Apply limit if specified
            if limit:
                admin_users = formatted_admin_users[:limit]
            else:
                admin_users = formatted_admin_users

            return admin_users
        except Exception as e:
            logging.error(f"Error getting admin users: {str(e)}")
            raise

    @classmethod
    def get_by_referral_code(cls, db, referral_code: str) -> Optional["User"]:
        """Get user by their referral code"""
        try:
            users_ref = db.collection(cls.collection_name)
            query = users_ref.where(filter=FieldFilter("referral_code", "==", referral_code.upper())).where(
                filter=FieldFilter("is_active", "==", True)
            )
            docs = list(query.stream())

            if docs:
                return cls.from_dict(docs[0].to_dict(), docs[0].id)
            return None
        except Exception as e:
            logging.error(f"Error getting user by referral code: {str(e)}")
            return None

    @classmethod
    def get_admin_by_email(cls, db, email: str) -> Optional["User"]:
        """Get admin user by email address"""
        try:
            users_ref = db.collection(cls.collection_name)
            query = (
                users_ref.where(filter=FieldFilter("email", "==", email))
                .where(filter=FieldFilter("is_admin", "==", True))
                .where(filter=FieldFilter("is_active", "==", True))
            )
            docs = list(query.stream())

            if docs:
                return cls.from_dict(docs[0].to_dict(), docs[0].id)
            return None
        except Exception as e:
            logging.error(f"Error getting admin user by email: {str(e)}")
            return None
