# Administrative tools: sessions, marked deletion, security profiles

Three administrative infobase subsystems: **ЗавершениеРаботыПользователей**
(common modules `СоединенияИБ*` - infobase and data area session locking,
connection information, shutdown mode), **УдалениеПомеченныхОбъектов**
(`УдалениеПомеченныхОбъектов*` - programmatic/interactive deletion with
referential integrity control, scheduled deletion, marked-object visibility in
list forms), **ПрофилиБезопасности** (`РаботаВБезопасномРежиме*` - permissions
for external resources: file system directories, COM classes, internet
resources, external modules/components, privileged mode).

## Modules

**ЗавершениеРаботыПользователей:**

- `СоединенияИБ` - **stable server-side API**: infobase locking, data area
  session locking, lock parameters, connection information. Server, Thick
  client, External connection.
- `СоединенияИБКлиент` - **stable client-side API**: shutdown mode, flag for
  terminating sessions, administration parameters form. Thin / Thick client.
- `СоединенияИБКлиентСервер` / `СоединенияИБВызовСервера` - ⚠️ service.
- `СоединенияИБПереопределяемый` - **hook**
  `ПриОпределенииПараметровБлокировкиСеансов`.

**УдалениеПомеченныхОбъектов:**

- `УдалениеПомеченныхОбъектов` - **stable server-side API**: get marked
  objects, references to objects being deleted, programmatic deletion, display
  settings, schedule mode. Server, Thick client, External connection.
- `УдалениеПомеченныхОбъектовКлиент` - **stable client-side API**:
  interactive deletion, marked-object visibility, schedule.
- `УдалениеПомеченныхОбъектовПереопределяемый` - **hooks** before/after group
  deletion, defining objects with the "Show marked" command.
- `УдалениеПомеченныхОбъектовПовтИсп` / `…Служебный` /
  `…СлужебныйВызовСервера` / `…СлужебныйКлиентСервер` - ⚠️ service.

**ПрофилиБезопасности:**

- `РаботаВБезопасномРежиме` - **stable server-side API**: constructors for
  `РазрешениеНа*`, `ЗапросНаИспользованиеВнешнихРесурсов`,
  `ЗапросНаОтменуРазрешений…`, `УстановленБезопасныйРежим`. Server, Thick
  client, External connection.
- `РаботаВБезопасномРежимеКлиент` - **stable client-side API**:
  `ПрименитьЗапросыНаИспользованиеВнешнихРесурсов`,
  `ОткрытьДиалогНастройкиИспользованияПрофилейБезопасности`.
- `РаботаВБезопасномРежимеПереопределяемый` - **hooks**: checking whether
  security profiles can be used/configured, filling permissions,
  profile creation/deletion requests.

⚠️ The `ЗавершениеРаботыПользователей` subsystem is implemented in modules with
the root `СоединенияИБ` (not `ЗавершениеРаботы…`), while `ПрофилиБезопасности`
is implemented in modules with the root `РаботаВБезопасномРежиме`. There are
no modules with the literal subsystem names - a typical mistake is to look for
`ЗавершениеРаботыПользователей.Установить…`.

## Scenarios

### 1. Lock the infobase before administration and unlock it afterward

**Task:** before an update/migration, set a connection lock on the infobase
with a delay and duration, and guarantee that it is released even if an error
occurs.

**Functions:**
`СоединенияИБ.УстановитьБлокировкуСоединений(Знач ТекстСообщения = "", Знач КодРазрешения = "КодРазрешения", Знач ОжиданиеНачалаБлокировки = 0, Знач ДлительностьБлокировки = 0) Экспорт`
- Function -> Boolean (success).
`СоединенияИБ.РазрешитьРаботуПользователей() Экспорт` - Function -> Boolean.
`СоединенияИБ.УстановленаБлокировкаСоединений() Экспорт` - Function -> Boolean.
- region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client,
  External connection.

**Parameters:**
- `ТекстСообщения` (String) - the text shown to connecting users if access is
  denied.
- `КодРазрешения` (String) - the code used by the administrator to enter the
  locked infobase; default is `"КодРазрешения"`.
- `ОжиданиеНачалаБлокировки` (Number, minutes) - delay before the lock takes
  effect (gives users time to finish work).
