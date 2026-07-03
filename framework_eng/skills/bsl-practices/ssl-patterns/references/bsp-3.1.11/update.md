# IB and Configuration Version Update

Subsystems **ОбновлениеВерсииИБ** (in the `БазоваяФункциональность` block) and
**ОбновлениеКонфигурации**. Covers update handlers (exclusive /
interactive / deferred), data migration when the configuration or
library version changes, IB version management through the `ВерсииПодсистем`
register, as well as interactive installation of updates and patches. The main task is to learn
how to correctly write code **inside** the update handler (record wrapper),
register handlers, and not confuse `*Переопределяемый` modules (hooks -
implemented) with modules that need to be **called**.

## Modules

The `ОбновлениеИнформационнойБазы*` family (root + context suffix):

- `ОбновлениеИнформационнойБазы` - server, external connection, thick client.
  **Stable API**: starting the update, IB versions, handler tables,
  record wrappers, logging.
- `ОбновлениеИнформационнойБазыКлиент` - client: deferred handler forms,
  progress indication, initiation of interactive update.
- `ОбновлениеИнформационнойБазыГлобальный` - global: ⚠️
  `ПроверитьСтатусОтложенногоОбновления` (region
  `СлужебныеПроцедурыИФункции`, backward compatibility is not guaranteed),
  called by name without a prefix.
- `ОбновлениеИнформационнойБазыВызовСервера` - server call from the client without
  form context.
- `ОбновлениеИнформационнойБазыПереопределяемый` - **hook**: server-side
  application configuration "hooks" (`ПередОбновлениемИнформационнойБазы`,
  `ПослеОбновленияИнформационнойБазы`, `ПриОпределенииНастроек`, etc.).
  БСП calls, application code implements.
- `ОбновлениеИнформационнойБазыКлиентПереопределяемый` - **hook**: client-side
  `ПриОпределенииВозможностиОбновления`,
  `ПриНажатииНаГиперссылкуВДокументеОписанияОбновлений`.
- `ОбновлениеИнформационнойБазыСлужебный` - ⚠️ service API (region
  `СлужебныйПрограммныйИнтерфейс`): internal iteration logic, IB locking,
  background update. Backward compatibility is not guaranteed.
- `ОбновлениеИнформационнойБазыСлужебныйВызовСервера` /
  `ОбновлениеИнформационнойБазыСлужебныйПовтИсп` - ⚠️ service, do not call.

Subsystem **ОбновлениеКонфигурации** (separate): interactive installation
of updates and patches. Modules `ОбновлениеКонфигурации` (server),
`ОбновлениеКонфигурацииКлиент`, `ОбновлениеКонфигурацииГлобальный`,
`ОбновлениеКонфигурацииВызовСервера`.

⚠️ **Module `ОбновлениеИнформационнойБазыСервер` does NOT exist** - the server
module is called `ОбновлениеИнформационнойБазы` (without a suffix). Compare: for
scheduled jobs, conversely, the server one is `РегламентныеЗаданияСервер`
(`РегламентныеЗадания` without a suffix does not exist). Before calling,
check against the actual shared modules directory.

## Scenarios

### 1. Register an update handler

**Task:** describe a data migration handler for a specific version and
register it in the update handlers table.

**Functions:**
`ОбновлениеИнформационнойБазы.НоваяТаблицаОбработчиковОбновления() Экспорт` —
Function (stable), returns `ТаблицаЗначений` with the full set of columns for
all execution modes.

**Parameters (handler table columns):**
- `Версия` (String) — the version number for which the handler is executed when
graded to, for example `"2.4.1.5"`. `"*"` — mandatory handler (on every
update). Empty string — handler only for initial fill (then
`НачальноеЗаполнение = Истина`).
- `Процедура` (String) — full name of the export procedure, for example
  `"ОбновлениеИнформационнойБазыУПП.ЗаполнитьНовыйРеквизит"`.
