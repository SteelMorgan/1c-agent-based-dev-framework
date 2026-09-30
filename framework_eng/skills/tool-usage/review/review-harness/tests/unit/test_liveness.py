"""HU-L01..L05 — liveness-классификация «думает/повисла» (RVSW-01, T-05, FR-13, AC-13).

Тест-план §3.3, TD §9:
- класс active  — свежая активность (возраст ≤ порога тишины);
- класс quiet   — тишина активности > порога (диагностический маркер, НЕ kill);
- класс dead_watcher — heartbeat старше 10 с (жёсткий сигнал: процесс-адаптер
  не жив), независимо от активности;
- порог тишины параметризуем (--silence-threshold-sec, TBD-04);
- граница детерминирована: ровно 120 с → active, ровно 10 с → не dead_watcher.
"""
from __future__ import annotations

import json
from datetime import UTC, datetime

import pytest

import liveness

NOW = 1_800_000_000.0  # фиксированная эпоха для детерминированных возрастов


def iso(ts: float) -> str:
    """Формат адаптеров: datetime.now(UTC).isoformat() (TD §9.1)."""
    return datetime.fromtimestamp(ts, UTC).isoformat()


def make_activity(activity_age: float | None = 5.0, heartbeat_age: float | None = 1.0) -> dict:
    """runtime_factory §2.3 в чистом виде: поля активности со смещениями от NOW."""
    return {
        "last_activity_at": None if activity_age is None else iso(NOW - activity_age),
        "last_heartbeat_at": None if heartbeat_age is None else iso(NOW - heartbeat_age),
    }


# ---------- HU-L01: свежая активность + свежий heartbeat → active ----------

def test_hu_l01_fresh_activity_is_active():
    result = liveness.classify_liveness(make_activity(activity_age=119.0, heartbeat_age=2.0), now=NOW)
    assert result["class"] == liveness.CLASS_ACTIVE == "active"
    assert result["activity_age_sec"] == pytest.approx(119.0)
    assert result["heartbeat_age_sec"] == pytest.approx(2.0)


# ---------- HU-L02: тишина активности > 120 с → quiet (маркер, не kill) ----------

def test_hu_l02_silent_activity_is_quiet_diagnostic_only():
    result = liveness.classify_liveness(make_activity(activity_age=300.0, heartbeat_age=2.0), now=NOW)
    assert result["class"] == liveness.CLASS_QUIET == "quiet"
    # Только диагностический сигнал (RISK-05): результат — чистая классификация,
    # без каких-либо полей действия (kill/остановка вне контракта модуля).
    assert set(result) == {"class", "activity_age_sec", "heartbeat_age_sec", "silence_threshold_sec"}
    assert result["silence_threshold_sec"] == liveness.DEFAULT_SILENCE_THRESHOLD_SEC == 120.0


# ---------- HU-L03: heartbeat > 10 с → dead_watcher независимо от активности ----------

def test_hu_l03_stale_heartbeat_is_dead_watcher():
    # Активность свежая, но watcher мёртв — жёсткий сигнал приоритетнее.
    result = liveness.classify_liveness(make_activity(activity_age=1.0, heartbeat_age=15.0), now=NOW)
    assert result["class"] == liveness.CLASS_DEAD_WATCHER == "dead_watcher"
    # И при тихой активности тоже dead_watcher (не quiet).
    result = liveness.classify_liveness(make_activity(activity_age=999.0, heartbeat_age=11.0), now=NOW)
    assert result["class"] == "dead_watcher"


# ---------- HU-L04: параметр --silence-threshold-sec переопределяет 120 с ----------

def test_hu_l04_silence_threshold_is_parametric():
    activity = make_activity(activity_age=300.0, heartbeat_age=2.0)
    assert liveness.classify_liveness(activity, now=NOW)["class"] == "quiet"
    result = liveness.classify_liveness(activity, now=NOW, silence_threshold_sec=600.0)
    assert result["class"] == "active"
    assert result["silence_threshold_sec"] == 600.0


# ---------- HU-L05: граничные значения детерминированы (≤ — active) ----------

def test_hu_l05_boundary_values():
    # Ровно 120 с тишины → active (граница включительна).
    result = liveness.classify_liveness(make_activity(activity_age=120.0, heartbeat_age=1.0), now=NOW)
    assert result["class"] == "active"
    # 120 с + ε → quiet.
    result = liveness.classify_liveness(make_activity(activity_age=120.1, heartbeat_age=1.0), now=NOW)
    assert result["class"] == "quiet"
    # Ровно 10 с heartbeat → ещё НЕ dead_watcher (граница строгая: > 10).
    result = liveness.classify_liveness(make_activity(activity_age=1.0, heartbeat_age=10.0), now=NOW)
    assert result["class"] == "active"
    # 10 с + ε → dead_watcher.
    result = liveness.classify_liveness(make_activity(activity_age=1.0, heartbeat_age=10.1), now=NOW)
    assert result["class"] == "dead_watcher"


# ---------- Деградация данных: отсутствующие поля активности ----------

def test_missing_timestamps_fail_safe():
    """Битые/отсутствующие поля (адаптер не успел записать): heartbeat →
    dead_watcher (watcher не подтверждён); активность при живом heartbeat →
    quiet (прогресс не подтверждён). Никогда не «active» по умолчанию."""
    assert liveness.classify_liveness(make_activity(heartbeat_age=None), now=NOW)["class"] == "dead_watcher"
    result = liveness.classify_liveness(make_activity(activity_age=None, heartbeat_age=1.0), now=NOW)
    assert result["class"] == "quiet"
    assert result["activity_age_sec"] is None
    # Эпохальные числа принимаются наравне с ISO (thin IO над runtime.json).
    result = liveness.classify_liveness(
        {"last_activity_at": NOW - 5.0, "last_heartbeat_at": NOW - 1.0}, now=NOW)
    assert result["class"] == "active"


# ---------- Thin IO: чтение через канонический контракт адаптера (T-03) ----------

def test_classify_participant_reads_runtime_json(tmp_path):
    """classify_participant читает поля через adapter_contract.read_participant_activity
    (FR-13: новый таймстамп не вводится)."""
    review_id = "rev-1"
    runtime_dir = tmp_path / ".review-sandboxes" / review_id
    runtime_dir.mkdir(parents=True)
    (runtime_dir / "runtime.json").write_text(json.dumps({
        "phase": "running",
        "last_activity_at": iso(NOW - 5.0),
        "last_heartbeat_at": iso(NOW - 1.0),
    }), encoding="utf-8")
    result = liveness.classify_participant(tmp_path, review_id, now=NOW)
    assert result["class"] == "active"
    # Отсутствующий runtime.json → dead_watcher (watcher не подтверждён).
    assert liveness.classify_participant(tmp_path, "rev-absent", now=NOW)["class"] == "dead_watcher"
