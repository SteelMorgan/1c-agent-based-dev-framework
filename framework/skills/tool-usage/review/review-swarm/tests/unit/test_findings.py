"""SU-F — схема находки и механическая валидация location (RVSW-01, T-08, FR-05/AC-05, TD §5.1).

Покрытие test-plan SU-F01..F05:
- SU-F01: валидная находка (полная схема TD §5.1, location резолвится в tmp-дерево
  проверяемого набора) принимается, получает сквозной `F-NNN` от ядра;
- SU-F02: находка без location / путь вне проверяемого набора / строки за границами
  файла — отклонение ДО учёта и дедупа + запись отклонения (журнал сессии);
- SU-F03: таблица severity полна P1..P5: P1=blocker/P2=major/P3=minor — в метрику
  ценности, P4=nit — отдельный счётчик, P5=note/question — без метрики;
- SU-F04: out-of-lens находки принимаются и тегируются (in_lens=False);
- SU-F05: author_id в транспорте ядра — реальный id (traceability); участникам
  туров 2–4 — anon_id (граница анонимизации, TD §5.2).
"""
from __future__ import annotations

import json

import pytest

import swarm_core

CHECKED_PATHS = ["services/x/y.py", "README.md"]


@pytest.fixture
def checked_set(tmp_path):
    """tmp-дерево проверяемого набора (diff + focused paths): y.py — 200 строк,
    README.md — 2 строки; checked_set — снимок {нормализованный путь: число строк}."""
    root = tmp_path / "review-set"
    (root / "services/x").mkdir(parents=True)
    (root / "services/x/y.py").write_text(
        "".join(f"line {i}\n" for i in range(1, 201)), encoding="utf-8"
    )
    (root / "README.md").write_text("one\ntwo\n", encoding="utf-8")
    return swarm_core.checked_set_from_paths(root, CHECKED_PATHS)


def _raw(**over):
    base = {
        "location": {"path": "services/x/y.py", "line_start": 120, "line_end": 135},
        "category": "correctness",
        "severity": "P2",
        "in_lens": True,
        "claim": "деление на ноль при пустом списке",
        "evidence": "services/x/y.py:127 — total / len(items)",
        "rationale": "свободный текст",
    }
    base.update(over)
    return base


# ---------- SU-F01: валидная находка принята, сквозной F-NNN ----------

def test_su_f01_valid_finding_accepted_with_sequential_id(checked_set):
    """SU-F01: полная схема TD §5.1 через fenced-блок → принята, F-001 от ядра."""
    text = (
        "Пояснение участника свободным текстом.\n"
        "```swarm-structured\n"
        + json.dumps({"findings": [_raw()]}, ensure_ascii=False)
        + "\n```\n"
    )
    raw_findings, warning = swarm_core.parse_findings_block(text)
    assert warning is None
    accepted, rejected = swarm_core.validate_findings(
        raw_findings, checked_set, author_id="claude-opus"
    )
    assert rejected == []
    assert len(accepted) == 1
    finding = accepted[0]
    assert finding["finding_id"] == "F-001"
    assert finding["author_id"] == "claude-opus"
    assert finding["location"] == {
        "path": "services/x/y.py", "line_start": 120, "line_end": 135,
    }
    assert finding["category"] == "correctness"
    assert finding["severity"] == "P2"
    assert finding["in_lens"] is True


def test_su_f01_ids_sequential_across_participants(checked_set):
    """SU-F01: нумерация F-NNN сквозная внутри сессии (start_index — продолжение)."""
    accepted_a, _ = swarm_core.validate_findings(
        [_raw(), _raw()], checked_set, author_id="claude-opus"
    )
    accepted_b, _ = swarm_core.validate_findings(
        [_raw()], checked_set, author_id="codex-gpt", start_index=3
    )
    assert [f["finding_id"] for f in accepted_a] == ["F-001", "F-002"]
    assert [f["finding_id"] for f in accepted_b] == ["F-003"]


def test_su_f01_point_location_defaults_range_to_line(checked_set):
    """SU-F01: точечная location (без line_end) нормализуется в диапазон line..line."""
    accepted, rejected = swarm_core.validate_findings(
        [_raw(location={"path": "services/x/y.py", "line_start": 42})],
        checked_set, author_id="claude-opus",
    )
    assert rejected == []
    assert accepted[0]["location"]["line_end"] == 42


# ---------- SU-F02: отклонение ДО учёта и дедупа ----------

def test_su_f02_missing_location_rejected(checked_set):
    """SU-F02: находка без location отклоняется (граница режимов, RISK-09)."""
    raw = _raw()
    del raw["location"]
    accepted, rejected = swarm_core.validate_findings(
        [raw], checked_set, author_id="claude-opus"
    )
    assert accepted == []
    assert rejected[0]["reason"] == "missing_location"


def test_su_f02_path_outside_checked_set_rejected(checked_set):
    """SU-F02: галлюцинированный путь (вне проверяемого набора) — отклонение."""
    accepted, rejected = swarm_core.validate_findings(
        [_raw(location={"path": "services/x/ghost.py", "line_start": 1})],
        checked_set, author_id="claude-opus",
    )
    assert accepted == []
    assert rejected[0]["reason"] == "invalid_location"
    assert "path_not_in_set" in rejected[0]["detail"]


