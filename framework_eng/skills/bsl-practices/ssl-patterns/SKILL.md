---
name: ssl-patterns
description: "Patterns for working with БСП (Library of Standard Subsystems). This skill teaches the agent to correctly use БСП (Library of Standard Subsystems, English abbreviation)."
---

# Patterns for working with БСП (Library of Standard Subsystems)

БСП code has been verified on millions of installations, is updated centrally, and is familiar to other developers. Duplicating БСП is an anti-pattern.

---

## Rule 1: Module ОбщегоНазначения — the main "Swiss army knife"

Before writing your own implementation, check whether БСП already has a ready-made function.

| Function | When to use |
|---------|-------------------|
| `ЗначениеРеквизитаОбъекта()` | Instead of `Ссылка.Реквизит` (avoid dotted notation) |
| `ЗначенияРеквизитовОбъекта()` | Multiple attributes in one call |
| `СообщитьПользователю()` | Field-bound message (instead of `Сообщить()`) |
| `МенеджерОбъектаПоСсылке()` | Instead of `Выполнить("Справочники." + Имя)` |
| `ПодсистемаСуществует()` | Conditional module call |
| `ОбщийМодуль()` | Dynamic call of a БСП module |
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

## Rule 2: СтроковыеФункцииКлиентСервер — string handling

The module contains optimized functions that handle edge cases correctly.

| Function | When to use |
|---------|-------------------|
| `ПодставитьПараметрыВСтроку()` | Equivalent of `СтрШаблон()`, with additional checks |
| `СтрокаСЧисломПредметов()` | Declension: "5 documents", "1 document" |
| `ЕстьНедопустимыеСимволы()` | Input validation |
| `ТолькоЦифрыВСтроке()` | Validation for INN, KPP |
| `РазложитьСтрокуВМассивПодстрок()` | Parsing by delimiter |

```bsl
// Склонение: «1 документ», «2 документа», «5 документов»
ТекстОповещения = СтроковыеФункцииКлиентСервер.СтрокаСЧисломПредметов(
    КоличествоДокументов,
    НСтр("ru = 'документ, документа, документов'"));
```

---

## Rule 3: ОбщегоНазначенияКлиентСервер — utilities for both environments

Directive `&НаКлиентеНаСервереБезКонтекста` is available on both the client and server.

| Function | Description |
|---------|----------|
| `ДополнитьМассив()` | Merging two arrays |
| `ДополнитьСтруктуру()` | Merging two structures |
| `СвойствоСтруктуры()` | Safe property reading (default value if missing) |
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
3. **AI assistant**: "Is there a БСП function that retrieves the exchange rate for a date?"

### When to write your own vs use БСП

| Situation | Decision |
|----------|---------|
| БСП has a suitable function | **Use БСП** |
| БСП has a similar function but with extra functionality | **Use БСП** — the extra features do not hurt |
| The needed function is not in БСП | Write your own in the БСП style |
| Configuration without БСП | Write your own |

---

## Rule 5: Working with the registration journal using БСП

See `error-handling`, rule 7.

---

## Rule 6: РаботаСФайлами — instead of direct FileSystem calls

Direct work with files does not account for access rights, temporary files, and cross-platform compatibility.

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

### Validation check (ОбработкаПроверкиЗаполнения)

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

### Obtaining data for printing

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

| What is often written manually | What БСП provides |
|----------------------|----------------|
| Getting an attribute by reference | `ОбщегоНазначения.ЗначениеРеквизитаОбъекта()` |
| Substituting into a string | `СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку()` |
| Word declension | `СтроковыеФункцииКлиентСервер.СтрокаСЧисломПредметов()` |
| Sending mail | `РаботаСПочтовымиСообщениями` |
| Exchange rate | `РаботаСКурсамиВалют.ПолучитьКурсВалюты()` |

---

## Rule 9: "КлиентСервер" modules — separation of responsibility

| Module suffix | Environment | Example |
|----------------|-------------|---------|
| (no suffix) | Server | `ОбщегоНазначения` |
| `Клиент` | Client | `ОбщегоНазначенияКлиент` |
| `КлиентСервер` | Both environments | `ОбщегоНазначенияКлиентСервер` |
| `ПовтИсп` | Server with caching | `ОбщегоНазначенияПовтИсп` |

For client form code, look first in `*КлиентСервер`, then in `*Клиент`. For server code, check the main module (without a suffix). `*ПовтИсп` is for frequently requested reference data.

---

## Finding analogs via Buddy

If `search_ssl_functions` yields no result, use `ask_ai_assistant` (the `VALIDATE_BSL` template from `buddy-prompting`): send a code snippet and get recommendations for replacing it with БСП methods. Also use `SEARCH_DOCS` for documentation on a specific БСП method.

---
depends_on: []
---
