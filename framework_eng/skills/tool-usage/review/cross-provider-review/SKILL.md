---
name: cross-provider-review
description: "Use for advisory second-opinion review between model families. Routes GPT/Codex primary agents to Claude/Opus review, and Claude/Opus/Sonnet primary agents to GPT/Codex review; supports isolated sandbox sessions, follow-up, debate, sync, status, log, stats, show, and close lifecycle. Suitable for acceptance-bound artifact review and free-form criticism of ideas/documents."
capabilities: review,agent-governance,cross-provider
---

# Cross-Provider Review

A single skill for cross-family second opinion. The reviewer is an advisory layer, not the final authority, and must not
edit the real project.

AI governance classification: `advice-only`. Owner: orchestrator/primary agent. HITL is required where workflow,
product, or architectural approval gates require it. Quality signal: evidence-backed findings, a clear primary-agent
position, review trace, and observable lifecycle/cleanup.

## Routing

- If the primary agent belongs to the GPT/Codex family, use the Claude/Opus adapter:
  `.agents/skills/cross-provider-review/scripts/claude_opus_review.py`
- If the primary agent belongs to the Claude/Opus/Sonnet family, use the Codex/GPT adapter:
  `.agents/skills/cross-provider-review/scripts/codex_review.py`
- Same-family/self-review does not satisfy the cross-family gate.

## Modes

The skill works in two modes with different verdict semantics:

- **advisory** (default) - per-artifact review within a phase. The final word belongs to the primary agent/orchestrator; the reviewer provides a second opinion that is handled as regular feedback. All per-artifact workflow runs are advisory.
- **gate** - final review before task closure. The reviewer verdict is blocking: `verdict: PASS` is a mandatory condition for completion. This mode is used by the orchestrator exactly once at the end of the task, instead of an advisory final.

The mode is fixed in the introductory prompt (via `--constraints` / `--review-ask`) - the reviewer must explicitly know whether the verdict is blocking or advisory.

## Prompts

- `references/review-prompt.md` - default shape for advisory review (task artifacts and acceptance-bound reviews). Can be used in a simplified form for free-form opinion review / idea critique, as long as the read-only and evidence boundaries are explicit.
- `references/finalization-prompt.md` - template for **gate** mode (task finalization). Includes a strict structure: bidirectional rule compliance check, goal verification with a traceability table, anti-deception checklist, and an iterative protocol with escalation to the user after 3 rounds.

## Session Lifecycle

Both adapters support the same lifecycle:

- `start`: creates `.review-sandboxes/<review_id>/workspace`, materializes focused paths or full context (by default via hardlink - almost instant and with no disk usage) and launches the reviewer.
- `ask`: continues a saved session.
- `debate`: discusses one specific finding.
- `sync`: updates the sandbox from the real source paths.
- `status`: shows phase, heartbeat, pid, logs, timeout, result preview, and live progress counters.
- `log`: shows prompt/response history.
- `stats`: shows available token/cost stats, raw event stats, and tool-call counters.
- `show`: shows review metadata, cumulative stats, and runtime state as a single JSON payload.
- `close`: closes and, by default, removes the sandbox; use `--keep-sandbox` only for forensic/debug purposes.

Status interpretation: a moving heartbeat means the process is alive; a stale heartbeat without stdout/stderr growth is
practical evidence of a stuck state; `phase=timeout` means a single invocation exceeded the timeout.

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

After `start`, use the same lifecycle for both adapters. In the examples below, `<adapter-script>` means the script selected
by routing:

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

Use `status` while a blocking review is running for a long time. Use `sync` after changing source artifacts and before
follow-up or delta review. Use `log`, `stats`, and `show` for trace/debug; these are not mandatory commands on every
happy path. Use `close --keep-sandbox` only for rare forensic/debug cases.

`status.runtime.progress` and `stats` include adapter-observable activity for Claude and Codex reviews:

- `raw_events`: number of JSON events from the CLI;
- `event_types`: event counters by type;
- `tool_calls_total`: total number of unique observed tool/function calls;
- `tool_calls_by_name`: counters for tool/function calls by tool name;
- `unique_tool_call_ids`: number of unique tool/function call ids, if the CLI provides ids;
- `tool_result_events`: observed tool/function result events;
- `permission_denials`: observed permission-denial events;
- `server_tool_use`: provider-reported server-side tool counters, if available.

