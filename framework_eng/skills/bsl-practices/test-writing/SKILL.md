---
name: test-writing
description: "For YaxUnit tests, BSL, mocks, and assertions"
---

# Writing YaxUnit Tests (BSL)

Running the written tests is a separate skill [`test-execution`](../../tool-usage/test-execution/SKILL.md).

**Full YaxUnit documentation:** see [`references/yaxunit-cheatsheet.md`](references/yaxunit-cheatsheet.md)

---

## Test location in the project

Tests are stored in a **separate configuration extension**: `<project root>/exts/TESTS/`

### Structure variants: EDT and DESIGNER

| Format | Module code | Metadata file |
|--------|-------------|-----------------|
| EDT | `exts/TESTS/src/CommonModules/<ИмяМодуля>/Module.bsl` | `.../<ИмяМодуля>.mdo` |
| DESIGNER | `exts/TESTS/src/CommonModules/<ИмяМодуля>/Ext/Module.bsl` | `.../<ИмяМодуля>.xml` |

If the format is not obvious, check `application-*.yml` / `yaxunit-*.yml` in the project root.

Do not mix structures: `Ext/` is required for DESIGNER, and it is not used for EDT.

A new module must be registered in `Configuration.[mdo|xml]`, otherwise the runner will not pick up the test.

**Absolute prohibitions:**
- Test source files are placed only in `exts/TESTS/**`, never in the main configuration
- `exts/YAXUNIT/**` is never modified manually — this is runner infrastructure

---

## Naming test modules

Template: `<Prefix>_<ObjectName>[_<Suffix>]`

### Prefixes by object type

| Object type | Prefix | Example |
|-------------|--------|---------|
| Common module | `ОМ_` | `ОМ_ОбщегоНазначения` |
| Document | `Док_` | `Док_ПоступлениеТоваров` |
| Catalog | `Спр_` | `Спр_Контрагенты` |
| Accumulation register | `РН_` | `РН_ОстаткиТоваров` |
| Information register | `РС_` | `РС_КурсыВалют` |
| Data processor | `Обр_` | `Обр_ЗакрытиеМесяца` |

### Suffixes by module type

| Module type | Suffix | Example |
|------------|--------|---------|
| Object module | `_МО` | `Спр_Контрагенты_МО` |
| Manager module | `_ММ` | `РН_ОстаткиТоваров_ММ` |
| Record set module | `_НЗ` | `РБ_Хозрасчетный_НЗ` |

---

## One-off operational YaxUnit modules

Sometimes YaxUnit is used not as a regression test, but as a one-off server-side channel for a manual production operation: fix data, re-post a targeted set of documents, perform a controlled migration. Such a module is NOT a normal test and must not accidentally end up in the “run all tests” mode.

### Required labeling

| What to mark | Convention |
|-----------------|-----------|
| Module name | `Опер_<Описание>[_Номер]` or a project prefix + explicit `_Операция_` fragment; do not disguise it as a normal `_Тест` |
| Module header | First-line comment: `// ONE_OFF_YAXUNIT_OPERATION: НЕ ЗАПУСКАТЬ В ОБЩЕМ ПРОГОНЕ. <назначение>` |
| Suite name | Prefix `[ONE_OFF_OPERATION] <brief purpose>` |
| YaxUnit tags | `.Тег("one-off-operation")` on the suite and, if tests are registered separately, on each operational test |
| Run context | Comment next to registration: who approved the operation, on which base/contour it can be run, how to verify the result, and how to take the module out of the general pass after execution |

### Barrier from the general pass

The marker and tag are navigation, not protection. The operational module MUST have a technical barrier that prevents a normal all-tests run from registering or executing the operation:

1. Preferably, do not keep such a module registered in the shared test extension after the operation is completed: move it into the task artifacts, remove the registration, or disable the module with a separate maintenance task.
2. If the module temporarily remains in the test extension, `ИсполняемыеСценарии()` MUST return without `ДобавитьТестовыйНабор()` unless there is explicit opt-in. Opt-in is set by a separate launch parameter/configuration/wrapper and is documented in the comment next to the registration. A normal “run all tests” does not set this opt-in.
3. Targeted execution of the operational module is allowed only with an explicit filter by module/method and the `one-off-operation` tag, after separate operator confirmation. Execution without a module/method filter is prohibited.
4. After a successful operation, the agent MUST record how the module was removed from the general pass. Leaving an executable production-operation module in the shared all-tests run without an opt-in barrier is prohibited.

