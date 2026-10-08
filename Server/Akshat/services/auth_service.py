import logging
import secrets
import string
from datetime import datetime, timedelta

import jwt
from flask import current_app

from app.models.user import User
from app.models.verification import EmailVerification
from app.services.email_service import EmailService
from app.services.firebase_service import get_firestore_client, verify_firebase_token
from app.services.mock_email_service import MockEmailService

# Import the Activity model only if it exists
try:
    from app.models.activity import Activity
except ImportError:
    Activity = None


class AuthService:
    """Updated service class for authentication operations using models"""

    def __init__(self):
        self.db = get_firestore_client()
        # Use mock email service in development if SendGrid is not configured
        sendgrid_key = current_app.config.get("SENDGRID_API_KEY")
        if not sendgrid_key or sendgrid_key == "disabled-for-development":
            self.email_service = MockEmailService()
        else:
            self.email_service = EmailService()

    def generate_jwt_token(self, user_data):
        """Generate JWT token for user"""
        try:
            payload = {
                "user_id": user_data["id"],
                "email": user_data["email"],
                "exp": datetime.utcnow()
                + timedelta(seconds=current_app.config["JWT_ACCESS_TOKEN_EXPIRES"]),
                "iat": datetime.utcnow(),
            }

            token = jwt.encode(
                payload, current_app.config["JWT_SECRET_KEY"], algorithm="HS256"
            )

            return token
        except Exception as e:
            logging.error(f"Error generating JWT token: {str(e)}")
            raise

    def verify_jwt_token(self, token):
        """Verify JWT token and return user data"""
        try:
            payload = jwt.decode(
                token, current_app.config["JWT_SECRET_KEY"], algorithms=["HS256"]
            )

            user = User.get_by_id(self.db, payload["user_id"])
            return user.to_api_dict() if user else None
        except jwt.ExpiredSignatureError:
            return None
        except jwt.InvalidTokenError:
            return None
        except Exception as e:
            logging.error(f"Error verifying JWT token: {str(e)}")
            return None

    def signup_with_email(self, email, referral_code=None, **_ignored):
        """Handle email signup flow using models with token-based verification

        Args:
            referral_code: Optional referral code (nullable - user can skip)
        """
        try:
            # Check if user already exists
            existing_user = User.get_by_email(self.db, email)

            if existing_user:
                return {
                    "success": False,
                    "message": "User already exists with this email",
                    "requires_verification": False,
                }

            # Validate referral code if provided (optional)
            referrer_user_id = None
            logging.info(f"🎟️  Referral code received in signup: {referral_code}")
            if referral_code:
                from app.services.referral_service import ReferralService
                referral_service = ReferralService()
                is_valid, referrer_info, message = referral_service.validate_referral_code(referral_code)
                logging.info(f"🎟️  Referral code validation: valid={is_valid}, message={message}")

                if not is_valid:
                    return {
                        "success": False,
                        "message": f"Invalid referral code: {message}",
                        "requires_verification": False,
                    }

                referrer_user_id = referrer_info["user_id"]
                logging.info(f"Valid referral code: {referral_code} from user {referrer_user_id}")

            # Generate and send verification code
            verification_code = self.generate_verification_code()

            # Prepare signup data to store temporarily
            signup_data = {
                "referral_code": referral_code,  # Store referral code for later
                "referrer_user_id": referrer_user_id,  # Store referrer ID
            }

            # Store verification code with signup data using model
            verification = EmailVerification.create_verification(
                self.db, email, verification_code, "signup", signup_data=signup_data
            )

            # Send verification email with both code and token
            email_sent = self.email_service.send_verification_email(
                email,
                verification_code,
                # verification_token=verification.verification_token
            )

            if not email_sent:
                return {
                    "success": False,
                    "message": "Failed to send verification email",
                    "requires_verification": False,
                }

            return {
                "success": True,
                "message": "Verification code sent to your email. You can verify by entering the code or clicking the link in your email.",
                "requires_verification": True,
                "email": email,
            }
        except Exception as e:
            logging.error(f"Error in email signup: {str(e)}")
            raise

    def login_with_email(self, email):
        """Handle email login flow using models"""
        try:
            # Check if user exists
            existing_user = User.get_by_email(self.db, email)

            if not existing_user:
                return {
                    "success": False,
                    "message": "No account found with this email",
                    "requires_verification": False,
                }

            # Generate and send verification code
            verification_code = self.generate_verification_code()

            # Store verification code using model
            EmailVerification.create_verification(
                self.db, email, verification_code, "login"
            )

            # Send verification email
            email_sent = self.email_service.send_verification_email(
                email, verification_code
            )

            if not email_sent:
                return {
                    "success": False,
                    "message": "Failed to send verification email",
                    "requires_verification": False,
                }

            return {
                "success": True,
                "message": "Verification code sent to your email",
                "requires_verification": True,
                "email": email,
            }
        except Exception as e:
            logging.error(f"Error in email login: {str(e)}")
            raise

    def verify_email_and_create_user(self, email, code):
        """Simplified verification - only requires email and code"""
        try:
            # Verify the code using model
            success, verification = EmailVerification.verify_code(self.db, email, code)

            if not success:
                return {
                    "success": False,
                    "message": "Invalid or expired verification code",
                }

            # Check if this is a signup verification
            if verification.purpose != "signup" or not verification.signup_data:
                return {"success": False, "message": "Invalid verification type"}

            signup_data = verification.signup_data
            referral_code = signup_data.get("referral_code")
            referrer_user_id = signup_data.get("referrer_user_id")

            logging.info(f"🎟️  Creating user - Referral code from verification: {referral_code}, Referrer ID: {referrer_user_id}")

            # Generate unique referral code for new user
            from app.services.referral_service import ReferralService
            referral_service = ReferralService()
            user_referral_code = referral_service.generate_unique_code()

            # Set account type based on whether they used a referral code
            account_type = "referral_limited" if referral_code else "full"

            # Create user using model
            user = User(
                email=email.lower(),
                auth_provider="email",
                verified=True,
                referral_code=user_referral_code,  # Every user gets a code
                account_type=account_type,  # Limited if used referral code
            )
            user.save(self.db)

            # Process referral if code was provided
            if referral_code and referrer_user_id:
                # Track referral relationship
                referral_service.track_referral(user.id, referrer_user_id, referral_code)
                # Consume one referral slot from referrer
                referral_service.consume_referral_slot(referrer_user_id)
                logging.info(f"Processed referral: {user.id} referred by {referrer_user_id}")


            # Generate JWT token
            token = self.generate_jwt_token(user.to_api_dict())

            # Log signup activity if Activity model exists
            if Activity:
                Activity.log_activity(
                    self.db,
                    user.id,
                    "user_signup",
                    activity_data={"auth_provider": "email"},
                )

            # Clean up verification code
            EmailVerification.cleanup_expired_for_email(self.db, email)

            return {
                "success": True,
                "message": "Account created successfully",
                "token": token,
                "user": user.to_api_dict(),
            }
        except Exception as e:
            logging.error(f"Error in email verification: {str(e)}")
            raise

    def verify_email_with_token(self, token):
        """Verify email using token (for email link clicks)"""
        try:
            # Verify the token using model
            success, verification = EmailVerification.verify_token(self.db, token)

            if not success:
                return {
                    "success": False,
                    "message": "Invalid or expired verification link",
                }

            if verification.purpose == "signup":
                # Handle signup completion
                if not verification.signup_data:
                    return {"success": False, "message": "Invalid signup data"}

                signup_data = verification.signup_data
                referral_code = signup_data.get("referral_code")
                referrer_user_id = signup_data.get("referrer_user_id")

                # Generate unique referral code for new user
                from app.services.referral_service import ReferralService
                referral_service = ReferralService()
                user_referral_code = referral_service.generate_unique_code()

                # Set account type based on whether they used a referral code
                account_type = "referral_limited" if referral_code else "full"

                # Create user using model
                user = User(
                    email=verification.email.lower(),
                    auth_provider="email",
                    verified=True,
                    referral_code=user_referral_code,
                    account_type=account_type,
                )
                user.save(self.db)

                # Process referral if code was provided
                if referral_code and referrer_user_id:
                    referral_service.track_referral(user.id, referrer_user_id, referral_code)
                    referral_service.consume_referral_slot(referrer_user_id)
                    logging.info(f"Processed referral via token: {user.id} referred by {referrer_user_id}")


                # Generate JWT token
                token = self.generate_jwt_token(user.to_api_dict())

                # Log signup activity
                if Activity:
                    Activity.log_activity(
                        self.db,
                        user.id,
                        "user_signup",
                        activity_data={
                            "auth_provider": "email",
                            "verification_method": "email_link",
                        },
                    )

                # Clean up verification code
                EmailVerification.cleanup_expired_for_email(self.db, verification.email)

                return {
                    "success": True,
                    "message": "Account created successfully",
                    "token": token,
                    "user": user.to_api_dict(),
                }

            elif verification.purpose == "login":
                # Handle login completion
                user = User.get_by_email(self.db, verification.email)

                if not user:
                    return {"success": False, "message": "User not found"}

                # Update last login
                user.update_last_login()
                user.save(self.db)

                # Generate JWT token
                token = self.generate_jwt_token(user.to_api_dict())

                # Log login activity
                if Activity:
                    Activity.log_activity(
                        self.db,
                        user.id,
                        "user_login",
                        activity_data={
                            "auth_provider": "email",
                            "verification_method": "email_link",
                        },
                    )

                # Clean up verification code
                EmailVerification.cleanup_expired_for_email(self.db, verification.email)

                return {
                    "success": True,
                    "message": "Login successful",
                    "token": token,
                    "user": user.to_api_dict(),
                }

            else:
                return {
                    "success": False,
                    "message": f"Unsupported verification purpose: {verification.purpose}",
                }

        except Exception as e:
            logging.error(f"Error in token verification: {str(e)}")
            raise

    def verify_email_and_login(self, email, code):
        """Verify email code and login user"""
        try:
            # Verify the code using model
            success, verification = EmailVerification.verify_code(self.db, email, code)

            if not success:
                return {
                    "success": False,
                    "message": "Invalid or expired verification code",
                }

            # Get user
            user = User.get_by_email(self.db, email)

            if not user:
                return {"success": False, "message": "User not found"}

            # Update last login
            user.update_last_login()
            user.save(self.db)

            # Generate JWT token
            token = self.generate_jwt_token(user.to_api_dict())

            # Log login activity if Activity model exists
            if Activity:
                Activity.log_activity(
                    self.db,
                    user.id,
                    "user_login",
                    activity_data={"auth_provider": "email"},
                )

            # Clean up verification code
            EmailVerification.cleanup_expired_for_email(self.db, email)

            return {
                "success": True,
                "message": "Login successful",
                "token": token,
                "user": user.to_api_dict(),
            }
        except Exception as e:
            logging.error(f"Error in email login verification: {str(e)}")
            raise

    def auth_with_provider(
        self,
        firebase_token,
        referral_code=None,
        **_ignored,
    ):
        """Handle authentication with auth providers using models

        Args:
            referral_code: Optional referral code for new users
        """
        try:
            # Verify Firebase token
            decoded_token = verify_firebase_token(firebase_token)

            if not decoded_token:
                return {"success": False, "message": "Invalid authentication token"}

            email = decoded_token.get("email")
            name = decoded_token.get(
                "name"
            )  # Get display name from provider (Google, Facebook, etc.)
            firebase_provider = decoded_token.get("firebase", {}).get(
                "sign_in_provider", "unknown"
            )
            uid = decoded_token.get("uid")

            # Map Firebase provider names to our internal names
            provider_mapping = {
                "google.com": "google",
                "facebook.com": "facebook",
                "apple.com": "apple",
                "twitter.com": "twitter",
                "github.com": "github",
                "microsoft.com": "microsoft",
                "yahoo.com": "yahoo",
                "anonymous": "anonymous",
                "password": "email",  # Firebase email/password auth
            }

            # Convert Firebase provider to our internal provider name
            provider = provider_mapping.get(firebase_provider, firebase_provider)

            if not email:
                return {
                    "success": False,
                    "message": "Email not provided by auth provider",
                }

            # Check if user already exists
            existing_user = User.get_by_email(self.db, email)

            if existing_user:
                # User exists, login

                # Auto-populate name if not set (for existing users without names)
                # Only updates if name is None or empty, never overwrites user customizations
                if (
                    not existing_user.name or existing_user.name.strip() == ""
                ) and name:
                    existing_user.name = name

                existing_user.update_last_login()
                existing_user.save(self.db)

                token = self.generate_jwt_token(existing_user.to_api_dict())

                # Log login activity
                Activity.log_activity(
                    self.db,
                    existing_user.id,
                    "user_login",
                    activity_data={"auth_provider": provider},
                )

                return {
                    "success": True,
                    "message": "Login successful",
                    "token": token,
                    "user": existing_user.to_api_dict(),
                    "is_new_user": False,
                }
            else:
                # New user, create account
                # Validate referral code if provided (optional)
                referrer_user_id = None
                if referral_code:
                    from app.services.referral_service import ReferralService
                    referral_service = ReferralService()
                    is_valid, referrer_info, message = referral_service.validate_referral_code(referral_code)

                    if is_valid:
                        referrer_user_id = referrer_info["user_id"]
                        logging.info(f"Valid referral code in provider auth: {referral_code}")
                    else:
                        logging.warning(f"Invalid referral code ignored: {referral_code} - {message}")

                # Generate unique referral code for new user
                from app.services.referral_service import ReferralService
                referral_service = ReferralService()
                user_referral_code = referral_service.generate_unique_code()

                # Set account type based on whether they used a referral code
                account_type = "referral_limited" if referral_code else "full"

                user = User(
                    email=email.lower(),
                    name=name,  # Store name from provider
                    auth_provider=provider,  # Use mapped provider name
                    provider_uid=uid,
                    verified=True,
                    referral_code=user_referral_code,  # Every user gets a code
                    account_type=account_type,  # Limited if used referral code
                )
                user.save(self.db)

                # Process referral if code was provided and valid
                if referral_code and referrer_user_id:
                    referral_service.track_referral(user.id, referrer_user_id, referral_code)
                    referral_service.consume_referral_slot(referrer_user_id)
                    logging.info(f"Processed referral in provider auth: {user.id} referred by {referrer_user_id}")


                # Generate JWT token
                token = self.generate_jwt_token(user.to_api_dict())

                # Log signup activity
                Activity.log_activity(
                    self.db,
                    user.id,
                    "user_signup",
                    activity_data={"auth_provider": provider},
                )

                return {
                    "success": True,
                    "message": "Account created successfully",
                    "token": token,
                    "user": user.to_api_dict(),
                    "is_new_user": True,
                }
        except Exception as e:
            logging.error(f"Error in provider auth: {str(e)}")
            raise

    def generate_verification_code(self):
        """Generate cryptographically secure random verification code"""
        length = current_app.config["VERIFICATION_CODE_LENGTH"]
        # Security: Use secrets module for cryptographic randomness instead of random module
        return "".join(secrets.choice(string.digits) for _ in range(length))
