# Connected commands and additional reports/processings

The **ПодключаемыеКоманды** and **ДополнительныеОтчетыИОбработки** subsystems are infrastructure
for placing “connected” commands in forms (printing, reports, filling, “create based on”, message templates) and for connecting external reports/processings (epf/erf) through
the `ДополнительныеОтчетыИОбработки` catalog. Use this when you need to add a command to an object form
whose source is an external processing or another connected object, rather than
the form's own code.

## Modules

The **ПодключаемыеКоманды** family (suffix logic):

- `ПодключаемыеКоманды` — server: placement of commands in a form, execution of server
  commands (`ПриСозданииНаСервере`, `ВыполнитьКоманду`, `ДобавитьУсловиеВидимостиКоманды`).
- `ПодключаемыеКомандыКлиент` — client: click/write handlers
  (`НачатьВыполнениеКоманды`, `ВыполнитьКоманду`, `ПослеЗаписи`, `НачатьОбновлениеКоманд`).
- `ПодключаемыеКомандыКлиентСервер` — common: `ОбновитьКоманды` (stable);
  `УсловияВыполняются`, `ПараметрыВыполненияКоманды`, `ВладелецКомандыПоИмениКоманды` —
  ⚠️ internal.
- `ПодключаемыеКомандыВызовСервера` — server call from the client without form context.
- `ПодключаемыеКомандыПереопределяемый` — **hooks**: БСП calls them, application code
  implements them (`ПриОпределенииВидовПодключаемыхКоманд`,
  `ПриОпределенииКомандПодключенныхКОбъекту`). NOT called from application code.

The **ДополнительныеОтчетыИОбработки** family:

- `ДополнительныеОтчетыИОбработки` — server: `ПодключитьВнешнююОбработку`,
  `ОбъектВнешнейОбработки`, `ВыполнитьКоманду`, `СведенияОВнешнейОбработке`,
  `СохранитьНастройки`, `ЗагрузитьНастройки` (stable).
- `ДополнительныеОтчетыИОбработкиКлиент` — client: `ОткрытьФормуКоманд…`,
  `ОткрытьВариантДополнительногоОтчета`, `ВыполнитьКомандуВФоне`,
  `ПараметрыВыполненияКомандыВФоне` (stable).
- `ДополнительныеОтчетыИОбработкиКлиентСервер` — common: constants for command kinds and types
  (`ВидОбработкиЗаполнениеОбъекта()`, `ТипКомандыВызовСерверногоМетода()` …).
- ⚠️ `ДополнительныеОтчетыИОбработкиКлиент.ОткрытьСписокДополнительныхОтчетовИОбработок`
  and `ДополнительныеОтчетыИОбработки.ИспользуютсяДополнительныеОтчетыИОбработки` —
  `СлужебныйПрограммныйИнтерфейс` region; backward compatibility is not guaranteed.

The **СозданиеНаОсновании** family:

- `СозданиеНаОсновании` — server: `ДобавитьКомандуСозданияНаОсновании` (stable).
  `ПриОпределенииКомандПодключенныхКОбъекту` here is ⚠️ internal (БСП-internal);
  application-level override of the composition is through `ПодключаемыеКомандыПереопределяемый`.
- `СозданиеНаОснованииПереопределяемый` — a hook for registering objects with create-based-on commands.

> ⚠️ There are no modules `ПодключаемыеКомандыСервер`, `ДополнительныеОтчетыИОбработкиСервер`
> — a common mistake by analogy. The server module is always without the `Сервер` suffix.

## Scenarios

### 1. Place attached commands in the object form

**Task:** in a document/catalog form, automatically build the "Print", "Reports", "Fill", "Create Based On" submenu from all registered sources.

**Functions:**
`ПодключаемыеКоманды.ПриСозданииНаСервере(Форма, Знач ПараметрыРазмещения = Неопределено) Экспорт`
`ПодключаемыеКомандыКлиент.НачатьВыполнениеКоманды(Форма, Команда, Знач Источник = Неопределено) Экспорт`
`ПодключаемыеКомандыКлиент.ПослеЗаписи(Форма, Объект, ПараметрыЗаписи) Экспорт`
`ПодключаемыеКомандыКлиент.НачатьОбновлениеКоманд(Форма) Экспорт`
— all Procedures, region `#Область ПрограммныйИнтерфейс` (stable). The first one is Server;
all the others are Client.

