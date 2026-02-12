---
name: form-dsl
description: JSON DSL для генерации управляемых форм 1С с UI-элементами, реквизитами и командами
category: 1c-development
tags: [1c, form, ui, dsl]
version: 1.0.0
---

# Form DSL

JSON DSL для генерации управляемых форм 1С.

## Команда

```bash
java -jar xml-gen.jar form compile <input.json> <output.xml>
```

**Параметры:**
- `<input.json>` — JSON DSL файл
- `<output.xml>` — выходной Form.xml

## Структура DSL

### Минимальная форма

```json
{
  "attributes": [],
  "elements": []
}
```

### Полная структура

```json
{
  "attributes": [
    {"name": "Наименование", "type": "string(100)"}
  ],
  "commands": [
    {"name": "Сохранить", "action": "Save"}
  ],
  "events": {
    "onOpen": "ПриОткрытии"
  },
  "elements": [
    {"type": "input", "name": "Наименование", "dataPath": "Наименование"}
  ]
}
```

## Реквизиты (attributes)

### Базовый синтаксис

```json
{
  "name": "ИмяРеквизита",
  "type": "тип",
  "title": "Заголовок"
}
```

### Поддерживаемые типы

| DSL тип | 1С тип | Пример |
|---------|--------|--------|
| `string` | Строка | `"type": "string"` |
| `string(N)` | Строка(N) | `"type": "string(100)"` |
| `number` | Число | `"type": "number"` |
| `number(D,F)` | Число(D,F) | `"type": "number(15,2)"` |
| `boolean` | Булево | `"type": "boolean"` |
| `date` | Дата | `"type": "date"` |
| `uuid` | УникальныйИдентификатор | `"type": "uuid"` |
| `CatalogRef.Name` | СправочникСсылка.Name | `"type": "CatalogRef.Номенклатура"` |
| `DocumentRef.Name` | ДокументСсылка.Name | `"type": "DocumentRef.РеализацияТоваров"` |
| `ValueTable` | ТаблицаЗначений | `"type": "ValueTable"` |

### Примеры реквизитов

```json
{
  "attributes": [
    {"name": "Наименование", "type": "string(100)", "title": "Наименование"},
    {"name": "Количество", "type": "number(15,2)", "title": "Количество"},
    {"name": "Активен", "type": "boolean", "title": "Активен"},
    {"name": "Дата", "type": "date", "title": "Дата"},
    {"name": "Организация", "type": "CatalogRef.Организации", "title": "Организация"},
    {"name": "Товары", "type": "ValueTable", "title": "Товары"}
  ]
}
```

## UI-элементы (elements)

### Поддерживаемые элементы (топ-15)

| Тип | DSL type | Описание |
|-----|----------|----------|
| Поле ввода | `input` | InputField |
| Обычная группа | `group` | UsualGroup |
| Таблица | `table` | Table |
| Кнопка | `button` | Button |
| Декорация-надпись | `label` | LabelDecoration |
| Поле надписи | `labelField` | LabelField |
| Поле флажка | `checkbox` | CheckBoxField |
| Страницы | `pages` | Pages |
| Страница | `page` | Page |
| Декорация-картинка | `picture` | PictureDecoration |
| Поле картинки | `pictureField` | PictureField |
| Поле календаря | `calendar` | CalendarField |
| Командная панель | `commandBar` | CommandBar |
| Всплывающее меню | `popup` | Popup |

### 1. Поле ввода (input)

```json
{
  "type": "input",
  "name": "Наименование",
  "dataPath": "Наименование",
  "title": "Наименование товара"
}
```

**Свойства:**
- `name` — имя элемента (обязательно)
- `dataPath` — путь к данным (обязательно)
- `title` — заголовок
- `readOnly` — только чтение (boolean)
- `visible` — видимость (boolean)

### 2. Обычная группа (group)

```json
{
  "type": "group",
  "name": "ГруппаОсновное",
  "title": "Основное",
  "group": "Vertical",
  "children": [
    {"type": "input", "name": "Наименование", "dataPath": "Наименование"},
    {"type": "input", "name": "Количество", "dataPath": "Количество"}
  ]
}
```

