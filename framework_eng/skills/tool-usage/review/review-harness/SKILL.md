---
name: review-harness
description: "Shared harness library for multi-model review tools (consilium, review-swarm): CLI adapters contract start/ask/fork/status/close/sync, native fork lineage, sandbox-lifecycle, structured-parsing, unified adapters registry v3, rotation/domains, track record, heartbeat/progress, quota-aware selection. Library, not a user-facing skill."
capabilities: agent-governance,multi-model-review,cross-provider,harness
---

# Review Harness — shared transport and record-keeping library (RVSW-01, FR-01)

This is a **library, not a tool and not the caller's playbook**. Harness is a shared
layer of reusable transport and record-keeping for multi-model review tools; it has no
own CLI for the calling agent and no protocol. There are exactly two consumers:

- `agent-consilium` (`scripts/consilium.py` / `consilium_core.py`) —
  phases A–E, digest-gates, moderator verdict;
- `review-swarm` (`scripts/swarm.py` / `swarm_core.py`) — divergent
  code review by a swarm, rounds 1–4, light tier, and gate semantics.

Everything that knows about consilium phases or swarm rounds lives in the tool cores.
The tool protocols are documented in their SKILL.md, not here.

## Boundary Rule (FR-01, AC-01)

> In the library — everything that **does not know about phases/rounds**; in the tools — only
> the protocol. Any logic specific to consilium phases A–E or swarm rounds 1–4
> is **forbidden** in harness.

The rule is enforced by executable tests `tests/unit/test_boundary.py`:

- **HU-B01** — import scan of `scripts/**`: harness does not import tool
  modules (`consilium`, `consilium_core`, `swarm`, `swarm_core`);
- **HU-B02** — search for forbidden phase/round identifiers
  (`phase_a..e`, `tour1..4`, `convene`, `stalemate`, `kill_candidate`, etc.)
  in harness sources; exceptions are allowed only through an explicit whitelist with a
  pointer comment, unused whitelist entries are an error.

Practical consequences of the boundary:

- `structured.py` knows only fenced-block mechanics; **field schemas for turns**
  (findings/verdicts/elements/…) are defined by tools and passed as parameters
  (`fence_tag`, `empty_schema`);
- `progress.py` does not know tool checkpoint names — the allowed set
  (`allowed_checkpoints`) is supplied by the caller, harness validates
  membership fail-closed;
- `domains.py` is not extended with round labels: `CHECKLIST_WAVE_TYPES` —
  the shared `applies_to` dictionary; the tool passes its enum through
  `validate_domains(..., wave_types=...)` (the swarm adds the `tour1` label);
- `track_record.py` does not know what to record: building the observation from session state
  is the tool protocol and stays in the cores; harness provides decay/projection formulas
  and a storage layer parameterized by `storage_dir`;
- gate adapters (`scripts/adapters/claude_opus_review.py`, `codex_review.py`)
  read the gate prompt from `review-swarm/references/review-prompt.md` — the reference
  for the gate executor and the carrier of gate semantics (RVSW-01 TD §3.2); this is an
  intentional harness → tool coupling point, recorded explicitly here. If the file is
  missing, the adapter silently falls back to the built-in
  `FALLBACK_REVIEW_PROMPT`/`FALLBACK_REVIEW_SYSTEM_PROMPT`: renaming or
  moving the reference is equivalent to an unnoticed replacement of the gate prompt with a stub.
  The dependency is checked executable — contract test HC-13
  (`tests/contract/test_adapter_contract.py`): the candidate must exist,
  and the loaded prompt must not match the fallback.

## Library Structure

