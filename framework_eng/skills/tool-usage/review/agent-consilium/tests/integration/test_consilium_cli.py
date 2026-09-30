"""Integration-слой (AC-20): ядро consilium.py + stub-адаптеры.

Взаимодействие команд на путях завершения, включая аварийные.
Trace: IT-01..IT-15. Реальные CLI не вызываются (stub на PATH).
"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

import pytest

import consilium_core as core
from consilium_cleanup_assertions import (
    assert_cleanup_checkpoint,
    review_root_has_only_closed_tombstones,
)
from invocation_lock import INVOCATION_LOCK_NAME

SCRIPTS_DIR = Path(__file__).resolve().parent.parent.parent / "scripts"

DIGEST_TEXT = "Дайджест модератора: живые модели M1, M2, M3; открытые разногласия по элементам E1, E2."


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------

def parse_session_id(stdout: str) -> str:
    match = re.search(r"session_id:\s*(\S+)", stdout)
    assert match, f"session_id не найден в выводе convene:\n{stdout}"
    return match.group(1)


def dir_empty(path: Path) -> bool:
    """Checkpoint-хелпер: каталог отсутствует или пуст."""
    return not path.exists() or list(path.iterdir()) == []


def session_dir(workdir: Path, session_id: str) -> Path:
    return workdir / ".consilium-sessions" / session_id


def read_session(workdir: Path, session_id: str) -> dict:
    return json.loads((session_dir(workdir, session_id) / "session.json").read_text(encoding="utf-8"))


def read_transcript(workdir: Path, session_id: str) -> list[dict]:
    return core.read_transcript(session_dir(workdir, session_id))


def write_digest(workdir: Path, name: str, text: str = DIGEST_TEXT) -> str:
    path = workdir / name
    path.write_text(text, encoding="utf-8")
    return str(path)


def convene(run_consilium, registry, *extra):
    result = run_consilium(
        "convene", "--question", "Как разделить ядро платформы и доменные модули?",
        "--registry", str(registry), "--timeout-sec", "30", *extra,
    )
    assert result.returncode == 0, f"convene failed:\n{result.stdout}\n{result.stderr}"
    return parse_session_id(result.stdout)


def drive_to_phase_e(run_consilium, workdir, session_id):
    """Доводит сессию до фазы E: B до стоп-условия (stub пустой structured) → C → D."""
    digest = write_digest(workdir, "digest.md")
    while read_session(workdir, session_id)["phase"] == "B":
        result = run_consilium("round", session_id, "--digest-file", digest)
        assert result.returncode == 0, f"B wave failed:\n{result.stdout}\n{result.stderr}"
    synthesis = workdir / "synthesis.md"
    synthesis.write_text(
        "# Синтез\n\n"
        "- [E1] разделение ядра и доменов (refs: seq:2)\n"
        "- [E2] транспорт через события (refs: seq:3)\n\n"
        "## Обоснование исключения вкладов\n\n"
        "Вклад невошедших живых моделей перекрыт элементами E1 и E2.\n",
        encoding="utf-8",
    )
    result = run_consilium("round", session_id, "--synthesis-file", str(synthesis))
    assert result.returncode == 0, f"synthesis failed:\n{result.stdout}\n{result.stderr}"
    while read_session(workdir, session_id)["phase"] == "D":
        result = run_consilium("round", session_id, "--digest-file", digest)
        assert result.returncode == 0, f"D wave failed:\n{result.stdout}\n{result.stderr}"
    assert read_session(workdir, session_id)["phase"] == "E"


def do_close(run_consilium, workdir, session_id, registry):
    result = run_consilium("close", session_id, "--registry", str(registry))
    assert result.returncode == 0, f"close failed:\n{result.stdout}\n{result.stderr}"
    return result


def test_cleanup_checkpoint_accepts_only_canonical_lock_tombstones(tmp_path):
    review_root = tmp_path / ".review-sandboxes"
    tombstone = review_root / "closed-review"
    tombstone.mkdir(parents=True)
    (tombstone / INVOCATION_LOCK_NAME).write_text("", encoding="utf-8")
    assert review_root_has_only_closed_tombstones(review_root)


@pytest.mark.parametrize("kind", ["payload", "directory", "symlink"])
def test_cleanup_checkpoint_rejects_live_or_noncanonical_sandbox(tmp_path, kind):
    """Any payload, unexpected directory or symlink remains blocking."""
    review_root = tmp_path / ".review-sandboxes"
    review_root.mkdir()
    child = review_root / "not-closed"
    if kind == "symlink":
        outside = tmp_path / "outside"
        outside.mkdir()
        child.symlink_to(outside, target_is_directory=True)
    else:
        child.mkdir()
        if kind == "payload":
            (child / "review.json").write_text("{}", encoding="utf-8")
    assert not review_root_has_only_closed_tombstones(review_root)


# ---------------------------------------------------------------------------
# IT-01: happy path (FR-01/NFR-01, AC-13/20)
# ---------------------------------------------------------------------------

def test_it01_happy_path_full_protocol(run_consilium, stub_env, three_family_registry):
    """IT-01: convene → A → B×2 → C → D → E → close; счётчик вызовов в 16–25 (NFR-01)."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)

    result = run_consilium("round", session_id)  # фаза A: proposal (без дайджеста)
    assert result.returncode == 0, f"phase A failed:\n{result.stdout}\n{result.stderr}"
    assert read_session(workdir, session_id)["phase"] == "B"

    drive_to_phase_e(run_consilium, workdir, session_id)

    status = run_consilium("status", session_id)
    assert status.returncode == 0
    status_json = json.loads(status.stdout[status.stdout.index("{"):])
    assert status_json["phase"] == "E"
    assert 16 <= status_json["invocation_count"] <= 25  # NFR-01 (3 + 12 + 4 = 19)

    decision = workdir / "decision.md"
    decision.write_text("Решение: синтез по seq:2. Traceability сохранена.", encoding="utf-8")
    result = run_consilium("verdict", session_id, "--decision-file", str(decision))
    assert result.returncode == 0, f"verdict failed:\n{result.stdout}\n{result.stderr}"
    verdict_path = session_dir(workdir, session_id) / "verdict.md"
    assert verdict_path.exists()

    do_close(run_consilium, workdir, session_id, registry)
    assert not session_dir(workdir, session_id).exists()


