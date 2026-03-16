---
name: xml-gen-cli
description: Rules for working with XmlGen CLI — validate, edit commands (add-attribute, add-element, add-command, etc.). Use when validating XML and modifying existing Form, Role, EPF, SKD.
---

# XmlGen CLI — validate and edit commands

Rules for calling xml-gen to validate and modify existing XML files.

## When to apply

| Trigger | Action |
|---------|----------|
| Need to check Form.xml before committing | `validate form Form.xml` |
| Need to check Rights.xml, Template.xml | `validate role <path>` or `validate skd <path>` |
| Need to add an attribute to an existing form | `form add-attribute --name ... --type ... Form.xml` |
| Need to add a UI element (field, button) | `form add-element --type ... --name ... [--path ...] [--parent ...] Form.xml` |
| Need to add object rights to a role | `role add-object --name ... --rights ... Rights.xml` |
| Need to add an attribute to a processing | `epf add-attribute --name ... <EpfRoot.xml>` |
| Need to add a parameter/field to SKD | `skd add-parameter` or `skd add-field` |
| Before an edit command — check the current state | Run `validate` first, then edit |

## Invocation

```bash
xml-gen <command> [args...]
```

> `xml-gen` installs automatically when the framework is installed (`python tools/1c-ai-agent-cli.py`).
> If the command is unavailable — run: `python tools/1c-ai-agent-cli.py --install-xml-gen`

## validate command

Validation of 1C metadata XML files (Form, Role, SKD, MXL, EPF).

**Syntax:**
```bash
xml-gen validate [--type <form|role|skd|mxl|epf>] [--format designer|edt] [--level structure|semantic] [--output text|json] [--src-root <path>] <file> [file2 ...]
```

**Exit codes:** 0=ok, 1=errors, 2=warnings

**Examples:**
```bash
xml-gen validate form Form.xml
xml-gen validate role output/Roles/МояРоль/Ext/Rights.xml
xml-gen validate --type skd --output json Template.xml
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

**XmlType:** `InputField`, `CheckBoxField`, `Button`, `UsualGroup`, `Table`, `LabelDecoration`, `Page`, `Pages` etc.

### Role (Rights.xml)

```bash
xml-gen role add-object --name <ObjectName> --rights <Right1,Right2,...> <Rights.xml>
xml-gen role add-right --object <ObjectName> --name <RightName> --value <true|false> <Rights.xml>
```

**Rights:** `Read`, `Insert`, `Update`, `Delete`, `View`, `Edit`, `Posting`, `UndoPosting` etc.

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

## Scenarios

**Scenario: Add an attribute and an element to a form**
1. `validate form Form.xml` — check the current state
2. `form add-attribute --name IsFavorite --type boolean Form.xml`
3. `form add-element --type CheckBoxField --name IsFavorite --path IsFavorite --parent ГруппаОсновное Form.xml`
4. On "Parent element not found" error — verify the parent name in Form.xml (case-sensitive)

**Scenario: Add rights to a role**
1. `validate role Rights.xml`
2. `role add-object --name Catalog.Номенклатура --rights Read,Insert,Update Rights.xml`
3. On "Object already exists" — use `role add-right` to modify the existing object

## Workarounds

| Problem | Cause | Solution |
|----------|---------|---------|
| "Parent element not found" | The parent name in `--parent` does not match the XML | Check the exact name in Form.xml (ChildItems, group) |
| "Object already exists" (role) | The object is already in Rights.xml | Use `role add-right` instead of `add-object` |
| "DataSet not found" (skd) | The DataSet name in `--dataset` is incorrect | Check the data set name in Schema.xml |
| "Validation failed after modification" | The edit command produced invalid XML | Rollback is automatic; fix the arguments and retry |
| Exit code 2 from validate | There is a WARNING but no ERROR | Usually you can continue; review the output |

## Right / Wrong

```bash
# ❌ Wrong — form add-element without --path for a data field (DataPath will not be created)
xml-gen form add-element --type InputField --name Наименование Form.xml

# ✅ Right — --path ties the element to the attribute
xml-gen form add-element --type InputField --name Наименование --path Наименование Form.xml
```

> Without `--path` the element will not render data. InputField, CheckBoxField, etc. require DataPath to bind to an attribute.

```bash
# ❌ Wrong — role add-object with preset "view" (CLI expects a comma-separated list: Read,View)
xml-gen role add-object --name Catalog.Номенклатура --rights view Rights.xml

# ✅ Right — rights comma-separated, casing from enum RoleRight
xml-gen role add-object --name Catalog.Номенклатура --rights Read,View Rights.xml
```

> CLI parses `--rights` as a string and splits by comma. Values must match the enum (Read, Insert, Update, Delete, View, Edit, etc.).

## Rules for the agent

1. **Before modification** — run `validate` to check the current state.
2. **After modification** — edit commands perform auto-validation; on error, changes are not saved (rollback).
3. **File paths** — use absolute or relative paths to the exact file.
4. **EPF** — root XML: `output/MyProcessor.xml`
5. **Form in EPF** — path: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`

## See also

- [epf-operations](../epf-operations/) — epf init, add-form, add-template
- [form-dsl](../form-dsl/) — form compile
- [role-dsl](../role-dsl/) — role compile

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
