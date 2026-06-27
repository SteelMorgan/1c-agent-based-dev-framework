---
name: form-dsl
description: "xml-gen managed form DSL: compile/edit"
---

# Form DSL

## Команды

```bash
xml-gen form compile [--format designer|edt] <input.json> <output.xml>

# Генерация формы по метаданным объекта
xml-gen form compile --from-object [--preset erp-standard] [--object <path>] <output.xml>

xml-gen form info <Form.xml>
xml-gen form decompile <Form.xml> [output.json]
```

Редактирование существующих форм (add-attribute, add-element, move-element и др.) — см. [xml-generation](../SKILL.md) §3 Edit-команды

`form decompile` выдаёт **черновой JSON** для scaffold по образцу. Это не lossless round-trip: точечные правки готовой формы делай через `form edit`, а не через цикл decompile → compile.

## Намеренно вне DSL — делать кодом

DSL покрывает **структуру** формы и **статические** свойства элементов (включая статическую `Visible: false`). Намеренно НЕ генерирует:

- **Условное оформление / видимость-по-условию** → реализуй в модуле формы через `УсловноеОформление.Элементы.Добавить()` (`Оформление`/`Отбор`/`ОформляемыеПоля`). Это рекомендуемый путь для типовых объектов.
- **Отборы / сортировку / параметры динамических списков** → задавай программно (`Список.КомпоновкаДанных.Отбор`) или в собственных настройках списка.

Отсутствие этих ключей — **дизайн**, а не дефект инструмента, см. правило `no-manual-xml-edit.md` § «Что делается кодом, а НЕ через xml-gen». (Условное оформление *отчёта* — другое: оно живёт в схеме СКД → используй `skd` DSL.)

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
| `columnGroup` | ColumnGroup | Группа колонок |
| `buttonGroup` | ButtonGroup | Группа кнопок, `children` |
| `table` | Table | Таблица (`dataPath`, `columns`) |
| `button` | Button | Кнопка (`commandName`) |
| `label` | LabelDecoration | Декорация-надпись |
| `checkbox` | CheckBoxField | Поле флажка |
| `radio` | RadioButtonField | Радиокнопки, `choices` |
| `pages` | Pages | Контейнер страниц |
| `page` | Page | Страница (только внутри `pages`) |
| `picture`, `picField`, `calendar` | PictureDecoration/PictureField/CalendarField | Базовые поля/декорации |
| `spreadsheet`, `html`, `textDoc`, `formattedDoc` | document fields | Поля документов |
| `progressBar`, `trackBar`, `periodField`, `graphicalSchema` | special fields | Простые спецполя без Chart/Gantt/Planner settings |

`input` дополнительно поддерживает статические `choiceList`, `choiceParameters`, `choiceParameterLinks`, `typeLink`. Большие настройки диаграмм/планировщика/условного оформления намеренно не переносим в DSL: их проще и безопаснее создавать кодом формы.

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
