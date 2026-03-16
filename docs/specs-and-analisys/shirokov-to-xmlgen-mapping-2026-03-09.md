# Mapping: навыки Широкова (cc-1c-skills) → xml-gen Java CLI

> Дата: 2026-03-09 (rev.1 — правки по ревью GPT + Opus)
> Репо-референс: https://github.com/Nikolay-Shirokov/cc-1c-skills (commit 86c8440)
> Наш CLI: `tools/xml-gen/` v0.1.0-SNAPSHOT
> Точка импорта: 2026-02-12 (commit acee4bd в cc-1c-skills)
> Дельта: +69K строк, 218 файлов
>
> **Scope:** Документ покрывает только навыки, работающие с XML-файлами (49 операций).
> Навыки, требующие 1С-платформу (db-*, web-*, epf-build/dump), вынесены в раздел 6.
> В репозитории Широкова всего ~67 навыков.

---

## 1. Архитектурные различия

| Аспект | cc-1c-skills (Широков) | xml-gen (наш) |
|--------|----------------------|---------------|
| Язык | Python 3 + PowerShell | Java 17 (StAX + Jackson) |
| Формат | Отдельные скрипты по 200–3000 строк | Монолитный JAR (~5.6 MB) |
| Дистрибуция | Копирование в `.claude/skills/` | `~/.local/bin/xml-gen` (fat JAR) |
| DSL-модели | JSON inline или файл → скрипт парсит сам | Jackson `@Value` POJO (FormDsl, RoleDsl, …) |
| Валидация | Встроена в каждый скрипт отдельно | Централизованный ValidatorFactory |
| XML-запись | PowerShell XmlWriter / Python xml.etree | StAX XMLStreamWriter |
| Типизация | mdclasses нет; enum-значения в коде | Библиотека `mdclasses 0.17.4` |
| Форматы выгрузки | Designer only | Designer + EDT (частично) |
| Расширяемость | Нет (каждый скрипт — изолирован) | Factory/Base-class pattern |

---

## 2. Полная матрица mapping

### Обозначения

- **✅ Есть** — команда реализована в xml-gen
- **🔶 Частично** — покрыто другой командой или с ограничениями
- **❌ Нет** — отсутствует, нужна реализация
- **📋 DSL-spec** — спецификация JSON DSL в cc-1c-skills, которую можно использовать

---

### 2.1. EPF — Внешние обработки

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `epf-init` | Scaffold EPF (XML + ObjectModule) | `xml-gen epf init` | ✅ Есть | |
| `epf-build` | Сборка EPF из XML (через 1cv8) | `xml-gen epf build` | ❌ → Plan | Phase 1: пакет `platform/`, PlatformResolver + StubDatabaseBuilder |
| `epf-dump` | Разборка EPF в XML (через 1cv8) | `xml-gen epf dump` | ❌ → Plan | Phase 1: пакет `platform/`, требует базу для ссылочных типов |
| `epf-validate` | Валидация XML обработки | `xml-gen validate --type epf` | ✅ Есть | У Широкова +698 строк Python, наш валидатор проще |
| `epf-add-form` | Добавить форму к EPF | `xml-gen epf add-form` | ✅ Есть | |
| `epf-bsp-init` | Добавить СведенияОВнешнейОбработке() | — | ❌ Нет | Генерация BSL-кода модуля, не XML |
| `epf-bsp-add-command` | Добавить команду БСП | — | ❌ Нет | Генерация BSL-кода |

**Дельта с момента импорта:**
- `epf-build`: добавлены Python-порт, auto-stub DB для ссылочных типов, сканирование Form.xml
- `epf-dump`: требует подключение к базе (было: авто-создание пустой)
- `epf-validate`: +698 строк Python с расширенной валидацией

---

### 2.2. ERF — Внешние отчёты

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `erf-init` | Scaffold ERF (XML + ObjectModule + опц. SKD) | — | 🔶 Частично | `epf init` не поддерживает ExternalReport; нужен `--type report` |
| `erf-build` | Сборка ERF из XML | — | ❌ Нет | Требует 1С-платформу |
| `erf-dump` | Разборка ERF в XML | — | ❌ Нет | Требует 1С-платформу |
| `erf-validate` | Валидация XML отчёта | — | 🔶 Частично | EPF-валидатор может подойти; нужно добавить MainDataCompositionSchema |

