---
name: vanessa-authoring
description: Creating and refining Vanessa Automation feature scenarios based on actual project requirements. Use when you need to write or update a scenario test rather than just run it.
---

# Vanessa Automation Scenario Authoring

## When to apply

| Trigger | Action |
|---------|--------|
| Need to write a new `.feature` | Use this skill as baseline authoring |
| Need to refine an existing scenario | Reduce the scenario to one behavior |
| Need to choose the next case for coverage | Take a real project requirement |

---

## Source of the scenario

The scenario sourcing policy — see rule `vanessa-scenario-policy`.

---

## Basic process

1. Identify the requirement source (specification or existing business case).
2. Find similar steps in the Vanessa library and project scenarios.
3. Formulate a single short `smoke` scenario: open the form → one action → one observable consequence.
4. If an exact step is missing, mark it as `unknown_step_candidate` rather than inventing a low-level BSL step.
5. Separate data setup from the core behavior.
6. After writing it, hand the scenario off to `vanessa-run`.

---

## Expectations in scenarios

Allowed: control state on a form, command visibility/availability, a change of a value in a table, the appearance/disappearance of a message.

Not allowed: internal method calls, technical detail, something not observable from the UI.

---

## Scenario placement

Project-local scenarios:

```text
<project_root>/vanessa-tests/features
<project_root>/vanessa-tests/support
```

Shared templates: `tools/runtime/vanessa/`. Project scenarios are always project-local.

---

## Common mistakes

| Mistake | How to avoid |
|--------|--------------|
| Made-up case | Always specify the requirement source |
| Scenario that is too long | Split into short smoke scenarios |
| Invented step | First search for an existing step in the library |
| Data setup mixed with verification | Keep setup separate |

---
depends_on:
  - framework/rules/vanessa-scenario-policy.mdc
  - framework/rules/vanessa-tests-location.mdc
  - framework/rules/vanessa-run-loop.mdc
  - framework/skills/tool-usage/vanessa/vanessa-run/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
requires:
  - tools
---
