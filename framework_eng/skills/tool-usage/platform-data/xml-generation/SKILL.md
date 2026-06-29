---
name: xml-generation
description: "For any 1C metadata XML via the xml-gen CLI"
argument-hint: <domain> <operation> [<args>]
allowed-tools:
  - Bash
  - Read
  - Write
  - Glob
metadata:
  category: 1c-development
  version: "2.0"
---

# xml-generation — Toolkit for working with 1C metadata XML

A single `xml-gen` CLI covers the entire 1C XML workflow: generation from JSON DSL, targeted modification of existing files, and validation. This SKILL.md is a **router**: it contains an overview, an index of sub-areas, and end-to-end principles. For detailed specifications for each domain, go to the corresponding sub-skill (`<name>/SKILL.md`).

## §1 xml-gen CLI Overview

Installation: `python tools/install.py --install-xml-gen` (requires JDK 17+).

`xml-gen` has two complementary work surfaces:

- **JSON DSL surface** — `mxl/skd/form/role/meta compile` and available decompilers, for example `mxl decompile`, `form decompile`. Use this when the artifact is easier to describe declaratively and compile into Designer XML. `form decompile` is draft/scaffold only, not a lossless round-trip.
- **Operational CLI surface** — public commands `epf init`, `epf add-template`, `form add-element`, `meta edit`, `template add`, `validate`. Use this when you need to create or modify an existing metadata tree through explicit CLI actions.
- **Support safety surface** — `support check/info` and the built-in mutation guard. `xml-gen` reads `Ext/ParentConfigurations.bin` and blocks direct XML edits to objects in a vendor-supported standard configuration.

To support the tool itself, `xml-gen` includes diagnostic oracle commands. They are not needed for ordinary XML generation/editing tasks; the reference for them is provided separately: [references/behavioral-oracles.md](references/behavioral-oracles.md).

Details are in §2 and the sub-skills. Universal commands (validate, form/template/help add, edit replace-text) are described in §3.

**Do not use** when: EDT format is needed (Designer only), DataSetUnion/CalculatedFields are needed in the data composition schema (workaround: calculations in queries).

## §2 Index of Sub-Areas

| Sub-area | What it does | When to use | Reference |
|-------------|------------|-----------------|-----------|
| `forms-toolkit` | info / edit / validate / element-mapping / epf-validate — the operational workflow for managed forms and EPF | analyze form structure, add fields, validate, map Title→Name for Vanessa | [forms-toolkit/SKILL.md](forms-toolkit/SKILL.md) |
| `form-dsl` | compile a form from JSON DSL (`form compile`, including `--from-object`) and draft `form decompile` | create a form from scratch, generate it from an object, or extract a JSON draft from an example | [form-dsl/SKILL.md](form-dsl/SKILL.md) |
| `skd-dsl` | compile the data composition schema from JSON (`skd compile`) | create a data composition schema from scratch | [skd-dsl/SKILL.md](skd-dsl/SKILL.md) |
| `skd-edit` | patch operations on an existing data composition schema (`skd add-parameter`, `skd add-field`) | targeted edits to Schema.xml | [skd-edit/SKILL.md](skd-edit/SKILL.md) |
| `mxl-dsl` | MXL layouts / SpreadsheetDocument (`mxl compile`) | print forms, templates | [mxl-dsl/SKILL.md](mxl-dsl/SKILL.md) |
| `role-dsl` | compile roles (`role compile`, `role add-object`, `role add-right`) | create or modify a role | [role-dsl/SKILL.md](role-dsl/SKILL.md) |
| `config-operations` | work with the configuration root (`config init/info/edit/validate`) | initialize a new CF, navigate the root | [config-operations/SKILL.md](config-operations/SKILL.md) |
| `meta-operations` | 23 metadata object types (`meta compile/info/edit`) | Catalogs / Documents / InformationRegisters / Enums and others | [meta-operations/SKILL.md](meta-operations/SKILL.md) |
| `subsystem-interface` | subsystems and command interfaces (`subsystem compile/edit`, `interface edit/validate`) | organize the configuration interface | [subsystem-interface/SKILL.md](subsystem-interface/SKILL.md) |
| `epf-full` | external processors and reports (`epf init/add-form/add-template/bsp-init`) | create EPF / ERF from scratch, including БСП variants | [epf-full/SKILL.md](epf-full/SKILL.md) |
| `extension-operations` | configuration extensions / CFE (`extension init/borrow/diff`) | create a CFE, borrow objects, compare the extension with the base | [extension-operations/SKILL.md](extension-operations/SKILL.md) |

> Universal commands (`xml-gen form add`, `template add`, `help add`, `edit replace-text`, `validate`, `support check/info`) are described in §3 below and do not have a separate sub-skill.

## §3 Universal Commands

Five groups: **validate** (structural/semantic validation of any XML), **support check/info** (check vendor support status), **form/template/help add** (add forms, layouts, help to any metadata object), **edit replace-text** (byte-level replacement without normalizing line endings).

When to use: validate — before and after every modification; support check/info — before intentional edits to a vendor-supported standard configuration or in hooks; form/template/help add — when you need to register a new artifact without rebuilding; edit replace-text — for targeted XML edits with multiline content in `<v8:content>` (tooltips, descriptions) or any replacement where preserving line endings matters.

→ [references/universal-commands.md](references/universal-commands.md)

## §3.1 Tool Diagnostics

Oracle commands are intended to support `xml-gen` and verify behavior on canonical XML, not for ordinary generation of a single artifact. See details, modes, and test matrices in [references/behavioral-oracles.md](references/behavioral-oracles.md).

## §4 End-to-End Principles

