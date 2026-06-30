---
name: codex-subagent-orchestration
description: Technical rule for launching subagents: multi_agent_v2, fork_turns, model/reasoning args, handoff context, and recovery when the runtime is unavailable.
alwaysApply: false
---

# Subagent Orchestration

## Purpose

This is a technical orchestrator-only rule for correctly launching subagents.

It does not decide **which** subagents to launch or **when** to launch them. Those decisions are made by the orchestrator based on
its skill, routing matrix, workflow rules, owner profiles, and the current task/risk profile.

This rule applies after the orchestrator has already decided that subagent delegation is needed or mandatory. It fixes only
the runtime contract: which tool to use, which parameters to pass, how to pass context, and what to do if the required runtime is unavailable.

## Explicit authorization

For this repository, a permanent user instruction applies: work that changes the project is performed through
multi-agent/subagent execution. This is explicit user authorization to launch subagents.

A rule recorded in the repository by the user is considered a direct user instruction. For subagent usage, this
rule satisfies the runtime requirement for an explicit user request.

If a higher-priority runtime or policy layer still blocks subagent launch, the agent must not simulate
delegation in solo mode. It must record the blocker/deviation and escalate.

## Runtime contract: `multi_agent_v2`

For `multi_agent_v2`, launch subagents through the namespaced tool `agents.spawn_agent`
(`spawn_agent` in the runtime namespace `agents`).

Do not use the top-level legacy `spawn_agent` if the `agents` namespace is available.

Each `agents.spawn_agent` call must explicitly pass real runtime arguments:

- `task_name`;
- `message`;
- `fork_turns: "none"`;
- `model`;
- `reasoning_effort`.

Use additional runtime arguments only when they are actually needed and available in the schema:

- `agent_type` — subagent profile/role;
- `service_tier` — only if the tier is explicitly selected.

Do not pass handoff-template fields like `spawn_settings`, `selection_rationale`, `scope`,
`constraints`, `inputs`, or `expected_output` to `agents.spawn_agent`. These fields must be inside `message` / handoff text or in the orchestration
trace.

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

Do not rely on the default `fork_turns`: in `multi_agent_v2`, an empty value is treated as a full-history fork (`all`).

For tasks in this repository, the following are prohibited by default:

- omitted / empty `fork_turns`;
- `fork_turns: "all"`;
- numeric partial fork;
- `fork_context` (not supported in `multi_agent_v2`).

To disable history forking, use only:

```json
{
  "fork_turns": "none"
}
```

The subagent must not receive old thread history as a hidden source of truth. If it needs context, the orchestrator assembles an explicit handoff in `message`.

## Handoff context

`message` in `agents.spawn_agent` must contain a complete handoff sufficient to perform the task without access to the parent thread history.

A minimal handoff contains:

- the subagent role / profile;
- the task;
- scope and non-goals;
- write/read boundaries;
- relevant paths;
- confirmed decisions and constraints;
- expected output;
- required skills/rules/docs;
- escalation triggers;
- requirements for append-only context / handoff-back, if they are needed by the active workflow.

If the orchestrator's handoff-template is used, the `spawn_settings` block is a local record of the selected runtime parameters and rationale. The `spawn_settings` object itself is not a parameter of `agents.spawn_agent`.

## Self-check in the subagent handoff

Every handoff to a subagent must include an explicit instruction for self-checking execution. This protects against hangs caused by failed commands, an unmet pre-run gate, cleanup after failure, and false waiting for "just a little longer".

Minimum wording in `message`:

- do not wait indefinitely for a command, process, GUI, build, test, or external service;
- if a command fails or the pre-run gate is not passed, classify the result (`test_error`,
  `implementation_error`, `environment_error`, `blocked_pre_run`, etc.), perform the required cleanup, and return a report;
- after each significant step, re-check: "is there already enough result or a blocker to return to the orchestrator?";
- do not start a new workaround after failure without an explicit check that it remains in scope;
- if there is no progress by the subagent's own time budget, stop with a partial result instead of continuing to hang.

For tasks with commands that may hang, the handoff must define a specific time budget and the expected signs of progress: which processes are allowed, which files/logs should appear, which report or status counts as completion, and which cleanup is mandatory on failure.

If the subagent created a result and completed cleanup, it must immediately return `FINAL_ANSWER` with a classification of the result. Additional "I'll check one more thing" steps after a sufficient result are prohibited unless they were part of the handoff.

## Model and reasoning

Before launching, the orchestrator selects `model` and `reasoning_effort` according to the task/risk profile and passes them as arguments to
`agents.spawn_agent`.

The rationale for the choice is recorded in the handoff/trace. If the orchestrator's handoff-template is used, the field
`spawn_settings.selection_rationale` is a local field of the handoff, not a runtime parameter.

