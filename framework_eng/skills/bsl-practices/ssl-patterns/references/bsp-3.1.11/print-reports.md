# Printing and report variants

The **Печать** and **ВариантыОтчетов** subsystems are infrastructure for print forms (tabular
and office documents, the "Печать" submenu in forms) and report variants (the
`ВариантыОтчетов` catalog, attaching variants to subsystems, programmatic generation
of a report using SKD). Used when you need to generate a print form via the "Печать"
command or open/generate a report through the БСП variant infrastructure.

## Modules

The **УправлениеПечатью** family (subsystem `Печать`):

- `УправлениеПечатью` — server, stable API: `СоздатьКоллекциюКомандПечати`,
  `КомандыПечатиФормы`, `ВывестиТабличныйДокументВКоллекцию`, `ЗадатьОбластьПечатиДокумента`,
  `МакетПечатнойФормы`, `МакетыИДанныеОбъектовДляПечати`, `СведенияОПечатнойФорме`,
  `НужноПечататьМакет`, `ДобавитьУсловиеВидимостиКоманды`.
- `УправлениеПечатьюКлиент` — client, stable API: `ВыполнитьКомандуПечати`,
  `ВыполнитьКомандуПечатиНаПринтер`, `ПечатьДокументов`, `ПараметрыПечати`.
- `УправлениеПечатьюВызовСервера` — server call from the client without form context.
- `УправлениеПечатьюКлиентСервер` — common: field name constants, save settings.
- `УправлениеПечатьюПереопределяемый` — **hooks**: `ПриОпределенииНастроекПечати`,
  `ПриПечати`, `ПриПолученииКомандПечати`, `ПередПечатью`. Called by БСП, implemented
  by application code, not called directly.
- `УправлениеПечатьюСлужебный`, `УправлениеПечатьюСлужебныйКлиент` — ⚠️ internal.

The **ВариантыОтчетов** family (subsystem `ВариантыОтчетов`):

- `ВариантыОтчетов` — server, stable API: `ВариантОтчета`, `НастроитьОтчетВМодулеМенеджера`,
  `ОписаниеОтчета`, `ОписаниеВарианта`, `КлючиВариантовОтчета`.
- `ВариантыОтчетовКлиент` — client, stable API: `ОткрытьФормуОтчета`,
  `ПоказатьПанельОтчетов`, `ОбновитьОткрытыеФормы`.
- `ВариантыОтчетовВызовСервера` — server call from the client.
- `ВариантыОтчетовПереопределяемый` — **hooks**: `НастроитьВариантыОтчетов`,
  `ОпределитьРазделыСВариантамиОтчетов`, `ОпределитьОбъектыСКомандамиОтчетов`,
  `ПриОпределенииНастроек`.
- `ОтчетыКлиент` — client, stable: `СформироватьОтчет(ФормаОтчета, ОбработчикЗавершения)`
  (generate a report from its form).

> ⚠️ **`Печать` is a subsystem (metadata object), not a common module.** The subsystem
> module is named `УправлениеПечатью`. `python … module Печать` will report
> "Module 'Печать' not found" - this is not an error, use `module УправлениеПечатью`.
>
> ⚠️ `УправлениеПечатью.СформироватьПечатныеФормы` — region
> `СлужебныеПроцедурыИФункции` (not `ПрограммныйИнтерфейс`); `ВариантыОтчетов.СформироватьОтчет`
> — `СлужебныйПрограммныйИнтерфейс`. Both may change in a minor БСП version —
> for new code, prefer the client stable paths (`ВыполнитьКомандуПечати`,
> `ОтчетыКлиент.СформироватьОтчет`).

## Scenarios

### 1. Declare a print form in the object’s "Print" submenu

**Task:** in the object manager module (document/catalog) register
a print command so that БСП automatically places it in the form’s "Print" submenu.

