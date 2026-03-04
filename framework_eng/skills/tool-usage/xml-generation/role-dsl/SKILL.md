---
name: role-dsl
description: JSON DSL for generating 1C roles with access rights to metadata objects. Use it when running role compile and editing Rights.xml via xml-gen-cli.
---

# Role DSL

JSON DSL for generating 1C roles with access rights.

## When to apply

| Trigger | Action |
|---------|----------|
| Need to create a role from scratch (object rights) | `role compile` with JSON DSL |
| Need to add rights to an object in an existing role | `role add-object` → [xml-gen-cli](../xml-gen-cli/) |
| Need to change a right for an existing object | `role add-right` → [xml-gen-cli](../xml-gen-cli/) |

## Compile command

```bash
xml-gen role compile [--format designer|edt] <input.json> <output_dir>
```

**Result (Designer):** creates `output_dir/Roles/<Name>.xml` and `output_dir/Roles/<Name>/Ext/Rights.xml`

**Example:**
```bash
xml-gen role compile role.json output/
```

## DSL structure

```json
{
  "name": "RoleName",
  "rights": {
    "Catalog.Номенклатура": ["Read", "Insert", "Update", "Delete"],
    "Document.РеализацияТоваров": ["Read", "Insert"],
    "Report.ОтчётПоПродажам": ["View"]
  }
}
```

### Object types

`Catalog`, `Document`, `Report`, `DataProcessor`, `InformationRegister`, `AccumulationRegister`

### Access rights

`Read`, `Insert`, `Update`, `Delete`, `View`, `Edit`, `InteractiveInsert`, `InteractiveDelete`, `Posting`, `UndoPosting`

## Editing commands

```bash
xml-gen role add-object --name <ObjectName> --rights <Right1,Right2,...> <Rights.xml>
xml-gen role add-right --object <ObjectName> --name <RightName> --value <true|false> <Rights.xml>
```

## Role examples

**ReadOnly:**
```json
{
  "name": "ReadOnly",
  "rights": {
    "Catalog.Номенклатура": ["Read"],
    "Report.ОтчётПоПродажам": ["View"]
  }
}
```

**SalesManager:**
```json
{
  "name": "SalesManager",
  "rights": {
    "Catalog.Номенклатура": ["Read"],
    "Document.РеализацияТоваров": ["Read", "Insert", "Update", "Posting", "UndoPosting"],
    "Report.ОтчётПоПродажам": ["View"]
  }
}
```

## Right / Wrong

```json
// ❌ Wrong — rights for Catalog in camelCase (must match the RoleRight enum)
"rights": {"Catalog.Номенклатура": ["read", "insert"]}

// ✅ Correct — Read, Insert, Update, Delete, View, Edit, etc.
"rights": {"Catalog.Номенклатура": ["Read", "Insert"]}
```

> The rights format is the RoleRight enum from mdclasses. Posting is for documents, View/Edit — for reports and data processors.

```json
// ❌ Wrong — object format without a dot (Catalog.Name, Document.Name)
"rights": {"Номенклатура": ["Read"]}

// ✅ Correct — ObjectType.ObjectName
"rights": {"Catalog.Номенклатура": ["Read"]}
```

> The key is the full metadata object name. Without the type, the CLI cannot determine the applicability of the rights.

## See also

- [xml-generation](../xml-generation/) — general description
- [xml-gen-cli](../xml-gen-cli/) — edit commands
- [epf-operations](../epf-operations/) — creating data processors

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
