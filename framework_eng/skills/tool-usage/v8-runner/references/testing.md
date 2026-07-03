# Testing

Use tests when behavior matters. Test commands build first, so do not run a separate `build` unless the user specifically asked for build-only diagnostics.

## WS Pairing with session-manager for `test yaxunit` / `test va`

> SteelMorgan forks used for WS transport are the canon in `SKILL.md`, section "Form of command".

WS flags for `test ...` are the same as for `launch ...`: `--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`. **clap subtlety:** on test commands, the flags are declared at the `TestArgs` level (via `flatten(McpClientWsArgs)`), that is, **before** the `yaxunit`/`va` subcommand:

```bash
# Correct — flags BEFORE the subcommand
v8-runner test --mcp-transport=ws --mcp-log-level=debug yaxunit module <NAME>
v8-runner test --mcp-transport=ws --mcp-ws-timeout-ms 5000 va

# Wrong — clap returns "error: unexpected argument"
v8-runner test yaxunit module <NAME> --mcp-transport=ws       # ❌
v8-runner test yaxunit --mcp-transport=ws all                 # ❌
```

The `test yaxunit ...` / `test yaxunit module ...` / `test va` subcommands do not declare their own `McpClientWsArgs`, so `--help` at their level does not show WS options. To see them, run `v8-runner test --help`.

CLI alternative — `tools.client_mcp.*` in `v8project.yaml` (priority: CLI flag → yaml → internal defaults); the full yaml example and the defaults/override table are in `project-workflows.md`.

`kind` mapping: `test yaxunit ...` → `yaxunit_runner`, `test va ...` → `vanessa_test_client`. Fixed by the entry point, not overridable from the CLI.

### Diagnostics of WS Pairing in the Test Phase

If `yaxunit_runner` / `vanessa_test_client` does not appear in the manager's `session_list`:

