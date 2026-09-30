# Protection of attributes, additional properties, and change prohibition dates

Three adjacent БСП subsystems related to validation and data protection in managed
forms: **ЗапретРедактированияРеквизитовОбъектов** (locking “key” attributes
after saving), **Свойства** (additional attributes and information through the ПВХ
`ДополнительныеРеквизитыИСведения`, modules `УправлениеСвойствами*`) and
**ДатыЗапретаИзменения** (accounting close period - prohibition of writing/deleting objects and
record sets in the closed period). Use when you need to protect an attribute from accidental
change, attach user properties to a form, or take the change prohibition date into account when
writing.

## Modules

**ЗапретРедактированияРеквизитовОбъектов:**

- `ЗапретРедактированияРеквизитовОбъектов` — server, stable API:
  `ЗаблокироватьРеквизиты`, `БлокируемыеРеквизитыОбъекта`, `НовыйБлокируемыйРеквизит`,
  `ОписаниеБлокируемогоРеквизита`.
- `ЗапретРедактированияРеквизитовОбъектовКлиент` — client, stable API:
  `РазрешитьРедактированиеРеквизитовОбъекта`, `УстановитьДоступностьЭлементовФормы`,
  `УстановитьРазрешенностьРедактированияРеквизитов`, `Реквизиты`.
- `ЗапретРедактированияРеквизитовОбъектовСлужебный` /
  `…СлужебныйКлиент` — ⚠️ service (form service attributes, cache).
- `ЗапретРедактированияРеквизитовОбъектовПереопределяемый` — **hook**:
  `ПриОпределенииОбъектовСЗаблокированнымиРеквизитами` (object registration),
  `ПриОпределенииЗаблокированныхРеквизитов` (targeted fine-tuning).

> ⚠️ There is no module `ЗапретРедактированияРеквизитовОбъектовКлиентСервер` and no method
> `СнятьЗапрет` — common mistakes by analogy. Unlocking is only through the
> user UI (the “Allow editing” command); there is intentionally no server-side “unlock”.

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
- `УправлениеСвойствамиСлужебный` — ⚠️ service.
- ⚠️ `УправлениеСвойствами.ПолучитьЗначенияСвойств`, `ПолучитьСписокСвойств`,
  `ПолучитьСписокЗначенийСвойств` — **obsolete** (region `УстаревшиеПроцедурыИФункции`);
  do not use in new code, alternatives are `ЗначенияСвойств`, `СвойстваОбъекта`.

