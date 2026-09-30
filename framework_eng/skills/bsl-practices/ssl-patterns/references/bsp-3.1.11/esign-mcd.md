# Electronic signature and machine-readable powers of attorney in БСП

Subsystems **ЭлектроннаяПодпись** (signing, verification, certificates) and
**МашиночитаемыеДоверенности** (MChD — FNS registry). Covers the stable
server/client API, certificate selection, signature and certificate verification,
signature enhancement, and working with MChD.

> ⚠️ **Two call models.** Client (`ЭлектроннаяПодписьКлиент`) is asynchronous,
> via `ОписаниеОповещения`, and opens forms. Server (`ЭлектроннаяПодпись`) is
> synchronous and returns the value directly. Server-side `ЭлектроннаяПодпись.Подписать`
> **does not exist** — signing is client-only; the server only saves the
> result (`ДобавитьПодпись`), reads signatures (`ПодписиОбъекта`), and verifies
> (`ПроверитьПодпись`, `ПроверитьСертификат`).

## Modules

The `ЭлектроннаяПодпись*` family follows the BSP suffix system:

- `ЭлектроннаяПодпись` — server: operations on referential objects, signature
  registers, verification, crypto manager. Stable API (60 exports in
  `ПрограммныйИнтерфейс`).
- `ЭлектроннаяПодписьКлиент` — client: interactive scenarios — signing form,
  certificate selection, dialogs, asynchronous operations via `ОписаниеОповещения`.
- `ЭлектроннаяПодписьКлиентСервер` — shared structures: `НовыеСвойстваПодписи`,
  `РезультатПроверкиПодписи`.
- `ЭлектроннаяПодписьСлужебный` — ⚠️ service (`СлужебныйПрограммныйИнтерфейс`):
  `Зашифровать(Данные, Сертификат, МенеджерКриптографии)`, `ДоступнаЭлектроннаяПодпись`.
  Backward compatibility is not guaranteed. A number of related service methods
  (`ПодписьВКодировкеDER`, `РасшифровкаДанных`) are located in the main module
  `ЭлектроннаяПодпись` (also `СлужебныйПрограммныйИнтерфейс`) — not in Служебный.
- `ЭлектроннаяПодписьПереопределяемый` / `ЭлектроннаяПодписьКлиентПереопределяемый`
  — hooks (implement, do not call): `ПередНачаломОперации` and so on.
- `ЭлектроннаяПодписьЛокализация` / `*КлиентЛокализация` / `*КлиентСерверЛокализация`
  — regional overrides (Russia-specific for MChD).
- `ЭлектроннаяПодписьВМоделиСервиса*` — data separation in SaaS.

MChD is a **separate family with the `ФНС` suffix**:

- `МашиночитаемыеДоверенностиФНС` — server-side MChD API: creation, signing the
  power of attorney file, verification, signature verification result for MChD.
- `МашиночитаемыеДоверенностиФНСКлиент` — client: `ОткрытьСписокМЧД`,
  `СоздатьМЧД`, `ПроверитьДоверенность`.
- `МашиночитаемыеДоверенностиФНСПереопределяемый` /
  `*КлиентПереопределяемый` — hooks.
- ⚠️ The module `МашиночитаемыеДоверенности` (without `ФНС`) **does not exist** —
  a typical mistake by analogy with `УправлениеДоступом`. The real name has the
  `ФНС` suffix.

DSS (digital signature service, КриптоПро DSS and analogues) is a separate family
of modules `СервисКриптографииDSS*` and `СервисМобильнойПодписи*` (subsystem
`ЭлектроннаяПодписьСервисаDSS`). This file covers DSS briefly — the main path is
through `ЭлектроннаяПодписьКлиент.Подписать`; specific DSS functions are
`СервисКриптографииDSS*` / `СервисМобильнойПодписи*` directly.

> ⚠️ **`ПроверитьПодпись` exists in 6 modules with DIFFERENT signatures**:
> `ЭлектроннаяПодпись` (server, synchronous, by crypto manager),
> `ЭлектроннаяПодписьКлиент` (client, asynchronous, by `ОписаниеОповещения`),
> `СервисКриптографии` / `СервисКриптографииКлиент` (shared crypto manager),
> `СервисКриптографииDSS` / `СервисКриптографииDSSКлиент` (DSS). For application
> code, the canonical ones are `ЭлектроннаяПодпись.ПроверитьПодпись` (server) and
> `ЭлектроннаяПодписьКлиент.ПроверитьПодпись` (client). **Always clarify
> `--module`** when searching for a signature.