**Свойства:**
- `group` — ориентация: `Vertical`, `Horizontal`, `AlwaysHorizontal`
- `children` — вложенные элементы

### 3. Таблица (table)

```json
{
  "type": "table",
  "name": "ТаблицаТоваров",
  "dataPath": "Товары",
  "columns": [
    {"type": "input", "name": "Номенклатура", "dataPath": "Товары.Номенклатура"},
    {"type": "input", "name": "Количество", "dataPath": "Товары.Количество"},
    {"type": "input", "name": "Цена", "dataPath": "Товары.Цена"}
  ]
}
```

**Свойства:**
- `dataPath` — путь к табличной части
- `columns` — колонки (массив элементов)

### 4. Кнопка (button)

```json
{
  "type": "button",
  "name": "КнопкаСохранить",
  "commandName": "Сохранить",
  "title": "Сохранить"
}
```

**Свойства:**
- `commandName` — имя команды
- `title` — текст кнопки

### 5. Страницы (pages)

```json
{
  "type": "pages",
  "name": "Страницы",
  "children": [
    {
      "type": "page",
      "name": "СтраницаОсновное",
      "title": "Основное",
      "children": [
        {"type": "input", "name": "Наименование", "dataPath": "Наименование"}
      ]
    },
    {
      "type": "page",
      "name": "СтраницаДополнительно",
      "title": "Дополнительно",
      "children": [
        {"type": "input", "name": "Комментарий", "dataPath": "Комментарий"}
      ]
    }
  ]
}
```

### 6. Декорация-надпись (label)

```json
{
  "type": "label",
  "name": "НадписьИнфо",
  "title": "Заполните все обязательные поля"
}
```

### 7. Поле флажка (checkbox)

```json
{
  "type": "checkbox",
  "name": "Активен",
  "dataPath": "Активен",
  "title": "Активен"
}
```

## Команды (commands)

```json
{
  "commands": [
    {
      "name": "Сохранить",
      "action": "Save",
      "title": "Сохранить",
      "picture": "Save"
    },
    {
      "name": "Закрыть",
      "action": "Close",
      "title": "Закрыть"
    }
  ]
}
```

**Свойства:**
- `name` — имя команды (обязательно)
- `action` — действие (обязательно)
- `title` — заголовок
- `picture` — картинка

## События (events)

```json
{
  "events": {
    "onOpen": "ПриОткрытии",
    "onClose": "ПриЗакрытии",
    "beforeClose": "ПередЗакрытием"
  }
}
```

**Поддерживаемые события:**
- `onOpen` — ПриОткрытии
- `onClose` — ПриЗакрытии
- `beforeClose` — ПередЗакрытием
- `notificationProcessing` — ОбработкаОповещения

## Полные примеры

### Пример 1: Простая форма ввода

```json
{
  "attributes": [
    {"name": "Наименование", "type": "string(100)"},
    {"name": "Количество", "type": "number(15,2)"},
    {"name": "Активен", "type": "boolean"}
  ],
  "commands": [
    {"name": "Сохранить", "action": "Save"},
    {"name": "Закрыть", "action": "Close"}
  ],
  "elements": [
    {
      "type": "group",
      "name": "ГруппаОсновное",
      "group": "Vertical",
      "children": [
        {"type": "input", "name": "Наименование", "dataPath": "Наименование"},
        {"type": "input", "name": "Количество", "dataPath": "Количество"},
        {"type": "checkbox", "name": "Активен", "dataPath": "Активен"}
      ]
    },
    {
      "type": "group",
      "name": "ГруппаКнопки",
      "group": "Horizontal",
      "children": [
        {"type": "button", "name": "Сохранить", "commandName": "Сохранить"},
        {"type": "button", "name": "Закрыть", "commandName": "Закрыть"}
      ]
    }
  ]
}
```

### Пример 2: Форма с табличной частью

