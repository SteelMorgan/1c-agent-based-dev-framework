---
name: skd-dsl
description: "Use for генерации схем компоновки данных 1С (СКД) с нуля через JSON DSL: наборы данных, вычисляемые поля, шаблоны вывода, варианты, условное оформление. Helps собрать Schema.xml через xml-gen skd compile/info/validate."
---

# SKD DSL

## Когда применять

| Триггер | Действие |
|---------|----------|
| Создать отчёт (СКД) с нуля | `xml-gen skd compile` с JSON DSL |
| Внешний набор (без запроса) | `dataSets[].objectName` (DataSetObject) |
| Объединение наборов | `dataSets[].items` (DataSetUnion) |
| Вычисляемые поля | `calculatedFields` (shorthand или объект) |
| Шаблоны вывода | `templates` + `groupTemplates` — см. [references/templates-dsl.md](references/templates-dsl.md) |
| Расшифровка ячеек | `parameters[].drilldown` в шаблоне |
| Связи между наборами | `dataSetLinks` |
| Понять чужую СКД | `xml-gen skd info --mode overview` → затем `trace`/`query`/`variant` |
| Проверить корректность | `xml-gen validate --type skd` |
| Точечное редактирование | `skd add-parameter` / `skd add-field` → [xml-generation](../SKILL.md) §3 |

## Команды CLI

```bash
# Компиляция: JSON DSL → Template.xml
xml-gen skd compile [--format designer|edt] <input.json> <output.xml>

# Анализ: Template.xml → компактная сводка (11 режимов)
xml-gen skd info <Template.xml> [--mode <mode>] [--name <name>] [--batch <N>]

# Валидация структуры
xml-gen validate --type skd <Template.xml> [--detailed] [--max-errors 20]
```

`output.xml` — путь к Template.xml макета: `.../Templates/<Name>/Ext/Template.xml`.

## Корневая структура DSL

```json
{
  "dataSets": [...],
  "calculatedFields": [...],
  "totalFields": [...],
  "parameters": [...],
  "templates": [...],
  "groupTemplates": [...],
  "dataSetLinks": [...],
  "settingsVariants": [...]
}
```

Умолчания: `dataSources` → авто `ИсточникДанных1/Local`; `settingsVariants` → авто «Основной» с детальными записями.

## Наборы данных

Тип определяется по ключу: `query` → DataSetQuery, `objectName` → DataSetObject, `items` → DataSetUnion.

**DataSetQuery:** `{ "name": "...", "query": "ВЫБРАТЬ ...", "fields": [...] }`. Запрос — инлайн-строка или файл `"query": "@queries/sales.sql"` (путь относительно JSON, затем CWD).
**DataSetObject:** внешний набор без запроса. Данные передаются через `ПроцессорКомпоновкиДанных.Инициализировать(Макет, Новый Структура("<objectName>", ТЗ), …)`. Поля описываются явно в `fields[]`. `name` — имя набора, `objectName` — ключ в структуре передачи данных.
**DataSetUnion:** `{ "name": "...", "items": [...], "fields": [...] }` — объединение наборов с общими полями.

## Поля — только объектная форма

> **ВНИМАНИЕ:** в текущей реализации CLI shorthand-строки (`"Имя [Заголовок]: тип @роль #ограничение"`) для коллекций `fields`, `calculatedFields`, `totalFields`, `parameters` **НЕ поддерживаются** — Jackson-десериализатор у `SkdDsl$Field`, `SkdDsl$CalculatedField`, `SkdDsl$Parameter`, `SkdDsl$TotalField` не имеет String-конструктора и отвергает строки с ошибкой `Cannot construct instance ... no String-argument constructor`. Используй **только объектную форму** во всех примерах ниже, независимо от того, что показано в shorthand-фрагментах документации. Shorthand-формы оставлены в файле как справочное описание целевой семантики.

Объектная форма поля:
```json
{ "field": "Сумма", "title": "Сумма продажи", "type": "decimal(15,2)",
  "appearance": { "ГоризонтальноеПоложение": "Right", "МинимальнаяШирина": "80" } }
```
`dataPath` берётся из `field`, если не указан явно.

Если нужны роль/ограничения — объектные эквиваленты shorthand-флагов:
```json
{ "field": "Организация", "type": "CatalogRef.Организации", "role": "@dimension" }
{ "field": "Служебное", "type": "string", "restrict": ["noFilter", "noOrder"] }
```

**Заголовок:** многоязычный `"title": { "ru": "...", "en": "..." }`. Поддерживается везде, где принимается title/presentation.

**Типы:** `string`, `string(N)`, `decimal`, `decimal(D,F)`, `boolean`, `date`, `dateTime`. `decimal` без скобок = `decimal(10,2)`. `decimal(N)` = `decimal(N,0)`. Суффикс `,nonneg` → `AllowedSign=Nonnegative`. Алиасы `number`/`число` ≡ `decimal`.

