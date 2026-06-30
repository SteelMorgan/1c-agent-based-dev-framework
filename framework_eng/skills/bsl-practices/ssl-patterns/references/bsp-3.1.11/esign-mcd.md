# BSP Electronic Signature and Machine-Readable Powers of Attorney

Subsystems **ЭлектроннаяПодпись** (signing, verification, certificates) and
**МашиночитаемыеДоверенности** (MChD - Federal Tax Service register). Covers
the stable server/client API, certificate selection, signature and certificate
verification, signature enhancement, and MChD operations.

> ⚠️ **Two calling models.** Client (`ЭлектроннаяПодписьКлиент`) - asynchronous,
> uses `ОписаниеОповещения`, opens forms. Server (`ЭлектроннаяПодпись`) -
> synchronous, returns the value directly. Server-side `ЭлектроннаяПодпись.Подписать`
> **does not exist** - signing is client-side only; the server only saves the
> result (`ДобавитьПодпись`), reads signatures (`ПодписиОбъекта`), and verifies
> (`ПроверитьПодпись`, `ПроверитьСертификат`).

## Modules

The `ЭлектроннаяПодпись*` family follows the BSP suffix scheme:

- `ЭлектроннаяПодпись` - server: operations on reference objects, signature
  registers, verification, cryptography manager. Stable API (60 exports in
  `ПрограммныйИнтерфейс`).
- `ЭлектроннаяПодписьКлиент` - client: interactive scenarios - signing form,
  certificate selection, dialogs, asynchronous operations through
  `ОписаниеОповещения`.
- `ЭлектроннаяПодписьКлиентСервер` - shared structures:
  `НовыеСвойстваПодписи`, `РезультатПроверкиПодписи`.
- `ЭлектроннаяПодписьСлужебный` - ⚠️ service (`СлужебныйПрограммныйИнтерфейс`):
  `Зашифровать(Данные, Сертификат, МенеджерКриптографии)`,
  `ДоступнаЭлектроннаяПодпись`. Backward compatibility is not guaranteed. A
  number of related service methods (`ПодписьВКодировкеDER`,
  `РасшифровкаДанных`) live in the main `ЭлектроннаяПодпись` module (also
  `СлужебныйПрограммныйИнтерфейс`) - not in `Служебный`.
- `ЭлектроннаяПодписьПереопределяемый` / `ЭлектроннаяПодписьКлиентПереопределяемый`
  - hooks (implement, do not call): `ПередНачаломОперации`, and so on.
- `ЭлектроннаяПодписьЛокализация` / `*КлиентЛокализация` /
  `*КлиентСерверЛокализация` - regional overrides (Russia-specific MChD
  behavior).
- `ЭлектроннаяПодписьВМоделиСервиса*` - data separation in SaaS.

MChD is a **separate family with the `ФНС` suffix**:

- `МашиночитаемыеДоверенностиФНС` - server MChD API: creation, signing the
  power-of-attorney file, verification, signature verification result by MChD.
- `МашиночитаемыеДоверенностиФНСКлиент` - client:
  `ОткрытьСписокМЧД`, `СоздатьМЧД`, `ПроверитьДоверенность`.
- `МашиночитаемыеДоверенностиФНСПереопределяемый` /
  `*КлиентПереопределяемый` - hooks.
- ⚠️ The `МашиночитаемыеДоверенности` module (without `ФНС`) **does not exist** -
  a typical mistake by analogy with `УправлениеДоступом`. The real name has the
  `ФНС` suffix.

DSS (digital signature service, CryptoPro DSS and similar) is a separate family
of `СервисКриптографииDSS*` and `СервисМобильнойПодписи*` modules
(`ЭлектроннаяПодписьСервисаDSS` subsystem). This file covers DSS briefly - the
main path goes through `ЭлектроннаяПодписьКлиент.Подписать`; DSS-specific
functions are called directly through `СервисКриптографииDSS*` /
`СервисМобильнойПодписи*`.

