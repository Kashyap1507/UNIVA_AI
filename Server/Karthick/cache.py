"""
Cache Configuration for the Wizly AI server
Provides centralized caching infrastructure using Redis + Flask-Caching
"""

import logging
import os

from flask import Flask
from flask_caching import Cache

logger = logging.getLogger(__name__)

# Initialize cache instance (will be configured with app)
cache = Cache()


def init_cache(app: Flask) -> Cache:
    """
    Initialize and configure caching for the Flask app

    Supports multiple cache backends:
    - Redis (production): Distributed, persistent, scalable
    - SimpleCache (development): In-memory, single process
    - NullCache (testing): No caching

    Args:
        app: Flask application instance

    Returns:
        Configured Cache instance
    """

    # Get cache configuration from environment
    cache_type = os.getenv("CACHE_TYPE", "simple").lower()
    redis_url = os.getenv(
        "REDIS_URL", os.getenv("CACHE_REDIS_URL", "redis://localhost:6379/0")
    )
    default_timeout = int(os.getenv("CACHE_DEFAULT_TIMEOUT", "3600"))  # 1 hour default

    # Configure cache based on type
    cache_config = {
        "CACHE_DEFAULT_TIMEOUT": default_timeout,
        "CACHE_KEY_PREFIX": "wizly_",  # Namespace for cache keys
    }

    if cache_type == "redis":
        # Redis configuration (production)
        cache_config.update(
            {
                "CACHE_TYPE": "RedisCache",
                "CACHE_REDIS_URL": redis_url,
                "CACHE_OPTIONS": {
                    "socket_connect_timeout": 2,  # Connection timeout
                    "socket_timeout": 2,  # Read/write timeout
                    "retry_on_timeout": True,  # Retry on timeout
                    "health_check_interval": 30,  # Health check every 30s
                },
            }
        )
        logger.info(f"Initializing Redis cache: {redis_url}")

    elif cache_type == "simple":
        # Simple in-memory cache (development)
        cache_config.update(
            {
                "CACHE_TYPE": "SimpleCache",
            }
        )
        logger.info("Initializing SimpleCache (in-memory, single process)")
        logger.warning("SimpleCache is not suitable for production - use Redis instead")

    elif cache_type == "null":
        # Null cache (testing/disabled)
        cache_config.update(
            {
                "CACHE_TYPE": "NullCache",
            }
        )
        logger.info("Cache disabled (NullCache)")

    else:
        # Fallback to simple cache
        logger.warning(
            f"Unknown CACHE_TYPE '{cache_type}', falling back to SimpleCache"
        )
        cache_config.update(
            {
                "CACHE_TYPE": "SimpleCache",
            }
        )

    # Initialize cache with app
    cache.init_app(app, config=cache_config)

    # Test cache connection
    try:
        # Try to set and get a test value
        cache.set("_test_key", "test_value", timeout=10)
        test_result = cache.get("_test_key")
        if test_result == "test_value":
            logger.info("✅ Cache initialized successfully and connection verified")
            cache.delete("_test_key")
        else:
            logger.warning("⚠️ Cache initialized but test failed")
    except Exception as e:
        logger.error(f"❌ Cache initialization failed: {str(e)}")
        logger.error("Application will continue without caching")

    return cache


def get_cache() -> Cache:
    """
    Get the configured cache instance

    Returns:
        Cache instance
    """
    return cache


def clear_all_cache():
    """
    Clear all cached data

    Use with caution - this clears the entire cache
    """
    try:
        cache.clear()
        logger.info("🧹 All cache cleared successfully")
        return True
    except Exception as e:
        logger.error(f"Error clearing cache: {str(e)}")
        return False


def clear_cache_by_pattern(pattern: str):
    """
    Clear cache entries matching a pattern

    Args:
        pattern: Pattern to match (e.g., 'hierarchy:*')

    Note: This only works with Redis backend
    """
    try:
        # Get Redis client
        if hasattr(cache.cache, "_write_client"):
            redis_client = cache.cache._write_client
            # Add prefix to pattern
            full_pattern = f"{cache.config['CACHE_KEY_PREFIX']}{pattern}"

            # Find and delete matching keys
            keys = redis_client.keys(full_pattern)
            if keys:
                redis_client.delete(*keys)
                logger.info(
                    f"🧹 Cleared {len(keys)} cache entries matching pattern: {pattern}"
                )
                return len(keys)
            else:
                logger.info(f"No cache entries found for pattern: {pattern}")
                return 0
        else:
            # Fallback for non-Redis backends - clear all
            logger.warning(
                "Pattern-based cache clearing only supported with Redis, clearing all cache"
            )
            cache.clear()
            return -1

    except Exception as e:
        logger.error(f"Error clearing cache by pattern '{pattern}': {str(e)}")
        return -1


def get_cache_stats() -> dict:
    """
    Get cache statistics

    Returns:
        Dictionary with cache statistics
    """
    stats = {
        "cache_type": cache.config.get("CACHE_TYPE", "unknown"),
        "default_timeout": cache.config.get("CACHE_DEFAULT_TIMEOUT", 0),
        "key_prefix": cache.config.get("CACHE_KEY_PREFIX", ""),
    }

    # Redis-specific stats
    if hasattr(cache.cache, "_write_client"):
        try:
            redis_client = cache.cache._write_client
            info = redis_client.info()
            stats.update(
                {
                    "redis_version": info.get("redis_version"),
                    "used_memory_human": info.get("used_memory_human"),
                    "connected_clients": info.get("connected_clients"),
                    "total_commands_processed": info.get("total_commands_processed"),
                    "keyspace_hits": info.get("keyspace_hits", 0),
                    "keyspace_misses": info.get("keyspace_misses", 0),
                }
            )

            # Calculate hit rate
            hits = info.get("keyspace_hits", 0)
            misses = info.get("keyspace_misses", 0)
            total = hits + misses
            if total > 0:
                stats["hit_rate"] = round((hits / total) * 100, 2)
            else:
                stats["hit_rate"] = 0.0

            # Count keys with prefix
            pattern = f"{cache.config['CACHE_KEY_PREFIX']}*"
            keys = redis_client.keys(pattern)
            stats["total_keys"] = len(keys)

        except Exception as e:
            logger.error(f"Error getting Redis stats: {str(e)}")
            stats["error"] = str(e)

    return stats
