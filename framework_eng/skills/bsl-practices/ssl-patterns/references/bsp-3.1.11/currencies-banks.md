# Currencies, banks, and work schedules

Three related subsystems of master data: **Currencies** (exchange rates,
conversion, amount in words), **Banks** (BIC classifier), and **WorkSchedules** /
**CalendarSchedules** (production calendars, calculation of working dates,
work schedule). Loading of reference data (banks, exchange rates, calendars) is performed
by the `РаботаСКлассификаторами` mechanism - see `classifiers.md`; here is reading and calculations.

## Modules

Currencies - the module names do **not** match the subsystem (there is no `Валюты` module):

- `РаботаСКурсамиВалют` - server stable API: exchange rate, conversion, amount
  in words, adding currencies. region `ПрограммныйИнтерфейс` (stable).
- `РаботаСКурсамиВалютКлиентСервер` - `ПересчитатьПоКурсу` (without a server call,
  using ready exchange rate parameters). Client + Server, stable.
- `РаботаСКурсамиВалютКлиентЛокализация` - `ПоказатьЗагрузкуКурсовВалют`
  (interactive exchange rate loading). Client, stable.
- `РаботаСКурсамиВалютКлиент` - ⚠️ `ПоказатьЗагрузкуКурсовВалют` here is in the region
  `СлужебныйПрограммныйИнтерфейс` (not stable) - use the variant from
  `…КлиентЛокализация`.

Banks:

- `РаботаСБанками` - server stable API: `СведенияБИК`,
  `ПояснениеНедействительногоБанка` (deprecated `ПолучитьДанныеКлассификатора` - see scenario 3).
- `РаботаСБанкамиКлиент` - `ВыбратьИзСправочникаБИК` (selection UI). Client, stable.

Schedules and calendars:

- `КалендарныеГрафики` - main server API: date calculation, nearest working dates,
  schedule, form filling, main calendar. Accepts both
  `СправочникСсылка.ПроизводственныеКалендари` and `СправочникСсылка.Календари`
  (internally switches to `ГрафикиРаботы`). stable.
- `ГрафикиРаботы` - work schedule API (`СправочникСсылка.Календари`):
  `ДатаПоГрафику`, `БлижайшиеДатыВключенныеВГрафик`, `РасписанияРаботыНаПериод`.
  ⚠️ `ГрафикиРаботы.РазностьДатПоКалендарю` - in `СлужебныйПрограммныйИнтерфейс`
  (not stable); for date difference, use `КалендарныеГрафики.РазностьДатПоКалендарю`.
- `ГрафикиРаботыКлиент` - schedule row collection utilities (not for date calculations).

For all three subsystems, modules `*Служебный` / `*ВызовСервера` / `*Глобальный` - ⚠️
service modules, and `*Переопределяемый` - hooks (implemented in the application configuration,
not called directly).

⚠️ A common trap is inventing modules `Валюты`, `Банки`, `Организации`. The real
names are `РаботаСКурсамиВалют`, `РаботаСБанками`, `ОрганизацииСервер`.

## Scenarios

### 1. Get the currency exchange rate and recalculate the amount

**Task:** read the exchange rate for a date; recalculate the amount from one currency into
another at the rate for the date; recalculate on the client using previously obtained rates.

**Functions:**
`РаботаСКурсамиВалют.ПолучитьКурсВалюты(Валюта, ДатаКурса) Экспорт`
— Function → `Структура` (`Курс, Кратность, Валюта, ДатаКурса`) / `Неопределено`, region `ПрограммныйИнтерфейс` (stable). Server.
`РаботаСКурсамиВалют.ПересчитатьВВалюту(Сумма, ИсходнаяВалюта, НоваяВалюта, Дата) Экспорт`
— Function → `Число`, region `ПрограммныйИнтерфейс` (stable). Server. Retrieves both rates internally.
`РаботаСКурсамиВалютКлиентСервер.ПересчитатьПоКурсу(Сумма, ПараметрыТекущегоКурса, ПараметрыНовогоКурса) Экспорт`
— Function → `Число`, region `ПрограммныйИнтерфейс` (stable). Client + Server. Recalculation using ready-made rate structures.

