---
name: agent-context-protocol
description: Протокол сохранения и восстановления контекста агентов (оркестратор + сабагенты) между запусками.
---

# Протокол контекста агента

> Каждый агент — **и оркестратор, и сабагенты** — MUST сохранять контекст перед завершением и MUST читать его при старте. Оркестратор ведёт `orchestrator-context.md`, сабагенты — `{role}-context.md`.

## Язык документов

Все артефакты задачи MUST быть на **русском языке**: спецификации, тех. дизайн, контексты агентов, отчёты, комментарии в `.feature`-файлах, `final-report.md`. Исключение — идентификаторы кода (имена переменных, модулей, метаданных) остаются как есть.

## Расположение контекстов

Все контекстные файлы агентов хранятся в подкаталоге `.context/` внутри `task_dir`:

```
task_dir/.context/{role}-context.md
```

Агент MUST создать каталог `.context/`, если он ещё не существует (mkdir -p).

## Первый шаг при старте

Каждый агент (оркестратор и сабагенты) MUST как **первый шаг**: проверить `task_dir/.context/{role}-context.md`, прочитать его и продолжить работу, не повторяя выполненные шаги.

| Агент | Файл контекста |
|-------|----------------|
| **orchestrator** | `orchestrator-context.md` |
| analyst | `analyst-context.md` |
| architect | `architect-context.md` |
| scenario-author | `scenario-author-context.md` |
| developer-tests | `developer-tests-context.md` |
| developer-code | `developer-code-context.md` |
| tester | `tester-context.md` |
| reviewer | `reviewer-context-{scope}.md` |

## Последний шаг перед завершением

Каждый агент (оркестратор и сабагенты) MUST записать `task_dir/.context/{role}-context.md` **перед любым завершением**: `completed`, `clarification_needed`, `implementation_error`.

## Структура файла контекста

```markdown
# {Role} Context

## Status
{completed | clarification_needed | implementation_error}

## Completed Steps
- {файлы, инструменты, артефакты — достаточно чтобы не повторять работу}

## Findings
- {модули, паттерны, структуры данных, зависимости}

## Assumptions
- {допущения при неопределённости}

## Pending Questions
- {только при clarification_needed, все вопросы одним блоком}

## User Answers
- {заполняет оркестратор}
```

## Что НЕ включать

- Полное содержимое файлов — только выводы и пути
- Промежуточные рассуждения — только финальные находки
- Информацию из других артефактов `task_dir`

## Механизм resume

`{role}-context.md` — основной механизм. `resume agentId` — оптимизация в рамках одной сессии. При `resume` контекст-файл всё равно MUST быть записан.

---
depends_on: []
---