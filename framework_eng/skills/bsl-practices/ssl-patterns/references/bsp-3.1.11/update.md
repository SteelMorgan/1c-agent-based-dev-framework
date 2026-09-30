# Updating the Database and Configuration Version

Subsystems **ОбновлениеВерсииИБ** (in the `БазоваяФункциональность` block) and
**ОбновлениеКонфигурации**. Covers update handlers (exclusive /
operational / deferred), data migration when the configuration or
library version changes, managing database versions through the `ВерсииПодсистем`
register, as well as interactive installation of updates and patches. The main task is to learn
how to correctly write code **inside** the update handler (the write wrapper),
register handlers, and not confuse `*Переопределяемый` modules (hooks -
implemented) with modules that need to be **called**.

## Modules

The `ОбновлениеИнформационнойБазы*` family (root + context suffix):

- `ОбновлениеИнформационнойБазы` — server, external connection, thick client.
  **Stable API**: starting updates, database versions, handler tables,
  write wrappers, logging.
- `ОбновлениеИнформационнойБазыКлиент` — client: deferred handler forms,
  progress indication, initiation of interactive update.
- `ОбновлениеИнформационнойБазыГлобальный` — global: ⚠️
  `ПроверитьСтатусОтложенногоОбновления` (region
  `СлужебныеПроцедурыИФункции`, backward compatibility is not guaranteed),
  called by name without a prefix.
- `ОбновлениеИнформационнойБазыВызовСервера` — server call from the client without
  form context.
- `ОбновлениеИнформационнойБазыПереопределяемый` — **hook**: server-side
  application configuration "hooks" (`ПередОбновлениемИнформационнойБазы`,
  `ПослеОбновленияИнформационнойБазы`, `ПриОпределенииНастроек`, etc.).
  БСП calls, application code implements.
- `ОбновлениеИнформационнойБазыКлиентПереопределяемый` — **hook**: client-side
  `ПриОпределенииВозможностиОбновления`,
  `ПриНажатииНаГиперссылкуВДокументеОписанияОбновлений`.
- `ОбновлениеИнформационнойБазыСлужебный` — ⚠️ service API (region
  `СлужебныйПрограммныйИнтерфейс`): internal iteration logic, database locking,
  background update. Backward compatibility is not guaranteed.
- `ОбновлениеИнформационнойБазыСлужебныйВызовСервера` /
  `ОбновлениеИнформационнойБазыСлужебныйПовтИсп` — ⚠️ service, do not call.

Subsystem **ОбновлениеКонфигурации** (separate): interactive installation
of updates and patches. Modules `ОбновлениеКонфигурации` (server),
`ОбновлениеКонфигурацииКлиент`, `ОбновлениеКонфигурацииГлобальный`,
`ОбновлениеКонфигурацииВызовСервера`.

⚠️ **Module `ОбновлениеИнформационнойБазыСервер` does NOT exist** — the server
module is called `ОбновлениеИнформационнойБазы` (without a suffix). Compare: for
scheduled jobs, conversely, the server one is `РегламентныеЗаданияСервер`
(`РегламентныеЗадания` without the suffix does not exist). Before calling,
check against the real common modules directory.

## Scenarios

### 1. Register an update handler

**Task:** describe a data migration handler for a specific version and
register it in the update handler table.

**Functions:**
`ОбновлениеИнформационнойБазы.НоваяТаблицаОбработчиковОбновления() Экспорт` —
Function (stable), returns `ТаблицаЗначений` with the full set of columns for
all execution modes.

**Parameters (handler table columns):**
- `Версия` (Строка) — version number for the transition on which the handler is
  executed, for example `"2.4.1.5"`. `"*"` — mandatory handler (on every
  update). Empty string — handler only for initial fill (then
  `НачальноеЗаполнение = Истина`).
- `Процедура` (Строка) — full name of the exported procedure, for example
  `"ОбновлениеИнформационнойБазыУПП.ЗаполнитьНовыйРеквизит"`.
- `РежимВыполнения` (Строка) — `"Монопольно"` (default, heavy migration),
  `"Оперативно"` (without locking the IB, light migration), `"Отложенно"` (in the
  background after the main cycle; requires `Идентификатор`, `БлокируемыеОбъекты`,
  `ПроцедураПроверки`).
