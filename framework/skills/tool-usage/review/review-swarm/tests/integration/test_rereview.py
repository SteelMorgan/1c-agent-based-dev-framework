"""SI-09/SI-10 — re-review фиксов на stub CLI (RVSW-01, T-12, test-plan §6.2;
AC-09, retention FR-09).

Сценарий: полный прогон роя до REPORTED (находка F-001 автора claude-opus,
contested P3 — арбитраж тура 4 не обязателен), затем:
- SI-09: `rereview --finding F-001 --fix-diff fix.diff` — НОВАЯ focused-сессия
  того же адаптера автора (не resume старой); fix-diff доставлен в sandbox
  (materialize_diff + обязательный sync контракта); старая сессия не
  сохраняется/не resume'ится; вердикт пишется в session["rereview"] для
  track record (доля fixed в observations); парный cleanup новой сессии.
- SI-10: вердикт introduced_new_issue → новая находка в общем пуле
  (routed_findings, не в тред); конфликт с позицией разработчика →
  арбитражная ветка (переиспользование arbitrate из T-10).
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

import adapter_contract as ac
from swarm_case_helpers import (
    author_response_block,
    findings_block,
    read_invocations,
    verdict_block,
)

EXIT_PROTOCOL = 2


# ---------- Хелперы сценариев (паттерн test_swarm_cli.py) ----------

def _session_id(stdout: str) -> str:
    for line in stdout.splitlines():
        if line.startswith("session_id: "):
            return line.split(": ", 1)[1].strip()
    raise AssertionError(f"session_id не найден в выводе convene:\n{stdout}")


def _session(workdir, session_id: str) -> dict:
    path = workdir / ".swarm-sessions" / session_id / "session.json"
    assert path.exists(), f"session.json не найден: {path}"
    return json.loads(path.read_text(encoding="utf-8"))


def _finding(path, start, end, category, severity, claim, in_lens=True):
    return {
        "location": {"path": path, "line_start": start, "line_end": end},
        "category": category,
        "severity": severity,
        "in_lens": in_lens,
        "claim": claim,
        "evidence": f"evidence для {claim}",
        "rationale": "обоснование",
    }


def rereview_block(finding_id: str, verdict: str, path: str, line: int,
                   quote: str, **extra) -> str:
    """Ответ автора находки в re-review (fenced swarm-rereview, TD §5.5)."""
    payload = {
        "finding_id": finding_id,
        "verdict": verdict,
        "evidence": {"path": path, "line": line, "quote": quote},
        "rationale": "проверил фикс по коду workspace",
        **extra,
    }
    return ("Re-review вердикт.\n\n```swarm-rereview\n"
            + json.dumps(payload, ensure_ascii=False) + "\n```\n")


FIX_DIFF = (
    "diff --git a/src/a.py b/src/a.py\n"
    "--- a/src/a.py\n"
    "+++ b/src/a.py\n"
    "@@ -10,3 +10,4 @@\n"
    "+# FIX-MARKER: санитизация ввода\n"
)


def _run_to_reported(run_swarm, three_family_registry, reply_dir):
    """Полный прогон до REPORTED: F-001 (claude, P3) contested после тура 4."""
    three_family_registry()
    reply_dir("claude", 1, findings_block([
        _finding("src/a.py", 10, 12, "security", "P3", "INJECTION-CLAIM-ALPHA"),
        _finding("src/b.py", 5, 8, "correctness", "P4", "OFFBYONE-CLAIM-BETA",
                 in_lens=False),
    ]))
    reply_dir("codex", 1, findings_block([
        _finding("src/b.py", 6, 9, "correctness", "P3", "BOUNDS-CLAIM-GAMMA"),
    ]))
    reply_dir("kimi", 1, findings_block([]))
    reply_dir("codex", 2, verdict_block("F-001", "upheld", "src/a.py", 11, "quote-alpha-1"))
    reply_dir("kimi", 2, verdict_block("F-001", "overruled", "src/a.py", 12, "quote-alpha-2"))
    reply_dir("claude", 2, author_response_block(
        "F-001", "maintain",
        counter={"path": "src/a.py", "line": 13, "quote": "quote-alpha-3"}))
    reply_dir("codex", 3, verdict_block("F-001", "upheld", "src/a.py", 14, "quote-alpha-4"))
    reply_dir("kimi", 3, verdict_block("F-001", "overruled", "src/a.py", 15, "quote-alpha-5"))

    result = run_swarm(
        "convene", "--tier", "swarm",
        "--diff", "changes.diff",
        "--paths", "src/a.py", "src/b.py",
        "--caller", "claude-opus",
        "--timeout-sec", "60",
        "--registry", "adapters.yaml",
    )
    assert result.returncode == 0, result.stderr
    session_id = _session_id(result.stdout)
    for command in ("attack", "dedup", "assess", "rebut", "vote", "report"):
        result = run_swarm(command, session_id)
        assert result.returncode == 0, f"{command}: {result.stderr}"
    return session_id


def _sandbox_log(workdir, review_id: str) -> list[dict]:
    path = workdir / ".review-sandboxes" / review_id / "messages.ndjson"
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip()]


def _observations(workdir) -> list[dict]:
    path = workdir / ".swarm-track-record" / "observations.jsonl"
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip()]


# ---------- SI-09: новая focused-сессия, доставка fix-diff, track record ----------

def test_si_09_rereview_new_focused_session(run_swarm, three_family_registry,
                                            review_tree, reply_dir, stub_env):
    workdir = stub_env["cwd"]
    (workdir / "fix.diff").write_text(FIX_DIFF, encoding="utf-8")
    session_id = _run_to_reported(run_swarm, three_family_registry, reply_dir)
    reply_dir("claude", 3, rereview_block(
        "F-001", "fixed", "src/a.py", 10, "FIX-MARKER: санитизация ввода"))

    before = _session(workdir, session_id)
    author = next(p for p in before["participants"] if p["id"] == "claude-opus")
    old_review_id = author["review_id"]

    result = run_swarm("rereview", session_id,
                       "--finding", "F-001", "--fix-diff", "fix.diff")
    assert result.returncode == 0, result.stderr

    session = _session(workdir, session_id)
    assert session["state"] == "REREVIEW"
    # Вердикт — в session["rereview"] (контракт T-11 для track record).
    assert session["rereview"]["F-001"] == "fixed"

    entry = session["rereview_log"][-1]
    assert entry["finding_id"] == "F-001"
    assert entry["author_id"] == "claude-opus"
    # Новая focused-сессия ТОГО ЖЕ адаптера: review_id новый, не старый.
    assert entry["review_id"] != old_review_id
    assert entry["conflict"] is False

    # НЕ resume старой сессии: последний вызов claude — без --resume
    # (start новой сессии; resume был только в ask тура 3).
    claude_calls = [inv for inv in read_invocations(stub_env["state_dir"])
                    if inv["cli"] == "claude"]
    assert claude_calls, "вызовы claude не залогированы"
    assert "--resume" not in claude_calls[-1]["argv"], \
        "re-review обязан быть новой focused-сессией, не resume старой"

    # Fix-diff доставлен в sandbox новой сессии: файл материализован,
    # обязательный sync контракта выполнен (событие sync в логе участника).
    new_sandbox = workdir / ".review-sandboxes" / entry["review_id"]
    fix_file = new_sandbox / "workspace" / "fix.diff"
    assert fix_file.exists(), "fix.diff не материализован в sandbox re-review"
    assert fix_file.read_text(encoding="utf-8") == FIX_DIFF
    log_events = [event["type"] for event in _sandbox_log(workdir, entry["review_id"])]
    assert "sync" in log_events, "обязательный sync контракта не выполнен"
    assert entry["sync_ok"] is True

    # Retention: старая сессия не сохраняется и не используется —
    # старый sandbox не получал fix.diff и sync.
    old_sandbox = workdir / ".review-sandboxes" / old_review_id
    assert not (old_sandbox / "workspace" / "fix.diff").exists()
    old_events = [event["type"] for event in _sandbox_log(workdir, old_review_id)]
    assert "sync" not in old_events

    # Промпт re-review: исходная находка + ссылка на файл fix.diff.
    prompt = (workdir / ".swarm-sessions" / session_id / "prompts"
              / "rereview-F-001-claude-opus.md").read_text(encoding="utf-8")
    assert "INJECTION-CLAIM-ALPHA" in prompt
    assert "fix.diff" in prompt

    # Доля fixed в track record: observations перезаписаны (без дублей),
    # у автора fixed == 1.
    observations = [obs for obs in _observations(workdir)
                    if obs["review_session_id"] == session_id]
    assert len(observations) == len(session["participants"]), \
        "перезапись observations не должна дублировать строки сессии"
    author_obs = next(obs for obs in observations
                      if obs["participant_id"] == "claude-opus")
    assert author_obs["fixed"] == 1
    assert author_obs["partially"] == 0
    assert author_obs["not_fixed"] == 0

    # Парность cleanup: close закрывает и rereview-сессию.
    result = run_swarm("close", session_id)
    assert result.returncode == 0, result.stderr
    assert not (workdir / ".swarm-sessions" / session_id).exists()
    sandboxes = workdir / ".review-sandboxes"
    assert not sandboxes.exists() or all(
        ac.is_lock_only_tombstone(path) for path in sandboxes.iterdir()
    ), \
        "sandbox re-review закрыт вместе с участниками (парность start↔close)"


def test_si_09_rereview_requires_reported_state(run_swarm, three_family_registry,
                                                review_tree, reply_dir, stub_env):
    workdir = stub_env["cwd"]
    (workdir / "fix.diff").write_text(FIX_DIFF, encoding="utf-8")
    three_family_registry()
    reply_dir("claude", 1, findings_block([]))
    reply_dir("codex", 1, findings_block([]))
    reply_dir("kimi", 1, findings_block([]))
    result = run_swarm(
        "convene", "--tier", "swarm", "--diff", "changes.diff",
        "--paths", "src/a.py", "src/b.py", "--timeout-sec", "60",
        "--caller", "claude-opus",
        "--registry", "adapters.yaml",
    )
    assert result.returncode == 0, result.stderr
    session_id = _session_id(result.stdout)
    assert run_swarm("attack", session_id).returncode == 0

    # rereview до report — отказ fail-closed (state machine TD §6.1).
    result = run_swarm("rereview", session_id,
                       "--finding", "F-001", "--fix-diff", "fix.diff")
    assert result.returncode == EXIT_PROTOCOL
    run_swarm("close", session_id)


def test_si_09_rereview_same_finding_twice_refused(run_swarm, three_family_registry,
                                                   review_tree, reply_dir, stub_env):
    workdir = stub_env["cwd"]
    (workdir / "fix.diff").write_text(FIX_DIFF, encoding="utf-8")
    session_id = _run_to_reported(run_swarm, three_family_registry, reply_dir)
    reply_dir("claude", 3, rereview_block(
        "F-001", "fixed", "src/a.py", 10, "FIX-MARKER: санитизация ввода"))
    assert run_swarm("rereview", session_id,
                     "--finding", "F-001", "--fix-diff", "fix.diff").returncode == 0
    # Повторный re-review той же находки — отказ (вердикт зафиксирован).
    result = run_swarm("rereview", session_id,
                       "--finding", "F-001", "--fix-diff", "fix.diff")
    assert result.returncode == EXIT_PROTOCOL
    run_swarm("close", session_id)


# ---------- SI-10: introduced_new_issue → пул; конфликт → арбитраж ----------

def test_si_10_new_issue_to_pool_and_conflict_arbitration(
        run_swarm, three_family_registry, review_tree, reply_dir, stub_env):
    workdir = stub_env["cwd"]
    (workdir / "fix.diff").write_text(FIX_DIFF, encoding="utf-8")
    session_id = _run_to_reported(run_swarm, three_family_registry, reply_dir)
    new_issue = _finding("src/a.py", 20, 22, "correctness", "P2",
                         "REGRESSION-CLAIM-DELTA")
    reply_dir("claude", 3, rereview_block(
        "F-001", "introduced_new_issue", "src/a.py", 21, "сломанная граница",
        new_issue=new_issue))

    result = run_swarm("rereview", session_id,
                       "--finding", "F-001", "--fix-diff", "fix.diff")
    assert result.returncode == 0, result.stderr

    session = _session(workdir, session_id)
    # Конфликт «разработчик исправил / автор — нет»: вердикт НЕ финален,
    # в session["rereview"] не пишется до арбитража Оркестратора.
    assert "F-001" not in session["rereview"]
    entry = session["rereview_log"][-1]
    assert entry["conflict"] is True
    assert entry["verdict"] == "introduced_new_issue"

    # Новая находка — в общем пуле (routed_findings), не в треде F-001;
    # автор — автор re-review; нумерация сквозная пула.
    routed = session["routed_findings"]
    assert len(routed) == 1
    pooled = routed[0]
    assert pooled["author_id"] == "claude-opus"
    assert pooled["claim"] == "REGRESSION-CLAIM-DELTA"
    assert pooled["finding_id"] not in session["threads"]
    base_ids = {f["finding_id"] for f in session["findings"]}
    assert pooled["finding_id"] not in base_ids
    assert entry["new_issue_id"] == pooled["finding_id"]

    # Повторный rereview по находке с открытым конфликтом — отказ.
    result = run_swarm("rereview", session_id,
                       "--finding", "F-001", "--fix-diff", "fix.diff")
    assert result.returncode == EXIT_PROTOCOL

    # Арбитражная ветка (переиспользование arbitrate из T-10): невалидное
    # решение (пустой evidence_quote) — отказ.
    bad_decision = workdir / "bad-decision.json"
    bad_decision.write_text(json.dumps({
        "finding_id": "F-001", "decision": "fixed", "evidence_quote": "  ",
        "location": {"path": "src/a.py", "line": 10}, "rationale": "пусто",
    }), encoding="utf-8")
    result = run_swarm("arbitrate", session_id, "--finding", "F-001",
                       "--decision-file", "bad-decision.json")
    assert result.returncode == EXIT_PROTOCOL

    # Валидное решение Оркестратора: конфликт разрешён, вердикт финален.
    # F-09 (R-Final): цитата — реальный фрагмент location-файла (src/a.py:10).
    decision = workdir / "decision.json"
    decision.write_text(json.dumps({
        "finding_id": "F-001", "decision": "not_fixed",
        "evidence_quote": "# строка 10 модуля a",
        "location": {"path": "src/a.py", "line": 10},
        "rationale": "посмотрел спорное место: фикс не покрывает путь ввода",
    }), encoding="utf-8")
    result = run_swarm("arbitrate", session_id, "--finding", "F-001",
                       "--decision-file", "decision.json")
    assert result.returncode == 0, result.stderr

    session = _session(workdir, session_id)
    assert session["rereview"]["F-001"] == "not_fixed"
    entry = session["rereview_log"][-1]
    assert entry["arbitration"]["decision"] == "not_fixed"
    assert entry["arbitration"]["evidence_quote"]

    # Решение финальное: второй арбитраж по тому же конфликту — отказ.
    result = run_swarm("arbitrate", session_id, "--finding", "F-001",
                       "--decision-file", "decision.json")
    assert result.returncode == EXIT_PROTOCOL

    # Track record: вердикт арбитража учтён (not_fixed у автора).
    observations = [obs for obs in _observations(workdir)
                    if obs["review_session_id"] == session_id]
    author_obs = next(obs for obs in observations
                      if obs["participant_id"] == "claude-opus")
    assert author_obs["not_fixed"] == 1
    assert author_obs["fixed"] == 0

    assert run_swarm("close", session_id).returncode == 0
