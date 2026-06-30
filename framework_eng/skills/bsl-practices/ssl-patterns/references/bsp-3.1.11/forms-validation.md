# Protection of attributes, additional properties, and change lock dates

Three related БСП subsystems tied to validation and data protection on managed
forms: **ЗапретРедактированияРеквизитовОбъектов** (locking "key" attributes
after saving), **Свойства** (additional attributes and data through the PVX
`ДополнительныеРеквизитыИСведения`, modules `УправлениеСвойствами*`) and
**ДатыЗапретаИзменения** (closing the accounting period - prohibiting writing/deletion of objects and
record sets in a closed period). Use them when you need to protect an attribute from accidental
change, connect custom properties to a form, or take the change lock date into account when
saving.

## Modules

**ЗапретРедактированияРеквизитовОбъектов:**

- `ЗапретРедактированияРеквизитовОбъектов` — server, stable API:
  `ЗаблокироватьРеквизиты`, `БлокируемыеРеквизитыОбъекта`, `НовыйБлокируемыйРеквизит`,
  `ОписаниеБлокируемогоРеквизита`.
- `ЗапретРедактированияРеквизитовОбъектовКлиент` — client, stable API:
  `РазрешитьРедактированиеРеквизитовОбъекта`, `УстановитьДоступностьЭлементовФормы`,
  `УстановитьРазрешенностьРедактированияРеквизитов`, `Реквизиты`.
- `ЗапретРедактированияРеквизитовОбъектовСлужебный` /
  `…СлужебныйКлиент` — ⚠️ internal (form internal attributes, cache).
- `ЗапретРедактированияРеквизитовОбъектовПереопределяемый` — **hook**:
  `ПриОпределенииОбъектовСЗаблокированнымиРеквизитами` (object registration),
  `ПриОпределенииЗаблокированныхРеквизитов` (targeted fine-tuning).

> ⚠️ There is no module `ЗапретРедактированияРеквизитовОбъектовКлиентСервер` and no method
> `СнятьЗапрет` — these are common mistakes by analogy. Unlocking is only through
> the user UI (command "Allow editing"), there is intentionally no server-side "unlock".

**Свойства (УправлениеСвойствами):**

- `УправлениеСвойствами` — server, stable API: `ПриСозданииНаСервере`,
  `ПриЧтенииНаСервере`, `ПередЗаписьюНаСервере`, `ОбработкаПроверкиЗаполнения`,
  `ЗначениеСвойства`, `ЗначенияСвойств`, `СвойстваОбъекта`, `ЗаписатьСвойстваУОбъекта`,
  `УстановитьСвойстваУОбъекта`, `ОбновитьЭлементыДополнительныхРеквизитов`.
- `УправлениеСвойствамиКлиент` — client, stable API:
  `ОбрабатыватьОповещения`, `ОбновитьЗависимостиДополнительныхРеквизитов`,
  `ПослеЗагрузкиДополнительныхРеквизитов`, `ВыполнитьКоманду`, `РедактироватьМетки`.
- `УправлениеСвойствамиПереопределяемый` — **hooks**:
  `ЗаполнитьНаборыСвойствОбъекта`, `ПриПолученииПредопределенныхНаборовСвойств`,
  `ПриПолученииНаименованийНаборовСвойств`.
- `УправлениеСвойствамиСлужебный` — ⚠️ internal.
- ⚠️ `УправлениеСвойствами.ПолучитьЗначенияСвойств`, `ПолучитьСписокСвойств`,
  `ПолучитьСписокЗначенийСвойств` — **deprecated** (region `УстаревшиеПроцедурыИФункции`);
  do not use in new code, alternatives are `ЗначенияСвойств`, `СвойстваОбъекта`.

> ⚠️ There is no module `УправлениеСвойствамиКлиентСервер`. Client and server
> code are split between `УправлениеСвойствами` and `УправлениеСвойствамиКлиент` (as in
> most БСП subsystems).

**ДатыЗапретаИзменения:**

