"""Unit-слой CONS-03 (spec-delta): реестр domains.yaml, чеклист-валидация,
предикат findings, назначение ролей по домену. Trace: AC-E01, AC-E05(unit), AC-E07, AC-E08.
"""
from __future__ import annotations

from pathlib import Path

import pytest

import consilium_core as core

DOMAINS_PATH = Path(__file__).resolve().parents[3] / "review-harness" / "domains.yaml"


# ---------- AC-E01: загрузка и валидация domains.yaml ----------

# test_ace01_load_real_domains_yaml ПЕРЕНЕСЁН в review-harness/tests/unit/
# test_domains.py (RVSW-01, TD §3.2): боевой domains.yaml переехал в harness и
# содержит пакет code-review роя; harness-копия теста фиксирует новый состав
# реестра (6 доменов) и порядок ролей = исторический ROLE_CATALOG.
# Здесь остаются проверки shim'ов консилиума на shared-реестре и схеме E-1.


def test_ace01_verbatim_parsing(tmp_path):
    """AC-E01: пункты с двоеточиями и кавычками «» парсятся дословно."""
    path = tmp_path / "domains.yaml"
    path.write_text(
        "version: 1\n"
        "domains:\n"
        "  - id: test-domain\n"
        "    description: Тест: с двоеточием\n"
        "    roles:\n"
        "      - id: r1\n"
        "        title: r1\n"
        "        lens: Линза: с двоеточием и «кавычками»\n"
        "        risk_checklist:\n"
        "          - item_id: i1\n"
        "            text: «Пункт: дословно, с двоеточием»\n"
        "            applies_to: [proposal, attack]\n"
        "      - id: r2\n"
        "        title: r2\n"
        "        lens: r2\n"
        "        risk_checklist:\n"
        "          - item_id: i2\n"
        "            text: t2\n"
        "            applies_to: [proposal]\n"
        "    evidence_requirements:\n"
        "      - требование: со ссылкой\n",
        encoding="utf-8",
    )
    domains = core.load_domains(path)
    assert core.validate_domains(domains) == []
    role = domains["domains"][0]["roles"][0]
    assert role["lens"] == "Линза: с двоеточием и «кавычками»"
    assert role["risk_checklist"][0]["text"] == "«Пункт: дословно, с двоеточием»"
    assert role["risk_checklist"][0]["applies_to"] == ["proposal", "attack"]
    assert domains["domains"][0]["evidence_requirements"] == ["требование: со ссылкой"]


def test_ace01_invalid_domains_rejected(tmp_path):
    """AC-E01: пропуск полей, дубликаты id ролей/item_id, <2 ролей, пустой чеклист,
    applies_to вне enum — fail-closed с перечнем ошибок."""
    base = (
        "version: 1\ndomains:\n  - id: d\n    description: d\n    roles:\n"
        "      - id: r1\n        title: t\n        lens: l\n        risk_checklist:\n"
        "          - item_id: i1\n            text: t\n            applies_to: [proposal]\n"
        "      - id: r2\n        title: t\n        lens: l\n        risk_checklist:\n"
        "          - item_id: i2\n            text: t\n            applies_to: [attack]\n"
        "    evidence_requirements:\n      - e\n"
    )

    def errors(text):
        return core.validate_domains(core.parse_domains_yaml(text))

    assert errors(base) == []
    # дубликат id роли
    assert errors(base.replace("- id: r2", "- id: r1"))
    # дубликат item_id внутри чеклиста одной роли
    dup_in_role = base.replace(
        "            applies_to: [proposal]\n",
        "            applies_to: [proposal]\n"
        "          - item_id: i1\n"
        "            text: t2\n"
        "            applies_to: [proposal]\n",
        1,
    )
    assert errors(dup_in_role)
    # <2 ролей
    one_role = base.split("      - id: r2")[0] + "    evidence_requirements:\n      - e\n"
    assert errors(one_role)
    # пустой чеклист
    assert errors(base.replace("applies_to: [attack]", "applies_to: []"))
    # applies_to вне enum
    assert errors(base.replace("applies_to: [attack]", "applies_to: [attack, verdict]"))
    # отсутствующий файл — actionable-отказ
    with pytest.raises(core.ProtocolError, match="domains.yaml"):
        core.load_domains(tmp_path / "missing-domains.yaml")


# ---------- AC-E05 (unit): таблица вырожденных случаев чеклиста ----------

def _arch_security_role():
    domains = core.load_domains(DOMAINS_PATH)
    return domains["domains"][0]["roles"][0]  # security (architecture pack)