**Parameters:**
- `Валюта` (`СправочникСсылка.Валюты`), `ДатаКурса` (`Дата`).
- `Сумма` (`Число`), `ИсходнаяВалюта` / `НоваяВалюта` (`СправочникСсылка.Валюты`), `Дата` (`Дата`).
- `ПараметрыТекущегоКурса` / `ПараметрыНовогоКурса` (`Структура`) — from `ПолучитьКурсВалюты`: `Валюта, Курс, Кратность`.

**Example:**
```bsl
// Server: get the rate and recalculate
Курс = РаботаСКурсамиВалют.ПолучитьКурсВалюты(Объект.Валюта, Объект.Дата);
Если Курс = Неопределено Тогда
    ОбщегоНазначения.СообщитьПользователю(
        НСтр("ru = 'Курс валюты на дату не задан.'"), , , "Объект", Отказ);
    Возврат;
КонецЕсли;

СуммаРуб = РаботаСКурсамиВалют.ПересчитатьВВалюту(
    Объект.Сумма, Объект.Валюта,
    Справочники.Валюты.НайтиПоКоду("643"), Объект.Дата);

// Client: recalculation using previously obtained rates (without a server call)
СуммаНовая = РаботаСКурсамиВалютКлиентСервер.ПересчитатьПоКурсу(Сумма, КурсИсходный, КурсНовый);
```

**Nuances / anti-patterns:**
- ❌ Direct query to `РегистрыСведений.КурсыВалют` — bypasses the wrapper, breaks
  multiplicity and caching. Only `ПолучитьКурсВалюты`.
- `ПолучитьКурсВалюты` returns `Неопределено` if there is no rate record —
  обязательно check before using.
- `ПересчитатьВВалюту` retrieves both rates itself; explicit `ПолучитьКурсВалюты` is needed
  only to show the rate to the user or validate it.
- `ПересчитатьПоКурсу` is convenient in client code: one server call for rates,
  then recalculations on the client without repeated requests.

### 2. Amount in words and adding currencies by code

**Task:** generate the amount in words in the required language; during initial
population, add currencies to the catalog by the numeric OKV code.

**Functions:**
`РаботаСКурсамиВалют.СформироватьСуммуПрописью(СуммаЧислом, Валюта, БезДробнойЧасти = Ложь, Знач КодЯзыка = Неопределено, ДробнаяЧастьПрописью = Ложь) Экспорт`
— Function → `Строка`, region `ПрограммныйИнтерфейс` (stable). Server.
`РаботаСКурсамиВалют.ДобавитьВалютыПоКоду(Знач КодыВалют) Экспорт`
— Function → `Массив` из `СправочникСсылка.Валюты`, region `ПрограммныйИнтерфейс` (stable). Server. For initial population handlers.
`РаботаСКурсамиВалют.ПодключитьИсточникДанныхПечатиЧислоПрописью(ИсточникиДанныхПечати) Экспорт`
— Procedure, stable. Connects a data source for the print layout of numbers in words.

**Parameters:**
- `СуммаЧислом` (`Число`), `Валюта` (`СправочникСсылка.Валюты`).
- `БезДробнойЧасти` (`Булево`) — `Истина` without kopeks.
- `КодЯзыка` (`Строка`) — code by ISO 639-1 (+ optionally ISO 3166-1 via `_`): `"ru"`,
  `"ru_RU"`, `"en"`, `"en_US"`. By default, the configuration language.
- `ДробнаяЧастьПрописью` (`Булево`).
- `КодыВалют` (`Массив` из `Строка`) — numeric codes (840, 978, 643 …).

**Example:**
```bsl
Текст = РаботаСКурсамиВалют.СформироватьСуммуПрописью(1234.56, ВалютаRUB, , "ru");
// "Одна тысяча двести тридцать четыре рубля 56 копеек"

// Initial population of the currency catalog
Коды = Новый Массив; Коды.Добавить("840"); Коды.Добавить("978"); Коды.Добавить("643");
Ссылки = РаботаСКурсамиВалют.ДобавитьВалютыПоКоду(Коды);
```

