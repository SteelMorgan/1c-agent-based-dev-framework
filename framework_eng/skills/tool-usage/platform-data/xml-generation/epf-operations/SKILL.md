---
name: epf-operations
description: "Operations with 1C external data processors (EPF) - creation, adding forms and templates. Use for epf init, add-form, add-template, add-attribute, add-tabular-section."
---

# EPF Operations

Working with 1C external data processors (ExternalDataProcessor).

## When to use

| Trigger | Action |
|---------|----------|
| Need to create a new external data processor | `epf init --name <Name> <output_dir>` |
| Need to add a form to a processor | `epf add-form --epf <EpfName> --name <FormName> <output_dir>` |
| Need to add a print form (spreadsheet document) | `epf add-template --epf ... --name ... --type spreadsheet <output_dir>` |
| Need to add an HTML/text template | `epf add-template --type html` or `--type text` |
| Need to add an attribute to an existing processor | `epf add-attribute --name ... --type ... <EpfRoot.xml>` |
| Need to create an external report (ERF) | `epf init --type report --name ... <output_dir>` |
| Need to add a tabular section to an existing processor | `epf add-tabular-section --name ... <EpfRoot.xml>` |

## Commands

### epf init

Create a new external data processor.

**Syntax:**
```bash
xml-gen epf init --name <Name> [--type processor|report] [--format designer|edt] [--synonym <Synonym>] <output_dir>
```

**Parameters:**
- `--name <Name>` — processor/report name (required)
- `--type processor|report` — type: `processor` (data processor, default) or `report` (external report, ERF)
- `--format designer|edt` — output format (default: designer)
- `--synonym <Synonym>` — synonym (optional)
- `<output_dir>` — output directory (required, positional argument)

**Example:**
```bash
xml-gen epf init --name MyProcessor output/
xml-gen epf init --format designer --name DataImport --synonym "Импорт данных" .
xml-gen epf init --type report --name SalesReport --synonym "Отчёт по продажам" output/
```

### epf add-form

Add a form to a processor.

**Syntax:**
```bash
xml-gen epf add-form --epf <EpfName> --name <FormName> [--format designer|edt] [--synonym <Synonym>] [--default] <output_dir>
```

**Example:**
```bash
xml-gen epf add-form --epf MyProcessor --name MainForm output/
xml-gen epf add-form --epf MyProcessor --name SettingsForm --default output/
```

### epf add-template

Add a template to a processor.

**Syntax:**
```bash
xml-gen epf add-template --epf <EpfName> --name <TemplateName> --type <Type> [--format designer|edt] [--synonym <Synonym>] <output_dir>
```

**Types:** `spreadsheet`, `html`, `text`

**Example:**
```bash
xml-gen epf add-template --epf MyProcessor --name PrintForm --type spreadsheet output/
xml-gen epf add-template --epf MyProcessor --name WebPage --type html output/
```

### epf add-attribute / add-tabular-section (editing)

Modifying an existing EPF (adding attributes, tabular sections). Works with the processor root XML.

```bash
xml-gen epf add-attribute --name <Name> [--type <Type>] [--synonym <Synonym>] <EpfRoot.xml>
xml-gen epf add-tabular-section --name <Name> [--synonym <Synonym>] <EpfRoot.xml>
```

**Example:**
```bash
xml-gen epf add-attribute --name Employee --type CatalogRef.Сотрудники --synonym "Сотрудник" output/MyProcessor.xml
```

## Key Paths (Designer)

- Root XML: `output/MyProcessor.xml`
- Object module: `output/MyProcessor/Ext/ObjectModule.bsl`
- Form.xml: `output/MyProcessor/Forms/<FormName>/Ext/Form.xml`
- Template: `output/MyProcessor/Templates/<Name>/Ext/Template.xml`

Integration: `form compile form.json <Form.xml path>`, `mxl compile template.json <Template path>`.

## Correct / Incorrect

```bash
# ❌ Неправильно — epf add-form с позиционными аргументами (CLI ожидает --epf, --name)
xml-gen epf add-form MyProcessor MainForm

# ✅ Правильно — именованные аргументы, output_dir в конце
xml-gen epf add-form --epf MyProcessor --name MainForm output/
```

> CLI parses only named arguments. `output_dir` is the last positional argument (the directory where `<EpfName>.xml` is located).

```bash
# ❌ Неправильно — epf add-attribute к Form.xml (add-attribute редактирует корневой XML обработки)
xml-gen epf add-attribute --name Employee MyProcessor/Forms/MainForm/Ext/Form.xml

# ✅ Правильно — путь к корневому XML обработки (MyProcessor.xml)
xml-gen epf add-attribute --name Employee --type CatalogRef.Сотрудники output/MyProcessor.xml
```

> `epf add-attribute` adds an attribute to the **processor**, not to the form. For a form, use `form add-attribute` with Form.xml.

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