**Function:**
`УправлениеПечатью.СоздатьКоллекциюКомандПечати() Экспорт`
— Function → ТаблицаЗначений (empty print command collection), region
`#Область ПрограммныйИнтерфейс` (stable). Server, ExternalConnection.

**Parameters:** none. Returns an empty table with columns `Идентификатор`,
`Представление`, `МенеджерПечати`, `Картинка`, `Порядок`, `ПроверкаПроведенияПередПечатью`,
`ДополнительныеПараметры` and others.

**Example:**
```bsl
// Модуль менеджера документа
Процедура ДобавитьКомандыПечати(КомандыПечати, Параметры) Экспорт
    // КомандыПечати приходит уже инициализированной — СоздатьКоллекциюКомандПечати
    // вызывать не нужно. Достаточно добавить строку.
    КомандаПечати = КомандыПечати.Добавить();
    КомандаПечати.Идентификатор = "СписаниеТоваров";
    КомандаПечати.Представление = НСтр("ru = 'Списание товаров'");
    КомандаПечати.МенеджерПечати = "Документ._ДемоСписаниеТоваров";
    КомандаПечати.Картинка = БиблиотекаКартинок.ПечатьMXL;
    КомандаПечати.Порядок = 10;
    КомандаПечати.ПроверкаПроведенияПередПечатью = Истина;
    КомандаПечати.ДополнительныеПараметры = Новый Структура("ПоказыватьЦены", Истина);

    // Видимость команды — только для проведённых
    УправлениеПечатью.ДобавитьУсловиеВидимостиКоманды(
        КомандаПечати, "Проведен", Истина, ВидСравненияКомпоновкиДанных.Равно);
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Implement print commands outside the object manager module (for example, in the common
  module "ПечатныеФормы"). `УправлениеПечатью.КомандыПечатиФормы` looks for commands specifically
  in `ДобавитьКомандыПечати` of the manager module — the submenu will remain empty.
- ❌ Create your own common module `ПечатьМоейПодсистемы` and build tabular
  documents bypassing `УправлениеПечатью` — this breaks the "Print" submenu, performance
  measurements, rights checking, print form language switching, and mailing.
- `УправлениеПечатью.ДобавитьУсловиеВидимостиКоманды(КомандаПечати, Реквизит, Значение, Знач МетодСравнения = Неопределено)`
  — the 4th parameter is named `МетодСравнения` (not `ВидСравнения`, as in
  `ПодключаемыеКоманды.ДобавитьУсловиеВидимостиКоманды` from `commands-external.md`).

### 2. Implement the print manager (generate a tabular document)

**Task:** in the object manager module, implement the export `Печать(...)`,
which the БСП infrastructure will call when the print command is selected; build a tabular
document and place it into the print forms collection.

**Functions:**
`УправлениеПечатью.НужноПечататьМакет(КоллекцияПечатныхФорм, ИмяМакета) Экспорт` — Function → Boolean, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеПечатью.ЗадатьОбластьПечатиДокумента(ТабличныйДокумент, НомерСтрокиНачало, ОбъектыПечати, Ссылка) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеПечатью.ВывестиТабличныйДокументВКоллекцию(КоллекцияПечатныхФорм, ИмяМакета, СинонимМакета, ТабличныйДокумент, Картинка = Неопределено, ПолныйПутьКМакету = "", ИмяФайлаПечатнойФормы = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеПечатью.МакетПечатнойФормы(ПутьКМакету, Знач КодЯзыка = Неопределено) Экспорт` — Function → ТабличныйДокумент, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `КоллекцияПечатныхФорм` (ТаблицаЗначений) — a collection from `Печать(...)`; columns
  `ИмяМакета`, `СинонимМакета`, `ТабличныйДокумент`, `Картинка`, …
- `ИмяМакета` (Строка) — the template identifier for which the document is generated.
- `ТабличныйДокумент` (ТабличныйДокумент) — the finished document for output.
- `ОбъектыПечати` (ТаблицаЗначений) — a "object → area" table for linking
  printed areas to the original references (used when printing a set).
