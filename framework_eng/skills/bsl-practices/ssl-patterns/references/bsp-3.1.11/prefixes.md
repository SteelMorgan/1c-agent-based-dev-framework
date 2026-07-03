# Object Prefixing: Number and Code Prefixes

The **ПрефиксацияОбъектов** subsystem is for automatic assignment and parsing of prefixes in document numbers and catalog/ПВХ/business process codes. Prefixes are needed in two dimensions: **information base** (РИБ, to distinguish nodes) and **organization** (end-to-end numbering by organizations). It covers programmatic number parsing, print-form number generation, and overriding prefixing logic through `*Переопределяемый` modules. Data exchange (exchange plans, change registration) is NOT described here - that is a separate subsystem.

## Modules

Suffix-based naming system (root `ПрефиксацияОбъектов` + context):

- `ПрефиксацияОбъектовКлиентСервер` - **stable API for application code**: number parsing, print-form number generation, extraction of the user prefix. Client + server + external connection.
- `ПрефиксацияОбъектовКлиентСерверПереопределяемый` - **hook**: replacement of `НомерНаПечать` logic. БСП calls it, application code implements it.
- `ПрефиксацияОбъектовПереопределяемый` - **hooks**: calculation of the base number/code for nonstandard formats, map of prefix-forming attributes.
- `ПрефиксацияОбъектовПовтИсп` - cache of the correspondence "metadata object → organization attribute name"; internal, cached.
- `ПрефиксацияОбъектовСлужебный` - ⚠️ service: adding a calculated field to SCD, changing the IB prefix with transactional renumbering.
- `ПрефиксацияОбъектовСобытия` - event subscription handlers (`УстановитьПрефиксОрганизации`, `УстановитьПрефиксИнформационнойБазы`, `ПроверитьНомерДокументаПоДате…`) and event `ПриОпределенииПрефиксаИнформационнойБазы` /
  `ПриОпределенииПрефиксаОрганизации`. Stable, but **do not call directly** - the platform invokes them through subscriptions.

⚠️ **The `ПрефиксацияОбъектов` module (without a suffix) does NOT exist** as a common module - there is a subsystem with that name in the configuration tree, but not a common module. The stable API is in `ПрефиксацияОбъектовКлиентСервер`. A common mistake is `ПрефиксацияОбъектов.НомерНаПечать(...)` (compilation error).

Number format: `[ОО][ИБ]-[ПП]ННН…`, where `ОО` is the organization prefix (2 characters, from the organizations catalog), `ИБ` is the IB prefix (2 characters, constant `ПрефиксУзлаРаспределеннойИнформационнойБазы`), `ПП` is the user prefix (optional, 2 characters), `ННН…` is the actual number. Example: `ОРИБ-АВ0000123`.
The print form omits the organization prefix and leading zeros: `ИБ-АВ123`.

## Scenarios

### 1. Get object number for printing

**Task:** convert a document/code number into a printable string - remove the organization prefix, optionally the information base prefix and the user prefix, strip leading zeros.

**Function:**
`ПрефиксацияОбъектовКлиентСервер.НомерНаПечать(Знач НомерОбъекта, УдалитьПрефиксИнформационнойБазы = Ложь, УдалитьПользовательскийПрефикс = Ложь) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Client + server + external connection.

**Parameters:**
- `НомерОбъекта` (String) — object number or code for printing.
- `УдалитьПрефиксИнформационнойБазы` (Boolean) — `Истина` removes the information base prefix
  (two characters after the organization prefix). Default is `Ложь`.
- `УдалитьПользовательскийПрефикс` (Boolean) — `Истина` removes the user prefix
  (the part between the standard prefix and the number digits). Default is
  `Ложь`.

**Example:**
```bsl
// Номер "ОРИБ-АВ0000123" -> на печать без префикса организации и нулей
ПечатныйНомер = ПрефиксацияОбъектовКлиентСервер.НомерНаПечать(НомерДокумента);
// "ИБ-АВ123"

