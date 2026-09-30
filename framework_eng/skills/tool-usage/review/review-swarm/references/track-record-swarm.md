# Swarm track record — `.swarm-track-record/` (FR-12, AC-12, TD §5.7, §8.2)

A durable repository-level store for swarm statistics. Survives `close`
of any session, excluded from cleanup-checkpoint; does NOT mix with the consilium
`.consilium-track-record/` (FR-01e: the tools have different observations files).
Contents are runtime data and are not committed to git.

```
.swarm-track-record/
├── observations.jsonl   # source of truth, append-only
├── strengths.json       # generated cache/report (not trusted on read)
├── gate-outcomes.jsonl  # durable trace of blocking gate outcomes (F-006)
└── config.json          # counters: reviews_completed, light_reviews_completed,
                         # calibration_every (lives in config, not in code, TD §8.2)
```

## Gate outcomes (`gate-outcomes.jsonl`, F-006 E2E-02)

The outcome of the blocking gate pass is recorded by the core on `close` (idempotently,
the `gate_outcome_recorded` flag in the session) and survives deletion of the ephemeral
session directory - `report.md` with the "Gate pass" section is deleted together with the
session, so the boolean `gate_pass` in observations is not enough. Record:

```json
{
  "session_id": "swarm-...", "tier": "light", "mode": "acceptance",
  "caller_id": "claude-opus", "caller_family": "claude",
  "reviewer_id": "codex-gpt", "status": "approved",
  "iterations": 1, "max_iterations": 3,
  "dispositions": {"F-001": "agree"}, "verdicts": [...],
  "conditional": null, "escalation": null, "reassignments": [],
  "recorded_at": "2026-07-30T..."
}
```

`status` is the final gate status (`approved | blocked | escalated |
conditional | pending`) at the moment of close; `conditional` is the reviewer’s conditions and
the Orchestrator’s decision on `conditional_accept` (F-007); `reassignments` is the
log of gate reviewer reassignments (F-012). The pair `caller_id` /
`reviewer_id` preserves a verifiable identity trace after cleanup;
`caller_family` is compatibility metadata and not proof of independence.
The file is not part of the strength-
projections and is not read by the protocol - an audit trail.


The storage layer is the harness `review-harness/scripts/track_record.py`
(parameterized by `storage_dir`); observation assembly and projection are handled by the swarm
core `swarm_core.py` (tool protocol). The session sequence number field is
`review_seq` (= `reviews_completed + 1` at the time of recording).

## Observation (one per participant per session)

Written by the core on `report`/`close` (idempotent: the `track_record_written` flag
in the session; retrying `close` and `close` after `report` does not duplicate entries).

```json
{
  "review_session_id": "swarm-...", "date": "2026-07-29", "review_seq": 7,
  "participant_id": "claude-opus", "family": "claude",
  "role": "security",
  "category": "security",
  "forced": false, "gate_pass": false,
  "findings_unique_confirmed": 2, "findings_unique_unconfirmed": 1,
  "findings_nonunique": 3, "nonunique_missed": 1,
  "upheld_as_author": 2, "overruled_as_author": 1, "unvalidated": 1,
  "attacks_made": 4, "attacks_confirmed_overrule": 1,
  "auto_confirmed_overridden": 0,
  "fixed": 0, "partially": 0, "not_fixed": 0,
  "nit_count": 1, "in_lens_count": 4, "out_of_lens_count": 1,
  "quota_mode": null, "quota_fallback_reason": null,
  "calibration_run": false
}
```

Aggregation of counters from session state (`swarm_core.build_observations`):

- **Finding outcome**: non-unique auto-confirmed (>=2 blind models, FR-06)
  - confirmed without a thread; unique - by thread: `confirmed`/`reclassified` ->
  confirmed; `withdrawn` -> not confirmed (an attack occurred, the finding did not
  survive); `open` (degradation, TD §11) and findings without dedupe/thread (early
  close, routed from rounds 2-4) -> `unvalidated` (F-03, R-Final): the attack did
  NOT occur - the finding is written under the separate `unvalidated` counter and
  does NOT count toward `overruled_as_author`/`findings_unique_unconfirmed` or the
  denominators of `accept_rate`/`upheld_rate_as_author` (FR-12: upheld = survived the attack);
  `contested` - by arbitration decision (`upheld`/`reclassified` -> confirmed,
  `overruled` or no arbitration -> unresolved disagreement -> no, FR-08).
- `nonunique_missed` - auto-confirmed clusters where the participant is NOT the author
  (a missed confirmed finding by them; coverage denominator).
