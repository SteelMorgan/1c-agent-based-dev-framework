---
name: cross-provider-review
description: "For second review by another model and debate sessions"
capabilities: review,agent-governance,cross-provider
---

# Cross-Provider Review

A single skill for cross-family second opinion. The reviewer is an advisory layer, not the final authority, and must not
modify the real project.

AI governance classification: `advice-only`. Owner: orchestrator/primary agent. HITL is required wherever workflow,
product, or architecture approval gates demand it. Quality signal: evidence-backed findings, explicit primary-agent
position, review trace, and observable lifecycle/cleanup.

## Routing

- If the primary agent belongs to the GPT/Codex family, use the Claude/Opus adapter:
  `.agents/skills/cross-provider-review/scripts/claude_opus_review.py`
- If the primary agent belongs to the Claude/Opus/Sonnet family, use the Codex/GPT adapter:
  `.agents/skills/cross-provider-review/scripts/codex_review.py`
- Same-family/self-review does not satisfy the cross-family gate.

## Modes

The skill works in two modes with different verdict semantics:

- **advisory** (default) — per-artifact review within a phase. The primary agent / orchestrator has the final word; the
  reviewer provides a second opinion, which is handled as ordinary feedback. All per-artifact runs in the workflow are
  advisory.
- **gate** — final review before task closure. The reviewer’s verdict is blocking: `verdict: PASS` is a mandatory
  condition for completion. This mode is used by the orchestrator exactly once at the end of the task, instead of an
  advisory final pass.

The mode is fixed in the opening prompt (via `--constraints` / `--review-ask`) — the reviewer must explicitly know
whether the verdict is blocking or advisory.

## Prompts

- `references/review-prompt.md` — default shape for advisory review (task artifacts and acceptance-bound reviews). It
  may also be used in a simplified form for free-form opinion review / idea critique, as long as read-only and evidence
  boundaries are explicit.
- `references/finalization-prompt.md` — template for **gate** mode (task finalization). Includes a strict structure:
  bidirectional rule compliance check, goal verification with a traceability table, anti-deception checklist, and an
  iterative protocol with escalation to the user after 3 rounds.

## Session Lifecycle

Both adapters support the same lifecycle:

- `start`: creates `.review-sandboxes/<review_id>/workspace`, materializes focused paths or the full context (by
  default via hardlink — nearly instant and with no disk overhead), and launches the reviewer.
- `ask`: continues a saved session.
- `debate`: discusses one specific finding.
- `sync`: refreshes the sandbox from the real source paths.
- `status`: shows phase, heartbeat, pid, logs, timeout, result preview, and live progress counters.
- `log`: shows the prompt/response history.
- `stats`: shows available token/cost stats, raw event stats, and tool-call counters.
- `show`: shows review metadata, cumulative stats, and runtime state as a single JSON payload.
- `close`: closes and, by default, deletes the sandbox; use `--keep-sandbox` only for forensic/debug purposes.

Status interpretation: a moving heartbeat means the process is alive; a stale heartbeat without stdout/stderr growth is
a practical sign of a stuck state; `phase=timeout` means one invocation exceeded the timeout.

## 🔴 CRITICAL: mandatory sandbox cleanup (`close`)

> **Level: CRITICAL / MUST.** Sandbox cleanup is tied EXCLUSIVELY to an explicit `close` call. The adapters have NO
> automatic cleanup: no `atexit`, no signal handler, no TTL/age sweep, and no orphaned-sandbox collection at `start`.
> If the agent does not reach `close` (crash, yield, error/FAIL branch, escalation, forgetfulness), the
> `.review-sandboxes/<review_id>/` directory together with the full-context source mirror remains on disk **forever**.
> In practice, this has already led to dozens of orphaned directories that were removed manually.

**MUST for every `start`:**

| Requirement | Description |
|-----------|----------|
| start↔close pairing | Any `start` MUST have a paired `close` in the same agent work session. `start` without a guaranteed `close` is forbidden |
| close on all branches | `close` is called on ANY termination path: PASS, FAIL, user escalation, review refusal, adapter error. Not only on the happy path |
| close in finalization | Before writing `final-report.md`, the agent MUST make sure that all open reviews are closed (see the checkpoint below) |
| cleanup report | The final report/context records the `cleanup status` of each review_id: `closed` or (rarely) `kept --keep-sandbox: <forensic reason>` |
| `--keep-sandbox` only when justified | Use ONLY for forensic/debug with an explicit written reason. Default is an ordinary `close` with deletion |

**✅ CHECKPOINT before task completion (MUST be executed):**

