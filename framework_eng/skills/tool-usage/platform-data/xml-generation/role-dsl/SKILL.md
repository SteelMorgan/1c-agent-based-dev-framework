---
name: role-dsl
description: "JSON DSL for generating 1С roles with access rights to metadata objects. Use it for role compile and while editing Rights.xml through xml-generation (edit commands)."
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
  "rights": {
    "Catalog.Номенклатура": ["Read", "Insert", "Update", "Delete"],
    "Document.РеализацияТоваров": ["Read", "Insert"],
    "Report.ОтчётПоПродажам": ["View"]
  }
}
```

**Object types:** `Catalog`, `Document`, `Report`, `DataProcessor`, `InformationRegister`, `AccumulationRegister`

**Rights:** `Read`, `Insert`, `Update`, `Delete`, `View`, `Edit`, `InteractiveInsert`, `InteractiveDelete`, `Posting`, `UndoPosting`

## Pitfalls

```json
// ❌ права в camelCase → enum не распознает
"rights": {"Catalog.Номенклатура": ["read", "insert"]}

// ✅ enum RoleRight: строго PascalCase
"rights": {"Catalog.Номенклатура": ["Read", "Insert"]}
```

```json
// ❌ объект без типа → CLI не определит применимость прав
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
