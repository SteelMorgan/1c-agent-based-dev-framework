# Business Processes and Tasks

The **БизнесПроцессыИЗадачи** subsystem consists of common modules `БизнесПроцессыИЗадачиСервер`
(server stable API), `БизнесПроцессыИЗадачиКлиент` (client UI wrappers),
`БизнесПроцессыИЗадачиВызовСервера` (server call from client, ⚠️ internal), and
overrideable hook modules `БизнесПроцессыИЗадачиПереопределяемый` /
`БизнесПроцессыИЗадачиКлиентПереопределяемый`. It covers task execution,
redirection, acceptance for execution, stopping/activating business processes,
retrieving the task execution form, and scheduled handlers for deadline control.

## Modules

Suffix naming scheme (one root + execution context):

- `БизнесПроцессыИЗадачиСервер` — **stable server API** (execution,
  redirection, acceptance, stopping, deadline control). Server, Thick
  client, External connection.
- `БизнесПроцессыИЗадачиКлиент` — **stable client API** (opening redirection
  forms, additional information, task subject, accepting for execution from a
  form). Thin / Thick client.
- `БизнесПроцессыИЗадачиВызовСервера` — ⚠️ **internal**: server calls from
  client. Duplicate server methods in `СлужебныеПроцедурыИФункции`
  (`ПеренаправитьЗадачи`, `ПринятьЗадачиКИсполнению`, `ОстановитьБизнесПроцесс`,
  `СделатьАктивнымБизнесПроцесс`, etc.) and in `УстаревшиеПроцедурыИФункции`
  (`ВыполнитьЗадачу`, `ФормаВыполненияЗадачи`, `ЭтоВедущаяЗадача`,
  `ЭтоЗадачаИсполнителю`, `СформироватьДанныеВыбораИсполнителя`). Backward
  compatibility is not guaranteed - do not call from application code, use the
  client wrappers.
- `БизнесПроцессыИЗадачиКлиентСервер` — safe code without server calls and
  without DB.
- `БизнесПроцессыИЗадачиПереопределяемый` (server) — **hooks**: БСП calls,
  application code implements (do not call directly).
- `БизнесПроцессыИЗадачиКлиентПереопределяемый` (client) — **hook**
  `ПриВыбореИсполнителя`.
- `БизнесПроцессыИЗадачиСобытия` — application/session event handlers
  (internal).

⚠️ There is **NO common module `БизнесПроцессыИЗадачи` (without a suffix)** —
this is the subsystem name and the defined type `ОпределяемыйТип.БизнесПроцесс`,
but not a common module. All server methods are in `…Сервер`, all client methods
are in `…Клиент`.

⚠️ **“Phantoms”** — methods `БизнесПроцессыИЗадачиСервер.ФормированиеЗадач`,
`…Сервер.СоздатьЗадачу`, `…Клиент.ОткрытьФормуЗадачи` **do not exist**. Task
creation is the responsibility of the business process code
(`БизнесПроцессы.<Имя>.СоздатьЗадачу(...)`);
to open the task form use `ОткрытьЗначение(...)` or
`ОткрытьФорму("Задача.ЗадачаИсполнителя.ФормаВыполненияЗадачи", ...)` after
`ФормаВыполненияЗадачи`.

## Scenarios

### 1. Execute a task with the default auto-handler

**Task:** mark the executor task as completed and, if necessary, call the
business process handler.

**Function:**
`БизнесПроцессыИЗадачиСервер.ВыполнитьЗадачу(ЗадачаСсылка, ДействиеПоУмолчанию = Ложь) Экспорт`
— Procedure, region: `#Область ПрограммныйИнтерфейс` (stable). Server, Thick
client, External connection.

**Parameters:**
- `ЗадачаСсылка` (ЗадачаСсылка.ЗадачаИсполнителя) — task to execute.
- `ДействиеПоУмолчанию` (Булево) — `Истина` → after the standard mark, call the
  `ОбработкаВыполненияПоУмолчанию` procedure of the task business process
  manager module (if it is defined). `Ложь` — only the standard completion mark.

