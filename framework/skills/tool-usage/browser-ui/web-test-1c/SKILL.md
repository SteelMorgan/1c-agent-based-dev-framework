---
name: web-test-1c
description: Автоматизация 1С через веб-клиент — навигация по разделам, заполнение форм, чтение таблиц и отчётов, фильтрация списков. Используй когда нужно протестировать, проверить или автоматизировать действия в 1С через браузер.
---

# web-test-1c — Автоматизация 1С веб-клиента

Семантический слой автоматизации поверх Playwright, заточенный под DOM 1С:Предприятие веб-клиента. ~5100 строк JS с 40+ API-функциями.

## Установка (первый раз)

```bash
cd tools/web-test && npm install
```

Требуется Node.js 18+. `npm install` скачает Playwright и Chromium.

## Быстрый старт

```bash
RUN="tools/web-test/run.mjs"

# Одноразовый запуск: открыть → выполнить → закрыть
cat <<'SCRIPT' | node $RUN run http://erp-server:8080/mydb -
await navigateSection('Продажи');
await openCommand('Заказы клиентов');
await clickElement('Создать');
await fillFields({ 'Клиент': 'Альфа' });
await clickElement('Провести и закрыть');
SCRIPT
```

## URL публикации

URL берётся из `.v8-project.json` (поле `webUrl` у базы) или задаётся явно. Apache может быть на удалённом сервере — web-test подключается по любому HTTP URL.

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

## Режимы работы

### Автономный (для полных сценариев)

```bash
node $RUN run <url> script.js   # выполняет и завершается
```

### Интерактивный (пошаговая разработка)

```bash
# 1. Запустить сессию (run_in_background=true)
node $RUN start <url>

# 2. Выполнять скрипты
cat <<'SCRIPT' | node $RUN exec -
const form = await getFormState();
console.log(JSON.stringify(form, null, 2));
SCRIPT

# 3. Скриншот
node $RUN shot result.png

# 4. Остановить (logout + закрытие — освобождает лицензию 1С)
node $RUN stop
```

## API — Навигация

### `navigateSection(name)` → `{ navigated, sections, commands }`

Перейти в раздел (fuzzy match). Возвращает список команд раздела.

```js
await navigateSection('Продажи');
```

### `openCommand(name)` → form state

Открыть команду из панели функций.

```js
const form = await openCommand('Заказы клиентов');
```

### `navigateLink(url)` → form state

Перейти по метаданным через Shift+F11. Поддержка русских имён.

```js
await navigateLink('Документ.ЗаказКлиента');
await navigateLink('Справочник.Контрагенты');
```

### `openFile(path)` → form state

Открыть EPF/ERF файл. Обрабатывает диалог безопасности автоматически.

## API — Чтение состояния формы

### `getFormState()` → `{ fields, buttons, tabs, table, filters, reportSettings?, errorModal?, confirmation? }`

Основной способ понять что на экране. Одним вызовом — все поля, кнопки, вкладки, таблица, ошибки.

**fields** — имя, значение, label, actions (select/clear/open), required
**table** — `{ name, columns, rowCount }` (сводка; для данных — `readTable()`)
**reportSettings** — фильтры СКД с человекочитаемыми именами
**errorModal** — модальная ошибка 1С (если есть)
**confirmation** — Да/Нет диалог (если есть)

## API — Чтение данных

### `readTable({ maxRows?, offset? })` → `{ columns, rows, total }`

Чтение таблицы с пагинацией. Строки: `{ columnName: value }`.

```js
const t = await readTable({ maxRows: 50 });
const page2 = await readTable({ maxRows: 50, offset: 50 });
```

### `readSpreadsheet()` → `{ title?, headers?, data?, totals?, total }`

Чтение отчёта (SpreadsheetDocument) после «Сформировать».

```js
await clickElement('Сформировать');
await wait(5);
const report = await readSpreadsheet();
```

## API — Действия

### `fillFields({ name: value })` → `{ filled, form }`

Заполнение полей формы по имени (fuzzy match). Автоопределение типа:
- Справочник → Ctrl+V + typeahead
- Чекбокс → toggle
- Радиокнопка → fuzzy match

