# SKD: шаблоны вывода (templates DSL)

Компактное табличное описание макетов вывода СКД вместо raw XML.

## Базовая структура

```json
"templates": [
  {
    "name": "Макет1",
    "style": "header",
    "widths": [36, 33, 16, 17],
    "minHeight": 24.75,
    "rows": [
      ["Виды кассы", "Валюта", "Остаток на начало\nпериода", "Остаток на\nконец периода"],
      ["|", "|", "|", "|"],
      ["К1", "К2", "К3", "К4"]
    ]
  },
  {
    "name": "Макет2",
    "style": "data",
    "widths": [36, 33, 16, 17],
    "rows": [["{ВидКассы}", "{Валюта}", "{Остаток}", "{ОстатокКонец}"]],
    "parameters": [
      { "name": "ВидКассы", "expression": "Представление(Счет)" },
      { "name": "Остаток",  "expression": "ОстатокНаНачалоПериода" }
    ]
  }
]
```

## Синтаксис ячеек

| Запись | Смысл |
|--------|-------|
| `"текст"` | Статическая надпись |
| `"{Имя}"` | Параметр (`ExpressionAreaTemplateParameter`) |
| `"\|"` | Объединение с ячейкой выше |
| `">"` | Объединение с ячейкой слева |
| `null` | Пустая ячейка |

Перенос строки в тексте — `\n`.

## Двухуровневая шапка с горизонтальным объединением

```json
"rows": [
  ["Вид актива", "Остаток начало", "Поступление", ">", ">", ">", "Выбытие", ">", ">", "Остаток конец"],
  ["|",          "|",              "из произв.",   "из п/ф", "со сч.40", "прочее", "Реализ.", "отгруж.", "прочее", "|"],
  ["К1",         "К2",             "К3",           "К4",     "К5",       "К6",     "К7",      "К8",      "К9",     "К10"]
]
```

## Встроенные стили

| `style` | Назначение |
|---------|------------|
| `header` | Шапка: фон, центр, перенос |
| `data` | Строки данных: фон группы |
| `subheader` | Подзаголовок: без фона, центр |
| `total` | Итоги: без фона |

Все стили — Arial 10, рамки Solid 1px, цвета через стили платформы.

## Пользовательские стили

Файл `skd-styles.json` ищется в порядке:

1. Рядом с JSON-определением.
2. В текущей директории.
3. В `presets/skills/skd/skd-styles.json` (поиск вверх от `OutputPath`).

Первый найденный побеждает.

Пример (`skd-styles.json`):

```json
{
  "header": {
    "font": { "name": "Arial", "size": 10, "bold": true },
    "background": "style:ФонШапки",
    "horizontalAlign": "Center",
    "verticalAlign": "Center",
    "wrap": true,
    "border": { "style": "Solid", "width": 1 }
  },
  "data": {
    "font": { "name": "Arial", "size": 10 },
    "background": "style:ФонДанных",
    "border": { "style": "Solid", "width": 1 }
  }
}
```

## Расшифровка (drilldown)

Ключ `drilldown` в параметре шаблона автоматически генерирует `DetailsAreaTemplateParameter` и привязку `Расшифровка` в `appearance` ячеек:

```json
"parameters": [
  { "name": "Сырье", "expression": "ПоступлениеСырья", "drilldown": "ПоступлениеСырья" }
]
```

Что эмитируется:

- `ExpressionAreaTemplateParameter` (обычный) — для `{Сырье}`.
- `DetailsAreaTemplateParameter` с именем `Расшифровка_ПоступлениеСырья`, `fieldExpression` по имени ресурса, `mainAction=DrillDown`.
- Все ячейки `{Сырье}` автоматически получают `appearance: { Расшифровка: Расшифровка_ПоступлениеСырья }`.

## Привязка макетов к группировкам (groupTemplates)

```json
"groupTemplates": [
  { "groupName": "ДанныеОтчета", "templateType": "GroupHeader", "template": "Макет1" },
  { "groupField": "Счет",        "templateType": "Header",       "template": "Макет2" },
  { "groupField": "Счет",        "templateType": "OverallHeader","template": "Макет3" }
]
```

| Поле | Что задаёт |
|------|------------|
| `groupField` | Привязка к полю группировки |
| `groupName` | Привязка к именованной группировке в структуре варианта |
| `templateType` | `Header` (строки данных) → `<groupTemplate>`; `OverallHeader` (итоги) → `<groupTemplate>`; `GroupHeader` (шапка) → `<groupHeaderTemplate>` |
| `template` | Имя макета из `templates` |

## Raw XML как fallback

Если в шаблоне есть ключ `template` со строкой XML — используется как есть (raw). Детект: наличие `rows` → DSL, иначе — raw.

```json
{ "name": "СтарыйМакет", "template": "<v8:Template ...>...</v8:Template>" }
```

Полезно для миграции существующих макетов до перехода на DSL.
