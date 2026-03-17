# План расширения xml-gen CLI

> Дата: 2026-03-09 (обновлено 2026-03-16)
> Основание: [shirokov-to-xmlgen-mapping-2026-03-09.md](shirokov-to-xmlgen-mapping-2026-03-09.md)
> Ревью: GPT (gpt-5.4) + Opus (claude-opus-4-6), 2026-03-09
> Текущая версия: xml-gen 0.1.0-SNAPSHOT (11 доменов, ~45 операций)
>
> **Статус 2026-03-16:** Phases 1-6 реализованы. Покрытие: 45/49 XML-операций (~92%).
> Оставшиеся пробелы см. [xml-gen-gap-analysis-2026-03-16.md](xml-gen-gap-analysis-2026-03-16.md).
> Единственная нереализованная фаза — Phase 2.5 (epf build/dump, требуют 1С-платформу).

---

## Принципы

1. **ROI-first** — сначала дешёвые расширения существующих доменов, потом новые
2. **info рядом с compile** — read-only команды включаются в фазу домена
3. **Общий object-container API** — form-add/template-add/help-add работают с любым типом объекта
4. **Спецификации из cc-1c-skills как reference** — не портируем скрипты, а используем как источник знаний для Java-реализации

---

## Phase 1: Добивание существующих доменов

**Цель:** info + decompile для Form/SKD/Role/MXL + ERF init
**Сложность:** низкая | **Новые команды:** 7

> **Примечание:** перед началом реализации — составить SDD (Software Design Document) по стандарту спецификации проекта.

### Задачи

| # | Команда | Описание | Источник знаний | Оценка |
|---|---------|----------|-----------------|--------|
| 1.1 | `xml-gen epf init --type report` | ERF scaffold: ExternalReport + опц. SKD | `erf-init`, `1c-erf-spec.md`, `epf-guide.md` | S |
| 1.2 | `xml-gen form info <xml>` | Парсинг Form.xml → элементы, реквизиты, команды, события | `form-info.py` (601 строк), `1c-form-spec.md`, `form-guide.md` | M |
| 1.3 | `xml-gen skd info <xml>` | Наборы, поля, параметры, варианты | `skd-info.py` (1681 строк), `1c-dcs-spec.md`, `skd-guide.md` | M |
| 1.4 | `xml-gen role info <xml>` | Объекты, права, RLS, шаблоны | `role-info.py` (232 строки), `1c-role-spec.md`, `role-guide.md` | S |
| 1.5 | `xml-gen mxl info <xml>` | Области, параметры, колонки | `mxl-info.py` (445 строк), `1c-spreadsheet-spec.md`, `mxl-guide.md` | S |
| 1.6 | `xml-gen mxl decompile <xml> <json>` | Template.xml → JSON DSL (обратная) | `mxl-decompile.py` (705 строк), `mxl-dsl-spec.md` | M |
| 1.7 | `xml-gen validate --type erf` | Валидация ERF (расширить EPF-валидатор) | `erf-validate`, `1c-erf-spec.md` | S |

### Архитектурные решения

- Info-команды реализовать как `XmlStructureReader` → formatted output (text/json)
- ERF: расширить `EpfWriter` и `EpfValidator` флагом `isReport`
- Decompile: новый пакет `decompiler/` с `MxlDecompiler`

### Definition of Done

- [ ] Все XML-команды работают в Designer-формате
- [ ] Unit-тесты с golden-файлами (из cc-1c-skills examples)
- [ ] SKILL.md обновлены в `xml-gen-cli/`

---

## Phase 2: Универсальный object-container API

**Цель:** form/template/help add/remove для любого типа объекта
**Сложность:** средняя | **Новые команды:** 5
**Зависимость:** Phase 1 (ERF)

> **Примечание:** перед началом реализации — составить SDD по стандарту спецификации проекта.

### Задачи

| # | Команда | Описание | Источник знаний | Оценка |
|---|---------|----------|-----------------|--------|
| 2.1 | Рефакторинг: `ObjectContainerEditor` | Написать с нуля общий API для работы с ChildObjects любого объекта (EPF, ERF, Catalog, Document и т.д.). Существующий `EpfEditor` не работает с ChildObjects — `ObjectContainerEditor` создаётся как новый класс | `form-add.py`, `template-add.py`, `help-add.py` — паттерн одинаковый | L |
| 2.2 | `xml-gen form add <objectPath> <formName>` | Регистрация формы в ChildObjects + scaffold Form.xml | `form-add.py` (450 строк), `form-guide.md` | M |
| 2.3 | `xml-gen form remove <objectPath> <formName>` | Удаление формы + очистка DefaultForm | `form-remove.py` (99 строк) | S |
| 2.4 | `xml-gen template add <objectPath> <name> --type <type>` | Универсальный add-template | `template-add.py` (251 строк) | S |
| 2.5 | `xml-gen help add <objectPath>` | Help.xml + HTML-шаблон | `help-add.py` (145 строк), `1c-help-spec.md` | S |

