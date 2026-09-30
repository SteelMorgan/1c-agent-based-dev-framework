# Information Base Backup

Subsystem **РезервноеКопированиеИБ** consists of the common modules `РезервноеКопированиеИБКлиент`
(client stable API - opening the form), `РезервноеКопированиеИБСервер`
(server-side, ⚠️ entirely in service regions - reading/writing settings, status,
navigation link), `РезервноеКопированиеИБВызовСервера` (⚠️ service -
server calls from the client), `РезервноеКопированиеИБКлиентПереопределяемый`
(warning suppression hook). It covers opening the backup form,
programmatic work with settings, calculating the next backup date, resetting
the flag after restore.

The subsystem supports **file-only** IB; in the client-server
variant, automatic backup methods return `Ложь`, and the UI form is
useless - use DBMS tools and the flag
`РезервноеКопированиеНастроено = Истина` without
`ВыполнятьАвтоматическоеРезервноеКопирование`. Uploading to cloud storage
(Google Drive, Яндекс.Диск) is not provided by this API version - it uses a
local file system directory (`КаталогХраненияРезервныхКопий`).

## Modules

- `РезервноеКопированиеИБКлиент` - the **only stable** client-side
  method: `ОткрытьФормуРезервногоКопирования`. The remaining exported methods of the
  module are subsystem event handlers (`ПриНачалеРаботыСистемы`,
  `ПередЗавершениемРаботыСистемы`, `ПриПредложенииПользователюСоздатьРезервнуюКопию`)
  in `СлужебныйПрограммныйИнтерфейс` - called by БСП, not from application code.
  Thin / Thick client.
- `РезервноеКопированиеИБСервер` - ⚠️ **entirely service-side**: in
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
- `РезервноеКопированиеИБГлобальный` - ⚠️ **service**: one exported method
  `ОбработчикДействийРезервногоКопирования()` (region `СлужебныеПроцедурыИФункции`).
- `РезервноеКопированиеОбластейДанных` / `…Клиент` - service model modules
  (SaaS) with their own API for managing backups in data areas: 15 exported methods in `ПрограммныйИнтерфейс`
  (`РезервноеКопированиеИспользуется()`,
  `УстановитьФлагАктивностиПользователяВОбласти()`,
  `МенеджерСервисаПоддерживаетРезервноеКопирование()` and others), `…Клиент` - 1
  exported method. They are not used in a local (non-SaaS) IB.

⚠️ There are **no** modules `РезервноеКопированиеИБСлужебный`,
`РезервноеКопированиеИБПереопределяемый` (without `Клиент`),
`РезервноеКопированиеИБКлиентСервер`. The service logic is built into the main
modules, and overriding is client-side only. Before calling, check the
actual common modules directory.

## Scenarios

### 1. Open the backup form with file-based variant check

**Task:** from an application command, open the form for creating/restoring a backup copy, first making sure that the infobase is file-based.

**Function:**
`РезервноеКопированиеИБКлиент.ОткрытьФормуРезервногоКопирования(Параметры = Неопределено) Экспорт`
— Procedure, region: `#Область ПрограммныйИнтерфейс` (stable). Thin / Thick client.

**Parameters:**
- `Параметры` (Structure / Undefined) — form parameters. To start on exit: `New Structure("РежимРаботы", "ВыполнитьПриЗавершенииРаботы")`.

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
- ❌ Open the form without checking `ИнформационнаяБазаФайловая()` — in the client-server variant, the form will open “empty”.
- `ОткрытьФормуРезервногоКопирования` is the **only** stable client entry point. Do not invent `ОткрытьФормуНастроекКопирования` — the settings form is opened by the subsystem UI command or a navigation link (scenario 3).

### 2. Read and save copy settings

**Task:** from server-side processing, initialize the schedule, directory, retention period, and automatic copy flags; correctly overwrite the settings without losing default values.

**Functions:**
`РезервноеКопированиеИБСервер.ПараметрыРезервногоКопирования() Экспорт` — Function → Structure (see `НовыеНастройкиРезервногоКопирования`).
`РезервноеКопированиеИБСервер.УстановитьНастройкиРезервногоКопирования(Знач Настройки, Знач Пользователь = Неопределено) Экспорт` — Procedure.
— ⚠️ **service** (`СлужебныеПроцедурыИФункции` / `СлужебныйПрограммныйИнтерфейс`);
backward compatibility is not guaranteed. Server, External connection.

**Parameters:**
- `Настройки` (Structure, see `НовыеНастройкиРезервногоКопирования`) — full set of fields: `ВыполнятьАвтоматическоеРезервноеКопирование` (Boolean),
  `РезервноеКопированиеНастроено` (Boolean),
  `РасписаниеКопирования` (scheduled job schedule structure),
  `КаталогХраненияРезервныхКопий` (String),
  `КаталогХраненияРезервныхКопийПриРучномЗапуске` (String),
  `ВариантВыполнения` (`"ПоРасписанию"` / `"ПриЗавершенииРаботы"`),
  `АдминистраторИБ` (String), `ПарольАдминистратораИБ` (String),
  `ПараметрыУдаления` (Structure: `ТипОграничения`, `КоличествоКопий`,
  `ЕдиницаИзмеренияПериода`, `ЗначениеВЕдиницахИзмерения`),
  `ДатаПоследнегоРезервногоКопирования`, `МинимальнаяДатаСледующегоАвтоматическогоРезервногоКопирования`,
  `РучнойЗапускПоследнегоРезервногоКопирования` and others.
