"""
Upload Service - Stores files to Firebase Storage (cloud)

Uses Firebase Storage, with a local "uploads/" folder as a development fallback.
"""

import logging
import os
import uuid
from datetime import datetime

from werkzeug.utils import secure_filename

from app.services.firebase_storage_service import get_firebase_storage_service


class UploadService:
    """Firebase Storage upload service"""

    def __init__(self):
        try:
            self._storage_service = get_firebase_storage_service()
            self._use_firebase = True
            logging.info("Upload Service initialized with Firebase Storage")
        except Exception as e:
            logging.warning(
                f"Firebase Storage not available, falling back to local: {e}"
            )
            self._use_firebase = False
            self.base_upload_folder = "uploads"
            self.general_uploads_folder = os.path.join(
                self.base_upload_folder, "general"
            )
            os.makedirs(self.general_uploads_folder, exist_ok=True)
            logging.info("Upload Service initialized with local storage (fallback)")

    def _local_folder(self, folder_type):
        folder = os.path.join(
            self.base_upload_folder, secure_filename(folder_type) or "general"
        )
        os.makedirs(folder, exist_ok=True)
        return folder

    def upload_file(self, file, user_id, folder_type="general"):
        """General file upload method - uses Firebase Storage or local fallback"""
        try:
            if not file or file.filename == "":
                raise ValueError("No file provided")

            original_filename = secure_filename(file.filename)

            if self._use_firebase:
                result = self._storage_service.upload_file(
                    file=file, user_id=user_id, folder_type=folder_type
                )

                if result.get("success"):
                    return {
                        "filename": result.get("filename"),
                        "original_filename": result.get("original_filename"),
                        "file_path": result.get("storage_path"),
                        "file_url": result.get("public_url"),
                        "file_size": result.get("file_size"),
                        "upload_time": result.get("uploaded_at"),
                        "user_id": user_id,
                        "folder_type": folder_type,
                    }
                raise Exception(result.get("error", "Upload failed"))

            # Local fallback (development only)
            upload_folder = self._local_folder(folder_type)
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            unique_id = str(uuid.uuid4())[:8]
            filename = f"{user_id}_{timestamp}_{unique_id}_{original_filename}"

            file_path = os.path.join(upload_folder, filename)
            file.save(file_path)

            result = {
                "filename": filename,
                "original_filename": original_filename,
                "file_path": file_path,
                "file_url": f"/{upload_folder}/{filename}",
                "file_size": os.path.getsize(file_path),
                "upload_time": datetime.now().isoformat(),
                "user_id": user_id,
                "folder_type": folder_type,
            }

            logging.info(f"File uploaded successfully: {filename}")
            return result

        except Exception as e:
            logging.error(f"Error uploading file: {str(e)}")
            raise

    def delete_file(self, file_path):
        """Delete a file from Firebase Storage or local storage"""
        try:
            # Cloud paths are relative (e.g. users/{uid}/documents/...)
            if (
                self._use_firebase
                and not file_path.startswith("/")
                and not file_path.startswith("uploads/")
            ):
                result = self._storage_service.delete_file(file_path)
                return result.get("success", False)

            if os.path.exists(file_path):
                os.remove(file_path)
                logging.info(f"File deleted: {file_path}")
                return True
            logging.warning(f"File not found for deletion: {file_path}")
            return False
        except Exception as e:
            logging.error(f"Error deleting file: {str(e)}")
            raise

    def get_file_info(self, filename, folder_type="general"):
        """Get information about a locally stored upload"""
        try:
            file_path = os.path.join(self._local_folder(folder_type), filename)

            if os.path.exists(file_path):
                return {
                    "filename": filename,
                    "file_path": file_path,
                    "file_size": os.path.getsize(file_path),
                    "last_modified": datetime.fromtimestamp(
                        os.path.getmtime(file_path)
                    ).isoformat(),
                    "exists": True,
                }
            return {"exists": False}

        except Exception as e:
            logging.error(f"Error getting file info: {str(e)}")
            raise
