"""Пути к адаптерам harness для тестов консилиума (RVSW-01, TD §14.1).

Вынесено из `conftest.py` в отдельный модуль с уникальным именем: bare
`from conftest import ...` неоднозначен при комбинированном прогоне нескольких
наборов тестов в одном pytest-процессе (конфликт basename `conftest`).
conftest.py импортирует отсюда же — единый источник путей.
"""
from __future__ import annotations

from pathlib import Path

TESTS_DIR = Path(__file__).resolve().parent
SKILL_DIR = TESTS_DIR.parent
HARNESS_SCRIPTS = SKILL_DIR.parent / "review-harness" / "scripts"

ADAPTER_PATHS = {
    "claude": HARNESS_SCRIPTS / "adapters" / "claude_opus_review.py",
    "codex": HARNESS_SCRIPTS / "adapters" / "codex_review.py",
    "kimi": HARNESS_SCRIPTS / "adapters" / "kimi_review.py",
}
