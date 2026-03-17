---
name: orchestrator
description: Orchestrator routes tasks and manages workflow phases.
---


# Orchestrator: Meta-workflow

> **Orchestrator** is a meta-workflow that routes tasks, selects workflows, and manages agent interactions.

---

## Purpose

The Orchestrator does not execute tasks itself. It:
1. Classifies incoming tasks
2. **Initializes the task directory** (`task_dir`)
3. Selects a workflow (quick-fix or full-cycle)
4. Assigns a model tier to each agent
5. Manages review cycles
6. Passes artifacts between agents, explicitly specifying `task_dir`
7. Identifies touchpoints with the user
8. **Keeps a registry of agent sessions** (`task_dir/.context/sessions.json`) to allow resuming
9. **Launches codex-review** for complex artifacts as a second independent opinion
10. **Maintains a context log** (`task_dir/.context/orchestrator-context.md`) — a minimalist journal of key events for task resumption
11. **Prepares the final report** (`task_dir/.spec/final-report.md`) after the task completes

---

## FREE mode: orchestrator is disabled

**IMPORTANT:** In FREE mode (without full-cycle) the Orchestrator is **not active**.

The agent works directly, using:
- Skills
- Rules
- Tool-registry

The user provides the task, and the agent resolves it in free mode. Cross-review is optional. Phases are not enforced.

---

## Orchestrator Responsibilities

### 1. Task Classification

