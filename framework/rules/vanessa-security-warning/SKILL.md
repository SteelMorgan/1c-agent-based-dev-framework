---
name: vanessa-security-warning
description: В ЖР есть запись о «Предупреждение безопасности» → обязательная визуальная проверка. Применить навыки gui-control / screenshot.
alwaysApply: true
---

# Предупреждения безопасности Vanessa

> **Триггер:** запись о `Предупреждение безопасности` в журнале регистрации. При срабатывании — применить навыки `gui-control` (`framework/skills/tool-usage/browser-ui/gui-control/SKILL.md`) и `screenshot` (`framework/skills/tool-usage/browser-ui/screenshot/SKILL.md`).

## MUST

| Требование | Описание |
|------------|----------|
| ЖР как триггер | Запись о `Предупреждение безопасности` в ЖР → обязательная визуальная проверка |
| Визуальная проверка обязательна | MUST использовать реальный экран: noVNC или скриншот |
| Не полагаться на X11-эвристику | Нельзя делать вывод только по `wmctrl`/`xwininfo`/заголовкам окон |
| Trust-flow только для первого запуска | Первый запуск после изменения EPF не считается валидным тестовым прогоном |

---
depends_on:
  - framework/skills/tool-usage/browser-ui/gui-control/SKILL.md
  - framework/skills/tool-usage/browser-ui/screenshot/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
---
