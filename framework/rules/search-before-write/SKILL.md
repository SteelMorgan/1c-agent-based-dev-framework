---
name: search-before-write
description: "Перед новым кодом или запросом искать существующее"
alwaysApply: true
---
# Поиск перед написанием

> **Триггер:** перед созданием новой функции, запроса или обработки. При срабатывании — применить навык `search-before-write` (`framework/skills/tool-usage/code-analysis/search-before-write/SKILL.md`).

**GUARD:** создавать новый код без предварительного поиска аналогов запрещено.

---
depends_on:
  - search-before-write
---
