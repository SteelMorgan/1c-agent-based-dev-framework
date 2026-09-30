"""Unit-слой CONS-06: предикаты и метрики human-critic режима (E1–E13).

Trace: E2 (привязка к волнам, аттестация, максимум 1 ход), E3 (membership-
инвариант анонимизации, исключение из nf/pc), E4 (фильтр автора атаки в
compute_model_metrics), E5 (пауза wall-clock), E8 (запрет skip фазы D),
E9 (lint exact protected identifiers).
"""
from __future__ import annotations

from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest

import consilium_core as core

H = core.HUMAN_CRITIC_ID


# ---------------------------------------------------------------------------
# E2: привязка human-хода к атакующим волнам (предикат ядра, fail-closed)
# ---------------------------------------------------------------------------

def test_hc_wave_attachment_allowed_only_attack_b_and_redteam_d():
    assert core.human_turn_wave_allowed("B", "attack") is True
    assert core.human_turn_wave_allowed("D", "redteam") is True
    for phase, wave in [("A", "proposal"), ("B", "response"), ("C", None),
                        ("D", "confirmation"), ("B", "redteam"), ("D", "attack"),
                        ("E", None), (None, "attack")]:
        assert core.human_turn_wave_allowed(phase, wave) is False, (phase, wave)


def test_hc_max_one_turn_per_wave(turn_factory):
    records = [
        turn_factory(seq=1, phase="B", round=1, wave="attack", author=H, type="attack"),
    ]
    assert core.wave_has_human_turn(records, "B", 1, "attack") is True
    # Другая волна/раунд/фаза — хода нет.
    assert core.wave_has_human_turn(records, "B", 1, "response") is False
    assert core.wave_has_human_turn(records, "B", 2, "attack") is False
    assert core.wave_has_human_turn(records, "D", 1, "redteam") is False


def test_hc_turns_in_phase_counts_participant_types_only(turn_factory):
    records = [
        turn_factory(seq=1, phase="B", round=1, wave="attack", author=H, type="attack"),
        turn_factory(seq=2, phase="B", round=2, wave="attack", author=H, type="attack"),
        turn_factory(seq=3, phase="B", round=2, wave="attack", author=H, type="system"),
        turn_factory(seq=4, phase="D", round=2, wave="redteam", author=H, type="redteam_attack"),
    ]
    assert core.human_turns_in_phase(records, "B") == 2
    assert core.human_turns_in_phase(records, "D") == 1
    assert core.human_turns_in_phase(records, "A") == 0


# ---------------------------------------------------------------------------
# E2/E9: валидация human turn-файла (аттестация + lint protected identifiers)
# ---------------------------------------------------------------------------

def _valid_payload():
    return {
        "content": "Критика элемента M1:E2: нет критериев отката.",
        "structured": {"position_changes": [{"element": "M1:E2", "action": "disagree", "refs": []}]},
        "human_approved": True,
    }


def test_hc_turn_file_requires_human_approved_true():
    missing = _valid_payload()
    del missing["human_approved"]
    for broken in (missing, {**_valid_payload(), "human_approved": False},
                   {**_valid_payload(), "human_approved": "true"},
                   {**_valid_payload(), "human_approved": 1}):
        with pytest.raises(core.ProtocolError, match="human_approved"):
            core.validate_human_turn(broken, protected_ids=["claude-opus"])


def test_hc_turn_file_requires_nonempty_content_and_dict_structured():
    with pytest.raises(core.ProtocolError, match="content"):
        core.validate_human_turn({**_valid_payload(), "content": ""}, protected_ids=[])
    with pytest.raises(core.ProtocolError, match="structured"):
        core.validate_human_turn({**_valid_payload(), "structured": ["не объект"]},
                                 protected_ids=[])
    with pytest.raises(core.ProtocolError, match="JSON-объект"):
        core.validate_human_turn(["не объект"], protected_ids=[])


