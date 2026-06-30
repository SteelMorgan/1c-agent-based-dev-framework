---
name: query-patterns
description: "When writing new 1C queries and parameters"
alwaysApply: false
---

# 1C Query Patterns

**Key principle:** Every database query is a network round-trip. Minimizing the number of queries and the amount of returned data is the priority.

---

## Rule 1: NEVER execute queries in a loop

For N iterations: N * (network latency + execution time). 1000 elements * 5 ms = 5 seconds just waiting on the network.

ITS standard: "Database queries - restriction on using queries in a loop".

### Correct - one query + Соответствие for processing

```bsl
Запрос = Новый Запрос;
Запрос.Текст =
"ВЫБРАТЬ
|   Номенклатура.Ссылка КАК Ссылка,
|   Номенклатура.Наименование КАК Наименование,
|   Номенклатура.ЕдиницаИзмерения КАК ЕдиницаИзмерения,
|   Номенклатура.СтавкаНДС КАК СтавкаНДС
|ИЗ
|   Справочник.Номенклатура КАК Номенклатура
|ГДЕ
|   Номенклатура.Ссылка В (&МассивНоменклатуры)";

Запрос.УстановитьПараметр("МассивНоменклатуры", МассивНоменклатуры);
Результат = Запрос.Выполнить();
Выборка = Результат.Выбрать();

СоответствиеДанных = Новый Соответствие;
Пока Выборка.Следующий() Цикл
    СоответствиеДанных.Вставить(Выборка.Ссылка,
        Новый Структура("Наименование, ЕдиницаИзмерения, СтавкаНДС",
            Выборка.Наименование, Выборка.ЕдиницаИзмерения, Выборка.СтавкаНДС));
КонецЦикла;

Для Каждого СтрокаТоваров Из Документ.Товары Цикл
    ДанныеНоменклатуры = СоответствиеДанных.Получить(СтрокаТоваров.Номенклатура);
    Если ДанныеНоменклатуры <> Неопределено Тогда
        СтрокаТоваров.ЕдиницаИзмерения = ДанныеНоменклатуры.ЕдиницаИзмерения;
    КонецЕсли;
КонецЦикла;
```

---

## Rule 2: Temporary tables for complex queries

Split into stages: readability, step-by-step debugging, indexing intermediate results.

```bsl
Запрос = Новый Запрос;
Запрос.Текст =
"ВЫБРАТЬ
|   Реализация.Ссылка КАК ДокументСсылка,
|   Реализация.Контрагент КАК Контрагент,
|   Реализация.СуммаДокумента КАК Сумма
|ПОМЕСТИТЬ втРеализации
|ИЗ
|   Документ.РеализацияТоваровУслуг КАК Реализация
|ГДЕ
|   Реализация.Дата МЕЖДУ &ДатаНачала И &ДатаОкончания
|   И Реализация.Проведен
|;
|
|////////////////////////////////////////////////////////////////////////////////
|ВЫБРАТЬ
|   втРеализации.Контрагент КАК Контрагент,
|   СУММА(втРеализации.Сумма) КАК ОбщаяСумма,
|   КОЛИЧЕСТВО(РАЗЛИЧНЫЕ втРеализации.ДокументСсылка) КАК КоличествоДокументов
|ПОМЕСТИТЬ втИтогиПоКонтрагентам
|ИЗ
|   втРеализации
|
|СГРУППИРОВАТЬ ПО
|   втРеализации.Контрагент
|;
|
|////////////////////////////////////////////////////////////////////////////////
|ВЫБРАТЬ
|   втИтоги.Контрагент КАК Контрагент,
|   втИтоги.ОбщаяСумма КАК ОбщаяСумма,
|   втИтоги.КоличествоДокументов КАК КоличествоДокументов,
|   ВзаиморасчетыОстатки.СуммаОстаток КАК Задолженность
|ИЗ
|   втИтогиПоКонтрагентам КАК втИтоги
|       ЛЕВОЕ СОЕДИНЕНИЕ РегистрНакопления.ВзаиморасчетыСКонтрагентами.Остатки КАК ВзаиморасчетыОстатки
|       ПО втИтоги.Контрагент = ВзаиморасчетыОстатки.Контрагент
|
|УПОРЯДОЧИТЬ ПО
|   ОбщаяСумма УБЫВ";
```

Rules: prefix `вт` (standard); `ИНДЕКСИРОВАТЬ ПО` for join fields; use `МенеджерВременныхТаблиц` to manage lifecycle.

---

## Rule 3: Query parameterization - do not inject values into text

Injecting values into text: vulnerability, inability to cache the DBMS execution plan, formatting errors.

