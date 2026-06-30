---
name: onescript
description: Writes and structures code in OneScript (BSL without 1C). Use when working with .os files, OneScript projects, packagedef, opm, the #Использовать directive, classes, and modules.
---

# OneScript

A skill for writing and structuring code in OneScript, a cross-platform interpreter for 1C:Enterprise 8 without the 1C platform. Code is executed from text `.os` files, similar to Python or Node.js.

## Structure of a Module

A module is a text file with the `.os` extension. Three sections (top to bottom):

1. **Variable section** — `Перем ИмяПеременной;`
2. **Method section** — procedures and functions
3. **Module body section** — code executed at startup (must be at the bottom of the file)

Minimal script:

```bsl
Сообщить("Привет, Мир!");
```

Procedures and functions:

```bsl
Процедура МояПроцедура(Параметр1, Параметр2)
    // операторы
КонецПроцедуры

Функция МояФункция(Параметр1, Параметр2)
    Возврат Параметр1 + Параметр2;
КонецФункции
```

## Types and Literals

Typing is dynamic. Primitives: Строка, Число, Булево, Дата; special: Неопределено, Null.

- Number: `12345.899`
- String: `"Текст"`, quote inside — `""`. Multiline: a new line starts with `|`, spaces before `|` are discarded.
- Date: `'20250212235959'` or `'2025-02-12 23:59:59'`

## Code Blocks

Condition:

```bsl
Если Условие Тогда
    // ...
ИначеЕсли ДругоеУсловие Тогда
    // ...
Иначе
    // ...
КонецЕсли;
```

Loops:

```bsl
Для Счетчик = 0 По 10 Цикл
    Сообщить(Счетчик);
КонецЦикла;

Для Каждого Элемент Из Массив Цикл
    Сообщить(Элемент);
КонецЦикла;

Пока Условие Цикл
    // ...
КонецЦикла;
```

Exceptions:

```bsl
Попытка
    // код
Исключение
    Сообщить(ОписаниеОшибки());
    ВызватьИсключение;  // rethrow
КонецПопытки;

ВызватьИсключение "Текст ошибки";
```

## Project Structure

Recommended directory structure:

- `src/` — source code (included in the distribution)
- `src/Классы/` — `.os` files included as classes (created via `Новый`)
- `src/Модули/` — `.os` files included as common modules
- `tests/` — tests
- `tasks/` — utility scripts (build, tests)
- Root: `packagedef` (manifest), README, LICENSE

The entry point (for example `src/main.os`) must import its directory:

```bsl
#Использовать "."

// далее использование классов и модулей
Сообщить(МойМодуль.Метод());
```

## packagedef Manifest

At the project root there is a file without an extension, `packagedef`. Minimum: name, version, contents.

```bsl
Описание.Имя("my-package")
    .Версия("1.0.0")
    .ВерсияСреды("2.0.0")
    .ЗависитОт("fs", "1.0.0")
    .ЗависитОт("asserts", "1.3.0")
    .ВключитьФайл("packagedef")
    .ВключитьФайл("src")
    .ВключитьФайл("oscript_modules")
    .ИсполняемыйФайл("src/my-script.os");
```

Main properties: `Имя`, `Версия`, `ВерсияСреды`, `ЗависитОт`, `ВключитьФайл`, `ИсполняемыйФайл`.

## Libraries and #Использовать

Include at the beginning of the module (before variables):

- By name (search in library directories): `#Использовать json`, `#Использовать fs`
- By path (relative to the file): `#Использовать "."`, `#Использовать "lib/mylib"`

A class from a library is a new type, created through `Новый ИмяКласса()`. A module is a global object with methods: `ИмяМодуля.Метод()`.

Standard loader: files from the `Классы` subdirectory are registered as classes, and files from `Модули` as modules. The file name (without `.os`) = the class/module identifier.

## OPM

- Build a package from a directory with a manifest: `opm build .`
- Publish to the hub: `opm push my-file.ospx --token ТОКЕН`
- Install a package: via opm or by cloning into the library directory

## Additional

Details of syntax, differences from 1C (Format/DF, constructor call in an expression, parameterized exceptions, rethrow with ИнформацияОбОшибке) — in [reference.md](reference.md).

Documentation: [oscript.io/learn](https://oscript.io/learn/).
