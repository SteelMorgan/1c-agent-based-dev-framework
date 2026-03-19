---
name: vanessa-diagnostics-policy
description: Diagnostic policy for Vanessa Automation issues. The main source is the event log, and the tech log is used only as a last fallback.
---

# Vanessa Automation Diagnostics Policy

> Priorities of diagnostic sources after an unsuccessful scenario run.

## MUST

| Requirement | Description |
|------------|-------------|
| Event Log first | The primary source of errors is the registration log (`event-log`) |
| Tech log last | Use the tech log only if `event-log` and visual diagnostics did not provide an answer |
| Account for timezone drift | Do not rely blindly on local time windows: ClickHouse and local time can diverge |

---
depends_on:
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
---