```bsl
// ONE_OFF_YAXUNIT_OPERATION: НЕ ЗАПУСКАТЬ В ОБЩЕМ ПРОГОНЕ. Разовая корректировка данных.
Процедура ИсполняемыеСценарии() Экспорт

    Если НЕ РазовыйОперационныйПрогонРазрешён() Тогда
        Возврат;
    КонецЕсли;

    ЮТТесты
        .ДобавитьТестовыйНабор("[ONE_OFF_OPERATION] Корректировка данных")
            .Тег("one-off-operation")
            .ДобавитьСерверныйТест("ВыполнитьКорректировку");

КонецПроцедуры
```

---

## Test Module Structure

Required: the exported procedure `ИсполняемыеСценарии`. Only test registration, no data, no logic.

```bsl
Процедура ИсполняемыеСценарии() Экспорт

    ЮТТесты
        .ДобавитьТестовыйНабор("Остатки")
            .ДобавитьСерверныйТест("ТестПолучитьОстатки")
            .ДобавитьСерверныйТест("ТестОстатокПустойСклад")
        .ДобавитьТестовыйНабор("Перемещение")
            .ДобавитьСерверныйТест("ТестПеремещениеМеждуСкладами");

КонецПроцедуры
```

| Method | Where it runs |
|-------|----------------|
| `ДобавитьТест` | default context |
| `ДобавитьСерверныйТест` | &НаСервереБезКонтекста |
| `ДобавитьКлиентскийТест` | &НаКлиенте |

---

## YaxUnit Requirement for Server-Side Changes

Any change to server logic or server context MUST have a YaxUnit check on the same
runtime layer. This applies to common modules, manager/object modules, server form methods,
queries, register/document writes, background and scheduled handlers, if the effect being checked
is accessible from a server test.

Selection rule:

| Situation | Action |
|----------|--------|
| An existing server method was changed, and a test already exists | Update the test for the new behavior and rerun it. |
| An existing server method was changed, and there is no test | Add a minimal YaxUnit test for the changed contract. |
| A new server method/API was added | Add a YaxUnit test together with the method. |
| The server logic change is visible only through a process | Write a server integration test for the observable effect of the process, or explicitly record why a higher-level scenario is needed. |

Syntax, LSP, and a successful build do NOT replace YaxUnit for server logic: they confirm that the code
can be loaded, but they do not confirm the method contract. If a test is technically impossible, this is
recorded as a blocker/residual risk with the reason; silent omission is prohibited.

---

## Test Implementation

One test verifies one assertion. Arrange-Act-Assert pattern:

```bsl
Процедура ТестПолучитьОстатки() Экспорт

    // Arrange
    Склад = ЮТест.Данные().СоздатьЭлемент("Справочник.Склады");
    НоменклатураСсылка = ЮТест.Данные().СоздатьЭлемент("Справочник.Номенклатура");

    // Act
    Остаток = УправлениеСкладом.ПолучитьОстаток(НоменклатураСсылка, Склад);

    // Assert
    ЮТест.ОжидаетЧто(Остаток).Равно(0);

КонецПроцедуры
```

---

## Assertions (ЮТест.ОжидаетЧто)

```bsl
// Basic comparisons
ЮТест.ОжидаетЧто(Результат).Равно(42);
ЮТест.ОжидаетЧто(Результат).НеРавно(0);
ЮТест.ОжидаетЧто(Результат).Больше(10);
ЮТест.ОжидаетЧто(Флаг).ЭтоИстина();
ЮТест.ОжидаетЧто(Значение).ВСписке(МассивДопустимых);

// Type and completeness
ЮТест.ОжидаетЧто(Ссылка).ИмеетТип("СправочникСсылка.Номенклатура");
ЮТест.ОжидаетЧто(Значение).НеЯвляетсяНеопределено();

// Exceptions
ЮТест.ОжидаетЧто(ЭтотОбъект).МетодВыбрасываетИсключение("МетодСОшибкой", Параметры);

// IB data
ЮТест.ОжидаетЧтоТаблицаБазы("Справочник.Склады")
    .СодержитЗаписи()
    .ГдеРеквизит("Наименование").Равно("Основной склад");
```

---

## Test Data (ЮТест.Данные)

