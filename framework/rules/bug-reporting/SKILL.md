---
name: bug-reporting
description: "Когда self-fix исчерпан, оформить bug-report"
alwaysApply: true
---
# Оформление bug-report

> **Триггер:** когда агент исчерпал лимит самовосстановления ИЛИ установил, что причина не в его собственном коде. При срабатывании — применить навык `bug-reporting` (`framework/skills/tool-usage/diagnostics/bug-reporting/SKILL.md`).

Без выполнения процедуры из навыка bug-report заводить нельзя. Обязательны: `expectation.quote`, непустой `self_fix_attempts`.

---
depends_on:
  - bug-reporting
---
