---
name: bug-reporting
description: "MUST use WHEN the subagent has exhausted its self-recovery limit and must hand the problem to the orchestrator for investigation. Provides the standard bug-report.json form and the criteria for \"this is a bug for the debugger\"."
alwaysApply: false
---

# Bug Reporting — form standard and criteria

## 1. When to create a bug-report

The problem becomes a **bug for the debugger** only if ALL conditions are met:

1. **Observed at runtime** — a test failed, a scenario failed, an exception occurred, or the result was unexpected. Static remarks (style, coverage, formatting) are a Reviewer BLOCK, not a bug.
2. **There is a mismatch between "expected ≠ actual"**, and the expectation comes from a **specific source** (spec, design, test assert, Acceptance Scenario), not from a guess.
3. **The agent has exhausted its own self-recovery limit** (see §2 — each agent has its own).
4. **This is NOT a requirements ambiguity** — otherwise the path is `clarification_needed` → user.
5. **This is NOT a missing API in the design** — otherwise the path is `clarification_needed` → Architect through the orchestrator.
6. **This is NOT an infrastructure problem** (DB not started, server not responding, file not found) — that is `environment_error`, it goes to the orchestrator as an infra problem, not to the debugger.

If at least one condition is not met, no bug-report is created.

| Situation | Where it goes |
|---|---|
| A typo in the formula of your own code, visible immediately | self-fix |
| A test does not compile after your own change | self-fix |
| Style/coverage/formatting | Reviewer BLOCK |
| The spec is contradictory/incomplete | clarification → user |
| The design lacks the required API | clarification → Architect |
| A test/scenario failed, the cause is not obvious within the attempt limit | **bug-report → debugger** |
| Vanessa Red-gate is green, the mock is not obvious | **bug-report → debugger** |
| Behavior in code diverges from the assert, the cause is unclear | **bug-report → debugger** |
| 1C is not started, fixtures are not up | environment_error → orchestrator |

---

## 2. Self-recovery limits (when "exhausted")

| Agent | What it fixes itself | Limit | When it creates a bug-report |
|---|---|---|---|
| `developer-code` | Its own syntax/logic in its own code | 2 attempts | The test fails for a reason unrelated to my code OR 2 attempts are exhausted without understanding the cause |
| `tester` | Technical errors in the test code (the test logic does not change) | 3 attempts | The failure cannot be fixed by changing the test code OR 3 attempts are exhausted |
| `scenario-coder` | Its own step implementations | 2 attempts | Red gate is green without explanation; a step fails for an unclear reason; 2 attempts are exhausted |

The other agents (`developer-tests`, `scenario-author`, `analyst`, `architect`, `explorer`, `reviewer`) do **not** create bug-reports — they either do not run code or handle remarks through the Reviewer loop.

---

