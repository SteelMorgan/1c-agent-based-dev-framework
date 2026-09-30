# Performance and business statistics monitoring of БСП

Three related subsystems: **ОценкаПроизводительности** (APDEX measurements of key
operations in the `ЗамерыВремени` register), **ЦентрМониторинга** (anonymized
business statistics and technological information sent by a scheduled task to
the 1С service or a third-party service), and **КонтрольРаботыПользователей**
(managing the registration of data access events in the registration log).
Teaches how to wrap business operations with measurements and record quantitative
metrics without creating your own wrappers.

## Modules

The `ОценкаПроизводительности` family (suffix logic):

- `ОценкаПроизводительности` — server-side measurements: `НачатьЗамерВремени`,
  `ЗакончитьЗамерВремени`, `ЗакончитьЗамерВремениТехнологический`,
  `НачатьЗамерДлительнойОперации`, `ЗафиксироватьЗамерДлительнойОперации`,
  `ЗакончитьЗамерДлительнойОперации`, `СоздатьКлючевыеОперации`,
  `УстановитьЦелевоеВремя`, `ИзменитьКлючевыеОперации`.
- `ОценкаПроизводительностиКлиент` — client-side measurements: `ЗамерВремени`
  (single-line, auto-completing), `ЗавершитьЗамерВремени`,
  `НачатьЗамерВремениТехнологический`, `УстановитьПараметрыЗамера` and others.
  ⚠️ `ОценкаПроизводительностиКлиент.НачатьЗамерВремени` — **deprecated**
  (`УстаревшиеПроцедурыИФункции`); use `ЗамерВремени`.
- `ОценкаПроизводительностиВызовСервера` — batch recording of measurements.
  ⚠️ `ЗафиксироватьДлительностьКлючевыхОпераций(ЗамерыДляЗаписи)` — region
  `СлужебныеПроцедурыИФункции` (internal, backward compatibility is not
  guaranteed); do not use as the primary API.
- `ОценкаПроизводительностиВызовСервераПовтИсп` — cached check of
  `ВыполнятьЗамерыПроизводительности` (measurement gating).
- ⚠️ `ОценкаПроизводительностиСлужебный` — internal API; in `src/cf` the root
  module also has methods in `СлужебныеПроцедурыИФункции`
  (`СоздатьКлючевуюОперацию`, `ЭкспортОценкиПроизводительности`, …) — do not
  call from application code.

The `ЦентрМониторинга` family:

- `ЦентрМониторинга` — stable server-side API: `ЦентрМониторингаВключен`,
  `ВключитьПодсистему`, `ОтключитьПодсистему`, `ИдентификаторИнформационнойБазы`,
  `ЗаписатьОперациюБизнесСтатистики` (+ `…Час`/`…Сутки`),
  `ЗаписыватьОперацииБизнесСтатистики`, `ЗаписатьСтатистикуКонфигурации`,
  `ЗаписатьСтатистикуОбъектаКонфигурации`.
- `ЦентрМониторингаКлиент` — client-side statistics recording. ⚠️ Signatures
  **differ** from the server-side ones: `ЗаписатьОперациюБизнесСтатистики(ИмяОперации,
  Значение)` (without `Комментарий`/`Разделитель`), and `…Час`/`…Сутки` have a different
  parameter order (`Значение` before `КлючУникальности`, `КлючУникальности`
  optional). Do not copy the server-side signature into client code.
- ⚠️ `ЦентрМониторингаСлужебный` — internal API; including
  `ЗаписатьОперациюБизнесСтатистикиСлужебная(ПараметрыЗаписи)`,
  `ПолучитьПараметрыЦентраМониторинга` — backward compatibility is not
  guaranteed.

`КонтрольРаботыПользователей` — one server-side module, 4 stable methods for
managing the registration of data access events (a separate subsystem from
`ОценкаПроизводительности`, but related to access monitoring).

## Scenarios

### 1. Measure a server-side key operation

**Task:** wrap a server method (posting a document, generating
report) with an APDEX measurement and write the result to the `ЗамерыВремени` register.

**Functions:**
`ОценкаПроизводительности.НачатьЗамерВремени() Экспорт`
— Function → `Число` (UTC, ms, 14 characters; `0` if measurements are disabled), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ОценкаПроизводительности.ЗакончитьЗамерВремени(КлючеваяОперация, ВремяНачала, ВесЗамера = 1, Комментарий = Неопределено, ВыполненСОшибкой = Ложь) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

