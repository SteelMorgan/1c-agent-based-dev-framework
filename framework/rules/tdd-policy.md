---
name: tdd-policy
description: Политика TDD — тесты пишутся до реализации и фиксируются в спецификации.
---

# Политика TDD (Test-Driven Development)

> Тесты и реализация пишутся **разными агентами** в **разных фазах**. Автор тестов не знает реализацию, автор кода не модифицирует тесты.

```
Phase 3a: Scenario-Author  → .feature (BDD)   ┐ параллельно
Phase 3b: Developer-Tests  → unit-тесты (Red)  ┘
Phase 3c: Developer-Code   → код (Green)
Phase 4:  Tester           → edge cases, регрессия, BDD + unit
```

## MUST

- Тест-план описан в спецификации ДО кода
- YaxUnit-тесты для MUST-сценариев написаны ДО реализации
- Цикл Red -> Green -> Refactor
- Тесты проверены ревьюером (покрытие против спеки)
- После исправления замечаний — перезапустить ВСЕ затронутые тесты

## Исключения

- UI-only, конфигурация без кода, документация — тесты MAY быть пропущены
- Quick-fix — допустимо тест после исправления (с пометкой в отчёте)

## Слои тестирования

| Слой | Фаза | Агент | Покрывает |
|------|------|-------|-----------|
| BDD (acceptance) | 3a | Scenario-Author | Поведение через UI |
| TDD (unit) | 3b | Developer-Tests | Публичные методы, MUST-сценарии, базовые негативы |
| TDD (green) | 3c | Developer-Code | Реализация, проходящая unit-тесты |
| Coverage | 4 | Tester | Edge cases, интеграция, регрессия |

Phase 3a и 3b — **параллельно**. Phase 3c стартует после завершения обоих.

## Границы агентов

- **Scenario-Author:** НЕ пишет unit-тесты, НЕ запускает сценарии, НЕ расширяет за пределы спецификации
- **Developer-Tests:** MUST-сценарии + базовые негативы; НЕ покрывает комбинаторные edge cases и интеграцию
- **Tester:** дополняет покрытие; НЕ дублирует тесты Developer; НЕ правит BSL-код

## Правило при падении теста у Tester

```
Тест упал
  ├── Ошибка в тесте → Tester исправляет (test_error)
  └── Баг в коде → СТОП. Метка implementation_error + описание.
                   Оркестратор возвращает задачу Developer.
```

---
depends_on:
  - framework/rules/sdd-policy.md
  - framework/skills/tool-usage/code-analysis/test-execution/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
---