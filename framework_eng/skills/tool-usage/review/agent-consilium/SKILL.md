---
name: agent-consilium
description: "MUST use WHEN an architectural or requirements question has material trade-offs that need structured multi-model deliberation (Claude/GPT/Kimi) with adversarial critique and moderator synthesis; advisory-only, never an acceptance gate."
capabilities: architecture,requirements,agent-governance,multi-model-deliberation,cross-provider
---

# Architectural Consilium — Rules for the Caller

This skill is the playbook for the calling agent (primary agent), which convenes the consilium,
moderates its phases, and receives the advisory verdict. The protocol mechanics, data schema, and
adapter contract are NOT documented here — the caller does not need them (see the section
"What the caller does not need to know").

## What it is and governance

The consilium is a multi-model discussion of material trade-offs in architecture or requirements: several frontier models
from different families (according to the single registry `review-harness/adapters.yaml`) work in a shared context under the protocol
"proposal → critique → response to critique", followed by moderator synthesis and a verdict with
minority report.

- **Advisory-only (FR-13).** The verdict is NOT an acceptance/finalization gate and not a condition
  for any gate. The decision is made by the primary agent (or a human), and the verdict is input for it.
- **The moderator is the primary agent, not a script.** You author the digests, synthesis, and verdict.
  The core (`scripts/consilium.py`) enforces the protocol's formal invariants and
  fails closed when they are violated — the core predicates cannot be bypassed.
- **Participants are read-only** and work only with sandbox copies of the materials (by default
  focused-paths, NOT `--full-context`); modifying the real project by a participant is prohibited.

## When to convene

Convene the consilium at **material forks with real trade-offs**:
multiple justified options, discernible long-term consequences, a non-obvious
trade-off between axes (performance/complexity/maintenance cost). The value
of the consilium lies in adversarial debate between different model families: the recommended composition is
≥2 participants from ≥2 different family (this is also the quorum: if not met, the core will
fail-closed); three families is preferable whenever possible.

For business interpretations, scope boundaries, MUST/SHOULD/MAY levels, and verifiability, use
the `requirements` domain; it does not make technical design decisions. For architectural
boundaries, API, and data flow, use the appropriate architectural domain.

## When NOT to convene

- **Small and local decisions** (naming, local refactoring, an obvious choice without
  trade-offs) — the cost of the consilium is 16–25 adapter calls (default with 3 participants,
  hard cap 31) and tens of minutes of wall-clock time; for a local fork, that is
  disproportionate.
- **As a replacement for the primary agent's own judgment.** The consilium is an advisory input,
  not an oracle: analysis of the verdict and the final decision remain with you.
- **As an acceptance verdict.** The consilium does not confirm or block work acceptance;
  project review/finalization gates exist for that.

## How to formulate the question

The quality of the verdict is determined by the quality of the question in `convene --question`. A good
question contains:

1. **Context**: what system/module it is, what constraints and non-functional requirements
   apply (links to real repository artifacts).
2. **The options being chosen between** — or an explicit "open field" if there are
   no options yet.
3. **Selection criteria**: which axes to compare on (cost, complexity, migration, risk).

Pass materials via `--paths` (focused-paths), not full context.

**Antipattern** — a vague question ("how is it best to implement authorization?"): participants
will build models under different hidden assumptions, the debate will turn into cross-criticism
of incomparable solutions, and synthesis will degenerate into a compilation of platitudes. If the question does not
fit into a specific fork — narrow it down yourself first.

## Call Lifecycle

Commands (core — `scripts/consilium.py`, run from the repository root):

```bash
SKILL_DIR="<absolute directory of the agent-consilium skill from the available skills directory>"
test -f "$SKILL_DIR/scripts/consilium.py"

python3 "$SKILL_DIR/scripts/consilium.py" doctor --json        # pre-flight healthcheck of the registry
python3 "$SKILL_DIR/scripts/consilium.py" convene --question "<architectural question>" \
    [--caller <id>] [--paths <focused paths>] [--timeout-sec 900]
python3 "$SKILL_DIR/scripts/consilium.py" round <session_id> [--digest-file f] \
    [--kill <participant_id>] [--synthesis-file f]
python3 "$SKILL_DIR/scripts/consilium.py" status <session_id>  # phase/round/live models/call counter
python3 "$SKILL_DIR/scripts/consilium.py" verdict <session_id> --decision-file f [--escalate]
python3 "$SKILL_DIR/scripts/consilium.py" close <session_id>   # cleanup parity (FR-10)
```

