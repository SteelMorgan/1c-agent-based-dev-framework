"""HU-S — переехавшие предикатные тесты structured-парсера и анонимизации (RVSW-01, T-03).

Перенесены из `agent-consilium/tests/unit/` без правки логики (ASM-01 —
меняются только импорты/пути; оригиналы в консилиуме не тронуты, T-06 переведёт
их на re-export shim'ы):
- UT-12 `test_ut12_parse_structured_block` (CONS-01, FR-04, TD 7.4);
- AC-E05 `test_ace05_structured_schema_includes_checklist` (CONS-03);
- anon-часть UT-13 (CONS-01): стабильность anon_map и подмена реальных id.
  Проверка утечки адаптирована с render_bundle на прямой вызов anonymize_text:
  render_bundle/transcript знают о волнах протокола и остаются в консилиуме
  (граница FR-01); тестируемая логика подмены — та же.
"""
from __future__ import annotations

import structured


# ---------- UT-12: парсинг structured-хода (FR-04, TD 7.4) ----------

def test_ut12_parse_structured_block(structured_block):
    """UT-12: валидный блок → поля; битый/отсутствующий → пустые structured + warning, ход не отвергается."""
    data = {"elements": ["E1"], "new_findings": [{"id": "F-01", "text": "x"}],
            "position_changes": [], "borrowed": []}
    parsed, warning = structured.parse_structured_block("текст хода\n" + structured_block(data))
    assert warning is None
    assert parsed["elements"] == ["E1"]
    assert parsed["new_findings"] == [{"id": "F-01", "text": "x"}]

    empty5 = {"elements": [], "new_findings": [], "position_changes": [], "borrowed": [],
              "risk_checklist_responses": []}
    parsed, warning = structured.parse_structured_block("текст\n```consilium-structured\n{broken json\n```")
    assert parsed == empty5
    assert warning  # предупреждение есть, ход не отвергается

    parsed, warning = structured.parse_structured_block("текст вообще без блока")
    assert parsed == empty5
    assert warning


# ---------- AC-E05: схема structured с risk_checklist_responses ----------

def test_ace05_structured_schema_includes_checklist(structured_block):
    """AC-E05: парсер принимает risk_checklist_responses; схема — 5 ключей."""
    data = {"elements": ["E1"], "new_findings": [], "position_changes": [], "borrowed": [],
            "risk_checklist_responses": [{"item_id": "i1", "verdict": "clear"}]}
    parsed, warning = structured.parse_structured_block("текст\n" + structured_block(data))
    assert warning is None
    assert parsed["risk_checklist_responses"] == [{"item_id": "i1", "verdict": "clear"}]
    assert set(parsed) == {"elements", "new_findings", "position_changes", "borrowed",
                           "risk_checklist_responses"}


def test_parse_structured_block_custom_fence_and_schema(structured_block):
    """TD §3.2: общий механизм fenced-блоков — fence-тег и схема полей задаются
    инструментом (рой использует свои схемы, не схему хода консилиума)."""
    schema = {"findings": []}
    data = {"findings": [{"finding_id": "F-001"}]}
    text = "текст\n" + structured_block(data, fence_tag="swarm-structured")
    parsed, warning = structured.parse_structured_block(
        text, fence_tag="swarm-structured", empty_schema=schema)
    assert warning is None
    assert parsed == {"findings": [{"finding_id": "F-001"}]}
    # блок с чужим fence-тегом не виден
    parsed, warning = structured.parse_structured_block(
        text, fence_tag="other-structured", empty_schema=schema)
    assert parsed == {"findings": []}
    assert warning


# ---------- UT-13 (anon-часть): анонимизация (FR-04) ----------

def test_ut13_anon_map_stable_and_no_leak():
    """UT-13 (anon-часть): mapping стабилен в сессии; реальные id подменяются без утечки."""
    ids = ["claude-opus", "codex-gpt", "kimi-k2"]
    anon1 = structured.create_anon_map(ids, seed=42)
    anon2 = structured.create_anon_map(ids, seed=42)
    assert anon1 == anon2
    assert sorted(anon1.values()) == ["M1", "M2", "M3"]

    text = "позиция claude-opus: E1 сильнее; ответ codex-gpt на атаку"
    anon_text = structured.anonymize_text(text, anon1)
    for real_id in ids:
        assert real_id not in anon_text
    assert anon1["claude-opus"] in anon_text
    assert anon1["codex-gpt"] in anon_text


def test_estimate_tokens_budget():
    """estimate_tokens: ~4 символа на токен, минимум 1 (context_budget)."""
    assert structured.estimate_tokens("") == 1
    assert structured.estimate_tokens("abcd" * 10) == 10
