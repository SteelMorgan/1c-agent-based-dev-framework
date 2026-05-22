---
name: xml-generation
description: "Unified toolkit for generating, editing, and validating 1C XML metadata through the xml-gen CLI. Covers 11 domains (EPF, Form, MXL, SKD, Role, Config, Subsystem, Interface, Meta 23 types, Extension/CFE) + universal operations (validate, edit replace-text, form/template/help add). ~45 CLI operations in Designer format. Use it when creating configurations and external data processors, adding metadata objects, forms, roles, reports, print forms, extensions; and when validating and making targeted modifications to existing XML."
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

# xml-generation — Toolkit for working with 1C XML metadata

The unified `xml-gen` CLI covers the full workflow for working with 1C XML: generation from JSON DSL, targeted modification of existing files, and validation. This SKILL.md is a **router**: it contains an overview, an index of subareas, and cross-cutting principles. For detailed specifications for each domain, go to the corresponding sub-skill (`<name>/SKILL.md`).

## §1 Overview of the xml-gen CLI

Install: `python tools/install.py --install-xml-gen` (requires JDK 17+).

`xml-gen` covers 4 types of operations — compile / edit / init / validate — details are in §2 and the sub-skills. Universal commands (validate, form/template/help add, edit replace-text) are described in §3.

**Do not use** when: EDT format is needed (Designer only), DataSetUnion/CalculatedFields are needed in SKD (workaround: calculations in queries).

## §2 Index of subareas

| Subarea | What it does | When to use | Reference |
|-------------|------------|-----------------|-----------|
| `forms-toolkit` | info / edit / validate / element-mapping / epf-validate — operational workflow for managed forms and EPF | analyze form structure, add fields, validate, Title→Name mapping for Vanessa | [forms-toolkit/SKILL.md](forms-toolkit/SKILL.md) |
| `form-dsl` | compile a form from JSON DSL (`form compile`, incl. `--from-object`) | create a form from scratch or generate it from an object | [form-dsl/SKILL.md](form-dsl/SKILL.md) |
| `skd-dsl` | compile SKD from JSON (`skd compile`) | create a data composition schema from scratch | [skd-dsl/SKILL.md](skd-dsl/SKILL.md) |
| `skd-edit` | patch operations on an existing SKD (`skd add-parameter`, `skd add-field`) | targeted editing of `Schema.xml` | [skd-edit/SKILL.md](skd-edit/SKILL.md) |
| `mxl-dsl` | MXL layouts / SpreadsheetDocument (`mxl compile`) | print forms, templates | [mxl-dsl/SKILL.md](mxl-dsl/SKILL.md) |
| `role-dsl` | compile roles (`role compile`, `role add-object`, `role add-right`) | create / modify a role | [role-dsl/SKILL.md](role-dsl/SKILL.md) |
| `config-operations` | work with the configuration root (`config init/info/edit/validate`) | initialize a new CF, navigate the root | [config-operations/SKILL.md](config-operations/SKILL.md) |
| `meta-operations` | 23 metadata object types (`meta compile/info/edit`) | Catalogs / Documents / InformationRegisters / Enums and others | [meta-operations/SKILL.md](meta-operations/SKILL.md) |
| `subsystem-interface` | subsystems and command interfaces (`subsystem compile/edit`, `interface edit/validate`) | organize the configuration interface | [subsystem-interface/SKILL.md](subsystem-interface/SKILL.md) |
| `epf-full` | external data processors and reports (`epf init/add-form/add-template/bsp-init`) | create EPF / ERF from scratch, including БСП variants | [epf-full/SKILL.md](epf-full/SKILL.md) |
| `extension-operations` | configuration extensions / CFE (`extension init/borrow/diff`) | create a CFE, borrow objects, compare an extension with the base | [extension-operations/SKILL.md](extension-operations/SKILL.md) |

> Universal commands (`xml-gen form add`, `template add`, `help add`, `edit replace-text`, `validate`) are described in §3 below and do not have a separate sub-skill.

## §3 Universal commands

