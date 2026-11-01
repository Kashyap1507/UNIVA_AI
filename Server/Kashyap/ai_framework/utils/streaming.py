"""
Streaming Response Utilities for Real-Time Workflow Feedback

Provides Server-Sent Events (SSE) support for streaming workflow progress
and partial results to clients in real-time.

Features:
- Step-by-step progress updates
- Partial result streaming
- Progress percentage tracking
- Compatible with Flask SSE
- Error handling and graceful degradation
"""

import json
import logging
import time
from dataclasses import asdict, dataclass
from enum import Enum
from queue import Empty, Queue
from threading import Thread
from typing import Any, Dict, Generator, List, Optional

logger = logging.getLogger(__name__)


class StreamEventType(Enum):
    """Types of streaming events"""

    WORKFLOW_START = "workflow_start"
    STEP_START = "step_start"
    STEP_PROGRESS = "step_progress"
    STEP_COMPLETE = "step_complete"
    PARTIAL_RESULT = "partial_result"
    WORKFLOW_COMPLETE = "workflow_complete"
    ERROR = "error"
    HEARTBEAT = "heartbeat"


@dataclass
class StreamEvent:
    """Event to stream to client"""

    event_type: StreamEventType
    data: Dict[str, Any]
    timestamp: float = None

    def __post_init__(self):
        if self.timestamp is None:
            self.timestamp = time.time()

    def to_sse_format(self) -> str:
        """
        Convert to Server-Sent Events format

        Returns:
            SSE-formatted string
        """
        event_data = {
            "type": self.event_type.value,
            "data": self.data,
            "timestamp": self.timestamp,
        }

        return f"data: {json.dumps(event_data)}\n\n"


class StreamManager:
    """
    Manages streaming events for workflow execution

    Usage:
        stream = StreamManager()

        # Emit events
        stream.emit_workflow_start("tutor_chat", metadata={...})
        stream.emit_step_start("detect_content", 1, 5)
        stream.emit_step_complete("detect_content", result={...})
        stream.emit_workflow_complete(final_result={...})

        # Get SSE generator for Flask
        return Response(stream.get_sse_generator(), mimetype='text/event-stream')
    """

    def __init__(self, buffer_size: int = 100):
        """
        Initialize stream manager

        Args:
            buffer_size: Maximum number of events to buffer
        """
        self._queue: Queue = Queue(maxsize=buffer_size)
        self._active = True
        self._heartbeat_interval = 15  # seconds

        logger.debug("StreamManager initialized")

    def emit_workflow_start(
        self,
        workflow_name: str,
        total_steps: int,
        metadata: Optional[Dict[str, Any]] = None,
    ):
        """
        Emit workflow start event

        Args:
            workflow_name: Name of the workflow
            total_steps: Total number of steps
            metadata: Additional metadata
        """
        event = StreamEvent(
            event_type=StreamEventType.WORKFLOW_START,
            data={
                "workflow_name": workflow_name,
                "total_steps": total_steps,
                "metadata": metadata or {},
            },
        )
        self._emit(event)

    def emit_step_start(
        self,
        step_name: str,
        step_number: int,
        total_steps: int,
        description: Optional[str] = None,
    ):
        """
        Emit step start event

        Args:
            step_name: Name of the step
            step_number: Current step number (1-indexed)
            total_steps: Total number of steps
            description: Human-readable description
        """
        progress = (step_number - 1) / total_steps * 100

        event = StreamEvent(
            event_type=StreamEventType.STEP_START,
            data={
                "step_name": step_name,
                "step_number": step_number,
                "total_steps": total_steps,
                "progress_percent": progress,
                "description": description or f"Starting {step_name}...",
            },
        )
        self._emit(event)

    def emit_step_progress(
        self, step_name: str, progress_percent: float, message: Optional[str] = None
    ):
        """
        Emit step progress event

        Args:
            step_name: Name of the step
            progress_percent: Progress percentage (0-100)
            message: Progress message
        """
        event = StreamEvent(
            event_type=StreamEventType.STEP_PROGRESS,
            data={
                "step_name": step_name,
                "progress_percent": progress_percent,
                "message": message or f"{progress_percent:.0f}% complete",
            },
        )
        self._emit(event)

    def emit_step_complete(
        self,
        step_name: str,
        step_number: int,
        total_steps: int,
        result: Optional[Dict[str, Any]] = None,
        duration_ms: Optional[float] = None,
    ):
        """
        Emit step complete event

        Args:
            step_name: Name of the step
            step_number: Current step number (1-indexed)
            total_steps: Total number of steps
            result: Step result (optional, for partial results)
            duration_ms: Step duration in milliseconds
        """
        progress = step_number / total_steps * 100

        event = StreamEvent(
            event_type=StreamEventType.STEP_COMPLETE,
            data={
                "step_name": step_name,
                "step_number": step_number,
                "total_steps": total_steps,
                "progress_percent": progress,
                "result": result,
                "duration_ms": duration_ms,
            },
        )
        self._emit(event)

    def emit_partial_result(
        self,
        result_type: str,
        result_data: Dict[str, Any],
        index: Optional[int] = None,
        total: Optional[int] = None,
    ):
        """
        Emit partial result event (e.g., individual question evaluation)

        Args:
            result_type: Type of result (e.g., "question_evaluation")
            result_data: The partial result data
            index: Index of this result (1-indexed)
            total: Total number of expected results
        """
        event = StreamEvent(
            event_type=StreamEventType.PARTIAL_RESULT,
            data={
                "result_type": result_type,
                "result": result_data,
                "index": index,
                "total": total,
            },
        )
        self._emit(event)

    def emit_workflow_complete(
        self,
        final_result: Dict[str, Any],
        total_duration_ms: Optional[float] = None,
        metadata: Optional[Dict[str, Any]] = None,
    ):
        """
        Emit workflow complete event

        Args:
            final_result: Final workflow result
            total_duration_ms: Total workflow duration
            metadata: Additional metadata
        """
        event = StreamEvent(
            event_type=StreamEventType.WORKFLOW_COMPLETE,
            data={
                "result": final_result,
                "total_duration_ms": total_duration_ms,
                "metadata": metadata or {},
            },
        )
        self._emit(event)
        self._active = False  # Stop streaming

    def emit_error(
        self,
        error_message: str,
        error_type: Optional[str] = None,
        step_name: Optional[str] = None,
    ):
        """
        Emit error event

        Args:
            error_message: Error message
            error_type: Type of error
            step_name: Step where error occurred
        """
        event = StreamEvent(
            event_type=StreamEventType.ERROR,
            data={
                "error": error_message,
                "error_type": error_type,
                "step_name": step_name,
            },
        )
        self._emit(event)
        self._active = False  # Stop streaming on error

    def _emit(self, event: StreamEvent):
        """
        Emit an event to the queue

        Args:
            event: Event to emit
        """
        try:
            self._queue.put_nowait(event)
            logger.debug(f"Emitted event: {event.event_type.value}")
        except Exception as e:
            logger.error(f"Failed to emit event: {str(e)}")

    def _emit_heartbeat(self):
        """Emit heartbeat to keep connection alive"""
        event = StreamEvent(
            event_type=StreamEventType.HEARTBEAT, data={"message": "Connection alive"}
        )
        try:
            self._queue.put_nowait(event)
        except:
            pass  # Don't fail on heartbeat errors

    def get_sse_generator(self) -> Generator[str, None, None]:
        """
        Get SSE generator for Flask Response

        Yields:
            SSE-formatted event strings

        Usage:
            from flask import Response
            return Response(stream.get_sse_generator(), mimetype='text/event-stream')
        """
        last_heartbeat = time.time()

        while self._active or not self._queue.empty():
            try:
                # Try to get event with timeout
                event = self._queue.get(timeout=1)
                yield event.to_sse_format()

                # Reset heartbeat timer
                last_heartbeat = time.time()

            except Empty:
                # Send heartbeat if needed
                if time.time() - last_heartbeat > self._heartbeat_interval:
                    self._emit_heartbeat()
                    event = self._queue.get(timeout=1)
                    yield event.to_sse_format()
                    last_heartbeat = time.time()

                continue

            except Exception as e:
                logger.error(f"Error in SSE generator: {str(e)}")
                # Emit error and stop
                error_event = StreamEvent(
                    event_type=StreamEventType.ERROR,
                    data={"error": f"Stream error: {str(e)}"},
                )
                yield error_event.to_sse_format()
                break

        logger.debug("SSE generator finished")

    def is_active(self) -> bool:
        """Check if stream is still active"""
        return self._active

    def close(self):
        """Close the stream"""
        self._active = False


