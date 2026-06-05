---
name: role-dsl
description: "Use for генерации ролей 1С с правами доступа через JSON DSL и точечного редактирования Rights.xml. Helps создать роль с нуля и управлять отдельными правами через xml-gen role compile/add-object/add-right."
---

# Role DSL

## Когда применять

| Триггер | Действие |
|---------|----------|
| Создать роль с нуля (права на объекты) | `role compile` с JSON DSL |
| Добавить права на объект в существующую роль | `role add-object` → [xml-generation](../SKILL.md) §3 |
| Изменить право для существующего объекта | `role add-right` → [xml-generation](../SKILL.md) §3 |
| Анализировать существующую роль | `role info <Rights.xml>` |

## Команды

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

## Структура DSL

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

**Корневые поля DSL (8 шт.):** `name`, `objects`, `templates`, `comment`, `synonym`, `setForNewObjects`, `setForAttributesByDefault`, `independentRightsOfChildObjects`.

**Типы объектов:** `Catalog`, `Document`, `Report`, `DataProcessor`, `InformationRegister`, `AccumulationRegister`

**Права (enum RoleRight, строго PascalCase):** `Read`, `Insert`, `Update`, `Delete`, `View`, `Edit`, `InteractiveInsert`, `InteractiveDelete`, `Posting`, `UndoPosting`.

## Ловушки

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
