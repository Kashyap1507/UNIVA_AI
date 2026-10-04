"""
Base model class with datetime serialization - Fixed version
File: app/models/base.py
"""

import logging
from datetime import datetime
from typing import Any, Dict, Optional

from google.cloud.firestore import SERVER_TIMESTAMP

from app.utils.datetime_utils import auto_serialize_document


class BaseModel:
    """Base model class with common CRUD operations and datetime serialization"""

    collection_name = None  # To be defined in subclasses

    def __init__(self, **kwargs):
        self.id = kwargs.get("id")
        self.created_at = kwargs.get("created_at")
        self.updated_at = kwargs.get("updated_at")

        # Set attributes from kwargs
        for key, value in kwargs.items():
            if hasattr(self, key):
                setattr(self, key, value)

    def to_dict(self) -> Dict[str, Any]:
        """Convert model instance to dictionary"""
        data = {}
        for key, value in self.__dict__.items():
            if not key.startswith("_") and value is not None:
                data[key] = value
        return data

    def to_api_dict(self) -> Dict[str, Any]:
        """Convert model to API-safe dictionary with serialized datetime fields"""
        data = self.to_dict()
        # Automatically serialize datetime fields to prevent JSON serialization errors
        return auto_serialize_document(data)

    @classmethod
    def from_dict(cls, data: Dict[str, Any], doc_id: str = None):
        """Create model instance from dictionary"""
        if doc_id:
            data["id"] = doc_id
        return cls(**data)

    def validate(self) -> tuple[bool, str]:
        """Validate model data - to be implemented in subclasses"""
        return True, "Valid"

    def save(self, db):
        """Save model to Firestore"""
        try:
            # Validate before saving
            is_valid, error_message = self.validate()
            if not is_valid:
                raise ValueError(error_message)

            data = self.to_dict()

            if self.id:
                # Update existing document
                data["updated_at"] = SERVER_TIMESTAMP
                doc_ref = db.collection(self.collection_name).document(self.id)
                doc_ref.update(data)
            else:
                # Create new document
                data["created_at"] = SERVER_TIMESTAMP
                data["updated_at"] = SERVER_TIMESTAMP
                doc_ref = db.collection(self.collection_name).document()
                self.id = doc_ref.id
                data["id"] = self.id
                doc_ref.set(data)

            return True
        except Exception as e:
            logging.error(f"Error saving {self.__class__.__name__}: {str(e)}")
            raise

    def delete(self, db):
        """Delete model from Firestore"""
        try:
            if not self.id:
                raise ValueError("Cannot delete model without ID")

            doc_ref = db.collection(self.collection_name).document(self.id)
            doc_ref.delete()
            return True
        except Exception as e:
            logging.error(f"Error deleting {self.__class__.__name__}: {str(e)}")
            raise

    @classmethod
    def get_by_id(cls, db, doc_id: str):
        """Get model instance by ID"""
        try:
            doc_ref = db.collection(cls.collection_name).document(doc_id)
            doc = doc_ref.get()

            if doc.exists:
                return cls.from_dict(doc.to_dict(), doc.id)
            return None
        except Exception as e:
            logging.error(f"Error getting {cls.__name__} by ID: {str(e)}")
            raise

    @classmethod
    def get_all(cls, db, limit: int = None):
        """Get all model instances"""
        try:
            query = db.collection(cls.collection_name)

            if limit:
                query = query.limit(limit)

            docs = query.stream()
            return [cls.from_dict(doc.to_dict(), doc.id) for doc in docs]
        except Exception as e:
            logging.error(f"Error getting all {cls.__name__}: {str(e)}")
            raise

    def __repr__(self):
        return f"<{self.__class__.__name__}(id={self.id})>"
