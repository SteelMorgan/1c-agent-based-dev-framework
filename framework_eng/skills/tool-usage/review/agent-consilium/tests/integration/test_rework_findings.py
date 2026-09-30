"""Rework по findings QA (qa-verification.md): F-01..F-05.

Каждый тест сначала воспроизводит дефект (red), затем подтверждает фикс (green).
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

import pytest

import adapter_contract as ac
import consilium
import consilium_core as core
from consilium_cleanup_assertions import assert_cleanup_checkpoint
from test_consilium_cli import (
    convene,
    parse_session_id,
    read_session,
    session_dir,
    write_digest,
)

REAL_IDS = ("claude-opus", "codex-gpt", "kimi-k2")


def _kimi_argv_calls(workdir: Path) -> list[list[str]]:
    log = workdir / ".stub-state" / "invocations.jsonl"
    if not log.exists():
        return []
    return [
        json.loads(line)["argv"]
        for line in log.read_text(encoding="utf-8").splitlines()
        if line.strip() and json.loads(line)["cli"] == "kimi"
    ]


def _seed_records(sdir: Path, session_id: str) -> None:
    """Детерминированный сценарий: codex критикует kimi:E1, kimi соглашается →
    кандидат на kill — kimi-k2 (как в IT-10)."""
    core.append_record(sdir, {
        "session_id": session_id, "phase": "B", "round": 1, "wave": "attack",
        "author": "codex-gpt", "type": "attack", "refs": [2],
        "content": "Атака на элемент E1 модели kimi.",
        "structured": {"elements": [], "new_findings": [],
                       "position_changes": [{"element": "kimi-k2:E1", "action": "disagree", "refs": [2]}],
                       "borrowed": []},
    })
    core.append_record(sdir, {
        "session_id": session_id, "phase": "B", "round": 1, "wave": "response",
        "author": "kimi-k2", "type": "response", "refs": [5],
        "content": "Принимаю критику E1.",
        "structured": {"elements": [], "new_findings": [],
                       "position_changes": [{"element": "E1", "action": "agree", "refs": [5]}],
                       "borrowed": []},
    })


# ---------------------------------------------------------------------------
# F-01 (block, FR-04): утечка анонимизации в attack-промпте раунда 1
# ---------------------------------------------------------------------------

def test_f01_no_real_ids_in_round1_attack_prompt_and_bundles(run_consilium, stub_env, three_family_registry):
    """F-01: bundle фазы A (proposals) и attack-промпт раунда 1 НЕ содержат реальных id."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0  # фаза A
    digest = write_digest(workdir, "digest.md")
    result = run_consilium("round", session_id, "--digest-file", digest)  # attack r1
    assert result.returncode == 0

    # сохранённые bundles (выдача участникам) не содержат реальных id
    bundles = sorted((session_dir(workdir, session_id) / "bundles").glob("*.bundle.md"))
    assert bundles, "bundles не созданы"
    for bundle in bundles:
        text = bundle.read_text(encoding="utf-8")
        for real_id in REAL_IDS:
            assert real_id not in text, f"{bundle.name}: утечка реального id {real_id}"

    # attack-промпт раунда 1 (kimi получает его через argv -p) анонимизирован
    kimi_calls = _kimi_argv_calls(workdir)
    assert len(kimi_calls) >= 2, "kimi должен быть вызван в A и в attack r1"
    attack_argv = " ".join(kimi_calls[-1])
    for real_id in REAL_IDS:
        assert real_id not in attack_argv, f"attack-промпт раунда 1 содержит реальный id {real_id}"
    result = run_consilium("close", session_id, "--registry", str(registry))
    assert result.returncode == 0


# ---------------------------------------------------------------------------
# F-02 (major): focused-paths через symlink-зеркала
# ---------------------------------------------------------------------------

def test_f02_symlinked_focused_paths(run_consilium, stub_env, three_family_registry):
    """F-02: focused-path через symlink-каталог resolve'ится, участник стартует."""
    workdir = stub_env["cwd"]
    real_dir = workdir / "materials-real"
    real_dir.mkdir()
    (real_dir / "design.md").write_text("материал для консилиума", encoding="utf-8")
    (workdir / "materials-link").symlink_to(real_dir, target_is_directory=True)

    registry = three_family_registry()
    session_id = convene(run_consilium, registry, "--paths", "materials-link")
    result = run_consilium("round", session_id)  # фаза A: start всех участников
    assert result.returncode == 0, f"phase A failed:\n{result.stdout}\n{result.stderr}"
    session = read_session(workdir, session_id)
    for participant in session["participants"]:
        assert participant["state"] == "active", f"{participant['id']} не стартовал: {participant['state']}"
        assert participant["review_id"], f"{participant['id']}: нет review_id"
    # sandbox-копия содержит resolve-нутый материал
    sandboxes = list((workdir / ".review-sandboxes").glob("*/workspace/**/design.md"))
    assert sandboxes, "design.md не скопирован в sandbox участников"
    result = run_consilium("close", session_id, "--registry", str(registry))
    assert result.returncode == 0


