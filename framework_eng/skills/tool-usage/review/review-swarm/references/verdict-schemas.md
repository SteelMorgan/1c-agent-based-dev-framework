# Verdict schemas for rounds 2–4, arbitration, and re-review (FR-07..FR-09, TD §5.2–5.5)

All swarm exchanges are structured (FR-03): each move is a fenced block of its own tag,
free text only in `rationale`. An invalid move is rejected by the core fail-closed
(`MoveRejected` with machine-readable `reason`); exactly one retry is handled at the
CLI level (NFR-01). The schema predicates for §5.2–5.3 are implemented as pure functions
`scripts/swarm_core.py` (T-09); schemas §5.4–5.5 are documented here, they are
executed via arbitration calls (T-10) and re-review (T-12); schema §5.9 is
the gate verdict for the light tier and dual role (T-13).

## 5.2 Validation verdict, rounds 2 and 4 (FR-07, AC-07)

Fenced tag: ` ```swarm-verdict `.

```json
{
  "finding_id": "F-001",
  "verdict": "upheld | overruled | reclassify | uncertain",
  "reclassify": {"category": "...", "severity": "P2"},
  "evidence": {"path": "services/x/y.py", "line": 123, "quote": "дословный фрагмент"},
  "rationale": "..."
}
```

- `reclassify` is required only when `verdict = reclassify` (at least one of the
  `category`/`severity` fields); values are canonicalized by the TD §5.1 taxonomy.
- `evidence` is always required; the core checks the resolvability of `path`/`line` in the
  reviewed set and the **novelty of evidence** in the finding thread: a repeat
  `(path, line, normalized quote)` is rejected (`stale_evidence`) — the stop rule
  “each move carries new evidence” (FR-07, analogous to the stalemate predicate). Quote
  normalization means collapsing whitespace sequences: reformatting does not bypass the
  predicate.
- Neighboring models vote (not the author); exactly one vote per participant in a wave.
- Author anonymity: the finding is passed to the attacker's payload with `anon_id`
  instead of `author_id` (`tour_payload`); `anon_map` is stable within the session,
  stored in the session directory, accessible only to the core/Orchestrator (the fair
  anonymization boundary CONS-01 RISK-07 is inherited).
- If all round 2 verdicts are `upheld`, the finding is confirmed early and rounds 3–4 are not
  scheduled (call savings, NFR-02).

## 5.3 Author response, round 3 (FR-07)

Fenced tag: ` ```swarm-author-response `. Exactly one move per finding - the second
is rejected by the core (`move_limit`).

```json
{
  "finding_id": "F-001",
  "response": "maintain | withdraw | accept_reclassify",
  "counter_evidence": {"path": "...", "line": 120, "quote": "..."},
  "rationale": "..."
}
```

- `counter_evidence` is required for `maintain` and is subject to the same novelty predicate
; for `withdraw`/`accept_reclassify` - optional.
- The author sees aggregated objections under the voters' `anon_id`
  (`objections_payload`) - real ids do not leak.
- `withdraw` -> status `withdrawn`; `accept_reclassify` -> `reclassified`
  (the thread closes without round 4); `maintain` -> final verdict (round 4).

## Stop rules and thread statuses (decision No. 5, TD §6.3.5–6.3.6)

- **Cap of 2 exchanges** after the initial attack on the finding: author response -
  exchange 1, final verdict - exchange 2; a third exchange is impossible (fail-closed,
  `thread_closed`).
- **No new findings in rounds 2-4**: a `swarm-structured` block with
  findings inside responses from rounds 2-4 are not accepted into the thread; findings
  are routed to the shared pool (raw records for `validate_findings`, numbering is global across the pool). Only round 1 and re-review produce findings.
- **Finding statuses at thread end**: `confirmed` (all `upheld` from round 2 or
  round 4), `withdrawn`, `reclassified`, `contested` - unresolved disagreement
  after the cap; contested severity ≥ major (P1–P2) - to Orchestrator arbitration
  (FR-08), `report` without such arbitration is rejected (TD §6.3.7).

## 5.4 Orchestrator arbitration (FR-08, AC-08) - execution in T-10

Side argument (the core assembles it from the thread; if needed - one
additional structured call to the side):

```json
{"finding_id": "F-001", "side": "author | attacker", "claim": "...", "evidence": {"path": "...", "line": 0, "quote": "..."}}
```

Orchestrator decision (submitted via `swarm.py arbitrate --decision-file`):

