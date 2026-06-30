# External Components and OData Interface

Subsystem **ВнешниеКомпоненты** is for connecting/installing external components
based on Native API and COM on the client and server (scanners, cash registers,
data collection terminals, etc.). Subsystem **ИнтерфейсOData** is the standard
REST interface of the 1С platform; from application code it uses a single hook for
overriding dependent tables.

Direct calls to arbitrary COM objects (`Новый COMОбъект(...)`) are a **platform**
mechanism; БСП does not wrap them. This skill covers only the COM scenario that
the `ВнешниеКомпоненты` subsystem itself uses for backward compatibility with
1C 7.7 components (`ПодключитьКомпонентуИзРеестраWindows`).

## Modules

ВнешниеКомпоненты:

- `ВнешниеКомпонентыСервер` — server-side stable API: connection, information,
  used components, update. region `ПрограммныйИнтерфейс` (stable).
- `ВнешниеКомпонентыКлиент` — asynchronous connection/install/load from file
  (with user dialogs). Client, stable.
- `ВнешниеКомпонентыКлиентЛокализация` — search/update components from the portal.
- `ВнешниеКомпонентыВызовСервера` — ⚠️ `ИнформацияОКомпоненте` here is in the
  region `УстаревшиеПроцедурыИФункции` (deprecated). Use
  `ВнешниеКомпонентыСервер.ИнформацияОКомпоненте` (via `&НаСервере`).
- `ВнешниеКомпонентыСлужебный` / `…СлужебныйКлиент` / `…СлужебныйВызовСервера` /
  `…ВМоделиСервисаСлужебный` / `…ВМоделиСервисаСлужебныйКлиент` — ⚠️ internal,
  backward compatibility is not guaranteed.

ИнтерфейсOData:

- `ИнтерфейсODataПереопределяемый` — **the only stable hook** for application
  code: overriding dependent OData tables. It is a hook method (БСП calls it,
  application code implements it, it is not called directly).
- `ИнтерфейсODataСлужебный` / `ИнтерфейсODataСлужебныйПовтИсп` — ⚠️ internal:
  model generation from metadata, cache. There is almost no direct stable API for
  OData from application code - only overriding and configuration through the
  Configurator (`РольИнтерфейсаOData` role).

> **Client/server.** Client methods are **asynchronous**, via `ОписаниеОповещения`.
> Server methods are **synchronous** and return `Структура` with `Подключено`
> (`Булево`) and `ПодключаемыйМодуль` (`ОбъектВнешнейКомпоненты`).

## Scenarios

### 1. Connect a component on the client (asynchronously)

**Task:** connect a Native API/COM component on the client computer with an
explanation for the user and result handling in a notification.

