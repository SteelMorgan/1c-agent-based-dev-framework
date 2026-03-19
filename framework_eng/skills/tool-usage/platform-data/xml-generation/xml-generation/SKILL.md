---
name: xml-generation
description: Generation of 1С metadata XML from a compact JSON DSL. Supports 11 domains — EPF, Form, MXL, SKD, Role, Config, Subsystem, Interface, Meta (23 object types), Extension (CFE) + utilities (template, help). ~45 CLI operations in Designer format. Use when creating configurations, external processors, metadata objects, forms, roles, reports, print forms, extensions.
---

# XML Generation Module

## Installation

`xml-gen` is installed automatically (`python tools/install.py`, requires JDK 17+).
If unavailable: `python tools/install.py --install-xml-gen`

## Routing by type

| Type | Command | Skill |
|-----|---------|-------|
| External processing (EPF) | `epf init/add-form/add-template` | [epf-operations](../epf-operations/) |
| Form (Form) | `form compile/add-attribute/add-element` | [form-dsl](../form-dsl/) |
| Spreadsheet document (MXL) | `mxl compile` | [mxl-dsl](../mxl-dsl/) |
| SKD (SKD) | `skd compile` | [skd-dsl](../skd-dsl/) |
| Role (Role) | `role compile` | [role-dsl](../role-dsl/) |
| Configuration (CF) | `config init/info/edit/validate` | [config-operations](../config-operations/) |
| Subsystem + Interface | `subsystem compile/edit` | [subsystem-operations](../subsystem-operations/) |
| Metadata (23 types) | `meta compile/info/edit` | [meta-operations](../meta-operations/) |
| Extension (CFE) | `extension init/borrow/diff` | [extension-operations](../extension-operations/) |
| Validation / edit / utilities | `validate`, `form add`, `template add`, `help add` | [xml-gen-cli](../xml-gen-cli/) |

**Do not use** when: EDT format is required, DataSetUnion/CalculatedFields in SKD are needed (workaround: calculations in queries).

## Key commands

```bash
# EPF
xml-gen epf init --name MyProcessor output/
xml-gen epf add-form --epf MyProcessor --name MainForm output/

# Compile DSL → XML
xml-gen form compile form.json Form.xml
xml-gen mxl compile template.json Template.xml
xml-gen skd compile schema.json Template.xml
xml-gen role compile role.json output/

# Конфигурация
xml-gen config init --name МояКонфигурация output/
xml-gen config info output/

# Метаданные (23 типа)
xml-gen meta compile meta.json output/
xml-gen meta edit Catalogs/Товары --op add-attribute "Вес: Number(15,3)"

# Подсистемы
xml-gen subsystem compile subsystem.json output/

# Расширения (CFE)
xml-gen extension init --name МоёРасширение --config output/ output_ext/
xml-gen extension borrow output_ext/ output/ "Catalog.Товары"

# Универсальные
xml-gen form add output/Catalogs/Товары MainForm
xml-gen template add output/Catalogs/Товары PrintForm --type spreadsheet
xml-gen help add output/Catalogs/Товары
xml-gen validate form Form.xml
```

## Limitations

- **Designer format only** — EDT will be added later
- **SKD ~90%** — no DataSetObject/Union or CalculatedFields → workaround via queries

## Correct / Incorrect

```bash
# ❌ epf init without --name and output_dir → "--name is required"
xml-gen epf init MyProcessor

# ✅ --name and output_dir are mandatory
xml-gen epf init --name MyProcessor output/
```

```bash
# ❌ role compile with a file output (creates a folder structure)
xml-gen role compile role.json Roles/МояРоль.xml

# ✅ output_dir → Roles/<Name>/Ext/Rights.xml
xml-gen role compile role.json output/
```

---
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/epf-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/form-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/mxl-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/role-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/skd-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/xml-gen-cli/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/config-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/subsystem-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/meta-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/extension-operations/SKILL.md
metadata:
  category: 1c-development
  version: "1.0"
---
