---
name: vanessa-security-warning
description: Rule for handling security warnings when running Vanessa Automation external processing.
---

# Vanessa Security Warnings Handling Policy

> RULES — mandatory requirements for interpreting launch blockings through `Предупреждение безопасности`.

## MUST

| Requirement | Description |
|------------|-------------|
| ЖР as a trigger | If there is an entry about `Предупреждение безопасности` in the ЖР, the agent MUST treat it as a trigger for visual verification |
| Visual check is mandatory | After such a signal the agent MUST use a real display: noVNC or a screenshot |
| Do not rely on X11 heuristics | You cannot draw conclusions solely from `wmctrl`/`xwininfo`/window titles |
| Trust-flow only for the first run | The first run after changing the EPF is not considered a valid test execution |

The detailed procedure is described in the diagnostics and GUI skills.

---
depends_on:
  - framework/skills/tool-usage/browser-ui/gui-control/SKILL.md
  - framework/skills/tool-usage/browser-ui/screenshot/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
---
