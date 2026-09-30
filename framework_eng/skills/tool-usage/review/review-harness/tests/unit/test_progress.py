"""HU-P01..P03 — progress.jsonl чекпоинтов (RVSW-01, T-05, FR-13, AC-13).

Тест-план §3.4, TD §5.6/§9.3:
- схема записи: ts/session_id/tool/checkpoint/summary/counters;
- чекпоинты — на границах туров/фаз, не поток (FR-13); множество допустимых
  имён — собственность протокола инструмента и передаётся вызывающей стороной
  (правило границы FR-01: harness не знает о фазах/турах);
- append-only: файл только растёт; status дублирует последний чекпоинт
  (через last_checkpoint);
- counters — снимок на границе (invocations/findings/unique_unconfirmed/
  unresponsive), неотрицательные int.
"""
from __future__ import annotations

import json
from datetime import datetime

import pytest

import progress

# Enum'ы TD §5.6 — здесь, в тесте инструментного слоя; в harness они запрещены
# правилом границы (HU-B02) и передаются параметром allowed_checkpoints.
SWARM_CHECKPOINTS = (
    "convened", "tour1_complete", "dedup_complete", "tour2_complete",
    "tour3_complete", "tour4_complete", "arbitration_complete",
    "report_ready", "closed",
)
CONSILIUM_CHECKPOINTS = (
    ("convened", "phase_a_complete", "phase_c_complete", "phase_d_complete",
     "verdict_ready", "closed")
    + tuple(f"round_{n}_complete" for n in (1, 2, 3, 4))
)

COUNTERS = {"invocations": 3, "findings": 14, "unique_unconfirmed": 5, "unresponsive": 0}


def append(session_dir, checkpoint, tool="swarm", allowed=SWARM_CHECKPOINTS,
           counters=None, summary="тур 1: 3/3 ответили, 14 находок, 5 unique"):
    return progress.append_checkpoint(
        session_dir,
        tool=tool,
        session_id=f"{tool}-s1",
        checkpoint=checkpoint,
        summary=summary,
        counters=COUNTERS if counters is None else counters,
        allowed_checkpoints=allowed,
    )


# ---------- HU-P01: схема записи и fail-closed на неизвестный checkpoint ----------

def test_hu_p01_checkpoint_schema(tmp_path):
    record = append(tmp_path, "tour1_complete")
    assert set(record) == {"ts", "session_id", "tool", "checkpoint", "summary", "counters"}
    assert record["session_id"] == "swarm-s1"
    assert record["tool"] == "swarm"
    assert record["checkpoint"] == "tour1_complete"
    datetime.fromisoformat(record["ts"])  # ts — валидный ISO-8601
    # В файле — ровно одна jsonl-строка, идентичная возвращённой записи.
    lines = (tmp_path / progress.PROGRESS_FILENAME).read_text(encoding="utf-8").splitlines()
    assert len(lines) == 1
    assert json.loads(lines[0]) == record


def test_hu_p01_unknown_checkpoint_rejected_fail_closed(tmp_path):
    with pytest.raises(ValueError, match="checkpoint"):
        append(tmp_path, "tour5_complete")
    assert not (tmp_path / progress.PROGRESS_FILENAME).exists()  # файл не тронут


def test_hu_p01_unknown_tool_rejected(tmp_path):
    with pytest.raises(ValueError, match="tool"):
        append(tmp_path, "tour1_complete", tool="unknown-tool")


def test_hu_p01_both_tool_enums_accepted(tmp_path):
    """Enum консилиума (TD §5.6, включая round_<n>_complete) валиден для tool=consilium."""
    record = append(tmp_path, "round_2_complete", tool="consilium", allowed=CONSILIUM_CHECKPOINTS)
    assert record["checkpoint"] == "round_2_complete"
    record = append(tmp_path, "verdict_ready", tool="consilium", allowed=CONSILIUM_CHECKPOINTS)
    assert record["tool"] == "consilium"


# ---------- HU-P02: append-only, монотонный рост, последний чекпоинт ----------

def test_hu_p02_append_only_monotonic_growth(tmp_path):
    first = append(tmp_path, "convened", summary="сессия созвана")
    path = tmp_path / progress.PROGRESS_FILENAME
    size_after_first = path.stat().st_size
    line_after_first = path.read_text(encoding="utf-8").splitlines()[0]

    second = append(tmp_path, "tour1_complete")
    size_after_second = path.stat().st_size
    lines = path.read_text(encoding="utf-8").splitlines()

    assert size_after_second > size_after_first  # только рост, перезаписи нет
    assert lines[0] == line_after_first          # первая запись неизменна
    assert json.loads(lines[0]) == first
    assert json.loads(lines[1]) == second
    # status дублирует последний чекпоинт (TD §9.3).
    assert progress.last_checkpoint(tmp_path) == second
    assert progress.read_checkpoints(tmp_path) == [first, second]


def test_hu_p02_readers_on_empty_session(tmp_path):
    assert progress.read_checkpoints(tmp_path) == []
    assert progress.last_checkpoint(tmp_path) is None


# ---------- HU-P03: counters — снимок на границе тура/фазы ----------

def test_hu_p03_counters_snapshot_recorded_verbatim(tmp_path):
    counters = {"invocations": 3, "findings": 14, "unique_unconfirmed": 5, "unresponsive": 1}
    record = append(tmp_path, "dedup_complete", counters=counters)
    assert record["counters"] == counters
    on_disk = progress.read_checkpoints(tmp_path)[0]
    assert on_disk["counters"] == counters  # снимок состояния сессии на границе


def test_hu_p03_invalid_counters_rejected(tmp_path):
    with pytest.raises(ValueError, match="counters"):
        append(tmp_path, "tour1_complete", counters={"invocations": "три"})
    with pytest.raises(ValueError, match="counters"):
        append(tmp_path, "tour1_complete", counters={"invocations": -1})
    with pytest.raises(ValueError, match="counters"):
        append(tmp_path, "tour1_complete", counters=[1, 2, 3])
    assert not (tmp_path / progress.PROGRESS_FILENAME).exists()


def test_hu_p03_empty_summary_rejected(tmp_path):
    """summary — человекочитаемая строка границы (FR-13: чекпоинт, не поток)."""
    with pytest.raises(ValueError, match="summary"):
        append(tmp_path, "tour1_complete", summary="")
    with pytest.raises(ValueError, match="summary"):
        append(tmp_path, "tour1_complete", summary=None)