---

### 2.3. Form — Управляемые формы

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `form-compile` | JSON DSL → Form.xml | `xml-gen form compile` | ✅ Есть | У Широкова +1120 строк Python, type synonym resolution |
| `form-edit` | Добавить элементы/реквизиты/команды | `xml-gen form add-element`, `add-attribute`, `add-command`, `remove-element`, `move-element` | ✅ Есть | У Широкова +1303 строк Python |
| `form-info` | Анализ структуры формы | — | ❌ Нет | Парсинг Form.xml → компактный вывод |
| `form-validate` | Валидация формы | `xml-gen validate --type form` | ✅ Есть | |
| `form-add` | Регистрация формы в ChildObjects объекта | `xml-gen epf add-form` | 🔶 Частично | Только для EPF, не для Catalog/Document |
| `form-remove` | Удаление формы из объекта | — | ❌ Нет | |
| `form-patterns` | Справочник паттернов компоновки | — | 🔶 Частично | У нас есть `bsl-practices/form-patterns` (knowledge skill) |

**Дельта с момента импорта:**
- `form-compile`: type synonym resolution (resilient DSL)
- `form-edit`: Python-порт +1303 строк
- `form-info`: Python-порт +601 строк

---

### 2.4. MXL — Табличные документы

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `mxl-compile` | JSON DSL → Template.xml (SpreadsheetDocument) | `xml-gen mxl compile` | ✅ Есть | |
| `mxl-decompile` | Template.xml → JSON DSL (обратно) | — | ❌ Нет | Полезно для анализа существующих макетов |
| `mxl-info` | Анализ структуры макета | — | ❌ Нет | Области, параметры, колонки |
| `mxl-validate` | Валидация макета | `xml-gen validate --type mxl` | ✅ Есть | |

---

### 2.5. SKD — Схема компоновки данных

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `skd-compile` | JSON DSL → DataCompositionSchema XML | `xml-gen skd compile` | ✅ Есть | У Широкова +1431 строк Python |
| `skd-edit` | Точечное редактирование СКД | `xml-gen skd add-parameter`, `add-field` | 🔶 Частично | У нас 2 операции; у Широкова больше |
| `skd-info` | Анализ структуры СКД | — | ❌ Нет | Наборы, поля, параметры, варианты |
| `skd-validate` | Валидация СКД | `xml-gen validate --type skd` | ✅ Есть | |

---

### 2.6. Role — Роли и права

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `role-compile` | JSON DSL → Rights.xml | `xml-gen role compile` | ✅ Есть | |
| `role-info` | Аудит прав: объекты, RLS, шаблоны | — | ❌ Нет | |
| `role-validate` | Валидация роли | `xml-gen validate --type role` | ✅ Есть | |

---

### 2.7. Meta — Объекты метаданных конфигурации (НОВЫЙ ДОМЕН)

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `meta-compile` | JSON DSL → XML для 23 типов (Catalog, Document, Register, …) | — | ❌ Нет | **Самый крупный навык**: 2946 строк PS1 + 2572 строк Python |
| `meta-edit` | add-attribute, add-ts, add-dimension, remove, modify | — | ❌ Нет | 2348 PS1 + 2200 Python |
| `meta-info` | Парсинг XML объекта → компактная сводка | — | ❌ Нет | 1119 PS1 + 1098 Python |
| `meta-remove` | Удаление объекта из конфигурации (с проверкой ссылок) | — | ❌ Нет | 475 PS1 + 470 Python |
| `meta-validate` | Валидация объекта метаданных (13+ проверок) | — | ❌ Нет | 1297 PS1 + 1209 Python |

**📋 DSL-спецификация:** `docs/meta-dsl-spec.md` (v2.1) — описание JSON-формата для всех 23 типов.

