---
name: analyst-controller
installable: true
description: Analyst role controller: primary work through Consilium and lazy standalone fallback only when technical unavailability is proven.
---

# Analyst Controller

The controller selects the execution branch and does not analyze requirements instead of the chosen branch.

## Reading Order

1. Check for `references/primary-consilium.md`. Missing file is `BLOCK`; do not read fallback.
2. Read only `references/primary-consilium.md` and start the primary protocol.
3. Do not open `references/fallback-standalone.md` until the Orchestrator has recorded the canonical `FALLBACK_TRIGGER`.

## Primary outcomes

- `SUCCESS` — the specification and durable Consilium verdict are prepared.
- `CONSILIUM_NOT_APPLICABLE` — no material trade-off exists; the primary branch creates the specification itself and returns the Orchestrator a rationale with links to the inputs. After materialization, the acceptance gate checks the link to the specification section.
- `BLOCK` / `ESCALATE_TO_HUMAN` — a substantive problem; fallback is forbidden.

## Technical fallback

Fallback is allowed only before session creation if machine-readable `doctor --json` simultaneously shows:

- `quorum.ok = false`;
- `excluded` contains at least one entry; an empty list does not allow fallback;
- all exclusion reasons that affected the quorum belong to the allowlist `cli_on_path`, `adapter_help`, `kimi_doctor`, `adapter_probe` in the `failed_check` field;
- the JSON schema is recognized completely, with no unknown reasons.

Before fallback, the Orchestrator is the sole author of the `FALLBACK_TRIGGER` event and provides evidence. Generic exit code, unknown outcome, `stalemate`, `round_limit`, disagreement, quality failure, substantive `BLOCK`, missing reference, and cleanup failure do not allow fallback. If the session has already been created, a successful `close` is required first; a crash after startup without a stable machine-readable reason remains `BLOCK`.

## Permission Boundary

`readonly: true` forbids modifying the project sources, but allows creating task artifacts, running Consilium lifecycle commands through review-harness, and closing the created session. Only focused paths are passed to external models; secrets, the full workspace, and extra evidence files are forbidden.

After an allowed `FALLBACK_TRIGGER`, read `references/fallback-standalone.md` and execute the old contract without mixing it with the partial transcript of the primary branch.

---
depends_on:
  - framework/skills/tool-usage/review/agent-consilium/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/skills/tool-usage/platform-data/platform-data-core/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
  - framework/skills/tool-usage/v8-session-manager/SKILL.md
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/capability-resolution/SKILL.md
  - framework/rules/no-direct-db-access/SKILL.md
  - framework/rules/skill-learning-policy/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
  - framework/rules/skill-reading-protocol/SKILL.md
---
