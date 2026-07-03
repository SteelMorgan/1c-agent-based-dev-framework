---
name: skill-reading-protocol
description: "Mandatory protocol for reading skills and rules by a subagent at startup. Common to all subagent profiles."
alwaysApply: true
---

# Mandatory Skill and Rule Reading Protocol

This rule is the single source of truth for the skill and rule reading protocol. Every subagent profile
references it instead of duplicating the text. The rule is read IN FULL at startup (like all rules).

**CRITICAL: Mandatory reading of skills and rules:**
At the end of the profile prompt there is a `depends_on` section with a list of dependencies.
In the header there is a `skills:` field with a list of skills.

**Skills are NOT loaded automatically.** BEFORE starting work, read ONLY the purpose (frontmatter: `name` + `description`) of each skill from `skills:` — so you know what each skill is for. **Read the full body of SKILL.md lazily — at the moment you actually apply that skill.** Rules (step 4 below) are read IN FULL at startup — these are guardrails, and you must know them before the first action.
Failing to apply the needed skill is a protocol violation. Do not create an artifact without having read and applied the corresponding skill.

1. Find `.install-session.json` in the project root
2. In it, the `component_map` field is a dictionary `"type/name" → {ru_path, en_path}`
3. For each skill from `skills:` in the header:
   - Find the `skill/{name}` key in `component_map`
   - Read ONLY the SKILL.md frontmatter (`name` + `description`) from `ru_path` (or `en_path`) — record the skill's purpose
   - Write to context: `[SKILL_NOTED] {name} — purpose recorded`
   - Read the full body of SKILL.md later, when the task requires applying that exact skill → then `[SKILL_READ] {name} — read before application`
4. For each path from `depends_on` containing `/rules/`:
   - Extract the file name without the extension → this is `name`
   - Find the `rule/{name}` key in `component_map`
   - Read the file from `en_path` (or `ru_path` if EN is unavailable)
5. Apply the read skills and rules throughout the entire work

---
depends_on: []
---