- `Пользователь` (UserRef / Undefined) — if specified, the settings are additionally saved to the `ПараметрыРезервногоКопирования` constant for transfer to a background session on exit.

**Example:**
```bsl
// Server: always read the current settings, then change the required fields
Настройки = РезервноеКопированиеИБСервер.ПараметрыРезервногоКопирования();
Настройки.ВыполнятьАвтоматическоеРезервноеКопирование = Истина;
Настройки.ВариантВыполнения = "ПоРасписанию";
Настройки.РасписаниеКопирования = ОбщегоНазначенияКлиентСервер.РасписаниеВСтруктуру(НовоеРасписание);
Настройки.КаталогХраненияРезервныхКопий = "D:\Backups";
Настройки.РезервноеКопированиеНастроено = Истина;
РезервноеКопированиеИБСервер.УстановитьНастройкиРезервногоКопирования(Настройки);
```

**Nuances / antipatterns:**
- ❌ Building the settings structure “from scratch” with `Новый Структура` —
  `УстановитьНастройкиРезервногоКопирования` expects the **full** set of fields, and
  default values and service fields (`ДатаПоследнегоРезервногоКопирования`,
  `МинимальнаяДатаСледующегоАвтоматическогоРезервногоКопирования`, etc.) will be lost. Only
  `ПараметрыРезервногоКопирования()` → field edit → write.
- ❌ Programmatically starting backup through `ЗавершитьРезервноеКопирование(Результат, ИмяФайлаРезервнойКопии = "")` — this is a service **completion** method
  (processing the result after execution), not a launch method. Backup is
  initiated through the form (`ОткрытьФормуРезервногоКопирования` with
  `РежимРаботы = "ВыполнитьПриЗавершенииРаботы"`) or a scheduled job.
- Backup to the cloud (Google Drive, Yandex.Disk) is not
  provided in this API version — only a local/network FS directory.

### 3. Show the status and navigation link in the card

**Task:** get a ready localized string-formulation of the current
backup mode and a navigation link to the processing for insertion into a
formatted string / email / notification.

**Functions:**
`РезервноеКопированиеИБСервер.ТекущаяНастройкаРезервногоКопирования() Экспорт` — Function → String.
`РезервноеКопированиеИБСервер.НавигационнаяСсылкаОбработкиРезервногоКопирования() Экспорт` — Function → String (`e1cib/app/Обработка.РезервноеКопированиеИБ`).
— ⚠️ **service** (`СлужебныйПрограммныйИнтерфейс`). Server, External connection.

**Parameters:** none.

**Example:**
```bsl
// Сервер
ТекстСтатуса = РезервноеКопированиеИБСервер.ТекущаяНастройкаРезервногоКопирования();
НавигационнаяСсылка = РезервноеКопированиеИБСервер.НавигационнаяСсылкаОбработкиРезервногоКопирования();

// В форматированной строке / поле HTML
СтрокаHTML = "<a href='" + НавигационнаяСсылка + "'>" + ТекстСтатуса + "</a>";
```

**Nuances / antipatterns:**
- `ТекущаяНастройкаРезервногоКопирования` takes the IB variant into account: in
  client-server it will return "Backup is not performed (handled by the DBMS)"; if settings are absent — "To configure backup, contact the administrator.".
  Do not compose such text yourself.
- `НавигационнаяСсылкаОбработкиРезервногоКопирования` returns a link to the
  **main** processing `РезервноеКопированиеИБ` (form `РезервноеКопированиеДанных`),
  not to the settings form.

### 4. Calculate the next backup date and update one settings field

**Task:** in the client settings form, show the date of the next
automatic backup and save a change to a single field without
re-reading the entire structure (from the form item change handler).

**Functions:**
`РезервноеКопированиеИБВызовСервера.ДатаСледующегоАвтоматическогоКопирования(ОтложитьРезервноеКопирование = Ложь) Экспорт` — Function → Date.
`РезервноеКопированиеИБВызовСервера.УстановитьЗначениеНастройки(ИмяЭлемента, ЗначениеЭлемента) Экспорт` — Procedure.
— ⚠️ **service** (`СлужебныеПроцедурыИФункции`). Server call from the client
(Thin/Fat client → Server).

**Parameters:**
- `ОтложитьРезервноеКопирование` (Boolean) — `Истина` shifts the minimum date
  15 minutes forward (for the "Delay" button).
