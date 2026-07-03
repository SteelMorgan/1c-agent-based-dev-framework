# Performance and business statistics monitoring in БСП

Three related subsystems: **ОценкаПроизводительности** (APDEX measurements of key
operations in the `ЗамерыВремени` register), **ЦентрМониторинга** (anonymized
business statistics and technical data sent by a scheduled job to a 1С service
or a third-party service) and **КонтрольРаботыПользователей**
(managing the registration of data access events in the registration log).
It teaches how to wrap business operations with measurements and record quantitative
metrics without creating your own wrappers.

## Modules

The `ОценкаПроизводительности` family (suffix logic):

- `ОценкаПроизводительности` — server-side measurements: `НачатьЗамерВремени`,
  `ЗакончитьЗамерВремени`, `ЗакончитьЗамерВремениТехнологический`,
  `НачатьЗамерДлительнойОперации`, `ЗафиксироватьЗамерДлительнойОперации`,
  `ЗакончитьЗамерДлительнойОперации`, `СоздатьКлючевыеОперации`,
  `УстановитьЦелевоеВремя`, `ИзменитьКлючевыеОперации`.
- `ОценкаПроизводительностиКлиент` — client-side measurements: `ЗамерВремени`
  (single-line, auto-complete), `ЗавершитьЗамерВремени`,
  `НачатьЗамерВремениТехнологический`, `УстановитьПараметрыЗамера` and others.
  ⚠️ `ОценкаПроизводительностиКлиент.НачатьЗамерВремени` — **deprecated**
  (`УстаревшиеПроцедурыИФункции`); use `ЗамерВремени`.
- `ОценкаПроизводительностиВызовСервера` — batch writing of measurements.
  ⚠️ `ЗафиксироватьДлительностьКлючевыхОпераций(ЗамерыДляЗаписи)` — region
  `СлужебныеПроцедурыИФункции` (internal, backward compatibility not
  guaranteed); do not use as the main API.
- `ОценкаПроизводительностиВызовСервераПовтИсп` — cached check
  `ВыполнятьЗамерыПроизводительности` (measurement gating).
- ⚠️ `ОценкаПроизводительностиСлужебный` — internal API; in `src/cf` the root
  module also has methods in `СлужебныеПроцедурыИФункции`
  (`СоздатьКлючевуюОперацию`, `ЭкспортОценкиПроизводительности`, …) — do not
  call from application code.

The `ЦентрМониторинга` family:

- `ЦентрМониторинга` — stable server API: `ЦентрМониторингаВключен`,
  `ВключитьПодсистему`, `ОтключитьПодсистему`, `ИдентификаторИнформационнойБазы`,
  `ЗаписатьОперациюБизнесСтатистики` (+ `…Час`/`…Сутки`),
  `ЗаписыватьОперацииБизнесСтатистики`, `ЗаписатьСтатистикуКонфигурации`,
  `ЗаписатьСтатистикуОбъектаКонфигурации`.
- `ЦентрМониторингаКлиент` — client-side statistics recording. ⚠️ Signatures
  **differ** from the server-side ones: `ЗаписатьОперациюБизнесСтатистики(ИмяОперации,
  Значение)` (without `Комментарий`/`Разделитель`), and `…Час`/`…Сутки` have a different
  parameter order (`Значение` before `КлючУникальности`, `КлючУникальности`
  is optional). Do not copy the server signature into client code.
- ⚠️ `ЦентрМониторингаСлужебный` — internal API; including
  `ЗаписатьОперациюБизнесСтатистикиСлужебная(ПараметрыЗаписи)`,
  `ПолучитьПараметрыЦентраМониторинга` — backward compatibility is not
  guaranteed.

`КонтрольРаботыПользователей` — one server module, 4 stable methods for
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
  key operation; for a string, БСП will find/create the catalog item
  `КлючевыеОперации` itself when writing.
