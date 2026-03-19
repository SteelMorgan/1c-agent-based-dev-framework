---
name: role-dsl
description: JSON DSL for generating 1C roles with access rights to metadata objects. Use with role compile and editing Rights.xml via xml-gen-cli.
---

# Role DSL

JSON DSL for generating 1C roles with access rights.

## When to apply

| Trigger | Action |
|---------|--------|
| Need to create a role from scratch (rights on objects) | `role compile` with JSON DSL |
| Need to add rights on an object in an existing role | `role add-object` → [xml-gen-cli](../xml-gen-cli/) |
| Need to change a right for an existing object | `role add-right` → [xml-gen-cli](../xml-gen-cli/) |
| Need to analyze an existing role | `role info <Rights.xml>` |

## Compile command

```bash
xml-gen role compile [--format designer|edt] <input.json> <output_dir>
```

**Result (Designer):** creates `output_dir/Roles/<Name>.xml` and `output_dir/Roles/<Name>/Ext/Rights.xml`

## Info command

Role rights audit: objects, rights, RLS, templates.

```bash
xml-gen role info <Rights.xml>
```

**Example:**
```bash
xml-gen role compile role.json output/
```

## DSL structure

```json
{
  "name": "ИмяРоли",
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

## Editing

```bash
xml-gen role add-object --name <ObjectName> --rights <Right1,Right2,...> <Rights.xml>
xml-gen role add-right --object <ObjectName> --name <RightName> --value <true|false> <Rights.xml>
```

## Correct / Incorrect

```json
// ❌ rights in camelCase → enum won't recognize
"rights": {"Catalog.Номенклатура": ["read", "insert"]}

// ✅ enum RoleRight: Read, Insert, Update, Delete, View, Edit, Posting, UndoPosting
"rights": {"Catalog.Номенклатура": ["Read", "Insert"]}
```

```json
// ❌ object without type → CLI cannot determine which rights apply
"rights": {"Номенклатура": ["Read"]}

// ✅ ТипОбъекта.ИмяОбъекта
"rights": {"Catalog.Номенклатура": ["Read"]}
```

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
