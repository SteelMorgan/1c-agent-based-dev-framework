---
name: visual-check
description: "Deprecated: визуальная проверка форм 1С перенесена в va-visual-check"
alwaysApply: false
---

# Deprecated: visual-check

Этот навык оставлен как совместимый указатель для старых ссылок. Для визуальной проверки 1С-форм используй профильный навык `va-visual-check`:

- основной маршрут Vanessa/TestClient + VA MCP;
- Linux headless X11/Xvfb рецепт для чёрных VA-скриншотов;
- browser fallback и правила фиксации остаточного риска.

Оценку качества формы выполняй по `form-visual-requirements`.

---
depends_on:
  - framework/skills/tool-usage/vanessa/va-visual-check/SKILL.md
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
---
