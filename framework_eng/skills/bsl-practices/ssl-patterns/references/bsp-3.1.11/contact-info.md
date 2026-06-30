# Contact Information and Address Classifier

Subsystems **КонтактнаяИнформация** (storing addresses, phone numbers, and e-mail for
arbitrary owners in the `КонтактнаяИнформация` tabular section in JSON format)
and **АдресныйКлассификатор** (validating addresses against KЛАДР/ФИАС through the 1C web service,
region codes, ОКТМО, ИФНС). Closely related is the **РаботаСАдресами** subsystem - parsing
JSON/XML addresses into fields, and assembling an address by FИАС identifier.

## Modules

The `УправлениеКонтактнойИнформацией*` family (context suffixes are the same as in
`base-common.md`):

- `УправлениеКонтактнойИнформацией` - server API: read/write CI, types,
  parsing. region `ПрограммныйИнтерфейс` (stable).
- `УправлениеКонтактнойИнформациейКлиент` - UI: opening forms, command
  handlers, call/e-mail/map. Client.
- `УправлениеКонтактнойИнформациейКлиентСервер` - safe code: format
  detectors, presentation generation. Client + Server.
- `УправлениеКонтактнойИнформациейЛокализация` / `…КлиентЛокализация` /
  `…КлиентСерверЛокализация` - regional specifics (Russia, EAEU, foreign).
- `УправлениеКонтактнойИнформациейПереопределяемый` - hooks: called by БСП,
  implemented by application code (not called directly).
- `УправлениеКонтактнойИнформациейСлужебный` / `…СлужебныйВызовСервера` /
  `…СлужебныйПовтИсп` - ⚠️ internal, backward compatibility is not guaranteed.
  ⚠️ `УправлениеКонтактнойИнформациейСлужебныйКлиент` / `…СлужебныйКлиентСервер`
  **do not exist** - all service variants are server-only.

Subsystem `АдресныйКлассификатор`:

- `АдресныйКлассификатор` - stable server API: address validation, region
  codes, ОКТМО, loading information.
- `АдресныйКлассификаторКлиент` - classifier load/clear UI.
- `АдресныйКлассификаторПовтИсп` - cache. `АдресныйКлассификаторСлужебный` - ⚠️ internal.

Subsystem `РаботаСАдресами`:

- `РаботаСАдресами` - parse address into fields/region/city; ФИАС; JSON↔XML
  conversion; assemble address by identifier.
- `РаботаСАдресамиКлиент` / `РаботаСАдресамиКлиентСервер` / `РаботаСАдресамиПовтИсп`.
  ⚠️ `РаботаСАдресамиСлужебный` (without suffix) **does not exist** - only
  `РаботаСАдресамиСлужебныйКлиент`.

⚠️ Do not confuse the subsystems: write CI into an object with `УправлениеКонтактнойИнформацией.*`,
find the region code by name with `АдресныйКлассификатор.*`, parse address JSON into
fields with `РаботаСАдресами.*`.

## Scenarios

### 1. Read an object's contact information

**Task:** read an object's address/phone/e-mail as a table with JSON or as a presentation string.

**Functions:**
`УправлениеКонтактнойИнформацией.КонтактнаяИнформацияОбъекта(СсылкаИлиОбъект, ВидИлиТипКонтактнойИнформации = Неопределено, Дата = Неопределено, ТолькоПредставление = Истина) Экспорт`
— Function, region `ПрограммныйИнтерфейс` (stable). Server, Thick client, External connection.
`УправлениеКонтактнойИнформацией.КонтактнаяИнформацияОбъектов(СсылкиИлиОбъекты, Знач ТипыКонтактнойИнформации = Неопределено, Знач ВидыКонтактнойИнформации = Неопределено, Дата = Неопределено) Экспорт`
— Function, region `ПрограммныйИнтерфейс` (stable). Batch reading of an array of objects in a single query.

**Parameters:**
- `СсылкаИлиОбъект` (`ОпределяемыйТип.ВладелецКонтактнойИнформации` / `СправочникОбъект` / `ДокументОбъект`) - CI owner.
- `ВидИлиТипКонтактнойИнформации` (`СправочникСсылка.ВидыКонтактнойИнформации` / `ПеречислениеСсылка.ТипыКонтактнойИнформации`) - filter by type or kind; `Неопределено` = all.
- `Дата` (`Дата`) - record effective date (if the owner stores history).
- `ТолькоПредставление` (`Булево`) - `Истина` (default) -> returns a **string**; `Ложь` -> a **value table** with columns `Объект, Вид, Тип, Значение(JSON), Представление, Дата`.
- `СсылкиИлиОбъекты` (Array) - for the batch variant; `ТипыКонтактнойИнформации`/`ВидыКонтактнойИнформации` - filters.

