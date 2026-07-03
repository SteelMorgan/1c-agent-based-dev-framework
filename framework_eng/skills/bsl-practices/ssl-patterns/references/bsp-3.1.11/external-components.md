# External Components and OData Interface

The **ВнешниеКомпоненты** subsystem is for connecting/installing external components of the Native API and COM technology on the client and server (scanners, cash registers, data collection terminals, etc.). The **ИнтерфейсOData** subsystem is the standard REST interface of the 1С platform; from application code, it uses a single override hook for dependent tables.

A direct call to arbitrary COM objects (`Новый COMОбъект(...)`) is a **platform** mechanism, and БСП does not wrap it. This skill covers only the COM scenario that the `ВнешниеКомпоненты` subsystem itself uses for backward compatibility with 1С 7.7 components (`ПодключитьКомпонентуИзРеестраWindows`).

## Modules

ВнешниеКомпоненты:

- `ВнешниеКомпонентыСервер` — server stable API: connection, information, used components, update. region `ПрограммныйИнтерфейс` (stable).
- `ВнешниеКомпонентыКлиент` — asynchronous connection/installation/loading from file
  (with user dialogs). Client, stable.
- `ВнешниеКомпонентыКлиентЛокализация` — search/update components from the portal.
- `ВнешниеКомпонентыВызовСервера` — ⚠️ `ИнформацияОКомпоненте` here is in the region
  `УстаревшиеПроцедурыИФункции` (deprecated). Use
  `ВнешниеКомпонентыСервер.ИнформацияОКомпоненте` (via `&НаСервере`).
- `ВнешниеКомпонентыСлужебный` / `…СлужебныйКлиент` / `…СлужебныйВызовСервера` /
  `…ВМоделиСервисаСлужебный` / `…ВМоделиСервисаСлужебныйКлиент` — ⚠️ internal,
  backward compatibility is not guaranteed.

ИнтерфейсOData:

- `ИнтерфейсODataПереопределяемый` — **the only stable hook** for application
  code: overriding OData dependent tables. Method — hook (БСП calls it,
  application code implements it, it is not called directly).
- `ИнтерфейсODataСлужебный` / `ИнтерфейсODataСлужебныйПовтИсп` — ⚠️ internal:
  building a model from metadata, cache. There is almost no direct stable API for OData from
  application code — only overriding and configuration through
  Конфигуратор (role `РольИнтерфейсаOData`).

> **Client/server: common connection contract.** Client methods are **asynchronous**,
> the result arrives in `ОписаниеОповещения`; server methods are **synchronous**, the result is
> the function return value. The result structure is the same in both cases: `Подключено` (`Булево`),
> `ПодключаемыйМодуль` (`ОбъектВнешнейКомпоненты`; `ФиксированноеСоответствие` for
> `ИдентификаторыСозданияОбъектов`), `ОписаниеОшибки` (`Строка`). Common fields of
> `ПараметрыПодключения` for both constructors: `ИдентификаторыСозданияОбъектов`
> (`Массив` of `Строка` — for components with multiple object creation identifiers)
> and `Изолированно` (`Булево` / `Неопределено` — `Истина` loads into
> a separate OS process). Below in the scenarios are only specific fields.

## Scenarios

### 1. Connect a component on the client (asynchronously)

**Task:** connect a Native API/COM component on the client computer with a user explanation and result handling in the notification.

**Functions:**
`ВнешниеКомпонентыКлиент.ПараметрыПодключения() Экспорт`
— Function → `Структура` (general fields — see the contract above; client-side: `Кэшировать, ПредложитьУстановить, ПредложитьЗагрузить, ТекстПояснения, ОбновлятьАвтоматически`), region `ПрограммныйИнтерфейс` (stable). Client.
`ВнешниеКомпонентыКлиент.ПодключитьКомпоненту(Оповещение, Идентификатор, Версия = Неопределено, ПараметрыПодключения = Неопределено) Экспорт`
— Procedure (asynchronous), region `ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `Оповещение` (`ОписаниеОповещения`) — handler `Результат` (result structure — see the contract above).
- `Идентификатор` (`Строка`) — identifier of the external component object.
- `Версия` (`Строка` / `Неопределено`) — `Неопределено` = latest available.
- `ПараметрыПодключения` (`Структура` from `ПараметрыПодключения`). `ТекстПояснения` —
  why the component is needed; `ПредложитьУстановить` / `ПредложитьЗагрузить` — БСП itself
  shows dialogs in thin/web client.

**Example:**
```bsl
&НаКлиенте
Процедура ПодключитьСканер(Команда)
    Параметры = ВнешниеКомпонентыКлиент.ПараметрыПодключения();
    Параметры.ТекстПояснения =
        НСтр("ru = 'Для работы со сканером требуется внешняя компонента (NativeApi).'");

    ВнешниеКомпонентыКлиент.ПодключитьКомпоненту(
        Новый ОписаниеОповещения("ПодключитьСканерЗавершение", ЭтотОбъект),
        "InputDevice", , Параметры);
