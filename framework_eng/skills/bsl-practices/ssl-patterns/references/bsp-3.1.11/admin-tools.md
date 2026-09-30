# Administrative tools: sessions, deletion of marked objects, security profiles

Three IB administration subsystems: **UserWorkTermination**
(common modules `СоединенияИБ*` — IB and data area session lock,
connection information, shutdown mode), **DeleteMarkedObjects**
(`УдалениеПомеченныхОбъектов*` — programmatic/interactive deletion with referential
integrity control, scheduled deletion, visibility of marked objects
in list forms), **SecurityProfiles** (`РаботаВБезопасномРежиме*` —
permissions for external resources: file system directories, COM classes, internet resources,
external modules/components, privileged mode).

## Modules

**UserWorkTermination:**

- `СоединенияИБ` — **stable server API**: IB lock, data area session lock,
  lock parameters, connection information.
  Server, Thick client, External connection.
- `СоединенияИБКлиент` — **stable client API**: shutdown mode, session termination
  flag, administration parameters form.
  Thin / Thick client.
- `СоединенияИБКлиентСервер` / `СоединенияИБВызовСервера` — ⚠️ internal.
- `СоединенияИБПереопределяемый` — **hook** `ПриОпределенииПараметровБлокировкиСеансов`.

**DeleteMarkedObjects:**

- `УдалениеПомеченныхОбъектов` — **stable server API**: retrieving
  marked objects, references to objects to be deleted, programmatic deletion, display settings,
  scheduled mode.
  Server, Thick client, External connection.
- `УдалениеПомеченныхОбъектовКлиент` — **stable client API**:
  interactive deletion, visibility of marked objects, schedule.
- `УдалениеПомеченныхОбъектовПереопределяемый` — **hooks** before/after deleting
  a group, determining objects with the "Show marked objects" command.
- `УдалениеПомеченныхОбъектовПовтИсп` / `…Служебный` /
  `…СлужебныйВызовСервера` / `…СлужебныйКлиентСервер` — ⚠️ internal.

**SecurityProfiles:**

- `РаботаВБезопасномРежиме` — **stable server API**: constructors
  for permissions `РазрешениеНа*`, `ЗапросНаИспользованиеВнешнихРесурсов`,
  `ЗапросНаОтменуРазрешений…`, `УстановленБезопасныйРежим`.
  Server, Thick client, External connection.
- `РаботаВБезопасномРежимеКлиент` — **stable client API**:
  `ПрименитьЗапросыНаИспользованиеВнешнихРесурсов`,
  `ОткрытьДиалогНастройкиИспользованияПрофилейБезопасности`.
- `РаботаВБезопасномРежимеПереопределяемый` — **hooks**: checking the possibility
  of using/configuring profiles, filling permissions, requests
  to create/delete a profile.

⚠️ The `UserWorkTermination` subsystem is implemented in modules with the root
`СоединенияИБ` (not `UserWorkTermination...`), `SecurityProfiles` is implemented in modules with the
root `РаботаВБезопасномРежиме`. There are no modules with the literal subsystem names - it is a common mistake to look for `UserWorkTermination.Установить...`.

## Scenarios

### 1. Block the infobase before administration and unblock afterward

**Task:** before updating/migrating, set an infobase connection lock with a delay and duration, and guarantee that it is removed even if an error occurs.

**Functions:**
`СоединенияИБ.УстановитьБлокировкуСоединений(Знач ТекстСообщения = "", Знач КодРазрешения = "КодРазрешения", Знач ОжиданиеНачалаБлокировки = 0, Знач ДлительностьБлокировки = 0) Экспорт`
— Function → Boolean (success).
`СоединенияИБ.РазрешитьРаботуПользователей() Экспорт` — Function → Boolean.
`СоединенияИБ.УстановленаБлокировкаСоединений() Экспорт` — Function → Boolean.
— region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client,
External connection.

**Parameters:**
- `ТекстСообщения` (String) — the text that connecting users will see on refusal.
- `КодРазрешения` (String) — the code for an administrator to enter the locked infobase;
  default is `"КодРазрешения"`.
- `ОжиданиеНачалаБлокировки` (Number, minutes) — delay before the
  lock takes effect (gives users time to finish their work).