Determines task complexity and selects a workflow via the [classification decision tree](#дерево-решений-классификации).

### 2. Model Routing

IMPORTANT!!! When launching subagents via Task you **MUST** specify the `model` parameter.
Each agent has a preset model (field `model` in frontmatter):
- Economy — Explorer
- Mid/High — Developer, Tester
- High/Premium — Architect, Analyst
- Premium — Reviewer (spec, Task Breakdown JSON decomposition, architecture)
- High — Reviewer (code, tests, BDD)
**NEVER** run a subagent without an explicit `model`.

### 3. Review Cycle Management

- Tracks iterations (max. 3)
- On BLOCK → returns to the author with comments
- On 3+ BLOCK without resolution → escalate to the user
- Ensures reviewer tier ≥ author tier

**Returning between agents via the Orchestrator:**

Subagents do not talk directly — every return goes through the Orchestrator.

| Situation | Who signals | Orchestrator action |
|----------|-------------|----------------------|
| Reviewer marked BLOCK on an artifact | Reviewer | Return the artifact to the phase author with comments |
| Tester found a bug in the implementation | Tester (label `implementation_error`) | Return to Developer-Code with a description: which test, expected outcome, actual outcome |
| Tester found an error in their own test | Tester (label `test_error`) | Tester fixes it independently, the Orchestrator does not intervene |
| Developer-Code: tests failed (label `test_failure`) | Developer-Code | Run Reviewer to determine the cause: bug in tests → return to Developer-Tests; bug in code → return to Developer-Code |
| Developer-Code: `test_failure` + `suspected_test_error` | Developer-Code | Trigger Reviewer arbitration: match spec + technical-design + tests + code, record in `reviewer-context-code.md` which artifact is faulty (`tests` or `code` or `bdd`). Then the Orchestrator routes the task according to the Reviewer summary: to Scenario-Author, Developer-Tests, or Developer-Code; the decision is logged in `orchestrator-context.md`. |
| 3+ iterations without clearing BLOCK | Reviewer / any agent | Escalate to the user, stop |

### 4. Artifact Management

- Passes the output of one phase to the next, **explicitly providing `task_dir`** to each subagent
- Stores/restores context between sessions (if supported by the adapter)
- Assembles the [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST] package for the reviewer, where for Phase 2 [ARTIFACT] includes both Technical Design and the Task Breakdown JSON decomposition

**Storage separation:**

| Data type | Stored where |
|-----------|--------------|
| Specification, technical design, test-report, final-report | `task_dir/.spec/` |
| Task Breakdown JSON | `task_dir/.context/` |
| Review results | `task_dir/.context/` |
| Agent sessions registry | `task_dir/.context/sessions.json` |
| Agent context files | `task_dir/.context/` |
| BSL code, tests, metadata XML | Project codebase (separate directory) |

**`task_dir` structure:**

```
tasks/
└── TASK-001-название/
    ├── .context/                     ← Agent contexts and brief phase outcomes
    │   ├── sessions.json             ← Orchestrator (registry of every agentId)
    │   ├── orchestrator-context.md   ← Orchestrator (context log, continually maintained)
    │   ├── explorer-context.md       ← Explorer (Phase 0)
    │   ├── analyst-context.md        ← Analyst (Phase 1)
    │   ├── architect-context.md      ← Architect (Phase 2)
    │   ├── scenario-author-context.md ← Scenario-Author (Phase 3a)
    │   ├── developer-tests-context.md← Developer-Tests (Phase 3b)
    │   ├── developer-code-context.md ← Developer-Code (Phase 3c)
    │   ├── tester-context.md         ← Tester (Phase 4)
    │   ├── reviewer-context-spec.md  ← Reviewer (Phase 1)
    │   ├── reviewer-context-arch.md  ← Reviewer (Phase 2)
    │   ├── reviewer-context-bdd.md   ← Reviewer (Phase 3a)
    │   ├── reviewer-context-tests.md ← Reviewer (Phase 3b)
    │   ├── reviewer-context-code.md  ← Reviewer (Phase 3c)
    │   ├── reviewer-context-tester.md← Reviewer (Phase 4)
    │   └── task-breakdown.json       ← Architect (Phase 2)
    └── .spec/                        ← Primary specification artifacts and final reports
        ├── spec.md                   ← Analyst (Phase 1)
        ├── technical-design.md       ← Architect (Phase 2)
        ├── test-report.md            ← Tester (Phase 4)
        └── final-report.md           ← Orchestrator (final report)
```

### 5. Agent Sessions Registry (`task_dir/.context/sessions.json`)

The Orchestrator keeps `task_dir/.context/sessions.json` — a registry of the agentId for every launched agent.
It is used for `resume` when rerunning the same agent (BLOCK → fix → re-review, clarification round, etc.).

**Structure:**

```json
{
  "explorer":         "agent-xxx",
  "analyst":          "agent-yyy",
  "architect":        "agent-zzz",
  "scenario-author":  "agent-xxx",
  "developer-tests":  "agent-aaa",
  "developer-code":   "agent-bbb",
  "tester":           "agent-ccc",
  "reviewer-spec":    "agent-ddd",
  "reviewer-arch":    "agent-eee",
  "reviewer-bdd":     "agent-xxx",
  "reviewer-tests":   "agent-fff",
  "reviewer-code":    "agent-ggg",
  "reviewer-tester":  "agent-hhh"
}
```

**Protocol:**
- After each agent launch — write the agentId under the corresponding key
- On rerun — read `task_dir/.context/sessions.json`, attempt `resume agentId`; if the agentId is stale — start a new session and update the entry
- Reviewer runs separately for each scope (`reviewer-spec`, `reviewer-arch`, `reviewer-bdd`, `reviewer-tests`, `reviewer-code`, `reviewer-tester`) — each has its own key

### 6. Codex-review as a second independent opinion

The Orchestrator runs `codex-review` (CLI) **on top of the main Reviewer** for complex artifacts. The Reviewer does not launch it themselves — the Orchestrator is responsible.

**When to run:**

| Condition | Action |
|-----------|--------|
| Architectural decision with trade-offs (Phase 2) | Launch codex-review after the Reviewer |
| Complex BSL code (> 5 files, > 300 lines) | Launch codex-review after the Reviewer |
| Reviewer placed BLOCK and the author disputes it | Launch codex-review as a tiebreaker |
| On user request `/review-gpt`, `/review-all` | Launch immediately |

**How to run:** see the `codex-review` skill.

### 5. User touchpoints

| Touchpoint | Action |
|------------|--------|
| Phase 1: Analyst returned `clarification_needed` | Ask the user all questions in a single batch, gather answers, rerun the Analyst with clarifications (max. 1 round) |
| Phase 2: Architect returned `clarification_needed` | Ask all questions in a single batch, gather answers, rerun the Architect with clarifications (max. 1 round) |
| Phase 2 (architecture) | Approval gate — wait for user confirmation |
| Escalation (3 BLOCK) | Request a decision from the user |
| New metadata object | Agent → user protocol: instruction → wait for creation → verification |

**Clarification round protocol:**

```
Agent → clarification_needed
  │  (questions recorded in task_dir/.context/{role}-context.md → Pending Questions)
  ▼
Orchestrator reads task_dir/.context/{role}-context.md → asks questions to the user
  │
  ▼
User replies
  │
  ▼
Orchestrator records answers in task_dir/.context/{role}-context.md → User Answers
  │
  ├── agentId still valid? → resume (optimization, same session)
  └── agentId outdated?  → start the agent anew with task_dir
                           (agent reads the context itself at step 1)
  │
  ▼
Agent continues with the saved context, without repeating exploration
  │
  ▼
Specification / technical design ready
(if clarification_needed again → escalate to the user,
 no third round — agent MUST write the artifact with assumptions)
```

---

## Orchestrator protocol

### Sequence of actions

```
1. Receive the task from the user
   ↓
2. Initialize task_dir:
   - If a task number/path is provided → use the existing directory
   - Otherwise → create tasks/TASK-XXX-название/
   - Create/read task_dir/.context/sessions.json
   - Create/append task_dir/.context/orchestrator-context.md: log the START event with timestamp and the task text
   ↓
3. Launch Explorer to inspect the codebase
   - Explorer returns: list of impacted modules, call graphs (incoming + outgoing),
     dependency depth, number of call points
   - Save the Explorer artifact to `task_dir/.context/explorer-context.md`
   - Record Explorer's agentId in `task_dir/.context/sessions.json` → key "explorer"
   - Classify the task based on these data (simple / medium / complex)
   ↓
4. Choose a workflow based on classification:
   - Simple → quick-fix.md
   - Medium/Complex → full-cycle.md
   ↓
5. For each phase of the selected workflow:
   a. Launch the agent (model specified in agent frontmatter)
      - Optimization: read `task_dir/.context/sessions.json`; if the agentId for this role exists → try resume
      - After launch: record the agentId in `task_dir/.context/sessions.json` → key for the agent role
   b. Pass inputs + explicitly task_dir
      - **For Phase 1 (Analyst):** task + `task_dir/.context/explorer-context.md` (module list, call graphs)
      - **For Phase 2 (Architect):** approved spec + `task_dir/.context/explorer-context.md` (call graphs, dependencies)
      - **For Phase 3a (Scenario-Author):** spec + technical-design + task-breakdown.json (runs in parallel with Phase 3b)
      - **For Phase 3b (Developer-Tests):** spec + technical-design + task-breakdown.json (runs in parallel with Phase 3a)
      - **For Phase 3c (Developer-Code):** spec + technical-design + task-breakdown.json + test modules from Phase 3b + `.feature` files from Phase 3a
   c. Collect the output artifact → save it to task_dir
      - Record in `task_dir/.context/orchestrator-context.md`: phase completion event (agent, result — OK / BLOCK / clarification_needed)
   d. If review is required:
      - Launch Reviewer with [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST]+[review_scope]
      - Pass `review_scope` explicitly: "spec" | "arch" | "bdd" | "tests" | "code" | "tester"
      - For Phase 2, [ARTIFACT] must include `task_dir/.spec/technical-design.md` + `task_dir/.context/task-breakdown.json`
      - Record the Reviewer agentId in `task_dir/.context/sessions.json` → key "reviewer-{scope}"
      - Save the review result in `task_dir/.context/reviewer-context-{scope}.md`
      - Process the outcome (pass / iterate / escalate)
      - If needed: run codex-review as a second opinion (see section 6)
   e. If the agent returned `clarification_needed` (Phase 1 — Analyst, Phase 2 — Architect):
      - Read `task_dir/.context/{role}-context.md` — this file contains the list of questions
      - Ask ALL questions to the user in one batch
      - Wait for answers
      - Record the answers in the `User Answers` section of `task_dir/.context/{role}-context.md`
      - Rerun the agent with the original task + task_dir
        (the agent will read the context and answers at startup)
      - Optimization: if the agentId from the previous run is current —
        use resume instead of a new launch
      - If clarification_needed occurs again → escalate to the user (do not repeat)
   f. Pass the artifact to the next phase
   ↓
6. Generate the final report `task_dir/.spec/final-report.md` (see format below)
   - Log the DONE event in `task_dir/.context/orchestrator-context.md`
   ↓
7. Deliver the result to the user
```

### Detailing step 4d (review handling)

| Review result | Action |
|---------------|--------|
| OK (no BLOCK) | Move to the next phase. WARN/INFO — optional for the author (can fix later). |
| BLOCK, iteration ≤ 3 | Return the artifact to the author with comments. Repeat the cycle. The same iteration rules apply to Task Breakdown JSON. |
| BLOCK, iteration > 3 | Escalate to the user. Stop. For Task Breakdown JSON: more than 3 iterations is prohibited; a user decision is required. |
| Phase 2: OK | Stop. Ask the user for confirmation. After confirmation — Phase 3 (parallel launch of 3a + 3b). |

### Parallel launch of Phase 3a and Phase 3b

After user confirmation of Phase 2, the Orchestrator launches **both simultaneously**:

- **Phase 3a — Scenario-Author (BDD):** writes `.feature` scenario files based on spec + technical-design + task-breakdown.json
- **Phase 3b — Developer-Tests:** writes unit test modules based on spec + technical-design + task-breakdown.json

**Parallel launch rules:**

1. Phase 3a and Phase 3b are **independent** — they do not depend on each other’s artifacts, both receive: spec + technical-design + task-breakdown.json.
2. The Orchestrator **waits for both** to finish (including each review) before starting Phase 3c.
3. **Phase 3c (Developer-Code)** receives the complete set: spec + technical-design + task-breakdown.json + test modules from Phase 3b + `.feature` files from Phase 3a.
4. If one agent (3a or 3b) returned `clarification_needed` or got a BLOCK from a reviewer — handle them **independently**, without blocking the other parallel agent.
5. If one completes earlier — wait for the other; the first result is stored in `task_dir/.context/`.

**Diagram:**

```
Phase 2 OK + User Approval
         │
    ┌────┴────┐
    ▼         ▼
 Phase 3a  Phase 3b
 (Scenario  (Developer
  -Author)   -Tests)
    │         │
    ▼         ▼
 Review    Review
 (bdd)     (tests)
    │         │
    └────┬────┘
         │  (waiting for both)
         ▼
      Phase 3c
   (Developer-Code)
         │
         ▼
      Review (code)
```

---

## Context log (`task_dir/.context/orchestrator-context.md`)

A minimalist journal of key events. It is maintained continuously — enabling task resumption from the same point if the Orchestrator stops.

**Entry format:**
```
[YYYY-MM-DD HH:MM] EVENT: description
```

**Key events to record:**

| Event | When to write |
|-------|---------------|
| `START` | Task start, the task text in one line |
| `PHASE` | Launch of each phase (Explorer, Analyst, Architect, Scenario-Author, Developer, Tester, Reviewer) |
| `DONE_PHASE` | Phase completion, result (OK / BLOCK / clarification_needed) |
| `CLARIFICATION` | Clarification requested from the user |
| `USER_INPUT` | User answer received |
| `REVIEW_BLOCK` | Reviewer placed BLOCK, iteration number |
| `ESCALATE` | Escalation to the user |
| `RESUME` | Resuming the task after a stop |
| `DONE` | Task completed |

**Example:**
```
[2026-03-02 10:15] START: Add attribute "ДатаОтгрузки" to document Реализация
[2026-03-02 10:16] PHASE: Explorer — codebase exploration
[2026-03-02 10:18] DONE_PHASE: Explorer — OK, the task was classified as MEDIUM
[2026-03-02 10:18] PHASE: Analyst — specification creation
[2026-03-02 10:22] DONE_PHASE: Analyst — clarification_needed
[2026-03-02 10:23] CLARIFICATION: Asked the user: is the attribute type Date or DateTime?
[2026-03-02 10:25] USER_INPUT: Date
[2026-03-02 10:28] DONE_PHASE: Analyst — OK, task_dir/.spec/spec.md created
[2026-03-02 10:29] PHASE: Reviewer (scope: spec)
[2026-03-02 10:31] DONE_PHASE: Reviewer spec — OK
...
[2026-03-02 11:45] DONE
```

**Rules:**
- Do not duplicate artifact contents — only note the event.
- One line per event maximum.
- When resuming the task — append to the existing log instead of overwriting.

---

## Final report (`task_dir/.spec/final-report.md`)

The Orchestrator generates this after all phases are complete.

**Format:**

```markdown
# Report: TASK-XXX-название

## New metadata objects
- Справочник.НовыйСправочник
- Документ.НовыйДокумент.Форма.ФормаДокумента

## Modified objects
<!-- Objects listed under "New" should not be repeated here -->
- Документ.Реализация — added the attribute ДатаОтгрузки
- РегистрНакопления.ТоварыНаСкладах — added a new filter
- ОбщийМодуль.РаботаСДокументами — updated the procedure ПровестиДокумент

## What was done
A brief semantic description in free form — what was implemented, which business problem it solves, which key decisions were made.
```

**Rules:**
- If an object appears under “New” — it must **not** be duplicated under “Modified”.
- List metadata objects in the 1C notation `Type.Name` (for example `Справочник.Контрагенты`).
- Sub-objects (forms, attributes, tabular sections) are listed with dots: `Документ.Реализация.Форма.ФормаДокумента`.
- The “What was done” section is free text, 3–7 sentences.
