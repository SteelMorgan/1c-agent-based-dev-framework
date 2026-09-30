#!/usr/bin/env bash
# consilium-watch-herdr.sh <session_id> [--interval N] — раскладка панелей herdr
# для watch (CONS-05).
#
# Создаёт справа широкую панель общего статуса (`watch` без --participant) и по
# панели на каждого участника (`watch --participant <pid>`, поток ходов). Скрипт
# НЕ закрывает чужие панели и НЕ меняет фокус пользователя; панели завершаются
# сами, когда watch видит терминальное состояние сессии (verdict/closed/удаление).
#
# Запускать из корня репозитория внутри herdr-панели (HERDR_ENV=1).
set -euo pipefail

MAX_PANES=8  # F-007: потолок панелей раскладки (статус + участники)

usage() {
    echo "usage: $0 <session_id> [--interval N]" >&2
    exit 2
}

SESSION_ID="${1:-}"
[[ -n "$SESSION_ID" ]] || usage

# F-005: строгий разбор опций — только [--interval N], N — положительное число.
INTERVAL="3.0"
if [[ $# -ge 2 ]]; then
    if [[ "$2" != "--interval" || $# -lt 3 ]]; then
        usage
    fi
    if ! [[ "$3" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
        echo "ОШИБКА: --interval должен быть положительным числом секунд, получено: $3" >&2
        exit 2
    fi
    INTERVAL="$3"
fi

if [[ "${HERDR_ENV:-}" != "1" ]]; then
    echo "ОШИБКА: не в herdr-окружении (HERDR_ENV != 1); раскладка панелей невозможна" >&2
    exit 1
fi
if ! command -v herdr >/dev/null 2>&1; then
    echo "ОШИБКА: herdr не найден в PATH" >&2
    exit 1
fi

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONSILIUM="$SKILL_DIR/consilium.py"
SESSION_DIR=".consilium-sessions/$SESSION_ID"
if [[ ! -d "$SESSION_DIR" ]]; then
    echo "ОШИБКА: неизвестная сессия: $SESSION_ID (нет каталога $SESSION_DIR)" >&2
    exit 1
fi

# Состав сессии — из session.json (watch read-only, helper его только читает).
mapfile -t PARTICIPANTS < <(python3 - "$SESSION_DIR/session.json" <<'PY'
import json
import sys

session = json.load(open(sys.argv[1], encoding="utf-8"))
for participant in session.get("participants") or []:
    print(participant["id"])
PY
)
if [[ ${#PARTICIPANTS[@]} -eq 0 ]]; then
    echo "ОШИБКА: в session.json нет участников" >&2
    exit 1
fi

if (( ${#PARTICIPANTS[@]} + 1 > MAX_PANES )); then
    # F-007: раскладка больше потолка бессмысленна (нечитаемые панели) — показываем
    # первых MAX_PANES-1 участников и явно сообщаем оператору об усечении.
    echo "ПРЕДУПРЕЖДЕНИЕ: участников ${#PARTICIPANTS[@]}, потолок панелей $MAX_PANES — " \
         "раскладка усечена, остальных смотрите в панели общего статуса" >&2
    PARTICIPANTS=("${PARTICIPANTS[@]:0:$((MAX_PANES - 1))}")
fi

pane_id_from_json() {
    python3 -c 'import json,sys; print(json.load(sys.stdin)["result"]["pane"]["pane_id"])'
}

split_pane() {
    # F-007: отказ split не роняет скрипт на середине (set -e): возвращает rc,
    # вызывающий решает; оператору — явное сообщение о неполной раскладке.
    herdr pane split "$@" --cwd "$PWD" | pane_id_from_json
}

run_watch() {
    # $1 — pane_id, далее — аргументы watch; python -u (F-008) + printf %q по всем
    # подстановкам (F-005): никакой интерполяции в шелл-команду без экранирования.
    local pane="$1"; shift
    local cmd="python3 -u $(printf %q "$CONSILIUM") watch $(printf %q "$SESSION_ID")"
    local arg
    for arg in "$@"; do
        cmd+=" $(printf %q "$arg")"
    done
    herdr pane run "$pane" "$cmd"
}

# Широкая панель вправо — общий статус сессии.
if ! STATUS_PANE="$(split_pane --current --direction right)"; then
    echo "ОШИБКА: split панели статуса не удался — раскладка не создана" >&2
    exit 1
fi
run_watch "$STATUS_PANE" --interval "$INTERVAL" || \
    echo "ПРЕДУПРЕЖДЕНИЕ: запуск watch в панели статуса не удался" >&2
echo "панель общего статуса: $STATUS_PANE"

# Узкие/высокие панели вниз — по одному участнику на панель; следующая панель
# отщепляется от предыдущей участниковой, чтобы колонка не дробила статус.
ANCHOR="$STATUS_PANE"
CREATED=1
for pid in "${PARTICIPANTS[@]}"; do
    if ! PANE="$(split_pane "$ANCHOR" --direction down)"; then
        echo "ПРЕДУПРЕЖДЕНИЕ: split панели для $pid не удался — раскладка неполная " \
             "(создано $CREATED панелей); участника смотрите в панели общего статуса" >&2
        continue
    fi
    run_watch "$PANE" --participant "$pid" --interval "$INTERVAL" || \
        echo "ПРЕДУПРЕЖДЕНИЕ: запуск watch для $pid не удался" >&2
    echo "панель участника $pid: $PANE"
    ANCHOR="$PANE"
    CREATED=$((CREATED + 1))
done

echo "раскладка watch готова: $CREATED панелей из запрошенных $(( ${#PARTICIPANTS[@]} + 1 )); фокус пользователя не менялся"
