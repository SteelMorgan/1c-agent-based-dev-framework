# Protection of Personal Data (152-FZ) in БСП

The **ЗащитаПерсональныхДанных** subsystem is designed to comply with the requirements
of Federal Law No. 152-FZ of 27.07.2006 "On Personal Data":
subject consents, destruction/hiding of PD, registration of access events to
PD, embedding the subsystem into subject list forms, and application hooks for
the PD lifecycle. It is connected with the **КонтрольРаботыПользователей** subsystem
(the global switch for registering data access events).

## Modules

The `ЗащитаПерсональныхДанных` family (suffix logic):

- `ЗащитаПерсональныхДанных` — main server-side API: consents,
  destruction, checks, access events, list forms, IB update.
  In `src/cf`, stable methods are in `ПрограммныйИнтерфейс`, deprecated ones are in
  `УстаревшиеПроцедурыИФункции`, service ones are in `СлужебныеПроцедурыИФункции`
  and `СлужебныйПрограммныйИнтерфейс`.
- `ЗащитаПерсональныхДанныхКлиент` — client-side form hooks: opening the consent
  form, handling form/list notifications, the command "Show with
  hidden PD". Stable ones are `ОткрытьФормуСогласиеНаОбработкуПерсональныхДанных`,
  `ОбработкаОповещенияФормы`, `ОбработкаОповещенияФормыСписка`,
  `ПоказыватьСоСкрытымиПДн`.
- `ЗащитаПерсональныхДанныхВызовСервера` — client→server (long-running
  operation for changing destruction settings).
- `ЗащитаПерсональныхДанныхПереопределяемый` — ⚠️ **hooks**: БСП calls it,
  application code **implements** it (copies the override module into the
  configuration and overrides the body). DO NOT call it from application code
  directly.
- `ЗащитаПерсональныхДанныхПовтИсп`, `ЗащитаПерсональныхДанныхУничтожениеПовтИсп`
  — cached directories/parameters; service ones.
- ⚠️ `ЗащитаПерсональныхДанныхСлужебный` — service API; backward
  compatibility is not guaranteed.

Defined types: `СубъектПерсональныхДанных` (reference),
`СубъектПерсональныхДанныхОбъект` (object). Functional option
`ИспользоватьСкрытиеПерсональныхДанныхСубъектов` (constant) — global
switch for destruction/hiding of PD; getter — `ИспользоватьУничтожениеПерсональныхДанныхСубъектов()`.

Key registers: `СогласияНаОбработкуПерсональныхДанных`,
`УничтоженныеПерсональныеДанные`, `ОбластиПерсональныхДанных`,
`СрокиХраненияПерсональныхДанных`, `СубъектыДляРасчетаСроковХранения`.
Documents: `СогласиеНаОбработкуПерсональныхДанных`,
`ОтзывСогласияНаОбработкуПерсональныхДанных`, `АктОбУничтоженииПерсональныхДанных`.

## Scenarios

### 1. Check the subject's current consent for personal data processing

**Task:** before saving the card / printing / generating a report, find out
whether the subject has valid consent in the specified organization as of the date.

**Function:**
`ЗащитаПерсональныхДанных.ДействующееСогласиеНаОбработкуПерсональныхДанных(Субъект, Организация = Неопределено, Знач Дата = Неопределено, ИсключаемыйРегистратор = Неопределено) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection. Read access is performed in **privileged mode**.

**Parameters:**
- `Субъект` (`ОпределяемыйТип.СубъектПерсональныхДанных`) — the subject whose
  consent is being determined.
- `Организация` (`ОпределяемыйТип.Организация`) — the personal data controller to
  which consent was granted; default is `Неопределено`.
- `Дата` (`Дата`) — the date for which the state is requested; if
  `Неопределено` — the latest record is selected.
- `ИсключаемыйРегистратор` (`ДокументСсылка.СогласиеНаОбработкуПерсональныхДанных`)
  — the current document; passed so that movements performed by this document
  itself are ignored during the search.

**Return value:** `Неопределено` (consent was not granted or
its term has expired) or `Структура` with fields `ДатаПолучения` (`Дата`),
`СрокДействия` (`Дата`; empty means indefinite), `ДокументОснование`
(`ДокументСсылка.СогласиеНаОбработкуПерсональныхДанных`).

**Example:**
```bsl
// В обработчике ПередЗаписью модуля объекта-субъекта
Процедура ПередЗаписью(Отказ)
    Если ОбменДанными.Загрузка Тогда Возврат; КонецЕсли;
    Если Не ЭтоНовый() И Не ЭтоГруппа Тогда
        Согласие = ЗащитаПерсональныхДанных.ДействующееСогласиеНаОбработкуПерсональныхДанных(
            Ссылка, Организация, ТекущаяДатаСеанса());
        Если Согласие = Неопределено Тогда
            ОбщегоНазначения.СообщитьПользователю(
                НСтр("ru = 'Нет действующего согласия на обработку ПДн.'"),
                , , , Отказ);
        КонецЕсли;
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Direct query to the `СогласияНаОбработкуПерсональныхДанных` register —
  bypasses privileged mode and the logic for overlapping validity periods. Only through
  `ДействующееСогласиеНаОбработкуПерсональныхДанных`.
