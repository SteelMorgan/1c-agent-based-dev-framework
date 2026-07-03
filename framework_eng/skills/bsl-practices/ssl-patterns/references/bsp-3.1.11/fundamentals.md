# BSP - navigation

**1C Standard Subsystems Library (BSP) 3.1.11**, root subsystem
`СтандартныеПодсистемы` - 70 top-level subsystems embedded into the
application configuration as a subset. BSP is physically present in the
configuration export tree (`src/cf`): source files of common modules are in
`CommonModules/<Имя>/Ext/Module.bsl`, subsystems are in
`Subsystems/СтандартныеПодсистемы/Subsystems/<Имя>.xml`.

This file is an **L0 navigator**: the suffix system for module names, the
subsystem map, region stability logic, and the method search algorithm. It does
**not** contain workflow call scenarios (`Модуль.Метод(...)` with parameters) -
those are in `references/<подсистема>.md`. The goal is to teach the agent to
**find the needed module** and **distinguish a stable API from a service one**
in 3 steps.

## Module suffixes

BSP common modules **do not have a single prefix** (there is no `БСП_…`). The
name root comes directly from the subsystem: `ОбщегоНазначения`,
`Пользователи`, `УправлениеПечатью`, `АдресныйКлассификатор`. Separation by
execution context and mode is done through **suffixes**. Suffixes can be
combined (`ОбщегоНазначенияСлужебныйКлиентСервер` = service + client-server).

Real suffixes found in `src/cf/CommonModules/` (verified for 3.1.11):

| Suffix | Meaning | Example |
|---|---|---|
| (no suffix) | Server code | `ОбщегоНазначения`, `Пользователи`, `УправлениеПечатью` |
| `Клиент` | Client code (thin/web client). Does not call the server by itself - through `ВызовСервера` or form context | `ОбщегоНазначенияКлиент`, `ПользователиКлиент` |
| `КлиентСервер` | Safe code - works both on the server and on the client, without direct database access | `ОбщегоНазначенияКлиентСервер`, `СтроковыеФункцииКлиентСервер` |
| `ВызовСервера` | Client module marked "Server call" - **makes a server call** without form context | `ОбщегоНазначенияВызовСервера`, `БизнесПроцессыИЗадачиВызовСервера` |
| `Глобальный` | Procedures of the global context - called by a short name without a module prefix | `ОбщегоНазначенияГлобальный`, `УправлениеПечатьюГлобальный` |
| `ПовтИсп` | "Reuse return values" mode - session caching | `ОбщегоНазначенияКлиентПовтИсп`, `АдресныйКлассификаторПовтИсп` |
| `КлиентПовтИсп` | Client + caching (combined) | `ОбщегоНазначенияКлиентПовтИсп`, `ПодключаемыеКомандыКлиентПовтИсп` |
| `Служебный` | Internal subsystem API. Exported methods exist, but **backward compatibility is not guaranteed**. Use only if there is no stable alternative | `РегламентныеЗаданияСлужебный`, `УправлениеДоступомСлужебный` |
| `СлужебныйКлиент` / `СлужебныйКлиентСервер` / `СлужебныйВызовСервера` / `СлужебныйПовтИсп` | Service API + execution context (combinations) | `ОбщегоНазначенияСлужебныйКлиент`, `УправлениеДоступомСлужебныйКлиентСервер` |
| `Переопределяемый` | **Override hook**. BSP calls these methods, application code **implements** them (copies the override module into the configuration and overrides the body). NOT called directly from application code | `ОбщегоНазначенияПереопределяемый`, `ПодключаемыеКомандыПереопределяемый` |
| `КлиентПереопределяемый` | Override hook, client context | `ОбщегоНазначенияКлиентПереопределяемый`, `ЭлектроннаяПодписьКлиентПереопределяемый` |
| `ВМоделиСервиса` | Variant for the service model (multi-tenancy). Often service-level | `УправлениеДоступомСлужебныйВМоделиСервиса`, `ОбменДаннымиВМоделиСервиса` |
| `Локализация`, `РФ` | Regional/context variants | `СтроковыеФункцииКлиентСерверЛокализация`, `УправлениеПечатьюРФ` |
| `БТС` | Basic table structures - **a separate subsystem**, not a "BSP prefix" | `ОбщегоНазначенияБТС`, `ОбщегоНазначенияБТСПовтИсп` |