1. **Manager log** — `/tmp/v8sm/logs/mcp/actions.log` (path depends on the manager's `workPath`). Look for `WS connection accepted (handshake completed)` in the run window. Start the manager with `--log-level debug` if it is set to `info`.
2. **`/C` payload** — start v8-runner with `--log-level=trace` (at the global options level) and check whether `mcpMode=ws;manager_url=...` was appended to `RunUnitTests=...`. If not, transport selection fell back to `mcp`.
3. **1C Enterprise log** — `<workPath>/temp/yaxunit/runs/<run-id>/enterprise.out.log` and `runner.log`. Look for `[MCP INFO ...] Logging params applied` and `provider registration ...` — this is MCP initialization diagnostics from the BSL devkit side.
4. **v8-runner stdout** — the diagnostic block `[MCP INFO ...]` appears in the `diagnostic` section of the `test` output (only when MCP client initialization succeeds).

Resolved (DRIVE 2026-05-11): `yaxunit_runner` was not registered in the manager's `session_list`, although v8-runner was correctly inserting the WS payload into `/C`. The trace log showed a race condition in BSL: the idle handler `Мсп_ОтложенныйСтарт_Тик` in `client_mcp` was scheduled with a 1 second interval, and YAXUNIT with `closeAfterTests: true` closed the application in about 1 second (tests ~200ms). The idle handler did not have time to tick. Fix: reduce the interval `1` → `0.1` in `exts/client_mcp/Ext/ManagedApplicationModule.bsl` (call `ПодключитьОбработчикОжидания("Мсп_ОтложенныйСтарт_Тик", 0.1, Истина)`). After the fix, yaxunit-Enterprise registers a WS session (`kind=yaxunit_runner`, tools=24).

Full description (transport, defaults, `/C` payload, JSON output, behavior when the manager is unavailable) is the canon in `project-workflows.md` (section "WS mode with session-manager"); entry points and the VA/UI-MCP workflow are in `SKILL.md`.

## YaXUnit

All tests:

```bash
v8-runner test yaxunit all
v8-runner test yaxunit --full all
```

One module:

```bash
v8-runner test yaxunit module <MODULE_NAME>
v8-runner test yaxunit --full module <MODULE_NAME>
```

Use module runs for narrow code changes. Use full test runs before pushing or for broad changes.

## Vanessa Automation

The VA startup config is described in `references/config-and-backends.md`, section "Vanessa Automation in `v8project.yaml`": `tools.va.epf_path`, `tests.va.params_path`, `tests.va.profile`, `tests.va.profiles.*`, and the TestClient profile inside VAParams. Before changing commands, check exactly these sections first.

Run the configured Vanessa Automation profile:

```bash
v8-runner test va
```

If the user points to a specific feature or profile, inspect `tests.va` in `v8project.yaml` before changing the command.

`test va` uses the configured `tests.va.profile`; do not invent ad hoc feature paths without updating config or using the installed wrapper in the repository.

`tests.va.fail_fast` defaults to `false`.

When setting `tests.va.profiles.<name>.filter_tags` or `ignore_tags`, as well as when passing `--filter-tag` / `--ignore-tag`, a leading `@` is accepted for user convenience, but the generated `СписокТеговОтбор` and `СписокТеговИсключение` in runtime `VAParams` must be written without this leading `@`.

## Debugging VA and Writing Scenarios

Use `launch mcp va` when the goal is interactive debugging of Vanessa Automation, scenario writing, or controlling the VA feature player through onec-client-mcp-devkit:

```bash
v8-runner launch mcp va
v8-runner launch mcp va --mode thin
v8-runner launch mcp va --mcp-transport ws --manager-url ws://127.0.0.1:4000/sessions
```

This launches the client MCP server in 1С and loads Vanessa Automation from `tools.va`. Prefer it for exploratory VA work; use `test va` for the configured automated test run.

Before starting, check the config:

1. `tools.va.epf_path` points to an existing `vanessa-automation.epf`.
2. `tests.va.params_path` points to the VAParams JSON template.
3. `tests.va.profile` exists in `tests.va.profiles`.
4. The VAParams contains a TestClient profile in `ДанныеКлиентовТестирования`; its `Имя` is the future `profileName` for `connect_test_client`.

### Exact VA manager → TestClient chain

1. Make sure the session-manager responds to `session_list`. If the manager is not running, start it using the `v8-session-manager` skill.

2. Start the VA test-manager via `v8-runner launch mcp va` in detached mode if the client must remain alive after the shell command returns:

```bash
uid=$(cat /proc/sys/kernel/random/uuid)
setsid nohup v8-runner --no-color --log-level debug launch mcp va \
  --mcp-transport ws \
  --manager-url ws://127.0.0.1:4000/sessions \
  --client-uid "$uid" \
  --corr-id "va-$uid" \
  --mcp-log-level debug \
  --mcp-ws-timeout-ms 5000 \
  > "/tmp/va-mcp-$uid.log" 2>&1 &
echo $!
```

3. Wait for the VA manager live session:

```json
{"name":"session_list","arguments":{}}
```

Readiness criterion: `kind=vanessa_test_client`, `state=active`, `disconnected_secs_ago=null`, `inflight=0`, and VA tools have appeared (`connect_test_client`, `get_form_analysis`). Having the tool name only in cached `tools/list` does not count as readiness. Normal session and VA tool appearance after startup takes 10-90 seconds; poll `session_list` every 5-10 seconds and use 120 seconds as the diagnostic limit.

4. Connect the application under test. The VA manager starts it according to the profile from VAParams; the agent must not separately launch `/TESTCLIENT` for this VA path. `connect_test_client` takes the required `profileName` argument:

```json
{"name":"connect_test_client","arguments":{"profileName":"<test-client-profile-name>"}}
```

If there are multiple live sessions, pass the `session_id` of the VA manager session in every MCP call. A successful launch must yield the real test-client PID in the VA profile/log, not `0`. After that, VA client MCP methods are available: form analysis, command interface and form element control, data reading, screenshots, and VA action execution.

5. After the investigation, close the test client. `close_test_client` can be called with the same `profileName`; without it, the tool closes the currently connected profile:

```json
{"name":"close_test_client","arguments":{"profileName":"<test-client-profile-name>"}}
```

If the VA manager was launched only for investigation, stop it as well by normal client termination or by targeted termination of the saved PID. Do not leave open TestClient processes before the next launch on the same fixed port.

## Launch Options During Tests

Test commands accept launch-related options such as `--client-mode`, `--c`, `--execute`, `--use-privileged-mode`, and repeatable `--raw-key`.

Use them only when the user needs a specific 1С launch context; otherwise prefer the configured defaults.

## Syntax as Validation

Designer module syntax:

```bash
v8-runner syntax designer-modules --server --thin-client
```

Designer configuration syntax:

```bash
v8-runner syntax designer-config
```

EDT syntax:

```bash
v8-runner syntax edt
```

## Monitoring Vanessa Runs (MUST)

| Requirement | Description |
|------------|----------|
| Monitoring v8-runner stdout | The agent MUST read v8-runner stdout every **20 seconds** while the test is running. Standard output contains success and failure markers immediately (`[diagnostic]`, `[artifact]`, `ERROR: runtime error: test run reported failures`, etc.) - this is more reliable than the event log. |
| Abort on error | If the string `ERROR:` appears in stdout (for example, `ERROR: runtime error: test run reported failures`) - the agent MUST stop waiting, read `runner.log` + `junit/junit.xml` in the run directory, and switch to diagnostics. Check the event log additionally if the primary artifacts are insufficient. |
| Hang detection | If there are no new lines in v8-runner stdout for more than 60 seconds and the `1cv8c.*vanessa-automation` process is still alive - the agent MUST check the manager/test-client processes and primary startup logs, then switch to Vanessa diagnostics. |
| Correct termination condition | Exit the wait when `va-status.log` appears (created on both success and failure) OR the `1cv8c.*vanessa-automation` process disappears OR `ERROR:` appears in stdout. **Do not use only `va-status.json`** - it is created only when the scenario finishes normally; on early failures (step error, client crash) it will not be there, and the blocking wait will hang. |
| Required artifact analysis | After the run the agent MUST check `va-status.json` and `vanessa-execution.log` under `workPath/temp/<runner-id>/runs/<run-id>/`. |
| Required event-log analysis | After the run the agent MUST check `event-log` if the scenario failed or the run looks suspicious. |
| Post-validation of success | After `va-status == 0`, the agent MUST check the logs for completeness: all steps executed, no skipped/not-found steps. |
| Explicit classification | On failure the agent MUST return an error class, not just the failure text. |

**False success:** Vanessa considers the run successful even if no step was found or some steps were skipped. The agent MUST detect such cases (number of executed steps > 0, no skipped/not-found steps).

## Pre-run Config Check (for v8-runner)

Before starting `v8-runner test va`, the runner agent performs this procedure:

1. Read `v8project.yaml` → the `tests.va` section, active profile (`tests.va.profile` or the one passed via `--profile`).
2. Compare the feature path in the profile with the expected `vanessa-tests/features/tasks/<taskID>/`.
3. If it does not match, add a task-specific `tests.va.profiles.<taskID>` profile (either in `v8project.yaml` or through `v8project.local.yaml`) and run with it.
4. When working with tags, remember: `filter_tags` / `ignore_tags` are written **without a leading `@`** in `СписокТеговОтбор` / `СписокТеговИсключение`.
5. Record the used profile and feature directory in `{role}-context.md`.

**Why:** the shared profile in the project usually contains a stale path from the last task. Running without a pre-check picks up the wrong directory and spends dozens of minutes running another task's scenarios.

## Result Monitoring for Long Runs

The `v8-runner test yaxunit ...` and `v8-runner test va` commands are long-running. Use the Monitor tool instead of blind file polling:

1. Start v8-runner in the background (`Bash run_in_background: true`) and redirect stdout to a log file.
2. Subscribe to that file through the **Monitor** tool with a filter for key markers: `ERROR:|passed|Failed:|\\[artifact\\]` - each matching line will arrive as a notification.
3. Stop waiting when **any** of these conditions is met:
   - For `test va`: `va-status.log` appears (created on success and on failure, unlike `va-status.json`) OR the `1cv8c.*vanessa` process exits OR the stdout contains `ERROR:` (for example, `ERROR: runtime error: test run reported failures`).
   - For `test yaxunit`: `junit/junit.xml` appears OR the process exits OR the stdout contains `ERROR:` or `FAIL`.
4. **Do not use `va-status.json` as the only exit condition.** It is created only when the scenario finishes normally; on early failure the file is absent and waiting for it will hang forever.
5. After completion: read the run artifacts (`va-status.json` / `junit.xml`), and if it failed, classify the error - see the `vanessa-diagnostics` skill.

## Artifacts

Save failed test artifacts in:

```text
workPath/temp/<runner-id>/runs/<run-id>/
```

In final responses, include the command, the pass/fail result, and the artifact path if there is one.