- `upheld_as_author`/`overruled_as_author` - unique findings by the author,
  that survived / did not survive the attacks of rounds 2-4.
- Attack - `overruled` vote in the waves of rounds 2/4 (`upheld` - no attack);
  `attacks_confirmed_overrule` - attacks on findings with the final result "not confirmed".
- `auto_confirmed_overridden` - author findings in clusters where auto-confirmation was
  removed by the Orchestrator (FR-06).
- `fixed`/`partially`/`not_fixed` - re-review dispositions (T-12 draft:
  read from `session["rereview"]`, `introduced_new_issue` -> `not_fixed`).
- `nit_count` - findings of severity P4; `in_lens_count`/`out_of_lens_count` -
  in-lens/out-of-lens split (shows whether the lenses are working, decision #7).
- `category` - the participant's modal finding category (reporting attribute).
- `quota_mode`/`quota_fallback_reason` - quota selection marker (FR-14;
  quota-blind rotation is marked, not a silent evaluation).

## Strengths projection (`swarm_core.compute_swarm_strengths`, read-only)

- Cell is `(participant_id × role)`; `category` is an observation attribute, NOT
  a formula cell (FR-12).
- Decay: `w = 0.5^(k/8)`, `k = max_seq − review_seq` (harness
  `observation_weight`, half-life 8 is inherited).
- `n_eff` is the number of clean observations for the cell; `n_eff < 3` (`STRENGTHS_FLOOR`) →
  round-robin on assignment (rotation T-07 reads `n_eff` through
  `lens_history`, argmax-by-strengths is absent — RISK-06).
- Score metrics:
  - `accept_rate = Σw·unique_confirmed / Σw·(unique_confirmed + unique_unconfirmed)`;
  - `upheld_rate_as_author = Σw·upheld / Σw·(upheld + overruled)`;
  - `score = 0.6·accept_rate + 0.4·upheld_rate_as_author` (weights `SCORE_W_*`
    are inherited from the harness; if one is None, use the other metric, if both are None — 0).
- Reporting metrics (NOT included in score):
  - `overrule_precision_as_attacker = Σw·attacks_confirmed_overrule / Σw·attacks_made`
    (attack quality is a separate axis from authorship, TD §5.7);
  - `coverage_nonunique = Σw·nonunique / Σw·(nonunique + nonunique_missed)`;
  - `precision = Σw·(unique_confirmed + nonunique) / Σw·total_proposed`;
  - `fixed_rate = Σw·fixed / Σw·(fixed + partially + not_fixed)`;
  - `nit_count`, `in_lens_count`, `out_of_lens_count` — decay-weighted sums.
- Empty denominator → `None` (no data), not division by zero.

## Markers outside strength formulas

- `forced` (FR-11), `gate_pass` (FR-16/T-13): the observation is excluded from
  the projection ENTIRELY (TD §5.7 — assignment/role was not a free choice
  of the model; without attachment with a discount, unlike a tainted consilium).
- `auto_confirmed_overridden` (FR-06), `calibration_run` (TD §8.2): written to
  observations, but not inputs to formulas and NOT a basis for excluding the observation.
  Calibration observations must feed statistics — otherwise the sample is biased
  (FR-15: the quota "calibrates both triage and statistics", RISK-07).

## Calibration quota (FR-15, TD §8.2, N = 4)

- `config.json → light_reviews_completed = c`; if
  `(c + 1) % calibration_every == 0` — the review is executed by the swarm regardless of
  triage (`swarm.calibration_due`, `triage` → `reason: calibration_quota`).
- `calibration_every = 4` is seeded in `config.json` on the first track
  record write and then is revised by editing the config, not the code (the revision
  checkpoint is after ≥ 20 light reviews, the Orchestrator's decision with a recorded reason).
- Calibration run: `convene --calibration-run` → `calibration_run: true`
  in the session observations.
- Counters: `reviews_completed` increments on track record
  write (close/report of the full swarm); `light_reviews_completed` — on close of a light
  review (T-13, the same `bump_counter`).

## Lens history for rotation (`swarm_core.lens_history` → `assign_lenses`)

`convene` reads the cell history from `observations.jsonl`
(`swarm.load_lens_history`; strengths.json is not trusted on read —
F-04 of the convening is inherited):

- `lenses_used` — the current no-repeat cycle: the distinct suffix of roles
  for free (non-forced/gate_pass) observations in ascending `review_seq`
  (role repeat = cycle boundary: rotation does not assign a lens until the pool is exhausted);
- `n_eff` — from the strengths projection; empty cells (< 3) → round-robin.
