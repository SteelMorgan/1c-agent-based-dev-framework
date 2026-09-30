"""SU-B / SU-FC — wall-clock бюджет, параллелизм волн, fail-closed предикаты
(RVSW-01, T-10, NFR-08/AC-25, NFR-01/AC-03/AC-19, TD §6.3, §6.4, §11).

Покрытие test-plan:
- SU-B01: бюджет пересчитывается от per-invocation timeout:
  max(3600, W×(T+240)+1800), W = 1 + ceil(2U/8) + ceil(U/8) + ceil(2U/8) + 2,
  пересчёт после dedup по фактическому U; предупреждение на 75 %;
  жёсткий потолок 16620 с;
- SU-B02: волны туров 2–4 исполняются через run_wave с чанкингом
  PARALLEL_CAP = 8 (in-process spy: последовательного перебора находок нет,
  одновременных вызовов не больше cap);
- SU-B03: при достижении бюджета минус одна волна новые волны не стартуют;
  незавершённые находки деградируют в `unvalidated` (репорт из текущего
  состояния, не тихий обрыв);
- SU-FC01: невалидный structured-ход → ровно один retry → unresponsive
  (предикат §6.3.1 на уровне CLI); таймаут → unresponsive без контент-retry.
"""
from __future__ import annotations

import threading
import time
from datetime import UTC, datetime, timedelta

import pytest

import adapter_contract as ac
import swarm


# ---------- SU-B01: формула бюджета (TD §11) ----------

def test_su_b01_default_budget_before_dedup():
    """До dedup U неизвестно: W=10 → max(3600, 10×(900+240)+1800) = 13200."""
    budget, warn = swarm.wall_clock_budget(900)
    assert budget == 13200
    assert warn == int(13200 * 0.75)


def test_su_b01_recalc_after_dedup():
    """Пересчёт по фактическому U=3: W = 1 + ceil(6/8) + ceil(3/8) + ceil(6/8) + 2 = 6."""
    assert swarm.wave_count(3) == 6
    budget, _ = swarm.wall_clock_budget(900, unique_unconfirmed=3)
    assert budget == max(3600, 6 * 1140 + 1800)


def test_su_b01_floor_for_small_u():
    """U=0: W = 1+0+0+0+2 = 3 → 3×1140+1800 = 5220 > 3600; пол 3600 при T=100."""
    budget, _ = swarm.wall_clock_budget(100, unique_unconfirmed=0)
    assert budget == max(3600, 3 * 340 + 1800)
    assert budget == 3600


def test_su_b01_timeout_override_rescales():
    """Переопределение per-invocation timeout пересчитывает бюджет (AC-25)."""
    budget_default, _ = swarm.wall_clock_budget(900)
    budget_custom, _ = swarm.wall_clock_budget(300)
    assert budget_custom == max(3600, 10 * 540 + 1800)
    assert budget_custom < budget_default


def test_su_b01_hard_cap():
    """Жёсткий потолок 16620 с не превышается даже при огромном U."""
    budget, _ = swarm.wall_clock_budget(900, unique_unconfirmed=500)
    assert budget == swarm.WALL_CLOCK_HARD_CAP_SEC == 16620


def test_su_b01_warn_at_75_percent():
    _, warn = swarm.wall_clock_budget(900, unique_unconfirmed=10)
    assert warn == int(swarm.wall_clock_budget(900, unique_unconfirmed=10)[0] * 0.75)


def test_su_b01_keyed_budget_accounts_for_more_unique_keys_than_cap():
    """9 независимых keys при cap=8 резервируют два scheduler-интервала."""
    per_invocation = 100 + 2 * ac.WAVE_GRACE_SEC
    budget, _ = swarm.wall_clock_budget(
        100, unique_unconfirmed=1, keyed_slot_bounds=(2, 0, 2))
    assert budget == max(3600, (3 + 2 + 0 + 2) * per_invocation + 1800)


@pytest.mark.parametrize("bounds", [[1, 1, 1], (1, 1), (1, -1, 1), (1, True, 1)])
def test_su_b01_keyed_budget_rejects_invalid_slot_bounds(bounds):
    """Keyed budget fail-closed rejects ambiguous shape, type and negatives."""
    with pytest.raises(ValueError, match="keyed_slot_bounds"):
        swarm.wall_clock_budget(100, unique_unconfirmed=1, keyed_slot_bounds=bounds)


def test_su_b01_keyed_validation_bounds_use_participant_fifo_lanes():
    """Slot bounds account for both cap batches and repeated participant lanes."""
    participants = [f"p{i}" for i in range(10)]
    assert swarm.keyed_validation_slot_bounds(participants, ["p0"]) == (2, 1, 2)
    assert swarm.keyed_validation_slot_bounds(["p0", "p1", "p2"], ["p0"] * 9) == (9, 9, 9)


# ---------- SU-B02: чанкинг волн PARALLEL_CAP=8 через run_wave (NFR-08) ----------