Ссылочные: `CatalogRef.X`, `DocumentRef.X`, `EnumRef.X`, `ChartOfAccountsRef.X`, `StandardPeriod`. Эмитируются с inline-неймспейсом `d5p1:`. Сборка EPF со ссылочными типами требует базы с подходящей конфигурацией.

Составной тип — массив в объектной форме: `"type": ["CatalogRef.А", "CatalogRef.Б"]`. Квалификаторы применяются к каждому элементу.

**Роли:** `@dimension`, `@account`, `@balance`, `@period`.

**Ограничения:** shorthand-флаги `#noField`, `#noFilter`, `#noGroup`, `#noOrder`; объектная форма: `"restrict": ["noField", "noFilter"]`.

**Дополнительно:** `presentationExpression` — выражение представления (значение остаётся для расшифровки). `appearance` — оформление колонки по умолчанию (ключи параметров платформы).

## Вычисляемые поля (calculatedFields)

Shorthand: `"Имя [Заголовок]: тип = Выражение #флаги"` — всё кроме имени опционально.
```json
"calculatedFields": [
  "Маржа = Цена - Закупка",
  "Наценка [Наценка, %]: decimal(10,2) = Маржа / Закупка * 100",
  "Служебное: string = \"\" #noField #noFilter #noGroup #noOrder"
]
```
Объектная форма — когда нужна `appearance` или составные настройки: `{ "name", "title", "expression", "type", "useRestriction" }`.

## Итоги (totalFields)

Shorthand: `"totalFields": ["Количество: Сумма", "Стоимость: Сумма(Кол * Цена)"]`.

С привязкой к группировкам — объектная форма:
```json
{ "dataPath": "Кол", "expression": "Сумма(Кол)", "group": ["Группа1", "Группа1 Иерархия", "ОбщийИтог"] }
```

## Параметры

Shorthand: `"Имя [Заголовок]: тип = значение @флаги"`.

| Флаг | Эффект |
|------|--------|
| `@autoDates` | Для `StandardPeriod` — добавляет производные `НачалоПериода`/`КонецПериода`. Используй `&НачалоПериода`/`&КонецПериода` в запросе. Параметр получает `use=Always`, `denyIncompleteValues=true`. |
| `@valueList` | `valueListAllowed=true` — разрешает список значений. |
| `@hidden` | `availableAsField=false` + исключение из `"dataParameters": "auto"`. |
| `@always` | `use=Always`. |

Объектная форма: `title`, `hidden`, `valueListAllowed`, `availableAsField`, `denyIncompleteValues`, `use: "Always"`, `availableValues[]`.

`"dataParameters": "auto"` в варианте настроек — выводит все не-hidden параметры с `userSettingID`. Параметры без значения по умолчанию отключаются (пользователь включит сам).

## Фильтры

Shorthand: `"Поле оператор значение @флаги"`. Значение `_` = пустое (placeholder).

Операторы: `=`, `<>`, `>`, `>=`, `<`, `<=`, `in`, `notIn`, `contains`, `filled`, `notFilled`, `InHierarchy`.

Флаги: `@off` (use=false), `@user` (userSettingID=auto), `@quickAccess`, `@normal`, `@inaccessible`.

Группы:
```json
{ "group": "Or", "items": [
  { "group": "And", "items": [
    { "field": "Статус", "op": "=", "value": "Активен" },
    { "field": "Сумма",  "op": ">", "value": 1000 }
  ]},
  { "field": "Количество", "op": "filled" }
]}
```

Типы значений: `Перечисление.*` / `Справочник.*` / `ПланСчетов.*` / `Документ.*` → DesignTimeValue (автодетект).

## Связи наборов (dataSetLinks)

```json
"dataSetLinks": [{ "source": "...", "target": "...",
  "items": [{ "sourceExpression": "Организация", "targetExpression": "Организация" }]
}]
```

## Структура варианта (structure)

String shorthand: `"structure": "Организация > Номенклатура > details"` (`>` разделяет уровни, `details`/`детали` = детальные записи).

Объектная форма:
```json
"structure": [{ "name": "...", "groupFields": ["Организация"],
  "selection": ["Организация", "Сумма", "Auto"], "children": [{ "groupFields": [] }] }]
```
`type` по умолчанию `"group"`. Поддерживаются `name`, `selection`, `order`, `filter`, `outputParameters`, рекурсивные `children`, `type: "table"`, `type: "chart"`.

## Варианты настроек (settingsVariants)

```json
"settingsVariants": [{
  "name": "Основной", "title": "Продажи по организациям",
  "settings": {
    "selection": ["Номенклатура", "Количество", "Auto"],
    "filter": ["Организация = _ @off @user"],
    "order": ["Количество desc", "Auto"],
    "outputParameters": { "Заголовок": "Мой отчёт" },
    "dataParameters": ["Период = LastMonth @user"],
    "structure": "Организация > details"
  }
}]
```

