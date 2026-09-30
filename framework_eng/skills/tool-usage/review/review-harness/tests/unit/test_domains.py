"""HU-D — переехавшие предикатные тесты доменных пакетов и чеклистов (RVSW-01, T-03).

Перенесены из `agent-consilium/tests/unit/test_cons03_core.py` без правки
логики (ASM-01 — только импорты/пути; оригиналы не тронуты, T-06 переведёт их
на re-export shim'ы):
- AC-E01: загрузка/валидация domains.yaml, дословный парсинг, fail-closed;
- AC-E05 (unit): таблица вырожденных случаев чеклиста.

Точечные адаптации границы (не логика): ROLE_CATALOG остаётся в консилиуме —
здесь зафиксирован литералом; ProtocolError консилиума → DomainsError harness.
"""
from __future__ import annotations

from pathlib import Path

import pytest

import domains

DOMAINS_PATH = Path(__file__).resolve().parents[2] / "domains.yaml"

# Метка туров роя (пакет code-review, RVSW-01 TD §5.8): applies_to: [tour1]
# валидируется через параметр wave_types — CHECKLIST_WAVE_TYPES консилиума
# НЕ расширяется (граница владения; рой — swarm_core.SWARM_WAVE_TYPES).
SWARM_TOUR_TYPES = {"tour1"}
ALL_WAVE_TYPES = domains.CHECKLIST_WAVE_TYPES | SWARM_TOUR_TYPES

# Литерал исторического ROLE_CATALOG консилиума (остаётся в ядре консилиума);
# боевой domains.yaml обязан воспроизводить его дословно и по порядку.
CONSILIUM_ROLE_CATALOG = ["security", "architecture", "pragmatics", "эксплуатация"]


# ---------- AC-E01: загрузка и валидация domains.yaml ----------

def test_ace01_load_real_domains_yaml():
    """AC-E01: боевой domains.yaml валиден; default-домен architecture, состав И
    ПОСЛЕДОВАТЕЛЬНОСТЬ ролей дословно = ROLE_CATALOG."""
    registry = domains.load_domains(DOMAINS_PATH, wave_types=ALL_WAVE_TYPES)
    assert domains.validate_domains(registry, wave_types=ALL_WAVE_TYPES) == []
    ids = [d["id"] for d in registry["domains"]]
    assert ids == ["architecture", "requirements", "security-compliance",
                   "data-schema-evolution", "incident-postmortem", "design-direction",
                   "code-review"]
    arch = registry["domains"][0]
    assert [r["id"] for r in arch["roles"]] == CONSILIUM_ROLE_CATALOG  # дословно и по порядку
    requirements = registry["domains"][1]
    for role in requirements["roles"]:
        item_ids = {item["item_id"] for item in role["risk_checklist"]}
        assert "claim-evidence-ref" in item_ids
    critical_partner = next(r for r in requirements["roles"] if r["id"] == "критический-партнёр")
    assert "first-proposal-anchoring" in {
        item["item_id"] for item in critical_partner["risk_checklist"]
    }
    # applies_to унифицировано (I-01): только enum-значения своего инструмента —
    # консилиумные пакеты ⊆ CHECKLIST_WAVE_TYPES, пакет роя ⊆ SWARM_TOUR_TYPES
    for domain in registry["domains"]:
        allowed = (SWARM_TOUR_TYPES if domain["id"] == "code-review"
                   else domains.CHECKLIST_WAVE_TYPES)
        for role in domain["roles"]:
            for item in role["risk_checklist"]:
                assert set(item["applies_to"]) <= allowed


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
    registry = domains.load_domains(path)
    assert domains.validate_domains(registry) == []
    role = registry["domains"][0]["roles"][0]
    assert role["lens"] == "Линза: с двоеточием и «кавычками»"
    assert role["risk_checklist"][0]["text"] == "«Пункт: дословно, с двоеточием»"
    assert role["risk_checklist"][0]["applies_to"] == ["proposal", "attack"]
    assert registry["domains"][0]["evidence_requirements"] == ["требование: со ссылкой"]


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
        return domains.validate_domains(domains.parse_domains_yaml(text))

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
    with pytest.raises(domains.DomainsError, match="domains.yaml"):
        domains.load_domains(tmp_path / "missing-domains.yaml")


# ---------- AC-E05 (unit): таблица вырожденных случаев чеклиста ----------

def _arch_security_role():
    registry = domains.load_domains(DOMAINS_PATH, wave_types=ALL_WAVE_TYPES)
    return registry["domains"][0]["roles"][0]  # security (architecture pack)


def test_ace05_checklist_validation_table():
    """AC-E05 (unit): все строки таблицы вырожденных случаев E-4."""
    role = _arch_security_role()
    all_three = [
        {"item_id": "overengineering", "verdict": "clear"},
        {"item_id": "bounded-context-erosion", "verdict": "clear"},
        {"item_id": "first-proposal-anchoring", "verdict": "clear"},
    ]
    # proposal: applicable = 2 пункта (anchoring не применим) — покрытие по applies_to
    assert domains.validate_checklist_responses(all_three[:2], role, "proposal") == []
    # attack: нужны все 3
    assert domains.validate_checklist_responses(all_three, role, "attack") == []
    # неполное покрытие по applies_to
    assert domains.validate_checklist_responses(all_three[:2], role, "attack")
    # лишний item_id вне каталога роли
    assert domains.validate_checklist_responses(
        all_three + [{"item_id": "typo-item", "verdict": "clear"}], role, "attack")
    # дубликат с конфликтующими verdict
    conflict = all_three + [{"item_id": "overengineering", "verdict": "hit", "note": "есть риск"}]
    assert domains.validate_checklist_responses(conflict, role, "attack")
    # дубликат с ОДИНАКОВЫМ verdict — не конфликт
    same = all_three + [{"item_id": "overengineering", "verdict": "clear"}]
    assert domains.validate_checklist_responses(same, role, "attack") == []
    # verdict вне lowercase-enum
    bad_enum = [dict(r) for r in all_three]
    bad_enum[0] = {"item_id": "overengineering", "verdict": "HIT", "note": "есть"}
    assert domains.validate_checklist_responses(bad_enum, role, "attack")
    # hit/na без валидного note
    for note in (None, "", "  ", "-", "ab"):
        bad_note = [dict(r) for r in all_three]
        bad_note[0] = {"item_id": "overengineering", "verdict": "hit", "note": note}
        assert domains.validate_checklist_responses(bad_note, role, "attack"), f"note={note!r}"
        bad_na = [dict(r) for r in all_three]
        bad_na[0] = {"item_id": "overengineering", "verdict": "na", "note": note}
        assert domains.validate_checklist_responses(bad_na, role, "attack"), f"na note={note!r}"
    # hit/na с валидным note; clear note не требует
    good = [
        {"item_id": "overengineering", "verdict": "hit", "note": "сложность выше риска"},
        {"item_id": "bounded-context-erosion", "verdict": "na", "note": "неприменимо здесь"},
        {"item_id": "first-proposal-anchoring", "verdict": "clear"},
    ]
    assert domains.validate_checklist_responses(good, role, "attack") == []