This is runtime observability, not a replacement for reviewer conclusions. The counters help distinguish a truly active
review from a process where only the heartbeat changes.

## Useful Options

- `--review-id`: set a stable ID for task traceability.
- `--timeout-sec`: change the timeout of a single reviewer invocation.
- `--copy-mode {hardlink,copy}`: sandbox materialization mode. `hardlink` (default) - almost instant, ~0 bytes on disk; `copy` - full byte copy. Hardlink automatically falls back to copy on cross-device or unsupported FS.
- `--keep-sandbox`: preserve review files on `close` only for forensic/debug.
- Codex only: `--artifact-type`, `--skills`, `--reasoning-effort`.
- Claude only: `--model`.

## Acceptance-Bound Protocol (advisory)

For per-artifact acceptance-bound review (advisory mode):

1. Launch the opposite-family adapter.
2. Monitor the process through `status` if the review takes a long time.
3. Assign finding IDs (`F-01...`) if the reviewer did not do so.
4. Check each finding against the real artifacts and mark `agree`, `partial`, `disagree`, `withdrawn`, or `out_of_scope`.
5. Add primary-agent findings as `C-01...` if needed.
6. If the source artifacts changed after rework, run `sync` before follow-up or delta review.
7. Use `ask` for follow-up/delta review and `debate` only for specific disputed finding IDs.
8. Use `log`, `stats`, or `show` when trace/debug evidence is needed.
9. Stop at consensus, unchanged stalemate for two rounds, or the maximum round count.
10. Close the review when it is no longer needed.
11. Record the final report: unified findings, disagreements with both positions, iteration count, recommendation,
    review id, cleanup status, and relevant status/log evidence.

## Finalization Gate Protocol (blocking)

Used by the orchestrator once at the end of the task. Unlike the advisory protocol, the reviewer has the final word here.

**Prerequisite:** the orchestrator must assemble a complete evidence pack (see `references/finalization-prompt.md` section "Input data"). If any item is missing, the reviewer responds `verdict: FAIL` on the first round.

**Steps:**

1. Launch the opposite-family adapter with the prompt from `references/finalization-prompt.md`. In `--constraints`, specify: "Finalization gate mode. Verdict is blocking, not advisory. Use bidirectional rule compliance check."
2. Pass the complete evidence pack (file paths + git diff + test stdout).
3. Receive the response: findings + `verdict: PASS | FAIL` + `iteration: N of 3`.
4. If `verdict: PASS` - the task may be closed. Record the review_id in `final-report.md` in the `cross_provider_review` block.
5. If `verdict: FAIL` - address the findings with evidence-based fixes (diff, new stdout, clarified log). Use `ask` for the next round.
6. If `iteration: 3` and the verdict is not `PASS` - the reviewer issues `escalate_to_user: true` with `dispute_summary`. The orchestrator must escalate to the user, passing the dispute_summary verbatim. The user's decision is final.
7. Close the review (`close`) only after a documented PASS verdict or user override.

**Forbidden:**

- Closing the task (`final-report.md` + report to the user "done") without `verdict: PASS` or a user override.
- Degrading findings from round to round - the reviewer is not required to soften.
- Running gate mode in same-family (Claude->Claude or Codex->Codex) - this violates the cross-family gate requirement.

## Safety

- Reviewers work in an isolated sandbox workspace, not in the real project. By default, the sandbox is a hardlink mirror of the source: writes from the primary agent to real files create a new inode, and the reviewer continues to see the frozen snapshot until an explicit `sync`. The reviewers themselves are strictly read-only (see below), so hardlinks are safe: writing through them is impossible.
- Full-context materialization excludes `.git`, `.venv`, `.review-sandboxes`, `node_modules`, `__pycache__`, common build outputs, and `.claude`, `.codex`, `.cursor`, `.windsurf`, `.idea` so the reviewer does not pick up hooks/permissions/MCP configs from the real project.
- Reviewer prompts and adapter prompts include read-only instructions.
- **Codex** runs with `--sandbox read-only` - the kernel-level sandbox blocks any writes regardless of what the model wants.
- **Claude** runs with `--tools=Read,Grep,Glob,LS`, `--permission-mode plan` (plan-only mode without write/edit) and `--strict-mcp-config` (without `--mcp-config` this means "no MCP servers at all"). This is a three-layer permission-level guarantee.
- The primary agent remains responsible for acceptance, rework, and final synthesis.