// Убрать и префикс ИБ, и пользовательский
ПечатныйНомер = ПрефиксацияОбъектовКлиентСервер.НомерНаПечать(НомерДокумента, Истина, Истина);
// "АВ123" (или "123" если пользовательский префикс тоже убрать — зависит от флагов)
```

**Nuances / anti-patterns:**
- ❌ `ПрефиксацияОбъектов.НомерНаПечать(Номер)` — a module without a suffix does
  not exist. Only `ПрефиксацияОбъектовКлиентСервер`.
- ❌ `Сред(Номер, 6)` / `Лев(Номер, …)` — brittle: does not account for insignificant zeros,
  different hyphen positions (3 or 5), or a user prefix. Delegate
  to the subsystem.
- Before standard processing, `НомерНаПечать` calls the hook
  `ПрефиксацияОбъектовКлиентСерверПереопределяемый.ПриПолученииНомераНаПечать`;
  if this handler is implemented in the application code and
  `СтандартнаяОбработка = Ложь`, the function returns what is specified in `НомерОбъекта`.

### 2. Remove the specified prefixes from the number (without stripping zeros)

**Task:** remove the organization prefix and/or the infobase prefix from the number while preserving
leading zeros and the user prefix in place.

**Function:**
`ПрефиксацияОбъектовКлиентСервер.УдалитьПрефиксыИзНомераОбъекта(Знач НомерОбъекта, УдалитьПрефиксОрганизации = Ложь, УдалитьПрефиксИнформационнойБазы = Ложь) Экспорт`
— Function (stable). Client + server + external connection.

**Parameters:**
- `НомерОбъекта` (String) — object number/code.
- `УдалитьПрефиксОрганизации` (Boolean) — `Истина` removes the organization prefix
  (first 2 characters). Default is `Ложь`.
- `УдалитьПрефиксИнформационнойБазы` (Boolean) — `Истина` removes the infobase prefix
  (characters 3–4). Default is `Ложь`.

**Example:**
```bsl
// Remove both standard prefixes, keep zeros and the user prefix
Номер = ПрефиксацияОбъектовКлиентСервер.УдалитьПрефиксыИзНомераОбъекта(
    "0ФГЛ-000001234", Истина, Истина);
// "000001234" — insignificant prefix zeros removed, leading zeros of the number preserved
```

**Notes / anti-patterns:**
- Insignificant prefix characters ("0") are also removed. The difference from `НомерНаПечать`
  is that the leading zeros of the number itself are **not** stripped.
- To strip leading zeros separately while keeping the prefixes, use
  `УдалитьЛидирующиеНулиИзНомераОбъекта`; to remove only the user prefix, use
  `УдалитьПользовательскиеПрефиксыИзНомераОбъекта`.

### 3. Extract the user prefix and strip leading zeros

**Task:** get the user prefix from the number (the part between the standard
prefix and the digits) and separately remove leading zeros without affecting the prefixes.

**Functions:**
`ПрефиксацияОбъектовКлиентСервер.ПользовательскийПрефикс(Знач НомерОбъекта) Экспорт`
— Function (stable).
`ПрефиксацияОбъектовКлиентСервер.УдалитьЛидирующиеНулиИзНомераОбъекта(Знач НомерОбъекта) Экспорт`
— Function (stable).
`ПрефиксацияОбъектовКлиентСервер.УдалитьПользовательскиеПрефиксыИзНомераОбъекта(Знач НомерОбъекта) Экспорт`
— Function (stable).

**Parameters:**
- `НомерОбъекта` (String) — object number/code. Pattern for `ПользовательскийПрефикс`:
  `ООГГ-ААХ…Х` or `ГГ-ААХ…Х`, where `ОО` is the organization prefix, `ГГ` is the infobase
  prefix, `АА` is the user prefix, `Х…Х` is the number.

**Example:**
```bsl
// Number "ОР00-АВ00777" — user prefix "АВ"
Префикс = ПрефиксацияОбъектовКлиентСервер.ПользовательскийПрефикс("ОР00-АВ00777");
// "АВ"

// Strip leading zeros while preserving the standard and user prefixes
НомерБезНулей = ПрефиксацияОбъектовКлиентСервер.УдалитьЛидирующиеНулиИзНомераОбъекта("00ИБ-000001234");
// "ИБ-1234"

