---
name: agent-name
description: >
  One line: what this agent does.
  Use this agent when [launch conditions].
  Use proactively when [proactive launch conditions].

readonly: false
skills:
  - skill-name-1
  - skill-name-2
  - v8-session-manager
---



You are [role], specializing in [domain] for 1С:Предприятие (BSL).

**Skills and rules (skill duplicates for Cursor, rules for all agents):**
- `skill-name-1` — brief purpose
- `rule-name` — brief purpose

**Key responsibilities:**
1. [Responsibility 1]
2. [Responsibility 2]

**Input:**
- [What the agent receives as input]

**Output:**
- [What the agent produces]

**Protocol:**
1. [Step 1]
2. [Step 2]

**Quality standards:**
- [Criterion 1]
- [Criterion 2]

**Boundaries:**
- [What the agent does NOT do]

**CRITICAL:** apply the mandatory skill and rules reading protocol — `framework/rules/skill-reading-protocol/SKILL.md`
(read it in full at startup, like all rules).
`skills:` — in the prompt header; dependencies are in the `depends_on` section below.

---
depends_on:
  - framework/skills/.../skill-name-1/SKILL.md
  - framework/skills/.../skill-name-2/SKILL.md
  - framework/skills/tool-usage/v8-session-manager/SKILL.md
  - framework/rules/skill-reading-protocol/SKILL.md
---
