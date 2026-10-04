"""
Email verification model for managing verification codes and tokens
"""

import logging
import secrets
import string
from datetime import datetime, timedelta
from typing import Any, Dict, Optional

from .base import BaseModel


class EmailVerification(BaseModel):
    """Email verification model for managing verification codes and tokens"""

    collection_name = "email_verifications"

    def __init__(self, **kwargs):
        # Verification-specific attributes
        self.email: str = kwargs.get("email", "")
        self.code: str = kwargs.get("code", "")
        self.expires_at: datetime = kwargs.get("expires_at", datetime.utcnow())
        self.verified: bool = kwargs.get("verified", False)
        self.attempts: int = kwargs.get("attempts", 0)
        self.purpose: str = kwargs.get(
            "purpose", "signup"
        )  # signup, login, reset_password
        self.ip_address: Optional[str] = kwargs.get("ip_address")

        # NEW: Token-based verification
        self.verification_token: Optional[str] = kwargs.get("verification_token")

        # NEW: Temporary signup data (for signup verifications only)
        self.signup_data: Optional[Dict[str, Any]] = kwargs.get("signup_data")

        # Call the base class constructor
        super().__init__(**kwargs)

    def validate(self) -> tuple[bool, str]:
        """Validate verification data"""
        # Validate email
        if not self.email:
            return False, "Email is required"

        # Validate code
        if not self.code:
            return False, "Verification code is required"

        if len(self.code) != 6 or not self.code.isdigit():
            return False, "Verification code must be 6 digits"

        # Validate purpose
        valid_purposes = ["signup", "login", "reset_password"]
        if self.purpose not in valid_purposes:
            return False, f"Purpose must be one of: {', '.join(valid_purposes)}"

        # For signup purpose, signup_data should be present
        if self.purpose == "signup" and not self.signup_data:
            return False, "Signup data is required for signup verification"

        return True, "Valid verification data"

    def is_expired(self) -> bool:
        """Check if verification code is expired"""
        from datetime import timezone

        if self.expires_at.tzinfo is None:
            expires_at = self.expires_at.replace(tzinfo=timezone.utc)
        else:
            expires_at = self.expires_at

        now = datetime.now(timezone.utc)
        return now > expires_at

    def increment_attempts(self):
        """Increment verification attempts"""
        self.attempts += 1
        self.updated_at = datetime.utcnow()

    @staticmethod
    def generate_verification_token(length=32) -> str:
        """Generate a secure verification token"""
        alphabet = string.ascii_letters + string.digits
        return "".join(secrets.choice(alphabet) for _ in range(length))

    @classmethod
    def create_verification(
        cls,
        db,
        email: str,
        code: str,
        purpose: str = "signup",
        expiry_minutes: int = 5,
        ip_address: Optional[str] = None,
        signup_data: Optional[Dict[str, Any]] = None,
    ):
        """Create a new verification record with optional signup data"""
        try:
            # Delete any existing verification for this email
            cls.cleanup_expired_for_email(db, email)

            expires_at = datetime.utcnow() + timedelta(minutes=expiry_minutes)
            verification_token = cls.generate_verification_token()

            verification = cls(
                email=email.lower(),
                code=code,
                expires_at=expires_at,
                purpose=purpose,
                ip_address=ip_address,
                verification_token=verification_token,
                signup_data=signup_data if purpose == "signup" else None,
            )

            # Use email as document ID for easy retrieval
            data = verification.to_dict()
            data["created_at"] = datetime.utcnow()
            data["updated_at"] = datetime.utcnow()

            doc_ref = db.collection(cls.collection_name).document(email.lower())
            doc_ref.set(data)

            verification.id = email.lower()
            return verification
        except Exception as e:
            logging.error(f"Error creating verification: {str(e)}")
            raise

    @classmethod
    def get_by_email(cls, db, email: str):
        """Get verification record by email"""
        try:
            doc_ref = db.collection(cls.collection_name).document(email.lower())
            doc = doc_ref.get()

            if doc.exists:
                return cls.from_dict(doc.to_dict(), doc.id)
            return None
        except Exception as e:
            logging.error(f"Error getting verification by email: {str(e)}")
            raise

    @classmethod
    def get_by_token(cls, db, token: str):
        """Get verification record by token"""
        try:
            verifications_ref = db.collection(cls.collection_name)
            query = verifications_ref.where("verification_token", "==", token).limit(1)
            docs = query.stream()

            for doc in docs:
                return cls.from_dict(doc.to_dict(), doc.id)

            return None
        except Exception as e:
            logging.error(f"Error getting verification by token: {str(e)}")
            raise

    @classmethod
    def verify_code(
        cls, db, email: str, code: str
    ) -> tuple[bool, Optional["EmailVerification"]]:
        """Verify code for email"""
        try:
            verification = cls.get_by_email(db, email)

            if not verification:
                return False, None

            # Check if expired
            if verification.is_expired():
                return False, None

            # Check attempts limit (prevent brute force)
            if verification.attempts >= 5:
                return False, None

            # Verify code
            if verification.code != code:
                # Increment attempts and save
                verification.increment_attempts()
                data = verification.to_dict()
                doc_ref = db.collection(cls.collection_name).document(email.lower())
                doc_ref.update(
                    {
                        "attempts": verification.attempts,
                        "updated_at": verification.updated_at,
                    }
                )
                return False, None

            # Mark as verified
            verification.verified = True
            verification.updated_at = datetime.utcnow()

            data = verification.to_dict()
            doc_ref = db.collection(cls.collection_name).document(email.lower())
            doc_ref.update({"verified": True, "updated_at": verification.updated_at})

            return True, verification
        except Exception as e:
            logging.error(f"Error verifying code: {str(e)}")
            raise

    @classmethod
    def verify_token(cls, db, token: str) -> tuple[bool, Optional["EmailVerification"]]:
        """Verify using token (for email link verification)"""
        try:
            verification = cls.get_by_token(db, token)

            if not verification:
                return False, None

            # Check if expired
            if verification.is_expired():
                return False, None

            # Mark as verified
            verification.verified = True
            verification.updated_at = datetime.utcnow()

            data = verification.to_dict()
            doc_ref = db.collection(cls.collection_name).document(verification.email)
            doc_ref.update({"verified": True, "updated_at": verification.updated_at})

            return True, verification
        except Exception as e:
            logging.error(f"Error verifying token: {str(e)}")
            raise

    @classmethod
    def cleanup_expired_for_email(cls, db, email: str):
        """Remove expired verification records for email"""
        try:
            doc_ref = db.collection(cls.collection_name).document(email.lower())
            doc = doc_ref.get()

            if doc.exists:
                verification = cls.from_dict(doc.to_dict(), doc.id)
                if verification.is_expired() or verification.verified:
                    doc_ref.delete()
                    logging.info(f"Cleaned up verification record for {email}")
        except Exception as e:
            logging.error(f"Error cleaning up verification: {str(e)}")
            raise
