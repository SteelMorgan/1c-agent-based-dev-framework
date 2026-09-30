"""Rework по блокерам cross-family completion review (cons-01-r-completion).

F-01 (FR-10): cleanup fail-closed + идемпотентный retry (FU-03 блокирующее).
F-02 (NFR-05): механическая read-only граница kimi через --agent-file.
F-05 (NFR-02): context_budget на полный собранный вход участника.
"""
from __future__ import annotations

import json
import os
from pathlib import Path

import pytest

import adapter_contract as ac
import consilium_core as core
from consilium_adapter_paths import ADAPTER_PATHS
from consilium_cleanup_assertions import assert_cleanup_checkpoint
from test_consilium_cli import (
    convene,
    read_session,
    session_dir,
    write_digest,
)

EXIT_CLEANUP_FAILED = 5


def _participant_review_id(workdir: Path, session_id: str, pid: str) -> str:
    session = read_session(workdir, session_id)
    review_id = next(p for p in session["participants"] if p["id"] == pid)["review_id"]
    assert review_id, f"{pid}: нет review_id"
    return review_id


def _corrupt_sandbox_meta(workdir: Path, review_id: str) -> None:
    """Инъекция сбоя close: review.json удалён → adapter close падает (sandbox есть)."""
    meta = workdir / ".review-sandboxes" / review_id / "review.json"
    assert meta.exists()
    meta.unlink()


def _kimi_argv_calls(workdir: Path) -> list[list[str]]:
    log = workdir / ".stub-state" / "invocations.jsonl"
    if not log.exists():
        return []
    return [
        json.loads(line)["argv"]
        for line in log.read_text(encoding="utf-8").splitlines()
        if line.strip() and json.loads(line)["cli"] == "kimi"
    ]


# ---------------------------------------------------------------------------
# F-01 (BLOCK, FR-10): cleanup fail-closed + retry
# ---------------------------------------------------------------------------

def test_f01_close_fail_closed_and_retry(run_consilium, stub_env, three_family_registry):
    """F-01: неуспешный close участника → сессия НЕ удалена, НЕ closed, статус failed
    с данными для retry, exit != 0; retry корректен и без ложного CLOSE FAILED (FU-03)."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0  # фаза A

    kimi_review = _participant_review_id(workdir, session_id, "kimi-k2")
    _corrupt_sandbox_meta(workdir, kimi_review)

    result = run_consilium("close", session_id, "--registry", str(registry))
    assert result.returncode == EXIT_CLEANUP_FAILED
    sdir = session_dir(workdir, session_id)
    assert sdir.exists(), "каталог сессии обязан сохраниться при failed cleanup"
    session = read_session(workdir, session_id)
    assert session["cleanup"]["session_closed"] is False
    assert session["cleanup"]["status"] == "failed"
    assert session["cleanup"]["failed_participants"] == ["kimi-k2"]
    assert session["cleanup"]["retry"]
    # track record ещё не записан (cleanup не завершён)
    assert not (workdir / ".consilium-track-record" / "observations.jsonl").exists()

    # retry: оператор убирает битый sandbox → идемпотентный close
    import shutil
    shutil.rmtree(workdir / ".review-sandboxes" / kimi_review)
    result = run_consilium("close", session_id, "--registry", str(registry))
    assert result.returncode == 0, f"retry close failed:\n{result.stdout}\n{result.stderr}"
    assert "CLOSE FAILED" not in result.stdout  # FU-03: уже закрытые — не ложные failure
    assert not sdir.exists()
    assert_cleanup_checkpoint(workdir)
    config = json.loads((workdir / ".consilium-track-record" / "config.json").read_text(encoding="utf-8"))
    assert config["consiliums_completed"] == 1  # track record записан ровно один раз


def test_f01b_terminate_respects_close_failure(run_consilium, stub_env, set_stub_mode, three_family_registry):
    """F-01b: аварийное завершение тоже fail-closed по cleanup; retry работает,
    track record не дублируется."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry, "--timeout-sec", "2")
    assert run_consilium("round", session_id).returncode == 0  # фаза A ok

    kimi_review = _participant_review_id(workdir, session_id, "kimi-k2")
    _corrupt_sandbox_meta(workdir, kimi_review)

    set_stub_mode("timeout")  # mass-unresponsive → кворум нарушен → terminate
    digest = write_digest(workdir, "digest.md")
    result = run_consilium("round", session_id, "--digest-file", digest)
    assert result.returncode == EXIT_CLEANUP_FAILED
    sdir = session_dir(workdir, session_id)
    assert sdir.exists(), "при failed cleanup сессия обязана сохраниться для retry"
    session = read_session(workdir, session_id)
    assert session["cleanup"]["status"] == "failed"
    assert "kimi-k2" in session["cleanup"]["failed_participants"]
    # terminated-исход зафиксирован в track record один раз
    observations = (workdir / ".consilium-track-record" / "observations.jsonl").read_text(encoding="utf-8")
    assert "terminated" in observations

    import shutil
    shutil.rmtree(workdir / ".review-sandboxes" / kimi_review)
    result = run_consilium("close", session_id, "--registry", str(registry))
    assert result.returncode == 0, f"retry close failed:\n{result.stdout}\n{result.stderr}"
    assert "CLOSE FAILED" not in result.stdout
    assert not sdir.exists()
    config = json.loads((workdir / ".consilium-track-record" / "config.json").read_text(encoding="utf-8"))
    assert config["consiliums_completed"] == 1  # без дубля записи


