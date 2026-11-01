"""
Image processing utilities for AI workflows
Handles encoding, downloading, validation and resizing of images

Supports:
- Base64 encoding for inline images
- Image validation and resizing
- Firebase Storage integration
"""

import base64
import io
import logging
from typing import Optional, Tuple

import requests
from PIL import Image

logger = logging.getLogger(__name__)


def get_image_info(image_data: bytes) -> dict:
    """
    Get basic information about an image.

    Args:
        image_data: Raw image bytes

    Returns:
        Dict with image info: width, height, format, size_bytes, size_mb
    """
    try:
        image = Image.open(io.BytesIO(image_data))
        return {
            "width": image.width,
            "height": image.height,
            "format": image.format or "UNKNOWN",
            "mode": image.mode,
            "size_bytes": len(image_data),
            "size_mb": len(image_data) / (1024 * 1024),
        }
    except Exception as e:
        logger.error(f"Error getting image info: {str(e)}")
        return {
            "error": str(e),
            "size_bytes": len(image_data),
            "size_mb": len(image_data) / (1024 * 1024),
        }


def encode_image_file(image_path: str) -> Optional[str]:
    """
    Encode image file to base64 for Gemini API

    Args:
        image_path: Path to the image file

    Returns:
        Base64 encoded image string or None if failed
    """
    try:
        with open(image_path, "rb") as image_file:
            image_data = image_file.read()
            base64_image = base64.b64encode(image_data).decode("utf-8")
            logger.info(f"Successfully encoded image from file: {image_path}")
            return base64_image
    except Exception as e:
        logger.error(f"Error encoding image file {image_path}: {str(e)}")
        return None


def download_and_encode_image(image_url: str) -> Optional[str]:
    """
    Download image from URL and encode to base64

    Args:
        image_url: URL of the image to download

    Returns:
        Base64 encoded image string or None if failed
    """
    try:
        response = requests.get(image_url, timeout=30)
        response.raise_for_status()
        image_data = response.content
        base64_image = base64.b64encode(image_data).decode("utf-8")
        logger.info(f"Successfully downloaded and encoded image from: {image_url}")
        return base64_image
    except Exception as e:
        logger.error(f"Error downloading/encoding image from {image_url}: {str(e)}")
        return None


def validate_image(
    image_data: bytes,
    max_size_mb: float = 20.0,
    allowed_formats: list = None,
) -> Tuple[bool, str]:
    """
    Validate image data

    Args:
        image_data: Raw image bytes
        max_size_mb: Maximum allowed size in MB
        allowed_formats: List of allowed image formats (e.g., ['JPEG', 'PNG'])

    Returns:
        Tuple of (is_valid, error_message)
    """
    if allowed_formats is None:
        allowed_formats = ["JPEG", "PNG", "GIF", "WEBP"]

    try:
        # Check size
        size_mb = len(image_data) / (1024 * 1024)
        if size_mb > max_size_mb:
            return (
                False,
                f"Image size ({size_mb:.2f}MB) exceeds maximum ({max_size_mb}MB)",
            )

        # Check format
        image = Image.open(io.BytesIO(image_data))
        if image.format not in allowed_formats:
            return (
                False,
                f"Image format '{image.format}' not allowed. Allowed: {allowed_formats}",
            )

        return True, ""
    except Exception as e:
        return False, f"Invalid image: {str(e)}"


