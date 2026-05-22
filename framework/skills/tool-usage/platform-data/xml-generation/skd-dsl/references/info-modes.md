# SKD info — 11 режимов анализа

Полная справка по `xml-gen skd info --mode <mode>`. Компактное описание — в [SKILL.md](../SKILL.md).

Паттерн: без `--name` — карта/индекс; с `--name` — деталь конкретного элемента.

## overview (по умолчанию) — карта схемы

Компактная навигационная карта (10–25 строк) со структурой и подсказками следующих шагов.

```
=== DCS: ОсновнаяСхемаКомпоновкиДанных (362 lines) ===

Sources: ИсточникДанных1 (Local)

Datasets:
  [Query]  НоменклатураСЦенами   7 fields, query 40 lines
Calculated: 1
Resources: 1
Templates: 1 templates, 1 group bindings
Params: (none)

Variants:
  [1] НоменклатураИЦены   "Номенклатура и цены"   Table(detail)  3 filters
  [2] НоменклатураБезЦен  "Номенклатура без цен"  Group(detail)  2 filters

Next:
  --mode query              query text
  --mode fields             field tables by dataset
  --mode calculated         calculated field expressions
  --mode resources          resource aggregation
  --mode variant --name <N> variant structure (1..2)
```

Для `DataSetUnion` — дерево наборов + связи:

```
Datasets:
  [Union] РасчетНалогаНаИмущество  52 fields
    ├─ [Query] РасчетНалогаНаИмущество   51 fields, query 181 lines
    ├─ [Query] ДанныеПоКадастровой       29 fields, query 40 lines
    ├─ [Query] ДанныеПоСреднегодовой     34 fields, query 41 lines
Links: РасчетНалогаНаИмущество -> СостояниеОС (2 fields)
```

Параметры разделяются на видимые/скрытые:

```
Params: 18 (7 visible, 11 hidden): Период, Ответственный, ...
```

## query — текст запроса набора

`--name <набор>` обязателен, если наборов > 1.

Извлекает raw-текст с деэкранированием XML (`&amp;` → `&`, `&gt;` → `>`). Для пакетных запросов — оглавление батчей:

```
=== Query: ДанныеТ13 (334 lines, 13 batches) ===
  Batch 1: lines 1-8     → ПОМЕСТИТЬ Представления_Периоды
  Batch 2: lines 9-26    → ПОМЕСТИТЬ Представления_СотрудникиОрганизации
  ...
--- Batch 1 ---
ВЫБРАТЬ
  ДАТАВРЕМЯ(1, 1, 1) КАК Период
ПОМЕСТИТЬ Представления_Периоды
...
```

`--batch 3` — показать только третий пакет.

## fields — поля наборов

**Без `--name`** — карта имён по наборам:

```
=== Fields map ===
СостояниеОС [Query] (3): Организация, ОсновноеСредство, ДатаСостояния
РасчетНалогаНаИмущество [Union] (52): ДоляСтоимостиЧислитель, ...
  РасчетНалогаНаИмущество [Query] (51): КадастроваяСтоимость, ...
```

**С `--name <поле>`** — детали:

```
=== Field: ДатаСостояния "Дата ввода в эксплуатацию" ===

Dataset: СостояниеОС [Query]
Format: ДФ=dd.MM.yyyy
```

Показывает: dataset, title, type, role, useRestriction, format, presentationExpression.

## links — связи наборов

```
=== Links (4) ===

РасчетНалогаНаИмущество -> СостояниеОС :
  Организация       -> Организация
  ОсновноеСредство  -> ОсновноеСредство
```

Группировка по парам наборов. Показывает поля связи и параметры.

## calculated — вычисляемые поля

**Без `--name`** — карта:

```
=== Calculated fields (23) ===
  ДоляСтоимости  "Доля стоимости"
  КоэффициентКи  "Коэффициент Ки"
  ...
```

**С `--name <поле>`** — полное выражение:

```
=== Calculated: ДоляСтоимости ===

Expression:
  ВЫБОР КОГДА ... ТОГДА "1" ИНАЧЕ ... КОНЕЦ
Title: Доля стоимости
Restrict: condition
```

## resources — ресурсы (итоги по группировкам)

**Без `--name`** — карта (`*` = есть группповые формулы):

```
=== Resources (51) ===
  НалоговаяБаза
  КоэффициентКи *
  ...
  * = has group-level formulas
```

**С `--name <поле>`** — формулы агрегации:

```
=== Resource: ДатаСостояния ===

  [ОсновноеСредство] ЕстьNull(ДатаСостояния, "")
```

## params — параметры схемы

