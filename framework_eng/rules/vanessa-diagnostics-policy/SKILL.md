---
name: vanessa-diagnostics-policy
description: "When Vanessa fails: event log -> visual -> tech log"
alwaysApply: true
---

# Vanessa Automation Diagnostics Policy

> **Trigger:** unsuccessful or suspicious scenario run. When triggered, apply the `vanessa-diagnostics` skill (`framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md`).

## MUST (source priority)

| Priority | Source | Condition |
|-----------|----------|---------|
| 1st | Registration log (`event-log`) | Primary source of errors — check first |
| 2nd | Visual diagnostics | For test client form state, GUI blocking, modal/manager window, and `Security warning` — visual artifact via `va-visual-check`: first VA MCP screenshot, with fallback if necessary and the reason recorded |
| 3rd | Technology log | Only if `event-log` and visual diagnostics did not provide an answer |

- Do not rely blindly on local time windows: ClickHouse and local time may differ (timezone drift).

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
---