- `НачальноеЗаполнение` (Булево) — `Истина`, the handler runs on an "empty" database.
- `Идентификатор` (УникальныйИдентификатор) — for a deferred handler.
- `БлокируемыеОбъекты` (Строка) — for deferred, for example
  `"Справочник.Контрагенты"`.
- `ПроцедураПроверки` (Строка) — for deferred, the handler completion check function.
- `Комментарий` (Строка) — description.

**Example:**
```bsl
// В прикладном модуле ОбновлениеИнформационнойБазыУПП:
Процедура ПриДобавленииОбработчиковОбновления(Обработчики) Экспорт
    Обработчик = Обработчики.Добавить();
    Обработчик.Версия          = "2.4.1.5";
    Обработчик.Процедура       = "ОбновлениеИнформационнойБазыУПП.ЗаполнитьНовыйРеквизит";
    Обработчик.РежимВыполнения = "Монопольно";

    Обработчик = Обработчики.Добавить();
    Обработчик.Версия          = "*";  // при каждом обновлении
    Обработчик.Процедура       = "ОбновлениеИнформационнойБазыУПП.ОбновитьКонтактнуюИнформацию";
    Обработчик.РежимВыполнения = "Отложенно";
    Обработчик.Идентификатор    = Новый УникальныйИдентификатор("a1b2c3d4-...");
    Обработчик.БлокируемыеОбъекты = "Справочник.Контрагенты";
    Обработчик.ПроцедураПроверки = "ОбновлениеИнформационнойБазыУПП.КонтрагентОбработан";
    Обработчик.Комментарий     = "Обновление КИ по новому формату";
КонецПроцедуры

Процедура ЗаполнитьНовыйРеквизит() Экспорт
    Запрос = Новый Запрос;
    Запрос.Текст = "ВЫБРАТЬ Ссылка ИЗ Справочник.Контрагенты ГДЕ НовыйРеквизит = """"";
    Выборка = Запрос.Выполнить().Выбрать();
    Пока Выборка.Следующий() Цикл
        Контрагент = Выборка.Ссылка.ПолучитьОбъект();
        Контрагент.НовыйРеквизит = "ЗначениеПоУмолчанию";
        ОбновлениеИнформационнойБазы.ЗаписатьДанные(Контрагент);
    КонецЦикла;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ⚠️ Handler registration is performed in `ПриДобавленииОбработчиковОбновления`
  of the **application** module `ОбновлениеИнформационнойБазы<Префикс>` (for example
  `…УПП`, `…БП`). The module `ОбновлениеИнформационнойБазыСлужебный` БСП contains
  its own `ПриДобавленииОбработчиковОбновления` — this is an internal БСП hook, not
  a place for application handlers.
- `Версия = "*"` — mandatory handler; runs on every update,
  independent of the previous IB version.
- The configuration version is stored in `Метаданные.Версия` and the `ВерсииПодсистем` register.

### 2. Safely write data from the update handler

**Task:** write an object / set of records / constant in the update handler without business logic and without registration on exchange plans (the migration must not spread across RIB nodes and must not trigger business logic that is not ready yet).

**Functions:**
`ОбновлениеИнформационнойБазы.ЗаписатьДанные(Знач Данные, Знач РегистрироватьНаУзлахПлановОбмена = Неопределено, Знач ВключитьБизнесЛогику = Ложь) Экспорт`
— Procedure (stable). Writes an object, a set of records, or a constant manager.
`ОбновлениеИнформационнойБазы.ЗаписатьОбъект(Знач Объект, Знач РегистрироватьНаУзлахПлановОбмена = Неопределено, Знач ВключитьБизнесЛогику = Ложь, ДокументРежимЗаписи = Неопределено) Экспорт`
— Procedure (stable). Writes an object with the ability to post the document.
`ОбновлениеИнформационнойБазы.ЗаписатьНаборЗаписей(Знач НаборЗаписей, Замещать = Истина, Знач РегистрироватьНаУзлахПлановОбмена = Неопределено, Знач ВключитьБизнесЛогику = Ложь) Экспорт`
— Procedure (stable).
`ОбновлениеИнформационнойБазы.УдалитьДанные(Знач Данные, Знач РегистрироватьНаУзлахПлановОбмена = Неопределено, Знач ВключитьБизнесЛогику = Ложь) Экспорт`
— Procedure (stable).

**Parameters:**
- `Данные` / `Объект` / `НаборЗаписей` (Arbitrary) — writable data.
- `РегистрироватьНаУзлахПлановОбмена` (Boolean / `Неопределено`) — `Неопределено`
  (default) — the update subsystem's standard behavior (usually **do not**
  register). `Истина` — force registration (the migration goes into
  exchange).
- `ВключитьБизнесЛогику` (Boolean) — `Ложь` (default) disables handlers of
  the object module and event subscriptions. `Истина` — enable it (e.g., for
  posting documents where the migration does not break the logic).
- `ДокументРежимЗаписи` (РежимЗаписиДокумента) — for `ЗаписатьОбъект`;
  `Неопределено` — normal write, `РежимЗаписиДокумента.Проведение` —
  posting.
- `Замещать` (Boolean) — for `ЗаписатьНаборЗаписей`; `Истина` (default) —
  replacement.

**Example:**
```bsl
Процедура ОбновитьРеквизитДокумента() Экспорт
    Выборка = Документы.Заказ.Выбрать();
    Пока Выборка.Следующий() Цикл
        ДокументОбъект = Выборка.ПолучитьОбъект();
        ДокументОбъект.НовыйРеквизит = "...";
        // Бизнес-логика ОТКЛЮЧЕНА, регистрация на планах обмена НЕ выполняется
        ОбновлениеИнформационнойБазы.ЗаписатьДанные(ДокументОбъект);
    КонецЦикла;
