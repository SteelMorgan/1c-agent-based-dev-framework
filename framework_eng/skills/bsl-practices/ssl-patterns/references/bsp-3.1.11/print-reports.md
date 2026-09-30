# Printing and report variants

Subsystems **Печать** and **ВариантыОтчетов** are the infrastructure for print forms (spreadsheets and office documents, the «Печать» submenu in forms) and report variants (the `ВариантыОтчетов` catalog, attaching variants to subsystems, programmatic generation of a report on SCD). When you need to generate a print form via the «Печать» command or open/generate a report through the БСП variant infrastructure.

## Modules

Family **УправлениеПечатью** (subsystem `Печать`):

- `УправлениеПечатью` — server, stable API: `СоздатьКоллекциюКомандПечати`,
  `КомандыПечатиФормы`, `ВывестиТабличныйДокументВКоллекцию`, `ЗадатьОбластьПечатиДокумента`,
  `МакетПечатнойФормы`, `МакетыИДанныеОбъектовДляПечати`, `СведенияОПечатнойФорме`,
  `НужноПечататьМакет`, `ДобавитьУсловиеВидимостиКоманды`.
- `УправлениеПечатьюКлиент` — client, stable API: `ВыполнитьКомандуПечати`,
  `ВыполнитьКомандуПечатиНаПринтер`, `ПечатьДокументов`, `ПараметрыПечати`.
- `УправлениеПечатьюВызовСервера` — server call from the client without form context.
- `УправлениеПечатьюКлиентСервер` — common: field name constants, save settings.
- `УправлениеПечатьюПереопределяемый` — **hooks**: `ПриОпределенииНастроекПечати`
  (registering print objects through `Настройки.ОбъектыПечати.Добавить(...)` —
  the main hook), `ПриПечати`, `ПриПолученииКомандПечати`, `ПередПечатью`,
  `ПриОпределенииИсточниковДанныхПечати`, `ПриПодготовкеДанныхПечати`
  (auto composition). БСП calls it, application code implements it, not called
  directly. ❌ Obsolete `ПриОпределениеОбъектовСКомандамиПечати` — kept
  for backward compatibility, do not use in new code (see scenario 0).
- `УправлениеПечатьюСлужебный`, `УправлениеПечатьюСлужебныйКлиент` — ⚠️ utility.

Family **ВариантыОтчетов** (subsystem `ВариантыОтчетов`):

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
  module is called `УправлениеПечатью`. `python … module Печать` will report
  «Module 'Печать' not found» — that is not an error, use `module УправлениеПечатью`.
>
> ⚠️ `УправлениеПечатью.СформироватьПечатныеФормы` is the
> `СлужебныеПроцедурыИФункции` region (not `ПрограммныйИнтерфейс`); `ВариантыОтчетов.СформироватьОтчет`
> is `СлужебныйПрограммныйИнтерфейс`. Both may change in a minor version of БСП —
> for new code, prefer the client stable paths (`ВыполнитьКомандуПечати`,
> `ОтчетыКлиент.СформироватьОтчет`).

## Scenarios

### 0. Register an object for printing (ПриОпределенииНастроекПечати)

**Task:** connect a configuration object (document/catalog) to the БСП printing subsystem so that БСП calls `ДобавитьКомандыПечати` in the object manager module.

Without this registration, the “Print” submenu in the object forms will remain empty, and `ДобавитьКомандыПечати` will not be called.

**Step 1 — registration in the overridable module.** In
`УправлениеПечатьюПереопределяемый.ПриОпределенииНастроекПечати(Настройки)`
(a hook called by БСП), add the object manager to `Настройки.ОбъектыПечати`:

```bsl
// Общий модуль УправлениеПечатьюПереопределяемый
Процедура ПриОпределенииНастроекПечати(Настройки) Экспорт
    Настройки.ОбъектыПечати.Добавить(Справочники._ДемоОрганизации);
    Настройки.ОбъектыПечати.Добавить(Документы._ДемоСписаниеТоваров);
КонецПроцедуры
```

`Настройки.ОбъектыПечати` is a `Массив`; its elements are object managers
(`Справочники.Имя`, `Документы.Имя`) in whose module
`ПриОпределенииНастроекПечати` is implemented.

