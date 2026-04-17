---
name: vanessa-scenario-policy
description: Vanessa Automation scenario testing policy. Scenarios must rely on real project requirements and verify a single observable behavior.
---

# Vanessa Automation Scenario Policy

## MUST

- **Don't guess — look at the code.** Do not assume the logic of checks, handlers, or form events — read the form module, object module, manager module. It is preferable to delegate code inspection to a subagent (Explorer or `code-navigation`) — it saves context and yields a structured result. If the code behavior does not match the test expectation, that is not a test failure but a discovered discrepancy: record it as a result
- The scenario relies on the task specification or an existing business case — no fictional cases
- One scenario = one observable behavior
- Before adding a new step — look for it in the Vanessa library and the project scenarios
- The first scenario for a new case is a short smoke
- The scenario runs under a specific business user, not admin/AgentAI — exception only if the function under test is exclusively available to an administrator; the user is determined from the task description, and if missing — **ask a person**

## MUST (continued)

- **The task tag is mandatory.** Every `.feature` file MUST contain a tag `@task-<ID>` (for example `@task-103`), where `<ID>` is the task identifier from the tracker. If a task arrives without an ID — use a short slug (`@task-order-processing-20260325`). The tag is placed at the `Functionality:` level so that all scenarios in the file inherit the association.
- **Source comment.** The file header (before tags) MUST include a comment: `# Task: <ID> — <title>`. This is a human-readable link for quick navigation.

## SHOULD

- Data preparation is extracted from the main scenario
- Data preparation is extracted from the main scenario
- Assert via the UI state, not through internal details

## Scenario Source

- New task: based on an approved specification (before or in parallel with implementation)
- Implemented task: based on a real case (regression capture)

## Anti-patterns

| Anti-pattern | Consequence |
|-------------|-------------|
| A made-up flow without a project source | Does not guard a real requirement |
| A long scenario covering 5–7 intents | Unstable, hard to diagnose |
| Testing internal details instead of behavior | Fragile scenario |

---
depends_on:
  - framework/rules/vanessa-test-isolation-policy.mdc
---
