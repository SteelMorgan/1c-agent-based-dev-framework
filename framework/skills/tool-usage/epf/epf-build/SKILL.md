---
name: epf-build
description: Собрать EPF/ERF из XML-исходников. Используй после внесения временных диагностических правок в разобранную обработку.
argument-hint: <SourceFile>
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
---

# /epf-build

Используй Python-скрипт из этой директории. В Codex/Linux не используй PowerShell-вариант из upstream.

## Команда

```bash
python3 scripts/epf-build.py -SourceFile "<root-xml>" -OutputFile "<epf>" <параметры подключения>
```

## Параметры подключения

- если есть готовая ИБ, предпочитай явное подключение:
  - файловая: `-InfoBasePath "<path>"`
  - серверная: `-InfoBaseServer "<server>" -InfoBaseRef "<base>"`
- если подключение не задано, скрипт создает временную файловую ИБ со stub-метаданными
- `-V8Path` опционален; если не задан, скрипт ищет `1cv8` в `V8_PATH`, `PATH`, `/opt/1cv8/current/1cv8`

## Примеры

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
