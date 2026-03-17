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
> This file adds **only** the 1C-specific flavor for the framework.

---

## 1. When to create a new skill

| Trigger | Action |
|---------|--------|
| A new MCP tool appears and the agent does not know WHEN to use it | Create a `tool-usage/` skill |
| A repeated anti-pattern in BSL code is found | Create a `bsl-practices/` skill |
| The agent needs training on a specific configuration | Create a **project-specific** skill (inside the IDE project directory) |
| A standard methodology is needed (SDD, TDD, review) | Create a `spec-writing/` skill or a new subfolder |
| An external skill needs adaptation for the framework | Create a `*-ext/` extension |

**Do NOT create** a skill if:
- The information already exists in an existing skill — extend it instead
- This is a one-off instruction — write a prompt, not a skill
- This is a rule/policy (MUST/SHOULD) — create a rule, not a skill

---

## 2. Framework skill categories

| Category | Directory | Purpose | Examples |
|----------|-----------|---------|----------|
| **BSL practices** | `skills/bsl-practices/` | Coding standards and patterns | `coding-standards`, `query-patterns` |
| **Tool-usage** | `skills/tool-usage/` | When and how to use MCP tools | `syntax-checking`, `metadata-discovery`, `test-execution` |
| **Spec-writing** | `skills/spec-writing/` | Documentation and specification standards | `spec-standard` |
| **Extensions** | `skills/*-ext/` | Extensions of external skills (Anthropic, etc.) | `agent-development-ext`, `skill-creator-ext` |

### Extensions (-ext) — convention

1. The base skill is installed via `npx skills add` (updatable)
2. The extension lives at `<base-name>-ext/SKILL.md` inside `framework/skills/`
3. An extension **only adds to** the base skill; it does not duplicate its content
4. In the extension: `> Read the base skill first: <name>`

---

## 3. Skill creation process

1. **Analysis:** determine the category, check for duplication, identify dependencies
2. **Design:** name the file `kebab-case.md`, target 300 lines (max 500)
3. **Writing** — required sections:
   - YAML frontmatter
   - Title + 1–3 sentences describing the purpose
   - **When to apply** — trigger → action table
   - **Scenarios** — concrete examples
   - **Code samples** — right + wrong with explanation of WHY
4. **Integration:** place the file under `framework/skills/`, refresh skills in agents, run `python tools/install.py --list`

---

## 4. Tool-usage skills — replacing tool-registry

Tool-usage skills are the **sole place** where MCP tools are described in the framework.

### Tool-usage skill structure

```markdown
# [Title] — how to use MCP tools for [task]

## When to apply

| Trigger | Action |
|---------|--------|
| The user asks to check syntax | Call `bsl.checkSyntax(uri)` |
| The agent created/edited .bsl code | Run a syntax check automatically |

## MCP Tools

| Tool | Purpose | Workarounds |
|------|---------|-------------|
| `bsl.checkSyntax` | Syntax checking | If the URI is missing, verify the encoding |

## Scenarios
[Concrete call chains]
```

### Principles

1. **Do not duplicate the MCP tool description** — it comes from MCP (`tools/list`)
2. **Focus on WHEN and WHY**, not parameter details
3. **Workarounds and pitfalls** are the main value
4. **Concrete scenarios** — call chains for common tasks

---

## 5. Skill formats

| Type | Location | Frontmatter | Installation |
|------|----------|-------------|-------------|
| Framework skill | `framework/skills/` | `name`, `description`, `depends_on` | CLI via symlinks |
| IDE skill | `.cursor/skills/` or `.agents/skills/` | `name`, `description` | `npx skills add` |
| Extension (-ext) | `framework/skills/<name>-ext/` | `name: base-name-ext`, `description` | CLI, framework-managed |

---

## 6. Content patterns

**Trigger → Action** (tool-usage): table `| Trigger | Action |`

**Right / Wrong** (bsl-practices): two code blocks + explanation of WHY (what breaks if ignored)

**Workflow with checklist** (methodologies): `- [ ] Step N: ...`

**Conditional workflow** (branches): `Determine the type → branch A / branch B`

---

## 7. Project-specific skills

Placement: `.cursor/skills/` or `.claude/skills/` at the project root. Anthropic SKILL.md format.

**Include:** configuration architecture, local conventions, business rules, critical modules, integrations.

**Do NOT include:** generic BSL standards (leave those in framework skills), secrets, information that ages quickly.

---

## 8. BSL specifics

### Platform limitations
- BSL does not support inheritance — only common modules
- There is no package manager — dependencies are wired through the configuration
- Code is divided by contexts (server/client/external connection)
- Metadata is declarative, code is imperative

---

## 9. New skill checklist

- [ ] YAML frontmatter, name in kebab-case (ASCII)
- [ ] Explains WHY, includes right/wrong examples
- [ ] "When to apply" table (trigger → action)
- [ ] No duplication with existing skills
- [ ] Length ≤ 300 lines (max 500)
- [ ] File under `framework/skills/`, agents updated, `install.py --list` shows the skill

---
depends_on: []
---
