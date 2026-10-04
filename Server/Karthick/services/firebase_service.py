"""
Firebase Service - Fixed with better error handling
Includes Firestore and Firebase Storage initialization
"""

import logging
import os

import firebase_admin
from firebase_admin import auth, credentials, firestore, storage
from flask import current_app

# Global Firestore client
db = None

# Global Firebase Storage bucket
storage_bucket = None

# Firebase Storage bucket name (your Firebase Storage bucket)
FIREBASE_STORAGE_BUCKET = "wizly-ai-2026.firebasestorage.app"


def initialize_firebase(app=None):
    """Initialize Firebase Admin SDK with better error handling"""
    global db, storage_bucket

    # Check if Firebase initialization should be skipped (e.g., in test environment)
    skip_firebase = os.environ.get("SKIP_FIREBASE_INIT", "").lower() in (
        "true",
        "1",
        "yes",
    )
    if skip_firebase:
        logging.warning("Skipping Firebase initialization (SKIP_FIREBASE_INIT=true)")
        return

    try:
        if not firebase_admin._apps:
            # Get config from app or environment variables
            if app:
                service_account_path = app.config["FIREBASE_SERVICE_ACCOUNT_PATH"]
                database_url = app.config.get("FIREBASE_DATABASE_URL")
            else:
                # Fallback to environment variables
                service_account_path = os.environ.get(
                    "FIREBASE_SERVICE_ACCOUNT_PATH", "firebase-service-account.json"
                )
                database_url = os.environ.get("FIREBASE_DATABASE_URL")

            # Check if running in Google Cloud (use Application Default Credentials)
            # Or if service account file exists
            if os.path.exists(service_account_path):
                # Local development with service account file
                cred = credentials.Certificate(service_account_path)
                logging.info("Using Firebase service account file for authentication")
            else:
                # Production: Use Application Default Credentials (Google Cloud)
                try:
                    cred = credentials.ApplicationDefault()
                    logging.info("Using Application Default Credentials for Firebase")
                except Exception as adc_error:
                    logging.error(f"Failed to use Application Default Credentials: {adc_error}")
                    raise FileNotFoundError(
                        f"Firebase service account file not found: {service_account_path}. "
                        f"Also failed to use Application Default Credentials in production."
                    )

            # Initialize with Storage bucket configuration
            firebase_admin.initialize_app(
                cred, {"storageBucket": FIREBASE_STORAGE_BUCKET}
            )

        # Try to initialize Firestore with error handling
        try:
            db = firestore.client()
            # Test the connection
            test_doc = db.collection("_test").document("_connection_test")
            test_doc.set({"test": True})
            test_doc.delete()  # Clean up test document
            logging.info("Firebase Firestore initialized successfully")
        except Exception as firestore_error:
            logging.error(f"Firestore initialization failed: {str(firestore_error)}")
            if "SERVICE_DISABLED" in str(firestore_error):
                logging.error(
                    "Cloud Firestore API is not enabled. Please enable it in Firebase Console."
                )
            elif "PERMISSION_DENIED" in str(firestore_error):
                logging.error(
                    "Firestore permissions issue. Check your Firebase rules and service account permissions."
                )
            else:
                logging.error(
                    "Unknown Firestore error. Check your Firebase configuration."
                )
            raise

        # Initialize Firebase Storage bucket
        try:
            storage_bucket = storage.bucket()
            logging.info(f"Firebase Storage initialized: {FIREBASE_STORAGE_BUCKET}")
        except Exception as storage_error:
            logging.warning(
                f"Firebase Storage initialization warning: {str(storage_error)}"
            )
            # Storage is optional - don't raise, just warn

    except Exception as e:
        logging.error(f"Failed to initialize Firebase: {str(e)}")
        raise


def get_storage_bucket():
    """Get Firebase Storage bucket instance"""
    global storage_bucket
    if storage_bucket is None:
        # Try to initialize if not already done
        try:
            storage_bucket = storage.bucket()
        except Exception as e:
            logging.error(f"Failed to get Storage bucket: {str(e)}")
            raise RuntimeError("Firebase Storage not initialized")
    return storage_bucket


