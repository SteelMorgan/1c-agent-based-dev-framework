---
name: role-dsl
description: JSON DSL for generating 1C roles with access rights to metadata objects. Use with role compile and editing Rights.xml through xml-gen-cli.
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

Audit a role's rights: objects, rights, RLS, templates.

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

## Editing commands

```bash
xml-gen role add-object --name <ObjectName> --rights <Right1,Right2,...> <Rights.xml>
xml-gen role add-right --object <ObjectName> --name <RightName> --value <true|false> <Rights.xml>
```

## Role examples

**Read-only:**
```json
{
  "name": "ТолькоЧтение",
  "rights": {
    "Catalog.Номенклатура": ["Read"],
    "Report.ОтчётПоПродажам": ["View"]
  }
}
```

**Sales manager:**
```json
{
  "name": "МенеджерПродаж",
  "rights": {
    "Catalog.Номенклатура": ["Read"],
    "Document.РеализацияТоваров": ["Read", "Insert", "Update", "Posting", "UndoPosting"],
    "Report.ОтчётПоПродажам": ["View"]
  }
}
```

## Correct / Incorrect

```json
// ❌ Неправильно — права для Catalog в camelCase (должны совпадать с enum RoleRight)
"rights": {"Catalog.Номенклатура": ["read", "insert"]}

// ✅ Правильно — Read, Insert, Update, Delete, View, Edit и т.д.
"rights": {"Catalog.Номенклатура": ["Read", "Insert"]}
```

> Rights format — enum RoleRight from mdclasses. Posting — for documents, View/Edit — for reports and data processors.

```json
// ❌ Неправильно — формат объекта без точки (Catalog.Имя, Document.Имя)
"rights": {"Номенклатура": ["Read"]}

// ✅ Правильно — ТипОбъекта.ИмяОбъекта
"rights": {"Catalog.Номенклатура": ["Read"]}
```

> The key is the full metadata object name. Without the type the CLI cannot determine which rights apply.

## See also

- [xml-generation](../xml-generation/) — general overview
- [xml-gen-cli](../xml-gen-cli/) — edit commands
- [epf-operations](../epf-operations/) — creating data processors

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
