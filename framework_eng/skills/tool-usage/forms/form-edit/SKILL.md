---
name: form-edit
description: Adding elements, attributes, and commands to an existing managed 1С form. Use when you need to surgically modify a ready form.
argument-hint: <FormPath> <JsonPath>
allowed-tools:
  - Bash
  - Read
  - Write
  - Glob
---

# /form-edit — Form editing

Adds elements, attributes, and/or commands to an existing Form.xml. Automatically selects IDs from the correct pool, generates companion elements (ContextMenu, ExtendedTooltip, etc.), and event handlers.

## Usage

```
/form-edit <FormPath> <JsonPath>
```

## Parameters

| Parameter | Required | Description |
|-----------|:------------:|----------------------------------|
| FormPath  | yes        | Path to the existing Form.xml    |
| JsonPath  | yes        | Path to the JSON describing the additions |

## Command

```bash
python3 scripts/form-edit.py -FormPath "<путь>" -JsonPath "<путь>"
```

## JSON format

```json
{
  "into": "ГруппаШапка",
  "after": "Контрагент",
  "elements": [
    { "input": "Склад", "path": "Объект.Склад", "on": ["OnChange"] }
  ],
  "attributes": [
    { "name": "СуммаИтого", "type": "decimal(15,2)" }
  ],
  "commands": [
    { "name": "Рассчитать", "action": "РассчитатьОбработка" }
  ]
}
```

### Extensions (extension forms)

For borrowed forms (with `<BaseForm>`), extension mode is automatically activated: IDs start at 1000000+. Additional sections are available:

```json
{
  "formEvents": [
    { "name": "OnCreateAtServer", "handler": "Расш1_ПриСозданииПосле", "callType": "After" },
    { "name": "OnOpen", "handler": "Расш1_ПриОткрытии", "callType": "Before" }
  ],
  "elementEvents": [
    { "element": "Банк", "name": "OnChange", "handler": "Расш1_БанкПриИзменении", "callType": "Before" }
  ],
  "commands": [
    { "name": "Подбор", "action": "Расш1_ПодборПосле", "callType": "After" },
    { "name": "Запрос", "actions": [
      { "callType": "Before", "handler": "Расш1_ЗапросПеред" },
      { "callType": "After", "handler": "Расш1_ЗапросПосле" }
    ]}
  ],
  "elements": [
    { "input": "Поле", "path": "Объект.Поле", "on": [{ "event": "OnChange", "callType": "After" }] }
  ]
}
```

### Positioning elements

| Key | Default | Description |
|------|-------------|----------|
| `into` | root ChildItems | Name of the group/table/page where to insert |
| `after` | end | Name of the element after which to insert |

### Element types

The same DSL keys as in `/form-compile`:

| Key | XML tag | Companions |
|------|---------|------------|
| `input` | InputField | ContextMenu, ExtendedTooltip |
| `check` | CheckBoxField | ContextMenu, ExtendedTooltip |
| `label` | LabelDecoration | ContextMenu, ExtendedTooltip |
| `labelField` | LabelField | ContextMenu, ExtendedTooltip |
| `group` | UsualGroup | ExtendedTooltip |
| `table` | Table | ContextMenu, AutoCommandBar, Search*, ViewStatus* |
| `pages` | Pages | ExtendedTooltip |
| `page` | Page | ExtendedTooltip |
| `button` | Button | ExtendedTooltip |

Groups and tables support `children`/`columns` for nested elements.

### Buttons: command and stdCommand

- "command": "ИмяКоманды" → `Form.Command.ИмяКоманды`
- "stdCommand": "Close" → `Form.StandardCommand.Close`
- "stdCommand": "Товары.Add" → `Form.Item.Товары.StandardCommand.Add` (standard element command)

### Allowed events (`on`)

The compiler warns about invalid event names. Core ones:

- **input**: `OnChange`, `StartChoice`, `ChoiceProcessing`, `Clearing`, `AutoComplete`, `TextEditEnd`
- **check**: `OnChange`
- **table**: `OnStartEdit`, `OnEditEnd`, `OnChange`, `Selection`, `BeforeAddRow`, `BeforeDeleteRow`, `OnActivateRow`
- **label/picture**: `Click`, `URLProcessing`
- **pages**: `OnCurrentPageChange`
- **button**: `Click`

### Type system (for attributes)

`string`, `string(100)`, `decimal(15,2)`, `boolean`, `date`, `dateTime`, `CatalogRef.XXX`, `DocumentObject.XXX`, `ValueTable`, `DynamicList`, `Type1 | Type2` (composite).

### Extension sections

| Section | Purpose |
|--------|-----------|
| `formEvents` | Form-level events with `callType` (Before/After/Override) |
| `elementEvents` | Events on existing elements of the borrowed form |
| `callType` on `commands` | callType on the command action |
| `callType` on `on` | callType on events of new elements (object format) |

All extension sections are optional — without them the skill works the same as with normal forms.

## Output

```
=== form-edit: Form ===

[EXTENSION] BaseForm detected — IDs start at 1000000+

Added form events:
  + OnCreateAtServer[After] -> Расш1_ПриСозданииПосле

Added elements (into ГруппаШапка, after Контрагент):
  + [Input] Склад -> Объект.Склад {OnChange}

Added attributes:
  + СуммаИтого: decimal(15,2) (id=1000000)

---
Total: 1 form event(s), 1 element(s) (+2 companions), 1 attribute(s)
Run /form-validate to verify.
```

## Workflow

`/form-info` → create the JSON → `/form-edit` → `/form-validate` → `/form-info`
