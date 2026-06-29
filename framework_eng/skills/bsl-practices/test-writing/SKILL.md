---
name: test-writing
description: "For YaxUnit tests, BSL, mocks, and assertions"
---

# Writing YaxUnit Tests (BSL)

Running written tests is a separate skill [`test-execution`](../../tool-usage/test-execution/SKILL.md).

**Full YaxUnit documentation:** see [`references/yaxunit-cheatsheet.md`](references/yaxunit-cheatsheet.md)

---

## Test Location in the Project

Tests are stored in a **separate configuration extension**: `<project root>/exts/TESTS/`

### Structure Options: EDT and DESIGNER

| Format | Module code | Metadata file |
|--------|------------|-----------------|
| EDT | `exts/TESTS/src/CommonModules/<ИмяМодуля>/Module.bsl` | `.../<ИмяМодуля>.mdo` |
| DESIGNER | `exts/TESTS/src/CommonModules/<ИмяМодуля>/Ext/Module.bsl` | `.../<ИмяМодуля>.xml` |

If the format is not obvious, check `application-*.yml` / `yaxunit-*.yml` in the project root.

Do not mix structures: `Ext/` is required for DESIGNER, and it is not used for EDT.

A new module must be registered in `Configuration.[mdo|xml]`, otherwise the runner will not pick up the test.

**Absolute prohibitions:**
- Test source files are placed only in `exts/TESTS/**`, never in the main configuration
- `exts/YAXUNIT/**` is never modified manually — this is runner infrastructure

---

## Naming Test Modules

Pattern: `<Prefix>_<ObjectName>[_<Suffix>]`

### Prefixes by Object Type

| Object type | Prefix | Example |
|-------------|---------|--------|
| Common module | `ОМ_` | `ОМ_ОбщегоНазначения` |
| Document | `Док_` | `Док_ПоступлениеТоваров` |
| Catalog | `Спр_` | `Спр_Контрагенты` |
| Accumulation register | `РН_` | `РН_ОстаткиТоваров` |
| Information register | `РС_` | `РС_КурсыВалют` |
| Processing | `Обр_` | `Обр_ЗакрытиеМесяца` |

### Suffixes by Module Type

| Module type | Suffix | Example |
|-----------|---------|--------|
| Object module | `_МО` | `Спр_Контрагенты_МО` |
| Manager module | `_ММ` | `РН_ОстаткиТоваров_ММ` |
| Record set module | `_НЗ` | `РБ_Хозрасчетный_НЗ` |

---

## One-Off Operational YaxUnit Modules

Sometimes YaxUnit is used not as a regression test, but as a one-off server-side channel for a manual production operation: fix data, repost a targeted set of documents, perform a controlled migration. Such a module is NOT a regular test and must not accidentally end up in the "run all tests" mode.

### Required Marking

| What to mark | Convention |
|-----------------|-----------|
| Module name | `Опер_<Description>[_Number]` or a project prefix + explicit `_Операция_` fragment; do not disguise it as a regular `_Тест` |
| Module header | First-line comment: `// ONE_OFF_YAXUNIT_OPERATION: НЕ ЗАПУСКАТЬ В ОБЩЕМ ПРОГОНЕ. <назначение>` |
| Set name | Prefix `[ONE_OFF_OPERATION] <short purpose>` |
| YaxUnit tags | `.Тег("one-off-operation")` on the set and, if tests are registered separately, on each operational test |
| Execution context | Comment near registration: who approved the operation, on which base/environment it may be run, how to verify the result, and how to remove the module from the general run after completion |

### Barrier Against the General Run

The marker and tag are navigation, not protection. The operational module MUST have a technical barrier that prevents a normal all-tests run from registering and executing the operation:

1. Preferably, do not keep such a module registered in the general test extension after the operation is complete: move it to task artifacts, remove the registration, or disable the module via a separate maintenance task.
2. If the module temporarily remains in the test extension, `ИсполняемыеСценарии()` MUST return without `ДобавитьТестовыйНабор()` unless there is an explicit opt-in. The opt-in is set by a separate run parameter/configuration/wrapper and is documented in the registration comment. A normal "run all tests" does not set this opt-in.
3. Targeted execution of the operational module is allowed only with an explicit filter by module/method and the `one-off-operation` tag, after separate operator confirmation. Execution without a module/method filter is prohibited.
4. After a successful operation, the agent MUST record how the module was removed from the general run. Leaving an executable production-operation module in the general all-tests without an opt-in barrier is forbidden.

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