class StreamingWorkflowWrapper:
    """
    Wrapper to add streaming capabilities to existing workflows

    Usage:
        # In workflow class
        def execute_with_streaming(self, input_data, user_id):
            stream = StreamingWorkflowWrapper(self)
            return stream.execute(input_data, user_id)
    """

    def __init__(self, workflow):
        """
        Initialize wrapper

        Args:
            workflow: The workflow instance to wrap
        """
        self.workflow = workflow
        self.stream_manager = StreamManager()

    def execute(
        self, input_data: Dict[str, Any], user_id: str, stream_events: bool = True
    ) -> Generator[str, None, None]:
        """
        Execute workflow with streaming

        Args:
            input_data: Workflow input data
            user_id: User ID
            stream_events: Whether to emit stream events

        Yields:
            SSE-formatted event strings
        """
        if not stream_events:
            # Fall back to non-streaming execution
            result = self.workflow.execute_sync(input_data, user_id)
            event = StreamEvent(
                event_type=StreamEventType.WORKFLOW_COMPLETE, data={"result": result}
            )
            yield event.to_sse_format()
            return

        # Execute in background thread
        result_queue = Queue()

        def execute_workflow():
            try:
                result = self.workflow.execute_sync(input_data, user_id)
                result_queue.put({"success": True, "result": result})
            except Exception as e:
                result_queue.put({"success": False, "error": str(e)})

        thread = Thread(target=execute_workflow, daemon=True)
        thread.start()

        # Emit start event
        self.stream_manager.emit_workflow_start(
            workflow_name=self.workflow.workflow_type,
            total_steps=5,  # Approximate
            metadata={"user_id": user_id},
        )

        # Stream events
        for event_str in self.stream_manager.get_sse_generator():
            yield event_str

        # Wait for workflow completion
        thread.join(timeout=60)

        # Get final result
        try:
            result_data = result_queue.get_nowait()
            if result_data["success"]:
                self.stream_manager.emit_workflow_complete(
                    final_result=result_data["result"]
                )
            else:
                self.stream_manager.emit_error(error_message=result_data["error"])
        except Empty:
            self.stream_manager.emit_error(error_message="Workflow timeout")

        # Stream final events
        for event_str in self.stream_manager.get_sse_generator():
            yield event_str


def create_sse_response(generator: Generator[str, None, None]):
    """
    Create Flask SSE response from generator

    Args:
        generator: SSE event generator

    Returns:
        Flask Response object

    Usage:
        from flask import Flask
        app = Flask(__name__)

        @app.route('/api/stream')
        def stream():
            stream = StreamManager()
            stream.emit_workflow_start(...)
            return create_sse_response(stream.get_sse_generator())
    """
    from flask import Response

    return Response(
        generator,
        mimetype="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",  # Disable nginx buffering
            "Connection": "keep-alive",
        },
    )
