"""
Helper utility functions
"""

import secrets
import string


def generate_secure_token(length=32):
    """Generate a cryptographically secure random token"""
    return secrets.token_urlsafe(length)


def generate_verification_code(length=6):
    """Generate a numeric verification code"""
    return "".join(secrets.choice(string.digits) for _ in range(length))
