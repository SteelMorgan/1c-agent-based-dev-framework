#!/usr/bin/env bash
# Claude Code statusLine command — двухколоночный мини-дашборд.
#
# Row 1: context window  · % · tokens left · model · effort · [git branch]
# Row 2: column header   ·   Claude   │   Codex   (только если Codex доступен)
# Row 3: 5-hour windows  ·   claude bar · pct · delta-time   │   codex bar · pct · delta-time
# Row 4: 7-day  windows  ·   claude bar · pct · delta-time   │   codex bar · pct · delta-time · freshness
#
# Стиль бара: shade — █ для заполненного, ░ для пустого.
# Цвет заполнения: <50% зелёный, 50..79 жёлтый, >=80% красный.
# Дельта до сброса округлена: "<1h" / "in Xh" / "in Xd".
#
# Codex данные снимаются из последнего ~/.codex/sessions/.../rollout-*.jsonl
# (поле rate_limits.primary = 5h, .secondary = 7d). Если файл старше 14 дней
# или не найден — правая колонка не печатается.
#
# Side-effect: пишет /tmp/claude-ctx-state.json для RLM context-monitor hook.

input=$(cat)

# === Helpers =================================================================

# 20-cell shade progress bar: █████░░░░░ (filled solid + light shade)
# Цвет filled зависит от %: <50 green, 50..79 yellow, >=80 red.
bar() {
    local pct="${1:-0}" w="${2:-20}"
    local fill=$(( (pct * w + 50) / 100 ))
    [ "$pct" -gt 0 ] && [ "$fill" -eq 0 ] && fill=1
    [ "$fill" -gt "$w" ] && fill=$w
    local empty=$(( w - fill ))

    # Пастельные тона — не отвлекают глаз
    local fc
    if   [ "$pct" -ge 80 ]; then fc='\033[38;5;217m'   # soft coral pink
    elif [ "$pct" -ge 50 ]; then fc='\033[38;5;222m'   # soft peach
    else                         fc='\033[38;5;151m'   # soft mint
    fi
    local ec='\033[38;5;238m'

    # Стили баров — задают, к какому краю ячейки прижат глиф:
    #   bottom (по умолчанию) — ▆/▂, плотный 3/4-блок снизу. Для context и 7d.
    #   top                    — ▀/▔, тонкий half-блок сверху. Для 5h.
    # 5h «смотрит вверх», 7d «смотрит вниз» — между рядами максимальный зазор.
    local fch ech
    case "${3:-bottom}" in
        top) fch='▀'; ech='▔' ;;
        *)   fch='▆'; ech='▂' ;;
    esac
    local f="" e="" i
    for (( i=0; i<fill;  i++ )); do f="${f}${fch}"; done
    for (( i=0; i<empty; i++ )); do e="${e}${ech}"; done

    printf "${fc}${f}${ec}${e}\033[0m"
}

# Целое число с разделителями тысяч (без locale).
fmt_num() {
    local n="${1:-0}"
    n="${n%%.*}"
    local result="" len="${#n}" i
    for (( i=0; i<len; i++ )); do
        if (( (len - i) % 3 == 0 && i > 0 )); then result="${result},"; fi
        result="${result}${n:$i:1}"
    done
    printf '%s' "$result"
}

# Unix epoch → "in 4h" / "in 4d" / "<1h" / "now" / "—".
# <60min  → "<1h"; 1..47h → "in Xh"; >=48h → "in Xd".
fmt_delta() {
    local target="$1"
    if [ -z "$target" ] || [ "$target" = "null" ]; then
        printf '—'
        return
    fi
    local now diff
    now=$(date +%s)
    diff=$(( target - now ))
    if   [ "$diff" -le 0 ];     then printf 'now'
    elif [ "$diff" -lt 3600 ];  then printf '<1h'
    elif [ "$diff" -lt 172800 ]; then printf 'in %dh' $(( diff / 3600 ))
    else                              printf 'in %dd' $(( diff / 86400 ))
    fi
}

# Pad a plain (no-ANSI) string to N visible characters with spaces.
pad_to() {
    local target="$1" str="$2"
    local len="${#str}"
    local pad=$(( target - len ))
    printf '%s' "$str"
    [ "$pad" -gt 0 ] && printf '%*s' "$pad" ""
}