- `ДатыЗапретаИзменения` — server, stable API: `ИзменениеЗапрещено`,
  `НайденЗапретИзмененияДанных`, `ОбъектПриЧтенииНаСервере`,
  `ПроверитьДатыЗапретаЗагрузкиДанных`, `ПараметрыСообщенияОЗапрете`,
  `ШаблонДанныхДляПроверки`, `ОтключитьПроверкуДатЗапрета`,
  `ПроверкаДатЗапретаОтключена`, and a series of event subscriptions
  (`ПроверитьДатуЗапретаИзмененияПередЗаписью`,
  `…ПередЗаписьюДокумента`, `…ПередЗаписьюНабораЗаписей`,
  `…ПередЗаписьюНабораЗаписейРегистраБухгалтерии`,
  `…ПередЗаписьюНабораЗаписейРегистраРасчета`, `…ПередУдалением`).
- `ДатыЗапретаИзмененияПереопределяемый` — **hook** for registering sections/objects with
  change lock dates (set of checks).
- ⚠️ `ДатыЗапретаИзменения.ОбновитьРазделыДатЗапретаИзменения` — **deprecated**
  (region `УстаревшиеПроцедурыИФункции`).

> Validation in forms with additional attributes is a platform contract
> (handlers `ОбработкаПроверкиЗаполненияНаСервере` of the form and `ОбработкаПроверкиЗаполнения`
> of the object); БСП extends it through `УправлениеСвойствами.ОбработкаПроверкиЗаполнения`.
> There is no separate module `ПроверкаЗаполненияСлужебный` in БСП — this is not an error,
> just how the export is structured.

## Scenarios

### 1. Lock object attributes on the form

**Task:** after saving the object, make the "key" attributes (cash currency,
contract organization) read-only; give a user with the `ПолныеПрава`/`РедактированиеРеквизитовОбъектов`
role the command "Allow editing".