- `ДлительностьБлокировки` (Number, minutes) - how long to keep the lock; `0`
  means until explicitly released.

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
- ❌ Set a lock without `Попытка…Исключение` - if the code fails between setting
  and releasing it, the infobase will remain locked. Always release it
  reliably.
- Nuance: if called from a session with separators set,
  `УстановитьБлокировкуСоединений` sets a **data area session lock**, not a
  lock for the entire infobase. For an explicit data-area lock, use
  `УстановитьБлокировкуСеансовОбластиДанных` (scenario 2).
- Before write/long-running operations, check
  `УстановленаБлокировкаСоединений()` and warn the user.

### 2. Lock data area sessions (service model)

**Task:** in the service model, lock sessions of one data area for a time
window, then read the current lock.

**Functions:**
`СоединенияИБ.НовыеПараметрыБлокировкиСоединений() Экспорт` - Function ->
parameters structure.
`СоединенияИБ.УстановитьБлокировкуСеансовОбластиДанных(Знач Параметры, Знач ПоМестномуВремени = Истина, Знач ОбластьДанных = -1) Экспорт` - Procedure.
`СоединенияИБ.ПолучитьБлокировкуСеансовОбластиДанных(Знач ПоМестномуВремени = Истина) Экспорт` - Function -> Structure.
`СоединенияИБ.ПараметрыБлокировкиСеансов(Знач ПолучитьКоличествоСеансов = Ложь) Экспорт` - Function -> infobase lock parameters structure.
- region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client,
  External connection.

**Parameters:**
- `Параметры` (Structure, see `НовыеПараметрыБлокировкиСоединений`) - contains
  `Начало`, `Конец` (Date), `Сообщение` (String), `Установлена` (Boolean),
  `КодРазрешения` (String).
- `ПоМестномуВремени` (Boolean) - `Истина` -> start/end in the session's local
  time; `Ложь` -> in UTC.
- `ОбластьДанных` (Number) - area number; `-1` means current (from session
  separators). From a session with separators, only a matching value can be
  passed, or it can be omitted; from a session without separators, it is
  required.

**Example:**
```bsl
Параметры = СоединенияИБ.НовыеПараметрыБлокировкиСоединений();
Параметры.Начало     = '20260101230000';  // 23:00 1 January
Параметры.Конец      = '20260102060000';  // 06:00 2 January
Параметры.Сообщение  = "Data area locked for update";
Параметры.Установлена = Истина;

// Set a lock for data area #3
СоединенияИБ.УстановитьБлокировкуСеансовОбластиДанных(Параметры, Истина, 3);

// Release - same procedure with Установлена = Ложь
Параметры.Установлена = Ложь;
СоединенияИБ.УстановитьБлокировкуСеансовОбластиДанных(Параметры, Истина, 3);

// Read the current lock
Текущая = СоединенияИБ.ПолучитьБлокировкуСеансовОбластиДанных(Истина);
```

**Nuances / anti-patterns:**
- ❌ Confuse `УстановитьБлокировкуСоединений` (entire infobase) with
  `УстановитьБлокировкуСеансовОбластиДанных` (one area in SaaS). These are
  different lock levels.
- `РазрешитьРаботуПользователей` removes the infobase lock; for data areas, the
  release is a repeated call to
  `УстановитьБлокировкуСеансовОбластиДанных` with
  `Параметры.Установлена = Ложь`.
- `ПараметрыБлокировкиСеансов(Истина)` returns a structure with the
  `КоличествоСеансов` field - useful for checks before locking.

### 3. Shutdown mode and administration parameters (client)

**Task:** from client code, enable user shutdown mode, mark other sessions for
termination, open the infobase/cluster administration parameters input form;
get connection information.

**Functions:**
`СоединенияИБКлиент.УстановитьРежимЗавершенияРаботыПользователей(Знач ЗавершитьРаботу) Экспорт` - Procedure.
`СоединенияИБКлиент.УстановитьПризнакЗавершитьВсеСеансыКромеТекущего(Значение) Экспорт` - Procedure.
`СоединенияИБКлиент.ПоказатьПараметрыАдминистрирования(ОписаниеОповещенияОЗакрытии, ЗапрашиватьПараметрыАдминистрированияИБ, ЗапрашиватьПараметрыАдминистрированияКластера, ПараметрыАдминистрирования = Неопределено, Заголовок = "", ПоясняющаяНадпись = "") Экспорт` - Procedure.
`СоединенияИБ.ИнформацияОСоединениях(ПолучатьСтрокуСоединения = Ложь, СообщенияДляЖурналаРегистрации = Неопределено, ПортКластера = 0) Экспорт` - Function -> Structure.
- client-side methods - region `ПрограммныйИнтерфейс` (stable), Thin/Thick
  client;
  `ИнформацияОСоединениях` - server-side, Server/Thick client/External
  connection.