# ---------------------------------------------------------------------------
# IT-02/IT-03/IT-04: аварийные ветки (FR-10/FR-11, AC-10/20)
# ---------------------------------------------------------------------------

def test_it02_timeout_unresponsive_close_parity(run_consilium, stub_env, set_stub_mode, three_family_registry):
    """IT-02: таймаут адаптера в раунде B → unresponsive → продолжение → close.
    Парность convene↔close и start↔close каждого участника; обе директории пусты."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry, "--timeout-sec", "2")

    result = run_consilium("round", session_id)  # фаза A в ok-режиме
    assert result.returncode == 0

    set_stub_mode("timeout:kimi")
    digest = write_digest(workdir, "digest.md")
    result = run_consilium("round", session_id, "--digest-file", digest)  # attack: kimi молчит
    assert result.returncode == 0, f"attack with timeout failed:\n{result.stdout}\n{result.stderr}"
    session = read_session(workdir, session_id)
    kimi = next(p for p in session["participants"] if p["id"] == "kimi-k2")
    assert kimi["state"] == "unresponsive"

    set_stub_mode("ok")
    drive_to_phase_e(run_consilium, workdir, session_id)
    decision = workdir / "decision.md"
    decision.write_text("Решение по seq:2.", encoding="utf-8")
    result = run_consilium("verdict", session_id, "--decision-file", str(decision))
    assert result.returncode == 0
    verdict_text = (session_dir(workdir, session_id) / "verdict.md").read_text(encoding="utf-8")
    assert "unresponsive" in verdict_text  # инцидент зафиксирован в вердикте (FR-11)

    result = do_close(run_consilium, workdir, session_id, registry)
    # двойной checkpoint (FR-10)
    assert_cleanup_checkpoint(workdir)


def test_it03_error_retry_unresponsive_close_on_abort(run_consilium, stub_env, set_stub_mode, three_family_registry):
    """IT-03: ошибка адаптера → retry → unresponsive → close на ветке отказа; парность cleanup."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry, "--timeout-sec", "2")

    result = run_consilium("round", session_id)  # фаза A ok
    assert result.returncode == 0

    set_stub_mode("error:kimi")
    digest = write_digest(workdir, "digest.md")
    result = run_consilium("round", session_id, "--digest-file", digest)
    assert result.returncode == 0, f"attack with error failed:\n{result.stdout}\n{result.stderr}"
    session = read_session(workdir, session_id)
    kimi = next(p for p in session["participants"] if p["id"] == "kimi-k2")
    assert kimi["state"] == "unresponsive"
    assert kimi["retries"] >= 1  # ровно один retry зафиксирован
    transcript = read_transcript(workdir, session_id)
    assert any(r["type"] == "error" for r in transcript)  # факт ошибки в transcript

    # отказ от консилиума: close без вердикта — парность обязана сохраниться
    result = do_close(run_consilium, workdir, session_id, registry)
    assert_cleanup_checkpoint(workdir)