- `ДлительностьБлокировки` (Number, minutes) — how long to keep the lock; `0` means
  until it is explicitly removed.

**Example:**
```bsl
УстановленаБлокировка = Ложь;
Попытка
    УстановленаБлокировка = СоединенияИБ.УстановитьБлокировкуСоединений(
        "Технические работы. Вход — с кодом разрешения.",
        "СекретныйКод", 5, 60);  // отсрочка 5 мин, длительность 60 мин
    Если Не УстановленаБлокировка Тогда
        ВызватьИсключение "Не удалось установить блокировку ИБ";
    КонецЕсли;
    // ...администрирование, обновление, миграция...
Исключение
    Если УстановленаБлокировка Тогда
        СоединенияИБ.РазрешитьРаботуПользователей();
    КонецЕсли;
    ВызватьИсключение;
КонецПопытки;

Если УстановленаБлокировка Тогда
    СоединенияИБ.РазрешитьРаботуПользователей();
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Set a lock without `Попытка…Исключение` — if the code crashes between
  setting and removing it, the infobase will remain locked. Always remove it
  reliably.
- Nuance: if called from a session with separators set,
  `УстановитьБлокировкуСоединений` sets a **data area session lock**, not the entire infobase.
  For an explicit data area lock, use
  `УстановитьБлокировкуСеансовОбластиДанных` (scenario 2).
- Before writing/doing a long-running operation, check
  `УстановленаБлокировкаСоединений()` and warn the user.

### 2. Data area session lock (service model)

**Task:** in the service model, lock sessions of one data area for a
period of time, then read the current lock.

**Functions:**
`СоединенияИБ.НовыеПараметрыБлокировкиСоединений() Экспорт` — Function → parameter Structure.
`СоединенияИБ.УстановитьБлокировкуСеансовОбластиДанных(Знач Параметры, Знач ПоМестномуВремени = Истина, Знач ОбластьДанных = -1) Экспорт` — Procedure.
`СоединенияИБ.ПолучитьБлокировкуСеансовОбластиДанных(Знач ПоМестномуВремени = Истина) Экспорт` — Function → Structure.
`СоединенияИБ.ПараметрыБлокировкиСеансов(Знач ПолучитьКоличествоСеансов = Ложь) Экспорт` — Function → structure of current IB lock parameters.
— region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client,
External connection.

**Parameters:**
- `Параметры` (Structure, see `НовыеПараметрыБлокировкиСоединений`) — contains
  `Начало`, `Конец` (Date), `Сообщение` (String), `Установлена` (Boolean),
  `Эксклюзивная` (Boolean).
- `ПоМестномуВремени` (Boolean) — `Истина` → start/end in the session's local time;
  `Ложь` → in universal time.
- `ОбластьДанных` (Number) — area number; `-1` — current one (from session
  separators). From a session with separators, only the matching one can be passed or
  omitted; from a session without separators, it is required.

**Example:**
```bsl
Параметры = СоединенияИБ.НовыеПараметрыБлокировкиСоединений();
Параметры.Начало     = '20260101230000';  // 23:00 January 1
Параметры.Конец      = '20260102060000';  // 06:00 January 2
Параметры.Сообщение  = "Область данных заблокирована для обновления";
Параметры.Установлена = Истина;

// Set a lock for data area #3
СоединенияИБ.УстановитьБлокировкуСеансовОбластиДанных(Параметры, Истина, 3);

// Remove it — the same procedure with Установлена = Ложь
Параметры.Установлена = Ложь;
СоединенияИБ.УстановитьБлокировкуСеансовОбластиДанных(Параметры, Истина, 3);

