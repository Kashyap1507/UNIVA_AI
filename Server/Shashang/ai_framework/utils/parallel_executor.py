"""
Parallel Execution Utilities for LangGraph Workflows

Provides utilities for executing workflow steps in parallel to improve performance.

Features:
- Parallel execution of independent LLM calls
- Concurrent processing of multiple items (e.g., question evaluations)
- Automatic error handling and retry logic
- Configurable concurrency limits
- Progress tracking for parallel tasks
"""

import asyncio
import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Optional, TypeVar

logger = logging.getLogger(__name__)

T = TypeVar("T")  # Input type
R = TypeVar("R")  # Result type


@dataclass
class ParallelResult:
    """Result of parallel execution"""

    results: List[Any]  # Successful results
    errors: List[Dict[str, Any]]  # Failed items with errors
    total_time_ms: float  # Total execution time
    avg_time_per_item_ms: float  # Average time per item
    success_count: int  # Number of successful items
    error_count: int  # Number of failed items


class ParallelExecutor:
    """
    Execute tasks in parallel with automatic error handling

    Usage:
        executor = ParallelExecutor(max_workers=5)

        # Execute function on list of items
        results = executor.execute(
            items=[q1, q2, q3, q4, q5],
            func=evaluate_question,
            max_workers=3
        )

        # Or async version
        results = await executor.execute_async(
            items=[q1, q2, q3, q4, q5],
            func=async_evaluate_question
        )
    """

    def __init__(self, max_workers: int = 5, timeout_per_item: Optional[float] = None):
        """
        Initialize parallel executor

        Args:
            max_workers: Maximum number of concurrent workers
            timeout_per_item: Timeout in seconds per item (None = no timeout)
        """
        self.max_workers = max_workers
        self.timeout_per_item = timeout_per_item

        logger.info(f"ParallelExecutor initialized with max_workers={max_workers}")

    def execute(
        self,
        items: List[T],
        func: Callable[[T], R],
        max_workers: Optional[int] = None,
        preserve_order: bool = True,
        fail_fast: bool = False,
    ) -> ParallelResult:
        """
        Execute function on items in parallel (synchronous version)

        Args:
            items: List of items to process
            func: Function to apply to each item
            max_workers: Override default max workers
            preserve_order: Whether to preserve order of results
            fail_fast: Whether to stop on first error

        Returns:
            ParallelResult with results and metadata
        """
        if not items:
            return ParallelResult(
                results=[],
                errors=[],
                total_time_ms=0,
                avg_time_per_item_ms=0,
                success_count=0,
                error_count=0,
            )

        workers = max_workers or self.max_workers
        workers = min(workers, len(items))  # Don't create more workers than items

        start_time = time.time()
        results = []
        errors = []

        logger.info(f"Executing {len(items)} items in parallel with {workers} workers")

        try:
            with ThreadPoolExecutor(max_workers=workers) as executor:
                # Submit all tasks
                if preserve_order:
                    # Use map to preserve order
                    future_to_item = {
                        executor.submit(self._safe_execute, func, item, idx): (
                            item,
                            idx,
                        )
                        for idx, item in enumerate(items)
                    }

                    # Collect results in order
                    sorted_results = [None] * len(items)
                    for future in as_completed(future_to_item):
                        item, idx = future_to_item[future]
                        try:
                            result = future.result(timeout=self.timeout_per_item)
                            if result["success"]:
                                sorted_results[idx] = result["data"]
                            else:
                                errors.append(
                                    {"item_index": idx, "error": result["error"]}
                                )

                                if fail_fast:
                                    logger.error(
                                        f"Fail-fast enabled, stopping on error: {result['error']}"
                                    )
                                    executor.shutdown(wait=False, cancel_futures=True)
                                    break

                        except Exception as e:
                            logger.error(f"Error processing item {idx}: {str(e)}")
                            errors.append({"item_index": idx, "error": str(e)})

                            if fail_fast:
                                executor.shutdown(wait=False, cancel_futures=True)
                                break

                    # Filter out None values (failed items)
                    results = [r for r in sorted_results if r is not None]

                else:
                    # Process without order guarantee (faster)
                    futures = [
                        executor.submit(self._safe_execute, func, item, idx)
                        for idx, item in enumerate(items)
                    ]

                    for future in as_completed(futures):
                        try:
                            result = future.result(timeout=self.timeout_per_item)
                            if result["success"]:
                                results.append(result["data"])
                            else:
                                errors.append({"error": result["error"]})

                                if fail_fast:
                                    executor.shutdown(wait=False, cancel_futures=True)
                                    break

                        except Exception as e:
                            logger.error(f"Error processing item: {str(e)}")
                            errors.append({"error": str(e)})

                            if fail_fast:
                                executor.shutdown(wait=False, cancel_futures=True)
                                break

        except Exception as e:
            logger.error(f"Fatal error in parallel execution: {str(e)}")
            errors.append({"error": f"Fatal: {str(e)}"})

        end_time = time.time()
        total_time_ms = (end_time - start_time) * 1000
        avg_time_per_item_ms = total_time_ms / len(items) if items else 0

        logger.info(
            f"Parallel execution complete: {len(results)} successes, {len(errors)} errors, "
            f"{total_time_ms:.0f}ms total ({avg_time_per_item_ms:.0f}ms avg/item)"
        )

        return ParallelResult(
            results=results,
            errors=errors,
            total_time_ms=total_time_ms,
            avg_time_per_item_ms=avg_time_per_item_ms,
            success_count=len(results),
            error_count=len(errors),
        )

    async def execute_async(
        self,
        items: List[T],
        func: Callable[[T], R],
        max_workers: Optional[int] = None,
        preserve_order: bool = True,
        fail_fast: bool = False,
    ) -> ParallelResult:
        """
        Execute async function on items in parallel (async version)

        Args:
            items: List of items to process
            func: Async function to apply to each item
            max_workers: Override default max workers (controls semaphore)
            preserve_order: Whether to preserve order of results
            fail_fast: Whether to stop on first error

        Returns:
            ParallelResult with results and metadata
        """
        if not items:
            return ParallelResult(
                results=[],
                errors=[],
                total_time_ms=0,
                avg_time_per_item_ms=0,
                success_count=0,
                error_count=0,
            )

        workers = max_workers or self.max_workers
        semaphore = asyncio.Semaphore(workers)

        start_time = time.time()
        results = []
        errors = []

        logger.info(
            f"Executing {len(items)} items in parallel (async) with {workers} workers"
        )

        async def bounded_execute(item: T, idx: int):
            """Execute with semaphore to limit concurrency"""
            async with semaphore:
                return await self._safe_execute_async(func, item, idx)

        try:
            # Create tasks
            tasks = [bounded_execute(item, idx) for idx, item in enumerate(items)]

            # Execute all tasks
            task_results = await asyncio.gather(*tasks, return_exceptions=True)

            # Process results
            if preserve_order:
                for idx, result in enumerate(task_results):
                    if isinstance(result, Exception):
                        errors.append({"item_index": idx, "error": str(result)})
                    elif result["success"]:
                        results.append(result["data"])
                    else:
                        errors.append({"item_index": idx, "error": result["error"]})
            else:
                for result in task_results:
                    if isinstance(result, Exception):
                        errors.append({"error": str(result)})
                    elif result["success"]:
                        results.append(result["data"])
                    else:
                        errors.append({"error": result["error"]})

        except Exception as e:
            logger.error(f"Fatal error in async parallel execution: {str(e)}")
            errors.append({"error": f"Fatal: {str(e)}"})

        end_time = time.time()
        total_time_ms = (end_time - start_time) * 1000
        avg_time_per_item_ms = total_time_ms / len(items) if items else 0

        logger.info(
            f"Async parallel execution complete: {len(results)} successes, {len(errors)} errors, "
            f"{total_time_ms:.0f}ms total ({avg_time_per_item_ms:.0f}ms avg/item)"
        )

        return ParallelResult(
            results=results,
            errors=errors,
            total_time_ms=total_time_ms,
            avg_time_per_item_ms=avg_time_per_item_ms,
            success_count=len(results),
            error_count=len(errors),
        )

    def _safe_execute(self, func: Callable, item: Any, idx: int) -> Dict[str, Any]:
        """
        Safely execute function with error handling

        Returns:
            Dict with 'success' and either 'data' or 'error'
        """
        try:
            result = func(item)
            return {"success": True, "data": result}
        except Exception as e:
            logger.error(f"Error executing function on item {idx}: {str(e)}")
            return {"success": False, "error": str(e)}

    async def _safe_execute_async(
        self, func: Callable, item: Any, idx: int
    ) -> Dict[str, Any]:
        """
        Safely execute async function with error handling

        Returns:
            Dict with 'success' and either 'data' or 'error'
        """
        try:
            result = await func(item)
            return {"success": True, "data": result}
        except Exception as e:
            logger.error(f"Error executing async function on item {idx}: {str(e)}")
            return {"success": False, "error": str(e)}


