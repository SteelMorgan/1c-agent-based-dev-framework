---
name: brainstorm
description: Use for structured brainstorming when choosing an approach, searching for alternatives, or explicitly calling /brainstorm. Helps expand the hypothesis space and converge on 3 distinct viable options, fighting LLM mode collapse.
---

# brainstorm — structured brainstorming

> The goal is to expand the full solution space of the task, not to produce the first plausible idea. This skill fights the typical LLM ailment: giving a "smooth answer from the mode of the distribution," while losing alternatives that might turn out better.

---

## When to use

| Trigger | Action |
|---------|----------|
| The user called `/brainstorm <topic>` | Go through all phases 0–5, optionally 6 |
| A request like "what is the best way to do X", "what options are there", "let's think about Y" | Offer to start `/brainstorm`; if agreed, go through the phases |
| Before writing a specification when no approach has been chosen | Use as preparation for Considered Options/ADR |
| In phase 1 of the full cycle (Analyst), the task allows multiple approaches | Run as an internal analysis step |

### When NOT to use

- The question has only one correct answer (fact, syntax, documentation)
- The user explicitly said "just do it"
- The task is a specific bug fix with a known cause
- There is already an approved specification - that is full-cycle territory, not brainstorming

---

## Principle

Six phases moving from expansion to narrowing, plus an optional seventh (external red team).

```
0. FRAME    — what task are we actually solving?
1. AXES     — along which axes can the solution differ?
2. SAMPLE   — which points in this space will we consider?
3. GENERATE — formulate a hypothesis for each point
4. STRESS   — what breaks it?
5. CONVERGE — three finalists, as different as possible
6. EXT-RED  — (optional) red team from another model family
```

**What is flexible:** the choice of techniques within each phase (5 Whys vs JTBD in FRAME, SCAMPER vs analogies in GENERATE, extrema vs Latin square in SAMPLE). This is guidance, not dogma.

**What is strict:** the order of phases and **the gates between them** (see the next section). Skipping them is prohibited.

---

## Dialogue Principle — a critical requirement

> Brainstorming is **collaborative** work with the user, not the agent's monologue. If the agent goes through all phases on its own and produces the final result, that's not brainstorming, it's a presentation in disguise. The purpose of the skill is lost at that exact moment: the user does not have time to adjust the axes before hypotheses are already generated along those axes.

### MUST

| Requirement | Description |
|-----------|----------|
| Do not dump everything in one message | It is forbidden to go through 2+ phases in a row without user feedback |
| Stop after each mandatory phase | Show result → specific question → **wait for answer** before the next phase |
| At least 4 STOPs per session | Phase 0, Phase 1, Phase 3+4, Phase 5 — mandatory gates |
| Use `AskUserQuestion` for discrete gates | Choosing a finalist, adding axes from a ready-made set, choosing a sampling strategy — these are closed questions. An open answer is a normal text question |
| Asked a question — stop | A text question without stopping = rhetorical. Do not use it as decoration for a monologue |

### Gate Table

| After phase | What to show | What to ask | STOP |
|-----------|--------------|--------------|------|
| **0 FRAME** | Restate task + success criteria + constraints | "Is the framing correct? What should be adjusted?" | ✓ required |
| **1 AXES** | List of axes and values | "Are all important axes included? What's missing?" | ✓ required |
| 2 SAMPLE | Sampling strategy + list of points | (optional) "Add a wild point from a specific domain?" | can continue |
| **3+4 GENERATE+STRESS** | Hypotheses with stress tests | "Which should be developed further? What should be cut?" | ✓ required |
| **5 CONVERGE** | 3 finalists + recommendation | "Which one do you choose?" | ✓ required |

Phase 2 is the only optional stop: if the user has already confirmed the axes, point sampling is technical work by the agent, and it can be shown together with the start of Phase 3+4 in a single block. Phases 0, 1, 3+4, 5 are mandatory STOPs.

### Self-check before sending a message

Before sending a message to the user, the agent checks:

- [ ] Does the message contain the result of **one** required phase (or one plus optional Phase 2)?
- [ ] Is there a specific question at the end of the message that expects an answer?
- [ ] Have hypotheses been generated along dimensions that the user has not yet confirmed?

If at least one item is "no" - rewrite the message and trim it to the current phase.

---

## Phases

### Phase 0 — FRAME (framing)

Before generating anything, make sure we are solving the right problem.

- Rephrase the request in your own words and show it to the user - let them confirm or correct it
- Determine the **success criteria**: how will we know the solution is good?
- Record constraints: time budget, technology stack, audience, non-functional requirements
- If the request is fuzzy - a short 5-Whys or Jobs-to-be-Done rephrasing
- Classify the task type: divergent (need expansion) / convergent (need a choice from known options) / mixed

**Signal to skip the phase:** the task is clear, criteria are explicit, constraints are known. Even in this case - a short check-in "I understand the task as X, continue?" before Phase 1.

**Anti-pattern:** jump into idea generation without confirming that we are solving what is needed. This is the most common cause of useless brainstorming.

