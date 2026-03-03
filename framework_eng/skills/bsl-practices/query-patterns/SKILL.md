---
name: query-patterns
description: Query patterns for 1C. This skill teaches the agent how to work correctly with the 1C:Enterprise query language.
---

# 1C Query Patterns

## Purpose

This skill teaches the agent how to work correctly with the 1C:Enterprise query language. The 1C query language resembles SQL but has significant differences: virtual tables, automatic joins by reference fields, and hierarchy handling. Incorrect queries are the main cause of performance problems in 1C.

**Key principle:** Every query to the database is a network round-trip (client → application server → DBMS → back). Minimizing the number of queries and the volume of returned data is a top priority.

**Sources:**
- [ITS standards: "Database queries"](https://its.1c.ru/db/v8std)
- [ITS standards: "Query optimization"](https://its.1c.ru/db/metod8dev)
- Methodological recommendations for query optimization (CKTP methodology)

---

## Rule summary

| # | Rule | Rationale |
|---|---------|-------------|
| 1 | No queries inside loops | N round-trips to the DBMS |
| 2 | Temporary tables for complex queries | Readability, debugging, reuse |
| 3 | Query parameterization | Security, plan caching |
| 4 | ЕСТЬNULL() with LEFT JOIN | NULL breaks arithmetic and conditions |
| 5 | Parameters for virtual tables | Optimal query plan |
| 6 | Batch operations via ТаблицаЗначений | One query instead of N |
| 7 | Выборка vs Выгрузка — choose consciously | Memory vs convenience |
| 8 | Indexing join and filter fields | Search speed |
| 9 | Only the needed fields | Traffic, covering indexes |
| 10 | ПЕРВЫЕ N to limit results | Protect against OOM |
| 11 | Don’t hide duplicates with РАЗЛИЧНЫЕ | Fix the cause, not the symptom |
| 12 | ВЫРАЗИТЬ for composite types | Remove unnecessary JOINs |
| 13 | ON vs WHERE in LEFT JOIN | LEFT vs actual INNER |

---

## Rule 1: NEVER execute queries inside a loop

**Why:** Each query execution involves:
1. Serializing parameters
2. Sending over the network to the DBMS server
3. Compiling the query plan (if not cached)
4. Execution
5. Returning the result

For N loop iterations this is N × (network latency + execution time). With 1,000 elements and 5 ms latency, that is **5 seconds** spent waiting for the network alone, even if each query itself runs instantly.

**ITS standard:** "Database queries — restriction on using queries inside loops."

### Correct approach — one query instead of N

```bsl
// Получаем все данные одним запросом
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

// Обрабатываем результат в цикле — запрос уже выполнен
СоответствиеДанных = Новый Соответствие;
Пока Выборка.Следующий() Цикл
    СоответствиеДанных.Вставить(Выборка.Ссылка, 
        Новый Структура("Наименование, ЕдиницаИзмерения, СтавкаНДС",
            Выборка.Наименование, Выборка.ЕдиницаИзмерения, Выборка.СтавкаНДС));
КонецЦикла;

// Используем полученные данные
Для Каждого СтрокаТоваров Из Документ.Товары Цикл
    ДанныеНоменклатуры = СоответствиеДанных.Получить(СтрокаТоваров.Номенклатура);
    Если ДанныеНоменклатуры <> Неопределено Тогда
        СтрокаТоваров.ЕдиницаИзмерения = ДанныеНоменклатуры.ЕдиницаИзмерения;
    КонецЕсли;
КонецЦикла;
```

### Incorrect — query inside a loop

```bsl
// ПЛОХО: N итераций = N запросов к СУБД
Для Каждого СтрокаТоваров Из Документ.Товары Цикл
    
    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ
    |   Номенклатура.ЕдиницаИзмерения
    |ИЗ
    |   Справочник.Номенклатура КАК Номенклатура
    |ГДЕ
    |   Номенклатура.Ссылка = &Номенклатура";
    
    Запрос.УстановитьПараметр("Номенклатура", СтрокаТоваров.Номенклатура);
    Результат = Запрос.Выполнить();
    // ... обработка ...
    
КонецЦикла; // Для 1000 строк — 1000 запросов!
```

---

## Rule 2: Use temporary tables for complex queries

**Why:** Complex queries with multi-level nested subqueries:
1. Are hard to read and debug
2. The DBMS optimizer may pick a suboptimal execution plan
3. You cannot reuse intermediate results

Temporary tables solve all three problems: the query is broken into stages, each stage can be verified separately, and intermediate results can be indexed.

### Correct — batch query with temporary tables

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

Запрос.УстановитьПараметр("ДатаНачала", ДатаНачала);
Запрос.УстановитьПараметр("ДатаОкончания", ДатаОкончания);

Результат = Запрос.Выполнить();
```

### Rules for temporary tables

1. **Prefix `вт`** (temporary table) — a standard convention that improves readability: `втОстаткиТоваров`, `втДокументыКОбработке`
2. **Index** temporary tables when joining them with others: `ПОМЕСТИТЬ втДокументы ... ИНДЕКСИРОВАТЬ ПО Контрагент`
3. **Destroy** temporary tables if the temporary table manager does not clean them up automatically: `МенеджерВТ = Новый МенеджерВременныхТаблиц`

```bsl
// Менеджер временных таблиц — управляет жизненным циклом
МенеджерВТ = Новый МенеджерВременныхТаблиц;
Запрос = Новый Запрос;
Запрос.МенеджерВременныхТаблиц = МенеджерВТ;

// ... выполнение пакета запросов ...

// Явное уничтожение или выход из области видимости переменной МенеджерВТ
```

---

## Rule 3: Parameterize queries — do not substitute values into text

**Why:** Substituting values directly into the query text:
1. Creates a **vulnerability** akin to SQL injection (in 1C context less critical, but still risky for string values)
2. Prevents **plan caching** — the DBMS cannot reuse the plan because the text changes every time
3. Causes **formatting errors** — dates, numbers with separators, strings with quotes are sources of bugs

### Correct

```bsl
Запрос = Новый Запрос;
Запрос.Текст =
"ВЫБРАТЬ
|   Товары.Наименование
|ИЗ
|   Справочник.Номенклатура КАК Товары
|ГДЕ
|   Товары.ВидНоменклатуры = &ВидНоменклатуры
|   И Товары.Цена >= &МинимальнаяЦена";

Запрос.УстановитьПараметр("ВидНоменклатуры", Перечисления.ВидыНоменклатуры.Товар);
Запрос.УстановитьПараметр("МинимальнаяЦена", 1000);

Результат = Запрос.Выполнить();
```

### Incorrect

```bsl
// ПЛОХО: подстановка значений в текст запроса
Запрос = Новый Запрос;
Запрос.Текст =
"ВЫБРАТЬ
|   Товары.Наименование
|ИЗ
|   Справочник.Номенклатура КАК Товары
|ГДЕ
|   Товары.Наименование ПОДОБНО \"%" + СтрокаПоиска + "%\""; // Опасно! Что если СтрокаПоиска содержит кавычки?
```

---

## Rule 4: Handle NULL in LEFT JOIN

**Why:** `ЛЕВОЕ СОЕДИНЕНИЕ` returns NULL for fields from the right table when there is no match. In 1C NULL is a special value: `NULL + 100 = NULL`, `NULL > 0 = ЛОЖЬ`. Unhandled NULL leads to:
1. Incorrect calculations (sum with NULL = NULL)
2. Missing rows in conditions (NULL does not satisfy `> 0`)
3. Errors during further processing in code

### Correct — ЕСТЬNULL()

```bsl
Запрос.Текст =
"ВЫБРАТЬ
|   Номенклатура.Ссылка КАК Номенклатура,
|   Номенклатура.Наименование КАК Наименование,
|   ЕСТЬNULL(ОстаткиТоваров.КоличествоОстаток, 0) КАК Остаток,
|   ЕСТЬNULL(ОстаткиТоваров.СуммаОстаток, 0) КАК СуммаОстатка
|ИЗ
|   Справочник.Номенклатура КАК Номенклатура
|       ЛЕВОЕ СОЕДИНЕНИЕ РегистрНакопления.ТоварыНаСкладах.Остатки КАК ОстаткиТоваров
|       ПО Номенклатура.Ссылка = ОстаткиТоваров.Номенклатура
|ГДЕ
|   ЕСТЬNULL(ОстаткиТоваров.КоличествоОстаток, 0) > 0";
```

### Incorrect

```bsl
// ПЛОХО: без ЕСТЬNULL при LEFT JOIN — числовые поля могут быть NULL
Запрос.Текст =
"ВЫБРАТЬ
|   Номенклатура.Наименование,
|   ОстаткиТоваров.КоличествоОстаток КАК Остаток  // Будет NULL, если нет остатков!
|ИЗ
|   Справочник.Номенклатура КАК Номенклатура
|       ЛЕВОЕ СОЕДИНЕНИЕ РегистрНакопления.ТоварыНаСкладах.Остатки КАК ОстаткиТоваров
|       ПО Номенклатура.Ссылка = ОстаткиТоваров.Номенклатура
|ГДЕ
|   ОстаткиТоваров.КоличествоОстаток > 0";  // NULL > 0 = ЛОЖЬ → строки без остатков пропадут!
```

---

## Rule 5: Pass parameters into virtual tables of registers

**Why:** Virtual tables of registers (`Остатки`, `ОстаткиИОбороты`, `Обороты`, `СрезПоследних`) are not real tables but parameterized DBMS functions. If you set parameters (period, selection) inside the virtual table, the DBMS uses an optimal plan. If you filter through WHERE, the DBMS first computes **all** data and then applies the filter. The difference can be orders of magnitude.

**ITS standard:** "Using virtual tables" — set selection parameters inside virtual tables rather than in the WHERE clause.

### Correct — parameters inside the virtual table

```bsl
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

Запрос.УстановитьПараметр("ДатаОстатков", ДатаОстатков);
Запрос.УстановитьПараметр("СписокНоменклатуры", МассивНоменклатуры);
Запрос.УстановитьПараметр("Склад", Склад);
```

### Incorrect — filtering through WHERE

```bsl
// ПЛОХО: СУБД сначала вычислит остатки по ВСЕЙ номенклатуре на ВСЕХ складах,
// а потом отфильтрует. На большой базе — минуты вместо миллисекунд.
Запрос.Текст =
"ВЫБРАТЬ
|   Остатки.Номенклатура,
|   Остатки.Склад,
|   Остатки.КоличествоОстаток
|ИЗ
|   РегистрНакопления.ТоварыНаСкладах.Остатки КАК Остатки
|ГДЕ
|   Остатки.Номенклатура В (&СписокНоменклатуры)
|   И Остатки.Склад = &Склад";
```

---

## Rule 6: Batch operations — ТаблицаЗначений + query instead of per-item processing

**Why:** When processing an array of data (for example, loading from an external system), passing the data into a temporary table via `Запрос.УстановитьПараметр("ВТ", ТаблицаЗначений)` allows you to handle all data with a single query instead of a loop.

### Correct — pass ТаблицаЗначений as a parameter

```bsl
// Подготавливаем таблицу значений с данными для обработки
ТаблицаДанных = Новый ТаблицаЗначений;
ТаблицаДанных.Колонки.Добавить("Штрихкод", Новый ОписаниеТипов("Строка", , Новый КвалификаторыСтроки(13)));
ТаблицаДанных.Колонки.Добавить("Количество", Новый ОписаниеТипов("Число", Новый КвалификаторыЧисла(15, 3)));

Для Каждого СтрокаИмпорта Из ДанныеИмпорта Цикл
    НоваяСтрока = ТаблицаДанных.Добавить();
    НоваяСтрока.Штрихкод = СтрокаИмпорта.Штрихкод;
    НоваяСтрока.Количество = СтрокаИмпорта.Количество;
КонецЦикла;

// Один запрос обрабатывает все данные через временную таблицу
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

Результат = Запрос.Выполнить();
```

### Incorrect

```bsl
// ПЛОХО: поиск по штрихкоду в цикле — N запросов
Для Каждого СтрокаИмпорта Из ДанныеИмпорта Цикл
    
    Запрос = Новый Запрос;
    Запрос.Текст = "ВЫБРАТЬ Штрихкоды.Номенклатура ИЗ РегистрСведений.ШтрихкодыНоменклатуры КАК Штрихкоды ГДЕ Штрихкоды.Штрихкод = &Штрихкод";
    Запрос.УстановитьПараметр("Штрихкод", СтрокаИмпорта.Штрихкод);
    // ... выполнение для КАЖДОЙ строки ...
    
КонецЦикла;
```

---

## Rule 7: Processing query results — Выборка vs Выгрузка

**Why:** Query results can be processed in two ways: via Выборка (cursor) or by unloading into ТаблицаЗначений. The choice affects memory usage and convenience.

### Выборка (Выбрать/Следующий) — for sequential processing

```bsl
// Выборка экономит память: данные загружаются порциями
Результат = Запрос.Выполнить();
Выборка = Результат.Выбрать();

Пока Выборка.Следующий() Цикл
    // Обработка строки
    Сообщение = СтрШаблон("Товар: %1, Остаток: %2",
        Выборка.Наименование, Выборка.Остаток);
КонецЦикла;
```

### Выборка с группировкой (ВыборкаДетальныеЗаписи) — for hierarchical data

```bsl
// Двухуровневая обработка: группировка по контрагенту, детали — документы
Результат = Запрос.Выполнить();
ВыборкаКонтрагенты = Результат.Выбрать(ОбходРезультатаЗапроса.ПоГруппировкам);

Пока ВыборкаКонтрагенты.Следующий() Цикл
    
    // Уровень группировки — контрагент
    ИтогоПоКонтрагенту = ВыборкаКонтрагенты.Сумма;
    
    ВыборкаДокументы = ВыборкаКонтрагенты.Выбрать();
    Пока ВыборкаДокументы.Следующий() Цикл
        // Детальные записи — документы данного контрагента
        ОбработатьДокумент(ВыборкаДокументы.ДокументСсылка, ВыборкаДокументы.Сумма);
    КонецЦикла;
    
КонецЦикла;
```

### Выгрузка в ТаблицуЗначений — for further manipulations

```bsl
// Выгрузка загружает ВСЕ данные в память. Используйте когда:
// - нужен произвольный доступ к строкам (поиск, сортировка)
// - результат передаётся в другую процедуру
// - данных гарантированно немного (< 10 000 строк)
ТаблицаРезультат = Запрос.Выполнить().Выгрузить();

// Поиск по ТаблицеЗначений
НайденнаяСтрока = ТаблицаРезультат.Найти(ИскомаяНоменклатура, "Номенклатура");
```

---

## Rule 8: Indexing — help the optimizer

**Why:** Indexes in 1C are automatically created for key fields (Ссылка, register dimensions, code, name). But for arbitrary queries with conditions on nonstandard fields there may not be an index, and the DBMS will perform a full table scan. For tables with millions of records, this is catastrophic.

### Where indexes are needed

1. **Fields in WHERE conditions** — if you frequently filter by a field, it should be indexed
2. **Fields in join conditions** — ON uses indexes
3. **Ordering fields** — УПОРЯДОЧИТЬ ПО can use an index

### Temporary tables — index join fields

```bsl
// Индекс на временной таблице — критически важно для JOIN
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

### Configuring indexes in the configurator

In the configurator/EDT: Property → "Индексировать" = "Индексировать" or "Индексировать с дополнительным упорядочиванием".

---

## Rule 9: Do not use ВЫБРАТЬ * (select only the needed fields)

**Why:** The query `ВЫБРАТЬ *` (in 1C — `ВЫБРАТЬ Таблица.*`) returns all fields from the table. Problems:
1. **Excessive traffic** — large fields (ХранилищеЗначения, long strings) are transmitted even if not needed
2. **Fragile code** — when a configuration attribute is added, the query suddenly returns more data, and the handling code can break
3. **Impossible to use a covering index** — the DBMS must read entire rows instead of relying on the index

### Correct

```bsl
// Явно перечисляем только нужные поля
Запрос.Текст =
"ВЫБРАТЬ
|   Контрагенты.Ссылка,
|   Контрагенты.Наименование,
|   Контрагенты.ИНН
|ИЗ
|   Справочник.Контрагенты КАК Контрагенты
|ГДЕ
|   Контрагенты.ЭтоГруппа = ЛОЖЬ";
```

### Incorrect

```bsl
// ПЛОХО: возвращает ВСЕ реквизиты, включая логотип (ХранилищеЗначения), 
// контактную информацию и десятки других полей
Запрос.Текст =
"ВЫБРАТЬ
|   Контрагенты.*
|ИЗ
|   Справочник.Контрагенты КАК Контрагенты";
```

---

## Rule 10: Limit the result and paginate

**Why:** A query without a limit can return millions of rows, exhausting the application server’s memory. For display to the user you typically only need the first 100–1000 records.

### Correct — ПЕРВЫЕ N

```bsl
// Ограничиваем количество возвращаемых строк
Запрос.Текст =
"ВЫБРАТЬ ПЕРВЫЕ 100
|   Товары.Наименование,
|   Товары.Код
|ИЗ
|   Справочник.Номенклатура КАК Товары
|ГДЕ
|   Товары.Наименование ПОДОБНО &СтрокаПоиска
|УПОРЯДОЧИТЬ ПО
|   Товары.Наименование";

Запрос.УстановитьПараметр("СтрокаПоиска", СтрокаПоиска + "%");
```

### Correct — query for dynamic list

```bsl
// Динамические списки автоматически поддерживают пагинацию через платформу.
// Не делайте свою пагинацию для данных, которые отображаются в динамическом списке.
```

---

## Rule 11: Avoid РАЗЛИЧНЫЕ unless necessary

**Why:** `ВЫБРАТЬ РАЗЛИЧНЫЕ` (analogous to `SELECT DISTINCT`) requires sorting or hashing all result rows to eliminate duplicates. If there are no duplicates or they are acceptable, `РАЗЛИЧНЫЕ` wastes resources. If duplicates appear, it often indicates an incorrect JOIN.

### Correct — fix the cause of duplicates

```bsl
// Если дубликаты из-за лишнего JOIN — исправьте запрос, а не маскируйте РАЗЛИЧНЫЕ
Запрос.Текст =
"ВЫБРАТЬ
|   Контрагенты.Ссылка,
|   Контрагенты.Наименование
|ИЗ
|   Справочник.Контрагенты КАК Контрагенты
|ГДЕ
|   Контрагенты.Ссылка В
|       (ВЫБРАТЬ РАЗЛИЧНЫЕ Реализация.Контрагент
|        ИЗ Документ.РеализацияТоваровУслуг КАК Реализация
|        ГДЕ Реализация.Дата >= &ДатаНачала)";
```

### Incorrect

```bsl
// ПЛОХО: РАЗЛИЧНЫЕ маскирует проблему дубликатов из-за лишнего JOIN
Запрос.Текст =
"ВЫБРАТЬ РАЗЛИЧНЫЕ
|   Контрагенты.Наименование
|ИЗ
|   Справочник.Контрагенты КАК Контрагенты
|       СОЕДИНЕНИЕ Документ.РеализацияТоваровУслуг КАК Реализация
|       ПО Контрагенты.Ссылка = Реализация.Контрагент";
// Контрагент с 10 реализациями — 10 строк, потом РАЗЛИЧНЫЕ убирает 9. Лишняя работа.
```

---

## Rule 12: ВЫРАЗИТЬ construct — optimize composite types

**Why:** If a field has a composite type (for example, a register’s "Регистратор" can be any document), the DBMS performs a LEFT JOIN to EVERY table in the composite type. `ВЫРАЗИТЬ(Поле КАК Тип)` limits the joins to the specified type, drastically improving performance.

**ITS standard:** "Using the ВЫРАЗИТЬ construct in queries."

### Correct

```bsl
// ВЫРАЗИТЬ — ограничиваем тип, СУБД делает JOIN только с одной таблицей
Запрос.Текст =
"ВЫБРАТЬ
|   Движения.Период,
|   ВЫРАЗИТЬ(Движения.Регистратор КАК Документ.РеализацияТоваровУслуг).Контрагент КАК Контрагент,
|   Движения.Количество
|ИЗ
|   РегистрНакопления.ТоварыНаСкладах КАК Движения
|ГДЕ
|   Движения.Регистратор ССЫЛКА Документ.РеализацияТоваровУслуг";
```

### Incorrect

```bsl
// ПЛОХО: обращение к реквизиту через составной тип без ВЫРАЗИТЬ
// СУБД сделает LEFT JOIN к КАЖДОМУ типу, входящему в «Регистратор»
Запрос.Текст =
"ВЫБРАТЬ
|   Движения.Период,
|   Движения.Регистратор.Контрагент КАК Контрагент,
|   Движения.Количество
|ИЗ
|   РегистрНакопления.ТоварыНаСкладах КАК Движения";
// Если Регистратор может быть 20 видами документов — это 20 LEFT JOIN!
```

---

## Rule 13: JOIN condition vs WHERE condition in LEFT JOIN

**Why:** In `ЛЕВОЕ СОЕДИНЕНИЕ` the condition in `ГДЕ` and the condition in `ПО` behave differently:
- The condition in `ПО` filters the **right** table before the join — rows from the left table without a match remain with NULL
- The condition in `ГДЕ` filters the **result** after the join — rows with NULL are discarded, turning the LEFT JOIN into an INNER JOIN

### Correct

```bsl
// Фильтр правой таблицы — в условии ON
Запрос.Текст =
"ВЫБРАТЬ
|   Номенклатура.Наименование,
|   ЕСТЬNULL(Цены.Цена, 0) КАК Цена
|ИЗ
|   Справочник.Номенклатура КАК Номенклатура
|       ЛЕВОЕ СОЕДИНЕНИЕ РегистрСведений.ЦеныНоменклатуры.СрезПоследних(&ДатаЦен,
|           ВидЦены = &ВидЦены) КАК Цены
|       ПО Номенклатура.Ссылка = Цены.Номенклатура";
// Все товары будут в результате, даже без цены
```

### Incorrect

```bsl
// ПЛОХО: фильтр по правой таблице в WHERE — LEFT JOIN фактически стал INNER JOIN
Запрос.Текст =
"ВЫБРАТЬ
|   Номенклатура.Наименование,
|   Цены.Цена
|ИЗ
|   Справочник.Номенклатура КАК Номенклатура
|       ЛЕВОЕ СОЕДИНЕНИЕ РегистрСведений.ЦеныНоменклатуры.СрезПоследних КАК Цены
|       ПО Номенклатура.Ссылка = Цены.Номенклатура
|ГДЕ
|   Цены.ВидЦены = &ВидЦены";  // Строки без цены (NULL) — отфильтруются!
```

---

depends_on: []
---