КонецПроцедуры

// Запись с проведением (business logic enabled)
ОбновлениеИнформационнойБазы.ЗаписатьОбъект(ДокументОбъект, , Истина, РежимЗаписиДокумента.Проведение);
```

**Nuances / anti-patterns:**
- ❌ `Объект.Записать()` directly in the handler — triggers business logic and
  exchange registration. Only the `ОбновлениеИнформационнойБазы.Записать*`
  wrappers.
- ❌ Use `ЗаписатьДанные` in ordinary application logic outside the update
  handler — the wrappers are intended only for migration code. For
  user-driven writing — `Объект.Записать()` with the full business logic.

### 3. Check object locking in the form

**Task:** lock editing of the object in the form until the deferred
update has processed it (standard BSP check).

**Functions:**
`ОбновлениеИнформационнойБазы.МетаданныеИОтборПоДанным(Данные, ДополнительныеПараметры = Неопределено) Экспорт`
— Function (stable), normalizes data for the locking check.
`ОбновлениеИнформационнойБазы.ДанныеОбновленыНаНовуюВерсиюПрограммы(МетаданныеИОтбор) Экспорт`
— Function (stable), returns `Булево`: `Истина` — the object has been updated and is available
for editing.

**Parameters:**
- `Данные` (СправочникОбъект / ДокументОбъект / … / ЛюбаяСсылка /
  ДанныеФормыСтруктура) — object or reference for normalization.
- `ДополнительныеПараметры` (Структура / `Неопределено`) — additional selection
  parameters.
- `МетаданныеИОтбор` (Структура) — result of `МетаданныеИОтборПоДанным`.

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    МетаданныеИОтбор = ОбновлениеИнформационнойБазы.МетаданныеИОтборПоДанным(Объект);
    Если Не ОбновлениеИнформационнойБазы.ДанныеОбновленыНаНовуюВерсиюПрограммы(МетаданныеИОтбор) Тогда
        Текст = НСтр("ru = 'Объект заблокирован для редактирования до завершения обновления.'");
        ОбщегоНазначения.СообщитьПользователю(Текст, , , , Отказ);
    КонецЕсли;
КонецПроцедуры
```

**Nuances / antipatterns:**
- `ДанныеОбновленыНаНовуюВерсиюПрограммы` — standard default check function; the
  locked objects are registered on the nodes of the `ОбновлениеИнформационнойБазы` exchange plan.
- For a non-standard check, you can register your own through the hook
  `ПриВыполненииПроверкиОбъектОбработан` of the module
  `ОбновлениеИнформационнойБазыПереопределяемый`.

### 4. Programmatically start the update (batch mode)

**Task:** start a non-interactive update from an external connection or
processing, after first clearing obsolete patches.

