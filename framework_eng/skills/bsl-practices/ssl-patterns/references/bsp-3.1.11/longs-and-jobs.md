# Long Operations and Scheduled Jobs

Two related БСП infrastructure mechanisms: **ДлительныеОперации***
(functional block inside `БазоваяФункциональность` - running server
code in a background job with UI waiting, progress, and cancellation) and the subsystem
**РегламентныеЗадания** (programmatic control of the schedule and state
of scheduled jobs). Needed when a server call lasts > 1 second and the form should not
freeze, or when a scheduled job needs to be scheduled/found/changed
from code.

## Modules

Suffix-based naming system (one root + execution context):

- `ДлительныеОперации` - server, external connection: starting background jobs,
  progress, cancellation, state reading. Stable API.
- `ДлительныеОперацииКлиент` - thin/thick client: waiting for completion,
  progress form, waiting parameters.
- `ДлительныеОперацииВызовСервера` - ⚠️ contains only `ФоновоеЗаданиеЗавершено`
  in `СлужебныеПроцедурыИФункции`; not for direct use from application code.
- `РегламентныеЗаданияСервер` - server, external connection: stable CRUD
  for scheduled jobs (`НайтиЗадания`, `ДобавитьЗадание`, `ИзменитьЗадание`,
  `УдалитьЗадание`, `УстановитьРасписаниеРегламентногоЗадания`,
  etc.).
- `РегламентныеЗаданияКлиент` - ⚠️ service module (region
  `СлужебныйПрограммныйИнтерфейс`): navigation to settings, blocking external
  resources.
- `РегламентныеЗаданияПереопределяемый` - **hook**: БСП calls it, application code
  implements it (copies the override module and overrides the body). Do not
  call directly.
- `РегламентныеЗаданияСлужебный` - ⚠️ service API, backward compatibility is not
  guaranteed.

⚠️ **The `РегламентныеЗадания` module (without a suffix) does NOT exist** as a common
module. CRUD operations are in `РегламентныеЗаданияСервер`. Schedule conversion
is in `ОбщегоНазначенияКлиентСервер.РасписаниеВСтруктуру` /
`СтруктураВРасписание` (region `ПрограммныйИнтерфейс`, area
`РегламентныеЗадания`).
⚠️ **The `ДлительныеОперацииСлужебный` module does NOT exist** - service methods
are built into `ДлительныеОперации` itself in `СлужебныйПрограммныйИнтерфейс`.

## Scenarios

### 1. Run a function in the background with a return value

**Task:** perform heavy server-side processing in a background job, return the result to the client through temporary storage, without blocking the form.

**Functions:**
`ДлительныеОперации.ВыполнитьФункцию(Знач ПараметрыВыполнения, ИмяФункции, Знач Параметр1 = Неопределено, Знач Параметр2 = Неопределено, Знач Параметр3 = Неопределено, Знач Параметр4 = Неопределено, Знач Параметр5 = Неопределено, Знач Параметр6 = Неопределено, Знач Параметр7 = Неопределено) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server, external connection.
`ДлительныеОперации.ПараметрыВыполненияФункции(Знач ИдентификаторФормы) Экспорт`
— Function (stable), parameter structure constructor.
`ДлительныеОперацииКлиент.ОжидатьЗавершение(Знач ДлительнаяОперация, Знач ОповещениеОЗавершении = Неопределено, Знач ПараметрыОжидания = Неопределено) Экспорт`
— Procedure (stable), client.

**Parameters:**
- `ПараметрыВыполнения` (ФормаКлиентскогоПриложения / УникальныйИдентификатор /
  Структура) — for `ВыполнитьФункцию`: owner form, its identifier, or
  the structure from `ПараметрыВыполненияФункции`.
- `ИмяФункции` (Строка) — the name of the export function of a common module, manager module, or data processor, e.g. `"Обработка.МояОбработка.ПодготовитьДанные"`.
- `Параметр1…7` (Произвольный) — parameters of the called function; values and
  return value must be serializable. Parameters must not be return values.
- `ДлительнаяОперация` (Структура) — result of `ВыполнитьФункцию`: `Статус`,
  `ИдентификаторЗадания`, `АдресРезультата`.
- `ОповещениеОЗавершении` (ОписаниеОповещения) — handler
  `<ИмяПроцедуры>(Результат, ДопПараметры) Экспорт`.
- `ПараметрыОжидания` (Структура) — from `ДлительныеОперацииКлиент.ПараметрыОжидания`.

**Example:**
```bsl
// Серверная функция — будет выполнена в фоне.
Функция ПодготовитьДанныеОтчёта(Знач Параметр1, Знач Параметр2) Экспорт
    ДлительныеОперации.СообщитьПрогресс(10, "Загрузка справочников");
    // ...тяжёлая работа...
    ДлительныеОперации.СообщитьПрогресс(90, "Формирование таблицы");
    Возврат Результат;  // попадёт в АдресРезультата
