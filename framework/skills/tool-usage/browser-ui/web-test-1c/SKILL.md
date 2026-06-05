---
name: web-test-1c
description: "Use for автоматизации действий в 1С через браузер (навигация по разделам, заполнение форм, чтение таблиц и отчётов, фильтрация списков). Helps писать browser-тесты 1С на семантическом слое без знания DOM-деталей платформы."
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
| `getFormState()` | Все поля, кнопки, вкладки, таблицы, ошибки одним вызовом |
| `readTable({ maxRows?, offset?, table? })` | Таблица с пагинацией: `{ columns, rows, total }`. `table` выбирает сетку по имени |
| `readSpreadsheet()` | Отчёт (SpreadsheetDocument) после «Сформировать». Поддерживает text-only и отчёты с числовыми шапками |

`getFormState()` возвращает: **fields** (имя, значение, actions, required), **table** (back-compat: первая сетка), **tables[]** (все видимые сетки: `{name, columns, rowCount, label}`), **openForms[]**, **formCount**, **modal**, **openTabs[]**, **navigation** (панель навигации формы), **reportSettings** (human-readable настройки СКД), **errors.stateText** (info-bar SpreadsheetDocument), **errorModal**, **confirmation**.

Строки дерева помечаются `_kind: 'group'|'parent'`, `_tree: 'expanded'|'collapsed'`, `_level`, `_selected`.

## API — Действия

| Функция | Описание |
|---------|----------|
| `fillFields({ name: value })` | Заполнение полей (fuzzy match, автотип: справочник/чекбокс/радио) |
| `fillField(name, value)` | Одиночный аналог `fillFields` |
| `selectValue(field, search, opts?)` | Выбор из справочника (выпадающий / форма выбора) |
| `clickElement(text, opts?)` | Клик по кнопке/ссылке/строке. `opts`: `dblclick`, `table` (scope командной панели конкретной сетки), `toggle`/`expand` (дерево), `modifier: 'ctrl'\|'shift'` (multi-select), `timeout` |
| `clickElement(target, opts?)` с `{row, column}` | Drill-down в SpreadsheetDocument: `{row: 0, column: 'К6'}`, `{row: {'К1': 'Материалы'}, column: 'К6'}`, `{row: 'totals', column: 'К6'}` |
| `fillTableRow(fields, opts)` | Заполнение строки ТЧ (`{ tab, add, row, table }`) |
| `deleteTableRow(row, { tab?, table? })` | Удаление строки |
| `filterList(text, opts?)` / `unfilterList()` | Фильтрация списков (простой / `{ field }`) |
| `closeForm({ save? })` | Закрытие с обработкой подтверждения |
| `switchTab(name)` | Переключение вкладки формы или открытой вкладки (tab bar) |
| `navigateLink(url)` | Открытие объекта по метаданным (Shift+F11), поддержка русских имён |
| `openFile(path)` | Открытие EPF/ERF через File→Open с обработкой security dialog |

## API — Утилиты и запись

| Функция | Описание |
|---------|----------|
| `screenshot()` | PNG скриншот |
| `wait(seconds)` | Ожидание + form state |
| `getPage()` | Playwright Page (нестандартные сценарии) |
| `startRecording(path, opts?)` / `stopRecording()` | Видеозапись (можно отключить на уровне CLI флагом `--no-record`) |
| `addNarration(videoPath, opts?)` | Наложение TTS-озвучки (node-edge-tts) |
| `showCaption(text, opts?)` / `hideCaption()` | Подпись поверх видео |
| `showTitleSlide(text)` / `hideTitleSlide()` | Титульный слайд |
| `showImage(path, opts?)` / `hideImage()` | Оверлей картинки |
| `highlight(text, opts?)` / `unhighlight()` / `setHighlight(on)` | Подсветка элементов |
| `fetchErrorStack(formNum, hasReport)` | Достать call stack из модального окна ошибки 1С |
| `getSections()` / `getCommands()` | Панель разделов |

## Важные особенности

- **Headed mode** — 1С требует видимый браузер, без headless
- **Ctrl+V** вместо `page.fill()` — 1С реагирует только на trusted events
- **Fuzzy matching** — exact > startsWith > includes; ё→е и \u00a0→пробел автоматически
- **Graceful logout** — `stop` → POST `/e1cib/logout` (освобождает лицензию)
- **Автодетект ошибок** — модальные, balloon, подтверждения включаются в ответ; при модальной ошибке автоматически подтягивается stack (`fetchErrorStack`) и скриншот
- **Multi-table** — если на форме несколько сеток, `tables[]` перечисляет все; передавай `{ table: 'Исходящие' }` в `readTable`/`clickElement`/`fillTableRow`/`deleteTableRow` чтобы указать нужную
- **Tree nodes** — по умолчанию клик выбирает, `{expand: true}` раскрывает/сворачивает
- **Multi-select** — `clickElement(..., { modifier: 'ctrl' })` или `'shift'`
- **1C browser extension** — если установлена в Chrome/Edge, автоматически подхватывается; можно переопределить через `extensionPath` в `.v8-project.json`
- **Макс. 2 попытки** — после двух неудач — сообщи пользователю

## Горячие клавиши 1С

| Клавиша | Контекст | Действие |
|---------|----------|----------|
| `F8` | Ссылочное поле | Создать новый элемент |
| `Shift+F4` | Ссылочное поле | Очистить значение |
| `F4` | Ссылочное поле | Открыть форму выбора |
| `Alt+F` | Список/таблица | Расширенный поиск |

## Регресс-движок

Когда нужно покрыть 1С-решение серией автоматизированных тестов — запуск нескольких `.test.mjs`-сценариев подряд, агрегация результатов, retry на flaky, скриншоты на падениях, отчёты Allure/JUnit — переходи к режиму `test`. Подробнее: [regress.md](regress.md).

По умолчанию используй `run`/`exec` для одноразовой автоматизации — `test` — специализированный режим для проектного покрытия.

## Запись видео и субтитры

Два пути в порядке приоритета:

**1. Vanessa Automation (рекомендуется)** — если сценарий описан в `.feature`-файле. Vanessa пишет видео прогона и генерирует субтитры из шагов Gherkin из коробки. Настраивается через профиль (`ЗаписыватьВидео`, `ГенерироватьСубтитры`, `ПутьКВидеозаписям`). Использовать для демо-видео команде, документирования бизнес-процессов.

**2. Playwright fallback** — когда Vanessa недоступна, нужен headless или сценарий написан на JS. API: `startRecording` / `stopRecording` / `showCaption` / `addNarration` (TTS через node-edge-tts, OpenAI или ElevenLabs). Требует ffmpeg.

Подробнее: [recording.md](recording.md) — сравнительная таблица, параметры профиля Vanessa, полный API Playwright-записи, примеры, устранение неполадок.

---
depends_on: []
requires:
  - tools
metadata:
  category: 1c-development
  version: "1.2"
---
