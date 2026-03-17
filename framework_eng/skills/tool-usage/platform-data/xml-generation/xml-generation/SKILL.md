---
name: xml-generation
description: Generation of 1С XML metadata from a compact JSON DSL. Supports 11 domains — EPF, Form, MXL, SKD, Role, Config, Subsystem, Interface, Meta (23 object types), Extension (CFE) + utilities (template, help). ~45 Designer-format CLI operations. Use when creating configurations, external processing modules, metadata objects, forms, roles, reports, print forms, extensions.
---

# XML Generation Module

Module for generating 1С XML metadata from a compact JSON DSL. 11 domains, ~45 operations.

## Quick Start

### Installation

`xml-gen` installs automatically when you install the framework:

```bash
python tools/install.py
```

The installer will build the JAR (requires JDK 17+) and create the `xml-gen` command in `~/.local/bin/`.

If `xml-gen` is unavailable, reinstall manually:
```bash
python tools/install.py --install-xml-gen
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

# Конфигурация
xml-gen config init --name МояКонфигурация output/
xml-gen config info output/
xml-gen config validate output/

# Объекты метаданных (23 типа)
xml-gen meta compile meta.json output/
xml-gen meta info Catalogs/Товары
xml-gen meta edit Catalogs/Товары --op add-attribute "Вес: Number(15,3)"

# Подсистемы
xml-gen subsystem compile subsystem.json output/
xml-gen subsystem info Subsystems/Основное

# Расширения (CFE)
xml-gen extension init --name МоёРасширение --config output/ output_ext/
xml-gen extension borrow output_ext/ output/ "Catalog.Товары"
xml-gen extension diff output_ext/ output/

# Универсальные операции
xml-gen form add output/Catalogs/Товары MainForm
xml-gen template add output/Catalogs/Товары PrintForm --type spreadsheet
xml-gen help add output/Catalogs/Товары
```

## Supported metadata types

| Type | Status | Skill |
|-----|--------|-------|
| Внешняя обработка (EPF) | ✅ | [epf-operations](../epf-operations/) |
| Управляемая форма (Form) | ✅ | [form-dsl](../form-dsl/) |
| Табличный документ (MXL) | ✅ | [mxl-dsl](../mxl-dsl/) |
| Схема компоновки данных (SKD) | ✅ | [skd-dsl](../skd-dsl/) |
| Роль (Role) | ✅ | [role-dsl](../role-dsl/) |
| Конфигурация (CF) | ✅ | [config-operations](../config-operations/) |
| Подсистема (Subsystem) + Интерфейс | ✅ | [subsystem-operations](../subsystem-operations/) |
| Объект метаданных (Meta, 23 типа) | ✅ | [meta-operations](../meta-operations/) |
| Расширение (CFE) | ✅ | [extension-operations](../extension-operations/) |
| Утилиты (template, help) | ✅ | [xml-gen-cli](../xml-gen-cli/) |

## When to apply

| Trigger | Action |
|---------|----------|
| Need to create an external processing module | `epf init` → `epf add-form` → [epf-operations](../epf-operations/) |
| Need to create a form with UI elements | `form compile` with JSON DSL → [form-dsl](../form-dsl/) |
| Need to create a print form (tabular document) | `epf add-template --type spreadsheet` → `mxl compile` → [mxl-dsl](../mxl-dsl/) |
| Need to create a report (СКД) | `skd compile` with JSON DSL → [skd-dsl](../skd-dsl/) |
| Need to create a role with rights | `role compile` with JSON DSL → [role-dsl](../role-dsl/) |
| Need to create/modify a configuration | `config init` / `config edit` → [config-operations](../config-operations/) |
| Need to create a metadata object (Справочник, Документ, Регистр...) | `meta compile` → [meta-operations](../meta-operations/) |
| Need to create/edit a subsystem | `subsystem compile` / `subsystem edit` → [subsystem-operations](../subsystem-operations/) |
| Need to create an extension | `extension init` → [extension-operations](../extension-operations/) |
| Need to borrow an object into an extension | `extension borrow` → [extension-operations](../extension-operations/) |
| Need to add a form/template/help to any object | `form add` / `template add` / `help add` → [xml-gen-cli](../xml-gen-cli/) |
| Need to modify existing XML (add attribute, element) | `form add-attribute`, `epf add-attribute` etc. → [xml-gen-cli](../xml-gen-cli/) |
| Need to validate XML | `validate` → [xml-gen-cli](../xml-gen-cli/) |

**Do not use** when: EDT format is required (not supported yet), DataSetUnion/CalculatedFields are needed in SKD (use the workaround in queries).

## Detailed documentation

- **[xml-gen-cli](../xml-gen-cli/)** — CLI: validate, edit commands, utilities (template, help)
- **[epf-operations](../epf-operations/)** — operations for external processing modules
- **[form-dsl](../form-dsl/)** — JSON DSL for forms
- **[mxl-dsl](../mxl-dsl/)** — JSON DSL for tabular documents
- **[skd-dsl](../skd-dsl/)** — JSON DSL for data composition schemas
- **[role-dsl](../role-dsl/)** — JSON DSL for roles
- **[config-operations](../config-operations/)** — operations for configurations (init, info, edit, validate)
- **[subsystem-operations](../subsystem-operations/)** — subsystems and interfaces
- **[meta-operations](../meta-operations/)** — metadata objects (23 types: справочники, документы, регистры и др.)
- **[extension-operations](../extension-operations/)** — extensions (CFE): init, borrow, diff

## Usage scenarios

### Scenario 1: Processor with a form

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
├── writer/        # XML generators (MetaWriter, ConfigWriter, SubsystemWriter, ExtensionWriter)
├── editor/        # XML editors (ConfigEditor, SubsystemEditor, InterfaceEditor, ExtensionEditor, ObjectContainerEditor)
├── validator/     # Validators (MetaValidator, ConfigValidator, SubsystemValidator, InterfaceValidator, ExtensionValidator)
├── info/          # Info printers (MetaInfoPrinter, ConfigInfoPrinter, SubsystemInfoPrinter, ExtensionDiffPrinter, SkdInfoPrinter, FormInfoPrinter, MxlInfoPrinter, RoleInfoPrinter)
├── model/         # Models (MetadataTypeRegistry, UuidGenerator)
└── format/        # Output formats (Designer/EDT)
```

## Limitations

1. **Designer format only** — EDT will be added later
2. **SKD is 90% supported** — no DataSetObject/Union, CalculatedFields
3. **No reference validation** — links between objects are not checked

### Workaround

- **DataSetObject/Union** → use DataSetQuery with queries
- **CalculatedFields** → use calculations in queries