```bsl
// Placeholder
Склад = ЮТест.Данные().СоздатьЭлемент("Справочник.Склады");

// Constructor with attributes
Номенклатура = ЮТест.Данные()
    .КонструкторОбъекта("Справочник.Номенклатура")
    .Установить("Наименование", "Тестовый товар")
    .Установить("ЕдиницаИзмерения", ПредопределенноеЗначение("Справочник.ЕдиницыИзмерения.Штука"))
    .Записать()
    .Ссылка();

// Document
Документ = ЮТест.Данные().СоздатьДокумент("Документ.ПоступлениеТоваров");
```

Data created through `ЮТест.Данные()` **is automatically deleted** after the test. Do not create data in `ИсполняемыеСценарии`.

---

## Rules for Filling Test Data (MUST)

A test object must be valid just like a production one. An incomplete test object either fails on `ПроверитьЗаполнение()`/posting, or is saved semantically incomplete and gives a false-green result (the test passes on data that does not exist in reality).

| Requirement | Rule |
|---|---|
| **Owner for subordinate catalogs** | A catalog subordinate to an owner (the metadata defines `Подчинение`/`Владельцы`) in test data ALWAYS fills `Владелец`. A subordinate item without an owner is semantically invalid; code uniqueness is checked **within the owner scope** (hence the `Код не уникально` collisions); queries and cleanup by owner break on such an item. |
| **All required attributes** | Fill ALL attributes whose metadata has `Проверка заполнения = Выдавать ошибку` (`FillChecking = ShowError`), plus the required standard attributes. |
| **Required standard attributes** | Catalog: `Наименование`/`Код`, if marked with fill checking; subordinate one: `Владелец`. Document: `Дата` (and `Номер`, if there is no auto-numbering). Record set of `РС`: ALL dimensions. |
| **Source of truth is metadata, NOT a neighboring test** | Before creating a test object, check the metadata description (`get_metadata_structure` / configurator): which attributes are `ShowError`, whether there is subordination. Copying the field set from a neighboring test without checking is forbidden - the object may have acquired a new required attribute. |

**Why metadata, not an example:** field requiredness is a property of the object (`Проверка заполнения`), and it changes when the configuration is modified. A test that filled fields "like a neighboring test" silently stops covering the new required attribute - and either fails on posting or writes incomplete data. Checking against the metadata description makes the field set self-updating.

```bsl
// Subordinate catalog: Owner is REQUIRED (Договор is subordinate to Контрагент)
Контрагент = ЮТест.Данные()
    .КонструкторОбъекта("Справочник.Контрагенты")
    .Установить("Наименование", "Тестовый контрагент")  // ShowError attribute
    .Записать()
    .Ссылка();

Договор = ЮТест.Данные()
    .КонструкторОбъекта("Справочник.ДоговорыКонтрагентов")
    .Установить("Владелец", Контрагент)                 // subordination: without an owner it is invalid
    .Установить("Наименование", "Тестовый договор")      // ShowError attribute
    .Записать()
    .Ссылка();
```

---

## Mocking (Mockito)

Pattern: Training -> Execution -> Verification.

```bsl
Процедура ТестРасчётСкидки() Экспорт

    Мокито.Обучение(МодульСкидок)
        .Когда().ПолучитьПроцентСкидки(Клиент)
        .Вернуть(15);

    Результат = УправлениеПродажами.РассчитатьСумму(100, Клиент);

    ЮТест.ОжидаетЧто(Результат).Равно(85);
    Мокито.Проверить(МодульСкидок).ПолучитьПроцентСкидки(Клиент);

КонецПроцедуры
```

### Mocking scenarios

```bsl
Мокито.Обучение(Модуль).Когда().МетодА(Параметр).Вернуть(42);
Мокито.Обучение(Модуль).Когда().МетодБ(Параметр).ВыброситьИсключение("Текст ошибки");
Мокито.Обучение(Модуль).Когда().МетодВ().Пропустить();
Мокито.Обучение(Модуль).Когда().МетодГ().Наблюдать();
```

---

## Lifecycle and event handlers

```bsl
Процедура ИсполняемыеСценарии() Экспорт

    ЮТТесты
        .ДобавитьТестовыйНабор("Расчёты")
            .Перед("ПередНаборомРасчёты")
            .После("ПослеКаждогоТестаОчистка")
            .ДобавитьСерверныйТест("ТестРасчётА")
            .ДобавитьСерверныйТест("ТестРасчётБ");

КонецПроцедуры

Процедура ПередНаборомРасчёты() Экспорт
    ЮТест.Контекст().УстановитьЗначение("Ставка", 18);
КонецПроцедуры
```

