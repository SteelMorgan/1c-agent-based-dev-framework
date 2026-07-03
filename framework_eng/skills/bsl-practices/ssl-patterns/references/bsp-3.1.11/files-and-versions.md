# Files and Object Versioning in BSP

Subsystems **РаботаСФайлами** (attached files - scans, images, PDF,
storage in the database and volumes), **ВерсионированиеОбъектов** (change history
for catalogs and documents, version reports) and **ВыгрузкаОбъектовВФайлы**
(saving objects/print forms to XML/JSON/Excel files). All three surface
in object forms through standard commands and hyperlinks, so their common
modules appear in every document and catalog of a standard configuration.

## Modules

BSP suffix naming scheme (one root + execution context):

- `РаботаСФайлами` - server API: create, update, copy,
  read attached files.
- `РаботаСФайламиКлиент` - client API: dialogs, opening forms, scanning.
- `РаботаСФайламиКлиентСервер` - shared code (parameter structures, command names).
- `РаботаСФайламиПереопределяемый` / `РаботаСФайламиКлиентПереопределяемый` -
  override hooks (BSP calls them, application code implements them; not called
  directly).
- Service: `РаботаСФайламиСлужебный*` (⚠️ backward compatibility is not
  guaranteed) - do not use as the primary interface.
- `ВерсионированиеОбъектов` - server API: settings, reading versions,
  report generation.
- `ВерсионированиеОбъектовКлиент` - client API: reports by version/
  comparison, opening history.
- `ВерсионированиеОбъектовПереопределяемый` - hooks (do not call).
- `ВерсионированиеОбъектовСобытия` - handlers `ПриЗаписи`/`ПередУдалением`,
  called **automatically by BSP subscriptions**; do not call them directly from
  application code.
- `ВыгрузкаОбъектовВФайлы` - server API for saving objects to files;
  connected to forms through the `ПодключаемыеКоманды` subsystem.

⚠️ Common module selection mistakes:
- `ВерсионированиеОбъектовСлужебный` (without the `ВызовСервера` suffix) does
  **not exist**. The subsystem's only service module is
  `ВерсионированиеОбъектовСлужебныйВызовСервера`. All required operations are
  in the main `ВерсионированиеОбъектов` module, split by regions.
- `РаботаСФайламиСлужебный` - exists, but ⚠️ is a service module; the stable
  counterparts are in `РаботаСФайлами`.

An attached file is a **catalog item** subordinate to a "owner"
(defined type `ВладелецПрисоединенныхФайлов`). The name of the storage catalog
depends on the owner (for example, for `Справочник.ДоговорыКонтрагентов` -
`Справочник.ДоговорыКонтрагентовПрисоединенныеФайлы`).

## Scenarios

### 1. Attach a file to an object from server-side code

**Task:** create an attached file (PDF invoice, scan) from binary data in
temporary storage and bind it to a document/catalog.

**Functions:**
`РаботаСФайлами.ПараметрыДобавленияФайла(ДополнительныеРеквизиты = Неопределено) Экспорт`
 - returns a parameter structure (fill `ВладелецФайлов`, `ИмяБезРасширения`,
  `Расширение`, `ДатаМодификацииУниверсальная`).
`РаботаСФайлами.ДобавитьФайл(ПараметрыФайла, Знач АдресФайлаВоВременномХранилище, Знач АдресВременногоХранилищаТекста = "", Знач Описание = "", Знач НоваяСсылкаНаФайл = Неопределено) Экспорт`
 - creates a storage catalog item, returns a reference.
 - both Functions, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ПараметрыФайла` (Структура) - file parameters; required field
  `ВладелецФайлов` (owner reference). Filled via
  `ПараметрыДобавленияФайла()`.
- `АдресФайлаВоВременномХранилище` (Строка) - address of binary data in
  temporary storage (from `ПоместитьВоВременноеХранилище`).
- `АдресВременногоХранилищаТекста` (Строка) - address of extracted text; `""` -
  do not extract.
- `Описание` (Строка) - file comment/description.
- `НоваяСсылкаНаФайл` (ЛюбаяСсылка) - prefilled reference to a new file
  (`Неопределено` - generate one).

**Example:**
```bsl
// Двоичные данные уже помещены во временное хранилище (АдресДвоичныхДанныхВХ)
ПараметрыФайла = РаботаСФайлами.ПараметрыДобавленияФайла();
ПараметрыФайла.ВладелецФайлов      = ЗаказПоставщику;
ПараметрыФайла.ИмяБезРасширения    = "Счёт_" + ЗаказПоставщику.Номер;
ПараметрыФайла.Расширение          = "pdf";
ПараметрыФайла.ДатаМодификацииУниверсальная = ТекущаяДатаСеанса();

