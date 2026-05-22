---
name: bug-reporting
description: "Standard form for bug-report.json and the criteria for what counts as a debugger bug. Use this when a subagent has exhausted its self-recovery limit and must hand the issue to the orchestrator for investigation. Without this skill, bug-report must not be created."
---

# Bug Reporting — form standard and criteria

## 1. When to create a bug-report

An issue becomes a **bug for the debugger** only if ALL of the following are true:

1. **It is observed at runtime** — a test failed, a scenario failed, an exception occurred, or the result was unexpected. Static remarks (style, coverage, formatting) are a Reviewer BLOCK, not a bug.
2. **There is a mismatch between expectation and reality**, and the expectation comes from a **specific source** (spec, design, test assert, Acceptance Scenario), not from a guess.
3. **The agent has exhausted its own self-recovery limit** (see §2 — each agent has its own limit).
4. **It is NOT an ambiguity in the requirements** — otherwise the path is `clarification_needed` → user.
5. **It is NOT a missing API in the design** — otherwise the path is `clarification_needed` → Architect through the orchestrator.
6. **It is NOT an infrastructure problem** (database not started, server not responding, file not found) — that is `environment_error`, sent to the orchestrator as an infra problem, not to the debugger.

If even one condition is not met, a bug-report is NOT created.

| Situation | Where it goes |
|---|---|
| A typo in your own code formula, visible immediately | self-fix |
| A test does not compile after your own change | self-fix |
| Style/coverage/formatting | Reviewer BLOCK |
| The spec is contradictory/incomplete | clarification → user |
| The required API is missing in the design | clarification → Architect |
| A test/scenario failed, the cause is not obvious within the attempt limit | **bug-report → debugger** |
| Vanessa Red-gate is green, the mock is not obvious | **bug-report → debugger** |
| Behavior in code diverges from the assert, the cause is unclear | **bug-report → debugger** |
| 1C is not running, fixtures are not started | environment_error → orchestrator |

---

## 2. Self-recovery limits (when "exhausted")

| Agent | What it fixes itself | Limit | When it creates a bug-report |
|---|---|---|---|
| `developer-code` | Its own syntax/logic in its own code | 2 attempts | The test fails not because of my code OR 2 attempts are exhausted without understanding the cause |
| `tester` | Technical errors in test code (the test logic does not change) | 3 attempts | The failure is not fixed by changing the test code OR 3 attempts are exhausted |
| `scenario-coder` | Its own step implementations | 2 attempts | Red-gate is green without explanation; the step fails for an unclear reason; 2 attempts are exhausted |

Other agents (`developer-tests`, `scenario-author`, `analyst`, `architect`, `explorer`, `reviewer`) do **not** create bug-reports — they either do not run code or handle remarks through the Reviewer loop.

---

## 3. `bug-report.json` structure

**Location:** `task_dir/.context/bugs/<bug-id>.json`

**ID:** `bug-<task-id>-<seq>`, for example `bug-T-042-001`. Numbering is within the task, sequential.

**Required fields are marked with `*`.**

