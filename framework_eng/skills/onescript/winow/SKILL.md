---
name: winow
description: Creates web applications and routes on Winow (a web server on OneScript and Autumn). Use when working with a web server on OneScript, routing, or Winow controllers.
---

# Winow

A minimalist web server on native TCP and Autumn acorns. Pure OneScript, without OneScript.web. Suitable for web applications, microservices, API mocks, serving static files and templates.

Limitations: no HTTPS, not designed for high loads.

## Installation

```bash
opm install winow
```

Dependency: the Autumn framework. Requires `#Использовать autumn` and a directory with controller classes.

## Entry Point

```bsl
#Использовать autumn
#Использовать winow

Поделка = Новый Поделка();
Поделка.ЗапуститьПриложение();
```

By default, the server listens on localhost:3333. Controller classes must be located in a directory connected via `#Использовать "путь"`, so that Autumn loads them.

## Controller and Route

A controller class is marked with `&Контроллер("/base/path")`. Handler methods are `&ТочкаМаршрута("name")`. The route point name becomes part of the URL: `/base/path/name`.

Minimal example (Hello World):

```bsl
&Контроллер("/")
Процедура ПриСозданииОбъекта()
КонецПроцедуры

&ТочкаМаршрута("/")
Процедура Приветствие(Ответ) Экспорт
    Ответ.УстановитьТипКонтента("html");
    Ответ.ТелоТекст = СтрШаблон("<!DOCTYPE html><div>%1</div>", "Привет, мир!");
КонецПроцедуры
```

URL: `http://localhost:3333/`

## Request Parameters

The method signature can include `Запрос` and `Ответ`. GET parameters are in `Запрос.ПараметрыИменные`.

Example: `http://localhost:3333/greeter/getparams?name=Nikita&familia=ivanchenko`

```bsl
&Контроллер("/greeter")
Процедура ПриСозданииОбъекта()
КонецПроцедуры

&ТочкаМаршрута("getparams")
Процедура Приветствие(Запрос, Ответ) Экспорт
    Ответ.УстановитьТипКонтента("html");
    Имя = Запрос.ПараметрыИменные["name"];
    Фамилия = Запрос.ПараметрыИменные["familia"];
    Ответ.ТелоТекст = СтрШаблон("<!DOCTYPE html><div>Имя: %1</div><div>Фамилия: %2</div>", Имя, Фамилия);
КонецПроцедуры
```

Named parameters from the query string can be passed by name into the route point method parameters (see the Winow documentation).

## Response

- `Ответ.УстановитьТипКонтента("html")` — Content-Type.
- `Ответ.ТелоТекст = "..."` — response body (string).

Additionally: working with POST body, cookie, sessions, static files, templates (jinja-like syntax), SSE, WebSocket, basic authorization and roles — in [Winow documentation](https://autumn-library.github.io/winow).

## Summary

1. Entry point: autumn + winow, Поделка, ЗапуститьПриложение.
2. Controller: a class with `&Контроллер("/путь")`.
3. Handler: a method with `&ТочкаМаршрута("имя")`, parameters `(Ответ)` or `(Запрос, Ответ) Экспорт`.
4. Response: УстановитьТипКонтента, ТелоТекст.
5. Request: ПараметрыИменные for GET.

Documentation: [autumn-library.github.io/winow](https://autumn-library.github.io/winow).
