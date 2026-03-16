---
name: gui-control
description: GUI control for 1C via X11. The skill teaches the agent to detect 1C windows (including error dialogs), capture screenshots, and simulate input (Enter, Escape) to manage the interface without human involvement.
---

# GUI Control for 1C via X11

## Purpose

The skill teaches the agent to **control the 1C graphical interface** in environments with a virtual X11 display (Xvfb). It is used when the standard tools (event log, tech log) are unavailable or insufficient — for example, when the infobase is "hung" on an error dialog and cannot shut down properly.

**Stack:**
```
Xvfb :99 (virtual framebuffer)
    ├── python-xlib   → reads window metadata (titles, geometry), simulates input
    └── PIL ImageGrab → reads framebuffer pixels → screenshot
```

**Principle:** X11 control is an action, not diagnostics. Use it only when a GUI dialog blocking a proper shutdown has been detected. Diagnose the cause via the event log (`event-log-analysis`).

For a `Security warning` in 1C the X11 window metadata may be incomplete or misleading. In this case rely on the combination:

1. the entry in the event log;
2. the real screen via noVNC or a screenshot;
3. only then perform keyboard/window actions.

---

## When to apply

| Trigger | Action |
|---------|--------|
| The infobase is running, but there are no events in the event log after `test_start_time` | Check whether a GUI dialog is stuck |
| A window title contains "Ошибка" / "Предупреждение" | Capture a screenshot → close the dialog → analyze the event log |
| The infobase does not terminate after the tests run | Shut down the infobase gracefully via Escape + Enter |
| Need to record the screen state for a log | Capture a screenshot of the 1C window |
| The event log has a `Предупреждение безопасности` on an EPF | Treat this as a trigger for visual inspection rather than acting blindly on window titles |

---

## Environment setup

```python
import os
os.environ['DISPLAY'] = ':99'
```

The variable must be set **before** importing `Xlib` and `PIL`.

---

## Usage scenarios

### Scenario 1: Detect a 1C error dialog

**Goal:** verify that the GUI is not hung on an error dialog without reading the event log.

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
| `error_windows` is empty, there are other 1C windows | The infobase is running normally |
| `error_windows` is empty, there are no 1C windows at all | The infobase finished (either successfully or crashed before the GUI appeared) |
| `error_windows` is not empty | An error dialog is blocking execution → proceed to Scenario 2 |

---

### Scenario 2: Close the error dialog and finish the infobase

**Goal:** close the infobase "naturally" after detecting an error dialog so the event log can be analyzed afterward.

**Keystroke sequence:**
1. `Enter` — close the error dialog.
2. `Escape` — initiate application shutdown.
3. `Enter` — confirm the shutdown.

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

send_key(d, ENTER)   # закрыть диалог ошибки
time.sleep(1)
send_key(d, ESCAPE)  # инициировать закрытие
time.sleep(1)
send_key(d, ENTER)   # подтвердить закрытие
```

**Important:** after executing wait 2–3 seconds and verify via Scenario 1 that no 1C windows remain.

---

### Scenario 3: Screenshot for the log

**Goal:** capture the screen state before closing the error dialog.

**When needed:** only for logging or manual debugging. In automated pipelines it is optional, usually before Step 2.

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

## Typical pipeline: tests finished but the infobase did not close

```
1. search_event_log(from=test_start_time, limit=20)
       │
       ├── there are events, no Error → infobase is working, wait
       ├── there is an Error       → screenshot (Scenario 3) → close (Scenario 2) → analyze the event log
       └── no events              → detect windows (Scenario 1)
                                          │
                                          ├── error window → screenshot → close
                                          └── no windows    → the infobase did not start
```

---

## Safety

| Rule | Description |
|------|-------------|
| **Locally only** | X11 control only works in an Xvfb environment. Do not apply it on production servers with a real display. |
| **Enter/Escape — not arbitrary input** | Simulate only navigation keys. Do not input data into form fields. |
| **Screenshots go into /tmp/** | Do not save them in the repository. Screenshots may contain personal data from 1C forms. |

---

## Common mistakes

| Mistake | Workaround |
|--------|------------|
| `DISPLAY` is not set | Set `os.environ['DISPLAY'] = ':99'` before the imports |
| `python-xlib` is not installed | Run `pip install python-xlib` |
| `PIL.ImageGrab` does not work on Linux | Use `Pillow` + `python3-xlib`: `pip install Pillow` |
| 1C windows are not found, but the process exists | The process started but the GUI has not been rendered yet — wait 2–3 seconds and retry |
| XTEST extension is unavailable | Ensure Xvfb is started with the `-extensions XTEST` flag |

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `python-xlib` | Reading window metadata, input simulation |
| `PIL ImageGrab` | Screenshot of the framebuffer or a single window |
