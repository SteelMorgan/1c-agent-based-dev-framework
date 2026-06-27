---
name: runtime-investigation
description: "Runtime bug diagnostics: call graph, DAP, tracing"
---

# Runtime Investigation — runtime bug investigation

## 1. When to Use

The goal of this skill is to answer three questions in strict order:

1. **What is actually happening?** Is the procedure being called? With which arguments? What are the variable values? What is the if/else path? What did the query return?
2. **Does this match the expectation?** (from spec/design/test assertion - `bug-report.expectation`)
3. **Where is the source of the mismatch?**
   - **The code is wrong** - behavior does not match the requirement
   - **The code is correct, the data is not** - the contract is violated on the caller/preparation side
   - **The code matches the spec, the spec is wrong/incomplete**
   - **The test/scenario checks the wrong thing**

Without step 1, steps 2-3 are impossible.

**Launch trigger:** the orchestrator passed `bug-report.json` with status `open`.

---

## 2. Tool Hierarchy (from cheap to expensive)

| Level | Tool | When |
|---|---|---|
| **L0** | Reading source code + spec/design (`code-navigation`) | Always first |
| **L1** | `event-log-analysis` - event log through ClickHouse | An already executed run, there is Error/Warning |
| **L2** | `platform-data-core` § Query Execution - database queries | Check data state independently of the code |
| **L3** | `dap-bsl-code-debug-procedure` - interactive DAP/MCP debugger | There is a safe reproducible scenario and stack/locals/step at 1-3 points are needed |
| **L4** | `agent-debug` markers in code + event log | DAP is not suitable or a broad trace is needed: call fact, if/else path, variable value/type |
| **L5** | Re-run the scenario/test after DAP/probes | Collect observations |
| **L6** | `gui-control` + `screenshot` | The symptom is in the UI, it is unclear what is on the form |
| **L7** | `syntax-checking` (`get_diagnostics` / `v8-runner syntax …`) | After any code change |
| **L8** | `tech-log-analysis` - technical log | **ONLY with the user's explicit consent.** Heavy, slow. When L0-L7 did not produce an answer: locks, deadlock, hidden platform exceptions, slow SQL |

The L0-L7 debugger is used autonomously. Moving to L8 requires going back to the orchestrator with a **structured request**:
- Which hypothesis cannot be checked through L0-L7 and why
- Which technical log events are needed (EXCP / DBMSSQL / TLOCK / TDEADLOCK / TTIMEOUT / CALL)
- Approximate collection time

The orchestrator asks the user again. Without consent - DO NOT raise it.

---

## 3. Full Algorithm

```
PHASE 1. Preparation
  1.1  Read bug-report.json. Change status -> in_investigation.
  1.2  Reproduce the bug deterministically (run the specified test/scenario).
       - Does not reproduce -> flaky, escalate to the orchestrator.
  1.3  Read the code around the symptom point + the spec/design (L0).
  1.4  Build the CALL GRAPH from the scenario/test entry point to the symptom point (see §4).
  1.5  Identify the KEY VARIABLES (see §5).

PHASE 2. First pass (WITHOUT hypotheses)
  2.1  Choose the runtime observation method:
       - DAP/MCP debugger: if it is safe to stop the thread and stack/locals/step are needed.
       - agent-debug + event log: if a broad trace is needed or stopping the thread is risky.
  2.2  For DAP: set a breakpoint at the key point, run the scenario, poll `wait_for_stop`
       every 5 seconds (fast code - up to 30 seconds; heavy - by the pre-set limit),
       record stack/locals/steps in trace-run-1.md, then clear the breakpoint, release the thread and detach.
  2.3  For agent-debug: place H0 probes on the graph nodes
       (prefix `AGENTDEBUG-<bug-id>-H0-NNN`):
       - EXECUTED marker
       - snapshot of key variables (safe serialization - §6)
       Run the scenario/test.
  2.4  Read the event log or DAP observations, assemble the trace: which nodes were passed, variable state.
       Save to task_dir/.context/debug/<bug-id>/trace-run-1.md.
  2.5  Compare the trace with the expectation. Localize the first mismatch "expectation != fact".
       If the trace is sufficient to determine the cause immediately -> move to Phase 4.

PHASE 3. Hypothesis cycle (<= 5 iterations; +3 extension, max 8 - see §7)
  For hypothesis N (1..5, with extension 6..8):

    3.N.1  Formulate the most likely hypothesis BASED ON THE CURRENT TRACE
           (not from thin air). Record in debug-report.md:
           - formulation
           - evidence_from_trace (which fact from the trace it is based on)

    3.N.2  Choose the verification method:
           (a) trial fix - a narrow change in code/test/scenario
               that is easy to roll back;
           (b) additional probes (prefix `AGENTDEBUG-<bug-id>-H<N>-NNN`) -
               new key variables, nodes between marked ones,
               data state through platform-data-core § Query Execution.

    3.N.3  Apply, run, read the trace. Save trace-run-<N+1>.md.

    3.N.4  Branch:
           ✓ CONFIRMED -> move to Phase 4 (fix by the rules)
           ✗ NOT confirmed:
               - roll back the trial fix (if any)
               - remove probes of THIS hypothesis only (grep H<N>); H0 probes and
                 previous disproved hypotheses STAY
               - record in debug-report.md: what was checked, result,
                 why it was disproved
               - move to hypothesis N+1

  Between iterations, it is allowed to return to Phase 1 and expand the graph/key
  variables by adding new H0+ probes (for example, new callers appeared).
  This does not count as a separate hypothesis.

  After 5 unconfirmed hypotheses:
    - if there is a concrete next hypothesis with high confidence ->
      contact the orchestrator with a request for a +3 extension (max 8 total)
    - otherwise -> Phase 5 (escalation)

PHASE 4. Fix (if the hypothesis was confirmed)
  4.1  Evaluate the scope by the "local vs return" criterion (§8).
  4.2  Local -> apply the fix, rerun the failed test/scenario + related ones.
       - It must turn green
       - If not, it was a wrong hypothesis, go back to 3.N.4 with rollback
  4.3  Large-scale -> return to the orchestrator with an explanation and recommendation
       (which agent to hand off to).

PHASE 5. Escalation (5/8 hypotheses exhausted or the scope is too large)
  5.1  Short structured report to the orchestrator (see §9).
  5.2  The orchestrator forwards it to the user.

PHASE 6. Cleanup (ALWAYS before finishing - success or escalation)
  6.1  If DAP was used: `clear_breakpoints`, safe `continue`, `detach`;
       with `ibInDebug`/a stuck session - `force_detach` and re-check targets.
  6.2  grep `//[AGENTDEBUG-` -> zero matches in ALL affected files.
  6.3  If the technical log was enabled - restore the original config.
  6.4  syntax-checking on the affected modules.
  6.5  Final debug-report.md with the final status and update
       bug-report.json (status: fixed_locally / returned_to_author / escalated_to_user).
