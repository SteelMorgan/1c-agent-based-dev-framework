---
name: web-test-1c
description: "Browser UI tests for 1C: forms, tables, reports"
---

# web-test-1c — Automation for the 1C web client

A semantic layer on top of Playwright for the DOM of the 1C:Enterprise web client.

## Scope

`web-test-1c` is NOT the default route for checking 1C UI. If the task is to open a form/list, click a command, fill fields, check visibility/accessibility, client handler reactions, tabular section rows, a user business flow, or visually accept a form, first apply the dedicated `va-visual-check` skill.

The web client is allowed as a browser-layer tool or as a fallback under the `va-visual-check` rules when the VA route has already been verified and the fallback reason has been recorded. Typical browser-layer cases:

- DOM/HTML/CSS: markup structure, non-standard widget, CSS clipping/overlap, exact browser selector.
- Browser diagnostics: console errors, network trace, cookies/localStorage/sessionStorage, web-auth/login/logout, publishing a database on a web server.
- Browser rendering: viewport/responsive behavior of the web client, pixel-level screenshot of the browser layer specifically, Chrome/Edge-only defect, behavior of the 1C browser extension.
- Browser-only I/O: file chooser/download/clipboard/drag-and-drop, if it depends on the browser rather than the 1C form.
- Fallback after VA MCP, when the browser/web client provides enough signal for the current task.

If the web client is selected, explicitly record the reason, the VA steps performed, and the residual risk of differences between the web client and the thin/thick client.

## Installation

```bash
cd tools/web-test && npm install
```

Node.js 18+. `npm install` downloads Playwright and Chromium.

## Quick Start

```bash
RUN="tools/web-test/run.mjs"

cat <<'SCRIPT' | node $RUN run http://erp-server:8080/mydb -
await navigateSection('Sales');
await openCommand('Customer Orders');
await clickElement('Create');
await fillFields({ 'Customer': 'Alpha' });
await clickElement('Post and Close');
SCRIPT
```

The URL comes from `.v8-project.json` (`webUrl` field) or is set explicitly.

## Modes

```bash
node $RUN run <url> script.js           # autonomous — executes and exits
node $RUN start <url>                   # interactive — starts a session
node $RUN test tests/<app>/ --url=<url> # regression suite *.test.mjs
cat <<'SCRIPT' | node $RUN exec -       # execute a script in the session
  const form = await getFormState();
SCRIPT
node $RUN shot result.png               # screenshot
node $RUN stop                          # logout + close (releases the license)
```

## API — Navigation

| Function | Description |
|---------|----------|
| `navigateSection(name)` | Go to a section (fuzzy match), returns `{ navigated, sections, commands }` |
| `openCommand(name)` | Open a command from the function panel → form state |
| `navigateLink(url)` | Navigate by metadata (Shift+F11), supports Russian names |
| `openFile(path)` | Open EPF/ERF, handles the security dialog |

## API — Reading

| Function | Description |
|---------|----------|
| `getFormState()` | All fields, buttons, tabs, tables, errors in one call |
| `readTable({ maxRows?, offset?, table? })` | Paginated table: `{ columns, rows, total }`. `table` selects a grid by name |
| `readSpreadsheet()` | Report (SpreadsheetDocument) after "Generate". Supports text-only and reports with numeric headers |

`getFormState()` returns: **fields** (name, value, actions, required), **table** (back-compat: first grid), **tables[]** (all visible grids: `{name, columns, rowCount, label}`), **openForms[]**, **formCount**, **modal**, **openTabs[]**, **navigation** (form navigation panel), **reportSettings** (human-readable settings of SCD), **errors.stateText** (info-bar SpreadsheetDocument), **errorModal**, **confirmation**.

Tree rows are marked `_kind: 'group'|'parent'`, `_tree: 'expanded'|'collapsed'`, `_level`, `_selected`.

## API — Actions

| Function | Description |
|---------|----------|
| `fillFields({ name: value })` | Fill fields (fuzzy match, auto type: catalog/checkbox/radio) |
| `fillField(name, value)` | Single-field analog of `fillFields` |
| `selectValue(field, search, opts?)` | Select from a catalog (dropdown / selection form) |
| `clickElement(text, opts?)` | Click a button/link/row. `opts`: `dblclick`, `table` (scope of the command panel of a specific grid), `toggle`/`expand` (tree), `modifier: 'ctrl'\|'shift'` (multi-select), `timeout` |
| `clickElement(target, opts?)` with `{row, column}` | Drill-down in SpreadsheetDocument: `{row: 0, column: 'К6'}`, `{row: {'К1': 'Materials'}, column: 'К6'}`, `{row: 'totals', column: 'К6'}` |
| `fillTableRow(fields, opts)` | Fill a tabular section row (`{ tab, add, row, table }`) |
| `deleteTableRow(row, { tab?, table? })` | Delete a row |
| `filterList(text, opts?)` / `unfilterList()` | Filter lists (simple / `{ field }`) |
| `closeForm({ save? })` | Close with confirmation handling |
| `switchTab(name)` | Switch the form tab or an open tab (tab bar) |
| `navigateLink(url)` | Open an object by metadata (Shift+F11), supports Russian names |
| `openFile(path)` | Open EPF/ERF via File→Open with security dialog handling |