## 3. Structure of `bug-report.json`

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
    "incomplete": false,                                    // if true, specify reason
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

  "debug_trigger": {                                        // recommended, * if the reporter knows how to invoke the code
    "context": "server",                                    // client | server | unknown
    "preferred_method": "yaxunit",                          // yaxunit | vanessa | ui_mcp | mcp_tool | http | scheduled_job | unknown
    "run_after_breakpoint": "запустить один unit-test РасчётСкидки_GivenVIP_Returns20",
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
    {"what_tried": "проверил формулу в РассчитатьСкидку()",  "result": "формула совпадает со спекой"},
    {"what_tried": "перепрогнал тест после rebuild",         "result": "то же значение 15"}
  ],
  "stopping_reason": "after_2_attempts",                    // * after_N_attempts | suspected_other_layer | out_of_scope

  "hypotheses": [                                           // optional, but if present, include reasoning
    {
      "layer": "data",                                      // code | test | scenario | step | data | spec | unknown
      "agent": "developer-tests",                           // suspected owner
      "reasoning": "тест ожидает VIP-категорию у клиента, но в фикстуре её может не быть"
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
    "protected_paths": [],                                  // paths the debugger MUST NOT touch
    "blocked_paths": []                                     // occupied by other tasks
  }
}
```

---

## 4. Fill Rules

### 4.1 General

- **`expectation.source` + `quote` are mandatory.** Without an explicit quote from the source of truth, the bug-report is not accepted. This removes "in my opinion it should be different."
- **`symptom.error_message` must be verbatim.** A direct quote from the assert/exception/log, not a paraphrase.
- **`self_fix_attempts` must contain at least one record.** Even "read the code, I cannot see the reason" is an artifact. This blocks "thrown over the wall."
- **`hypotheses` are optional, but if provided, they must include `reasoning`.** A hypothesis without justification is noise.
- **`context.files_touched_this_phase` is mandatory.** The debugger must know what changed recently.
- **Use concrete values, not "a typical document".** Quote actual data from the test/fixture/run.
- **Fill `debug_trigger` whenever the code path is known.** This is not an instruction for the debugger to necessarily use DAP; it is the starting hint for choosing between DAP and a trace through the event log.

### 4.2 `scenario_context` — what to fill and how

The debugger cannot reproduce and simulate the issue without understanding which action was performed, under which user, and with which data.

**`action`** — a concrete system action: "posting document X", "running processing Y", "calling function Z", "building report".

**`user`** — mandatory. 1C has many branches based on permissions.

**`input_data.kind`** determines which subfields to fill:

- `document` → `document.type`, `is_new` (important: a new document has no reference), `header` (header attributes), `tabular_sections` (row count + first/problem row).
- `processor` → `processor.name`, `form_fields` (values on the form).
- `function_call` → `module`, `procedure`, `arguments` (actual values).
- `report` → `name`, `parameters`.

**What not to dump:**

- Whole objects (`FormObject`, `CatalogObject.<AllFields>`) — only relevant attributes.
- A whole `ValueTable` — only `rows_count` + the first/problem row.
- Binary data, passwords, tokens, user personal data beyond service identification.
- Metadata (`Metadata.Documents.X.<everything>`) — only the type name.

**`is_new: true/false` is critical** — a new document has no reference and many attributes are missing.

**`relevant_db_state`** is filled only if the reporter has already checked the DB state (`platform-data-core` § Query Execution); otherwise use `"not checked"`. The debugger will check it independently.

### 4.3 `debug_trigger` — how the debugger should initiate the code

`debug_trigger` describes how to reproduce execution after the Debugger sets a breakpoint or prepares a trace.

Fill in:

- `context` — where the main code executes: `client`, `server`, `unknown`.
- `preferred_method` — the narrowest way to launch it: `yaxunit`, `vanessa`, `ui_mcp`, `mcp_tool`, `http`, `scheduled_job`, `unknown`.
- `run_after_breakpoint` — exactly what to run after setting the breakpoint: a command, test, scenario, UI action, or tool call.
- `entry_point` — module/procedure/line, if the reporter knows the expected entry point.
- `target_hint` — user, infobase session number, client type, or other target markers, if visible from the run.
- `timeout_hint_seconds` — 30 for fast code; for a heavy operation, specify a deliberate limit or `null` with an explanation in `self_fix_attempts`.

If the launch method is unknown, set `preferred_method: "unknown"` and explain what exactly is unknown. Do not invent the target or breakpoint line.

### 4.4 When `scenario_context.incomplete: true`

If the reporter cannot fill the context completely (for example, Developer-Code cannot see how the test prepares the document):

- Set `incomplete: true` and `incomplete_reason` (what exactly could not be established).
- Fill in the minimum that is visible (for example, only `function_call`).
- The debugger will reconstruct the context first.

**A partial report marked `incomplete` is better than invented data.**

---

## 5. Filling Protocols by Agent

### 5.1 `developer-code` (Phase 3d)

**Trigger:** a unit test fails, the reason is not in my code OR 2 self-fix attempts are exhausted.

Fill in:
- `symptom.what_ran` — test name + full path.
- `symptom.error_message` — assertion verbatim from stdout/event log.
- `expectation.source` — the spec section or assert line from the test.
- `scenario_context.input_data.kind = "function_call"` if a unit test failed on a specific function; add `document` if the test runs against a document.
- `debug_trigger.preferred_method = "yaxunit"`; `run_after_breakpoint` — the command to run one test; `entry_point` — the function/procedure from the failing stack, if known.
- `hypotheses` — if there is suspicion of test/data/scenario/step, specify it with reasoning.
- `context.files_touched_this_phase` — all BSL/XML files changed in Phase 3d.

### 5.2 `tester` (Phase 4)

**Trigger:** after 3 attempts to fix the test, it still did not help OR the failure cannot be fixed by changing the test.

Fill in:
- Full `scenario_context` — Tester sees the end-to-end scenario and must fill in as much as possible.
- `symptom.what_ran` — test name / `.feature` / scenario name.
- `expectation.source` — spec OR Acceptance Scenario from the spec OR assert.
- `debug_trigger` — fill in according to the actual run method: `yaxunit` for unit, `vanessa` for a scenario, `ui_mcp` if the action was reproduced through the test client.
- `hypotheses` — the current Tester classification (`test_error` / `implementation_error` / `spec_mismatch`) is mapped into `hypotheses[].layer`.
- `self_fix_attempts` — all 3 attempts with what changed and the result.

### 5.3 `scenario-coder` (Phase 3c)

**Trigger:**
- the Red gate is green without production code (the mock is not obvious), or
- a step fails for an unclear reason after 2 attempts.

Fill in:
- `symptom.what_ran` — `.feature` name + concrete scenario + step.
- `expectation.source` — Acceptance Scenario from the spec + expected Red-gate behavior (must be red).
- `scenario_context.action` — what the scenario does (Given blocks in the `.feature` provide the data).
- `scenario_context.input_data` — from the scenario's Given steps.
- `debug_trigger.preferred_method = "vanessa"`; if the step is implemented through client UI actions, add a target_hint for the test client if it is known.
- `hypotheses` — for example `layer: step` if a hidden mock is suspected in the step implementation.

---

## 6. Bug-report Life Cycle

| Status | Who changes it | When |
|---|---|---|
| `open` | reporter | On creation |
| `in_investigation` | orchestrator | When the debugger starts |
| `fixed_locally` | debugger | After a local fix + verification |
| `returned_to_author` | debugger | If the fix is large, return it to the relevant agent |
| `escalated_to_user` | orchestrator | After hypotheses are exhausted or after 2 bug→fix→bug cycles |

**Duplicate control:** if the same symptom matches (`symptom.fail_location` + `symptom.error_message`), the existing bug-report is updated (a new `self_fix_attempts` entry, new `hypotheses`), and no new one is created.

---

## 7. Anti-patterns

| Anti-pattern | Why it is bad |
|---|---|
| `error_message` paraphrased in your own words | The exact signature and stack trace are lost |
| `expectation.quote` is missing or "well, logically..." | No source of truth, so the debugger does not know what to compare against |
| Dumping the whole object into `scenario_context` | Clutters the report, may contain sensitive data |
| A hypothesis without `reasoning` | Noise, the debugger cannot prioritize |
| Empty `self_fix_attempts: []` | There was not even an attempt to understand the issue, so it is not a bug for the debugger |
| Creating a new bug-report for the same symptom | Duplicates hinder tracking; update the existing one |
| `scenario_context` with invented data instead of `incomplete: true` | The debugger will go down the wrong path |
| Empty `debug_trigger` when the launch method is known | The debugger wastes time reconstructing what the reporter already knows |

---
depends_on:
  - framework/rules/source-of-truth/SKILL.md
---
