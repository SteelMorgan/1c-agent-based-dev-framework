---
name: self-recovery-limits
description: "Canonical registry of self-recovery limits for subagents: how many attempts are allowed for self-fix / hypotheses / review and what to do after exhaustion."
alwaysApply: true
---

# Self-Recovery Limits (Canonical Registry)

A single source of numeric limits for subagents. Profiles reference this registry; the numbers in the profiles
duplicate the table and must not diverge from it. When the limit is exhausted, the agent does NOT continue
“blindly” — it switches to the specified action (bug-report or escalation to the orchestrator/user).

| Context | Who | Limit | What after exhaustion |
|----------|-----|-------|----------------------|
| Self-fix in the implementation's own code | developer-code | 2 attempts | create `bug-report.json` → STOP (orchestrator → Debugger) |
| Red gate: the reason for the "green" state before prod code is not found | scenario-coder | 2 attempts | create `bug-report.json` → STOP |
| Fixing a technical test error | tester | 3 attempts | create `bug-report.json` (when the runtime defect is not obvious) → STOP |
| Hypothesis investigation cycle | debugger | 5 hypotheses (+3 by agreement with the orchestrator → max 8) | escalation to the user (`escalated_to_user`) |
| BLOCK iterations of artifact review | reviewer / orchestrator | 3 iterations | escalation to the orchestrator/user |
| Review of the debugger's local fix (scope=debug) | reviewer / orchestrator | 1 iteration | escalation to the orchestrator/user |
| bug → fix → bug again cycle on the same artifact | orchestrator | 2 cycles | escalation to the orchestrator/user |

---
depends_on: []
---
