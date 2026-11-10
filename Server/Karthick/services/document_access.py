"""Ownership checks for scanned documents (documents collection)."""


def user_owns_document(db, doc_data: dict, user_id: str) -> bool:
    """A document belongs to the user (user_id).

    Scans saved before the user_id backfill (scripts/migration/migrate_documents_to_user_id.py)
    only carry the old learner record id in child_id; those are matched through that record.
    """
    if not user_id or not doc_data:
        return False
    if doc_data.get("user_id"):
        return doc_data["user_id"] == user_id
    child_id = doc_data.get("child_id")
    if not child_id:
        return False
    child_snapshot = db.collection("children").document(child_id).get()
    return child_snapshot.exists and (child_snapshot.to_dict() or {}).get("user_id") == user_id