- `ВремяНачала` (`Число`) — value returned by `НачатьЗамерВремени`.
  If `0` is passed (measurements were disabled at start time) — the write is
  skipped silently, no check is required on the caller side.
- `ВесЗамера` (`Число`) — quantitative measurement value (e.g. number of
  lines in the document); default is `1`.
- `Комментарий` (`Строка` / `Соответствие`) — arbitrary measurement information;
  default is `Неопределено`.
- `ВыполненСОшибкой` (`Булево`) — indicates that the measurement was not completed;
  default is `Ложь`.

**Example:**
```bsl
// In the ОбработкаПроведения handler of the document module
ВремяНачала = ОценкаПроизводительности.НачатьЗамерВремени();

Попытка
    // ...standard posting logic...
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
- ❌ Measurement via `ТекущаяДата()` / `ТекущаяДатаСеанса()` — low precision
  (seconds), no link to the key operation and the register. Only
  `ОценкаПроизводительности.НачатьЗамерВремени` (precision down to ms, APDEX).
- ❌ Call `ОценкаПроизводительности.НачатьЗамерВремени` on a thin client
  — the module is server-side. For the client — `ОценкаПроизводительностиКлиент.ЗамерВремени`.
- ❌ `ОценкаПроизводительности.ЗакончитьЗамерВремени("Операция", 0)` without a
  preceding `НачатьЗамерВремени` — the duration will be calculated from the "epoch";
  always pass the value from `НачатьЗамерВремени`.
- `НачатьЗамерВремени` itself gates the write through `ВыполнятьЗамерыПроизводительности`
  (reuse cache) — wrapping in `If ... Then` is not needed.

### 2. Measure a long-running operation with nested steps

**Task:** measure a multi-step operation (loading → parsing → writing to the DB) with
detailing for each step.

**Functions:**
`ОценкаПроизводительности.НачатьЗамерДлительнойОперации(КлючеваяОперация) Экспорт`
— Function → `Соответствие` (measurement context; keys `КлючеваяОперация`, `ВремяНачала`, `ВремяПоследнегоЗамера`, `ВесЗамера`, `ВложенныеЗамеры`), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ОценкаПроизводительности.ЗафиксироватьЗамерДлительнойОперации(ОписаниеЗамера, КоличествоДанных, ИмяШага, Комментарий = "") Экспорт`
— Procedure (intermediate step), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ОценкаПроизводительности.ЗакончитьЗамерДлительнойОперации(ОписаниеЗамера, КоличествоДанных, ИмяШага = "", Комментарий = "") Экспорт`
— Procedure (completion), region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.

**Parameters:**
- `КлючеваяОперация` (`Строка`) — the name of the key operation.
- `ОписаниеЗамера` (`Соответствие`) — the value returned by
  `НачатьЗамерДлительнойОперации`; **must** be from the same pair of calls.
- `КоличествоДанных` (`Число`) — the amount processed in the step (for example, the number of
  rows).
- `ИмяШага` (`Строка`) — an arbitrary name of the nested step.
- `Комментарий` (`Строка`) — an arbitrary description; default is `""`.

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
- ❌ Passing into `ОписаниеЗамера` a `Соответствие` from another operation or
  `Неопределено` — the measurement will be incorrect; the context must come from the same
  `НачатьЗамерДлительнойОперации`.
- ❌ `ОценкаПроизводительности.ЗафиксироватьДлительностьКлючевойОперации(100)`
  — such a method **does not exist**. A single entry is
  `ЗакончитьЗамерВремени`; batch processing is
  `ОценкаПроизводительностиВызовСервера.ЗафиксироватьДлительностьКлючевыхОпераций`
  (⚠️ internal, see “Редкие методы”).
- For long-running background operations (background job) — a separate subsystem
  `ДлительныеОперации`; here only the measurement **inside** such an operation.

### 3. Measure a client operation

**Task:** measure the time to open a form / client processing and write it to
the same `ЗамерыВремени` register via the client buffer.

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
- `КлючеваяОперация` (`Строка` / `Неопределено`) — name of the key operation; if
  `Неопределено` — it is set later via `УстановитьКлючевуюОперациюЗамера`.
- `ФиксироватьСОшибкой` (`Булево`) — `Истина` → when auto-completed, the measurement
  will be recorded with the "completed with error" flag; `Ложь` → normal.
- `АвтоЗавершение` (`Булево`) — `Истина` (default) → completion through the
  global wait handler; `Ложь` → explicit call to `ЗавершитьЗамерВремени`.
- `УИДЗамера` (`УникальныйИдентификатор`) — the value returned by `ЗамерВремени`.

**Example:**
```bsl
// Single-line measurement with auto-completion
УИД = ОценкаПроизводительностиКлиент.ЗамерВремени("ОткрытиеФормыДокумента");

