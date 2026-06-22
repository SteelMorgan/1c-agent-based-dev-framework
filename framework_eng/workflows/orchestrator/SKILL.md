---
name: orchestrator
description: >
  Pointer to the orchestration prompting. After retiering (manifest §6, §7.2), the orchestrator's
  operational prompting (Layer 1 — Lead/dispatcher, Layer 2 — discipline) moved into the MAIN THREAD
  PROFILE framework/subagents/orchestrator.md. Detailed phase mechanics (Layer 3) —
  framework/workflows/full-cycle/SKILL.md. This file is preserved as a stable entry point and a
  carrier of depends_on links; it does NOT duplicate the body of the prompting.
---

# Orchestrator: meta-workflow (pointer)

> **Important.** Previously the entire operational prompting of the orchestrator lived in this
> always-on document and was inherited by subagents — exactly the A-channel bloat that retiering
> removed (see manifest `docs/rules-skills-retiering/manifest.md`, §6, §7.1, §7.2). Now the prompting
> is the durable identity of the main thread in its system prompt, not a loaded document.

## Where everything lives

| Layer | Content | Residence | Durability |
|------|------------|------------------|------------|
| **1. Lead / dispatcher** | classification; short/full cycle selection; for short — self vs delegate under quick-fix guard | **profile** `framework/subagents/orchestrator.md` | durable, every request (main-only) |
| **2. Orchestration discipline** | "I do not execute — I delegate", routing, gates, review-cycle, BUG-routing, escalation filter, cross-provider (§7), Infostart audit (§9), LOG protocol | **profile** `framework/subagents/orchestrator.md` | durable; active only in full mode |
| **3. Detailed phase mechanics** | phases Phase 0…4, artifact handoff, error handling | `framework/workflows/full-cycle/SKILL.md` | read-on-choice (upon entering the phase) |
| **Anchor / cross-harness bridge** | thin self-promoting stub that raises the profile on any harness | `framework/rules/framework-bootstrap/SKILL.md` (always-on) | survives compact, re-triggers |

## How this works in the flow

1. The main thread starts under the orchestrator profile (`--append-system-prompt` — recommended
   default, or `--agent orchestrator`; see profile § "Launch method" and manifest §6.1). On harnesses
   without these flags, the profile brings up the portable `framework-bootstrap` stub (manifest §7.3).
2. Lead classifies the task (Layer 1 of the profile) → chooses short (`quick-fix` skill) or full.
3. In full mode, the orchestrator operates by Layer 2 discipline (profile) and loads detailed phase
   mechanics from `full-cycle.md` upon entering each phase.
4. "Escalation quick-fix → full" = the orchestrator raises phase prompting in itself (it is already in
   the profile), rather than handing it off to an external document.

## The "do not execute yourself" prohibition is scoped to full mode

The main agent is **Lead**, wearing one of the hats, not "always orchestrator". The prohibition
"orchestrator is NOT executor" applies ONLY in full mode. In Lead/short mode, main executes itself or
delegates one subagent within quick-fix boundaries (`< 20 lines, 1 file, no new metadata objects, no
architecture`) with a mandatory verify step. The full wording is in the profile (Layer 1 §1.3,
Layer 2 "PROHIBITED").

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
