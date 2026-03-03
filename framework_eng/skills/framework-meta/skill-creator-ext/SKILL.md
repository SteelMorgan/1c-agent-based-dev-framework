---
name: skill-creator-ext
description: >
  1C BSL Framework extension for skill-creator skill.
  Use together with the base skill-creator skill when creating or modifying
  framework skills (bsl-practices, tool-usage, spec-writing, agent-development-ext, etc.).
  Covers: framework skill categories, tool-usage skills replacing tool-registry,
  BSL content patterns, 1c-ai-agent-cli integration, project-specific skills.
---

# Skill Creator — 1C BSL Framework Extension

> **Base skill:** `skill-creator` (Anthropic).
> Read the base skill first — it contains the general principles for creating skills.
> This file adds **only** 1С-specific framework content.

---

## 1. When to create a new skill

| Trigger | Action |
|---------|--------|
| A new MCP tool has appeared and the agent doesn’t know WHEN to use it | Create a `tool-usage/` skill |
| A recurring antipattern in BSL code has been found | Create a `bsl-practices/` skill |
| Need to teach the agent the specifics of a particular configuration | Create a **project-specific** skill (in the IDE project directory) |
| Need a standard methodology (SDD, TDD, reviews) | Create a `spec-writing/` skill or a new subdirectory |
| Need to adapt an external skill to the framework | Create an `*-ext/` extension |

**Do NOT create** a skill if:
- The information already exists in an existing skill — expand it
- This is a one-off instruction — create a prompt, not a skill
- This is a rule/policy (MUST/SHOULD) — create a rule, not a skill

---

## 2. Framework skill categories

| Category | Directory | Purpose | Examples |
|----------|-----------|---------|---------|
| **BSL practices** | `skills/bsl-practices/` | Coding standards and patterns | `coding-standards`, `query-patterns` |
| **Tool-usage** | `skills/tool-usage/` | When and how to use MCP tools | `syntax-checking`, `metadata-discovery`, `test-execution` |
| **Spec-writing** | `skills/spec-writing/` | Documentation and specification standards | `spec-standard` |
| **Extensions** | `skills/*-ext/` | Extensions of external skills (Anthropic, etc.) | `agent-development-ext`, `skill-creator-ext` |

### Extensions (-ext) — convention

1. The base skill is installed via `npx skills add` (updatable)
2. The extension lives at `<base-name>-ext/SKILL.md` under `framework/skills/`
3. The extension **only augments**, it does not duplicate the base skill's content
4. In the extension: `> Read the base skill first: <name>`

---

## 3. Skill creation process

### Analysis

1. Determine the category (see the table above)
2. Check: does it duplicate an existing skill?
3. Identify dependencies

### Design

- **File name** — `kebab-case.md` (Latin letters, hyphens)
- **Size** — target limit **300 lines** (500 is the absolute maximum)
- **Format** — see section 5

### Writing

**Required sections:**
- YAML frontmatter
- Title plus 1-3 sentences describing the purpose
- **When to apply** — table trigger → action
- **Usage scenarios** — concrete examples
- **Code examples** — correct + incorrect with an explanation of WHY

### Integration

1. Add the file to the appropriate `framework/skills/` subdirectory
2. Update `skills` in the agents that should use the skill
3. `python tools/1c-ai-agent-cli.py --list` — verify that the skill appears

---

## 4. Tool-usage skills — replacing the tool-registry

Tool-usage skills are the **only place** where MCP tools are documented in the framework.

### Structure of a tool-usage skill

```markdown
# [Название] — как использовать MCP-инструменты для [задачи]

## Когда применять

| Триггер | Действие |
|---------|----------|
| Пользователь просит проверить синтаксис | Вызвать `bsl.checkSyntax(uri)` |
| Агент написал/изменил .bsl код | Автоматически проверить синтаксис |

## MCP-инструменты

| Инструмент | Назначение | Workarounds |
|------------|------------|-------------|
| `bsl.checkSyntax` | Проверка синтаксиса | Если URI не найден — проверить encoding |

## Сценарии
[Конкретные цепочки вызовов]
```

### Principles

1. **Do not duplicate the MCP tool description** — it comes from MCP (`tools/list`)
2. **Focus on WHEN and WHY**, not on parameters
3. **Workarounds and pitfalls** are the main value
4. **Concrete scenarios** — chains of calls for typical tasks

