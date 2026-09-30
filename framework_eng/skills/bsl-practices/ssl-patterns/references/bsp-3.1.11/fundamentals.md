# БСП — Navigation

**1C Standard Subsystems Library (БСП) 3.1.11**, root subsystem
`СтандартныеПодсистемы` - 70 top-level subsystems embedded into the
application configuration as a subset. БСП is physically present in the
configuration export tree (`src/cf`): shared module sources are in
`CommonModules/<name>/Ext/Module.bsl`, subsystems are in
`Subsystems/СтандартныеПодсистемы/Subsystems/<name>.xml`.

This file is an **L0 navigator**: the module-name suffix system, the subsystem
map, region stability logic, and the method lookup algorithm. There are **no**
workflow invocation scenarios here (`Module.Method(...)` with parameters) - they
are in `references/<subsystem>.md`. The goal is to teach the agent how to
**find the needed module** and **distinguish stable API from internal API** in 3
steps.

## Module suffixes

БСП shared modules **do not have a single prefix** (there is no `БСП_…`). The
name root comes directly from the subsystem: `ОбщегоНазначения`,
`Пользователи`, `УправлениеПечатью`, `АдресныйКлассификатор`. Separation by
execution context and mode is done through **suffixes**. Suffixes can be
combined (`ОбщегоНазначенияСлужебныйКлиентСервер` = internal + client-server).

Real suffixes found in `src/cf/CommonModules/` (verified for 3.1.11):

| Suffix | Meaning | Example |
|---|---|---|
| (no suffix) | Server code | `ОбщегоНазначения`, `Пользователи`, `УправлениеПечатью` |
| `Клиент` | Client code (thin/web client). Does not call the server by itself - via `ВызовСервера` or form context | `ОбщегоНазначенияКлиент`, `ПользователиКлиент` |
| `КлиентСервер` | Safe code - works both on server and client, without direct DB access | `ОбщегоНазначенияКлиентСервер`, `СтроковыеФункцииКлиентСервер` |
| `ВызовСервера` | Client module marked "Server call" - **performs a server call** without form context | `ОбщегоНазначенияВызовСервера`, `БизнесПроцессыИЗадачиВызовСервера` |
| `Глобальный` | Global context procedures - called by short name without module prefix | `ОбщегоНазначенияГлобальный`, `УправлениеПечатьюГлобальный` |
| `ПовтИсп` | "Return value reuse" mode - session caching | `ОбщегоНазначенияКлиентПовтИсп`, `АдресныйКлассификаторПовтИсп` |
| `КлиентПовтИсп` | Client + caching (combination) | `ОбщегоНазначенияКлиентПовтИсп`, `ПодключаемыеКомандыКлиентПовтИсп` |
| `Служебный` | Internal subsystem API. Exported methods exist, but **backward compatibility is not guaranteed**. Use only if there is no stable alternative | `РегламентныеЗаданияСлужебный`, `УправлениеДоступомСлужебный` |
| `СлужебныйКлиент` / `СлужебныйКлиентСервер` / `СлужебныйВызовСервера` / `СлужебныйПовтИсп` | Internal API + execution context (combinations) | `ОбщегоНазначенияСлужебныйКлиент`, `УправлениеДоступомСлужебныйКлиентСервер` |
| `Переопределяемый` | **Override hook**. БСП calls these methods, application code **implements** them (copies the override module into the configuration and overrides the body). NOT called directly from application code | `ОбщегоНазначенияПереопределяемый`, `ПодключаемыеКомандыПереопределяемый` |
| `КлиентПереопределяемый` | Override hook, client context | `ОбщегоНазначенияКлиентПереопределяемый`, `ЭлектроннаяПодписьКлиентПереопределяемый` |
| `ВМоделиСервиса` | Variant for the service model (multitenancy). Often internal | `УправлениеДоступомСлужебныйВМоделиСервиса`, `ОбменДаннымиВМоделиСервиса` |
| `Локализация`, `РФ` | Regional/context variants | `СтроковыеФункцииКлиентСерверЛокализация`, `УправлениеПечатьюРФ` |
| `БТС` | Basic tabular structures - **separate subsystem**, not a "БСП prefix" | `ОбщегоНазначенияБТС`, `ОбщегоНазначенияБТСПовтИсп` |

### ⚠️ Nonexistent modules (typical mistakes by analogy)

Before using a module, **always** check `CommonModules/<name>/Ext/Module.bsl`.
Names that are easy to "invent" by analogy, but that do **not** exist in БСП
3.1.11 (verified by `src/cf`):

- **`ОбщегоНазначенияСлужебный`** (without suffix) - does not exist. Internal
  variants:
  `ОбщегоНазначенияСлужебныйКлиент`, `ОбщегоНазначенияСлужебныйКлиентСервер`.