### Context - passing data between methods

```bsl
ЮТест.Контекст().УстановитьЗначение("МоёЗначение", Данные);
Данные = ЮТест.Контекст().Значение("МоёЗначение");
```

---

## Parameterized tests

```bsl
Процедура ИсполняемыеСценарии() Экспорт

    Варианты = ЮТест.Варианты()
        .Добавить(0,   "Нулевое количество",  0)
        .Добавить(10,  "Положительное",       100)
        .Добавить(-5,  "Отрицательное",       0);

    ЮТТесты
        .ДобавитьТестовыйНабор("Расчёт суммы")
            .ДобавитьСерверныйТест("ТестРасчётСуммы")
                .СПараметрами(Варианты);

КонецПроцедуры

Процедура ТестРасчётСуммы(Количество, Описание, ОжидаемаяСумма) Экспорт
    Результат = МойМодуль.РассчитатьСумму(Количество);
    ЮТест.ОжидаетЧто(Результат)
        .НазваниеПроверки(Описание)
        .Равно(ОжидаемаяСумма);
КонецПроцедуры
```

---

## Test Data Isolation (MUST)

A test that writes to the database must roll back its changes completely. Without isolation, each run leaves garbage in the database, and tests lose idempotency. The basic `.ВТранзакции()` pattern (called immediately after `ДобавитьТестовыйНабор()`, applied at the **set** level: the runtime looks up the fluent setting by the Test → Set → Module hierarchy), the three exceptions to it, and the reread pattern during reposting are canonical in `references/yaxunit-cheatsheet.md` ("Cheat Sheet: Isolation of Writing Tests"). Here we cover only what is not covered by the cheat sheet: the test object collector, the specifics of the exceptions, and acceptance of isolation by delta.

### Test Object Collector (mandatory teardown mechanism)

> The `test-zero-residue` rule requires: a test that generates data registers EVERY created object in the collector **at the moment of creation**; teardown iterates over the collector and physically deletes everything that survived transaction rollback. This is the main standard cleanup mechanism - NOT a database scan by names/prefixes.

**Why a collector, not YТест.Данные() auto-tracking:** automatic deletion by `YТест.Данные()` happens ONLY if `.УдалениеТестовыхДанных()` is called in the set. Objects created via `КонструкторОбъекта(...).Записать()`, `Документы.X.СоздатьДокумент()`, `Справочники.X.СоздатьЭлемент()` or from helpers are **NOT tracked at all**. The collector covers ALL creation paths uniformly - with exact references, no guesses.

**Collector module contract (shared server module, e.g. `биг_ТестовыйКоллектор`):**
- `Зарегистрировать(Ссылка) Экспорт` - called immediately after EVERY creation (catalog, document, subaccount, owner set, etc.).
- `ОчиститьВсё() Экспорт` - in teardown (`.После()` / final scenario step / `ПослеВсехТестов()`): LIFO traversal (dependents → owners); `Объект = Ссылка.ПолучитьОбъект()`; if `Неопределено` (survived `.ВТранзакции()` rollback or was removed by cascade) → skip; otherwise `Объект.ОбменДанными.Загрузка = Истина; Объект.Удалить();` inside `Попытка` + log. At the end - clear the accumulator.
- **Accumulator storage trap:** a module `Перем` at session level in a shared server module does NOT persist between separate `НаСервереБезКонтекста` calls that YaxUnit uses to run each test. Store the accumulator in `ХранилищеОбщихНастроек` (or an equivalent that survives calls), not in `Перем`.
- **Reverse traversal trap (LIFO):** a `Для` loop in 1C counts ONLY upward - there is no downward step. `Для Сч = Накопитель.ВГраница() По 0 Цикл` does NOT execute the body AT ALL (the condition `ВГраница() <= 0` is false immediately for a non-empty accumulator) - this is a SILENT no-op: the test is green, the log is clean, and residue accumulates (GBIG PAM precedent: `удалено=0` with 40 in the accumulator). Perform reverse traversal ONLY through `Пока` with manual decrement BEFORE any `Продолжить`.

```bsl
// создание — сразу регистрируем
Портфель = ЮТест.Данные().СоздатьЭлемент("Справочник.биг_Портфели").Установить(...).Объект().Ссылка;
биг_ТестовыйКоллектор.Зарегистрировать(Портфель);
...
// teardown — один вызов на весь накопитель
Процедура ПослеВсехТестов() Экспорт
    биг_ТестовыйКоллектор.ОчиститьВсё();
КонецПроцедуры
```

