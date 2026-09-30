# BSP Base Functionality: general-purpose utilities

The **БазоваяФункциональность** subsystem consists of common modules `ОбщегоНазначения*`,
`СтроковыеФункции*`, `ФайловаяСистема*`. It covers tasks found in
every application module: user messages, XML/JSON serialization,
reading attributes by reference, secure storage of secrets, string
formatting, date parsing, temporary directories.

## Modules

The BSP suffix naming system (one base root + execution context):

- `ОбщегоНазначения` — server code.
- `ОбщегоНазначенияКлиент` — client code.
- `ОбщегоНазначенияКлиентСервер` — common (both client and server).
- `ОбщегоНазначенияВызовСервера` — client code with a server call without
  form context.
- `ОбщегоНазначенияСлужебныйКлиент` / `ОбщегоНазначенияСлужебныйКлиентСервер` —
  service API, ⚠️ backward compatibility is not guaranteed.
- ⚠️ `ОбщегоНазначенияСлужебный` (without suffix) **does not exist** — a common
  mistake: calling a non-existent module.
- `СтроковыеФункцииКлиентСервер` — string utilities, callable from both client
  and server (used in this file as a general-purpose one).
- `ФайловаяСистема` (server), `ФайловаяСистемаКлиент` (client).
  ⚠️ `ФайловаяСистемаКлиентСервер` **does not exist** — the client calls the server
  through the form context.

`БезопасноеХранилище` is an **information register**, not a **common module**. Access
to it is only through the wrappers `ОбщегоНазначения.*ДанныеВБезопасноеХранилище*`.

## Scenarios

### 1. Show a user message bound to a field

**Task:** show an error message next to a form field and abort
the operation (`Отказ = Истина`).

**Function:**
`ОбщегоНазначения.СообщитьПользователю(Знач ТекстСообщенияПользователю, Знач КлючДанных = Неопределено, Знач Поле = "", Знач ПутьКДанным = "", Отказ = Ложь) Экспорт`
— Procedure, region: `#Область ПрограммныйИнтерфейс` (stable). Server, Thick
client, External connection.

**Parameters:**
- `ТекстСообщенияПользователю` (String) — message text; for localization,
  wrap it in `НСтр("ru = '…'")`.
- `КлючДанных` (Arbitrary) — object/reference/IB record key to which the
  message applies. Default is `Неопределено`.
- `Поле` (String) — name of the form attribute (binding the message to a field).
- `ПутьКДанным` (String) — data path (path to a form attribute, e.g.
  `"Объект"`).
- `Отказ` (Boolean) — output parameter; the method always sets `Истина`.

**Example:**
```bsl
Попытка
    // ...бизнес-логика...
Исключение
    // Message at the object attribute field, aborts the form transaction
    ОбщегоНазначения.СообщитьПользователю(
        НСтр("ru = 'Не удалось провести документ.'"),
        ,                              // КлючДанных
        "Объект.НомерСтроки",           // Поле (path inside Объект)
        ,                              // ПутьКДанным
        Отказ);                         // Отказ = Истина
КонецПопытки;
```

**Nuances / anti-patterns:**
- ❌ `Сообщить("Ошибка!")` — is not bound to a form attribute and does not affect
  `Отказ`. Use `СообщитьПользователю` with `Поле` and `Отказ`.
- ❌ Passing both `КлючДанных` and `ПутьКДанным` at the same time — binding conflict.
  Either reference + `Поле`, or `ПутьКДанным`.
- ❌ `ОбщегоНазначения.ТекущаяДатаСеанса()` — method **does not exist**
  (compilation error). `ТекущаяДатаСеанса()` is a platform global method,
  called without a module prefix.
- In a background job of a long-running operation (outside a transaction), the message  is written to the service register and sent to the client if the interaction
  system is connected — no separate processing is needed.

### 2. Save and read a secret in secure storage

**Task:** save an external API password/token for an account and then
read it at call time, bypassing direct access to the register.

