# BSP data exchange: exchange plans, change registration, synchronization

Subsystem **ОбменДанными** (with nested `ОбменДанными.Регистрация`,
`ОбменДанными.ТранспортСообщенийОбмена`) + **СинхронизацияДанныхЧерезУниверсальныйФормат**
(EnterpriseData) + **ОбменДаннымиВМоделиСервиса** (SaaS). Covers change
registration, exchange plan nodes, collision resolution, exchange launch, data
area exchange.

> ⚠️ **Main feature.** Unlike `Печать` or `ВариантыОтчетов`, exchange
> subsystems **do not have a "thick" stable API for direct calls**. The main
> path is the **platform** `ПланыОбмена[ИмяПлана].*` + **override** of
> `*Переопределяемый` modules. The stable BSP server API (`ОбменДаннымиСервер`)
> is mainly auxiliary (nodes, version, state); the main exchange logic is
> implemented by the platform based on registered changes. Override, do not call
> helper routines directly.

## Modules

The `ОбменДанными*` family in the BSP suffix-based scheme:

- `ОбменДаннымиСервер` - server, main stable API
  (`УзлыОбменаБСП`, `УзелПланаОбменаПоКоду`, `НастройкаСинхронизацииЗавершена`,
  `ЗавершитьНастройкуСинхронизацииДанных`, `ПрефиксИнформационнойБазы`,
  `ВерсияКорреспондента`, `ЭтоАвтономноеРабочееМесто`). Main stable methods are
  listed above; the full list of stable methods is available through
  `python scripts/bsp_api.py module ОбменДаннымиСервер --src src/cf`.
- `ОбменДаннымиВызовСервера` - server call from the client without form context.
  Stable: `СброситьКэшМеханизмаРегистрацииОбъектов`. Heavy
  `ВыполнитьОбменДаннымиПоСценариюОбменаДанными` - ⚠️ `СлужебныеПроцедурыИФункции`.
- `ОбменДаннымиКлиент` - client UI for exchanges
  (`ОткрытьПанельСинхронизацииДанных`, `ОткрытьСоставОтправляемыхДанных`,
  `УдалитьНастройкуСинхронизации`).
- `ОбменДаннымиПереопределяемый` - **hooks** (implement, do not call):
  `ПолучитьПланыОбмена`, `ПриКоллизииИзмененийДанных`, `ПриВыгрузкеДанных`,
  `ПриЗагрузкеДанных`, `ПриПолученииДоступныхВерсийФормата`.
- `ОбменДаннымиСобытия` - **event handlers**; BSP connects them through event
  subscriptions (`МеханизмРегистрацииОбъектовПередЗаписьюДокумента`, etc.).
  **Do not call** - configure object registration rules in the exchange plan.
- `ОбменДаннымиXDTOСервер` - ⚠️ helper, but huge: EnterpriseData conversion
  rules. Entry point for exchange manager modules through the universal format.
- `ТрансляцияXDTOПереопределяемый` - **hook**:
  `ЗаполнитьОбработчикиТрансляцииСообщений`.
- `ОбменДаннымиВМоделиСервиса` - stable API for SaaS (exchange between data
  areas): `ПриОтключенииСинхронизацииДанных`,
  `ИзменитьПризнакНеобходимостиОбменаДаннымиВМоделиСервиса`.
- ⚠️ `ОбменДанными` (without a suffix) **does not exist** as a common module -
  a typical mistake is `ОбменДанными.ЧтоТо(...)`. Real modules have the suffixes
  above.
- ⚠️ `ОбменДаннымиПовтИсп` has a public `ПрограммныйИнтерфейс` region with
  **only deprecated** methods (`ЭтоАвтономноеРабочееМесто`,
  `НайтиУзелПланаОбменаПоКоду`) - replaced by `ОбменДаннымиСервер.*` methods
  with the same names (the rest in the module is helper code).

## Scenarios

### 1. Register / unregister object changes for a node

**Task:** programmatically register object changes (or all data) for an exchange
plan node, or unregister them.

**Functions:**
`ПланыОбмена[ИмяПлана].ЗарегистрироватьИзменения(Узел, Данные)` - platform
method of the exchange plan manager (BSP works on top of it, does not wrap it).
`ПланыОбмена[ИмяПлана].УдалитьРегистрациюИзменений(Узел, Данные = Неопределено)` -
remove registration for specific data or for the entire node selection.
`ПланыОбмена[ИмяПлана].ВыбратьИзменения(Узел, НомерСообщения, ФильтрВыборки = Неопределено)` -
selection of registered changes for sending.
Server, External connection. Region: platform API (no label needed).

