# Plug-in Commands and Additional Reports/Processors

The **ПодключаемыеКоманды** and **ДополнительныеОтчетыИОбработки** subsystems are infrastructure
for placing "plug-in" commands in forms (printing, reports, filling, "create based on",
message templates) and for connecting external reports/processors (epf/erf) through the
`ДополнительныеОтчетыИОбработки` catalog. Use this when you need to add a command to an
object form, where the source is an external processor or another plug-in object rather
than the form's own code.

## Modules

The **ПодключаемыеКоманды** family (suffix logic):

- `ПодключаемыеКоманды` - server: placing commands in a form, executing server commands
  (`ПриСозданииНаСервере`, `ВыполнитьКоманду`, `ДобавитьУсловиеВидимостиКоманды`).
- `ПодключаемыеКомандыКлиент` - client: click/save handlers
  (`НачатьВыполнениеКоманды`, `ВыполнитьКоманду`, `ПослеЗаписи`, `НачатьОбновлениеКоманд`).
- `ПодключаемыеКомандыКлиентСервер` - common: `ОбновитьКоманды` (stable);
  `УсловияВыполняются`, `ПараметрыВыполненияКоманды`, `ВладелецКомандыПоИмениКоманды` -
  ⚠️ internal.
- `ПодключаемыеКомандыВызовСервера` - server call from the client without form context.
- `ПодключаемыеКомандыПереопределяемый` - **hooks**: called by БСП, implemented by
  application code (`ПриОпределенииВидовПодключаемыхКоманд`,
  `ПриОпределенииКомандПодключенныхКОбъекту`). It is NOT called from application code.

The **ДополнительныеОтчетыИОбработки** family:

- `ДополнительныеОтчетыИОбработки` - server: `ПодключитьВнешнююОбработку`,
  `ОбъектВнешнейОбработки`, `ВыполнитьКоманду`, `СведенияОВнешнейОбработке`,
  `СохранитьНастройки`, `ЗагрузитьНастройки` (stable).
- `ДополнительныеОтчетыИОбработкиКлиент` - client: `ОткрытьФормуКоманд…`,
  `ОткрытьВариантДополнительногоОтчета`, `ВыполнитьКомандуВФоне`,
  `ПараметрыВыполненияКомандыВФоне` (stable).
- `ДополнительныеОтчетыИОбработкиКлиентСервер` - common: constants for command types
  and kinds (`ВидОбработкиЗаполнениеОбъекта()`, `ТипКомандыВызовСерверногоМетода()` …).
- ⚠️ `ДополнительныеОтчетыИОбработкиКлиент.ОткрытьСписокДополнительныхОтчетовИОбработок`
  and `ДополнительныеОтчетыИОбработки.ИспользуютсяДополнительныеОтчетыИОбработки` -
  the `СлужебныйПрограммныйИнтерфейс` region; backward compatibility is not guaranteed.

The **СозданиеНаОсновании** family:

- `СозданиеНаОсновании` - server: `ДобавитьКомандуСозданияНаОсновании` (stable).
  `ПриОпределенииКомандПодключенныхКОбъекту` here is ⚠️ internal (internal to БСП);
  application-level override of composition is done through
  `ПодключаемыеКомандыПереопределяемый`.
- `СозданиеНаОснованииПереопределяемый` - hook for registering objects with creation-based
  commands.

> ⚠️ There are no modules `ПодключаемыеКомандыСервер`, `ДополнительныеОтчетыИОбработкиСервер`
> - a common mistake by analogy. The server module always has no `Сервер` suffix.

## Scenarios

### 1. Place plug-in commands in an object form

**Task:** in a document/catalog form, automatically build the "Print", "Reports",
"Fill", "Create based on" submenus from all registered sources.

**Functions:**
`ПодключаемыеКоманды.ПриСозданииНаСервере(Форма, Знач ПараметрыРазмещения = Неопределено) Экспорт`
`ПодключаемыеКомандыКлиент.НачатьВыполнениеКоманды(Форма, Команда, Знач Источник = Неопределено) Экспорт`
`ПодключаемыеКомандыКлиент.ПослеЗаписи(Форма, Объект, ПараметрыЗаписи) Экспорт`
`ПодключаемыеКомандыКлиент.НачатьОбновлениеКоманд(Форма) Экспорт`
— all Procedures, region `#Область ПрограммныйИнтерфейс` (stable). The first is Server;
the others are Client.

**Parameters:**
- `Форма` (УправляемаяФорма) - the object form; in `ПриСозданииНаСервере` this is
  `ЭтотОбъект`.
