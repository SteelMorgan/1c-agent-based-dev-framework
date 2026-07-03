# BSP Base Functionality: general-purpose utilities

The **БазоваяФункциональность** subsystem consists of the common modules `ОбщегоНазначения*`,
`СтроковыеФункции*`, `ФайловаяСистема*`. It covers tasks that appear in
every application module: user messages, XML/JSON serialization,
reading attributes by reference, secure secret storage, string formatting,
date parsing, temporary directories.

## Modules

The BSP suffix naming system (one base root + execution context):

- `ОбщегоНазначения` — server code.
- `ОбщегоНазначенияКлиент` — client code.
- `ОбщегоНазначенияКлиентСервер` — common (both client and server).
- `ОбщегоНазначенияВызовСервера` — client code with a server call without
  form context.
- `ОбщегоНазначенияСлужебныйКлиент` / `ОбщегоНазначенияСлужебныйКлиентСервер` —
  service API, ⚠️ backward compatibility is not guaranteed.
- ⚠️ `ОбщегоНазначенияСлужебный` (without a suffix) **does not exist** — a common
  mistake: calling a nonexistent module.
- `СтроковыеФункцииКлиентСервер` — string utilities, called from both the client
  and the server (used in this file as a universal one).
- `ФайловаяСистема` (server), `ФайловаяСистемаКлиент` (client).
  ⚠️ `ФайловаяСистемаКлиентСервер` **does not exist** — the client calls the server
  through the form context.

`БезопасноеХранилище` is an **information register**, not a **common module**. Access
is only through the wrappers `ОбщегоНазначения.*ДанныеВБезопасноеХранилище*`.

## Scenarios

### 1. Show a user message tied to an attribute

**Task:** show an error message next to a form field and abort
the operation (`Отказ = Истина`).

**Function:**
`ОбщегоНазначения.СообщитьПользователю(Знач ТекстСообщенияПользователю, Знач КлючДанных = Неопределено, Знач Поле = "", Знач ПутьКДанным = "", Отказ = Ложь) Экспорт`
— Procedure, region: `#Область ПрограммныйИнтерфейс` (stable). Server, Thick
client, External connection.

**Parameters:**
- `ТекстСообщенияПользователю` (String) — message text; for localization
  wrap it in `НСтр("ru = '…'")`.
- `КлючДанных` (Arbitrary) — object/reference/IB record key to which the
  message applies. Default is `Неопределено`.
- `Поле` (String) — form attribute name (binding the message to a field).
- `ПутьКДанным` (String) — data path (path to a form attribute, e.g.
  `"Объект"`).
- `Отказ` (Boolean) — output parameter; the method always sets it to `Истина`.

**Example:**
```bsl
Попытка
    // ...бизнес-логика...
Исключение
    // Сообщение у поля реквизита объекта, прерывает транзакцию формы
    ОбщегоНазначения.СообщитьПользователю(
        НСтр("ru = 'Не удалось провести документ.'"),
        ,                              // КлючДанных
        "Объект.НомерСтроки",           // Поле (путь внутри Объект)
        ,                              // ПутьКДанным
        Отказ);                         // Отказ = Истина
КонецПопытки;
```

**Nuances / anti-patterns:**
- ❌ `Сообщить("Ошибка!")` — not bound to a form attribute and does not affect
  `Отказ`. Use `СообщитьПользователю` with `Поле` and `Отказ`.
- ❌ Passing both `КлючДанных` and `ПутьКДанным` — binding conflict.
  Either a reference + `Поле`, or `ПутьКДанным`.
- ❌ `ОбщегоНазначения.ТекущаяДатаСеанса()` — the method **does not exist**
  (compilation error). `ТекущаяДатаСеанса()` is a platform global method,
  called without a module prefix.
- In a background job for a long-running operation (outside a transaction), the message  is written to a service register and sent to the client if the interaction
  system is connected - no separate processing is needed.

### 2. Save and read a secret in secure storage

**Task:** save an external API password/token for an account and then
read it at the time of the call, bypassing direct access to the register.

**Functions:**
`ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(Владелец, Данные, Ключ = "Пароль") Экспорт`
`ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(Владелец, Ключи = "Пароль", ОбщиеДанные = Неопределено, ОбластьДанных = Неопределено) Экспорт`
`ОбщегоНазначения.УдалитьДанныеИзБезопасногоХранилища(Знач Владелец, Знач Ключи = Неопределено) Экспорт`
- all Procedures/Functions, region `#Область ПрограммныйИнтерфейс` (stable).
Server, Thick client, External connection.

