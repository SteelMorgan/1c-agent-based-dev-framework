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
> Read the base skill first — it contains the general principles for agent creation.
> This file adds **only** the 1C-specific details and adaptation for our framework.

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

model: sonnet              # haiku | sonnet | opus (alias, the CLI will substitute a concrete model for Cursor)
readonly: true             # true for read-only agents (analyst, explorer, reviewer)
skills:                    # Claude Code will preload them automatically; Cursor ignores this field
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
- [What the agent does NOT do]
```

### Why include the “Skills and Rules” section in the body?

Claude Code will preload the skills from the frontmatter automatically. Cursor does not; it simply ignores the `skills` field. That is why we duplicate **only the names and a one-line purpose** in the body so that the Cursor agent knows which skills are relevant and can locate them.

---

## 2. Framework Roles and Models

### Role-to-model mapping table

| Role | model | readonly | Rationale |
|------|-------|----------|-----------|
| explorer | haiku | true | Deterministic work, tools provide precise results |
| analyst | sonnet | true | Requirements analysis, specification drafting |
| tester | sonnet | false | Writing and running tests |
| architect | sonnet | true | Technical decisions, trade-offs |
| developer | sonnet | false | Code implementation, TDD |
| reviewer | opus | true | Critical role — assessing artifacts, tier ≥ author |

### Reviewer rule

The reviewer’s `model` MUST be ≥ the model of the artifact’s author. If the author uses `sonnet`, the reviewer must be `sonnet` or `opus`.

### CLI: interactive model selection

When running `python tools/1c-ai-agent-cli.py`, the user selects a specific model for each agent (or accepts the defaults).

**Defaults** are stored in `tools/model-defaults.json` — per IDE:

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
- Edit `model-defaults.json` to match their available model lineup
- Choose a per-agent model during setup (mode `[a]`)
- Use non-Anthropic models (gpt, gemini, grok — whatever the IDE supports)

---

## 3. Cursor vs Claude Code format nuances

| Field | Claude Code | Cursor |
|------|-------------|--------|
| `name` | ✓ agent identifier | ✓ rule identifier |
| `description` | ✓ trigger description with examples | ✓ used as the rule `description` |
| `model` | ✓ native: haiku/sonnet/opus | ✓ CLI writes the concrete model into the agent file |
| `readonly` | — (tools/disallowedTools are used instead) | ✓ natively supported |
| `skills` | ✓ preload skills into the context | ✗ ignored (skills go in the body) |
| `color` | ✓ visual identifier | ✗ not supported |
| `tools` | ✓ tool restrictions | ✗ not supported |

**Compatibility strategy:**
- Fields not understood by an IDE are simply ignored — that is not an error
- `readonly: true` is native to Cursor; in Claude Code it can be mapped to `tools` at the adapter layer
- We duplicate the skill names in the body as inexpensive insurance for Cursor

---

## 4. 1C BSL Domain: Context for the System Prompt

When writing the system prompt for 1C agents keep in mind:

### Key platform constraints
- The agent **does NOT create metadata objects** (справочники, документы, регистры) — only code inside `.bsl` modules
- Metadata is managed via the configurator or EDT, not code
- BSL is server-side and client-side code with compilation directives (`&НаСервере`, `&НаКлиенте`)

### Tools via MCP
- The agent sees tools through MCP (`tools/list`) — this is the current source
- The “Used capability” section in the agent file is **not needed** — the agent discovers tools dynamically
- The `tool-usage/*` skills contain hints about when and how to use specific MCP tools

### Standards and patterns
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
6. [ ] Body — system prompt in the second person (You are...)
7. [ ] In the body — “Skills and Rules” section with names and purpose
8. [ ] In the body — Core Responsibilities, Protocol, Quality Standards, Boundaries
9. [ ] No “Used capability” section — tools via MCP
10. [ ] No separate tables for “Input/Output” — they are embedded in the body

---
depends_on: []
---