# Pace-коэффициент по недельному окну:
#   k = pct × WEEK / (elapsed × 100)
#     = доля_использованного_лимита / доля_прошедшего_времени_окна
# k<1 — успеваю до резета, k≈1 — впритык, k>1 — сгорю раньше.
# args: pct  resets_at
# stdout: число "0.8" или пусто (если данных недостаточно)
pace_coef() {
    local pct="$1" resets_at="$2"
    [ -z "$pct" ] && return 0
    [ -z "$resets_at" ] || [ "$resets_at" = "null" ] && return 0
    local now week window_start elapsed
    now=$(date +%s)
    week=604800
    window_start=$(( resets_at - week ))
    elapsed=$(( now - window_start ))
    # Окно только началось — знаменатель слишком мал, цифра прыгает
    [ "$elapsed" -le 1800 ] && return 0
    LC_ALL=C awk -v p="$pct" -v e="$elapsed" -v w="$week" \
        'BEGIN { printf "%.1f", p * w / (e * 100) }'
}

# Цвет для pace-коэффициента (пастельный)
pace_color() {
    local k="$1"
    [ -z "$k" ] && { printf '%s' '\033[38;5;245m'; return; }
    local k_x100
    k_x100=$(LC_ALL=C awk -v k="$k" 'BEGIN { printf "%d", k * 100 }')
    if   [ "$k_x100" -gt 110 ]; then printf '%s' '\033[38;5;217m'   # coral
    elif [ "$k_x100" -gt  90 ]; then printf '%s' '\033[38;5;222m'   # peach
    else                              printf '%s' '\033[38;5;151m'   # mint
    fi
}

# === Parse input =============================================================

