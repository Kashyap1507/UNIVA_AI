"""
Email Service - Handles email sending operations
"""

import logging

import sendgrid
from flask import current_app
from sendgrid.helpers.mail import Content, Email, Mail, To


class EmailService:
    """Service class for email operations"""

    def __init__(self):
        self.sg = sendgrid.SendGridAPIClient(
            api_key=current_app.config.get("SENDGRID_API_KEY")
        )
        self.from_email = current_app.config.get("FROM_EMAIL")
        self.frontend_url = current_app.config.get(
            "FRONTEND_URL", "https://sparxlabs.ai"
        )

    def send_verification_email(self, to_email, verification_code):
        """Send verification code email"""
        try:
            # Create email content
            subject = "Verify Your Univa AI Account"

            html_content = f"""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Verify Your Account</title>
                <style>
                    body {{
                        font-family: Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                    }}
                    .container {{
                        background-color: #f9f9f9;
                        padding: 30px;
                        border-radius: 10px;
                        text-align: center;
                    }}
                    .logo {{
                        font-size: 24px;
                        font-weight: bold;
                        color: #4A90E2;
                        margin-bottom: 20px;
                    }}
                    .code {{
                        font-size: 36px;
                        font-weight: bold;
                        color: #2196F3;
                        background-color: #E3F2FD;
                        padding: 20px;
                        border-radius: 8px;
                        margin: 20px 0;
                        letter-spacing: 5px;
                    }}
                    .footer {{
                        margin-top: 30px;
                        font-size: 14px;
                        color: #666;
                    }}
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="logo">Univa AI</div>
                    <h2>Verify Your Account</h2>
                    <p>Welcome to Univa AI! Please use the verification code below to complete your account setup:</p>
                    
                    <div class="code">{verification_code}</div>
                    
                    <p>This code will expire in 5 minutes.</p>
                    <p>If you didn't request this code, please ignore this email.</p>
                    
                    <div class="footer">
                        <p>Best regards,<br>The Univa AI Team</p>
                    </div>
                </div>
            </body>
            </html>
            """

            text_content = f"""
            Welcome to Univa AI!
            
            Your verification code is: {verification_code}
            
            This code will expire in 5 minutes.
            
            If you didn't request this code, please ignore this email.
            
            Best regards,
            The Univa AI Team
            """

            # Create and send email
            mail = Mail(
                from_email=Email(self.from_email),
                to_emails=To(to_email),
                subject=subject,
                html_content=Content("text/html", html_content),
                plain_text_content=Content("text/plain", text_content),
            )

            response = self.sg.send(mail)

            if response.status_code in [200, 201, 202]:
                logging.info(f"Verification email sent successfully to {to_email}")
                return True
            else:
                logging.error(
                    f"Failed to send email. Status code: {response.status_code}"
                )
                return False

        except Exception as e:
            logging.error(f"Error sending verification email: {str(e)}")
            return False

    def send_verification_email_with_token(
        self, email, verification_code, verification_token=None
    ):
        """
        Send verification email with both code and optional token link

        Args:
            email: User's email address
            verification_code: 6-digit verification code
            verification_token: Optional token for email link verification
        """
        try:
            # Create verification link if token is provided
            verification_link = None
            if verification_token:
                verification_link = (
                    f"{self.frontend_url}/verify?token={verification_token}"
                )

            subject = "Verify Your Univa AI Account"

            # Enhanced HTML template with both options
            html_content = f"""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Verify Your Account</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; margin: 0; padding: 20px; background-color: #f4f4f4;">
                <div style="max-width: 600px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 10px; box-shadow: 0 0 10px rgba(0,0,0,0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <h1 style="color: #333; margin-bottom: 10px;">Welcome to Univa AI!</h1>
                        <p style="color: #666; font-size: 16px;">Please verify your email address to complete your account setup.</p>
                    </div>
                    
                    <div style="background-color: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0;">
                        <h2 style="color: #333; margin-top: 0;">Option 1: Enter Verification Code</h2>
                        <p style="color: #666;">Enter this 6-digit code in the app:</p>
                        <div style="text-align: center; margin: 20px 0;">
                            <span style="font-size: 32px; font-weight: bold; color: #007bff; letter-spacing: 8px; background-color: white; padding: 15px 25px; border-radius: 8px; border: 2px dashed #007bff;">{verification_code}</span>
                        </div>
                        <p style="color: #999; font-size: 14px; text-align: center;">Code expires in 5 minutes</p>
                    </div>
                    
                    {f'''
                    <div style="background-color: #e8f5e8; padding: 20px; border-radius: 8px; margin: 20px 0;">
                        <h2 style="color: #333; margin-top: 0;">Option 2: Click to Verify</h2>
                        <p style="color: #666;">Or simply click the button below:</p>
                        <div style="text-align: center; margin: 20px 0;">
                            <a href="{verification_link}" style="display: inline-block; background-color: #28a745; color: white; padding: 15px 30px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px;">Verify My Account</a>
                        </div>
                        <p style="color: #999; font-size: 14px; text-align: center;">Link expires in 5 minutes</p>
                    </div>
                    ''' if verification_link else ''}
                    
                    <div style="margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee;">
                        <p style="color: #999; font-size: 14px; text-align: center;">
                            If you didn't create an account with Univa AI, please ignore this email.
                        </p>
                        {f'<p style="color: #999; font-size: 12px; text-align: center;">Or copy and paste this link: {verification_link}</p>' if verification_link else ''}
                    </div>
                </div>
            </body>
            </html>
            """

            # Plain text version
            plain_content = f"""
            Welcome to Univa AI!
            
            Please verify your email address to complete your account setup.
            
            Option 1: Enter this 6-digit verification code in the app:
            {verification_code}
            
            {f'''Option 2: Click this link to verify instantly:
            {verification_link}
            ''' if verification_link else ''}
            
            This code expires in 5 minutes.
            
            If you didn't create an account with Univa AI, please ignore this email.
            """

            message = Mail(
                from_email=self.from_email,
                to_emails=email,
                subject=subject,
                html_content=html_content,
                plain_text_content=plain_content,
            )

            response = self.sendgrid_client.send(message)

            if response.status_code == 202:
                logging.info(f"Verification email sent successfully to {email}")
                return True
            else:
                logging.error(
                    f"Failed to send email. Status code: {response.status_code}"
                )
                return False

        except Exception as e:
            logging.error(f"Error sending verification email: {str(e)}")
            return False

    def send_welcome_email(self, email, name):
        """Send welcome email after successful verification"""
        try:
            subject = f"Welcome to Univa AI, {name}"

            html_content = f"""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Welcome to Univa AI</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; margin: 0; padding: 20px; background-color: #f4f4f4;">
                <div style="max-width: 600px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 10px; box-shadow: 0 0 10px rgba(0,0,0,0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <h1 style="color: #333;">Welcome to Univa AI</h1>
                        <p style="color: #666; font-size: 18px;">Your account has been successfully created!</p>
                    </div>
                    
                    <div style="background-color: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0;">
                        <h2 style="color: #333;">What's Next?</h2>
                        <ul style="color: #666;">
                            <li>Add your courses and upload your syllabus, slides and notes</li>
                            <li>Ask the tutor questions grounded in your own material</li>
                            <li>Review flashcards and practise before your exams</li>
                            <li>Plan your study time around your deadlines</li>
                        </ul>
                    </div>
                    
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="{self.frontend_url}/dashboard" style="display: inline-block; background-color: #007bff; color: white; padding: 15px 30px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px;">Open Univa AI</a>
                    </div>
                    
                    <div style="margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; text-align: center;">
                        <p style="color: #999; font-size: 14px;">
                            Need help? Reply to this email.
                        </p>
                    </div>
                </div>
            </body>
            </html>
            """

            message = Mail(
                from_email=self.from_email,
                to_emails=email,
                subject=subject,
                html_content=html_content,
            )

            response = self.sendgrid_client.send(message)

            if response.status_code == 202:
                logging.info(f"Welcome email sent successfully to {email}")
                return True
            else:
                logging.error(
                    f"Failed to send welcome email. Status code: {response.status_code}"
                )
                return False

        except Exception as e:
            logging.error(f"Error sending welcome email: {str(e)}")
            return False