> ⚠️ There is no module `УправлениеСвойствамиКлиентСервер`. Client and server
> code are split into `УправлениеСвойствами` and `УправлениеСвойствамиКлиент` (as in
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
  change restriction dates (validation set).
- ⚠️ `ДатыЗапретаИзменения.ОбновитьРазделыДатЗапретаИзменения` — **deprecated**
  (region `УстаревшиеПроцедурыИФункции`).

> Validation of filled values in forms with additional attributes is a platform contract
> (handlers `ОбработкаПроверкиЗаполненияНаСервере` of the form and `ОбработкаПроверкиЗаполнения`
> of the object); БСП extends it through `УправлениеСвойствами.ОбработкаПроверкиЗаполнения`.
> There is no separate module `ПроверкаЗаполненияСлужебный` in БСП - this is not an error, but a reality
> of the export.

## Scenarios

### 1. Lock object attributes in a form

**Task:** after saving the object, make the "key" attributes (cash currency,
contract organization) read-only; give a user with the role
`ПолныеПрава`/`РедактированиеРеквизитовОбъектов` the command "Allow editing".

**Functions:**
`ЗапретРедактированияРеквизитовОбъектов.ЗаблокироватьРеквизиты(Форма, ГруппаДляКнопкиЗапрета = Неопределено, ЗаголовокКнопкиЗапрета = "", Объект = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.
`ЗапретРедактированияРеквизитовОбъектовКлиент.РазрешитьРедактированиеРеквизитовОбъекта(Знач Форма, ОбработкаПродолжения = Неопределено, ТолькоВидимые = Истина) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Client.
`ЗапретРедактированияРеквизитовОбъектовКлиент.Реквизиты(Знач Форма, Знач ТолькоЗаблокированные = Истина, ТолькоВидимые = Истина) Экспорт` — Function → Array, `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `Форма` (УправляемаяФорма) — the object form.
- `ГруппаДляКнопкиЗапрета` (ГруппаФормы / Неопределено) — the group where the
  "Allow editing" button should be placed; `Неопределено` → the default placement.
- `ЗаголовокКнопкиЗапрета` (Строка) — button text; `""` → default.
- `Объект` (ДанныеФормыСтруктура / Неопределено) — the form object; `Неопределено` →
  `Форма.Объект`.
- `ОбработкаПродолжения` (ОписаниеОповещения / Неопределено) — handler after
  unlocking; called with `Булево` (unlock result).
- `ТолькоВидимые` (Булево) — `Истина` → unlock only visible attributes.

**Example:**
```bsl
&НаСервере
Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    Если Объект.Ссылка.Пустая() Тогда
        Возврат;  // новый объект — блокировать не нужно
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
        // реквизиты разблокированы
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Manually iterating over attributes and setting `Элементы.Валюта.ТолькоПросмотр = Истина` —
  you lose: rights checking, the unlock button, conditional locking (for new
  objects), integration with bulk attribute changes. One call to
  `ЗаблокироватьРеквизиты` does all of this.
- ❌ Calling the non-existent `ЗапретРедактированияРеквизитовОбъектов.СнятьЗапрет(Форма)`
  — method is absent (intentionally, to avoid accidental unlocking by code). Unlocking is
  only through the UI (form command → common form `РазблокированиеРеквизитов`).
- The button `Подключаемый_РазрешитьРедактированиеРеквизитовОбъекта` and its handler
  are added to the form automatically when `ЗаблокироватьРеквизиты` is called (if the user
  has the right and has permission to edit the object).
- The list of blockable attributes on the server is
  `ЗапретРедактированияРеквизитовОбъектов.БлокируемыеРеквизитыОбъекта(ИмяОбъекта)`
  (returns an array of names from `ПолучитьБлокируемыеРеквизитыОбъекта` in the manager module).

### 2. Declare blockable attributes in the manager module

**Task:** in the object manager module, return a list of blockable attributes
(simple strings and extended descriptions with a warning).

**Functions:**
`ЗапретРедактированияРеквизитовОбъектов.НовыйБлокируемыйРеквизит() Экспорт` — Function → Structure (Имя, ЭлементыФормы (Array of String), Группа, ПредставлениеГруппы, Предупреждение, ПредупреждениеГруппы), `#Область ПрограммныйИнтерфейс` (stable). Server.
`ЗапретРедактированияРеквизитовОбъектов.ОписаниеБлокируемогоРеквизита() Экспорт` — Function → `Строка` (format `"ИмяРеквизита[;ИмяЭлементаФормы,...][;ИмяГруппы]"` — a simple description variant for the array from `ПолучитьБлокируемыеРеквизитыОбъекта`; for the extended description with fields, use `НовыйБлокируемыйРеквизит`), `#Область ДляВызоваИзДругихПодсистем` (semi-stable). Server.

**Parameters:** none; constructor methods return empty structures to fill in.

**Example:**
```bsl
// Manager module of the catalog "Кассы"
Функция ПолучитьБлокируемыеРеквизитыОбъекта() Экспорт
    БлокируемыеРеквизиты = Новый Массив;
    БлокируемыеРеквизиты.Добавить("Валюта");
    БлокируемыеРеквизиты.Добавить("Организация");

    // Extended description with a warning when unlocking
    Реквизит = ЗапретРедактированияРеквизитовОбъектов.НовыйБлокируемыйРеквизит();
    Реквизит.Имя = "Ответственный";
    Реквизит.Предупреждение = НСтр("ru = 'Changing the responsible person affects all previously entered documents.'");
    БлокируемыеРеквизиты.Добавить(Реквизит);

    Возврат БлокируемыеРеквизиты;
КонецФункции
```

**Nuances / anti-patterns:**
- Registering the object as a subsystem participant is done in the hook
  `ЗапретРедактированияРеквизитовОбъектовПереопределяемый.ПриОпределенииОбъектовСЗаблокированнымиРеквизитами(Объекты)`
  (application code implements it, БСП calls it). Without registration,
  `ЗаблокироватьРеквизиты` will not work.
- The format `"Валюта"` (string) is a simple lock; `"Партнер;Партнер"` means that after `;`
  the names of form elements are listed, and they are locked together with the attribute.
- ❌ Storing the list of blockable attributes in the form code instead of the manager module
  violates the single source of truth; `БлокируемыеРеквизитыОбъекта` reads the manager
  module itself.

### 3. Connect the "Properties" subsystem to the object form

**Task:** render additional attributes from the `НаборыДополнительныхРеквизитовИСведений` set in the object form, read them correctly when opening, and save them before writing.

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
  placing fields, etc.
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

**Nuances / anti-patterns:**
- ❌ Forget `ПриЧтенииНаСервере` when opening a form from a list, bypassing
  `ПриСозданииНаСервере` — additional attribute values will not be reread.
- ❌ Duplicate service form attributes (`Свойства_ИспользоватьСвойства`,
  `Свойства_ОписаниеДополнительныхРеквизитов`, `ПараметрыСвойств`) with your own — БСП itself
  creates them in `ПриСозданииНаСервере`.
- `ОбновитьЭлементыДополнительныхРеквизитов` — rebuild the fields after an administrator
  changes the composition of the set (added/removed an attribute in
  `НаборыДополнительныхРеквизитовИСведений`).

### 4. Check required additional attributes are filled in

**Task:** when saving the object, check required additional attributes and
attach error messages to form fields; add custom application-level
checks.

**Function:**
`УправлениеСвойствами.ОбработкаПроверкиЗаполнения(Форма, Отказ, ПроверяемыеРеквизиты, Объект = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Форма` (УправляемаяФорма) — object form.
- `Отказ` (Булево) — output: `Истина` interrupts saving.
- `ПроверяемыеРеквизиты` (Массив) — from the platform handler
  `ОбработкаПроверкиЗаполненияНаСервере(Отказ, ПроверяемыеРеквизиты)`.
- `Объект` (ДанныеФормыСтруктура / Неопределено) — object; `Неопределено` → `Форма.Объект`.

**Example:**
```bsl
&НаСервере
Процедура ОбработкаПроверкиЗаполненияНаСервере(Отказ, ПроверяемыеРеквизиты)
    // First line is the БСП bridge: extends validation with required additional attributes
    УправлениеСвойствами.ОбработкаПроверкиЗаполнения(ЭтаФорма, Отказ, ПроверяемыеРеквизиты);

    // Application-level check tied to a form attribute
    Если Объект.Сумма <= 0 Тогда
        ОбщегоНазначения.СообщитьПользователю(
            НСтр("ru = 'Сумма должна быть больше нуля.'"),
            , "Объект.Сумма", , Отказ);
    КонецЕсли;
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Omit the call to `УправлениеСвойствами.ОбработкаПроверкиЗаполнения` — the `ЗаполнятьОбязательно` flag in the `ДополнительныеРеквизитыИСведения` catalog is ignored,
  and the user will save the object with an empty required additional attribute.
- The platform has already performed the “default” validation (`АвтоОтметкаНезаполненного`,
  `ПроверкаЗаполнения`); application code only **supplements** it and outputs messages
  through `ОбщегоНазначения.СообщитьПользователю` (see `base-common.md`).
- ❌ Call the nonexistent `ПроверкаЗаполненияСлужебный.ПроверитьЗаполнениеРеквизитовОбъекта`
  — there is no such module in БСП; validation of additional attributes is done only through
  `УправлениеСвойствами.ОбработкаПроверкиЗаполнения` from the form handler.

### 5. Programmatically read and write object properties

**Task:** read the values of additional attributes/information of an object by reference
and programmatically write new values (e.g., during exchange/loading).

**Functions:**
`УправлениеСвойствами.ЗначениеСвойства(Объект, Свойство, КодЯзыка = "") Экспорт` — Function → Any, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеСвойствами.ЗначенияСвойств(ОбъектыСоСвойствами, ПолучатьДопРеквизиты = Истина, ПолучатьДопСведения = Истина, Свойства = Неопределено, КодЯзыка = "") Экспорт` — Function → ValueTable, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеСвойствами.СвойстваОбъекта(ВладелецСвойств, ПолучатьДопРеквизиты = Истина, ПолучатьДопСведения = Истина) Экспорт` — Function → Array, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеСвойствами.ЗаписатьСвойстваУОбъекта(ВладелецСвойств, ТаблицаСвойствИЗначений) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеСвойствами.УстановитьСвойстваУОбъекта(Владелец, Свойства) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Объект` / `ВладелецСвойств` (AnyRef) — property owner object.
- `Свойство` (СправочникСсылка.ДополнительныеРеквизиты / Сведения) — property.
- `ОбъектыСоСвойствами` (Array / AnyRef) — array of references for `ЗначенияСвойств`.
- `ПолучатьДопРеквизиты` (Boolean) — `Истина` → include additional attributes.
- `ПолучатьДопСведения` (Boolean) — `Истина` → include additional information.
- `Свойства` (Array / Неопределено) — selection of specific properties.
- `ТаблицаСвойствИЗначений` (ValueTable) — columns `Свойство`, `Значение`.
- `Свойства` (Structure / Array) — for `УстановитьСвойстваУОбъекта`: mapping
  property → value.

**Example:**
```bsl
// Read a single property
Значение = УправлениеСвойствами.ЗначениеСвойства(Ссылка, СвойствоИнн);

// Batch-read attributes and information for several objects
ТаблицаСвойств = УправлениеСвойствами.ЗначенияСвойств(МассивСсылок);

// Write several properties at once
Таблица = Новый ТаблицаЗначений("Свойство,Значение");
Таблица.Добавить().Свойство = СвойствоИнн;     Таблица[0].Значение = "7701234567";
Таблица.Добавить().Свойство = СвойствоОтдел;  Таблица[1].Значение = Справочники.Отделы.Продажи;
УправлениеСвойствами.ЗаписатьСвойстваУОбъекта(Ссылка, Таблица);
```

**Nuances / antipatterns:**
- ❌ Use deprecated `УправлениеСвойствами.ПолучитьЗначенияСвойств` /
  `ПолучитьСписокСвойств` / `ПолучитьСписокЗначенийСвойств` (region
  `УстаревшиеПроцедурыИФункции`) — in new code, prefer `ЗначенияСвойств`,
  `СвойстваОбъекта`, `ПредставленияЗначенийСвойств`.
- `ЗначенияСвойств` is more efficient as a batch call than several `ЗначениеСвойства` —
  one query to the database.
- ❌ Directly writing to the object's `ДополнительныеРеквизиты` tabular section bypassing
  `ЗаписатьСвойстваУОбъекта` — type and mandatory constraints are lost. Only
  through the `УправлениеСвойствами` API.

### 6. Update dependent attributes and handle property notifications

**Task:** after changing the controlling attribute, recalculate the visibility/accessibility/requiredness of dependent additional attributes; respond to changes in the property set made by an administrator.

**Functions:**
`УправлениеСвойствамиКлиент.ОбновитьЗависимостиДополнительныхРеквизитов(Форма, Объект = Неопределено) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Client.
`УправлениеСвойствамиКлиент.ОбрабатыватьОповещения(Форма, ИмяСобытия, Параметр) Экспорт` — Function → Boolean, `#Область ПрограммныйИнтерфейс` (stable). Client.
`УправлениеСвойствамиКлиент.ПослеЗагрузкиДополнительныхРеквизитов(Форма) Экспорт` — Procedure, `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `Форма` (ManagedForm) — object form with properties.
- `Объект` (FormDataStructure / Undefined) — object; `Undefined` → `Форма.Объект`.
- `ИмяСобытия` (String) — notification event name.
- `Параметр` (Any) — notification parameter.

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

**Nuances / anti-patterns:**
- ❌ Call a non-existent `УправлениеСвойствамиКлиентСервер.ОбновитьЗависимости…`
  — there is no such module; client code is entirely in `УправлениеСвойствамиКлиент`, server code is in
  `УправлениеСвойствами`.
- `ОбрабатыватьОповещения` returns `True` if the notification relates to the set of
  properties/additional attribute/form source — then the form updates the elements; otherwise
  the notification is unrelated, do not react.
- `УправлениеСвойствамиКлиент.ОткрытьСписокСвойств(ИмяКоманды)` — ⚠️ internal (region
  `СлужебныйПрограммныйИнтерфейс`), backward compatibility is not guaranteed;
  use only for administrator commands, understanding the risk.

### 7. Block the form when reading an object in a closed period

**Task:** when opening an object whose date falls within a closed period
(change prohibition date), block the form for read-only access so the user sees that
editing is forbidden, without separate checks in each handler.

**Function:**
`ДатыЗапретаИзменения.ОбъектПриЧтенииНаСервере(Форма, ТекущийОбъект) Экспорт` — Function → Boolean (True if the check is skipped, False otherwise; when prohibited, sets `Форма.ТолькоПросмотр = Истина`), `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Форма` (УправляемаяФорма) — form of an object item or register record.
- `ТекущийОбъект` (СправочникОбъект / ДокументОбъект / … / РегистрСведенийМенеджерЗаписи)
  — object from the `ПриЧтенииНаСервере` handler.

**Example:**
```bsl
&НаСервере
Процедура ПриЧтенииНаСервере(ТекущийОбъект)
    ДатыЗапретаИзменения.ОбъектПриЧтенииНаСервере(ЭтаФорма, ТекущийObject);
КонецПроцедуры
```

**Nuances / anti-patterns:**
- The method is the `ПриЧтенииНаСервере` handler; it is embedded into forms of
  catalog items, documents, and register records. When the prohibition is triggered, the form
  switches to “read only” mode.
- The subscriptions for write/delete (`ПроверитьДатуЗапретаИзмененияПередЗаписью*`)
  are connected by БСП through event subscriptions — application code usually does not call them
  directly; they are triggered automatically for objects registered in the
  subsystem (via `ДатыЗапретаИзмененияПереопределяемый`).
- For programmatic operations that bypass forms (exchange, scheduled jobs),
  use an explicit `ИзменениеЗапрещено` / `НайденЗапретИзмененияДанных`
  check (scenario 8) — subscriptions do not cover all write paths.

### 8. Programmatically check whether data modification is prohibited

**Task:** before programmatic write/delete of an object or record set in a
closed period, check whether modification is allowed, and if prohibited, obtain
an error description for the user.

**Functions:**
`ДатыЗапретаИзменения.ИзменениеЗапрещено(ДанныеИлиПолноеИмя, ИдентификаторДанных = Неопределено, ОписаниеОшибки = Null, УзелПроверкиЗапретаЗагрузки = Неопределено) Экспорт` — Function → Boolean, `#Область ПрограммныйИнтерфейс` (stable). Server.
`ДатыЗапретаИзменения.НайденЗапретИзмененияДанных(Знач ДанныеДляПроверки, ПараметрыСообщенияОЗапрете = Неопределено, ОписаниеОшибки = Null, УзелПроверкиЗапретаЗагрузки = Неопределено) Экспорт` — Function → Boolean, `#Область ПрограммныйИнтерфейс` (stable). Server.
`ДатыЗапретаИзменения.ШаблонДанныхДляПроверки() Экспорт` — Function → `ТаблицаЗначений` (columns `Дата` — Date, `Раздел` — String | ПланВидовХарактеристикСсылка.РазделыДатЗапретаИзменения, `Объект` — type PВХ РазделыДатЗапретаИзменения, not ЛюбаяСсылка) for filling and passing to `НайденЗапретИзмененияДанных`, `#Область ПрограммныйИнтерфейс` (stable). Server.
`ДатыЗапретаИзменения.ПараметрыСообщенияОЗапрете() Экспорт` — Function → Structure, `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ДанныеИлиПолноеИмя` (Объект / НаборЗаписей / Строка) — data object being checked
  (catalog/document/… object, record set) or full metadata name.
- `ИдентификаторДанных` (Произвольный / Неопределено) — additional identifier
  (for example, register record reference).
- `ОписаниеОшибки` (Null / Строка / Структура) — `Null` → description not needed; `Строка`
  → return text; `Структура` → return structured description (`ПредставлениеДанных`,
  `ЗаголовокОшибки`, `Запреты`).
- `УзелПроверкиЗапретаЗагрузки` (ПланОбменаСсылка / Неопределено) — node for checking the prohibition of data loading during exchange; `Неопределено` → check the prohibition of changes.
- `ДанныеДляПроверки` (`ТаблицаЗначений`) — from `ШаблонДанныхДляПроверки()`
  (columns `Дата`, `Раздел`, `Объект`); fill the rows with the data to be checked.
- `ПараметрыСообщенияОЗапрете` (Структура / Неопределено) — from
  `ПараметрыСообщенияОЗапрете()`; `Неопределено` → the message text is not generated.

**Example:**
```bsl
// Проверка перед программной записью объекта
ОписаниеОшибки = "";
Если ДатыЗапретаИзменения.ИзменениеЗапрещено(ДокументОбъект, , ОписаниеОшибки) Тогда
    ОбщегоНазначения.СообщитьПользователю(ОписаниеОшибки, , "Объект.Дата", , Отказ);
    Возврат;
КонецЕсли;

// Проверка запрета загрузки из узла обмена
Если ДатыЗапретаИзменения.ИзменениеЗапрещено(НаборЗаписей, , , УзелОбмена) Тогда
    // пропустить запись, записать в журнал регистрации
КонецЕсли;

// Детальная проверка с шаблоном данных
ДанныеДляПроверки = ДатыЗапретаИзменения.ШаблонДанныхДляПроверки();
Строка = ДанныеДляПроверки.Добавить();
Строка.Дата   = ДокументОбъект.Дата;
Строка.Объект = ДокументОбъект.Ссылка;
Строка.Раздел = "Документы";  // раздел из ПриЗаполненииРазделовДатЗапретаИзменения
ОписаниеОшибки = "";
Если ДатыЗапретаИзменения.НайденЗапретИзмененияДанных(ДанныеДляПроверки, , ОписаниеОшибки) Тогда
    // ОписаниеОшибки — структура с ПредставлениеДанных, ЗаголовокОшибки, Запреты
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Ignore `ИзменениеЗапрещено` during programmatic write in a scheduled
  job/exchange — this violates the accounting close period, and data is pushed
  past the restriction. Always check before writing objects in closed periods.
- The change prohibition date **does not apply** to data exchange (synchronization) —
  to protect against changes during exchange, use a separate loading prohibition date and
  pass `УзелПроверкиЗапретаЗагрузки`.
- `ОтключитьПроверкуДатЗапрета(Истина)` / `ПроверкаДатЗапретаОтключена()` —
  programmatic disabling of the check for privileged code (for example, update
  handlers); use extremely carefully and only inside the
  `УстановитьПривилегированныйРежим` wrapper.
- ❌ Call the obsolete `ДатыЗапретаИзменения.ОбновитьРазделыДатЗапретаИзменения()`
  (`УстаревшиеПроцедурыИФункции` region) — do not use it in new code.

## Additional

Other stable methods (region `ПрограммныйИнтерфейс`, unless otherwise specified):

- `ЗапретРедактированияРеквизитовОбъектовКлиент.УстановитьРазрешенностьРедактированияРеквизитов(Знач Форма, Знач Реквизиты, Знач РедактированиеРазрешено = Истина, Знач ПравоРедактирования = Неопределено) Экспорт`
  — Procedure (client): programmatically mark attributes as allowed/blocked for editing.
- `ЗапретРедактированияРеквизитовОбъектовКлиент.УстановитьДоступностьЭлементовФормы(Знач Форма, Знач Реквизиты = Неопределено) Экспорт`
  — Procedure (client): makes elements available and disables the "Allow
  editing" button if all attributes are unblocked.
- `ЗапретРедактированияРеквизитовОбъектовКлиент.ПоказатьПредупреждениеВсеВидимыеРеквизитыРазблокированы(ОбработкаПродолжения = Неопределено) Экспорт`
  — Procedure (client): warning when there are no visible blocked attributes.
- `УправлениеСвойствами.ПроверитьСвойствоУОбъекта(ВладелецСвойств, Свойство) Экспорт` —
  Function (server): checks whether an object has a property.
- `УправлениеСвойствами.ПредставлениеЗначенияСвойства(Объект, Свойство, КодЯзыка = "") Экспорт`
  and `УправлениеСвойствами.ПредставленияЗначенийСвойств(ОбъектыСоСвойствами, КодЯзыка = "") Экспорт`
  — Functions (server): string representations of property values (for reports/printing).
- `УправлениеСвойствами.ИспользоватьДопРеквизиты(ВладелецСвойств) Экспорт` /
  `УправлениеСвойствами.ИспользоватьДопСведения(ВладелецСвойств) Экспорт` /
  `УправлениеСвойствами.СвойстваДоступны() Экспорт` — Functions (server): checks
  whether properties are used for an object/globally.
- `УправлениеСвойствамиКлиент.ВыполнитьКоманду(Форма, Элемент = Неопределено, СтандартнаяОбработка = Неопределено, Объект = Неопределено) Экспорт`
  — Procedure (client): property command handler in the form (e.g. "Open property list",
  "Edit labels").
- `ДатыЗапретаИзменения.ПроверитьДатыЗапретаЗагрузкиДанных(Данные, УзелПроверкиЗапретаЗагрузки, Отказ, ОписаниеОшибки = Null) Экспорт`
  — Procedure (server): checks the prohibition on loading data from the exchange node.
- `ДатыЗапретаИзменения.ДобавитьСтроку(Данные, Таблица, ПолеДаты, Раздел = "", ПолеОбъекта = "") Экспорт`
  — Procedure (server): fills the data table for the prohibition check.

Override hooks (`*Переопределяемый`, region `ПрограммныйИнтерфейс`) — БСП
calls them, application code implements them in its override module, does NOT call them
 directly:

- `ЗапретРедактированияРеквизитовОбъектовПереопределяемый.ПриОпределенииОбъектовСЗаблокированнымиРеквизитами(Объекты)`
  — registration of objects with blocked attributes.
- `ЗапретРедактированияРеквизитовОбъектовПереопределяемый.ПриОпределенииЗаблокированныхРеквизитов(ИмяОбъектаМетаданных, ЗаблокированныеРеквизиты)`
  — targeted adjustment of the set of blocked attributes.
- `УправлениеСвойствамиПереопределяемый.ЗаполнитьНаборыСвойствОбъекта(Знач Объект, ТипСсылки, НаборыСвойств, СтандартнаяОбработка, КлючНазначения)`
  — binding the owner type to property sets.
- `УправлениеСвойствамиПереопределяемый.ПриПолученииПредопределенныхНаборовСвойств(Наборы)`
  — setting predefined property sets.
- `УправлениеСвойствамиПереопределяемый.ПриПолученииНаименованийНаборовСвойств(Наименования, КодЯзыка)`
  — property set names.
- `ДатыЗапретаИзмененияПереопределяемый.ПриЗаполненииРазделовДатЗапретаИзменения(Разделы)`
  — composition of prohibition date sections (names + objects of each section). Without this hook
  only the common restriction date setting is available. Adjacent hooks:
  `ЗаполнитьИсточникиДанныхДляПроверкиЗапретаИзменения` (data sources for the check),
  `ПередПроверкойЗапретаИзменения` (targeted check tuning).

To search for signatures/regions of any method —
`python .claude/skills/bsp/scripts/bsp_api.py method <Имя> --module <Модуль> --src src/cf`.