- `НомерСтрокиНачало` (Число) — the starting row number of an object's area.
- `Ссылка` (ЛюбаяСсылка) — the owner object of the area.
- `ПутьКМакету` (Строка) — a path like `"Документ._ДемоСписаниеТоваров.ПФ_MXL_СписаниеТоваров"`.
- `КодЯзыка` (Строка) — the template language code; `Неопределено` → current.

**Example:**
```bsl
// Document manager module
Процедура Печать(МассивОбъектов, ПараметрыПечати, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода) Экспорт
    Если УправлениеПечатью.НужноПечататьМакет(КоллекцияПечатныхФорм, "СписаниеТоваров") Тогда
        ТабДок = Новый ТабличныйДокумент;
        Для Каждого Ссылка Из МассивОбъектов Цикл
            НачалоОбласти = ТабДок.ВысотаТаблицы + 1;
            // ...fill ТабДок rows using Ссылка data...
            УправлениеПечатью.ЗадатьОбластьПечатиДокумента(ТабДок, НачалоОбласти, ОбъектыПечати, Ссылка);
        КонецЦикла;
        УправлениеПечатью.ВывестиТабличныйДокументВКоллекцию(
            КоллекцияПечатныхФорм, "СписаниеТоваров", НСтр("ru = 'Списание товаров'"), ТабДок);
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Read the template through `ПолучитьОбщийМакет(...)` / `МакетОбъекта()` bypassing
  `УправлениеПечатью.МакетПечатнойФормы` — you lose support for user templates
  (the administrator could override the template) and the language code.
- `МассивОбъектов`, `КоллекцияПечатныхФорм`, `ОбъектыПечати`, `ПараметрыВывода` —
  standard print manager parameters; `ПараметрыПечати` is the structure passed
  from `УправлениеПечатьюКлиент.ВыполнитьКомандуПечати` (contains the command's
  `ДополнительныеПараметры`, `ЗаголовокФормы`, etc.).
- Post-processing of the print form (add date/signature) goes in the hook
  `УправлениеПечатьюПереопределяемый.ПриПечати(МассивОбъектов, ПараметрыПечати, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода)`
  (override, not a call).

### 3. Run printing from the object form (client → ПечатьДокументов form)

**Task:** on clicking the “Печать” command in the form, open the `ПечатьДокументов` form with
ready-made tabular documents; or print directly to the printer.

**Functions:**
`УправлениеПечатьюКлиент.ВыполнитьКомандуПечати(ИмяМенеджераПечати, ИменаМакетов, МассивОбъектов, ВладелецФормы, ПараметрыПечати = Неопределено) Экспорт` — Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.
`УправлениеПечатьюКлиент.ВыполнитьКомандуПечатиНаПринтер(ИмяМенеджераПечати, ИменаМакетов, МассивОбъектов, ПараметрыПечати = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `ИмяМенеджераПечати` (String) — full name of the print manager module, e.g.
  `"Документ._ДемоСписаниеТоваров"`.
- `ИменаМакетов` (String) — layout identifiers separated by commas, e.g.
  `"СписаниеТоваров,АктСписания"`.
- `МассивОбъектов` (Array) — object references for printing.
- `ВладелецФормы` (ManagedForm) — owner form (for `ВыполнитьКомандуПечати`).
- `ПараметрыПечати` (Structure / Undefined) — parameters structure; by default
  `Неопределено` (БСП will build it from `ПараметрыПечати()` and the command's `ДополнительныеПараметры`).

**Example:**
```bsl
&НаКлиенте
Процедура КомандаПечати(Команда)
    УправлениеПечатьюКлиент.ВыполнитьКомандуПечати(
        "Документ._ДемоСписаниеТоваров",
        "СписаниеТоваров,АктСписания",
        ДокументыНаПечать,
        ЭтотОбъект);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Call `УправлениеПечатью.СформироватьПечатныеФормы` synchronously from a form
  command handler — the form “hangs” during heavy printing. For interactive printing,
  use `ВыполнитьКомандуПечати` (opens `ПечатьДокументов` with background generation);
  for heavy server-side generation — `ДлительныеОперации.ВыполнитьФункцию`
  (see scenario 6).
- `ВыполнитьКомандуПечатиНаПринтер` bypasses preview; use it for
  scheduled/batch printing, not for manual commands.

### 4. Programmatically form a print forms package (server)

**Task:** form a print forms package from server-side code (for example, for
sending by e-mail, report distribution) and obtain ready tabular documents.

**Function:**
`УправлениеПечатью.СформироватьПечатныеФормы(Знач ИмяМенеджераПечати, Знач ИменаМакетов, Знач МассивОбъектов, Знач ПараметрыПечати, ДопустимыеТипыОбъектовПечати = Неопределено, Знач КодЯзыка = Неопределено, Знач ОбъектыПечати = Неопределено) Экспорт`
— Function → Structure (`КоллекцияПечатныхФорм`, `ОбъектыПечати`, `ПараметрыВывода`),
region `#Область СлужебныеПроцедурыИФункции` (⚠️ internal, not `ПрограммныйИнтерфейс`).
Server.

**Parameters:**
- `ИмяМенеджераПечати` (String) — full name of the print manager module.
- `ИменаМакетов` (String) — layout identifiers separated by commas.
- `МассивОбъектов` (Array) — object references for printing.
- `ПараметрыПечати` (Structure) — parameters; at minimum an empty `Новый Структура`.
- `ДопустимыеТипыОбъектовПечати` (TypeDescription / Неопределено) — restriction on object types;
  `Неопределено` → no validation.
- `КодЯзыка` (String) — language code; `Неопределено` → current.
- `ОбъектыПечати` (ValueTable / Неопределено) — for returning the region linkage.

**Example:**
```bsl
&НаСервере
Функция ПодготовитьПечатныеФормыНаСервере(МассивСсылок)
    ПараметрыПечати = Новый Структура;
    ПараметрыПечати.Вставить("ЗаголовокФормы", НСтр("ru = 'Печатные формы документов'"));
    Возврат УправлениеПечатью.СформироватьПечатныеФормы(
        "Документ._ДемоСписаниеТоваров",
        "СписаниеТоваров,АктСписания",
        МассивСсылок,
        ПараметрыПечати);
КонецФункции

// Результат.КоллекцияПечатныхФорм — таблица с колонками ИмяМакета, СинонимМакета,
// ТабличныйДокумент; Результат.ОбъектыПечати, Результат.ПараметрыВывода.
```

**Nuances / anti-patterns:**
- ⚠️ The method is in the `СлужебныеПроцедурыИФункции` region - backward compatibility is not
  guaranteed. For interactive printing, prefer the client-side
  `ВыполнитьКомандуПечати`; for server-side "heavy" printing, use background formation
  via `ДлительныеОперации.ВыполнитьФункцию` (scenario 6).
- The method has variants in `УправлениеПечатьюВызовСервера.СформироватьПечатныеФормы(МассивОбъектов, Команды)`
  and `УправлениеПечатьюСлужебный.СформироватьПечатныеФормы(...)` - all internal, with
  different signatures. Application code uses only the server-side
  `УправлениеПечатью.СформироватьПечатныеФормы` with the 7-parameter signature above.

### 5. Open the report form by variant

**Task:** open a report form programmatically (internal or additional) by
reference to a variant from the `ВариантыОтчетов` catalog.

**Functions:**
`ВариантыОтчетов.ВариантОтчета(Отчет, КлючВарианта) Экспорт` — Function → СправочникСсылка.ВариантыОтчетов / Неопределено, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ВариантыОтчетовКлиент.ОткрытьФормуОтчета(Знач ФормаВладелец, Знач Вариант, Знач ДополнительныеПараметры = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `Отчет` (СправочникСсылка.ИдентификаторыОбъектовРасширений /
  ИдентификаторыОбъектовМетаданных / ДополнительныеОтчетыИОбработки / Строка) — report
  or the full name of an external report.
- `КлючВарианта` (Строка) — report variant name (e.g. `"Основной"`).
- `ФормаВладелец` (УправляемаяФорма) — owner form (for modal opening).
- `Вариант` (СправочникСсылка.ВариантыОтчетов / Структура) — variant reference or
  description structure.
- `ДополнительныеПараметры` (Структура / Неопределено) — additional opening parameters.

**Example:**
```bsl
&НаКлиенте
Процедура ОткрытьОтчет(Команда)
    Вариант = ПолучитьСсылкуВариантаНаСервере();
    Если Вариант <> Неопределено Тогда
        ВариантыОтчетовКлиент.ОткрытьФормуОтчета(ЭтотОбъект, Вариант);
    КонецЕсли;
КонецПроцедуры

&НаСервереБезКонтекста
Функция ПолучитьСсылкуВариантаНаСервере()
    Возврат ВариантыОтчетов.ВариантОтчета("Отчет.ЗависимостиПодсистем", "Основной");
КонецФункции
```

**Nuances / anti-patterns:**
- ❌ `ОткрытьЗначение(СсылкаВарианта)` or `ПолучитьФорму(...).Открыть()` — bypass
  of the variant subsystem: breaks settings caching, report panel, availability
  by rights. Only `ВариантыОтчетовКлиент.ОткрытьФормуОтчета`.
- `ВариантОтчета` returns `Неопределено` if the report is missing or unavailable
  by rights — check before opening.
- For an external (additional) report, the same `ОткрытьФормуОтчета` opens the form
  of the additional report; the variant reference is provided by `ВариантОтчета` with `Отчет` of type
  `СправочникСсылка.ДополнительныеОтчетыИОбработки`.

### 6. Generate a report programmatically (heavy - in background)

**Task:** programmatically generate a report using SKD and obtain a tabular document;
do not block the form during heavy generation - move it to a background job.

**Functions:**
`ВариантыОтчетов.СформироватьОтчет(Знач Параметры, Знач ПроверятьЗаполнение, Знач ПолучатьФлажокПустой) Экспорт` — Function → Structure (`ТабличныйДокумент`, `Успех`, `ТекстОшибки`, `Расшифровка`, `НастройкиКД`), region `#Область СлужебныйПрограммныйИнтерфейс` (⚠️ internal). Server.
`ВариантыОтчетов.ПараметрыФормированияОтчета() Экспорт` — Function → Structure, `#Область СлужебныйПрограммныйИнтерфейс`. Server.
`ДлительныеОперации.ВыполнитьФункцию(Знач ПараметрыВыполнения, ИмяФункции, Знач Параметр1 = Неопределено, Знач Параметр2 = Неопределено, Знач Параметр3 = Неопределено, Знач Параметр4 = Неопределено, Знач Параметр5 = Неопределено, Знач Параметр6 = Неопределено, Знач Параметр7 = Неопределено) Экспорт` — Function, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters (СформироватьОтчет):**
- `Параметры` (Structure) — from `ПараметрыФормированияОтчета()`, populated with:
  `Отчет` (Metadata / string), `КлючВарианта` (String), user settings.
- `ПроверятьЗаполнение` (Boolean) — `Истина` → validate parameter filling.
- `ПолучатьФлажокПустой` (Boolean) — `Истина` → return the "report is empty" flag.

**Example:**
```bsl
&НаСервере
Функция СформироватьОтчетСервер(ПараметрыОтчета)
    ПараметрыФормирования = ВариантыОтчетов.ПараметрыФормированияОтчета();
    ПараметрыФормирования.Отчет = Метаданные.Отчеты.ЗависимостиПодсистем;
    ПараметрыФормирования.КлючВарианта = "Основной";
    ЗаполнитьЗначенияСвойств(ПараметрыФормирования, ПараметрыОтчета);
    Возврат ВариантыОтчетов.СформироватьОтчет(ПараметрыФормирования, Истина, Истина);
КонецФункции

// Тяжёлый отчёт — в фоне (предпочтительный путь для нового кода):
&НаСервере
Функция ЗапуститьФормирование(ПараметрыОтчета)
    ПараметрыВыполнения = ДлительныеОперации.ПараметрыВыполненияФункции(УникальныйИдентификатор);
    Возврат ДлительныеОперации.ВыполнитьФункцию(
        ПараметрыВыполнения,
        "Отчет.ЗависимостиПодсистем.СформироватьТяжелыйОтчет",
        ПараметрыОтчета);
КонецФункции

&НаКлиенте
Процедура Сформировать(Команда)
    ДлительнаяОперация = ЗапуститьФормирование(ПараметрыОтчета);
    Оповещение = Новый ОписаниеОповещения("ПослеФормирования", ЭтотОбъект);
    ДлительныеОперацииКлиент.ОжидатьЗавершение(
        ДлительнаяОперация, Оповещение,
        ДлительныеОперацииКлиент.ПараметрыОжидания(ЭтотОбъект));
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ⚠️ `ВариантыОтчетов.СформироватьОтчет` — internal (region
  `СлужебныйПрограммныйИнтерфейс`), backward compatibility is not guaranteed. For
  generating a report **from its form** there is a stable
  `ОтчетыКлиент.СформироватьОтчет(ФормаОтчета, ОбработчикЗавершения = Неопределено)`
  (client, `ПрограммныйИнтерфейс`) — preferred when the report is already open in a form.
- ❌ `ДлительныеОперации.ВыполнитьВФоне(ИмяПроцедуры, ПараметрыПроцедуры, ПараметрыВыполнения)`
  in new code — not recommended (the method is stable, `ПрограммныйИнтерфейс`, but
  the BSP doc comment explicitly recommends `ВыполнитьФункцию`/`ВыполнитьПроцедуру`;
  it requires wrapping parameters in a `Структуру` and an explicit `АдресХранилища` in
  the handler). Use `ВыполнитьФункцию` / `ВыполнитьПроцедуру` (up to 7
  parameters directly, without a wrapper). Details — in `longs-and-jobs.md`.
- ❌ A synchronous `СформироватьОтчет` from an `&НаКлиенте` handler blocks the form —
  move heavy report generation into `ВыполнитьФункцию` + `ОжидатьЗавершение`.

### 7. Register report variants in the overridable module

**Task:** when implementing БСП, register the application configuration's report variants (call `НастроитьОтчетВМодулеМенеджера` for each report).

**Function:**
`ВариантыОтчетов.НастроитьОтчетВМодулеМенеджера(Настройки, ОтчетМетаданные) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Настройки` (Structure) — variant settings (passed from the `НастроитьВариантыОтчетов` hook).
- `ОтчетМетаданные` (MetadataObject) — report metadata, in the manager module of which `НастроитьВариантыОтчета(Настройки, НастройкиОтчета)` is defined.

**Example:**
```bsl
// In your own ВариантыОтчетовПереопределяемый (hook — БСП calls it, application code implements it)
Процедура НастроитьВариантыОтчетов(Настройки) Экспорт
    ВариантыОтчетов.НастроитьОтчетВМодулеМенеджера(Настройки, Метаданные.Отчеты.ЗависимостиПодсистем);
    ВариантыОтчетов.НастроитьОтчетВМодулеМенеджера(Настройки, Метаданные.Отчеты.СтатистикаВыполненияОбработчиковОбновления);
КонецПроцедуры

// In the report manager module:
Процедура НастроитьВариантыОтчета(Настройки, НастройкиОтчета) Экспорт
    Вариант = НастройкиОтчета.Варианты.Добавить();
    Вариант.КлючВарианта = "Основной";
    Вариант.Представление = НСтр("ru = 'Основной'");
КонецПроцедуры
```

**Nuances / anti-patterns:**
- `ВариантыОтчетовПереопределяемый.НастроитьВариантыОтчетов(Настройки)` — **hook**
  override (БСП calls it, application code implements it). Do not call it from
  application code directly.
- ❌ Creating report variants "around" the `ВариантыОтчетов` catalog (with your own
  register) breaks the reports panel, settings caching, access by rights,
  and user-defined variants.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`, unless otherwise specified):

- `УправлениеПечатью.КомандыПечатиФормы(Форма, СписокОбъектов = Неопределено) Экспорт`
  — Function (server): assembled list of print commands for an arbitrary form
  (journals, shared forms).
- `УправлениеПечатью.СведенияОПечатнойФорме(КоллекцияПечатныхФорм, Идентификатор) Экспорт`
  — Function (server): collection row by `ИмяМакета`.
- `УправлениеПечатью.МакетыИДанныеОбъектовДляПечати(Знач ИмяМенеджераПечати, Знач ИменаМакетов, Знач СоставДокументов) Экспорт`
  — Function (server): in a single call - template binary data + object data
  (for printing office documents from the client).
- `УправлениеПечатью.СписокПечатныхФормИзВнешнихИсточников(ПолноеИмяОбъектаМетаданных) Экспорт`
  — Function (server): print forms from external print-form processing.
- `ВариантыОтчетов.ОписаниеОтчета(Настройки, Отчет) Экспорт`,
  `ВариантыОтчетов.ОписаниеВарианта(Настройки, Отчет, КлючВарианта) Экспорт` — Functions
  (server): report/variant description.
- `ВариантыОтчетов.КлючиВариантовОтчета(КлючОтчета, Знач Пользователь = Неопределено) Экспорт`
  — Function (server): keys of the user's report variants.
- `ВариантыОтчетовКлиент.ОбновитьОткрытыеФормы(Знач КлючВарианта = "", Знач Источник = Неопределено) Экспорт`
  — Procedure (client): update open report forms after a variant change.

Override hooks (`УправлениеПечатьюПереопределяемый`, `ВариантыОтчетовПереопределяемый`,
region `ПрограммныйИнтерфейс`) - БСП calls them, application code implements:

- `УправлениеПечатьюПереопределяемый.ПриОпределенииНастроекПечати(Настройки)` — general
  print subsystem settings (`ИспользоватьПодписиИПечати`, list of objects with commands).
- `УправлениеПечатьюПереопределяемый.ПриПечати(МассивОбъектов, ПараметрыПечати, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода)`
  — post-processing of print forms after the print manager.
- `УправлениеПечатьюПереопределяемый.ПриПолученииКомандПечати(Знач ПолноеИмяОбъектаМетаданных, КомандыПечати)`
  — modification of the object's print command collection.
- ⚠️ `УправлениеПечатьюПереопределяемый.ПриОпределенииОбъектовСКомандамиПечати(СписокОбъектов)`
  — **deprecated** (region `УстаревшиеПроцедурыИФункции`); do not use in new
  code, alternative - `ПриОпределенииНастроекПечати`.
- `ВариантыОтчетовПереопределяемый.НастроитьВариантыОтчетов(Настройки)` —
  registering variants (see scenario 7).
- `ВариантыОтчетовПереопределяемый.ОпределитьРазделыСВариантамиОтчетов(Разделы)` —
  composition of sections with report variants.
- `ВариантыОтчетовПереопределяемый.ОпределитьОбъектыСКомандамиОтчетов(Объекты)` —
  objects with report commands in forms.
- `ВариантыОтчетовПереопределяемый.ПриОпределенииНастроек(Настройки)` — general
  settings of the report variants subsystem.

For searching signatures/regions of any method -
`python .claude/skills/bsp/scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`. 