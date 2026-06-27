---
name: vanessa-security-warning
description: "Security Warning в ЖР требует visual verification"
alwaysApply: true
---

# Предупреждения безопасности Vanessa

> **Триггер:** запись о `Предупреждение безопасности` в журнале регистрации. При срабатывании — применить навыки `gui-control` (`framework/skills/tool-usage/browser-ui/gui-control/SKILL.md`) и `screenshot` (`framework/skills/tool-usage/browser-ui/screenshot/SKILL.md`).

## MUST

| Требование | Описание |
|------------|----------|
| ЖР как триггер | Запись о `Предупреждение безопасности` в ЖР → обязательная визуальная проверка |
| Визуальная проверка обязательна | MUST использовать VA MCP-скриншот реального окна тест-клиента |
| Не полагаться на X11-эвристику | Нельзя делать вывод только по `wmctrl`/`xwininfo`/заголовкам окон; визуальный артефакт получать по `va-visual-check` |
| Скриншот валидируется | Проверить, что PNG не пустой/чёрный; Linux/Xvfb и fallback-случаи выполнять по `va-visual-check` |
| Trust-flow только для первого запуска | Первый запуск после изменения EPF не считается валидным тестовым прогоном |

---
depends_on:
  - framework/skills/tool-usage/browser-ui/gui-control/SKILL.md
  - framework/skills/tool-usage/vanessa/va-visual-check/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
---
