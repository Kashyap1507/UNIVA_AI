"""
Firebase Storage Service - Handle file uploads to Firebase Storage
File: app/services/firebase_storage_service.py

Uses Firebase Admin SDK for server-side uploads to Firebase Storage bucket.
This replaces local file storage for better security and separation of concerns.
"""

import io
import logging
import os
import uuid
from datetime import datetime, timedelta
from typing import Optional, Tuple

from PIL import Image
from werkzeug.utils import secure_filename

from app.services.firebase_service import FIREBASE_STORAGE_BUCKET, get_storage_bucket


class FirebaseStorageService:
    """Service for handling file uploads to Firebase Storage"""

    def __init__(self):
        self.bucket = get_storage_bucket()

        # Allowed file extensions
        self.allowed_extensions = {
            "image": {"png", "jpg", "jpeg", "gif", "webp"},
            "document": {"pdf", "doc", "docx", "txt"},
        }

        # Max file sizes (in bytes)
        self.max_file_sizes = {
            "image": 10 * 1024 * 1024,  # 10MB
            "document": 20 * 1024 * 1024,  # 20MB
        }

        logging.info("Firebase Storage Service initialized")

    def upload_image(
        self,
        image_data: bytes,
        storage_path: str,
        content_type: str = "image/jpeg",
        public: bool = False,
    ) -> dict:
        """
        Upload image bytes to Firebase Storage.

        Args:
            image_data: Raw image bytes
            storage_path: Path in bucket (e.g., "users/u1/documents/doc123/pages/page001.jpg")
            content_type: MIME type of the image
            public: Make the object publicly readable (default private; private
                objects are served through short-lived signed URLs, get_signed_url).

        Returns:
            dict with public_url (None when private), storage_path, file_size
        """
        try:
            blob = self.bucket.blob(storage_path)

            # Upload from bytes
            blob.upload_from_string(image_data, content_type=content_type)

            if public:
                blob.make_public()

            result = {
                "success": True,
                "public_url": blob.public_url if public else None,
                "storage_path": storage_path,
                "file_size": len(image_data),
                "content_type": content_type,
                "uploaded_at": datetime.utcnow().isoformat(),
            }

            logging.info(f"Image uploaded to Firebase Storage: {storage_path}")
            return result

        except Exception as e:
            logging.error(f"Error uploading image to Firebase Storage: {str(e)}")
            return {"success": False, "error": str(e)}

    def upload_document_page(
        self,
        doc_id: str,
        page_id: str,
        page_number: int,
        image_data: bytes,
        generate_thumbnail: bool = True,
        owner_id: str = None,
    ) -> dict:
        """
        Upload a document page image to Firebase Storage.

        Pages of a student's scan are private: they live under
        users/{owner_id}/documents/{doc_id}/... and are read through signed URLs.

        Args:
            doc_id: Document ID
            page_id: Page ID
            page_number: Page number (1-indexed)
            image_data: Raw image bytes
            generate_thumbnail: Whether to also create a thumbnail
            owner_id: The student's user id (required: pages are always private)

        Returns:
            dict with storage_path, thumbnail_path, file_size (image_url and thumbnail_url are None)
        """
        if not owner_id:
            return {"success": False, "error": "owner_id is required for document pages"}
        try:
            # Define storage paths
            base = f"users/{owner_id}/documents/{doc_id}"
            image_path = f"{base}/pages/{page_id}_{page_number:03d}.jpg"
            thumb_path = f"{base}/thumbnails/{page_id}_{page_number:03d}_thumb.jpg"

            # Upload full image
            image_result = self.upload_image(image_data, image_path)
            if not image_result.get("success"):
                return image_result

            result = {
                "success": True,
                "image_url": image_result["public_url"],
                "storage_path": image_path,
                "file_size": image_result["file_size"],
            }

            # Generate and upload thumbnail
            if generate_thumbnail:
                thumb_data = self._create_thumbnail(image_data, size=(300, 300))
                if thumb_data:
                    thumb_result = self.upload_image(thumb_data, thumb_path)
                    if thumb_result.get("success"):
                        result["thumbnail_url"] = thumb_result["public_url"]
                        result["thumbnail_path"] = thumb_path

            logging.info(f"Document page uploaded: {image_path}")
            return result

        except Exception as e:
            logging.error(f"Error uploading document page: {str(e)}")
            return {"success": False, "error": str(e)}

    def upload_file(self, file, user_id: str, folder_type: str = "general") -> dict:
        """
        General file upload method (for Flask file objects).

        Args:
            file: Flask file object
            user_id: User ID
            folder_type: Type of upload (general, document_page)

        Returns:
            dict with file info
        """
        try:
            if not file or file.filename == "":
                return {"success": False, "error": "No file provided"}

            # Read file data
            file_data = file.read()

            # Generate unique filename
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            unique_id = str(uuid.uuid4())[:8]
            original_filename = secure_filename(file.filename)
            filename = f"{user_id}_{timestamp}_{unique_id}_{original_filename}"

            # Private path under the student (served through signed URLs)
            storage_path = f"users/{user_id}/uploads/{folder_type}/{filename}"

            # Determine content type
            extension = self._get_file_extension(original_filename)
            content_type = self._get_content_type(extension)

            # Upload to Firebase Storage
            result = self.upload_image(file_data, storage_path, content_type)

            if result.get("success"):
                result.update(
                    {
                        "filename": filename,
                        "original_filename": original_filename,
                        "user_id": user_id,
                        "folder_type": folder_type,
                    }
                )

            return result

        except Exception as e:
            logging.error(f"Error uploading file: {str(e)}")
            return {"success": False, "error": str(e)}

    def delete_file(self, storage_path: str) -> dict:
        """
        Delete a file from Firebase Storage.

        Args:
            storage_path: Path in bucket to delete

        Returns:
            dict with success status
        """
        try:
            blob = self.bucket.blob(storage_path)

            if blob.exists():
                blob.delete()
                logging.info(f"File deleted from Firebase Storage: {storage_path}")
                return {"success": True}
            else:
                logging.warning(f"File not found for deletion: {storage_path}")
                return {"success": True, "message": "File not found"}

        except Exception as e:
            logging.error(f"Error deleting file from Firebase Storage: {str(e)}")
            return {"success": False, "error": str(e)}

    def delete_document_files(self, doc_id: str, owner_id: Optional[str] = None) -> dict:
        """
        Delete all files associated with a document (private and old public paths).

        Args:
            doc_id: Document ID
            owner_id: The student's user id (private files live under users/{owner_id}/)

        Returns:
            dict with count of deleted files
        """
        try:
            prefixes = [f"documents/{doc_id}/"]
            if owner_id:
                prefixes.insert(0, f"users/{owner_id}/documents/{doc_id}/")

            deleted_count = 0
            for prefix in prefixes:
                for blob in self.bucket.list_blobs(prefix=prefix):
                    blob.delete()
                    deleted_count += 1

            logging.info(f"Deleted {deleted_count} files for document {doc_id}")
            return {"success": True, "deleted_count": deleted_count}

        except Exception as e:
            logging.error(f"Error deleting document files: {str(e)}")
            return {"success": False, "error": str(e)}

    def get_signed_url(
        self, storage_path: str, expiration_minutes: int = 60
    ) -> Optional[str]:
        """
        Generate a signed URL for temporary access to a private file.

        Args:
            storage_path: Path in bucket
            expiration_minutes: URL validity in minutes

        Returns:
            Signed URL string or None
        """
        try:
            blob = self.bucket.blob(storage_path)

            if not blob.exists():
                return None

            url = blob.generate_signed_url(
                expiration=timedelta(minutes=expiration_minutes), method="GET"
            )

            return url

        except Exception as e:
            logging.error(f"Error generating signed URL: {str(e)}")
            return None

    def get_public_url(self, storage_path: str) -> str:
        """
        Get the public URL for a file.

        Args:
            storage_path: Path in bucket

        Returns:
            Public URL string
        """
        return (
            f"https://storage.googleapis.com/{FIREBASE_STORAGE_BUCKET}/{storage_path}"
        )

    def generate_signed_upload_url(
        self,
        storage_path: str,
        content_type: str = "image/jpeg",
        expiration_minutes: int = 30,
    ) -> dict:
        """
        Generate a signed URL for direct upload from mobile.

        This allows mobile apps to upload directly to Firebase Storage
        without going through the backend, reducing latency.

        Args:
            storage_path: Path in bucket where file will be uploaded
            content_type: MIME type of the file
            expiration_minutes: URL validity in minutes

        Returns:
            dict with upload_url, storage_path, expires_at
        """
        try:
            blob = self.bucket.blob(storage_path)

            # Generate signed URL for PUT (upload) operation
            url = blob.generate_signed_url(
                expiration=timedelta(minutes=expiration_minutes),
                method="PUT",
                content_type=content_type,
            )

            logging.info(f"Generated signed upload URL for: {storage_path}")

            return {
                "success": True,
                "upload_url": url,
                "storage_path": storage_path,
                "content_type": content_type,
                "expires_at": (datetime.utcnow() + timedelta(minutes=expiration_minutes)).isoformat(),
            }

        except Exception as e:
            logging.error(f"Error generating signed upload URL: {str(e)}")
            return {"success": False, "error": str(e)}

    def download_file(self, storage_path: str) -> Optional[bytes]:
        """
        Download a file from Firebase Storage.

        Args:
            storage_path: Path in bucket

        Returns:
            File bytes or None if failed
        """
        try:
            blob = self.bucket.blob(storage_path)

            if not blob.exists():
                logging.warning(f"File not found: {storage_path}")
                return None

            data = blob.download_as_bytes()
            logging.info(f"Downloaded file from Firebase Storage: {storage_path}")
            return data

        except Exception as e:
            logging.error(f"Error downloading file: {str(e)}")
            return None

    def file_exists(self, storage_path: str) -> bool:
        """
        Check if a file exists in Firebase Storage.

        Args:
            storage_path: Path in bucket

        Returns:
            True if file exists, False otherwise
        """
        try:
            blob = self.bucket.blob(storage_path)
            return blob.exists()
        except Exception as e:
            logging.error(f"Error checking file existence: {str(e)}")
            return False

    def _create_thumbnail(
        self, image_data: bytes, size: Tuple[int, int] = (300, 300)
    ) -> Optional[bytes]:
        """
        Create a thumbnail from image data.

        Args:
            image_data: Raw image bytes
            size: Thumbnail dimensions (width, height)

        Returns:
            Thumbnail bytes or None
        """
        try:
            # Open image
            img = Image.open(io.BytesIO(image_data))

            # Convert to RGB if necessary (for JPEG output)
            if img.mode in ("RGBA", "P"):
                img = img.convert("RGB")

            # Create thumbnail maintaining aspect ratio
            img.thumbnail(size, Image.Resampling.LANCZOS)

            # Save to bytes
            output = io.BytesIO()
            img.save(output, format="JPEG", quality=85)
            output.seek(0)

            return output.getvalue()

        except Exception as e:
            logging.error(f"Error creating thumbnail: {str(e)}")
            return None

    def _get_file_extension(self, filename: str) -> Optional[str]:
        """Get file extension from filename"""
        if "." not in filename:
            return None
        return filename.rsplit(".", 1)[1].lower()

    def _get_content_type(self, extension: Optional[str]) -> str:
        """Get MIME type based on file extension"""
        content_types = {
            # Images
            "png": "image/png",
            "jpg": "image/jpeg",
            "jpeg": "image/jpeg",
            "gif": "image/gif",
            "webp": "image/webp",
            # Documents
            "pdf": "application/pdf",
            "doc": "application/msword",
            "docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "txt": "text/plain",
        }
        return content_types.get(extension, "application/octet-stream")


# Singleton instance
_firebase_storage_service = None


def get_firebase_storage_service() -> FirebaseStorageService:
    """Get or create the Firebase Storage service singleton"""
    global _firebase_storage_service
    if _firebase_storage_service is None:
        _firebase_storage_service = FirebaseStorageService()
    return _firebase_storage_service