# ---------------------------------------------------------------------------
# F-03 (block, FR-03/FR-11): атомарный kill, участник без review_id
# ---------------------------------------------------------------------------

def test_f03a_kill_unresponsive_participant_atomic(run_consilium, stub_env, set_stub_mode, three_family_registry):
    """F-03a: kill unresponsive-участника (с review_id) — атомарно: kill_log ↔
    kill_decision ↔ состояние ↔ verdict согласованы."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0  # A
    digest = write_digest(workdir, "digest.md")
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0  # attack r1
    set_stub_mode("timeout:kimi")
    assert run_consilium("round", session_id, "--digest-file", digest, ).returncode == 0  # response r1
    set_stub_mode("ok")
    session = read_session(workdir, session_id)
    assert next(p for p in session["participants"] if p["id"] == "kimi-k2")["state"] == "unresponsive"
    _seed_records(session_dir(workdir, session_id), session_id)

    # FU-01: на kill-пути дайджест записывается ровно один раз
    digests_before = sum(
        1 for r in core.read_transcript(session_dir(workdir, session_id)) if r["type"] == "digest"
    )
    result = run_consilium("round", session_id, "--kill", "kimi-k2", "--digest-file", digest)
    assert result.returncode == 0, f"kill failed:\n{result.stdout}\n{result.stderr}"

    # атомарная согласованность session.json ↔ transcript
    session = read_session(workdir, session_id)
    kimi = next(p for p in session["participants"] if p["id"] == "kimi-k2")
    assert kimi["state"] == "killed"
    assert kimi["killed_at_round"] == 1  # FU-02: kill за завершённым раундом 1
    assert len(session["kill_log"]) == 1
    assert session["kill_log"][0]["round"] == 1  # FU-02
    transcript = core.read_transcript(session_dir(workdir, session_id))
    kill_records = [r for r in transcript if r["type"] == "kill_decision"]
    assert len(kill_records) == 1
    assert kill_records[0]["round"] == 1  # FU-02
    assert "kimi-k2" in kill_records[0]["content"]
    digests_after = sum(1 for r in transcript if r["type"] == "digest")
    assert digests_after == digests_before + 1  # FU-01: без дубля на kill-пути

    # verdict отражает kill согласованно
    while read_session(workdir, session_id)["phase"] == "B":
        assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0
    synthesis = workdir / "synthesis.md"
    synthesis.write_text(
        "# Синтез\n\n"
        "- [E1] разделение ядра и доменов (refs: seq:2)\n"
        "- [E2] транспорт через события (refs: seq:3)\n\n"
        "## Обоснование исключения вкладов\n\n"
        "Вклад невошедших живых моделей перекрыт элементами E1 и E2.\n",
        encoding="utf-8",
    )
    assert run_consilium("round", session_id, "--synthesis-file", str(synthesis)).returncode == 0
    while read_session(workdir, session_id)["phase"] == "D":
        assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0
    decision = workdir / "decision.md"
    decision.write_text("Решение по seq:2.", encoding="utf-8")
    assert run_consilium("verdict", session_id, "--decision-file", str(decision)).returncode == 0
    verdict_text = (session_dir(workdir, session_id) / "verdict.md").read_text(encoding="utf-8")
    assert "kimi-k2" in verdict_text
    assert "kill не производился" not in verdict_text
    assert run_consilium("close", session_id, "--registry", str(registry)).returncode == 0


def test_f03b_kill_without_review_id_no_adapter_call(run_consilium, stub_env, three_family_registry):
    """F-03b: kill участника без review_id (start не состоялся) — без обращения к
    адаптеру, без краша, состояние применяется полностью."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0  # A
    digest = write_digest(workdir, "digest.md")
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0  # attack r1
    sdir = session_dir(workdir, session_id)
    # симулируем потерю сессии адаптера у kimi (start не состоялся)
    session = read_session(workdir, session_id)
    next(p for p in session["participants"] if p["id"] == "kimi-k2")["review_id"] = None
    (sdir / "session.json").write_text(json.dumps(session, ensure_ascii=False, indent=2), encoding="utf-8")
    _seed_records(sdir, session_id)
    kimi_calls_before = len(_kimi_argv_calls(workdir))

    result = run_consilium("round", session_id, "--kill", "kimi-k2", "--digest-file", digest)
    assert result.returncode == 0, f"kill failed:\n{result.stdout}\n{result.stderr}"
    # адаптер kimi НЕ вызывался для финального заявления
    assert len(_kimi_argv_calls(workdir)) == kimi_calls_before

    session = read_session(workdir, session_id)
    kimi = next(p for p in session["participants"] if p["id"] == "kimi-k2")
    assert kimi["state"] == "killed"
    assert len(session["kill_log"]) == 1
    transcript = core.read_transcript(sdir)
    assert sum(1 for r in transcript if r["type"] == "kill_decision") == 1
    assert any(
        r["type"] == "system" and "финальное заявление недоступно" in r["content"]
        for r in transcript
    )
    assert run_consilium("close", session_id, "--registry", str(registry)).returncode == 0


