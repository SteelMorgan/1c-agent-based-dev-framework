# BSP Classifiers (loading and updating reference information)

The **РаботаСКлассификаторами** subsystem is a unified mechanism for loading and updating
classifiers from the 1С service (ITS portal) or from the cache of delivered data:
address classifier, bank classifier (BIC), exchange rates, production calendars, countries
of the world. Application code either registers its own classifier through a hook and
processes the loaded files, or checks/updates existing ones, or reads reference data
(countries, regions).

Address validation itself by classifier and **region codes/OKTMO** are in
`contact-info.md` (module `АдресныйКлассификатор`); **date calculation by the
production calendar** is in `currencies-banks.md` (module `КалендарныеГрафики`);
**bank lookup by BIC** is in `currencies-banks.md` (module `РаботаСБанками`). Here is
the update mechanism and registration of new classifiers.

## Modules

- `РаботаСКлассификаторами` — server stable API: update, version/date,
  files, description. region `ПрограммныйИнтерфейс` (stable).
- `РаботаСКлассификаторамиКлиент` — UI: update assistant, parameters. Client.
- `РаботаСКлассификаторамиПереопределяемый` — hooks: БСП calls, application code
  implements (not called directly).
- `РаботаСКлассификаторамиВызовСервера` / `РаботаСКлассификаторамиГлобальный` /
  `РаботаСКлассификаторамиКлиентСервер` / `РаботаСКлассификаторамиСлужебныйВМоделиСервиса` /
  `РаботаСКлассификаторамиВМоделиСервисаПереопределяемый` — ⚠️ service
  (`СлужебныйПрограммныйИнтерфейс` / `СлужебныеПроцедурыИФункции`); backward
  compatibility is not guaranteed. Methods `РаботаСКлассификаторамиВызовСервера`
  (`НастройкиОбновленияКлассификаторов`, `ЗаписатьРасписаниеОбновления`,
  `ВключитьАвтоматическоеОбновлениеКлассификаторовИзСервиса`) — all in the region
  `СлужебныйПрограммныйИнтерфейс`, not for application use.

⚠️ Do not confuse: the countries of the world catalog and methods `ДанныеКлассификатораСтранМира*` live
in the module `УправлениеКонтактнойИнформацией` (subsystem `КонтактнаяИнформация`), not
in `РаботаСКлассификаторами` — see scenario 7.

### Which classifiers the mechanism loads

| Identifier (service string) | What it updates | Who registers |
|---|---|---|
| `АдресныйКлассификатор` | KLADR/FIAS (regions, cities, streets) | `КалендарныеГрафики.ПриДобавленииКлассификаторов`, `РаботаСАдресами` |
| `КлассификаторБанков` | bank catalog by BIC | `РаботаСБанками` |
| `КурсыВалют` | periodic exchange rates information register | `РаботаСКурсамиВалют` |
| `ПроизводственныеКалендари` | production calendars of the Russian Federation | `КалендарныеГрафики.ПриДобавленииКлассификаторов` |
| `СтраныМира` | countries catalog | `УправлениеКонтактнойИнформацией` |

Registration is through the `ПриДобавленииКлассификаторов` hook (see scenario 1).

## Scenarios

### 1. Register your own classifier

**Task:** connect an application classifier to the BSP update mechanism so that
it updates automatically together with the others.

**Function (hook):**
`РаботаСКлассификаторамиПереопределяемый.ПриДобавленииКлассификаторов(Классификаторы) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс`. **Override hook**: implemented in the
application configuration module of the same name, called by БСП when collecting classifiers.

**Parameters:**
- `Классификаторы` (`Массив` of `Структура`) — each structure (from
  `РаботаСКлассификаторами.ОписаниеКлассификатора`) with keys: `Наименование`
  (String, ≤150 characters), `Идентификатор` (String, ≤50, identifier in the service),
  `ОбновлятьАвтоматически` (Boolean), `ОбщиеДанные` (Boolean — `Ложь` loads into each
  data area; only in the service model), `СохранятьФайлВКэш` (Boolean).