- `РежимВыполнения` (String) — `"Монопольно"` (default, heavy migration),
  `"Оперативно"` (without locking the IB, light migration), `"Отложенно"` (in the background
after the main cycle; requires `Идентификатор`, `БлокируемыеОбъекты`,
  `ПроцедураПроверки`).
- `НачальноеЗаполнение` (Boolean) — `Истина`, the handler fires on an "empty" database.
- `Идентификатор` (УникальныйИдентификатор) — for a deferred handler.
- `БлокируемыеОбъекты` (String) — for deferred, for example `"Справочник.Контрагенты"`.
- `ПроцедураПроверки` (String) — for deferred, a handler completion check function.
- `Комментарий` (String) — description.

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
  of the **application** module `ОбновлениеИнформационнойБазы<Префикс>` (for example,
  `…УПП`, `…БП`). The `ОбновлениеИнформационнойБазыСлужебный` BСП module contains
  its own `ПриДобавленииОбработчиковОбновления` — this is an internal BСП hook, not
  the place for application handlers.
- `Версия = "*"` — mandatory handler; runs on every update,
  independent of the previous IB version.
- The configuration version is stored in `Метаданные.Версия` and the `ВерсииПодсистем` register.

### 2. Safely write data from the update handler

**Task:** write an object / record set / constant in the update handler
without business logic and without registering on exchange plans (the migration must not spread across RIB nodes and must not trigger business logic that is not ready yet).

**Functions:**
`ОбновлениеИнформационнойБазы.ЗаписатьДанные(Знач Данные, Знач РегистрироватьНаУзлахПлановОбмена = Неопределено, Знач ВключитьБизнесЛогику = Ложь) Экспорт`
— Procedure (stable). Writing an object, record set, or constant manager.
`ОбновлениеИнформационнойБазы.ЗаписатьОбъект(Знач Объект, Знач РегистрироватьНаУзлахПлановОбмена = Неопределено, Знач ВключитьБизнесЛогику = Ложь, ДокументРежимЗаписи = Неопределено) Экспорт`
— Procedure (stable). Writing an object with the ability to post a document.
`ОбновлениеИнформационнойБазы.ЗаписатьНаборЗаписей(Знач НаборЗаписей, Замещать = Истина, Знач РегистрироватьНаУзлахПлановОбмена = Неопределено, Знач ВключитьБизнесЛогику = Ложь) Экспорт`
— Procedure (stable).
`ОбновлениеИнформационнойБазы.УдалитьДанные(Знач Данные, Знач РегистрироватьНаУзлахПлановОбмена = Неопределено, Знач ВключитьБизнесЛогику = Ложь) Экспорт`
— Procedure (stable).

**Parameters:**
- `Данные` / `Объект` / `НаборЗаписей` (Any) — written data.
- `РегистрироватьНаУзлахПлановОбмена` (Boolean / `Undefined`) — `Undefined`
  (default) — standard behavior of the update subsystem (usually **do not**
  register). `True` — force registration (the migration goes into
  exchange).
- `ВключитьБизнесЛогику` (Boolean) — `False` (default) disables handlers
  of the object module and event subscriptions. `True` — enable (e.g., for
  posting documents where the migration does not break logic).
- `ДокументРежимЗаписи` (DocumentWriteMode) — for `ЗаписатьОбъект`;
  `Undefined` — normal write, `DocumentWriteMode.Posting` —
  posting.
- `Замещать` (Boolean) — for `ЗаписатьНаборЗаписей`; `True` (default) —
  replacement.

**Example:**
```bsl
Процедура ОбновитьРеквизитДокумента() Экспорт
    Выборка = Документы.Заказ.Выбрать();
    Пока Выборка.Следующий() Цикл
        ДокументОбъект = Выборка.ПолучитьОбъект();
        ДокументОбъект.НовыйРеквизит = "...";
        // Business logic is DISABLED, registration on exchange plans is NOT performed
        ОбновлениеИнформационнойБазы.ЗаписатьДанные(ДокументОбъект);
    КонецЦикла;
КонецПроцедуры

// Write with posting (business logic enabled)
ОбновлениеИнформационнойБазы.ЗаписатьОбъект(ДокументОбъект, , Истина, РежимЗаписиДокумента.Проведение);
```