**Functions:**
`ОбновлениеКонфигурации.ИсправленияИзменены(ТолькоПроверка = Ложь) Экспорт` —
Function (stable). Removes obsolete patches and applies new ones. Returns
`ЕстьИзменения` (Булево), `ОписаниеИзменений`.
`ОбновлениеИнформационнойБазы.ВыполнитьОбновлениеИнформационнойБазы(ВыполнятьОтложенныеОбработчики = Ложь) Экспорт`
— Function (stable). Returns a string: `"Успешно"` / `"НеТребуется"` /
`"ОшибкаУстановкиМонопольногоРежима"`.

**Parameters:**
- `ТолькоПроверка` (Булево) — for `ИсправленияИзменены`: `Истина` only
  check, do not apply.
- `ВыполнятьОтложенныеОбработчики` (Булево) — `Истина` — deferred update
  is performed in the main loop (client-server mode only).

**Example:**
```bsl
// In processing called from an external connection
ОбновлениеКонфигурации.ИсправленияИзменены();  // remove obsolete patches
Результат = ОбновлениеИнформационнойБазы.ВыполнитьОбновлениеИнформационнойБазы();
Если Результат = "ОшибкаУстановкиМонопольногоРежима" Тогда
    // retry later or notify the administrator
КонецЕсли;
```

**Nuances / antipatterns:**
- ❌ Run `ВыполнитьОбновлениеИнформационнойБазы` without `ИсправленияИзменены`
  — accumulation of "dead" fixes and compatibility errors. Patches first.
- ❌ `ОбновлениеИнформационнойБазыВызовСервера.ВыполнитьОбновлениеИнформационнойБазы(Истина)`
  from client code while users are working — interactive update is
  needed via `ОбновлениеКонфигурацииКлиент.ПоказатьПоискИУстановкуОбновлений()`.
- When called with connected extensions that modify roles, the method
  will throw an exception.

### 5. Read and write the information base version

**Task:** find out the current version of the subsystem/configuration, record the version
without running handlers (e.g. to cancel the standard migration from another
program), register a new subsystem.

**Functions:**
`ОбновлениеИнформационнойБазы.ВерсияИБ(Знач ИдентификаторБиблиотеки) Экспорт` —
Function (stable), returns the saved version (String).
`ОбновлениеИнформационнойБазы.УстановитьВерсиюИБ(Знач ИдентификаторБиблиотеки, Знач НомерВерсии, Знач ЭтоОсновнаяКонфигурация) Экспорт`
— Procedure (stable).
`ОбновлениеИнформационнойБазы.ВерсииПодсистем() Экспорт` — Function (stable),
returns a versions table.
`ОбновлениеИнформационнойБазы.УстановитьВерсииПодсистем(ВерсииПодсистем) Экспорт`
— Procedure (stable).
`ОбновлениеИнформационнойБазы.ЗарегистрироватьНовуюПодсистему(ИмяПодсистемы, НомерВерсии = "") Экспорт`
— Procedure (stable). Registers a new subsystem **without** running
initial fill handlers. Call from `ПередОбновлениемИнформационнойБазы`.

**Parameters:**
- `ИдентификаторБиблиотеки` (String) — configuration or library name; for
the main configuration — `Метаданные.Имя`.
- `НомерВерсии` (String) — version number, e.g. `Метаданные.Версия`.
- `ЭтоОсновнаяКонфигурация` (Boolean) — `Истина` for the main configuration,
  `Ложь` for the library. Required.

**Example:**
```bsl
// Read the current version of the main configuration
ТекущаяВерсия = ОбновлениеИнформационнойБазы.ВерсияИБ(Метаданные.Имя);

// Write the version without running handlers (cancel the standard update)
ОбновлениеИнформационнойБазы.УстановитьВерсиюИБ(Метаданные.Имя, Метаданные.Версия, Истина);
```

**Nuances / anti-patterns:**
- ❌ `УстановитьВерсиюИБ(Метаданные.Имя, "1.0.0.0")` without the third parameter —
  the version will be written to the library branch. Always pass
  `ЭтоОсновнаяКонфигурация` (`Истина` for the main configuration).
- `ЗарегистрироватьНовуюПодсистему` does not run handlers — it is used
  to mark the subsystem as already at the current version (e.g. when migrating from
  another program, when migration is not needed).

### 6. Check the need for and progress of the update

**Task:** determine from application code whether an update is required, whether
it is currently running, and whether a deferred update has finished (so you can
safely deny writes or show a warning).