## Scenarios

### 1. Sign data or an object (client, asynchronously)

**Task:** sign binary data, a file, or a reference object with the selected
certificate through the standard form; when an object is specified, write the signature
to the infobase.

**Function:**
`ЭлектроннаяПодписьКлиент.Подписать(ОписаниеДанных, Форма = Неопределено, ОбработкаРезультата = Неопределено, ПараметрыПодписи = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client (Thin,
Thick). Result is returned in `ОбработкаРезультата` (asynchronously).

**Parameters:**
- `ОписаниеДанных` (Structure) — keys:
  - `Операция` (String) — signing form title (for example,
    `НСтр("ru = 'Подписание документа'")`).
  - `ЗаголовокДанных` (String) — data title (`"Документ"`, `"Файл"`).
  - `Данные` (`ДвоичныеДанные` / `Строка`-address / `ОписаниеОповещения` / Structure
    `ПараметрыCMS` + `Данные`) — data to be signed.
  - `Объект` (`ЛюбаяСсылка`, optional) — reference to add the signature to; if
    specified, the server side will call `ДобавитьПодпись` itself and set
    `ПодписанЭП = Истина`.
  - `ВерсияОбъекта` (String, optional) — version for checking/locking.
  - `ПоказатьКомментарий` (Boolean, optional) — allow comment input in the form.
  - `ОтборСертификатов` (Array, optional) — references to
    `СертификатыЭлектроннойПодписиИШифрования` to filter the selection.
  - `ВыбраннаяДоверенность` (`СправочникСсылка.МашиночитаемыеДоверенности`,
    optional) — MChD for signing on behalf of a representative.
  - `ВыполнятьНаСервере` (Boolean / `Неопределено`, optional) — `Неопределено` =
    server first, client if that fails; `Истина` = server only; `Ложь` =
    client only.
- `Форма` (`ФормаКлиентскогоПриложения` / `УникальныйИдентификатор` /
  `Неопределено`) — form for locking the object; `Неопределено` = standard
  form.
- `ОбработкаРезультата` (`ОписаниеОповещения`) — result handler; it receives
  `ОписаниеДанных`, supplemented with `Успех` (Boolean), `Отказ` (Boolean),
  `СвойстваПодписи` (Structure/address), and `ВыбранныйСертификат`.
- `ПараметрыПодписи` (see `ЭлектроннаяПодписьКлиент.НовыйТипПодписи`) — signature type.

**Example:**
```bsl
// &НаКлиенте — команда "Подписать"
ОписаниеДанных = Новый Структура;
ОписаниеДанных.Вставить("Операция",        НСтр("ru = 'Подписание документа'"));
ОписаниеДанных.Вставить("ЗаголовокДанных", НСтр("ru = 'Документ'"));
ОписаниеДанных.Вставить("Объект",          Объект.Ссылка);
ОписаниеДанных.Вставить("ВерсияОбъекта",   Объект.ВерсияДанных);
ОписаниеДанных.Вставить("ПоказатьКомментарий", Истина);

ОбработкаРезультата = Новый ОписаниеОповещения("ПослеПодписания", ЭтотОбъект);
ЭлектроннаяПодписьКлиент.Подписать(ОписаниеДанных, ЭтаФорма, ОбработкаРезультата);

// &НаКлиенте
Процедура ПослеПодписания(Результат, ДопПараметры) Экспорт
    Если Результат.Свойство("Успех") И Результат.Успех Тогда
        ЭтаФорма.Прочитать();  // серверная часть уже записала подпись к объекту
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ `ЭлектроннаяПодпись.Подписать(...)` from server code — the method does not exist
  on the server (compile error). Signing is initiated by the client; the server only
  saves the result through `ДобавитьПодпись` (called inside `Подписать`, when `Объект`
  is specified).
- ❌ Calling the client API from `&НаСервере` — `ЭлектроннаяПодписьКлиент` does not
  compile on the server.
- Before calling, check subsystem availability:
  `ОбщегоНазначения.ПодсистемаСуществует("СтандартныеПодсистемы.ЭлектроннаяПодпись")`
  and `ЭлектроннаяПодпись.ИспользоватьЭлектронныеПодписи()`.

### 2. Verify the signature on the server (background/job processing)

**Task:** programmatically verify the validity of the signature and certificate on
 the server (scheduled job, processing), capturing the error description.

