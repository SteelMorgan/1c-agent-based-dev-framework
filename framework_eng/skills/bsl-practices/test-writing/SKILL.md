---
name: test-writing
description: "Writing YaxUnit (BSL) tests. The skill teaches an agent to create test modules for the YaxUnit framework - test registration, assertions, mocking, test data."
---

# Writing YaxUnit (BSL) Tests

Running the written tests is a separate skill [`test-execution`](../../tool-usage/test-execution/SKILL.md).

**Full YaxUnit documentation:** see [`references/yaxunit-cheatsheet.md`](references/yaxunit-cheatsheet.md)

---

## Test Placement in the Project

Tests are stored in a **separate configuration extension**: `<project-root>/exts/TESTS/`

### Structure options: EDT and DESIGNER

| Format | Module code | Metadata file |
|--------|-------------|---------------|
| EDT | `exts/TESTS/src/CommonModules/<ModuleName>/Module.bsl` | `.../<ModuleName>.mdo` |
| DESIGNER | `exts/TESTS/src/CommonModules/<ModuleName>/Ext/Module.bsl` | `.../<ModuleName>.xml` |

If the format is unclear - check `application-*.yml` / `yaxunit-*.yml` in the project root.

Do not mix structures: DESIGNER requires `Ext/`, EDT does not use it.

A new module must be registered in `Configuration.[mdo|xml]`, otherwise the runner will not pick up the test.

**Strict prohibitions:**
- Test sources are placed only in `exts/TESTS/**`, never in the main configuration
- `exts/YAXUNIT/**` is never modified manually - it is runner infrastructure

---

## Naming Test Modules

Pattern: `<Prefix>_<ObjectName>[_<Suffix>]`

### Prefixes by object type

| Object type | Prefix | Example |
|-------------|--------|---------|
| Общий модуль | `ОМ_` | `ОМ_ОбщегоНазначения` |
| Документ | `Док_` | `Док_ПоступлениеТоваров` |
| Справочник | `Спр_` | `Спр_Контрагенты` |
| Регистр накопления | `РН_` | `РН_ОстаткиТоваров` |
| Регистр сведений | `РС_` | `РС_КурсыВалют` |
| Обработка | `Обр_` | `Обр_ЗакрытиеМесяца` |

### Suffixes by module type

| Module type | Suffix | Example |
|-------------|--------|---------|
| Модуль объекта | `_МО` | `Спр_Контрагенты_МО` |
| Модуль менеджера | `_ММ` | `РН_ОстаткиТоваров_ММ` |
| Модуль набора записей | `_НЗ` | `РБ_Хозрасчетный_НЗ` |

---

## Test Module Structure

Mandatory: exported procedure `ИсполняемыеСценарии`. Only test registration - no data, no logic.

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

| Method | Execution context |
|--------|------------------|
| `ДобавитьТест` | default context |
| `ДобавитьСерверныйТест` | &НаСервереБезКонтекста |
| `ДобавитьКлиентскийТест` | &НаКлиенте |

---

## Test Implementation

One test verifies a single assertion. Arrange-Act-Assert pattern:

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

// Type and presence
ЮТест.ОжидаетЧто(Ссылка).ИмеетТип("СправочникСсылка.Номенклатура");
ЮТест.ОжидаетЧто(Значение).НеЯвляетсяНеопределено();

// Exceptions
ЮТест.ОжидаетЧто(ЭтотОбъект).МетодВыбрасываетИсключение("МетодСОшибкой", Параметры);

// Database data
ЮТест.ОжидаетЧтоТаблицаБазы("Справочник.Склады")
    .СодержитЗаписи()
    .ГдеРеквизит("Наименование").Равно("Основной склад");
```

---

## Test Data (ЮТест.Данные)

```bsl
// Empty stub
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

Data created via `ЮТест.Данные()` are **automatically deleted** after the test. Do not create data inside `ИсполняемыеСценарии`.

---

## Mocking (Mockito)

Pattern: Train -> Run -> Verify.

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

### Context - passing data between methods

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

## Anti-patterns

| Anti-pattern | Correct approach |
|-------------|------------------|
| Data in `ИсполняемыеСценарии` | Data inside the test body or `Перед` handler |
| One test checks 10 conditions | One test is one assertion |
| Test depends on execution order | Each test is isolated |
| Hardcoding links to IB objects | Create through `ЮТест.Данные()` |
| Testing private logic | Test through the public interface |
| Mocking the tested module | Mock only *dependencies* |

---

## TDD Layers and Role Boundaries

Tests and implementation are written by **different agents** in **different phases**. The test author does not know the implementation, and the code author does not modify the tests.

```
Phase 3a: Scenario-Author  → .feature (BDD)   ┐ параллельно
Phase 3b: Developer-Tests  → unit-тесты (Red)  ┘
Phase 3c: Developer-Code   → код (Green)
Phase 4:  Tester           → edge cases, регрессия, BDD + unit
```

### Test Layers

| Layer | Phase | Agent | Covers |
|------|------|-------|-----------|
| BDD (acceptance) | 3a | Scenario-Author | Behavior through UI |
| TDD (unit) | 3b | Developer-Tests | Public methods, MUST scenarios, basic negatives |
| TDD (green) | 3c | Developer-Code | Implementation that passes unit tests |
| Coverage | 4 | Tester | Edge cases, integration, regression |

Phases 3a and 3b are **parallel**. Phase 3c starts after both are complete.

### Agent Boundaries

- **Scenario-Author:** does NOT write unit tests, does NOT run scenarios, does NOT expand beyond the specification
- **Developer-Tests:** MUST scenarios + basic negatives; does NOT cover combinatorial edge cases or integration
- **Tester:** extends coverage; does NOT duplicate Developer tests; does NOT edit BSL code

### Rule When a Tester Test Fails

```
Тест упал
  ├── Ошибка в тесте → Tester исправляет (test_error)
  └── Баг в коде → СТОП. Метка implementation_error + описание.
                   Оркестратор возвращает задачу Developer.
```

**User/Role context in Test Plan:** if code uses `SetPrivilegedMode`, role checks (`AccessRight`, `RoleAvailable`), or the result depends on the current user, the specification MUST explicitly state for each test in the "Test Plan" section: user name/role set, required mode (privileged or not), expected result (success/failure). Without this, a test under a full-rights runner (for example `AgentAI`) will produce a false positive: it will pass "by coincidence" through the privileged branch without checking role-dependent behavior. If this is technically impossible for unit tests, record it in the spec as a separate ADR and move it to integration scope (Phase 4).

---
depends_on: []
---
