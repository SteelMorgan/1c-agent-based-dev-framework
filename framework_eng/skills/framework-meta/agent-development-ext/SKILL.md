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

# Agent Development — 1C BSL Framework Extension

> **Base skill:** `agent-development` (Anthropic).
> First read the base skill — it contains the general principles for creating agents.
> This file adds **only** 1C specifics and adaptation for our framework.

---

## 1. Universal framework agent format

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
- `skill-name` — brief purpose
- `rule-name` — brief purpose

**Your Core Responsibilities:**
1. [Responsibility 1]
2. [Responsibility 2]

**Input:**
- [What the agent receives as input]

**Output:**
- [What the agent produces]

**Protocol:**
1. [Step 1]
2. [Step 2]

**Quality Standards:**
- [Criterion 1]
- [Criterion 2]

**Boundaries:**
- [What the agent does NOT do]
```

The "Skills and Rules" section in the body is needed for Cursor — it ignores `skills` from the frontmatter. Duplicate only the names and a single line describing the purpose.

---

## 2. Framework Roles and Models

### Roles → model mapping table (single source of truth)

This is the **canonical registry** for role → model tier mapping for all 10 framework subagents.
Other files (including `framework/subagents/README.md`, `framework/subagents/orchestrator.md` §2)
refer to this table rather than duplicating their own list - any divergence from it is considered
a synchronization error. Tier is a provider-independent scale (Economy < Mid < High < Premium);
the specific alias/model for each tier is determined by `tools/model-defaults.json` for the selected IDE.

| Role | Tier | readonly | Rationale |
|------|------|----------|-------------|
| explorer | Economy | true | Deterministic work, tools produce exact results |
| analyst | High/Premium | true | Requirements analysis, specification creation |
| architect | High/Premium | true | Technical decisions, trade-offs |
| scenario-author | Mid | false | Converting intent scenarios into `.feature` files according to formalized requirements |
| developer-tests | Mid/High | false | Writing unit tests from the specification (Red phase TDD) |
| scenario-coder | Mid | false | Implementing executable Vanessa steps on top of design contracts |
| developer-code | Mid/High | false | Implementing code, TDD (Green phase) |
| tester | Mid/High | false | Writing and running tests, diagnosing the causes of failures |
| debugger | Premium | false | Investigating bugs at runtime - high cost of an incorrect verdict |
| reviewer | Premium (scope: spec/arch/JSON) / High (scope: code/tests/bdd/bdd-steps) | true | Critical role - evaluating artifacts, tier >= author |

### Reviewer rule

The reviewer's `model` MUST be >= the model of the artifact author. If the author is `sonnet` - the reviewer is `sonnet` or `opus`.

### CLI: model mapping

Defaults are in `tools/model-defaults.json`. During `python tools/install.py`, the user can accept the defaults, choose per-agent (mode `[a]`), or use non-Anthropic models.

---

## 3. Cursor / Claude Code Compatibility

| Field | Claude Code | Cursor |
|------|-------------|--------|
| `name` | ✓ | ✓ |
| `description` | ✓ trigger + examples | ✓ description rules |
| `model` | ✓ alias | ✓ CLI will substitute the specific model |
| `readonly` | — (tools/disallowedTools) | ✓ natively |
| `skills` | ✓ preload | ✗ ignored → duplicate names in body |
| `color` / `tools` | ✓ | ✗ |

Unknown fields are ignored — this is not an error.

---

## 4. 1C BSL Domain: Context for system prompt

When writing a system prompt for 1C agents, keep in mind:

### Key restrictions
- The agent **does NOT create metadata objects** — only code in `.bsl` modules
- BSL is server and client code with compilation directives (`&НаСервере`, `&НаКлиенте`)
- Tools are discovered through MCP (`tools/list`); the "capability" section in the agent file is not needed
- `tool-usage/*` skills describe when and how to use MCP tools
- Standards: MADR 4.0, RFC 2119, YaxUnit, БСП

---

## 5. Framework Agent Creation Checklist

1. [ ] `name` — lowercase, hyphens, 3-50 chars
2. [ ] `description` — trigger conditions + 2-3 `<<example>>` blocks
3. [ ] `model` — alias from the table (haiku/sonnet/opus)
4. [ ] `readonly` — true for read-only roles
5. [ ] `skills` — list of skills for preload
6. [ ] Body — system prompt in second person (You are...)
7. [ ] In body — a "Skills and rules" section with names and purpose
8. [ ] In body — Core Responsibilities, Protocol, Quality Standards, Boundaries
9. [ ] No "Used capability" section — tools via MCP
10. [ ] No separate "Input/Output data" tables — built into body

---
depends_on: []
---
