# Mapping: навыки Широкова (cc-1c-skills) → xml-gen Java CLI

> Дата: 2026-03-09 (rev.2 — актуализация по коду 2026-03-16)
> Репо-референс: https://github.com/Nikolay-Shirokov/cc-1c-skills (commit 86c8440)
> Наш CLI: `tools/xml-gen/` v0.1.0-SNAPSHOT
> Точка импорта: 2026-02-12 (commit acee4bd в cc-1c-skills)
> Дельта: +69K строк, 218 файлов
>
> **Scope:** Документ покрывает только навыки, работающие с XML-файлами (49 операций).
> Навыки, требующие 1С-платформу (db-*, web-*, epf-build/dump), вынесены в раздел 6.
> В репозитории Широкова всего ~67 навыков.
>
> **Актуализация rev.2:** Статусы покрытия пересчитаны по реальному коду (`Commands.java`, классы Writer/Editor/Validator/InfoPrinter). Раздел 4 преобразован из плана реализации в gap-анализ глубины.

---

## 1. Архитектурные различия

| Аспект | cc-1c-skills (Широков) | xml-gen (наш) |
|--------|----------------------|---------------|
| Язык | Python 3 + PowerShell | Java 21 (StAX + Jackson) |
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
| `epf-build` | Сборка EPF из XML (через 1cv8) | — | ❌ Нет | Требует 1С-платформу, пакет `platform/`, PlatformResolver + StubDatabaseBuilder |
| `epf-dump` | Разборка EPF в XML (через 1cv8) | — | ❌ Нет | Требует 1С-платформу, требует базу для ссылочных типов |
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
| `erf-init` | Scaffold ERF (XML + ObjectModule + опц. SKD) | `xml-gen epf init --type report` | ✅ Есть | Поддержка `--type report` добавлена |
| `erf-build` | Сборка ERF из XML | — | ❌ Нет | Требует 1С-платформу |
| `erf-dump` | Разборка ERF в XML | — | ❌ Нет | Требует 1С-платформу |
| `erf-validate` | Валидация XML отчёта | `xml-gen validate --type epf` | 🔶 Частично | EPF-валидатор работает; нет проверки MainDataCompositionSchema |

---

### 2.3. Form — Управляемые формы

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `form-compile` | JSON DSL → Form.xml | `xml-gen form compile` | ✅ Есть | У Широкова +1120 строк Python, type synonym resolution |
| `form-edit` | Добавить элементы/реквизиты/команды | `xml-gen form add-element`, `add-attribute`, `add-command`, `remove-element`, `move-element` | ✅ Есть | У Широкова +1303 строк Python |
| `form-info` | Анализ структуры формы | `xml-gen form info` | ✅ Есть | Парсинг Form.xml → элементы, реквизиты, команды, события |
| `form-validate` | Валидация формы | `xml-gen validate --type form` | ✅ Есть | |
| `form-add` | Регистрация формы в ChildObjects объекта | `xml-gen form add` | ✅ Есть | Универсальный через ObjectContainerEditor (любой тип объекта) |
| `form-remove` | Удаление формы из объекта | `xml-gen form remove` | ✅ Есть | Удаление + очистка DefaultForm |
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
| `mxl-decompile` | Template.xml → JSON DSL (обратно) | `xml-gen mxl decompile` | ✅ Есть | Обратная конвертация XML → JSON DSL |
| `mxl-info` | Анализ структуры макета | `xml-gen mxl info` | ✅ Есть | Области, параметры, колонки |
| `mxl-validate` | Валидация макета | `xml-gen validate --type mxl` | ✅ Есть | |

---

### 2.5. SKD — Схема компоновки данных

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `skd-compile` | JSON DSL → DataCompositionSchema XML | `xml-gen skd compile` | ✅ Есть | У Широкова +1431 строк Python |
| `skd-edit` | Точечное редактирование СКД | `xml-gen skd add-parameter`, `add-field` | 🔶 Частично | У нас 2 операции; у Широкова больше |
| `skd-info` | Анализ структуры СКД | `xml-gen skd info` | ✅ Есть | Наборы, поля, параметры, варианты |
| `skd-validate` | Валидация СКД | `xml-gen validate --type skd` | ✅ Есть | |

---

### 2.6. Role — Роли и права

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `role-compile` | JSON DSL → Rights.xml | `xml-gen role compile` | ✅ Есть | |
| `role-info` | Аудит прав: объекты, RLS, шаблоны | `xml-gen role info` | ✅ Есть | |
| `role-validate` | Валидация роли | `xml-gen validate --type role` | ✅ Есть | |

