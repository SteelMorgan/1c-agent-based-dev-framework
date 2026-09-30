"""Integration-слой rework E2E-02 (RVSW-01, blocking completion review):
F-002 (gate acceptance при 0 находках), F-003 (лёгкий тариф vs карта
критичности), F-006 (durable исход gate), F-007 (conditional_accept),
F-012 (переназначение gate-ревьюера), F-013 (close вне реестра).

Stub CLI: harness tests/stubs/stub_cli.py (scripted-ответы STUB_REPLY_DIR).
"""
from __future__ import annotations

import json

import pytest
import adapter_contract as ac
from swarm_case_helpers import findings_block

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


def _write_session(workdir, session: dict) -> None:
    path = workdir / ".swarm-sessions" / session["session_id"] / "session.json"
    path.write_text(json.dumps(session, ensure_ascii=False, indent=2),
                    encoding="utf-8")


def _finding(path, start, end, category, severity, claim):
    return {
        "location": {"path": path, "line_start": start, "line_end": end},
        "category": category, "severity": severity, "in_lens": True,
        "claim": claim, "evidence": f"evidence для {claim}", "rationale": "обоснование",
    }


def _acceptance_block(verdict: str, positions=None,
                      rationale="позиция gate-ревьюера") -> str:
    payload = {"verdict": verdict, "positions": positions or [],
               "rationale": rationale}
    return ("Gate-вердикт.\n\n```swarm-gate-verdict\n"
            + json.dumps(payload, ensure_ascii=False) + "\n```\n")


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


@pytest.mark.parametrize("caller_args", [[], ["--caller", "unknown"]])
def test_direct_cli_requires_explicit_valid_caller_policy_assertion(
        caller_args, run_swarm, three_family_registry, review_tree, stub_env):
    """Missing/unknown policy assertion отказывает до создания session."""
    three_family_registry()
    result = run_swarm(
        "convene", "--tier", "light", "--diff", "changes.diff",
        "--paths", "src/a.py", "--registry", "adapters.yaml", *caller_args,
    )
    assert result.returncode == EXIT_PROTOCOL
    assert "--caller" in result.stderr
    assert not (stub_env["cwd"] / ".swarm-sessions").exists()


# ---------- F-002: gate acceptance достижим при 0 находках ----------

def test_f002_light_gate_acceptance_zero_findings(run_swarm, three_family_registry,
                                                  review_tree, reply_dir, stub_env):
    """Ревьюер не выдал ни одной находки: пустые диспозиции {} — валидный
    файл; gate acceptance исполняется (тупик F-002 устранён)."""
    three_family_registry()
    reply_dir("codex", 1, findings_block([]))          # 0 находок
    reply_dir("codex", 2, _acceptance_block("accept"))
    session_id = _convene_light(run_swarm, caller="claude-opus", gate="acceptance")
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("report", session_id).returncode == 0

    # негатив: без --dispositions-file — fail-closed даже при 0 находках
    result = run_swarm("gate-verdict", session_id)
    assert result.returncode == EXIT_PROTOCOL
    assert "dispositions" in result.stderr

    # позитив: явный файл пустых диспозиций — gate-verdict исполняется
    result = run_swarm("gate-verdict", session_id,
                       "--dispositions-file", _dispositions_file(workdir, {}))
    assert result.returncode == 0, result.stderr
    gate = _session(workdir, session_id)["gate"]
    assert gate["status"] == "approved"
    assert gate["dispositions"] == {}
    _assert_clean_close(run_swarm, workdir, session_id)


def test_f002_empty_dispositions_rejected_with_findings(
        run_swarm, three_family_registry, review_tree, reply_dir, stub_env):
    """Есть находки — пустой файл диспозиций отклоняется fail-closed."""
    three_family_registry()
    reply_dir("codex", 1, findings_block([
        _finding("src/a.py", 10, 12, "security", "P2", "CLAIM-ALPHA"),
    ]))
    session_id = _convene_light(run_swarm, caller="claude-opus", gate="acceptance")
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("report", session_id).returncode == 0

    result = run_swarm("gate-verdict", session_id,
                       "--dispositions-file", _dispositions_file(workdir, {}))
    assert result.returncode == EXIT_PROTOCOL
    assert "непустой" in result.stderr

    # контроль: непустые диспозиции проходят
    reply_dir("codex", 2, _acceptance_block("accept"))
    result = run_swarm("gate-verdict", session_id,
                       "--dispositions-file", _dispositions_file(
                           workdir, {"F-001": "agree"}))
    assert result.returncode == 0, result.stderr
    _assert_clean_close(run_swarm, workdir, session_id)


