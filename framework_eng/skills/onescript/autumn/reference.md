# Autumn — extended reference

## Core annotations

- **&Желудь** — registers a class as a component. Parameter: identifier (defaults to the class name). The constructor (ПриСозданииОбъекта) receives dependencies by parameter types/names.
- **&Верховный** — marks the root component.
- **&Дуб** — a container class for buds, not created itself as a dependency in an arbitrary place.
- **&Завязь** — a method in Дуб that returns an instance; used to "acornify" classes without changing their code.

## annotations library

Autumn uses annotations as first-class objects. Annotation parameters are specified in parentheses: `&Желудь("MyId")`, `&КомандаПриложения(Имя = "cmd", Описание = "…")`. Meta-annotations are supported (aggregators of other annotations). The API is described in [Autumn documentation — annotations](https://autumn-library.github.io/annotations).

## Application settings

The `autumn-properties.json` file next to the entry point or in the project directory lets you define properties for subsystems (for example, cli: application name, version). An alternative is a bud with a specific interface (for example, "ОписаниеКонсольногоПриложения" with the methods ИмяПриложения, ВерсияПриложения).

## Ecosystem modules

- **autumn-collections** — attachable collections.
- **autumn-cli** — console commands on top of cli.
- **autumn-logos** — logging.
- **autumn-async**, **autumn-synchronized**, **autumn-event-publisher** — async, synchronization, events.

Connected via `#Использовать` and component registration in the same class directory as the main application, or through separate directories specified at the entry point.
