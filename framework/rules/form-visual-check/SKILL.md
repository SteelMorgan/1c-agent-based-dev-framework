---
name: form-visual-check
description: "После правок или скриншота формы выполнить визуальную проверку"
alwaysApply: true
---
# Визуальная проверка форм

> **Триггер:** после изменения управляемой формы, при исследовании/проверке клиентской формы через VA/TestClient, ИЛИ после получения скриншота формы на ревью. При срабатывании — применить навыки `va-visual-check` (`framework/skills/tool-usage/vanessa/va-visual-check/SKILL.md`) и `form-visual-requirements` (`framework/skills/bsl-practices/form-visual-requirements/SKILL.md`).

Предпочтительный маршрут для форм 1С — Vanessa/TestClient и VA MCP: `connect_test_client` → реальный PID тест-клиента → `get_window_list_os` → `get_window_screenshot_os`. Детали маршрута, Linux headless X11/Xvfb рецепт для чёрных скриншотов и browser fallback описаны в `va-visual-check`.

Платформенный TestClient MCP можно использовать для структурного управления формой, если это часть VA/TestClient-сценария. Если используется browser/web-client fallback, причину, выполненные VA-шаги и остаточный риск нужно явно записать в контекст.

---
depends_on:
  - framework/skills/tool-usage/vanessa/va-visual-check/SKILL.md
  - form-visual-requirements
---
