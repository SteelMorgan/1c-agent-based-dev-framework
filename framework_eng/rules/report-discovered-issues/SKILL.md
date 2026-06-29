---
name: report-discovered-issues
description: "Report out-of-scope defects after the task"
alwaysApply: true
---

# Reporting Discovered Issues

> The agent often sees more than is needed for the current task. A silently ignored finding remains an unfixed risk, so it must be explicitly brought to the user's attention.

## Principle

While solving one task, the agent may find regressions, bugs in adjacent modules, technical debt, anti-patterns, mismatches between code and specification, performance issues, or security issues. These findings do not need to be fixed within the current task without the user's permission, but they cannot be hidden.

## MUST

| Requirement | Description |
|-----------|----------|
| Record findings | As problems are discovered, record them in the working context, task notes, or the final report section |
| Do not expand scope silently | Do not fix found issues within the current task without the user's explicit permission |
| Report after completion | In the final answer or report, list the found issues that are outside the completed scope |
| Provide specifics | For each finding, specify the location, nature, risk, severity, and approximate fix size |
| Suggest a path | Propose a next action: separate task, quick-fix, defer, document, or investigate further |

## Report Format

```markdown
## Found Along the Way

### 1. [Short Title]
- **Where:** `path/to/file:line`
- **What:** specific description of the problem
- **Why it is a problem:** consequences or risk
- **Severity:** critical / medium / low
- **Effort:** simple fix / separate task / large effort
- **Suggestion:** what to do next
```

## What Must Be Reported

- Bugs that can lead to data loss, money loss, security issues, or availability issues.
- Regressions and mismatches with the source of truth.
- Data integrity issues.
- Crashes or exceptions possible in a real workflow.
- Errors in tests or infrastructure that mask the real result.

## What May Be Omitted

- Purely stylistic nits with no maintenance impact.
- Typos in comments.
- Abstract refactoring wishes without a concrete risk.

## What NOT to Do

- Do not turn the current task into a cleanup of everything found.
- Do not postpone the report "for later."
- Do not merge different problems into one vague statement.
- Do not dramatize or downplay: the description must be verifiable.

## Related Rules

- `agent-context-protocol` - where to record working context and found issues.
- `quick-fix` / `full-cycle` - how to turn findings into further work.
- `source-of-truth` - how to check mismatches between artifacts.

---
depends_on:
  - agent-context-protocol
  - source-of-truth
---