def test_it04_mass_unresponsive_quorum_fail_closed(run_consilium, stub_env, set_stub_mode, three_family_registry):
    """IT-04: mass-unresponsive → нарушение кворума → сессия завершается без вердикта,
    причина зафиксирована, парный close всех участников."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry, "--timeout-sec", "2")
    result = run_consilium("round", session_id)  # фаза A ok
    assert result.returncode == 0

    set_stub_mode("timeout")  # молчат все
    digest = write_digest(workdir, "digest.md")
    result = run_consilium("round", session_id, "--digest-file", digest)
    assert result.returncode != 0  # fail-closed
    assert "кворум" in (result.stdout + result.stderr).lower()

    # вердикт невозможен; парный close выполнен; обе директории пусты
    assert not session_dir(workdir, session_id).exists()
    assert_cleanup_checkpoint(workdir)
    observations = (workdir / ".consilium-track-record" / "observations.jsonl")
    assert observations.exists()
    assert any("terminated" in line for line in observations.read_text(encoding="utf-8").splitlines())


# ---------------------------------------------------------------------------
# IT-05/IT-06/IT-07: doctor и кворум (FR-06, AC-05/07/20)
# ---------------------------------------------------------------------------

def test_it05_doctor_excludes_unavailable_fail_closed(run_consilium, stub_env, registry_factory, participant_entry):
    """IT-05: недоступный CLI исключается с явной записью; кворум после исключений — fail-closed."""
    workdir = stub_env["cwd"]
    registry = registry_factory([
        participant_entry("claude-opus", "claude"),
        participant_entry("codex-gpt", "codex"),
        dict(participant_entry("ghost", "ghost-family"), cli="definitely-missing-cli-xyz"),
    ])
    result = run_consilium("doctor", "--registry", str(registry), "--json")
    assert result.returncode == 0
    report = json.loads(result.stdout[result.stdout.index("{"):])
    excluded = {e["id"]: e for e in report["excluded"]}
    assert "ghost" in excluded
    assert excluded["ghost"]["reason"]  # явная запись причины
    assert report["quorum"]["ok"] is True  # claude+codex — кворум есть

    # кворум нарушен после исключений → doctor и convene отказывают
    registry2 = registry_factory([
        participant_entry("claude-opus", "claude"),
        dict(participant_entry("ghost1", "ghost-a"), cli="definitely-missing-cli-1"),
        dict(participant_entry("ghost2", "ghost-b"), cli="definitely-missing-cli-2"),
    ])
    result = run_consilium("doctor", "--registry", str(registry2), "--json")
    assert result.returncode != 0
    result = run_consilium("convene", "--question", "Q", "--registry", str(registry2))
    assert result.returncode != 0  # fail-closed: сессия не создаётся
    assert dir_empty(workdir / ".consilium-sessions")


def test_it06_homogeneity_warning_and_family_quorum(run_consilium, stub_env, registry_factory, participant_entry):
    """IT-06: >1 участника одного family → предупреждение; кворум >=2 family обязателен."""
    workdir = stub_env["cwd"]
    registry = registry_factory([
        participant_entry("claude-a", "claude"),
        participant_entry("claude-b", "claude"),
        participant_entry("codex-gpt", "codex"),
    ])
    result = run_consilium("doctor", "--registry", str(registry), "--json")
    assert result.returncode == 0
    report = json.loads(result.stdout[result.stdout.index("{"):])
    assert report["quorum"]["homogeneity_warning"] is True

    session_id = convene(run_consilium, registry)
    session = read_session(workdir, session_id)
    assert session["quorum"]["homogeneity_warning"] is True
    do_close(run_consilium, workdir, session_id, registry)

    # одно family → кворума нет, convene отказывает
    registry_one = registry_factory([
        participant_entry("claude-a", "claude"),
        participant_entry("claude-b", "claude"),
    ])
    result = run_consilium("convene", "--question", "Q", "--registry", str(registry_one))
    assert result.returncode != 0


def test_it07_convene_without_members_takes_enabled(run_consilium, stub_env, registry_factory, participant_entry):
    """IT-07: convene без --members берёт всех enabled; disabled пропускаются."""
    workdir = stub_env["cwd"]
    registry = registry_factory([
        participant_entry("claude-opus", "claude"),
        participant_entry("codex-gpt", "codex"),
        participant_entry("kimi-k2", "kimi", enabled=False),
    ])
    session_id = convene(run_consilium, registry)
    session = read_session(workdir, session_id)
    ids = [p["id"] for p in session["participants"]]
    assert ids == ["claude-opus", "codex-gpt"]
    do_close(run_consilium, workdir, session_id, registry)


# ---------------------------------------------------------------------------
# IT-08/IT-09: анонимизация и дайджест-гейт (FR-04/NFR-02, AC-14/20)
# ---------------------------------------------------------------------------

def test_it08_anonymization_end_to_end(run_consilium, stub_env, three_family_registry):
    """IT-08: bundle волны B без реальных id; transcript хранит author; lint дайджеста отклоняет реальный id."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    result = run_consilium("round", session_id)  # A
    assert result.returncode == 0
    digest = write_digest(workdir, "digest.md")
    result = run_consilium("round", session_id, "--digest-file", digest)  # attack
    assert result.returncode == 0

    bundles_dir = session_dir(workdir, session_id) / "bundles"
    bundles = list(bundles_dir.glob("*.bundle.md"))
    assert bundles, "bundle волны B не создан"
    bundle_text = bundles[-1].read_text(encoding="utf-8")
    for real_id in ("claude-opus", "codex-gpt", "kimi-k2"):
        assert real_id not in bundle_text
    transcript = read_transcript(workdir, session_id)
    attack_authors = {r["author"] for r in transcript if r["type"] == "attack"}
    assert attack_authors == {"claude-opus", "codex-gpt", "kimi-k2"}  # авторство сохранено

    # дайджест с реальным id — отказ fail-closed
    bad_digest = write_digest(workdir, "bad-digest.md", "Позиция claude-opus сильнее остальных.")
    result = run_consilium("round", session_id, "--digest-file", bad_digest)
    assert result.returncode != 0
    do_close(run_consilium, workdir, session_id, registry)


