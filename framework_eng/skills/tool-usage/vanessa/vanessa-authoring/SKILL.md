---
name: vanessa-authoring
description: Creating and refining Vanessa Automation feature scenarios based on real project requirements. Use when you need to write or update a scenario test, not just run it.
---

# Authoring Vanessa Automation Scenarios

## Purpose

This skill explains how to write Vanessa Automation `.feature` scenarios so they are ready for review, stable, and rooted in real project requirements.

---

## When to apply

| Trigger | Action |
|---------|--------|
| Need to write a new `.feature` | Use this skill as the baseline authoring |
| Need to polish an existing scenario | Check the requirement source and trim the scenario down to one behavior |
| Need to pick the next case to cover | Take a real project requirement instead of a made-up case |

---

## Scenario source

Only two sources are allowed:

1. The specification for a new task - the scenario describes the expected behavior before or in parallel with implementation.
2. A real existing project case - the scenario captures behavior that is already implemented and guards against regression.

Not allowed:

- inventing a scenario without a project source;
- writing "demo" scenarios without a link to the specification or business case.

---

## Basic process

1. Identify the requirement source.
2. Find similar steps in the Vanessa library and the project's scenarios.
3. Formulate one short `smoke` scenario.
4. If there is no exact step - mark `unknown_step_candidate` rather than invent a low-level BSL step.
5. Separate data setup from the main behavior whenever possible.
6. After writing - hand the scenario over to `vanessa-run`.

---

## Structure of the first scenario

The first scenario for a new case should:

- open the required object/form;
- perform one key action;
- verify one observable consequence.

If the case is longer - break it into several scenarios.

---

## What to write in expectations

Correct:

- the state of a form attribute;
- visibility/accessibility of a command;
- a change in a table value;
- the appearance or absence of a message or result that the user can see.

Incorrect:

- an internal method call;
- a technical detail that is not observable from the UI;
- several different expectations in one scenario without a reason.

---

## Where scenarios and templates should live

Project-local scenarios:

```text
<project_root>/vanessa-tests/features
<project_root>/vanessa-tests/support
```

Shared templates may live separately:

```text
/.../tools/runtime/vanessa/
```

If a project uses a shared template, it may create a project-local runtime copy based on it. But the project's own scenarios must stay project-local.

---

## Common mistakes

| Mistake | Consequence | How to avoid |
|--------|-------------|--------------|
| Made-up case | The scenario does not protect a real requirement | Always specify the requirement source |
| Too long scenario | Fragility and poor diagnostics | Split it into short smoke/story scenarios |
| Invented step "from the head" | Mismatch with the Vanessa library | Search for an existing step first |
| Data setup mixed with behavior checks | The scenario becomes hard to read and unstable | Extract the setup separately |

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
  - framework/skills/tool-usage/vanessa/vanessa-run/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
---
