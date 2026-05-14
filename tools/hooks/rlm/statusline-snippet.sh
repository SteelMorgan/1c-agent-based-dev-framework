#!/usr/bin/env bash
# RLM context-monitor integration snippet for Claude Code statusLine.
#
# WHAT: writes /tmp/claude-ctx-state.json on every statusline tick.
# WHO READS IT: tools/hooks/rlm/context-monitor.sh (triggers 70%/80%/300k).
# WITHOUT THIS: context-monitor.sh sees no input, ritual "summarize" never auto-fires.
#
# HOW TO USE:
#   1. Open your existing ~/.claude/statusline-command.sh.
#   2. Paste the marked block below somewhere AFTER `input=$(cat)`
#      but BEFORE printing the prompt. The block does not produce stdout —
#      it only writes the JSON state file.
#   3. If you do not have a statusline command yet, this whole file works as a
#      minimal stub: wire it via `statusLine.command` in ~/.claude/settings.json.
#
# CONTRACT:
#   path:   ${TMPDIR:-/tmp}/claude-ctx-state.json
#   schema: {"pct":<int>, "tokens":<int>, "limit":<int>}

input=$(cat)

# >>> RLM context-state block — start (paste this into your statusline) >>>
# Claude Code 2.x payload schema: three counters fill the context window.
# Summing all three is critical — taking only input_tokens under-reports usage
# by 2-10x, so context-monitor.sh thresholds would never fire.
ctx_used_tokens=$(echo "$input" | jq -r '
    (.context_window.current_usage // {}) as $u
    | (($u.input_tokens // 0)
       + ($u.cache_creation_input_tokens // 0)
       + ($u.cache_read_input_tokens // 0))
')
ctx_limit=$(echo "$input" | jq -r '.context_window.context_window_size // 200000')
ctx_pct_raw=$(echo "$input" | jq -r '.context_window.used_percentage // empty')
ctx_pct_int=$(printf '%.0f' "${ctx_pct_raw:-0}")

printf '{"pct":%s,"tokens":%s,"limit":%s}' \
    "${ctx_pct_int}" "${ctx_used_tokens:-0}" "${ctx_limit}" \
    > "${TMPDIR:-/tmp}/claude-ctx-state.json" 2>/dev/null || true
# <<< RLM context-state block — end <<<

# Minimal stub statusline output (replace with your own prompt rendering):
model=$(echo "$input" | jq -r '.model.display_name // ""')
printf "%s ctx:%s%%" "${model}" "${ctx_pct_int}"
