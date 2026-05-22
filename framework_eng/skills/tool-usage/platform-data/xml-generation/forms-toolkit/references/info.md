# form-info — Compact form summary

## Usage

```
/form-info <FormPath>
```

## Command

```bash
xmlgen form info "<FormPath>"
```

With pagination:
```bash
xmlgen form info "<FormPath>" --limit 150 --offset 150
```

## Parameters

| Parameter | Required | Default | Description                                |
|-----------|:--------:|---------|--------------------------------------------|
| FormPath  | yes      | —       | Path to the Form.xml file                  |
| Limit     | no       | `150`   | Max. output lines (overflow protection)    |
| Offset    | no       | `0`     | Skip N lines (for pagination)              |

> Implementation: Java CLI `xmlgen form info` (replacement for the Python script). The result is printed to stdout and has the same structure regardless of platform.

## Reading the output

### Header

```
=== Form: ФормаДокумента — "Реализация товаров и услуг" (Documents.РеализацияТоваровУслуг) ===
```

For extension-borrowed forms (with `<BaseForm>`):
```
=== Form: ФормаЭлемента [EXTENSION] (Catalogs.Валюты) ===
```

### Properties — form properties

Only non-default properties (those that differ from the default):

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

A compact tree with types, data bindings, flags, and events:

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

**Flags** (only when different from default):
- `[visible:false]` — element is hidden (Visible=false)
- `[enabled:false]` — element is unavailable (Enabled=false)
- `[ro]` — ReadOnly=true
- `,collapse` — Behavior=Collapsible (for groups)

**Data binding**: `-> Объект.Поле` — DataPath

**Command binding**: `-> ИмяКоманды [cmd]` — form command, `-> Close [std]` — standard command

**Events**: `{OnChange, StartChoice}` — handler names; `{OnChange[Before]}` — with callType for extensions

**Title**: `[title:Text]` — only if it differs from the element name

### Attributes — form attributes

```
Attributes:
  *Объект: DocumentObject.РеализацияТоваров (main)
  Валюта: CatalogRef.Валюты
  Итого: decimal(15,2)
  Таблица: ValueTable [Номенклатура: CatalogRef.Номенклатура, Кол: decimal(10,3)]
  Список: DynamicList -> Catalog.Пользователи
```

- `*` and `(main)` — main form attribute (MainAttribute)
- ValueTable/ValueTree types expand columns in `[...]`
- DynamicList shows MainTable via `->`

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

For extensions with callType on Action:
```
Commands:
  Подбор -> Расш1_ПодборПеред[Before], Расш1_ПодборПосле[After]
```

Format: `Name -> Handler [Shortcut]`

### BaseForm (extensions)

For borrowed forms, the following is printed at the end:
```
BaseForm: present (version 2.17)
```

## Omitted data

Visual properties, auto-generated ExtendedTooltip/ContextMenu, multilingual wrappers, namespace declarations, id attributes. For details, use grep by the element name.

## Overflow protection

Output is limited to 150 lines. If it is exceeded, use `--limit N` and `--offset N` for pagination.