> ⚠️ **`ПроверитьПодпись` exists in 6 modules with DIFFERENT signatures**:
> `ЭлектроннаяПодпись` (server, synchronous, by cryptography manager),
> `ЭлектроннаяПодписьКлиент` (client, asynchronous, via `ОписаниеОповещения`),
> `СервисКриптографии` / `СервисКриптографииКлиент` (shared cryptography
> manager), `СервисКриптографииDSS` / `СервисКриптографииDSSКлиент` (DSS). For
> application code, the canonical calls are `ЭлектроннаяПодпись.ПроверитьПодпись`
> (server) and `ЭлектроннаяПодписьКлиент.ПроверитьПодпись` (client). **Always
> specify `--module`** when looking up a signature.

## Scenarios

### 1. Sign data or an object (client, asynchronous)

**Task:** sign binary data, a file, or a reference object with a selected
certificate through the standard form; when an object is specified, write the
signature into the infobase.

**Function:**
`ЭлектроннаяПодписьКлиент.Подписать(ОписаниеДанных, Форма = Неопределено, ОбработкаРезультата = Неопределено, ПараметрыПодписи = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client (Thin,
Thick). The result is returned through `ОбработкаРезультата` (asynchronously).

**Parameters:**
- `ОписаниеДанных` (Structure) - keys:
  - `Операция` (String) - signing form title (for example,
    `НСтр("ru = 'Подписание документа'")`).
  - `ЗаголовокДанных` (String) - data title (`"Документ"`, `"Файл"`).
  - `Данные` (`ДвоичныеДанные` / `Строка` address / `ОписаниеОповещения` / structure
    `ПараметрыCMS` + `Данные`) - data to sign.
  - `Объект` (`ЛюбаяСсылка`, optional) - reference to attach the signature to;
    if specified, the server side will call `ДобавитьПодпись` itself and set
    `ПодписанЭП = Истина`.
  - `ВерсияОбъекта` (String, optional) - version for verification/locking.
  - `ПоказатьКомментарий` (Boolean, optional) - allow comment entry in the form.
  - `ОтборСертификатов` (Array, optional) - references to
    `СертификатыЭлектроннойПодписиИШифрования` to filter the selection list.
  - `ВыбраннаяДоверенность`
    (`СправочникСсылка.МашиночитаемыеДоверенности`, optional) - MChD for
    signing on behalf of a representative.
  - `ВыполнятьНаСервере` (Boolean / `Неопределено`, optional) - `Неопределено`
    = server first, client if it fails; `Истина` = server only; `Ложь` = client
    only.
- `Форма` (`ФормаКлиентскогоПриложения` / `УникальныйИдентификатор` /
  `Неопределено`) - form used to lock the object; `Неопределено` = standard form.
- `ОбработкаРезультата` (`ОписаниеОповещения`) - result handler; receives
  `ОписаниеДанных` supplemented with `Успех` (Boolean), `Отказ` (Boolean),
  `СвойстваПодписи` (Structure/address), and `ВыбранныйСертификат`.
- `ПараметрыПодписи` (see `ЭлектроннаяПодписьКлиент.НовыйТипПодписи`) -
  signature type.

**Example:**
```bsl
// &НаКлиенте - command "Подписать"
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
        ЭтаФорма.Прочитать();  // server side has already written the signature to the object
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ `ЭлектроннаяПодпись.Подписать(...)` from server-side code - the method does
  not exist on the server (compile error). Signing is initiated by the client;
  the server only saves the result through `ДобавитьПодпись` (called inside
  `Подписать` when `Объект` is specified).
- ❌ Calling client API from `&НаСервере` - `ЭлектроннаяПодписьКлиент` does not
  compile on the server.
- Before calling, check subsystem availability:
  `ОбщегоНазначения.ПодсистемаСуществует("СтандартныеПодсистемы.ЭлектроннаяПодпись")`
  and `ЭлектроннаяПодпись.ИспользоватьЭлектронныеПодписи()`.

### 2. Verify a signature on the server (background job / processing)

**Task:** programmatically verify the validity of a signature and certificate on
the server (scheduled job, processing), capturing the error description.

