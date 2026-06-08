---
name: test-writing
description: "Use for writing YaxUnit (BSL) test modules. Covers test registration, assertions, mocking, and test data preparation."
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
| Общий модуль | `ОМ_` | `ОМ_ОбщегоНазначения` |
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

A test that writes to the DB must roll back its changes. Without isolation, every run leaves garbage in the database and the tests lose idempotency.

### Transactional isolation via `.ВТранзакции()`

The fluent method `.ВТранзакции()` is called immediately after `ДобавитьТестовыйНабор()` - the setting applies at the **set** level (runtime resolves it by hierarchy: Test -> Set -> Module). Before each test in the set, YaxUnit opens a transaction, and after the test it rolls it back.

```bsl
Процедура ИсполняемыеСценарии() Экспорт

    ЮТТесты
        .ДобавитьТестовыйНабор("Проведение документа")
            .ВТранзакции()                              // ← изоляция: откат после каждого теста
            .ДобавитьСерверныйТест("ТестПроведениеСДоговором")
            .ДобавитьСерверныйТест("ТестПроведениеСКорректнойСуммой");

КонецПроцедуры
```

### Справочники - only through `ЮТест.Данные()`

Create catalog items through `ЮТест.Данные().СоздатьЭлемент(...)` or `КонструкторОбъекта(...).Записать()`. Such objects are tracked by YaxUnit and deleted automatically. A direct call to `Справочники.X.СоздатьЭлемент()` is an antipattern: the object is not tracked and remains in the database.

### Документы via `СоздатьДокумент()` - mandatory teardown

`ЮТест.Данные().СоздатьДокумент(...)` is tracked and deleted automatically. But if a document is created directly through `Документы.X.СоздатьДокумент()`, it is NOT tracked, and an explicit teardown in `.После("ИмяПроцедурыОчистки")` is required.

### Exceptions from `.ВТранзакции()`

There are three situations where `.ВТранзакции()` MUST NOT be used - in each case, a justification comment is required on the set and teardown through `.После()`:

| Situation | Reason for exclusion | Isolation method |
|---|---|---|
| **(a) Negative posting test** (expected `Отказ`) | A failed nested transaction poisons the outer one: "Errors have already occurred in this transaction!" on subsequent reads | `.УдалениеТестовыхДанных()` + `.После("Очистка")` |
| **(b) Prod code with `ТранзакцияАктивна()` guard** | Two-phase commits, real API calls, registers with a unique key - fail or behave unpredictably inside a transaction | `.После("Очистка")` with manual cleanup |
| **(c) Client context** | `ДобавитьКлиентскийТест` - transactional rollback on the client is unavailable due to platform architecture | `Перед`/`После` handlers with server context |

```bsl
// Exception (a): negative test - the expected Отказ poisons the outer transaction.
// Isolation: ЮТест.Данные() + .УдалениеТестовыхДанных() + teardown in .После().
ЮТТесты
    .ДобавитьТестовыйНабор("Запрет проведения")
        .УдалениеТестовыхДанных()
        .После("ОчиститьДокументыЗапретПроведения")
        .ДобавитьСерверныйТест("ТестЗапретБезДоговора");
```

### Object reread pattern when reposting

A test that changes a document's write mode **must reread the object** between mode changes - this models form behavior:

```bsl
// Провести
ДокОбъект = ДокСсылка.ПолучитьОбъект();
ДокОбъект.Записать(РежимЗаписиДокумента.Проведение);

// Отменить проведение — перечитываем, как форма
ДокОбъект = ДокСсылка.ПолучитьОбъект();
ДокОбъект.Записать(РежимЗаписиДокумента.ОтменаПроведения);
```

**Platform 8.3.27 limitation:** programmatic reposting in a server session sometimes produces `[ОшибкаХранимыхДанных]` - a stack without application frames, and neither rereading nor `.ВТранзакции()` helps. In that case, reposting idempotency is verified at the scenario layer (Vanessa), and the unit test is recorded via `ЮТест.Пропустить()` with an explicit justification.

### Self-cleanup checklist - verification by queries (MUST after writing/editing write tests)

Isolation is declared in code (`.ВТранзакции()`, teardown), but it is **proven by facts** - counters in the DB before/after the run. A green run does NOT prove cleanliness: a test may pass and still leave garbage behind.

