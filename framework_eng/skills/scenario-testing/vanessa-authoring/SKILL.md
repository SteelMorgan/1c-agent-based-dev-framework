---
name: vanessa-authoring
description: Creating and refining Vanessa Automation feature scenarios based on real project requirements. Use when you need to write or update a scenario test, not just run it.
---

# Authoring Vanessa Automation Scenarios

## Purpose

The skill describes how to write Vanessa Automation `.feature` scenarios so that they are review-ready, stable, and grounded in real project requirements.

---

## When to apply

| Trigger | Action |
|---------|--------|
| Need to write a new `.feature` | Use this skill as the baseline authoring |
| Need to refine an existing scenario | Check the requirement source and reduce the scenario to a single behavior |
| Need to choose the next case to cover | Take a real project requirement rather than a made-up case |

---

## Scenario source

Only two sources are acceptable:

1. The specification of a new task — the scenario describes the expected behavior before or alongside implementation.
2. A real existing project case — the scenario documents already implemented behavior and protects against regressions.

Not allowed:

- inventing a scenario without a project source;
- writing “demo” scenarios without a link to a specification or business case.

---

## Basic process

1. Identify the requirement source.
2. Find similar steps in the Vanessa library and the project’s scenarios.
3. Craft one short `smoke` scenario.
4. If no exact step exists — mark `unknown_step_candidate` instead of inventing a low-level BSL step.
5. Separate data setup from the main behavior whenever possible.
6. After writing — hand the scenario over to `vanessa-run`.

---

## Structure of the first scenario

The first scenario for a new case should:

- open the required object/form;
- perform one key action;
- verify one observable consequence.

If the case is longer — split it into multiple scenarios.

---

## What to write in expectations

Correct:

- the state of a form field;
- visibility/accessibility of a command;
- a change of value in a table;
- the appearance or absence of a message or result visible to the user.

Incorrect:

- an internal method call;
- a technical detail not observable from the UI;
- several different expectations in one scenario without a need.

---

## Where scenarios and templates should live

Project-local scenarios:

```text
<project_root>/vanessa-tests/features
<project_root>/vanessa-tests/support
```

Shared templates may live elsewhere:

```text
/.../framework/runtime/vanessa/
```

If the project uses a shared template, it may create a project-local runtime copy based on it. But the project’s own scenarios must remain project-local.

---

## Common mistakes

| Mistake | Consequence | How to avoid |
|--------|-------------|--------------|
| Made-up case | The scenario does not protect a real requirement | Always cite the requirement source |
| Too long scenario | Fragility and poor diagnostics | Split into short smoke/story scenarios |
| Imagined step “from the head” | Mismatch with the Vanessa library | Search for an existing step first |
| Data setup mixed with behavior checks | Scenario becomes hard to read and unstable | Extract setup into a separate part |

---

## Related resources

- `framework/rules/vanessa-scenario-policy.mdc`
- `framework/rules/vanessa-tests-location.mdc`
- `framework/skills/tool-usage/vanessa/vanessa-run/SKILL.md`
- `framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md`

---
depends_on:
  - framework/rules/vanessa-scenario-policy.mdc
  - framework/rules/vanessa-tests-location.mdc
  - framework/rules/vanessa-run-loop.mdc
---