- **`ФайловаяСистемаКлиентСервер`** - does not exist. There are
  `ФайловаяСистема` (server), `ФайловаяСистемаКлиент` (client),
  `ФайловаяСистемаСлужебныйКлиент`, `ФайловаяСистемаСлужебныйКлиентСервер`.
- **`ДлительныеОперацииСлужебный`** - does not exist. Internal "long-running
  operation" functions are built into `ДлительныеОперации`,
  `ДлительныеОперацииКлиент`, `ДлительныеОперацииВызовСервера`.
- **`РегламентныеЗадания`** (as a shared module without suffix) - does not
  exist. Server module: `РегламентныеЗаданияСервер`. The `РегламентныеЗадания`
  subsystem exists - it is a metadata object, **not** a shared module.
- **`БизнесПроцессыИЗадачи`** (without suffix) - does not exist. Server module:
  `БизнесПроцессыИЗадачиСервер`.
- **`УправлениеДоступомКлиент`** - does not exist. The client variant exists
  only in the internal layer: `УправлениеДоступомСлужебныйКлиент`. Server:
  `УправлениеДоступом` (no suffix).
- **`МашиночитаемыеДоверенности`** (without the `ФНС` suffix) - does not exist.
  All MCD modules have the `ФНС` suffix: `МашиночитаемыеДоверенностиФНС`,
  `МашиночитаемыеДоверенностиФНСКлиент`, `...Служебный`, etc.
- **`БезопасноеХранилище`** as a shared module - does not exist. This is the
  information register `БезопасноеХранилищеДанных`. Work with it only through
  `ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище` /
  `ПрочитатьДанныеИзБезопасногоХранилища` /
  `УдалитьДанныеИзБезопасногоХранилища`.

## Subsystem map

The top-level БСП 3.1.11 subsystems are `Subsystems/СтандартныеПодсистемы/Subsystems/*.xml`,
70 files (verified by `src/cf`). Subsystem -> skill reference file mapping.
Subsystems without a sought-after application API from shared modules are marked
"out of scope".

| Subsystem | Reference | Note |
|---|---|---|
| БазоваяФункциональность | `base-common.md` | `ОбщегоНазначения*`, `СтроковыеФункции*`, `ФайловаяСистема*` |
| ДлительныеОперации (shared modules, БазоваяФункциональность subsystem) | `longs-and-jobs.md` | background/long-running operations |
| РегламентныеЗадания | `longs-and-jobs.md` | |
| ПрефиксацияОбъектов | `prefixes.md` | |
| ОбновлениеВерсииИБ | `update.md` | |
| ОбновлениеКонфигурации | `update.md` | |
| ОбменДанными | `data-exchange.md` | |
| ЭлектроннаяПодпись | `esign-mcd.md` | |
| МашиночитаемыеДоверенности | `esign-mcd.md` | modules with the `ФНС` suffix |
| КонтактнаяИнформация | `contact-info.md` | |
| АдресныйКлассификатор | `contact-info.md` | |
| (classifiers outside addresses) | `classifiers.md` | country banks, OKEI, OKSM, etc. |
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
| Анкетирование | out of scope | |
| СклонениеПредставленийОбъектов | out of scope | |
| ЗаметкиПользователя | out of scope | |
| НапоминанияПользователя | out of scope | |
| ТекущиеДела | out of scope | |
| ГенерацияШтрихкода | out of scope | |
| КонструкторФормул | out of scope | |
| ПолнотекстовыйПоиск | out of scope | |
| ЗагрузкаДанныхИзФайла | out of scope | |
| РассылкаОтчетов | out of scope | |
| ОтчетОДвиженияхДокумента | out of scope | |
| КонтрольВеденияУчета | out of scope | |
| ИнформацияПриЗапуске | out of scope | |
| НастройкиПрограммы | out of scope | |
| Организации | out of scope | |
| РаботаВМоделиСервиса | out of scope | |
| ОбращенияВТехническуюПоддержку | out of scope | |
| ПолучениеФайловИзИнтернета | out of scope | |
| НастройкаПорядкаЭлементов | out of scope | |
| ПроверкаЛегальностиПолученияОбновления | out of scope | |
| УправлениеИтогамиИАгрегатами | out of scope | |
| УчетОригиналовПервичныхДокументов | out of scope | |
| СервисМобильнойПодписи | out of scope | |
| ЭлектроннаяПодписьСервисаDSS | out of scope | separate DSS service, not the main ЭП API |

In total: **70 top-level subsystems** (verified by
`Subsystems/СтандартныеПодсистемы/Subsystems/*.xml`), of which **46 are covered**
by 23 reference files, **24 are out of scope** (they do not expose a sought-after
application API from shared modules or are rarely needed by application code).
Additionally, the map marks `ДлительныеОперации*` modules (they belong to the
`БазоваяФункциональность` subsystem, do not form a separate subsystem ->
`longs-and-jobs.md`) and the classifier group outside `АдресныйКлассификатор`
(`classifiers.md`). Subsystems with the `_Демо…` prefix are demonstration-only,
not for production; `БТС`/`БТСКлиент` is a separate subsystem of basic tabular
structures and is not included in the skill.

