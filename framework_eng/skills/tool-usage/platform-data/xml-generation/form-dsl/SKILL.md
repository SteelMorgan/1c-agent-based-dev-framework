---
name: form-dsl
description: JSON DSL for generating 1C managed forms with UI elements, attributes, and commands. Use it when running form compile or editing forms via xml-gen-cli.
---

# Form DSL

JSON DSL for generating 1C managed forms.

## When to use

| Trigger | Action |
|---------|--------|
| Need to create a form from scratch (attributes, elements, commands) | `form compile` with the JSON DSL |
| Need to add an attribute to an existing form | `form add-attribute` → [xml-gen-cli](../xml-gen-cli/) |
| Need to add a UI element (field, button, group) | `form add-element` → [xml-gen-cli](../xml-gen-cli/) |
| Need to add a form command | `form add-command` → [xml-gen-cli](../xml-gen-cli/) |
| Need to remove or move an element | `form remove-element`, `form move-element` → [xml-gen-cli](../xml-gen-cli/) |
| Need to inspect the structure of an existing form | `form info <Form.xml>` |

## Compile command

```bash
xml-gen form compile [--format designer|edt] <input.json> <output.xml>
```

**Editing existing forms** (add-attribute, add-element, add-command, remove-element, move-element) — see [xml-gen-cli](../xml-gen-cli/)

## Info command

Parses Form.xml — elements, attributes, commands, events.

```bash
xml-gen form info <Form.xml>
```

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
| `group` | UsualGroup | Regular group |
| `table` | Table | Table |
| `button` | Button | Button |
| `label` | LabelDecoration | Decoration label |
| `checkbox` | CheckBoxField | Checkbox field |
| `pages` | Pages | Pages |
| `page` | Page | Page |

**Input example:**
```json
{"type": "input", "name": "Наименование", "dataPath": "Наименование", "title": "Наименование товара"}
```

**Group example with children:**
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

**Table example:**
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
> The DSL only sets the **procedure name**; you must add the compiler directive manually in the form module:
>
> | DSL event | Procedure name | Directive in the form module |
> |-----------|----------------|------------------------------|
> | `onCreateAtServer` | `ПриСозданииНаСервере` | `&НаСервере` |
> | `onOpen` | `ПриОткрытии` | `&НаКлиенте` |
> | `onClose` | `ПриЗакрытии` | `&НаКлиенте` |
> | `beforeClose` | `ПередЗакрытием` | `&НаКлиенте` |
>
> Initialization code for form data should be written in `ПриСозданииНаСервере` (`&НаСервере`).
> UI-related code (showing notifications, navigation) should run only in client handlers.
> Mixing up contexts will result in a compilation error or server objects being unavailable on the client.

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

- UUID, ID, ContextMenu, ExtendedTooltip are created automatically
- Arbitrary nesting is supported: group → group → input, pages → page → table

## Right / Wrong

```json
// ❌ Wrong — dataPath does not match an attribute (the element will not show data)
{"attributes": [{"name": "Наименование", "type": "string(100)"}], "elements": [{"type": "input", "name": "Поле1", "dataPath": "Поле1"}]}

// ✅ Correct — dataPath equals the attribute name
{"attributes": [{"name": "Наименование", "type": "string(100)"}], "elements": [{"type": "input", "name": "Наименование", "dataPath": "Наименование"}]}
```

> `dataPath` must reference an existing attribute from `attributes` (or a table part field path, e.g., `Товары.Номенклатура`).

```json
// ❌ Wrong — page without a parent pages (page must be inside pages)
{"elements": [{"type": "page", "name": "Страница1", "children": [...]}]}

// ✅ Correct — pages as a container, page inside
{"elements": [{"type": "pages", "name": "Страницы", "children": [{"type": "page", "name": "Страница1", "children": [...]}]}]}
```

> In 1C, pages (Pages) act as a container for tabs. Page must always be a child element of Pages.

## See also

- [xml-gen-cli](../xml-gen-cli/) — edit commands
- [xml-generation](../xml-generation/) — general overview
- [epf-operations](../epf-operations/) — creating operations

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