**Functions:**
`ЗапретРедактированияРеквизитовОбъектов.ЗаблокироватьРеквизиты(Форма, ГруппаДляКнопкиЗапрета = Неопределено, ЗаголовокКнопкиЗапрета = "", Объект = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.
`ЗапретРедактированияРеквизитовОбъектовКлиент.РазрешитьРедактированиеРеквизитовОбъекта(Знач Форма, ОбработкаПродолжения = Неопределено, ТолькоВидимые = Истина) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Client.
`ЗапретРедактированияРеквизитовОбъектовКлиент.Реквизиты(Знач Форма, Знач ТолькоЗаблокированные = Истина, ТолькоВидимые = Истина) Экспорт` — Function → Array, `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `Форма` (УправляемаяФорма) — object form.
- `ГруппаДляКнопкиЗапрета` (ГруппаФормы / Неопределено) — group where the
  "Allow editing" button should be placed; `Неопределено` → default placement.
- `ЗаголовокКнопкиЗапрета` (Строка) — button text; `""` → default.
- `Объект` (ДанныеФормыСтруктура / Неопределено) — form object; `Неопределено` →
  `Форма.Объект`.
- `ОбработкаПродолжения` (ОписаниеОповещения / Неопределено) — handler after
  unlocking; called with `Булево` (unlock result).
- `ТолькоВидимые` (Булево) — `Истина` → unlock only visible attributes.

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    Если Объект.Ссылка.Пустая() Тогда
        Возврат;  // new object - no need to lock
    КонецЕсли;
    ЗапретРедактированияРеквизитовОбъектов.ЗаблокироватьРеквизиты(ЭтаФорма);
КонецПроцедуры

&НаКлиенте
Процедура Подключаемый_РазрешитьРедактированиеРеквизитовОбъекта(Команда)
    ЗапретРедактированияРеквизитовОбъектовКлиент.РазрешитьРедактированиеРеквизитовОбъекта(
        ЭтаФорма, Новый ОписаниеОповещения("ПослеРазблокировки", ЭтотОбъект));
КонецПроцедуры

&НаКлиенте
Процедура ПослеРазблокировки(Результат, ДополнительныеПараметры) Экспорт
    Если Результат = Истина Тогда
        // attributes unlocked
    КонецЕсли;
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Manually iterating over attributes and setting `Элементы.Валюта.ТолькоПросмотр = Истина` —
  you lose rights checking, the unlock button, conditional locking (for new
  objects), and integration with grouped attribute editing. One call to
  `ЗаблокироватьРеквизиты` does all of this.
- ❌ Calling the nonexistent `ЗапретРедактированияРеквизитовОбъектов.СнятьЗапрет(Форма)`
  — the method does not exist (intentionally, to avoid accidental unlock from code). Unlocking is
  only through the UI (form command → common form `РазблокированиеРеквизитов`).
- The `Подключаемый_РазрешитьРедактированиеРеквизитовОбъекта` button and its handler
  are added to the form automatically when `ЗаблокироватьРеквизиты` is called (if the user
  has the right and the object is editable).
- The list of lockable attributes on the server is
  `ЗапретРедактированияРеквизитовОбъектов.БлокируемыеРеквизитыОбъекта(ИмяОбъекта)`
  (returns an array of names from `ПолучитьБлокируемыеРеквизитыОбъекта` in the manager module).

### 2. Declare lockable attributes in the manager module

**Task:** in the object manager module, return a list of lockable attributes
simple strings and extended descriptions with warnings).

**Functions:**
`ЗапретРедактированияРеквизитовОбъектов.НовыйБлокируемыйРеквизит() Экспорт` — Function → Structure (extended description: `Имя`, `ЭлементыФормы` (Array of String), `Предупреждение`, `Группа`), `#Область ПрограммныйИнтерфейс` (stable). Server.
`ЗапретРедактированияРеквизитовОбъектов.ОписаниеБлокируемогоРеквизита() Экспорт` — Function → `Строка` (format `"ИмяРеквизита[;ИмяЭлементаФормы,...][;ИмяГруппы]"` — simple description format for the array returned by `ПолучитьБлокируемыеРеквизитыОбъекта`; for the extended field-based description use `НовыйБлокируемыйРеквизит`), `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:** none; constructor methods return empty structures to fill.

**Example:**
```bsl
// Manager module of the "Cash desks" directory
Функция ПолучитьБлокируемыеРеквизитыОбъекта() Экспорт
    БлокируемыеРеквизиты = Новый Массив;
    БлокируемыеРеквизиты.Добавить("Валюта");
    БлокируемыеРеквизиты.Добавить("Организация");

    // Extended description with a warning during unlock
    Реквизит = ЗапретРедактированияРеквизитовОбъектов.НовыйБлокируемыйРеквизит();
    Реквизит.Имя = "Ответственный";
    Реквизит.Предупреждение = НСтр("ru = 'Changing the responsible person affects all previously entered documents.'");
    БлокируемыеРеквизиты.Добавить(Реквизит);

    Возврат БлокируемыеРеквизиты;