- `ПараметрыРазмещения` (Структура / Неопределено) - fine-tuning of the submenu
  (sources, command panel, group prefix). Default is `Неопределено` - standard placement.
  The structure is returned by `ПодключаемыеКоманды.ПараметрыРазмещения()`.
- `Команда` (КомандаФормы) - a command of the plug-in submenu.
- `Источник` (Произвольный) - the object/list the command is bound to
  (`Элементы.Список`, `Форма.Объект`).

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    // ...свой код инициализации...
    ПодключаемыеКоманды.ПриСозданииНаСервере(ЭтотОбъект);
КонецПроцедуры

&НаКлиенте
Процедура ПодключаемаяКоманда(Команда)
    // НачатьВыполнениеКоманды сам проверит ТребуетсяЗапись/ТребуетсяПроведение,
    // спросит пользователя и только потом вызовет сервер.
    ПодключаемыеКомандыКлиент.НачатьВыполнениеКоманды(ЭтотОбъект, Команда, Элементы.Список);
КонецПроцедуры

&НаКлиенте
Процедура ПослеЗаписи(ПараметрыЗаписи)
    ПодключаемыеКомандыКлиент.ПослеЗаписи(ЭтотОбъект, Объект, ПараметрыЗаписи);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Forgetting `ПриСозданииНаСервере` - the submenus will not appear, and all БСП
  infrastructure for the form is disabled. Always call it after your own initialization
  code.
- ❌ Calling `ПодключаемыеКоманды.ВыполнитьКоманду` (server-side) directly from a client
  `&НаКлиенте` handler - the method is unavailable on the client. From the client use
  `ПодключаемыеКомандыКлиент.НачатьВыполнениеКоманды` (it goes to the server itself).
- ❌ Duplicating a plug-in command with an ordinary form button "outside" БСП - the command
  will not appear in the shared list and will not be taken into account in "Current tasks",
  roles, or visibility.
- `НачатьВыполнениеКоманды` builds the "Save? Post?" dialog and only then goes to the
  server - do not call server `ВыполнитьКоманду` "directly" from the client without
  checking that the object has been saved.

### 2. Set a command visibility condition by attribute value

**Task:** show a print/fill command only for posted documents, or only when an attribute
has a value.

**Function:**
`ПодключаемыеКоманды.ДобавитьУсловиеВидимостиКоманды(Команда, Реквизит, Значение = Неопределено, Знач ВидСравнения = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Команда` (СтрокаТаблицыКоманд) - a command list row (from `КомандыПечати.Добавить()`
  or from `Команды` in `ПриОпределенииКомандПодключенныхКОбъекту`).
- `Реквизит` (Строка) - the object attribute name, for example `"Проведен"`,
  `"Контрагент"`.
- `Значение` (Произвольный) - the value to compare against; `Неопределено` for
  `Заполнено`/`НеЗаполнено`.
- `ВидСравнения` (ВидСравненияКомпоновкиДанных) - `Равно`, `НеРавно`, `Заполнено`,
  `НеЗаполнено`, `ВСписке`, `НеВСписке`, `Больше`, `Меньше`, `БольшеИлиРавно`,
  `МеньшеИлиРавно`. Default is `Неопределено` -> `Равно`.

**Example:**
```bsl
// В модуле менеджера объекта, в ДобавитьКомандыПечати (или в
// ПодключаемыеКомандыПереопределяемый.ПриОпределенииКомандПодключенныхКОбъекту):
КомандаПечати = КомандыПечати.Добавить();
КомандаПечати.Идентификатор = "Акт";
КомандаПечати.Представление = НСтр("ru = 'Акт выполненных работ'");

// Показывать только для проведённых документов
ПодключаемыеКоманды.ДобавитьУсловиеВидимостиКоманды(
    КомандаПечати, "Проведен", Истина, ВидСравненияКомпоновкиДанных.Равно);
```

**Nuances / anti-patterns:**
- ❌ Implement visibility "manually" in an attribute `ПриИзменении` handler by toggling
  `Элементы.КомандаПечать.Видимость` - this gets out of sync with the БСП infrastructure
  and breaks command refresh after saving. Use only `ДобавитьУсловиеВидимостиКоманды`.
- The conditions are evaluated during `ПодключаемыеКомандыКлиентСервер.ОбновитьКоманды`
  (called by БСП after saving and when the context changes).
- For print commands there is a parallel method
  `УправлениеПечатью.ДобавитьУсловиеВидимостиКоманды(КомандаПечати, Реквизит, Значение, Знач МетодСравнения = Неопределено)`
  - the 4th parameter is called `МетодСравнения`, not `ВидСравнения`. See
  `print-reports.md`.