**Functions:**
`ЭлектроннаяПодпись.МенеджерКриптографии(Операция, ПоказатьОшибку = Истина, ОписаниеОшибки = "", Программа = Неопределено) Экспорт`
— Function → `МенеджерКриптографии` / `Неопределено`, region
`ПрограммныйИнтерфейс` (stable). Server.
`ЭлектроннаяПодпись.ПроверитьПодпись(МенеджерКриптографии, ИсходныеДанные, Подпись, ОписаниеОшибки = Null, НаДату = Неопределено, РезультатСтруктура = Неопределено) Экспорт`
— Function → `Булево`, region `ПрограммныйИнтерфейс` (stable). Server.
`ЭлектроннаяПодпись.ПроверитьСертификат(МенеджерКриптографии, Сертификат, ОписаниеОшибки = Null, НаДату = Неопределено, ПараметрыПроверки = Неопределено) Экспорт`
— Function → `Булево`, region `ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Операция` (String) - `"Подписание"`, `"ПроверкаПодписи"`, `"Шифрование"`,
  `"Расшифровка"`, `"ПроверкаСертификата"`, `"ПолучениеСертификатов"` (inserted
  into the error text).
- `ПоказатьОшибку` (Boolean) - `Истина` = raise an exception on failure;
  `Ложь` = return `Неопределено` and fill `ОписаниеОшибки`.
- `ОписаниеОшибки` (String) - output, filled when `Неопределено` is returned.
- `Программа`
  (`СправочникСсылка.ПрограммыЭлектроннойПодписиИШифрования` / `Неопределено`)
  - `Неопределено` = first program from the directory.
- `ИсходныеДанные` (`ДвоичныеДанные` / `Строка` address / envelope structure) -
  signed data.
- `Подпись` (`ДвоичныеДанные` / `Строка` address) - signature to verify.
- `ОписаниеОшибки` (String, output, default `Null`) - filled only on failure.
- `НаДату` (`Дата` / `Неопределено`) - certificate verification date;
  `Неопределено` = date from the signature, otherwise the session date.
- `РезультатСтруктура` (Structure, optional) - if you pass a structure (from
  `ЭлектроннаяПодписьКлиентСервер.РезультатПроверкиПодписи()`), the result is
  filled in detail (error categories, statuses).

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
- ❌ Ignoring `ОписаниеОшибки` (4th parameter) - a Boolean does not provide a
  reason for the user/log. Always pass an output string and log on failure.
- ❌ Creating `Новый МенеджерКриптографии("Crypto-Pro GOST R 34.10-2012", "", 75)`
  bypassing `ЭлектроннаяПодпись.МенеджерКриптографии` - this breaks subsystem
  integration (program settings, logging, certificate notifications). Use the
  BSP wrapper.
- A certificate is always verified on the server if the administrator configured
  signature verification on the server
  (`ЭлектроннаяПодпись.ПроверятьЭлектронныеПодписиНаСервере()`).
- For a detailed result, pass the structure from
  `ЭлектроннаяПодписьКлиентСервер.РезультатПроверкиПодписи()` into
  `РезультатСтруктура`.

### 3. Write, update, and delete an object signature

**Task:** save a signature for a reference object (setting `ПодписанЭП`), update
the properties of an already saved signature, delete a signature.

**Functions:**
`ЭлектроннаяПодпись.ДобавитьПодпись(Объект, Знач СвойстваПодписи, ИдентификаторФормы = Неопределено, ВерсияОбъекта = Неопределено, ЗаписанныйОбъект = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Server.
`ЭлектроннаяПодпись.ОбновитьПодпись(Объект, Знач СвойстваПодписи, ОбновитьПоПорядковомуНомеру = Ложь) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Server.
`ЭлектроннаяПодпись.УдалитьПодпись(Объект, ПорядковыйНомер, ИдентификаторФормы = Неопределено, ВерсияОбъекта = Неопределено, ЗаписанныйОбъект = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Объект` (`ОпределяемыйТип.ПодписанныйОбъект` - reference or object) - must
  have the `ПодписанЭП` attribute. For a reference: the object is locked,
  modified, and written. If you pass the **object** (not the reference), it is
  modified without locking and without writing (the caller writes it themselves).
- `СвойстваПодписи` (`Строка` address / `Структура` / Array) - structure from
  `ЭлектроннаяПодписьКлиентСервер.НовыеСвойстваПодписи()` (fingerprint, date,
  certificate, status, signature type, signature).
- `ИдентификаторФормы` (`УникальныйИдентификатор`) - for locking the object;
  from the form: `ЭтаФорма.УникальныйИдентификатор`.
