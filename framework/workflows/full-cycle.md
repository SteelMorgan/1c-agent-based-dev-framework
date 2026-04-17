---
name: full-cycle
description: Полный цикл разработки с обязательным кросс-ревью на каждой фазе.
---

# Воркфлоу: Полный цикл разработки (Full Cycle)

> Детерминированный воркфлоу с кросс-ревью на каждой фазе. Для задач средней и высокой сложности.

## Фазы

### Phase 0: Классификация (Explorer → Economy)

Explorer исследует кодовую базу → модули, графы вызовов, зависимости. Оркестратор классифицирует: Простая → quick-fix; Средняя/Сложная → Phase 1.

Артефакты Explorer передаются в Phase 1 и Phase 2 как контекст.

### Phase 1: Анализ (Analyst → Mid/High)

Вход: задача + `explorer-context.md`. Analyst создаёт спеку MADR 4.0 + RFC 2119. Ревью Reviewer (Premium). Макс. 3 итерации BLOCK. Ревью + cross-provider-review + **STOP: ждём ОК пользователя**.

Approval gate Phase 1 нужен, потому что спецификация фиксирует бизнес-решения (уровни RFC 2119, границы scope, выбор между альтернативами), которые пользователь ОБЯЗАН подтвердить ДО того, как Architect потратит ресурс на дизайн, опирающийся на возможно неверный контракт. Пропуск этого gate исторически приводил к множественным итерациям: cross-provider-review или Architect находили противоречия в спеке, которые можно было устранить одним уточнением у пользователя на этой стадии.

### Phase 2: Архитектура (Architect → High/Premium)

Вход: утверждённая спека + `explorer-context.md`. Architect → `technical-design.md` + `task-breakdown.json`. Ревью + **STOP: ждём ОК пользователя**.

### Phase 3a + 3b: ПАРАЛЛЕЛЬНО

- **3a (Scenario-Author):** intent-сценарии → `.feature` Vanessa. Ревью (scope=bdd).
- **3b (Developer-Tests):** MUST-сценарии → unit-тесты (Red). Ревью (scope=tests).

Оба MUST завершиться перед Phase 3c.

### Phase 3c: Реализация (Developer-Code → High)

Вход: всё из Phase 2 + тесты 3b + `.feature` 3a. Developer-Code пишет код (Green). Только тесты Phase 3b. При `test_failure` + `suspected_test_error` → Reviewer-арбитраж → маршрутизация.

Phase 3c начинается ТОЛЬКО после 3a и 3b (включая ревью).

### Phase 4: Покрытие и регрессия (Tester → Mid/High)

Tester запускает все тесты, дописывает edge-cases, интеграционные, регрессионные. Ревью (High). Phase 4 НЕ дублирует Phase 3.

---

## Передача артефактов

| От → К | Артефакт |
|--------|----------|
| 0 → 1, 2 | `explorer-context.md` |
| 1 → 2 | `spec.md` |
| 2 → 3a, 3b | spec + technical-design + task-breakdown.json |
| 3a → 3c | `.feature` |
| 3b → 3c | test-модули (.bsl) |
| 3c → 4 | BSL + `.feature` + зелёные тесты |

**Обязательные поля:** Спецификация — Context, Requirements, Scope, Test Plan. Technical Design — компоненты, интерфейсы. Task Breakdown JSON — task_id, task_type, depends_on, spec_refs, критерии завершения. Код — coding-standards. Тесты — связь с MUST-сценариями.

---

## Обработка ошибок

| Ситуация | Действие |
|----------|----------|
| BLOCK, <= 3 итерации | Вернуть автору |
| BLOCK, > 3 | Эскалация пользователю |
| Пользователь отклонил Phase 1 | Analyst перерабатывает |
| Пользователь отклонил Phase 2 | Architect перерабатывает |
| `test_failure` в Phase 3c | Developer-Code: если свой код → исправить; если тест → `suspected_test_error` → Reviewer-арбитраж |
| `test_failure` в Phase 4 | Tester: свой тест → исправить; баг в коде → `implementation_error` → Developer |
| `check_syntax` падение | Developer исправляет до ревью |
| MCP недоступен | Escape hatch → эскалация |

---
depends_on:
  - framework/workflows/quick-fix.md
  - framework/subagents/explorer.md
  - framework/subagents/analyst.md
  - framework/subagents/architect.md
  - framework/subagents/scenario-author.md
  - framework/subagents/developer-tests.md
  - framework/subagents/developer-code.md
  - framework/subagents/tester.md
  - framework/subagents/reviewer.md
  - framework/workflows/source-of-truth-policy.md
  - framework/rules/tdd-policy.md
---
