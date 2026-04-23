---
name: xml-gen-cli
description: "Rules for working with XmlGen CLI — validate, edit commands (form/role/epf/skd), edit replace-text (byte-by-byte replacement) and universal operations (form/template/help add/remove). Use when validating XML and modifying existing files."
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

## Byte-by-byte text replacement (edit replace-text)

Safe text replacement in XML without normalizing line endings. Keeps bare LF (0x0A) inside `<v8:content>`, CRLF between tags, UTF-8 BOM.

**Use instead of Claude Code Edit tool** when the file contains multiline content in `<v8:content>` tags (tooltips, descriptions).

```bash
xml-gen edit replace-text <file> --old "<old_text>" --new "<new_text>" [--all] [--dry-run] [--backup] [--validate] [--encoding utf-8-sig|utf-8]
```

| Flag | Description |
|------|-------------|
| `--old` / `--new` | Replacement pair. You can specify several: `--old "A" --new "B" --old "C" --new "D"` |
| `--all` | Replace all occurrences (by default — only the first) |
| `--dry-run` | Show the result without writing the file |
| `--backup` | Create a .bak before writing |
| `--validate` | Check XML well-formedness after the replacement |
| `--encoding` | `utf-8-sig` (default, keeps the BOM) or `utf-8` (without BOM) |

Exit codes: 0=replacement performed, 1=text not found, 2=error.

Output (stdout): JSON `{"file": "...", "replacements": N, "bytes_before": N, "bytes_after": N}`

```bash
# Замена Type на TypeSet
xml-gen edit replace-text src/xml/Documents/биг_Операция.xml \
  --old '<v8:Type>cfg:DocumentRef.big_Order_OKX</v8:Type>' \
  --new '<v8:TypeSet>cfg:DefinedType.биг_ОрдерБиржи</v8:TypeSet>'

# Множественная замена во всех вхождениях с dry-run
xml-gen edit replace-text Form.xml \
  --old 'cfg:DefinedType.биг_ДокументыПозиций' \
  --new 'cfg:DefinedType.биг_ПозицияБиржи' \
  --all --dry-run
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
3. **EPF** — root XML: `output/MyProcessor.xml`. Form in the EPF: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`.

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
| "Object already exists" (role) | `role add-right` instead of `add-object` |
| "DataSet not found" (skd) | Check the data set name in Schema.xml |
| Edit tool breaks line endings in XML | Use `xml-gen edit replace-text` instead of Claude Code Edit |

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