### Архитектурные решения

- `ObjectContainerEditor` — новый класс, не извлечение из `EpfEditor` (тот не работает с ChildObjects)
- Определение типа объекта по корневому XML-элементу (ExternalDataProcessor, Catalog, Document, …)
- `template remove` — реализовать вместе с add

### Definition of Done

- [ ] `form add` работает для EPF, ERF, Catalog, Document
- [ ] `template add/remove` работает для любого объекта
- [ ] Обратная совместимость с `epf add-form` / `epf add-template`

---

## Phase 2.5: EPF Build/Dump (платформенные команды)

**Цель:** сборка/разборка EPF/ERF через 1С-платформу
**Сложность:** высокая | **Новые команды:** 2
**Зависимость:** Phase 1 (ERF scaffold), наличие 1С-платформы

> **Примечание:** перед началом реализации — составить SDD по стандарту спецификации проекта.

### Задачи

| # | Команда | Описание | Источник знаний | Оценка |
|---|---------|----------|-----------------|--------|
| 2.5.1 | `xml-gen epf build` | Сборка EPF/ERF из XML через 1С-платформу | `epf-build.py` (143 строки) + `stub-db-create.py` (1085 строк), `epf-guide.md`, `build-spec.md` | XL |
| 2.5.2 | `xml-gen epf dump` | Разборка EPF/ERF в XML через 1С-платформу | `epf-dump.py` (136 строк), `epf-guide.md` | M |

### Архитектурные решения

- **Новый пакет `platform/`** — команды, требующие 1С-платформу:
  ```
  io.github.onec.xmlgen.platform/
  ├── PlatformResolver.java      — поиск 1cv8/ibcmd, чтение .v8-project.json
  ├── EpfBuildCommand.java       — xml-gen epf build
  ├── EpfDumpCommand.java        — xml-gen epf dump
  └── StubDatabaseBuilder.java   — временная база с заглушками метаданных
  ```
- `PlatformResolver` — порядок поиска: `--v8-path` CLI → `V8_PATH` env → `.v8-project.json` → автоопределение (`/opt/1cv8/*/1cv8`, `C:\Program Files\1cv8\*\bin\1cv8.exe`)
- При отсутствии платформы — понятная ошибка: `Error: 1C platform not found. Specify --v8-path or set V8_PATH`
- `PlatformResolver` переиспользуется в будущих Phase (db-*, cf load/dump)
- `StubDatabaseBuilder` — сложность XL из-за генерации заглушек метаданных для ссылочных типов (1085 строк в оригинале)

### Definition of Done

- [ ] `epf build` собирает EPF из XML-исходников (с auto-stub для ссылочных типов)
- [ ] `epf dump` разбирает EPF в XML (с проверкой наличия базы для ссылочных типов)
- [ ] При отсутствии платформы — ошибка с подсказкой, не silent failure
- [ ] `PlatformResolver` покрыт тестами (mock filesystem)

---

## Phase 3: CF — Конфигурация

**Цель:** init/info/edit/validate для Configuration.xml
**Сложность:** средняя | **Новые команды:** 5
**Зависимость:** Phase 2 (object-container API для cf-edit add-child)

> **Примечание:** перед началом реализации — составить SDD по стандарту спецификации проекта.

### Задачи

| # | Команда | Описание | Источник знаний | Оценка |
|---|---------|----------|-----------------|--------|
| 3.0 | Рефакторинг: command model | Рефакторинг модели команд (switch-case → registry/dispatch) для масштабирования на 40+ команд. Без этого добавление новых доменов будет усложняться | Текущий `Commands.java` | M |
| 3.1 | `xml-gen config init <name>` | Scaffold Configuration.xml + Languages/ | `cf-init.py` (203 строки), `1c-configuration-spec.md` | S |
| 3.2 | `xml-gen config info <path>` | Свойства, состав, счётчики объектов | `cf-info.py` (402 строки), `cf-guide.md` | M |
| 3.3 | `xml-gen config edit <path> --op <op>` | modify-property, add/remove-child, add/remove-defaultRole | `cf-edit.py`, `1c-configuration-spec.md` | M |
| 3.4 | `xml-gen config validate <path>` | XML, InternalInfo, свойства, ChildObjects ordering (44 типа) | `cf-validate.py` (532 строки), `1c-configuration-spec.md` | M |

