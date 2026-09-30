# Testing

Use tests when behavior matters. Test commands build first, so do not run a separate `build` unless the user asked specifically for build-only diagnostics.

## WS integration with session-manager for test yaxunit / test va

> SteelMorgan forks used for WS transport are canonical in `SKILL.md`, section "Command Form".

WS flags for `test ...` are the same as for `launch ...`: `--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`. **clap-structure nuance:** on test commands, the flags are declared at the `TestArgs` level (via `flatten(McpClientWsArgs)`), that is **before** the `yaxunit`/`va` subcommand:

```bash
# Правильно — флаги ДО подкоманды
v8-runner test --mcp-transport=ws --mcp-log-level=info yaxunit module <NAME>
v8-runner test --mcp-ws-timeout-ms 5000 va

# Неправильно — clap отвечает "error: unexpected argument"
v8-runner test yaxunit module <NAME> --mcp-transport=ws       # ❌
v8-runner test yaxunit --mcp-transport=ws all                 # ❌
```

Subcommands `test yaxunit ...` / `test yaxunit module ...` / `test va` do not declare their own `McpClientWsArgs`, so `--help` at their level does not show WS options. To see them, use `v8-runner test --help`.

The CLI alternative is `tools.wt_mcp_adapter.*` in `v8project.yaml` (CLI flag priority → yaml → internal defaults); the full yaml example and the default/override table are in `project-workflows.md`.

The `kind` mapping: `test yaxunit ...` → `yaxunit_runner`, `test va ...` → `vanessa_test_client`. It is fixed by the entry point and cannot be overridden from the CLI.

### Diagnosing WS integration in the test phase

If `yaxunit_runner` / `vanessa_test_client` does not appear in the manager's `session_list`:

