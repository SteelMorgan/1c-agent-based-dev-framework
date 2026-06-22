---
name: vanessa-tests-location
description: You create / update a Vanessa feature file → follow the location convention. Apply the `vanessa-authoring` skill for details.
alwaysApply: true
---

# Location of Vanessa scenarios

> **Trigger:** creating or modifying a `.feature` file in the project. When triggered, apply the `vanessa-authoring` skill (`framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md`) for formatting details.

## MUST (location convention)

| Requirement | Rule |
|------------|---------|
| Project feature files | MUST be stored in `<project_root>/vanessa-tests/features` |
| Project support files | MUST be stored in `<project_root>/vanessa-tests/support` |
| Link in task documentation | If a feature file is created/modified, the task documentation MUST include a direct link or an explicit path |
| Do not mix with shared | Project-specific scenarios cannot be stored in the framework shared runtime/template directory |

## MUST (step library)

| Requirement | Rule |
|------------|---------|
| Reusable steps | MUST be placed in `vanessa-tests/features/steps/<feature>.feature` (file name - by business domain, not by task ID) |
| Reuse-first | Before creating a step, MUST search: the standard Vanessa library → project `features/**` → `support/`. A match of ≥ 80% must be parameterized, not duplicated |
| BSL steps | Function steps in `support/` MUST be used only when composition of subscenarios cannot express it; the justification is in the step author's context |
| `@exportscenarios` without task ID | You cannot put `@task-<ID>` on reusable steps - the step lives across tasks |

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
---
