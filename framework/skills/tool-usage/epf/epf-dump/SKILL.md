---
name: epf-dump
description: Разобрать EPF/ERF в XML-исходники. Используй, когда нужно быстро получить исходный код внешней обработки или отчета для анализа и временной модификации.
argument-hint: <EpfFile>
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
---

# /epf-dump

Используй Python-скрипт из этой директории. В Codex/Linux не используй PowerShell-вариант из upstream.

## Команда

```bash
python3 scripts/epf-dump.py -InputFile "<epf>" -OutputDir "<out>" <параметры подключения>
```

## Параметры подключения

Для dump база обязательна. Без ИБ ссылочные типы теряются.

- файловая ИБ: `-InfoBasePath "<path>"`
- серверная ИБ: `-InfoBaseServer "<server>" -InfoBaseRef "<base>"`
- при необходимости: `-UserName "<user>" -Password "<pwd>"`
- `-V8Path` опционален; если не задан, скрипт ищет `1cv8` в `V8_PATH`, `PATH`, `/opt/1cv8/current/1cv8`

## Примеры

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