КонецФункции

&НаСервере
Функция ЗапуститьПодготовку()
    ПараметрыВыполнения = ДлительныеОперации.ПараметрыВыполненияФункции(УникальныйИдентификатор);
    Возврат ДлительныеОперации.ВыполнитьФункцию(
        ПараметрыВыполнения,
        "Обработка.МояОбработка.ПодготовитьДанныеОтчёта",
        Параметр1, Параметр2);
КонецФункции

&НаКлиенте
Процедура Запустить(Команда)
    ДлительнаяОперация = ЗапуститьПодготовку();
    Оповещение = Новый ОписаниеОповещения("ОбработатьРезультат", ЭтотОбъект);
    ДлительныеОперацииКлиент.ОжидатьЗавершение(ДлительнаяОперация, Оповещение,
        ДлительныеОперацииКлиент.ПараметрыОжидания(ЭтотОбъект));
КонецПроцедуры

&НаКлиенте
Процедура ОбработатьРезультат(Результат, ДопПараметры) Экспорт
    Если Результат = Неопределено Тогда Возврат; КонецЕсли;
    Если Результат.Статус = "Ошибка" Тогда
        СтандартныеПодсистемыКлиент.ВывестиИнформациюОбОшибке(Результат.ИнформацияОбОшибке);
        Возврат;
    КонецЕсли;
    Данные = ПолучитьИзВременногоХранилища(Результат.АдресРезультата);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Synchronous call to a heavy function in `&НаКлиенте` — the form freezes for
  minutes. Always `ВыполнитьФункцию` + `ОжидатьЗавершение`.
- ❌ Direct `ФоновыеЗадания.Выполнить(...)` — bypasses the event log,
  performance measurements, BSP error handling. Only through
  `ДлительныеОперации.ВыполнитьФункцию` / `ВыполнитьПроцедуру`.
- The background job runs **outside the form transaction**; messages
  `СообщитьПользователю` from the background accumulate and are delivered to the client through
  `ДлительныеОперации.СообщенияПользователю`.

### 2. Run a procedure in the background without a return value

**Task:** perform background processing (mailing, exchange, loading) without
waiting for a result.

**Functions:**
`ДлительныеОперации.ВыполнитьПроцедуру(Знач ПараметрыВыполнения = Неопределено, ИмяПроцедуры, Знач Параметр1 = Неопределено, Знач Параметр2 = Неопределено, Знач Параметр3 = Неопределено, Знач Параметр4 = Неопределено, Знач Параметр5 = Неопределено, Знач Параметр6 = Неопределено, Знач Параметр7 = Неопределено) Экспорт`
— Function (stable). Server, external connection.
`ДлительныеОперации.ПараметрыВыполненияПроцедуры() Экспорт` — Function (stable),
a parameter constructor (no arguments).

**Parameters:**
- `ПараметрыВыполнения` (Structure) — from `ПараметрыВыполненияПроцедуры`; you can
  set `НаименованиеФоновогоЗадания` for the waiting form title.
- `ИмяПроцедуры` (String) — name of the export procedure (without a return
  value).
- `Параметр1…7` (Any) — procedure parameters, serializable.

