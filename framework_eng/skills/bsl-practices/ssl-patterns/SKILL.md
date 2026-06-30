---
name: ssl-patterns
description: "Before writing logic on БСП, check the ready-made mechanisms; use versioned БСП 3.1.11 references for exact modules, signatures, stable/service/deprecated API, override hooks, plug-in commands, printing, files, access, data exchange, background jobs, and other subsystems"
uses_capabilities:
  - get_signature_help
alwaysApply: false
metadata:
  bsp_reference_version: "БСП 3.1.11"
  borrowed_from: "https://github.com/brake71/1c-ssl-skills"
  borrowed_commit: "85783eececb3a658ea15fc793b095ac370b5339c"
  borrowed_at: "2026-06-30"
  borrowed_table: "references/bsp-borrowings.md"
---

# Working Patterns with БСП (Standard Subsystems Library)

БСП code has been tested on millions of installations, is updated centrally, and is familiar to other developers. Duplicating БСП is an antipattern.

> **Use `get_signature_help` for БСП function signatures.** Functions from `ОбщегоНазначения` and
> other БСП modules have many parameters and overloads; do not guess the order or set of arguments.
> At the call site, `get_signature_help(uri, line, character)` shows the parameters and overloads
> of the called method right where it is used - without opening the БСП module definition. Use it when calling
> any function from the catalog below if you are unsure about the signature.

## Versioned БСП 3.1.11 map

This skill contains a borrowed reference layer for БСП 3.1.11:

- `references/bsp-3.1.11/*.md` - scenario guides for БСП subsystems: module, method, signature, API region, example, nuances, and anti-patterns.
- `scripts/bsp_api.py` - local search for a method/module in the `src/cf` configuration export with detection of the `#Область` region.
- `references/bsp-borrowings.md` - borrowing table: upstream version, relation to our skills, and the value of the additional knowledge.

If the task concerns a specific БСП subsystem, first open the corresponding reference from the table below. If the reference and the current configuration differ, the source configuration and verification through `get_signature_help` / `scripts/bsp_api.py` take priority.

| Task / subsystem | Reference |
|---|---|
| Module suffixes, stable/service/deprecated, `*Переопределяемый` hooks, subsystem map | `references/bsp-3.1.11/fundamentals.md` |
| `ОбщегоНазначения*`, strings, dates, attributes by reference, XML/JSON, secure storage | `references/bsp-3.1.11/base-common.md` |
| Long-running operations, background and scheduled jobs | `references/bsp-3.1.11/longs-and-jobs.md` |
| Users, RLS, access group profiles, external users | `references/bsp-3.1.11/users-access.md` |
| Plug-in commands, additional reports and processing objects | `references/bsp-3.1.11/commands-external.md` |
| Printing, print manager, report variants, СКД | `references/bsp-3.1.11/print-reports.md` |
| Properties, edit lock, change lock dates | `references/bsp-3.1.11/forms-validation.md` |
| Files, volumes, object versions, exporting objects to files | `references/bsp-3.1.11/files-and-versions.md` |
| Data exchange, exchange plans, synchronization, SaaS areas | `references/bsp-3.1.11/data-exchange.md` |
| Mail, SMS, message templates, discussions, interactions | `references/bsp-3.1.11/comms.md` |
| Contact information, addresses, address classifier | `references/bsp-3.1.11/contact-info.md` |
| Currencies, exchange rates, banks, work schedules and calendars | `references/bsp-3.1.11/currencies-banks.md` |
| Number/code prefixes and IB prefix | `references/bsp-3.1.11/prefixes.md` |
| IB version update and update handlers | `references/bsp-3.1.11/update.md` |
| Electronic signature, MCD, cryptography, DSS | `references/bsp-3.1.11/esign-mcd.md` |
| Administration, backups, monitoring, personal data, duplicates, classifiers, external components | see the remaining `references/bsp-3.1.11/*.md` |

---

## Rule 1: The `ОбщегоНазначения` module is the main "Swiss army knife"

Before writing your own implementation, check whether БСП already has a ready-made function.