// Read the current lock
Текущая = СоединенияИБ.ПолучитьБлокировкуСеансовОбластиДанных(Истина);
```

**Nuances / anti-patterns:**
- ❌ Confusing `УстановитьБлокировкуСоединений` (entire IB) and
  `УстановитьБлокировкуСеансовОбластиДанных` (one area in SaaS). These are different
  lock levels.
- `РазрешитьРаботуПользователей` removes the IB lock; for data areas,
  removal is a repeated call to `УстановитьБлокировкуСеансовОбластиДанных` with
  `Параметры.Установлена = Ложь`.
- `ПараметрыБлокировкиСеансов(Истина)` returns a structure with the field
  `КоличествоСеансов` — useful for control before locking.

### 3. Shutdown mode and administration parameters (client)

**Task:** from client code, enable user shutdown mode,
mark other sessions for shutdown, open the input form for
IB/cluster administration parameters; get connection information.

**Functions:**
`СоединенияИБКлиент.УстановитьРежимЗавершенияРаботыПользователей(Знач ЗавершитьРаботу) Экспорт` — Procedure.
`СоединенияИБКлиент.УстановитьПризнакЗавершитьВсеСеансыКромеТекущего(Значение) Экспорт` — Procedure.
`СоединенияИБКлиент.ПоказатьПараметрыАдминистрирования(ОписаниеОповещенияОЗакрытии, ЗапрашиватьПараметрыАдминистрированияИБ, ЗапрашиватьПараметрыАдминистрированияКластера, ПараметрыАдминистрирования = Неопределено, Заголовок = "", ПоясняющаяНадпись = "") Экспорт` — Procedure.
`СоединенияИБ.ИнформацияОСоединениях(ПолучатьСтрокуСоединения = Ложь, СообщенияДляЖурналаРегистрации = Неопределено, ПортКластера = 0) Экспорт` — Function → Structure.
— client-side — region `ПрограммныйИнтерфейс` (stable), Thin/Fat client;
`ИнформацияОСоединениях` — server-side, Server/Thick client/External connection.

**Parameters:**
- `ЗавершитьРаботу` (Boolean) — `Истина` enables shutdown mode.
- `Значение` (Boolean) — the "shut down all sessions except the current one" flag.
- `ОписаниеОповещенияОЗакрытии` (NotificationDescription) — handler after closing
the parameters form.
- `ЗапрашиватьПараметрыАдминистрированияИБ` / `…Кластера` (Boolean) — which
groups of parameters to request.
- `ПараметрыАдминистрирования` (Structure) — initial values.
- `ПолучатьСтрокуСоединения` (Boolean) — add the connection string to the
  `ИнформацияОСоединениях` result.

**Example:**
```bsl
&НаКлиенте
Процедура НачатьАдминистрирование(Команда)
    Если СоединенияИБ.УстановленаБлокировкаСоединений() Тогда
        ПоказатьПредупреждение(, "ИБ заблокирована. Обратитесь к администратору.");
        Возврат;
    КонецЕсли;
    // Send a warning to active users
    СоединенияИБКлиент.УстановитьРежимЗавершенияРаботыПользователей(Истина);
    // Do not end the current session
    СоединенияИБКлиент.УстановитьПризнакЗавершитьВсеСеансыКромеТекущего(Истина);

    // Open the IB administration parameters form
    Оповещение = Новый ОписаниеОповещения("ПослеПараметровАдминистрирования", ЭтотОбъект);
    СоединенияИБКлиент.ПоказатьПараметрыАдминистрирования(Оповещение, Истина, Ложь);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Calling `ПоказатьПараметрыАдминистрирования` without checking the subsystem — if
  `ЗавершениеРаботыПользователей` is not implemented, an error will occur. First call
  `ОбщегоНазначенияКлиент.ПодсистемаСуществует("СтандартныеПодсистемы.ЗавершениеРаботыПользователей")`.
- `ИнформацияОСоединениях` — a server method; from client code, call it
  in `&НаСервере` and pass the result to the client.

### 4. Programmatically delete marked objects with result control

**Task:** get objects marked for deletion (with metadata filtering),
delete them while controlling referential integrity, and correctly handle
blocking references.

**Functions:**
`УдалениеПомеченныхОбъектов.ПомеченныеНаУдаление(Знач ОтборМетаданных = Неопределено, ИскатьТехнологическиеОбъекты = Ложь) Экспорт` — Function → Array.
`УдалениеПомеченныхОбъектов.УдалитьПомеченныеОбъекты(УдаляемыеОбъекты, РежимУдаления = "Стандартный") Экспорт` — Function → Structure.
`УдалениеПомеченныхОбъектов.СсылкиНаУдаляемыеОбъекты(Источник) Экспорт` — Function → Map.
— region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client,
External connection.

