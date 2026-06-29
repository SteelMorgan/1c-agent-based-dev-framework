---
description: Technical rule for launching subagents with multi_agent_v2, fork_turns, model/reasoning args, handoff context, and recovery when the runtime is unavailable.
alwaysApply: false
---

# Subagent Orchestration

## Purpose

This is a technical orchestrator-only rule for launching subagents correctly.

It does not decide **which** subagents to launch or **when** to launch them. Those decisions are made by the orchestrator based on
its skill, routing matrix, workflow rules, owner profiles, and the current task/risk profile.

This rule applies after the orchestrator has already decided that subagent delegation is needed or mandatory. It only defines
the runtime contract: which tool to use, which parameters to pass, how to pass context, and what to do if the required runtime
is unavailable.

## Explicit authorization

This repository is governed by a persistent user instruction: any work that changes the project is performed through
multi-agent/subagent execution. This is explicit user authorization to launch subagents.

A rule recorded in the repository by the user is treated as a direct user instruction. For subagent usage, this rule satisfies
the runtime requirement for an explicit user request.

If a higher-priority runtime or policy layer still blocks subagent launch, the agent must not imitate
delegation in solo mode. It must record the blocker/deviation and escalate.

## Runtime contract: `multi_agent_v2`

For `multi_agent_v2`, launch subagents through the namespaced tool `agents.spawn_agent`
(`spawn_agent` in the `agents` runtime namespace).

Do not use the top-level legacy `spawn_agent` if the `agents` namespace is available.

Every `agents.spawn_agent` call must explicitly pass real runtime arguments:

- `task_name`;
- `message`;
- `fork_turns: "none"`;
- `model`;
- `reasoning_effort`.

Use additional runtime arguments only when they are truly needed and available in the schema:

- `agent_type` - subagent profile/role;
- `service_tier` - only if the tier is selected explicitly.

Do not pass handoff-template fields such as `spawn_settings`, `selection_rationale`, `scope`,
`constraints`, `inputs`, or `expected_output` to `agents.spawn_agent`. Those fields must live inside `message` / handoff text
or in the orchestration trace.

Canonical runtime call form:

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

Do not rely on the default `fork_turns`: in `multi_agent_v2`, an empty value is treated as a full-history fork (`all`).

For tasks in this repository, the following are prohibited by default:

- omitted / empty `fork_turns`;
- `fork_turns: "all"`;
- numeric partial fork;
- `fork_context` (not supported in `multi_agent_v2`).

To disable history fork, use only:

```json
{
  "fork_turns": "none"
}
```

A subagent must not receive old thread history as a hidden source of truth. If it needs context, the orchestrator
builds an explicit handoff in `message`.

## Handoff context

The `message` in `agents.spawn_agent` must contain a complete handoff sufficient to execute the task without access to
the parent thread history.

A minimal handoff contains:

- subagent role / profile;
- task;
- scope and non-goals;
- write/read boundaries;
- relevant paths;
- confirmed decisions and constraints;
- expected output;
- required skills/rules/docs;
- escalation triggers;
- append-only context / handoff-back requirements, if they are needed by the active workflow.

If the orchestrator uses a handoff template, the `spawn_settings` block is a local record of the selected runtime
parameters and rationale. The `spawn_settings` object itself is not a parameter of `agents.spawn_agent`.

## Model and reasoning

Before launching, the orchestrator selects `model` and `reasoning_effort` based on the task/risk profile and passes them
as `agents.spawn_agent` arguments.

The rationale for the choice is recorded in the handoff/trace. If the orchestrator uses a handoff template, the
`spawn_settings.selection_rationale` field is a local handoff field, not a runtime parameter.

General selection rule:

- `low` - simple search, mechanical checks, bounded auxiliary verifications;
- `medium` - ordinary bounded engineering tasks;
- `high` / `xhigh` - architecture, security/compliance, complex debugging, acceptance-bound review, and final decisions.

`mini`-class models are allowed only for exploration, bounded discovery, and other sidecar tasks where the result does not
close the acceptance gate and is not the final owner output for the phase.

The capability floor for blocking review and acceptance-bound gates is defined by the active routing / reviewer rules. If the
available runtime does not allow this floor to be met, record a deviation/blocker instead of silently substituting the model.

If `agent_type` is specified in `agents.spawn_agent`, the agent profile may have role-locked model and reasoning settings.
The orchestrator must account for a possible override: the passed `model` / `reasoning_effort` do not guarantee the effective
settings if the profile overrides them. If the override violates the capability floor, record a deviation/blocker.

## If multi-agent tools are unavailable

If the current session does not have `agents.spawn_agent`, `agents.list_agents`, `agents.wait_agent`, or the
`agents.spawn_agent` schema does not allow passing `model` / `reasoning_effort`, do not replace this with legacy calls and do
not continue a medium/full-cycle task as solo execution.

First, record the blocker/deviation: the multi-agent runtime does not satisfy the requirements of this rule.

Then tell the user that this repository requires enabled Codex `multi_agent_v2`, and ask for explicit
confirmation to change the user runtime configuration. Silently changing the agent settings is strictly forbidden.

After explicit user confirmation, you can propose or add the following block to `~/.codex/config.toml`
(`/home/vscode/.codex/config.toml`):

```toml
[features.multi_agent_v2]
enabled = true
tool_namespace = "agents"
hide_spawn_agent_metadata = false
max_concurrent_threads_per_session = 8
```

`hide_spawn_agent_metadata = false` is required if the agent must see and pass `model`, `reasoning_effort`,
`service_tier`, and `agent_type` in `agents.spawn_agent`.

`max_concurrent_threads_per_session = 8` is an explicit safety cap for `multi_agent_v2`, not just a limit for subagents.
Codex counts all active threads inside the session tree, including the root agent. Therefore, a value of `8` means: `1` root +
up to `7` simultaneously resident/active subagent threads. This cap protects the session from uncontrolled growth in loaded
threads, parallel model turns, tool calls, token/usage consumption, and noise in the orchestration trace.

If a specific task needs more parallel subagents, the value can be increased deliberately: desired number of subagents + `1`
for the root agent. For example, `10` subagents require `max_concurrent_threads_per_session = 11`.
Do not use legacy `[agents].max_threads` together with `multi_agent_v2`: this configuration conflicts with the v2 runtime.

After changing `config.toml`, tell the user that a new Codex session is required for the `multi_agent_v2` tools and the
updated schema to appear. The current session may not receive these tools after the file change.

If the user has not confirmed the configuration change or a new session is impossible, stop the medium/full-cycle flow and
leave an explicit blocker instead of lowering the requirements for multi-agent execution.

## Trace expectations

The orchestrator records the following in the orchestration trace / `.context/orchestrator-context.md`:

- workstream / task name;
- owner profile / `agent_type`, if used;
- `agent/session id`;
- the actual passed `fork_turns`, `model`, `reasoning_effort`, `service_tier`;
- rationale for the model/reasoning choice;
- the fact that `fork_turns: "none"` was used;
- blockers/deviations, including unavailable runtime or capability floor violations.