The general selection rule is:

- `low` — simple search, mechanical verification, bounded auxiliary checks;
- `medium` — ordinary bounded engineering tasks;
- `high` / `xhigh` — architecture, security/compliance, complex debugging, acceptance-bound review, and final decisions.

`mini` class models are allowed only for exploration, bounded discovery, and other sidecar tasks where the result does not close
the acceptance gate and is not the final owner output for the phase.

The capability floor for blocking review and acceptance-bound gates is set by active routing / reviewer rules. If
available runtime does not allow this floor to be met, a deviation/blocker must be recorded rather than silently substituting the model.

If `agent_type` is specified in `agents.spawn_agent`, the agent profile may have role-locked model and reasoning settings.
The orchestrator must account for a possible override: passed `model` / `reasoning_effort` do not guarantee effective
settings if the profile overrides them. If the override violates the capability floor, record a deviation/blocker.

## Health-check of running subagents

The orchestrator is required to monitor running subagents on a **5 → 10 → 15 → 15... minutes** scale:

- the first health-check 5 minutes after the subagent starts;
- the second one 10 minutes after the previous check;
- the third one 15 minutes after the previous check;
- then every 15 minutes until the subagent is finished.

A health-check is not passive waiting on `wait_agent`. At each check, the orchestrator verifies external signs
of movement:

- whether the subagent is alive and whether there is a queued/final message;
- which processes it started and whether they match the handoff;
- whether expected artifacts have appeared: context, report, build/test logs, temp files, cleanup markers;
- whether logs are growing or the process has hung without output;
- whether the actual work has already finished via files/logs, even if the subagent has not sent `FINAL_ANSWER`;
- whether temporary objects, locks, deny flags, GUI/session processes, or other cleanup obligations were left behind;
- whether the subagent has gone off on an alternate path outside the scope.

If artifacts show that the work is already complete, but the subagent has not sent a report, the orchestrator must interrupt
the subagent, independently record the result from the source-of-truth artifacts, and continue routing.

If two consecutive health-checks show no progress, the process is doing something other than what is specified in the handoff, or the time budget
has been exceeded by approximately 1.5x, the orchestrator must interrupt the subagent and restart a narrower task with facts
obtained from files/logs. A third "let's wait a bit longer" is forbidden.

When starting a subagent, the orchestrator must specify the time budget, expected progress artifacts, and cleanup obligations in the handoff;
for an anomaly, record `HEALTHCHECK_ANOMALY`, `INTERRUPT`, `RESTART`, or `SCOPE_CORRECTION` in the orchestration trace.

## If multi-agent tools are unavailable

If `agents.spawn_agent`, `agents.list_agents`, `agents.wait_agent` are not available in the current session, or the `agents.spawn_agent` schema
does not allow passing `model` / `reasoning_effort`, do not replace this with legacy calls and do not continue the medium/full-cycle
task as solo execution.

First, record the blocker/deviation: the multi-agent runtime does not meet the requirements of this rule.

Then tell the user that this repository requires enabled Codex `multi_agent_v2`, and ask for explicit
confirmation to change the user runtime configuration. Changing agent settings silently is strictly prohibited.

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
`service_tier` and `agent_type` in `agents.spawn_agent`.

`max_concurrent_threads_per_session = 8` is an explicit safety cap for `multi_agent_v2`, not a limit only on subagents.
Codex counts all active threads inside the session tree, including the root-agent. Therefore, the value `8` means: `1` root +
up to `7` simultaneously resident/active subagent threads. This cap protects the session from uncontrolled growth of loaded
threads, parallel model turns, tool calls, token/usage consumption, and noise in the orchestration trace.

If a specific task needs more parallel subagents, the value can be increased deliberately: the desired number of
subagents + `1` for the root-agent. For example, `10` subagents require `max_concurrent_threads_per_session = 11`.
Do not use legacy `[agents].max_threads` together with `multi_agent_v2`: such a config conflicts with the v2 runtime.

After changing `config.toml`, tell the user that launching a new Codex session is required for the `multi_agent_v2` tools and updated schema to appear.
The current session may not receive these tools after the file is changed.

If the user did not confirm the configuration change, or a new session is not possible, stop the medium/full-cycle flow and
leave an explicit blocker instead of lowering the requirements for multi-agent execution.

## Trace expectations

Orchestrator records in the orchestration trace / `.context/orchestrator-context.md`:

- workstream / task name;
- owner profile / `agent_type`, if used;
- `agent/session id`;
- actually passed `fork_turns`, `model`, `reasoning_effort`, `service_tier`;
- rationale for the choice of model/reasoning;
- the fact of `fork_turns: "none"`;
- blockers/deviations, including unavailable runtime or capability floor violation.