**Parameters:**
- `Владелец` (ПланОбменаСсылка / СправочникСсылка / Строка до 128 символов) -
  the secret owner object. For non-reference types - a string key with the subsystem
  name, e.g. `"СтандартныеПодсистемы.УправлениеДоступом"`; for multiple
  storages per subsystem - `"….<Уточнение>"`.
- `Данные` (Произвольный) - the secret; `Неопределено` deletes all owner data.
  If `Ключ = Неопределено` - `Данные` is `Структура`
  (structure key = data key name, value = secret).
- `Ключ` (Строка) - key of the stored data, default `"Пароль"`. Key name
  according to identifier rules (letter/_ as the first character).
- `Ключи` (Строка / Неопределено) - names separated by commas; `Неопределено` -
  return all owner data. Returns one value (one key) or
  `Структуру` (several), or `Неопределено` (no data).
- `ОбщиеДанные` (Булево) - `Истина` for shared data in the service model.
- `ОбластьДанных` (Число) - data area identifier for reading from an
  non-shared session; ignored in other cases.

**Example:**
```bsl
// Запись (при настройке интеграции) — в привилегированном режиме
УстановитьПривилегированныйРежим(Истина);
ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(УчётнаяЗапись, Пароль);          // Ключ по умолчанию "Пароль"
ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(УчётнаяЗапись, ТокенAPI, "Token");
УстановитьПривилегированныйРежим(Ложь);

// Чтение (в момент HTTP-вызова)
УстановитьПривилегированныйРежим(Истина);
Пароль = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(УчётнаяЗапись);
Токен  = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(УчётнаяЗапись, "Token");
УстановитьПривилегированныйРежим(Ложь);
```

**Nuances / anti-patterns:**
- ❌ Direct access to the register `РегистрыСведений.БезопасноеХранилищеДанных.СоздатьНаборЗаписей()`
  - bypasses encryption and access control. Only through `ОбщегоНазначения.*`.
- ⚠️ The calling code **itself** sets privileged mode before
  writing/reading - the wrapper does not do this. Without it, reading someone else's data
  will fail on permissions.
- The storage does not expose data directly to the user interface (except for administrators).

### 3. Serialize a value to XML/JSON and back

**Task:** serialize a structure/object into a string for storage/transmission and
deserialize it back; parse a JSON response from an external service with type
control.

**Functions:**
`ОбщегоНазначения.ЗначениеВСтрокуXML(Значение) Экспорт` — → XML string.
`ОбщегоНазначения.ЗначениеИзСтрокиXML(СтрокаXML) Экспорт` — ← XML string.
`ОбщегоНазначения.ЗначениеВJSON(Знач Значение) Экспорт` — → JSON string; dates in ISO (`YYYY-MM-DDThh:mm:ssZ`).
`ОбщегоНазначения.JSONВЗначение(Знач Строка, Знач ИменаСвойствСоЗначениямиДата = Неопределено, Знач ПрочитатьВСоответствие = Истина) Экспорт` — ← JSON string.
— all Functions, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Значение` (Arbitrary) — serializable value (only types
  serializable by the platform — see the syntax help).
- `ИменаСвойствСоЗначениямиДата` (comma-separated String / Array of String) —
  names of JSON properties that need to be deserialized as `Дата` (ISO format).
- `ПрочитатьВСоответствие` (Boolean) — `Истина` (default) → `Соответствие`;
  `Ложь` → `Структура`.

**Example:**
```bsl
// Сериализация
СтрокаXML = ОбщегоНазначения.ЗначениеВСтрокуXML(СтруктураДанных);
Обратно    = ОбщегоНазначения.ЗначениеИзСтрокиXML(СтрокаXML);

