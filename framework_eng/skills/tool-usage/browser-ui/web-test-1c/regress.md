# Playwright Regression Engine

Use this document when you need to cover a 1C solution with automated regression tests: run several `.feature` / JSON scenarios in sequence, aggregate results, get a fail/pass report, configure retry for flaky tests, save screenshots on failures. For one-off automation (a single scenario), stay in the `run`/`exec` modes from `SKILL.md`.

For 1C UI this is not the default regression path. If a test checks a form, command, field, TЧ, client handler, or business flow without browser-specific behavior, first write/run Vanessa `.feature` through TestClient. Choose Playwright regression through `web-test-1c` for the web-client/browser layer or as a fallback for `va-visual-check`: DOM/CSS/HTML, console/network, web-auth/publication, viewport/pixel rendering, browser extension, or browser-only I/O. In the test or report, record the VA steps, the reason for choosing browser/fallback, and the residual risk.

The runner is the same `run.mjs`. Mode is `test`:

```bash
node $RUN test <dir|file> [--url=<url>] [flags]
```

The current implementation in this repository is a single-context runner: `url`, discovery, hooks, `step`, `assert`, `--tags`, `--grep`, `--retry`, JSON/JUnit/Allure-smoke reports, and screenshots on failure are supported. The multi-user `contexts` below describes the target contract, but it is not yet enabled in `tools/web-test` runtime.

Tests live next to the project they cover, not inside the skill. Convention: `tests/` at the project root, `_hooks.mjs` and `webtest.config.mjs` at the suite root.

## When to use test, not exec

| Goal | Mode |
|------|------|
| Explore a form / prototype a step without a browser-specific reason | Vanessa/TestClient or platform TestClient MCP |
| Debug a DOM/CSS selector or browser-only behavior | `exec` (interactive web session) |
| Reproduce a bug as a failing test before the fix | `test` |
| Cover a feature with tests for the future | `test` |
| Run project regression on a new build | `test` |
| Create a screencast workflow | `exec` with `startRecording` |

Do not write `.test.mjs` for a one-off request. Do not run a regression suite through a chain of `exec` calls.

## Reconnaissance Before Writing Tests

Two levels, in order.

**1. Static reconnaissance - metadata.** Never invent identifiers. For each metadata object, run the corresponding skill: `/meta-info` (attributes/TЧ), `/form-info` (form layout), `/skd-info` (СКД), `/role-info` (permissions). If you cannot find it, ask.

**2. Live reconnaissance - interactive run.** For a non-trivial scenario, walk through it in `exec` mode before writing the test. Metadata tells you what exists; the live run tells you what actually happens. Capture from `getFormState()`: exact button names, table section names, required fields, and places where you really need to wait. Then transfer the working sequence into `*.test.mjs`, wrapping logical blocks in `step('...', async () => { ... })`.

## Suite Structure

**Each application has its own subfolder in `tests/`.** One repository can contain several isolated suites side by side - they must not share `_hooks.mjs` or `webtest.config.mjs`, because each one restores a different DB and publishes to a different URL.

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

Inside the application subfolder, organize by **feature**, not by metadata type. Numeric prefixes in folders and files define the run order. Entries starting with `_` or `.` are excluded from discovery (therefore `_hooks.mjs`, `_allure/` are not picked up as tests).

## Test File Anatomy

```js
export const name = 'Создание контрагента';       // required
export const tags = ['catalog', 'create'];        // optional, for filtering + Allure
export const timeout = 60000;                     // optional, default is 30000
// export const skip = 'ожидаем фикс #123';       // optional: true | string
// export const only = true;                      // debug only - do not commit
// export const context = 'manager';             // optional, one non-standard context
// export const contexts = ['clerk', 'manager']; // optional, multi-user test
// export const severity = 'critical';           // optional, overrides config

export async function setup(ctx) {
  // preparation before the test - runs before the default one
}

export async function teardown(ctx) {
  // cleanup after the test - always runs (even on failure)
}

export default async function(ctx) {
  const { navigateSection, openCommand, clickElement, fillFields,
          readTable, closeForm, getFormState,
          assert, step, log } = ctx;

  await step('Open the counterparty list', async () => {
    await navigateSection('Продажи');
    await openCommand('Контрагенты');
  });

  await step('Create a new counterparty', async () => {
    await clickElement('Создать');
    await fillFields({ 'Наименование': 'Тест ' + Date.now() });
    await clickElement('Записать и закрыть');
  });

  await step('Make sure the item appeared in the list', async () => {
    const t = await readTable();
    assert.tableHasRow(t, r => r['Наименование']?.startsWith('Тест '));
  });
}
```

