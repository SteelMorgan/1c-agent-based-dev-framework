# Monitoring Performance and BSP Business Statistics

Three related subsystems: **ОценкаПроизводительности** (APDEX measurements of key operations in the `ЗамерыВремени` register), **ЦентрМониторинга** (anonymized business statistics and technical information sent by a scheduled job to a 1C service or a third-party service), and **КонтрольРаботыПользователей** (managing the registration of data access events in the registration log).
It teaches you to wrap business operations with measurements and record quantitative metrics without creating your own wrappers.

## Modules

`ОценкаПроизводительности` family (suffix-based logic):

- `ОценкаПроизводительности` — server-side measurements: `НачатьЗамерВремени`,
  `ЗакончитьЗамерВремени`, `ЗакончитьЗамерВремениТехнологический`,
  `НачатьЗамерДлительнойОперации`, `ЗафиксироватьЗамерДлительнойОперации`,
  `ЗакончитьЗамерДлительнойОперации`, `СоздатьКлючевыеОперации`,
  `УстановитьЦелевоеВремя`, `ИзменитьКлючевыеОперации`.
- `ОценкаПроизводительностиКлиент` — client-side measurements: `ЗамерВремени`
  (single-line, auto-completing), `ЗавершитьЗамерВремени`,
  `НачатьЗамерВремениТехнологический`, `УстановитьПараметрыЗамера` and others.
  ⚠️ `ОценкаПроизводительностиКлиент.НачатьЗамерВремени` is **deprecated**
  (`УстаревшиеПроцедурыИФункции`); use `ЗамерВремени`.
- `ОценкаПроизводительностиВызовСервера` — batch recording of measurements.
  ⚠️ `ЗафиксироватьДлительностьКлючевыхОпераций(ЗамерыДляЗаписи)` — region
  `СлужебныеПроцедурыИФункции` (internal, backward compatibility not
  guaranteed); do not use as the main API.
- `ОценкаПроизводительностиВызовСервераПовтИсп` — cached check of
  `ВыполнятьЗамерыПроизводительности` (measurement gating).
- ⚠️ `ОценкаПроизводительностиСлужебный` — internal API; in `src/cf` the root
  module also has methods in `СлужебныеПроцедурыИФункции`
  (`СоздатьКлючевуюОперацию`, `ЭкспортОценкиПроизводительности`, …) — do not
  call from application code.

`ЦентрМониторинга` family:

- `ЦентрМониторинга` — stable server API: `ЦентрМониторингаВключен`,
  `ВключитьПодсистему`, `ОтключитьПодсистему`, `ИдентификаторИнформационнойБазы`,
  `ЗаписатьОперациюБизнесСтатистики` (+ `…Час`/`…Сутки`),
  `ЗаписыватьОперацииБизнесСтатистики`, `ЗаписатьСтатистикуКонфигурации`,
  `ЗаписатьСтатистикуОбъектаКонфигурации`.
- `ЦентрМониторингаКлиент` — client-side statistics recording. ⚠️ Signatures
  **differ** from the server-side ones: `ЗаписатьОперациюБизнесСтатистики(ИмяОперации,
  Значение)` (without `Комментарий`/`Разделитель`), and `…Час`/`…Сутки` have a different
  parameter order (`Значение` before `КлючУникальности`, with `КлючУникальности`
  optional). Do not copy the server signature into client code.
- ⚠️ `ЦентрМониторингаСлужебный` — internal API; including
  `ЗаписатьОперациюБизнесСтатистикиСлужебная(ПараметрыЗаписи)`,
  `ПолучитьПараметрыЦентраМониторинга` — backward compatibility is not
  guaranteed.

`КонтрольРаботыПользователей` is one server module, with 4 stable methods for
controlling the registration of data access events (a separate subsystem from
`ОценкаПроизводительности`, but related to access monitoring).

## Scenarios

### 1. Measure a server-side key operation

**Task:** wrap a server method (document posting, report generation) with an APDEX measurement and write the result to the `ЗамерыВремени` register.

**Functions:**
`ОценкаПроизводительности.НачатьЗамерВремени() Экспорт`
— Function → `Число` (UTC, ms, 14 characters; `0` if measurements are disabled), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ОценкаПроизводительности.ЗакончитьЗамерВремени(КлючеваяОперация, ВремяНачала, ВесЗамера = 1, Комментарий = Неопределено, ВыполненСОшибкой = Ложь) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

**Parameters:**
- `КлючеваяОперация` (`СправочникСсылка.КлючевыеОперации` / `Строка`) —
  key operation; when given a string, BSP will find/create the
  `КлючевыеОперации` catalog item itself when writing.
