# form-edit — Form Editing

Adds elements, attributes, commands, and events to an existing `Form.xml`. Implementation: Java CLI `xmlgen form edit` (replacement for the Python script). Automatically:

- allocates IDs from three independent pools (elements / attributes / commands),
- generates companion elements (ContextMenu, ExtendedTooltip, AutoCommandBar, etc.) based on the element type,
- detects extension mode when `<BaseForm>` is present and sets the ID floor to 1,000,000,
- appends empty BSL handler stubs to `Ext/Form/Module.bsl` with the correct compilation directive (`&НаКлиенте`/`&НаСервере`) and parameter signature.

## Usage

```
/form-edit <FormPath> <JsonPath>
```

## Parameters

| Parameter | Required | Description                      |
|-----------|:--------:|----------------------------------|
| FormPath  | yes      | Path to the existing Form.xml    |
| JsonPath  | yes      | Path to the JSON specification of additions |

## Command

```bash
xmlgen form edit "<FormPath>" --json "<JsonPath>"
```

## JSON format

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

### Element types (`kind`)

You can specify either the short DSL alias (`input`) or the direct XML tag name (`InputField`).

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
| `into` | root ChildItems | Name of the group/table/page where the item will be inserted |
| `after` | at the end | Name of the element after which to insert |

### Attributes — type system

`string`, `string(100)`, `decimal(15,2)`, `decimal(15,2,nonneg)`, `boolean`, `date`, `dateTime`, `time`, `CatalogRef.X`, `DocumentObject.X`, `ValueTable` (+ `columns:[]`), `ValueTree`, `DynamicList`, `TypeA | TypeB` (composite).

Russian synonyms: `string(100)`, `decimal(15,2)`, `date`, `boolean`, `CatalogRef.X` — are recognized and converted into canonical English names.

Attribute flags:
- `"main": true` — marks the attribute as primary (`<MainAttribute>true</MainAttribute>`).
- `"savedData": true` — save in form settings.
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

Explicit handler override via `handlers`:
```json
{ "kind": "input", "name": "Поле",
  "on": [{ "event": "OnChange" }],
  "handlers": { "OnChange": "МойКастомныйОбработчик" }
}
```

### Buttons with command binding

```json
{ "kind": "button", "name": "БтнВыполнить", "command": "Выполнить" }
```
→ `CommandName = Form.Command.Выполнить`.

## Workflow

`/form-info` → create JSON → `/form-edit` → `/form-validate` → `/form-info`.