// Разбор JSON-ответа с датами
Данные = ОбщегоНазначения.JSONВЗначение(ТелоОтвета, "ДатаОтправки,ДатаПолучения");
// ПрочитатьВСоответствие = Ложь -> Структура вместо Соответствия
ДанныеСтр = ОбщегоНазначения.JSONВЗначение(ТелоОтвета, , Ложь);
```

**Nuances / anti-patterns:**
- ❌ `ОбщегоНазначения.JSONСтрокой(Структура)` — the method **does not exist**.
  The stable API is `ЗначениеВJSON`.
- ❌ Concatenating strings for formatted messages breaks localization —
  see scenario 6 (`ПодставитьПараметрыВСтроку`).
- JSON objects default to `Соответствие`; for `Структуры`, pass
  `ПрочитатьВСоответствие = Ложь`.

### 4. Read an object attribute by reference

**Task:** quickly read individual object attributes by reference, without
accessing them through dot notation (which loads the entire object); taking access rights into account.

**Functions:**
`ОбщегоНазначения.ЗначениеРеквизитаОбъекта(Ссылка, ИмяРеквизита, ВыбратьРазрешенные = Ложь, Знач КодЯзыка = Неопределено) Экспорт`
`ОбщегоНазначения.ЗначенияРеквизитовОбъекта(Ссылка, Знач Реквизиты, ВыбратьРазрешенные = Ложь, Знач КодЯзыка = Неопределено) Экспорт`
`ОбщегоНазначения.ЕстьРеквизитОбъекта(ИмяРеквизита, МетаданныеОбъекта) Экспорт` — check whether the attribute exists.
`ОбщегоНазначения.ЭтоСсылка(ПроверяемыйТип) Экспорт` — whether the type is a reference type.
— all Functions, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Ссылка` (ЛюбаяСсылка / Строка) — object or full name of a predefined
  item.
- `ИмяРеквизита` (Строка) — attribute name, can be via dot (`"Контрагент.ИНН"`).
- `Реквизиты` (Строка через запятую / Структура / Массив) — for
  `ЗначенияРеквизитовОбъекта` you can set aliases via a structure
  (key = alias, value = field name).
- `ВыбратьРазрешенные` (Булево) — `Истина` → query taking RLS into account: when
  record access is restricted, returns `Неопределено` for inaccessible fields; `Ложь` →
  exception if access is denied.
- `ПроверяемыйТип` (Тип) — type for `ЭтоСсылка`; for `Неопределено` returns `Ложь`.

**Example:**
```bsl
// One attribute
Контрагент = ОбщегоНазначения.ЗначениеРеквизитаОбъекта(ДокументСсылка, "Контрагент");

// Several attributes at once (one database query)
Реквизиты = ОбщегоНазначения.ЗначенияРеквизитовОбъекта(ДокументСсылка, "Контрагент, Сумма, Ответственный");

// Check whether an attribute exists before accessing it
Если ОбщегоНазначения.ЕстьРеквизитОбъекта("ИНН", Метаданные.Документы.Заказ) Тогда
    Инн = ОбщегоНазначения.ЗначениеРеквизитаОбъекта(Ссылка, "ИНН");
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `ОбщегоНазначения.ЭтоСсылка("СправочникСсылка.Контрагенты")` — the method expects
  a `Тип`, not a string. Wrap it: `ЭтоСсылка(Тип("СправочникСсылка.Контрагенты"))`.
- To read attributes **regardless of the current user's rights** —
  first switch to privileged mode.
- `ЗначенияРеквизитовОбъекта` is more efficient than several calls to
  `ЗначениеРеквизитаОбъекта` — one database query.

### 5. Check whether the БСП subsystem is connected

**Task:** optionally call functionality of a subsystem without requiring its mandatory connection in the configuration.

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
  (the server variant is not available on the client).
- Use together with `ОбщегоНазначения.ОбщийМодуль(<Имя>)` to obtain
  the module of an optional subsystem.

### 6. Format a string with parameters / parse a string / parse a date

**Task:** build a localizable message from a template with parameter substitution;
split a CSV string into an array; convert a string to a date.

**Functions:**
`СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(Знач ШаблонСтроки, Знач Параметр1, Знач Параметр2 = Неопределено, …, Знач Параметр9 = Неопределено) Экспорт` — `%1…%9`.
`СтроковыеФункцииКлиентСервер.РазложитьСтрокуВМассивПодстрок(Знач Значение, Знач Разделитель = ",", Знач ПропускатьПустыеСтроки = Неопределено, СокращатьНепечатаемыеСимволы = Ложь) Экспорт`
`СтроковыеФункцииКлиентСервер.СтрокаВДату(Знач Значение, ЧастьДаты = Неопределено) Экспорт` — string → Date; if the date cannot be recognized — `01.01.01 00:00:00`. A simple variant without date-part control is `ОбщегоНазначенияКлиентСервер.СтрокаВДату(Знач Значение) Экспорт` (1 parameter).
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
// Локализуемое сообщение (НСтр + шаблон)
Текст = СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(
    НСтр("ru = 'Ошибка в строке %1, колонке %2.'"), НомерСтроки, ИмяКолонки);

// Разбор CSV
Части = СтроковыеФункцииКлиентСервер.РазложитьСтрокуВМассивПодстрок(CSVСтрока, ",", Истина);

// Строка -> Дата (по умолчанию ЧастиДаты.Дата); нераспознанная дата -> 01.01.01
Дата = СтроковыеФункцииКлиентСервер.СтрокаВДату("31.12.2024");
ДатаВремя = СтроковыеФункцииКлиентСервер.СтрокаВДату("23-02-1980 09:15:45", ЧастиДаты.ДатаВремя);
```

