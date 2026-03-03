---
name: ssl-patterns
description: Patterns for working with БСП (Библиотека стандартных подсистем). This skill teaches the agent to correctly use БСП (Библиотеку стандартных подсистем, англ.
---

# Patterns for working with БСП (Библиотека стандартных подсистем)

## Purpose

This skill teaches the agent to correctly use БСП (Библиотеку стандартных подсистем, англ. SSL — Standard Subsystems Library) when developing on 1С. БСП is a set of universal subsystems supplied by firm «1С» that implement standard mechanisms: working with users, files, mail, data exchange, etc. Most typical 1С configurations (УТ, ERP, БП, ЗУП) are built on БСП.

**Why it is important to use БСП:**
- The БСП code has been verified on millions of installations — it is stable and optimized
- Standard mechanisms are updated along with БСП — you do not need to maintain handwritten code
- Other developers know the БСП API — the code will be understandable to the team
- When updating the configuration, custom code that duplicates БСП can conflict

**Sources:**
- [Документация БСП на ИТС](https://its.1c.ru/db/bsp)
- Source code of БСП modules (supplied with configurations)
- [GitHub: 1C-Company/ssl-support](https://github.com/1C-Company/ssl-support)

---

## Summary of rules

| # | Rule | Rationale |
|---|------|-----------|
| 1 | ОбщегоНазначения — the first place to look | Avoid duplication |
| 2 | СтроковыеФункции for working with strings | Proven code, edge cases |
| 3 | КлиентСервер modules for reuse | Single codebase for both environments |
| 4 | LSP → grep → AI for finding functions | Systematic approach |
| 5 | ЗаписьЖурналаРегистрации via the standard | Uniform logs |
| 6 | РаботаСФайлами for file operations | Safety, cleanup |
| 7 | Typical patterns for standard tasks | Standardization |
| 8 | Do not duplicate БСП | Single source of truth |
| 9 | Module suffix determines the environment | Pick the right module |

---

## Rule 1: Модуль ОбщегоНазначения — the primary "Swiss army knife"

**Why:** `ОбщегоНазначения` is the central БСП module that contains hundreds of universal functions. Before writing your own implementation, check — maybe БСП already provides a ready-made function. Duplicating БСП code is an anti-pattern.

### Key functions of ОбщегоНазначения

| Function | Description | When to use |
|----------|-------------|-------------|
| `ЗначениеРеквизитаОбъекта()` | Gets the value of an attribute by reference | Instead of `Ссылка.Реквизит` (avoid dot notation) |
| `ЗначенияРеквизитовОбъекта()` | Gets multiple attributes by reference | Instead of multiple dot accesses |
| `СообщитьПользователю()` | Message bound to a field | Instead of `Сообщить()` |
| `МенеджерОбъектаПоСсылке()` | A manager for an object reference | Instead of `Выполнить("Справочники." + Имя)` |
| `ПодсистемаСуществует()` | Checks for the presence of a subsystem | For conditional module calls |
| `ОбщийМодуль()` | Gets a module by name | Dynamic invocation of a БСП module |
| `ЭтоСсылка()` | Checks whether the value is a reference | Parameter validation |
| `СсылкаСуществует()` | Determines if the object exists in the database | Check before accessing |

### Example: ЗначенияРеквизитовОбъекта — instead of dot notation

```bsl
// ❌ ПЛОХО: три обращения к БД через точку
Наименование = КонтрагентСсылка.Наименование;      // чтение 1
ИНН = КонтрагентСсылка.ИНН;                          // чтение 2
Ответственный = КонтрагентСсылка.ОсновнойМенеджер;  // чтение 3

// ✅ ПРАВИЛЬНО: одно обращение через БСП
РеквизитыКонтрагента = ОбщегоНазначения.ЗначенияРеквизитовОбъекта(
    КонтрагентСсылка,
    "Наименование, ИНН, ОсновнойМенеджер");

Наименование = РеквизитыКонтрагента.Наименование;
ИНН = РеквизитыКонтрагента.ИНН;
Ответственный = РеквизитыКонтрагента.ОсновнойМенеджер;
```

### Example: ЗначениеРеквизитаОбъекта — single value

```bsl
// ❌ ПЛОХО: обращение через точку
ВидНоменклатуры = НоменклатураСсылка.ВидНоменклатуры;

// ✅ ПРАВИЛЬНО: через БСП
ВидНоменклатуры = ОбщегоНазначения.ЗначениеРеквизитаОбъекта(
    НоменклатураСсылка, "ВидНоменклатуры");
```

---

## Rule 2: СтроковыеФункцииКлиентСервер — string handling

**Why:** `СтроковыеФункцииКлиентСервер` (or `СтроковыеФункции` on the server) contains optimized functions for working with strings. They correctly handle edge cases (empty strings, delimiters, Unicode) and are covered by thousands of tests.

### Key functions

| Function | Description | When to use |
|---------|-------------|--------------|
| `ПодставитьПараметрыВСтроку()` | Substitutes `%1`, `%2` into a template | Equivalent to `СтрШаблон()` with extra checks |
| `СтрокаСЧисломПредметов()` | “5 документов”, “1 документ”, “21 документ” | Noun declension |
| `ЕстьНедопустимыеСимволы()` | Checks a string for forbidden characters | Input validation |
| `ТолькоЦифрыВСтроке()` | Checks whether the string contains only digits | Validation for ИНН, КПП, numbers |
| `РазложитьСтрокуВМассивПодстрок()` | Splits a string by a delimiter | Data parsing |

### Example: ПодставитьПараметрыВСтроку

```bsl
// Формирование сообщения с подстановкой параметров
ТекстСообщения = СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(
    НСтр("ru = 'Обработано %1 из %2 документов. Ошибок: %3.'"),
    Формат(Обработано, "ЧГ="),
    Формат(Всего, "ЧГ="),
    Формат(КоличествоОшибок, "ЧГ="));
```

### Example: СтрокаСЧисломПредметов

```bsl
// Корректное склонение: «1 документ», «2 документа», «5 документов»
ТекстОповещения = СтроковыеФункцииКлиентСервер.СтрокаСЧисломПредметов(
    КоличествоДокументов,
    НСтр("ru = 'документ, документа, документов'"));
// Результат: "5 документов" или "1 документ" или "23 документа"
```

---

## Rule 3: ОбщегоНазначенияКлиентСервер — utilities for both environments

**Why:** `ОбщегоНазначенияКлиентСервер` is a module marked with `&НаКлиентеНаСервереБезКонтекста`; its functions are available both on the client and on the server. This allows reusing code without duplication.

### Key functions

| Function | Description |
|----------|-------------|
| `ДополнитьТаблицуИзМассива()` | Fills a value table from an array |
| `ДополнитьМассив()` | Merges two arrays |
| `ДополнитьСтруктуру()` | Merges two structures |
| `СвойствоСтруктуры()` | Safe read of a property (returns default if missing) |
| `ПроверитьПараметр()` | Validates the parameter type with an informative error |
| `Сообщить()` | Wrapper around `ОбщегоНазначения.СообщитьПользователю()` (client variant) |

### Example: СвойствоСтруктуры — safe access

```bsl
// ❌ ПЛОХО: ошибка если свойства нет в структуре
Значение = ПараметрыОтчёта.ДатаНачала; // Ошибка если нет свойства «ДатаНачала»

// ✅ ПРАВИЛЬНО: безопасный доступ с значением по умолчанию
ДатаНачала = ОбщегоНазначенияКлиентСервер.СвойствоСтруктуры(
    ПараметрыОтчёта, "ДатаНачала", НачалоГода(ТекущаяДатаСеанса()));
```

---

## Rule 4: Strategy for searching БСП functions

**Why:** БСП contains thousands of functions distributed across hundreds of modules. Without a search strategy it is easy to miss an existing function and write a duplicate.

### Search algorithm

1. **LSP (symbol navigation)** — if an LSP server is available (capability: `navigate_symbol`), search by name:
   ```
   navigate_symbol("СообщитьПользователю")
   navigate_symbol("ЗначенияРеквизитовОбъекта")
   ```

2. **Text search in modules** — if LSP is unavailable, search the sources directly:
   ```
   grep -r "Функция.*КурсВалюты" src/CommonModules/
   ```

3. **AI assistant (copilot-proxy)** — if direct search did not return results:
   ```
   ask_ai_assistant("Есть ли в БСП функция для получения курса валюты на дату?")
   ```

4. **ITS documentation** — to understand the semantics and limitations of the discovered function

### When to write your own vs use БСП

| Situation | Decision |
|-----------|----------|
| БСП has an exact match | **Use БСП** |
| БСП has a similar function with extra features | **Use БСП** — the extra capabilities do not hurt, and the code stays standardized |
| You need a function that БСП lacks | **Write your own**, but in the БСП style (comments, naming) |
| БСП function works but is suboptimal for your case | **Use БСП** — optimize only if it is a bottleneck |
| Configuration without БСП | **Write your own** or include standalone БСП modules |

---

## Rule 5: Logging via БСП

**Why:** БСП provides wrappers around `ЗаписьЖурналаРегистрации()` that:
- Automatically determine the metadata of the object
- Handle transactions correctly (a ЖР entry inside a transaction can be lost upon a rollback)
- Format the messages according to the standard

### Correct usage — via БСП

```bsl
// Логирование ошибки через обёртку БСП
Попытка
    ДокументОбъект.Записать(РежимЗаписиДокумента.Проведение);
Исключение
    ЗаписьЖурналаРегистрации(
        НСтр("ru = 'Проведение документа'",
            ОбщегоНазначения.КодОсновногоЯзыка()),
        УровеньЖурналаРегистрации.Ошибка,
        ДокументОбъект.Метаданные(),
        ДокументОбъект.Ссылка,
        ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
    
    ВызватьИсключение;
КонецПопытки;
```

### Standard event names

```bsl
// Имена событий — иерархические, через точку
// Подсистема.Событие или Объект.Действие
ИмяСобытия = НСтр("ru = 'ОбменДанными.ОтправкаДанных'");
ИмяСобытия = НСтр("ru = 'Проведение документа.Ошибка'");
ИмяСобытия = НСтр("ru = 'ЗагрузкаДанных.ИзВнешнегоИсточника'");
```

---

## Rule 6: РаботаСФайлами — use instead of direct FileSystem calls

**Why:** Direct interaction with the file system via `Новый Файл()`, `КопироватьФайл()`, `ЗначениеВФайл()` does not consider:
- Access rights (file system vs 1С rights)
- Antivirus checks
- Temporary files (might remain undeleted)
- Cross-platform compatibility (Windows/Linux)

БСП provides the `РаботаСФайлами` module that handles all these cases.

### Example: obtaining a temporary file

```bsl
// ❌ ПЛОХО: прямое создание файла — можно забыть удалить
ИмяВременногоФайла = ПолучитьИмяВременногоФайла("xlsx");
ТабличныйДокумент.Записать(ИмяВременногоФайла, ТипФайлаТабличногоДокумента.XLSX);
// ... работа с файлом ...
// Если ошибка — файл останется навсегда

// ✅ ПРАВИЛЬНО: через БСП — временные файлы управляются автоматически
ИмяВременногоФайла = ПолучитьИмяВременногоФайла("xlsx");
Попытка
    ТабличныйДокумент.Записать(ИмяВременногоФайла, ТипФайлаТабличногоДокумента.XLSX);
    // ... работа с файлом ...
Исключение
    // Обработка ошибки
КонецПопытки;

// Явное удаление
УдалитьФайлы(ИмяВременногоФайла);
```

---

## Rule 7: Typical БСП patterns for common tasks

### Pattern: Validation checks (ОбработкаПроверкиЗаполнения)

```bsl
// Модуль объекта — стандартный обработчик проверки заполнения
Процедура ОбработкаПроверкиЗаполнения(Отказ, ПроверяемыеРеквизиты)
    
    // Добавляем свои проверки к стандартным
    Если НЕ ЗначениеЗаполнено(Контрагент) Тогда
        ОбщегоНазначения.СообщитьПользователю(
            НСтр("ru = 'Не заполнен контрагент.'"),
            ЭтотОбъект, "Контрагент",, Отказ);
    КонецЕсли;
    
    // Условное исключение реквизитов из проверки
    // (например, если вид операции не требует склада)
    Если ВидОперации = Перечисления.ВидыОпераций.Услуга Тогда
        ОбщегоНазначенияКлиентСервер.УдалитьЗначениеИзМассива(
            ПроверяемыеРеквизиты, "Склад");
    КонецЕсли;
    
КонецПроцедуры
```

### Pattern: Preparing data for printing

```bsl
// Использование модуля УправлениеПечатью для формирования печатных форм
Процедура Печать(МассивОбъектов, ПараметрыПечати, КоллекцияПечатныхФорм,
        ОбъектыПечати, ПараметрыВывода) Экспорт
    
    Если УправлениеПечатью.НужноПечататьМакет(КоллекцияПечатныхФорм, "Счёт") Тогда
        
        ТабличныйДокумент = Новый ТабличныйДокумент;
        ТабличныйДокумент.КлючПараметровПечати = "Документ.РеализацияТоваровУслуг.Счёт";
        
        // ... формирование печатной формы ...
        
        УправлениеПечатью.ВывестиТабличныйДокументВКоллекцию(
            КоллекцияПечатныхФорм, "Счёт", НСтр("ru = 'Счёт на оплату'"),
            ТабличныйДокумент);
    КонецЕсли;
    
КонецПроцедуры
```

### Pattern: Access rights handling

```bsl
// Проверка прав через БСП (вместо прямого обращения к ПравоДоступа())
Если НЕ ОбщегоНазначения.ПравоДоступаОбъекта("Изменение",
        Метаданные.Документы.РеализацияТоваровУслуг) Тогда
    
    ВызватьИсключение НСтр("ru = 'Недостаточно прав для изменения документов реализации.'");
    
КонецЕсли;
```

---

## Rule 8: Do not duplicate БСП functionality

**Why:** Each custom function is code that needs to be maintained, tested, and updated. БСП already does that for you. Duplication leads to:
- Two sources of truth — your code vs БСП
- Conflicts when updating the configuration
- Missed edge cases (БСП accounts for them, your code probably does not)

### Common duplication cases

| What is often written manually | What exists in БСП |
|------------------------------|--------------------|
| Function to get an attribute by reference | `ОбщегоНазначения.ЗначениеРеквизитаОбъекта()` |
| Check for a filled value | `ЗначениеЗаполнено()` (platform) + `ОбщегоНазначенияКлиентСервер.ПроверитьПараметр()` |
| String construction with substitution | `СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку()` |
| Word declension | `СтроковыеФункцииКлиентСервер.СтрокаСЧисломПредметов()` |
| Sending mail | `РаботаСПочтовымиСообщениями` |
| Fetching an exchange rate | `РаботаСКурсамиВалют.ПолучитьКурсВалюты()` |
| Logging to the journal | Standard `ЗаписьЖурналаРегистрации()` according to БСП rules |


---

## Rule 9: “КлиентСервер” modules — separation of responsibilities

**Why:** In БСП modules are organized by the execution environment:

| Module suffix | Environment | Example |
|---------------|-------------|---------|
| (without suffix) | Server | `ОбщегоНазначения` |
| `Клиент` | Client | `ОбщегоНазначенияКлиент` |
| `КлиентСервер` | Both environments | `ОбщегоНазначенияКлиентСервер` |
| `Сервер` (explicit) | Server only | `СтандартныеПодсистемыСервер` |
| `ПовтИсп` | Server, with caching | `ОбщегоНазначенияПовтИсп` |

**Selection rules:**
- For client form code — search in `*КлиентСервер` first, then in `*Клиент`
- For server code — search primarily in the module without suffix or in `*Сервер`
- `*ПовтИсп` functions cache results for the session; use them for frequently requested reference data

### Example: correct module selection

```bsl
// На клиенте — используем КлиентСервер модуль
&НаКлиенте
Процедура КонтрагентПриИзменении(Элемент)
    // ОбщегоНазначенияКлиентСервер — доступен на клиенте
    Если НЕ ЗначениеЗаполнено(Объект.Контрагент) Тогда
        Возврат;
    КонецЕсли;
    
    // Для серверного вызова используем БезКонтекста
    ДанныеКонтрагента = ПолучитьДанныеКонтрагентаНаСервере(Объект.Контрагент);
КонецПроцедуры

// На сервере — используем серверный модуль
&НаСервереБезКонтекста
Функция ПолучитьДанныеКонтрагентаНаСервере(КонтрагентСсылка)
    // ОбщегоНазначения — серверный модуль
    Возврат ОбщегоНазначения.ЗначенияРеквизитовОбъекта(
        КонтрагентСсылка, "Наименование, ИНН, КПП");
КонецФункции
```


---

depends_on: []
---
