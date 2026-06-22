---
name: vanessa-diagnostics-policy
description: Vanessa run failed -> diagnose in the order event log -> visual -> tech log. Apply the `vanessa-diagnostics` skill.
alwaysApply: true
---

# Vanessa Diagnostics Policy

> **Trigger:** an unsuccessful or suspicious scenario run. When triggered, apply the `vanessa-diagnostics` skill (`framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md`).

## MUST (source priority)

| Priority | Source | Condition |
|-----------|--------|----------|
| 1st | Registration log (`event-log`) | Main source of errors - check first |
| 2nd | Visual diagnostics (noVNC / screenshot) | If a GUI blockage or `Security Warning` is suspected |
| 3rd | Tech log | Only if `event-log` and visual diagnostics did not provide an answer |

- Do not rely blindly on local time windows: ClickHouse and local time may diverge (timezone drift).

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
---
