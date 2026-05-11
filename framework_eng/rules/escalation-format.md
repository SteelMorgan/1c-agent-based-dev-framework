# User Escalation Format

> Applies in all cases where the orchestrator or subagent requests a user decision: `clarification_needed`, iteration limit exceeded (3+ BLOCK), an architectural choice without a single clear source, a conflict between source-of-truth levels, etc.

> **IMPORTANT - the "when to escalate" filter is located in `framework/workflows/orchestrator.md` § 4 "The 'delegate, don't ask' principle".** This file defines only the **format** of the message. Before applying it, the orchestrator/subagent MUST pass the filter in orchestrator.md and make sure that the escalation is legitimate (admin operation / L1-L2 contract change / business choice / 3+ BLOCK / scope expansion). Technical choices and escalation diagnostics do NOT require escalation - they are resolved by delegating to a subagent. Applying the format below to a technical question is a mistake.

## Hard

- **Each escalated problem must have the following structure:**
  1. **What:** one sentence describing the problem itself.
  2. **Why this is a problem:** a brief explanation of the reasons - what exactly does not work / is violated / is contradicted. Include concrete `file:line` links or a REQ-ID so the user can verify it.
  3. **Solution options:** list realistic options (minimum 2), numbered (A, B, C...).
  4. **Assessment of each option:** for each one - pros / cons / impact on the affected artifacts (spec, design, tests, code), including the cost of changes and risks.
  5. **Recommendation:** if there is one - state your choice and the rationale. If the choice is not obvious - explicitly say "the choice is up to the user".

- **It is forbidden** to escalate "a single question without context". If the user does not understand what problems stand behind the question, they cannot choose.

- **It is forbidden** to hide options in a single phrase like "accept it or change the spec". Each option must be expanded - what changes, where, and at what cost.

- **Grouping**: if multiple related problems are escalated - format each one as a separate block with a heading; do not dump everything into one paragraph.

## Soft

- Use markdown tables to compare options if there are 3+ of them.
- Try to keep the answer to 1-2 screens; if there are many problems, highlight the Top-3 critical ones and put the rest below.
- If a problem has business meaning (UX, load, security) - be sure to reflect it in the assessment of the options, not only the technical side.

## Example (good)

> ### Problem 1 - REQ-X contradicts REQ-Y
>
> **What:** REQ-X requires behavior A, REQ-Y requires not-A.
>
> **Why this is a problem:** during testing (`spec.md:142`) UT-X-01 expects behavior A, while UT-Y-02 expects not-A. Developer-Code cannot satisfy both tests at the same time. This is a violation of source-of-truth-policy level L2.
>
> **Options:**
> - **A.** Remove REQ-Y. Pros: the spec becomes consistent, minimum rework. Cons: the business case "not-A" is lost.
> - **B.** Rewrite REQ-X as SHOULD. Pros: both requirements can coexist. Cons: the A contract becomes optional - Developer-Code can bypass it.
> - **C.** Split the context: A applies in case K1, not-A in K2. Pros: both business cases are preserved. Cons: spec change + new ADR + design changes (3-5 places) + 2 new unit tests.
>
> **Recommendation:** C - the only option that preserves both business meanings. The change cost is acceptable.

## Example (bad)

> "We have a contradiction in the spec. Fix it or leave it?"

— Without explanation of the reasons, without options, without assessment. Forbidden.

---
depends_on: []
---