Four groups: **validate** (structural/semantic check of any XML), **form/template/help add** (adding forms, templates, help to any metadata object), **edit replace-text** (byte-level replacement without normalizing line endings).

When to use: validate — before and after each modification; form/template/help add — when you need to register a new artifact without rebuilding; edit replace-text — when making targeted XML edits with multi-line content in `<v8:content>` (tooltips, descriptions) or any replacement where preserving line endings matters.

→ [references/universal-commands.md](references/universal-commands.md)

## §4 Cross-cutting principles

1. **Designer format only** — `--format designer` (default). EDT is not supported.
2. **Encoding** — UTF-8 with BOM (`utf-8-sig`). Preserve the BOM when editing.
3. **Line endings** — CRLF between tags, bare LF in `<v8:content>`. Do not use Claude Code Edit — `xml-gen edit replace-text` (→ [references/universal-commands.md](references/universal-commands.md)).
4. **Idempotency** — `validate` before and after modification. On error, `<domain> edit` rolls back automatically.
5. **Batch operations** — the JSON format for `form edit` / `meta edit` / `subsystem edit` accepts arrays of operations; use it instead of repeated CLI calls.
6. **EPF layout** — root XML: `output/MyProcessor.xml`. EPF forms: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`.

## §5 Quick examples (entry-level workflows)

### Create an external data processor with a form

```bash
xml-gen epf init --name MyProcessor output/
xml-gen epf add-form --epf MyProcessor --name MainForm output/
xml-gen validate --type epf output/MyProcessor
```

Details — [epf-full/SKILL.md](epf-full/SKILL.md).

### Add a field to an existing form

```bash
# 1. Изучить структуру
xml-gen form info "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 2. Добавить элемент с привязкой к реквизиту
xml-gen form add-element --type InputField --name Склад --path Объект.Склад \
  --parent ГруппаШапка --after Контрагент \
  "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 3. Проверить
xml-gen validate --type form "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"
```

Details — [forms-toolkit/SKILL.md](forms-toolkit/SKILL.md) (info/edit/validate) and [form-dsl/SKILL.md](form-dsl/SKILL.md) (compile from scratch).

### Compile SKD from JSON

```bash
xml-gen skd compile schema.json Template.xml
xml-gen validate --type skd Template.xml
```

Details — [skd-dsl/SKILL.md](skd-dsl/SKILL.md). For targeted editing of an existing `Schema.xml` — [skd-edit/SKILL.md](skd-edit/SKILL.md).

### Create an extension and borrow an object

```bash
xml-gen extension init --name МоёРасширение --config output/ output_ext/
xml-gen extension borrow output_ext/ output/ "Catalog.Товары"
xml-gen extension diff output_ext/ output/
```

Details — [extension-operations/SKILL.md](extension-operations/SKILL.md).

## §6 Anti-patterns (correct / incorrect)

```bash
# Неправильно: role compile с файлом на выход
xml-gen role compile role.json Roles/МояРоль.xml

# Правильно: output_dir → Roles/<Name>/Ext/Rights.xml
xml-gen role compile role.json output/
```

```bash
# Неправильно: form add-element без --path
xml-gen form add-element --type InputField --name Наименование Form.xml

# Правильно: --path связывает элемент с реквизитом
xml-gen form add-element --type InputField --name Наименование --path Наименование Form.xml
```

```bash
# Неправильно: role add-object с "view"
xml-gen role add-object --name Catalog.Номенклатура --rights view Rights.xml

# Правильно: права через запятую, регистр из enum RoleRight
xml-gen role add-object --name Catalog.Номенклатура --rights Read,View Rights.xml
```

## §7 Workarounds

| Problem | Solution |
|----------|---------|
| `Parent element not found` (form) | Check the exact parent name in Form.xml — case matters |
| `Object already exists` (role) | Use `role add-right` instead of `add-object` |
| `DataSet not found` (skd) | Check the data set name in `Schema.xml` |
| Edit tool breaks line endings | Use `xml-gen edit replace-text` |
| Need DataSetUnion / CalculatedFields in SKD | Workaround: calculations in queries |
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