```bash
# 1. Show all UNclosed sandboxes in the project:
ls -1 .review-sandboxes/ 2>/dev/null
# 2. For each remaining <review_id> — close it:
<adapter-script> close <review_id>
# 3. Confirm that the directory is empty (expected 0):
ls -1 .review-sandboxes/ 2>/dev/null | wc -l
```

If step 3 returned anything other than 0, the task is NOT considered complete with respect to cleanup: close the
remaining reviews and only then close the task. A non-empty `.review-sandboxes/` at the time of the final report is a
violation of this skill.

## Claude / Opus Adapter

Start:

```bash
.agents/skills/cross-provider-review/scripts/claude_opus_review.py start \
  --full-context \
  --task "<task>" \
  --goal "<review focus>" \
  --requirements "<requirements>" \
  --constraints "Second-opinion review only. Do not implement fixes." \
  --primary-target "<file>" \
  --changed-files <file1> <file2> \
  --open-concerns "<concerns>" \
  --review-ask "Review this artifact as a second opinion. Order findings by severity." \
  --question "Perform a second-opinion review of the current work."
```

Focused/free-form:

```bash
.agents/skills/cross-provider-review/scripts/claude_opus_review.py start \
  --question "Review this idea and identify the strongest counterarguments." path/to/file.md
```

## Codex / GPT Adapter

Start:

```bash
.agents/skills/cross-provider-review/scripts/codex_review.py start \
  --full-context \
  --task "<task>" \
  --goal "<review focus>" \
  --artifact-type "<code|tests|architecture|policy|prompt>" \
  --requirements "<requirements>" \
  --constraints "Second-opinion review only. Do not implement fixes." \
  --primary-target "<file>" \
  --changed-files <file1> <file2> \
  --open-concerns "<concerns>" \
  --review-ask "Review this artifact as a second opinion. Order findings by severity." \
  --question "Perform a second-opinion review of the current work."
```

Focused/free-form:

```bash
.agents/skills/cross-provider-review/scripts/codex_review.py start \
  --question "Review this idea and identify the strongest counterarguments." path/to/file.md
```

## Common Session Commands

After `start`, use the same lifecycle for both adapters. In the examples below, `<adapter-script>` means the script
selected by routing:

- `.agents/skills/cross-provider-review/scripts/claude_opus_review.py`
- `.agents/skills/cross-provider-review/scripts/codex_review.py`

```bash
<adapter-script> ask REVIEW_ID --question "..."
<adapter-script> debate REVIEW_ID --issue "F-01" --finding "..." --position "..."
<adapter-script> sync REVIEW_ID
<adapter-script> status REVIEW_ID
<adapter-script> log REVIEW_ID
<adapter-script> stats REVIEW_ID
<adapter-script> show REVIEW_ID
<adapter-script> close REVIEW_ID
```

Use `status` while a blocking review is taking a long time. Use `sync` after source artifacts change and before a
follow-up or delta review. Use `log`, `stats`, and `show` for trace/debug; these are not required on every happy path.
Use `close --keep-sandbox` only for rare forensic/debug cases.

`status.runtime.progress` and `stats` include adapter-observable activity for Claude and Codex reviews:

- `raw_events`: number of CLI JSON events;
- `event_types`: counters by event type;
- `tool_calls_total`: total number of unique observed tool/function calls;
- `tool_calls_by_name`: counters for tool/function calls by tool name;
- `unique_tool_call_ids`: number of unique tool/function call ids, if the CLI emits ids;
- `tool_result_events`: observed tool/function result events;
- `permission_denials`: observed permission-denial events;
- `server_tool_use`: provider-reported server-side tool counters, if available.

This is runtime observability, not a replacement for the reviewer’s conclusions. The counters help distinguish a truly
active review from a process in which only the heartbeat changes.

## Useful Options

- `--review-id`: set a stable ID for task traceability.
- `--timeout-sec`: change the timeout for one reviewer invocation.
- `--copy-mode {hardlink,copy}`: sandbox materialization method. `hardlink` (default) is nearly instant and uses ~0
  bytes on disk; `copy` is a full byte-for-byte copy. Hardlink automatically falls back to copy on cross-device or
  unsupported filesystems.
- `--keep-sandbox`: preserve review files on `close` only for forensic/debug.
- Codex only: `--artifact-type`, `--skills`, `--reasoning-effort`.
- Claude only: `--model`.

## Acceptance-Bound Protocol (advisory)

For per-artifact acceptance-bound reviews (advisory mode):