**Example:**
```bsl
&НаСервере
Процедура ЗапуститьРассылку()
    ПараметрыВыполнения = ДлительныеОперации.ПараметрыВыполненияПроцедуры();
    ПараметрыВыполнения.НаименованиеФоновогоЗадания = "Рассылка уведомлений";
    ДлительныеОперации.ВыполнитьПроцедуру(
        ПараметрыВыполнения,
        "Обработка.РассылкаУведомлений.ВыполнитьРассылку",
        СписокПолучателей);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ `ВыполнитьПроцедуру(Параметры, П1, "Обработка.Моя.Метод")` — `ИмяПроцедуры`
  is the **second** parameter, not the last. Order: `ПараметрыВыполнения,
  ИмяПроцедуры, Параметр1…`.
- For a procedure without parameters, you can pass `ПараметрыВыполнения = Неопределено`
  (by default) — a temporary owner form will be created.

### 3. Show progress and retrieve user messages

**Purpose:** display the percentage/text of a long-running operation in the standard waiting form and collect accumulated user messages.

**Functions:**
`ДлительныеОперации.СообщитьПрогресс(Знач Процент = Неопределено, Знач Текст = Неопределено, Знач ДополнительныеПараметры = Неопределено) Экспорт`
— Procedure (stable). Called **inside the body of a background procedure/function**.
`ДлительныеОперации.ПрочитатьПрогресс(Знач ИдентификаторЗадания) Экспорт` —
Function (stable), server.
`ДлительныеОперации.СообщенияПользователю(УдалятьПолученные = Ложь, ИдентификаторЗадания = Неопределено) Экспорт`
— Function (stable), server.
`ДлительныеОперацииКлиент.ПараметрыОжидания(ФормаВладелец) Экспорт` — Function
(stable), client; returns a structure with the properties `ВыводитьОкноОжидания`,
`ВыводитьПрогрессВыполнения`, `ОповещениеОПрогрессеВыполнения`, `ВыводитьСообщения`,
`Интервал`.

**Parameters:**
- `Процент` (Number) — 0…100; `Неопределено` — text only without a percentage.
- `Текст` (String) — description of the current step.
- `ДополнительныеПараметры` (Arbitrary) — arbitrary data, passed to
  `ОповещениеОПрогрессеВыполнения`.
- `ИдентификаторЗадания` (UniqueIdentifier) — background job identifier.
- `УдалятьПолученные` (Boolean) — `Истина` removes read messages from the
  queue.

**Example:**
```bsl
// В теле фоновой процедуры
Процедура ОбработатьНаборДанных(Параметры) Экспорт
    Для Сч = 1 По Параметры.КоличествоЦикл Цикл
        // ...обработка порции...
        ДлительныеОперации.СообщитьПрогресс(Сч * 100 / Параметры.КоличествоЦикл,
            "Обработано " + Сч + " из " + Параметры.КоличествоЦикл);
    КонецЦикла;
КонецПроцедуры

// На клиенте — включить вывод прогресса в форме ожидания
&НаКлиенте
Процедура Запустить()
    ПараметрыОжидания = ДлительныеОперацииКлиент.ПараметрыОжидания(ЭтотОбъект);
    ПараметрыОжидания.ВыводитьПрогрессВыполнения = Истина;
    ДлительныеОперацииКлиент.ОжидатьЗавершение(ДлительнаяОперация, Оповещение, ПараметрыОжидания);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ⚠️ Do not report progress more than 100 times per operation — excessive memory consumption
  and leaks. Messages sent more often than every 3 seconds replace the previous one.
- ❌ Use `СообщитьПрогресс` to send the result in parts — that is not what it is for; the
  result is returned through `АдресРезультата`.
- Progress is visible to the user only if `ВыводитьПрогрессВыполнения = Истина`
  in `ПараметрыОжидания`.

### 4. Cancel a background job and check its status

**Task:** cancel a long-running operation on the user's command and find out
the job state without opening the wait form.

**Functions:**
`ДлительныеОперации.ОтменитьВыполнениеЗадания(Знач ИдентификаторЗадания) Экспорт`
— Procedure (stable). Server.
`ДлительныеОперации.ЗаданиеВыполнено(Знач ИдентификаторЗадания, РасширенныйРезультат = Ложь) Экспорт`
— Function (stable). Server. Returns `Булево` or `Структуру` (when
`РасширенныйРезультат = Истина`) with the `Статус` property:
`"Выполняется"`/`"Выполнено"`/`"Ошибка"`/`"Отменено"`.

**Parameters:**
- `ИдентификаторЗадания` (УникальныйИдентификатор) — from
  `ДлительнаяОперация.ИдентификаторЗадания`.
- `РасширенныйРезультат` (Булево) — `Истина` → structure with details; `Ложь`
  (default) → just `Булево` (completed or not).

**Example:**
```bsl
// Отмена по команде пользователя
&НаСервере
Процедура ОтменитьОперацию(ИдентификаторЗадания)
    ДлительныеОперации.ОтменитьВыполнениеЗадания(ИдентификаторЗадания);
КонецПроцедуры

// Поллинг состояния без формы ожидания (напр., из внешнего соединения)
Статус = ДлительныеОперации.ЗаданиеВыполнено(ИдентификаторЗадания, Истина).Статус;
Если Статус = "Отменено" Тогда
    // пользователь отменил
ИначеЕсли Статус = "Ошибка" Тогда
    // при РасширенныйРезультат = Истина исключение не пробрасывается,
    // а кладётся в Свойства.ИнформацияОбОшибке
КонецЕсли;
```

