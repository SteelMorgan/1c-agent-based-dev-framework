---
name: vanessa-tests-location
description: Policy for the placement of project-specific Vanessa Automation feature files and references to them in task documentation.
---

# Location of project-specific Vanessa scenarios

## MUST

| Requirement | Description |
|------------|-------------|
| Project feature files in `vanessa-tests/` | Project-specific scenarios MUST be stored in `<project_root>/vanessa-tests/features` |
| Project support files nearby | Project-specific support/fixtures MUST be stored in `<project_root>/vanessa-tests/support` |
| Links in task documentation | If a feature file is created or updated as part of a task, the task documentation MUST include a link to that file |
| Do not mix with shared templates | Project-specific scenarios must not be stored in the framework shared runtime/template directory |

## Separation

### Shared / universal

- `tools/runtime/vanessa/*.json`
- Vanessa library steps in the tools directory

### Project-local

- `<project_root>/vanessa-tests/features`
- `<project_root>/vanessa-tests/support`

## Project step library

In Vanessa, a step is an exported subscenario (`@exportscenarios`) in a regular `.feature` file. There is no separate "step processing"; the project's step library is the `.feature` files themselves in `vanessa-tests/features/`.

| Requirement | Description |
|------------|-------------|
| Project `@exportscenarios` in `features/steps/` | New reusable steps MUST be placed in `<project_root>/vanessa-tests/features/steps/<feature>.feature`, unless the project already has a different arrangement (then follow the existing one) |
| Group by feature | The file name and step body MUST reflect the business domain (for example, `customer-order.feature`), NOT the task ID |
| Step name without task ID | You must not put `@task-<ID>` on `@exportscenarios` or mention the ID in the wording - the step is reused across tasks |
| BSL steps are an escape hatch | Function steps in `vanessa-tests/support/` MUST be used only when composition of subscenarios cannot express the need (parsing, filesystem, non-trivial calculations); the rationale belongs in the step author's context |
| Reuse first | Before creating a new step, MUST look for matches: the standard Vanessa library -> project `features/**` -> `support/`. A match of >= ~80% should be parameterized and reused, not duplicated |

## What is considered a link in the task documentation

A direct link or an explicit path to the created/updated `.feature` is enough for the next agent to quickly open the scenario without searching again.

---
depends_on: []
requires:
  - tools
---
