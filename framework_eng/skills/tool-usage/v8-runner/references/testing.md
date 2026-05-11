# Testing

Use tests when behavior matters. Test commands build first, so do not run a separate `build` unless the user specifically asked for a build-only diagnosis.

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

`test va` uses the configured `tests.va.profile`; do not invent ad hoc feature paths without updating config or using the repo's established wrapper.

`tests.va.fail_fast` defaults to `false`.

When setting `tests.va.profiles.<name>.filter_tags` or `ignore_tags`, or passing `--filter-tag` / `--ignore-tag`, a leading `@` is accepted for user convenience but the generated `СписокТеговОтбор` and `СписокТеговИсключение` in runtime `VAParams` must be written without that leading `@`.

## VA Debugging And Scenario Authoring

Use `launch mcp va` when the goal is interactive Vanessa Automation debugging, scenario writing, or driving the VA feature player through onec-client-mcp-devkit:

```bash
v8-runner launch mcp va
v8-runner launch mcp va --mode thin
v8-runner launch mcp va --mcp-port <PORT>
v8-runner launch mcp va --mcp-config <FILE>
```

This starts the client-side MCP server in 1С and loads Vanessa Automation from `tools.va`. Prefer it for exploratory VA work; use `test va` for the configured automated test run.

## Launch Options During Tests

Test commands accept launch-related options such as `--client-mode`, `--c`, `--execute`, `--use-privileged-mode`, and repeatable `--raw-key`.

Use these only when the user needs a specific 1С launch context; otherwise prefer the configured defaults.

## Syntax As Validation

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
