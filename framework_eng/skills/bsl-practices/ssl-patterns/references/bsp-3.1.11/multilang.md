# BSP Multilingual Support

The **Multilingual Support** subsystem is the built-in BSP mechanism for storing and
displaying string attribute values of application objects in multiple
languages. Two ways to store translations: in the object's **header** by duplicate
attributes with the `ЯзыкN` suffix (`Наименование`, `НаименованиеЯзык1`,
`НаименованиеЯзык2`) or in the `Представления` **tabular section** (`КодЯзыка` + localized attributes). The methods are mutually exclusive at the level of a
single object.

Here is only the API of the `Мультиязычность` subsystem. Translating interface strings
via `NСтр("ru = '...'; en = '...'")` is a platform mechanism and does not
belong to BSP. Printed forms in different languages are `УправлениеПечатьюМультиязычность*`
(print skill). Machine translation is the “Text Translation” subsystem and is not
covered.

## Modules

- `МультиязычностьСервер` — server API: form handlers, query transformation,
  language information.
- `МультиязычностьКлиентСервер` — common code (client + server): suffix by
  number, object/reference presentation handlers.
- `МультиязычностьКлиент` — client API: `ПриОткрытии` handler for a form field
  that opens `ОбщаяФорма.ВводНаРазныхЯзыках`.
- `МультиязычностьПереопределяемый` — hook `ПриОпределенииНастроек(Настройки)`
  (BSP calls it, application code implements it; not called directly).
- `МультиязычностьПовтИсп` — cached language information; in application code
  **do not call directly** — only through the `МультиязычностьСервер` /
  `МультиязычностьКлиентСервер` wrappers.

Main language code — `ОбщегоНазначения.КодОсновногоЯзыка()` (stable,
`ПрограммныйИнтерфейс`; also `ОбщегоНазначенияКлиент.КодОсновногоЯзыка`).
Suffix for the main language is an empty string: the attribute is simply called
`Наименование` (without `Язык0`). Additional languages are numbered in order of
enabling: `Язык1`, `Язык2`, … The additional language code (`"en"`, `"de"`) is
a BCP-47-compatible code from the `Языки.КодЯзыка` metadata.

## Scenarios

### 1. Object form with multilingual fields

**Task:** add the “input in different languages” button to the object form and
automatically display the value in the user's current language when reading,
and write the correct language column when saving.