// Explicit cycle: exact boundaries + weight + error flag
УИД = ОценкаПроизводительностиКлиент.ЗамерВремени(, Ложь, Ложь);
Попытка
    // ...длительная клиентская обработка...
    ОценкаПроизводительностиКлиент.УстановитьВесЗамера(УИД, КоличествоСтрок);
Исключение
    ОценкаПроизводительностиКлиент.УстановитьПризнакОшибкиЗамера(УИД, Истина);
КонецПопытки;
ОценкаПроизводительностиКлиент.ЗавершитьЗамерВремени(УИД);
```

**Nuances / anti-patterns:**
- ⚠️ Client measurements are stored in the client buffer and written with the
  periodicity of the `ОценкаПроизводительностиПериодЗаписи` constant (by
  default, every minute); if the session terminates abnormally, some measurements
  may be lost.
- ❌ `ОценкаПроизводительностиКлиент.НачатьЗамерВремени(...)` — **deprecated**
  (`УстаревшиеПроцедурыИФункции`). Use `ЗамерВремени`.
- If `КлючеваяОперация = Неопределено` — be sure to set it via
  `УстановитьКлючевуюОперациюЗамера`, otherwise the measurement will not be tied to an operation.

### 4. Register business statistics

**Task:** write a quantitative indicator (“how many documents
were posted”, “average attachment size”) to the `БуферОперацийСтатистики` buffer for
scheduled sending to the service.

**Functions:**
`ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистики(ИмяОперации, Значение, Комментарий = Неопределено, Разделитель = ".") Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистикиЧас(ИмяОперации, КлючУникальности, Значение, Замещать = Ложь) Экспорт`
`ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистикиСутки(ИмяОперации, КлючУникальности, Значение, Замещать = Ложь) Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`ЦентрМониторинга.ЗаписыватьОперацииБизнесСтатистики() Экспорт`
— Function → `Булево` (registration status getter), region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ИмяОперации` (`Строка`) — the name of the statistics operation; if absent,
  a new one is created. The hierarchy is separated by `Разделитель` (default
  `"."`).
- `Значение` (`Число`) — quantitative value.
- `Комментарий` (`Строка`) — arbitrary comment; defaults to
  `Неопределено`.
- `Разделитель` (`Строка`) — separator for values in `ИмяОперации`, if not
  a dot; default `"."`.
- `КлючУникальности` (arbitrary) — for `…Час`/`…Сутки`: a key by which
  only one record is stored per period (hour/day).
- `Замещать` (`Булево`) — `Истина` → replaces the previous value for the period;
  `Ложь` (default) → accumulates.

**Example:**
```bsl
// Simple counter
ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистики(
    "Документы.ЗаказПокупателя.Проведение.Количество", 1, , ".");

// Counter with uniqueness per hour (one record per document per hour)
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
  `ЗаписыватьОперацииБизнесСтатистики()`. The check is only appropriate if, when
  the center is disabled, separate logic is needed (for example, writing to a local log).