**Parameters:**
- `Узел` (`ПланОбменаСсылка`) - recipient node for which changes are registered.
- `Данные` (Arbitrary) - reference/object/DB record key. For
  `УдалитьРегистрациюИзменений`, `Неопределено` means remove all registration for
  the node.
- `НомерСообщения` (Number) - exchange message number from which to select
  changes (usually `Узел.НомерОтправлено + 1`).
- `ФильтрВыборки` (Arbitrary) - platform filter for the change selection.

**Example:**
```bsl
// Регистрация изменения документа для узла (сервер)
ИмяПлана = ОбменДаннымиСервер.УзлыОбменаБСП()[0].ИмяПланаОбмена;  // колонка таблицы
ПланыОбмена[ИмяПлана].ЗарегистрироватьИзменения(УзелОбмена, ДокументОбъект);

// Снять регистрацию конкретного объекта
ПланыОбмена[ИмяПлана].УдалитьРегистрациюИзменений(УзелОбмена, ДокументОбъект);

// Снять всю регистрацию узла (следующая выгрузка начнёт «с нуля»)
ПланыОбмена[ИмяПлана].УдалитьРегистрациюИзменений(УзелОбмена);

// Выборка изменений для отправки сообщения
Выборка = ПланыОбмена[ИмяПлана].ВыбратьИзменения(УзелОбмена, УзелОбмена.НомерОтправлено + 1);
Пока Выборка.Следующий() Цикл
    // Выборка.ПолучитьОбъект(), Выборка.Метаданные() и т. п.
КонецЦикла;
```

**Nuances / anti-patterns:**
- ❌ Writing to `РегистрацияИзмененийПланаОбмена` tables manually via
  `НаборЗаписей` bypasses the platform mechanism, breaks auto-registration, and
  leads to mismatches. Only `ПланыОбмена[ИмяПлана].ЗарегистрироватьИзменения`.
- ❌ Looking for `ОбменДанными.ЗарегистрироватьИзменения` - BSP does not provide
  such a module/method. Registration is a platform API.
- Auto-registration of changes through event subscriptions makes
  `ОбменДаннымиСобытия` work automatically; manual registration is needed for
  non-standard cases (for example, forced re-registration after correcting
  data).

### 2. Attach your exchange plan to the subsystem

**Task:** register an application exchange plan in the BSP exchange subsystem so
that it appears in the synchronization panel, registration rules, and scenarios.

**Function:**
`ОбменДаннымиПереопределяемый.ПолучитьПланыОбмена(ПланыОбменаПодсистемы) Экспорт`
- Procedure, region `#Область ПрограммныйИнтерфейс` in the `*Переопределяемый`
module (hook). BSP calls it; application code **implements** the body; it is
not called directly. Server, External connection.

**Parameters:**
- `ПланыОбменаПодсистемы` (Array) - array of exchange plan metadata included in
  the subsystem. Add yours in the hook body.

**Example:**
```bsl
// В общем модуле ОбменДаннымиПереопределяемый (копируется в конфигурацию)
Процедура ПолучитьПланыОбмена(ПланыОбменаПодсистемы) Экспорт
    ПланыОбменаПодсистемы.Добавить(Метаданные.ПланыОбмена.МойПланОбмена);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Calling `ОбменДаннымиПереопределяемый.ПолучитьПланыОбмена(Массив)` from
  application code "to get the list" - the method is overridable, its body is
  empty by default, and your call returns nothing. Use
  `ОбменДаннымиСервер.УзлыОбменаБСП()` for the node list.
- The exchange plan must be correctly described in the configuration
  (composition, node attributes) - BSP checks metadata, not the database state.

### 3. Resolve change collisions during exchange

**Task:** determine which version of the object wins in a collision (main vs.
subordinate RIB node), taking the object type into account.

**Function:**
`ОбменДаннымиПереопределяемый.ПриКоллизииИзмененийДанных(Знач ЭлементДанных, ПолучениеЭлемента, Знач Отправитель, Знач ПолучениеОтГлавного) Экспорт`
- Procedure, region `#Область ПрограммныйИнтерфейс` in the `*Переопределяемый`
module (hook). BSP calls it when a collision occurs; application code
implements the logic. Server, External connection.

**Parameters:**
- `ЭлементДанных` (Arbitrary) - object/record for which the collision occurred.
- `ПолучениеЭлемента` (`ПолучениеЭлементаДанных`) - **output**: what to do with
  the incoming element. Set it in the body to `Принять` or `Игнорировать`.
- `Отправитель` (`ПланОбменаСсылка`) - sender node.
- `ПолучениеОтГлавного` (Boolean) - `Истина` if the incoming item came from the
  main node.

