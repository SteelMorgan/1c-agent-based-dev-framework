# Long-running operations and scheduled jobs

Two related BSP infrastructure mechanisms: **ДлительныеОперации***
(a functional block inside `БазоваяФункциональность` - launching server-side
code in a background job with UI waiting, progress, and cancellation) and the
**РегламентныеЗадания** subsystem (programmatic management of scheduled job
schedule and state). They are needed when a server call lasts > 1 second and
the form should not “freeze”, or when you need to schedule/find/change a
scheduled job from code.

## Modules

Suffix-based naming scheme (one root + execution context):

- `ДлительныеОперации` - server, external connection: launching background
  jobs, progress, cancellation, state reading. Main stable API.
- `ДлительныеОперацииКлиент` - thin/thick client: waiting for completion,
  progress form, waiting parameters.
- `ДлительныеОперацииВызовСервера` - ⚠️ contains only `ФоновоеЗаданиеЗавершено`
  in `СлужебныеПроцедурыИФункции`; not for direct use from application code.
- `РегламентныеЗаданияСервер` - server, external connection: stable CRUD for
  scheduled jobs (`НайтиЗадания`, `ДобавитьЗадание`, `ИзменитьЗадание`,
  `УдалитьЗадание`, `УстановитьРасписаниеРегламентногоЗадания`, etc.).
- `РегламентныеЗаданияКлиент` - client: navigate to settings, lock external
  resources.
- `РегламентныеЗаданияПереопределяемый` - **hook**: BSP calls it, application
  code implements it (copies the override module and overrides the body). Do
  not call directly.
- `РегламентныеЗаданияСлужебный` - ⚠️ service API, backward compatibility is
  not guaranteed.

⚠️ **Module `РегламентныеЗадания` (without a suffix) DOES NOT exist** as a
common module. CRUD operations live in `РегламентныеЗаданияСервер`.
Schedule conversion is in `ОбщегоНазначенияКлиентСервер.РасписаниеВСтруктуру`
/ `СтруктураВРасписание` (region `ПрограммныйИнтерфейс`, area
`РегламентныеЗадания`).
⚠️ **Module `ДлительныеОперацииСлужебный` DOES NOT exist** - service methods
are built into `ДлительныеОперации` itself in `СлужебныйПрограммныйИнтерфейс`.

## Scenarios

### 1. Run a function in the background with a return value

**Task:** perform heavy server-side processing in a background job, return the
result to the client via temporary storage, without blocking the form.