ПрисоединенныйФайл = РаботаСФайлами.ДобавитьФайл(
    ПараметрыФайла,
    АдресДвоичныхДанныхВХ,
    "",                       // без извлечения текста
    "Загружен из EDI");       // описание
```

**Nuances / anti-patterns:**
- ❌ `РаботаСФайлами.ПолучитьДанныеФайла(Файл)` - the method **does not exist**
  (compile error). The real name is `ДанныеФайла` (without the `Получить`
  prefix).
- ❌ `РаботаСФайлами.УдалитьФайл(Файл)` - the method **does not exist**. Deletion
  is intentionally not exposed in the stable API (side effects: version cleanup,
  EDS, registers). Use the platform deletion mark:
  `Файл.ПолучитьОбъект().УстановитьПометкуУдаления(Истина)`.
- ❌ Passing `ВладелецФайлов` as the first positional argument to `ДобавитьФайл` -
  exception "Не задано значение параметра ПараметрыФайла.ВладелецФайлов".
  The owner is taken from the `ПараметрыФайла` structure.
- For files from disk there is a simplified wrapper
  `РаботаСФайлами.ДобавитьФайлСДиска(ВладелецФайлов, ПутьКФайлуНаДиске)` -
  without manual parameter construction.

### 2. Read and update file binary data

**Task:** get the attributes and binary data of an attached file, overwrite its
content without creating a new version.

**Functions:**
`РаботаСФайлами.ДанныеФайла(Знач ПрисоединенныйФайл, Знач ДополнительныеПараметры = Неопределено, Знач УдалитьПолучатьСсылкуНаДвоичныеДанные = Истина, Знач УдалитьДляРедактирования = Ложь) Экспорт`
 - attributes + optionally the binary data address in temporary storage.
`РаботаСФайлами.ДвоичныеДанныеФайла(Знач ПрисоединенныйФайл, Знач ВызыватьИсключение = Истина) Экспорт`
 - file binary data directly.
`РаботаСФайлами.ОбновитьФайл(Знач ПрисоединенныйФайл, Знач ИнформацияОФайле) Экспорт`
 - overwrite binary data/attributes **without** a new version.
 - all Functions/Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ПрисоединенныйФайл` (СправочникСсылка.ПрисоединенныеФайлы) - file reference.
- `ДополнительныеПараметры` (Структура) - optional parameters; `Неопределено` -
  default.
- `УдалитьПолучатьСсылкуНаДвоичныеДанные` (Булево) - `Истина` -> place the
  binary data in temporary storage and return the address in
  `ДанныеФайла.СсылкаНаДвоичныеДанныеФайла`.
- `УдалитьДляРедактирования` (Булево) - `Истина` -> place in the working
  directory for editing.
- `ИнформацияОФайле` (Структура) - for `ОбновитьФайл`: new file properties.
  Keys: `АдресФайлаВоВременномХранилище` (Строка - new binary data),
  `АдресВременногоХранилищаТекста` (Строка - extracted text),
  `ИмяБезРасширения` / `Расширение` (Строка - optional),
  `ДатаМодификацииУниверсальная` (Дата - optional, otherwise current session date).

**Example:**
```bsl
// Прочитать реквизиты и адрес двоичных данных
Данные = РаботаСФайлами.ДанныеФайла(Файл, Неопределено);
АдресДвоичных = Данные.СсылкаНаДвоичныеДанныеФайла;  // во временном хранилище

// Получить двоичные данные напрямую (без временного хранилища)
ДвоичныеДанные = РаботаСФайлами.ДвоичныеДанныеФайла(Файл);

// Обновить содержимое без новой версии (только для файлов БЕЗ хранения версий)
АдресВХ = ПоместитьВоВременноеХранилище(НовыеДвоичныеДанные);
ИнформацияОФайле = Новый Структура;
ИнформацияОФайле.Вставить("АдресФайлаВоВременномХранилище", АдресВХ);
ИнформацияОФайле.Вставить("ДатаМодификацииУниверсальная", ТекущаяДатаСеанса());
РаботаСФайлами.ОбновитьФайл(Файл, ИнформацияОФайле);
```

