# Information Base Backup

Subsystem **РезервноеКопированиеИБ** - common modules `РезервноеКопированиеИБКлиент`
(stable client API - opening the form), `РезервноеКопированиеИБСервер`
(server-side, ⚠️ entirely in service regions - reading/writing settings, status,
navigation link), `РезервноеКопированиеИБВызовСервера` (⚠️ service -
server calls from the client), `РезервноеКопированиеИБКлиентПереопределяемый`
(warning suppression hook). Covers opening the backup form,
programmatic work with settings, calculating the next backup date, resetting
the flag after restoration.

The subsystem supports **file-based only** information bases; in client-server
mode the automatic backup methods return `Ложь`, and the UI form is useless -
use DBMS tools and the `РезервноеКопированиеНастроено = Истина` flag without
`ВыполнятьАвтоматическоеРезервноеКопирование`. Uploading to cloud storage
(Google Drive, Yandex.Disk) is not provided in this API version - a local file
system directory is used (`КаталогХраненияРезервныхКопий`).

## Modules

- `РезервноеКопированиеИБКлиент` - **the only stable** client-side method:
  `ОткрытьФормуРезервногоКопирования`. The remaining exported methods of the
  module - subsystem event handlers (`ПриНачалеРаботыСистемы`,
  `ПередЗавершениемРаботыСистемы`, `ПриПредложенииПользователюСоздатьРезервнуюКопию`)
  in `СлужебныйПрограммныйИнтерфейс` - are called by BSP, not from application
  code. Thin / Thick client.
- `РезервноеКопированиеИБСервер` - ⚠️ **entirely service**: in
  `СлужебныйПрограммныйИнтерфейс` - `УстановитьНастройкиРезервногоКопирования`,
  `ТекущаяНастройкаРезервногоКопирования`,
  `НавигационнаяСсылкаОбработкиРезервногоКопирования`; in
  `СлужебныеПроцедурыИФункции` - `ПараметрыРезервногоКопирования`,
  `СброситьПризнакРезервногоКопирования`, `УстановитьДатуПоследнегоНапоминания`,
  `УстановитьЗначениеНастройки`, `ЗавершитьРезервноеКопирование`. Backward
  compatibility is not guaranteed.
- `РезервноеКопированиеИБВызовСервера` - ⚠️ service (server call from the
  client): `УстановитьЗначениеНастройки`, `ДатаСледующегоАвтоматическогоКопирования`,
  `УстановитьДатуПоследнегоНапоминания` (in `СлужебныеПроцедурыИФункции`).
- `РезервноеКопированиеИБКлиентПереопределяемый` - **hook**
  `ПриОпределенииНеобходимостиПоказаПредупрежденийОРезервномКопировании`.
- `РезервноеКопированиеОбластейДанных` / `…Клиент` - service model stubs
  (SaaS), empty in a local information base.

⚠️ **There are no** modules `РезервноеКопированиеИБСлужебный`,
`РезервноеКопированиеИБПереопределяемый` (without `Клиент`),
`РезервноеКопированиеИБКлиентСервер`. Service logic is built into the main
modules, and overriding is client-side only. Before calling, check the actual
shared modules directory.

## Scenarios

### 1. Open the backup form with file-based variant check

**Task:** from an application command, open the form for creating/restoring a
backup copy, first making sure that the information base is file-based.

**Function:**
`РезервноеКопированиеИБКлиент.ОткрытьФормуРезервногоКопирования(Параметры = Неопределено) Экспорт`
— Procedure, region: `#Область ПрограммныйИнтерфейс` (stable). Thin / Thick
client.

**Parameters:**
- `Параметры` (Structure / Undefined) - form parameters. To run when
  shutting down: `Новый Структура("РежимРаботы", "ВыполнитьПриЗавершенииРаботы")`.

