# Templates — layouts and embedded help for metadata objects

Managing layouts and embedded help for 1C metadata objects (Справочник, Документ, Отчёт, Обработка, etc.).

Implemented in `xml-gen` (Java): the `template add`, `template remove`, and `template add-help` commands are available from the CLI.
For external processors/reports (EPF/ERF) — see §1 of the main SKILL.md; it contains `epf add-template`.

---

## Command: template add

Adds a layout of the specified type to a metadata object and registers it in the root XML `ChildObjects`.

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
| `--src` | no | `src` | Source directory inside configDir |
| `--set-main-dcs` | no | — | Force `MainDataCompositionSchema` to be set (Report only) |
| `configDir` | yes | — | Root configuration directory (where `Configuration.xml` is located) |

### Template types

| User specifies | `--type` | File extension | Contents |
|----------------|----------|-----------------|------------|
| HTML, html document | `HTMLDocument` | `.html` | Empty HTML |
| Text, text, txt | `TextDocument` | `.txt` | Empty file |
| SpreadsheetDocument, MXL, tabular document | `SpreadsheetDocument` | `.xml` | Minimal SpreadsheetDocument |
| BinaryData, binary data, bin | `BinaryData` | `.bin` | Empty file |
| DataCompositionSchema, DCS, composition schema | `DataCompositionSchema` | `.xml` | Minimal DCS schema |

### Supported object types

Catalog, Document, Report, DataProcessor, InformationRegister, AccumulationRegister, AccountingRegister, CalculationRegister, ChartOfCharacteristicTypes, ChartOfAccounts, ChartOfCalculationTypes, BusinessProcess, Task, ExchangePlan.

### Examples

```bash
# Добавить MXL-макет (печатная форма) к документу
xml-gen template add \
  --object Document.ЗаказКлиента \
  --name ПФ_Счёт \
  --type SpreadsheetDocument \
  src/

# Добавить СКД к отчёту конфигурации и установить как основную схему
xml-gen template add \
  --object Report.ОстаткиТоваров \
  --name ОсновнаяСхема \
  --type DataCompositionSchema \
  --set-main-dcs \
  src/

# Добавить HTML-макет к справочнику
xml-gen template add \
  --object Catalog.Номенклатура \
  --name WebШаблон \
  --type HTMLDocument \
  src/
```

### What is created

```
<SrcDir>/<ObjectType>/<ObjectName>/Templates/
├── <TemplateName>.xml              # Метаданные макета (UUID, синоним, тип)
└── <TemplateName>/
    └── Ext/
        └── Template.<ext>          # Содержимое макета
```

### What is modified

- `<SrcDir>/<ObjectType>/<ObjectName>.xml` - `<Template>TemplateName</Template>` is added to the end of `ChildObjects`
- For Report + `DataCompositionSchema` only: `MainDataCompositionSchema` is populated (if empty or `--set-main-dcs` is specified)

### Naming convention

For print form templates (type `SpreadsheetDocument`), use the `ПФ_` prefix:

| Context | Name format | Example |
|----------|-------------|--------|
| Print form | `ПФ_<ShortName>` | `ПФ_Счёт`, `ПФ_М11`, `ПФ_СчётФактура` |
| Other templates (loading, settings, service) | Without prefix | `МакетЗагрузки`, `НастройкиПечати` |

If the user named the template without the prefix, but the context is a print form, add `ПФ_` automatically and report it.

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
| `--src` | no | `src` | Source directory |
| `configDir` | yes | — | Root configuration directory |

### Example

```bash
xml-gen template remove \
  --object Document.ЗаказКлиента \
  --name ПФ_Счёт \
  src/
```

### What is removed

```
<SrcDir>/<ObjectType>/<ObjectName>/Templates/<TemplateName>.xml   # Template metadata
<SrcDir>/<ObjectType>/<ObjectName>/Templates/<TemplateName>/      # Contents directory (recursive)
```

### What is modified

- `<SrcDir>/<ObjectType>/<ObjectName>.xml` - `<Template>TemplateName</Template>` is removed from `ChildObjects`
- If the removed template was referenced in `MainDataCompositionSchema`, the value is cleared

---

## Command: template add-help

Adds embedded help to a metadata object: the `Help.xml` descriptor file and an HTML page.

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
| `--src` | no | `src` | Source directory |
| `configDir` | yes | — | Root configuration directory |

### Example

```bash
xml-gen template add-help \
  --object Catalog.Контрагенты \
  src/
```

### What is created

```
<SrcDir>/<ObjectType>/<ObjectName>/Ext/
├── Help.xml                        # Help descriptor (list of languages)
└── Help/
    └── ru.html                     # Help HTML page
```

### What is modified

- If the object has forms, `<IncludeHelpInContents>false</IncludeHelpInContents>` is added to `Forms/<FormName>.xml` (if it is missing). This enables the help button in the form's `AutoCommandBar`.
- The help is **not** registered in `ChildObjects` - the files themselves are sufficient.

### After creation

Edit `Ext/Help/ru.html` manually - fill it with content:
```html
<h1>Контрагенты</h1>
<h2>Назначение</h2>
<p>Справочник хранит информацию о контрагентах...</p>
```
Use standard tags: `<h1>...<h4>`, `<p>`, `<ul>`, `<table>`.

---

## Integration with other commands

After adding an MXL template, fill in its contents via `mxl compile`:

```bash
# 1. Добавить макет
xml-gen template add \
  --object Document.ЗаказКлиента \
  --name ПФ_Счёт \
  --type SpreadsheetDocument \
  src/

# 2. Заполнить содержимое через MXL DSL
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