**Nuances / anti-patterns:**
- ⚠️ On an abnormal termination, `ЗаданиеВыполнено` **throws an exception** with
  the error text from the background job. Wrap it in `Попытка…Исключение`
  if you need graceful handling.
- `ОтменитьВыполнениеЗадания` only initiates cancellation; in fact, the job
  will not stop instantly - check the status via `ЗаданиеВыполнено`.
- ❌ `ДлительныеОперацииВызовСервера.ФоновоеЗаданиеЗавершено` — service
  (`СлужебныеПроцедурыИФункции` region); use the stable
  `ДлительныеОперации.ЗаданиеВыполнено`.

### 5. Create a scheduled job with a schedule

**Task:** programmatically create a scheduled job (during initial
information base filling, configuration update) with a schedule and an application key.

**Functions:**
`РегламентныеЗаданияСервер.ДобавитьЗадание(Параметры) Экспорт` — Function
(stable). Server, external connection. Returns the created job.
`ОбщегоНазначенияКлиентСервер.РасписаниеВСтруктуру(Знач Расписание) Экспорт` —
Function (stable), `РасписаниеРегламентногоЗадания` → `Структура`.
`ОбщегоНазначенияКлиентСервер.СтруктураВРасписание(Знач СтруктураРасписания) Экспорт`
— Function (stable), `Структура` → `РасписаниеРегламентногоЗадания`.

**Parameters:**
- `Параметры` (Structure) — for `ДобавитьЗадание`: `Метаданные`
  (ObjectMetadataScheduledJob, required), `Расписание`
  (РасписаниеРегламентногоЗадания), `Использование` (Boolean), `Ключ` (String,
  application identifier), `Параметры` (Array — handler method parameters),
  `ИнтервалПовтораПриАварийномЗавершении` (Number, sec),
  `КоличествоПовторовПриАварийномЗавершении` (Number).
- `Расписание` (РасписаниеРегламентногоЗадания) — source schedule.
- `СтруктураРасписания` (Structure) — schedule fields: `ПериодПовтораДней`,
  `ПериодПовтораВТечениеДня`, `ДниНедели`, `ВремяНачала`, `ВремяКонца`,
  `ДатаНачала`, `ДатаКонца` etc.

**Example:**
```bsl
// Расписание удобнее собирать в структуре (на клиенте), затем конвертировать
РасписаниеСтруктурой = ОбщегоНазначенияКлиентСервер.РасписаниеВСтруктуру(РасписаниеРегламентногоЗадания);
РасписаниеСтруктурой.ПериодПовтораДней = 1;
РасписаниеСтруктурой.ПериодПовтораВТечениеДня = 3600;
РасписаниеОбъектом = ОбщегоНазначенияКлиентСервер.СтруктураВРасписание(РасписаниеСтруктурой);

ПараметрыЗадания = Новый Структура;
ПараметрыЗадания.Вставить("Метаданные",   Метаданные.РегламентныеЗадания.МояЗадача);
ПараметрыЗадания.Вставить("Расписание",   РасписаниеОбъектом);
ПараметрыЗадания.Вставить("Использование", Истина);
ПараметрыЗадания.Вставить("Ключ",         "МояЗадача_Основная");
Задание = РегламентныеЗаданияСервер.ДобавитьЗадание(ПараметрыЗадания);
```

**Nuances / antipatterns:**
- ❌ Call `РегламентныеЗаданияСервер.ДобавитьЗадание` from client code —
  the module is server-side, it will fail on a thin client. Only through `&НаСервере` /
  `&НаСервереБезКонтекста`.
- ❌ `РегламентныеЗадания.ДобавитьЗадание(...)` — the module without the suffix does not
  exist. Only `РегламентныеЗаданияСервер`.
- In the service model, `ДобавитьЗадание` creates a record in the catalog
  `ОчередьЗаданий`, not a platform scheduled job.

### 6. Find, modify, and delete a scheduled job

**Task:** find a job by application key, change its schedule or
enable/disable it, remove duplicates.

