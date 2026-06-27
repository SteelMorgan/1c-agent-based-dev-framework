---
name: va-visual-check
description: "Vanessa/VA MCP: visual checking of 1C forms and screenshots"
---

# VA Visual Check

Use this skill for visual validation of 1C forms through Vanessa Automation / TestClient and VA MCP. This is the dedicated route for UI/UX screenshots of managed 1C forms.

## Main Route

1. If the VA MCP manager session is not up yet, start it strictly according to the `v8-runner` skill; here check only the live session `kind=vanessa_test_client` in `session_list`.
2. Connect the test client through `connect_test_client` with the profile from the VA settings; do not guess the profile name.
3. Make sure a real test-client is connected: the profile/log/state of VA contains a PID, not `0`.
4. Open the required form through VA/TestClient tools.
5. Get the structured state of the form (`get_form_analysis`, `get_window_list_testclient`, reading elements/tables).
6. Get the list of OS windows through `get_window_list_os`.
7. Critical: perform screenshot operations through VA MCP strictly synchronously. Do not launch several `get_window_screenshot_os` in parallel and do not use `multi_tool_use.parallel` for them: send one request, wait for the full response, and make sure through `session_list` that the session is alive and `inflight=0`; only then send the next request.
8. Take a PNG through `get_window_screenshot_os`:

```text
get_window_screenshot_os {
  "window_title": "<точный заголовок окна формы>",
  "file_name": "<путь>.png",
  "color_mode": "color"
}
```

9. Check the PNG: the file is created, the size is as expected, the image is not empty, not single-color, and not black.

## Linux headless X11/Xvfb without a window manager

This recipe applies only to Linux on a virtual X11/Xvfb display without a graphical environment/window manager. It is needed when `get_window_list_os` sees the form window, but `get_window_screenshot_os` returns a black or nearly empty PNG.

X11 commands are used only to expose an already opened window. The preferred screenshot after that is still taken through VA MCP.

1. Find the X11 id of the form window:

```bash
xwininfo -root -tree | sed -n '1,220p'
```

If `wmctrl -l` or other EWMH tools respond with `Cannot get client list properties` / `_NET_CLIENT_LIST or _WIN_CLIENT_LIST`, this is expected for Xvfb without a window manager. Use the `xwininfo` tree, not the window-manager client list.

2. Verify that the found window belongs to the test-client, not the VA manager:

```bash
xprop -id <window_id> _NET_WM_PID WM_NAME WM_CLASS
```

`_NET_WM_PID` must match the PID of the connected test-client. If the PID is not yet fixed, get it from the VA profile/connection state; use the window title only as an additional filter.

3. Move, resize, and raise the window:

```bash
xdotool windowmove <window_id> 0 0 || true
xdotool windowsize <window_id> 1200 800 || true
xdotool windowraise <window_id> || true
xdotool windowactivate --sync <window_id> || true
xwininfo -id <window_id> | sed -n '1,60p'
```

In an environment without a window manager, `windowactivate` may fail with a message about `_NET_ACTIVE_WINDOW`; this is not a blocker if `xwininfo` shows `Map State: IsViewable`.

4. Repeat the standard VA screenshot through `get_window_screenshot_os`.

5. Repeat the PNG check. If the screenshot is still black/monochrome, move to the fallback solution below and explicitly record the reason.

## Browser fallback

VA MCP is the preferred route for ordinary 1C forms because it works with the real TestClient and gives both the form structure and a visual PNG.

Web/browser fallback is allowed when:

- VA MCP is unavailable or does not pass readiness;
- `connect_test_client` does not provide a real PID;
- `get_window_list_os` does not see the required window;
- `get_window_screenshot_os` remains black/monochrome after the Linux/Xvfb recipe;
- the behavior being checked relates to the browser layer: DOM/CSS/HTML, console/network, web-auth/publication, viewport/pixel rendering, browser extension, browser-only upload/download/clipboard.

Before fallback, record:

- which VA capability failed;
- which VA-route steps have already been completed;
- why the browser/web-client will provide enough signal for the current task;
- residual risk: the web-client may differ from the thin/thick 1C client.

For browser fallback, use the relevant browser skills (`web-test-1c`, `playwright`, `screenshot`) for their intended purpose. Do not mix the result: if the artifact was obtained through web/browser fallback, call it that in the report.

## What Not To Do

- Do not replace the VA MCP screenshot with a direct X11/noVNC/OS screenshot without an explicit fallback note.
- Do not choose the window only by title in Xvfb: the VA manager and the test-client can have identical titles.
- Do not treat `get_window_list_testclient` as visual confirmation: it is the structure of internal windows, not a PNG.
- Do not continue based on cached `tools/list`: you need a live session of the required `kind`.

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
  - framework/skills/tool-usage/v8-session-manager/SKILL.md
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
---