def test_ace05_checklist_validation_table():
    """AC-E05 (unit): все строки таблицы вырожденных случаев E-4."""
    role = _arch_security_role()
    all_three = [
        {"item_id": "overengineering", "verdict": "clear"},
        {"item_id": "bounded-context-erosion", "verdict": "clear"},
        {"item_id": "first-proposal-anchoring", "verdict": "clear"},
    ]
    # proposal: applicable = 2 пункта (anchoring не применим) — покрытие по applies_to
    assert core.validate_checklist_responses(all_three[:2], role, "proposal") == []
    # attack: нужны все 3
    assert core.validate_checklist_responses(all_three, role, "attack") == []
    # неполное покрытие по applies_to
    assert core.validate_checklist_responses(all_three[:2], role, "attack")
    # лишний item_id вне каталога роли
    assert core.validate_checklist_responses(
        all_three + [{"item_id": "typo-item", "verdict": "clear"}], role, "attack")
    # дубликат с конфликтующими verdict
    conflict = all_three + [{"item_id": "overengineering", "verdict": "hit", "note": "есть риск"}]
    assert core.validate_checklist_responses(conflict, role, "attack")
    # дубликат с ОДИНАКОВЫМ verdict — не конфликт
    same = all_three + [{"item_id": "overengineering", "verdict": "clear"}]
    assert core.validate_checklist_responses(same, role, "attack") == []
    # verdict вне lowercase-enum
    bad_enum = [dict(r) for r in all_three]
    bad_enum[0] = {"item_id": "overengineering", "verdict": "HIT", "note": "есть"}
    assert core.validate_checklist_responses(bad_enum, role, "attack")
    # hit/na без валидного note
    for note in (None, "", "  ", "-", "ab"):
        bad_note = [dict(r) for r in all_three]
        bad_note[0] = {"item_id": "overengineering", "verdict": "hit", "note": note}
        assert core.validate_checklist_responses(bad_note, role, "attack"), f"note={note!r}"
        bad_na = [dict(r) for r in all_three]
        bad_na[0] = {"item_id": "overengineering", "verdict": "na", "note": note}
        assert core.validate_checklist_responses(bad_na, role, "attack"), f"na note={note!r}"
    # hit/na с валидным note; clear note не требует
    good = [
        {"item_id": "overengineering", "verdict": "hit", "note": "сложность выше риска"},
        {"item_id": "bounded-context-erosion", "verdict": "na", "note": "неприменимо здесь"},
        {"item_id": "first-proposal-anchoring", "verdict": "clear"},
    ]
    assert core.validate_checklist_responses(good, role, "attack") == []


# ---------- AC-E07: предикат findings и видимость ответов ----------

def test_ace07_hit_note_counts_as_finding(turn_factory):
    """AC-E07: hit с валидным note → round_has_new_findings; clear/na — нет;
    position_changes/stalemate не затронуты."""
    def record(verdict, note=None):
        responses = [{"item_id": "overengineering", "verdict": verdict}]
        if note is not None:
            responses[0]["note"] = note
        return turn_factory(seq=1, author="p1", round=2, type="attack",
                            structured_extra={"risk_checklist_responses": responses})

    assert core.round_has_new_findings([record("hit", "сложность превышает риск")], 2) is True
    assert core.round_has_new_findings([record("clear")], 2) is False
    assert core.round_has_new_findings([record("na", "неприменимо")], 2) is False
    # hit с невалидным note finding не засчитывается
    assert core.round_has_new_findings([record("hit", "-")], 2) is False
    # stalemate-путь не затронут: PC по-прежнему сбрасывает счётчик
    assert core.update_stalemate(1, freeze=False, pc=True) == 0
    assert core.update_stalemate(1, freeze=False, pc=False) == 2


def test_ace07_render_bundle_excludes_checklist_responses(turn_factory):
    """AC-E07: risk_checklist_responses исключены из payload render_bundle."""
    records = [turn_factory(
        seq=1, author="p1", type="attack",
        structured_extra={"risk_checklist_responses": [
            {"item_id": "overengineering", "verdict": "hit", "note": "заметка"}]},
    )]
    bundle = core.render_bundle(records, {"p1": "M1"})
    assert "risk_checklist_responses" not in bundle
    assert "overengineering" not in bundle


# ---------- AC-E08: назначение ролей на каталоге домена ----------

def test_ace08_roles_use_domain_catalog():
    """AC-E08: assign_roles/assign_phase_d_role работают на каталоге домена сессии."""
    catalog = ["атакующий", "защитник", "compliance", "эксплуатация"]
    ids = ["p1", "p2", "p3"]
    roles = core.assign_roles(ids, role_catalog=catalog)
    assert set(roles.values()) <= set(catalog)
    assert len(set(roles.values())) == len(ids)
    assert roles == core.assign_roles_round_robin(ids, role_catalog=catalog)
    # фаза D: новая роль внутри домена; fallback — внутри домена
    role_d = core.assign_phase_d_role("p1", ["атакующий"], [("защитник", 1)], role_catalog=catalog)
    assert role_d != "атакующий"
    assert role_d in catalog
    fallback = core.assign_phase_d_role(
        "p1", list(catalog), [("защитник", 1), ("compliance", 2)], role_catalog=catalog)
    assert fallback in catalog


# ---------- AC-E05 (unit): схема structured с risk_checklist_responses ----------

def test_ace05_structured_schema_includes_checklist(structured_block):
    """AC-E05: парсер принимает risk_checklist_responses; схема — 5 ключей."""
    data = {"elements": ["E1"], "new_findings": [], "position_changes": [], "borrowed": [],
            "risk_checklist_responses": [{"item_id": "i1", "verdict": "clear"}]}
    structured, warning = core.parse_structured_block("текст\n" + structured_block(data))
    assert warning is None
    assert structured["risk_checklist_responses"] == [{"item_id": "i1", "verdict": "clear"}]
    assert set(structured) == {"elements", "new_findings", "position_changes", "borrowed",
                               "risk_checklist_responses"}
