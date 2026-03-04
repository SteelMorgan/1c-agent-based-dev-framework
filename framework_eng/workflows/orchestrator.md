---
name: orchestrator
description: The Orchestrator routes tasks and manages workflow phases.
---


# Orchestrator: Meta-workflow

> **The Orchestrator** is a meta-workflow that routes tasks, selects workflows, and manages agent interactions.

---

## Purpose

The Orchestrator does not execute tasks itself. It:
1. Classifies incoming tasks
2. **Initializes the task catalog** (`task_dir`)
3. Selects a workflow (quick-fix or full-cycle)
4. Assigns tier models to each agent
5. Manages review cycles
6. Passes artifacts between agents, explicitly specifying `task_dir`
7. Defines user interaction points
8. **Maintains the agent session registry** (`task_dir/.context/sessions.json`) for possible resume
9. **Starts codex-review** for complex artifacts as a second independent opinion
10. **Keeps the context log** (`task_dir/.context/orchestrator-context.md`) — a minimalist journal of key events used to resume the task
11. **Produces the final report** (`task_dir/.spec/final-report.md`) after the task is completed

---

## FREE mode: orchestrator disabled

**IMPORTANT:** In FREE mode (without full-cycle) the Orchestrator is **not active**.

The agent works directly, using:
- Skills
- Rules
- Tool-registry

The user gives a task, the agent solves it in free mode. Cross-review is optional. Phases are not enforced.

---

## Orchestrator responsibilities

### 1. Task classification

