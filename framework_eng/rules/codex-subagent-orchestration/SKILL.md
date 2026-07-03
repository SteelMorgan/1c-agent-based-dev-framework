---
name: codex-subagent-orchestration
description: Technical rule for launching multi_agent_v2 subagents, fork_turns, model/reasoning args, handoff context, and recovery when the runtime is unavailable.
alwaysApply: false
---

# Subagent Orchestration

## Purpose

Technical orchestrator-only rule: runtime contract for launching subagents (which tool to use, which parameters, how to pass context, what to do if the runtime is unavailable). **Which** subagents to launch and **when** to launch them is decided by the orchestrator according to its skill, routing matrix, workflow rules, owner profiles, and task/risk profile. The rule applies after the delegation decision has already been made.

## Explicit authorization

Persistent repository user instruction: work that changes the project is performed through multi-agent/subagent execution - this is explicit authorization to launch subagents and satisfies the runtime requirement for an explicit user request. If a higher-priority policy layer still blocks the launch, do not imitate delegation in solo mode: record the blocker/deviation and escalate.

## Runtime contract: `multi_agent_v2`

Launch subagents through the namespaced tool `agents.spawn_agent` (`spawn_agent` in the runtime namespace `agents`). Do not use the top-level legacy `spawn_agent` if the `agents` namespace is available.

Each call passes real runtime arguments:

| Argument | Status | Value |
|----------|--------|----------|
| `task_name` | required | lowercase task name |
| `message` | required | full handoff without relying on thread history |
| `fork_turns` | required | only `"none"` (see Fork policy) |
| `model` | required | chosen according to task/risk profile |
| `reasoning_effort` | required | `low` \| `medium` \| `high` \| `xhigh` |
| `agent_type` | optional | subagent profile/role, if needed and present in the schema |
| `service_tier` | optional | only if the tier is explicitly selected |

Do not pass the handoff-template fields `spawn_settings`, `selection_rationale`, `scope`, `constraints`, `inputs`, `expected_output` to `agents.spawn_agent` - they live inside `message` / handoff text or in the orchestration trace.

Canonical form of the runtime call:

```json
{
  "task_name": "<lowercase_task_name>",
  "message": "<full handoff without relying on thread history>",
  "fork_turns": "none",
  "model": "<chosen model>",
  "reasoning_effort": "<low|medium|high|xhigh>",
  "agent_type": "<profile role, if needed>",
  "service_tier": "<if explicitly selected>"
}
```

## Fork policy

Do not rely on the default `fork_turns`: in `multi_agent_v2`, an empty value is treated as a full-history fork (`all`). By default, the following are prohibited:

- omitted / empty `fork_turns`;
- `fork_turns: "all"`;
- numeric partial fork;
- `fork_context` (not supported in `multi_agent_v2`).

To disable history forking, use only `fork_turns: "none"`. The subagent must not receive the old thread history as a hidden source of truth - the orchestrator assembles the needed context explicitly via handoff in `message`.

## Handoff context

`message` contains the full handoff, sufficient to execute without access to the parent thread history. Minimum handoff:

- subagent role / profile; task; scope and non-goals;
- write/read boundaries; relevant paths;
- confirmed decisions and constraints; expected output;
- required skills/rules/docs; escalation triggers;
- requirements for append-only context / handoff-back, if needed for the active workflow.

The `spawn_settings` block (if the orchestrator handoff template is used) is a local record of the selected runtime parameters and rationale, and is not a parameter of `agents.spawn_agent`.

### Self-check in the subagent handoff

Every handoff must contain an explicit instruction for self-check - protection against hangs on failed commands, an unmet pre-run gate, lack of cleanup after failure, and false expectation of "just a little more". Minimum wording in `message`:

- do not wait forever for a command, process, GUI, build, test, or external service;
- if a command fails or the pre-run gate is not passed - classify the result (`test_error`, `implementation_error`, `environment_error`, `blocked_pre_run`, etc.), perform the required cleanup, and return a report;
- after each significant step, re-check: "is there already enough result or blocker to return to the orchestrator?";
- do not start a new workaround path after failure without an explicit check that it remains in scope;
- if there is no progress by the subagent's own time budget - stop with a partial result instead of hanging.

For commands that can hang, the handoff sets a specific time budget and signs of progress: allowed processes, expected files/logs, which report/status counts as completion, required cleanup on failure. After creating the result and performing cleanup, the subagent immediately returns `FINAL_ANSWER` with result classification; additional "I'll check one more thing" steps after a sufficient result are prohibited unless they were part of the handoff.

## Model and reasoning

Orchestrator selects `model` and `reasoning_effort` according to the task/risk profile and passes them as arguments, recording the rationale in the handoff/trace (`spawn_settings.selection_rationale` — a local handoff field, not a runtime parameter). Selection rule:

- `low` — simple search, mechanical verification, bounded auxiliary checks;
- `medium` — ordinary bounded engineering tasks;
- `high` / `xhigh` — architecture, security/compliance, complex debugging, acceptance-bound review, and final decisions.

Models of the `mini` class are allowed only for exploration, bounded discovery, and sidecar tasks where the result does not close the acceptance gate and is not the final owner-output for the phase. The capability floor for blocking review and acceptance-bound gates is set by the active routing / reviewer rules; if the runtime cannot satisfy it, record a deviation/blocker instead of silently substituting the model. If `agent_type` is specified, the profile may have role-locked model and reasoning settings: the passed `model` / `reasoning_effort` do not guarantee the effective settings if the profile overrides them. If the override violates the capability floor, record a deviation/blocker.

## Health-check of running subagents

Control cadence: **5 → 10 → 15 → 15… minutes** — the first health-check is 5 minutes after launch; the second is 10 minutes after the previous check; the third is 15 minutes after the previous one; then every 15 minutes until the subagent finishes. A health-check is not passive waiting via `wait_agent`; at each check, inspect external signs of progress:

- whether the subagent is alive, whether there is a queued/final message;
- what processes it started and whether they match the handoff;
- whether the expected artifacts appeared: context, report, build/test logs, temp files, cleanup markers;
- whether logs are growing or the process is stuck without output;
- whether the work is effectively finished in files/logs even if there is no `FINAL_ANSWER`;
- whether temp objects, locks, deny flags, GUI/session processes, or other cleanup obligations were left behind;
- whether the subagent went off on a detour outside the scope.

**Two-check rule / escalation.** If the work is complete by artifacts but there is no report, interrupt the subagent, record the result from the source-of-truth artifacts, and continue routing. If two consecutive health-checks show no progress, the process is doing something other than what was in the handoff, or the time budget is exceeded by about 1.5x, interrupt the subagent and restart a narrower task with facts from files/logs. A third "let's wait a bit more" is forbidden. At launch, specify in the handoff the time budget, expected progress artifacts, and cleanup obligations; on anomaly, record `HEALTHCHECK_ANOMALY`, `INTERRUPT`, `RESTART`, or `SCOPE_CORRECTION` in the orchestration trace.

## If multi-agent tools are unavailable

If there is no `agents.spawn_agent`, `agents.list_agents`, `agents.wait_agent`, or the `agents.spawn_agent` schema does not allow passing `model` / `reasoning_effort` - do not replace this with legacy calls and do not continue a medium/full-cycle task as solo execution. Order:

1. Record the blocker/deviation: the multi-agent runtime does not meet the requirements of this rule.
2. Tell the user that this repository requires Codex `multi_agent_v2` to be enabled, and ask for explicit confirmation to change the user runtime configuration. Changing agent settings silently is strictly forbidden.
3. After explicit confirmation, suggest or apply the following in `~/.codex/config.toml` (`/home/vscode/.codex/config.toml`):

```toml
[features.multi_agent_v2]
enabled = true
tool_namespace = "agents"
hide_spawn_agent_metadata = false
max_concurrent_threads_per_session = 8
```

- `hide_spawn_agent_metadata = false` is required if the agent must see and pass `model`, `reasoning_effort`, `service_tier`, and `agent_type` in `agents.spawn_agent`.
- `max_concurrent_threads_per_session = 8` is an explicit safety cap for `multi_agent_v2` (not only for subagents): Codex counts all active threads in the session tree, including the root agent, so `8` = `1` root + up to `7` simultaneous resident/active subagent threads. The cap protects the session from uncontrolled growth of loaded threads, parallel model turns, tool calls, token/usage consumption, and trace noise. If you need more parallel subagents, increase it intentionally: desired number + `1` for the root (for `10` subagents - `max_concurrent_threads_per_session = 11`). Do not use the legacy `[agents].max_threads` together with `multi_agent_v2` - that configuration conflicts with the v2 runtime.

After changing `config.toml`, report that a new Codex session is required for the `multi_agent_v2` tools and updated schema to appear; the current session may not receive them. If the user did not confirm the change, or a new session is not possible - stop the medium/full-cycle flow and leave an explicit blocker instead of lowering the requirements for multi-agent execution.

## Trace expectations

Orchestrator records in orchestration trace / `.context/orchestrator-context.md`:

- workstream / task name;
- owner profile / `agent_type`, if used;
- `agent/session id`;
- actually passed `fork_turns`, `model`, `reasoning_effort`, `service_tier`;
- rationale for choosing model/reasoning;
- the fact of `fork_turns: "none"`;
- blockers/deviations, including unavailable runtime or capability floor violation.