Required: export procedure `ИсполняемыеСценарии`. Only test registration - no data, no logic.

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

## YaxUnit Is Mandatory for Server-Side Changes

Any change to server-side logic or server context MUST have a YaxUnit check on the same runtime layer. This applies to common modules, manager/object modules, server-side form methods, queries, register/document writes, background and scheduled handlers, if the effect being checked is available from a server test.

Selection rule:

| Situation | Action |
|----------|----------|
| An existing server method changed, and a test already exists | Update the test for the new behavior and rerun it. |
| An existing server method changed, and there is no test | Add a minimal YaxUnit test for the changed contract. |
| A new server method/API was added | Add a YaxUnit test together with the method. |
| A server-side logic change is visible only through a process | Write a server integration test for the observable process effect or explicitly record why a higher scenario level is needed. |

Syntax, LSP, and a successful build do NOT replace YaxUnit for server logic: they confirm that the code can be loaded, but they do not confirm the method contract. If a test is technically impossible, this is recorded as a blocker/residual risk with a reason; silent omission is forbidden.

---

## Test Implementation

One test checks one assertion. Arrange-Act-Assert pattern:

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
    .Установить("ЕдиницаИзмерения", ПредопределённыйЭлемент("Классификатор.ЕдиницыИзмерения.Штука"))
    .Записать()
    .Ссылка();

// Document
Документ = ЮТест.Данные().СоздатьДокумент("Документ.ПоступлениеТоваров");
```

Data created through `ЮТест.Данные()` is **automatically deleted** after the test. Do not create data in `ИсполняемыеСценарии`.

---

## Rules for Filling Test Data (MUST)

The test object must be valid just like a production object. An incomplete test object either fails on `ПроверитьЗаполнение()`/posting, or is written semantically incomplete and produces a false-green result (the test passes on data that does not exist in reality).

| Requirement | Rule |
|---|---|
| **Owner for subordinate catalogs** | A catalog subordinate to an owner (the metadata has `Подчинение`/`Владельцы`) ALWAYS fills in `Владелец` in test data. A subordinate item without an owner is semantically invalid; code uniqueness is checked **per owner** (hence `Код не уникально` collisions); queries and cleanup by owner break on such an item. |
| **All required attributes** | Fill in ALL attributes for which metadata has `Проверка заполнения = Выдавать ошибку` (`FillChecking = ShowError`), plus the mandatory standard attributes. |
| **Required standard attributes** | Catalog: `Наименование`/`Код`, if marked by fill checking; subordinate one - `Владелец`. Document: `Дата` (and `Номер`, if there is no auto-numbering). Information register record set: ALL dimensions. |
| **Source of truth is metadata, NOT a neighboring test** | Before creating a test object, check the metadata description (`get_metadata_structure` / configurator): which attributes are `ShowError`, whether there is subordination. Copying the field set from a neighboring test without verification is forbidden - the object may have acquired a new required attribute. |

**Why by metadata, not by example:** field mandatory status is a property of the object (`Проверка заполнения`), and it changes when the configuration is modified. A test that filled fields "like a neighboring one" silently stops covering the new required attribute - and either fails during posting or writes incomplete data. Checking against the metadata description makes the field set self-updating.

```bsl
// Subordinate catalog: Owner is REQUIRED (Contract is subordinate to Counterparty)
Контрагент = ЮТест.Данные()
    .КонструкторОбъекта("Справочник.Контрагенты")
    .Установить("Наименование", "Тестовый контрагент")  // ShowError attribute
    .Записать()
    .Ссылка();

Договор = ЮТест.Данные()
    .КонструкторОбъекта("Справочник.ДоговорыКонтрагентов")
    .Установить("Владелец", Контрагент)                 // subordination: invalid without owner
    .Установить("Наименование", "Тестовый договор")      // ShowError attribute
    .Записать()
    .Ссылка();
