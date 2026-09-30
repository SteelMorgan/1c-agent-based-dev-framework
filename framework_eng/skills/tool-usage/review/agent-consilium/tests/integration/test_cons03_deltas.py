"""Integration-слой CONS-03 (spec-delta): доменные пакеты консилиума.

Trace: AC-E02, AC-E03, AC-E04, AC-E05(integration), AC-E06, AC-E09.
"""
from __future__ import annotations

import json
import os
from pathlib import Path

import pytest

import consilium_core as core
from test_consilium_cli import (
    convene,
    do_close,
    parse_session_id,
    read_session,
    read_transcript,
    session_dir,
    write_digest,
)

DEFAULT_CHECKLIST = [
    {"item_id": "overengineering", "verdict": "clear"},
    {"item_id": "bounded-context-erosion", "verdict": "clear"},
    {"item_id": "first-proposal-anchoring", "verdict": "clear"},
]


def _reply_with_checklist(responses) -> str:
    return (
        "Ответ участника с моделью E1, E2.\n\n```consilium-structured\n"
        + json.dumps({"elements": ["E1", "E2"], "new_findings": [],
                      "position_changes": [], "borrowed": [],
                      "risk_checklist_responses": responses}, ensure_ascii=False)
        + "\n```"
    )


def _kimi_argv_calls(workdir: Path) -> list[list[str]]:
    log = workdir / ".stub-state" / "invocations.jsonl"
    if not log.exists():
        return []
    return [
        json.loads(line)["argv"]
        for line in log.read_text(encoding="utf-8").splitlines()
        if line.strip() and json.loads(line)["cli"] == "kimi"
    ]


# ---------- AC-E02: регресс default-домена ----------

def test_ace02_default_domain_regression(run_consilium, stub_env, three_family_registry):
    """AC-E02: convene без --domain → architecture; состав И ПОСЛЕДОВАТЕЛЬНОСТЬ ролей
    = ROLE_CATALOG; снапшот каталога в session.json."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    session = read_session(workdir, session_id)
    assert session["domain"] == "architecture"
    snapshot = session["domain_snapshot"]
    assert snapshot["domain"] == "architecture"
    assert snapshot["version"] == 1
    assert snapshot["content_hash"]
    assert [r["id"] for r in snapshot["catalog"]["roles"]] == core.ROLE_CATALOG
    # round-robin назначение по умолчанию — дословно прежнее
    roles = [p["roles"]["A"] for p in session["participants"]]
    assert roles == ["security", "architecture", "pragmatics"]
    do_close(run_consilium, workdir, session_id, registry)


def test_ace02_domain_filter_on_nonempty_track_record(run_consilium, stub_env, three_family_registry):
    """AC-E02: домен-фильтр истории ролей не смещает default-назначение при
    непустом track record с чужим доменом."""
    workdir = stub_env["cwd"]
    track_dir = workdir / ".consilium-track-record"
    track_dir.mkdir()
    # история ролей claude в ДРУГОМ домене — не должна влиять на default-домен
    obs = {"consilium_id": "cons-old", "consilium_seq": 1, "date": "2026-07-01",
           "participant_id": "claude-opus", "family": "claude", "role": "атакующий",
           "domain": "security-compliance",
           "moderator_id": "primary", "moderator_is_participant": False,
           "findings_accepted": 0, "findings_withdrawn": 0,
           "critiques_upheld": 0, "critiques_overruled": 0,
           "model_killed": False, "unresponsive_events": 0, "outcome": "verdict"}
    (track_dir / "observations.jsonl").write_text(
        json.dumps(obs, ensure_ascii=False) + "\n", encoding="utf-8")
    (track_dir / "config.json").write_text('{"consiliums_completed": 1}', encoding="utf-8")
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    session = read_session(workdir, session_id)
    roles = [p["roles"]["A"] for p in session["participants"]]
    assert roles == ["security", "architecture", "pragmatics"]
    do_close(run_consilium, workdir, session_id, registry)


# ---------- AC-E03: неизвестный домен ----------

def test_ace03_unknown_domain_fail_closed(run_consilium, stub_env, three_family_registry):
    """AC-E03: convene --domain <unknown> → отказ с перечнем доступных id."""
    registry = three_family_registry()
    result = run_consilium("convene", "--question", "Q", "--registry", str(registry),
                           "--domain", "no-such-domain")
    assert result.returncode != 0
    output = result.stdout + result.stderr
    assert "architecture" in output and "security-compliance" in output


# ---------- AC-E04: прокладка lens/чеклиста в промпты ----------

def test_ace04_prompts_carry_lens_checklist_evidence(run_consilium, stub_env, three_family_registry):
    """AC-E04: proposal/attack/confirmation-промпты содержат title+lens роли,
    риск-чеклист и evidence домена; confirmation — роль фазы D."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0  # proposal
    kimi_proposal = " ".join(_kimi_argv_calls(workdir)[-1])
    assert "Оценивай прагматику" in kimi_proposal  # lens роли pragmatics
    assert "overengineering" in kimi_proposal
    assert "со ссылкой на артефакты репозитория" in kimi_proposal  # evidence

    digest = write_digest(workdir, "digest.md")
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0  # attack
    kimi_attack = " ".join(_kimi_argv_calls(workdir)[-1])
    assert "first-proposal-anchoring" in kimi_attack  # пункт applies_to attack

    # доводим до D: B до стопа, синтез (два источника), redteam, confirmation
    while read_session(workdir, session_id)["phase"] == "B":
        assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0
    synthesis = workdir / "synthesis.md"
    synthesis.write_text(
        "# Синтез\n\n- [E1] ядро (refs: seq:2)\n- [E2] транспорт (refs: seq:3)\n\n"
        "## Обоснование исключения вкладов\n\nВклад третьей модели перекрыт E1/E2.\n",
        encoding="utf-8")
    assert run_consilium("round", session_id, "--synthesis-file", str(synthesis)).returncode == 0
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0  # redteam
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0  # confirmation
    kimi_confirmation = " ".join(_kimi_argv_calls(workdir)[-1])
    # фаза D: у kimi (роль A = pragmatics) кросс-ротация → security
    assert "Оценивай решение через угрозы" in kimi_confirmation
    do_close(run_consilium, workdir, session_id, registry)