**Functions:**
`РегламентныеЗаданияСервер.НайтиЗадания(Отбор) Экспорт` — Function (stable).
Returns an array of jobs (local mode) or a value table (service
model).
`РегламентныеЗаданияСервер.ИзменитьЗадание(Знач Идентификатор, Знач Параметры) Экспорт`
— Procedure (stable).
`РегламентныеЗаданияСервер.УдалитьЗадание(Знач Идентификатор) Экспорт` —
Procedure (stable).
`РегламентныеЗаданияСервер.УстановитьРасписаниеРегламентногоЗадания(Знач Идентификатор, Знач Расписание) Экспорт`
— Procedure (stable).
`РегламентныеЗаданияСервер.УстановитьИспользованиеРегламентногоЗадания(Знач Идентификатор, Знач Использование) Экспорт`
— Procedure (stable).

**Parameters:**
- `Отбор` (Structure) — properties: `Метаданные`
  (ОбъектМетаданныхРегламентноеЗадание), `Ключ` (String), `Использование`
  (Boolean), `УникальныйИдентификатор` (УникальныйИдентификатор / String /
  `СправочникСсылка.ОчередьЗаданий`).
- `Идентификатор` (УникальныйИдентификатор / String / MetadataObject) — for
  `ИзменитьЗадание`/`УдалитьЗадание`/`Установить*`.
- `Параметры` (Structure) — mutable properties for `ИзменитьЗадание` (the same
  ones as in `ДобавитьЗадание`).
- `Расписание` (ScheduleOfScheduledJob) — new schedule.
- `Использование` (Boolean) — `Истина` enables execution by schedule.

**Example:**
```bsl
// Find by key and change the schedule, remove duplicates
Отбор = Новый Структура("Ключ", "МояЗадача_Основная");
Задания = РегламентныеЗаданияСервер.НайтиЗадания(Отбор);
Если Задания.Количество() > 0 Тогда
    Идентификатор = Задания[0].УникальныйИдентификатор;
    РегламентныеЗаданияСервер.УстановитьРасписаниеРегламентногоЗадания(Идентификатор, НовоеРасписание);
    // Remove accidental duplicates — keep only the first
    Для Сч = 1 По Задания.ВГраница() Цикл
        РегламентныеЗаданияСервер.УдалитьЗадание(Задания[Сч].УникальныйИдентификатор);
    КонецЦикла;
КонецЕсли;

// Disable the job on error
РегламентныеЗаданияСервер.УстановитьИспользованиеРегламентногоЗадания(Идентификатор, Ложь);
```

**Nuances / antipatterns:**
- ❌ Create jobs with one `Метаданные` and different keys for each
  organization — they conflict at startup. Better to have one job, iterate inside
  the handler; or use different `Метаданные`.
- `НайтиЗадания` without a filter returns all jobs — expensive; always pass a
  filter.
- In the scheduled job handler, check that it still exists
  (`НайтиЗадания(Отбор).Количество() > 0`) before doing the work — the job may
  have been deleted.

### 7. Multithreaded Execution and Correct Error Handling

**Task:** parallelize processing of a large data set across several
background threads and safely handle an error in a scheduled task handler.

**Functions:**
`ДлительныеОперации.ВыполнитьФункциюВНесколькоПотоков(ИмяФункции, ПараметрыВыполнения, НаборПараметровФункции = Неопределено) Экспорт`
— Function (stable). Server.
`ДлительныеОперации.ВыполнитьПроцедуруВНесколькоПотоков(ИмяПроцедуры, ПараметрыВыполнения, НаборПараметровПроцедуры = Неопределено) Экспорт`
— Function (stable). Server.
`ДлительныеОперации.ДопустимоеКоличествоПотоков() Экспорт` — Function
(⚠️ region `СлужебныйПрограммныйИнтерфейс`), returns the maximum
allowed number of threads.

**Parameters:**
- `ИмяФункции` / `ИмяПроцедуры` (String) — export function/procedure of a common
  module or manager module.
- `ПараметрыВыполнения` (Structure) — from `ПараметрыВыполненияФункции` /
  `ПараметрыВыполненияПроцедуры`.
- `НаборПараметровФункции` (Map from KeyAndValue / `Неопределено`)
  — a map where Key is Arbitrary, Value is an Array of function call parameters
  (0..7); one entry per thread. `Неопределено` — one parameter set for all threads.
- `НаборПараметровПроцедуры` (Map from KeyAndValue / `Неопределено`)
  — a map where Key is Arbitrary, Value is an Array of procedure call parameters
  (0..7); one entry per thread. `Неопределено` — one parameter set for all threads.