### Архитектурные решения

- **ChildObjects ordering:** 44 типа в строгом порядке — хранить как enum-массив
- **ConfigEditor** наследует от `ObjectContainerEditor` (Phase 2)
- **Версионность:** параметр `--platform-version` (default: 8.3.24 / format 2.17)

### Definition of Done

- [ ] `config init` создаёт валидный Configuration.xml (проходит `config validate`)
- [ ] `config edit add-child` сохраняет порядок 44 типов
- [ ] Валидатор проверяет ChildObjects ordering

---

## Phase 4: Subsystem + Interface

**Цель:** CRUD подсистем + управление командным интерфейсом
**Сложность:** средняя | **Новые команды:** 6
**Зависимость:** Phase 3 (CF — регистрация подсистем в Configuration.xml)

> **Примечание:** перед началом реализации — составить SDD по стандарту спецификации проекта.

### Задачи

| # | Команда | Описание | Источник знаний | Оценка |
|---|---------|----------|-----------------|--------|
| 4.1 | `xml-gen subsystem compile <json> <outputDir>` | JSON → Subsystem XML + регистрация в Configuration.xml | `subsystem-compile.py` (288 строк), `1c-subsystem-spec.md` | M |
| 4.2 | `xml-gen subsystem edit <path> --op <op>` | add/remove-content, add/remove-child, set-property | `subsystem-edit.py` (464 строки) | M |
| 4.3 | `xml-gen subsystem info <path>` | Состав, дочерние, CommandInterface, дерево | `subsystem-info.py` (525 строк), `subsystem-guide.md` | M |
| 4.4 | `xml-gen subsystem validate <path>` | 13 проверок | `subsystem-validate.py` (351 строк) | S |
| 4.5 | `xml-gen interface edit <ciPath> --op <op>` | hide, show, place, order | `interface-edit.py` (444 строки) | M |
| 4.6 | `xml-gen interface validate <ciPath>` | 13 проверок | `interface-validate.py` (392 строки) | S |

### Definition of Done

- [ ] `subsystem compile` регистрирует подсистему в Configuration.xml через ConfigEditor
- [ ] `interface edit` корректно работает с 5 секциями CommandInterface.xml

---

## Phase 5: Meta — Объекты метаданных

**Цель:** CRUD для 23 типов объектов конфигурации
**Сложность:** высокая | **Новые команды:** 5 (но 23 типа каждая)
**Зависимость:** Phase 3 (CF — регистрация в Configuration.xml)

> **Примечание:** перед началом каждой подфазы — составить SDD по стандарту спецификации проекта.

### Предварительные задачи

> **mdclasses 0.17.4:** gap-анализ проведён 2026-03-09 — все 23 типа покрыты (`MDOType` enum, 75 констант).
> Дописывать библиотеку не нужно. Нужен интеграционный слой:
>
> 1. **`MetadataTypeRegistry`** — обёртка над `MDOType`: тип → каталог выгрузки, namespace, правила регистрации в Configuration.xml (данные из `1c-config-objects-spec.md`)
> 2. **Переключить `TypeResolver`** на `MDOType` enum вместо ручного string matching
> 3. **`MetaWriter`** с type-dispatch — один writer, который по MDOType выбирает стратегию генерации XML

### Подфаза 5a: info + validate (read-only)

| # | Команда | Источник знаний | Оценка |
|---|---------|-----------------|--------|
| 5a.1 | `xml-gen meta info <objectPath>` | `meta-info.py` (1098 строк), `1c-config-objects-spec.md` | L |
| 5a.2 | `xml-gen meta validate <objectPath>` | `meta-validate.py` (1209 строк) | L |

### Подфаза 5b: compile — ссылочные типы (7 типов)

Catalog, Document, Enum, ChartOfCharacteristicTypes, ChartOfAccounts, ChartOfCalculationTypes, ExchangePlan.

| # | Команда | Источник знаний | Оценка |
|---|---------|-----------------|--------|
| 5b.1 | `xml-gen meta compile <json> <outputDir>` | `meta-compile.py` (2572 строки), `meta-dsl-spec.md`, `reference/types-basic.md` | XL |

**Архитектурное решение:** generic `MetaDsl` (Map-based) вместо 23 отдельных POJO. `MetaWriter` с type-dispatch.

### Подфаза 5c: compile — регистры (4 типа)

InformationRegister, AccumulationRegister, AccountingRegister, CalculationRegister.

