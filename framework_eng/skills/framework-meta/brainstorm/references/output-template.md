# Final Brainstorm Output Template

> Used both as the format of the final response to the user in Phase 5 and as the format of the `brainstorm.md` memory file. Compatible with the "Considered Options" section in the `spec-standard` specifications.

## Full Template

```markdown
# Brainstorm: <short topic name>

**Task:** <task ID or topic slug>
**Iteration:** <N>
**Date:** <YYYY-MM-DD>
**Status:** in_progress | finalized | archived

---

## Problem Statement

<1-3 sentences describing how the user confirmed the wording in Phase 0.
State what is NOT in scope - this establishes the boundaries.>

### Success Criteria

- <Criterion 1 - observable, verifiable>
- <Criterion 2>
- <Criterion 3>

### Constraints

- <Constraint 1: technical / budget / time>
- <Constraint 2>

---

## Diversity Axes

| Axis | Description | Values |
|-----|----------|----------|
| <axis 1> | <what this axis characterizes> | <v1>, <v2>, <v3> |
| <axis 2> | <...> | <v1>, <v2> |
| <axis 3> | <...> | <v1>, <v2>, <v3>, <v4> |

**Orthogonality check:** <one sentence stating that the axes do not determine one another>.

---

## Finalists

### Option A: <short name>

- **Coordinates:** axis1=<v>, axis2=<v>, axis3=<v>
- **Essence:** <2-3 sentences - what it is, how it works>
- **Key mechanism:** <one idea that the solution rests on>
- **Strengths:**
  - <plus 1>
  - <plus 2>
- **Risks (from pre-mortem):**
  - <risk 1: what could go wrong>
  - <risk 2>
- **Falsifier:** <what must happen for it to become clear that the option is bad>
- **Effort estimate:** <S/M/L/XL or person-days>

### Option B: <short name>

<same block>

### Option C: <short name>

<same block>

---

## Rejected Branches

| Branch | Coordinates | Why NOT |
|-------|------------|-----------|
| <name> | axis1=<v>, axis2=<v> | <one sentence> |
| <name> | <...> | <...> |
| <name> | <...> | <...> |

---

## Recommendation

<2-4 sentences: which option is recommended and why. Explicitly state
which criterion it beats the other two on. If there is no recommendation
(the choice is up to the user) - say so honestly, and specify the axis
on which the options are not comparable without a business decision.>

---

## External red team (if Phase 6 was run)

**Reviewer:** <model/family>
**Date:** <YYYY-MM-DD>

### Comments on Option A
- <comment 1>
- <comment 2>

### Comments on Option B
- <comment 1>

### Comments on Option C
- <comment 1>
- <comment 2>

### Response to comments
<2-3 sentences: what we agree with, what we do not, whether it affects the recommendation>

---

## Iteration Log

- **Iteration 1 (<date>):** <what was done - for example, "extracted 4 axes, generated 8 hypotheses">
- **Iteration 2 (<date>):** <"added axis X, rejected option Y">
```

---

## Minimal Version (for light brainstorms)

When the task does not call for the full format - it is acceptable to omit the `External red team` and `Iteration Log` blocks, and simplify the finalist block to 4 lines (Coordinates / Essence / Strengths / Risks).

```markdown
# Brainstorm: <topic>

**Date:** <YYYY-MM-DD>

## Problem Statement
<...>

## Axes
- <axis 1>: <v1, v2, v3>
- <axis 2>: <v1, v2>

## Finalists

### A: <name> [axis1=v, axis2=v]
- Essence: <...>
- Strengths: <...>
- Risks: <...>

### B: ...
### C: ...

## Rejected
- <...>: <why>
- <...>: <why>

## Recommendation
<...>
```

---

## Adaptation for the Specification (Considered Options)

When transferring the final result into the `Considered Options` section of the `spec-standard` specification:

- "Finalists A/B/C" -> "Option 1/2/3"
- "Coordinates in the axes" -> omit (process details, not decision details)
- "Falsifier" -> combine with "Risks" in the spec
- "Rejected branches" -> section "Other options considered" or ADR
- "Iteration Log" -> do not transfer, it remains in `.context/brainstorm.md` as history

---

## Filling Anti-Patterns

| Anti-pattern | Why it is bad |
|-------------|--------------|
| Finalists differ only in wording, with identical coordinates in the axes | There was no real diversification, the axes do not work |
| The `Falsifier` field is empty or says "if it does not work" | The falsifier must be a specific observable event |
| Rejected branches without justification | It is impossible to tell "considered and rejected" from "forgot to consider" |
| A recommendation without a reference to a criterion from "Success Criteria" | The recommendation is hanging in the air, the reader does not understand the logic |
| Strengths = "flexible, simple, convenient" | Non-informative. A strength is a specific property in a specific situation |