Step by step:

1. **`doctor --json`** — pre-flight: adapter availability and quorum. `convene` runs
   doctor automatically and excludes an unavailable participant with an explicit log entry; if
   quorum is violated, it fails closed.
2. **`convene --question ... [--caller <id>] [--domain <id>] [--paths ...]`** — creates a session.
   **Composition rule (FR-05): the roster is ALWAYS all `enabled` entries from `review-harness/adapters.yaml`
   (including the caller's family — you participate as an ordinary member through your adapter)
   + you as moderator.** The moderator is not assigned — it is whoever convened the consilium;
   there is no `--moderator` flag. `--caller <id>` records the caller id in
   `session.json.moderator_id` and track record (default: `primary`). There is no `--members`
   flag: the roster is strictly all enabled (period, not an option); `--caller`,
   when it matches a registry participant, is marked as your model participant; a caller
   outside the registry is a legitimate moderator without an adapter (warning). The core assigns phase A roles.
   **`--domain <id>`** (CONS-03) selects a domain package from `review-harness/domains.yaml`
   (default: `architecture` — current behavior): the package's roles, lenses, risk checklist, and
   evidence requirements are injected into participant prompts; a snapshot of the directory is
   recorded in `session.json` (editing `review-harness/domains.yaml` in the middle of a session does not affect it).
   Unknown id — refusal with a list of available ones.
3. **`round <id>`** — phase A: each participant blindly puts forward their own version of the solution
   (the only wave without a digest).
4. **Phase B cycle (2 waves per round):**
   - After each wave the core generates an **extractive draft digest**
     (`digests/draft-*.md`, CONS-02 OPT-2): only facts from the turns (attacks, changes
     of position, borrowings, findings) in fenced `participant-extract` blocks,
     anonymized. The draft is your assistant, NOT auto-insertion: read
     `bundles/wave-*.bundle.md` and the draft, check against the transcript and write the
     **final digest** yourself (the core rejects the wave without it - digest-gate
     fail-closed; real ids and exceeding `context_budget` are rejected) →
     `round <id> --digest-file ...` (wave "attack");
   - digest again for the new bundle → `round` (wave "response to criticism").
   - After each round the core independently evaluates the stop conditions (convergence ≤2 live
     models / round limit 2→3→4 / stalemate) and, if live >2, prints a
     **kill recommendation** with a deterministic candidate and proxy metrics.
   - Kill (if recommended): `round <id> --kill <candidate> --digest-file ...` —
     the core applies ONLY the deterministic candidate (anything else is a refusal, exit 4) and
     runs a final-statement wave for the excluded model.
5. **Phase C**: you write the synthesis of the final model. The format is mandatory (CONS-02 OPT-3):
   item by item `- [E<n>] <text> (refs: seq:<n>, …)` (ranges are forbidden, at least
   ≥1 item required) + section `## Rationale for excluding contributions` (why the contribution of each
   live model that was not included is excluded) → `round <id> --synthesis-file ...`.
   Any text outside the template and sections is fail-closed refusal.
6. **Phase D (red-team)** — conditional: the core skips it only if ALL elements
   of the synthesis are traceable to a single live source model
   (membership check by structured.elements; any uncertainty → D
   runs). When skipped — transition C→E without calls, the reason in transcript and
   verdict. Otherwise: digest → `round <id> --digest-file ...` (1 attack on the merge,
   role by cross-rotation); digest → `round` (authors confirm/challenge
   non-distortion of contribution). Fix defects locally, without restarting the cycle.
7. **Phase E**: `verdict <id> --decision-file ...` (template —
   `references/consilium-prompts.md`, core checks required sections fail-closed);
   when there is a material disagreement add `--escalate`.
8. **`close <id>` — mandatory always** (see cleanup discipline below).

`status <id>` at any time shows the phase, round, live models, unresponsive-
participants, actual number of adapter calls and wall-clock. Additionally (RVSW-01):
liveness classification of participants (`active`/`quiet`/`dead_watcher` — diagnostics,
not kill) and the last progress checkpoint (`progress.jsonl` of the session: phase/round boundaries).

## Session Monitoring: watch (CONS-05)

`watch <id>` — read-only layer for observing a running consilium (option A:
execution remains headless, only observability added). Execution and protocol do
not change: watch does not write to the session state, does not call adapters
and the network, is not a gate and not an entry point for human turns.

```bash
python3 "$SKILL_DIR/scripts/consilium.py" watch <session_id> \
    [--participant <id>] [--interval 3] [--excerpt-lines 6] [--once]
```

- **By default, a single live view**: phase/round/wave, call counter,
  last checkpoint and for each participant a block: state, liveness, counters
  for calls/retries, last turn (excerpt of the last N lines). During a wave
  session files are silent (written after blocking `run_wave`), so live view
  additionally shows **live invocation progress from the runtime.json of the
  sandbox**: state/phase/elapsed, event counters and adapter tool calls.
  Liveness class (`active`/`quiet`/`dead_watcher`) is printed only while the
  invocation is running; in the pause between waves - explicit "invocation is not
  running" + age of the last activity. Refresh by poll interval (`--interval`, default
  3 s); actual participant IDs are shown intentionally - the observer is you
  (moderator), not the participants.
