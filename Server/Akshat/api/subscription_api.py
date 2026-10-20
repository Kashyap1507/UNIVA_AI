"""
Subscription API endpoints
Handles subscription plan retrieval, current subscription status, and upgrades
"""

import logging
from datetime import datetime

from flask import request
from flask_restful import Resource

from app.models.subscription_plan import SubscriptionPlan
from app.models.user import User
from app.services.firebase_service import get_firestore_client
from app.services.usage_limit_service import UsageLimitService
from app.utils.decorators import jwt_required


class SubscriptionPlansAPI(Resource):
    """
    GET /api/subscriptions/plans
    List all available subscription plans
    """

    def get(self):
        """
        Get all active subscription plans

        Response:
            - success: Boolean
            - plans: List of subscription plan objects
        """
        try:
            db = get_firestore_client()
            plans = SubscriptionPlan.get_all_active(db, order_by_display_order=True)

            # Manually construct response to avoid serialization issues
            plans_data = []
            for plan in plans:
                plans_data.append({
                    "id": plan.id,
                    "tier": plan.tier,
                    "name": plan.name,
                    "description": plan.description,
                    "price_monthly": plan.price_monthly,
                    "price_yearly": plan.price_yearly,
                    "limits": plan.limits,
                    "features": plan.features,
                    "is_popular": plan.is_popular,
                    "display_order": plan.display_order,
                })

            return {
                "success": True,
                "plans": plans_data,
            }, 200

        except Exception as e:
            logging.error(f"Error getting subscription plans: {str(e)}")
            return {
                "success": False,
                "error": "Failed to retrieve subscription plans",
            }, 500


class CurrentSubscriptionAPI(Resource):
    """
    GET /api/subscriptions/current
    Get user's current subscription and usage stats
    """

    @jwt_required
    def get(self, current_user):
        """
        Get current user's subscription details and usage summary

        Response:
            - success: Boolean
            - subscription: Subscription details object
            - usage: Usage summary object
        """
        try:
            db = get_firestore_client()
            usage_service = UsageLimitService()

            # Get user
            user = User.get_by_id(db, current_user["id"])
            if not user:
                return {
                    "success": False,
                    "error": "User not found",
                }, 404

            # Get current plan details
            plan = SubscriptionPlan.get_by_tier(db, user.subscription_tier)

            # Get usage summary
            usage_summary = usage_service.get_usage_summary(current_user["id"])

            # Construct subscription data
            subscription_data = {
                "tier": user.subscription_tier,
                "status": user.subscription_status,
                "account_type": user.account_type,
                "start_date": user.subscription_start_date.isoformat() if user.subscription_start_date else None,
                "end_date": user.subscription_end_date.isoformat() if user.subscription_end_date else None,
                "auto_renew": user.auto_renew,
                "plan_name": plan.name if plan else "Free",
                "plan_limits": plan.limits if plan else {},
            }

            return {
                "success": True,
                "subscription": subscription_data,
                "usage": usage_summary,
            }, 200

        except Exception as e:
            logging.error(f"Error getting current subscription: {str(e)}")
            return {
                "success": False,
                "error": "Failed to retrieve subscription details",
            }, 500


class SubscriptionUpgradeAPI(Resource):
    """
    POST /api/subscriptions/upgrade
    Upgrade user's subscription tier (payment integration stub)
    """

    @jwt_required
    def post(self, current_user):
        """
        Upgrade user's subscription to a new tier

        Request body:
            - tier: Target subscription tier (basic, premium, pro)

        Response:
            - success: Boolean
            - message: Status message
            - subscription: Updated subscription details
        """
        try:
            data = request.get_json()
            new_tier = data.get("tier")

            if not new_tier:
                return {
                    "success": False,
                    "error": "Tier is required",
                }, 400

            # Validate tier
            valid_tiers = ["free", "basic", "premium", "pro"]
            if new_tier not in valid_tiers:
                return {
                    "success": False,
                    "error": f"Invalid tier. Must be one of: {', '.join(valid_tiers)}",
                }, 400

            db = get_firestore_client()

            # Get user
            user = User.get_by_id(db, current_user["id"])
            if not user:
                return {
                    "success": False,
                    "error": "User not found",
                }, 404

            # Verify plan exists
            new_plan = SubscriptionPlan.get_by_tier(db, new_tier)
            if not new_plan:
                return {
                    "success": False,
                    "error": f"Subscription plan '{new_tier}' not found",
                }, 404

            # Update user subscription
            # NOTE: In production, this would be called AFTER payment confirmation
            user.subscription_tier = new_tier
            user.subscription_status = "active"
            user.subscription_start_date = datetime.now()

            # Set end date to 30 days from now (for monthly subscription)
            from datetime import timedelta
            user.subscription_end_date = datetime.now() + timedelta(days=30)

            user.auto_renew = data.get("auto_renew", True)
            user.updated_at = datetime.now()

            # Update limits based on new plan
            user.daily_limits = new_plan.limits

            # Reset daily usage
            user.daily_usage = {
                "work_checks": 0,
                "practice_sets": 0,
                "flashcard_sets": 0,
                "last_reset": datetime.now().strftime("%Y-%m-%d"),
            }

            # Save to database
            user.save(db)

            logging.info(
                f"User {current_user['id']} upgraded to {new_tier} tier"
            )

            # Return updated subscription data
            subscription_data = {
                "tier": user.subscription_tier,
                "status": user.subscription_status,
                "start_date": user.subscription_start_date.isoformat() if user.subscription_start_date else None,
                "end_date": user.subscription_end_date.isoformat() if user.subscription_end_date else None,
                "auto_renew": user.auto_renew,
                "plan_name": new_plan.name,
                "plan_limits": new_plan.limits,
            }

            return {
                "success": True,
                "message": f"Successfully upgraded to {new_plan.name} plan",
                "subscription": subscription_data,
            }, 200

        except Exception as e:
            logging.error(f"Error upgrading subscription: {str(e)}")
            return {
                "success": False,
                "error": "Failed to upgrade subscription",
            }, 500
