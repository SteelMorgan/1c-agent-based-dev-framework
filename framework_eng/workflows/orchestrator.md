---
name: orchestrator
description: Orchestrator routes tasks and manages workflow phases.
---

# Orchestrator: Meta-workflow

> Orchestrator does not execute tasks itself — it classifies, routes, manages reviews, and delivers artifacts.

## FREE mode

In FREE mode (without a full-cycle) the orchestrator is **inactive**. The agent works directly with skills, rules, and the tool registry.

---

## Responsibilities

### 1. Task classification

See the [decision tree](#classification-decision-tree).

### 2. Model routing

**MUST** specify `model` when launching subagents. Tier from frontmatter:
- Economy: Explorer
- Mid/High: Developer, Tester
- High/Premium: Architect, Analyst
- Premium: Reviewer (spec, arch, JSON) / High: Reviewer (code, tests, bdd)

### 3. Review cycle management

- Max 3 iterations of BLOCK → escalate to the user
- Reviewer tier must be >= author tier

**Transfers between agents (subagents DO NOT communicate directly):**

| Situation | Who signals | Orchestrator action |
|----------|-------------|---------------------|
| BLOCK on an artifact | Reviewer | Return to the author with comments |
| Bug in the implementation | Tester (`implementation_error`) | Return to Developer-Code with details |
| Error in a test | Tester (`test_error`) | Tester fixes it themselves |
| Tests failed | Developer-Code (`test_failure`) | Reviewer determines the reason → routing |
| `test_failure` + `suspected_test_error` | Developer-Code | Reviewer arbitration: spec + design + tests + code → `reviewer-context-code.md` → route to Scenario-Author / Developer-Tests / Developer-Code |
| 3+ BLOCK iterations | Any | Escalate to the user |

### 4. Artifact management

Pass the output of each phase to the next phase’s input, **explicitly specifying `task_dir`**. Bundle for the reviewer: [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST]+[review_scope].

**Storage:** `.spec/` — specification, design, reports; `.context/` — contexts, JSON, reviews, sessions.json; codebase — BSL/XML/tests.

The full `task_dir` tree, the `sessions.json` structure, and diagrams are in `references/orchestrator-structures.md`.

### 5. Session registry (`sessions.json`)

Registry of agentIds for resume. After launching an agent — record the agentId. On a repeat launch — try to resume; if it is outdated — start a new run.

### 6. Codex-review

The orchestrator runs `codex-review` on top of the Reviewer for:
- Architectural decisions with trade-offs (Phase 2)
- Complex BSL code (> 5 files, > 300 lines)
- Tiebreaker when BLOCK + dispute arises
- At the user’s request

### 7. User touchpoints

| Touchpoint | Action |
|------------|--------|
| `clarification_needed` (Phase 1/2) | Consolidate all questions into one block → answers → rerun (max 1 round) |
| Phase 2 OK | Approval gate — wait for confirmation |
| 3 BLOCKs | Escalate |
| New metadata object | Instruction → wait → review |

**Clarification round:** questions from `{role}-context.md` → Pending Questions → user → answers in User Answers → resume/new run → if `clarification_needed` repeats → escalate (agent MUST write with assumptions).

---

## Orchestrator protocol

```
1. Receive the task
2. Initialize task_dir (existing or tasks/TASK-XXX-name/)
   + sessions.json + orchestrator-context.md (START)
3. Explorer → classification (simple/medium/complex)
4. Choose workflow: simple → quick-fix; medium/complex → full-cycle
5. For each phase:
   a. Launch the agent (resume if agentId is current) + record agentId
   b. Pass input data + task_dir:
      - Phase 1: task + explorer-context.md
      - Phase 2: spec + explorer-context.md
      - Phase 3a/3b: spec + technical-design + task-breakdown.json (parallel)
      - Phase 3c: everything above + tests 3b + .feature 3a
   c. Collect the artifact → orchestrator-context.md (DONE_PHASE)
   d. Review: Reviewer + review_scope → handle (pass/iterate/escalate) → codex-review if needed
   e. clarification_needed → questions to the user → answers in User Answers → rerun
   f. Pass the artifact to the next phase
6. final-report.md → orchestrator-context.md (DONE)
7. Deliver the result to the user
```

### Review handling

| Result | Action |
|--------|--------|
| OK | Next phase. WARN/INFO — at the author’s discretion. |
| BLOCK, <= 3 | Return to the author. |
| BLOCK, > 3 | Escalate. |
| Phase 2: OK | Approval gate → Phase 3 (parallel 3a + 3b). |

### Parallel Phase 3a and 3b execution

Phase 3a and 3b are **independent** and start simultaneously after Phase 2 approval. The orchestrator waits for both to finish (including reviews) before Phase 3c. Clarifications/BLOCKs are handled independently.

---

## Context log (`orchestrator-context.md`)

Format: `[YYYY-MM-DD HH:MM] EVENT: description` (one line per event).

| Event | When |
|-------|------|
| `START` | Task start |
| `PHASE` / `DONE_PHASE` | Phase launch / completion |
| `CLARIFICATION` / `USER_INPUT` | Question / answer |
| `REVIEW_BLOCK` / `ESCALATE` | BLOCK / escalation |
| `RESUME` / `DONE` | Resume / finish |

Append to the existing log, do not overwrite.

---

## Final report (`final-report.md`)

```markdown
# Report: TASK-XXX-name
## New metadata objects
## Modified objects
## What was done
```

Rules: new items are NOT duplicated under modified; use 1C notation `Type.Name`; subitems via dots; “What was done” should be 3–7 sentences.

---

## Classification decision tree

```
Task
  ├── New metadata objects? → Yes → COMPLEX → full-cycle
  ├── Does it change data flow / architecture? → Yes → COMPLEX → full-cycle
  ├── Bug in a single file? → Yes → SIMPLE → quick-fix
  └── Everything else / uncertainty → MEDIUM → full-cycle
```

---
depends_on:
  - framework/workflows/full-cycle.md
  - framework/workflows/quick-fix.md
  - framework/rules/agent-context-protocol.md
  - framework/skills/tool-usage/review/codex-review/SKILL.md
  - framework/subagents/scenario-author.md
---
