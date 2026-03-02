---
name: gui-control
description: Control of the 1С GUI via X11. The skill teaches an agent to detect 1С windows (including error dialogs), take screenshots, and simulate input (Enter, Escape) to manage the interface without human intervention.
---

# 1С GUI Control via X11

## Purpose

The skill teaches an agent to **control the 1С graphical interface** in environments that use a virtual X11 display (Xvfb). It is used when standard tools (ЖР, ТЖ) are unavailable or insufficient — for example, when the database "hangs" on an error dialog and cannot exit cleanly.

**Stack:**
```
Xvfb :99 (virtual framebuffer)
    ├── python-xlib   → reads window metadata (titles, geometry), simulates input
    └── PIL ImageGrab → reads framebuffer pixels → screenshot
```

**Principle:** X11 control is an action, not diagnostics. Use it only when a GUI dialog that blocks the normal termination of the database is detected. Analyze the root causes through ЖР (`event-log-analysis`).

---

## When to apply

| Trigger | Action |
|---------|--------|
| Database is running, but there are no events in ЖР after `test_start_time` | Check whether a GUI dialog is stuck |
| Window title contains “Ошибка” / “Предупреждение” | Take a screenshot → close the dialog → analyze ЖР |
| Database does not exit after running tests | Close the database naturally with Escape + Enter |
| Need to capture the screen state for the log | Capture a screenshot of the 1С window |

---

## Environment setup

```python
import os
os.environ['DISPLAY'] = ':99'
```

The variable must be set **before** importing `Xlib` and `PIL`.

---

## Usage Scenarios

### Scenario 1: Detect a 1С error dialog

**Goal:** verify whether the GUI is stuck on an error dialog without reading ЖР.

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

**Interpretation:**

| Result | Conclusion |
|--------|------------|
| `error_windows` is empty, other 1С windows exist | Database is operating normally |
| `error_windows` is empty, no 1С windows at all | Database has finished (either successfully or crashed before GUI) |
| `error_windows` is not empty | An error dialog is blocking execution → proceed to Scenario 2 |

---

### Scenario 2: Close the error dialog and exit the database

**Goal:** close the database "naturally" after detecting an error dialog so that ЖР can be analyzed later.

**Key sequence:**
1. `Enter` — close the error dialog.
2. `Escape` — initiate application shutdown.
3. `Enter` — confirm exit.

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

send_key(d, ENTER)   # close the error dialog
time.sleep(1)
send_key(d, ESCAPE)  # initiate shutdown
time.sleep(1)
send_key(d, ENTER)   # confirm shutdown
```

**Important:** after execution wait 2–3 seconds and verify via Scenario 1 that no 1С windows remain.

---

### Scenario 3: Screenshot for the log

**Goal:** record the screen state before closing the error dialog.

**When needed:** only for logging or manual debugging. In an automated pipeline — optional, before Step 2.

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
        img = ImageGrab.grab(bbox=(
            geom.x, geom.y,
            geom.x + geom.width,
            geom.y + geom.height
        ))
        path = f'/tmp/onec_{win.id}.png'
        img.save(path)
        print(f'Скриншот сохранён: {path}')
```

---

## Typical pipeline: tests finished, database not closed

```
1. search_event_log(from=test_start_time, limit=20)
       │
       ├── there are events, no Error → database is running, wait
       ├── there is an Error            → screenshot (Scenario 3) → close (Scenario 2) → analyze ЖР
       └── no events                   → detect windows (Scenario 1)
                                          │
                                          ├── window with an error → screenshot → close
                                          └── no windows            → database did not start
```

---

## Safety

| Rule | Description |
|------|-------------|
| **Local only** | X11 control works only in an environment with Xvfb. Do not apply it on production servers with a real display. |
| **Enter/Escape are not arbitrary input** | Simulate only navigation keys. Do not type data into form fields. |
| **Screenshots go to /tmp/** | Do not store them in source control. Screenshots may contain personal data from 1С forms. |

---

## Common errors

| Error | Workaround |
|-------|------------|
| `DISPLAY` is not set | `os.environ['DISPLAY'] = ':99'` before imports |
| `python-xlib` is not installed | `pip install python-xlib` |
| `PIL.ImageGrab` does not work on Linux | Use `Pillow` + `python3-xlib`: `pip install Pillow` |
| 1С windows are not found, but the process exists | The process started, but the GUI is not rendered yet — wait 2–3 seconds and try again |
| XTEST extension is unavailable | Check that Xvfb is started with the `-extensions XTEST` flag |

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `python-xlib` | Read window metadata, simulate input |
| `PIL ImageGrab` | Capture the framebuffer or a specific window |

---

## Related skills

| Skill | When to use |
|-------|------------|
| [`event-log-analysis`](../event-log-analysis/SKILL.md) | Analyze the cause of an error after closing the dialog |
