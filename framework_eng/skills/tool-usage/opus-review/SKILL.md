---
name: opus-review
description: Review through a second independent Opus instance. The skill teaches the agent to launch an independent review of artifacts via a Task subagent with an isolated context. Use when invoking /review-opus, /review-all, or when the user asks for a second opinion on a plan, specification, code, or architecture with the same model.
---

# Review through a second independent Opus instance

## Purpose

The skill trains the agent to obtain an **independent review** of artifacts from a second Opus instance with a clean context. The reviewer does not know about the decisions made in the current session, sees only what is passed in the prompt, and reads the project files on its own.

**Principle:** Claude Code (orchestrator) composes the task and launches a subagent via the Task tool. The subagent receives an isolated context, reads files through standard tools (Read, Glob, Grep, Bash), and returns a structured review.

**Why a second Opus finds bugs:**
- Each request is an independent context without the current session’s “blind spots”
- Generation mode vs critique mode — different cognitive setups
- The prompt explicitly assigns the role of a strict reviewer with concrete criteria

**How it differs from GPT review:**
- Does not require external CLI (Codex) — works directly through the Task tool
- Instant start, no background process or monitoring needed
- Result is returned synchronously in the Task response
- But: the same model — Opus. GPT catches a different class of issues (different architecture). Opus review and GPT review complement each other.

---

## When to apply

| Trigger | Action |
|---------|--------|
| User calls `/review-opus` | Run review via a Task subagent |
| User calls `/review-all` | Run Opus review in parallel with GPT |
| User asks for a “second opinion” | Offer `/review-opus` as a quick option |
| Complex architecture, > 5 files | Recommend a review |
| Before implementing a specification | Suggest reviewing the plan |

---

## Prompt formation

The reviewer prompt consists of three required blocks. Detailed template: [references/prompt-template.md](references/prompt-template.md).

### Block 1: Role and task

The role must be stated explicitly — this is critical for review quality:

```
Ты — старший ревьюер 1С BSL с опытом 10+ лет. Ревьюишь независимо от автора.
Находишь реальные проблемы, а не придираешься к мелочам.
Критика конструктивна: не «это плохо», а «это плохо, потому что X, исправь так: Y». 

Задача: проведи ревью <тип> — <описание и контекст>.
```

### Block 2: Artifact

Specific object under review. **Always pass file paths — the reviewer reads them itself. Never paste file contents into the prompt.**

Rules by type:

- **Specification** — path to the specification file + paths to source materials (task, analysis)
- **Code** — basis for the changes (spec or task) + cue to use `git diff --name-only HEAD~1` + key files
- **Tests** — path to test files/directory + module under test + basis (spec)
- **Form** — path to the form module + basis for the changes
- **Architecture** — path to the architectural document + key implementation files
- **If there are no files** (artifact exists only in chat) — provide text as an exception

### Block 3: Skills (review criteria)

Paths to `SKILL.md` files that the reviewer will read independently:

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

### Block 4: Response format (required)

The subagent must be given an explicit format — otherwise the answer may be arbitrary:

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
  description: "Independent review of <type> via Opus",
  prompt: "<composed prompt>"
)
```

### Particulars

- **Synchronous call** — result is returned directly, no need to monitor a background process
- **Isolated context** — the subagent does not see the history of the current session, only what is in the prompt
- **File access** — the subagent uses the same tools (Read, Glob, Grep, Bash), and sees the entire project
- **Parallel execution** — several Tasks can be started simultaneously (within the same message)

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

## Receiving and presenting the result

The Task tool returns its result synchronously — no monitoring required.

1. Wait for the Task to complete
2. Read the returned text — that is the reviewer’s feedback
3. Show it to the user
4. If there are BLOCK comments — strongly suggest making fixes
5. The review is an opinion; the final decision belongs to the user

---

## Error handling

| Situation | Action |
|----------|--------|
| Task returned an empty result | Retry with a clearer prompt — add "Start the review right now" |
| The subagent could not find the artifact file | Check the path; provide the correct path relative to the project root |
| The subagent writes general statements without referencing the code | Clarify in the prompt: "Tie each comment to a specific file and line" |
| The subagent refuses to criticize | Reinforce the role in the prompt: "You must find problems — if there are none, explain why" |

---

## Common mistakes

| Mistake | Consequence | How to avoid it |
|--------|-------------|-----------------|
| Not setting the reviewer role | Subagent behaves like an assistant instead of a critic | Always start with the "Role" block |
| Not specifying the response format | Arbitrary structure, hard to read | Always include the "Response format" block |
| Passing skill contents instead of paths | Prompt bloating | Pass only paths — the subagent reads the files itself |
| Passing the artifact without the task | Subagent lacks context | Always describe the goal of the review |
| Expecting the subagent to know the session context | Subagent is isolated — it knows nothing besides the prompt | Include all necessary paths and context explicitly |

---

## Related resources

- [Prompt template](references/prompt-template.md) — detailed template with examples
- [Skill codex-review](../codex-review/SKILL.md) — review through GPT (Codex CLI)
- [Subagent reviewer](../../../subagents/reviewer.md) — framework’s internal reviewer (alternative scenario)

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
