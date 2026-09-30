# review-harness adapter contract (RVSW-01, FR-01; inherits FR-07/TD council)

> The canonical location of the contract is this file. It was moved from
> `agent-consilium/references/adapter-contract.md` (T-14) with additions
> RVSW-01: mandatory `sync`, canonical activity fields
> `last_activity_at`/`last_heartbeat_at`, diff materialization in the sandbox.
> The council copy is a pointer to this document.

The adapter is a thin wrapper around the model CLI, implementing a unified lifecycle.
Tools (council, swarm) communicate with participants ONLY through this contract
(wrappers - `scripts/adapter_contract.py`); a new [CLI+model] is connected
with a single entry in `adapters.yaml` + a new adapter in `scripts/adapters/`,
without changes to harness and tools (NFR-07).

## 1. Lifecycle (required commands)

| Command | Semantics |
| --- | --- |
| `start --question Q [--model M] [--review-id ID] [--timeout-sec N] [paths ...]` | Creates a sandbox `.review-sandboxes/<review_id>/workspace`, copies focused-paths (without paths - full context; tool cores always pass focused-paths, NFR-05), performs the first model call |
| `ask <review_id> --question Q [--timeout-sec N]` | Continues the saved participant session (resume): the participant preserves the context of its position between turns |
| `status <review_id>` | JSON `{review_id, status, runtime, stats}`: heartbeat, pid, phase, progress counters |
| `close <review_id> [--keep-sandbox]` | Closes the session and deletes the sandbox by default (pairing start↔close) |
| `sync <review_id>` | **MANDATORY element of the contract (FR-01б)**: updates the copied sources/diff in the participant sandbox to the current project state. Required for re-review of fixes (FR-09) and delta gate iterations (rework → sync → repeat call). Implemented in all three adapters |

The extended lifecycle of existing adapters (`debate`/`show`/`log`/`stats`)
remains available and does not break.

## 2. IO Contract

### Input (material submission)

- The participant is given focused-path copies of files (positional `paths` in
  `start`) — NOT `--full-context`;
- the turn text is passed via `--question` (tool question + role + format
  instruction);
- the brief additional-arguments schema follows the pattern of existing adapters
  (`--task`/`--goal`/`--requirements`/`--constraints`/`--primary-target`/
  `--changed-files`/`--open-concerns`/`--review-ask`);
- **diff materialization (FR-01к, FR-04)**: the harness materializes the diff
  as a review file inside the participant's sandbox (`adapter_contract.materialize_diff()`,
  canonical name `review.diff`; for re-review — `fix.diff`). The participant reads
  the diff as a normal file in its workspace, not from the prompt. The sandbox remains
  read-only for the participant: writing is performed by the caller side — this is a channel
  for delivering the contract artifact, not the participant's right to write. Fail-closed:
  without `workspace_path` in the participant meta — `RuntimeError`.

### Output `start` (stdout)

Service lines at the start of output (parsed by harness):

```
review_id: <id>
session_id: <session id CLI модели>
workspace: <абсолютный путь sandbox>
<статистическая строка>
<пустая строка>
<текст ответа участника>
```

### Session and resume

- the adapter session `session_id` is stored in `.review-sandboxes/<review_id>/review.json`
  (`session_id`, `last_response`, `workspace_path`);
- `ask` MUST continue exactly the saved session (claude: `--resume`;
  codex: `codex exec resume <id>`; kimi: `-r <id>`, fallback `--session`).

### Error and timeout mapping

| Call result | Classification | Action |
| --- | --- | --- |
| exit 0 | ok | the turn is accepted |
| exit 1 + `runtime.json: phase=timeout` | timeout | participant `unresponsive` WITHOUT retry |
| any other exit 1 | error | exactly one retry → on repeat `unresponsive` |

The adapter MUST, when `--timeout-sec` is exceeded, kill the CLI process and record
`runtime.json: {"state": "failed", "phase": "timeout"}` — this is a machine-readable
timeout signal.

## 3. Canonical activity fields (FR-13)

All adapters MUST maintain two activity fields in `.review-sandboxes/<review_id>/runtime.json`
(canonicalized by the contract; no new timestamp is introduced):

| Field | Semantics |
| --- | --- |
| `last_heartbeat_at` | "The process-adapter is alive and watching the CLI" — watcher tick (cadence ~1 s) |
| `last_activity_at` | "The CLI emits events" — the last observed model event |

Consumer — `scripts/liveness.py` (classification `active`/`quiet`/
`dead_watcher`, diagnostics, not kill). Access to the fields is only through
`adapter_contract.read_participant_activity()` (canonical reader,
`ACTIVITY_FIELDS`).

## 4. Read-only boundary (NFR-05)

- the participant works only with sandbox copies of materials; changing the real project
  is prohibited;
- the read-only instruction is mandatory in the adapter prompt/system-prompt;
- the full-context copy (when applied) excludes `.git`, `.venv`,
  `.review-sandboxes`, `node_modules`, `__pycache__` and common build outputs;
- codex: read-only sandbox mode; claude: tools `Read,Grep,Glob,LS`;
- kimi: there is no allowlist flag in the CLI; the mechanical boundary is the built-in read-only
  agent profile via `--agent-file` (the profile is built into the adapter and
  materializes in the participant session directory; an explicit `--agent-file`
  overrides it). Additional contours: sandbox copies + read-only
  instruction in the prompt.

## 5. Structured turn

The fenced-block mechanism is shared (`scripts/structured.py`): the participant's response
ends with a fenced block tagged with the tool; a missing/broken block yields
empty structured + warning (conservative semantics). **Field schemas belong to
the tools, not the harness**: the consilium turn format is
`agent-consilium/references/transcript-schema.md`
(```consilium-structured); the swarm turn schemas are
`review-swarm/references/finding-schema.md` and `verdict-schemas.md`
(```swarm-structured, ```swarm-verdict and others).

## 6. Contract Tests

`tests/contract/test_adapter_contract.py` — library tests (moved from the
council, NFR-04): argument schema, `session_id`/resume, error and timeout
mapping, mandatory `sync`, canonicalized activity fields, diff materialization
in sandbox — for all three CLI (stub-CLI: `tests/stubs/stub_cli.py`).
