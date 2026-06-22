---
name: bug-reporting
description: "MUST use WHEN the subagent has exhausted its self-recovery limit and must hand the problem to the orchestrator for investigation. Provides the standard bug-report.json form and the criteria for 'this is a bug for the debugger'."
alwaysApply: false
---

# Bug Reporting — standard form and criteria

## 1. When to create a bug-report

A problem becomes a **bug for the debugger** only if ALL of the following conditions are met:

1. **It is observed at runtime** — a test failed, a scenario failed, an exception, an unexpected result. Static remarks (style, coverage, formatting) are a Reviewer BLOCK, not a bug.
2. **There is a mismatch between "expectation ≠ fact"**, and the expectation comes from a **specific source** (spec, design, test assert, Acceptance Scenario), not from a guess.
3. **The agent has exhausted its own self-recovery limit** (see §2 - each agent has its own).
4. **This is NOT a requirements ambiguity** — otherwise the path is `clarification_needed` → user.
5. **This is NOT a missing API in the design** — otherwise the path is `clarification_needed` → Architect via the orchestrator.
6. **This is NOT an infrastructure problem** (database is not running, server does not respond, file not found) — that is `environment_error`, and it goes to the orchestrator as an infra problem, not to the debugger.

If at least one condition is not met, a bug-report is NOT created.

| Situation | Where it goes |
|---|---|
| Typo in your own code formula, visible immediately | self-fix |
| Test does not compile after your own change | self-fix |
| Style/coverage/formatting | Reviewer BLOCK |
| Spec is contradictory/incomplete | clarification → user |
| The design does not contain the needed API | clarification → Architect |
| Test/scenario failed, the reason is not obvious within the attempt limit | **bug-report → debugger** |
| Vanessa Red-gate is green, the mock is not obvious | **bug-report → debugger** |
| Behavior in code differs from the assert, the reason is unclear | **bug-report → debugger** |
| 1C is not running, fixtures are not started | environment_error → orchestrator |

---

## 2. Self-recovery limits (when "exhausted")

| Agent | What it fixes by itself | Limit | When it creates a bug-report |
|---|---|---|---|
| `developer-code` | Its own syntax/logic in its own code | 2 attempts | The test fails not because of my code OR 2 attempts are exhausted without understanding the cause |
| `tester` | Technical errors in the test code (the test logic does not change) | 3 attempts | The failure is not fixed by changing the test code OR 3 attempts are exhausted |
| `scenario-coder` | Its own step implementations | 2 attempts | Red-gate is green without explanation; the step fails for an unclear reason; 2 attempts are exhausted |