def test_hc_turn_file_lint_exact_protected_identifiers():
    # Внутренний id участника в content — отказ (E9).
    with pytest.raises(core.ProtocolError, match="claude-opus"):
        core.validate_human_turn(
            {**_valid_payload(), "content": "атака по модели claude-opus"},
            protected_ids=["claude-opus", H])
    # Внутренний id записи человека в structured — отказ.
    with pytest.raises(core.ProtocolError, match=H):
        core.validate_human_turn(
            {**_valid_payload(),
             "structured": {"position_changes": [{"element": "human-critic:E1",
                                                  "action": "disagree", "refs": []}]}},
            protected_ids=["claude-opus", H])
    # Широкие маркеры НЕ запрещены (вердикт отверг маркерный словарь).
    turn = core.validate_human_turn(
        {**_valid_payload(), "content": "критика от human critic по существу"},
        protected_ids=["claude-opus"])
    assert turn["content"].startswith("критика от human")


def test_hc_turn_file_normalization():
    turn = core.validate_human_turn(_valid_payload(), protected_ids=["claude-opus"])
    assert set(turn) == {"content", "structured", "refs"}
    assert turn["refs"] == []


# ---------------------------------------------------------------------------
# E3: membership-инвариант анонимизации в render_bundle (BLOCK-дефект F)
# ---------------------------------------------------------------------------

def test_hc_render_bundle_membership_invariant(turn_factory):
    record = turn_factory(seq=1, author="claude-opus", type="attack")
    anon_map = core.create_anon_map(["claude-opus", "codex-gpt"])
    assert "M" in core.render_bundle([record], anon_map)
    # Запись участника с author вне anon_map → ProtocolError (не молчаливый
    # fallback на реальный id).
    stranger = turn_factory(seq=2, author=H, type="attack")
    with pytest.raises(core.ProtocolError, match="membership-инвариант"):
        core.render_bundle([record, stranger], anon_map)


def test_hc_render_bundle_human_in_anon_map(turn_factory):
    ids = ["claude-opus", "codex-gpt", H]
    anon_map = core.create_anon_map(ids)
    record = turn_factory(seq=1, author=H, type="attack", content="критика human")
    rendered = core.render_bundle([record], anon_map)
    assert anon_map[H] in rendered
    assert H not in rendered


# ---------------------------------------------------------------------------
# E3: human-записи вне стоп-предикатов nf/pc
# ---------------------------------------------------------------------------

def test_hc_excluded_from_nf_pc_predicates(turn_factory):
    human_turn = turn_factory(
        seq=1, round=1, author=H,
        new_findings=[{"id": "F-99", "text": "human finding"}],
        position_changes=[{"element": "M1:E2", "action": "disagree", "refs": []}],
    )
    # Без исключения human-запись двигает предикаты (красный контроль).
    assert core.round_has_new_findings([human_turn], 1) is True
    assert core.round_has_position_changes([human_turn], 1) is True
    # С exclude_authors — не участвует (E3).
    assert core.round_has_new_findings([human_turn], 1, exclude_authors={H}) is False
    assert core.round_has_position_changes([human_turn], 1, exclude_authors={H}) is False
    # Модельные записи при том же exclude работают как раньше.
    model_turn = turn_factory(seq=2, round=1, author="claude-opus",
                              new_findings=[{"id": "F-01", "text": "x"}])
    assert core.round_has_new_findings([human_turn, model_turn], 1, exclude_authors={H}) is True


# ---------------------------------------------------------------------------
# E4: фильтр автора атаки в compute_model_metrics (политика 2)
# ---------------------------------------------------------------------------

