"""
Shared helpers for the one-off migration scripts.

The scripts are run by hand (never by the server). Each one supports --dry-run,
is safe to re-run and only connects to wizly-ai-2026.
"""

import sys
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parents[2]
SERVICE_ACCOUNT = SERVER_DIR / "firebase-service-account.json"
PROJECT = "wizly-ai-2026"
BATCH_SIZE = 400


def connect():
    """Firestore client for wizly-ai-2026; refuses a service account for any other project."""
    from google.cloud import firestore
    from google.oauth2 import service_account

    creds = service_account.Credentials.from_service_account_file(str(SERVICE_ACCOUNT))
    if creds.project_id != PROJECT:
        sys.exit(f"Service account is for {creds.project_id}, expected {PROJECT}")
    return firestore.Client(project=PROJECT, credentials=creds)


class BatchWriter:
    """Groups writes into Firestore batches of BATCH_SIZE."""

    def __init__(self, db):
        self.db = db
        self.batch = db.batch()
        self.pending = 0
        self.total = 0

    def set(self, ref, data):
        self.batch.set(ref, data)
        self._count()

    def update(self, ref, data):
        self.batch.update(ref, data)
        self._count()

    def _count(self):
        self.pending += 1
        self.total += 1
        if self.pending >= BATCH_SIZE:
            self.flush()

    def flush(self):
        if self.pending:
            self.batch.commit()
            self.batch = self.db.batch()
            self.pending = 0
