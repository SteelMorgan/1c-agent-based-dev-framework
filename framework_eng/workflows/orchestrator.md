---
name: orchestrator
description: The Orchestrator routes tasks and manages workflow phases.
---



# Orchestrator: Meta-Workflow

> **The Orchestrator** is a meta-workflow that routes tasks, selects workflows, and coordinates agent interactions.

---

## Purpose

The Orchestrator does not perform tasks itself. It:
1. Classifies incoming tasks
2. **Initializes the task catalog** (`task_dir`)
3. Selects a workflow (quick-fix or full-cycle)
4. Assigns model tiers to each agent
5. Manages review cycles
6. Passes artifacts between agents, explicitly specifying `task_dir`
7. Determines user interaction points
8. **Maintains an agent session registry** (`task_dir/sessions.json`) for possible resume
9. **Runs codex-review** for complex artifacts as a second independent opinion
10. **Maintains a context log** (`task_dir/context.log`) — a minimal record of key events to resume the task
11. **Generates the final report** (`task_dir/report.md`) after task completion

---

## FREE mode: orchestrator disabled

**IMPORTANT:** In FREE mode (without full-cycle) the orchestrator is **inactive**.

The agent works directly, using:
- Skills
- Rules
- Tool-registry

The user provides a task, the agent solves it in free mode. Cross-review is optional. Phases are not enforced.

---

## Responsibilities of the Orchestrator

### 1. Task classification