**Nuances / anti-patterns:**
- ❌ `РаботаСФайлами.ОбновитьФайл` for a file with **version storage enabled** -
  silent loss of the old version. First check
  `ВерсионированиеОбъектов.ВключеноВерсионированиеОбъекта("Справочник." +
  Файл.Метаданные().Имя)`; for versioned files create a new version
  through the "Check out/Check in file for editing" mechanism in the file form.
- `ДанныеФайла` switches to privileged mode itself to read
  binary data; the calling code does not need to enable it separately.
- For batch reading of binary data from an array of files there is
  `РаботаСФайлами.ДвоичныеДанныеФайлов(Знач ПрисоединенныеФайлы, Знач ВызыватьИсключение = Истина)`.

### 3. Open the file form and the add dialog from client-side code

**Task:** from a form command, open the attached file card or
the multiple-file add dialog with an extension filter.

**Functions:**
`РаботаСФайламиКлиент.ОткрытьФормуФайла(Знач ПрисоединенныйФайл, СтандартнаяОбработка = Ложь, ДополнительныеПараметры = Неопределено, ОписаниеОповещенияОЗакрытии = Неопределено) Экспорт`
 - open the file card (also used as the "Open" command handler).
`РаботаСФайламиКлиент.ДобавитьФайлы(Знач ВладелецФайла, Знач ИдентификаторФормы, Знач Фильтр = "", ГруппаФайлов = Неопределено, ОбработчикРезультата = Неопределено) Экспорт`
 - open the multiple-add dialog with an extension filter.
`РаботаСФайламиКлиент.ОткрытьФормуВыбораФайлов(Знач ВладелецФайлов, Знач ЭлементФормы, СтандартнаяОбработка = Ложь, ОписаниеОповещенияОВыборе = Неопределено) Экспорт`
 - open the owner's file selection form.
 - all Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `ПрисоединенныйФайл` (СправочникСсылка.ПрисоединенныеФайлы) - file to open.
- `СтандартнаяОбработка` (Булево) - **output** parameter; the method always sets
  it to `Ложь` so the standard platform handling does not run.
- `ВладелецФайла` (ЛюбаяСсылка) - file owner object.
- `ИдентификаторФормы` (УникальныйИдентификатор) - owner form UID (for
  returning the result).
- `Фильтр` (Строка) - comma-separated extension filter, e.g. `"pdf,docx"`.

**Example:**
```bsl
&НаКлиенте
Процедура ОткрытьСканДоговора(Команда)
    Если Объект.Ссылка.Пустая() Тогда
        Возврат;  // файлы нельзя открыть у незаписанного объекта
    КонецЕсли;

    МассивФайлов = Новый Массив;
    РаботаСФайлами.ЗаполнитьПрисоединенныеФайлыКОбъекту(Объект.Ссылка, МассивФайлов);

    Если МассивФайлов.Количество() = 1 Тогда
        РаботаСФайламиКлиент.ОткрытьФормуФайла(МассивФайлов[0], Ложь);
    Иначе
        РаботаСФайламиКлиент.ОткрытьФормуВыбораФайлов(Объект.Ссылка, Элементы.СканДоговора, Ложь);
    КонецЕсли;
КонецПроцедуры

// Диалог пакетного добавления с фильтром
&НаКлиенте
Процедура ДобавитьДокументы(Команда)
    РаботаСФайламиКлиент.ДобавитьФайлы(Объект.Ссылка, УникальныйИдентификатор, "pdf,docx");
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Opening files for an unsaved object (empty owner reference) -
  the storage catalog is subordinate to the owner, the file will "hang".
- `ОткрытьФормуФайла` combines two roles: a standalone method and a form
  command handler. As a handler, pass `СтандартнаяОбработка = Ложь`
  (the method will set it to `Ложь` itself).
- For the owner's file list form -
  `РаботаСФайламиКлиент.ОткрытьФормуСпискаФайлов(ВладелецФайлов, ПараметрыФормы, ВладелецФормы, ОповещениеОЗакрытии)`.

### 4. Copy files and check attachment availability

**Task:** during "Create Based On" / "Copy", transfer all attached files from
the source to the target; verify that files can be attached to the object type
at all.

**Functions:**
`РаботаСФайлами.КОбъектуМожноПрисоединятьФайлы(ВладелецФайлов, ИмяСправочника = "") Экспорт`
 - `Булево`: whether the owner has a storage catalog.
`РаботаСФайлами.СкопироватьПрисоединенныеФайлы(Знач Источник, Знач Получатель) Экспорт`
 - copy all source files to the target (same type).
`РаботаСФайлами.ЗаполнитьПрисоединенныеФайлыКОбъекту(Знач ВладелецФайла, Знач Файлы) Экспорт`
 - fill the passed array with references to all files of the owner.
 - all Functions/Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ВладелецФайлов` (ЛюбаяСсылка) - owner object (or its metadata).
