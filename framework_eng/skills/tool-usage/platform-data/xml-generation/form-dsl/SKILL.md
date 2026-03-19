---
name: form-dsl
description: JSON DSL for generating 1С managed forms with UI elements, attributes, and commands. Use it during form compile and when editing forms via xml-gen-cli.
---

# Form DSL

## Commands

```bash
xml-gen form compile [--format designer|edt] <input.json> <output.xml>
xml-gen form info <Form.xml>
```

Editing existing forms (add-attribute, add-element, move-element, etc.) — see [xml-gen-cli](../xml-gen-cli/)

## DSL structure

Minimal form: `{"attributes": [], "elements": []}`

### Attributes (attributes)

```json
{"name": "ИмяРеквизита", "type": "тип", "title": "Заголовок"}
```

**Types:** `string`, `string(N)`, `number`, `number(D,F)`, `boolean`, `date`, `uuid`, `CatalogRef.Name`, `DocumentRef.Name`, `ValueTable`

### UI elements (elements)

| DSL type | XML type | Description |
|----------|----------|-------------|
| `input` | InputField | Input field |
| `group` | UsualGroup | Group (`"group": "Vertical"/"Horizontal"`, `children`) |
| `table` | Table | Table (`dataPath`, `columns`) |
| `button` | Button | Button (`commandName`) |
| `label` | LabelDecoration | Label decoration |
| `checkbox` | CheckBoxField | Checkbox field |
| `pages` | Pages | Pages container |
| `page` | Page | Page (only inside `pages`) |

### Commands (commands)

```json
{"name": "Сохранить", "action": "Save", "title": "Сохранить"}
```

### Events (events)

```json
{"events": {"onCreateAtServer": "ПриСозданииНаСервере", "onOpen": "ПриОткрытии"}}
```

The DSL only sets the procedure name. Add the compiler directive in the form module manually:

| DSL event | Directive |
|-----------|-----------|
| `onCreateAtServer` | `&НаСервере` |
| `onOpen`, `onClose`, `beforeClose` | `&НаКлиенте` |

Mixing contexts = compilation error or server objects unavailable on the client.

## Automatic generation

UUID, ID, ContextMenu, ExtendedTooltip are generated automatically. Arbitrary nesting: group → group → input, pages → page → table.

## Correct / Incorrect

```json
// ❌ dataPath не совпадает с реквизитом → элемент не отобразит данные
{"attributes": [{"name": "Наименование", "type": "string(100)"}], "elements": [{"type": "input", "name": "Поле1", "dataPath": "Поле1"}]}

// ✅ dataPath = name реквизита (или путь к полю ТЧ: Товары.Номенклатура)
{"attributes": [{"name": "Наименование", "type": "string(100)"}], "elements": [{"type": "input", "name": "Наименование", "dataPath": "Наименование"}]}
```

```json
// ❌ page без родителя pages
{"elements": [{"type": "page", "name": "Страница1", "children": [...]}]}

// ✅ pages как контейнер, page внутри
{"elements": [{"type": "pages", "name": "Страницы", "children": [{"type": "page", "name": "Страница1", "children": [...]}]}]}
```

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
