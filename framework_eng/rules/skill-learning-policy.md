---
name: skill-learning-policy
description: Knowledge accumulation policy. After an iteration cycle the agent extracts verified tactics and anti-tactics and writes them to references/learned-patterns.md of the target skill.
---

# Knowledge Accumulation Policy (Skill Learning)

> Agent learns from its iterations: what works (tactics) and what breaks (anti-tactics).
> Knowledge is recorded in `references/learned-patterns.md` of the owner skill.

## Two knowledge levels

| Level | File | What it stores |
|-------|------|----------------|
| Universal | `skill/references/learned-patterns.md` | Tactics that work in any 1С configuration |
| Project-specific | `{project}/.context/learned-patterns.md` | Tactics tied to a specific configuration/project |

**Separation criterion:** if an entry mentions a specific metadata object, form name, attribute, or configuration peculiarity → project-specific. If it describes a general platform/framework pattern → universal.

## Using accumulated knowledge

Before starting work with the skill:
1. Check `references/learned-patterns.md` in the skill directory — universal tactics
2. Check `{project}/.context/learned-patterns.md` — project tactics

Treat `confirmed` entries as additional rules, `candidate` entries as hints.

## When to run

After completing a cycle of work that included **≥2 iterations** (wrote → error → fixed → success).
If the task was solved on the first attempt, no retrospective is needed.

## Procedure

1. **Reconstruct the iteration chain** — what was done → what failed → how it was fixed → what succeeded

2. **For every non-trivial fix** craft an entry:

```
status: candidate | confirmed
scope: <free-form description>
tactic: <what to do — verified success>
anti-tactic: <what NOT to do — verified failure>
why: <what happens when violated>
steps: <concrete steps/code, if applicable>
source: <task, iteration>
```

Tactic and anti-tactic are **one entry**, two sides of the same discovery. Do not duplicate.

3. **Filter** — exclude:
   - Typos and accidental syntax mistakes
   - One-time environment failures

4. **Determine the level and owner skill:**
   - Mentions a specific metadata object or configuration → **project-specific** → `{project}/.context/learned-patterns.md`
   - General platform/framework pattern → **universal** → `references/learned-patterns.md` of the owner skill
   - Owner skill: the skill whose content the tactic relates to.
     Path to the RU version: `.install-session.json` → `component_map` → `skill/{name}` → `ru_path`.

5. **Read existing entries** from the target file:
   - An identical tactic already exists → do not duplicate
   - There is a `candidate` with the same scope → promote it to `confirmed`

6. **Append** the entry to the target file.
   If the file is missing — create it. Do not overwrite existing entries.

## MUST

| Requirement | Description |
|-------------|-------------|
| Do not change the skill body | Entries belong only in `references/learned-patterns.md`, not in `SKILL.md` |
| Do not duplicate | Read existing tactics before writing |
| First occurrence = candidate | A single instance gets `status: candidate` |
| Repeat = confirmed | A recurring similar situation gets upgraded to `confirmed` |

---
depends_on: []
---
