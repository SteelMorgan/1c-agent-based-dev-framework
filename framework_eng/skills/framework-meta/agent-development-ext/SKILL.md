---
name: agent-development-ext
description: MUST load together with `agent-development` in GBIG context. Adds the universal agent format for the framework (analyst, architect, developer, reviewer, tester, explorer), model tier mapping, and 1C BSL specifics.
---

# Agent Development — 1C BSL Framework Extension

> **Base skill:** `agent-development` (Anthropic).
> First read the base skill — it contains the general principles for creating agents.
> This file adds **only** 1C-specific details and adaptation for our framework.

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

model: opus                # статический IDE-алиас; runtime controller использует exact tuple из канонической матрицы
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

**Skills and rules (for Cursor):**
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

The "Skills and rules" section in the body is needed for Cursor — it ignores `skills` from frontmatter. We duplicate only the names and one line of purpose.

---

## 2. Framework Roles and Models

### Canonical role → model/effort matrix

This is the single source of recommended default routes for all 10 subagents. The entry
`Primary / fallback` denotes the operational route in case of technical unavailability, not a benchmark
challenger. Exact tuple is verified against the actual schema harness before execution and recorded in
the runtime trace. `tools/model-defaults.json` contains only IDE aliases and does not override this matrix.

| Role | Primary | Operational fallback | readonly |
|------|---------|----------------------|----------|
| analyst | Claude Opus 5 / high | GPT-5.6 Sol / high | true |
| architect | Claude Opus 5 / high | GPT-5.6 Sol / high | true |
| explorer | GPT-5.6 Luna / max | — | true |
| reviewer | Claude Opus 5 / medium | dynamic escalation to the author's floor | true |
| scenario-author | Claude Opus 5 / medium | GPT-5.6 Sol / medium | false |
| scenario-coder | GPT-5.6 Luna / max | Claude Opus 5 / medium | false |
| developer-tests | GPT-5.6 Luna / max | Claude Opus 5 / medium | false |
| developer-code | GPT-5.6 Luna / max | Claude Opus 5 / medium | false |
| tester | Claude Opus 5 / medium | GPT-5.6 Sol / medium | false |
| debugger | Claude Opus 5 / high | GPT-5.6 Sol / high | false |

The common emergency fallback when limits are exhausted is Kimi K3 with the maximum supported mode,
but only after role-specific qualification and when an allowed route is available in the current environment.
Kimi is not a silent fallback and does not lower the capability floor.

The orchestrator may change effort based on complexity, criticality, and error tolerance. The table values are
defaults, not a ceiling; an override must have a rationale and a runtime trace. Outside Herdr, only
qualified models from the current harness family are used; inside Herdr, cross-family routing is performed via
`herdr-agent-routing`.

### Reviewer Rule

`Opus 5 / medium` — Reviewer controller default. The actual blocking gate reviewer MUST not be
weaker than the actual author in capability and effort; for an author with `high`, Reviewer is raised at least to
`high`. If the schema does not allow preserving the floor, the gate is blocked.

### CLI: model mapping

`tools/model-defaults.json` serves only static aliases for a specific
IDE. It does not express effort, the cross-provider Herdr route, or the emergency Kimi K3,
therefore it is not a way to apply the canonical matrix. The exact tuple
is selected by the Orchestrator/controller at runtime; the installer only materializes
the profile and may substitute a static alias supported by this IDE.

---

## 3. Cursor / Claude Code Compatibility

| Field | Claude Code | Cursor |
|------|-------------|--------|
| `name` | ✓ | ✓ |
| `description` | ✓ trigger + examples | ✓ description rules |
| `model` | ✓ alias | ✓ CLI will substitute a specific model |
| `readonly` | — (tools/disallowedTools) | ✓ native |
| `skills` | ✓ preload | ✗ ignored → duplicate names in body |
| `color` / `tools` | ✓ | ✗ |

Unknown fields are ignored — this is not an error.

---

## 4. 1C BSL Domain: Context for system prompt

When writing a system prompt for 1С agents, keep in mind:

### Key Constraints
- The agent **does not create metadata objects** — only code in `.bsl` modules
- BSL is server-side and client-side code with compilation directives (`&НаСервере`, `&НаКлиенте`)
- Tools are discovered through MCP (`tools/list`); a "capability" section in the agent file is not needed
- The `tool-usage/*` skills describe when and how to use MCP tools
- Standards: MADR 4.0, RFC 2119, YaxUnit, БСП

---

## 5. Framework Agent Creation Checklist

1. [ ] `name` — lowercase, hyphens, 3-50 chars
2. [ ] `description` — trigger conditions + 2-3 `<<example>>` blocks
3. [ ] `model` — a supported static alias for the current IDE; for a dynamic role, the canonical exact tuple is set by the runtime route, not by this field
4. [ ] `readonly` — true for read-only roles
5. [ ] `skills` — list of skills for preload
6. [ ] Body — system prompt in second person (You are...)
7. [ ] In body — a "Skills and Rules" section with names and purpose
8. [ ] In body — Core Responsibilities, Protocol, Quality Standards, Boundaries
9. [ ] No "Used capability" section — tools via MCP
10. [ ] No separate "Input/Output Data" tables — they are embedded in the body

---
depends_on: []
---
