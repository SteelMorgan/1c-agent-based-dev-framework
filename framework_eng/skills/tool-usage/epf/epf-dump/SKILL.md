---
name: epf-dump
description: "Parse EPF/ERF into XML source files. Use when you need to quickly get the source code of an external processing object or report for analysis and temporary modification."
argument-hint: <EpfFile>
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
---

# /epf-dump

Use the Python script from this directory. In Codex/Linux, do not use the upstream PowerShell variant.

## Command

```bash
python3 scripts/epf-dump.py -InputFile "<epf>" -OutputDir "<out>" <параметры подключения>
```

## Connection parameters

For dump, the infobase is required. Without the IB, reference types are lost.

- file IB: `-InfoBasePath "<path>"`
- server IB: `-InfoBaseServer "<server>" -InfoBaseRef "<base>"`
- if needed: `-UserName "<user>" -Password "<pwd>"`
- `-V8Path` is optional; if not specified, the script looks for `1cv8` in `V8_PATH`, `PATH`, `/opt/1cv8/current/1cv8`

## Examples

```bash
python3 scripts/epf-dump.py \
  -InfoBasePath "/path/to/ib" \
  -InputFile "build/MyProcessor.epf" \
  -OutputDir "tmp/epf-src"
```

```bash
python3 scripts/epf-dump.py \
  -InfoBaseServer "onec-infra" \
  -InfoBaseRef "dssl_drive_ai" \
  -UserName "AgentAI" \
  -Password "AgentAI" \
  -InputFile "/opt/onescript/2.0.0/lib/add/bddRunner.epf" \
  -OutputDir "epf-source/bddrunner"
```
