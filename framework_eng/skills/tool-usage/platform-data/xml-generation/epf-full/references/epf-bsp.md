# EPF БСП — registration in the subsystem "Additional Reports and Data Processors"

Working with the БСП subsystem "Additional Reports and Data Processors": generating the `СведенияОВнешнейОбработке()` function and adding commands to the EPF/ERF object module. Pure BSL - does not require DESIGNER, works cross-platform.

## Operation 1: Initialize `СведенияОВнешнейОбработке`

Adds the `СведенияОВнешнейОбработке()` function to the processing object module, which is required for registration in the БСП subsystem.

### Prerequisites

The processing has been created (there is an `ObjectModule.bsl`). If it has not been created yet, first run `epf init` (§1 of the main SKILL.md).

### Processing type mapping

The user may specify the type in free form. Determine the required one from the context:

| User writes | Type | API method |
|-------------|------|------------|
| additional processing, processing, global | ДополнительнаяОбработка | `ВидОбработкиДополнительнаяОбработка()` |
| additional report, global report | ДополнительныйОтчет | `ВидОбработкиДополнительныйОтчет()` |
| fill, populate | ЗаполнениеОбъекта | `ВидОбработкиЗаполнениеОбъекта()` |
| report (assignable, for object) | Отчет | `ВидОбработкиОтчет()` |
| print form, print | ПечатнаяФорма | `ВидОбработкиПечатнаяФорма()` |
| create related objects | СозданиеСвязанныхОбъектов | `ВидОбработкиСозданиеСвязанныхОбъектов()` |

**Assigned types** (require `Назначение`): ЗаполнениеОбъекта, Отчет, ПечатнаяФорма, СозданиеСвязанныхОбъектов.

**Global types** (without `Назначение`): ДополнительнаяОбработка, ДополнительныйОтчет.

### Default command type

| Type | Default `ТипКоманды` |
|------|----------------------|
| ДополнительнаяОбработка | `ТипКомандыОткрытиеФормы()` |
| ДополнительныйОтчет | `ТипКомандыОткрытиеФормы()` |
| ЗаполнениеОбъекта | `ТипКомандыВызовСерверногоМетода()` |
| Отчет | `ТипКомандыОткрытиеФормы()` |
| ПечатнаяФорма | `ТипКомандыВызовСерверногоМетода()` |
| СозданиеСвязанныхОбъектов | `ТипКомандыВызовСерверногоМетода()` |

### Template: `СведенияОВнешнейОбработке`

```bsl
Функция СведенияОВнешнейОбработке() Экспорт

	МетаданныеОбработки = Метаданные();

	ПараметрыРегистрации = ДополнительныеОтчетыИОбработки.СведенияОВнешнейОбработке("2.2.2.1");
	ПараметрыРегистрации.Вид    = ДополнительныеОтчетыИОбработкиКлиентСервер.{{ВидОбработки}};
	ПараметрыРегистрации.Версия = "1.0";

	{{СЕКЦИЯ_НАЗНАЧЕНИЕ}}

	НоваяКоманда = ПараметрыРегистрации.Команды.Добавить();
	НоваяКоманда.Представление        = МетаданныеОбработки.Представление();
	НоваяКоманда.Идентификатор        = МетаданныеОбработки.Имя;
	НоваяКоманда.Использование        = ДополнительныеОтчетыИОбработкиКлиентСервер.{{ТипКоманды}};
	НоваяКоманда.ПоказыватьОповещение = Ложь;
	{{СЕКЦИЯ_МОДИФИКАТОР}}

	Возврат ПараметрыРегистрации;

КонецФункции
```

#### Substitutions

- `{{ВидОбработки}}` - API method from the processing type mapping table
- `{{ТипКоманды}}` - API method from the default command type table

#### Conditional sections

**`{{СЕКЦИЯ_НАЗНАЧЕНИЕ}}`** - only for assigned types. One line for each target object:

```bsl
	ПараметрыРегистрации.Назначение.Добавить("Документ.СчетНаОплату");
```

Format: `MetadataObjectClassName.ObjectName` (for example `Документ.СчетНаОплату`, `Справочник.Контрагенты`).

For global types (ДополнительнаяОбработка, ДополнительныйОтчет), do not insert the section.

**`{{СЕКЦИЯ_МОДИФИКАТОР}}`** - only for `ПечатнаяФорма`:

```bsl
	НоваяКоманда.Модификатор = "ПечатьMXL";
```

For all other types, do not insert the line.

### Handler templates

For types with command type `ВызовСерверногоМетода`, add a handler procedure to the `ПрограммныйИнтерфейс` region, after `СведенияОВнешнейОбработке`.

#### ЗаполнениеОбъекта / СозданиеСвязанныхОбъектов