**Functions:**
`ДлительныеОперации.ВыполнитьФункцию(Знач ПараметрыВыполнения, ИмяФункции, Знач Параметр1 = Неопределено, Знач Параметр2 = Неопределено, Знач Параметр3 = Неопределено, Знач Параметр4 = Неопределено, Знач Параметр5 = Неопределено, Знач Параметр6 = Неопределено, Знач Параметр7 = Неопределено) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server, external
connection.
`ДлительныеОперации.ПараметрыВыполненияФункции(Знач ИдентификаторФормы) Экспорт`
— Function (stable), parameter structure constructor.
`ДлительныеОперацииКлиент.ОжидатьЗавершение(Знач ДлительнаяОперация, Знач ОповещениеОЗавершении = Неопределено, Знач ПараметрыОжидания = Неопределено) Экспорт`
— Procedure (stable), client.

**Parameters:**
- `ПараметрыВыполнения` (ФормаКлиентскогоПриложения / УникальныйИдентификатор /
  Структура) - for `ВыполнитьФункцию`: owner form, its identifier, or a
  structure from `ПараметрыВыполненияФункции`.
- `ИмяФункции` (Строка) - name of an export function of a common module, a
  manager module, or a processing module, e.g.
  `"Обработка.МояОбработка.ПодготовитьДанные"`.
- `Параметр1…7` (Произвольный) - parameters of the called function; values and
  return value must be serializable. Parameters must not be returnable.
- `ДлительнаяОперация` (Структура) - result of `ВыполнитьФункцию`: `Статус`,
  `ИдентификаторЗадания`, `АдресРезультата`.
- `ОповещениеОЗавершении` (ОписаниеОповещения) - handler
  `<ИмяПроцедуры>(Результат, ДопПараметры) Экспорт`.
- `ПараметрыОжидания` (Структура) - from `ДлительныеОперацииКлиент.ПараметрыОжидания`.

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
- ❌ Synchronous call of a heavy function in `&НаКлиенте` - the form “freezes”
  for minutes. Always use `ВыполнитьФункцию` + `ОжидатьЗавершение`.
- ❌ Direct `ФоновыеЗадания.Выполнить(...)` - bypasses the registration log,
  performance measurements, and BSP error handling. Only through
  `ДлительныеОперации.ВыполнитьФункцию` / `ВыполнитьПроцедуру`.
- The background job runs **outside** the form transaction; `СообщитьПользователю`
  messages from the background are accumulated and delivered to the client
  through `ДлительныеОперации.СообщенияПользователю`.

### 2. Run a procedure in the background without a return value

**Task:** perform background processing (mailing, exchange, loading) without
waiting for the result.

**Functions:**
`ДлительныеОперации.ВыполнитьПроцедуру(Знач ПараметрыВыполнения = Неопределено, ИмяПроцедуры, Знач Параметр1 = Неопределено, Знач Параметр2 = Неопределено, Знач Параметр3 = Неопределено, Знач Параметр4 = Неопределено, Знач Параметр5 = Неопределено, Знач Параметр6 = Неопределено, Знач Параметр7 = Неопределено) Экспорт`
— Function (stable). Server, external connection.
`ДлительныеОперации.ПараметрыВыполненияПроцедуры() Экспорт` — Function (stable),
parameter constructor (no arguments).

**Parameters:**
- `ПараметрыВыполнения` (Структура) - from `ПараметрыВыполненияПроцедуры`; you
  can set `НаименованиеФоновогоЗадания` for the waiting form title.
- `ИмяПроцедуры` (Строка) - name of an export procedure (no return value).
- `Параметр1…7` (Произвольный) - procedure parameters, serializable.

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
- ❌ `ВыполнитьПроцедуру(Параметры, П1, "Обработка.Моя.Метод")` -
  `ИмяПроцедуры` is the **second** parameter, not the last one. Order:
  `ПараметрыВыполнения, ИмяПроцедуры, Параметр1…`.
- For a procedure without parameters, you can pass `ПараметрыВыполнения = Неопределено`
  (default) - a temporary owner form will be created.

### 3. Show progress and get user messages

**Task:** display the percentage/text of a long-running operation in the
standard waiting form and retrieve accumulated user messages.

**Functions:**
`ДлительныеОперации.СообщитьПрогресс(Знач Процент = Неопределено, Знач Текст = Неопределено, Знач ДополнительныеПараметры = Неопределено) Экспорт`
— Procedure (stable). Called **inside the background procedure/function**.
`ДлительныеОперации.ПрочитатьПрогресс(Знач ИдентификаторЗадания) Экспорт` —
Function (stable), server.
`ДлительныеОперации.СообщенияПользователю(УдалятьПолученные = Ложь, ИдентификаторЗадания = Неопределено) Экспорт`
— Function (stable), server.
`ДлительныеОперацииКлиент.ПараметрыОжидания(ФормаВладелец) Экспорт` — Function
(stable), client; returns a structure with properties
`ВыводитьОкноОжидания`, `ВыводитьПрогрессВыполнения`,
`ОповещениеОПрогрессеВыполнения`, `ВыводитьСообщения`, `Интервал`.

**Parameters:**
- `Процент` (Число) - 0…100; `Неопределено` - text only, without a percentage.
- `Текст` (Строка) - description of the current step.
- `ДополнительныеПараметры` (Произвольный) - arbitrary data, passed to
  `ОповещениеОПрогрессеВыполнения`.
- `ИдентификаторЗадания` (УникальныйИдентификатор) - background job identifier.
- `УдалятьПолученные` (Булево) - `Истина` removes read messages from the queue.

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
- ⚠️ Do not report progress more than 100 times per operation - excessive
  memory consumption and leaks. Messages more often than every 3 seconds
  replace the previous one.
- ❌ Use `СообщитьПрогресс` to pass the result in parts - that is not its
  purpose; the result is returned through `АдресРезультата`.
- Progress is visible to the user only if `ВыводитьПрогрессВыполнения = Истина`
  in `ПараметрыОжидания`.

### 4. Cancel a background job and check its status

**Task:** cancel a long-running operation by user command and find out the job
state without attaching a waiting form.

**Functions:**
`ДлительныеОперации.ОтменитьВыполнениеЗадания(Знач ИдентификаторЗадания) Экспорт`
— Procedure (stable). Server.
`ДлительныеОперации.ЗаданиеВыполнено(Знач ИдентификаторЗадания, РасширенныйРезультат = Ложь) Экспорт`
— Function (stable). Server. Returns `Булево` or `Структуру` (when
`РасширенныйРезультат = Истина`) with property `Статус`:
`"Выполняется"`/`"Выполнено"`/`"Ошибка"`/`"Отменено"`.

**Parameters:**
- `ИдентификаторЗадания` (УникальныйИдентификатор) - from
  `ДлительнаяОперация.ИдентификаторЗадания`.
- `РасширенныйРезультат` (Булево) - `Истина` -> structure with details; `Ложь`
  (default) -> just `Булево` (finished or not).

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
    // ЗаданиеВыполнено уже вызвало исключение с текстом ошибки
КонецЕсли;
```

