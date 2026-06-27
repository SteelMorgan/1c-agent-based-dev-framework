---
name: gui-control
description: "Unlocking frozen 1С windows, dialogs, and tests"
---

# Controlling 1С GUI through X11

X11 control is an action, not diagnosis. Use only when a GUI dialog has been detected that blocks normal database shutdown. Diagnose the causes through the event log (`event-log-analysis`).

For UI/UX acceptance of ordinary 1C forms, do not use `gui-control` as the primary route. First apply `va-visual-check`; X11 keys and direct GUI control are allowed only as a fallback/action, with the reason and residual risk recorded.

For `Security warning`, X11 window metadata may be incomplete. Rely on the chain: event log → visual artifact via `va-visual-check` → keyboard action if needed.

## When to apply

| Trigger | Action |
|---------|----------|
| The event log has no events after `test_start_time` | Check whether a GUI dialog is frozen |
| Window title: «Error» / «Warning» | VA MCP screenshot → close the dialog only if VA MCP fundamentally cannot perform the required action → event log analysis |
| The database does not terminate after tests | Close it via Escape + Enter only if VA MCP fundamentally cannot close the blocking window |
| The event log contains `Security warning` for EPF | Visual inspection, do not act blindly based on titles |

## Environment setup

```python
import os
os.environ['DISPLAY'] = ':99'  # до импортов Xlib и PIL
```

## Working algorithm

### 1. Detect the error dialog

```python
import os
os.environ['DISPLAY'] = ':99'
from Xlib import display

d = display.Display()
root = d.screen().root

error_windows = []
for win in root.query_tree().children:
    name = win.get_wm_name()
    wm_class = win.get_wm_class()
    if wm_class and '1cv8' in wm_class:
        if name and any(kw in name for kw in ['Ошибка', 'Предупреждение', 'Error']):
            error_windows.append({'id': win.id, 'name': name})

print(error_windows)
```

- Empty + 1С windows present → the database is working normally
- Empty + no windows → the database terminated
- Non-empty → error dialog → step 2

### 2. Close the dialog and terminate the database

First check whether there is a VA MCP tool to close/confirm the required window. If you use X11 keys as a fallback/action, record the reason. Sequence: Enter (close dialog) → Escape (close) → Enter (confirm). After that, wait 2–3 sec and check via step 1.

```python
import os, time
os.environ['DISPLAY'] = ':99'
from Xlib import display, X
from Xlib.ext.xtest import fake_input

def send_key(d, keycode, delay=0.3):
    fake_input(d, X.KeyPress, keycode)
    d.flush()
    time.sleep(delay)
    fake_input(d, X.KeyRelease, keycode)
    d.flush()
    time.sleep(delay)

d = display.Display()
ENTER  = d.keysym_to_keycode(0xFF0D)
ESCAPE = d.keysym_to_keycode(0xFF1B)

send_key(d, ENTER)
time.sleep(1)
send_key(d, ESCAPE)
time.sleep(1)
send_key(d, ENTER)
```

### 3. Screenshot for the log (required via VA MCP, before step 2)

Take the screenshot for the 1C UI via `va-visual-check`: VA MCP PNG, Linux/Xvfb recipe, and fallback rules.

```json
{"name":"get_window_screenshot_os","arguments":{"window_title":"<title-from-get_window_list_os>","file_name":"<path>.png","color_mode":"color"}}
```

## Pipeline: tests finished, the database did not close

```
search_event_log(from=test_start_time, limit=20)
  ├── there are events, no Error → wait
  ├── there is Error → VA MCP screenshot → close only if the required VA capability is unavailable → event log analysis
  └── no events → detect windows
        ├── error window → VA MCP screenshot → close only if the required VA capability is unavailable
        └── no windows → the database did not start
```

## Safety

- **Only Xvfb** — do not use on production servers with a real display
- **Only navigation keys** (Enter/Escape) — do not enter data into fields
- **VA MCP screenshots are in /tmp/** — may contain personal data

## Typical errors

| Error | Workaround |
|--------|---------------|
| `DISPLAY` not set | `os.environ['DISPLAY'] = ':99'` before imports |
| `python-xlib` not installed | `pip install python-xlib` |
| Windows not found, but process exists | GUI has not been rendered yet — wait 2–3 sec |
| VA MCP screenshot of Xvfb is black/single-color | Act according to `va-visual-check`: Linux/Xvfb recipe, repeat the VA capture, then fallback if necessary |
| XTEST unavailable | Xvfb with `-extensions XTEST` flag |

## Capabilities

| Capability | Purpose |
|------------|------------|
| `python-xlib` | Reading window metadata, simulating input |
| `get_window_screenshot_os` | VA MCP screenshot of the test-client window |

---
depends_on: []
---
