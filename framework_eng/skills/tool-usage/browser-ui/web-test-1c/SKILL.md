---
name: web-test-1c
description: "Use for browser automation in 1С (navigation across sections, filling forms, reading tables and reports, filtering lists). Helps write browser tests for 1С on a semantic layer without knowing DOM details of the platform."
---

# web-test-1c — 1С Web Client Automation

Semantic layer on top of Playwright for the DOM of the 1С:Предприятие web client.

## Installation

```bash
cd tools/web-test && npm install
```

Node.js 18+. `npm install` will download Playwright and Chromium.

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

The URL comes from `.v8-project.json` (the `webUrl` field) or is set explicitly.

## Operating modes

```bash
node $RUN run <url> script.js           # autonomous — runs and exits
node $RUN start <url>                   # interactive — starts a session
node $RUN test tests/<app>/ --url=<url> # regression suite *.test.mjs
cat <<'SCRIPT' | node $RUN exec -       # run a script in the session
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
| `navigateLink(url)` | Go to an object by metadata (Shift+F11), Russian names are supported |
| `openFile(path)` | Open EPF/ERF, handles the security dialog |

## API — Reading

| Function | Description |
|---------|----------|
| `getFormState()` | All fields, buttons, tabs, tables, and errors in a single call |
| `readTable({ maxRows?, offset?, table? })` | Table with pagination: `{ columns, rows, total }`. `table` selects the grid by name |
| `readSpreadsheet()` | Report (SpreadsheetDocument) after “Generate”. Supports text-only and reports with numeric headers |

`getFormState()` returns: **fields** (name, value, actions, required), **table** (back-compat: first grid), **tables[]** (all visible grids: `{name, columns, rowCount, label}`), **openForms[]**, **formCount**, **modal**, **openTabs[]**, **navigation** (form navigation panel), **reportSettings** (human-readable СКД settings), **errors.stateText** (info-bar SpreadsheetDocument), **errorModal**, **confirmation**.

Tree rows are marked `_kind: 'group'|'parent'`, `_tree: 'expanded'|'collapsed'`, `_level`, `_selected`.

## API — Actions

| Function | Description |
|---------|----------|
| `fillFields({ name: value })` | Fill fields (fuzzy match, auto-type: catalog/checkbox/radio) |
| `fillField(name, value)` | Single-field version of `fillFields` |
| `selectValue(field, search, opts?)` | Choose from a catalog (dropdown / selection form) |
| `clickElement(text, opts?)` | Click a button/link/row. `opts`: `dblclick`, `table` (scope the command panel to a specific grid), `toggle`/`expand` (tree), `modifier: 'ctrl'\|'shift'` (multi-select), `timeout` |
| `clickElement(target, opts?)` with `{row, column}` | Drill down in SpreadsheetDocument: `{row: 0, column: 'К6'}`, `{row: {'К1': 'Материалы'}, column: 'К6'}`, `{row: 'totals', column: 'К6'}` |
| `fillTableRow(fields, opts)` | Fill a tabular-section row (`{ tab, add, row, table }`) |
| `deleteTableRow(row, { tab?, table? })` | Delete a row |
| `filterList(text, opts?)` / `unfilterList()` | Filter lists (simple / `{ field }`) |
| `closeForm({ save? })` | Close with confirmation handling |
| `switchTab(name)` | Switch the form tab or an open tab (tab bar) |
| `navigateLink(url)` | Open an object by metadata (Shift+F11), Russian names are supported |
| `openFile(path)` | Open EPF/ERF through File→Open with security dialog handling |

## API — Utilities and recording

| Function | Description |
|---------|----------|
| `screenshot()` | PNG screenshot |
| `wait(seconds)` | Wait + form state |
| `getPage()` | Playwright Page (non-standard scenarios) |
| `startRecording(path, opts?)` / `stopRecording()` | Video recording (can be disabled at the CLI level with `--no-record`) |
| `addNarration(videoPath, opts?)` | Overlay TTS narration (node-edge-tts) |
| `showCaption(text, opts?)` / `hideCaption()` | Caption over the video |
| `showTitleSlide(text)` / `hideTitleSlide()` | Title slide |
| `showImage(path, opts?)` / `hideImage()` | Image overlay |
| `highlight(text, opts?)` / `unhighlight()` / `setHighlight(on)` | Highlight elements |
| `fetchErrorStack(formNum, hasReport)` | Extract the call stack from the 1C error modal |
| `getSections()` / `getCommands()` | Section panel |

## Important features

- **Headed mode** — 1C requires a visible browser, no headless
- **Ctrl+V** instead of `page.fill()` — 1C reacts only to trusted events
- **Fuzzy matching** — exact > startsWith > includes; `ё` is normalized to `е` and `\u00a0` to a space automatically
- **Graceful logout** — `stop` → POST `/e1cib/logout` (releases the license)
- **Auto error detection** — modals, balloons, confirmations are included in the response; when a modal error occurs, the stack (`fetchErrorStack`) and screenshot are pulled automatically
- **Multi-table** — if a form has multiple grids, `tables[]` lists them all; pass `{ table: 'Outgoing' }` to `readTable`/`clickElement`/`fillTableRow`/`deleteTableRow` to select the right one
- **Tree nodes** — by default, clicking selects; `{expand: true}` expands/collapses
- **Multi-select** — `clickElement(..., { modifier: 'ctrl' })` or `'shift'`
- **1C browser extension** — if installed in Chrome/Edge, it is picked up automatically; you can override it via `extensionPath` in `.v8-project.json`
- **Max. 2 attempts** — after two failures, tell the user

## 1C hotkeys

| Key | Context | Action |
|---------|----------|----------|
| `F8` | Reference field | Create a new element |
| `Shift+F4` | Reference field | Clear the value |
| `F4` | Reference field | Open the selection form |
| `Alt+F` | List/table | Advanced search |

## Regression engine

When you need to cover a 1C solution with a series of automated tests — running multiple `.test.mjs` scenarios in sequence, aggregating results, retrying flaky cases, screenshots on failures, Allure/JUnit reports — switch to `test` mode. More details: [regress.md](regress.md).

By default, use `run`/`exec` for one-off automation — `test` is a specialized mode for project-wide coverage.

Current runtime limitation: `test` supports one browser context; multi-user scenarios from `regress.md` remain the target contract until the modular engine is ported.

## Video recording and subtitles

Two paths in priority order:

**1. Vanessa Automation (recommended)** — if the scenario is described in a `.feature` file. Vanessa records the run video and generates subtitles from Gherkin steps out of the box. Configured via a profile (`ЗаписыватьВидео`, `ГенерироватьСубтитры`, `ПутьКВидеозаписям`). Use for demo videos for the team and for documenting business processes.

**2. Playwright fallback** — when Vanessa is unavailable, headless is needed, or the scenario is written in JS. API: `startRecording` / `stopRecording` / `showCaption` / `addNarration` (TTS via node-edge-tts, OpenAI, or ElevenLabs). Requires ffmpeg.

More details: [recording.md](recording.md) — comparison table, Vanessa profile parameters, full Playwright recording API, examples, troubleshooting.

---
depends_on: []
requires:
  - tools
metadata:
  category: 1c-development
  version: "1.2"
---
