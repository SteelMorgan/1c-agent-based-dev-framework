---
name: agent-development-ext
description: >
  1C BSL Framework extension for agent-development skill.
  Use together with the base agent-development skill when creating or modifying
  framework agents (analyst, architect, developer, reviewer, tester, explorer).
  Covers: universal agent format (Cursor + Claude Code), model tier mapping,
  framework-specific frontmatter fields, 1C BSL domain context.
---

# Agent Development — 1C BSL Framework Extension

> **Base skill:** `agent-development` (Anthropic).
> Read the base skill first — it contains the general principles for creating agents.
> This file adds **only** 1C-specific details and adapts them for our framework.

---

## 1. Universal Framework Agent Format

A single `.md` file works for both Cursor and Claude Code without transformation.

### Frontmatter

```yaml
---
name: agent-name          # lowercase, hyphens, 3-50 chars
description: >
  One-liner + trigger conditions.
  Use proactively when...

  <<example>>
  Context: ...
  user: "..."
  assistant: "..."
  <<commentary>>...<</commentary>>
  <</example>>

model: sonnet              # haiku | sonnet | opus (алиас, CLI подставит конкретную модель для Cursor)
readonly: true             # true для read-only агентов (analyst, explorer, reviewer)
skills:                    # Claude Code подгрузит автоматически; Cursor проигнорирует
  - spec-standard
  - search-before-write
---
```

### Body (System Prompt)

Written in the second person (`You are...`). Structure:

```markdown
You are [role] specializing in [domain] for 1C:Enterprise (BSL).

**Skills and Rules (for Cursor):**
- `skill-name` — short purpose
- `rule-name` — short purpose

**Your Core Responsibilities:**
1. [Responsibility 1]
2. [Responsibility 2]

**Input:**
- [What the agent receives]

**Output:**
- [What the agent produces]

**Protocol:**
1. [Step 1]
2. [Step 2]

**Quality Standards:**
- [Criterion 1]
- [Criterion 2]

**Boundaries:**
- [What the agent DOES NOT do]
```

The "Skills and Rules" section in the body is needed for Cursor — it ignores the `skills` field in frontmatter. We duplicate only the names and a brief purpose there.

---

## 2. Framework Roles and Models

### Role → Model Mapping Table

| Role | model | readonly | Rationale |
|------|-------|----------|-----------|
| explorer | haiku | true | Deterministic work, tools deliver precise answers |
| analyst | sonnet | true | Requirements analysis and specification writing |
| tester | sonnet | false | Writing and executing tests |
| architect | sonnet | true | Technical decisions and trade-offs |
| developer | sonnet | false | Implementing code, following TDD |
| reviewer | opus | true | Critical role — assessing artifacts, tier ≥ author |

### Reviewer Rule

The reviewer `model` MUST be at least the author’s model. If the author is `sonnet`, the reviewer must be `sonnet` or `opus`.

### CLI: Model Mapping

Defaults live in `tools/model-defaults.json`. When running `python tools/install.py`, the user can accept defaults, pick per-agent (mode `[a]`), or use non-Anthropic models.

---

## 3. Cursor / Claude Code Compatibility

| Field | Claude Code | Cursor |
|------|-------------|--------|
| `name` | ✓ | ✓ |
| `description` | ✓ trigger + examples | ✓ description as the rule text |
| `model` | ✓ alias | ✓ CLI writes the concrete model |
| `readonly` | — (tools/disallowedTools) | ✓ supported natively |
| `skills` | ✓ preloads | ✗ ignored → mirror names in the body |
| `color` / `tools` | ✓ | ✗ |

Unknown fields are simply ignored — not an error.

---

## 4. 1C BSL Domain: Context for the System Prompt

When writing the system prompt for 1C agents, keep these points in mind:

### Key Constraints
- The agent **DOES NOT create metadata objects** — only code in `.bsl` modules.
- BSL spans server and client modules with compilation directives (`&НаСервере`, `&НаКлиенте`).
- Tools are discovered through MCP (`tools/list`); the "capability" section is not needed in the agent file.
- Skills under `tool-usage/*` explain when and how to use specific MCP tools.
- Standards: MADR 4.0, RFC 2119, YaxUnit, БСП.

---

## 5. Framework Agent Creation Checklist

1. [ ] `name` — lowercase, hyphens, 3-50 chars
2. [ ] `description` — trigger conditions + 2-3 `<<example>>` blocks
3. [ ] `model` — alias from the table (haiku/sonnet/opus)
4. [ ] `readonly` — true for read-only roles
5. [ ] `skills` — list of skills to preload
6. [ ] Body — system prompt in second person (You are...)
7. [ ] In the body — a "Skills and Rules" section with names and purposes
8. [ ] In the body — Core Responsibilities, Protocol, Quality Standards, Boundaries
9. [ ] No "capability" section — tools come through MCP
10. [ ] No separate "Input/Output" tables — keep them inline

---
depends_on: []
---