**Canonical reverse traversal in `ОчиститьВсё()` (LIFO: dependents before owners):**

```bsl
Процедура ОчиститьВсё() Экспорт
    Накопитель = ПрочитатьНакопитель();

    // ВАЖНО: `Пока` с декрементом, а НЕ `Для ... По 0` (тот не исполнится — см. ловушку выше).
    Сч = Накопитель.ВГраница();
    Пока Сч >= 0 Цикл
        Ссылка = Накопитель[Сч];
        Сч = Сч - 1;                       // decrement BEFORE `Продолжить`, otherwise infinite loop
        Если НЕ ЗначениеЗаполнено(Ссылка) Тогда
            Продолжить;
        КонецЕсли;
        Попытка
            Объект = Ссылка.ПолучитьОбъект();
            Если Объект <> Неопределено Тогда   // Неопределено = survived rollback / removed by cascade -> normal
                Объект.ОбменДанными.Загрузка = Истина;  // bypass FillChecking/posting during physical deletion
                Объект.Удалить();
            КонецЕсли;
        Исключение
            ЗаписьЖурналаРегистрации("ТестовыйКоллектор", УровеньЖурналаРегистрации.Предупреждение,
                , Ссылка, ОписаниеОшибки());   // leak is visible in the event log, but teardown does not fail
        КонецПопытки;
    КонецЦикла;

    Сбросить();   // clear accumulator -> next module starts empty
КонецПроцедуры
```

**Collector acceptance:** not a "green test," but DELTA-0 - the counters of affected catalogs/documents/registers before and after the run are equal. A green test with broken teardown is a typical masking pattern (loop no-op above). Verify the delta with a `КОЛИЧЕСТВО(*)` query before/after, and load BSL changes through full-rebuild (dynamic build is a no-op on BSL, residue from the previous run creates a false picture).

### Catalogs - create traceably + register in collector

Create catalog items through `ЮТест.Данные().СоздатьЭлемент(...)` or `КонструкторОбъекта(...).Записать()` and **register them in the collector immediately**. Important: `КонструкторОбъекта(...).Записать()` and a direct `Справочники.X.СоздатьЭлемент()` are NOT tracked by YaxUnit (they remain in the database) - for them, the collector is mandatory; even `ЮТест.Данные()` without `.УдалениеТестовыхДанных()` does not self-delete. A direct `Справочники.X.СоздатьЭлемент()` outside the collector is an antipattern.

### Documents via `СоздатьДокумент()` - mandatory teardown

`ЮТест.Данные().СоздатьДокумент(...)` is tracked and deleted automatically. But if a document is created directly through `Документы.X.СоздатьДокумент()` - it is NOT tracked, and an explicit teardown in `.После("ИмяПроцедурыОчистки")` is required.

### Exceptions from `.ВТранзакции()` - specifics

Code templates for the three exceptions and the pattern for rereading the object during re-posting (including the platform 8.3.27 limitation, `[ОшибкаХранимыхДанных]`) are in `references/yaxunit-cheatsheet.md`. For each exception, a rationale comment for the set and teardown via `.После()` is mandatory; below is the reason for the exception beyond what is in the cheat sheet:

| Situation | Reason for exception | Isolation method |
|---|---|---|
| **(a) Negative posting test** (expected `Отказ`) | A failed nested transaction poisons the outer one: "Errors have already occurred in this transaction!" on subsequent reads | `.УдалениеТестовыхДанных()` + `.После("Очистка")` |
| **(b) Prod code with `ТранзакцияАктивна()` guard** | Two-phase commits, real API calls, registers with a unique key - fail or behave unpredictably inside a transaction | `.После("Очистка")` with manual cleanup |
| **(c) Client context** | `ДобавитьКлиентскийТест` - transactional rollback on the client is unavailable due to the platform architecture | `Перед`/`После` handlers with server context |

### Self-cleanup checklist — query-based verification (MUST after writing/editing write tests)

Isolation is declared in code (`.ВТранзакции()`, teardown) and **proven by facts** — database counters before/after the run. A green run does NOT prove cleanliness: a test can pass and still leave garbage behind.

