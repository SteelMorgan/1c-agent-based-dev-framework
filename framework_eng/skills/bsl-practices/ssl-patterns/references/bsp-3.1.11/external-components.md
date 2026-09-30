# External Components and OData Interface

The **ExternalComponents** subsystem is for connecting/installing external components
based on Native API and COM on the client and server (scanners, cash registers,
data collection terminals, etc.). The **ODataInterface** subsystem is the standard REST interface
of the 1С platform; from application code, only a single hook for overriding
dependent tables is used.

A direct call to arbitrary COM objects (`New COMObject(...)`) is a **platform**
mechanism; БСП does not wrap it. This skill covers only the COM scenario that the
`ExternalComponents` subsystem itself uses for backward compatibility with
1С 7.7 components (`ConnectComponentFromWindowsRegistry`).

## Modules

ExternalComponents:

- `ExternalComponentsServer` — server stable API: connection, information,
  used components, update. region `ProgramInterface` (stable).
- `ExternalComponentsClient` — asynchronous connection/install/load from file
  (with user dialogs). Client, stable.
- `ExternalComponentsClientLocalization` — search/update components from the portal.
- `ExternalComponentsServerCall` — ⚠️ `ComponentInformation` is here in the region
  `DeprecatedProceduresAndFunctions` (deprecated). Use
  `ExternalComponentsServer.ComponentInformation` (via `&OnServer`).
- `ExternalComponentsService` / `…ServiceClient` / `…ServiceServerCall` /
  `…ServiceModelService` / `…ServiceModelServiceClient` — ⚠️ service,
  backward compatibility is not guaranteed.

ODataInterface:

- `ODataInterfaceOverridable` — the **only stable hook** for application
  code: overriding OData dependent tables. The method is a hook (БСП calls it,
  application code implements it, it is not called directly).
- `ODataInterfaceService` / `ODataInterfaceServiceReuse` — ⚠️ service:
  building the model from metadata, cache. There is almost no direct stable API for OData from
  application code — only overriding and configuration through
  Configurator (`ODataInterfaceRole`).

> **Client/server.** Client methods are **asynchronous**, via `NotificationDescription`.
> Server methods are **synchronous**, return a `Structure` with `Connected` (`Boolean`) and
> `ConnectableModule` (`ExternalComponentObject`).

## Scenarios

### 1. Connect a component on the client (asynchronously)

**Task:** connect a Native API/COM component on the client computer with
an explanation to the user and result handling in a notification.

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
  why the component is needed; `ПредложитьУстановить` / `ПредложитьЗагрузить` — БСП itself
  will show dialogs in the thin/web client.

**Example:**
```bsl
&НаКлиенте
Процедура ПодключитьСканер(Команда)
    Параметры = ВнешниеКомпонентыКлиент.ПараметрыПодключения();
    Параметры.ТекстПояснения =
        НСтр("ru = 'Для работы со сканером требуется внешняя компонента (NativeApi).'"ույթ);

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
  returns `Неопределено`; the result comes in `ОписаниеОповещения`. The client-side
  variant is **always asynchronous**.
- ❌ `Новый COMОбъект("InputDevice.BarcodeScanner")` bypassing the subsystem — will not pass
  security checks, will not update automatically, and will not work in the service model. Only through `ПодключитьКомпоненту`.

### 2. Connect the component on the server (synchronously)

**Task:** connect the component in server code (background jobs, scheduled
processing) synchronously, with an immediate result.

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
  (`Строка`) — path to the configuration common template (`"ОбщийМакет.КомпонентаСканера"`).

**Example:**
```bsl
// Сервер (фоновое задание)
Результат = ВнешниеКомпонентыСервер.ПодключитьКомпоненту("InputDevice", , );

Если Результат.Подключено Тогда
    Попытка
        Результат.ПодключаемыйМодуль.Подключить("COM", 0);
    Исключение
        // Записать в журнал регистрации
    КонецПопытки;
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Pass `ПолноеИмяМакета` through `ВнешниеКомпонентыКлиент.ПараметрыПодключения()` —
  this field does **not** exist in the client constructor; it is only in the server
  `ВнешниеКомпонентыСервер.ПараметрыПодключения()`.
