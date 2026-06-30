# BSP Base Functionality: General-Purpose Utilities

The **БазоваяФункциональность** subsystem consists of common modules `ОбщегоНазначения*`,
`СтроковыеФункции*`, `ФайловаяСистема*`. It covers tasks found in every application
module: user messages, XML/JSON serialization, reading attributes by reference,
secure storage of secrets, string formatting, date parsing, temporary directories.

## Modules

The BSP suffix naming scheme (one base root + execution context):

- `ОбщегоНазначения` — server code.
- `ОбщегоНазначенияКлиент` — client code.
- `ОбщегоНазначенияКлиентСервер` — shared (both client and server).
- `ОбщегоНазначенияВызовСервера` — client code with server call without form context.
- `ОбщегоНазначенияСлужебныйКлиент` / `ОбщегоНазначенияСлужебныйКлиентСервер` —
  service API, ⚠️ backward compatibility is not guaranteed.
- ⚠️ `ОбщегоНазначенияСлужебный` (without suffix) **does not exist** — a common
  mistake: calling a non-existent module.
- `СтроковыеФункцииКлиентСервер` — string utilities, callable from both client
  and server (used in this file as the universal one).
- `ФайловаяСистема` (server), `ФайловаяСистемаКлиент` (client).
  ⚠️ `ФайловаяСистемаКлиентСервер` **does not exist** — the client calls the
  server through form context.

`БезопасноеХранилище` is a **register of information**, not a common module.
Access to it is only through `ОбщегоНазначения.*ДанныеВБезопасноеХранилище*`
wrappers.

## Scenarios

### 1. Show a user message bound to an attribute

**Task:** show an error message next to a form field and interrupt the operation
(`Отказ = Истина`).

**Function:**
`ОбщегоНазначения.СообщитьПользователю(Знач ТекстСообщенияПользователю, Знач КлючДанных = Неопределено, Знач Поле = "", Знач ПутьКДанным = "", Отказ = Ложь) Экспорт`
— Procedure, region: `#Область ПрограммныйИнтерфейс` (stable). Server, Thick
client, External connection.

**Parameters:**
- `ТекстСообщенияПользователю` (String) — message text; for localization wrap
  it in `НСтр("ru = '…'")`.
- `КлючДанных` (Arbitrary) — object/reference/infobase record key to which the
  message applies. Default is `Неопределено`.
- `Поле` (String) — form attribute name (binding the message to a field).
- `ПутьКДанным` (String) — data path (path to a form attribute, e.g.
  `"Объект"`).
- `Отказ` (Boolean) — output parameter; the method always sets `Истина`.

**Example:**
```bsl
Попытка
    // ...business logic...
Исключение
    // Message at the object's attribute field, interrupts the form transaction
    ОбщегоНазначения.СообщитьПользователю(
        НСтр("ru = 'Не удалось провести документ.'"),
        ,                              // КлючДанных
        "Объект.НомерСтроки",           // Поле (path inside Объект)
        ,                              // ПутьКДанным
        Отказ);                         // Отказ = Истина
КонецПопытки;
```

**Nuances / anti-patterns:**
- ❌ `Сообщить("Ошибка!")` — does not bind to a form attribute and does not
  affect `Отказ`. Use `СообщитьПользователю` with `Поле` and `Отказ`.
- ❌ Passing both `КлючДанных` and `ПутьКДанным` — binding conflict. Either a
  reference + `Поле`, or `ПутьКДанным`.
- ❌ `ОбщегоНазначения.ТекущаяДатаСеанса()` — the method **does not exist**
  (compile error). `ТекущаяДатаСеанса()` is a platform global method, called
  without a module prefix.
- In a background job of a long-running operation (outside a transaction) the
  message is written to a service register and sent to the client if the
  interaction system is connected — no separate handling is needed.

### 2. Save and read a secret in secure storage

**Task:** save an external API password/token for an account and later read it
at the moment of invocation, without direct access to the register.

**Functions:**
`ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(Владелец, Данные, Ключ = "Пароль") Экспорт`
`ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(Владелец, Ключи = "Пароль", ОбщиеДанные = Неопределено, ОбластьДанных = Неопределено) Экспорт`
`ОбщегоНазначения.УдалитьДанныеИзБезопасногоХранилища(Знач Владелец, Знач Ключи = Неопределено) Экспорт`
— all Procedures/Functions, region `#Область ПрограммныйИнтерфейс` (stable).
Server, Thick client, External connection.

