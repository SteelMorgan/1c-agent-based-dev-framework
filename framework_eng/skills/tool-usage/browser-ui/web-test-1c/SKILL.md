---
name: web-test-1c
description: "Browser UI tests for 1C: forms, tables, reports"
---

# web-test-1c — 1C web client automation

A semantic layer on top of Playwright for the 1C:Enterprise web client DOM.

## Scope

`web-test-1c` is NOT the default route for checking 1C UI. If the task is to open a form/list, press a command, fill in fields, check visibility/accessibility, client handler reactions, table section rows, a user business flow, or visually approve a form, first apply the dedicated skill `va-visual-check`.

The web client is allowed as a browser-layer tool or as a fallback under `va-visual-check` rules, when the VA route has already been checked and the fallback reason is recorded. Typical browser-layer cases:

- DOM/HTML/CSS: markup structure, custom widget, CSS clipping/overlap, exact browser selector.
- Browser diagnostics: console errors, network trace, cookies/localStorage/sessionStorage, web-auth/login/logout, publishing the database on a web server.
- Browser rendering: viewport/responsive behavior of the web client, pixel-level screenshot of the browser layer itself, Chrome/Edge-only defect, behavior of the 1C browser extension.
- Browser-only I/O: file chooser/download/clipboard/drag-and-drop, if it depends on the browser and not on the 1C form.
- Fallback after VA MCP, when the browser/web client gives enough signal for the current task.

If the web client is chosen, explicitly record the reason, the VA steps that were performed, and the residual risk of differences between the web client and the thin/thick client.

## Installation

```bash
cd tools/web-test && npm install
```

Node.js 18+. `npm install` downloads Playwright and Chromium.

## Quick start

```bash
RUN="tools/web-test/run.mjs"

cat <<'SCRIPT' | node $RUN run http://erp-server:8080/mydb -
await navigateSection('Продажи');
await openCommand('Заказы клиентов');
await clickElement('Создать');
await fillFields({ 'Клиент': 'Альфа' });
await clickElement('Провести и закрыть');
SCRIPT
```

URL from `.v8-project.json` (`webUrl` field) or set explicitly.

## Operation modes

