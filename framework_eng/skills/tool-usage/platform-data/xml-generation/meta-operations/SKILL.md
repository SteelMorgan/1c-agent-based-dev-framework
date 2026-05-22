---
name: meta-operations
description: "Operations on 1С metadata objects (23 types) - compile, info, edit, validate, remove. Use when creating catalogs, documents, registers, enumerations, and other configuration objects."
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
| Reference types | Catalog, Document, Enum, ChartOfCharacteristicTypes, ChartOfAccounts, ChartOfCalculationTypes, ExchangePlan |
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

**The `multiLine` attribute flag** makes a string field multiline (`<MultiLine>true</MultiLine>`). In shorthand: `"Description: String(500) | multiline"`.

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

Examples: `"Article: String(50)"`, `"Amount: Number(15,2) | nonneg"`, `"Counterparty: CatalogRef.Counterparties | indexing"`

### meta validate

About 40 checks: XML structure, UUID, Properties, boolean properties, type-specific rules (22 types), strict enum validation (HierarchyType, SubordinationUse, ChoiceMode, EditType, CodeAllowedLength, CodeSeries, NumberAllowedLength, RegisterRecordsDeletion, RegisterRecordsWritingOnPost, Periodicity, RequireCalculationTypes and others), file structure.

```bash
xml-gen meta validate <objectPath>
```

**Compilation invariants:**
- `FillFromFillingValue` / `FillValue` / `DataHistory` — only for InformationRegister attributes; for other registers they trigger an XSD error on load.
- Attribute names that match standard ones are rejected during compilation: `Ref, Code, Description, Parent, Owner, IsFolder, DeletionMark, PostingMode, DataVersion, Predefined, PredefinedDataName, Posted, Date, Number` (and Russian synonyms: `Ссылка, Код, Наименование, Родитель, Владелец, ЭтоГруппа, ПометкаУдаления, РежимПроведения, ВерсияДанных, Предопределенный, ИмяПредопределенныхДанных, Проведен, Дата, Номер`).

### meta remove

```bash
xml-gen meta remove <configDir> <Type.Name> [--dry-run] [--keep-files] [--force]
```

Algorithm: file search → link check in XML/BSL → removal from Configuration.xml ChildObjects → removal from subsystems → deletion of files.

## Russian Type Synonyms

In shorthand: Справочник → Catalog, Документ → Document, Перечисление → Enum, РегистрСведений → InformationRegister, etc.

## Batch JSON Patch (meta edit --batch)

```bash
# Один объект
xml-gen meta edit <objectPath> --batch patch.json

# Мультиобъектный патч (ObjectPath внутри JSON)
xml-gen meta edit --batch multi-patch.json
```

Use when: multiple operations of different types on one object in a single call, agent-generated patch creation, reproducible schema migrations.

**Inline batch via `;;`:**
```bash
xml-gen meta edit <objectPath> --op add-attribute "Цена: Number(15,2) ;; Вес: Number(10,3) | nonneg"
```

Detailed specification, full JSON structure, positional insertion, multi-object patches — [references/batch-patch.md](references/batch-patch.md).

> **Status:** `--batch <file.json>` and inline `;;` are implemented in `xml-gen` (Java, transactionally).

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.1"
---
