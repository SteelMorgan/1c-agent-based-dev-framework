---
name: skill-learning-policy
description: "Before skill use and after iterations, update learned patterns"
alwaysApply: true
---
# Knowledge Accumulation Policy (Skill Learning)

## TRIGGER — RECORD a lesson (MUST, strict)

> Completed a work cycle with **≥2 iterations** (wrote → error → found the correct path → success) → a retrospective is **MANDATORY**. Apply the `skill-learning` skill (`framework/skills/agent-process/skill-learning/SKILL.md`) and conduct it.
>
> Task solved on the first attempt → retrospective is NOT needed, trigger does not fire.
>
> **Trigger acceptance:** in a full-cycle, skipping the retrospective is recorded by the orchestrator during phase acceptance. In quick-fix/FREE there is no orchestrator acceptance → the agent confirms completion itself with a line in `{role}-context.md` / report: `SKILL_LEARNING: matched|refined|new|skipped(<reason>)`.

## TRIGGER — READ lessons (before working with a skill)

> Before starting work with any skill — read the accumulated lessons of the skill owner, BEFORE design/implementation:
> - `<skill>/references/learned-patterns.md` — universal techniques;
> - `{project}/.context/learned-patterns.md` — project techniques.
>
> Apply `confirmed` as additional rules, `candidate` as hints. File missing → skip. Phase trigger: on entering work with a skill, not on every turn.

## MUST (invariant, always)

- **Abstraction level above the incident.** The lesson describes a named CLASS of error + anti-recipe (generalized prohibition/check), NOT a specific incident. The specific `file:line` / symbol names of that incident are given as a "source example", NOT as the body of the rule.
- **Abstraction test (record acceptance).** The anti-recipe applies to at least ≥1 OTHER hypothetical situation besides the one that produced it. Not applicable → this is an instance: reformulate higher or reject it.
- **Verification/update before recording.** Compare the incident against existing recipes (`references/learned-patterns.md` of the skill owner + `{project}/.context/learned-patterns.md`). If it falls under an existing class → update it (candidate→confirmed, expand the anti-recipe boundaries, add a source), do NOT create a duplicate. If it does not fit → new class. The outcome is recorded explicitly: `matched | refined | new`.
- Entries are written ONLY to `references/learned-patterns.md` of the skill owner (universal) or `{project}/.context/learned-patterns.md` (project-specific) — NOT in the body of `SKILL.md`.
- **Target directory for a universal lesson is the RU source of the framework** (the skill owner's directory in `framework/`, NOT the installed ENG symlink `framework_eng/`: otherwise the lesson is lost during synchronization). Search for the directory and synchronize to ENG with the `skill-editing-from-project` skill. The project lesson (`{project}/.context/learned-patterns.md`) is in Russian, without mapping or synchronization.
- Acceptance and anti-acceptance are one entry; the first case = `candidate`, repetition = `confirmed`.

Retrospective procedure (reconstructing the iteration chain, abstracting to a class, abstraction test, cascade verification, choosing the level and skill owner, searching the RU directory) is in the `skill-learning` skill.

---
depends_on:
  - framework/skills/agent-process/skill-learning/SKILL.md
  - framework/skills/framework-meta/skill-editing-from-project/SKILL.md
---
