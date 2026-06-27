---
name: meta-operations
description: "xml-gen metadata: objects, attributes, tabular parts"
---

# Meta Operations

## When to Use

| Trigger | Action |
|---------|----------|
| Create a catalog/document/register | `meta compile meta.json <output_dir>` |
| View object structure | `meta info <objectPath>` |
| Add an attribute/tabular section/dimension | `meta edit <objectPath> --op add-attribute "Name: Type"` |
| Validate a metadata object | `meta validate <objectPath>` |
| Remove an object from the configuration | `meta remove <configDir> Type.Name` |

## Supported Types (23)

| Category | Types |
|-----------|------|
| Reference | Catalog, Document, Enum, ChartOfCharacteristicTypes, ChartOfAccounts, ChartOfCalculationTypes, ExchangePlan |
| Registers | InformationRegister, AccumulationRegister, AccountingRegister, CalculationRegister |
| Processes | BusinessProcess, Task |
| Service | HTTPService, WebService |
| Other | Constant, DefinedType, CommonModule, Report, DataProcessor, ScheduledJob, DocumentJournal, EventSubscription |

## Commands

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

**Full Catalog properties:** `hierarchical`, `hierarchyType` (HierarchyFoldersAndItems|HierarchyItemsOnly), `limitLevelCount`, `levelCount`, `foldersOnTop`, `codeLength`, `codeType` (String|Number), `codeAllowedLength` (Variable|Fixed), `codeSeries` (WholeCatalog|WithinOwnerSubordination|WithinSubordination), `descriptionLength`, `autonumbering`, `checkUnique`, `defaultPresentation` (AsDescription|AsCode), `subordinationUse` (ToItems|ToFolders|ToFoldersAndItems), `quickChoice`, `choiceMode` (BothWays|FromChoiceForm|QuickChoice), `editType` (InDialog|InList|BothWays), `owners` (array of strings, e.g. `["Catalog.Counterparties"]`).

**Attribute flag `multiLine`** - makes a string field multiline (`<MultiLine>true</MultiLine>`). In shorthand: `"Description: String(500) | multiline"`.

### meta info

```bash
xml-gen meta info [--mode brief|overview|full] <objectPath>
```

### meta edit

```bash
xml-gen meta edit <objectPath> --op <operation> "<value>"
```

Operations: `add-attribute` / `add-dimension` / `add-resource` / `add-ts` / `add-ts-attribute` / `add-enumValue` / `add-form` / `add-template` / `add-command` / `remove-attribute` / `remove-ts` / `remove-enumValue` / `modify-attribute` / `add-property` / `modify-property`

**Shorthand format:**
```
ИмяРеквизита: ТипДанных | флаги >> after/before Якорь
```

Examples: `"ItemCode: String(50)"`, `"Amount: Number(15,2) | nonneg"`, `"Counterparty: CatalogRef.Counterparties | indexing"`

**Predefined data:**

```bash
# Простой shorthand, батч через ;;
xml-gen meta edit src/xml/Catalogs/Товары.xml --op add-predefined \
  --value "Основной|Основной элемент|000000001;;Группа|Группа||folder"

# Полное JSON-дерево элементов, включая пустой Code, ChildItems и поля планов
xml-gen meta edit src/xml/ChartsOfAccounts/Основной.xml --op add-predefined \
  --value @predefined-items.json
```

`add-predefined` supports `Catalog`, `ChartOfAccounts`, `ChartOfCalculationTypes`, `ChartOfCharacteristicTypes`. In shorthand format: `Name[|Description[|Code[|folder]]]`; an explicitly empty third field (`Name|Description|`) creates `<Code/>`, and a missing third field generates the next code. The JSON file can be an array or an object `{ "items": [...] }`; item fields: `name`, `code`, `description`, `isFolder`, `childItems`, `types`, `accountType`, `offBalance`, `order`, `accountingFlags`, `extDimensionTypes`, `actionPeriodIsBase`, `displaced`.

**Exchange plan contents:**

```bash
xml-gen meta edit src/xml/ExchangePlans/РИБ.xml --op add-exchange-content \
  --value "Catalog.Товары|Deny;;Document.Заказ|Allow"

xml-gen meta edit src/xml/ExchangePlans/РИБ.xml --op add-exchange-content \
  --value @exchange-content-items.json
```

`add-exchange-content` supports only `ExchangePlan` objects. Shorthand format: `Metadata[|AutoRecord]`; if `AutoRecord` is not specified, `Deny` is used. The JSON can be an array or an object `{ "items": [...] }` with `metadata` and `autoRecord` fields.

### meta validate

~40 checks: XML structure, UUID, Properties, boolean properties, type-specific rules (22 types), strict enum validation (HierarchyType, SubordinationUse, ChoiceMode, EditType, CodeAllowedLength, CodeSeries, NumberAllowedLength, RegisterRecordsDeletion, RegisterRecordsWritingOnPost, Periodicity, RequireCalculationTypes, etc.), file structure.

```bash
xml-gen meta validate <objectPath>
```

**Compilation invariants:**
- `FillFromFillingValue` / `FillValue` / `DataHistory` - only for InformationRegister attributes; for other registers they trigger an XSD error on load.
- Attribute names that match standard ones are rejected at compile time: `Ref, Code, Description, Parent, Owner, IsFolder, DeletionMark, PostingMode, DataVersion, Predefined, PredefinedDataName, Posted, Date, Number` (and the Russian equivalents: `Ссылка, Код, Наименование, Родитель, Владелец, ЭтоГруппа, ПометкаУдаления, РежимПроведения, ВерсияДанных, Предопределенный, ИмяПредопределенныхДанных, Проведен, Дата, Номер`).

### meta remove

```bash
xml-gen meta remove <configDir> <Type.Name> [--dry-run] [--keep-files] [--force]
```

Algorithm: file search -> checking references in XML/BSL -> removing from Configuration.xml ChildObjects -> removing from subsystems -> deleting files.

## Russian Type Synonyms

In shorthand: `Справочник` -> `Catalog`, `Документ` -> `Document`, `Перечисление` -> `Enum`, `РегистрСведений` -> `InformationRegister`, etc.

## Batch JSON Patch (meta edit --batch)

```bash
# Один объект
xml-gen meta edit <objectPath> --batch patch.json

# Мультиобъектный патч (ObjectPath внутри JSON)
xml-gen meta edit --batch multi-patch.json
```

Use when: several operations of different types need to be applied to a single object in one call, the agent is generating patches, or schema migrations need to be reproducible.

**Inline batch via `;;`:**
```bash
xml-gen meta edit <objectPath> --op add-attribute "Цена: Number(15,2) ;; Вес: Number(10,3) | nonneg"
```

Detailed specification, full JSON structure, positional insertion, multi-object patches - [references/batch-patch.md](references/batch-patch.md).

> **Status:** `--batch <file.json>` and inline `;;` are implemented in `xml-gen` (Java, transactional).

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.1"
---