**Functions:**
`ОбновлениеИнформационнойБазы.НеобходимоОбновлениеИнформационнойБазы() Экспорт` —
Function (stable), `Булево`.
`ОбновлениеИнформационнойБазы.ВыполняетсяОбновлениеИнформационнойБазы() Экспорт`
— Function (stable), `Булево`.
`ОбновлениеИнформационнойБазы.ОтложенноеОбновлениеЗавершено(Знач ИменаПодсистем = Неопределено) Экспорт`
— Function (stable), `Булево`.
`ОбновлениеИнформационнойБазы.ПерезапуститьОтложенноеОбновление(Отбор = Неопределено) Экспорт`
— Procedure (stable).

**Parameters:**
- `ИменаПодсистем` (String / Array / `Неопределено`) — subsystem names for
  checking deferred update; `Неопределено` means all subsystems.
- `Отбор` (Structure / `Неопределено`) — for `ПерезапуститьОтложенноеОбновление`,
  e.g. `Новый Структура("ИмяОбработчика", "МойОбработчик")`.

**Example:**
```bsl
// Deny writing if an update is running
Если ОбновлениеИнформационнойБазы.ВыполняетсяОбновлениеИнформационнойБазы() Тогда
    Отказ = Истина;
    ОбщегоНазначения.СообщитьПользователю(НСтр("ru = 'Запись невозможна: выполняется обновление.'"));
КонецЕсли;

// Restart a previously deferred handler after fixing the data
ОбновлениеИнформационнойБазы.ПерезапуститьОтложенноеОбновление(
    Новый Структура("ИмяОбработчика", "МойОбработчик"));
```

**Nuances / anti-patterns:**
- `НеобходимоОбновлениеИнформационнойБазы` compares `Метаданные.Версия` with
  the version in the IB; `Истина` means there are handlers with a version higher than the saved one.
- `ОтложенноеОбновлениеЗавершено` is useful for blocking expensive operations
  that depend on complete data migration.

### 7. Implement update override hooks

**Task:** implement BSP hooks in the application configuration: actions before
update, after update, override of settings and checks.

**Functions (hooks, module `*Переопределяемый`):**
`ОбновлениеИнформационнойБазыПереопределяемый.ПередОбновлениемИнформационнойБазы() Экспорт`
— Procedure (hook), server. Called **before** handlers are started.
`ОбновлениеИнформационнойБазыПереопределяемый.ПослеОбновленияИнформационнойБазы(Знач ПредыдущаяВерсияИБ, Знач ТекущаяВерсияИБ, Знач ИтерацииОбновления, ВыводитьОписаниеОбновлений, Знач МонопольныйРежим) Экспорт`
— Procedure (hook), server. Called **after** the update completes.
`ОбновлениеИнформационнойБазыПереопределяемый.ПриОпределенииНастроек(Параметры) Экспорт`
— Procedure (hook), overrides the common settings of the update subsystem.
`ОбновлениеИнформационнойБазыПереопределяемый.ПриВыполненииПроверкиОбъектОбработан(ПолноеИмяОбъекта, БлокироватьИзменение, ТекстСообщения) Экспорт`
— Procedure (hook), called when checking whether an object has been "processed" during
update; application code can set `БлокироватьИзменение = Истина` and the
message text to block changes to the object until the update handler finishes.
`ОбновлениеИнформационнойБазыКлиентПереопределяемый.ПриОпределенииВозможностиОбновления(Знач ВерсияДанных) Экспорт`
— Procedure (hook), client. Checks whether update is possible in
`ПередНачаломРаботыСистемы`.

**Parameters:**
- `ПредыдущаяВерсияИБ` / `ТекущаяВерсияИБ` (String) — versions before/after.
- `ИтерацииОбновления` (Array of Structure) — data about the update iterations
  of each library/configuration. Structure keys: `Подсистема` (String),
  `Версия` (String), `ЭтоОсновнаяКонфигурация` (Boolean), `Обработчики`
  (ValueTable), `ВыполненныеОбработчики` (ValueTree),
  `ИмяОсновногоСерверногоМодуля` (String), `ОсновнойСерверныйМодуль`
  (CommonModule), `ПредыдущаяВерсия` (String), etc.
- `ВыводитьОписаниеОбновлений` (Boolean) — output: `Ложь` disables showing the form
  with the change description.