**Step 2 — hook in the object manager module.** Implement
`ПриОпределенииНастроекПечати(Настройки)` with
`Настройки.ПриДобавленииКомандПечати = Истина` set - this is the flag by which БСП
will call `ДобавитьКомандыПечати`:

```bsl
// Модуль менеджера объекта (Catalogs/МашиночитаемыеДоверенности/Ext/ManagerModule.bsl)
Процедура ПриОпределенииНастроекПечати(Настройки) Экспорт
    Настройки.ПриДобавленииКомандПечати = Истина;
КонецПроцедуры
```

Mechanism (confirmed in `src/cf/CommonModules/УправлениеПечатью/Ext/Module.bsl`):
- `УправлениеПечатью.НастройкиПечатиОбъекта(МенеджерОбъекта)` (стр. ~1051)
  returns a structure with `ПриДобавленииКомандПечати` (default `Ложь`) and
  calls `МенеджерОбъекта.ПриОпределенииНастроекПечати(Настройки)` if the object
  is registered in `Настройки.ОбъектыПечати`.
- `КомандыПечатиОбъекта` (стр. ~4719) checks `ПриДобавленииКомандПечати` and
  only then calls `Менеджер.ДобавитьКомандыПечати(КомандыПечати)`.

**Nuances / anti-patterns:**
- ❌ Use the obsolete `ПриОпределениеОбъектовСКомандамиПечати` (left for
  backward compatibility, marked `// АПК:222 Вызов устаревшей процедуры`). In new
  code - only `ПриОпределенииНастроекПечати` + `Настройки.ОбъектыПечати`.
- Without `Настройки.ПриДобавленииКомандПечати = Истина`, БСП will not call
  `ДобавитьКомандыПечати` - the default flag value is `Ложь`.
- `ПриОпределенииНастроекПечати` in the manager module and the hook of the same name in
  the overridable module are different procedures: the first defines object flags,
  the second registers the object itself.

### 1. Declare the print form in the object’s "Print" submenu

**Task:** in the object manager module (document/catalog), register a
print command so that БСП automatically places it in the form’s "Print" submenu.

**Prerequisite:** the object is connected to the print subsystem - see scenario 0
(`ПриОпределенииНастроекПечати` with `ПриДобавленииКомандПечати = Истина`).

**Function:**
`УправлениеПечатью.СоздатьКоллекциюКомандПечати() Экспорт`
- Function -> ТаблицаЗначений (empty print command collection), region
`#Область ПрограммныйИнтерфейс` (stable). Server, ExternalConnection.

**Parameters:** none. Returns an empty table with columns `Идентификатор`,
`Представление`, `МенеджерПечати`, `Картинка`, `Порядок`, `ПроверкаПроведенияПередПечатью`,
`ДополнительныеПараметры` and others.

**Hook signature:** `ДобавитьКомандыПечати(КомандыПечати) Экспорт` - **one parameter**
(without `Параметры`). Confirmed in all manager modules `src/cf/`
(`Catalogs/МашиночитаемыеДоверенности`, `Documents/_ДемоСписаниеТоваров` and others).

