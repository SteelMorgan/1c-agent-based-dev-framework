---
name: query-patterns
description: "Перед написанием нового запроса → применить навык query-patterns"
alwaysApply: true
---
# Паттерны запросов (перед написанием)

> **Триггер:** перед написанием нового запроса на языке запросов 1С. При срабатывании — применить навык `query-patterns` (`framework/skills/bsl-practices/query-patterns/SKILL.md`).

Каждый запрос — сетевой round-trip. Убедись, что не создаёшь query-in-loop, dot-dereference и избыточных join-ов.

---
depends_on:
  - query-patterns
---