- `ВремяНачала` (`Число`) — the value returned by `НачатьЗамерВремени`.
  If `0` is passed (measurements were disabled at start time), the record is
  skipped silently; no check is required in the calling code.
- `ВесЗамера` (`Число`) — quantitative measurement value (for example, the
  number of lines in a document); default is `1`.
- `Комментарий` (`Строка` / `Соответствие`) — arbitrary measurement
  information; default is `Неопределено`.
- `ВыполненСОшибкой` (`Булево`) — indicates that the measurement was not
  completed successfully; default is `Ложь`.

**Example:**
```bsl
// В обработчике ОбработкаПроведения модуля документа
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
  (seconds), no connection to the key operation and register. Only
  `ОценкаПроизводительности.НачатьЗамерВремени` (millisecond precision, APDEX).
- ❌ Calling `ОценкаПроизводительности.НачатьЗамерВремени` on the thin client
  — the module is server-side. For the client, use `ОценкаПроизводительностиКлиент.ЗамерВремени`.
- ❌ `ОценкаПроизводительности.ЗакончитьЗамерВремени("Операция", 0)` without a
  preceding `НачатьЗамерВремени` — duration will be calculated from the "epoch";
  always pass the value returned by `НачатьЗамерВремени`.
- `НачатьЗамерВремени` itself gates recording through `ВыполнятьЗамерыПроизводительности`
  (reusable cache) — wrapping it in `Если … Тогда` is not needed.

### 2. Measure a long-running operation with nested steps

**Goal:** measure a multi-step operation (load → parse → write to the information base) with detail for each step.

**Functions:**
`ОценкаПроизводительности.НачатьЗамерДлительнойОперации(КлючеваяОперация) Экспорт`
— Function → `Соответствие` (measurement context; keys `КлючеваяОперация`, `ВремяНачала`, `ВремяПоследнегоЗамера`, `ВесЗамера`, `ВложенныеЗамеры`), region `#Область ПрограммныйИнтерфейс` (stable). Server, thick client, external connection.
`ОценкаПроизводительности.ЗафиксироватьЗамерДлительнойОперации(ОписаниеЗамера, КоличествоДанных, ИмяШага, Комментарий = "") Экспорт`
— Procedure (intermediate step), region `#Область ПрограммныйИнтерфейс` (stable). Server, thick client, external connection.
`ОценкаПроизводительности.ЗакончитьЗамерДлительнойОперации(ОписаниеЗамера, КоличествоДанных, ИмяШага = "", Комментарий = "") Экспорт`
— Procedure (completion), region `#Область ПрограммныйИнтерфейс` (stable). Server, thick client, external connection.

**Parameters:**
- `КлючеваяОперация` (`Строка`) — the name of the key operation.
- `ОписаниеЗамера` (`Соответствие`) — the value returned by `НачатьЗамерДлительнойОперации`; **must** be from the same pair of calls.
- `КоличествоДанных` (`Число`) — the amount processed in the step (e.g. number of rows).
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

**Notes / anti-patterns:**
- ❌ Passing to `ОписаниеЗамера` a `Соответствие` from another operation or `Неопределено` will make the measurement incorrect; the context must come from the same `НачатьЗамерДлительнойОперации`.
- ❌ `ОценкаПроизводительности.ЗафиксироватьДлительностьКлючевойОперации(100)` — such a method **does not exist**. A single record is `ЗакончитьЗамерВремени`; batch recording is `ОценкаПроизводительностиВызовСервера.ЗафиксироватьДлительностьКлючевыхОпераций` (⚠️ internal, see “Rare Methods”).
- For long-running background operations (background job), there is a separate subsystem `ДлительныеОперации`; here only measurement **inside** such an operation is covered.

### 3. Measure a client-side operation

**Goal:** measure the time to open a form / client-side processing with recording to the same `ЗамерыВремени` register through the client buffer.