1. **Designer format only** — `--format designer` (default). EDT is not supported.
2. **Encoding** — UTF-8 with BOM (`utf-8-sig`). Preserve the BOM when editing.
3. **Line endings** — CRLF between tags, bare LF in `<v8:content>`. Do not use Claude Code Edit — `xml-gen edit replace-text` (→ [references/universal-commands.md](references/universal-commands.md)).
4. **Idempotency** — `validate` before and after modification. On error, `<domain> edit` rolls back automatically.
5. **Batch operations** — the JSON format for `form edit` / `meta edit` / `subsystem edit` accepts arrays of operations; use them instead of repeated CLI calls.
6. **EPF layout** — root XML: `output/MyProcessor.xml`. EPF forms: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`.
7. **Oracle sandboxing** — `xml-gen oracle ...` reads the canon and writes generated XML only under `--out`; do not place oracle output inside `src/xml`.
8. **Vendor support guard** — mutation commands inside `xml-gen` check `Ext/ParentConfigurations.bin`: `G=1` blocks the entire configuration, `f1=0` blocks the object, deletion requires `f1=2`. CFE extensions are not blocked.

## §5 Quick Examples (entry-level workflows)

### Create an external processor with a form

```bash
xml-gen epf init --name MyProcessor output/
xml-gen epf add-form --epf MyProcessor --name MainForm output/
xml-gen validate --type epf output/MyProcessor
```

Details — [epf-full/SKILL.md](epf-full/SKILL.md).

### Add a field to an existing form

```bash
# 1. Study the structure
xml-gen form info "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 2. Add an element bound to a property
xml-gen form add-element --type InputField --name Склад --path Объект.Склад \
  --parent ГруппаШапка --after Контрагент \
  "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 3. Check
xml-gen validate --type form "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"
```

Details — [forms-toolkit/SKILL.md](forms-toolkit/SKILL.md) (info/edit/decompile/validate) and [form-dsl/SKILL.md](form-dsl/SKILL.md) (compile from scratch).

### Compile the data composition schema from JSON

```bash
xml-gen skd compile schema.json Template.xml
xml-gen validate --type skd Template.xml
```

Details — [skd-dsl/SKILL.md](skd-dsl/SKILL.md). For targeted edits to an existing Schema.xml — [skd-edit/SKILL.md](skd-edit/SKILL.md).

### Create an extension and borrow an object

```bash
xml-gen extension init output_ext/ МоёРасширение --config-path output/
xml-gen extension borrow output_ext/ output/ "Catalog.Товары"
xml-gen extension diff output_ext/ output/
```

Details — [extension-operations/SKILL.md](extension-operations/SKILL.md).

### Check support status before editing

```bash
xml-gen support info "src/Catalogs/Номенклатура.xml"
xml-gen support check "src/Catalogs/Номенклатура.xml" --require editable
xml-gen support check "src/Catalogs/Номенклатура.xml" --require removed --output json
```

`support check` ends the command with an error if the mutation is forbidden. To remove an object, you first need an explicit transition of the object to an off-support state through an external agreed action; `xml-gen` does not remove support implicitly.

## §6 Anti-Patterns (correct / incorrect)

```bash
# Incorrect: role compile with a file as output
xml-gen role compile role.json Roles/МояРоль.xml

# Correct: output_dir → Roles/<Name>/Ext/Rights.xml
xml-gen role compile role.json output/
```

```bash
# Incorrect: form add-element without --path
xml-gen form add-element --type InputField --name Наименование Form.xml

# Correct: --path binds the element to a property
xml-gen form add-element --type InputField --name Наименование --path Наименование Form.xml
```

```bash
# Incorrect: role add-object with "view"
xml-gen role add-object --name Catalog.Номенклатура --rights view Rights.xml

# Correct: rights are comma-separated, casing from the RoleRight enum
xml-gen role add-object --name Catalog.Номенклатура --rights Read,View Rights.xml
```

## §7 Additional Protection Layers (for agents without PreToolUse)

For agents without the PreToolUse protocol (Codex, Cursor, Aider, Cline, etc.), it is recommended to configure additional protection layers:

- **Git pre-commit hook** (`tools/hooks/pre-commit`) — extend it with a `--check` call for all staged `.xml`/`.mxl` files. This is the last line of defense: it prevents the change from reaching the repository even if the agent ignored the rule:
  ```bash
  python3 tools/hooks/block-direct-xml-edit.py --check "<staged-file>" --tool Edit
  ```
  On exit code `2`, the file belongs to 1C metadata, and the commit is aborted.
- **CI on PR** — the same `--check` over the diff catches any attempt at direct edits before they land in `main`.

Fine-tuning: the `ONEC_ROOT_DIRS`, `EXCLUDE_SUBSTRINGS`, `EXCLUDE_BASENAMES` lists are defined as constants in `tools/hooks/block-direct-xml-edit.py`. Extend them if the project introduces a new 1C configuration pattern (for example, a non-standard location) or a new false positive case (build XML with a unique name).

## §8 Workarounds

| Problem | Solution |
|----------|---------|
| `Parent element not found` (form) | Check the exact parent name in Form.xml — case matters |
| `Object already exists` (role) | Use `role add-right` instead of `add-object` |
| `DataSet not found` (skd) | Check the dataset name in Schema.xml |
| Edit tool breaks line endings | Use `xml-gen edit replace-text` |
| Need DataSetUnion / CalculatedFields in the data composition schema | Workaround: calculations in queries |
| Need EDT format | Not supported, Designer only |

---
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/forms-toolkit/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/form-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/skd-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/skd-edit/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/mxl-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/role-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/config-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/meta-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/subsystem-interface/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/epf-full/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/extension-operations/SKILL.md
---
