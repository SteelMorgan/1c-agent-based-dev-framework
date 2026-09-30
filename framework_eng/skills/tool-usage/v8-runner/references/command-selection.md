# Command Selection

Choose commands by the user's intent, not by listing the entire CLI surface.

## Initialization

Use when the project has no `v8project.yaml` or generated runtime state:

```bash
v8-runner config init
v8-runner config init --connection "File=build/ib"
v8-runner config init --format edt
v8-runner config init --builder IBCMD
v8-runner init
```

Inspect `v8project.yaml` after `config init` and before commands that create or modify the infobase, workspaces, or source files.

## Build and Recovery

Apply Git-visible source changes to the configured infobase:

```bash
v8-runner build
```

Limit the build to a single configured source set:

```bash
v8-runner build --source-set <NAME>
```

Recover after branch switches, rebase, large object moves, or suspicious incremental state:

```bash
v8-runner build --full-rebuild
```

Use `test` directly when behavior matters; test commands run `build` first.

## Syntax

Designer modules:

```bash
v8-runner build
v8-runner syntax designer-modules --server --thin-client
```

Designer configuration:

```bash
v8-runner build
v8-runner syntax designer-config
```

EDT:

```bash
v8-runner build
v8-runner syntax edt
```

## Tests

All YaXUnit tests:

```bash
v8-runner test yaxunit all
```

Targeted YaXUnit module:

```bash
v8-runner test yaxunit module <MODULE_NAME>
```

Vanessa Automation:

```bash
v8-runner test va
```

Interactive VA debugging and scenario authoring:

```bash
v8-runner launch mcp va
```

## Extensions

Update properties of all configured extensions:

```bash
v8-runner extensions
```

Update selected extension source sets:

```bash
v8-runner extensions --name <SOURCE_SET>
```

## Dump, convert, load, and artifacts

Return infobase changes to Git-visible files:

```bash
git status --short
v8-runner dump --mode incremental
git diff
```

Unload individual objects when the backend supports it:

```bash
v8-runner dump --mode partial --object <TYPE:NAME>
```

Convert configured source sets between Designer and EDT file formats:

```bash
v8-runner convert
v8-runner convert --source-set <NAME>
v8-runner convert --output <DIR>
```

Apply built `.cf` or `.cfe` artifacts:

```bash
v8-runner load --path <FILE>
v8-runner load --path <FILE> --mode merge --settings <FILE>
v8-runner load --path <FILE> --extension <NAME>
```

Export release artifacts or publish external artifacts:

```bash
v8-runner make --output <TARGET>
v8-runner make --output <TARGET> --source-set <NAME>
v8-runner make --output <TARGET> --extension <NAME>
```

`artifacts` is a visible alias for `make`.

## Launch

Launch 1C clients through runner:

```bash
v8-runner launch designer
v8-runner launch thin
v8-runner launch thick
v8-runner launch ordinary
```

Launch wt-mcp-adapter inside 1C without VA:

```bash
v8-runner launch mcp
v8-runner launch mcp --mode thin --mcp-port <PORT>
v8-runner launch mcp --mcp-config <FILE>
```

WS-mode flags (when v8-session-manager is available):

```bash
v8-runner launch mcp --mcp-transport=ws --manager-url ws://127.0.0.1:4000/sessions
v8-runner launch mcp --mcp-transport=mcp                # принудительно локальный HTTP MCP без probe
v8-runner launch mcp --mcp-log-level=info --client-uid <UUID> --corr-id <STR>
```

`--mcp-transport=auto` (default) performs a TCP probe of `manager_url` for 200 ms and selects `ws` on success and `mcp` on failure. The same WS flags also work for `test yaxunit ...` and `test va ...`. See the full WS-mode section in `project-workflows.md`, the internal mapping `kind`, and the `--json-message` output format.