**Parameters:**
- `Владелец` (ПланОбменаСсылка / СправочникСсылка / String up to 128 characters) —
  the secret owner object. For non-reference types, a string key with the
  subsystem name, e.g. `"СтандартныеПодсистемы.УправлениеДоступом"`; for
  multiple storages per subsystem — `"….<Уточнение>"`.
- `Данные` (Arbitrary) — secret; `Неопределено` deletes all owner data. If
  `Ключ = Неопределено` — `Данные` is a `Структура` (structure key = data key
  name, value = secret).
- `Ключ` (String) — key of stored data, default `"Пароль"`. The key name must
  follow identifier rules (letter/_ as the first character).
- `Ключи` (String / Неопределено) — names separated by commas; `Неопределено`
  returns all owner data. Returns a single value (one key) or a `Структура`
  (multiple), or `Неопределено` (no data).
- `ОбщиеДанные` (Boolean) — `Истина` for shared data in the service model.
- `ОбластьДанных` (Number) — data area identifier for reading from a
  non-separated session; ignored in other cases.

**Example:**
```bsl
// Write (during integration setup) — in privileged mode
УстановитьПривилегированныйРежим(Истина);
ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(УчётнаяЗапись, Пароль);          // Ключ по умолчанию "Пароль"
ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(УчётнаяЗапись, ТокенAPI, "Token");
УстановитьПривилегированныйРежим(Ложь);

// Read (at the time of HTTP call)
УстановитьПривилегированныйРежим(Истина);
Пароль = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(УчётнаяЗапись);
Токен  = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(УчётнаяЗапись, "Token");
УстановитьПривилегированныйРежим(Ложь);
```

**Nuances / anti-patterns:**
- ❌ Direct access to `РегистрыСведений.БезопасноеХранилище.СоздатьНаборЗаписей()`
  bypasses encryption and access control. Only through `ОбщегоНазначения.*`.
- ⚠️ The calling code **itself** sets privileged mode before writing/reading —
  the wrapper does not do this. Without it, reading another user's data will
  fail with permissions.
- The storage does not expose data directly to the user interface (except for
  administrators).

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
- `Значение` (Arbitrary) — serializable value (only types serializable by the
  platform - see the syntax helper).
- `ИменаСвойствСоЗначениямиДата` (comma-separated String / Array of String) —
  names of JSON properties that must be deserialized as `Дата` (ISO format).
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

**Nuances / anti-patterns:**
- ❌ `ОбщегоНазначения.JSONСтрокой(Структура)` — the method **does not exist**.
  The stable API is `ЗначениеВJSON`.
- ❌ String concatenation for formatted messages breaks localization —
  see scenario 6 (`ПодставитьПараметрыВСтроку`).
- JSON objects by default map to `Соответствие`; for `Структуры` pass
  `ПрочитатьВСоответствие = Ложь`.

### 4. Read an object's attribute by reference

**Task:** quickly read individual object attributes by reference without
accessing them via dot notation (which loads the entire object); taking access
rights into account.

**Functions:**
`ОбщегоНазначения.ЗначениеРеквизитаОбъекта(Ссылка, ИмяРеквизита, ВыбратьРазрешенные = Ложь, Знач КодЯзыка = Неопределено) Экспорт`
`ОбщегоНазначения.ЗначенияРеквизитовОбъекта(Ссылка, Знач Реквизиты, ВыбратьРазрешенные = Ложь, Знач КодЯзыка = Неопределено) Экспорт`
`ОбщегоНазначения.ЕстьРеквизитОбъекта(ИмяРеквизита, МетаданныеОбъекта) Экспорт` — check whether an attribute exists.
`ОбщегоНазначения.ЭтоСсылка(ПроверяемыйТип) Экспорт` — whether the type is reference-based.
— all Functions, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Ссылка` (AnyReference / String) — object or full name of a predefined item.
- `ИмяРеквизита` (String) — attribute name, can use dot notation
  (`"Контрагент.ИНН"`).
- `Реквизиты` (String separated by commas / Структура / Array) — for
  `ЗначенияРеквизитовОбъекта` aliases can be specified through a structure
  (key = alias, value = field name).
- `ВыбратьРазрешенные` (Boolean) — `Истина` → query with RLS taken into account:
  with record restrictions it returns `Неопределено` for inaccessible fields;
  `Ложь` → exception if permissions are missing.
- `ПроверяемыйТип` (Type) — type for `ЭтоСсылка`; for `Неопределено` returns `Ложь`.

**Example:**
```bsl
// One attribute
Контрагент = ОбщегоНазначения.ЗначениеРеквизитаОбъекта(ДокументСсылка, "Контрагент");