| Function | When to use |
|---------|-------------------|
| `ЗначениеРеквизитаОбъекта()` | Instead of `Ссылка.Реквизит` (avoid dot notation) |
| `ЗначенияРеквизитовОбъекта()` | Several attributes in one call |
| `СообщитьПользователю()` | Message bound to a field (instead of `Сообщить()`) |
| `МенеджерОбъектаПоСсылке()` | Instead of `Выполнить("Справочники." + Имя)` |
| `ПодсистемаСуществует()` | Conditional invocation of modules |
| `ОбщийМодуль()` | Dynamic invocation of a БСП module |
| `ЭтоСсылка()` | Parameter validation |
| `СсылкаСуществует()` | Check before accessing |

```bsl
// ПЛОХО: три обращения к БД через точку
Наименование = КонтрагентСсылка.Наименование;
ИНН = КонтрагентСсылка.ИНН;
Ответственный = КонтрагентСсылка.ОсновнойМенеджер;

// ПРАВИЛЬНО: одно обращение через БСП
РеквизитыКонтрагента = ОбщегоНазначения.ЗначенияРеквизитовОбъекта(
    КонтрагентСсылка,
    "Наименование, ИНН, ОсновнойМенеджер");
```

---

## Rule 2: `СтроковыеФункцииКлиентСервер` - working with strings

The module contains optimized functions that correctly handle edge cases.

| Function | When to use |
|---------|-------------------|
| `ПодставитьПараметрыВСтроку()` | Equivalent of `СтрШаблон()`, with additional checks |
| `СтрокаСЧисломПредметов()` | Declension: "5 documents", "1 document" |
| `ЕстьНедопустимыеСимволы()` | Input validation |
| `ТолькоЦифрыВСтроке()` | Validation of INN, KPP |
| `РазложитьСтрокуВМассивПодстрок()` | Parsing by delimiter |

```bsl
// Склонение: «1 документ», «2 документа», «5 документов»
ТекстОповещения = СтроковыеФункцииКлиентСервер.СтрокаСЧисломПредметов(
    КоличествоДокументов,
    НСтр("ru = 'документ, документа, документов'"));
```

---

## Rule 3: `ОбщегоНазначенияКлиентСервер` - utilities for both environments

The directive `&НаКлиентеНаСервереБезКонтекста` means it is available on both the client and the server.

| Function | Description |
|---------|----------|
| `ДополнитьМассив()` | Merge two arrays |
| `ДополнитьСтруктуру()` | Merge two structures |
| `СвойствоСтруктуры()` | Safe property read (default value if missing) |
| `ПроверитьПараметр()` | Type validation with an informative error |

```bsl
// Безопасный доступ с значением по умолчанию
ДатаНачала = ОбщегоНазначенияКлиентСервер.СвойствоСтруктуры(
    ПараметрыОтчёта, "ДатаНачала", НачалоГода(ТекущаяДатаСеанса()));
```

---

## Rule 4: БСП function search strategy

### Algorithm: LSP -> grep -> AI

1. **LSP** (if available): `navigate_symbol("ЗначенияРеквизитовОбъекта")`
2. **Text search**: `grep -r "Функция.*КурсВалюты" src/CommonModules/`
3. **AI assistant**: "Is there a БСП function for getting the exchange rate on a date?"

### When to write your own vs use БСП

| Situation | Decision |
|----------|---------|
| БСП has a suitable function | **Use БСП** |
| БСП has a similar function, but with extra functionality | **Use БСП** - the extra does not hurt |
| The needed function does not exist in БСП | Write your own in БСП style |
| Configuration without БСП | Write your own |

---

## Rule 5: Working with the registration log through БСП

See `error-handling`, rule 7.

---

## Rule 6: `РаботаСФайлами` - instead of direct `ФайловаяСистема`

Direct file handling does not account for: access rights, temporary files, cross-platform compatibility.

```bsl
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

## Rule 7: Typical БСП patterns

### Fill check (`ОбработкаПроверкиЗаполнения`)

```bsl
Процедура ОбработкаПроверкиЗаполнения(Отказ, ПроверяемыеРеквизиты)

    Если НЕ ЗначениеЗаполнено(Контрагент) Тогда
        ОбщегоНазначения.СообщитьПользователю(
            НСтр("ru = 'Не заполнен контрагент.'"),
            ЭтотОбъект, "Контрагент",, Отказ);
    КонецЕсли;

    // Условное исключение реквизитов из проверки
    Если ВидОперации = Перечисления.ВидыОпераций.Услуга Тогда
        ОбщегоНазначенияКлиентСервер.УдалитьЗначениеИзМассива(
            ПроверяемыеРеквизиты, "Склад");
    КонецЕсли;