**Example:**
```bsl
// Multithreaded processing of batches
ПараметрыВыполнения = ДлительныеОперации.ПараметрыВыполненияФункции(УникальныйИдентификатор);
НаборПараметров = Новый Массив;
Для Каждого Порция Из ПорцииДанных Цикл
    НаборПараметров.Добавить(Новый Структура("Данные", Порция));
КонецЦикла;
ДлительнаяОперация = ДлительныеОперации.ВыполнитьФункциюВНесколькоПотоков(
    "Обработка.МояОбработка.ОбработатьПорцию", ПараметрыВыполнения, НаборПараметров);

// Scheduled task handler protected against errors
Процедура МояЗадача() Экспорт
    Ключ = "МояЗадача_Основная";
    Если РегламентныеЗаданияСервер.НайтиЗадания(Новый Структура("Ключ", Ключ)).Количество() = 0 Тогда
        Возврат; // task was deleted
    КонецЕсли;
    Попытка
        // ...main work...
    Исключение
        Ид = РегламентныеЗаданияСервер.НайтиЗадания(Новый Структура("Ключ", Ключ))[0].УникальныйИдентификатор;
        РегламентныеЗаданияСервер.УстановитьИспользованиеРегламентногоЗадания(Ид, Ложь);
        ОбщегоНазначения.ЗаписатьВЖурналРегистрации(УровеньЖурналаРегистрации.Ошибка,, , ,
            "МояЗадача", ИнформацияОбОшибке());
        ВызватьИсключение;
    КонецПопытки;
КонецПроцедуры
```

**Nuances / antipatterns:**
- ⚠️ `ДопустимоеКоличествоПотоков` — a service region; for application code
  rely on `ДлительныеОперации.ПараметрыВыполненияФункции` and do not exceed the
  platform limit on the number of background jobs.
- ❌ Transactional writes inside a thread without `Попытка…Исключение` — an error
  in one thread aborts the entire operation. Each thread is an independent processing
  of its own batch.
- `ВыполнитьВФоне(ИмяПроцедуры, ПараметрыПроцедуры, ПараметрыВыполнения)` —
  stable (region `ПрограммныйИнтерфейс`), but the doc comment recommends
  `ВыполнитьФункцию`/`ВыполнитьПроцедуру` (arbitrary number of parameters up to 7,
  without a `Параметры`/`АдресРезультата` wrapper). In new code — `ВыполнитьФункцию`.
- ⚠️ `ЗапуститьВыполнениеВФоне` — region `УстаревшиеПроцедурыИФункции`
  (deprecated), do not use in new code; alternative is `ВыполнитьВФоне`
  (which itself is recommended to be replaced with `ВыполнитьФункцию`/
  `ВыполнитьПроцедуру`).

## Additionally

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures are
available via `python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `РегламентныеЗаданияСервер.РасписаниеРегламентногоЗадания(Знач Идентификатор, Знач ВСтруктуре = Ложь)` — get the schedule (as an object or a structure).
- `РегламентныеЗаданияСервер.РегламентноеЗаданиеИспользуется(Знач Идентификатор)` — Boolean, whether the task is enabled.
- `РегламентныеЗаданияСервер.ПолучитьРегламентноеЗадание(Знач Идентификатор)` — task object.
- `РегламентныеЗаданияСервер.СвойстваПоследнегоЗадания(Знач Задание)` — properties of the last background execution.
- `РегламентныеЗаданияСервер.РаботаСВнешнимиРесурсамиЗаблокирована()` /
  `ЗаблокироватьРаботуСВнешнимиРесурсами()` /
  `РазблокироватьРаботуСВнешнимиРесурсами()` — control of blocking external
  resources during update.
- `ДлительныеОперацииКлиент.НовыйРезультатДлительнойОперации()` /
  `НовоеСостояниеДлительнойОперации()` — constructors of stable structures
  (region `ПрограммныйИнтерфейс`).
- Override hook:
  `РегламентныеЗаданияПереопределяемый.ПриОпределенииНастроекРегламентныхЗаданий(Настройки)`
  — called by БСП, implemented by application code (module `*Переопределяемый`);
  setting for blocking scheduled tasks in service model. Do not call
  directly.
- ⚠️ Service (region `СлужебныйПрограммныйИнтерфейс` /
  `СлужебныеПроцедурыИФункции`), do not use in application code:
  `РегламентныеЗаданияСлужебный.ВыполнитьРегламентноеЗаданиеВручную(Знач Задание)`,
  `РегламентныеЗаданияСлужебный.ПолучитьСвойстваФоновогоЗадания(Идентификатор, ИменаСвойств = "")`,
  `ДлительныеОперации.ОперацияВыполнена(Знач ИдентификаторЗадания, Задание = Неопределено)`.