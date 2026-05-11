#!/usr/bin/env bash
# Pre-Compact Hook for RLM Workflow (bash port)
# Event: PreCompact — fires before Claude Code auto-compacts the conversation.
# Mechanism: stdout output is injected as a system message into Claude's context.

set +e

TS="$(date -u +%Y-%m-%dT%H:%M:%S)"

cat <<EOF
AUTO-COMPACT TRIGGERED at ${TS}. MANDATORY: Save session state to RLM NOW — before compacting erases context.

Execute these 3 calls immediately, before anything else:

1. rlm_add_hierarchical_fact(
     content="TASK PROGRESS [task_id if known] auto-compact ${TS}:
      Done: <1-2 sentence summary of accomplished work>.
      Files: <list key modified files>.
      Decisions: <key architectural/design decisions>.
      Remaining: <what still needs to be done>.",
     domain="retrospective", level=1
   )

2. rlm_add_hierarchical_fact(
     content="PENDING tasks next session [task_id: <id if known>]: <numbered list. Write 'none' if nothing pending>",
     domain="workflow", level=1
   )

3. rlm_sync_state()

Context can be restored in next session with: контекст
EOF

exit 0