**Parameters:**
- `ЗавершитьРаботу` (Boolean) - `Истина` enables shutdown mode.
- `Значение` (Boolean) - flag "terminate all sessions except the current one".
- `ОписаниеОповещенияОЗакрытии` (ОписаниеОповещения) - handler after closing
  the parameters form.
- `ЗапрашиватьПараметрыАдминистрированияИБ` / `…Кластера` (Boolean) - which
  parameter groups to request.
- `ПараметрыАдминистрирования` (Structure) - initial values.
- `ПолучатьСтрокуСоединения` (Boolean) - add the connection string to the
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
    // Do not terminate the current session
    СоединенияИБКлиент.УстановитьПризнакЗавершитьВсеСеансыКромеТекущего(Истина);

    // Open the infobase administration parameters form
    Оповещение = Новый ОписаниеОповещения("ПослеПараметровАдминистрирования", ЭтотОбъект);
    СоединенияИБКлиент.ПоказатьПараметрыАдминистрирования(Оповещение, Истина, Ложь);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Call `ПоказатьПараметрыАдминистрирования` without checking the subsystem -
  if `ЗавершениеРаботыПользователей` is not implemented, an error will occur.
  Check `ОбщегоНазначенияКлиент.ПодсистемаСуществует("СтандартныеПодсистемы.ЗавершениеРаботыПользователей")`
  first.
- `ИнформацияОСоединениях` is a server-side method; from client code, call it
  in `&НаСервере` and pass the result back to the client.

### 4. Programmatically delete marked objects with result control

**Task:** get objects marked for deletion (with metadata filtering), delete
them with referential integrity control, and correctly handle blocking
references.

**Functions:**
`УдалениеПомеченныхОбъектов.ПомеченныеНаУдаление(Знач ОтборМетаданных = Неопределено, ИскатьТехнологическиеОбъекты = Ложь) Экспорт` - Function -> Array.
`УдалениеПомеченныхОбъектов.УдалитьПомеченныеОбъекты(УдаляемыеОбъекты, РежимУдаления = "Стандартный") Экспорт` - Function -> Structure.
`УдалениеПомеченныхОбъектов.СсылкиНаУдаляемыеОбъекты(Источник) Экспорт` - Function -> Map.
- region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client,
  External connection.

**Parameters:**
- `ОтборМетаданных` (Value list of String / `Неопределено`) - full metadata
  names, e.g. `"Справочник.Номенклатура"`; `Неопределено` means no filter.
- `ИскатьТехнологическиеОбъекты` (Boolean) - include technical objects.
- `УдаляемыеОбъекты` (Array of *Reference) - objects to delete.
- `РежимУдаления` (String) - `"Стандартный"` (control + multiuser operation),
  `"Монопольный"` (with exclusive mode set; if it fails - exception),
  `"Упрощенный"` (control only on unmarked objects; in marked objects,
  references to objects being deleted are **cleared**).
- `Источник` (CatalogObject / DocumentObject / InformationRegisterRecordSet) -
  object in which to look for references to the objects being deleted.

