---
name: coding-standards
description: Coding standards for BSL (1С). This skill teaches the agent to write code in the embedded language 1C (BSL) in accordance with the standards of the 1С:Предприятие platform and ITS recommendations.
---

# BSL (1С) Coding Standards

## Purpose

This skill teaches the agent to write code in the embedded language 1C (BSL) in accordance with the standards of the 1С:Предприятие platform and ITS recommendations. Following the standards is critically important: 1C code lives in production for years and is read and maintained by dozens of developers. A consistent style reduces cognitive load and the number of mistakes.

**Sources of the standards:**
- [ITS Development Standards](https://its.1c.ru/db/v8std)
- Naming conventions: ITS, section “General requirements for the configuration”
- BSP standards (Library of standard subsystems)

---

## Rule summary

| # | Rule | Justification |
|---|------|---------------|
| 1 | CamelCase in Russian | ITS standard, consistency |
| 2 | Module structure with regions | Navigation, readability |
| 3 | Prefer &НаСервереБезКонтекста | Reduced client-server traffic |
| 4 | ТекущаяДатаСеанса() | Correct timezone |
| 5 | СообщитьПользователю() with targeting | UX — user sees the problematic field |
| 6 | Do not shadow the global context | Hiding global collections → bugs |
| 7 | СтрСоединить() instead of “+” in loops | O(N) instead of O(N²) |
| 8 | Standard #Область | Navigation, analysis tools |
| 9 | Comments about “why”, not “what” | Useful documentation |
| 10 | НСтр() for user strings | Localization |
| 11 | One procedure, one responsibility | Readability, testability |
| 12 | Typing in comments | Documenting the contract |
| 13 | Avoid Выполнить()/Вычислить() | Security, analysability |
| 14 | No magic numbers | Readability, configurability |
| 15 | Explicit JOINs instead of dotted notation | Avoid N+1 and extra reads |
| 16 | Open forms via ОткрытьФорму() | Compatibility with managed interface |
| 17 | Business logic outside form modules | Reuse, testability |
| 18 | Do not swallow exceptions | Proper error handling and transactions |
| 19 | One server call instead of many | Fewer client-server delays |

---

## Rule 1: Naming variables in Russian CamelCase

**Why:** The 1C platform historically uses Russian identifiers in CamelCase (PascalCase). English names are acceptable only in export APIs targeted at external systems. Mixing languages within one module makes reading and searching harder.

**ITS standard:** “Module texts” — names of variables, procedures, and functions must be in Russian, in the ВерблюжийРегистр format.

### Naming rules

| Element | Format | Example |
|---------|--------|---------|
| Variable | NounOrPhrase | `КоличествоСтрок`, `ДатаНачалаПериода` |
| Procedure | VerbPhrase | `ЗаполнитьТабличнуюЧасть`, `УстановитьОтбор` |
| Function | NounOrQuestion | `ПолучитьСписокДокументов`, `ЭтоНовый` |
| Boolean variable | Affirmative form | `ЭтоНовый`, `РазрешеноРедактирование`, `ЕстьОшибки` |
| Parameter | LikeVariable | `ДокументСсылка`, `РежимОткрытия` |

### Correct

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

### Incorrect

```bsl
// Mixing languages, non-informative names, breaking CamelCase
Процедура fill_table(doc, data)
    
    n = data.Количество();
    flag = Ложь;
    
    Для Каждого row Из data Цикл
        nr = doc.Товары.Добавить();
        nr.Номенклатура = row.Номенклатура;
    КонецЦикла;
    
КонецПроцедуры
```

---

## Rule 2: Module structure — interface and implementation sections

**Why:** A clear module structure makes it easy to find the right code. Exported procedures are the module’s contract; placing them at the top allows understanding of the API without reading the entire implementation. The ITS standard describes the exact order of sections.

**ITS standard:** “Module structure” — the module is split into regions (#Область) that follow a specific order.

### General module sections order

```bsl
#Область ПрограммныйИнтерфейс

// Exported procedures and functions — the module’s public API.
// This is the “contract” with the calling code. Their signatures cannot change without analyzing all call sites.

Функция ПолучитьКурсВалюты(Валюта, ДатаКурса) Экспорт
    // ...
КонецФункции

#КонецОбласти

#Область СлужебныйПрограммныйИнтерфейс

// Exported procedures meant to be called only from other modules of this subsystem.
// Not a public API — only called by “own” modules.

Функция ПересчитатьКурсВнутренний(ПараметрыПересчета) Экспорт
    // ...
КонецФункции

#КонецОбласти

#Область СлужебныеПроцедурыИФункции

// Internal implementation. Not exported.

Функция СформироватьЗапросКурса(Валюта, Дата)
    // ...
КонецФункции

Процедура ЗаписатьКурсВРегистр(ДанныеКурса)
    // ...
КонецПроцедуры

#КонецОбласти
```

### Object module sections order

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

// Internal logic of the object

#КонецОбласти

#Область Инициализация

// Initialization code for module variables (runs when the object is created)
МассивИзменённыхРеквизитов = Новый Массив;

#КонецОбласти
```

---

## Rule 3: Compilation directives — &НаКлиенте, &НаСервере, &НаСервереБезКонтекста

**Why:** 1С is a client-server platform. The directive choice determines where the code runs and which data is transmitted between client and server. Misusing directives leads to excessive traffic, slow forms, and security issues.

### Directives description

| Directive | Where it runs | Access to form data | When to use |
|-----------|---------------|---------------------|-------------|
| `&НаКлиенте` | Client (thin/web) | Yes (client copy) | Interactive logic: dialogues, navigation, calling server methods |
| `&НаСервере` | Server | Yes (full form context) | When you need access to form attributes + database |
| `&НаСервереБезКонтекста` | Server | No | DB queries, calculations that do not require form data |
| `&НаКлиентеНаСервереБезКонтекста` | Both client and server | No | Pure calculations, formatting, validation without the database |

### Preference: &НаСервереБезКонтекста

**Why prefer `&НаСервереБезКонтекста` over `&НаСервере`:**

When you call `&НаСервере`, the platform serializes **the entire form context** (all attributes, form tables, settings) and sends it to the server, and then back. For a form with a large tabular part (1000+ rows) this can be megabytes of data both ways.

`&НаСервереБезКонтекста` does not transmit the form context — only the call parameters. This drastically reduces traffic.

### Correct

```bsl
// Client: reaction to user action
&НаКлиенте
Процедура НоменклатураПриИзменении(Элемент)
    // On the client we take the value from the form and call the server without context
    ДанныеНоменклатуры = ПолучитьДанныеНоменклатуры(Элементы.Товары.ТекущиеДанные.Номенклатура);
    ЗаполнитьСтрокуТоваровНаКлиенте(ДанныеНоменклатуры);
КонецПроцедуры

// Server without context: database access, does not require form data
&НаСервереБезКонтекста
Функция ПолучитьДанныеНоменклатуры(НоменклатураСсылка)
    
    Возврат Новый Структура("ЕдиницаИзмерения, Цена, СтавкаНДС",
        НоменклатураСсылка.ЕдиницаИзмерения,
        НоменклатураСсылка.Цена,
        НоменклатураСсылка.СтавкаНДС);
    
КонецФункции
```

### Incorrect

```bsl
// BAD: &НаСервере sends the entire form context for a single query
&НаСервере
Процедура НоменклатураПриИзмененииНаСервере()
    
    СтрокаТоваров = Объект.Товары.НайтиПоИдентификатору(
        Элементы.Товары.ТекущаяСтрока);
    
    // To get the unit of measure we transmitted the whole form (all tabular parts) to the server and back
    СтрокаТоваров.ЕдиницаИзмерения = СтрокаТоваров.Номенклатура.ЕдиницаИзмерения;
    
КонецПроцедуры
```

---

## Rule 6: Do not shadow the global context

**Why:** The 1С platform exposes global metadata collections: `Документы`, `Справочники`, `Регистры`, `Перечисления`, etc. If you name a local variable `Документы`, it hides the global context — further references to `Документы` will point to your local variable, not the document manager. This leads to elusive bugs.

### Correct

```bsl
// A specific name that does not conflict with the global context
МассивДокументовКОбработке = Новый Массив;
СправочникНоменклатура = Справочники.Номенклатура;
СсылкаНаКонтрагента = Справочники.Контрагенты.НайтиПоКоду("00001");
```

### Incorrect

```bsl
// BAD: shadows the global collection “Документы”
Документы = Новый Массив;
// Now Документы is an array, not the document manager!
// Документы.РеализацияТоваровУслуг — error!

// BAD: shadows “Справочники”
Справочники = ПолучитьСписокСправочников();
```

### Reserved names list (do not use for variables)

`Документы`, `Справочники`, `Регистры`, `Перечисления`, `ПланыОбмена`, `ПланыВидовХарактеристик`, `ПланыВидовРасчета`, `ПланыСчетов`, `БизнесПроцессы`, `Задачи`, `Обработки`, `Отчеты`, `Константы`, `ПараметрыСеанса`, `РегистрыСведений`, `РегистрыНакопления`, `РегистрыБухгалтерии`, `РегистрыРасчета`

---

## Rule 7: String concatenation — do not use “+” inside loops

**Why:** Strings in BSL are immutable. Each `Строка1 + Строка2` operation creates a new string object in memory. In a loop with N iterations this becomes O(N²) in memory and time (each iteration copies all previous data). For 10 000 strings the difference is seconds vs milliseconds.

**ITS standard:** “Efficient string handling”.

### Correct — option 1: array + СтрСоединить()

```bsl
// O(N) in memory and time
ЧастиСтроки = Новый Массив;

Для Каждого Элемент Из КоллекцияДанных Цикл
    ЧастиСтроки.Добавить(Элемент.Наименование);
КонецЦикла;

РезультатСтрока = СтрСоединить(ЧастиСтроки, ", ");
```

### Correct — option 2: СтрШаблон() for fixed substitution count

```bsl
// For building a string with substitutions (up to 10 parameters)
ТекстСообщения = СтрШаблон(
    НСтр("ru = 'Документ %1 от %2 на сумму %3 руб.'"),
    НомерДокумента,
    Формат(ДатаДокумента, "ДЛФ=D"),
    Формат(Сумма, "ЧДЦ=2"));
```

### Incorrect

```bsl
// BAD: O(N²) — each iteration creates a new string copying the previous one
Результат = "";
Для Каждого Элемент Из КоллекцияДанных Цикл
    Результат = Результат + Элемент.Наименование + ", ";  // Copies the entire string on every step!
КонецЦикла;
```

---

## Rule 8: Regions (#Область) for organizing code

**Why:** Regions help group related code and collapse it in the IDE. This is a standard navigation mechanism in 1С modules. Without regions, a 500+ line module becomes unreadable.

**ITS standard:** “Module structure” — mandatory use of standard regions.

### Standard regions for a form module

```bsl
#Область ОписаниеПеременных

#КонецОбласти

#Область ОбработчикиСобытийФормы

&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    // ...
КонецПроцедуры

#КонецОбласти

#Область ОбработчикиСобытийЭлементовШапкиФормы

// Event handlers for form elements (not tabular parts)

#КонецОбласти

#Область ОбработчикиСобытийЭлементовТаблицыФормыТовары

// Handlers for the table part “Товары”

#КонецОбласти

#Область ОбработчикиКомандФормы

// Command handlers (form buttons)

#КонецОбласти

#Область СлужебныеПроцедурыИФункции

// Internal logic of the form module

#КонецОбласти
```

### Region rules

1. **Do not create nested regions** deeper than 2 levels — this confuses rather than helps.
2. **Do not leave empty regions** — if a region is empty, remove it.
3. **Use standard names** from ITS — do not invent your own (the IDE and analysis tools rely on standard names).

---

## Rule 9: Comments — explain “why”, not “what”

**Why:** BSL reads like a natural language (Russian keywords and identifiers). A comment like “// Increase the counter by 1” next to `Счётчик = Счётчик + 1` is useless noise. Comments should:
- Describe **why** (business logic, reason for the decision)
- Describe **constraints** and **assumptions**
- Describe the **public API** (exported procedures)

**ITS standard:** “Description of procedures and functions” — exported procedures and functions MUST have descriptive comments.

### Correct — describing an exported function

```bsl
// Returns the currency rate for the specified date.
// If the rate is not set for the specified date, returns the rate for the nearest previous date.
//
// Parameters:
//  Валюта   - СправочникСсылка.Валюты - the currency whose rate is required.
//  ДатаКурса - Дата - the date for which the rate is needed.
//              If not specified, the current session date is used.
//
// Return value:
//  Число - the currency rate. 0 if the rate is not found.
//
Функция ПолучитьКурсВалюты(Валюта, ДатаКурса = Неопределено) Экспорт
```

### Correct — “why” comment

```bsl
// We round the amount to kopecks because accounting does not allow fractional kopecks,
// and recalculating VAT can introduce fractions.
СуммаНДС = Окр(СуммаБезНДС * СтавкаНДС / 100, 2);
```

### Incorrect

```bsl
// BAD: comment repeats the code
// Assigning the nomenclature value to the variable
ТекущаяНоменклатура = СтрокаТоваров.Номенклатура;

// BAD: comment is outdated (code changed, comment did not)
// Get the rate for the beginning of the month
КурсВалюты = ПолучитьКурсВалюты(Валюта, ДатаДокумента); // passing the document date, not the month start!
```

---

## Rule 10: Use НСтр() for string literals

**Why:** The 1С platform supports multilingual configurations. `НСтр()` allows you to specify a string in several languages and automatically selects the version for the current interface language. Even if the configuration is single-language, using `НСтр()` is good practice: future localization won’t require reworking the entire code.

**ITS standard:** “Use of the НСтр() function” — all string literals displayed to the user must be wrapped in `НСтр()`.

### Correct

```bsl
// A user-facing string — via НСтр()
ТекстПредупреждения = НСтр("ru = 'Документ не может быть проведён. Не заполнена дата.'");

// With parameters — combine НСтр() and СтрШаблон()
ТекстСообщения = СтрШаблон(
    НСтр("ru = 'Остаток товара ""%1"" на складе: %2 %3'"),
    Номенклатура,
    Остаток,
    ЕдиницаИзмерения);
```

### Incorrect

```bsl
// BAD: string without НСтр() — cannot be localized
ТекстПредупреждения = "Документ не может быть проведён.";

// BAD: НСтр() inside a loop with concatenation
Для Каждого Строка Из ТаблицаОшибок Цикл
    Результат = Результат + НСтр("ru = 'Ошибка в строке: '") + Строка.Описание;  // НСтр in a loop is allowed, but concatenation is not
КонецЦикла;
```

---

## Rule 11: One procedure, one responsibility

**Why:** Long procedures (100+ lines) are hard to read, test, and reuse. If a procedure does several things, changing one part easily breaks another. Splitting into small functions with descriptive names makes the code self-documenting.

### Correct

```bsl
Процедура ОбработкаПроведения(Отказ, РежимПроведения)
    
    ИнициализироватьДанныеДокумента();
    ПроверитьЗаполнениеРеквизитов(Отказ);
    
    Если Не Отказ Тогда
        СформироватьДвижения(Отказ);
    КонецЕсли;
    
КонецПроцедуры
```

### Incorrect

```bsl
// BAD: one 300-line procedure doing everything:
// validation, movement creation, summary recalculation, notification sending
Процедура ОбработкаПроведения(Отказ, РежимПроведения)
    // ... 300 consecutive lines ...
КонецПроцедуры
```

---

## Rule 12: Explicit typing of parameters in comments

**Why:** BSL is dynamically typed. Without parameter type descriptions, the calling code must read the implementation to know what to pass. Describing types in the comment for an exported function is the only way to document the contract.

### Correct

```bsl
// Creates a new catalog item “Номенклатура” with default filling.
//
// Parameters:
//  ДанныеЗаполнения - Структура - contains fields:
//    * Наименование      - Строка - the catalog name (required).
//    * ВидНоменклатуры    - ПеречислениеСсылка.ВидыНоменклатуры - the type (required).
//    * ЕдиницаИзмерения  - СправочникСсылка.ЕдиницыИзмерения - unit of measure. Optional,
//                          default “шт.”.
//    * Артикул            - Строка - article. Optional.
//
// Return value:
//  СправочникСсылка.Номенклатура - link to the created item.
//
Функция СоздатьНоменклатуру(ДанныеЗаполнения) Экспорт
```

---

## Rule 13: Avoid «Выполнить()» and «Вычислить()» unless absolutely necessary

**Why:** `Выполнить()` and `Вычислить()` are equivalents of `eval()`. They compile and execute arbitrary code at runtime. This results in:
1. **A security risk** — if user data makes it into the string, arbitrary code may run
2. **Invisibility for static analysis** — code within the string is not visible to checkers
3. **Hardness of debugging** — errors inside `Выполнить()` are harder to diagnose

### Correct

```bsl
// Use a direct call by name via metadata
МенеджерОбъекта = ОбщегоНазначения.МенеджерОбъектаПоСсылке(СсылкаНаОбъект);
```

### Incorrect

```bsl
// BAD: dynamic execution of code
Выполнить("Результат = Справочники." + ИмяСправочника + ".НайтиПоКоду(Код)");
```

---

## Rule 14: Magic numbers and strings — move them to parameters

**Why:** Hardcoded values (“magic numbers”) in code:
1. Are unclear — what does `Если Статус = 3` mean?
2. Are duplicated — changing them requires finding all occurrences
3. Are not configurable — changing the value demands code edits and redeployment

### Correct

```bsl
// Use an enumeration — the value is self-documented
Если Документ.Статус = Перечисления.СтатусыДокументов.Согласован Тогда
    // ...
КонецЕсли;

// Or a constant for configurable values
МаксимальноеКоличествоПопыток = Константы.МаксимальноеКоличествоПопытокОтправки.Получить();
```

### Incorrect

```bsl
// BAD: what is 3? Why precisely 5?
Если Документ.Статус = 3 Тогда
    // ...
КонецЕсли;

Для Попытка = 1 По 5 Цикл
    // Where did 5 come from?
КонецЦикла;
```

---

## Rule 15: Explicit JOINs instead of dotted notation via links

**Why:** Long chains of links (`Документ.Контрагент.ГоловнойКонтрагент`) create implicit JOINs and provoke N+1 database requests, especially inside loops. For composite types the platform is forced to join all possible tables.

### Correct

```bsl
// One query with explicit JOIN
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
Запрос.УстановитьПараметр("ДокументСсылка", Документ.Ссылка);
```

### Incorrect

```bsl
Для Каждого СтрокаТоваров Из Документ.Товары Цикл
    ВидНоменклатуры = СтрокаТоваров.Номенклатура.ВидНоменклатуры;
    ЕдиницаИзмерения = СтрокаТоваров.Номенклатура.ЕдиницаИзмерения;
    ИмяКонтрагента = Документ.Контрагент.Наименование;
КонецЦикла;
```

---

## Rule 16: Open forms via ОткрытьФорму()

**Why:** `ПолучитьФорму()` belongs to the traditional interface and does not work in the managed interface. It creates the form on the server without showing it and does not support notifications and modality.

### Correct

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

### Incorrect

```bsl
Форма = ПолучитьФорму("Обработка.ЗагрузкаДанных.Форма");
Форма.Открыть();
```

---

## Rule 17: Business logic should not live in the form module

**Why:** The form module is responsible for the UI. Business logic in forms leads to duplication, reduces testability, and breaks the separation of concerns. Place writing and validation logic in the object module.

### Correct

```bsl
// Object module of the document
Процедура ПередЗаписью(Отказ)
    Для Каждого Строка Из Товары Цикл
        Строка.Сумма = Строка.Количество * Строка.Цена;
    КонецЦикла;
КонецПроцедуры
```

### Incorrect

```bsl
// Form module of the document
&НаСервере
Процедура ЗаписатьИПровестиНаСервере()
    ДокументОбъект = РеквизитФормыВЗначение("Объект");
    Для Каждого Строка Из ДокументОбъект.Товары Цикл
        Строка.Сумма = Строка.Количество * Строка.Цена;
    КонецЦикла;
    ДокументОбъект.Записать(РежимЗаписиДокумента.Проведение);
    ЗначениеВРеквизитФормы(ДокументОбъект, "Объект");
КонецПроцедуры
```

---

## Rule 18: Do not swallow exceptions

**Why:** If an exception is only logged, the calling code continues working with incorrect data. In transactions this leads to unclosed changes. Either return a status or rethrow the exception.

### Correct

```bsl
Попытка
    НачатьТранзакцию();
    // ... data writing ...
    ЗафиксироватьТранзакцию();
Исключение
    ОтменитьТранзакцию();
    ЗаписьЖурналаРегистрации("Ошибка", УровеньЖурналаРегистрации.Ошибка,,,
        ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
    ВызватьИсключение;
КонецПопытки;
```

### Incorrect

```bsl
Попытка
    НачатьТранзакцию();
    // ... data writing ...
    ЗафиксироватьТранзакцию();
Исключение
    ОтменитьТранзакцию();
    ЗаписьЖурналаРегистрации("Ошибка", УровеньЖурналаРегистрации.Ошибка,,,
        ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
    // Error is swallowed
КонецПопытки;
```

---

## Rule 19: Aggregate server calls from the form

**Why:** Sequential server calls triggered by a single user action add up latency. Try to fetch all needed data in one call.

### Correct

```bsl
&НаКлиенте
Процедура КонтрагентПриИзменении(Элемент)
    ДанныеЗаполнения = ПолучитьДанныеКонтрагента(Объект.Контрагент, Объект.Дата);
    Объект.Договор = ДанныеЗаполнения.Договор;
    Объект.Валюта = ДанныеЗаполнения.Валюта;
    КурсВалюты = ДанныеЗаполнения.Курс;
КонецПроцедуры

&НаСервереБезКонтекста
Функция ПолучитьДанныеКонтрагента(Контрагент, Дата)
    Результат = Новый Структура("Договор, Валюта, Курс");
    Результат.Договор = ПолучитьДоговорПоУмолчанию(Контрагент);
    Результат.Валюта = ОбщегоНазначения.ЗначениеРеквизитаОбъекта(Результат.Договор, "ВалютаВзаиморасчетов");
    Результат.Курс = РаботаСКурсамиВалют.ПолучитьКурсВалюты(Результат.Валюта, Дата);
    Возврат Результат;
КонецФункции
```

### Incorrect

```bsl
&НаКлиенте
Процедура КонтрагентПриИзменении(Элемент)
    ДоговорПоУмолчанию = ПолучитьДоговорПоУмолчанию(Объект.Контрагент);
    ВалютаДоговора = ПолучитьВалютуДоговора(ДоговорПоУмолчанию);
    КурсВалюты = ПолучитьКурсВалюты(ВалютаДоговора, Объект.Дата);
КонецПроцедуры
```

---
depends_on: []
---