Other agents (`developer-tests`, `scenario-author`, `analyst`, `architect`, `explorer`, `reviewer`) do **not** create bug-reports — they either do not run code or handle remarks through the Reviewer cycle.

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
    "error_message": "Expected 20, got 15",                 // * verbatim, не пересказ
    "log_path": "task_dir/runs/2026-04-27T14-30/test.log",  // *
    "deterministic": true                                   // * true | false | unknown
  },

  "expectation": {                                          // *
    "source": "spec.md §3.2",                               // * file + section
    "quote": "Для VIP-клиентов скидка MUST составлять 20%." // * verbatim из источника
  },

  "scenario_context": {                                     // * (см. §4 — может быть incomplete)
    "incomplete": false,                                    // если true — указать reason
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

  "self_fix_attempts": [                                    // * минимум одна запись
    {"what_tried": "проверил формулу в РассчитатьСкидку()",  "result": "формула совпадает со спекой"},
    {"what_tried": "перепрогнал тест после rebuild",         "result": "то же значение 15"}
  ],
  "stopping_reason": "after_2_attempts",                    // * after_N_attempts | suspected_other_layer | out_of_scope

  "hypotheses": [                                           // опционально, но если есть — с reasoning
    {
      "layer": "data",                                      // code | test | scenario | step | data | spec | unknown
      "agent": "developer-tests",                           // подозреваемый владелец
      "reasoning": "тест ожидает VIP-категорию у клиента, но в фикстуре её может не быть"
    }
  ],

  "context": {                                              // *
    "files_touched_this_phase": [                           // * что менялось в этой фазе
      "src/CommonModules/Скидки/Module.bsl"
    ],
    "related_artifacts": [                                  // *
      "spec.md",
      "tests/unit-РасчётСкидки.bsl"
    ],
    "protected_paths": [],                                  // что дебаггеру НЕ трогать
    "blocked_paths": []                                     // занято другими задачами
  }
}
```

---

## 4. Filling rules

### 4.1 General

- **`expectation.source` + `quote` are mandatory.** Without an explicit quote from the source of truth, the bug-report is not accepted. This removes "in my opinion it should be different".
- **`symptom.error_message` must be verbatim.** A direct quote from the assert/exception/log, not a paraphrase.
- **`self_fix_attempts` must contain at least one entry.** Even "I read the code and do not see the cause" is an artifact. This blocks "threw it over the wall".
- **`hypotheses` are optional, but if provided they must include `reasoning`.** A hypothesis without justification is noise.
- **`context.files_touched_this_phase` is mandatory.** The debugger must know what changed recently.
- **Concrete values, not "a typical document".** Quote the actual data from the test/fixture/run.

### 4.2 `scenario_context` - what to fill and how

The debugger will not be able to reproduce and model the issue without understanding which action was performed, under which user, and with which data.

**`action`** — a specific action in the system: "posting document X", "running processing Y", "calling function Z", "generating report".

**`user`** — mandatory. In 1C there are many branches by permissions.

**`input_data.kind`** determines which subfields to fill:

- `document` → `document.type`, `is_new` (important - a new document has no reference), `header` (header attributes), `tabular_sections` (row count + first/problematic row).
- `processor` → `processor.name`, `form_fields` (values on the form).
- `function_call` → `module`, `procedure`, `arguments` (actual values).
- `report` → `name`, `parameters`.

**What NOT to dump:**

- Objects in full (`Form object`, `СправочникОбъект.<ВсеПоля>`) - only relevant attributes.
- `ТаблицаЗначений` in full - only `rows_count` + the first/problematic row.
- Binary data, passwords, tokens, user personal data except service identification.
- Metadata (`Метаданные.Документы.X.<всё>`) - only the type name.

**`is_new: true/false` is critical** - a new document has no reference and many attributes.

**`relevant_db_state`** is filled in only if the reporter has already checked the database state (`platform-data-core` § Query Execution); otherwise `"not checked"`. The debugger will check it itself.

### 4.3 When `scenario_context.incomplete: true`

If the reporter cannot fill the context completely (for example, Developer-Code cannot see how the test prepares the document):

- Set `incomplete: true` and `incomplete_reason` (what exactly could not be determined).
- Fill in the minimum that is visible (for example, only `function_call`).
- The debugger will reconstruct the context first.

**A partial report marked `incomplete` is better than invented data.**

---

## 5. Filling protocols by agent

### 5.1 `developer-code` (Phase 3d)

**Trigger:** a unit test does not pass, the reason is not in my code OR 2 self-fix attempts are exhausted.

Filling:
- `symptom.what_ran` — test name + full path.
- `symptom.error_message` — assertion verbatim from stdout/event-log.
- `expectation.source` — spec section or assert line from the test.
- `scenario_context.input_data.kind = "function_call"` if a unit test on a specific function failed; add `document` if the test runs on a document.
- `hypotheses` — if there is a suspicion of test/data/scenario/step, specify it with reasoning.
- `context.files_touched_this_phase` — all BSL/XML files changed in Phase 3d.

### 5.2 `tester` (Phase 4)

**Trigger:** after 3 attempts to fix the test did not help OR the failure is not fixed by a test change.

Filling:
- Full `scenario_context` - Tester sees the end-to-end scenario and must fill in the maximum.
- `symptom.what_ran` — test name / `.feature` / scenario name.
- `expectation.source` — spec OR Acceptance Scenario from the spec OR assert.
- `hypotheses` — the current Tester classification (`test_error` / `implementation_error` / `spec_mismatch`) is mapped into `hypotheses[].layer`.
- `self_fix_attempts` — all 3 attempts with a description of what was changed and the result.

### 5.3 `scenario-coder` (Phase 3c)

**Trigger:**
- Red-gate is green without production code (the mock is not obvious), or
- The step fails for an unclear reason after 2 attempts.

Filling:
- `symptom.what_ran` — `.feature` name + specific scenario + step.
- `expectation.source` — Acceptance Scenario from the spec + expected Red-gate behavior (it must be red).
- `scenario_context.action` — what the scenario does (Given blocks in `.feature` provide the data).
- `scenario_context.input_data` — from the scenario Given steps.
- `hypotheses` — for example `layer: step` if there is a suspicion of a hidden mock in the step implementation.

---

## 6. Bug-report lifecycle

| Status | Who changes it | When |
|---|---|---|
| `open` | reporter | When created |
| `in_investigation` | orchestrator | When debugger starts |
| `fixed_locally` | debugger | After a local fix + verification |
| `returned_to_author` | debugger | If the fix is large, return it to the relevant agent |
| `escalated_to_user` | orchestrator | After hypotheses are exhausted or after 2 bug→fix→bug cycles |

**Duplicate control:** if the same symptom matches (`symptom.fail_location` + `symptom.error_message`) — update the existing bug-report (new `self_fix_attempts` entry, new `hypotheses`), do NOT create a new one.

---

## 7. Anti-patterns

| Anti-pattern | Why it is bad |
|---|---|
| `error_message` is paraphrased in your own words | Exact signature and stack trace are lost |
| `expectation.quote` is missing or "well, logically..." | No source of truth -> the debugger does not know what to compare against |
| Dumping the entire object in `scenario_context` | Pollutes the report, may contain sensitive data |
| Hypothesis without `reasoning` | Noise, the debugger cannot prioritize |
| `self_fix_attempts: []` is empty | There was not even an attempt to understand it -> therefore it is not a bug for the debugger |
| Creating a new bug-report for the same symptom | Duplicates interfere with tracking; update the existing one |
| `scenario_context` with invented data instead of `incomplete: true` | The debugger will go down the wrong path |

---
depends_on:
  - framework/rules/source-of-truth/SKILL.md
---
