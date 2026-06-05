---
name: ssl-patterns
description: "MUST use WHEN you use or extend functionality of БСП (Standard Subsystems Library). Provides a catalog of ready-made ОбщегоНазначения functions and rules for calling subsystems without duplication."
alwaysApply: false
---

# Patterns for working with БСП (Standard Subsystems Library)

БСП code is tested on millions of installations, updated centrally, and familiar to other developers. Duplicating БСП is an anti-pattern.

---

## Rule 1: The ОбщегоНазначения module is the main "Swiss Army knife"

Before writing your own implementation, check whether БСП already has a ready-made function.

| Function | When to use |
|---------|-------------------|
| `ЗначениеРеквизитаОбъекта()` | Instead of `Ссылка.Реквизит` (avoid dot notation) |
| `ЗначенияРеквизитовОбъекта()` | Several attributes in one call |
| `СообщитьПользователю()` | Message tied to a field (instead of `Сообщить()`) |
| `МенеджерОбъектаПоСсылке()` | Instead of `Выполнить("Справочники." + Имя)` |
| `ПодсистемаСуществует()` | Conditional module invocation |
| `ОбщийМодуль()` | Dynamic call to a БСП module |
| `ЭтоСсылка()` | Parameter validation |
| `СсылкаСуществует()` | Check before access |

```bsl
// ПЛОХО: three database accesses through dot notation
Наименование = КонтрагентСсылка.Наименование;
ИНН = КонтрагентСсылка.ИНН;
Ответственный = КонтрагентСсылка.ОсновнойМенеджер;

// ПРАВИЛЬНО: one access through БСП
РеквизитыКонтрагента = ОбщегоНазначения.ЗначенияРеквизитовОбъекта(
    КонтрагентСсылка,
    "Наименование, ИНН, ОсновнойМенеджер");
```

---

## Rule 2: СтроковыеФункцииКлиентСервер is for string handling

The module contains optimized functions that handle edge cases correctly.

| Function | When to use |
|---------|-------------------|
| `ПодставитьПараметрыВСтроку()` | An equivalent of `СтрШаблон()`, with additional checks |
| `СтрокаСЧисломПредметов()` | Declension: "5 documents", "1 document" |
| `ЕстьНедопустимыеСимволы()` | Input validation |
| `ТолькоЦифрыВСтроке()` | Validation of INN, KPP |
| `РазложитьСтрокуВМассивПодстрок()` | Parsing by delimiter |

```bsl
// Declension: "1 document", "2 documents", "5 documents"
ТекстОповещения = СтроковыеФункцииКлиентСервер.СтрокаСЧисломПредметов(
    КоличествоДокументов,
    НСтр("ru = 'документ, документа, документов'"));
```

---

## Rule 3: ОбщегоНазначенияКлиентСервер are utilities for both environments

The directive `&НаКлиентеНаСервереБезКонтекста` is available on both client and server.

| Function | Description |
|---------|----------|
| `ДополнитьМассив()` | Merge two arrays |
| `ДополнитьСтруктуру()` | Merge two structures |
| `СвойствоСтруктуры()` | Safe property read (default value if absent) |
| `ПроверитьПараметр()` | Type validation with an informative error |

```bsl
// Safe access with a default value
ДатаНачала = ОбщегоНазначенияКлиентСервер.СвойствоСтруктуры(
    ПараметрыОтчёта, "ДатаНачала", НачалоГода(ТекущаяДатаСеанса()));
```

---

## Rule 4: Strategy for finding БСП functions

### Algorithm: LSP -> grep -> AI

1. **LSP** (if available): `navigate_symbol("ЗначенияРеквизитовОбъекта")`
2. **Text search**: `grep -r "Функция.*КурсВалюты" src/CommonModules/`
3. **AI assistant**: "Is there a БСП function for getting the exchange rate on a date?"

### When to write your own vs use БСП

| Situation | Decision |
|----------|---------|
| БСП has a suitable function | **Use БСП** |
| БСП has a similar function, but with extra functionality | **Use БСП** - extra functionality does not hurt |
| The needed function is not in БСП | Write your own in the БСП style |
| Configuration without БСП | Write your own |