**Nuances / anti-patterns:**
- ⚠️ On abnormal termination `ЗаданиеВыполнено` **throws an exception** with
  the error text from the background job. Wrap in `Попытка…Исключение` if you
  need graceful handling.
- `ОтменитьВыполнениеЗадания` only initiates cancellation; the job will not
  stop immediately - check the status through `ЗаданиеВыполнено`.
- ❌ `ДлительныеОперацииВызовСервера.ФоновоеЗаданиеЗавершено` - service
  (region `СлужебныеПроцедурыИФункции`); use the stable
  `ДлительныеОперации.ЗаданиеВыполнено`.

### 5. Create a scheduled job with a schedule

**Task:** programmatically create a scheduled job (during initial database
population, configuration update) with a schedule and an application key.

**Functions:**
`РегламентныеЗаданияСервер.ДобавитьЗадание(Параметры) Экспорт` — Function
(stable). Server, external connection. Returns the created job.
`ОбщегоНазначенияКлиентСервер.РасписаниеВСтруктуру(Знач Расписание) Экспорт` —
Function (stable), `РасписаниеРегламентногоЗадания` -> `Структура`.
`ОбщегоНазначенияКлиентСервер.СтруктураВРасписание(Знач СтруктураРасписания) Экспорт`
— Function (stable), `Структура` -> `РасписаниеРегламентногоЗадания`.

**Parameters:**
- `Параметры` (Структура) - for `ДобавитьЗадание`: `Метаданные`
  (ОбъектМетаданныхРегламентноеЗадание, required), `Расписание`
  (РасписаниеРегламентногоЗадания), `Использование` (Булево), `Ключ` (Строка,
  application identifier), `Параметры` (Массив - handler method parameters),
  `ИнтервалПовтораПриАварийномЗавершении` (Число, sec),
  `КоличествоПовторовПриАварийномЗавершении` (Число).
- `Расписание` (РасписаниеРегламентногоЗадания) - source schedule.
- `СтруктураРасписания` (Структура) - schedule fields: `ПериодПовтораДней`,
  `ПериодПовтораВТечениеДня`, `ДниНедели`, `ВремяНачала`, `ВремяКонца`,
  `ДатаНачала`, `ДатаКонца`, etc.

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

**Nuances / anti-patterns:**
- ❌ Calling `РегламентныеЗаданияСервер.ДобавитьЗадание` from client code -
  the module is server-side, it will fail on a thin client. Only through
  `&НаСервере` / `&НаСервереБезКонтекста`.
- ❌ `РегламентныеЗадания.ДобавитьЗадание(...)` - the module without a suffix
  does not exist. Only `РегламентныеЗаданияСервер`.
- In the service model, `ДобавитьЗадание` creates a record in the
  `ОчередьЗаданий` catalog, not a platform scheduled job.

### 6. Find, change, and delete a scheduled job

**Task:** find a job by application key, change its schedule or enable/disable
it, delete duplicates.

