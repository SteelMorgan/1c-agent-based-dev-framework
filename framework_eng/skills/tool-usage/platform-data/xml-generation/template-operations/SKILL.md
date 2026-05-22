---
name: template-operations
description: "Operations with templates and built-in help for 1C metadata objects (Catalog/Document/Report/DataProcessor and others) — adding, removing templates of any type (MXL/HTML/Text/DCS/Binary) and adding help. Use this for template add/remove/help for configuration objects."
---

# Template Operations

Managing templates and built-in help for 1C metadata objects (Справочник, Документ, Отчёт, Обработка and others).

This skill describes the expected interface of the `xml-gen template` commands — the Java implementation is handled separately.
For external processors/reports (EPF/ERF), see the `epf-operations` skill — it includes ready-made `epf add-template`.

## When to Use

| Trigger | Action |
|---------|----------|
| Need to add a template to a catalog/document/register | `xml-gen template add` |
| Need to add a DCS to a configuration report | `xml-gen template add --type DataCompositionSchema --set-main-dcs` |
| Need to add a print form (MXL) | `xml-gen template add --type SpreadsheetDocument` |
| Need to remove a template | `xml-gen template remove` |
| Need to add built-in help to an object | `xml-gen template add-help` |

---

## Command: template add

Adds a template of the specified type to a metadata object and registers it in the root XML `ChildObjects`.

### Syntax

```bash
xml-gen template add \
  --object <Type.ObjectName> \
  --name <TemplateName> \
  --type <TemplateType> \
  [--synonym <Synonym>] \
  [--src <SrcDir>] \
  [--set-main-dcs] \
  <configDir>
```

### Parameters

| Parameter | Required | Default | Description |
|----------|:------------:|--------------|----------|
| `--object` | yes | — | Object type and name: `Catalog.Контрагенты`, `Document.ЗаказКлиента`, `Report.ОстаткиТоваров`, etc. |
| `--name` | yes | — | Name of the template being created |
| `--type` | yes | — | Template type (see the type table) |
| `--synonym` | no | = `--name` | Template synonym (display name) |
| `--src` | no | `src` | Directory with sources inside `configDir` |
| `--set-main-dcs` | no | — | Force `MainDataCompositionSchema` to be set (only for Report) |
| `configDir` | yes | — | Root configuration directory (where `Configuration.xml` is located) |

### Template Types

| User specifies | `--type` | File extension | Contents |
|----------------|----------|-----------------|------------|
| HTML, HTML document | `HTMLDocument` | `.html` | Empty HTML |
| Text, text, txt | `TextDocument` | `.txt` | Empty file |
| SpreadsheetDocument, MXL, spreadsheet document | `SpreadsheetDocument` | `.xml` | Minimal SpreadsheetDocument |
| BinaryData, binary data, bin | `BinaryData` | `.bin` | Empty file |
| DataCompositionSchema, СКД, composition schema | `DataCompositionSchema` | `.xml` | Minimal DCS schema |

### Supported Object Types

Catalog, Document, Report, DataProcessor, InformationRegister, AccumulationRegister, AccountingRegister, CalculationRegister, ChartOfCharacteristicTypes, ChartOfAccounts, ChartOfCalculationTypes, BusinessProcess, Task, ExchangePlan.

### Examples

```bash
# Add an MXL template (print form) to a document
xml-gen template add \
  --object Document.ЗаказКлиента \
  --name ПФ_Счёт \
  --type SpreadsheetDocument \
  src/

# Add a DCS to a configuration report and set it as the main schema
xml-gen template add \
  --object Report.ОстаткиТоваров \
  --name ОсновнаяСхема \
  --type DataCompositionSchema \
  --set-main-dcs \
  src/

# Add an HTML template to a catalog
xml-gen template add \
  --object Catalog.Номенклатура \
  --name WebШаблон \
  --type HTMLDocument \
  src/
```

### What Is Created

```
<SrcDir>/<ObjectType>/<ObjectName>/Templates/
├── <TemplateName>.xml              # Template metadata (UUID, synonym, type)
└── <TemplateName>/
    └── Ext/
        └── Template.<ext>          # Template contents
```

### What Is Modified

- `<SrcDir>/<ObjectType>/<ObjectName>.xml` — `<Template>TemplateName</Template>` is appended to the end of `ChildObjects`
- Only for Report + `DataCompositionSchema`: `MainDataCompositionSchema` is filled in (if empty or if `--set-main-dcs` is specified)

### Naming Convention

For print form templates (type `SpreadsheetDocument`), use the `ПФ_` prefix:

| Context | Name Format | Example |
|----------|-------------|--------|
| Print form | `ПФ_<ShortName>` | `ПФ_Счёт`, `ПФ_М11`, `ПФ_СчётФактура` |
| Other templates (loading, settings, utility) | Without prefix | `МакетЗагрузки`, `НастройкиПечати` |

If the user named the template without a prefix, but the context is a print form, add `ПФ_` automatically and report it.

---

## Command: template remove

Removes a template and clears its registration in the root XML `ChildObjects`.

### Syntax

```bash
xml-gen template remove \
  --object <Type.ObjectName> \
  --name <TemplateName> \
  [--src <SrcDir>] \
  <configDir>
```

### Parameters

| Parameter | Required | Default | Description |
|----------|:------------:|--------------|----------|
| `--object` | yes | — | Object type and name: `Catalog.Контрагенты`, etc. |
| `--name` | yes | — | Name of the template being removed |
| `--src` | no | `src` | Directory with sources |
| `configDir` | yes | — | Root configuration directory |

### Example

```bash
xml-gen template remove \
  --object Document.ЗаказКлиента \
  --name ПФ_Счёт \
  src/
```

### What Is Removed

```
<SrcDir>/<ObjectType>/<ObjectName>/Templates/<TemplateName>.xml   # Template metadata
<SrcDir>/<ObjectType>/<ObjectName>/Templates/<TemplateName>/      # Contents directory (recursive)
```

### What Is Modified

- `<SrcDir>/<ObjectType>/<ObjectName>.xml` — `<Template>TemplateName</Template>` is removed from `ChildObjects`
- If the removed template was specified in `MainDataCompositionSchema`, the value is cleared

---

## Command: template add-help

Adds built-in help to a metadata object: the `Help.xml` descriptor file and an HTML page.

### Syntax

```bash
xml-gen template add-help \
  --object <Type.ObjectName> \
  [--lang <lang>] \
  [--src <SrcDir>] \
  <configDir>
```

### Parameters

| Parameter | Required | Default | Description |
|----------|:------------:|--------------|----------|
| `--object` | yes | — | Object type and name: `Catalog.Контрагенты`, etc. |
| `--lang` | no | `ru` | Help language code |
| `--src` | no | `src` | Directory with sources |
| `configDir` | yes | — | Root configuration directory |

### Example

```bash
xml-gen template add-help \
  --object Catalog.Контрагенты \
  src/
```

### What Is Created

```
<SrcDir>/<ObjectType>/<ObjectName>/Ext/
├── Help.xml                        # Help descriptor (list of languages)
└── Help/
    └── ru.html                     # Help HTML page
```

### What Is Modified

- If the object has forms, `<IncludeHelpInContents>false</IncludeHelpInContents>` is added to `Forms/<FormName>.xml` (if absent). This enables the help button in the form's `AutoCommandBar`.
- Help is **not registered** in `ChildObjects` — the files are enough.

### After Creation

Edit `Ext/Help/ru.html` manually — fill it with content:
```html
<h1>Контрагенты</h1>
<h2>Назначение</h2>
<p>Справочник хранит информацию о контрагентах...</p>
```
Use standard tags: `<h1>...<h4>`, `<p>`, `<ul>`, `<table>`.

---

## Integration with Other Commands

After adding an MXL template, fill its contents using `mxl compile`:

```bash
# 1. Add the template
xml-gen template add \
  --object Document.ЗаказКлиента \
  --name ПФ_Счёт \
  --type SpreadsheetDocument \
  src/

# 2. Fill the contents via MXL DSL
xml-gen mxl compile invoice.json \
  src/Documents/ЗаказКлиента/Templates/ПФ_Счёт/Ext/Template.xml
```

To get a list of existing templates, use:

```bash
xml-gen meta info src/Documents/ЗаказКлиента
```

---

## Correct / Incorrect

```bash
# Неправильно — не указан тип объекта (только имя)
xml-gen template add --object ЗаказКлиента --name ПФ_Счёт --type SpreadsheetDocument src/

# Правильно — полный путь Type.Name
xml-gen template add --object Document.ЗаказКлиента --name ПФ_Счёт --type SpreadsheetDocument src/
```

```bash
# Неправильно — для Report без --set-main-dcs при уже существующей MainDataCompositionSchema
xml-gen template add --object Report.Продажи --name НоваяСхема --type DataCompositionSchema src/
# Результат: MainDataCompositionSchema не перезапишется (сохранится старое значение)

# Правильно — явно указать флаг для перезаписи
xml-gen template add --object Report.Продажи --name НоваяСхема --type DataCompositionSchema --set-main-dcs src/
```

---
depends_on:
  - mxl-dsl
  - meta-operations
metadata:
  category: 1c-development
  version: "1.0"
---