**Example:**
```bsl
// В модуле РаботаСКлассификаторамиПереопределяемый прикладной конфигурации
Процедура ПриДобавленииКлассификаторов(Классификаторы) Экспорт
    Описание = РаботаСКлассификаторами.ОписаниеКлассификатора();
    Описание.Наименование             = НСтр("ru = 'Свой справочник НСИ'");
    Описание.Идентификатор            = "MyAppClassifier";
    Описание.ОбновлятьАвтоматически   = Истина;
    Описание.СохранятьФайлВКэш         = Истина;
    Классификаторы.Добавить(Описание);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Calling `ПриДобавленииКлассификаторов` directly from application code — this is
  a hook, БСП calls it itself when collecting classifiers. Only implement the body.
- `ОписаниеКлассификатора()` returns a ready-made structure with all fields and
  default values — do not construct the structure manually.
- With `ОбщиеДанные = Ложь` in the service model, loading goes into each data area
  separately; `Истина` means once into shared data.

### 2. Check for available updates

**Task:** find out whether there are fresh versions of classifiers in the 1С service, without
performing the load itself.

**Function:**
`РаботаСКлассификаторами.ДоступныеОбновленияКлассификаторов(Идентификаторы) Экспорт`
— Function, region `ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Идентификаторы` (`Массив` of `Строка`) — classifier identifiers in the service.

**Return value:** `Структура` with result: `КодОшибки` (String; empty =
success; `"НеверныйЛогинИлиПароль"`, `"ПревышеноКоличествоПопыток"` etc.) and
`ДоступныеВерсии` (`ТаблицаЗначений`) — empty if `СохранятьФайлВКэш = Ложь` for the
classifier.

**Example:**
```bsl
Идентификаторы = Новый Массив;
Идентификаторы.Добавить("АдресныйКлассификатор");
Идентификаторы.Добавить("КлассификаторБанков");

Результат = РаботаСКлассификаторами.ДоступныеОбновленияКлассификаторов(Идентификаторы);
Если ПустаяСтрока(Результат.КодОшибки) Тогда
    Для Каждого СтрокаВерсии Из Результат.ДоступныеВерсии Цикл
        // СтрокаВерсии.Идентификатор, .Версия, .ИдентификаторФайла
    КонецЦикла;
КонецЕсли;
```

**Nuances / anti-patterns:**
- The method accesses the 1С service (or the cache of delivered data) — run it in a
  background job if you call it from the UI, so you do not block the form.
- `ДоступныеВерсии` will be empty for classifiers with `СохранятьФайлВКэш = Ложь` —
  their current versions are not cached; use `ПолучитьФайлыКлассификаторов`.

### 3. Load a classifier update

**Task:** perform loading and processing of a classifier update (in one call or step by step
— check + process).

**Functions:**
`РаботаСКлассификаторами.ОбновитьКлассификаторы(Идентификаторы) Экспорт`
— Function, region `ПрограммныйИнтерфейс` (stable). Full cycle: file download +
  processing. Returns `Структура` with `КодОшибки` (`""`, `"ОбновлениеНеТребуется"`,
  `"НеверныйЛогинИлиПароль"`, `"ПревышеноКоличествоПопыток"` …).
`РаботаСКлассификаторами.ОбработатьОбновлениеКлассификатора(Идентификатор, Версия, ИдентификаторФайла) Экспорт`
— Function, region `ПрограммныйИнтерфейс` (stable). Download of a specific file +
  processing; use together with `ДоступныеОбновленияКлассификаторов`.
`РаботаСКлассификаторами.ПолучитьФайлыКлассификаторов(Идентификаторы) Экспорт`
— Function, region `ПрограммныйИнтерфейс` (stable). Current files from the service/cache.

**Parameters:**
- `Идентификаторы` (`Массив` of `Строка`).
- `Идентификатор` (`Строка`), `Версия` (`Строка`), `ИдентификаторФайла` (`Строка`) —
  from the result row of `ДоступныеОбновленияКлассификаторов`.

**Example:**
```bsl
// Вариант А — полный цикл одним вызовом
Результат = РаботаСКлассификаторами.ОбновитьКлассификаторы(Идентификаторы);
Если Результат.КодОшибки = "ОбновлениеНеТребуется" Тогда
    // already up to date
КонецЕсли;

// Вариант Б — пошагово: сначала проверить, потом обработать
Доступно = РаботаСКлассификаторами.ДоступныеОбновленияКлассификаторов(Идентификаторы);
Если ПустаяСтрока(Доступно.КодОшибки) Тогда
    Для Каждого СтрокаВерсии Из Доступно.ДоступныеВерсии Цикл
        РаботаСКлассификаторами.ОбработатьОбновлениеКлассификатора(
            СтрокаВерсии.Идентификатор, СтрокаВерсии.Версия, СтрокаВерсии.ИдентификаторФайла);
    КонецЦикла;
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Ignoring `КодОшибки` — with `"НеверныйЛогинИлиПароль"` the data will not load,
  but no exception is thrown. Check the code and notify the user.
- Loading from the service is a network operation; in BSP scheduled jobs it is already
  wrapped. From application code, call it in a background job if there is a lot of data.
- After successful processing, БСП will itself call `УстановитьВерсиюКлассификатора` /
  `УстановитьДатуОбновленияКлассификатора` — you only need to do this manually when loading
  **not from the service** (for example, data migration from an external file).

### 4. Find out the version and date of a loaded classifier