def test_su_f02_lines_out_of_range_rejected(checked_set):
    """SU-F02: line_start/line_end за границами реального файла — отклонение."""
    for loc in (
        {"path": "services/x/y.py", "line_start": 0},          # строки с 1
        {"path": "services/x/y.py", "line_start": 201},        # за концом файла
        {"path": "services/x/y.py", "line_start": 190, "line_end": 250},
        {"path": "services/x/y.py", "line_start": 130, "line_end": 120},  # инверсия
        {"path": "README.md", "line_start": 3},                # файла 2 строки
    ):
        accepted, rejected = swarm_core.validate_findings(
            [_raw(location=loc)], checked_set, author_id="claude-opus"
        )
        assert accepted == [], f"принята невалидная location: {loc}"
        assert rejected[0]["reason"] == "invalid_location"


def test_su_f02_rejection_recorded_for_session_journal(checked_set):
    """SU-F02: отклонение фиксируется записью (индекс, автор, причина) — payload
    журнала сессии; отклонённые находки сквозной нумерации не получают."""
    accepted, rejected = swarm_core.validate_findings(
        [_raw(location={"path": "ghost.py", "line_start": 1}), _raw()],
        checked_set, author_id="claude-opus",
    )
    assert [f["finding_id"] for f in accepted] == ["F-001"]  # нумеруются только принятые
    record = rejected[0]
    assert record["index"] == 0
    assert record["author_id"] == "claude-opus"
    assert record["reason"] == "invalid_location"


def test_su_f02_missing_required_fields_rejected(checked_set):
    """SU-F02/AC-05: обязательные поля — category/severity/claim/evidence/in_lens
    + автор; отсутствие любого — отклонение fail-closed."""
    for field in ("category", "severity", "claim", "evidence", "in_lens"):
        raw = _raw()
        del raw[field]
        accepted, rejected = swarm_core.validate_findings(
            [raw], checked_set, author_id="claude-opus"
        )
        assert accepted == [], f"принята находка без {field}"
        assert rejected[0]["reason"] == f"missing_field:{field}"
    # без автора (ни в записи, ни параметром ядра)
    accepted, rejected = swarm_core.validate_findings([_raw()], checked_set)
    assert accepted == []
    assert rejected[0]["reason"] == "missing_field:author_id"


def test_su_f02_unknown_category_and_severity_rejected(checked_set):
    """SU-F02: category вне таксономии / severity вне P1..P5 — fail-closed."""
    for raw in (_raw(category="ux"), _raw(severity="critical"), _raw(severity="P0")):
        accepted, rejected = swarm_core.validate_findings(
            [raw], checked_set, author_id="claude-opus"
        )
        assert accepted == []
        assert rejected[0]["reason"] in ("invalid_category", "invalid_severity")


# ---------- SU-F03: таблица severity полна ----------

def test_su_f03_severity_table_complete():
    """SU-F03: P1=blocker, P2=major, P3=minor — в метрику ценности; P4=nit —
    отдельный счётчик; P5=note/question — информационный, без метрики."""
    assert swarm_core.SEVERITY_LABELS == {
        "P1": "blocker", "P2": "major", "P3": "minor",
        "P4": "nit", "P5": "note",
    }
    for code in ("P1", "P2", "P3"):
        assert swarm_core.severity_metric(code) == "value"
    assert swarm_core.severity_metric("P4") == "nit"   # отдельный счётчик
    assert swarm_core.severity_metric("P5") is None    # без метрики


def test_su_f03_all_severities_accepted_in_schema(checked_set):
    """SU-F03: все пять severity — валидные значения схемы (P4/P5 не запрещены)."""
    accepted, rejected = swarm_core.validate_findings(
        [_raw(severity=f"P{i}") for i in range(1, 6)],
        checked_set, author_id="claude-opus",
    )
    assert rejected == []
    assert [f["severity"] for f in accepted] == ["P1", "P2", "P3", "P4", "P5"]


# ---------- SU-F04: тег in-lens / out-of-lens ----------

def test_su_f04_out_of_lens_accepted_and_tagged(checked_set):
    """SU-F04: находка вне линзы принимается (чеклист не ограничивает модель,
    решение №7) и сохраняет тег in_lens=False для статистики FR-12."""
    accepted, rejected = swarm_core.validate_findings(
        [_raw(in_lens=False, category="performance")],
        checked_set, author_id="claude-opus",
    )
    assert rejected == []
    assert accepted[0]["in_lens"] is False


# ---------- SU-F05: граница анонимизации автора ----------

def test_su_f05_core_transport_keeps_real_author_participants_get_anon(checked_set):
    """SU-F05: в транспорте ядра author_id — реальный id (traceability, re-review);
    в payload участников туров 2–4 — anon_id из anon_map; finding_id (сквозной)
    идентичности не раскрывает."""
    accepted, _ = swarm_core.validate_findings(
        [_raw()], checked_set, author_id="claude-opus"
    )
    finding = accepted[0]
    assert finding["author_id"] == "claude-opus"  # транспорт ядра — реальный id
    anon = swarm_core.anonymize_finding(finding, {"claude-opus": "M2"})
    assert anon["author_id"] == "M2"
    assert anon["finding_id"] == finding["finding_id"]
    assert finding["author_id"] == "claude-opus"  # оригинал не мутирован
    with pytest.raises(ValueError):
        swarm_core.anonymize_finding(finding, {"codex-gpt": "M1"})