// Remove only the user prefix, keep the standard prefix
Номер = ПрефиксацияОбъектовКлиентСервер.УдалитьПользовательскиеПрефиксыИзНомераОбъекта("ОР00-АВ00777");
// "ОР00-00777"
```

**Notes / anti-patterns:**
- `ПользовательскийПрефикс` returns an empty string if there is no user
  prefix in the number.
- ❌ Custom parsing with `СтрНайти`/`Сред` will miss cases with
  non-standard formats and insignificant zeros; the standard functions already take into account
  the position of the hyphen (position 3 for `ГГ-…` or 5 for `ООГГ-…`).

### 4. Override the "base number" for a non-standard format

**Task:** for a document with a non-standard number format (letter prefixes without
hyphens, e.g. `АБВГ0012345`), correctly compute the base number when the database
prefix changes.

**Function (hook):**
`ПрефиксацияОбъектовПереопределяемый.ПриИзмененииНомера(Объект, Знач Номер, БазовыйНомер, СтандартнаяОбработка) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` of the `*Переопределяемый` module.
**Hook**: БСП calls it when processing the number; application code implements the body in
its own override module. Do not call it directly.
Analog for codes: `ПриИзмененииКода(Объект, Знач Код, БазовыйКод, СтандартнаяОбработка) Экспорт`.

**Parameters:**
- `Объект` (ЛюбаяСсылка) — the object whose number is being processed.
- `Номер` (Строка) — current number.
- `БазовыйНомер` (Строка) — output: the base part of the number for continuing
  numbering.
- `СтандартнаяОбработка` (Булево) — output: `Ложь` disables the standard
  algorithm (by default it looks for the first non-digit position from the right and leaves
  the numeric tail).

**Example:**
```bsl
// In the application module ПрефиксацияОбъектовПереопределяемый:
Процедура ПриИзмененииНомера(Объект, Знач Номер, БазовыйНомер, СтандартнаяОбработка) Экспорт
    Если ТипЗнч(Объект) = Тип("ДокументОбъект.РеализацияТоваров") Тогда
        Если Сред(Номер, 5, 1) <> "-" Тогда
            // Non-standard format "АБВГ0012345": 3 old prefixes of 1 character each
            СтандартнаяОбработка = Ложь;
            БазовыйНомер = Сред(Номер, 4); // skip 3 prefixes
        КонецЕсли;
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Ignoring the hook for a non-standard format — the standard algorithm will yield
  an incorrect base number, and `ПрефиксацияОбъектовСлужебный.ОбработатьДанныеДляПродолженияНумерации`
  will renumber documents incorrectly when the database prefix changes.
- Without `СтандартнаяОбработка = Ложь`, the standard algorithm will be executed
  on top of the application result.

### 5. Register a prefix-forming attribute with a nonstandard name

**Task:** tell БСП that in the application document the organization is stored not in the
`Организация` attribute, but in `ГоловнаяОрганизация`, so that the
`УстановитьПрефиксОрганизации` subscription finds the attribute and generates the prefix.

**Function (hook):**
`ПрефиксацияОбъектовПереопределяемый.ПолучитьПрефиксообразующиеРеквизиты(Объекты) Экспорт`
— Procedure (hook), `*Переопределяемый` module. БСП calls it, and the application code
fills the `Объекты` table.

**Parameters:**
- `Объекты` (ТаблицаЗначений) — created in advance by БСП; columns `Объект`
  (ОбъектМетаданных) and `Реквизит` (Строка, organization attribute name). It is enough
  to add rows. The result is cached in `ПрефиксацияОбъектовПовтИсп`.