**Functions:**
`ОценкаПроизводительностиКлиент.ЗамерВремени(КлючеваяОперация = Неопределено, ФиксироватьСОшибкой = Ложь, АвтоЗавершение = Истина) Экспорт`
— Function → `УникальныйИдентификатор` (measurement ID), region `#Область ПрограммныйИнтерфейс` (stable). Thin client, thick client.
`ОценкаПроизводительностиКлиент.ЗавершитьЗамерВремени(УИДЗамера, ВыполненСОшибкой = Ложь) Экспорт`
— Procedure (explicit completion when `АвтоЗавершение = Ложь`), region `#Область ПрограммныйИнтерфейс` (stable). Thin client, thick client.
`ОценкаПроизводительностиКлиент.УстановитьКлючевуюОперациюЗамера(УИДЗамера, КлючеваяОперация) Экспорт`
`ОценкаПроизводительностиКлиент.УстановитьВесЗамера(УИДЗамера, ВесЗамера) Экспорт`
`ОценкаПроизводительностиКлиент.УстановитьКомментарийЗамера(УИДЗамера, Комментарий) Экспорт`
`ОценкаПроизводительностиКлиент.УстановитьПризнакОшибкиЗамера(УИДЗамера, Признак) Экспорт`
— all Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Thin client, thick client.

**Parameters:**
- `КлючеваяОперация` (`Строка` / `Неопределено`) — the name of the key operation; if `Неопределено`, it is set later via `УстановитьКлючевуюОперациюЗамера`.
- `ФиксироватьСОшибкой` (`Булево`) — `Истина` → on auto-completion, the measurement is recorded with the “completed with error” flag; `Ложь` → normal.
- `АвтоЗавершение` (`Булево`) — `Истина` (default) → completion through the global wait handler; `Ложь` → explicit call to `ЗавершитьЗамерВремени`.
- `УИДЗамера` (`УникальныйИдентификатор`) — the value returned by `ЗамерВремени`.

**Example:**
```bsl
// Однострочный замер с авто-завершением
УИД = ОценкаПроизводительностиКлиент.ЗамерВремени("ОткрытиеФормыДокумента");

// Явный цикл: точные границы + вес + признак ошибки
УИД = ОценкаПроизводительностиКлиент.ЗамерВремени(, Ложь, Ложь);
Попытка
    // ...длительная клиентская обработка...
    ОценкаПроизводительностиКлиент.УстановитьВесЗамера(УИД, КоличествоСтрок);
Исключение
    ОценкаПроизводительностиКлиент.УстановитьПризнакОшибкиЗамера(УИД, Истина);
КонецПопытки;
ОценкаПроизводительностиКлиент.ЗавершитьЗамерВремени(УИД);
```

**Notes / anti-patterns:**
- ⚠️ Client measurements are stored in the client buffer and written with the periodicity of constant `ОценкаПроизводительностиПериодЗаписи` (by default, once per minute); if the session ends abnormally, some measurements may be lost.
- ❌ `ОценкаПроизводительностиКлиент.НачатьЗамерВремени(...)` — **deprecated** (`УстаревшиеПроцедурыИФункции`). Use `ЗамерВремени`.
- If `КлючеваяОперация = Неопределено`, be sure to set it via `УстановитьКлючевуюОперациюЗамера`, otherwise the measurement will not be linked to an operation.

### 4. Register business statistics

**Task:** record a quantitative metric ("how many documents were posted", "average attachment size") in the `БуферОперацийСтатистики` buffer for scheduled sending to the service.

**Functions:**
`ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистики(ИмяОперации, Значение, Комментарий = Неопределено, Разделитель = ".") Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистикиЧас(ИмяОперации, КлючУникальности, Значение, Замещать = Ложь) Экспорт`
`ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистикиСутки(ИмяОперации, КлючУникальности, Значение, Замещать = Ложь) Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЦентрМониторинга.ЗаписыватьОперацииБизнесСтатистики() Экспорт`
— Function → `Булево` (registration state getter), region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ИмяОперации` (`Строка`) — statistics operation name; if absent, a new one is created. The hierarchy is separated by `Разделитель` (default `"."`).
- `Значение` (`Число`) — quantitative value.
- `Комментарий` (`Строка`) — arbitrary comment; default is `Неопределено`.
- `Разделитель` (`Строка`) — separator for values in `ИмяОперации`, if not a dot; default is `"."`.
- `КлючУникальности` (arbitrary) — for `…Час`/`…Сутки`: a key by which only one record is kept for the period (hour/day).
- `Замещать` (`Булево`) — `Истина` → replaces the previous value for the period; `Ложь` (default) → accumulates.

**Example:**
```bsl
// Простой счётчик
ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистики(
    "Документы.ЗаказПокупателя.Проведение.Количество", 1, , ".");

// Счётчик с уникальностью за час (одна запись на документ в час)
Для Каждого Строка Из ТаблицаДокументов Цикл
    ПровестиДокумент(Строка.Ссылка);
    ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистикиЧас(
        "Документы.ЗаказПокупателя.Проведение.Факт",
        Строка.Ссылка.УникальныйИдентификатор(), 1, Ложь);
