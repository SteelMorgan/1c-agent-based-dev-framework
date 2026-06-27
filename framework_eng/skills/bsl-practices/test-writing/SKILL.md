---
name: test-writing
description: "Use for YaxUnit BSL tests, mocks, and assertions"
---

# Writing YaxUnit Tests (BSL)

Running written tests is a separate skill [`test-execution`](../../tool-usage/test-execution/SKILL.md).

**Full YaxUnit documentation:** see [`references/yaxunit-cheatsheet.md`](references/yaxunit-cheatsheet.md)

---

## Test location in the project

Tests are stored in a **separate configuration extension**: `<project root>/exts/TESTS/`

### Structure options: EDT and DESIGNER

| Format | Module code | Metadata file |
|--------|------------|-----------------|
| EDT | `exts/TESTS/src/CommonModules/<ИмяМодуля>/Module.bsl` | `.../<ИмяМодуля>.mdo` |
| DESIGNER | `exts/TESTS/src/CommonModules/<ИмяМодуля>/Ext/Module.bsl` | `.../<ИмяМодуля>.xml` |

If the format is not obvious, check `application-*.yml` / `yaxunit-*.yml` at the project root.

Do not mix the structures: `Ext/` is required for DESIGNER, and it is not used for EDT.

A new module must be registered in `Configuration.[mdo|xml]`, otherwise the runner will not pick up the test.

**Absolute prohibitions:**
- Test sources are placed only in `exts/TESTS/**`, never in the main configuration
- `exts/YAXUNIT/**` is never edited manually - this is runner infrastructure

---

## Naming of test modules

Template: `<Prefix>_<ObjectName>[_<Suffix>]`

### Prefixes by object type

| Object type | Prefix | Example |
|-------------|---------|--------|
| Common module | `ОМ_` | `ОМ_ОбщегоНазначения` |
| Документ | `Док_` | `Док_ПоступлениеТоваров` |
| Справочник | `Спр_` | `Спр_Контрагенты` |
| Регистр накопления | `РН_` | `РН_ОстаткиТоваров` |
| Регистр сведений | `РС_` | `РС_КурсыВалют` |
| Обработка | `Обр_` | `Обр_ЗакрытиеМесяца` |

### Suffixes by module type

| Module type | Suffix | Example |
|-----------|---------|--------|
| Object module | `_МО` | `Спр_Контрагенты_МО` |
| Manager module | `_ММ` | `РН_ОстаткиТоваров_ММ` |
| Record set module | `_НЗ` | `РБ_Хозрасчетный_НЗ` |

---

## One-off operational YaxUnit modules

Sometimes YaxUnit is used not as a regression test, but as a one-off server-side channel for a manual production operation: fix data, repost a targeted set of documents, perform a controlled migration. Such a module is NOT an ordinary test and must not accidentally end up in the "run all tests" mode.

### Required marking

| What to mark | Convention |
|-----------------|-----------|
| Module name | `Опер_<Description>[_Number]` or project prefix + explicit `_Операция_` fragment; do not disguise it as an ordinary `_Тест` |
| Module header | First-line comment: `// ONE_OFF_YAXUNIT_OPERATION: НЕ ЗАПУСКАТЬ В ОБЩЕМ ПРОГОНЕ. <назначение>` |
| Set name | Prefix `[ONE_OFF_OPERATION] <short purpose>` |
| YaxUnit tags | `.Тег("one-off-operation")` on the set and, if tests are registered separately, on each operational test |
| Run context | Comment next to registration: who approved the operation, on which base/environment it may run, how to verify the result, and how to remove the module from the general run after completion |

### Barrier against the general run

The marker and tag are navigation, not protection. The operational module MUST have a technical barrier that prevents the ordinary all-tests run from registering and executing the operation:

1. Preferably, do not keep such a module registered in the general test extension after the operation is complete: move it to task artifacts, remove the registration, or disable the module through a separate maintenance task.
2. If the module temporarily remains in the test extension, `ИсполняемыеСценарии()` MUST return without `ДобавитьТестовыйНабор()` unless there is explicit opt-in. Opt-in is provided by a separate run parameter / setting / wrapper and is documented in the registration comment. The ordinary "run all tests" does not set this opt-in.
3. A targeted run of the operational module is allowed only with an explicit filter by module/method and the `one-off-operation` tag, after separate operator confirmation. Running without a module/method filter is forbidden.
4. After a successful operation, the agent MUST record how the module was removed from the general run. Leaving an executable production-operation module in the general all-tests run without an opt-in barrier is forbidden.

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

## Test module structure

