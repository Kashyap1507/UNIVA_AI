"""
Move scanned documents from the old learner record to the student (idempotent, --dry-run).

Before the university migration a scan belonged to a learner record:
documents/{id}.child_id -> children/{child_id}.user_id (parent_id on the oldest records).
The server and the apps now key documents by user_id only. For every document that
still has child_id, this script:
    - sets user_id to the learner record's owner when user_id is missing, and
    - renames child_id to learner_id (kept for reference; nothing reads it).
Documents whose learner record is missing or has no owner are listed and left as they are.

Usage (from Wizly-Server):
    ./venv/bin/python scripts/migration/migrate_documents_to_user_id.py --dry-run
    ./venv/bin/python scripts/migration/migrate_documents_to_user_id.py
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from google.cloud.firestore import DELETE_FIELD  # noqa: E402

from scripts.migration._common import BatchWriter, connect  # noqa: E402


def _learner_owner(db, learner_id, cache):
    if learner_id not in cache:
        snapshot = db.collection("children").document(learner_id).get()
        data = (snapshot.to_dict() if snapshot.exists else None) or {}
        cache[learner_id] = data.get("user_id") or data.get("parent_id")
    return cache[learner_id]


def plan(db):
    """Return (changes, skipped): changes are (doc_id, update) pairs, skipped are doc ids without an owner."""
    changes, skipped, owners = [], [], {}
    for snapshot in db.collection("documents").stream():
        data = snapshot.to_dict() or {}
        learner_id = data.get("child_id")
        if not learner_id:
            continue
        update = {"learner_id": learner_id, "child_id": DELETE_FIELD}
        if not data.get("user_id"):
            owner = _learner_owner(db, learner_id, owners)
            if not owner:
                skipped.append(snapshot.id)
                continue
            update["user_id"] = owner
        changes.append((snapshot.id, update))
    return changes, skipped


def apply(db, changes):
    """Write the planned updates; returns the number of documents updated."""
    writer = BatchWriter(db)
    for doc_id, update in changes:
        writer.update(db.collection("documents").document(doc_id), update)
    writer.flush()
    return writer.total


def describe(update):
    return {key: ("<removed>" if value is DELETE_FIELD else value) for key, value in update.items()}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true", help="show what would change without writing")
    args = parser.parse_args(argv)

    db = connect()
    changes, skipped = plan(db)
    verb = "would update" if args.dry_run else "update"
    for doc_id, update in changes:
        print(f"{verb} documents/{doc_id}: {describe(update)}")
    for doc_id in skipped:
        print(f"skip documents/{doc_id}: its learner record is missing or has no owner")

    if args.dry_run:
        print(f"\nDry run: {len(changes)} to update, {len(skipped)} skipped. Nothing was written.")
        return 0
    updated = apply(db, changes)
    print(f"\nUpdated {updated} documents, skipped {len(skipped)}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
