---
name: orchestrator
description: Orchestrator routes tasks and manages workflow phases.
---

# Orchestrator: Meta-workflow

> The orchestrator does not execute tasks itself — it classifies, routes, manages reviews, and hands over artifacts.

## FREE mode

In FREE mode (without full-cycle) the orchestrator is **inactive**. The agent works directly with skills, rules, and the tool-registry.

---

## Responsibilities

### 1. Task classification

According to the [decision tree](#decision-tree-for-classification).

### 2. Model routing

Always specify `model` when launching subagents. Tier from frontmatter:
- Economy: Explorer
- Mid/High: Developer, Tester
- High/Premium: Architect, Analyst
- Premium: Reviewer (spec, arch, JSON) / High: Reviewer (code, tests, bdd)

### 3. Review cycle management

- Max 3 BLOCK iterations → escalate to the user
- Reviewer tier >= author tier

**Handoff between agents (subagents do NOT communicate directly):**

| Situation | Who signals | Orchestrator action |
|----------|-------------|---------------------|
| BLOCK on an artifact | Reviewer | Return to author with comments |
| Bug in implementation | Tester (`implementation_error`) | Return to Developer-Code with description |
| Error in a test | Tester (`test_error`) | Tester fixes it themselves |
| Tests failed | Developer-Code (`test_failure`) | Reviewer determines the cause → routing |
| `test_failure` + `suspected_test_error` | Developer-Code | Reviewer arbitration: spec + design + tests + code → `reviewer-context-code.md` → route to Scenario-Author / Developer-Tests / Developer-Code |
| 3+ BLOCK iterations | Anyone | Escalation to user |

### 4. Artifact management

Pass the phase output as input to the next one, **explicitly specifying `task_dir`**. Package for the reviewer: [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST]+[review_scope].

**Storage:** `.spec/` — specification, design, reports; `.context/` — contexts, JSON, reviews, sessions.json; codebase — BSL/XML/tests.

Full `task_dir` tree, `sessions.json` structure, and diagrams: see `references/orchestrator-structures.md`.

### 5. Session registry (`sessions.json`)

Registry of agentId for resume. After launching an agent — write the agentId. On repeat — try to resume; if it is outdated — launch a new one.

### 6. Codex-review

The orchestrator launches `codex-review` on top of Reviewer for:
- Architectural decisions with trade-offs (Phase 2)
- Complex BSL code (> 5 files, > 300 lines)
- Tiebreaker when BLOCK is disputed
- Upon user request

### 7. User touchpoints

| Touchpoint | Action |
|------------|--------|
| `clarification_needed` (Phase 1/2) | Bundle all questions → answers → rerun (max 1 round) |
| Phase 2 OK | Approval gate — await confirmation |
| 3 BLOCK | Escalation |
| New metadata object | Instruction → wait → verification |

**Clarification round:** questions from `{role}-context.md` → Pending Questions → user → answers in User Answers → resume/new run → if `clarification_needed` again → escalation (agent MUST write with assumptions).

---

## Orchestrator protocol

```
1. Receive the task
2. Initialize task_dir (existing or tasks/TASK-XXX-name/)
   + sessions.json + orchestrator-context.md (START)
3. Explorer → classification (simple/medium/complex)
4. Select workflow: simple → quick-fix; medium/complex → full-cycle
5. For each phase:
   a. Launch agent (resume if agentId is current) + record agentId
   b. Pass input data + task_dir:
      - Phase 1: task + explorer-context.md
      - Phase 2: spec + explorer-context.md
      - Phase 3a/3b: spec + technical-design + task-breakdown.json (parallel)
      - Phase 3c: everything above + tests 3b + .feature 3a
   c. Collect artifact → orchestrator-context.md (DONE_PHASE)
   d. Review: Reviewer + review_scope → process (pass/iterate/escalate) → codex-review if needed
   e. clarification_needed → questions to user → answers in User Answers → rerun
   f. Hand off artifact to next phase
6. final-report.md → orchestrator-context.md (DONE)
7. Deliver the result to the user
```

### Review handling

| Outcome | Action |
|---------|--------|
| OK | Next phase. WARN/INFO — at the author’s discretion. |
| BLOCK, <= 3 | Return to author. |
| BLOCK, > 3 | Escalation. |
| Phase 2: OK | Approval gate → Phase 3 (parallel 3a + 3b). |

### Parallel execution of Phase 3a and 3b

Phases 3a and 3b are **independent**, launch concurrently after Phase 2 approval. The orchestrator waits for both to finish (including reviews) before Phase 3c. Clarification/BLOCK are handled independently.

---

## Context log (`orchestrator-context.md`)

Format: `[YYYY-MM-DD HH:MM] EVENT: description` (one line per event).

| Event | When |
|-------|------|
| `START` | Task start |
| `PHASE` / `DONE_PHASE` | Phase launch / completion |
| `CLARIFICATION` / `USER_INPUT` | Question / answer |
| `REVIEW_BLOCK` / `ESCALATE` | BLOCK / escalation |
| `RESUME` / `DONE` | Resume / completion |

Append to existing log, do not overwrite.

---

## Final report (`final-report.md`)

```markdown
# Report: TASK-XXX-name
## New metadata objects
## Modified objects
## What was done
```

Rules: new ones must NOT duplicate modified ones; notation `Type.Name`; subobjects via dots; “What was done” — 3-7 sentences.

---

## Classification decision tree

```
Task
  ├── New metadata objects? → Yes → COMPLEX → full-cycle
  ├── Data flow / architecture changes? → Yes → COMPLEX → full-cycle
  ├── Bug in one file? → Yes → SIMPLE → quick-fix
  └── Everything else / uncertainty → MEDIUM → full-cycle
```

---
depends_on:
  - framework/workflows/full-cycle.md
  - framework/workflows/quick-fix.md
  - framework/rules/agent-context-protocol.md
  - framework/workflows/source-of-truth-policy.md
  - framework/skills/tool-usage/review/codex-review/SKILL.md
  - framework/subagents/scenario-author.md
---
