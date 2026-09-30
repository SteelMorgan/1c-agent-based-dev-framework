---
name: orchestrator
description: "Orchestrator: work routing and agent phases"
---

# Orchestrator: meta-workflow (pointer)

> **Important.** Previously, all of the orchestrator's operational maning lived in this always-on document and was inherited
> by subagents — exactly the channel A bloat that tiering eliminated (see the manifest
> `docs/rules-skills-retiering/manifest.md`, §6, §7.1, §7.2). Now, maning is the durable identity
> of the main flow in its system prompt, not a loadable document.

## Where things live

| Layer | Content | Residency | Durability |
|------|------------|------------------|------------|
| **1. Lead / dispatcher** | classification; choosing the short/full cycle; for short — self vs delegate under the quick-fix guard | **profile** `framework/subagents/orchestrator.md` | durable, every request (main-only) |
| **2. Orchestration discipline** | "I do not execute — I delegate", routing, gates, review-cycle, BUG-routing, escalation filter, cross-provider (§7), Infostart audit (§9), LOG protocol | **profile** `framework/subagents/orchestrator.md` | durable; active only in full mode |
| **3. Detailed phase mechanics** | phases Phase 0…4, artifact handoff, error handling | `framework/workflows/full-cycle/SKILL.md` | read-on-choice (on entering a phase) |
| **Anchor / cross-harness bridge** | a thin self-promoting stub that lifts the profile on any harness | `framework/rules/framework-bootstrap/SKILL.md` (always-on) | survives compaction, retriggers |

## How it works in the flow

1. The main flow starts under the orchestrator profile (`--append-system-prompt` is the recommended default,
   or `--agent orchestrator`; see profile § "Launch method" and manifest §6.1). On harnesses without these
   flags, the profile brings up the portable `framework-bootstrap` stub (manifest §7.3).
2. Lead classifies the task (Layer 1 of the profile) → chooses short (`quick-fix`) or full.
3. In full mode, the orchestrator works according to Layer 2 discipline (profile) and brings up detailed phase
   mechanics from `full-cycle.md` when entering each phase.
4. "quick-fix → full escalation" = the orchestrator raises the phase maning itself (it is already in the profile),
   rather than handing it off to an external document.

## Self-execution prohibition — scoped to full-mode

The main agent is **Lead**, wearing one of the hats, not "always the orchestrator." The prohibition
"the orchestrator is NOT the executor" applies ONLY in full-mode. In Lead/short-mode, main executes itself
or delegates a single subagent within the quick-fix boundaries (`< 20 lines, 1 file, no new metadata
objects, no architecture`) with a mandatory verify step. The full wording is in the profile
(Layer 1 §1.3, Layer 2 "PROHIBITED").

---
depends_on:
  - framework/subagents/orchestrator.md
  - framework/workflows/full-cycle/SKILL.md
  - framework/skills/agent-process/quick-fix/SKILL.md
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
  - framework/rules/cross-provider-review/SKILL.md
  - framework/rules/herdr-agent-routing/SKILL.md
  - framework/skills/framework-meta/agent-development-ext/SKILL.md
  - framework/skills/agent-process/analyst-controller/SKILL.md
  - framework/skills/agent-process/architect-controller/SKILL.md
  - framework/skills/agent-process/reviewer-controller/SKILL.md
  - framework/skills/tool-usage/review/review-swarm/SKILL.md
  - framework/subagents/scenario-author.md
  - framework/subagents/scenario-coder.md
  - framework/subagents/debugger.md
  - framework/skills/tool-usage/diagnostics/bug-reporting/SKILL.md
  - framework/skills/tool-usage/diagnostics/runtime-investigation/SKILL.md
---