- `ВерсияОбъекта` (String, optional) - data version for verification and locking.
- `ПорядковыйНомер` (Number) - signature sequence number (for
  `УдалитьПодпись` and `ОбновитьПодпись` when
  `ОбновитьПоПорядковомуНомеру = Истина`).
- `ЗаписанныйОбъект` (object, optional) - already written object (to avoid
  rereading).

**Example:**
```bsl
// In your own server-side processing (outside client-side Подписать)
СвойстваПодписи = ЭлектроннаяПодписьКлиентСервер.НовыеСвойстваПодписи();
СвойстваПодписи.Отпечаток = ОтпечатокСертификата;
СвойстваПодписи.Подпись   = ДвоичныеДанныеПодписи;
СвойстваПодписи.Сертификат = ДвоичныеДанныеСертификата;
// ... other properties ...

ЭлектроннаяПодпись.ДобавитьПодпись(Объект.Ссылка, СвойстваПодписи, УИДФормы);

// Update signature properties (for example, after enhancement) by sequence number
ЭлектроннаяПодпись.ОбновитьПодпись(Объект.Ссылка, НовыеСвойства, Истина);

// Delete signature #2
ЭлектроннаяПодпись.УдалитьПодпись(Объект.Ссылка, 2, УИДФормы);
```

**Nuances / anti-patterns:**
- ❌ Writing the signature directly into `РегистрСведений.ЭлектронныеПодписи`
  through a record set - this bypasses `ПодписанЭП`, locking, and notifications.
  Use only `ДобавитьПодпись` / `ОбновитьПодпись` / `УдалитьПодпись`.
- `ДобавитьПодпись` locks, modifies, and writes the object by reference itself.
  If you already hold the object and write it yourself, pass the object (not the
  reference), and BSP will modify it without writing.
- ❌ `ЭлектроннаяПодпись.УстановленныеПодписи(...)` - **deprecated**
  (`УстаревшиеПроцедурыИФункции`), marked `// Obsolete. Use
  ПодписиОбъекта`. Do not use in new code.

### 4. Get object signatures and their properties

**Task:** read the list of installed object signatures (properties, fingerprints,
statuses) for a UI form or a report.

**Functions:**
`ЭлектроннаяПодпись.ПодписиОбъекта(Объект, ДополнительныеПараметры = Неопределено) Экспорт`
— Function → Array of signature property structures, region
`ПрограммныйИнтерфейс` (stable). Server.
`ЭлектроннаяПодпись.СвойстваПодписи(Подпись, ПрочитатьСертификаты = Истина) Экспорт`
— Function → Structure, region `ПрограммныйИнтерфейс` (stable). Server.
`ЭлектроннаяПодпись.ДатаПодписания(Подпись, ПривестиКЧасовомуПоясуСеанса = Истина) Экспорт`
— Function → `Дата` / `Неопределено`, region `ПрограммныйИнтерфейс` (stable).
Server.
`ЭлектроннаяПодписьКлиентСервер.НовыеСвойстваПодписи() Экспорт` — Function →
signature property constructor structure, region `ПрограммныйИнтерфейс`
(stable). Client + Server.

**Parameters:**
- `Объект` (`ОпределяемыйТип.ПодписанныйОбъект`) - reference; must have the
  `ПодписанЭП` attribute.
- `ДополнительныеПараметры` (see
  `ЭлектроннаяПодпись.НовыйПараметрыПолученияПодписейОбъекта()`) - optional
  filter/retrieval settings.
- `Подпись` (`ДвоичныеДанные` / `Строка` address) - signature for property /
  date parsing.
- `ПрочитатьСертификаты` (Boolean) - `Истина` = read certificates from the
  signature.

**Example:**
```bsl
Подписи = ЭлектроннаяПодпись.ПодписиОбъекта(ДокументСсылка);
Для Каждого СвойстваПодписи Из Подписи Цикл
    // СвойстваПодписи.Отпечаток, .ДатаПодписи, .Сертификат, .ТипПодписи, .СтатусПроверки
    Сообщить(СвойстваПодписи.Отпечаток + " от " + СвойстваПодписи.ДатаПодписи);
КонецЦикла;

// Extract properties and the date directly from the binary signature data
Св = ЭлектроннаяПодпись.СвойстваПодписи(ДвоичныеДанныеПодписи);
ДатаПодписи = ЭлектроннаяПодпись.ДатаПодписания(ДвоичныеДанныеПодписи);
```