def test_su_b02_parallel_cap_chunking():
    """20 задач: одновременно исполняется не более 8; все выполнены; порядок
    результатов сохранён; волн = ceil(20/8) = 3 (вторая глава волны ждёт первую)."""
    running = 0
    max_running = 0
    wave_marks: list[float] = []
    lock = threading.Lock()

    def make_task(i):
        def task():
            nonlocal running, max_running
            with lock:
                running += 1
                max_running = max(max_running, running)
            time.sleep(0.05)
            with lock:
                running -= 1
                wave_marks.append(time.monotonic())
            return i
        return task

    tasks = [make_task(i) for i in range(20)]
    results = swarm.run_capped_wave(tasks, parallel_cap=8)
    assert results == list(range(20))
    assert max_running <= 8
    assert max_running > 1, "ожидается реальный параллелизм, не последовательный перебор"


def test_su_b02_delegates_to_harness_run_wave(monkeypatch):
    """Исполнение — переиспользование adapter_contract.run_wave (NFR-08):
    последовательный перебор находок запрещён."""
    calls: list[int] = []

    def spy(chunk, wave_timeout_sec=None, max_workers=None):
        calls.append(len(chunk))
        return [task() for task in chunk]

    monkeypatch.setattr(ac, "run_wave", spy)
    results = swarm.run_capped_wave([lambda: 1, lambda: 2], parallel_cap=8)
    assert results == [1, 2]
    assert calls == [2], "одна волна через harness run_wave, не по одной находке"


def test_su_b02_empty_wave():
    assert swarm.run_capped_wave([], parallel_cap=8) == []


# ---------- SU-B03: бюджет минус одна волна → новые волны не стартуют ----------

def _ts(dt: datetime) -> str:
    return dt.isoformat()


def test_su_b03_wave_allowed_within_budget():
    started = _ts(datetime.now(UTC) - timedelta(seconds=100))
    assert swarm.wave_allowed(started, budget_sec=13200, wave_sec=1140) is True


def test_su_b03_wave_blocked_at_budget_minus_wave():
    """elapsed ≥ budget − wave → волна НЕ стартует (TD §11)."""
    started = _ts(datetime.now(UTC) - timedelta(seconds=13200 - 1140 + 1))
    assert swarm.wave_allowed(started, budget_sec=13200, wave_sec=1140) is False


def test_su_b03_deterministic_now():
    started = _ts(datetime(2026, 7, 29, tzinfo=UTC))
    now = datetime(2026, 7, 29, tzinfo=UTC) + timedelta(seconds=13000)
    assert swarm.wave_allowed(started, 13200, 1140, now=now) is False
    now_ok = datetime(2026, 7, 29, tzinfo=UTC) + timedelta(seconds=1000)
    assert swarm.wave_allowed(started, 13200, 1140, now=now_ok) is True


def test_su_b03_degrade_open_threads_to_unvalidated():
    """Деградация в репорт: находки без завершённой валидации — unvalidated,
    завершённые статусы сохраняются (не тихий обрыв, TD §11)."""
    threads = {
        "F-001": {"finding_id": "F-001", "status": "open"},
        "F-002": {"finding_id": "F-002", "status": "confirmed"},
        "F-003": {"finding_id": "F-003", "status": "contested"},
    }
    degraded = swarm.degraded_statuses(threads)
    assert degraded == {
        "F-001": "unvalidated",
        "F-002": "confirmed",
        "F-003": "contested",
    }


# ---------- SU-FC01: fail-closed предикаты §6.3.1 ----------

def test_su_fc01_invalid_move_retry_once():
    """Невалидный structured-ход → ровно ОДИН контент-retry → unresponsive."""
    assert swarm.content_retry_decision(valid=True, retried=False) == "accept"
    assert swarm.content_retry_decision(valid=False, retried=False) == "retry"
    assert swarm.content_retry_decision(valid=False, retried=True) == "unresponsive"
    assert swarm.content_retry_decision(valid=True, retried=True) == "accept"


def test_su_fc01_timeout_no_retry():
    """Таймаут → unresponsive БЕЗ контент-retry (§6.3.1); транспортный retry
    ошибки — внутри adapter_contract (ровно один), здесь решение только о
    контент-retry."""
    assert swarm.content_retry_decision(valid=False, retried=False,
                                        invocation_kind="timeout") == "unresponsive"
    assert swarm.content_retry_decision(valid=False, retried=False,
                                        invocation_kind="error") == "retry"


def test_su_fc01_mark_unresponsive_after_failed_invocation():
    """Результат адаптера после транспортной retry-политики контракта:
    не ok → участник unresponsive (предикат CLI)."""
    result = ac.InvocationResult(ok=False, kind="timeout", error="t")
    assert swarm.invocation_outcome(result) == "unresponsive"
    ok_result = ac.InvocationResult(ok=True, kind="ok", text="...")
    assert swarm.invocation_outcome(ok_result) == "ok"
