---
name: escalation-format
description: MUST use BEFORE sending an escalation message to the user. Defines the structure (What → Why → Options → Assessment → Recommendation), grouping rules, and prohibitions.
alwaysApply: false
---

# Escalation Format for the User

> This skill defines the **format** of an escalation message. The **“when to escalate” filter** is separate: it is in `framework/workflows/orchestrator.md` § 4 “Principle ‘delegate, don’t ask’”. Before applying the format, you MUST pass this filter and make sure the escalation is legitimate (admin operation / L1-L2 contract change / business decision / 3+ BLOCK / scope expansion). Technical choices and escalation diagnostics do NOT require it - they are solved by delegating to a subagent. Applying the format below to a technical question is an error.

## Hard

- **For each escalated issue, the following structure is mandatory:**
  1. **What:** one sentence describing the issue itself.
  2. **Why this is a problem:** a brief explanation of the reasons - what exactly does not work / is violated / conflicts. Include specific `file:line` links or REQ-ID so the user can verify.
  3. **Solution options:** list realistic options (at least 2), numbered (A, B, C...).
  4. **Assessment of each option:** for each one - pros / cons / impact on affected artifacts (spec, design, tests, code), including the cost of changes and risks.
  5. **Recommendation:** if there is one, state your choice and rationale. If the choice is not obvious, explicitly say “the choice is up to the user”.

- **It is forbidden** to escalate “a single question without context”. If the user does not understand what problems stand behind the question, they cannot choose.

- **It is forbidden** to hide options in a single phrase like “accept or change the spec”. Each option must be expanded - what changes, where, and at what cost.

- **Grouping**: if several related issues are escalated, format each one as a separate block with a heading; do not dump everything into one paragraph.

## Soft

- Use markdown tables to compare options if there are 3+.
- Try to keep the answer to 1-2 screens; if there are many issues, highlight the Top-3 critical ones and list the rest below.
- If the issue has business meaning (UX, load, security), make sure to reflect that in the assessment of options, not only the technical side.

## Example (good)

> ### Problem 1 - REQ-X conflicts with REQ-Y
>
> **What:** REQ-X requires behavior A, REQ-Y requires not-A.
>
> **Why this is a problem:** when attempting to test (`spec.md:142`), UT-X-01 expects behavior A, while UT-Y-02 expects not-A. Developer-Code cannot satisfy both tests at the same time. This is a violation of source-of-truth-policy level L2.
>
> **Options:**
> - **A.** Remove REQ-Y. Pros: the spec becomes consistent, minimal rework. Cons: the business case “not-A” is lost.
> - **B.** Rewrite REQ-X as SHOULD. Pros: both requirements can coexist. Cons: the A contract becomes optional - Developer-Code may bypass it.
> - **C.** Split the context: A applies in case K1, not-A applies in K2. Pros: both business cases are preserved. Cons: spec change + new ADR + design change (3-5 places) + 2 new unit tests.
>
> **Recommendation:** C - the only option that preserves both business meanings. The cost of changes is acceptable.

## Example (bad)

> “There is a contradiction in the spec. Fix it or leave it?”

— No explanation of the reasons, no options, no assessment. Forbidden.

---
depends_on:
  - framework/workflows/orchestrator.md
---
