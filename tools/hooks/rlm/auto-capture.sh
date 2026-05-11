#!/usr/bin/env bash
# Auto-Capture Hook for RLM Workflow (bash port)
# Event: PostToolUse — fires after every tool call.
# Silently records significant mutations (Edit, Write, notable Bash) to a JSONL buffer.
# Buffer path: ~/.claude/autocapture-buffer.jsonl

set +e

BUFFER_FILE="${HOME}/.claude/autocapture-buffer.jsonl"
mkdir -p "${HOME}/.claude"

INPUT="$(cat)"
[ -z "${INPUT}" ] && exit 0

if ! command -v jq >/dev/null 2>&1; then
    exit 0
fi

TS="$(date -u +%Y-%m-%dT%H:%M:%S)"
TOOL="$(printf '%s' "${INPUT}" | jq -r '.tool_name // empty' 2>/dev/null)"
SESSION="$(printf '%s' "${INPUT}" | jq -r '.session_id // empty' 2>/dev/null)"

[ -z "${TOOL}" ] && exit 0

ENTRY=""

case "${TOOL}" in
    Edit|Write|NotebookEdit)
        FILE_PATH="$(printf '%s' "${INPUT}" | jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' 2>/dev/null)"
        if [ -n "${FILE_PATH}" ]; then
            ENTRY="$(jq -nc --arg ts "${TS}" --arg tool "${TOOL}" --arg file "${FILE_PATH}" --arg sid "${SESSION}" \
                '{ts:$ts, tool:$tool, file:$file, session:$sid}')"
        fi
        ;;
    Bash)
        CMD="$(printf '%s' "${INPUT}" | jq -r '.tool_input.command // empty' 2>/dev/null)"
        if [ -n "${CMD}" ] && printf '%s' "${CMD}" | grep -Eq '(git commit|git push|git tag|git merge|npm run build|dotnet build|pytest|cargo build|make |mvn |gradle |go build|go test)'; then
            CMD_SHORT="$(printf '%s' "${CMD}" | tr -s '[:space:]' ' ' | cut -c1-200)"
            ENTRY="$(jq -nc --arg ts "${TS}" --arg cmd "${CMD_SHORT}" --arg sid "${SESSION}" \
                '{ts:$ts, tool:"Bash", cmd:$cmd, session:$sid}')"
        fi
        ;;
esac

if [ -n "${ENTRY}" ]; then
    printf '%s\n' "${ENTRY}" >> "${BUFFER_FILE}" 2>/dev/null
fi

exit 0