```
review-harness/
├── adapters.yaml            # единый реестр адаптеров, схема v3 (FR-02)
├── domains.yaml             # доменные пакеты ролей/линз/риск-чеклистов
├── scripts/
│   ├── adapter_contract.py  # единственное место, знающее о subprocess
│   ├── registry.py          # парсер/валидатор реестра v3 (stdlib-only)
│   ├── structured.py        # fenced-block parsing, анонимизация, оценка токенов
│   ├── domains.py           # парсер/валидатор domains.yaml, чеклисты
│   ├── track_record.py      # decay/strengths + хранение (storage_dir)
│   ├── liveness.py          # классификация «думает/повисла» (FR-13)
│   ├── progress.py          # progress.jsonl — семантические чекпоинты (FR-13)
│   ├── quota.py             # квотный выбор адаптера (FR-14)
│   └── adapters/
│       ├── claude_opus_review.py
│       ├── codex_review.py
│       └── kimi_review.py
├── references/
│   ├── adapter-contract.md      # канонический контракт адаптера
│   └── track-record-schema.md   # каноническая схема track record
└── tests/ (unit/ + contract/)
```

### `scripts/adapter_contract.py` — adapter and wave contract

The only place that knows about subprocess: launching adapters, parsing stdout,
mapping timeout/errors, retry policy, parallel waves.

- Contract wrappers: `start_participant()`, `ask_participant()`,
  `fork_participant()`, `read_participant_status()`, `close_participant()`,
  **`sync_participant()`**
  (mandatory contract element, FR-01b: delivery of updated source/diff
  into the participant sandbox is necessary for re-review of fixes, FR-09).
- `materialize_diff()` — diff materialization as a file (`review.diff`) inside
  the participant sandbox (FR-01k, FR-04): the participant reads the diff as an ordinary file,
  not from the prompt. Fail-closed without `workspace_path` in meta and when
  `workspace_path` is outside `.review-sandboxes/<review_id>/` (R-Final F-05).
- `read_participant_activity()` — canonical access to the activity fields
  `last_activity_at` / `last_heartbeat_at` (FR-13; no new timestamp is
  introduced, both fields are required for all adapters).
- `probe_fork_capability()` checks runtime capability separately from registry declaration.
  `fork_participant()` accepts mandatory immutable `operation_id`, `snapshot_digest`
  and canonical parent `provider_checkpoint_id`; logical snapshot digest never
  substitutes for provider cursor. Success requires a new child session,
  full provider lineage, `provider_turn_id`, and explicit `prompt_consumed`.
  Synthetic `start`/prompt replay as fallback is forbidden.
- Failed fork and successful subprocess with corrupted stdout undergo
  reconciliation of the expected `child_review_id`; orphan child is closed only
  when parent/operation lineage matches. Arbitrary stdout identity is not used as
  a cleanup target.
  The canonical close postcondition is not absence of a directory, but a lock-only
  tombstone: the directory contains exactly a stable regular `invocation.lock`.
  A repeated deterministic fork can reuse only such a tombstone through
  canonical prepare; a directory with meta/runtime/workspace is considered live and is
  not cleaned/reused automatically.
  The common classification is performed by `is_lock_only_tombstone()`; a repeated
  `close_participant()` on a canonical tombstone is idempotently successful without
  launching the adapter and without replacing the lock inode.
- Exact-route `ask_participant()` accepts paired `operation_id` and
  `parent_provider_turn_id`. Before the repeated model call the adapter must run
  `reconcile`: `completed` returns an already existing provider turn, `absent`
  allows exactly one repeat of the same operation, `ambiguous`/uninspectable
  blocks continuation. Timeout never leads to a blind re-ask.
  The call without this pair is preserved only for legacy non-shard consumers and does not
  satisfy the exactly-once/native-shard acceptance contract.
- `reconcile_participant()` trusts `completed`/`absent` only when
  `evidence_complete=true`, checks operation identity and provider turn
  lineage. The operation marker must be provider-visible, and the pending record
  is preserved until the provider is called.
- `run_shards()` executes different shard-callable in parallel with a bounded worker
  budget and preserves result order; one callable owns the task sequence inside a shard.
- Outcome mapping (inherited): `exit 0` → ok; `exit 1 + runtime.json
  phase=timeout` → timeout → unresponsive **without retry**; any other exit 1 → error →
  **exactly one retry** → unresponsive.
- Constants: `DEFAULT_TIMEOUT_SEC = 900` (per-invocation), `WAVE_GRACE_SEC =
  120` (subprocess timeout = T + 120, wave = T + 240), `PARALLEL_CAP = 8`
  (wave parallelism ceiling, TD §6.4), `REVIEW_ROOT = .review-sandboxes`,
  `DIFF_FILENAME = "review.diff"`, `ACTIVITY_FIELDS`.
