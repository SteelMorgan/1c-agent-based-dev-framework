"""HU-B01..B02 — исполняемое правило границы harness (RVSW-01, FR-01, AC-01; test-plan §3.2).

Правило границы: harness не содержит ничего, знающего о фазах консилиума или
турах роя. Состояния протоколов, стоп-условия, кворум, дедуп и арбитраж живут
в ядрах инструментов (`consilium_core.py`, `swarm_core.py`).

- HU-B01: скан импортов `scripts/**` — harness не импортирует модули инструментов.
- HU-B02: поиск запрещённых идентификаторов фаз/туров в исходниках harness.
  Совпадения допустимы только через явный whitelist (комментарии-указатели);
  неиспользованные записи whitelist — ошибка (защита от протухания).
"""
from __future__ import annotations

import ast
import re
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[2] / "scripts"

# HU-B01: модули ядер инструментов, запрещённые к импорту из harness.
FORBIDDEN_MODULES = ("consilium", "consilium_core", "swarm", "swarm_core")

# HU-B02: идентификаторы фаз консилиума (A–E) и туров роя (1–4), а также
# протокольных команд/чекпоинтов инструментов. Список зафиксирован здесь
# (test-plan HU-B02). Намеренно НЕ входят: общие слова данных ("attack",
# "response", "verdict" чеклиста, "redteam" applies_to) — это разделяемый
# словарь схем, а не знание о фазах/турах.
FORBIDDEN_PATTERNS = {
    "consilium_phases": re.compile(r"\bphase_[a-eA-E]\b"),
    "swarm_tours": re.compile(r"\btour[1-4]\b"),
    "convene_command": re.compile(r"\bconvene[ds]?\b"),
    "verdict_phase": re.compile(r"\bverdict_ready\b|\bverdict_phase\b"),
    "stalemate_predicate": re.compile(r"\bstalemate\b"),
    "kill_proxy": re.compile(r"\bkill_candidate\b|\bkill_proxy\b"),
}

# Whitelist документированных исключений: {относительный путь: {метки паттернов}}.
# Каждая запись обязана сопровождаться комментарием-указателем в исходнике.
WHITELIST: dict[str, set[str]] = {}


def _harness_sources() -> list[Path]:
    return sorted(
        path for path in SCRIPTS_DIR.rglob("*.py") if "__pycache__" not in path.parts
    )


def _imported_modules(path: Path) -> list[str]:
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    modules: list[str] = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            modules.extend(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.module:
            modules.append(node.module)
    return modules


def _is_forbidden_module(module: str) -> str | None:
    for forbidden in FORBIDDEN_MODULES:
        if module == forbidden or module.startswith(forbidden + "."):
            return forbidden
    return None


def test_hu_b01_no_tool_module_imports():
    """HU-B01: 0 импортов модулей инструментов (consilium*/swarm*) в scripts/**."""
    sources = _harness_sources()
    assert sources, f"нет исходников harness: {SCRIPTS_DIR}"
    violations = []
    for path in sources:
        for module in _imported_modules(path):
            forbidden = _is_forbidden_module(module)
            if forbidden:
                violations.append(f"{path.relative_to(SCRIPTS_DIR)}: import {module}")
    assert violations == [], "harness импортирует модули инструментов:\n" + "\n".join(violations)


def test_hu_b02_no_phase_tour_identifiers():
    """HU-B02: 0 запрещённых идентификаторов фаз/туров вне whitelist."""
    violations = []
    used_whitelist: dict[str, set[str]] = {}
    for path in _harness_sources():
        rel = str(path.relative_to(SCRIPTS_DIR))
        text = path.read_text(encoding="utf-8")
        allowed = WHITELIST.get(rel, set())
        for label, pattern in FORBIDDEN_PATTERNS.items():
            for match in pattern.finditer(text):
                line_no = text.count("\n", 0, match.start()) + 1
                if label in allowed:
                    used_whitelist.setdefault(rel, set()).add(label)
                    continue
                violations.append(f"{rel}:{line_no}: {label} ({match.group(0)!r})")
    assert violations == [], (
        "запрещённые идентификаторы фаз/туров в harness:\n" + "\n".join(violations)
    )
    stale = {
        f"{rel}: {sorted(labels - used_whitelist.get(rel, set()))}"
        for rel, labels in WHITELIST.items()
        if labels - used_whitelist.get(rel, set())
    }
    assert not stale, "протухшие записи whitelist:\n" + "\n".join(sorted(stale))
