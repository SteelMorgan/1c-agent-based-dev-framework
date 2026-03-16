---
name: xml-generation
description: XML metadata generation for 1С from a compact JSON DSL. Supports EPF, Form, MXL, SKD, Role in Designer format. Use when creating external processors, forms, roles, reports, and print layouts.
---

# XML Generation Module

Module for generating XML metadata for 1С from a compact JSON DSL.

## Quick start

### Installation

`xml-gen` installs automatically when installing the framework:

```bash
python tools/1c-ai-agent-cli.py
```

The installer will build the JAR (requires JDK 17+) and create the `xml-gen` command in `~/.local/bin/`.

If `xml-gen` is unavailable — reinstall manually:
```bash
python tools/1c-ai-agent-cli.py --install-xml-gen
```

### Usage

```bash
# Create an external processor (--name and output_dir are required)
xml-gen epf init --name MyProcessor output/

# Compile a form
xml-gen form compile form.json Form.xml

# Compile a tabular document
xml-gen mxl compile template.json Template.xml

# Compile a data composition schema
xml-gen skd compile schema.json Template.xml

# Compile a role (creates Roles/<Name>/Ext/Rights.xml)
xml-gen role compile role.json output/

# Validate XML
xml-gen validate form Form.xml
```

## Supported metadata types

| Type | Status | Skill |
|-----|--------|-------|
| External processor (EPF) | ✅ 100% | [epf-operations](../epf-operations/) |
| Managed form (Form) | ✅ 100% | [form-dsl](../form-dsl/) |
| Tabular document (MXL) | ✅ 100% | [mxl-dsl](../mxl-dsl/) |
| Data composition schema (SKD) | ✅ 85% | [skd-dsl](../skd-dsl/) |
| Role (Role) | ✅ 100% | [role-dsl](../role-dsl/) |

## Usage scenarios

| Trigger | Action |
|---------|----------|
| Need to create an external processor | `epf init` → `epf add-form` → [epf-operations](../epf-operations/) |
| Need to create a form with UI elements | `form compile` with JSON DSL → [form-dsl](../form-dsl/) |
| Need to create a print layout (tabular document) | `epf add-template --type spreadsheet` → `mxl compile` → [mxl-dsl](../mxl-dsl/) |
| Need to create a report (СКД) | `skd compile` with JSON DSL → [skd-dsl](../skd-dsl/) |
| Need to create a role with rights | `role compile` with JSON DSL → [role-dsl](../role-dsl/) |
| Need to modify existing XML (add attribute, element) | `form add-attribute`, `epf add-attribute`, etc. → [xml-gen-cli](../xml-gen-cli/) |
| Need to verify XML correctness | `validate` → [xml-gen-cli](../xml-gen-cli/) |

**Do not use** when EDT format is required (not supported yet), or DataSetUnion/CalculatedFields in SKD are needed (use a workaround in queries).

## Detailed documentation

- **[xml-gen-cli](../xml-gen-cli/)** — CLI: validate and edit commands (add-attribute, add-element, etc.)
- **[epf-operations](../epf-operations/)** — operations for external processors
- **[form-dsl](../form-dsl/)** — JSON DSL for forms
- **[mxl-dsl](../mxl-dsl/)** — JSON DSL for tabular documents
- **[skd-dsl](../skd-dsl/)** — JSON DSL for data composition schemas
- **[role-dsl](../role-dsl/)** — JSON DSL for roles

## Usage walkthroughs

### Scenario 1: Processor with a form

```bash
# 1. Create the processor
xml-gen epf init --name DataImport output/

# 2. Add a form
xml-gen epf add-form --epf DataImport --name MainForm output/

# 3. Create the JSON DSL for the form
cat > form.json <<EOF
{
  "attributes": [
    {"name": "ИмяФайла", "type": "string(255)"}
  ],
  "elements": [
    {"type": "input", "name": "ИмяФайла", "dataPath": "ИмяФайла"}
  ]
}
EOF

# 4. Generate Form.xml
xml-gen form compile form.json output/DataImport/Forms/MainForm/Ext/Form.xml
```

### Scenario 2: Report (SKD)

```bash
# Create the JSON DSL
cat > report.json <<EOF
{
  "dataSets": [{
    "name": "Продажи",
    "query": "ВЫБРАТЬ Организация, Сумма ИЗ РегистрНакопления.Продажи",
    "fields": [
      {"dataPath": "Организация", "title": "Организация"},
      {"dataPath": "Сумма", "title": "Сумма", "type": "number(15,2)"}
    ]
  }],
  "settingsVariants": [{
    "name": "Основной",
    "settings": {
      "selection": ["Организация", "Сумма"],
      "filter": ["Сумма > 0"],
      "order": ["Сумма desc"]
    }
  }]
}
EOF

# Generate Template.xml
xml-gen skd compile report.json Template.xml
```

## Architecture

```
io.github.onec.xmlgen/
├── cli/           # CLI commands
├── dsl/           # JSON DSL classes
├── writer/        # XML generators
├── model/         # Supporting models
└── format/        # Output formats (Designer/EDT)
```

## Limitations

1. **Designer format only** — EDT will be added later
2. **SKD at 85%** — no DataSetObject/Union, CalculatedFields
3. **No reference validation** — cross-object links are not checked
4. **No reverse conversion** — JSON → XML only

### Workaround

- **DataSetObject/Union** → use DataSetQuery with queries
- **CalculatedFields** → use calculations in queries
- **EDT format** → convert Designer → EDT via 1С

## Right / Wrong

```bash
# ❌ Wrong — epf init without --name and output_dir (CLI returns "--name is required")
xml-gen epf init MyProcessor

# ✅ Right — --name and output_dir are required; CLI parses named arguments
xml-gen epf init --name MyProcessor output/
```

> CLI requires explicit `--name` and positional `output_dir` because it does not support the old syntax with positional arguments.

```bash
# ❌ Wrong — role compile expecting a single output file (a directory structure is created)
xml-gen role compile role.json Roles/МояРоль.xml

# ✅ Right — output_dir, creates Roles/<Name>/Ext/Rights.xml
xml-gen role compile role.json output/
```

> RoleWriter creates the full role structure (metadata + Rights.xml), not a single file.

---
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/epf-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/form-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/mxl-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/role-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/skd-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/xml-gen-cli/SKILL.md
metadata:
  category: 1c-development
  version: "1.0"
---