def test_f03c_kill_atomicity_under_injected_failure(run_consilium, stub_env, three_family_registry, monkeypatch):
    """F-03c: инъекция сбоя посередине kill (run_wave падает) — нет частичного
    применения: либо полная согласованность, либо откат; краха нет."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0  # A
    digest = write_digest(workdir, "digest.md")
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0  # attack r1
    sdir = session_dir(workdir, session_id)
    _seed_records(sdir, session_id)

    monkeypatch.chdir(workdir)
    session = json.loads((sdir / "session.json").read_text(encoding="utf-8"))
    registry_data = core.load_registry(registry)
    args = argparse.Namespace(kill="kimi-k2", digest_file=digest, synthesis_file=None)

    def boom(tasks, wave_timeout_sec=ac.WAVE_TIMEOUT_SEC):
        raise RuntimeError("injected wave failure")

    monkeypatch.setattr(ac, "run_wave", boom)
    rc = consilium._handle_kill(workdir, sdir, session, registry_data, args)
    assert rc == 0  # краха нет

    # согласованность: kill применён полностью И сбой зафиксирован
    saved = json.loads((sdir / "session.json").read_text(encoding="utf-8"))
    kimi = next(p for p in saved["participants"] if p["id"] == "kimi-k2")
    assert kimi["state"] == "killed"
    assert len(saved["kill_log"]) == 1
    transcript = core.read_transcript(sdir)
    assert sum(1 for r in transcript if r["type"] == "kill_decision") == 1
    assert any(r["type"] == "system" and "final statement" in r["content"] for r in transcript)
    result = run_consilium("close", session_id, "--registry", str(registry))
    assert result.returncode == 0


# ---------------------------------------------------------------------------
# F-04 (major, FR-09): strengths.json не доверяется на чтении
# ---------------------------------------------------------------------------

def test_f04_tampered_strengths_json_ignored(run_consilium, stub_env, three_family_registry):
    """F-04: подменённый strengths.json НЕ влияет на назначение ролей —
    проекция пересчитывается из observations при каждом чтении."""
    workdir = stub_env["cwd"]
    track_dir = workdir / ".consilium-track-record"
    track_dir.mkdir()
    # 3 чистых наблюдения: claude силён в security (n_eff = 3 ≥ floor);
    # 4-е наблюдение с другой ролью — чтобы last_roles не блокировал security (FR-08)
    observations = [
        {"consilium_id": f"cons-old-{i}", "consilium_seq": i, "date": "2026-07-01",
         "participant_id": "claude-opus", "family": "claude", "role": "security",
         "moderator_id": "primary", "moderator_is_participant": False,
         "findings_accepted": 4, "findings_withdrawn": 0,
         "critiques_upheld": 3, "critiques_overruled": 0,
         "model_killed": False, "unresponsive_events": 0, "outcome": "verdict"}
        for i in (1, 2, 3)
    ]
    observations.append({
        "consilium_id": "cons-old-4", "consilium_seq": 4, "date": "2026-07-02",
        "participant_id": "claude-opus", "family": "claude", "role": "pragmatics",
        "moderator_id": "primary", "moderator_is_participant": False,
        "findings_accepted": 1, "findings_withdrawn": 0,
        "critiques_upheld": 0, "critiques_overruled": 0,
        "model_killed": False, "unresponsive_events": 0, "outcome": "verdict",
    })
    (track_dir / "observations.jsonl").write_text(
        "\n".join(json.dumps(o, ensure_ascii=False) for o in observations) + "\n", encoding="utf-8"
    )
    (track_dir / "config.json").write_text('{"consiliums_completed": 2}', encoding="utf-8")
    # ПОДМЕНА: strengths.json утверждает обратное (kimi «силён», claude «слаб»)
    (track_dir / "strengths.json").write_text(json.dumps({
        "claude-opus|security": {"score": 0.0, "n_eff": 0, "accept_rate": 0.0, "upheld_rate": 0.0},
        "kimi-k2|security": {"score": 0.99, "n_eff": 99, "accept_rate": 0.99, "upheld_rate": 0.99},
    }, ensure_ascii=False, indent=2), encoding="utf-8")

    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    session = read_session(workdir, session_id)
    roles = {p["id"]: p["roles"]["A"] for p in session["participants"]}
    # честный пересчёт из observations: security → claude-opus; подмена отвергнута
    assert roles["claude-opus"] == "security"
    assert roles["kimi-k2"] != "security"
    result = run_consilium("close", session_id, "--registry", str(registry))
    assert result.returncode == 0


def test_f04b_runtime_dirs_in_gitignore():
    """F-04 (сопутствующее): .consilium-sessions/ и .consilium-track-record/ в .gitignore."""
    repo_root = Path(__file__).resolve().parents[5]
    gitignore = (repo_root / ".gitignore").read_text(encoding="utf-8")
    assert ".consilium-sessions" in gitignore
    assert ".consilium-track-record" in gitignore


# ---------------------------------------------------------------------------
# F-05 (minor): unresponsive без модели — не «живая модель»
# ---------------------------------------------------------------------------

def test_f05_unresponsive_without_model_excluded_from_alive(run_consilium, stub_env, set_stub_mode, three_family_registry):
    """F-05: участник, ставший unresponsive в фазе A (без proposal), исключается из
    живых моделей: конвергенция достижима, kill-кандидатом не предлагается."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    set_stub_mode("error:kimi")
    result = run_consilium("round", session_id)  # A: kimi error×2 → unresponsive, БЕЗ proposal
    assert result.returncode == 0
    set_stub_mode("ok")
    session = read_session(workdir, session_id)
    kimi = next(p for p in session["participants"] if p["id"] == "kimi-k2")
    assert kimi["state"] == "unresponsive"

    digest = write_digest(workdir, "digest.md")
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0  # attack r1
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0  # response r1

    session = read_session(workdir, session_id)
    # alive=2 (kimi без модели исключён) → конвергенция после раунда 1
    assert session["phase"] == "C"
    assert session["stop_state"]["stop_reason"] == "converged"
    # status: kimi не в живых моделях
    status = run_consilium("status", session_id)
    status_json = json.loads(status.stdout[status.stdout.index("{"):])
    assert "kimi-k2" not in status_json["alive_models"]
    assert "kimi-k2" in status_json["unresponsive"]  # факт заморозки зафиксирован
    result = run_consilium("close", session_id, "--registry", str(registry))
    assert result.returncode == 0