---

## Rule 5: Working with the registration log through БСП

See `error-handling`, rule 7.

---

## Rule 6: РаботаСФайлами instead of direct FileSystem access

Direct file handling does not account for access rights, temporary files, or cross-platform compatibility.

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

### Fill validation (ОбработкаПроверкиЗаполнения)

```bsl
Процедура ОбработкаПроверкиЗаполнения(Отказ, ПроверяемыеРеквизиты)

    Если НЕ ЗначениеЗаполнено(Контрагент) Тогда
        ОбщегоНазначения.СообщитьПользователю(
            НСтр("ru = 'Не заполнен контрагент.'"),
            ЭтотОбъект, "Контрагент",, Отказ);
    КонецЕсли;

    // Conditionally exclude attributes from the check
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

| What people often write themselves | What is in БСП |
|----------------------|----------------|
| Get an attribute by reference | `ОбщегоНазначения.ЗначениеРеквизитаОбъекта()` |
| String substitution | `СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку()` |
| Word declension | `СтроковыеФункцииКлиентСервер.СтрокаСЧисломПредметов()` |
| Sending mail | `РаботаСПочтовымиСообщениями` |
| Exchange rate | `РаботаСКурсамиВалют.ПолучитьКурсВалюты()` |
| Long-running operation in the background | `ДлительныеОперации.ВыполнитьФункцию()` |
| Secret / password storage | `БезопасноеХранилище.ПрочитатьДанные()` |
| Access right profiles | `ГруппыДоступаПользователей` / `ПрофилиГруппДоступа` |
| Registering an external processor | `СведенияОВнешнейОбработке()` |

---

## Rule 9: "КлиентСервер" modules - responsibility split

| Module suffix | Environment | Example |
|----------------|-------|--------|
| (no suffix) | Server | `ОбщегоНазначения` |
| `Клиент` | Client | `ОбщегоНазначенияКлиент` |
| `КлиентСервер` | Both environments | `ОбщегоНазначенияКлиентСервер` |
| `ПовтИсп` | Server, with caching | `ОбщегоНазначенияПовтИсп` |

For client-side form code, first look in `*КлиентСервер`, then in `*Клиент`. For server-side code, look primarily in the main module (without suffix). `*ПовтИсп` is for frequently requested reference data.

---

## Rule 10: Long-running operations (ДлительныеОперации)

Use the `ДлительныеОперации` subsystem for any server work that takes longer than about 3 seconds. Do not block the UI with a homemade wait loop.

```bsl
// Launch a background task
&НаСервере
Функция ЗапуститьОперацию(Параметры)
    ПараметрыФона = ДлительныеОперации.ПараметрыВыполненияВФоне(УникальныйИдентификатор);
    ПараметрыФона.НаименованиеФоновогоЗадания = НСтр("ru = 'Обработка данных'");
    Возврат ДлительныеОперации.ВыполнитьФункцию("ОбщийМодуль.ФункцияДляФона",
        ПараметрыФона, Параметры);
КонецФункции

// Connect waiting on the client
&НаКлиенте
Процедура ЗапуститьОперациюНаКлиенте()
    Операция = ЗапуститьОперацию(ПараметрыРасчёта);
    ПараметрыОжидания = ДлительныеОперацииКлиент.ПараметрыОжидания(ЭтотОбъект);
    ПараметрыОжидания.ВыводитьПрогресс = Истина;
    ДлительныеОперацииКлиент.ОжидатьЗавершение(Операция,
        Новый ОписаниеОповещения("ОперацияЗавершена", ЭтотОбъект), ПараметрыОжидания);
КонецПроцедуры

// Handle the result
&НаКлиенте
Процедура ОперацияЗавершена(Операция, ДополнительныеПараметры) Экспорт
    Если Операция = Неопределено Тогда
        Возврат; // Canceled by the user
    КонецЕсли;
    Если Операция.Статус = "Ошибка" Тогда
        СтандартныеПодсистемыКлиент.ОбработатьОшибкуФоновогоЗадания(Операция);
        Возврат;
    КонецЕсли;
    // Get result
    РезультатОперации = ПолучитьРезультатСервер(Операция.АдресРезультата);