**Functions:**
`МультиязычностьСервер.ПриСозданииНаСервере(Форма, Объект = Неопределено, ИмяОбъекта = Неопределено) Экспорт`
— connects the open button and handler to form fields with localized fields; for list forms, modifies the dynamic list query text.
`МультиязычностьСервер.ПриЧтенииНаСервере(Форма, ТекущийОбъект, ИмяОбъекта = Неопределено) Экспорт`
— fills the form fields with values in the current language.
`МультиязычностьСервер.ПередЗаписьюНаСервере(ТекущийОбъект) Экспорт`
— maps values in the current language into `ЯзыкN` fields or rows of the `Представления` tabular section.
— all Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Форма` (УправляемаяФорма) — object form.
- `Объект` (Произвольный) — optional main form attribute.
- `ИмяОбъекта` (Строка) — name of the main form attribute: `"Объект"` (default for object forms), `"Запись"` for registers, `"Список"` for lists. Pass it explicitly if the name is nonstandard.
- `ТекущийОбъект` (Произвольный) — form object/record.

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    МультиязычностьСервер.ПриСозданииНаСервере(ЭтотОбъект, Объект, "Объект");
КонецПроцедуры

&НаСервере
Процедура ПриЧтенииНаСервере(ТекущийОбъект)
    МультиязычностьСервер.ПриЧтенииНаСервере(ЭтотОбъект, ТекущийОбъект, "Объект");
КонецПроцедуры

&НаСервере
Процедура ПередЗаписьюНаСервере(Отказ, ТекущийОбъект, ПараметрыЗаписи)
    МультиязычностьСервер.ПередЗаписьюНаСервере(ТекущийОбъект);
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Do not call `МультиязычностьСервер.ПриСозданииНаСервере(ЭтотОбъект)` in a
  list form — the list will show values in the main language for all
  users. The method modifies the `ТекстЗапроса` of the dynamic list, and
  it must be called **first** in the handler, otherwise subsequent code may
  overwrite the text.
- Fallback to the main language is built into `ПриЧтенииНаСервере`: if the value in
  the current language is empty, the main language is substituted. Do not implement the fallback manually.
- `ИмяОбъекта = "Объект"` — the standard name of the main form attribute. For register record forms, pass `"Запись"`; for list forms, `"Список"`.

### 2. Manager module: reference presentation and string input

**Task:** so that a reference to an item is displayed in the user's current language,
and string input searches across all language-specific representations of the object.

**Functions:**
`МультиязычностьКлиентСервер.ОбработкаПолученияПредставления(Данные, Представление, СтандартнаяОбработка, ИмяРеквизита = "Наименование") Экспорт`
— replaces the reference presentation with the value of the attribute in the current language.
  Client + Server.
`МультиязычностьКлиентСервер.ОбработкаПолученияПолейПредставления(Поля, СтандартнаяОбработка, ИмяРеквизита = "Наименование") Экспорт`
— adds language-specific attributes to the presentation fields. Client + Server.
`МультиязычностьСервер.ОбработкаПолученияДанныхВыбора(ДанныеВыбора, Знач Параметры, СтандартнаяОбработка, ОбъектМетаданных) Экспорт`
— replaces the standard string-based selection: searches across all language-specific representations.
  Server.
— all Procedures, region `#Область ПрограммныйИнтерфейс` (stable).

**Parameters:**
- `Данные` (Arbitrary) — object data from the manager module handler.
- `Представление` (String) — **output** parameter: the generated presentation.
- `СтандартнаяОбработка` (Boolean) — **output** parameter; the method sets
  `Ложь` when it replaces the presentation.
- `ИмяРеквизита` (String) — name of the localizable attribute; default is
  `"Наименование"`. Pass it explicitly for nonstandard attributes
  (e.g. `"НаименованиеДляПечати"`).
- `Поля` (Array) — **output** parameter: fields used to build the
  presentation.
- `ДанныеВыбора` (ДанныеВыбора) — **output** parameter: the selection data list.
- `ОбъектМетаданных` (Metadata) — object metadata (from `Метаданные()`).

**Example:**
```bsl
// Catalog manager module
Процедура ОбработкаПолученияПредставления(Данные, Представление, СтандартнаяОбработка) Экспорт
    МультиязычностьКлиентСервер.ОбработкаПолученияПредставления(
        Данные, Представление, СтандартнаяОбработка, "Наименование");
КонецПроцедуры

Процедура ОбработкаПолученияПолейПредставления(Поля, СтандартнаяОбработка) Экспорт
    МультиязычностьКлиентСервер.ОбработкаПолученияПолейПредставления(
        Поля, СтандартнаяОбработка, "Наименование");
КонецПроцедуры

Процедура ОбработкаПолученияДанныхВыбора(ДанныеВыбора, Параметры, СтандартнаяОбработка) Экспорт
    МультиязычностьСервер.ОбработкаПолученияДанныхВыбора(
        ДанныеВыбора, Параметры, СтандартнаяОбработка, Метаданные());
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ `ОбработкаПолученияПолейПредставления(Поля, СтандартнаяОбработка)` without
  the third parameter — `ИмяРеквизита` defaults to `"Наименование"`. If the
  localizable attribute is named differently (e.g. `"Заголовок"`), without an explicit
  parameter the method will substitute the wrong field. Specify `ИмяРеквизита` for
  nonstandard attributes.
- Without these three handlers, the reference to the item will be displayed only in
  the main language, and string input will not find items by translations.
- Fallback to the main language is built into `ОбработкаПолученияПредставления`: if
  it is empty in the current language, the main language is returned; if that is also empty, standard
  processing is not disabled.

### 3. Dynamic list with language switching

**Task:** in a list form, the dynamic list must show values in the user's current language; for a programmatically built query, append the `ЯзыкN` suffix to the required field.

**Functions:**
`МультиязычностьСервер.ПриСозданииНаСервере(Форма, Объект = Неопределено, ИмяОбъекта = Неопределено) Экспорт`
— for a list form without an explicit `Объект`, modifies the `ТекстЗапроса`
  of the dynamic list `Список`. Server.
`МультиязычностьСервер.ИзменитьПолеЗапросаПодТекущийЯзык(ТекстЗапроса, ИмяПоля) Экспорт`
— appends the `ЯзыкN` suffix to the specified field in the query text (supports
  `ИмяПоля КАК Псевдоним`). Does nothing if the current language is the main one.
  Server.
— both Procedures, region `#Область ПрограммныйИнтерфейс` (stable).