### 3. Declare "Create based on" commands for an object

**Task:** in the "Создать на основании" submenu of a document form, show commands for
creating other documents based on the current one.

**Function:**
`СозданиеНаОсновании.ДобавитьКомандуСозданияНаОсновании(КомандыСозданияНаОсновании, ОбъектМетаданных) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `КомандыСозданияНаОсновании` (Массив) - the collection of creation commands, filled in
  the `ДобавитьКомандыСозданияНаОсновании(Команды, Объект, Параметры)` handler of the
  object manager module.
- `ОбъектМетаданных` (ОбъектМетаданных) - metadata of the target object, for example
  `Метаданные.Документы.СчетФактураВыданный`.

**Example:**
```bsl
// Модуль менеджера документа-источника
Процедура ДобавитьКомандыСозданияНаОсновании(Команды, Объект, Параметры) Экспорт
    СозданиеНаОсновании.ДобавитьКомандуСозданияНаОсновании(Команды, Метаданные.Документы.СчетФактураВыданный);
    СозданиеНаОсновании.ДобавитьКомандуСозданияНаОсновании(Команды, Метаданные.Документы.ПлатежноеПоручение);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Calling `СозданиеНаОсновании.ПриОпределенииКомандПодключенныхКОбъекту` - this is an
  ⚠️ internal (`СлужебныйПрограммныйИнтерфейс`) method of БСП. Application-level
  override of the composition belongs in
  `ПодключаемыеКомандыПереопределяемый.ПриОпределенииКомандПодключенныхКОбъекту`.
- Registering the object itself as a subsystem participant belongs in
  `СозданиеНаОснованииПереопределяемый.ПриОпределенииОбъектовСКомандамиСозданияНаОсновании`
  (an override hook implemented in the application configuration).
- Creating an object **programmatically** (`Документы.Х.СоздатьДокумент()`) is a platform
  API; the БСП plug-in command infrastructure has nothing to do with it.

### 4. Register an external processor (describe registration parameters)

**Task:** in an external processor (epf/erf), describe the kind, purpose, commands, and
safe mode so that БСП loads it correctly into the `ДополнительныеОтчетыИОбработки`
catalog.

**Function:**
`ДополнительныеОтчетыИОбработки.СведенияОВнешнейОбработке(ВерсияБСП = "") Экспорт`
— Function -> Structure `ПараметрыРегистрации`, region `#Область ПрограммныйИнтерфейс`
(stable). Server.

**Parameters:**
- `ВерсияБСП` (Строка) - the БСП version for which the template is generated
  (affects the set of fields). Default `""` - current.

The returned structure contains fields: `Вид`, `Назначение` (an array of full names of
owner metadata), `Наименование`, `Информация`, `БезопасныйРежим` (Булево), `Команды`
(a table with columns `Идентификатор`, `Представление`, `Использование`,
`ПоказыватьОповещение`, …). Command kinds and types are taken from
`ДополнительныеОтчетыИОбработкиКлиентСервер.ВидОбработки…()` /
`ТипКоманды…()` (all stable).

**Example:**
```bsl
// Модуль объекта внешней обработки
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

**Nuances / anti-patterns:**
- ❌ Store the external processor in a configuration template and connect it through
  `ПолучитьМакетОбработки`/`ОткрытьЗначение` - bypassing the catalog breaks loading/
  updating without configuration updates, safe mode, permissions, the data separator in the
  service model, and scheduled jobs. Use only the `ДополнительныеОтчетыИОбработки` catalog
  (loaded through "Administration -> Print forms, reports and processors").
- `БезопасныйРежим = Истина` blocks external connections, the file system, and COM for
  the external processor - set it to `Истина` if the processor does not need dangerous
  actions.
- Available kinds: `ВидОбработкиПечатнаяФорма()`, `…ЗаполнениеОбъекта()`,
  `…СозданиеСвязанныхОбъектов()`, `…Отчет()`, `…ШаблонСообщения()`,
  `…ДополнительнаяОбработка()`, `…ДополнительныйОтчет()`. Command types:
  `ТипКомандыВызовСерверногоМетода()`, `…ВызовКлиентскогоМетода()`, `…ОткрытиеФормы()`,
  `…ЗаполнениеФормы()`, `…ЗагрузкаДанныхИзФайла()`.

### 5. Execute an external processor command programmatically (from a scheduled job)

**Task:** start a command of an assigned external processor (for example, "Object
filling") from a scheduled job or server code, bypassing the form.

**Function:**
`ДополнительныеОтчетыИОбработки.ВыполнитьКоманду(ПараметрыКоманды, АдресРезультата = Неопределено) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ПараметрыКоманды` (Структура) - required keys:
  - `ДополнительнаяОбработкаСсылка` (СправочникСсылка.ДополнительныеОтчетыИОбработки) -
    link to the registered processor;
  - `ИдентификаторКоманды` (Строка) - command identifier from `ПараметрыРегистрации.Команды`;
  - `ОбъектыНазначения` (Массив) - target object references; required for assigned
    processors (kinds "Object filling", "Create related objects").
- `АдресРезультата` (Строка) - temporary storage address for the result; default
  `Неопределено`.

**Example:**
```bsl
// Модуль регламентного задания
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
- ❌ Use a "homegrown" processor registry (information register) instead of the
  `ДополнительныеОтчетыИОбработки` catalog - you lose safe mode, separators, scheduled
  jobs, and the `УправлениеВнешнимиОбработками` interceptor.
