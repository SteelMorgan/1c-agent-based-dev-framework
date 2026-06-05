---
name: brainstorm
description: Use for structured brainstorming when choosing an approach, searching for alternatives, or explicitly invoking /brainstorm. Helps unfold the hypothesis space and distill the result into 3 different viable options, fighting LLM mode collapse.
---

# brainstorm — structured brainstorming

> The goal is to expand the full solution space for the task, not to produce the first plausible idea. The skill fights the typical LLM pathology: giving a "smooth answer from the distribution mode" and losing alternatives that might turn out better.

---

## When to apply

| Trigger | Action |
|---------|--------|
| User invoked `/brainstorm <topic>` | Go through phases 0–5, optionally 6 |
| A request like "what's the best way to do X", "what options are there", "let's think about Y" | Offer to start `/brainstorm`; if agreed, go through the phases |
| Before writing a specification when the approach is not chosen | Use as preparation for Considered Options/ADR |
| In phase 1 of a full cycle (Analyst), the task allows several approaches | Run as an internal analysis step |

### When not to apply

- The question has one correct answer (fact, syntax, documentation)
- The user explicitly said "just do it"
- The task is a fix for a specific bug with a known cause
- There is already an approved specification — this is full-cycle territory, not brainstorming

---

## Principle

Six phases moving from expansion to narrowing, plus an optional seventh (external red team).

```
0. FRAME    — what task are we actually solving?
1. AXES     — along which axes can the solution differ?
2. SAMPLE   — which points in this space will we examine?
3. GENERATE — formulate a hypothesis for each point
4. STRESS   — what breaks it?
5. CONVERGE — three finalists, maximally different
6. EXT-RED  — (optional) red team from another model family
```

**Flexible:** the choice of techniques inside each phase (5 Whys vs JTBD in FRAME, SCAMPER vs analogies in GENERATE, extremes vs Latin square in SAMPLE). This is a guide, not dogma.

**Rigid:** the order of the phases and the **gates between them** (see the next section). Skipping them is forbidden.

---

## Principle of dialogue — critical requirement

> Brainstorm is **collaborative** work with the user, not an agent monologue. If the agent goes through all phases on its own and outputs the final result, that is not brainstorming, it is a presentation in disguise. The purpose of the skill is lost at that exact moment: the user does not have time to adjust the axes before hypotheses are already generated along them.

### MUST

| Requirement | Description |
|-----------|----------|
| Do not dump everything in one message | It is forbidden to go through 2+ phases in a row without user feedback |
| Stop after each mandatory phase | Output the result → ask a specific question → **wait for an answer** before the next phase |
| Minimum 4 STOPs per session | Phase 0, Phase 1, Phase 3+4, Phase 5 are mandatory gates |
| Use `AskUserQuestion` for discrete gates | Choosing a finalist, adding axes from a predefined set, choosing a sampling strategy are closed questions. An open response is a normal text question |
| Ask a question, then stop | A text question without a stop is rhetorical. Do not use it as decoration for a monologue |

### Gate table

| After phase | What to show | What to ask | STOP |
|-----------|--------------|-------------|------|
| **0 FRAME** | Rephrased task + success criteria + constraints | "Is the framing correct? What should be adjusted?" | ✓ required |
| **1 AXES** | List of axes and values | "Are all important axes present? What is missing?" | ✓ required |
| 2 SAMPLE | Sampling strategy + list of points | (optional) "Add a wild point from a specific domain?" | can continue |
| **3+4 GENERATE+STRESS** | Hypotheses with stress tests | "Which should be developed further? What should be cut?" | ✓ required |
| **5 CONVERGE** | 3 finalists + recommendation | "Which one do you choose?" | ✓ required |

Phase 2 is the only optional stop: if the user has already confirmed the axes, sampling points is the agent's technical work, and it can be shown together with the start of Phase 3+4 in one block. Phases 0, 1, 3+4, 5 — STOP is required.

### Self-check before sending a message

Before sending a message to the user, the agent checks:

- [ ] Does the message contain the result of **one** mandatory phase (or one plus optional Phase 2)?
- [ ] Does the message end with a specific question that expects an answer?
- [ ] Have hypotheses been generated along axes the user has not yet confirmed?

If at least one item is "no", rewrite the message and trim it to the current phase.

---

## Phases

### Phase 0 — FRAME (framing)

Before generating anything, make sure we are solving the right problem.

- Rephrase the request in your own words and show it to the user so they can confirm or correct it
- Identify the **success criteria**: how will we know the solution is good?
- Fix the constraints: time budget, technology stack, audience, non-functional requirements
- If the request is vague, do a short 5 Whys or Jobs-to-be-Done reformulation
- Classify the task type: divergent (need expansion) / convergent (need a choice from known options) / mixed

**Signal to skip the phase:** the task is clear, the criteria are explicit, and the constraints are known. Even in this case, do a short check-in "I understand the task as follows, shall I continue?" before Phase 1.

