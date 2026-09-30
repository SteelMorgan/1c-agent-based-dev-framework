# File and Artifact Workflows

Use these commands when the task is about files, artifacts, publishing, or source-format conversion.

## Dump

`dump` synchronizes the current infobase state back into project files.

```bash
git status --short
v8-runner dump --mode incremental
git diff
```

Supported modes:

```bash
v8-runner dump --mode full
v8-runner dump --mode incremental
v8-runner dump --mode partial --object <TYPE:NAME>
```

Useful selectors:

```bash
v8-runner dump --mode incremental --source-set <NAME>
v8-runner dump --mode incremental --extension <EXTENSION>
```

`partial` requires at least one `--object`. With `builder=IBCMD`, object-scoped partial dump degrades to incremental dump with a warning.

For `format=EDT`, dump uses an internal Designer snapshot in `workPath/designer/<sourceSetName>`, then imports the result into the EDT target.

## Convert

`convert` is repo-aware conversion of files between Designer and EDT source formats.

```bash
v8-runner convert
v8-runner convert --source-set <NAME>
v8-runner convert --output <DIR>
```

This is not an alias for dump:

- does not use the infobase;
- does not use `builder`;
- the direction is derived from the configured `format`;
- without `--output`, results are published to `workPath/convert/out/<sourceSetName>/<designer|edt>/`;
- `--output` is the root of the target, which mirrors `source-set.path` relative to `basePath`.

`convert` is a file-based CLI scenario and does not work through the infobase.

## Load

`load` applies existing `.cf` or `.cfe` artifacts to an infobase.

```bash
v8-runner load --path <FILE>
v8-runner load --path <FILE> --mode merge --settings <FILE>
v8-runner load --path <FILE> --extension <NAME>
```

Rules:

- supported only for `format=DESIGNER`, `builder=DESIGNER`;
- `.cfe` requires `--extension`;
- `--mode merge` requires `--settings`;
- `load --mode update` is rejected by the current command contract.

## Make And Artifacts

`make` and `artifacts` are the same scenario. In examples, prefer `make` unless the user uses the alias themselves.

```bash
v8-runner make --output <TARGET>
v8-runner make --output <TARGET> --source-set <NAME>
v8-runner make --output <TARGET> --extension <NAME>
```

Behavior:

- the main configuration is exported as `.cf`;
- extension export uses `.cfe`;
- external data processors and reports are published as `.epf` / `.erf` in the output directory;
- `builder=DESIGNER` is required.

Full dump and publishing of package/external artifacts use staged publishing with backup/rollback semantics. Incremental and partial dump are non-atomic update modes.