**Поддерживаемые типы (23):**
| Категория | Типы |
|-----------|------|
| Ссылочные | Catalog, Document, Enum, ChartOfCharacteristicTypes, ChartOfAccounts, ChartOfCalculationTypes, ExchangePlan |
| Регистры | InformationRegister, AccumulationRegister, AccountingRegister, CalculationRegister |
| Процессы | BusinessProcess, Task |
| Сервисные | HTTPService, WebService |
| Прочие | Constant, DefinedType, CommonModule, Report, DataProcessor, ScheduledJob, DocumentJournal, EventSubscription |

---

### 2.8. CF — Конфигурация (НОВЫЙ ДОМЕН)

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `cf-init` | Scaffold Configuration.xml + Languages/ | — | ❌ Нет | 215 PS1 + 203 Python |
| `cf-info` | Анализ конфигурации (свойства, состав, счётчики) | — | ❌ Нет | 387 PS1 + 402 Python |
| `cf-edit` | Изменить свойства, добавить/удалить объект | — | ❌ Нет | |
| `cf-validate` | Валидация Configuration.xml | — | ❌ Нет | 538 PS1 + 532 Python |

**📋 Спецификация:** `docs/1c-configuration-spec.md` — полная структура Configuration.xml, 44 типа ChildObjects в строгом порядке.

---

### 2.9. CFE — Расширения конфигурации (НОВЫЙ ДОМЕН)

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `cfe-init` | Scaffold расширения | — | ❌ Нет | Purpose: Patch/Customization/AddOn |
| `cfe-borrow` | Заимствование объекта из конфигурации | — | ❌ Нет | ObjectBelonging=Adopted, ExtendedConfigurationObject |
| `cfe-diff` | Анализ: состав, перехватчики, проверка переноса | — | ❌ Нет | Mode A (обзор) / Mode B (diff) |
| `cfe-patch-method` | Генерация перехватчика (&Перед/&После/&Вместо) | — | ❌ Нет | Смешанный scope: читает XML расширения + генерирует BSL-декораторы. XML-часть (поиск модуля, проверка ObjectBelonging) — in scope; BSL-генерация — out of scope |
| `cfe-validate` | Валидация расширения | — | ❌ Нет | 607 PS1 + 596 Python |

**📋 Спецификация:** `docs/1c-extension-spec.md` — ObjectBelonging, ID-диапазоны (base 1-999999, ext 1000000+), callType, diff-маркеры.

---

### 2.10. Subsystem — Подсистемы (НОВЫЙ ДОМЕН)

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `subsystem-compile` | JSON → Subsystem XML + регистрация | — | ❌ Нет | 338 PS1 + 288 Python |
| `subsystem-edit` | add/remove-content, add/remove-child | — | ❌ Нет | 414 PS1 + 464 Python |
| `subsystem-info` | Состав, дочерние, командный интерфейс, дерево | — | ❌ Нет | 514 PS1 + 525 Python |
| `subsystem-validate` | 13 проверок | — | ❌ Нет | 325 PS1 + 351 Python |

**📋 Спецификация:** `docs/1c-subsystem-spec.md` — Content, вложенные подсистемы, CommandInterface.xml.

---

### 2.11. Interface — Командный интерфейс (НОВЫЙ ДОМЕН)

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `interface-edit` | hide, show, place, order, subsystem-order, group-order | — | ❌ Нет | CommandInterface.xml |
| `interface-validate` | 13 проверок | — | ❌ Нет | |

---

### 2.12. Утилитарные навыки

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `help-add` | Встроенная справка (Help.xml + HTML) | — | ❌ Нет | Простая генерация |
| `template-add` | Универсальный add-template (любой объект) | `xml-gen epf add-template` | 🔶 Частично | Только EPF |
| `template-remove` | Удаление макета из объекта | — | ❌ Нет | |
| `img-grid` | Наложение сетки на изображение | — | ❌ Нет | Вне scope xml-gen |

---

## 3. Сводная таблица покрытия

