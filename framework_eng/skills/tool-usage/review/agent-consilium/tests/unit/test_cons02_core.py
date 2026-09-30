"""Unit-слой CONS-02 (spec-delta): OPT-1 таймауты, OPT-2 черновик дайджеста,
OPT-3 условная фаза D. Trace: AC-D01, AC-D04, AC-D06.
"""
from __future__ import annotations

from datetime import datetime, timedelta, UTC

import pytest

import adapter_contract as ac
import consilium_core as core


# ---------- AC-D01: предикаты таймаутов (OPT-1) ----------

def test_acd01_timeout_defaults():
    """AC-D01: per-invocation 900; волна = T+240 = 1140; retry на timeout не вводится."""
    assert ac.DEFAULT_TIMEOUT_SEC == 900
    assert ac.WAVE_GRACE_SEC == 120
    assert ac.WAVE_TIMEOUT_SEC == 1140  # T + 2×grace
    # бюджеты вызовов без изменений
    assert core.INVOCATION_HARD_MAX == 31
    assert core.INVOCATION_WARN_MAX == 25


def test_acd01_wall_clock_cap_formula():
    """AC-D01: cap = max(7200, 13×(T+240)+1800); default 16620, warn 75% = 12465;
    нижний пол 7200 при downward-override."""
    budget, warn = core.wall_clock_budget(900)
    assert budget == 16620  # 13 × 1140 + 1800
    assert warn == 12465    # 75%
    budget_down, warn_down = core.wall_clock_budget(60)
    assert budget_down == 7200  # 13×300+1800 = 5700 < 7200 → нижний пол
    assert warn_down == 5400
    budget_up, _ = core.wall_clock_budget(1800)
    assert budget_up == 13 * 2040 + 1800


def test_acd01_wall_clock_status_uses_session_budget():
    """AC-D01: wall_clock_status работает от переданного бюджета (пересчёт от override)."""
    now = datetime.now(UTC)
    started = (now - timedelta(seconds=100)).isoformat()
    assert core.wall_clock_status(started, now=now, budget_sec=200, warn_at_sec=150) == "ok"
    started_warn = (now - timedelta(seconds=160)).isoformat()
    assert core.wall_clock_status(started_warn, now=now, budget_sec=200, warn_at_sec=150) == "warn"
    started_exceeded = (now - timedelta(seconds=201)).isoformat()
    assert core.wall_clock_status(started_exceeded, now=now, budget_sec=200, warn_at_sec=150) == "exceeded"


# ---------- AC-D04: черновик дайджеста (OPT-2) ----------

def _records_for_draft(turn_factory):
    return [
        turn_factory(seq=2, author="claude-opus", phase="A", round=0, wave="proposal",
                     type="proposal", elements=["E1", "E2"], content="модель claude"),
        turn_factory(seq=3, author="codex-gpt", phase="A", round=0, wave="proposal",
                     type="proposal", elements=["E1", "E3"], content="модель codex"),
        turn_factory(seq=5, author="codex-gpt", phase="B", round=1, wave="attack",
                     type="attack", refs=[2],
                     position_changes=[{"element": "claude-opus:E1", "action": "disagree", "refs": [2]}],
                     new_findings=[{"id": "F-01", "text": "риск гонки"}]),
        turn_factory(seq=6, author="claude-opus", phase="B", round=1, wave="response",
                     type="response", refs=[5],
                     position_changes=[{"element": "E1", "action": "withdraw", "refs": [5]}],
                     borrowed=[{"element": "E3", "source_ref": 3}]),
    ]


def test_acd04_draft_extracts_only_facts(turn_factory):
    """AC-D04: черновик извлекает атаки/position_changes/borrowed/findings с refs,
    анонимизирован, полезная нагрузка — только в fenced-блоках participant-extract."""
    records = _records_for_draft(turn_factory)
    anon_map = {"claude-opus": "M1", "codex-gpt": "M2"}
    draft = core.generate_digest_draft(records, anon_map)
    # анонимизация: реальных id нет нигде
    for real_id in ("claude-opus", "codex-gpt"):
        assert real_id not in draft
    assert "M1" in draft and "M2" in draft
    # факты: атака, withdraw, borrowed, finding — с refs
    assert "participant-extract" in draft
    assert "disagree" in draft
    assert "withdraw" in draft
    assert "seq:5" in draft
    assert "E3" in draft  # borrowed
    assert "F-01" in draft
    # полезная нагрузка — только внутри fenced-блоков
    outside = core.draft_template_text(draft)
    assert "disagree" not in outside
    assert "withdraw" not in outside


def test_acd04_template_has_no_evaluative_language(turn_factory):
    """AC-D04: текст шаблона (вне fenced-блоков) свободен от маркеров оценочности
    по именованному словарю (case-insensitive)."""
    records = _records_for_draft(turn_factory)
    anon_map = {"claude-opus": "M1", "codex-gpt": "M2"}
    draft = core.generate_digest_draft(records, anon_map)
    outside = core.draft_template_text(draft).lower()
    for marker in core.EVALUATIVE_MARKERS:
        assert marker.lower() not in outside, f"оценочный маркер в шаблоне: {marker}"
    assert isinstance(core.EVALUATIVE_MARKERS, (list, tuple)) and core.EVALUATIVE_MARKERS


