"""
SM-2 spaced repetition, shared with the app (`commonMain/.../srs/Sm2.kt`).

Both sides must give identical results so reviews done offline schedule the
same way as reviews done online. Keep them in step: any change here needs the
same change in Sm2.kt and new vectors in tests/fixtures/srs_vectors.json.

Grades are 0-5. The app's buttons map to Again=1, Hard=3, Good=4, Easy=5.
Rounding is half-up (floor(x + 0.5)), never Python's banker's rounding.
"""

import math
from dataclasses import asdict, dataclass
from datetime import datetime, timedelta
from typing import Optional

DEFAULT_EASE = 2.5
MIN_EASE = 1.3
MAX_INTERVAL_DAYS = 3650
PASS_GRADE = 3


@dataclass
class SrsState:
    ease: float = DEFAULT_EASE
    interval_days: int = 0
    reps: int = 0
    lapses: int = 0

    @classmethod
    def from_dict(cls, data: Optional[dict]) -> "SrsState":
        data = data or {}
        return cls(
            ease=float(data.get("ease", DEFAULT_EASE)),
            interval_days=int(data.get("interval_days", 0)),
            reps=int(data.get("reps", 0)),
            lapses=int(data.get("lapses", 0)),
        )

    def to_dict(self) -> dict:
        return asdict(self)


def _round_half_up(x: float) -> int:
    return int(math.floor(x + 0.5))


def _round_ease(x: float) -> float:
    return math.floor(x * 1000 + 0.5) / 1000


def next_state(state: SrsState, grade: int) -> SrsState:
    """The SM-2 step. The interval uses the ease from before this review."""
    if not isinstance(grade, int) or isinstance(grade, bool) or not 0 <= grade <= 5:
        raise ValueError("grade must be an integer from 0 to 5")
    reps, lapses = state.reps, state.lapses
    if grade < PASS_GRADE:
        reps, interval, lapses = 0, 1, lapses + 1
    else:
        if reps == 0:
            interval = 1
        elif reps == 1:
            interval = 6
        else:
            interval = _round_half_up(state.interval_days * state.ease)
        reps += 1
    q = 5 - grade
    ease = max(MIN_EASE, _round_ease(state.ease + 0.1 - q * (0.08 + q * 0.02)))
    return SrsState(ease=ease, interval_days=min(max(interval, 1), MAX_INTERVAL_DAYS), reps=reps, lapses=lapses)


def due_after(reviewed_at: datetime, state: SrsState) -> datetime:
    return reviewed_at + timedelta(days=state.interval_days)
