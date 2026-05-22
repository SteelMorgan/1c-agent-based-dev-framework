---
name: role-dsl
description: "JSON DSL для генерации ролей 1С с правами доступа к объектам метаданных. Используй при role compile и редактировании Rights.xml через xml-generation (edit-команды)."
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
  "rights": {
    "Catalog.Номенклатура": ["Read", "Insert", "Update", "Delete"],
    "Document.РеализацияТоваров": ["Read", "Insert"],
    "Report.ОтчётПоПродажам": ["View"]
  }
}
```

**Типы объектов:** `Catalog`, `Document`, `Report`, `DataProcessor`, `InformationRegister`, `AccumulationRegister`

**Права:** `Read`, `Insert`, `Update`, `Delete`, `View`, `Edit`, `InteractiveInsert`, `InteractiveDelete`, `Posting`, `UndoPosting`

## Ловушки

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
