---
name: bug-reporting
description: "When self-fix is exhausted, file bug-report"
alwaysApply: true
---
# Bug Report Formatting

> **Trigger:** when the agent has exhausted the self-recovery limit OR determined that the cause is not in its own code. When this happens, apply the `bug-reporting` skill (`framework/skills/tool-usage/diagnostics/bug-reporting/SKILL.md`).

You must not start a bug report without following the procedure from the bug-report skill. Required: `expectation.quote`, non-empty `self_fix_attempts`.

---
depends_on:
  - bug-reporting
---
