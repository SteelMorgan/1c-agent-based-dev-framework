---
name: form-dsl
description: JSON DSL для генерации управляемых форм 1С с UI-элементами, реквизитами и командами. Используй при form compile и редактировании форм через xml-gen-cli.
---

# Form DSL

## Команды

```bash
xml-gen form compile [--format designer|edt] <input.json> <output.xml>
xml-gen form info <Form.xml>
```

Редактирование существующих форм (add-attribute, add-element, move-element и др.) — см. [xml-gen-cli](../xml-gen-cli/)

## Структура DSL

Минимальная форма: `{"attributes": [], "elements": []}`

### Реквизиты (attributes)

```json
{"name": "ИмяРеквизита", "type": "тип", "title": "Заголовок"}
```

**Типы:** `string`, `string(N)`, `number`, `number(D,F)`, `boolean`, `date`, `uuid`, `CatalogRef.Name`, `DocumentRef.Name`, `ValueTable`

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

### Команды (commands)

```json
{"name": "Сохранить", "action": "Save", "title": "Сохранить"}
```

### События (events)

```json
{"events": {"onCreateAtServer": "ПриСозданииНаСервере", "onOpen": "ПриОткрытии"}}
```

DSL задаёт только имя процедуры. Директиву компилятора ставить в модуле формы вручную:

| Событие DSL | Директива |
|-------------|-----------|
| `onCreateAtServer` | `&НаСервере` |
| `onOpen`, `onClose`, `beforeClose` | `&НаКлиенте` |

Перепутать контексты = ошибка компиляции или недоступность серверных объектов на клиенте.

## Автоматическая генерация

UUID, ID, ContextMenu, ExtendedTooltip создаются автоматически. Произвольная вложенность: group → group → input, pages → page → table.

## Правильно / Неправильно

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