def resize_image_if_needed(
    base64_image: str,
    max_file_size_mb: float = 4.0,
    max_dimension: int = 3072,
) -> str:
    """
    Resize image if it exceeds Gemini's limits

    Gemini limits:
    - Max file size: 4MB
    - Max dimensions: 3072x3072

    Args:
        base64_image: Base64 encoded image
        max_file_size_mb: Maximum file size in MB (default 4MB for Gemini)
        max_dimension: Maximum width or height in pixels

    Returns:
        Resized base64 encoded image (or original if already within limits)
    """
    try:
        # Decode image
        image_data = base64.b64decode(base64_image)
        image = Image.open(io.BytesIO(image_data))

        original_width, original_height = image.size
        original_size_mb = len(image_data) / (1024 * 1024)

        # Check if resize needed
        needs_resize = (
            original_width > max_dimension
            or original_height > max_dimension
            or original_size_mb > max_file_size_mb
        )

        if not needs_resize:
            logger.debug(
                f"Image already within limits: {original_width}x{original_height}, "
                f"{original_size_mb:.2f}MB"
            )
            return base64_image

        # Calculate new dimensions
        scale = min(
            max_dimension / original_width,
            max_dimension / original_height,
            1.0,
        )
        new_width = int(original_width * scale)
        new_height = int(original_height * scale)

        # Resize
        image = image.resize((new_width, new_height), Image.Resampling.LANCZOS)

        # Convert to RGB if necessary (for JPEG encoding)
        if image.mode in ("RGBA", "P"):
            image = image.convert("RGB")

        # Encode with quality reduction if needed to meet size limit
        quality = 85
        while quality >= 30:
            buffer = io.BytesIO()
            image.save(buffer, format="JPEG", quality=quality, optimize=True)
            resized_data = buffer.getvalue()
            resized_size_mb = len(resized_data) / (1024 * 1024)

            if resized_size_mb <= max_file_size_mb:
                logger.info(
                    f"Resized image: {original_width}x{original_height} ({original_size_mb:.2f}MB) -> "
                    f"{new_width}x{new_height} ({resized_size_mb:.2f}MB), quality={quality}"
                )
                return base64.b64encode(resized_data).decode("utf-8")

            quality -= 10

        # Fallback: return best effort
        logger.warning(
            f"Could not resize image to under {max_file_size_mb}MB. "
            f"Final size: {resized_size_mb:.2f}MB"
        )
        return base64.b64encode(resized_data).decode("utf-8")

    except Exception as e:
        logger.error(f"Error resizing image: {str(e)}")
        return base64_image  # Return original on error


def resize_image_for_langsmith(
    base64_image: str,
    target_raw_size_mb: float = 14.0,
) -> str:
    """
    Resize image for LangSmith tracing payload size limits.

    LangSmith has a 20MB payload limit. This function targets ~14MB raw
    (before JSON encoding overhead) to stay safely under the limit.

    The approach:
    1. First try reducing JPEG quality progressively (85 -> 25)
    2. If still too large, also reduce dimensions
    3. Keep reducing until under target or at minimum quality

    Args:
        base64_image: Base64 encoded image
        target_raw_size_mb: Target raw size in MB (default 14MB)

    Returns:
        Resized base64 encoded image
    """
    try:
        # Decode image
        image_data = base64.b64decode(base64_image)
        current_size_mb = len(image_data) / (1024 * 1024)

        # Check if resize needed
        if current_size_mb <= target_raw_size_mb:
            logger.debug(
                f"Image already under {target_raw_size_mb}MB: {current_size_mb:.2f}MB"
            )
            return base64_image

        image = Image.open(io.BytesIO(image_data))
        original_width, original_height = image.size

        # Convert to RGB if necessary
        if image.mode in ("RGBA", "P", "LA"):
            image = image.convert("RGB")

        # Try quality reduction first (faster, preserves dimensions)
        for quality in [85, 70, 55, 40, 25]:
            buffer = io.BytesIO()
            image.save(buffer, format="JPEG", quality=quality, optimize=True)
            resized_data = buffer.getvalue()
            resized_size_mb = len(resized_data) / (1024 * 1024)

            if resized_size_mb <= target_raw_size_mb:
                logger.info(
                    f"Reduced image for LangSmith: {current_size_mb:.2f}MB -> "
                    f"{resized_size_mb:.2f}MB (quality={quality})"
                )
                return base64.b64encode(resized_data).decode("utf-8")

        # If quality reduction wasn't enough, also reduce dimensions
        scale = 0.75
        while scale >= 0.25:
            new_width = int(original_width * scale)
            new_height = int(original_height * scale)
            resized_image = image.resize(
                (new_width, new_height), Image.Resampling.LANCZOS
            )

            buffer = io.BytesIO()
            resized_image.save(buffer, format="JPEG", quality=50, optimize=True)
            resized_data = buffer.getvalue()
            resized_size_mb = len(resized_data) / (1024 * 1024)

            if resized_size_mb <= target_raw_size_mb:
                logger.info(
                    f"Reduced image for LangSmith: {current_size_mb:.2f}MB -> "
                    f"{resized_size_mb:.2f}MB ({new_width}x{new_height})"
                )
                return base64.b64encode(resized_data).decode("utf-8")

            scale -= 0.25

        # Return best effort
        logger.warning(
            f"Could not reduce image under {target_raw_size_mb}MB. "
            f"Final: {resized_size_mb:.2f}MB"
        )
        return base64.b64encode(resized_data).decode("utf-8")

    except Exception as e:
        logger.error(f"Error resizing image for LangSmith: {str(e)}")
        # Return original on error - LangSmith will handle the failure
        return base64_image
