---
name: role-dsl
description: "JSON DSL for generating 1С roles with access rights to metadata objects. Use it for role compile and when editing Rights.xml through xml-generation (edit commands)."
---

# Role DSL

## When to Use

| Trigger | Action |
|---------|----------|
| Create a role from scratch (object rights) | `role compile` with JSON DSL |
| Add rights to an object in an existing role | `role add-object` → [xml-generation](../SKILL.md) §3 |
| Change a right for an existing object | `role add-right` → [xml-generation](../SKILL.md) §3 |
| Analyze an existing role | `role info <Rights.xml>` |

## Commands

```bash
# Компиляция из DSL
xml-gen role compile [--format designer|edt] <input.json> <output_dir>
# Результат (Designer): output_dir/Roles/<Name>.xml + output_dir/Roles/<Name>/Ext/Rights.xml

# Аудит прав: объекты, права, RLS, шаблоны
xml-gen role info <Rights.xml>

# Точечное редактирование
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

**Rights (enum RoleRight, strictly PascalCase):** `Read`, `Insert`, `Update`, `Delete`, `View`, `Edit`, `InteractiveInsert`, `InteractiveDelete`, `Posting`, `UndoPosting`.

## Pitfalls

```json
// ❌ map-form {"rights": {"Type.Name": [...]}} — NOT supported
// CLI: Unrecognized field "rights" — the root field of RoleDsl must be "objects" (array)
{"name": "X", "rights": {"Catalog.Номенклатура": ["Read"]}}

// ✅ array-form "objects": [...]
{"name": "X", "objects": [{"name": "Catalog.Номенклатура", "rights": ["Read"]}]}
```

```json
// ❌ rights in camelCase → enum does not recognize them
"objects": [{"name": "Catalog.Номенклатура", "rights": ["read", "insert"]}]

// ✅ enum RoleRight: strictly PascalCase
"objects": [{"name": "Catalog.Номенклатура", "rights": ["Read", "Insert"]}]
```

```json
// ❌ object without type → CLI won't determine rights applicability
"objects": [{"name": "Номенклатура", "rights": ["Read"]}]

// ✅ TypeName.ObjectName
"objects": [{"name": "Catalog.Номенклатура", "rights": ["Read"]}]
```

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