**Functions:**
`РегламентныеЗаданияСервер.НайтиЗадания(Отбор) Экспорт` — Function (stable).
Returns an array of jobs (local mode) or a value table (service model).
`РегламентныеЗаданияСервер.ИзменитьЗадание(Знач Идентификатор, Знач Параметры) Экспорт`
— Procedure (stable).
`РегламентныеЗаданияСервер.УдалитьЗадание(Знач Идентификатор) Экспорт` —
Procedure (stable).
`РегламентныеЗаданияСервер.УстановитьРасписаниеРегламентногоЗадания(Знач Идентификатор, Знач Расписание) Экспорт`
— Procedure (stable).
`РегламентныеЗаданияСервер.УстановитьИспользованиеРегламентногоЗадания(Знач Идентификатор, Знач Использование) Экспорт`
— Procedure (stable).

**Parameters:**
- `Отбор` (Структура) - properties: `Метаданные`
  (ОбъектМетаданныхРегламентноеЗадание), `Ключ` (Строка), `Использование`
  (Булево), `УникальныйИдентификатор` (УникальныйИдентификатор / Строка /
  `СправочникСсылка.ОчередьЗаданий`).
- `Идентификатор` (УникальныйИдентификатор / Строка / ОбъектМетаданных) - for
  `ИзменитьЗадание`/`УдалитьЗадание`/`Установить*`.
- `Параметры` (Структура) - mutable properties for `ИзменитьЗадание` (the same
  ones as in `ДобавитьЗадание`).
- `Расписание` (РасписаниеРегламентногоЗадания) - new schedule.
- `Использование` (Булево) - `Истина` enables execution by schedule.

**Example:**
```bsl
// Найти по ключу и сменить расписание, удалить дубли
Отбор = Новый Структура("Ключ", "МояЗадача_Основная");
Задания = РегламентныеЗаданияСервер.НайтиЗадания(Отбор);
Если Задания.Количество() > 0 Тогда
    Идентификатор = Задания[0].УникальныйИдентификатор;
    РегламентныеЗаданияСервер.УстановитьРасписаниеРегламентногоЗадания(Идентификатор, НовоеРасписание);
    // Удалить случайные дубли — оставить только первое
    Для Сч = 1 По Задания.ВГраница() Цикл
        РегламентныеЗаданияСервер.УдалитьЗадание(Задания[Сч].УникальныйИдентификатор);
    КонецЦикла;
КонецЕсли;

// Выключить задание при ошибке
РегламентныеЗаданияСервер.УстановитьИспользованиеРегламентногоЗадания(Идентификатор, Ложь);
```

**Nuances / anti-patterns:**
- ❌ Creating jobs with one `Метаданные` and different keys for each
  organization - they conflict on startup. Better one job, iteration inside the
  handler; or different `Метаданные`.
- `НайтиЗадания` without a selection returns all jobs - expensive; always pass
  a selection.
- In a scheduled job handler, check that it still exists
  (`НайтиЗадания(Отбор).Количество() > 0`) before doing work - the job may
  have been deleted.

### 7. Multithreaded execution and correct error handling

**Task:** parallelize processing of a large data set into several background
threads and safely handle an error in a scheduled job handler.

**Functions:**
`ДлительныеОперации.ВыполнитьФункциюВНесколькоПотоков(ИмяФункции, ПараметрыВыполнения, НаборПараметровФункции = Неопределено) Экспорт`
— Function (stable). Server.
`ДлительныеОперации.ВыполнитьПроцедуруВНесколькоПотоков(ИмяПроцедуры, ПараметрыВыполнения, НаборПараметровПроцедуры = Неопределено) Экспорт`
— Function (stable). Server.
`ДлительныеОперации.ДопустимоеКоличествоПотоков() Экспорт` — Function
(⚠️ region `СлужебныйПрограммныйИнтерфейс`), returns the maximum allowed
number of threads.

**Parameters:**
- `ИмяФункции` / `ИмяПроцедуры` (Строка) - export function/procedure of a common
  module or manager module.
- `ПараметрыВыполнения` (Структура) - from `ПараметрыВыполненияФункции` /
  `ПараметрыВыполненияПроцедуры`.
- `НаборПараметровФункции` (Array of Структура / `Неопределено`) - array of
  parameter structures; one element per thread. `Неопределено` - one parameter
  for all threads.

