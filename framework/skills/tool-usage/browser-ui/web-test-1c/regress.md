# Регресс-движок Playwright

Используй этот документ когда нужно покрыть 1С-решение автоматизированными регрессионными тестами: запускать несколько .feature / JSON-сценариев подряд, агрегировать результаты, получать отчёт fail/pass, настраивать retry на flaky-тесты, сохранять скриншоты на падения. Для разовой автоматизации (один сценарий) оставайся в режимах `run`/`exec` из SKILL.md.

Для 1С UI это не default-регресс. Если тест проверяет форму, команду, поле, ТЧ, клиентский обработчик или бизнес-поток без браузерной специфики — сначала пиши/запускай Vanessa `.feature` через TestClient. Playwright-регресс через `web-test-1c` выбирай для web-client/browser-слоя или как fallback по `va-visual-check`: DOM/CSS/HTML, console/network, web-auth/publication, viewport/pixel rendering, browser extension или browser-only I/O. В тесте или отчёте фиксируй VA-шаги, причину выбора browser/fallback и остаточный риск.

Раннер — тот же `run.mjs`. Режим — `test`:

```bash
node $RUN test <dir|file> [--url=<url>] [флаги]
```

Текущая реализация в этом репозитории — single-context runner: `url`, discovery, hooks, `step`, `assert`, `--tags`, `--grep`, `--retry`, JSON/JUnit/Allure-smoke отчёты и скриншоты на падении поддерживаются. Multi-user `contexts` ниже описывает целевой контракт, но пока не включён в runtime `tools/web-test`.

Тесты живут рядом с проектом, который покрывают (не внутри навыка). Соглашение: `tests/` в корне проекта, `_hooks.mjs` и `webtest.config.mjs` в корне сьюта.

## Когда test, а не exec

| Цель | Режим |
|------|-------|
| Исследовать форму/прототипировать шаг без browser-specific причины | Vanessa/TestClient или платформенный TestClient MCP |
| Отладить DOM/CSS selector или browser-only поведение | `exec` (интерактивная web-сессия) |
| Воспроизвести баг как падающий тест перед фиксом | `test` |
| Покрыть фичу тестами на будущее | `test` |
| Запустить регресс проекта на новой сборке | `test` |
| Сделать скринкаст-воркфлоу | `exec` с `startRecording` |

Не пиши `.test.mjs` для одноразового запроса. Не гоняй регресс-сьют через цепочку `exec`-вызовов.

## Разведка перед написанием тестов

Два уровня — по порядку.

**1. Статическая разведка — метаданные.** Никогда не изобретай идентификаторы. Для каждого объекта метаданных запусти соответствующий навык: `/meta-info` (реквизиты/ТЧ), `/form-info` (макет формы), `/skd-info` (СКД), `/role-info` (права). Не нашёл — спроси.

**2. Живая разведка — интерактивный прогон.** Для нетривиального сценария пройди его в `exec`-режиме до написания теста. Метаданные скажут что существует; живой прогон — что реально происходит. Захвати из `getFormState()`: точные имена кнопок, имена секций таблиц, обязательные поля, места реального ожидания. Затем перенеси рабочую последовательность в `*.test.mjs`, оборачивая логические блоки в `step('...', async () => { ... })`.

## Структура сьюта

**Каждое приложение — своя подпапка в `tests/`.** Один репозиторий может содержать несколько изолированных сьютов рядом — они не должны делить `_hooks.mjs` или `webtest.config.mjs`, потому что каждый восстанавливает разную БД и публикует на другой URL.

```
tests/
  <app-name>/
    _hooks.mjs
    webtest.config.mjs
    _allure/          # опционально: categories.json, executor.json
    01-login/
    02-counterparties/
    03-sales/
    ...
  <another-app>/      # второе решение, полностью изолировано
```

Внутри подпапки приложения — организация по **фиче**, а не по виду метаданных. Числовые префиксы в папках и файлах задают порядок запуска. Записи, начинающиеся с `_` или `.`, исключаются из обнаружения (поэтому `_hooks.mjs`, `_allure/` не попадают в тесты).

## Анатомия тест-файла

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

**Имена шагов — по-русски, описательно.** Лейблы шагов попадают в вывод консоли, JSON/JUnit и Allure-шаги. Используй полную фразу-действие (`'Создать нового контрагента'`), а не тег (`'create'`).

## Контракт ctx

Раннер инжектирует в `ctx` все экспорты `browser.mjs` (все API-функции 1С), плюс утилиты тестирования:

```js
step(name, fn)   // async-обёртка. Записывает старт/стоп. Поддерживает вложенность.
log(...args)     // добавляет строку в вывод ctx.testInfo (идёт в JSON/Allure attachment)
assert.*         // см. «Утверждения» ниже
```

### ctx.testInfo (всегда установлен, только чтение)

```js
{
  name,          // 'Навигация по разделам' (с подставленными параметрами)
  file,          // '01-navigation.test.mjs' (basename)
  tags,          // ['nav', 'smoke']
  timeout,       // ms
  attempt,       // 1..maxAttempts
  maxAttempts,   // 1 + retry
  param,         // { ... } | undefined (только когда export const params задан)
  // planned после переноса modular engine:
  // contexts: { clerk: { url, ... }, manager: { ... } },
  // primaryContext: 'clerk'
}
```

### ctx.testResult (только в afterEach)

```js
{
  status,    // 'passed' | 'failed'
  duration,  // ms
  attempts,  // фактически выполнено попыток
  error,     // { message, step?, screenshot? } | null
  steps      // массив step-результатов
}
```

## Утверждения

Все на `ctx.assert`. Бросают `AssertionError` с `.message`, `.actual`, `.expected`.

