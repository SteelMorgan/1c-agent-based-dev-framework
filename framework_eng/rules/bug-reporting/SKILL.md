---
name: bug-reporting
description: "When self-fix is exhausted, file a bug report"
alwaysApply: true
---
# Bug Report Filing

> **Trigger:** when the agent has exhausted the self-recovery limit OR determined that the cause is not in its own code. When triggered, apply the `bug-reporting` skill (`framework/skills/tool-usage/diagnostics/bug-reporting/SKILL.md`).

It is not allowed to file a bug report without following the procedure from the bug-report skill. Required: `expectation.quote`, non-empty `self_fix_attempts`.

---
depends_on:
  - bug-reporting
---