КонецЦикла;
```

**Nuances / anti-patterns:**
- ❌ Wrapping the write in `Если ЦентрМониторинга.ЦентрМониторингаВключен()`
  is redundant: `ЗаписатьОперациюБизнесСтатистики` gates the write itself through
  `ЗаписыватьОперацииБизнесСтатистики()`. The check is only appropriate if you need separate logic when the center is disabled (for example, writing to a local log).
- ❌ `ЦентрМониторинга.ЗаписатьОперацию("Сервис.Тест", 1)` — there is **no** such method; the stable name is `ЗаписатьОперациюБизнесСтатистики`.
- ❌ Direct write to the register `РегистрыСведений.БуферОперацийСтатистики.СоздатьНаборЗаписей()`
  bypasses service checks and categorization. Use only `ЦентрМониторинга.*`.
- ⚠️ On the client the signatures are different: `ЦентрМониторингаКлиент.ЗаписатьОперациюБизнесСтатистики(ИмяОперации, Значение)`
  (without `Комментарий`/`Разделитель`), and `…Час`/`…Сутки` —
  `(ИмяОперации, Значение, Замещать = Ложь, КлючУникальности = Неопределено)`
  (different parameter order). Do not substitute the server signature into
  client code.

### 5. Manage the "Monitoring Center" subsystem

**Task:** programmatically check/enable/disable the monitoring center and
obtain the information base identifier for linking statistics.

**Functions:**
`ЦентрМониторинга.ЦентрМониторингаВключен() Экспорт`
— Function → `Булево`, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЦентрМониторинга.ВключитьПодсистему() Экспорт`
`ЦентрМониторинга.ОтключитьПодсистему() Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЦентрМониторинга.ИдентификаторИнформационнойБазы() Экспорт`
— Function → information base identifier, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

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
  programmatic control, use the server-side `ВключитьПодсистему` /
  `ОтключитьПодсистему`.
- `ВключитьПодсистему` enables the scheduled job `СборИОтправкаСтатистики`
  (through the service module) — no separate job registration is needed.

### 6. Create and configure a key operation programmatically

**Task:** programmatically register a key operation in the `КлючевыеОперации`
catalog with target time and a “long-running” flag.

**Functions:**
`ОценкаПроизводительности.СоздатьКлючевыеОперации(КлючевыеОперации) Экспорт`
`ОценкаПроизводительности.УстановитьЦелевоеВремя(КлючевыеОперации) Экспорт`
`ОценкаПроизводительности.ИзменитьКлючевыеОперации(КлючевыеОперации) Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ОценкаПроизводительности.СоздатьКлючевуюОперацию(ИмяКлючевойОперации, ЦелевоеВремя = 1, Длительная = Ложь) Экспорт`
— Function → `СправочникСсылка.КлючевыеОперации`, ⚠️ region `#Область СлужебныеПроцедурыИФункции` (internal).

**Parameters:**
- `КлючевыеОперации` (`Массив` из `Структура`) — for batch
  `СоздатьКлючевыеОперации`/`УстановитьЦелевоеВремя`/`ИзменитьКлючевыеОперации`:
  structure items with fields `ИмяКлючевойОперации`, `ЦелевоеВремя`,
  `Длительная` and others.
- `ИмяКлючевойОперации` (`Строка`) — the operation name.
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
- ⚠️ `СоздатьКлючевуюОперацию` (single item) is **internal** region
  (`СлужебныеПроцедурыИФункции`); for new code, prefer the stable batch
  `СоздатьКлючевыеОперации`.
- ❌ Invented `ОценкаПроизводительности.ЗафиксироватьДлительностьКлючевойОперации(...)`
  does not exist. See scenario 2 and “Редкие методы”.
- Passing a string to `ЗакончитьЗамерВремени` instead of a reference is allowed:
  BSP will find/create the `КлючевыеОперации` catalog item itself when the measurement is written; separate operation registration is not required for one-off measurements.

### 7. Enable registration of data access events

**Task:** programmatically control registration of “Доступ.Доступ” events to
data in the registration log (152-FZ requirement for personal data) - a global
switch and detailed settings.

**Functions:**
`КонтрольРаботыПользователей.РегистрироватьДоступКДанным() Экспорт`
— Function → `Булево` (getter for the `НастройкиПользователейИПрав` panel setting), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`КонтрольРаботыПользователей.УстановитьРегистрациюДоступаКДанным(РегистрироватьДоступКДанным) Экспорт`
— Procedure (setter), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`КонтрольРаботыПользователей.НастройкиРегистрацииСобытийДоступаКДанным() Экспорт`
— Function → `Структура` (`Состав` — array of descriptions, `Комментарии` — `Соответствие`, `ОбщийКомментарий` — `Строка`), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`КонтрольРаботыПользователей.УстановитьНастройкиРегистрацииСобытийДоступаКДанным(Настройки) Экспорт`
— Procedure (settings setter), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

