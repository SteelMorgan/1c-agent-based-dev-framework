---
name: skill-learning
description: Procedure for retrospection and the format for recording accumulated knowledge (pattern/anti-pattern) in the owning skill's references/learned-patterns.md or {project}/.context/learned-patterns.md. Apply after a work cycle with ≥2 iterations (wrote → error → fixed → success).
alwaysApply: false
---

# Accumulating Knowledge (Skill Learning) — procedure

> The body was moved out of the rule `skill-learning-policy`. The trigger "after a cycle of ≥2 iterations" remains in the rule; here is the retrospective procedure, recording format, and criterion for choosing the level/file.

> The agent learns from its iterations: what works (patterns) and what breaks (anti-patterns).
> Knowledge is recorded in the owning skill's `references/learned-patterns.md`.

## Two levels of knowledge

| Level | File | What it stores |
|---------|------|------------|
| Universal | `skill/references/learned-patterns.md` | Patterns that work in any 1C configuration |
| Project-specific | `{project}/.context/learned-patterns.md` | Patterns tied to a specific configuration / project |

**Separation criterion:** if the entry mentions a specific metadata object, form name, attribute
or a configuration-specific feature → project-specific. If it describes a general platform/framework pattern → universal.

## Using accumulated knowledge

Before starting work with the skill:
1. Check `references/learned-patterns.md` in the skill directory — universal patterns
2. Check `{project}/.context/learned-patterns.md` — project-specific patterns

Apply `confirmed` entries as additional rules, `candidate` entries as hints.

## When to run

After completing a work cycle that had **≥2 iterations** (wrote → error → fixed → success).
If the task is solved on the first attempt — no retrospective is needed.

## Procedure

1. **Reconstruct the iteration chain** — what was done → what failed → how it was fixed → what passed

2. **For each nontrivial fix** formulate an entry:

```
status: candidate | confirmed
область: <free-form wording>
приём: <what to do — verified by success>
антиприём: <what NOT to do — verified by failure>
почему: <what happens when the rule is broken>
шаги: <concrete steps/code, if applicable>
источник: <task, iteration>
```

The pattern and anti-pattern are **one entry**, two sides of the same discovery. Do not duplicate.

3. **Filter out**:
   - Typos and accidental syntax errors
   - One-off environment failures

4. **Determine the level and owning skill:**
   - Mentions a specific metadata object / configuration → **project-specific** → `{project}/.context/learned-patterns.md`
   - General platform / framework pattern → **universal** → `references/learned-patterns.md` of the owning skill
   - Owning skill: the skill to which the pattern belongs by content.
     Path to the RU version: `.install-session.json` → `component_map` → `skill/{name}` → `ru_path`.

5. **Read the existing entries** from the target file:
   - A similar pattern already exists → do not duplicate
   - There is a `candidate` with the same scope → promote it to `confirmed`

6. **Append** the entry to the target file.
   If the file does not exist — create it. Do not overwrite existing entries.

## MUST

| Requirement | Description |
|------------|----------|
| Do not change the skill body | Entries are only in `references/learned-patterns.md`, not in `SKILL.md` |
| Do not duplicate | Read existing patterns before writing |
| First occurrence = candidate | A single occurrence gets `status: candidate` |
| Repeat = confirmed | When a similar situation repeats, promote it to `confirmed` |

---
depends_on: []
---