- ❌ `ЦентрМониторинга.ЗаписатьОперацию("Сервис.Тест", 1)` — there is **no** such method;
  the stable name is `ЗаписатьОперациюБизнесСтатистики`.
- ❌ Direct write to the register `РегистрыСведений.БуферОперацийСтатистики.СоздатьНаборЗаписей()`
  — bypasses service checks and categorization. Only through
  `ЦентрМониторинга.*`.
- ⚠️ On the client the signatures are different: `ЦентрМониторингаКлиент.ЗаписатьОперациюБизнесСтатистики(ИмяОперации, Значение)`
  (without `Комментарий`/`Разделитель`), and `…Час`/`…Сутки` —
  `(ИмяОперации, Значение, Замещать = Ложь, КлючУникальности = Неопределено)`
  (different parameter order). Do not substitute the server signature into
  client code.

### 5. Manage the "Monitoring Center" subsystem programmatically

All methods without parameters, region `#Область ПрограммныйИнтерфейс` (stable), Server / Thick client / External connection:
`ЦентрМониторинга.ЦентрМониторингаВключен()` → `Булево`; `ЦентрМониторинга.ВключитьПодсистему()` / `ОтключитьПодсистему()`;
`ЦентрМониторинга.ИдентификаторИнформационнойБазы()` → information base identifier (binding of statistics packages).

**Nuances / antipatterns:**
- ❌ `ЦентрМониторингаКлиент.ПоказатьНастройкиЦентраМониторинга` — this is
  a UI method (region `СлужебныйПрограммныйИнтерфейс`), not a server API; for
  programmatic control use the server-side `ВключитьПодсистему` /
  `ОтключитьПодсистему`.
- `ВключитьПодсистему` activates the scheduled job `СборИОтправкаСтатистики`
  (through a service module) — no separate job registration is needed.

### 6. Create and configure a key operation programmatically

**Task:** programmatically register a key operation in the `КлючевыеОперации`
reference catalog with a target time and the "long-running" flag.

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
  `Длительная` and others.
- `ИмяКлючевойОперации` (`Строка`) — operation name.
- `ЦелевоеВремя` (`Число`) — target time in seconds; default is `1`.
- `Длительная` (`Булево`) — long-running operation flag; default is `Ложь`.

**Example:**
```bsl
// Batch registration (stable API)
КлючевыеОперации = Новый Массив;
Операция = Новый Структура;
Операция.Вставить("ИмяКлючевойОперации", "Документы.ЗаказПокупателя.Проведение");
Операция.Вставить("ЦелевоеВремя", 2);
Операция.Вставить("Длительная", Ложь);
КлючевыеОперации.Добавить(Операция);
ОценкаПроизводительности.СоздатьКлючевыеОперации(КлючевыеОперации);

// Later — change the target time
ОценкаПроизводительности.УстановитьЦелевоеВремя(КлючевыеОперации);
```

**Nuances / antipatterns:**
- ⚠️ `СоздатьКлючевуюОперацию` (single) — **service** region
  (`СлужебныеПроцедурыИФункции`); for new code, prefer the stable
  batch `СоздатьКлючевыеОперации`.
- ❌ Invented `ОценкаПроизводительности.ЗафиксироватьДлительностьКлючевойОперации(...)`
  — does not exist. See scenario 2 and "Rare methods".
- Passing a string to `ЗакончитьЗамерВремени` instead of a reference is acceptable:
  БСП will find/create the `КлючевыеОперации` reference catalog item itself when the
  measurement is written; separate operation registration is not required for one-off measurements.

### 7. Enable data access event logging

Programmatic control of logging the `Доступ.Доступ` event in the Event Log (152-FZ requirement for personal data). A pair of getter/setter for the global switch and a pair for detailed settings — all in region `#Область ПрограммныйИнтерфейс` (stable), Server / Thick client / External connection:

- `КонтрольРаботыПользователей.РегистрироватьДоступКДанным()` → `Булево` /
  `УстановитьРегистрациюДоступаКДанным(РегистрироватьДоступКДанным)` — global flag
  (setting of the panel `НастройкиПользователейИПрав`).