# ---------- F-003: лёгкий тариф не обходит карту критичности ----------

def test_f003_light_tier_refused_on_criticality_paths(
        run_swarm, three_family_registry, review_tree, stub_env):
    """convene --tier light с путём из карты критичности — fail-closed с
    указанием полного тарифа (AC-15 не обходится флагом)."""
    three_family_registry()
    workdir = stub_env["cwd"]
    (workdir / "secrets").mkdir()
    (workdir / "secrets" / "keys.txt").write_text("key-1\n", encoding="utf-8")
    result = run_swarm(
        "convene", "--tier", "light", "--diff", "changes.diff",
        "--paths", "src/a.py", "secrets/keys.txt",
        "--caller", "claude-opus", "--registry", "adapters.yaml",
    )
    assert result.returncode == EXIT_PROTOCOL
    assert "swarm" in result.stderr
    assert "критичн" in result.stderr
    assert not (workdir / ".swarm-sessions").exists(), \
        "сессия не создаётся при отказе (fail-closed до convene)"


def test_f003_light_tier_refused_on_criticality_diff(
        run_swarm, three_family_registry, review_tree, stub_env):
    """Попадание в карту через пути самого diff (не только --paths)."""
    three_family_registry()
    workdir = stub_env["cwd"]
    (workdir / "secrets").mkdir()
    (workdir / "secrets" / "keys.txt").write_text("key-1\n", encoding="utf-8")
    (workdir / "crit.diff").write_text(
        "diff --git a/secrets/keys.txt b/secrets/keys.txt\n"
        "--- a/secrets/keys.txt\n+++ b/secrets/keys.txt\n"
        "@@ -1,1 +1,2 @@\n+key-2\n", encoding="utf-8")
    result = run_swarm(
        "convene", "--tier", "light", "--diff", "crit.diff",
        "--paths", "src/a.py",
        "--caller", "claude-opus", "--registry", "adapters.yaml",
    )
    assert result.returncode == EXIT_PROTOCOL
    assert "критичн" in result.stderr


# ---------- F-006: durable след исхода gate в track record ----------

