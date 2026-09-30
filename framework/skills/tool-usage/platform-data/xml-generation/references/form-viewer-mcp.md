# MCP `1c-form-viewer` — визуальный просмотр форм, макетов и СКД

Сторонний MCP-сервер `1c-form-viewer` из проекта BslEdit (лицензия MIT). Рисует управляемую форму (`Ext/Form.xml`), табличный макет (`Template.xml`) или схему компоновки данных в скрытом headless-браузере и отдаёт агенту PNG. 1С и сборка базы для этого не нужны. Происхождение, лицензия и что из него переносится в `xml-gen` описаны в [../../../../../docs/third-party-tools.md](../../../../../docs/third-party-tools.md).

## Роль в процессе

| Что | Чем | Статус |
|-----|-----|--------|
| Правка XML формы, макета, СКД | только `xml-gen` | единственный путь (`no-manual-xml-edit`) |
| Структурная проверка | `xml-gen validate` | основная проверка |
| Второе мнение по структуре формы | `validate_form` | дополнительная, не блокирующая |
| Первичная визуальная оценка без сборки | `open_preview` + `capture_preview` | быстрая грубая проверка |
| Проверка в реальном клиенте 1С | `va-visual-check` / тонкий клиент | окончательная, превью её не заменяет |

## Триггеры

1. **После генерации или правки формы** (`xml-gen form compile/edit/add-element`, `epf add-form`) и после `xml-gen validate --type form`:
   - `open_preview` с `path` = абсолютный путь к `Ext/Form.xml` и `audience="agent"`;
   - `capture_preview` — снимок; проверить порядок и вложенность групп и страниц, видимость, заголовки, наличие кнопок и колонок. Чек-лист оценки — `form-visual-requirements`;
   - `validate_form` — второе мнение рядом с `xml-gen validate`.
2. **После генерации или правки макета** (`xml-gen mxl compile`, `template add`) — `open_preview` + `capture_preview`. Дополнительно `list_markup` (области и параметры) и `validate_template` (висячие ссылки на форматы/шрифты, ячейки вне сетки).
3. **После генерации или правки СКД** (`xml-gen skd compile/edit`) — `open_preview` + `capture_preview`: наборы, поля, параметры, структура варианта.
4. Найти элемент на снимке — `preview operation=inspect` (дерево с id, строкой в XML, признаком visible), затем `preview operation=select/switch_tab/scroll` и `capture_preview scope=element`.

Ревьюер может использовать сервер для просмотра формы или макета, в том числе в режиме сравнения версий (`open_preview` с `base_revision`).

## Инструменты (в режиме только просмотра)

| Инструмент | Назначение |
|------------|------------|
| `open_preview` | открыть превью формы / макета / СКД; `base_revision` или `base_path` — режим сравнения |
| `preview` | действия с открытым превью: `inspect`, `switch_tab`, `select`, `scroll`, `reload`, `url`, `close` |
| `capture_preview` | PNG открытого превью (`scope`: viewport или элемент) |
| `list_form_elements` | дерево элементов формы (имя, вид) |
| `validate_form` | структурная проверка формы: дубли id и имён, компаньоны, пути данных, ссылки на команды, объявление префиксов пространств имён |
| `list_markup` | области и параметры макета |
| `validate_template` | структурная проверка макета |
| `convert_xlsx_to_template` | **пишет файл** (`Template.xml` из xlsx); флагами сервера не отключается — запрещать на уровне клиента (см. «Подключение») |

## Ограничения

- **Не для правки.** Сервер запускать только с `--no-form-edit-tools --no-template-edit-tools`, тогда `edit_form`/`edit_template` не публикуются. Правка через них ставит узлы не в канонический порядок, если нет локального словаря свойств.
- **`validate_form` не заменяет `xml-gen validate`.** Он не ловит часть XDTO-дефектов, на которых падает загрузка: регистр имён событий, позицию корневого `<Title>`, отсутствие `<Type>` у кнопки, детей контейнера без `<ChildItems>`, `CommandName` без префикса `Form.Command.`, опечатки в регистре имён свойств, несовпадение версии формата. `ok:true` не доказывает, что форма загрузится.
- **Превью — не клиент 1С.** Данных нет. Стандартные колонки и команды достраиваются условно, у таблиц рисуются плашки поиска. В сложных формах разметка может наезжать. Не делать вывод «в клиенте так же» без проверки через `va-visual-check`.
- Только `audience="agent"`: `audience="user"` пытается открыть браузер пользователя (`xdg-open`), в контейнере это не работает.
- Пути — **абсолютные** внутри корня `--root`. Относительный путь считается от рабочего каталога сервера, файлы вне корня отклоняются. `--allow-any-path` не использовать.
- Скорость: `open_preview` занимает 1–2 с, `capture_preview` — до 1 с, `validate_form` работает без браузера за миллисекунды.

## Подключение

Сервер собирается из исходников BslEdit на зафиксированном коммите. Установка описана в `docs/tools-install/1c-form-viewer.md` репозитория фреймворка. Параметры запуска:

```text
command: <обёртка> -> node <BslEdit>/packages/1c-form-viewer/dist/mcp-server.js
args:    --stdio --root <корень проекта> --no-form-edit-tools --no-template-edit-tools
env:     ONE_C_FORM_VIEWER_CHROMIUM=<путь к системному Chrome/Chromium>
```

- Claude Code: запись `1c-form-viewer` в `.mcp.json` проекта и `permissions.deny: ["mcp__1c-form-viewer__convert_xlsx_to_template"]` в настройках.
- Codex: `[mcp_servers.1c-form-viewer]` с `startup_timeout_sec = 20`, `tool_timeout_sec = 120`, `disabled_tools = ["convert_xlsx_to_template"]`.
- Сервер недоступен — сообщить пользователю. Не запускать `mcp-server.js` вручную с флагами правки и не вызывать его в обход подключённого MCP.