Mandatory: export procedure `ИсполняемыеСценарии`. Registration only - no data, no logic.

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

## Test implementation

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
// Базовые сравнения
ЮТест.ОжидаетЧто(Результат).Равно(42);
ЮТест.ОжидаетЧто(Результат).НеРавно(0);
ЮТест.ОжидаетЧто(Результат).Больше(10);
ЮТест.ОжидаетЧто(Флаг).ЭтоИстина();
ЮТест.ОжидаетЧто(Значение).ВСписке(МассивДопустимых);

// Тип и заполненность
ЮТест.ОжидаетЧто(Ссылка).ИмеетТип("СправочникСсылка.Номенклатура");
ЮТест.ОжидаетЧто(Значение).НеЯвляетсяНеопределено();

// Исключения
ЮТест.ОжидаетЧто(ЭтотОбъект).МетодВыбрасываетИсключение("МетодСОшибкой", Параметры);

// Данные ИБ
ЮТест.ОжидаетЧтоТаблицаБазы("Справочник.Склады")
    .СодержитЗаписи()
    .ГдеРеквизит("Наименование").Равно("Основной склад");
```

---

## Test data (ЮТест.Данные)

```bsl
// Пустышка
Склад = ЮТест.Данные().СоздатьЭлемент("Справочник.Склады");

// Конструктор с реквизитами
Номенклатура = ЮТест.Данные()
    .КонструкторОбъекта("Справочник.Номенклатура")
    .Установить("Наименование", "Тестовый товар")
    .Установить("ЕдиницаИзмерения", ПредопределённыйЭлемент("Классификатор.ЕдиницыИзмерения.Штука"))
    .Записать()
    .Ссылка();

// Документ
Документ = ЮТест.Данные().СоздатьДокумент("Документ.ПоступлениеТоваров");
```

Data created through `ЮТест.Данные()` is **automatically deleted** after the test. Do not create data in `ИсполняемыеСценарии`.

---

## Rules for filling test data (MUST)

A test object must be valid just like a production one. An incomplete test object either fails in `ПроверитьЗаполнение()`/posting, or is written semantically incomplete and produces a false-green result (the test passes on data that do not exist in real life).

| Requirement | Rule |
|---|---|
| **Owner for subordinate Справочник** | A `Справочник` subordinate to an owner (metadata has `Подчинение`/`Владельцы`) must ALWAYS fill `Владелец` in test data. A subordinate item without an owner is semantically invalid; code uniqueness is checked **within the owner scope** (hence the `Код не уникально` collisions); queries and cleanup by owner break on such an item. |
| **All mandatory requisites** | Fill ALL requisites whose metadata has `Проверка заполнения = Выдавать ошибку` (`FillChecking = ShowError`), plus mandatory standard requisites. |
| **Mandatory standard requisites** | `Справочник`: `Наименование`/`Код` if they are marked by fill checking; subordinate - `Владелец`. `Документ`: `Дата` (and `Номер` if there is no auto-numbering). `Набор записей РС`: ALL dimensions. |
| **Source of truth - metadata, NOT a neighboring test** | Before creating a test object, check the metadata description (`get_metadata_structure` / Configurator): which requisites are `ShowError`, whether there is subordination. Copying a field set from a neighboring test without checking is forbidden - the object may have acquired a new mandatory requisite. |

**Why by metadata, not by example:** field mandatory-ness is an object property (`Проверка заполнения`), and it changes when the configuration is updated. A test that fills fields "like the neighbor" silently stops covering a new mandatory requisite - and then either fails on posting or writes incomplete data. Checking against the metadata description makes the field set self-updating.

```bsl
// Подчинённый справочник: Владелец ОБЯЗАТЕЛЕН (Договор подчинён Контрагенту)
Контрагент = ЮТест.Данные()
    .КонструкторОбъекта("Справочник.Контрагенты")
    .Установить("Наименование", "Тестовый контрагент")  // ShowError-реквизит
    .Записать()
    .Ссылка();

Договор = ЮТест.Данные()
    .КонструкторОбъекта("Справочник.ДоговорыКонтрагентов")
    .Установить("Владелец", Контрагент)                 // подчинение: без владельца невалидно
    .Установить("Наименование", "Тестовый договор")      // ShowError-реквизит
    .Записать()
    .Ссылка();
