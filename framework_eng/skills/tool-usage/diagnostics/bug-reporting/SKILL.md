---
name: bug-reporting
description: "Escalate a bug after the agent self-recovery limit is reached"
alwaysApply: false
---

# Bug Reporting — Form standard and criteria

## 1. When to create a bug-report

The problem becomes a **bug for the debugger** only if ALL of the following conditions are met:

1. **It is observed at runtime** — a test failed, a scenario failed, an exception occurred, an unexpected result. Static remarks (style, coverage, formatting) are a REVIEWER BLOCK, not a bug.
2. **There is a mismatch between expectation and fact**, and the expectation is taken from a **specific source** (spec, design, test assert, Acceptance Scenario), not from a guess.
3. **The agent has exhausted its own self-recovery limit** (see §2 — each agent has its own).
4. **This is NOT an ambiguity in the requirements** — otherwise the path is `clarification_needed` → user.
5. **This is NOT a missing API in the design** — otherwise `clarification_needed` → Architect through the orchestrator.
6. **This is NOT an infrastructure problem** (DB is not running, server is not responding, file not found) — this is `environment_error`, sent to the orchestrator as an infra problem, not to the debugger.

If at least one condition is not met, a bug-report is NOT created.

| Situation | Where it goes |
|---|---|
| A typo in your own code formula, visible immediately | self-fix |
| The test no longer compiles after your own change | self-fix |
| Style/coverage/formatting | Reviewer BLOCK |
| The spec is contradictory/incomplete | clarification → user |
| The design lacks the required API | clarification → Architect |
| A test/scenario failed, the cause is not obvious after the attempt limit | **bug-report → debugger** |
| Vanessa Red gate is green, the mock is not obvious | **bug-report → debugger** |
| The behavior in code differs from the assert, the cause is unclear | **bug-report → debugger** |
| 1C is not running, fixtures are not up | environment_error → orchestrator |

---

## 2. Self-recovery limits (when it is considered "exhausted")

| Agent | What it fixes itself | Limit | When it creates a bug-report |
|---|---|---|---|
| `developer-code` | Its own syntax/logic in its own code | 2 attempts | The test fails not because of my code OR 2 attempts are exhausted without understanding the cause |
| `tester` | Technical errors in test code (the test logic does not change) | 3 attempts | The failure is not fixed by changing the test code OR 3 attempts are exhausted |
| `scenario-coder` | Its own step implementations | 2 attempts | Red gate is green without explanation; a step fails for a non-obvious reason; 2 attempts are exhausted |

The other agents (`developer-tests`, `scenario-author`, `analyst`, `architect`, `explorer`, `reviewer`) do **not** create bug-reports — they either do not run code or handle remarks through the Reviewer cycle.

---

## 3. `bug-report.json` structure

**Location:** `task_dir/.context/bugs/<bug-id>.json`