**Functions:**
`ВнешниеКомпонентыКлиент.ПараметрыПодключения() Экспорт`
— Function → `Структура` (`Кэшировать, ПредложитьУстановить, ПредложитьЗагрузить, ТекстПояснения, ИдентификаторыСозданияОбъектов, Изолированно, ОбновлятьАвтоматически`), region `ПрограммныйИнтерфейс` (stable). Client.
`ВнешниеКомпонентыКлиент.ПодключитьКомпоненту(Оповещение, Идентификатор, Версия = Неопределено, ПараметрыПодключения = Неопределено) Экспорт`
— Procedure (asynchronous), region `ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `Оповещение` (`ОписаниеОповещения`) — handler `Результат` (`Структура`:
  `Подключено` (`Булево`), `ПодключаемыйМодуль` (`ОбъектВнешнейКомпоненты` /
  `ФиксированноеСоответствие` when `ИдентификаторыСозданияОбъектов`), `ОписаниеОшибки`).
- `Идентификатор` (`Строка`) — identifier of the external component object.
- `Версия` (`Строка` / `Неопределено`) — `Неопределено` = latest available.
- `ПараметрыПодключения` (`Структура` from `ПараметрыПодключения`). `ТекстПояснения` —
  why the component is needed; `ПредложитьУстановить` / `ПредложитьЗагрузить` —
  БСП will show dialogs itself in thin/web client.

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
  returns `Неопределено`; the result comes through `ОписаниеОповещения`. The client
  variant is **always asynchronous**.
- ❌ `Новый COMОбъект("InputDevice.BarcodeScanner")` outside the subsystem — will
  not pass security checks, will not update automatically, and will not work in
  service model. Only through `ПодключитьКомпоненту`.

### 2. Connect a component on the server (synchronously)

**Task:** connect a component in server code (background jobs, scheduled
processing) - synchronously, with an immediate result.

**Functions:**
`ВнешниеКомпонентыСервер.ПараметрыПодключения() Экспорт`
— Function → `Структура` (`ИдентификаторыСозданияОбъектов, Изолированно, ПолноеИмяМакета`), region `ПрограммныйИнтерфейс` (stable). Server.
`ВнешниеКомпонентыСервер.ПодключитьКомпоненту(Знач Идентификатор, Версия = Неопределено, ПараметрыПодключения = Неопределено) Экспорт`
— Function → `Структура` (`Подключено, ПодключаемыйМодуль, ОписаниеОшибки`), stable. Server.

**Parameters:**
- `Идентификатор` (`Строка`), `Версия` (`Строка` / `Неопределено`).
- `ПараметрыПодключения` (`Структура` from `ПараметрыПодключения`):
  `ИдентификаторыСозданияОбъектов` (`Массив` of `Строка`) — for components with
  multiple object creation identifiers; `Изолированно` (`Булево` /
  `Неопределено`) — `Истина` loads into a separate OS process; `ПолноеИмяМакета`
  (`Строка`) — path to the configuration common layout (`"ОбщийМакет.КомпонентаСканера"`).

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
- ❌ Passing `ПолноеИмяМакета` through `ВнешниеКомпонентыКлиент.ПараметрыПодключения()` —
  this field is **not** in the client constructor; it exists only in the server
  `ВнешниеКомпонентыСервер.ПараметрыПодключения()`.
- In the **service model** only connection of **shared** external components
  approved by the service administrator is allowed.
- `ПодключаемыйМодуль` is available until the end of the server call; it does not
  need to be disconnected explicitly.

### 3. Install a component from the ITS portal

**Task:** if the component is not installed, offer the user to install it from the
ITS portal or from a common layout.

**Functions:**
`ВнешниеКомпонентыКлиент.ПараметрыУстановки() Экспорт`
— Function → `Структура` (`ТекстПояснения, ПредложитьЗагрузить, ПредложитьУстановить`), stable. Client.
`ВнешниеКомпонентыКлиент.УстановитьКомпоненту(Оповещение, Идентификатор, Версия = Неопределено, ПараметрыУстановки = Неопределено) Экспорт`
— Procedure (asynchronous), stable. Client.

**Parameters:**
- `Оповещение` (`ОписаниеОповещения`) — `Результат` (`Структура`:
  `Установлено` (`Булево`), `ОписаниеОшибки` (`Строка`; empty if canceled by user)).
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
- ❌ Waiting for `УстановитьКомпоненту` as a return value - it is asynchronous, the
  result comes in the notification.
- In the thin/web client БСП shows installation/download dialogs itself -
  controlled by the `ПредложитьУстановить` / `ПредложитьЗагрузить` flags.

### 4. Load a component file into a catalog

**Task:** an administrator loads a component `.zip` into the `ВнешниеКомпоненты`
catalog from a local file.

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
    // Идентификатор/Версия optional - they will be determined from the file

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
- The method is for administrators (loading into the `ВнешниеКомпоненты` catalog);
  for the application scenario "connect and use", `ПодключитьКомпоненту` with
  `ПредложитьУстановить = Истина` is enough.

### 5. Get component information and list of used ones

**Task:** before connecting, check whether a component with the specified
identifier/version exists and whether it is available for editing; get the list of
configuration components.

**Functions:**
`ВнешниеКомпонентыСервер.ИнформацияОКомпоненте(Знач Идентификатор, Знач Версия = Неопределено) Экспорт`
— Function → `Структура` (`Существует, ДоступноРедактирование, Идентификатор, Версия, Наименование, ОписаниеОшибки`), region `ПрограммныйИнтерфейс` (stable). Server.
`ВнешниеКомпонентыСервер.ИспользуемыеКомпоненты(Вариант) Экспорт`
— Function → `ТаблицаЗначений` (`Идентификатор, Версия, Наименование, ДатаВерсии`), stable. Server.

**Parameters:**
- `Идентификатор` (`Строка`), `Версия` (`Строка` / `Неопределено`).
- `Вариант` (`Строка`): `"ДляОбновления"` — with the Internet update flag;
  `"ДляЗагрузки"` — components used in the configuration; `"Поставляемые"` —
  supplied components in the service model.

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
- ❌ `ВнешниеКомпонентыВызовСервера.ИнформацияОКомпоненте(...)` — ⚠️ deprecated
  (region `УстаревшиеПроцедурыИФункции`), the doc comment explicitly says:
  "Deprecated. Use `ВнешниеКомпонентыСервер.ИнформацияОКомпоненте`."
  In new code, use the server method via `&НаСервере`.
- ❌ `ВнешниеКомпонентыСлужебный.ДанныеВнешнихКомпонент("ДляОбновления")` —
  internal module. The stable equivalent is
  `ВнешниеКомпонентыСервер.ИспользуемыеКомпоненты("ДляОбновления")`.

### 6. Override dependent OData tables

**Task:** add your own objects to the list of tables whose rights are required to
write the tables included in the standard OData interface (so that related ones
are pulled in automatically when an object is exported).

**Procedure (hook):**
`ИнтерфейсODataПереопределяемый.ПриЗаполненииЗависимыхТаблицДляВыгрузкиЗагрузкиOData(Таблицы) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс`. **Override hook**: implemented in
the identically named module of the application configuration, БСП calls it when
building the model.

**Parameters:**
- `Таблицы` (`Массив` of `Строка`) — full names of metadata objects. The array is
  modified inside the procedure body (your tables are added).

**Example:**
```bsl
// In the application configuration module ИнтерфейсODataПереопределяемый
Процедура ПриЗаполненииЗависимыхТаблицДляВыгрузкиЗагрузкиOData(Таблицы) Экспорт
    // Tables not included in the OData export, but whose rights are required
    // for writing the tables included in the interface
    Таблицы.Добавить("РегистрСведений.СостоянияЗаказов");
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Call `ПриЗаполненииЗависимыхТаблицДляВыгрузкиЗагрузкиOData` directly from
  application code - it is a hook, БСП calls it itself. Only implement the body.