**Example:**
```bsl
// All marked objects
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
            "Cannot delete " + Строка(СтрокаПрепятствия.УдаляемыйСсылка)
            + ": used in " + Строка(СтрокаПрепятствия.МестоИспользования));
    КонецЦикла;
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Call `УдалитьПомеченныеОбъекты` inside an explicit transaction - the method
  manages transactions and batching itself. An external transaction will cause
  conflicts.
- ❌ Ignore the result - objects may not be deleted because of referential
  integrity; always check `Результат.Успешно` and iterate over
  `ПрепятствующиеУдалению` (`УдаляемыйСсылка`, `МестоИспользования`,
  `ОписаниеОшибки`, `ПодробноеОписаниеОшибки`).
- ❌ `УдалениеПомеченныхОбъектовСлужебный.<Метод>` - a service module,
  backward compatibility is not guaranteed. Use only the stable API.
- `СсылкиНаУдаляемыеОбъекты` for record sets subordinate to the recorder
  returns an empty list - this is intentional for performance and to keep
  movement generation mechanisms running smoothly.

### 5. Integrate marked-object visibility into a list form

**Task:** in a form with a dynamic list, configure the visibility of objects
marked for deletion and the state of the "Show marked" button.

**Functions:**
`УдалениеПомеченныхОбъектов.ПриСозданииНаСервере(Форма, Знач НастройкиОтображенияПомеченныхОбъектов) Экспорт` - Procedure.
`УдалениеПомеченныхОбъектов.НастройкиОтображенияПомеченныхОбъектов() Экспорт` - Function -> Value table (columns `ИмяЭлементаФормы`, `ТипыМетаданных`, `ИмяСписка`).
`УдалениеПомеченныхОбъектов.УстановитьПометкуКомандыПоказатьПомеченные(Форма, ТаблицаФормы, КнопкаФормы) Экспорт` - Procedure.
- region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client,
  External connection.

**Parameters:**
- `Форма` (Client application form) - form with a dynamic list.
- `НастройкиОтображенияПомеченныхОбъектов` (see
  `НастройкиОтображенияПомеченныхОбъектов` / `ТаблицаФормы`) - either a
  settings table or the form element of a dynamic list (for one list).
- `ТаблицаФормы` / `КнопкаФормы` - form elements for
  `УстановитьПометкуКомандыПоказатьПомеченные`.

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    // Option 1: one dynamic list - pass the form element
    УдалениеПомеченныхОбъектов.ПриСозданииНаСервере(ЭтотОбъект, Элементы.Список);
КонецПроцедуры

&НаСервере
Процедура ПриСозданииНаСервере_НесколькоСписков(Отказ, СтандартнаяОбработка)
    // Option 2: several lists - settings table
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
- The second parameter of `ПриСозданииНаСервере` accepts **either** a form
  element (TableForm, for one list) **or** the settings table from
  `НастройкиОтображенияПомеченныхОбъектов` (for multiple lists). Do not pass an
  arbitrary structure - only these two variants.
- `ТипыМетаданных` (Value list of String) - used to open the marked-items list
  with a preset type filter.

### 6. Scheduled deletion by schedule

**Task:** read the scheduled deletion settings for marked objects and change
the usage flag.

**Functions:**
`УдалениеПомеченныхОбъектов.РежимУдалятьПоРасписанию() Экспорт` - Function ->
Structure (`Расписание`, `Использование`).
`УдалениеПомеченныхОбъектов.ЗначениеФлажкаУдалятьПоРасписанию() Экспорт` - ⚠️
deprecated (region `УстаревшиеПроцедурыИФункции`); alternative -
`РежимУдалятьПоРасписанию`.
- server-side. The client-side checkbox toggle wrapper is
  `УдалениеПомеченныхОбъектовКлиент.ПриИзмененииФлажкаУдалятьПоРасписанию(АвтоматическиУдалятьПомеченныеОбъекты, ОповещениеОбИзменении = Неопределено) Экспорт`.

**Parameters:**
- `АвтоматическиУдалятьПомеченныеОбъекты` (Boolean) - new checkbox value.

**Example:**
```bsl
// Server: read the mode
Режим = УдалениеПомеченныхОбъектов.РежимУдалятьПоРасписанию();
Если Режим.Использование Тогда
    // Schedule = Режим.Расписание (see РегламентныеЗаданияСервер.РасписаниеРегламентногоЗадания)
КонецЕсли;

// Client: checkbox change handler in the settings form
&НаКлиенте
Процедура АвтоматическиУдалятьПомеченныеОбъектыПриИзменении(Элемент)
    УдалениеПомеченныхОбъектовКлиент.ПриИзмененииФлажкаУдалятьПоРасписанию(
        Элементы.АвтоматическиУдалятьПомеченныеОбъекты.Проверять);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Use `ЗначениеФлажкаУдалятьПоРасписанию` in new code - the method is
  deprecated (region `УстаревшиеПроцедурыИФункции`). Replacement -
  `РежимУдалятьПоРасписанию`.
- The schedule is stored in the settings of the scheduled job
  `УдалениеПомеченныхОбъектов`; for interactive schedule changes use
  `УдалениеПомеченныхОбъектовКлиент.НачатьИзменениеРасписанияРегламентногоЗадания(ОповещениеОбИзменении)`.