def test_it09_digest_gate_and_context_budget(run_consilium, stub_env, three_family_registry):
    """IT-09: волна без --digest-file (кроме первой) — отказ; дайджест сверх context_budget — отказ с лимитом."""
    workdir = stub_env["cwd"]
    registry = three_family_registry(**{"claude-opus": {"context_budget": 500}})
    session_id = convene(run_consilium, registry)
    result = run_consilium("round", session_id)  # A — без дайджеста, разрешено
    assert result.returncode == 0

    result = run_consilium("round", session_id)  # attack без дайджеста — отказ
    assert result.returncode != 0
    assert "digest" in (result.stdout + result.stderr).lower()

    big_digest = write_digest(workdir, "big-digest.md", "x" * 4000)  # ~1000 токенов > 10
    result = run_consilium("round", session_id, "--digest-file", big_digest)
    assert result.returncode != 0
    assert "context_budget" in (result.stdout + result.stderr) or "10" in (result.stdout + result.stderr)
    do_close(run_consilium, workdir, session_id, registry)


# ---------------------------------------------------------------------------
# IT-10: kill-защита критерия (FR-03, AC-18/20)
# ---------------------------------------------------------------------------

def test_it10_kill_deterministic_guard_and_final_statement(run_consilium, stub_env, three_family_registry):
    """IT-10: ядро отклоняет --kill не по кандидату; принятый kill → kill_decision +
    финальное заявление дословно в minority report."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    result = run_consilium("round", session_id)  # A
    assert result.returncode == 0
    digest = write_digest(workdir, "digest.md")
    result = run_consilium("round", session_id, "--digest-file", digest)  # attack r1
    assert result.returncode == 0

    # детерминированный сценарий: codex критикует kimi:E1, kimi соглашается (upheld против kimi)
    sdir = session_dir(workdir, session_id)
    session = read_session(workdir, session_id)
    core.append_record(sdir, {
        "session_id": session_id, "phase": "B", "round": 1, "wave": "attack",
        "author": "codex-gpt", "type": "attack", "refs": [2],
        "content": "Атака на элемент E1 модели kimi.",
        "structured": {"elements": [], "new_findings": [],
                       "position_changes": [{"element": "kimi-k2:E1", "action": "disagree", "refs": [2]}],
                       "borrowed": []},
    })
    result = run_consilium("round", session_id, "--digest-file", digest)  # response r1
    assert result.returncode == 0
    core.append_record(sdir, {
        "session_id": session_id, "phase": "B", "round": 1, "wave": "response",
        "author": "kimi-k2", "type": "response", "refs": [5],
        "content": "Принимаю критику E1.",
        "structured": {"elements": [], "new_findings": [],
                       "position_changes": [{"element": "E1", "action": "agree", "refs": [5]}],
                       "borrowed": []},
    })
    # пересчёт рекомендации: следующий kill-запрос обязан принять только kimi-k2
    result = run_consilium("round", session_id, "--kill", "codex-gpt", "--digest-file", digest)
    assert result.returncode != 0  # kill не по детерминированному кандидату — отказ

    reply = workdir / "final.md"
    reply.write_text("ФИНАЛЬНОЕ ЗАЯВЛЕНИЕ ИСКЛЮЧЁННОЙ МОДЕЛИ: E1 защищаю до конца.", encoding="utf-8")
    os.environ["STUB_REPLY_FILE"] = str(reply)
    try:
        result = run_consilium("round", session_id, "--kill", "kimi-k2", "--digest-file", digest)
        assert result.returncode == 0, f"kill failed:\n{result.stdout}\n{result.stderr}"
    finally:
        os.environ.pop("STUB_REPLY_FILE", None)
    session = read_session(workdir, session_id)
    kimi = next(p for p in session["participants"] if p["id"] == "kimi-k2")
    assert kimi["state"] == "killed"
    assert session["kill_log"], "kill_decision не зафиксирован"
    kill_entry = session["kill_log"][0]
    assert kill_entry["participant_id"] == "kimi-k2"
    assert "survived" in kill_entry["metrics"]

    # доводим до вердикта: alive=2 (claude, codex)
    drive_to_phase_e(run_consilium, workdir, session_id)
    decision = workdir / "decision.md"
    decision.write_text("Решение по seq:2.", encoding="utf-8")
    result = run_consilium("verdict", session_id, "--decision-file", str(decision))
    assert result.returncode == 0
    verdict_text = (session_dir(workdir, session_id) / "verdict.md").read_text(encoding="utf-8")
    assert "ФИНАЛЬНОЕ ЗАЯВЛЕНИЕ ИСКЛЮЧЁННОЙ МОДЕЛИ: E1 защищаю до конца." in verdict_text
    assert "kimi-k2" in verdict_text  # kill-log в вердикте
    do_close(run_consilium, workdir, session_id, registry)


# ---------------------------------------------------------------------------
# IT-11/IT-12/IT-13: track record, cleanup, модератор (FR-05/FR-09/FR-10)
# ---------------------------------------------------------------------------

def test_it11_track_record_durability(run_consilium, stub_env, three_family_registry):
    """IT-11: после close — observations (moderator_id, moderator_is_participant),
    регенерация strengths.json; track record переживает close."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    result = run_consilium("round", session_id)
    assert result.returncode == 0
    drive_to_phase_e(run_consilium, workdir, session_id)
    decision = workdir / "decision.md"
    decision.write_text("Решение по seq:2.", encoding="utf-8")
    assert run_consilium("verdict", session_id, "--decision-file", str(decision)).returncode == 0
    do_close(run_consilium, workdir, session_id, registry)

    track_dir = workdir / ".consilium-track-record"
    assert track_dir.is_dir()  # durable-хранилище пережило close
    observations = [
        json.loads(line)
        for line in (track_dir / "observations.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    assert observations
    for obs in observations:
        assert obs["moderator_id"] == "primary"
        assert obs["moderator_is_participant"] is False
        assert obs["role"] in core.ROLE_CATALOG
    strengths = json.loads((track_dir / "strengths.json").read_text(encoding="utf-8"))
    assert strengths  # проекция регенерирована
    config = json.loads((track_dir / "config.json").read_text(encoding="utf-8"))
    assert config["consiliums_completed"] == 1


def test_it12_double_cleanup_checkpoint(run_consilium, stub_env, three_family_registry):
    """IT-12: после close обе директории пусты, track record нетронут, cleanup status записан."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    result = run_consilium("round", session_id)
    assert result.returncode == 0
    result = do_close(run_consilium, workdir, session_id, registry)
    assert "closed" in result.stdout  # cleanup status каждого участника
    assert_cleanup_checkpoint(workdir)
    assert (workdir / ".consilium-track-record").is_dir()  # из checkpoint исключён


def test_it13_caller_recorded_as_moderator(run_consilium, stub_env, three_family_registry):
    """IT-13 (FR-05, правило композиции): id вызывающего (--caller) фиксируется как
    moderator_id в session.json и переносится в observations (сегрегация tainted)."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry, "--caller", "claude-opus")
    session = read_session(workdir, session_id)
    assert session["moderator_id"] == "claude-opus"
    do_close(run_consilium, workdir, session_id, registry)
    observations = [
        json.loads(line)
        for line in (workdir / ".consilium-track-record" / "observations.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    assert observations
    for obs in observations:
        assert obs["moderator_id"] == "claude-opus"
        assert obs["moderator_is_participant"] is True  # вызывающий совпал с участником


def test_it13b_default_caller_and_all_enabled_composition(run_consilium, stub_env, three_family_registry):
    """IT-13b: без --caller moderator_id='primary' (moderator_is_participant=False);
    состав по умолчанию = все enabled (правило композиции FR-05)."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    session = read_session(workdir, session_id)
    assert session["moderator_id"] == "primary"
    ids = [p["id"] for p in session["participants"]]
    assert ids == ["claude-opus", "codex-gpt", "kimi-k2"]  # все enabled включая семейство вызывающего
    do_close(run_consilium, workdir, session_id, registry)
    observations = [
        json.loads(line)
        for line in (workdir / ".consilium-track-record" / "observations.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    assert observations
    for obs in observations:
        assert obs["moderator_id"] == "primary"
        assert obs["moderator_is_participant"] is False


def test_it13c_members_flag_rejected(run_consilium, stub_env, three_family_registry):
    """IT-13c (FR-05): флага --members не существует — попытка передать → отказ парсера."""
    registry = three_family_registry()
    result = run_consilium(
        "convene", "--question", "Q", "--registry", str(registry),
        "--members", "claude-opus", "codex-gpt",
    )
    assert result.returncode != 0  # argparse: unrecognized arguments (fail-closed)


def test_it13d_caller_matching_marks_participant(run_consilium, stub_env, three_family_registry):
    """IT-13d (FR-05): caller = id участника реестра → участник помечен caller_model;
    moderator_is_participant=True в observations track record."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry, "--caller", "kimi-k2")
    session = read_session(workdir, session_id)
    kimi = next(p for p in session["participants"] if p["id"] == "kimi-k2")
    assert kimi["caller_model"] is True
    assert all(
        not p["caller_model"] for p in session["participants"] if p["id"] != "kimi-k2"
    )
    do_close(run_consilium, workdir, session_id, registry)
    observations = [
        json.loads(line)
        for line in (workdir / ".consilium-track-record" / "observations.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    assert observations
    for obs in observations:
        assert obs["moderator_id"] == "kimi-k2"
        assert obs["moderator_is_participant"] is True


def test_it13e_unknown_caller_warns_and_continues(run_consilium, stub_env, three_family_registry):
    """IT-13e (FR-05): caller вне реестра — warning в stdout и transcript
    («участвует только как модератор»), convene продолжается, НЕ fail-closed."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    result = run_consilium(
        "convene", "--question", "Q", "--registry", str(registry),
        "--caller", "external-agent-x", "--timeout-sec", "30",
    )
    assert result.returncode == 0, f"convene failed:\n{result.stdout}\n{result.stderr}"
    assert "ПРЕДУПРЕЖДЕНИЕ" in result.stdout
    session_id = parse_session_id(result.stdout)
    transcript = read_transcript(workdir, session_id)
    assert any(
        r["type"] == "system" and "не представлен в реестре" in r["content"]
        for r in transcript
    )
    session = read_session(workdir, session_id)
    assert session["moderator_id"] == "external-agent-x"
    assert all(not p["caller_model"] for p in session["participants"])
    do_close(run_consilium, workdir, session_id, registry)
    observations = [
        json.loads(line)
        for line in (workdir / ".consilium-track-record" / "observations.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    assert observations
    for obs in observations:
        assert obs["moderator_id"] == "external-agent-x"
        assert obs["moderator_is_participant"] is False


# ---------------------------------------------------------------------------
# IT-14/IT-15: minority report дословно, неголосующий модератор (FR-05, AC-04)
# ---------------------------------------------------------------------------

def test_it14_minority_report_verbatim(run_consilium, stub_env, three_family_registry):
    """IT-14: вердикт содержит текст несогласного байт-в-байт из transcript;
    отредактированный модератором вариант конструктивно не попадает в вердикт."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0
    digest = write_digest(workdir, "digest.md")
    for _ in range(2):
        assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0
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
    assert run_consilium("round", session_id, "--digest-file", digest).returncode == 0  # redteam

    dissent = (
        "ПОЗИЦИЯ НЕСОГЛАСНОГО: синтез исказил мой элемент E1, я его не признаю.\n\n"
        "```consilium-structured\n"
        '{"elements": ["E1"], "new_findings": [], "position_changes": '
        '[{"element": "synthesis", "action": "disagree", "refs": [2]}], "borrowed": [], '
        '"risk_checklist_responses": [{"item_id": "overengineering", "verdict": "clear"}, '
        '{"item_id": "bounded-context-erosion", "verdict": "clear"}]}\n```'
    )
    reply = workdir / "dissent.md"
    reply.write_text(dissent, encoding="utf-8")
    os.environ["STUB_REPLY_FILE"] = str(reply)
    try:
        result = run_consilium("round", session_id, "--digest-file", digest)  # confirmation
        assert result.returncode == 0
    finally:
        os.environ.pop("STUB_REPLY_FILE", None)

    decision = workdir / "decision.md"
    decision.write_text(
        "Решение по seq:2.\n\nMinority: ОТРЕДАКТИРОВАННАЯ ВЕРСИЯ НЕСОГЛАСИЯ МОДЕРАТОРОМ.",
        encoding="utf-8",
    )
    result = run_consilium("verdict", session_id, "--decision-file", str(decision))
    assert result.returncode == 0
    verdict_text = (session_dir(workdir, session_id) / "verdict.md").read_text(encoding="utf-8")
    # minority-секция собирается ТОЛЬКО из transcript: дословный текст несогласного есть,
    # отредактированный модератором вариант в minority-секцию не попадает (FR-05)
    minority_section = verdict_text.split("## Minority report", 1)[1].split("\n## ", 1)[0]
    assert "ПОЗИЦИЯ НЕСОГЛАСНОГО: синтез исказил мой элемент E1, я его не признаю." in minority_section
    assert "ОТРЕДАКТИРОВАННАЯ ВЕРСИЯ НЕСОГЛАСИЯ МОДЕРАТОРОМ" not in minority_section
    do_close(run_consilium, workdir, session_id, registry)


def test_it15_moderator_never_votes(run_consilium, stub_env, three_family_registry, tmp_path):
    """IT-15: в transcript фаз A/B/D нет ходов участника от author=moderator;
    попытка записать такой ход — отказ ядра."""
    # уровень ядра: запрет исполняемо
    sdir = tmp_path / "core-sess"
    sdir.mkdir()
    with pytest.raises(core.ProtocolError):
        core.append_record(sdir, {"author": "moderator", "type": "attack", "content": "моё мнение"})
    with pytest.raises(core.ProtocolError):
        core.append_record(sdir, {"author": "moderator", "type": "proposal", "content": "моя модель"})

    # уровень протокола: полный прогон, moderator пишет только digest/synthesis/verdict
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0
    drive_to_phase_e(run_consilium, workdir, session_id)
    decision = workdir / "decision.md"
    decision.write_text("Решение по seq:2.", encoding="utf-8")
    assert run_consilium("verdict", session_id, "--decision-file", str(decision)).returncode == 0
    transcript = read_transcript(workdir, session_id)
    participant_types = {"proposal", "attack", "response", "redteam_attack", "confirmation"}
    for record in transcript:
        if record["type"] in participant_types:
            assert record["author"] != "moderator"
            assert record["author"] in {"claude-opus", "codex-gpt", "kimi-k2"}
    do_close(run_consilium, workdir, session_id, registry)


def test_it16_status_aggregates_adapter_observability(run_consilium, stub_env, three_family_registry):
    """IT-16 (NFR-04/F-08): status агрегирует heartbeat/phase/progress counters
    адаптеров участников."""
    workdir = stub_env["cwd"]
    registry = three_family_registry()
    session_id = convene(run_consilium, registry)
    assert run_consilium("round", session_id).returncode == 0  # фаза A: участники стартовали
    result = run_consilium("status", session_id)
    assert result.returncode == 0
    payload = json.loads(result.stdout[result.stdout.index("{"):])
    for participant in payload["participants"]:
        adapter = participant.get("adapter")
        assert adapter, f"{participant['id']}: нет adapter observability в status"
        assert adapter["review_id"]
        assert adapter["phase"] in {"finished", "reading", "starting", "running"}
        assert adapter["heartbeat"], f"{participant['id']}: нет heartbeat"
        assert "raw_events" in adapter["progress"]
    do_close(run_consilium, workdir, session_id, registry)
