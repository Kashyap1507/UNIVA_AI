"""
Datetime serialization utilities for Firebase DatetimeWithNanoseconds
File: app/utils/datetime_utils.py
"""

import logging
from datetime import datetime
from typing import Any, Dict, List, Optional, Union


def serialize_datetime(dt: Any) -> Optional[str]:
    """
    Convert DatetimeWithNanoseconds, datetime, or other date objects to ISO string

    Args:
        dt: Date/time object to serialize

    Returns:
        ISO formatted string or None if input is None/invalid
    """
    if dt is None:
        return None

    try:
        # Handle Firestore DatetimeWithNanoseconds
        if hasattr(dt, "timestamp"):
            # Convert to regular datetime first
            return datetime.fromtimestamp(dt.timestamp()).isoformat()

        # Handle regular datetime objects
        elif isinstance(dt, datetime):
            return dt.isoformat()

        # If it's already a string, return as is
        elif isinstance(dt, str):
            return dt

        # Try to convert other objects to string
        else:
            return str(dt)

    except Exception as e:
        logging.warning(f"Error serializing datetime {dt}: {str(e)}")
        return None


def serialize_document(doc: Dict[str, Any]) -> Dict[str, Any]:
    """
    Recursively serialize all datetime fields in a document

    Args:
        doc: Document dictionary that may contain datetime fields

    Returns:
        Document with all datetime fields serialized to ISO strings
    """
    if not isinstance(doc, dict):
        return doc

    serialized = {}

    for key, value in doc.items():
        if value is None:
            serialized[key] = None
        elif isinstance(value, dict):
            # Recursively serialize nested dictionaries
            serialized[key] = serialize_document(value)
        elif isinstance(value, list):
            # Serialize lists that might contain documents
            serialized[key] = serialize_list(value)
        elif hasattr(value, "timestamp") or isinstance(value, datetime):
            # Serialize datetime objects
            serialized[key] = serialize_datetime(value)
        else:
            # Keep other values as-is
            serialized[key] = value

    return serialized


def serialize_list(items: List[Any]) -> List[Any]:
    """
    Serialize a list that may contain documents or datetime objects

    Args:
        items: List to serialize

    Returns:
        List with all datetime fields serialized
    """
    if not isinstance(items, list):
        return items

    serialized = []

    for item in items:
        if isinstance(item, dict):
            serialized.append(serialize_document(item))
        elif hasattr(item, "timestamp") or isinstance(item, datetime):
            serialized.append(serialize_datetime(item))
        else:
            serialized.append(item)

    return serialized


def serialize_firestore_data(data: Union[Dict, List, Any]) -> Union[Dict, List, Any]:
    """
    Main function to serialize any Firestore data structure

    Args:
        data: Data from Firestore that may contain DatetimeWithNanoseconds

    Returns:
        Data with all datetime fields properly serialized
    """
    if isinstance(data, dict):
        return serialize_document(data)
    elif isinstance(data, list):
        return serialize_list(data)
    elif hasattr(data, "timestamp") or isinstance(data, datetime):
        return serialize_datetime(data)
    else:
        return data


# Common datetime field names that should be serialized
DATETIME_FIELDS = {
    "created_at",
    "updated_at",
    "deleted_at",
    "last_login",
    "admin_last_login",
    "expires_at",
    "completed_at",
    "started_at",
    "submitted_at",
    "due_date",
    "birth_date",
    "timestamp",
}


def auto_serialize_document(
    doc: Dict[str, Any], datetime_fields: set = None
) -> Dict[str, Any]:
    """
    Automatically serialize common datetime fields in a document

    Args:
        doc: Document to serialize
        datetime_fields: Set of field names to serialize (defaults to DATETIME_FIELDS)

    Returns:
        Document with specified datetime fields serialized
    """
    if datetime_fields is None:
        datetime_fields = DATETIME_FIELDS

    serialized = doc.copy()

    for field in datetime_fields:
        if field in serialized:
            serialized[field] = serialize_datetime(serialized[field])

    return serialized
