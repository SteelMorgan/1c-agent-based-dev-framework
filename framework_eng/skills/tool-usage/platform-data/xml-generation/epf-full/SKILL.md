---
name: epf-full
description: "Use for creating external processors and reports (EPF/ERF), adding forms, templates, and help, and connecting to the BSP subsystem «Additional Reports and Processors». Helps go through the full init→add-form→template→BSP registration cycle via xml-gen."
targets:
  - developer-code
  - architect
---

# EPF Full — Full Cycle of External Processors

Workflow: `epf init → add-form → add-template → BSP registration (BSL)`. Steps 1–3 are done through the `xml-gen` CLI, BSP is direct editing of `ObjectModule.bsl`. For templates of configuration objects — see §4.

---

## §2 Quick command index

| Task | Command |
|--------|---------|
| Create a processor | `xml-gen epf init --name <Name> output/` |
| Create an external report (ERF) | `xml-gen epf init --type report --name <Name> output/` |
| Add a form | `xml-gen epf add-form --epf <Name> --name <FormName> output/` |
| Add an MXL template to EPF | `xml-gen epf add-template --epf <Name> --name <T> --type spreadsheet output/` |
| Add an attribute to the processor | `xml-gen epf add-attribute --name <N> --type <T> output/<Name>.xml` |
| Add a tabular section to the processor | `xml-gen epf add-tabular-section --name <N> output/<Name>.xml` |
| Register in BSP (printing) | Insert `СведенияОВнешнейОбработке()` into `ObjectModule.bsl` — see §5 |
| Add a BSP command | Insert the command block before `Возврат` — see §5 |
| Add a template to a Справочник/Документ | `xml-gen template add --object <Type.Name> --name <T> --type <TemplateType> src/` |
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

- The CLI accepts only **named arguments** `--epf`, `--name`; `output_dir` is the last positional argument.
- `epf add-attribute` edits the **root XML of the processor** (`<Name>.xml`), not Form.xml. For a form, use `form add-attribute`.

---

## §4 Templates — templates for any metadata objects

`template add / remove / add-help` — for Catalog, Document, Report, DataProcessor, InformationRegister, AccumulationRegister, and others.

> **[references/templates.md](references/templates.md)**

- `--object` is required, format `Type.Name` (example: `Document.ЗаказКлиента`).
- For DCS reports, when adding the schema for the first time, use `--set-main-dcs`.
- Apply the `ПФ_` prefix to SpreadsheetDocument automatically.

**Template types for `xml-gen epf add-template` (4 supported):**
| `--type` | Purpose |
|----------|-----------|
| `SpreadsheetDocument` | Print form (MXL) |
| `HTMLDocument` | HTML template |
| `TextDocument` | Text template |
| `BinaryData` | Binary data |

> **`DataCompositionSchema` in `epf add-template` is NOT supported** — the CLI responds with `Failed to add template: Unknown template type: DataCompositionSchema`. To add a DCS template to an EPF, use the universal command `xml-gen template add --object DataProcessor.<EpfName> --name <T> --type DataCompositionSchema <output_dir>` (it knows all 5 types, including `DataCompositionSchema`). See also section §3 in [xml-generation/SKILL.md](../SKILL.md) and [references/universal-commands.md](../references/universal-commands.md).

After adding an MXL template, fill it with contents through `xml-gen mxl compile invoice.json <path to Template.xml>`.

---

## §5 EPF BSP — registration in «Additional Reports and Processors»

> **[references/epf-bsp.md](references/epf-bsp.md)**

- BSP registration is **BSL code** in `ObjectModule.bsl`, not a CLI command.
- `СведенияОВнешнейОбработке()` goes in the `#Область ПрограммныйИнтерфейс` region.
- **Assignable types** (ЗаполнениеОбъекта, Отчет, ПечатнаяФорма, СозданиеСвязанныхОбъектов) require `Назначение.Добавить(...)`.
- **Global types** (ДополнительнаяОбработка, ДополнительныйОтчет) are without an assignment.
- Additional commands: use `НСтр("ru = '...'")` for `Представление` (not `МетаданныеОбработки.Представление()`).
- Mapping of BspKind → ВидОбработки, BspCommandType → ТипКоманды — see references/epf-bsp.md.

---
depends_on:
  - mxl-dsl
  - meta-operations
metadata:
  category: 1c-development
  version: "1.0"
---
