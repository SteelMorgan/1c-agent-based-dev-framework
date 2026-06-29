---
name: role-dsl
description: "xml-gen roles DSL and Rights.xml editing"
---

# Role DSL

## When to use

| Trigger | Action |
|---------|----------|
| Create a role from scratch (object permissions) | `role compile` with JSON DSL |
| Add object permissions to an existing role | `role add-object` → [xml-generation](../SKILL.md) §3 |
| Change a permission for an existing object | `role add-right` → [xml-generation](../SKILL.md) §3 |
| Analyze an existing role | `role info <Rights.xml>` |

## Commands

```bash
# Compile from DSL
xml-gen role compile [--format designer|edt] <input.json> <output_dir>
# Result (Designer): output_dir/Roles/<Name>.xml + output_dir/Roles/<Name>/Ext/Rights.xml

# Permissions audit: objects, rights, RLS, templates
xml-gen role info <Rights.xml>

# Targeted editing
xml-gen role add-object --name <ObjectName> --rights <Right1,Right2,...> <Rights.xml>
xml-gen role add-right --object <ObjectName> --name <RightName> --value <true|false> <Rights.xml>
```

## DSL Structure

```json
{
  "name": "ИмяРоли",
  "objects": [
    {"name": "Catalog.Номенклатура",         "rights": ["Read", "Insert", "Update", "Delete"]},
    {"name": "Document.РеализацияТоваров",    "rights": ["Read", "Insert"]},
    {"name": "Report.ОтчётПоПродажам",       "rights": ["View"]}
  ]
}
```

**Root DSL fields (8 total):** `name`, `objects`, `templates`, `comment`, `synonym`, `setForNewObjects`, `setForAttributesByDefault`, `independentRightsOfChildObjects`.

**Object types:** `Catalog`, `Document`, `Report`, `DataProcessor`, `InformationRegister`, `AccumulationRegister`

**Rights (RoleRight enum, strictly PascalCase):** `Read`, `Insert`, `Update`, `Delete`, `View`, `Edit`, `InteractiveInsert`, `InteractiveDelete`, `Posting`, `UndoPosting`.

## Pitfalls

```json
// ❌ map-форма {"rights": {"Type.Name": [...]}} — НЕ поддерживается
// CLI: Unrecognized field "rights" — корневое поле объекта RoleDsl должно быть "objects" (массив)
{"name": "X", "rights": {"Catalog.Номенклатура": ["Read"]}}

// ✅ array-форма "objects": [...]
{"name": "X", "objects": [{"name": "Catalog.Номенклатура", "rights": ["Read"]}]}
```

```json
// ❌ права в camelCase → enum не распознает
"objects": [{"name": "Catalog.Номенклатура", "rights": ["read", "insert"]}]

// ✅ enum RoleRight: строго PascalCase
"objects": [{"name": "Catalog.Номенклатура", "rights": ["Read", "Insert"]}]
```

```json
// ❌ объект без типа → CLI не определит применимость прав
"objects": [{"name": "Номенклатура", "rights": ["Read"]}]

// ✅ ТипОбъекта.ИмяОбъекта
"objects": [{"name": "Catalog.Номенклатура", "rights": ["Read"]}]
```

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
