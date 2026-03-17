---
name: meta-operations
description: Операции с объектами метаданных 1С (23 типа) — compile, info, edit, validate, remove. Используй при создании справочников, документов, регистров, перечислений и других объектов конфигурации.
---

# Meta Operations

Работа с объектами метаданных 1С (Catalog, Document, Register и др. — 23 типа).

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать справочник/документ/регистр | `meta compile meta.json <output_dir>` |
| Нужно посмотреть структуру объекта | `meta info <objectPath>` |
| Нужно добавить реквизит/ТЧ/измерение | `meta edit <objectPath> --op add-attribute "Name: Type"` |
| Нужно проверить объект метаданных | `meta validate <objectPath>` |
| Нужно удалить объект из конфигурации | `meta remove <configDir> Type.Name` |

## Поддерживаемые типы (23)

| Категория | Типы |
|-----------|------|
| Ссылочные | Catalog, Document, Enum, ChartOfCharacteristicTypes, ChartOfAccounts, ChartOfCalculationTypes, ExchangePlan |
| Регистры | InformationRegister, AccumulationRegister, AccountingRegister, CalculationRegister |
| Процессы | BusinessProcess, Task |
| Сервисные | HTTPService, WebService |
| Прочие | Constant, DefinedType, CommonModule, Report, DataProcessor, ScheduledJob, DocumentJournal, EventSubscription |

## Команды

### meta compile

Генерация объекта из JSON DSL.

```bash
xml-gen meta compile <meta.json> <output_dir>
```

**JSON DSL:**
```json
{
  "type": "Catalog",
  "name": "Товары",
  "codeLength": 9,
  "descriptionLength": 150,
  "hierarchical": true,
  "attributes": [
    "Артикул: String(50)",
    "Цена: Number(15,2)",
    "Производитель: CatalogRef.Контрагенты"
  ],
  "tabularSections": [
    {
      "name": "Штрихкоды",
      "attributes": ["Штрихкод: String(13)"]
    }
  ]
}
```

### meta info

Анализ объекта: свойства, реквизиты, ТЧ, формы.

```bash
xml-gen meta info [--mode brief|overview|full] <objectPath>
```

### meta edit

Модификация объекта (add/remove/modify).

```bash
xml-gen meta edit <objectPath> --op <operation> "<value>"
```

**Операции:**
- `add-attribute` — `"Вес: Number(15,3) | indexing"`
- `add-dimension` — для регистров
- `add-resource` — для регистров
- `add-ts` — `"Штрихкоды"`
- `add-ts-attribute` — `"ТЧ.Штрихкоды: Значение: String(13)"`
- `add-enumValue` — `"Оплачен"`
- `add-form` / `add-template` / `add-command`
- `remove-attribute` / `remove-ts` / `remove-enumValue` и др.
- `modify-attribute` — `"Name: synonym=Новый синоним, type=String(100)"`
- `add-property` / `modify-property` — изменение свойств объекта

**Shorthand формат:**
```
ИмяРеквизита: ТипДанных | флаги >> after/before Якорь
```

Примеры:
```
Артикул: String(50)
Сумма: Number(15,2) | nonneg
Контрагент: CatalogRef.Контрагенты | indexing
```

### meta validate

Валидация объекта (~40 проверок).

```bash
xml-gen meta validate <objectPath>
```

**Проверки:** структура XML, UUID, Properties (Name, Synonym), boolean-свойства, type-specific правила (22 типа), StandardAttributes, forbidden properties, ChildObjects, InternalInfo/GeneratedType, файловая структура.

### meta remove

Удаление объекта из конфигурации.

```bash
xml-gen meta remove <configDir> <Type.Name> [--dry-run] [--keep-files] [--force]
```

**Алгоритм:**
1. Поиск файлов объекта
2. Проверка ссылок в XML/BSL
3. Удаление из Configuration.xml ChildObjects
4. Удаление из подсистем
5. Удаление файлов

## Русские синонимы типов

В shorthand можно использовать русские имена:
Справочник → Catalog, Документ → Document, Перечисление → Enum, Регистр сведений → InformationRegister и т.д.

## См. также

- [config-operations](../config-operations/) — работа с конфигурацией
- [xml-gen-cli](../xml-gen-cli/) — validate и edit-команды

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