def test_hc_attack_excluded_from_upheld_metrics(turn_factory):
    models = ["claude-opus", "codex-gpt"]
    records = [
        turn_factory(seq=1, phase="A", round=0, wave="proposal", author="claude-opus",
                     type="proposal", elements=["E1", "E2"]),
        # Human атакует claude-opus:E1.
        turn_factory(seq=2, author=H, type="attack",
                     position_changes=[{"element": "claude-opus:E1", "action": "disagree",
                                        "refs": [1]}]),
        # Модель соглашается с human-критикой.
        turn_factory(seq=3, author="claude-opus", type="response",
                     position_changes=[{"element": "E1", "action": "agree", "refs": [2]}]),
    ]
    with_human = core.compute_model_metrics(records, models, excluded_attack_authors={H})
    # Политика 2: human-атака НЕ в upheld → survived не снижается, upheld_against = 0.
    assert with_human["claude-opus"]["survived"] == 2
    assert with_human["claude-opus"]["upheld_against"] == 0
    # Контроль: без фильтра та же атака попадает в upheld (старая семантика).
    without_filter = core.compute_model_metrics(records, models)
    assert without_filter["claude-opus"]["upheld_against"] == 1
    assert without_filter["claude-opus"]["survived"] == 1


def test_hc_withdraw_after_human_critique_still_reduces_survived(turn_factory):
    """Суверенный withdraw модели после human-критики снижает survived (E4:
    withdrawn-наполнение не трогаем)."""
    models = ["claude-opus", "codex-gpt"]
    records = [
        turn_factory(seq=1, phase="A", round=0, wave="proposal", author="claude-opus",
                     type="proposal", elements=["E1", "E2"]),
        turn_factory(seq=2, author=H, type="attack",
                     position_changes=[{"element": "claude-opus:E1", "action": "disagree",
                                        "refs": [1]}]),
        turn_factory(seq=3, author="claude-opus", type="response",
                     position_changes=[{"element": "E1", "action": "withdraw", "refs": [2]}]),
    ]
    metrics = core.compute_model_metrics(records, models, excluded_attack_authors={H})
    assert metrics["claude-opus"]["survived"] == 1  # вычет ровно один (withdraw)
    assert metrics["claude-opus"]["upheld_against"] == 0


def test_hc_metrics_default_unchanged(turn_factory):
    """Default-путь (excluded_attack_authors=None) побайтово по поведению прежний."""
    records = [
        turn_factory(seq=1, phase="A", round=0, wave="proposal", author="claude-opus",
                     type="proposal", elements=["E1"]),
        turn_factory(seq=2, author="codex-gpt", type="attack",
                     position_changes=[{"element": "claude-opus:E1", "action": "disagree",
                                        "refs": [1]}]),
        turn_factory(seq=3, author="claude-opus", type="response",
                     position_changes=[{"element": "E1", "action": "agree", "refs": [2]}]),
    ]
    assert core.compute_model_metrics(records, ["claude-opus", "codex-gpt"]) == \
        core.compute_model_metrics(records, ["claude-opus", "codex-gpt"],
                                   excluded_attack_authors=None)


# ---------------------------------------------------------------------------
# E5: пауза wall-clock (paused_sec, НЕ сдвиг started_at)
# ---------------------------------------------------------------------------

def test_hc_wall_clock_paused_sec_stops_budget():
    started = (datetime.now(UTC) - timedelta(seconds=100)).isoformat()
    budget, warn = 120, 90
    assert core.wall_clock_status(started, budget_sec=budget, warn_at_sec=warn) == "warn"
    # Пауза вычитается из elapsed: 100 - 50 = 50 < 90 → ok.
    assert core.wall_clock_status(started, budget_sec=budget, warn_at_sec=warn,
                                  paused_sec=50) == "ok"
    # Пауза больше elapsed не уводит в отрицательное (clamp 0).
    assert core.wall_clock_status(started, budget_sec=budget, warn_at_sec=warn,
                                  paused_sec=10_000) == "ok"
    # Default paused_sec=0 — поведение прежнее.
    assert core.wall_clock_status(started, budget_sec=budget, warn_at_sec=warn,
                                  paused_sec=0) == "warn"


