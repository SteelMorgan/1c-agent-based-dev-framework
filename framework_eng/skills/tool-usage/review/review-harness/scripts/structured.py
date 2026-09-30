"""Structured-блоки и анонимизация — переезд из `consilium_core.py` (RVSW-01, TD §3.2).

Перенесены без изменения логики (ASM-01): STRUCTURED_BLOCK_RE, EMPTY_STRUCTURED,
parse_structured_block, create_anon_map, anonymize_text, estimate_tokens.

Единственное обобщение (TD §3.2: «общий механизм fenced-блоков; схемы полей —
в инструментах»): parse_structured_block принимает fence_tag/empty_schema
параметрами; дефолты дословно воспроизводят поведение консилиума. Схемы полей
ходов (findings/verdicts/...) определяются инструментами, не harness.
"""
from __future__ import annotations

import json
import random
import re


def structured_block_re(fence_tag: str) -> re.Pattern:
    """Regex fenced-блока для заданного тега (```<tag> ... ```)."""
    return re.compile(
        r"```" + re.escape(fence_tag) + r"\s*\n(?P<body>.*?)\n```", re.DOTALL
    )


# Дефолтный тег/схема — совместимость с форматом хода консилиума (CONS-01..03).
STRUCTURED_BLOCK_RE = structured_block_re("consilium-structured")
EMPTY_STRUCTURED = {"elements": [], "new_findings": [], "position_changes": [], "borrowed": [],
                    "risk_checklist_responses": []}


def parse_structured_block(
    text: str,
    fence_tag: str = "consilium-structured",
    empty_schema: dict | None = None,
) -> tuple[dict, str | None]:
    """Извлекает fenced JSON-блок из ответа участника.

    Отсутствующий/битый блок → пустые structured + warning (ход не отвергается;
    консервативная семантика — предикаты инструмента считают ход «пустым»).
    fence_tag/empty_schema — схема инструмента; дефолт = формат консилиума.
    """
    schema = EMPTY_STRUCTURED if empty_schema is None else empty_schema
    matches = list(structured_block_re(fence_tag).finditer(text or ""))
    if not matches:
        return dict(schema), "structured-блок отсутствует в ответе участника"
    body = matches[-1].group("body")
    try:
        parsed = json.loads(body)
    except json.JSONDecodeError as exc:
        return dict(schema), f"structured-блок не парсится: {exc}"
    if not isinstance(parsed, dict):
        return dict(schema), "structured-блок не является JSON-объектом"
    result = dict(schema)
    for key in schema:
        value = parsed.get(key)
        result[key] = value if isinstance(value, list) else []
    return result, None


def create_anon_map(participant_ids: list[str], seed: int | None = None) -> dict:
    """Случайная перестановка id → M1..Mn; стабильна при фиксированном seed."""
    shuffled = list(participant_ids)
    random.Random(seed).shuffle(shuffled)
    return {pid: f"M{i + 1}" for i, pid in enumerate(shuffled)}


def anonymize_text(text: str, anon_map: dict) -> str:
    """Подмена реальных id на anon_id (длинные id первыми — защита от префиксов)."""
    result = text
    for real_id in sorted(anon_map, key=len, reverse=True):
        result = result.replace(real_id, anon_map[real_id])
    return result


def estimate_tokens(text: str) -> int:
    """Грубая оценка токенов (~4 символа на токен) для context_budget."""
    return max(1, len(text) // 4)
