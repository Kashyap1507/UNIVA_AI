"""
Mock Email Service for Development
Prints verification codes to console instead of sending emails
"""

import logging


class MockEmailService:
    """Mock email service that prints codes to console for development"""

    def __init__(self):
        self.from_email = "dev@wizly.ai"
        self.frontend_url = "http://localhost:3000"
        print("🚀 Using Mock Email Service (Development Mode)")
        print(
            "📧 Verification codes will be printed to console instead of sent via email"
        )

    def send_verification_email(self, to_email, verification_code):
        """Mock send verification email - prints to console"""
        try:
            print("\n" + "=" * 60)
            print("📧 MOCK EMAIL SERVICE")
            print("=" * 60)
            print(f"📬 To: {to_email}")
            print(f"📋 Subject: Your Univa AI sign-in code")
            print(f"🔢 Verification Code: {verification_code}")
            print("⏰ Expires in: 5 minutes")
            print("=" * 60)
            print("💡 Use this code to verify your account")
            print("=" * 60 + "\n")

            logging.info(
                f"Mock verification email 'sent' to {to_email} with code {verification_code}"
            )
            return True

        except Exception as e:
            logging.error(f"Error in mock email service: {str(e)}")
            return False

    def send_verification_email_with_token(
        self, email, verification_code, verification_token=None
    ):
        """Mock send verification email with token"""
        verification_link = (
            f"{self.frontend_url}/verify?token={verification_token}"
            if verification_token
            else "No link"
        )

        print("\n" + "=" * 60)
        print("📧 MOCK EMAIL SERVICE")
        print("=" * 60)
        print(f"📬 To: {email}")
        print(f"📋 Subject: Your Univa AI sign-in code")
        print(f"🔢 Verification Code: {verification_code}")
        print(f"🔗 Verification Link: {verification_link}")
        print("⏰ Expires in: 5 minutes")
        print("=" * 60)
        print("💡 Use either the code or click the link to verify")
        print("=" * 60 + "\n")

        logging.info(f"Mock verification email with token 'sent' to {email}")
        return True

    def send_welcome_email(self, email, first_name=None):
        """Mock send welcome email"""
        print("\n" + "=" * 50)
        print("🎉 WELCOME EMAIL")
        print("=" * 50)
        print(f"📬 To: {email}")
        print(f"📋 Subject: Welcome to Univa AI{', ' + first_name if first_name else ''}!")
        print("=" * 50 + "\n")

        logging.info(f"Mock welcome email 'sent' to {email}")
        return True