1. **Table map.** Make a list of the tables that the module tests may write to: everything created by `Перед` handlers and test bodies (catalogs, documents, registers), including context helper objects (`СоздатьКонтекстТеста` and so on).
2. **Counters BEFORE.** For each table, run `ВЫБРАТЬ КОЛИЧЕСТВО(*) ИЗ Справочник.X` (platform query: MCP `execute_query` / query console). For information registers, count by the test marker dimension, not the whole table.
3. **Run + counters AFTER.** Run the whole module and take the same counters. The delta for each table must be 0.
4. **Second run.** Repeat: GREEN + delta again 0. The first run may have “eaten” someone else’s accumulated garbage and masked its own, so repeatability is mandatory.
5. **Delta != 0 -> culprit.** Find the remaining objects by names/markers (`ВЫБРАТЬ Наименование ... ГДЕ Наименование ПОДОБНО "...%"`), identify the creating handler/test, add teardown, repeat the checklist from step 2.

**Pitfalls (precedent TASK-173 / TASK-165.7):**

| Pitfall | Essence |
|---|---|
| YaxUnit auto-delete fails on `.Записать(Ложь, Истина)` | `УстановитьПометкуУдаления` revalidates required attributes and refuses - the object remains. Teardown is physical deletion: `Объект.ОбменДанными.Загрузка = Истина; Объект.Удалить();` |
| Partial teardown | `После` handler cleans up only part of what was created (for example, the semaphore register, but not the account catalog) - it looks like teardown, but it leaves garbage |
| “GREEN = clean” | 41/41 tests are green, while one module was leaving +15 objects per run - discovered only by counters |

---

## Anti-patterns

| Anti-pattern | Correct |
|-------------|-----------|
| Data in `ИсполняемыеСценарии` | Data in the body of the test or the `Перед` handler |
| One test checks 10 conditions | One test — one assertion |
| Test depends on execution order | Each test is isolated |
| Hardcoded references to IB objects | Create through `ЮТест.Данные()` |
| Test private logic | Test through the public interface |
| Mock the module under test | Mock only *dependencies* |
| Writing suite without `.ВТранзакции()` | `.ВТранзакции()` by default; exceptions — with a comment + teardown |
| `Справочники.X.СоздатьЭлемент()` in the test | `ЮТест.Данные().СоздатьЭлемент()` + registration in the collector |
| `.ВТранзакции()` on a negative posting test | Exception (a) poisons the transaction; use `.УдалениеТестовыхДанных()` + `.После()` |
| Created an object, did not register it in the collector | Every creation → `Коллектор.Зарегистрировать(Ссылка)` immediately; teardown → `Коллектор.ОчиститьВсё()` |
| Teardown by iterating the database by name/prefix/regexp (`ПОДОБНО "Тест%"`) | Brittle (false-positive wide will wipe production, false-negative narrow will leave residue). Only a one-off sweeper of historical garbage; standard teardown — collector (exact references) |
| `GREEN = clean` — acceptance without counters | Self-cleanup checklist: counters before/after via queries, two runs, delta 0 |

---

## TDD Layers and Phases, Role Boundaries

Orchestration of phases and the Red→Green cycle is the rule of `tdd-policy` (`framework/rules/tdd-policy/SKILL.md`). There also live (not duplicated here) the **rule for a failing test in Tester** (`test_error` → Tester fixes it themself; `implementation_error` → STOP, return to Developer) and the requirement for **User/Role context in the Test Plan** (for code with `SetPrivilegedMode` / `AccessRight` / `RoleAvailable`, the spec must specify the user, mode, and expected result - otherwise the full-rights runner produces a false positive). Below are only the layers and role boundaries specific to writing tests.

Tests and implementation are written by **different agents** in **different phases**: the test author does not know the implementation, and the code author does not modify tests. Phases 3a and 3b are **parallel**; 3c starts after both are finished.

| Layer | Phase | Agent | Covers |
|------|------|-------|-----------|
| BDD (acceptance) | 3a | Scenario-Author | Behavior through the UI (`.feature`) |
| TDD (unit, Red) | 3b | Developer-Tests | Public methods, MUST scenarios, basic negatives |
| TDD (green) | 3c | Developer-Code | Implementation that passes unit tests |
| Coverage | 4 | Tester | Edge cases, integration, regression, BDD + unit |

Agent boundaries:
- **Scenario-Author:** does NOT write unit tests, does NOT run scenarios, does NOT go beyond the specification.
- **Developer-Tests:** MUST scenarios + basic negatives; does NOT cover combinatorial edge cases or integration.
- **Tester:** extends coverage; does NOT duplicate Developer tests; does NOT edit BSL code.

---
depends_on:
  - framework/rules/tdd-policy/SKILL.md
---
