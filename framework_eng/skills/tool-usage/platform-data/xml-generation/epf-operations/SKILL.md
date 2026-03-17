---
name: epf-operations
description: Operations with external data processors 1С (EPF) — creation, adding forms and templates. Use when epf init, add-form, add-template, add-attribute, add-tabular-section.
---

# EPF Operations

Working with external data processors 1С (ExternalDataProcessor).

## When to use

| Trigger | Action |
|---------|--------|
| You need to create a new external data processor | `epf init --name <Name> <output_dir>` |
| You need to add a form to the processor | `epf add-form --epf <EpfName> --name <FormName> <output_dir>` |
| You need to add a print form (tabular document) | `epf add-template --epf ... --name ... --type spreadsheet <output_dir>` |
| You need to add an HTML/text template | `epf add-template --type html` or `--type text` |
| You need to add an attribute to an existing processor | `epf add-attribute --name ... --type ... <EpfRoot.xml>` |
| You need to create an external report (ERF) | `epf init --type report --name ... <output_dir>` |
| You need to add a tabular section to an existing processor | `epf add-tabular-section --name ... <EpfRoot.xml>` |

## Commands

### epf init

Create a new external data processor.

**Syntax:**
```bash
xml-gen epf init --name <Name> [--type processor|report] [--format designer|edt] [--synonym <Synonym>] <output_dir>
```

**Parameters:**
- `--name <Name>` — the processor/report name (required)
- `--type processor|report` — type: `processor` (processor by default) or `report` (external report, ERF)
- `--format designer|edt` — output format (default: designer)
- `--synonym <Synonym>` — synonym (optional)
- `<output_dir>` — output directory (required positional argument)

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

## EPF structure (Designer)

```
MyProcessor/
├── MyProcessor.xml              # Корневой файл метаданных
├── Ext/
│   └── ObjectModule.bsl         # Модуль объекта
├── Forms/
│   └── MainForm/
│       ├── Form.xml             # Метаданные формы
│       └── Ext/
│           └── Form/
│               └── Module.bsl    # Модуль формы
└── Templates/
    └── PrintForm/
        ├── Template.xml
        └── Ext/
            └── Template.mxl     # Табличный документ
```

## Integration with form compile and mxl compile

After creating a form:

```bash
xml-gen form compile form.json output/MyProcessor/Forms/MainForm/Ext/Form.xml
```

After creating a template:

```bash
xml-gen mxl compile template.json output/MyProcessor/Templates/PrintForm/Ext/Template.xml
```

## Right / Wrong

```bash
# ❌ Wrong — epf add-form with positional arguments (CLI expects --epf, --name)
xml-gen epf add-form MyProcessor MainForm

# ✅ Correct — named arguments, output_dir at the end
xml-gen epf add-form --epf MyProcessor --name MainForm output/
```

> The CLI parses only named arguments. `output_dir` is the last positional argument (the folder containing `<EpfName>.xml`).

```bash
# ❌ Wrong — epf add-attribute against Form.xml (add-attribute edits the processor root XML)
xml-gen epf add-attribute --name Employee MyProcessor/Forms/MainForm/Ext/Form.xml

# ✅ Correct — path to processor root XML (MyProcessor.xml)
xml-gen epf add-attribute --name Employee --type CatalogRef.Сотрудники output/MyProcessor.xml
```

> `epf add-attribute` adds an attribute to the **processor**, not to the form. For a form use `form add-attribute` with Form.xml.

## See also

- [xml-generation](../xml-generation/) — general description
- [form-dsl](../form-dsl/) — form content generation
- [mxl-dsl](../mxl-dsl/) — tabular document generation
- [xml-gen-cli](../xml-gen-cli/) — validate and edit commands

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
