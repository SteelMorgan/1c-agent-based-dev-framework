---
name: full-cycle
description: Full development cycle with mandatory cross-review at every phase.
---

# Workflow: Full development cycle (Full Cycle)

> Deterministic workflow with cross-review at every phase. For medium and high complexity tasks.

## Phases

### Phase 0: Classification (Explorer → Economy)

Explorer explores the codebase → modules, call graphs, dependencies. The Orchestrator classifies: Simple → quick-fix; Medium/Complex → Phase 1.

Explorer artifacts are passed to Phase 1 and Phase 2 as context.

### Phase 1: Analysis (Analyst → Mid/High)

Input: task + `explorer-context.md`. Analyst creates MADR 4.0 spec + RFC 2119. Review by Reviewer (Premium). Max 3 BLOCK iterations.

### Phase 2: Architecture (Architect → High/Premium)

Input: approved spec + `explorer-context.md`. Architect produces `technical-design.md` + `task-breakdown.json`. Review + **STOP: wait for user OK**.

### Phase 3a + 3b: PARALLEL

- **3a (Scenario-Author):** intent scenarios → `.feature` Vanessa. Review (scope=bdd).
- **3b (Developer-Tests):** MUST scenarios → unit tests (Red). Review (scope=tests).

Both MUST be completed before Phase 3c.

### Phase 3c: Implementation (Developer-Code → High)

Input: everything from Phase 2 + tests from 3b + `.feature` from 3a. Developer-Code writes the code (Green). Only Phase 3b tests. On `test_failure` + `suspected_test_error` → Reviewer arbitration → routing.

Phase 3c starts ONLY after 3a and 3b (including reviews).

### Phase 4: Coverage and regression (Tester → Mid/High)

Tester runs all tests, adds edge cases, integration, regression. Review (High). Phase 4 DOES NOT duplicate Phase 3.

---

## Artifact handoff

| From → To | Artifact |
|--------|----------|
| 0 → 1, 2 | `explorer-context.md` |
| 1 → 2 | `spec.md` |
| 2 → 3a, 3b | spec + technical-design + task-breakdown.json |
| 3a → 3c | `.feature` |
| 3b → 3c | test modules (.bsl) |
| 3c → 4 | BSL + `.feature` + green tests |

**Mandatory fields:** Specification — Context, Requirements, Scope, Test Plan. Technical Design — components, interfaces. Task Breakdown JSON — task_id, task_type, depends_on, spec_refs, acceptance criteria. Code — coding-standards. Tests — linkage to MUST scenarios.

---

## Error handling

| Situation | Action |
|----------|----------|
| BLOCK, <= 3 iterations | Return to author |
| BLOCK, > 3 | Escalate to user |
| User rejected Phase 2 | Architect reworks |
| `test_failure` in Phase 3c | Developer-Code: own code → fix; test → `suspected_test_error` → Reviewer arbitration |
| `test_failure` in Phase 4 | Tester: own test → fix; bug in code → `implementation_error` → Developer |
| `check_syntax` failure | Developer fixes before review |
| MCP unavailable | Escape hatch → escalation |

---
depends_on:
  - framework/workflows/quick-fix.md
  - framework/subagents/explorer.md
  - framework/subagents/analyst.md
  - framework/subagents/architect.md
  - framework/subagents/scenario-author.md
  - framework/subagents/developer-tests.md
  - framework/subagents/developer-code.md
  - framework/subagents/tester.md
  - framework/subagents/reviewer.md
  - framework/rules/tdd-policy.md
---
