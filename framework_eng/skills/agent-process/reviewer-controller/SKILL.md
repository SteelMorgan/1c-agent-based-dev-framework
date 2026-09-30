---
name: reviewer-controller
installable: true
description: Reviewer controller: primary check via review-swarm and a lazy standalone fallback when the swarm's technical unavailability has been proven.
---

# Reviewer Controller

## Reading Order

1. Check for the presence of `references/primary-review-swarm.md`; if it is absent, `BLOCK`, do not read the fallback.
2. Read only the primary reference and execute its evidence/gate protocol.
3. `references/fallback-standalone.md` may be read only after the canonical `FALLBACK_TRIGGER` from the Orchestrator.

## Technical Fallback

Fallback is allowed only if the machine-readable preflight simultaneously shows `quorum.ok=false`, a non-empty `excluded`, and the absence of an eligible reviewer solely because of `failed_check` allowlist `cli_on_path`, `adapter_help`, `kimi_doctor`, `adapter_probe`; there must be no unknown reasons. An empty `excluded`, generic exit code, disagreement, findings, `BLOCK_COMPLETION`, exhausted iterations, provider cost/latency, missing reference, and cleanup failure do not allow fallback.

Before the transition, all created Swarm sessions must be successfully closed. The Orchestrator is the sole author of `FALLBACK_TRIGGER`; the Reviewer returns only structured evidence. A partial report/transcript is not passed to standalone fallback.

`readonly: true` prohibits fixing the artifact under review, but allows creating review evidence/session artifacts and performing lifecycle review-swarm. Focused paths and evidence minimization are mandatory.

The Orchestrator must grant the controller process lifecycle write permissions only
in `.swarm-sessions/`, `.review-sandboxes/`, `.swarm-track-record/`, and the evidence
for the current task, while keeping project sources read-only. If the tool profile
does not allow lifecycle execution, return
`BLOCKED_REVIEW_TOOL_PROFILE` with the required paths/commands; this is an explicit BLOCK
of the launch configuration, not grounds for standalone fallback.

---
depends_on:
  - framework/skills/tool-usage/review/review-swarm/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/skills/spec-writing/technical-design-standard/SKILL.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/bsl-practices/test-writing/SKILL.md
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/agent-debug/SKILL.md
  - framework/rules/capability-resolution/SKILL.md
  - framework/rules/dap-bsl-debugger/SKILL.md
  - framework/rules/no-direct-db-access/SKILL.md
  - framework/rules/skill-learning-policy/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
  - framework/rules/test-zero-residue/SKILL.md
  - framework/rules/vanessa-scenario-policy/SKILL.md
  - framework/rules/vanessa-test-isolation-policy/SKILL.md
  - framework/rules/self-recovery-limits/SKILL.md
  - framework/rules/skill-reading-protocol/SKILL.md
---