**Anti-pattern:** jumping into idea generation without confirming that we are solving the right thing. This is the most common reason for a useless brainstorm.

> **[STOP] After Phase 0.** Output: rephrasing + criteria + constraints. Question: "Is the framing correct? What should be adjusted?" **Do not move to Phase 1 without the user's answer.**

### Phase 1 — AXES (axes of diversity)

Extract 3–6 axes along which solutions can differ in principle. This is Zwicky's morphological analysis.

- On each axis, there should be 2–4 values
- **Critically:** check orthogonality. Axes must not predefine one another. If choosing a value on axis A automatically fixes axis B, that is one axis, not two
- Good axes for technical tasks: "where the logic lives", "synchronous/asynchronous", "level of coupling", "who initiates", "data model", "timing of validation"
- Good axes for product tasks: "who the user is", "moment in the flow", "explicit vs implicit action", "reversibility", "level of automation"

**Anti-pattern:** marketing axes ("simple vs complex", "better vs faster"). These are not axes, they are assessments.

> **[STOP] After Phase 1.** Output: list of axes and their values. Question: "Are all important axes present? What is missing?" **Do not move to Phase 2 without an answer.** Generating hypotheses along unconfirmed axes means burning a round for nothing.

### Phase 2 — SAMPLE (sampling points in the space)

A full grid is rarely needed (5 axes × 3 values = 243 combinations). Choose a coverage strategy:

- **Extremes** — combinations of extreme values on each axis (covers boundaries)
- **Latin square** — even coverage with a limited budget
- **Random with anchors** — a couple of extremes plus random combinations
- **Full grid** — only if there are few axes and combinations ≤ 8

Default budget: **6–9 points**. Fewer than 5 means we lose coverage; more than 10 means we lose attention.

It is **useful to add** to the selected points:
- 1 "wild" point via an analogy from another domain (how this problem is solved in [biology / military logistics / music / another industry])
- 1 "provocation" — an intentionally absurd combination as a seed (see `references/prompt-techniques.md` → Provocation)

> **Optional stop after Phase 2.** If the user is engaged and it makes sense, ask "Add a wild point from a specific domain?" If not, move to Phase 3 without a separate message, and combine the sampling output with the hypotheses in Phase 3+4.

### Phase 3 — GENERATE (hypothesis generation)

For each point, formulate a solution hypothesis. The main risks here are anchoring and diversity theater.

- Generate one hypothesis at a time, explicitly referring to its coordinates on the axes. Do not output them as a list in a single paragraph — that anchors on the first one
- For each hypothesis: **essence in 2–3 sentences** + **one key mechanism** + **what it relies on**
- Do not evaluate in this phase — evaluation kills divergent thinking
- If two hypotheses from different points end up semantically identical, one of the axes was not real; go back to Phase 1
- Optional: 1 worst-idea for completeness (often, inverting the bad one yields something unexpectedly good)

See `references/prompt-techniques.md` for specific generation techniques (SCAMPER, analogies, reversal, first principles).

### Phase 4 — STRESS-TEST (stress testing)

A short pass over all hypotheses.

- **Pre-mortem** for each: "imagine we built this and a year later it failed - why?"
- **Hidden assumptions**: "what must be true for this to work?"
- Filter out obviously non-viable options, with explicit justification, not silently
- Optional (in a hard case) — a separate call in the role of critic/red-team for the shortlist

**Anti-pattern:** doing the stress test in the same role/context as the generation. The internal critic is lazy. Better to shift the frame explicitly ("now I am a skeptical investor").

> **[STOP] After Phase 3+4.** Output: hypotheses with stress tests (can be combined with the sampling from Phase 2). Question: "Which hypotheses should be developed further? What should be cut right away?" **Do not move to Phase 5 without an answer.** Convergence on an unfiltered list chosen by the user means the choice is left to the user, which breaks the purpose of the skill.

### Phase 5 — CONVERGE (converging to 3)

The final output is **three** options. Not five, not one.

- Explicit criteria with weights, **fixed BEFORE evaluation** (otherwise it is tuning the result to the favorite)
- Selection rule: "quality ≥ threshold AND maximally different along the axes". Not "top 3 by total score" — that would give three variants of the same point
- For each finalist: essence, coordinates on the axes, risks from Phase 4, falsifier, estimate of effort
- Short list of "rejected branches" (1–2 lines each) — why NOT them

The final output **MUST** match the template in `references/output-template.md` — it is compatible with the Considered Options section in specifications by `spec-standard`.

> **[STOP] After Phase 5.** Output: 3 finalists + recommendation + rejected branches. Question: "Which one do you choose?" — via `AskUserQuestion` with three options. **Do not act on your own recommendation until the user has chosen.**

### Phase 6 — EXT-RED (optional external red team)

For hard tasks (architectural choice, product strategy, expensive mistake) — the finalists are run through `cross-provider-review` in `advisory` mode as a critique idea.

**When to apply:**
- Architectural decision with long-term consequences
- Choice of external dependency / vendor lock-in
- A decision that will be hard to roll back
- The user explicitly asked for a "second opinion"

