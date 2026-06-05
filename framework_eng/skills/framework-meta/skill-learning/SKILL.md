---
name: skill-learning
description: MUST use AFTER a work cycle with ≥2 iterations (wrote → error → fixed → success). Provides the retrospective procedure and the format for recording practice/anti-patterns in references/learned-patterns.md or {project}/.context/learned-patterns.md.
alwaysApply: false
---

# Knowledge Accumulation (Skill Learning) — procedure

> The body was moved out of the `skill-learning-policy` rule. The trigger "after a cycle of ≥2 iterations" remains in the rule; here are the retrospective procedure, the recording format, and the criterion for choosing the level/file.

> The agent learns from its iterations: what works (techniques) and what breaks (anti-techniques).
> Knowledge is recorded in `references/learned-patterns.md` of the owning skill.

## Two levels of knowledge

| Level | File | What it stores |
|-------|------|----------------|
| Universal | `skill/references/learned-patterns.md` | Techniques that work in any 1C configuration |
| Project | `{project}/.context/learned-patterns.md` | Techniques tied to a specific configuration / project |

**Separation criterion:** if an entry mentions a specific metadata object, form name, attribute
or configuration feature -> project-level. If it describes a general platform/framework pattern -> universal.

## Using accumulated knowledge

The reading trigger ("before working with the skill, read its lessons") is in the `skill-learning-policy` rule (always-on). Here is the split by level:

- `references/learned-patterns.md` in the skill directory — universal techniques;
- `{project}/.context/learned-patterns.md` — project techniques.

Apply `confirmed` entries as additional rules, `candidate` as hints.

## When to run

After completing a work cycle in which there were **≥2 iterations** (wrote → error → fixed → success).
If the task is solved on the first attempt, a retrospective is not needed.

## Procedure

1. **Reconstruct the iteration chain** — what was done → what failed → how it was fixed → what passed

2. **For each nontrivial fix**, formulate an entry:

```
status: candidate | confirmed
область: <free-form wording>
приём: <what to do — verified by success>
антиприём: <what NOT to do — verified by failure>
почему: <what happens when the rule is violated>
шаги: <concrete steps/code, if applicable>
источник: <task, iteration>
```

The technique and the anti-technique are **one entry**, two sides of one discovery. Do not duplicate.

3. **Filter out** — exclude:
   - Typos and accidental syntax errors
   - One-off environment failures

4. **Determine the level and the owning skill:**
   - Mentions a specific metadata object / configuration -> **project-level** -> `{project}/.context/learned-patterns.md`
   - General platform / framework pattern -> **universal** -> `references/learned-patterns.md` of the owning skill
   - Owning skill: the skill to which the technique belongs by content.
     Path to the RU version: `.install-session.json` → `component_map` → `skill/{name}` → `ru_path`.

5. **Read the existing entries** from the target file:
   - Similar technique already exists -> do not duplicate
   - There is a `candidate` with the same area -> promote to `confirmed`

6. **Append** the entry to the target file.
   If the file does not exist, create it. Do not overwrite existing entries.

## MUST

| Requirement | Description |
|-------------|-------------|
| Do not modify the skill body | Entries are only in `references/learned-patterns.md`, not in `SKILL.md` |
| Do not duplicate | Read existing techniques before writing |
| First occurrence = candidate | A single occurrence gets `status: candidate` |
| Repeat = confirmed | When a similar situation repeats, promote to `confirmed` |

---
depends_on: []
---
