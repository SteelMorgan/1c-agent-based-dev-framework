---
name: gui-control
description: GUI control for 1C via X11. The skill trains the agent to detect 1C windows (including error dialogs), capture screenshots, and simulate input (Enter, Escape) to manage the interface unattended.
---

# GUI Control for 1C via X11

X11 control is an action, not diagnostics. Use it only when a GUI dialog that blocks normal infobase shutdown is detected. Diagnose the cause through the event log (`event-log-analysis`).

For a `Предупреждение безопасности` the X11 window metadata can be incomplete. Rely on the chain: event log → screenshot → keyboard actions.

## When to apply

| Trigger | Action |
|---------|--------|
| No events in the event log after `test_start_time` | Check whether a GUI dialog is stuck |
| Window title: “Ошибка” / “Предупреждение” | Screenshot → close the dialog → analyze the event log |
| The infobase does not terminate after tests | Close using Escape + Enter |
| The event log shows `Предупреждение безопасности` on an EPF | Inspect visually; do not act blindly on titles |

## Environment setup

```python
import os
os.environ['DISPLAY'] = ':99'  # before importing Xlib and PIL
```

## Workflow

### 1. Detect an error dialog

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

- Empty + there are 1C windows → the infobase works normally
- Empty + no windows → the infobase finished
- Non-empty → error dialog detected → proceed to step 2

### 2. Close the dialog and finish the infobase

Sequence: Enter (close the dialog) → Escape (initiate shutdown) → Enter (confirm). After that wait 2–3 seconds and re-check following step 1.

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

### 3. Screenshot for the log (optional, before step 2)

```python
import os
os.environ['DISPLAY'] = ':99'
from PIL import ImageGrab
from Xlib import display

d = display.Display()
root = d.screen().root

for win in root.query_tree().children:
    name = win.get_wm_name()
    wm_class = win.get_wm_class()
    if wm_class and '1cv8' in wm_class:
        geom = win.get_geometry()
        img = ImageGrab.grab(bbox=(geom.x, geom.y, geom.x + geom.width, geom.y + geom.height))
        path = f'/tmp/onec_{win.id}.png'
        img.save(path)
        print(f'Скриншот сохранён: {path}')
```

## Pipeline: tests finished but the infobase did not close

```
search_event_log(from=test_start_time, limit=20)
  ├── there are events without Error → wait
  ├── there is an Error → screenshot → close → analyze the event log
  └── no events → detect windows
        ├── error window → screenshot → close
        └── no windows → the infobase did not start
```

## Safety

- **Only Xvfb** — do not apply on production servers with a real display
- **Only navigation keys** (Enter/Escape) — do not enter data into fields
- **Screenshots go to /tmp/** — they may contain personal data

## Typical mistakes

| Mistake | Workaround |
|--------|-----------|
| `DISPLAY` is not set | Set `os.environ['DISPLAY'] = ':99'` before the imports |
| `python-xlib` is not installed | `pip install python-xlib` |
| `PIL.ImageGrab` does not work | `pip install Pillow` |
| Windows are not found, but the process exists | The GUI is not rendered yet — wait 2–3 seconds |
| XTEST is unavailable | Run Xvfb with the `-extensions XTEST` flag |

## Capabilities

| Capability | Purpose |
|------------|---------|
| `python-xlib` | Read window metadata, simulate input |
| `PIL ImageGrab` | Screenshot framebuffer or window |

---
depends_on: []
---