**Nuances / anti-patterns:**
- ⚠️ `ДатаПодписания` exists in **two** modules with different signatures:
  `ЭлектроннаяПодпись.ДатаПодписания(Подпись, ПривестиКЧасовомуПоясуСеанса = Истина)`
  - server, Function (returns `Дата`); and
  `ЭлектроннаяПодписьКлиент.ДатаПодписания(Оповещение, Подпись,
  ПривестиКЧасовомуПоясуСеанса = Истина)` - client, Procedure (result through
  `Оповещение`). Specify the module via `--module`.
- ❌ `УстановленныеПодписи(...)` - deprecated, use `ПодписиОбъекта`.

### 5. Enhance a signature to qualified/archive

**Task:** increase the signature type (for example, to CAdES-T/A) - for either
an individual signature or a signature already saved on an object.

**Functions:**
`ЭлектроннаяПодпись.УсовершенствоватьПодпись(Подпись, ТипПодписи, ДобавитьАрхивнуюМеткуВремени = Ложь, ДополнительныеПараметры = Неопределено) Экспорт`
— Function → structure of changed properties, region `ПрограммныйИнтерфейс`
(stable). Server.
`ЭлектроннаяПодпись.УсовершенствоватьПодписьОбъекта(ПодписанныйОбъект, ПорядковыйНомер, ТипПодписи, ДобавитьАрхивнуюМеткуВремени = Ложь, ИдентификаторФормы = Неопределено, ДополнительныеПараметры = Неопределено) Экспорт`
— Function, region `ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Подпись` (`ДвоичныеДанные`) - signature to enhance.
- `ТипПодписи` (`ПеречислениеСсылка.ТипыПодписиКриптографии`) - target type. If
  the actual type is already higher, nothing is done.
- `ДобавитьАрхивнуюМеткуВремени` (Boolean) - `Истина` = add a timestamp to the
  archive signature (CAdES-A).
- `ПодписанныйОбъект` (`ОпределяемыйТип.ПодписанныйОбъект`) - the object whose
  signature is being enhanced.
- `ПорядковыйНомер` (Number) - object signature sequence number.
- `ИдентификаторФормы` (`УникальныйИдентификатор`) - for locking.
- `ДополнительныеПараметры` (Structure) - `МенеджерКриптографии`
  (`Неопределено` / `МенеджерКриптографии`),
  `ИгнорироватьСрокДействияСертификата` (Boolean).

**Example:**
```bsl
// Enhance object signature #1 to archive
ДопПараметры = Новый Структура;
ДопПараметры.Вставить("ИгнорироватьСрокДействияСертификата", Ложь);
ЭлектроннаяПодпись.УсовершенствоватьПодписьОбъекта(
    ДокументСсылка, 1, Перечисления.ТипыПодписиКриптографии.Архивная, Истина, , ДопПараметры);

// Enhance a "bare" signature (binary data)
ИзмененныеСвойства = ЭлектроннаяПодпись.УсовершенствоватьПодпись(
    ДвоичныеПодписи, Перечисления.ТипыПодписиКриптографии.Усиленная);
```

**Nuances / anti-patterns:**
- ❌ `ЭлектроннаяПодпись.ДоступнаУсовершенствованнаяПодпись()` - **deprecated**
  (`УстаревшиеПроцедурыИФункции`). Do not use in new code; the target signature
  type is specified by the `ТипПодписи` parameter.
- `УсовершенствоватьПодпись` returns only the **changed** properties - not the
  full set. If the type is already above the target one, the structure is empty
  and nothing is done.

### 6. Register a certificate and set a password (client)

**Task:** open the form for adding a user certificate to the
`СертификатыКлючейЭлектроннойПодписиИШифрования` directory; set the certificate
password for the session.

