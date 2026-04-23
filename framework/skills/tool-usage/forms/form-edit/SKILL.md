---
name: form-edit
description: "Добавление элементов, реквизитов, команд и событий в существующую управляемую форму 1С через xmlgen CLI. Используй когда нужно точечно модифицировать готовую форму."
argument-hint: <FormPath> <JsonPath>
allowed-tools:
  - Bash
  - Read
  - Write
  - Glob
---

# /form-edit — Редактирование формы

Добавляет элементы, реквизиты, команды и события в существующий `Form.xml`. Реализация: Java-CLI `xmlgen form edit` (замена Python-скрипта). Автоматически:

- выделяет ID из трёх независимых пулов (элементы / реквизиты / команды),
- генерирует companion-элементы (ContextMenu, ExtendedTooltip, AutoCommandBar и др.) по типу элемента,
- распознаёт extension-режим при наличии `<BaseForm>` и устанавливает ID-floor 1 000 000,
- дописывает пустые BSL-заглушки обработчиков в `Ext/Form/Module.bsl` с корректной директивой компиляции (`&НаКлиенте`/`&НаСервере`) и сигнатурой параметров.

## Использование

```
/form-edit <FormPath> <JsonPath>
```

## Параметры

| Параметр  | Обязательный | Описание                         |
|-----------|:------------:|----------------------------------|
| FormPath  | да           | Путь к существующему Form.xml    |
| JsonPath  | да           | Путь к JSON со спецификацией добавлений |

## Команда

```bash
xmlgen form edit "<FormPath>" --json "<JsonPath>"
```

## JSON-формат

```json
{
  "elements": [
    {
      "kind": "input",
      "name": "Склад",
      "dataPath": "Объект.Склад",
      "into": "ГруппаШапка",
      "after": "Контрагент",
      "on": [{ "event": "OnChange" }]
    }
  ],
  "attributes": [
    { "name": "СуммаИтого", "type": "decimal(15,2)" }
  ],
  "commands": [
    { "name": "Рассчитать", "action": "РассчитатьОбработка" }
  ]
}
```

### Типы элементов (`kind`)

Можно указывать либо короткий DSL-алиас (`input`), либо прямое XML-имя тега (`InputField`).

| kind | XML-тег | Companions |
|------|---------|------------|
| `input` | InputField | ContextMenu, ExtendedTooltip |
| `check` | CheckBoxField | ContextMenu, ExtendedTooltip |
| `label` | LabelDecoration | ContextMenu, ExtendedTooltip |
| `labelField` | LabelField | ContextMenu, ExtendedTooltip |
| `picField` | PictureField | ContextMenu, ExtendedTooltip |
| `calendar` | CalendarField | ContextMenu, ExtendedTooltip |
| `picture` | PictureDecoration | ContextMenu, ExtendedTooltip |
| `table` | Table | ContextMenu, AutoCommandBar, SearchStringAddition, ViewStatusAddition, SearchControlAddition |
| `button` | Button | ExtendedTooltip |
| `group` | UsualGroup | ExtendedTooltip |
| `pages` | Pages | ExtendedTooltip |
| `page` | Page | ExtendedTooltip |
| `cmdBar` | CommandBar | — |
| `popup` | Popup | — |

Группы и таблицы поддерживают `children` для вложенных элементов.

### Позиционирование

| Ключ | По умолчанию | Описание |
|------|-------------|----------|
| `into` | корневой ChildItems | Имя группы/таблицы/страницы, куда вставлять |
| `after` | в конец | Имя элемента, после которого вставлять |

### Реквизиты — система типов

`string`, `string(100)`, `decimal(15,2)`, `decimal(15,2,nonneg)`, `boolean`, `date`, `dateTime`, `time`, `CatalogRef.X`, `DocumentObject.X`, `ValueTable` (+ `columns:[]`), `ValueTree`, `DynamicList`, `TypeA | TypeB` (составной).

Русские синонимы: `строка(100)`, `число(15,2)`, `дата`, `булево`, `справочникСсылка.X` — распознаются и преобразуются в канонические англ. имена.

Флаги атрибута:
- `"main": true` — помечает реквизит как основной (`<MainAttribute>true</MainAttribute>`).
- `"savedData": true` — сохранение в настройках формы.
- `"columns": [{ "name": "…", "type": "…" }]` — колонки для `ValueTable`/`ValueTree`.

### События (`on`, `formEvents`, `elementEvents`)

Для нового элемента:
```json
{ "kind": "input", "name": "Поле", "on": [{ "event": "OnChange" }] }
```
При отсутствии явного `handler` имя генерируется как `<имя>ПриИзменении`. Пустая BSL-заглушка процедуры автоматически дописывается в `Ext/Form/Module.bsl`, если её там ещё нет.

Для расширений (`<BaseForm>` присутствует в форме) доступен `callType`:
```json
{
  "formEvents": [
    { "name": "OnCreateAtServer", "handler": "Расш_ПриСоздании", "callType": "After" }
  ],
  "elementEvents": [
    { "element": "Банк", "name": "OnChange", "handler": "Расш_БанкПриИзменении", "callType": "Before" }
  ],
  "elements": [
    { "kind": "input", "name": "П", "on": [{ "event": "OnChange", "callType": "After" }] }
  ]
}
```

Явное переопределение handler'а через `handlers`:
```json
{ "kind": "input", "name": "Поле",
  "on": [{ "event": "OnChange" }],
  "handlers": { "OnChange": "МойКастомныйОбработчик" }
}
```

### Кнопки с привязкой к команде

```json
{ "kind": "button", "name": "БтнВыполнить", "command": "Выполнить" }
```
→ `CommandName = Form.Command.Выполнить`.

## Workflow

`/form-info` → создать JSON → `/form-edit` → `/form-validate` → `/form-info`.