### ⚠️ Nonexistent modules (common mistakes by analogy)

Before using a module, **always** verify `CommonModules/<Имя>/Ext/Module.bsl`.
Names that are easy to "invent" by analogy, but do **not** exist in BSP 3.1.11
(verified in `src/cf`):

- **`ОбщегоНазначенияСлужебный`** (without a suffix) - does not exist. Service
  variants:
  `ОбщегоНазначенияСлужебныйКлиент`, `ОбщегоНазначенияСлужебныйКлиентСервер`.
- **`ФайловаяСистемаКлиентСервер`** - does not exist. There are
  `ФайловаяСистема` (server), `ФайловаяСистемаКлиент` (client),
  `ФайловаяСистемаСлужебныйКлиент`,
  `ФайловаяСистемаСлужебныйКлиентСервер`.
- **`ДлительныеОперацииСлужебный`** - does not exist. The service functions of
  "Long-running operations" are built into `ДлительныеОперации`,
  `ДлительныеОперацииКлиент`, `ДлительныеОперацииВызовСервера`.
- **`РегламентныеЗадания`** (as a common module without a suffix) - does not
  exist. Server module: `РегламентныеЗаданияСервер`. The `РегламентныеЗадания`
  subsystem exists - it is a metadata object, **not** a common module.
- **`БизнесПроцессыИЗадачи`** (without a suffix) - does not exist. Server
  module: `БизнесПроцессыИЗадачиСервер`.
- **`УправлениеДоступомКлиент`** - does not exist. The client variant exists
  only in the service layer: `УправлениеДоступомСлужебныйКлиент`. Server:
  `УправлениеДоступом` (without a suffix).
- **`МашиночитаемыеДоверенности`** (without the `ФНС` suffix) - does not exist.
  All MCD modules have the `ФНС` suffix: `МашиночитаемыеДоверенностиФНС`,
  `МашиночитаемыеДоверенностиФНСКлиент`, `...Служебный`, etc.
- **`БезопасноеХранилище`** as a common module - does not exist. This is the
  information register `БезопасноеХранилищеДанных`. Work with it only through
  `ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище` /
  `ПрочитатьДанныеИзБезопасногоХранилища` /
  `УдалитьДанныеИзБезопасногоХранилища`.

## Subsystem map

Top-level BSP 3.1.11 subsystems are `Subsystems/СтандартныеПодсистемы/Subsystems/*.xml`,
70 files (verified in `src/cf`). Subsystem -> reference skill file mapping.
Subsystems without in-demand application API from common modules are marked
"outside the skill".

