---
name: ssl-patterns
description: "Patterns for working with БСП (Standard Subsystems Library). This skill teaches the agent to use БСП correctly (Standard Subsystems Library, Eng."
---

# Patterns for Working with БСП (Standard Subsystems Library)

БСП code has been proven on millions of installations, is updated centrally, and is familiar to other developers. Duplicating БСП is an anti-pattern.

---

## Rule 1: `ОбщегоНазначения` module is the main “Swiss army knife”

Before writing your own implementation, check whether БСП already has a ready-made function.

| Function | When to use |
|---------|-------------------|
| `ЗначениеРеквизитаОбъекта()` | Instead of `Ссылка.Реквизит` (avoid dot notation) |
| `ЗначенияРеквизитовОбъекта()` | Several attributes in one call |
| `СообщитьПользователю()` | A message bound to a field (instead of `Сообщить()`) |
| `МенеджерОбъектаПоСсылке()` | Instead of `Выполнить("Справочники." + Имя)` |
| `ПодсистемаСуществует()` | Conditional module invocation |
| `ОбщийМодуль()` | Dynamic invocation of a БСП module |
| `ЭтоСсылка()` | Parameter validation |
| `СсылкаСуществует()` | Check before access |

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
| `ПодставитьПараметрыВСтроку()` | An analogue of `СтрШаблон()`, with additional checks |
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

Directive `&НаКлиентеНаСервереБезКонтекста` - available on both client and server.

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

## Rule 4: Strategy for finding БСП functions

### Algorithm: LSP -> grep -> AI

1. **LSP** (if available): `navigate_symbol("ЗначенияРеквизитовОбъекта")`
2. **Text search**: `grep -r "Функция.*КурсВалюты" src/CommonModules/`
3. **AI assistant**: "Is there a БСП function for getting the exchange rate on a date?"

### When to write your own vs use БСП

| Situation | Decision |
|----------|---------|
| БСП has a suitable function | **Use БСП** |
| БСП has a similar one, but with extra functionality | **Use БСП** - the extra parts do not hurt |
| The needed function does not exist in БСП | Write your own in the style of БСП |
| Configuration without БСП | Write your own |

---

## Rule 5: Working with the event log through БСП

See `error-handling`, rule 7.

---

## Rule 6: `РаботаСФайлами` - instead of direct `ФайловаяСистема`

Direct file handling does not account for access rights, temporary files, and cross-platform compatibility.

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

### Fill validation (`ОбработкаПроверкиЗаполнения`)

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

| What people often write themselves | What is in БСП |
|----------------------|----------------|
| Getting an attribute by reference | `ОбщегоНазначения.ЗначениеРеквизитаОбъекта()` |
| String substitution | `СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку()` |
| Word declension | `СтроковыеФункцииКлиентСервер.СтрокаСЧисломПредметов()` |
| Sending email | `РаботаСПочтовымиСообщениями` |
| Exchange rate | `РаботаСКурсамиВалют.ПолучитьКурсВалюты()` |
| Long-running background operation | `ДлительныеОперации.ВыполнитьФункцию()` |
| Storing secrets / passwords | `БезопасноеХранилище.ПрочитатьДанные()` |
| Access right profiles | `ГруппыДоступаПользователей` / `ПрофилиГруппДоступа` |
| Registering an external processor | `СведенияОВнешнейОбработке()` |

---

## Rule 9: `КлиентСервер` modules - separation of responsibilities

| Module suffix | Environment | Example |
|----------------|-------|--------|
| (without suffix) | Server | `ОбщегоНазначения` |
| `Клиент` | Client | `ОбщегоНазначенияКлиент` |
| `КлиентСервер` | Both environments | `ОбщегоНазначенияКлиентСервер` |
| `ПовтИсп` | Server, with caching | `ОбщегоНазначенияПовтИсп` |

For form client code, first look in `*КлиентСервер`, then in `*Клиент`. For server-side code, use the main module (without suffix) first. `*ПовтИсп` is for frequently requested reference data.

---

## Rule 10: Long-running operations (`ДлительныеОперации`)

Use the `ДлительныеОперации` subsystem for any server-side work longer than ~3 seconds. Do not block the UI with a homemade wait loop.

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
- Implement an idempotent restart: a repeated call with the same parameters must produce the same result.

---

## Rule 11: Secure storage (`БезопасноеХранилище`)

Never store passwords, tokens, and secrets in:
- metadata object attributes
- configuration constants
- the event log
- version control systems (config files, xml)

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

In the object's `ПередУдалением` handler, always call `БезопасноеХранилище.Удалить()` - otherwise "orphaned" records accumulate in the storage.

---

## Rule 12: Access group profiles (`ПрофилиГруппДоступа`)

When developing subsystems with role-based access, use the БСП profile mechanism instead of assigning roles directly.

```bsl
// Example of defining a profile in ОписаниеПрофилейГруппДоступа()
Профиль = УправлениеДоступом.ОписаниеПрофиля();
Профиль.Идентификатор = "ИдентификаторПрофиля_UUID";
Профиль.Наименование   = НСтр("ru = 'Менеджер по продажам'");
Профиль.Роли.Добавить("РольМенеджерПродаж");
Профили.Добавить(Профиль);
```

**Key rules:**
- The profile identifier is a fixed UUID and does not change when renamed.
- For elevated privileges, use `ПривилегированныйРежим()` strictly locally and turn it off immediately after the operation.
- Check permissions through `УправлениеДоступом.ПроверитьДопустимостьДействия()`, not through `РольДоступна()` directly - the latter does not account for RLS.

---

## Rule 13: External processors and extensions (`СведенияОВнешнейОбработке`)

Registering an external processor in a БСП-based configuration requires the `СведенияОВнешнейОбработке()` function in the processor's main module.

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

## Finding analogs through Buddy

If `search_ssl_functions` did not return a result, use `ask_ai_assistant` (the VALIDATE_BSL template from `buddy-prompting`): pass the code fragment, get recommendations for replacing it with БСП methods. Also use `SEARCH_DOCS` for documentation on a specific БСП method.

---
depends_on: []
---
