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

## What is considered a link in the task documentation

A direct link or an explicit path to the created/updated `.feature` is enough for the next agent to quickly open the scenario without searching again.

---
depends_on: []
requires:
  - tools
---