КонецФункции
```

**Nuances / antipatterns:**
- Registering the object as a subsystem participant is done in the hook
  `ЗапретРедактированияРеквизитовОбъектовПереопределяемый.ПриОпределенииОбъектовСЗаблокированнымиРеквизитами(Объекты)`
  (application code implements it, БСП calls it). Without registration
  `ЗаблокироватьРеквизиты` will not work.
- The format `"Валюта"` (string) is a simple lock; `"Партнер;Партнер"` means that after `;`
  the names of form elements locked together with the attribute are listed.
- ❌ Storing the list of lockable attributes in form code instead of the manager module —
  breaks the single source of truth; `БлокируемыеРеквизитыОбъекта` reads exactly the manager
  module.

### 3. Connect the "Properties" subsystem to an object form

**Task:** in the object form, render additional attributes from the
`НаборыДополнительныхРеквизитовИСведений` set, read them correctly when opening, and
save them before writing.

**Functions:**
`УправлениеСвойствами.ПриСозданииНаСервере(Форма, ДополнительныеПараметры = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеСвойствами.ПриЧтенииНаСервере(Форма, ТекущийОбъект) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеСвойствами.ПередЗаписьюНаСервере(Форма, ТекущийОбъект) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеСвойствами.ОбновитьЭлементыДополнительныхРеквизитов(Форма, Объект = Неопределено, СкрытьУдаленные = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Форма` (УправляемаяФорма) — object form with properties (must have `Объект`
  with `Ссылка`).
- `ДополнительныеПараметры` (Неопределено / Структура) — optional properties:
  `Объект` (ДанныеФормыСтруктура), `ИмяЭлементаДляРазмещения` (Строка) — group for
  field placement, and others.
- `ТекущийОбъект` (ДанныеФормыСтруктура) — object from the `ПриЧтенииНаСервере` handler.

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    УправлениеСвойствами.ПриСозданииНаСервере(ЭтаФорма);
КонецПроцедуры

&НаСервере
Процедура ПриЧтенииНаСервере(ТекущийОбъект)
    УправлениеСвойствами.ПриЧтенииНаСервере(ЭтаФорма, ТекущийОбъект);
КонецПроцедуры

&НаСервере
Процедура ПередЗаписьюНаСервере(Отказ, ТекущийОбъект, ПараметрыЗаписи)
    УправлениеСвойствами.ПередЗаписьюНаСервере(ЭтаФорма, ТекущийОбъект);
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Forgetting `ПриЧтенииНаСервере` when opening a form from a list (bypassing
  `ПриСозданииНаСервере`) — additional attribute values will not be reread.
- ❌ Duplicating form internal attributes (`Свойства_ИспользоватьСвойства`,
  `Свойства_ОписаниеДополнительныхРеквизитов`, `ПараметрыСвойств`) with your own — БСП creates
  them in `ПриСозданииНаСервере`.
- `ОбновитьЭлементыДополнительныхРеквизитов` — rebuild fields after an administrator changes
  the set composition (adds/removes an attribute in
  `НаборыДополнительныхРеквизитовИСведений`).

### 4. Validate required additional attributes

**Task:** when saving an object, validate required additional attributes and
bind error messages to form fields; add custom application-specific checks.

**Function:**
`УправлениеСвойствами.ОбработкаПроверкиЗаполнения(Форма, Отказ, ПроверяемыеРеквизиты, Объект = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Форма` (УправляемаяФорма) — object form.
- `Отказ` (Булево) — output: `Истина` aborts saving.
- `ПроверяемыеРеквизиты` (Массив) — from the platform handler
  `ОбработкаПроверкиЗаполненияНаСервере(Отказ, ПроверяемыеРеквизиты)`.
- `Объект` (ДанныеФормыСтруктура / Неопределено) — object; `Неопределено` → `Форма.Объект`.

**Example:**
```bsl
&НаСервере
Процедура ОбработкаПроверкиЗаполненияНаСервере(Отказ, ПроверяемыеРеквизиты)
    // First line - the БСП bridge: extends validation with required additional attributes
    УправлениеСвойствами.ОбработкаПроверкиЗаполнения(ЭтаФорма, Отказ, ПроверяемыеРеквизиты);

    // Application-specific check with binding to a form attribute
    Если Объект.Сумма <= 0 Тогда
        ОбщегоНазначения.СообщитьПользователю(
            НСтр("ru = 'The amount must be greater than zero.'"),
            , "Объект.Сумма", , Отказ);
    КонецЕсли;
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Omitting the call to `УправлениеСвойствами.ОбработкаПроверкиЗаполнения` — the flag
  `ЗаполнятьОбязательно` in the PVX `ДополнительныеРеквизитыИСведения` is ignored,
  and the user will save the object with an empty required additional attribute.
- The platform already performed the "default" validation (`АвтоОтметкаНезаполненного`,
  `ПроверкаЗаполнения`); application code only **supplements** it and outputs messages
  through `ОбщегоНазначения.СообщитьПользователю` (see `base-common.md`).
- ❌ Calling the nonexistent `ПроверкаЗаполненияСлужебный.ПроверитьЗаполнениеРеквизитовОбъекта`
  — there is no such module in БСП; additional attribute validation is done only through
  `УправлениеСвойствами.ОбработкаПроверкиЗаполнения` from the form handler.

### 5. Programmatically read and write object properties

**Task:** read values of an object's additional attributes/data by reference
and programmatically write new values (for example, during exchange/import).

**Functions:**
`УправлениеСвойствами.ЗначениеСвойства(Объект, Свойство, КодЯзыка = "") Экспорт` — Function → Any, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеСвойствами.ЗначенияСвойств(ОбъектыСоСвойствами, ПолучатьДопРеквизиты = Истина, ПолучатьДопСведения = Истина, Свойства = Неопределено, КодЯзыка = "") Экспорт` — Function → ValueTable, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеСвойствами.СвойстваОбъекта(ВладелецСвойств, ПолучатьДопРеквизиты = Истина, ПолучатьДопСведения = Истина) Экспорт` — Function → Array, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеСвойствами.ЗаписатьСвойстваУОбъекта(ВладелецСвойств, ТаблицаСвойствИЗначений) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеСвойствами.УстановитьСвойстваУОбъекта(Владелец, Свойства) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Объект` / `ВладелецСвойств` (AnyReference) — owner object of the properties.
- `Свойство` (СправочникСсылка.ДополнительныеРеквизиты / Сведения) — property.
- `ОбъектыСоСвойствами` (Array / AnyReference) — array of references for `ЗначенияСвойств`.
- `ПолучатьДопРеквизиты` (Булево) — `Истина` → include additional attributes.
- `ПолучатьДопСведения` (Булево) — `Истина` → include additional data.
- `Свойства` (Array / Неопределено) — filter for specific properties.
- `ТаблицаСвойствИЗначений` (ValueTable) — columns `Свойство`, `Значение`.
- `Свойства` (Structure / Array) — for `УстановитьСвойстваУОбъекта`: property → value
  mapping.

**Example:**
```bsl
// Read one property
Значение = УправлениеСвойствами.ЗначениеСвойства(Ссылка, СвойствоИнн);

// Batch read attributes and data of several objects
ТаблицаСвойств = УправлениеСвойствами.ЗначенияСвойств(МассивСсылок);

// Write several properties at once
Таблица = Новый ТаблицаЗначений("Свойство,Значение");
Таблица.Добавить().Свойство = СвойствоИнн;     Таблица[0].Значение = "7701234567";
Таблица.Добавить().Свойство = СвойствоОтдел;  Таблица[1].Значение = Справочники.Отделы.Продажи;
УправлениеСвойствами.ЗаписатьСвойстваУОбъекта(Ссылка, Таблица);
```

**Nuances / antipatterns:**
- ❌ Using deprecated `УправлениеСвойствами.ПолучитьЗначенияСвойств` /
  `ПолучитьСписокСвойств` / `ПолучитьСписокЗначенийСвойств` (region
  `УстаревшиеПроцедурыИФункции`) — in new code the alternatives are `ЗначенияСвойств`,
  `СвойстваОбъекта`, `ПредставленияЗначенийСвойств`.
- `ЗначенияСвойств` as a batch call is more efficient than several `ЗначениеСвойства` calls —
  one database query.
- ❌ Directly writing to the object's `ДополнительныеРеквизиты` tabular section bypassing
  `ЗаписатьСвойстваУОбъекта` — type and mandatory checks are lost. Use only
  the `УправлениеСвойствами` API.

### 6. Update dependent attributes and handle property notifications

**Task:** after changing a controlling attribute, recalculate
visibility/accessibility/requiredness of dependent additional attributes; respond to
an administrator changing the property set.

**Functions:**
`УправлениеСвойствамиКлиент.ОбновитьЗависимостиДополнительныхРеквизитов(Форма, Объект = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Client.
`УправлениеСвойствамиКлиент.ОбрабатыватьОповещения(Форма, ИмяСобытия, Параметр) Экспорт` — Function → Boolean, `#Область ПрограммныйИнтерфейс` (stable). Client.
`УправлениеСвойствамиКлиент.ПослеЗагрузкиДополнительныхРеквизитов(Форма) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `Форма` (УправляемаяФорма) — object form with properties.
- `Объект` (ДанныеФормыСтруктура / Неопределено) — object; `Неопределено` → `Форма.Объект`.
- `ИмяСобытия` (Строка) — notification event name.
- `Параметр` (Произвольный) — notification parameter.

**Example:**
```bsl
&НаКлиенте
Процедура ПриИзмененииУправляющегоРеквизита(Элемент)
    УправлениеСвойствамиКлиент.ОбновитьЗависимостиДополнительныхРеквизитов(ЭтаФорма);
КонецПроцедуры

&НаКлиенте
Процедура ОбработкаОповещения(ИмяСобытия, Параметр, Источник)
    Если УправлениеСвойствамиКлиент.ОбрабатыватьОповещения(ЭтаФорма, ИмяСобытия, Параметр) Тогда
        УправлениеСвойствами.ОбновитьЭлементыДополнительныхРеквизитов(ЭтаФорма);
    КонецЕсли;
КонецПроцедуры
```

**Nuances / antipatterns:**
- ❌ Calling the nonexistent `УправлениеСвойствамиКлиентСервер.ОбновитьЗависимости…`
  — there is no such module; client code is entirely in `УправлениеСвойствамиКлиент`, server code in
  `УправлениеСвойствами`.
- `ОбрабатыватьОповещения` returns `Истина` if the notification belongs to the form's
  property set/additional attribute/data item — then the form updates elements; otherwise
  the notification is unrelated, do not react.
- `УправлениеСвойствамиКлиент.ОткрытьСписокСвойств(ИмяКоманды)` — ⚠️ internal (region
  `СлужебныйПрограммныйИнтерфейс`), backward compatibility is not guaranteed;
  use only for administrative commands, understanding the risk.

### 7. Lock a form when reading an object in a closed period

**Task:** when opening an object whose date falls into a closed period
(change lock date), lock the form for reading - the user sees that
editing is forbidden without separate checks in each handler.

**Function:**
`ДатыЗапретаИзменения.ОбъектПриЧтенииНаСервере(Форма, ТекущийОбъект) Экспорт` — Function, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Форма` (УправляемаяФорма) — form of an object item or register record.
- `ТекущийОбъект` (СправочникОбъект / ДокументОбъект / … / РегистрСведенийМенеджерЗаписи)
  — object from the `ПриЧтенииНаСервере` handler.

**Example:**
```bsl
&НаСервере
Процедура ПриЧтенииНаСервере(ТекущийОбъект)
    ДатыЗапретаИзменения.ОбъектПриЧтенииНаСервере(ЭтаФорма, ТекущийОбъект);
КонецПроцедуры
```

**Nuances / antipatterns:**
- The method is a `ПриЧтенииНаСервере` handler; it is embedded into forms of
  directory items, documents, and register records. When the lock triggers, the form
  switches to read-only mode.
- The write/delete subscriptions themselves (`ПроверитьДатуЗапретаИзмененияПередЗаписью*`)
  are connected by БСП through event subscriptions - application code usually does not call them
  directly; they fire automatically for objects registered in the subsystem
  (through `ДатыЗапретаИзмененияПереопределяемый`).
- For programmatic operations that bypass forms (exchange, scheduled jobs),
  use an explicit `ИзменениеЗапрещено` / `НайденЗапретИзмененияДанных`
  check (scenario 8) - subscriptions do not cover all write paths.

### 8. Programmatically check whether data changes are forbidden

**Task:** before programmatic writing/deleting of an object or record set in
a closed period, check whether the change is allowed and, if forbidden, obtain
an error description for the user.

**Functions:**
`ДатыЗапретаИзменения.ИзменениеЗапрещено(ДанныеИлиПолноеИмя, ИдентификаторДанных = Неопределено, ОписаниеОшибки = Null, УзелПроверкиЗапретаЗагрузки = Неопределено) Экспорт` — Function → Boolean, `#Область ПрограммныйИнтерфейс` (stable). Server.
`ДатыЗапретаИзменения.НайденЗапретИзмененияДанных(Знач ДанныеДляПроверки, ПараметрыСообщенияОЗапрете = Неопределено, ОписаниеОшибки = Null, УзелПроверкиЗапретаЗагрузки = Неопределено) Экспорт` — Function → Boolean, `#Область ПрограммныйИнтерфейс` (stable). Server.
`ДатыЗапретаИзменения.ШаблонДанныхДляПроверки() Экспорт` — Function → `ТаблицаЗначений` (columns `Дата` — Date, `Раздел` — String, `Объект` — AnyReference) for filling and passing to `НайденЗапретИзмененияДанных`, `#Область ПрограммныйИнтерфейс` (stable). Server.
`ДатыЗапретаИзменения.ПараметрыСообщенияОЗапрете() Экспорт` — Function → Structure, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ДанныеИлиПолноеИмя` (Object / RecordSet / String) — data object being checked
  (directory/document/... object, record set) or full metadata name.
- `ИдентификаторДанных` (Any / Неопределено) — additional identifier
  (for example, register record reference).
- `ОписаниеОшибки` (Null / String / Structure) — `Null` → no description needed; `String`
  → return text; `Structure` → return structured description (`ПредставлениеДанных`,
  `ЗаголовокОшибки`, `Запреты`).
- `УзелПроверкиЗапретаЗагрузки` (ПланОбменаСсылка / Неопределено) — node for checking
  the **load** prohibition during exchange; `Неопределено` → change prohibition check.
- `ДанныеДляПроверки` (`ТаблицаЗначений`) — from `ШаблонДанныхДляПроверки()`
  (columns `Дата`, `Раздел`, `Объект`); fill rows with data to check.
- `ПараметрыСообщенияОЗапрете` (Structure / Неопределено) — from
  `ПараметрыСообщенияОЗапрете()`; `Неопределено` → no message text is formed.

**Example:**
```bsl
// Check before programmatic object writing
ОписаниеОшибки = "";
Если ДатыЗапретаИзменения.ИзменениеЗапрещено(ДокументОбъект, , ОписаниеОшибки) Тогда
    ОбщегоНазначения.СообщитьПользователю(ОписаниеОшибки, , "Объект.Дата", , Отказ);
    Возврат;
КонецЕсли;

// Check load prohibition from the exchange node
Если ДатыЗапретаИзменения.ИзменениеЗапрещено(НаборЗаписей, , , УзелОбмена) Тогда
    // skip write, write to the registration log
КонецЕсли;

// Detailed check with a data template
ДанныеДляПроверки = ДатыЗапретаИзменения.ШаблонДанныхДляПроверки();
Строка = ДанныеДляПроверки.Добавить();
Строка.Дата   = ДокументОбъект.Дата;
Строка.Объект = ДокументОбъект.Ссылка;
Строка.Раздел = "Документы";  // section from ПриЗаполненииРазделовДатЗапретаИзменения
ОписаниеОшибки = "";
Если ДатыЗапретаИзменения.НайденЗапретИзмененияДанных(ДанныеДляПроверки, , ОписаниеОшибки) Тогда
    // ОписаниеОшибки — structure with ПредствалениеДанных, ЗаголовокОшибки, Запреты
КонецЕсли;
```

**Nuances / antipatterns:**
- ❌ Ignoring `ИзменениеЗапрещено` during programmatic writing in a scheduled job/exchange — the
  closed accounting period is violated, and data is pushed through despite the lock. Always check
  before writing objects in closed periods.
- The change lock date **does not apply** to data exchange (synchronization) —
  to protect against changes during exchange, use a separate load lock date and
  pass `УзелПроверкиЗапретаЗагрузки`.
- `ОтключитьПроверкуДатЗапрета(Истина)` / `ПроверкаДатЗапретаОтключена()` —
  programmatic disabling of the check for privileged code (for example, update handlers); use it
  extremely carefully and only inside `УстановитьПривилегированныйРежим`.
- ❌ Calling the deprecated `ДатыЗапретаИзменения.ОбновитьРазделыДатЗапретаИзменения()`
  (region `УстаревшиеПроцедурыИФункции`) — do not use in new code.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`, unless otherwise noted):

- `ЗапретРедактированияРеквизитовОбъектовКлиент.УстановитьРазрешенностьРедактированияРеквизитов(Знач Форма, Знач Реквизиты, Знач РедактированиеРазрешено = Истина, Знач ПравоРедактирования = Неопределено) Экспорт`
  — Procedure (client): programmatically mark attributes as allowed/forbidden.
- `ЗапретРедактированияРеквизитовОбъектовКлиент.УстановитьДоступностьЭлементовФормы(Знач Форма, Знач Реквизиты = Неопределено) Экспорт`
  — Procedure (client): makes elements available and disables the "Allow
  editing" button if all attributes are unlocked.
- `ЗапретРедактированияРеквизитовОбъектовКлиент.ПоказатьПредупреждениеВсеВидимыеРеквизитыРазблокированы(ОбработкаПродолжения = Неопределено) Экспорт`
  — Procedure (client): warning when there are no visible locked attributes.
- `УправлениеСвойствами.ПроверитьСвойствоУОбъекта(ВладелецСвойств, Свойство) Экспорт` —
  Function (server): checks whether a property exists for an object.
- `УправлениеСвойствами.ПредставлениеЗначенияСвойства(Объект, Свойство, КодЯзыка = "") Экспорт`
  and `УправлениеСвойствами.ПредставленияЗначенийСвойств(ОбъектыСоСвойствами, КодЯзыка = "") Экспорт`
  — Functions (server): string representations of property values (for reports/printing).
- `УправлениеСвойствами.ИспользоватьДопРеквизиты(ВладелецСвойств) Экспорт` /
  `УправлениеСвойствами.ИспользоватьДопСведения(ВладелецСвойств) Экспорт` /
  `УправлениеСвойствами.СвойстваДоступны() Экспорт` — Functions (server): checks
  whether properties are used for an object/globally.
- `УправлениеСвойствамиКлиент.ВыполнитьКоманду(Форма, Элемент = Неопределено, СтандартнаяОбработка = Неопределено, Объект = Неопределено) Экспорт`
  — Procedure (client): command handler for properties on a form (for example, "Open property
  list", "Edit labels").
- `ДатыЗапретаИзменения.ПроверитьДатыЗапретаЗагрузкиДанных(Данные, УзелПроверкиЗапретаЗагрузки, Отказ, ОписаниеОшибки = Null) Экспорт`
  — Procedure (server): checks the prohibition on loading data from an exchange node.
- `ДатыЗапретаИзменения.ДобавитьСтроку(Данные, Таблица, ПолеДаты, Раздел = "", ПолеОбъекта = "") Экспорт`
  — Procedure (server): fills the data table used for the prohibition check.

Override hooks (`*Переопределяемый`, region `ПрограммныйИнтерфейс`) - БСП
calls them, application code implements them in its own override module, and does NOT call them
directly:

- `ЗапретРедактированияРеквизитовОбъектовПереопределяемый.ПриОпределенииОбъектовСЗаблокированнымиРеквизитами(Объекты)`
  — registering objects with lockable attributes.
- `ЗапретРедактированияРеквизитовОбъектовПереопределяемый.ПриОпределенииЗаблокированныхРеквизитов(...)`
  — targeted fine-tuning of the lockable attribute set.
- `УправлениеСвойствамиПереопределяемый.ЗаполнитьНаборыСвойствОбъекта(Знач Объект, ТипСсылки, НаборыСвойств, СтандартнаяОбработка, КлючНазначения)`
  — binding an owner type to property sets.
- `УправлениеСвойствамиПереопределяемый.ПриПолученииПредопределенныхНаборовСвойств(Наборы)`
  — defining predefined property sets.
- `УправлениеСвойствамиПереопределяемый.ПриПолученииНаименованийНаборовСвойств(...)`
  — set names.
- `ДатыЗапретаИзмененияПереопределяемый.ПриЗаполненииРазделовДатЗапретаИзменения(Разделы)`
  — set composition for change lock dates (names + objects for each section). Without this hook
  only the common change lock date setting is available. Neighboring hooks:
  `ЗаполнитьИсточникиДанныхДляПроверкиЗапретаИзменения` (sources for checking),
  `ПередПроверкойЗапретаИзменения` (targeted check fine-tuning).

For signature/region lookup of any method use:
`python .claude/skills/bsp/scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`.
