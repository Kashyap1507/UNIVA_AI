"""
AI Workflow Logger Service
Provides async logging for all AI workflows with comprehensive tracking
"""

import base64
import io
import json
import logging
import os
import time
from datetime import datetime, timedelta
from threading import Thread
from typing import Any, Dict, Optional

from PIL import Image
from pydantic import BaseModel

from app.utils import usage_meter
from app.services import ai_usage_service
from app.services.firebase_service import get_firestore_client


def convert_pydantic_to_dict(obj: Any) -> Any:
    """
    Recursively convert Pydantic models to dicts for Firestore serialization.

    Args:
        obj: Any object that may contain Pydantic models

    Returns:
        Converted object safe for Firestore
    """
    if isinstance(obj, BaseModel):
        # Pydantic model - convert to dict
        return obj.model_dump()
    elif isinstance(obj, dict):
        # Recursively convert dict values
        return {k: convert_pydantic_to_dict(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        # Recursively convert list items
        return [convert_pydantic_to_dict(item) for item in obj]
    else:
        # Return as-is for primitives
        return obj


# Token estimation constants (approximate)
CHARS_PER_TOKEN = 4  # Rough estimate for English text
TOKENS_PER_IMAGE = 258  # Base tokens for image processing (Gemini standard)

# Pricing per 1M tokens lives with the token meter (one table for estimates and real usage)
PRICING = usage_meter.PRICING


def with_usage(model: str, metrics: Optional[Dict[str, Any]], usage: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    """
    Metrics plus token usage and cost: real counts from the API when the run was metered,
    otherwise the caller's estimate (legacy workflows), otherwise zero.
    """
    metrics = dict(metrics or {})
    if usage:
        metrics.update(
            {
                "input_tokens": usage["tokens_in"],
                "output_tokens": usage["tokens_out"],
                "cost_usd": usage["cost_usd"],
                "api_calls": usage["calls"],
                "usage_source": "api",
            }
        )
        # The AI workflows admin page reads the estimated_* fields
        metrics["estimated_input_tokens"] = usage["tokens_in"]
        metrics["estimated_output_tokens"] = usage["tokens_out"]
        metrics["estimated_cost_usd"] = usage["cost_usd"]
    else:
        tokens_in = metrics.get("estimated_input_tokens") or 0
        tokens_out = metrics.get("estimated_output_tokens") or 0
        cost = metrics.get("estimated_cost_usd")
        if cost is None:
            cost = usage_meter.cost_usd(model, tokens_in, tokens_out)
        metrics.update(
            {
                "input_tokens": tokens_in,
                "output_tokens": tokens_out,
                "cost_usd": round(cost or 0.0, 6),
                "api_calls": 0,
                "usage_source": "estimate" if (tokens_in or tokens_out) else "none",
            }
        )
    return metrics


class AIWorkflowLogger:
    """
    Async logger for AI workflow requests and responses
    """

    def __init__(self):
        """Initialize the logger"""
        self.logger = logging.getLogger(__name__)
        self.enabled = (
            os.getenv("AI_WORKFLOW_LOGGING_ENABLED", "true").lower() == "true"
        )
        self.environment = os.getenv("FLASK_ENV", "development")
        self.server_version = os.getenv("SERVER_VERSION", "1.0.0")

        if self.enabled:
            self.logger.info("AI Workflow Logging is ENABLED")
        else:
            self.logger.info("AI Workflow Logging is DISABLED")

    def log_workflow(
        self,
        workflow_type: str,
        model: str,
        prompt: Dict[str, Any],
        input_data: Dict[str, Any],
        response: Dict[str, Any],
        metrics: Dict[str, Any],
        user_id: Optional[str] = None,
        course_id: Optional[str] = None,
        admin_user_id: Optional[str] = None,
        workflow_version: str = "1.0.0",
    ):
        """
        Log an AI workflow execution asynchronously

        Args:
            workflow_type: Model router task (e.g. tutor_chat, work_feedback)
            model: Model name (e.g., 'gemini-2.5-flash')
            prompt: Dict with template_version, full_prompt, system_prompt, dynamic_values
            input_data: Dict with parameters, image_metadata, educational_context
            response: Dict with success, raw_text, parsed_json, error_message, validation_errors
            metrics: Dict with latency_ms, estimated_input_tokens, estimated_output_tokens, etc.
            user_id: User ID if applicable
            course_id: Course ID if applicable
            admin_user_id: Admin user ID if applicable
            workflow_version: Version of the workflow implementation
        """
        # Real token usage metered by the Gemini client on this thread (taken even when logging is off, so it
        # never leaks into the next workflow run on the same thread)
        metrics = with_usage(model, metrics, usage_meter.take())

        if not self.enabled:
            return

        # Run logging in background thread to not block AI response
        thread = Thread(
            target=self._async_log,
            args=(
                workflow_type,
                model,
                prompt,
                input_data,
                response,
                metrics,
                user_id,
                course_id,
                admin_user_id,
                workflow_version,
            ),
        )
        thread.daemon = True
        thread.start()

    def _async_log(
        self,
        workflow_type: str,
        model: str,
        prompt: Dict[str, Any],
        input_data: Dict[str, Any],
        response: Dict[str, Any],
        metrics: Dict[str, Any],
        user_id: Optional[str],
        course_id: Optional[str],
        admin_user_id: Optional[str],
        workflow_version: str,
    ):
        """Internal method to perform async logging"""
        try:
            db = get_firestore_client()

            # Convert any Pydantic models to dicts for Firestore serialization
            # This handles BatchEvaluation, DetectionAndExtraction, etc.
            safe_input = convert_pydantic_to_dict(input_data)
            safe_prompt = convert_pydantic_to_dict(prompt)
            safe_response = convert_pydantic_to_dict(response)
            safe_metrics = convert_pydantic_to_dict(metrics)

            # Prepare log entry
            log_entry = {
                "timestamp": datetime.utcnow(),
                "workflow_type": workflow_type,
                "workflow_version": workflow_version,
                "user_id": user_id,
                "course_id": course_id,
                "admin_user_id": admin_user_id,
                "model": model,
                "temperature": safe_input.get("parameters", {}).get("temperature"),
                "max_tokens": safe_input.get("parameters", {}).get("max_tokens"),
                "input": safe_input,
                "prompt": safe_prompt,
                "response": safe_response,
                "metrics": safe_metrics,
                "created_at": datetime.utcnow(),
                "environment": self.environment,
                "server_version": self.server_version,
            }

            # Add to Firestore
            db.collection("ai_workflow_logs").add(log_entry)
            ai_usage_service.record(
                user_id,
                workflow_type,
                safe_metrics.get("input_tokens") or 0,
                safe_metrics.get("output_tokens") or 0,
                safe_metrics.get("cost_usd") or 0.0,
                db=db,
            )

            self.logger.info(
                f"[AI_WORKFLOW_LOG] {workflow_type} | "
                f"Success: {response.get('success')} | "
                f"Latency: {metrics.get('latency_ms')}ms | "
                f"User: {user_id or 'N/A'}"
            )

        except Exception as e:
            self.logger.error(f"Failed to log AI workflow: {str(e)}")

    @staticmethod
    def prepare_input_data(
        parameters: Dict[str, Any],
        image_data: Optional[Any] = None,
        source_id: Optional[str] = None,
        source_type: Optional[str] = None,
        educational_context: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """
        Prepare input data structure for logging

        Args:
            parameters: Request parameters (difficulty, num_questions, etc.)
            image_data: Image data (base64, bytes, or PIL Image)
            source_id: Source document/photo ID
            source_type: Type of source (e.g. document, course_material)
            educational_context: Dict with course_id, material_id, etc.

        Returns:
            Formatted input data dict
        """
        input_dict = {"parameters": parameters}

        # Add image metadata if image provided
        if image_data is not None:
            try:
                image_metadata = AIWorkflowLogger._extract_image_metadata(
                    image_data, source_id, source_type
                )
                input_dict["image_metadata"] = image_metadata
            except Exception as e:
                logging.error(f"Failed to extract image metadata: {str(e)}")

        # Add educational context if provided
        if educational_context:
            input_dict["educational_context"] = educational_context

        return input_dict

    @staticmethod
    def _extract_image_metadata(
        image_data: Any, source_id: Optional[str], source_type: Optional[str]
    ) -> Dict[str, Any]:
        """Extract metadata from image data"""
        metadata = {"source_id": source_id, "source_type": source_type}

        try:
            # Convert to PIL Image for metadata extraction
            pil_image = None

            if isinstance(image_data, str):
                # Base64 string
                if image_data.startswith("data:image"):
                    # Remove data URL prefix
                    image_data = image_data.split(",")[1]
                image_bytes = base64.b64decode(image_data)
                metadata["file_size"] = len(image_bytes)
                pil_image = Image.open(io.BytesIO(image_bytes))

            elif isinstance(image_data, bytes):
                # Raw bytes
                metadata["file_size"] = len(image_data)
                pil_image = Image.open(io.BytesIO(image_data))

            elif isinstance(image_data, Image.Image):
                # PIL Image
                pil_image = image_data
                # Estimate file size (rough)
                img_byte_arr = io.BytesIO()
                pil_image.save(img_byte_arr, format=pil_image.format or "PNG")
                metadata["file_size"] = len(img_byte_arr.getvalue())

            # Extract image properties
            if pil_image:
                metadata["dimensions"] = f"{pil_image.width}x{pil_image.height}"
                metadata["format"] = pil_image.format or "UNKNOWN"
                metadata["mode"] = pil_image.mode  # RGB, RGBA, etc.

        except Exception as e:
            logging.error(f"Error extracting image metadata: {str(e)}")
            metadata["error"] = str(e)

        return metadata

    @staticmethod
    def prepare_prompt_data(
        template_version: str,
        full_prompt: str,
        system_prompt: str,
        dynamic_values: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """
        Prepare prompt data structure for logging

        Args:
            template_version: Version identifier for the prompt template
            full_prompt: Complete rendered prompt sent to AI
            system_prompt: System instructions only
            dynamic_values: Dynamic values inserted into template

        Returns:
            Formatted prompt data dict
        """
        return {
            "template_version": template_version,
            "full_prompt": full_prompt,
            "system_prompt": system_prompt,
            "dynamic_values": dynamic_values or {},
        }

    @staticmethod
    def prepare_response_data(
        success: bool,
        raw_text: Optional[str] = None,
        parsed_json: Optional[Dict[str, Any]] = None,
        error_message: Optional[str] = None,
        validation_errors: Optional[list] = None,
    ) -> Dict[str, Any]:
        """
        Prepare response data structure for logging

        Args:
            success: Whether the AI call succeeded
            raw_text: Raw response text from AI
            parsed_json: Parsed JSON output
            error_message: Error message if failed
            validation_errors: List of validation error messages

        Returns:
            Formatted response data dict
        """
        return {
            "success": success,
            "raw_text": raw_text,
            "parsed_json": parsed_json,
            "error_message": error_message,
            "validation_errors": validation_errors,
        }

    @staticmethod
    def calculate_metrics(
        start_time: float,
        prompt_text: str,
        response_text: Optional[str],
        model: str,
        has_image: bool = False,
        output_count: Optional[int] = None,
    ) -> Dict[str, Any]:
        """
        Calculate performance metrics for the AI call

        Args:
            start_time: Time when request started (from time.time())
            prompt_text: Full prompt text
            response_text: Response text from AI
            model: Model name for pricing lookup
            has_image: Whether request included an image
            output_count: Number of items generated (questions, events, etc.)

        Returns:
            Metrics dict with latency, tokens, cost estimates
        """
        latency_ms = int((time.time() - start_time) * 1000)

        # Estimate tokens
        input_tokens = len(prompt_text) // CHARS_PER_TOKEN
        if has_image:
            input_tokens += TOKENS_PER_IMAGE

        output_tokens = 0
        if response_text:
            output_tokens = len(response_text) // CHARS_PER_TOKEN

        # Estimate cost
        cost_usd = 0.0
        if model in PRICING:
            input_cost = (input_tokens / 1_000_000) * PRICING[model]["input"]
            output_cost = (output_tokens / 1_000_000) * PRICING[model]["output"]
            cost_usd = input_cost + output_cost

        return {
            "latency_ms": latency_ms,
            "estimated_input_tokens": input_tokens,
            "estimated_output_tokens": output_tokens,
            "estimated_cost_usd": round(cost_usd, 6),
            "output_count": output_count,
        }

    @staticmethod
    def cleanup_old_logs(days: int = 30):
        """
        Clean up logs older than specified days

        Args:
            days: Number of days to retain logs (default: 30)
        """
        try:
            db = get_firestore_client()
            cutoff_date = datetime.utcnow() - timedelta(days=days)

            # Query old logs
            old_logs = (
                db.collection("ai_workflow_logs")
                .where("created_at", "<", cutoff_date)
                .stream()
            )

            # Delete in batches
            batch = db.batch()
            count = 0

            for log in old_logs:
                batch.delete(log.reference)
                count += 1

                # Commit batch every 500 documents
                if count % 500 == 0:
                    batch.commit()
                    batch = db.batch()

            # Commit remaining
            if count % 500 != 0:
                batch.commit()

            logging.info(f"Cleaned up {count} AI workflow logs older than {days} days")

        except Exception as e:
            logging.error(f"Failed to cleanup old AI workflow logs: {str(e)}")


# Global logger instance
ai_workflow_logger = AIWorkflowLogger()
