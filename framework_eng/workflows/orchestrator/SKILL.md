---
name: orchestrator
description: "Orchestrator: routing work and agent phases"
---

# Orchestrator: meta-workflow (pointer)

> **Important.** Previously, the orchestrator's entire operational main-thread handling lived in this always-on document and was inherited
> by subagents - exactly the kind of channel A bloat that retiering eliminated (see the manifest
> `docs/rules-skills-retiering/manifest.md`, §6, §7.1, §7.2). Now, handling is the durable identity
> of the main thread in its system prompt, not a loaded document.

## Where things live

| Layer | Content | Location | Durability |
|------|------------|------------------|------------|
| **1. Lead / dispatcher** | classification; choosing the short/full cycle; for short - self vs delegate under the quick-fix guard | **profile** `framework/subagents/orchestrator.md` | durable, every request (main-only) |
| **2. Orchestration discipline** | "I do not execute - I delegate", routing, gates, review-cycle, BUG-routing, escalation filter, cross-provider (§7), Infostart audit (§9), LOG protocol | **profile** `framework/subagents/orchestrator.md` | durable; active only in full mode |
| **3. Detailed phase mechanics** | phases Phase 0...4, artifact transfer, error handling | `framework/workflows/full-cycle/SKILL.md` | read-on-choice (when entering a phase) |
| **Anchor / cross-harness bridge** | a thin self-promoting stub that raises the profile on any harness | `framework/rules/framework-bootstrap/SKILL.md` (always-on) | survives compact, re-triggers |

## How it works in the flow

1. The main thread starts under the orchestrator profile (`--append-system-prompt` is the recommended default,
   or `--agent orchestrator`; see profile § "Launch method" and manifest §6.1). On harnesses without these
   flags, the profile raises the portable `framework-bootstrap` stub (manifest §7.3).
2. The Lead classifies the task (Layer 1 of the profile) -> chooses short (`quick-fix`) or full.
3. In full mode, the orchestrator works according to Layer 2 discipline (profile) and raises the detailed phase
   mechanics from `full-cycle.md` on entry to each phase.
4. "Escalation quick-fix -> full" = the orchestrator raises the phase handling in itself (it is already in the profile),
   rather than passing it to an external document.

## The "do not execute yourself" prohibition is scoped to full mode

The main agent is **Lead**, putting on one of the hats, not "always the orchestrator". The prohibition
"the orchestrator is NOT an executor" applies ONLY in full mode. In Lead/short mode, main executes itself
or delegates one subagent within the quick-fix bounds (`< 20 lines, 1 file, no new metadata objects,
no architecture`) with a mandatory verify step. The full wording is in the profile
(Layer 1 §1.3, Layer 2 "PROHIBITED").

---
depends_on:
  - framework/subagents/orchestrator.md
  - framework/workflows/full-cycle/SKILL.md
  - framework/skills/agent-process/quick-fix/SKILL.md
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
  - framework/skills/tool-usage/review/cross-provider-review/SKILL.md
  - framework/subagents/scenario-author.md
  - framework/subagents/scenario-coder.md
  - framework/subagents/debugger.md
  - framework/skills/tool-usage/diagnostics/bug-reporting/SKILL.md
  - framework/skills/tool-usage/diagnostics/runtime-investigation/SKILL.md
---
