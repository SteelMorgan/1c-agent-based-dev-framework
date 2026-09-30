---
name: va-visual-check
description: "Vanessa/VA MCP: visual checking of 1C forms and screenshots"
---

# VA Visual Check

Use this skill for visual validation of 1C forms through Vanessa Automation / TestClient and VA MCP. This is the dedicated route for UI/UX screenshots of managed 1C forms.

## Main Route

1. If the VA MCP manager session is not up yet, start it strictly according to the `v8-runner` skill; here check only the live session `kind=vanessa_test_client` in `session_list`.
2. After starting VA/the client, let the process start up normally: 10-20 seconds before the first check is a normal interval. If the live session is already visible but the VA tools have not yet appeared in the catalog, wait for the tools to register with periodic checks for up to 120 seconds; proceed as soon as the required tools appear.
3. Connect the test client through `connect_test_client` with the profile from the VA settings; do not guess the profile name.
4. Make sure a real test-client is connected. Do not rely only on the `connect_test_client` text response: verify the selected VAParams profile, the VA log/state, a live `/TESTCLIENT -TPort ...` process, or `get_window_list_os`. If the PID is `0`/empty, first check the active `tests.va.params_path`, the profile row in `ДанныеКлиентовТестирования`, busy/stale ports, and old TestClient processes.
5. Open the required form through VA/TestClient tools.
6. Get the structured state of the form (`get_form_analysis`, `get_window_list_testclient`, reading elements/tables).
7. Get the list of OS windows through `get_window_list_os`.
8. Critical: perform screenshot operations through VA MCP strictly synchronously. Do not launch several `get_window_screenshot_os` in parallel and do not use `multi_tool_use.parallel` for them: send one request, wait for the full response, and make sure through `session_list` that the session is alive and `inflight=0`; only then send the next request.
9. Take a PNG through `get_window_screenshot_os` by the OS window title returned by `get_window_list_os`. In the current VA MCP contract, `connect_test_client(profileName)` selects the active TestClient, and `window_title` selects one OS window from the window list of that connected TestClient process:

```text
get_window_screenshot_os {
  "window_title": "<title from get_window_list_os>",
  "file_name": "<путь>.png",
  "color_mode": "color"
}
```

Use `window_id`/PID from `get_window_list_os` only for technical debugging and to prove that the OS window belongs to the TestClient. Do not choose a window by global title search outside VA: the VA manager and TestClient can have identical titles. If the PNG is black, single-color, or the VA manager disconnects during screenshot, first prove that the current VanessaExt build is installed and that the call is made through the connected TestClient route; only then continue with the Linux/Xvfb diagnostics below.

10. Check the PNG: the file is created, the size is as expected, the image is not empty, not single-color, and not black.

## MCP research vs scenario run-loop

Do not mix two different VA execution schemes in one verification loop:

- **MCP research / interactive form inspection**: a test-client is already running and connected to the VA MCP manager. Use `connect_test_client`, `execute_feature_step`, `get_form_analysis`, `manage_form_elements`, screenshots, and table-document save tools against that live client.
- **Scenario run-loop**: VA executes a feature through `run_scenario` / `v8-runner test va`, and steps such as `Я подключаю клиент тестирования с параметрами` create/manage their own TestClient session.

These schemes do not reliably share client state, PID/profile binding, modal windows, or ports. If MCP research is in progress, do not insert `Я подключаю клиент тестирования...` into the same feature to "restart" the client; it can leave VA with PID `0`, stale modal state, or an unconnected client. If a fresh client is required for MCP research, restart it by the same MCP-research recipe/profile and then call `connect_test_client` again. If a full scenario run is required, switch to the `vanessa-run-loop` workflow and treat it as a separate run with its own artifacts.

## Linux headless X11/Xvfb without a window manager

Current VanessaExt is expected to handle Linux virtual X11/Xvfb without a graphical environment/window manager inside `get_window_list_os` and `get_window_screenshot_os(window_title=...)`. Do not add manual window-selection parameters, a PID argument, a `window_id` argument, or any external window-preparation step to the normal screenshot route.

The normal route is still:

```text
connect_test_client { "profileName": "<profile from VAParams>" }
get_window_list_os {}
get_window_screenshot_os {
  "window_title": "<title from get_window_list_os>",
  "file_name": "<путь>.png",
  "color_mode": "color"
}
```

If the PNG is black/monochrome in no-WM Xvfb, treat it as a broken route, stale component, wrong profile/port binding, or wrong window selection until disproven. Check:

- the installed component in the 1C user cache matches the intended VanessaExt build;
- the X11 root really has no window manager/client-list properties (`_NET_SUPPORTING_WM_CHECK`, `_NET_CLIENT_LIST`, `_WIN_CLIENT_LIST`);
- `get_window_list_os` returns the target TestClient window title after `connect_test_client(profileName)`;
- the selected window is the visible form window, not a hidden `1cv8c` helper window.

Do not run external X11 window-management commands as part of the screenshot procedure. Do not pass manual window-selection parameters to `get_window_screenshot_os`. If the standard route fails after the checks above, fix the component/profile/port/window-selection cause or move to the fallback solution below with the failure recorded.

## Browser fallback

VA MCP is the preferred route for ordinary 1C forms because it works with the real TestClient and gives both the form structure and a visual PNG.

Web/browser fallback is allowed when:

- VA MCP is unavailable or does not pass readiness;
- the connected TestClient still has PID `0`/empty after checking VAParams profile, ports, stale `/TESTCLIENT` processes, and `get_window_list_os`;
- `get_window_list_os` does not see the required window;
- `get_window_screenshot_os` remains black/monochrome after proving that the current VanessaExt build is installed and the profile/port/window-selection checks do not identify the cause;
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