```bsl
// Правильно — через параметры
Запрос = Новый Запрос;
Запрос.Текст =
"ВЫБРАТЬ Товары.Наименование
|ИЗ Справочник.Номенклатура КАК Товары
|ГДЕ Товары.ВидНоменклатуры = &ВидНоменклатуры
|   И Товары.Цена >= &МинимальнаяЦена";

Запрос.УстановитьПараметр("ВидНоменклатуры", Перечисления.ВидыНоменклатуры.Товар);
Запрос.УстановитьПараметр("МинимальнаяЦена", 1000);
```

---

## Rule 4: ЕСТЬNULL() with LEFT JOIN

`NULL + 100 = NULL`, `NULL > 0 = FALSE`. An unhandled NULL leads to incorrect calculations and lost rows in conditions.

```bsl
Запрос.Текст =
"ВЫБРАТЬ
|   Номенклатура.Ссылка КАК Номенклатура,
|   ЕСТЬNULL(ОстаткиТоваров.КоличествоОстаток, 0) КАК Остаток,
|   ЕСТЬNULL(ОстаткиТоваров.СуммаОстаток, 0) КАК СуммаОстатка
|ИЗ
|   Справочник.Номенклатура КАК Номенклатура
|       ЛЕВОЕ СОЕДИНЕНИЕ РегистрНакопления.ТоварыНаСкладах.Остатки КАК ОстаткиТоваров
|       ПО Номенклатура.Ссылка = ОстаткиТоваров.Номенклатура
|ГДЕ
|   ЕСТЬNULL(ОстаткиТоваров.КоличествоОстаток, 0) > 0";
```

### Trap - filter without ЕСТЬNULL

```bsl
// ПЛОХО: ГДЕ ОстаткиТоваров.КоличествоОстаток > 0
// → NULL > 0 = FALSE → rows without stock will disappear (LEFT JOIN turns into INNER)
```

---

## Rule 5: Register virtual tables - parameters inside

Virtual tables (`Остатки`, `Обороты`, `СрезПоследних`) are parameterized DBMS functions. Parameters inside mean an optimal plan. Parameters in WHERE mean the DBMS will first calculate **all** data, then filter it. The difference is by orders of magnitude.

ITS standard: "Using virtual tables".

```bsl
// Правильно — параметры внутри виртуальной таблицы
Запрос.Текст =
"ВЫБРАТЬ
|   Остатки.Номенклатура КАК Номенклатура,
|   Остатки.Склад КАК Склад,
|   Остатки.КоличествоОстаток КАК Остаток
|ИЗ
|   РегистрНакопления.ТоварыНаСкладах.Остатки(
|       &ДатаОстатков,
|       Номенклатура В (&СписокНоменклатуры)
|           И Склад = &Склад
|   ) КАК Остатки";
```

### Incorrect — filtering through WHERE

```bsl
// ПЛОХО: СУБД вычислит остатки по ВСЕЙ номенклатуре на ВСЕХ складах, потом отфильтрует
"ИЗ РегистрНакопления.ТоварыНаСкладах.Остатки КАК Остатки
|ГДЕ Остатки.Номенклатура В (&СписокНоменклатуры)"
```

---

## Rule 6: Batch operations — ТаблицаЗначений as a query parameter

Passing an array of data into a temporary table through `Запрос.УстановитьПараметр("ВТ", ТаблицаЗначений)` is one query instead of a loop.

```bsl
ТаблицаДанных = Новый ТаблицаЗначений;
ТаблицаДанных.Колонки.Добавить("Штрихкод", Новый ОписаниеТипов("Строка", , Новый КвалификаторыСтроки(13)));
ТаблицаДанных.Колонки.Добавить("Количество", Новый ОписаниеТипов("Число", Новый КвалификаторыЧисла(15, 3)));

Для Каждого СтрокаИмпорта Из ДанныеИмпорта Цикл
    НоваяСтрока = ТаблицаДанных.Добавить();
    НоваяСтрока.Штрихкод = СтрокаИмпорта.Штрихкод;
    НоваяСтрока.Количество = СтрокаИмпорта.Количество;
КонецЦикла;

Запрос = Новый Запрос;
Запрос.УстановитьПараметр("ДанныеИмпорта", ТаблицаДанных);

Запрос.Текст =
"ВЫБРАТЬ
|   Данные.Штрихкод КАК Штрихкод,
|   Данные.Количество КАК Количество
|ПОМЕСТИТЬ втДанныеИмпорта
|ИЗ
|   &ДанныеИмпорта КАК Данные
|;
|
|////////////////////////////////////////////////////////////////////////////////
|ВЫБРАТЬ
|   Штрихкоды.Номенклатура КАК Номенклатура,
|   втДанные.Количество КАК Количество
|ИЗ
|   втДанныеИмпорта КАК втДанные
|       ВНУТРЕННЕЕ СОЕДИНЕНИЕ РегистрСведений.ШтрихкодыНоменклатуры КАК Штрихкоды
|       ПО втДанные.Штрихкод = Штрихкоды.Штрихкод";
```

