"""HU-T — переехавшие предикатные тесты track record + параметризация storage_dir (RVSW-01, T-03).

Перенесены из `agent-consilium/tests/unit/test_core.py` без правки логики
(ASM-01 — только импорты; оригиналы не тронуты, T-06 переведёт их на shim'ы):
- UT-16 (exploration-часть): каждый 4-й — exploration. Часть про assign_roles
  НЕ переезжает: назначение ролей — протокол инструмента, остаётся в ядрах.
- UT-17: формулы strengths (веса 0.6/0.4, decay 0.5^(k/8), перенормировка).
- UT-18: сегрегация «модератор = участник» (tainted, дисконт x0.5, n_eff).

Новые тесты слоя хранения (FR-01е): observations/strengths/config живут в
параметризованном storage_dir — консилиум и рой пишут в РАЗНЫЕ каталоги.
"""
from __future__ import annotations

import json

import pytest

import track_record


# ---------- UT-16 (exploration-часть): exploration-бюджет ----------

def test_ut16_exploration_budget_every_4th():
    """UT-16: (c+1) % 4 == 0 → exploration (round-robin вразрез статистике — в ядрах)."""
    assert track_record.is_exploration_consilium(3) is True
    assert track_record.is_exploration_consilium(0) is False
    assert track_record.is_exploration_consilium(8) is False


# ---------- UT-17..UT-18: strengths (TBD-02, TD 3.4) ----------

def test_ut17_strengths_formula(track_record_factory):
    """UT-17: веса 0.6/0.4, decay 0.5^(k/8), перенормировка при пустом знаменателе."""
    observations = track_record_factory([
        {"consilium_seq": 1, "findings_accepted": 3, "findings_withdrawn": 1,
         "critiques_upheld": 2, "critiques_overruled": 1},
        {"consilium_seq": 2, "findings_accepted": 1, "findings_withdrawn": 1,
         "critiques_upheld": 1, "critiques_overruled": 1},
    ])
    result = track_record.compute_strengths(observations)
    entry = result[("claude-opus", "architecture")]
    w1 = 0.5 ** (1 / 8)
    accept_rate = (w1 * 3 + 1) / (w1 * 4 + 2)
    upheld_rate = (w1 * 2 + 1) / (w1 * 3 + 2)
    assert entry["accept_rate"] == pytest.approx(accept_rate, rel=1e-9)
    assert entry["upheld_rate"] == pytest.approx(upheld_rate, rel=1e-9)
    assert entry["score"] == pytest.approx(0.6 * accept_rate + 0.4 * upheld_rate, rel=1e-9)
    assert entry["n_eff"] == pytest.approx(2.0)

    # пустой знаменатель accept → компонента исключается, веса перенормируются
    observations = track_record_factory([
        {"consilium_seq": 1, "findings_accepted": 0, "findings_withdrawn": 0,
         "critiques_upheld": 2, "critiques_overruled": 2},
    ])
    entry = track_record.compute_strengths(observations)[("claude-opus", "architecture")]
    assert entry["accept_rate"] is None
    assert entry["score"] == pytest.approx(0.5)  # = upheld_rate


def test_ut18_moderator_participant_segregation(track_record_factory):
    """UT-18: tainted исключены при >=3 clean; при <3 clean — tainted с x0.5; n_eff."""
    clean = [
        {"consilium_seq": i, "findings_accepted": 2, "findings_withdrawn": 0,
         "critiques_upheld": 1, "critiques_overruled": 0}
        for i in (1, 2, 3)
    ]
    tainted = [{"consilium_seq": 4, "moderator_is_participant": True,
                "findings_accepted": 0, "findings_withdrawn": 5,
                "critiques_upheld": 0, "critiques_overruled": 5}]
    entry = track_record.compute_strengths(track_record_factory(clean + tainted))[("claude-opus", "architecture")]
    assert entry["n_clean"] == 3
    assert entry["n_tainted"] == 1
    assert entry["n_eff"] == pytest.approx(3.0)  # tainted исключён
    assert entry["accept_rate"] == pytest.approx(1.0)  # провал tainted не влияет

    # clean < 3 → tainted подключаются с дисконтом x0.5
    entry = track_record.compute_strengths(track_record_factory(clean[:2] + tainted))[("claude-opus", "architecture")]
    assert entry["n_clean"] == 2
    assert entry["n_tainted"] == 1
    assert entry["n_eff"] == pytest.approx(2.5)  # 2 + 0.5*1
    assert entry["accept_rate"] < 1.0  # провал tainted учтён с дисконтом