**Functions:**
`ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(Владелец, Данные, Ключ = "Пароль") Экспорт`
`ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(Владелец, Ключи = "Пароль", ОбщиеДанные = Неопределено, ОбластьДанных = Неопределено) Экспорт`
`ОбщегоНазначения.УдалитьДанныеИзБезопасногоХранилища(Знач Владелец, Знач Ключи = Неопределено) Экспорт`
— all Procedures/Functions, region `#Область ПрограммныйИнтерфейс` (stable).
Server, Thick client, External connection.

**Parameters:**
- `Владелец` (ПланОбменаСсылка / СправочникСсылка / Строка до 128 символов) —
  the secret owner object. For non-reference types, use a string key with the subsystem name, e.g. `"СтандартныеПодсистемы.УправлениеДоступом"`; for multiple
  storages per subsystem — `"….<Уточнение>"`.
- `Данные` (Произвольный) — the secret; `Неопределено` deletes all data for the owner. If `Ключ = Неопределено`, `Данные` is a `Структура`
  (structure key = data key name, value = secret).
- `Ключ` (Строка) — the key for the stored data, default `"Пароль"`. The key name
  must follow identifier rules (letter/_ as the first character).
- `Ключи` (Строка / Неопределено) — names separated by commas; `Неопределено` —
  return all owner data. Returns a single value (one key) or a `Структуру`
  (multiple), or `Неопределено` (no data).
- `ОбщиеДанные` (Булево) — `Истина` for shared data in the service model.
- `ОбластьДанных` (Число) — data area identifier for reading from a
  non-partitioned session; ignored in other cases.

**Example:**
```bsl
// Writing (during integration setup) — in privileged mode
УстановитьПривилегированныйРежим(Истина);
ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(УчётнаяЗапись, Пароль);          // Default key "Пароль"
ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(УчётнаяЗапись, ТокенAPI, "Token");
УстановитьПривилегированныйРежим(Ложь);

// Reading (at the time of the HTTP call)
УстановитьПривилегированныйРежим(Истина);
Пароль = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(УчётнаяЗапись);
Токен  = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(УчётнаяЗапись, "Token");
УстановитьПривилегированныйРежим(Ложь);
```

**Nuances / antipatterns:**
- ❌ Direct access to the register `РегистрыСведений.БезопасноеХранилище.СоздатьНаборЗаписей()`
  — bypasses encryption and access control. Only through `ОбщегоНазначения.*`.
- ⚠️ The calling code **itself** sets privileged mode before
  writing/reading — the wrapper does not do this. Without it, reading someone else’s data
  will fail due to permissions.
- The storage does not return data directly to the user interface (except for administrators).

### 3. Serialize a value to XML/JSON and back

**Task:** serialize a structure/object to a string for storage/transmission and
deserialize it back; parse a JSON response from an external service with type
control.

**Functions:**
`ОбщегоНазначения.ЗначениеВСтрокуXML(Значение) Экспорт` — → XML string.
`ОбщегоНазначения.ЗначениеИзСтрокиXML(СтрокаXML) Экспорт` — ← XML string.
`ОбщегоНазначения.ЗначениеВJSON(Знач Значение) Экспорт` — → JSON string; dates in ISO (`YYYY-MM-DDThh:mm:ssZ`).
`ОбщегоНазначения.JSONВЗначение(Знач Строка, Знач ИменаСвойствСоЗначениямиДата = Неопределено, Знач ПрочитатьВСоответствие = Истина) Экспорт` — ← JSON string.
— all Functions, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Значение` (Произвольный) — serializable value (only types
  serializable by the platform — see syntax help).
- `ИменаСвойствСоЗначениямиДата` (String separated by commas / Array of String) —
  names of JSON properties that should be deserialized as `Дата` (ISO format).
- `ПрочитатьВСоответствие` (Boolean) — `Истина` (default) → `Соответствие`;
  `Ложь` → `Структура`.

**Example:**
```bsl
// Serialization
СтрокаXML = ОбщегоНазначения.ЗначениеВСтрокуXML(СтруктураДанных);
Обратно    = ОбщегоНазначения.ЗначениеИзСтрокиXML(СтрокаXML);