- In the **service model**, only connection of **shared** external components
  approved by the service administrator is allowed.
- `ПодключаемыйМодуль` is available until the end of the server call; there is no need to disconnect it explicitly.

### 3. Install the component from the ITS portal

**Task:** if the component is not installed, offer the user to install it
from the ITS portal or from the shared template.

**Functions:**
`ВнешниеКомпонентыКлиент.ПараметрыУстановки() Экспорт`
— Function → `Структура` (`ТекстПояснения, ПредложитьЗагрузить, ПредложитьУстановить`), stable. Client.
`ВнешниеКомпонентыКлиент.УстановитьКомпоненту(Оповещение, Идентификатор, Версия = Неопределено, ПараметрыУстановки = Неопределено) Экспорт`
— Procedure (asynchronous), stable. Client.

**Parameters:**
- `Оповещение` (`ОписаниеОповещения`) — `Результат` (`Структура`:
  `Установлено` (`Булево`), `ОписаниеОшибки` (`Строка`; empty when canceled by the user)).
- `Идентификатор` (`Строка`), `Версия` (`Строка` / `Неопределено`).
- `ПараметрыУстановки` (`Структура` from `ПараметрыУстановки`). `ПредложитьЗагрузить` —
  offer to download from the ITS site; `ПредложитьУстановить` (default `Ложь`).

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
- ❌ Waiting for `УстановитьКомпоненту` to return a value is wrong — it is asynchronous,
  the result comes in the notification.
- In the thin/web client, БСП shows the install/download dialogs itself —
  controlled by the `ПредложитьУстановить` / `ПредложитьЗагрузить` flags.

### 4. Upload a component file into the catalog

**Task:** the administrator uploads a `.zip` component into the
`ВнешниеКомпоненты` catalog from a local file.

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
    // Идентификатор/Версия необязательны — определятся из файла

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

**Notes / anti-patterns:**
- Method for administrators (loading into the `ВнешниеКомпоненты` catalog); for
  the application scenario "connect and use", `ПодключитьКомпоненту` is enough
  with `ПредложитьУстановить = Истина`.

### 5. Get component information and the list of used ones

**Task:** before connecting, check whether a component with the specified
identifier/version exists and is available for editing; get the list of
configuration components.

**Functions:**
`ВнешниеКомпонентыСервер.ИнформацияОКомпоненте(Знач Идентификатор, Знач Версия = Неопределено) Экспорт`
— Function → `Структура` (`Существует, ДоступноРедактирование, Идентификатор, Версия, Наименование, ОписаниеОшибки`), region `ПрограммныйИнтерфейс` (stable). Server.
`ВнешниеКомпонентыСервер.ИспользуемыеКомпоненты(Вариант) Экспорт`
— Function, stable. Server. When `"ДляОбновления"` / `"ДляЗагрузки"`, returns
`ТаблицаЗначений` (`Идентификатор, Версия, Наименование, ДатаВерсии,
ОбновлятьАвтоматически`); when `"Поставляемые"` — `Массив` of `Строка`
(identifiers).

**Parameters:**
- `Идентификатор` (`Строка`), `Версия` (`Строка` / `Неопределено`).
- `Вариант` (`Строка`): `"ДляОбновления"` — with the Internet update flag;
  `"ДляЗагрузки"` — used in the configuration; `"Поставляемые"` — delivered
  components in the service model (returns `Массив` of `Строка`, not a table).

**Example:**
```bsl
&НаСервере
Функция КомпонентаДоступна(Идентификатор)
    Инфо = ВнешниеКомпонентыСервер.ИнформацияОКомпоненте(Идентификатор);
    Возврат Инфо.Существует;
КонецФункции

// Список компонент, используемых в конфигурации
Таблица = ВнешниеКомпонентыСервер.ИспользуемыеКомпоненты("ДляЗагрузки");
```

