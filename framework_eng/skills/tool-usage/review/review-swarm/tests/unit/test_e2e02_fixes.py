"""Unit-слой rework E2E-02 (RVSW-01, blocking completion review): фиксы
F-002..F-009, F-013..F-015 — чистые предикаты и in-process проверки cmd-слоя.

- F-002: пустые диспозиции `{}` валидны ровно при 0 находках сессии;
- F-004: таймаут волны выводится из session["timeout_sec"] (волна = T+240);
- F-005: review_id участников сохраняется инкрементально при исключении волны;
- F-007: conditional_accept — НЕ положительный исход gate (auto-approve нет);
- F-008: _budget_gate резервирует реальное число волн команды (чанки cap);
- F-009: zip-сопоставление результатов волны — strict fail-closed;
- F-014: код gate-протокола — в карте критичности (forced-линза);
- F-015: parse_criticality_map поддерживает `->` и `→`, fail-closed на
  нераспознанный разделитель.
"""
from __future__ import annotations

import math
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

import pytest

import adapter_contract as ac
import swarm
import swarm_core as core


# ---------- Фабрика лёгкой сессии на диске (in-process cmd_attack) ----------

def _light_session(tmp_path, timeout_sec=60):
    workdir = tmp_path
    (workdir / "src").mkdir(exist_ok=True)
    (workdir / "src" / "a.py").write_text(
        "\n".join(f"# строка {i}" for i in range(1, 41)) + "\n", encoding="utf-8")
    sid = "swarm-test-e2e02"
    sdir = workdir / ".swarm-sessions" / sid
    (sdir / "prompts").mkdir(parents=True)
    (sdir / "review.diff").write_text(
        "diff --git a/src/a.py b/src/a.py\n--- a/src/a.py\n+++ b/src/a.py\n"
        "@@ -1,1 +1,2 @@\n+# изменение\n", encoding="utf-8")
    session = {
        "session_id": sid,
        "state": swarm.STATE_CONVENED,
        "tier": swarm.TIER_LIGHT,
        "registry": "adapters.yaml",
        "orchestrator_id": "claude-opus",
        "caller_family": "claude",
        "created_at": swarm.utc_now(),
        "timeout_sec": timeout_sec,
        "silence_threshold_sec": 120,
        "paths": ["src/a.py"],
        "diff_source": "changes.diff",
        "checked_set": {"src/a.py": 40},
        "criticality": {"hits": [], "forced_lenses": {}},
        "gate": None,
        "calibration_run": False,
        "quota_mode": "blind",
        "quota_fallback_reason": None,
        "participants": [{
            "id": "codex-gpt", "family": "codex", "state": "active",
            "lens": swarm.LIGHT_REVIEWER_ROLE, "lens_forced": False,
            "gate_pass": False, "review_id": None, "adapter_session_id": None,
            "invocations": 0, "retries": 0,
        }],
        "anon_map": {"codex-gpt": "P1"},
        "invocation_count": 0,
        "wall_clock": {"started_at": swarm.utc_now(),
                       "budget_sec": 3600, "warn_at_sec": 2700},
        "findings": [], "rejected": [], "routed_findings": [],
        "dedup": None, "threads": {}, "arbitration": {}, "degraded": None,
        "cleanup": {"session_closed": False, "participants_closed": {}},
    }
    swarm.save_session(sdir, session)
    return sdir, session


def _stub_attack_env(monkeypatch, tmp_path, start_result):
    registry = {"participants": [{
        "id": "codex-gpt", "family": "codex", "adapter": "adapters/codex.py",
        "model": "stub-model",
    }]}
    monkeypatch.setattr(swarm, "load_registry_checked", lambda path: registry)
    monkeypatch.setattr(ac, "start_participant",
                        lambda *a, **kw: start_result)
    monkeypatch.setattr(ac, "materialize_diff", lambda *a, **kw: None)


# ---------- F-002: пустые диспозиции при 0 находках ----------

