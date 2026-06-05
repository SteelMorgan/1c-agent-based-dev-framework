---
name: agent-development-ext
description: MUST load together with `agent-development` in GBIG context. Adds the framework's universal agent format (analyst, architect, developer, reviewer, tester, explorer), model tier mapping, and 1C BSL specifics.
---

# Agent Development — 1C BSL Framework Extension

> **Base skill:** `agent-development` (Anthropic).
> First read the base skill — it contains the general principles of creating agents.
> This file adds **only** 1C-specific details and adapts it to our framework.

---

## 1. Universal agent format of the framework

A single `.md` file works in both Cursor and Claude Code without transformation.

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

## 2. Framework roles and models

### Role → Model Mapping Table

| Role | model | readonly | Rationale |
|------|-------|----------|-----------|
| explorer | haiku | true | Deterministic work, tools deliver precise results |
| analyst | sonnet | true | Requirements analysis and specification writing |
| tester | sonnet | false | Writing and executing tests |
| architect | sonnet | true | Technical decisions, trade-offs |
| developer | sonnet | false | Implementing code, TDD |
| reviewer | opus | true | Critical role — evaluating artifacts, tier ≥ author |

### Reviewer Rule

The reviewer's `model` MUST be ≥ the artifact author's model. If the author is `sonnet` — the reviewer is `sonnet` or `opus`.

### CLI: Model Mapping

Defaults are in `tools/model-defaults.json`. When running `python tools/install.py`, the user can accept the defaults, choose per-agent (mode `[a]`), or use non-Anthropic models.

---

## 3. Cursor / Claude Code Compatibility

| Field | Claude Code | Cursor |
|------|-------------|--------|
| `name` | ✓ | ✓ |
| `description` | ✓ trigger + examples | ✓ description rules |
| `model` | ✓ alias | ✓ CLI will substitute the concrete model |
| `readonly` | — (tools/disallowedTools) | ✓ native |
| `skills` | ✓ preload | ✗ ignored → duplicate names in the body |
| `color` / `tools` | ✓ | ✗ |

Unknown fields are ignored — this is not an error.

---

## 4. 1C BSL Domain: Context for the System Prompt

When writing a system prompt for 1C agents, consider:

### Key Constraints
- The agent **DOES NOT create metadata objects** — only code in `.bsl` modules.
- BSL is server and client code with compilation directives (`&НаСервере`, `&НаКлиенте`).
- Tools are discovered through MCP (`tools/list`); the "capability" section in the agent file is not needed.
- Skills `tool-usage/*` describe when and how to use MCP tools.
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