**Nuances / anti-patterns:**
- ❌ Direct `Объект.Записать()` in the handler — triggers business logic and
  exchange registration. Only the `ОбновлениеИнформационнойБазы.Записать*`
  wrappers.
- ❌ Use `ЗаписатьДанные` in normal application logic outside the update
  handler — the wrappers are intended only for migration code. For user
  writes — `Объект.Записать()` with all business logic.

### 3. Check object locking in the form

**Task:** lock editing of the object in the form until deferred
update has processed it (standard BSP check).

**Functions:**
`ОбновлениеИнформационнойБазы.МетаданныеИОтборПоДанным(Данные, ДополнительныеПараметры = Неопределено) Экспорт`
— Function (stable), normalizes data for the lock check.
`ОбновлениеИнформационнойБазы.ДанныеОбновленыНаНовуюВерсиюПрограммы(МетаданныеИОтбор) Экспорт`
— Function (stable), returns `Булево`: `Истина` — the object is updated and available
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

**Nuances / anti-patterns:**
- `ДанныеОбновленыНаНовуюВерсиюПрограммы` — standard check function by
  default; locked objects are registered on the exchange plan nodes
  `ОбновлениеИнформационнойБазы`.
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
- `ТолькоПроверка` (Булево) — for `ИсправленияИзменены`: `Истина` means only
  check, do not apply.
- `ВыполнятьОтложенныеОбработчики` (Булево) — `Истина` — deferred update
  is performed in the main loop (client-server mode only).

**Example:**
```bsl
// В обработке, вызываемой из внешнего соединения
ОбновлениеКонфигурации.ИсправленияИзменены();  // удалить устаревшие патчи
Результат = ОбновлениеИнформационнойБазы.ВыполнитьОбновлениеОбновлениеИнформационнойБазы();
Если Результат = "ОшибкаУстановкиМонопольногоРежима" Тогда
    // повторить позже или сообщить администратору
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Start `ВыполнитьОбновлениеИнформационнойБазы` without `ИсправленияИзменены`
  — accumulation of "dead" fixes and compatibility errors. Patches first.
- ❌ `ОбновлениеИнформационнойБазыВызовСервера.ВыполнитьОбновлениеИнформационнойБазы(Истина)`
  from client code while users are working — interactive update
  should go through `ОбновлениеКонфигурацииКлиент.ПоказатьПоискИУстановкуОбновлений()`.
- When called with attached extensions that modify roles, the method
  will raise an exception.

### 5. Read and write the IB version

**Task:** determine the current version of the subsystem/configuration, record the version
without executing handlers (for example, to cancel the standard transition from another
program), register a new subsystem.

**Functions:**
`ОбновлениеИнформационнойБазы.ВерсияИБ(Знач ИдентификаторБиблиотеки) Экспорт` —
Function (stable), returns the saved version (String).
`ОбновлениеИнформационнойБазы.УстановитьВерсиюИБ(Знач ИдентификаторБиблиотеки, Знач НомерВерсии, Знач ЭтоОсновнаяКонфигурация) Экспорт`
— Procedure (stable).
`ОбновлениеИнформационнойБазы.ВерсииПодсистем() Экспорт` — Function (stable),
returns a table of versions.
`ОбновлениеИнформационнойБазы.УстановитьВерсииПодсистем(ВерсииПодсистем) Экспорт`
— Procedure (stable).
`ОбновлениеИнформационнойБазы.ЗарегистрироватьНовуюПодсистему(ИмяПодсистемы, НомерВерсии = "") Экспорт`
— Procedure (stable). Registers a new subsystem **without** executing
initial fill handlers. Call from `ПередОбновлениемИнформационнойБазы`.

**Parameters:**
- `ИдентификаторБиблиотеки` (String) — the name of the configuration or library; for
the main configuration — `Метаданные.Имя`.
- `НомерВерсии` (String) — version number, for example `Метаданные.Версия`.
- `ЭтоОсновнаяКонфигурация` (Boolean) — `Истина` for the main configuration,
  `Ложь` for the library. Required.

**Example:**
```bsl
// Read the current version of the main configuration
ТекущаяВерсия = ОбновлениеИнформационнойБазы.ВерсияИБ(Метаданные.Имя);