> **[STOP] After Phase 0.** Output: rephrasing + criteria + constraints. Question: "Is the framing correct? What should be fixed?". **Do not proceed to Phase 1 without the user's answer.**

### Phase 1 — AXES (axes of diversity)

Extract 3–6 axes along which solutions can differ fundamentally. This is Zwicky's morphological analysis.

- On each axis - 2–4 values
- **Critical:** check orthogonality. Axes must not determine each other. If choosing a value on axis A automatically fixes axis B, then that is one axis, not two
- Good axes for technical tasks: "where the logic lives", "synchronous/asynchronous", "coupling level", "who is the initiator", "data model", "timing of validation"
- Good axes for product tasks: "who the user is", "moment in the flow", "explicit vs implicit action", "reversibility", "automation level"

**Anti-pattern:** marketing axes ("simple vs complex", "better vs faster"). Those are not axes, those are evaluations.

> **[STOP] After Phase 1.** Output: list of axes and their values. Question: "Are all important axes in place? What is missing?". **Do not proceed to Phase 2 without an answer.** Generating hypotheses along unconfirmed axes = wasting a round for nothing.

### Phase 2 — SAMPLE (sampling of space points)

A full grid is rarely needed (5 axes × 3 values = 243 combinations). Choose a coverage strategy:

- **Extremes** — combinations of boundary values on each axis (covers the boundaries)
- **Latin square** — uniform coverage with a limited budget
- **Random with anchors** — a pair of extremes + random combinations
- **Full grid** — only if there are few axes and combinations ≤ 8

Default budget: **6–9 points**. Fewer than 5 — we lose coverage; more than 10 — we lose attention.

It is useful to add to the selected points:
- 1 "wild" point via an analogy from another domain (how this problem is solved in [biology / military logistics / music / another industry])
- 1 "provocation" — an intentionally absurd combination as a seed (see `references/prompt-techniques.md` → Provocation)

> **Optional stop after Phase 2.** If the user is active and it makes sense, ask "add a wild point from a specific domain?" If not, move on to Phase 3 without a separate message, combine the sampling output with the hypotheses in Phase 3+4.

### Phase 3 — GENERATE (hypothesis generation)

For each point, formulate a solution hypothesis. The main risks here are anchoring and diversity theater.

- Generate one hypothesis at a time, explicitly referring to its coordinates on the axes. Do not output them as a list in a single paragraph — that anchors on the first one
- For each hypothesis: **essence in 2–3 sentences** + **one key mechanism** + **what it rests on**
- Do not evaluate in this phase — evaluation kills divergence
- If two hypotheses from different points turn out semantically identical — one of the axes was not real, return to Phase 1
- Optional: 1 worst-idea for completeness (often inverting the bad one yields something unexpectedly good)

See `references/prompt-techniques.md` for specific generation techniques (SCAMPER, analogies, reversal, first-principles).

### Phase 4 — STRESS-TEST (stress testing)

A short pass over all hypotheses.

- **Pre-mortem** for each: “imagine we did this and in a year it failed — why?”
- **Hidden assumptions**: “what must be true for this to work?”
- Filter out clearly nonviable ones — with explicit justification, not silently
- Optional (difficult case) — a separate call in the role of critic/red-team for the shortlist

**Anti-pattern:** performing the stress test in the same role/context as the generation. The internal critic is lazy. Better: an explicit frame shift (“now I’m a skeptical investor”).

> **[STOP] After Phase 3+4.** Output: hypotheses with stress tests (can be combined with a sample from Phase 2). Question: “Which hypotheses should be developed further? What should be cut immediately?”. **Do not proceed to Phase 5 without an answer.** Convergence over a user-unfiltered list = choosing on behalf of the user, which violates the essence of the skill.

### Phase 5 — CONVERGE (converging to 3)

The finale is **three** options. Not five, not one.

- Explicit criteria with weights, **fixed BEFORE evaluation** (otherwise it becomes favorite-fitting)
- Selection rule: “quality ≥ threshold AND maximally different across axes”. Not “top 3 by total score” — that will give three variants of the same point
- For each finalist: essence, coordinates on the axes, risks from Phase 4, falsifier, effort estimate
- Short list of “rejected branches” (1–2 lines each) — why NOT them

The final output **MUST** match the template `references/output-template.md` — it is compatible with the Considered Options section in the `spec-standard` specifications.

> **[STOP] After Phase 5.** Output: 3 finalists + recommendation + rejected branches. Question: “Which do you choose?” — via `AskUserQuestion` with three options. **Do not act on your own recommendation until the user has chosen.**

### Phase 6 — EXT-RED (optional external red team)

For heavyweight tasks (architectural choice, product strategy, costly mistake) — finalists are run through `cross-provider-review` in `advisory` mode as an idea critique.

**When to apply:**
- An architectural decision with long-term consequences
- Choosing an external dependency / vendor lock-in
- A decision that will be hard to roll back
- The user explicitly asked for a "second opinion"

**When NOT to apply:**
- A light brainstorm on a UX micro-decision
- A decision in a reversible area (can be redone in a day)
- Time is more valuable than quality