**Parameters:**
- `Форма` (УправляемаяФорма) — the object form; in `ПриСозданииНаСервере` this is `ЭтотОбъект`.
- `ПараметрыРазмещения` (Structure / Undefined) — fine-tuning of the submenu
  (sources, command bar, group prefix). By default, `Неопределено` means
  standard placement. The structure is returned by `ПодключаемыеКоманды.ПараметрыРазмещения()`.
- `Команда` (КомандаФормы) — a command of the attached submenu.
- `Источник` (Arbitrary) — the object/list to which the command is bound
  (`Элементы.Список`, `Форма.Объект`).

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    // ...your initialization code...
    ПодключаемыеКоманды.ПриСозданииНаСервере(ЭтотОбъект);
КонецПроцедуры

&НаКлиенте
Процедура ПодключаемаяКоманда(Команда)
    // НачатьВыполнениеКоманды will check ТребуетсяЗапись/ТребуетсяПроведение itself,
    // ask the user and only then call the server.
    ПодключаемыеКомандыКлиент.НачатьВыполнениеКоманды(ЭтотОбъект, Команда, Элементы.Список);
КонецПроцедуры

&НаКлиенте
Процедура ПослеЗаписи(ПараметрыЗаписи)
    ПодключаемыеКомандыКлиент.ПослеЗаписи(ЭтотОбъект, Объект, ПараметрыЗаписи);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Forgetting `ПриСозданииНаСервере` — the submenus will not appear, all BSP infrastructure for
  the form is disabled. Always call it after your own initialization code.
- ❌ Calling `ПодключаемыеКоманды.ВыполнитьКоманду` (server-side) directly from a client
  `&НаКлиенте` handler — the method is not available on the client. From the client use
  `ПодключаемыеКомандыКлиент.НачатьВыполнениеКоманды` (it goes to the server on its own).
- ❌ Duplicating an attached command with a regular form button "bypassing" BSP — the command
  will not end up in the shared list, and will not be accounted for in "Current Tasks", roles, or visibility.
- `НачатьВыполнениеКоманды` builds the "Save? Post?" dialog and only after
  confirmation goes to the server — do not call the server-side `ВыполнитьКоманду` "directly"
  from the client without checking that the object is saved.

### 2. Set the command visibility condition by attribute value

**Task:** show the print/fill command only for posted documents
or only when the attribute is filled.

**Function:**
`ПодключаемыеКоманды.ДобавитьУсловиеВидимостиКоманды(Команда, Реквизит, Значение = Неопределено, Знач ВидСравнения = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Команда` (СтрокаТаблицыКоманд) — command collection row (from `КомандыПечати.Добавить()`
  or from `Команды` in `ПриОпределенииКомандПодключенныхКОбъекту`).
- `Реквизит` (Строка) — object attribute name, e.g. `"Проведен"`, `"Контрагент"`.
- `Значение` (Произвольный) — value to compare; `Неопределено` for `Заполнено`/
  `НеЗаполнено`.
- `ВидСравнения` (ВидСравненияКомпоновкиДанных) — `Равно`, `НеРавно`, `Заполнено`,
  `НеЗаполнено`, `ВСписке`, `НеВСписке`, `Больше`, `Меньше`, `БольшеИлиРавно`,
  `МеньшеИлиРавно`. By default `Неопределено` → `Равно`.

**Example:**
```bsl
// In the object manager module, in ДобавитьКомандыПечати (or in
// ПодключаемыеКомандыПереопределяемый.ПриОпределенииКомандПодключенныхКОбъекту):
КомандаПечати = КомандыПечати.Добавить();
КомандаПечати.Идентификатор = "Акт";
КомандаПечати.Представление = НСтр("ru = 'Акт выполненных работ'");

// Show only for posted documents
ПодключаемыеКоманды.ДобавитьУсловиеВидимостиКоманды(
    КомандаПечати, "Проведен", Истина, ВидСравненияКомпоновкиДанных.Равно);
```

**Nuances / anti-patterns:**
- ❌ Implement visibility "by hand" in the `ПриИзменении` handler of an attribute,
  toggling `Элементы.КомандаПечать.Видимость` — desynchronizes from the БСП infrastructure,
  breaks command refresh after saving. Only `ДобавитьУсловиеВидимостиКоманды`.
- Conditions are evaluated when `ПодключаемыеКомандыКлиентСервер.ОбновитьКоманды` is called (invoked
  by БСП after saving and when the context changes).
