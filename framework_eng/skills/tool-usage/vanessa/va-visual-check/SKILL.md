---
name: va-visual-check
description: "Vanessa/VA MCP: visual validation of 1C forms and screenshots"
---

# VA Visual Check

Use this skill for visual validation of 1C forms through Vanessa Automation / TestClient and VA MCP. This is the dedicated path for UI/UX screenshots of managed 1C forms.

## Main Path

1. Start a VA MCP manager session through `v8-runner launch mcp va ...` and wait for a live session with `kind=vanessa_test_client` in `session_list`.
2. Connect the test client through `connect_test_client` with the profile from the VA settings; do not guess the profile name.
3. Make sure a real test client is connected: the VA profile/log/state contains a PID, not `0`.
4. Open the required form through VA/TestClient tools.
5. Obtain the structured form state (`get_form_analysis`, `get_window_list_testclient`, reading elements/tables).
6. Obtain the list of OS windows through `get_window_list_os`.
7. Take a PNG through `get_window_screenshot_os`:

```text
get_window_screenshot_os {
  "window_title": "<точный заголовок окна формы>",
  "file_name": "<путь>.png",
  "color_mode": "color"
}
```

8. Verify the PNG: the file was created, the size is as expected, the image is not empty, not monochrome, and not black.

## Linux headless X11/Xvfb without window-manager

This recipe applies only to Linux on a virtual X11/Xvfb display without a graphical environment/window-manager. It is needed when `get_window_list_os` sees the form window, but `get_window_screenshot_os` returns a black or almost empty PNG.

X11 commands are used only to expose an already open window. The preferred screenshot after that is still taken through VA MCP.

1. Find the X11 id of the form window:

```bash
xwininfo -root -tree | sed -n '1,220p'
```

If `wmctrl -l` or other EWMH tools respond with `Cannot get client list properties` / `_NET_CLIENT_LIST or _WIN_CLIENT_LIST`, that is expected for Xvfb without a window-manager. Use the `xwininfo` tree, not the window-manager client list.

2. Make sure the located window belongs to the test client, not the VA manager:

```bash
xprop -id <window_id> _NET_WM_PID WM_NAME WM_CLASS
```

`_NET_WM_PID` must match the PID of the connected test client. If the PID has not been fixed yet, obtain it from the VA profile/connection state; use the window title only as an additional filter.

3. Move, resize, and raise the window:

```bash
xdotool windowmove <window_id> 0 0 || true
xdotool windowsize <window_id> 1200 800 || true
xdotool windowraise <window_id> || true
xdotool windowactivate --sync <window_id> || true
xwininfo -id <window_id> | sed -n '1,60p'
```

In an environment without a window-manager, `windowactivate` may fail with a message about `_NET_ACTIVE_WINDOW`; this is not a blocker if `xwininfo` shows `Map State: IsViewable`.

4. Repeat the standard VA screenshot through `get_window_screenshot_os`.

5. Repeat the PNG check. If the screenshot is still black/monochrome, move to the fallback solution below and explicitly record the reason.

## Browser fallback

VA MCP is the preferred path for ordinary 1C forms because it works with the real TestClient and provides both the form structure and the visual PNG at the same time.

Web/browser fallback is allowed when:

- VA MCP is unavailable or fails readiness;
- `connect_test_client` does not provide a real PID;
- `get_window_list_os` does not see the required window;
- `get_window_screenshot_os` remains black/monochrome after the Linux/Xvfb recipe;
- the behavior being checked belongs to the browser layer: DOM/CSS/HTML, console/network, web-auth/publication, viewport/pixel rendering, browser extension, browser-only upload/download/clipboard.

Before fallback, record:

- which VA capability did not work;
- which steps of the VA path have already been completed;
- why the browser/web-client will provide sufficient signal for the current task;
- residual risk: the web-client may differ from the thin/thick 1C client.

For browser fallback, use the profile browser skills (`web-test-1c`, `playwright`, `screenshot`) for their intended purpose. Do not mix the result: if the artifact was obtained through web/browser fallback, name it that way in the report.

## What Not To Do

- Do not replace the VA MCP screenshot with a direct X11/noVNC/OS screenshot without an explicit fallback record.
- Do not choose the window by title alone in Xvfb: the VA manager and the test client may have identical titles.
- Do not treat `get_window_list_testclient` as visual confirmation: it is the structure of internal windows, not a PNG.
- Do not continue using a cached `tools/list`: you need a live session of the required `kind`.

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
  - framework/skills/tool-usage/v8-session-manager/SKILL.md
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
---
