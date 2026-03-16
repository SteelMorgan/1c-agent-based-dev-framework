---
name: epf-operations
description: Operations with external 1C data processors (EPF) — creation, adding forms and templates. Use when running epf init, add-form, add-template, add-attribute, add-tabular-section.
---

# EPF Operations

Working with external 1C data processors (ExternalDataProcessor).

## When to apply

| Trigger | Action |
|---------|----------|
| Need to create a new external data processor | `epf init --name <Name> <output_dir>` |
| Need to add a form to the processor | `epf add-form --epf <EpfName> --name <FormName> <output_dir>` |
| Need to add a print form (tabular document) | `epf add-template --epf ... --name ... --type spreadsheet <output_dir>` |
| Need to add an HTML/text template | `epf add-template --type html` or `--type text` |
| Need to add an attribute to an existing processor | `epf add-attribute --name ... --type ... <EpfRoot.xml>` |
| Need to add a tabular section to an existing processor | `epf add-tabular-section --name ... <EpfRoot.xml>` |

## Commands

### epf init

Create a new external data processor.

**Syntax:**
```bash
xml-gen epf init --name <Name> [--format designer|edt] [--synonym <Synonym>] <output_dir>
```

**Parameters:**
- `--name <Name>` — processor name (required)
- `--format designer|edt` — output format (default: designer)
- `--synonym <Synonym>` — synonym (optional)
- `<output_dir>` — output directory (required, positional argument)

**Example:**
```bash
xml-gen epf init --name MyProcessor output/
xml-gen epf init --format designer --name DataImport --synonym "Data Import" .
```

### epf add-form

Add a form to the processor.

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

Add a template to the processor.

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

Modify an existing EPF (add attributes, tabular sections). Works with the processor root XML.

```bash
xml-gen epf add-attribute --name <Name> [--type <Type>] [--synonym <Synonym>] <EpfRoot.xml>
xml-gen epf add-tabular-section --name <Name> [--synonym <Synonym>] <EpfRoot.xml>
```

**Example:**
```bash
xml-gen epf add-attribute --name Employee --type CatalogRef.Сотрудники --synonym "Employee" output/MyProcessor.xml
```

## EPF Structure (Designer)

```
MyProcessor/
├── MyProcessor.xml              # Root metadata file
├── Ext/
│   └── ObjectModule.bsl         # Object module
├── Forms/
│   └── MainForm/
│       ├── Form.xml             # Form metadata
│       └── Ext/
│           └── Form/
│               └── Module.bsl    # Form module
└── Templates/
    └── PrintForm/
        ├── Template.xml
        └── Ext/
            └── Template.mxl     # Tabular document
```

## Integration with form compile and mxl compile

After creating the form:

```bash
xml-gen form compile form.json output/MyProcessor/Forms/MainForm/Ext/Form.xml
```

After creating the template:

```bash
xml-gen mxl compile template.json output/MyProcessor/Templates/PrintForm/Ext/Template.xml
```

## Right / Wrong

```bash
# ❌ Wrong — epf add-form with positional arguments (CLI expects --epf, --name)
xml-gen epf add-form MyProcessor MainForm

# ✅ Right — named arguments, output_dir last
xml-gen epf add-form --epf MyProcessor --name MainForm output/
```

> The CLI only parses named arguments. `output_dir` is the final positional argument (the directory containing `<EpfName>.xml`).

```bash
# ❌ Wrong — epf add-attribute against Form.xml (add-attribute edits the processor root XML)
xml-gen epf add-attribute --name Employee MyProcessor/Forms/MainForm/Ext/Form.xml

# ✅ Right — path to the processor root XML (MyProcessor.xml)
xml-gen epf add-attribute --name Employee --type CatalogRef.Сотрудники output/MyProcessor.xml
```

> `epf add-attribute` adds an attribute to the **processor**, not the form. For a form use `form add-attribute` with Form.xml.

## See also

- [xml-generation](../xml-generation/) — general overview
- [form-dsl](../form-dsl/) — form content generation
- [mxl-dsl](../mxl-dsl/) — tabular document generation
- [xml-gen-cli](../xml-gen-cli/) — validate and edit commands

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
