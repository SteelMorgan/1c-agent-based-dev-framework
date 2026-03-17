---
name: xml-gen-cli
description: Rules for working with XmlGen CLI — validate (form/role/skd/mxl/epf/config/subsystem/interface/meta/extension), edit commands and universal operations (form/template/help add/remove). Use when validating XML and modifying existing Form, Role, EPF, SKD.
---

# XmlGen CLI — validate and edit commands

## Validate command

```bash
xml-gen validate [--type <form|role|skd|mxl|epf>] [--format designer|edt] [--level structure|semantic] [--output text|json] <file> [file2 ...]
```

Exit codes: 0=ok, 1=errors, 2=warnings (you can continue)

Domain validate commands:
```bash
xml-gen config validate <configPath>
xml-gen subsystem validate <subsystemPath>
xml-gen interface validate <ciPath>
xml-gen meta validate <objectPath>
xml-gen extension validate <extensionPath>
```

## Universal operations

```bash
xml-gen form add <objectPath> <formName>
xml-gen form remove <objectPath> <formName>
xml-gen template add <objectPath> <name> --type <spreadsheet|html|text|dcs|binary>
xml-gen template remove <objectPath> <name>
xml-gen help add <objectPath>
```

## Edit commands

### Form

```bash
xml-gen form add-attribute --name <Name> --type <Type> <Form.xml>
xml-gen form add-element --type <XmlType> --name <Name> [--path <DataPath>] [--parent <ParentName>] [--after <AfterName>] <Form.xml>
xml-gen form add-command --name <Name> [--title <Title>] [--action <Action>] <Form.xml>
xml-gen form remove-element --name <Name> <Form.xml>
xml-gen form move-element --name <Name> [--after <Name>] [--before <Name>] [--into <ParentName>] <Form.xml>
```

**XmlType:** `InputField`, `CheckBoxField`, `Button`, `UsualGroup`, `Table`, `LabelDecoration`, `Page`, `Pages` and others.

### Role (Rights.xml)

```bash
xml-gen role add-object --name <ObjectName> --rights <Right1,Right2,...> <Rights.xml>
xml-gen role add-right --object <ObjectName> --name <RightName> --value <true|false> <Rights.xml>
```

### EPF (root XML)

```bash
xml-gen epf add-attribute --name <Name> [--type <Type>] [--synonym <Synonym>] <EpfRoot.xml>
xml-gen epf add-tabular-section --name <Name> [--synonym <Synonym>] <EpfRoot.xml>
```

### SKD

```bash
xml-gen skd add-parameter --name <Name> [--title <Title>] [--type <Type>] <Schema.xml>
xml-gen skd add-field --dataset <DataSetName> --name <FieldName> --path <DataPath> [--title <Title>] <Schema.xml>
```

## Rules for the agent

1. **Before modification** — `validate` to check the current state.
2. **After modification** — auto-validation; rollback is automatic if an error occurs.
3. **EPF** — root XML: `output/MyProcessor.xml`. Form inside the EPF: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`.

## Correct / Incorrect

```bash
# ❌ form add-element without --path (DataPath will not be created, the element will not show data)
xml-gen form add-element --type InputField --name Наименование Form.xml

# ✅ --path links the element to the attribute
xml-gen form add-element --type InputField --name Наименование --path Наименование Form.xml
```

```bash
# ❌ role add-object with "view" (enum required: Read,View)
xml-gen role add-object --name Catalog.Номенклатура --rights view Rights.xml

# ✅ rights separated by commas, case from enum RoleRight
xml-gen role add-object --name Catalog.Номенклатура --rights Read,View Rights.xml
```

## Workarounds

| Problem | Solution |
|----------|---------|
| "Parent element not found" | Check the exact parent name in Form.xml (case matters) |
| "Object already exists" (role) | use `role add-right` instead of `add-object` |
| "DataSet not found" (skd) | Check the data set name in Schema.xml |

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
