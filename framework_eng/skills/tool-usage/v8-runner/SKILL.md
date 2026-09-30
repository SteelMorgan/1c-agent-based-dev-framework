---
name: v8-runner
description: "v8-runner: databases, build, checks, tests, 1C clients"
provides_capabilities:
  - build_project
  - full_rebuild_project
  - init_infobase
  - config_init
  - syntax_check_designer_modules
  - syntax_check_designer_config
  - syntax_check_edt
  - run_yaxunit
  - run_vanessa
  - dump_config
  - load_artifact
  - make_artifacts
  - convert_sources
  - launch_designer
  - launch_thin_client
  - launch_mcp_client
  - extensions_update
---

# v8-runner

Entry point for selecting `v8-runner` commands. Load only the needed reference:

| Need | Read |
|---|---|
| Select a command | `references/command-selection.md` |
| Create/validate `v8project.yaml` | `references/bootstrap.md`, `references/config-and-backends.md` |
| Build, syntax, dump, run, source synchronization | `references/project-workflows.md` |
| Dump, convert, load, make/artifacts | `references/file-and-artifact-workflows.md` |
| YaXUnit, Vanessa, test monitoring | `references/testing.md` |
| path/auth/license errors | `references/auth-guard.md`, `references/troubleshooting.md` |
| Headless `.epf` launch via `/Execute` | `references/headless-epf.md` |

## Command Form

The canonical form is the command from `PATH`:

```bash
v8-runner <command>
```

Do not hardcode an absolute path. In the container, `/usr/local/bin/v8-runner` is expected; path problem checks start with `command -v v8-runner`. If the command is not found, stop and report the environment problem. The legacy fallback `tools/external/v8-runner/v8-runner` is allowed only for older environments and does not become the new canonical form.

`v8project.yaml` is the default config. `v8project.local.yaml` is picked up automatically for local paths, credentials, and MCP settings; do not pass it via `--config`.

Enable JSON output only for machine processing:

```bash
v8-runner --json-message build
```

Common global flags: `--config`, `--json-message`, `--workdir`, `--clean-before-execution`, `--log-level`, `--no-color`.

## First pass

1. Check for `v8project.yaml`.
2. If it is missing, choose the minimal `v8-runner config init ...` according to `references/bootstrap.md`.
3. Before mutating commands, read the generated config.
4. Run `v8-runner init` only to create a file IB or EDT workspace.
5. Validate with the minimal command that matches the goal.

## Routing

| Situation | Command / reference |
|---|---|
| Sources changed, IB is stale | `v8-runner build` |
| Suspicious incremental state, branch switch, large moves | `v8-runner build --full-rebuild` |
| Single source-set | Command with `--source-set <NAME>` |
| Syntax | `syntax designer-modules`, `syntax designer-config` or `syntax edt` according to `format`/`builder` |
| YaXUnit / Vanessa | `v8-runner test ...`, details in `references/testing.md` |
| Extension properties | `v8-runner extensions [--name <SOURCE_SET>]` |
| IB → Git-visible files | `git status`, then `v8-runner dump ...` |
| Convert/load/make | `references/file-and-artifact-workflows.md` |
| UI/VA/MCP launch | `references/project-workflows.md` and `references/testing.md` |

## WS and `v8sm`

`v8-runner` only launches/connects 1С clients. The WS session manager is not started by `v8-runner`: in the container it is usually auto-started and available via `v8sm`. Before starting WS, check the manager endpoint / `session_list`; if there is no manager, use the `v8-session-manager` skill.

WS flags (`--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`) are available for `launch ...` and `test ...`. On `test`, they are set before the subcommand:

```bash
v8-runner test --mcp-transport=ws yaxunit module <NAME>
```

The full transport/probe contract, `/C` payload, `kind` mapping, and JSON output are in `references/project-workflows.md`; testing nuances and readiness loops are in `references/testing.md`.

## 1C client lifecycle

For processes that must keep running after the shell command returns:

1. Start them with the standard `v8-runner launch ...` command.
2. If the environment cleans up child processes, use a detached mechanism (`setsid`, `nohup`, service/job runner), save the PID and the log.
3. Check readiness through external state: live manager session, MCP-tools, 1C window, file log, ЖР.
4. Terminate explicitly: a shutdown tool, the client command, or `kill <PID>` only for the saved PID.

After the launch command, give the client the standard time to start: 10-20 seconds before the first check is a normal interval, not a sign of a hang. If the process/window/1C session has appeared, but the required MCP-tools are not there yet, continue periodic checks for tool registration for up to 120 seconds: the extension may register the surface later than the session itself appears. End the wait early as soon as the required live session and tools are visible; if the limit expires, diagnose the client launch, WS connection, and extension registration through `v8-session-manager`.

`sleep` is allowed only as a short wait in a readiness loop, not as the owner of the lifecycle.

## Guardrails

- Before any operation that accesses the infobase, apply `auth-guard`.
- Do not delete or recreate the infobase, workspace, temporary directory, or generated state without an explicit request or a documented recovery path.
- Do not invent raw `1cv8`, `ibcmd`, `1cedtcli` flags; prefer the `v8-runner` surface.
- Before `dump`, check `git status`.
- Do not clear artifacts from failed tests before diagnosis.
- Separate in the report: source failure, command/config failure, 1C environment failure, test failure, and the path to artifacts.