- **`--participant <id>`** — a stream for one participant for a separate panel:
  prints its new turns and system events about it as they appear
  (tail semantics over transcript.jsonl).
- **Completion**: watch exits with a clear message when the session reaches
  a terminal state (verdict/close/directory deletion); Ctrl+C — clean
  exit, the session state is never affected.
- **`--once`** — one frame without a loop (diagnostics, tests).

**Interactive monitoring in herdr — ONLY at the user's explicit request** (not by
by default and not as a protocol step): `scripts/consilium-watch-herdr.sh <session_id>` —
creates the herdr panel layout and launches its own `watch` in each one. Owner's decision
2026-08-04: the panel layout is not created automatically; the basic way to observe is
`watch` in the current panel. Details — `references/watch-mode.md`.

## Human-critic mode (CONS-06, optional)

Disabled by default. `convene --human-critic [--human-wait-cap-sec 1800]`
enables human participation ONLY in attack waves (attack phases B, redteam
phases D): the human does not generate ideas, does not affect quorum/family diversity,
track-record and kill metrics; for participants, they are a regular anon_id from the shared
`create_anon_map` space, authorship is not disclosed.

- In an attack wave, the core waits for the human turn BEFORE starting the LLM wave (anti-anchoring):
  the moderator shows the bundle to the human, collects criticism into a turn file, and passes
  `round <id> --human-turn-file <path>` (JSON `{content, structured,
  human_approved: true}`; without attestation there is a fail-closed refusal; at most 1 turn
  per wave). Internal participant ids in the file are forbidden (lint).
- Waiting pauses the wall-clock budget (`wall_clock.paused_sec`,
  exposed in `status`/`watch` as awaiting_human). When the waiting cap is reached,
  `awaiting_moderator_decision`: the round does not run until an explicit decision:
  `--human-continue-wait` | `--human-skip-wave` (one-time) |
  `--human-withdraw --reason "..."` (mode collapse).
- If the human made ≥1 turn in phase B, phase D is mandatory (the skip predicate is
  canceled); human-critic participation is disclosed on a separate line in the verdict.

The full protocol for the moderator working with a human (showing the bundle, collecting criticism,
prohibitions on disclosing authorship and marking contributions, residual risks) —
`references/human-critic-playbook.md`.

## Domain Packages (CONS-03)

The consilium is invoked in the context of a domain package (`review-harness/domains.yaml`): roles with lenses,
machine-readable risk checklist and domain evidence requirements.

- Available packages: `architecture` (default — current behavior), `requirements`,
  `security-compliance`, `data-schema-evolution`, `incident-postmortem`, `design-direction`.

