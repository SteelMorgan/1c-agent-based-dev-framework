"""Общие фикстуры contract/integration-слоёв review-harness (RVSW-01, test-plan §2).

Переезд из `agent-consilium/tests/conftest.py` (T-04): ADAPTER_PATHS →
`scripts/adapters/` harness. Консилиум-специфичные фикстуры (реестр v2, transcript,
run_consilium) не переезжают — они остаются в тестах консилиума (FR-17).

Изоляция: каждый тест работает в tmp_path; каталог `.review-sandboxes/`
создаётся только там. Репозиторные runtime-каталоги тесты не трогают.
"""
from __future__ import annotations

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
ADAPTERS_DIR = SCRIPTS_DIR / "adapters"
STUBS_DIR = TESTS_DIR / "stubs"

# scripts/ доступен для import (adapter_contract и др.) во всех слоях
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

ADAPTER_PATHS = {
    "claude": ADAPTERS_DIR / "claude_opus_review.py",
    "codex": ADAPTERS_DIR / "codex_review.py",
    "kimi": ADAPTERS_DIR / "kimi_review.py",
}


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
    # Хранилище сессий Claude Code — только в tmp_path: и стаб CLI, и адаптер
    # читают один и тот же приватный контракт <config>/projects/<slug(cwd)>/…,
    # реальный ~/.claude тесты не трогают.
    claude_config = workdir / "claude-config"
    claude_config.mkdir(exist_ok=True)
    monkeypatch.setenv("CLAUDE_CONFIG_DIR", str(claude_config))
    return {
        "cwd": workdir,
        "bin": stub_bin,
        "state_dir": state_dir,
        "claude_config": claude_config,
    }


@pytest.fixture()
def set_stub_mode(monkeypatch):
    def _set(mode: str):
        monkeypatch.setenv("STUB_MODE", mode)
    return _set


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
