---
name: form-visual-check
description: "After changes or a form screenshot, run visual-check"
alwaysApply: true
---
# Form Visual Check

> **Trigger:** after changing a managed form, when investigating/checking a client form through TestClient/VA/web client, OR after receiving a form screenshot in review. When triggered, apply the `visual-check` skill (`framework/skills/tool-usage/browser-ui/visual-check/SKILL.md`) and `form-visual-requirements` (`framework/skills/bsl-practices/form-visual-requirements/SKILL.md`).

The default route for 1С forms is Vanessa/TestClient or the platform TestClient MCP. A visual screenshot is mandatory: first try the proven VA MCP screenshot if it actually works in the current environment; otherwise take an external OS/noVNC screenshot of the visible 1С window. The web client in `visual-check` is chosen only for browser-specific defects: DOM/CSS/HTML, JS console/network, web-auth/publication, viewport/pixel rendering, browser extension, or browser-only file/clipboard.

---
depends_on:
  - visual-check
  - form-visual-requirements
---
