---
name: test-writing
description: Writing YaxUnit tests (BSL). The skill teaches the agent to create test modules for the YaxUnit framework — test registration, assertions, mocking, and test data.
---

# Writing YaxUnit tests (BSL)

## Purpose

The skill teaches the agent to **write tests in BSL** for the YaxUnit framework. It covers the structure of a test module, the assertion API, mocking, and creating test data.

Running the written tests is covered by a separate skill [`test-execution`](../../tool-usage/test-execution/SKILL.md).

**Full YaxUnit documentation:** see [`references/yaxunit-cheatsheet.md`](references/yaxunit-cheatsheet.md)

---

## Test location in the project

Tests are stored in a **separate configuration extension** at the path:

```
<project root>/exts/TESTS/
```

The extension structure is standard for 1С:EDT:

```
exts/TESTS/
  src/
    CommonModules/          ← тестовые общие модули (ТестыXxx)
    ...
```

**Rules for working with test sources:**

| Action | Path |
|--------|------|
| Create a new test module | `exts/TESTS/src/CommonModules/<ИмяМодуля>/` |
| Find an existing test by name | `exts/TESTS/src/CommonModules/<ИмяМодуля>/Module.bsl` |
| Read tests before analysis | look in `exts/TESTS/`, not in the main `src/` |

> **Important:** never place tests in the main configuration or other extensions. Only `exts/TESTS/`.

---

## Test module structure

A test is a **common module** of the configuration. An exported procedure `ИсполняемыеСценарии` is mandatory. Its sole purpose is to register tests; no data, no initialization logic.

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

| Method | Where executed |
|--------|----------------|
| `ДобавитьТест` | default context |
| `ДобавитьСерверныйТест` | &НаСервереБезКонтекста |
| `ДобавитьКлиентскийТест` | &НаКлиенте |

---

## Implementing a test

A test is an exported procedure without parameters (if not parameterized):

```bsl
Процедура ТестПолучитьОстатки() Экспорт

    // Arrange — подготовка данных
    Склад = ЮТест.Данные().СоздатьЭлемент("Справочник.Склады");
    НоменклатураСсылка = ЮТест.Данные().СоздатьЭлемент("Справочник.Номенклатура");

    // Act — вызов тестируемого кода
    Остаток = УправлениеСкладом.ПолучитьОстаток(НоменклатураСсылка, Склад);

    // Assert — проверка результата
    ЮТест.ОжидаетЧто(Остаток).Равно(0);

КонецПроцедуры
```

**Rule:** one test verifies one assertion. If you need to verify several, create multiple tests.

---

## Assertions (ЮТест.ОжидаетЧто)

Entry point: `ЮТест.ОжидаетЧто(Значение)` — returns an assertion builder.

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

### Type and completeness checks

```bsl
ЮТест.ОжидаетЧто(Ссылка).ИмеетТип("СправочникСсылка.Номенклатура");
ЮТест.ОжидаетЧто(Значение).НеЯвляетсяНеопределено();
ЮТест.ОжидаетЧто(Строка).НеПусто();
```

### Exception checking

```bsl
// Ожидаем, что код выбросит исключение
ЮТест.ОжидаетЧто(ЭтотОбъект).МетодВыбрасываетИсключение("МетодСОшибкой", Параметры);
```

### Assertions for infobase data

```bsl
// Проверить, что запись существует в базе
ЮТест.ОжидаетЧтоТаблицаБазы("Справочник.Склады")
    .СодержитЗаписи()
    .ГдеРеквизит("Наименование").Равно("Основной склад");
```

---

## Test data (ЮТест.Данные)

### Creating infobase objects

```bsl
// Создать элемент справочника с минимальным заполнением (пустышка)
Склад = ЮТест.Данные().СоздатьЭлемент("Справочник.Склады");

// Создать с нужными реквизитами через конструктор
Номенклатура = ЮТест.Данные()
    .КонструкторОбъекта("Справочник.Номенклатура")
    .Установить("Наименование", "Тестовый товар")
    .Установить("ЕдиницаИзмерения", ПредопределённыйЭлемент("Классификатор.ЕдиницыИзмерения.Штука"))
    .Записать()
    .Ссылка();

// Создать документ
Документ = ЮТест.Данные().СоздатьДокумент("Документ.ПоступлениеТоваров");
```

### Managing test data

Data created through `ЮТест.Данные()` is **automatically deleted** after the test if `УдалениеТестовыхДанных` is configured in the registration. Do not create data in `ИсполняемыеСценарии`.

---

## Mocking (Мокито)

Use mocking to isolate the tested code from external dependencies — HTTP services, other modules, heavy queries.

### Pattern: Train → Run → Verify

```bsl
Процедура ТестРасчётСкидки() Экспорт

    // Обучение — настраиваем поведение метода
    Мокито.Обучение(МодульСкидок)
        .Когда().ПолучитьПроцентСкидки(Клиент)
        .Вернуть(15);

    // Прогон — вызываем тестируемый код
    Результат = УправлениеПродажами.РассчитатьСумму(100, Клиент);

    // Проверка — убеждаемся в результате
    ЮТест.ОжидаетЧто(Результат).Равно(85);

    // Опционально: проверить, что метод был вызван
    Мокито.Проверить(МодульСкидок).ПолучитьПроцентСкидки(Клиент);

КонецПроцедуры
```

### Mocking scenarios

```bsl
// Вернуть значение
Мокито.Обучение(Модуль).Когда().МетодА(Параметр).Вернуть(42);

// Выбросить исключение
Мокито.Обучение(Модуль).Когда().МетодБ(Параметр).ВыброситьИсключение("Текст ошибки");

// Пропустить (ничего не делать)
Мокито.Обучение(Модуль).Когда().МетодВ().Пропустить();

// Только наблюдать (собирать статистику без изменения поведения)
Мокито.Обучение(Модуль).Когда().МетодГ().Наблюдать();
```

---

## Lifecycle and event handlers

Handlers allow code to run before/after a set or every test:

```bsl
Процедура ИсполняемыеСценарии() Экспорт

    ЮТТесты
        .ДобавитьТестовыйНабор("Расчёты")
            // Перед всеми тестами набора
            .Перед("ПередНаборомРасчёты")
            // После каждого теста
            .После("ПослеКаждогоТестаОчистка")
            .ДобавитьСерверныйТест("ТестРасчётА")
            .ДобавитьСерверныйТест("ТестРасчётБ");

КонецПроцедуры

Процедура ПередНаборомРасчёты() Экспорт
    // Инициализация окружения для набора
    ЮТест.Контекст().УстановитьЗначение("Ставка", 18);
КонецПроцедуры

Процедура ПослеКаждогоТестаОчистка() Экспорт
    // Очистка после каждого теста
КонецПроцедуры
```

### Context — passing data between methods

```bsl
// Сохранить в контекст теста
ЮТест.Контекст().УстановитьЗначение("МоёЗначение", Данные);

// Прочитать из контекста (ищет по всем уровням: тест → набор → модуль)
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

## Rules and anti-patterns

| ❌ Anti-pattern | ✅ Correct |
|----------------|------------|
| Creating data in `ИсполняемыеСценарии` | Create data in the test body or `Перед` handler |
| One test verifies 10 conditions | One test — one assertion |
| Test depends on execution order | Every test is isolated, does not rely on others |
| Hardcoding infobase object references | Create data through `ЮТест.Данные()` |
| Testing private logic directly | Test via the public interface of the module |
| Mocking the module under test | Mock only the *dependencies* of the tested code |

---
depends_on: []
---