| Subsystem | Reference | Note |
|---|---|---|
| БазоваяФункциональность | `base-common.md` | `ОбщегоНазначения*`, `СтроковыеФункции*`, `ФайловаяСистема*` |
| ДлительныеОперации (nested) | `longs-and-jobs.md` | background/long-running operations |
| РегламентныеЗадания | `longs-and-jobs.md` | |
| ПрефиксацияОбъектов | `prefixes.md` | |
| ОбновлениеВерсииИБ | `update.md` | |
| ОбновлениеКонфигурации | `update.md` | |
| ОбменДанными | `data-exchange.md` | |
| ЭлектроннаяПодпись | `esign-mcd.md` | |
| МашиночитаемыеДоверенности | `esign-mcd.md` | modules with the `ФНС` suffix |
| КонтактнаяИнформация | `contact-info.md` | |
| АдресныйКлассификатор | `contact-info.md` | |
| (classifiers outside address ones) | `classifiers.md` | country banks, OKEI, OKSM, etc. |
| Валюты | `currencies-banks.md` | |
| Банки | `currencies-banks.md` | |
| ГрафикиРаботы | `currencies-banks.md` | |
| КалендарныеГрафики | `currencies-banks.md` | |
| ВнешниеКомпоненты | `external-components.md` | |
| ИнтерфейсOData | `external-components.md` | |
| Пользователи | `users-access.md` | |
| УправлениеДоступом | `users-access.md` | |
| РаботаСПочтовымиСообщениями | `comms.md` | |
| ОтправкаSMS | `comms.md` | |
| ШаблоныСообщений | `comms.md` | |
| Обсуждения | `comms.md` | |
| Взаимодействия | `comms.md` | |
| БизнесПроцессыИЗадачи | `bp-tasks.md` | server module `...Сервер` |
| ЗавершениеРаботыПользователей | `admin-tools.md` | |
| УдалениеПомеченныхОбъектов | `admin-tools.md` | |
| ПрофилиБезопасности | `admin-tools.md` | |
| РезервноеКопированиеИБ | `backup.md` | |
| ОценкаПроизводительности | `perf-monitoring.md` | |
| ЦентрМониторинга | `perf-monitoring.md` | |
| КонтрольРаботыПользователей | `perf-monitoring.md` | |
| ЗащитаПерсональныхДанных | `protection-pd.md` | |
| ПодключаемыеКоманды | `commands-external.md` | |
| ДополнительныеОтчетыИОбработки | `commands-external.md` | |
| Печать | `print-reports.md` | |
| ВариантыОтчетов | `print-reports.md` | |
| ЗапретРедактированияРеквизитовОбъектов | `forms-validation.md` | |
| Свойства | `forms-validation.md` | |
| ДатыЗапретаИзменения | `forms-validation.md` | |
| РаботаСФайлами | `files-and-versions.md` | |
| ВерсионированиеОбъектов | `files-and-versions.md` | |
| ВыгрузкаОбъектовВФайлы | `files-and-versions.md` | |
| Мультиязычность | `multilang.md` | |
| ПоискИУдалениеДублей | `report-dedup.md` | |
| ГрупповоеИзменениеОбъектов | `report-dedup.md` | |
| СтруктураПодчиненности | `report-dedup.md` | |
| Анкетирование | outside the skill | |
| СклонениеПредставленийОбъектов | outside the skill | |
| ЗаметкиПользователя | outside the skill | |
| НапоминанияПользователя | outside the skill | |
| ТекущиеДела | outside the skill | |
| ГенерацияШтрихкода | outside the skill | |
| КонструкторФормул | outside the skill | |
| ПолнотекстовыйПоиск | outside the skill | |
| ЗагрузкаДанныхИзФайла | outside the skill | |
| РассылкаОтчетов | outside the skill | |
| ОтчетОДвиженияхДокумента | outside the skill | |
| КонтрольВеденияУчета | outside the skill | |
| ИнформацияПриЗапуске | outside the skill | |
| НастройкиПрограммы | outside the skill | |
| Организации | outside the skill | |
| РаботаВМоделиСервиса | outside the skill | |
| ОбращенияВТехническуюПоддержку | outside the skill | |
| ПолучениеФайловИзИнтернета | outside the skill | |
| НастройкаПорядкаЭлементов | outside the skill | |
| ПроверкаЛегальностиПолученияОбновления | outside the skill | |
| УправлениеИтогамиИАгрегатами | outside the skill | |
| УчетОригиналовПервичныхДокументов | outside the skill | |
| СервисМобильнойПодписи | outside the skill | |
| ЭлектроннаяПодписьСервисаDSS | outside the skill | separate DSS service, not the main e-signature API |

Total: **70 top-level subsystems** (verified in
`Subsystems/СтандартныеПодсистемы/Subsystems/*.xml`), of which **46 are
covered** by 23 reference files, **24 are outside the skill** (they do not have
in-demand application APIs from common modules or are rarely needed by
application code). Additionally, the map marks `ДлительныеОперации` (nested
subsystem -> `longs-and-jobs.md`) and the group of classifiers outside
`АдресныйКлассификатор` (`classifiers.md`). Subsystems with the `_Демо…` prefix
are demo-only, not for production; `БТС`/`БТСКлиент` are a separate subsystem
of basic table structures and are not included in the skill.

> Note: the authoring plan mentions the `ОбновлениеИнформационнойБазы`
> subsystem in the `update.md` group, but it is not present at the top level in
> the real `src/cf/Subsystems/.../` list - IB update is implemented through
> `ОбновлениеКонфигурации` and `ОбновлениеВерсииИБ`.

## Regions and stability