```

---

## 4. Building the Call Graph

The starting point is the location of the observed symptom (failed assert, exception, incorrect value from `bug-report.symptom.fail_location`).

**Method:** go BACKWARD from the symptom up the stack:
- Which procedure called it?
- Who called that one?
- ... up to the scenario/test entry point.

**Tools:** `code-navigation` (symbol navigation), reading the module, search for `Call` / `Execute` / form event handlers / manager export procedures.

**Result:** a list of graph nodes in the form:
```
[Test.MyTest]
  -> [Document.GoodsIssue.Object.PostingRoutine]
    -> [CommonModule.CalculateDiscount]
      -> [CommonModule.GetCustomerCategory]  <- symptom point
```

Save as `task_dir/.context/debug/<bug-id>/call-graph.md`.

---

## 5. Identifying Key Variables

**Definition:** a key variable is one that affects:
1. The execution condition of the problematic point (enters `If/Else/While/For` on the path to the symptom), or
2. The result of the computation at the problematic point (participates in the formula/query/return value), or
3. Branching higher up the stack that leads to this point.

**Identification method - reverse traversal:**

1. At the symptom point: which variables participate in the assert/formula? -> key.
2. Up the graph: which variables participate in the conditions leading to this point? -> key.
3. Procedure parameters passed and transformed along the path -> key.
4. Global session parameters (current user, relevance date, active organization) - **key by default**, unless proven otherwise.

**NOT key:** local variables used only for calculation without affecting branching and not returned.

Save as `task_dir/.context/debug/<bug-id>/instrumentation-plan.md`: which probes are placed where, which key variables are in each.

---

## 6. Safe Serialization for Logging

In `agent-debug` probes, record variable values. **Do NOT dump them wholesale:**

| Type | What NOT to log | What to log instead |
|---|---|---|
| Document/Catalog Object | The entire object | `TypeOf`, `Ref`, relevant attributes one by one |
| ValueTable | All rows | `Count()`, fields of the first/problematic row |
| Structure | Serialization | `Count()`, list of keys separated by commas |
| Map | Serialization | `Count()`, key-target if looking for a specific one |
| Form object | As a whole | Specific form attributes one by one |
| Query | Full text | Name, key parameters |
| Metadata | `Metadata.X.<all>` | Only the type name: `Metadata(Ref).Name` |
| Binary data | Contents | `Size()` |
| Passwords, tokens, personal data | Never | Mask or skip |

**Main rule:** log only those object fields that the code actually reads on the path to the symptom (determined by §5). Do not dump the entire object.

**Parameter-object as a key variable:** if the key variable is a reference/object, the experiment must be modeled with **the exact object on which the bug reproduces**. Do not substitute a "similar" one from the database.

---

## 7. Hypothesis Limit

**Default: 5 hypotheses.** After the 5th unconfirmed one - escalation.

**+3 extension (max 8 total):** allowed once if:
- there is a concrete next hypothesis with **high confidence** (there is direct evidence from the trace),
- a request has been sent to the orchestrator with justification,
- the orchestrator agreed.

If confidence is low - DO NOT request an extension, escalate immediately.

**Quality over quantity.** Each hypothesis in `debug-report.md` must have `evidence_from_trace` - which fact from the collected trace it is based on. This blocks "guesswork hypotheses".

---

## 8. "Local Fix vs Return to Orchestrator" Criterion

**The debugger fixes it themselves if ALL conditions are met:**
- Change in <= 2 prod-code files OR <= 1 test/scenario file
- Public API does not change (exported procedures, their signatures)
- Spec and technical design do not change
- Does not affect `protected_paths` from bug-report
- Fix fits within ~30 lines of diff

**Return to the orchestrator in any of these cases:**
- The spec needs to change -> Analyst
- The technical design needs to change or an API needs to be added -> Architect
- More than 2 files need to be rewritten -> Developer-Code
- `.feature` or step-library need broad changes -> Scenario-Author / Scenario-Coder
- The bug is in data and requires revising the test environment preparation -> Developer-Tests or Scenario-Coder

After a local fix - **mandatory verification**:
1. Re-run the failed test/scenario -> must be green.
2. Re-run related unit tests for the module and Vanessa scenarios with the same task tag.
3. Check that nothing adjacent broke (narrow regression).
4. If verification failed - it was a wrong hypothesis, roll back the fix, return to 3.N.4.

A local fix ALWAYS goes through review (Reviewer scope=`debug` or the corresponding artifact type) - otherwise it bypasses quality control.

---

## 9. `debug-report.md` Template

Saved to `task_dir/.context/debug/<bug-id>/debug-report.md`.

```markdown
# Debug Report — <bug-id>

