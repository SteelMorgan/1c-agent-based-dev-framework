# TDD в этом проекте (Test-Driven Development)

TDD в этом фреймворке — это дисциплина **Red → Green → Refactor**, где тесты пишутся до реализации, а порядок фаз контролирует оркестратор.

Базовое правило: [framework/rules/tdd-policy.md](../../framework/rules/tdd-policy.md)

## Что это значит на практике

- Сначала пишутся тесты на MUST-сценарии из Test Plan (Red).
- Затем пишется минимальный код, чтобы тесты прошли (Green).
- После этого выполняется рефакторинг с повторным прогоном тестов.
- Тесты и код пишут разные роли, чтобы исключить конфликт интересов.

## Как TDD встроен в фазы workflow

В full-cycle процессе TDD распределён по фазам:

1. **Phase 3a — Developer-Tests (Red)**
   - пишет unit-тесты по спецификации;
   - покрывает все MUST-сценарии из Test Plan;
   - тесты должны падать, так как реализации ещё нет.

2. **Phase 3b — Developer-Code (Green)**
   - реализует BSL-код по `spec.md` + `technical-design.md` + `task-breakdown.json`;
   - не изменяет тесты из Phase 3a;
   - добивается прохождения тестов.

3. **Phase 4 — Tester (расширение покрытия)**
   - дополняет edge cases, негативные, интеграционные и регрессионные сценарии;
   - делает полный прогон;
   - формирует `test-report.md`.

Подробно:
- [framework/workflows/full-cycle.md](../../framework/workflows/full-cycle.md)
- [framework/workflows/orchestrator.md](../../framework/workflows/orchestrator.md)

## Обязательные требования

Согласно политике TDD:

- Test Plan должен быть в спецификации до кодинга.
- MUST-сценарии должны быть покрыты тестами до реализации.
- Соблюдается порядок Red → Green → Refactor.
- После правок по ревью — обязательный повторный прогон затронутых тестов.
- После реализации выполняется проверка синтаксиса (`check_syntax`).

## Граница ответственности ролей

### Developer-Tests
- пишет тесты по спецификации;
- не реализует бизнес-код;
- не принимает архитектурные решения за пределами Test Plan.

### Developer-Code
- пишет бизнес-код и проходит тесты;
- не переписывает тестовые модули;
- при проблемах в тестах/инфраструктуре сообщает в оркестратор через контекст и статус.

### Tester
- расширяет покрытие после Green-фазы;
- различает `test_error` и `implementation_error`;
- при `implementation_error` не правит код реализации, а возвращает задачу в Developer-Code через оркестратор.

## Что означают `test_error` и `implementation_error`

- **`test_error`**: ошибка в самом тесте или тестовых данных — исправляется Tester.
- **`implementation_error`**: ошибка в бизнес-логике — задача маршрутизируется обратно в Developer-Code с описанием: какой тест упал, что ожидалось, что получено.

Это поведение зафиксировано в:
- [framework/workflows/orchestrator.md](../../framework/workflows/orchestrator.md)
- [framework/subagents/tester.md](../../framework/subagents/tester.md)

## Какие артефакты участвуют

- `task_dir/.spec/spec.md` (включает Test Plan)
- тест-модули из Phase 3a
- BSL-код из Phase 3b
- `task_dir/.spec/test-report.md` из Phase 4
- контекст-файлы в `task_dir/.context/` для статусов и маршрутизации

## Связанные ресурсы

- Политика TDD: [framework/rules/tdd-policy.md](../../framework/rules/tdd-policy.md)
- Политика SDD: [framework/rules/sdd-policy.md](../../framework/rules/sdd-policy.md)
- Кросс-ревью: [framework/rules/cross-review-policy.md](../../framework/rules/cross-review-policy.md)
- Mandatory tools: [framework/rules/mandatory-tools.md](../../framework/rules/mandatory-tools.md)
- Тестовый subagent: [framework/subagents/developer-tests.md](../../framework/subagents/developer-tests.md)
- Кодовый subagent: [framework/subagents/developer-code.md](../../framework/subagents/developer-code.md)
- Tester subagent: [framework/subagents/tester.md](../../framework/subagents/tester.md)

---

Коротко: в этом проекте TDD — это не только техника написания тестов, а оркестрируемый процесс с разделением ролей, обязательными ревью и чёткой маршрутизацией ошибок.