**What to provide:**
- The final result from Phase 5 in `output-template.md` format
- WITHOUT generation context (axes, rejected options, reasoning) — to get an independent assessment
- Explicit instruction: "red team role, find weak spots in each of the 3 options, do not balance them against the positives"

The red team result is a separate section in the final output, and it does **not** automatically change the recommendation. The decision based on the results is up to the user.

---

## Memory between sessions

A brainstorm on a single task often goes through multiple passes. To avoid repeating rejected branches and losing axes, the skill keeps lightweight context.

### Where to save

| Context | Path |
|----------|------|
| Brainstorm tied to a task in `tasks/<id>/` | `tasks/<id>/.context/brainstorm.md` |
| Brainstorm without a task (free-form discussion) | `.context/brainstorm-<topic-slug>.md` in the current working directory |
| General brainstorm patterns for the project | `MEMORY.md` through the standard memory mechanism (if present) |

### What to save

- **Task statement** (after Phase 0) — as confirmed by the user
- **Axes and values** (Phase 1) — so they do not need to be re-extracted on resume
- **Rejected branches** (Phase 5) — a list with the rationale for "why NOT"
- **Finalists** — 3 options in `output-template` format
- **Date and iteration** — for understanding relevance

### What NOT to save

- Full reasoning log for each hypothesis (bloats the context)
- Intermediate scores before weighted criteria
- Direct quotes from LLM responses

### Behavior on resumption

1. At the start of `/brainstorm` - check whether there is a relevant file for the task context
2. If there is - read it, show the user a brief summary ("last time we discussed these axes, rejected X and Y, and settled on 3 finalists")
3. Ask: continue from the same point / expand the axes / start over?
4. If "start over" - rename the old file to `brainstorm.<date>.md` for archiving, do not delete it

### Memory file template

See `references/output-template.md` - it is used both as the final output format and as the memory file format. Additionally, at the start of the file:

```markdown
# Brainstorm: <topic>

**Task:** <ID or slug>
**Iteration:** <N>
**Date:** <ISO>
**Status:** in_progress | finalized | archived
```

---

## Heuristics

| Signal | Action |
|--------|----------|
| All three finalists are similar | The axes were not orthogonal - go back to Phase 1 |
| Cannot extract 3 axes | The task is either trivial (brainstorm not needed) or poorly framed (go back to Phase 0) |
| The hypotheses turn out "correct and boring" | Add an analogy from another domain or provocation in Phase 3 |
| The user says "but I wanted something else" | Phase 0 was skipped / was of poor quality - redo the framing |
| All finalists are variants of the same thing | Convergence was based on total score rather than a diversity-aware rule |
| The decision is heavy and long-term | Include Phase 6 (external red team) |
| The brainstorm is resumed on the same topic | First read the memory file, then expand, do not repeat |
| The agent passed 2+ mandatory phases in one message | The dialogue principle was violated. Roll back to the point of the last real check-in with the user, apologize, continue with gates |
| The agent asked a question, but in the same message already gave the answer for the next phase | The question is rhetorical - it does not count. Rewrite the message, stop at the question, wait for an answer |
| The final output of 3 finalists was obtained without user involvement at gates 0/1/3+4 | This is not brainstorming, this is a presentation. Admit the mistake, ask which gate to roll back to, replay |

---

## User Communication

The basic dialogue rule is described in the **"Dialogue Principle"** section above (gate table + self-check). Here are the additions to the format:

- At each gate - a short message, not a lecture. 3–10 lines of output + 1 question
- For a discrete choice (finalist, sampling strategy) - `AskUserQuestion` instead of a text question
- Final - concise: 3 options in a unified format (see `references/output-template.md`), rejected branches as a list, recommendation with justification
- When Phase 6 is enabled - explicitly warn: "I will send the finalists for external critique, this will take N minutes"
- If the user explicitly says "no gates, let's do everything at once" - that is their conscious refusal, record it and continue. But do **not** assume such a refusal silently

---

## Optional: Role Separation

For heavy tasks, phases can be delegated to different agents:

- **Generator** (Phase 3) - high temperature, focus on diversity
- **Critic** (Phase 4) - a separate call in the skeptic role, without visibility into generation
- **Synthesizer** (Phase 5) - independent evaluation against criteria

This is more expensive, but produces less "diversity theater." For lightweight tasks, one agent goes through all phases on its own.

---

## Relationship to Other Skills and Rules

| Skill/rule | Relationship |
|---------------|-------|
| `spec-standard` | The brainstorm final is mapped into Considered Options/ADR specifications in the `output-template.md` format |
| `cross-provider-review` | Used in Phase 6 (optional) for external red team critique of finalists |
| `framework/workflows/full-cycle/SKILL.md` | In phase 1 (Analyst), brainstorming is an internal analysis step |
| `agent-context-protocol` | The `brainstorm.md` memory file lives next to `{role}-context.md` in `.context/` |

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/skills/tool-usage/review/review-swarm/SKILL.md
  - framework/rules/agent-context-protocol/SKILL.md
references:
  - references/prompt-techniques.md
  - references/output-template.md
---