1. **Table map.** Make a list of the tables the module tests may write to: everything created by `Перед` handlers and test bodies (catalogs, documents, registers), including context helper objects (`СоздатьКонтекстТеста` and so on).
2. **Counters BEFORE.** For each table - `ВЫБРАТЬ КОЛИЧЕСТВО(*) ИЗ Справочник.X` (platform query: MCP `execute_query` / query console). For information registers, count by the test marker dimension, not the whole table.
3. **Run + counters AFTER.** Run the whole module, collect the same counters. The delta for each table = 0.
4. **Second run.** Repeat: GREEN + delta 0 again. The first run may have "eaten" someone else's accumulated garbage and masked your own - repeatability is mandatory.
5. **Delta != 0 -> culprit.** Find the remaining objects by names/markers (`ВЫБРАТЬ Наименование ... ГДЕ Наименование ПОДОБНО "...%"`), identify the creating handler/test, add teardown, repeat the checklist from step 2.

**Pitfalls (precedent TASK-173 / TASK-165.7):**

| Pitfall | Essence |
|---|---|
| YaxUnit auto-delete fails on `.Записать(Ложь, Истина)` | `УстановитьПометкуУдаления` revalidates required fields and refuses - the object remains. Teardown is physical deletion: `Объект.ОбменДанными.Загрузка = Истина; Объект.Удалить();` |
| Partial teardown | The `После` handler cleans only part of what was created (for example, the semaphore register but not the account catalog) - it looks like teardown, but still leaves garbage |
| "GREEN = clean" | 41/41 tests are green, while one module still left +15 objects per run - detected only by counters |

---

## Antipatterns

| Antipattern | Correct |
|-------------|-----------|
| Data in `ИсполняемыеСценарии` | Data in the test body or `Перед` handler |
| One test checks 10 conditions | One test - one assertion |
| Test depends on execution order | Each test is isolated |
| Hardcoded references to infobase objects | Create through `ЮТест.Данные()` |
| Testing private logic | Test through the public interface |
| Mocking the module under test | Mock only *dependencies* |
| Writable set without `.ВТранзакции()` | `.ВТранзакции()` by default; exceptions - with a comment + teardown |
| `Справочники.X.СоздатьЭлемент()` in a test | `ЮТест.Данные().СоздатьЭлемент()` - tracked and deleted automatically |
| `.ВТранзакции()` on a negative posting test | Exception (a) - poisons the transaction; use `.УдалениеТестовыхДанных()` + `.После()` |
| "GREEN = clean" acceptance without counters | Self-cleanup checklist: counters before/after by queries, two runs, delta 0 |

---

## TDD layers and phases, role boundaries

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
| BDD (acceptance) | 3a | Scenario-Author | Behavior through the UI |
| TDD (unit) | 3b | Developer-Tests | Public methods, MUST scenarios, basic negatives |
| TDD (green) | 3c | Developer-Code | Implementation that passes unit tests |
| Coverage | 4 | Tester | Edge cases, integration, regression |

Phase 3a and 3b run **in parallel**. Phase 3c starts after both are complete.

### Agent boundaries

- **Scenario-Author:** does NOT write unit tests, does NOT run scenarios, does NOT go beyond the specification
- **Developer-Tests:** MUST scenarios + basic negatives; does NOT cover combinatorial edge cases and integration
- **Tester:** extends coverage; does NOT duplicate Developer tests; does NOT edit BSL code

### Rule when a Tester test fails

```
Тест упал
  ├── Ошибка в тесте → Tester исправляет (test_error)
  └── Баг в коде → СТОП. Метка implementation_error + описание.
                   Оркестратор возвращает задачу Developer.
```

**User/Role context in Test Plan:** if the code uses `SetPrivilegedMode`, role checks (`AccessRight`, `RoleAvailable`), or the result depends on the current user, the specification MUST explicitly state for each test in the "Test Plan" section: user name / role set, required mode (privileged or not), expected result (success / refusal). Without this, a test under a full-rights runner (for example `AgentAI`) will produce a false positive: it will pass "by coincidence" through the privileged branch without checking role-dependent behavior. If this is technically impossible for unit tests, it is recorded in the spec as a separate ADR with a move to integration scope (Phase 4).

---
depends_on: []
---