---

### 2.7. Meta — Объекты метаданных конфигурации

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `meta-compile` | JSON DSL → XML для 23 типов (Catalog, Document, Register, …) | `xml-gen meta compile` | ✅ Есть | MetaWriter + MetadataTypeRegistry. Референс: 2946 PS1 + 2572 Python |
| `meta-edit` | add-attribute, add-ts, add-dimension, remove, modify | `xml-gen meta edit` | ✅ Есть | MetaEditor. Референс: 2348 PS1 + 2200 Python |
| `meta-info` | Парсинг XML объекта → компактная сводка | `xml-gen meta info` | ✅ Есть | MetaInfoPrinter. Референс: 1119 PS1 + 1098 Python |
| `meta-remove` | Удаление объекта из конфигурации (с проверкой ссылок) | `xml-gen meta remove` | ✅ Есть | MetaRemover. Референс: 475 PS1 + 470 Python |
| `meta-validate` | Валидация объекта метаданных (13+ проверок) | `xml-gen meta validate` | ✅ Есть | MetaValidator. Референс: 1297 PS1 + 1209 Python |

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

### 2.8. CF — Конфигурация

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `cf-init` | Scaffold Configuration.xml + Languages/ | `xml-gen config init` | ✅ Есть | ConfigWriter. Референс: 215 PS1 + 203 Python |
| `cf-info` | Анализ конфигурации (свойства, состав, счётчики) | `xml-gen config info` | ✅ Есть | ConfigInfoPrinter. Референс: 387 PS1 + 402 Python |
| `cf-edit` | Изменить свойства, добавить/удалить объект | `xml-gen config edit` | ✅ Есть | ConfigEditor |
| `cf-validate` | Валидация Configuration.xml | `xml-gen config validate` | ✅ Есть | ConfigValidator. Референс: 538 PS1 + 532 Python |

**📋 Спецификация:** `docs/1c-configuration-spec.md` — полная структура Configuration.xml, 44 типа ChildObjects в строгом порядке.

---

### 2.9. CFE — Расширения конфигурации

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `cfe-init` | Scaffold расширения | `xml-gen extension init` | ✅ Есть | ExtensionWriter. Purpose: Patch/Customization/AddOn |
| `cfe-borrow` | Заимствование объекта из конфигурации | `xml-gen extension borrow` | ✅ Есть | ExtensionEditor. ObjectBelonging=Adopted, ExtendedConfigurationObject, заимствование форм |
| `cfe-diff` | Анализ: состав, перехватчики, проверка переноса | `xml-gen extension diff` | ✅ Есть | ExtensionDiffPrinter. Mode A (обзор) / Mode B (проверка переноса) |
| `cfe-patch-method` | Генерация перехватчика (&Перед/&После/&Вместо) | — | ❌ Нет | BSL-генерация out of scope. XML-часть (поиск модуля, проверка ObjectBelonging) покрыта через `extension borrow` |
| `cfe-validate` | Валидация расширения | `xml-gen extension validate` | ✅ Есть | ExtensionValidator. 9 проверок. Референс: 607 PS1 + 596 Python |

**📋 Спецификация:** `docs/1c-extension-spec.md` — ObjectBelonging, ID-диапазоны (base 1-999999, ext 1000000+), callType, diff-маркеры.

---

### 2.10. Subsystem — Подсистемы

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `subsystem-compile` | JSON → Subsystem XML + регистрация | `xml-gen subsystem compile` | ✅ Есть | SubsystemWriter. Референс: 338 PS1 + 288 Python |
| `subsystem-edit` | add/remove-content, add/remove-child | `xml-gen subsystem edit` | ✅ Есть | SubsystemEditor. Референс: 414 PS1 + 464 Python |
| `subsystem-info` | Состав, дочерние, командный интерфейс, дерево | `xml-gen subsystem info` | ✅ Есть | SubsystemInfoPrinter. Референс: 514 PS1 + 525 Python |
| `subsystem-validate` | 13 проверок | `xml-gen subsystem validate` | ✅ Есть | SubsystemValidator. Референс: 325 PS1 + 351 Python |

**📋 Спецификация:** `docs/1c-subsystem-spec.md` — Content, вложенные подсистемы, CommandInterface.xml.

---