- For print commands there is a parallel method
  `УправлениеПечатью.ДобавитьУсловиеВидимостиКоманды(КомандаПечати, Реквизит, Значение, Знач МетодСравнения = Неопределено)`
  — the 4th parameter is named `МетодСравнения`, not `ВидСравнения`. See `print-reports.md`.

### 3. Declare the "Create Based On" commands for the object

**Task:** in the "Create Based On" submenu of the document form, show commands
for creating other documents based on the current one.

**Function:**
`СозданиеНаОсновании.ДобавитьКомандуСозданияНаОсновании(КомандыСозданияНаОсновании, ОбъектМетаданных) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `КомандыСозданияНаОсновании` (Массив) — collection of creation commands, filled in the
  `ДобавитьКомандыСозданияНаОсновании(КомандыСозданияНаОсновании, Параметры)` handler of the
  object manager module.
- `ОбъектМетаданных` (ОбъектМетаданных) — metadata of the resulting object, e.g.
  `Метаданные.Документы.СчетФактураВыданный`.

**Example:**
```bsl
// Source document manager module
Процедура ДобавитьКомандыСозданияНаОсновании(КомандыСозданияНаОсновании, Параметры) Экспорт
    СозданиеНаОсновании.ДобавитьКомандуСозданияНаОсновании(КомандыСозданияНаОсновании, Метаданные.Документы.СчетФактураВыданный);
    СозданиеНаОсновании.ДобавитьКомандуСозданияНаОсновании(КомандыСозданияНаОсновании, Метаданные.Документы.ПлатежноеПоручение);
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Call `СозданиеНаОсновании.ПриОпределенииКомандПодключенныхКОбъекту` — this is
  a ⚠️ service (`СлужебныйПрограммныйИнтерфейс`) internal БСП method. Application-side
  redefinition of the composition is in `ПодключаемыеКомандыПереопределяемый.ПриОпределенииКомандПодключенныхКОбъекту`.
- Registering the object itself as a subsystem participant is in
  `СозданиеНаОснованииПереопределяемый.ПриОпределенииОбъектовСКомандамиСозданияНаОсновании`
  (a redefinition hook implemented in the application configuration).
- Creating an object **programmatically** (`Документы.Х.СоздатьДокумент()`) is a platform
  API; the БСП connected-commands infrastructure has nothing to do with it.

### 4. Register an external processing object (describe registration parameters)

**Task:** in the external processing object (epf/erf), describe the type, purpose, commands, and
safe mode so that БСП correctly loads it into the `ДополнительныеОтчетыИОбработки`
directory.

**Function:**
`ДополнительныеОтчетыИОбработки.СведенияОВнешнейОбработке(ВерсияБСП = "") Экспорт`
— Function → Structure `ПараметрыРегистрации`, region `#Область ПрограммныйИнтерфейс`
(stable). Server.

**Parameters:**
- `ВерсияБСП` (String) — the БСП version for which the template is generated (affects the set
  of fields). Default is `""` — current.

The returned structure contains the fields: `Вид`, `Назначение` (an array of full names of
owner metadata), `Наименование`, `Информация`, `БезопасныйРежим` (Boolean),
`Команды` (a table with columns `Идентификатор`, `Представление`, `Использование`,
`ПоказыватьОповещение`, …). Command types and kinds are taken from
`ДополнительныеОтчетыИОбработкиКлиентСервер.ВидОбработки…()` /
`ТипКоманды…()` (all stable).

