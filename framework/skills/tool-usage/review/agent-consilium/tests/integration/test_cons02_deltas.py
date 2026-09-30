"""Integration-слой CONS-02 (spec-delta): OPT-1/OPT-2/OPT-3 на stub CLI.

Trace: AC-D02, AC-D03, AC-D05, AC-D07, AC-D08, AC-D09, AC-D10, AC-D11.
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
from test_consilium_cli import (
    convene,
    do_close,
    drive_to_phase_e,
    parse_session_id,
    read_session,
    read_transcript,
    session_dir,
    write_digest,
)

SYNTH_SINGLE_SOURCE = """# Синтез

- [E1] разделение ядра и доменов (refs: seq:2)

## Обоснование исключения вкладов

Вклад codex-gpt и kimi-k2 не вошёл: их элементы перекрыты E1 модели-источника.
"""

SYNTH_TWO_SOURCES = """# Синтез

- [E1] разделение ядра и доменов (refs: seq:2)
- [E2] транспорт через события (refs: seq:3)

## Обоснование исключения вкладов

Вклад kimi-k2 не вошёл: его элементы перекрыты E1 и E2.
"""


def _drive_b_to_c(run_consilium, workdir, session_id):
    digest = write_digest(workdir, "digest.md")
    while read_session(workdir, session_id)["phase"] == "B":
        result = run_consilium("round", session_id, "--digest-file", digest)
        assert result.returncode == 0, f"B wave failed:\n{result.stdout}\n{result.stderr}"
    return digest


def _run_synthesis(run_consilium, workdir, session_id, text):
    synthesis = workdir / "synthesis.md"
    synthesis.write_text(text, encoding="utf-8")
    result = run_consilium("round", session_id, "--synthesis-file", str(synthesis))
    assert result.returncode == 0, f"synthesis failed:\n{result.stdout}\n{result.stderr}"


def _verdict(run_consilium, workdir, session_id):
    decision = workdir / "decision.md"
    decision.write_text("Решение по seq:2.", encoding="utf-8")
    result = run_consilium("verdict", session_id, "--decision-file", str(decision))
    assert result.returncode == 0, f"verdict failed:\n{result.stdout}\n{result.stderr}"
    return (session_dir(workdir, session_id) / "verdict.md").read_text(encoding="utf-8")


# ---------- AC-D02/D03: таймауты (OPT-1) ----------

def test_acd02_default_timeouts_in_session(run_consilium, stub_env, three_family_registry):
    """AC-D02: default per-invocation 900; поведение unresponsive-политики неизменно (IT-02)."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    result = run_consilium(
        "convene", "--question", "Q", "--registry", str(registry),
    )
    assert result.returncode == 0, f"convene failed:\n{result.stdout}\n{result.stderr}"
    session = read_session(workdir, parse_session_id(result.stdout))
    assert session["timeout_sec"] == 900
    assert session["wall_clock"]["budget_sec"] == 16620
    assert session["wall_clock"]["warn_at_sec"] == 12465
    do_close(run_consilium, workdir, session["session_id"], registry)


def test_acd03_override_recomputes_wave_and_wallclock(run_consilium, stub_env, three_family_registry, monkeypatch):
    """AC-D03: override per-invocation фиксируется; волна = T+240 и cap (с нижним
    полом 7200) пересчитываются от переопределённого."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry, "--timeout-sec", "120")
    session = read_session(workdir, session_id)
    assert session["timeout_sec"] == 120
    assert session["wall_clock"]["budget_sec"] == 7200   # 13×360+1800 = 5700 < 7200
    assert session["wall_clock"]["warn_at_sec"] == 5400

    # волна получает timeout из состояния сессии (T + 240), не модульную константу
    monkeypatch.chdir(workdir)
    captured: dict = {}
    real_run_wave = ac.run_wave

    def spy(tasks, wave_timeout_sec=ac.WAVE_TIMEOUT_SEC):
        captured["wave_timeout_sec"] = wave_timeout_sec
        return real_run_wave(tasks, wave_timeout_sec)

    monkeypatch.setattr(ac, "run_wave", spy)
    session = json.loads((session_dir(workdir, session_id) / "session.json").read_text(encoding="utf-8"))
    registry_data = core.load_registry(registry)
    args = argparse.Namespace(session_id=session_id, digest_file=None, kill=None,
                              synthesis_file=None, registry=None)
    consilium.cmd_round(args)
    assert captured["wave_timeout_sec"] == 120 + 240
    # добиваем сессию честно (внешний close после in-process round)
    do_close(run_consilium, workdir, session_id, registry)


# ---------- AC-D05/D10: черновик дайджеста (OPT-2) ----------

def test_acd05_draft_generated_but_gate_stays_fail_closed(run_consilium, stub_env, three_family_registry):
    """AC-D05: черновик пишется артефактом сессии и system-записью; digest-gate
    остаётся fail-closed — автоподстановки нет."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    result = run_consilium("round", session_id)  # фаза A → черновик после волны
    assert result.returncode == 0
    sdir = session_dir(workdir, session_id)
    drafts = list((sdir / "digests").glob("draft-*.md"))
    assert drafts, "черновик дайджеста не сгенерирован"
    transcript = read_transcript(workdir, session_id)
    assert any(
        r["type"] == "system" and "participant-extract" in r["content"]
        for r in transcript
    ), "черновик не зафиксирован system-записью"
    # волна без --digest-file отклоняется и при наличии черновика
    result = run_consilium("round", session_id)
    assert result.returncode != 0
    assert "digest" in (result.stdout + result.stderr).lower()
    do_close(run_consilium, workdir, session_id, registry)