**Example:**
```bsl
// В ОбменДаннымиПереопределяемый
Процедура ПриКоллизииИзмененийДанных(Знач ЭлементДанных, ПолучениеЭлемента, Знач Отправитель, Знач ПолучениеОтГлавного) Экспорт
    Если ТипЗнч(ЭлементДанных) = Тип("СправочникОбъект.Контрагенты") Тогда
        // В распределёнке подчинённый узел приоритетнее своих данных
        ПолучениеЭлемента = ?(ПолучениеОтГлавного,
            ПолучениеЭлементаДанных.Игнорировать,
            ПолучениеЭлементаДанных.Принять);
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Calling `ПриКоллизииИзмененийДанных(...)` from application code - the
  method is overridable, and BSP calls it itself while parsing a message.
  Implement the logic **in the body**, do not call it.
- `ПолучениеЭлемента` is an output parameter (by reference); it must be
  **assigned**, not returned. BSP sets the default value before calling the
  hook.

### 4. Find a node, check the state, and finish synchronization setup

**Task:** find an exchange plan node by code; check whether synchronization
setup is complete; finish creating the initial image of a subordinate RIB node.

**Functions:**
`ОбменДаннымиСервер.УзлыОбменаБСП() Экспорт` - Function -> `ТаблицаЗначений`
(`УзелИнформационнойБазы` - `ПланОбменаСсылка`, `Наименование` - `Строка`,
`ИмяПланаОбмена` - `Строка`), region `#Область ПрограммныйИнтерфейс` (stable).
Server.
`ОбменДаннымиСервер.УзелПланаОбменаПоКоду(ИмяПланаОбмена, КодУзла) Экспорт` -
Function -> `ПланОбменаСсылка` / `Неопределено`, region `ПрограммныйИнтерфейс`
(stable). Server.
`ОбменДаннымиСервер.НастройкаСинхронизацииЗавершена(УзелОбмена) Экспорт` -
Function -> `Булево`, region `ПрограммныйИнтерфейс` (stable). Server.
`ОбменДаннымиСервер.ЗавершитьНастройкуСинхронизацииДанных(УзелОбмена) Экспорт` -
Procedure, region `ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ИмяПланаОбмена` (String) - exchange plan name, as in the configurator.
- `КодУзла` (String) - node code; `УзелПланаОбменаПоКоду` internally calls
  `ПланыОбмена[ИмяПлана].НайтиПоКоду(КодУзла)`.
- `УзелОбмена` (`ПланОбменаСсылка`) - exchange node.

**Example:**
```bsl
// Все узлы обменов, зарегистрированные в подсистеме
Для Каждого Узел Из ОбменДаннымиСервер.УзлыОбменаБСП() Цикл
    // ...
КонецЦикла;

// Найти узел по коду в конкретном плане
Узел = ОбменДаннымиСервер.УзелПланаОбменаПоКоду("МойПланОбмена", "001");
Если Узел <> Неопределено И ОбменДаннымиСервер.НастройкаСинхронизацииЗавершена(Узел) Тогда
    // синхронизация готова к работе
КонецЕсли;

// После создания начального образа подчинённого узла РИБ
ОбменДаннымиСервер.ЗавершитьНастройкуСинхронизацииДанных(Узел);
```

**Nuances / anti-patterns:**
- ❌ `ОбменДаннымиПовтИсп.НайтиУзелПланаОбменаПоКоду(...)` - the method is
  **deprecated**, marked `// Устарела. Следует использовать ОбменДаннымиСервер.УзелПланаОбменаПоКоду`.
  Use `ОбменДаннымиСервер`.
- `УзелПланаОбменаПоКоду` returns `Неопределено` (not an empty reference) if the
  node is not found - check `<> Неопределено`, not the empty-reference check.

### 5. Get database parameters, RIB workstation flag, and correspondent version

**Task:** get the DB prefix, autonomous workstation flag, and correspondent
version for branching exchange logic.

**Functions:**
`ОбменДаннымиСервер.ПрефиксИнформационнойБазы() Экспорт` - Function -> String,
region `ПрограммныйИнтерфейс` (stable). Server.
`ОбменДаннымиСервер.УстановитьПрефиксИнформационнойБазы(Знач Префикс) Экспорт` -
Procedure, region `ПрограммныйИнтерфейс` (stable). Server.
`ОбменДаннымиСервер.ЭтоАвтономноеРабочееМесто() Экспорт` - Function -> `Булево`,
region `ПрограммныйИнтерфейс` (stable). Server.
`ОбменДаннымиСервер.ВерсияКорреспондента(Знач Корреспондент) Экспорт` -
Function -> String (for example `"1.7"`), region `ПрограммныйИнтерфейс` (stable).
Server.