# ---------------------------------------------------------------------------
# E8: поправка предиката пропуска фазы D
# ---------------------------------------------------------------------------

def test_hc_phase_d_skip_forbidden_only_after_actual_b_turn(turn_factory):
    details = {"source": "claude-opus", "elements": 2, "sources": ["claude-opus"]}
    records = []
    # Режим включён, но human-ходов в B не было — обычный предикат сохраняется.
    skip, out = core.phase_d_skip_with_human(True, details, records, True)
    assert skip is True and out is details
    # Фактический human-ход в B — skip запрещён (E8).
    records.append(turn_factory(seq=1, phase="B", round=1, wave="attack",
                                author=H, type="attack"))
    skip, out = core.phase_d_skip_with_human(True, details, records, True)
    assert skip is False
    assert out.get("human_override") is True
    assert "E8" in out["reason"]
    # Режим выключен — предикат не меняется даже при human-записях.
    skip, out = core.phase_d_skip_with_human(True, details, records, False)
    assert skip is True
    # skip=False изначально — поправка ничего не делает.
    skip, out = core.phase_d_skip_with_human(False, {"reason": "обычный"}, records, True)
    assert skip is False and out == {"reason": "обычный"}


# ---------------------------------------------------------------------------
# E10: human-critic состояние в watch overview
# ---------------------------------------------------------------------------

def test_hc_watch_view_states():
    from datetime import UTC, datetime, timedelta

    import consilium_watch as watch

    assert watch.human_critic_view({}) is None
    assert watch.human_critic_view({"human_critic": {"enabled": False}}) is None

    since = (datetime.now(UTC) - timedelta(seconds=30)).isoformat()
    session = {
        "wall_clock": {"paused_sec": 12.0},
        "human_critic": {"enabled": True, "state": "awaiting_human",
                         "awaiting_since": since, "wait_cap_sec": 1800, "turns": [5]},
    }
    view = watch.human_critic_view(session)
    assert "awaiting_human" in view and "ходов: 1" in view and "ждём human-ход" in view

    session["human_critic"]["state"] = "awaiting_moderator_decision"
    assert "РЕШЕНИЕ МОДЕРАТОРА" in watch.human_critic_view(session)

    session["human_critic"] = {"enabled": True, "withdrawn": True,
                               "withdraw_reason": "ушёл", "turns": []}
    assert "свёрнут" in watch.human_critic_view(session)


# ---------------------------------------------------------------------------
# Rev F-001/F-003: атомарная фиксация human-хода (check+append под flock)
# ---------------------------------------------------------------------------

def test_hc_append_human_turn_atomic_one_per_wave(tmp_path, turn_factory):
    first = core.append_human_turn(tmp_path, turn_factory(
        phase="B", round=1, wave="attack", author=H, type="attack",
        content="первый"), "B", 1, "attack")
    assert first["seq"] == 1
    with pytest.raises(core.ProtocolError, match="максимум 1 human-ход"):
        core.append_human_turn(tmp_path, turn_factory(
            phase="B", round=1, wave="attack", author=H, type="attack",
            content="второй"), "B", 1, "attack")
    # Дубликат seq не появился.
    assert len(core.read_transcript(tmp_path)) == 1
    # Ход модели той же волны — обычным append_record (human-функция для human).
    third = core.append_record(tmp_path, turn_factory(
        phase="B", round=1, wave="attack", author="claude-opus", type="attack",
        content="модель"))
    assert third["seq"] == 2
    # Human-ход на ДРУГУЮ атакующую волну — разрешён.
    fourth = core.append_human_turn(tmp_path, turn_factory(
        phase="D", round=1, wave="redteam", author=H, type="redteam_attack",
        content="редтим"), "D", 1, "redteam")
    assert fourth["seq"] == 3


