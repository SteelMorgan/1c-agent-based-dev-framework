---
name: xml-generation
description: Generation of 1С metadata XML from a compact JSON DSL. Supports EPF, Form, MXL, SKD, Role in Designer format. Use when creating external processing, forms, roles, reports, print forms.
---

# XML Generation Module

Module for generating 1С metadata XML from a compact JSON DSL.

## Quick Start

### Installation

`xml-gen` installs automatically when the framework is installed:

```bash
python tools/1c-ai-agent-cli.py
```

The installer will build the JAR (requires JDK 17+) and create the `xml-gen` command in `~/.local/bin/`.

If `xml-gen` is unavailable, reinstall manually:
```bash
python tools/1c-ai-agent-cli.py --install-xml-gen
```

### Usage

```bash
# Создать внешнюю обработку (--name и output_dir обязательны)
xml-gen epf init --name MyProcessor output/

# Скомпилировать форму
xml-gen form compile form.json Form.xml

# Скомпилировать табличный документ
xml-gen mxl compile template.json Template.xml

# Скомпилировать схему компоновки данных
xml-gen skd compile schema.json Template.xml

# Скомпилировать роль (создаёт Roles/<Name>/Ext/Rights.xml)
xml-gen role compile role.json output/

# Валидация XML
xml-gen validate form Form.xml
```

## Supported metadata types

| Type | Status | Skill |
|-----|--------|-------|
| Внешняя обработка (EPF) | ✅ 100% | [epf-operations](../epf-operations/) |
| Управляемая форма (Form) | ✅ 100% | [form-dsl](../form-dsl/) |
| Табличный документ (MXL) | ✅ 100% | [mxl-dsl](../mxl-dsl/) |
| Схема компоновки данных (SKD) | ✅ 85% | [skd-dsl](../skd-dsl/) |
| Роль (Role) | ✅ 100% | [role-dsl](../role-dsl/) |

## When to use

| Trigger | Action |
|---------|----------|
| Need to create an external processing | `epf init` → `epf add-form` → [epf-operations](../epf-operations/) |
| Need to create a form with UI elements | `form compile` with JSON DSL → [form-dsl](../form-dsl/) |
| Need to create a print form (tabular document) | `epf add-template --type spreadsheet` → `mxl compile` → [mxl-dsl](../mxl-dsl/) |
| Need to create a report (СКД) | `skd compile` with JSON DSL → [skd-dsl](../skd-dsl/) |
| Need to create a role with permissions | `role compile` with JSON DSL → [role-dsl](../role-dsl/) |
| Need to modify existing XML (add attribute, element) | `form add-attribute`, `epf add-attribute`, etc. → [xml-gen-cli](../xml-gen-cli/) |
| Need to validate XML | `validate` → [xml-gen-cli](../xml-gen-cli/) |

**Do not use** when: EDT format is needed (not supported yet), DataSetUnion/CalculatedFields are required in SKD (use workarounds in queries).

## Detailed documentation

- **[xml-gen-cli](../xml-gen-cli/)** — CLI: validate, edit commands (add-attribute, add-element, etc.)
- **[epf-operations](../epf-operations/)** — operations with external processing
- **[form-dsl](../form-dsl/)** — JSON DSL for forms
- **[mxl-dsl](../mxl-dsl/)** — JSON DSL for tabular documents
- **[skd-dsl](../skd-dsl/)** — JSON DSL for data composition schemes
- **[role-dsl](../role-dsl/)** — JSON DSL for roles

## Usage scenarios

### Scenario 1: External processing with a form

```bash
# 1. Создать обработку
xml-gen epf init --name DataImport output/

# 2. Добавить форму
xml-gen epf add-form --epf DataImport --name MainForm output/

# 3. Создать JSON DSL для формы
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

# 4. Сгенерировать Form.xml
xml-gen form compile form.json output/DataImport/Forms/MainForm/Ext/Form.xml
```

### Scenario 2: Report (SKD)

```bash
# Создать JSON DSL
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

# Сгенерировать Template.xml
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
3. **No reference validation** — references between objects are not checked
4. **No reverse conversion** — JSON → XML only

### Workaround

- **DataSetObject/Union** → use DataSetQuery with queries
- **CalculatedFields** → use calculations in queries
- **EDT format** → convert Designer → EDT via 1С
