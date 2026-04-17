---
name: form-dsl
description: JSON DSL for generating 1С managed forms with UI elements, attributes, and commands. Use it for form compile and editing forms through xml-gen-cli.
---

# Form DSL

## Commands

```bash
xml-gen form compile [--format designer|edt] <input.json> <output.xml>

# Generate a form from an object's metadata (Catalog/Document/Register and other objects)
# Object type and purpose are derived from the OutputPath path.
xml-gen form compile --from-object [--preset erp-standard] [--object <path>] <output.xml>

xml-gen form info <Form.xml>
```

Editing existing forms (add-attribute, add-element, move-element, etc.) — see [xml-gen-cli](../xml-gen-cli/)

## `--from-object` Mode

Generates `Form.xml` automatically from the object's XML description. Coverage: `Catalog` (item/folder/list/choice), `Document` (item/list/choice), `InformationRegister` (record/list), `AccumulationRegister` (list), `ChartOfCharacteristicTypes`, `ExchangePlan`, `ChartOfAccounts`, `DataProcessor`/`Report` (stub).

Purpose is determined by the form folder name: `ФормаСписка`→list, `ФормаВыбора`→choice, `ФормаГруппы`→folder, `ФормаЗаписи`→record, otherwise item/default.

Layout is controlled through the `erp-standard` JSON preset (built in) — overridden by the `<project-root>/presets/skills/form/erp-standard.json` file.

Guardrails: `ValueStorage`-attributes are automatically skipped; `FormDataStructure/Collection/Tree` in an attribute → `FromObjectException`.

## DSL Structure

Minimal form: `{"attributes": [], "elements": []}`

### Attributes (attributes)

```json
{"name": "ИмяРеквизита", "type": "тип", "title": "Заголовок"}
```

**Types:** `string`, `string(N)`, `number`, `number(D,F)`, `boolean`, `date`, `uuid`, `CatalogRef.Name`, `DocumentRef.Name`, `ValueTable`

**Forbidden runtime types:** `FormDataStructure`, `FormDataCollection`, `FormDataTree` — they do not exist in the form XML schema and cause an XDTO error on load. The compiler throws `IllegalArgumentException`, and the validator returns `FORM-114 ERROR`. Use `CatalogObject.X` / `DocumentObject.X` / `DataProcessorObject.X`, `ValueTable`, `ValueTree`.

### UI Elements (elements)

| DSL type | XML type | Description |
|----------|---------|----------|
| `input` | InputField | Input field |
| `group` | UsualGroup | Group (`"group": "Vertical"/"Horizontal"`, `children`) |
| `table` | Table | Table (`dataPath`, `columns`) |
| `button` | Button | Button (`commandName`) |
| `label` | LabelDecoration | Label decoration |
| `checkbox` | CheckBoxField | Checkbox field |
| `pages` | Pages | Container for pages |
| `page` | Page | Page (only inside `pages`) |

### Commands (commands)

```json
{"name": "Сохранить", "action": "Save", "title": "Сохранить"}
```

### Events (events)

```json
{"events": {"onCreateAtServer": "ПриСозданииНаСервере", "onOpen": "ПриОткрытии"}}
```

The DSL specifies only the procedure name. Set the compiler directive in the form module manually:

| DSL event | Directive |
|-------------|-----------|
| `onCreateAtServer` | `&НаСервере` |
| `onOpen`, `onClose`, `beforeClose` | `&НаКлиенте` |

Mixing contexts = compilation error or server objects being unavailable on the client.

## Automatic Generation

UUID, ID, ContextMenu, ExtendedTooltip are created automatically. Arbitrary nesting: group → group → input, pages → page → table.

## Correct / Incorrect

```json
// ❌ dataPath не совпадает с реквизитом → элемент не отобразит данные
{"attributes": [{"name": "Наименование", "type": "string(100)"}], "elements": [{"type": "input", "name": "Поле1", "dataPath": "Поле1"}]}

// ✅ dataPath = name реквизита (или путь к полю ТЧ: Товары.Номенклатура)
{"attributes": [{"name": "Наименование", "type": "string(100)"}], "elements": [{"type": "input", "name": "Наименование", "dataPath": "Наименование"}]}
```

```json
// ❌ page без родителя pages
{"elements": [{"type": "page", "name": "Страница1", "children": [...]}]}

// ✅ pages как контейнер, page внутри
{"elements": [{"type": "pages", "name": "Страницы", "children": [{"type": "page", "name": "Страница1", "children": [...]}]}]}
```

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
