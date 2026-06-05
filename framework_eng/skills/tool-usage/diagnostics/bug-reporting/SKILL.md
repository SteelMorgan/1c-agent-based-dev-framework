---
name: bug-reporting
description: "The standard for the bug-report.json form and the criteria for “this is a debugger bug”. Use when a subagent has exhausted its self-recovery limit and must hand the problem to the orchestrator for investigation. Without this skill, you must not file a bug-report."
alwaysApply: false
---

# Bug Reporting — form standard and criteria

## 1. When to file a bug-report

A problem becomes a **debugger bug** only if ALL conditions are met:

1. **Observed at runtime** — a test failed, a scenario failed, an exception occurred, or the result was unexpected. Static remarks (style, coverage, formatting) are a BLOCK from Reviewer, not a bug.
2. **There is a mismatch between “expected ≠ actual”**, and the expectation is taken from a **concrete source** (spec, design, test assertion, Acceptance Scenario), not from a guess.
3. **The agent has exhausted its own self-recovery limit** (see §2 — each agent has its own).
4. **This is NOT a requirements ambiguity** — otherwise the path is `clarification_needed` → user.
5. **This is NOT a missing API in the design** — otherwise `clarification_needed` → Architect via the orchestrator.
6. **This is NOT an infrastructure problem** (DB is not running, the server is not responding, a file is not found) — this is `environment_error`, and it goes to the orchestrator as an infra problem, not to the debugger.

If at least one condition is not met, no bug-report is filed.

| Situation | Where it goes |
|---|---|
| A typo in your own code, visible immediately | self-fix |
| A test does not compile after your own change | self-fix |
| Style/coverage/formatting | Reviewer BLOCK |
| Spec is contradictory/incomplete | clarification → user |
| The design does not contain the needed API | clarification → Architect |
| A test/scenario failed, and the cause is unclear after the attempt limit | **bug-report → debugger** |
| Vanessa Red-gate is green, mock is not obvious | **bug-report → debugger** |
| Behavior in code diverges from the assertion, cause unclear | **bug-report → debugger** |
| 1C is not running, fixtures are not started | environment_error → orchestrator |

---

## 2. Self-recovery limits (when “exhausted”)

| Agent | What it fixes itself | Limit | When it files a bug-report |
|---|---|---|---|
| `developer-code` | Its own syntax/logic in its own code | 2 attempts | The test fails not because of my code OR 2 attempts are exhausted without understanding the cause |
| `tester` | Technical errors in test code (the test logic does not change) | 3 attempts | The failure is not fixed by changing test code OR 3 attempts are exhausted |
| `scenario-coder` | Its own step implementations | 2 attempts | Red-gate is green without explanation; the step fails for an unclear reason; 2 attempts are exhausted |