Determines the task complexity and selects a workflow according to the [decision tree](#decision-tree-for-classification).

### 2. Model routing

IMPORTANT!!! When launching subagents via Task, **ALWAYS** specify the `model` parameter. Each agent has a predefined model (field `model` in frontmatter):
- Economy — Explorer
- Mid/High — Developer, Tester
- High/Premium — Architect, Analyst
- Premium — Reviewer (spec, Task Breakdown JSON decomposition, architecture)
- High — Reviewer (code, tests)

**NEVER** launch a subagent without an explicit `model`.

### 3. Review cycle management

- Tracks iterations (max. 3)
- On BLOCK → returns to the author with feedback
- On 3+ BLOCKs without resolution → escalate to the user
- Ensures reviewer tier ≥ author tier

**Return between agents via the Orchestrator:**

Subagents do not communicate directly — all returns go through the Orchestrator.

| Situation | Who signals | Orchestrator action |
|----------|-------------------|-----------------------|
| Reviewer placed BLOCK on an artifact | Reviewer | Return the artifact to the phase author with comments |
| Tester found a bug in implementation | Tester (label `implementation_error`) | Return to Developer-Code with description: which test, expected vs actual |
| Tester found an error in their own test | Tester (label `test_error`) | Tester fixes it themselves, Orchestrator does not interfere |
| Developer-Code: tests failed (label `test_failure`) | Developer-Code | Start Reviewer to determine: bug in the test → return to Developer-Tests; bug in code → return to Developer-Code |
| Developer-Code: `test_failure` + `suspected_test_error` | Developer-Code | Start Reviewer arbitration: correlate spec + technical-design + tests + code, log in `reviewer-context-code.md` which artifact is faulty (`tests` or `code`). Then the Orchestrator routes the task based on the Reviewer summary: to Developer-Tests or Developer-Code; the decision is recorded in `orchestrator-context.md`. |
| 3+ iterations without removing BLOCK | Reviewer / any agent | Escalate to the user, stop |

### 4. Artifact management

- Passes the output of one phase to the next, **explicitly supplying `task_dir`** to each subagent
- Stores/restores context between sessions (if the adapter supports it)
- Builds a package [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST] for the reviewer, where for Phase 2 [ARTIFACT] includes both the Technical Design and Task Breakdown JSON decomposition

**Storage separation:**

| Data type | Storage location |
|------------|--------------|
| Specification, technical design, test-report, final-report | `task_dir/.spec/` |
| Task Breakdown JSON | `task_dir/.context/` |
| Review results | `task_dir/.context/` |
| Agent session registry | `task_dir/.context/sessions.json` |
| Agent context files | `task_dir/.context/` |
| BSL code, tests, metadata XML | Project codebase (separate directory) |

**Structure of `task_dir`:**

```
tasks/
└── TASK-001-name/
    ├── .context/                     ← Agent contexts and brief phase results
    │   ├── sessions.json             ← Orchestrator (registry of agentId for all agents)
    │   ├── orchestrator-context.md   ← Orchestrator (context log, kept continuously)
    │   ├── explorer-context.md       ← Explorer (Phase 0)
    │   ├── analyst-context.md        ← Analyst (Phase 1)
    │   ├── architect-context.md      ← Architect (Phase 2)
    │   ├── developer-tests-context.md← Developer-Tests (Phase 3a)
    │   ├── developer-code-context.md ← Developer-Code (Phase 3b)
    │   ├── tester-context.md         ← Tester (Phase 4)
    │   ├── reviewer-context-spec.md  ← Reviewer (Phase 1)
    │   ├── reviewer-context-arch.md  ← Reviewer (Phase 2)
    │   ├── reviewer-context-tests.md ← Reviewer (Phase 3a)
    │   ├── reviewer-context-code.md  ← Reviewer (Phase 3b)
    │   ├── reviewer-context-tester.md← Reviewer (Phase 4)
    │   └── task-breakdown.json       ← Architect (Phase 2)
    └── .spec/                        ← Main specification artifacts and final reports
        ├── spec.md                   ← Analyst (Phase 1)
        ├── technical-design.md       ← Architect (Phase 2)
        ├── test-report.md            ← Tester (Phase 4)
        └── final-report.md           ← Orchestrator (final report)
```

### 5. Agent session registry (`task_dir/.context/sessions.json`)

The Orchestrator maintains `task_dir/.context/sessions.json` — a registry of agentId for all launched agents. It is used for `resume` when rerunning the same agent (BLOCK → fix → re-review, clarification round, etc.).

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
- On rerun — read `task_dir/.context/sessions.json`, try `resume agentId`; if agentId is outdated — launch anew and update the entry
- Reviewer is launched separately for each scope (`reviewer-spec`, `reviewer-arch`, etc.) — each has its own key

### 6. Codex-review as a second independent opinion

The Orchestrator starts `codex-review` (CLI) **on top of the main Reviewer review** for complex artifacts. Reviewer does not launch it — this is the Orchestrator’s responsibility.

**When to run:**

| Condition | Action |
|---------|----------|
| Architectural decision with trade-offs (Phase 2) | Run codex-review after Reviewer |
| Complex BSL code (> 5 files, > 300 lines) | Run codex-review after Reviewer |
| Reviewer placed BLOCK and the author disputes it | Run codex-review as a tiebreaker |
| Upon user request `/review-gpt`, `/review-all` | Run immediately |

**How to run:** see the `codex-review` skill.

### 5. User interaction points

| Point | Action |
|-------|----------|
| Phase 1: Analyst returned `clarification_needed` | Ask the user all questions in one block, gather answers, rerun the Analyst with clarifications (max. 1 round) |
| Phase 2: Architect returned `clarification_needed` | Ask the user all questions in one block, gather answers, rerun the Architect with clarifications (max. 1 round) |
| Phase 2 (architecture) | Approval gate — wait for user confirmation |
| Escalation (3 BLOCKs) | Request a decision from the user |
| New metadata object | Protocol “agent → user”: instruction → wait for creation → verification |

**Clarification protocol (clarification round):**

```
Agent → clarification_needed
  │  (questions recorded in task_dir/.context/{role}-context.md → Pending Questions)
  ▼
Orchestrator reads task_dir/.context/{role}-context.md → asks questions to the user
  │
  ▼
User answers
  │
  ▼
Orchestrator records answers in the User Answers section of task_dir/.context/{role}-context.md
  │
  ├── is agentId still valid? → resume (optimization, same session)
  └── agentId outdated?  → relaunch the agent with task_dir
                           (agent reads the context at step 1)
  │
  ▼
Agent continues with preserved context, without redoing exploration
  │
  ▼
Specification / technical design ready
(if clarification_needed appears again → escalate to the user,
 not a third round — the agent MUST write the artifact with assumptions)
```

---

## Orchestrator protocol

### Action sequence

```
1. Receive the task from the user
   ↓
2. Initialize task_dir:
   - If a task number/path is provided → use the existing directory
   - Otherwise → create tasks/TASK-XXX-name/
   - Create/read task_dir/.context/sessions.json
   - Create/update task_dir/.context/orchestrator-context.md: write the START event with date-time and task text
   ↓
3. Launch Explorer to explore the codebase
   - Explorer returns: list of affected modules, call graphs (incoming + outgoing),
     dependency depth, number of call sites
   - Save the Explorer artifact in `task_dir/.context/explorer-context.md`
   - Record Explorer agentId in `task_dir/.context/sessions.json` → key "explorer"
   - Based on this data, classify the task (simple / medium / complex)
   ↓
4. Choose a workflow based on the classification:
   - Simple → quick-fix.md
   - Medium/Complex → full-cycle.md
   ↓
5. For each phase of the chosen workflow:
   a. Launch the agent (model defined in agent frontmatter)
      - Optimization: read `task_dir/.context/sessions.json`; if an agentId exists for this role → try to resume
      - After launch: record the agentId in `task_dir/.context/sessions.json` → the agent role key
   b. Provide input plus explicit task_dir
      - **For Phase 1 (Analyst):** task + `task_dir/.context/explorer-context.md` (module list, call graphs)
      - **For Phase 2 (Architect):** approved spec + `task_dir/.context/explorer-context.md` (call graphs, dependencies)
   c. Collect the output artifact → save it in task_dir
      - Write to `task_dir/.context/orchestrator-context.md`: phase completion event (agent, result — OK / BLOCK / clarification_needed)
   d. If review is required:
      - Launch Reviewer with [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST]+[review_scope]
      - Pass `review_scope` explicitly: "spec" | "arch" | "tests" | "code" | "tester"
      - For Phase 2, ensure [ARTIFACT] includes `task_dir/.spec/technical-design.md` + `task_dir/.context/task-breakdown.json`
      - Record Reviewer agentId in `task_dir/.context/sessions.json` → key "reviewer-{scope}"
      - Save review results in `task_dir/.context/reviewer-context-{scope}.md`
      - Handle the outcome (pass / iterate / escalate)
      - If necessary: start codex-review as a second opinion (see section 6)
   e. If the agent returned `clarification_needed` (Phase 1 — Analyst, Phase 2 — Architect):
      - Read `task_dir/.context/{role}-context.md` — it contains the question list
      - Ask ALL questions to the user in one block
      - Wait for answers
      - Record answers in the User Answers section of `task_dir/.context/{role}-context.md`
      - Rerun the agent with the original task + task_dir
        (the agent will read the context and answers at startup)
      - Optimization: if the agentId from the previous run is still valid —
        use resume instead of a fresh launch
      - If clarification_needed occurs again → escalate to the user (do not repeat)
   f. Pass the artifact to the next phase
   ↓
6. Generate the final report `task_dir/.spec/final-report.md` (see format below)
   - Log the DONE event in `task_dir/.context/orchestrator-context.md`
   ↓
7. Deliver the result to the user
```

### Step 4d details (review handling)

| Review result | Action |
|-----------------|----------|
| OK (no BLOCK) | Proceed to the next phase. WARN/INFO — at the author’s discretion (can fix later). |
| BLOCK, iteration ≤ 3 | Return the artifact to the author with comments. Repeat the cycle. The same iteration rules apply to Task Breakdown JSON. |
| BLOCK, iteration > 3 | Escalate to the user. Stop. For Task Breakdown JSON: more than 3 iterations is forbidden; user decision is required. |
| Phase 2: OK | Stop. Ask the user for confirmation. After confirmation — Phase 3. |

---

## Context log (`task_dir/.context/orchestrator-context.md`)

A minimalist journal of key events. Maintained continuously — allows resuming the task from the same point when the Orchestrator stops.

**Entry format:**
```
[YYYY-MM-DD HH:MM] EVENT: description
```

**Key events to record:**

| Event | When to log |
|---------|-------------|
| `START` | Task start, task text in one line |
| `PHASE` | Launch of each phase (Explorer, Analyst, Architect, Developer, Tester, Reviewer) |
| `DONE_PHASE` | Phase completion, outcome (OK / BLOCK / clarification_needed) |
| `CLARIFICATION` | Request for clarification from the user |
| `USER_INPUT` | User response received |
| `REVIEW_BLOCK` | Reviewer placed BLOCK, iteration number |
| `ESCALATE` | Escalate to the user |
| `RESUME` | Resuming the task after a stop |
| `DONE` | Task completed |

**Example:**
```
[2026-03-02 10:15] START: Add attribute "ДатаОтгрузки" to document Реализация
[2026-03-02 10:16] PHASE: Explorer — codebase exploration
[2026-03-02 10:18] DONE_PHASE: Explorer — OK, task classified as MEDIUM
[2026-03-02 10:18] PHASE: Analyst — specification creation
[2026-03-02 10:22] DONE_PHASE: Analyst — clarification_needed
[2026-03-02 10:23] CLARIFICATION: Asked the user: is the attribute type Date or DateTime?
[2026-03-02 10:25] USER_INPUT: Date
[2026-03-02 10:28] DONE_PHASE: Analyst — OK, task_dir/.spec/spec.md generated
[2026-03-02 10:29] PHASE: Reviewer (scope: spec)
[2026-03-02 10:31] DONE_PHASE: Reviewer spec — OK
...
[2026-03-02 11:45] DONE
```

**Rules:**
- Do not duplicate artifact contents — only record the event occurrence.
- One line per event maximum.
- When resuming the task — append to the existing log, do not overwrite.

---

## Final report (`task_dir/.spec/final-report.md`)

Compiled by the Orchestrator after all task phases are complete.

**Format:**

```markdown
# Report: TASK-XXX-name

## New metadata objects
- Справочник.НовыйСправочник
- Документ.НовыйДокумент.Форма.ФормаДокумента

## Modified objects
<!-- Objects listed under "New" should not be repeated here -->
- Документ.Реализация — added attribute ДатаОтгрузки
- РегистрНакопления.ТоварыНаСкладах — added a new filter
- ОбщийМодуль.РаботаСДокументами — updated the procedure ПровестиДокумент

## What was done
A brief semantic description in free form — what was implemented, which business problem it solves, which key decisions were made.
```

**Rules:**
- If an object appears in “New” — it **must not be duplicated** under “Modified.”
- Metadata objects must be specified in the 1С notation: `Type.Name` (e.g., `Справочник.Контрагенты`).
- Sub-objects (forms, attributes, tabular sections) should be indicated using dots: `Документ.Реализация.Форма.ФормаДокумента`.
- The “What was done” section is free text, 3–7 sentences.

---

## Decision tree for classification

```
Задача от пользователя
         │
         ├─► Требуются новые объекты метаданных?
         │        Да → СЛОЖНАЯ → full-cycle
         │
         ├─► Изменяется поток данных / архитектура?
         │        Да → СЛОЖНАЯ → full-cycle
         │
         ├─► Исправление бага в одном файле?
         │        Да → ПРОСТАЯ → quick-fix
         │
         ├─► Всё остальное
         │        → СРЕДНЯЯ → full-cycle
         │
         └─► (По умолчанию при неопределённости)
                   → СРЕДНЯЯ → full-cycle
```

### Tree rules

| Question | “Yes” answer | Complexity |
|--------|------------|------------|
| Are new metadata objects required (directories, documents, registers, forms)? | Yes | Complex |
| Does the data flow or architecture of the solution change? | Yes | Complex |
| Is this a bug fix in a single file? | Yes | Simple |
| Everything else | — | Medium |

**In case of uncertainty:** treat it as medium and use full-cycle.

---

## Orchestrator diagram

```
  ┌──────────┐
  │  Задача  │
  └─────┬────┘
        ▼
  ┌──────────────────────┐
  │ Explorer (Economy)   │
  │ классификация задачи │
  └──────────┬───────────┘
             │
     ┌───────┴────────┐
     ▼                ▼
 [Простая]     [Средняя/Сложная]
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
            │  Результат │
            └────────────┘
```

---

## Related resources

| Resource | Relation |
|--------|-------|
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