**Parameters:**
- `КлючеваяОперация` (`СправочникСсылка.КлючевыеОперации` / `Строка`) —
  key operation; for a string, БСП will itself find/create the catalog item
  `КлючевыеОперации` when writing.
- `ВремяНачала` (`Число`) — value returned by `НачатьЗамерВремени`.
  If `0` is passed in (measurements were disabled at start time) — the write
  is skipped silently, no checks are required on the caller side.
- `ВесЗамера` (`Число`) — quantitative indicator of the measurement (e.g. number
  of rows in the document); default is `1`.
- `Комментарий` (`Строка` / `Соответствие`) — arbitrary measurement information;
  default is `Неопределено`.
- `ВыполненСОшибкой` (`Булево`) — sign that the measurement was not completed;
  default is `Ложь`.

**Example:**
```bsl
// In the ОбработкаПроведения handler of the document module
ВремяНачала = ОценкаПроизводительности.НачатьЗамерВремени();

Попытка
    // ...штатная логика проведения...
    Отказ = Ложь;
Исключение
    Отказ = Истина;
    ОценкаПроизводительности.ЗакончитьЗамерВремени(
        "Документы.ЗаказПокупателя.Проведение",
        ВремяНачала, 1, , Истина);   // ВыполненСОшибкой = Истина
    ВызватьИсключение;
КонецПопытки;

ОценкаПроизводительности.ЗакончитьЗамерВремени(
    "Документы.ЗаказПокупателя.Проведение", ВремяНачала);
```

**Nuances / anti-patterns:**
- ❌ Measuring via `ТекущаяДата()` / `ТекущаяДатаСеанса()` — low precision
  (seconds), no link to the key operation and the register. Only
  `ОценкаПроизводительности.НачатьЗамерВремени` (precision to ms, APDEX).
- ❌ Calling `ОценкаПроизводительности.НачатьЗамерВремени` on the thin client
  — the module is server-side. For the client — `ОценкаПроизводительностиКлиент.ЗамерВремени`.
- ❌ `ОценкаПроизводительности.ЗакончитьЗамерВремени("Операция", 0)` without
  a preceding `НачатьЗамерВремени` — the duration will be calculated from the “epoch”;
  always pass the value from `НачатьЗамерВремени`.
- `НачатьЗамерВремени` itself gates the write through `ВыполнятьЗамерыПроизводительности`
  (reused cache) — wrapping it in `Если … Тогда` is not needed.

### 2. Measure a long-running operation with nested steps

**Task:** measure a multi-step operation (load → parse → write to the information base) with
breakdown for each step.

**Functions:**
`ОценкаПроизводительности.НачатьЗамерДлительнойОперации(КлючеваяОперация) Экспорт`
— Function → `Соответствие` (measurement context; keys `КлючеваяОперация`, `ВремяНачала`, `ВремяПоследнегоЗамера`, `ВесЗамера`, `ВложенныеЗамеры`), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ОценкаПроизводительности.ЗафиксироватьЗамерДлительнойОперации(ОписаниеЗамера, КоличествоДанных, ИмяШага, Комментарий = "") Экспорт`
— Procedure (intermediate step), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ОценкаПроизводительности.ЗакончитьЗамерДлительнойОперации(ОписаниеЗамера, КоличествоДанных, ИмяШага = "", Комментарий = "") Экспорт`
— Procedure (completion), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

**Parameters:**
- `КлючеваяОперация` (`Строка`) — name of the key operation.
- `ОписаниеЗамера` (`Соответствие`) — value returned by
  `НачатьЗамерДлительнойОперации`; **must** be from the same pair of calls.
- `КоличествоДанных` (`Число`) — amount processed at the step (for example, number of
  rows).
- `ИмяШага` (`Строка`) — arbitrary name of the nested step.
- `Комментарий` (`Строка`) — arbitrary description; default is `""`.

**Example:**
```bsl
Замер = ОценкаПроизводительности.НачатьЗамерДлительнойОперации("МассоваяЗагрузкаНоменклатуры");

ДанныеФайла = ПрочитатьФайл(ИмяФайла);
ОценкаПроизводительности.ЗафиксироватьЗамерДлительнойОперации(
    Замер, ДанныеФайла.КоличествоСтрок(), "ЧтениеФайла");

Строки = РазобратьДанные(ДанныеФайла);
ОценкаПроизводительности.ЗафиксироватьЗамерДлительнойОперации(
    Замер, Строки.Количество(), "РазборДанных");

Для Каждого Строка Из Строки Цикл ЗаписатьЭлемент(Строка); КонецЦикла;
ОценкаПроизводительности.ЗакончитьЗамерДлительнойОперации(
    Замер, Строки.Количество(), "ЗаписьВБД", "");
```