**Parameters:**
- `ОтборМетаданных` (ValueList of String / Undefined) — full metadata names,
  for example, `"Справочник.Номенклатура"`; `Undefined` means no filtering.
- `ИскатьТехнологическиеОбъекты` (Boolean) — include technological objects.
- `УдаляемыеОбъекты` (Array of *Reference) — objects to delete.
- `РежимУдаления` (String) — `"Стандартный"` (control + multi-user
  operation), `"Монопольный"` (with exclusive mode enabled; if it fails —
  exception), `"Упрощенный"` (control only for unmarked objects; for marked
  objects, references to deleted ones are **cleared**).
- `Источник` (СправочникОбъект / ДокументОбъект / РегистрСведенийНаборЗаписей) —
  object in which to search for references to the deleted objects.

**Example:**
```bsl
// All marked
Помеченные = УдалениеПомеченныхОбъектов.ПомеченныеНаУдаление();
Если Помеченные.Количество() = 0 Тогда
    Возврат;
КонецЕсли;

// Filter by type (optional)
ОтборМетаданных = Новый СписокЗначений;
ОтборМетаданных.Добавить("Справочник.Контрагенты");
ПомеченныеКонтрагенты = УдалениеПомеченныхОбъектов.ПомеченныеНаУдаление(ОтборМетаданных);

Результат = УдалениеПомеченныхОбъектов.УдалитьПомеченныеОбъекты(ПомеченныеКонтрагенты, "Стандартный");
Если Не Результат.Успешно Тогда
    Для Каждого СтрокаПрепятствия Из Результат.ПрепятствующиеУдалению Цикл
        ОбщегоНазначения.СообщитьПользователю(
            "Нельзя удалить " + Строка(СтрокаПрепятствия.УдаляемыйСсылка)
            + ": используется в " + Строка(СтрокаПрепятствия.МестоИспользования));
    КонецЦикла;
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Call `УдалитьПомеченныеОбъекты` inside an explicit transaction — the method
  manages transactions and batching itself. An external transaction will cause
  conflicts.
- ❌ Ignore the result — objects may not be deleted because of referential
  integrity; always check `Результат.Успешно` and iterate over
  `ПрепятствующиеУдалению` (value table columns `УдаляемыйСсылка`,
  `МестоИспользования`, `ОписаниеОшибки`, `ПодробноеОписаниеОшибки`,
  `ОбнаруженныйСтатус`).
- ❌ `УдалениеПомеченныхОбъектовСлужебный.<Метод>` — service module, backward
  compatibility is not guaranteed. Use only the stable API.
- `СсылкиНаУдаляемыеОбъекты` for record sets subordinate to the registrar
  returns an empty list — this is intentional for performance and for the
  uninterrupted operation of posting generation mechanisms.

### 5. Integrate marked-item visibility into the list form

**Task:** in a form with a dynamic list, configure the visibility of the marked for deletion and mark statuses for the «Show Marked» button.

**Functions:**
`УдалениеПомеченныхОбъектов.ПриСозданииНаСервере(Форма, Знач НастройкиОтображенияПомеченныхОбъектов) Экспорт` — Procedure.
`УдалениеПомеченныхОбъектов.НастройкиОтображенияПомеченныхОбъектов() Экспорт` — Function → Table of Values (columns `ИмяЭлементаФормы`, `ТипыМетаданных`, `ИмяСписка`).
`УдалениеПомеченныхОбъектов.УстановитьПометкуКомандыПоказатьПомеченные(Форма, ТаблицаФормы, КнопкаФормы) Экспорт` — Procedure.
— region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client,
External connection.

**Parameters:**
- `Форма` (ФормаКлиентскогоПриложения) — form with a dynamic list.
- `НастройкиОтображенияПомеченныхОбъектов` (see `НастройкиОтображенияПомеченныхОбъектов` / ТаблицаФормы) — either a settings table or a dynamic list form
  element (for one list).
- `ТаблицаФормы` / `КнопкаФормы` — form elements for `УстановитьПометкуКомандыПоказатьПомеченные`.

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    // Option 1: one dynamic list — pass the form element
    УдалениеПомеченныхОбъектов.ПриСозданииНаСервере(ЭтотОбъект, Элементы.Список);
КонецПроцедуры

&НаСервере
Процедура ПриСозданииНаСервере_НесколькоСписков(Отказ, СтандартнаяОбработка)
    // Option 2: multiple lists — settings table
    Настройки = УдалениеПомеченныхОбъектов.НастройкиОтображенияПомеченныхОбъектов();
    Настройка = Настройки.Добавить();
    Настройка.ИмяЭлементаФормы = "Список1";
    ОсновныеТаблицы = Новый СписокЗначений;
    ОсновныеТаблицы.Добавить("Справочник.Номенклатура");
    Настройка.ТипыМетаданных = ОсновныеТаблицы;
    Настройка = Настройки.Добавить();
    Настройка.ИмяЭлементаФормы = "Список2";
    УдалениеПомеченныхОбъектов.ПриСозданииНаСервере(ЭтотОбъект, Настройки);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- The second parameter of `ПриСозданииНаСервере` accepts **either** a form element
  (ТаблицаФормы, for one list) **or** a settings table from
  `НастройкиОтображенияПомеченныхОбъектов` (for multiple lists). Do not pass
  an arbitrary structure - only these two variants.
- `ТипыМетаданных` (СписокЗначений from Строка) — for navigating to the marked-items
  list with a preset filter by type.

### 6. Scheduled deletion by schedule

**Task:** read the settings for scheduled deletion of marked objects and change the use flag.

**Functions:**
`УдалениеПомеченныхОбъектов.РежимУдалятьПоРасписанию() Экспорт` — Function →
Structure (`Расписание`, `Использование`, `РазделениеВключено`).
`УдалениеПомеченныхОбъектов.ЗначениеФлажкаУдалятьПоРасписанию() Экспорт` — ⚠️
obsolete (region `УстаревшиеПроцедурыИФункции`); alternative —
`РежимУдалятьПоРасписанию`.
— server-side. The client wrapper for toggling the flag is —
`УдалениеПомеченныхОбъектовКлиент.ПриИзмененииФлажкаУдалятьПоРасписанию(АвтоматическиУдалятьПомеченныеОбъекты, ОповещениеОбИзменении = Неопределено) Экспорт`.

**Parameters:**
- `АвтоматическиУдалятьПомеченныеОбъекты` (Boolean) — new value of the flag.

**Example:**
```bsl
// Server: read mode
Режим = УдалениеПомеченныхОбъектов.РежимУдалятьПоРасписанию();
Если Режим.Использование Тогда
    // Расписание = Режим.Расписание (see РегламентныеЗаданияСервер.РасписаниеРегламентногоЗадания)