---

## 5. Skill formats

### Framework skill (in `framework/skills/`)

```yaml
---
name: query-patterns
description: 1С query patterns.
---

---
depends_on: []
---
```

The CLI installs via symlinks. The agent loads by name.

### IDE skill (external, in `.cursor/skills/` or `.agents/skills/`)

```yaml
---
name: skill-name
description: >
  Trigger description — when to use this skill.
---
```

Installed via `npx skills add`. Anthropic Agent Skills format.

### Extension (-ext)

```yaml
---
name: base-name-ext
description: >
  1C BSL Framework extension for <base-name> skill.
  Use together with base <base-name> skill when...
---
```

`framework/skills/<name>-ext/SKILL.md`. IDE skill format, part of the framework.

---

## 6. Content patterns

### Pattern “Trigger → Action” (tool-usage)

```markdown
| Триггер | Действие |
|---------|----------|
| Пользователь просит проверить запрос | `validate_query` → `execute_query` |
| Нужно исследовать структуру объекта | `list_metadata_objects` → `get_metadata_structure` |
```

### Pattern “Right / Wrong” (bsl-practices)

```bsl
// ✅ Один запрос с условием
Запрос.УстановитьПараметр("МассивСсылок", МассивСсылок);
РезультатЗапроса = Запрос.Выполнить();
```

```bsl
// ❌ Запрос в цикле: N обращений к СУБД вместо одного
Для Каждого Ссылка Из МассивСсылок Цикл
    РезультатЗапроса = Запрос.Выполнить(); // Каждая итерация = поход в базу
КонецЦикла;
```

**Always explain WHY:**
> Each `Запрос.Выполнить()` is a call to the DBMS over the network.
> 1000 iterations = 1000 calls. One request with `В (&Массив)` equals 1 call.

### Pattern “Workflow with checklist” (methodologies)

```markdown
- [ ] Шаг 1: Прочитать контекст и цели
- [ ] Шаг 2: Проверить Requirements (RFC 2119)
- [ ] Шаг 3: Сформировать вердикт (BLOCK/WARN/INFO)
```

---

## 7. Project-specific skills

Project-specific skills are placed in the IDE project directory:

| IDE | Directory |
|-----|-----------|
| Cursor | `.cursor/skills/` in the project root |
| Claude Code | `.claude/skills/` in the project root |

**Format:** Anthropic SKILL.md (`name`, `description`).

**Include:**
- Configuration architecture (subsystems, relationships)
- Local coding conventions
- Domain business rules
- List of critical modules
- Integrations and external systems

**Do not include:**
- General BSL standards (those belong in framework skills)
- Secrets, passwords, connection strings
- Fast-aging information

---

## 8. BSL specifics

### Degrees of freedom for BSL

| Level | When | Example |
|-------|------|---------|
| **Low** | Specific ИТС rules | Coding standards, naming |
| **Medium** | Preferred pattern | Query formats, traversal templates |
| **High** | Multiple approaches are valid | Architectural decisions, reviews |

### Terminology

| Term | Description |
|------|-------------|
| Metadata | Configuration objects (справочники, документы, регистры) |
| Module | A .bsl file with code |
| BSP | Library of standard subsystems |
| EDT | Enterprise Development Tools (IDE from 1С) |
| YaxUnit | Test framework for 1С |

### Platform constraints

- BSL does not support inheritance — only common modules
- No package manager — dependencies are configured through the configuration
- Code is split by contexts (server/client/external connection)
- Metadata is declarative, code is imperative

---

## 9. New skill checklist

### Content
- [ ] Explains WHY, not just WHAT
- [ ] Code examples: correct + incorrect
- [ ] Table “When to apply” (trigger → action)
- [ ] No duplication with existing skills
- [ ] Size ≤ 300 lines (target), ≤ 500 (maximum)

### Format
- [ ] YAML frontmatter is filled
- [ ] File name: kebab-case, Latin letters

### Integration
- [ ] File is in the correct `framework/skills/` subdirectory
- [ ] Agents are updated (`skills` in the frontmatter)
- [ ] `python tools/1c-ai-agent-cli.py --list` shows the skill

---
depends_on: []
---
