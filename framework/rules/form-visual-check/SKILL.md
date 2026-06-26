---
name: form-visual-check
description: "После правок или скриншота формы выполнить visual-check"
alwaysApply: true
---
# Визуальная проверка форм

> **Триггер:** после изменения управляемой формы, при исследовании/проверке клиентской формы через TestClient/VA/web-клиент, ИЛИ после получения скриншота формы на ревью. При срабатывании — применить навыки `visual-check` (`framework/skills/tool-usage/browser-ui/visual-check/SKILL.md`) и `form-visual-requirements` (`framework/skills/bsl-practices/form-visual-requirements/SKILL.md`).

Маршрут по умолчанию для форм 1С — Vanessa/TestClient или платформенный TestClient MCP. Визуальный скриншот обязателен: сначала пробуй проверенный VA MCP screenshot, если он реально работает в текущем окружении; иначе снимай внешний OS/noVNC screenshot видимого окна 1С. Web-клиент в `visual-check` выбирается только для browser-specific дефектов: DOM/CSS/HTML, JS console/network, web-auth/publication, viewport/pixel rendering, browser extension или browser-only file/clipboard.

---
depends_on:
  - visual-check
  - form-visual-requirements
---
