# Diagnostics

Distinguish project source failures from failures in the local environment or runner installation.

## Initial checks

```bash
git status --short
test -f v8project.yaml
```

Inspect the `v8project.yaml` fields that affect the failing command:

- `format`
- `builder`
- `connection`
- `basePath`
- `workPath`
- `source-set`
- `tools.platform`
- `tools.edt_cli`
- `tests`

## Typical situations

The absence of the 1С platform, EDT CLI, IBCMD, or test launcher utilities is an environment/installation problem. Report the missing tool and the config fields used to locate it.

Stale incremental state after switching branches, rebasing, or large source moves usually requires:

```bash
v8-runner build --full-rebuild
```

A partial dump with IBCMD degrades to an incremental dump. Mention this in the summary and check the final Git diff.

Do not clean up failed run directories until diagnostics are complete. Failure artifacts must remain in:

```text
workPath/temp/<runner-id>/runs/<run-id>/
```

## Runtime directories

Useful locations inside `workPath`:

- `workPath/hash-storages/`: persistent change-detection state.
- `workPath/edt-workspace/`: shared EDT workspace for `init`.
- `workPath/convert/edt-workspace/`: separate EDT workspace for `convert`.
- `workPath/designer/<sourceSetName>/`: generated Designer representation, especially for EDT scenarios.
- `workPath/logs/platform/`: platform logs.
- `workPath/temp/`: temporary run artifacts and diagnostics.
