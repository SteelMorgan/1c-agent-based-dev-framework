# BSP Classifiers (loading and updating normative-reference information)

The **РаботаСКлассификаторами** subsystem is a unified mechanism for loading and updating
classifiers from the 1C service (ITS portal) or from the cache of supplied data:
address classifier, bank classifier (BIC), exchange rates, production
calendars, countries of the world. Application code either registers its own classifier through
a hook and processes the loaded files, or checks/updates existing ones, or
reads reference data (countries, regions).

The actual **address validation** by the classifier and **region/OKTMO codes** are in
`contact-info.md` (module `АдресныйКлассификатор`); **date calculation by the
production calendar** is in `currencies-banks.md` (module `КалендарныеГрафики`);
**bank lookup by BIC** is in `currencies-banks.md` (module `РаботаСБанками`). Here is the
update mechanism and registration of new classifiers.

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
  `СлужебныйПрограммныйИнтерфейс`, not for application calls.

⚠️ Do not confuse: the countries of the world catalog and methods `ДанныеКлассификатораСтранМира*` live
in the module `УправлениеКонтактнойИнформацией` (subsystem `КонтактнаяИнформация`), not
in `РаботаСКлассификаторами` — see scenario 5.

### Which classifiers does the mechanism load

| Identifier (service string) | What it updates | Who registers it |
|---|---|---|
| `АдресныйКлассификатор` | KLADR/FIAS (regions, cities, streets) | `КалендарныеГрафики.ПриДобавленииКлассификаторов`, `РаботаСАдресами` |
| `КлассификаторБанков` | bank directory by BIC | `РаботаСБанками` |
| `КурсыВалют` | periodic exchange rates register | `РаботаСКурсамиВалют` |
| `ПроизводственныеКалендари` | production calendars of the Russian Federation | `КалендарныеГрафики.ПриДобавленииКлассификаторов` |
| `СтраныМира` | country directory | `УправлениеКонтактнойИнформацией` |

Registration is through the `ПриДобавленииКлассификаторов` hook (see scenario 1).

## Scenarios

### 1. Register your own classifier

**Task:** connect an application classifier to the БСП update mechanism so that
it is updated automatically together with the others.

**Function (hook):**
`РаботаСКлассификаторамиПереопределяемый.ПриДобавленииКлассификаторов(Классификаторы) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс`. **Override hook**: implemented in the
same-named module of the application configuration, called by БСП when collecting classifiers.

