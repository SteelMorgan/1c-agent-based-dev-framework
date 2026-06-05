---
name: tdd-policy
description: Пишешь тесты или код → тесты до реализации (Red→Green). Применить навык test-writing.
alwaysApply: true
---

# Политика TDD (Test-Driven Development)

> **Триггер:** фаза написания тестов (Phase 3b) или реализации (Phase 3c/3d). При срабатывании — применить навык `test-writing` (`framework/skills/bsl-practices/test-writing/SKILL.md`).

## MUST

- Тест-план описан в спецификации **ДО** кода.
- YaxUnit-тесты для MUST-сценариев написаны ДО реализации (Red → Green → Refactor).
- Тесты проверены ревьюером (покрытие против спеки).
- После исправления замечаний — перезапустить ВСЕ затронутые тесты.
- **User/Role context в Test Plan:** если код использует `SetPrivilegedMode`, проверки ролей (`AccessRight`, `RoleAvailable`) или результат зависит от текущего пользователя — спецификация ДОЛЖНА в разделе «Test Plan» явно указать для каждого теста: имя пользователя/набор ролей, требуемый режим (привилегированный или нет), ожидаемый результат (успех/отказ). Без этого тест под full-rights runner-ом (например `AgentAI`) даст false-positive. Если для unit это технически невозможно — фиксировать в спеке ADR с переносом в integration scope (Phase 4).

## Исключения

- UI-only, конфигурация без кода, документация — тесты MAY быть пропущены.
- Quick-fix — допустимо тест после исправления (с пометкой в отчёте).

## Правило при падении теста у Tester

```
Тест упал
  ├── Ошибка в тесте → Tester исправляет (test_error)
  └── Баг в коде → СТОП. Метка implementation_error + описание.
                   Оркестратор возвращает задачу Developer.
```

---
depends_on:
  - framework/skills/bsl-practices/test-writing/SKILL.md
  - framework/rules/sdd-policy.md
---