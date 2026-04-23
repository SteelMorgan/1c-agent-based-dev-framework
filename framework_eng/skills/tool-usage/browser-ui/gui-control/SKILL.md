---
name: gui-control
description: "Managing 1C GUI via X11. The skill teaches the agent to detect 1C windows (including error dialogs), take screenshots, and simulate input (Enter, Escape) to control the interface without human involvement."
---

# Managing 1C GUI via X11

X11 control is an action, not diagnostics. Use only when a GUI dialog is detected that blocks the normal shutdown of the database. Diagnose the cause through the event log (`event-log-analysis`).

For `Security Warning`, X11 window metadata may be incomplete. Rely on the sequence: event log → screenshot → keyboard actions.

## When to use

| Trigger | Action |
|---------|----------|
| No events in the event log after `test_start_time` | Check whether a GUI dialog is hanging |
| Window title: "Error" / "Warning" | Screenshot → close the dialog → analyze the event log |
| The database does not shut down after tests | Close with Escape + Enter |
| The event log shows `Security Warning` on EPF | Visually verify; do not act blindly based on titles |

## Environment setup

```python
import os
os.environ['DISPLAY'] = ':99'  # before importing Xlib and PIL
```

## Workflow

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

- Empty + there are 1C windows → the database is operating normally
- Empty + no windows → the database has shut down
- Not empty → error dialog → step 2

### 2. Close the dialog and shut down the database

Sequence: Enter (close the dialog) → Escape (close) → Enter (confirm). After that, wait 2-3 seconds and check again using step 1.

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
        print(f'Screenshot saved: {path}')
```

## Pipeline: tests finished, the database did not close

```
search_event_log(from=test_start_time, limit=20)
  ├── there are events, no Error → wait
  ├── there is Error → screenshot → close → analyze the event log
  └── no events → detect windows
        ├── window with an error → screenshot → close
        └── no windows → the database did not start
```

## Safety

- **Xvfb only** — do not use on production servers with a real display
- **Navigation keys only** (Enter/Escape) — do not enter data into fields
- **Screenshots go to /tmp/** — they may contain personal data

## Common mistakes

| Error | Workaround |
|--------|---------------|
| `DISPLAY` is not set | `os.environ['DISPLAY'] = ':99'` before imports |
| `python-xlib` is not installed | `pip install python-xlib` |
| `PIL.ImageGrab` does not work | `pip install Pillow` |
| Windows are not found, but the process exists | The GUI has not been rendered yet - wait 2-3 seconds |
| XTEST is unavailable | Xvfb with the `-extensions XTEST` flag |

## Capabilities

| Capability | Purpose |
|------------|------------|
| `python-xlib` | Reading window metadata, simulating input |
| `PIL ImageGrab` | Screenshot of the framebuffer or a window |

---
depends_on: []
---