**Functions:**
`ЭлектроннаяПодпись.МенеджерКриптографии(Операция, ПоказатьОшибку = Истина, ОписаниеОшибки = "", Программа = Неопределено) Экспорт`
— Function -> `МенеджерКриптографии` / `Неопределено`, region `ПрограммныйИнтерфейс`
(stable). Server.
`ЭлектроннаяПодпись.ПроверитьПодпись(МенеджерКриптографии, ИсходныеДанные, Подпись, ОписаниеОшибки = Null, НаДату = Неопределено, РезультатСтруктура = Неопределено) Экспорт`
— Function -> `Булево`, region `ПрограммныйИнтерфейс` (stable). Server.
`ЭлектроннаяПодпись.ПроверитьСертификат(МенеджерКриптографии, Сертификат, ОписаниеОшибки = Null, НаДату = Неопределено, ПараметрыПроверки = Неопределено) Экспорт`
— Function -> `Булево`, region `ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Операция` (String) — `"Подписание"`, `"ПроверкаПодписи"`, `"Шифрование"`,
  `"Расшифровка"`, `"ПроверкаСертификата"`, `"ПолучениеСертификатов"` (inserted
  into the error text).
- `ПоказатьОшибку` (Boolean) — `Истина` = raise an exception on failure; `Ложь` =
  return `Неопределено` and fill `ОписаниеОшибки`.
- `ОписаниеОшибки` (String) — output, filled when returning `Неопределено`.
- `Программа` (`СправочникСсылка.ПрограммыЭлектроннойПодписиИШифрования` /
  `Неопределено`) — `Неопределено` = first program from the catalog.
- `ИсходныеДанные` (`ДвоичныеДанные` / `Строка`-address / Envelope structure) —
  signed data.
- `Подпись` (`ДвоичныеДанные` / `Строка`-address) — signature being verified.
- `ОписаниеОшибки` (String, output, default `Null`) — filled only
  on failure.
- `НаДату` (`Дата` / `Неопределено`) — certificate verification date; `Неопределено`
  = date from the signature, otherwise the session date.
- `РезультатСтруктура` (Structure, optional) — if you pass a structure (from
  `ЭлектроннаяПодписьКлиентСервер.РезультатПроверкиПодписи()`), the result
  is filled in detail (error categories, statuses).

**Example:**
```bsl
// Server code (scheduled job / processing)
ОписаниеОшибки = "";
Менеджер = ЭлектроннаяПодпись.МенеджерКриптографии("ПроверкаПодписи", Ложь, ОписаниеОшибки);
Если Менеджер = Неопределено Тогда
    ЗаписьЖурналаРегистрации(НСтр("ru = 'ЭП.Проверка подписи'"),
        УровеньЖурналаРегистрации.Предупреждение, , , ОписаниеОшибки);
    Возврат;
КонецЕсли;

Подписи = ЭлектроннаяПодпись.ПодписиОбъекта(ДокументСсылка);
Для Каждого СвойстваПодписи Из Подписи Цикл
    ОписаниеОшибки = "";
    Верна = ЭлектроннаяПодпись.ПроверитьПодпись(Менеджер,
        ДвоичныеДанныеОбъекта, СвойстваПодписи.Подпись, ОписаниеОшибки);
    Если Не Верна Тогда
        ЗаписьЖурналаРегистрации(НСтр("ru = 'ЭП.Проверка подписи'"),
            УровеньЖурналаРегистрации.Предупреждение, , , ОписаниеОшибки);
    КонецЕсли;
КонецЦикла;
```

**Nuances / anti-patterns:**
- ❌ Ignore `ОписаниеОшибки` (4th parameter) — a boolean does not give a reason to
  the user/log. Always pass the output string and log on failure.
- ❌ Create `Новый МенеджерКриптографии("Crypto-Pro GOST R 34.10-2012", "", 75)`
  instead of `ЭлектроннаяПодпись.МенеджерКриптографии` — breaks integration with the subsystem
  (program settings, logging, certificate notifications). Use
  the БСП wrapper.
- The certificate is always checked on the server if the administrator configured EP verification on the server (`ЭлектроннаяПодпись.ПроверятьЭлектронныеПодписиНаСервере()`).
- For a detailed result, pass the structure from `ЭлектроннаяПодписьКлиентСервер.РезультатПроверкиПодписи()` into `РезультатСтруктура`.

### 3. Save, update, and delete an object signature

**Task:** save a signature for a reference object (with `ПодписанЭП` set), update the properties of an already saved signature, delete the signature.