**Example:**
```bsl
ВидАдреса = УправлениеКонтактнойИнформацией.ВидКонтактнойИнформацииПоИмени("ЮрАдресКонтрагента");

// Table with JSON values (ТолькоПредставление = Ложь is critical)
ТЗ = УправлениеКонтактнойИнформацией.КонтактнаяИнформацияОбъекта(
    Контрагент, ВидАдреса, , Ложь);
Если ТЗ.Количество() > 0 Тогда
    JSONАдреса   = ТЗ[0].Значение;      // BСП JSON string
    Представление = ТЗ[0].Представление;
КонецЕсли;

// Batch by array of references - a single DB query (not N+1)
ТЗВсе = УправлениеКонтактнойИнформацией.КонтактнаяИнформацияОбъектов(
    МассивКонтрагентов, Перечисления.ТипыКонтактнойИнформации.Адрес);
```

**Nuances / anti-patterns:**
- ❌ `ТЗ[0].Значение` when `ТолькоПредставление = Истина` - the method returns a **string**, not a table; indexing will fail. Pass `Ложь` explicitly when you need JSON.
- ❌ Calling `КонтактнаяИнформацияОбъекта` in a loop for each reference - `N+1` queries. Use `КонтактнаяИнформацияОбъектов` (one query).
- Getting a presentation string through `КонтактнаяИнформацияОбъекта(..., Истина)` is marked deprecated in the doc comment; for presentation use `ПредставлениеКонтактнойИнформацииОбъекта` (see scenario 3).

### 2. Write contact information into an object

**Task:** write an address/phone into an object's `КонтактнаяИнформация` tabular section from JSON.

**Functions:**
`УправлениеКонтактнойИнформацией.ЗаписатьКонтактнуюИнформацию(Объект, Знач Значение, ВидИнформации, ТипИнформации, ИдентификаторСтроки = 0, Дата = Неопределено) Экспорт`
— Procedure (not a Function), region `ПрограммныйИнтерфейс` (stable).
`УправлениеКонтактнойИнформацией.УстановитьКонтактнуюИнформациюОбъекта(СсылкаИлиОбъект, Знач КонтактнаяИнформация, Замещать = Истина) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Adds/modifies CI from a table (from `НоваяКонтактнаяИнформация`).
`УправлениеКонтактнойИнформацией.УстановитьКонтактнуюИнформациюОбъектов(КонтактнаяИнформация, Замещать = Истина) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Batch for multiple owners.

**Parameters:**
- `Объект` (`СправочникОбъект` / `ДокументОбъект`) - the **object**, not a reference.
- `Значение` (`Строка`) - CI in the internal BСП JSON format.
- `ВидИнформации` (`СправочникСсылка.ВидыКонтактнойИнформации`), `ТипИнформации` (`ПеречислениеСсылка.ТипыКонтактнойИнформации`).
- `ИдентификаторСтроки` (`Число`) - `0` for the object attribute; `> 0` for a tabular section row.
- `Дата` (`Дата`) - effective date (when history is stored).
- `КонтактнаяИнформация` (`ТаблицаЗначений`) - for `Установить*`: a table with columns from `НоваяКонтактнаяИнформация`.
- `Замещать` (`Булево`) - `Истина` (default) replaces all CI of the kind; `Ложь` adds a record.

**Example:**
```bsl
ВидАдреса = УправлениеКонтактнойИнформацией.ВидКонтактнойИнформацииПоИмени("ЮрАдресКонтрагента");
ОбъектСпр = Контрагент.ПолучитьОбъект();   // обязательно объект, not a reference

УправлениеКонтактнойИнформацией.ЗаписатьКонтактнуюИнформацию(
    ОбъектСпр, JSONАдреса, ВидАдреса,
    Перечисления.ТипыКонтактнойИнформации.Адрес);
ОбъектСпр.Записать();
```

**Nuances / anti-patterns:**
- ❌ Passing a **reference** to `ЗаписатьКонтактнуюИнформацию` - the write goes to
  an in-memory copy, no actual database write occurs. Only `СправочникОбъект` + `.Записать()`.
