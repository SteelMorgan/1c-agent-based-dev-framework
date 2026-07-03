---
name: report-discovered-issues
description: "Report out-of-scope defects after the task"
alwaysApply: true
---

# Report on Discovered Issues

> The agent often sees more than is needed for the current task. A silently ignored finding remains an unresolved risk, so it must be explicitly reported to the user.

## Principle

While solving a task, the agent may find regressions, errors in adjacent modules, technical debt, antipatterns, discrepancies between code and the specification, performance issues, or security issues. Fixing them inside the current task without the user's permission is not allowed, but hiding them is not allowed either.

## MUST

| Requirement | Description |
|-----------|----------|
| Record findings | As issues are discovered, record the problem in the working context, task notes, or the final report section |
| Do not expand scope silently | Do not fix discovered issues within the current task without explicit user permission |
| Report after completion | In the final response or report, list the discovered issues that are outside the completed scope |
| Be specific | For each finding, include the location, essence, risk, severity, and an estimated fix size |
| Suggest a path | Suggest a next action: separate task, quick-fix, postpone, document, or investigate further |

## What to Report: Verdict

| Finding / behavior | Verdict |
|--------------------|---------|
| Bugs leading to loss of data, money, security, or availability | report |
| Regressions and deviations from the source of truth | report |
| Data integrity issues | report |
| Crashes or exceptions possible in a real workflow | report |
| Errors in tests or infrastructure that mask the real result | report |
| Purely stylistic nits with no impact on maintainability | do not report |
| Typos in comments | do not report |
| Abstract refactoring wishes without a concrete risk | do not report |
| Turning the current task into cleanup of everything found | prohibited |
| Putting the report off until later | prohibited |
| Merging different problems into one vague phrase | prohibited |
| Dramatising or downplaying (the description must be verifiable) | prohibited |

## Report Format

```markdown
## Found by path

### 1. [Short title]
- **Where:** `path/to/file:line`
- **What:** specific description of the problem
- **Why it's a problem:** consequences or risk
- **Severity:** critical / medium / low
- **Effort:** simple fix / separate task / large work
- **Suggestion:** what to do next
```

## Related Rules

- `agent-context-protocol` — where to record working context and discovered issues.
- `quick-fix` / `full-cycle` — how to turn findings into follow-up work.
- `source-of-truth` — how to check discrepancies between artifacts.

---
depends_on:
  - agent-context-protocol
  - source-of-truth
---