КонецЕсли;

// Client: handler for toggling the flag in the settings form
&НаКлиенте
Процедура АвтоматическиУдалятьПомеченныеОбъектыПриИзменении(Элемент)
    УдалениеПомеченныхОбъектовКлиент.ПриИзмененииФлажкаУдалятьПоРасписанию(
        Элементы.АвтоматическиУдалятьПомеченныеОбъекты.Проверять);
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Use `ЗначениеФлажкаУдалятьПоРасписанию` in new code — the method is
  obsolete (region `УстаревшиеПроцедурыИФункции`). Replacement —
  `РежимУдалятьПоРасписанию`.
- The schedule is stored in the settings of the scheduled job
  `УдалениеПомеченныхОбъектов`; to change the schedule interactively,
  use `УдалениеПомеченныхОбъектовКлиент.НачатьИзменениеРасписанияРегламентногоЗадания(ОповещениеОбИзменении)`.
- The deletion procedure itself is launched by a scheduled job; calling
  `УдалитьПомеченныеОбъекты` programmatically (scenario 4) and the scheduled job are
  different scenarios: the first is a one-time deletion, the second is background execution by schedule.

### 7. Request permissions for external resources (security profiles)

**Task:** before making an external call (HTTP, COM class, file system directory,
external component), request security profile permissions and apply them
on the client through a dialog.

