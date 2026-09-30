---
name: architect-controller
installable: true
description: Architect role controller: primary work through Consilium and a lazy standalone fallback only when technical unavailability is proven.
---

# Architect Controller

The controller chooses the execution branch and does not design the solution instead of the selected branch.

## Reading Order

1. Check for the presence of `references/primary-consilium.md`. Missing file means `BLOCK`; do not read the fallback.
2. Read only the primary reference and start the primary protocol.
3. `references/fallback-standalone.md` may be read only after the Orchestrator's canonical `FALLBACK_TRIGGER`.

## Outcomes and Fallback

`SUCCESS`, `CONSILIUM_NOT_APPLICABLE`, a substantive `BLOCK`, and `ESCALATE_TO_HUMAN` are primary outcomes. `CONSILIUM_NOT_APPLICABLE` does not include a fallback: the primary branch creates the design itself.

Technical fallback is allowed only before session creation when `doctor --json: quorum.ok=false`, when `excluded` is non-empty and all influencing reasons have `failed_check` from the allowlist `cli_on_path`, `adapter_help`, `kimi_doctor`, `adapter_probe`. An empty `excluded`, an unknown field/reason, a generic exit code, stalemate, round limit, disagreement, quality failure, missing reference, and cleanup failure result in `BLOCK`. A created session must be closed successfully before any transition.

`readonly: true` means that modifying the project source files is prohibited; task/session artifacts, the Consilium lifecycle, and mandatory cleanup are allowed. Focused paths are required.

After the permitted event, read the fallback reference and follow the legacy contract without a transcript of the primary branch.

---
depends_on:
  - framework/skills/tool-usage/review/agent-consilium/SKILL.md
  - framework/skills/spec-writing/technical-design-standard/SKILL.md
  - framework/skills/spec-writing/task-breakdown/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/platform-data/platform-data-core/SKILL.md
  - framework/skills/bsl-practices/api-design/SKILL.md
  - framework/skills/bsl-practices/integration-patterns/SKILL.md
  - framework/skills/bsl-practices/security/SKILL.md
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/capability-resolution/SKILL.md
  - framework/rules/no-direct-db-access/SKILL.md
  - framework/rules/skill-learning-policy/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
  - framework/rules/skill-reading-protocol/SKILL.md
---