def test_f002_empty_dispositions_allowed_without_findings():
    """При нуле находок сессии единственная валидная диспозиция — {}."""
    assert swarm.validate_dispositions({}, []) == {}


def test_f002_empty_dispositions_rejected_with_findings():
    """Непустой пул находок требует непустых диспозиций (fail-closed)."""
    with pytest.raises(ValueError, match="непустой"):
        swarm.validate_dispositions({}, ["F-001"])


def test_f002_dispositions_still_validated_with_findings():
    with pytest.raises(ValueError, match="F-099"):
        swarm.validate_dispositions({"F-099": "agree"}, [])
    assert swarm.validate_dispositions({"F-001": "agree"}, ["F-001"]) == {"F-001": "agree"}


# ---------- F-004: таймаут волны из timeout_sec сессии ----------

def test_f004_wave_timeout_derived_from_session_timeout():
    """Волна = T + 240 (как в консилиуме), не жёсткий DEFAULT: при
    --timeout-sec > 900 волна не падает раньше адаптера."""
    assert (swarm.session_wave_timeout_sec({"timeout_sec": 1200})
            == 1200 + 2 * ac.WAVE_GRACE_SEC)
    assert (swarm.session_wave_timeout_sec({"timeout_sec": 900})
            == ac.WAVE_TIMEOUT_SEC)


def test_f004_attack_wave_uses_session_timeout(tmp_path, monkeypatch):
    """cmd_attack передаёт wave_timeout_sec из timeout_sec сессии в волну."""
    monkeypatch.chdir(tmp_path)
    sdir, session = _light_session(tmp_path, timeout_sec=60)
    start_result = ac.InvocationResult(
        ok=True, kind="ok", review_id="rev-1", session_id="as-1",
        text='```swarm-structured\n{"findings": []}\n```')
    _stub_attack_env(monkeypatch, tmp_path, start_result)
    captured = {}

    def fake_wave(tasks, parallel_cap=ac.PARALLEL_CAP, wave_timeout_sec=None):
        captured["wave_timeout_sec"] = wave_timeout_sec
        return [task() for task in tasks]

    monkeypatch.setattr(swarm, "run_capped_wave", fake_wave)
    args = SimpleNamespace(session_id=session["session_id"], registry=None)
    assert swarm.cmd_attack(args) == 0
    assert captured["wave_timeout_sec"] == 60 + 2 * ac.WAVE_GRACE_SEC


# ---------- F-005: инкрементальное сохранение review_id при исключении волны ----------

def test_f005_wave_exception_saves_started_review_ids(tmp_path, monkeypatch):
    """Исключение волны (TimeoutError) не теряет review_id уже стартовавших
    участников: session.json сохранён ДО проброса — close закроет sandbox'ы
    (парность cleanup §6.3.9)."""
    monkeypatch.chdir(tmp_path)
    sdir, session = _light_session(tmp_path)
    start_result = ac.InvocationResult(
        ok=True, kind="ok", review_id="rev-1", session_id="as-1", text="...")
    _stub_attack_env(monkeypatch, tmp_path, start_result)

    def exploding_wave(tasks, parallel_cap=ac.PARALLEL_CAP, wave_timeout_sec=None):
        tasks[0]()  # первый участник успел стартовать — sandbox создан
        raise TimeoutError("волна превысила таймаут")

    monkeypatch.setattr(swarm, "run_capped_wave", exploding_wave)
    args = SimpleNamespace(session_id=session["session_id"], registry=None)
    with pytest.raises(TimeoutError):
        swarm.cmd_attack(args)
    on_disk = swarm.load_session(sdir)
    assert on_disk["participants"][0]["review_id"] == "rev-1", (
        "review_id стартовавшего участника обязан пережить исключение волны"
    )


# ---------- F-007: conditional_accept — не терминальный approved ----------

def test_f007_conditional_accept_not_positive():
    """Условное принятие — промежуточный статус, требующий явного решения
    Оркестратора; auto-approve не происходит."""
    assert swarm.gate_verdict_positive("acceptance", {"verdict": "accept"})
    assert not swarm.gate_verdict_positive(
        "acceptance", {"verdict": "conditional_accept"})
    assert swarm.gate_verdict_positive("completion", {"decision": "APPROVE_COMPLETION"})