**Example:**
```bsl
// Многопоточная обработка порций
ПараметрыВыполнения = ДлительныеОперации.ПараметрыВыполненияФункции(УникальныйИдентификатор);
НаборПараметров = Новый Массив;
Для Каждого Порция Из ПорцииДанных Цикл
    НаборПараметров.Добавить(Новый Структура("Данные", Порция));
КонецЦикла;
ДлительнаяОперация = ДлительныеОперации.ВыполнитьФункциюВНесколькоПотоков(
    "Обработка.МояОбработка.ОбработатьПорцию", ПараметрыВыполнения, НаборПараметров);

// Обработчик регламентного задания с защитой от ошибок
Процедура МояЗадача() Экспорт
    Ключ = "МояЗадача_Основная";
    Если РегламентныеЗаданияСервер.НайтиЗадания(Новый Структура("Ключ", Ключ)).Количество() = 0 Тогда
        Возврат; // задание удалили
    КонецЕсли;
    Попытка
        // ...основная работа...
    Исключение
        Ид = РегламентныеЗаданияСервер.НайтиЗадания(Новый Структура("Ключ", Ключ))[0].УникальныйИдентификатор;
        РегламентныеЗаданияСервер.УстановитьИспользованиеРегламентногоЗадания(Ид, Ложь);
        ОбщегоНазначения.ЗаписатьВЖурналРегистрации(УровеньЖурналаРегистрации.Ошибка,, , ,
            "МояЗадача", ИнформацияОбОшибке());
        ВызватьИсключение;
    КонецПопытки;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ⚠️ `ДопустимоеКоличествоПотоков` - service region; for application code,
  rely on `ДлительныеОперации.ПараметрыВыполненияФункции` and do not exceed
  the platform limit on background jobs.
- ❌ Transactional write inside a thread without `Попытка…Исключение` - an
  error in one thread interrupts the entire operation. Each thread is an
  independent processing of its own batch.
- `ВыполнитьВФоне(ИмяПроцедуры, ПараметрыПроцедуры, ПараметрыВыполнения)` -
  stable (region `ПрограммныйИнтерфейс`), but the doc comment recommends
  `ВыполнитьФункцию`/`ВыполнитьПроцедуру` (arbitrary number of parameters up to
  7, without the `Параметры`/`АдресРезультата` wrapper). In new code -
  `ВыполнитьФункцию`.
- ⚠️ `ЗапуститьВыполнениеВФоне` - region `УстаревшиеПроцедурыИФункции`
  (deprecated), do not use in new code; alternative - `ВыполнитьФункцию`.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures - via
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `РегламентныеЗаданияСервер.РасписаниеРегламентногоЗадания(Знач Идентификатор, Знач ВСтруктуре = Ложь)` - get the schedule (as an object or structure).
- `РегламентныеЗаданияСервер.РегламентноеЗаданиеИспользуется(Знач Идентификатор)` - `Булево`, whether the job is enabled.
- `РегламентныеЗаданияСервер.ПолучитьРегламентноеЗадание(Знач Идентификатор)` - job object.
- `РегламентныеЗаданияСервер.СвойстваПоследнегоЗадания(Знач Задание)` - properties of the last background execution.
- `РегламентныеЗаданияСервер.РаботаСВнешнимиРесурсамиЗаблокирована()` /
  `ЗаблокироватьРаботуСВнешнимиРесурсами()` /
  `РазблокироватьРаботуСВнешнимиРесурсами()` - control the lock on external
  resources during updates.
- `ДлительныеОперацииКлиент.НовыйРезультатДлительнойОперации()` /
  `НовоеСостояниеДлительнойОперации()` - constructors of service structures.
- Override hook:
  `РегламентныеЗаданияПереопределяемый.ПриОпределенииНастроекРегламентныхЗаданий(Настройки)`
  - BSP calls it, application code implements it (module `*Переопределяемый`);
  scheduled job blocking setting in the service model. Do not call directly.
- ⚠️ Service methods (region `СлужебныйПрограммныйИнтерфейс` /
  `СлужебныеПроцедурыИФункции`), do not use in application code:
  `РегламентныеЗаданияСлужебный.ВыполнитьРегламентноеЗаданиеВручную(Знач Задание)`,
  `РегламентныеЗаданияСлужебный.ПолучитьСвойстваФоновогоЗадания(Идентификатор, ИменаСвойств = "")`,
  `ДлительныеОперации.ОперацияВыполнена(Знач ИдентификаторЗадания, Задание = Неопределено)`.