- The deletion procedure itself is launched by a scheduled job; programmatically
  calling `УдалитьПомеченныеОбъекты` (scenario 4) and the scheduled timetable
  are different scenarios: the first is one-time deletion, the second is
  background scheduled deletion.

### 7. Request permissions for external resources (security profiles)

**Task:** before making an external call (HTTP, COM class, file system
directory, external component), request security profile permissions and apply
them on the client through a dialog.

**Functions:**
`РаботаВБезопасномРежиме.РазрешениеНаИспользованиеИнтернетРесурса(Знач Протокол, Знач Адрес, Знач Порт = Неопределено, Знач Описание = "") Экспорт` - Function -> XDTOObject.
`РаботаВБезопасномРежиме.РазрешениеНаИспользованиеКаталогаФайловойСистемы(Знач Адрес, Знач ЧтениеДанных = Ложь, Знач ЗаписьДанных = Ложь, Знач Описание = "") Экспорт` - Function -> XDTOObject.
`РаботаВБезопасномРежиме.РазрешениеНаСозданиеCOMКласса(Знач ProgID, Знач CLSID, Знач ИмяКомпьютера = "", Знач Описание = "") Экспорт` - Function -> XDTOObject.
`РаботаВБезопасномРежиме.РазрешениеНаИспользованиеВнешнейКомпоненты(Знач ИмяМакета, Знач Описание = "") Экспорт` / `РазрешениеНаИспользованиеВнешнегоМодуля(Знач Имя, Знач КонтрольнаяСумма, Знач Описание = "") Экспорт` / `РазрешениеНаИспользованиеКаталогаВременныхФайлов(…) Экспорт` / `РазрешениеНаИспользованиеКаталогаПрограммы(…) Экспорт` / `РазрешениеНаИспользованиеПриложенияОперационнойСистемы(…) Экспорт` / `РазрешениеНаИспользованиеПривилегированногоРежима(Знач Описание = "") Экспорт`.
`РаботаВБезопасномРежиме.ЗапросНаИспользованиеВнешнихРесурсов(Знач НовыеРазрешения, Знач Владелец = Неопределено, Знач РежимЗамещения = Истина) Экспорт` - Function.
`РаботаВБезопасномРежиме.УстановленБезопасныйРежим() Экспорт` - Function ->
Boolean.
`РаботаВБезопасномРежимеКлиент.ПрименитьЗапросыНаИспользованиеВнешнихРесурсов(Знач Идентификаторы, ФормаВладелец, ОповещениеОЗакрытии) Экспорт` - Procedure.
- region `#Область ПрограммныйИнтерфейс` (stable). Server-side methods -
  Server/Thick client/External connection; client wrapper - Thin/Thick client.

**Parameters:**
- `Протокол` (String) - `IMAP`, `POP3`, `SMTP`, `HTTP`, `HTTPS`, `FTP`, `FTPS`,
  `WS`, `WSS`.
- `Адрес` (String) - resource address without the protocol.
- `Порт` (Number) - port number.
- `ProgID` / `CLSID` (String) - COM class identifiers (e.g.
  `"Excel.Application"`).
- `ИмяМакета` (String) - name of the external component template in the
  configuration.
- `НовыеРазрешения` (Array) - permission objects from `РазрешениеНа*`.
- `Владелец` (AnyReference) - infobase object logically associated with the
  permissions (e.g. a catalog item `ТомаХраненияФайлов` for volume directories).
- `РежимЗамещения` (Boolean) - `Истина` replaces the owner's previous
  permissions.
- `Описание` (String) - reason for the request (visible to the administrator).

**Example:**
```bsl
// Server: prepare a request for HTTP access to the API and the upload folder
Разрешения = Новый Массив;
Разрешения.Добавить(РаботаВБезопасномРежиме.РазрешениеНаИспользованиеИнтернетРесурса(
    "HTTPS", "api.example.com", 443, "Запрос курсов валют"));
Разрешения.Добавить(РаботаВБезопасномРежиме.РазрешениеНаИспользованиеКаталогаФайловойСистемы(
    "D:\Uploads", , Истина, "Каталог выгрузки"));

// Create the request (applied in the next session/profile refresh)
Идентификаторы = РаботаВБезопасномРежиме.ЗапросНаИспользованиеВнешнихРесурсов(
    Разрешения, СправочникСсылка.ИнтеграцияСAPI);

// Client: apply requests through the security profiles dialog
РаботаВБезопасномРежимеКлиент.ПрименитьЗапросыНаИспользованиеВнешнихРесурсов(
    Идентификаторы, ЭтаФорма, ОписаниеОповещения);
```