**Example:**
```bsl
&НаКлиенте
Процедура ВыполнитьЗадачу(Команда)
    ТекущиеДанные = Элементы.СписокЗадач.ТекущиеДанные;
    Если ТекущиеДанные = Неопределено Тогда
        Возврат;
    КонецЕсли;
    ВыполнитьЗадачуНаСервере(ТекущиеДанные.Ссылка);
    ОповеститьОбИзменении(Тип("ЗадачаСсылка.ЗадачаИсполнителя"));
КонецПроцедуры

&НаСервере
Процедура ВыполнитьЗадачуНаСервере(ЗадачаСсылка)
    // ДействиеПоУмолчанию = Истина — вызовет ОбработкаВыполненияПоУмолчанию
    БизнесПроцессыИЗадачиСервер.ВыполнитьЗадачу(ЗадачаСсылка, Истина);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ `БизнесПроцессыИЗадачи.ВыполнитьЗадачу(...)` — the module without a suffix
  does not exist, compilation error. Only `БизнесПроцессыИЗадачиСервер`.
- ❌ Calling the server method directly from `&НаКлиенте` in a thin application —
  the `…Сервер` module is not available on the client. Wrap it in `&НаСервере`
  or use `БизнесПроцессыИЗадачиВызовСервера` (⚠️ internal).
- Without `ДействиеПоУмолчанию = Истина`, only the mark will be performed; the
  business process application logic will not be called - a typical mistake when
  copying “execute task” without the flag.

### 2. Redirect tasks to a new executor with a preliminary check

**Task:** redirect an array of tasks to another executor / another role, first
checking the possibility without side effects.

**Function:**
`БизнесПроцессыИЗадачиСервер.ПеренаправитьЗадачи(Знач ПеренаправляемыеЗадачи, Знач ИнфоОПеренаправлении, Знач ТолькоПроверка = Ложь, ПеренаправленныеЗадачи = Неопределено) Экспорт`
— Function → Булево (Истина = success), region: `#Область ПрограммныйИнтерфейс`
(stable). Server, Thick client, External connection.

**Parameters:**
- `ПеренаправляемыеЗадачи` (Array of ЗадачаСсылка.ЗадачаИсполнителя) — tasks.
- `ИнфоОПеренаправлении` (Structure) — new values of task addressing
  attributes: keys `Исполнитель`, `РольИсполнителя`, `ОсновнойОбъектАдресации`,
  `ДополнительныйОбъектАдресации`.
- `ТолькоПроверка` (Булево) — `Истина` → only check the possibility, without
  physical changes; `Ложь` — perform the redirection.
- `ПеренаправленныеЗадачи` (Array, output) — successfully redirected tasks; may
  be shorter than the source array if not all tasks were redirected.

**Example:**
```bsl
ИнфоОПеренаправлении = Новый Структура;
ИнфоОПеренаправлении.Вставить("Исполнитель", НовыйИсполнитель);
ИнфоОПеренаправлении.Вставить("РольИсполнителя", Неопределено);

// Step 1 - only check (fast, without changing state)
МожноПеренаправить = БизнесПроцессыИЗадачиСервер.ПеренаправитьЗадачи(
    МассивЗадач, ИнфоОПеренаправлении, Истина);
Если Не МожноПеренаправить Тогда
    Возврат; // Message to the user has already been formed inside
КонецЕсли;

// Step 2 - actual redirection
ПеренаправленныеЗадачи = Новый Массив;
Успех = БизнесПроцессыИЗадачиСервер.ПеренаправитьЗадачи(
    МассивЗадач, ИнфоОПеренаправлении, Ложь, ПеренаправленныеЗадачи);
```

**Nuances / anti-patterns:**
- ❌ Calling immediately with `ТолькоПроверка = Ложь` without a preliminary check
  - if the array is partially unsuitable, part of the tasks will change before
  the error. First `ТолькоПроверка = Истина`, then `Ложь`.
- If `Исполнитель` is a user / external user, БСП additionally checks that they
  are not marked for deletion and are valid; otherwise -
  `ОбщегоНазначения.СообщитьПользователю`.
- ❌ Calling server `ПеренаправитьЗадачи` directly from `&НаКлиенте`. From the
  client use `БизнесПроцессыИЗадачиКлиент.ПеренаправитьЗадачи` (opens a form,
  see scenario 3). Programmatic server call from the client through
  `БизнесПроцессыИЗадачиВызовСервера.ПеренаправитьЗадачи` is ⚠️ internal, and
  backward compatibility is not guaranteed.

### 3. Open the redirection form from the task list

