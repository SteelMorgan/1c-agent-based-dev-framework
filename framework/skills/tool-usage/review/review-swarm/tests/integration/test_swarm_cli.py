"""SI-01..SI-08 — integration-слой state machine роя на stub CLI
(RVSW-01, T-10, test-plan §6.1; AC-03/04/08/10/19/20).

Stub CLI: harness tests/stubs/stub_cli.py (scripted-ответы STUB_REPLY_DIR,
адресные режимы timeout:<cli>, argv-лог invocations.jsonl).
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


# ---------- Хелперы сценариев ----------

def _session_id(stdout: str) -> str:
    for line in stdout.splitlines():
        if line.startswith("session_id: "):
            return line.split(": ", 1)[1].strip()
    raise AssertionError(f"session_id не найден в выводе convene:\n{stdout}")


def _convene(run_swarm, timeout_sec=60):
    result = run_swarm(
        "convene", "--tier", "swarm",
        "--diff", "changes.diff",
        "--paths", "src/a.py", "src/b.py",
        "--caller", "claude-opus",
        "--timeout-sec", str(timeout_sec),
        "--registry", "adapters.yaml",
    )
    assert result.returncode == 0, result.stderr
    return _session_id(result.stdout)


def _session(workdir, session_id: str) -> dict:
    path = workdir / ".swarm-sessions" / session_id / "session.json"
    assert path.exists(), f"session.json не найден: {path}"
    return json.loads(path.read_text(encoding="utf-8"))


def _checkpoints(workdir, session_id: str) -> list[str]:
    path = workdir / ".swarm-sessions" / session_id / "progress.jsonl"
    if not path.exists():
        return []
    return [json.loads(line)["checkpoint"]
            for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


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


def _script_tour1(reply_dir, severity="P3"):
    """Тур 1: уникальная находка claude (→ F-001), пересекающаяся пара
    claude+codex по src/b.py (→ авто-подтверждённый кластер), kimi — пусто."""
    reply_dir("claude", 1, findings_block([
        _finding("src/a.py", 10, 12, "security", severity, "INJECTION-CLAIM-ALPHA"),
        _finding("src/b.py", 5, 8, "correctness", "P4", "OFFBYONE-CLAIM-BETA",
                 in_lens=False),
    ]))
    reply_dir("codex", 1, findings_block([
        _finding("src/b.py", 6, 9, "correctness", "P3", "BOUNDS-CLAIM-GAMMA"),
    ]))
    reply_dir("kimi", 1, findings_block([]))


def _script_validation_tours(reply_dir):
    """Туры 2–4 для F-001: спор → maintain → contested."""
    reply_dir("codex", 2, verdict_block("F-001", "upheld", "src/a.py", 11, "quote-alpha-1"))
    reply_dir("kimi", 2, verdict_block("F-001", "overruled", "src/a.py", 12, "quote-alpha-2"))
    reply_dir("claude", 2, author_response_block(
        "F-001", "maintain",
        counter={"path": "src/a.py", "line": 13, "quote": "quote-alpha-3"}))
    reply_dir("codex", 3, verdict_block("F-001", "upheld", "src/a.py", 14, "quote-alpha-4"))
    reply_dir("kimi", 3, verdict_block("F-001", "overruled", "src/a.py", 15, "quote-alpha-5"))


def _run_to_vote(run_swarm, session_id):
    for command in ("attack", "dedup", "assess", "rebut", "vote"):
        result = run_swarm(command, session_id)
        assert result.returncode == 0, f"{command}: {result.stderr}"


# ---------- SI-01: отказ при <2 enabled из <2 family (AC-19) ----------

def test_si_01_single_enabled_refused(run_swarm, registry_factory,
                                      participant_entry, review_tree, stub_env):
    registry_factory([
        participant_entry("claude-opus", "claude", enabled=True),
        participant_entry("codex-gpt", "codex", enabled=False),
        participant_entry("kimi-k2", "kimi", enabled=False),
    ])
    result = run_swarm(
        "convene", "--tier", "swarm", "--diff", "changes.diff",
        "--paths", "src/a.py", "--caller", "claude-opus",
        "--registry", "adapters.yaml",
    )
    assert result.returncode == EXIT_PROTOCOL
    assert "кворум" in result.stderr or "family" in result.stderr
    assert not (stub_env["cwd"] / ".swarm-sessions").exists(), \
        "сессия не должна создаваться при отказе (fail-closed до convene)"


def test_si_01_single_family_refused(run_swarm, registry_factory,
                                     participant_entry, review_tree, stub_env):
    registry_factory([
        participant_entry("claude-opus", "claude", enabled=True),
        participant_entry("claude-sonnet", "claude", enabled=True),
    ])
    result = run_swarm(
        "convene", "--tier", "swarm", "--diff", "changes.diff",
        "--paths", "src/a.py", "--caller", "claude-opus",
        "--registry", "adapters.yaml",
    )
    assert result.returncode == EXIT_PROTOCOL
    assert not (stub_env["cwd"] / ".swarm-sessions").exists()


# ---------- SI-02: полный цикл state machine (TD §6.1, FR-13) ----------

def test_si_02_full_cycle(run_swarm, three_family_registry, review_tree,
                          reply_dir, stub_env):
    three_family_registry()
    _script_tour1(reply_dir)
    _script_validation_tours(reply_dir)
    session_id = _convene(run_swarm)
    workdir = stub_env["cwd"]

    expected_states = {
        "attack": "TOUR1", "dedup": "DEDUP", "assess": "TOUR2",
        "rebut": "TOUR3", "vote": "TOUR4",
    }
    for command, state in expected_states.items():
        result = run_swarm(command, session_id)
        assert result.returncode == 0, f"{command}: {result.stderr}"
        assert _session(workdir, session_id)["state"] == state

    result = run_swarm("report", session_id)
    assert result.returncode == 0, result.stderr
    assert _session(workdir, session_id)["state"] == "REPORTED"
    report_path = workdir / ".swarm-sessions" / session_id / "report.md"
    assert report_path.exists()

    assert _checkpoints(workdir, session_id) == [
        "convened", "tour1_complete", "dedup_complete", "tour2_complete",
        "tour3_complete", "tour4_complete", "report_ready",
    ]

    result = run_swarm("close", session_id)
    assert result.returncode == 0, result.stderr
    assert not (workdir / ".swarm-sessions" / session_id).exists(), \
        "эфемерная сессия удаляется на close"
    sandboxes = workdir / ".review-sandboxes"
    assert not sandboxes.exists() or all(
        ac.is_lock_only_tombstone(path) for path in sandboxes.iterdir()
    ), \
        "sandbox'ы участников закрыты (парность start↔close)"


# ---------- SI-03: слепота тура 1 (AC-04) ----------

def test_si_03_tour1_blindness(run_swarm, three_family_registry, review_tree,
                               reply_dir, stub_env):
    three_family_registry()
    _script_tour1(reply_dir)
    session_id = _convene(run_swarm)
    workdir = stub_env["cwd"]
    result = run_swarm("attack", session_id)
    assert result.returncode == 0, result.stderr

    session = _session(workdir, session_id)
    prompts_dir = workdir / ".swarm-sessions" / session_id / "prompts"
    prompts = {
        pid: (prompts_dir / f"tour1-{pid}.md").read_text(encoding="utf-8")
        for pid in ("claude-opus", "codex-gpt", "kimi-k2")
    }
    anon_ids = set(session["anon_map"].values())
    assert anon_ids == {"M1", "M2", "M3"}

    lenses = {}
    for pid, prompt in prompts.items():
        assert prompt.count("Линза:") == 1, f"{pid}: ровно одна линза на участника"
        lenses[pid] = [
            line for line in prompt.splitlines() if "Линза:" in line
        ][0]
        for anon in anon_ids:
            assert anon not in prompt, f"{pid}: в промпте тура 1 утёк {anon}"
        # чужих находок не существует в промпте тура 1 (слепота)
        for marker in ("INJECTION-CLAIM-ALPHA", "OFFBYONE-CLAIM-BETA",
                       "BOUNDS-CLAIM-GAMMA", "F-001", "swarm-verdict"):
            assert marker not in prompt, f"{pid}: в промпте тура 1 чужой контент"
        assert "review.diff" in prompt, f"{pid}: diff — файлом, не текстом промпта"
        assert "diff --git" not in prompt, f"{pid}: diff не встраивается в промпт"
    assert len(set(lenses.values())) == 3, "у участников разные линзы"

    # одинаковый контекст: diff материализован файлом в sandbox каждого
    for participant in session["participants"]:
        review_id = participant["review_id"]
        diff_file = (workdir / ".review-sandboxes" / review_id
                     / "workspace" / "review.diff")
        assert diff_file.exists(), f"{participant['id']}: review.diff не материализован"
        assert diff_file.read_text(encoding="utf-8") == review_tree["diff_content"]

    # argv-лог stub'а: kimi получает промпт через -p — кросс-проверка слепоты
    kimi_calls = [inv for inv in read_invocations(stub_env["state_dir"])
                  if inv["cli"] == "kimi" and "-p" in inv["argv"]]
    assert kimi_calls, "стартовый вызов kimi не залогирован"
    kimi_prompt = kimi_calls[0]["argv"][kimi_calls[0]["argv"].index("-p") + 1]
    for anon in anon_ids:
        assert anon not in kimi_prompt

    run_swarm("close", session_id)


# ---------- F-01 (R-Final): diff в sandbox к моменту первого хода ----------

def test_f01_diff_in_sandbox_at_tour1_first_move(run_swarm, three_family_registry,
                                                 review_tree, reply_dir, stub_env):
    """F-01 (R-Final): diff уходит в paths старта участника — адаптер копирует
    его в sandbox на фазе copying, ДО первого хода модели (start блокирующий).
    Untracked-файл `.swarm-sessions/<sid>/review.diff` допустим: вне gitignore
    (git-admitted --others) и покрыт fallback-копированием адаптера."""
    three_family_registry()
    _script_tour1(reply_dir)
    session_id = _convene(run_swarm)
    workdir = stub_env["cwd"]
    result = run_swarm("attack", session_id)
    assert result.returncode == 0, result.stderr

    session = _session(workdir, session_id)
    for participant in session["participants"]:
        review_id = participant["review_id"]
        review_dir = workdir / ".review-sandboxes" / review_id
        workspace = review_dir / "workspace"
        # копия из paths старта — доказательство, что diff был в sandbox
        # на момент хода тура 1 (фаза copying предшествует вызову CLI)
        staged = workspace / ".swarm-sessions" / session_id / "review.diff"
        assert staged.exists(), (
            f"{participant['id']}: diff не скопирован из paths старта — "
            f"модель тура 1 не видела review.diff (F-01)"
        )
        assert staged.read_text(encoding="utf-8") == review_tree["diff_content"]
        # источник diff зарегистрирован в sources старта адаптера
        meta = json.loads((review_dir / "review.json").read_text(encoding="utf-8"))
        assert any(
            source["dest_rel"] == f".swarm-sessions/{session_id}/review.diff"
            for source in meta["sources"]
        ), f"{participant['id']}: review.diff отсутствует в sources старта"
        # backup-копия в корне workspace (каноническое имя для туров 2–4)
        assert (workspace / "review.diff").exists()
        # F-12: промпт тура 1 ссылается на фактический путь staged-файла
        # (dest_rel из sources — оракул), а не на корневой review.diff,
        # которого на момент первого хода ещё нет
        prompt = (workdir / ".swarm-sessions" / session_id / "prompts"
                  / f"tour1-{participant['id']}.md").read_text(encoding="utf-8")
        staged_rels = [s["dest_rel"] for s in meta["sources"]
                       if s["dest_rel"].endswith("/review.diff")]
        assert staged_rels, f"{participant['id']}: нет staged review.diff в sources"
        assert any(rel in prompt for rel in staged_rels), (
            f"{participant['id']}: промпт тура 1 не содержит путь staged-файла "
            f"{staged_rels} (F-12)"
        )

    run_swarm("close", session_id)


# ---------- SI-04: аварийная ветка, парность start↔close (AC-19) ----------

def test_si_04_unresponsive_and_cleanup_parity(run_swarm, three_family_registry,
                                               review_tree, reply_dir, stub_env,
                                               set_stub_mode, monkeypatch):
    three_family_registry()
    monkeypatch.setenv("STUB_TIMEOUT_SLEEP", "120")
    _script_tour1(reply_dir)
    reply_dir("codex", 2, verdict_block("F-001", "upheld", "src/a.py", 11, "quote-alpha-1"))
    session_id = _convene(run_swarm, timeout_sec=20)
    workdir = stub_env["cwd"]

    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("dedup", session_id).returncode == 0

    set_stub_mode("timeout:kimi")
    result = run_swarm("assess", session_id, timeout=240)
    assert result.returncode == 0, result.stderr
    session = _session(workdir, session_id)
    kimi = next(p for p in session["participants"] if p["id"] == "kimi-k2")
    assert kimi["state"] == "unresponsive", "таймаут → unresponsive без retry (§6.3.1)"
    # деградировавшая волна завершилась по валидным голосам: all-upheld → confirmed
    assert session["threads"]["F-001"]["status"] == "confirmed"

    result = run_swarm("close", session_id)
    assert result.returncode == 0, result.stderr
    assert not (workdir / ".swarm-sessions" / session_id).exists()
    sandboxes = workdir / ".review-sandboxes"
    assert not sandboxes.exists() or all(
        ac.is_lock_only_tombstone(path) for path in sandboxes.iterdir()
    ), \
        "парность start↔close на аварийной ветке (включая unresponsive)"


# ---------- SI-05: status в ходе прогона (AC-13/AC-20) ----------

def test_si_05_status_observability(run_swarm, three_family_registry,
                                    review_tree, reply_dir, stub_env):
    three_family_registry()
    _script_tour1(reply_dir)
    _script_validation_tours(reply_dir)
    session_id = _convene(run_swarm)
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("dedup", session_id).returncode == 0
    assert run_swarm("assess", session_id).returncode == 0

    result = run_swarm("status", session_id, "--json")
    assert result.returncode == 0, result.stderr
    payload = json.loads(result.stdout)
    assert payload["session_id"] == session_id
    assert payload["state"] == "TOUR2"
    assert payload["unresponsive"] == []
    assert payload["invocation_count"] == 5  # 3 start + 2 ask тура 2
    participants = {p["id"]: p for p in payload["participants"]}
    assert set(participants) == {"claude-opus", "codex-gpt", "kimi-k2"}
    for pid, entry in participants.items():
        assert entry["state"] == "active"
        assert entry["lens"] in (
            "security", "correctness", "concurrency",
            "performance", "data-contracts", "tests",
        )
        assert "last_activity_at" in entry["activity"]
        assert "last_heartbeat_at" in entry["activity"]
        assert entry["liveness"]["class"] in ("active", "quiet", "dead_watcher")
    assert participants["claude-opus"]["invocations"] == 1
    assert participants["codex-gpt"]["invocations"] == 2
    assert payload["findings"]["accepted"] == 3
    assert payload["findings"]["unique_unconfirmed"] == 1
    assert payload["threads"] == {"F-001": "open"}
    assert payload["last_checkpoint"]["checkpoint"] == "tour2_complete"
    assert payload["wall_clock"]["status"] in ("ok", "warn", "exceeded")

    run_swarm("close", session_id)


# ---------- SI-06/SI-07: арбитраж и гейт репорта (AC-08/AC-10) ----------

def _decision_file(workdir, name, **overrides):
    decision = {
        "finding_id": "F-001",
        "decision": "upheld",
        # F-09 (R-Final): цитата обязана быть фрагментом location-файла
        # (src/a.py:11 — «# строка 11 модуля a»)
        "evidence_quote": "# строка 11 модуля a",
        "location": {"path": "src/a.py", "line": 11},
        "rationale": "решение Оркестратора",
    }
    decision.update(overrides)
    path = workdir / name
    path.write_text(json.dumps(decision, ensure_ascii=False), encoding="utf-8")
    return name


def test_si_06_07_arbitration_and_report_gate(run_swarm, three_family_registry,
                                              review_tree, reply_dir, stub_env):
    three_family_registry()
    _script_tour1(reply_dir, severity="P2")  # contested severity ≥ major
    _script_validation_tours(reply_dir)
    session_id = _convene(run_swarm)
    workdir = stub_env["cwd"]
    _run_to_vote(run_swarm, session_id)
    assert _session(workdir, session_id)["threads"]["F-001"]["status"] == "contested"

    # SI-07: report при contested ≥ major без арбитража → отказ (§6.3.7)
    result = run_swarm("report", session_id)
    assert result.returncode == EXIT_PROTOCOL
    assert "арбитраж" in result.stderr or "contested" in result.stderr

    # SI-06: невалидные решения отклоняются fail-closed
    bad_cases = [
        _decision_file(workdir, "d1.json", evidence_quote=""),
        _decision_file(workdir, "d2.json", evidence_quote="   "),
        _decision_file(workdir, "d3.json",
                       location={"path": "src/zzz.py", "line": 1}),
        _decision_file(workdir, "d4.json",
                       location={"path": "src/a.py", "line": 999}),
        _decision_file(workdir, "d5.json", decision="reclassified"),
        _decision_file(workdir, "d6.json", decision="maybe"),
        # F-09 (R-Final): непустая цитата, которой НЕТ в location-файле
        _decision_file(workdir, "d7.json", evidence_quote="cursor.execute(query)"),
        # цитата из ДРУГОГО файла проверяемого набора (src/b.py, не location)
        _decision_file(workdir, "d8.json", evidence_quote="# строка 11 модуля b"),
    ]
    for name in bad_cases:
        result = run_swarm("arbitrate", session_id, "--finding", "F-001",
                           "--decision-file", name)
        assert result.returncode == EXIT_PROTOCOL, f"{name}: ожидался отказ"

    # валидное решение принимается и финально
    good = _decision_file(workdir, "good.json")
    result = run_swarm("arbitrate", session_id, "--finding", "F-001",
                       "--decision-file", good)
    assert result.returncode == 0, result.stderr
    session = _session(workdir, session_id)
    assert session["state"] == "ARBITRATED"
    assert session["arbitration"]["F-001"]["decision"] == "upheld"
    assert "arbitration_complete" in _checkpoints(workdir, session_id)

    # повторный арбитраж по той же находке отклоняется (решение финальное)
    result = run_swarm("arbitrate", session_id, "--finding", "F-001",
                       "--decision-file", good)
    assert result.returncode == EXIT_PROTOCOL

    # SI-07: репорт после арбитража — кластеризован, без verdict/kill/консенсуса
    result = run_swarm("report", session_id)
    assert result.returncode == 0, result.stderr
    report = (workdir / ".swarm-sessions" / session_id / "report.md").read_text(
        encoding="utf-8")
    for marker in ("F-001", "C-001", "contested", "арбитраж", "claude-opus"):
        assert marker in report, f"в репорте нет {marker!r}"
    for forbidden in ("verdict", "kill", "консенсус"):
        assert forbidden not in report.lower(), \
            f"репорт без консенсуса/verdict/kill (FR-10): найдено {forbidden!r}"

    assert run_swarm("close", session_id).returncode == 0


# ---------- SI-08: невалидный ход → один retry → unresponsive (AC-03/AC-19) ----------

def test_si_08_invalid_move_retry_then_unresponsive(run_swarm, three_family_registry,
                                                    review_tree, reply_dir, stub_env):
    three_family_registry()
    reply_dir("claude", 1, findings_block([
        _finding("src/a.py", 10, 12, "security", "P3", "INJECTION-CLAIM-ALPHA"),
    ]))
    reply_dir("codex", 1, findings_block([]))
    reply_dir("kimi", 1, findings_block([]))
    # codex: невалидный ход → retry валидным (восстановление)
    reply_dir("codex", 2, "Свободный текст без structured-блока.")
    reply_dir("codex", 3, verdict_block("F-001", "upheld", "src/a.py", 11, "quote-alpha-1"))
    # kimi: невалидный ход → retry тоже невалиден → unresponsive
    reply_dir("kimi", 2, "Тоже без блока.")
    reply_dir("kimi", 3, "И снова без блока.")

    session_id = _convene(run_swarm)
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("dedup", session_id).returncode == 0
    result = run_swarm("assess", session_id)
    assert result.returncode == 0, result.stderr

    session = _session(workdir, session_id)
    participants = {p["id"]: p for p in session["participants"]}
    assert participants["codex-gpt"]["state"] == "active"
    assert participants["codex-gpt"]["invocations"] == 3  # start + ход + контент-retry
    assert participants["kimi-k2"]["state"] == "unresponsive"
    assert participants["kimi-k2"]["invocations"] == 3  # start + ход + контент-retry
    # волна завершена по валидным голосам: upheld → досрочное confirmed (SU-V04)
    assert session["threads"]["F-001"]["status"] == "confirmed"

    assert run_swarm("close", session_id).returncode == 0


# ---------- F-09 (R-Final): whitespace-нормализация evidence_quote арбитража ----------

def test_f09_evidence_quote_whitespace_normalized(run_swarm, three_family_registry,
                                                  review_tree, reply_dir, stub_env):
    """F-09 (R-Final): цитата с иными отступами/переводами строк принимается,
    если после whitespace-нормализации входит в location-файл (проверка —
    механическая, не «только непустота»)."""
    three_family_registry()
    _script_tour1(reply_dir, severity="P2")  # contested severity ≥ major
    _script_validation_tours(reply_dir)
    session_id = _convene(run_swarm)
    workdir = stub_env["cwd"]
    _run_to_vote(run_swarm, session_id)
    assert _session(workdir, session_id)["threads"]["F-001"]["status"] == "contested"

    # src/a.py:10-11 — «# строка 10 модуля a\n# строка 11 модуля a»; цитата
    # с collapsed-пробелами и разрывом строки нормализуется к фрагменту файла
    normalized = _decision_file(
        workdir, "normalized.json",
        evidence_quote="# строка 10\n   модуля    a\r\n# строка 11  модуля a",
        location={"path": "src/a.py", "line": 10})
    result = run_swarm("arbitrate", session_id, "--finding", "F-001",
                       "--decision-file", normalized)
    assert result.returncode == 0, result.stderr
    session = _session(workdir, session_id)
    assert session["arbitration"]["F-001"]["decision"] == "upheld"

    assert run_swarm("close", session_id).returncode == 0