---

## Rule 7: Result processing — Выборка vs Выгрузка

| Method | When to use |
|--------|-------------|
| `Выбрать()/Следующий()` | Sequential processing, memory savings |
| `Выбрать(ПоГруппировкам)` | Hierarchical data |
| `Выгрузить()` → ТаблицаЗначений | Random access, search, passing to another procedure, data < 10,000 rows |

```bsl
// Выборка — данные загружаются порциями
Выборка = Запрос.Выполнить().Выбрать();
Пока Выборка.Следующий() Цикл
    // обработка
КонецЦикла;

// Выгрузка — всё в память
ТаблицаРезультат = Запрос.Выполнить().Выгрузить();
НайденнаяСтрока = ТаблицаРезультат.Найти(ИскомаяНоменклатура, "Номенклатура");
```

---

## Rule 8: Indexing — help the optimizer

Index: fields in WHERE conditions, fields in join conditions (ON/ON), sorting fields.

An index on a temporary table is not added "just in case". It is justified when the temporary table is large and later repeatedly participates in joins/selections, or when there is a benchmark/query plan showing a gain. For small one-off temp tables, an index can cost more than the subsequent read.

### Temporary tables — index join fields

```bsl
Запрос.Текст =
"ВЫБРАТЬ
|   ДанныеЗаказов.Номенклатура КАК Номенклатура,
|   ДанныеЗаказов.Количество КАК Количество
|ПОМЕСТИТЬ втЗаказы
|ИЗ
|   &ТаблицаЗаказов КАК ДанныеЗаказов
|
|ИНДЕКСИРОВАТЬ ПО
|   Номенклатура
|;
|
|////////////////////////////////////////////////////////////////////////////////
|ВЫБРАТЬ
|   втЗаказы.Номенклатура,
|   втЗаказы.Количество КАК Заказано,
|   ЕСТЬNULL(Остатки.КоличествоОстаток, 0) КАК НаСкладе
|ИЗ
|   втЗаказы
|       ЛЕВОЕ СОЕДИНЕНИЕ РегистрНакопления.ТоварыНаСкладах.Остатки(,
|           Номенклатура В (ВЫБРАТЬ втЗаказы.Номенклатура ИЗ втЗаказы)) КАК Остатки
|       ПО втЗаказы.Номенклатура = Остатки.Номенклатура";
```

In the configurator/EDT: Attribute -> Properties -> «Index».

---

## Rule 9: Only the needed fields (not SELECT *)

Excess fields: unnecessary traffic, inability to use a covering index, fragility when adding attributes.

```bsl
// Correct — explicit list of fields
"ВЫБРАТЬ
|   Контрагенты.Ссылка,
|   Контрагенты.Наименование,
|   Контрагенты.ИНН
|ИЗ Справочник.Контрагенты КАК Контрагенты"
```

---

## Rule 10: FIRST N to limit the result

A query without a limit can return millions of rows and exhaust memory.

```bsl
"ВЫБРАТЬ ПЕРВЫЕ 100
|   Товары.Наименование, Товары.Код
|ИЗ Справочник.Номенклатура КАК Товары
|ГДЕ Товары.Наименование ПОДОБНО &СтрокаПоиска
|УПОРЯДОЧИТЬ ПО Товары.Наименование"
```

For displaying lists, use dynamic lists — they implement pagination automatically.

---

## Rule 11: Do not mask duplicates with DISTINCT

`DISTINCT` requires sorting/hashing all rows. If duplicates are caused by an unnecessary JOIN, fix the query.

```bsl
// Правильно — подзапрос вместо JOIN + РАЗЛИЧНЫЕ
"ВЫБРАТЬ Контрагенты.Ссылка, Контрагенты.Наименование
|ИЗ Справочник.Контрагенты КАК Контрагенты
|ГДЕ Контрагенты.Ссылка В
|       (ВЫБРАТЬ РАЗЛИЧНЫЕ Реализация.Контрагент
|        ИЗ Документ.РеализацияТоваровУслуг КАК Реализация
|        ГДЕ Реализация.Дата >= &ДатаНачала)"
```

---

## Rule 12: ВЫРАЗИТЬ for composite types

If a field has a composite type (e.g. "Recorder"), the DBMS makes a LEFT JOIN to **all** tables of the composite type. `ВЫРАЗИТЬ(Поле КАК Тип)` limits the JOIN to one table.