```bsl
Процедура ВыполнитьКоманду(ИдентификаторКоманды, ОбъектыНазначения, ПараметрыВыполненияКоманды) Экспорт

	// TODO: Реализация

КонецПроцедуры
```

#### ПечатнаяФорма

```bsl
Процедура Печать(МассивОбъектов, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода) Экспорт

	// TODO: Реализация

КонецПроцедуры
```

#### ДополнительнаяОбработка / ДополнительныйОтчет (if server call is selected)

```bsl
Процедура ВыполнитьКоманду(ИдентификаторКоманды, ПараметрыВыполненияКоманды) Экспорт

	// TODO: Реализация

КонецПроцедуры
```

Global handlers do not have the `ОбъектыНазначения` parameter.

### Execution steps

1. Find `ObjectModule.bsl` through Glob: `<SrcDir>/<ProcessorName>/Ext/ObjectModule.bsl` (default `SrcDir = src`)
2. Read the file
3. If `СведенияОВнешнейОбработке` already exists, notify the user and do not duplicate it
4. If the file is not found, suggest creating the processing first via `epf init`
5. Find the `#Область ПрограммныйИнтерфейс` ... `#КонецОбласти` region
6. Insert the `СведенияОВнешнейОбработке()` function inside this region
7. If the type requires a server handler, insert it in the same region, after the function
8. Use tabs for indentation (as in the source file)

### Example: `ПечатнаяФорма` for `Документ.СчетНаОплату`

```bsl
#Область ПрограммныйИнтерфейс

Функция СведенияОВнешнейОбработке() Экспорт

	МетаданныеОбработки = Метаданные();

	ПараметрыРегистрации = ДополнительныеОтчетыИОбработки.СведенияОВнешнейОбработке("2.2.2.1");
	ПараметрыРегистрации.Вид    = ДополнительныеОтчетыИОбработкиКлиентСервер.ВидОбработкиПечатнаяФорма();
	ПараметрыРегистрации.Версия = "1.0";

	ПараметрыРегистрации.Назначение.Добавить("Документ.СчетНаОплату");

	НоваяКоманда = ПараметрыРегистрации.Команды.Добавить();
	НоваяКоманда.Представление        = МетаданныеОбработки.Представление();
	НоваяКоманда.Идентификатор        = МетаданныеОбработки.Имя;
	НоваяКоманда.Использование        = ДополнительныеОтчетыИОбработкиКлиентСервер.ТипКомандыВызовСерверногоМетода();
	НоваяКоманда.ПоказыватьОповещение = Ложь;
	НоваяКоманда.Модификатор          = "ПечатьMXL";

	Возврат ПараметрыРегистрации;

КонецФункции

Процедура Печать(МассивОбъектов, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода) Экспорт

	// TODO: Реализация

КонецПроцедуры

#КонецОбласти
```

---

## Operation 2: Add a БСП command

Adds a command to an existing `СведенияОВнешнейОбработке()` function and generates the corresponding handler.

### Prerequisites

The processing is already registered in БСП - the `ObjectModule.bsl` contains the `СведенияОВнешнейОбработке()` function. If not, first run Operation 1.

### Command type mapping

| User writes | `ТипКоманды` |
|-------------|--------------|
| open form, form | `ТипКомандыОткрытиеФормы()` |
| client method, on client | `ТипКомандыВызовКлиентскогоМетода()` |
| server method, on server | `ТипКомандыВызовСерверногоМетода()` |
| form fill, fill form | `ТипКомандыЗаполнениеФормы()` |
| script, safe mode | `ТипКомандыСценарийВБезопасномРежиме()` |

If the type is not specified, determine it from the processing type in the existing `СведенияОВнешнейОбработке()` code (the line with `ВидОбработки...()`) according to the table in Operation 1.

### Command block template

Inserted **before** the line `Возврат ПараметрыРегистрации`:

```bsl
	НоваяКоманда = ПараметрыРегистрации.Команды.Добавить();
	НоваяКоманда.Представление        = НСтр("ru = '{{Представление}}'");
	НоваяКоманда.Идентификатор        = "{{Идентификатор}}";
	НоваяКоманда.Использование        = ДополнительныеОтчетыИОбработкиКлиентСервер.{{ТипКоманды}};
	НоваяКоманда.ПоказыватьОповещение = Ложь;
```

For `ПечатнаяФорма`, also add:

```bsl
	НоваяКоманда.Модификатор = "ПечатьMXL";
```

**Difference from the first command**: additional commands use `НСтр("ru = '...'")` for the representation and a string literal for the identifier (not `Метаданные()`).

### Handler templates for the new command

#### `ВызовСерверногоМетода` - if the `ВыполнитьКоманду` procedure already exists

Add a branch before `КонецЕсли`:

```bsl
	ИначеЕсли ИдентификаторКоманды = "{{Идентификатор}}" Тогда
		// TODO: Реализация {{Идентификатор}}
```