| Домен | compile | edit | info | validate | remove | init | Итого |
|-------|---------|------|------|----------|--------|------|-------|
| **EPF** | — | ✅ 2 ops | — | ✅ | — | ✅ | 3/6 |
| **ERF** | — | — | — | 🔶 | — | 🔶 | 0/6 |
| **Form** | ✅ | ✅ 5 ops | ❌ | ✅ | ❌ | — | 3/5 |
| **MXL** | ✅ | — | ❌ | ✅ | — | — | 2/4 |
| **SKD** | ✅ | 🔶 2 ops | ❌ | ✅ | — | — | 2/4 |
| **Role** | ✅ | ✅ 2 ops | ❌ | ✅ | — | — | 3/4 |
| **Meta** | ❌ | ❌ | ❌ | ❌ | ❌ | — | 0/5 |
| **CF** | — | ❌ | ❌ | ❌ | — | ❌ | 0/4 |
| **CFE** | — | ❌ | ❌ | ❌ | — | ❌ | 0/5 |
| **Subsystem** | ❌ | ❌ | ❌ | ❌ | — | — | 0/4 |
| **Interface** | — | ❌ | — | ❌ | — | — | 0/2 |

**Покрытие: 13 из 49 возможных операций (27%)**

---

## 4. Предлагаемое расширение xml-gen CLI

> Порядок фаз оптимизирован по ROI: сначала дешёвые расширения существующих
> доменов, затем новые домены в порядке возрастания сложности.
> `info`-команды включены в фазу соответствующего домена (а не вынесены отдельно).

### Phase 1: Добивание существующих доменов (низкая сложность, высокий ROI)

Переиспользует текущие XmlStructureReader, IdAllocator, Writers/Editors.

```
xml-gen epf init --type report              ← из erf-init (ERF = EPF + MainDataCompositionSchema)
xml-gen form info <xml>                     ← из form-info (read-only анализ Form.xml)
xml-gen skd info <xml>                      ← из skd-info
xml-gen role info <xml>                     ← из role-info
xml-gen mxl info <xml>                      ← из mxl-info
xml-gen mxl decompile <xml> <json>          ← из mxl-decompile (обратная конвертация)
```

**Трудоёмкость:** низкая. Переиспользование существующей инфраструктуры.

### Phase 2: Универсализация object-container операций (низкая-средняя)

Обобщение EPF-only команд на любой тип объекта.

```
xml-gen form add <objectPath> <formName>    ← из form-add (не только EPF)
xml-gen form remove <objectPath> <formName> ← из form-remove
xml-gen template add <objectPath> <name>    ← из template-add (универсальный)
xml-gen template remove <objectPath> <name> ← из template-remove
xml-gen help add <objectPath>               ← из help-add
```

**Архитектурное решение:** Общий object-container API для работы с ChildObjects любого типа объекта.
**Трудоёмкость:** низкая-средняя. Основа — существующий EpfEditor.

### Phase 3: CF — Конфигурация (средняя)

```
xml-gen config init <name>                  ← из cf-init
xml-gen config info <configPath>            ← из cf-info
xml-gen config edit <configPath> --op <op>  ← из cf-edit
xml-gen config validate <configPath>        ← из cf-validate
```

**Трудоёмкость:** средняя (~1200 строк Python-референса).

### Phase 4: Subsystem + Interface (средняя)

Проектировать совместно с CF — оба меняют Configuration.xml и дерево ChildObjects.

```
xml-gen subsystem compile <json> <outputDir>
xml-gen subsystem edit <path> --op <op>
xml-gen subsystem info <path>
xml-gen subsystem validate <path>

xml-gen interface edit <ciPath> --op <op>
xml-gen interface validate <ciPath>
```

**Трудоёмкость:** средняя (~2100 строк Python-референса).

### Phase 5: Meta — Объекты метаданных (высокая, разбита на подфазы)

Самый крупный блок: ~7500 строк Python → ~12-15K Java.

```
xml-gen meta compile <json> <outputDir>     ← из meta-compile
xml-gen meta edit <objectPath> --op <op>    ← из meta-edit
xml-gen meta info <objectPath>              ← из meta-info
xml-gen meta validate <objectPath>          ← из meta-validate
xml-gen meta remove <configDir> <Type.Name> ← из meta-remove
```

