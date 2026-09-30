#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  expose-testclient-window.sh --pid <PID> [--slot <N>] [--width <W>] [--height <H>]
  expose-testclient-window.sh --port <PORT> [--slot <N>] [--width <W>] [--height <H>]

Expose a 1C /TESTCLIENT X11 window in Linux/Xvfb environments before VA screenshots.
The window is selected by _NET_WM_PID, not by title.
USAGE
}

pid=""
port=""
slot=0
width=1200
height=800
gap=20

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pid)
      pid="${2:-}"
      shift 2
      ;;
    --port)
      port="${2:-}"
      shift 2
      ;;
    --slot)
      slot="${2:-0}"
      shift 2
      ;;
    --width)
      width="${2:-1200}"
      shift 2
      ;;
    --height)
      height="${2:-800}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${DISPLAY:-}" ]]; then
  echo "DISPLAY is not set; cannot expose an X11 window" >&2
  exit 2
fi

if [[ -z "$pid" && -n "$port" ]]; then
  pid="$(ps -eo pid,args | awk -v port="$port" '$0 ~ "/TESTCLIENT" && $0 ~ ("-TPort " port) { print $1; exit }')"
fi

if [[ -z "$pid" ]]; then
  pid="$(ps -eo pid,args | awk '$0 ~ "/TESTCLIENT" { print $1 }' | tail -n 1)"
fi

if [[ -z "$pid" ]]; then
  echo "TestClient PID not found" >&2
  exit 3
fi

if ! kill -0 "$pid" 2>/dev/null; then
  echo "Process $pid is not alive" >&2
  exit 3
fi

window_id=""
while read -r candidate; do
  candidate="${candidate%% *}"
  [[ "$candidate" =~ ^0x[0-9a-fA-F]+$ ]] || continue
  if xprop -id "$candidate" _NET_WM_PID 2>/dev/null | grep -q "= $pid\\b"; then
    window_id="$candidate"
    break
  fi
done < <(xwininfo -root -tree 2>/dev/null | awk '/0x[0-9a-fA-F]+/ { print $1 }')

if [[ -z "$window_id" ]]; then
  echo "X11 window for PID $pid not found" >&2
  exit 4
fi

cols=2
x=$(( (slot % cols) * (width + gap) ))
y=$(( (slot / cols) * (height + gap) ))

xdotool windowmove "$window_id" "$x" "$y" || true
xdotool windowsize "$window_id" "$width" "$height" || true
xdotool windowraise "$window_id" || true
xdotool windowactivate --sync "$window_id" || true

echo "pid=$pid"
echo "window_id=$window_id"
echo "geometry=${width}x${height}+${x}+${y}"
xwininfo -id "$window_id" | sed -n '1,35p'
