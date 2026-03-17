---
name: form-info
description: Analyze the structure of a managed 1С form (Form.xml) — elements, attributes, commands, events. Use to understand the form when writing the form module, analyzing handlers, or elements
argument-hint: <FormPath>
allowed-tools:
  - Bash
  - Read
  - Glob
---

# /form-info — Compact form summary

## Usage

```
/form-info <FormPath>
```

## Parameters

| Parameter | Required | Default | Description                                   |
|-----------|:--------:|---------|-----------------------------------------------|
| FormPath  | yes      | —       | Path to the Form.xml file                      |
| Limit     | no       | `150`   | Max output rows (overflow protection)          |
| Offset    | no       | `0`     | Skip N rows (for pagination)                   |

## Command

```bash
python3 scripts/form-info.py -FormPath "<путь к Form.xml>"
```

With pagination:
```bash
python3 scripts/form-info.py -FormPath "<путь>" -Offset 150
```

## Reading output

### Title

```
=== Form: ФормаДокумента — "Реализация товаров и услуг" (Documents.РеализацияТоваровУслуг) ===
```

For borrowed extension forms (with `<BaseForm>`):
```
=== Form: ФормаЭлемента [EXTENSION] (Catalogs.Валюты) ===
```

### Properties — form properties

Only non-default properties:

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

Compact tree with types, data bindings, flags, and events:

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
| `[Page]` | Page (shows item count instead of expanding) |
| `[Popup]` | Popup |
| `[BtnGroup]` | ButtonGroup |

**Flags** (only when deviating from default):
- `[visible:false]` — element is hidden (Visible=false)
- `[enabled:false]` — element is disabled (Enabled=false)
- `[ro]` — ReadOnly=true
- `,collapse` — Behavior=Collapsible (for groups)

**Data binding**: `-> Объект.Поле` — DataPath

**Command binding**: `-> ИмяКоманды [cmd]` — form command, `-> Close [std]` — standard command

**Events**: `{OnChange, StartChoice}` — handler names; `{OnChange[Before]}` — with callType for extensions

**Title**: `[title:Текст]` — only if different from the element name

### Attributes — form attributes

```
Attributes:
  *Объект: DocumentObject.РеализацияТоваров (main)
  Валюта: CatalogRef.Валюты
  Итого: decimal(15,2)
  Таблица: ValueTable [Номенклатура: CatalogRef.Номенклатура, Кол: decimal(10,3)]
  Список: DynamicList -> Catalog.Пользователи
```

- `*` and `(main)` — main attribute of the form (MainAttribute)
- ValueTable/ValueTree types expose columns inside `[...]`
- DynamicList shows the MainTable via `->`

### Parameters — form parameters

```
Parameters:
  Ключ: DocumentRef.ЗакупкаТоваров (key)
  Основание: DocumentRef.*
```

- `(key)` — key parameter (KeyParameter)

### Commands — form commands

```
Commands:
  Печать -> ПечатьДокумента [Ctrl+P]
  Заполнить -> ЗаполнитьОбработка
```

For extensions with callType on an Action:
```
Commands:
  Подбор -> Расш1_ПодборПеред[Before], Расш1_ПодборПосле[After]
```

Format: `Name -> Handler [Shortcut]`

### BaseForm (extensions)

For borrowed forms, the output ends with:
```
BaseForm: present (version 2.17)
```

## Skippable data

Visual properties, auto-generated ExtendedTooltip/ContextMenu, multilingual wrappers, namespace declarations, id attributes. For details — grep by element name.

## Overflow protection

The output is limited to 150 rows. If exceeded, use `-Offset N` and `-Limit N` for pagination.