```

---

## Mocking (Мокито)

Pattern: Learning -> Run -> Check.

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

### Mocking Scenarios

```bsl
Мокито.Обучение(Модуль).Когда().МетодА(Параметр).Вернуть(42);
Мокито.Обучение(Модуль).Когда().МетодБ(Параметр).ВыброситьИсключение("Текст ошибки");
Мокито.Обучение(Модуль).Когда().МетодВ().Пропустить();
Мокито.Обучение(Модуль).Когда().МетодГ().Наблюдать();
```

---

## Lifecycle and Event Handlers

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

### Context - Passing Data Between Methods

```bsl
ЮТест.Контекст().УстановитьЗначение("МоёЗначение", Данные);
Данные = ЮТест.Контекст().Значение("МоёЗначение");
```

---

## Parameterized Tests

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

A test that writes to the database must roll back its changes. Without isolation, each run leaves garbage in the database and tests lose idempotence.

### Transactional Isolation via `.ВТранзакции()`

The fluent method `.ВТранзакции()` is called immediately after `ДобавитьТестовыйНабор()` - the setting applies at the **set** level (the runtime searches the hierarchy: Test -> Set -> Module). Before each test in the set, YaxUnit opens a transaction, and after the test it rolls it back.

```bsl
Процедура ИсполняемыеСценарии() Экспорт

    ЮТТесты
        .ДобавитьТестовыйНабор("Проведение документа")
            .ВТранзакции()                              // ← изоляция: откат после каждого теста
            .ДобавитьСерверныйТест("ТестПроведениеСДоговором")
            .ДобавитьСерверныйТест("ТестПроведениеСКорректнойСуммой");

КонецПроцедуры
```

### Test Object Collector (mandatory teardown mechanism)

> The `test-zero-residue` rule requires: a test that generates data registers EVERY created object in the collector **at the moment of creation**; teardown iterates through the collector and physically deletes everything that survived transaction rollback. This is the main standard cleanup mechanism - NOT a database sweep by names/prefixes.

**Why a collector, not `ЮТест.Данные()` auto-tracking:** automatic deletion in `ЮТест.Данные()` works ONLY if `.УдалениеТестовыхДанных()` is called in the set. Objects created through `КонструкторОбъекта(...).Записать()`, `Документы.X.СоздатьДокумент()`, `Справочники.X.СоздатьЭлемент()`, or from helpers are **NOT tracked at all**. The collector covers ALL creation paths uniformly - with exact references, no guessing.

**Collector module contract (shared server module, e.g. `биг_ТестовыйКоллектор`):**
- `Зарегистрировать(Ссылка) Экспорт` - called immediately after EACH creation (catalog, document, subaccount, owner set, etc.).
- `ОчиститьВсё() Экспорт` - in teardown (`.После()` / final scenario step / `ПослеВсехТестов()`): LIFO traversal (dependents -> owners); `Объект = Ссылка.ПолучитьОбъект()`; if `Неопределено` (survived `.ВТранзакции()` rollback or was removed by cascade) -> skip; otherwise `Объект.ОбменДанными.Загрузка = Истина; Объект.Удалить();` under `Попытка` + log. At the end, reset the accumulator.
- **Accumulator storage trap:** a module `Перем` at session level in a shared server module does NOT survive between separate `НаСервереБезКонтекста` calls used by YaxUnit for each test. Store the accumulator in `ХранилищеОбщихНастроек` (or an equivalent that survives calls), not in `Перем`.
- **Reverse traversal trap (LIFO):** the `Для` loop in 1C counts ONLY upwards - there is no downward step. `Для Сч = Накопитель.ВГраница() По 0 Цикл` does NOT execute the body AT ALL (the condition `ВГраница() <= 0` is false immediately for a non-empty accumulator) - this is a SILENT no-op: the test is green, the log is clean, and residue accumulates (GBIG PAM precedent: `удалено=0` with 40 in the accumulator). Do reverse traversal ONLY via `Пока` with manual decrement BEFORE any `Продолжить`.

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
        Сч = Сч - 1;                       // декремент ДО `Продолжить`, иначе вечный цикл
        Если НЕ ЗначениеЗаполнено(Ссылка) Тогда
            Продолжить;
        КонецЕсли;
        Попытка
            Объект = Ссылка.ПолучитьОбъект();
            Если Объект <> Неопределено Тогда   // Неопределено = пережил откат / снят каскадом -> норма
                Объект.ОбменДанными.Загрузка = Истина;  // обход FillChecking/проведения при физ. удалении
                Объект.Удалить();
            КонецЕсли;
        Исключение
            ЗаписьЖурналаРегистрации("ТестовыйКоллектор", УровеньЖурналаРегистрации.Предупреждение,
                , Ссылка, ОписаниеОшибки());   // утечку видно в ЖР, но teardown не валим
        КонецПопытки;
    КонецЦикла;

    Сбросить();   // обнулить накопитель -> следующий модуль стартует с пустого
КонецПроцедуры
```