```json
{
  "id": "bug-T-042-001",                                    // *
  "status": "open",                                         // * open | in_investigation | fixed_locally | returned_to_author | escalated_to_user

  "reporter": {                                             // *
    "agent": "developer-code",                              // *
    "phase": "3d",                                          // *
    "timestamp": "2026-04-27T14:32:00Z"                     // *
  },

  "symptom": {                                              // *
    "what_ran": "unit-test 'РасчётСкидки_GivenVIP_Returns20'", // *
    "command": "1c-ai-agent-cli test ...",                  // *
    "fail_location": "tests/unit-РасчётСкидки.bsl:42",      // *
    "error_message": "Expected 20, got 15",                 // * verbatim, not a paraphrase
    "log_path": "task_dir/runs/2026-04-27T14-30/test.log",  // *
    "deterministic": true                                   // * true | false | unknown
  },

  "expectation": {                                          // *
    "source": "spec.md §3.2",                               // * file + section
    "quote": "Для VIP-клиентов скидка MUST составлять 20%." // * verbatim from the source
  },

  "scenario_context": {                                     // * (see §4 — may be incomplete)
    "incomplete": false,                                    // if true — specify reason
    "incomplete_reason": null,
    "action": "posting the document РасходТовара",
    "user": {
      "name": "Иванов И.И.",
      "roles": ["Менеджер"],
      "is_admin": false
    },
    "input_data": {
      "kind": "document",                                   // document | processor | function_call | report
      "document": {
        "type": "Документ.РасходТовара",
        "is_new": true,
        "header": {
          "Дата": "2026-04-27",
          "Организация": "ООО Ромашка",
          "Контрагент": "<пусто>"
        },
        "tabular_sections": {
          "Товары": {
            "rows_count": 2,
            "rows_sample": [
              {"Номенклатура": "Товар А", "Количество": 5, "Цена": 100}
            ]
          }
        }
      }
    },
    "system_state": {
      "current_date": "2026-04-27",
      "active_session_params": {"ТекущаяОрганизация": "ООО Ромашка"},
      "relevant_db_state": "not checked"
    }
  },

  "self_fix_attempts": [                                    // * at least one entry
    {"what_tried": "checked the formula in РассчитатьСкидку()",  "result": "the formula matches the spec"},
    {"what_tried": "reran the test after rebuild",         "result": "the same value 15"}
  ],
  "stopping_reason": "after_2_attempts",                    // * after_N_attempts | suspected_other_layer | out_of_scope

  "hypotheses": [                                           // optional, but if present — with reasoning
    {
      "layer": "data",                                      // code | test | scenario | step | data | spec | unknown
      "agent": "developer-tests",                           // suspected owner
      "reasoning": "the test expects the client to have the VIP category, but the fixture may not contain it"
    }
  ],

  "context": {                                              // *
    "files_touched_this_phase": [                           // * what changed in this phase
      "src/CommonModules/Скидки/Module.bsl"
    ],
    "related_artifacts": [                                  // *
      "spec.md",
      "tests/unit-РасчётСкидки.bsl"
    ],
    "protected_paths": [],                                  // paths the debugger must NOT touch
    "blocked_paths": []                                     // paths occupied by other tasks
  }
}
```

---

## 4. Filling rules

### 4.1 General

- **`expectation.source` + `quote` are mandatory.** Without an explicit quote from the source of truth, the bug-report is not accepted. This removes "in my opinion it should be different."
- **`symptom.error_message` must be verbatim.** A direct quote of the assert/exception/log, not a paraphrase.
- **`self_fix_attempts` must contain at least one entry.** Even "read the code, I do not see the cause" is an artifact. This blocks "threw it over the wall."
- **`hypotheses` are optional, but if provided, they must include `reasoning`.** A hypothesis without justification is noise.
- **`context.files_touched_this_phase` is mandatory.** The debugger must know what changed recently.
- **Concrete values only, not "a typical document".** Quote actual data from the test/fixture/run.

### 4.2 `scenario_context` - what to fill and how

The debugger cannot reproduce and model the issue without understanding which action was performed, under which user, and with which data.

**`action`** — a specific action in the system: "posting document X", "running processing Y", "calling function Z", "generating report".

**`user`** — mandatory. In 1C there are many branches based on permissions.

**`input_data.kind`** determines which subfields to fill:

- `document` → `document.type`, `is_new` (important — a new document has no reference), `header` (header attributes), `tabular_sections` (row count + first/problematic row).
- `processor` → `processor.name`, `form_fields` (values on the form).
- `function_call` → `module`, `procedure`, `arguments` (actual values).
- `report` → `name`, `parameters`.

**What NOT to dump:**

- Whole objects (`FormObject`, `СправочникОбъект.<AllFields>`) — only relevant attributes.
- Whole `ValueTable` objects — only `rows_count` + the first/problematic row.
- Binary data, passwords, tokens, user personal data except service identification.
- Metadata (`Метаданные.Документы.X.<everything>`) — only the type name.