#### `ВызовСерверногоМетода` - if the procedure does not exist

For global processing (without `ОбъектыНазначения`):

```bsl
Процедура ВыполнитьКоманду(ИдентификаторКоманды, ПараметрыВыполненияКоманды) Экспорт

	Если ИдентификаторКоманды = "{{Идентификатор}}" Тогда
		// TODO: Реализация {{Идентификатор}}
	КонецЕсли;

КонецПроцедуры
```

For assigned processing (with `ОбъектыНазначения`):

```bsl
Процедура ВыполнитьКоманду(ИдентификаторКоманды, ОбъектыНазначения, ПараметрыВыполненияКоманды) Экспорт

	Если ИдентификаторКоманды = "{{Идентификатор}}" Тогда
		// TODO: Реализация {{Идентификатор}}
	КонецЕсли;

КонецПроцедуры
```

#### `ПечатнаяФорма` - if the `Печать` procedure already exists

Add a block before `КонецПроцедуры`:

```bsl
	ПечатнаяФорма = УправлениеПечатью.СведенияОПечатнойФорме(КоллекцияПечатныхФорм, "{{Идентификатор}}");
	Если ПечатнаяФорма <> Неопределено Тогда
		ПечатнаяФорма.ТабличныйДокумент = Сформировать{{Идентификатор}}(МассивОбъектов, ОбъектыПечати);
		ПечатнаяФорма.СинонимМакета = НСтр("ru = '{{Представление}}'");
	КонецЕсли;
```

#### `ПечатнаяФорма` - if the `Печать` procedure does not exist

```bsl
Процедура Печать(МассивОбъектов, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода) Экспорт

	ПечатнаяФорма = УправлениеПечатью.СведенияОПечатнойФорме(КоллекцияПечатныхФорм, "{{Идентификатор}}");
	Если ПечатнаяФорма <> Неопределено Тогда
		ПечатнаяФорма.ТабличныйДокумент = Сформировать{{Идентификатор}}(МассивОбъектов, ОбъектыПечати);
		ПечатнаяФорма.СинонимМакета = НСтр("ru = '{{Представление}}'");
	КонецЕсли;

КонецПроцедуры
```

#### `ВызовКлиентскогоМетода`

The handler is added in the **form module** (find it through Glob: `<SrcDir>/<ProcessorName>/Forms/*/Ext/Form/Module.bsl`).

For global processing:

```bsl
&НаКлиенте
Процедура ВыполнитьКоманду(ИдентификаторКоманды) Экспорт

	Если ИдентификаторКоманды = "{{Идентификатор}}" Тогда
		// TODO: Реализация {{Идентификатор}}
	КонецЕсли;

КонецПроцедуры
```

For assigned processing:

```bsl
&НаКлиенте
Процедура ВыполнитьКоманду(ИдентификаторКоманды, ОбъектыНазначенияМассив) Экспорт

	Если ИдентификаторКоманды = "{{Идентификатор}}" Тогда
		// TODO: Реализация {{Идентификатор}}
	КонецЕсли;

КонецПроцедуры
```

If the procedure already exists, add an `ИначеЕсли` branch.

### Execution steps

1. Find and read `ObjectModule.bsl` through Glob: `<SrcDir>/<ProcessorName>/Ext/ObjectModule.bsl`
2. Make sure `СведенияОВнешнейОбработке()` exists. If not, suggest running Operation 1 first
3. Determine the processing type from the existing code (the line with `ВидОбработки...()`)
4. Insert the command block **before** `Возврат ПараметрыРегистрации`
5. Add the handler:
   - Server-side - in `ObjectModule.bsl`, `ПрограммныйИнтерфейс` region
   - Client-side - in the form module
6. If the handler (`ВыполнитьКоманду` / `Печать`) already exists, add a branch, do not create a duplicate procedure
7. Use tabs for indentation

### Example: adding a server command

Request: `МояОбработка ЗаказПокупателя серверный "Заказ покупателя"`

The following will be added to `СведенияОВнешнейОбработке()` before `Возврат`:

```bsl
	НоваяКоманда = ПараметрыРегистрации.Команды.Добавить();
	НоваяКоманда.Представление        = НСтр("ru = 'Заказ покупателя'");
	НоваяКоманда.Идентификатор        = "ЗаказПокупателя";
	НоваяКоманда.Использование        = ДополнительныеОтчетыИОбработкиКлиентСервер.ТипКомандыВызовСерверногоМетода();
	НоваяКоманда.ПоказыватьОповещение = Ложь;
```

---

## Key paths

| File | Path |
|------|------|
| Object module | `<SrcDir>/<ProcessorName>/Ext/ObjectModule.bsl` |
| Form module | `<SrcDir>/<ProcessorName>/Forms/<FormName>/Ext/Form/Module.bsl` |

By default, `SrcDir = src`.