**Nuances / anti-patterns:**
- ❌ Passing to `ОписаниеЗамера` a `Соответствие` from another operation or
  `Неопределено` will make the measurement incorrect; the context must come from the same
  `НачатьЗамерДлительнойОперации`.
- ❌ `ОценкаПроизводительности.ЗафиксироватьДлительностьКлючевойОперации(100)`
  — the method **exists** in `src/cf` (Module.bsl:676), but it is in the `СлужебныеПроцедурыИФункции`
  region and is **not exported** (without `Экспорт`); BSP calls it internally with a
  `Параметры` structure. It cannot be called from application code - use
  `ЗакончитьЗамерВремени`. Batch write -
  `ОценкаПроизводительностиВызовСервера.ЗафиксироватьДлительностьКлючевыхОпераций`
  (⚠️ service, see "Редкие методы").
- For long-running background operations (background job), there is a separate subsystem
  `ДлительныеОперации`; here this is only a measurement **inside** such an operation.

### 3. Measure a client operation

**Task:** measure the time to open a form / client-side processing with writing to
the same `ЗамерыВремени` register through the client buffer.

**Functions:**
`ОценкаПроизводительностиКлиент.ЗамерВремени(КлючеваяОперация = Неопределено, ФиксироватьСОшибкой = Ложь, АвтоЗавершение = Истина) Экспорт`
— Function → `УникальныйИдентификатор` (measurement identifier), region `#Область ПрограммныйИнтерфейс` (stable). Thin client, Thick client.
`ОценкаПроизводительностиКлиент.ЗавершитьЗамерВремени(УИДЗамера, ВыполненСОшибкой = Ложь) Экспорт`
— Procedure (explicit completion when `АвтоЗавершение = Ложь`), region `#Область ПрограммныйИнтерфейс` (stable). Thin client, Thick client.
`ОценкаПроизводительностиКлиент.УстановитьКлючевуюОперациюЗамера(УИДЗамера, КлючеваяОперация) Экспорт`
`ОценкаПроизводительностиКлиент.УстановитьВесЗамера(УИДЗамера, ВесЗамера) Экспорт`
`ОценкаПроизводительностиКлиент.УстановитьКомментарийЗамера(УИДЗамера, Комментарий) Экспорт`
`ОценкаПроизводительностиКлиент.УстановитьПризнакОшибкиЗамера(УИДЗамера, Признак) Экспорт`
— all Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Thin client, Thick client.

**Parameters:**
- `КлючеваяОперация` (`Строка` / `Неопределено`) — the name of the key operation; if
  `Неопределено` — set later via `УстановитьКлючевуюОперациюЗамера`.
- `ФиксироватьСОшибкой` (`Булево`) — `Истина` → when auto-completed, the measurement
  will be recorded with the "completed with error" flag; `Ложь` → normal.
- `АвтоЗавершение` (`Булево`) — `Истина` (default) → completion through the
  global wait handler; `Ложь` → explicit call to `ЗавершитьЗамерВремени`.
- `УИДЗамера` (`УникальныйИдентификатор`) — value returned by `ЗамерВремени`.

**Example:**
```bsl
// Single-line measurement with auto-completion
УИД = ОценкаПроизводительностиКлиент.ЗамерВремени("ОткрытиеФормыДокумента");

// Explicit cycle: precise boundaries + weight + error flag
УИД = ОценкаПроизводительностиКлиент.ЗамерВремени(, Ложь, Ложь);
Попытка
    // ...long client-side processing...
    ОценкаПроизводительностиКлиент.УстановитьВесЗамера(УИД, КоличествоСтрок);
Исключение
    ОценкаПроизводительностиКлиент.УстановитьПризнакОшибкиЗамера(УИД, Истина);
КонецПопытки;
ОценкаПроизводительностиКлиент.ЗавершитьЗамерВремени(УИД);
```

