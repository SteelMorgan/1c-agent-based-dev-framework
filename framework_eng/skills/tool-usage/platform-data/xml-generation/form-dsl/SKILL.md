---
name: form-dsl
description: JSON DSL for generating 1C managed forms with UI elements, attributes, and commands. Use it for `form compile` and editing forms via xml-gen-cli.
---

# Form DSL

JSON DSL for generating 1C managed forms.

## When to apply

| Trigger | Action |
|---------|--------|
| Need to create a form from scratch (attributes, elements, commands) | `form compile` with JSON DSL |
| Need to add an attribute to an existing form | `form add-attribute` → [xml-gen-cli](../xml-gen-cli/) |
| Need to add a UI element (field, button, group) | `form add-element` → [xml-gen-cli](../xml-gen-cli/) |
| Need to add a form command | `form add-command` → [xml-gen-cli](../xml-gen-cli/) |
| Need to remove/move an element | `form remove-element`, `form move-element` → [xml-gen-cli](../xml-gen-cli/) |

## Compile command

```bash
xml-gen form compile [--format designer|edt] <input.json> <output.xml>
```

**Editing existing forms** (add-attribute, add-element, add-command, remove-element, move-element) — see [xml-gen-cli](../xml-gen-cli/)

## DSL structure

### Minimal form

```json
{
  "attributes": [],
  "elements": []
}
```

### Attributes (attributes)

```json
{
  "name": "ИмяРеквизита",
  "type": "тип",
  "title": "Заголовок"
}
```

**Supported types:** `string`, `string(N)`, `number`, `number(D,F)`, `boolean`, `date`, `uuid`, `CatalogRef.Name`, `DocumentRef.Name`, `ValueTable`

### UI elements (elements)

| DSL type | XML type | Description |
|----------|----------|-------------|
| `input` | InputField | Input field |
| `group` | UsualGroup | Usual group |
| `table` | Table | Table |
| `button` | Button | Button |
| `label` | LabelDecoration | Decoration label |
| `checkbox` | CheckBoxField | Checkbox field |
| `pages` | Pages | Pages |
| `page` | Page | Page |

**Example input:**
```json
{"type": "input", "name": "Наименование", "dataPath": "Наименование", "title": "Наименование товара"}
```

**Example group with children:**
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

**Example table:**
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

### Commands (commands)

```json
{
  "commands": [
    {"name": "Сохранить", "action": "Save", "title": "Сохранить"},
    {"name": "Закрыть", "action": "Close", "title": "Закрыть"}
  ]
}
```

### Events (events)

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

> ⚠️ **Client/server context is critical for 1C.**
> The DSL only specifies the **procedure name**; you must set the compiler directive manually in the form module:
>
> | DSL event | Procedure name | Directive in the form module |
> |-----------|----------------|----------------------------|
> | `onCreateAtServer` | `ПриСозданииНаСервере` | `&НаСервере` |
> | `onOpen` | `ПриОткрытии` | `&НаКлиенте` |
> | `onClose` | `ПриЗакрытии` | `&НаКлиенте` |
> | `beforeClose` | `ПередЗакрытием` | `&НаКлиенте` |
>
> Form data initialization code runs in `ПриСозданииНаСервере` (`&НаСервере`).
> UI-related code (showing notifications, navigation) belongs only to client handlers.
> Mixing contexts = compilation error or server objects unavailable on the client.

## Full example

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

## Automatic generation

- UUID, ID, ContextMenu, ExtendedTooltip are generated automatically
- Arbitrary nesting is supported: group → group → input, pages → page → table

## Correct / Incorrect

```json
// ❌ Неправильно — dataPath не совпадает с реквизитом (элемент не отобразит данные)
{"attributes": [{"name": "Наименование", "type": "string(100)"}], "elements": [{"type": "input", "name": "Поле1", "dataPath": "Поле1"}]}

// ✅ Правильно — dataPath = name реквизита
{"attributes": [{"name": "Наименование", "type": "string(100)"}], "elements": [{"type": "input", "name": "Наименование", "dataPath": "Наименование"}]}
```

> `dataPath` should point to an existing attribute from `attributes` (or to a tabular section field path, e.g. `Товары.Номенклатура`).

```json
// ❌ Неправильно — page без родителя pages (page должен быть внутри pages)
{"elements": [{"type": "page", "name": "Страница1", "children": [...]}]}

// ✅ Правильно — pages как контейнер, page внутри
{"elements": [{"type": "pages", "name": "Страницы", "children": [{"type": "page", "name": "Страница1", "children": [...]}]}]}
```

> In 1C pages (Pages) are a container for tabs. Page must always be a child element of Pages.

## See also

- [xml-gen-cli](../xml-gen-cli/) — edit commands
- [xml-generation](../xml-generation/) — general overview
- [epf-operations](../epf-operations/) — creating external handlers

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