**Parameters:**
- `Классификаторы` (`Массив` of `Структура`) — each structure (from
  `РаботаСКлассификаторами.ОписаниеКлассификатора`) with keys: `Наименование`
  (String, ≤150 characters), `Идентификатор` (String, ≤50, identifier in the service),
  `ОбновлятьАвтоматически` (Boolean), `ОбщиеДанные` (Boolean — `Ложь` load into each
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
- ❌ Call `ПриДобавленииКлассификаторов` directly from application code — this is
  a hook, БСП calls it itself when collecting classifiers. Only implement the body.
- `ОписаниеКлассификатора()` returns a ready-made structure with all fields and
  default values — do not construct the structure manually.
- With `ОбщиеДанные = Ложь` in the service model, loading goes into each data area
  separately; `Истина` — once into common data.

### 2. Update lifecycle: check → download → version

One cycle, three groups of stable methods (`РаботаСКлассификаторами`, region
`ПрограммныйИнтерфейс`, Server):

**Check for updates (without downloading):**
`ДоступныеОбновленияКлассификаторов(Идентификаторы) Экспорт`
— `Идентификаторы` (`Массив` of `Строка`); returns `Структура`: `КодОшибки`
(`Строка`; empty = success; `"НеверныйЛогинИлиПароль"`, `"ПревышеноКоличествоПопыток"`
and so on.) and `ДоступныеВерсии` (`ТаблицаЗначений`: `Идентификатор`, `Версия`,
`ИдентификаторФайла`) — empty for classifiers with `СохранятьФайлВКэш = Ложь`
(their versions are not cached; use `ПолучитьФайлыКлассификаторов`).

**Download and process:**
`ОбновитьКлассификаторы(Идентификаторы) Экспорт` — the full cycle in one call
(file download + processing); returns a `Структура` with `КодОшибки` (`""`,
`"ОбновлениеНеТребуется"`, `"НеверныйЛогинИлиПароль"`, `"ПревышеноКоличествоПопыток"` ...).
`ОбработатьОбновлениеКлассификатора(Идентификатор, Версия, ИдентификаторФайла) Экспорт`
— step-by-step variant: parameters are taken from the result row `ДоступныеОбновленияКлассификаторов`.
`ПолучитьФайлыКлассификаторов(Идентификаторы) Экспорт` — current files from the service/cache.

**Version and date of the downloaded one (without contacting the service):**
`ВерсияКлассификатора(Идентификатор, ВызыватьИсключение = Ложь) Экспорт` → `Число` / `Неопределено`.
`ДатаОбновленияКлассификатора(Идентификатор, ВызыватьИсключение = Ложь) Экспорт` → `Дата` / `Неопределено`.
`УстановитьВерсиюКлассификатора(Идентификатор, Версия)` /
`УстановитьДатуОбновленияКлассификатора(Идентификатор, ДатаОбновления)` — **only**
for loading not from the service (for example, migration from an external file).
`ВызыватьИсключение` — `Истина` throws an exception if the identifier is not found;
`Ложь` (default) → `Неопределено` (= the classifier has not been loaded yet).

**Example (full cycle):**
```bsl
Идентификаторы = Новый Массив;
Идентификаторы.Добавить("АдресныйКлассификатор");
Идентификаторы.Добавить("КлассификаторБанков");

// Variant A — full cycle in one call
Результат = РаботаСКлассификаторами.ОбновитьКлассификаторы(Идентификаторы);
Если Результат.КодОшибки = "ОбновлениеНеТребуется" Тогда
    // already up to date
КонецЕсли;

// Variant B — step by step: check, then process
Доступно = РаботаСКлассификаторами.ДоступныеОбновленияКлассификаторов(Идентификаторы);
Если ПустаяСтрока(Доступно.КодОшибки) Тогда
    Для Каждого СтрокаВерсии Из Доступно.ДоступныеВерсии Цикл
        РаботаСКлассификаторами.ОбработатьОбновлениеКлассификатора(
            СтрокаВерсии.Идентификатор, СтрокаВерсии.Версия, СтрокаВерсии.ИдентификаторФайла);
    КонецЦикла;
КонецЕсли;

// Control: which version is loaded
Версия = РаботаСКлассификаторами.ВерсияКлассификатора("АдресныйКлассификатор");

// After manual data loading (not from the service) — mark the version
РаботаСКлассификаторами.УстановитьВерсиюКлассификатора("MyAppClassifier", 42);
РаботаСКлассификаторами.УстановитьДатуОбновленияКлассификатора("MyAppClassifier", ТекущаяДатаСеанса());
```

**Nuances / anti-patterns:**
- ❌ Ignore `КодОшибки` — when `"НеверныйЛогинИлиПароль"`, the data will not be loaded,
  but an exception is not thrown. Check the code and inform the user.
- Checking and downloading contact the 1С service (or the cache of supplied data) —
  network operations; in BSP scheduled jobs this is already wrapped, from application
  code / UI call it in a background job so as not to block the form.
- After successful processing, БСП itself will call `УстановитьВерсиюКлассификатора` /
  `УстановитьДатуОбновленияКлассификатора` — only set them manually when loading **not from the service**.
- If the identifier is not found, the version/date methods themselves update the data in the
  `ВерсииКлассификаторов` register — manual synchronization is not needed.
- `УстановитьДатуОбновленияКлассификатора` in the exclusive update handler
  registers information only **after** the update of the "Работа с классификаторами" subsystem
  is completed — take the order into account.

### 3. Process the loaded classifier file

**Task:** the application configuration registered its classifier — it is necessary to
process the binary file that БСП loaded from the service.

**Function (hook):**
`РаботаСКлассификаторамиПереопределяемый.ПриЗагрузкеКлассификатора(Идентификатор, Версия, Адрес, Обработан, ДополнительныеПараметры) Экспорт`
— Procedure, `ПрограммныйИнтерфейс` region. **Override hook**: implemented in the
module of the same name in the application configuration.

**Parameters:**
- `Идентификатор` (`Строка`) — identifier specified in `ПриДобавленииКлассификаторов`.
- `Версия` (`Число`) — loaded version number.
- `Адрес` (`Строка`) — address of the file's binary data in temporary storage.
- `Обработан` (`Булево`, output) — `Ложь` if there were errors during processing and the file
  needs to be uploaded again.
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
- ❌ Deleting the temporary storage `Адрес` in the handler — БСП itself saves the file in
  the cache (when `СохранятьФайлВКэш = Истина`) for later use in other
  data areas.
- `Обработан = Ложь` (default) will make БСП repeat the upload — be sure to
  set `Истина` on successful processing.
- The hook `ПриОпределенииНачальногоНомераВерсииКлассификатора(Идентификатор, НачальныйНомерВерсии)`
  sets the starting version from which the count of "updates" begins for a new
  classifier.

### 4. Launch interactive classifier update from the client

**Task:** from the form, show the user the classifier update assistant and
wait for the загрузки to complete.

**Functions:**
`РаботаСКлассификаторамиКлиент.НовыйПараметрыОбновленияКлассификаторов(Владелец = Неопределено, Идентификаторы = Неопределено, ОписаниеОповещения = Неопределено) Экспорт`
— Parameter constructor function. Client, region `ПрограммныйИнтерфейс` (stable).
`РаботаСКлассификаторамиКлиент.ОбновитьКлассификаторы(ПараметрыОбновления = Неопределено) Экспорт`
— Procedure, opens the update assistant. Client, stable.
`РаботаСКлассификаторамиКлиент.ИмяСобытияОповещенияОЗагрузки() Экспорт`
— Function → `Строка` (notification event name for completion of loading). Client, stable.

**Parameters:**
- `Владелец` (`ФормаКлиентскогоПриложения`) — owner form.
- `Идентификаторы` (`Массив` из `Строка` / `Неопределено`) — which ones to update; `Неопределено` = all registered.
- `ОписаниеОповещения` (`ОписаниеОповещения`) — will be called after the assistant closes; `Результат` = array of updated identifiers.

**Example:**
```bsl
&НаКлиенте
Процедура ОбновитьКлассификаторы(Команда)
    Параметры = РаботаСКлассификаторамиКлиент.НовыйПараметрыОбновленияКлассификаторов(
        ЭтаФорма,
        ,   // all classifiers
        Новый ОписаниеОповещения("ПослеОбновленияКлассификаторов", ЭтаФорма));
    РаботаСКлассификаторамиКлиент.ОбновитьКлассификаторы(Параметры);
КонецПроцедуры

&НаКлиенте
Процедура ПослеОбновленияКлассификаторов(ОбновленныеИдентификаторы, ДопПараметры) Экспорт
    // ОбновленныеИдентификаторы — array of strings
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Call the server `ОбновитьКлассификаторы`/`ОбработатьОбновлениеКлассификатора`
  directly from a client handler — this is server API with network calls.
  Use the client assistant `ОбновитьКлассификаторы`.
- Before calling, you can check `РаботаСКлассификаторами.ИнтерактивнаяЗагрузкаКлассификаторовДоступна()`
  (server, stable) — returns `Истина` if classifier update processing
  is allowed in the current mode.

### 5. Get world country data by code or name

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
— reference functions for the EAEU. stable.

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
    // special EAEU address mode
КонецЕсли;
```

**Nuances / anti-patterns:**
- ⚠️ The methods live in the `УправлениеКонтактнойИнформацией` module, **not** in
  `РаботаСКлассификаторами` — a common mistake is to look for them in the classifiers module.
- `ДанныеКлассификатораСтранМираПоКоду` works from the loaded country directory
  (updated via the `СтраныМира` mechanism) — if the classifier is not loaded,
  the data will be incomplete.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures via
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `РаботаСКлассификаторами.ИнтерактивнаяЗагрузкаКлассификаторовДоступна()` —
  sign of update processing availability in the current mode (server).
- `РаботаСКлассификаторами.ОписаниеКлассификатора()` — constructor for the
  classifier description structure for `ПриДобавленииКлассификаторов`.
- `РаботаСКлассификаторамиКлиент.ИмяСобытияОповещенияОЗагрузки()` — name of the
  load completion notification event (for subscription via `ПодключитьОбработчикОповещения`).

Override hook `РаботаСКлассификаторамиПереопределяемый.ПриОпределенииНастроекПользователя(Настройки)`
— user parameter setup (ITS portal authentication) for classifiers.