| # | Команда | Источник знаний | Оценка |
|---|---------|-----------------|--------|
| 5c.1 | Расширение `meta compile` | `reference/types-registers.md` | L |

### Подфаза 5d: compile — прочие (12 типов)

CommonModule, Report, DataProcessor, Constant, DefinedType, HTTPService, WebService, ScheduledJob, BusinessProcess, Task, DocumentJournal, EventSubscription.

| # | Команда | Источник знаний | Оценка |
|---|---------|-----------------|--------|
| 5d.1 | Расширение `meta compile` | `reference/types-process.md`, `reference/types-web.md` | L |

### Подфаза 5e: edit + remove

| # | Команда | Источник знаний | Оценка |
|---|---------|-----------------|--------|
| 5e.1 | `xml-gen meta edit <objectPath> --op <op>` | `meta-edit.py` (2200 строк), `json-dsl.md`, `child-operations.md`, `properties-reference.md` | XL |
| 5e.2 | `xml-gen meta remove <configDir> <Type.Name>` | `meta-remove.py` (470 строк) | M |

### Definition of Done — 5a (info + validate)

- [ ] `meta info` выводит свойства, реквизиты, табличные части для любого из 23 типов
- [ ] `meta validate` проверяет структуру, обязательные поля, ChildObjects для любого типа

### Definition of Done — 5b (compile — ссылочные типы)

- [ ] `meta compile` генерирует валидный XML для 7 ссылочных типов
- [ ] `meta compile` регистрирует объект в Configuration.xml (через ConfigEditor из Phase 3)
- [ ] `meta validate` проходит для всех сгенерированных объектов 5b

### Definition of Done — 5c (compile — регистры)

- [ ] `meta compile` генерирует валидный XML для 4 типов регистров
- [ ] Измерения, ресурсы, реквизиты корректно генерируются

### Definition of Done — 5d (compile — прочие)

- [ ] `meta compile` генерирует валидный XML для оставшихся 12 типов
- [ ] Полное покрытие всех 23 типов достигнуто

### Definition of Done — 5e (edit + remove)

- [ ] `meta edit` поддерживает add-attribute, add-tabular-section, add-dimension, add-resource, remove, modify
- [ ] `meta remove` удаляет объект и дерегистрирует из Configuration.xml

---

## Phase 6: CFE — Расширения конфигурации

**Цель:** init/borrow/diff/validate для расширений
**Сложность:** высокая | **Новые команды:** 4
**Зависимость:** Phase 3 (CF), Phase 5a (meta info/validate)

> **Примечание:** перед началом реализации — составить SDD по стандарту спецификации проекта.

### Задачи

| # | Команда | Описание | Источник знаний | Оценка |
|---|---------|----------|-----------------|--------|
| 6.1 | `xml-gen extension init <name>` | Scaffold с Purpose, NamePrefix, CompatibilityMode | `cfe-init.py` (239 строк), `1c-extension-spec.md`, `cfe-guide.md` | M |
| 6.2 | `xml-gen extension borrow <ext> <cfg> <obj>` | ObjectBelonging=Adopted, ExtendedConfigurationObject | `cfe-borrow.py` (846 строк) | L |
| 6.3 | `xml-gen extension diff <ext> <cfg>` | Mode A (обзор) / Mode B (проверка переноса) | `cfe-diff.py` (540 строк) | L |
| 6.4 | `xml-gen extension validate <extPath>` | Валидация расширения | `cfe-validate.py` (596 строк) | M |

### Архитектурные решения

- **ID-диапазоны:** base elements 1-999999, extension 1000000+ — учитывать в FormEditor
- **ObjectBelonging:** новый enum в TypeResolver
- **Diff-маркеры:** `#Вставка`/`#КонецВставки`, `#Удаление`/`#КонецУдаления` — BSL-анализ

### Definition of Done

- [ ] `extension init` создаёт валидное расширение (проходит `extension validate`)
- [ ] `extension borrow` корректно копирует Properties + устанавливает ObjectBelonging
- [ ] `extension diff` находит все заимствованные объекты и перехватчики

---

## Сводка

| Фаза | Команд | Сложность | Зависимости |
|------|--------|-----------|-------------|
| **1. Existing+Info** | 7 | Низкая | — |
| **2. Object-container** | 5 | Средняя | Phase 1 |
| **2.5. EPF Build/Dump** | 2 | Высокая | Phase 1, платформа |
| **3. CF** | 5 (+refactoring) | Средняя | Phase 2 |
| **4. Subsystem+Interface** | 6 | Средняя | Phase 3 |
| **5. Meta** (5 подфаз) | 5 × 23 типа | Высокая | Phase 3 |
| **6. CFE** | 4 | Высокая | Phase 3, 5a |
| **Итого** | ~38 команд | | |