- `ИмяСправочника` (Строка) - storage catalog name; `""` - any.
- `Источник` / `Получатель` (ЛюбаяСсылка) - objects of the same type.
- `Файлы` (Массив) - filled with file references; clear it before calling.

**Example:**
```bsl
// Проверка перед прикреплением
Если Не РаботаСФайлами.КОбъектуМожноПрисоединятьФайлы(Документ) Тогда
    Возврат;  // у типа объекта нет справочника хранения файлов
КонецЕсли;

// Собрать все файлы объекта в массив
МассивФайлов = Новый Массив;
РаботаСФайлами.ЗаполнитьПрисоединенныеФайлыКОбъекту(ДокументИсточник, МассивФайлов);

// Перенести файлы при копировании объекта
РаботаСФайлами.СкопироватьПрисоединенныеФайлы(ДокументИсточник, ДокументПолучатель);
```

**Nuances / anti-patterns:**
- `СкопироватьПрисоединенныеФайлы` requires `Источник` and `Получатель` to be of
  the same type (one storage catalog). If the types do not match, behavior is
  undefined.
- `КОбъектуМожноПрисоединятьФайлы` returns `Ложь` if the owner has no storage
  catalog at all - use it as a guard before dynamic attachment.

### 5. Enable and configure object versioning

**Task:** during initial database population, enable change history recording
for your catalog/document and set the versioning mode and retention period.

**Functions:**
`ВерсионированиеОбъектов.ВключеноВерсионированиеОбъекта(ИмяОбъекта) Экспорт`
 - `Булево`: whether versioning is enabled for the metadata object.
`ВерсионированиеОбъектов.ВключитьВерсионированиеОбъекта(ИмяОбъекта, Знач ВариантВерсионирования = Неопределено) Экспорт`
 - enable versioning for the object.
`ВерсионированиеОбъектов.ВключитьВерсионированиеОбъектов(Объекты) Экспорт`
 - batch enable for a list of objects. `Объекты` is **`Соответствие`**
  (key - full metadata path `"Справочник.Номенклатура"`, value -
  `Перечисления.ВариантыВерсионированияОбъектов.*`), not an array.
`ВерсионированиеОбъектов.ЗаписатьНастройкуВерсионированияПоОбъекту(Знач ТипОбъекта, Знач ВариантВерсионирования, Знач СрокХраненияВерсий = Неопределено) Экспорт`
 - fine-tuning: versioning mode (`Перечисления.ВариантыВерсионированияОбъектов.*`)
  and retention period (`Перечисления.СрокиХраненияВерсий.*`).
`ВерсионированиеОбъектов.ЗначениеФлажкаХранитьИсторию() Экспорт`
 - current value of the functional option "Use object versioning".
 - all Functions/Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ИмяОбъекта` (Строка) - full metadata name, e.g.
  `"Справочник.ДоговорыКонтрагентов"`.
- `ВариантВерсионирования` (ПеречислениеСсылка.ВариантыВерсионированияОбъектов) -
  `Неопределено` = default value (`ПриЗаписи`).
- `ТипОбъекта` (Тип) - object reference type, e.g.
  `Тип("СправочникСсылка.ДоговорыКонтрагентов")`.
- `СрокХраненияВерсий` (ПеречислениеСсылка.СрокиХраненияВерсий) - `Неопределено`
  = no retention limit.

**Example:**
```bsl
ИмяОбъекта = "Справочник.ДоговорыКонтрагентов";