// Parsing a JSON response with dates
Данные = ОбщегоНазначения.JSONВЗначение(ТелоОтвета, "ДатаОтправки,ДатаПолучения");
// ПрочитатьВСоответствие = Ложь -> Структура instead of Соответствие
ДанныеСтр = ОбщегоНазначения.JSONВЗначение(ТелоОтвета, , Ложь);
```

**Nuances / antipatterns:**
- ❌ `ОбщегоНазначения.JSONСтрокой(Структура)` — the method does **not exist**.
  Stable API — `ЗначениеВJSON`.
- ❌ String concatenation for formatted messages breaks localization —
  see scenario 6 (`ПодставитьПараметрыВСтроку`).
- JSON objects by default → `Соответствие`; for `Структуры` pass
  `ПрочитатьВСоответствие = Ложь`.

### 4. Read an object attribute by reference

**Task:** quickly read individual object attributes by reference without
accessing them through dot notation (which loads the entire object); with access rights taken into account.

**Functions:**
`ОбщегоНазначения.ЗначениеРеквизитаОбъекта(Ссылка, ИмяРеквизита, ВыбратьРазрешенные = Ложь, Знач КодЯзыка = Неопределено) Экспорт`
`ОбщегоНазначения.ЗначенияРеквизитовОбъекта(Ссылка, Знач Реквизиты, ВыбратьРазрешенные = Ложь, Знач КодЯзыка = Неопределено) Экспорт`
`ОбщегоНазначения.ЕстьРеквизитОбъекта(ИмяРеквизита, МетаданныеОбъекта) Экспорт` — check whether an attribute exists.
`ОбщегоНазначения.ЭтоСсылка(ПроверяемыйТип) Экспорт` — whether the type is a reference type.
— all Functions, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Ссылка` (ЛюбаяСсылка / Строка) — object or full name of a predefined
  item.
- `ИмяРеквизита` (Строка) — attribute name, can use dot notation (`"Контрагент.ИНН"`).
- `Реквизиты` (String separated by commas / Structure / Array) — for
  `ЗначенияРеквизитовОбъекта` you can set aliases via a structure
  (key = alias, value = field name).
- `ВыбратьРазрешенные` (Булево) — `Истина` → query with RLS taken into account: when
  record access is restricted, returns `Неопределено` for inaccessible fields; `Ложь` →
  exception if rights are missing.
- `ПроверяемыйТип` (Тип) — type for `ЭтоСсылка`; for `Неопределено` returns `Ложь`.

**Example:**
```bsl
// Single attribute
Контрагент = ОбщегоНазначения.ЗначениеРеквизитаОбъекта(ДокументСсылка, "Контрагент");

// Several attributes at once (one DB query)
Реквизиты = ОбщегоНазначения.ЗначенияРеквизитовОбъекта(ДокументСсылка, "Контрагент, Сумма, Ответственный");

// Check whether an attribute exists before accessing it
Если ОбщегоНазначения.ЕстьРеквизитОбъекта("ИНН", Метаданные.Документы.Заказ) Тогда
    Инн = ОбщегоНазначения.ЗначениеРеквизитаОбъекта(Ссылка, "ИНН");
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `ОбщегоНазначения.ЭтоСсылка("СправочникСсылка.Контрагенты")` — the method expects
  `Тип`, not a string. Wrap it: `ЭтоСсылка(Тип("СправочникСсылка.Контрагенты"))`.
- To read attributes **regardless of the current user's rights** —
  first switch to privileged mode.
- `ЗначенияРеквизитовОбъекта` is more efficient than multiple calls to
  `ЗначениеРеквизитаОбъекта` — one DB query.

### 5. Check whether the БСП subsystem is connected

**Task:** optionally call functionality of a subsystem without requiring it to be
mandatory in the configuration.

**Function:**
`ОбщегоНазначения.ПодсистемаСуществует(ПолноеИмяПодсистемы) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ПолноеИмяПодсистемы` (String) — full subsystem name without the word
  `"Подсистема."`, case-sensitive, e.g.
  `"СтандартныеПодсистемы.ЭлектроннаяПодпись"`.

**Example:**
```bsl
Если ОбщегоНазначения.ПодсистемаСуществует("СтандартныеПодсистемы.ЭлектроннаяПодпись") Тогда
    МодульЭП = ОбщегоНазначения.ОбщийМодуль("ЭлектроннаяПодпись");
    МодульЭП.<ИмяМетода>();  // условный вызов API ЭП