**Task:** from the client task list form, open a managed redirection form with a
preliminary server-side availability check.

**Function:**
`БизнесПроцессыИЗадачиКлиент.ПеренаправитьЗадачи(ПеренаправляемыеЗадачи, ФормаВладелец) Экспорт`
— Procedure, region: `#Область ПрограммныйИнтерфейс` (stable). Thin / Thick
client.

**Parameters:**
- `ПеренаправляемыеЗадачи` (Array of ЗадачаСсылка.ЗадачаИсполнителя) — tasks
  selected in the list.
- `ФормаВладелец` (ФормаКлиентскогоПриложения) — owner form for the redirection
  form being opened.

**Example:**
```bsl
&НаКлиенте
Процедура ПеренаправитьЗадачи(Команда)
    ВыделенныеЗадачи = Элементы.СписокЗадач.ВыделенныеСтроки;
    Если ВыделенныеЗадачи.Количество() = 0 Тогда
        ПоказатьПредупреждение(, НСтр("ru = 'Не выбраны задачи.'"));
        Возврат;
    КонецЕсли;
    // Opens the form; the server-side check call (ТолькоПроверка = Истина)
    // is done inside the wrapper itself
    БизнесПроцессыИЗадачиКлиент.ПеренаправитьЗадачи(ВыделенныеЗадачи, ЭтаФорма);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- This client wrapper **does not duplicate** the server method - it opens the
  form where the user enters a new executor / role; the server-side check call
  is done inside. Do not try to “programmatically redirect” through it - for
  programmatic redirection use the server method (scenario 2) in server context.
- ❌ `БизнесПроцессыИЗадачиКлиент.ОткрытьФормуЗадачи(...)` - the method does not
  exist. To open the task itself use `ОткрытьЗначение(ЗадачаСсылка)` or
  parameterized opening via `ФормаВыполненияЗадачи` (scenario 6).

### 4. Accept tasks for execution and cancel acceptance

**Task:** mark an array of tasks as accepted by the current user, or cancel the
acceptance (executor change, role refusal).

**Functions:**
`БизнесПроцессыИЗадачиСервер.ПринятьЗадачиКИсполнению(Задачи) Экспорт`
`БизнесПроцессыИЗадачиСервер.ОтменитьПринятиеЗадачКИсполнению(Задачи) Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick
client, External connection. Client wrappers —
`БизнесПроцессыИЗадачиКлиент.ПринятьЗадачиКИсполнению(Знач МассивЗадач) Экспорт`
and `БизнесПроцессыИЗадачиКлиент.ОтменитьПринятиеЗадачКИсполнению(Знач МассивЗадач) Экспорт`.

**Parameters:**
- `Задачи` / `МассивЗадач` (Array of ЗадачаСсылка.ЗадачаИсполнителя) — tasks
  to accept / cancel.

**Example:**
```bsl
&НаКлиенте
Процедура ПринятьКИсполнению(Команда)
    ВыделенныеЗадачи = Элементы.СписокЗадач.ВыделенныеСтроки;
    БизнесПроцессыИЗадачиКлиент.ПринятьЗадачиКИсполнению(ВыделенныеЗадачи);
КонецПроцедуры

// On the server (for example, from an update handler)
&НаСервере
Процедура ПринятьНаСервере(МассивЗадач)
    БизнесПроцессыИЗадачиСервер.ПринятьЗадачиКИсполнению(МассивЗадач);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- Acceptance records that the executor has taken the task into work; cancellation
  removes the flag. The server variant does not show questions to the user; the
  client wrapper `ПринятьЗадачуКИсполнению(Форма, ТекущийПользователь)` (with a
  form) additionally asks for confirmation.
- ❌ Passing a single reference instead of an array - the methods expect `Массив`.
  Wrap it:
  `Новый Массив; Массив.Добавить(Ссылка)`.

### 5. Stop and resume a business process

**Task:** pause an active business process (for example, before administration)
and then make it active again.

**Functions:**
`БизнесПроцессыИЗадачиСервер.ОстановитьБизнесПроцесс(БизнесПроцесс) Экспорт`
`БизнесПроцессыИЗадачиСервер.СделатьАктивнымБизнесПроцесс(БизнесПроцесс) Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick
client, External connection.

**Parameters:**
- `БизнесПроцесс` (ОпределяемыйТип.БизнесПроцесс) — a reference to a business
  process of any kind that matches the defined type.