- The method enters privileged mode on its own — a separate
  `УстановитьПривилегированныйРежим` is not needed.
- When checking from the posting handler of the consent document itself,
  pass `ИсключаемыйРегистратор = Ссылка`, otherwise the document will "not find"
  its own consent.

### 2. Verify that the object’s personal data has been destroyed

**Task:** hide an object with destroyed personal data in lists/reports/print
forms; check the state before outputting data.

**Functions:**
`ЗащитаПерсональныхДанных.ЭтоОбъектСУничтоженнымиПерсональнымиДанными(Знач Объект) Экспорт`
— Function → `Булево`, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЗащитаПерсональныхДанных.ИспользоватьУничтожениеПерсональныхДанныхСубъектов() Экспорт`
— Function → `Булево` (getter for functional option `ИспользоватьСкрытиеПерсональныхДанныхСубъектов`), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

**Parameters:**
- `Объект` (`СправочникОбъект`, `СправочникСсылка`, `ДокументОбъект`,
  `ДокументСсылка`, `РегистрСведенийНаборЗаписей` and sets of other registers)
  — the object being checked; the subject itself may also be passed.

**Example:**
```bsl
// Гейтинг: имеет смысл только при включённой ФО
Если ЗащитаПерсональныхДанных.ИспользоватьУничтожениеПерсональныхДанныхСубъектов() Тогда
    Если ЗащитаПерсональныхДанных.ЭтоОбъектСУничтоженнымиПерсональнымиДанными(ОбъектДанных) Тогда
        // Не выводить уничтоженные ПДн в отчёт / печатную форму
        Возврат;
    КонецЕсли;
КонецЕсли;
```

**Nuances / antipatterns:**
- ❌ `ЗащитаПерсональныхДанных.ЭтоОбъектСоСкрытымиПерсональнымиДанными(Объект)`
  — **deprecated** (`УстаревшиеПроцедурыИФункции`). Use
  `ЭтоОбъектСУничтоженнымиПерсональнымиДанными`.
- ❌ Ignore the functional option: destruction operations may pass through
  "silently" if the FO is disabled. First check
  `ИспользоватьУничтожениеПерсональныхДанныхСубъектов()`.
- `ЭтоОбъектСУничтоженнымиПерсональнымиДанными` works correctly even when the
  FO is disabled (returns `Ложь`) — but semantically it only makes sense to
  perform the check when it is enabled.

### 3. Learn the actual destruction date / subjects with expired retention period

**Objective:** check whether the personal data of the subject has **already been destroyed** (and when), and obtain a list of subjects with an **expired planned** retention period - for scheduled cleanup. These are two different tasks and two different data sources.

**Functions:**
`ЗащитаПерсональныхДанных.ДатаУничтоженияДанныхСубъекта(Субъект) Экспорт`
— Function → `Дата` (**actual** date of completed destruction from the `УничтоженныеПерсональныеДанные` register), region `#Область ПрограммныйИнтерфейс`
(stable). Server, Thick client, External connection.
`ЗащитаПерсональныхДанных.СубъектыСИстекшимСрокомХранения(Субъекты, ДатаАктуальности = Неопределено) Экспорт`
— Function → `Массив` of `ОпределяемыйТип.СубъектПерсональныхДанных`
(subjects with an expired **planned** retention period - candidates for destruction),
region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client,
External connection.

**Parameters:**
- `Субъект` (`ОпределяемыйТип.СубъектПерсональныхДанных`) — subject.
- `Субъекты` — selection set (see the register
  `СрокиХраненияПерсональныхДанных.СубъектыСИстекшимСрокомХранения`).
- `ДатаАктуальности` (`Дата`) — the date as of which expiration is calculated;
  by default `Неопределено`.

