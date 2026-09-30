---
name: skill-learning
description: MUST use AFTER a work cycle with ≥2 iterations (wrote → error → fixed → success). Provides a retrospective procedure and a recording format for practices/anti-patterns in references/learned-patterns.md or {project}/.context/learned-patterns.md.
installable: true
alwaysApply: false
---

# Knowledge Accumulation (Skill Learning) — procedure

> The body was moved out of the `skill-learning-policy` rule. The trigger “after a cycle of ≥2 iterations” remains in the rule; here is the retrospective procedure, the recording format, and the criterion for choosing the level/file.

> The agent learns from its iterations: what works (practices) and what breaks (anti-patterns).
> Knowledge is recorded in the owning skill's `references/learned-patterns.md`.

## Two knowledge levels

| Level | File | What it stores |
|---------|------|------------|
| Universal | `skill/references/learned-patterns.md` | Practices that work in any 1C configuration |
| Project-specific | `{project}/.context/learned-patterns.md` | Practices tied to a specific configuration / project |

**Separation criterion:** if an entry mentions a specific metadata object, form name, attribute
or configuration-specific feature → project-specific. If it describes a general platform/framework pattern → universal.

## Using accumulated knowledge

The read trigger (“before working with a skill, read its lessons”) is in the `skill-learning-policy` rule (always-on). Here is the breakdown by level:

- `references/learned-patterns.md` in the skill directory — universal practices;
- `{project}/.context/learned-patterns.md` — project-specific practices.

Apply `confirmed` entries as additional rules, and `candidate` entries as hints.

## When to run

After completing a work cycle in which there were **≥2 iterations** (wrote → error → fixed → success).
If the task is solved on the first attempt, a retrospective is not needed.

A direct user instruction does not trigger learned-patterns recording by itself:
this is not the agent exiting its own error cycle, but a change to the rule/process.
Such a correction must be made in the body of the relevant skill/rule or in the project
rule if it applies only to the project.

## Procedure

1. **Reconstruct the iteration chain** — what was done → what failed → root cause → how it was fixed → what passed.

2. **Abstract to a class.** Name the ERROR CLASS one level above the incident, not the incident itself:
   - bad (instance): “`big_Module.Method:142` failed because `LineIdentifier` was empty”;
   - good (class): “accessing a record set field without checking whether it is filled before writing”.

   The anti-recipe is formulated as a generalized prohibition/check that can be applied to as-yet-unseen special cases. Concrete `file:line` entries go into the `source` field, NOT into the body of the recipe/anti-recipe.

3. **Abstraction test.** Ask: “to what other situation, besides the one that generated it, does this anti-recipe apply?”. No answer → this is an instance, not a class: rephrase higher or reject the entry.

4. **Formulate the entry** (recipe and anti-recipe are **one entry**, two sides of one discovery):

```
status: candidate | confirmed
class: <named error class>
recipe: <generalized rule — what to do, verified by success>
anti-recipe: <generalized prohibition — what NOT to do, verified by failure>
why: <what happens when violated>
steps: <concrete steps/code, if applicable>
source: <task, iteration, file:line example>
```

5. **Filter out** — NOT a lesson:
   - typos and accidental syntax errors;
   - one-off environment failures without a reproducible class;
   - an incident that did not pass the abstraction test (an instance without a class).
   - a direct user instruction: a new rule, prohibition, diagnostic order
     or process correction explicitly formulated by the user is not a
     learned lesson. It must be added to the body of the corresponding skill/rule
     (or to the project rule, if the instruction is project-specific) according to the
     `skill-editing-from-project` procedure.

6. **Determine the level and owning skill:**
   - mentions a specific metadata object / configuration → **project-level** → `{project}/.context/learned-patterns.md` (Russian, without mapping or synchronization);
   - a general platform / framework pattern → **universal** → `references/learned-patterns.md` of the owning skill in the **RU source of the framework**;
   - the owning skill is the one to which the class belongs by content.

7. **Find the RU directory of the universal lesson** (via the `skill-editing-from-project` skill):
   `.install-session.json` → `component_map` → `skill/{owning-skill-name}` → `ru_path` → its directory → `references/learned-patterns.md`. Write to `framework/` (RU), NOT to the installed ENG symlink `framework_eng/` — it will be overwritten by synchronization.

8. **Cross-check cascade** (before writing, read the target file):
   - a similar class already exists → DO NOT duplicate; update the existing entry (expand the boundaries of the anti-recipe, add the source), outcome = `refined`;
   - there is a `candidate` with the same class → promote to `confirmed`, outcome = `refined`;
   - no class exists → new `candidate` entry, outcome = `new`.

9. **Append** to the target file; if the file does not exist, create it; do not overwrite existing entries. For a universal lesson, after writing - **synchronize the ENG mirror** via the `sync_script` from `.install-session.json` (see `skill-editing-from-project`).

## MUST

| Requirement | Description |
|------------|----------|
| One level above the incident | An entry about the ERROR CLASS + anti-pattern, not about a specific incident; an abstraction test is required |
| Do not change the skill body | learned-patterns entries go only in `references/learned-patterns.md`, not in `SKILL.md`; exception: a direct user instruction about a new rule/prohibition/process that changes the body of the corresponding skill/rule |
| Reconcile and update | Before writing, read the target file; do not create duplicates; update the existing class; outcome: `matched`/`refined`/`new` |
| RU source for universal | Write the universal lesson in the `framework/` (RU) directory of the owning skill via `skill-editing-from-project`, NOT in `framework_eng/`; after writing, sync to ENG |
| First case = candidate | A single case gets `status: candidate` |
| Repetition = confirmed | When a similar situation repeats, promote it to `confirmed` |

---
depends_on:
  - framework/skills/framework-meta/skill-editing-from-project/SKILL.md
---