**When not to apply:**
- A light brainstorm on a UX micro-decision
- A decision in a reversible area (can be redone in a day)
- Time is more expensive than quality

**What to provide:**
- The Phase 5 final in `output-template.md` format
- WITHOUT generation context (axes, rejected branches, reasoning) — to get an independent assessment
- Explicit instruction: "red team role, find the weak points of each of the 3 options, do not balance them with positives"

The red team result is a separate section in the final output, and does **not** automatically change the recommendation. The decision based on the result remains with the user.

---

## Memory between sessions

Brainstorming on one task often goes through multiple passes. To avoid repeating rejected branches and losing axes, the skill keeps lightweight context.

### Where to save

| Context | Path |
|---------|------|
| Brainstorm is tied to a task in `tasks/<id>/` | `tasks/<id>/.context/brainstorm.md` |
| Brainstorm without a task (free discussion) | `.context/brainstorm-<topic-slug>.md` in the current working directory |
| Universal brainstorm patterns for the project | `MEMORY.md` through the standard memory mechanism (if present) |

### What to save

- **Task framing** (after Phase 0) — as confirmed by the user
- **Axes and values** (Phase 1) — so they do not need to be re-extracted when resuming
- **Rejected branches** (Phase 5) — a list with the justification "why NOT"
- **Finalists** — 3 options in `output-template` format
- **Date and iteration** — to understand recency

### What not to save

- Full reasoning logs for each hypothesis (bloats context)
- Intermediate ratings before weighted criteria
- Direct quotes from LLM answers

### Resume behavior

1. At the start of `/brainstorm`, check whether there is a relevant file for the task context
2. If there is, read it and show the user a short summary ("last time we discussed these axes, rejected X and Y, and stopped at 3 finalists")
3. Ask: continue from the same point / expand the axes / start over?
4. If "start over", rename the old file to `brainstorm.<date>.md` for archiving, do not delete it

### Memory file template

See `references/output-template.md` — it is used both as the final output format and as the memory file format. Add this at the top of the file:

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
|--------|--------|
| All three finalists are similar | The axes were not orthogonal — go back to Phase 1 |
| Cannot extract 3 axes | The task is either trivial (no brainstorm needed) or poorly framed (go back to Phase 0) |
| Hypotheses come out "correct and boring" | Add an analogy from another domain or a provocation in Phase 3 |
| The user says "but I meant something else" | Phase 0 was skipped or done poorly — redo the framing |
| The finalists are all variants of the same thing | Convergence was done by total score instead of a diversity-aware rule |
| The decision is hard and long-term | Enable Phase 6 (external red team) |
| The brainstorm resumes on the same topic | Read the memory file first, then expand; do not repeat |
| The agent went through 2+ mandatory phases in one message | The principle of dialogue was violated. Roll back to the point of the last real check-in with the user, apologize, and continue with gates |
| The agent asked a question but in the same message already gave the answer for the next phase | The question is rhetorical — it does not count. Rewrite the message, cut it off at the question, wait for the answer |
| The final result of 3 finalists was obtained without user participation at gates 0/1/3+4 | This is not brainstorming, it is a presentation. Admit the mistake, ask at which gate to roll back, and replay |

---

## User communication

The basic dialogue rule is described above in the **"Principle of dialogue"** section (gate table + self-check). Here are the format additions:

- At each gate, keep the message short, not a lecture. 3–10 lines of output + 1 question
- For discrete choices (finalist, sampling strategy), use `AskUserQuestion` instead of a text question
- The final result should be compact: 3 options in a single format (see `references/output-template.md`), rejected branches as a list, recommendation with justification
- When Phase 6 is enabled, explicitly warn: "I will send the finalists to external criticism; this will take N minutes"
- If the user explicitly says "no gates, let's do everything at once", that is their conscious refusal, record it and continue. But do **not** assume such a refusal silently

---

## Optional: role separation

For hard tasks, you can delegate phases to different agents:

- **Generator** (Phase 3) — high temperature, focus on diversity
- **Critic** (Phase 4) — separate challenge in the role of a skeptic, without seeing the generation
- **Synthesizer** (Phase 5) — independent evaluation by criteria

This is more expensive, but results in less "diversity theater". For lightweight tasks, one agent goes through all phases on its own.

---

## Relationship to other skills and rules

| Skill/rule | Relationship |
|------------|--------------|
| `spec-standard` | The brainstorm final output maps to the Considered Options/ADR section of the specification in `output-template.md` format |
| `cross-provider-review` | Used in Phase 6 (optionally) for an external red team of finalists |
| `framework/workflows/full-cycle.md` | In phase 1 (Analyst), brainstorm is an internal analysis step |
| `agent-context-protocol` | The `brainstorm.md` memory file lives next to `{role}-context.md` in `.context/` |

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/skills/tool-usage/review/cross-provider-review/SKILL.md
  - framework/rules/agent-context-protocol.md
references:
  - references/prompt-techniques.md
  - references/output-template.md
---