**Which package to choose** (if none fits — use `architecture`):

| Package | When to choose |
|-------|----------------|
| `requirements` | Business requirements, RFC 2119, scope, acceptance criteria and test plan without choosing a technical design |
| `security-compliance` | Threats, vulnerabilities, PDn/152-FZ, secrets/keys, access perimeter |
| `data-schema-evolution` | Changing contracts/schemas/data models with existing consumers |
| `incident-postmortem` | Analysis of an already occurred failure/incident (causes, not remediation) |
| `design-direction` | Direction for a page/miniapp/UX before implementation (not pixel-review) |
| `architecture` | Service boundaries, solution structure, everything else (default) |
- **Participant move format MUST include `risk_checklist_responses`** (for all
  domains, including default): for each checklist item applicable to the move type -
  `{"item_id", "verdict": "hit|clear|na", "note"}`; `hit`/`na` require a note.
  An invalid move is rejected fail-closed (exactly one content-retry via ask, then -
  unresponsive). `hit` with a note is counted by the core as a new finding in stop-condition
  predicates; participants do not see answers from other checklists.
- A foreign domain in the role/strengths history does not affect assignment (domain filter);
  checklist statistics (`hit/clear/na`) and the domain are written to the track record.

## Moderator Responsibilities and Restrictions (FR-05)

The moderator is always the calling primary agent: the consilium always = all enabled participants
in the registry (including your model as an ordinary member) + you as moderator. It:

- **Does NOT vote and does NOT nominate its own model.** If you want to express an opinion -
  register yourself as an ordinary participant through your adapter (entry in `review-harness/adapters.yaml`),
  and participate under the common rules.
- **Authoritative digests** for each wave after the first: a condensed map of live models and
  debate by anon_id, without real participant ids (anonymization in output).
- **Kill - only by core recommendation**: the candidate is determined by a deterministic
  criterion from structured moves; the core rejects `--kill` that does not match the candidate
  (protection against ownership bias). The moderator has no right to "kill at their own discretion."
- **Phase C synthesis - with mandatory traceability**: each element of the merge references
  `seq:<n>` of the source model.
- **Minority report - only verbatim**: the texts of dissenters and the final statements
  of excluded models are included without editing.
- **Escalation to a human** - only in case of material disagreement (changing the selected
  verdict option).

## How to read the verdict

`verdict.md` (required sections are checked by the core): decision (synthesis with seq-
traceability) · kill-log · minority report (verbatim) · composition flag · incidents ·
cost · phase B completion stop condition · escalation.

- **Minority report — a mandatory part of the decision**, not an appendix “for reference”:
  analyze it on par with the decision. Unanimity is the anti-goal of the protocol; the absence
  of dissenters is a reason for distrust, not for celebration.
- **Composition flag**: whether quorum was met/degraded and whether family diversity was present;
  when the family is homogeneous, the verdict contains an explicit warning — treat it as a
  reduced reliability of second-opinion.
- **Stop condition**: `converged` — the debate has converged; `round_limit`/`stalemate`/
  `invocation_ceiling` — the synthesis was made from the current state, and its confidence is lower.
- **Escalation**: `ESCALATE_TO_HUMAN` is set only in case of material disagreement —
  such a verdict cannot be accepted without a human.
- The verdict is input for the primary agent. The final decision (accept, modify, reject)
  is made by you or a human; the council is not a condition for any gate.

## 🔴 CRITICAL: cleanup discipline (FR-10)

> **Level: CRITICAL / MUST.** By precedent of the gate rule `cross-provider-review.md`, reinforced with a double
> contour. There is no automatic cleanup — only pairing and checkpoint. Orphaned
> sandbox stay on disk forever.