**Example:**
```bsl
// Факт уничтожения: метод возвращает ДАТУ, при отсутствии записи — пустую Дата(1, 1, 1)
ДатаУничтожения = ЗащитаПерсональныхДанных.ДатаУничтоженияДанныхСубъекта(ФизЛицо);
Если ДатаУничтожения <> Дата(1, 1, 1) Тогда
    // ПДн субъекта уже уничтожены (факт) — ДатаУничтожения = дата события
КонецЕсли;

// Плановый срок: кандидаты на уничтожение (очередь), не факт уничтожения
Истекшие = ЗащитаПерсональныхДанных.СубъектыСИстекшимСрокомХранения(МассивСубъектов);
```

**Nuances / anti-patterns:**
- ❌ Compare the result with `Неопределено` — the method **never** returns
  `Неопределено`: when no destruction record exists, it returns an **empty
  date** `Дата(1, 1, 1)`. The check is `ДатаУничтожения <> Дата(1, 1, 1)`.
- ❌ Treat `ДатаУничтоженияДанныхСубъекта` as the **planned** date / “the
  retention period has expired - the subject is in the queue”. The method reads
  the fact register `УничтоженныеПерсональныеДанные`; the “queue for destruction”
  (planned retention period) is `СубъектыСИстекшимСрокомХранения` /
  `СрокиХраненияПерсональныхДанных`.
  To check “the data has already been destroyed”, there is also
  `ЭтоОбъектСУничтоженнымиПерсональнымиДанными` (scenario 2).
- `ЗащитаПерсональныхДанных.СрокХраненияПерсональныхДанныхСубъекта(Субъект)`
  — ⚠️ **service** (`СлужебныйПрограммныйИнтерфейс`); reads the planned retention
  period from `СрокиХраненияПерсональныхДанных` and returns `Неопределено`/`Дата`.
  This is a **different** task than `ДатаУничтоженияДанныхСубъекта` (fact);
  the methods are not interchangeable. The stable path for “expired retention
  period” is `СубъектыСИстекшимСрокомХранения`; for “already destroyed” is
  `ДатаУничтоженияДанныхСубъекта` / `ЭтоОбъектСУничтоженнымиПерсональнымиДанными`.
- Planned retention periods are filled by the scheduled job
  `РасчетСроковХраненияПерсональныхДанных` (override hook
  `ПриРасчетеСроковХраненияПерсональныхДанных`, scenario 7); until it runs,
  `СубъектыСИстекшимСрокомХранения` will not return subjects that have not yet
  been calculated.

### 4. Enable registration of personal data access events

**Task:** enable/disable registration of the `Доступ.Доступ` event in the registration log
for personal data categories (152-FZ requirement) and find out the current
state.

**Functions:**
`ЗащитаПерсональныхДанных.УстановитьИспользованиеСобытияДоступ(Использование, КатегорииДанных = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЗащитаПерсональныхДанных.ИспользованиеСобытияДоступ() Экспорт`
— Function → `ДеревоЗначений` (columns `Имя`, `Представление`, `Использование`), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

**Parameters:**
- `Использование` (`Булево`) — `Истина` → `Доступ.Доступ` events
  are registered; `Ложь` → disabled.
- `КатегорииДанных` (`Массив`) — an array of personal data categories for which
  usage is set; `Неопределено` (default) — for all
  categories from `ЗаполнитьСведенияОПерсональныхДанных`.

**Example:**
```bsl
// Global switch of the "User activity control" subsystem
КонтрольРаботыПользователей.УстановитьРегистрациюДоступаКДанным(Истина);

// Details: enable "Доступ.Доступ" for personal data categories
ЗащитаПерсональныхДанных.УстановитьИспользованиеСобытияДоступ(Истина);

// Find out the current state by areas
ДеревоОбластей = ЗащитаПерсональныхДанных.ИспользованиеСобытияДоступ();
```

**Nuances / anti-patterns:**
- A link between two subsystems: `УстановитьИспользованиеСобытияДоступ` enables
  registration for **personal data categories**, but the `Доступ.Доступ` log itself
  works only when the global switch
  `КонтрольРаботыПользователей.РегистрироватьДоступКДанным()` = `Истина`.
  First the global one, then the detail by categories.
- ❌ Direct write to the log `ЗаписьЖурналаРегистрации("Доступ.Доступ", ...)`
  instead of subsystem settings bypasses structured accounting and personal data categories.
- `ИспользованиеСобытияДоступ` returns `ДеревоЗначений` (not a structure):
  iterate over the tree rows, not its properties.

