# Config and backends

Study `v8project.yaml` before diagnosing build, syntax, dump, test, and launch behavior.
If `v8project.local.yaml` exists next to it, study it too - it overrides machine-local
settings before CLI overrides.

## Fields we check first

- `basePath`: the root of the 1C sources; if omitted, defaults to the directory containing the main configuration.
- `workPath`: the location of generated state, temporary files, and the workspace.
- `format`: `DESIGNER` or `EDT`.
- `builder`: `DESIGNER` or `IBCMD`.
- `infobase.connection`: often `File=build/ib` for local automation.
- `source-set`: ordered configuration and extension sources.
- `tools.platform.path` or `tools.platform.version`: hints for locating the 1C platform.
- `tools.edt_cli.path`, `version` and `interactive-mode`: hints for locating EDT CLI and execution mode.
- `tests.yaxunit` and `tests.va`: test launcher configuration.
- `tools.client_mcp`, `tools.va` and `tools.enterprise`: hints for launch and client MCP integration.
- `tools.client_mcp.extension`: optional tool extension that prepares `build`; this is not the project's source-set.

## Format and backend rules

- `format=DESIGNER`, `builder=DESIGNER`: supports init, build, extensions, dump, Designer syntax checks, tests, and make/load/artifact scenarios if they are configured.
- `format=DESIGNER`, `builder=IBCMD`: supports init, build, extensions, dump with a limited backend and only file-based infobases.
- `format=EDT`, `builder=DESIGNER`: supports init, build through EDT export into Designer files, EDT syntax checks, extensions, and tests.
- `format=EDT`, `builder=IBCMD`: supports init and build through EDT export into Designer files followed by IBCMD import/apply; requires a file-based infobase.
- `extensions` supports Designer and EDT projects, but only `source-set` entries with extensions are effective.
- `syntax designer-config` and `syntax designer-modules` require Designer format and Designer backend.
- `syntax edt` requires EDT format with Designer backend.
- `dump --mode partial` with IBCMD degrades to incremental dump, and this needs to be mentioned in user summaries.
- `convert` is CLI-only, repo-aware, uses configured `source-set`s, does not use `builder`, and does not require an infobase.
- `load` supports `.cf` and `.cfe` only for `format=DESIGNER`, `builder=DESIGNER`.
- `tools.client_mcp.extension.source` is prepared during `build`, skipped if nothing changed, and updated via `build --full-rebuild`; `.artifact.path` must point to a `.cfe` and in the current implementation requires `builder=DESIGNER`.
- `make` / `artifacts` require `builder=DESIGNER` and publish `.cf`, `.cfe`, `.epf` or `.erf` depending on target/source-set.

## Notes on source-set

`source-set.name` is a stable identity for ordering, diagnostics, runtime contexts, generated directories, and command selection.

Supported values of `source-set.type`:

- `CONFIGURATION`
- `EXTENSION`
- `EXTERNAL_DATA_PROCESSORS`
- `EXTERNAL_REPORTS`

Prefer `--source-set <NAME>` for narrow build, dump, convert, and artifact scenarios when user changes are limited to one configured source-set.

## Config Path

`v8project.yaml` is the default config file name. Use `--config <path>` only when the active project config is not under the default path or the user explicitly asks for that command form.

`v8project.local.yaml` is only an automatic local overlay. It can override only `workPath`,
`infobase.*`, `tools.*`, `tests.*` and `mcp.*`; it must not set `source-set`, `format`, or
`builder`, and it cannot be used as `--config`. `--workdir` takes precedence over both config files.
