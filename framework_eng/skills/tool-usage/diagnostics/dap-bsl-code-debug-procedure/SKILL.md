---
name: dap-bsl-code-debug-procedure
description: "Interactively debug one BSL procedure through DAP"
uses_capabilities:
  - debug_bsl_code
---

# Debugging a BSL procedure through DAP/MCP

Use this skill for targeted interactive debugging when static analysis, the event log, screenshots, and temporary logging do not answer the actual execution path or variable values.

## Prerequisites

Before starting, the following must be known:

- URL of the 1С HTTP debug server or the local SSH tunnel;
- infobase alias;
- path to the configuration sources;
- paths to extensions, if the breakpoint is in an extension;
- a reproducible scenario that calls the needed procedure;
- a safe execution window for stopping the code.

If the debugger MCP server is not configured, use the instructions in [`docs/info/mcp-bsl-debugger.md`](../../../../../docs/info/mcp-bsl-debugger.md).

## Basic cycle

1. Find the procedure and stop line through `code-navigation` or by reading the module.
2. Connect to the debug server: `attach`.
3. Check targets: `get_targets`.
4. Load or refresh metadata: `reload_metadata`.
5. Set a breakpoint on a line inside the needed procedure: `set_breakpoints`.
6. Only after the breakpoint is set successfully, run the scenario that calls the procedure.
7. Immediately switch to the MCP debugger and poll the stop event: `wait_for_stop` every 5 seconds.
8. For fast code, stop after 30 seconds without a stop event and review the target/breakpoint/scenario. For a heavy operation, define the expected duration and a control wait limit before starting.
9. Inspect the stack and variables: `get_call_stack`, if the tool is available, then `get_variables`.
10. If needed, evaluate safe expressions: `evaluate`.
11. Execute one or more steps: `step_in`, `step_out`, `continue`.
12. Clear the breakpoint: `clear_breakpoints`.
13. Detach: `detach`.

## How to initiate code execution

The order is always the same: **first the breakpoint, then the scenario launch, then polling the stop event in the debugger**. Do not start a test or client action before setting the breakpoint, otherwise the needed section may run before the debugger connects.

After the scenario starts, the agent does not infer a stop from the client "hanging". It immediately switches to the debugger MCP and calls `wait_for_stop` with a 5-second interval:

- fast code: total limit 30 seconds;
- heavy operation: estimate the duration, possible locks, and a safe control limit before starting;
- if the limit is exceeded without a stop event: do not wait forever, check the target, breakpoint line, loaded metadata, the actual call scenario, and the execution context.

### Client context through Vanessa

Use this when the code under debug runs in a form, command, UI handler, or another client context.

1. Start Vanessa/test client in the project’s standard way.
2. Through `v8-client-session-manager` / `v8-session-manager`, get the list of active sessions and identify the test agent session: `infobase_name`, `ib_session_number`, `session_id`, user, and the test-client flag.
3. In the debugger `get_targets`, choose the target that matches this session. If the match is not obvious, compare the start time, user, and infobase session number.
4. Set a breakpoint in the client module.
5. Run the Vanessa scenario or the specific step that calls the needed handler.
6. Immediately switch to the debugger and poll `wait_for_stop` every 5 seconds until the stop event or the control timeout.
7. After `continue`, return to the Vanessa run and wait for its normal completion.

### Client context through MCP client control

Use this when it is easier to trigger the scenario by clicking through it than by writing or running Vanessa.

1. Start the 1С test client.
2. Connect it to `v8-client-session-manager` / `v8-session-manager`.
3. Find its session through `session_list` and map it to the debugger target.
4. Set a breakpoint.
5. Through the manager’s UI tools, open the form, press the command, fill in the field, or perform another action that calls the needed client code.
6. After the stop, control execution through the debugger MCP, not through UI tools.

### Server context through YaxUnit

Use this when the code under debug runs on the server, in a common module with a server purpose, manager, object, posting, or the server side of a form.

1. Prepare a minimal YaxUnit test that calls the needed exported server procedure or the nearest public entry point.
2. If the project already has a tool/runner for invoking an arbitrary server method, you can use it instead of a new test.
3. Set the breakpoint before starting the test.
4. Run one specific test, not the full suite.
5. Immediately switch to `wait_for_stop`; for a fast server method the wait limit is 30 seconds, for a heavy one use a predefined control limit.
6. After `continue`, wait for the test to finish and check its result.

### Server context through a temporary MCP tool in an extension

Use this as a last resort when the server method cannot be conveniently called by a test, HTTP request, or an existing runner/tool.

1. In the extension with `mcp_tools`, temporarily add a narrow tool that calls only the needed server entry point with controlled parameters.
2. Mark the temporary method as debugging-only and do not mix it with the extension’s working API.
3. Set a breakpoint.
4. Call the temporary tool through the MCP facade.
5. After debugging, remove the temporary tool, its registration, exported methods, and test data.
6. Verify that no junk methods, temporary commands, debug names, or extra permissions remain in the sources.

### Other server triggers

HTTP services, scheduled jobs, document posting, or background jobs are allowed if this is safe and reproducible. For such scenarios, record in advance which target must stop, and do not leave a stopped thread in a transaction.

## Choosing a breakpoint

Set the breakpoint not "somewhere in the procedure", but on the line that answers the current question:

- procedure entry - check the call fact and arguments;
- line before `If` - check the variables that affect branching;
- line before the query - check the query parameters;
- line after the query - check the size and key fields of the result;
- line before return - check the final value.

If the procedure does not stop, check:

- whether this exact module is called, not an extension/override module;
- whether the line matches after metadata is loaded;
- whether there is a target of the needed type;
- whether the code is running in another session or background job.

## Working with variables

Look only at the values that affect the current hypothesis:

- procedure parameters;
- variables from the current branch condition;
- query parameters;
- query result, but without a full dump of large tables;
- references and objects only through key attributes.

Do not evaluate expressions with side effects. `evaluate` is allowed for reading simple expressions, but not for writing, posting, HTTP calls, changing global state, or starting business operations.

## Step-by-step debugging

Use steps sparingly:

- `step_in` - when you need to enter the called procedure and see its arguments/branch;
- `step_out` - when the current procedure is already clear and you need the return result;
- `continue` - when you need to reach the next breakpoint or release the thread;
- `pause` - only if you need to stop an already running target and this is safe for the session.

After each step, record what you observed: where you stopped, what values changed, which hypothesis was confirmed or rejected.

## Completion

Before the final answer or handing the task off, make sure to:

1. `clear_breakpoints` for all set points.
2. `continue`, if the thread is still stopped and it is safe to release it.
3. `detach`.
4. If `detach` did not work, `attach` returns `ibInDebug`, the ping cycle is stuck, or `get_targets` shows an active debug state after cleanup, run `force_detach`, then check `get_targets` again.
5. If a temporary YaxUnit test or MCP tool was created, remove it or explicitly leave it only if this has been agreed as a useful test artifact.
6. Check that the scenario did not leave a locked session, a stuck job, or disabled scheduled jobs.
7. In the report, specify: module, procedure, breakpoint lines, target, the way execution was initiated, key values, and the conclusion.

## When it is better not to use

- There is no reproducible scenario for calling the procedure.
- The scenario runs in a production base or risks stopping a user transaction.
- You need to collect a trace across many branches: `agent-debug` with the event log is cheaper.
- The answer from the code, the event log, the technical journal, or a data query is sufficient.

---
depends_on:
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/v8-session-manager/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