### 5. Embed the subsystem into the subject list form

**Task:** add the `ОтсутствуетСогласие` column with an image to the subject list form, the `Показывать со скрытыми ПДн` command, and handling of notifications about destroying PДн from another form.

**Functions:**
`ЗащитаПерсональныхДанных.ПриСозданииНаСервереФормыСписка(Форма, СписокФормы) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ЗащитаПерсональныхДанных.ПриПолученииДанныхНаСервере(Настройки, Строки) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ЗащитаПерсональныхДанныхКлиент.ОбработкаОповещенияФормыСписка(СписокФормы, ИмяСобытия) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Thin client, Thick client.
`ЗащитаПерсональныхДанныхКлиент.ПоказыватьСоСкрытымиПДн(Форма, Список) Экспорт`
— Procedure (command handler), region `#Область ПрограммныйИнтерфейс` (stable). Thin client, Thick client.

**Parameters:**
- `Форма` (`ФормаКлиентскогоПриложения`) — subject form.
- `СписокФормы` / `Список` (`ДинамическийСписок`) — dynamic list of subjects.
- `Настройки` — dynamic list settings (extended by the subsystem).
- `Строки` — list data rows (the subsystem supplements them with PДн flags).
- `ИмяСобытия` (`Строка`) — notification name (about destroying PДн).

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    ЗащитаПерсональныхДанных.ПриСозданииНаСервереФормыСписка(ЭтаФорма, Элементы.Список);
КонецПроцедуры

&НаСервере
Процедура ПриПолученииДанныхНаСервере(ИмяСписка, Настройки, Строки)
    ЗащитаПерсональныхДанных.ПриПолученииДанныхНаСервере(Настройки, Строки);
КонецПроцедуры

&НаКлиенте
Процедура ОбработкаОповещения(ИмяСобытия, Параметр, Источник)
    ЗащитаПерсональныхДанныхКлиент.ОбработкаОповещенияФормыСписка(Элементы.Список, ИмяСобытия);
КонецПроцедуры

// Command `ПоказыватьСоСкрытымиПДн` on the form
&НаКлиенте
Процедура ПоказыватьСоСкрытымиПДн(Команда)
    ЗащитаПерсональныхДанныхКлиент.ПоказыватьСоСкрытымиПДн(ЭтаФорма, Элементы.Список);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Forgetting `ОбработкаОповещения` in the list form — the consent icon column will not refresh after PДн are destroyed in another form.
- ❌ Creating your own common module `ЗащитаПерсональныхДанных` in the application configuration — extend only through `ЗащитаПерсональныхДанныхПереопределяемый` (hooks).
- When the functional option `ИспользоватьСкрытиеПерсональныхДанныхСубъектов` is disabled, `ПриСозданииНаСервереФормыСписка` works correctly (does not add the column) — no separate check is needed.

### 6. Add a command to print consent for personal data processing

**Task:** add a command to the subject object form for navigating to the preparation of
consent for personal data processing (through the standard subsystem `Печать`).

**Function:**
`ЗащитаПерсональныхДанных.ДобавитьКомандуПечатиСогласияНаОбработкуПерсональныхДанных(КомандыПечати) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

**Parameters:**
- `КомандыПечати` — print commands collection (see
  `УправлениеПечатью.СоздатьКоллекциюКомандПечати`).

**Example:**
```bsl
// В переопределяемом обработчике ДобавитьКомандыПечати прикладной конфигурации
Процедура ДобавитьКомандыПечати(КомандыПечати) Экспорт
    // ...свои команды печати объекта...
    ЗащитаПерсональныхДанных.ДобавитьКомандуПечатиСогласияНаОбработкуПерсональныхДанных(КомандыПечати);
