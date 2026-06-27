---
name: report-discovered-issues
description: "Report out-of-scope defects after the task"
alwaysApply: true
---

# Report of Discovered Issues

> The agent often sees more than is needed for the current task. A silently ignored finding remains an unresolved risk, so it must be explicitly communicated to the user.

## Principle

While solving one task, the agent may discover regressions, bugs in adjacent modules, technical debt, antipatterns, mismatches between code and specification, performance issues, or security issues. These findings do not need to be fixed within the current task without the user's permission, but they cannot be hidden.

## MUST

| Requirement | Description |
|-----------|----------|
| Record findings | As they are discovered, record the issue in the working context, task notes, or the final report section |
| Do not silently expand scope | Do not fix discovered issues within the current task without explicit user permission |
| Report after completion | In the final response or report, list the discovered issues that are outside the completed scope |
| Provide specifics | For each finding, specify the location, essence, risk, severity, and approximate size of the fix |
| Suggest a path | Propose a next step: separate task, quick-fix, defer, document, or investigate further |

## Report Format

```markdown
## Найдено по пути

### 1. [Краткое название]
- **Где:** `path/to/file:line`
- **Что:** конкретное описание проблемы
- **Почему проблема:** последствия или риск
- **Серьезность:** критично / средне / низко
- **Усилие:** простой фикс / отдельная задача / большая работа
- **Предложение:** что сделать дальше
```

## What Must Be Reported

- Bugs that can lead to data loss, financial loss, security issues, or availability issues.
- Regressions and divergences from the source of truth.
- Data integrity issues.
- Crashes or exceptions possible in a production scenario.
- Errors in tests or infrastructure that mask the real result.

## What Can Be Omitted

- Purely stylistic details without maintainability impact.
- Typos in comments.
- Abstract refactoring wishes without a concrete risk.

## What Not to Do

- Do not turn the current task into cleaning up everything found.
- Do not postpone the report until later.
- Do not combine different problems into one vague phrase.
- Do not dramatize or understate it: the description must be verifiable.

## Related Rules

- `agent-context-protocol` - where to record working context and discovered issues.
- `quick-fix` / `full-cycle` - how to turn findings into follow-up work.
- `source-of-truth` - how to check discrepancies between artifacts.

---
depends_on:
  - agent-context-protocol
  - source-of-truth
---
