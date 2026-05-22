# Batch JSON-патч — режим пакетного редактирования

Расширение команды `meta edit`, позволяющее применить **несколько операций к одному объекту в одной транзакции** через JSON-файл или inline-batch через `;;`.

---

## Зачем нужен batch-режим

| Режим | Когда уместен |
|-------|---------------|
| Inline `--op` | Одна операция, быстрая правка в терминале |
| Inline batch `;;` | 2–4 операции одного типа (только add/remove) |
| JSON batch (`--batch`) | Смешанные операции: add + remove + modify за один вызов, генерация агентом, воспроизводимые миграции |

Главное преимущество JSON batch — **атомарность**: либо все операции применяются, либо ни одна (при ошибке — откат).

---

## CLI

### Inline batch через `;;`

Несколько значений одной операции разделяются `;;`:

```bash
xml-gen meta edit <objectPath> --op add-attribute "Цена: Number(15,2) ;; Количество: Number(15,3) | nonneg"
xml-gen meta edit <objectPath> --op remove-attribute "СтараяКолонка ;; ЕщёОднаКолонка"
```

### JSON batch через `--batch`

```bash
xml-gen meta edit --batch <file.json>
```

Файл может ссылаться на один объект или задавать список объектов (мультиобъектный патч):

```bash
# Один объект — ObjectPath внутри JSON или как параметр
xml-gen meta edit <objectPath> --batch patch.json

# Мультиобъектный — ObjectPath задаётся в каждом элементе массива
xml-gen meta edit --batch multi-patch.json
```

---

## Структура JSON-патча

### Одиночный патч (один объект)

```json
{
  "add": {
    "attributes": [
      "Цена: Number(15,2)",
      { "name": "Количество", "type": "Number(15,3)", "fillChecking": "ShowError" }
    ],
    "tabularSections": [
      {
        "name": "Штрихкоды",
        "attrs": ["Штрихкод: String(13)", "Тип: EnumRef.ТипыШтрихкодов"]
      }
    ],
    "forms": ["ФормаЭлемента"],
    "enumValues": ["Оплачен", "ЧастичноОплачен"]
  },
  "remove": {
    "attributes": ["УстаревшийРеквизит", "СтараяКолонка"],
    "tabularSections": ["УстаревшаяТЧ"]
  },
  "modify": {
    "properties": {
      "CodeLength": 11,
      "DescriptionLength": 150
    },
    "attributes": {
      "СтароеИмя": { "name": "НовоеИмя", "type": "String(200)" }
    },
    "tabularSections": {
      "Товары": {
        "add": ["СтавкаНДС: EnumRef.СтавкиНДС"],
        "remove": ["УстаревшийРекв"],
        "modify": {
          "Цена": { "type": "Number(18,4)" }
        }
      }
    }
  }
}
```

### Мультиобъектный патч (массив)

```json
[
  {
    "objectPath": "src/cf/Catalogs/Товары",
    "add": {
      "attributes": ["Артикул: String(50) | index"]
    }
  },
  {
    "objectPath": "src/cf/Documents/ПоступлениеТоваров",
    "add": {
      "tabularSections": [{ "name": "УслугиДоп", "attrs": ["Услуга: CatalogRef.Услуги", "Сумма: Number(15,2)"] }]
    },
    "remove": {
      "attributes": ["УстаревшийРеквизит"]
    }
  }
]
```

---

## Поддерживаемые операции в JSON-патче

### add — добавить

| Ключ | Типы объектов | Формат элемента |
|------|---------------|-----------------|
| `attributes` | Catalog, Document, Register\*, ChartOf\*, BP, Task, Report, DP | shorthand-строка или объект `{name, type, ...}` |
| `dimensions` | \*Register (4 типа) | shorthand-строка или объект |
| `resources` | \*Register (4 типа) | shorthand-строка или объект |
| `tabularSections` | Catalog, Document, ChartOf\*, BP, Task, Report, DP | объект `{name, attrs[]}` |
| `forms` | все кроме Constant | массив строк (имён форм) |
| `templates` | все кроме Constant | массив строк |
| `commands` | все кроме Constant | массив строк |
| `enumValues` | Enum | массив строк |
| `columns` | DocumentJournal | shorthand-строка или объект |

### remove — удалить