def test_f05_unit_alive_model_ids(turn_factory):
    """F-05 (unit): alive_model_ids — не killed И имеет proposal."""
    participants = [
        {"id": "p1", "state": "active"},
        {"id": "p2", "state": "unresponsive"},   # без proposal → исключён
        {"id": "p3", "state": "unresponsive"},   # с proposal → живая (замороженная) модель
        {"id": "p4", "state": "killed"},         # killed → исключён
    ]
    records = [
        turn_factory(seq=1, author="p1", type="proposal", phase="A", round=0, wave="proposal"),
        turn_factory(seq=2, author="p3", type="proposal", phase="A", round=0, wave="proposal"),
        turn_factory(seq=3, author="p4", type="proposal", phase="A", round=0, wave="proposal"),
    ]
    assert core.alive_model_ids(records, participants) == ["p1", "p3"]


# ---------------------------------------------------------------------------
# R-Final F-02: probe-режим doctor (TD 4.6)
# ---------------------------------------------------------------------------

def test_doctor_probe_executes_live_invocation(run_consilium, stub_env, three_family_registry):
    """R-Final F-02: --probe выполняет 1 живой invocation на участника + парный close."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    result = run_consilium("doctor", "--registry", str(registry), "--json", "--probe")
    assert result.returncode == 0, f"doctor --probe failed:\n{result.stdout}\n{result.stderr}"
    report = json.loads(result.stdout[result.stdout.index("{"):])
    assert report["excluded"] == []
    assert report["quorum"]["ok"] is True
    # каждый CLI реально вызван (probe — это invocation адаптера, не только --help)
    log = workdir / ".stub-state" / "invocations.jsonl"
    calls = [json.loads(line) for line in log.read_text(encoding="utf-8").splitlines() if line.strip()]
    for cli in ("claude", "codex", "kimi"):
        assert any(c["cli"] == cli for c in calls), f"probe не вызвал {cli}"
    # probe закрыл sandbox'ы (парность); probe-файлы убраны
    assert_cleanup_checkpoint(workdir)
    assert list(workdir.glob(".doctor-probe-*.md")) == []