Inside a BSP common module, code is divided into **areas** by the preprocessor
directive `#Область` / `#КонецОбласти`. These are valid 1C language constructs
visible to the Configurator as structural blocks (not `//` comments).

Standard regions (names from real modules in `src/cf`, BSP 3.1.11):

| Region | Stability | Can be called from application code |
|---|---|---|
| `ПрограммныйИнтерфейс` | **stable** - backward compatibility is supported between BSP minor versions | **Yes.** This is the subsystem's main API |
| `СлужебныйПрограммныйИнтерфейс` | ⚠️ service - backward compatibility is **not guaranteed** | Only if there is no stable alternative, with a ⚠️ mark in the code |
| `СлужебныеПроцедурыИФункции` | ⚠️ internal, usually non-exported | **No** |
| `УстаревшиеПроцедурыИФункции` | ⚠️ **deprecated** - obsolete, do not use in new code | **No**, look for an alternative in `ПрограммныйИнтерфейс` |

Other regions you may encounter (`ОбработчикиСобытийПодсистемКонфигурации`,
`ДляВызоваИзДругихПодсистем`, `ВспомогательныеПроцедурыИФункции`,
`ОбновлениеИнформационнойБазы`, `ОбработчикиРегламентныхЗаданий`, etc.) are
internal/service BSP blocks, not intended for application calls.

### Override hooks

The main mechanism for overriding BSP behavior is **modules with the suffix
`Переопределяемый`** (and `КлиентПереопределяемый`). Methods in them live in
the `ПрограммныйИнтерфейс` region, but semantically they are **hooks**: BSP
calls these methods at extension points, and application code **implements**
them by copying the override module into the configuration and overriding the
body. **They are not called from application code** as `Модуль.Метод(...)`.

> ⚠️ In BSP 3.1.11, the `#Область Переопределение` region **does not exist** -
> this is a common mistake in older descriptions. The real override regions in
> `src/cf` are rare: `ПереопределениеВызовов` and
> `ПереопределениеТекстаЗапросаНабораДанных` (found in 1-2 modules). The mass
> hook mechanism is precisely the `*Переопределяемый` modules, not a separate
> `Переопределение` region.

Example (override module):

```bsl
// ОбщегоНазначенияПереопределяемый (модуль-хук, регион ПрограммныйИнтерфейс)
// БСП вызывает этот метод; прикладной код реализует тело под свои нужды.
Процедура ПриЗаполненииПараметровРаботыПользователя(Параметры) Экспорт
    // своя логика: добавить параметры сеанса
КонецПроцедуры
```

## How to find a method

The 4-step algorithm from task to signature:

1. **Determine the subsystem** using the "Subsystem map" table above (print ->
   `Печать` -> `print-reports.md`; users -> `Пользователи` ->
   `users-access.md`).
2. **Use the grep pattern** from `SKILL.md` for `src/cf/CommonModules/` - find
   the module's export methods or a method by name across all modules:
   ```bash
   # all export methods of a module
   grep -Pn "^(Функция|Процедура)\s+\w+.*Экспорт" src/cf/CommonModules/<Модуль>/Ext/Module.bsl
   # method by name across all modules
   grep -rPl "^(Функция|Процедура)\s+<Метод>\b" src/cf/CommonModules/
   ```
3. **`bsp_api.py` script** (gives signature + region name + doc comment +
   path): `python scripts/bsp_api.py method <Имя> [--module <Модуль>] --src src/cf`
   - to disambiguate the module if the method exists in several; `python
   scripts/bsp_api.py module <Модуль> --src src/cf` - all export methods of the
   module with regions.
4. **For rare/service methods** - direct grep over `src/cf/CommonModules/`
   (`СлужебныйПрограммныйИнтерфейс`, `УстаревшиеПроцедурыИФункции`). Classify
   stability by region name (see the table above).

Before using a module, **always** verify that it exists
(`CommonModules/<Имя>/Ext/Module.bsl`) - especially if the name looks
"invented" (see the "Nonexistent modules" section). Analogies in BSP often
mislead: the name root may not have a variant without a suffix
(`БизнесПроцессыИЗадачи` -> only `...Сервер`), and the service layer may exist
only with an additional suffix (`ОбщегоНазначенияСлужебныйКлиент`, but not
`...Служебный`).