```json
{
  "attributes": [
    {"name": "Организация", "type": "CatalogRef.Организации"},
    {"name": "Дата", "type": "date"},
    {"name": "Товары", "type": "ValueTable"}
  ],
  "elements": [
    {
      "type": "pages",
      "name": "Страницы",
      "children": [
        {
          "type": "page",
          "name": "СтраницаШапка",
          "title": "Шапка",
          "children": [
            {"type": "input", "name": "Организация", "dataPath": "Организация"},
            {"type": "input", "name": "Дата", "dataPath": "Дата"}
          ]
        },
        {
          "type": "page",
          "name": "СтраницаТовары",
          "title": "Товары",
          "children": [
            {
              "type": "table",
              "name": "ТаблицаТоваров",
              "dataPath": "Товары",
              "columns": [
                {"type": "input", "name": "Номенклатура", "dataPath": "Товары.Номенклатура"},
                {"type": "input", "name": "Количество", "dataPath": "Товары.Количество"},
                {"type": "input", "name": "Цена", "dataPath": "Товары.Цена"}
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

### Пример 3: Форма с вложенными группами

```json
{
  "attributes": [
    {"name": "Наименование", "type": "string(100)"},
    {"name": "Код", "type": "string(20)"},
    {"name": "Артикул", "type": "string(50)"},
    {"name": "Цена", "type": "number(15,2)"}
  ],
  "elements": [
    {
      "type": "group",
      "name": "ГруппаВсе",
      "group": "Vertical",
      "children": [
        {
          "type": "group",
          "name": "ГруппаОсновное",
          "title": "Основное",
          "group": "Vertical",
          "children": [
            {"type": "input", "name": "Наименование", "dataPath": "Наименование"},
            {"type": "input", "name": "Код", "dataPath": "Код"}
          ]
        },
        {
          "type": "group",
          "name": "ГруппаДополнительно",
          "title": "Дополнительно",
          "group": "Vertical",
          "children": [
            {"type": "input", "name": "Артикул", "dataPath": "Артикул"},
            {"type": "input", "name": "Цена", "dataPath": "Цена"}
          ]
        }
      ]
    }
  ]
}
```

## Автоматическая генерация

### Автоматически создаются:

1. **UUID** — для формы и всех элементов
2. **ID** — уникальные идентификаторы элементов
3. **ContextMenu** — контекстное меню для каждого элемента
4. **ExtendedTooltip** — расширенная подсказка для каждого элемента
5. **AutoCommandBar** — командная панель для таблиц

### Пример автогенерации:

**DSL:**
```json
{"type": "input", "name": "Наименование", "dataPath": "Наименование"}
```

**XML:**
```xml
<InputField name="Наименование" id="1">
  <DataPath>Наименование</DataPath>
  <ContextMenu name="НаименованиеКонтекстноеМеню" id="2"/>
  <ExtendedTooltip name="НаименованиеРасширеннаяПодсказка" id="3"/>
</InputField>
```

## Особенности

### Вложенность элементов

Поддерживается произвольная вложенность:
- `group` → `group` → `input`
- `pages` → `page` → `group` → `table`
- `table` → `columns` → `input`

### Свойства элементов

Любые дополнительные свойства передаются как есть:

```json
{
  "type": "input",
  "name": "Наименование",
  "dataPath": "Наименование",
  "readOnly": true,
  "visible": false,
  "width": 20
}
```

Преобразуются в:
```xml
<ReadOnly>true</ReadOnly>
<Visible>false</Visible>
<Width>20</Width>
```

## Ограничения

### Текущие ограничения

1. **Нет событий элементов** — события формы есть, событий элементов нет
2. **Нет параметров формы** — Parameters не генерируются
3. **Нет excludedCommands** — исключённые команды не поддерживаются
4. **Базовые свойства** — не все свойства элементов поддерживаются

### Workaround

- **События элементов** — добавляй вручную в Form.xml
- **Параметры** — добавляй вручную в Form.xml
- **Сложные свойства** — добавляй вручную в Form.xml

## См. также

- [XML Generation](./xml-generation.md) — общее описание модуля
- [EPF Operations](./epf-operations.md) — создание обработок
- [SPEC-002](../../docs/SPEC-002-xml-generation.md) — полная спецификация

## Версия

**Текущая версия:** 1.0.0  
**Статус:** Production Ready  
**Последнее обновление:** 2026-02-12
