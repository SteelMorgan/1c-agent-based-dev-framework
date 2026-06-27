# Playwright Regression Engine

Use this document when you need to cover a 1C solution with automated regression tests: run several `.feature` / JSON scenarios in sequence, aggregate results, get a fail/pass report, configure retry on flaky tests, and save screenshots on failures. For one-off automation (one scenario), stay in the `run`/`exec` modes from `SKILL.md`.

For 1C UI this is not the default regression path. If a test checks a form, command, field, tabular section, client handler, or business flow without browser-specific behavior, first write/run a Vanessa `.feature` through TestClient. Choose Playwright regression through `web-test-1c` for the web-client/browser layer or as fallback under `va-visual-check`: DOM/CSS/HTML, console/network, web-auth/publication, viewport/pixel rendering, browser extension, or browser-only I/O. In the test or report, record the VA steps, the reason for choosing browser/fallback, and the residual risk.

The runner is the same `run.mjs`. Mode: `test`:

```bash
node $RUN test <dir|file> [--url=<url>] [флаги]
```

The current implementation in this repository is a single-context runner: `url`, discovery, hooks, `step`, `assert`, `--tags`, `--grep`, `--retry`, JSON/JUnit/Allure-smoke reports, and screenshots on failure are supported. The multi-user `contexts` below describes the target contract, but it is not yet enabled in the `tools/web-test` runtime.

Tests live next to the project they cover, not inside the skill. Convention: `tests/` at the project root, `_hooks.mjs` and `webtest.config.mjs` at the suite root.

## When to use test, not exec

| Goal | Mode |
|------|-------|
| Explore a form / prototype a step without a browser-specific reason | Vanessa/TestClient or platform TestClient MCP |
| Debug a DOM/CSS selector or browser-only behavior | `exec` (interactive web session) |
| Reproduce a bug as a failing test before the fix | `test` |
| Cover a feature with tests for the future | `test` |
| Run a project regression on a new build | `test` |
| Make a screencast workflow | `exec` with `startRecording` |

Do not write a `.test.mjs` for a one-off request. Do not drive a regression suite through a chain of `exec` calls.

## Reconnaissance before writing tests

Two levels, in order.

**1. Static reconnaissance - metadata.** Never invent identifiers. For each metadata object, run the corresponding skill: `/meta-info` (attributes/tabular sections), `/form-info` (form layout), `/skd-info` (SKD), `/role-info` (permissions). If you cannot find it, ask.

**2. Live reconnaissance - interactive run.** For a non-trivial scenario, go through it in `exec` mode before writing the test. Metadata tells you what exists; a live run tells you what actually happens. Capture from `getFormState()`: exact button names, table section names, required fields, places where real waiting happens. Then transfer the working sequence into `*.test.mjs`, wrapping logical blocks in `step('...', async () => { ... })`.

## Suite Structure

**Each application has its own subfolder in `tests/`.** One repository can contain several isolated suites side by side - they must not share `_hooks.mjs` or `webtest.config.mjs`, because each restores a different database and publishes to a different URL.

```
tests/
  <app-name>/
    _hooks.mjs
    webtest.config.mjs
    _allure/          # optional: categories.json, executor.json
    01-login/
    02-counterparties/
    03-sales/
    ...
  <another-app>/      # second solution, fully isolated
```

Inside the application subfolder, organize by **feature**, not by metadata type. Numeric prefixes in folders and files define execution order. Entries starting with `_` or `.` are excluded from discovery (so `_hooks.mjs`, `_allure/` do not become tests).

## Anatomy of a test file

```js
export const name = 'Создание контрагента';       // обязательно
export const tags = ['catalog', 'create'];        // опционально, для фильтрации + Allure
export const timeout = 60000;                     // опционально, по умолчанию 30000
// export const skip = 'ожидаем фикс #123';       // опционально: true | string
// export const only = true;                      // только для отладки — не коммитить
// export const context = 'manager';             // опционально, один нестандартный контекст
// export const contexts = ['clerk', 'manager']; // опционально, multi-user тест
// export const severity = 'critical';           // опционально, переопределяет config

export async function setup(ctx) {
  // подготовка перед тестом — выполняется до дефолтного
}

export async function teardown(ctx) {
  // очистка после теста — выполняется всегда (даже при падении)
}

export default async function(ctx) {
  const { navigateSection, openCommand, clickElement, fillFields,
          readTable, closeForm, getFormState,
          assert, step, log } = ctx;

  await step('Открыть список контрагентов', async () => {
    await navigateSection('Продажи');
    await openCommand('Контрагенты');
  });

  await step('Создать нового контрагента', async () => {
    await clickElement('Создать');
    await fillFields({ 'Наименование': 'Тест ' + Date.now() });
    await clickElement('Записать и закрыть');
  });

  await step('Убедиться, что элемент появился в списке', async () => {
    const t = await readTable();
    assert.tableHasRow(t, r => r['Наименование']?.startsWith('Тест '));
  });
}
```

