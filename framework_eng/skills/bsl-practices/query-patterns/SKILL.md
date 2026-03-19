---
name: query-patterns
description: Query patterns for 1C. This skill teaches an agent how to correctly work with the 1C:Предприятие query language.
---

# 1C Query Patterns

**Key principle:** Every database request is a network round-trip. Minimizing the number of requests and the volume of returned data is the priority.

---

## Rule 1: NEVER execute queries inside a loop

For N iterations — N * (network latency + execution time). 1,000 items * 5 ms = 5 seconds just waiting for the network.

ITS standard: “Database queries — restriction on using queries inside a loop.”

### Correct — one query + Correspondence for processing

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

## Rule 2: Use temporary tables for complex queries

Splitting into stages improves readability, step-by-step debugging, and indexing of intermediate results.

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

Rules: use the `вт` prefix (standard); `ИНДЕКСИРОВАТЬ ПО` for join fields; use `МенеджерВременныхТаблиц` to manage the lifecycle.

---

## Rule 3: Parameterize queries — do not inject values into the text

Injecting values into the text leads to vulnerabilities, prevents the DBMS from caching execution plans, and causes formatting errors.

```bsl
// Correct — via parameters
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

## Rule 4: EСTЬNULL() with LEFT JOIN

`NULL + 100 = NULL`, `NULL > 0 = ЛОЖЬ`. Unhandled NULLs lead to incorrect calculations and lost rows in filters.

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

### Pitfall — filter without EСTЬNULL

```bsl
// BAD: ГДЕ ОстаткиТоваров.КоличествоОстаток > 0
// → NULL > 0 = ЛОЖЬ → rows without balances disappear (LEFT JOIN becomes INNER)
```

---

## Rule 5: Virtual register tables — parameters inside

Virtual tables (`Остатки`, `Обороты`, `СрезПоследних`) are parameterized DBMS functions. Parameters inside them yield optimal plans. Parameters in WHERE force the DBMS to compute **all** data first and filter later. The difference is orders of magnitude.

ITS standard: “Use of virtual tables.”

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

### Wrong — filtering via WHERE

```bsl
// BAD: The DBMS calculates balances for ALL nomenclature on ALL warehouses, then filters
"ИЗ РегистрНакопления.ТоварыНаСкладах.Остатки КАК Остатки
|ГДЕ Остатки.Номенклатура В (&СписокНоменклатуры)"
```

---

## Rule 6: Batch operations — ТаблицаЗначений as a query parameter

Passing data arrays through `Запрос.УстановитьПараметр("ВТ", ТаблицаЗначений)` feeds a temporary table — one query instead of a loop.

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

## Rule 7: Handling results — Выборка vs Выгрузка

| Method | When to use |
|--------|-------------|
| `Выбрать()/Следующий()` | Sequential processing, memory savings |
| `Выбрать(ПоГруппировкам)` | Hierarchical data |
| `Выгрузить()` → ТаблицаЗначений | Random access, searches, passing data to another procedure, data < 10,000 rows |

```bsl
// Выборка — data loads in chunks
Выборка = Запрос.Выполнить().Выбрать();
Пока Выборка.Следующий() Цикл
    // processing
КонецЦикла;

// Выгрузка — everything in memory
ТаблицаРезультат = Запрос.Выполнить().Выгрузить();
НайденнаяСтрока = ТаблицаРезультат.Найти(ИскомаяНоменклатура, "Номенклатура");
```

---

## Rule 8: Indexing — help the optimizer

Index fields used in WHERE, join conditions (ON), and ORDER BY.

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

In the configurator/EDT: Property -> Indexing -> “Индексировать”.

---

## Rule 9: Only required fields (not SELECT *)

Extra fields create excess traffic, prevent covering indexes, and increase fragility when new attributes are added.

```bsl
// Correct — explicit field list
"ВЫБРАТЬ
|   Контрагенты.Ссылка,
|   Контрагенты.Наименование,
|   Контрагенты.ИНН
|ИЗ Справочник.Контрагенты КАК Контрагенты"
```

---

## Rule 10: FIRST N to limit the result

A query without limits can return millions of rows and exhaust memory.

```bsl
"ВЫБРАТЬ ПЕРВЫЕ 100
|   Товары.Наименование, Товары.Код
|ИЗ Справочник.Номенклатура КАК Товары
|ГДЕ Товары.Наименование ПОДОБНО &СтрокаПоиска
|УПОРЯДОЧИТЬ ПО Товары.Наименование"
```

Use dynamic lists for display — they implement pagination automatically.

---

## Rule 11: Do not mask duplicates via DISTINCT

`РАЗЛИЧНЫЕ` requires sorting/hashing all rows. If duplicates come from an unnecessary JOIN — fix the query.

```bsl
// Correct — subquery instead of JOIN + РАЗЛИЧНЫЕ
"ВЫБРАТЬ Контрагенты.Ссылка, Контрагенты.Наименование
|ИЗ Справочник.Контрагенты КАК Контрагенты
|ГДЕ Контрагенты.Ссылка В
|       (ВЫБРАТЬ РАЗЛИЧНЫЕ Реализация.Контрагент
|        ИЗ Документ.РеализацияТоваровУслуг КАК Реализация
|        ГДЕ Реализация.Дата >= &ДатаНачала)"
```

---

## Rule 12: EXPR for composite types

If a field has a composite type (e.g., “Регистратор”), the DBMS joins **all** tables of that composite type. `ВЫРАЗИТЬ(Поле КАК Тип)` limits the JOIN to one table.

ITS standard: “Use of the ВЫРАЗИТЬ construction in queries.”

```bsl
// Correct — JOIN only with one table
"ВЫБРАТЬ
|   Движения.Период,
|   ВЫРАЗИТЬ(Движения.Регистратор КАК Документ.РеализацияТоваровУслуг).Контрагент КАК Контрагент,
|   Движения.Количество
|ИЗ РегистрНакопления.ТоварыНаСкладах КАК Движения
|ГДЕ Движения.Регистратор ССЫЛКА Документ.РеализацияТоваровУслуг"
```

### Pitfall — referencing a composite type without EXPR

```bsl
// BAD: Движения.Регистратор.Контрагент without ВЫРАЗИТЬ
// If Регистратор can be 20 types of documents — 20 LEFT JOINs!
```

---

## Rule 13: ON vs WHERE in LEFT JOIN

- A condition in `ПО` filters the right table **BEFORE** the join — rows on the left without a match stay with NULL
- A condition in `ГДЕ` filters **AFTER** — rows with NULL are dropped, turning the LEFT JOIN into INNER JOIN

```bsl
// Correct — filtering the right table in virtual table parameters / ON
"ВЫБРАТЬ
|   Номенклатура.Наименование,
|   ЕСТЬNULL(Цены.Цена, 0) КАК Цена
|ИЗ Справочник.Номенклатура КАК Номенклатура
|       ЛЕВОЕ СОЕДИНЕНИЕ РегистрСведений.ЦеныНоменклатуры.СрезПоследних(&ДатаЦен,
|           ВидЦены = &ВидЦены) КАК Цены
|       ПО Номенклатура.Ссылка = Цены.Номенклатура"
// All products appear in the result, even without a price
```

### Pitfall — filtering the right table in WHERE

```bsl
// BAD: ГДЕ Цены.ВидЦены = &ВидЦены
// → rows without prices (NULL) are filtered out — LEFT JOIN became INNER JOIN
```

---
depends_on: []
---