# ---------- AC-E05 (integration): контент-retry и fail-closed ----------

def test_ace05_content_retry_then_accept(run_consilium, stub_env, three_family_registry, monkeypatch):
    """AC-E05: невалидный чеклист → отклонение ДО transcript + ровно ОДИН
    контент-retry через ask той же сессии; валидный retry принимается."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    reply_dir = workdir / "replies"
    reply_dir.mkdir()
    # kimi-1: proposal с неполным покрытием чеклиста (нет bounded-context-erosion)
    (reply_dir / "kimi-1.md").write_text(
        _reply_with_checklist([{"item_id": "overengineering", "verdict": "clear"}]),
        encoding="utf-8")
    # kimi-2: контент-retry — валидный ответ
    (reply_dir / "kimi-2.md").write_text(_reply_with_checklist(DEFAULT_CHECKLIST), encoding="utf-8")
    monkeypatch.setenv("STUB_REPLY_DIR", str(reply_dir))

    session_id = convene(run_consilium, registry)
    result = run_consilium("round", session_id)  # proposal wave
    assert result.returncode == 0, f"proposal failed:\n{result.stdout}\n{result.stderr}"
    session = read_session(workdir, session_id)
    kimi = next(p for p in session["participants"] if p["id"] == "kimi-k2")
    assert kimi["state"] == "active"
    assert kimi["invocations"] == 2  # start + 1 контент-retry (ask)
    transcript = read_transcript(workdir, session_id)
    kimi_proposals = [r for r in transcript if r["author"] == "kimi-k2" and r["type"] == "proposal"]
    assert len(kimi_proposals) == 1  # отклонённый ход в transcript НЕ попал
    # контент-retry — через ask (resume), не через start
    kimi_calls = [c for c in _kimi_argv_calls(workdir) if c != ["doctor"]]
    assert len(kimi_calls) == 2  # start + ask
    assert "-r" in kimi_calls[1] or "--session" in kimi_calls[1]
    do_close(run_consilium, workdir, session_id, registry)


def test_ace05_persistent_invalid_goes_unresponsive(run_consilium, stub_env, three_family_registry, monkeypatch):
    """AC-E05: повторная невалидность после контент-retry → unresponsive."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    reply_dir = workdir / "replies"
    reply_dir.mkdir()
    bad = _reply_with_checklist([{"item_id": "overengineering", "verdict": "clear"}])
    (reply_dir / "kimi-1.md").write_text(bad, encoding="utf-8")
    (reply_dir / "kimi-2.md").write_text(bad, encoding="utf-8")
    monkeypatch.setenv("STUB_REPLY_DIR", str(reply_dir))

    session_id = convene(run_consilium, registry)
    result = run_consilium("round", session_id)
    assert result.returncode == 0
    session = read_session(workdir, session_id)
    kimi = next(p for p in session["participants"] if p["id"] == "kimi-k2")
    assert kimi["state"] == "unresponsive"
    assert kimi["invocations"] == 2
    do_close(run_consilium, workdir, session_id, registry)