**Example:**
```bsl
// In the application module ПрефиксацияОбъектовПереопределяемый:
Процедура ПолучитьПрефиксообразующиеРеквизиты(Объекты) Экспорт
    Строка = Объекты.Добавить();
    Строка.Объект   = Метаданные.Документы.РеализацияТоваров;
    Строка.Реквизит = "ГоловнаяОрганизация";
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Calling `ПрефиксацияОбъектовСобытия.УстановитьПрефиксОрганизации(Источник, Истина, "")`
  directly from application code — the handler is connected as an event subscription,
  and the platform invokes it automatically. A direct call violates the subscription contract.
- By default, БСП looks for the `Организация` attribute; without registering a nonstandard
  name, the organization prefix will not be generated.

### 6. Change the infobase prefix programmatically and add the “Number with prefix” field to SCD

**Task:** programmatically change the infobase prefix while continuing numbering (in the background,
transactionally) and add the calculated field `Номер.СПрефиксом` to the report data
composition schema.

**Functions (⚠️ service region):**
`ПрефиксацияОбъектовСлужебный.ИзменитьПрефиксИБ(Параметры, АдресРезультата = "") Экспорт`
— Procedure, region `СлужебныйПрограммныйИнтерфейс`. Server-side. Writes the new
value of the `ПрефиксУзлаРаспределеннойИнформационнойБазы` constant and (if
`ПродолжитьНумерацию = Истина`) renumbers the elements last in the period.
Requires the connected "Обмен данными" subsystem.
`ПрефиксацияОбъектовСлужебный.ДобавитьРасширениеПоляНомер(СхемаКомпоновкиДанных) Экспорт`
— Procedure (service), adds the calculated field `Номер.СПрефиксом` to the SCD.

**Parameters:**
- `Параметры` (Структура) — `НовыйПрефиксИБ` (Строка), `ПродолжитьНумерацию`
  (Булево).
- `АдресРезультата` (Строка) — address of the temporary storage for the result.
- `СхемаКомпоновкиДанных` (СхемаКомпоновкиДанных) — schema that has the `Номер` field.

**Example:**
```bsl
// Changing the infobase prefix while continuing numbering (background job)
ПараметрыВыполнения = Новый Структура;
ПараметрыВыполнения.Вставить("НовыйПрефиксИБ", "НБ");
ПараметрыВыполнения.Вставить("ПродолжитьНумерацию", Истина);
АдресРезультата = ПоместитьВоВременноеХранилище(Неопределено, УникальныйИдентификатор);
ПрефиксацияОбъектовСлужебный.ИзменитьПрефиксИБ(ПараметрыВыполнения, АдресРезультата);

// Field "Номер.СПрефиксом" in the report SCD
ПрефиксацияОбъектовСлужебный.ДобавитьРасширениеПоляНомер(СхемаКомпоновкиДанных);
```

**Nuances / anti-patterns:**
- ⚠️ Both methods are in the service region (`СлужебныйПрограммныйИнтерфейс`), backward
  compatibility is not guaranteed. In the usual scenario, the infobase prefix is changed through
  the "Обмен данными" subsystem interface (Administration → Data synchronization);
  programmatic setting is only for migration handlers.
- ❌ `Константы.ПрефиксУзлаРаспределеннойИнформационнойБазы.Установить("НБ")`
  directly — does not update the prefix in the exchange subsystem, breaks RIB numbering.
  Only through `ИзменитьПрефиксИБ` (or interactively).
- `ДобавитьРасширениеПоляНомер` creates the calculated field `Номер.СПрефиксом` —
  there is no need to substitute `НомерНаПечать` again in the field expression.

## Additional

Event methods of the `ПрефиксацияОбъектовСобытия` module (region
`ПрограммныйИнтерфейс`) — subscription handlers, **do not call directly**
(the platform invokes them through event subscriptions):

- `УстановитьПрефиксОрганизации(Источник, СтандартнаяОбработка, Префикс)` —
  assigns the organization prefix in `ПриУстановкеНовогоКода`/`ПриУстановкеНомера`.
- `УстановитьПрефиксИнформационнойБазы(Источник, СтандартнаяОбработка, Префикс)` /
  `УстановитьПрефиксИнформационнойБазыИОрганизации(...)` — the same for the infobase and both.
- `ПроверитьНомерДокументаПоДате(Источник, Отказ, РежимЗаписи, РежимПроведения)` /
  `ПроверитьНомерДокументаПоДатеИОрганизации(...)` /
  `ПроверитьКодСправочникаПоОрганизации(Источник, Отказ)` /
  `ПроверитьНомерБизнесПроцессаПоДате(...)` — reset the number/code when the
  date/organization changes so that the platform generates a new number in the correct period.
- `ПриОпределенииПрефиксаИнформационнойБазы(ПрефиксИнформационнойБазы)` /
  `ПриОпределенииПрефиксаОрганизации(Знач Организация, ПрефиксОрганизации)` —
  event methods for obtaining prefixes; the logic can be overridden in the
  application module `ПрефиксацияОбъектовСобытия` (by copying it into the configuration).

The override hook `ПрефиксацияОбъектовКлиентСерверПереопределяемый.ПриПолученииНомераНаПечать(НомерОбъекта, СтандартнаяОбработка, УдалитьПрефиксИнформационнойБазы, УдалитьПользовательскийПрефикс)`
— БСП calls it before standard processing of `НомерНаПечать`; application code
implements the body and can set `СтандартнаяОбработка = Ложь` and assign
`НомерОбъекта` directly. Do not call it from application code.