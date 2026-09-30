---
name: codex-subagent-orchestration
description: Technical rule for launching Codex subagents, choosing model/reasoning_effort, isolating handoff, bounded consultation, and recovery when the runtime is unavailable.
alwaysApply: false
---

# Codex Subagent Orchestration

## Purpose

Apply this runtime contract to spawns by the root orchestrator and to each explicitly authorized spawn by a
first-level owner: a specialized support task or bounded consultation. The decision about phases and their owners
remains with the orchestrator and workflow. This rule does not give an owner the right to route phases,
coordinate peer owners, or transfer ownership.

A standing repository user instruction for multi-agent execution counts as explicit
authorization. If a higher-priority policy blocks a spawn, record the blocker/deviation;
do not simulate delegation in solo mode.

## Runtime contract: `multi_agent_v2`

Before each spawn, first read the actual `agents.spawn_agent` schema: check the available
models, effort levels supported by the selected model, and optional arguments. Do not invent enums and
do not use the legacy tool when the `agents` namespace is available.

Pass the actual runtime arguments:

| Argument | Status | Value |
|---|---|---|
| `task_name` | MUST | lowercase task name |
| `message` | MUST | self-contained handoff that does not rely on thread history |
| `fork_turns` | MUST | only `"none"` |
| `model` | MUST | separately selected supported model |
| `reasoning_effort` | MUST | separately selected supported effort |
| `agent_type` | MAY | profile/role, if present in the schema |
| `service_tier` | MAY | only when explicitly selected |

Do not pass handoff-template fields as runtime arguments: `spawn_settings`,
`selection_rationale`, `scope`, `constraints`, `inputs`, `expected_output`. Keep them in `message`
or the orchestration trace.

## Model and reasoning

Choose `model` and `reasoning_effort` independently. For each axis, record a brief rationale covering scope, complexity, risk, cost, and required depth. The family name does not determine effort, and effort does not replace the family choice.

In Codex-harness, use the following binding canonical role matrix. It is not inherited from the model of the current main session and is not an independent source of product policy:

| Role / work type | Model | Effort |
|---|---|---|
| `explorer` | `gpt-5.6-luna` | `max` |
| `scenario-coder`, `developer-tests`, `developer-code` | `gpt-5.6-luna` | `max` |
| `scenario-author`, `tester` | `gpt-5.6-sol` | `medium` |
| `analyst`, `architect`, `debugger` | `gpt-5.6-sol` | `high` |
| acceptance-bound `reviewer` | `gpt-5.6-sol` | no lower than the author's actual effort; default `high` |

For Explorer, the default is `Luna/max`; lowering it is permitted only as an explicit effort override by the Orchestrator for a mechanical, bounded, and independently verifiable sidecar task, but not for a phase owner. Always record the actual pair and rationale in the trace.
Ordinary routing ranks: `Luna < Terra < Sol` and
`low < medium < high < xhigh < max`.

### Risk override and capability floor

- Raise the family and/or effort for architecture, security/compliance, complex diagnostics,
  ambiguous source of truth, and decisions that close a gate.
- Lower the baseline only for a mechanical, bounded, and independently verifiable sidecar task
  that does not own a phase artifact and does not close a gate.
- Preserve the active role's capability floor after all overrides.
- For a blocking/acceptance-bound Reviewer, compare the author's and Reviewer's pairs actually selected after overrides. The Reviewer's rank MUST be no lower than the author's on both axes. If the schema does not allow this to be ensured, stop the review launch with a blocker; do not lower the author or Reviewer.
- If `agent_type` overrides the passed values, verify the effective pair where possible. If the floor is violated, record a deviation/blocker.

### GPT-only guard against overengineering

This restriction applies only to GPT/Codex family models, including Sol, Terra, and Luna. Do not
apply it automatically to Claude, Kimi, or other families.

Before finalizing a specification, architecture, decomposition, or implementation, a GPT agent must critically
review its own solution: each new component, abstraction, service, registry, DSL, layer, or
technology must address a direct requirement or a proven risk. If the same result can be achieved
with an existing mechanism at lower complexity and without worsening acceptance criteria, choose the
simpler solution. “Possible future development” without a current requirement is not a justification.

This guard does not permit lowering quality, skipping mandatory checks, or ignoring
an architectural boundary; it only prohibits unjustified complexity.

### Schema fallback: monotonic and fail-closed

If the exact pair is unavailable, only an explicitly safe monotonic mapping within the
GPT-5.6 family is allowed: `Luna → Terra → Sol` at the same or higher supported effort, or a transition
to the next supported level of the ordinary ladder in the same or higher family. Downgrades along
either axis and a subjective “closest” model are prohibited.

Do not rank `gpt-5.5`, `gpt-5.4`, and other generations relative to GPT-5.6, or use them as a fallback
without a separate, previously approved mapping. If the mapping does not yield a pair at or above the capability floor, and
for the Reviewer, also the author's actual pair, return a blocker.

### `max` and `ultra`