КонецПроцедуры
```

### Getting data for printing

```bsl
Процедура Печать(МассивОбъектов, ПараметрыПечати, КоллекцияПечатныхФорм,
        ОбъектыПечати, ПараметрыВывода) Экспорт

    Если УправлениеПечатью.НужноПечататьМакет(КоллекцияПечатныхФорм, "Счёт") Тогда

        ТабличныйДокумент = Новый ТабличныйДокумент;
        ТабличныйДокумент.КлючПараметровПечати = "Документ.РеализацияТоваровУслуг.Счёт";

        УправлениеПечатью.ВывестиТабличныйДокументВКоллекцию(
            КоллекцияПечатныхФорм, "Счёт", НСтр("ru = 'Счёт на оплату'"),
            ТабличныйДокумент);
    КонецЕсли;

КонецПроцедуры
```

---

## Rule 8: Do not duplicate БСП functionality

| What people often write themselves | What exists in БСП |
|----------------------|----------------|
| Get an attribute by reference | `ОбщегоНазначения.ЗначениеРеквизитаОбъекта()` |
| String substitution | `СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку()` |
| Word declension | `СтроковыеФункцииКлиентСервер.СтрокаСЧисломПредметов()` |
| Sending mail | `РаботаСПочтовымиСообщениями` |
| Exchange rate | `РаботаСКурсамиВалют.ПолучитьКурсВалюты()` |
| Long-running background operation | `ДлительныеОперации.ВыполнитьФункцию()` |
| Storing secrets / passwords | `БезопасноеХранилище.ПрочитатьДанные()` |
| Access rights profiles | `ГруппыДоступаПользователей` / `ПрофилиГруппДоступа` |
| Registering an external processing object | `СведенияОВнешнейОбработке()` |

---

## Rule 9: "ClientServer" modules - separation of responsibilities

| Module suffix | Environment | Example |
|---|---|---|
| (no suffix) | Server | `ОбщегоНазначения` |
| `Клиент` | Client | `ОбщегоНазначенияКлиент` |
| `КлиентСервер` | Both environments | `ОбщегоНазначенияКлиентСервер` |
| `ПовтИсп` | Server, with caching | `ОбщегоНазначенияПовтИсп` |

For client-side form code, search first in `*КлиентСервер`, then in `*Клиент`. For server-side code, use the main module (without suffix). `*ПовтИсп` is for frequently requested reference data.

---

## Rule 10: Long-running operations (`ДлительныеОперации`)

Use the `ДлительныеОперации` subsystem for any server-side work longer than ~3 seconds. Do not block the UI with a hand-rolled wait loop.

```bsl
// Запуск фоновой задачи
&НаСервере
Функция ЗапуститьОперацию(Параметры)
    ПараметрыФона = ДлительныеОперации.ПараметрыВыполненияВФоне(УникальныйИдентификатор);
    ПараметрыФона.НаименованиеФоновогоЗадания = НСтр("ru = 'Обработка данных'");
    Возврат ДлительныеОперации.ВыполнитьФункцию("ОбщийМодуль.ФункцияДляФона",
        ПараметрыФона, Параметры);
КонецФункции

// Подключение ожидания на клиенте
&НаКлиенте
Процедура ЗапуститьОперациюНаКлиенте()
    Операция = ЗапуститьОперацию(ПараметрыРасчёта);
    ПараметрыОжидания = ДлительныеОперацииКлиент.ПараметрыОжидания(ЭтотОбъект);
    ПараметрыОжидания.ВыводитьПрогресс = Истина;
    ДлительныеОперацииКлиент.ОжидатьЗавершение(Операция,
        Новый ОписаниеОповещения("ОперацияЗавершена", ЭтотОбъект), ПараметрыОжидания);
КонецПроцедуры

// Обработка результата
&НаКлиенте
Процедура ОперацияЗавершена(Операция, ДополнительныеПараметры) Экспорт
    Если Операция = Неопределено Тогда
        Возврат; // Отменена пользователем
    КонецЕсли;
    Если Операция.Статус = "Ошибка" Тогда
        СтандартныеПодсистемыКлиент.ОбработатьОшибкуФоновогоЗадания(Операция);
        Возврат;
    КонецЕсли;
    // Получить результат
    РезультатОперации = ПолучитьРезультатСервер(Операция.АдресРезультата);