1. Launch the opposite-family adapter.
2. Monitor the process with `status` if the review takes a long time.
3. Assign finding IDs (`F-01...`) if the reviewer did not do so.
4. Check each finding against the real artifacts and mark `agree`, `partial`, `disagree`, `withdrawn`, or
   `out_of_scope`.
5. Add primary-agent findings as `C-01...` if needed.
6. If the source artifacts changed after rework, run `sync` before a follow-up or delta review.
7. Use `ask` for follow-up/delta review and `debate` only for specific disputed finding IDs.
8. Use `log`, `stats`, or `show` when trace/debug evidence is needed.
9. Stop at consensus, an unchanged stalemate for two rounds, or the maximum round count.
10. **🔴 MUST — `close` REVIEW_ID as soon as the review is no longer needed** (see the “CRITICAL: mandatory sandbox
    cleanup” section). This is not “when convenient” but a required closing step: without it, the sandbox remains on
    disk forever. `close` is called even if the review ended in refusal/error.
11. Record the final report: unified findings, disagreements with both positions, iteration count, recommendation,
    review id, **cleanup status (`closed` for each review_id)**, and relevant status/log evidence. Before closing the
    task, run the CHECKPOINT from the “CRITICAL” section: `.review-sandboxes/` must be empty.

## Finalization Gate Protocol (blocking)

Used by the orchestrator once at the end of the task. Unlike the advisory protocol, the reviewer has the final word
here.

**Precondition:** the orchestrator must assemble a complete evidence pack (see the “Input data” section in
`references/finalization-prompt.md`). If any item is missing, the reviewer responds with `verdict: FAIL` on the very
first round.

**Steps:**

1. Launch the opposite-family adapter with the prompt from `references/finalization-prompt.md`. In `--constraints`,
   specify: “Finalization gate mode. Verdict is blocking, not advisory. Use bidirectional rule compliance check.”
2. Pass the full evidence pack (file paths + git diff + test stdout).
3. Receive the response: findings + `verdict: PASS | FAIL` + `iteration: N of 3`.
4. If `verdict: PASS` — the task may be closed. Record the review_id in `final-report.md` in the `cross_provider_review`
   block.
5. If `verdict: FAIL` — apply evidence-based fixes to the findings (diff, new stdout, clarified log). Use `ask` for the
   next round.
6. If `iteration: 3` and the verdict is not `PASS` — the reviewer provides `escalate_to_user: true` with
   `dispute_summary`. The orchestrator must escalate to the user by passing `dispute_summary` verbatim. The user’s
   decision is final.
7. **🔴 MUST — `close` the review after a documented PASS verdict or user override** (see the “CRITICAL: mandatory
   sandbox cleanup” section). Closing the gate review is mandatory in ALL outcomes, including escalation after 3 rounds:
   after recording the verdict/override in `final-report.md`, the sandbox must be deleted via `close`. Then run the
   CHECKPOINT: `.review-sandboxes/` is empty.

**Forbidden:**

- Close the task (`final-report.md` + tell the user “done”) without `verdict: PASS` or a user override.
- Close the task with a non-empty `.review-sandboxes/` — every review_id MUST be `close`-d (see the CHECKPOINT in the
  “CRITICAL” section).
- Degrade findings round after round — the reviewer is not required to soften.
- Run gate mode in same-family (Claude→Claude or Codex→Codex) — this violates the cross-family gate requirement.

## Safety

- Reviewers work in an isolated sandbox workspace, not in the real project. By default, the sandbox is a hardlink mirror
  of the source: writes from the primary agent to real files create a new inode, and the reviewer continues to see the
  frozen snapshot until an explicit `sync`. The reviewers themselves are strictly read-only (see below), so hardlinks
  are safe: writes through them are impossible.
- Full-context materialization excludes `.git`, `.venv`, `.review-sandboxes`, `node_modules`, `__pycache__`, common
  build outputs, and `.claude`, `.codex`, `.cursor`, `.windsurf`, `.idea` — so the reviewer does not pick up hooks,
  permissions, or MCP configs from the real project.
- Reviewer prompts and adapter prompts include read-only instructions.
- **Codex** runs with `--sandbox read-only` — kernel-level sandboxing blocks any writes regardless of what the model
  wants.
- **Claude** runs with `--tools=Read,Grep,Glob,LS`, `--permission-mode plan` (plan-only mode without write/edit), and
  `--strict-mcp-config` (without `--mcp-config` this means “no MCP servers at all”). This is a three-layer
  permission-level guarantee.
- The primary agent remains responsible for acceptance, rework, and final synthesis.
