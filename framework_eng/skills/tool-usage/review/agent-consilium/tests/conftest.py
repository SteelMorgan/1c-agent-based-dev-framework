"""Общие фикстуры тестовых слоёв консилиума (test-plan §2).

Изоляция: каждый тест работает в tmp_path; каталоги `.consilium-sessions/`,
`.review-sandboxes/`, `.consilium-track-record/` создаются только там.
"""
from __future__ import annotations

import json
import os
import shutil
import stat
import subprocess
import sys
from pathlib import Path

import pytest

TESTS_DIR = Path(__file__).resolve().parent
SKILL_DIR = TESTS_DIR.parent
SCRIPTS_DIR = SKILL_DIR / "scripts"
HARNESS_SCRIPTS = SKILL_DIR.parent / "review-harness" / "scripts"
STUBS_DIR = SKILL_DIR.parent / "review-harness" / "tests" / "stubs"
REPO_ROOT = SKILL_DIR.parent.parent.parent

# scripts/ доступен для import во всех слоях; adapter_contract и адаптеры —
# из review-harness (RVSW-01, TD §14.1; contract-слой переехал в harness-тесты).
if str(HARNESS_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(HARNESS_SCRIPTS))
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

# ADAPTER_PATHS — в consilium_adapter_paths.py (уникальное имя модуля: bare
# `from conftest import` неоднозначен при комбинированном прогоне наборов
# тестов разных скиллов в одном pytest-процессе).
from consilium_adapter_paths import ADAPTER_PATHS  # noqa: E402


@pytest.fixture()
def workdir(tmp_path, monkeypatch):
    """Временный cwd с runtime-каталогами; репозиторий не трогаем."""
    monkeypatch.chdir(tmp_path)
    return tmp_path


@pytest.fixture()
def stub_bin(tmp_path):
    """Исполняемые stub-бинари claude/codex/kimi в отдельном bin-каталоге."""
    bin_dir = tmp_path / "stub-bin"
    bin_dir.mkdir()
    stub = STUBS_DIR / "stub_cli.py"
    for name in ("claude", "codex", "kimi"):
        target = bin_dir / name
        shutil.copy(stub, target)
        target.chmod(target.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    return bin_dir


@pytest.fixture()
def stub_env(workdir, stub_bin, monkeypatch):
    """PATH со stub CLI + env управления режимами. Возвращает контекст."""
    monkeypatch.setenv("PATH", f"{stub_bin}{os.pathsep}{os.environ.get('PATH', '')}")
    state_dir = workdir / ".stub-state"
    monkeypatch.setenv("STUB_STATE_DIR", str(state_dir))
    monkeypatch.setenv("STUB_MODE", "ok")
    monkeypatch.delenv("STUB_REPLY_FILE", raising=False)
    return {
        "cwd": workdir,
        "bin": stub_bin,
        "state_dir": state_dir,
    }


@pytest.fixture()
def set_stub_mode(monkeypatch):
    def _set(mode: str):
        monkeypatch.setenv("STUB_MODE", mode)
    return _set


@pytest.fixture()
def registry_factory(workdir):
    """Пишет adapters.yaml в workdir; entries — список dict (схема v2, TD §4)."""
    def _make(entries, filename="adapters.yaml"):
        lines = ["version: 2", "participants:"]
        for entry in entries:
            first = True
            for key, value in entry.items():
                prefix = "  - " if first else "    "
                first = False
                if isinstance(value, bool):
                    rendered = "true" if value else "false"
                else:
                    rendered = str(value)
                lines.append(f"{prefix}{key}: {rendered}")
        path = workdir / filename
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return path
    return _make


# Дефолты v2-полей по семейству (зеркало review-harness/adapters.yaml, TD §4):
# квотный инвариант quota_introspection != "none" ⟺ quota_status == "proven".
_FAMILY_V2_DEFAULTS = {
    "claude": {"model": "claude-opus-5", "gate_legal": True,
               "quota_introspection": "none", "quota_status": "unavailable"},
    "codex": {"model": "gpt-5.6-sol", "gate_legal": True,
              "quota_introspection": "codex-rollout", "quota_status": "proven"},
    "kimi": {"model": "kimi-code/k3", "gate_legal": False,  # TBD-05: advisory only
             "quota_introspection": "none", "quota_status": "unavailable"},
}


@pytest.fixture()
def participant_entry():
    """Запись реестра v2 для реального адаптера (stub CLI на PATH)."""
    def _make(pid, family, enabled=True, context_budget=120000, model=None):
        defaults = _FAMILY_V2_DEFAULTS.get(family, {
            "model": f"{family}-model", "gate_legal": True,
            "quota_introspection": "none", "quota_status": "unavailable",
        })
        return {
            "id": pid,
            "family": family,
            "model": model or defaults["model"],
            "adapter": str(ADAPTER_PATHS.get(family, ADAPTER_PATHS["claude"])),
            "cli": family if family in ("claude", "codex", "kimi") else "claude",
            "context_budget": context_budget,
            "enabled": enabled,
            "gate_legal": defaults["gate_legal"],
            "quota_introspection": defaults["quota_introspection"],
            "quota_status": defaults["quota_status"],
        }
    return _make


@pytest.fixture()
def three_family_registry(registry_factory, participant_entry):
    """Стандартный MVP-состав: 3 участника, 3 family (ASM-03)."""
    def _make(**overrides):
        entries = [
            participant_entry("claude-opus", "claude", **overrides.get("claude-opus", {})),
            participant_entry("codex-gpt", "codex", **overrides.get("codex-gpt", {})),
            participant_entry("kimi-k2", "kimi", **overrides.get("kimi-k2", {})),
        ]
        return registry_factory(entries)
    return _make


@pytest.fixture()
def turn_factory():
    """Генератор записей transcript (test-plan §2)."""
    def _make(
        seq=None,
        phase="B",
        round=1,
        wave="attack",
        author="claude-opus",
        type="attack",
        refs=None,
        content="ход",
        elements=None,
        new_findings=None,
        position_changes=None,
        borrowed=None,
        structured_extra=None,
    ):
        return {
            "seq": seq,
            "phase": phase,
            "round": round,
            "wave": wave,
            "author": author,
            "type": type,
            "refs": refs or [],
            "content": content,
            "structured": {
                "elements": elements or [],
                "new_findings": new_findings or [],
                "position_changes": position_changes or [],
                "borrowed": borrowed or [],
                **(structured_extra or {}),
            },
        }
    return _make


@pytest.fixture()
def structured_block():
    """Fenced JSON-блок структурированного хода (TD 3.3)."""
    def _make(data: dict) -> str:
        return "```consilium-structured\n" + json.dumps(data, ensure_ascii=False) + "\n```"
    return _make


@pytest.fixture()
def track_record_factory():
    """Наборы observations (clean/tainted, датировка по consilium_seq)."""
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


def run_process(args, cwd, env_extra=None, timeout=120):
    """Запуск процесса с capture; возвращает CompletedProcess."""
    env = os.environ.copy()
    if env_extra:
        env.update(env_extra)
    return subprocess.run(
        args,
        cwd=str(cwd),
        env=env,
        capture_output=True,
        text=True,
        timeout=timeout,
        check=False,
    )


@pytest.fixture()
def run_consilium(stub_env):
    """Запуск CLI ядра consilium.py в изолированном cwd."""
    def _run(*args, timeout=180):
        return run_process(
            [sys.executable, str(SCRIPTS_DIR / "consilium.py"), *args],
            cwd=stub_env["cwd"],
            timeout=timeout,
        )
    return _run