Если Не ВерсионированиеОбъектов.ВключеноВерсионированиеОбъекта(ИмяОбъекта) Тогда
    ВерсионированиеОбъектов.ВключитьВерсионированиеОбъекта(ИмяОбъекта);
КонецЕсли;

// Тонкая настройка: вариант «При записи» + срок «Последний год»
ТипОбъекта = Тип("СправочникСсылка.ДоговорыКонтрагентов");
ВерсионированиеОбъектов.ЗаписатьНастройкуВерсионированияПоОбъекту(
    ТипОбъекта,
    Перечисления.ВариантыВерсионированияОбъектов.ПриЗаписи,
    Перечисления.СрокиХраненияВерсий.ПоследнийГод);
```

**Nuances / anti-patterns:**
- ❌ Duplicating version write logic in the object module's `ПриЗаписи` is not
  needed - BSP connects its own subscriptions
  (`ВерсионированиеОбъектовСобытия.ЗаписатьВерсиюОбъекта`) automatically for
  all versioned objects. Enable/disable via
  `ВключитьВерсионированиеОбъекта`; writing to the `ВерсииОбъектов` register
  will happen automatically.
- The functional option `ИспользоватьВерсионированиеОбъектов` globally
  disables version writing; it is checked inside write methods. Read the value
  via `ЗначениеФлажкаХранитьИсторию()`.
- To batch-enable several objects at once -
  `ВключитьВерсионированиеОбъектов(Объекты)`, where `Объекты` is **Соответствие**
  (key - full metadata path, value - `ВариантыВерсионированияОбъектов`), not an
  array.

### 6. Read an object version and generate a report

**Task:** given a reference and version number, get the object attributes at the
time of that version and output the printable version report.

**Functions:**
`ВерсионированиеОбъектов.СведенияОВерсииОбъекта(Знач Ссылка, Знач НомерВерсии) Экспорт`
 - structure: `ВерсияОбъекта` (`ДвоичныеДанные` - serialized object version),
  `АвторВерсии` (`СправочникСсылка.Пользователи` / `.ВнешниеПользователи`),
  `ДатаВерсии` (`Дата`).
`ВерсионированиеОбъектов.ОтчетПоВерсииОбъекта(СсылкаНаОбъект, Знач ВерсияОбъекта = Неопределено, ПользовательскийНомерВерсии = Неопределено) Экспорт`
 - `ТабличныйДокумент` with the attributes of the specified version.
`ВерсионированиеОбъектов.НомерПоследнейВерсии(Ссылка, ИзмененныеПользователем = Ложь) Экспорт`
 - number of the last object version.
 - ⚠️ **all three methods are in `#Область СлужебныеПроцедурыИФункции`** - backward
  compatibility is **not guaranteed**. There is intentionally no stable public
  analogue for reading versions. Server.

**Parameters:**
- `Ссылка` (ЛюбаяСсылка) - object.
- `НомерВерсии` (Число) - version number (starting from `1`).
- `ВерсияОбъекта` (Число) - `Неопределено` -> latest version.
- `ПользовательскийНомерВерсии` (Число) - displayed number (after
  reindexing it may differ from the internal one).

**Example:**
```bsl
// Прочитать данные версии
Сведения = ВерсионированиеОбъектов.СведенияОВерсииОбъекта(СсылкаНаОбъект, 3);
// Сведения.ВерсияОбъекта — ДвоичныеДанные сериализованной версии объекта
// Сведения.АвторВерсии, Сведения.ДатаВерсии — автор и дата записи версии

// Печатная форма отчёта по версии
ТабДок = ВерсионированиеОбъектов.ОтчетПоВерсииОбъекта(СсылкаНаОбъект, 3);
ТабДок.Показать("Версия №" + 3);

// Узнать последнюю версию
Номер = ВерсионированиеОбъектов.НомерПоследнейВерсии(СсылкаНаОбъект);
```

**Nuances / anti-patterns:**
- ⚠️ `СведенияОВерсииОбъекта` sets privileged mode itself and checks the
  `ЧтениеДанныхВерсийОбъектов` right (via
  `ЕстьПравоЧтенияДанныхВерсийОбъектов`). If the role is missing, it throws
  the exception "Не удалось получить предыдущую версию объекта".