**Parameters:**
- `РегистрироватьДоступКДанным` (`Булево`) — global flag for registering
  access events.
- `Настройки` (`Структура`, see `НастройкиРегистрацииСобытийДоступаКДанным`) —
  composition of registered events, comments by fields, overall comment.

**Example:**
```bsl
// Включить регистрацию, если выключена
Если Не КонтрольРаботыПользователей.РегистрироватьДоступКДанным() Тогда
    КонтрольРаботыПользователей.УстановитьРегистрациюДоступаКДанным(Истина);
КонецЕсли;

// Прочитать текущие настройки и добавить комментарий по полю
Настройки = КонтрольРаботыПользователей.НастройкиРегистрацииСобытийДоступаКДанным();
Настройки.Комментарии.Вставить("Справочник.ФизическиеЛица.НомерДокумента",
    НСтр("ru = 'Серия и номер паспорта'"));
КонтрольРаботыПользователей.УстановитьНастройкиРегистрацииСобытийДоступаКДанным(Настройки);
```

**Nuances / anti-patterns:**
- This is the **user activity control** subsystem (shared access log),
  separate from `ЗащитаПерсональныхДанных` (which uses
  `УстановитьИспользованиеСобытияДоступ` to enable “Доступ.Доступ” for
  specific personal-data categories). Sequence: first the global switch
  `КонтрольРаботыПользователей.УстановитьРегистрациюДоступаКДанным(Истина)`,
  then per-category detail through `ЗащитаПерсональныхДанных`.
- ❌ Direct write to the registration log `ЗаписьЖурналаРегистрации(...)` instead of
  subsystem settings bypasses structured accounting of access events and
  personal-data categories.
- Settings are a structure, modified through the setter; do not try to write
  them directly into a constant/register.

## Rare Methods

- `ОценкаПроизводительности.ЗакончитьЗамерВремениТехнологический(КлючеваяОперация, ВремяНачала, ВесЗамера = 1, Комментарий = Неопределено) Экспорт`
  — stable (`ПрограммныйИнтерфейс`); technological measurement (without an
  error flag). Used less often than `ЗакончитьЗамерВремени`.
- `ОценкаПроизводительностиКлиент.НачатьЗамерВремениТехнологический(АвтоЗавершение = Истина, КлючеваяОперация = Неопределено) Экспорт`
  — stable; client technological measurement.
- `ОценкаПроизводительности.УстановитьПризнакЗавершенияСОшибкой(КлючевыеОперации) Экспорт`
  — ⚠️ **deprecated** (`УстаревшиеПроцедурыИФункции`): “deprecated, do not
  use in new code; alternative —
  `ЗакончитьЗамерВремени(…, ВыполненСОшибкой = Истина)`”.
- `ОценкаПроизводительностиВызовСервера.ЗафиксироватьДлительностьКлючевыхОпераций(ЗамерыДляЗаписи) Экспорт`
  — ⚠️ **internal** (`СлужебныеПроцедурыИФункции`): batch recording of a
  measurements array (`Структура` with `ЗамерыЗавершенные` — `Соответствие` from
  `УникальныйИдентификатор` → `Соответствие`, and `ИнформацияПрограммыПросмотра`
  — `Строка`). Returns the measurement write period on the server (seconds).
  Backward compatibility is not guaranteed - do not use as the main method;
  for a single record, use `ЗакончитьЗамерВремени`.
- `ЦентрМониторинга.ЗаписатьСтатистикуКонфигурации(СоответствиеИменМетаданных) Экспорт`
  and `ЦентрМониторинга.ЗаписатьСтатистикуОбъектаКонфигурации(ИмяОбъекта, Значение) Экспорт`
  — stable (`ПрограммныйИнтерфейс`); writing technical statistics by
  configuration metadata (volume, number of objects).
- `ЦентрМониторингаСлужебный.ЗаписатьОперациюБизнесСтатистикиСлужебная(ПараметрыЗаписи) Экспорт`
  — ⚠️ internal (`СлужебныеПроцедурыИФункции`); low-level write to the
  buffer; use the stable `ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистики`.

To look up the signature/region of any of these methods —
`python .claude/skills/bsp/scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`.