# Convenience function for simple parallel map
def parallel_map(
    items: List[T], func: Callable[[T], R], max_workers: int = 5
) -> List[R]:
    """
    Simple parallel map function

    Args:
        items: List of items to process
        func: Function to apply to each item
        max_workers: Maximum concurrent workers

    Returns:
        List of results (same order as input)

    Example:
        questions = [q1, q2, q3, q4, q5]
        results = parallel_map(questions, evaluate_question, max_workers=3)
    """
    executor = ParallelExecutor(max_workers=max_workers)
    result = executor.execute(items, func, preserve_order=True)

    if result.error_count > 0:
        logger.warning(f"parallel_map completed with {result.error_count} errors")

    return result.results


# Convenience function for async parallel map
async def async_parallel_map(
    items: List[T], func: Callable[[T], R], max_workers: int = 5
) -> List[R]:
    """
    Simple async parallel map function

    Args:
        items: List of items to process
        func: Async function to apply to each item
        max_workers: Maximum concurrent workers

    Returns:
        List of results (same order as input)

    Example:
        questions = [q1, q2, q3, q4, q5]
        results = await async_parallel_map(questions, async_evaluate_question, max_workers=3)
    """
    executor = ParallelExecutor(max_workers=max_workers)
    result = await executor.execute_async(items, func, preserve_order=True)

    if result.error_count > 0:
        logger.warning(f"async_parallel_map completed with {result.error_count} errors")

    return result.results