**Functions:**
`РаботаВБезопасномРежиме.РазрешениеНаИспользованиеИнтернетРесурса(Знач Протокол, Знач Адрес, Знач Порт = Неопределено, Знач Описание = "") Экспорт` — Function → XDTOObject.
`РаботаВБезопасномРежиме.РазрешениеНаИспользованиеКаталогаФайловойСистемы(Знач Адрес, Знач ЧтениеДанных = Ложь, Знач ЗаписьДанных = Ложь, Знач Описание = "") Экспорт` — Function → XDTOObject.
`РаботаВБезопасномРежиме.РазрешениеНаСозданиеCOMКласса(Знач ProgID, Знач CLSID, Знач ИмяКомпьютера = "", Знач Описание = "") Экспорт` — Function → XDTOObject.
`РаботаВБезопасномРежиме.РазрешениеНаИспользованиеВнешнейКомпоненты(Знач ИмяМакета, Знач Описание = "") Экспорт` / `РазрешениеНаИспользованиеВнешнегоМодуля(Знач Имя, Знач КонтрольнаяСумма, Знач Описание = "") Экспорт` / `РазрешениеНаИспользованиеКаталогаВременныхФайлов(…) Экспорт` / `РазрешениеНаИспользованиеКаталогаПрограммы(…) Экспорт` / `РазрешениеНаИспользованиеПриложенияОперационнойСистемы(…) Экспорт` / `РазрешениеНаИспользованиеПривилегированногоРежима(Знач Описание = "") Экспорт`.
`РаботаВБезопасномРежиме.ЗапросНаИспользованиеВнешнихРесурсов(Знач НовыеРазрешения, Знач Владелец = Неопределено, Знач РежимЗамещения = Истина) Экспорт` — Function.
`РаботаВБезопасномРежиме.УстановленБезопасныйРежим() Экспорт` — Function → Boolean.
`РаботаВБезопасномРежимеКлиент.ПрименитьЗапросыНаИспользованиеВнешнихРесурсов(Знач Идентификаторы, ФормаВладелец, ОповещениеОЗакрытии) Экспорт` — Procedure.
— region `#Область ПрограммныйИнтерфейс` (stable). Server-side — Server/Thick
client/External connection; client wrapper — Thin/Thick client.

**Parameters:**
- `Протокол` (String) — `IMAP`, `POP3`, `SMTP`, `HTTP`, `HTTPS`, `FTP`, `FTPS`,
  `WS`, `WSS`.
- `Адрес` (String) — resource address without the protocol.
- `Порт` (Number) — port number.
- `ProgID` / `CLSID` (String) — COM class identifiers (e.g. `"Excel.Application"`).
- `ИмяМакета` (String) — the name of the external component template in the configuration.
- `НовыеРазрешения` (Array) — permission objects from `РазрешениеНа*`.
- `Владелец` (AnyReference) — an IB object with which the permissions are logically associated
  (e.g. a catalog item `ТомаХраненияФайлов` for volume directories).
- `РежимЗамещения` (Boolean) — `Истина` replaces the owner's previous permissions.
- `Описание` (String) — the reason for the request (visible to the administrator).

**Example:**
```bsl
// Сервер: подготовить запрос на HTTP-доступ к API и каталогу выгрузки
Разрешения = Новый Массив;
Разрешения.Добавить(РаботаВБезопасномРежиме.РазрешениеНаИспользованиеИнтернетРесурса(
    "HTTPS", "api.example.com", 443, "Запрос курсов валют"));
Разрешения.Добавить(РаботаВБезопасномРежиме.РазрешениеНаИспользованиеКаталогаФайловойСистемы(
    "D:\Uploads", , Истина, "Каталог выгрузки"));

// Создать запрос (применяется при следующем сеансе/обновлении профиля)
Идентификатор = РаботаВБезопасномРежиме.ЗапросНаИспользованиеВнешнихРесурсов(
    Разрешения, СправочникСсылка.ИнтеграцияСAPI);
Идентификаторы = Новый Массив;
Идентификаторы.Добавить(Идентификатор);

// Клиент: применить запросы через диалог профилей безопасности
РаботаВБезопасномРежимеКлиент.ПрименитьЗапросыНаИспользованиеВнешнихРесурсов(
    Идентификаторы, ЭтаФорма, ОписаниеОповещения);
```