- `УстановитьКонтактнуюИнформациюОбъекта` writes the owner itself if a **reference** is passed; if an **object** is passed, save it separately.
- ⚠️ There are no `ПолучитьКонтактнуюИнформацию` / `УстановитьКонтактнуюИнформацию`
  methods (without the `Объекта` suffix) - a common mistake by analogy. The real names are
  `КонтактнаяИнформацияОбъекта`, `УстановитьКонтактнуюИнформациюОбъекта`.

### 3. Get a string presentation of CI (for UI/printing)

**Task:** get a ready-to-use address/phone string for a report, print form, or form attribute.

**Function:**
`УправлениеКонтактнойИнформацией.ПредставлениеКонтактнойИнформацииОбъекта(СсылкаИлиОбъект, ВидКонтактнойИнформации, Разделитель = ",", Дата = Неопределено, ДополнительныеПараметры = Неопределено) Экспорт`
— Function, region `ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `СсылкаИлиОбъект` (Arbitrary) - CI owner.
- `ВидКонтактнойИнформации` (`СправочникСсылка.ВидыКонтактнойИнформации`).
- `Разделитель` (`Строка`) - between multiple records; default is `", "` (comma with a space).
- `Дата` (`Дата`) - required if the owner stores change history.
- `ДополнительныеПараметры` (`Структура`): `ТолькоПервая` (`Булево`, default `Ложь`) - only the main record; `БезПробелов` (`Булево`, default `Ложь`) - no space after the separator.

**Example:**
```bsl
ВидТелефона = УправлениеКонтактнойИнформацией.ВидКонтактнойИнформацииПоИмени("ТелефонКонтрагента");
СтрокаКИ = УправлениеКонтактнойИнформацией.ПредставлениеКонтактнойИнформацииОбъекта(
    Контрагент, ВидТелефона, ", ", ,
    Новый Структура("ТолькоПервая, БезПробелов", Ложь, Ложь));
ОбластьМакета.Параметры.Телефон = СтрокаКИ;
```

**Nuances / anti-patterns:**
- ❌ Substituting `ТЗ[0].Значение` (JSON/XML) directly into a print form layout -
  unreadable JSON will appear. First use `ПредставлениеКонтактнойИнформацииОбъекта`,
  then place the string into the layout.
- `КонтактнаяИнформацияОбъекта(..., ТолькоПредставление = Истина)` for getting
  the presentation is deprecated - use `ПредставлениеКонтактнойИнформацииОбъекта`.

### 4. Open the contact information input form

**Task:** from a form command handler, open a managed form for entering/editing CI (address/phone/e-mail) with the result returned in a notification.

**Functions:**
`УправлениеКонтактнойИнформациейКлиент.ПараметрыФормыКонтактнойИнформации(ВидКонтактнойИнформации, Значение, Представление = Неопределено, Комментарий = Неопределено, ТипКонтактнойИнформации = Неопределено) Экспорт`
— Structure constructor function. Client.
`УправлениеКонтактнойИнформациейКлиент.ОткрытьФормуКонтактнойИнформации(Параметры, Владелец = Неопределено, Оповещение = Неопределено) Экспорт`
— Function (returns `ФормаКлиентскогоПриложения`), region `ПрограммныйИнтерфейс` (stable). Client.
`УправлениеКонтактнойИнформациейКлиент.ОткрытьФормуВводаАдреса(Форма, Результат) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Opens the form for **address only** from a plug-in command.

**Parameters:**
- `Параметры` (`Структура`) - from `ПараметрыФормыКонтактнойИнформации`: keys `Вид`, `Значение` (JSON), `Представление`, `Комментарий`, `Тип`.
- `Владелец` (Arbitrary) - for the opened form.
- `Оповещение` (`ОписаниеОповещения`) - close handler; `Результат` = `Неопределено` (cancel) or a structure with `Значение` (new JSON) and `Представление`.

