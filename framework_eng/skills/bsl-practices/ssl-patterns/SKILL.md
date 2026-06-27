---
name: ssl-patterns
description: "Check ready BSP/SSL mechanisms before custom logic"
uses_capabilities:
  - get_signature_help
alwaysApply: false
---

# Patterns for Working with BСП (Standard Subsystems Library)

BСП code is battle-tested on millions of installations, updated centrally, and familiar to other developers. Duplicating BСП is an antipattern.

> **BСП function signatures are available through `get_signature_help`.** Functions from `ОбщегоНазначения` and
> other BСП modules have many parameters and overloads; do not guess the order or set of arguments.
> At the call site, `get_signature_help(uri, line, character)` shows the parameters and overloads
> of the invoked method right in place - without opening the BСП module definition. Use it when calling
> any function from the catalog below if you are unsure about the signature.

---

## Rule 1: The `ОбщегоНазначения` module is the main "Swiss army knife"

Before writing your own implementation, check whether BСП already has a ready-made function.

| Function | When to use |
|---------|---|
| `ЗначениеРеквизитаОбъекта()` | Instead of `Ссылка.Реквизит` (avoid dot notation) |
| `ЗначенияРеквизитовОбъекта()` | Several attributes in one call |
| `СообщитьПользователю()` | Message bound to a field (instead of `Сообщить()`) |
| `МенеджерОбъектаПоСсылке()` | Instead of `Выполнить("Справочники." + Имя)` |
| `ПодсистемаСуществует()` | Conditional invocation of modules |
| `ОбщийМодуль()` | Dynamic invocation of a BСП module |
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
|---------|---|
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
|---------|---|
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

## Rule 4: BСП function search strategy

### Algorithm: LSP -> grep -> AI

1. **LSP** (if available): `navigate_symbol("ЗначенияРеквизитовОбъекта")`
2. **Text search**: `grep -r "Функция.*КурсВалюты" src/CommonModules/`
3. **AI assistant**: "Is there a BСП function for getting the exchange rate on a date?"

### When to write your own vs use BСП

| Situation | Decision |
|---|---|
| BСП has a suitable function | **Use BСП** |
| BСП has a similar function, but with extra functionality | **Use BСП** - the extra does not hurt |
| The needed function does not exist in BСП | Write your own in BСП style |
| Configuration without BСП | Write your own |

---

## Rule 5: Working with the registration log through BСП

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

## Rule 7: Typical BСП patterns

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

## Rule 8: Do not duplicate BСП functionality

| What people often write themselves | What exists in BСП |
|---|---|
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

When developing subsystems with role-based access, use the BСП profile mechanism instead of assigning roles directly.

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

Registering an external processing object in a BСП-based configuration requires the `СведенияОВнешнейОбработке()` function in the main module of the processing object.

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

If `search_ssl_functions` did not return a result, use `ask_ai_assistant` (VALIDATE_BSL template from `buddy-prompting`): pass a code fragment and get recommendations for replacing it with BСП methods. Also use `SEARCH_DOCS` for documentation on a specific BСП method.

---
depends_on: []
---
