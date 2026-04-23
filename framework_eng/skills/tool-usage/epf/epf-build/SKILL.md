---
name: epf-build
description: "Build an EPF/ERF from XML sources. Use after making temporary diagnostic tweaks to the parsed external processing."
argument-hint: <SourceFile>
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
---

# /epf-build

Use the Python script from this directory. In Codex/Linux do not use the PowerShell variant from upstream.

## Command

```bash
python3 scripts/epf-build.py -SourceFile "<root-xml>" -OutputFile "<epf>" <параметры подключения>
```

## Connection parameters

- if a ready infobase exists, prefer an explicit connection:
  - file-based: `-InfoBasePath "<path>"`
  - server-based: `-InfoBaseServer "<server>" -InfoBaseRef "<base>"`
- if connection is not specified, the script creates a temporary file infobase with stub metadata
- `-V8Path` is optional; if not specified, the script searches for `1cv8` in `V8_PATH`, `PATH`, `/opt/1cv8/current/1cv8`

## Examples

```bash
python3 scripts/epf-build.py \
  -SourceFile "epf-source/bddrunner/bddRunner.xml" \
  -OutputFile "build/debug/bddRunner-debug.epf"
```

```bash
python3 scripts/epf-build.py \
  -InfoBaseServer "onec-infra" \
  -InfoBaseRef "dssl_drive_ai" \
  -UserName "AgentAI" \
  -Password "AgentAI" \
  -SourceFile "epf-source/bddrunner/bddRunner.xml" \
  -OutputFile "build/debug/bddRunner-debug.epf"
```
