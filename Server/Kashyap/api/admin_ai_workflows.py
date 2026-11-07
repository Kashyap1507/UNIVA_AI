"""
Admin AI Workflows API endpoints
Provides access to AI workflow logs and statistics for debugging
"""

import logging
from datetime import datetime, timedelta

from flask import request
from flask_restful import Resource

from app.services.firebase_service import get_firestore_client
from app.utils.decorators import admin_required


class AdminAIWorkflowsList(Resource):
    """List all AI workflows with summary statistics"""

    @admin_required
    def get(self, current_user=None):
        """
        Get list of all AI workflows with stats

        Query params:
            - days: Number of days to look back (default: 7)
        """
        try:
            db = get_firestore_client()
            days = int(request.args.get("days", 7))

            # Calculate date range
            end_date = datetime.utcnow()
            start_date = end_date - timedelta(days=days)

            # Query logs within date range
            logs_ref = db.collection("ai_workflow_logs")
            logs_query = logs_ref.where("created_at", ">=", start_date).where(
                "created_at", "<=", end_date
            )
            logs = logs_query.stream()

            # Aggregate statistics by workflow type (one entry per task seen in the logs)
            workflows = {}

            # Process logs
            for log_doc in logs:
                log_data = log_doc.to_dict()
                workflow_type = log_data.get("workflow_type")

                if workflow_type:
                    workflow = workflows.setdefault(
                        workflow_type,
                        {
                            "name": workflow_type.replace("_", " ").capitalize(),
                            "type": workflow_type,
                            "model": log_data.get("model"),
                            "total_executions": 0,
                            "successful": 0,
                            "failed": 0,
                            "total_latency_ms": 0,
                            "total_cost_usd": 0.0,
                            "last_execution": None,
                        },
                    )
                    workflow["total_executions"] += 1

                    # Track success/failure
                    if log_data.get("response", {}).get("success"):
                        workflow["successful"] += 1
                    else:
                        workflow["failed"] += 1

                    # Accumulate metrics
                    metrics = log_data.get("metrics", {})
                    workflow["total_latency_ms"] += metrics.get("latency_ms") or 0
                    workflow["total_cost_usd"] += metrics.get("estimated_cost_usd") or 0.0

                    # Track last execution
                    timestamp = log_data.get("timestamp")
                    if timestamp:
                        if isinstance(timestamp, datetime):
                            timestamp_str = timestamp.isoformat()
                        else:
                            timestamp_str = (
                                timestamp.isoformat()
                                if hasattr(timestamp, "isoformat")
                                else str(timestamp)
                            )

                        if (
                            not workflow["last_execution"]
                            or timestamp_str > workflow["last_execution"]
                        ):
                            workflow["last_execution"] = timestamp_str

            # Calculate averages
            for workflow in workflows.values():
                if workflow["total_executions"] > 0:
                    workflow["avg_latency_ms"] = int(
                        workflow["total_latency_ms"] / workflow["total_executions"]
                    )
                    workflow["success_rate"] = round(
                        (workflow["successful"] / workflow["total_executions"]) * 100, 1
                    )
                else:
                    workflow["avg_latency_ms"] = 0
                    workflow["success_rate"] = 0.0

                # Remove intermediate calculation fields
                del workflow["total_latency_ms"]
                workflow["total_cost_usd"] = round(workflow["total_cost_usd"], 6)

            return {
                "workflows": sorted(
                    workflows.values(), key=lambda w: w["total_executions"], reverse=True
                ),
                "date_range": {
                    "start": start_date.isoformat(),
                    "end": end_date.isoformat(),
                    "days": days,
                },
            }, 200

        except Exception as e:
            logging.error(f"Error in AdminAIWorkflowsList: {str(e)}")
            return {"error": f"Failed to fetch workflows: {str(e)}"}, 500