```
=== Parameters (16) ===
  Name              Type                    Default      Visible  Expression
  Период            StandardPeriod          LastMonth    yes      -
  НачалоПериода     DateTime                -            hidden   &Период.ДатаНачала
  Организация       CatalogRef.Организации  null         yes      -
```

## variant — варианты отчёта

**Без `--name`** — список:

```
=== Variants (2) ===
  [1] НоменклатураИЦены   "Номенклатура и цены"   Table(detail)  3 filters
  [2] НоменклатураБезЦен  "Номенклатура без цен"  Group(detail)  2 filters
```

**С `--name <N|имя>`** — структура варианта:

```
=== Variant [1]: НоменклатураИЦены "Номенклатура и цены" ===

Structure:
  Table "Таблица"
  ├── Columns: [ТипЦен Items]
  │     Selection: Auto, Цена
  └── Rows: [Номенклатура Items]
        Selection: Номенклатура, УИД, Auto

Filter:
  [ ] Номенклатура InHierarchy  [user]
  [ ] ТипЦен Equal
  [x] ВАрхиве = false  "Исключая скрытые товары"

DataParams: КлючВарианта="НоменклатураИЦены"
Output: style=ЧерноБелый  groups=Separately  totalsH=None  totalsV=None
```

## templates — привязки шаблонов вывода

Три типа: `fieldTemplate` (к полю), `groupTemplate` (к группировке: Header/Footer), `groupHeaderTemplate` (заголовок группы).

**Без `--name`** — карта привязок:

```
=== Templates (70 defined: 49 field, 37 group) ===

Field bindings (49): (all trivial)
  ОстаточнаяСтоимостьНа0101, ОстаточнаяСтоимостьНа0102, ...

Group bindings (37):
  ВидНалоговойБазы
    Header -> Макет3 (1 rows, 1 params)
  СреднегодоваяСтоимость2019
    Footer      -> Макет50 (1 rows) spacer
    GroupHeader -> Макет40 (3 rows)
```

**С `--name <группировка|поле>`** — содержимое:

```
=== Templates: СреднегодоваяСтоимость2019 ===

Footer -> Макет50 [1 rows, 1 cells]:
  Row 1: (empty)

GroupHeader -> Макет40 [3 rows, 78 cells]:
  Row 1: "№ п/п" | "###Группировки1###" | "Инв. номер" | ...
  Row 2: "01.01" | "01.02" | ... | "31.12"
  Row 3: "1"     | "2"     | ... | "26"
```

Для field-привязок:

```
=== Field template: ОстаточнаяСтоимостьНа0101 -> Макет4 ===
[1 rows, 1 cells]
  Row 1: {ОстаточнаяСтоимостьНа0101}
  (all params trivial)
```

**Тривиальность выражений**: `Поле = Поле` и `Поле = Представление(Поле)` — тривиальны и НЕ выводятся. Показываются только нетривиальные.

## trace — трассировка поля от заголовка до запроса

Ищет поле по dataPath ИЛИ заголовку (включая подстроку) и показывает полную цепочку происхождения за один вызов.

```
=== Trace: КоэффициентКи "Коэффициент Ки" ===

Dataset: (schema-level only, not in dataset fields)

Calculated:
  ВЫБОР КОГДА ... ТОГДА 0 ИНАЧЕ ... КОНЕЦ
  Operands:
    КоличествоМесяцевИспользования -> РасчетНалогаНаИмущество [Query]
    КоличествоМесяцевВладения      -> РасчетНалогаНаИмущество [Query]

Resource:
  [ОсновноеСредство] Сумма(КоэффициентКи)
```

Типичный сценарий: пользователь видит колонку «Коэффициент Ки» в отчёте и спрашивает, как она считается. Один `trace` показывает: формулу, источники операндов, агрегацию в ресурс.

Самый ценный режим для аналитика и архитектора.

## full — комбинированная сводка

`overview + query + fields + resources + params + variant` в одном вызове. Полезно для генерации полного снимка СКД (например, для контекста LLM-агента).

## Что не выводится

- XML namespace-декларации.
- Обёртки `v8:item` / `v8:lang` / `v8:content` — извлекается чистый текст.
- `userSettingID` (GUID пользовательских настроек).
- Дефолтные `periodAdditionBegin` / `periodAdditionEnd` = `0001-01-01`.
- `viewMode`, если значение по умолчанию.

## Целевые subagent'ы

| Subagent | Типичные режимы |
|----------|-----------------|
| `analyst` | `overview`, `trace`, `variant`, `params` |
| `architect` | `trace` (главное), `links`, `full` |
| `developer-code` | `query`, `fields`, `calculated`, `templates` |
| `reviewer` | `validate` + `full` / `trace` для подозрительных полей |
| `explorer` | `overview` → `query` → `variant` |