## Source
- Bug-report: <link to bug-report.json>
- Symptom: <symptom.what_ran> failed at <fail_location>
- Expectation: <expectation.quote> (source: <expectation.source>)

## Reproduction
- Command: <symptom.command>
- Determinism: <yes/no>

## Call Graph
<link to call-graph.md>

## Key Variables
<link to instrumentation-plan.md>

## First Pass (H0)
- Run: <link to trace-run-1.md>
- Mismatch localization: <graph node + what did not match>

## Hypotheses

### H1: <formulation>
- Evidence_from_trace: <which fact from the trace it is based on>
- Verification method: <fix / additional probes>
- Run: <link to trace-run-N.md>
- Result: CONFIRMED / DISPROVED
- If disproved - why: <...>

### H2: ...
...

## Verdict
- Cause class: code / data / spec / test-scenario
- Root cause: <...>
- Affected truth-source layer (L1-L6): <see source-of-truth-policy>

## Action
- OPTION A - Local Fix:
  - File(s): <...>
  - Diff: <= 30 lines
  - Verification: failed test green, related tests green
  - Requires review: scope=debug
- OPTION B - Return to orchestrator:
  - Who to hand off to: <agent>
  - Why the scope is large: <...>
  - Fix recommendation: <...>
- OPTION C - Escalation:
  - 5/8 hypotheses not confirmed
  - What was established for sure: <...>
  - What we wanted to check but could not: <...>
  - Recommendation: who to go to (Architect / Analyst / user)

## Cleanup
- [x] DAP breakpoints removed, thread released through `continue`/release, `detach` / `force_detach` performed (if DAP was used)
- [x] grep `//[AGENTDEBUG-` -> 0 matches
- [x] technical log restored (if it was enabled)
- [x] syntax-checking passed
```

---

## 10. Anti-Patterns

| Anti-pattern | Consequence |
|---|---|
| Hypothesis without `evidence_from_trace` | Guessing; investigation resources are wasted |
| Not removing probes of a disproved hypothesis before the next one | Noise in the trace, confusion in interpretation |
| Leaving a trial fix in place after the hypothesis was disproved | Accumulation of junk in the code |
| Dumping the whole object in an `agent-debug` point | Event log overflow, data leak |
| DAP breakpoint left active | Subsequent runs stop at unexpected places |
| `detach`/`force_detach` not performed when `ibInDebug` | The database remains occupied by the debug session |
| Replacing the test object with a "similar" one from the database | The bug will not reproduce, false negative |
| Raising the technical log without the user's consent | Policy violation; heavy process for nothing |
| 10+ H0 probes without clear key variables | Broad observation, unclear result -> split into hypotheses |
| Skipping cleanup before finishing | `AGENTDEBUG` markers will end up in the commit |
| Skipping verification after a local fix | False "fixed", while adjacent behavior actually broke |

---

depends_on:
  - framework/skills/tool-usage/diagnostics/bug-reporting/SKILL.md
  - framework/skills/tool-usage/diagnostics/dap-bsl-code-debug-procedure/SKILL.md
  - framework/skills/tool-usage/diagnostics/agent-debug/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
  - framework/skills/tool-usage/platform-data/platform-data-core/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md
  - framework/rules/dap-bsl-debugger/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
---