```

---

## Mocking (Мокито)

Pattern: Training -> Run -> Verify.

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

## Test data isolation (MUST)

A test that writes to the database must roll back its changes. Without isolation, every run leaves garbage in the database and the tests lose idempotence.

### Transactional isolation via `.ВТранзакции()`

The fluent method `.ВТранзакции()` is called immediately after `ДобавитьТестовыйНабор()` - the setting is applied at the **set** level (the runtime searches by hierarchy: Test -> Set -> Module). Before each test in the set, YaxUnit opens a transaction, and after the test it rolls it back.

```bsl
Процедура ИсполняемыеСценарии() Экспорт

    ЮТТесты
        .ДобавитьТестовыйНабор("Проведение документа")
            .ВТранзакции()                              // ← изоляция: откат после каждого теста
            .ДобавитьСерверныйТест("ТестПроведениеСДоговором")
            .ДобавитьСерверныйТест("ТестПроведениеСКорректнойСуммой");

КонецПроцедуры
```

### Test object collector (mandatory teardown mechanism)

> The `test-zero-residue` rule requires: a test that generates data must register EVERY created object in the collector **at the moment of creation**; teardown iterates through the collector and physically deletes everything that survived the transaction rollback. This is the main standard cleanup mechanism - NOT a database sweep by names/prefixes.

**Why a collector, not `ЮТест.Данные()` auto-tracking:** auto-deletion of `ЮТест.Данные()` works ONLY if `.УдалениеТестовыхДанных()` is called on the set. Objects created through `КонструкторОбъекта(...).Записать()`, `Документы.X.СоздатьДокумент()`, `Справочники.X.СоздатьЭлемент()` or from helpers are **NOT tracked at all**. The collector covers ALL creation paths uniformly - by exact references, without guessing.

**Collector module contract (a common server module, for example `биг_ТестовыйКоллектор`):**
- `Зарегистрировать(Ссылка) Экспорт` - called immediately after EACH creation (catalog, document, subaccount, owner set, etc.).
- `ОчиститьВсё() Экспорт` - in teardown (`.После()` / final scenario step / `ПослеВсехТестов()`): LIFO traversal (dependents -> owners); `Объект = Ссылка.ПолучитьОбъект()`; if `Неопределено` (survived `.ВТранзакции()` rollback or was removed cascade) -> skip; otherwise `Объект.ОбменДанными.Загрузка = Истина; Объект.Удалить();` under `Попытка` + log. At the end - reset the accumulator.
- **Accumulator storage trap:** a module `Перем` at the session level in a common server module does NOT survive between separate `НаСервереБезКонтекста` calls, which YaxUnit uses to run each test. Store the accumulator in `ХранилищеОбщихНастроек` (or an equivalent that survives calls), not in `Перем`.
- **Reverse traversal trap (LIFO):** the `Для` loop in 1C counts ONLY upward - there is no downward step. `Для Сч = Накопитель.ВГраница() По 0 Цикл` does NOT execute the body AT ALL (the condition `ВГраница() <= 0` is false immediately for a non-empty accumulator) - this is a SILENT no-op: the test is green, the log is clean, and residue keeps accumulating (GBIG PAM precedent: `удалено=0` with 40 in the accumulator). Reverse traversal must be done ONLY with `Пока` and manual decrement BEFORE any `Продолжить`.

```bsl
// creation — register immediately
Портфель = ЮТест.Данные().СоздатьЭлемент("Справочник.биг_Портфели").Установить(...).Объект().Ссылка;
биг_ТестовыйКоллектор.Зарегистрировать(Портфель);
...
// teardown — one call for the whole accumulator
Процедура ПослеВсехТестов() Экспорт
    биг_ТестовыйКоллектор.ОчиститьВсё();
КонецПроцедуры
```

**Canonical reverse traversal in `ОчиститьВсё()` (LIFO: dependents before owners):**

```bsl
Процедура ОчиститьВсё() Экспорт
    Накопитель = ПрочитатьНакопитель();

    // ВАЖНО: `Пока` with decrement, NOT `Для ... По 0` (that one will not execute - see the trap above).
    Сч = Накопитель.ВГраница();
    Пока Сч >= 0 Цикл
        Ссылка = Накопитель[Сч];
        Сч = Сч - 1;                       // decrement BEFORE `Продолжить`, otherwise infinite loop
        Если НЕ ЗначениеЗаполнено(Ссылка) Тогда
            Продолжить;
        КонецЕсли;
        Попытка
            Объект = Ссылка.ПолучитьОбъект();
            Если Объект <> Неопределено Тогда   // Неопределено = survived rollback / removed cascade -> normal
                Объект.ОбменДанными.Загрузка = Истина;  // bypass FillChecking/posting during physical delete
                Объект.Удалить();
            КонецЕсли;
        Исключение
            ЗаписьЖурналаРегистрации("ТестовыйКоллектор", УровеньЖурналаРегистрации.Предупреждение,
                , Ссылка, ОписаниеОшибки());   // the leak is visible in the log, but teardown does not fail
        КонецПопытки;
    КонецЦикла;

    Сбросить();   // reset the accumulator -> the next module starts empty
