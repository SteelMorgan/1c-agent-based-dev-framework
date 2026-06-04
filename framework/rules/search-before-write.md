---
name: search-before-write
description: "Перед созданием новой функции/запроса/обработки → применить навык search-before-write"
alwaysApply: true
---
# Поиск перед написанием

> **Триггер:** перед созданием новой функции, запроса или обработки. При срабатывании — применить навык `search-before-write` (`framework/skills/tool-usage/code-analysis/search-before-write/SKILL.md`).

Сначала выполни каскад поиска из навыка. Не писать новый код, пока не убедился, что аналог не существует в проекте или в БСП.

---
depends_on:
  - search-before-write
---