КонецПроцедуры

&НаКлиенте
Процедура ПодключитьСканерЗавершение(Результат, ДопПараметры) Экспорт
    Если Результат.Подключено Тогда
        ПодключаемыйМодуль = Результат.ПодключаемыйМодуль;   // ОбъектВнешнейКомпоненты
        // далее — вызовы методов конкретной компоненты
    ИначеЕсли НЕ ПустаяСтрока(Результат.ОписаниеОшибки) Тогда
        ПоказатьПредупреждение(, Результат.ОписаниеОшибки);
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ `Результат = ВнешниеКомпонентыКлиент.ПодключитьКомпоненту(...)` — the method
  returns `Неопределено`; the result arrives in `ОписаниеОповещения`. The client-side
  variant is **always asynchronous**.
- ❌ `Новый COMОбъект("InputDevice.BarcodeScanner")` bypassing the subsystem — it will not pass
  security checks, will not update automatically, and will not work in the service
  model. Only through `ПодключитьКомпоненту`.

### 2. Connect the component on the server (synchronously)

**Task:** connect the component in server code (background jobs, scheduled
processing) synchronously, with an immediate result.

**Functions:**
`ВнешниеКомпонентыСервер.ПараметрыПодключения() Экспорт`
— Function → `Структура` (common fields - see the contract above; server-specific: `ПолноеИмяМакета`), region `ПрограммныйИнтерфейс` (stable). Server.
`ВнешниеКомпонентыСервер.ПодключитьКомпоненту(Знач Идентификатор, Версия = Неопределено, ПараметрыПодключения = Неопределено) Экспорт`
— Function → `Структура` (result structure - see the contract above), stable. Server.

**Parameters:**
- `Идентификатор` (`Строка`), `Версия` (`Строка` / `Неопределено`).
- `ПараметрыПодключения` — server-specific field `ПолноеИмяМакета` (`Строка`) —
  path to the configuration's common layout (`"ОбщийМакет.КомпонентаСканера"`).

**Example:**
```bsl
// Server (background job)
Результат = ВнешниеКомпонентыСервер.ПодключитьКомпоненту("InputDevice", , );

Если Результат.Подключено Тогда
    Попытка
        Результат.ПодключаемыйМодуль.Подключить("COM", 0);
    Исключение
        // Write to the registration log
    КонецПопытки;
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Pass `ПолноеИмяМакета` through `ВнешниеКомпонентыКлиент.ПараметрыПодключения()` —
  this field is **not** present in the client constructor; it exists only in the server
  `ВнешниеКомпонентыСервер.ПараметрыПодключения()`.
- In the **service model**, only **shared** external components approved by the service administrator
  are allowed.
- `ПодключаемыйМодуль` is available until the end of the server call; there is no need to disconnect it explicitly.

### 3. Install the component from the ITS portal

**Task:** if the component is not installed, offer the user to install it
from the ITS portal or from a common layout.

**Functions:**
`ВнешниеКомпонентыКлиент.ПараметрыУстановки() Экспорт`
— Function → `Структура` (`ТекстПояснения, ПредложитьЗагрузить, ПредложитьУстановить`), stable. Client.
`ВнешниеКомпонентыКлиент.УстановитьКомпоненту(Оповещение, Идентификатор, Версия = Неопределено, ПараметрыУстановки = Неопределено) Экспорт`
— Procedure (asynchronous), stable. Client.

**Parameters:**
- `Оповещение` (`ОписаниеОповещения`) — `Результат` (`Структура`:
  `Установлено` (`Булево`), `ОписаниеОшибки` (`Строка`; empty if canceled by the user)).
- `Идентификатор` (`Строка`), `Версия` (`Строка` / `Неопределено`).
- `ПараметрыУстановки` (`Структура` from `ПараметрыУстановки`). `ПредложитьЗагрузить` —
  suggest downloading from the ITS website; `ПредложитьУстановить` (default `Ложь`).

**Example:**
```bsl
&НаКлиенте
Процедура УстановитьСканерПриНеобходимости()
    ПараметрыУстановки = ВнешниеКомпонентыКлиент.ПараметрыУстановки();
    ПараметрыУстановки.ТекстПояснения = НСтр("ru = 'Требуется установить компоненту сканера.'");
    ПараметрыУстановки.ПредложитьЗагрузить = Истина;

    ВнешниеКомпонентыКлиент.УстановитьКомпоненту(
        Новый ОписаниеОповещения("УстановкаЗавершение", ЭтотОбъект),
        "InputDevice", , ПараметрыУстановки);
