---
name: query-patterns
description: "Use when writing new 1C queries and parameters"
alwaysApply: false
---

# 1С Query Patterns

**Key principle:** Every database query is a network round-trip. Minimizing the number of queries and the volume of returned data is the priority.

---

## Rule 1: NEVER run queries in a loop

For N iterations, it is N * (network latency + execution time). 1000 items * 5 ms = 5 seconds just waiting on the network.

ITS standard: “Database queries — restriction on using queries in a loop”.

### Correct — one query + Соответствие for processing

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

Breaking it into stages: readability, step-by-step debugging, indexing intermediate results.

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

Rules: prefix `вт` (standard); `ИНДЕКСИРОВАТЬ ПО` for join fields; use `МенеджерВременныхТаблиц` to manage the lifecycle.

---

## Rule 3: Query parameterization — do not substitute values into the text

Substituting values into the text: vulnerability, inability to cache the DBMS plan, formatting errors.

```bsl
// Correct — through parameters
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

### Trap — filter without ЕСТЬNULL

```bsl
// BAD: WHERE ОстаткиТоваров.КоличествоОстаток > 0
// → NULL > 0 = FALSE → rows without stock will disappear (LEFT JOIN turns into INNER)
```

---

## Rule 5: Register virtual tables — put parameters inside

Virtual tables (`Остатки`, `Обороты`, `СрезПоследних`) are parameterized DBMS functions. Parameters inside mean the optimal plan. Parameters in WHERE mean the DBMS will first compute **all** data, then filter it. The difference is thousands of times.

ITS standard: “Using virtual tables”.

```bsl
// Correct — parameters inside the virtual table
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

### Wrong — filtering through WHERE

```bsl
// BAD: the DBMS will compute stock balances for ALL items on ALL warehouses, then filter
"ИЗ РегистрНакопления.ТоварыНаСкладах.Остатки КАК Остатки
|ГДЕ Остатки.Номенклатура В (&СписокНоменклатуры)"
```

---

## Rule 6: Batch operations — use ТаблицаЗначений as a query parameter

Passing an array of data into a temporary table through `Запрос.УстановитьПараметр("ВТ", ТаблицаЗначений)` means one query instead of a loop.

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

## Rule 7: Result processing — Selection vs Export

| Method | When to use |
|--------|-------------------|
| `Выбрать()/Следующий()` | Sequential processing, memory savings |
| `Выбрать(ПоГруппировкам)` | Hierarchical data |
| `Выгрузить()` → ТаблицаЗначений | Random access, search, passing to another procedure, data < 10,000 rows |

```bsl
// Выборка — data is loaded in chunks
Выборка = Запрос.Выполнить().Выбрать();
Пока Выборка.Следующий() Цикл
    // обработка
КонецЦикла;

// Выгрузка — everything in memory
ТаблицаРезультат = Запрос.Выполнить().Выгрузить();
НайденнаяСтрока = ТаблицаРезультат.Найти(ИскомаяНоменклатура, "Номенклатура");
```

---

## Rule 8: Indexing — help the optimizer

Index: fields in WHERE conditions, fields in join conditions (ОН/ON), sorting fields.

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

In Configurator/EDT: Attribute -> Properties -> "Index".

---

## Rule 9: Only the fields you need (not SELECT *)

Excess fields: extra traffic, no covering index, fragility when attributes are added.

```bsl
// Correct — explicit field list
"ВЫБРАТЬ
|   Контрагенты.Ссылка,
|   Контрагенты.Наименование,
|   Контрагенты.ИНН
|ИЗ Справочник.Контрагенты КАК Контрагенты"
```

---

## Rule 10: TOP N to limit results

A query without a limit can return millions of rows and exhaust memory.

```bsl
"ВЫБРАТЬ ПЕРВЫЕ 100
|   Товары.Наименование, Товары.Код
|ИЗ Справочник.Номенклатура КАК Товары
|ГДЕ Товары.Наименование ПОДОБНО &СтрокаПоиска
|УПОРЯДОЧИТЬ ПО Товары.Наименование"
```

For displaying lists, use dynamic lists - they implement pagination automatically.

---

## Rule 11: Do not mask duplicates with DISTINCT

`РАЗЛИЧНЫЕ` requires sorting/hashing all rows. If duplicates appear because of an unnecessary JOIN, fix the query.

```bsl
// Correct — subquery instead of JOIN + DISTINCT
"ВЫБРАТЬ Контрагенты.Ссылка, Контрагенты.Наименование
|ИЗ Справочник.Контрагенты КАК Контрагенты
|ГДЕ Контрагенты.Ссылка В
|       (ВЫБРАТЬ РАЗЛИЧНЫЕ Реализация.Контрагент
|        ИЗ Документ.РеализацияТоваровУслуг КАК Реализация
|        ГДЕ Реализация.Дата >= &ДатаНачала)"
```

---

## Rule 12: ВЫРАЗИТЬ for composite types

If a field has a composite type (for example, `Регистратор`), the DBMS does a LEFT JOIN to **all** tables in the composite type. `ВЫРАЗИТЬ(Поле КАК Тип)` limits the JOIN to one table.

ITS standard: “Using the ВЫРАЗИТЬ construct in queries”.

```bsl
// Correct — JOIN only with one table
"ВЫБРАТЬ
|   Движения.Период,
|   ВЫРАЗИТЬ(Движения.Регистратор КАК Документ.РеализацияТоваровУслуг).Контрагент КАК Контрагент,
|   Движения.Количество
|ИЗ РегистрНакопления.ТоварыНаСкладах КАК Движения
|ГДЕ Движения.Регистратор ССЫЛКА Документ.РеализацияТоваровУслуг"
```

### Trap — accessing a composite type without ВЫРАЗИТЬ

```bsl
// BAD: Движения.Регистратор.Контрагент without ВЫРАЗИТЬ
// If Регистратор can be 20 document types, that is 20 LEFT JOINs!
```

---

## Rule 13: ON vs WHERE in LEFT JOIN

- Condition in `ПО` filters the right table **before** the join - left rows without a match remain with NULL
- Condition in `ГДЕ` filters **after** - rows with NULL are discarded, turning LEFT JOIN into INNER JOIN

```bsl
// Correct — filter the right table in the virtual table parameters / in ON
"ВЫБРАТЬ
|   Номенклатура.Наименование,
|   ЕСТЬNULL(Цены.Цена, 0) КАК Цена
|ИЗ Справочник.Номенклатура КАК Номенклатура
|       ЛЕВОЕ СОЕДИНЕНИЕ РегистрСведений.ЦеныНоменклатуры.СрезПоследних(&ДатаЦен,
|           ВидЦены = &ВидЦены) КАК Цены
|       ПО Номенклатура.Ссылка = Цены.Номенклатура"
// All items are in the result, even without a price
```

### Trap — filtering the right table in WHERE

```bsl
// BAD: WHERE Цены.ВидЦены = &ВидЦены
// → rows without a price (NULL) will be filtered out - LEFT JOIN became INNER JOIN
```

---
depends_on: []
---
