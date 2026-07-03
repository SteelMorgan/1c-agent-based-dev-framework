---
name: autumn
description: Creates and configures component applications on the Autumn framework (ОСень) for OneScript with DI and annotations. Use when working with Autumn, Dependency Injection, "acorns", and the &Желудь, &Дуб, &Верховный annotations.
---

# Autumn (ОСень)

A framework for component applications for OneScript with Dependency Injection and Inversion of Control. Automatically resolves constructor dependencies: there is no need to manually create object chains and pass parameters to every creation point.

## How It Works

An object with a constructor that accepts parameters (dependencies) is registered as a component (an "acorn"). Instead of calling `Новый Класс(зависимость1, зависимость2)` in code, `Осень.НайтиЖелудь("ИмяКласса")` is used - the framework creates the dependencies itself and passes them to the constructor.

## Entry Point

```bsl
#Использовать autumn
#Использовать "Каталог/С/Классами/Команд"   // путь к папке с классами-компонентами

Поделка = Новый Поделка();
Поделка.ЗапуститьПриложение();
```

The directory with classes must contain classes marked with Autumn annotations (Желудь, Дуб, etc.) so that they are registered in the application context.

## Getting a Component

```bsl
Обновлятор1С = Осень.НайтиЖелудь("Обновлятор1С");
```

The identifier matches the class name or the value of the `&Желудь("Идентификатор")` annotation.

## Key Annotations

- **&Желудь("Идентификатор")** — the class is registered as a component (acorn). Without a parameter, the class name is used. Constructor parameters are resolved as dependencies of other acorns.

- **&Верховный** — the root component (for example, the application description). Used together with &Желудь.

- **&Дуб** — a factory class: it is not itself an acorn, but in a method with &Завязь it returns instances of other classes (acorns).

- **&Завязь** — a method inside &Дуб that returns a class instance; used to register commands/components without annotations on the target class (for example, when migrating legacy code).

Example of an acorn:

```bsl
&Желудь("Обновлятор1С")
Процедура ПриСозданииОбъекта(ПроверяторВерсий, Настройки)
    // ПроверяторВерсий and Настройки will be resolved by Autumn and passed here
КонецПроцедуры
```

Example of a дуб with завязь:

```bsl
&Дуб
Процедура ПриСозданииОбъекта()
КонецПроцедуры

&Завязь
&КомандаПриложения(Имя = "legacy", Описание = "Легаси команда")
Функция МояЛегасиКоманда() Экспорт
    Возврат Новый МояЛегасиКоманда();
КонецФункции
```

## Recommendations

- One class, one responsibility; pass dependencies through the constructor.
- Do not build long chains manually - register classes as beans and retrieve them through `Осень.НайтиЖелудь()`.
- Application settings can be moved into `autumn-properties.json` or into a separate bean with a settings interface.

## Additional

Extended reference for annotations and configuration - [reference.md](reference.md).

Documentation: [autumn-library.github.io](https://autumn-library.github.io/).