**Nuances / anti-patterns:**
- ❌ Format the number in words manually for a print form — use
  `СформироватьСуммуПрописью` or `ПодключитьИсточникДанныхПечатиЧислоПрописью`
  (data source for the layout).
- If the currency classifier is missing, `ДобавитьВалютыПоКоду` creates items with
  the name `"Валюта"`, and the symbolic code matches the numeric one.

### 3. Find a bank by BIC and explain an invalid bank

**Task:** by BIC (optionally with correspondent account) obtain bank details;
explain to the user why the bank is marked invalid.

**Functions:**
`РаботаСБанками.СведенияБИК(Знач БИК, Знач КоррСчет = Неопределено, ТолькоАктуальные = Истина) Экспорт`
— Function → `ТаблицаЗначений` (`Ссылка, БИК, КоррСчет, Наименование, Город, Адрес` …), region `ПрограммныйИнтерфейс` (stable). Server.
`РаботаСБанками.ПояснениеНедействительногоБанка(Банк) Экспорт`
— Function → `ФорматированнаяСтрока`, region `ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `БИК` (`Строка`), `КоррСчет` (`Строка` / `Неопределено`).
- `ТолькоАктуальные` (`Булево`) — `Истина` (default) only active banks.
- `Банк` (`СправочникСсылка.КлассификаторБанков`).

**Example:**
```bsl
Таблица = РаботаСБанками.СведенияБИК("044525225", , Ложь);   // включая недействующие
Если Таблица.Количество() > 0 Тогда
    БанкСсылка = Таблица[0].Ссылка;
    Наименование = Таблица[0].Наименование;
КонецЕсли;