// Write the version without executing handlers (cancel standard update)
ОбновлениеИнформационнойБазы.УстановитьВерсиюИБ(Метаданные.Имя, Метаданные.Версия, Истина);
```

**Nuances / anti-patterns:**
- ❌ `УстановитьВерсиюИБ(Метаданные.Имя, "1.0.0.0")` without the third parameter —
  the version will be written to the library branch. Always pass
  `ЭтоОсновнаяКонфигурация` (`Истина` for the main configuration).
- `ЗарегистрироватьНовуюПодсистему` does not execute handlers — it is used
  to mark the subsystem as already at the current version (for example, when migrating from
  another program, when migration is not needed).

### 6. Check whether an update is needed and in progress

**Task:** from application code, determine whether an update is required, whether
it is currently running, and whether deferred update has completed (so that you
can safely deny a write or show a warning).

**Functions:**
`ОбновлениеИнформационнойБазы.НеобходимоОбновлениеИнформационнойБазы() Экспорт` —
Function (stable), `Boolean`.
`ОбновлениеИнформационнойБазы.ВыполняетсяОбновлениеИнформационнойБазы() Экспорт`
— Function (stable), `Boolean`.
`ОбновлениеИнформационнойБазы.ОтложенноеОбновлениеЗавершено(Знач ИменаПодсистем = Неопределено) Экспорт`
— Function (stable), `Boolean`.
`ОбновлениеИнформационнойБазы.ПерезапуститьОтложенноеОбновление(Отбор = Неопределено) Экспорт`
— Procedure (stable).

**Parameters:**
- `ИменаПодсистем` (String / Array / `Неопределено`) — subsystem names for
  checking deferred update; `Неопределено` — all subsystems.
- `Отбор` (Structure / `Неопределено`) — for `ПерезапуститьОтложенноеОбновление`,
  e.g. `Новый Структура("ИмяОбработчика", "МойОбработчик")`.

**Example:**
```bsl
// Deny the write if the update is running
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
- `ОтложенноеОбновлениеЗавершено` is useful for blocking heavy operations
  that depend on the full data migration.

### 7. Implement update override hooks

**Task:** in the application configuration, implement the БСП hooks: actions before
update, after update, overriding settings and checks.

**Functions (hooks, `*Переопределяемый` module):**
`ОбновлениеИнформационнойБазыПереопределяемый.ПередОбновлениемИнформационнойБазы() Экспорт`
— Procedure (hook), server. Called **before** handlers are started.
`ОбновлениеИнформационнойБазыПереопределяемый.ПослеОбновленияИнформационнойБазы(Знач ПредыдущаяВерсияИБ, Знач ТекущаяВерсияИБ, Знач ИтерацииОбновления, ВыводитьОписаниеОбновлений, Знач МонопольныйРежим) Экспорт`
— Procedure (hook), server. Called **after** the update is completed.
`ОбновлениеИнформационнойБазыПереопределяемый.ПриОпределенииНастроек(Параметры) Экспорт`
— Procedure (hook), overrides the common settings of the update subsystem.
`ОбновлениеИнформационнойБазыПереопределяемый.ПриВыполненииПроверкиОбъектОбработан(ПолноеИмяОбъекта, БлокироватьИзменение, ТекстСообщения) Экспорт`
— Procedure (hook), called when checking whether an object has been processed during
update; application code can set `БлокироватьИзменение = Истина` and a message text to block changes to the object until the update handler has run.
`ОбновлениеИнформационнойБазыКлиентПереопределяемый.ПриОпределенииВозможностиОбновления(Знач ВерсияДанных) Экспорт`
— Procedure (hook), client. Checks whether updating is possible in
`ПередНачаломРаботыСистемы`.