КонецПроцедуры

&НаКлиенте
Процедура УстановкаЗавершение(Результат, ДопПараметры) Экспорт
    Если НЕ Результат.Установлено И НЕ ПустаяСтрока(Результат.ОписаниеОшибки) Тогда
        ПоказатьПредупреждение(, Результат.ОписаниеОшибки);
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Wait for the result of `УстановитьКомпоненту` as a return value — it is asynchronous,
  the result is in the notification.
- In the thin/web client, БСП itself shows the install/download dialogs —
  is controlled by the flags `ПредложитьУстановить` / `ПредложитьЗагрузить`.

### 4. Load the component file into the directory

**Task:** the administrator uploads the `.zip` component into the directory
`ВнешниеКомпоненты` from a local file.

**Functions:**
`ВнешниеКомпонентыКлиент.ПараметрыЗагрузки() Экспорт`
— Function → `Структура` (`Идентификатор, Версия, ПараметрыПоискаДополнительнойИнформации`), stable. Client.
`ВнешниеКомпонентыКлиент.ЗагрузитьКомпонентуИзФайла(Оповещение, ПараметрыЗагрузки = Неопределено) Экспорт`
— Procedure (asynchronous), stable. Client.

**Parameters:**
- `Оповещение` (`ОписаниеОповещения`) — `Результат` (`Структура`: `Загружена`
  (`Булево`), `Идентификатор`, `Версия`, `Наименование`, `ДополнительнаяИнформация`).
- `ПараметрыЗагрузки` (`Структура` from `ПараметрыЗагрузки`): `Идентификатор` /
  `Версия` — optional; `ПараметрыПоискаДополнительнойИнформации` —
  `Соответствие` for requesting additional component information.

**Example:**
```bsl
&НаКлиенте
Процедура ЗагрузитьКомпонентуИзФайла(Команда)
    Параметры = ВнешниеКомпонентыКлиент.ПараметрыЗагрузки();
    // Идентификатор/Версия optional — will be determined from the file

    ВнешниеКомпонентыКлиент.ЗагрузитьКомпонентуИзФайла(
        Новый ОписаниеОповещения("ЗагрузкаЗавершение", ЭтотОбъект), Параметры);
КонецПроцедуры

&НаКлиенте
Процедура ЗагрузкаЗавершение(Результат, ДопПараметры) Экспорт
    Если Результат.Загружена Тогда
        // Результат.Идентификатор, Результат.Версия, Результат.Наименование
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- Method for administrators (uploading into the `ВнешниеКомпоненты` directory); for
  the application scenario “connect and use”, `ПодключитьКомпоненту`
  with `ПредложитьУстановить = Истина` is sufficient.

### 5. Get component information and the list of used ones

**Task:** before connecting, check whether a component with the specified
identifier/version exists and is available for editing; get the list of
configuration components.

**Functions:**
`ВнешниеКомпонентыСервер.ИнформацияОКомпоненте(Знач Идентификатор, Знач Версия = Неопределено) Экспорт`
— Function → `Структура` (`Существует, ДоступноРедактирование, Идентификатор, Версия, Наименование, ОписаниеОшибки`), region `ПрограммныйИнтерфейс` (stable). Server.
`ВнешниеКомпонентыСервер.ИспользуемыеКомпоненты(Вариант) Экспорт`
— Function → `ТаблицаЗначений` (`Идентификатор, Версия, Наименование, ДатаВерсии`), stable. Server.

**Parameters:**
- `Идентификатор` (`Строка`), `Версия` (`Строка` / `Неопределено`).
- `Вариант` (`Строка`): `"ДляОбновления"` — with the Internet update flag;
  `"ДляЗагрузки"` — used in the configuration; `"Поставляемые"` — delivered
  components in the service model.

**Example:**
```bsl
&НаСервере
Функция КомпонентаДоступна(Идентификатор)
    Инфо = ВнешниеКомпонентыСервер.ИнформацияОКомпоненте(Идентификатор);
    Возврат Инфо.Существует;