### 2.11. Interface — Командный интерфейс

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `interface-edit` | hide, show, place, order, subsystem-order, group-order | `xml-gen interface edit` | ✅ Есть | InterfaceEditor |
| `interface-validate` | 13 проверок | `xml-gen interface validate` | ✅ Есть | InterfaceValidator |

---

### 2.12. Утилитарные навыки

| Навык Широкова | Что делает | xml-gen команда | Статус | Примечания |
|----------------|-----------|-----------------|--------|------------|
| `help-add` | Встроенная справка (Help.xml + HTML) | `xml-gen help add` | ✅ Есть | Help.xml + HTML-шаблон |
| `template-add` | Универсальный add-template (любой объект) | `xml-gen template add` | ✅ Есть | ObjectContainerEditor, универсальный (любой тип объекта) |
| `template-remove` | Удаление макета из объекта | `xml-gen template remove` | ✅ Есть | Удаление + очистка файлов |
| `img-grid` | Наложение сетки на изображение | — | ❌ Нет | Вне scope xml-gen |

---

## 3. Сводная таблица покрытия

| Домен | compile | edit | info | validate | remove | init/other | Итого |
|-------|---------|------|------|----------|--------|------------|-------|
| **EPF** | — | ✅ 2 ops | — | ✅ | — | ✅ init | 3/7 (build/dump требуют платформу) |
| **ERF** | — | — | — | 🔶 | — | ✅ init | 1/4 (build/dump требуют платформу) |
| **Form** | ✅ | ✅ 5 ops | ✅ | ✅ | ✅ remove | ✅ add | 7/7 |
| **MXL** | ✅ | — | ✅ | ✅ | — | ✅ decompile | 4/4 |
| **SKD** | ✅ | 🔶 2 ops | ✅ | ✅ | — | — | 4/4 |
| **Role** | ✅ | ✅ 2 ops | ✅ | ✅ | — | — | 4/4 |
| **Meta** | ✅ | ✅ | ✅ | ✅ | ✅ | — | 5/5 |
| **CF** | — | ✅ | ✅ | ✅ | — | ✅ init | 4/4 |
| **CFE** | — | ✅ borrow | ✅ diff | ✅ | — | ✅ init | 4/5 (patch-method — BSL, out of scope) |
| **Subsystem** | ✅ | ✅ | ✅ | ✅ | — | — | 4/4 |
| **Interface** | — | ✅ | — | ✅ | — | — | 2/2 |
| **Utilities** | — | — | — | — | ✅ tmpl rm | ✅ help/tmpl add | 3/3 (img-grid out of scope) |

**Покрытие: 45 из 49 XML-операций (~92%)**

**Не реализовано (4):**
- `epf build` / `epf dump` — требуют 1С-платформу (2 операции)
- `cfe-patch-method` — BSL-генерация, out of scope (1 операция)
- `erf-validate` полноценный — нет проверки MainDataCompositionSchema (частично, 1 операция)

---

## 4. Нереализованные операции

> Все 6 фаз из первоначального плана расширения (Phases 1-6) реализованы.
> Ниже перечислены оставшиеся пробелы.

### 4.1. Платформенные команды (требуют 1С)

| Команда | Описание | Референс | Приоритет |
|---------|----------|----------|-----------|
| `xml-gen epf build` | Сборка EPF/ERF из XML через 1cv8 | `epf-build.py` (143 строки) + `stub-db-create.py` (1085 строк) | Высокий |
| `xml-gen epf dump` | Разборка EPF/ERF в XML через 1cv8 | `epf-dump.py` (136 строк) | Высокий |

**Архитектура:** Новый пакет `platform/` — PlatformResolver, StubDatabaseBuilder, EpfBuildCommand, EpfDumpCommand.

### 4.2. BSL-генерация (out of scope XML)

| Навык | Описание | Решение |
|-------|----------|---------|
| `cfe-patch-method` | Генерация перехватчиков &Перед/&После/&Вместо | XML-часть покрыта `extension borrow`. BSL-генерация — отдельный инструмент |
| `epf-bsp-init` | СведенияОВнешнейОбработке() | BSL-код, не XML |
| `epf-bsp-add-command` | Команда БСП | BSL-код, не XML |

### 4.3. Частичные реализации

| Команда | Что есть | Чего не хватает |
|---------|----------|-----------------|
| `erf-validate` | EPF-валидатор работает для ERF | Нет проверки MainDataCompositionSchema |
| `skd edit` | add-parameter, add-field | У Широкова больше операций |

---

## 5. Ключевые спецификации и guides для копирования в проект

### DSL-спецификации (формат входных данных)

