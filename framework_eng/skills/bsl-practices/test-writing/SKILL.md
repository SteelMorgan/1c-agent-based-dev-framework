---
name: test-writing
description: Writing YaxUnit (BSL) tests. The skill teaches the agent to create test modules for the YaxUnit framework — registering tests, assertions, mocking, test data.
---

# Writing YaxUnit (BSL) Tests

## Purpose

The skill teaches the agent how to **write BSL tests** for the YaxUnit framework. It covers the test module structure, assertion API, mocking, and test data creation.

Running the written tests is a separate skill [`test-execution`](../../tool-usage/test-execution/SKILL.md).

**Full YaxUnit documentation:** see [`references/yaxunit-cheatsheet.md`](references/yaxunit-cheatsheet.md)

---

## Location of Tests in the Project

Tests are stored in a **separate configuration extension** at:

```
<project root>/exts/TESTS/
```

Basic extension structure:

```
exts/TESTS/
  src/
    CommonModules/          ← shared test modules
    Configuration/          ← module registration (if needed)
    ...
```

### Module Structure Options: EDT and DESIGNER

Depending on the project format, the placement of `Module.bsl` and the metadata file differs.

| Format | Module code | Metadata file |
|--------|-------------|-----------------|
| EDT | `exts/TESTS/src/CommonModules/<ModuleName>/Module.bsl` | `exts/TESTS/src/CommonModules/<ModuleName>/<ModuleName>.mdo` |
| DESIGNER | `exts/TESTS/src/CommonModules/<ModuleName>/Ext/Module.bsl` | `exts/TESTS/src/CommonModules/<ModuleName>.xml` |

If the format is unclear — check `application-*.yml` / `yaxunit-*.yml` in the project root or ask the user.

Do not mix structures: DESIGNER requires `Ext/`, EDT does not use it.

Make sure the new module is registered in `Configuration.[mdo|xml]`, otherwise the runner will not pick up the test.

---

Structure for EDT (the most common case):

```
exts/TESTS/
  src/
    CommonModules/
      <ModuleName>/
        <ModuleName>.mdo
        Module.bsl
```

Structure for DESIGNER:

```
exts/TESTS/
  src/
    CommonModules/
      <ModuleName>.xml
      <ModuleName>/
        Ext/
          Module.bsl
```

---

**Typical test path in the repository:** `exts/TESTS/src/CommonModules/<ModuleName>/Module.bsl` (or `.../Ext/Module.bsl` for DESIGNER).

---

**Rules for working with test source code:**

| Action | Path |
|--------|------|
| Create a new test module | `exts/TESTS/src/CommonModules/<ModuleName>/` |
| Find an existing test by name | `exts/TESTS/src/CommonModules/<ModuleName>/Module.bsl` or `.../Ext/Module.bsl` |
| Read tests before analyzing | look in `exts/TESTS/`, not in the main `src/` |

> **Important:** test source code is placed only in `exts/TESTS/**`. Never place tests in the main configuration or other extensions.
>
> **Absolute restriction:** `exts/YAXUNIT/**` is never modified manually by the agent — it is the runner infrastructure.

---

## Naming Test Modules

Recommended template for naming a shared test module:

```
<Prefix>_<ObjectName>[_<Suffix>]
```

### Prefixes by object type

| Object type | Prefix | Example |
|-------------|--------|---------|
| Common module | `ОМ_` | `ОМ_ОбщегоНазначения` |
| Document | `Док_` | `Док_ПоступлениеТоваров` |
| Catalog | `Спр_` | `Спр_Контрагенты` |
| Accumulation register | `РН_` | `РН_ОстаткиТоваров` |
| Information register | `РС_` | `РС_КурсыВалют` |
| Accounting register | `РБ_` | `РБ_Хозрасчетный` |
| Calculation register | `РР_` | `РР_Начисления` |
| Report | `Отч_` | `Отч_Продажи` |
| Processing | `Обр_` | `Обр_ЗакрытиеМесяца` |

### Suffixes by module type

