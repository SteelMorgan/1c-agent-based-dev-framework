---
name: vanessa-security-warning
description: "Security Warning in the event log requires visual verification"
alwaysApply: true
---

# Vanessa Security Warnings

> **Trigger:** a `Security Warning` entry in the event log. When it fires, apply the `gui-control` skill (`framework/skills/tool-usage/browser-ui/gui-control/SKILL.md`) and `screenshot` (`framework/skills/tool-usage/browser-ui/screenshot/SKILL.md`).

## MUST

| Requirement | Description |
|------------|----------|
| Event log as trigger | An entry for `Security Warning` in the event log → mandatory visual verification |
| Visual verification is mandatory | MUST use a VA MCP screenshot of the real test client window |
| Do not rely on X11 heuristics | Do not draw conclusions only from `wmctrl`/`xwininfo`/window titles; obtain the visual artifact via `va-visual-check` |
| Screenshot is validated | Check that the PNG is not empty/black; handle Linux/Xvfb and fallback cases via `va-visual-check` |
| Trust-flow only for the first run | The first run after changing the EPF does not count as a valid test run |

---
depends_on:
  - framework/skills/tool-usage/browser-ui/gui-control/SKILL.md
  - framework/skills/tool-usage/vanessa/va-visual-check/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
---
