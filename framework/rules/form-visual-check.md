---
name: form-visual-check
description: "После изменения управляемой формы или скриншота формы → применить visual-check + form-visual-requirements"
alwaysApply: true
---
# Визуальная проверка форм

> **Триггер:** после изменения управляемой формы ИЛИ после получения скриншота формы на ревью. При срабатывании — применить навыки `visual-check` (`framework/skills/tool-usage/browser-ui/visual-check/SKILL.md`) и `form-visual-requirements` (`framework/skills/bsl-practices/form-visual-requirements/SKILL.md`).

`visual-check` делает скриншот и проверяет JS-консоль. `form-visual-requirements` — чек-лист разметки, выравнивания, читаемости. Применяй оба вместе.

---
depends_on:
  - visual-check
  - form-visual-requirements
---
