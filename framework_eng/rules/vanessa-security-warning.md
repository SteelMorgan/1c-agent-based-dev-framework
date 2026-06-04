---
name: vanessa-security-warning
description: An entry about `Security Warning` in the event log means mandatory visual verification. Apply the `gui-control` / `screenshot` skills.
alwaysApply: true
---

# Vanessa Security Warning Alerts

> **Trigger:** an entry about `Security Warning` in the event log. When it occurs, apply the `gui-control` skills (`framework/skills/tool-usage/browser-ui/gui-control/SKILL.md`) and `screenshot` (`framework/skills/tool-usage/browser-ui/screenshot/SKILL.md`).

## MUST

| Requirement | Description |
|------------|----------|
| Event log as a trigger | An entry about `Security Warning` in the event log -> mandatory visual verification |
| Visual verification is mandatory | MUST use a real screen: noVNC or a screenshot |
| Do not rely on X11 heuristics | You cannot draw conclusions based only on `wmctrl`/`xwininfo`/window titles |
| Trust-flow only for the first run | The first run after changing the EPF does not count as a valid test run |

---
depends_on:
  - framework/skills/tool-usage/browser-ui/gui-control/SKILL.md
  - framework/skills/tool-usage/browser-ui/screenshot/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
---
