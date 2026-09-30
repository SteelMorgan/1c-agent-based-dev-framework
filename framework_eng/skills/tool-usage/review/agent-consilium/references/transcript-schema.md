# transcript.jsonl and Structured Turn Schema (FR-04, TD 3.3)

The consilium transport is append-only `.consilium-sessions/<session_id>/transcript.jsonl`.
There is no P2P exchange between participants: everything goes through the moderator. One line = one record.

## Entry

```json
{
  "seq": 17,
  "ts": "2026-07-28T...",
  "session_id": "cons-...",
  "phase": "B",
  "round": 2,
  "wave": "attack",
  "author": "claude-opus",
  "anon_id": "M2",
  "type": "attack",
  "refs": [9, 12],
  "content": "полный текст хода",
  "structured": {
    "elements": ["E1", "E4"],
    "new_findings": [{"id": "F-01", "text": "..."}],
    "position_changes": [{"element": "M1:E2", "action": "disagree", "refs": [9]}],
    "borrowed": [{"element": "E7", "source_ref": 12}]
  }
}
```

| Field | Rule |
| --- | --- |
| `seq` | global, monotonically increasing from 1; the core on append checks `seq = last+1`; overwrite is prohibited (no-rewrite). Cryptography is not used (RISK-10 — accepted residual risk) |
| `type` | enum: `proposal / attack / response / digest / kill_decision / final_statement / synthesis / redteam_attack / confirmation / verdict / system / error` |
| `author` | in transcript always the real id (traceability); moderator records are `author: "moderator"`; the moderator is forbidden participant types (proposal/attack/response/redteam_attack/confirmation) — the core rejects them (FR-05) |
| `anon_id` | filled in for phase B records; used only in output to participants (bundles); mapping — `anon_map.json`, only in the session directory |
| `refs` | links to `seq` of previous records on which the turn relies |

## Structured Turn (`structured`)

The participant declares fields in the fenced block `consilium-structured` at the end of the response.

- `elements` — stable ids of elements of the participant's current model (the adapter session is preserved,
  the participant remembers their ids between turns). Other participants' elements are addressed as `<anon_id>:<element_id>`.
- `new_findings` — new findings from the turn `[{"id": "F-01", "text": "..."}]`.
- `position_changes` — by item: `agree / disagree / refine / withdraw` + `refs`.
- `borrowed` — `[{"element": "E7", "source_ref": <seq>}]`.
- `risk_checklist_responses` (CONS-03 E-4, required for turns in phases A/B/D:
  proposal/attack/response/redteam/confirmation; `final_statement` is not covered) —
  responses to the role risk checklist: `[{"item_id": "<id of registry item>", "verdict": "hit|clear|na",
  "note": "<text>"}]`. Fail-closed: all items applicable to the turn type (`applies_to`) are covered;
  `item_id` is from the role catalog; verdict is strictly lowercase; `hit`/`na` require a valid note
  (non-empty after trim, not "-", at least 3 characters); a duplicate with conflicting verdicts is rejected.
  An invalid turn is rejected BEFORE being written to the transcript; the core performs exactly ONE content-retry
  via `ask` of the same adapter session, repeated invalidity → unresponsive. `hit` with a valid
  note counts as a new finding in `round_has_new_findings` (predicate input; stop-condition mechanics
  unchanged). Responses are EXCLUDED from participants' `render_bundle` payload.

### Phase B Predicates (FR-02)

- the round has new findings ⟺ ∃ round record with `structured.new_findings ≠ ∅`;
- the round has position changes ⟺ ∃ round record with `structured.position_changes ≠ ∅`.

The moderator score does not participate in the predicates.

### Anti-gaming borrowed (FR-04)

`borrowed[].source_ref` counts toward the kill proxy ONLY if the core allows the reference:
`source_ref` is an existing `seq` of a record by ANOTHER author containing the declaration
of the borrowed element in `structured.elements`. An unresolvable reference → borrowed
is discarded with a `system` record in transcript.

### Upheld critique (kill tie-break, track record)

An attack on element `X` of model `m` (someone else's `disagree` on `m:X`) is considered upheld if
the nearest subsequent `position_changes` by author `m` on `X` has `action ∈ {agree, withdraw}`.

## Conservative parsing semantics (TD 7.4)

A missing/corrupt structured block → the turn is accepted with empty `structured` +
a `system` warning record. The FR-02 predicates then treat the turn as "without findings/changes" —
this pushes stop conditions toward completion (the safe side).

## CONS-02 System records

- `type: system` with a digest draft (OPT-2): an extractive draft after the wave,
  payload in fenced blocks `participant-extract`; addressed to the moderator,
  not included in the participants' wave-bundle.
- `type: system` "phase D skipped" (OPT-3): reason for skipping (source model,
  number of elements, predicate values) — a record of the conditional phase D.

## Human-critic mode records (CONS-06, optional)

During `convene --human-critic`, the transcript may include turns with internal author id
`human-critic` (session-scoped transcript-level entity; it is not added to `session.participants`
and is not included in the `adapters.yaml` registry):

- types: only `attack` (phase B) and `redteam_attack` (phase D), at most 1 turn per
  wave; submission via the moderator file `round --human-turn-file` with mandatory
  attestation `human_approved: true` (audit, not access control);
- `anon_id` is from the shared `create_anon_map` space (presentation-id);
  membership invariant: a participant-visible record with author outside `anon_map` ->
  ProtocolError (fail-closed);
- human records are excluded from quorum/family diversity, track-record/strengths/
  observations, kill candidates, nf/pc stop predicates, and upheld/metrics
  (policy 2: impact on kill only through model withdraw);
- `type: system` - mode events: entering awaiting_human, accepting a turn with
  attestation, wait-cap exhaustion (awaiting_moderator_decision), and every
  moderator decision (`--human-continue-wait` / `--human-skip-wave` /
  `--human-withdraw`), phase D is mandatory after a human turn in B (E8);
- the waiting pause is persisted in `session.json`: `wall_clock.paused_sec` (DOES NOT shift
  `started_at`); `status` exposes `awaiting_human`, `paused_total_sec`,
  `real_elapsed_sec`, `active_elapsed_sec`.