- `start_participant(..., review_id=...)` accepts an optional preassigned
  identifier, validates it before launching subprocess and passes it to the adapter
  via `--review-id`; without the parameter the generated-ID mode is preserved.
- `run_wave(tasks, ..., task_keys=...)` — bounded `PARALLEL_CAP`
  parallel wave of calls (ThreadPoolExecutor). When keys are provided, one
  key forms a sequential FIFO lane, independent keys run in parallel, and the deadline
  is returned only after quiescence of already launched callable. `keyed_wave_slots_bound()`
  gives an upper bound on scheduler slots.

### `scripts/registry.py` — single registry, schema v3 (FR-02, AC-02)

One declarative `adapters.yaml` registry for both tools; **two registries are
forbidden** (guaranteed desynchronization). Parser stdlib-only, fail-closed.

Record fields (all required, no optional ones):

| Field | Semantics |
| --- | --- |
| `id` | Unique participant id (reference key, observations, rotation) |
| `family` | Model family (`claude`/`codex`/`kimi`): diversity quorum and observed metadata |
| `model` | Model alias → `--model` adapter |
| `adapter` | Path to the adapter (checked for existence when `base_dir` is set) |
| `cli` | CLI binary for healthcheck (`doctor`) |
| `context_budget` | Positive int — participant context budget |
| `enabled` | bool — included in the tool set |
| `gate_legal` | bool — capability flag for a specific participant for gate roles (FR-14/FR-16); does not encode pairs or family-based selection |
| `quota_introspection` | Quota reading method: `none` \| `codex-rollout` |
| `quota_status` | Availability proof status (TBD-01): `proven` \| `unavailable` |
| `native_fork` | Strictly typed capability `{supported, route, min_cli_version, exact_checkpoint, automation_safe}` |

Invariants (RISK-10, fail-closed in `validate_registry`):

- only `version: 3` is accepted (there is no silent migration/fallback);
- unknown record fields are rejected with diagnostics;
- field **`strengths` is forbidden** (inherited from CONS-01: strengths — read-only
  projection from track record, self-declaration is forbidden);
- the only allowed nested structure is `native_fork` with an exact field set;
  other nested structures and unknown capability fields are forbidden;
- duplicate `id`, empty `family`, nonexistent `adapter` — errors;
- quota invariant: `quota_introspection != "none"` ⟺ `quota_status ==
  "proven"` (otherwise — schema error).

Extending the registry with a new [CLI+model]: one `adapters.yaml` entry + a thin
adapter in `scripts/adapters/` by contract — harness and tools are not
modified (NFR-07).

### `scripts/structured.py` — fenced blocks and anonymization

- `parse_structured_block(text, fence_tag, empty_schema)` — extraction of a fenced
  JSON block from a participant's response; missing/broken block → empty structured
  + warning (conservative semantics; whether the move is rejected is decided by the tool
  predicate). The default tag `consilium-structured` is for compatibility with the
  consilium format; the swarm passes its own tags (`swarm-structured`,
  `swarm-verdict`, …).
- `create_anon_map()` / `anonymize_text()` — random permutation id →
  `M1..Mn` (stable with a fixed seed) and replacement of real ids in texts
  (longer ids first for prefix protection).
- `estimate_tokens()` — rough estimate (~4 characters/token) for `context_budget`.

### `scripts/domains.py` — domain packages and risk checklists

- Nested stdlib parser for `domains.yaml` (schema E-1): `parse_domains_yaml`,
  `validate_domains`, `load_domains` (fail-closed when the registry is missing/broken),
  `domain_by_id`.
- Package schema: `id`, `description`, `roles` (≥2; a role has `id`, `title`,
  `lens`, `risk_checklist` with items `{item_id, text, applies_to}`),
  `evidence_requirements`.
- `CHECKLIST_WAVE_TYPES = {proposal, attack, response, redteam,
  confirmation}` — shared `applies_to` dictionary; tools extend the
  enum via the `wave_types` parameter (swarm: `+ tour1`), the harness constant does not
  change.
