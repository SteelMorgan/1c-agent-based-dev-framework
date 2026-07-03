# IB Backup

Subsystem **РезервноеКопированиеИБ** — common modules `РезервноеКопированиеИБКлиент`
(client stable API - opening the form), `РезервноеКопированиеИБСервер`
(server-side, ⚠️ entirely in service regions - read/write settings, status,
navigation link), `РезервноеКопированиеИБВызовСервера` (⚠️ service -
server calls from the client), `РезервноеКопированиеИБКлиентПереопределяемый`
(hook for disabling warnings). Covers opening the backup form,
programmatic work with settings, calculating the next backup date, resetting
the flag after restore.

The subsystem supports only the **file-based** IB variant; in the client-server
variant, automatic backup methods return `Ложь`, and the UI form is useless - use
database tools and the flag
`РезервноеКопированиеНастроено = Истина` without
`ВыполнятьАвтоматическоеРезервноеКопирование`. Uploading to cloud storage
(Google Drive, Yandex.Disk) is not provided in this API version - a local file
system directory is used (`КаталогХраненияРезервныхКопий`).

## Modules

- `РезервноеКопированиеИБКлиент` - the **only stable** client
  method: `ОткрытьФормуРезервногоКопирования`. The module's other exported methods
  - subsystem event handlers (`ПриНачалеРаботыСистемы`,
  `ПередЗавершениемРаботыСистемы`, `ПриПредложенииПользователюСоздатьРезервнуюКопию`)
  in `СлужебныйПрограммныйИнтерфейс` - are called by БСП, not from application code.
  Thin / Thick client.
- `РезервноеКопированиеИБСервер` - ⚠️ **entirely service**: in
  `СлужебныйПрограммныйИнтерфейс` - `УстановитьНастройкиРезервногоКопирования`,
  `ТекущаяНастройкаРезервногоКопирования`,
  `НавигационнаяСсылкаОбработкиРезервногоКопирования`; in
  `СлужебныеПроцедурыИФункции` - `ПараметрыРезервногоКопирования`,
  `СброситьПризнакРезервногоКопирования`, `УстановитьДатуПоследнегоНапоминания`,
  `УстановитьЗначениеНастройки`, `ЗавершитьРезервноеКопирование`. Backward
  compatibility is not guaranteed.
- `РезервноеКопированиеИБВызовСервера` - ⚠️ service (server call from
  the client): `УстановитьЗначениеНастройки`, `ДатаСледующегоАвтоматическогоКопирования`,
  `УстановитьДатуПоследнегоНапоминания` (in `СлужебныеПроцедурыИФункции`).
- `РезервноеКопированиеИБКлиентПереопределяемый` - **hook**
  `ПриОпределенииНеобходимостиПоказаПредупрежденийОРезервномКопировании`.
- `РезервноеКопированиеОбластейДанных` / `…Клиент` - service model stubs
  (SaaS), in the local IB they are empty.

⚠️ The modules `РезервноеКопированиеИБСлужебный`,
`РезервноеКопированиеИБПереопределяемый` (without `Клиент`),
`РезервноеКопированиеИБКлиентСервер` do not exist. Service logic is built into the main
modules, and overriding is client-only. Before calling, check the real directory of common modules.

## Scenarios

### 1. Open the backup form with file-mode validation

**Task:** from an application command, open the form for creating/restoring a
backup, first making sure that the infobase is file-based.

**Function:**
`РезервноеКопированиеИБКлиент.ОткрытьФормуРезервногоКопирования(Параметры = Неопределено) Экспорт`
— Procedure, region: `#Область ПрограммныйИнтерфейс` (stable). Thin / Thick
client.

**Parameters:**
- `Параметры` (Structure / Undefined) — form parameters. To start on
  shutdown: `New Structure("РежимРаботы", "ВыполнитьПриЗавершенииРаботы")`.

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
- ❌ Opening the form without checking `ИнформационнаяБазаФайловая()` — in the
  client-server variant the form will open “empty” (a subsystem limitation - see the warning in the introduction).
- `ОткрытьФормуРезервногоКопирования` is the **only** stable client entry
  point. Do not invent `ОткрытьФормуНастроекКопирования` — the settings form is
  opened by a subsystem UI command or a navigation link
  (scenario 3).

### 2. Read and save copy settings

**Task:** from server-side processing, initialize the schedule, directory,
retention period, and automatic copy flags; correctly overwrite the settings
without losing default values.

