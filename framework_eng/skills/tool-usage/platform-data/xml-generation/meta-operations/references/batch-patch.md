# Batch JSON patch - batch editing mode

An extension of the `meta edit` command that allows you to apply **multiple operations to a single object in one transaction** via a JSON file or an inline batch separated by `;;`.

---

## Why batch mode is needed

| Mode | When it is appropriate |
|-------|------------------------|
| Inline `--op` | One operation, a quick edit in the terminal |
| Inline batch `;;` | 2-4 operations of one type (add/remove only) |
| JSON batch (`--batch`) | Mixed operations: add + remove + modify in one call, agent-generated, reproducible migrations |

The main advantage of JSON batch is **atomicity**: either all operations are applied, or none of them are (on error, rollback occurs).

---

## CLI

### Inline batch via `;;`

Multiple values of one operation are separated by `;;`:

```bash
xml-gen meta edit <objectPath> --op add-attribute "Цена: Number(15,2) ;; Количество: Number(15,3) | nonneg"
xml-gen meta edit <objectPath> --op remove-attribute "СтараяКолонка ;; ЕщёОднаКолонка"
```

### JSON batch via `--batch`

```bash
xml-gen meta edit --batch <file.json>
```

The file can reference a single object or define a list of objects (a multi-object patch):

```bash
# Single object — ObjectPath inside JSON or as a parameter
xml-gen meta edit <objectPath> --batch patch.json

# Multi-object — ObjectPath is set in each array element
xml-gen meta edit --batch multi-patch.json
```

---

## JSON patch structure

### Single patch (one object)

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

### Multi-object patch (array)

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

## Supported operations in a JSON patch

### add - add

| Key | Object types | Element format |
|-----|--------------|----------------|
| `attributes` | Catalog, Document, Register\*, ChartOf\*, BP, Task, Report, DP | shorthand string or object `{name, type, ...}` |
| `dimensions` | \*Register (4 types) | shorthand string or object |
| `resources` | \*Register (4 types) | shorthand string or object |
| `tabularSections` | Catalog, Document, ChartOf\*, BP, Task, Report, DP | object `{name, attrs[]}` |
| `forms` | all except Constant | array of strings (form names) |
| `templates` | all except Constant | array of strings |
| `commands` | all except Constant | array of strings |
| `enumValues` | Enum | array of strings |
| `columns` | DocumentJournal | shorthand string or object |

### remove - remove

The same keys as in `add`. Values are arrays of names:

```json
{ "remove": { "attributes": ["Рекв1", "Рекв2"], "tabularSections": ["ТЧ1"] } }
```

### modify - modify

| Key | Description |
|-----|-------------|
| `properties` | Scalar object properties: `CodeLength`, `Hierarchical`, etc. |
| `attributes` | Dictionary `{AttrName: {name?, type?, synonym?, indexing?, fillChecking?}}` |
| `dimensions` | Same as `attributes` |
| `resources` | Same as `attributes` |
| `tabularSections` | Dictionary `{TabularSectionName: {add?, remove?, modify?}}` - nested tabular section patch |
| `enumValues` | Dictionary `{OldName: {name?}}` |

---

## Positional insertion

In a JSON patch, position is specified by `after` / `before`:

```json
{
  "add": {
    "attributes": [
      { "name": "Склад", "type": "CatalogRef.Склады", "after": "Организация" }
    ]
  }
}
```

In an inline batch, use the `>> after AnchorName` suffix:

```
"Склад: CatalogRef.Склады >> after Организация ;; Цена: Number(15,2)"
```

---

## Key synonyms (case-insensitive)

| Canonical | Synonyms |
|-----------|----------|
| `attributes` | `requisites`, `attrs` |
| `tabularSections` | `tabularParts`, `ts` |
| `dimensions` | `dimensions`, `dims` |
| `resources` | `resources`, `res` |
| `enumValues` | `values`, `choices` |
| `columns` | `columns`, `cols` |
| `forms` | `forms` |
| `templates` | `templates` |
| `commands` | `commands` |
| `properties` | `properties` |
| `add` | `add` |
| `remove` | `remove` |
| `modify` | `modify` |

---

## Composite types

For attributes with multiple allowed types:

```json
{ "name": "Значение", "type": ["String", "Number(15,2)", "Date", "CatalogRef.Контрагенты"] }
```

In inline format - via `+`:
```
"Значение: String + Number(15,2) + Date + CatalogRef.Контрагенты"
```

---

## Typical agent generation scenario

The agent receives the task "add attributes `Артикул`, `Вес`, tabular section `Аналоги` to the `Товары` catalog, remove the obsolete `УстарелоеПоле`". Instead of three sequential calls, the agent generates one JSON file and runs one command:

```bash
# Агент создаёт файл patch.json:
xml-gen meta edit src/cf/Catalogs/Товары --batch patch.json
```

Contents of `patch.json`:
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

## Relation to other skills

| Skill/command | Relation |
|---------------|----------|
| `meta compile` (this skill) | Creates an object from scratch. `--batch` modifies an existing one |
| `skd-edit` | Similar patch concept for data composition schemes |
| `meta validate` | Runs automatically after a successful `--batch`, if `--no-validate` is not specified |
| `meta info` | Apply before the patch to make sure the object's current structure is correct |
| future `cfe-patch-method` | Planned patch skill for extension methods (CFE), with similar JSON batch semantics |

---

## Command flags

| Flag | Description |
|------|-------------|
| `--batch <file.json>` | JSON file with operations (required in batch mode) |
| `--no-validate` | Do not run `meta validate` after applying the patch |
| `--dry-run` | Show the operation plan without applying changes |
| `--strict` | Stop execution at the first error (by default - apply the rest) |

---

> **Implementation status:** JSON batch mode (`--batch <file.json>`) is implemented in `xml-gen` (Java). Transactional: if any operation fails, the file is not changed. Inline batch via `;;` is also supported.
