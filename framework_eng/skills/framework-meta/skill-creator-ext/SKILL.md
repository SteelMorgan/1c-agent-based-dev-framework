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
> This document adds **only** the 1C-specific framework details.

---

## 1. When to create a new skill

| Trigger | Action |
|---------|--------|
| A new MCP tool appears and the agent does not know WHEN to use it | Create a `tool-usage/` skill |
| A recurring anti-pattern in BSL code is spotted | Create a `bsl-practices/` skill |
| You need to teach the agent the specifics of a particular configuration | Create a **project-specific** skill (in the IDE project directory) |
| A standard methodology is needed (SDD, TDD, review) | Create a `spec-writing/` skill or add a new subdirectory |
| You need to adapt an external skill for the framework | Create a `*-ext/` extension |

**Do NOT create** a skill if:
- The information already exists in another skill — extend that skill instead
- It is a one-off instruction — make a prompt, not a skill
- It is a rule/policy (MUST/SHOULD) — create a rule, not a skill

---

## 2. Framework skill categories

| Category | Directory | Purpose | Examples |
|-----------|---------|---------|---------|
| **BSL practices** | `skills/bsl-practices/` | Coding standards and patterns | `coding-standards`, `query-patterns` |
| **Tool-usage** | `skills/tool-usage/` | When and how to use MCP tools | `syntax-checking`, `metadata-discovery`, `test-execution` |
| **Spec-writing** | `skills/spec-writing/` | Documentation and specification standards | `spec-standard` |
| **Extensions** | `skills/*-ext/` | Extensions of external skills (Anthropic, etc.) | `agent-development-ext`, `skill-creator-ext` |

### Extensions (-ext) — convention

1. The base skill is installed via `npx skills add` (updatable)
2. The extension lives at `<base-name>-ext/SKILL.md` inside `framework/skills/`
3. The extension **only augments**, it does not duplicate the base skill’s content
4. In the extension: `> Read the base skill first: <name>`

---

## 3. Skill creation process

### Analysis

1. Determine the category (see table above)
2. Check whether it duplicates an existing skill
3. Identify dependencies

### Design

- **File name** — `kebab-case.md` (Latin letters, dashes)
- **Size** — target limit **300 lines** (500 is absolute maximum)
- **Format** — see section 5

### Writing

**Mandatory sections:**
- YAML frontmatter
- Header + 1-3 sentences describing the intent
- **When to apply** — table mapping triggers to actions
- **Use cases** — concrete examples
- **Code samples** — right/wrong with explanations of WHY

### Integration

1. Add the file to the appropriate subdirectory under `framework/skills/`
2. Update the `skills` lists in the agents that should consume the skill
3. Run `python tools/install.py --list` to confirm the skill appears

---

## 4. Tool-usage skills — replacing tool-registry

Tool-usage skills are the **only place** where MCP tools are documented in the framework.

### Tool-usage skill structure

```markdown
# [Title] — how to use MCP tools for [task]

## When to apply

| Trigger | Action |
|---------|--------|
| The user asks to check syntax | Call `bsl.checkSyntax(uri)` |
| The agent just wrote/changed .bsl code | Automatically validate the syntax |

## MCP tools

| Tool | Purpose | Workarounds |
|------------|------------|-------------|
| `bsl.checkSyntax` | Syntax validation | If the URI is not found — verify encoding |

## Scenarios
[Concrete call flows]
```

### Principles

1. **Do not duplicate the MCP tool description** — that comes from MCP (`tools/list`)
2. **Focus on WHEN and WHY**, not parameters
3. **Workarounds and pitfalls** are the main value
4. **Concrete scenarios** — sequences of calls for typical tasks

---

## 5. Skill formats

### Framework skill (in `framework/skills/`)

```yaml
---
name: query-patterns
description: Паттерны запросов 1С.
---

---
depends_on: []
---
```

The CLI installs them via symlinks. The agent loads them by name.

### IDE skill (external, in `.cursor/skills/` or `.agents/skills/`)

```yaml
---
name: skill-name
description: >
  Trigger description — when to use this skill.
---
```

Installed through `npx skills add`. Anthropic Agent Skills format.

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

### "Trigger → Action" pattern (tool-usage)

```markdown
| Trigger | Action |
|---------|--------|
| The user asks to validate a query | `validate_query` → `execute_query` |
| You need to inspect an object structure | `list_metadata_objects` → `get_metadata_structure` |
```

### "Right / Wrong" pattern (bsl-practices)

```bsl
// ✅ One query with a filter
Запрос.УстановитьПараметр("МассивСсылок", МассивСсылок);
РезультатЗапроса = Запрос.Выполнить();
```

```bsl
// ❌ Query inside a loop: N database hits instead of one
Для Каждого Ссылка Из МассивСсылок Цикл
    РезультатЗапроса = Запрос.Выполнить(); // Each iteration hits the database
КонецЦикла;
```

**Always explain WHY:**
> Each `Запрос.Выполнить()` is a network call to the DBMS.
> 1000 iterations = 1000 calls. One query with `В (&Массив)` = 1 call.

### "Workflow with checklist" pattern (methodologies)

```markdown
- [ ] Step 1: Read the context and goals
- [ ] Step 2: Check the Requirements (RFC 2119)
- [ ] Step 3: Form the verdict (BLOCK/WARN/INFO)
```

### "Conditional workflow" pattern (branching)

```markdown
1. Determine the error type:
   **Is the test written incorrectly?** → Fix the test
   **Is the issue in the implementation?** → Return to the Developer with details
```

---

## 7. Project-specific skills

Project-specific skills live in the IDE project directory:

| IDE | Directory |
|-----|-----------|
| Cursor | `.cursor/skills/` at the project root |
| Claude Code | `.claude/skills/` at the project root |

**Format:** Anthropic `SKILL.md` (`name`, `description`).

**Include:**
- Configuration architecture (subsystems, relationships)
- Local coding conventions
- Business rules for the domain
- List of critical modules
- Integrations and external systems

**Do NOT include:**
- General BSL standards (those belong in framework skills)
- Secrets, passwords, connection strings
- Information that becomes stale quickly

---

## 8. BSL specifics

### Degrees of freedom for BSL

| Level | When | Example |
|---------|-------|--------|
| **Low** | Concrete ITS rules | Coding standards, naming conventions |
| **Medium** | Preferred pattern | Query formatting, traversal templates |
| **High** | Multiple approaches are valid | Architectural decisions, reviews |

### Terminology

| Term | Description |
|--------|-------------|
| Metadata | Configuration objects (справочники, документы, регистры) |
| Module | A .bsl file with code |
| БСП | Library of standard subsystems |
| EDT | Enterprise Development Tools (1C IDE) |
| YaxUnit | Testing framework for 1C |

### Platform constraints

- BSL does not support inheritance — only common modules
- There is no package manager — dependencies are defined through the configuration
- Code is split by contexts (server/client/external connection)
- Metadata is declarative, code is imperative

---

## 9. New skill checklist

### Content
- [ ] Explains WHY, not just WHAT
- [ ] Code samples: right + wrong
- [ ] "When to apply" table (trigger → action)
- [ ] No duplication with existing skills
- [ ] Length ≤ 300 lines (target), ≤ 500 (maximum)

### Format
- [ ] YAML frontmatter filled
- [ ] File name: kebab-case, Latin letters

### Integration
- [ ] File in the correct subdirectory under `framework/skills/`
- [ ] Agents updated (`skills` in frontmatter)
- [ ] `python tools/install.py --list` shows the skill

---
depends_on: []
---