КонецПроцедуры
```

**Collector acceptance:** not "the test is green", but DELTA-0 - the counters of affected catalogs/documents/registers before and after the run are equal. A green test with a broken teardown is a typical disguise (loop no-op above). Check the delta with `КОЛИЧЕСТВО(*)` before/after, and load BSL changes through a full rebuild (dynamic build - no-op for BSL, residue from the previous run creates a false picture).

### Справочники - create trackably + register in the collector

Create catalog elements through `ЮТест.Данные().СоздатьЭлемент(...)` or `КонструкторОбъекта(...).Записать()` and **register them in the collector immediately**. Important: `КонструкторОбъекта(...).Записать()` and direct `Справочники.X.СоздатьЭлемент()` are NOT tracked by YaxUnit (they remain in the database) - for them the collector is mandatory; even `ЮТест.Данные()` without `.УдалениеТестовыхДанных()` is not self-deleting. Direct `Справочники.X.СоздатьЭлемент()` outside the collector is an anti-pattern.

### Documents through `СоздатьДокумент()` - mandatory teardown

`ЮТест.Данные().СоздатьДокумент(...)` is tracked and deleted automatically. But if a document is created directly through `Документы.X.СоздатьДокумент()` - it is NOT tracked, and you need an explicit teardown in `.После("ИмяПроцедурыОчистки")`.

### Exceptions from `.ВТранзакции()`

Three situations when `.ВТранзакции()` MUST NOT be used - for each of them, a comment with the reason is mandatory next to the set and teardown through `.После()`:

| Situation | Reason for exclusion | Isolation method |
|---|---|---|
| **(a) Negative posting test** (expected `Отказ`) | A failed nested transaction poisons the outer one: "В данной транзакции уже происходили ошибки!" on subsequent reads | `.УдалениеТестовыхДанных()` + `.После("Очистка")` |
| **(b) Production code with `ТранзакцияАктивна()` guard** | Two-phase commits, real API calls, registers with a unique key - fail or behave unpredictably inside a transaction | `.После("Очистка")` with manual cleanup |
| **(c) Client context** | `ДобавитьКлиентскийТест` - transactional rollback is not available on the client by platform architecture | `Перед`/`После` handlers with server context |

```bsl
// Исключение (а): негативный тест — ожидаемый Отказ отравляет внешнюю транзакцию.
// Изоляция: ЮТест.Данные() + .УдалениеТестовыхДанных() + teardown в .После().
ЮТТесты
    .ДобавитьТестовыйНабор("Запрет проведения")
        .УдалениеТестовыхДанных()
        .После("ОчиститьДокументыЗапретПроведения")
        .ДобавитьСерверныйТест("ТестЗапретБезДоговора");
```

### Pattern for rereading an object during reposting

A test that changes the document write mode **must reread the object** between changes - this models form behavior:

```bsl
// Провести
ДокОбъект = ДокСсылка.ПолучитьОбъект();
ДокОбъект.Записать(РежимЗаписиДокумента.Проведение);