**Task:** check which classifier version is loaded and when, without contacting the
service.

**Functions:**
`РаботаСКлассификаторами.ВерсияКлассификатора(Идентификатор, ВызыватьИсключение = Ложь) Экспорт`
— Function → `Число` / `Неопределено`, region `ПрограммныйИнтерфейс` (stable).
`РаботаСКлассификаторами.ДатаОбновленияКлассификатора(Идентификатор, ВызыватьИсключение = Ложь) Экспорт`
— Function → `Дата` / `Неопределено`, region `ПрограммныйИнтерфейс` (stable).
`РаботаСКлассификаторами.УстановитьВерсиюКлассификатора(Идентификатор, Версия) Экспорт`
`РаботаСКлассификаторами.УстановитьДатуОбновленияКлассификатора(Идентификатор, ДатаОбновления) Экспорт`
— Procedures (stable). Only for loading not from the service.

**Parameters:**
- `Идентификатор` (`Строка`).
- `ВызыватьИсключение` (`Булево`) — `Истина` throws an exception if the
  identifier is not found; `Ложь` (default) — returns `Неопределено`.

**Example:**
```bsl
Версия = РаботаСКлассификаторами.ВерсияКлассификатора("АдресныйКлассификатор");
ДатаОбновления = РаботаСКлассификаторами.ДатаОбновленияКлассификатора("АдресныйКлассификатор");

Если Версия = Неопределено Тогда
    // classifier has not been loaded yet
КонецЕсли;

// After manual data loading (not from the service) — mark the version
РаботаСКлассификаторами.УстановитьВерсиюКлассификатора("MyAppClassifier", 42);
РаботаСКлассификаторами.УстановитьДатуОбновленияКлассификатора("MyAppClassifier", ТекущаяДатаСеанса());
```

**Nuances / anti-patterns:**
- If the identifier is not found, the methods themselves perform an update of the
  `ВерсииКлассификаторов` register data — no manual synchronization is required.
- `УстановитьДатуОбновленияКлассификатора` in the exclusive update handler
  records information only **after** the update of the “Working with classifiers” subsystem
  is completed — keep the order in mind.

### 5. Process a loaded classifier file

**Task:** the application configuration registered its own classifier — it needs to
process the binary file that БСП loaded from the service.

**Function (hook):**
`РаботаСКлассификаторамиПереопределяемый.ПриЗагрузкеКлассификатора(Идентификатор, Версия, Адрес, Обработан, ДополнительныеПараметры) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс`. **Override hook**: implemented in the
application configuration module of the same name.

**Parameters:**
- `Идентификатор` (`Строка`) — the identifier specified in `ПриДобавленииКлассификаторов`.
- `Версия` (`Число`) — loaded version number.
- `Адрес` (`Строка`) — address of the binary file data in temporary storage.
- `Обработан` (`Булево`, output) — `Ложь` if there were errors during processing and the file
  must be loaded again.
- `ДополнительныеПараметры` (`Структура`) — service parameters.

**Example:**
```bsl
// В модуле РаботаСКлассификаторамиПереопределяемый прикладной конфигурации
Процедура ПриЗагрузкеКлассификатора(Идентификатор, Версия, Адрес, Обработан, ДополнительныеПараметры) Экспорт
    Если Идентификатор = "MyAppClassifier" Тогда
        ДвоичныеДанные = ПолучитьИзВременногоХранилища(Адрес);
        // ...разбор файла и заполнение своего справочника...
        Обработан = Истина;   // файл обработан успешно
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Deleting the temporary storage `Адрес` in the handler — БСП stores the file itself in
  the cache (when `СохранятьФайлВКэш = Истина`) for later use in other data areas.
- `Обработан = Ложь` (default) will make БСП retry the load — always set `Истина` when processing succeeds.
- The `ПриОпределенииНачальногоНомераВерсииКлассификатора(Идентификатор, НачальныйНомерВерсии)`
  hook sets the starting version from which the count of “updates” begins for a new
  classifier.

### 6. Start interactive classifier updates from the client

**Task:** from a form, show the user the classifier update assistant and wait for the
download to finish.

**Functions:**
`РаботаСКлассификаторамиКлиент.НовыйПараметрыОбновленияКлассификаторов(Владелец = Неопределено, Идентификаторы = Неопределено, ОписаниеОповещения = Неопределено) Экспорт`
— Parameter constructor function. Client, region `ПрограммныйИнтерфейс` (stable).
`РаботаСКлассификаторамиКлиент.ОбновитьКлассификаторы(ПараметрыОбновления = Неопределено) Экспорт`
— Procedure, opens the update assistant. Client, stable.
`РаботаСКлассификаторамиКлиент.ИмяСобытияОповещенияОЗагрузки() Экспорт`
— Function → `Строка` (name of the notification event for download completion). Client, stable.

**Parameters:**
- `Владелец` (`ФормаКлиентскогоПриложения`) — owner form.
- `Идентификаторы` (`Массив` of `Строка` / `Неопределено`) — which ones to update; `Неопределено` = all registered.
- `ОписаниеОповещения` (`ОписаниеОповещения`) — called after the assistant closes; `Результат` = array of updated identifiers.

**Example:**
```bsl
&НаКлиенте
Процедура ОбновитьКлассификаторы(Команда)
    Параметры = РаботаСКлассификаторамиКлиент.НовыйПараметрыОбновленияКлассификаторов(
        ЭтаФорма,
        ,   // все классификаторы
        Новый ОписаниеОповещения("ПослеОбновленияКлассификаторов", ЭтаФорма));
    РаботаСКлассификаторамиКлиент.ОбновитьКлассификаторы(Параметры);