**Parameters:**
- `ПредыдущаяВерсияИБ` / `ТекущаяВерсияИБ` (String) — versions before/after.
- `ИтерацииОбновления` (Structure) — iteration data.
- `ВыводитьОписаниеОбновлений` (Boolean) — output: `Ложь` disables showing the change description form.
- `МонопольныйРежим` (Boolean) — exclusive mode flag.
- `Параметры` (Structure) — update subsystem settings.
- `ГруппыПроверок` / `Проверки` (Array) — lock check groups and checks.
- `ВерсияДанных` (String) — IB data version for the client check.

**Example:**
```bsl
// В прикладном модуле ОбновлениеИнформационнойБазыПереопределяемый:
Процедура ПередОбновлениемИнформационнойБазы() Экспорт
    ВерсииПодсистем = ОбновлениеИнформационнойБазы.ВерсииПодсистем();
    Если ВерсииПодсистем.Количество() > 0
        И ВерсииПодсистем.Найти(Метаданные.Имя, "ИмяПодсистемы") = Неопределено Тогда
        // Отмена штатного перехода с другой программы — регистрируем как актуальную
        ОбновлениеИнформационнойБазы.ЗарегистрироватьНовуюПодсистему(Метаданные.Имя, Метаданные.Версия);
    КонецЕсли;
КонецПроцедуры

Процедура ПослеОбновленияИнформационнойБазы(Знач ПредыдущаяВерсияИБ, Знач ТекущаяВерсияИБ,
    Знач ИтерацииОбновления, ВыводитьОписаниеОбновлений, Знач МонопольныйРежим) Экспорт
    ВыводитьОписаниеОбновлений = Ложь; // отключить форму описания изменений
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Call `ОбновлениеИнформационнойБазыПереопределяемый.ПередОбновлениемИнформационнойБазы()`
  from application code — the `*Переопределяемый` module is only implemented, БСП
  invokes the hooks itself. To record the version before updating, use the direct API
  `УстановитьВерсиюИБ`.
- ❌ Use `ОбновлениеИнформационнойБазыСлужебный` for application tasks —
  it is internal, backward compatibility is not guaranteed. Stable equivalents are
  `ВерсияИБ`, `УстановитьВерсиюИБ`, `ВерсииПодсистем`.

## Rare methods

Methods that appear less often (from the companion `*-key-methods.md`), without the full
scenario:

- `ОбновлениеИнформационнойБазы.НоваяТаблицаОбработчиковОбновления()` — see
  scenario 1; returns `ТаблицаЗначений` with the full set of columns for all
  modes (`Монопольно` / `Оперативно` / `Отложенно` / `Параллельно`).
- `ОбновлениеИнформационнойБазы.ДанныеОбновленыНаНовуюВерсиюПрограммы(МетаданныеИОтбор)`
  — see scenario 3; standard blocking check function for the object form's
  `ПриСозданииНаСервере`.
- `ОбновлениеИнформационнойБазы.ЗаписатьОшибкуВЖурналРегистрации(СсылкаМетаданные, Знач Представление, ИнформацияОбОшибке = Неопределено, Уровень = Неопределено)`
— Procedure (stable), writes an error to the log, linked to the
  update event. For informational events — `ЗаписатьСобытиеВЖурналРегистрации`
  (returns the log event through `СобытиеЖурналаРегистрации()`).
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
— Procedure (stable), client. Opens the interactive update installation form
  (with backup and locks).
- ⚠️ `ОбновлениеИнформационнойБазыСлужебный.ПараметрыОбновления()` — Function,
  region `СлужебныеПроцедурыИФункции` (⚠️ service). Internal structure
  of update parameters; only for debugging and rare system scenarios, not
  for application code — will break when updating БСП.