**`is_new: true/false` is critical** — a new document has no reference and lacks many attributes.

**`relevant_db_state`** is filled only if the reporter has already checked the database state (`platform-data-core` § Query Execution); otherwise `"not checked"`. The debugger will check it itself.

### 4.3 When `scenario_context.incomplete: true`

If the reporter cannot fully fill the context (for example, Developer-Code cannot see how the test prepares the document):

- Set `incomplete: true` and `incomplete_reason` (what exactly could not be established).
- Fill in the minimum that is visible (for example, only `function_call`).
- The debugger first reconstructs the context on its own.

**A partial report marked `incomplete` is better than invented data.**

---

## 5. Fill protocol by agent

### 5.1 `developer-code` (Phase 3d)

**Trigger:** the unit test fails, and the cause is not in my code OR 2 self-fix attempts are exhausted.

Fill in:
- `symptom.what_ran` — test name + full path.
- `symptom.error_message` — assertion verbatim from stdout/event-log.
- `expectation.source` — spec section or assert line from the test.
- `scenario_context.input_data.kind = "function_call"` if a unit test on a specific function failed; add `document` if the test runs on a document.
- `hypotheses` — if there is suspicion about test/data/scenario/step, specify it with reasoning.
- `context.files_touched_this_phase` — all BSL/XML files changed in Phase 3d.

### 5.2 `tester` (Phase 4)

**Trigger:** after 3 attempts, fixing the test did not help OR the failure cannot be fixed by changing the test.

Fill in:
- Full `scenario_context` — Tester sees the end-to-end scenario and must fill in as much as possible.
- `symptom.what_ran` — test name / `.feature` / scenario name.
- `expectation.source` — spec OR Acceptance Scenario from the spec OR assert.
- `hypotheses` — the current Tester classification (`test_error` / `implementation_error` / `spec_mismatch`) is transferred to `hypotheses[].layer`.
- `self_fix_attempts` — all 3 attempts with what was changed and the result.

### 5.3 `scenario-coder` (Phase 3c)

**Trigger:**
- The Red-gate is green without production code (the mock is not obvious), or
- The step fails for an unclear reason after 2 attempts.

Fill in:
- `symptom.what_ran` — `.feature` name + specific scenario + step.
- `expectation.source` — Acceptance Scenario from the spec + expected Red-gate behavior (it should be red).
- `scenario_context.action` — what the scenario does (Given blocks in the `.feature` provide the data).
- `scenario_context.input_data` — from the scenario Given steps.
- `hypotheses` — for example `layer: step` if there is suspicion of a hidden mock in the step implementation.

---

## 6. Bug-report lifecycle

| Status | Who changes it | When |
|---|---|---|
| `open` | reporter | When created |
| `in_investigation` | orchestrator | When the debugger starts |
| `fixed_locally` | debugger | After a local fix + verification |
| `returned_to_author` | debugger | If the fix is large, return it to the specialized agent |
| `escalated_to_user` | orchestrator | After hypotheses are exhausted or after 2 bug→fix→bug cycles |

**Duplicate control:** if the same symptom matches (`symptom.fail_location` + `symptom.error_message`), the existing bug-report is updated (a new `self_fix_attempts` entry, new `hypotheses`), and a new one is NOT created.

---

## 7. Anti-patterns

| Anti-pattern | Why it is bad |
|---|---|
| `error_message` is paraphrased in your own words | Exact signature and stack trace are lost |
| `expectation.quote` is missing or "well, by logic..." | No source of truth → the debugger does not know what to compare against |
| Dumping the entire object into `scenario_context` | Pollutes the report, may contain sensitive data |
| Hypothesis without `reasoning` | Noise, the debugger cannot prioritize |
| Empty `self_fix_attempts: []` | There was not even an attempt to understand it → therefore not a bug for the debugger |
| Creating a new bug-report for the same symptom | Duplicates interfere with tracking; update the existing one |
| `scenario_context` with invented data instead of `incomplete: true` | The debugger will go down a false trail |

---
depends_on:
  - framework/workflows/source-of-truth-policy.md
---
