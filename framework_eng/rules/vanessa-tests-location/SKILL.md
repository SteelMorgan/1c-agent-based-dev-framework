---
name: vanessa-tests-location
description: "When adding Vanessa features, follow the location rules"
alwaysApply: true
---

# Placement of Vanessa Scenarios

> **Trigger:** creating or modifying a `.feature` file in the project. When triggered, apply the `vanessa-authoring` skill (`framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md`) for formatting details.

## MUST (location convention)

| Requirement | Rule |
|------------|---------|
| Project feature files | MUST be stored in `<project_root>/vanessa-tests/features` |
| Project support files | MUST be stored in `<project_root>/vanessa-tests/support` |
| Link in task documentation | If a feature file is created/changed, the task documentation MUST contain a direct link or an explicit path |
| Do not mix with shared | Project-specific scenarios must not be stored in the shared runtime/template directory of the framework |

## MUST (step library)

| Requirement | Rule |
|------------|---------|
| Reusable steps | MUST be placed in `vanessa-tests/features/steps/<functionality>.feature` (file name based on the business domain, not the task ID) |
| Reuse-first | Before creating a step, MUST search: the standard Vanessa library -> project `features/**` -> `support/`. If a match is ≥ 80%, parameterize it instead of duplicating it |
| BSL steps | Function steps in `support/` MUST be used only when the scenario composition cannot express the behavior; the rationale belongs in the step author's context |
| `@exportscenarios` without task-ID | Do not put `@task-<ID>` on reusable steps - the step lives across tasks |

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
---