**СКД-фильтры**: чекбокс включается автоматически:
```js
await fillFields({ 'Склад': 'Основной', 'Номенклатура': 'Бумага' });
```

### `selectValue(field, search, opts?)` → form state

Выбор значения из справочника через выпадающий список или форму выбора.

```js
await selectValue('Организация', 'Конфетпром');
// Составной тип:
await selectValue('Документ', '0000-000601', { type: 'Реализация (акт' });
```

### `clickElement(text, { dblclick? })` → form state

Клик по кнопке, гиперссылке, строке таблицы (fuzzy match). Двойной клик открывает элемент.

```js
await clickElement('Создать');
await clickElement('КП00-000227', { dblclick: true }); // открыть документ
```

### `fillTableRow(fields, opts)` → form state

Заполнение строки табличной части. Tab-навигация, определение типа ячейки.

```js
await fillTableRow(
  { 'Номенклатура': 'Бумага', 'Количество': '10', 'Цена': '100' },
  { tab: 'Товары', add: true }
);
```

### `deleteTableRow(row, { tab? })` → form state

### `filterList(text, opts?)` / `unfilterList({ field? })` → form state

Фильтрация списков: простой поиск или расширенный (по конкретному полю).

```js
await filterList('КП00-000018');
await filterList('Мишка', { field: 'Наименование' });
await unfilterList();
```

### `closeForm({ save? })` → form state

Закрытие формы с обработкой подтверждения (Да/Нет).

### `switchTab(name)` → form state

## API — Утилиты

| Функция | Описание |
|---------|----------|
| `screenshot()` | PNG скриншот |
| `wait(seconds)` | Ожидание + состояние формы |
| `getPage()` | Playwright Page (для нестандартных сценариев) |
| `startRecording(path)` / `stopRecording()` | Видеозапись (CDP screencast → ffmpeg) |
| `showCaption(text)` / `hideCaption()` | Текстовый оверлей |
| `addNarration(videoPath, opts)` | TTS-озвучка (Edge TTS / OpenAI) |
| `getSections()` / `getCommands()` | Чтение панели разделов |
| `getPageState()` | Разделы + открытые вкладки |

## Типовые сценарии

### Создать и провести документ

```js
await navigateSection('Продажи');
await openCommand('Заказы клиентов');
await clickElement('Создать');
await fillFields({ 'Организация': 'Конфетпром', 'Контрагент': 'Альфа' });
await fillTableRow({ 'Номенклатура': 'Бумага', 'Количество': '10' }, { tab: 'Товары', add: true });
await clickElement('Провести и закрыть');
```

### Сформировать и прочитать отчёт

```js
await fillFields({ 'Склад': 'Основной склад' });
await clickElement('Сформировать');
await wait(5);
const report = await readSpreadsheet();
console.log('Строк:', report.data?.length);
```

### Найти и открыть элемент из списка

```js
await filterList('Конфетпром');
await clickElement('Конфетпром ООО', { dblclick: true });
await closeForm();
await unfilterList();
```

## Важные особенности

- **Headed mode** — 1С требует видимый браузер, без headless
- **Ctrl+V** вместо `page.fill()` — 1С реагирует только на trusted events (автокомплит, подбор)
- **Fuzzy matching** — все поиски по именам: exact > startsWith > includes
- **ё→е нормализация** и **\u00a0→пробел** — автоматически во всех сравнениях
- **Graceful logout** — `stop` отправляет POST `/e1cib/logout` для освобождения лицензии 1С
- **Автоопределение ошибок** — модальные ошибки, balloon, подтверждения детектируются и включаются в ответ
- **Макс. 2 попытки** — если действие не удалось дважды, сообщи пользователю, не зацикливайся

## Горячие клавиши 1С

| Клавиша | Контекст | Действие |
|---------|----------|----------|
| `F8` | Ссылочное поле | Создать новый элемент |
| `Shift+F4` | Ссылочное поле | Очистить значение |
| `F4` | Ссылочное поле | Открыть форму выбора |
| `Alt+F` | Список/таблица | Расширенный поиск |

## См. также

- [playwright](../playwright/) — generic Playwright для не-1С веб-приложений
- [playwright-interactive](../playwright-interactive/) — интерактивная сессия Playwright
- [visual-check](../visual-check/) — визуальная проверка форм

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