ITS standard: "Using the ВЫРАЗИТЬ construct in queries".

```bsl
// Правильно — JOIN только с одной таблицей
"ВЫБРАТЬ
|   Движения.Период,
|   ВЫРАЗИТЬ(Движения.Регистратор КАК Документ.РеализацияТоваровУслуг).Контрагент КАК Контрагент,
|   Движения.Количество
|ИЗ РегистрНакопления.ТоварыНаСкладах КАК Движения
|ГДЕ Движения.Регистратор ССЫЛКА Документ.РеализацияТоваровУслуг"
```

### Trap — access through a composite type without ВЫРАЗИТЬ

```bsl
// ПЛОХО: Движения.Регистратор.Контрагент без ВЫРАЗИТЬ
// Если Регистратор может быть 20 видами документов — 20 LEFT JOIN!
```

---

## Rule 13: ON vs WHERE in LEFT JOIN

- A condition in `ПО` filters the right table **BEFORE** the join - left rows without a match remain with NULL
- A condition in `ГДЕ` filters **AFTER** - rows with NULL are discarded, turning LEFT JOIN into INNER JOIN

```bsl
// Правильно — фильтр правой таблицы в параметрах виртуальной таблицы / в ON
"ВЫБРАТЬ
|   Номенклатура.Наименование,
|   ЕСТЬNULL(Цены.Цена, 0) КАК Цена
|ИЗ Справочник.Номенклатура КАК Номенклатура
|       ЛЕВОЕ СОЕДИНЕНИЕ РегистрСведений.ЦеныНоменклатуры.СрезПоследних(&ДатаЦен,
|           ВидЦены = &ВидЦены) КАК Цены
|       ПО Номенклатура.Ссылка = Цены.Номенклатура"
// Все товары в результате, даже без цены
```

### Trap — filter of the right table in WHERE

```bsl
// ПЛОХО: ГДЕ Цены.ВидЦены = &ВидЦены
// → строки без цены (NULL) отфильтруются — LEFT JOIN стал INNER JOIN
```

---

## Rule 14: Review checklist for query text

These checks do not replace optimization; they catch style/safety defects before performance measurements.

| Check | Rule |
|----------|---------|
| Names | Nested queries, temporary tables, and SKD datasets have meaningful names. Do not leave `ВложенныйЗапрос`, `ВТ1`, `НаборДанных1` in production code. |
| Composite types | For dereferencing a composite-type attribute, use `ВЫРАЗИТЬ(... КАК ...)` and/or `ССЫЛКА` so you do not get implicit JOINs to all possible types. |
| Predefined values | For empty references and predefined values, use `ЗНАЧЕНИЕ(...)` when the value is part of the model rather than an external parameter. |
| Extra fields | Do not select fields that are not used later. |
| String constants | Pass values that change from call to call as parameters, not as string literals in the query text. |
| RLS | Do not remove `РАЗРЕШЕННЫЕ` for performance without an explicit security decision. |
| Calculations | First calculate complex computed fields in `ВЫБРАТЬ`/VT, then filter by the ready-made field if that reduces repeated calculations. |
| `ПО` vs `ГДЕ` | Keep join conditions in `ПО`; keep result filtering conditions in `ГДЕ`. Mix them only with a deliberate reason. |
| Dot dereference | Avoid long dotted chains in `ГДЕ`; move the needed values into VT or use an explicit join. |
| Query construction | Do not build arbitrary query text by concatenation in business logic. Dynamic fragments must be predefined, marked, and constrained by a whitelist of tables/fields. |
| Long query | Move a long query into a separate function that returns only the text. Execution, parameters, and result handling remain separate. |
| Order | First set `Запрос.Текст`, then call `Запрос.УстановитьПараметр(...)`. |
| Empty result | Do not make a separate `РезультатЗапроса.Пустой()` check before a normal traversal if the first `Выборка.Следующий()` is enough. |
| Boolean fields | For a boolean result, use boolean algebra (`Количество > 0 КАК ЕстьОстаток`) rather than `ВЫБОР ... ТОГДА ИСТИНА ИНАЧЕ ЛОЖЬ`. |
| `АВТОНОМЕРЗАПИСИ` | Do not build business logic on the expectation that record autonumbering is guaranteed to start from one and have no gaps. |

## Rule 15: `IN (...)` is not a universal solution

For small parameter lists, `IN (&List)` is fine. For large sets, subqueries, and critical sections, check the variant using a temporary table and an explicit join: this makes it easier to control the plan, indexes, and data volume.

Do not rewrite `IN` mechanically. Change the query shape only when there is an expected gain, readability, or evidence from the plan/measurement.

---
depends_on: []
---
