# Consilium track record schema — pointer + observation fields

> **The canonical location of the shared track record schema has moved (RVSW-01, T-14):**
> `review-harness/references/track-record-schema.md` — a schema shared by both
> tools: storage_dir parameterization, decay formulas (half-life 8),
> floor `n_eff ≥ 3`, score weights 0.6/0.4, exploration budget, prohibition
> of self-declaration of strengths. This file preserves the consilium-specific set
> of observation fields.

Durable storage `.consilium-track-record/` at repository level: survives
`close` of any session and is excluded from cleanup-checkpoint (FR-10).

```
.consilium-track-record/
├── observations.jsonl   # one record per (participant × role × consilium)
├── strengths.json       # GENERATED read-only projection (manual editing prohibited)
└── config.json          # {"consiliums_completed": N} — counter for exploration budget
```

## Observation record

Written by the core on `close` (and on abnormal termination) from transcript:

```json
{
  "consilium_id": "cons-...", "consilium_seq": 7, "date": "2026-07-28",
  "participant_id": "claude-opus", "family": "claude",
  "role": "architecture",
  "moderator_id": "primary",
  "moderator_is_participant": false,
  "findings_accepted": 3, "findings_withdrawn": 1,
  "critiques_upheld": 2, "critiques_overruled": 1,
  "model_killed": false, "unresponsive_events": 0,
  "outcome": "verdict",
  "phase_d_skipped": false, "phase_d_saved_invocations": 0
}
```

- `phase_d_skipped` / `phase_d_saved_invocations` (CONS-02 OPT-3, metric RISK-D06):
  the fact that phase D was skipped and the number of saved invocations; written to EVERY observation
  of the session regardless of predicate outcome (skip=true/false) — a measurable hypothesis
  about the frequency of triggering conditional phase D.
- `domain` (CONS-03 E-2): id of the session's domain package (default `architecture`);
  observations from older consiliums without the field are interpreted as `architecture`.
- `checklist_stats: {hit, clear, na}` (CONS-03 E-4): aggregate of risk-checklist verdicts
  over the participant's ACCEPTED moves for the session (rejected moves do not enter the transcript and
  are not counted; `final_statement` is excluded). Data source for revising package composition
  (RISK-E02); the strengths key and projection schema do not change.

- `consilium_seq` — serial number of the consilium (base of recency decay);
- `moderator_is_participant = true` when `moderator_id` matches one of the participants
  (the primary agent spoke through its adapter, FR-05) — segregation marker TBD-02;
- `outcome`: `verdict` | `closed_without_verdict` | `terminated:<reason>`.

## strengths projection

The formulas are general, see `review-harness/references/track-record-schema.md`
(decay `w = 0.5^(k/8)`, segregation of tainted items with a discount ×0.5, `n_eff`,
`accept_rate`/`upheld_rate`, `score = 0.6·accept + 0.4·upheld`, floor
`n_eff ≥ 3` → fallback round-robin, exploration every 4th consilium by
`config.json → consiliums_completed`). The consilium projection is
`review-harness/scripts/track_record.py: compute_strengths` with
`seq_field="consilium_seq"`.
