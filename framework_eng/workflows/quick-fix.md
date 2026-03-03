---
name: quick-fix
description: Quick fix in one file without cross-review.
---


# Workflow: Quick Fix (Quick Fix)

> **Three steps without cross-review.** For simple tasks: bug fix, minor edit, a single point of change.

---

## Purpose

Quick-fix is a lightweight workflow for tasks that do not require a full cycle. One agent (Developer) performs all the work. A reviewer is not involved. Saves time and tokens.

---

## When to use Quick-fix vs Full-cycle

| Criterion | Quick-fix | Full-cycle |
|----------|-----------|------------|
| **Scope of changes** | One file, < 20 lines | Multiple files, architectural decisions |
| **Architectural decisions** | No | Yes |
| **New metadata objects** | No | Yes (or possible) |
| **New features** | No | Yes |
| **Task type** | Bug fix, variable refactoring, pinpoint edit | New feature, integration, module refactoring |

### Quick reminder

- **Quick-fix:** change in one file, fewer than 20 lines, without architectural decisions, without new features
- **Full-cycle:** everything else

---

## Workflow steps

### Step 1: Find (Explorer → Economy)

| Element | Description |
|---------|-------------|
| **Goal** | Locate relevant code and understand the current behavior |
| **Tools** | `navigate_symbol`, `get_call_graph`, `list_metadata_objects`, `get_metadata_structure` |
| **Output** | Module path, understanding of context, list of affected symbols |

**Actions:**
- Use `navigate_symbol` to locate definitions and usages
- Use `get_call_graph` to understand dependencies
- Ensure that the change is localized

---

### Step 2: Fix (Developer → Mid)

| Element | Description |
|---------|-------------|
| **Goal** | Implement the change |
| **Rules** | Follow the BSL coding standards ([coding-standards.md](../skills/bsl-practices/coding-standards.md)) |
| **Output** | Modified BSL module |

**Actions:**
- Apply the minimally required change
- Avoid adding “improvements” beyond the task
- Maintain the project’s coding style

---

### Step 3: Verify (Developer → Mid)

| Element | Description |
|---------|-------------|
| **Goal** | Make sure the change did not break the system |
| **Tools** | `check_syntax`, `run_tests`, `get_diagnostics` |
| **Output** | Confirmation: syntax OK, tests passed (if present), no diagnostics |

**Actions:**
1. **check_syntax** — required for the modified module
2. **run_tests** — if tests exist for the affected module
3. **get_diagnostics** — confirm no LSP errors

---

## Escalation to Full-cycle

If quick-fix is not sufficient — switch to [full-cycle.md](./full-cycle.md).

### Signs that escalation is needed

| Situation | Action |
|----------|--------|
| Tests fail after the change | Fix or escalate to full-cycle (Tester + Reviewer) |
| The issue affects multiple modules | Full-cycle |
| An architectural change is needed | Full-cycle |
| The user requested a review | Full-cycle |
| The change grew beyond 20 lines | Consider full-cycle |

### Escalation protocol

1. Record the current status (what was done, what failed)
2. Pass the context to the orchestrator
3. Launch full-cycle starting with Phase 1 (Analyst) if the context is insufficient, or Phase 3 (Developer) if the specification already exists

---

## Diagram

```mermaid
flowchart LR
    A[Step 1: Find] --> B[Step 2: Fix]
    B --> C[Step 3: Verify]
    C --> D{OK?}
    D -->|Yes| E([Result])
    D -->|No| F[Escalation: full-cycle]
```

---

## Related resources

| Resource | Connection |
|----------|------------|
| [full-cycle.md](./full-cycle.md) | Workflow when escalating |
| [orchestrator.md](./orchestrator.md) | Routing quick-fix vs full-cycle |
| [syntax-checking.md](../skills/tool-usage/syntax-checking.md) | Syntax checking skill |
| [test-execution.md](../skills/tool-usage/test-execution.md) | Test execution skill |


---
depends_on:
  - framework/workflows/full-cycle.md
  - framework/subagents/explorer.md
  - framework/subagents/developer.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/tool-usage/syntax-checking/SKILL.md
  - framework/skills/tool-usage/test-execution/SKILL.md
---
