---
name: opus-review
description: Review through a second independent Opus instance. The skill teaches the agent to launch an independent review of artifacts via a Task subagent with an isolated context. Use it when invoking /review-opus, /review-all, or when the user requests an independent check of a plan, specification, code, or architecture with the same model's perspective.
---

# Review through a second independent Opus instance

## Purpose

The skill teaches the agent to obtain an **independent review** of artifacts from a second Opus instance with a clean context. The reviewer does not know the decisions made in the current session, sees only what is passed in the prompt, and reads the project files on its own.

**Principle:** Claude Code (the orchestrator) constructs the task and launches a subagent via the Task tool. The subagent receives an isolated context, reads files with the standard tools (Read, Glob, Grep, Bash), and returns a structured review.

**Why a second Opus finds issues:**
- Each request is an independent context without the current session's "blind spots"
- Generation mode vs. critique mode - different cognitive setups
- The prompt explicitly defines the role of a strict reviewer with concrete criteria

**How it differs from GPT review:**
- No need for external CLI (Codex) - it works directly via the Task tool
- Instant start, no background process or monitoring
- The result is returned synchronously in the Task response
- But: the same model - Opus. GPT catches a different class of problems (different architecture). Opus review and GPT review complement each other.

---

## When to apply

| Trigger | Action |
|---------|--------|
| The user invokes `/review-opus` | Launch the review via a Task subagent |
| The user invokes `/review-all` | Start the Opus review in parallel with GPT |
| The user asks for a "second opinion" | Offer `/review-opus` as a quick option |
| Complex architecture, > 5 files | Recommend a review |
| Before implementing a specification | Suggest reviewing the plan |

---

## Prompt building

The prompt for the reviewer consists of three mandatory blocks. Detailed template: [references/prompt-template.md](references/prompt-template.md).

### Block 1: Role and task

The role must be stated explicitly - this is critically important for the quality of the review:

```
Ты — старший ревьюер 1С BSL с опытом 10+ лет. Ревьюишь независимо от автора.
Находишь реальные проблемы, а не придираешься к мелочам.
Критика конструктивна: не «это плохо», а «это плохо, потому что X, исправь так: Y».

Задача: проведи ревью <тип> — <описание и контекст>.
```

### Block 2: Artifact

The specific object under review. **Always provide file paths - the reviewer reads them on its own. Never paste file contents into the prompt.**

Guidelines by type:

- **Specification** - the path to the specification file plus paths to the source materials (task, analysis)
- **Code** - the rationale for the changes (spec or task) plus a hint to use `git diff --name-only HEAD~1` plus the key files
- **Tests** - the path to the test files/directory plus the module under test plus the rationale (spec)
- **Form** - the path to the form module plus the rationale for the changes
- **Architecture** - the path to the architecture document plus the key implementation files
- **If there are no files** (the artifact is only in chat) - send it as text as an exception

### Block 3: Skills (review criteria)

Paths to `SKILL.md` files that the reviewer will read on its own:

| Artifact type | Skills |
|---------------|--------|
| BSL code | `coding-standards`, `error-handling`, `query-patterns`, `ssl-patterns`, `form-patterns` |
| Specification / plan | `spec-standard` |
| Form (UI) | `form-patterns`, `form-visual-requirements` |
| Architecture | `ssl-patterns`, `query-patterns`, `coding-standards` |
| Tests | `coding-standards`, `error-handling` |

Paths relative to the project root:
```
framework/skills/bsl-practices/<name>/SKILL.md
framework/skills/spec-writing/<name>/SKILL.md
```

### Block 4: Response format (mandatory)

You must explicitly specify the format for the subagent - otherwise the response may become arbitrary:

```
## Формат ответа

Для каждого замечания:
[BLOCK|WARN|INFO] <файл>:<строка> (или <раздел> для спецификаций)
Проблема: <что не так>
Причина: <почему это проблема>
Исправление: <направление исправления>

В конце — сводка:
- Количество BLOCK / WARN / INFO
- Общая оценка: принято | нужны исправления | требуется переработка
- Топ-3 проблемы по приоритету

Если артефакт чистый — скажи «замечаний нет» и не выдумывай проблемы.
```

---

## Calling the subagent

### Command

Use the Task tool with `subagent_type: "general-purpose"`:

```
Task(
  subagent_type: "general-purpose",
  description: "Независимое ревью <тип> через Opus",
  prompt: "<сформированный промпт>"
)
```

### Particularities

- **Synchronous call** - the result is returned directly, no background monitoring is needed
- **Isolated context** - the subagent does not see the history of the current session, only what is in the prompt
- **File access** - the subagent uses the same tools (Read, Glob, Grep, Bash) and sees the whole project
- **Parallel execution** - you can run several Task instances at once (in a single message)

### Example call

```
Task(
  subagent_type: "general-purpose",
  description: "Ревью спецификации резервирования товаров",
  prompt: """
Ты — старший ревьюер 1С BSL с опытом 10+ лет. Ревьюишь независимо от автора.
Находишь реальные проблемы, а не придираешься к мелочам.

Задача: проведи ревью спецификации механизма резервирования товаров.
Контекст: доработка типовой УТ, задача #42.

# Артефакт
Тип: спецификация
Прочитай: docs/specs/SPEC-резервирование-товаров.md
Исходные материалы: docs/tasks/task-42.md

# Навыки (критерии)
Прочитай и используй как критерии:
- framework/skills/spec-writing/spec-standard/SKILL.md

# Формат ответа
[BLOCK|WARN|INFO] <раздел>
Проблема: ...
Причина: ...
Исправление: ...

Сводка: BLOCK/WARN/INFO, общая оценка, топ-3 проблемы.
  """
)
```

---

## Obtaining and presenting the result

The Task tool returns the result synchronously - no monitoring is needed.

1. Wait for the Task to finish
2. Read the returned text - that is the reviewer’s feedback
3. Show it to the user
4. If there are BLOCK findings - strongly suggest fixing them
5. The feedback is an opinion; the final decision is up to the user

---

## Error handling

| Situation | Action |
|----------|--------|
| Task returned an empty result | Retry with a clearer prompt - add "Start the review right now" |
| The subagent couldn’t find the artifact file | Check the path, provide the correct path relative to the project root |
| The subagent writes vague generalities without tying them to the code | Clarify the prompt: "Tie each remark to a specific file and line" |
| The subagent refuses to criticize | Strengthen the role in the prompt: "You must find problems - if there are none, explain why" |

---

## Common mistakes

| Mistake | Consequence | How to avoid it |
|--------|-------------|------------------|
| Not defining the reviewer role | The subagent behaves as an assistant rather than a critic | Always start with the "Role" block |
| Not defining the response format | Arbitrary structure that is hard to read | Always include the "Response format" block |
| Sending the skill contents instead of paths | The prompt balloons | Send only the paths - the subagent reads the files itself |
| Providing the artifact without a task | The subagent lacks context | Always describe the purpose of the review |
| Expecting the subagent to know the session context | The subagent is isolated - it knows nothing beyond the prompt | Include all necessary paths and context explicitly |

---

## Related resources

- [Prompt template](references/prompt-template.md) - a detailed template with examples
- [codex-review skill](../codex-review/SKILL.md) - review through GPT (Codex CLI)
- [Subagent reviewer](../../../subagents/reviewer.md) - the framework’s internal reviewer (a different scenario)

---
depends_on:
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/bsl-practices/error-handling/SKILL.md
  - framework/skills/bsl-practices/query-patterns/SKILL.md
  - framework/skills/bsl-practices/ssl-patterns/SKILL.md
  - framework/skills/bsl-practices/form-patterns/SKILL.md
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
