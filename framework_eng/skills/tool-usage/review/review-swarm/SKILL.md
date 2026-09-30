---
name: review-swarm
description: "MUST use WHEN a code diff needs second-opinion or multi-model review: light tier = one eligible independent cross-family reviewer selected from enabled participants outside the caller family (also the executor of blocking acceptance-bound review and the finalization gate APPROVE_COMPLETION), full tier = divergent swarm of all enabled models with dedup, validation rounds and arbitration. Findings must have code locations; architectural disputes without a location belong to agent-consilium."
capabilities: review,code-review,agent-governance,multi-model-review,cross-provider
---

# Swarm review (review-swarm) — rules for the caller

This skill is the caller agent playbook (Orchestrator, primary agent) that
launches code review for a diff in the light tier (one eligible reviewer, not the
caller) or the full swarm (all enabled models), conducts rounds, resolves edge
cases, and accepts the disposition of findings. The core mechanics and data
schemas are not documented here — the caller does not need them (see "What the
caller does NOT need to know").

## What it is and governance

- **Two tiers that differ in width, not model quality** (FR-15):
  - **light** — one reviewer from the enabled/eligible participants of another
    family relative to `--caller` (the successor of the gate rule
    `cross-provider-review.md`); one review call + re-review of fixes;
  - **full swarm** — all enabled registry adapters (including the caller's
    family): blind round 1 with lenses, mechanical dedup, validation rounds
    2-4, Orchestrator arbitration, re-review of fixes, clustered report without
    convergence to a single opinion.
- **Advisory for findings (advice-only).** Findings from the swarm by themselves do not block
  acceptance; the Orchestrator makes the dispositions. Exception — gate mode
  (FR-16): the light tier performs a **blocking** acceptance-bound review and
  finalization gate `APPROVE_COMPLETION` (see "Gate inheritance").
- **The Orchestrator is you, not a script.** The core (`scripts/swarm.py` + `swarm_core.py`)
  controls deterministic invariants (state machine, turn limits, anonymization,
  diversity quorum, wall-clock budget, paired cleanup) and fails closed; the
  core predicates cannot be bypassed. Deduplication of edge cases, evidence-based
  arbitration, dispositions, and the final decision are yours.
- **Participants are read-only** and work only with sandbox copies (focused-paths,
  NOT `--full-context`); the diff is delivered as a file in the sandbox
  (`review.diff`), not as prompt text.
- All exchanges are structured turns according to reference schemas; free text is
  allowed only in `rationale`. Free-form multi-agent "chats" are forbidden (FR-03).

## When to call

- **Diff code review** before acceptance: an independent second opinion
  (light tier) or a divergent multimodel review (swarm).
- **Acceptance-bound artifacts and finalization gate** — light tier with
  `--gate` (blocking semantics, inherited from the gate rule `cross-provider-review.md`).

**Boundary between modes (project rule):** a code review finding must have
`location` (file:line) — the core rejects a finding without it or with an unresolved
location before counting. A "controversial architectural decision" without location is
the territory of `agent-consilium`, not the swarm. Do not mix them: architectural disputes in the swarm
pollute the statistics of both mechanisms (RISK-09).

## Tiers and triage (FR-15)

1. **Deterministic criticality map** (`references/criticality-map.md`):
   diff paths that intersect the map patterns ALWAYS go through the full swarm;
   the `glob → lens` binding provides a forced lens for the critical path. The map is
   a tier escalator, not a taxonomy (verdict CMAP-01, 2026-07-30): critical
   is declared only on owner paths of an executable/governance invariant
   (semantic auth/crypto/payment/billing/migrations, gate protocol in
   both mirrors, owners of security/data-contracts invariants, dormant
   `secrets/**`). Managing the map consists of three one-way artifacts
   (metrics → map-proposal → executable map), a mandatory load test
   before writing, guard tests `tests/integration/test_criticality_map.py`,
   a directed coverage invariant; rationale and update policy —
   `tasks/agentic-operations/CMAP-01-criticality-map/map-proposal.md`.
2. **Gray zone** (no intersection with the map) — your rubric: you are already reading
   the diff; there is no separate evaluation model and there does not need to be one. The
   fail-closed core requires an explicit `--tier light|swarm` choice.
3. **Calibration quota (N = 4)**: every 4th light review is run by the full swarm regardless of triage (counter `.swarm-track-record/config.json
   → light_reviews_completed`, `calibration_every` in the config) — it calibrates both
   triage and statistics (otherwise the sample would be biased: only
   difficult items would be checked by the swarm, RISK-07). The calibration run is marked
   `convene --calibration-run` and recorded in the track record.

```bash
SKILL_DIR="<абсолютный каталог навыка review-swarm из каталога доступных skills>"
test -f "$SKILL_DIR/scripts/swarm.py"
python3 "$SKILL_DIR/scripts/swarm.py" triage --paths <пути diff/focused-набора> [--tier light|swarm]
# → {"tier", "reason": criticality_map | calibration_quota | orchestrator_rubric,
#    "calibration", "hits", "forced_lenses"}
```

## Invocation Lifecycle

Commands (core is `scripts/swarm.py`, direct CLI from the repository root).
`convene` always requires an explicit valid `--caller` as a policy assertion
of the trusted agent environment:

```bash
SKILL_DIR="<абсолютный каталог навыка review-swarm из каталога доступных skills>"
test -f "$SKILL_DIR/scripts/swarm.py"

python3 "$SKILL_DIR/scripts/swarm.py" doctor --json                 # pre-flight: registry, healthcheck, quorum
python3 "$SKILL_DIR/scripts/swarm.py" triage --paths <paths> [--tier light|swarm]
python3 "$SKILL_DIR/scripts/swarm.py" convene --tier light|swarm --diff <diff-файл> \
    --paths <focused paths> --caller <id участника реестра> \
    [--gate acceptance|completion] [--gate-reviewer <id>] \
    [--timeout-sec 900] [--calibration-run]
python3 "$SKILL_DIR/scripts/swarm.py" attack <session_id>           # round 1 (blind, parallel)
python3 "$SKILL_DIR/scripts/swarm.py" dedup <session_id> [--journal-file склейки.json]
python3 "$SKILL_DIR/scripts/swarm.py" assess <session_id>           # round 2: verdict waves
python3 "$SKILL_DIR/scripts/swarm.py" rebut <session_id>            # round 3: author responses (1 turn)
python3 "$SKILL_DIR/scripts/swarm.py" vote <session_id>             # round 4: final votes
python3 "$SKILL_DIR/scripts/swarm.py" lineage-query <session_id> --provider-turn-id <provider_turn_id>
python3 "$SKILL_DIR/scripts/swarm.py" clarify <session_id> --shard-task-id <task_id> \
    --idempotency-key <key> --prompt-file <уточнение.md>
python3 "$SKILL_DIR/scripts/swarm.py" arbitrate <session_id> --finding F-NNN --decision-file решение.json
python3 "$SKILL_DIR/scripts/swarm.py" report <session_id>
python3 "$SKILL_DIR/scripts/swarm.py" gate-verdict <session_id> [--dispositions-file д.json] [--claim-file claim.md] \
    [--conditional-decision confirm|reject] [--gate-reviewer <id>]
python3 "$SKILL_DIR/scripts/swarm.py" rereview <session_id> --finding F-NNN --fix-diff <fix.diff> [--developer-claim fixed]
python3 "$SKILL_DIR/scripts/swarm.py" status <session_id>
python3 "$SKILL_DIR/scripts/swarm.py" close <session_id> [--keep --keep-reason "<причина>"]
```

Step by step (full swarm):

1. **`doctor --json`** — pre-flight: availability of the CLI/adapters and diversity
   quorum (≥2 enabled participants from ≥2 family; if violated, `convene`
   for the full tier will refuse fail-closed — a swarm without diversity is pointless).
2. **`triage`** — tier (see above).
3. **`convene --tier swarm --diff ... --paths ... --caller <id>`** — creates
   a session: composition = all enabled (including your family — you participate as
   the Orchestrator, not the gate reviewer; the full swarm composition does not change), the core assigns
   each one **lens** from the `code-review` catalog (security, correctness,
   concurrency, performance, data-contracts, tests): forced lenses for critical
   paths are mandatory, the rest are assigned by balanced rotation (exploration,
   no repeats until the pool is exhausted, min n_eff of the "model × role" cell;
   argmax-by-strengths is intentionally absent). `--caller` is mandatory and must
   resolve in the registry; this is a policy assertion of a trusted agent environment, not
   authentication of a hostile caller. Family is preserved only as metadata/for diversity quorum.
4. **`attack`** — round 1: blind parallel attack. Participants do not see one another's findings
   or turns (blindness is a requirement for measuring uniqueness); each one
   receives the same context (diff file in the sandbox + focused paths) and their
   lens with a checklist. The checklist does not constrain the model: findings outside the lens   are accepted with `in_lens: false`. The core validates findings (`location` must
   resolve to the verifiable set; invalid ones are rejected before counting, with
   the reason recorded) and numbers accepted `F-NNN`.
5. **`dedup [--journal-file ...]`** — mechanical dedup by the core on
   (location, category) with an overlap window of 4 lines → three groups:
   **non-unique** (found by ≥2 blind models — auto-confirmed, free
   validation), **unique unconfirmed** (→ tour 2), **borderline**
   (strict overlap of ranges across different categories — printed to you).
   You resolve borderline cases: manual merge/split only through
   `--journal-file` with mandatory justification (dedup-journal session,
   fail-closed). For correlated false positives (≥2 models
   repeated the same mistake) — override auto-confirmation by recording
   `override_auto_confirm` with justification (marker `auto_confirmed_overridden`
   in the track record). **A repeated `dedup --journal-file` from state DEDUP
   is allowed** (E2E-F1): the delta is appended to the raw append-only journal,
   the result is a projection of the ENTIRE journal by a single fold in file
   order (duplicate entries are filtered out at fold input; the replay marker
   is in the `dedup_complete` checkpoint). From TOUR2 and later `dedup` is
   still unavailable (fail-closed).
6. **`assess`** — tour 2 (attack): neighboring models (not the author) render,
   for each unique unconfirmed finding, a verdict `upheld/overruled/reclassify/
   uncertain` with mandatory **new** evidence; the author is anonymous
   (`anon_id`).
   All `upheld` → the finding is confirmed early, tours 3–4 do not run on it.
7. **`rebut`** — tour 3 (author response, exactly one move): `maintain` (with
   counter-evidence) / `withdraw` / `accept_reclassify`.
8. **`vote`** — tour 4 (final vote, one move). Unresolved disagreement →
   `contested`.
9. **`arbitrate --finding F-NNN --decision-file ...`** — arbitration of contested
   severity ≥ major (P1–P2) **is mandatory** before reporting (the core will reject `report`).
   The decision is based on the code, not on rhetoric: `evidence_quote` (a
   verbatim quote of the disputed passage) and `location`, resolving to a real
   file, are checked by the core fail-closed; the decision is final, there is no
   second appeal round.
10. **`report`** — clustered report with attribution and statuses
    (confirmed/withdrawn/reclassified/contested; `unvalidated` — degradation by
    wall-clock, not a silent break). **No convergence to a single opinion**: no
    verdict, no kill, no consensus — disagreement is recorded, minority
    findings are the main value of the mode. The disposition of findings is up to you.
11. **`rereview --finding F-NNN --fix-diff ...`** — re-review of the fix by the **author of
    the finding**: a new focused session of the same adapter (the old one is not
    preserved), fix-diff is delivered as a file + mandatory `sync`; verdict
    `fixed/partially/not_fixed/introduced_new_issue` (the new issue goes into the
    shared pool). Conflict 'developer claims it is fixed / author says no'
    (mismatch with `--developer-claim`) → escalation to your arbitration
    (`arbitrate`, same evidence mechanism; decision is the final fix status).
12. **`close`** — always required (see cleanup discipline below).

Light tier: `convene --tier light` (the core selects an eligible reviewer from a
   different family than `--caller`; an empty pool is a rejection, literal
   self-review and caller-family review are forbidden. A reviewer family matching
   the actual author of the artifact is allowed only by the approved matrix and when
   completedcapability/effort floor, because the author and `--caller` are different identities) →
`attack` (1 review call) → `report` (using the template
`references/review-report-template.md`) → optional `gate-verdict` →
`rereview` on fixes → `close`. Tours 2–4 and dedup are absent - full-tier
commands are unavailable in a light session (fail-closed). **The criticality map is
not bypassed by a flag** (F-003): if paths/diff intersect with
`references/criticality-map.md`, `convene --tier light` fails closed
with an indication of the full tier - triage in the swarm is mandatory (AC-15).

## Tour Protocol 1–4 - move limits (FR-07)

- **Tour 1** = N calls (the number of enabled adapters), full context. After
  completion of tour 1, the parent session of each participant is sealed:
  no further questions are sent to it, and its provider checkpoint serves as
  an immutable basis for native forks.
- **Tours 2–4 are executed through native fork shards**. For each participant,
  one shard is created from its sealed checkpoint. Shards for different participants
  run in parallel, but tasks within a single shard are strictly
  sequential in one child provider session. `start` of a new independent
  session and artificial context copying are not a fallback: if the registry,
  capability probe, or lineage proof do not confirm native fork,
  the corresponding phase fails closed.
- **Tours 2–4 are focused** (finding + local fragment), an order of magnitude cheaper
  than tour 1; upper bound: tour 2 ≤ U×(N−1), tour 3 ≤ U×1, tour 4 ≤ U×(N−1),
  where U is the number of unique unconfirmed items; early stop when all
  `upheld` is mandatory.
- **2-message ceiling** after the initial attack: the author's response - message 1,
  final vote - message 2; a third message is impossible (the core rejects it).
- **Each move must carry new evidence**: repeating `(path, line, quote)`
  in the thread is rejected by the core (`stale_evidence`; whitespace normalization does not
  bypass the predicate).
- **No new findings in tours 2–4**: a new bug in a response is not
  accepted into the thread - it is routed to the general pool as a separate finding.
- An invalid structured move gets exactly one retry, then the participant becomes `unresponsive`
  (the protocol continues without it); the call timeout makes it `unresponsive` without retry.
- The `execution → snapshot → shard → task → provider turn`
  linkage is appended to `lineage.jsonl`. To analyze a late question,
  first reconstruct the original task and shard by provider turn via
  `lineage-query`. When the backend routed a question into a clarification-task,
  take its `shard_task_id` from `status.pending_clarifications` and continue
  that same child shard with the `clarify` command and a new stable
  `--idempotency-key`. Passing `clarify` the id of the original review-task is forbidden:
  the command accepts only an already routed pending clarification.
  Redirecting the clarification to another shard or to the sealed parent is
  forbidden; repeating the same key is idempotent.

## Orchestrator Responsibilities

- **Dedup edge cases - only with a journal**: every manual
  merge/split/override must be recorded in dedup-journal with a rationale
  (fail-closed; the core rejects entries not from the Orchestrator and without a reason).
  Do not silently override mechanical grouping. Edge-case pairs are printed by the first `dedup`; apply decisions on them with a REPEATED
  `dedup --journal-file` - the delta will be appended to the journal, and the final result will be recomputed by replaying the entire journal (resubmitting the same file is safe:
  duplicates are filtered out at fold input).
- **Arbitration - by evidence with code inspection**: you **must inspect the disputed
  place in the code yourself** (read-only) and quote it in the `evidence_quote`
  of the decision; decide based on the code, not rhetoric. The decision is final; escalation to
  a human is for material disagreements (those that change the final decision).
- **You initiate fix re-review** after dispositions: for each fixed
  finding - `rereview` (checked by the finding author); resolve conflicts by
  arbitration, hearing both sides based on the code.
- **Finding dispositions** (`agree/partial/disagree/withdrawn/out_of_scope`)
  are accepted by you based on real artifacts and evidence - the swarm report does not contain them
  (advisory). In the Claude → Codex/GPT route, additionally explicitly assess the overengineering risk for each finding (an inherited rule, see the template
  `references/review-report-template.md`).

## Gate Inheritance (FR-16, AC-16/AC-24)

The light tier is a descendant of the gate rule `cross-provider-review.md` and the executor of its
blocking gate semantics. Blocking authority and fail-closed guards are preserved:

- **Identity/capability rule**: literal caller/self-review and caller-family participant
  do not satisfy the gate. Gate roles are performed only by participants of another
  family with `gate_legal` in the registry.
  Direct CLI requires an explicit valid `--caller`; this is a policy assertion inside the
  trusted agent environment, not authentication of a hostile caller. The artifact author
  and the caller are different subjects; the author's identity is not substituted for the caller.
  The threat model boundary and the separate need for an external authenticated
  runtime contract are described in
  `references/design-decisions-e2e02.md`.
- **The full swarm does NOT replace the gate** (FR-15): an acceptance-bound artifact moved
  by triage into the swarm must receive a gate pass separately - by the light tier or by an
  explicitly assigned participant of a swarm from another family relative to the caller,
  which does not coincide with the caller and has `gate_legal`
  (`convene --gate acceptance|completion [--gate-reviewer <id>]`). Such a
  participant is a **dual role (AC-24)**: in round 1 they are an ordinary reviewer, and their
  findings and gate verdict are marked `gate_pass` and **excluded from the
  advisory statistics** track record. The fact and form of the gate pass are recorded in the
  review trace report (section "Gate pass").
- **Gate verdict is a separate structured call AFTER `report` and dispositions**
  (`gate-verdict`), ≤3 review/rework/delta iterations; on delta iterations the core
  runs `sync` sandbox before the call.
- **Empty dispositions when there are zero findings** (F-002): if the reviewer did not produce a single
  finding, a valid dispositions file is `{}` (passed explicitly via
  `--dispositions-file`); with a non-empty pool of findings, an empty file is rejected
  fail-closed.
- **`conditional_accept` is NOT approved** (F-007): conditional acceptance moves the
  gate to the intermediate `conditional` status; terminal `approved`
  is set only by your explicit decision - `gate-verdict
  --conditional-decision confirm` (conditions verified by code) or `reject`
  (disagreement → delta iteration/escalation by counter). The decision is recorded
  in session and review trace.
- **Gate reviewer reassignment** (F-012): if the assigned one becomes
  `unresponsive`, pass `gate-verdict --gate-reviewer <id>` — the replacement
  goes through the same floor (`enabled` + `healthy` + `family != caller-family` + not
  caller + `gate_legal`), and if necessary starts a new focused sandbox (cleanup
  pairing is preserved); recreating the session is not required. An active reviewer cannot be reassigned
  (fail-closed).
- **Durable trace of gate outcome** (F-006): on `close`, the outcome of the blocking gate
  (mode, caller, reviewer, status, iterations, verdicts, dispositions, escalation,
  reassignments) is written to `.swarm-track-record/gate-outcomes.jsonl` and
  survives deletion of the ephemeral session.

### Acceptance-bound protocol (inherited verbatim in meaning)

For acceptance-bound artifacts:

1. Launch an independent eligible reviewer (`convene --tier light
   --gate acceptance` or a full swarm with `--gate acceptance`).
2. Monitor via `status` if the review runs for a long time.
3. Check each finding against real artifacts and record the disposition
   `agree`/`partial`/`disagree`/`withdrawn`/`out_of_scope` (for gate - via
   `gate-verdict --dispositions-file`, required on the 1st iteration). In the Claude → Codex/GPT route, additionally explicitly record for each finding
   an overengineering risk assessment with evidence (using the template
   `references/review-report-template.md`); the overengineering risk conclusion itself must
   reference artifacts/evidence, and without them it is not grounds for not
   removing a confirmed risk.
4. Add your own findings as `C-01...` when needed.
5. If the source artifacts changed after rework - delta iteration
   (`gate-verdict` again; the core will run `sync` sandbox).
6. Stop at agreement (`accept`) or escalation after 3 iterations.
   `conditional_accept` is an intermediate status (F-007): make an explicit decision
   (`--conditional-decision confirm|reject`), auto-approve does not happen.
7. **🔴 MUST — `close <session_id>` as soon as the review is no longer needed** (see
   "CRITICAL: cleanup discipline"). This is not "when convenient" but a mandatory closure step:
   without it, the sandbox and session remain on disk forever. `close`
   is called even if the review ended in refusal/error.
8. Record the final report: unified findings, disagreements with both
   positions, iteration count, recommendation, session id, **cleanup status**
   and relevant status/log evidence. Before closing the task, run the CHECKPOINT from the
   "CRITICAL" section.

### Finalization Gate Protocol (blocking)

For final orchestrator completion review (`--gate completion`; prompt —
`references/final-orchestrator-completion-review-prompt.md`):

1. Prepare a completion claim with direct evidence (`gate-verdict
   --claim-file`).
2. Run a gate reviewer other than the caller and pass the evidence
   package.
3. Monitor via `status` if the review runs for a long time.
4. Review the findings and record your position.
5. Perform rework or provide evidence, then request a delta review
   for substantial changes (a repeat `gate-verdict`).
6. After 3 iterations with material disagreement — stop and escalate to the user with
   both positions and evidence (the core records `escalated` in the session and review
   trace).
7. **🔴 MUST — `close` after a documented PASS verdict or a user
   override.** Closing the gate-review is mandatory in ALL outcomes, including
   escalation after 3 iterations. Then run CHECKPOINT.

**Forbidden:**

- Announce completion without `APPROVE_COMPLETION`, a user override or
  a recorded escalation after 3 iterations. The gate reviewer during completion
  narrows its advisory authority: it does not determine strategy and does not apply
  fixes, but its `BLOCK_COMPLETION` blocks task completion; you cannot bypass the
  final completion gate.
- Close the task if there are `.swarm-sessions/` or live/with payload
  directories in `.review-sandboxes/`. A directory containing only a stable
  invocation lock is a terminal closed tombstone, not an active
  sandbox, and by itself does not block completion. The harness determines the canonical tombstone form; the caller must not duplicate the lock-file name.

## Observability: heartbeat/liveness and progress.jsonl (FR-13)

`status <session_id>` (JSON) at any moment shows: state
machine state, tier, participants with lenses and call/retry counters,
unresponsive, finding counters (accepted/rejected/routed/
auto-confirmed), thread statuses, arbitrations, gate state, wall-clock
(`elapsed/budget/status ok|warn|exceeded`), plus:

- **activity fields and each participant's liveness class**: `activity
  {last_activity_at, last_heartbeat_at}` and `liveness.class`:
  - `active` — “the model is thinking”: CLI events are flowing (activity fresher than the silence threshold, default 120 s — `--silence-threshold-sec`);
  - `quiet` — silence longer than the threshold while the watcher is alive: a diagnostic marker,
    **not kill** (a long thinking step is legitimate; intervention is
    your decision);
  - `dead_watcher` — heartbeat older than 10 s: the process adapter is not alive (hard
    signal);
- **`last_checkpoint`** — the last `progress.jsonl` checkpoint of the session.

`progress.jsonl` (in the session directory, append-only) — semantic checkpoints at
stage boundaries, not a stream of consciousness: `convened`, `tour1_complete`,
`dedup_complete`, `tour2_complete`, `tour3_complete`, `tour4_complete`,
`arbitration_complete`, `report_ready`, `rereview_complete`, `gate_complete`,
`closed`. Line: `{ts, session_id, tool: "swarm", checkpoint, summary,
counters}` — counters are a snapshot (invocations, findings, unique_unconfirmed,
unresponsive). Read the file directly or the last checkpoint via `status`.

## Track record (FR-12)

Durable `.swarm-track-record/` (`observations.jsonl` — source of truth,
`strengths.json` — generated cache, `config.json` — counters,
`gate-outcomes.jsonl` — durable trace of blocking gate outcomes, F-006); NOT
mixed with consensus and excluded from cleanup-checkpoint. Written by the core on
`report`/`close` (idempotently): one observation per participant per session with
metrics (unique confirmed/unconfirmed, non-unique and coverage,
upheld/overruled as author, attacks and their precision, share reaching `fixed`,
nit P4 separate counter, in-lens/out-of-lens, quota mode,
calibration_run). Formula cell is “model × role” (lens), decay half-life 8,
floor n_eff ≥ 3; category is an observation attribute, not a cell.

**Entirely excluded from strength statistics** are observations with markers
`forced` (forced lens for the critical path - not a free model choice) and
`gate_pass` (dual role of gate reviewer). Markers `auto_confirmed_overridden`
and `calibration_run` are recorded, but they do NOT exclude the observation. Empty cells
(< 3 observations) -> round-robin when assigning lenses. Detailed scheme —
`references/track-record-swarm.md`.

## 🔴 CRITICAL: cleanup discipline

> **Level: CRITICAL / MUST.** Following the precedent of the gate rule `cross-provider-review.md`, reinforced
> by a dual control loop. There is no automatic cleanup - neither atexit, nor signal
> handlers, nor TTL/age-sweep: only parity and checkpoint. Orphaned
> sandboxes and session directories remain on disk forever.

| Requirement | Description |
|-----------|----------|
| convene↔close parity | Any `convene` MUST have a paired `close` on ANY termination path: report, gate escalation, refusal to review, adapter error, wall-clock degradation |
| start↔close participant parity | Each participant is an adapter session with its own sandbox in `.review-sandboxes/`; the swarm `close` closes EVERY participant, including focused re-review sessions |
| Cleanup fail-closed | If a participant close fails, the session is NOT deleted and is NOT marked closed: `cleanup.status=failed`, exit code 5; repeated `close` is idempotent - **retry is mandatory** until cleanup succeeds |
| `--keep` for forensic use only | `close --keep --keep-reason "<written reason>"` preserves the ephemeral session directory - ONLY for forensic/debug with an explicit written reason (without a reason the core will refuse) |
| Lock-only tombstone | After a successful adapter `close`, a review directory is allowed that `adapter_contract.is_lock_only_tombstone()` recognizes as canonical: payload is already removed, provider session is closed, lock is not held. This is a terminal coordination state, not an active sandbox |
| Cleanup report | The task's final report records the cleanup status of each session and each participant: `closed` or justified `kept` |

**✅ DOUBLE CHECKPOINT before closing the task (mandatory):**

```bash
ls -1 .swarm-sessions/ 2>/dev/null | wc -l    # expected 0
PYTHONPATH="$SKILL_DIR/../review-harness/scripts" python3 - <<'PY'  # expected 0 payload/live sandbox
from pathlib import Path
from adapter_contract import REVIEW_ROOT, is_lock_only_tombstone
root = Path(REVIEW_ROOT)
active = [str(path) for path in (root.iterdir() if root.is_dir() else ())
          if not is_lock_only_tombstone(path)]
print(len(active))
PY
```

If the first check or the number of payload/live sandboxes is not 0 - the task is NOT
complete in terms of cleanup: close the remaining sessions and only then close the
task. Do not manually delete a lock-only tombstone or weaken the classifier:
an unexpected file, directory, symlink, or other payload must be treated as active and
block completion. `.swarm-track-record/` is durable storage, excluded from checkpoint
EXCLUDED: do not delete.

## Failure Diagnostics

- **Unresponsive participant**: invocation timeout (per-invocation, default 900 s,
  `--timeout-sec`) → `unresponsive` without retry; adapter error or invalid
  structured turn → exactly one retry → `unresponsive`. The protocol continues
  without it; the fact is recorded in `status`, report incidents, and observations.
- **Core failures (fail-closed)**: exit 2 — protocol/state machine/
  validation/quorum violation; exit 3 — emergency termination (wall-clock); exit 5 —
  cleanup failed (retry `close`). Do not try to "work around" the failure with manual edits
  to session state.
- **Wall-clock budget (NFR-08)**: swarm session `max(3600, W×(T+240)+1800)` s,
  hard cap 16620 s, warning at 75 %; W is the wave model,
  recomputed after `dedup` by the actual U, and for keyed waves — by
  exact upper bounds of scheduler slots for rounds 2–4, taking into account the cap and the depth
  of the FIFO lane. When reaching "budget
  minus one wave", new waves do not start: the session is marked `degraded`,
  bring it to a report from the current state (unfinished threads are
  `unvalidated`, not a silent drop) and close it.
- **Where to look**: `status <id>`; turn prompts — `.swarm-sessions/<id>/prompts/`;
  participant adapter logs and history — adapter `status`/`log` from
  `review-harness/scripts/adapters/` by participant `review_id`.

## Security

- Reviewers work in copied sandbox workspaces (`.review-sandboxes/`),
  not in the real project; modifying the real project is forbidden.
- Default is focused-paths copies, NOT `--full-context`.
- Adapter prompts and system prompts include read-only instructions; codex —
  read-only sandbox mode; claude — only `Read,Grep,Glob,LS`; kimi —
  built-in read-only agent profile.
- The orchestrator remains responsible for rework and the final decision; for
  final completion review, an independent `APPROVE_COMPLETION` is required before
  the task can be declared complete.

## What the caller does NOT need to know

Underlying details are for the mechanism maintainers (all in `references/`):

- `references/finding-schema.md` — finding schema, P1–P5 severity table,
  location validation, dedup.
- `references/verdict-schemas.md` — schemas for rounds 2–4, arbitration,
  re-review, and the gate verdict.
- `references/track-record-swarm.md` — `.swarm-track-record/` schema, metrics
  and strengths projection, calibration quota.
- `references/criticality-map.md` — machine-readable criticality map and
  forced-lens binding (tariff escalator by CMAP-01 verdict; justifications for
  globs and update policy —
  `tasks/agentic-operations/CMAP-01-criticality-map/map-proposal.md`,
  guard tests — `tests/integration/test_criticality_map.py`).
- `references/design-decisions-e2e02.md` — recorded design decisions for
  E2E-02 findings (F-010: semantics of `content_retry_decision`; F-011:
  bounded policy assertion `--caller` and the threat model boundary).
- `references/report-template.md` — full swarm report template;
  `references/review-report-template.md` — light-tariff report template
  (inherited, including the overengineering risk assessment field).
- `references/review-prompt.md`,
  `references/final-orchestrator-completion-review-prompt.md` — inherited
  reviewer and finalization gate prompts.
- Common layer (adapters, the `start/ask/status/close/sync` contract, the
  `adapters.yaml` registry, track record, liveness/progress, quota selection) —
  `review-harness` (library, not a tool): `{{runtime-ref:framework/skills/tool-usage/review/review-harness/SKILL.md}}`,
  canonical contracts — `review-harness/references/adapter-contract.md` and
  `track-record-schema.md`.

---
depends_on:
  - framework/skills/tool-usage/review/review-harness/SKILL.md
---
