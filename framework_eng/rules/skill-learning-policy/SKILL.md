---
name: skill-learning-policy
description: Knowledge accumulation, two triggers. WRITE — after a cycle with ≥2 iterations, perform a retrospective. READ — before working with a skill, read its references/learned-patterns.md. Procedure and write format → skill-learning.
alwaysApply: true
---
# Knowledge Accumulation Policy (Skill Learning)

## TRIGGER — WRITE a lesson (MUST, strict)

> A work cycle with **≥2 iterations** has completed (wrote → error → found the correct path → success) → a retrospective is **MANDATORY**. Apply the `skill-learning` skill (`framework/skills/agent-process/skill-learning/SKILL.md`) and perform it.
>
> The task was solved on the first attempt → a retrospective is NOT needed, the trigger does not fire.
>
> **Trigger acceptance:** in full-cycle, skipping the retrospective is recorded by the orchestrator during phase acceptance. In quick-fix/FREE there is no orchestrator acceptance → the agent confirms completion itself with a line in `{role}-context.md` / report: `SKILL_LEARNING: matched|refined|new|skipped(<reason>)`.

## TRIGGER — READ lessons (before working with a skill)

> Before starting work with any skill, read the accumulated lessons of the owning skill, BEFORE design/implementation:
> - `<skill>/references/learned-patterns.md` — universal techniques;
> - `{project}/.context/learned-patterns.md` — project techniques.
>
> Apply `confirmed` as additional rules, `candidate` as hints. If the file does not exist → skip it. The trigger is phase-based: when entering work on a skill, not on every turn.

## MUST (invariant, always)

- **Abstraction level above the incident.** A lesson describes a named CLASS of error + an anti-recipe (a generalized prohibition/check), NOT a specific incident. Specific `file:line` / symbol names from that incident go as a "source example", NOT as the body of the rule.
- **Abstraction test (record acceptance).** The anti-recipe must apply to at least ≥1 OTHER hypothetical situation besides the one that created it. If it does not apply, it is an instance: rephrase it higher or reject it.
- **Update check before writing.** Compare the incident with existing recipes (`references/learned-patterns.md` of the owning skill + `{project}/.context/learned-patterns.md`). If it falls under an existing class, update it (candidate→confirmed, expand the boundaries of the anti-recipe, add the source), do NOT create a duplicate. If it does not fit, create a new class. The outcome is recorded explicitly: `matched | refined | new`.
- Entries are written ONLY to `references/learned-patterns.md` of the owning skill (universal) or `{project}/.context/learned-patterns.md` (project) — NOT into the body of `SKILL.md`.
- **The target directory for the universal lesson is the RU source of the framework** (the owning skill directory in `framework/`, NOT the installed ENG symlink `framework_eng/`: otherwise the lesson is lost during synchronization). Search for the directory and synchronize to ENG via the `skill-editing-from-project` skill. A project lesson (`{project}/.context/learned-patterns.md`) is in Russian, without mapping or synchronization.
- Acceptance and anti-acceptance are one entry; the first case = `candidate`, repetition = `confirmed`.

The retrospective procedure (reconstructing the chain of iterations, abstracting to a class, abstraction test, check-cascade, choosing the level and owning skill, searching the RU directory) is in the `skill-learning` skill.

---
depends_on:
  - framework/skills/agent-process/skill-learning/SKILL.md
  - framework/skills/framework-meta/skill-editing-from-project/SKILL.md
---