- `МонопольныйРежим` (Boolean) — exclusive mode flag.
- `Параметры` (Structure) — update subsystem settings.
- `ВерсияДанных` (String) — IB data version for the client-side check.

**Example:**
```bsl
// In the application module ОбновлениеИнформационнойБазыПереопределяемый:
Процедура ПередОбновлениемИнформационнойБазы() Экспорт
    ВерсииПодсистем = ОбновлениеИнформационнойБазы.ВерсииПодсистем();
    Если ВерсииПодсистем.Количество() > 0
        И ВерсииПодсистем.Найти(Метаданные.Имя, "ИмяПодсистемы") = Неопределено Тогда
        // Cancel the standard transition from another program — register it as current
        ОбновлениеИнформационнойБазы.ЗарегистрироватьНовуюПодсистему(Метаданные.Имя, Метаданные.Версия);
    КонецЕсли;
КонецПроцедуры

Процедура ПослеОбновленияИнформационнойБазы(Знач ПредыдущаяВерсияИБ, Знач ТекущаяВерсияИБ,
    Знач ИтерацииОбновления, ВыводитьОписаниеОбновлений, Знач МонопольныйРежим) Экспорт
    ВыводитьОписаниеОбновлений = Ложь; // отключить форму описания изменений
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Call `ОбновлениеИнформационнойБазыПереопределяемый.ПередОбновлениемИнформационнойБазы()`
  from application code — the `*Переопределяемый` module is only implemented,
  BSP invokes the hooks itself. To record the version before the update, use the direct API
  `УстановитьВерсиюИБ`.
- ❌ Use `ОбновлениеИнформационнойБазыСлужебный` for application tasks —
  service, backward compatibility is not guaranteed. Stable counterparts are
  `ВерсияИБ`, `УстановитьВерсиюИБ`, `ВерсииПодсистем`.

## Rare methods

Methods that occur less often (from companion `*-key-methods.md`), without a full
scenario:

- `ОбновлениеИнформационнойБазы.НоваяТаблицаОбработчиковОбновления()` — see
  scenario 1; returns `ТаблицаЗначений` with a full set of columns for all
  modes (`Монопольно` / `Оперативно` / `Отложенно` / `Параллельно`).
- `ОбновлениеИнформационнойБазы.ДанныеОбновленыНаНовуюВерсиюПрограммы(МетаданныеИОтбор)`
  — see scenario 3; standard lock-check function for
  `ПриСозданииНаСервере` of the object form.
- `ОбновлениеИнформационнойБазы.ЗаписатьОшибкуВЖурналРегистрации(СсылкаМетаданные, Знач Представление, ИнформацияОбОшибке = Неопределено, Уровень = Неопределено)`
— Procedure (stable), writes an error to the log tied to the update event.
  For informational events —
  `ОбновлениеИнформационнойБазы.ЗаписатьСобытиеВЖурналРегистрации`
  (Procedure, returns nothing); `СобытиеЖурналаРегистрации()` —
  a separate Function, returns a log event string for use
  in update handlers.
- `ОбновлениеИнформационнойБазы.ЭтоВызовИзОбработчикаОбновления(РежимВыполненияОбработчика = "")`
— Function (stable), `Булево` — checks that the code is running in the context
  of the update handler (to disable extra logic).
- `ОбновлениеИнформационнойБазы.ОбработчикиОбновления(Отбор = Неопределено)` /
  `ОбновляемыеОбъекты()` — reads registered handlers and
  updateable objects for deferred update.
- `ОбновлениеКонфигурацииКлиент.ПоддерживаетсяУстановкаОбновлений()` — Function
  (stable), client. Returns a structure with `Поддерживается` — whether
  an update can be installed interactively (Windows OS + Configurator + administrator
  rights; not service model).
- `ОбновлениеКонфигурацииКлиент.ПоказатьПоискИУстановкуОбновлений(ПараметрыУстановкиОбновлений = Неопределено)`
— Procedure (stable), client. Opens the form for interactive installation
  of an update (with backup and locks).
- ⚠️ `ОбновлениеИнформационнойБазыСлужебный.ПараметрыОбновления()` — Function,
  region `СлужебныеПроцедурыИФункции` (⚠️ service). Internal structure
  of update parameters; only for debugging and rare system scenarios, not
  for application code — it will break when БСП is updated.