> Note: the authoring plan mentions the `ОбновлениеИнформационнойБазы`
> subsystem in the `update.md` group, but it is not present at the top level in
> the real `src/cf/Subsystems/.../` list - information base update is
> implemented through `ОбновлениеКонфигурации` and `ОбновлениеВерсииИБ`.

## Regions and stability

Inside a БСП shared module, code is divided into **regions** by the preprocessor
directive `#Область` / `#КонецОбласти`. These are valid 1C language constructs,
visible to the Configurator as structural blocks (not `//` comments).

Standard regions (names from actual modules in `src/cf`, БСП 3.1.11):

| Region | Stability | Can it be called from application code |
|---|---|---|
| `ПрограммныйИнтерфейс` | **stable** - backward compatibility is preserved between minor БСП versions | **Yes.** This is the subsystem's main API |
| `СлужебныйПрограммныйИнтерфейс` | ⚠️ internal - backward compatibility is **not guaranteed** | Only if there is no stable alternative, with a ⚠️ note in code |
| `СлужебныеПроцедурыИФункции` | ⚠️ internal, usually non-exported | **No** |
| `УстаревшиеПроцедурыИФункции` | ⚠️ **deprecated** - obsolete, do not use in new code | **No**, look for an alternative in `ПрограммныйИнтерфейс` |

Other commonly seen regions (`ОбработчикиСобытийПодсистемКонфигурации`,
`ДляВызоваИзДругихПодсистем`, `ВспомогательныеПроцедурыИФункции`,
`ОбновлениеИнформационнойБазы`, `ОбработчикиРегламентныхЗаданий`, etc.) are
internal/internal-use БСП blocks not intended for application calls.

### Override hooks

The main mechanism for overriding БСП behavior is **modules with the suffix
`Переопределяемый`** (and `КлиентПереопределяемый`). Methods in them live in the
`ПрограммныйИнтерфейс` region, but semantically they are **hooks**: БСП itself
calls these methods at extension points, and application code **implements** them
by copying the override module into the configuration and overriding the body.
**They are not called from application code** as `Module.Method(...)`.

> ⚠️ In БСП 3.1.11 the region `#Область Переопределение` **does not exist** -
> this is a common mistake in older descriptions. The real override regions in
> `src/cf` are isolated: `ПереопределениеВызовов` and
> `ПереопределениеТекстаЗапросаНабораДанных` (found in 1-2 modules). The bulk
> hook mechanism is the `*Переопределяемый` modules, not a separate
> `Переопределение` region.

Example (override module):

```bsl
// ОбщегоНазначенияПереопределяемый (модуль-хук, регион ПрограммныйИнтерфейс)
// БСП вызывает этот метод; прикладной код реализует тело под свои нужды.
Процедура ПриДобавленииПараметровРаботыКлиента(Параметры) Экспорт
    // своя логика: добавить параметры сеанса
КонецПроцедуры
```

## How to find a method

A 4-step algorithm - from task to signature:

1. **Determine the subsystem** using the "Subsystem map" table above (printing ->
   `Печать` -> `print-reports.md`; users -> `Пользователи` ->
   `users-access.md`).
2. **Grep template** from `SKILL.md` over `src/cf/CommonModules/` - find the
   module's exported methods or a method by name across all modules:
   ```bash
   # all exported methods of a module
   grep -Pn "^(Функция|Процедура)\s+\w+.*Экспорт" src/cf/CommonModules/<Модуль>/Ext/Module.bsl
   # method by name across all modules
   grep -rPl "^(Функция|Процедура)\s+<Метод>\b" src/cf/CommonModules/
   ```
3. **`bsp_api.py` script** (gives signature + region name + doc comment +
   path): `python scripts/bsp_api.py method <name> [--module <module>] --src src/cf`
   - to disambiguate the module if the method exists in several; `python scripts/bsp_api.py
   module <module> --src src/cf` - all exported methods of the module with regions.
4. **For rare/internal methods** - direct grep over `src/cf/CommonModules/`
   (`СлужебныйПрограммныйИнтерфейс`, `УстаревшиеПроцедурыИФункции` regions).
   Classify stability by the region name (see the table above).

Before using a module, **always** check that it exists
(`CommonModules/<name>/Ext/Module.bsl`) - especially if the name looks
"invented" (see the "Nonexistent modules" section). Analogies in БСП are often
misleading: the name root may not have a version without a suffix
(`БизнесПроцессыИЗадачи` -> only `...Сервер`), and the internal layer may exist
only with an additional suffix (`ОбщегоНазначенияСлужебныйКлиент`, but not
`...Служебный`).