## API — Utilities and Recording

| Function | Description |
|---------|----------|
| `screenshot()` | PNG screenshot |
| `wait(seconds)` | Wait + form state |
| `getPage()` | Playwright Page (special scenarios) |
| `startRecording(path, opts?)` / `stopRecording()` | Video recording (can be disabled at CLI level with `--no-record`) |
| `addNarration(videoPath, opts?)` | Overlay TTS narration (node-edge-tts) |
| `showCaption(text, opts?)` / `hideCaption()` | Caption over the video |
| `showTitleSlide(text)` / `hideTitleSlide()` | Title slide |
| `showImage(path, opts?)` / `hideImage()` | Image overlay |
| `highlight(text, opts?)` / `unhighlight()` / `setHighlight(on)` | Highlight elements |
| `fetchErrorStack(formNum, hasReport)` | Extract the call stack from the 1C error modal window |
| `getSections()` / `getCommands()` | Section panel |

## Important Details

- **Headed mode** — 1C requires a visible browser, no headless
- **Ctrl+V** instead of `page.fill()` — 1C reacts only to trusted events
- **Fuzzy matching** — exact > startsWith > includes; yo→e and \u00a0→ space automatically
- **Graceful logout** — `stop` → POST `/e1cib/logout` (releases the license)
- **Automatic error detection** — modals, balloons, confirmations are included in the response; on a modal error the stack (`fetchErrorStack`) and screenshot are pulled automatically
- **Multi-table** — if there are several grids on a form, `tables[]` lists them all; pass `{ table: 'Outgoing' }` to `readTable`/`clickElement`/`fillTableRow`/`deleteTableRow` to specify the needed one
- **Tree nodes** — by default, click selects, `{expand: true}` expands/collapses
- **Multi-select** — `clickElement(..., { modifier: 'ctrl' })` or `'shift'`
- **Accepted upstream delta** — the backlog accepted the transfer of `selectValue(field, [values])`, headerless grids, and stable clicking on a row in the modal selection form from `cc-1c-skills w-2026-06-28`; before use, check support in the current `tools/web-test` runtime
- **1C browser extension** — if installed in Chrome/Edge, it is picked up automatically; it can be overridden via `extensionPath` in `.v8-project.json`
- **Max 2 attempts** — after two failures, inform the user

## 1C Hotkeys

| Key | Context | Action |
|---------|----------|----------|
| `F8` | Reference field | Create a new item |
| `Shift+F4` | Reference field | Clear the value |
| `F4` | Reference field | Open the selection form |
| `Alt+F` | List/table | Advanced search |

## Regression Engine

When you need to cover a 1C solution with a series of automated tests - running several `.test.mjs` scenarios in a row, aggregating results, retrying flaky cases, screenshots on failures, Allure/JUnit reports - switch to `test` mode. More details: [regress.md](regress.md).

By default, use `run`/`exec` for one-off automation - `test` is a specialized mode for project-wide coverage.

Current runtime limitation: `test` supports one browser context; multi-user scenarios from `regress.md` remain the target contract until the modular engine is migrated.

## Video Recording and Subtitles

Two paths in priority order:

**1. Vanessa Automation (recommended)** — if the scenario is described in a `.feature` file. Vanessa records the run video and generates subtitles from Gherkin steps out of the box. Configured through the profile (`ЗаписыватьВидео`, `ГенерироватьСубтитры`, `ПутьКВидеозаписям`). Use for demo videos to the team and for documenting business processes.

**2. Playwright for browser/fallback recording** — when the scenario is in the browser layer, written in JS, or selected as a fallback under the `va-visual-check` rules. API: `startRecording` / `stopRecording` / `showCaption` / `addNarration` (TTS via node-edge-tts, OpenAI or ElevenLabs). Requires ffmpeg.

More details: [recording.md](recording.md) - comparison table, Vanessa profile parameters, full Playwright recording API, examples, troubleshooting.

---
depends_on: []
requires:
  - tools
metadata:
  category: 1c-development
  version: "1.2"
---
