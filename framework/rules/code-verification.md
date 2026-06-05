---
name: code-verification
description: "После правки BSL → применить навыки code-verification + syntax-checking"
alwaysApply: true
---
# Верификация BSL после правок

> **Триггер:** после любого изменения BSL-кода. При срабатывании — применить навыки `code-verification` (`framework/skills/tool-usage/code-analysis/code-verification/SKILL.md`) и `syntax-checking` (`framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md`).

**GUARD:** нулевые ошибки LSP обязательны перед коммитом.

---
depends_on:
  - code-verification
  - syntax-checking
---