// Отменить проведение — перечитываем, как форма
ДокОбъект = ДокСсылка.ПолучитьОбъект();
ДокОбъект.Записать(РежимЗаписиДокумента.ОтменаПроведения);
```

**Platform limitation 8.3.27:** programmatic reposting in a server session sometimes gives `[ОшибкаХранимыхДанных]` - a stack without application frames, and neither rereading nor `.ВТранзакции()` helps. In that case, reposting idempotence is checked at the scenario layer (Vanessa), and the unit test is оформляется through `ЮТест.Пропустить()` with an explicit explanation.

### Self-cleanup checklist - verification by queries (MUST after writing/editing write tests)

Isolation is declared in code (`.ВТранзакции()`, teardown), but **proved by fact** - by database counters before/after the run. A green run does NOT prove cleanliness: the test may pass and still leave garbage behind.

1. **Table map.** Make a list of the tables that the module tests may write to: everything created by `Перед` handlers and test bodies (catalogs, documents, registers), including context-helper objects (`СоздатьКонтекстТеста` and the like).
2. **Counters BEFORE.** For each table - `ВЫБРАТЬ КОЛИЧЕСТВО(*) ИЗ Справочник.X` (platform query: MCP `execute_query` / query console). For information registers - a counter by the test marker dimension, not by the whole table.
3. **Run + counters AFTER.** Run the whole module, collect the same counters. Delta for each table = 0.
4. **Second run.** Repeat: GREEN + delta 0 again. The first run may have "eaten" someone else's accumulated garbage and masked its own - repeatability is mandatory.
5. **Delta != 0 -> culprit.** Find the remaining objects by names/markers (`ВЫБРАТЬ Наименование ... ГДЕ Наименование ПОДОБНО "...%"`), identify the creating handler/test, add teardown, and repeat the checklist from step 2.

**Traps (TASK-173 / TASK-165.7 precedent):**

| Trap | Essence |
|---|---|
| YaxUnit auto-deletion fails on `.Записать(Ложь, Истина)` | `УстановитьПометкуУдаления` re-validates mandatory requisites and refuses - the object remains. Teardown is physical deletion: `Объект.ОбменДанными.Загрузка = Истина; Объект.Удалить();` |
| Partial teardown | The `После` handler cleans only part of what was created (for example a semaphore register, but not the account catalog) - it looks like teardown, but it still leaves garbage |
| "GREEN = clean" | 41/41 tests are green, while one module left +15 objects per run - detected only by counters |

---

## Anti-patterns

| Anti-pattern | Correct |
|-------------|-----------|
| Data in `ИсполняемыеСценарии` | Data in the test body or `Перед` handler |
| One test checks 10 conditions | One test - one assertion |
| Test depends on execution order | Each test is isolated |
| Hardcoded links to database objects | Create through `ЮТест.Данные()` |
| Testing private logic | Test through the public interface |
| Mocking the module under test | Mock only *dependencies* |
| Writing set without `.ВТранзакции()` | `.ВТранзакции()` by default; exceptions - with a comment + teardown |
| `Справочники.X.СоздатьЭлемент()` in a test | `ЮТест.Данные().СоздатьЭлемент()` + registration in the collector |
| `.ВТранзакции()` on a negative posting test | Exception (a) - poisons the transaction; use `.УдалениеТестовыхДанных()` + `.После()` |
| Created an object, did not register it in the collector | Every creation -> `Коллектор.Зарегистрировать(Ссылка)` immediately; teardown -> `Коллектор.ОчиститьВсё()` |
| Teardown by scanning the database by name/prefix/regex (`ПОДОБНО "Тест%"`) | Fragile (too broad deletes production data, too narrow leaves residue). Only a one-time sweeper for historical garbage; standard teardown is the collector (exact references) |
| "GREEN = clean" acceptance without counters | Self-cleanup checklist: counters before/after by queries, two runs, delta 0 |

---

## Layers and phases of TDD, role boundaries

Tests and implementation are written by **different agents** in **different phases**. The test author does not know the implementation, and the code author does not modify tests.

```
Phase 3a: Scenario-Author  → .feature (BDD)   ┐ parallel
Phase 3b: Developer-Tests  → unit tests (Red)  ┘
Phase 3c: Developer-Code   → code (Green)
Phase 4:  Tester           → edge cases, regression, BDD + unit
```

### Testing layers

| Layer | Phase | Agent | Covers |
|------|------|-------|-----------|
| BDD (acceptance) | 3a | Scenario-Author | Behavior through UI |
| TDD (unit) | 3b | Developer-Tests | Public methods, MUST scenarios, basic negatives |
| TDD (green) | 3c | Developer-Code | Implementation that passes unit tests |
| Coverage | 4 | Tester | Edge cases, integration, regression |

Phase 3a and 3b are **parallel**. Phase 3c starts after both are complete.

### Agent boundaries

- **Scenario-Author:** does NOT write unit tests, does NOT run scenarios, does NOT extend beyond the specification
- **Developer-Tests:** MUST scenarios + basic negatives; does NOT cover combinatorial edge cases and integration
- **Tester:** extends coverage; does NOT duplicate Developer tests; does NOT modify BSL code

### Rule when a Tester test fails

```
Тест упал
  ├── Ошибка в тесте → Tester исправляет (test_error)
  └── Баг в коде → СТОП. Метка implementation_error + описание.
                   Оркестратор возвращает задачу Developer.
```

**User/Role context in Test Plan:** if the code uses `SetPrivilegedMode`, role checks (`AccessRight`, `RoleAvailable`) or the result depends on the current user, the specification MUST explicitly list for each test in the "Test Plan" section: user name / role set, required mode (privileged or not), expected result (success / отказ). Without this, the test under a full-rights runner (for example `AgentAI`) will produce a false positive: it will pass "by coincidence" through the privileged branch without checking role-dependent behavior. If this is technically impossible for unit tests, it is recorded in the spec as a separate ADR with a move to integration scope (Phase 4).

---
depends_on: []
---