def test_acd10_draft_never_reaches_participant_bundles(run_consilium, stub_env, three_family_registry):
    """AC-D10 (регресс): черновик не появляется ни в одном wave-bundle участников."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0
    digest = write_digest(workdir, "digest.md")
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0  # attack r1
    for bundle in (session_dir(workdir, session_id) / "bundles").glob("*.bundle.md"):
        assert "participant-extract" not in bundle.read_text(encoding="utf-8"), \
            f"черновик утёк в bundle {bundle.name}"
    do_close(run_consilium, workdir, session_id, registry)


# ---------- AC-D07/D08/D09/D11: условная фаза D (OPT-3) ----------

def test_acd07_d09_d11_phase_d_skipped_on_single_source(run_consilium, stub_env, three_family_registry):
    """AC-D07/D09/D11: предикат выполнен → фаза D пропущена: переход C→E, system-запись
    с причиной, раздел в verdict, 0 вызовов фазы D, skip-метрика в track record."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0
    _drive_b_to_c(run_consilium, workdir, session_id)

    invocations_before = read_session(workdir, session_id)["invocation_count"]
    _run_synthesis(run_consilium, workdir, session_id, SYNTH_SINGLE_SOURCE)
    session = read_session(workdir, session_id)
    assert session["phase"] == "E"  # C→E без D-волн
    # AC-D09: вызовов адаптера в фазе D = 0
    assert session["invocation_count"] == invocations_before
    transcript = read_transcript(workdir, session_id)
    assert not any(r["type"] in ("redteam_attack", "confirmation") for r in transcript)
    skip_records = [
        r for r in transcript
        if r["type"] == "system" and "фаза D пропущена" in r["content"]
    ]
    assert skip_records, "system-запись о пропуске D отсутствует"
    assert "claude-opus" in skip_records[0]["content"]  # модель-источник в причине

    verdict_text = _verdict(run_consilium, workdir, session_id)
    assert "фаза d пропущена" in verdict_text.lower()
    # F-04: секция обоснования исключения вкладов попадает в verdict
    assert "Обоснование исключения вкладов" in verdict_text
    assert "Вклад codex-gpt и kimi-k2 не вошёл" in verdict_text

    do_close(run_consilium, workdir, session_id, registry)
    observations = [
        json.loads(line)
        for line in (workdir / ".consilium-track-record" / "observations.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    assert observations
    for obs in observations:
        assert obs["phase_d_skipped"] is True
        assert obs["phase_d_saved_invocations"] >= 1


def test_acd08_d11_regression_phase_d_runs_on_two_sources(run_consilium, stub_env, three_family_registry):
    """AC-D08 (регресс)/D11: ≥2 модели-источника → фаза D выполняется как раньше;
    skip-метрика skip=false присутствует в observations."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0
    _drive_b_to_c(run_consilium, workdir, session_id)
    _run_synthesis(run_consilium, workdir, session_id, SYNTH_TWO_SOURCES)
    session = read_session(workdir, session_id)
    assert session["phase"] == "D"
    assert session["redteamer"]  # кросс-ротация назначена

    digest = write_digest(workdir, "digest2.md")
    while read_session(workdir, session_id)["phase"] == "D":
        assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0
    transcript = read_transcript(workdir, session_id)
    assert any(r["type"] == "redteam_attack" for r in transcript)
    assert any(r["type"] == "confirmation" for r in transcript)

    verdict_text = _verdict(run_consilium, workdir, session_id)
    assert "фаза d пропущена" not in verdict_text.lower()
    # F-04: секция обоснования исключения вкладов попадает в verdict и при выполненной D
    assert "Обоснование исключения вкладов" in verdict_text
    assert "Вклад kimi-k2 не вошёл" in verdict_text

    do_close(run_consilium, workdir, session_id, registry)
    observations = [
        json.loads(line)
        for line in (workdir / ".consilium-track-record" / "observations.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    assert observations
    for obs in observations:
        assert obs["phase_d_skipped"] is False
        assert obs["phase_d_saved_invocations"] == 0
