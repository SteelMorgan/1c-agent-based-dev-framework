---
name: xml-gen-cli
description: Rules for working with XmlGen CLI — validate, edit commands (add-attribute, add-element, add-command, etc.). Use when validating XML and modifying existing Form, Role, EPF, SKD.
---

# XmlGen CLI — validate and edit commands

Rules for invoking xml-gen to validate and modify existing XML files.

## When to apply

| Trigger | Action |
|---------|--------|
| Need to check Form.xml before committing | `validate form Form.xml` |
| Need to check Rights.xml, Template.xml | `validate role <path>` or `validate skd <path>` |
| Need to add an attribute to an existing form | `form add-attribute --name ... --type ... Form.xml` |
| Need to add a UI element (field, button) | `form add-element --type ... --name ... [--path ...] [--parent ...] Form.xml` |
| Need to add rights on an object to a role | `role add-object --name ... --rights ... Rights.xml` |
| Need to add an attribute to a processor | `epf add-attribute --name ... <EpfRoot.xml>` |
| Need to add a parameter/field to SKD | `skd add-parameter` or `skd add-field` |
| Before an edit command — check the current state | First `validate`, then edit |

## Invocation

```bash
xml-gen <command> [args...]
```

> `xml-gen` installs automatically when the framework is installed (`python tools/1c-ai-agent-cli.py`).
> If the command is not available — run: `python tools/1c-ai-agent-cli.py --install-xml-gen`

## Validate command

Validation of 1С metadata XML files (Form, Role, SKD, MXL, EPF).

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

**XmlType:** `InputField`, `CheckBoxField`, `Button`, `UsualGroup`, `Table`, `LabelDecoration`, `Page`, `Pages` and others.

### Role (Rights.xml)

```bash
xml-gen role add-object --name <ObjectName> --rights <Right1,Right2,...> <Rights.xml>
xml-gen role add-right --object <ObjectName> --name <RightName> --value <true|false> <Rights.xml>
```

**Rights:** `Read`, `Insert`, `Update`, `Delete`, `View`, `Edit`, `Posting`, `UndoPosting` and others.

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

**Scenario: Add an attribute and element to a form**
1. `validate form Form.xml` — check the current state
2. `form add-attribute --name IsFavorite --type boolean Form.xml`
3. `form add-element --type CheckBoxField --name IsFavorite --path IsFavorite --parent ГруппаОсновное Form.xml`
4. If the error "Parent element not found" occurs — verify the parent name in Form.xml (case sensitive)

**Scenario: Add rights to a role**
1. `validate role Rights.xml`
2. `role add-object --name Catalog.Номенклатура --rights Read,Insert,Update Rights.xml`
3. If "Object already exists" appears — use `role add-right` to adjust the existing object

## Workarounds

| Problem | Cause | Solution |
|----------|---------|---------|
| "Parent element not found" | The parent name in `--parent` does not match the XML | Check the exact name in Form.xml (ChildItems, group) |
| "Object already exists" (role) | The object is already present in Rights.xml | Use `role add-right` instead of `add-object` |
| "DataSet not found" (skd) | The DataSet name in `--dataset` is incorrect | Verify the dataset name in Schema.xml |
| "Validation failed after modification" | The edit command produced invalid XML | Rollback happens automatically; fix the arguments and try again |
| Exit code 2 from validate | There is a WARNING, no ERROR | Usually it is safe to continue; review the output |

## Correct / Incorrect

```bash
# ❌ Incorrect — form add-element without --path for a data field (DataPath will not be created)
xml-gen form add-element --type InputField --name Наименование Form.xml

# ✅ Correct — --path binds the element to the attribute
xml-gen form add-element --type InputField --name Наименование --path Наименование Form.xml
```

> Without `--path` the element will not display data. InputField, CheckBoxField, etc. require DataPath to bind to the attribute.

```bash
# ❌ Incorrect — role add-object with preset "view" (CLI expects a comma-separated list: Read,View)
xml-gen role add-object --name Catalog.Номенклатура --rights view Rights.xml

# ✅ Correct — rights listed via comma, matching the RoleRight enum casing
xml-gen role add-object --name Catalog.Номенклатура --rights Read,View Rights.xml
```

> The CLI parses `--rights` as a string and splits it by comma. Values must match the enum (Read, Insert, Update, Delete, View, Edit, etc.).

## Rules for the agent

1. **Before modification** — run `validate` to inspect the current state.
2. **After modification** — edit commands perform auto-validation; on error the changes are not saved (rollback).
3. **File paths** — use absolute or relative paths to the specific file.
4. **EPF** — root XML: `output/MyProcessor.xml`
5. **Form inside EPF** — path: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`

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