**Parameters:**
- `ТекстЗапроса` (String) — **output** parameter: query text with the
  modified field.
- `ИмяПоля` (String) — field name in the query text, e.g.
  `"СправочникНоменклатура.Наименование"`.

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    // Automatically modifies ТекстЗапроса of the dynamic list "Список"
    МультиязычностьСервер.ПриСозданииНаСервере(ЭтотОбъект);
КонецПроцедуры

// Manual control (when the query text is built programmatically)
ТекстЗапроса = "ВЫБРАТЬ СправочникНоменклатура.Наименование КАК Наименование, "
    + "СправочникНоменклатура.Артикул КАК Артикул ИЗ Справочник.Номенклатура КАК СправочникНоменклатура";
МультиязычностьСервер.ИзменитьПолеЗапросаПодТекущийЯзык(ТекстЗапроса, "СправочникНоменклатура.Наименование");
// On the first additional language, Наименование will become НаименованиеЯзык1; on the main one — unchanged
```

**Nuances / anti-patterns:**
- ❌ Hardcoding `"Наименование" + "Язык1"` in the query — it will break when the
  language order changes (the user enabled `en` first, but `de` comes first in the
  configuration). Use `ИзменитьПолеЗапросаПодТекущийЯзык` or
  `СуффиксТекущегоЯзыка()`.
- `ИзменитьПолеЗапросаПодТекущийЯзык` does nothing on the main language —
  the field remains `Наименование`. This is normal: the main language is stored in the base attribute without a suffix.

### 4. Initial filling of predefined values

**Task:** when initially filling predefined items, fill the
`AttributeName_LanguageCode` columns from a string in `NStr` format at once for
all languages, with fallback to the main language.

**Function:**
`МультиязычностьСервер.ЗаполнитьМультиязычныйРеквизит(Элемент, ИмяРеквизита, ИсходнаяСтрока, КодыЯзыков = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Элемент` (СтрокаТаблицыЗначений) — the table row being filled with columns
  `AttributeName_LanguageCode`.
- `ИмяРеквизита` (Строка) — attribute name, e.g. `"Наименование"`.
- `ИсходнаяСтрока` (Строка) — a string in `NStr` format, e.g.
  `"ru = 'Пример'; en = 'Example'"`.
- `КодыЯзыков` (Массив) — language codes for which the rows need to be filled;
  `Неопределено` — all configuration languages.

**Example:**
```bsl
Процедура ПриНачальномЗаполненииЭлементов(Элементы) Экспорт
    КодыЯзыков = Новый Массив;
    КодыЯзыков.Добавить("en");
    КодыЯзыков.Добавить("de");

    Для Каждого Элемент Из Элементы Цикл
        МультиязычностьСервер.ЗаполнитьМультиязычныйРеквизит(
            Элемент,
            "Наименование",
            "ru = 'Демо: пример'; en = 'Demo: example'; de = 'Demo: Beispiel'",
            КодыЯзыков);
    КонецЦикла;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Fill an object attribute via `НСтр` directly:
  `Объект.НаименованиеЯзык1 = НСтр("en = 'Example'")` — `НСтр` depends on the
  current session language, not on the language in which the object is being
  edited; different users will have different content. Use
  `ЗаполнитьМультиязычныйРеквизит` with an explicit list of codes — it does not
  depend on the session and fills all columns at once.
- The method itself provides fallback to the main language for languages missing
  in `ИсходнаяСтрока`: when `НСтр(ИсходнаяСтрока, КодЯзыка)` is empty,
  `НСтр(ИсходнаяСтрока, КодОсновногоЯзыка())` is used.
- ❌ Store translations in a single string attribute with a separator
  (`"Пример||Example||Beispiel"`) — this breaks line-based input, indexing and
  search. Use `ЯзыкN` attributes (header) or the `Представления` tabular section.

### 5. Determine the suffix and code of the current language

**Task:** programmatically obtain the suffix of the current language (`Язык1` / `""`) or
suffix by language code for building an attribute name.

**Functions:**
`МультиязычностьСервер.СуффиксТекущегоЯзыка() Экспорт`
— suffix of the current session language (`""` for the main one, `"Язык1"`, `"Язык2"`).
  Server.
`МультиязычностьСервер.СуффиксЯзыка(КодЯзыка) Экспорт`
— suffix by language code (`"en"` → `"Язык1"`); `""` if the language is not used.
  Server.
`МультиязычностьКлиентСервер.СуффиксЯзыка(ПорядковыйНомерЯзыка = Неопределено) Экспорт`
— suffix by ordinal number (`1` → `"Язык1"`); without a parameter — base
  string `"Язык"`. Client + Server.
`МультиязычностьСервер.ИспользуетсяДополнительныйЯзык(ПорядковыйНомерЯзыка) Экспорт`
— `Булево`: whether an additional language with the given number is enabled. Server.
`МультиязычностьСервер.КоличествоДополнительныхЯзыков() Экспорт`
— number of enabled additional languages. Server.
`МультиязычностьСервер.СведенияОЯзыках() Экспорт`
— structure with information about the configuration languages. Server.
— all Functions, region `#Область ПрограммныйИнтерфейс` (stable).

**Parameters:**
- `КодЯзыка` (Строка) — BCP-47-compatible code in lowercase (`"en"`).
- `ПорядковыйНомерЯзыка` (Число) — number of additional language (`1`, `2`, …).

**Example:**
```bsl
// Суффикс текущего языка
ИмяРеквизита = "Наименование" + МультиязычностьСервер.СуффиксТекущегоЯзыка();
// На основном языке -> "Наименование"; на первом доп. -> "НаименованиеЯзык1"

// Суффикс по коду языка
Суффикс = МультиязычностьСервер.СуффиксЯзыка("en");  // "Язык1" или ""
Если ПустаяСтрока(Суффикс) Тогда
    // "en" не включён в конфигурацию — берём основной реквизит
КонецЕсли;

// Суффикс по номеру (клиент + сервер)
Суффикс = МультиязычностьКлиентСервер.СуффиксЯзыка(1);  // "Язык1"

// Проверить, включён ли второй доп. язык
Если МультиязычностьСервер.ИспользуетсяДополнительныйЯзык(2) Тогда
    // ...
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Hardcoding `"Язык1"` in application code will break when the language order changes.
  Always obtain the suffix programmatically via `СуффиксТекущегоЯзыка()`
  or `СуффиксЯзыка(КодЯзыка)`.
- ❌ Calling `МультиязычностьПовтИсп.КоличествоДополнительныхЯзыков()` directly
  — `ПовтИсп` is for internal БСП use. The wrapper
  `МультиязычностьСервер.КоличествоДополнительныхЯзыков()` reads from `ПовтИсп`
  and will not break when the internal contract changes.
- Normalize the language code input to lowercase (`НРег(КодЯзыка)`) and do not
  rely on a regional suffix: БСП does not distinguish `"en-US"` and `"en"` —
  both map to the same suffix.

### 6. Programmatically read multilingual object attributes

**Task:** when reading an object programmatically outside a form (in a filling handler,
background job), populate multilingual attributes in the current language, as the
form does.

**Function:**
`МультиязычностьСервер.ПриЧтенииПредставленийНаСервере(Объект) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Объект` (СправочникОбъект / ДокументОбъект) — already obtained object
  (via `ПолучитьОбъект()` or a query with `Разрешенные`).

**Example:**
```bsl
// В обработке заполнения/фоновом задании
Объект = Ссылка.ПолучитьОбъект();
МультиязычностьСервер.ПриЧтенииПредставленийНаСервере(Объект);
// Объект.Наименование теперь содержит значение на текущем языке сеанса
// (с fallback на основной, если перевод пуст)
```

**Nuances / antipatterns:**
- Unlike `ПриЧтенииНаСервере(Форма, …)` (for forms), this method accepts an
  already obtained `*Объект`, without form context. Use it for
  programmatic reading outside the UI.
- Before writing an object modified programmatically, call
  `МультиязычностьСервер.ПередЗаписьюНаСервере(ТекущийОбъект)` — it will split
  the value in the current language into the required language columns.

### 7. Extension of subsystem settings (override hook)

**Task:** the application configuration adjusts multilingual behavior
(e.g., disables input in different languages for certain roles).

**Hook:**
`МультиязычностьПереопределяемый.ПриОпределенииНастроек(Настройки) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (override hook).
  БСП calls this method; application code **implements** it in the identically
  named module of the application configuration. It is not called directly from
  application code.

**Parameters:**
- `Настройки` (Структура) — subsystem settings; filled/modified in the hook.

**Example:**
```bsl
// МультиязычностьПереопределяемый (модуль прикладной конфигурации)
Процедура ПриОпределенииНастроек(Настройки) Экспорт
    // Напр., отключить ввод на разных языках для роли "Только чтение"
    Если Пользователи.РолиДоступны("ТолькоЧтение") Тогда
        Настройки.ВводНаРазныхЯзыкахДоступен = Ложь;
    КонецЕсли;
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Implementing the hook in the main module `МультиязычностьСервер` is not allowed.
  The hook must be in `МультиязычностьПереопределяемый` **application**
  configuration (the override module is copied, the body is overridden).
- ❌ Calling `МультиязычностьПереопределяемый.ПриОпределенииНастроек(…)` from
  application code is not allowed — this is a hook, and БСП calls it at the needed moment.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures are available
via `python scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`:

- `МультиязычностьСервер.ИменаРеквизитовСУчетомКодаЯзыка(ИменаРеквизитов, КодЯзыка = "")`
  — mapping of attribute names with a language suffix appended (key is the original
  name, value is the name with the suffix); `ИменаРеквизитов` is a comma-separated
  string of names.
- `МультиязычностьСервер.СведенияОЯзыках()` — structure with configuration language
  information (codes, ordinals, primary language).
- `МультиязычностьКлиент.ПриОткрытии(Форма, Объект, Элемент, СтандартнаяОбработка)`
  — client `ПриОткрытии` handler for the form field, opening
  `ОбщаяФорма.ВводНаРазныхЯзыках` (called from the connectable form handler,
  not directly).
- `ОбщегоНазначения.КодОсновногоЯзыка()` / `ОбщегоНазначенияКлиент.КодОсновногоЯзыка()`
  — code of the infobase primary language (e.g. `"ru"`), stable. ⚠️ The service wrapper
  `МультиязычностьСервер.КодОсновногоЯзыка()` also exists, but in
  `СлужебныйПрограммныйИнтерфейс` — the stable
  `ОбщегоНазначения.КодОсновногоЯзыка()` is preferred.
- `МультиязычностьКлиент.ОткрытьФормуРегиональныхНастроек(ОписаниеОповещения = Неопределено, Параметры = Неопределено)`
  — ⚠️ `СлужебныйПрограммныйИнтерфейс`: opening the regional settings
  form (administrative scenario, not for application-level use).

To find the signature/region of any of these methods —
`python scripts/bsp_api.py method <Имя> --src src/cf`.