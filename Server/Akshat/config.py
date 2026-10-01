"""
Application Configuration
"""

import os

from dotenv import load_dotenv

load_dotenv()


class Config:
    """Base configuration class"""

    # Flask settings
    # Security: SECRET_KEY must be set in environment - no insecure defaults
    SECRET_KEY = os.environ.get("SECRET_KEY")
    if not SECRET_KEY:
        raise ValueError("SECRET_KEY environment variable must be set for security")

    # Firebase settings
    FIREBASE_SERVICE_ACCOUNT_PATH = os.environ.get(
        "FIREBASE_SERVICE_ACCOUNT_PATH", "firebase-service-account.json"
    )
    FIREBASE_DATABASE_URL = os.environ.get("FIREBASE_DATABASE_URL")

    # JWT settings
    # Security: JWT_SECRET_KEY must be set in environment - no insecure defaults
    JWT_SECRET_KEY = os.environ.get("JWT_SECRET_KEY")
    if not JWT_SECRET_KEY:
        raise ValueError("JWT_SECRET_KEY environment variable must be set for security")
    JWT_ACCESS_TOKEN_EXPIRES = 3600 * 24 * 7  # 7 days

    # Email settings
    SENDGRID_API_KEY = os.environ.get("SENDGRID_API_KEY")
    FROM_EMAIL = os.environ.get("FROM_EMAIL", "noreply@localhost")  # set FROM_EMAIL in production

    # CORS settings
    CORS_ORIGINS = (
        os.environ.get(
            "CORS_ORIGINS",
            "http://localhost:3000",
        ).split(",")
        if os.environ.get("CORS_ORIGINS") != "*"
        else ["*"]
    )

    # Reject request bodies larger than this (course material uploads are capped at 50 MB)
    MAX_CONTENT_LENGTH = 60 * 1024 * 1024

    # Verification code settings
    VERIFICATION_CODE_EXPIRY = 300  # 5 minutes
    VERIFICATION_CODE_LENGTH = 6

    # Rate limiting
    RATE_LIMIT_SIGNUP = 5  # per hour
    RATE_LIMIT_LOGIN = 10  # per hour
    RATE_LIMIT_VERIFY = 3  # per minute


class DevelopmentConfig(Config):
    """Development configuration"""

    DEBUG = True
    CORS_ORIGINS = ["*"]

    # Development: Allow fallback secrets ONLY in development
    # Security: Override the base class validation for development environment only
    SECRET_KEY = os.environ.get(
        "SECRET_KEY", "dev-secret-key-INSECURE-change-in-production"
    )
    JWT_SECRET_KEY = os.environ.get("JWT_SECRET_KEY", SECRET_KEY)


class ProductionConfig(Config):
    """Production configuration"""

    DEBUG = False


class TestingConfig(Config):
    """Testing configuration"""

    TESTING = True
    DEBUG = True


class ReferralConfig:
    """Referral system configuration"""

    # Referral limits
    DEFAULT_REFERRAL_LIMIT = 5  # Max number of referrals per user
    REFERRAL_CODE_LENGTH = 6  # ABC1XY format
    REFERRAL_CODE_FORMAT = "XXX0XX"  # 3 letters + 1 digit + 2 letters

    # Feature limits for referred users (referral_limited account type)
    REFERRED_USER_LIMITS = {
        "work_checks_per_day": 3,
        "practice_sets_per_day": 2,
        "flashcard_sets_per_day": 10,
    }

    # Features that require usage limit checking
    FEATURES_REQUIRING_LIMITS = [
        "work_checks",
        "practice_sets",
        "flashcard_sets",
    ]

    # Characters excluded from referral codes (ambiguous)
    EXCLUDED_CHARS = ["0", "O", "1", "I", "l"]


config = {
    "development": DevelopmentConfig,
    "production": ProductionConfig,
    "testing": TestingConfig,
    "default": DevelopmentConfig,
}