**Example:**
```bsl
// Module object of the external processing object
Функция СведенияОВнешнейОбработке() Экспорт
    ПараметрыРегистрации = ДополнительныеОтчетыИОбработки.СведенияОВнешнейОбработке("3.1.11.0");

    ПараметрыРегистрации.Вид             = ДополнительныеОтчетыИОбработкиКлиентСервер.ВидОбработкиЗаполнениеОбъекта();
    ПараметрыРегистрации.Назначение       = Новый Массив;
    ПараметрыРегистрации.Назначение.Добавить("Документ.РеализацияТоваровУслуг");
    ПараметрыРегистрации.Наименование   = "Заполнение реализации по заказу";
    ПараметрыРегистрации.БезопасныйРежим  = Истина;
    ПараметрыРегистрации.Информация      = "Заполняет табличную часть «Товары» по данным заказа";

    Команда = ПараметрыРегистрации.Команды.Добавить();
    Команда.Идентификатор         = "ЗаполнитьПоЗаказу";
    Команда.Представление         = "Заполнить по заказу";
    Команда.Использование         = ДополнительныеОтчетыИОбработкиКлиентСервер.ТипКомандыВызовСерверногоМетода();
    Команда.ПоказыватьОповещение   = Истина;

    Возврат ПараметрыРегистрации;
КонецФункции

Процедура ВыполнитьКоманду(ИдентификаторКоманды, ПараметрыВыполнения) Экспорт
    // ...серверная логика заполнения...
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Store the external processing object in a configuration template and connect it through
  `ПолучитьМакетОбработки`/`ОткрытьЗначение` — bypassing the directory breaks: loading/
  updating without a configuration update, safe mode, permissions, data separator in the service model,
  scheduled jobs. Only the `ДополнительныеОтчетыИОбработки` directory (loaded through “Administration → Print
  forms, reports and processing objects”).
- `БезопасныйРежим = Истина` forbids external processing objects from using external connections, the file
  system, and COM — set `Истина` if the processing object does not need dangerous actions.
- Available kinds: `ВидОбработкиПечатнаяФорма()`, `…ЗаполнениеОбъекта()`,
  `…СозданиеСвязанныхОбъектов()`, `…Отчет()`, `…ШаблонСообщения()`,
  `…ДополнительнаяОбработка()`, `…ДополнительныйОтчет()`. Command types:
  `ТипКомандыВызовСерверногоМетода()`, `…ВызовКлиентскогоМетода()`, `…ОткрытиеФормы()`,
  `…ЗаполнениеФормы()`, `…ЗагрузкаДанныхИзФайла()`.

### 5. Programmatically execute an external processing command (from a scheduled job)

**Task:** start a command of an assigned external processing object (e.g. “Fill Object”) from a scheduled job or server-side code, bypassing the form.

**Function:**
`ДополнительныеОтчетыИОбработки.ВыполнитьКоманду(ПараметрыКоманды, АдресРезультата = Неопределено) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ПараметрыКоманды` (Structure) — required keys:
  - `ДополнительнаяОбработкаСсылка` (СправочникСсылка.ДополнительныеОтчетыИОбработки) —
    reference to the registered processing object;
  - `ИдентификаторКоманды` (String) — command identifier from `ПараметрыРегистрации.Команды`;
  - `ОбъектыНазначения` (Array) — target object references; required for
    assignable processing objects (types “Fill Object”, “Create Related Objects”).
- `АдресРезультата` (String) — temporary storage address for the result; default is
  `Неопределено`.

**Example:**
```bsl
// Module of the scheduled job
Процедура ЗаполнитьДокументы() Экспорт
    Ссылка = Справочники.ДополнительныеОтчетыИОбработки.НайтиПоНаименованию("Заполнение документов");
    Если Ссылка.Пустая() Тогда
        Возврат;
    КонецЕсли;

    ПараметрыКоманды = Новый Структура;
    ПараметрыКоманды.Вставить("ДополнительнаяОбработкаСсылка", Ссылка);
    ПараметрыКоманды.Вставить("ИдентификаторКоманды",          "Заполнить");
    ПараметрыКоманды.Вставить("ОбъектыНазначения",              СсылкиНаДокументы());

    ДополнительныеОтчетыИОбработки.ВыполнитьКоманду(ПараметрыКоманды);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Use a “handwritten” processing registry (information register) instead of
  the `ДополнительныеОтчетыИОбработки` catalog — there is no safe mode, separators,
  scheduled jobs, or `УправлениеВнешнимиОбработками` interceptor.
- `ОбъектыНазначения` is required for assignable processing objects; for global
  ones (type “Additional processing”) it is not passed.
- Before starting, you can check `ДополнительныеОтчетыИОбработки.ИспользуютсяДополнительныеОтчетыИОбработки()`
  — ⚠️ the method is internal (region `СлужебныйПрограммныйИнтерфейс`), backward
  compatibility is not guaranteed; in new code, it is preferable to check for the
  presence of the reference via `Справочники.ДополнительныеОтчетыИОбработки.НайтиПоНаименованию(...).Пустая()`.

### 6. Connect an external processing and get its object

**Task:** programmatically connect epf/erf from the catalog and call its methods through
an object (e.g., in server-side printing from an external source).

