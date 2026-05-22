---
name: form-dsl
description: "JSON DSL for generating 1С managed forms with UI elements, attributes, and commands. Use for form compile and editing forms through xml-generation (edit commands)."
---

# Form DSL

## Commands

```bash
xml-gen form compile [--format designer|edt] <input.json> <output.xml>

# Generate a form from object metadata
xml-gen form compile --from-object [--preset erp-standard] [--object <path>] <output.xml>

xml-gen form info <Form.xml>
```

Editing existing forms (add-attribute, add-element, move-element, etc.) — see [xml-generation](../SKILL.md) §3 Edit commands

## `--from-object` Mode

Generates `Form.xml` from the object's XML description. Coverage: `Catalog` (item/folder/list/choice), `Document` (item/list/choice), `InformationRegister` (record/list), `AccumulationRegister` (list), `ChartOfCharacteristicTypes`, `ExchangePlan`, `ChartOfAccounts`, `DataProcessor`/`Report` (template).

Purpose is determined by the folder name: `ФормаСписка`→list, `ФормаВыбора`→choice, `ФормаГруппы`→folder, `ФормаЗаписи`→record, otherwise item.

The `erp-standard` preset is built in and overridden by file `<project-root>/presets/skills/form/erp-standard.json`.

Guardrails: `ValueStorage` attributes are skipped; `FormDataStructure/Collection/Tree` in an attribute → `FromObjectException`.

## DSL Structure

Minimal form: `{"attributes": [], "elements": []}`

### Attributes

```json
{"name": "ИмяРеквизита", "type": "тип", "title": "Заголовок"}
```

**Types:** `string`, `string(N)`, `number`, `number(D,F)`, `boolean`, `date`, `uuid`, `CatalogRef.Name`, `DocumentRef.Name`, `ValueTable`

**Prohibited runtime types:** `FormDataStructure`, `FormDataCollection`, `FormDataTree` do not exist in the XML schema and cause an XDTO error when loading (compiler: `IllegalArgumentException`; validator: `FORM-114 ERROR`). Use `CatalogObject.X` / `DocumentObject.X` / `DataProcessorObject.X`, `ValueTable`, `ValueTree`.

### UI Elements

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

### Commands and Events

```json
{"name": "Сохранить", "action": "Save", "title": "Сохранить"}
{"events": {"onCreateAtServer": "ПриСозданииНаСервере", "onOpen": "ПриОткрытии"}}
```

The DSL specifies only the procedure name; set the compiler directive in the module manually: `onCreateAtServer` → `&НаСервере`, `onOpen`/`onClose`/`beforeClose` → `&НаКлиенте`. Mixing contexts = compilation error or server objects being unavailable.

UUID, ID, ContextMenu, ExtendedTooltip are created automatically.

## Pitfalls

```json
// ❌ dataPath does not match the attribute → the element will not display data
{"attributes": [{"name": "Наименование", "type": "string(100)"}],
 "elements": [{"type": "input", "name": "Поле1", "dataPath": "Поле1"}]}

// ✅ dataPath = attribute name (or the path to a tabular section field: Товары.Номенклатура)
{"elements": [{"type": "input", "name": "Наименование", "dataPath": "Наименование"}]}
```

```json
// ❌ page without a pages parent — the platform will not load the form
{"elements": [{"type": "page", "name": "Страница1", "children": [...]}]}

// ✅ pages as a container
{"elements": [{"type": "pages", "name": "Страницы", "children": [{"type": "page", ...}]}]}
```

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