def test_f006_gate_outcome_survives_close(run_swarm, three_family_registry,
                                          review_tree, reply_dir, stub_env):
    """Исход блокирующего gate (approved + режим + ревьюер + вердикты) пишется
    в .swarm-track-record/gate-outcomes.jsonl при close и переживает удаление
    эфемерной сессии (F-006)."""
    three_family_registry()
    reply_dir("codex", 1, findings_block([]))
    reply_dir("codex", 2, _acceptance_block("accept"))
    session_id = _convene_light(run_swarm, caller="claude-opus", gate="acceptance")
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("report", session_id).returncode == 0
    result = run_swarm("gate-verdict", session_id,
                       "--dispositions-file", _dispositions_file(workdir, {}))
    assert result.returncode == 0, result.stderr

    _assert_clean_close(run_swarm, workdir, session_id)

    outcomes_path = workdir / ".swarm-track-record" / "gate-outcomes.jsonl"
    assert outcomes_path.exists(), "durable след исхода gate не записан (F-006)"
    records = [json.loads(line) for line in
               outcomes_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    assert len(records) == 1
    record = records[0]
    assert record["session_id"] == session_id
    assert record["mode"] == "acceptance"
    assert record["status"] == "approved"
    assert record["caller_id"] == "claude-opus"
    assert record["caller_family"] == "claude"
    assert record["reviewer_id"] == "codex-gpt"
    assert record["iterations"] == 1
    assert record["verdicts"][0]["verdict"]["verdict"] == "accept"


# ---------- F-007: conditional_accept — промежуточный статус ----------

def _drive_to_conditional(run_swarm, workdir, reply_dir):
    reply_dir("codex", 1, findings_block([
        _finding("src/a.py", 10, 12, "security", "P2", "CLAIM-ALPHA"),
    ]))
    reply_dir("codex", 2, _acceptance_block(
        "conditional_accept",
        positions=[{"finding_id": "F-001", "position": "partial"}],
        rationale="принимаю при условии фикса P2"))
    session_id = _convene_light(run_swarm, caller="claude-opus", gate="acceptance")
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("report", session_id).returncode == 0
    result = run_swarm("gate-verdict", session_id,
                       "--dispositions-file", _dispositions_file(
                           workdir, {"F-001": "agree"}))
    assert result.returncode == 0, result.stderr
    return session_id


def test_f007_conditional_accept_not_auto_approved(
        run_swarm, three_family_registry, review_tree, reply_dir, stub_env):
    """conditional_accept НЕ переводит gate в терминальный approved:
    промежуточный статус conditional, auto-approve не происходит; требуется
    явное решение Оркестратора (confirm|reject), фиксируемое в сессии."""
    three_family_registry()
    workdir = stub_env["cwd"]
    session_id = _drive_to_conditional(run_swarm, workdir, reply_dir)

    gate = _session(workdir, session_id)["gate"]
    assert gate["status"] == "conditional", (
        "conditional_accept не терминальный approved (F-007)")
    assert gate["verdicts"][0]["verdict"]["verdict"] == "conditional_accept"
    assert gate["conditional"]["resolution"] is None

    # повторный gate-вызов без явного решения — fail-closed
    result = run_swarm("gate-verdict", session_id)
    assert result.returncode == EXIT_PROTOCOL
    assert "conditional" in result.stderr

    # явное подтверждение условий Оркестратором → approved, с фиксацией
    result = run_swarm("gate-verdict", session_id,
                       "--conditional-decision", "confirm")
    assert result.returncode == 0, result.stderr
    gate = _session(workdir, session_id)["gate"]
    assert gate["status"] == "approved"
    assert gate["conditional"]["resolution"]["decision"] == "confirm"
    report = (workdir / ".swarm-sessions" / session_id / "report.md").read_text(
        encoding="utf-8")
    assert "conditional" in report and "статус: approved" in report

    _assert_clean_close(run_swarm, workdir, session_id)


def test_f007_conditional_reject_returns_to_rework_loop(
        run_swarm, three_family_registry, review_tree, reply_dir, stub_env):
    """Отказ Оркестратора от условного принятия — разногласие: blocked и
    дельта-итерация gate (rework → повторный вердикт), не эскалация и не
    auto-approve."""
    three_family_registry()
    workdir = stub_env["cwd"]
    session_id = _drive_to_conditional(run_swarm, workdir, reply_dir)
    assert _session(workdir, session_id)["gate"]["status"] == "conditional"

    result = run_swarm("gate-verdict", session_id,
                       "--conditional-decision", "reject")
    assert result.returncode == 0, result.stderr
    gate = _session(workdir, session_id)["gate"]
    assert gate["status"] == "blocked"
    assert gate["conditional"]["resolution"]["decision"] == "reject"

    # дельта-итерация после reject: новый вердикт accept → approved
    reply_dir("codex", 3, _acceptance_block("accept"))
    result = run_swarm("gate-verdict", session_id)
    assert result.returncode == 0, result.stderr
    gate = _session(workdir, session_id)["gate"]
    assert gate["status"] == "approved"
    assert gate["iterations"] == 2

    _assert_clean_close(run_swarm, workdir, session_id)


# ---------- F-012: переназначение gate-ревьюера при unresponsive ----------

def test_f012_gate_reviewer_reassignment_after_unresponsive(
        run_swarm, three_family_registry, review_tree, reply_dir, stub_env):
    """Назначенный gate-ревьюер ушёл в unresponsive: Оркестратор переназначает
    его флагом --gate-reviewer без пересоздания сессии; замена проходит тот же
    floor (enabled + не literal caller + gate_legal), sandbox закрывается парно."""
    three_family_registry()
    reply_dir("codex", 1, findings_block([]))
    reply_dir("kimi", 1, findings_block([]))            # старт замены
    reply_dir("kimi", 2, _acceptance_block("accept"))
    session_id = _convene_light(run_swarm, caller="claude-opus", gate="acceptance")
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("report", session_id).returncode == 0

    # назначенный gate-ревьюер ушёл в unresponsive (туре 1)
    session = _session(workdir, session_id)
    session["participants"][0]["state"] = "unresponsive"
    _write_session(workdir, session)

    # без переназначения — отказ
    result = run_swarm("gate-verdict", session_id,
                       "--dispositions-file", _dispositions_file(workdir, {}))
    assert result.returncode == EXIT_PROTOCOL

    # переназначение Оркестратором → gate-проход исполняется
    result = run_swarm("gate-verdict", session_id,
                       "--gate-reviewer", "kimi-k2",
                       "--dispositions-file", "dispositions.json")
    assert result.returncode == 0, result.stderr
    session = _session(workdir, session_id)
    gate = session["gate"]
    assert gate["reviewer_id"] == "kimi-k2"
    assert gate["status"] == "approved"
    assert gate["reassignments"][0]["from"] == "codex-gpt"
    assert gate["reassignments"][0]["to"] == "kimi-k2"
    replacement = next(p for p in session["participants"] if p["id"] == "kimi-k2")
    assert replacement["gate_pass"] is True
    assert replacement["review_id"], "замене стартует focused-sandbox (парность cleanup)"
    report = (workdir / ".swarm-sessions" / session_id / "report.md").read_text(
        encoding="utf-8")
    assert "- Ревьюер: kimi-k2 (family kimi)" in report
    assert "- cross_family_reviewer_id: kimi-k2" in report
    assert "- cross_family_reviewer_family: kimi" in report
    assert "- gate-ревьюер: kimi-k2" in report
    metadata = report.split("## Findings", 1)[0]
    acceptance_trace = report.split("## Acceptance Trace", 1)[1].split(
        "## Gate-проход", 1)[0]
    assert "codex-gpt" not in metadata
    assert "cross_family_reviewer_id: codex-gpt" not in acceptance_trace

    _assert_clean_close(run_swarm, workdir, session_id)
    outcomes = (workdir / ".swarm-track-record" / "gate-outcomes.jsonl")
    record = json.loads(outcomes.read_text(encoding="utf-8").splitlines()[-1])
    assert record["caller_id"] == "claude-opus"
    assert record["reviewer_id"] == "kimi-k2"


def test_f012_reassignment_report_refreshed_immediately_even_if_verdict_fails(
        run_swarm, three_family_registry, review_tree, reply_dir, stub_env):
    """Изолирует `_refresh_gate_report` внутри `_reassign_gate_reviewer`
    (сохраняемое поведение, НЕ часть cross-family политики): report.md
    обязан отражать нового ревьюера СРАЗУ после успешного переназначения,
    даже если последующий (в той же CLI-команде) gate-verdict ask падает.
    Без этой изоляции зелёный тест мог бы полагаться на финальный refresh
    успешного прохода gate-verdict, а не на вызов внутри самого
    reassignment — здесь этого финального успешного прохода нет."""
    three_family_registry()
    reply_dir("codex", 1, findings_block([]))
    session_id = _convene_light(run_swarm, caller="claude-opus", gate="acceptance")
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("report", session_id).returncode == 0

    session = _session(workdir, session_id)
    session["participants"][0]["state"] = "unresponsive"
    _write_session(workdir, session)

    # kimi-k2 не получает scripted reply → дефолтный ответ стаба без
    # fenced-блока swarm-gate-verdict → gate-verdict падает ПОСЛЕ успешного
    # reassignment (missing_block).
    result = run_swarm(
        "gate-verdict", session_id, "--gate-reviewer", "kimi-k2",
        "--dispositions-file", _dispositions_file(workdir, {}),
    )
    assert result.returncode == EXIT_PROTOCOL
    assert "невалидный" in result.stderr

    # Reassignment уже применён и сохранён в session, несмотря на падение
    # последующего ask.
    session = _session(workdir, session_id)
    assert session["gate"]["reviewer_id"] == "kimi-k2"
    assert session["gate"]["reassignments"][0]["to"] == "kimi-k2"

    # report.md обязан быть обновлён ДО провала ask — фиксирует именно
    # вызов _refresh_gate_report внутри _reassign_gate_reviewer.
    report = (workdir / ".swarm-sessions" / session_id / "report.md").read_text(
        encoding="utf-8")
    assert "- Ревьюер: kimi-k2 (family kimi)" in report
    assert "- gate-ревьюер: kimi-k2" in report

    _assert_clean_close(run_swarm, workdir, session_id)


def test_f012_reassignment_refused_while_reviewer_active(
        run_swarm, three_family_registry, review_tree, reply_dir, stub_env):
    """Переназначение активного gate-ревьюера — fail-closed."""
    three_family_registry()
    reply_dir("codex", 1, findings_block([]))
    session_id = _convene_light(run_swarm, caller="claude-opus", gate="acceptance")
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("report", session_id).returncode == 0
    result = run_swarm("gate-verdict", session_id,
                       "--gate-reviewer", "kimi-k2",
                       "--dispositions-file", _dispositions_file(workdir, {}))
    assert result.returncode == EXIT_PROTOCOL
    assert "активен" in result.stderr
    _assert_clean_close(run_swarm, workdir, session_id)


def test_f012_same_family_distinct_reassignment_refused(
        run_swarm, registry_factory, participant_entry, review_tree,
        reply_dir, stub_env):
    """Владелец 2026-08-03 (cross-family gate policy): replacement ТОГО ЖЕ
    family, что caller, отказан даже при отличающемся id — отменяет прежнее
    поведение «same-family distinct id allowed». Текущий (unresponsive)
    gate-ревьюер и session не меняются при отказе (fail-closed, без побочных
    эффектов)."""
    registry_factory([
        participant_entry("claude-opus", "claude"),
        participant_entry("codex-gpt", "codex"),
        participant_entry("claude-sonnet", "claude"),
    ])
    reply_dir("codex", 1, findings_block([]))
    # replies для потенциальной (но НЕ ожидаемой под cross-family) замены
    # claude-sonnet — если production ошибочно разрешит reassignment, ход
    # обязан суметь дойти до structured-ответа, а не «повезло» на unresponsive.
    reply_dir("claude", 1, findings_block([]))
    reply_dir("claude", 2, _acceptance_block("accept"))
    session_id = _convene_light(run_swarm, caller="claude-opus", gate="acceptance")
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("report", session_id).returncode == 0
    session = _session(workdir, session_id)
    session["participants"][0]["state"] = "unresponsive"
    _write_session(workdir, session)

    result = run_swarm(
        "gate-verdict", session_id, "--gate-reviewer", "claude-sonnet",
        "--dispositions-file", _dispositions_file(workdir, {}),
    )
    assert result.returncode == EXIT_PROTOCOL
    assert "family" in result.stderr
    # Отказ без побочных эффектов: reviewer_id и состояние сессии не меняются.
    session = _session(workdir, session_id)
    assert session["gate"]["reviewer_id"] == "codex-gpt"
    assert session["gate"]["reassignments"] == []
    _assert_clean_close(run_swarm, workdir, session_id)


@pytest.mark.parametrize(
    ("candidate_id", "candidate_kind", "error_marker"),
    [
        ("claude-opus", "caller", "family"),
        ("kimi-k2", "disabled", "family"),
        ("kimi-k2", "not_gate_legal", "family"),
        ("kimi-k2", "unhealthy", "health floor"),
    ],
)
def test_f012_reassignment_floor_refuses_ineligible_candidate(
        candidate_id, candidate_kind, error_marker, run_swarm, registry_factory,
        participant_entry, review_tree, reply_dir, stub_env):
    """Reassignment сохраняет cross-family (владелец 2026-08-03: family !=
    caller family, не только exact-caller), enabled, gate_legal и health floor."""
    candidate = participant_entry("kimi-k2", "kimi")
    if candidate_kind == "disabled":
        candidate["enabled"] = False
    elif candidate_kind == "not_gate_legal":
        candidate["gate_legal"] = False
    elif candidate_kind == "unhealthy":
        candidate["cli"] = "missing-review-cli"
    registry_factory([
        participant_entry("claude-opus", "claude"),
        participant_entry("codex-gpt", "codex"),
        candidate,
    ])
    reply_dir("codex", 1, findings_block([]))
    session_id = _convene_light(run_swarm, caller="claude-opus", gate="acceptance")
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("report", session_id).returncode == 0
    session = _session(workdir, session_id)
    session["participants"][0]["state"] = "unresponsive"
    _write_session(workdir, session)

    result = run_swarm(
        "gate-verdict", session_id, "--gate-reviewer", candidate_id,
        "--dispositions-file", _dispositions_file(workdir, {}),
    )
    assert result.returncode == EXIT_PROTOCOL
    assert error_marker in result.stderr
    assert all(p["id"] != "kimi-k2"
               for p in _session(workdir, session_id)["participants"])
    _assert_clean_close(run_swarm, workdir, session_id)


# ---------- F-013: close устойчив к исчезнувшему из реестра участнику ----------

def test_f013_close_tolerates_missing_registry_entry(
        run_swarm, registry_factory, participant_entry, review_tree,
        reply_dir, stub_env):
    """Участник исчез из adapters.yaml между convene и close: KeyError →
    пропуск с записью, cleanup продолжается, сессия закрывается (F-013)."""
    registry_factory([
        participant_entry("claude-opus", "claude"),
        participant_entry("codex-gpt", "codex"),
        participant_entry("kimi-k2", "kimi"),
    ])
    reply_dir("codex", 1, findings_block([]))
    session_id = _convene_light(run_swarm, caller="claude-opus")
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0

    # ревьюер исключён из реестра после convene
    registry_factory([
        participant_entry("claude-opus", "claude"),
        participant_entry("kimi-k2", "kimi"),
    ])
    result = run_swarm("close", session_id,
                       "--keep", "--keep-reason", "проверка F-013")
    assert result.returncode == 0, result.stderr
    session = _session(workdir, session_id)
    statuses = session["cleanup"]["participants_closed"]
    assert "реестр" in statuses["codex-gpt"], (
        "пропуск исчезнувшего участника обязан быть записан (F-013)")
    assert session["cleanup"]["status"] == "ok"