**Parameters:**
- `Префикс` (String) - new DB prefix (for `УстановитьПрефиксИнформационнойБазы`).
- `Корреспондент` (`ПланОбменаСсылка`) - correspondent node.

**Example:**
```bsl
Если ОбменДаннымиСервер.ЭтоАвтономноеРабочееМесто() Тогда
    // АРМ РИБ — ограниченная логика, нет прямого обмена с главным
    Возврат;
КонецЕсли;

Префикс = ОбменДаннымиСервер.ПрефиксИнформационнойБазы();
Версия = ОбменДаннымиСервер.ВерсияКорреспондента(УзелКорреспондента);
Если Версия >= "1.7" Тогда
    // используем расширенный состав EnterpriseData 1.7
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `ОбменДаннымиПовтИсп.ЭтоАвтономноеРабочееМесто()` - **deprecated**, replaced
  by `ОбменДаннымиСервер.ЭтоАвтономноеРабочееМесто()`.
- `ВерсияКорреспондента` returns the format version of the **correspondent**
  (the peer we exchange with), not the local database version.

### 6. Run exchange in the background and show progress

**Task:** launch a heavy exchange scenario (export/import) without blocking the
UI, with progress display and cancellation.

**Functions:**
`ОбменДаннымиВызовСервера.ВыполнитьОбменДаннымиПоСценариюОбменаДанными(Отказ, НастройкаВыполненияОбмена, НомерСтроки = Неопределено) Экспорт`
- Procedure, region `#Область СлужебныеПроцедурыИФункции` (⚠️ helper).
Server (client call without form context).
`ОбменДаннымиВызовСервера.СостояниеЗадания(Знач ИдентификаторЗадания) Экспорт` -
Function, region `СлужебныйПрограммныйИнтерфейс` (⚠️). Server.
For background execution - the long-running operations subsystem: on the server
`ДлительныеОперации.ПараметрыВыполненияПроцедуры()` (constructor) +
`ДлительныеОперации.ВыполнитьПроцедуру(ПараметрыВыполнения, ИмяПроцедуры, Параметр1, …)`
(returns a long-running operation structure), on the client -
`ДлительныеОперацииКлиент.ОжидатьЗавершение(ДлительнаяОперация, ОписаниеОповещения)`.

**Parameters:**
- `Отказ` (Boolean) - output: `Истина` aborts the exchange.
- `НастройкаВыполненияОбмена` (Arbitrary) - exchange scenario parameters (node,
  action, schedule).
- `НомерСтроки` (Number / `Неопределено`) - scenario row number in the exchange
  settings list.
- `ИдентификаторЗадания` (UniqueIdentifier) - background job identifier for
  polling status.

**Example:**
```bsl
// ❌ НЕЛЬЗЯ — синхронный вызов с формы зависает
// ОбменДаннымиВызовСервера.ВыполнитьОбменДаннымиПоСценариюОбменаДанными(Отказ, Настройка);

// ✅ Запуск через длительную операцию: серверная часть запускает фоновое задание,
// клиентская — ожидает результат через оповещение

&НаСервере
Функция ЗапуститьОбменНаСервере(Настройка)
    ПараметрыВыполнения = ДлительныеОперации.ПараметрыВыполненияПроцедуры();
    Возврат ДлительныеОперации.ВыполнитьПроцедуру(
        ПараметрыВыполнения,
        "ОбменДаннымиВызовСервера.ВыполнитьОбменДаннымиПоСценариюОбменаДанными",
        Ложь,       // Отказ
        Настройка,  // НастройкаВыполненияОбмена
        1);         // НомерСтроки
КонецФункции

&НаКлиенте
Процедура ЗапуститьОбмен(Команда)
    ДлительнаяОперация = ЗапуститьОбменНаСервере(НастройкаВыполненияОбмена);
    ДлительныеОперацииКлиент.ОжидатьЗавершение(
        ДлительнаяОперация,
        Новый ОписаниеОповещения("ОбменЗавершен", ЭтотОбъект));
КонецПроцедуры

&НаКлиенте
Процедура ОбменЗавершен(Результат, ДопПараметры) Экспорт
    // Результат.Статус = "Выполнено" / "Ошибка" / "Отменено"
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Calling `ВыполнитьОбменДаннымиПоСценариюОбменаДанными` synchronously from a
  managed form hangs the UI and prevents the user from cancelling. Use only
  `ДлительныеОперации.ВыполнитьПроцедуру` + `ОжидатьЗавершение`.
- ⚠️ The method is in `СлужебныеПроцедурыИФункции` - backward compatibility is
  not guaranteed. Pin the dependency with a comment that mentions the BSP
  version if you use it directly.

### 7. Exchange between data areas in the service model (SaaS)

**Task:** in the service model (data partitioned by areas), manage the exchange
necessity flag and react to synchronization being disabled.

**Functions:**
`ОбменДаннымиВМоделиСервиса.ИзменитьПризнакНеобходимостиОбменаДаннымиВМоделиСервиса(НеобходимоВыполнитьОбмен, ДополнительныеПараметры = Неопределено) Экспорт`
- Procedure, region `#Область СлужебныйПрограммныйИнтерфейс` (⚠️ helper).
Server.
`ОбменДаннымиВМоделиСервиса.ПриОтключенииСинхронизацииДанных(Отказ) Экспорт` -
Procedure, region `ПрограммныйИнтерфейс` (stable). Server.
`ОбменДаннымиВМоделиСервиса.ПередЗаписьюОбщихДанных(Объект, Отказ) Экспорт` -
Procedure, region `ПрограммныйИнтерфейс` (stable). Server.

