---
name: xml-generation
description: "MUST use WHEN you need to create, modify, or validate any 1C metadata XML (forms, roles, objects, MXL, SCD, EPF, extensions, configuration). Provides safe generation and targeted modification through the xml-gen CLI while following the no-manual-xml-edit rule."
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

The unified `xml-gen` CLI covers the full lifecycle of 1C XML work: generation from JSON DSL, targeted modification of existing files, and validation. This SKILL.md is a **router**: it contains an overview, an index of sub-areas, and end-to-end principles. For detailed specifications for each domain, go to the corresponding sub-skill (`<name>/SKILL.md`).

## §1 Overview of the xml-gen CLI

Installation: `python tools/install.py --install-xml-gen` (requires JDK 17+).

`xml-gen` covers 4 types of operations — compile / edit / init / validate — details are in §2 and the sub-skills. Universal commands (validate, form/template/help add, edit replace-text) are described in §3.

**Do not use** when: EDT format is required (Designer only), DataSetUnion/CalculatedFields are needed in SCD (workaround: calculations in queries).

## §2 Index of sub-areas

| Sub-area | What it does | When to use | Reference |
|-------------|------------|-----------------|-----------|
| `forms-toolkit` | info / edit / validate / element-mapping / epf-validate — the operational workflow for managing forms and EPF | form structure analysis, adding fields, validation, Title→Name mapping for Vanessa | [forms-toolkit/SKILL.md](forms-toolkit/SKILL.md) |
| `form-dsl` | compile a form from JSON DSL (`form compile`, including `--from-object`) | create a form from scratch or generate it from an object | [form-dsl/SKILL.md](form-dsl/SKILL.md) |
| `skd-dsl` | compile SCD from JSON (`skd compile`) | create a data composition schema from scratch | [skd-dsl/SKILL.md](skd-dsl/SKILL.md) |
| `skd-edit` | patch operations on an existing SCD (`skd add-parameter`, `skd add-field`) | targeted editing of Schema.xml | [skd-edit/SKILL.md](skd-edit/SKILL.md) |
| `mxl-dsl` | MXL layouts / SpreadsheetDocument (`mxl compile`) | print forms, templates | [mxl-dsl/SKILL.md](mxl-dsl/SKILL.md) |
| `role-dsl` | compile roles (`role compile`, `role add-object`, `role add-right`) | create/modify a role | [role-dsl/SKILL.md](role-dsl/SKILL.md) |
| `config-operations` | work with the configuration root (`config init/info/edit/validate`) | initialize a new CF, navigate the root | [config-operations/SKILL.md](config-operations/SKILL.md) |
| `meta-operations` | 23 types of metadata objects (`meta compile/info/edit`) | Catalogs / Documents / InformationRegisters / Enums, etc. | [meta-operations/SKILL.md](meta-operations/SKILL.md) |
| `subsystem-interface` | subsystems and command interfaces (`subsystem compile/edit`, `interface edit/validate`) | organize the configuration interface | [subsystem-interface/SKILL.md](subsystem-interface/SKILL.md) |
| `epf-full` | external processors and reports (`epf init/add-form/add-template/bsp-init`) | create EPF / ERF from scratch, including БСП variants | [epf-full/SKILL.md](epf-full/SKILL.md) |
| `extension-operations` | configuration extensions / CFE (`extension init/borrow/diff`) | create a CFE, borrow objects, compare an extension with the base configuration | [extension-operations/SKILL.md](extension-operations/SKILL.md) |

> Universal commands (`xml-gen form add`, `template add`, `help add`, `edit replace-text`, `validate`) are described in §3 below and do not have a separate sub-skill.

## §3 Universal commands

Four groups: **validate** (structural/semantic validation of any XML), **form/template/help add** (adding forms, layouts, help to any metadata object), **edit replace-text** (byte-by-byte replacement without normalizing line endings).

When to use: validate — before and after every modification; form/template/help add — when you need to register a new artifact without rebuilding; edit replace-text — for targeted XML edits with multiline content in `<v8:content>` (tooltips, descriptions) or any replacement where preserving line endings matters.

→ [references/universal-commands.md](references/universal-commands.md)

## §4 Cross-cutting principles

1. **Designer only format** — `--format designer` (default). EDT is not supported.
2. **Encoding** — UTF-8 with BOM (`utf-8-sig`). Preserve the BOM when editing.
3. **Line endings** — CRLF between tags, bare LF in `<v8:content>`. Do not use Claude Code Edit — `xml-gen edit replace-text` (→ [references/universal-commands.md](references/universal-commands.md)).
4. **Idempotency** — `validate` before and after modification. On error, `<domain> edit` performs rollback automatically.
5. **Batch operations** — the JSON format for `form edit` / `meta edit` / `subsystem edit` accepts arrays of operations; use it instead of repeated CLI calls.
6. **EPF layout** — root XML: `output/MyProcessor.xml`. EPF forms: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`.

## §5 Quick examples (entry-level workflows)

### Create an external processor with a form

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

### Compile SCD from JSON

```bash
xml-gen skd compile schema.json Template.xml
xml-gen validate --type skd Template.xml
```

Details — [skd-dsl/SKILL.md](skd-dsl/SKILL.md). For targeted editing of a ready-made Schema.xml — [skd-edit/SKILL.md](skd-edit/SKILL.md).

### Create an extension and borrow an object

```bash
xml-gen extension init output_ext/ МоёРасширение --config-path output/
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

## §7 Additional protection layers (for agents without PreToolUse)

For agents without the PreToolUse protocol (Codex, Cursor, Aider, Cline, etc.), it is recommended to configure additional protection layers:

- **Git pre-commit hook** (`tools/hooks/pre-commit`) — extend it with a `--check` call for all staged `.xml`/`.mxl` files. This is a late safety net: it prevents the change from reaching the repository even if the agent ignored the rule:
  ```bash
  python3 tools/hooks/block-direct-xml-edit.py --check "<staged-file>" --tool Edit
  ```
  If the exit code is `2`, the file belongs to 1C metadata and the commit is aborted.
- **CI on PR** — the same `--check` over the diff catches any attempts to directly edit on the way into `main`.

Fine tuning: the `ONEC_ROOT_DIRS`, `EXCLUDE_SUBSTRINGS`, `EXCLUDE_BASENAMES` lists are defined as constants in `tools/hooks/block-direct-xml-edit.py`. Extend them if the project introduces a new 1C configuration pattern (for example, a non-standard location) or a new false positive case (build XML with a unique name).

## §8 Workarounds

| Problem | Solution |
|----------|---------|
| `Parent element not found` (form) | Check the exact parent name in Form.xml — case matters |
| `Object already exists` (role) | `role add-right` instead of `add-object` |
| `DataSet not found` (skd) | Check the data set name in Schema.xml |
| Edit tool breaks line endings | Use `xml-gen edit replace-text` |
| DataSetUnion / CalculatedFields are needed in SCD | Workaround: calculations in queries |
| EDT format is needed | Not supported, Designer only |

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