- `КонтрольРаботыПользователей.НастройкиРегистрацииСобытийДоступаКДанным()` → `Структура`
  (`Состав` — array of descriptions, `Комментарии` — `Соответствие`, e.g.
  `Настройки.Комментарии.Вставить("Справочник.ФизическиеЛица.НомерДокумента", НСтр("ru = 'Серия и номер паспорта'"))`,
  `ОбщийКомментарий` — `Строка`) /
  `УстановитьНастройкиРегистрацииСобытийДоступаКДанным(Настройки)` — read → modify → write via the setter.

**Nuances / antipatterns:**
- This is the **User Activity Control** subsystem (common access log),
  separate from `ЗащитаПерсональныхДанных` (which via
  `УстановитьИспользованиеСобытияДоступ` enables `Доступ.Доступ` for
  specific personal data categories). The chain is: first the global switch
  `КонтрольРаботыПользователей.УстановитьРегистрациюДоступаКДанным(Истина)`,
  then detailed categorization by categories via `ЗащитаПерсональныхДанных`.
- ❌ Direct write to the registration log `ЗаписьЖурналаРегистрации(...)` instead of
  subsystem settings bypasses structured accounting of access events and
  personal data categories.
- Settings are a structure, modified via the setter; do not try to write
  it directly to a constant/register.

## Rare Methods

- `ОценкаПроизводительности.ЗакончитьЗамерВремениТехнологический(КлючеваяОперация, ВремяНачала, ВесЗамера = 1, Комментарий = Неопределено) Экспорт`
  — stable (`ПрограммныйИнтерфейс`); technological measurement (without error
  flag). Used less often than `ЗакончитьЗамерВремени`.
- `ОценкаПроизводительностиКлиент.НачатьЗамерВремениТехнологический(АвтоЗавершение = Истина, КлючеваяОперация = Неопределено) Экспорт`
  — stable; client-side technological measurement.
- `ОценкаПроизводительности.УстановитьПризнакЗавершенияСОшибкой(КлючевыеОперации) Экспорт`
  — ⚠️ **deprecated** (`УстаревшиеПроцедурыИФункции`): "deprecated, do not
  use in new code; alternative —
  `ЗакончитьЗамерВремени(…, ВыполненСОшибкой = Истина)`".
- `ОценкаПроизводительностиВызовСервера.ЗафиксироватьДлительностьКлючевыхОпераций(ЗамерыДляЗаписи) Экспорт`
  — ⚠️ **service** (`СлужебныеПроцедурыИФункции`): batch write of an array
  of measurements (`Структура` with `ЗамерыЗавершенные` — `Соответствие` from
  `УникальныйИдентификатор` → `Соответствие`, and `ИнформацияПрограммыПросмотра`
  — `Строка`). Returns the measurement write period on the server (seconds).
  Backward compatibility is not guaranteed — do not use as the primary
  method; for single writes, use `ЗакончитьЗамерВремени`.
- `ЦентрМониторинга.ЗаписатьСтатистикуКонфигурации(СоответствиеИменМетаданных) Экспорт`
  and `ЦентрМониторинга.ЗаписатьСтатистикуОбъектаКонфигурации(ИмяОбъекта, Значение) Экспорт`
  — stable (`ПрограммныйИнтерфейс`); writing technological statistics
  for configuration metadata (volume, number of objects).
- `ЦентрМониторингаСлужебный.ЗаписатьОперациюБизнесСтатистикиСлужебная(ПараметрыЗаписи) Экспорт`
  — ⚠️ service (`СлужебныеПроцедурыИФункции`); low-level write to
  buffer; use the stable `ЦентрМониторинга.ЗаписатьОперациюБизнесСтатистики`.

To find the signature/region of any of these methods —
`python .claude/skills/bsp/scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`.