def test_hc_append_record_concurrent_seq_allocation(tmp_path, turn_factory):
    """F-003 (rev): параллельные append из разных процессов не дают дубликатов seq."""
    import multiprocessing

    def worker(seq_dir, scripts_dir, n):
        import sys
        sys.path.insert(0, str(scripts_dir))
        import consilium_core as wcore
        for _ in range(n):
            wcore.append_record(seq_dir, {"type": "system", "author": "moderator",
                                          "content": "x"})

    scripts_dir = Path(__file__).resolve().parents[1] / "scripts"
    procs = [multiprocessing.Process(target=worker, args=(tmp_path, scripts_dir, 5))
             for _ in range(3)]
    for p in procs:
        p.start()
    for p in procs:
        p.join(timeout=60)
        assert p.exitcode == 0
    seqs = [r["seq"] for r in core.read_transcript(tmp_path)]
    assert sorted(seqs) == list(range(1, 16))  # 15 записей, seq без дыр и дублей


# ---------------------------------------------------------------------------
# Rev F-008/F-018: вложенная валидация structured human-хода
# ---------------------------------------------------------------------------

def test_hc_structured_nested_validation():
    base = _valid_payload()
    # action вне enum
    with pytest.raises(core.ProtocolError, match="action"):
        core.validate_human_turn({**base, "structured": {
            "position_changes": [{"element": "M1:E2", "action": "approve", "refs": []}]}},
            protected_ids=[])
    # refs не целые seq
    with pytest.raises(core.ProtocolError, match="seq"):
        core.validate_human_turn({**base, "structured": {
            "position_changes": [{"element": "M1:E2", "action": "disagree",
                                  "refs": ["9"]}]}}, protected_ids=[])
    with pytest.raises(core.ProtocolError, match="refs"):
        core.validate_human_turn({**base, "refs": [0]}, protected_ids=[])
    # неизвестное поле structured
    with pytest.raises(core.ProtocolError, match="неизвестные поля"):
        core.validate_human_turn({**base, "structured": {"evil": True}}, protected_ids=[])
    # чеклист у human-critic запрещён (нет роли)
    with pytest.raises(core.ProtocolError, match="risk_checklist"):
        core.validate_human_turn({**base, "structured": {
            "risk_checklist_responses": [{"item_id": "X", "verdict": "hit",
                                          "note": "текст"}]}}, protected_ids=[])
    # borrowed.source_ref — целый seq
    with pytest.raises(core.ProtocolError, match="source_ref"):
        core.validate_human_turn({**base, "structured": {
            "borrowed": [{"element": "E7", "source_ref": "12"}]}}, protected_ids=[])
    # валидный полный structured проходит
    turn = core.validate_human_turn({**base, "structured": {
        "elements": [], "new_findings": [{"id": "F-1", "text": "находка"}],
        "position_changes": [{"element": "M1:E2", "action": "refine", "refs": [3]}],
        "borrowed": [{"element": "E7", "source_ref": 2}]}}, protected_ids=[])
    assert turn["structured"]["borrowed"][0]["source_ref"] == 2


# ---------------------------------------------------------------------------
# Rev F-013: санитизация ANSI/OSC на выводе watch
# ---------------------------------------------------------------------------

def test_hc_watch_sanitize_terminal():
    import consilium_watch as watch

    payload = "чисто \x1b[31mкрасный\x1b[0m и \x1b]8;;http://evil\x07link\x1b]8;;\x07 + \x07bell"
    cleaned = watch.sanitize_terminal(payload)
    assert "\x1b" not in cleaned and "\x07" not in cleaned
    assert "красный" in cleaned and "link" in cleaned
    assert watch.sanitize_terminal("строка\nс\tтабом") == "строка\nс\tтабом"
    # render_turn санитизирует контент записи
    record = {"seq": 1, "type": "attack", "phase": "B", "round": 1, "wave": "attack",
              "ts": "t", "content": "атака \x1b[2J\x1b[H с очисткой экрана"}
    rendered = watch.render_turn(record)
    assert "\x1b" not in rendered and "с очисткой экрана" in rendered