**Step names should be in Russian and descriptive.** Step labels appear in console output, JSON/JUnit, and Allure steps. Use a full action phrase (`'Create a new counterparty'`), not a tag (`'create'`).

## ctx Contract

The runner injects into `ctx` all exports from `browser.mjs` (all 1C API functions), plus testing utilities:

```js
step(name, fn)   // async wrapper. Records start/stop. Supports nesting.
log(...args)     // adds a line to ctx.testInfo output (goes into JSON/Allure attachment)
assert.*         // see "Assertions" below
```

### ctx.testInfo (always set, read-only)

```js
{
  name,          // 'Навигация по разделам' (with parameters substituted)
  file,          // '01-navigation.test.mjs' (basename)
  tags,          // ['nav', 'smoke']
  timeout,       // ms
  attempt,       // 1..maxAttempts
  maxAttempts,   // 1 + retry
  param,         // { ... } | undefined (only when export const params is set)
  // planned after modular engine migration:
  // contexts: { clerk: { url, ... }, manager: { url, ... } },
  // primaryContext: 'clerk'
}
```

### ctx.testResult (only in afterEach)

```js
{
  status,    // 'passed' | 'failed'
  duration,  // ms
  attempts,  // attempts actually performed
  error,     // { message, step?, screenshot? } | null
  steps      // array of step results
}
```

## Assertions

Everything is on `ctx.assert`. They throw `AssertionError` with `.message`, `.actual`, `.expected`.

```js
// generic
assert.ok(value, msg?)
assert.equal(actual, expected, msg?)
assert.notEqual(actual, expected, msg?)
assert.deepEqual(actual, expected, msg?)
assert.includes(haystack, needle, msg?)
assert.match(string, regex, msg?)
await assert.throws(asyncFn, msg?)

// 1C specifics - work with getFormState() / readTable()
assert.formHasField(state, 'Контрагент', msg?)
assert.formTitle(state, expected, msg?)
assert.tableHasRow(table, predicate, msg?)   // predicate: object (partial) or fn(row) => bool
assert.tableRowCount(table, expected, msg?)
assert.noErrors(state, msg?)
```

## webtest.config.mjs

```js
export default {
  // Single context:
  url: 'http://localhost:9191/myapp/ru_RU',

  // OR multiple contexts:
  // contexts: {
  //   clerk:   { url: '...', displayName: 'Кладовщик' },
  //   manager: { url: '...', displayName: 'Менеджер' },
  // },
  // defaultContext: 'clerk',

  timeout: 30000,
  retries: 0,              // retry on flaky tests
  screenshot: 'on-failure', // 'every-step' | 'off' | 'on-failure'
  record: false,

  severity: {
    critical: ['smoke', 'crud'],
    minor:    ['recording'],
  },
  defaultSeverity: 'normal',
};
```

CLI flags override the config. Use Latin context IDs plus Russian `displayName` for ergonomics.

## _hooks.mjs

```js
// Infrastructure hooks - work without a browser
export async function prepare({ hookArgs, log, config }) {
  // Restore the database, publish, build EPF. Make it idempotent.
}
export async function cleanup({ log, config }) { /* optional */ }

// Test level - work with browser ctx
export async function beforeAll(ctx) { }
export async function afterAll(ctx)  { }
export async function beforeEach(ctx) { }
export async function afterEach(ctx)  { }

// Per-context
export async function afterOpenContext(ctx, name, spec)   { }
export async function beforeCloseContext(ctx, name, spec) { }
```

Pass hook arguments after `--`:

```bash
node $RUN test tests/<app-name>/ --bail -- --rebuild-stand --data=demo
```

## Running

```bash
node $RUN test tests/<app-name>/                     # entire application suite
node $RUN test tests/<app-name>/03-goods-receipt/    # one feature folder
node $RUN test tests/<app-name>/02-cnt/01-create.test.mjs  # one file
node $RUN test tests/<app-name>/ --tags=smoke        # by tag
node $RUN test tests/<app-name>/ --grep='накладн'    # by name (regex)
node $RUN test tests/<app-name>/ --bail --retry=1    # stop on first failure + 1 retry
node $RUN test tests/<app-name>/ --format=allure --report-dir=allure-results
node $RUN test tests/<app-name>/ -- --rebuild-stand  # hookArgs
```

