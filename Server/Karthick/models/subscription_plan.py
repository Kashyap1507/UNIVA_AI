"""
Subscription Plan model for managing subscription tiers and features
"""

import logging
from datetime import datetime
from typing import Any, Dict, List, Optional

from google.cloud.firestore_v1 import FieldFilter

from .base import BaseModel


class SubscriptionPlan(BaseModel):
    """
    Subscription Plan model for defining subscription tiers and their features

    Firestore collection: 'subscription_plans'

    Supports four tiers: FREE, BASIC, PREMIUM, PRO
    """

    collection_name = "subscription_plans"

    # Tier constants
    TIER_FREE = "free"
    TIER_BASIC = "basic"
    TIER_PREMIUM = "premium"
    TIER_PRO = "pro"

    def __init__(self, **kwargs):
        # Plan identification
        self.tier: str = kwargs.get("tier", self.TIER_FREE)
        self.name: str = kwargs.get("name", "Free")
        self.description: str = kwargs.get("description", "")

        # Pricing
        self.price_monthly: float = kwargs.get("price_monthly", 0.0)
        self.price_yearly: float = kwargs.get("price_yearly", 0.0)

        # Daily usage limits
        self.limits: Dict[str, int] = kwargs.get("limits", {
            "work_checks": 3,
            "practice_sets": 2,
            "flashcard_sets": 10
        })

        # Features list for UI display
        self.features: List[str] = kwargs.get("features", [])

        # Display settings
        self.is_active: bool = kwargs.get("is_active", True)
        self.is_popular: bool = kwargs.get("is_popular", False)  # "Most Popular" badge
        self.display_order: int = kwargs.get("display_order", 0)

        # Call the base class constructor
        super().__init__(**kwargs)

    def validate(self) -> tuple[bool, str]:
        """Validate subscription plan data"""
        if not self.tier:
            return False, "Tier is required"

        valid_tiers = [self.TIER_FREE, self.TIER_BASIC, self.TIER_PREMIUM, self.TIER_PRO]
        if self.tier not in valid_tiers:
            return False, f"Tier must be one of: {', '.join(valid_tiers)}"

        if not self.name:
            return False, "Plan name is required"

        if self.price_monthly < 0 or self.price_yearly < 0:
            return False, "Prices cannot be negative"

        return True, "Valid subscription plan data"

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
            "tier": self.tier,
            "name": self.name,
            "description": self.description,
            "price_monthly": self.price_monthly,
            "price_yearly": self.price_yearly,
            "limits": self.limits,
            "features": self.features,
            "is_active": self.is_active,
            "is_popular": self.is_popular,
            "display_order": self.display_order,
            "created_at": serialize_datetime(self.created_at),
            "updated_at": serialize_datetime(self.updated_at),
        }

    @classmethod
    def get_by_tier(cls, db, tier: str) -> Optional["SubscriptionPlan"]:
        """Get subscription plan by tier"""
        try:
            plans_ref = db.collection(cls.collection_name)
            query = plans_ref.where(filter=FieldFilter("tier", "==", tier)).where(
                filter=FieldFilter("is_active", "==", True)
            )
            docs = list(query.stream())

            if docs:
                return cls.from_dict(docs[0].to_dict(), docs[0].id)
            return None
        except Exception as e:
            logging.error(f"Error getting plan by tier: {str(e)}")
            return None

    @classmethod
    def get_all_active(cls, db, order_by_display_order: bool = True) -> List["SubscriptionPlan"]:
        """Get all active subscription plans"""
        try:
            plans_ref = db.collection(cls.collection_name)
            query = plans_ref.where(filter=FieldFilter("is_active", "==", True))

            docs = query.stream()
            plans = [cls.from_dict(doc.to_dict(), doc.id) for doc in docs]

            if order_by_display_order:
                plans.sort(key=lambda x: x.display_order)

            return plans
        except Exception as e:
            logging.error(f"Error getting active plans: {str(e)}")
            return []

    def copy(self):
        """Create a copy of the subscription plan"""
        return SubscriptionPlan(**self.to_api_dict())