**Collector acceptance:** not "the test is green", but DELTA-0 - counts of affected catalogs/documents/registers before and after the run are equal. A green test with broken teardown is a typical mask (loop no-op above). Check the delta with a `КОЛИЧЕСТВО(*)` query before/after, and load BSL changes through a full rebuild (dynamic build is a no-op for BSL, residue from a previous run creates a false picture).

### Catalogs - create in a trackable way + register in the collector

Create catalog items through `ЮТест.Данные().СоздатьЭлемент(...)` or `КонструкторОбъекта(...).Записать()` and **register them in the collector immediately**. Important: `КонструкторОбъекта(...).Записать()` and direct `Справочники.X.СоздатьЭлемент()` are NOT tracked by YaxUnit (they remain in the database) - for them the collector is mandatory; even `ЮТест.Данные()` without `.УдалениеТестовыхДанных()` is not self-deleting. Direct `Справочники.X.СоздатьЭлемент()` outside the collector is an anti-pattern.

### Documents via `СоздатьДокумент()` - mandatory teardown

`ЮТест.Данные().СоздатьДокумент(...)` is tracked and deleted automatically. But if a document is created directly through `Документы.X.СоздатьДокумент()`, it is NOT tracked, and you need explicit teardown in `.После("ИмяПроцедурыОчистки")`.

### Exceptions from `.ВТранзакции()`

Three situations where `.ВТранзакции()` MUST NOT be used - in each case, a comment explaining the reason is mandatory for the set, along with teardown through `.После()`:

| Situation | Reason for exception | Isolation method |
|---|---|---|
| **(a) Negative posting test** (expected `Отказ`) | A failed nested transaction poisons the outer one: "В данной транзакции уже происходили ошибки!" on subsequent reads | `.УдалениеТестовыхДанных()` + `.После("Очистка")` |
| **(b) Production code with `ТранзакцияАктивна()` guard** | Two-phase commits, real API calls, registers with a unique key - fail or behave unpredictably inside a transaction | `.После("Очистка")` with manual cleanup |
| **(c) Client context** | `ДобавитьКлиентскийТест` - transactional rollback on the client is unavailable due to the platform architecture | `Перед`/`После` handlers with server context |

```bsl
// Исключение (а): негативный тест — ожидаемый Отказ отравляет внешнюю транзакцию.
// Изоляция: ЮТест.Данные() + .УдалениеТестовыхДанных() + teardown в .После().
ЮТТесты
    .ДобавитьТестовыйНабор("Запрет проведения")
        .УдалениеТестовыхДанных()
        .После("ОчиститьДокументыЗапретПроведения")
        .ДобавитьСерверныйТест("ТестЗапретБезДоговора");
```

### Object reread pattern during reposting

A test that changes the document write mode **must reread the object** between mode changes - this models form behavior:

```bsl
// Провести
ДокОбъект = ДокСсылка.ПолучитьОбъект();
ДокОбъект.Записать(РежимЗаписиДокумента.Проведение);

// Отменить проведение — перечитываем, как форма
ДокОбъект = ДокСсылка.ПолучитьОбъект();
ДокОбъект.Записать(РежимЗаписиДокумента.ОтменаПроведения);
```

**Platform 8.3.27 limitation:** programmatic reposting in a server session sometimes returns `[ОшибкаХранимыхДанных]` - a stack with no application frames, and neither rereading nor `.ВТранзакции()` helps. In that case, repost idempotence is checked at the scenario layer (Vanessa), and the unit test is оформлен through `ЮТест.Пропустить()` with an explicit reason.

### Self-Cleanup Checklist - verify with queries (MUST after writing/modifying write tests)

Isolation is declared in code (`.ВТранзакции()`, teardown), but **proven by fact** - with database counters before/after the run. A green run does NOT prove cleanliness: a test can pass and still leave garbage.

