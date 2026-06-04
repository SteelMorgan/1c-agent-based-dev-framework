---
name: skill-learning-policy
description: Knowledge accumulation trigger — after a work cycle with ≥2 iterations (wrote → error → fixed → success), run a retrospective. Recording procedure and format → skill-learning skill.
alwaysApply: true
---
# Knowledge Accumulation Policy (Skill Learning)

> **Trigger:** a work cycle with **≥2 iterations** has completed (wrote → error → fixed → success). When it triggers, apply the `skill-learning` skill (`framework/skills/framework-meta/skill-learning/SKILL.md`) and conduct a retrospective.
>
> If the task is solved on the first attempt, no retrospective is needed and the trigger does not fire.

## MUST (invariant, always)

- Knowledge accumulation entries are written ONLY to `references/learned-patterns.md` of the owning skill (universal) or `{project}/.context/learned-patterns.md` (project-specific) — NOT into the body of `SKILL.md`.
- Before recording, read existing practices — do not duplicate; promote `candidate` with the same scope to `confirmed` if it repeats.
- First occurrence = `candidate`; repeated similar situation = `confirmed`.
- A practice and an anti-practice are a single entry (two sides of one discovery).

Retrospective procedure (reconstructing the iteration chain, recording format, filtering, choosing the level and owning skill) — in the `skill-learning` skill.

---
depends_on:
  - skill-learning
---
