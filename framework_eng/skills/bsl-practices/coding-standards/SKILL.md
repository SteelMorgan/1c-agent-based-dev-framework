---
name: coding-standards
description: "When writing or reviewing BSL, apply 1C standards"
alwaysApply: false
---

# BSL Coding Standards (1C)

## Rule 1: Variable Naming - CamelCase in Russian

ITS standard: "Module Texts" - names in Russian, ВерблюжийРегистр.

| Element | Format | Example |
|---------|--------|--------|
| Variable | СуществительноеИлиФраза | `КоличествоСтрок`, `ДатаНачалаПериода` |
| Procedure | ГлагольнаяФраза | `ЗаполнитьТабличнуюЧасть`, `УстановитьОтбор` |
| Function | СуществительноеИлиВопрос | `ПолучитьСписокДокументов`, `ЭтоНовый` |
| Boolean variable | Affirmative form | `ЭтоНовый`, `РазрешеноРедактирование`, `ЕстьОшибки` |
| Parameter | КакПеременная | `ДокументСсылка`, `РежимОткрытия` |

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

## Rule 2: Module Structure - Interface and Implementation Sections

ITS standard: "Module Structure" - regions (#Область) in a specific order.

### Order of sections in a common module

```bsl
#Область ПрограммныйИнтерфейс

// Exported procedures and functions - the module's public API.

Функция ПолучитьКурсВалюты(Валюта, ДатаКурса) Экспорт
    // ...
КонецФункции

#КонецОбласти

#Область СлужебныйПрограммныйИнтерфейс

// Exported procedures for use only from other modules of this subsystem.

Функция ПересчитатьКурсВнутренний(ПараметрыПересчета) Экспорт
    // ...
КонецФункции

#КонецОбласти

#Область СлужебныеПроцедурыИФункции

// Internal implementation. Not exported.

Функция СформироватьЗапросКурса(Валюта, Дата)
    // ...
КонецФункции

#КонецОбласти
```

### Order of sections in an object module

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

## Rule 3: Compilation Directives - &НаКлиенте, &НаСервере, &НаСервереБезКонтекста

When calling `&НаСервере`, the platform serializes **the entire form context** back and forth. `&НаСервереБезКонтекста` passes only parameters - dramatically less traffic.

| Directive | Where it runs | Access to form data | When to use |
|-----------|----------------|----------------------|-------------------|
| `&НаКлиенте` | Client (thin/web) | Yes (client copy) | Interactive logic: dialogs, navigation |
| `&НаСервере` | Server | Yes (full form context) | Need access to form attributes + DB |
| `&НаСервереБезКонтекста` | Server | No | DB queries, calculations without form data |
| `&НаКлиентеНаСервереБезКонтекста` | Both client and server | No | Pure calculations, validation without DB |

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

## Rule 6: Do Not Shadow the Global Context

A local variable with the name of a global collection hides the manager - later accesses to it will raise an error.

```bsl
// Correct - a specific name
МассивДокументовКОбработке = Новый Массив;
СправочникНоменклатура = Справочники.Номенклатура;
```

### List of reserved names (do not use for variables)

`Документы`, `Справочники`, `Регистры`, `Перечисления`, `ПланыОбмена`, `ПланыВидовХарактеристик`, `ПланыВидовРасчета`, `ПланыСчетов`, `БизнесПроцессы`, `Задачи`, `Обработки`, `Отчеты`, `Константы`, `ПараметрыСеанса`, `РегистрыСведений`, `РегистрыНакопления`, `РегистрыБухгалтерии`, `РегистрыРасчета`

---

## Rule 7: String Concatenation - Do Not Use "+" in Loops

In BSL, strings are immutable. `Строка1 + Строка2` in a loop of N iterations gives O(N^2) in memory and time - each iteration copies everything before it.

ITS standard: "Efficient work with strings".

```bsl
// O(N) - array + StrJoin()
ЧастиСтроки = Новый Массив;
Для Каждого Элемент Из КоллекцияДанных Цикл
    ЧастиСтроки.Добавить(Элемент.Наименование);
КонецЦикла;
РезультатСтрока = СтрСоединить(ЧастиСтроки, ", ");

// For a fixed number of substitutions - StrTemplate() (up to 10 parameters)
ТекстСообщения = СтрШаблон(
    НСтр("ru = 'Документ %1 от %2 на сумму %3 руб.'"),
    НомерДокумента,
    Формат(ДатаДокумента, "ДЛФ=D"),
    Формат(Сумма, "ЧДЦ=2"));
```

---

## Rule 8: Regions (#Область) for Code Organization

ITS standard: "Module Structure" - mandatory standard regions.

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

## Rule 9: Comments - Explain "Why", Not "What"

ITS standard: "Description of procedures and functions" - exported procedures MUST have a description comment.

### Description of an exported function

```bsl
// Returns the currency exchange rate for the specified date.
// If the rate is not set for the specified date, returns the rate for the nearest previous date.
//
// Parameters:
//  Валюта   - СправочникСсылка.Валюты - currency whose rate needs to be retrieved.
//  ДатаКурса - Дата - date for which the rate is needed.
//              If not specified, the current session date is used.
//
// Return value:
//  Число - currency rate. 0 if the rate is not found.
//
Функция ПолучитьКурсВалюты(Валюта, ДатаКурса = Неопределено) Экспорт
```

### "Why" comment

```bsl
// Round the amount to kopeks because accounting does not allow fractional kopeks,
// and fractions may appear when recalculating VAT.
СуммаНДС = Окр(СуммаБезНДС * СтавкаНДС / 100, 2);
```

---

## Rule 10: Use НСтр() for String Literals

All strings displayed to the user are wrapped in `НСтр()` for localization.

ITS standard: "Using the НСтр() function".

```bsl
ТекстПредупреждения = НСтр("ru = 'Документ не может быть проведён. Не заполнена дата.'");

// With parameters - НСтр() + СтрШаблон()
ТекстСообщения = СтрШаблон(
    НСтр("ru = 'Остаток товара ""%1"" на складе: %2 %3'"),
    Номенклатура,
    Остаток,
    ЕдиницаИзмерения);
```

---

## Rule 11: One Procedure - One Responsibility

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

## Rule 12: Explicit Typing of Parameters in Comments

BSL is dynamically typed. Type descriptions in the comment for an exported function are the only way to document the contract.

```bsl
// Creates a new item in the "Номенклатура" catalog with default filling.
//
// Parameters:
//  ДанныеЗаполнения - Структура - contains fields:
//    * Наименование      - Строка - item name (required).
//    * ВидНоменклатуры    - ПеречислениеСсылка.ВидыНоменклатуры - type (required).
//    * ЕдиницаИзмерения  - СправочникСсылка.ЕдиницыИзмерения - unit of measure. Optional,
//                          default is "шт.".
//    * Артикул            - Строка - item code. Optional.
//
// Return value:
//  СправочникСсылка.Номенклатура - reference to the created item.
//
Функция СоздатьНоменклатуру(ДанныеЗаполнения) Экспорт
```

---

## Rule 13: Do Not Use "Выполнить()" and "Вычислить()" Unless Absolutely Necessary

Security threat (analogous to eval), invisible to static analysis, hard to debug.

```bsl
// Correct - direct call through metadata
МенеджерОбъекта = ОбщегоНазначения.МенеджерОбъектаПоСсылке(СсылкаНаОбъект);
```

---

## Rule 14: Magic Numbers and Strings - Move Them to Parameters

Hardcoded values are unclear, duplicated, and not configurable.

```bsl
// Correct - enumeration, the value is self-documenting
Если Документ.Статус = Перечисления.СтатусыДокументов.Согласован Тогда
    // ...
КонецЕсли;

// Or a constant for configurable values
МаксимальноеКоличествоПопыток = Константы.МаксимальноеКоличествоПопытокОтправки.Получить();
```

---

## Rule 15: Explicit JOINs Instead of Dotted Notation Through References

Reference chains create implicit JOINs. For composite types, the platform performs JOINs to **all** possible tables.

```bsl
// Correct - one query with explicit JOINs
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

### Incorrect - access through dot notation in a loop (N+1)

```bsl
Для Каждого СтрокаТоваров Из Документ.Товары Цикл
    ВидНоменклатуры = СтрокаТоваров.Номенклатура.ВидНоменклатуры;
    ЕдиницаИзмерения = СтрокаТоваров.Номенклатура.ЕдиницаИзмерения;
КонецЦикла;
```

---

## Rule 16: Open Forms via OpenForm()

`ПолучитьФорму()` - classic application, does not work in the managed interface.

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

## Rule 17: Business Logic Should Not Live in the Form Module

Place write and validation logic in the object module - for testability and reuse.

```bsl
// Object module of the document
Процедура ПередЗаписью(Отказ)
    Для Каждого Строка Из Товары Цикл
        Строка.Сумма = Строка.Количество * Строка.Цена;
    КонецЦикла;
КонецПроцедуры
```

---

## Rule 18: Do Not Swallow Exceptions

See `error-handling`, rule 1.

---

## Rule 19: Aggregate Server Calls from the Form

See `form-patterns`.

---

## Verification via Buddy

- **Check code against standards and BСП analogs:** `ask_ai_assistant` (template VALIDATE_BSL from `buddy-prompting`). Give a code fragment - get standard violations and recommendations for replacement with BСП/platform methods.
- **Check the standard against the primary source:** `ask_ai_assistant` (template SEARCH_ITS from `buddy-prompting`). If the skill conflicts with ITS, ITS takes priority.

---
depends_on: []
---