КонецПроцедуры
```

**Key rules:**
- Pass progress through `ДлительныеОперации.СообщитьПрогресс()` inside the background procedure.
- Do not store state between steps in global variables - use job parameters.
- Implement idempotent restart: a repeated call with the same parameters must produce the same result.

---

## Rule 11: Secure storage (БезопасноеХранилище)

Never store passwords, tokens, or secrets in:
- metadata object attributes
- configuration constants
- the registration log
- version control systems (configs, xml)

```bsl
// Write secret
БезопасноеХранилище.Записать(ЭтотОбъект, Новый Структура("Пароль", ПарольПользователя));

// Read secret
ДанныеХранилища = БезопасноеХранилище.ПрочитатьДанные(ЭтотОбъект);
Если ДанныеХранилища <> Неопределено Тогда
    Пароль = ДанныеХранилища.Пароль;
КонецЕсли;

// Delete when deleting the object
БезопасноеХранилище.Удалить(ЭтотОбъект);
```

In the object's `ПередУдалением` handler, always call `БезопасноеХранилище.Удалить()` - otherwise "orphaned" records accumulate in the storage.

---

## Rule 12: Access group profiles (ПрофилиГруппДоступа)

When developing subsystems with role-based access, use the БСП profile mechanism instead of assigning roles directly.

```bsl
// Example of a profile description in ОписаниеПрофилейГруппДоступа()
Профиль = УправлениеДоступом.ОписаниеПрофиля();
Профиль.Идентификатор = "ИдентификаторПрофиля_UUID";
Профиль.Наименование   = НСтр("ru = 'Менеджер по продажам'");
Профиль.Роли.Добавить("РольМенеджерПродаж");
Профили.Добавить(Профиль);
```

**Key rules:**
- The profile identifier is a fixed UUID and does not change when renamed.
- For elevated privileges, use `ПривилегированныйРежим()` strictly locally, and disable it immediately after the operation.
- Perform permission checks through `УправлениеДоступом.ПроверитьДопустимостьДействия()`, not directly through `РольДоступна()` - the latter does not take RLS into account.

---

## Rule 13: External processors and extensions (СведенияОВнешнейОбработке)

Registering an external processor in a БСП-based configuration requires the `СведенияОВнешнейОбработке()` function in the processor's main module.

```bsl
// In the processor module
Функция СведенияОВнешнейОбработке() Экспорт

    СведенияОВнешнейОбработке = ДополнительныеОтчётыИОбработки.СведенияОВнешнейОбработке();
    СведенияОВнешнейОбработке.Вид = ДополнительныеОтчётыИОбработкиКлиентСервер
        .ВидОбработки().ДополнительнаяОбработка;
    СведенияОВнешнейОбработке.Наименование = НСтр("ru = 'Моя обработка'");
    СведенияОВнешнейОбработке.Версия       = "1.0";
    СведенияОВнешнейОбработке.БезопасныйРежим = Истина;

    // Command description
    Команда = СведенияОВнешнейОбработке.Команды.Добавить();
    Команда.Представление = НСтр("ru = 'Выполнить'");
    Команда.Идентификатор = "Выполнить";
    Команда.ИспользованиеКонтекста = ДополнительныеОтчётыИОбработкиКлиентСервер
        .ИспользованиеКонтекстаКоманды().ВПроцедуреВыполнитьКоманду;

    Возврат СведенияОВнешнейОбработке;

КонецФункции

// Entry point for the command
Процедура ВыполнитьКоманду(Идентификатор, ПараметрыКоманды, ОбъектыНазначения) Экспорт
    // ... implementation ...
КонецПроцедуры
```

---

## Searching for analogs via Buddy

If `search_ssl_functions` did not return a result, use `ask_ai_assistant` (VALIDATE_BSL template from `buddy-prompting`): pass a code fragment and get recommendations for replacing it with БСП methods. Also use `SEARCH_DOCS` for documentation on a specific БСП method.

---
depends_on: []
---