**Example:**
```bsl
// Stop
БизнесПроцессыИЗадачиСервер.ОстановитьБизнесПроцесс(БизнесПроцессСсылка);

// Resume (cancel stop)
БизнесПроцессыИЗадачиСервер.СделатьАктивнымБизнесПроцесс(БизнесПроцессСсылка);
```

**Nuances / anti-patterns:**
- There is no need to cast to a specific kind (`БизнесПроцессСсылка.Согласование`
  etc.) - the defined type resolves the required one itself.
- For batch processing there are `ОстановитьБизнесПроцессы(БизнесПроцессы)` and
  `СделатьАктивнымБизнесПроцессы(БизнесПроцессы)` (array) in the same module.
- Before stopping from an object form, use the client wrapper
  `БизнесПроцессыИЗадачиКлиент.ОстановитьБизнесПроцессИзФормыОбъекта(Форма)` /
  `ПродолжитьБизнесПроцессИзФормыОбъекта(Форма)` - they correctly check rights
  through `БизнесПроцессыИЗадачиПереопределяемый.ПриПроверкеПравНаОстановкуБизнесПроцесса`.

### 6. Get task execution form parameters and open the form

**Task:** for a task, get a structure of execution form parameters (form name,
key, parameters) depending on the business process, and open the form; plus
check whether the task is leading.

**Functions:**
`БизнесПроцессыИЗадачиСервер.ФормаВыполненияЗадачи(Знач ЗадачаСсылка) Экспорт`
— Function → form parameter structure (the set of keys is determined by the hook
`БизнесПроцессыИЗадачиПереопределяемый.ПриПолученииФормыВыполненияЗадачи`).
`БизнесПроцессыИЗадачиСервер.ЭтоВедущаяЗадача(ЗадачаСсылка) Экспорт`
— Function → Булево, whether the task is the leading (parent) task for its
business process.
— region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client,
External connection.

**Parameters:**
- `ЗадачаСсылка` (ЗадачаСсылка.ЗадачаИсполнителя) — task.

**Example:**
```bsl
&НаКлиенте
Процедура ОткрытьФормуВыполнения(ЗадачаСсылка)
    ПараметрыФормы = БизнесПроцессыИЗадачиСервер.ФормаВыполненияЗадачи(ЗадачаСсылка);
    Если ПараметрыФормы = Неопределено ИЛИ ПараметрыФормы.Количество() = 0 Тогда
        // The task has no business process - open the regular task form
        ОткрытьЗначение(ЗадачаСсылка);
        Возврат;
    КонецЕсли;
    ОткрытьФорму("Задача.ЗадачаИсполнителя.ФормаВыполненияЗадачи",
        ПараметрыФормы, ЭтаФорма);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Direct `ОткрытьФорму("Задача.ЗадачаИсполнителя.ФормаОбъекта", ...)` bypassing
  `ФормаВыполненияЗадачи` is an anti-pattern: it will not pass rights checks and
  will not substitute the execution form if it is defined by the hook.
- `ФормаВыполненияЗадачи` is a universal container; the form name, key and
  parameters depend on the application business process and are overridden in
  `БизнесПроцессыИЗадачиПереопределяемый.ПриПолученииФормыВыполненияЗадачи`.

### 7. Scheduled jobs: deadline control and notifications

**Task:** run the handlers of scheduled jobs `МониторингЗадач`
(overdue tasks) and `УведомлениеИсполнителейОНовыхЗадачах` (mailing about new
tasks), usually by the scheduled job itself, less often forcibly.

**Functions:**
`БизнесПроцессыИЗадачиСервер.ПроконтролироватьЗадачи() Экспорт`
`БизнесПроцессыИЗадачиСервер.УведомитьИсполнителейОНовыхЗадачах() Экспорт`
— Procedures without parameters, region `#Область ПрограммныйИнтерфейс`
(stable). Server, Thick client, External connection.

**Parameters:** none.

**Example:**
```bsl
// Handler for the scheduled job МониторингЗадач -
// sending notifications about overdue tasks from the system account
БизнесПроцессыИЗадачиСервер.ПроконтролироватьЗадачи();

// Handler for the scheduled job УведомлениеИсполнителейОНовыхЗадачах -
// mailing about new tasks for the period since the previous mailing
БизнесПроцессыИЗадачиСервер.УведомитьИсполнителейОНовыхЗадачах();
```