`max` is the top step of the ordinary ladder, if supported by the selected model. `ultra` is not part of the
baseline or ordinary ladder, and is not a fallback or automatic escalation for the owner/Reviewer/
consultation. Apply `ultra` only for a separately and explicitly approved broad read-only
multi-agent spike: record the goal, slot/token budget, schema support, and prohibition on artifact ownership and gate verdicts.
Without such approval, use the ordinary ladder or return a blocker; auto-ultra is prohibited.

## Support child and bounded consultation

A Support child performs a narrow auxiliary task explicitly provided for by the profile, for example
`analyst → Explore`. It does not make owner decisions, receive ownership, or count as a
consultation, but it follows the entire runtime contract of this rule.

A first-level owner MAY launch one bounded read-only consultation only when there is a verifiable trigger:

1. two plausible interpretations of the source of truth change the outcome;
2. the decision affects architecture, security/compliance, or an acceptance gate;
3. a high-risk hypothesis remains after the standard checks have been exhausted;
4. the owner must make a decision outside their primary specialty.

A general request to “check everything,” saving time, and handing off the owner’s own phase work are not
triggers. Only the depth `root → owner → consultant` is allowed, that is, `depth=1` relative to the
owner. The consultant must not spawn other agents.

The consultant must be stronger for the specific question: for an owner on Luna — Terra or Sol; for an
owner on Terra — Sol; for an owner on Sol — any supported effort on the ordinary ladder, strictly higher
than the owner’s actual effort, up to `max`. Do not lower the second axis. If an upgrade is
impossible, do not launch a consultation and return a blocker. `ultra` is allowed only with separate
broad-spike approval from the previous section.

## Fork policy and nested handoff

Always pass `fork_turns: "none"`. Omitted/empty, `"all"`, numeric partial forks, and
unsupported `fork_context` are prohibited. Hidden thread history is not a source of truth.

The handoff for any child must contain the role, task, scope/non-goals, read/write boundaries,
confirmed facts and decisions, relevant paths, expected output, required rules, escalation triggers,
time budget, signs of progress, and cleanup obligations.

For a nested child, additionally specify the concrete question, permitted read paths,
write prohibition, prohibition on further spawning, and return conditions. Peer-to-peer phase routing through
handoff is prohibited.

## Ownership and gates

Consultant returns a recommendation only to its parent owner and remains read-only with respect to the
owner artifact. It does not edit the phase artifact, make the final decision, close the shared acceptance
gate through Reviewer-controller/review-swarm, take ownership, or coordinate independent owners.

Parent owner checks the response against the source of truth, accepts or rejects the recommendation, and remains
the author accountable for the result.

## Slot budget

Before nested spawn, count root and all active/resident threads toward
`max_concurrent_threads_per_session`. Each owner may have no more than one consultant at a time.
Consultation does not displace a required phase owner/Reviewer; when there is competition, keep
one free slot for gate/recovery. If the budget is exhausted, defer or cancel the consultation or
return a blocker. Do not automatically raise the safety cap.

## Health check

Regularly monitor launched workers: messages, processes, artifacts, logs, progress, and
cleanup. The launch environment skill defines the mechanism and frequency (for Herdr — the
`herdr` skill / worker watchdog); do not set up your own timers on top of it. The initiator is responsible for the launched child: the parent owner is responsible for a support child or
consultant, and the root orchestrator is responsible for the owner and consultation visibility through trace. Do not create a separate infinite wait loop for
consultation.

If work is complete according to the artifacts but there is no response, interrupt and record the result. After two
consecutive checks with no progress, work outside scope, or exceeding the time budget by approximately 1.5
times, interrupt and do one narrower restart with the facts. A third wait cycle is forbidden. Record
`HEALTHCHECK_ANOMALY`, `INTERRUPT`, `RESTART`, or `SCOPE_CORRECTION`.

## Self-check and recovery

Require the child not to wait indefinitely; on an error or failed pre-run gate, classify
the result, perform cleanup, and return; after a substantial step, check whether the result or a blocker is already sufficient; do not start an unplanned investigation after failure; return a
partial result when the budget is exhausted.

If `agents.spawn_agent`, `agents.list_agents`, `agents.wait_agent` are unavailable, or the schema does not allow explicitly passing `model` and `reasoning_effort`, do not switch to a legacy call and do not continue the medium/full
flow in solo mode. Record a blocker and request separate confirmation to change the user's
runtime configuration. Without confirmation, do not change `~/.codex/config.toml`; after an agreed
change, warn that the schema will update only in a new Codex session.

## Trace expectations

For each spawn, including the owner, support child, and consultant, record:

- parent/owner, workstream/task name, task/session id, and depth;
- objective, and for consultation, the objective trigger;
- passed and, when available, effective `model` and `reasoning_effort`, as well as `fork_turns`;
- separate rationale for both axes and the actual slot count;
- consultant result, owner decision, and a brief check against the source of truth;
- deviations/blockers and health-check/recovery events.