**Example:**
```bsl
&НаКлиенте
Процедура СоздатьРезервнуюКопию(Команда)
    Если ОбщегоНазначенияКлиент.ИнформационнаяБазаФайловая() Тогда
        РезервноеКопированиеИБКлиент.ОткрытьФормуРезервногоКопирования();
    Иначе
        ПоказатьПредупреждение(,
            НСтр("ru = 'В клиент-серверном варианте резервное копирование выполняется средствами СУБД.'"));
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Open the form without checking `ИнформационнаяБазаФайловая()` - in the
  client-server variant the form will open "empty".
- `ОткрытьФормуРезервногоКопирования` is the **only** stable client entry
  point. Do not invent `ОткрытьФормуНастроекКопирования` - the settings form
  is opened by the subsystem UI command or navigation link (scenario 3).

### 2. Read and save backup settings

**Task:** from a server-side handler, initialize the schedule, directory,
retention period, and automatic backup flags; overwrite settings correctly
without losing default values.

**Functions:**
`РезервноеКопированиеИБСервер.ПараметрыРезервногоКопирования() Экспорт` - Function → Structure (see `НовыеНастройкиРезервногоКопирования`).
`РезервноеКопированиеИБСервер.УстановитьНастройкиРезервногоКопирования(Знач Настройки, Знач Пользователь = Неопределено) Экспорт` - Procedure.
— ⚠️ **service** (`СлужебныеПроцедурыИФункции` / `СлужебныйПрограммныйИнтерфейс`);
backward compatibility is not guaranteed. Server, External connection.

**Parameters:**
- `Настройки` (Structure, see `НовыеНастройкиРезервногоКопирования`) - full
  set of fields: `ВыполнятьАвтоматическоеРезервноеКопирование` (Boolean),
  `РезервноеКопированиеНастроено` (Boolean),
  `РасписаниеКопирования` (scheduled task schedule structure),
  `КаталогХраненияРезервныхКопий` (String),
  `КаталогХраненияРезервныхКопийПриРучномЗапуске` (String),
  `ВариантВыполнения` (`"ПоРасписанию"` / `"ПриЗавершенииРаботы"`),
  `АдминистраторИБ` (String), `ПарольАдминистратораИБ` (String),
  `ПараметрыУдаления` (Structure: `ТипОграничения`, `КоличествоКопий`,
  `ЕдиницаИзмеренияПериода`, `ЗначениеВЕдиницахИзмерения`),
  `ДатаПоследнегоРезервногоКопирования`, `МинимальнаяДатаСледующегоАвтоматическогоРезервногоКопирования`,
  `РучнойЗапускПоследнегоРезервногоКопирования` and others.
- `Пользователь` (UserRef / Undefined) - if specified, settings are additionally
  saved to the `ПараметрыРезервногоКопирования` constant for transfer to a
  background session when shutting down.

**Example:**
```bsl
// Server: always read the current settings first, then change the required fields
Настройки = РезервноеКопированиеИБСервер.ПараметрыРезервногоКопирования();
Настройки.ВыполнятьАвтоматическоеРезервноеКопирование = Истина;
Настройки.ВариантВыполнения = "ПоРасписанию";
Настройки.РасписаниеКопирования = ОбщегоНазначенияКлиентСервер.РасписаниеВСтруктуру(НовоеРасписание);
Настройки.КаталогХраненияРезервныхКопий = "D:\Backups";
Настройки.РезервноеКопированиеНастроено = Истина;
РезервноеКопированиеИБСервер.УстановитьНастройкиРезервногоКопирования(Настройки);
```

**Nuances / anti-patterns:**
- ❌ Build the settings structure "from scratch" with `Новый Структура` -
  `УстановитьНастройкиРезервногоКопирования` expects the **full** set of
  fields, and default values and service fields
  (`ДатаПоследнегоРезервногоКопирования`,
  `МинимальнаяДатаСледующегоАвтоматическогоРезервногоКопирования`, etc.) will
  be lost. Only `ПараметрыРезервногоКопирования()` → edit fields → write.
- ❌ Start backup programmatically through
  `ЗавершитьРезервноеКопирование(Результат, ИмяФайлаРезервнойКопии = "")` -
  this is the service **completion** method (processing the result after
  execution), not the start. Backup is initiated through the form
  (`ОткрытьФормуРезервногоКопирования` with
  `РежимРаботы = "ВыполнитьПриЗавершенииРаботы"`) or a scheduled job.
- Cloud backup (Google Drive, Yandex.Disk) is not provided in this API version -
  only a local/network FS directory.

### 3. Show the status and navigation link in a card

**Task:** get a ready localized text formulation of the current backup mode and
a navigation link to the handler for insertion into a formatted string / email /
notification.

**Functions:**
`РезервноеКопированиеИБСервер.ТекущаяНастройкаРезервногоКопирования() Экспорт` - Function → String.
`РезервноеКопированиеИБСервер.НавигационнаяСсылкаОбработкиРезервногоКопирования() Экспорт` - Function → String (`e1cib/app/Обработка.РезервноеКопированиеИБ`).
— ⚠️ **service** (`СлужебныйПрограммныйИнтерфейс`). Server, External connection.

**Parameters:** none.

**Example:**
```bsl
// Server
ТекстСтатуса = РезервноеКопированиеИБСервер.ТекущаяНастройкаРезервногоКопирования();
НавигационнаяСсылка = РезервноеКопированиеИБСервер.НавигационнаяСсылкаОбработкиРезервногоКопирования();

