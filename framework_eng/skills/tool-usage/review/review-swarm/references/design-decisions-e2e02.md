# Design Decisions for E2E-02 Findings (RVSW-01 rework)

Recorded Orchestrator decisions on findings accepted as
"document, do not rework" (rework disposition E2E-02).

## F-010 (P3): `content_retry_decision` — predicate reserved for future use

`swarm.content_retry_decision(valid, retried, invocation_kind)` is declared
as the implementation of §6.3.1 ("invalid structured turn -> exactly one
content-retry -> unresponsive; timeout -> unresponsive WITHOUT retry"), but
on the production path its result does not affect control flow: retry is
performed unconditionally at the first invalid turn.

**Decision:** keep as is, fix the semantics:

- the current behavior of the production path is **equivalent** to the
  predicate: on the first invalid turn the decision is always `retry`, there
  is no second invalid turn (after one retry the participant is marked
  `unresponsive`); timeouts are handled by a separate `invocation_outcome`
  branch without content-retry. The unconditional retry is an expansion of the
  same decision, not a semantic divergence;
- the predicate is retained as an **explicit anchoring point for the
  §6.3.1 protocol**: it is covered by unit tests (SU-FC01), used in
  comments/checks, and is the place for future retry-policy changes (for
  example, forbidding retry by failure category). It must not be removed - then
  the §6.3.1 rule would lose a single machine-checkable expression;
- if in the future the retry policy becomes context-dependent (failure
  category, round, budget limits), switching to a predicate call is a local
  change in `cmd_attack`/`_verdict_wave`/`cmd_rebut`/`cmd_gate_verdict`/`cmd_rereview`.

## F-011 (P3): `--caller` — mandatory policy assertion of the trusted agent environment

The current review-swarm is launched by an agent locally and does not have an
external runtime contract for authenticating participant identity.

**Bounded solution A:**

- `convene` requires an explicit `--caller <participant id from registry>` in
  all tiers; absence of or unknown id fails before session creation;
- the value is a policy assertion of the calling agent in the current trusted
  agent environment, **not authentication** and not proof of identity against a
  hostile caller;
- the threat model covers accidental self-review, a missing argument, and an
  incorrect registry identity. Deliberate caller spoofing by the caller itself
  is outside the current threat model;
- the exact declared caller is excluded from light selection, the requested
  gate reviewer, and reassignment; another participant of the same family is
  allowed;
- the identity of the artifact author is a separate provenance role and is not
  substituted for the caller;
- authenticated caller identity will require a separate external runtime
  contract (for example, signed capability/tool metadata); its choice and
  implementation are a separate user decision, not a hidden obligation of this
  CLI;
- the session/report retain `orchestrator_id` and compatibility-only
  `caller_family`; durable `gate-outcomes.jsonl` stores `caller_id`,
  `caller_family`, and the actual `reviewer_id`.