```bash
node $RUN run <url> script.js           # autonomous — executes and exits
node $RUN start <url>                   # interactive — session start
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
| `openCommand(name)` | Open a command from the functions panel -> form state |
| `navigateLink(url)` | Navigate by metadata (Shift+F11), supports Russian names |
| `openFile(path)` | Open EPF/ERF, handles the security dialog |

## API — Reading

| Function | Description |
|---------|----------|
| `getFormState()` | All fields, buttons, tabs, tables, errors in one call |
| `readTable({ maxRows?, offset?, table? })` | Table with pagination: `{ columns, rows, total }`. `table` selects the grid by name |
| `readSpreadsheet()` | Report (SpreadsheetDocument) after "Generate". Supports text-only and reports with numeric headers |

`getFormState()` returns: **fields** (name, value, actions, required), **table** (back-compat: first grid), **tables[]** (all visible grids: `{name, columns, rowCount, label}`), **openForms[]**, **formCount**, **modal**, **openTabs[]**, **navigation** (form navigation panel), **reportSettings** (human-readable SKD settings), **errors.stateText** (info-bar SpreadsheetDocument), **errorModal**, **confirmation**.

Tree rows are marked `_kind: 'group'|'parent'`, `_tree: 'expanded'|'collapsed'`, `_level`, `_selected`.

## API — Actions

| Function | Description |
|---------|----------|
| `fillFields({ name: value })` | Field filling (fuzzy match, auto-type: lookup list/checkbox/radio) |
| `fillField(name, value)` | Single-field equivalent of `fillFields` |
| `selectValue(field, search, opts?)` | Select from a lookup list (drop-down / selection form) |
| `clickElement(text, opts?)` | Click a button/link/row. `opts`: `dblclick`, `table` (scope of the command panel for a specific grid), `toggle`/`expand` (tree), `modifier: 'ctrl'\|'shift'` (multi-select), `timeout` |
| `clickElement(target, opts?)` with `{row, column}` | Drill-down in SpreadsheetDocument: `{row: 0, column: 'К6'}`, `{row: {'К1': 'Материалы'}, column: 'К6'}`, `{row: 'totals', column: 'К6'}` |
| `fillTableRow(fields, opts)` | Table row filling (`{ tab, add, row, table }`) |
| `deleteTableRow(row, { tab?, table? })` | Delete row |
| `filterList(text, opts?)` / `unfilterList()` | List filtering (simple / `{ field }`) |
| `closeForm({ save? })` | Close with confirmation handling |
| `switchTab(name)` | Switch the form tab or an open tab (tab bar) |
| `navigateLink(url)` | Open an object by metadata (Shift+F11), supports Russian names |
| `openFile(path)` | Open EPF/ERF via File→Open with security dialog handling |

## API — Utilities and recording

| Function | Description |
|---------|----------|
| `screenshot()` | PNG screenshot |
| `wait(seconds)` | Pause + form state |
| `getPage()` | Playwright Page (non-standard scenarios) |
| `startRecording(path, opts?)` / `stopRecording()` | Video recording (can be disabled at the CLI level with `--no-record`) |
| `addNarration(videoPath, opts?)` | Overlay TTS narration (node-edge-tts) |
| `showCaption(text, opts?)` / `hideCaption()` | Caption over video |
| `showTitleSlide(text)` / `hideTitleSlide()` | Title slide |
| `showImage(path, opts?)` / `hideImage()` | Image overlay |
| `highlight(text, opts?)` / `unhighlight()` / `setHighlight(on)` | Highlight elements |
| `fetchErrorStack(formNum, hasReport)` | Retrieve the call stack from a 1C error modal |
| `getSections()` / `getCommands()` | Sections panel |

## Important details

- **Headed mode** — 1C requires a visible browser, no headless
- **Ctrl+V** instead of `page.fill()` — 1C reacts only to trusted events
- **Fuzzy matching** — exact > startsWith > includes; yo→e and \u00a0→space automatically
- **Graceful logout** — `stop` → POST `/e1cib/logout` (releases the license)
- **Automatic error detection** — modals, balloon messages, confirmations are included in the response; on a modal error, the stack (`fetchErrorStack`) and screenshot are attached automatically
- **Multi-table** — if there are multiple grids on the form, `tables[]` lists all of them; pass `{ table: 'Outgoing' }` in `readTable`/`clickElement`/`fillTableRow`/`deleteTableRow` to specify the needed one
- **Tree nodes** — by default, click selects; `{expand: true}` expands/collapses
- **Multi-select** — `clickElement(..., { modifier: 'ctrl' })` or `'shift'`
- **1C browser extension** — if installed in Chrome/Edge, it is picked up automatically; it can be overridden through `extensionPath` in `.v8-project.json`
- **Max. 2 attempts** — after two failures, report to the user

## 1C hotkeys

| Key | Context | Action |
|---------|----------|----------|
| `F8` | Reference field | Create a new item |
| `Shift+F4` | Reference field | Clear the value |
| `F4` | Reference field | Open the selection form |
| `Alt+F` | List/table | Advanced search |

## Regression engine

When you need to cover a 1C solution with a series of automated tests — running several `.test.mjs` scenarios in a row, aggregating results, retrying flaky cases, screenshots on failures, Allure/JUnit reports — switch to `test` mode. More details: [regress.md](regress.md).

By default, use `run`/`exec` for one-off automation — `test` is a specialized mode for project coverage.

Current runtime limitation: `test` supports one browser context; multi-user scenarios from `regress.md` remain the target contract until the modular engine is migrated.

## Video recording and subtitles

Two paths in priority order:

**1. Vanessa Automation (recommended)** — if the scenario is described in a `.feature` file. Vanessa records the run video and generates subtitles from Gherkin steps out of the box. Configure it through the profile (`ЗаписыватьВидео`, `ГенерироватьСубтитры`, `ПутьКВидеозаписям`). Use it for demo videos to the team and for documenting business processes.

**2. Playwright for browser/fallback recording** — when the scenario is in the browser layer, written in JS, or selected as fallback under `va-visual-check` rules. API: `startRecording` / `stopRecording` / `showCaption` / `addNarration` (TTS via node-edge-tts, OpenAI or ElevenLabs). Requires ffmpeg.

More details: [recording.md](recording.md) — comparison table, Vanessa profile parameters, full Playwright recording API, examples, troubleshooting.

---
depends_on: []
requires:
  - tools
metadata:
  category: 1c-development
  version: "1.2"
---
