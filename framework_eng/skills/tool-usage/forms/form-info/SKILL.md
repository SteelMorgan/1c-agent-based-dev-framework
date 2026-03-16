---
name: form-info
description: Analyze the structure of a 1С managed form (Form.xml) — elements, attributes, commands, events. Use it to understand the form when writing the form module or reviewing handlers and elements.
argument-hint: <FormPath>
allowed-tools:
  - Bash
  - Read
  - Glob
---

# /form-info — Compact form summary

Reads the Form.xml of a managed form and prints a compact summary: the element tree, attributes with types, commands, events. Removes the need to read thousands of lines of XML.

## Usage

```
/form-info <FormPath>
```

## Parameters

| Parameter | Required | Default | Description                                      |
|-----------|:--------:|---------|--------------------------------------------------|
| FormPath  | yes      | —       | Path to the Form.xml file                         |
| Limit     | no       | `150`   | Maximum number of lines to print (overflow guard) |
| Offset    | no       | `0`     | Skip N lines (for pagination)                    |

## Command

```bash
python3 scripts/form-info.py -FormPath "<path to Form.xml>"
```

With pagination:
```bash
python3 scripts/form-info.py -FormPath "<path>" -Offset 150
```

## Reading the output

### Header

```
=== Form: ФормаДокумента — "Sales of Goods and Services" (Documents.РеализацияТоваровУслуг) ===
```

For extension forms that inherit a base form (with `<BaseForm>`):
```
=== Form: ФормаЭлемента [EXTENSION] (Catalogs.Валюты) ===
```

The form name, title, and object context are derived from the file path and XML.

### Properties — form properties

Only the non-default properties are listed. The Title property is shown in the header rather than here:

```
Properties: AutoTitle=false, WindowOpeningMode=LockOwnerWindow, CommandBarLocation=Bottom
```

### Events — form event handlers

```
Events:
  OnCreateAtServer -> ПриСозданииНаСервере
  OnOpen -> ПриОткрытии
```

For extensions with callType:
```
Events:
  OnCreateAtServer[After] -> Расш1_ПриСозданииПосле
  OnOpen[Before] -> Расш1_ПриОткрытии
```

### Elements — UI element tree

A compact tree showing types, data bindings, flags, and events:

```
Elements:
  ├─ [Group:AH] ГруппаШапка
  │  ├─ [Input] Организация -> Объект.Организация {OnChange}
  │  └─ [Input] Договор -> Объект.Договор [visible:false] {StartChoice}
  ├─ [Table] Товары -> Объект.Товары
  │  ├─ [Input] Номенклатура -> Объект.Товары.Номенклатура {OnChange}
  │  └─ [Input] Сумма -> Объект.Товары.Сумма [ro]
  └─ [Pages] Страницы
     ├─ [Page] Основное (5 items)
     └─ [Page] Печать (2 items)
```

**Element type abbreviations:**

| Abbreviation | Element |
|---|---|
| `[Group:V]` | UsualGroup Vertical |
| `[Group:H]` | UsualGroup Horizontal |
| `[Group:AH]` | UsualGroup AlwaysHorizontal |
| `[Group:AV]` | UsualGroup AlwaysVertical |
| `[Group]` | UsualGroup (default orientation) |
| `[Input]` | InputField |
| `[Check]` | CheckBoxField |
| `[Label]` | LabelDecoration |
| `[LabelField]` | LabelField |
| `[Picture]` | PictureDecoration |
| `[PicField]` | PictureField |
| `[Calendar]` | CalendarField |
| `[Table]` | Table |
| `[Button]` | Button |
| `[CmdBar]` | CommandBar |
| `[Pages]` | Pages |
| `[Page]` | Page (shows the number of items instead of expanding) |
| `[Popup]` | Popup |
| `[BtnGroup]` | ButtonGroup |

**Flags** (only when they differ from defaults):
- `[visible:false]` — the element is hidden (Visible=false)
- `[enabled:false]` — the element is disabled (Enabled=false)
- `[ro]` — ReadOnly=true
- `,collapse` — Behavior=Collapsible (for groups)

**Data binding**: `-> Object.Field` — DataPath

**Command binding**: `-> CommandName [cmd]` — form command, `-> Close [std]` — standard command

**Events**: `{OnChange, StartChoice}` — handler names; `{OnChange[Before]}` — callType for extensions

**Title**: `[title:Text]` — only when it differs from the element name

### Attributes — form attributes

```
Attributes:
  *Объект: DocumentObject.РеализацияТоваров (main)
  Валюта: CatalogRef.Валюты
  Итого: decimal(15,2)
  Таблица: ValueTable [Номенклатура: CatalogRef.Номенклатура, Кол: decimal(10,3)]
  Список: DynamicList -> Catalog.Пользователи
```

- `*` and `(main)` mark the form’s main attribute (MainAttribute)
- ValueTable/ValueTree types expand their columns inside `[...]`
- DynamicList displays the main table via `->`

### Parameters — form parameters

```
Parameters:
  Ключ: DocumentRef.ЗакупкаТоваров (key)
  Основание: DocumentRef.*
```

- `(key)` marks a key parameter (KeyParameter)

### Commands — form commands

```
Commands:
  Печать -> ПечатьДокумента [Ctrl+P]
  Заполнить -> ЗаполнитьОбработка
```

For extensions with callType on actions:
```
Commands:
  Подбор -> Расш1_ПодборПеред[Before], Расш1_ПодборПосле[After]
```

Format: `Name -> Handler [Shortcut]`

### BaseForm (extensions)

For inherited forms the footer shows:
```
BaseForm: present (version 2.17)
```

## What is omitted

The script trims over 80% of the XML volume:
- Visual properties (Width, Height, Color, Font, Border, Align, Stretch)
- Auto-generated ExtendedTooltip and ContextMenu
- Multilingual wrappers (v8:item/v8:lang/v8:content)
- Namespace declarations
- id attributes

For a deep dive into details, use grep with the element name from the summary.

## When to use it

- **Before modifying a form**: understand the structure and locate the right group for inserting elements
- **When analyzing a form**: see which attributes, commands, and handlers are involved
- **When navigating large forms**: 28K lines of XML → 50–100 lines of context

## Overflow protection

The output is limited to 150 lines by default. If the limit is exceeded:
```
[TRUNCATED] Shown 150 of 220 lines. Use -Offset 150 to continue.
```

Use `-Offset N` and `-Limit N` for paging through the output.