**Example (from `Catalogs/МашиночитаемыеДоверенности/Ext/ManagerModule.bsl`):**
```bsl
// Модуль менеджера объекта
Процедура ДобавитьКомандыПечати(КомандыПечати) Экспорт
    // КомандыПечати приходит уже инициализированной — СоздатьКоллекциюКомандПечати
    // вызывать не нужно. Достаточно добавить строку.
    КомандаПечати = КомандыПечати.Добавить();
    КомандаПечати.Идентификатор = "Доверенность";
    КомандаПечати.Представление = НСтр("ru = 'Доверенность'");
    // МенеджерПечати можно не указывать — БСП подставит полное имя объекта.
    // Для автокомпоновки (Способ А) указывать "УправлениеПечатью" — см. сценарий 1А.

    // Видимость команды — только если есть файл доверенности
    УправлениеПечатью.ДобавитьУсловиеВидимостиКоманды(
        КомандаПечати, "ФайлДоверенности",
        Справочники.МашиночитаемыеДоверенностиПрисоединенныеФайлы.ПустаяСсылка(),
        ВидСравнения.НеРавно);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Specify a second `Параметры` parameter in the `ДобавитьКомандыПечати` signature -
  БСП calls it with one parameter, `КомандыПечати`, and does not pass `Параметры`.
- ❌ Implement print commands outside the object manager module (for example, in the common
  module "ПечатныеФормы"). `УправлениеПечатью.КомандыПечатиФормы` looks for commands exactly
  in `ДобавитьКомандыПечати` of the manager module - the submenu will remain empty.
- ❌ Create your own common module `ПечатьМоейПодсистемы` and generate tabular
  documents bypassing `УправлениеПечатью` - this breaks the "Print" submenu, performance
  measurements, rights checks, print form language switching, and distribution.
- `УправлениеПечатью.ДобавитьУсловиеВидимостиКоманды(КомандаПечати, Реквизит, Значение, Знач МетодСравнения = Неопределено)`
  - the 4th parameter is named `МетодСравнения` (not `ВидСравнения`, as in
  `ПодключаемыеКоманды.ДобавитьУсловиеВидимостиКоманды` from `commands-external.md`).

### 1А. Automatic composition of a print form by template and data (Method A)

**Task:** generate a print form **without writing the `Печать` procedure** —
БСП will assemble the tabular document itself from the template and the data composition schema (СКД).
Suitable for simple print forms where a template with parameters is enough.

**Conditions:**
- `КомандаПечати.МенеджерПечати = "УправлениеПечатью"` (the name of the common module, **not**
  the full object name). БСП uses the internal print manager.
- `КомандаПечати.Идентификатор` is the **full path to the template**: `"<Object>.ПФ_MXL_<Name>"`
  (for example, `"Документ._ДемоСчетНаОплатуПокупателю.ПФ_MXL_СчетНаОплату"`).
  Template prefixes: `ПФ_MXL_` (tabular document, `.mxl`), `ПФ_DOC_` (Office Open
  XML, `.docx`).
- You do **not** need to write the `Печать(...)` procedure in the manager module.
- СКД is generated **automatically** from the object attributes (all fields + tabular
  sections) or overridden by the `ДанныеПечати` template in the metadata object, or
  created programmatically in the hook `УправлениеПечатьюПереопределяемый.ПриОпределенииИсточниковДанныхПечати`.

**Example (from the БСП documentation, ch. 3):**
```bsl
// Модуль менеджера объекта
Процедура ДобавитьКомандыПечати(КомандыПечати) Экспорт
    КомандаПечати = КомандыПечати.Добавить();
    КомандаПечати.МенеджерПечати = "УправлениеПечатью";
    КомандаПечати.Идентификатор = "Документ._ДемоСчетНаОплатуПокупателю.ПФ_MXL_СчетНаОплату";
    КомандаПечати.Представление = НСтр("ru = 'Счет на оплату (на основе СКД)'");
