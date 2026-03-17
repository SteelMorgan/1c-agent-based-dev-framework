---
name: form-dsl
description: JSON DSL для генерации управляемых форм 1С с UI-элементами, реквизитами и командами. Используй при form compile и редактировании форм через xml-gen-cli.
---

# Form DSL

JSON DSL для генерации управляемых форм 1С.

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать форму с нуля (реквизиты, элементы, команды) | `form compile` с JSON DSL |
| Нужно добавить реквизит в существующую форму | `form add-attribute` → [xml-gen-cli](../xml-gen-cli/) |
| Нужно добавить UI-элемент (поле, кнопку, группу) | `form add-element` → [xml-gen-cli](../xml-gen-cli/) |
| Нужно добавить команду формы | `form add-command` → [xml-gen-cli](../xml-gen-cli/) |
| Нужно удалить/переместить элемент | `form remove-element`, `form move-element` → [xml-gen-cli](../xml-gen-cli/) |
| Нужно анализировать структуру существующей формы | `form info <Form.xml>` |

## Команда compile

```bash
xml-gen form compile [--format designer|edt] <input.json> <output.xml>
```

**Редактирование существующих форм** (add-attribute, add-element, add-command, remove-element, move-element) — см. [xml-gen-cli](../xml-gen-cli/)

## Команда info

Парсинг Form.xml — элементы, реквизиты, команды, события.

```bash
xml-gen form info <Form.xml>
```

## Структура DSL

### Минимальная форма

```json
{
  "attributes": [],
  "elements": []
}
```

### Реквизиты (attributes)

```json
{
  "name": "ИмяРеквизита",
  "type": "тип",
  "title": "Заголовок"
}
```

**Поддерживаемые типы:** `string`, `string(N)`, `number`, `number(D,F)`, `boolean`, `date`, `uuid`, `CatalogRef.Name`, `DocumentRef.Name`, `ValueTable`

### UI-элементы (elements)

| DSL type | XML тип | Описание |
|----------|---------|----------|
| `input` | InputField | Поле ввода |
| `group` | UsualGroup | Обычная группа |
| `table` | Table | Таблица |
| `button` | Button | Кнопка |
| `label` | LabelDecoration | Декорация-надпись |
| `checkbox` | CheckBoxField | Поле флажка |
| `pages` | Pages | Страницы |
| `page` | Page | Страница |

**Пример input:**
```json
{"type": "input", "name": "Наименование", "dataPath": "Наименование", "title": "Наименование товара"}
```

**Пример group с children:**
```json
{
  "type": "group",
  "name": "ГруппаОсновное",
  "group": "Vertical",
  "children": [
    {"type": "input", "name": "Наименование", "dataPath": "Наименование"},
    {"type": "input", "name": "Количество", "dataPath": "Количество"}
  ]
}
```

**Пример table:**
```json
{
  "type": "table",
  "name": "ТаблицаТоваров",
  "dataPath": "Товары",
  "columns": [
    {"type": "input", "name": "Номенклатура", "dataPath": "Товары.Номенклатура"},
    {"type": "input", "name": "Количество", "dataPath": "Товары.Количество"}
  ]
}
```

### Команды (commands)

```json
{
  "commands": [
    {"name": "Сохранить", "action": "Save", "title": "Сохранить"},
    {"name": "Закрыть", "action": "Close", "title": "Закрыть"}
  ]
}
```

### События (events)

```json
{
  "events": {
    "onCreateAtServer": "ПриСозданииНаСервере",
    "onOpen": "ПриОткрытии",
    "onClose": "ПриЗакрытии",
    "beforeClose": "ПередЗакрытием"
  }
}
```

> ⚠️ **Клиент/серверный контекст — критично для 1С.**
> DSL задаёт только **имя процедуры**, директиву компилятора нужно проставить в модуле формы вручную:
>
> | Событие DSL | Имя процедуры | Директива в модуле формы |
> |-------------|---------------|--------------------------|
> | `onCreateAtServer` | `ПриСозданииНаСервере` | `&НаСервере` |
> | `onOpen` | `ПриОткрытии` | `&НаКлиенте` |
> | `onClose` | `ПриЗакрытии` | `&НаКлиенте` |
> | `beforeClose` | `ПередЗакрытием` | `&НаКлиенте` |
>
> Код инициализации данных формы пишется в `ПриСозданииНаСервере` (`&НаСервере`).
> Код работы с UI (открытие оповещений, навигация) — только в клиентских обработчиках.
> Перепутать контексты = ошибка компиляции или недоступность серверных объектов на клиенте.

## Полный пример

```json
{
  "attributes": [
    {"name": "Наименование", "type": "string(100)"},
    {"name": "Количество", "type": "number(15,2)"},
    {"name": "Активен", "type": "boolean"}
  ],
  "commands": [
    {"name": "Сохранить", "action": "Save"},
    {"name": "Закрыть", "action": "Close"}
  ],
  "elements": [
    {
      "type": "group",
      "name": "ГруппаОсновное",
      "group": "Vertical",
      "children": [
        {"type": "input", "name": "Наименование", "dataPath": "Наименование"},
        {"type": "input", "name": "Количество", "dataPath": "Количество"},
        {"type": "checkbox", "name": "Активен", "dataPath": "Активен"}
      ]
    },
    {
      "type": "group",
      "name": "ГруппаКнопки",
      "group": "Horizontal",
      "children": [
        {"type": "button", "name": "Сохранить", "commandName": "Сохранить"},
        {"type": "button", "name": "Закрыть", "commandName": "Закрыть"}
      ]
    }
  ]
}
```

## Автоматическая генерация

- UUID, ID, ContextMenu, ExtendedTooltip создаются автоматически
- Поддерживается произвольная вложенность: group → group → input, pages → page → table

## Правильно / Неправильно

```json
// ❌ Неправильно — dataPath не совпадает с реквизитом (элемент не отобразит данные)
{"attributes": [{"name": "Наименование", "type": "string(100)"}], "elements": [{"type": "input", "name": "Поле1", "dataPath": "Поле1"}]}

// ✅ Правильно — dataPath = name реквизита
{"attributes": [{"name": "Наименование", "type": "string(100)"}], "elements": [{"type": "input", "name": "Наименование", "dataPath": "Наименование"}]}
```

> `dataPath` должен указывать на существующий реквизит из `attributes` (или путь к полю табличной части, напр. `Товары.Номенклатура`).

```json
// ❌ Неправильно — page без родителя pages (page должен быть внутри pages)
{"elements": [{"type": "page", "name": "Страница1", "children": [...]}]}

// ✅ Правильно — pages как контейнер, page внутри
{"elements": [{"type": "pages", "name": "Страницы", "children": [{"type": "page", "name": "Страница1", "children": [...]}]}]}
```

> В 1С страницы (Pages) — контейнер для вкладок. Page всегда должен быть дочерним элементом Pages.

## См. также

- [xml-gen-cli](../xml-gen-cli/) — edit-команды
- [xml-generation](../xml-generation/) — общее описание
- [epf-operations](../epf-operations/) — создание обработок

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