**Подфазы:**
- **5a:** info + validate (read-only, нужны для отладки compile)
- **5b:** compile — ссылочные типы (Catalog, Document, Enum, ExchangePlan, ChartOf* — 7 типов, наиболее востребованы)
- **5c:** compile — регистры (InformationRegister, AccumulationRegister, AccountingRegister, CalculationRegister — 4 типа)
- **5d:** compile — прочие (CommonModule, Report, DataProcessor, Constant, DefinedType, HTTPService, WebService, ScheduledJob, BusinessProcess, Task, DocumentJournal, EventSubscription — 12 типов)
- **5e:** edit + remove

**Предварительное условие:** gap-анализ mdclasses 0.17.4 — какие из 23 типов покрыты.

### Phase 6: CFE — Расширения конфигурации (высокая)

Требует Phase 3 (CF) как основу.

```
xml-gen extension init <name>               ← из cfe-init
xml-gen extension borrow <ext> <cfg> <obj>  ← из cfe-borrow
xml-gen extension diff <ext> <cfg>          ← из cfe-diff
xml-gen extension validate <extPath>        ← из cfe-validate
```

**Специфика:** ObjectBelonging, ExtendedConfigurationObject, ID-диапазоны (base 1-999999, ext 1000000+).
**Трудоёмкость:** высокая (~2800 строк Python-референса).

---

## 5. Ключевые спецификации и guides для копирования в проект

### DSL-спецификации (формат входных данных)

| Файл в cc-1c-skills | Цель | Фаза |
|---------------------|------|------|
| `docs/form-dsl-spec.md` | JSON DSL для form-compile | Existing+Info |
| `docs/skd-dsl-spec.md` | JSON DSL для skd-compile | Existing+Info |
| `docs/role-dsl-spec.md` | JSON DSL для role-compile | Existing+Info |
| `docs/mxl-dsl-spec.md` | JSON DSL для mxl-compile | Existing+Info |
| `docs/meta-dsl-spec.md` | JSON DSL для meta-compile (v2.1) | Meta |
| `meta-compile/reference/types-*.md` | Справочник типов по категориям | Meta |
| `meta-edit/json-dsl.md` | DSL для edit-операций | Meta |
| `meta-edit/child-operations.md` | Операции над вложенными объектами | Meta |
| `meta-edit/properties-reference.md` | Справочник свойств | Meta |

### XML-спецификации (структура выходных файлов)

| Файл в cc-1c-skills | Цель | Фаза |
|---------------------|------|------|
| `docs/1c-epf-spec.md` | Структура EPF XML | Existing+ERF |
| `docs/1c-erf-spec.md` | Структура ERF XML | Existing+ERF |
| `docs/1c-form-spec.md` | Структура Form.xml | Existing+Info |
| `docs/1c-role-spec.md` | Структура Rights.xml | Existing+Info |
| `docs/1c-dcs-spec.md` | Структура DataCompositionSchema | Existing+Info |
| `docs/1c-spreadsheet-spec.md` | Структура SpreadsheetDocument | Existing+Info |
| `docs/1c-config-objects-spec.md` | XML-структуры 23 типов объектов | Meta |
| `docs/1c-configuration-spec.md` | Configuration.xml | CF |
| `docs/1c-extension-spec.md` | CFE-расширения | CFE |
| `docs/1c-subsystem-spec.md` | Подсистемы | Subsystem |
| `docs/1c-help-spec.md` | Структура Help.xml | Utilities |
| `docs/1c-specs-index.md` | Мета-ссылка на все спецификации | Общий |

### Guides (workflow, edge cases, порядок операций)