**Functions:**
`ЭлектроннаяПодпись.ДобавитьПодпись(Объект, Знач СвойстваПодписи, ИдентификаторФормы = Неопределено, ВерсияОбъекта = Неопределено, ЗаписанныйОбъект = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Server.
`ЭлектроннаяПодпись.ОбновитьПодпись(Объект, Знач СвойстваПодписи, ОбновитьПоПорядковомуНомеру = Ложь) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Server.
`ЭлектроннаяПодпись.УдалитьПодпись(Объект, ПорядковыйНомер, ИдентификаторФормы = Неопределено, ВерсияОбъекта = Неопределено, ЗаписанныйОбъект = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Объект` (`ОпределяемыйТип.ПодписанныйОбъект` — reference or object) — must
  have the `ПодписанЭП` attribute. By reference: the object is locked, modified,
  and written. If you pass an **object** (not a reference), it is modified without locking
  and without writing (the caller writes it themselves).
- `СвойстваПодписи` (`Строка`-address / `Структура` / Array) — structure from
  `ЭлектроннаяПодписьКлиентСервер.НовыеСвойстваПодписи()` (thumbprint, date,
  certificate, status, signature type, signature).
- `ИдентификаторФормы` (`УникальныйИдентификатор`) — for object locking;
  from the form — `ЭтаФорма.УникальныйИдентификатор`.
- `ВерсияОбъекта` (String, optional) — data version for checking and locking.
- `ПорядковыйНомер` (Number) — sequence number of the signature (for `УдалитьПодпись` and
  `ОбновитьПодпись` when `ОбновитьПоПорядковомуНомеру = Истина`).
- `ЗаписанныйОбъект` (object, optional) — already written object (so as not to read
  it again).

**Example:**
```bsl
// In custom server processing (outside the client `Подписать`)
СвойстваПодписи = ЭлектроннаяПодписьКлиентСервер.НовыеСвойстваПодписи();
СвойстваПодписи.Отпечаток = ОтпечатокСертификата;
СвойстваПодписи.Подпись   = ДвоичныеДанныеПодписи;
СвойстваПодписи.Сертификат = ДвоичныеДанныеСертификата;
// ... остальные свойства ...

ЭлектроннаяПодпись.ДобавитьПодпись(Объект.Ссылка, СвойстваПодписи, УИДФормы);

// Update signature properties (for example after enhancement) by sequence number
ЭлектроннаяПодпись.ОбновитьПодпись(Объект.Ссылка, НовыеСвойства, Истина);

// Delete signature #2
ЭлектроннаяПодпись.УдалитьПодпись(Объект.Ссылка, 2, УИДФормы);
```

**Nuances / anti-patterns:**
- ❌ Write the signature directly into `РегистрСведений.ЭлектронныеПодписи` through a
  record set — this bypasses the `ПодписанЭП` logic, locking, and notifications. Only through
  `ДобавитьПодпись` / `ОбновитьПодпись` / `УдалитьПодпись`.
- `ДобавитьПодпись` itself locks, modifies, and writes the object by reference. If
  you already hold the object and write it yourself, pass the object (not the reference); then
  БСП will modify it without writing.
- ❌ `ЭлектроннаяПодпись.УстановленныеПодписи(...)` — **deprecated**
  (`УстаревшиеПроцедурыИФункции`), marked `// Устарела. Следует использовать
  ПодписиОбъекта`. Do not use in new code.

### 4. Get object signatures and their properties

**Task:** read the list of installed object signatures (properties,
fingerprints, statuses) for a UI form or report.

**Functions:**
`ЭлектроннаяПодпись.ПодписиОбъекта(Объект, ДополнительныеПараметры = Неопределено) Экспорт`
— Function → `Массив` of the signature properties structure, region `ПрограммныйИнтерфейс`
(stable). Server.
`ЭлектроннаяПодпись.СвойстваПодписи(Подпись, ПрочитатьСертификаты = Истина) Экспорт`
— Function → Structure, region `ПрограммныйИнтерфейс` (stable). Server.
`ЭлектроннаяПодпись.ДатаПодписания(Подпись, ПривестиКЧасовомуПоясуСеанса = Истина) Экспорт`
— Function → `Дата` / `Неопределено`, region `ПрограммныйИнтерфейс` (stable).
Server.
`ЭлектроннаяПодписьКлиентСервер.НовыеСвойстваПодписи() Экспорт` — Function →
Signature properties constructor structure, region `ПрограммныйИнтерфейс` (stable).
Client + Server.

**Parameters:**
- `Объект` (`ОпределяемыйТип.ПодписанныйОбъект`) — reference; must have the
  `ПодписанЭП` attribute.
- `ДополнительныеПараметры` (see `ЭлектроннаяПодпись.НовыйПараметрыПолученияПодписейОбъекта()`)
  — optional filter/selection settings.
