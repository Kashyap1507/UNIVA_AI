"""
Per-thread meter for real Gemini token usage.

The Gemini client adds each call's `usage_metadata` here; the workflow logger
takes (and clears) the total when it writes the `ai_workflow_logs` entry, so
the log and the daily cost roll-up use real token counts. Every AI task runs
its calls and its log on one thread (request thread or background worker),
so a thread-local total belongs to exactly one workflow run.
"""

import threading
from typing import Dict, Optional

# USD per 1M tokens (paid tier, prompts up to 200k tokens).
PRICING = {
    "gemini-2.5-flash": {"input": 0.30, "output": 2.50},
    "gemini-2.5-pro": {"input": 1.25, "output": 10.00},
    "gemini-2.0-flash": {"input": 0.10, "output": 0.40},
    "gemini-1.5-flash": {"input": 0.075, "output": 0.30},
    "gemini-1.5-pro": {"input": 1.25, "output": 5.00},
    "gemini-embedding-001": {"input": 0.15, "output": 0.0},
}
DEFAULT_PRICE = PRICING["gemini-2.5-flash"]

_local = threading.local()


def cost_usd(model: Optional[str], tokens_in: int, tokens_out: int) -> float:
    price = PRICING.get((model or "").replace("models/", ""), DEFAULT_PRICE)
    return round(tokens_in / 1_000_000 * price["input"] + tokens_out / 1_000_000 * price["output"], 6)


def _totals() -> Dict:
    if not hasattr(_local, "totals"):
        _local.totals = {"calls": 0, "tokens_in": 0, "tokens_out": 0, "cost_usd": 0.0}
    return _local.totals


def add(model: Optional[str], tokens_in: int, tokens_out: int) -> None:
    t = _totals()
    t["calls"] += 1
    t["tokens_in"] += int(tokens_in or 0)
    t["tokens_out"] += int(tokens_out or 0)
    t["cost_usd"] = round(t["cost_usd"] + cost_usd(model, int(tokens_in or 0), int(tokens_out or 0)), 6)


def add_response(model: Optional[str], response) -> None:
    """Record a raw Gemini response's usage_metadata, if it has any."""
    usage = getattr(response, "usage_metadata", None)
    if usage is None:
        return
    tokens_in = getattr(usage, "prompt_token_count", 0) or 0
    tokens_out = (getattr(usage, "candidates_token_count", 0) or 0) + (getattr(usage, "thoughts_token_count", 0) or 0)
    if tokens_in or tokens_out:
        add(model, tokens_in, tokens_out)


def take() -> Optional[Dict]:
    """The usage since the last take (None if no call was metered), then reset."""
    t = _totals()
    if not t["calls"]:
        return None
    result = dict(t)
    reset()
    return result


def reset() -> None:
    _local.totals = {"calls": 0, "tokens_in": 0, "tokens_out": 0, "cost_usd": 0.0}
