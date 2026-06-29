---
name: form-visual-check
description: "After changes or a form screenshot, perform a visual check"
alwaysApply: true
---
# Form Visual Check

> **Trigger:** after changing a managed form, when investigating/checking a client form through VA/TestClient, OR after receiving a form screenshot in review. When triggered, apply the `va-visual-check` skill (`framework/skills/tool-usage/vanessa/va-visual-check/SKILL.md`) and `form-visual-requirements` (`framework/skills/bsl-practices/form-visual-requirements/SKILL.md`).

The preferred route for 1С forms is Vanessa/TestClient and VA MCP: `connect_test_client` → the real test-client PID → `get_window_list_os` → `get_window_screenshot_os`. The route details, the Linux headless X11/Xvfb recipe for black screenshots, and the browser fallback are described in `va-visual-check`.

The platform TestClient MCP can be used for structural form control if that is part of a VA/TestClient scenario. If browser/web-client fallback is used, the reason, the completed VA steps, and the residual risk must be explicitly recorded in the context.

---
depends_on:
  - framework/skills/tool-usage/vanessa/va-visual-check/SKILL.md
  - form-visual-requirements
---
