"""
Admin Cache Management API
Provides endpoints for cache monitoring and management
"""

import logging

from flask import request
from flask_restful import Resource

from app.cache import clear_all_cache, clear_cache_by_pattern, get_cache_stats
from app.services.admin_auth_service import admin_required

logger = logging.getLogger(__name__)


class AdminCacheResource(Resource):
    """Main cache management resource"""

    @admin_required
    def get(self):
        """
        Get cache information and available patterns

        Returns general cache info and list of available invalidation patterns
        """
        try:
            stats = get_cache_stats()

            response = {
                "success": True,
                "cache_stats": stats,
                "available_patterns": {
                    "all": "Clear entire cache",
                    "<prefix>:*": "Clear keys matching a pattern",
                },
                "endpoints": {
                    "stats": "GET /api/admin/cache/stats - Detailed statistics",
                    "clear": "DELETE /api/admin/cache/clear - Clear cache",
                    "clear_pattern": "DELETE /api/admin/cache/clear?pattern=<prefix>:* - Clear specific pattern",
                },
            }

            return response, 200

        except Exception as e:
            logger.error(f"Error getting cache info: {str(e)}")
            return {"error": "Internal server error"}, 500


class AdminCacheStatsResource(Resource):
    """Cache statistics endpoint"""

    @admin_required
    def get(self):
        """
        Get detailed cache statistics

        Returns comprehensive cache performance metrics
        """
        try:
            stats = get_cache_stats()

            # Add additional metrics
            cache_type = stats.get("cache_type", "unknown")

            response = {"success": True, "stats": stats, "recommendations": []}

            # Add recommendations based on stats
            if cache_type == "SimpleCache":
                response["recommendations"].append(
                    {
                        "type": "warning",
                        "message": "Using SimpleCache - not suitable for production. Consider Redis for better performance and scalability.",
                    }
                )

            if cache_type == "RedisCache":
                hit_rate = stats.get("hit_rate", 0)
                if hit_rate < 50:
                    response["recommendations"].append(
                        {
                            "type": "info",
                            "message": f"Cache hit rate is {hit_rate}% - consider increasing cache timeout or warming cache on startup.",
                        }
                    )
                elif hit_rate >= 80:
                    response["recommendations"].append(
                        {
                            "type": "success",
                            "message": f"Excellent cache hit rate: {hit_rate}%",
                        }
                    )

            return response, 200

        except Exception as e:
            logger.error(f"Error getting cache stats: {str(e)}")
            return {"error": "Internal server error"}, 500


class AdminCacheClearResource(Resource):
    """Cache clearing endpoint"""

    @admin_required
    def delete(self):
        """
        Clear cache entries

        Query Parameters:
            pattern (optional): Pattern to match for selective clearing
                              Example: tutor:*
                              Use 'all' or omit to clear entire cache

        Returns:
            Success status and number of entries cleared
        """
        try:
            pattern = request.args.get("pattern", "all")

            if pattern == "all" or not pattern:
                # Clear entire cache
                success = clear_all_cache()
                if success:
                    logger.info("Admin cleared entire cache")
                    return {
                        "success": True,
                        "message": "All cache cleared successfully",
                        "cleared_count": "all",
                    }, 200
                else:
                    return {"error": "Failed to clear cache"}, 500

            else:
                # Clear specific pattern
                actual_pattern = pattern

                cleared_count = clear_cache_by_pattern(actual_pattern)

                logger.info(
                    f"Admin cleared cache pattern: {actual_pattern} ({cleared_count} entries)"
                )

                return {
                    "success": True,
                    "message": f"Cache cleared for pattern: {pattern}",
                    "pattern": actual_pattern,
                    "cleared_count": cleared_count,
                }, 200

        except Exception as e:
            logger.error(f"Error clearing cache: {str(e)}")
            return {"error": "Internal server error"}, 500

    @admin_required
    def post(self):
        """
        Clear cache using POST with body

        Request Body:
            {
                "patterns": ["tutor:*"],  // List of patterns to clear
                "clear_all": false  // Set to true to clear entire cache
            }

        Returns:
            Success status and details of cleared patterns
        """
        try:
            data = request.get_json()

            if not data:
                return {"error": "Request body required"}, 400

            clear_all_flag = data.get("clear_all", False)
            patterns = data.get("patterns", [])

            results = []

            if clear_all_flag:
                success = clear_all_cache()
                return {
                    "success": success,
                    "message": (
                        "All cache cleared" if success else "Failed to clear cache"
                    ),
                    "cleared_count": "all",
                }, 200

            if not patterns:
                return {"error": "No patterns specified"}, 400

            # Clear each pattern
            total_cleared = 0

            for pattern in patterns:
                actual_pattern = pattern
                cleared_count = clear_cache_by_pattern(actual_pattern)
                total_cleared += cleared_count if cleared_count > 0 else 0

                results.append(
                    {
                        "pattern": pattern,
                        "actual_pattern": actual_pattern,
                        "cleared_count": cleared_count,
                    }
                )

            logger.info(
                f"Admin cleared {len(patterns)} cache patterns, total entries: {total_cleared}"
            )

            return {
                "success": True,
                "message": f"Cleared {len(patterns)} cache patterns",
                "total_cleared": total_cleared,
                "details": results,
            }, 200

        except Exception as e:
            logger.error(f"Error clearing cache: {str(e)}")
            return {"error": "Internal server error"}, 500