- `ИмяЭлемента` (String) — the name of the settings field, e.g. `"КаталогХраненияРезервныхКопий"`.
- `ЗначениеЭлемента` (Any) — the new value of the field.

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
    // Save one field without re-reading the entire settings structure
    РезервноеКопированиеИБВызовСервера.УстановитьЗначениеНастройки(
        "КаталогХраненияРезервныхКопий", Текст);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ⚠️ `УстановитьЗначениеНастройки` and `УстановитьДатуПоследнегоНапоминания`
  are defined in **two** modules — `…ВызовСервера` (client server call)
  and `…Сервер` (server context). From client code, use `ВызовСервера`; from
  server code, use `Сервер`. Verify via
  `python scripts/bsp_api.py method УстановитьЗначениеНастройки --src src/cf`.
- `ДатаСледующегоАвтоматическогоКопирования` only makes sense when
  `ВариантВыполнения = "ПоРасписанию"` and the file-based IB option is used.

### 5. Reset the copy flag after recovery

**Task:** after restoring the information base from a backup copy, reset the `ПроведеноКопирование` flag (so that БСП does not consider copying current) and, if the monitoring subsystem is present, record the operation.

**Function:**
`РезервноеКопированиеИБСервер.СброситьПризнакРезервногоКопирования() Экспорт`
— Procedure, region `#Область СлужебныеПроцедурыИФункции` (⚠️ service).
Server, External connection.

**Parameters:** none.

**Example:**
```bsl
// Сервер: после восстановления из копии
РезервноеКопированиеИБСервер.СброситьПризнакРезервногоКопирования();
```

**Nuances / anti-patterns:**
- ❌ Reset the flag “just in case” in a normal session — this is a service
  method for the recovery scenario. In normal operation, the flag is set by
  the copying mechanism itself.
- The method also writes the operation to the monitoring center if the
  monitoring subsystem is present — no separate call is needed.

### 6. Override: disable backup setup warnings

**Task:** in the company, backups are made by third-party tools, and БСП banners
about the need to configure copying get in the way - disable them via a hook.

**Function (hook):**
`РезервноеКопированиеИБКлиентПереопределяемый.ПриОпределенииНеобходимостиПоказаПредупрежденийОРезервномКопировании(ПоказыватьПредупреждение) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс`. **Hook**: БСП calls it,
application code implements it in the module with the same name in the application configuration.
Thin / Thick client.

**Parameters:**
- `ПоказыватьПредупреждение` (Boolean, output) — set to `Ложь` so that БСП
does not show warnings.

**Example:**
```bsl
// В модуле РезервноеКопированиеИБКлиентПереопределяемый прикладной конфигурации
Процедура ПриОпределенииНеобходимостиПоказаПредупрежденийОРезервномКопировании(ПоказыватьПредупреждение) Экспорт
    // Бэкапы делает внешняя система — не показываем предупреждения БСП
    ПоказыватьПредупреждение = Ложь;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Call the hook as a regular method `РезервноеКопированиеИБКлиентПереопределяемый.ПриОпределениеНеобходимостиПоказаПредупреждений…(Ложь)`
  from application code - this is **not called**, it is **implemented**. БСП itself
  will call your implementation.
- Do not confuse this with disabling the subsystem itself - the hook silences only the warnings,
  the scheduled job and the settings API continue to work.

## Additional

Other service methods (full signatures are available via
`python scripts/bsp_api.py method <Имя> --src src/cf`):

- `РезервноеКопированиеИБСервер.НастройкиРезервногоКопирования(НачалоРаботы = Ложь)` — ⚠️ service, a settings-reading variant with a work-start flag.
- `РезервноеКопированиеИБСервер.УстановитьДатуПоследнегоНапоминания(ДатаНапоминания)` — ⚠️ service; records the reminder date for the user (duplicated in `…ВызовСервера`).
- `РезервноеКопированиеИБСервер.ЗавершитьРезервноеКопирование(Результат, ИмяФайлаРезервнойКопии = "")` / `ЗавершитьВосстановление(Результат)` — ⚠️ service handlers for the execution **result**, not the launch.
- `РезервноеКопированиеИБСервер.ИнформацияОПользователе()` — ⚠️ service, user data for copying.

Subsystem processing:

- `Обработка.РезервноеКопированиеИБ` — the main form `РезервноеКопированиеДанных` (create/restore a copy). Opened via `ОткрытьФормуРезервногоКопирования`.
- `Обработка.НастройкаРезервногоКопированияИБ` — the settings form; opened by the subsystem UI command or a navigation link (scenario 3), there is no direct export method for opening it.

Settings storage: the `ПараметрыРезервногоКопирования` key in the shared settings storage + a duplicate constant `ПараметрыРезервногоКопирования` (type `ХранилищеЗначения`, compression 9) for passing parameters into a background session when finishing work. The `ПараметрыРезервногоКопирования` method returns a `Структура` (not a `ФиксированнаяСтруктура`) with a fixed set of fields — do not add your own fields, `УстановитьНастройкиРезервногоКопирования` will write only the original set.