// In a formatted string / HTML field
СтрокаHTML = "<a href='" + НавигационнаяСсылка + "'>" + ТекстСтатуса + "</a>";
```

**Nuances / anti-patterns:**
- `ТекущаяНастройкаРезервногоКопирования` takes the information base variant
  into account: in the client-server variant it will return "Backup is not being
  performed (organized by DBMS tools)"; when no settings exist - "To configure
  backup, contact the administrator.". Do not construct such text yourself.
- `НавигационнаяСсылкаОбработкиРезервногоКопирования` returns a link to the
  **main** handler `РезервноеКопированиеИБ` (form `РезервноеКопированиеДанных`),
  not to the settings form.

### 4. Calculate the next backup date and change one settings field

**Task:** in the client settings form, show the date of the next automatic
backup and save a change to one field without rereading the whole structure
(from a form item change handler).

**Functions:**
`РезервноеКопированиеИБВызовСервера.ДатаСледующегоАвтоматическогоКопирования(ОтложитьРезервноеКопирование = Ложь) Экспорт` - Function → Date.
`РезервноеКопированиеИБВызовСервера.УстановитьЗначениеНастройки(ИмяЭлемента, ЗначениеЭлемента) Экспорт` - Procedure.
— ⚠️ **service** (`СлужебныеПроцедурыИФункции`). Server call from the client
(Thin/Thick client → Server).

**Parameters:**
- `ОтложитьРезервноеКопирование` (Boolean) - `Истина` shifts the minimum date
  forward by 1 hour (for the "Postpone" button).
- `ИмяЭлемента` (String) - name of the settings field, e.g.
  `"КаталогХраненияРезервныхКопий"`.
- `ЗначениеЭлемента` (Any) - new field value.

**Example:**
```bsl
&НаКлиенте
Процедура РасписаниеКопированияПриИзменении(Элемент)
    ДатаСледующего = РезервноеКопированиеИБВызовСервера.ДатаСледующегоАвтоматическогоКопирования();
    Элементы.ДатаСледующегоКопирования.Заголовок =
        НСтр("ru = 'Следующее копирование:'") + " "
        + Формат(ДатаСледующего, "ДФ='dd.MM.yyyy HH:mm'");
КонецПроцедуры

&НаКлиенте
Процедура КаталогХраненияПриОкончанииВводаТекста(Элемент, Текст, Отказ)
    // Save one field without rereading the whole settings structure
    РезервноеКопированиеИБВызовСервера.УстановитьЗначениеНастройки(
        "КаталогХраненияРезервныхКопий", Текст);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ⚠️ `УстановитьЗначениеНастройки` and `УстановитьДатуПоследнегоНапоминания`
  are defined in **two** modules - `…ВызовСервера` (client server call) and
  `…Сервер` (server context). From client code use `ВызовСервера`, from server
  code use `Сервер`. Check via
  `python scripts/bsp_api.py method УстановитьЗначениеНастройки --src src/cf`.
- `ДатаСледующегоАвтоматическогоКопирования` is meaningful only when
  `ВариантВыполнения = "ПоРасписанию"` and the information base is file-based.

