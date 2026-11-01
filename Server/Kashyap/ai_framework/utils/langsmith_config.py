"""
LangSmith Configuration and Integration
Provides observability and tracing for AI workflows
"""

import logging
import os
from contextlib import contextmanager
from typing import Any, Dict, Optional

logger = logging.getLogger(__name__)


def is_langsmith_enabled() -> bool:
    """
    Check if LangSmith is enabled via environment variables

    Returns:
        True if LangSmith is configured and enabled
    """
    api_key = os.getenv("LANGSMITH_API_KEY")
    tracing_enabled = os.getenv("LANGSMITH_TRACING_V2", "false").lower() == "true"

    return bool(api_key) and tracing_enabled


def get_langsmith_config() -> Optional[Dict[str, Any]]:
    """
    Get LangSmith configuration from environment

    Returns:
        Dict with LangSmith config or None if disabled
    """
    if not is_langsmith_enabled():
        return None

    return {
        "api_key": os.getenv("LANGSMITH_API_KEY"),
        "project": os.getenv("LANGSMITH_PROJECT", "wizly-ai-workflows"),
        "tracing_v2": os.getenv("LANGSMITH_TRACING_V2", "true"),
        "endpoint": os.getenv("LANGSMITH_ENDPOINT", "https://api.smith.langchain.com"),
    }


def configure_langsmith():
    """
    Configure LangSmith environment variables for tracing

    This should be called once at application startup.
    LangSmith will automatically trace LangGraph workflows when enabled.
    """
    if not is_langsmith_enabled():
        logger.info(
            "LangSmith tracing is disabled (no LANGSMITH_API_KEY or LANGSMITH_TRACING_V2=false)"
        )
        return

    config = get_langsmith_config()

    # Set environment variables for LangSmith SDK
    os.environ["LANGCHAIN_TRACING_V2"] = config["tracing_v2"]
    os.environ["LANGCHAIN_API_KEY"] = config["api_key"]
    os.environ["LANGCHAIN_PROJECT"] = config["project"]
    os.environ["LANGCHAIN_ENDPOINT"] = config["endpoint"]

    # Configure LangSmith to hide large inputs/outputs by default
    # This prevents payload size issues with large base64 images
    # Sanitized inputs are still available in trace metadata for debugging
    os.environ["LANGCHAIN_HIDE_INPUTS"] = os.getenv("LANGCHAIN_HIDE_INPUTS", "true")
    os.environ["LANGCHAIN_HIDE_OUTPUTS"] = os.getenv("LANGCHAIN_HIDE_OUTPUTS", "false")

    logger.info(f"✓ LangSmith tracing enabled for project: {config['project']}")
    logger.info(f"  View traces at: https://smith.langchain.com/")
    logger.info(f"  Input hiding enabled to avoid payload size limits")


@contextmanager
def trace_workflow(
    workflow_name: str,
    metadata: Optional[Dict[str, Any]] = None,
    tags: Optional[list] = None,
):
    """
    Context manager for tracing a specific workflow execution

    Usage:
        with trace_workflow("tutor_chat", metadata={"user_id": "123"}):
            result = workflow.execute(input_data)

    Args:
        workflow_name: Name of the workflow being traced
        metadata: Additional metadata to attach to the trace
        tags: Tags to categorize the trace

    Yields:
        Context for the workflow execution
    """
    if not is_langsmith_enabled():
        # No-op if LangSmith is disabled
        yield
        return

    try:
        from langsmith import trace

        # Prepare metadata
        trace_metadata = {"workflow_name": workflow_name, **(metadata or {})}

        # Prepare tags
        trace_tags = tags or []
        trace_tags.append(f"workflow:{workflow_name}")

        # Use LangSmith's trace decorator as context manager
        with trace(
            name=workflow_name,
            run_type="chain",
            metadata=trace_metadata,
            tags=trace_tags,
        ):
            yield

    except ImportError:
        logger.warning("langsmith package not installed, tracing disabled")
        yield
    except Exception as e:
        # Don't fail workflow if tracing fails
        logger.error(f"Error setting up LangSmith tracing: {str(e)}")
        yield


def get_trace_url(run_id: str) -> Optional[str]:
    """
    Get the LangSmith trace URL for a specific run

    Args:
        run_id: The run ID from LangSmith

    Returns:
        URL to view the trace, or None if LangSmith is disabled
    """
    if not is_langsmith_enabled():
        return None

    config = get_langsmith_config()
    project = config["project"]

    # LangSmith trace URL format
    return f"https://smith.langchain.com/public/{project}/r/{run_id}"


def log_workflow_metrics(
    workflow_name: str, metrics: Dict[str, Any], run_id: Optional[str] = None
):
    """
    Log custom metrics to LangSmith for a workflow run

    Args:
        workflow_name: Name of the workflow
        metrics: Dict of metrics to log (latency, quality_score, etc.)
        run_id: Optional run ID to associate metrics with
    """
    if not is_langsmith_enabled():
        return

    try:
        from langsmith import Client

        client = Client()

        # Create feedback/metrics for the run
        if run_id:
            # Log as feedback on the specific run
            for key, value in metrics.items():
                if isinstance(value, (int, float)):
                    client.create_feedback(run_id=run_id, key=key, score=value)
        else:
            # Log as general metrics
            logger.info(f"LangSmith metrics for {workflow_name}: {metrics}")

    except ImportError:
        logger.warning("langsmith package not installed, metrics logging disabled")
    except Exception as e:
        logger.error(f"Error logging metrics to LangSmith: {str(e)}")


# Auto-configure LangSmith on module import if enabled
# This ensures tracing is set up when the app starts
try:
    configure_langsmith()
except Exception as e:
    logger.error(f"Failed to configure LangSmith: {str(e)}")