parsed=$(echo "$input" | jq -r '
    [
        (.context_window.used_percentage       // ""),
        (.context_window.remaining_percentage  // ""),
        (.context_window.context_window_size   // 200000 | tostring),
        ((.context_window.current_usage // {}) as $u
         | (($u.input_tokens // 0)
            + ($u.cache_creation_input_tokens // 0)
            + ($u.cache_read_input_tokens // 0)) | tostring),
        (.model.display_name // ""),
        (.model.id           // ""),
        (.rate_limits.five_hour.used_percentage // ""),
        (.rate_limits.five_hour.resets_at       // ""),
        (.rate_limits.seven_day.used_percentage // ""),
        (.rate_limits.seven_day.resets_at       // ""),
        (.effort.level       // ""),
        (.workspace.current_dir // .cwd // ""),
        (.session_id // "")
    ] | join("")
')

IFS=$'\x1f' read -r \
    ctx_used_pct ctx_remaining_pct ctx_limit ctx_used_tokens \
    model_name model_id \
    five_h_pct five_h_resets \
    seven_d_pct seven_d_resets \
    effort_level cwd session_id \
    <<< "$parsed"

# === RLM context-monitor side-effect =========================================

ctx_pct_int=$(LC_ALL=C printf '%.0f' "${ctx_used_pct:-0}")
printf '{"pct":%s,"tokens":%s,"limit":%s}' \
    "${ctx_pct_int}" "${ctx_used_tokens:-0}" "${ctx_limit:-200000}" \
    > "${TMPDIR:-/tmp}/claude-ctx-state.json" 2>/dev/null || true

# === Colours =================================================================

RESET='\033[0m'
WHITE='\033[0;37m'
# Пастельная палитра 256-color — мягкие тона, не отвлекают глаз
CYAN='\033[38;5;152m'    # pale cyan — проценты
PURPLE='\033[38;5;183m'  # lavender — tokens left
GRAY='\033[38;5;245m'
DIM='\033[38;5;245m'
BLUE='\033[38;5;117m'    # soft sky — model name
GREEN='\033[38;5;151m'   # soft mint — effort, pace ok
YELLOW='\033[38;5;222m'  # soft peach — pace впритык
RED='\033[38;5;217m'     # soft coral — pace warn, dirty git
PINK='\033[38;5;217m'

# === Подготовка значений =====================================================

# Tokens left
if [ -n "$ctx_limit" ] && [ -n "$ctx_used_tokens" ]; then
    rem=$(( ctx_limit - ctx_used_tokens ))
    [ "$rem" -lt 0 ] && rem=0
    ctx_left="$(fmt_num "$rem") left"
else
    ctx_left="?"
fi

# Model label (Family + Version + опционально (1M context))
_mid_lower="${model_id,,}"
case "$_mid_lower" in
    *opus*)   _family="Opus"   ;;
    *sonnet*) _family="Sonnet" ;;
    *haiku*)  _family="Haiku"  ;;
    *)        _family=""       ;;
esac
_stripped="${_mid_lower#*${_family,,}-}"
_ver=$(printf '%s' "$_stripped" | grep -oE '^[0-9]+(-[0-9]+)?' | head -1)
_ver="${_ver//-/.}"
if printf '%s' "$_mid_lower" | grep -qE '\[1m\]|[-_]1m([-\[]|$)'; then
    _ctx_tag=" (1M)"
else
    _ctx_tag=""
fi
if [ -n "$_family" ] && [ -n "$_ver" ]; then
    model_label="${_family} ${_ver}${_ctx_tag}"
else
    _dn="${model_name}"
    _dn="${_dn/ (1M context)/}"
    _dn="${_dn/ (200K context)/}"
    model_label="${_dn}${_ctx_tag}"
fi

# Effort
case "$effort_level" in
    low|medium|high|xhigh|max) effort_label="$effort_level" ;;
    *)                         effort_label=""             ;;
esac

# Git branch (с маркером * если dirty) — пастельные тона
git_line=""
if [ -n "$cwd" ]; then
    _branch=$(git --no-optional-locks -C "$cwd" symbolic-ref --short HEAD 2>/dev/null)
    if [ -n "$_branch" ]; then
        _dirty=$(git --no-optional-locks -C "$cwd" status --porcelain 2>/dev/null | head -1)
        if [ -n "$_dirty" ]; then
            git_line=$(printf '\033[38;5;152m[%s \033[38;5;217m*\033[38;5;152m]\033[0m' "$_branch")
        else
            git_line=$(printf '\033[38;5;152m[%s]\033[0m' "$_branch")
        fi
    fi
fi

# Claude limits — целочисленные проценты и дельты
five_h_pct_int=$(LC_ALL=C printf '%.0f' "${five_h_pct:-0}")
seven_d_pct_int=$(LC_ALL=C printf '%.0f' "${seven_d_pct:-0}")
five_h_delta=$(fmt_delta "$five_h_resets")
seven_d_delta=$(fmt_delta "$seven_d_resets")

# Codex limits — парсим из rollout-jsonl последней активной сессии
codex_5h_pct=""; codex_5h_reset=""
codex_7d_pct=""; codex_7d_reset=""
codex_fresh=""; codex_fresh_color="$DIM"
has_codex=0

codex_session=$(ls -t ~/.codex/sessions/*/*/*/rollout-*.jsonl 2>/dev/null | head -1)
if [ -n "$codex_session" ] && [ -f "$codex_session" ]; then
    codex_mtime=$(stat -c %Y "$codex_session" 2>/dev/null || stat -f %m "$codex_session" 2>/dev/null || echo 0)
    codex_age=$(( $(date +%s) - codex_mtime ))
    # Старше 14 дней — данные неактуальны
    if [ "$codex_age" -lt 1209600 ]; then
        _prim=$(grep -o '"primary":{[^}]*}' "$codex_session" 2>/dev/null | tail -1)
        _sec=$(grep -o '"secondary":{[^}]*}' "$codex_session" 2>/dev/null | tail -1)
        if [ -n "$_prim" ]; then
            codex_5h_pct=$(printf '%s' "$_prim" | grep -oE '"used_percent":[0-9.]+' | cut -d: -f2)
            codex_5h_reset=$(printf '%s' "$_prim" | grep -oE '"resets_at":[0-9]+' | cut -d: -f2)
        fi
        if [ -n "$_sec" ]; then
            codex_7d_pct=$(printf '%s' "$_sec" | grep -oE '"used_percent":[0-9.]+' | cut -d: -f2)
            codex_7d_reset=$(printf '%s' "$_sec" | grep -oE '"resets_at":[0-9]+' | cut -d: -f2)
        fi
        if [ "$codex_age" -lt 1800 ]; then
            codex_fresh="live"; codex_fresh_color="$GREEN"
        elif [ "$codex_age" -lt 86400 ]; then
            codex_fresh="$(( codex_age / 3600 ))h ago"
        else
            codex_fresh="$(( codex_age / 86400 ))d ago"
        fi
        [ -n "$codex_5h_pct" ] && has_codex=1
    fi
fi

codex_5h_pct_int=$(LC_ALL=C printf '%.0f' "${codex_5h_pct:-0}")
codex_7d_pct_int=$(LC_ALL=C printf '%.0f' "${codex_7d_pct:-0}")
codex_5h_delta=$(fmt_delta "$codex_5h_reset")
codex_7d_delta=$(fmt_delta "$codex_7d_reset")

# Pace по недельному окну для обоих провайдеров
claude_pace=$(pace_coef "$seven_d_pct" "$seven_d_resets")
codex_pace=$(pace_coef  "$codex_7d_pct" "$codex_7d_reset")

# === Печать ===================================================================
#
# Колонки строк 3-4 (фиксированные позиции, чтобы Codex-колонка начиналась
# в одном месте независимо от длины claude-дельты):
#   label  bar(20)  pct(4)  delta(8)   │   bar(20)  pct(4)  delta(8)   freshness
#   ^9 ch  ^cells   ^chars  ^chars         ^cells   ^chars  ^chars
#
# context-строка использует более широкий бар (24 ячейки) — там одна колонка.

# --- Row 1: context + модель + effort ----------------------------------------
ctx_pct_disp="${ctx_pct_int}%"
printf "${WHITE}context:${RESET} "
bar "$ctx_pct_int" 20
printf "  ${CYAN}%-4s${RESET}" "$ctx_pct_disp"
# Предупреждение: ⚠ жёлтым при >=70% или >=300k токенов,
# красным при >=80% или >=400k токенов. Пастельные тона.
_ctx_tokens_int="${ctx_used_tokens:-0}"
_warn=""
if [ "$ctx_pct_int" -ge 80 ] || [ "$_ctx_tokens_int" -ge 400000 ]; then
    _warn="${RED}⚠${RESET}"
elif [ "$ctx_pct_int" -ge 70 ] || [ "$_ctx_tokens_int" -ge 300000 ]; then
    _warn="${YELLOW}⚠${RESET}"
fi
if [ -n "$_warn" ]; then
    printf "  %b" "$_warn"
fi
printf "  ${BLUE}%s${RESET}" "$model_label"
if [ -n "$effort_label" ]; then
    printf " ${DIM}·${RESET} ${GREEN}%s${RESET}" "$effort_label"
fi
if [ -n "$git_line" ]; then
    printf "  ${DIM}·${RESET} %b" "$git_line"
fi
printf "\n"

# --- Row 2: «пустая» строка-разрыв ------------------------------------------
# Чистый \n часто схлопывается рендером статус-лайна. Печатаем Braille-blank
# (U+2800) — это печатный символ, но в большинстве шрифтов рисуется как пробел,
# поэтому глаз не цепляется, а строка не пропадает.
printf "⠀\n"

# --- Row 4: имена + pace одной строкой, выровненной с бар-колонками ---------
# Кластер "Claude [×0.8]" стартует в позиции 9 (старт claude-бара).
# Кластер "Codex [×0.3]"  стартует в позиции 50 (старт codex-бара).
# Свежесть Codex идёт хвостом после его кластера.

# Собираем plain-строку Claude-кластера для расчёта padding
_claude_cluster_plain="Claude"
if [ -n "$claude_pace" ]; then
    _claude_cluster_plain="Claude   [x${claude_pace}]"
fi
# ${#var} в UTF-8 локали считает code-points (символы), не байты.
# Используем ASCII 'x' вместо '×' (U+00D7), т.к. у × East Asian Width=Ambiguous —
# в Claude Code TUI он рендерится как 2 ячейки, ломая выравнивание с барами ниже.
_claude_cluster_len=${#_claude_cluster_plain}

# Печать Claude-кластера
printf "         ${WHITE}Claude${RESET}"
if [ -n "$claude_pace" ]; then
    _cp_color=$(pace_color "$claude_pace")
    printf "   ${DIM}[${RESET}%bx%s${RESET}${DIM}]${RESET}" "$_cp_color" "$claude_pace"
fi

if [ "$has_codex" -eq 1 ]; then
    # Padding от текущей позиции (9 + _claude_cluster_len) до позиции 45,
    # затем "  │  " (5 символов) выводит курсор на позицию 50 = старт codex-бара.
    # +7 компенсация визуального дрейфа Claude Code TUI (рендер ANSI/блоков)
    _pad=$(( 45 - _claude_cluster_len ))
    [ "$_pad" -gt 0 ] && printf '%*s' "$_pad" ""
    printf "  ${DIM}│${RESET}  ${WHITE}Codex${RESET}"
    if [ -n "$codex_pace" ]; then
        _xp_color=$(pace_color "$codex_pace")
        printf "   ${DIM}[${RESET}%bx%s${RESET}${DIM}]${RESET}" "$_xp_color" "$codex_pace"
    fi
    if [ -n "$codex_fresh" ]; then
        # Выравниваем freshness под колонки 5h/7d справа:
        #   "23h" — над процентом (col 72), "ago" — над delta (col 78).
        # Codex-кластер стартует в col 50, его длина = _codex_cluster_len.
        _codex_cluster_plain="Codex"
        [ -n "$codex_pace" ] && _codex_cluster_plain="Codex   [x${codex_pace}]"
        _codex_cluster_len=${#_codex_cluster_plain}
        # Разрезаем "23h ago" по пробелу: первое слово → col 72, второе → col 78
        _fresh_num="${codex_fresh%% *}"
        _fresh_word="${codex_fresh#* }"
        [ "$_fresh_num" = "$codex_fresh" ] && _fresh_word=""
        _gap1=$(( 72 - 50 - _codex_cluster_len ))
        [ "$_gap1" -gt 0 ] && printf '%*s' "$_gap1" ""
        printf "%b%s${RESET}" "$codex_fresh_color" "$_fresh_num"
        if [ -n "$_fresh_word" ]; then
            _gap2=$(( 78 - 72 - ${#_fresh_num} ))
            [ "$_gap2" -gt 0 ] && printf '%*s' "$_gap2" ""
            printf "%b%s${RESET}" "$codex_fresh_color" "$_fresh_word"
        fi
    fi
fi
printf "\n"

# --- Helper: одна строка лимит-окна с двумя колонками ------------------------
# args: label  claude_pct  claude_delta  codex_pct  codex_delta  bar_style
print_limit_row() {
    local label="$1"
    local cl_pct="$2" cl_delta="$3"
    local cx_pct="$4" cx_delta="$5"
    local style="${6:-bottom}"

    printf "${WHITE}"
    pad_to 8 "$label"
    printf "${RESET} "
    bar "$cl_pct" 20 "$style"
    printf "  ${CYAN}%-4s${RESET}  ${GRAY}" "${cl_pct}%"
    pad_to 8 "$cl_delta"
    printf "${RESET}"

    if [ "$has_codex" -eq 1 ]; then
        printf "  ${DIM}│${RESET}  "
        bar "$cx_pct" 20 "$style"
        printf "  ${CYAN}%-4s${RESET}  ${GRAY}" "${cx_pct}%"
        pad_to 8 "$cx_delta"
        printf "${RESET}"
    fi
    printf "\n"
}

# --- Row 5: 5h ---------------------------------------------------------------
print_limit_row "5h:" \
    "$five_h_pct_int" "$five_h_delta" \
    "$codex_5h_pct_int" "$codex_5h_delta"

# --- Row 6: 7d ---------------------------------------------------------------
print_limit_row "7d:" \
    "$seven_d_pct_int" "$seven_d_delta" \
    "$codex_7d_pct_int" "$codex_7d_delta"
