---
name: web-test-1c
description: Automation of 1C via the web client — navigation across sections, filling forms, reading tables and reports, filtering lists. Use when you need to test, verify, or automate actions in 1C through a browser.
---

# web-test-1c — 1C web client automation

A semantic layer on top of Playwright for the DOM of the 1C:Предприятие web client.

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

The URL comes from `.v8-project.json` (the `webUrl` field) or is specified explicitly.

## Operating modes

```bash
node $RUN run <url> script.js           # autonomous — runs and exits
node $RUN start <url>                   # interactive — starts a session
cat <<'SCRIPT' | node $RUN exec -       # run a script inside the session
  const form = await getFormState();
SCRIPT
node $RUN shot result.png               # screenshot
node $RUN stop                          # logout + close (releases licensing)
```

## API — Navigation

| Функция | Описание |
|---------|----------|
| `navigateSection(name)` | Go to a section (fuzzy match), returns `{ navigated, sections, commands }` |
| `openCommand(name)` | Open a command from the function panel → form state |
| `navigateLink(url)` | Follow metadata (Shift+F11), supports Russian names |
| `openFile(path)` | Open EPF/ERF, handles the security dialog |

## API — Reading

| Функция | Описание |
|---------|----------|
| `getFormState()` | All fields, buttons, tabs, table, and errors in a single call |
| `readTable({ maxRows?, offset? })` | Table with pagination: `{ columns, rows, total }` |
| `readSpreadsheet()` | Report (SpreadsheetDocument) after “Сформировать” |

`getFormState()` returns: **fields** (name, value, actions, required), **table** (summary), **reportSettings** (СКД filters), **errorModal**, **confirmation**.

## API — Actions

| Функция | Описание |
|---------|----------|
| `fillFields({ name: value })` | Fill fields (fuzzy match, auto-type: справочник/checkbox/radio) |
| `selectValue(field, search, opts?)` | Choose from a справочник (dropdown / selection form) |
| `clickElement(text, { dblclick? })` | Click a button/link/row (dblclick opens the item) |
| `fillTableRow(fields, opts)` | Fill a tabular section row (`{ tab, add }`) |
| `deleteTableRow(row, { tab? })` | Remove a row |
| `filterList(text, opts?)` / `unfilterList()` | Filter lists (simple / `{ field }`) |
| `closeForm({ save? })` | Close with confirmation handling |
| `switchTab(name)` | Switch tabs |

## API — Utilities

| Функция | Описание |
|---------|----------|
| `screenshot()` | PNG screenshot |
| `wait(seconds)` | Wait + form state |
| `getPage()` | Playwright Page (non-standard scenarios) |
| `startRecording(path)` / `stopRecording()` | Video recording |
| `getSections()` / `getCommands()` | Section panel |

## Important features

- **Headed mode** — 1C requires a visible browser, no headless
- **Ctrl+V** instead of `page.fill()` — 1C reacts only to trusted events
- **Fuzzy matching** — exact > startsWith > includes; ё→е and \u00a0→space are normalized automatically
- **Graceful logout** — `stop` → POST `/e1cib/logout` (releases the license)
- **Auto error detection** — modals, balloons, confirmations are captured in the response
- **Max 2 attempts** — after two failures, report the issue instead of retrying

## 1C hotkeys

| Клавиша | Контекст | Действие |
|---------|----------|----------|
| `F8` | Ссылочное поле | Create a new element |
| `Shift+F4` | Ссылочное поле | Clear the value |
| `F4` | Ссылочное поле | Open the selection form |
| `Alt+F` | Список/таблица | Advanced search |

---
depends_on: []
requires:
  - tools
metadata:
  category: 1c-development
  version: "1.0"
---