# ---------- AC-D06: формат синтеза и предикат пропуска D (OPT-3) ----------

SYNTH_OK = """# Синтез

- [E1] разделение ядра и доменов (refs: seq:2, seq:5)
- [E3] транспорт через события (refs: seq:3)

## Обоснование исключения вкладов

Вклад kimi-k2 не вошёл: его элементы перекрыты E1 и E3.
"""


def _synthesis_records(turn_factory):
    return [
        turn_factory(seq=1, author="moderator", type="system", content="convene"),
        turn_factory(seq=2, author="claude-opus", phase="A", round=0, wave="proposal",
                     type="proposal", elements=["E1", "E2"]),
        turn_factory(seq=3, author="codex-gpt", phase="A", round=0, wave="proposal",
                     type="proposal", elements=["E3"]),
        turn_factory(seq=4, author="kimi-k2", phase="A", round=0, wave="proposal",
                     type="proposal", elements=["E7"]),
        turn_factory(seq=5, author="claude-opus", phase="B", round=1, wave="attack",
                     type="attack", elements=[]),
    ]


def _participants():
    return [
        {"id": "claude-opus", "state": "active"},
        {"id": "codex-gpt", "state": "active"},
        {"id": "kimi-k2", "state": "active"},
    ]


def test_acd06_parse_synthesis_valid():
    """AC-D06: валидный синтез — элементы с id/текстом/refs."""
    elements = core.parse_synthesis(SYNTH_OK)
    assert len(elements) == 2
    assert elements[0]["id"] == "E1"
    assert elements[0]["refs"] == [2, 5]
    assert elements[1]["id"] == "E3"
    assert elements[1]["refs"] == [3]


def test_acd06_parse_synthesis_rejects_invalid():
    """AC-D06: fail-closed — без элементов; внетекст вне whitelist; диапазоны; без секции исключения."""
    with pytest.raises(core.ProtocolError):
        core.parse_synthesis("# Синтез\n\nНет элементов вообще.\n\n## Обоснование исключения вкладов\nтекст\n")
    with pytest.raises(core.ProtocolError):
        core.parse_synthesis("- [E1] текст (refs: seq:2)\n\nВольный комментарий вне секций.\n\n## Обоснование исключения вкладов\nтекст\n")
    with pytest.raises(core.ProtocolError):
        core.parse_synthesis("- [E1] текст (refs: seq:2-4)\n\n## Обоснование исключения вкладов\nтекст\n")
    with pytest.raises(core.ProtocolError):
        core.parse_synthesis("- [E1] текст (refs: seq:2)\n")  # нет секции исключения вкладов


def test_acd06_skip_predicate_single_source(turn_factory):
    """AC-D06: все элементы от одной живой активной модели-источника → пропуск."""
    records = _synthesis_records(turn_factory)
    synthesis = "- [E1] ядро (refs: seq:2)\n\n## Обоснование исключения вкладов\nтекст\n"
    elements = core.parse_synthesis(synthesis)
    skip, details = core.should_skip_phase_d(elements, records, _participants())
    assert skip is True
    assert details["source"] == "claude-opus"


def test_acd06_skip_predicate_conservative(turn_factory):
    """AC-D06: любая неопределённость → фаза D обязательна."""
    records = _synthesis_records(turn_factory)

    def check(synthesis, participants=None):
        elements = core.parse_synthesis(synthesis)
        skip, _ = core.should_skip_phase_d(elements, records, participants or _participants())
        return skip

    exclusion = "\n\n## Обоснование исключения вкладов\nтекст\n"
    # два источника
    assert check(f"- [E1] ядро (refs: seq:2)\n- [E3] транспорт (refs: seq:3){exclusion}") is False
    # элемент без refs — отклоняется fail-closed уже парсером (раньше предиката)
    with pytest.raises(core.ProtocolError):
        core.parse_synthesis(f"- [E1] ядро (){exclusion}")
    # неразрешимый ref
    assert check(f"- [E1] ядро (refs: seq:99){exclusion}") is False
    # membership: E3 не объявлен в записи seq:2 (там E1, E2)
    assert check(f"- [E3] текст (refs: seq:2){exclusion}") is False
    # ref на запись модератора
    assert check(f"- [E1] текст (refs: seq:1){exclusion}") is False
    # источник unresponsive
    unresponsive = [
        {"id": "claude-opus", "state": "unresponsive"},
        {"id": "codex-gpt", "state": "active"},
        {"id": "kimi-k2", "state": "active"},
    ]
    assert check(f"- [E1] ядро (refs: seq:2){exclusion}", unresponsive) is False
    # источник killed (вне alive_model_ids)
    killed = [
        {"id": "claude-opus", "state": "killed"},
        {"id": "codex-gpt", "state": "active"},
        {"id": "kimi-k2", "state": "active"},
    ]
    assert check(f"- [E1] ядро (refs: seq:2){exclusion}", killed) is False
