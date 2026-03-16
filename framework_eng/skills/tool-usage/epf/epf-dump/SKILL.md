---
name: epf-dump
description: Dump EPF/ERF into XML sources. Use when you need to quickly get the source code of an external processing or report for analysis and temporary modification.
argument-hint: <EpfFile>
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
---

# /epf-dump

Use the Python script from this directory. On Codex/Linux do not use the PowerShell variant from upstream.

## Command

```bash
python3 scripts/epf-dump.py -InputFile "<epf>" -OutputDir "<out>" <параметры подключения>
```

## Connection parameters

An infobase is required for the dump. Without an infobase, reference types are lost.

- file-based infobase: `-InfoBasePath "<path>"`
- server infobase: `-InfoBaseServer "<server>" -InfoBaseRef "<base>"`
- when needed: `-UserName "<user>" -Password "<pwd>"`
- `-V8Path` is optional; if it is not set, the script searches for `1cv8` in `V8_PATH`, `PATH`, `/opt/1cv8/current/1cv8`

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
