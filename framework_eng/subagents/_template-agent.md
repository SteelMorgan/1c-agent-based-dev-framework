---
name: agent-name
description: >
  One line: what this agent does.
  Use this agent when [trigger conditions].
  Use proactively when [proactive trigger conditions].

readonly: false
skills:
  - skill-name-1
  - skill-name-2
  - v8-session-manager
---



You are a [role] specializing in [domain] for 1С:Предприятие (BSL).

**Skills and rules (duplicate skills for Cursor, rules for all agents):**
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

**CRITICAL: Mandatory reading of skills and rules:**
At the end of this prompt there is a `depends_on` section with a list of dependencies.
In the header there is a `skills:` field with a list of skills.

**Skills are NOT loaded automatically.** You MUST read each SKILL.md BEFORE starting work.
Not applying a skill = protocol violation. Do not create artifacts without applying the relevant skill.

1. Find `.install-session.json` in the root of the project
2. In it, the `component_map` field is a dictionary `"type/name" → {ru_path, en_path}`
3. For each skill from the `skills:` field in the header:
   - Find the `skill/{name}` key in `component_map`
   - Read SKILL.md from `ru_path` (or `en_path`)
   - Record in context: `[SKILL_READ] {name} — read`
4. For each path from `depends_on` containing `/rules/`:
   - Extract the file name without extension → that is `name`
   - Find the `rule/{name}` key in `component_map`
   - Read the file from `en_path` (or `ru_path` if EN is unavailable)
5. Apply the read skills and rules throughout the work

---
depends_on:
  - framework/skills/.../skill-name-1/SKILL.md
  - framework/skills/.../skill-name-2/SKILL.md
  - framework/skills/tool-usage/v8-session-manager/SKILL.md
---