- Checklists: `applicable_checklist_items`, `validate_checklist_responses`
  (fail-closed table of degenerate cases: coverage by `applies_to`, enum
  `hit/clear/na`, required note for `hit`/`na`), `checklist_stats`.
- `domains.yaml` contains consilium packages (`architecture` — default,
  `security-compliance`, `data-schema-evolution`, `incident-postmortem`,
  `design-direction`) and the swarm `code-review` package (six lenses: security,
  correctness, concurrency, performance, data-contracts, tests).

### `scripts/track_record.py` — decay, strengths, storage

- Formulas (inherited from CONS-01, with no logic changes): `observation_weight` —
  recency decay `w = 0.5^(k / half_life)`, `STRENGTHS_HALF_LIFE = 8` sessions;
  `STRENGTHS_FLOOR = 3` (projection is applied when `n_eff ≥ 3`);
  `TAINTED_DISCOUNT = 0.5`; score weights `SCORE_W_ACCEPT = 0.6`,
  `SCORE_W_UPHELD = 0.4`; `EXPLORATION_EVERY = 4`.
- `compute_strengths(observations, seq_field=...)` — read-only projection by
  cells `(participant_id, role)` with segregation of tainted observations.
  `seq_field` is parameterized: tools have different session counters
  (`consilium_seq` for consilium, `review_seq` for the swarm).
- The storage layer is **parameterized by `storage_dir`** (FR-01e — tools have
  different directories): `read_track_config` / `write_track_config`,
  `bump_counter`, `read_observations` / `append_observations` (append-only),
  `regenerate_strengths`. Consilium writes to `.consilium-track-record/`, the swarm —
  to `.swarm-track-record/`; schema — `references/track-record-schema.md`.
- Source of truth is `observations.jsonl`; `strengths.json` is a generated
  cache/report (not trusted on read).

### `scripts/liveness.py` — "thinking" / "hung" (FR-13, AC-13)

Pure classification `classify_liveness()` by canonical activity fields
(values ISO-8601 or epoch):

- heartbeat older than `HEARTBEAT_DEAD_THRESHOLD_SEC` (10 s; heartbeat cadence
  of adapters is 1 s) → `dead_watcher` (hard signal: the process adapter is not alive);
- activity newer than `silence_threshold_sec` (default 120 s — TBD-04,
  overridden by the session parameter) → `active` ("the model is thinking");
- activity older than the threshold → `quiet` (diagnostic marker of silence);
- missing fields — fail-safe: heartbeat → `dead_watcher`, activity →
  `quiet`.

**Heartbeat — a diagnostic signal, not an automatic kill (RISK-05)**:
the module only classifies; intervention is the decision of the calling
Orchestrator. `classify_participant()` — thin IO on top of
`adapter_contract.read_participant_activity`.

### `scripts/progress.py` — semantic progress (FR-13, AC-13)

`progress.jsonl` in the session directory, append-only; written by the tool core,
the calling model reads it optionally. Checkpoints are at stage boundaries (rounds/
phases), not stream of consciousness.

- Record schema: `{ts, session_id, tool ("swarm"|"consilium"), checkpoint,
  summary (non-empty), counters (flat dict of non-negative int)}`.
- `append_checkpoint(session_dir, ..., allowed_checkpoints=...)` — fail-closed
  validation: unknown tool/checkpoint, empty summary, invalid counters →
  `ValueError`, the file is left untouched. The tool provides the set of allowed
  checkpoints (boundary rule).
- `read_checkpoints()`, `last_checkpoint()` — read-only; the last checkpoint
  is duplicated in the tool's `status`.

### `scripts/quota.py` — quota-based adapter selection (FR-14, AC-14)

The quota source is the usage-introspection CLI (local bookkeeping is forbidden).
Proven introspection (TBD-01, probe 2026-07-29, TD §10): only codex —
rollout files `~/.codex/sessions/**/rollout-*.jsonl`, the last non-empty
`payload.rate_limits` record (`read_codex_quota`); claude/kimi —
`unavailable`.

`select_adapter()` algorithm (pure predicate + thin IO):