| Файл в cc-1c-skills | Цель | Фаза |
|---------------------|------|------|
| `docs/epf-guide.md` | Workflow EPF: scaffold → формы → сборка | Existing+ERF |
| `docs/form-guide.md` | Workflow форм, edge cases | Existing+Info |
| `docs/skd-guide.md` | Workflow СКД | Existing+Info |
| `docs/mxl-guide.md` | Workflow макетов | Existing+Info |
| `docs/role-guide.md` | Workflow ролей | Existing+Info |
| `docs/meta-guide.md` | Workflow метаданных, типичные сценарии | Meta |
| `docs/cf-guide.md` | Workflow конфигурации | CF |
| `docs/cfe-guide.md` | Workflow расширений | CFE |
| `docs/subsystem-guide.md` | Workflow подсистем | Subsystem |
| `docs/form-patterns.md` | Паттерны компоновки форм | Existing+Info |

---

## 6. Что НЕ переносить в xml-gen

| Навык | Причина |
|-------|---------|
| `erf-build`, `erf-dump` | Требуют 1С-платформу; покрываются командами `epf build`/`dump` (ERF = EPF с флагом `--type report`) |
| `db-create`, `db-run`, `db-update` | Управление ИБ через ibcmd/1cv8 — отдельный домен |
| `db-dump-cf`, `db-dump-xml`, `db-load-cf`, `db-load-xml` | Выгрузка/загрузка конфигурации через ibcmd — требуют платформу |
| `db-load-git` | Определяет изменения через git, но загружает через ibcmd — требует платформу |
| `db-list` | Управление `.v8-project.json` — файловая операция, но не XML-генерация. Отдельный утилитарный навык |
| `web-*` (publish, unpublish, info, stop, test) | Управление Apache — отдельный домен |
| `epf-bsp-init`, `epf-bsp-add-command` | Генерация BSL-кода, не XML |
| `cfe-patch-method` (BSL-часть) | Генерация BSL-декораторов — out of scope. XML-часть (поиск модуля, проверка ObjectBelonging) — in scope Phase CFE |
| `img-grid` | Обработка изображений — вне scope |
| `form-patterns` | Knowledge skill — уже есть в `bsl-practices/` |

## 7. Архитектурные риски

| Риск | Влияние | Статус | Митигация |
|------|---------|--------|-----------|
| ~~**mdclasses 0.17.4 не покрывает все 23 типа Meta**~~ | ~~Блокер Phase 5~~ | ✅ Снят | Gap-анализ проведён 2026-03-09: все 23 типа покрыты. `MDOType` enum содержит 75 констант, у каждого типа есть модельный класс с builder (Lombok). Дописывать библиотеку не нужно |
| **TypeResolver не использует MDOType** | Дублирование логики, ручной string matching | ⚠️ Актуален | Переключить `TypeResolver` на `MDOType` enum из bsl-common-library вместо ручных строк. Сделать при Phase 5 (Meta) |
| **Нет MetadataTypeRegistry** | Нет маппинга тип → каталог выгрузки → namespace → правила регистрации | ⚠️ Актуален | Создать интеграционный слой `MetadataTypeRegistry` поверх `MDOType`: тип → путь в файловой структуре, namespace, правила регистрации в Configuration.xml. Использовать данные из `1c-config-objects-spec.md` |
| **Взрыв доменных моделей** | 23 типа → 23 DSL-класса? | ⚠️ Актуален | Использовать generic MetaDsl с Map<String,Object> + type-dispatch в MetaWriter, а не 23 отдельных POJO |
| **Смешение уровней абстракции** | meta-compile пишет XML + правит Configuration.xml + создает модули | ⚠️ Актуален | Чёткое разделение: MetaWriter (XML), ConfigEditor (регистрация), ModuleWriter (BSL-шаблоны) |
| **Версионность форматов 2.17/2.20** | Configuration/Subsystem/CFE имеют два формата | ⚠️ Актуален | Параметр `--platform-version`; по умолчанию 2.17 (8.3.20+) |
| **Commands.java switchboard** | Текущий switchboard не масштабируется | ⚠️ Актуален | Перейти на registrable command model при Phase 3+ |
| **ID/UUID/path consistency** | Между объектом, ChildObjects и вложенными файлами | ⚠️ Актуален | Централизованный UuidRegistry; unit-тесты на consistency |
