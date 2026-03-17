---
name: web-test-1c
description: Автоматизация 1С через веб-клиент — навигация по разделам, заполнение форм, чтение таблиц и отчётов, фильтрация списков. Используй когда нужно протестировать, проверить или автоматизировать действия в 1С через браузер.
---

# web-test-1c — Автоматизация 1С веб-клиента

Семантический слой поверх Playwright для DOM 1С:Предприятие веб-клиента.

## Установка

```bash
cd tools/web-test && npm install
```

Node.js 18+. `npm install` скачает Playwright и Chromium.

## Быстрый старт

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

URL из `.v8-project.json` (поле `webUrl`) или задаётся явно.

## Режимы работы

```bash
node $RUN run <url> script.js           # автономный — выполняет и завершается
node $RUN start <url>                   # интерактивный — запуск сессии
cat <<'SCRIPT' | node $RUN exec -       # выполнить скрипт в сессии
  const form = await getFormState();
SCRIPT
node $RUN shot result.png               # скриншот
node $RUN stop                          # logout + закрытие (освобождает лицензию)
```

## API — Навигация

| Функция | Описание |
|---------|----------|
| `navigateSection(name)` | Перейти в раздел (fuzzy match), возвращает `{ navigated, sections, commands }` |
| `openCommand(name)` | Открыть команду из панели функций → form state |
| `navigateLink(url)` | Перейти по метаданным (Shift+F11), поддержка русских имён |
| `openFile(path)` | Открыть EPF/ERF, обработка диалога безопасности |

## API — Чтение

| Функция | Описание |
|---------|----------|
| `getFormState()` | Все поля, кнопки, вкладки, таблица, ошибки одним вызовом |
| `readTable({ maxRows?, offset? })` | Таблица с пагинацией: `{ columns, rows, total }` |
| `readSpreadsheet()` | Отчёт (SpreadsheetDocument) после «Сформировать» |

`getFormState()` возвращает: **fields** (имя, значение, actions, required), **table** (сводка), **reportSettings** (фильтры СКД), **errorModal**, **confirmation**.

## API — Действия

| Функция | Описание |
|---------|----------|
| `fillFields({ name: value })` | Заполнение полей (fuzzy match, автотип: справочник/чекбокс/радио) |
| `selectValue(field, search, opts?)` | Выбор из справочника (выпадающий / форма выбора) |
| `clickElement(text, { dblclick? })` | Клик по кнопке/ссылке/строке (dblclick открывает элемент) |
| `fillTableRow(fields, opts)` | Заполнение строки табличной части (`{ tab, add }`) |
| `deleteTableRow(row, { tab? })` | Удаление строки |
| `filterList(text, opts?)` / `unfilterList()` | Фильтрация списков (простой / `{ field }`) |
| `closeForm({ save? })` | Закрытие с обработкой подтверждения |
| `switchTab(name)` | Переключение вкладки |

## API — Утилиты

| Функция | Описание |
|---------|----------|
| `screenshot()` | PNG скриншот |
| `wait(seconds)` | Ожидание + form state |
| `getPage()` | Playwright Page (нестандартные сценарии) |
| `startRecording(path)` / `stopRecording()` | Видеозапись |
| `getSections()` / `getCommands()` | Панель разделов |

## Важные особенности

- **Headed mode** — 1С требует видимый браузер, без headless
- **Ctrl+V** вместо `page.fill()` — 1С реагирует только на trusted events
- **Fuzzy matching** — exact > startsWith > includes; ё→е и \u00a0→пробел автоматически
- **Graceful logout** — `stop` → POST `/e1cib/logout` (освобождает лицензию)
- **Автодетект ошибок** — модальные, balloon, подтверждения включаются в ответ
- **Макс. 2 попытки** — после двух неудач — сообщи пользователю

## Горячие клавиши 1С

| Клавиша | Контекст | Действие |
|---------|----------|----------|
| `F8` | Ссылочное поле | Создать новый элемент |
| `Shift+F4` | Ссылочное поле | Очистить значение |
| `F4` | Ссылочное поле | Открыть форму выбора |
| `Alt+F` | Список/таблица | Расширенный поиск |

---
depends_on: []
requires:
  - tools
metadata:
  category: 1c-development
  version: "1.0"
---