def test_observation_weight_decay():
    """Decay: w = 0.5^(k/half_life), half-life 8 (TD 3.4)."""
    assert track_record.observation_weight(0) == pytest.approx(1.0)
    assert track_record.observation_weight(8) == pytest.approx(0.5)
    assert track_record.observation_weight(16) == pytest.approx(0.25)
    assert track_record.observation_weight(4, half_life=4) == pytest.approx(0.5)


def test_compute_strengths_seq_field_param(track_record_factory):
    """Параметр seq_field: рой ведёт свой счётчик seq, формулы те же (FR-01е)."""
    observations = track_record_factory([
        {"review_seq": 1, "findings_accepted": 2, "findings_withdrawn": 0,
         "critiques_upheld": 1, "critiques_overruled": 0},
        {"review_seq": 2, "findings_accepted": 1, "findings_withdrawn": 1,
         "critiques_upheld": 0, "critiques_overruled": 1},
    ])
    for obs in observations:
        del obs["consilium_seq"]
    result = track_record.compute_strengths(observations, seq_field="review_seq")
    entry = result[("claude-opus", "architecture")]
    w1 = 0.5 ** (1 / 8)
    assert entry["accept_rate"] == pytest.approx((w1 * 2 + 1) / (w1 * 2 + 2), rel=1e-9)


# ---------- Слой хранения (FR-01е): параметризация storage_dir ----------

def test_storage_read_missing_dir(tmp_path):
    """Отсутствующий каталог → пустые observations/config (без ошибки)."""
    storage = tmp_path / "absent-track-record"
    assert track_record.read_observations(storage) == []
    assert track_record.read_track_config(storage) == {}


def test_storage_append_and_regenerate(tmp_path, track_record_factory):
    """append_observations (append-only) → read_observations; regenerate_strengths
    пишет strengths.json тем же форматом «pid|role», что и консилиум."""
    storage = tmp_path / ".track-record"
    batch1 = track_record_factory([
        {"consilium_seq": 1, "findings_accepted": 3, "findings_withdrawn": 1,
         "critiques_upheld": 2, "critiques_overruled": 1},
    ])
    track_record.append_observations(storage, batch1)
    batch2 = track_record_factory([
        {"consilium_seq": 2, "findings_accepted": 1, "findings_withdrawn": 0,
         "critiques_upheld": 1, "critiques_overruled": 0},
    ])
    track_record.append_observations(storage, batch2)
    loaded = track_record.read_observations(storage)
    assert [o["consilium_seq"] for o in loaded] == [1, 2]  # append-only, порядок

    strengths = track_record.regenerate_strengths(storage)
    path = storage / "strengths.json"
    assert path.exists()
    on_disk = json.loads(path.read_text(encoding="utf-8"))
    assert strengths == on_disk
    key = "claude-opus|architecture"
    assert key in on_disk
    assert set(on_disk[key]) == {"score", "n_eff", "accept_rate", "upheld_rate"}


def test_storage_dirs_isolated_between_tools(tmp_path, track_record_factory):
    """FR-01е: консилиум и рой пишут в РАЗНЫЕ каталоги — полная изоляция."""
    consilium_dir = tmp_path / ".consilium-track-record"
    swarm_dir = tmp_path / ".swarm-track-record"
    track_record.append_observations(
        consilium_dir, track_record_factory([{"consilium_seq": 1, "findings_accepted": 1}]))
    track_record.append_observations(
        swarm_dir, track_record_factory([{"consilium_seq": 1, "participant_id": "codex-gpt"}]))
    assert len(track_record.read_observations(consilium_dir)) == 1
    assert len(track_record.read_observations(swarm_dir)) == 1
    assert track_record.read_observations(consilium_dir)[0]["participant_id"] == "claude-opus"
    assert track_record.read_observations(swarm_dir)[0]["participant_id"] == "codex-gpt"


def test_storage_bump_counter(tmp_path):
    """Счётчики сессий в config.json: независимые поля, монотонный инкремент
    (consiliums_completed / reviews_completed / light_reviews_completed)."""
    storage = tmp_path / ".track-record"
    assert track_record.bump_counter(storage, "consiliums_completed") == 1
    assert track_record.bump_counter(storage, "consiliums_completed") == 2
    assert track_record.bump_counter(storage, "reviews_completed") == 1
    config = track_record.read_track_config(storage)
    assert config == {"consiliums_completed": 2, "reviews_completed": 1}
