"""Фикстуры integration-слоя review-swarm (RVSW-01, T-10, test-plan §2, §6.1).

Stub CLI — переиспользование harness `tests/stubs/stub_cli.py` (§2.1): режимы
ok/timeout/error/flaky + scripted-ответы через STUB_REPLY_DIR/<cli>-<n>.md;
argv-лог вызовов — invocations.jsonl (доказательство слепоты тура 1, SI-03).

Изоляция: каждый тест работает в tmp_path; `.swarm-sessions/`,
`.review-sandboxes/`, `.swarm-track-record/` создаются только там.
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
SKILL_DIR = TESTS_DIR.parents[1]
SCRIPTS_DIR = SKILL_DIR / "scripts"
HARNESS_DIR = SKILL_DIR.parent / "review-harness"
HARNESS_SCRIPTS = HARNESS_DIR / "scripts"
STUBS_DIR = HARNESS_DIR / "tests" / "stubs"

for scripts_dir in (SCRIPTS_DIR, HARNESS_SCRIPTS):
    if str(scripts_dir) not in sys.path:
        sys.path.insert(0, str(scripts_dir))

ADAPTER_PATHS = {
    "claude": HARNESS_SCRIPTS / "adapters" / "claude_opus_review.py",
    "codex": HARNESS_SCRIPTS / "adapters" / "codex_review.py",
    "kimi": HARNESS_SCRIPTS / "adapters" / "kimi_review.py",
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
    # См. review-harness/tests/conftest.py: приватное хранилище сессий Claude
    # Code изолируется в tmp_path, реальный ~/.claude не трогаем.
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


@pytest.fixture()
def reply_dir(stub_env, monkeypatch):
    """Scripted-ответы stub'а: STUB_REPLY_DIR/<cli>-<n>.md по счётчику вызовов."""
    directory = stub_env["cwd"] / "stub-replies"
    directory.mkdir()
    monkeypatch.setenv("STUB_REPLY_DIR", str(directory))

    def _write(cli: str, n: int, text: str) -> Path:
        path = directory / f"{cli}-{n}.md"
        path.write_text(text, encoding="utf-8")
        return path

    _write.dir = directory
    return _write


@pytest.fixture()
def registry_factory(workdir):
    """Пишет adapters.yaml схемы v2 (TD §4) в workdir; entries — список dict."""
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


@pytest.fixture()
def participant_entry():
    """Запись реестра v2 для реального адаптера (stub CLI на PATH)."""
    def _make(pid, family, enabled=True, gate_legal=True, model="stub-model"):
        return {
            "id": pid,
            "family": family,
            "model": model,
            "adapter": str(ADAPTER_PATHS.get(family, ADAPTER_PATHS["claude"])),
            "cli": family if family in ("claude", "codex", "kimi") else "claude",
            "context_budget": 120000,
            "enabled": enabled,
            "gate_legal": gate_legal,
            "quota_introspection": "none",
            "quota_status": "unavailable",
        }
    return _make


@pytest.fixture()
def three_family_registry(registry_factory, participant_entry):
    """Стандартный состав роя: 3 участника, 3 family."""
    def _make(**overrides):
        entries = [
            participant_entry("claude-opus", "claude", **overrides.get("claude-opus", {})),
            participant_entry("codex-gpt", "codex", **overrides.get("codex-gpt", {})),
            participant_entry("kimi-k2", "kimi", **overrides.get("kimi-k2", {})),
        ]
        return registry_factory(entries)
    return _make


@pytest.fixture()
def review_tree(workdir):
    """Проверяемый набор: исходники + diff (материализуется harness'ом)."""
    src = workdir / "src"
    src.mkdir()
    (src / "a.py").write_text(
        "\n".join(f"# строка {i} модуля a" for i in range(1, 41)) + "\n",
        encoding="utf-8",
    )
    (src / "b.py").write_text(
        "\n".join(f"# строка {i} модуля b" for i in range(1, 41)) + "\n",
        encoding="utf-8",
    )
    diff = (
        "diff --git a/src/a.py b/src/a.py\n"
        "--- a/src/a.py\n"
        "+++ b/src/a.py\n"
        "@@ -10,3 +10,4 @@\n"
        "+# изменение в a\n"
        "diff --git a/src/b.py b/src/b.py\n"
        "--- a/src/b.py\n"
        "+++ b/src/b.py\n"
        "@@ -5,3 +5,4 @@\n"
        "+# изменение в b\n"
    )
    (workdir / "changes.diff").write_text(diff, encoding="utf-8")
    return {
        "paths": ["src/a.py", "src/b.py"],
        "diff": "changes.diff",
        "diff_content": diff,
    }


def run_process(args, cwd, env_extra=None, timeout=180):
    env = os.environ.copy()
    if env_extra:
        env.update(env_extra)
    return subprocess.run(
        args, cwd=str(cwd), env=env, capture_output=True, text=True,
        timeout=timeout, check=False,
    )


@pytest.fixture()
def run_swarm(stub_env):
    """Запуск production CLI state machine swarm.py в изолированном cwd."""
    def _run(*args, timeout=180):
        return run_process(
            [sys.executable, str(SCRIPTS_DIR / "swarm.py"), *args],
            cwd=stub_env["cwd"],
            timeout=timeout,
        )
    return _run


# ---------- Хелперы scripted-ответов (structured-блоки роя) ----------
# Живут в swarm_case_helpers.py (уникальное имя модуля — bare `from conftest
# import` неоднозначен при комбинированном прогоне нескольких наборов тестов);
# здесь — реэкспорт для обратной совместимости.
from swarm_case_helpers import (  # noqa: E402,F401
    author_response_block,
    findings_block,
    read_invocations,
    verdict_block,
)
