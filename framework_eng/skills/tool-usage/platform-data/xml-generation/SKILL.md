---
name: xml-generation
description: "MUST use WHEN you need to create, modify, or validate any 1C metadata XML (forms, roles, objects, MXL, SKD, EPF, extensions, configuration). Provides safe generation and targeted modification through the xml-gen CLI while following the no-manual-xml-edit rule."
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

The unified `xml-gen` CLI covers the full 1C XML workflow: generation from a JSON DSL, targeted modification of existing files, and validation. This SKILL.md is a **router**: it contains an overview, an index of sub-areas, and cross-cutting principles. For detailed specifications for each domain, go to the corresponding sub-skill (`<name>/SKILL.md`).

## §1 Overview of the xml-gen CLI

Install: `python tools/install.py --install-xml-gen` (requires JDK 17+).

`xml-gen` has two complementary work surfaces:

- **JSON DSL surface** — `mxl/skd/form/role/meta compile` and available decompilers, for example `mxl decompile`. Use this when the artifact is easier to describe declaratively and compile into Designer XML.
- **Operational CLI surface** — public commands `epf init`, `epf add-template`, `form add-element`, `meta edit`, `template add`, `validate`. Use this when you need to create or modify an existing metadata tree with explicit CLI actions.

For maintaining the tool itself, `xml-gen` includes diagnostic oracle commands. They are not needed in normal XML generation/editing tasks; the reference for them is provided separately: [references/behavioral-oracles.md](references/behavioral-oracles.md).

Details are in §2 and the sub-skills. Universal commands (`validate`, `form/template/help add`, `edit replace-text`) are described in §3.

**Do not use** when: the EDT format is required (Designer only), DataSetUnion/CalculatedFields are needed in SKD (workaround: calculations in queries).

## §2 Index of sub-areas

| Sub-area | What it does | When to use | Reference |
|-------------|------------|-----------------|-----------|
| `forms-toolkit` | info / edit / validate / element-mapping / epf-validate — the operational cycle for working with managed forms and EPF | analyzing form structure, adding fields, validation, Title→Name mapping for Vanessa | [forms-toolkit/SKILL.md](forms-toolkit/SKILL.md) |
| `form-dsl` | compile a form from JSON DSL (`form compile`, including `--from-object`) | create a form from scratch or generate one from an object | [form-dsl/SKILL.md](form-dsl/SKILL.md) |
| `skd-dsl` | compile SKD from JSON (`skd compile`) | create a data composition schema from scratch | [skd-dsl/SKILL.md](skd-dsl/SKILL.md) |
| `skd-edit` | patch operations on an existing SKD (`skd add-parameter`, `skd add-field`) | targeted Schema.xml edits | [skd-edit/SKILL.md](skd-edit/SKILL.md) |
| `mxl-dsl` | MXL layouts / SpreadsheetDocument (`mxl compile`) | print forms, templates | [mxl-dsl/SKILL.md](mxl-dsl/SKILL.md) |
| `role-dsl` | compile roles (`role compile`, `role add-object`, `role add-right`) | create/modify a role | [role-dsl/SKILL.md](role-dsl/SKILL.md) |
| `config-operations` | work with the configuration root (`config init/info/edit/validate`) | initialize a new CF, navigate the root | [config-operations/SKILL.md](config-operations/SKILL.md) |
| `meta-operations` | 23 metadata object types (`meta compile/info/edit`) | Catalogs / Documents / InformationRegisters / Enums and others | [meta-operations/SKILL.md](meta-operations/SKILL.md) |
| `subsystem-interface` | subsystems and command interfaces (`subsystem compile/edit`, `interface edit/validate`) | organizing the configuration interface | [subsystem-interface/SKILL.md](subsystem-interface/SKILL.md) |
| `epf-full` | external processing and reports (`epf init/add-form/add-template/bsp-init`) | create EPF / ERF from scratch, including BSP variants | [epf-full/SKILL.md](epf-full/SKILL.md) |
| `extension-operations` | configuration extensions / CFE (`extension init/borrow/diff`) | create a CFE, borrow objects, compare an extension with its base | [extension-operations/SKILL.md](extension-operations/SKILL.md) |