**Nuances / anti-patterns:**
- ❌ Perform an external call without requesting permission in the security profile —
  when profiles are enabled, the call will fail with an access error. First
  `ЗапросНаИспользованиеВнешнихРесурсов`, then apply via
  `ПрименитьЗапросыНаИспользованиеВнешнихРесурсов` (client dialog).
- `УстановленБезопасныйРежим()` checks safe mode, **ignoring** the security
  profile with the configuration privilege level — useful for conditional
  code.
- There are `ЗапросНаОтменуРазрешенийИспользованияВнешнихРесурсов(Владелец, ОтменяемыеРазрешения)` and
  `ЗапросНаОчисткуРазрешенийИспользованияВнешнихРесурсов(Владелец)` — for revoking
  permissions that are no longer needed (e.g. when removing an integration).
- `Владелец` binds permissions to the IB object — when the owner is deleted, BSP
  can clear its permissions.

## Rare Methods

Other stable methods (full signatures — via
`python scripts/bsp_api.py method <Имя> --src src/cf`):

- `СоединенияИБ.ИнформацияОСоединениях(ПолучатьСтрокуСоединения = Ложь, СообщенияДляЖурналаРегистрации = Неопределено, ПортКластера = 0)` — information about current connections; `СообщенияДляЖурналаРегистрации` (ValueList) — writes events to the log.
- `УдалениеПомеченныхОбъектовКлиент.НачатьУдалениеПомеченных(УдаляемыеОбъекты, ПараметрыУдаления = Неопределено, Владелец = Неопределено, ОписаниеОповещенияОЗакрытии = Неопределено)` — opens the interactive deletion form; `ПараметрыУдаления` — from `УдалениеПомеченныхОбъектовКлиент.ПараметрыИнтерактивногоУдаления()`.
- `УдалениеПомеченныхОбъектовКлиент.ПоказатьПомеченныеНаУдаление(Форма, ТаблицаФормы, КнопкаФормы)` / `ПерейтиКПомеченнымНаУдаление(Форма, ТаблицаФормы = Неопределено)` — toggle the visibility of marked items and navigate to the deletion workspace.
- `РаботаВБезопасномРежиме.ЗапросыОбновленияРазрешенийКонфигурации(Знач ВключаяЗапросСозданияПрофиляИБ = Истина)` — pending permission update requests; `КонтрольныеСуммыФайловКомплектаВнешнейКомпоненты(Знач ИмяМакета)` — checksums of the external component package.

Override hooks (modules `*Переопределяемый`, region `ПрограммныйИнтерфейс`
— **BSP calls, application code implements**):

- `УдалениеПомеченныхОбъектовПереопределяемый.ПередУдалениемГруппыОбъектов(Контекст, УдаляемыеОбъекты)` — **outside the transaction** before deleting the group; you can initialize `Контекст` to pass into `ПослеУдаления`.
- `УдалениеПомеченныхОбъектовПереопределяемый.ПослеУдаленияГруппыОбъектов(Контекст, Успешно)` — **outside the transaction** after; for logging, cleaning up external data.
- `УдалениеПомеченныхОбъектовПереопределяемый.ПриОпределенииОбъектовСКомандойПоказатьПомеченные(Объекты)` — add metadata objects whose list forms will have the commands “Show marked” / “Go to marked”.
- `СоединенияИБПереопределяемый.ПриОпределенииПараметровБлокировкиСеансов(ПараметрыБлокировкиСеансов)` — modify parameters when setting the IB lock.
- `РаботаВБезопасномРежимеПереопределяемый.ПриЗаполненииРазрешенийНаДоступКВнешнимРесурсам(ЗапросыРазрешений)` — fill default permissions. `ПриВключенииИспользованияПрофилейБезопасности()`, `ПриЗапросеСозданияПрофиляБезопасности(...)`, `ПриЗапросеУдаленияПрофиляБезопасности(...)`, `ПриПроверкеВозможностиИспользованияПрофилейБезопасности(Отказ)` — lifecycle hooks for security profiles.