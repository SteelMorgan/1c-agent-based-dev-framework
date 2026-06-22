---
name: skill-learning
description: MUST use AFTER a work cycle with ≥2 iterations (wrote → error → fixed → success). Provides the retrospective procedure and format for recording technique/anti-technique in references/learned-patterns.md or {project}/.context/learned-patterns.md.
installable: true
alwaysApply: false
---

# Knowledge Accumulation (Skill Learning) — procedure

> The body was moved out of the `skill-learning-policy` rule. The trigger "after a cycle of ≥2 iterations" remains in the rule; here are the retrospective procedure, the recording format, and the criterion for choosing the level/file.

> The agent learns from its iterations: what works (techniques) and what breaks (anti-techniques).
> Knowledge is recorded in `references/learned-patterns.md` of the owning skill.

## Two levels of knowledge

| Level | File | What it stores |
|---------|------|------------|
| Universal | `skill/references/learned-patterns.md` | Techniques that work in any 1C configuration |
| Project | `{project}/.context/learned-patterns.md` | Techniques tied to a specific configuration / project |

**Separation criterion:** if an entry mentions a specific metadata object, form name, attribute
or configuration feature → project-level. If it describes a general platform/framework pattern → universal.

## Using accumulated knowledge

The reading trigger ("before working with the skill, read its lessons") is in the `skill-learning-policy` rule (always-on). Here is the split by levels:

- `references/learned-patterns.md` in the skill directory — universal techniques;
- `{project}/.context/learned-patterns.md` — project techniques.

Apply `confirmed` entries as additional rules, `candidate` as hints.

## When to run

After completing a work cycle in which there were **≥2 iterations** (wrote → error → fixed → success).
If the task is solved on the first attempt, a retrospective is not needed.

## Procedure

1. **Reconstruct the iteration chain** — what was done → what failed → root cause → how it was fixed → what passed.

2. **Abstract to a class.** Name the CLASS of the error one level above the incident, not the incident itself:
   - bad (instance): "``биг_Модуль.Метод:142`` crashed because `ИдентификаторСтроки` was empty";
   - good (class): "accessing a record set field without checking whether it is filled before writing".

   The anti-technique is formulated as a generalized prohibition/check that can be carried over to not-yet-seen special cases. Concrete `file:line` references go in the `source` field, NOT in the body of the technique/anti-technique.

3. **Test the abstraction.** Ask: "To what other situation, besides the one that produced it, does this anti-technique apply?" No answer → this is an instance, not a class: reformulate it higher or reject the entry.

4. **Formulate the entry** (technique and anti-technique are **one entry**, two sides of one discovery):

```
status: candidate | confirmed
класс: <named class of error>
приём: <general rule — what to do, verified by success>
антиприём: <general prohibition — what NOT to do, verified by failure>
почему: <what happens when violated>
шаги: <concrete steps/code, if applicable>
источник: <task, iteration, file:line example>
```

5. **Filter out** — NOT a lesson:
   - Typos and accidental syntax errors;
   - One-off environment failures without a reproducible class;
   - An incident that did not pass the abstraction test (an instance without a class).

6. **Determine the level and the owning skill:**
   - mentions a specific metadata object / configuration → **project-level** → `{project}/.context/learned-patterns.md` (Russian, without mapping and synchronization);
   - general platform / framework pattern → **universal** → `references/learned-patterns.md` of the owning skill in the **RU source of the framework**;
   - the owning skill is the one to which the class belongs by content.

7. **Find the RU directory of the universal lesson** (using the `skill-editing-from-project` skill):
   `.install-session.json` → `component_map` → `skill/{owning-skill-name}` → `ru_path` → its directory → `references/learned-patterns.md`. Write to `framework/` (RU), NOT to the installed ENG symlink `framework_eng/` — it will be overwritten by synchronization.

8. **Reconciliation cascade** (read the target file before writing):
   - a similar class already exists → DO NOT duplicate; update the existing entry (expand the anti-technique boundaries, add the source), result = `refined`;
   - there is a `candidate` with the same class → promote to `confirmed`, result = `refined`;
   - there is no class → new `candidate` entry, result = `new`.

9. **Append** to the target file; if the file does not exist, create it; do not overwrite existing entries. For a universal lesson, after writing, **synchronize the ENG mirror** via `sync_script` from `.install-session.json` (see `skill-editing-from-project`).

## MUST

| Requirement | Description |
|------------|----------|
| One level above the incident | The entry is about the CLASS of the error + anti-technique, not a specific incident; the abstraction test is mandatory |
| Do not change the body of the skill | Entries go only into `references/learned-patterns.md`, not into `SKILL.md` |
| Reconciliation / update | Read the target file before writing; do not create duplicates, update the existing class; result `matched`/`refined`/`new` |
| RU source for universal | Write the universal lesson into the `framework/` (RU) directory of the owning skill via `skill-editing-from-project`, NOT into `framework_eng/`; after writing, synchronize to ENG |
| First case = candidate | A single case gets `status: candidate` |
| Repeat = confirmed | When a similar situation repeats, promote it to `confirmed` |

---
depends_on:
  - skill-editing-from-project
---
