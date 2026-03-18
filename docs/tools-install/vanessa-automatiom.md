# Установка Vanessa Automation для фреймворка

Этот документ фиксирует, **что именно нужно установить из оригинальных upstream-репозиториев**, чтобы фреймворк мог запускать сценарные тесты Vanessa Automation на используемых базах 1С.

Документ про **runtime-контур**, а не про sandbox/отладочный репозиторий.

## Что обязательно должно быть в контейнере

### 0. Где уже всё настроено

https://github.com/SteelMorgan/1c-ai-sandbox-client-server - песочница это не только ограничение ИИ-агента, но и готовое окружение для работы данного фреймворка.

### 1. Клиент 1С:Предприятие

Фреймворк ожидает доступный тонкий/обычный клиент 1С с `1cv8c`.

Ожидаемый путь:

```text
/opt/1cv8/x86_64/<version>/1cv8c
```

В текущем рабочем контуре использовался:

```text
/opt/1cv8/x86_64/8.3.27.1719/1cv8c
```

### 2. Vanessa ADD (`add`)

Оригинальный репозиторий:

```text
https://github.com/vanessa-opensource/add
```

Фреймворк ожидает не просто clone исходников, а **установленный runtime-каталог** `add` в библиотеке OneScript.

Ожидаемые пути внутри контейнера:

```text
/opt/onescript/2.0.0/lib/add/bddRunner.epf
/opt/onescript/2.0.0/lib/add/plugins/
/opt/onescript/2.0.0/lib/add/features/libraries/
```

Минимум, который реально нужен фреймворку:

- `bddRunner.epf`
- каталог `plugins/`
- каталог `features/libraries/`

### 3. Vanessa Runner (`vrunner`)

Оригинальный репозиторий:

```text
https://github.com/vanessa-opensource/vanessa-runner
```

Фреймворк ожидает:

- команду `vrunner` в `PATH`

То есть внутри контейнера должно выполняться:

```bash
vrunner --version
```

Путь clone исходников не важен, если CLI установлен и доступен в `PATH`.

### 4. OneScript

`add` ожидается именно как библиотека OneScript.

Рабочий каталог библиотек, который использует фреймворк:

```text
/opt/onescript/2.0.0/lib/
```

Из этого следует, что runtime `add` должен быть установлен именно сюда:

```text
/opt/onescript/2.0.0/lib/add
```

## Что нужно клонировать

Обязательные upstream-репозитории:

```text
https://github.com/vanessa-opensource/add
https://github.com/vanessa-opensource/vanessa-runner
```

Важно:

- фреймворк **не требует**, чтобы исходники этих репозиториев лежали в каком-то одном фиксированном каталоге;
- фреймворк требует, чтобы после установки были доступны runtime-артефакты по ожидаемым путям.

То есть clone можно делать в любой технический каталог, например:

```text
/opt/src/add
/opt/src/vanessa-runner
```

Но после установки фреймворк ожидает:

```text
/opt/onescript/2.0.0/lib/add/...
vrunner в PATH
```

## Что фреймворк ожидает от runtime `add`

### Обязательные runtime-файлы

```text
/opt/onescript/2.0.0/lib/add/bddRunner.epf
/opt/onescript/2.0.0/lib/add/plugins/*
/opt/onescript/2.0.0/lib/add/features/libraries/*
```

### Почему это важно

Навыки и правила фреймворка используют именно эти пути:

- baseline EPF:
  `/opt/onescript/2.0.0/lib/add/bddRunner.epf`
- универсальные библиотеки шагов Vanessa:
  `/opt/onescript/2.0.0/lib/add/features/libraries`

Если `add` установлен в другое место, навыки запуска Vanessa работать не будут без дополнительной адаптации.

## Runtime-шаблоны самого фреймворка

Во фреймворке уже лежат универсальные runtime templates:

```text
tools/runtime/vanessa/vrunner-va.json
tools/runtime/vanessa/va-params.template.json
tools/runtime/vanessa/va-params-debug.template.json
```

Они используют:

- `$workspaceRoot`
- baseline EPF из `/opt/onescript/2.0.0/lib/add/bddRunner.epf`
- project-specific сценарии из:
  `<project_root>/vanessa-tests/features`

То есть дополнительно копировать runtime JSON из sandbox-репозитория не нужно.

## Что ещё нужно для headless-запуска

Минимально рекомендуемый X11-контур:

- `Xvfb`

Для диагностики и визуального fallback:

- `ffmpeg`
- `xwininfo`
- `xdotool`
- `wmctrl`

Для ручного подключения к экрану через браузер:

- `x11vnc`
- `websockify` / `noVNC`

## Текущая важная оговорка по `bddRunner.epf`

На Linux 8.3.27 в нашем контуре штатный `bddRunner.epf` из `add` падал на обработке режима совместимости `НеИспользовать / DontUse`.

Upstream PR:

```text
https://github.com/vanessa-opensource/add/pull/1165
```

Поэтому на практике нужно одно из двух:

1. использовать версию `add`, в которую уже вошёл этот фикс;
2. либо после установки `add` заменить `bddRunner.epf` на сборку с этим исправлением.

Пока upstream-фикс не выпущен в используемой версии `add`, это обязательная проверка.

## Минимальная проверка установки

После установки должны выполняться проверки:

```bash
test -f /opt/onescript/2.0.0/lib/add/bddRunner.epf
test -d /opt/onescript/2.0.0/lib/add/plugins
test -d /opt/onescript/2.0.0/lib/add/features/libraries
vrunner --version
```

И для клиента 1С:

```bash
test -x /opt/1cv8/x86_64/8.3.27.1719/1cv8c
```

## Что НЕ нужно брать из sandbox-репозитория

Не требуются для обязательной runtime-установки:

- `epf-source/*`
- `build/debug/*`
- `docs/*`
- отладочные скриншоты и логи
- временные diagnostic EPF

Это reference/debug-материалы, а не обязательные runtime-компоненты.

## Интеграция в конкретный проект-базу

После установки runtime-компонентов сам проект должен предоставить только project-specific слой.

### Где хранить сценарии проекта

Фреймворк ожидает следующее соглашение:

```text
<project_root>/vanessa-tests/features
<project_root>/vanessa-tests/support
```

Назначение:

- `vanessa-tests/features` — project-specific `.feature`
- `vanessa-tests/support` — project-specific fixtures, support-данные и вспомогательные материалы

### Как использовать runtime templates framework

Во framework уже есть шаблоны:

```text
tools/runtime/vanessa/vrunner-va.json
tools/runtime/vanessa/va-params.template.json
tools/runtime/vanessa/va-params-debug.template.json
```

Есть два допустимых режима:

#### 1. Использовать шаблоны как есть

Подходит, если проект принимает соглашение:

```text
<project_root>/vanessa-tests/features
<project_root>/build/vanessa/...
```

и не требует project-specific правок JSON.

#### 2. Создать project-local runtime copies

Подходит, если проекту нужны:

- свои пути к feature-файлам;
- свои runtime-настройки Vanessa;
- отдельный debug-профиль;
- project-specific параметры запуска.

В этом случае project-local копии можно хранить, например, в:

```text
<project_root>/tools/json/va-params.json
<project_root>/tools/json/va-params-debug.json
<project_root>/tools/json/vrunner-va.json
```

Источник для таких файлов — templates из `tools/runtime/vanessa/`.

### Что обязательно документировать в задаче

Если в рамках задачи создан или изменён сценарный тест, в документации задачи должна быть ссылка на соответствующий `.feature`.

Минимум:

- путь к feature-файлу;
- краткая пометка, что именно он проверяет.

Пример:

```text
Feature: <project_root>/vanessa-tests/features/smoke/create_customer.feature
Назначение: проверка создания клиента по задаче <id>
```

### Что не нужно класть в проект-базу

В project-root не нужно копировать:

- `epf-source/*`
- debug-сборки `bddRunner`
- sandbox docs и отладочные материалы

В проекте должны жить только:

- project-specific `.feature`
- project-specific support/fixtures
- при необходимости project-local runtime copies JSON
