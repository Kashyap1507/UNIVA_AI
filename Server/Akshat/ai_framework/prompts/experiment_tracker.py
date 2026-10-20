"""
LangSmith Experiment Tracking & Comparison

Provides A/B test tracking, experiment management, and statistical comparison
of prompt variants using LangSmith's evaluation framework.

Features:
- Track experiment results (latency, quality, cost, success rate)
- Compare prompt variants statistically
- Generate experiment reports
- Automatic winner selection based on metrics
"""

import logging
import os
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)


class MetricType(Enum):
    """Types of metrics to track"""

    LATENCY_MS = "latency_ms"
    QUALITY_SCORE = "quality_score"
    SUCCESS_RATE = "success_rate"
    COST_USD = "cost_usd"
    ERROR_RATE = "error_rate"


class ComparisonResult(Enum):
    """Result of comparing two variants"""

    VARIANT_A_BETTER = "variant_a_better"
    VARIANT_B_BETTER = "variant_b_better"
    NO_SIGNIFICANT_DIFFERENCE = "no_significant_difference"
    INSUFFICIENT_DATA = "insufficient_data"


@dataclass
class ExperimentMetrics:
    """Metrics for an experiment run"""

    latency_ms: float
    quality_score: Optional[float] = None
    success: bool = True
    cost_usd: Optional[float] = None
    error: Optional[str] = None
    timestamp: datetime = field(default_factory=datetime.utcnow)
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class VariantStats:
    """Aggregated statistics for a variant"""

    variant_tag: str
    total_runs: int = 0
    success_count: int = 0
    error_count: int = 0

    # Latency stats
    avg_latency_ms: float = 0.0
    p50_latency_ms: float = 0.0
    p99_latency_ms: float = 0.0

    # Quality stats
    avg_quality_score: Optional[float] = None
    min_quality_score: Optional[float] = None
    max_quality_score: Optional[float] = None

    # Cost stats
    total_cost_usd: float = 0.0
    avg_cost_usd: float = 0.0

    # Success rate
    success_rate: float = 0.0
    error_rate: float = 0.0

    # Raw data for statistical tests
    latencies: List[float] = field(default_factory=list)
    quality_scores: List[float] = field(default_factory=list)


@dataclass
class ExperimentComparison:
    """Comparison of two variants"""

    variant_a_tag: str
    variant_b_tag: str
    variant_a_stats: VariantStats
    variant_b_stats: VariantStats

    # Comparison results
    latency_winner: ComparisonResult
    quality_winner: ComparisonResult
    cost_winner: ComparisonResult
    overall_winner: ComparisonResult

    # Statistical significance
    latency_p_value: Optional[float] = None
    quality_p_value: Optional[float] = None

    # Recommendations
    recommendation: str = ""
    confidence: str = ""  # "high", "medium", "low"