КонецФункции

// List of components used in the configuration
Таблица = ВнешниеКомпонентыСервер.ИспользуемыеКомпоненты("ДляЗагрузки");
```

**Nuances / anti-patterns:**
- ❌ `ВнешниеКомпонентыВызовСервера.ИнформацияОКомпоненте(...)` — ⚠️ outdated
  (region `УстаревшиеПроцедурыИФункции`), the doc comment explicitly says:
  “Obsolete. `ВнешниеКомпонентыСервер.ИнформацияОКомпоненте` should be used”
  In new code — server method via `&НаСервере`.
- ❌ `ВнешниеКомпонентыСлужебный.ДанныеВнешнихКомпонент("ДляОбновления")` —  service module. Stable equivalent — `ВнешниеКомпонентыСервер.ИспользуемыеКомпоненты("ДляОбновления")`.

### 6. Override dependent OData tables

**Task:** add your own objects to the list of tables whose permissions are required to write tables included in the standard OData interface (so that when an object is exported, related ones are automatically pulled in).

**Procedure (hook):**
`ИнтерфейсODataПереопределяемый.ПриЗаполненииЗависимыхТаблицДляВыгрузкиЗагрузкиOData(Таблицы) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс`. **Override hook**: implemented in the homonymous module of the application configuration, called by БСП when building the model.

**Parameters:**
- `Таблицы` (`Массив` of `Строка`) — full metadata object names. The array is modified inside the procedure (your own tables are added).

**Example:**
```bsl
// В модуле ИнтерфейсODataПереопределяемый прикладной конфигурации
Процедура ПриЗаполненииЗависимыхТаблицДляВыгрузкиЗагрузкиOData(Таблицы) Экспорт
    // Таблицы, не входящие в выгрузку OData, но права на которые нужны
    // для записи таблиц, включённых в интерфейс
    Таблицы.Добавить("РегистрСведений.СостоянияЗаказов");
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Call `ПриЗаполненииЗависимыхТаблицДляВыгрузкиЗагрузкиOData` directly from application
  code — this is a hook, БСП calls it itself. Only implement the body.
- ❌ `ИнтерфейсODataСлужебныйПовтИсп.ОписаниеМоделиДанныхКонфигурации()` — a service
  module; the model is rebuilt on update. The stable extension point is only
  `ИнтерфейсODataПереопределяемый`.
- Permissions/roles for the standard OData interface are configured in Configurator
  (role `РольИнтерфейсаOData`), not through program code.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures — via
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `ВнешниеКомпонентыСервер.ОбновитьВнешниеКомпоненты(ДанныеВнешнихКомпонент, АдресРезультата = Неопределено)` —
  updating components (for scheduled update handlers).
- `ВнешниеКомпонентыСервер.ОписаниеПоставляемойОбщейКомпоненты()` /
  `ОбновитьОбщуюКомпоненту(ОписаниеКомпоненты)` — shared components in the service model.
- `ВнешниеКомпонентыСервер.АвтоматическиОбновляемыеКомпоненты()` — a list of
  components marked for automatic update.
- `ВнешниеКомпонентыКлиент.ПодключитьКомпонентуИзРеестраWindows(Оповещение, Идентификатор, ИдентификаторСозданияОбъекта = Неопределено)` —
  connecting a COM component from the Windows registry (backward compatibility with 1С 7.7).
- `ВнешниеКомпонентыКлиент.ПараметрыПоискаДополнительнойИнформации()` — parameters
  for requesting additional component information (for `ПараметрыЗагрузки`).

⚠️ Service modules (do not use in application code — backward compatibility is not
guaranteed): `ВнешниеКомпонентыСлужебный`, `…СлужебныйКлиент`,
`…СлужебныйВызовСервера`, `…ВМоделиСервисаСлужебный`, `…ВМоделиСервисаСлужебныйКлиент`,
`ИнтерфейсODataСлужебный`, `ИнтерфейсODataСлужебныйПовтИсп`. If you need
functionality from them, look for a stable equivalent in `ВнешниеКомпонентыСервер` /
`ВнешниеКомпонентыКлиент` or through the hook `ИнтерфейсODataПереопределяемый`.