// Several attributes at once (one database query)
Реквизиты = ОбщегоНазначения.ЗначенияРеквизитовОбъекта(ДокументСсылка, "Контрагент, Сумма, Ответственный");

// Check whether an attribute exists before access
Если ОбщегоНазначения.ЕстьРеквизитОбъекта("ИНН", Метаданные.Документы.Заказ) Тогда
    Инн = ОбщегоНазначения.ЗначениеРеквизитаОбъекта(Ссылка, "ИНН");
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `ОбщегоНазначения.ЭтоСсылка("СправочникСсылка.Контрагенты")` — the method
  expects a `Тип`, not a string. Wrap it: `ЭтоСсылка(Тип("СправочникСсылка.Контрагенты"))`.
- To read attributes **regardless of the current user's rights** —
  temporarily switch to privileged mode first.
- `ЗначенияРеквизитовОбъекта` is more efficient than several
  `ЗначениеРеквизитаОбъекта` calls — one database query.

### 5. Check whether the BSP subsystem is connected

**Task:** optionally call subsystem functionality without requiring its
mandatory connection in the configuration.

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
    МодульЭП.<ИмяМетода>();  // conditional call of the EP API
КонецЕсли;
```

**Nuances / anti-patterns:**
- For calling from **client** code — `ОбщегоНазначенияКлиент.ПодсистемаСуществует`
  (the server variant is not available on the client).
- Use together with `ОбщегоНазначения.ОбщийМодуль(<Имя>)` to obtain the module
  of an optional subsystem.

### 6. Format a string with parameters / split a string / parse a date

**Task:** build a localizable message from a template with parameter
substitution; split a CSV string into an array; convert a string to a date.

**Functions:**
`СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(Знач ШаблонСтроки, Знач Параметр1, Знач Параметр2 = Неопределено, …, Знач Параметр9 = Неопределено) Экспорт` — `%1…%9`.
`СтроковыеФункцииКлиентСервер.РазложитьСтрокуВМассивПодстрок(Знач Значение, Знач Разделитель = ",", Знач ПропускатьПустыеСтроки = Неопределено, СокращатьНепечатаемыеСимволы = Ложь) Экспорт`
`СтроковыеФункцииКлиентСервер.СтрокаВДату(Знач Значение, ЧастьДаты = Неопределено) Экспорт` — string → `Дата`; if the date cannot be recognized — `01.01.01 00:00:00`. A simple variant without date part control is `ОбщегоНазначенияКлиентСервер.СтрокаВДату(Знач Значение) Экспорт` (1 parameter).
— all Functions, region `#Область ПрограммныйИнтерфейс` (stable). Client + Server.

**Parameters:**
- `ШаблонСтроки` (String) — template with placeholders `%1…%9`; up to 9 parameters.
- `Параметр1…9` (Arbitrary) — substitution values; unused ones = `Неопределено`.
- `Разделитель` (String) — default `","`.
- `ПропускатьПустыеСтроки` (Boolean) — `Истина` discards empty elements.
- `СокращатьНепечатаемыеСимволы` (Boolean) — `Истина` trims spaces/non-printable characters.
- `Значение` (String) — for `СтрокаВДату`: date in `"ДД.ММ.ГГГГ"`, `"ДД/ММ/ГГ"` or `"ДД-ММ-ГГ ЧЧ:ММ:СС"` format, e.g. `"23.02.1980"`.
- `ЧастьДаты` (ЧастиДаты) — allowed date parts; default `ЧастиДаты.Дата` (can be `ЧастиДаты.Время` / `ЧастиДаты.ДатаВремя`).

