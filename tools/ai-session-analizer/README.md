# ai-session-analizer

Локальный анализатор сессий `Claude Code` и `Codex`.

Инструмент читает локальные логи из:
- `~/.claude/projects`
- `~/.codex/sessions`

И нормализует их в общую модель:
- `provider` — `claude` или `codex`
- `working_directory` — рабочий каталог сессии
- `task_id` — номер задачи, извлечённый из текста стартового промпта
- `task_dir` — путь к задаче, если он был в промпте
- `session_id`
- `parent_session_id`
- `agent_type`
- `agent_label`
- `git_branch`
- `step_type`
- токены: `input/output/cache`

## Step Types

Текущая эвристическая классификация шагов:

- `vanessa_log_analysis` — работа с Vanessa Automation, feature-файлами, отчётами и артефактами
- `screenshot_analysis` — анализ скриншотов и изображений
- `code_reading` — чтение кода, grep, diff, навигация, read-only команды
- `code_writing` — edit/apply_patch/write и другие сигналы модификации кода
- `test_execution` — запуск тестов и синтаксических проверок
- `review` — ревью-итерации и reviewer-профили
- `planning` — дизайн, планирование, спецификации
- `other`

Классификация не претендует на абсолютную точность. Это рабочий слой эвристик, который удобно улучшать под вашу практику.

## Запуск

```bash
python3 tools/ai-session-analizer/analyzer.py build
python3 tools/ai-session-analizer/analyzer.py serve --port 8765
```

После `serve` UI доступен по адресу:

```text
http://127.0.0.1:8765
```

## Основные фильтры в UI

- `provider`
- `working_directory`
- `task_id`
- `agent_type`
- `step_type`

Это позволяет:
- разделять разные 1С-проекты по `working_directory`
- смотреть затраты по конкретной задаче через `task_id`
- отделять main/subagent и agent profiles
- видеть самые дорогие типы шагов

## Замечания по данным

- `Claude`:
  - `agentType` берётся из `subagents/*.meta.json`
  - `task_id` и `task_dir` обычно извлекаются из первого prompt сабагента
- `Codex`:
  - `git.branch` берётся из `session_meta.payload.git.branch`
  - для сабагентов есть связь с родителем через `parent_thread_id`
  - `task_id` и `task_dir` извлекаются из стартового текста, так как отдельного структурного поля обычно нет

## Дальнейшие улучшения

- добавить пользовательский словарь классификации шагов
- сохранять dataset в SQLite/DuckDB
- строить timeline по task/session/agent
- добавить сравнение main agent vs subagents
