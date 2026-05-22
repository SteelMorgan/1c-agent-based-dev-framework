---
name: form-dsl
description: "JSON DSL для генерации управляемых форм 1С с UI-элементами, реквизитами и командами. Используй при form compile и редактировании форм через xml-generation (edit-команды)."
---

# Form DSL

## Команды

```bash
xml-gen form compile [--format designer|edt] <input.json> <output.xml>

# Генерация формы по метаданным объекта
xml-gen form compile --from-object [--preset erp-standard] [--object <path>] <output.xml>

xml-gen form info <Form.xml>
```

Редактирование существующих форм (add-attribute, add-element, move-element и др.) — см. [xml-generation](../SKILL.md) §3 Edit-команды

## Режим `--from-object`

Генерирует `Form.xml` по XML-описанию объекта. Покрытие: `Catalog` (item/folder/list/choice), `Document` (item/list/choice), `InformationRegister` (record/list), `AccumulationRegister` (list), `ChartOfCharacteristicTypes`, `ExchangePlan`, `ChartOfAccounts`, `DataProcessor`/`Report` (заготовка).

Purpose определяется по имени папки: `ФормаСписка`→list, `ФормаВыбора`→choice, `ФормаГруппы`→folder, `ФормаЗаписи`→record, иначе item.

Пресет `erp-standard` встроен; переопределяется файлом `<project-root>/presets/skills/form/erp-standard.json`.

Guardrails: `ValueStorage`-атрибуты скипаются; `FormDataStructure/Collection/Tree` в атрибуте → `FromObjectException`.

## Структура DSL

Минимальная форма: `{"attributes": [], "elements": []}`

### Реквизиты (attributes)

```json
{"name": "ИмяРеквизита", "type": "тип", "title": "Заголовок"}
```

**Типы:** `string`, `string(N)`, `number`, `number(D,F)`, `boolean`, `date`, `uuid`, `CatalogRef.Name`, `DocumentRef.Name`, `ValueTable`

**Запрещённые runtime-типы:** `FormDataStructure`, `FormDataCollection`, `FormDataTree` — не существуют в XML-схеме, вызывают XDTO-ошибку при загрузке (компилятор: `IllegalArgumentException`; валидатор: `FORM-114 ERROR`). Используй `CatalogObject.X` / `DocumentObject.X` / `DataProcessorObject.X`, `ValueTable`, `ValueTree`.

### UI-элементы (elements)

| DSL type | XML тип | Описание |
|----------|---------|----------|
| `input` | InputField | Поле ввода |
| `group` | UsualGroup | Группа (`"group": "Vertical"/"Horizontal"`, `children`) |
| `table` | Table | Таблица (`dataPath`, `columns`) |
| `button` | Button | Кнопка (`commandName`) |
| `label` | LabelDecoration | Декорация-надпись |
| `checkbox` | CheckBoxField | Поле флажка |
| `pages` | Pages | Контейнер страниц |
| `page` | Page | Страница (только внутри `pages`) |

### Команды и события

```json
{"name": "Сохранить", "action": "Save", "title": "Сохранить"}
{"events": {"onCreateAtServer": "ПриСозданииНаСервере", "onOpen": "ПриОткрытии"}}
```

DSL задаёт только имя процедуры; директиву компилятора ставить в модуле вручную: `onCreateAtServer` → `&НаСервере`, `onOpen`/`onClose`/`beforeClose` → `&НаКлиенте`. Перепутать контексты = ошибка компиляции или недоступность серверных объектов.

UUID, ID, ContextMenu, ExtendedTooltip создаются автоматически.

## Ловушки

```json
// ❌ dataPath не совпадает с реквизитом → элемент не отобразит данные
{"attributes": [{"name": "Наименование", "type": "string(100)"}],
 "elements": [{"type": "input", "name": "Поле1", "dataPath": "Поле1"}]}

// ✅ dataPath = name реквизита (или путь к полю ТЧ: Товары.Номенклатура)
{"elements": [{"type": "input", "name": "Наименование", "dataPath": "Наименование"}]}
```

```json
// ❌ page без родителя pages — платформа не загрузит форму
{"elements": [{"type": "page", "name": "Страница1", "children": [...]}]}

// ✅ pages как контейнер
{"elements": [{"type": "pages", "name": "Страницы", "children": [{"type": "page", ...}]}]}
```

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