**Example:**
```bsl
&НаКлиенте
Процедура КомандаРедактироватьАдрес(Команда)
    ВидАдреса = УправлениеКонтактнойИнформацией.ВидКонтактнойИнформацииПоИмени("ЮрАдресКонтрагента");

    ПараметрыФормы = УправлениеКонтактнойИнформациейКлиент.ПараметрыФормыКонтактнойИнформации(
        ВидАдреса, ЭтаФорма[ВидАдреса.ИмяРеквизита], ЭтаФорма[ВидАдреса.ИмяРеквизита + "Представление"]);

    УправлениеКонтактнойИнформациейКлиент.ОткрытьФормуКонтактнойИнформации(
        ПараметрыФормы, ЭтаФорма,
        Новый ОписаниеОповещения("ПослеРедактированияАдреса", ЭтаФорма));
КонецПроцедуры

&НаКлиенте
Процедура ПослеРедактированияАдреса(Результат, ДопПараметры) Экспорт
    Если Результат <> Неопределено Тогда
        // Результат.Значение - new JSON, Результат.Представление - string
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- `ОткрытьФормуКонтактнойИнформации` is universal (address/phone/e-mail);
  `ОткрытьФормуВводаАдреса` is address only, called from a plug-in command.
- The result arrives in `ОписаниеОповещения`, not as a return value - do not
  wait for it synchronously.

### 5. Detect the stored CI format and parse the address into fields

**Task:** before parsing the CI string, determine the format (JSON or XML), then
extract region/city/postal code/codes from the address.

**Functions:**
`УправлениеКонтактнойИнформациейКлиентСервер.ЭтоКонтактнаяИнформацияВJSON(Знач Текст) Экспорт`
`УправлениеКонтактнойИнформациейКлиентСервер.ЭтоКонтактнаяИнформацияВXML(Знач Текст) Экспорт`
— Functions -> `Булево`, region `ПрограммныйИнтерфейс` (stable). Client + Server.
`РаботаСАдресами.СведенияОбАдресе(Адрес, ДополнительныеПараметры = Неопределено) Экспорт`
— Function, region `ПрограммныйИнтерфейс` (stable). Parse address into fields.
`РаботаСАдресами.РегионАдресаКонтактнойИнформации(Знач Адрес) Экспорт`
— Function -> `Строка` (name of the Russian Federation subject; empty string if not defined; **exception** if the string is not an address). stable. Server.

**Parameters:**
- `Текст` (`Строка`) - CI string to check.
- `Адрес` (`Строка` / `Структура`) - JSON or XML matching the `Адрес` XDTO package.
- `ДополнительныеПараметры` (`Структура`) - for `СведенияОбАдресе`: `КодыКЛАДР` (`Булево`) - add KЛАДР codes; `ПроверитьАдрес`; `КодыАдреса` (ФИАС); `НаименованиеВключаетСокращение`.

**Example:**
```bsl
JSONАдреса = ТЗ[0].Значение;   // from КонтактнаяИнформацияОбъекта

Если УправлениеКонтактнойИнформациейКлиентСервер.ЭтоКонтактнаяИнформацияВJSON(JSONАдреса) Тогда
    Регион = РаботаСАдресами.РегионАдресаКонтактнойИнформации(JSONАдреса);
    Данные = РаботаСАдресами.СведенияОбАдресе(JSONАдреса, Новый Структура("КодыКЛАДР", Истина));
    // Данные.Регион, Данные.Город, Данные.ПочтовыйИндекс, Данные.КодКЛАДР, Данные.КодИФНСФЛ ...
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Parsing CI JSON/XML manually with `СтрРазделить`/`ЧтениеJSON` - breaks when
  the internal CI structure changes in a new BСП version. Use only BСП methods.
- ⚠️ The `СведенияОбАдресе` method is defined in **two** modules with different signatures:
  `РаботаСАдресами.СведенияОбАдресе(Адрес, ДополнительныеПараметры = Неопределено)` and
  `УправлениеКонтактнойИнформацией.СведенияОбАдресе(Адрес = Неопределено, ДополнительныеПараметры = Неопределено)`.
  Specify the module via `python scripts/bsp_api.py method СведенияОбАдресе --module <Модуль>`.
- Before parsing, **always** check the format: old records may be XML.

### 6. Validate a batch of addresses against the classifier (in the background)

**Task:** validate an array of addresses against KЛАДР/ФИАС through the 1C web service
(takes up to 20 seconds - wrap it in a long-running operation).

**Functions:**
`АдресныйКлассификатор.АдресныйКлассификаторЗагружен() Экспорт`
— Function -> `Булево`, region `ПрограммныйИнтерфейс` (stable). Server. **Up to 7 seconds** (checks web service availability) - call in the background.
`АдресныйКлассификатор.ПроверитьАдреса(Адреса) Экспорт`
— Function, region `ПрограммныйИнтерфейс` (stable). Server. Returns an array of results with fields `Ошибки`, `Варианты` (`КодКЛАДР`, `OKATO`, `ОКТМО`, `КодИФНСФЛ`, `КодИФНСЮЛ`, `КодУчасткаИФНСФЛ`, `КодУчасткаИФНСЮЛ`).