**Notes / anti-patterns:**
- ❌ `ВнешниеКомпонентыВызовСервера.ИнформацияОКомпоненте(...)` — ⚠️ obsolete
  (region `УстаревшиеПроцедурыИФункции`), the doc comment explicitly states:
  "Obsolete. Use `ВнешниеКомпонентыСервер.ИнформацияОКомпоненте` instead".
  In new code, use the server method via `&НаСервере`.
- ❌ `ВнешниеКомпонентыСлужебный.ДанныеВнешнихКомпонент("ДляОбновления")` —
  service module. Stable counterpart — `ВнешниеКомпонентыСервер.ИспользуемыеКомпоненты("ДляОбновления")`.

### 6. Override dependent OData tables

**Task:** add your own objects to the list of tables whose rights are required for
writing tables included in the standard OData interface (so that when an object is
exported, related ones are automatically pulled in).

**Procedure (hook):**
`ИнтерфейсODataПереопределяемый.ПриЗаполненииЗависимыхТаблицДляВыгрузкиЗагрузкиOData(Таблицы) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс`. **Override hook**: implemented in
the identically named module of the application configuration, called by БСП when
building the model.

**Parameters:**
- `Таблицы` (`Массив` of `Строка`) — full names of metadata objects. The array
  is modified in the procedure body (your own tables are added).

**Example:**
```bsl
// В модуле ИнтерфейсODataПереопределяемый прикладной конфигурации
Процедура ПриЗаполненииЗависимыхТаблицДляВыгрузкиЗагрузкиOData(Таблицы) Экспорт
    // Таблицы, не входящие в выгрузку OData, но права на которые нужны
    // для записи таблиц, включённых в интерфейс
    Таблицы.Добавить("РегистрСведений.СостоянияЗаказов");
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Call `ПриЗаполненииЗависимыхТаблицДляВыгрузкиЗагрузкиOData` directly from
  application code — this is a hook, БСП calls it itself. Only implement the body.
- ❌ `ИнтерфейсODataСлужебныйПовтИсп.ОписаниеМоделиДанныхКонфигурации()` — service
  module; the model is rebuilt during updates. The stable extension point is
  only `ИнтерфейсODataПереопределяемый`.
- Rights/roles for the standard OData interface are configured in the Configurator
  (role `РольИнтерфейсаOData`), not through program code.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures are available via
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `ВнешниеКомпонентыСервер.ОбновитьВнешниеКомпоненты(ДанныеВнешнихКомпонент, АдресРезультата = Неопределено)` —
  component update (for scheduled update handlers).
- `ВнешниеКомпонентыСервер.ОписаниеПоставляемойОбщейКомпоненты()` /
  `ОбновитьОбщуюКомпоненту(ОписаниеКомпоненты)` — common components in the service model.
- `ВнешниеКомпонентыСервер.АвтоматическиОбновляемыеКомпоненты()` — list of
  components marked for automatic update.
- `ВнешниеКомпонентыКлиент.ПодключитьКомпонентуИзРеестраWindows(Оповещение, Идентификатор, ИдентификаторСозданияОбъекта = Неопределено)` —
  connect a COM component from the Windows registry (backward compatibility with 1С 7.7).
- `ВнешниеКомпонентыКлиент.ПараметрыПоискаДополнительнойИнформации()` — parameters
  for requesting additional information about a component (for `ПараметрыЗагрузки`).

⚠️ Service modules (do not use in application code - backward compatibility is not
guaranteed): `ВнешниеКомпонентыСлужебный`, `…СлужебныйКлиент`,
`…СлужебныйВызовСервера`, `…ВМоделиСервисаСлужебный`, `…ВМоделиСервисаСлужебныйКлиент`,
`ИнтерфейсODataСлужебный`, `ИнтерфейсODataСлужебныйПовтИсп`. If you need
functionality from them, look for a stable analogue in `ВнешниеКомпонентыСервер` /
`ВнешниеКомпонентыКлиент` or through the `ИнтерфейсODataПереопределяемый` hook.