- ❌ `ВерсионированиеОбъектовСлужебный.ЗаписатьВерсиюОбъекта(...)` - the module
  **does not exist** (there is no `ВызовСервера` suffix). Forced version write -
  `ВерсионированиеОбъектов.ЗаписатьВерсиюОбъекта(Знач Источник, РежимЗаписи = Неопределено)`
  (⚠️ `СлужебныйПрограммныйИнтерфейс`), but application code does not need to
  call it - BSP subscriptions do that themselves.
- ❌ `ВерсионированиеОбъектовКлиент.ОткрытьИсторию(Ссылка)` - the method **does
  not exist**. The real name is `ПоказатьИсториюИзменений(Ссылка, ФормаВладелец)`
  (⚠️ `СлужебныйПрограммныйИнтерфейс`). For the version report from the client -
  `ВерсионированиеОбъектовКлиент.ОткрытьОтчетПоВерсииОбъекта(Ссылка, АдресСериализованногоОбъекта)`.

### 7. Connect files and versioning to an object form

**Task:** in a custom form (extension/custom form), show the attached-files
hyperlink and the "Change history" button.

**Functions:**
`РаботаСФайлами.ПриСозданииНаСервере(Форма, ДобавляемыеЭлементы = Неопределено, НастройкиРаботыСФайламиВФорме = Неопределено) Экспорт`
 - adds the hyperlink, commands and file preview field. Server.
`РаботаСФайламиКлиент.ПриОткрытии(Форма, Отказ) Экспорт`
 - client-side initialization when opening the form. Client.
`РаботаСФайламиКлиент.ОбработкаОповещения(Форма, ИмяСобытия) Экспорт`
 - client-side notification handling (refreshing the file list). Client.
`ВерсионированиеОбъектов.ПриСозданииНаСервере(Форма) Экспорт`
 - sets the functional option parameter `ТипВерсионируемогоОбъекта`;
  without calling it, the "Change history" button will not appear. Server.
 - all in `#Область ПрограммныйИнтерфейс` (stable).

**Parameters:**
- `Форма` (УправляемаяФорма) - object form.
- `ДобавляемыеЭлементы` (Массив) - array of added form elements (from the
  `ПриСозданииНаСервере` handler).
- `НастройкиРаботыСФайламиВФорме` (Структура) - optional presets.

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    // Присоединённые файлы — гиперссылка, команды, поле предпросмотра
    РаботаСФайлами.ПриСозданииНаСервере(ЭтаФорма, ДобавляемыеЭлементы);

    // Версионирование — кнопка "История изменений"
    ВерсионированиеОбъектов.ПриСозданииНаСервере(ЭтаФорма);
КонецПроцедуры

&НаКлиенте
Процедура ПриОткрытии(Отказ)
    РаботаСФайламиКлиент.ПриОткрытии(ЭтаФорма, Отказ);
КонецПроцедуры

&НаКлиенте
Процедура ОбработкаОповещения(ИмяСобытия, Параметр, Источник)
    РаботаСФайламиКлиент.ОбработкаОповещения(ЭтаФорма, ИмяСобытия);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- These calls are **optional** if BSP is connected through the standard
  "attached files" mechanism - it will hook itself in automatically. They are
  needed only for custom forms (extension, custom form).
- Without `ВерсионированиеОбъектов.ПриСозданииНаСервере` the "Change history"
  button will not appear, even if versioning is globally enabled and
  `НастройкиВерсионированияОбъектов` contains entries for the object.

### 8. Save objects to files (XML/JSON/Excel/print)

**Task:** programmatically export an array of objects to files in a given format
(for example, an XML package for exchange, an Excel report, a PDF print form)
and obtain the binary data of the files.

**Function:**
`ВыгрузкаОбъектовВФайлы.СохранитьПоФорматуВФайл(КомандыВыгрузки, СписокОбъектов, НастройкиСохранения) Экспорт`
 - Function -> `ТаблицаЗначений` (columns `ИмяФайла`, `ДвоичныеДанные`),
  region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `КомандыВыгрузки` (Структура / Массив) - export command or commands;
  the structure is compatible with `УправлениеПечатью.КомандыПечатиФормы` (the
  export format is taken from the command).
- `СписокОбъектов` (Массив из СправочникСсылка / ДокументСсылка) - references to
  the objects being saved.