Determines task complexity and chooses the workflow using the [decision tree](#decision-tree-for-classification).

### 2. Model routing

IMPORTANT!!! When launching subagents via Task **ALWAYS** specify the `model` parameter. Each agent has a pre-configured model (field `model` in frontmatter):
- Economy — Explorer
- Mid/High — Developer, Tester
- High/Premium — Architect, Analyst
- Premium — Reviewer (spec, Task Breakdown JSON decomposition, architecture)
- High — Reviewer (code, tests)
**NEVER** launch a subagent without an explicit `model`.

### 3. Review cycle management

- Tracks iterations (max. 3)
- On BLOCK → returns to the author with comments
- On 3+ BLOCK without resolution → escalates to the user
- Ensures reviewer tier ≥ author tier

**Returns between agents go through the Orchestrator:**

Subagents do not communicate directly — any return is handled by the Orchestrator.

| Situation | Who signals | Orchestrator action |
|----------|-------------|----------------------|
| Reviewer placed a BLOCK on an artifact | Reviewer | Return the artifact to the phase author with comments |
| Tester found a bug in the implementation | Tester (label `implementation_error`) | Return Developer-Code with details: which test, expected result, actual result |
| Tester found an error in their test | Tester (label `test_error`) | Tester fixes it themselves; the Orchestrator does not intervene |
| Developer-Code: tests failed (label `test_failure`) | Developer-Code | Launch Reviewer to determine cause: bug in test → return Developer-Tests; bug in code → return Developer-Code |
| 3+ iterations without clearing BLOCK | Reviewer / any agent | Escalate to the user, stop |

### 4. Artifact management

- Passes the output of one phase to the input of the next, **explicitly specifying `task_dir`** to each subagent
- Stores/restores context between sessions (if supported by the adapter)
- Builds the [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST] package for the reviewer, where Phase 2 [ARTIFACT] includes both Technical Design and the Task Breakdown JSON decomposition

**Storage separation:**

| Data type | Location |
|-----------|----------|
| Specification, technical design, Task Breakdown JSON | `task_dir/` |
| Review results | `task_dir/` |
| Testing reports | `task_dir/` |
| Agent session registry | `task_dir/sessions.json` |
| BSL code, tests, XML metadata | Project codebase (separate directory) |

**Structure of `task_dir`:**

```
tasks/
└── TASK-001-name/
    ├── context.log                ← Orchestrator (context log, maintained continuously)
    ├── report.md                  ← Orchestrator (final report, compiled after completion)
    ├── sessions.json              ← Orchestrator (registry of agentId values for all agents)
    ├── explorer-context.md        ← Explorer (Phase 0)
    ├── analyst-context.md         ← Analyst (Phase 1)
    ├── spec.md                    ← Analyst (Phase 1)
    ├── architect-context.md       ← Architect (Phase 2)
    ├── technical-design.md        ← Architect (Phase 2)
    ├── task-breakdown.json        ← Architect (Phase 2)
    ├── developer-tests-context.md ← Developer-Tests (Phase 3a)
    ├── developer-code-context.md  ← Developer-Code (Phase 3b)
    ├── tester-context.md          ← Tester (Phase 4)
    ├── reviewer-context-spec.md   ← Reviewer (Phase 1)
    ├── reviewer-context-arch.md   ← Reviewer (Phase 2)
    ├── reviewer-context-tests.md  ← Reviewer (Phase 3a)
    ├── reviewer-context-code.md   ← Reviewer (Phase 3b)
    ├── reviewer-context-tester.md ← Reviewer (Phase 4)
    └── test-report.md             ← Tester (Phase 4)
```

### 5. Agent session registry (sessions.json)

The Orchestrator maintains `task_dir/sessions.json` — a registry of agentId values for all launched agents. It is used to `resume` when restarting the same agent (BLOCK → fix → re-review, clarification round, etc.).

**Structure:**

```json
{
  "explorer":         "agent-xxx",
  "analyst":          "agent-yyy",
  "architect":        "agent-zzz",
  "developer-tests":  "agent-aaa",
  "developer-code":   "agent-bbb",
  "tester":           "agent-ccc",
  "reviewer-spec":    "agent-ddd",
  "reviewer-arch":    "agent-eee",
  "reviewer-tests":   "agent-fff",
  "reviewer-code":    "agent-ggg",
  "reviewer-tester":  "agent-hhh"
}
```

**Protocol:**
- After each agent launch — record the agentId under the corresponding key
- When relaunching — read sessions.json, try to `resume agentId`; if the agentId is outdated — start a new run and update the entry
- Reviewer is launched independently for each scope (`reviewer-spec`, `reviewer-arch`, etc.) — each has its own key

### 6. Codex-review as a second independent opinion

The Orchestrator runs `codex-review` (CLI) **on top of the main Reviewer** for complex artifacts. Reviewer does not launch it themselves — this is the Orchestrator’s responsibility.

**When to launch:**

| Condition | Action |
|-----------|--------|
| Architectural decision with trade-offs (Phase 2) | Run codex-review after the Reviewer |
| Complex BSL code (> 5 files, > 300 lines) | Run codex-review after the Reviewer |
| Reviewer placed BLOCK, author disputes it | Run codex-review as a tiebreaker |
| Upon user request `/review-gpt`, `/review-all` | Launch immediately |

**How to launch:** see the `codex-review` skill.

### 5. User interaction points

| Point | Action |
|-------|--------|
| Phase 1: Analyst returned `clarification_needed` | Ask all questions to the user in a single batch, collect answers, restart the Analyst with clarifications (max. 1 round) |
| Phase 2: Architect returned `clarification_needed` | Ask all questions to the user in a single batch, collect answers, restart the Architect with clarifications (max. 1 round) |
| Phase 2 (architecture) | Approval gate — wait for user confirmation |
| Escalation (3 BLOCK) | Request a decision from the user |
| New metadata object | Protocol “agent → user”: instruction → wait for creation → verification |

**Clarification protocol (clarification round):**

```
Agent → clarification_needed
  │  (questions recorded in {role}-context.md → Pending Questions)
  ▼
Orchestrator reads {role}-context.md → asks questions to the user
  │
  ▼
User responds
  │
  ▼
Orchestrator records answers in {role}-context.md → User Answers
  │
  ├── agentId still valid? → resume (optimization, same session)
  └── agentId outdated?  → relaunch the agent with task_dir
                           (agent reads context itself at step 1)
  │
  ▼
Agent continues with the preserved context, without repeating research
  │
  ▼
Specification / technical design are ready
(If clarification_needed appears again → escalate to the user,
 do not start a third round — the agent MUST write the artifact with assumptions)
```

---

## Orchestrator protocol

### Sequence of actions

```
1. Receive the task from the user
   ↓
2. Initialize task_dir:
   - If a task number/path is provided → use the existing directory
   - Otherwise → create tasks/TASK-XXX-name/
   - Create/read task_dir/sessions.json
   - Create/update task_dir/context.log: log the START event with timestamp and task text
   ↓
3. Launch Explorer to investigate the codebase
   - Explorer returns: list of affected modules, call graphs (incoming & outgoing), dependency depth, number of call sites
   - Save the Explorer artifact in task_dir (explorer-context.md)
   - Record Explorer agentId in sessions.json → key "explorer"
   - Based on these data, classify the task (simple / medium / complex)
   ↓
4. Based on the classification, choose the workflow:
   - Simple → quick-fix.md
   - Medium/Complex → full-cycle.md
   ↓
5. For each phase of the selected workflow:
   a. Launch the agent (model defined in agent frontmatter)
      - Optimization: read sessions.json; if an agentId exists for this role → try to resume
      - After launch: write the agentId into sessions.json → agent role key
   b. Provide input + explicitly task_dir
      - **For Phase 1 (Analyst):** task + `explorer-context.md` (module list, call graphs)
      - **For Phase 2 (Architect):** approved spec + `explorer-context.md` (call graphs, dependencies)
   c. Collect the output artifact → save it in task_dir
      - Log in context.log: phase completion event (agent, result — OK / BLOCK / clarification_needed)
   d. If review is required:
      - Launch Reviewer with [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST]+[review_scope]
      - Explicitly provide `review_scope`: "spec" | "arch" | "tests" | "code" | "tester"
      - For Phase 2, include Technical Design + Task Breakdown JSON decomposition in [ARTIFACT]
      - Record Reviewer agentId in sessions.json → key "reviewer-{scope}"
      - Save the review result in task_dir (reviewer-context-{scope}.md)
      - Process the outcome (pass / iterate / escalate)
      - If needed: launch codex-review as a second opinion (see section 6)
   e. If the agent returned `clarification_needed` (Phase 1 — Analyst, Phase 2 — Architect):
      - Read `{role}-context.md` from task_dir — it contains the list of questions
      - Ask ALL questions to the user in a single batch
      - Wait for answers
      - Record responses in the `User Answers` section of `{role}-context.md`
      - Restart the agent with the original task + task_dir
        (the agent will read the context and answers at startup)
      - Optimization: if the previous agentId is still valid — use resume instead of a new run
      - If clarification_needed occurs again → escalate to the user (do not repeat)
   f. Pass the artifact to the next phase
   ↓
6. Generate the final report task_dir/report.md (see format below)
   - Log in context.log: the DONE event
   ↓
7. Deliver the result to the user
```

### Step 4d elaboration (review handling)

| Review outcome | Action |
|----------------|--------|
| OK (no BLOCK) | Proceed to the next phase. WARN/INFO — at the author’s discretion (can be fixed later). |
| BLOCK, iteration ≤ 3 | Return the artifact to the author with comments. Repeat the cycle. Task Breakdown JSON follows the same iteration rules. |
| BLOCK, iteration > 3 | Escalate to the user. Stop. For Task Breakdown JSON: >3 iterations are forbidden; a user decision is required. |
| Phase 2: OK | Stop. Request user confirmation. After confirmation — proceed to Phase 3. |

---

## Context log (context.log)

A minimalist log of key events. It is maintained continuously — enabling resumption from the same point if the Orchestrator stops.

**Entry format:**
```
[YYYY-MM-DD HH:MM] EVENT: description
```

**Key events to record:**

| Event | When to log |
|-------|-------------|
| `START` | Task start, task text on a single line |
| `PHASE` | Launch of each phase (Explorer, Analyst, Architect, Developer, Tester, Reviewer) |
| `DONE_PHASE` | Phase completion, result (OK / BLOCK / clarification_needed) |
| `CLARIFICATION` | Requesting user clarification |
| `USER_INPUT` | Received user response |
| `REVIEW_BLOCK` | Reviewer placed BLOCK, iteration number |
| `ESCALATE` | Escalated to the user |
| `RESUME` | Task resumption after halt |
| `DONE` | Task finished |

**Example:**
```
[2026-03-02 10:15] START: Add the attribute "ДатаОтгрузки" to the document Реализация
[2026-03-02 10:16] PHASE: Explorer — codebase investigation
[2026-03-02 10:18] DONE_PHASE: Explorer — OK, task classified as MEDIUM
[2026-03-02 10:18] PHASE: Analyst — specification drafting
[2026-03-02 10:22] DONE_PHASE: Analyst — clarification_needed
[2026-03-02 10:23] CLARIFICATION: Asked the user: Should the attribute be Date or DateTime?
[2026-03-02 10:25] USER_INPUT: Date
[2026-03-02 10:28] DONE_PHASE: Analyst — OK, spec.md created
[2026-03-02 10:29] PHASE: Reviewer (scope: spec)
[2026-03-02 10:31] DONE_PHASE: Reviewer spec — OK
...
[2026-03-02 11:45] DONE
```

**Rules:**
- Do not duplicate artifact contents — only record the fact of the event.
- One line per event maximum.
- When resuming a task — append to the existing log, do not overwrite.

---

## Final report (report.md)

Formed by the Orchestrator after all task phases complete.

**Format:**

```markdown
# Report: TASK-XXX-name

## New metadata objects
- Справочник.НовыйСправочник
- Документ.НовыйДокумент.Форма.ФормаДокумента

## Modified objects
<!-- Objects listed in "New" are not included here -->
- Документ.Реализация — added attribute ДатаОтгрузки
- РегистрНакопления.ТоварыНаСкладах — added a new filter
- ОбщийМодуль.РаботаСДокументами — updated the procedure ПровестиДокумент

## What was done
A brief semantic description in free form — what was implemented,
which business problem it addresses, which key decisions were made.
```

**Rules:**
- If an object is listed under “New” — do **not duplicate** it under “Modified”.
- Specify metadata objects in the 1C notation `Type.Name` (e.g., `Справочник.Контрагенты`).
- Sub-objects (forms, attributes, tablular parts) are specified with dots: `Документ.Реализация.Форма.ФормаДокумента`.
- The “What was done” section is free text, 3–7 sentences.

---

## Decision tree for classification

```
User task
         │
         ├─► Requires new metadata objects?
         │        Yes → COMPLEX → full-cycle
         │
         ├─► Is the data flow / architecture being changed?
         │        Yes → COMPLEX → full-cycle
         │
         ├─► Is it a bug fix in a single file?
         │        Yes → SIMPLE → quick-fix
         │
         ├─► Everything else
         │        → MEDIUM → full-cycle
         │
         └─► (Default when uncertain)
                   → MEDIUM → full-cycle
```

### Decision tree rules

| Question | "Yes" answer | Complexity |
|----------|----------------|------------|
| Are new metadata objects required (directories, documents, registers, forms)? | Yes | Complex |
| Is the data flow or architecture of the solution being changed? | Yes | Complex |
| Is this a fix for a bug in a single file? | Yes | Simple |
| Everything else | — | Medium |

**When uncertain:** treat as medium, use full-cycle.

---

## Orchestrator diagram

```
  ┌──────────┐
  │  Task    │
  └─────┬────┘
        ▼
  ┌──────────────────────┐
  │ Explorer (Economy)   │
  │ task classification  │
  └──────────┬───────────┘
             │
     ┌───────┴────────┐
     ▼                ▼
 [Simple]     [Medium/Complex]
     │                │
     ▼                ▼
┌──────────┐   ┌─────────────────────────────────────────┐
│quick-fix │   │              full-cycle                  │
│          │   │                                          │
│ 1. Найти │   │  Analyst ──► Review ──► Architect ──►    │
│ 2. Fixить│   │  Review ──► ⏸ User OK? ──► Developer    │
│ 3. Check │   │  ──► Review ──► Tester ──► Review ──►   │
│          │   │  Formatter                               │
└─────┬────┘   └───────────────────┬─────────────────────┘
      │                            │
      └────────────┬───────────────┘
                   ▼
            ┌────────────┐
            │  Result    │
            └────────────┘
```

---

## Related resources

| Resource | Relation |
|----------|----------|
| [full-cycle.md](./full-cycle.md) | Deterministic workflow |
| [quick-fix.md](./quick-fix.md) | Lightweight workflow |
| [cross-review-policy.md](../rules/cross-review-policy.md) | Review protocol |
| [docs/SPEC-001-framework-architecture.md](../../docs/SPEC-001-framework-architecture.md) | Framework architecture |

---
depends_on:
  - framework/workflows/full-cycle.md
  - framework/workflows/quick-fix.md
  - framework/rules/cross-review-policy.md
  - framework/rules/agent-context-protocol.md
  - framework/skills/tool-usage/codex-review/SKILL.md
---