КонецПроцедуры
```

**Nuances / antipatterns:**
- The method is intended to be called **from** the `ДобавитьКомандыПечати` method
  of the standard `Печать` subsystem in personal-data subject objects; do not call it
  outside the commands collection.
- This is the only print command of this subsystem; for other print forms
  - use the `Печать` subsystem.

### 7. Implement application lifecycle hooks for personal data

**Task:** in the application configuration, define the set of personal data categories, data
areas, populate full name, refuse destruction of a specific subject, and
perform actions before/after destruction.

**Functions (hooks - implemented by application code in `ЗащитаПерсональныхДанныхПереопределяемый`):**
`ЗаполнитьСведенияОПерсональныхДанных(ТаблицаСведений) Экспорт`
`ЗаполнитьОбластиПерсональныхДанных(КатегорииПерсональныхДанных) Экспорт`
`ДополнитьДанныеСубъектовПерсональныхДанных(СубъектыПерсональныхДанных, ДатаАктуальности) Экспорт`
`ДополнитьДанныеОрганизацииОператораПерсональныхДанных(Организация, ДанныеОрганизации, ДатаАктуальности) Экспорт`
`ЗаполнитьФИОФизическогоЛица(ФизическоеЛицо, ФИО) Экспорт`
`ПриЗаполненииСведенийОбУничтожаемыхПерсональныхДанных(ТаблицаСведений) Экспорт`
`ПередСкрытиемПерсональныхДанныхСубъектов(Субъекты, ТаблицаИсключений, ОтказОтСкрытия) Экспорт`
`ПередУничтожениемПерсональныхДанных(Объект, Субъекты, ВыполнитьЗаписьОбъекта) Экспорт`
`ПослеУничтоженияПерсональныхДанных(Объект, Субъекты) Экспорт`
`ПриРасчетеСроковХраненияПерсональныхДанных(ДанныеСубъектов, СрокиХранения) Экспорт`
— all in the `#Область ПрограммныйИнтерфейс` region of the `*Переопределяемый` module. `БСП` **calls** these methods; application code **implements** them by copying the override module into the configuration. A direct call from application code is not needed.

**Parameters (key):**
- `ТаблицаСведений` (`ТаблицаЗначений`) — for `ЗаполнитьСведения…`: columns
  `Объект` (full metadata name), `ПоляРегистрации` (subject identification
  fields, comma-separated; alternatives separated by `|`),
  `ПоляДоступа` (access fields comma-separated), `ОбластьДанных` (category
  identifier).
- `Субъекты` (`Массив` from `ОпределяемыйТип.СубъектПерсональныхДанных`) —
  subjects for destruction.
- `ТаблицаИсключений` (`ТаблицаЗначений`) — columns `Субъект`,
  `ПричинаОтменыУничтожения`; after adding a row, application code refuses
  destruction of the subject.
- `ОтказОтСкрытия` (`Булево`, default `Истина`) — if refusal reasons are defined,
  the parameter must be set to `Ложь`.
- `Объект` (`СправочникОбъект`, `ДокументОбъект`) — carrier object of personal data.
- `ВыполнитьЗаписьОбъекта` (`Булево`) — output: `Ложь` cancels object write
  during destruction.

**Example:**
```bsl
// Реализация в прикладном ЗащитаПерсональныхДанныхПереопределяемый

Процедура ЗаполнитьСведенияОПерсональныхДанных(ТаблицаСведений) Экспорт
    Сведения = ТаблицаСведений.Добавить();
    Сведения.Объект            = "Справочник.ФизическиеЛица";
    Сведения.ПоляРегистрации   = "Ссылка";
    Сведения.ПоляДоступа       = "Наименование";
    Сведения.ОбластьДанных     = "ФИО";

    Сведения = ТаблицаСведений.Добавить();
    Сведения.Объект            = "Справочник.ФизическиеЛица";
    Сведения.ПоляРегистрации   = "Ссылка";
    Сведения.ПоляДоступа       = "СерияДокумента,НомерДокумента,КемВыданДокумент,ДатаВыдачиДокумента";
    Сведения.ОбластьДанных     = "ПаспортныеДанные";
КонецПроцедуры

Процедура ПередСкрытиемПерсональныхДанныхСубъектов(Субъекты, ТаблицаИсключений, ОтказОтСкрытия) Экспорт
    Для Каждого Субъект Из Субъекты Цикл
        Если СубъектЯвляетсяДействующимСотрудником(Субъект) Тогда
            Строка = ТаблицаИсключений.Добавить();
            Строка.Субъект = Субъект;
            Строка.ПричинаОтменыУничтожения = НСтр("ru = 'Субъект — действующий сотрудник.'");
        КонецЕсли;
    КонецЦикла;
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Call `ЗащитаПерсональныхДанныхПереопределяемый.ПередУничтожениемПерсональныхДанных(...)`
  from application code — this is a hook; it is **implemented**, and БСП itself calls it
  inside `УничтожитьПерсональныеДанныеСубъектов`.
- ❌ Create your own common module
  `ЗащитаПерсональныхДанных` in the application configuration — only the override module.
- When the set of subjects is expanded, event registration for them will not start
  automatically; to control this when upgrading the version, implement an
  update handler that calls
  `ЗащитаПерсональныхДанных.УстановитьИспользованиеСобытияДоступ`.

### 8. Change deletion settings programmatically

**Task:** programmatically enable/disable the personal data deletion
functional option from application code, including from a long-running operation.

**Function:**
`ЗащитаПерсональныхДанных.ИзменитьНастройкиУничтоженияПерсональныхДанных(Параметры, АдресРезультата = "") Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

