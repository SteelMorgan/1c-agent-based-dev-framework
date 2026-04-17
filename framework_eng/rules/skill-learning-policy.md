---
name: skill-learning-policy
description: Knowledge accumulation policy. After an iteration cycle the agent extracts vetted practices and anti-practices and writes them to references/learned-patterns.md of the target skill.
alwaysApply: true
---

# Knowledge Accumulation Policy (Skill Learning)

> The agent learns from its iterations: what works (practices) and what breaks (anti-practices).
> Knowledge is recorded in `references/learned-patterns.md` of the owner skill.

## Two Levels of Knowledge

| Level | File | What it stores |
|-------|------|----------------|
| Universal | `skill/references/learned-patterns.md` | Practices that work in any 1С configuration |
| Project-level | `{project}/.context/learned-patterns.md` | Practices tied to a specific configuration/project |

**Splitting criterion:** if an entry mentions a specific metadata object, form name, attribute, or configuration peculiarity → project-level. If it describes a common platform/framework pattern → universal.

## Using Accumulated Knowledge

Before starting work with the skill:
1. Check `references/learned-patterns.md` in the skill directory — universal practices
2. Check `{project}/.context/learned-patterns.md` — project practices

Apply `confirmed` entries as additional rules, `candidate` entries as hints.

## When to Run

After completing a work cycle composed of **≥2 iterations** (wrote → error → fixed → success).
If the task is solved on the first try — no retrospective is needed.

## Procedure

1. **Reconstruct the iteration chain** — what was done → what failed → how it was fixed → what succeeded

2. **For each nontrivial fix** formulate an entry:

```
status: candidate | confirmed
область: <свободная формулировка>
приём: <что делать — проверено успехом>
антиприём: <что НЕ делать — проверено падением>
почему: <что происходит при нарушении>
шаги: <конкретные шаги/код, если применимо>
источник: <задача, итерация>
```

Practice and anti-practice are **one entry**, two sides of the same discovery. Do not duplicate.

3. **Filter** — exclude:
   - Typos and accidental syntax mistakes
   - One-time environment failures

4. **Determine the level and owner skill:**
   - Mentions a specific metadata object or configuration → **project-level** → `{project}/.context/learned-patterns.md`
   - General platform/framework pattern → **universal** → `references/learned-patterns.md` of the owner skill
   - Owner skill: the skill whose content the practice relates to.
     Path to the RU version: `.install-session.json` → `component_map` → `skill/{name}` → `ru_path`.

5. **Read existing entries** from the target file:
   - An identical practice already exists → do not duplicate
   - There is a `candidate` with the same scope → promote it to `confirmed`

6. **Append** the entry to the target file.
   If the file is missing — create it. Do not overwrite existing entries.

## MUST

| Requirement | Description |
|-------------|-------------|
| Do not change the skill body | Entries belong only in `references/learned-patterns.md`, not in `SKILL.md` |
| Do not duplicate | Read existing practices before writing |
| First occurrence = candidate | A single instance gets `status: candidate` |
| Repeat = confirmed | A recurring similar situation gets upgraded to `confirmed` |

---
depends_on: []
---
