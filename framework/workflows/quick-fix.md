---
name: quick-fix
description: Быстрое исправление в одном файле без кросс-ревью.
---

# Воркфлоу: Quick Fix

> Три шага без кросс-ревью. Один файл, < 20 строк, без архитектурных решений, без новых фич.

## Шаги

### 1. Найти (Explorer → Economy)

`navigate_symbol` + `get_call_graph` → путь к модулю, зависимости, убедиться что изменение локализовано.

### 2. Исправить (Developer → Mid)

Минимально необходимое изменение по `coding-standards`. Без «улучшений» за рамками задачи.

### 3. Проверить (Developer → Mid)

1. `check_syntax` — обязательно
2. `run_tests` — если есть тесты для модуля
3. `get_diagnostics` — отсутствие ошибок LSP

## Эскалация на Full-cycle

| Ситуация | Действие |
|----------|----------|
| Тесты падают после изменения | Исправить или эскалировать |
| Несколько модулей / архитектура / ревью / > 20 строк | Full-cycle |

**Протокол:** зафиксировать состояние → передать оркестратору → full-cycle с Phase 1 (или Phase 3 если спека есть).

---
depends_on:
  - framework/workflows/full-cycle.md
  - framework/subagents/explorer.md
  - framework/subagents/developer-code.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md
  - framework/skills/tool-usage/code-analysis/test-execution/SKILL.md
---