1. **Table map.** Make a list of the tables that the module's tests may write to: everything created by `Перед` handlers and test bodies (catalogs, documents, registers), including context helper objects (`СоздатьКонтекстТеста` etc.).
2. **Counters BEFORE.** For each table, run `ВЫБРАТЬ КОЛИЧЕСТВО(*) ИЗ Справочник.X` (platform query: MCP `execute_query` / query console). For information registers, use a counter by the test marker dimension, not by the whole table.
3. **Run + counters AFTER.** Run the module end-to-end, capture the same counters. Delta for each table = 0.
4. **Second run.** Repeat: GREEN + delta 0 again. The first run may have "eaten" someone else's accumulated garbage and masked its own - repeatability is mandatory.
5. **Delta != 0 -> culprit.** Find the remaining objects by names/markers (`ВЫБРАТЬ Наименование ... ГДЕ Наименование ПОДОБНО "...%"`), determine the creating handler/test, add teardown, and repeat the checklist from step 2.

**Traps (TASK-173 / TASK-165.7 precedent):**

| Trap | Essence |
|---|---|
| YaxUnit auto-delete fails on `.Записать(Ложь, Истина)` | `УстановитьПометкуУдаления` revalidates required attributes and refuses - the object remains. Teardown is physical deletion: `Объект.ОбменДанными.Загрузка = Истина; Объект.Удалить();` |
| Partial teardown | A `После` handler cleans only part of what was created (for example, a semaphore register but not the account catalog) - it looks like teardown, but it leaves garbage |
| "GREEN = clean" | 41/41 tests are green, while one module left +15 objects per run - discovered only by counters |

---

## Anti-Patterns

| Anti-pattern | Correct |
|-------------|-----------|
| Data in `ИсполняемыеСценарии` | Data in the test body or `Перед` handler |
| One test checks 10 conditions | One test - one assertion |
| Test depends on execution order | Each test is isolated |
| Hardcoded references to IB objects | Create through `ЮТест.Данные()` |
| Testing private logic | Test through the public interface |
| Mocking the module under test | Mock only *dependencies* |
| Write-enabled set without `.ВТранзакции()` | `.ВТранзакции()` by default; exceptions - with a comment + teardown |
| `Справочники.X.СоздатьЭлемент()` in a test | `ЮТест.Данные().СоздатьЭлемент()` + registration in the collector |
| `.ВТранзакции()` on a negative posting test | Exception (a) - poisons the transaction; use `.УдалениеТестовыхДанных()` + `.После()` |
| Created an object, did not register it in the collector | Each creation -> `Коллектор.Зарегистрировать(Ссылка)` immediately; teardown -> `Коллектор.ОчиститьВсё()` |
| Teardown by sweeping the database by name/prefix/regex (`ПОДОБНО "Тест%"`) | Fragile (too broad will delete production data, too narrow will leave residue). Only a one-time historical junk sweeper; standard teardown is the collector (exact references) |
| "GREEN = clean" - acceptance without counters | Self-cleanup checklist: counters before/after via queries, two runs, delta 0 |

---

## TDD Layers and Role Boundaries

Tests and implementation are written by **different agents** in **different phases**. The test author does not know the implementation, and the code author does not modify tests.

```
Phase 3a: Scenario-Author  → .feature (BDD)   ┐ parallel
Phase 3b: Developer-Tests  → unit tests (Red)  ┘
Phase 3c: Developer-Code   → code (Green)
Phase 4:  Tester           → edge cases, regression, BDD + unit
```

### Test Layers

| Layer | Phase | Agent | Covers |
|------|------|-------|-----------|
| BDD (acceptance) | 3a | Scenario-Author | Behavior through the UI |
| TDD (unit) | 3b | Developer-Tests | Public methods, MUST scenarios, basic negatives |
| TDD (green) | 3c | Developer-Code | Implementation that passes unit tests |
| Coverage | 4 | Tester | Edge cases, integration, regression |

Phase 3a and 3b are **parallel**. Phase 3c starts after both are complete.

### Agent Boundaries

- **Scenario-Author:** does NOT write unit tests, does NOT run scenarios, does NOT extend beyond the specification
- **Developer-Tests:** MUST scenarios + basic negatives; does NOT cover combinatorial edge cases and integration
- **Tester:** expands coverage; does NOT duplicate Developer tests; does NOT modify BSL code