- `ОбъектыНазначения` is required for assigned processors; for global ones
  (kind "Дополнительная обработка") - it is not passed.
- Before running, you can check `ДополнительныеОтчетыИОбработки.ИспользуютсяДополнительныеОтчетыИОбработки()`
  - ⚠️ the method is internal (`СлужебныйПрограммныйИнтерфейс`), backward compatibility is
  not guaranteed; in new code it is preferable to check whether the reference exists via
  `Справочники.ДополнительныеОтчетыИОбработки.НайтиПоНаименованию(...).Пустая()`.

### 6. Connect an external processor and get its object

**Task:** programmatically connect an epf/erf from the catalog and call its methods
through the object (for example, in server-side printing from an external source).

**Functions:**
`ДополнительныеОтчетыИОбработки.ПодключитьВнешнююОбработку(Ссылка) Экспорт` — Function -> String (name of the connected processor), region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ДополнительныеОтчетыИОбработки.ОбъектВнешнейОбработки(Ссылка) Экспорт` — Function -> Object (instance of the external processor/report), region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Ссылка` (СправочникСсылка.ДополнительныеОтчетыИОбработки) - catalog entry.

**Example:**
```bsl
ИмяОбработки = ДополнительныеОтчетыИОбработки.ПодключитьВнешнююОбработку(Ссылка);
ВнешнийОбъект = ДополнительныеОтчетыИОбработки.ОбъектВнешнейОбработки(Ссылка);
// Дальше — вызов экспортных методов внешнего объекта:
ВнешнийОбъект.МояЭкспортнаяПроцедура(Параметры);
```

**Nuances / anti-patterns:**
- ❌ Connect an epf through the platform `ВнешниеОбработки.Подключить(...)` bypassing
  БСП - you lose safe mode, permissions, and separators. Use only
  `ДополнительныеОтчетыИОбработки.ПодключитьВнешнююОбработку`.
- `ОбъектВнешнейОбработки` internally connects the processor itself when needed; an
  explicit `ПодключитьВнешнююОбработку` is needed when the connection name is required.

### 7. Save and load external processor settings

**Task:** save arbitrary processor settings (fill parameters, report settings) between
runs and read them on the next call.

**Functions:**
`ДополнительныеОтчетыИОбработки.СохранитьНастройки(Ссылка, Настройки) Экспорт` — Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ДополнительныеОтчетыИОбработки.ЗагрузитьНастройки(Ссылка) Экспорт` — Function -> Arbitrary (saved settings), region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Ссылка` (СправочникСсылка.ДополнительныеОтчетыИОбработки) - catalog entry.
- `Настройки` (Произвольный) - a serializable value (Structure, ValueTable, etc.).

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

**Nuances / anti-patterns:**
- ❌ Store processor settings in a separate information register "by your own key" -
  you lose the binding to the data separator and service model. Use the built-in
  `СохранитьНастройки`/`ЗагрузитьНастройки` (storage is tied to the processor reference).
- `ЗагрузитьНастройки` returns `Неопределено` if the settings have not been saved
  previously - check before using dot notation.

### 8. Open a command form for processors and run a command in the background

**Task:** from client code, open the command selection form for additional reports/
processors of a selected kind; run a long-running processor command in the background
with a completion handler.