**Nuances / anti-patterns:**
- ⚠️ Client measurements are stored in the client buffer and written with
the periodicity of the `ОценкаПроизводительностиПериодЗаписи` constant (by
default, every minute); if the session terminates abnormally, some measurements
may be lost.
- ❌ `ОценкаПроизводительностиКлиент.НачатьЗамерВремени(...)` — **deprecated**
  (`УстаревшиеПроцедурыИФункции`). Use `ЗамерВремени`.
- If `КлючеваяОперация = Неопределено` — обязательно set it via
  `УстановитьКлючевуюОперациюЗамера`, otherwise the measurement will not be linked to the operation.

### 4. Register business statistics

**Task:** write a quantitative metric (“how many documents
have been posted”, “average attachment size”) into the `БуферОперацийСтатистики` buffer for
scheduled sending to the service.

**Functions:**
`ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистики(ИмяОперации, Значение, Комментарий = Неопределено, Разделитель = ".") Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистикиЧас(ИмяОперации, КлючУникальности, Значение, Замещать = Ложь) Экспорт`
`ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистикиСутки(ИмяОперации, КлючУникальности, Значение, Замещать = Ложь) Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЦентрМониторинга.ЗаписыватьОперацииБизнесСтатистики() Экспорт`
— Function → `Булево` (registration state getter), region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ИмяОперации` (`Строка`) — statistics operation name; if absent,
  a new one is created. The hierarchy is separated by `Разделитель` (default
  `"."`).
- `Значение` (`Число`) — quantitative value.
- `Комментарий` (`Строка`) — arbitrary comment; default is
  `Неопределено`.
- `Разделитель` (`Строка`) — separator for values in `ИмяОперации`, if not a
  dot; default is `"."`.
- `КлючУникальности` (arbitrary) — for `…Час`/`…Сутки`: the key by which
  only one record is stored per period (hour/day).
- `Замещать` (`Булево`) — `Истина` → replaces the previous value for the period;
  `Ложь` (default) → accumulates.

**Example:**
```bsl
// Simple counter
ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистики(
    "Документы.ЗаказПокупателя.Проведение.Количество", 1, , ".");

// Counter with hourly uniqueness (one record per document per hour)
Для Каждого Строка Из ТаблицаДокументов Цикл
    ПровестиДокумент(Строка.Ссылка);
    ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистикиЧас(
        "Документы.ЗаказПокупателя.Проведение.Факт",
        Строка.Ссылка.УникальныйИдентификатор(), 1, Ложь);
КонецЦикла;
```

**Nuances / antipatterns:**
- ❌ Wrapping the write in `Если ЦентрМониторинга.ЦентрМониторингаВключен()`
  is redundant: `ЗаписатьОперациюБизнесСтатистики` itself gates the write through
  `ЗаписыватьОперацииБизнесСтатистики()`. The check is only appropriate if, when
  the center is disabled, separate logic is needed (for example, writing to a local log).
- ❌ `ЦентрМониторинга.ЗаписатьОперацию("Сервис.Тест", 1)` — there is **no** such method;
  the stable name is `ЗаписатьОперациюБизнесСтатистики`.
- ❌ Direct write to the register `РегистрыСведений.БуферОперацийСтатистики.СоздатьНаборЗаписей()`
  bypasses service checks and categorization. Only through
  `ЦентрМониторинга.*`.
- ⚠️ On the client, the signatures are different: `ЦентрМониторингаКлиент.ЗаписатьОперациюБизнесСтатистики(ИмяОперации, Значение)`
  (without `Комментарий`/`Разделитель`), and `…Час`/`…Сутки` are
  `(ИмяОперации, Значение, Замещать = Ложь, КлючУникальности = Неопределено)`
  (different parameter order). Do not substitute the server signature into
  client code.

### 5. Manage the "Monitoring Center" subsystem

**Task:** programmatically check/enable/disable the monitoring center and
obtain the information base identifier to associate statistics.

**Functions:**
`ЦентрМониторинга.ЦентрМониторингаВключен() Экспорт`
— Function → `Булево`, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЦентрМониторинга.ВключитьПодсистему() Экспорт`
`ЦентрМониторинга.ОтключитьПодсистему() Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЦентрМониторинга.ИдентификаторИнформационнойБазы() Экспорт`
— Function → IB identifier, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

**Parameters:** no parameters.

