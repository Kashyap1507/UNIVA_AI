"""
Base Workflow Class for LangGraph AI Workflows
Provides common functionality and patterns for all workflows
"""

import logging
import time
from abc import ABC, abstractmethod
from typing import Any, Dict, Optional, Type, TypeVar

from langgraph.graph import END, StateGraph
from pydantic import BaseModel

from app.ai_framework.utils.gemini_client import GeminiClientFactory
from app.ai_framework.utils.image_utils import resize_image_for_langsmith
from app.ai_framework.utils.langsmith_config import is_langsmith_enabled, trace_workflow
from app.services.ai_workflow_logger import AIWorkflowLogger, ai_workflow_logger

T = TypeVar("T", bound=BaseModel)


def sanitize_input_for_tracing(input_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Create a sanitized copy of input data for LangSmith tracing.

    Replaces large binary data (like base64 images) with placeholder strings
    to avoid exceeding LangSmith's 20MB payload limit while preserving
    useful metadata for debugging.

    Args:
        input_data: Original input data dict

    Returns:
        Sanitized copy with large data replaced by placeholders
    """
    sanitized = {}

    for key, value in input_data.items():
        if key in ("image_data", "image", "base64_image") and isinstance(value, str):
            # Replace base64 image data with size placeholder
            size_mb = len(value) * 3 / 4 / (1024 * 1024)  # Estimate decoded size
            sanitized[key] = f"[IMAGE_DATA: {size_mb:.2f} MB base64]"
        elif key == "images" and isinstance(value, list):
            # Handle list of images
            sanitized[key] = [
                (
                    f"[IMAGE_DATA: {len(img) * 3 / 4 / (1024 * 1024):.2f} MB base64]"
                    if isinstance(img, str) and len(img) > 10000
                    else img
                )
                for img in value
            ]
        elif isinstance(value, str) and len(value) > 100000:
            # Any large string data (>100KB) - truncate for tracing
            sanitized[key] = f"[LARGE_DATA: {len(value) / 1024:.1f} KB truncated]"
        elif isinstance(value, dict):
            # Recursively sanitize nested dicts
            sanitized[key] = sanitize_input_for_tracing(value)
        else:
            sanitized[key] = value

    return sanitized


class BaseAIWorkflow(ABC):
    """
    Abstract base class for all AI workflows
    Provides common functionality:
    - Logging integration
    - Error handling
    - Metrics tracking
    - Gemini client access
    """

    def __init__(
        self,
        workflow_type: str,
        model_name: str = "gemini-2.5-flash",
        workflow_version: str = "1.0.0",
    ):
        """
        Initialize base workflow

        Args:
            workflow_type: Model router task (e.g. tutor_chat, work_feedback)
            model_name: Gemini model to use
            workflow_version: Version of this workflow implementation
        """
        self.workflow_type = workflow_type
        self.model_name = model_name
        self.workflow_version = workflow_version
        self.logger = logging.getLogger(self.__class__.__name__)

        # Get Gemini client
        self.gemini_client = GeminiClientFactory.get_client(model_name)

        # Compiled graph (set by subclasses)
        self.graph = None

    @abstractmethod
    def build_graph(self) -> StateGraph:
        """
        Build the LangGraph workflow
        Must be implemented by subclasses

        Returns:
            Compiled StateGraph
        """
        pass

    async def execute(
        self,
        input_data: Dict[str, Any],
        user_id: Optional[str] = None,
        course_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Execute the workflow with logging and error handling

        Args:
            input_data: Input parameters for the workflow
            user_id: Optional user ID for logging
            course_id: Optional course ID for logging

        Returns:
            Workflow execution result
        """
        start_time = time.time()
        success = False
        result = None
        error_message = None

        # Prepare LangSmith metadata with sanitized inputs
        # This allows debugging without exceeding payload limits
        sanitized_inputs = sanitize_input_for_tracing(input_data)
        trace_metadata = {
            "workflow_type": self.workflow_type,
            "workflow_version": self.workflow_version,
            "model": self.model_name,
            "user_id": user_id,
            "course_id": course_id,
            "sanitized_inputs": sanitized_inputs,  # Include sanitized for debugging
        }

        trace_tags = [
            f"workflow:{self.workflow_type}",
            f"version:{self.workflow_version}",
            f"model:{self.model_name}",
        ]

        # Run config to control LangSmith tracing behavior
        # Disable automatic input/output capture to avoid large payloads
        run_config = {
            "run_name": f"{self.workflow_type}_workflow",
            "metadata": {"has_large_inputs": True},
        }

        # Wrap execution in LangSmith trace
        with trace_workflow(
            workflow_name=f"{self.workflow_type}_workflow",
            metadata=trace_metadata,
            tags=trace_tags,
        ):
            try:
                # Build graph if not already built
                if self.graph is None:
                    self.graph = self.build_graph()

                # Execute workflow
                self.logger.info(
                    f"Executing {self.workflow_type} workflow with input: {input_data.keys()}"
                )
                if is_langsmith_enabled():
                    self.logger.info(
                        "  📊 LangSmith tracing enabled - view at https://smith.langchain.com/"
                    )

                # Pass run_config to control tracing behavior
                result = await self.graph.ainvoke(input_data, config=run_config)
                success = not result.get("error")

                if success:
                    self.logger.info(
                        f"{self.workflow_type} workflow completed successfully"
                    )
                else:
                    error_message = result.get("error", "Unknown error")
                    self.logger.error(
                        f"{self.workflow_type} workflow failed: {error_message}"
                    )

                return result

            except Exception as e:
                error_message = str(e)
                self.logger.error(
                    f"Error executing {self.workflow_type} workflow: {error_message}",
                    exc_info=True,
                )

                return {"error": error_message, "success": False}

            finally:
                # Log workflow execution
                self._log_execution(
                    start_time=start_time,
                    input_data=input_data,
                    result=result,
                    success=success,
                    error_message=error_message,
                    user_id=user_id,
                    course_id=course_id,
                )

    def execute_sync(
        self,
        input_data: Dict[str, Any],
        user_id: Optional[str] = None,
        course_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Synchronous version of execute()
        For compatibility with non-async code

        Args:
            input_data: Input parameters for the workflow
            user_id: Optional user ID for logging
            course_id: Optional course ID for logging

        Returns:
            Workflow execution result
        """
        start_time = time.time()
        success = False
        result = None
        error_message = None

        # Prepare LangSmith metadata with sanitized inputs
        # This allows debugging without exceeding payload limits
        sanitized_inputs = sanitize_input_for_tracing(input_data)
        trace_metadata = {
            "workflow_type": self.workflow_type,
            "workflow_version": self.workflow_version,
            "model": self.model_name,
            "user_id": user_id,
            "course_id": course_id,
            "execution_mode": "sync",
            "sanitized_inputs": sanitized_inputs,  # Include sanitized for debugging
        }

        trace_tags = [
            f"workflow:{self.workflow_type}",
            f"version:{self.workflow_version}",
            f"model:{self.model_name}",
            "sync",
        ]

        # Run config to control LangSmith tracing behavior
        # Disable automatic input/output capture to avoid large payloads
        run_config = {
            "run_name": f"{self.workflow_type}_workflow_sync",
            "metadata": {"has_large_inputs": True},
        }

        # Wrap execution in LangSmith trace
        with trace_workflow(
            workflow_name=f"{self.workflow_type}_workflow",
            metadata=trace_metadata,
            tags=trace_tags,
        ):
            try:
                # Build graph if not already built
                if self.graph is None:
                    self.graph = self.build_graph()

                # Execute workflow (synchronous)
                self.logger.info(
                    f"Executing {self.workflow_type} workflow (sync) with input: {input_data.keys()}"
                )
                if is_langsmith_enabled():
                    self.logger.info(
                        "  📊 LangSmith tracing enabled - view at https://smith.langchain.com/"
                    )

                # Pass run_config to control tracing behavior
                result = self.graph.invoke(input_data, config=run_config)
                success = not result.get("error")

                if success:
                    self.logger.info(
                        f"{self.workflow_type} workflow completed successfully"
                    )
                else:
                    error_message = result.get("error", "Unknown error")
                    self.logger.error(
                        f"{self.workflow_type} workflow failed: {error_message}"
                    )

                return result

            except Exception as e:
                error_message = str(e)
                self.logger.error(
                    f"Error executing {self.workflow_type} workflow: {error_message}",
                    exc_info=True,
                )

                return {"error": error_message, "success": False}

            finally:
                # Log workflow execution
                self._log_execution(
                    start_time=start_time,
                    input_data=input_data,
                    result=result,
                    success=success,
                    error_message=error_message,
                    user_id=user_id,
                    course_id=course_id,
                )

    def _log_execution(
        self,
        start_time: float,
        input_data: Dict[str, Any],
        result: Optional[Dict[str, Any]],
        success: bool,
        error_message: Optional[str],
        user_id: Optional[str],
        course_id: Optional[str],
    ):
        """
        Log workflow execution to ai_workflow_logs collection

        Args:
            start_time: Execution start time
            input_data: Input parameters
            result: Workflow result
            success: Whether execution succeeded
            error_message: Error message if failed
            user_id: User ID
            course_id: Course ID
        """
        try:
            # Calculate metrics
            latency_ms = int((time.time() - start_time) * 1000)

            # Resize image for LangSmith logging if needed (20MB limit)
            # Original image is still used for AI processing, only logging uses resized
            image_for_logging = resize_image_for_langsmith(input_data.get("image_data"))

            # Prepare input data for logging
            input_log_data = AIWorkflowLogger.prepare_input_data(
                parameters=input_data,
                image_data=image_for_logging,  # Use resized image for logging
                source_id=input_data.get("source_id"),
                source_type=input_data.get("source_type"),
                educational_context=input_data.get("educational_context"),
            )

            # Prepare prompt data (workflow doesn't have single prompt, but has structure)
            prompt_data = {
                "workflow_type": self.workflow_type,
                "workflow_version": self.workflow_version,
                "model": self.model_name,
            }

            # Prepare response data
            # Convert Pydantic models to JSON-serializable dicts for Firestore
            parsed_json = None
            if result:
                try:
                    # Check if result contains Pydantic models
                    if hasattr(result, 'model_dump'):
                        # Single Pydantic model - convert with mode='json' to serialize enums
                        parsed_json = result.model_dump(mode='json')
                    elif isinstance(result, dict):
                        # Dict that might contain Pydantic models
                        parsed_json = {}
                        for key, value in result.items():
                            if hasattr(value, 'model_dump'):
                                parsed_json[key] = value.model_dump(mode='json')
                            else:
                                parsed_json[key] = value
                    else:
                        parsed_json = result
                except Exception as e:
                    self.logger.warning(f"Failed to serialize result for logging: {e}")
                    parsed_json = str(result)

            response_data = AIWorkflowLogger.prepare_response_data(
                success=success,
                raw_text=str(result) if result else None,
                parsed_json=parsed_json,
                error_message=error_message,
                validation_errors=[error_message] if error_message else None,
            )

            # Prepare metrics
            metrics = {
                "latency_ms": latency_ms,
                "estimated_input_tokens": None,  # Could estimate based on input size
                "estimated_output_tokens": None,  # Could estimate based on output size
                "estimated_cost_usd": None,
                "output_count": self._count_outputs(result) if result else None,
            }

            # Log to database
            ai_workflow_logger.log_workflow(
                workflow_type=self.workflow_type,
                model=self.model_name,
                prompt=prompt_data,
                input_data=input_log_data,
                response=response_data,
                metrics=metrics,
                user_id=user_id,
                course_id=course_id,
                workflow_version=self.workflow_version,
            )

        except Exception as e:
            # Don't fail workflow if logging fails
            self.logger.error(f"Error logging workflow execution: {str(e)}")

    def _count_outputs(self, result: Dict[str, Any]) -> Optional[int]:
        """
        Count number of outputs in result (e.g., number of questions)
        Can be overridden by subclasses

        Args:
            result: Workflow result

        Returns:
            Count of outputs or None
        """
        # Default implementation - look for common patterns
        if "questions" in result:
            return len(result["questions"])
        elif "events" in result:
            return len(result["events"])
        return None


class WorkflowBuilder:
    """
    Helper class for building common workflow patterns
    Provides utility methods for common node types
    """

    @staticmethod
    def create_conditional_router(
        condition_func, routes: Dict[str, str], default_route: str = END
    ):
        """
        Create a conditional routing function for LangGraph

        Args:
            condition_func: Function that returns route key based on state
            routes: Dict mapping route keys to node names
            default_route: Default route if condition doesn't match

        Returns:
            Routing function compatible with add_conditional_edges
        """

        def router(state):
            route_key = condition_func(state)
            return routes.get(route_key, default_route)

        return router

    @staticmethod
    def create_quality_validator(min_quality_score: float = 0.8, max_retries: int = 2):
        """
        Create a quality validation node that checks output quality

        Args:
            min_quality_score: Minimum acceptable quality score (0-1)
            max_retries: Maximum retry attempts

        Returns:
            Quality validation function
        """

        def validate_quality(state):
            quality_score = state.get("quality_score", 0.0)
            retry_count = state.get("regeneration_count", 0)

            if quality_score >= min_quality_score:
                state["needs_regeneration"] = False
                return "pass"

            elif retry_count < max_retries:
                state["needs_regeneration"] = True
                state["regeneration_count"] = retry_count + 1
                return "retry"

            else:
                # Max retries reached, accept current quality
                state["needs_regeneration"] = False
                return "pass"

        return validate_quality