| Module type | Suffix | Example |
|-------------|--------|---------|
| Object module | `_МО` | `Спр_Контрагенты_МО` |
| Manager module | `_ММ` | `РН_ОстаткиТоваров_ММ` |
| Record set module | `_НЗ` | `РБ_Хозрасчетный_НЗ` |

If a common module is tested — usually no suffix is needed.

---

**Quick naming template for procedures:**
- module: `Тесты<SubsystemOrModuleName>`
- test: `Тест<Scenario>`
- suite: by functionality (`"Остатки"`, `"Перемещение"`).

---

**Useful:** see the extended list of assertions and predicates in [`references/yaxunit-cheatsheet.md`](references/yaxunit-cheatsheet.md).

---

## Test Module Structure

A test is a **common module** of the configuration. An exported procedure `ИсполняемыеСценарии` is mandatory. Its only job is to register tests; no data, no initialization logic.

```bsl
// Общий модуль: ТестыУправлениеСкладом
// Свойства: Сервер = Истина, Клиент (управляемое приложение) = по необходимости

Процедура ИсполняемыеСценарии() Экспорт

    ЮТТесты
        .ДобавитьТестовыйНабор("Остатки")
            .ДобавитьСерверныйТест("ТестПолучитьОстатки")
            .ДобавитьСерверныйТест("ТестОстатокПустойСклад")
        .ДобавитьТестовыйНабор("Перемещение")
            .ДобавитьСерверныйТест("ТестПеремещениеМеждуСкладами");

КонецПроцедуры
```

### Execution contexts

| Method | Where it runs |
|--------|---------------|
| `ДобавитьТест` | default context |
| `ДобавитьСерверныйТест` | &НаСервереБезКонтекста |
| `ДобавитьКлиентскийТест` | &НаКлиенте |

---

## Implementing a Test

A test is an exported parameterless procedure (if not parameterized):

```bsl
Процедура ТестПолучитьОстатки() Экспорт

    // Arrange — preparing data
    Склад = ЮТест.Данные().СоздатьЭлемент("Справочник.Склады");
    НоменклатураСсылка = ЮТест.Данные().СоздатьЭлемент("Справочник.Номенклатура");

    // Act — invoking the code under test
    Остаток = УправлениеСкладом.ПолучитьОстаток(НоменклатураСсылка, Склад);

    // Assert — checking the result
    ЮТест.ОжидаетЧто(Остаток).Равно(0);

КонецПроцедуры
```

**Rule:** one test verifies one assertion. If multiple checks are needed — create multiple tests.

---

## Assertions (ЮТест.ОжидаетЧто)

Entry point: `ЮТест.ОжидаетЧто(Value)` — returns an assertion builder.

### Basic comparisons

```bsl
ЮТест.ОжидаетЧто(Результат).Равно(42);
ЮТест.ОжидаетЧто(Результат).НеРавно(0);
ЮТест.ОжидаетЧто(Результат).Больше(10);
ЮТест.ОжидаетЧто(Результат).БольшеИлиРавно(10);
ЮТест.ОжидаетЧто(Результат).Меньше(100);
ЮТест.ОжидаетЧто(Результат).МеньшеИлиРавно(100);
ЮТест.ОжидаетЧто(Флаг).ЭтоИстина();
ЮТест.ОжидаетЧто(Флаг).ЭтоЛожь();
ЮТест.ОжидаетЧто(Значение).ВСписке(МассивДопустимых);
```

### Type and non-empty checks

```bsl
ЮТест.ОжидаетЧто(Ссылка).ИмеетТип("СправочникСсылка.Номенклатура");
ЮТест.ОжидаетЧто(Значение).НеЯвляетсяНеопределено();
ЮТест.ОжидаетЧто(Строка).НеПусто();
```

### Exception assertions

```bsl
// Expect the code to throw an exception
ЮТест.ОжидаетЧто(ЭтотОбъект).МетодВыбрасываетИсключение("МетодСОшибкой", Параметры);
```

### Assertions for database data