**Example:**
```bsl
Если Не ЦентрМониторинга.ЦентрМониторингаВключен() Тогда
    ЦентрМониторинга.ВключитьПодсистему();   // включит и регламент. задание сбора/отправки
КонецЕсли;

ИдентификаторИБ = ЦентрМониторинга.ИдентификаторИнформационнойБазы();
// Используется для привязки пакетов статистики к конкретной ИБ
```

**Nuances / anti-patterns:**
- ❌ `ЦентрМониторингаКлиент.ПоказатьНастройкиЦентраМониторинга` — this is
a UI method (region `СлужебныйПрограммныйИнтерфейс`), not a server API; for
programmatic control use the server-side `ВключитьПодсистему` /
`ОтключитьПодсистему`.
- `ВключитьПодсистему` triggers the scheduled job `СборИОтправкаСтатистики`
  (through a service module) — no separate job registration is needed.

### 6. Create and configure a key operation programmatically

**Task:** programmatically register a key operation in the catalog
`КлючевыеОперации` with a target time and the "long-running" flag.

**Functions:**
`ОценкаПроизводительности.СоздатьКлючевыеОперации(КлючевыеОперации) Экспорт`
`ОценкаПроизводительности.УстановитьЦелевоеВремя(КлючевыеОперации) Экспорт`
`ОценкаПроизводительности.ИзменитьКлючевыеОперации(КлючевыеОперации) Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ОценкаПроизводительности.СоздатьКлючевуюОперацию(ИмяКлючевойОперации, ЦелевоеВремя = 1, Длительная = Ложь) Экспорт`
— Function → `СправочникСсылка.КлючевыеОперации`, ⚠️ region `#Область СлужебныеПроцедурыИФункции` (service).

**Parameters:**
- `КлючевыеОперации` (`Массив` from `Структура`) — for batch
  `СоздатьКлючевыеОперации`/`УстановитьЦелевоеВремя`/`ИзменитьКлючевыеОперации`:
  structure elements with fields `ИмяКлючевойОперации`, `ЦелевоеВремя`,
  `Длительная`, and others.
- `ИмяКлючевойОперации` (`Строка`) — operation name.
- `ЦелевоеВремя` (`Число`) — target time in seconds; default is `1`.
- `Длительная` (`Булево`) — long-running operation flag; default is `Ложь`.

**Example:**
```bsl
// Пакетная регистрация (стабильный API)
КлючевыеОперации = Новый Массив;
Операция = Новый Структура;
Операция.Вставить("ИмяКлючевойОперации", "Документы.ЗаказПокупателя.Проведение");
Операция.Вставить("ЦелевоеВремя", 2);
Операция.Вставить("Длительная", Ложь);
КлючевыеОперации.Добавить(Операция);
ОценкаПроизводительности.СоздатьКлючевыеОперации(КлючевыеОперации);

// Позже — поменять целевое время
ОценкаПроизводительности.УстановитьЦелевоеВремя(КлючевыеОперации);
```

**Nuances / anti-patterns:**
- ⚠️ `СоздатьКлючевуюОперацию` (single-item) — **service** region
  (`СлужебныеПроцедурыИФункции`); for new code, prefer the stable batch
  `СоздатьКлючевыеОперации`.
- ❌ `ОценкаПроизводительности.ЗафиксироватьДлительностьКлючевойОперации(...)`
  — method **exists** (Module.bsl:676), but is internal and **not exported**
  (region `СлужебныеПроцедурыИФункции`, without `Экспорт`); do not call it from application code —
  use `ЗакончитьЗамерВремени`. See scenario 2 and "Редкие методы".
- Passing a string to `ЗакончитьЗамерВремени` instead of a reference is acceptable:
  БСП will find/create the catalog item `КлючевыеОперации` when writing
  the measurement; separate registration of the operation is not required for one-off measurements.

### 7. Enable data access event logging

**Task:** programmatically control the logging of "Доступ.Доступ" events to
 data in the event log (requirement 152-FZ for personal data) — a global
 switch and detailed settings.

**Functions:**
`КонтрольРаботыПользователей.РегистрироватьДоступКДанным() Экспорт`
— Function → `Булево` (getter for the "НастройкиПользователейИПрав" panel setting), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`КонтрольРаботыПользователей.УстановитьРегистрациюДоступаКДанным(РегистрироватьДоступКДанным) Экспорт`
— Procedure (setter), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`КонтрольРаботыПользователей.НастройкиРегистрацииСобытийДоступаКДанным() Экспорт`
— Function → `Структура` (`Состав` — array of descriptions, `Комментарии` — `Соответствие`, `ОбщийКомментарий` — `Строка`), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`КонтрольРаботыПользователей.УстановитьНастройкиРегистрацииСобытийДоступаКДанным(Настройки) Экспорт`
— Procedure (settings setter), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

