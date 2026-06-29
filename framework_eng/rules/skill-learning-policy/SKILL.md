---
name: skill-learning-policy
description: "Before skill use and after iterations, update learned patterns"
alwaysApply: true
---
# Knowledge Accumulation Policy (Skill Learning)

## TRIGGER — WRITE a lesson (MUST, strict)

> Completed a work cycle with **≥2 iterations** (wrote → error → found a correct path → success) → retrospective is **MANDATORY**. Apply the `skill-learning` skill (`framework/skills/agent-process/skill-learning/SKILL.md`) and carry it out.
>
> The task was solved on the first try → retrospective is NOT needed, the trigger does not fire.
>
> **Trigger acceptance:** in full-cycle, skipping the retrospective is recorded by the orchestrator during phase acceptance. In quick-fix/FREE there is no orchestrator acceptance → the agent confirms completion itself with a line in `{role}-context.md` / report: `SKILL_LEARNING: matched|refined|new|skipped(<reason>)`.

## TRIGGER — READ lessons (before working with a skill)

> Before starting work with any skill, read the accumulated lessons of the skill owner, BEFORE design/implementation:
> - `<skill>/references/learned-patterns.md` — general techniques;
> - `{project}/.context/learned-patterns.md` — project techniques.
>
> Treat `confirmed` as additional rules, `candidate` as hints. If the file does not exist, skip it. This is a phase trigger: on entering work with a skill, not on every turn.

## MUST (invariant, always)

- **Abstraction level above the incident.** A lesson describes a named CLASS of error + an anti-recipe (generalized prohibition/check), NOT a specific incident. Concrete `file:line` / symbol names from that incident go as a "source example", NOT as the body of the rule.
- **Abstraction test (record acceptance).** The anti-recipe must apply to at least ≥1 OTHER hypothetical situation besides the one that generated it. If not applicable, this is an instance: reformulate it at a higher level or reject it.
- **Reconciliation update before recording.** Reconcile the incident with existing recipes (`references/learned-patterns.md` of the skill owner + `{project}/.context/learned-patterns.md`). If it falls under an existing class, update it (candidate→confirmed, expand the anti-recipe boundaries, add the source), do NOT create a duplicate. If it does not fit, create a new class. The outcome must be recorded explicitly: `matched | refined | new`.
- Records are written ONLY to `references/learned-patterns.md` of the skill owner (general) or `{project}/.context/learned-patterns.md` (project) — NOT into the body of `SKILL.md`.
- **Target directory for a general lesson is the RU source of the framework** (the skill owner's directory in `framework/`, NOT the installed ENG symlink `framework_eng/`: otherwise the lesson is lost during synchronization). Search for the directory and synchronize into ENG using the `skill-editing-from-project` skill. The project lesson (`{project}/.context/learned-patterns.md`) is in Russian, without mapping or synchronization.
- Acceptance and anti-acceptance are one record; the first case is `candidate`, repetition is `confirmed`.

The retrospective procedure (reconstructing the iteration chain, abstracting to a class, abstraction test, reconciliation cascade, choosing the level and skill owner, searching the RU directory) is in the `skill-learning` skill.

---
depends_on:
  - framework/skills/agent-process/skill-learning/SKILL.md
  - framework/skills/framework-meta/skill-editing-from-project/SKILL.md
---