**Parameters:**
- `Адреса` (`Массив` of `Структура`) - each element is `{Адрес: <JSON or XML>}`.

**Example:**
```bsl
// Server: wrapper for a background call
&НаСервере
Функция ПроверитьАдресаВФоне(МассивJSON)
    ПараметрыВыполнения = ДлительныеОперации.ПараметрыВыполненияФункции(УникальныйИдентификатор);
    ПараметрыВыполнения.НаименованиеФоновогоЗадания = НСтр("ru = 'Проверка адресов'");
    Возврат ДлительныеОперации.ВыполнитьФункцию(
        ПараметрыВыполнения, "АдресныйКлассификатор.ПроверитьАдреса", МассивJSON);
КонецФункции

// Client: wait for completion
&НаКлиенте
Процедура КомандаПроверитьАдреса(Команда)
    ДлительнаяОперация = ПроверитьАдресаВФоне(МассивJSONАдресов);
    Оповещение = Новый ОписаниеОповещения("ПослеПроверкиАдресов", ЭтаФорма);
    ДлительныеОперацииКлиент.ОжидатьЗавершение(ДлительнаяОперация, Оповещение,
        ДлительныеОперацииКлиент.ПараметрыОжидания(ЭтаФорма));
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Calling `АдресныйКлассификатор.ПроверитьАдреса` synchronously from the client - the form will freeze for 20 seconds. Use only `ДлительныеОперации.ВыполнитьФункцию`.
- `АдресныйКлассификаторЗагружен` also takes up to 7 seconds (HTTP check of the web service) - do not call it in a form `ПриОткрытии` handler without a background wrapper.
- ⚠️ `РаботаСАдресами.ПроверитьАдреса(Знач Адреса, ДополнительныеПараметры = Неопределено)` - a separate method (different module, different signature); do not confuse it with `АдресныйКлассификатор.ПроверитьАдреса`.

### 7. Region codes and ОКТМО / assembling an address by ФИАС

**Task:** get a region code by subject name; get address details by ОКТМО code; assemble a full address by ФИАС identifier.

**Functions:**
`АдресныйКлассификатор.КодРегионаПоНаименованию(Название) Экспорт`
— Function -> `Число` (e.g. `50`) / `Неопределено`, region `ПрограммныйИнтерфейс` (stable). Server.
`АдресныйКлассификатор.СведенияПоОКМТО(ОКТМО) Экспорт`
— Function -> `Структура` (`КодРегиона, Регион, РегионТипПолный, РегионТипКраткий, ИдентификаторРегиона, МуниципальныйРайон, Поселение, НаселенныйПункт, ПочтовыйИндекс, OKATO, КодКЛАДР, КодИФНСФЛ, КодИФНСЮЛ` ...). stable.
`РаботаСАдресами.АдресПоИдентификатору(ИдентификаторАдреса, ДополнительнаяИнформацияАдреса = Неопределено) Экспорт`
— Function -> `Строка` (XML by default; JSON if `ДополнительнаяИнформацияАдреса.АдресВJSON = Истина`). stable.

**Parameters:**
- `Название` (`Строка`) - the name or full name of a Russian Federation **subject** with an abbreviation, e.g. `"Московская"` or `"Московская обл"`.
- `ОКТМО` (`Строка`) - 8 or 11 digits.
- `ИдентификаторАдреса` (`Строка`) - GUID of the FИАС address object.
- `ДополнительнаяИнформацияАдреса` (`Структура`) - `НомерДома, НомерОфиса, НомерСтроения, ПочтовыйИндекс, ДополнительнаяИнформация` (comment), `АдресВJSON` (`Булево`), `Муниципальный` (`Булево`).

**Example:**
```bsl
Код = АдресныйКлассификатор.КодРегионаПоНаименованию("Московская");   // 50
ДанныеОКТМО = АдресныйКлассификатор.СведенияПоОКМТО("45380000");     // structure

АдресJSON = РаботаСАдресами.АдресПоИдентификатору(
    "d2dc5217-...-...",
    Новый Структура("НомерДома, НомерОфиса, АдресВJSON", "5", "12", Истина));
