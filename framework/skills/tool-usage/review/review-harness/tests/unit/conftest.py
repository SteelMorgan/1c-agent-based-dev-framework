"""Unit-слой review-harness: доступ к scripts/ без установки пакета."""
import json
import sys
from pathlib import Path

import pytest

SCRIPTS_DIR = Path(__file__).resolve().parents[2] / "scripts"
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))


@pytest.fixture()
def structured_block():
    """Fenced JSON-блок структурированного хода (переезд из консилиума, TD 3.3)."""
    def _make(data: dict, fence_tag: str = "consilium-structured") -> str:
        return f"```{fence_tag}\n" + json.dumps(data, ensure_ascii=False) + "\n```"
    return _make


@pytest.fixture()
def track_record_factory():
    """Наборы observations (clean/tainted, датировка по seq) — переезд из консилиума."""
    def _make(entries):
        defaults = {
            "consilium_id": "cons-x",
            "date": "2026-07-28",
            "participant_id": "claude-opus",
            "family": "claude",
            "role": "architecture",
            "moderator_id": "primary",
            "moderator_is_participant": False,
            "findings_accepted": 0,
            "findings_withdrawn": 0,
            "critiques_upheld": 0,
            "critiques_overruled": 0,
            "model_killed": False,
            "unresponsive_events": 0,
            "consilium_seq": 1,
        }
        return [dict(defaults, **entry) for entry in entries]
    return _make