- `Подпись` (`ДвоичныеДанные` / `Строка`-address) — signature for parsing properties /
  date.
- `ПрочитатьСертификаты` (Булево) — `Истина` = read certificates from the signature.

**Example:**
```bsl
Подписи = ЭлектроннаяПодпись.ПодписиОбъекта(ДокументСсылка);
Для Каждого СвойстваПодписи Из Подписи Цикл
    // СвойстваПодписи.Отпечаток, .ДатаПодписи, .Сертификат, .ТипПодписи, .СтатусПроверки
    Сообщить(СвойстваПодписи.Отпечаток + " от " + СвойстваПодписи.ДатаПодписи);
КонецЦикла;

// Извлечь свойства и дату прямо из двоичных данных подписи
Св = ЭлектроннаяПодпись.СвойстваПодписи(ДвоичныеДанныеПодписи);
ДатаПодписи = ЭлектроннаяПодпись.ДатаПодписания(ДвоичныеДанныеПодписи);
```

**Nuances / anti-patterns:**
- ⚠️ `ДатаПодписания` exists in **two** modules with different signatures:
  `ЭлектроннаяПодпись.ДатаПодписания(Подпись, ПривестиКЧасовомуПоясуСеанса = Истина)`
  — server, Function (returns `Дата`); and
  `ЭлектроннаяПодписьКлиент.ДатаПодписания(Оповещение, Подпись,
  ПривестиКЧасовомуПоясуСеанса = Истина)` — client, Procedure (result in
  `Оповещение`). Specify the module via `--module`.
- ❌ `УстановленныеПодписи(...)` — deprecated, use `ПодписиОбъекта`.

### 5. Improve a signature to qualified/archive

**Task:** raise the signature type (e.g. to CAdES-T/A) - either a standalone signature
or a signature already written to an object.

**Functions:**
`ЭлектроннаяПодпись.УсовершенствоватьПодпись(Подпись, ТипПодписи, ДобавитьАрхивнуюМеткуВремени = Ложь, ДополнительныеПараметры = Неопределено) Экспорт`
— Function → Structure of changed properties, region `ПрограммныйИнтерфейс`
(stable). Server.
`ЭлектроннаяПодпись.УсовершенствоватьПодписьОбъекта(ПодписанныйОбъект, ПорядковыйНомер, ТипПодписи, ДобавитьАрхивнуюМеткуВремени = Ложь, ИдентификаторФормы = Неопределено, ДополнительныеПараметры = Неопределено) Экспорт`
— Function, region `ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Подпись` (`ДвоичныеДанные`) — signature to improve.
- `ТипПодписи` (`ПеречислениеСсылка.ТипыПодписиКриптографии`) — target type. If
the actual type is already higher, no action will be taken.
- `ДобавитьАрхивнуюМеткуВремени` (Булево) — `Истина` = add a timestamp to the
archive signature (CAdES-A).
- `ПодписанныйОбъект` (`ОпределяемыйТип.ПодписанныйОбъект`) — object whose signature
we are improving.
- `ПорядковыйНомер` (Число) — sequential number of the object's signature.
- `ИдентификаторФормы` (`УникальныйИдентификатор`) — for locking.
- `ДополнительныеПараметры` (Структура) — `МенеджерКриптографии`
  (`Неопределено` / `МенеджерКриптографии`), `ИгнорироватьСрокДействияСертификата`
  (Булево).

**Example:**
```bsl
// Improve object signature No. 1 to archive
ДопПараметры = Новый Структура;
ДопПараметры.Вставить("ИгнорироватьСрокДействияСертификата", Ложь);
ЭлектроннаяПодпись.УсовершенствоватьПодписьОбъекта(
    ДокументСсылка, 1, Перечисления.ТипыПодписиКриптографии.Архивная, Истина, , ДопПараметры);

// Improve a "bare" signature (binary data)
ИзмененныеСвойства = ЭлектроннаяПодпись.УсовершенствоватьПодпись(
    ДвоичныеПодписи, Перечисления.ТипыПодписиКриптографии.Усиленная);
```

**Nuances / anti-patterns:**
- ❌ `ЭлектроннаяПодпись.ДоступнаУсовершенствованнаяПодпись()` — **deprecated**
  (`УстаревшиеПроцедурыИФункции`). Do not use in new code; the target type
  of the signature is set by the `ТипПодписи` parameter.
- `УсовершенствоватьПодпись` returns only the **changed** properties, not the
  full set. If the type is already higher than the target, the structure is empty and no action is taken.