**Functions:**
`РезервноеКопированиеИБСервер.ПараметрыРезервногоКопирования() Экспорт` — Function → Structure (see `НовыеНастройкиРезервногоКопирования`).
`РезервноеКопированиеИБСервер.УстановитьНастройкиРезервногоКопирования(Знач Настройки, Знач Пользователь = Неопределено) Экспорт` — Procedure.
— ⚠️ **internal** (`СлужебныеПроцедурыИФункции` / `СлужебныйПрограммныйИнтерфейс`);
backward compatibility is not guaranteed. Server, External connection.

**Parameters:**
- `Настройки` (Structure, see `НовыеНастройкиРезервногоКопирования`) — full
  set of fields: `ВыполнятьАвтоматическоеРезервноеКопирование` (Boolean),
  `РезервноеКопированиеНастроено` (Boolean),
  `РасписаниеКопирования` (Structure of the scheduled job schedule),
  `КаталогХраненияРезервныхКопий` (String),
  `КаталогХраненияРезервныхКопийПриРучномЗапуске` (String),
  `ВариантВыполнения` (`"ПоРасписанию"` / `"ПриЗавершенииРаботы"`),
  `АдминистраторИБ` (String), `ПарольАдминистратораИБ` (String),
  `ПараметрыУдаления` (Structure: `ТипОграничения`, `КоличествоКопий`,
  `ЕдиницаИзмеренияПериода`, `ЗначениеВЕдиницахИзмерения`),
  `ДатаПоследнегоРезервногоКопирования`, `МинимальнаяДатаСледующегоАвтоматическогоРезервногоКопирования`,
  `РучнойЗапускПоследнегоРезервногоКопирования` and others.
- `Пользователь` (ПользовательСсылка / Undefined) — if specified, the settings
  are additionally saved in the constant `ПараметрыРезервногоКопирования` for
  transfer to the background session when shutting down.

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

**Nuances / anti-patterns:**
- ❌ Building the settings structure "from scratch" with `Новый Структура` —
  `УстановитьНастройкиРезервногоКопирования` expects a **complete** set of fields, and
  default values and service fields will be lost (`ДатаПоследнегоРезервногоКопирования`,
  `МинимальнаяДатаСледующегоАвтоматическогоРезервногоКопирования`, etc.). Only
  `ПараметрыРезервногоКопирования()` → edit fields → write.
- ❌ Programmatically starting backup through `ЗавершитьРезервноеКопирование(Результат, ИмяФайлаРезервнойКопии = "")` — this is a service **completion** method
  (processing the result after execution), not a start method. The backup
  is initiated through the form (`ОткрытьФормуРезервногоКопирования` with
  `РежимРаботы = "ВыполнитьПриЗавершенииРаботы"`) or by a scheduled job.

### 3. Show the status and navigation link in the card

**Task:** get a ready-made localized string describing the current
backup mode and a navigation link to the processing object for insertion into a
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

**Nuances / anti-patterns:**
- `ТекущаяНастройкаРезервногоКопирования` takes the IB variant into account: in
  client-server mode it returns "Backup is not performed (handled by the DBMS)"; if settings are absent — "To configure backup, contact the administrator.".
  Do not construct such text yourself.
- `НавигационнаяСсылкаОбработкиРезервногоКопирования` returns a link to the
  **main** processing object `РезервноеКопированиеИБ` (form `РезервноеКопированиеДанных`),
  not to the settings form.

### 4. Calculate the next backup date and change a single settings field

**Task:** in the client-side settings form, show the date of the next
automatic backup and save the change of a single field without
re-reading the entire structure (from the form item change handler).

**Functions:**
`РезервноеКопированиеИБВызовСервера.ДатаСледующегоАвтоматическогоКопирования(ОтложитьРезервноеКопирование = Ложь) Экспорт` — Function → Date.
`РезервноеКопированиеИБВызовСервера.УстановитьЗначениеНастройки(ИмяЭлемента, ЗначениеЭлемента) Экспорт` — Procedure.
— ⚠️ **service** (`СлужебныеПроцедурыИФункции`). Server call from the client
(Thin/Fat client → Server).

**Parameters:**
- `ОтложитьРезервноеКопирование` (Boolean) — `Истина` shifts the minimum date
  forward by 1 hour (for the "Отложить" button).
