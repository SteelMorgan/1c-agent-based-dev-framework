---
name: cross-provider-review
description: Scope-aware review via Reviewer-controller and review-swarm for artifacts that affect acceptance.
alwaysApply: false
---

# Cross-provider review

Apply this rule to artifacts that change configuration behavior, the contract, framework governance, or acceptance evidence.

## Terms and responsibility

- `agent-consilium` — advisory discussion of material trade-offs. It is not a gate.
- `review-swarm` without `--gate` — advisory findings.
- `review-swarm --gate acceptance|completion` — blocking gate.
- Blocking gate can be closed only by an eligible participant from another family relative to `--caller`; literal self-review and caller-family review are forbidden.
- Reviewer-controller assembles a focused evidence package, launches the gate, and stores provenance; it does not fix the artifact.
- The orchestrator routes rework and makes the management decision.

## Scope-aware acceptance

| Scope | Required package before a single acceptance gate |
|---|---|
| `spec`, `arch` | durable Consilium verdict or evidence `CONSILIUM_NOT_APPLICABLE`; checklist; proposed artifact; resolvable `artifact.md:<line>` references for faithful materialization, structure, and MUST traceability |
| `bdd`, `bdd-steps`, `tests`, `tester` | artifact, scope checklist, and executable results required for the phase |
| `code` | revision-bound diff of changed paths/hunks, raw syntax output, caller map of changed exported methods |
| `debug` | debug report, revision/debug-session identity, marker search, DAP detach/targets status, cleanup of temporary artifacts and data |

After a full Consilium for `spec/arch`, do not run a repeat architectural deliberation or a separate standalone Reviewer. The gate checks materialization and evidence, not the decision again. Standalone reviewer-context is created only with a proven technical fallback.

For `code`, Reviewer-controller remains an evidence producer: it receives the diff, syntax output, and caller map before the external gate starts. Removing this producer is allowed only after a proven deterministic replacement.

## External Package Minimization

1. Always use focused paths; full context is allowed only with explicit justification and the data owner's permission.
2. Caller map and full-repository marker search are passed as `path:line + symbol/marker`, without code bodies, adjacent lines, or a full list of irrelevant paths.
3. Diff is limited to changed paths/hunks.
4. DAP evidence contains statuses and session identity, but not connection strings, tokens, secrets, or test data.
5. Missing required evidence, revision mismatch, or exceeding the focused-package policy block the gate.

## Loop

1. Before starting, check identity and model floor, then lock in the selected cross-family participant via `--gate-reviewer`; quota-based auto-selection does not replace this decision. Run one `review-swarm --gate acceptance` for the phase artifact, wait for the terminal gate outcome, and observe the long-running call through `status`/heartbeat. While the blocking gate is running, stalled, or contains an unresolved BLOCK, the downstream phase does not start.
2. Analyze each finding against the actual artifacts and record the disposition: `agree`, `partial`, `disagree`, `withdrawn`, `out_of_scope`.
3. After a substantial fix, request a delta review. Do not accept an unresolved BLOCK; after three iterations, escalate with both positions and evidence.
4. Before claiming completion, the Orchestrator runs a separate `--gate completion` on the final evidence package. Completion is allowed only with `APPROVE_COMPLETION`, an explicit user override, or a documented escalation after three iterations.
5. Always close the session. In the trace, preserve session id, reviewer/family, exact model/effort, revision, findings/dispositions, iterations, and cleanup status.

## Model Floor and Fallback

Default controller Reviewer is Opus 5 Medium. The actual blocking reviewer must not be weaker than the actual author in capability and effort; if the author is High, the Reviewer is raised to at least High.

Findings, disagreement, `BLOCK_COMPLETION`, cost/latency, and exhausted iterations are not technical unavailability. Standalone fallback is allowed only for the reviewer-controller via a machine-readable allowlist and after successful cleanup; an unknown reason yields BLOCK.

## Scope of application

Review is mandatory for BSL, metadata/XML, queries, roles/RLS, integrations, background jobs, migrations, spec/design/task breakdown, Vanessa/YaxUnit, final report, and changes to framework rules/skills/workflows/scripts. Local refactoring without behavior changes may be exempted from the blocking gate only with an explicit entry in the review trace.

---
depends_on:
  - framework/skills/agent-process/reviewer-controller/SKILL.md
  - framework/skills/tool-usage/review/review-swarm/SKILL.md
  - framework/skills/tool-usage/review/agent-consilium/SKILL.md
---