**ID:** `bug-<task-id>-<seq>`, for example `bug-T-042-001`. Numbering within the task, sequential.

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
    "quote": "For VIP clients, the discount MUST be 20%."   // * verbatim from the source
  },

  "scenario_context": {                                     // * (see §4 — may be incomplete)
    "incomplete": false,                                    // if true — specify reason
    "incomplete_reason": null,
    "action": "posting the РасходТовара document",
    "user": {
      "name": "Ivanov I.I.",
      "roles": ["Manager"],
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
          "Контрагент": "<empty>"
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

  "debug_trigger": {                                        // recommended, * if the reporter knows how to invoke the code
    "context": "server",                                    // client | server | unknown
    "preferred_method": "yaxunit",                          // yaxunit | vanessa | ui_mcp | mcp_tool | http | scheduled_job | unknown
    "run_after_breakpoint": "run one unit test РасчётСкидки_GivenVIP_Returns20",
    "entry_point": {
      "module": "ОбщийМодуль.Скидки",
      "procedure": "РассчитатьСкидку",
      "line_hint": null
    },
    "target_hint": {
      "user": "AgentAI",
      "infobase_session_number": null,
      "client_kind": "unit-test"
    },
    "timeout_hint_seconds": 30
  },

  "self_fix_attempts": [                                    // * at least one record
    {"what_tried": "checked the formula in РассчитатьСкидку()",  "result": "the formula matches the spec"},
    {"what_tried": "re-ran the test after rebuild",               "result": "the same value 15"}
  ],
  "stopping_reason": "after_2_attempts",                    // * after_N_attempts | suspected_other_layer | out_of_scope

  "hypotheses": [                                           // optional, but if present — with reasoning
    {
      "layer": "data",                                      // code | test | scenario | step | data | spec | unknown
      "agent": "developer-tests",                           // suspected owner
      "reasoning": "the test expects a VIP category for the client, but it may be missing in the fixture"
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
    "blocked_paths": []                                     // occupied by other tasks
  }
}
```

---

## 4. Filling rules

### 4.1 General

- **`expectation.source` + `quote` are mandatory.** Without an explicit quote from the source of truth, a bug-report is not accepted. This eliminates "in my opinion it should be different."
- **`symptom.error_message` must be verbatim.** A direct quote from the assert/exception/log, not a paraphrase.
- **`self_fix_attempts` must contain at least one record.** Even "I read the code, I don't see the reason" is an artifact. This blocks "thrown over the wall."
- **`hypotheses` are optional, but if provided they must include `reasoning`.** A hypothesis without justification is noise.
- **`context.files_touched_this_phase` is mandatory.** The debugger must know what changed recently.
- **Concrete values, not "a typical document".** Cite actual data from the test/fixture/run.
- **`debug_trigger` must always be filled when you know how to invoke the code.** This is not an instruction for the debugger to necessarily use DAP; it is a starting hint for choosing between DAP and tracing through the event log.

### 4.2 `scenario_context` — what and how to fill

The debugger will not be able to reproduce and simulate without understanding which action was performed, under which user, and with what data.

**`action`** — a concrete action in the system: "posting document X", "running processing Y", "calling function Z", "forming a report".

**`user`** — mandatory. There is a lot of branching by permissions in 1C.

**`input_data.kind`** determines which subfields to fill:

- `document` → `document.type`, `is_new` (important — a new document has no reference), `header` (header attributes), `tabular_sections` (row count + first/problematic row).
- `processor` → `processor.name`, `form_fields` (values on the form).
- `function_call` → `module`, `procedure`, `arguments` (actual values).
- `report` → `name`, `parameters`.

**What NOT to dump:**

- Whole objects (`Form Object`, `CatalogObject.<AllFields>`) — only relevant attributes.
- Whole `ValueTable` — only `rows_count` + the first/problematic row.
- Binary data, passwords, tokens, user personal data except service identification.
- Metadata (`Metadata.Dokuments.X.<all>`) — only the type name.

**`is_new: true/false` is critical** — a new document has no reference and many attributes.

**`relevant_db_state`** is filled only if the reporter has already checked the DB state (`platform-data-core` § Query Execution); otherwise `"not checked"`. The debugger will check it themselves.

### 4.3 `debug_trigger` — how the debugger should initiate the code

`debug_trigger` describes how to reproduce execution after the Debugger sets a breakpoint or prepares a trace.

Fill in:

- `context` — where the main code executes: `client`, `server`, `unknown`.
- `preferred_method` — the narrowest way to launch: `yaxunit`, `vanessa`, `ui_mcp`, `mcp_tool`, `http`, `scheduled_job`, `unknown`.
- `run_after_breakpoint` — what exactly to run after setting the breakpoint: command, test, scenario, UI action, tool call.
- `entry_point` — module/procedure/line if the reporter knows the presumed entry point.
- `target_hint` — user, infobase session number, client type, or other target signs if visible from the run.
- `timeout_hint_seconds` — 30 for fast code; for a heavy operation, specify a deliberate limit or `null` with an explanation in `self_fix_attempts`.

If the launch method is unknown, set `preferred_method: "unknown"` and explain what exactly is unknown. Do not invent the target or breakpoint line.

### 4.4 When `scenario_context.incomplete: true`

If the reporter cannot fill the context completely (for example, Developer-Code cannot see how the test prepares the document):

- Set `incomplete: true` and `incomplete_reason` (what exactly could not be determined).
- Fill in the minimum that is visible (for example, only `function_call`).
- The debugger will reconstruct the context first.

**A partial report marked `incomplete` is better than invented data.**

---

## 5. Filling protocols by agent

### 5.1 `developer-code` (Phase 3d)

**Trigger:** a unit test does not pass, and the reason is not in my code OR 2 self-fix attempts are exhausted.

Fill in:
- `symptom.what_ran` — test name + full path.
- `symptom.error_message` — assertion verbatim from stdout/event log.
- `expectation.source` — the spec section or the assert line from the test.
- `scenario_context.input_data.kind = "function_call"` if a unit test failed on a specific function; add `document` if the test is run on a document.
- `debug_trigger.preferred_method = "yaxunit"`; `run_after_breakpoint` — command to run one test; `entry_point` — function/procedure from the failing stack, if known.
- `hypotheses` — if there is a suspicion about test/data/scenario/step, specify it with reasoning.
- `context.files_touched_this_phase` — all BSL/XML files changed in Phase 3d.

### 5.2 `tester` (Phase 4)

**Trigger:** after 3 attempts to fix the test did not help OR the failure is not fixed by changing the test.

Fill in:
- Full `scenario_context` — Tester sees the end-to-end scenario and is required to fill in the maximum.
- `symptom.what_ran` — test name / `.feature` / scenario name.
- `expectation.source` — spec OR Acceptance Scenario from the spec OR assert.
- `debug_trigger` — fill according to the actual launch method: `yaxunit` for unit, `vanessa` for a scenario, `ui_mcp` if the action was reproduced through a test client.
- `hypotheses` — the Tester’s current classification (`test_error` / `implementation_error` / `spec_mismatch`) is mapped into `hypotheses[].layer`.
- `self_fix_attempts` — all 3 attempts with a description of what was changed and the result.

### 5.3 `scenario-coder` (Phase 3c)

**Trigger:**
- Red gate is green without production code (the mock is not obvious), or
- A step fails for a non-obvious reason after 2 attempts.

Fill in:
- `symptom.what_ran` — name of the `.feature` + the specific scenario + the step.
- `expectation.source` — Acceptance Scenario from the spec + the expected Red-gate behavior (should be red).
- `scenario_context.action` — what the scenario does (Given blocks in `.feature` provide the data).
- `scenario_context.input_data` — from the scenario's Given steps.
- `debug_trigger.preferred_method = "vanessa"`; if the step is implemented through client UI actions, add the test-client target_hint if known.
- `hypotheses` — for example `layer: step` if there is suspicion of a hidden mock in the step implementation.

---

## 6. Bug-report lifecycle

| Status | Who changes it | When |
|---|---|---|
| `open` | reporter | When created |
| `in_investigation` | orchestrator | When debugger starts |
| `fixed_locally` | debugger | After local fix + verification |
| `returned_to_author` | debugger | If the fix is large, return to the responsible agent |
| `escalated_to_user` | orchestrator | After exhausting hypotheses or 2 bug→fix→bug cycles |

**Duplicate control:** if the same symptom matches (`symptom.fail_location` + `symptom.error_message`) — update the existing bug-report (new `self_fix_attempts` entry, new `hypotheses`), do NOT create a new one.

---

## 7. Anti-patterns

| Anti-pattern | Why it is bad |
|---|---|
| `error_message` is paraphrased in your own words | Exact signature and stack trace are lost |
| `expectation.quote` is missing or "well, by logic..." | No source of truth → the debugger does not know what to compare against |
| Dumping the whole object in `scenario_context` | Clutters the report, may contain sensitive data |
| Hypothesis without `reasoning` | Noise, the debugger cannot prioritize |
| Empty `self_fix_attempts: []` | There was not even an attempt to understand it → then it is not a bug for the debugger |
| Creating a new bug-report for the same symptom | Duplicates interfere with tracking; update the existing one |
| `scenario_context` with invented data instead of `incomplete: true` | The debugger will go down the wrong path |
| Empty `debug_trigger` when the launch method is known | The debugger wastes time reconstructing what the reporter already knows |

---
depends_on:
  - framework/rules/source-of-truth/SKILL.md
---
