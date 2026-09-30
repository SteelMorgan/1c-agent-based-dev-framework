# Consilium adapter contract — pointer

> **The canonical location of the adapter contract has moved (RVSW-01, T-14):**
> `review-harness/references/adapter-contract.md` — a single harness contract
> for both tools (consilium and swarm): lifecycle
> `start/ask/status/close` + mandatory `sync`, IO contract, diff materialization
> in sandbox, canonical activity fields
> `last_activity_at`/`last_heartbeat_at` (FR-13), read-only boundary,
> contract tests. This file keeps only consilium-specific
> additions.

## Consilium-specific additions to the harness contract

- The schema of a structured consilium turn is a fenced block
  ```consilium-structured: `references/transcript-schema.md`
  (`elements`/`new_findings`/`position_changes`/`borrowed`/
  `risk_checklist_responses`). A missing/broken block does not reject the turn: the core
  accepts it with empty `structured` and writes a warning to transcript
  (conservative semantics).
- The consilium core additionally stores the adapter `session_id` in
  `.consilium-sessions/<id>/participants/<participant>.json`.
- The text of the consilium turn in `--question`: consilium question + role + moderator
  digest + format instruction (prompts — `references/consilium-prompts.md`).
- Classification of call outcomes (ok/timeout/error) and retry policy are shared,
  see the canonical contract; an `error` record is additionally written to transcript.