The `--retry=1` flag gives one retry for a flaky test - especially useful for unstable environments with 1C licenses.

### Screenshots on failures

`screenshot: 'on-failure'` in `webtest.config.mjs` (or `--screenshot=on-failure`) automatically captures a PNG on every failed test. The screenshot path is included in `ctx.testResult.error.screenshot` and in the report. With `'every-step'`, a shot is taken after every `step()`.

## Ready-made Patterns

### SKD report

```js
await openCommand('Остатки товаров');
// Reset user settings (1С stores them between sessions)
await clickElement('Ещё');
await clickElement('Установить стандартные настройки');
await selectValue('Номенклатура', 'Товар 02');
await clickElement('Сформировать');
await wait(3);
const r = await readSpreadsheet();
assert.deepEqual(r.headers, ['Номенклатура', 'Количество', 'Сумма']);
assert.ok(r.data.length >= 1);
assert.ok(r.totals?.['Сумма']);
```

### Multi-user process

```js
export const contexts = ['clerk', 'manager'];

export default async function({ clerk, manager, step, assert }) {
  await step('Кладовщик создаёт накладную', async () => {
    await clerk.navigateSection('Склад');
    await clerk.openCommand('Приходные накладные');
    await clerk.clickElement('Создать');
    await clerk.fillFields({ 'Контрагент': 'ООО Север' });
    await clerk.clickElement('Записать');
  });
  await step('Менеджер утверждает', async () => {
    await manager.navigateSection('Согласование');
    await manager.openCommand('На утверждении');
    await manager.clickElement('ООО Север', { dblclick: true });
    await manager.clickElement('Утвердить');
  });
  await step('Кладовщик видит новый статус', async () => {
    const s = await clerk.getFormState();
    assert.equal(s.fields['Статус']?.value, 'Утверждён');
  });
  // Освобождаем лицензию 1С
  await manager.closeContext('clerk');
}
```

### Parameterized test

```js
export const name = 'Заполнение поля {type}';
export const params = [
  { type: 'String', field: 'Наименование', value: 'Тест' },
  { type: 'Number', field: 'Цена', value: '100.50' },
];

export default async function({ fillFields, getFormState, assert }, { field, value }) {
  await fillFields({ [field]: value });
  const state = await getFormState();
  assert.equal(state.fields[field]?.value, String(value));
}
```

### Bug reproduction (failing test)

```js
export const name = 'Bug #123: накладная без контрагента не должна проводиться';
export const tags = ['bug', 'validation'];

export default async function({ openCommand, clickElement, getFormState, assert, step }) {
  await openCommand('Приходные накладные');
  await clickElement('Создать');
  await clickElement('Провести');
  const s = await getFormState();
  assert.ok(s.errorModal || s.fields['Контрагент']?.required,
    'Должна быть ошибка валидации или поле помечено обязательным');
}
```

Write it failing first, hand it to the user, fix it, rerun it green.

## Test Severity (severity)

| Type of test | Recommendation |
|-----------|--------------|
| Login + navigation, basic CRUD for covered entities | `critical` (+ tag `smoke`) |
| Posting documents, report generation, end-to-end processes | `critical` |
| Edge cases for fields, formatting, optional flows | `normal` |
| Video recording / non-functional | `minor` |

Do not mark everything `critical` - that erodes the signal in the Allure dashboard.

## Antipatterns

- **Sleep instead of waiting for state.** `wait(5)` after `openCommand` is fine; `wait(30)` because it is flaky is a bug.
- **Retry instead of understanding.** “Not found” twice means the data is missing or the name is wrong.
- **Binding to row position** (`rows[0]`) when the database has shared data. Filter by a unique marker.
- **Manual state reset in `afterEach`.** The runner already closes forms and hides errors.
- **Dependence on test order.** Every test should start from the desktop and prepare its own data.
- **`tags: ['smoke']` on a 90-second test.** Smoke means fast.

## Failure Triage

1. Check the JSON or Allure summary for `failed`.
2. For each failure: `error.message` + `error.step` + screenshot.
3. If there is `error.onecError.stack` - that is a 1C exception, inspect the platform traceback.
4. Classify:
   - **Test bug** - wrong selector, wrong expectation, race condition -> fix the test.
   - **Application bug** -> report to the user with the name of the failing step and the stack.
   - **Environment instability** - Apache timeout, no license -> fix hook idempotency.
5. After fixes, rerun only the failed files, then the full suite.

## Reference

- Base browser API: [SKILL.md](SKILL.md)
- Video recording + subtitles: [recording.md](recording.md)
