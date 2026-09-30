# Consilium Watch Mode (CONS-05, Variant A)

Real-time observation of a running consilium. Owner decision is
option A: execution remains fully headless (the convene/round/… commands as
before), and watch is a separate read-only layer over the session files.

## Layer Boundaries

- **Read-only**: watch only reads files; there are no writes to session state
  (session.json/transcript/progress are never changed, including on Ctrl+C).
- **No network and no adapters**: no network calls to models and no adapter
  subprocesses; liveness is read directly from the sandbox's `runtime.json`.
- **Not a gate and not an entry point for turns**: watch does not participate in
  the protocol, does not affect the state machine, stop conditions, or quorum;
  human turns are not fed through it.

## What watch reads

| Source | What it provides |
| --- | --- |
| `.consilium-sessions/<id>/session.json` | phase/round/wave, composition, state/invocations/retries, verdict_done, cleanup |
| `.consilium-sessions/<id>/transcript.jsonl` | participant turns (excerpt in live-view, stream for --participant) |
| `.consilium-sessions/<id>/progress.jsonl` | phase/round boundary checkpoints |
| `.consilium-sessions/<id>/anon_map.json` | anon_id values for phase B participants |
| `.review-sandboxes/<review_id>/runtime.json` | live invocation progress: state/phase/elapsed_sec, `raw_events`/`tool_calls_total`/`last_event_type` counters, last_activity_at/last_heartbeat_at → liveness |

During a wave, session files are written only after the blocking `run_wave`, so
the line "adapter: state=… events=… tools=…" from runtime.json is the only live
progress signal. The liveness class is printed only when `runtime.state=running`
(the heartbeat ticks only during invocation); in the pause between waves,
"invocation is not running" + the age of the last activity is shown - a plain
`dead_watcher` is not output during a healthy pause.

The terminal state is determined by the first of these signs: the session
folder is removed (close without --keep) → `cleanup.session_closed` →
`verdict_done` → the last checkpoint ∈ {`verdict_ready`, `closed`}.

## Commands

```bash
SKILL={{runtime-ref:framework/skills/tool-usage/review/agent-consilium}}

# единый live-view по всем участникам (кадр перерисовывается каждые 3 с)
python3 $SKILL/scripts/consilium.py watch <session_id> [--interval 5] [--excerpt-lines 8]

# поток одного участника (отдельная панель)
python3 $SKILL/scripts/consilium.py watch <session_id> --participant <participant_id>

# один кадр/снимок без цикла (диагностика)
python3 $SKILL/scripts/consilium.py watch <session_id> --once
```

The real ids and excerpt' excerpts of turns are shown intentionally: the watch viewer is the moderator/
owner, anonymization is needed only in the output to participants (bundles).

## herdr panel layout (opt-in, only on explicit request)

The panel layout is NOT created by default and is not a step of the consilium protocol
(owner decision 2026-08-04). The basic way to observe is `watch` in the current panel;
the helper is started only when the user explicitly asks for multi-panel observation.

`scripts/consilium-watch-herdr.sh <session_id> [--interval N]` — helper for
interactive observation in herdr:

- checks `HERDR_ENV=1`, the presence of `herdr` in PATH, and the session directory;
- reads the composition from `session.json`;
- creates a wide panel on the right — a general live-view (`watch` without `--participant`);
- for each participant — a panel below with the `watch --participant <pid>` stream
  (launch via `python3 -u` — line-by-line stdout buffering, otherwise
  tail semantics break in a non-tty panel);
- the layout ceiling is 8 panels: with a larger composition, truncation occurs with an explicit
  warning (the rest are visible in the general status panel); a `pane split`
  failure does not crash the script halfway — the operator receives a message about an incomplete layout;
- the user's focus does not change, other people's panels are not closed; each panel
  exits on its own when the session reaches a terminal state.

Run it from the repository root inside a herdr panel:

```bash
$SKILL/scripts/consilium-watch-herdr.sh <session_id>
```