**Step names should be in Russian and descriptive.** Step labels appear in the console output, JSON/JUnit, and Allure steps. Use the full action phrase (`'Создать нового контрагента'`), not a tag (`'create'`).

## ctx Contract

The runner injects all exports from `browser.mjs` (all 1C API functions) into `ctx`, plus testing utilities:

```js
step(name, fn)   // async wrapper. Records start/stop. Supports nesting.
log(...args)     // appends a line to ctx.testInfo output (goes into JSON/Allure attachment)
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
  // contexts: { clerk: { url, ... }, manager: { ... } },
  // primaryContext: 'clerk'
}
```

### ctx.testResult (only in afterEach)

```js
{
  status,    // 'passed' | 'failed'
  duration,  // ms
  attempts,  // number of attempts actually executed
  error,     // { message, step?, screenshot? } | null
  steps      // array of step results
}
```

## Assertions

All on `ctx.assert`. They throw an `AssertionError` with `.message`, `.actual`, `.expected`.

```js
// generic
assert.ok(value, msg?)
assert.equal(actual, expected, msg?)
assert.notEqual(actual, expected, msg?)
assert.deepEqual(actual, expected, msg?)
assert.includes(haystack, needle, msg?)
assert.match(string, regex, msg?)
await assert.throws(asyncFn, msg?)

// 1C-specific - work with getFormState() / readTable()
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
  retries: 0,              // retry for flaky tests
  screenshot: 'on-failure', // 'every-step' | 'off' | 'on-failure'
  record: false,

  severity: {
    critical: ['smoke', 'crud'],
    minor:    ['recording'],
  },
  defaultSeverity: 'normal',
};
```

CLI flags override the config. Use Latin context IDs plus Russian `displayName` values for ergonomics.

## _hooks.mjs

```js
// Infrastructure hooks - work without a browser
export async function prepare({ hookArgs, log, config }) {
  // Restore DB, publication, EPF build. Make it idempotent.
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

## Run

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

The `--retry=1` flag gives one retry attempt for a flaky test - especially useful for unstable environments with 1C licenses.

### Failure Screenshots

`screenshot: 'on-failure'` in `webtest.config.mjs` (or `--screenshot=on-failure`) automatically captures a PNG for each failed test. The screenshot path goes into `ctx.testResult.error.screenshot` and into the report. With `'every-step'`, a shot is taken after each `step()`.

## Ready-Made Patterns

### SKD Report

```js
await openCommand('Остатки товаров');
// Reset user settings (1C saves them between sessions)
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

### Multi-User Process

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
  // Free the 1C license
  await manager.closeContext('clerk');
}
```

### Parameterized Test

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

### Bug Reproduction (Failing Test)

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

Write it red first, give it to the user, fix it, rerun green.

## Test Severity

| Test Type | Recommendation |
|-----------|-----------------|
| Login + navigation, basic CRUD for covered entities | `critical` (+ `smoke` tag) |
| Posting documents, generating reports, end-to-end flows | `critical` |
| Edge cases for fields, formatting, optional flows | `normal` |
| Video recording / non-functional | `minor` |

Do not mark everything as `critical` - that loses signal in the Allure dashboard.

## Anti-Patterns

- **Sleep instead of waiting for state.** `wait(5)` after `openCommand` is normal; `wait(30)` because it is flaky is a bug.
- **Retry instead of understanding.** "Not found" twice means the data is missing or the name is wrong.
- **Binding to row position** (`rows[0]`) when the DB has shared data. Filter by a unique marker.
- **Manually resetting state in `afterEach`.** The runner already closes forms and hides errors.
- **Dependence on test order.** Each test should start from the desktop and prepare its own data.
- **`tags: ['smoke']` on a 90-second test.** Smoke means fast.

## Failure Analysis

1. Look at the JSON or Allure summary for `failed`.
2. For each failure: `error.message` + `error.step` + screenshot.
3. If there is `error.onecError.stack`, that is a 1C exception, check the platform trace.
4. Classify:
   - **Test bug** - wrong selector, wrong expectation, race condition -> fix the test.
   - **Application bug** -> report to the user with the name of the failing step and the stack.
   - **Environment instability** - Apache timeout, no license -> fix the hook idempotency.
5. After fixes, rerun only the failed files, then the full suite.

## Reference

- Browser base API: [SKILL.md](SKILL.md)
- Video recording + subtitles: [recording.md](recording.md)