```json
{
  "finding_id": "F-001",
  "decision": "upheld | overruled | reclassified",
  "reclassified": {"category": "...", "severity": "..."},
  "evidence_quote": "дословная цитата спорного места кода из файла location.path",
  "location": {"path": "...", "line": 0},
  "rationale": "..."
}
```

Core fail-closed validates: non-empty `evidence_quote`; `location` resolves
to a real file in the checked set; `decision` agrees with the thread (for
`reclassified` the block is filled in). **`evidence_quote` must be a verbatim
quote from the file specified in the decision's `location.path`** (FR-08
"code decision"): a quote from a neighboring/other file is rejected fail-closed - the arbitration must rely on the disputed code location, not indirect context. The decision
is final; escalation to a human - in case of material disagreement (inherited
semantics).

## 5.5 Re-review verdict (FR-09, AC-09) — execution in T-12

```json
{
  "finding_id": "F-001",
  "verdict": "fixed | partially | not_fixed | introduced_new_issue",
  "new_issue": { "...": "полная находка по схеме finding-schema.md" },
  "evidence": {"path": "...", "line": 0, "quote": "..."},
  "rationale": "..."
}
```

- Input: the original finding + fix diff via `sync_participant()`
  harness contract (a new focused session of the same author adapter; the old
  session is not retained — retention is not violated).
- `new_issue` is required when `introduced_new_issue`; the new finding goes into
  the shared pool (not into the thread) — the same routing rule as for rounds 2–4.
- Conflict with the developer's position → arbitration §5.4.

## 5.9 Gate verdict (FR-16, AC-16/AC-24, TD §7) — execution in T-13

Fenced tag: ` ```swarm-gate-verdict `. Inherited gate semantics of the rule
`cross-provider-review.md`: a separate structured call to the gate reviewer
(enabled/healthy, not the exact declared caller, `gate_legal`) **AFTER** the report and the
Orchestrator dispositions —
`swarm.py gate-verdict`, ≤ 3 review/rework/delta iterations (delta iteration:
rework → `sync` sandbox → repeat call), then escalation to the user with both
positions and evidence (Hard Rule 16). Two modes (`GATE_MODES`).

### Mode `completion` (finalization gate, blocking)

```json
{
  "decision": "APPROVE_COMPLETION | BLOCK_COMPLETION",
  "findings": [{"id": "F-01", "severity": "BLOCK | WARN | INFO",
                "claim": "что не так", "evidence": "file:line / цитата"}],
  "rationale": "...",
  "escalation_needed": false
}
```

- `decision` is strictly from `GATE_COMPLETION_DECISIONS`; `BLOCK_COMPLETION`
  blocks completion (Hard Rule 15): the Orchestrator does not declare the task
  complete without `APPROVE_COMPLETION`, user override, or a recorded
  escalation after 3 iterations.
- `escalation_needed: true` — only in case of a material disagreement on the last
  iteration.
- Canonical core form: `{decision, findings, rationale,
  escalation_needed}`; prompt — `final-orchestrator-completion-review-prompt.md`.

### `acceptance` mode (acceptance-bound review)

```json
{
  "verdict": "accept | conditional_accept | reject | re-review",
  "positions": [{"finding_id": "F-001",
                 "position": "agree | partial | disagree | withdrawn"}],
  "rationale": "..."
}
```

- `verdict` must be strictly from `GATE_ACCEPTANCE_VERDICTS`; `accept` is a positive
  outcome (terminal `approved`); `reject`/`re-review` mean disagreement →
  rework and delta iteration. `conditional_accept` (F-007, E2E-02) is NOT a
  positive outcome: an intermediate `conditional` status, terminal `approved`
  is set only by an explicit Orchestrator decision
  (`gate-verdict --conditional-decision confirm|reject`).
- `positions` are the reviewer's positions on session findings where the
  Orchestrator's disposition differs from the reviewer's assessment; the fail-closed core checks:
  `finding_id` — only known session findings (`unknown_finding`),
  `position` — only from `GATE_POSITIONS` (`invalid_position`).
  `out_of_scope` — the Orchestrator's disposition, NOT the reviewer's position.
- The core canonical form: `{verdict, positions, rationale}`.

General rules: the schema is strict, fail-closed — an invalid move → exactly one retry
→ participant `unresponsive`; the gate reviewer's verdict and findings are marked
`gate_pass` and removed from the advisory track record statistics (dual role,
AC-24); the fact and form of the gate pass are recorded in the review trace report (section
“Gate pass”). Predicates — `swarm_core.parse_gate_verdict_move` /
`validate_gate_verdict`; statuses `approved | blocked | escalated` —
`swarm.resolve_gate_status` (`gate_verdict_positive`).