### 6. Register the certificate and set the password (client)

**Task:** open the form for adding a user certificate to the `СертификатыКлючейЭлектроннойПодписиИШифрования` catalog; set the certificate password for the session.

**Functions:**
`ЭлектроннаяПодписьКлиент.ДобавитьСертификат(ОбработчикЗавершения = Неопределено, ПараметрыДобавления = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Client.
`ЭлектроннаяПодписьКлиент.ПараметрыДобавленияСертификата() Экспорт` — Function →
Structure, region `ПрограммныйИнтерфейс` (stable). Client.
`ЭлектроннаяПодписьКлиент.УстановитьПарольСертификата(СертификатСсылка, Пароль, ПояснениеПароля = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Client.
`ЭлектроннаяПодписьКлиент.ПолучитьОтпечаткиСертификатов(Оповещение, ТолькоЛичные, ПараметрыПолучения = Истина) Экспорт`
— Procedure (asynchronous), region `ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `ОбработчикЗавершения` (`ОписаниеОповещения`) — called after adding.
- `ПараметрыДобавления` (Structure from `ПараметрыДобавленияСертификата`) — `Комментарий`,
  `ДляШифрования`, etc.
- `СертификатСсылка` (`СправочникСсылка.СертификатыКлючейЭлектроннойПодписиИШифрования`)
  — certificate whose password is remembered.
- `Пароль` (String) — certificate password (stored only for the session).
- `Оповещение` (`ОписаниеОповещения`) — returns an array of fingerprint strings.
- `ТолькоЛичные` (Boolean) — `Истина` = user's personal certificates, `Ложь` = all.
- `ПараметрыПолучения` (Boolean / Structure from `ПараметрыПолученияОтпечатковСертификатов`)
  — `Истина` = default values (client + server + service).

**Example:**
```bsl
// &НаКлиенте — add a certificate
Параметры = ЭлектроннаяПодписьКлиент.ПараметрыДобавленияСертификата();
Параметры.Комментарий = НСтр("ru = 'Сертификат для подписания ЭП'");
Обработчик = Новый ОписаниеОповещения("ПослеДобавленияСертификата", ЭтотОбъект);
ЭлектроннаяПодписьКлиент.ДобавитьСертификат(Обработчик, Параметры);

// Remember the certificate password for the duration of the session (so it is not asked on every signature)
ЭлектроннаяПодписьКлиент.УстановитьПарольСертификата(СертификатСсылка, ВведенныйПароль);
```

**Nuances / anti-patterns:**
- ❌ Store the certificate password in attributes/constants in plain text. If
  long-term storage is needed, use only secure storage
  (`ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище` — see `base-common.md`),
  and read it before signing, without writing it to the information base.
- `УстановитьПарольСертификата` works **only for the current session** — on the
  next launch the password must be entered again (or stored in secure storage).

### 7. Create an MChD, verify the power of attorney and the signature against the MChD

**Task:** open the form for creating a machine-readable power of attorney; verify
 the power of attorney (including in the FNS register); verify the recorded object signature against the MChD (signer = representative, powers, date).

**Functions:**
`МашиночитаемыеДоверенностиФНСКлиент.СоздатьМЧД(ПараметрыФормы, ОповещениеОЗавершении = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Client (Thin, Thick).
`МашиночитаемыеДоверенностиФНСКлиент.ПроверитьДоверенность(Оповещение, Доверенность, ИдентификаторФормы = Неопределено) Экспорт`
— Procedure (asynchronous), region `ПрограммныйИнтерфейс` (stable). Client.
`МашиночитаемыеДоверенностиФНСКлиент.ОткрытьСписокМЧД(Отборы = Неопределено, ОповещениеОЗакрытии = Неопределено, Владелец = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Client.
`МашиночитаемыеДоверенностиФНС.РезультатПроверкиДоверенности(Доверенность, ПроверятьВРеестреФНС = Неопределено) Экспорт`
— Function -> Structure, region `ПрограммныйИнтерфейс` (stable). Server.
`МашиночитаемыеДоверенностиФНС.РезультатПроверкиПодписиПоМЧД(ПодписанныйОбъект, ИдентификаторПодписи, СертификатПодписи, НаДату) Экспорт`
— Function -> `Массив` of Structure, region `ПрограммныйИнтерфейс` (stable). Server.
`МашиночитаемыеДоверенностиФНС.ДобавитьПодписьКФайлуДоверенности(ФайлДоверенности, Знач Подпись) Экспорт`
— Function -> `Булево` (True) or String (error text), region
`ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ПараметрыФормы` (see `МашиночитаемыеДоверенностиФНСКлиент.ПараметрыСозданияМЧД()`)
  — creation form parameters.
- `Оповещение` / `ОповещениеОЗавершении` (`ОписаниеОповещения`) — result handler.
- `Доверенность` (`СправочникСсылка.МашиночитаемыеДоверенности`) — the power of
 attorney being verified.
- `ПроверятьВРеестреФНС` (Булево / `Неопределено`) — `Неопределено` = depending on
 the `РегистрироватьВРеестре` flag.
- `ПодписанныйОбъект` (`ОпределяемыйТип.ПодписанныйОбъект`) — the object whose signature
 is being verified against the MChD.
- `ИдентификаторПодписи` (`УникальныйИдентификатор`) — signature identifier from
 `НовыеСвойстваПодписи.ИдентификаторПодписи`.
- `СертификатПодписи` (`СертификатКриптографии` / `ДвоичныеДанные` / `Строка`-address)
  — the signer's certificate.
- `НаДату` (`Дата`) — signature date; if not filled in, verification is performed for the session date.
- `ФайлДоверенности` (link to the attached MChD file) — the file to which the
 signature is added.
- `Подпись` (signature properties structure) — the grantor's signature.

**Example:**
```bsl
// Client: open the MChD creation form
Параметры = МашиночитаемыеДоверенностиФНСКлиент.ПараметрыСозданияМЧД();
МашиночитаемыеДоверенностиФНСКлиент.СоздатьМЧД(Параметры,
    Новый ОписаниеОповещения("ПослеСозданияМЧД", ЭтотОбъект));

// Server: verify the power of attorney (including in the FNS register)
Результат = МашиночитаемыеДоверенностиФНС.РезультатПроверкиДоверенности(МЧДСсылка);
Если Результат.Верна Тогда
    // the power of attorney signatures are valid and match the grantors
Иначе
    Сообщить(Результат.ТекстОшибки);
КонецЕсли;

// Server: verify the object signature against the MChD (array!)
РезультатМЧД = МашиночитаемыеДоверенностиФНС.РезультатПроверкиПодписиПоМЧД(
    ДокументСсылка, ИдентификаторПодписи, СертификатПодписи, ДатаПодписи);
Для Каждого СтрокаРезультата Из РезультатМЧД Цикл
    Если СтрокаРезультата.Верна Тогда
        // signature is valid + powers match
    Иначе
        // СтрокаРезультата.ПротоколПроверки — detailed breakdown
    КонецЕсли;
КонецЦикла;
```

**Nuances / antipatterns:**
- ❌ `РезультатПроверкиПодписиПоМЧД(...).Верна` directly (as in older examples) —
  the method returns a **Массив** of Структура, not a single structure. Iterate
  over the array: `Для Каждого СтрокаРезультата Из РезультатМЧД`. Each row contains
  `МашиночитаемаяДоверенность`, `Верна`, `ТребуетсяПроверка`,
  `ПодписантСоответствуетПредставителю`, `СовместныеПолномочия`,
  `ПротоколПроверки`.
- ❌ Module `МашиночитаемыеДоверенности` (without `ФНС`) — does not exist. The real
  modules are `МашиночитаемыеДоверенностиФНС*` (with the `ФНС` suffix).
- `ДобавитьПодписьКФайлуДоверенности` returns `Истина` on success or
  a **string** with the error text on failure — check `ТипЗнч(Результат) =
  Тип("Булево")`, not just `Если Результат Тогда`.

## Rare methods

Additional stable methods, full signatures — via
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `ЭлектроннаяПодпись.СертификатИзДвоичныхДанныхПодписи(Подпись)` — extract
  the certificate from the signature (ДвоичныеДанные).
- `ЭлектроннаяПодпись.ОтпечаткиСертификатов(ТолькоЛичные, ОписаниеОшибки = Null,
  Сервис = Истина)` — an array of fingerprints of available certificates (server).
- `ЭлектроннаяПодпись.ПолучитьСертификатПоОтпечатку(Отпечаток, ТолькоВЛичномХранилище)`
  — `СертификатКриптографии` by fingerprint.
- `ЭлектроннаяПодпись.ЗаписатьСертификатВСправочник(Знач Сертификат,
  ДополнительныеПараметры = Неопределено)` — server-side write of the certificate to
  the directory (client-side analogue — `ЭлектроннаяПодписьКлиент.ЗаписатьСертификатВСправочник`).
- `ЭлектроннаяПодпись.СсылкаНаСертификат(Знач Сертификат)` /
  `ЭлектроннаяПодпись.СсылкиНаСертификаты(Знач Сертификаты,
  Знач ВозвращатьНесуществующие = Ложь)` — reference/references to the certificate
  directory by binary data.
- `ЭлектроннаяПодпись.ШтампВизуализацииЭлектроннойПодписи(Знач Подпись,
  Знач ДатаПодписи = Неопределено, Знач ТекстОтметки = "",
  Знач ЛоготипОрганизации = Неопределено)` — signature visualization stamp for
  a printed form.
- `ЭлектроннаяПодпись.ПроверитьУстановкуПрограммКриптографии(
  ПараметрыПроверки = Неопределено)` — check installation of signature software.
- `ЭлектроннаяПодпись.РезультатПроверкиУдостоверяющегоЦентраСертификата(
  Сертификат, НаДату = Неопределено, ПараметрыПроверки = Неопределено)` —
  result of CA certificate verification.
- `ЭлектроннаяПодписьКлиент.ОтправитьНаПодписание(ОписаниеДанных, Форма =
  Неопределено, ОбработкаРезультата = Неопределено, ПараметрыПодписи =
  Неопределено)` — send for remote signing (DSS).
- `ЭлектроннаяПодписьКлиент.ПроверитьСертификат(Оповещение, Сертификат,
  МенеджерКриптографии = Неопределено, НаДату = Неопределено,
  ПараметрыПроверки = Неопределено)` — asynchronous certificate verification (client).
- `ЭлектроннаяПодписьКлиентСервер.РезультатПроверкиПодписи()` — constructor
  of the detailed verification result structure; pass it to
  `ЭлектроннаяПодпись.ПроверитьПодпись(..., РезультатСтруктура)`.
- `МашиночитаемыеДоверенностиФНС.СоздатьИзменитьМашиночитаемуюДоверенность(
  Доверенность, ДанныеЗаполнения)` — server-side creation/modification of МЧД
  (programmatically, without a form).
- `МашиночитаемыеДоверенностиФНС.ФайлыДоверенности(Знач Доверенность,
  Знач ДляНалоговыхОрганов)` — power of attorney files (for ФНС / for representation).
- `МашиночитаемыеДоверенностиФНС.УстановитьСтатусРегистрации(Доверенность,
  ИдентификаторТранзакции = Неопределено, ЭтоОтмена = Ложь)` — set the registration
  status in the ФНС registry.
- `МашиночитаемыеДоверенностиФНС.ПрочитатьСостояниеМЧД(Доверенность)` —
  MЧД state (status, errors).

⚠️ Service methods `ЭлектроннаяПодписьСлужебный` (region
`СлужебныйПрограммныйИнтерфейс`, backward compatibility is not guaranteed):
- `Зашифровать(Данные, Сертификат, МенеджерКриптографии)` — server-side
  encryption; the third parameter is the **cryptography manager**, not an algorithm string.
  ⚠️ Collision: in the main module `ЭлектроннаяПодпись` there is a method with the same name
  `Зашифровать(Данные, Сертификат, АлгоритмШифрования = "")` — there the third parameter
  is a string; distinguish by module when calling (`--module` in `bsp_api.py`).
- `ДоступнаЭлектроннаяПодпись(ТипОбъекта)` — `Булево`, checks for the presence of
  the `ПодписанЭП` attribute on the type (via `ОпределяемыйТип.ПодписанныйОбъект`).

⚠️ Related service methods in the main module `ЭлектроннаяПодпись` (region
`СлужебныйПрограммныйИнтерфейс` — not in `…Служебный`):
- `ПодписьВКодировкеDER(ДанныеПодписи)` — signature from binary data in DER encoding
  (`ДвоичныеДанные` / `Строка`-address).
- `РасшифровкаДанных() Экспорт` — ⚠️ **NOT a decryption method**. A parameterless
  function that returns `Булево`: a flag indicating availability of the "РасшифровкаДанных" role
  (`ИспользоватьШифрование() И Пользователи.РолиДоступны("РасшифровкаДанных")`).
  Actual decryption is performed by the `Расшифровать` methods (see below).

Decryption methods `Расшифровать` (exported):
- `ЭлектроннаяПодписьКлиент.Расшифровать(ОписаниеДанных, Форма = Неопределено,
  ОбработкаРезультата = Неопределено)` — client-side, asynchronous.
- `СервисКриптографии.Расшифровать(ЗашифрованныеДанные, Сертификат,
  ТипШифрования = "CMS", ПараметрыШифрования = Неопределено)` — server-side.

To search for the signature/region of any method —
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`. 