### Шкала оценки размера задачи

| Размер | Описание |
|--------|----------|
| S | < 200 строк Java, 1 файл |
| M | 200-500 строк, 2-3 файла |
| L | 500-1000 строк, 3-5 файлов |
| XL | 1000+ строк, 5+ файлов |

---

## Обновление навыков (SKILL.md)

> Ревью: GPT (gpt-5.4), 2026-03-09

### Стратегия

1. **Prep-pass (до Phase 1):** зафиксировать целевую топологию навыков, починить битые `depends_on` в xml-generation, зарезервировать имена новых навыков
2. **Инкрементально по фазам:** обновлять навыки при завершении каждой фазы
3. **Frontmatter обязательно:** при обновлении body — обновлять и `name`/`description` в frontmatter (определяют триггеринг)
4. **Один index-навык:** xml-generation остаётся единственным обзорным навыком; xml-gen-cli — тонкий common CLI skill (validate + общие правила)

### Существующие навыки — план обновления

| # | Навык | Фазы влияния | Объём | Что менять |
|---|-------|-------------|-------|------------|
| 1 | **xml-generation** | 1,2,2.5,3,4,5,6 | XL | Таблица типов (5→11+), «Когда применять», ограничения, архитектура, ссылки на новые навыки, сценарии. Frontmatter: расширить description. Починить `depends_on` (битые пути без `platform-data/`) |
| 2 | **xml-gen-cli** | 1,2,3,4,5,6 | L | +info commands, +erf validate, +universal form/template/help add. При Phase 3+ — тонкий common-skill (validate + общие edit-правила), детали в domain-навыках |
| 3 | **epf-operations** | 1,2,2.5 | L | +ERF init (--type report), +структура ERF, deprecation `epf add-form`/`add-template` → universal, +epf build/dump (или ссылка на platform-operations) |
| 4 | **form-dsl** | 1,2 | S | +form info, уточнить: `form add` (ChildObjects) vs `form add-attribute` (внутри формы) |
| 5 | **skd-dsl** | 1 | S | +skd info |
| 6 | **role-dsl** | 1 | S | +role info. _(Phase 5 влияет только если расширяется role compile — в текущем плане не предусмотрено)_ |
| 7 | **mxl-dsl** | 1 | S | +mxl info, +mxl decompile |

### Новые навыки

| Навык | Фаза | Описание |
|-------|------|----------|
| **platform-operations** | 2.5 | PlatformResolver, epf build/dump, требования к 1С-платформе. Переиспользуется в будущих фазах (cf load/dump, db-*) |
| **config-operations** | 3 | Configuration init/info/edit/validate, ChildObjects ordering (44 типа) |
| **subsystem-operations** | 4 | Subsystem compile/info/edit/validate + Interface edit/validate |
| **meta-operations** | 5a | Meta info/validate/compile/edit/remove для 23 типов. Создать на 5a, расширять в 5b-5e |
| **extension-operations** | 6 | Extension init/borrow/diff/validate, ObjectBelonging |

### Архитектурные исправления (prep-pass)

- [ ] Починить `depends_on` в xml-generation: пути `framework/skills/tool-usage/xml-generation/...` → `framework/skills/tool-usage/platform-data/xml-generation/...`
- [ ] Обновить frontmatter description всех 7 навыков при первом же изменении body
- [ ] Для тяжёлых новых навыков (meta-operations, extension-operations) — сразу закладывать `references/` подкаталог

---

## Спецификации для копирования (первое действие)

Скопировать из `src_temp/cc-1c-skills/docs/` в `docs/reference/cc-1c-skills/`:

```bash
# Phase 1-2
cp 1c-erf-spec.md 1c-form-spec.md 1c-dcs-spec.md 1c-role-spec.md \
   1c-spreadsheet-spec.md 1c-help-spec.md \
   form-dsl-spec.md skd-dsl-spec.md role-dsl-spec.md mxl-dsl-spec.md \
   epf-guide.md form-guide.md skd-guide.md mxl-guide.md role-guide.md

# Phase 3-4
cp 1c-configuration-spec.md 1c-subsystem-spec.md cf-guide.md subsystem-guide.md

# Phase 5
cp meta-dsl-spec.md 1c-config-objects-spec.md meta-guide.md

# Phase 6
cp 1c-extension-spec.md cfe-guide.md

# Мета
cp 1c-specs-index.md
```