> Universal commands (`xml-gen form add`, `template add`, `help add`, `edit replace-text`, `validate`) are described in §3 below and do not have a separate sub-skill.

## §3 Universal commands

Four groups: **validate** (structural/semantic validation of any XML), **form/template/help add** (adding forms, layouts, help to any metadata object), **edit replace-text** (byte-for-byte replacement without line ending normalization).

When to use: validate — before and after each modification; form/template/help add — when you need to register a new artifact without rebuilding; edit replace-text — for targeted XML edits with multiline text in `<v8:content>` (tooltips, descriptions) or any replacement where preserving line endings matters.

→ [references/universal-commands.md](references/universal-commands.md)

## §3.1 Tool diagnostics

Oracle commands are intended to support `xml-gen` and verify behavior on canonical XML, not for normal generation of a single artifact. Details, modes, and test matrices are in [references/behavioral-oracles.md](references/behavioral-oracles.md).

## §4 Cross-cutting principles

1. **Designer format only** — `--format designer` (default). EDT is not supported.
2. **Encoding** — UTF-8 with BOM (`utf-8-sig`). Preserve the BOM when editing.
3. **Line endings** — CRLF between tags, bare LF in `<v8:content>`. Do not use Claude Code Edit — `xml-gen edit replace-text` (→ [references/universal-commands.md](references/universal-commands.md)).
4. **Idempotency** — `validate` before and after modification. On error, `<domain> edit` performs rollback automatically.
5. **Batch operations** — the JSON format for `form edit` / `meta edit` / `subsystem edit` accepts arrays of operations; use that instead of repeated CLI calls.
6. **EPF layout** — root XML: `output/MyProcessor.xml`. EPF forms: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`.
7. **Oracle sandboxing** — `xml-gen oracle ...` reads the canonical source and writes generated XML only under `--out`; do not point oracle output inside `src/xml`.

## §5 Quick examples (entry-level workflows)

### Create an external processing with a form

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

Details — [skd-dsl/SKILL.md](skd-dsl/SKILL.md). For targeted edits of an existing Schema.xml — [skd-edit/SKILL.md](skd-edit/SKILL.md).

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

For agents without a PreToolUse protocol (Codex, Cursor, Aider, Cline, etc.), it is recommended to set up additional protection layers:

- **Git pre-commit hook** (`tools/hooks/pre-commit`) — extend it by calling `--check` for all staged `.xml` / `.mxl` files. This is a late safety net: it prevents the change from reaching the repository even if the agent ignored the rule:
  ```bash
  python3 tools/hooks/block-direct-xml-edit.py --check "<staged-file>" --tool Edit
  ```
  With exit code `2` — the file belongs to 1C metadata, and the commit is aborted.
- **CI on PR** — the same `--check` on the diff catches any attempt at direct edits before they reach `main`.

Fine-tuning: the `ONEC_ROOT_DIRS`, `EXCLUDE_SUBSTRINGS`, and `EXCLUDE_BASENAMES` lists are defined as constants in `tools/hooks/block-direct-xml-edit.py`. Extend them if the project introduces a new 1C configuration pattern (for example, a nonstandard location) or a new false positive (a build XML with a unique name).

## §8 Workarounds

| Problem | Solution |
|----------|---------|
| `Parent element not found` (form) | Check the exact parent name in Form.xml — case matters |
| `Object already exists` (role) | `role add-right` instead of `add-object` |
| `DataSet not found` (skd) | Check the data set name in Schema.xml |
| Edit tool breaks line endings | Use `xml-gen edit replace-text` |
| DataSetUnion / CalculatedFields are needed in SKD | Workaround: calculations in queries |
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
