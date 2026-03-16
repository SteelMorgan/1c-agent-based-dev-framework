---
name: role-dsl
description: JSON DSL for generating 1С roles with metadata object access rights. Use when role compile and editing Rights.xml via xml-gen-cli.
---

# Role DSL

JSON DSL for generating 1С roles with access rights.

## When to apply

| Trigger | Action |
|---------|--------|
| Need to create a role from scratch (object rights) | `role compile` with JSON DSL |
| Need to add rights to an object in an existing role | `role add-object` → [xml-gen-cli](../xml-gen-cli/) |
| Need to change a right for an existing object | `role add-right` → [xml-gen-cli](../xml-gen-cli/) |

## Compile command

```bash
xml-gen role compile [--format designer|edt] <input.json> <output_dir>
```

**Result (Designer):** `output_dir/Roles/<Name>.xml` and `output_dir/Roles/<Name>/Ext/Rights.xml` are created.

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

**Только чтение:**
```json
{
  "name": "ТолькоЧтение",
  "rights": {
    "Catalog.Номенклатура": ["Read"],
    "Report.ОтчётПоПродажам": ["View"]
  }
}
```

**Менеджер продаж:**
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

> The rights format is the enum RoleRight from mdclasses. Posting is for documents, View/Edit are for reports and data processors.

```json
// ❌ Неправильно — формат объекта без точки (Catalog.Имя, Document.Имя)
"rights": {"Номенклатура": ["Read"]}

// ✅ Правильно — ТипОбъекта.ИмяОбъекта
"rights": {"Catalog.Номенклатура": ["Read"]}
```

> The key is the full metadata object name. Without the type, the CLI cannot determine where the rights apply.

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
