---
name: opus-review
description: Independent review via a second Opus instance. The skill teaches the agent to launch an independent artifact review through the Task subagent with an isolated context. Use when invoking /review-opus, /review-all, or when the user asks to check a plan, specification, code, or architecture with another independent view from the same model.
---

# Review via a second independent Opus instance

Independent review through the Task tool (a subagent with an isolated context). The result is synchronous—no monitoring required. The difference from `codex-review`: it does not require the CLI, starts instantly, but uses the same model—Opus and GPT complement each other.

---

## When to apply

| Trigger | Action |
|---------|--------|
| The user invokes `/review-opus` | Launch review through the Task subagent |
| The user invokes `/review-all` | Run Opus review in parallel with GPT |
| The user asks for a “second opinion” | Suggest `/review-opus` as a quick option |
| Complex architecture, more than 5 files | Recommend a review |
| Before implementing a specification | Offer a plan review |

---

## Prompt construction

The prompt for the reviewer consists of three mandatory blocks. Detailed template: [references/prompt-template.md](references/prompt-template.md).

### Block 1: Role and task

The role must be stated explicitly—this is critical for review quality:

```
Ты — старший ревьюер 1С BSL с опытом 10+ лет. Ревьюишь независимо от автора.
Находишь реальные проблемы, а не придираешься к мелочам.
Критика конструктивна: не «это плохо», а «это плохо, потому что X, исправь так: Y».

Задача: проведи ревью <тип> — <описание и контекст>.
```

### Block 2: Artifact

The specific object under review. **Always pass file paths—the reviewer reads them independently. Never insert file contents into the prompt.**

Rules by type:

- **Specification** — path to the specification file + paths to source materials (task, analysis)
- **Code** — the basis of changes (spec or task) + a hint to use `git diff --name-only HEAD~1` + key files
- **Tests** — path to the test files/directory + the module under test + the basis (spec)
- **Form** — path to the form module + the basis of changes
- **Architecture** — path to the architectural document + key implementation files
- **If there are no files** (artifact only in chat) — provide the text as an exception

### Block 3: Skills (review criteria)

Paths to the `SKILL.md` files that the reviewer will read themselves:

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

You must explicitly specify the format for the subagent—otherwise the reply can be arbitrary:

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

## Subagent invocation

### Command

Use the Task tool with `subagent_type: "general-purpose"`:

```
Task(
  subagent_type: "general-purpose",
  description: "Independent review <тип> via Opus",
  prompt: "<constructed prompt>"
)
```

### Nuances

- **Synchronous call** — the result is returned directly, no background monitoring is needed
- **Isolated context** — the subagent does not see the current session history, only what is in the prompt
- **File access** — the subagent uses the same tools (Read, Glob, Grep, Bash) and sees the entire project
- **Parallel execution** — you can launch multiple Tasks simultaneously (in one message)

### Invocation example

```
Task(
  subagent_type: "general-purpose",
  description: "Review of the goods reservation specification",
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

## Receiving the result

Task returns the feedback synchronously. Show it to the user. If there are BLOCK remarks—suggest fixing them. Final decision is up to the user.

---

## Error handling

| Situation | Action |
|----------|--------|
| Task returned an empty result | Repeat with a clearer prompt—add “Start the review right now” |
| The subagent could not find the artifact file | Check the path, provide the correct path relative to the project root |
| The subagent writes general statements without tying to code | Clarify the prompt: “Tie every remark to a specific file and line” |
| The subagent refuses to criticize | Strengthen the role in the prompt: “You must find problems—if there are none, explain why” |

---

## Typical mistakes

| Mistake | Consequence |
|--------|-------------|
| No reviewer role in the prompt | The subagent behaves like an assistant instead of a critic |
| No “Response format” block | Arbitrary structure |
| Skill contents instead of paths | Bloating the prompt—pass only paths |
| Artifact without task description | The subagent does not understand the context |
| Expecting session context | The subagent is isolated—include all paths explicitly |

---

Prompt template: `references/prompt-template.md`. GPT review: `codex-review`.

---
depends_on:
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/bsl-practices/error-handling/SKILL.md
  - framework/skills/bsl-practices/query-patterns/SKILL.md
  - framework/skills/bsl-practices/ssl-patterns/SKILL.md
  - framework/skills/bsl-practices/form-patterns/SKILL.md
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