КонецПроцедуры
```

**СКД fields:** the field list must include `Ссылка` (used to filter data). If
an data set contains a table (tabular section), `НомерСтроки` is mandatory.

**Object-type data set:** if СКД uses an **Объект** data set, the manager module needs `ПриПодготовкеДанныхПечати(ИсточникиДанных, ВнешниеНаборыДанных, КодЯзыка, ДополнительныеПараметры) Экспорт`
— fill `ВнешниеНаборыДанных`.

**Registering templates through the catalog.** БСП also adds print commands
automatically from `Справочник.МакетыПечатныхФорм` (registered templates)
through the internal `УправлениеПечатью.ДобавитьКомандыПечати(КомандыПечати, ОбъектМетаданных)`
(`src/cf/CommonModules/УправлениеПечатью/Ext/Module.bsl` line ~6830) — it reads
`МакетыПечатныхФорм.ИсточникиДанных`, sets `МенеджерПечати = ИмяМодуляПечати()`
(i.e. `"УправлениеПечатью"`) and `Идентификатор = "ПФ_" + Идентификатор`. This
allows an administrator to add/edit templates in enterprise mode.

**Nuances / anti-patterns:**
- ❌ Set `МенеджерПечати = "Документ.МойДокумент"` for auto-composition —
  БСП will look for the `Печать` procedure, it does not exist → error. For auto-composition only
  `"УправлениеПечатью"`.
- For complex logic (multiple templates, calculated areas, signatures/stamps in
  non-standard places) — use Method B (scenario 2, `Печать` procedure).
- Programmatic creation of СКД — functions `УправлениеПечатью.ТаблицаПолейДанныхПечати()`,
  `ДеревоПолейДанныхПечати()`, `СхемаКомпоновкиДанныхПечати(СписокПолей)`.

### 2. Implement the print manager (Method B — programmatic generation)

**Task:** implement the exported `Печать(...)` in the object manager module,
which the БСП infrastructure will call when the print command is selected; build a tabular
document and place it into the collection of print forms. Used when automatic layout
(scenario 1A) is not enough — complex logic, multiple layouts,
computed areas, etc. In this case, the print command is registered with
`МенеджерПечати = "\u003cПолноеИмяОбъекта\u003e"` (not `"УправлениеПечатью"`).

**Functions:**
`УправлениеПечатью.НужноПечататьМакет(КоллекцияПечатныхФорм, ИмяМакета) Экспорт` — Function → Boolean, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеПечатью.ЗадатьОбластьПечатиДокумента(ТабличныйДокумент, НомерСтрокиНачало, ОбъектыПечати, Ссылка) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеПечатью.ВывестиТабличныйДокументВКоллекцию(КоллекцияПечатныхФорм, ИмяМакета, СинонимМакета, ТабличныйДокумент, Картинка = Неопределено, ПолныйПутьКМакету = "", ИмяФайлаПечатнойФормы = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеПечатью.МакетПечатнойФормы(ПутьКМакету, Знач КодЯзыка = Неопределено) Экспорт` — Function → ТабличныйДокумент, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `КоллекцияПечатныхФорм` (ТаблицаЗначений) — collection from `Печать(...)`; columns
  `ИмяМакета`, `СинонимМакета`, `ТабличныйДокумент`, `Картинка`, …
- `ИмяМакета` (Строка) — layout identifier for which the document is generated.
- `ТабличныйДокумент` (ТабличныйДокумент) — ready document for output.
- `ОбъектыПечати` (ТаблицаЗначений) — "object → area" table for linking
  printed areas with the source references (used when printing a set).
- `НомерСтрокиНачало` (Число) — starting row number of one object's area.
- `Ссылка` (ЛюбаяСсылка) — owner object of the area.
- `ПутьКМакету` (Строка) — path like `"Документ._ДемоСписаниеТоваров.ПФ_MXL_СписаниеТоваров"`.
- `КодЯзыка` (Строка) — layout language code; `Неопределено` → current.