class AdminAIWorkflowLogs(Resource):
    """Get AI workflow execution logs with filters and pagination"""

    @admin_required
    def get(self, current_user=None):
        """
        Get AI workflow logs with pagination and filters

        Query params:
            - workflow_type: Filter by workflow type (tutor_chat, work_feedback, ...)
            - success: Filter by success status (true/false)
            - user_id: Filter by user ID
            - course_id: Filter by course ID
            - days: Number of days to look back (default: 7)
            - limit: Number of results per page (default: 50, max: 100)
            - offset: Pagination offset (default: 0)
        """
        try:
            db = get_firestore_client()

            # Parse query parameters
            workflow_type = request.args.get("workflow_type")
            success_filter = request.args.get("success")
            user_id = request.args.get("user_id")
            course_id = request.args.get("course_id")
            days = int(request.args.get("days", 7))
            limit = min(int(request.args.get("limit", 50)), 100)
            offset = int(request.args.get("offset", 0))

            # Calculate date range
            end_date = datetime.utcnow()
            start_date = end_date - timedelta(days=days)

            # Build query
            logs_ref = db.collection("ai_workflow_logs")
            query = logs_ref.where("created_at", ">=", start_date).where(
                "created_at", "<=", end_date
            )

            # Apply filters
            if workflow_type:
                query = query.where("workflow_type", "==", workflow_type)

            if user_id:
                query = query.where("user_id", "==", user_id)

            if course_id:
                query = query.where("course_id", "==", course_id)

            # Order by timestamp descending
            query = query.order_by("created_at", direction="DESCENDING")

            # Get all matching documents (we'll filter success and paginate in memory)
            logs_docs = list(query.stream())

            # Filter by success status if specified (in-memory filter)
            if success_filter is not None:
                success_bool = success_filter.lower() == "true"
                logs_docs = [
                    doc
                    for doc in logs_docs
                    if doc.to_dict().get("response", {}).get("success") == success_bool
                ]

            # Calculate total count
            total_count = len(logs_docs)

            # Apply pagination
            logs_docs = logs_docs[offset : offset + limit]

            # Format logs for response
            logs = []
            for log_doc in logs_docs:
                log_data = log_doc.to_dict()

                # Convert Firestore timestamp to ISO string
                timestamp = log_data.get("timestamp")
                if timestamp:
                    if isinstance(timestamp, datetime):
                        timestamp_str = timestamp.isoformat()
                    else:
                        timestamp_str = (
                            timestamp.isoformat()
                            if hasattr(timestamp, "isoformat")
                            else str(timestamp)
                        )
                else:
                    timestamp_str = None

                logs.append(
                    {
                        "id": log_doc.id,
                        "timestamp": timestamp_str,
                        "workflow_type": log_data.get("workflow_type"),
                        "model": log_data.get("model"),
                        "user_id": log_data.get("user_id"),
                        "course_id": log_data.get("course_id"),
                        "success": log_data.get("response", {}).get("success"),
                        "error_message": log_data.get("response", {}).get(
                            "error_message"
                        ),
                        "latency_ms": log_data.get("metrics", {}).get("latency_ms"),
                        "estimated_cost_usd": log_data.get("metrics", {}).get(
                            "estimated_cost_usd"
                        ),
                        "output_count": log_data.get("metrics", {}).get("output_count"),
                    }
                )

            return {
                "logs": logs,
                "pagination": {
                    "total": total_count,
                    "limit": limit,
                    "offset": offset,
                    "has_more": (offset + limit) < total_count,
                },
            }, 200

        except Exception as e:
            logging.error(f"Error in AdminAIWorkflowLogs: {str(e)}")
            return {"error": f"Failed to fetch logs: {str(e)}"}, 500


class AdminAIWorkflowLogDetail(Resource):
    """Get detailed information for a single AI workflow execution"""

    @admin_required
    def get(self, log_id, current_user=None):
        """
        Get detailed information for a single log entry

        Args:
            log_id: The Firestore document ID of the log entry
        """
        try:
            db = get_firestore_client()

            # Fetch log document
            log_ref = db.collection("ai_workflow_logs").document(log_id)
            log_doc = log_ref.get()

            if not log_doc.exists:
                return {"error": "Log not found"}, 404

            log_data = log_doc.to_dict()

            # Convert Firestore timestamps to ISO strings
            timestamp = log_data.get("timestamp")
            if timestamp:
                if isinstance(timestamp, datetime):
                    log_data["timestamp"] = timestamp.isoformat()
                elif hasattr(timestamp, "isoformat"):
                    log_data["timestamp"] = timestamp.isoformat()

            created_at = log_data.get("created_at")
            if created_at:
                if isinstance(created_at, datetime):
                    log_data["created_at"] = created_at.isoformat()
                elif hasattr(created_at, "isoformat"):
                    log_data["created_at"] = created_at.isoformat()

            # Add document ID
            log_data["id"] = log_doc.id

            return {"log": log_data}, 200

        except Exception as e:
            logging.error(f"Error in AdminAIWorkflowLogDetail: {str(e)}")
            return {"error": f"Failed to fetch log detail: {str(e)}"}, 500


