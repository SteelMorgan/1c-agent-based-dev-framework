---
name: skill-creator-ext
description: MUST load together with `skill-creator` in GBIG context. Adds framework skill categories (bsl-practices, tool-usage, spec-writing), BSL content patterns, and 1c-ai-agent-cli integration.
---

# Skill Creator — 1C BSL Framework Extension

> **Base skill:** `skill-creator` (Anthropic).
> First read the base skill — it contains the general principles for creating skills.
> This file adds **only** the 1C-specific framework details.

---

## 1. When to create a new skill

| Trigger | Action |
|---------|----------|
| A new MCP tool appears, and the agent does not know WHEN to use it | Create a `tool-usage/` skill |
| A recurring anti-pattern is found in BSL code | Create a `bsl-practices/` skill |
| The agent needs to be taught the specifics of a particular configuration | Create a **project-specific** skill (in the project's IDE directory) |
| A standard methodology is needed (SDD, TDD, review) | Create a `spec-writing/` skill or a new subdirectory |
| An external skill needs to be adapted for the framework | Create a `*-ext/` extension |

**DO NOT create** a skill if:
- The information already exists in an existing skill — extend it
- This is a one-off instruction — make a prompt, not a skill
- This is a rule/policy (MUST/SHOULD) — make a rule, not a skill

---

## 2. Framework skill categories

| Category | Directory | Purpose | Examples |
|-----------|---------|------------|---------|
| **BSL practices** | `skills/bsl-practices/` | Coding standards and patterns | `coding-standards`, `query-patterns` |
| **Tool-usage** | `skills/tool-usage/` | When and how to use MCP tools | `syntax-checking`, `platform-data-core`, `test-execution` |
| **Spec-writing** | `skills/spec-writing/` | Documentation and specification standards | `spec-standard` |
| **Extensions** | `skills/*-ext/` | Extensions of external skills (Anthropic and others) | `agent-development-ext`, `skill-creator-ext` |

### Extensions (-ext) — convention

1. The base skill is installed via `npx skills add` (updatable)
2. The extension is `<base-name>-ext/SKILL.md` in `framework/skills/`
3. The extension **only supplements**, it does not duplicate the base skill content
4. In the extension: `> First read the base skill: <name>`

---

## 3. Skill creation process

1. **Analysis:** determine the category, check for duplication, determine dependencies
2. **Design:** name `kebab-case.md`, target limit 300 lines (max 500)
3. **Writing** — required sections:
   - YAML frontmatter
   - Heading + 1-3 sentences describing the purpose
   - **When to apply** — trigger → action table
   - **Scenarios** — concrete examples
   - **Code examples** — correct + incorrect with explanation of WHY
4. **Integration:** file in `framework/skills/`, update `skills` in agents, `python tools/install.py --list`

---

## 4. Tool-usage skills — replacement for tool-registry

Tool-usage skills are the **only place** where MCP tools are described in the framework.

### Tool-usage skill structure

```markdown
# [Name] — how to use MCP tools for [task]

## When to apply

| Trigger | Action |
|---------|--------|
| The user asks to check syntax | Call `bsl.checkSyntax(uri)` |
| The agent wrote/changed .bsl code | Automatically check syntax |

## MCP tools

| Tool | Purpose | Workarounds |
|------------|------------|-------------|
| `bsl.checkSyntax` | Syntax check | If the URI is not found, check encoding |

## Scenarios
[Конкретные цепочки вызовов]
```

### Principles

1. **Do not duplicate the MCP tool description** — it comes from MCP (`tools/list`)
2. **Focus on WHEN and WHY**, not on parameters
3. **Workarounds and pitfalls** are the main value
4. **Concrete scenarios** — call chains for typical tasks

---

## 5. Skill formats

| Type | Location | Frontmatter | Installation |
|-----|-----------|-------------|-----------|
| Framework skill | `framework/skills/` | `name`, `description`, `depends_on` | CLI via symlinks |
| IDE skill | `.cursor/skills/` or `.agents/skills/` | `name`, `description` | `npx skills add` |
| Extension (-ext) | `framework/skills/<name>-ext/` | `name: base-name-ext`, `description` | CLI, part of the framework |

---

## 6. Content patterns

**Trigger → Action** (tool-usage): table `| Trigger | Action |`

**Correct / Incorrect** (bsl-practices): two code blocks + explanation of WHY (the consequence of violating it)

**Workflow with checklist** (methodologies): `- [ ] Step N: ...`

**Conditional workflow** (branches): `Determine type → branch A / branch B`

---

## 7. Project-specific skills

Placement: `.cursor/skills/` or `.claude/skills/` at the project root. Anthropic SKILL.md format.

**Include:** configuration architecture, local conventions, business rules, critical modules, integrations.

**Do NOT include:** general BSL standards (in framework skills), secrets, fast-changing information.

---

## 8. BSL specifics

### Platform limitations
- BSL does not support inheritance — only common modules
- There is no package manager — dependencies are through the configuration
- Code is divided by contexts (server/client/external connection)
- Metadata is declarative, code is imperative

---

## 9. New skill checklist

- [ ] YAML frontmatter, name in kebab-case (ASCII)
- [ ] Explains WHY, has correct/incorrect examples
- [ ] "When to apply" table (trigger → action)
- [ ] No duplication with existing skills
- [ ] Length ≤ 300 lines (max 500)
- [ ] File in `framework/skills/`, agents updated, `install.py --list` shows the skill

---
depends_on: []
---