`selection`: `"Auto"` = все доступные поля; `{ "folder": "...", "items": [...] }` → `SelectedItemFolder`.

## Условное оформление (conditionalAppearance)

```json
{ "selection": ["Поле1"], "filter": ["Поле1 notFilled"],
  "appearance": { "Текст": "Не указано", "ЦветТекста": "style:XXX" },
  "presentation": "...", "viewMode": "Normal", "userSettingID": "auto" }
```

Значения `appearance`: `style:XXX`/`web:XXX`/`win:XXX` → Color; `true`/`false` → Boolean; `Формат`/`Текст`/`Заголовок` → LocalStringType; прочее → String.

В `settingsVariants.settings` добавляется ключом `"conditionalAppearance": [...]`.

## Шаблоны вывода и группировок

Полная спецификация (синтаксис ячеек, стили, drilldown, groupTemplates) — [references/templates-dsl.md](references/templates-dsl.md).

## Анализ — `xml-gen skd info`

11 режимов. Подробные примеры вывода — [references/info-modes.md](references/info-modes.md).

| Режим | Без `--name` | С `--name` |
|-------|--------------|-------------|
| `overview` (по умолч.) | Карта схемы + подсказки следующих шагов | — |
| `query` | — | Текст запроса набора (с оглавлением батчей) |
| `fields` | Карта полей по наборам | Деталь поля: dataset, тип, роль, формат |
| `links` | Все связи наборов | — |
| `calculated` | Карта вычисляемых полей | Выражение + заголовок + ограничения |
| `resources` | Карта ресурсов (`*` = есть групповые формулы) | Формулы агрегации по группировкам |
| `params` | Таблица параметров (тип, значение, видимость) | — |
| `variant` | Список вариантов | Структура группировок + фильтры + вывод |
| `templates` | Карта привязок шаблонов | Содержимое шаблона: строки, ячейки, выражения |
| `trace` | — | Полная цепочка: набор → вычисление → ресурс |
| `full` | overview + query + fields + resources + params + variant | — |

Workflow: `overview` → `trace --name <поле>` → `query --name <набор>` → `variant --name <N>`. Параметры: `--mode`, `--name`, `--batch` (`0` = все пакеты), `--limit`/`--offset` (по умолч. 150), `--out-file`.

## Пример — с внешним запросом, ресурсами, @autoDates

> Все коллекции — объектная форма (shorthand-строки CLI отвергает, см. предупреждение выше).

```json
{
  "dataSets": [{
    "query": "@queries/sales.sql",
    "fields": [
      {"field": "Организация",  "type": "CatalogRef.Организации", "role": "@dimension"},
      {"field": "Номенклатура", "type": "CatalogRef.Номенклатура", "role": "@dimension"},
      {"field": "Количество",   "type": "decimal(15,3)"},
      {"field": "Сумма",        "type": "decimal(15,2)"}
    ]
  }],
  "totalFields": [
    {"dataPath": "Количество", "expression": "Сумма(Количество)"},
    {"dataPath": "Сумма",      "expression": "Сумма(Сумма)"}
  ],
  "parameters": [
    {"name": "Период", "type": "StandardPeriod", "value": "LastMonth", "autoDates": true}
  ],
  "settingsVariants": [{
    "name": "Основной",
    "settings": {
      "selection": ["Организация", "Номенклатура", "Количество", "Сумма"],
      "filter": ["Организация = _ @off @user"],
      "dataParameters": "auto",
      "structure": "Организация > details"
    }
  }]
}
```

## Антипаттерны

`"filter": ["Сумма больше 0"]` — **неверно**: парсер принимает операторы строго из фиксированного набора (`=`, `<>`, `>`, `>=`, `<`, `<=`, `in`, `notIn`, `contains`, `filled`, `notFilled`, `InHierarchy`). `больше` не распознаётся.

Поля в `selection`/`order`/`filter`/`structure` должны существовать в `dataSets` или `calculatedFields` — иначе СКД не скомпонуется.

**Верификация после compile:** `xml-gen validate --type skd <output.xml>` → `xml-gen skd info <output.xml>` → при нужде `skd info --mode trace --name <поле>`.

## См. также

- [references/templates-dsl.md](references/templates-dsl.md) — шаблоны, drilldown, стили.
- [references/info-modes.md](references/info-modes.md) — 11 режимов `skd info` с примерами вывода.
- [xml-generation](../SKILL.md) — `skd add-parameter`, `skd add-field`, replace-text.
- [mxl-dsl](../mxl-dsl/) — печатные формы.

---
depends_on: []
metadata:
  category: 1c-development
  version: "2.0"
---