КонецЕсли;
```

**Nuances / anti-patterns:**
- For calls from **client** code — `ОбщегоНазначенияКлиент.ПодсистемаСуществует`
  (the server version is unavailable on the client).
- Use together with `ОбщегоНазначения.ОбщийМодуль(<Имя>)` to obtain the
  module of an optional subsystem.

### 6. Format a string with parameters / split a string / parse a date

**Task:** build a localizable message from a template with parameter substitution;
split a CSV string into an array; convert a string to a date.

**Functions:**
`СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(Знач ШаблонСтроки, Знач Параметр1, Знач Параметр2 = Неопределено, …, Знач Параметр9 = Неопределено) Экспорт` — `%1…%9`.
`СтроковыеФункцииКлиентСервер.РазложитьСтрокуВМассивПодстрок(Знач Значение, Знач Разделитель = ",", Знач ПропускатьПустыеСтроки = Неопределено, СокращатьНепечатаемыеСимволы = Ложь) Экспорт`
`СтроковыеФункцииКлиентСервер.СтрокаВДату(Знач Значение, ЧастьДаты = Неопределено) Экспорт` — string → Date; if the date could not be recognized — `01.01.01 00:00:00`. A simple variant without date-part control is `ОбщегоНазначенияКлиентСервер.СтрокаВДату(Знач Значение) Экспорт` (1 parameter).
— all Functions, region `#Область ПрограммныйИнтерфейс` (stable). Client + Server.

**Parameters:**
- `ШаблонСтроки` (String) — template with `%1…%9` placeholders; up to 9 parameters.
- `Параметр1…9` (Any) — substitution values; unused = `Неопределено`.
- `Разделитель` (String) — default `","`.
- `ПропускатьПустыеСтроки` (Boolean) — `Истина` discards empty elements.
- `СокращатьНепечатаемыеСимволы` (Boolean) — `Истина` trims spaces/non-printable characters.
- `Значение` (String) — for `СтрокаВДату`: date in format `"ДД.ММ.ГГГГ"`, `"ДД/ММ/ГГ"` or `"ДД-ММ-ГГ ЧЧ:ММ:СС"`, e.g. `"23.02.1980"`.
- `ЧастьДаты` (ЧастиДаты) — allowed date parts; default `ЧастиДаты.Дата` (can be `ЧастиДаты.Время` / `ЧастиДаты.ДатаВремя`).

**Example:**
```bsl
// Localizable message (НСтр + template)
Текст = СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(
    НСтр("ru = 'Ошибка в строке %1, колонке %2.'"), НомерСтроки, ИмяКолонки);

// CSV parsing
Части = СтроковыеФункцииКлиентСервер.РазложитьСтрокуВМассивПодстрок(CSVСтрока, ",", Истина);

// String -> Date (default ЧастьДаты.Дата); unrecognized date -> 01.01.01
Дата = СтроковыеФункцииКлиентСервер.СтрокаВДату("31.12.2024");
ДатаВремя = СтроковыеФункцииКлиентСервер.СтрокаВДату("23-02-1980 09:15:45", ЧастиДаты.ДатаВремя);
```

**Nuances / anti-patterns:**
- ❌ Concatenating `"Ошибка в строке " + Номер` breaks multilingual support.
  Use `ПодставитьПараметрыВСтроку` + `НСтр`.