# ---------- F-008: _budget_gate резервирует волны команды ----------

def _session_at_elapsed(elapsed_sec, timeout_sec=60, budget_sec=3600):
    started = (datetime.now(UTC) - timedelta(seconds=elapsed_sec)).isoformat()
    return {
        "timeout_sec": timeout_sec,
        "degraded": None,
        "wall_clock": {"started_at": started, "budget_sec": budget_sec,
                       "warn_at_sec": int(budget_sec * 0.75)},
    }


def test_f008_budget_gate_reserves_planned_waves():
    """При «бюджет минус N волн команды» волны не стартуют: одна волна ещё
    проходит, три — уже нет (NFR-08 внутри команды)."""
    wave = 60 + 2 * ac.WAVE_GRACE_SEC
    budget = 10 * wave
    session = _session_at_elapsed(budget - 2.5 * wave, budget_sec=budget)
    assert swarm._budget_gate(session, waves=1) is True
    assert swarm._budget_gate(session, waves=3) is False
    assert session["degraded"]["reason"] == "wall_clock_budget"


def test_f008_planned_verdict_wave_count_chunks():
    """Число волн команды = чанки PARALLEL_CAP от (треды × голосующие)."""
    findings = [{"finding_id": f"F-{i:03d}", "author_id": "claude-opus"}
                for i in range(1, 3)]
    threads = {f["finding_id"]: core.start_thread(f) for f in findings}
    participants = [{"id": "claude-opus", "state": "active", "review_id": "r0"}]
    participants += [{"id": f"voter-{i}", "state": "active", "review_id": f"r{i}"}
                     for i in range(1, 10)]  # 9 голосующих
    session = {"threads": threads, "participants": participants}
    # 2 треда × 9 голосующих = 18 задач → ceil(18/8) = 3 волны
    assert swarm._planned_verdict_wave_count(session, tour=2) == 3
    # 1 тред × 9 голосующих = 9 задач → 2 волны
    one = {"threads": {"F-001": threads["F-001"]}, "participants": participants}
    assert swarm._planned_verdict_wave_count(one, tour=2) == 2
    # голосовать некому → волн нет, команда пуста
    empty = {"threads": threads,
             "participants": [{"id": "claude-opus", "state": "active",
                               "review_id": "r0"}]}
    assert swarm._planned_verdict_wave_count(empty, tour=2) == 0


def test_f008_hard_cap_not_exceeded():
    """Жёсткий потолок 16620 с не нарушается формулой бюджета."""
    budget, _ = swarm.wall_clock_budget(1500, unique_unconfirmed=500)
    assert budget == swarm.WALL_CLOCK_HARD_CAP_SEC == 16620


# ---------- HC-16: retained lock-only tombstone после adapter close ----------

def test_hc16_lock_only_tombstone_is_retained_and_canonically_classified(tmp_path):
    """Stable lock остаётся durable tombstone и распознаётся общим контрактом."""
    tombstone = tmp_path / ac.REVIEW_ROOT / "review-closed"
    tombstone.mkdir(parents=True)
    stable_lock = tombstone / ac.INVOCATION_LOCK_NAME
    stable_lock.touch()

    assert ac.is_lock_only_tombstone(tombstone)
    assert stable_lock.exists()


def test_hc16_payload_directory_is_not_misclassified_as_tombstone(tmp_path):
    """Лишний payload исключает lock-only классификацию и сохраняется."""
    tombstone = tmp_path / ac.REVIEW_ROOT / "review-unexpected"
    tombstone.mkdir(parents=True)
    stable_lock = tombstone / ac.INVOCATION_LOCK_NAME
    stable_lock.touch()
    payload = tombstone / "review.json"
    payload.write_text("payload", encoding="utf-8")

    assert not ac.is_lock_only_tombstone(tombstone)
    assert stable_lock.exists()
    assert payload.exists()