**Example:**
```bsl
// При изменении состава областей данных — пересчитать признак обмена
ОбменДаннымиВМоделиСервиса.ИзменитьПризнакНеобходимостиОбменаДаннымиВМоделиСервиса(Истина);

// Перед записью общих (неразделённых) данных
Отказ = Ложь;
ОбменДаннымиВМоделиСервиса.ПередЗаписьюОбщихДанных(Объект, Отказ);
```

**Nuances / anti-patterns:**
- In the service model, exchange happens **between data areas** through common
  nodes; the usual change registration
  `ПланыОбмена[Имя].ЗарегистрироватьИзменения` works, but taking the data area
  separator into account - make sure the object belongs to the correct area.
- `ОбменДаннымиВМоделиСервисаПереопределяемый` - SaaS-specific hooks (implement,
  do not call).

## Rare methods

Stable `ОбменДаннымиСервер` methods (region `ПрограммныйИнтерфейс`), full
signatures - via `python scripts/bsp_api.py module ОбменДаннымиСервер --src src/cf`:

- `ЗавершитьСозданиеНачальногоОбраза(УзелОбмена)` - set the flag that the
  initial image of a subordinate RIB node has been created.
- `ЭтоУзелАвтономногоРабочегоМеста(Знач УзелИнформационнойБазы)` - `Булево`,
  check whether the node is a workstation node.
- `ЕстьПраваНаАдминистрированиеОбменов()` - `Булево`, check access rights for
  exchange administration.
- `ПрофильДоступаСинхронизацияДанныхСДругимиПрограммами()` - access profile for
  the synchronization role.
- `ИспользуютсяТиповыеПравила(ИмяПланаОбмена)` - `Булево`, whether the shipped
  standard conversion rules are used.
- `ПолучитьWSПрокси(СтруктураНастроек, СтрокаСообщенияОбОшибке = "",
  СообщениеПользователю = "")` - `WSПрокси`, correspondent web service. There
  are versions `ПолучитьWSПрокси_2_1_1_7` / `_3_0_1_1` / `_3_0_2_2` for format
  versions.
- `КоличествоЭлементовВТранзакцииЗагрузкиДанных()` /
  `КоличествоЭлементовВТранзакцииВыгрузкиДанных()` - exchange batch settings.

Hooks `ОбменДаннымиПереопределяемый` (implement, do not call):
- `ПриВыгрузкеДанных(СтандартнаяОбработка, Получатель, ИмяФайлаСообщения,
  ДанныеСообщения, КоличествоЭлементовВТранзакции, ИмяСобытияЖурналаРегистрации,
  КоличествоОтправленныхОбъектов)` - handle message export.
- `ПриЗагрузкеДанных(СтандартнаяОбработка, Отправитель, ИмяФайлаСообщения,
  ДанныеСообщения, КоличествоЭлементовВТранзакции, ИмяСобытияЖурналаРегистрации,
  КоличествоПолученныхОбъектов)` - handle message import.
- `ПриПолученииДоступныхВерсийФормата(ВерсииФормата)` - declare supported
  EnterpriseData versions (key = version, value = rules module).
- `ПриОпределенииПрефиксаИнформационнойБазыПоУмолчанию(Префикс)` - calculate the
  default DB prefix.

Hook `ТрансляцияXDTOПереопределяемый.ЗаполнитьОбработчикиТрансляцииСообщений(МассивОбработчиков)`
- register translator modules between EnterpriseData versions (for example
  1.6 -> 1.7).

To find the signature/region of any method -
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`.
