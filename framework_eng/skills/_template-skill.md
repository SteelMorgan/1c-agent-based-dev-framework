---
name: template-skill
description: Skill template for filling new files.
---


<!--
  Skill template (Skill)
  ========================
  Copy this file to the appropriate subdirectory and fill in all sections.
  Remove hint comments after you complete it.

  Skill types:
  - bsl-practices/ — BSL coding standards, patterns, and antipatterns
  - tool-usage/    — how and when to use MCP tools
  - spec-writing/  — specification writing standards
  - (new type)/   — create a subdirectory for the new type

  YAML frontmatter:
  - name: <kebab-case>
  - description: <purpose and triggers>

  Backmatter (technical block at the end of the file):
  - depends_on: []
-->

# [Skill Name]

## Purpose

<!-- 1-3 sentences: why this skill exists, what problem it solves.
     The main thing is to explain WHY, not just WHAT. -->

---

## When to apply

<!-- Trigger table: what the agent must notice to apply this skill -->

| Trigger | Action |
|---------|--------|
| [Situation the agent notices] | [What the agent should do] |
| [Situation 2] | [Action 2] |

---

## Use cases

### Scenario 1: [Title]

<!-- A concrete use case example for the skill:
     1. Context — what is given
     2. Actions — what the agent does (steps)
     3. Result — what is achieved -->

**Context:** [description of the situation]

**Capability:** `[capability_name]` (for tool-usage skills)

**Steps:**
1. [Step 1]
2. [Step 2]
3. [Step N]

---

## Examples

### Correct

```bsl
// [Описание — почему это правильно]
[код BSL]
```

### Incorrect

```bsl
// [Описание — почему это неправильно и к чему приведёт]
[код BSL]
```

---

## Common mistakes

<!-- What mistakes agents make if they do not follow this skill -->

| Mistake | Consequence | How to avoid |
|--------|------------|--------------|
| [Mistake 1] | [What will happen] | [What to do correctly] |

---

## Related resources

<!-- Links to related skills, rules, capabilities -->

- [Resource name](relative/path) — brief description of the relation

---
depends_on: []
---