1. **Floor filter BEFORE weighting**: `enabled`, `id !=` the caller's id,
   for gate roles — `gate_legal`. Empty pool → fail-closed
   `QuotaSelectionError`; literal self-review is forbidden (NFR-01), but another
   participant of the same family remains eligible.
2. **Weighted** (deterministic argmax `remaining_percent`, no repeat of the
   last choice) — only if introspection is proven for **all** candidates and the
   data is fresh (staleness ≤ 24 h by mtime of the rollout file RECORD —
   `recorded_at`, R-Final F-07; not by `resets_at` — the moment the window resets).
3. Otherwise — **blind** (uniform rotation by registry order, no repeat)
   with recording `quota_fallback_reason` in observations: `introspection
   unavailable: <cli>` / `stale: <id>` (expired record) / `no data: <id>`
   (no record) — not silent estimation and not blocking.

Return: `{adapter_id, quota_mode, quota_fallback_reason, candidates,
remaining_percent}`.

### `scripts/adapters/` — three CLI adapters

`claude_opus_review.py`, `codex_review.py`, `kimi_review.py` — thin wrappers
around the models' CLI, implementing the `start/ask/fork/capabilities/status/close` + **mandatory
`sync`** contract (the extended lifecycle `debate`/`log`/`stats`/`show` is preserved and does
not break). All three maintain the canonical activity fields
`last_activity_at`/`last_heartbeat_at` in `runtime.json`. Read-only boundary:
sandbox copies, read-only instructions in prompts; codex — read-only sandbox
mode; claude — tools `Read,Grep,Glob,LS`; kimi — built-in read-only
agent profile via `--agent-file` (materialized by the adapter). The canonical
contract is `references/adapter-contract.md`; contract tests are
`tests/contract/test_adapter_contract.py` (all three CLI: argument schema,
`session_id`/resume, error/timeout mapping, mandatory `sync`, activity fields,
diff materialization).

Native fork is available only after a runtime probe and an exact match with
`native_fork` from registry v3. The fork command must accept and preserve one
`operation_id`, canonical `snapshot_digest` strictly of the form
`sha256:<64 lowercase hex>`, and the parent `provider_checkpoint_id`; stdout must
prove these values, the child session/turn lineage, and `prompt_consumed`.
`prompt_consumed=false` is a valid zero-turn fork, not evidence of execution
of the first task.

The impossibility boundary is explicit: if the provider does not expose a full
non-mutating history/inventory API and the local provider stream/transcript is also
lost, harness cannot prove either completion or absence. Such a state remains `ambiguous`
and requires an external decision; repeated model call, synthetic replay or the assumption
"most likely did not execute" are forbidden. For Claude, proof is limited to the
local stream/transcript; for Codex, app-server thread history/inventory is used; for
Kimi — authenticated REST history and session inventory. Unavailability of the
corresponding channel means fail-closed.

## How to connect the library (for consumers)

There is no package installation (no overengineering, FR-01): the tool adds
the `scripts/` harness to `sys.path` from its own file and imports modules
directly. The canonical pattern (the same in `consilium.py` and `swarm.py`):

```python
HARNESS_DIR = Path(__file__).resolve().parents[2] / "review-harness"
_HARNESS_SCRIPTS = HARNESS_DIR / "scripts"
if str(_HARNESS_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(_HARNESS_SCRIPTS))

import adapter_contract as ac
import liveness
import progress as progress_tracker
import quota
import registry as registry_mod
import structured
import track_record
# + domains — где нужны доменные пакеты
```

Registry and domains by default: `HARNESS_DIR / "adapters.yaml"`,
`HARNESS_DIR / "domains.yaml"`. A second registry/copy of modules in the tool is
forbidden — only a thin tool section of "who to select for this
run" (FR-02).

## Tests

```bash
python3 -m pytest framework/skills/tool-usage/review/review-harness/tests -q --tb=short
```

- `tests/unit/` — clean module predicates + boundary test HU-B01/B02;
- `tests/contract/` — adapter contract tests (moved from consilium,
  became library tests, NFR-04); stub-CLI — `tests/stubs/stub_cli.py`.
