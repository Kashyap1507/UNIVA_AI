"""
Copy the K-12 collections into archive_k12_<name> (idempotent, --dry-run). Originals are never deleted.

The university app no longer reads these collections: child profiles and their progress,
the K-12 curriculum and question bank, homework photos, study collections, calendar
events, communities, textbooks and worksheets. Every document is copied, with its
subcollections, to archive_k12_<collection>/<same id>, adding _archived_at and _source.
Documents that are already archived are skipped, so the script can be re-run at any time.

Nothing is deleted. Once you have checked the archive in the Firebase console you can
delete the original collections there yourself.

Run migrate_documents_to_user_id.py first: it reads children/ to find each scan's owner.

Usage (from Wizly-Server):
    ./venv/bin/python scripts/migration/archive_k12_collections.py --dry-run
    ./venv/bin/python scripts/migration/archive_k12_collections.py
"""

import argparse
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.migration._common import BatchWriter, connect  # noqa: E402

ARCHIVE_PREFIX = "archive_k12_"

# From migration/AUDIT.md section 1 (collections labelled DELETE or REPLACE)
LEGACY_COLLECTIONS = [
    # child profiles and progress
    "children",
    "activity_sessions",
    "child_progress",
    "badges",
    "user_answers",
    "user_progress",
    "game_sessions",
    "progress",
    # K-12 curriculum and question bank
    "grades",
    "subjects",
    "lessons",
    "concepts",
    "topics",
    "skills",
    "difficulties",
    "questions",
    "question_options",
    "question_concepts",
    "question_skills",
    "question_sets",
    "question_set_items",
    "question_i18n",
    "option_i18n",
    "collection_quizzes",
    "ai_import_batches",
    "ai_import_items",
    "textbooks",
    "textbook_chunks",
    # K-12 features
    "homework_photos",
    "study_collections",
    "user_study_collections",
    "calendar_events",
    "communities",
    "worksheets",
]


def copy_collection(source, target, writer, dry_run, archived_at, label):
    """Copy one collection and its subcollections; returns (copied, already_archived)."""
    existing = {snapshot.id for snapshot in target.select([]).stream()}
    copied = already = 0
    for snapshot in source.stream():
        destination = target.document(snapshot.id)
        if snapshot.id in existing:
            already += 1
        else:
            copied += 1
            if not dry_run:
                data = snapshot.to_dict() or {}
                data.update({"_archived_at": archived_at, "_source": f"{label}/{snapshot.id}"})
                writer.set(destination, data)
        list_subcollections = getattr(snapshot.reference, "collections", None)
        for sub in list_subcollections() if list_subcollections else []:
            sub_copied, sub_already = copy_collection(
                sub, destination.collection(sub.id), writer, dry_run, archived_at, f"{label}/{snapshot.id}/{sub.id}"
            )
            copied += sub_copied
            already += sub_already
    return copied, already


def archive(db, dry_run=False, collections=None):
    """Archive the legacy collections; returns {collection: (copied, already_archived)} for non-empty ones."""
    writer = BatchWriter(db)
    archived_at = datetime.now(timezone.utc)
    report = {}
    for name in collections or LEGACY_COLLECTIONS:
        counts = copy_collection(db.collection(name), db.collection(ARCHIVE_PREFIX + name), writer, dry_run, archived_at, name)
        if any(counts):
            report[name] = counts
    writer.flush()
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true", help="show what would be copied without writing")
    args = parser.parse_args(argv)

    report = archive(connect(), dry_run=args.dry_run)
    verb = "to copy" if args.dry_run else "copied"
    for name, (copied, already) in report.items():
        print(f"{name} -> {ARCHIVE_PREFIX}{name}: {copied} {verb}, {already} already archived")
    if not report:
        print("No legacy documents found.")
    print("\nDry run: nothing was written." if args.dry_run else "\nDone. The original collections were not changed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
