# EPF Base — creating and modifying external processors/reports

Working with 1C external processors (ExternalDataProcessor) and external reports (ExternalReport / ERF).

## epf init

Create a new external processor or report.

**Syntax:**
```bash
xml-gen epf init --name <Name> [--type processor|report] [--format designer|edt] [--synonym <Synonym>] <output_dir>
```

**Parameters:**
- `--name <Name>` — processor/report name (required)
- `--type processor|report` — type: `processor` (processor, default) or `report` (external report, ERF)
- `--format designer|edt` — output format (default: designer)
- `--synonym <Synonym>` — synonym (optional)
- `<output_dir>` — output directory (required, positional argument)

**Example:**
```bash
xml-gen epf init --name MyProcessor output/
xml-gen epf init --format designer --name DataImport --synonym "Импорт данных" .
xml-gen epf init --type report --name SalesReport --synonym "Отчёт по продажам" output/
```

## epf add-form

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

## epf add-template

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

## epf add-attribute / add-tabular-section (editing)

Modification of an existing EPF (adding attributes, tabular sections). Works with the root XML of the processor.

```bash
xml-gen epf add-attribute --name <Name> [--type <Type>] [--synonym <Synonym>] <EpfRoot.xml>
xml-gen epf add-tabular-section --name <Name> [--synonym <Synonym>] <EpfRoot.xml>
```

**Example:**
```bash
xml-gen epf add-attribute --name Employee --type CatalogRef.Сотрудники --synonym "Сотрудник" output/MyProcessor.xml
```

## Key paths (Designer)

- Root XML: `output/MyProcessor.xml`
- Object module: `output/MyProcessor/Ext/ObjectModule.bsl`
- Form.xml: `output/MyProcessor/Forms/<FormName>/Ext/Form.xml`
- Template: `output/MyProcessor/Templates/<Name>/Ext/Template.xml`

Integration: `form compile form.json <Form.xml path>`, `mxl compile template.json <Template path>`.

## Correct / Incorrect

```bash
# Incorrect — epf add-form with positional arguments (CLI expects --epf, --name)
xml-gen epf add-form MyProcessor MainForm

# Correct — named arguments, output_dir at the end
xml-gen epf add-form --epf MyProcessor --name MainForm output/
```

> The CLI parses named arguments only. `output_dir` is the last positional argument (the directory where `<EpfName>.xml` is located).

```bash
# Incorrect — epf add-attribute to Form.xml (add-attribute edits the root XML of the processor)
xml-gen epf add-attribute --name Employee MyProcessor/Forms/MainForm/Ext/Form.xml

# Correct — path to the root XML of the processor (MyProcessor.xml)
xml-gen epf add-attribute --name Employee --type CatalogRef.Сотрудники output/MyProcessor.xml
```

> `epf add-attribute` adds an attribute to the **processor**, not to the form. For a form, use `form add-attribute` with Form.xml.