**Example:**
```bsl
// Localizable message (НСтр + template)
Текст = СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(
    НСтр("ru = 'Ошибка в строке %1, колонке %2.'"), НомерСтроки, ИмяКолонки);

// CSV parsing
Части = СтроковыеФункцииКлиентСервер.РазложитьСтрокуВМассивПодстрок(CSVСтрока, ",", Истина);

// String -> Date (default ЧастиДаты.Дата); unrecognized date -> 01.01.01
Дата = СтроковыеФункцииКлиентСервер.СтрокаВДату("31.12.2024");
ДатаВремя = СтроковыеФункцииКлиентСервер.СтрокаВДату("23-02-1980 09:15:45", ЧастиДаты.ДатаВремя);
```

**Nuances / anti-patterns:**
- ❌ Concatenating `"Ошибка в строке " + Номер` breaks multilingual support.
  Use `ПодставитьПараметрыВСтроку` + `НСтр`.
- ⚠️ In BSP there are **two** `СтрокаВДату` methods: `СтроковыеФункцииКлиентСервер`
  (2 parameters, date part control) and `ОбщегоНазначенияКлиентСервер` (1
  parameter, simpler). Determine the needed one with `python scripts/bsp_api.py method
  СтрокаВДату --module <Модуль> --src src/cf`.
- For > 9 parameters — `ПодставитьПараметрыВСтрокуИзМассива` of the same module.

### 7. Create a temporary directory / open File Explorer

**Task:** get a unique temporary directory on the server for files; open
Windows File Explorer on the client focused on a path.

**Functions:**
`ФайловаяСистема.СоздатьВременныйКаталог(Знач Расширение = "") Экспорт` — server, returns full path.
`ФайловаяСистема.УдалитьВременныйКаталог(Знач Путь) Экспорт` — server, deletes the directory created by `СоздатьВременныйКаталог`.
`ФайловаяСистемаКлиент.ОткрытьПроводник(ПутьККаталогуИлиФайлу) Экспорт` — client.
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable).

**Parameters:**
- `Расширение` (String) — temporary directory extension (e.g. `"xml"`); default `""`.
- `Путь` (String) — path to the temporary directory (the value returned by `СоздатьВременныйКаталог`).
- `ПутьККаталогуИлиФайлу` (String) — path that File Explorer will focus on.

**Example:**
```bsl
// Server: temporary directory for export
Каталог = ФайловаяСистема.СоздатьВременныйКаталог("xml");
Попытка
    // ...write files into Каталог, process...
Исключение
    // ...error handling...
КонецПопытки;
ФайловаяСистема.УдалитьВременныйКаталог(Каталог);  // remove after yourself обязательно

// Client: open File Explorer on the downloaded file
ФайловаяСистемаКлиент.ОткрытьПроводник(ПолныйПутьКФайлу);
```

**Nuances / anti-patterns:**
- ⚠️ `СоздатьВременныйКаталог` returns a directory that **you must delete**
  yourself after use (`ФайловаяСистема.УдалитьВременныйКаталог`), otherwise
  temporary files accumulate. There is no `ФайловаяСистема.УдалитьКаталог` method.
- `ФайловаяСистема` is a server module; `ФайловаяСистемаКлиент` is a client
  module.
  ⚠️ `ФайловаяСистемаКлиентСервер` **does not exist**.

## Additional

Other stable methods of `ОбщегоНазначения` (region `ПрограммныйИнтерфейс`),
full signatures — via `python scripts/bsp_api.py module ОбщегоНазначения --src src/cf`:

- `ЗначенияРеквизитовОбъектов(Ссылки, Реквизиты, …)` / `ЗначениеРеквизитаОбъектов(МассивСсылок, ИмяРеквизита, …)` — batch reading by an array of references.
- `УстановитьЗначениеРеквизита(Объект, ИмяРеквизита, Значение, КодЯзыка)` / `УстановитьЗначенияРеквизитов(Объект, Значения)` — writing object attributes.
- `ПредопределенныйЭлемент(ПолноеИмяПредопределенного)` — reference to a predefined item by name.
- `ЕстьСсылкиНаОбъект(СсылкаИлиМассив, ИскатьСредиСлужебныхОбъектов = Ложь)` — check whether an object is used before deletion.
- `ОбщийМодуль(Имя)` — get the module of an optional subsystem (for conditional call with `ПодсистемаСуществует`).
- `ПодставитьПараметрыВСтрокуИзМассива(ШаблонСтроки, Параметры)` — formatting with parameter count > 9.

To search for the signature/region of any of these methods —
`python scripts/bsp_api.py method <Имя> --src src/cf`.
