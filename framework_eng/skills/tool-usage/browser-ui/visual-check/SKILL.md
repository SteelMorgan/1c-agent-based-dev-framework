---
name: visual-check
description: "1C form UI acceptance: screenshot, console, checklist"
alwaysApply: false
---

# Visual Check of Forms (Visual Check)

By default, visual verification of 1C managed forms is performed through Vanessa/TestClient or the platform test client MCP: open the form, perform a user action, obtain the structured form data (`get_form_analysis`, `get_window_list_testclient`, `get_value`, `get_table_rows`) and compare it with `form-visual-requirements`.

For any work with a client form where layout, visibility, accessibility, or user perception matter, a visual screenshot is mandatory. The screenshot path is chosen as follows:

1. If the short smoke check `connect_test_client` -> `get_window_list_os` -> `get_window_screenshot_os` has actually passed in the current VA MCP environment, use the VA MCP screenshot.
2. If the VA MCP screenshot did not pass or fails on `PID=0` / `Failed to obtain the PID of the testing client process`, use an external OS/noVNC screenshot of the visible 1C window.
3. Use the web client for screenshot only as the browser-specific exception below.

Use the web client only when checking the browser layer that is unavailable to TestClient: DOM/CSS/HTML, JS console/network, viewport/responsive, web publishing and web auth, cookies/storage, browser extensions, browser-only upload/download/clipboard, or a defect reproducible only in the Chrome/Edge web client.

Required for the web exception: the 1C web client URL (published database), credentials, and a short reason why TestClient/VA are insufficient.

## Verification Process

### 1. Navigate to the form

If the target is not browser-based, stop and switch to the TestClient/VA path (`vanessa-authoring`, `v8-runner`). The following steps apply only to the web exception.

Prefer Deep Linking - it is faster than navigating through the interface.

- List: `<base_url>/e1cib/list/<MetadataType>.<Name>`
- New object: `<base_url>/e1cib/data/<MetadataType>.<Name>?ref=00000000-0000-0000-0000-000000000000`
- Existing object: `<base_url>/e1cib/data/<MetadataType>.<Name>?ref=<UUID>`

### 2. Authentication (if redirected to sign-in)

`browser_snapshot` → `browser_fill` (login/password from ref) → `browser_click` (Log in).

### 3. Screenshot and Console

After loading (wait for the indicator to disappear):
1. `browser_take_screenshot`
2. `browser_console_messages` — look for "Error", "Exception", "Uncaught"

### 4. Analysis against `form-visual-requirements`

- Layout and alignment (grouping, padding, width)
- Controls and labels (labels, truncation, headings, command bar)
- Usability (tab order, key fields, tables, horizontal scrolling)
- Object-type specifics (directories, documents, data processors)

**Report:** screenshot analysis result + presence/absence of JS errors.

## Capabilities

| Capability | Purpose |
|------------|---------|
| `browser_navigate` | Open the form URL |
| `browser_snapshot` | Page structure and element refs |
| `browser_fill` | Fill in fields |
| `browser_click` | Click elements |
| `browser_take_screenshot` | Capture the form |
| `browser_console_messages` | Check for JS errors |
| `browser_wait_for` | Wait for loading |

## Typical Issues

| Error | Workaround |
|--------|------------|
| Blank screenshot | `browser_wait_for` before the screenshot |
| Deep Link does not work for a new object | List → "Create" via `browser_click` |
| `browser_fill` cannot find the field | `browser_snapshot` for current refs |
| JS errors on a normal form | Record it - they will surface on save |

---
depends_on:
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
---
