---
name: web-test-1c
description: Automation of 1C via the web client — navigating sections, filling forms, reading tables and reports, filtering lists. Use when you need to test, verify, or automate actions in 1C through a browser.
---

# web-test-1c — 1C web client automation

Semantic automation layer on top of Playwright, tailored to the 1С:Предприятие web client DOM. ~5100 lines of JS with 40+ API functions.

## Installation (first time)

```bash
cd tools/web-test && npm install
```

Node.js 18+ is required. `npm install` will download Playwright and Chromium.

## Quick start

```bash
RUN="tools/web-test/run.mjs"

# Single-run: open → execute → close
cat <<'SCRIPT' | node $RUN run http://erp-server:8080/mydb -
await navigateSection('Продажи');
await openCommand('Заказы клиентов');
await clickElement('Создать');
await fillFields({ 'Клиент': 'Альфа' });
await clickElement('Провести и закрыть');
SCRIPT
```

## Publication URL

The URL is taken from `.v8-project.json` (the database `webUrl` field) or can be set explicitly. Apache may run on a remote server — web-test connects via any HTTP URL.

```json
{
  "databases": [
    {
      "id": "dev",
      "webUrl": "http://erp-server:8080/devdb"
    }
  ]
}
```

## Modes of operation

### Autonomous (for full scenarios)

```bash
node $RUN run <url> script.js   # executes and exits
```

### Interactive (step-by-step development)

```bash
# 1. Start the session (run_in_background=true)
node $RUN start <url>

# 2. Execute scripts
cat <<'SCRIPT' | node $RUN exec -
const form = await getFormState();
console.log(JSON.stringify(form, null, 2));
SCRIPT

# 3. Screenshot
node $RUN shot result.png

# 4. Stop (logout + close — frees the 1C license)
node $RUN stop
```

## API — Navigation

### `navigateSection(name)` → `{ navigated, sections, commands }`

Move to a section (fuzzy match). Returns the section command list.

```js
await navigateSection('Продажи');
```

### `openCommand(name)` → form state

Open a command from the function panel.

```js
const form = await openCommand('Заказы клиентов');
```

### `navigateLink(url)` → form state

Navigate through metadata via Shift+F11. Russian names are supported.

```js
await navigateLink('Документ.ЗаказКлиента');
await navigateLink('Справочник.Контрагенты');
```

### `openFile(path)` → form state

Open an EPF/ERF file. Security dialog is handled automatically.

## API — Reading form state

### `getFormState()` → `{ fields, buttons, tabs, table, filters, reportSettings?, errorModal?, confirmation? }`

Primary way to understand what is on the screen. One call returns all fields, buttons, tabs, table, errors.

**fields** — name, value, label, actions (select/clear/open), required
**table** — `{ name, columns, rowCount }` (summary; for data — `readTable()`)
**reportSettings** — СКД filters with human-readable names
**errorModal** — 1C modal error (if present)
**confirmation** — Yes/No dialog (if present)

## API — Reading data

### `readTable({ maxRows?, offset? })` → `{ columns, rows, total }`

Read a table with pagination. Rows: `{ columnName: value }`.

```js
const t = await readTable({ maxRows: 50 });
const page2 = await readTable({ maxRows: 50, offset: 50 });
```

### `readSpreadsheet()` → `{ title?, headers?, data?, totals?, total }`

Read a report (SpreadsheetDocument) after “Сформировать”.

```js
await clickElement('Сформировать');
await wait(5);
const report = await readSpreadsheet();
```

## API — Actions

### `fillFields({ name: value })` → `{ filled, form }`

Fill form fields by name (fuzzy match). Type is auto-detected:
- Справочник → Ctrl+V + typeahead
- Checkbox → toggle
- Radio button → fuzzy match

**СКД filters**: the checkbox is enabled automatically:
```js
await fillFields({ 'Склад': 'Основной', 'Номенклатура': 'Бумага' });
```

### `selectValue(field, search, opts?)` → form state

Select a value from a catalog via dropdown or selection form.

```js
await selectValue('Организация', 'Конфетпром');
// Composite type:
await selectValue('Документ', '0000-000601', { type: 'Реализация (акт' });
```

### `clickElement(text, { dblclick? })` → form state

Click on a button, hyperlink, table row (fuzzy match). Double click opens the item.

```js
await clickElement('Создать');
await clickElement('КП00-000227', { dblclick: true }); // open the document
```

### `fillTableRow(fields, opts)` → form state

Fill a tabular section row. Tab navigation, cell type detection.

```js
await fillTableRow(
  { 'Номенклатура': 'Бумага', 'Количество': '10', 'Цена': '100' },
  { tab: 'Товары', add: true }
);
```

### `deleteTableRow(row, { tab? })` → form state

### `filterList(text, opts?)` / `unfilterList({ field? })` → form state

Filtering lists: simple search or advanced (by a specific field).

```js
await filterList('КП00-000018');
await filterList('Мишка', { field: 'Наименование' });
await unfilterList();
```

### `closeForm({ save? })` → form state

Close a form with confirmation handling (Yes/No).

### `switchTab(name)` → form state

## API — Utilities

| Function | Description |
|---------|-------------|
| `screenshot()` | PNG screenshot |
| `wait(seconds)` | Wait + form state |
| `getPage()` | Playwright Page (for nonstandard scenarios) |
| `startRecording(path)` / `stopRecording()` | Video recording (CDP screencast → ffmpeg) |
| `showCaption(text)` / `hideCaption()` | Text overlay |
| `addNarration(videoPath, opts)` | TTS narration (Edge TTS / OpenAI) |
| `getSections()` / `getCommands()` | Read the section panel |
| `getPageState()` | Sections + open tabs |

## Typical scenarios

### Create and post a document

```js
await navigateSection('Продажи');
await openCommand('Заказы клиентов');
await clickElement('Создать');
await fillFields({ 'Организация': 'Конфетпром', 'Контрагент': 'Альфа' });
await fillTableRow({ 'Номенклатура': 'Бумага', 'Количество': '10' }, { tab: 'Товары', add: true });
await clickElement('Провести и закрыть');
```

### Generate and read a report

```js
await fillFields({ 'Склад': 'Основной склад' });
await clickElement('Сформировать');
await wait(5);
const report = await readSpreadsheet();
console.log('Rows:', report.data?.length);
```

### Find and open an item from a list

```js
await filterList('Конфетпром');
await clickElement('Конфетпром ООО', { dblclick: true });
await closeForm();
await unfilterList();
```

## Important notes

- **Headed mode** — 1C requires a visible browser, no headless
- **Ctrl+V** instead of `page.fill()` — 1C only reacts to trusted events (autocomplete, selection)
- **Fuzzy matching** — all name searches: exact > startsWith > includes
- **ё→е normalization** and **\u00a0→space** — applied automatically in all comparisons
- **Graceful logout** — `stop` sends POST `/e1cib/logout` to release the 1C license
- **Auto error detection** — modal errors, balloons, confirmations are detected and included in the response
- **Max 2 attempts** — if an action fails twice, notify the user instead of looping

## 1C hotkeys

| Key | Context | Action |
|-----|---------|--------|
| `F8` | Reference field | Create a new item |
| `Shift+F4` | Reference field | Clear the value |
| `F4` | Reference field | Open the selection form |
| `Alt+F` | List/table | Advanced search |

## See also

- [playwright](../playwright/) — generic Playwright for non-1C web applications
- [playwright-interactive](../playwright-interactive/) — interactive Playwright session
- [visual-check](../visual-check/) — visual form verification

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
