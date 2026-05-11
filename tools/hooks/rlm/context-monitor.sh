#!/usr/bin/env bash
# Context Monitor Hook for RLM Workflow (bash port)
# Event: PostToolUse — fires after every tool call.
# Reads context % from statusline state file (/tmp/claude-ctx-state.json).
# At 70%: warning. At 80%: injects full суммаризируем instruction.
#
# Depends on: a statusline script writing /tmp/claude-ctx-state.json each turn.

set +e

BUFFER_FILE="${HOME}/.claude/autocapture-buffer.jsonl"
CTX_FILE="${TMPDIR:-/tmp}/claude-ctx-state.json"
ALERT_DIR="${TMPDIR:-/tmp}/claude-ctx-alerts"
STALE_SEC="${RLM_CTX_STALE_SEC:-3600}"

# Триггеры срабатывают по принципу OR: процент ИЛИ абсолютный потолок токенов,
# что наступит раньше. Нужно для больших контекстов (1M), где 70% = 700k — это
# слишком поздно. Можно переопределить через env.
WARN_PCT="${RLM_CTX_WARN_PCT:-70}"
WARN_TOKENS="${RLM_CTX_WARN_TOKENS:-200000}"
CRIT_PCT="${RLM_CTX_CRIT_PCT:-80}"
CRIT_TOKENS="${RLM_CTX_CRIT_TOKENS:-300000}"

INPUT="$(cat)"
[ -z "${INPUT}" ] && exit 0

if ! command -v jq >/dev/null 2>&1; then
    exit 0
fi

SESSION_ID="$(printf '%s' "${INPUT}" | jq -r '.session_id // empty' 2>/dev/null)"
[ -z "${SESSION_ID}" ] && exit 0

[ -f "${CTX_FILE}" ] || exit 0

# Skip if state file is stale
if command -v stat >/dev/null 2>&1; then
    MTIME="$(stat -c %Y "${CTX_FILE}" 2>/dev/null || stat -f %m "${CTX_FILE}" 2>/dev/null)"
    NOW="$(date +%s)"
    if [ -n "${MTIME}" ] && [ $((NOW - MTIME)) -gt "${STALE_SEC}" ]; then
        exit 0
    fi
fi

PCT="$(jq -r '.pct // 0' "${CTX_FILE}" 2>/dev/null)"
TOKENS="$(jq -r '.tokens // 0' "${CTX_FILE}" 2>/dev/null)"
LIMIT="$(jq -r '.limit // 0' "${CTX_FILE}" 2>/dev/null)"

# numeric guard
case "${PCT}" in
    ''|*[!0-9]*) PCT=0 ;;
esac
case "${TOKENS}" in
    ''|*[!0-9]*) TOKENS=0 ;;
esac
[ "${PCT}" -le 0 ] && [ "${TOKENS}" -le 0 ] && exit 0

mkdir -p "${ALERT_DIR}" 2>/dev/null

WARN_FLAG="${ALERT_DIR}/warn_${SESSION_ID}"
CRIT_FLAG="${ALERT_DIR}/crit_${SESSION_ID}"

# Триггер: pct OR абсолютный потолок токенов
hit_crit=0
[ "${PCT}" -ge "${CRIT_PCT}" ] && hit_crit=1
[ "${TOKENS}" -ge "${CRIT_TOKENS}" ] && hit_crit=1

hit_warn=0
[ "${PCT}" -ge "${WARN_PCT}" ] && hit_warn=1
[ "${TOKENS}" -ge "${WARN_TOKENS}" ] && hit_warn=1

if [ "${hit_crit}" -eq 1 ]; then
    if [ ! -f "${CRIT_FLAG}" ]; then
        : > "${CRIT_FLAG}"
        cat <<EOF
CONTEXT AUTO-SUMMARIZE: ${PCT}% used (${TOKENS}/${LIMIT} tokens; trigger: pct>=${CRIT_PCT}% OR tokens>=${CRIT_TOKENS}). Execute full суммаризируем ritual NOW before responding to anything else:

IF active agent team exists (TeamCreate was called this session):
  A. SendMessage(type="broadcast", content="CHECKPOINT: Save your current state to RLM immediately (rlm_add_hierarchical_fact with what you've done, files changed, decisions, remaining). Then stop and wait.")
  B. Wait for teammates to confirm saves (or 30s timeout)
  C. SendMessage(type="shutdown_request") to each teammate
  D. Wait for shutdown confirmations

THEN (always):
1. Read autocapture buffer: cat '${BUFFER_FILE}'
2. rlm_add_hierarchical_fact(content="TASK PROGRESS [task_id] auto-save at ${PCT}%: Done: <summary>. Files: <list>. Decisions: <list>. Remaining: <list>.", domain="retrospective", level=1)
3. rlm_add_hierarchical_fact(content="PENDING tasks next session [task_id: <id>]: <numbered list or 'none'>", domain="workflow", level=1)
4. rlm_sync_state()
5. git status → commit if needed
6. Clear buffer: : > "${BUFFER_FILE}"

MANDATORY FINAL OUTPUT FORMAT:
After completing steps 1-6, your response MUST END with EXACTLY this line, on its own, as the last line — no rephrasing, no extras after it, no markdown decoration around it:

Контекст сохранён в RLM. Жми /clear.

This is a contract with the user: they look for this exact string to know it's safe to /clear. Anything else (extended summaries, "I also did X", emojis, follow-up questions) goes BEFORE this line, never after. If you cannot complete steps 1-6, still end with that exact line preceded by a short note about what failed.
EOF
    fi
elif [ "${hit_warn}" -eq 1 ]; then
    if [ ! -f "${WARN_FLAG}" ]; then
        : > "${WARN_FLAG}"
        echo "CONTEXT WARNING: ${PCT}% used (${TOKENS}/${LIMIT} tokens; trigger: pct>=${WARN_PCT}% OR tokens>=${WARN_TOKENS}). Start wrapping up current task — суммаризируем will auto-trigger at ${CRIT_PCT}% or ${CRIT_TOKENS} tokens."
    fi
fi

exit 0
