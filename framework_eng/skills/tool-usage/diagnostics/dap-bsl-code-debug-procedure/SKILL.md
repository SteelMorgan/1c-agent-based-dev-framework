---
name: dap-bsl-code-debug-procedure
description: "Interactive DAP debugging of a single BSL procedure"
uses_capabilities:
  - debug_bsl_code
---

# Debugging a BSL Procedure via DAP/MCP

Use this skill for focused interactive debugging when static analysis, the event log, screenshots, and temporary logging do not answer the actual execution path or variable values.

## Preconditions

Before starting, the following must be known:

- the URL of the 1С HTTP debug server or a local SSH tunnel;
- the infobase alias;
- the path to the configuration sources;
- the paths to extensions, if the breakpoint is in an extension;
- a reproducible scenario that calls the required procedure;
- a safe window for pausing execution.

If the MCP debugger server is not configured, use the instructions in [`docs/info/mcp-bsl-debugger.md`](../../../../../docs/info/mcp-bsl-debugger.md).

## Basic Cycle

1. Find the procedure and the breakpoint line via `code-navigation` or by reading the module.
2. Connect to the debug server: `attach`.
3. Check the targets: `get_targets`.
4. Load or refresh metadata: `reload_metadata`.
5. Set a breakpoint on a line inside the required procedure: `set_breakpoints`.
6. Only after the breakpoint is set successfully, start the scenario that calls the procedure.
7. Immediately switch to the MCP debugger and poll the stop event: `wait_for_stop` every 5 seconds.
8. For fast code, stop after 30 seconds without a stop event and reconsider the target/breakpoint/scenario. For a heavy operation, explicitly determine the expected duration and a safe waiting limit before starting.
9. Inspect the stack and variables: `get_call_stack`, if the tool is available, then `get_variables`.
10. If needed, evaluate safe expressions: `evaluate`.
11. Execute one or more steps: `step_in`, `step_out`, `continue`.
12. Clear the breakpoint: `clear_breakpoints`.
13. Disconnect: `detach`.

## How to Start Code Execution

The order is always the same: **breakpoint first, then scenario start, then stop-event polling in the debugger**. Do not start the test or client action before the breakpoint is set, otherwise the required section may run before the debugger connects.

After the scenario starts, the agent does not determine a stop from the client "freezing". It immediately switches to the debugger MCP and calls `wait_for_stop` at 5-second intervals:

- fast code: total limit of 30 seconds;
- heavy operation: estimate the duration, possible blocking, and a safe control limit before starting;
- if the limit expires without a stop event: do not wait forever, check the target, the breakpoint line, the loaded metadata, the actual call scenario, and the execution context.

### Client Context via Vanessa

Use this when the code being debugged runs in a form, command, UI handler, or other client context.

1. Start Vanessa/test client in the standard project way.
2. Through `v8-client-session-manager` / `v8-session-manager`, get the list of active sessions and identify the test agent session: `infobase_name`, `ib_session_number`, `session_id`, user, test client flag.
3. In the debugger `get_targets`, select the target corresponding to this session. If the match is not obvious, verify the start time, user, and infobase session number.
4. Set a breakpoint in the client module.
5. Start the Vanessa scenario or the specific step that calls the required handler.
6. Immediately switch to the debugger and poll `wait_for_stop` every 5 seconds until a stop event or the control timeout.
7. After `continue`, return to the Vanessa run and wait for it to finish normally.

### Client Context via MCP Client Control

Use this when it is easier to trigger the scenario by clicking around than by writing or running Vanessa.

1. Start the 1С test client.
2. Connect it to `v8-client-session-manager` / `v8-session-manager`.
3. Find its session through `session_list` and match it with the debugger target.
4. Set a breakpoint.
5. Through the manager's UI tools, open the form, press the command, fill in the field, or perform another action that calls the required client code.
6. After the stop, control execution flow through the debugger MCP, not through the UI tools.

### Server Context via YaxUnit

Use this when the code being debugged runs on the server, in a server-side common module, manager, object, posting, or the server part of a form.

1. Prepare a minimal YaxUnit test that calls the required exported server procedure or the nearest public entry point.
2. If the project already has a tool/runner for calling an arbitrary server method, you can use it instead of a new test.
3. Set a breakpoint before starting the test.
4. Run one specific test, not the entire suite.
5. Immediately switch to `wait_for_stop`; for a fast server method the waiting limit is 30 seconds, for a heavy one - a pre-defined control limit.
6. After `continue`, wait for the test to finish and verify its result.

### Server Context via a Temporary MCP Tool in an Extension

Use this as a last resort when the server method cannot be conveniently called by a test, HTTP request, or an existing runner/tool.

1. In the extension with `mcp_tools`, temporarily add a narrow tool that calls only the required server entry point with controlled parameters.
2. Mark the temporary method as debug-only and do not mix it with the extension's production API.
3. Set a breakpoint.
4. Call the temporary tool through the MCP surface.
5. After debugging, remove the temporary tool, its registration, exported methods, and test data.
6. Verify that no junk methods, temporary commands, debug names, or extra permissions remain in the sources.

### Other Server Triggers

HTTP service, scheduled job, document posting, or background job are acceptable if this is safe and reproducible. For such scenarios, record in advance which target should stop, and do not leave a stopped thread in a transaction.

## Choosing the Breakpoint

Set the breakpoint not "somewhere in the procedure", but on the line that answers the current question:

- procedure entry - verify the call and the arguments;
- line before `If` - verify the variables that affect branching;
- line before the query - verify the query parameters;
- line after the query - verify the size and key fields of the result;
- line before the return - verify the final value.

If the procedure does not stop, check:

- whether this exact module is called, not an extension/override module;
- whether the line matches after metadata is loaded;
- whether there is a target of the required type;
- whether the code is running in another session or a background job.

## Working with Variables

Look only at the values that affect the current hypothesis:

- procedure parameters;
- variables from the current branch condition;
- query parameters;
- query result, but without dumping large tables in full;
- references and objects only through key attributes.

Do not evaluate expressions with side effects. `evaluate` is allowed for reading simple expressions, but not for writing, posting, HTTP calls, changing global state, or starting business operations.

## Step-by-Step Debugging

Use steps sparingly:

- `step_in` - when you need to enter the called procedure and see its arguments/branch;
- `step_out` - when the current procedure is already clear and you need the return result;
- `continue` - when you need to reach the next breakpoint or release the thread;
- `pause` - only if you need to stop an already running target and this is safe for the session.

After each step, record the observation: where execution stopped, which values changed, which hypothesis was confirmed or rejected.

## Completion

Before the final answer or before handing the task over, do the following:

1. `clear_breakpoints` for all set breakpoints.
2. `continue`, if the thread is still stopped and it is safe to release it.
3. `detach`.
4. If `detach` did not work, `attach` returns `ibInDebug`, the ping cycle hung, or `get_targets` shows an active debug state after cleanup - run `force_detach`, then check `get_targets` again.
5. If a temporary YaxUnit test or MCP tool was created - remove it or explicitly keep it only if this was agreed as a useful test artifact.
6. Verify that the scenario did not leave a locked session, a stuck job, or disabled scheduled jobs.
7. In the report, include: module, procedure, breakpoint lines, target, execution trigger method, key values, conclusion.

## When Not to Use

- There is no reproducible scenario for calling the procedure.
- The scenario runs in a production database or risks stopping a user's transaction.
- You need to collect a trace across many branches: `agent-debug` with the event log is cheaper.
- The answer from code, the event log, the technical log, or a data query is sufficient.

---
depends_on:
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/v8-session-manager/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