**Functions:**
`ДополнительныеОтчетыИОбработки.ПодключитьВнешнююОбработку(Ссылка) Экспорт` — Function → String (name of the connected processing), region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ДополнительныеОтчетыИОбработки.ОбъектВнешнейОбработки(Ссылка) Экспорт` — Function → Object (instance of the external processing/report), region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Ссылка` (СправочникСсылка.ДополнительныеОтчетыИОбработки) — catalog record.

**Example:**
```bsl
ИмяОбработки = ДополнительныеОтчетыИОбработки.ПодключитьВнешнююОбработку(Ссылка);
ВнешнийОбъект = ДополнительныеОтчетыИОбработки.ОбъектВнешнейОбработки(Ссылка);
// Дальше — вызов экспортных методов внешнего объекта:
ВнешнийОбъект.МояЭкспортнаяПроцедура(Параметры);
```

**Nuances / antipatterns:**
- ❌ Connect epf through the platform `ВнешниеОбработки.Подключить(...)` bypassing
  БСП — safe mode, permissions, separators are lost. Only through
  `ДополнительныеОтчетыИОбработки.ПодключитьВнешнююОбработку`.
- `ОбъектВнешнейОбработки` internally connects the processing itself if necessary;
  explicit `ПодключитьВнешнююОбработку` is needed when the connection name is required.

### 7. Save and load external processing settings

**Task:** save arbitrary processing settings (fill parameters,
report settings) between runs and read them on the next call.

**Functions:**
`ДополнительныеОтчетыИОбработки.СохранитьНастройки(Ссылка, Настройки) Экспорт` — Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ДополнительныеОтчетыИОбработки.ЗагрузитьНастройки(Ссылка) Экспорт` — Function → Arbitrary (saved settings), region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Ссылка` (СправочникСсылка.ДополнительныеОтчетыИОбработки) — catalog record.
- `Настройки` (Произвольный) — serializable value (Structure, ValueTable …).

**Example:**
```bsl
// Сохранить
Настройки = Новый Структура("Период, Склад", Период, Склад);
ДополнительныеОтчетыИОбработки.СохранитьНастройки(СсылкаОбработки, Настройки);

// Прочитать при следующем запуске
Настройки = ДополнительныеОтчетыИОбработки.ЗагрузитьНастройки(СсылкаОбработки);
Если Настройки <> Неопределено Тогда
    Период = Настройки.Период;
КонецЕсли;
```

**Nuances / antipatterns:**
- ❌ Store processing settings in a separate information register "with your own key" —
  the link to the data separator and service model is lost. Use the standard
  `СохранитьНастройки`/`ЗагрузитьНастройки` (storage is tied to the processing reference).
- `ЗагрузитьНастройки` returns `Неопределено` if the settings were not
  previously saved — check before accessing via dot.

### 8. Open the processing commands form and run a command in the background

**Task:** from client code, open the command selection form for additional
reports/processings of the selected type; run a long-running processing command in the background
with a completion handler.

**Functions:**
`ДополнительныеОтчетыИОбработкиКлиент.ОткрытьФормуКомандДополнительныхОтчетовИОбработок(ПараметрКоманды, ПараметрыВыполненияКоманды, Вид, ИмяРаздела = "") Экспорт` — Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.
`ДополнительныеОтчетыИОбработкиКлиент.ВыполнитьКомандуВФоне(Знач ИдентификаторКоманды, Знач ПараметрыКоманды, Знач Обработчик) Экспорт` — Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.
`ДополнительныеОтчетыИОбработкиКлиент.ПараметрыВыполненияКомандыВФоне(Ссылка) Экспорт` — Function → Structure, region `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `ПараметрКоманды` (Arbitrary) — object/reference for which the command list is opened
  (for example, the form `Объект`).
- `ПараметрыВыполненияКоманды` (Structure) — execution parameters; see
  `ПодключаемыеКомандыКлиент.ПараметрыВыполненияКоманды()`.
- `Вид` (String) — processing type, for example the result of
  `ДополнительныеОтчетыИОбработкиКлиентСервер.ВидОбработкиЗаполнениеОбъекта()`.
- `ИмяРаздела` (String) — navigation section for placement; default `""`.
- `ИдентификаторКоманды` (String) — command identifier from `ПараметрыРегистрации.Команды`.
- `ПараметрыКоманды` (Structure) — keys `ДополнительнаяОбработкаСсылка`,
  `ОбъектыНазначения` (see scenario 5).
- `Обработчик` (DescriptionOfNotification) — completion handler for the background command.

**Example:**
```bsl
&НаКлиенте
Процедура КомандаЗаполнить(Команда)
    ДополнительныеОтчетыИОбработкиКлиент.ОткрытьФормуКомандДополнительныхОтчетовИОбработок(
        Объект,
        ПодключаемыеКомандыКлиент.ПараметрыВыполненияКоманды(),
        ДополнительныеОтчетыИОбработкиКлиентСервер.ВидОбработкиЗаполнениеОбъекта(),
        "Справочник.Контрагенты");