```js
// generic
assert.ok(value, msg?)
assert.equal(actual, expected, msg?)
assert.notEqual(actual, expected, msg?)
assert.deepEqual(actual, expected, msg?)
assert.includes(haystack, needle, msg?)
assert.match(string, regex, msg?)
await assert.throws(asyncFn, msg?)

// специфика 1С — работают с getFormState() / readTable()
assert.formHasField(state, 'Контрагент', msg?)
assert.formTitle(state, expected, msg?)
assert.tableHasRow(table, predicate, msg?)   // predicate: object (частичное) или fn(row) => bool
assert.tableRowCount(table, expected, msg?)
assert.noErrors(state, msg?)
```

## webtest.config.mjs

```js
export default {
  // Один контекст:
  url: 'http://localhost:9191/myapp/ru_RU',

  // ИЛИ несколько контекстов:
  // contexts: {
  //   clerk:   { url: '...', displayName: 'Кладовщик' },
  //   manager: { url: '...', displayName: 'Менеджер' },
  // },
  // defaultContext: 'clerk',

  timeout: 30000,
  retries: 0,              // retry на flaky-тесты
  screenshot: 'on-failure', // 'every-step' | 'off' | 'on-failure'
  record: false,

  severity: {
    critical: ['smoke', 'crud'],
    minor:    ['recording'],
  },
  defaultSeverity: 'normal',
};
```

CLI-флаги переопределяют конфиг. Используй латинские ID контекстов + русские `displayName` для эргономики.

## _hooks.mjs

```js
// Инфра-хуки — работают без браузера
export async function prepare({ hookArgs, log, config }) {
  // Восстановление БД, публикация, сборка EPF. Делай идемпотентным.
}
export async function cleanup({ log, config }) { /* опционально */ }

// Уровень тестов — работают с browser ctx
export async function beforeAll(ctx) { }
export async function afterAll(ctx)  { }
export async function beforeEach(ctx) { }
export async function afterEach(ctx)  { }

// Per-context
export async function afterOpenContext(ctx, name, spec)   { }
export async function beforeCloseContext(ctx, name, spec) { }
```

Аргументы хуков передавай после `--`:

```bash
node $RUN test tests/<app-name>/ --bail -- --rebuild-stand --data=demo
```

## Запуск

```bash
node $RUN test tests/<app-name>/                     # весь сьют приложения
node $RUN test tests/<app-name>/03-goods-receipt/    # одна фича-папка
node $RUN test tests/<app-name>/02-cnt/01-create.test.mjs  # один файл
node $RUN test tests/<app-name>/ --tags=smoke        # по тегу
node $RUN test tests/<app-name>/ --grep='накладн'    # по имени (regex)
node $RUN test tests/<app-name>/ --bail --retry=1    # стоп на первом падении + 1 retry
node $RUN test tests/<app-name>/ --format=allure --report-dir=allure-results
node $RUN test tests/<app-name>/ -- --rebuild-stand  # hookArgs
```

Флаг `--retry=1` даёт одну повторную попытку на flaky-тест — особенно полезно для нестабильных окружений с 1С лицензиями.

### Скриншоты на падениях

`screenshot: 'on-failure'` в `webtest.config.mjs` (или `--screenshot=on-failure`) автоматически снимает PNG при каждом упавшем тесте. Путь к скриншоту попадает в `ctx.testResult.error.screenshot` и в отчёт. При `'every-step'` снимок делается после каждого `step()`.

## Готовые паттерны

### СКД-отчёт

```js
await openCommand('Остатки товаров');
// Сбрасываем пользовательские настройки (1С их сохраняет между сессиями)
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

### Multi-user процесс

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

### Параметризованный тест

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

### Воспроизведение бага (падающий тест)

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

Пиши красным сначала, отдавай пользователю, фикси, перезапускай зелёным.

## Тяжесть тестов (severity)

| Тип теста | Рекомендация |
|-----------|--------------|
| Логин + навигация, базовый CRUD по покрытым сущностям | `critical` (+ тег `smoke`) |
| Проведение документов, генерация отчётов, сквозные процессы | `critical` |
| Краевые случаи по полям, форматирование, опциональные потоки | `normal` |
| Запись видео / нефункциональные | `minor` |

Не присваивай всему `critical` — это теряет сигнал в Allure-дашборде.

## Антипаттерны

- **sleep вместо ожидания состояния.** `wait(5)` после `openCommand` — нормально; `wait(30)` потому что флучит — баг.
- **Retry вместо понимания.** «Не найдено» дважды = данных нет или имя неверное.
- **Привязка к позиции строки** (`rows[0]`) когда в БД общие данные. Фильтруй по уникальному маркеру.
- **Сброс состояния вручную в `afterEach`.** Раннер уже закрывает формы и скрывает ошибки.
- **Зависимость от порядка тестов.** Каждый тест должен стартовать с десктопа и готовить свои данные.
- **`tags: ['smoke']` на 90-секундном тесте.** Smoke — значит быстро.

## Разбор падений

1. Посмотри сводку JSON или Allure по `failed`.
2. Для каждого падения: `error.message` + `error.step` + скриншот.
3. Если есть `error.onecError.stack` — это исключение 1С, смотри трассировку платформы.
4. Классифицируй:
   - **Баг теста** — неверный селектор, неверное ожидание, гонка → фикси тест.
   - **Баг приложения** → сообщи пользователю с именем падающего шага и стеком.
   - **Нестабильность стенда** — таймаут Apache, нет лицензии → фикси идемпотентность hook.
5. После фиксов перезапусти только упавшие файлы, потом полный сьют.

## Справка

- Базовый API браузера: [SKILL.md](SKILL.md)
- Запись видео + субтитры: [recording.md](recording.md)