Ключи те же, что в `add`. Значения — массив имён:

```json
{ "remove": { "attributes": ["Рекв1", "Рекв2"], "tabularSections": ["ТЧ1"] } }
```

### modify — изменить

| Ключ | Описание |
|------|----------|
| `properties` | Скалярные свойства объекта: `CodeLength`, `Hierarchical` и др. |
| `attributes` | Словарь `{ИмяРекв: {name?, type?, synonym?, indexing?, fillChecking?}}` |
| `dimensions` | Аналогично `attributes` |
| `resources` | Аналогично `attributes` |
| `tabularSections` | Словарь `{ИмяТЧ: {add?, remove?, modify?}}` — вложенный патч ТЧ |
| `enumValues` | Словарь `{СтароеИмя: {name?}}` |

---

## Позиционная вставка

В JSON-патче позиция задаётся полями `after` / `before`:

```json
{
  "add": {
    "attributes": [
      { "name": "Склад", "type": "CatalogRef.Склады", "after": "Организация" }
    ]
  }
}
```

В inline-batch через суффикс `>> after ИмяЯкоря`:

```
"Склад: CatalogRef.Склады >> after Организация ;; Цена: Number(15,2)"
```

---

## Синонимы ключей (case-insensitive)

| Канонический | Синонимы |
|-------------|----------|
| `attributes` | `реквизиты`, `attrs` |
| `tabularSections` | `табличныеЧасти`, `тч`, `ts` |
| `dimensions` | `измерения`, `dims` |
| `resources` | `ресурсы`, `res` |
| `enumValues` | `значения`, `values` |
| `columns` | `графы`, `колонки` |
| `forms` | `формы` |
| `templates` | `макеты` |
| `commands` | `команды` |
| `properties` | `свойства` |
| `add` | `добавить` |
| `remove` | `удалить` |
| `modify` | `изменить` |

---

## Составные типы

Для реквизитов с несколькими допустимыми типами:

```json
{ "name": "Значение", "type": ["String", "Number(15,2)", "Date", "CatalogRef.Контрагенты"] }
```

В inline-формате — через `+`:
```
"Значение: String + Number(15,2) + Date + CatalogRef.Контрагенты"
```

---

## Типичный сценарий генерации агентом

Агент получает задачу «добавить в справочник `Товары` реквизиты `Артикул`, `Вес`, ТЧ `Аналоги`, удалить устаревший `УстарелоеПоле`». Вместо трёх последовательных вызовов агент генерирует один JSON-файл и запускает одну команду:

```bash
# Агент создаёт файл patch.json:
xml-gen meta edit src/cf/Catalogs/Товары --batch patch.json
```

Содержимое `patch.json`:
```json
{
  "add": {
    "attributes": [
      "Артикул: String(50) | index",
      "Вес: Number(10,3)"
    ],
    "tabularSections": [
      { "name": "Аналоги", "attrs": ["Номенклатура: CatalogRef.Номенклатура | req"] }
    ]
  },
  "remove": {
    "attributes": ["УстарелоеПоле"]
  }
}
```

---

## Связь с другими навыками

| Навык/команда | Отношение |
|---------------|-----------|
| `meta compile` (этот навык) | Создаёт объект с нуля. `--batch` — модифицирует существующий |
| `skd-edit` | Аналогичная patch-концепция для схем компоновки данных |
| `meta validate` | Запускается автоматически после успешного `--batch`, если не задан `--no-validate` |
| `meta info` | Применяй до патча, чтобы убедиться в актуальной структуре объекта |
| будущий `cfe-patch-method` | Планируемый патч-навык для методов расширений (CFE), аналогичная семантика JSON batch |

---

## Флаги команды

| Флаг | Описание |
|------|----------|
| `--batch <file.json>` | JSON-файл с операциями (обязателен в batch-режиме) |
| `--no-validate` | Не запускать `meta validate` после применения патча |
| `--dry-run` | Показать план операций без применения изменений |
| `--strict` | Прервать выполнение при первой ошибке (по умолчанию — применить остальные) |

---

> **Статус реализации:** batch JSON-режим (`--batch <file.json>`) реализован в `xml-gen` (Java). Транзакционно: при сбое любой операции файл не меняется. Inline batch через `;;` тоже поддерживается.
