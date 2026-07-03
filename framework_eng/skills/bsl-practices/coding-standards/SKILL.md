---
name: coding-standards
description: "When writing or reviewing BSL, apply 1C standards"
alwaysApply: false
---

# BSL Coding Standards (1C)

## Rule 1: Variable naming - CamelCase in Russian

ITS standard: "Module texts" - names in Russian, CamelCase.

| Element | Format | Example |
|---------|--------|--------|
| Variable | NounOrPhrase | `КоличествоСтрок`, `ДатаНачалаПериода` |
| Procedure | VerbPhrase | `ЗаполнитьТабличнуюЧасть`, `УстановитьОтбор` |
| Function | NounOrQuestion | `ПолучитьСписокДокументов`, `ЭтоНовый` |
| Boolean variable | Affirmative form | `ЭтоНовый`, `РазрешеноРедактирование`, `ЕстьОшибки` |
| Parameter | AsVariable | `ДокументСсылка`, `РежимОткрытия` |

```bsl
Процедура ЗаполнитьТабличнуюЧастьТовары(ДокументОбъект, ДанныеЗаполнения)

    КоличествоСтрок = ДанныеЗаполнения.Количество();
    ЕстьОшибки = Ложь;

    Для Каждого СтрокаДанных Из ДанныеЗаполнения Цикл
        НоваяСтрока = ДокументОбъект.Товары.Добавить();
        НоваяСтрока.Номенклатура = СтрокаДанных.Номенклатура;
        НоваяСтрока.Количество = СтрокаДанных.Количество;
    КонецЦикла;

КонецПроцедуры
```

---

## Rule 2: Module structure - interface and implementation sections

