"""SI-11..SI-14 — integration-слой лёгкого тарифа и gate-семантики
(RVSW-01, T-13, test-plan §6.3; FR-15/FR-16, AC-16/AC-19/AC-24; TD §7).

Stub CLI: harness tests/stubs/stub_cli.py (scripted-ответы STUB_REPLY_DIR).
Gate-вердикт — отдельный structured-вызов ПОСЛЕ report и диспозиций
(fenced swarm-gate-verdict); ≤3 итераций → эскалация (Hard Rule 16).
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

import adapter_contract as ac
from swarm_case_helpers import findings_block, verdict_block

EXIT_PROTOCOL = 2


# ---------- Хелперы сценариев ----------

def _session_id(stdout: str) -> str:
    for line in stdout.splitlines():
        if line.startswith("session_id: "):
            return line.split(": ", 1)[1].strip()
    raise AssertionError(f"session_id не найден в выводе convene:\n{stdout}")


def _session(workdir, session_id: str) -> dict:
    path = workdir / ".swarm-sessions" / session_id / "session.json"
    assert path.exists(), f"session.json не найден: {path}"
    return json.loads(path.read_text(encoding="utf-8"))


def _report(workdir, session_id: str) -> str:
    return (workdir / ".swarm-sessions" / session_id / "report.md").read_text(
        encoding="utf-8")


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


def _gate_block(payload: dict) -> str:
    return ("Gate-вердикт.\n\n```swarm-gate-verdict\n"
            + json.dumps(payload, ensure_ascii=False) + "\n```\n")


def _completion_block(decision: str, rationale="позиция gate-ревьюера") -> str:
    return _gate_block({"decision": decision, "findings": [],
                        "rationale": rationale, "escalation_needed": False})


def _acceptance_block(verdict: str, positions=None,
                      rationale="позиция gate-ревьюера") -> str:
    return _gate_block({"verdict": verdict, "positions": positions or [],
                        "rationale": rationale})


def _dispositions_file(workdir, mapping: dict, name="dispositions.json") -> str:
    (workdir / name).write_text(json.dumps(mapping, ensure_ascii=False),
                                encoding="utf-8")
    return name


def _convene_light(run_swarm, caller, gate=None):
    args = [
        "convene", "--tier", "light",
        "--diff", "changes.diff",
        "--paths", "src/a.py", "src/b.py",
        "--caller", caller,
        "--timeout-sec", "60",
        "--registry", "adapters.yaml",
    ]
    if gate:
        args += ["--gate", gate]
    result = run_swarm(*args)
    assert result.returncode == 0, result.stderr
    return _session_id(result.stdout)


def _assert_clean_close(run_swarm, workdir, session_id):
    result = run_swarm("close", session_id)
    assert result.returncode == 0, result.stderr
    assert not (workdir / ".swarm-sessions" / session_id).exists()
    sandboxes = workdir / ".review-sandboxes"
    assert not sandboxes.exists() or all(
        ac.is_lock_only_tombstone(path) for path in sandboxes.iterdir()
    ), \
        "обязательный close во всех ветках: парность start↔close (§6.3.9)"


# ---------- SI-11: acceptance-bound лёгкий тариф (FR-16) ----------

def test_si_11_light_acceptance_gate(run_swarm, three_family_registry,
                                     review_tree, reply_dir, stub_env):
    three_family_registry()
    reply_dir("codex", 1, findings_block([
        _finding("src/a.py", 10, 12, "security", "P2", "INJECTION-CLAIM-ALPHA"),
        _finding("src/b.py", 5, 8, "correctness", "P4", "OFFBYONE-CLAIM-BETA"),
    ]))
    session_id = _convene_light(run_swarm, caller="claude-opus", gate="acceptance")
    workdir = stub_env["cwd"]

    session = _session(workdir, session_id)
    assert session["tier"] == "light"
    assert len(session["participants"]) == 1
    reviewer = session["participants"][0]
    # Literal caller исключён; rotation выбирает следующий eligible adapter.
    assert reviewer["id"] != "claude-opus"
    assert reviewer["id"] == "codex-gpt"
    assert reviewer["gate_pass"] is True  # gate-прогон вне advisory-статистики
    assert session["gate"]["mode"] == "acceptance"
    assert session["gate"]["reviewer_id"] == "codex-gpt"
    assert session["gate"]["status"] == "pending"
    assert session["quota_mode"] == "blind"
    assert "introspection unavailable" in session["quota_fallback_reason"]

    # туров 2–4 в лёгком тарифе нет — команды полного тарифа отклоняются
    assert run_swarm("attack", session_id).returncode == 0
    result = run_swarm("dedup", session_id)
    assert result.returncode == EXIT_PROTOCOL
    assert "light" in result.stderr

    result = run_swarm("report", session_id)
    assert result.returncode == 0, result.stderr
    report = _report(workdir, session_id)
    # репорт по review-report-template: findings с ID, секция диспозиций, trace
    for marker in ("F-001", "F-002", "INJECTION-CLAIM-ALPHA", "Findings",
                   "Позиция primary agent", "Acceptance Trace",
                   "Gate-проход", "статус: pending",
                   "cross_family_reviewer_id: codex-gpt",
                   "cross_family_reviewer_family: codex"):
        assert marker in report, f"в репорте лёгкого тарифа нет {marker!r}"

    # acceptance gate без диспозиций Оркестратора — fail-closed
    result = run_swarm("gate-verdict", session_id)
    assert result.returncode == EXIT_PROTOCOL
    assert "dispositions" in result.stderr

    # диспозиции agree/out_of_scope (полный enum валидируется unit-слоем)
    dispositions = _dispositions_file(workdir, {"F-001": "agree",
                                                "F-002": "out_of_scope"})
    reply_dir("codex", 2, _acceptance_block(
        "conditional_accept",
        positions=[{"finding_id": "F-002", "position": "withdrawn"}]))
    result = run_swarm("gate-verdict", session_id,
                       "--dispositions-file", dispositions)
    assert result.returncode == 0, result.stderr
    session = _session(workdir, session_id)
    gate = session["gate"]
    # F-007 (E2E-02): conditional_accept — промежуточный статус, НЕ approved:
    # требуется явное решение Оркестратора (confirm|reject)
    assert gate["status"] == "conditional"
    assert gate["iterations"] == 1
    assert gate["dispositions"] == {"F-001": "agree", "F-002": "out_of_scope"}
    assert gate["verdicts"][0]["verdict"]["verdict"] == "conditional_accept"
    assert gate["conditional"]["resolution"] is None

    # явное подтверждение условий Оркестратором → терминальный approved
    result = run_swarm("gate-verdict", session_id,
                       "--conditional-decision", "confirm")
    assert result.returncode == 0, result.stderr
    gate = _session(workdir, session_id)["gate"]
    assert gate["status"] == "approved"
    assert gate["conditional"]["resolution"]["decision"] == "confirm"

    # review trace: диспозиции и вердикт зафиксированы в репорте
    report = _report(workdir, session_id)
    for marker in ("F-001 → agree", "F-002 → out_of_scope",
                   "статус: approved", "conditional_accept"):
        assert marker in report, f"в review trace нет {marker!r}"

    _assert_clean_close(run_swarm, workdir, session_id)

    # track record: observations с gate_pass вне strengths (FR-12/FR-16);
    # счётчик лёгких ревью — на close (калибровочная квота TD §8.2)
    track_dir = workdir / ".swarm-track-record"
    observations = [json.loads(line) for line in
                    (track_dir / "observations.jsonl").read_text(
                        encoding="utf-8").splitlines() if line.strip()]
    assert len(observations) == 1
    assert observations[0]["participant_id"] == "codex-gpt"
    assert observations[0]["gate_pass"] is True
    assert observations[0]["quota_mode"] == "blind"
    strengths = json.loads((track_dir / "strengths.json").read_text(encoding="utf-8"))
    assert strengths == {}, "gate_pass-наблюдение исключено из advisory-статистики"
    config = json.loads((track_dir / "config.json").read_text(encoding="utf-8"))
    assert config["light_reviews_completed"] == 1
    assert config["last_light_reviewer"] == "codex-gpt"


# ---------- F-01 (R-Final): diff в sandbox лёгкого тарифа к первому ходу ----------

def test_f01_light_diff_in_sandbox_at_first_move(run_swarm, three_family_registry,
                                                 review_tree, reply_dir, stub_env):
    """F-01 (R-Final), лёгкий тариф: единственный ревьюер получает diff в
    sandbox ДО первого хода — файл уходит в paths старта адаптера."""
    three_family_registry()
    reply_dir("codex", 1, findings_block([]))
    session_id = _convene_light(run_swarm, caller="claude-opus")
    workdir = stub_env["cwd"]
    result = run_swarm("attack", session_id)
    assert result.returncode == 0, result.stderr

    session = _session(workdir, session_id)
    reviewer = session["participants"][0]
    workspace = (workdir / ".review-sandboxes" / reviewer["review_id"] / "workspace")
    staged = workspace / ".swarm-sessions" / session_id / "review.diff"
    assert staged.exists(), (
        "лёгкий тариф: diff не скопирован из paths старта — ревьюер не видел "
        "review.diff на первом ходу (F-01)"
    )
    assert staged.read_text(encoding="utf-8") == review_tree["diff_content"]

    # F-12: промпт лёгкого тарифа ссылается на фактический относительный путь
    # staged-файла (`.swarm-sessions/<sid>/review.diff`), а не на корневой
    # review.diff, который материализуется только ПОСЛЕ первого хода
    prompt = (workdir / ".swarm-sessions" / session_id / "prompts"
              / f"tour1-{reviewer['id']}.md").read_text(encoding="utf-8")
    assert f".swarm-sessions/{session_id}/review.diff" in prompt, (
        "лёгкий тариф: промпт не содержит путь staged-файла (F-12)"
    )

    _assert_clean_close(run_swarm, workdir, session_id)


# ---------- SI-12: finalization gate (APPROVE/BLOCK, ≤3 итераций, эскалация) ----------

def test_si_12_completion_gate_block_iterations_escalation(
        run_swarm, three_family_registry, review_tree, reply_dir, stub_env):
    three_family_registry()
    reply_dir("claude", 1, findings_block([]))
    session_id = _convene_light(run_swarm, caller="codex-gpt", gate="completion")
    workdir = stub_env["cwd"]
    session = _session(workdir, session_id)
    assert session["participants"][0]["id"] == "claude-opus"  # не literal caller
    assert session["gate"]["mode"] == "completion"

    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("report", session_id).returncode == 0

    # ≤3 итераций BLOCK (rework → sync → повторный gate-verdict) → эскалация
    for iteration in (2, 3, 4):
        reply_dir("claude", iteration, _completion_block(
            "BLOCK_COMPLETION", rationale=f"разногласие, итерация {iteration - 1}"))
    statuses = []
    for expected in ("blocked", "blocked", "escalated"):
        result = run_swarm("gate-verdict", session_id)
        assert result.returncode == 0, result.stderr
        statuses.append(_session(workdir, session_id)["gate"]["status"])
    assert statuses == ["blocked", "blocked", "escalated"]

    gate = _session(workdir, session_id)["gate"]
    assert gate["iterations"] == 3
    # дельта-итерации делали sync после rework (TD §7.1)
    assert len(gate["sync_log"]) == 2
    assert all(entry["sync_ok"] for entry in gate["sync_log"])
    # эскалация зафиксирована с обеими позициями (Hard Rule 16)
    escalation = gate["escalation"]
    assert escalation is not None
    assert len(escalation["reviewer_positions"]) == 3
    report = _report(workdir, session_id)
    assert "ЭСКАЛАЦИЯ" in report
    assert "статус: escalated" in report

    # 4-й вызов — fail-closed: итерации исчерпаны, эскалация зафиксирована
    result = run_swarm("gate-verdict", session_id)
    assert result.returncode == EXIT_PROTOCOL
    assert "escalated" in result.stderr or "исчерпан" in result.stderr

    _assert_clean_close(run_swarm, workdir, session_id)


def test_si_12_completion_gate_approve(run_swarm, three_family_registry,
                                       review_tree, reply_dir, stub_env):
    three_family_registry()
    reply_dir("claude", 1, findings_block([]))
    reply_dir("claude", 2, _completion_block("APPROVE_COMPLETION"))
    session_id = _convene_light(run_swarm, caller="codex-gpt", gate="completion")
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("report", session_id).returncode == 0

    result = run_swarm("gate-verdict", session_id)
    assert result.returncode == 0, result.stderr
    assert _session(workdir, session_id)["gate"]["status"] == "approved"
    report = _report(workdir, session_id)
    assert "APPROVE_COMPLETION" in report
    assert "статус: approved" in report

    # повторный вызов после approve — отказ (вердикт финальный)
    result = run_swarm("gate-verdict", session_id)
    assert result.returncode == EXIT_PROTOCOL

    _assert_clean_close(run_swarm, workdir, session_id)


# ---------- SI-13: gate-ревьюер в полном рое (AC-24, критичный путь + acceptance) ----------

def test_si_13_gate_reviewer_inside_swarm(run_swarm, three_family_registry,
                                          review_tree, reply_dir, stub_env):
    three_family_registry()
    workdir = stub_env["cwd"]
    # критичный путь (карта критичности secrets/**) + acceptance-bound артефакт
    (workdir / "secrets").mkdir()
    (workdir / "secrets" / "keys.txt").write_text("key-1\nkey-2\n",
                                                  encoding="utf-8")

    reply_dir("claude", 1, findings_block([
        _finding("src/a.py", 10, 12, "security", "P3", "INJECTION-CLAIM-ALPHA"),
    ]))
    reply_dir("codex", 1, findings_block([]))
    reply_dir("kimi", 1, findings_block([]))
    reply_dir("codex", 2, verdict_block("F-001", "upheld", "src/a.py", 11, "quote-alpha-1"))
    reply_dir("kimi", 2, verdict_block("F-001", "upheld", "src/a.py", 12, "quote-alpha-2"))

    result = run_swarm(
        "convene", "--tier", "swarm", "--gate", "acceptance",
        "--diff", "changes.diff",
        "--paths", "src/a.py", "src/b.py", "secrets/keys.txt",
        "--caller", "claude-opus",
        "--timeout-sec", "60",
        "--registry", "adapters.yaml",
    )
    assert result.returncode == 0, result.stderr
    session_id = _session_id(result.stdout)

    session = _session(workdir, session_id)
    # Флаг в session.json: gate-ревьюер — не caller и с gate_legal.
    assert session["orchestrator_id"] == "claude-opus"
    assert session["caller_family"] == "claude"
    assert session["gate"]["reviewer_id"] == "codex-gpt"
    assert session["gate"]["status"] == "pending"
    participants = {p["id"]: p for p in session["participants"]}
    assert participants["codex-gpt"]["gate_pass"] is True
    assert participants["claude-opus"]["gate_pass"] is False
    assert participants["kimi-k2"]["gate_pass"] is False
    assert "secrets/keys.txt" in session["criticality"]["hits"]

    # gate-verdict — ОТДЕЛЬНЫЙ вызов ПОСЛЕ репорта и диспозиций (TD §7.2):
    # до report — отказ state machine
    for command in ("attack", "dedup", "assess"):
        assert run_swarm(command, session_id).returncode == 0
    result = run_swarm("gate-verdict", session_id,
                       "--dispositions-file",
                       _dispositions_file(workdir, {"F-001": "agree"}))
    assert result.returncode == EXIT_PROTOCOL

    result = run_swarm("report", session_id)
    assert result.returncode == 0, result.stderr
    report = _report(workdir, session_id)
    for marker in ("Gate-проход", "codex-gpt", "статус: pending"):
        assert marker in report, f"в review trace репорта нет {marker!r}"

    reply_dir("codex", 3, _acceptance_block("accept"))
    result = run_swarm("gate-verdict", session_id,
                       "--dispositions-file", "dispositions.json")
    assert result.returncode == 0, result.stderr
    session = _session(workdir, session_id)
    assert session["gate"]["status"] == "approved"
    assert session["gate"]["dispositions"] == {"F-001": "agree"}
    # отдельный focused-вызов в сессии gate-ревьюера: +1 invocation у codex
    codex_after = {p["id"]: p for p in session["participants"]}["codex-gpt"]
    assert codex_after["invocations"] == 3  # start + ask тура 2 + gate-вызов

    report = _report(workdir, session_id)
    for marker in ("статус: approved", "accept", "F-001 → agree"):
        assert marker in report, f"в обновлённом review trace нет {marker!r}"

    _assert_clean_close(run_swarm, workdir, session_id)

    # gate_pass в observations; вердикт и находки gate-ревьюера выведены из
    # advisory-статистики (FR-16, TD §5.7): ячейки codex-gpt в strengths нет
    track_dir = workdir / ".swarm-track-record"
    observations = [json.loads(line) for line in
                    (track_dir / "observations.jsonl").read_text(
                        encoding="utf-8").splitlines() if line.strip()]
    by_pid = {obs["participant_id"]: obs for obs in observations}
    assert by_pid["codex-gpt"]["gate_pass"] is True
    assert by_pid["claude-opus"]["gate_pass"] is False
    strengths = json.loads((track_dir / "strengths.json").read_text(encoding="utf-8"))
    assert not any(key.startswith("codex-gpt|") for key in strengths), \
        "gate-ревьюер исключён из advisory-статистики роя"
    # kimi — рядовой advisory-участник: его ячейка в статистике остаётся
    # (claude-opus получил forced-линзу критичного пути secrets/** → security,
    # forced тоже вне strengths, TD §5.7)
    assert any(key.startswith("kimi-k2|") for key in strengths)


# ---------- SI-14: capability/cross-family floor лёгкого тарифа (AC-16/AC-19) ----------

def test_si_14_same_family_only_pool_refused(run_swarm, registry_factory,
                                             participant_entry, review_tree,
                                             stub_env):
    """Владелец 2026-08-03 (cross-family gate policy): пул из одних
    claude-участников не eligible для claude-caller — участник ТОГО ЖЕ family
    (не только буквальный caller) исключён floor-фильтром; convene отказывает
    fail-closed ДО создания сессии, а не выбирает другого claude."""
    registry_factory([
        participant_entry("claude-opus", "claude", enabled=True),
        participant_entry("claude-sonnet", "claude", enabled=True),
    ])
    result = run_swarm(
        "convene", "--tier", "light", "--diff", "changes.diff",
        "--paths", "src/a.py", "--caller", "claude-opus",
        "--registry", "adapters.yaml",
    )
    assert result.returncode == EXIT_PROTOCOL
    assert "self-review" in result.stderr
    assert not (stub_env["cwd"] / ".swarm-sessions").exists(), \
        "сессия не создаётся при отказе (fail-closed до convene)"


def test_si_14_cross_family_adapter_is_eligible(run_swarm, registry_factory,
                                                participant_entry, review_tree,
                                                stub_env):
    """Тот же реестр + участник ДРУГОГО family — eligible (позитивная
    сторона cross-family floor, зеркало предыдущего теста)."""
    registry_factory([
        participant_entry("claude-opus", "claude", enabled=True),
        participant_entry("claude-sonnet", "claude", enabled=True),
        participant_entry("codex-gpt", "codex", enabled=True),
    ])
    result = run_swarm(
        "convene", "--tier", "light", "--diff", "changes.diff",
        "--paths", "src/a.py", "--caller", "claude-opus",
        "--registry", "adapters.yaml",
    )
    assert result.returncode == 0, result.stderr
    session_id = _session_id(result.stdout)
    session = _session(stub_env["cwd"], session_id)
    assert session["participants"][0]["id"] == "codex-gpt"
    assert session["participants"][0]["family"] == "codex"
    _assert_clean_close(run_swarm, stub_env["cwd"], session_id)


def test_si_14_exact_caller_only_refused(run_swarm, registry_factory,
                                         participant_entry, review_tree,
                                         stub_env):
    registry_factory([
        participant_entry("claude-opus", "claude", enabled=True),
    ])
    result = run_swarm(
        "convene", "--tier", "light", "--diff", "changes.diff",
        "--paths", "src/a.py", "--caller", "claude-opus",
        "--registry", "adapters.yaml",
    )
    assert result.returncode == EXIT_PROTOCOL
    assert "self-review" in result.stderr
    assert not (stub_env["cwd"] / ".swarm-sessions").exists(), \
        "сессия не создаётся при отказе (fail-closed до convene)"


def test_si_14_requested_same_family_gate_reviewer_refused(
        run_swarm, registry_factory, participant_entry, review_tree, stub_env):
    """Владелец 2026-08-03 (cross-family gate policy): запрошенный
    `--gate-reviewer` ТОГО ЖЕ family, что caller (claude-sonnet при
    claude-opus caller), отказан — даже при отличающемся id. Отменяет прежнее
    поведение «same-family gate reviewer allowed»."""
    registry_factory([
        participant_entry("claude-opus", "claude", enabled=True),
        participant_entry("claude-sonnet", "claude", enabled=True),
        participant_entry("codex-gpt", "codex", enabled=True),
    ])
    common = (
        "convene", "--tier", "swarm", "--gate", "acceptance",
        "--diff", "changes.diff", "--paths", "src/a.py",
        "--caller", "claude-opus", "--registry", "adapters.yaml",
    )
    # Тот же family, что caller, — отказ, даже другой id (cross-family floor).
    result = run_swarm(*common, "--gate-reviewer", "claude-sonnet")
    assert result.returncode == EXIT_PROTOCOL
    assert "self-review" in result.stderr
    sessions = stub_env["cwd"] / ".swarm-sessions"
    assert not sessions.exists() or not any(sessions.iterdir())

    # Cross-family запрошенный gate-ревьюер — допустим.
    result = run_swarm(*common, "--gate-reviewer", "codex-gpt")
    assert result.returncode == 0, result.stderr
    session_id = _session_id(result.stdout)
    session = _session(stub_env["cwd"], session_id)
    assert session["gate"]["reviewer_id"] == "codex-gpt"
    participants = {p["id"]: p for p in session["participants"]}
    assert participants["codex-gpt"]["gate_pass"] is True
    _assert_clean_close(run_swarm, stub_env["cwd"], session_id)

    # Literal caller — тоже отказ (частный случай cross-family floor).
    result = run_swarm(*common, "--gate-reviewer", "claude-opus")
    assert result.returncode == EXIT_PROTOCOL
    assert "self-review" in result.stderr
    sessions = stub_env["cwd"] / ".swarm-sessions"
    assert not sessions.exists() or not any(sessions.iterdir())


def test_si_14_gate_without_gate_legal_candidate_refused(
        run_swarm, registry_factory, participant_entry, review_tree, stub_env):
    registry_factory([
        participant_entry("claude-opus", "claude", enabled=True),
        participant_entry("codex-gpt", "codex", enabled=True, gate_legal=False),
        participant_entry("kimi-k2", "kimi", enabled=True, gate_legal=False),
    ])
    result = run_swarm(
        "convene", "--tier", "light", "--gate", "acceptance",
        "--diff", "changes.diff", "--paths", "src/a.py",
        "--caller", "claude-opus", "--registry", "adapters.yaml",
    )
    assert result.returncode == EXIT_PROTOCOL
    assert not (stub_env["cwd"] / ".swarm-sessions").exists()


def test_si_14_gate_legal_floor_only_for_gate(run_swarm, registry_factory,
                                              participant_entry, review_tree,
                                              stub_env):
    """Тот же реестр БЕЗ --gate: gate_legal не входит во floor advisory-тарифа —
    ревьюер выбирается (kimi/codex advisory-легальны, TBD-05)."""
    registry_factory([
        participant_entry("claude-opus", "claude", enabled=True),
        participant_entry("codex-gpt", "codex", enabled=True, gate_legal=False),
        participant_entry("kimi-k2", "kimi", enabled=True, gate_legal=False),
    ])
    result = run_swarm(
        "convene", "--tier", "light", "--diff", "changes.diff",
        "--paths", "src/a.py", "--caller", "claude-opus",
        "--timeout-sec", "60", "--registry", "adapters.yaml",
    )
    assert result.returncode == 0, result.stderr
    session_id = _session_id(result.stdout)
    session = _session(stub_env["cwd"], session_id)
    assert session["participants"][0]["id"] == "codex-gpt"
    assert session["participants"][0]["gate_pass"] is False
    _assert_clean_close(run_swarm, stub_env["cwd"], session_id)
