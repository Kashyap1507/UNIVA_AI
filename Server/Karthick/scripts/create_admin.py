"""
Create or update an admin panel account and set its password.

The password is typed in the terminal (hidden) and stored only as a
werkzeug hash on the user's doc. If no user exists with the email, a new
verified email user is created. Re-running changes the password.

Usage (from Wizly-Server/):
    ./venv/bin/python scripts/create_admin.py you@example.com
    ./venv/bin/python scripts/create_admin.py you@example.com --dry-run
"""

import argparse
import getpass
import sys
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SERVER_DIR))

from dotenv import load_dotenv  # noqa: E402

load_dotenv(SERVER_DIR / ".env")

from app.models.user import User  # noqa: E402
from app.services.firebase_service import get_firestore_client, initialize_firebase  # noqa: E402
from app.utils.validators import validate_email  # noqa: E402

MIN_PASSWORD_LENGTH = 12


def read_password() -> str:
    while True:
        password = getpass.getpass(f"New admin password (min {MIN_PASSWORD_LENGTH} chars): ")
        if len(password) < MIN_PASSWORD_LENGTH:
            print("Too short, try again.")
            continue
        if getpass.getpass("Repeat password: ") != password:
            print("Passwords don't match, try again.")
            continue
        return password


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("email")
    parser.add_argument("--dry-run", action="store_true", help="show what would happen, change nothing")
    args = parser.parse_args()

    email = args.email.strip().lower()
    if not validate_email(email):
        sys.exit(f"Invalid email: {email}")

    initialize_firebase()
    db = get_firestore_client()
    user = User.get_by_email(db, email)

    if args.dry_run:
        if user:
            state = "already an admin" if user.is_admin else "not an admin yet"
            print(f"[dry-run] Would grant admin:all and set the password on user {user.id} ({state}).")
        else:
            print(f"[dry-run] Would create a new user for {email} with admin:all and a password.")
        return

    password = read_password()

    if not user:
        user = User(email=email, auth_provider="email", verified=True)
        user.save(db)
        print(f"Created user {user.id}")

    user.grant_admin_access(["admin:all"])
    user.set_admin_password(password)
    db.collection(User.collection_name).document(user.id).update(
        {
            "is_admin": True,
            "admin_permissions": user.admin_permissions,
            "admin_created_at": user.admin_created_at,
            "admin_password_hash": user.admin_password_hash,
        }
    )
    print(f"Admin ready: {email}. Log in at http://localhost:3000/admin/login")


if __name__ == "__main__":
    main()
