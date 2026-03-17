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

- **Base skill:** `agent-development` (Anthropic).
> Read the base skill first — it contains general principles for creating agents.
> This file adds **only** the 1C-specific aspects and adapts them for our framework.

---

## 1. Universal Framework Agent Format

One `.md` file works in both Cursor and Claude Code without transformation.

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

model: sonnet              # haiku | sonnet | opus (alias, CLI will substitute specific model for Cursor)
readonly: true             # true for read-only agents (analyst, explorer, reviewer)
skills:                    # Claude Code will preload automatically; Cursor ignores
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
- `rule-name` — short description

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

### Why include the "Skills and Rules" section in the body?

Claude Code loads skills from frontmatter automatically. Cursor ignores the `skills` field entirely, so we duplicate **only the names and one-line descriptions** in the body so the Cursor agent knows which skills are relevant and can locate them.

---

## 2. Framework Roles and Models

### Role → Model Mapping Table

| Role | model | readonly | Rationale |
|------|-------|----------|-----------|
| explorer | haiku | true | Deterministic work, tools provide precise answers |
| analyst | sonnet | true | Requirements analysis, specification writing |
| tester | sonnet | false | Writing and running tests |
| architect | sonnet | true | Technical decisions, trade-offs |
| developer | sonnet | false | Implementing code, TDD |
| reviewer | opus | true | Critical role — evaluating artifacts, tier ≥ author |

### Reviewer Rule

The reviewer `model` MUST be ≥ the author’s model. If the author uses `sonnet`, the reviewer must be `sonnet` or `opus`.

### CLI: Interactive Model Selection

When running `python tools/install.py` the user selects a specific model for each agent (or accepts the defaults).

**Defaults** live in `tools/model-defaults.json` — per IDE:

```json
{
  "cursor": {
    "haiku":  "claude-4.5-haiku",
    "sonnet": "claude-4.5-sonnet-thinking",
    "opus":   "claude-4.6-opus-high-thinking"
  }
}
```

The user can:
- Edit `model-defaults.json` for their own set of models
- Choose a model per agent during installation (mode `[a]`)
- Use non-Anthropic models (gpt, gemini, grok — anything the IDE exposes)

---

## 3. Cursor vs Claude Code Format Details

| Field | Claude Code | Cursor |
|-------|-------------|--------|
| `name` | ✓ agent identifier | ✓ rule identifier |
| `description` | ✓ trigger description with examples | ✓ used as the rule `description` |
| `model` | ✓ native: haiku/sonnet/opus | ✓ CLI writes the concrete model into the agent file |
| `readonly` | — (tools/disallowedTools are used instead) | ✓ natively supported |
| `skills` | ✓ preloads skills into the context | ✗ ignored (skills live in the body) |
| `color` | ✓ visual identifier | ✗ not supported |
| `tools` | ✓ tool restrictions | ✗ not supported |

**Compatibility strategy:**
- Fields the IDE does not understand are simply ignored — that is not an error
- `readonly: true` is native to Cursor; in Claude Code it can be replaced by `tools` in the adapter layer
- In the body we mirror the skill names — cheap insurance for Cursor

---

## 4. 1C BSL Domain: Context for the System Prompt

When you craft the system prompt for a 1C agent, keep the following context in mind:

### Key Platform Constraints
- The agent **DOES NOT create metadata objects** (Справочники, Документы, регистры) — only code in `.bsl` modules
- Metadata are managed by the Configurator or EDT, not by code
- BSL is server and client code with compilation directives (`&НаСервере`, `&НаКлиенте`)

### Tools via MCP
- The agent sees tools through MCP (`tools/list`) — this is the ground truth
- The "Used capability" section in the agent file is **not needed** — the agent discovers tools dynamically
- Skills under `tool-usage/*` contain hints about when and how to use specific MCP tools

### Standards and Patterns
- MADR 4.0 — specification format
- RFC 2119 — obligation levels (MUST/SHOULD/MAY)
- YaxUnit — testing framework
- БСП (BSL Subsystem Library) — standard subsystem patterns

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
9. [ ] No "Used capability" section — tools come through MCP
10. [ ] No separate "Input/Output" tables — inline them in the body

---
depends_on: []
---