def test_ace05_final_statement_needs_no_checklist(run_consilium, stub_env, three_family_registry):
    """AC-E05: final_statement чеклиста не требует (дословное заявление FR-03b)."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0
    digest = write_digest(workdir, "digest.md")
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0
    # детерминированный сценарий kill (как IT-10)
    sdir = session_dir(workdir, session_id)
    core.append_record(sdir, {
        "session_id": session_id, "phase": "B", "round": 1, "wave": "attack",
        "author": "codex-gpt", "type": "attack", "refs": [2],
        "content": "Атака на E1 kimi.",
        "structured": {"elements": [], "new_findings": [],
                       "position_changes": [{"element": "kimi-k2:E1", "action": "disagree", "refs": [2]}],
                       "borrowed": []},
    })
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0
    core.append_record(sdir, {
        "session_id": session_id, "phase": "B", "round": 1, "wave": "response",
        "author": "kimi-k2", "type": "response", "refs": [5],
        "content": "Принимаю критику E1.",
        "structured": {"elements": [], "new_findings": [],
                       "position_changes": [{"element": "E1", "action": "agree", "refs": [5]}],
                       "borrowed": []},
    })
    result = run_consilium("round", session_id, "--kill", "kimi-k2", "--digest-file", digest)
    assert result.returncode == 0, f"kill failed:\n{result.stdout}\n{result.stderr}"
    transcript = read_transcript(workdir, session_id)
    finals = [r for r in transcript if r["type"] == "final_statement"]
    assert len(finals) == 1  # финальное заявление принято БЕЗ чеклиста
    assert not finals[0]["structured"].get("risk_checklist_responses")
    do_close(run_consilium, workdir, session_id, registry)


# ---------- AC-E06/E09: domain и checklist_stats в observations ----------

def test_ace06_ace09_domain_and_checklist_stats_in_observations(run_consilium, stub_env, three_family_registry, monkeypatch):
    """AC-E06/E09: domain в session.json и каждой записи observations;
    checklist_stats {hit, clear, na} по принятым ходам; final_statement исключён."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    reply_dir = workdir / "replies"
    reply_dir.mkdir()
    hit_reply = _reply_with_checklist([
        {"item_id": "migration-cost-section", "verdict": "clear"},
        {"item_id": "consumer-impact-repo", "verdict": "hit", "note": "влияние недооценено"},
    ])
    (reply_dir / "kimi-1.md").write_text(hit_reply, encoding="utf-8")
    monkeypatch.setenv("STUB_REPLY_DIR", str(reply_dir))

    session_id = convene(run_consilium, registry, "--domain", "data-schema-evolution")
    session = read_session(workdir, session_id)
    assert session["domain"] == "data-schema-evolution"
    assert run_consilium("round", session_id).returncode == 0  # proposal (kimi: hit+clear)
    do_close(run_consilium, workdir, session_id, registry)

    observations = [
        json.loads(line)
        for line in (workdir / ".consilium-track-record" / "observations.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    assert observations
    for obs in observations:
        assert obs["domain"] == "data-schema-evolution"
    kimi_obs = [o for o in observations if o["participant_id"] == "kimi-k2"]
    assert kimi_obs
    # proposal kimi (принятый ход): hit=1, clear=1, na=0
    assert kimi_obs[0]["checklist_stats"] == {"hit": 1, "clear": 1, "na": 0}
    # claude/codex — динамический stub-ответ (оба пункта clear)
    claude_obs = [o for o in observations if o["participant_id"] == "claude-opus"][0]
    assert claude_obs["checklist_stats"] == {"hit": 0, "clear": 2, "na": 0}


# ---------- Rework по internal review: F-01, F-03, F-05 ----------

def test_f01_strengths_filtered_by_domain(run_consilium, stub_env, three_family_registry):
    """F-01: strengths из другого домена (n_eff>=3) НЕ влияют на назначение ролей
    default-домена — фильтр observations по домену сессии."""
    workdir = stub_env["cwd"]
    track_dir = workdir / ".consilium-track-record"
    track_dir.mkdir()
    # 3 чистых наблюдения: claude «силён» в pragmatics, но в ЧУЖОМ домене
    observations = [
        {"consilium_id": f"cons-old-{i}", "consilium_seq": i, "date": "2026-07-01",
         "participant_id": "claude-opus", "family": "claude", "role": "pragmatics",
         "domain": "security-compliance",
         "moderator_id": "primary", "moderator_is_participant": False,
         "findings_accepted": 5, "findings_withdrawn": 0,
         "critiques_upheld": 4, "critiques_overruled": 0,
         "model_killed": False, "unresponsive_events": 0, "outcome": "verdict"}
        for i in (1, 2, 3)
    ]
    (track_dir / "observations.jsonl").write_text(
        "\n".join(json.dumps(o, ensure_ascii=False) for o in observations) + "\n", encoding="utf-8")
    (track_dir / "config.json").write_text('{"consiliums_completed": 2}', encoding="utf-8")

    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    session = read_session(workdir, session_id)
    roles = {p["id"]: p["roles"]["A"] for p in session["participants"]}
    # фильтр по домену: чужая статистика проигнорирована, round-robin default
    assert roles["claude-opus"] == "security"
    assert roles["codex-gpt"] == "architecture"
    assert roles["kimi-k2"] == "pragmatics"
    do_close(run_consilium, workdir, session_id, registry)


def test_f03_checklist_stats_written_once_per_participant(run_consilium, stub_env, three_family_registry):
    """F-03: checklist_stats пишется один раз на участника (на A-роли), без double-count."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0
    # полный путь до verdict (D-роли назначаются) — чтобы у участников было 2 роли
    digest = write_digest(workdir, "digest.md")
    while read_session(workdir, session_id)["phase"] == "B":
        assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0
    synthesis = workdir / "synthesis.md"
    synthesis.write_text(
        "# Синтез\n\n- [E1] ядро (refs: seq:2)\n- [E2] транспорт (refs: seq:3)\n\n"
        "## Обоснование исключения вкладов\n\nПерекрыто E1/E2.\n", encoding="utf-8")
    assert run_consilium("round", session_id, "--synthesis-file", str(synthesis)).returncode == 0
    while read_session(workdir, session_id)["phase"] == "D":
        assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0
    decision = workdir / "decision.md"
    decision.write_text("Решение по seq:2.", encoding="utf-8")
    assert run_consilium("verdict", session_id, "--decision-file", str(decision)).returncode == 0
    do_close(run_consilium, workdir, session_id, registry)

    observations = [
        json.loads(line)
        for line in (workdir / ".consilium-track-record" / "observations.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    by_participant: dict[str, list[dict]] = {}
    for obs in observations:
        by_participant.setdefault(obs["participant_id"], []).append(obs)
    for pid, obs_list in by_participant.items():
        assert len(obs_list) >= 2, f"{pid}: ожидались записи по ролям A и D"
        with_stats = [o for o in obs_list if "checklist_stats" in o]
        assert len(with_stats) == 1, f"{pid}: checklist_stats дублируется ({len(with_stats)})"


def test_f05_broken_domains_actionable_failure(run_consilium, stub_env, three_family_registry, tmp_path):
    """F-05: битый/отсутствующий domains.yaml → actionable-отказ в doctor и convene."""
    registry = three_family_registry()
    broken = tmp_path / "broken-domains.yaml"
    broken.write_text("version: 1\ndomains:\n  - id: x\n", encoding="utf-8")

    result = run_consilium("doctor", "--registry", str(registry), "--domains", str(broken), "--json")
    assert result.returncode != 0
    assert "domains.yaml" in (result.stdout + result.stderr)

    result = run_consilium("convene", "--question", "Q", "--registry", str(registry),
                           "--domains", str(broken))
    assert result.returncode != 0
    assert "domains.yaml" in (result.stdout + result.stderr)

    missing = tmp_path / "missing-domains.yaml"
    result = run_consilium("convene", "--question", "Q", "--registry", str(registry),
                           "--domains", str(missing))
    assert result.returncode != 0
    assert "domains.yaml" in (result.stdout + result.stderr)
