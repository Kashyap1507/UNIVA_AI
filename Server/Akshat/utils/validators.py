"""
Validation utilities
"""

import re

from email_validator import EmailNotValidError
from email_validator import validate_email as email_validate


def validate_email(email):
    """Validate email format"""
    try:
        # Use email-validator library for robust validation
        valid = email_validate(email)
        return True
    except EmailNotValidError:
        return False


def validate_verification_code(code):
    """Validate verification code format"""
    if not code:
        return False

    # Should be 6 digits - Fixed the regex pattern
    if not re.match(r"^\d{6}$", code):
        return False

    return True
