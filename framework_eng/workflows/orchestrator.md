---
name: orchestrator
description: >
  Pointer to the orchestration prompt. After retiering (manifest §6, §7.2) the operational prompt
  of the orchestrator (Layer 1 — Lead/dispatcher, Layer 2 — discipline) moved into the MAIN PROFILE
  `framework/subagents/orchestrator.md`. Detailed phase mechanics (Layer 3) — `framework/workflows/full-cycle.md`.
  This file is preserved as a stable entry point and carrier of depends_on links; it does NOT duplicate the prompt body.
---

# Orchestrator: meta-workflow (pointer)

> **Important.** Earlier the entire operational prompt of the orchestrator lived in this always-on document and was inherited
> by subagents — exactly the channel-A bloat that retiering eliminated (see the manifest
> `docs/rules-skills-retiering/manifest.md`, §6, §7.1, §7.2). Now the prompt is the durable identity
> of the main flow in its system prompt, not a loaded document.

## Where everything lives

| Layer | Content | Location | Durability |
|------|------------|------------------|------------|
| **1. Lead / dispatcher** | classification; choice of short/full cycle; for short — self vs delegate under quick-fix guard | **profile** `framework/subagents/orchestrator.md` | durable, every request (main-only) |
| **2. Orchestration discipline** | "I do not execute — I delegate", routing, gates, review-cycle, BUG-routing, escalation filter, cross-provider (§7), Infostart audit (§9), LOG protocol | **profile** `framework/subagents/orchestrator.md` | durable; active only in full mode |
| **3. Detailed phase mechanics** | phases Phase 0…4, artifact handoff, error handling | `framework/workflows/full-cycle.md` | read-on-choice (upon entering the phase) |
| **Anchor / cross-harness bridge** | a thin self-promoting stub that raises the profile on any harness | `framework/rules/framework-bootstrap.md` (always-on) | survives compact, re-triggers |

## How this works in the flow

1. The main flow starts under the orchestrator profile (`--append-system-prompt` is the recommended default,
   or `--agent orchestrator`; see profile § "Launch method" and manifesto §6.1). On harnesses without these
   flags the profile raises the portable `framework-bootstrap` stub (manifest §7.3).
2. Lead classifies the task (Layer 1 of the profile) → chooses short (`quick-fix` skill) or full.
3. In full mode the orchestrator operates by Layer 2 discipline (profile) and raises detailed phase mechanics
   from `full-cycle.md` upon entering each phase.
4. "quick-fix → full escalation" = the orchestrator raises the phase prompt in itself (it is already in the profile),
   rather than handing off to an external document.

## The prohibition "do not execute yourself" is scoped to full mode

The main agent is the **Lead**, putting on one of the hats, not "always the orchestrator". The prohibition
"orchestrator is NOT an executor" applies ONLY in full mode. In Lead/short mode the main executes itself
or delegates one subagent within quick-fix bounds (`< 20 lines, 1 file, no new metadata objects, no architecture`)
with a mandatory verify step. Full wording — in the profile
(Layer 1 §1.3, Layer 2 "PROHIBITED").

---
depends_on:
  - framework/subagents/orchestrator.md
  - framework/workflows/full-cycle.md
  - framework/skills/framework-meta/quick-fix/SKILL.md
  - framework/rules/agent-context-protocol.md
  - framework/rules/source-of-truth.md
  - framework/skills/tool-usage/review/cross-provider-review/SKILL.md
  - framework/subagents/scenario-author.md
  - framework/subagents/scenario-coder.md
  - framework/subagents/debugger.md
  - framework/skills/tool-usage/diagnostics/bug-reporting/SKILL.md
  - framework/skills/tool-usage/diagnostics/runtime-investigation/SKILL.md
---