The remaining agents (`developer-tests`, `scenario-author`, `analyst`, `architect`, `explorer`, `reviewer`) do **not** create bug-reports — they either do not run code or handle remarks through the Reviewer cycle.

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
    "action": "проведение документа РасходТовара",
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
      "relevant_db_state": "не проверялось"
    }
  },

  "self_fix_attempts": [                                    // * at least one entry
    {"what_tried": "проверил формулу в РассчитатьСкидку()",  "result": "формула совпадает со спекой"},
    {"what_tried": "перепрогнал тест после rebuild",         "result": "то же значение 15"}
  ],
  "stopping_reason": "after_2_attempts",                    // * after_N_attempts | suspected_other_layer | out_of_scope

  "hypotheses": [                                           // optional, but if present — with reasoning
    {
      "layer": "data",                                      // code | test | scenario | step | data | spec | unknown
      "agent": "developer-tests",                           // suspected owner
      "reasoning": "the test expects the customer to have a VIP category, but it may not be present in the fixture"
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
    "protected_paths": [],                                  // what the debugger must NOT touch
    "blocked_paths": []                                     // occupied by other tasks
  }
}
```

---

## 4. Filling rules

### 4.1 General

- **`expectation.source` + `quote` are mandatory.** Without an explicit quote from the source of truth, the bug-report is not accepted. This removes “in my opinion it should be different”.
- **`symptom.error_message` must be verbatim.** A direct quote of the assertion/exception/log, not a paraphrase.
- **`self_fix_attempts` must contain at least one entry.** Even “read the code, I do not see the cause” is an artifact. This blocks “threw it over the wall”.
- **`hypotheses` are optional, but if provided, they must include `reasoning`.** A hypothesis without justification is noise.
- **`context.files_touched_this_phase` is mandatory.** The debugger must know what changed recently.
- **Concrete values, not “a typical document”.** Quote the actual data from the test/fixture/run.

### 4.2 `scenario_context` — what and how to fill it in

The debugger will not be able to reproduce or model the issue without understanding which action was performed, under which user, and with which data.

**`action`** — a concrete action in the system: “posting document X”, “running processor Y”, “calling function Z”, “generating report”.

**`user`** — mandatory. In 1C, many branches depend on permissions.

**`input_data.kind`** determines which subfields to fill:

- `document` → `document.type`, `is_new` (important — a new document has no reference), `header` (header attributes), `tabular_sections` (row count + first/problematic row).
- `processor` → `processor.name`, `form_fields` (form values).
- `function_call` → `module`, `procedure`, `arguments` (actual values).
- `report` → `name`, `parameters`.

**What not to dump:**

- Entire objects (`FormObject`, `ReferenceObject.<AllFields>`) — only relevant attributes.
- `ValueTable` in full — only `rows_count` + the first/problematic row.
- Binary data, passwords, tokens, user personal data except for service identification.
- Metadata (`Metadata.Documents.X.<everything>`) — only the type name.

**`is_new: true/false` is critical** — a new document has no reference and many attributes are missing.

**`relevant_db_state`** — fill this only if the reporter has already checked the database state (`platform-data-core` § Query Execution); otherwise `"not checked"`. The debugger will check it on its own.

### 4.3 When `scenario_context.incomplete: true`

If the reporter cannot fill in the context completely (for example, Developer-Code cannot see how the test prepares the document):

- Set `incomplete: true` and `incomplete_reason` (what exactly could not be established).
- Fill in the minimum that is visible (for example, only `function_call`).
- The debugger reconstructs the context first.

**An incomplete report marked `incomplete` is better than invented data.**

---

## 5. Filling protocols by agent

### 5.1 `developer-code` (Phase 3d)

**Trigger:** a unit test does not pass, the reason is not in my code OR 2 self-fix attempts are exhausted.

Filling:
- `symptom.what_ran` — test name + full path.
- `symptom.error_message` — assertion verbatim from stdout/event-log.
- `expectation.source` — the spec section or the assertion line from the test.
- `scenario_context.input_data.kind = "function_call"` if the unit test failed on a specific function; add `document` if the test runs on a document.
- `hypotheses` — if there is a suspicion of `test/data/scenario/step`, specify it with reasoning.
- `context.files_touched_this_phase` — all BSL/XML files changed in Phase 3d.

### 5.2 `tester` (Phase 4)

**Trigger:** after 3 attempts to fix the test, it still did not help OR the failure is not fixed by changing the test.

Filling:
- Full `scenario_context` — Tester sees the end-to-end scenario and must fill in the maximum.
- `symptom.what_ran` — test name / `.feature` / scenario name.
- `expectation.source` — spec OR Acceptance Scenario from the spec OR assertion.
- `hypotheses` — the Tester's current classification (`test_error` / `implementation_error` / `spec_mismatch`) is mapped into `hypotheses[].layer`.
- `self_fix_attempts` — all 3 attempts, with what was changed and the result.

### 5.3 `scenario-coder` (Phase 3c)

**Trigger:**
- Red-gate is green without production code (the mock is not obvious), or
- The step fails for an unclear reason after 2 attempts.

Filling:
- `symptom.what_ran` — `.feature` name + specific scenario + step.
- `expectation.source` — Acceptance Scenario from the spec + the expected Red-gate behavior (should be red).
- `scenario_context.action` — what the scenario does (the `Given` blocks in `.feature` provide the data).
- `scenario_context.input_data` — from the scenario `Given` steps.
- `hypotheses` — for example `layer: step` if there is suspicion of a hidden mock in the step implementation.

---

## 6. Bug-report lifecycle

| Status | Who changes it | When |
|---|---|---|
| `open` | reporter | On creation |
| `in_investigation` | orchestrator | When the debugger starts |
| `fixed_locally` | debugger | After a local fix + verification |
| `returned_to_author` | debugger | If the fix is broad, return it to the responsible agent |
| `escalated_to_user` | orchestrator | After hypotheses are exhausted or after 2 bug→fix→bug cycles |

**Duplicate control:** if the symptom is the same (`symptom.fail_location` + `symptom.error_message` match), the existing bug-report is updated (a new `self_fix_attempts` entry, new `hypotheses`), and a new one is NOT created.

---

## 7. Anti-patterns

| Anti-pattern | Why it is bad |
|---|---|
| `error_message` is paraphrased in your own words | Exact signature and stack trace are lost |
| `expectation.quote` is missing or “well, by logic...” | No source of truth → the debugger does not know what to compare against |
| Dumping the entire object into `scenario_context` | Clutters the report, may contain sensitive data |
| A hypothesis without `reasoning` | Noise, the debugger cannot prioritize |
| `self_fix_attempts: []` is empty | There was not even an attempt to understand the issue → therefore it is not a debugger bug |
| Creating a new bug-report for the same symptom | Duplicates interfere with tracking; update the existing one |
| `scenario_context` with invented data instead of `incomplete: true` | The debugger will follow a false lead |

---
depends_on:
  - framework/rules/source-of-truth.md
---