**Parameters:**
- `РегистрироватьДоступКДанным` (`Булево`) — global flag for logging
  access events.
- `Настройки` (`Структура`, see `НастройкиРегистрацииСобытийДоступаКДанным`) —
  composition of logged events, field comments, and a general comment.

**Example:**
```bsl
// Enable logging if it is disabled
Если Не КонтрольРаботыПользователей.РегистрироватьДоступКДанным() Тогда
    КонтрольРаботыПользователей.УстановитьРегистрациюДоступаКДанным(Истина);
КонецЕсли;

// Read current settings and add a field comment
Настройки = КонтрольРаботыПользователей.НастройкиРегистрацииСобытийДоступаКДанным();
Настройки.Комментарии.Вставить("Справочник.ФизическиеЛица.НомерДокумента",
    НСтр("ru = 'Серия и номер паспорта'"));
КонтрольРаботыПользователей.УстановитьНастройкиРегистрацииСобытийДоступаКДанным(Настройки);
```

**Nuances / antipatterns:**
- This is the **Контроль работы пользователей** subsystem (general access log),
  separate from `ЗащитаПерсональныхДанных` (which via
  `УстановитьИспользованиеСобытияДоступ` enables "Доступ.Доступ" for
  specific personal data categories). Sequence: first the global switch
  `КонтрольРаботыПользователей.УстановитьРегистрациюДоступаКДанным(Истина)`,
  then detailing by category through `ЗащитаПерсональныхДанных`.
- ❌ Directly writing to the event log with `ЗаписьЖурналаРегистрации(...)` instead of
  subsystem settings bypasses structured accounting of access events and
  personal data categories.
- Settings are a structure modified through the setter; do not try to write
  it directly into a constant/register.

## Rare methods

- `ОценкаПроизводительности.ЗакончитьЗамерВремениТехнологический(КлючеваяОперация, ВремяНачала, ВесЗамера = 1, Комментарий = Неопределено) Экспорт`
  — stable (`ПрограммныйИнтерфейс`); technological measurement (without the error flag). Used less often than `ЗакончитьЗамерВремени`.
- `ОценкаПроизводительностиКлиент.НачатьЗамерВремениТехнологический(АвтоЗавершение = Истина, КлючеваяОперация = Неопределено) Экспорт`
  — stable; client technological measurement.
- `ОценкаПроизводительности.УстановитьПризнакЗавершенияСОшибкой(КлючевыеОперации) Экспорт`
  — ⚠️ **deprecated** (`УстаревшиеПроцедурыИФункции`): “deprecated, do not
  use in new code; alternative —
  `ЗакончитьЗамерВремени(…, ВыполненСОшибкой = Истина)`”.
- `ОценкаПроизводительностиВызовСервера.ЗафиксироватьДлительностьКлючевыхОпераций(ЗамерыДляЗаписи) Экспорт`
  — ⚠️ **service** (`СлужебныеПроцедурыИФункции`): batch write of an array
  of measurements (`Структура` with `ЗамерыЗавершенные` — `Соответствие` from
  `УникальныйИдентификатор` → `Соответствие`, and `ИнформацияПрограммыПросмотра`
  — `Строка`). Returns the measurement write period on the server (seconds).
  Backward compatibility is not guaranteed — do not use as the primary
  method; for a single write, use `ЗакончитьЗамерВремени`.
- `ЦентрМониторинга.ЗаписатьСтатистикуКонфигурации(СоответствиеИменМетаданных) Экспорт`
  and `ЦентрМониторинга.ЗаписатьСтатистикуОбъектаКонфигурации(ИмяОбъекта, Значение) Экспорт`
  — stable (`ПрограммныйИнтерфейс`); writing technological statistics
  for configuration metadata (volume, number of objects).
- `ЦентрМониторингаСлужебный.ЗаписатьОперациюБизнесСтатистикиСлужебная(ПараметрыЗаписи) Экспорт`
  — ⚠️ service (`СлужебныеПроцедурыИФункции`); low-level write into the
  buffer; use the stable `ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистики`.

To find the signature/region of any of these methods —
`python .claude/skills/bsp/scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`.