| Файл в cc-1c-skills | Цель | Статус |
|---------------------|------|--------|
| `docs/form-dsl-spec.md` | JSON DSL для form-compile | Справочный |
| `docs/skd-dsl-spec.md` | JSON DSL для skd-compile | Справочный |
| `docs/role-dsl-spec.md` | JSON DSL для role-compile | Справочный |
| `docs/mxl-dsl-spec.md` | JSON DSL для mxl-compile | Справочный |
| `docs/meta-dsl-spec.md` | JSON DSL для meta-compile (v2.1) | Справочный |
| `meta-compile/reference/types-*.md` | Справочник типов по категориям | Справочный |
| `meta-edit/json-dsl.md` | DSL для edit-операций | Справочный |
| `meta-edit/child-operations.md` | Операции над вложенными объектами | Справочный |
| `meta-edit/properties-reference.md` | Справочник свойств | Справочный |

### XML-спецификации (структура выходных файлов)

| Файл в cc-1c-skills | Цель | Статус |
|---------------------|------|--------|
| `docs/1c-epf-spec.md` | Структура EPF XML | Справочный |
| `docs/1c-erf-spec.md` | Структура ERF XML | Справочный |
| `docs/1c-form-spec.md` | Структура Form.xml | Справочный |
| `docs/1c-role-spec.md` | Структура Rights.xml | Справочный |
| `docs/1c-dcs-spec.md` | Структура DataCompositionSchema | Справочный |
| `docs/1c-spreadsheet-spec.md` | Структура SpreadsheetDocument | Справочный |
| `docs/1c-config-objects-spec.md` | XML-структуры 23 типов объектов | Справочный |
| `docs/1c-configuration-spec.md` | Configuration.xml | Справочный |
| `docs/1c-extension-spec.md` | CFE-расширения | Справочный |
| `docs/1c-subsystem-spec.md` | Подсистемы | Справочный |
| `docs/1c-help-spec.md` | Структура Help.xml | Справочный |
| `docs/1c-specs-index.md` | Мета-ссылка на все спецификации | Справочный |

### Guides (workflow, edge cases, порядок операций)

| Файл в cc-1c-skills | Цель | Статус |
|---------------------|------|--------|
| `docs/epf-guide.md` | Workflow EPF: scaffold → формы → сборка | Справочный |
| `docs/form-guide.md` | Workflow форм, edge cases | Справочный |
| `docs/skd-guide.md` | Workflow СКД | Справочный |
| `docs/mxl-guide.md` | Workflow макетов | Справочный |
| `docs/role-guide.md` | Workflow ролей | Справочный |
| `docs/meta-guide.md` | Workflow метаданных, типичные сценарии | Справочный |
| `docs/cf-guide.md` | Workflow конфигурации | Справочный |
| `docs/cfe-guide.md` | Workflow расширений | Справочный |
| `docs/subsystem-guide.md` | Workflow подсистем | Справочный |
| `docs/form-patterns.md` | Паттерны компоновки форм | Справочный |

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

## 7. Архитектурные риски и технический долг

> Обновлено 2026-03-16: статусы проверены по текущему коду.

| Риск / тех. долг | Влияние | Статус | Примечание |
|------------------|---------|--------|------------|
| ~~**mdclasses 0.17.4 не покрывает все 23 типа Meta**~~ | ~~Блокер~~ | ✅ Снят | Все 23 типа покрыты. `MDOType` enum: 75 констант |
| **MetadataTypeRegistry** | Маппинг тип → каталог/namespace/правила регистрации | ✅ Реализован | Класс `MetadataTypeRegistry.java` создан |
| **TypeResolver не использует MDOType** | Дублирование логики, ручной string matching | ⚠️ Тех. долг | Нужно переключить на `MDOType` enum |
| **Commands.java switchboard** | ~2100 строк switch-case | ⚠️ Тех. долг | Работает, но не масштабируется. Рефакторинг в registrable command model |
| **Версионность форматов 2.17/2.20** | Configuration/Subsystem/CFE имеют два формата | ⚠️ Тех. долг | Параметр `--platform-version` не реализован; всё на 2.17 |
| **ID/UUID/path consistency** | Между объектом, ChildObjects и вложенными файлами | ⚠️ Тех. долг | Нет централизованного UuidRegistry |
| **Глубина реализации vs Широков** | Наши реализации могут быть проще оригинала | ⚠️ Требует gap-анализ | См. раздел 4 expansion plan (отдельный документ) |