ITS standard: "Module structure" - regions (#Region) in a defined order.

### Section order for a common module

```bsl
#Область ПрограммныйИнтерфейс

// Экспортные процедуры и функции — публичный API модуля.

Функция ПолучитьКурсВалюты(Валюта, ДатаКурса) Экспорт
    // ...
КонецФункции

#КонецОбласти

#Область СлужебныйПрограммныйИнтерфейс

// Экспортные процедуры для вызова только из других модулей данной подсистемы.

Функция ПересчитатьКурсВнутренний(ПараметрыПересчета) Экспорт
    // ...
КонецФункции

#КонецОбласти

#Область СлужебныеПроцедурыИФункции

// Внутренняя реализация. Не экспортные.

Функция СформироватьЗапросКурса(Валюта, Дата)
    // ...
КонецФункции

#КонецОбласти
```

### Order of sections in the object module

```bsl
#Область ОписаниеПеременных

Перем МассивИзменённыхРеквизитов;

#КонецОбласти

#Область ОбработчикиСобытий

Процедура ОбработкаЗаполнения(ДанныеЗаполнения, ТекстЗаполнения, СтандартнаяОбработка)
    // ...
КонецПроцедуры

Процедура ПередЗаписью(Отказ)
    // ...
КонецПроцедуры

#КонецОбласти

#Область СлужебныеПроцедурыИФункции
#КонецОбласти

#Область Инициализация

МассивИзменённыхРеквизитов = Новый Массив;

#КонецОбласти
```

---

## Rule 3: Compilation directives — &НаКлиенте, &НаСервере, &НаСервереБезКонтекста

When calling `&НаСервере`, the platform serializes **the entire form context** back and forth. `&НаСервереБезКонтекста` transfers only parameters — radically less traffic.

| Directive | Where it runs | Access to form data | When to use |
|-----------|----------------|----------------------|-------------------|
| `&НаКлиенте` | Client (thin/web) | Yes (client copy) | Interactive logic: dialogs, navigation |
| `&НаСервере` | Server | Yes (full form context) | Access to form attributes + database is needed |
| `&НаСервереБезКонтекста` | Server | No | Database queries, calculations without form data |
| `&НаКлиентеНаСервереБезКонтекста` | Both client and server | No | Pure calculations, validation without database |

---

## Rule 6: Do not shadow the global context

A local variable with the same name as a global collection hides the manager, so later references to it in code will cause an error.

```bsl
// Correct — a specific name
МассивДокументовКОбработке = Новый Массив;
СправочникНоменклатура = Справочники.Номенклатура;
```

### List of reserved names (do not use for variables)

`Документы`, `Справочники`, `Регистры`, `Перечисления`, `ПланыОбмена`, `ПланыВидовХарактеристик`, `ПланыВидовРасчета`, `ПланыСчетов`, `БизнесПроцессы`, `Задачи`, `Обработки`, `Отчеты`, `Константы`, `ПараметрыСеанса`, `РегистрыСведений`, `РегистрыНакопления`, `РегистрыБухгалтерии`, `РегистрыРасчета`

---

## Rule 7: String concatenation — do not use `+` in loops

In BSL, strings are immutable. `Строка1 + Строка2` in a loop with N iterations gives O(N^2) in memory and time — each iteration copies everything that came before.

ITS standard: "Efficient Working with Strings".

```bsl
// O(N) — array + СтрСоединить()
ЧастиСтроки = Новый Массив;
Для Каждого Элемент Из КоллекцияДанных Цикл
    ЧастиСтроки.Добавить(Элемент.Наименование);
КонецЦикла;
РезультатСтрока = СтрСоединить(ЧастиСтроки, ", ");

// For a fixed number of substitutions — СтрШаблон() (up to 10 parameters)
ТекстСообщения = СтрШаблон(
    НСтр("ru = 'Документ %1 от %2 на сумму %3 руб.'"),
    НомерДокумента,
    Формат(ДатаДокумента, "ДЛФ=D"),
    Формат(Сумма, "ЧДЦ=2"));
```

---

## Rule 8: Regions (#Область) for organizing code

ITS standard: "Module Structure" — mandatory standard regions.

### Standard regions for a form module

```bsl
#Область ОписаниеПеременных
#КонецОбласти

#Область ОбработчикиСобытийФормы
#КонецОбласти

#Область ОбработчикиСобытийЭлементовШапкиФормы
#КонецОбласти

#Область ОбработчикиСобытийЭлементовТаблицыФормыТовары
#КонецОбласти

#Область ОбработчикиКомандФормы
#КонецОбласти

#Область СлужебныеПроцедурыИФункции
#КонецОбласти
```

Rules: do not nest deeper than 2 levels; do not create empty regions; use the standard names from ITS (the IDE and analysis tools rely on them).

---

## Rule 9: Comments - explain the "why", not the "what"

ITS standard: "Description of procedures and functions" - exported procedures MUST have a descriptive comment.

### Description of an exported function

```bsl
// Возвращает курс валюты на указанную дату.
// Если на указанную дату курс не установлен, возвращает курс на ближайшую предыдущую дату.
//
// Параметры:
//  Валюта   - СправочникСсылка.Валюты - валюта, курс которой нужно получить.
//  ДатаКурса - Дата - дата, на которую нужен курс.
//              Если не указана, используется текущая дата сеанса.
//
// Возвращаемое значение:
//  Число - курс валюты. 0 если курс не найден.
//
Функция ПолучитьКурсВалюты(Валюта, ДатаКурса = Неопределено) Экспорт
```

### Comment on the "why"

```bsl
// Сумму округляем до копеек, потому что бухгалтерский учёт не допускает дробных копеек,
// а при пересчёте НДС могут возникнуть дроби.
СуммаНДС = Окр(СуммаБезНДС * СтавкаНДС / 100, 2);
```

---

## Rule 10: Use НСтр() for string literals

All strings displayed to the user are wrapped in `НСтр()` for localization.

ITS standard: "Using the НСтр() function".

```bsl
ТекстПредупреждения = НСтр("ru = 'Документ не может быть проведён. Не заполнена дата.'");

// С параметрами — НСтр() + СтрШаблон()
ТекстСообщения = СтрШаблон(
    НСтр("ru = 'Остаток товара ""%1"" на складе: %2 %3'"),
    Номенклатура,
    Остаток,
    ЕдиницаИзмерения);
```

---

## Rule 11: One procedure - one responsibility

A procedure longer than 100 lines is a signal to decompose it. Splitting it into small functions with descriptive names makes the code self-documenting.

```bsl
Процедура ОбработкаПроведения(Отказ, РежимПроведения)

    ИнициализироватьДанныеДокумента();
    ПроверитьЗаполнениеРеквизитов(Отказ);

    Если Не Отказ Тогда
        СформироватьДвижения(Отказ);
    КонецЕсли;

КонецПроцедуры
```

---

## Rule 12: Explicitly type parameters in comments

BSL is dynamically typed. Describing types in a comment for an exported function is the only way to document the contract.

```bsl
// Создаёт новый элемент справочника «Номенклатура» с заполнением по умолчанию.
//
// Параметры:
//  ДанныеЗаполнения - Структура - содержит поля:
//    * Наименование      - Строка - наименование номенклатуры (обязательно).
//    * ВидНоменклатуры    - ПеречислениеСсылка.ВидыНоменклатуры - вид (обязательно).
//    * ЕдиницаИзмерения  - СправочникСсылка.ЕдиницыИзмерения - ед. изм. Необязательно,
//                          по умолчанию «шт.».
//    * Артикул            - Строка - артикул. Необязательно.
//
// Возвращаемое значение:
//  СправочникСсылка.Номенклатура - ссылка на созданный элемент.
//
Функция СоздатьНоменклатуру(ДанныеЗаполнения) Экспорт
```

---

## Rule 13: Do not use `Execute()` and `Evaluate()` unless absolutely necessary

Security threat (analogous to eval), invisible to static analysis, hard to debug.

```bsl
// Правильно — прямой вызов через метаданные
МенеджерОбъекта = ОбщегоНазначения.МенеджерОбъектаПоСсылке(СсылкаНаОбъект);
```

---

## Rule 14: Magic numbers and strings - move them into parameters

Hardcoded values are unclear, duplicated, and not configurable.

```bsl
// Правильно — перечисление, значение самодокументировано
Если Документ.Статус = Перечисления.СтатусыДокументов.Согласован Тогда
    // ...
КонецЕсли;

// Или константа для настраиваемых значений
МаксимальноеКоличествоПопыток = Константы.МаксимальноеКоличествоПопытокОтправки.Получить();
```

---

## Rule 15: Explicit JOINs instead of dotted notation through references

Reference chains create implicit JOINs. For composite types, the platform performs a JOIN to **all** possible tables.

```bsl
// Правильно — один запрос с явными JOIN
Запрос = Новый Запрос;
Запрос.Текст =
"ВЫБРАТЬ
|   Товары.Номенклатура КАК Номенклатура,
|   Товары.Номенклатура.ВидНоменклатуры КАК ВидНоменклатуры,
|   Товары.Номенклатура.ЕдиницаИзмерения КАК ЕдиницаИзмерения
|ИЗ
|   Документ.РеализацияТоваровУслуг.Товары КАК Товары
|ГДЕ
|   Товары.Ссылка = &ДокументСсылка";
```

### Incorrect — dot access inside a loop (N+1)

```bsl
Для Каждого СтрокаТоваров Из Документ.Товары Цикл
    ВидНоменклатуры = СтрокаТоваров.Номенклатура.ВидНоменклатуры;
    ЕдиницаИзмерения = СтрокаТоваров.Номенклатура.ЕдиницаИзмерения;
КонецЦикла;
```

---

## Rule 16: Open forms through ОткрытьФорму()

`ПолучитьФорму()` is available in client code of a managed application, but it only creates the form and returns it without showing it, leaving lifecycle management to the developer. To open a form, prefer `ОткрытьФорму()` - it creates and shows the form in a single call.

```bsl
ПараметрыФормы = Новый Структура;
ПараметрыФормы.Вставить("Ключ", ДокументСсылка);

ОткрытьФорму("Документ.РеализацияТоваровУслуг.ФормаОбъекта",
    ПараметрыФормы,
    ЭтотОбъект,
    ,
    ,
    ,
    Новый ОписаниеОповещения("ПослеЗакрытияФормыДокумента", ЭтотОбъект));
```

---

## Rule 17: Business logic should not live in the form module

Place write and validation logic in the object module - for testability and reuse.

```bsl
// Модуль объекта документа
Процедура ПередЗаписью(Отказ)
    Для Каждого Строка Из Товары Цикл
        Строка.Сумма = Строка.Количество * Строка.Цена;
    КонецЦикла;
КонецПроцедуры
```

---

## Rule 18: Do not swallow exceptions

See `error-handling`, rule 1.

---

## Rule 19: Aggregate server calls from the form

See `form-patterns`.

---

## Verification via Buddy

- **Code check for standards and BSP analogs:** `ask_ai_assistant` (VALIDATE_BSL template from `buddy-prompting`). Pass the code fragment and get standard violations plus recommendations for replacing them with BSP/platform methods.
- **Standard check against the source:** `ask_ai_assistant` (SEARCH_ITS template from `buddy-prompting`). If the skill conflicts with ITS, ITS takes priority.

---
depends_on: []
---
