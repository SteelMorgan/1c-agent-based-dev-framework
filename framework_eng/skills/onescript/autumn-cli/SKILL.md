---
name: autumn-cli
description: Creates console applications with commands and subcommands using autumn-cli for OneScript. Use for OneScript CLI, application commands, arguments, and options.
---

# autumn-cli

A wrapper around the cli library for creating console applications on the Autumn framework. Commands, subcommands, arguments, and options are described with annotations on classes.

## Connection and launch

Entry point (for example `main.os`):

```bsl
#Использовать autumn
#Использовать autumn-cli
#Использовать "Каталог/С/Классами/Команд"

Поделка = Новый Поделка();
Поделка.ЗапуститьПриложение();
```

The directory with commands must contain classes annotated with `&КомандаПриложения` or `&ПодкомандаПриложения`.

## Application command

A class is declared as a command with an annotation on the constructor. The method executed when the command is called is marked with `&ВыполнениеКоманды`.

```bsl
&Аргумент(Имя = "ARG", Описание = "Значение числа")
&ТЧисло
Перем ПереданноеЧисло;

&КомандаПриложения(Имя = "p plus", Описание = "Прибавляет 10 к заданному числу")
Процедура ПриСозданииОбъекта()
КонецПроцедуры

&ВыполнениеКоманды
Процедура ВывестиРезультатСложения() Экспорт
    Результат = ПереданноеЧисло + 10;
    Сообщить("Результат сложения: " + Результат);
КонецПроцедуры
```

Annotation parameters for `&КомандаПриложения`: `Имя` is the CLI name (for example `"p plus"`), `Описание` is the help text. Repeated parameter `Подкоманда` is names of subcommands.

## Arguments and options

- **Аргумент** is a positional argument: a variable with `&Аргумент(Имя = "ARG", Описание = "...")`.
- **Опция** is a named parameter: a variable with `&Опция(Имя = "n name", Описание = "Имя пользователя")`.

Example option:

```bsl
&Опция(Имя = "n name", Описание = "Имя пользователя")
Перем ИмяПользователя;

&КомандаПриложения(Имя = "H hello", Описание = "Поздоровается с пользователем")
Процедура ПриСозданииОбъекта()
КонецПроцедуры

&ВыполнениеКоманды
Процедура ПоприветствоватьПользователя() Экспорт
    Сообщить("Привет " + ИмяПользователя + "!");
КонецПроцедуры
```

## Argument and option types

Type annotations for fields: `&ТЧисло`, `&ТСтрока`, `&ТБулево`, `&ТДата`, `&ТМассивДат`, `&ТМассивЧисел`, `&ТМассивСтрок`, `&ТПеречисление`. For boolean also `&Флаговый`, `&Флаг`.

Additionally: `&ВОкружении("ИМЯ_ПЕРЕМЕННОЙ")`, `&ПоУмолчанию("значение")`, `&СкрытьВСправке`, `&Обязательный`, `&Описание`, `&ПодробноеОписание`, `&Псевдоним`, `&Перечисление` with Name/Value/Description parameters.

## Subcommands

The parent command lists subcommands through the repeatable `Подкоманда` parameter:

```bsl
&КомандаПриложения(Имя = "d date", Описание = "Выводит дату",
    Подкоманда = "ПодкомандаДень",
    Подкоманда = "ПодкомандаМесяц"
)
Процедура ПриСозданииОбъекта()
КонецПроцедуры
```

Subcommand class:

```bsl
&ПодкомандаПриложения(Имя = "day", Описание = "Выводит дату - начало дня")
Процедура ПриСозданииОбъекта()
КонецПроцедуры

&ВыполнениеКоманды
Процедура СообщитьДень() Экспорт
    Сообщить(НачалоДня(ТекущаяДата()));
КонецПроцедуры
```

`&ПодкомандаПриложения` has the same parameters as `&КомандаПриложения` (Идентификатор, Имя, Описание, Подкоманда, ДобавлятьАннотациюЖелудь).

## Application name and version

**Option 1** - the `autumn-properties.json` file in the project directory:

```json
{
    "cli": {
        "ИмяПриложения": "cli_test",
        "ПолноеИмяПриложения": "cli_test v%{cli.ВерсияПриложения}",
        "ВерсияПриложения": "1.0.1"
    }
}
```

**Option 2** - acorn with the application description interface in the commands directory:

```bsl
&Желудь("ОписаниеКонсольногоПриложения")
&Верховный
Процедура ПриСозданииОбъекта()
КонецПроцедуры

Функция ИмяПриложения() Экспорт
    Возврат "demo-cli";
КонецФункции

Функция ПолноеИмяПриложения() Экспорт
    Возврат "Демонстрационное консольное приложение";
КонецФункции

Функция ВерсияПриложения() Экспорт
    Возврат "1.0";
КонецФункции
```

## Migration from the pure CLI

Existing commands with the `ОписаниеКоманды` and `ВыполнитьКоманду` methods do not need to be rewritten: add the `&КомандаПриложения`/`&ПодкомандаПриложения` annotations and keep these methods - the framework will use them. Or wrap it in Dub with `&Завязь` and return an instance of the legacy class.

Documentation: [autumn-library.github.io/autumn-cli](https://autumn-library.github.io/autumn-cli).