**Nuances / anti-patterns:**
- ❌ Concatenation `"Ошибка в строке " + Номер` breaks multilingual support.
  Use `ПодставитьПараметрыВСтроку` + `НСтр`.
- ⚠️ In БСП there are **two** `СтрокаВДату` methods: `СтроковыеФункцииКлиентСервер` (2  parameter, date-part validation) and `ОбщегоНазначенияКлиентСервер` (1
  parameter, simpler). Clarify the needed one with `python scripts/bsp_api.py method
  СтрокаВДату --module <Модуль> --src src/cf`.
- For > 9 parameters - `ПодставитьПараметрыВСтрокуИзМассива` from the same module.

### 7. Create a temporary directory / open File Explorer

**Task:** get a unique temporary directory on the server for files;
open Windows File Explorer on the client with focus on the path.

**Functions:**
`ФайловаяСистема.СоздатьВременныйКаталог(Знач Расширение = "") Экспорт` — server, returns the full path.
`ФайловаяСистема.УдалитьВременныйКаталог(Знач Путь) Экспорт` — server, deletes the directory created by `СоздатьВременныйКаталог`.
`ФайловаяСистемаКлиент.ОткрытьПроводник(ПутьККаталогуИлиФайлу) Экспорт` — client.
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable).

**Parameters:**
- `Расширение` (String) — extension of the temporary directory (e.g. `"xml"`); by
  default `""`.
- `Путь` (String) — path to the temporary directory (the value returned by
  `СоздатьВременныйКаталог`).
- `ПутьККаталогуИлиФайлу` (String) — path that File Explorer will focus on.

**Example:**
```bsl
// Сервер: временный каталог для выгрузки
Каталог = ФайловаяСистема.СоздатьВременныйКаталог("xml");
Попытка
    // ...записать файлы в Каталог, обработать...
Исключение
    // ...обработка ошибки...
КонецПопытки;
ФайловаяСистема.УдалитьВременныйКаталог(Каталог);  // убрать за собой обязательно

// Клиент: открыть Проводник на скачанном файле
ФайловаяСистемаКлиент.ОткрытьПроводник(ПолныйПутьКФайлу);
```

**Nuances / anti-patterns:**
- ⚠️ `СоздатьВременныйКаталог` returns a directory that you **must delete**
  yourself after use (`ФайловаяСистема.УдалитьВременныйКаталог`),
  otherwise temporary files will accumulate. There is no `ФайловаяСистема.УдалитьКаталог` method.
- `ФайловаяСистема` is a server module; `ФайловаяСистемаКлиент` is a client module.
  ⚠️ `ФайловаяСистемаКлиентСервер` **does not exist**.

## Additional

Other stable methods of `ОбщегоНазначения` (region `ПрограммныйИнтерфейс`),
full signatures are available via `python scripts/bsp_api.py module ОбщегоНазначения --src src/cf`:

- `ЗначенияРеквизитовОбъектов(Ссылки, Реквизиты, …)` / `ЗначениеРеквизитаОбъектов(МассивСсылок, ИмяРеквизита, …)` — batch reading by an array of references.
- `УстановитьЗначениеРеквизита(Объект, ИмяРеквизита, Значение, КодЯзыка)` / `УстановитьЗначенияРеквизитов(Объект, Значения)` — writing object attributes.
- `ПредопределенныйЭлемент(ПолноеИмяПредопределенного)` — a reference to a predefined item by name.
- `ЕстьСсылкиНаОбъект(СсылкаИлиМассив, ИскатьСредиСлужебныхОбъектов = Ложь)` — checking whether the object is used before deletion.
- `ОбщийМодуль(Имя)` — get the module of an optional subsystem (for conditional invocation with `ПодсистемаСуществует`).
- `ПодставитьПараметрыВСтрокуИзМассива(ШаблонСтроки, Параметры)` — formatting with more than 9 parameters.

To find the signature/region of any of these methods —
`python scripts/bsp_api.py method <Имя> --src src/cf`. 