КонецПроцедуры

&НаКлиенте
Процедура ЗапуститьВФоне(Идентификатор, ПараметрыКоманды)
    ПараметрыВФоне = ДополнительныеОтчетыИОбработкиКлиент.ПараметрыВыполненияКомандыВФоне(СсылкаОбработки);
    ДополнительныеОтчетыИОбработкиКлиент.ВыполнитьКомандуВФоне(
        Идентификатор, ПараметрыКоманды,
        Новый ОписаниеОповещения("ПослеВыполненияКоманды", ЭтотОбъект, ПараметрыВФоне));
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Open the external report form through `ОткрытьЗначение(Ссылка)` — this bypasses
  the variants subsystem. The additional report is opened through
  `ДополнительныеОтчетыИОбработкиКлиент.ОткрытьВариантДополнительногоОтчета(ДополнительныйОтчет, КлючВарианта)`.
- `ОткрытьСписокДополнительныхОтчетовИОбработок()` — ⚠️ internal (region
  `СлужебныйПрограммныйИнтерфейс`); acceptable for admin UI, but backward
  compatibility is not guaranteed.

## Rare methods

Structure constructors and helper methods (all stable, region
`ПрограммныйИнтерфейс`, unless otherwise noted):

- `ПодключаемыеКоманды.ПараметрыРазмещения() Экспорт` — Function → Structure
  (`Источники`, `КоманднаяПанель`, `ПрефиксГрупп`, `ВладелецКоманд`). Passed as the second
  parameter to `ПриСозданииНаСервере` for custom submenu placement.
- `ПодключаемыеКоманды.ПараметрыВыполненияКоманды() Экспорт` and
  `ПодключаемыеКомандыКлиент.ПараметрыВыполненияКоманды() Экспорт` — Functions →
  Execution parameters structure (`ОписаниеКоманды`, `Форма`, `ЭтоФормаОбъекта`,
  `Источник`). Server and client variants respectively.
- `ПодключаемыеКомандыКлиентСервер.ОбновитьКоманды(Форма, Знач Источник = Неопределено) Экспорт`
  — Procedure (client + server): recalculate the visibility/availability/marks of form
  commands. Used after programmatic changes to the context.
- `ДополнительныеОтчетыИОбработки.ВыполнитьКомандуИзФормыВнешнегоОбъекта(ИдентификаторКоманды, ПараметрыКоманды, Форма) Экспорт`
  — Function (server): run a command from the form module of the external processing itself.
- `ДополнительныеОтчетыИОбработки.ПечатьПоВнешнемуИсточнику(ДополнительнаяОбработкаСсылка, ПараметрыИсточника, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода) Экспорт`
  — Procedure (server): link an external processing/print form with the print manager
  (called by the print infrastructure, see `print-reports.md`).

Override hooks (`*Переопределяемый`, region `ПрограммныйИнтерфейс`): БСП
calls them, application code implements them in its own override module - DO NOT call
them directly:

- `ПодключаемыеКомандыПереопределяемый.ПриОпределенииВидовПодключаемыхКоманд(ВидыПодключаемыхКоманд)`
  — add custom types of connected commands.
- `ПодключаемыеКомандыПереопределяемый.ПриОпределенииСоставаНастроекПодключаемыхОбъектов(НастройкиПрограммногоИнтерфейса)`
  — configure the set of objects with connected commands.
- `ПодключаемыеКомандыПереопределяемый.ПриОпределенииКомандПодключенныхКОбъекту(НастройкиФормы, Источники, ПодключенныеОтчетыИОбработки, Команды)`
  — implement arbitrary commands bound to a metadata object.

To search for signatures/regions of any method —
`python .claude/skills/bsp/scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`. 