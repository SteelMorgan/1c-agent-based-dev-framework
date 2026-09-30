"""Integration-слой CONS-06: human-critic режим end-to-end (stub-адаптеры).

Trace: E1/E2 (convene --human-critic, turn-file на pending атакующую волну),
E5 (пауза wall-clock в status), E6 (wait-cap → awaiting_moderator_decision и
решения модератора), E7 (human ходит до LLM-волны, ход в bundle), E9 (lint
digest по protected identifier), E10 (наблюдаемость состояния).
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import pytest

import consilium_core as core

SCRIPTS_DIR = Path(__file__).resolve().parent.parent.parent / "scripts"
DIGEST_TEXT = "Дайджест модератора: живые модели M1, M2, M3; открытые разногласия по E1, E2."
H = core.HUMAN_CRITIC_ID


def parse_session_id(stdout: str) -> str:
    match = re.search(r"session_id:\s*(\S+)", stdout)
    assert match, f"session_id не найден:\n{stdout}"
    return match.group(1)


def sdir(workdir: Path, session_id: str) -> Path:
    return workdir / ".consilium-sessions" / session_id


def read_session(workdir: Path, session_id: str) -> dict:
    return json.loads((sdir(workdir, session_id) / "session.json").read_text(encoding="utf-8"))


def write_digest(workdir: Path, text: str = DIGEST_TEXT) -> str:
    path = workdir / "digest.md"
    path.write_text(text, encoding="utf-8")
    return str(path)


def write_turn(workdir: Path, name="human-turn.json", **overrides) -> str:
    payload = {
        "content": "Критика M1:E2 — нет критериев отката изменений.",
        "structured": {"position_changes": [{"element": "M1:E2", "action": "disagree",
                                             "refs": []}]},
        "human_approved": True,
        **overrides,
    }
    path = workdir / name
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return str(path)


def convene_human(run_consilium, registry, *extra):
    result = run_consilium(
        "convene", "--question", "Как разделить ядро и домены?",
        "--registry", str(registry), "--timeout-sec", "30",
        "--human-critic", *extra,
    )
    assert result.returncode == 0, f"convene failed:\n{result.stdout}\n{result.stderr}"
    return parse_session_id(result.stdout)


def to_attack_wave(run_consilium, workdir, session_id):
    """Фаза A (proposal не требует human-хода) → pending attack раунда 1."""
    result = run_consilium("round", session_id)
    assert result.returncode == 0, f"phase A failed:\n{result.stdout}\n{result.stderr}"
    assert read_session(workdir, session_id)["wave"] == "attack"


# ---------------------------------------------------------------------------
# E1/E2: convene --human-critic
# ---------------------------------------------------------------------------

def test_hc_convene_creates_human_block_and_anon_id(run_consilium, workdir,
                                                    three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry())
    session = read_session(workdir, session_id)
    hc = session["human_critic"]
    assert hc["enabled"] is True and hc["state"] == "idle"
    assert hc["wait_cap_sec"] == core.DEFAULT_HUMAN_WAIT_CAP_SEC
    # E1: human НЕ participant сессии.
    assert [p["id"] for p in session["participants"]] == ["claude-opus", "codex-gpt", "kimi-k2"]
    anon_map = json.loads((sdir(workdir, session_id) / "anon_map.json").read_text(encoding="utf-8"))
    assert H in anon_map and anon_map[H].startswith("M")
    assert len(anon_map) == 4
    # Пауза wall-clock persisted (E5).
    assert session["wall_clock"]["paused_sec"] == 0.0


def test_hc_default_convene_without_human_block(run_consilium, workdir,
                                                three_family_registry):
    result = run_consilium(
        "convene", "--question", "Вопрос?", "--registry", str(three_family_registry()),
        "--timeout-sec", "30",
    )
    assert result.returncode == 0
    session_id = parse_session_id(result.stdout)
    session = read_session(workdir, session_id)
    assert "human_critic" not in session
    assert "paused_sec" not in session["wall_clock"]


def test_hc_flags_rejected_without_mode(run_consilium, workdir, three_family_registry):
    result = run_consilium(
        "convene", "--question", "Вопрос?", "--registry", str(three_family_registry()),
        "--timeout-sec", "30",
    )
    session_id = parse_session_id(result.stdout)
    turn = write_turn(workdir)
    rejected = run_consilium("round", session_id, "--human-turn-file", turn)
    assert rejected.returncode == 2
    assert "не включён" in rejected.stderr


# ---------------------------------------------------------------------------
# E2/E7: ожидание human-хода и подача turn-файла
# ---------------------------------------------------------------------------

def test_hc_awaiting_human_blocks_llm_wave(run_consilium, workdir, three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    waiting = run_consilium("round", session_id, "--digest-file", digest)
    assert waiting.returncode == 0, waiting.stderr
    assert "awaiting_human" in waiting.stdout
    session = read_session(workdir, session_id)
    assert session["human_critic"]["state"] == "awaiting_human"
    assert session["human_critic"]["awaiting_since"]
    # LLM-волна НЕ запущена (E7): attack-записей нет, волна осталась attack.
    records = core.read_transcript(sdir(workdir, session_id))
    assert [r for r in records if r["type"] == "attack"] == []
    assert session["wave"] == "attack"
    assert any("ожидание human-хода" in r["content"] for r in records if r["type"] == "system")


def test_hc_status_exposes_awaiting_and_pause(run_consilium, workdir, three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    run_consilium("round", session_id, "--digest-file", digest)
    status = run_consilium("status", session_id)
    assert status.returncode == 0
    payload = json.loads(status.stdout)
    wall = payload["wall_clock"]
    assert wall["awaiting_human"] is True
    assert wall["paused_total_sec"] >= 0
    assert wall["active_elapsed_sec"] <= wall["real_elapsed_sec"]
    hc = payload["human_critic"]
    assert hc["state"] == "awaiting_human" and hc["withdrawn"] is False


def test_hc_turn_file_requires_attestation(run_consilium, workdir, three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    bad_turn = write_turn(workdir, human_approved=False)
    result = run_consilium("round", session_id, "--digest-file", digest,
                           "--human-turn-file", bad_turn)
    assert result.returncode == 2
    assert "human_approved" in result.stderr
    assert [r for r in core.read_transcript(sdir(workdir, session_id))
            if r.get("author") == H] == []


def test_hc_turn_file_rejected_on_non_attack_wave(run_consilium, workdir,
                                                  three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry())
    # Фаза A (proposal) — human-ход не привязан (E2).
    turn = write_turn(workdir)
    result = run_consilium("round", session_id, "--human-turn-file", turn)
    assert result.returncode == 2
    assert "атакующим волнам" in result.stderr


def test_hc_turn_recorded_and_llm_wave_follows(run_consilium, workdir,
                                                three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    run_consilium("round", session_id, "--digest-file", digest)  # вход в awaiting
    turn = write_turn(workdir)
    result = run_consilium("round", session_id, "--digest-file", digest,
                           "--human-turn-file", turn)
    assert result.returncode == 0, result.stderr
    assert "human-ход зафиксирован" in result.stdout
    session = read_session(workdir, session_id)
    records = core.read_transcript(sdir(workdir, session_id))
    human_turns = [r for r in records if r.get("author") == H]
    assert len(human_turns) == 1
    record = human_turns[0]
    anon_map = json.loads((sdir(workdir, session_id) / "anon_map.json").read_text(encoding="utf-8"))
    assert record["type"] == "attack" and record["wave"] == "attack" and record["round"] == 1
    assert record["anon_id"] == anon_map[H]
    # E7: human-ход ПЕРЕД LLM-атаками в transcript.
    attack_seqs = [r["seq"] for r in records if r["type"] == "attack" and r["author"] != H]
    assert attack_seqs and record["seq"] < min(attack_seqs)
    # LLM-волна сыграна, следующая волна — response; состояние закрыто.
    assert session["wave"] == "response"
    assert session["human_critic"]["state"] == "idle"
    assert session["human_critic"]["awaiting_since"] is None
    assert session["human_critic"]["turns"] == [record["seq"]]
    assert session["invocation_count"] == 6  # 3 proposal + 3 attack
    # E7: human-ход в bundle участников; внутренний id не раскрыт (E9).
    bundles = list((sdir(workdir, session_id) / "bundles").glob("wave-*.bundle.md"))
    bundle_text = bundles[-1].read_text(encoding="utf-8")
    assert anon_map[H] in bundle_text
    assert H not in bundle_text
    # Audit-запись об аттестации (E2).
    assert any("аттестация human_approved" in r["content"]
               for r in records if r["type"] == "system")


def test_hc_second_turn_same_wave_rejected(run_consilium, workdir, three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    run_consilium("round", session_id, "--digest-file", digest)
    run_consilium("round", session_id, "--digest-file", digest,
                  "--human-turn-file", write_turn(workdir))
    # Response-волна: turn-file запрещён привязкой (максимум 1 ход на волну
    # защищён и тем, что волна уже ушла в response).
    again = run_consilium("round", session_id, "--digest-file", digest,
                          "--human-turn-file", write_turn(workdir, name="t2.json"))
    assert again.returncode == 2


# ---------------------------------------------------------------------------
# E6: wait-cap → awaiting_moderator_decision и решения модератора
# ---------------------------------------------------------------------------

def test_hc_wait_cap_requires_moderator_decision(run_consilium, workdir,
                                                 three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry(),
                               "--human-wait-cap-sec", "0")
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    run_consilium("round", session_id, "--digest-file", digest)  # awaiting
    capped = run_consilium("round", session_id, "--digest-file", digest)
    assert capped.returncode == 2
    assert "awaiting_moderator_decision" in capped.stderr
    session = read_session(workdir, session_id)
    assert session["human_critic"]["state"] == "awaiting_moderator_decision"
    # Молчаливого продолжения нет: LLM-волна не запущена.
    assert [r for r in core.read_transcript(sdir(workdir, session_id))
            if r["type"] == "attack"] == []


def test_hc_moderator_decisions_continue_wait_and_skip(run_consilium, workdir,
                                                       three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry(),
                               "--human-wait-cap-sec", "0")
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    run_consilium("round", session_id, "--digest-file", digest)
    run_consilium("round", session_id, "--digest-file", digest)  # → decision state
    continued = run_consilium("round", session_id, "--digest-file", digest,
                              "--human-continue-wait")
    assert continued.returncode == 0, continued.stderr
    assert "продлено" in continued.stdout
    session = read_session(workdir, session_id)
    assert session["human_critic"]["state"] == "awaiting_human"
    assert session["human_critic"]["wait_extended_until"]
    # Одноразовый skip: волна идёт без human-хода.
    skipped = run_consilium("round", session_id, "--digest-file", digest,
                            "--human-skip-wave")
    assert skipped.returncode == 0, skipped.stderr
    session = read_session(workdir, session_id)
    assert session["wave"] == "response"
    assert session["human_critic"]["skipped_waves"] == ["B:1:attack"]
    records = core.read_transcript(sdir(workdir, session_id))
    assert [r for r in records if r.get("author") == H] == []
    # Решения модератора — system-записи в transcript (E6).
    systems = [r["content"] for r in records if r["type"] == "system"]
    assert any("--human-continue-wait" in c for c in systems)
    assert any("--human-skip-wave" in c for c in systems)


def test_hc_moderator_withdraw_closes_mode(run_consilium, workdir, three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    # Без --reason — отказ.
    no_reason = run_consilium("round", session_id, "--digest-file", digest,
                              "--human-withdraw")
    assert no_reason.returncode == 2
    withdrawn = run_consilium("round", session_id, "--digest-file", digest,
                              "--human-withdraw", "--reason", "человек недоступен")
    assert withdrawn.returncode == 0, withdrawn.stderr
    session = read_session(workdir, session_id)
    hc = session["human_critic"]
    assert hc["withdrawn"] is True and hc["withdraw_reason"] == "человек недоступен"
    # Режим свёрнут: волна attack сыграна без ожидания.
    assert session["wave"] == "response"
    # Дальнейшие атакующие волны не ждут human-хода.
    run_consilium("round", session_id, "--digest-file", digest)  # response
    session = read_session(workdir, session_id)
    while session["phase"] == "B":
        result = run_consilium("round", session_id, "--digest-file", digest)
        assert result.returncode == 0, result.stderr
        session = read_session(workdir, session_id)
    assert session["phase"] == "C"
    # Human-флаги после withdraw недопустимы.
    records = core.read_transcript(sdir(workdir, session_id))
    assert any("--human-withdraw" in r["content"] and "человек недоступен" in r["content"]
               for r in records if r["type"] == "system")


# ---------------------------------------------------------------------------
# E9: lint digest по exact protected identifier человека
# ---------------------------------------------------------------------------

def test_hc_digest_lint_rejects_human_identifier(run_consilium, workdir,
                                                 three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    waiting = run_consilium("round", session_id, "--digest-file", digest)
    assert waiting.returncode == 0 and "awaiting_human" in waiting.stdout
    # Дайджест с внутренним id человека отклоняется ДО коммита human-хода
    # (E9 + rev F-004/F-009: валидации раньше двухстороннего коммита).
    bad_digest = write_digest(workdir, text="Дайджест: вклад human-critic учтён.")
    turn = write_turn(workdir)
    result = run_consilium("round", session_id, "--digest-file", bad_digest,
                           "--human-turn-file", turn)
    assert result.returncode == 2
    assert H in result.stderr
    assert [r for r in core.read_transcript(sdir(workdir, session_id))
            if r.get("author") == H] == []  # F-004: ход НЕ зафиксирован
    # Обычные слова human/critic не запрещены (широкий словарь отвергнут).
    ok_digest = write_digest(workdir, text="Дайджест: человек и critic обсуждались.")
    accepted = run_consilium("round", session_id, "--digest-file", ok_digest,
                             "--human-turn-file", turn)
    assert accepted.returncode == 0, accepted.stderr
    assert read_session(workdir, session_id)["wave"] == "response"


def test_hc_mutually_exclusive_flags_rejected(run_consilium, workdir,
                                              three_family_registry):
    """F-012 (rev): взаимоисключающие human-флаги в одной команде — отказ."""
    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    run_consilium("round", session_id, "--digest-file", digest)  # awaiting
    turn = write_turn(workdir)
    result = run_consilium("round", session_id, "--digest-file", digest,
                           "--human-turn-file", turn, "--human-skip-wave")
    assert result.returncode == 2
    assert "взаимоисключающие" in result.stderr
    # keep-raw без turn-file — тоже отказ.
    result = run_consilium("round", session_id, "--digest-file", digest,
                           "--human-keep-raw")
    assert result.returncode == 2
    assert "--human-keep-raw" in result.stderr
    # turn-file без digest — отказ precheck (волна следует за ходом).
    result = run_consilium("round", session_id, "--human-turn-file", turn)
    assert result.returncode == 2
    assert "--digest-file" in result.stderr
    assert [r for r in core.read_transcript(sdir(workdir, session_id))
            if r.get("author") == H] == []


# ---------------------------------------------------------------------------
# E10/E11: наблюдаемость и disclosure в вердикте
# ---------------------------------------------------------------------------

def test_hc_verdict_discloses_human_critic(run_consilium, workdir, three_family_registry):
    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    run_consilium("round", session_id, "--digest-file", digest)
    run_consilium("round", session_id, "--digest-file", digest,
                  "--human-turn-file", write_turn(workdir))
    # Доводим B до стоп-условия (stub: пустой structured → converged/round_limit);
    # на атакующих волнах человек пропускает волну одноразовым skip.
    session = read_session(workdir, session_id)
    while session["phase"] == "B":
        result = run_consilium("round", session_id, "--digest-file", digest)
        if "awaiting" in result.stdout:
            result = run_consilium("round", session_id, "--digest-file", digest,
                                   "--human-skip-wave")
        assert result.returncode == 0, result.stderr
        session = read_session(workdir, session_id)
    synthesis = workdir / "synthesis.md"
    synthesis.write_text(
        "# Синтез\n\n- [E1] разделение ядра (refs: seq:2)\n\n"
        "## Обоснование исключения вкладов\n\nтекст\n", encoding="utf-8")
    result = run_consilium("round", session_id, "--synthesis-file", str(synthesis))
    assert result.returncode == 0, result.stderr
    session = read_session(workdir, session_id)
    while session["phase"] == "D":
        result = run_consilium("round", session_id, "--digest-file", digest)
        if "awaiting" in result.stdout or result.returncode != 0:
            # redteam-волна ждёт human-хода — сворачиваем режим.
            result = run_consilium("round", session_id, "--digest-file", digest,
                                   "--human-withdraw", "--reason", "тест завершает")
        assert result.returncode == 0, result.stderr
        session = read_session(workdir, session_id)
    assert session["phase"] == "E"
    decision = workdir / "decision.md"
    decision.write_text("Решение по seq:2.", encoding="utf-8")
    result = run_consilium("verdict", session_id, "--decision-file", str(decision))
    assert result.returncode == 0, result.stderr
    verdict = (sdir(workdir, session_id) / "verdict.md").read_text(encoding="utf-8")
    assert "human-critic: режим включён" in verdict
    assert "НЕ засчитывается" in verdict


# ---------------------------------------------------------------------------
# Гейт-ревью F-010 (rev): доказательство достижимости human-критики LLM-участниками
# ---------------------------------------------------------------------------

def _drive_to_phase_d(run_consilium, workdir, session_id, digest):
    """B до стоп-условия (human пропускает волны skip'ом) → синтез → фаза D."""
    session = read_session(workdir, session_id)
    while session["phase"] == "B":
        result = run_consilium("round", session_id, "--digest-file", digest)
        if "awaiting" in result.stdout:
            result = run_consilium("round", session_id, "--digest-file", digest,
                                   "--human-skip-wave")
        assert result.returncode == 0, result.stderr
        session = read_session(workdir, session_id)
    synthesis = workdir / "synthesis.md"
    synthesis.write_text(
        "# Синтез\n\n- [E1] разделение ядра (refs: seq:2)\n\n"
        "## Обоснование исключения вкладов\n\nтекст\n", encoding="utf-8")
    result = run_consilium("round", session_id, "--synthesis-file", str(synthesis))
    assert result.returncode == 0, result.stderr
    assert read_session(workdir, session_id)["phase"] == "D"


def test_f010_human_attack_reaches_response_wave_bundle(run_consilium, workdir,
                                                        three_family_registry):
    """(1) Human-ход (attack текущего раунда) ПОПАДАЕТ в bundle response-волны —
    анонимизированный (по anon-id), без внутреннего id human."""
    import consilium

    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    run_consilium("round", session_id, "--digest-file", digest)  # awaiting
    run_consilium("round", session_id, "--digest-file", digest,
                  "--human-turn-file", write_turn(workdir))  # human + LLM attack
    session = read_session(workdir, session_id)
    assert session["wave"] == "response"
    bundle = consilium._prompt_bundle(sdir(workdir, session_id), session, "response")
    anon_map = json.loads((sdir(workdir, session_id) / "anon_map.json").read_text(encoding="utf-8"))
    # Анонимизированный human-ход виден отвечающим по anon-id.
    assert anon_map[H] in bundle
    assert "нет критериев отката изменений" in bundle  # содержание human-критики
    # Внутренний идентификатор human НЕ раскрыт.
    assert H not in bundle


def test_f010_attack_wave_prompt_blind_for_all(run_consilium, workdir,
                                              three_family_registry):
    """(2) Attack-промпт текущего раунда слеп для ВСЕХ: ни human-атаки, ни атаки
    других участников текущего раунда в нём нет (только позиции proposals)."""
    import consilium

    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    run_consilium("round", session_id, "--digest-file", digest)
    run_consilium("round", session_id, "--digest-file", digest,
                  "--human-turn-file", write_turn(workdir))
    session = read_session(workdir, session_id)  # раунд 1, волна response
    bundle = consilium._prompt_bundle(sdir(workdir, session_id), session, "attack")
    assert '"type": "attack"' not in bundle  # атак текущего раунда нет вовсе
    assert "нет критериев отката изменений" not in bundle  # human-атаки в т.ч.
    assert '"type": "proposal"' in bundle  # источник — позиции фазы A
    assert H not in bundle


def test_f010_phase_d_human_redteam_visible_to_moderator(run_consilium, workdir,
                                                         three_family_registry):
    """(3) Фаза D: human redteam_attack фиксируется в transcript (доступен
    модератору для дайджеста) с типом redteam_attack; bundle-рендера для
    redteam/confirmation не существует по дизайну — контракт ядра."""
    import consilium

    session_id = convene_human(run_consilium, three_family_registry())
    to_attack_wave(run_consilium, workdir, session_id)
    digest = write_digest(workdir)
    run_consilium("round", session_id, "--digest-file", digest)
    run_consilium("round", session_id, "--digest-file", digest,
                  "--human-turn-file", write_turn(workdir))
    _drive_to_phase_d(run_consilium, workdir, session_id, digest)
    session = read_session(workdir, session_id)
    assert session["wave"] == "redteam"
    # Ядро ждёт human-ход и на redteam-волне (E7).
    waiting = run_consilium("round", session_id, "--digest-file", digest)
    assert "awaiting_human" in waiting.stdout
    redteam_turn = write_turn(workdir, name="human-redteam.json",
                              content="redteam-критика human: склейка теряет критерии отката")
    result = run_consilium("round", session_id, "--digest-file", digest,
                           "--human-turn-file", redteam_turn)
    assert result.returncode == 0, result.stderr
    records = core.read_transcript(sdir(workdir, session_id))
    human_d = [r for r in records if r.get("author") == H and r.get("phase") == "D"]
    assert len(human_d) == 1
    assert human_d[0]["type"] == "redteam_attack" and human_d[0]["wave"] == "redteam"
    # Модератор читает transcript напрямую — ход доступен для дайджеста.
    assert "склейка теряет критерии отката" in human_d[0]["content"]
    # Контракт дизайна: bundle-источника для redteam/confirmation нет (фаза D —
    # digest + synthesis для всех, human и LLM red-teamer равноправны).
    session = read_session(workdir, session_id)
    assert consilium._prompt_bundle(sdir(workdir, session_id), session, "redteam") == ""
    assert consilium._prompt_bundle(sdir(workdir, session_id), session, "confirmation") == ""