- ❌ `ИнтерфейсODataСлужебныйПовтИсп.ОписаниеМоделиДанныхКонфигурации()` — internal
  module; the model is rebuilt during update. The stable extension point is only
  `ИнтерфейсODataПереопределяемый`.
- Rights/roles for the standard OData interface are configured in the Configurator
  (`РольИнтерфейсаOData` role), not through program code.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures - via
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `ВнешниеКомпонентыСервер.ОбновитьВнешниеКомпоненты(ДанныеВнешнихКомпонент, АдресРезультата = Неопределено)` —
  component update (for scheduled update handlers).
- `ВнешниеКомпонентыСервер.ОписаниеПоставляемойОбщейКомпоненты()` /
  `ОбновитьОбщуюКомпоненту(ОписаниеКомпоненты)` — shared components in the service model.
- `ВнешниеКомпонентыСервер.АвтоматическиОбновляемыеКомпоненты()` — list of
  components flagged for automatic update.
- `ВнешниеКомпонентыКлиент.ПодключитьКомпонентуИзРеестраWindows(Оповещение, Идентификатор, ИдентификаторСозданияОбъекта = Неопределено)` —
  connect a COM component from the Windows registry (backward compatibility with 1C 7.7).
- `ВнешниеКомпонентыКлиент.ПараметрыПоискаДополнительнойИнформации()` — parameters
  for requesting additional component information (for `ПараметрыЗагрузки`).

⚠️ Internal modules (do not use in application code - backward compatibility is
not guaranteed): `ВнешниеКомпонентыСлужебный`, `…СлужебныйКлиент`,
`…СлужебныйВызовСервера`, `…ВМоделиСервисаСлужебный`, `…ВМоделиСервисаСлужебныйКлиент`,
`ИнтерфейсODataСлужебный`, `ИнтерфейсODataСлужебныйПовтИсп`. If you need
functionality from them, look for a stable equivalent in
`ВнешниеКомпонентыСервер` / `ВнешниеКомпонентыКлиент` or through the hook
`ИнтерфейсODataПереопределяемый`.
