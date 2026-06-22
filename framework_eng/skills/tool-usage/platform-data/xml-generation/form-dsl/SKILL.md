---
name: form-dsl
description: "Use for generating 1C managed forms with UI elements, attributes, and commands through a JSON DSL. Helps describe the form structure and static properties for xml-gen form compile/edit."
---

# Form DSL

## Commands

```bash
xml-gen form compile [--format designer|edt] <input.json> <output.xml>

# Generate a form from the object's metadata
xml-gen form compile --from-object [--preset erp-standard] [--object <path>] <output.xml>

xml-gen form info <Form.xml>
xml-gen form decompile <Form.xml> [output.json]
```

Editing existing forms (add-attribute, add-element, move-element, etc.) - see [xml-generation](../SKILL.md) §3 Edit commands

`form decompile` emits a **draft JSON** scaffold from a sample form. It is not a lossless round-trip: use `form edit` for targeted changes to an existing form, not a decompile → compile cycle.

## Intentionally outside the DSL - do it in code

The DSL covers the **structure** of the form and the **static** properties of elements (including static `Visible: false`). It intentionally does NOT generate:

- **Conditional formatting / visibility-by-condition** -> implement this in the form module via `УсловноеОформление.Элементы.Добавить()` (`Оформление`/`Отбор`/`ОформляемыеПоля`). This is the recommended path for standard objects.
- **Filters / sorting / parameters of dynamic lists** -> set them programmatically (`Список.КомпоновкаДанных.Отбор`) or in custom list settings.

The absence of these keys is a **design choice**, not a tool defect; see rule `no-manual-xml-edit.md` § "What is done in code, and NOT through xml-gen". (Conditional formatting of a *report* is different: it lives in the Data Composition Schema, so use the `skd` DSL.)

## `--from-object` mode

Generates `Form.xml` from the object's XML description. Coverage: `Catalog` (item/folder/list/choice), `Document` (item/list/choice), `InformationRegister` (record/list), `AccumulationRegister` (list), `ChartOfCharacteristicTypes`, `ExchangePlan`, `ChartOfAccounts`, `DataProcessor`/`Report` (skeleton).

Purpose is determined by the folder name: `ФормаСписка`->list, `ФормаВыбора`->choice, `ФормаГруппы`->folder, `ФормаЗаписи`->record, otherwise item.

The `erp-standard` preset is built in; it can be overridden by the file `<project-root>/presets/skills/form/erp-standard.json`.

Guardrails: `ValueStorage` attributes are skipped; `FormDataStructure/Collection/Tree` in an attribute -> `FromObjectException`.

## DSL structure

Minimal form: `{"attributes": [], "elements": []}`

### Attributes (attributes)

```json
{"name": "ИмяРеквизита", "type": "тип", "title": "Заголовок"}
```

**Types:** `string`, `string(N)`, `number`, `number(D,F)`, `boolean`, `date`, `uuid`, `CatalogRef.Name`, `DocumentRef.Name`, `ValueTable`

**Forbidden runtime types:** `FormDataStructure`, `FormDataCollection`, `FormDataTree` - they do not exist in the XML schema and cause XDTO errors when loading (compiler: `IllegalArgumentException`; validator: `FORM-114 ERROR`). Use `CatalogObject.X` / `DocumentObject.X` / `DataProcessorObject.X`, `ValueTable`, `ValueTree`.

### UI elements (elements)

| DSL type | XML type | Description |
|----------|---------|----------|
| `input` | InputField | Input field |
| `group` | UsualGroup | Group (`"group": "Vertical"/"Horizontal"`, `children`) |
| `columnGroup` | ColumnGroup | Column group |
| `buttonGroup` | ButtonGroup | Button group, `children` |
| `table` | Table | Table (`dataPath`, `columns`) |
| `button` | Button | Button (`commandName`) |
| `label` | LabelDecoration | Label decoration |
| `checkbox` | CheckBoxField | Checkbox field |
| `radio` | RadioButtonField | Radio buttons, `choices` |
| `pages` | Pages | Pages container |
| `page` | Page | Page (only inside `pages`) |
| `picture`, `picField`, `calendar` | PictureDecoration/PictureField/CalendarField | Basic fields/decorations |
| `spreadsheet`, `html`, `textDoc`, `formattedDoc` | document fields | Document fields |
| `progressBar`, `trackBar`, `periodField`, `graphicalSchema` | special fields | Simple special fields without Chart/Gantt/Planner settings |

`input` also supports static `choiceList`, `choiceParameters`, `choiceParameterLinks`, and `typeLink`. Large chart/planner/conditional-appearance settings are intentionally not moved into the DSL: they are simpler and safer to create in form code.

### Commands and events

```json
{"name": "Сохранить", "action": "Save", "title": "Сохранить"}
{"events": {"onCreateAtServer": "ПриСозданииНаСервере", "onOpen": "ПриОткрытии"}}
```

The DSL defines only the procedure name; set the compiler directive in the module manually: `onCreateAtServer` -> `&НаСервере`, `onOpen`/`onClose`/`beforeClose` -> `&НаКлиенте`. Mixing up contexts = compilation error or server objects being unavailable.

UUID, ID, ContextMenu, ExtendedTooltip are created automatically.

## Pitfalls

```json
// ❌ dataPath does not match the attribute -> the element will not display data
{"attributes": [{"name": "Наименование", "type": "string(100)"}],
 "elements": [{"type": "input", "name": "Поле1", "dataPath": "Поле1"}]}

// ✅ dataPath = attribute name (or the path to the tabular section field: Товары.Номенклатура)
{"elements": [{"type": "input", "name": "Наименование", "dataPath": "Наименование"}]}
```

```json
// ❌ page without a pages parent - the platform will not load the form
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