- `НастройкиСохранения` (Структура) - save settings; the structure is compatible
  with `УправлениеПечатью.НастройкиСохранения` (directory, file naming method,
  etc.).

**Example:**
```bsl
// Команда выгрузки формируется по аналогии с командами печати
КомандыВыгрузки = Новый Массив;
КомандаВыгрузки = Новый Структура("Формат, Идентификатор",
    "XML", "ВыгрузкаВXML");
КомандыВыгрузки.Добавить(КомандаВыгрузки);

СписокОбъектов = Новый Массив;
СписокОбъектов.Добавить(СсылкаНаДокумент);

НастройкиСохранения = Новый Структура;  // см. УправлениеПечатью.НастройкиСохранения

Результат = ВыгрузкаОбъектовВФайлы.СохранитьПоФорматуВФайл(
    КомандыВыгрузки, СписокОбъектов, НастройкиСохранения);
// Результат — ТаблицаЗначений: ИмяФайла, ДвоичныеДанные
Для Каждого СтрокаФайла Из Результат Цикл
    СтрокаФайла.ДвоичныеДанные.Записать(КаталогВыгрузки + СтрокаФайла.ИмяФайла);
КонецЦикла;
```

**Nuances / anti-patterns:**
- The subsystem `ВыгрузкаОбъектовВФайлы` is an extension over `УправлениеПечатью`:
  export formats (`XML`, `JSON`, tabular formats) reuse the print-command
  infrastructure. The `КомандыВыгрузки` / `НастройкиСохранения` structures are
  compatible with the printing module equivalents.
- In interactive mode, export is connected to forms through the
  `ПодключаемыеКоманды` subsystem (methods `ПриОпределенииКомандПодключенныхКОбъекту` and
  `ПриОпределенииВидовПодключаемыхКоманд` of the module - ⚠️
  `СлужебныйПрограммныйИнтерфейс`, called automatically by BSP). For
  programmatic export, use only `СохранитьПоФорматуВФайл`.
- The remaining module methods (`ВыполнитьВыгрузкуВXML`, `ВыполнитьВыгрузкуВJSON`,
  `СформироватьСтруктуруДляВыгрузки`) are ⚠️ `СлужебныйПрограммныйИнтерфейс`;
  you do not need to call them directly - `СохранитьПоФорматуВФайл` calls them
  itself based on the command format.

## Rare methods

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures -
via `python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `РаботаСФайлами.НоваяСсылкаНаФайл(ВладелецФайлов, ИмяСправочника = Неопределено)`
  - prefilled reference to a new file (for `ДобавитьФайл`).
- `РаботаСФайлами.ИмяФормыОбъектаФайловПоВладельцу(Знач ВладелецФайлов)` - name
  of the owner's file list form.
- `РаботаСФайлами.ЕстьТомаХраненияФайлов()` - `Булево`: are file storage volumes
  configured (external directories).
- `РаботаСФайлами.НастройкиРаботыСФайлами()` / `СохранитьНастройкиРаботыСФайлами(Настройки)`
  - read/write common file-handling settings.
- `РаботаСФайлами.МаксимальныйРазмерФайла()` / `МаксимальныйРазмерФайлаОбщий()`
  - file size limits (single and total).
- `РаботаСФайлами.ПеренестиФайлыМеждуСправочникамиХранения(Знач ВладелецФайлов, Знач Источник = Неопределено, Знач Приемник = Неопределено)`
  - file migration when changing the storage catalog.
- `ВерсионированиеОбъектов.ВключеноВерсионированиеОбъектов(СписокОбъектов)` -
  batch check of enablement for a list of objects.
- `ВерсионированиеОбъектов.ЕстьПравоЧтенияИнформацииОВерсияхОбъектов()` /
  `ЕстьПравоЧтенияДанныхВерсийОбъектов()` - ⚠️ `СлужебныйПрограммныйИнтерфейс`:
  checks of rights to read version information and version data.

Override hooks (modules `*Переопределяемый` - BSP calls them, application
code implements them; do not call directly):
- `РаботаСФайламиПереопределяемый` - file behavior settings (which files are
  "unnecessary", attachment filters).
- `ВерсионированиеОбъектовПереопределяемый` - which objects are versioned,
  application versioning rules.

For the signature/region of any of these methods -
`python scripts/bsp_api.py method <Имя> --src src/cf`.