КонецПроцедуры
```

**Key rules:**
- Pass progress through `ДлительныеОперации.СообщитьПрогресс()` inside the background procedure.
- Do not store state between steps in global variables - use task parameters.
- Implement idempotent restart: a repeated call with the same parameters should produce the same result.

---

## Rule 11: Secure storage (`БезопасноеХранилище`)

Never store passwords, tokens, or secrets in:
- metadata object attributes
- configuration constants
- the registration log
- version control system (configs, xml)

```bsl
// Запись секрета
БезопасноеХранилище.Записать(ЭтотОбъект, Новый Структура("Пароль", ПарольПользователя));

// Чтение секрета
ДанныеХранилища = БезопасноеХранилище.ПрочитатьДанные(ЭтотОбъект);
Если ДанныеХранилища <> Неопределено Тогда
    Пароль = ДанныеХранилища.Пароль;
КонецЕсли;

// Удаление при удалении объекта
БезопасноеХранилище.Удалить(ЭтотОбъект);
```

In an object `ПередУдалением` handler, always call `БезопасноеХранилище.Удалить()` - otherwise "orphaned" records accumulate in storage.

---

## Rule 12: Access group profiles (`ПрофилиГруппДоступа`)

When developing subsystems with role-based access, use the БСП profile mechanism instead of assigning roles directly.

```bsl
// Пример описания профиля в ОписаниеПрофилейГруппДоступа()
Профиль = УправлениеДоступом.ОписаниеПрофиля();
Профиль.Идентификатор = "ИдентификаторПрофиля_UUID";
Профиль.Наименование   = НСтр("ru = 'Менеджер по продажам'");
Профиль.Роли.Добавить("РольМенеджерПродаж");
Профили.Добавить(Профиль);
```

**Key rules:**
- The profile identifier is a fixed UUID and does not change when renamed.
- For elevated privileges, use `ПривилегированныйРежим()` strictly locally, and turn it off immediately after the operation.
- Check permissions through `УправлениеДоступом.ПроверитьДопустимостьДействия()`, not directly through `РольДоступна()` - the latter does not account for RLS.

---

## Rule 13: External processing objects and extensions (`СведенияОВнешнейОбработке`)

Registering an external processing object in a БСП-based configuration requires the `СведенияОВнешнейОбработке()` function in the main module of the processing object.

```bsl
// В модуле обработки
Функция СведенияОВнешнейОбработке() Экспорт

    СведенияОВнешнейОбработке = ДополнительныеОтчётыИОбработки.СведенияОВнешнейОбработке();
    СведенияОВнешнейОбработке.Вид = ДополнительныеОтчётыИОбработкиКлиентСервер
        .ВидОбработки().ДополнительнаяОбработка;
    СведенияОВнешнейОбработке.Наименование = НСтр("ru = 'Моя обработка'");
    СведенияОВнешнейОбработке.Версия       = "1.0";
    СведенияОВнешнейОбработке.БезопасныйРежим = Истина;

    // Описание команды
    Команда = СведенияОВнешнейОбработке.Команды.Добавить();
    Команда.Представление = НСтр("ru = 'Выполнить'");
    Команда.Идентификатор = "Выполнить";
    Команда.ИспользованиеКонтекста = ДополнительныеОтчётыИОбработкиКлиентСервер
        .ИспользованиеКонтекстаКоманды().ВПроцедуреВыполнитьКоманду;

    Возврат СведенияОВнешнейОбработке;

КонецФункции

// Точка входа для команды
Процедура ВыполнитьКоманду(Идентификатор, ПараметрыКоманды, ОбъектыНазначения) Экспорт
    // ... реализация ...
КонецПроцедуры
```

---

## Searching for analogs through Buddy

If `search_ssl_functions` did not return a result, use `ask_ai_assistant` (VALIDATE_BSL template from `buddy-prompting`): pass a code fragment and get recommendations for replacing it with БСП methods. Also use `SEARCH_DOCS` for documentation on a specific БСП method.

---
depends_on: []
---