1. **Manager log** — `/tmp/v8sm/logs/mcp/actions.log` (the path depends on the manager's `workPath`). Look for `WS connection accepted (handshake completed)` in the run window. Start the manager with `--log-level debug` if it is on `info`.
2. **`/C` payload** — start v8-runner with `--log-level=trace` (at the global options level) and check whether `mcpMode=ws;manager_url=...` was appended to `RunUnitTests=...`. If not, transport selection fell back to `mcp`.
3. **Enterprise-1С log** — `<workPath>/temp/yaxunit/runs/<run-id>/enterprise.out.log` and `runner.log`. Look for `[MCP INFO ...] Logging params applied` and `Регистрация провайдера ...` — this is MCP initialization diagnostics from the BSL devkit side.
4. **v8-runner stdout** — the diagnostic block `[MCP INFO ...]` appears in the `diagnostic` section of the `test` output (only when the MCP client initializes successfully).

Resolved (DRIVE 2026-05-11): `yaxunit_runner` was not registering in the manager's `session_list`, although v8-runner was correctly inserting the WS payload into `/C`. The runtime trace showed a race condition in BSL: the idle handler `Мсп_ОтложенныйСтарт_Тик` in `client_mcp` was set with a 1-second interval, and YAXUNIT with `closeAfterTests: true` closed the application in about 1 second (tests ~200ms). The idle handler did not have time to tick. Fix: reduce the interval `1` → `0.1` in `exts/client_mcp/Ext/ManagedApplicationModule.bsl` (call `ПодключитьОбработчикОжидания("Мсп_ОтложенныйСтарт_Тик", 0.1, Истина)`). After the fix, yaxunit-Enterprise registers the WS session (`kind=yaxunit_runner`, tools=24).

The full description (transport, defaults, `/C` payload, JSON output, behavior when the manager is unavailable) is canonical in `project-workflows.md` (section "WS mode with session-manager"); entry points and the VA/UI-MCP workflow are in `SKILL.md`.

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

Use the module run for narrow code changes. Use the full test run before pushing or for broad changes.

## Vanessa Automation

The VA launch config is described in `references/config-and-backends.md`, section “Vanessa Automation in `v8project.yaml`”: `tools.va.epf_path`, `tests.va.params_path`, `tests.va.profile`, `tests.va.profiles.*` and the TestClient profile inside VAParams. Before changing commands, first check exactly these sections.

Run the configured Vanessa Automation profile:

```bash
v8-runner test va
```

If the user points to a specific feature or profile, inspect `tests.va` in `v8project.yaml` before changing the command.

`test va` uses the configured `tests.va.profile`; do not invent ad-hoc feature paths without updating the config or using the wrapper installed in the repository.

`tests.va.fail_fast` defaults to `false`.

When setting `tests.va.profiles.<name>.filter_tags` or `ignore_tags`, as well as when passing `--filter-tag` / `--ignore-tag`, the leading `@` is accepted for user convenience, but the generated `СписокТеговОтбор` and `СписокТеговИсключение` in runtime `VAParams` must be written without that leading `@`.

## VA Debugging and Scenario Writing

Use `launch mcp va` when the goal is interactive debugging of Vanessa Automation, scenario writing, or controlling the VA feature player through wt-mcp-adapter:

```bash
v8-runner launch mcp va
v8-runner launch mcp va --mode thin
v8-runner launch mcp va --mcp-transport ws --manager-url ws://127.0.0.1:4000/sessions
```

This starts the client MCP server in 1С and loads Vanessa Automation from `tools.va`. Prefer it for exploratory work with VA; for configured automated test runs, use `test va`.

Before starting, check the config:

1. `tools.va.epf_path` points to an existing `vanessa-automation.epf`.
2. `tests.va.params_path` points to the VAParams JSON template.
3. `tests.va.profile` exists in `tests.va.profiles`.
4. `infobase.connection` of the active config for `launch mcp va` points to the test manager base. For research/UI workflow, prefer a separate empty file-based infobase; if it does not exist, the environment setup should create/initialize it before launch.
5. VAParams contains the TestClient profile in `ДанныеКлиентовТестирования`; its `Имя` is the future `profileName` for `connect_test_client`, and `ПутьКИнфобазе` is the base of the application under test.

### Exact VA manager → TestClient chain

1. Make sure session-manager responds to `session_list`. If the manager is not running, start it using the `v8-session-manager` skill.

2. Start VA test-manager via `v8-runner launch mcp va` in detached mode if the client must live after the shell command returns:

   In this run, the runner opens the manager database from `infobase.connection`. This can be an empty infobase: it is needed only to start the VA processing and publish the MCP tools. The test infobases are started later according to the TestClient profiles from `VAParams`.

```bash
uid=$(cat /proc/sys/kernel/random/uuid)
setsid nohup v8-runner --no-color --log-level info launch mcp va \
  --mcp-transport ws \
  --manager-url ws://127.0.0.1:4000/sessions \
  --client-uid "$uid" \
  --corr-id "va-$uid" \
  --mcp-log-level info \
  --mcp-ws-timeout-ms 5000 \
  > "/tmp/va-mcp-$uid.log" 2>&1 &
echo $!
```

3. Wait for the live VA manager session:

```json
{"name":"session_list","arguments":{}}
```

Readiness criterion: `kind=vanessa_test_client`, `state=active`, `disconnected_secs_ago=null`, `inflight=0`, and VA tools (`connect_test_client`, `get_form_analysis`) have appeared. The presence of the tool name only in cached `tools/list` does not count as readiness. Normal appearance of the session and VA tools after startup takes 10-90 seconds; poll `session_list` every 5-10 seconds and keep a diagnostic limit of 120 seconds.

4. Before connecting the tested application, check the port of the selected TestClient profile. Take `ПортЗапускаТестКлиента` and, if set, `ДиапазонПортовTestclient` from `ДанныеКлиентовТестирования`. The port must be free on `ИмяКомпьютера`; if it is occupied by an old test-client from this workflow, close it gracefully; if it is occupied by another process, choose another profile/port. Do not call `connect_test_client` if startup is guaranteed to fail because the port is already in use.

5. Connect the tested application. It is started by the VA manager using the profile from `VAParams`; the agent must not separately launch `/TESTCLIENT` for this VA path. `connect_test_client` takes the required `profileName` argument. The profile determines exactly which test infobase to open: see `ДанныеКлиентовТестирования[*].ПутьКИнфобазе`.

```json
{"name":"connect_test_client","arguments":{"profileName":"<test-client-profile-name>"}}
```

If there are multiple live sessions, pass the `session_id` of the VA manager session into every MCP call. A successful launch should yield a real test-client PID in the profile/log of VA, not `0`. After that, the client-side MCP methods of VA are available: form analysis, command interface and form element management, data reading, screenshots, and execution of VA actions.

6. After investigation, close the test-client. `close_test_client` can be called with the same `profileName`; without it, the tool closes the currently connected profile:

```json
{"name":"close_test_client","arguments":{"profileName":"<test-client-profile-name>"}}
```

If the VA manager was started only for investigation, stop it as well by gracefully terminating the client or by targeted termination of the saved PID. Do not leave open TestClient processes before the next launch with the same fixed port.

## launch options during tests

Test commands accept launch-related options such as `--client-mode`, `--c`, `--execute`, `--use-privileged-mode` and the repeatable `--raw-key`.

Use them only when the user needs a specific 1C launch context; otherwise prefer the configured defaults.

## Syntax as validation

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

## Monitoring a Vanessa run (MUST)

| Requirement | Description |
|------------|----------|
| Monitor v8-runner stdout | The agent MUST read v8-runner stdout every **20 seconds** while the test is running. Standard output contains success and failure markers immediately (`[diagnostic]`, `[artifact]`, `ERROR: runtime error: test run reported failures`, etc.) - this is more reliable than ЖР. |
| Abort on error | If a line `ERROR:` appears in stdout (for example `ERROR: runtime error: test run reported failures`) - the agent MUST stop waiting, read `runner.log` + `junit/junit.xml` in the run directory, and proceed to diagnostics. Check ЖР additionally if the primary artifacts are insufficient. |
| Hang detection | If there are no new lines in v8-runner stdout for >60 sec and the `1cv8c.*vanessa-automation` process is still alive - the agent MUST check manager/test-client processes and the primary startup logs, then proceed to Vanessa diagnostics. |
| Correct termination condition | The exit condition for waiting is: `va-status.log` appears (created both on success and on failure) OR the `1cv8c.*vanessa-automation` process disappears OR `ERROR:` appears in stdout. **Do not use only `va-status.json`** - it is created only when the scenario finishes normally; on early failures (step error, client crash) it will not exist, and a blocking wait will hang. |
| Mandatory artifact analysis | After the run the agent MUST check `va-status.json` and `vanessa-execution.log` under `workPath/temp/<runner-id>/runs/<run-id>/`. |
| Mandatory ЖР analysis | After the run the agent MUST check `event-log` if the scenario failed or the run looks suspicious. |
| Post-validation of success | After `va-status == 0` the agent MUST check the logs for completeness: all steps were executed, there are no skipped/not found steps. |
| Explicit classification | On failure the agent MUST return the error class, not just the failure text. |

**False success:** Vanessa considers a run successful even if no step was found or some steps were skipped. The agent MUST detect such cases (number of executed steps > 0, no skipped/not found). |

## Pre-run config check (for v8-runner)

Before starting `v8-runner test va`, the runner agent performs the following procedure:

1. Read `v8project.yaml` → section `tests.va`, active profile (`tests.va.profile` or the one passed via `--profile`).
2. Compare the features path in the profile with the expected `vanessa-tests/features/tasks/<taskID>/`.
3. If there is a mismatch, add a task-dedicated profile `tests.va.profiles.<taskID>` (either in `v8project.yaml` or via `v8project.local.yaml`), and run with it.
4. When working with tags, remember: `filter_tags` / `ignore_tags` are written **without the leading `@`** into `СписокТеговОтбор` / `СписокТеговИсключение`.
5. Record in `{role}-context.md` the profile used and the features directory.

**Why:** the shared profile in the project usually contains a "stuck" path from the last task. Running without a pre-check picks up someone else's directory and spends dozens of minutes running another task's scenarios.

## Result control for long runs

The commands `v8-runner test yaxunit ...` and `v8-runner test va` are long-running. Instead of blindly polling files, use the Monitor tool:

1. Start v8-runner in the background (`Bash run_in_background: true`), redirect stdout to a log file.
2. Subscribe to that file through the **Monitor** tool with a filter for key markers: `ERROR:|passed|Failed:|\\[artifact\\]` - each matched line will arrive as a notification.
3. Stop waiting when **any** of the following conditions is met:
   - For `test va`: `va-status.log` appears (it is created on both success and failure, unlike `va-status.json`) OR the `1cv8c.*vanessa` process exits OR the line `ERROR:` appears in stdout (for example `ERROR: runtime error: test run reported failures`).
   - For `test yaxunit`: `junit/junit.xml` appears OR the process exits OR the line `ERROR:` or `FAIL` appears in stdout.
4. **Do not use `va-status.json` as the sole exit condition.** It is created only when the scenario completes normally; if it fails early, the file is absent and waiting for it will hang forever.
5. After completion: read the run artifacts (`va-status.json` / `junit.xml`), and if it failed, classify the error - see the `vanessa-diagnostics` skill.

## Artifacts

Save artifacts from failed tests in:

```text
workPath/temp/<runner-id>/runs/<run-id>/
```

In final responses, include the command, the pass/fail result, and the artifact path, if there is one.