**Nuances / anti-patterns:**
- Running them manually from application code is usually **not necessary** -
  the scheduled job will call them itself. Forced execution is allowed
  (for example, in an information base update handler), but keep in mind the
  duration of the mailing.
- `ПроконтролироватьЗадачи`: if a task is sent “nowhere” (a role with an empty
  executor list) - a new task is created for the person responsible for role
  setup.
- The mailing is sent by email **from the system account** and may take a
  noticeable amount of time; do not call it from an interactive handler.
- ❌ Creating a task manually with `Задачи.ЗадачаИсполнителя.СоздатьЗадачу()`
  without filling in `БизнесПроцесс` and `ТочкаМаршрута` - the
  task ↔ business process ↔ routing point relationship is mandatory for the
  scheduled handlers and `ЭтоВедущаяЗадача` to work.

## Additional

Other stable methods of `БизнесПроцессыИЗадачиСервер` (region
`ПрограммныйИнтерфейс`), full signatures - via
`python scripts/bsp_api.py method <Имя> --module БизнесПроцессыИЗадачиСервер --src src/cf`:

- `ИсполнительСтрокой(Знач Исполнитель, Знач РольИсполнителя, Знач ОсновнойОбъектАдресации = Неопределено, Знач ДополнительныйОбъектАдресации = Неопределено)` — string representation of the executor for reports/UI.
- `РольСтрокой(Знач РольИсполнителя, Знач ОсновнойОбъектАдресации = Неопределено, Знач ДополнительныйОбъектАдресации = Неопределено)` — string representation of the role.
- `БизнесПроцессыВедущейЗадачи(ЗадачаСсылка, ДляИзменения = Ложь)` / `БизнесПроцессыГлавнойЗадачи(ЗадачаСсылка, ДляИзменения = Ложь)` — business processes of the leading/main task.
- `ДатаЗавершенияБизнесПроцесса(БизнесПроцессСсылка)` — completion date.
- `УстановитьПометкуУдаленияЗадач(БизнесПроцессСсылка, ПометкаУдаления)` — mass delete mark for tasks.
- `ЗаблокироватьБизнесПроцессы(БизнесПроцессы)` / `ЗаблокироватьЗадачи(Задачи)` — locking by references (for modification in a transaction).
- `ГруппаИсполнителейЗадач(РольИсполнителя, ОсновнойОбъектАдресации, ДополнительныйОбъектАдресации)` — executor group of a role.
- `ЗаполнитьГлавнуюЗадачу(БизнесПроцессОбъект, ДанныеЗаполнения)` — filling the main task.
- Deferred start: `ДобавитьПроцессДляОтложенногоСтарта(Процесс, ДатаСтарта)`, `ОтключитьОтложенныйСтартПроцесса(Процесс)`, `СтартоватьОтложенныйПроцесс(БизнесПроцесс)`, `ПараметрыОтложенногоПроцесса(Процесс)`, `ДатаОтложенногоСтартаПроцесса(БизнесПроцесс)`.

Override hooks (module `БизнесПроцессыИЗадачиПереопределяемый`, region
`ПрограммныйИнтерфейс` - **БСП calls, application code implements**):

- `ПриПолученииФормыВыполненияЗадачи(ИмяБизнесПроцесса, ЗадачаСсылка, ТочкаМаршрутаБизнесПроцесса, ПараметрыФормы)` — fill the task execution form parameters (used by `ФормаВыполненияЗадачи`).
- `ПриОпределенииБизнесПроцессов(ПодключенныеБизнесПроцессы)` — list of connected business processes.
- `ПриЗаполненииГлавнойЗадачиБизнесПроцесса(БизнесПроцессОбъект, ДанныеЗаполнения, СтандартнаяОбработка)` — filling the main task.
- `ПриПроверкеПравНаОстановкуБизнесПроцесса(БизнесПроцесс, ЕстьПрава, СтандартнаяОбработка)` — rights to stop.

Client hook `БизнесПроцессыИЗадачиКлиентПереопределяемый.ПриВыбореИсполнителя(ЭлементИсполнитель, РеквизитИсполнитель, ТолькоПростыеРоли, БезВнешнихРолей, СтандартнаяОбработка)` —
implemented in the application module, called by БСП when choosing an executor.