**Parameters:**
- `Параметры` (`Структура`) — `ИспользоватьУничтожениеПерсональныхДанных`
  (`Булево`) — new value of the flag.
- `АдресРезультата` (`Строка`) — address of the temporary storage where
  the result of the operation is placed; default is `""`.

**Example:**
```bsl
Параметры = Новый Структура;
Параметры.Вставить("ИспользоватьУничтожениеПерсональныхДанных", Истина);
ЗащитаПерсональныхДанных.ИзменитьНастройкиУничтоженияПерсональныхДанных(Параметры);
```

**Nuances / antipatterns:**
- ❌ `ЗащитаПерсональныхДанных.ИзменитьНастройкиСкрытияПерсональныхДанных(...)`
  — **deprecated** (`УстаревшиеПроцедурыИФункции`). Use
  `ИзменитьНастройкиУничтоженияПерсональныхДанных`.
- Changing the setting is a potentially long-running operation; to start it from
  client code, use `ЗащитаПерсональныхДанныхВызовСервера`
  (the wrapper launches a long-running operation).

## Rare methods

- `ЗащитаПерсональныхДанных.УничтожитьПерсональныеДанныеСубъектов(Знач Субъекты) Экспорт`
  — ⚠️ **internal** (`СлужебныеПроцедурыИФункции`): irreversible destruction of
  subjects' personal data (reference or array) in a transaction. No stable public API
  for destruction is exposed in BSP 3.1.11 — destruction is initiated through the
  subsystem processing/form (processing `УничтожениеПерсональныхДанных`,
  document `АктОбУничтоженииПерсональныхДанных`), not by a direct call. Inside
  `СкрытьПерсональныеДанныеСубъектов` (deprecated), it is reduced to this method.
- `ЗащитаПерсональныхДанных.СрокХраненияПерсональныхДанныхСубъекта(Субъект) Экспорт`
  — ⚠️ **internal** (`СлужебныйПрограммныйИнтерфейс`): returns
  `Неопределено` / `Дата`. Stable alternative —
  `ДатаУничтоженияДанныхСубъекта` (`ПрограммныйИнтерфейс`).
- `ЗащитаПерсональныхДанных.СведенияОПерсональныхДанных() Экспорт`
  — ⚠️ **internal** (`СлужебныеПроцедурыИФункции` of the main module):
  direct read of the personal data composition table. Do not use from application code;
  the composition is configured through the hook `ЗаполнитьСведенияОПерсональныхДанных`
  of the `ЗащитаПерсональныхДанныхПереопределяемый` module.
- Deprecated (`УстаревшиеПроцедурыИФункции` of the main module):
  `СкрытьПерсональныеДанныеСубъектов(Знач Субъекты, СообщатьОбИсключениях = Ложь) Экспорт`
  — marks personal data as hidden; use destruction through the subsystem processing;
  `ЭтоОбъектСоСкрытымиПерсональнымиДанными(Знач Объект) Экспорт` — use
  `ЭтоОбъектСУничтоженнымиПерсональнымиДанными`;
  `ИзменитьНастройкиСкрытияПерсональныхДанных(Параметры, АдресРезультата = "") Экспорт`
  — use `ИзменитьНастройкиУничтоженияПерсональныхДанных`.
- `ЗащитаПерсональныхДанных.УдалитьИнформациюОбУничтоженииПерсональныхДанных(Субъект) Экспорт`
  — stable (`ПрограммныйИнтерфейс`); clears the record of personal data destruction
  for a subject (e.g. during restoration).
- `ЗащитаПерсональныхДанных.ДобавитьСубъектыДляРасчетаСроковХранения(Знач Субъекты, Знач ДатаСобытия, ИспользоватьУничтожениеПДн = Неопределено) Экспорт`
  — stable (`ПрограммныйИнтерфейс`); queues subjects for retention period calculation.
- `ЗащитаПерсональныхДанныхКлиент.ОткрытьФормуСогласиеНаОбработкуПерсональныхДанных(ПараметрыПечати) Экспорт`
  — stable (`ПрограммныйИнтерфейс`); client-side opening of the consent form.

To find the signature/region of any of these methods —
`python .claude/skills/bsp/scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`.