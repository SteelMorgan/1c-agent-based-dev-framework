---
name: skill-learning-policy
description: Knowledge accumulation, two triggers. WRITE — after a cycle with ≥2 iterations perform a retrospective. READ — before working with a skill, read its references/learned-patterns.md. Procedure and write format → skill-learning.
alwaysApply: true
---
# Knowledge Accumulation Policy (Skill Learning)

## TRIGGER — WRITE a lesson

> A work cycle with **≥2 iterations** has completed (wrote → error → fixed → success). When triggered, apply the `skill-learning` skill (`framework/skills/framework-meta/skill-learning/SKILL.md`) and perform a retrospective.
>
> The task was solved on the first attempt → a retrospective is NOT needed, the trigger does not fire.

## TRIGGER — READ lessons (before working with a skill)

> Before starting work with any skill, read the accumulated lessons of the owning skill, BEFORE design/implementation:
> - `<skill>/references/learned-patterns.md` — universal patterns;
> - `{project}/.context/learned-patterns.md` — project patterns.
>
> Apply `confirmed` as additional rules, `candidate` as hints. If the file does not exist → skip it. The trigger is phase-based: when entering work on a skill, not on every turn.

## MUST (invariant, always)

- Accumulated knowledge entries are written ONLY to `references/learned-patterns.md` of the owning skill (universal) or `{project}/.context/learned-patterns.md` (project) — NOT into the body of `SKILL.md`.
- Before writing, read the existing patterns — do not duplicate; when repeated, raise `candidate` with the same scope to `confirmed`.
- First occurrence = `candidate`; repetition of a similar situation = `confirmed`.
- Pattern and anti-pattern are one entry (two sides of the same discovery).

The retrospective procedure (reconstructing the iteration chain, write format, filtering, choosing the level and owning skill) is in the `skill-learning` skill.

---
depends_on:
  - skill-learning
---