КонецПроцедуры

&НаКлиенте
Процедура ПослеОбновленияКлассификаторов(ОбновленныеИдентификаторы, ДопПараметры) Экспорт
    // ОбновленныеИдентификаторы — массив строк
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Calling the server `ОбновитьКлассификаторы`/`ОбработатьОбновлениеКлассификатора`
  directly from a client handler — this is server API with network calls.
  Use the client assistant `ОбновитьКлассификаторы`.
- Before calling, you can check `РаботаСКлассификаторами.ИнтерактивнаяЗагрузкаКлассификаторовДоступна()`
  (server, stable) — returns `Истина` if classifier update processing is allowed in the current mode.

### 7. Get countries of the world data by code or name

**Task:** by numeric/alphabetic code or name, get the country structure
(name, Alpha-2/Alpha-3 codes, EAEU membership flag).

**Functions:**
`УправлениеКонтактнойИнформацией.ДанныеКлассификатораСтранМираПоКоду(Знач Код, Знач ТипКода = "КодСтраны") Экспорт`
— Function → `Структура` (`Наименование, Код, НаименованиеПолное, КодАльфа2, КодАльфа3, УчастникЕАЭС`), region `ПрограммныйИнтерфейс` (stable). Server.
`УправлениеКонтактнойИнформацией.ДанныеКлассификатораСтранМираПоНаименованию(Знач Наименование) Экспорт`
— Function → `Структура`. stable.
`УправлениеКонтактнойИнформацией.СтранаМираПоКодуИлиНаименованию(КодИлиНаименование, ДанныеЗаполнения = Неопределено) Экспорт`
— Function → country reference/data. stable.
`УправлениеКонтактнойИнформацией.СтраныУчастникиЕАЭС() Экспорт` / `ЭтоСтранаУчастникЕАЭС(Страна) Экспорт`
— reference functions for EAEU. stable.

**Parameters:**
- `Код` (`Строка` / `Число`) — country code.
- `ТипКода` (`Строка`) — `"КодСтраны"` (default), `"Альфа2"`, `"Альфа3"`.
- `Наименование` (`Строка`) — country name.

**Example:**
```bsl
Россия  = УправлениеКонтактнойИнформацией.ДанныеКлассификатораСтранМираПоКоду("643");
Беларусь = УправлениеКонтактнойИнформацией.ДанныеКлассификатораСтранМираПоКоду("BY", "Альфа2");
// Россия.Наименование, Россия.КодАльфа2, Россия.КодАльфа3, Россия.УчастникЕАЭС

Если УправлениеКонтактнойИнформацией.ЭтоСтранаУчастникЕАЭС(Россия) Тогда
    // особый режим адреса ЕАЭС
КонецЕсли;
```

**Nuances / anti-patterns:**
- ⚠️ The methods live in the `УправлениеКонтактнойИнформацией` module, **not** in
  `РаботаСКлассификаторами` — a typical mistake is to look for them in the classifiers module.
- `ДанныеКлассификатораСтранМираПоКоду` works from the loaded countries catalog
  (updated through the `СтраныМира` mechanism) — if the classifier is not loaded,
  the data will be incomplete.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures — via
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `РаботаСКлассификаторами.ИнтерактивнаяЗагрузкаКлассификаторовДоступна()` —
  indicator of whether update processing is available in the current mode (server).
- `РаботаСКлассификаторами.ОписаниеКлассификатора()` — constructor for the
  classifier description structure for `ПриДобавленииКлассификаторов`.
- `РаботаСКлассификаторамиКлиент.ИмяСобытияОповещенияОЗагрузки()` — name of the
  notification event for download completion (for subscription through `ПодключитьОбработчикОповещения`).

Override hook `РаботаСКлассификаторамиПереопределяемый.ПриОпределенииНастроекПользователя(Настройки)`
— user settings parameters (ITS portal authentication) for classifiers.
