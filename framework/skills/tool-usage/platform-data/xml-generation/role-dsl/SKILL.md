---
name: role-dsl
description: JSON DSL для генерации ролей 1С с правами доступа к объектам метаданных. Используй при role compile и редактировании Rights.xml через xml-gen-cli.
---

# Role DSL

JSON DSL для генерации ролей 1С с правами доступа.

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать роль с нуля (права на объекты) | `role compile` с JSON DSL |
| Нужно добавить права на объект в существующую роль | `role add-object` → [xml-gen-cli](../xml-gen-cli/) |
| Нужно изменить право для существующего объекта | `role add-right` → [xml-gen-cli](../xml-gen-cli/) |
| Нужно анализировать существующую роль | `role info <Rights.xml>` |

## Команда compile

```bash
xml-gen role compile [--format designer|edt] <input.json> <output_dir>
```

**Результат (Designer):** создаётся `output_dir/Roles/<Name>.xml` и `output_dir/Roles/<Name>/Ext/Rights.xml`

## Команда info

Аудит прав роли: объекты, права, RLS, шаблоны.

```bash
xml-gen role info <Rights.xml>
```

**Пример:**
```bash
xml-gen role compile role.json output/
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

### Типы объектов

`Catalog`, `Document`, `Report`, `DataProcessor`, `InformationRegister`, `AccumulationRegister`

### Права доступа

`Read`, `Insert`, `Update`, `Delete`, `View`, `Edit`, `InteractiveInsert`, `InteractiveDelete`, `Posting`, `UndoPosting`

## Редактирование

```bash
xml-gen role add-object --name <ObjectName> --rights <Right1,Right2,...> <Rights.xml>
xml-gen role add-right --object <ObjectName> --name <RightName> --value <true|false> <Rights.xml>
```

## Правильно / Неправильно

```json
// ❌ права в camelCase → enum не распознает
"rights": {"Catalog.Номенклатура": ["read", "insert"]}

// ✅ enum RoleRight: Read, Insert, Update, Delete, View, Edit, Posting, UndoPosting
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