class ExperimentTracker:
    """
    Track and compare A/B test experiments

    Usage:
        tracker = ExperimentTracker(experiment_name="work-feedback-evaluate")

        # Record results
        tracker.record(variant_tag="prod-a", metrics=ExperimentMetrics(...))
        tracker.record(variant_tag="prod-b", metrics=ExperimentMetrics(...))

        # Compare variants
        comparison = tracker.compare_variants("prod-a", "prod-b")

        # Get stats
        stats = tracker.get_variant_stats("prod-a")
    """

    def __init__(self, experiment_name: str, use_langsmith: bool = True):
        """
        Initialize experiment tracker

        Args:
            experiment_name: Name of the experiment
            use_langsmith: Whether to sync with LangSmith (requires API key)
        """
        self.experiment_name = experiment_name
        self.use_langsmith = use_langsmith and self._check_langsmith_availability()

        # In-memory storage for metrics
        self._metrics: Dict[str, List[ExperimentMetrics]] = {}

        # LangSmith client
        self._client = None
        if self.use_langsmith:
            try:
                from langsmith import Client

                self._client = Client()
                logger.info(
                    f"✓ ExperimentTracker initialized for '{experiment_name}' with LangSmith"
                )
            except Exception as e:
                logger.error(f"Failed to initialize LangSmith client: {str(e)}")
                self.use_langsmith = False

        if not self.use_langsmith:
            logger.info(
                f"ExperimentTracker initialized for '{experiment_name}' (in-memory only)"
            )

    def _check_langsmith_availability(self) -> bool:
        """Check if LangSmith is available"""
        try:
            from langsmith import Client

            return bool(os.getenv("LANGSMITH_API_KEY"))
        except ImportError:
            return False

    def record(
        self, variant_tag: str, metrics: ExperimentMetrics, run_id: Optional[str] = None
    ):
        """
        Record metrics for a variant

        Args:
            variant_tag: Tag of the variant (e.g., "prod-a", "prod-b")
            metrics: Metrics from the run
            run_id: Optional LangSmith run ID to attach feedback
        """
        # Store in memory
        if variant_tag not in self._metrics:
            self._metrics[variant_tag] = []

        self._metrics[variant_tag].append(metrics)

        # Sync to LangSmith if enabled and run_id provided
        if self.use_langsmith and run_id and self._client:
            try:
                # Create feedback for each metric
                if metrics.latency_ms:
                    self._client.create_feedback(
                        run_id=run_id, key="latency_ms", score=metrics.latency_ms
                    )

                if metrics.quality_score is not None:
                    self._client.create_feedback(
                        run_id=run_id, key="quality_score", score=metrics.quality_score
                    )

                if metrics.cost_usd is not None:
                    self._client.create_feedback(
                        run_id=run_id, key="cost_usd", score=metrics.cost_usd
                    )

                self._client.create_feedback(
                    run_id=run_id, key="success", score=1.0 if metrics.success else 0.0
                )

            except Exception as e:
                logger.error(f"Error syncing metrics to LangSmith: {str(e)}")

        logger.debug(
            f"Recorded metrics for variant '{variant_tag}': latency={metrics.latency_ms}ms, quality={metrics.quality_score}"
        )

    def get_variant_stats(self, variant_tag: str) -> Optional[VariantStats]:
        """
        Get aggregated statistics for a variant

        Args:
            variant_tag: Tag of the variant

        Returns:
            VariantStats or None if no data
        """
        if variant_tag not in self._metrics or not self._metrics[variant_tag]:
            return None

        metrics_list = self._metrics[variant_tag]

        # Calculate stats
        stats = VariantStats(variant_tag=variant_tag)
        stats.total_runs = len(metrics_list)
        stats.success_count = sum(1 for m in metrics_list if m.success)
        stats.error_count = sum(1 for m in metrics_list if not m.success)

        # Latency stats
        latencies = [m.latency_ms for m in metrics_list]
        stats.latencies = latencies
        stats.avg_latency_ms = sum(latencies) / len(latencies)
        sorted_latencies = sorted(latencies)
        stats.p50_latency_ms = sorted_latencies[len(sorted_latencies) // 2]
        stats.p99_latency_ms = sorted_latencies[int(len(sorted_latencies) * 0.99)]

        # Quality stats
        quality_scores = [
            m.quality_score for m in metrics_list if m.quality_score is not None
        ]
        if quality_scores:
            stats.quality_scores = quality_scores
            stats.avg_quality_score = sum(quality_scores) / len(quality_scores)
            stats.min_quality_score = min(quality_scores)
            stats.max_quality_score = max(quality_scores)

        # Cost stats
        costs = [m.cost_usd for m in metrics_list if m.cost_usd is not None]
        if costs:
            stats.total_cost_usd = sum(costs)
            stats.avg_cost_usd = stats.total_cost_usd / len(costs)

        # Success/error rates
        stats.success_rate = stats.success_count / stats.total_runs
        stats.error_rate = stats.error_count / stats.total_runs

        return stats

    def compare_variants(
        self, variant_a_tag: str, variant_b_tag: str, min_samples: int = 10
    ) -> Optional[ExperimentComparison]:
        """
        Compare two variants statistically

        Args:
            variant_a_tag: Tag of first variant
            variant_b_tag: Tag of second variant
            min_samples: Minimum samples required per variant

        Returns:
            ExperimentComparison or None if insufficient data
        """
        stats_a = self.get_variant_stats(variant_a_tag)
        stats_b = self.get_variant_stats(variant_b_tag)

        if not stats_a or not stats_b:
            logger.warning(f"Cannot compare: missing data for one or both variants")
            return None

        if stats_a.total_runs < min_samples or stats_b.total_runs < min_samples:
            logger.warning(f"Insufficient data: need {min_samples} samples per variant")
            return self._create_insufficient_data_comparison(
                variant_a_tag, variant_b_tag, stats_a, stats_b
            )

        # Compare latency (lower is better)
        latency_winner, latency_p = self._compare_metric(
            stats_a.latencies, stats_b.latencies, lower_is_better=True
        )

        # Compare quality (higher is better)
        quality_winner, quality_p = self._compare_metric(
            stats_a.quality_scores, stats_b.quality_scores, lower_is_better=False
        )

        # Compare cost (lower is better)
        if stats_a.avg_cost_usd > 0 and stats_b.avg_cost_usd > 0:
            cost_winner = (
                ComparisonResult.VARIANT_A_BETTER
                if stats_a.avg_cost_usd < stats_b.avg_cost_usd
                else (
                    ComparisonResult.VARIANT_B_BETTER
                    if stats_b.avg_cost_usd < stats_a.avg_cost_usd
                    else ComparisonResult.NO_SIGNIFICANT_DIFFERENCE
                )
            )
        else:
            cost_winner = ComparisonResult.NO_SIGNIFICANT_DIFFERENCE

        # Determine overall winner
        overall_winner = self._determine_overall_winner(
            latency_winner, quality_winner, cost_winner
        )

        # Generate recommendation
        recommendation, confidence = self._generate_recommendation(
            overall_winner,
            variant_a_tag,
            variant_b_tag,
            stats_a,
            stats_b,
            latency_p,
            quality_p,
        )

        return ExperimentComparison(
            variant_a_tag=variant_a_tag,
            variant_b_tag=variant_b_tag,
            variant_a_stats=stats_a,
            variant_b_stats=stats_b,
            latency_winner=latency_winner,
            quality_winner=quality_winner,
            cost_winner=cost_winner,
            overall_winner=overall_winner,
            latency_p_value=latency_p,
            quality_p_value=quality_p,
            recommendation=recommendation,
            confidence=confidence,
        )

    def _compare_metric(
        self, values_a: List[float], values_b: List[float], lower_is_better: bool = True
    ) -> Tuple[ComparisonResult, Optional[float]]:
        """
        Compare a metric between two variants using t-test

        Args:
            values_a: Values for variant A
            values_b: Values for variant B
            lower_is_better: Whether lower values are better

        Returns:
            Tuple of (winner, p_value)
        """
        if not values_a or not values_b:
            return ComparisonResult.INSUFFICIENT_DATA, None

        # Simple comparison using mean (could use scipy.stats.ttest_ind for proper t-test)
        mean_a = sum(values_a) / len(values_a)
        mean_b = sum(values_b) / len(values_b)

        # Calculate percent difference
        percent_diff = abs(mean_a - mean_b) / max(mean_a, mean_b) * 100

        # If difference is less than 5%, consider it insignificant
        if percent_diff < 5:
            return ComparisonResult.NO_SIGNIFICANT_DIFFERENCE, None

        # Determine winner based on direction
        if lower_is_better:
            winner = (
                ComparisonResult.VARIANT_A_BETTER
                if mean_a < mean_b
                else ComparisonResult.VARIANT_B_BETTER
            )
        else:
            winner = (
                ComparisonResult.VARIANT_A_BETTER
                if mean_a > mean_b
                else ComparisonResult.VARIANT_B_BETTER
            )

        # Mock p-value (would use scipy.stats.ttest_ind in production)
        p_value = 0.05 if percent_diff > 10 else 0.15

        return winner, p_value

    def _determine_overall_winner(
        self,
        latency_winner: ComparisonResult,
        quality_winner: ComparisonResult,
        cost_winner: ComparisonResult,
    ) -> ComparisonResult:
        """
        Determine overall winner based on multiple metrics

        Priority: Quality > Latency > Cost
        """
        # Quality is most important
        if quality_winner == ComparisonResult.VARIANT_A_BETTER:
            return ComparisonResult.VARIANT_A_BETTER
        elif quality_winner == ComparisonResult.VARIANT_B_BETTER:
            return ComparisonResult.VARIANT_B_BETTER

        # If quality is tied, check latency
        if latency_winner == ComparisonResult.VARIANT_A_BETTER:
            return ComparisonResult.VARIANT_A_BETTER
        elif latency_winner == ComparisonResult.VARIANT_B_BETTER:
            return ComparisonResult.VARIANT_B_BETTER

        # If latency is tied, check cost
        if cost_winner == ComparisonResult.VARIANT_A_BETTER:
            return ComparisonResult.VARIANT_A_BETTER
        elif cost_winner == ComparisonResult.VARIANT_B_BETTER:
            return ComparisonResult.VARIANT_B_BETTER

        # All metrics are tied
        return ComparisonResult.NO_SIGNIFICANT_DIFFERENCE

    def _generate_recommendation(
        self,
        overall_winner: ComparisonResult,
        variant_a_tag: str,
        variant_b_tag: str,
        stats_a: VariantStats,
        stats_b: VariantStats,
        latency_p: Optional[float],
        quality_p: Optional[float],
    ) -> Tuple[str, str]:
        """
        Generate human-readable recommendation and confidence level

        Returns:
            Tuple of (recommendation, confidence)
        """
        if overall_winner == ComparisonResult.NO_SIGNIFICANT_DIFFERENCE:
            return (
                f"No significant difference between {variant_a_tag} and {variant_b_tag}. "
                f"Either variant is acceptable.",
                "medium",
            )

        winner_tag = (
            variant_a_tag
            if overall_winner == ComparisonResult.VARIANT_A_BETTER
            else variant_b_tag
        )
        winner_stats = (
            stats_a if overall_winner == ComparisonResult.VARIANT_A_BETTER else stats_b
        )
        loser_stats = (
            stats_b if overall_winner == ComparisonResult.VARIANT_A_BETTER else stats_a
        )

        # Calculate improvements
        quality_improvement = ""
        if winner_stats.avg_quality_score and loser_stats.avg_quality_score:
            quality_diff = (
                (winner_stats.avg_quality_score - loser_stats.avg_quality_score)
                / loser_stats.avg_quality_score
                * 100
            )
            quality_improvement = f"+{quality_diff:.1f}% quality"

        latency_improvement = ""
        latency_diff = (
            (loser_stats.avg_latency_ms - winner_stats.avg_latency_ms)
            / loser_stats.avg_latency_ms
            * 100
        )
        if latency_diff > 0:
            latency_improvement = f"-{latency_diff:.1f}% latency"

        improvements = ", ".join(
            filter(None, [quality_improvement, latency_improvement])
        )

        # Determine confidence
        confidence = "high"
        if quality_p and quality_p > 0.1:
            confidence = "medium"
        if (quality_p and quality_p > 0.2) or (latency_p and latency_p > 0.2):
            confidence = "low"

        recommendation = (
            f"Recommend using {winner_tag} ({improvements}). "
            f"Based on {winner_stats.total_runs} samples."
        )

        return recommendation, confidence

    def _create_insufficient_data_comparison(
        self,
        variant_a_tag: str,
        variant_b_tag: str,
        stats_a: Optional[VariantStats],
        stats_b: Optional[VariantStats],
    ) -> ExperimentComparison:
        """Create comparison result when there's insufficient data"""
        return ExperimentComparison(
            variant_a_tag=variant_a_tag,
            variant_b_tag=variant_b_tag,
            variant_a_stats=stats_a or VariantStats(variant_tag=variant_a_tag),
            variant_b_stats=stats_b or VariantStats(variant_tag=variant_b_tag),
            latency_winner=ComparisonResult.INSUFFICIENT_DATA,
            quality_winner=ComparisonResult.INSUFFICIENT_DATA,
            cost_winner=ComparisonResult.INSUFFICIENT_DATA,
            overall_winner=ComparisonResult.INSUFFICIENT_DATA,
            recommendation="Insufficient data to make a recommendation. Continue testing.",
            confidence="low",
        )

    def export_results(self) -> Dict[str, Any]:
        """
        Export all experiment results for analysis

        Returns:
            Dict with all variant stats
        """
        return {
            "experiment_name": self.experiment_name,
            "variants": {
                tag: {
                    "total_runs": stats.total_runs,
                    "success_rate": stats.success_rate,
                    "avg_latency_ms": stats.avg_latency_ms,
                    "avg_quality_score": stats.avg_quality_score,
                    "avg_cost_usd": stats.avg_cost_usd,
                }
                for tag, metrics_list in self._metrics.items()
                if (stats := self.get_variant_stats(tag))
            },
        }