| Requirement | Description |
|-----------|----------|
| Pairing convene↔close | Any `convene` MUST have a paired `close` on ANY termination path: verdict, stalemate, escalation, adapter error, refusal of the council |
| Pairing start↔close of participants | Each participant is an adapter session with its own sandbox in `.review-sandboxes/`; the council `close` closes ALL participants |
| Cleanup fail-closed | If a participant close fails, the session is NOT deleted and is NOT marked closed: `cleanup.status=failed`, exit code 5; a repeated `close` is idempotent — **retry is mandatory** until cleanup succeeds |
| `--keep` for forensic only | `close --keep` preserves the session directory — ONLY for forensic/debug with an explicit written reason |
| Lock-only tombstone | After a successful adapter `close`, a canonical lock-only tombstone is allowed, recognized by `adapter_contract.is_lock_only_tombstone()`; any payload, unexpected file/directory, or symlink blocks the checkpoint |
| Cleanup report | The final task report records the cleanup status of each session and each participant: `closed` or justified `kept` |

**✅ DOUBLE CHECKPOINT before closing the task (required):**

```bash
ls -1 .consilium-sessions/ 2>/dev/null | wc -l   # expected 0
PYTHONPATH="$SKILL_DIR/../review-harness/scripts" python3 - <<'PY'
from pathlib import Path
from adapter_contract import REVIEW_ROOT, is_lock_only_tombstone
root = Path(REVIEW_ROOT)
active = [str(path) for path in (root.iterdir() if root.is_dir() else ())
          if not is_lock_only_tombstone(path)]
print(len(active))  # expected 0
PY
```

If it is not 0, the task is NOT complete in terms of cleanup: close the remaining sessions and only
then close the task. `.consilium-track-record/` is durable storage, EXCLUDED from the checkpoint:
do not delete.

## Failure Diagnostics

- **Unresponsive participant**: call timeout (per-invocation, default 900 s,
  CONS-02 OPT-1) → the participant
  is marked `unresponsive`, its model is frozen (it remains an object of criticism and
  borrowing, and makes no turns); adapter error → exactly one retry → `unresponsive`.
  The protocol continues without it; the fact is recorded in `transcript`, `status`, and the verdict.
  A participant who does not survive until the first proposal is removed from the live models and
  kill candidates. If the removal breaks quorum, the session ends with the cause recorded
  and a paired `close` (exit 3).
- **Core failures (fail-closed)**: exit 2 — protocol/quorum violation; exit 4 —
  `--kill` against a nondeterministic candidate; exit 5 — cleanup failed (retry `close`).
  Do not try to "work around" a core failure with manual session-state edits.
- **Where to look**: `status <id>` — phase, live models, unresponsive, call count,
  wall-clock; transcript and adapter call logs are in `.consilium-sessions/<id>/`;
  participant adapter logs are the `status`/`log` of the corresponding adapter from
  `review-harness/scripts/adapters/` by the participant `review_id`.
- **Timeouts (CONS-02 OPT-1)**: per-invocation default 900 s (`--timeout-sec`),
  wave = T + 240 s, session wall-clock = `max(7200, 13 × (T + 240) + 1800)` s
  (default 16620 s, warning at 75 %) — recalculated from `--timeout-sec`.
  When approaching the cap, bring the verdict to completion from the current state
  and close the session; do not start new rounds.

## What the caller does NOT need to know

The details below are for the mechanism maintainers, not for calling the consilium
(all in `references/`):

- `references/adapter-contract.md` — pointer to the canonical contract
  `review-harness/references/adapter-contract.md` (adapter lifecycle and IO contract,
  mandatory `sync`, activity fields, diff materialization, error/timeout mapping, read-only boundary)
  + consilium-specific additions; how to write an adapter for a new [CLI+model]
  (1 entry in `review-harness/adapters.yaml` + thin adapter in `review-harness/scripts/adapters/`,
  core is not modified).
- `references/transcript-schema.md` — schema of `transcript.jsonl` and the structured turn.
- `references/track-record-schema.md` — pointer to the canonical shared schema
  `review-harness/references/track-record-schema.md` (strength formulas,
  decay/weights, exploration budget) + consilium-specific set of observation fields;
  role assignment by statistics.
- `references/consilium-prompts.md` — wave prompts (built by the core), moderator prompts
  (digest/synthesis) and the `verdict.md` template.

---
depends_on:
  - framework/skills/tool-usage/review/review-harness/SKILL.md
---
