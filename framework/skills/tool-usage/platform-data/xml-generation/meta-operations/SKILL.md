---
name: meta-operations
description: "Операции с объектами метаданных 1С (23 типа) — compile, info, edit, validate, remove. Используй при создании справочников, документов, регистров, перечислений и других объектов конфигурации."
---

# Meta Operations

## Когда применять

| Триггер | Действие |
|---------|----------|
| Создать справочник/документ/регистр | `meta compile meta.json <output_dir>` |
| Посмотреть структуру объекта | `meta info <objectPath>` |
| Добавить реквизит/ТЧ/измерение | `meta edit <objectPath> --op add-attribute "Name: Type"` |
| Проверить объект метаданных | `meta validate <objectPath>` |
| Удалить объект из конфигурации | `meta remove <configDir> Type.Name` |

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
    { "name": "Штрихкоды", "attributes": ["Штрихкод: String(13)"] }
  ]
}
```

**Полные свойства Catalog:** `hierarchical`, `hierarchyType` (HierarchyFoldersAndItems|HierarchyItemsOnly), `limitLevelCount`, `levelCount`, `foldersOnTop`, `codeLength`, `codeType` (String|Number), `codeAllowedLength` (Variable|Fixed), `codeSeries` (WholeCatalog|WithinOwnerSubordination|WithinSubordination), `descriptionLength`, `autonumbering`, `checkUnique`, `defaultPresentation` (AsDescription|AsCode), `subordinationUse` (ToItems|ToFolders|ToFoldersAndItems), `quickChoice`, `choiceMode` (BothWays|FromChoiceForm|QuickChoice), `editType` (InDialog|InList|BothWays), `owners` (массив строк, напр. `["Catalog.Контрагенты"]`).

**Флаг реквизита `multiLine`** — делает строковое поле многострочным (`<MultiLine>true</MultiLine>`). В shorthand: `"Описание: String(500) | multiline"`.

### meta info

```bash
xml-gen meta info [--mode brief|overview|full] <objectPath>
```

### meta edit

```bash
xml-gen meta edit <objectPath> --op <operation> "<value>"
```

Операции: `add-attribute` / `add-dimension` / `add-resource` / `add-ts` / `add-ts-attribute` / `add-enumValue` / `add-form` / `add-template` / `add-command` / `remove-attribute` / `remove-ts` / `remove-enumValue` / `modify-attribute` / `add-property` / `modify-property`

**Shorthand формат:**
```
ИмяРеквизита: ТипДанных | флаги >> after/before Якорь
```

Примеры: `"Артикул: String(50)"`, `"Сумма: Number(15,2) | nonneg"`, `"Контрагент: CatalogRef.Контрагенты | indexing"`

### meta validate

~40 проверок: структура XML, UUID, Properties, boolean-свойства, type-specific правила (22 типа), строгая enum-валидация (HierarchyType, SubordinationUse, ChoiceMode, EditType, CodeAllowedLength, CodeSeries, NumberAllowedLength, RegisterRecordsDeletion, RegisterRecordsWritingOnPost, Periodicity, RequireCalculationTypes и др.), файловая структура.

```bash
xml-gen meta validate <objectPath>
```

**Инварианты компиляции:**
- `FillFromFillingValue` / `FillValue` / `DataHistory` — только для реквизитов InformationRegister; для других регистров вызывают XSD-ошибку при загрузке.
- Имена реквизитов, совпадающие со стандартными, отклоняются при компиляции: `Ref, Code, Description, Parent, Owner, IsFolder, DeletionMark, PostingMode, DataVersion, Predefined, PredefinedDataName, Posted, Date, Number` (и русские синонимы: `Ссылка, Код, Наименование, Родитель, Владелец, ЭтоГруппа, ПометкаУдаления, РежимПроведения, ВерсияДанных, Предопределенный, ИмяПредопределенныхДанных, Проведен, Дата, Номер`).

### meta remove

```bash
xml-gen meta remove <configDir> <Type.Name> [--dry-run] [--keep-files] [--force]
```

Алгоритм: поиск файлов → проверка ссылок в XML/BSL → удаление из Configuration.xml ChildObjects → удаление из подсистем → удаление файлов.

## Русские синонимы типов

В shorthand: Справочник → Catalog, Документ → Document, Перечисление → Enum, РегистрСведений → InformationRegister и т.д.

## Batch JSON-патч (meta edit --batch)

```bash
# Один объект
xml-gen meta edit <objectPath> --batch patch.json

# Мультиобъектный патч (ObjectPath внутри JSON)
xml-gen meta edit --batch multi-patch.json
```

Применять при: нескольких операциях разных типов к одному объекту за один вызов, генерации патчей агентом, воспроизводимых миграциях схемы.

**Inline batch через `;;`:**
```bash
xml-gen meta edit <objectPath> --op add-attribute "Цена: Number(15,2) ;; Вес: Number(10,3) | nonneg"
```

Подробная спецификация, полная структура JSON, позиционная вставка, мультиобъектные патчи — [references/batch-patch.md](references/batch-patch.md).

> **Статус:** `--batch <file.json>` и inline `;;` реализованы в `xml-gen` (Java, транзакционно).

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.1"
---