- ⚠️ In БСП there are **two** `СтрокаВДату` methods: `СтроковыеФункцииКлиентСервер` (2  parameter, date part control) and `ОбщегоНазначенияКлиентСервер` (1
  parameter, simpler). Specify the needed one with `python scripts/bsp_api.py method
  СтрокаВДату --module <Модуль> --src src/cf`.
- For > 9 parameters — `ПодставитьПараметрыВСтрокуИзМассива` of the same module.

### 7. Create a temporary directory / open File Explorer

**Task:** get a unique temporary directory on the server for files;
open Windows File Explorer on the client focused on a path.

**Functions:**
`ФайловаяСистема.СоздатьВременныйКаталог(Знач Расширение = "") Экспорт` — server, returns the full path.
`ФайловаяСистема.УдалитьВременныйКаталог(Знач Путь) Экспорт` — server, deletes the directory created by `СоздатьВременныйКаталог`.
`ФайловаяСистемаКлиент.ОткрытьПроводник(ПутьККаталогуИлиФайлу) Экспорт` — client.
— Function/Procedure (СоздатьВременныйКаталог is a Function, returns a path; УдалитьВременныйКаталог and ОткрытьПроводник are Procedures), region `#Область ПрограммныйИнтерфейс` (stable).

**Parameters:**
- `Расширение` (String) — extension of the temporary directory (e.g. `"xml"`); by
  default `""`.
- `Путь` (String) — path to the temporary directory (the value returned by
  `СоздатьВременныйКаталог`).
- `ПутьККаталогуИлиФайлу` (String) — path that File Explorer will focus on.

**Example:**
```bsl
// Server: temporary directory for export
Каталог = ФайловаяСистема.СоздатьВременныйКаталог("xml");
Попытка
    // ...write files to Каталог, process...
Исключение
    // ...error handling...
КонецПопытки;
ФайловаяСистема.УдалитьВременныйКаталог(Каталог);  // обязательно clean up after yourself

// Client: open File Explorer on the downloaded file
ФайловаяСистемаКлиент.ОткрытьПроводник(ПолныйПутьКФайлу);
```

**Nuances / antipatterns:**
- ⚠️ `СоздатьВременныйКаталог` returns a directory that **you must
  delete** yourself after use (`ФайловаяСистема.УдалитьВременныйКаталог`),
  otherwise temporary files accumulate. There is no `ФайловаяСистема.УдалитьКаталог` method.
- `ФайловаяСистема` is a server module; `ФайловаяСистемаКлиент` is a client module.
  ⚠️ `ФайловаяСистемаКлиентСервер` **does not exist**.

## Additional

Other stable methods of `ОбщегоНазначения` (region `ПрограммныйИнтерфейс`),
full signatures are via `python scripts/bsp_api.py module ОбщегоНазначения --src src/cf`:

- `ЗначенияРеквизитовОбъектов(Ссылки, Реквизиты, …)` / `ЗначениеРеквизитаОбъектов(МассивСсылок, ИмяРеквизита, …)` — batch reading by array of references.
- `УстановитьЗначениеРеквизита(Объект, ИмяРеквизита, Значение, КодЯзыка = Неопределено)` / `УстановитьЗначенияРеквизитов(Объект, Значения, КодЯзыка = Неопределено)` — writing object attributes.
- `ПредопределенныйЭлемент(ПолноеИмяПредопределенного)` — reference to a predefined item by name.
- `ЕстьСсылкиНаОбъект(Знач СсылкаИлиМассивСсылок, Знач ИскатьСредиСлужебныхОбъектов = Ложь)` — checking object usage before deletion.
- `ОбщийМодуль(Имя)` — get the module of an optional subsystem (for conditional call with `ПодсистемаСуществует`).
- `ПодставитьПараметрыВСтрокуИзМассива(ШаблонСтроки, Параметры)` — formatting with a number of parameters > 9 (module СтроковыеФункцииКлиентСервер, not ОбщегоНазначения).

To find the signature/region of any of these methods —
`python scripts/bsp_api.py method <Имя> --src src/cf`.