- `ИмяЭлемента` (String) — the settings field name, e.g. `"КаталогХраненияРезервныхКопий"`.
- `ЗначениеЭлемента` (Any) — the new field value.

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
    // Save a single field without re-reading the entire settings structure
    РезервноеКопированиеИБВызовСервера.УстановитьЗначениеНастройки(
        "КаталогХраненияРезервныхКопий", Текст);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ⚠️ `УстановитьЗначениеНастройки` and `УстановитьДатуПоследнегоНапоминания`
  are defined in **two** modules — `…ВызовСервера` (client server call)
  and `…Сервер` (server context). From client code use `ВызовСервера`, from
  server-side code use `Сервер`. Verify via
  `python scripts/bsp_api.py method УстановитьЗначениеНастройки --src src/cf`.
- `ДатаСледующегоАвтоматическогоКопирования` only makes sense when
  `ВариантВыполнения = "ПоРасписанию"` and the file-based IB variant.

### 5. Reset the copy flag after restoration

**Task:** after restoring the infobase from a backup, reset the flag
`ПроведеноКопирование` (so that БСП does not consider copying current) and,
if the monitoring subsystem is available, record the operation.

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
- ❌ Resetting the flag "just in case" in a regular session — this is a service
  method for the restoration scenario. In normal operation, the flag is set
  by the copy mechanism itself.
- The method also writes the operation to the monitoring center if the monitoring
  subsystem is present — no separate call is needed.

### 6. Override: disable backup configuration warnings

**Task:** in the company, backups are made by third-party tools, and the БСП banners
about the need to configure copying get in the way — disable them via a hook.

**Function (hook):**
`РезервноеКопированиеИБКлиентПереопределяемый.ПриОпределенииНеобходимостиПоказаПредупрежденийОРезервномКопировании(ПоказыватьПредупреждение) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс`. **Hook**: БСП calls it,
application code implements it in a module with the same name in the application configuration.
Thin / Thick client.

**Parameters:**
- `ПоказыватьПредупреждение` (Boolean, output) — set to `Ложь` so that БСП
  does not show warnings.

**Example:**
```bsl
// В модуле РезервноеКопированиеИБКлиентПереопределяемый прикладной конфигурации
Процедура ПриОпределениеНеобходимостиПоказаПредупрежденийОРезервномКопировании(ПоказыватьПредупреждение) Экспорт
    // Бэкапы делает внешняя система — не показываем предупреждения БСП
    ПоказыватьПредупреждение = Ложь;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Calling the hook as a regular method `РезервноеКопированиеИБКлиентПереопределяемый.ПриОпределениеНеобходимостиПоказаПредупреждений…(Ложь)`
  from application code — it is **not called**, it is **implemented**. БСП itself
  will call your implementation.
- Do not confuse this with disabling the subsystem itself — the hook suppresses only the warnings,
  the scheduled job and the settings API continue to work.

## Additional

Other service methods (full signatures are available via
`python scripts/bsp_api.py method <Name> --src src/cf`):

- `РезервноеКопированиеИБСервер.НастройкиРезервногоКопирования(НачалоРаботы = Ложь)` — ⚠️ service, a settings-reading variant with a startup flag.
- `РезервноеКопированиеИБСервер.УстановитьДатуПоследнегоНапоминания(ДатаНапоминания)` — ⚠️ service; record the reminder date for the user (duplicated in `…ВызовСервера`).
- `РезервноеКопированиеИБСервер.ЗавершитьРезервноеКопирование(Результат, ИмяФайлаРезервнойКопии = "")` / `ЗавершитьВосстановление(Результат)` — ⚠️ service handlers for the execution **result**, not the launch.
- `РезервноеКопированиеИБСервер.ИнформацияОПользователе()` — ⚠️ service, user data for copying.

Subsystem processing objects:

- `Обработка.РезервноеКопированиеИБ` — main form `РезервноеКопированиеДанных` (create/restore a copy). Opened through `ОткрытьФормуРезервногоКопирования`.
- `Обработка.НастройкаРезервногоКопированияИБ` — settings form; opened by the subsystem UI command or a navigation link (scenario 3), there is no direct exported opening method.

Settings storage: key `ПараметрыРезервногоКопирования` in the common settings storage + duplicate constant `ПараметрыРезервногоКопирования` (type `ХранилищеЗначения`, compression 9) for passing parameters to a background session when finishing work. The `ПараметрыРезервногоКопирования` method returns a `Структуру` (not `ФиксированнаяСтруктура`) with a fixed set of fields — do not add your own fields, `УстановитьНастройкиРезервногоКопирования` will write only the original set.