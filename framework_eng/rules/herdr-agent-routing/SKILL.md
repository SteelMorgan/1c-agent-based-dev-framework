---
name: herdr-agent-routing
description: Read-on-choice Orchestrator contract for routing phase owners across families inside and outside Herdr.
alwaysApply: false
---

# Herdr-aware agent routing

The rule defines only transport selection and ownership. Command syntax and panel management safety come from the vendor skill `herdr`; do not duplicate or modify it.

## Environment check

Before each phase owner launch, compute:

```bash
test "${HERDR_ENV:-}" = 1
```

- `HERDR_ENV=1` — only the first sign of Herdr context. Additionally, `command -v herdr`, `herdr status --json`, and `herdr pane current --current` must successfully confirm an available client/server and the current pane; otherwise return `BLOCKED_HERDR_CONTEXT_INVALID`.
- Any other value or an absent variable — Herdr control is forbidden; use only qualified models from the current harness family.

`current_family` is taken from the harness's actual runtime identity (`claude`, `gpt/codex`, `kimi`), not guessed from the task text. Within Herdr, identity can be confirmed through current pane/agent metadata. Unknown family — `BLOCK`, no silent fallback.

`target_family`, exact model, and effort are taken from the canonical role-routing matrix. The Orchestrator may change effort, but must record the reason and preserve the capability/reviewer floor.

## Algorithm inside Herdr

1. Record in the orchestration trace: role, `current_family`, `target_family`, exact model/effort, route, and rationale.
2. If `target_family == current_family`, launch the current harness's standard native child. This is a full phase owner, not a consultation; pass a self-contained handoff without forking history.
3. If the families differ:
   - read the vendor skill `herdr` and the current CLI help;
   - topology “1 executor = 1 pane = 1 tab”: create a separate tab for the executor in the current workspace/cwd (`herdr tab create ... --no-focus` or `pane move <pane> --new-tab --label <agent-name> --no-focus`) — DO NOT split the Orchestrator's tab; this is an explicit user requirement for topology, overriding the vendor default “sibling pane in current tab”;
   - for `target_family=claude`, check in the target interactive shell that alias `cc` resolves to `/home/vscode/bin/claude-safe.sh`, and launch it via `herdr pane run <pane-id> "cc <provider-native-args...>"`;
   - for `target_family=gpt/codex`, similarly check alias `cx` → `/home/vscode/bin/codex-safe.sh` and launch `herdr pane run <pane-id> "cx <provider-native-args...>"`;
   - `cc`/`cx` are shell aliases, not Herdr kinds: `--kind cc|cx` is invalid. `herdr agent start --kind claude|codex` is also forbidden for Claude/Codex because it bypasses the safe wrapper and launches the raw executable without the full project runtime contract;
   - if a required alias is missing or does not resolve to the expected wrapper, return `BLOCKED_AGENT_ALIAS_UNAVAILABLE`; silent fallback to `claude`/`codex` is forbidden;
   - wait for the agent to be detected by `pane id` within the startup timeout, assign a unique name via `herdr agent rename <pane-id> <name>`, and only then use `herdr agent prompt ... --wait`;
   - for other target families, use the standard `herdr agent start --kind <kind>`, passing supported provider-native exact model/effort arguments after `--`;
   - pass a self-contained phase task via `herdr agent prompt ... --wait` and wait for the terminal result;
   - save the agent name, pane id, provider session identity, exact tuple, and result path in `sessions.json`/role context.
4. `idle`/`done` after an observed work transition is a valid terminal signal. `blocked` requires reading the reason and routing the question; `unknown` never means completion.
5. Determine progress from the lifecycle and new output. Silence alone does not mean a hang. After two checks without progress, apply the Orchestrator’s standard interrupt/restart limit.
6. After receiving and checking the artifact, close only the agent/pane created by this run. Do not touch other panes, tabs, workspaces, or agents.

If the exact tuple is not supported by the actual CLI schema, apply only the pre-approved outage fallback from the matrix. Do not independently select the “closest” model.

## Algorithm Outside Herdr

1. Do not call `herdr` or attempt to control the focused session externally.
2. Choose a qualified route from the same family as the current harness, preserving the role capability floor.
3. If the approved matrix contains no valid same-family route, return `BLOCKED_ROUTE_UNAVAILABLE`; do not substitute a model from the current family for another family based on name similarity.
4. Kimi K3 is available as an emergency fallback outside Herdr only when the current harness itself belongs to the Kimi family.

## Ownership and Cleanup

- The Orchestrator remains the sole owner of phase routing and fallback events.
- A phase owner does not launch a peer owner or transfer ownership.
- `final-report.md` includes the created agent/pane IDs, terminal states, and cleanup status.
- Do not declare the task complete while Herdr resources created by the current Orchestrator are active.

---
depends_on:
  - framework/skills/tool-usage/herdr/SKILL.md
  - framework/rules/codex-subagent-orchestration/SKILL.md
  - framework/rules/self-recovery-limits/SKILL.md
---
