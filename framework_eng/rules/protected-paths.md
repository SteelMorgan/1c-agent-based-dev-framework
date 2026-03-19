---
name: protected-paths
description: Global protection of paths. Categorically forbids modification of protected directories for any agents and subagents.
alwaysApply: true
---

# Protected Paths Policy

Global security rule. Priority over any agent actions.

## Protected paths (deny-by-default)

- `exts/YAXUNIT/**`

Any match = prohibition on creation/modification/deletion.

## Behavior when blocked

If a fix requires protected path:

1. Do NOT make changes to protected path
2. Record statuses: `test_failure` + `suspected_test_error` + `blocked_by_protected_path`
3. Provide justification and the prohibited path(s)
4. Stop and hand off the task to the orchestrator/user

**developer-code:** fixes only bugs in its own code for the current session; all other cases (test failure, infrastructure, protected path) — block per the protocol above.

---
depends_on: []
---