def test_hc16_failed_start_tombstone_close_is_idempotent_and_retained(tmp_path):
    """Close принимает уже закрытый failed-start tombstone и не удаляет lock."""
    tombstone = tmp_path / ac.REVIEW_ROOT / "review-failed-start"
    tombstone.mkdir(parents=True)
    (tombstone / ac.INVOCATION_LOCK_NAME).touch()

    status, ok = swarm._close_participant_idempotent(
        "unused-adapter.py", "review-failed-start", tmp_path)
    assert ok
    assert "tombstone" in status
    assert ac.is_lock_only_tombstone(tombstone)


# ---------- F-009: strict-сопоставление результатов волны ----------

def test_f009_wave_result_length_mismatch_fail_closed(tmp_path, monkeypatch):
    """Несовпадение длин results/turn — fail-closed (ValueError), не тихое
    усечение zip: результат волны не может быть сопоставлен не тому участнику."""
    monkeypatch.chdir(tmp_path)
    sdir, session = _light_session(tmp_path)
    start_result = ac.InvocationResult(
        ok=True, kind="ok", review_id="rev-1", session_id="as-1", text="...")
    _stub_attack_env(monkeypatch, tmp_path, start_result)
    monkeypatch.setattr(swarm, "run_capped_wave",
                        lambda tasks, **kw: [])  # потерян результат
    args = SimpleNamespace(session_id=session["session_id"], registry=None)
    with pytest.raises(ValueError):
        swarm.cmd_attack(args)


# ---------- F-014: код gate-протокола в карте критичности ----------

def test_f014_gate_protocol_paths_in_criticality_map():
    """Изменения кода gate-протокола (review-swarm/review-harness) не могут
    триажироваться в серую зону лёгкого тарифа (F-014)."""
    criticality_map = swarm.load_criticality_map(swarm.DEFAULT_CRITICALITY_MAP)
    assert ".framework/skills/review-swarm/**" in criticality_map["patterns"]
    assert ".framework/skills/review-harness/**" in criticality_map["patterns"]
    hits = swarm.criticality_hits(
        [".framework/skills/review-swarm/scripts/swarm.py"], criticality_map)
    assert hits == [".framework/skills/review-swarm/scripts/swarm.py"]
    lenses = swarm.resolve_forced_lenses(hits, criticality_map)
    assert lenses[".framework/skills/review-swarm/scripts/swarm.py"] in (
        "security", "correctness")


# ---------- F-015: ASCII-разделитель привязки линзы ----------

def test_f015_ascii_arrow_binding_supported():
    parsed = swarm.parse_criticality_map("- `x/**` -> security\n")
    assert parsed["lens_bindings"] == {"x/**": "security"}
    assert parsed["patterns"] == ["x/**"]


def test_f015_unicode_arrow_still_supported():
    parsed = swarm.parse_criticality_map("- `x/**` → security\n")
    assert parsed["lens_bindings"] == {"x/**": "security"}
    assert parsed["warnings"] == []


def test_f015_unrecognized_separator_warned():
    """Нераспознанный разделитель — явное предупреждение (не молчаливая
    потеря привязки)."""
    parsed = swarm.parse_criticality_map("- `x/**` ⇒ security\n")
    assert parsed["lens_bindings"] == {}
    assert parsed["patterns"] == ["x/**"]
    assert parsed["warnings"], "ожидается предупреждение о нераспознанном разделителе"


def test_f015_load_map_fail_closed_on_unrecognized(tmp_path):
    path = tmp_path / "map.md"
    path.write_text("- `x/**`\n- `y/**` ⇒ security\n", encoding="utf-8")
    with pytest.raises(swarm.CliError, match="разделител"):
        swarm.load_criticality_map(path)


def test_f015_real_map_has_no_warnings():
    parsed = swarm.parse_criticality_map(
        swarm.DEFAULT_CRITICALITY_MAP.read_text(encoding="utf-8"))
    assert parsed["warnings"] == []