```bsl
// Check that a record exists in the database
ЮТест.ОжидаетЧтоТаблицаБазы("Справочник.Склады")
    .СодержитЗаписи()
    .ГдеРеквизит("Наименование").Равно("Основной склад");
```

---

## Test Data (ЮТест.Данные)

### Creating database objects

```bsl
// Create a catalog item with minimal filling (stub)
Склад = ЮТест.Данные().СоздатьЭлемент("Справочник.Склады");

// Create with required attributes via constructor
Номенклатура = ЮТест.Данные()
    .КонструкторОбъекта("Справочник.Номенклатура")
    .Установить("Наименование", "Тестовый товар")
    .Установить("ЕдиницаИзмерения", ПредопределённыйЭлемент("Классификатор.ЕдиницыИзмерения.Штука"))
    .Записать()
    .Ссылка();

// Create a document
Документ = ЮТест.Данные().СоздатьДокумент("Документ.ПоступлениеТоваров");
```

### Managing test data

Data created via `ЮТест.Данные()` is **automatically deleted** after the test if `УдалениеТестовыхДанных` is enabled in the registration. Do not create data in `ИсполняемыеСценарии`.

---

## Mocking (Мокито)

Use mocking to isolate the code under test from external dependencies — HTTP services, other modules, heavy queries.

### Pattern: Arrange → Act → Assert

```bsl
Процедура ТестРасчётСкидки() Экспорт

    // Arrange — configure method behavior
    Мокито.Обучение(МодульСкидок)
        .Когда().ПолучитьПроцентСкидки(Клиент)
        .Вернуть(15);

    // Act — invoke the code under test
    Результат = УправлениеПродажами.РассчитатьСумму(100, Клиент);

    // Assert — ensure the result
    ЮТест.ОжидаетЧто(Результат).Равно(85);

    // Optional: verify that the method was called
    Мокито.Проверить(МодульСкидок).ПолучитьПроцентСкидки(Клиент);

КонецПроцедуры
```

### Mocking scenarios

```bsl
// Return a value
Мокито.Обучение(Модуль).Когда().МетодА(Параметр).Вернуть(42);

// Throw an exception
Мокито.Обучение(Модуль).Когда().МетодБ(Параметр).ВыброситьИсключение("Текст ошибки");

// Skip (do nothing)
Мокито.Обучение(Модуль).Когда().МетодВ().Пропустить();

// Only observe (collect statistics without changing behavior)
Мокито.Обучение(Модуль).Когда().МетодГ().Наблюдать();
```

---

## Lifecycle and Event Handlers

Handlers allow executing code before/after a suite or each test:

```bsl
Процедура ИсполняемыеСценарии() Экспорт

    ЮТТесты
        .ДобавитьТестовыйНабор("Расчёты")
            // Before all tests in the suite
            .Перед("ПередНаборомРасчёты")
            // After each test
            .После("ПослеКаждогоТестаОчистка")
            .ДобавитьСерверныйТест("ТестРасчётА")
            .ДобавитьСерверныйТест("ТестРасчётБ");

КонецПроцедуры

Процедура ПередНаборомРасчёты() Экспорт
    // Initialize the suite environment
    ЮТест.Контекст().УстановитьЗначение("Ставка", 18);
КонецПроцедуры

Процедура ПослеКаждогоТестаОчистка() Экспорт
    // Cleanup after each test
КонецПроцедуры
```

### Context — sharing data between methods

```bsl
// Save to the test context
ЮТест.Контекст().УстановитьЗначение("МоёЗначение", Данные);

// Read from the context (searches all levels: test → suite → module)
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

## Rules and Antipatterns

| ❌ Antipattern | ✅ Correct |
|----------------|-----------|
| Creating data in `ИсполняемыеСценарии` | Create data in the test body or `Перед` handler |
| One test checks 10 conditions | One test — one assertion |
| Test depends on execution order | Each test is isolated, does not depend on others |
| Hardcoding references to database objects | Create data via `ЮТест.Данные()` |
| Testing private logic directly | Test via the module's public interface |
| Mocking the module under test | Mock only the *dependencies* of the code under test |

---

depends_on: []
---
