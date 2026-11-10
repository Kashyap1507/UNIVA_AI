# Scripts

Run from `Wizly-Server` with the project venv (`./venv/bin/python ...`). They use
`firebase-service-account.json` and only work against `wizly-ai-2026`.

| Script | What it does |
|---|---|
| `create_admin.py` | Create or update an admin user with a password for the admin panel. |
| `ensure_firestore_indexes.py` | Create the composite and vector indexes in `firestore.indexes.json` (safe to re-run). |
| `seed/seed_subscription_plans.py` | Write the Free/Basic/Premium/Pro plans and their daily limits (work checks, practice sets, AI flashcard sets). |
| `migration/` | One-off data migrations for the university conversion. See `migration/README.md`. |
