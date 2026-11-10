# Migration scripts

One-off data migrations for the university conversion (migration Phase 13). You run them
by hand from `Wizly-Server`. Each one connects only to `wizly-ai-2026`, supports
`--dry-run` and is safe to re-run. None of them deletes data.

Run them in this order, with a dry run first each time:

```bash
./venv/bin/python scripts/migration/migrate_documents_to_user_id.py --dry-run
./venv/bin/python scripts/migration/migrate_documents_to_user_id.py

./venv/bin/python scripts/migration/archive_k12_collections.py --dry-run
./venv/bin/python scripts/migration/archive_k12_collections.py
```

- `migrate_documents_to_user_id.py`: for each scan that still has `child_id`, it sets `user_id`
  to the owner of the old learner record and renames `child_id` to `learner_id`. Until it runs,
  the server still reads those scans by id through the old learner record
  (`app/services/document_access.py`), but they don't appear in the student's document list.
- `archive_k12_collections.py`: copies the K-12 collections (children, curriculum, question
  bank, homework photos, study collections, calendar events, communities, textbooks,
  worksheets and so on) to `archive_k12_<name>`. The originals stay. You can delete them in
  the Firebase console once you have checked the archive.
