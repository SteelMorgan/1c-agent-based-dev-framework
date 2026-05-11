# Testing

Use tests when behavior matters. Test commands build first, so do not run a separate `build` unless the user specifically asked for a build-only diagnosis.

## WS Pairing with session-manager on test yaxunit / test va

WS flags for `test ...` are the same as for `launch ...`: `--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`. **clap subtlety:** on test commands, the flags are declared at the `TestArgs` level (via `flatten(McpClientWsArgs)`), i.e. **before** the `yaxunit`/`va` subcommand:

```bash
# Correct — flags BEFORE the subcommand
v8-runner test --mcp-transport=ws --mcp-log-level=debug yaxunit module <NAME>
v8-runner test --mcp-transport=ws --mcp-ws-timeout-ms 5000 va

# Wrong — clap returns "error: unexpected argument"
v8-runner test yaxunit module <NAME> --mcp-transport=ws       # ❌
v8-runner test yaxunit --mcp-transport=ws all                 # ❌
```

The `test yaxunit ...` / `test yaxunit module ...` / `test va` subcommands do not declare their own `McpClientWsArgs`, so `--help` at their level does not show WS options. To see them, run `v8-runner test --help`.

CLI alternative — `tools.client_mcp.*` in `v8project.yaml`:

```yaml
tools:
  client_mcp:
    transport: auto         # ws | legacy | auto
    manager_url: ws://127.0.0.1:4000/sessions
    log_level: info
    ws_timeout_ms: 1000
```

Priority: CLI flag → yaml → internal defaults.

`kind` mapping: `test yaxunit ...` → `yaxunit_runner`, `test va ...` → `vanessa_test_client`. Fixed by the entry point, not overridable from CLI.

Diagnostics of WS pairing in the test phase

If `yaxunit_runner` / `vanessa_test_client` does not appear in the manager's `session_list`:

1. **Manager log** — `/tmp/v8sm/logs/mcp/actions.log` (path depends on the manager's `workPath`). Look for `WS connection accepted (handshake completed)` in the run window. Start the manager with `--log-level debug` if it is on `info`.
2. **`/C` payload** — start v8-runner with `--log-level=trace` (at the global options level) and check whether `mcpMode=ws;manager_url=...` was appended to `RunUnitTests=...`. If not, `decide_mcp_transport` returned `Legacy`.
3. **1С Enterprise log** — `<workPath>/temp/yaxunit/runs/<run-id>/enterprise.out.log` and `runner.log`. Look for `[MCP INFO ...] Logging params applied` and `provider registration ...` — this is MCP initialization diagnostics from the BSL devkit side.
4. **v8-runner stdout** — the diagnostic block `[MCP INFO ...]` appears in the `diagnostic` section of the `test` output (only when MCP client initialization succeeds).

Resolved (DRIVE 2026-05-11): `yaxunit_runner` was not registered in the manager's `session_list`, although v8-runner was correctly inserting the WS payload into `/C`. The trace showed a race condition in BSL: the idle-handler `Мсп_ОтложенныйСтарт_Тик` in `client_mcp` was scheduled with a 1 second interval, and YAXUNIT with `closeAfterTests: true` closed the application in about 1 second (tests ~200ms). The idle-handler did not have time to tick. Fix: reduce the interval `1` → `0.1` in `exts/client_mcp/Ext/ManagedApplicationModule.bsl` (call `ПодключитьОбработчикОжидания("Мсп_ОтложенныйСтарт_Тик", 0.1, Истина)`). After the fix, yaxunit-Enterprise registers a WS session (`kind=yaxunit_runner`, tools=24).

Full description (transport, defaults, `/C` payload, JSON output, behavior when the manager is unavailable) — in `SKILL.md` (section "WS Pairing Parameters with session-manager") and `project-workflows.md` (section "WS mode with session-manager").

## YaXUnit

All tests:

```bash
v8-runner test yaxunit all
v8-runner test yaxunit --full all
```

Target one module:

```bash
v8-runner test yaxunit module <MODULE_NAME>
v8-runner test yaxunit --full module <MODULE_NAME>
```

Use module-level runs for narrow code changes. Use all tests for pre-push confidence or broad changes.

## Vanessa Automation

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
v8-runner launch mcp va --mcp-port <PORT>
v8-runner launch mcp va --mcp-config <FILE>
```

This starts the client MCP server in 1С and loads Vanessa Automation from `tools.va`. Prefer it for exploratory VA work; use `test va` for the configured automated test run.

## Launch Options During Tests

Test commands accept launch-related options such as `--client-mode`, `--c`, `--execute`, `--use-privileged-mode`, and repeatable `--raw-key`.

Use these only when the user needs a specific 1С launch context; otherwise prefer the configured defaults.

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

## Artifacts

Preserve failed test artifacts under:

```text
workPath/temp/<runner-id>/runs/<run-id>/
```

In final answers, include the command, pass/fail result, and artifact path when present.