# ---------------------------------------------------------------------------
# F-02 (BLOCK, NFR-05): механическая read-only граница kimi (--agent-file)
# ---------------------------------------------------------------------------

def test_f02_kimi_readonly_agent_profile_by_default(run_consilium, stub_env, three_family_registry):
    """F-02: kimi-участник стартует с read-only agent profile по умолчанию;
    профиль материализуется адаптером и содержит allowlist только read-инструментов."""
    workdir = stub_env["cwd"]

    # уровень адаптера (контракт): start передаёт --agent-file без явного флага
    result = ac.start_participant(str(ADAPTER_PATHS["kimi"]), "Вопрос", [], workdir, timeout_sec=30)
    assert result.ok, result.error
    kimi_argv = _kimi_argv_calls(workdir)[-1]
    assert "--agent-file" in kimi_argv, f"--agent-file не передан: {kimi_argv}"
    profile_path = Path(kimi_argv[kimi_argv.index("--agent-file") + 1])
    assert profile_path.exists(), "профиль обязан быть материализован адаптером"
    content = profile_path.read_text(encoding="utf-8")
    tools_line = next(line for line in content.splitlines() if line.startswith("tools:"))
    assert tools_line == "tools: Read, Grep, Glob"
    assert "Write" not in tools_line and "Edit" not in tools_line and "Bash" not in tools_line
    assert "read-only" in content.lower()
    ac.close_participant(str(ADAPTER_PATHS["kimi"]), result.review_id, workdir)

    # уровень консилиума: участник через convene/round тоже идёт с профилем
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0
    kimi_argv = _kimi_argv_calls(workdir)[-1]
    assert "--agent-file" in kimi_argv
    result = run_consilium("close", session_id, "--registry", str(registry))
    assert result.returncode == 0


@pytest.mark.real_cli
@pytest.mark.skipif(os.environ.get("CONSILIUM_REAL_CLI") != "1", reason="живой вызов kimi: CONSILIUM_REAL_CLI=1")
def test_f02_kimi_readonly_agent_live(workdir):
    """F-02 (живой smoke): старт с read-only профилем проходит на реальном kimi."""
    result = ac.start_participant(
        str(ADAPTER_PATHS["kimi"]), "Ответь одним словом: ok.", [], workdir, timeout_sec=180,
    )
    assert result.ok, result.error
    assert result.text
    ac.close_participant(str(ADAPTER_PATHS["kimi"]), result.review_id, workdir)


# ---------------------------------------------------------------------------
# F-05 (BLOCK, NFR-02/AC-14): context_budget на полный вход
# ---------------------------------------------------------------------------

def test_f05_full_input_budget_fail_closed(run_consilium, stub_env, three_family_registry):
    """F-05: дайджест проходит lint, но полный вход (дайджест + bundle + инструкции)
    превышает context_budget участника → волна отклонена fail-closed, адаптер не вызывается."""
    workdir = stub_env["cwd"]
    registry = three_family_registry(**{"kimi-k2": {"context_budget": 500}})
    session_id = convene(run_consilium, registry)

    # раздуваем proposals: bundle раунда 1 будет большим (с валидным structured-блоком,
    # иначе ходы отклоняются по E-4)
    big_reply = workdir / "big-reply.md"
    big_reply.write_text(
        "x " * 4000
        + "\n\n```consilium-structured\n"
        + '{"elements": ["E1", "E2"], "new_findings": [], "position_changes": [], "borrowed": [], '
        + '"risk_checklist_responses": [{"item_id": "overengineering", "verdict": "clear"}, '
        + '{"item_id": "bounded-context-erosion", "verdict": "clear"}]}\n```\n',
        encoding="utf-8",
    )
    os.environ["STUB_REPLY_FILE"] = str(big_reply)
    try:
        assert run_consilium("round", session_id).returncode == 0  # фаза A
    finally:
        os.environ.pop("STUB_REPLY_FILE", None)

    digest = write_digest(workdir, "digest.md", "Короткий дайджест: модели M1..M3 близки.")
    kimi_calls_before = len(_kimi_argv_calls(workdir))
    result = run_consilium("round", session_id, "--digest-file", digest)
    assert result.returncode != 0
    assert "context_budget" in (result.stdout + result.stderr)
    # волна не запускалась: kimi НЕ вызывался
    assert len(_kimi_argv_calls(workdir)) == kimi_calls_before
    # состояние не продвинулось
    session = read_session(workdir, session_id)
    assert session["phase"] == "B"
    assert session["wave"] == "attack"
    result = run_consilium("close", session_id, "--registry", str(registry))
    assert result.returncode == 0