**Nuances / anti-patterns:**
- ❌ Perform an external call without requesting permission in the security
  profile - when profiles are enabled, the call will fail with an access error.
  First call `ЗапросНаИспользованиеВнешнихРесурсов`, then apply it through
  `ПрименитьЗапросыНаИспользованиеВнешнихРесурсов` (client dialog).
- `УстановленБезопасныйРежим()` checks safe mode, **ignoring** the security
  profile with the configuration privilege level - useful for conditional code.
- There are `ЗапросНаОтменуРазрешенийИспользованияВнешнихРесурсов(Владелец, ОтменяемыеРазрешения)`
  and `ЗапросНаОчисткуРазрешенийИспользованияВнешнихРесурсов(Владелец)` - for
  revoking permissions that are no longer needed (e.g. when removing an
  integration).
- `Владелец` links permissions to an infobase object - when the owner is
  deleted, BSP can clear its permissions.

## Rare methods

Other stable methods (full signatures - via
`python scripts/bsp_api.py method <Имя> --src src/cf`):

- `СоединенияИБ.ИнформацияОСоединениях(ПолучатьСтрокуСоединения = Ложь, СообщенияДляЖурналаРегистрации = Неопределено, ПортКластера = 0)` - information about current connections; `СообщенияДляЖурналаРегистрации` (Value list) - logs events to the journal.
- `УдалениеПомеченныхОбъектовКлиент.НачатьУдалениеПомеченных(УдаляемыеОбъекты, ПараметрыУдаления = Неопределено, Владелец = Неопределено, ОписаниеОповещенияОЗакрытии = Неопределено)` - open the interactive deletion form; `ПараметрыУдаления` - from `УдалениеПомеченныхОбъектовКлиент.ПараметрыИнтерактивногоУдаления()`.
- `УдалениеПомеченныхОбъектовКлиент.ПоказатьПомеченныеНаУдаление(Форма, ТаблицаФормы, КнопкаФормы)` / `ПерейтиКПомеченнымНаУдаление(Форма, ТаблицаФормы = Неопределено)` - toggle marked-object visibility and navigate to the deletion workspace.
- `РаботаВБезопасномРежиме.ЗапросыОбновленияРазрешенийКонфигурации(Знач ВключаяЗапросСозданияПрофиляИБ = Истина)` - pending permission update requests; `КонтрольныеСуммыФайловКомплектаВнешнейКомпоненты(Знач ИмяМакета)` - component package checksums.

Override hooks (modules `*Переопределяемый`, region `ПрограммныйИнтерфейс` -
**BSP calls, application code implements**):

- `УдалениеПомеченныхОбъектовПереопределяемый.ПередУдалениемГруппыОбъектов(Контекст, УдаляемыеОбъекты)` - **outside the transaction** before deleting a group; `Контекст` can be initialized for transfer to `ПослеУдаления`.
- `УдалениеПомеченныхОбъектовПереопределяемый.ПослеУдаленияГруппыОбъектов(Контекст, Успешно)` - **outside the transaction** afterward; for logging and external data cleanup.
- `УдалениеПомеченныхОбъектовПереопределяемый.ПриОпределенииОбъектовСКомандойПоказатьПомеченные(Объекты)` - add metadata for objects whose list forms will have the "Show marked" / "Go to marked" commands.
- `СоединенияИБПереопределяемый.ПриОпределенииПараметровБлокировкиСеансов(ПараметрыБлокировкиСеансов)` - modify parameters when setting an infobase lock.
- `РаботаВБезопасномРежимеПереопределяемый.ПриЗаполненииРазрешенийНаДоступКВнешнимРесурсам(ЗапросыРазрешений)` - fill default permissions. `ПриВключенииИспользованияПрофилейБезопасности()`, `ПриЗапросеСозданияПрофиляБезопасности(...)`, `ПриЗапросеУдаленияПрофиляБезопасности(...)`, `ПриПроверкеВозможностиИспользованияПрофилейБезопасности(Отказ)` - profile lifecycle hooks.