```

**Nuances / anti-patterns:**
- ❌ `КодРегионаПоНаименованию("Москва")` will return **`77`**, not `Неопределено`: Moscow/St. Petersburg/Sevastopol are federal cities, which are also **subjects** of the Russian Federation (codes 77/78/92), and the function finds them. `Неопределено` is returned for a **settlement that is not a subject** (e.g. `"Подольск"` - a city in Moscow Oblast). For cities/districts use `СведенияПоОКМТО` or `РаботаСАдресами.СведенияОбАдресе`.
- ❌ Storing CI in your own string attributes bypassing the `КонтактнаяИнформация` tabular section - bypasses the registration log, change history, and checks. Use only `УправлениеКонтактнойИнформацией.*`.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures - via
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `УправлениеКонтактнойИнформацией.НоваяКонтактнаяИнформация(КолонкаОбъект = Истина)` -
  empty CI table with the required columns (for `УстановитьКонтактнуюИнформациюОбъекта`).
- `УправлениеКонтактнойИнформацией.ДобавитьКонтактнуюИнформацию(СсылкаИлиОбъект, ЗначениеИлиПредставление, ВидКонтактнойИнформации, Дата = Неопределено, Замещать = Истина, РаспознатьАдрес = Истина)` -
  add CI by presentation (BСП recognizes the address itself).
- `УправлениеКонтактнойИнформацией.КонтактнаяИнформацияВJSON(Знач КонтактнаяИнформация, Знач ОжидаемыйВид = Неопределено)` /
  `КонтактнаяИнформацияВXML(Знач ЗначенияПолей, Знач Представление = "", Знач ОжидаемыйВид = Неопределено)` -
  conversion between formats.
- `УправлениеКонтактнойИнформацией.ДанныеКлассификатораСтранМираПоКоду(Знач Код, Знач ТипКода = "КодСтраны")` /
  `ДанныеКлассификатораСтранМираПоНаименованию(Знач Наименование)` -
  world countries reference data (not the address classifier).
- `УправлениеКонтактнойИнформацией.СодержитКонтактнуюИнформацию(ПроверяемыйОбъект)` -
  a flag indicating whether the object has CI.
- `УправлениеКонтактнойИнформацией.ВидыКонтактнойИнформацииОбъекта(ВладелецКонтактнойИнформации, ТипКонтактнойИнформации = Неопределено)` -
  CI kinds available to the owner.
- `УправлениеКонтактнойИнформациейКлиентСервер.КонтактнаяИнформацияЗаполнена(Значение)` -
  filled-state flag (not `Неопределено`/empty).
- `АдресныйКлассификатор.СубъектыРФ()` / `КоличествоЗагруженныхРегионов()` /
  `РазрешенДоступВИнтернет()` / `НаименованиеРегионаПоКоду(КодСубъектаРФ)` -
  reference functions for regions.
- `АдресныйКлассификатор.КодыАдреса(Адрес, Источник = Неопределено)` -
  address codes (KЛАДР/ФИАС) for a CI string.

Override hooks (module `УправлениеКонтактнойИнформациейПереопределяемый`,
region `ПрограммныйИнтерфейс`): called by БСП, implemented by application code in
the module of the same name - do not call directly:
- `ПриОпределенииНастроек(Настройки)` - override of the subsystem's general CI settings.
- `ПриПолученииНаименованийВидовКонтактнойИнформации(Наименования, КодЯзыка)` - localization of CI kind names.
- `ПриНастройкеНачальногоЗаполненияЭлементов(Настройки)` - configuration of initial fill for predefined elements.
- `ПриНачальномЗаполненииЭлементов(КодыЯзыков, Элементы, ТабличныеЧасти)` - initial filling of elements during deployment.
- `ПриНачальномЗаполненииЭлемента(Объект, Данные, ДополнительныеПараметры)` - initial filling of a single element.

> ⚠️ The methods `ПриОпределенииТипаКонтактнойИнформации`,
> `ПриОпределениеКомандТипаКонтактнойИнформации` (name with "и"),
> `ПриКонвертированииКонтактнойИнформацииИзXML`,
> `ПриПроверкеСтраныПослеПоискаСтраныПоКлассификатору` and similar live in
> the `…Локализация` modules (БСП implements them for regional specifics of
> Russia/EAEU), not in `…Переопределяемый` - these are not application hooks,
> but BСП extension points for localization; do not copy them as override hooks.