**Example:**
```bsl
// Модуль менеджера документа
Процедура Печать(МассивОбъектов, ПараметрыПечати, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода) Экспорт
    Если УправлениеПечатью.НужноПечататьМакет(КоллекцияПечатныхФорм, "СписаниеТоваров") Тогда
        ТабДок = Новый ТабличныйДокумент;
        Для Каждого Ссылка Из МассивОбъектов Цикл
            НачалоОбласти = ТабДок.ВысотаТаблицы + 1;
            // ...заполнение строк ТабДок по данным Ссылка...
            УправлениеПечатью.ЗадатьОбластьПечатиДокумента(ТабДок, НачалоОбласти, ОбъектыПечати, Ссылка);
        КонецЦикла;
        УправлениеПечатью.ВывестиТабличныйДокументВКоллекцию(
            КоллекцияПечатныхФорм, "СписаниеТоваров", НСтр("ru = 'Списание товаров'"), ТабДок);
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Reading the layout through `ПолучитьОбщийМакет(...)` / `МакетОбъекта()` bypassing
  `УправлениеПечатью.МакетПечатнойФормы` — user layouts are no longer respected
  (the administrator could have overridden the layout) and the language code.
- `МассивОбъектов`, `КоллекцияПечатныхФорм`, `ОбъектыПечати`, `ПараметрыВывода` —
  standard print manager parameters; `ПараметрыПечати` is a structure passed
  from `УправлениеПечатьюКлиент.ВыполнитьКомандуПечати` (contains the command's
  `ДополнительныеПараметры`, `ЗаголовокФормы`, etc.).
- Post-processing of the print form (add date/signature) — in the hook
  `УправлениеПечатьюПереопределяемый.ПриПечати(МассивОбъектов, ПараметрыПечати, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода)`
  (override, not a call).

### 3. Start printing from the object form (client → ПечатьДокументов form)

**Task:** on clicking the "Print" command in the form, open the `ПечатьДокументов` form with
ready-made tabular documents; or print directly to the printer.

**Functions:**
`УправлениеПечатьюКлиент.ВыполнитьКомандуПечати(ИмяМенеджераПечати, ИменаМакетов, МассивОбъектов, ВладелецФормы, ПараметрыПечати = Неопределено) Экспорт` — Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.
`УправлениеПечатьюКлиент.ВыполнитьКомандуПечатиНаПринтер(ИмяМенеджераПечати, ИменаМакетов, МассивОбъектов, ПараметрыПечати = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `ИмяМенеджераПечати` (String) — full name of the print manager module, e.g.
  `"Документ._ДемоСписаниеТоваров"`.
- `ИменаМакетов` (String) — layout identifiers separated by commas, e.g.
  `"СписаниеТоваров,АктСписания"`.
- `МассивОбъектов` (Array) — object references to print.
- `ВладелецФормы` (ManagedForm) — owner form (for `ВыполнитьКомандуПечати`).
- `ПараметрыПечати` (Structure / Undefined) — parameters structure; default is
  `Неопределено` (БСП will assemble it from `ПараметрыПечати()` and the command's `ДополнительныеПараметры`).

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
- ❌ Calling `УправлениеПечатью.СформироватьПечатныеФормы` synchronously from a form
  command handler makes the form "freeze" during heavy printing. For interactive printing,
  use `ВыполнитьКомандуПечати` (it opens `ПечатьДокументов` and generates in the background);
  for heavy server-side generation, use `ДлительныеОперации.ВыполнитьФункцию`
  (see scenario 6).
- `ВыполнитьКомандуПечатиНаПринтер` skips preview; use it for
  scheduled/batch printing, not for manual commands.

### 4. Programmatically generate a print forms package (server)

**Task:** generate a package of print forms from server-side code (for example, for
sending by e-mail, distributing reports) and obtain ready-made tabular documents.

**Function:**
`УправлениеПечатью.СформироватьПечатныеФормы(Знач ИмяМенеджераПечати, Знач ИменаМакетов, Знач МассивОбъектов, Знач ПараметрыПечати, ДопустимыеТипыОбъектовПечати = Неопределено, Знач КодЯзыка = Неопределено, Знач ОбъектыПечати = Неопределено) Экспорт`
— Function → Structure (`КоллекцияПечатныхФорм`, `ОбъектыПечати`, `ПараметрыВывода`),
region `#Область СлужебныеПроцедурыИФункции` (⚠️ service, not `ПрограммныйИнтерфейс`).
Server.

**Parameters:**
- `ИмяМенеджераПечати` (String) — full name of the print manager module.
- `ИменаМакетов` (String) — layout identifiers separated by commas.
- `МассивОбъектов` (Array) — object references for printing.
- `ПараметрыПечати` (Structure) — parameters; at minimum an empty `Новый Структура`.
- `ДопустимыеТипыОбъектовПечати` (TypeDescription / Undefined) — restriction on object types;
  `Неопределено` → no validation.
- `КодЯзыка` (String) — language code; `Неопределено` → current.
- `ОбъектыПечати` (ValueTable / Undefined) — for returning the area mapping.

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

**Nuances / antipatterns:**
- ⚠️ The method in the `СлужебныеПроцедурыИФункции` region is not
  guaranteed to remain backward compatible. For interactive printing, prefer the client-side
  `ВыполнитьКомандуПечати`; for server-side "heavy" printing, use background generation
  via `ДлительныеОперации.ВыполнитьФункцию` (scenario 6).
- The method has variants in `УправлениеПечатьюВызовСервера.СформироватьПечатныеФормы(МассивОбъектов, Команды)`
  and `УправлениеПечатьюСлужебный.СформироватьПечатныеФормы(...)` — all are service-level, with
  different signatures. Application code uses only the server-side
  `УправлениеПечатью.СформироватьПечатныеФормы` with the 7-parameter signature above.

### 5. Open the report form by variant

**Task:** programmatically open the report form (internal or additional) by
reference to a variant from the `ВариантыОтчетов` catalog.

**Functions:**
`ВариантыОтчетов.ВариантОтчета(Отчет, КлючВарианта) Экспорт` — Function → СправочникСсылка.ВариантыОтчетов / Неопределено, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ВариантыОтчетовКлиент.ОткрытьФормуОтчета(Знач ФормаВладелец, Знач Вариант, Знач ДополнительныеПараметры = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `Отчет` (СправочникСсылка.ИдентификаторыОбъектовРасширений /
  ИдентификаторыОбъектовМетаданных / ДополнительныеОтчетыИОбработки / Строка) — report
  or full name of an external report.
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
- ❌ `ОткрытьЗначение(СсылкаВарианта)` or `ПолучитьФорму(...).Открыть()` — bypasses
  the variant subsystem: breaks settings caching, the reports panel, and access
  by permissions. Only `ВариантыОтчетовКлиент.ОткрытьФормуОтчета`.
- `ВариантОтчета` returns `Неопределено` if the report is missing or unavailable
  by permissions — check before opening.
- For an external (additional) report, the same `ОткрытьФормуОтчета` opens the form
  of the additional report; the variant reference is provided by `ВариантОтчета` with `Отчет` of type
  `СправочникСсылка.ДополнительныеОтчетыИОбработки`.

### 6. Generate report programmatically (heavy - in background)

**Task:** programmatically generate a report on SCD and obtain a tabular document;
do not block the form with heavy generation - move it to a background job.

**Functions:**
`ВариантыОтчетов.СформироватьОтчет(Знач Параметры, Знач ПроверятьЗаполнение, Знач ПолучатьФлажокПустой) Экспорт` — Function → Structure (`ТабличныйДокумент`, `Успех`, `ТекстОшибки`, `Расшифровка`, `НастройкиКД`), region `#Область СлужебныйПрограммныйИнтерфейс` (⚠️ service). Server.
`ВариантыОтчетов.ПараметрыФормированияОтчета() Экспорт` — Function → Structure, `#Область СлужебныйПрограммныйИнтерфейс`. Server.
`ДлительныеОперации.ВыполнитьФункцию(Знач ПараметрыВыполнения, ИмяФункции, Знач Параметр1 = Неопределено, Знач Параметр2 = Неопределено, Знач Параметр3 = Неопределено, Знач Параметр4 = Неопределено, Знач Параметр5 = Неопределено, Знач Параметр6 = Неопределено, Знач Параметр7 = Неопределено) Экспорт` — Function, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters (СформироватьОтчет):**
- `Параметры` (Structure) — from `ПараметрыФормированияОтчета()`, filled:
  `Отчет` (Metadata / string), `КлючВарианта` (String), user settings.
- `ПроверятьЗаполнение` (Boolean) — `Истина` → validate parameter filling.
- `ПолучатьФлажокПустой` (Boolean) — `Истина` → return the "report empty" flag.

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
- ⚠️ `ВариантыОтчетов.СформироватьОтчет` — service (region
  `СлужебныйПрограммныйИнтерфейс`), backward compatibility is not guaranteed. For
  generating a report **from its form** there is a stable
  `ОтчетыКлиент.СформироватьОтчет(ФормаОтчета, ОбработчикЗавершения = Неопределено)`
  (client, `ПрограммныйИнтерфейс`) — preferred when the report is already open as a form.
- ❌ `ДлительныеОперации.ВыполнитьВФоне(ИмяПроцедуры, ПараметрыПроцедуры, ПараметрыВыполнения)`
  in new code — not recommended (the method is stable, `ПрограммныйИнтерфейс`, but
  the BSP doc comment explicitly recommends `ВыполнитьФункцию`/`ВыполнитьПроцедуру`;
  requires packing parameters into a `Structure` and an explicit `АдресХранилища` in
  the handler). Use `ВыполнитьФункцию` / `ВыполнитьПроцедуру` (up to 7
  parameters directly, without wrapping). Details — in `longs-and-jobs.md`.
- ❌ Synchronous `СформироватьОтчет` from an `&НаКлиенте` handler blocks the form —
  move heavy generation into `ВыполнитьФункцию` + `ОжидатьЗавершение`.

### 7. Register report variants in the overridable module

**Task:** when implementing БСП, register the report variants of the application
configuration (call `НастроитьОтчетВМодулеМенеджера` for each report).

**Function:**
`ВариантыОтчетов.НастроитьОтчетВМодулеМенеджера(Настройки, ОтчетМетаданные) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Настройки` (Структура) — variant settings (passed from the hook
  `НастроитьВариантыОтчетов`).
- `ОтчетМетаданные` (ОбъектМетаданных) — report metadata, in the manager module
  of which `НастроитьВариантыОтчета(Настройки, НастройкиОтчета)` is defined.

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
  for overriding (БСП calls it, application code implements it). Do not call it from
  application code directly.
- ❌ Generating report variants "outside" the `ВариантыОтчетов` catalog (with your own
  register) breaks the report panel, settings caching, permission-based availability,
  and user variants.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`, unless otherwise noted):

- `УправлениеПечатью.КомандыПечатиФормы(Форма, СписокОбъектов = Неопределено) Экспорт`
  — Function (server): collected list of print commands for an arbitrary form
  (journals, common forms).
- `УправлениеПечатью.СведенияОПечатнойФорме(КоллекцияПечатныхФорм, Идентификатор) Экспорт`
  — Function (server): collection string by `ИмяМакета`.
- `УправлениеПечатью.МакетыИДанныеОбъектовДляПечати(Знач ИмяМенеджераПечати, Знач ИменаМакетов, Знач СоставДокументов) Экспорт`
  — Function (server): in one call - template binary data + object data
  (for printing office documents from the client).
- `УправлениеПечатью.СписокПечатныхФормИзВнешнихИсточников(ПолноеИмяОбъектаМетаданных) Экспорт`
  — Function (server): print forms from external print-form handlers.
- `ВариантыОтчетов.ОписаниеОтчета(Настройки, Отчет) Экспорт`,
  `ВариантыОтчетов.ОписаниеВарианта(Настройки, Отчет, КлючВарианта) Экспорт` — Functions
  (server): report/variant description.
- `ВариантыОтчетов.КлючиВариантовОтчета(КлючОтчета, Знач Пользователь = Неопределено) Экспорт`
  — Function (server): keys of the user's report variants.
- `ВариантыОтчетовКлиент.ОбновитьОткрытыеФормы(Знач КлючВарианта = "", Знач Источник = Неопределено) Экспорт`
  — Procedure (client): refresh open report forms after changing the variant.

Override hooks (`УправлениеПечатьюПереопределяемый`, `ВариантыОтчетовПереопределяемый`,
region `ПрограммныйИнтерфейс`) — БСП calls, application code implements:

- `УправлениеПечатьюПереопределяемый.ПриОпределенииНастроекПечати(Настройки)` — general
  print subsystem settings (`ИспользоватьПодписиИПечати`, list of objects with commands).
- `УправлениеПечатьюПереопределяемый.ПриПечати(МассивОбъектов, ПараметрыПечати, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода)`
  — post-processing of print forms after the print manager.
- `УправлениеПечатьюПереопределяемый.ПриПолученииКомандПечати(Знач ПолноеИмяОбъектаМетаданных, КомандыПечати)`
  — modification of the object's print command collection.
- ⚠️ `УправлениеПечатьюПереопределяемый.ПриОпределенииОбъектовСКомандамиПечати(СписокОбъектов)`
  — **obsolete** (region `УстаревшиеПроцедурыИФункции`); do not use in new
  code, alternative is `ПриОпределенииНастроекПечати`.
- `ВариантыОтчетовПереопределяемый.НастроитьВариантыОтчетов(Настройки)` —
  variant registration (see scenario 7).
- `ВариантыОтчетовПереопределяемый.ОпределитьРазделыСВариантамиОтчетов(Разделы)` —
  section composition with report variants.
- `ВариантыОтчетовПереопределяемый.ОпределитьОбъектыСКомандамиОтчетов(Объекты)` —
  objects with report commands in forms.
- `ВариантыОтчетовПереопределяемый.ПриОпределенииНастроек(Настройки)` — general
  settings of the report variants subsystem.

To search for signatures/regions of any method —
`python .claude/skills/bsp/scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`.