**Functions:**
`ЭлектроннаяПодписьКлиент.ДобавитьСертификат(ОбработчикЗавершения = Неопределено, ПараметрыДобавления = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Client.
`ЭлектроннаяПодписьКлиент.ПараметрыДобавленияСертификата() Экспорт` — Function
→ Structure, region `ПрограммныйИнтерфейс` (stable). Client.
`ЭлектроннаяПодписьКлиент.УстановитьПарольСертификата(СертификатСсылка, Пароль, ПояснениеПароля = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Client.
`ЭлектроннаяПодписьКлиент.ПолучитьОтпечаткиСертификатов(Оповещение, ТолькоЛичные, ПараметрыПолучения = Истина) Экспорт`
— Procedure (asynchronous), region `ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `ОбработчикЗавершения` (`ОписаниеОповещения`) - called after adding.
- `ПараметрыДобавления` (Structure from `ПараметрыДобавленияСертификата`) -
  `Комментарий`, `ДляШифрования`, and so on.
- `СертификатСсылка`
  (`СправочникСсылка.СертификатыКлючейЭлектроннойПодписиИШифрования`) - the
  certificate whose password is remembered.
- `Пароль` (String) - certificate password (stored only for the session).
- `Оповещение` (`ОписаниеОповещения`) - returns an array of fingerprint strings.
- `ТолькоЛичные` (Boolean) - `Истина` = personal user certificates,
  `Ложь` = all.
- `ПараметрыПолучения` (Boolean / structure from
  `ПараметрыПолученияОтпечатковСертификатов`) - `Истина` = default values
  (client + server + service).

**Example:**
```bsl
// &НаКлиенте - add certificate
Параметры = ЭлектроннаяПодписьКлиент.ПараметрыДобавленияСертификата();
Параметры.Комментарий = НСтр("ru = 'Сертификат для подписания ЭП'");
Обработчик = Новый ОписаниеОповещения("ПослеДобавленияСертификата", ЭтотОбъект);
ЭлектроннаяПодписьКлиент.ДобавитьСертификат(Обработчик, Параметры);

// Remember the certificate password for the session (to avoid asking on each signing)
ЭлектроннаяПодписьКлиент.УстановитьПарольСертификата(СертификатСсылка, ВведенныйПароль);
```

**Nuances / anti-patterns:**
- ❌ Store the certificate password in attributes/constants in plain text. If
  long-term storage is required, use only secure storage
  (`ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище` - see
  `base-common.md`), and read it right before signing, without writing it to the
  infobase.
- `УстановитьПарольСертификата` is effective **only for the current session** -
  on the next start, the password must be entered again (or stored in secure
  storage).

### 7. Create an MChD, verify a power of attorney, and verify a signature by MChD

**Task:** open the form for creating a machine-readable power of attorney;
verify the power of attorney (including in the FNS register); verify a saved
object signature against the MChD (signer = representative, powers, date).

**Functions:**
`МашиночитаемыеДоверенностиФНСКлиент.СоздатьМЧД(ПараметрыФормы, ОповещениеОЗавершении = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Client (Thin, Thick).
`МашиночитаемыеДоверенностиФНСКлиент.ПроверитьДоверенность(Оповещение, Доверенность, ИдентификаторФормы = Неопределено) Экспорт`
— Procedure (asynchronous), region `ПрограммныйИнтерфейс` (stable). Client.
`МашиночитаемыеДоверенностиФНСКлиент.ОткрытьСписокМЧД(Отборы = Неопределено, ОповещениеОЗакрытии = Неопределено, Владелец = Неопределено) Экспорт`
— Procedure, region `ПрограммныйИнтерфейс` (stable). Client.
`МашиночитаемыеДоверенностиФНС.РезультатПроверкиДоверенности(Доверенность, ПроверятьВРеестреФНС = Неопределено) Экспорт`
— Function → Structure, region `ПрограммныйИнтерфейс` (stable). Server.
`МашиночитаемыеДоверенностиФНС.РезультатПроверкиПодписиПоМЧД(ПодписанныйОбъект, ИдентификаторПодписи, СертификатПодписи, НаДату) Экспорт`
— Function → Array of Structure, region `ПрограммныйИнтерфейс` (stable).
Server.
`МашиночитаемыеДоверенностиФНС.ДобавитьПодписьКФайлуДоверенности(ФайлДоверенности, Знач Подпись) Экспорт`
— Function → `Булево` (`Истина`) or String (error text), region
`ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ПараметрыФормы` (see
  `МашиночитаемыеДоверенностиФНСКлиент.ПараметрыСозданияМЧД()`) - creation form
  parameters.
- `Оповещение` / `ОповещениеОЗавершении` (`ОписаниеОповещения`) - result
  handler.
- `Доверенность` (`СправочникСсылка.МашиночитаемыеДоверенности`) - power of
  attorney to verify.
- `ПроверятьВРеестреФНС` (Boolean / `Неопределено`) - `Неопределено` =
  depending on the `РегистрироватьВРеестре` flag.
- `ПодписанныйОбъект` (`ОпределяемыйТип.ПодписанныйОбъект`) - the object whose
  signature is checked against the MChD.
- `ИдентификаторПодписи` (`УникальныйИдентификатор`) - signature identifier
  from `НовыеСвойстваПодписи.ИдентификаторПодписи`.
- `СертификатПодписи` (`СертификатКриптографии` / `ДвоичныеДанные` / `Строка`
  address) - signer certificate.
- `НаДату` (`Дата`) - signature date; if not set, verification is performed at
  the session date.
- `ФайлДоверенности` (reference to an attached MChD file) - file to which the
  signature is added.
- `Подпись` (signature properties structure) - principal's signature.

**Example:**
```bsl
// Client: open the MChD creation form
Параметры = МашиночитаемыеДоверенностиФНСКлиент.ПараметрыСозданияМЧД();
МашиночитаемыеДоверенностиФНСКлиент.СоздатьМЧД(Параметры,
    Новый ОписаниеОповещения("ПослеСозданияМЧД", ЭтотОбъект));

// Server: verify the power of attorney (including in the FNS register)
Результат = МашиночитаемыеДоверенностиФНС.РезультатПроверкиДоверенности(МЧДСсылка);
Если Результат.Верна Тогда
    // power-of-attorney signatures are valid and match the principals
Иначе
    Сообщить(Результат.ТекстОшибки);
КонецЕсли;

// Server: verify an object signature against the MChD (array!)
РезультатМЧД = МашиночитаемыеДоверенностиФНС.РезультатПроверкиПодписиПоМЧД(
    ДокументСсылка, ИдентификаторПодписи, СертификатПодписи, ДатаПодписи);
Для Каждого СтрокаРезультата Из РезультатМЧД Цикл
    Если СтрокаРезультата.Верна Тогда
        // signature is valid + powers match
    Иначе
        // СтрокаРезультата.ПротоколПроверки - detailed breakdown
    КонецЕсли;
КонецЦикла;
```

**Nuances / anti-patterns:**
- ❌ Calling `.Верна` directly on `РезультатПроверкиПодписиПоМЧД(...)` (as in
  older examples) - the method returns an **Array** of Structure, not a single
  structure. Iterate over the array:
  `Для Каждого СтрокаРезультата Из РезультатМЧД`. Each row contains
  `МашиночитаемаяДоверенность`, `Верна`, `ТребуетсяПроверка`,
  `ПодписантСоответствуетПредставителю`, `СовместныеПолномочия`,
  `ПротоколПроверки`.
- ❌ The `МашиночитаемыеДоверенности` module (without `ФНС`) does not exist.
  The real modules are `МашиночитаемыеДоверенностиФНС*` (with the `ФНС`
  suffix).
- `ДобавитьПодписьКФайлуДоверенности` returns `Истина` on success or a **string**
  with an error message on failure - check `ТипЗнч(Результат) = Тип("Булево")`,
  not just `Если Результат Тогда`.

## Rare methods

Additional stable methods, full signatures via
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `ЭлектроннаяПодпись.СертификатИзДвоичныхДанныхПодписи(Подпись)` - extract a
  certificate from a signature (binary data).
- `ЭлектроннаяПодпись.ОтпечаткиСертификатов(ТолькоЛичные, ОписаниеОшибки = Null,
  Сервис = Истина)` - array of fingerprints of available certificates (server).
- `ЭлектроннаяПодпись.ПолучитьСертификатПоОтпечатку(Отпечаток, ТолькоВЛичномХранилище)`
  - `СертификатКриптографии` by fingerprint.
- `ЭлектроннаяПодпись.ЗаписатьСертификатВСправочник(Знач Сертификат,
  ДополнительныеПараметры = Неопределено)` - server-side certificate write to
  the directory (client analog -
  `ЭлектроннаяПодписьКлиент.ЗаписатьСертификатВСправочник`).
- `ЭлектроннаяПодпись.СсылкаНаСертификат(Знач Сертификат)` /
  `ЭлектроннаяПодпись.СсылкиНаСертификаты(Знач Сертификаты,
  Знач ВозвращатьНесуществующие = Ложь)` - reference/references to the
  certificate directory by binary data.
- `ЭлектроннаяПодпись.ШтампВизуализацииЭлектроннойПодписи(Знач Подпись,
  Знач ДатаПодписи = Неопределено, Знач ТекстОтметки = "",
  Знач ЛоготипОрганизации = Неопределено)` - signature visualization stamp for
  a print form.
- `ЭлектроннаяПодпись.ПроверитьУстановкуПрограммКриптографии(
  ПараметрыПроверки = Неопределено)` - check cryptography software installation.
- `ЭлектроннаяПодпись.РезультатПроверкиУдостоверяющегоЦентраСертификата(
  Сертификат, НаДату = Неопределено, ПараметрыПроверки = Неопределено)` -
  certificate CA verification result.
- `ЭлектроннаяПодписьКлиент.ОтправитьНаПодписание(ОписаниеДанных, Форма =
  Неопределено, ОбработкаРезультата = Неопределено, ПараметрыПодписи =
  Неопределено)` - send for remote signing (DSS).
- `ЭлектроннаяПодписьКлиент.ПроверитьСертификат(Оповещение, Сертификат,
  МенеджерКриптографии = Неопределено, НаДату = Неопределено,
  ПараметрыПроверки = Неопределено)` - asynchronous certificate verification
  (client).
- `ЭлектроннаяПодписьКлиентСервер.РезультатПроверкиПодписи()` - constructor for
  the detailed verification result structure; pass it to
  `ЭлектроннаяПодпись.ПроверитьПодпись(..., РезультатСтруктура)`.
- `МашиночитаемыеДоверенностиФНС.СоздатьИзменитьМашиночитаемуюДоверенность(
  Доверенность, ДанныеЗаполнения)` - server-side creation/update of MChD
  (programmatically, without a form).
- `МашиночитаемыеДоверенностиФНС.ФайлыДоверенности(Знач Доверенность,
  Знач ДляНалоговыхОрганов)` - power-of-attorney files (for the FNS / for
  submission).
- `МашиночитаемыеДоверенностиФНС.УстановитьСтатусРегистрации(Доверенность,
  ИдентификаторТранзакции = Неопределено, ЭтоОтмена = Ложь)` - set the FNS
  register status.
- `МашиночитаемыеДоверенностиФНС.ПрочитатьСостояниеМЧД(Доверенность)` - MChD
  state (status, errors).

⚠️ Service methods `ЭлектроннаяПодписьСлужебный` (region
`СлужебныйПрограммныйИнтерфейс`, backward compatibility is not guaranteed):
- `Зашифровать(Данные, Сертификат, МенеджерКриптографии)` - server encryption;
  the third parameter is a **cryptography manager**, not a string algorithm.
  ⚠️ Collision: the main `ЭлектроннаяПодпись` module has a method with the same
  name `Зашифровать(Данные, Сертификат, АлгоритмШифрования = "")` - there the
  third parameter is a string; distinguish them by module when calling (`--module`
  in `bsp_api.py`).
- `ДоступнаЭлектроннаяПодпись(ТипОбъекта)` - `Булево`, check for the
  `ПодписанЭП` attribute on a type (through `ОпределяемыйТип.ПодписанныйОбъект`).

⚠️ Related service methods in the main `ЭлектроннаяПодпись` module (region
`СлужебныйПрограммныйИнтерфейс` - not in `…Служебный`):
- `ПодписьВКодировкеDER(ДанныеПодписи)` - signature from binary data in DER
  encoding (`ДвоичныеДанные` / `Строка` address).
- `РасшифровкаДанных(Данные, Сертификат, МенеджерКриптографии)` - decryption;
  returns `ДвоичныеДанные` / `Неопределено`.

To look up the signature/region of any method -
`python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`.
