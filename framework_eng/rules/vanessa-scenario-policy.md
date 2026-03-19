---
name: vanessa-scenario-policy
description: Vanessa Automation scenario testing policy. Scenarios must rely on real project requirements and verify one observable behavior.
---

# Vanessa Automation Scenario Policy

## MUST

- **Don't guess — look at the code.** Do not assume the logic of checks, handlers, and form events — read the form module, object module, manager module. It is preferable to delegate code examination to a subagent (Explorer or `code-navigation`) — this saves context and delivers a structured result. If the code behavior does not match the test's expectation, it is not a test failure but a found discrepancy: record it as a result
- The scenario relies on the task specification or an existing business case — no fictitious cases
- One scenario = one observable behavior
- Before adding a new step — look for an existing one in the Vanessa library and project scenarios
- The first scenario for a new case is a short smoke
- The scenario runs under a specific business user, not admin/AgentAI — exception only if the function under test is exclusively available to an administrator; the user is determined from the task description, and if missing — **ask a person**

## SHOULD

- Specify the source: specification, task number, the case being verified
- Data preparation is moved out of the main scenario
- Assert via the UI state, not by internal details

## Scenario Source

- New task: based on an approved specification (before or in parallel with implementation)
- Implemented task: based on a real case (regression verification)

## Anti-patterns

| Anti-pattern | Consequence |
|-------------|-------------|
| Made-up flow without a project source | Does not protect a real requirement |
| Long scenario covering 5-7 intents | Unstable, hard to diagnose |
| Checking internal details instead of behavior | Fragile scenario |

---
depends_on:
  - framework/rules/vanessa-test-isolation-policy.mdc
---
