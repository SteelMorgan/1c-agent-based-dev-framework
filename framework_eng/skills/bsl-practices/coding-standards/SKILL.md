---
name: coding-standards
description: "BSL coding standards (1C). This skill teaches the agent to write code in the built-in 1C language (BSL) in accordance with the standards of the 1C:Enterprise platform and ITS recommendations."
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
| Parameter | LikeVariable | `ДокументСсылка`, `РежимОткрытия` |

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

ITS standard: "Module structure" - regions (#Область) in a specific order.

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

### Section order for an object module

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

## Rule 3: Compilation directives - &НаКлиенте, &НаСервере, &НаСервереБезКонтекста

When `&НаСервере` is called, the platform serializes **the entire form context** back and forth. `&НаСервереБезКонтекста` passes only parameters - drastically less traffic.

| Directive | Where it runs | Access to form data | When to use |
|-----------|----------------|----------------------|-------------------|
| `&НаКлиенте` | Client (thin/web) | Yes (client copy) | Interactive logic: dialogs, navigation |
| `&НаСервере` | Server | Yes (full form context) | Need access to form attributes + database |
| `&НаСервереБезКонтекста` | Server | No | Database queries, calculations without form data |
| `&НаКлиентеНаСервереБезКонтекста` | Both client and server | No | Pure calculations, validation without database |

```bsl
&НаКлиенте
Процедура НоменклатураПриИзменении(Элемент)
    ДанныеНоменклатуры = ПолучитьДанныеНоменклатуры(Элементы.Товары.ТекущиеДанные.Номенклатура);
    ЗаполнитьСтрокуТоваровНаКлиенте(ДанныеНоменклатуры);
КонецПроцедуры

&НаСервереБезКонтекста
Функция ПолучитьДанныеНоменклатуры(НоменклатураСсылка)

    Возврат Новый Структура("ЕдиницаИзмерения, Цена, СтавкаНДС",
        НоменклатураСсылка.ЕдиницаИзмерения,
        НоменклатураСсылка.Цена,
        НоменклатураСсылка.СтавкаНДС);

КонецФункции
```

---

## Rule 6: Do not shadow the global context

A local variable with the name of a global collection hides the manager - later references to it in the code will fail.

```bsl
// Правильно — конкретное имя
МассивДокументовКОбработке = Новый Массив;
СправочникНоменклатура = Справочники.Номенклатура;
```

### List of reserved names (do not use for variables)

`Документы`, `Справочники`, `Регистры`, `Перечисления`, `ПланыОбмена`, `ПланыВидовХарактеристик`, `ПланыВидовРасчета`, `ПланыСчетов`, `БизнесПроцессы`, `Задачи`, `Обработки`, `Отчеты`, `Константы`, `ПараметрыСеанса`, `РегистрыСведений`, `РегистрыНакопления`, `РегистрыБухгалтерии`, `РегистрыРасчета`

---

## Rule 7: String concatenation - do not use `+` in loops

In BSL, strings are immutable. `Строка1 + Строка2` in a loop with N iterations gives O(N^2) in memory and time - each iteration copies everything before it.

ITS standard: "Efficient string handling".

```bsl
// O(N) — массив + СтрСоединить()
ЧастиСтроки = Новый Массив;
Для Каждого Элемент Из КоллекцияДанных Цикл
    ЧастиСтроки.Добавить(Элемент.Наименование);
КонецЦикла;
РезультатСтрока = СтрСоединить(ЧастиСтроки, ", ");

// Для фиксированного числа подстановок — СтрШаблон() (до 10 параметров)
ТекстСообщения = СтрШаблон(
    НСтр("ru = 'Документ %1 от %2 на сумму %3 руб.'"),
    НомерДокумента,
    Формат(ДатаДокумента, "ДЛФ=D"),
    Формат(Сумма, "ЧДЦ=2"));
```

---

## Rule 8: Regions (#Область) for organizing code

ITS standard: "Module structure" - mandatory standard regions.

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

## Rule 9: Comments - explain "why", not "what"

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

### Comment "why"

```bsl
// Сумму округляем до копеек, потому что бухгалтерский учёт не допускает дробных копеек,
// а при пересчёте НДС могут возникнуть дроби.
СуммаНДС = Окр(СуммаБезНДС * СтавкаНДС / 100, 2);
```

---

## Rule 10: Use НСтр() for string literals

All strings shown to the user are wrapped in `НСтр()` for localization.

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

A procedure longer than 100 lines is a signal to decompose it. Splitting into small functions with descriptive names makes the code self-documenting.

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

BSL is dynamically typed. Describing types in the comment for an exported function is the only way to document the contract.

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

## Rule 13: Do not use `Выполнить()` and `Вычислить()` unless absolutely necessary

Security risk (an eval-like equivalent), invisible to static analysis, difficult to debug.

```bsl
// Правильно — прямой вызов через метаданные
МенеджерОбъекта = ОбщегоНазначения.МенеджерОбъектаПоСсылке(СсылкаНаОбъект);
```

---

## Rule 14: Magic numbers and strings - move them into parameters

Hard-coded values are unclear, duplicated, and not configurable.

```bsl
// Правильно — перечисление, значение самодокументировано
Если Документ.Статус = Перечисления.СтатусыДокументов.Согласован Тогда
    // ...
КонецЕсли;

// Или константа для настраиваемых значений
МаксимальноеКоличествоПопыток = Константы.МаксимальноеКоличествоПопытокОтправки.Получить();
```

---

## Rule 15: Explicit JOINs instead of dot notation through references

Reference chains create implicit JOINs. For compound types, the platform makes a JOIN to **all** possible tables.

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

### Incorrect - dot access in a loop (N+1)

```bsl
Для Каждого СтрокаТоваров Из Документ.Товары Цикл
    ВидНоменклатуры = СтрокаТоваров.Номенклатура.ВидНоменклатуры;
    ЕдиницаИзмерения = СтрокаТоваров.Номенклатура.ЕдиницаИзмерения;
КонецЦикла;
```

---

## Rule 16: Open forms through ОткрытьФорму()

`ПолучитьФорму()` is for the classic application and does not work in the managed interface.

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

Put write and validation logic in the object module - for testability and reuse.

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

## Verification via Partner

- **Code check against standards and БСП equivalents:** `ask_ai_assistant` (VALIDATE_BSL template from `buddy-prompting`). Provide a code fragment - get standards violations and recommendations for replacing them with БСП/platform methods.
- **Check the standard against the primary source:** `ask_ai_assistant` (SEARCH_ITS template from `buddy-prompting`). If the skill differs from ITS, ITS takes priority.

---
depends_on: []
---