### 5. Reset the backup flag after restoration

**Task:** after restoring the information base from a backup copy, reset the
`ПроведеноКопирование` flag so that BSP does not consider the backup current,
and record the operation if the monitoring subsystem is present.

**Function:**
`РезервноеКопированиеИБСервер.СброситьПризнакРезервногоКопирования() Экспорт`
— Procedure, region `#Область СлужебныеПроцедурыИФункции` (⚠️ service).
Server, External connection.

**Parameters:** none.

**Example:**
```bsl
// Server: after restore from a backup copy
РезервноеКопированиеИБСервер.СброситьПризнакРезервногоКопирования();
```

**Nuances / anti-patterns:**
- ❌ Reset the flag "just in case" in a normal session - this is a service
  method for the restore scenario. In normal operation the flag is set by the
  backup mechanism itself.
- The method also writes the operation to the monitoring center if the
  monitoring subsystem is present - no separate call is needed.

### 6. Override: disable backup setup warnings

**Task:** in the company, backups are made by third-party tools, and the BSP
warnings about the need to configure backup are in the way - disable them
through the hook.

**Function (hook):**
`РезервноеКопированиеИБКлиентПереопределяемый.ПриОпределенииНеобходимостиПоказаПредупрежденийОРезервномКопировании(ПоказыватьПредупреждение) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс`. **Hook**: BSP calls it,
application code implements it in the identically named module of the
application configuration. Thin / Thick client.

**Parameters:**
- `ПоказыватьПредупреждение` (Boolean, output) - set to `Ложь` so that BSP does
  not show warnings.

**Example:**
```bsl
// In the module РезервноеКопированиеИБКлиентПереопределяемый of the application configuration
Процедура ПриОпределениеНеобходимостиПоказаПредупрежденийОРезервномКопировании(ПоказыватьПредупреждение) Экспорт
    // Backups are made by an external system - do not show BSP warnings
    ПоказыватьПредупреждение = Ложь;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Call the hook like a regular method `РезервноеКопированиеИБКлиентПереопределяемый.ПриОпределениеНеобходимостиПоказаПредупреждений…(Ложь)`
  from application code - this is **not called**, it is **implemented**. BSP
  will call your implementation itself.
- Do not confuse this with disabling the subsystem itself - the hook only
  suppresses warnings, while the scheduled job and settings API continue to
  work.

## Additional

Other service methods (full signatures - via
`python scripts/bsp_api.py method <Имя> --src src/cf`):

- `РезервноеКопированиеИБСервер.НастройкиРезервногоКопирования(НачалоРаботы = Ложь)` - ⚠️ service, a settings read variant with a start-of-work flag.
- `РезервноеКопированиеИБСервер.УстановитьДатуПоследнегоНапоминания(ДатаНапоминания)` - ⚠️ service; record the reminder date for the user (duplicated in `…ВызовСервера`).
- `РезервноеКопированиеИБСервер.ЗавершитьРезервноеКопирование(Результат, ИмяФайлаРезервнойКопии = "")` / `ЗавершитьВосстановление(Результат)` - ⚠️ service handlers for the **result** of execution, not the start.
- `РезервноеКопированиеИБСервер.ИнформацияОПользователе()` - ⚠️ service, user data for backup.

Subsystem handlers:

- `Обработка.РезервноеКопированиеИБ` - main form `РезервноеКопированиеДанных` (create/restore backup). Opened via `ОткрытьФормуРезервногоКопирования`.
- `Обработка.НастройкаРезервногоКопированияИБ` - settings form; opened by the subsystem UI command or navigation link (scenario 3), no direct exported open method exists.

Settings storage: key `ПараметрыРезервногоКопирования` in the shared
settings store + a duplicate constant `ПараметрыРезервногоКопирования` (type
`ХранилищеЗначения`, compression 9) for passing parameters to a background
session when shutting down. The `ПараметрыРезервногоКопирования` method returns
a `Structure` (not `FixedStructure`) with a fixed set of fields - do not add
your own fields, `УстановитьНастройкиРезервногоКопирования` will write only the
original set.