// Пояснение для реквизита формы банка
Пояснение = РаботаСБанками.ПояснениеНедействительногоБанка(БанкСсылка);
// ФорматированнаяСтрока с гиперссылкой на новый БИК, если он нашёлся
```

**Nuances / anti-patterns:**
- ❌ `РаботаСБанками.ПолучитьДанныеКлассификатора(БИК = "", КоррСчет = "", ЗаписьОБанке = "")` —
  ⚠️ deprecated (region `УстаревшиеПроцедурыИФункции`): uses the output
  parameter `ЗаписьОБанке` instead of returning a value. In new code use `СведенияБИК`.
- `ПояснениеНедействительногоБанка` returns `ФорматированнаяСтрока` for
  display in a form attribute, not a user message — for messages use
  `ОбщегоНазначения.СообщитьПользователю` (`base-common.md`).

### 4. Select BIC from a form

**Task:** from the BIC input field on an object form, open the selection form with a filter and
get the selected bank in the notification handler.

**Function:**
`РаботаСБанкамиКлиент.ВыбратьИзСправочникаБИК(БИК, Форма, ОбработчикОповещения = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Thin/Web client.

**Parameters:**
- `БИК` (`Строка`) — selection filter.
- `Форма` (`ФормаКлиентскогоПриложения`) — source form.
- `ОбработчикОповещения` (`ОписаниеОповещения`) — `Результат` = `СправочникСсылка.КлассификаторБанков`
  (selected item) or `Неопределено`; if absent, the standard selection handler is used.

**Example:**
```bsl
&НаКлиенте
Процедура БИКНачалоВыбора(Элемент, ДанныеВыбора, СтандартнаяОбработка)
    СтандартнаяОбработка = Ложь;
    РаботаСБанкамиКлиент.ВыбратьИзСправочникаБИК(
        Объект.БИК, ЭтотОбъект,
        Новый ОписаниеОповещения("ОбработкаВыбораБИК", ЭтотОбъект));
КонецПроцедуры

&НаКлиенте
Процедура ОбработкаВыбораБИК(Результат, ДопПараметры) Экспорт
    Если Результат <> Неопределено Тогда
        Объект.БИК = Результат.БИК;                  // если Результат — ссылка, читать реквизиты
        Объект.НаименованиеБанка = Результат.Наименование;
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Forgetting `СтандартнаяОбработка = Ложь` — the standard selection from the catalog
  will run instead of the BIC form.
- If there is only one record in the selection list, the choice is made automatically (without showing the form).

### 5. Date calculation by production calendar / schedule

**Task:** planned date `ДатаОт + N working days`; a chain of related dates; how many
working days there are between two dates.

**Functions:**
`КалендарныеГрафики.ДатаПоКалендарю(Знач ГрафикРаботы, Знач ДатаОт, Знач КоличествоДней, ВызыватьИсключение = Истина) Экспорт`
— Function → `Дата` / `Неопределено`, region `ПрограммныйИнтерфейс` (stable). Server. `ГрафикРаботы` — `СправочникСсылка.ПроизводственныеКалендари` or `СправочникСсылка.Календари`.
`КалендарныеГрафики.ДатыПоКалендарю(Знач ГрафикРаботы, Знач ДатаОт, Знач МассивДней, Знач РассчитыватьСледующуюДатуОтПредыдущей = Ложь, ВызыватьИсключение = Истина) Экспорт`
— Function → `Массив` of `Дата`, stable.
`КалендарныеГрафики.РазностьДатПоКалендарю(Знач ГрафикРаботы, Знач ДатаНачала, Знач ДатаОкончания, ВызыватьИсключение = Истина) Экспорт`
— Function → `Число`, stable.
`ГрафикиРаботы.ДатаПоГрафику(Знач ГрафикРаботы, Знач ДатаОт, Знач КоличествоДней, ВызыватьИсключение = Истина) Экспорт`
— Function → `Дата` / `Неопределено`, stable. Only `СправочникСсылка.Календари`.

**Parameters:**
- `ГрафикРаботы` — calendar/schedule.
- `ДатаОт` (`Дата`), `КоличествоДней` (`Число`).
- `МассивДней` (`Массив` of `Число`) — offsets for `ДатыПоКалендарю`.
- `РассчитыватьСледующуюДатуОтПредыдущей` (`Булево`) — `Истина` = chained shift (each next one is calculated from the previous one).
- `ВызыватьИсключение` (`Булево`) — `Истина` (default) throws an exception when the calendar is not filled in; `Ложь` → `Неопределено`.

**Example:**
```bsl
Календарь = КалендарныеГрафики.ОсновнойПроизводственныйКалендарь();
Если Календарь = Неопределено Тогда
    ПлановаяДата = ТекущаяДатаСеанса() + 5 * 86400;   // fallback: календарные дни
Иначе
    ПлановаяДата = КалендарныеГрафики.ДатаПоКалендарю(Календарь, ТекущаяДатаСеанса(), 5);
КонецЕсли;

// Цепочка дат: каждая от предыдущей (стадии согласования)
МассивДней = Новый Массив; МассивДней.Добавить(3); МассивДней.Добавить(5); МассивДней.Добавить(7);
МассивДат = КалендарныеГрафики.ДатыПоКалендарю(Календарь, ДатаСтарта, МассивДней, Истина);

// Сколько рабочих дней между датами
ДнейПросрочки = КалендарныеГрафики.РазностьДатПоКалендарю(Календарь, ДатаОтгрузки, ТекущаяДатаСеанса());
```

**Nuances / anti-patterns:**
- ❌ Calculating working days with a `Пока ... Если ДеньНедели < 6` loop does not take into account
  holidays, weekend shifts, and non-working periods established by decrees. Use only
  `ДатаПоКалендарю` / `РазностьДатПоКалендарю`.
- `ДатаПоКалендарю` accepts both types (calendar and schedule); `ДатаПоГрафику`
  (module `ГрафикиРаботы`) accepts only `СправочникСсылка.Календари`. The result
  is the same - choose based on what you have.
- `РазностьДатПоКалендарю` always returns a positive number (the sign
  is normalized internally).

### 6. Nearest working day considering non-working periods

**Task:** for a set of dates, find the nearest working dates (forward/backward), taking into account
special non-working periods (presidential decrees).

**Functions:**
`КалендарныеГрафики.ПараметрыПолученияБлижайшихРабочихДат(ПроизводственныйКалендарь = Неопределено) Экспорт`
— Function → `Структура`, region `ПрограммныйИнтерфейс` (stable). Parameter constructor.
`КалендарныеГрафики.БлижайшиеРабочиеДаты(ПроизводственныйКалендарь, НачальныеДаты, ПараметрыПолучения = Неопределено) Экспорт`
— Function → `Соответствие` (`Ключ` — source `Дата`, `Значение` — nearest working `Дата`), stable. Only `СправочникСсылка.ПроизводственныеКалендари`.
`ГрафикиРаботы.БлижайшиеДатыВключенныеВГрафик(ГрафикРаботы, НачальныеДаты, ПараметрыПолучения = Неопределено) Экспорт`
— Function → `Соответствие`, stable. Only `СправочникСсылка.Календари` — nearest date **included in the schedule**.

**Parameters:**
- `ПроизводственныйКалендарь` (`СправочникСсылка.ПроизводственныеКалендари`).
- `НачальныеДаты` (`Массив` of `Дата`).
- `ПараметрыПолучения` (`Структура` from `ПараметрыПолученияБлижайшихРабочихДат`): `ПолучатьПредшествующие` (`Булево` — backward), `УчитыватьНерабочиеПериоды` (`Булево`), `НерабочиеПериоды`, `ВызыватьИсключение`, `ПолучатьДатыЕслиКалендарьНеЗаполнен`.

**Example:**
```bsl
Параметры = КалендарныеГрафики.ПараметрыПолученияБлижайшихРабочихДат(Календарь);
Параметры.ПолучатьПредшествующие = Ложь;        // forward
Параметры.УчитыватьНерабочиеПериоды = Истина;   // take into account special non-working days

Даты = Новый Массив; Даты.Добавить(ДатаСдачи);
Соотв = КалендарныеГрафики.БлижайшиеРабочиеДаты(Календарь, Даты, Параметры);
НоваяДатаСдачи = Соотв[ДатаСдачи];   // if the source date is working, returns the same date
```

**Nuances / anti-patterns:**
- ❌ `БлижайшиеРабочиеДаты(ГрафикРаботы, ...)` with `СправочникСсылка.Календари` —
  the method will throw an exception: it accepts **only** `ПроизводственныеКалендари`. For
  a work schedule — `ГрафикиРаботы.БлижайшиеДатыВключенныеВГрафик`.
- ⚠️ `КалендарныеГрафики.ДатыБлижайшихРабочихДней(График, НачальныеДаты, ПолучатьПредшествующие = Ложь, ВызыватьИсключение = Истина, ИгнорироватьНезаполненностьГрафика = Ложь)`
  — obsolete (`УстаревшиеПроцедурыИФункции` region), 5 parameters instead of 3. In
  new code — `БлижайшиеРабочиеДаты` / `БлижайшиеДатыВключенныеВГрафик`.

### 7. Work schedule for a period and filling the calendar in the form

**Task:** obtain work start/end times by schedules for a period;
fill the `ПроизводственныйКалендарь` attribute in the form taking the region into account (`КПП`).

**Functions:**
`КалендарныеГрафики.ОсновнойПроизводственныйКалендарь() Экспорт`
— Function → `СправочникСсылка.ПроизводственныеКалендари` / `Неопределено`, stable. Server.
`КалендарныеГрафики.ЗаполнитьПроизводственныйКалендарьВФорме(Форма, ПутьРеквизита, КПП = Неопределено) Экспорт`
— Procedure, stable. Server. Takes the functional option `ИспользоватьНесколькоПроизводственныхКалендарей` into account.
`ГрафикиРаботы.РасписанияРаботыНаПериод(Графики, ДатаНачала, ДатаОкончания) Экспорт`
— Function → `ТаблицаЗначений` (`ГрафикРаботы, ДатаГрафика, ВремяНачала, ВремяОкончания`), stable. Server. Requires the connected subsystem `ГрафикиРаботы`.
`КалендарныеГрафики.РасписанияРаботыНаПериод(Графики, ДатаНачала, ДатаОкончания) Экспорт`
— Function, stable. Delegates to `ГрафикиРаботы`; throws an exception if the subsystem is absent.

**Parameters:**
- `Форма` (`ФормаКлиентскогоПриложения`), `ПутьРеквизита` (`Строка`, e.g. `"Объект.ПроизводственныйКалендарь"`).
- `КПП` (`Строка`) — for a regional calendar when the multiple calendars option is enabled.
- `Графики` (`Массив` of `СправочникСсылка.Календари`), `ДатаНачала` / `ДатаОкончания` (`Дата`).

**Example:**
```bsl
// Fill the attribute in the form (server, ПриСозданииНаСервере)
КалендарныеГрафики.ЗаполнитьПроизводственныйКалендарьВФорме(
    ЭтаФорма, "Объект.ПроизводственныйКалендарь", Объект.КПП);

// Warehouse work schedule for a week
Графики = Новый Массив; Графики.Добавить(ГрафикСклада);
ТаблицаРасписаний = ГрафикиРаботы.РасписанияРаботыНаПериод(
    Графики, ДатаНачала, ДатаОкончания);
// Columns: ГрафикРаботы, ДатаГрафика, ВремяНачала, ВремяОкончания
```

**Nuances / antipatterns:**
- ❌ `ЭтаФорма.Объект.ПроизводственныйКалендарь = КалендарныеГрафики.ОсновнойПроизводственныйКалендарь()`
  without taking `КПП` into account — when the multiple calendars option is enabled, for a separate
  subdivision the “main” one will be substituted instead of the regional one. Use
  `ЗаполнитьПроизводственныйКалендарьВФорме` with `КПП`.
- ❌ Store a reference to the calendar in a constant and consider it the “only” one — when
  the multiple calendars option is enabled, there may be no constant; `ОсновнойПроизводственныйКалендарь`
  returns the “first one it finds”.
- The methods `КалендарныеГрафики` and `ГрафикиРаботы` are server-side (Server, Thick client,
  External connection); from a thin client — via `&НаСервере`. There is no separate module
  `КалендарныеГрафикиКлиент`.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures are available via
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `РаботаСКурсамиВалют.ОписаниеТипаДенежногоПоля(Знач ДопустимыйЗнакПоля = Неопределено)` —
  description of the money field type (for constructing attributes).
- `РаботаСКурсамиВалютКлиентЛокализация.ПоказатьЗагрузкуКурсовВалют(ПараметрыЗагрузки)` —
  interactive currency rate loading (stable variant; `ПараметрыЗагрузки.ОткрытиеИзСписка`).
- `КалендарныеГрафики.ПериодыНерабочихДней(ПроизводственныйКалендарь, ПериодОтбор)` —
  calendar non-working day periods.
- `КалендарныеГрафики.СоздатьВТРасписанияРаботыНаПериод(МенеджерВременныхТаблиц, Графики, ДатаНачала, ДатаОкончания)` /
  `ГрафикиРаботы.СоздатьВТРасписанияРаботыНаПериод(...)` — schedule variant into a
  temporary table (for queries).
- `ГрафикиРаботы.ДатыПоГрафику(Знач ГрафикРаботы, Знач ДатаОт, Знач МассивДней, Знач РассчитыватьСледующуюДатуОтПредыдущей = Ложь, ВызыватьИсключение = Истина)` —
  chain of dates by schedule (analog of `ДатыПоКалендарю`).

⚠️ Service methods (do not use in new code):
- `РаботаСКурсамиВалютСлужебный.ЗагрузитьКурсы()` — ⚠️ service
  (`СлужебныйПрограммныйИнтерфейс`). Manual currency rate loading; backward compatibility
  is not guaranteed. Launch through the interactive form
  `РаботаСКурсамиВалютКлиентЛокализация.ПоказатьЗагрузкуКурсовВалют`.
- Deprecated replacements are listed in place: `ПолучитьДанныеКлассификатора` → scenario 3,
  `ДатыБлижайшихРабочихДней` → scenario 6, `ГрафикиРаботы.РазностьДатПоКалендарю` → section "Modules".

Override hook `КалендарныеГрафикиПереопределяемый.ПриОбновленииПроизводственныхКалендарей`
— application configuration reaction to updating production calendars
(implemented in the module of the same name, not called directly).