**Functions:**
`ДополнительныеОтчетыИОбработкиКлиент.ОткрытьФормуКомандДополнительныхОтчетовИОбработок(ПараметрКоманды, ПараметрыВыполненияКоманды, Вид, ИмяРаздела = "") Экспорт` — Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.
`ДополнительныеОтчетыИОбработкиКлиент.ВыполнитьКомандуВФоне(Знач ИдентификаторКоманды, Знач ПараметрыКоманды, Знач Обработчик) Экспорт` — Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.
`ДополнительныеОтчетыИОбработкиКлиент.ПараметрыВыполненияКомандыВФоне(Ссылка) Экспорт` — Function -> Structure, region `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `ПараметрКоманды` (Произвольный) - the object/reference for which the command list is
  opened (for example, the form `Объект`).
- `ПараметрыВыполненияКоманды` (Структура) - execution parameters; see
  `ПодключаемыеКомандыКлиент.ПараметрыВыполненияКоманды()`.
- `Вид` (Строка) - the processor kind, for example the result of
  `ДополнительныеОтчетыИОбработкиКлиентСервер.ВидОбработкиЗаполнениеОбъекта()`.
- `ИмяРаздела` (Строка) - navigation section for placement; default `""`.
- `ИдентификаторКоманды` (Строка) - command identifier from `ПараметрыРегистрации.Команды`.
- `ПараметрыКоманды` (Структура) - keys `ДополнительнаяОбработкаСсылка`,
  `ОбъектыНазначения` (see scenario 5).
- `Обработчик` (ОписаниеОповещения) - completion handler for the background command.

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
- ❌ Open the form of an external report via `ОткрытьЗначение(Ссылка)` - this bypasses
  the variants subsystem. A supplementary report is opened through
  `ДополнительныеОтчетыИОбработкиКлиент.ОткрытьВариантДополнительногоОтчета(ДополнительныйОтчет, КлючВарианта)`.
- `ОткрытьСписокДополнительныхОтчетовИОбработок()` - ⚠️ internal (region
  `СлужебныйПрограммныйИнтерфейс`); acceptable for admin UI, but backward compatibility
  is not guaranteed.

## Rare Methods

Structure constructors and helper methods (all stable, region
`ПрограммныйИнтерфейс`, unless stated otherwise):

- `ПодключаемыеКоманды.ПараметрыРазмещения() Экспорт` - Function -> Structure
  (`Источники`, `КоманднаяПанель`, `ПрефиксГрупп`, `ВладелецКоманд`). Passed as the second
  parameter in `ПриСозданииНаСервере` for non-standard submenu placement.
- `ПодключаемыеКоманды.ПараметрыВыполненияКоманды() Экспорт` and
  `ПодключаемыеКомандыКлиент.ПараметрыВыполненияКоманды() Экспорт` - Functions ->
  execution-parameter Structure (`ОписаниеКоманды`, `Форма`, `ЭтоФормаОбъекта`,
  `Источник`). Server and client variants respectively.
- `ПодключаемыеКомандыКлиентСервер.ОбновитьКоманды(Форма, Знач Источник = Неопределено) Экспорт`
  - Procedure (client + server): recalculate visibility / availability / marks of form
  commands. Used after programmatic context changes.
- `ДополнительныеОтчетыИОбработки.ВыполнитьКомандуИзФормыВнешнегоОбъекта(ИдентификаторКоманды, ПараметрыКоманды, Форма) Экспорт`
  - Function (server): start a command from the form module of the external processor
  itself.
- `ДополнительныеОтчетыИОбработки.ПечатьПоВнешнемуИсточнику(ДополнительнаяОбработкаСсылка, ПараметрыИсточника, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода) Экспорт`
  - Procedure (server): link an external processor print form with the print manager
  (called by the print infrastructure, see `print-reports.md`).

Override hooks (`*Переопределяемый`, region `ПрограммныйИнтерфейс`): БСП calls them,
application code implements them in its override module - do NOT call them directly:

- `ПодключаемыеКомандыПереопределяемый.ПриОпределенииВидовПодключаемыхКоманд(ВидыПодключаемыхКоманд)`
  - add custom kinds of plug-in commands.
- `ПодключаемыеКомандыПереопределяемый.ПриОпределенииСоставаНастроекПодключаемыхОбъектов(НастройкиПрограммногоИнтерфейса)`
  - configure the composition of objects with plug-in commands.
- `ПодключаемыеКомандыПереопределяемый.ПриОпределенииКомандПодключенныхКОбъекту(НастройкиФормы, Источники, ПодключенныеОтчетыИОбработки, Команды)`
  - implement arbitrary commands bound to a metadata object.

To search signatures/regions of any method -
`python .claude/skills/bsp/scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`.