class AdminAIWorkflowStats(Resource):
    """Get aggregated statistics for AI workflows"""

    @admin_required
    def get(self, current_user=None):
        """
        Get aggregated statistics across all workflows

        Query params:
            - days: Number of days to look back (default: 30)
        """
        try:
            db = get_firestore_client()
            days = int(request.args.get("days", 30))

            # Calculate date range
            end_date = datetime.utcnow()
            start_date = end_date - timedelta(days=days)

            # Query logs within date range
            logs_ref = db.collection("ai_workflow_logs")
            logs_query = logs_ref.where("created_at", ">=", start_date).where(
                "created_at", "<=", end_date
            )
            logs = logs_query.stream()

            # Initialize statistics
            stats = {
                "total_executions": 0,
                "successful_executions": 0,
                "failed_executions": 0,
                "total_cost_usd": 0.0,
                "total_latency_ms": 0,
                "by_workflow": {},
                "by_day": {},
            }

            # Process logs
            for log_doc in logs:
                log_data = log_doc.to_dict()
                workflow_type = log_data.get("workflow_type")

                stats["total_executions"] += 1

                # Track success/failure
                if log_data.get("response", {}).get("success"):
                    stats["successful_executions"] += 1
                else:
                    stats["failed_executions"] += 1

                # Accumulate metrics
                metrics = log_data.get("metrics", {})
                stats["total_cost_usd"] += metrics.get("estimated_cost_usd") or 0.0
                stats["total_latency_ms"] += metrics.get("latency_ms") or 0

                # By workflow type
                if workflow_type not in stats["by_workflow"]:
                    stats["by_workflow"][workflow_type] = {
                        "count": 0,
                        "successful": 0,
                        "failed": 0,
                        "cost_usd": 0.0,
                    }

                stats["by_workflow"][workflow_type]["count"] += 1
                if log_data.get("response", {}).get("success"):
                    stats["by_workflow"][workflow_type]["successful"] += 1
                else:
                    stats["by_workflow"][workflow_type]["failed"] += 1
                stats["by_workflow"][workflow_type]["cost_usd"] += metrics.get(
                    "estimated_cost_usd"
                ) or 0.0

                # By day
                timestamp = log_data.get("timestamp")
                if timestamp:
                    if isinstance(timestamp, datetime):
                        day_key = timestamp.strftime("%Y-%m-%d")
                    elif hasattr(timestamp, "strftime"):
                        day_key = timestamp.strftime("%Y-%m-%d")
                    else:
                        day_key = "unknown"

                    if day_key not in stats["by_day"]:
                        stats["by_day"][day_key] = {
                            "count": 0,
                            "successful": 0,
                            "failed": 0,
                        }

                    stats["by_day"][day_key]["count"] += 1
                    if log_data.get("response", {}).get("success"):
                        stats["by_day"][day_key]["successful"] += 1
                    else:
                        stats["by_day"][day_key]["failed"] += 1

            # Calculate averages
            if stats["total_executions"] > 0:
                stats["avg_latency_ms"] = int(
                    stats["total_latency_ms"] / stats["total_executions"]
                )
                stats["success_rate"] = round(
                    (stats["successful_executions"] / stats["total_executions"]) * 100,
                    1,
                )
            else:
                stats["avg_latency_ms"] = 0
                stats["success_rate"] = 0.0

            # Round cost
            stats["total_cost_usd"] = round(stats["total_cost_usd"], 6)

            # Remove intermediate calculation field
            del stats["total_latency_ms"]

            # Round costs in by_workflow
            for workflow_stats in stats["by_workflow"].values():
                workflow_stats["cost_usd"] = round(workflow_stats["cost_usd"], 6)

            return {
                "stats": stats,
                "date_range": {
                    "start": start_date.isoformat(),
                    "end": end_date.isoformat(),
                    "days": days,
                },
            }, 200

        except Exception as e:
            logging.error(f"Error in AdminAIWorkflowStats: {str(e)}")
            return {"error": f"Failed to fetch statistics: {str(e)}"}, 500


