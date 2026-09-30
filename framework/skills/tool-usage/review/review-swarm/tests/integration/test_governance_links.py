"""Контрольный тест CH-01 (test-plan §8): миграция ссылок удалённого скилла (FR-18, AC-18, T-15).

После миграции поиск по `.framework` обязан давать только разрешённые остатки:

- имя/ссылки на gate-правило (файл `rules/<NEEDLE>.md`, включая runtime mirror
  `.claude/rules/...` — правило сохраняет имя, TD §12);
- имя роли/профиля reviewer (NEEDLE + "er" — governance-surface жива, TD §12).

Запрещено:

- каталог `skills/<NEEDLE>/` в `.framework` (скилл удалён в T-15);
- любые пути `skills/<NEEDLE>/**` (включая зеркальные) — висячих ссылок на
  удалённый скилл быть не должно (AC-18);
- любые иные формы NEEDLE в документации фреймворка (*.md): упоминание скилла
  как инструмента — всегда висячая ссылка.

Строки-паттерны собираются конкатенацией, чтобы сам тест не попадал в
контрольный поиск по `.framework`.

Граница проверки (зафиксировано в T-15): семантический разбор остатков ведётся
по документации (*.md) — оперативной поверхности фреймворка. Код (*.py)
проверяется только на отсутствие литералов путей `skills/<NEEDLE>` (тест 2):
исторические комментарии о наследуемой gate-семантике в ядрах
review-swarm/consilium — не ссылки, а правка кода вне границ T-15.
Runtime-логи (*.log, напр. `.codex_image_gen.*.log`) исключены из проверки —
TD §12 исключает runtime-логи image-gen из инвентаря миграции (исторические
артефакты выполнения, не ссылки фреймворка).
"""
from __future__ import annotations

from pathlib import Path

FRAMEWORK = Path(__file__).resolve().parents[4]

NEEDLE = "cross-provider-" + "review"           # имя удалённого скилла / префикс роли
ROLE = NEEDLE + "er"                             # имя роли (жива, governance-surface)
RULE_NAME = NEEDLE + ".md"                       # имя файла gate-правила (живо)
RULE_REF = "rules/" + RULE_NAME                  # ссылка на правило (любой префикс)
SKILL_PATH = "skills/" + NEEDLE                  # путь удалённого скилла
RULE_FILE = FRAMEWORK / "rules" / RULE_NAME
# В исходном слое .framework профили лежат в agents/profiles/, в зеркале .claude —
# плоско в agents/ (соглашение о каталогах); тест обязан проходить из обоих слоёв.
_PROFILE_NESTED = FRAMEWORK / "agents" / "profiles" / (ROLE + ".md")
PROFILE_FILE = _PROFILE_NESTED if _PROFILE_NESTED.is_file() else FRAMEWORK / "agents" / (ROLE + ".md")
HANDOFF_TEMPLATE = (
    FRAMEWORK / "skills" / "orchestrator" / "references"
    / "handoff-templates" / (ROLE + ".md")
)

_SKIP_DIRS = {"__pycache__", ".pytest_cache"}
_SKIP_SUFFIXES = {".pyc", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".woff", ".woff2", ".log"}
_DOC_SUFFIXES = {".md"}


def _iter_lines(docs_only: bool = False):
    """Все текстовые строки .framework (без бинарников и кэшей)."""
    for path in sorted(FRAMEWORK.rglob("*")):
        if not path.is_file() or path.suffix in _SKIP_SUFFIXES:
            continue
        if docs_only and path.suffix.lower() not in _DOC_SUFFIXES:
            continue
        if any(part in _SKIP_DIRS for part in path.parts):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for lineno, line in enumerate(text.splitlines(), start=1):
            yield path, lineno, line


def test_skill_directory_removed():
    """Каталог удалённого скилла отсутствует в .framework."""
    assert not (FRAMEWORK / SKILL_PATH).exists(), (
        f"каталог {SKILL_PATH}/ должен быть удалён (git rm, T-15)"
    )


def test_no_skill_path_references():
    """Ни одной ссылки на пути удалённого скилла (все текстовые файлы, включая код)."""
    hits = [
        f"{path.relative_to(FRAMEWORK).as_posix()}:{lineno}: {line.strip()[:200]}"
        for path, lineno, line in _iter_lines()
        if SKILL_PATH in line
    ]
    assert not hits, "висячие ссылки на удалённый скилл:\n" + "\n".join(hits)


def test_only_allowed_remnants():
    """В документации NEEDLE встречается только как имя правила или имя роли."""
    bad = []
    for path, lineno, line in _iter_lines(docs_only=True):
        if NEEDLE not in line:
            continue
        rel = path.relative_to(FRAMEWORK).as_posix()
        if rel == RULE_REF:
            continue  # само правило — его содержимое не «ссылка на скилл»
        residual = line.replace(RULE_NAME, "").replace(ROLE, "")
        if NEEDLE in residual:
            bad.append(f"{rel}:{lineno}: {line.strip()[:200]}")
    assert not bad, (
        "запрещённые упоминания (разрешены только имя/ссылка на правило "
        f"{RULE_REF} и имя роли {ROLE}):\n" + "\n".join(bad)
    )


def test_governance_surface_preserved():
    """Governance-surface переориентирован, не удалён (AC-18)."""
    assert RULE_FILE.is_file(), f"правило {RULE_REF} обязано сохраниться"
    assert PROFILE_FILE.is_file(), f"профиль роли {ROLE} обязан сохраниться"
    assert HANDOFF_TEMPLATE.is_file(), "шаблон handoff роли обязан сохраниться"
