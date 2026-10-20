"""
Referral API endpoints
Handles referral code validation, retrieval, and statistics
"""

import logging

from flask import request
from flask_restful import Resource

from app.services.referral_service import ReferralService
from app.services.usage_limit_service import UsageLimitService
from app.utils.decorators import jwt_required


class ReferralValidateAPI(Resource):
    """API for validating referral codes"""

    def __init__(self):
        self.service = ReferralService()

    def post(self):
        """
        POST /api/referrals/validate

        Validate a referral code (optional check before signup)

        Request body:
            - referral_code (required): Code to validate

        Response:
            - success: Boolean
            - valid: Boolean
            - message: Status message
            - referrer_info: Dict with referrer details (if valid)
        """
        try:
            data = request.get_json()
            referral_code = data.get("referral_code")

            if not referral_code:
                return {
                    "success": False,
                    "valid": False,
                    "message": "Referral code is required",
                }, 400

            # Validate code
            is_valid, referrer_info, message = self.service.validate_referral_code(
                referral_code
            )

            if is_valid:
                return {
                    "success": True,
                    "valid": True,
                    "message": message,
                    "referrer_info": {
                        "name": referrer_info["name"],
                        "referrals_remaining": referrer_info["referrals_remaining"],
                    },
                }, 200
            else:
                return {
                    "success": True,
                    "valid": False,
                    "message": message,
                }, 200

        except Exception as e:
            logging.error(f"Error validating referral code: {str(e)}")
            return {
                "success": False,
                "valid": False,
                "message": "Error validating referral code",
            }, 500


class ReferralMyCodeAPI(Resource):
    """API for getting user's referral code"""

    def __init__(self):
        self.service = ReferralService()

    @jwt_required
    def get(self, current_user=None):
        """
        GET /api/referrals/my-code

        Get current user's referral code

        Response:
            - success: Boolean
            - referral_code: User's unique code
            - referral_limit: Max referrals allowed
            - referrals_remaining: Number of referrals left
            - can_refer: Boolean
        """
        try:
            if not current_user:
                return {
                    "success": False,
                    "message": "Authentication required",
                }, 401

            user_id = current_user["id"]

            # Get user's referral stats
            stats = self.service.get_referral_stats(user_id)

            if "error" in stats:
                return {
                    "success": False,
                    "message": stats["error"],
                }, 404

            return {
                "success": True,
                "referral_code": stats["referral_code"],
                "referral_limit": stats["referral_limit"],
                "referrals_remaining": stats["referrals_remaining"],
                "can_refer": stats["can_refer"],
            }, 200

        except Exception as e:
            logging.error(f"Error getting referral code: {str(e)}")
            return {
                "success": False,
                "message": "Error retrieving referral code",
            }, 500


class ReferralMyReferralsAPI(Resource):
    """API for getting user's referrals"""

    def __init__(self):
        self.service = ReferralService()

    @jwt_required
    def get(self, current_user=None):
        """
        GET /api/referrals/my-referrals

        Get list of users referred by current user

        Response:
            - success: Boolean
            - referrals: List of referred users
            - total_count: Number of referrals
        """
        try:
            if not current_user:
                return {
                    "success": False,
                    "message": "Authentication required",
                }, 401

            user_id = current_user["id"]

            # Get referred users
            referred_users = self.service.get_user_referrals(user_id)

            return {
                "success": True,
                "referrals": referred_users,
                "total_count": len(referred_users),
            }, 200

        except Exception as e:
            logging.error(f"Error getting referrals: {str(e)}")
            return {
                "success": False,
                "message": "Error retrieving referrals",
            }, 500


class ReferralStatsAPI(Resource):
    """API for getting referral statistics"""

    def __init__(self):
        self.service = ReferralService()

    @jwt_required
    def get(self, current_user=None):
        """
        GET /api/referrals/stats

        Get comprehensive referral statistics

        Response:
            - success: Boolean
            - referral_code: User's code
            - referral_limit: Max referrals
            - referrals_remaining: Remaining slots
            - can_refer: Boolean
            - referral_stats: Dict with total/active counts
            - referred_users: List of referred users
        """
        try:
            if not current_user:
                return {
                    "success": False,
                    "message": "Authentication required",
                }, 401

            user_id = current_user["id"]

            # Get complete stats
            stats = self.service.get_referral_stats(user_id)

            if "error" in stats:
                return {
                    "success": False,
                    "message": stats["error"],
                }, 404

            return {
                "success": True,
                **stats,
            }, 200

        except Exception as e:
            logging.error(f"Error getting referral stats: {str(e)}")
            return {
                "success": False,
                "message": "Error retrieving referral statistics",
            }, 500


class ReferralLimitsAPI(Resource):
    """API for getting referral capacity limits"""

    def __init__(self):
        self.service = ReferralService()

    @jwt_required
    def get(self, current_user=None):
        """
        GET /api/referrals/my-limits

        Get user's referral capacity information

        Response:
            - success: Boolean
            - referrals_remaining: Number of referrals left
            - referral_limit: Maximum referrals allowed
            - can_refer: Boolean
        """
        try:
            if not current_user:
                return {
                    "success": False,
                    "message": "Authentication required",
                }, 401

            user_id = current_user["id"]

            # Check capacity
            can_refer, message = self.service.check_referral_capacity(user_id)

            # Get full stats for detailed info
            stats = self.service.get_referral_stats(user_id)

            return {
                "success": True,
                "referrals_remaining": stats.get("referrals_remaining", 0),
                "referral_limit": stats.get("referral_limit", 0),
                "can_refer": can_refer,
                "message": message,
            }, 200

        except Exception as e:
            logging.error(f"Error getting referral limits: {str(e)}")
            return {
                "success": False,
                "message": "Error retrieving referral limits",
            }, 500


class ReferralUsageAPI(Resource):
    """API for getting feature usage limits"""

    def __init__(self):
        self.usage_service = UsageLimitService()

    @jwt_required
    def get(self, current_user=None):
        """
        GET /api/referrals/usage

        Get current user's daily feature usage limits

        Response (for referral_limited accounts):
            - success: Boolean
            - account_type: "full" or "referral_limited"
            - unlimited: Boolean
            - features: Dict with usage info per feature
            - last_reset: Last reset date

        Response (for full accounts):
            - success: Boolean
            - account_type: "full"
            - unlimited: True
        """
        try:
            if not current_user:
                return {
                    "success": False,
                    "message": "Authentication required",
                }, 401

            user_id = current_user["id"]

            # Get usage summary
            usage_summary = self.usage_service.get_usage_summary(user_id)

            if "error" in usage_summary:
                return {
                    "success": False,
                    "message": usage_summary["error"],
                }, 404

            return {
                "success": True,
                **usage_summary,
            }, 200

        except Exception as e:
            logging.error(f"Error getting usage limits: {str(e)}")
            return {
                "success": False,
                "message": "Error retrieving usage limits",
            }, 500