class AdminPromptManagement(Resource):
    """Manage prompts in LangSmith Hub"""

    @admin_required
    def post(self, current_user=None):
        """
        Push a prompt to LangSmith Hub

        Request body:
            {
                "name": "tutor-chat-answer",
                "content": "You are an expert...",
                "tags": ["prod", "v2"],
                "description": "Content detection prompt",
                "is_public": false
            }
        """
        try:
            from app.ai_framework.prompts import get_prompt_manager

            data = request.get_json()

            if not data or "name" not in data or "content" not in data:
                return {"error": "Missing required fields: name, content"}, 400

            prompt_manager = get_prompt_manager()

            success = prompt_manager.push_prompt(
                name=data["name"],
                content=data["content"],
                tags=data.get("tags", ["prod"]),
                description=data.get("description"),
                is_public=data.get("is_public", False),
            )

            if success:
                return {
                    "message": f"Prompt '{data['name']}' pushed to LangSmith Hub",
                    "name": data["name"],
                    "tags": data.get("tags", ["prod"]),
                }, 200
            else:
                return {
                    "error": "Failed to push prompt (check LangSmith configuration)"
                }, 500

        except Exception as e:
            logging.error(f"Error in AdminPromptManagement POST: {str(e)}")
            return {"error": f"Failed to push prompt: {str(e)}"}, 500

    @admin_required
    def get(self, current_user=None):
        """
        Get a prompt from LangSmith Hub

        Query params:
            - name: Prompt name (required)
            - tag: Prompt tag/version (default: prod)
        """
        try:
            from app.ai_framework.prompts import PromptConfig, get_prompt_manager

            name = request.args.get("name")
            tag = request.args.get("tag", "prod")

            if not name:
                return {"error": "Missing required parameter: name"}, 400

            prompt_manager = get_prompt_manager()

            prompt_result = prompt_manager.get_prompt(PromptConfig(name=name, tag=tag))

            return {
                "prompt": {
                    "name": prompt_result.name,
                    "tag": prompt_result.tag,
                    "content": prompt_result.content,
                    "source": prompt_result.source.value,
                    "metadata": prompt_result.metadata,
                }
            }, 200

        except Exception as e:
            logging.error(f"Error in AdminPromptManagement GET: {str(e)}")
            return {"error": f"Failed to fetch prompt: {str(e)}"}, 500


class AdminExperiments(Resource):
    """View and manage A/B test experiments"""

    @admin_required
    def get(self, current_user=None):
        """
        Get experiment results and comparisons

        Query params:
            - experiment_name: Name of experiment (required)
            - variant_a: First variant tag (optional)
            - variant_b: Second variant tag (optional)
        """
        try:
            from app.ai_framework.prompts import ExperimentTracker

            experiment_name = request.args.get("experiment_name")

            if not experiment_name:
                return {"error": "Missing required parameter: experiment_name"}, 400

            tracker = ExperimentTracker(experiment_name=experiment_name)

            variant_a = request.args.get("variant_a")
            variant_b = request.args.get("variant_b")

            # If variants specified, compare them
            if variant_a and variant_b:
                comparison = tracker.compare_variants(variant_a, variant_b)

                if not comparison:
                    return {"error": "Insufficient data for comparison"}, 400

                return {
                    "comparison": {
                        "variant_a": {
                            "tag": comparison.variant_a_tag,
                            "total_runs": comparison.variant_a_stats.total_runs,
                            "success_rate": comparison.variant_a_stats.success_rate,
                            "avg_latency_ms": comparison.variant_a_stats.avg_latency_ms,
                            "avg_quality_score": comparison.variant_a_stats.avg_quality_score,
                            "avg_cost_usd": comparison.variant_a_stats.avg_cost_usd,
                        },
                        "variant_b": {
                            "tag": comparison.variant_b_tag,
                            "total_runs": comparison.variant_b_stats.total_runs,
                            "success_rate": comparison.variant_b_stats.success_rate,
                            "avg_latency_ms": comparison.variant_b_stats.avg_latency_ms,
                            "avg_quality_score": comparison.variant_b_stats.avg_quality_score,
                            "avg_cost_usd": comparison.variant_b_stats.avg_cost_usd,
                        },
                        "results": {
                            "latency_winner": comparison.latency_winner.value,
                            "quality_winner": comparison.quality_winner.value,
                            "cost_winner": comparison.cost_winner.value,
                            "overall_winner": comparison.overall_winner.value,
                            "latency_p_value": comparison.latency_p_value,
                            "quality_p_value": comparison.quality_p_value,
                        },
                        "recommendation": comparison.recommendation,
                        "confidence": comparison.confidence,
                    }
                }, 200

            # Otherwise, return all experiment results
            else:
                results = tracker.export_results()

                return {"experiment": results}, 200

        except Exception as e:
            logging.error(f"Error in AdminExperiments GET: {str(e)}")
            return {"error": f"Failed to fetch experiment data: {str(e)}"}, 500
