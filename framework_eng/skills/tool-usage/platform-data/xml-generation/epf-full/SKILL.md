---
name: epf-full
description: "xml-gen EPF/ERF: external reports and data processors"
targets:
  - developer-code
  - architect
---

# EPF Full — Complete cycle of external data processors

Workflow: `epf init → add-form → add-template → BSP registration (BSL)`. Steps 1–3 are via the `xml-gen` CLI, BSP is direct editing of `ObjectModule.bsl`. For configuration object templates, see §4.

---

## §2 Quick command index

| Task | Command |
|--------|---------|
| Create a data processor | `xml-gen epf init --name <Name> output/` |
| Create an external report (ERF) | `xml-gen epf init --type report --name <Name> output/` |
| Create an external SKD report (ERF) | `xml-gen epf init --type report --name <Name> --with-skd output/` |
| Add a form | `xml-gen epf add-form --epf <Name> --name <FormName> output/` |
| Add an MXL template to EPF | `xml-gen epf add-template --epf <Name> --name <T> --type spreadsheet output/` |
| Add an attribute to the data processor | `xml-gen epf add-attribute --name <N> --type <T> output/<Name>.xml` |
| Add a tabular section to the data processor | `xml-gen epf add-tabular-section --name <N> output/<Name>.xml` |
| Register in БСП (printing) | Insert `СведенияОВнешнейОбработке()` into `ObjectModule.bsl` — see §5 |
| Add a БСП command | Insert the command block before `Возврат` — see §5 |
| Add a template to a Catalog/Document | `xml-gen template add --object <Type.Name> --name <T> --type <TemplateType> src/` |
| Remove a template | `xml-gen template remove --object <Type.Name> --name <T> src/` |
| Add built-in help | `xml-gen template add-help --object <Type.Name> src/` |

**Key paths (Designer):**
- Root XML: `output/<Name>.xml`
- Object module: `output/<Name>/Ext/ObjectModule.bsl`
- Form.xml: `output/<Name>/Forms/<FormName>/Ext/Form.xml`
- Template: `output/<Name>/Templates/<TName>/Ext/Template.xml`

---

## §3 EPF Base — init, add-form, add-template

> **[references/epf-base.md](references/epf-base.md)**

- CLI accepts only **named arguments** `--epf`, `--name`; `output_dir` is the last positional argument.
- `epf add-attribute` edits the **root XML of the processing** (`<Name>.xml`), not Form.xml. For a form — `form add-attribute`.
- For an external DCS report, `epf init --type report --with-skd` is preferred: the generated ERF must contain an empty `DefaultForm`, `MainDataCompositionSchema`, and a DCS template. By default, it **must not** create a form.
- An empty `DefaultForm` and the absence of `Forms/` for reports are the standard case for both external ERFs and built-in configuration reports. The platform automatically uses the standard report form. Add your own form only when you explicitly need to customize the form or intercept client form events.
- `epf add-form` is a separate explicit operation. If a form is explicitly added to an external report, the first form becomes `DefaultForm`, and its main attribute must be `Отчет`, not `Объект`.
- After generating the ERF, run `xml-gen validate --type epf output/<Name>.xml` and check the root XML for the `DefaultForm`/`MainDataCompositionSchema`/`Template` triad before building the binary `.erf`. For a regular DCS report, do not require `<Form>`.
- In the root XML, the external object and its contained object must have different identifiers: `ExternalReport/@uuid` or `ExternalDataProcessor/@uuid` must not equal `InternalInfo/ContainedObject/ObjectId`. `xml-gen validate --type epf` should diagnose equality as `EPF-018`, but after validation still perform the Designer package/load/open check of the built EPF/ERF.
- If the generator violates this invariant, stop building the dependent object and apply the defect-fix process from `xml-generation` §4.1. Do not manually change either identifier in the generated XML: the fix must pass the regression test, a full build of the tool, and a repeated Designer/live-check of the source object.

---

## §4 Templates — templates for any metadata objects

`template add / remove / add-help` — for Catalog, Document, Report, DataProcessor, InformationRegister, AccumulationRegister, etc.

> **[references/templates.md](references/templates.md)**

- `--object` is required, format `Type.Name` (example: `Document.ЗаказКлиента`).
- For DCS reports, use `--set-main-dcs` when adding the schema for the first time.
- The `ПФ_` prefix for SpreadsheetDocument should be applied automatically.

**Template types for `xml-gen epf add-template` (4 supported):**
| `--type` | Purpose |
|----------|-----------|
| `SpreadsheetDocument` | Print form (MXL) |
| `HTMLDocument` | HTML template |
| `TextDocument` | Text template |
| `BinaryData` | Binary data |

For external reports, `epf init --type report --with-skd` creates the main `DataCompositionSchema` and links it as `MainDataCompositionSchema`. For an existing ERF, `epf add-template --type DataCompositionSchema` is supported, and it must populate `ExternalReport.<Name>.Template.<TemplateName>`.

After adding an MXL template, fill its contents with `xml-gen mxl compile invoice.json <path to Template.xml>`.

---

## §5 EPF БСП — registration in "Additional Reports and Data Processing"

> **[references/epf-bsp.md](references/epf-bsp.md)**

- BSP registration is **BSL code** in `ObjectModule.bsl`, not a CLI command.
- `СведенияОВнешнейОбработке()` is in the `#Область ПрограммныйИнтерфейс` scope.
- **Assignable types** (ЗаполнениеОбъекта, Отчет, ПечатнаяФорма, СозданиеСвязанныхОбъектов) require `Назначение.Добавить(...)`.
- **Global types** (ДополнительнаяОбработка, ДополнительныйОтчет) are without assignment.
- Additional commands: `НСтр("ru = '...'")` for Presentation (not `МетаданныеОбработки.Представление()`).
- Mapping BspKind → ВидОбработки, BspCommandType → ТипКоманды — see references/epf-bsp.md.

---

depends_on:
  - mxl-dsl
  - meta-operations
metadata:
  category: 1c-development
  version: "1.0"
---
