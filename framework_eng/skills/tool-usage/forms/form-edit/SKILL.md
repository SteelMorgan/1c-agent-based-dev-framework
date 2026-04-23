---
name: form-edit
description: "Adding elements, attributes, commands, and events to an existing managed 1С form through xmlgen CLI. Use when you need to precisely modify a ready-made form."
argument-hint: <FormPath> <JsonPath>
allowed-tools:
  - Bash
  - Read
  - Write
  - Glob
---

# /form-edit — Form Editing

Adds elements, attributes, commands, and events to an existing `Form.xml`. Implementation: Java CLI `xmlgen form edit` (replacement for the Python script). Automatically:

- allocates IDs from three independent pools (elements / attributes / commands),
- generates companion elements (ContextMenu, ExtendedTooltip, AutoCommandBar, etc.) based on the element type,
- detects extension mode when `<BaseForm>` is present and sets the ID floor to 1 000 000,
- appends empty BSL handler stubs to `Ext/Form/Module.bsl` with the correct compilation directive (`&НаКлиенте`/`&НаСервере`) and parameter signature.

## Usage

```
/form-edit <FormPath> <JsonPath>
```

## Parameters

| Parameter | Required | Description |
|-----------|:------------:|----------------------------------|
| FormPath  | yes      | Path to the existing Form.xml |
| JsonPath  | yes      | Path to the JSON specification of the additions |

## Command

```bash
xmlgen form edit "<FormPath>" --json "<JsonPath>"
```

## JSON Format

```json
{
  "elements": [
    {
      "kind": "input",
      "name": "Склад",
      "dataPath": "Объект.Склад",
      "into": "ГруппаШапка",
      "after": "Контрагент",
      "on": [{ "event": "OnChange" }]
    }
  ],
  "attributes": [
    { "name": "СуммаИтого", "type": "decimal(15,2)" }
  ],
  "commands": [
    { "name": "Рассчитать", "action": "РассчитатьОбработка" }
  ]
}
```

### Element Types (`kind`)

You can specify either a short DSL alias (`input`) or the direct XML tag name (`InputField`).

| kind | XML tag | Companions |
|------|---------|------------|
| `input` | InputField | ContextMenu, ExtendedTooltip |
| `check` | CheckBoxField | ContextMenu, ExtendedTooltip |
| `label` | LabelDecoration | ContextMenu, ExtendedTooltip |
| `labelField` | LabelField | ContextMenu, ExtendedTooltip |
| `picField` | PictureField | ContextMenu, ExtendedTooltip |
| `calendar` | CalendarField | ContextMenu, ExtendedTooltip |
| `picture` | PictureDecoration | ContextMenu, ExtendedTooltip |
| `table` | Table | ContextMenu, AutoCommandBar, SearchStringAddition, ViewStatusAddition, SearchControlAddition |
| `button` | Button | ExtendedTooltip |
| `group` | UsualGroup | ExtendedTooltip |
| `pages` | Pages | ExtendedTooltip |
| `page` | Page | ExtendedTooltip |
| `cmdBar` | CommandBar | — |
| `popup` | Popup | — |

Groups and tables support `children` for nested elements.

### Positioning

| Key | Default | Description |
|------|-------------|----------|
| `into` | root ChildItems | Name of the group/table/page to insert into |
| `after` | end | Name of the element after which to insert |

### Attributes - Type System

`string`, `string(100)`, `decimal(15,2)`, `decimal(15,2,nonneg)`, `boolean`, `date`, `dateTime`, `time`, `CatalogRef.X`, `DocumentObject.X`, `ValueTable` (+ `columns:[]`), `ValueTree`, `DynamicList`, `TypeA | TypeB` (composite).

Russian synonyms: `строка(100)`, `число(15,2)`, `дата`, `булево`, `справочникСсылка.X` are recognized and converted into canonical English names.

Attribute flags:
- `"main": true` — marks the attribute as main (`<MainAttribute>true</MainAttribute>`).
- `"savedData": true` — saves it in form settings.
- `"columns": [{ "name": "…", "type": "…" }]` — columns for `ValueTable`/`ValueTree`.

### Events (`on`, `formEvents`, `elementEvents`)

For a new element:
```json
{ "kind": "input", "name": "Поле", "on": [{ "event": "OnChange" }] }
```
If no explicit `handler` is provided, the name is generated as `<name>OnChange`. An empty BSL procedure stub is automatically appended to `Ext/Form/Module.bsl` if it is not already there.

For extensions (`<BaseForm>` is present in the form), `callType` is available:
```json
{
  "formEvents": [
    { "name": "OnCreateAtServer", "handler": "Расш_ПриСоздании", "callType": "After" }
  ],
  "elementEvents": [
    { "element": "Банк", "name": "OnChange", "handler": "Расш_БанкПриИзменении", "callType": "Before" }
  ],
  "elements": [
    { "kind": "input", "name": "П", "on": [{ "event": "OnChange", "callType": "After" }] }
  ]
}
```

Explicit handler override through `handlers`:
```json
{ "kind": "input", "name": "Поле",
  "on": [{ "event": "OnChange" }],
  "handlers": { "OnChange": "МойКастомныйОбработчик" }
}
```

### Buttons Bound to a Command

```json
{ "kind": "button", "name": "БтнВыполнить", "command": "Выполнить" }
```
→ `CommandName = Form.Command.Выполнить`.

## Workflow

`/form-info` → create JSON → `/form-edit` → `/form-validate` → `/form-info`.
