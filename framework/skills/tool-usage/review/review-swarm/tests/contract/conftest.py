"""Contract-слой review-swarm: настоящий swarm.py + настоящий adapter_contract.

Подделывается ТОЛЬКО исполняемый файл адаптера (внешний процесс) —
`review-harness/tests/stubs/stub_adapter.py`. Ни `ac.fork_participant`, ни
`ac.ask_participant`, ни функции `swarm.py` не подменяются.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

SKILLS_DIR = Path(__file__).resolve().parents[3]
HARNESS_TESTS = SKILLS_DIR / "review-harness" / "tests"
STUB_ADAPTER = HARNESS_TESTS / "stubs" / "stub_adapter.py"

for scripts_dir in (
    SKILLS_DIR / "review-swarm" / "scripts",
    SKILLS_DIR / "review-harness" / "scripts",
):
    if str(scripts_dir) not in sys.path:
        sys.path.insert(0, str(scripts_dir))


@pytest.fixture()
def stub_adapter(tmp_path, monkeypatch):
    """Стаб-адаптер + изолированное состояние. Репозиторные runtime-каталоги
    (`.swarm-sessions/`, `.review-sandboxes/`) не затрагиваются: всё в tmp_path."""
    state = tmp_path / "stub-adapter-state"
    state.mkdir()
    control_path = tmp_path / "stub-control.json"
    control_path.write_text("{}", encoding="utf-8")
    monkeypatch.setenv("STUB_ADAPTER_STATE", str(state))
    monkeypatch.setenv("STUB_ADAPTER_CONTROL", str(control_path))
    monkeypatch.setenv("STUB_ADAPTER_SEMANTICS", "coupled")

    def set_control(payload: dict) -> None:
        control_path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

    def invocations() -> list[dict]:
        path = state / "invocations.jsonl"
        if not path.exists():
            return []
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()
                if line.strip()]

    def model_calls() -> list[dict]:
        path = state / "model-calls.jsonl"
        if not path.exists():
            return []
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()
                if line.strip()]

    return {
        "path": str(STUB_ADAPTER),
        "cwd": tmp_path,
        "state": state,
        "set_control": set_control,
        "set_semantics": lambda value: monkeypatch.setenv("STUB_ADAPTER_SEMANTICS", value),
        "invocations": invocations,
        "model_calls": model_calls,
    }