def get_firestore_client():
    """Get Firestore client instance"""
    if db is None:
        logging.error(
            "Firestore client not initialized. Call initialize_firebase() first."
        )
        raise RuntimeError("Firestore not initialized")
    return db


def verify_firebase_token(token):
    """Verify Firebase ID token from auth providers"""
    try:
        decoded_token = auth.verify_id_token(token)
        return decoded_token
    except Exception as e:
        logging.error(f"Token verification failed: {str(e)}")
        return None


class FirestoreService:
    """Updated service class using models with better error handling"""

    def __init__(self):
        try:
            self.db = get_firestore_client()
        except Exception as e:
            logging.error(f"Failed to initialize FirestoreService: {str(e)}")
            raise

    # User operations
    def create_user(self, user_data):
        """Create a new user using User model"""
        from app.models.user import User

        try:
            user = User(**user_data)
            user.save(self.db)
            return user.to_api_dict()
        except Exception as e:
            logging.error(f"Error creating user: {str(e)}")
            if "SERVICE_DISABLED" in str(e):
                raise RuntimeError(
                    "Firestore API is disabled. Please enable it in Firebase Console."
                )
            elif "PERMISSION_DENIED" in str(e):
                raise RuntimeError("Firestore permission denied. Check Firebase rules.")
            else:
                raise RuntimeError(f"Database error: {str(e)}")

    def get_user_by_email(self, email):
        """Get user by email using User model"""
        from app.models.user import User

        try:
            user = User.get_by_email(self.db, email)
            return user.to_api_dict() if user else None
        except Exception as e:
            logging.error(f"Error getting user by email: {str(e)}")
            if "SERVICE_DISABLED" in str(e):
                raise RuntimeError(
                    "Firestore API is disabled. Please enable it in Firebase Console."
                )
            else:
                raise RuntimeError(f"Database error: {str(e)}")

    def get_user_by_id(self, user_id):
        """Get user by ID using User model"""
        from app.models.user import User

        try:
            user = User.get_by_id(self.db, user_id)
            return user.to_api_dict() if user else None
        except Exception as e:
            logging.error(f"Error getting user by ID: {str(e)}")
            raise RuntimeError(f"Database error: {str(e)}")

    def update_user(self, user_id, update_data):
        """Update user using User model"""
        from app.models.user import User

        try:
            user = User.get_by_id(self.db, user_id)
            if not user:
                raise ValueError("User not found")

            # Update attributes
            for key, value in update_data.items():
                if hasattr(user, key):
                    setattr(user, key, value)

            user.save(self.db)
            return True
        except Exception as e:
            logging.error(f"Error updating user: {str(e)}")
            raise RuntimeError(f"Database error: {str(e)}")

    # Verification codes
    def store_verification_code(self, email, code, expires_at, purpose="signup"):
        """Store email verification code using EmailVerification model"""
        from app.models.verification import EmailVerification

        try:
            verification = EmailVerification.create_verification(
                self.db,
                email,
                code,
                purpose,
                expiry_minutes=5,  # Convert expires_at to minutes if needed
            )
            return True
        except Exception as e:
            logging.error(f"Error storing verification code: {str(e)}")
            raise RuntimeError(f"Database error: {str(e)}")

    def verify_code(self, email, code):
        """Verify email verification code using EmailVerification model"""
        from app.models.verification import EmailVerification

        try:
            success, verification = EmailVerification.verify_code(self.db, email, code)
            return success
        except Exception as e:
            logging.error(f"Error verifying code: {str(e)}")
            raise RuntimeError(f"Database error: {str(e)}")

    def delete_verification_code(self, email):
        """Delete verification code using EmailVerification model"""
        from app.models.verification import EmailVerification

        try:
            EmailVerification.cleanup_expired_for_email(self.db, email)
            return True
        except Exception as e:
            logging.error(f"Error deleting verification code: {str(e)}")
            raise RuntimeError(f"Database error: {str(e)}")
