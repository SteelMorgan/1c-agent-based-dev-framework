# SPEC-002: XML Generation Module

> Спецификация для реализации Java-модуля генерации XML метаданных 1С.
> Формат: MADR 4.0. Статус: **accepted** (реализовано 100%, 2026-02-12).

---

## Контекст и мотивация

### Проект

Репозиторий: `1c-agent-based-dev-framework` — фреймворк модульной агентной разработки для 1С BSL.
Архитектура фреймворка: [docs/SPEC-001-framework-architecture.md](SPEC-001-framework-architecture.md).
README: [README.md](../README.md).

### Проблема

AI-агенты (Cursor, Claude Code) не умеют создавать артефакты 1С — внешние обработки (EPF), управляемые формы, роли, макеты табличных документов (MXL), схемы компоновки данных (SKD). Все эти артефакты хранятся в проприетарном XML-формате, спецификация которого не опубликована 1С.

### Решение

Java-модуль, который:
1. Принимает компактный JSON DSL на входе
2. Генерирует корректный XML в форматах Designer и EDT
3. Использует `mdclasses` (1c-syntax) как зависимость для enum-ов и моделей
4. Покрыт roundtrip-тестами (read fixture → generate → compare)
5. Спроектирован для будущей передачи в open-source (потенциальный PR в `1c-syntax/mdclasses`)

### Стейкхолдеры

- **Valery Maximov** (@theshadowco) — мейнтейнер mdclasses. Подтвердил интерес к write-поддержке. Рекомендовал начать с EPF в EDT-формате. Отметил, что формы — самое сложное (читаются упрощённо из-за количества объектов). Предложил подход "конструктор" (залить структуру → получить объект).
- **1c-syntax community** — mdclasses (55 stars), BSL Language Server (385 stars, 8400+ коммитов). Используется как ядро open-source экосистемы 1С.

---

## Источники XML-формата

### Приоритет 1: 1c-syntax/mdclasses

- **Репозиторий**: https://github.com/1c-syntax/mdclasses (Java, LGPL-3.0)
- **Назначение**: библиотека чтения метаданных 1С (read-only, write НЕ поддерживается)
- **Что берём**:
  - **Enum-ы** как `implementation` зависимость:
    - `FormElementType` — 42 типа элементов формы (Button, InputField, Table, UsualGroup, Pages/Page, CommandBar, LabelDecoration и т.д.), EN/RU имена
    - `RoleRight` — 74 права (Administration, Read, Insert, Update, Delete, View, Edit и т.д.), EN/RU имена
    - `DataSetType` — типы наборов данных СКД (DataSetQuery, DataSetUnion, DataSetObject)
    - `TemplateType` — типы макетов (SpreadsheetDocument, HTMLDocument, TextDocument, BinaryData и т.д.)
  - **Модели** (интерфейсы и record-ы):
    - `ExternalDataProcessor` — uuid, name, synonym, commands, attributes, tabularSections, forms, templates, modules
    - `ManagedFormData` — title, handlers, items (дерево FormItem), attributes
    - `FormItem` — id, name, title, type (FormElementType), dataPath, items (children)
    - `FormAttribute` — id, name, title, ValueTypeDescription
    - `RoleData` — setForNewObjects, setForAttributesByDefault, independentRightsOfChildObjects, List<ObjectRight>
    - `RoleData.ObjectRight` — MdoReference name, List<Right> rights
    - `RoleData.Right` — RoleRight name, boolean value
    - `DataCompositionSchema` — List<DataSet> dataSets, Path dataPath
    - `DataCompositionSchema.DataSet` — name, DataSetType type, dataSource, items (дерево), querySource, fields
  - **Тестовые фикстуры** (`src/test/resources/ext/`) — эталонные XML-файлы в обоих форматах:
    - Designer: `ext/designer/external/src/epf/ТестоваяВнешняяОбработка.xml` и подкаталоги (Forms/, Templates/, Ext/)
    - EDT: `ext/edt/external/src/ExternalDataProcessors/ТестоваяВнешняяОбработка/` (.mdo + подкаталоги)
    - Designer Roles: `ext/designer/mdclasses/src/cf/Roles/Роль1/Ext/Rights.xml`
    - Designer конфигурация: `ext/designer/mdclasses/src/cf/` (полный набор объектов)
- **Версия mdclasses**: последний релиз (на момент написания — смотреть https://github.com/1c-syntax/mdclasses/releases)
- **Стек mdclasses**: Java 17, Gradle (Kotlin DSL), Lombok, XStream 1.4.21, JUnit 5, AssertJ

### Приоритет 2: Nikolay-Shirokov/cc-1c-skills

- **Репозиторий**: https://github.com/Nikolay-Shirokov/cc-1c-skills (PowerShell, 27 навыков)
- **Что берём**: JSON DSL спецификации и документацию XML-формата (реверс-инженернута из выгрузок конфигуратора)
- **Ключевые документы** (читать из репозитория при реализации каждой фазы):
  - `docs/1c-xml-format-spec.md` — формат XML внешней обработки (Designer), namespaces, структура каталогов, UUID, BOM
  - `docs/1c-form-spec.md` — XML-формат Form.xml (элементы, реквизиты, команды, события)
  - `docs/form-dsl-spec.md` — JSON DSL для форм
  - `docs/form-guide.md` — руководство по созданию форм
  - `docs/form-patterns.md` — паттерны форм (диалог загрузки, мастер, список с фильтром)
  - `docs/1c-role-spec.md` — XML-формат Rights.xml
  - `docs/role-dsl-spec.md` — JSON DSL для ролей
  - `docs/role-guide.md` — руководство по созданию ролей
  - `docs/1c-spreadsheet-spec.md` — XML-формат табличного документа (MXL)
  - `docs/mxl-dsl-spec.md` — JSON DSL для MXL
  - `docs/mxl-guide.md` — руководство по MXL
  - `docs/1c-dcs-spec.md` — XML-формат DataCompositionSchema (СКД)
  - `docs/skd-dsl-spec.md` — JSON DSL для СКД
  - `docs/skd-guide.md` — руководство по СКД
  - `docs/epf-guide.md` — руководство по EPF
  - `docs/build-spec.md` — сборка EPF через 1cv8.exe/ibcmd
- **При конфликте с mdclasses** — mdclasses побеждает (более проверенный источник)

### Протокол сверки (для каждого Writer)

1. Прочитать DSL-спеку Широкова (`docs/*-dsl-spec.md`)
2. Прочитать XML-спеку Широкова (`docs/1c-*-spec.md`)
3. Прочитать соответствующую Java-модель из mdclasses (enum-ы, record-ы, интерфейсы)
4. Прочитать тестовую фикстуру XML из mdclasses — эталонный вывод
5. Если расхождение — записать в комментарий Java-кода: `// NOTE: mdclasses uses X, Shirokov uses Y. Following mdclasses.`

---

## Архитектура модуля

### Поток данных

```
Agent (Cursor/Claude)
    |
    | пишет JSON по навыку (form-dsl.md и т.д.)
    v
JSON DSL файл (input.json)
    |
    | java -jar xml-gen.jar <command> --format <designer|edt> input.json output_dir/
    v
+-------------------+
| CLI (Main.java)   |
| Commands.java     |
+-------------------+
    |
    | Jackson: JSON -> DSL POJO
    v
+-------------------+
| DSL Models        |
| FormDsl.java      |
| EpfDsl.java       |
| RoleDsl.java      |
| MxlDsl.java       |
| SkdDsl.java       |
+-------------------+
    |
    | нормализация, defaults, разрешение типов
    v
+-------------------+
| Model Layer       |  <-- использует enum-ы и модели из mdclasses
| FormModel.java    |
| TypeResolver.java |
| IdGenerator.java  |
| UuidGenerator.java|
+-------------------+
    |
    | XMLStreamWriter
    v
+-------------------+
| Writers           |  <-- ядро: генерация XML
| XmlWriter.java    |
| FormWriter.java   |
| EpfWriter.java    |
| RoleWriter.java   |
| MxlWriter.java    |
| DcsWriter.java    |
+-------------------+
    |
    | выбор структуры каталогов
    v
+-------------------+
| Format Layer      |
| OutputFormat.java  |  enum: DESIGNER, EDT
| DesignerLayout.java|  структура каталогов Designer
| EdtLayout.java    |  структура каталогов EDT (.mdo)
+-------------------+
    |
    v
XML файлы + структура каталогов (output_dir/)
```

### Структура Gradle-проекта

```
tools/xml-gen/
  build.gradle.kts                 # Зависимости: mdclasses, jackson, lombok, junit5, shadow
  settings.gradle.kts              # rootProject.name = "xml-gen"
  gradle/wrapper/                  # Gradle wrapper (8.x+)
  src/
    main/
      java/io/github/onec/xmlgen/
        cli/
          Main.java                # Entry point: парсинг args, dispatch
          Commands.java            # enum команд: form, epf, role, mxl, skd + subcommands
        dsl/
          FormDsl.java             # Jackson POJO для JSON DSL формы
          EpfDsl.java              # Jackson POJO для JSON DSL EPF
          RoleDsl.java             # Jackson POJO для JSON DSL роли
          MxlDsl.java              # Jackson POJO для JSON DSL MXL
          SkdDsl.java              # Jackson POJO для JSON DSL SKD
        model/
          FormModel.java           # Нормализованная модель формы (из DSL + defaults)
          TypeResolver.java        # DSL типы -> XML типы (string(100) -> xs:string + StringQualifiers)
          IdGenerator.java         # Авто-инкремент ID элементов формы (thread-safe counter)
          UuidGenerator.java       # UUID v4 генератор + пары UUID для InternalInfo
        writer/
          XmlWriter.java           # Базовый класс: UTF-8 BOM, namespace registry, indent, XMLStreamWriter wrapper
          FormWriter.java          # Form.xml генерация (Designer + EDT)
          EpfWriter.java           # EPF XML генерация + создание структуры каталогов
          RoleWriter.java          # Rights.xml + Role.xml генерация
          MxlWriter.java           # SpreadsheetDocument XML генерация
          DcsWriter.java           # DataCompositionSchema XML генерация
        format/
          OutputFormat.java        # enum: DESIGNER, EDT
          DesignerLayout.java      # Создание структуры каталогов формата Designer
          EdtLayout.java           # Создание структуры каталогов формата EDT
    test/
      java/io/github/onec/xmlgen/
        writer/
          EpfWriterTest.java       # Roundtrip-тест EPF
          RoleWriterTest.java      # Roundtrip-тест Role
          FormWriterTest.java      # Roundtrip-тест Form
          MxlWriterTest.java       # Roundtrip-тест MXL
          DcsWriterTest.java       # Roundtrip-тест SKD
        model/
          TypeResolverTest.java    # Тесты разрешения типов
      resources/
        fixtures/                  # Копии/симлинки тестовых фикстур mdclasses
        golden/                    # Ожидаемые результаты наших генераторов
        dsl/                       # Примеры JSON DSL для тестов
```

### CLI-интерфейс

```bash
# EPF
java -jar xml-gen.jar epf init --format designer --name МояОбработка output/
java -jar xml-gen.jar epf add-form --format designer --epf-dir output/ form.json
java -jar xml-gen.jar epf add-template --format designer --epf-dir output/ --type spreadsheet template.json

# Form
java -jar xml-gen.jar form compile --format designer form.json output/

# Role
java -jar xml-gen.jar role compile --format designer role.json output/

# MXL
java -jar xml-gen.jar mxl compile mxl.json output/Template.xml

# SKD
java -jar xml-gen.jar skd compile skd.json output/DataCompositionSchema.xml

# Общие флаги
--format designer|edt    # Формат вывода (default: designer)
--verbose                # Подробный вывод
--validate               # Только валидация JSON DSL (без генерации)
```

### Навыки фреймворка (markdown)

Навыки — это markdown-документы в `framework/skills/xml-generation/`, которые обучают AI-агента DSL-формату. Агент читает навык, пишет JSON DSL, вызывает JAR.

```
framework/skills/xml-generation/
  xml-generation.md     # Обзорный навык: что такое xml-gen, как использовать, ссылки на DSL-навыки
  form-dsl.md           # JSON DSL для форм: справочник элементов, атрибутов, команд, паттерны
  epf-full/             # EPF: создание, добавление форм/макетов + шаблоны объектов + BSP-регистрация
  mxl-dsl.md            # JSON DSL для MXL: fonts, styles, areas, cells, паттерны печатных форм
  role-dsl.md           # JSON DSL для ролей: права, пресеты (view/edit/full), RLS
  skd-dsl.md            # JSON DSL для СКД: dataSets, fields, query, параметры, паттерны
```

---

## Покрытие навыков Широкова (27 -> наш фреймворк)

| # | Навык Широкова | Наш аналог | Реализация |
|---|----------------|------------|------------|
| 1 | `form-compile` | `FormWriter.java` | Java: JSON DSL -> Form.xml |
| 2 | `form-validate` | `--validate` флаг CLI | Java: проверка JSON DSL |
| 3 | `form-add` | `form-dsl.md` секция | Инструкции в навыке |
| 4 | `form-edit` | `form-dsl.md` секция | Инструкции в навыке |
| 5 | `form-info` | `form-dsl.md` секция | DSL-справочник |
| 6 | `form-patterns` | `form-dsl.md` секция | Паттерны форм |
| 7 | `epf-init` | `EpfWriter.java` (init) | Java: scaffold EPF |
| 8 | `epf-add-form` | `EpfWriter.java` (add-form) | Java: добавить форму |
| 9 | `epf-add-template` | `EpfWriter.java` (add-template) | Java: добавить макет |
| 10 | `epf-add-help` | `EpfWriter.java` (add-template --type html) | Покрыто макетом HTMLDocument |
| 11 | `epf-remove-form` | `epf-full/references/epf-base.md` | Текстовые инструкции |
| 12 | `epf-remove-template` | `epf-full/references/epf-base.md` | Текстовые инструкции |
| 13 | `epf-build` | `epf-full/references/epf-base.md` секция "Сборка" | Инструкции 1cv8.exe/ibcmd |
| 14 | `epf-dump` | `epf-full/references/epf-base.md` секция "Выгрузка" | Инструкции 1cv8.exe |
| 15 | `epf-bsp-init` | `epf-full/references/epf-bsp.md` §1 | Паттерн модуля |
| 16 | `epf-bsp-add-command` | `epf-full/references/epf-bsp.md` §2 | Паттерн |
| 17 | `mxl-compile` | `MxlWriter.java` | Java: JSON DSL -> MXL XML |
| 18 | `mxl-validate` | `--validate` флаг CLI | Java: проверка JSON DSL |
| 19 | `mxl-decompile` | `mxl-dsl.md` секция | Инструкции разбора XML |
| 20 | `mxl-info` | `mxl-dsl.md` секция | DSL-справочник |
| 21 | `role-compile` | `RoleWriter.java` | Java: JSON DSL -> Rights.xml |
| 22 | `role-validate` | `--validate` флаг CLI | Java: проверка JSON DSL |
| 23 | `role-info` | `role-dsl.md` секция | DSL-справочник |
| 24 | `skd-compile` | `DcsWriter.java` | Java: JSON DSL -> DCS XML |
| 25 | `skd-validate` | `--validate` флаг CLI | Java: проверка JSON DSL |
| 26 | `skd-edit` | `skd-dsl.md` секция | Инструкции |
| 27 | `skd-info` | `skd-dsl.md` секция | DSL-справочник |

Не портируем: `img-grid` (генерация grid-изображений, не связан с XML).

---

## Два XML-формата: Designer vs EDT

### Designer (формат Конфигуратора)

- Один XML-файл на объект + подкаталоги с вложенными файлами
- UUID-пары в `<InternalInfo>` (ObjectId + TypeId + ValueId)
- Namespace: `http://v8.1c.ru/8.3/MDClasses` (метаданные), `http://v8.1c.ru/8.3/xcf/logform` (формы)
- BOM (EF BB BF) в метаданных, без BOM в Form.xml и .bsl
- Сложнее, отличия между версиями платформы

**Пример структуры EPF (Designer):**
```
МояОбработка.xml               # Корневой файл
МояОбработка/
  Ext/
    ObjectModule.bsl
  Forms/
    ОсновнаяФорма.xml
    ОсновнаяФорма/
      Ext/
        Form.xml
        Form/
          Module.bsl
  Templates/
    Макет.xml
    Макет/
      Ext/
        Template.xml
```

### EDT (формат 1C:Enterprise Development Tools)

- `.mdo` файлы (XML с другой схемой)
- Структура каталогов: `src/ExternalDataProcessors/<Name>/`
- Проще, стабильнее между релизами
- Рекомендован мейнтейнером mdclasses для write-операций

**Пример структуры EPF (EDT):**
```
src/ExternalDataProcessors/МояОбработка/
  МояОбработка.mdo              # Описание объекта
  ObjectModule.bsl
  Forms/
    ОсновнаяФорма/
      Form.form                 # Описание формы
      Module.bsl
  Templates/
    Макет/
      Template.xml
```

### Определение формата

MCP-сервер `alkoleft/mcp-onec-test-runner` имеет конфигурацию (`application-yaxunit.yml`) с параметром:
```yaml
app:
  format: DESIGNER|EDT
```
Агент может определить формат через MCP-tool `yaxunit_get_configuration`. Если формат неизвестен — default: DESIGNER.

---

## XML Namespaces (Designer формат)

### Метаданные (корневой XML, Forms/*.xml, Templates/*.xml)

```xml
<MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses"
  xmlns:app="http://v8.1c.ru/8.2/managed-application/core"
  xmlns:cfg="http://v8.1c.ru/8.1/data/enterprise/current-config"
  xmlns:cmi="http://v8.1c.ru/8.2/managed-application/cmi"
  xmlns:ent="http://v8.1c.ru/8.1/data/enterprise"
  xmlns:lf="http://v8.1c.ru/8.2/managed-application/logform"
  xmlns:style="http://v8.1c.ru/8.1/data/ui/style"
  xmlns:sys="http://v8.1c.ru/8.1/data/ui/fonts/system"
  xmlns:v8="http://v8.1c.ru/8.1/data/core"
  xmlns:v8ui="http://v8.1c.ru/8.1/data/ui"
  xmlns:web="http://v8.1c.ru/8.1/data/ui/colors/web"
  xmlns:win="http://v8.1c.ru/8.1/data/ui/colors/windows"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  version="2.17">
```

### Form.xml (описание формы)

```xml
<Form xmlns="http://v8.1c.ru/8.3/xcf/logform"
  xmlns:app="http://v8.1c.ru/8.2/managed-application/core"
  xmlns:cfg="http://v8.1c.ru/8.1/data/enterprise/current-config"
  xmlns:cmi="http://v8.1c.ru/8.2/managed-application/cmi"
  xmlns:ent="http://v8.1c.ru/8.1/data/enterprise"
  xmlns:style="http://v8.1c.ru/8.1/data/ui/style"
  xmlns:sys="http://v8.1c.ru/8.1/data/ui/fonts/system"
  xmlns:v8="http://v8.1c.ru/8.1/data/core"
  xmlns:v8ui="http://v8.1c.ru/8.1/data/ui"
  xmlns:web="http://v8.1c.ru/8.1/data/ui/colors/web"
  xmlns:win="http://v8.1c.ru/8.1/data/ui/colors/windows"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
```

---

## Фазы реализации

### Phase 0: Инфраструктура (оценка: ~500 строк Java + build config)

**Задачи:**
- `build.gradle.kts`: Java 17, зависимости (mdclasses latest, jackson-databind, lombok, junit5, assertj, shadow plugin для fat JAR)
- `settings.gradle.kts`: `rootProject.name = "xml-gen"`
- `XmlWriter.java`: базовый класс — XMLStreamWriter wrapper, UTF-8 BOM для метаданных, namespace registry, методы indent/writeElement/writeTextElement
- `OutputFormat.java`: enum DESIGNER / EDT
- `DesignerLayout.java` / `EdtLayout.java`: создание структуры каталогов (mkdir + path resolution)
- `TypeResolver.java`: DSL типы -> XML типы (`string(100)` -> `xs:string` + `<v8:StringQualifiers><v8:Length>100</v8:Length><v8:AllowedLength>Variable</v8:AllowedLength></v8:StringQualifiers>`)
- `UuidGenerator.java`: UUID v4, пары UUID для InternalInfo
- `IdGenerator.java`: thread-safe auto-increment counter
- `Main.java` + `Commands.java`: CLI каркас (arg parsing, dispatch)
- Smoke-тест: собрать fat JAR, `java -jar xml-gen.jar --help`

**Критерий завершения:** `./gradlew build` проходит, fat JAR собирается, `java -jar xml-gen.jar --help` выводит usage.

### Phase 1: EPF (оценка: ~600 строк Java + ~300 строк Markdown)

**Источники для чтения:**
- Широков: `docs/1c-xml-format-spec.md`, `docs/epf-guide.md`, `docs/build-spec.md`
- mdclasses: `ExternalDataProcessor.java`, фикстуры `ext/designer/external/src/epf/`, `ext/edt/external/src/ExternalDataProcessors/`

**Задачи:**
- `EpfDsl.java`: Jackson POJO — name, synonym, defaultForm, attributes[], tabularSections[], forms[], templates[]
- `EpfWriter.java`:
  - `init`: scaffold корневого XML + ObjectModule.bsl + структура каталогов
  - `add-form`: модификация корневого XML (добавить в ChildObjects) + создание Forms/<Name>.xml + Form.xml scaffold
  - `add-template`: модификация корневого XML + создание Templates/<Name>.xml + тело макета
  - Оба формата: Designer (корневой XML с UUID-парами) и EDT (.mdo)
- `EpfWriterTest.java`: roundtrip по фикстуре `ТестоваяВнешняяОбработка`
- `epf-full/SKILL.md`: навык фреймворка (слияние #6)

**Критерий завершения:** `java -jar xml-gen.jar epf init --name ТестОбработка output/` генерирует валидный XML, загружаемый в конфигуратор 1С. Roundtrip-тест проходит.

### Phase 2: Role/Rights (оценка: ~500 строк Java + ~200 строк Markdown)

**Источники для чтения:**
- Широков: `docs/1c-role-spec.md`, `docs/role-dsl-spec.md`, `docs/role-guide.md`
- mdclasses: `RoleRight.java` (74 права), `RoleData.java`, `Role.java`, фикстура `Roles/Роль1/Ext/Rights.xml`

**Задачи:**
- `RoleDsl.java`: Jackson POJO — name, setForNewObjects, setForAttributesByDefault, rights[] (каждый: object, rights[{name, value}]), rls[], presets
- `RoleWriter.java`:
  - Генерация Role.xml (метаданные роли) + Rights.xml (права)
  - Валидация имён прав по `RoleRight` enum из mdclasses
  - Пресеты: `view` (Read+View), `edit` (Read+View+Insert+Update+Delete+Edit+InteractiveInsert+InteractiveDelete+InteractiveSetDeletionMark), `full` (все права объекта)
  - RLS-шаблоны
  - Оба формата
- `RoleWriterTest.java`: roundtrip по фикстуре `Роль1`
- `role-dsl.md`: навык фреймворка

**Критерий завершения:** генерирует Rights.xml, побитово совпадающий с фикстурой mdclasses. Roundtrip-тест проходит.

### Phase 3: Form (оценка: ~1500 строк Java + ~500 строк Markdown)

**Самая сложная фаза.** Валерий подтвердил: "прям беда — формы... такое количество объектов, что просто страх".

**Источники для чтения:**
- Широков: `docs/1c-form-spec.md`, `docs/form-dsl-spec.md`, `docs/form-guide.md`, `docs/form-patterns.md`
- mdclasses: `FormElementType.java` (42 типа), `FormItem.java`, `FormAttribute.java`, `FormHandler.java`, `SimpleFormItem.java`, `ManagedFormData.java`, фикстуры Form.xml

**Задачи:**
- `FormDsl.java`: Jackson POJO — title, handlers[], elements[] (дерево), attributes[], commands[], parameters[]
- `FormModel.java`: нормализованная модель — добавляет обязательные элементы (ExtendedTooltip, ContextMenu, AutoCommandBar), расставляет ID, разрешает типы
- `FormWriter.java`:
  - Секции: `<AutoCommandBar>`, `<items>` (дерево элементов), `<attributes>`, `<commands>`
  - Элементы: фокус на топ-15 реально используемых из 42 в FormElementType:
    - UsualGroup, InputField, Button, Table, LabelDecoration, CheckBoxField, LabelField, CommandBar, Pages/Page, Popup, RadioButtonField, HTMLDocumentField, SpreadsheetDocumentField, PictureDecoration, FormField
  - Каждый элемент автоматически получает ExtendedTooltip (обязателен)
  - Таблицы/деревья: ContextMenu (обязателен), колонки с DataPath
  - Оба формата
- `FormWriterTest.java`: roundtrip
- `form-dsl.md`: навык — DSL-справочник, паттерны (диалог загрузки, мастер, список с фильтром)

**Критерий завершения:** генерирует Form.xml с группами, полями ввода, таблицей, кнопками — форма открывается в конфигураторе без ошибок.

### Phase 4: MXL (оценка: ~800 строк Java + ~250 строк Markdown)

**Источники для чтения:**
- Широков: `docs/1c-spreadsheet-spec.md`, `docs/mxl-dsl-spec.md`, `docs/mxl-guide.md`
- mdclasses: `TemplateType.java`, `TemplateData.java`, фикстуры Templates/

**Задачи:**
- `MxlDsl.java`: fonts, styles, areas, cells, rowStyle, autoFill, rowspan
- `MxlWriter.java`: SpreadsheetDocument XML генерация
- `MxlWriterTest.java`
- `mxl-dsl.md`: навык — паттерны печатных форм

### Phase 5: SKD (оценка: ~700 строк Java + ~250 строк Markdown)

**Источники для чтения:**
- Широков: `docs/1c-dcs-spec.md`, `docs/skd-dsl-spec.md`, `docs/skd-guide.md`
- mdclasses: `DataCompositionSchema.java`, `DataSetType.java`, `QuerySource.java`, фикстуры CommonTemplates/

**Задачи:**
- `SkdDsl.java`: dataSets[], fields[], parameters[], settings
- `DcsWriter.java`: DataCompositionSchema XML генерация
- `DcsWriterTest.java`
- `skd-dsl.md`: навык — паттерны СКД

### Phase 6: Интеграция (оценка: ~100 строк diff)

- `xml-generation.md` — обзорный навык: что такое xml-gen, как использовать, ссылки на DSL-навыки
- Обновить `framework/capabilities/registry.yaml` — добавить xml-generation capabilities
- Обновить `docs/SPEC-001-framework-architecture.md` — отметить requirement как выполненный
- Обновить `README.md` — добавить xml-generation в структуру
- Добавить xml-generation в `skills` frontmatter агентов `developer.md` и `formatter.md`
- Добавить инструкцию сборки JAR в `docs/install-guide.md`

---

## Критические детали реализации

> Этот раздел содержит конкретные значения и правила, без которых сгенерированный XML не будет загружаться в 1С. Другой агент должен знать их ДО начала реализации.

### Gradle-зависимость mdclasses

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.1c-syntax:mdclasses:0.17.4")  // проверить последнюю версию на Maven Central
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.+")

    compileOnly("org.projectlombok:lombok:1.18.+")
    annotationProcessor("org.projectlombok:lombok:1.18.+")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.+")
    testImplementation("org.assertj:assertj-core:3.25.+")
}
```

### Константы

| Константа | Значение | Где используется |
|-----------|----------|-----------------|
| ClassId ExternalDataProcessor | `c3831ec8-d8d5-4f93-8a22-f9bfae07327f` | Корневой XML EPF, секция `<InternalInfo>` |
| XML version attribute | `2.17` | `<MetaDataObject ... version="2.17">` |
| XML encoding declaration | `<?xml version="1.0" encoding="UTF-8"?>` | Первая строка каждого XML |
| FormType | `Managed` | `<FormType>Managed</FormType>` в метаданных формы |
| AutoCommandBar id | `-1` | Зарезервированный ID для `<AutoCommandBar>` формы |

### Правила BOM (Byte Order Mark)

| Тип файла | BOM (EF BB BF) | Пример |
|-----------|----------------|--------|
| Корневой XML EPF (`*.xml`) | **ДА** | `МояОбработка.xml` |
| Метаданные формы (`Forms/*.xml`) | **ДА** | `Forms/Форма.xml` |
| Метаданные макета (`Templates/*.xml`) | **ДА** | `Templates/Макет.xml` |
| Описание формы (`Form.xml`) | **НЕТ** | `Forms/Форма/Ext/Form.xml` |
| Модули BSL (`*.bsl`) | **НЕТ** | `Module.bsl`, `ObjectModule.bsl` |
| Тело макета (`Template.*`) | Зависит от типа | `.xml` — нет, `.html` — нет |

> Конфигуратор принимает файлы и без BOM, но выгрузка всегда генерирует с BOM для метаданных. Рекомендация: следовать конвенции конфигуратора.

### Система типов DSL -> XML

```
DSL тип           XML тип (v8:Type)          Квалификаторы
-----------       ----------------------     ------------------
string             xs:string                  StringQualifiers: Length=0, AllowedLength=Variable
string(100)        xs:string                  StringQualifiers: Length=100, AllowedLength=Variable
string!(50)        xs:string                  StringQualifiers: Length=50, AllowedLength=Fixed
number(10,2)       xs:decimal                 NumberQualifiers: Digits=10, FractionDigits=2, AllowedSign=Any
number+(10,2)      xs:decimal                 NumberQualifiers: Digits=10, FractionDigits=2, AllowedSign=Nonnegative
boolean            xs:boolean                 —
date               xs:dateTime                DateQualifiers: DateFractions=Date
time               xs:dateTime                DateQualifiers: DateFractions=Time
datetime           xs:dateTime                DateQualifiers: DateFractions=DateTime
uuid               v8:UUID                    —
valuetable         v8:ValueTable              Обязателен для коллекций с колонками
valuetree          v8:ValueTree               Обязателен для деревьев с колонками
spreadsheet        mxl:SpreadsheetDocument     xmlns:mxl="http://v8.1c.ru/8.2/data/spreadsheet"
ref:Catalog.Name   cfg:CatalogRef.Name        —
object:EPF.Name    cfg:ExternalDataProcessorObject.Name  SavedData=true (основной реквизит)
```

### Обязательные дочерние элементы форм

Эти правила — частый источник ошибок. Без них форма не загрузится или будет работать некорректно.

| Правило | Описание |
|---------|----------|
| **ExtendedTooltip** | КАЖДЫЙ элемент формы ОБЯЗАН иметь дочерний `<ExtendedTooltip>`. Имя: `<ИмяРодителя>ExtendedTooltip`. Минимум: `<ExtendedTooltip name="..." id="N"/>` |
| **ContextMenu** | Элементы InputField, CheckBoxField, LabelField, FormTree, Table, HTMLDocumentField ОБЯЗАНЫ иметь `<ContextMenu>`. Имя: `<ИмяРодителя>ContextMenu` |
| **AutoCommandBar** | Форма ОБЯЗАНА иметь `<AutoCommandBar>` с `id="-1"` как первый элемент верхнего уровня |
| **Тип коллекций** | Реквизиты формы типа ValueTable/ValueTree ОБЯЗАНЫ иметь явный `<v8:Type>`. Без него — ошибка "Неверный путь к данным" |
| **ID уникальность** | ID уникальны в пределах секции (элементы, реквизиты, команды нумеруются НЕЗАВИСИМО) |
| **DataPath колонок** | Формат: `<ИмяРеквизита>.<ИмяКолонки>` для реквизитов формы, `Объект.<ИмяТЧ>.<ИмяРеквизита>` для табличных частей объекта |

### Ключевые пути в исходниках mdclasses

Полные пути к Java-классам, на которые нужно ориентироваться (от корня репозитория `1c-syntax/mdclasses`):

```
# Enum-ы
src/main/java/com/github/_1c_syntax/mdclasses/mdo/support/FormElementType.java
src/main/java/com/github/_1c_syntax/mdclasses/mdo/support/RoleRight.java
src/main/java/com/github/_1c_syntax/mdclasses/mdo/support/DataSetType.java
src/main/java/com/github/_1c_syntax/mdclasses/mdo/support/TemplateType.java

# Модели форм
src/main/java/com/github/_1c_syntax/mdclasses/mdo/storage/ManagedFormData.java
src/main/java/com/github/_1c_syntax/mdclasses/mdo/storage/form/FormItem.java
src/main/java/com/github/_1c_syntax/mdclasses/mdo/storage/form/FormAttribute.java
src/main/java/com/github/_1c_syntax/mdclasses/mdo/storage/form/FormHandler.java

# Модель EPF
src/main/java/com/github/_1c_syntax/mdclasses/mdclasses/ExternalDataProcessor.java

# Модель ролей
src/main/java/com/github/_1c_syntax/mdclasses/mdo/Role.java
src/main/java/com/github/_1c_syntax/mdclasses/mdo/storage/RoleData.java

# Модель СКД
src/main/java/com/github/_1c_syntax/mdclasses/mdo/storage/DataCompositionSchema.java

# Тестовые фикстуры
src/test/resources/ext/designer/external/src/epf/
src/test/resources/ext/edt/external/src/ExternalDataProcessors/
src/test/resources/ext/designer/mdclasses/src/cf/Roles/Роль1/Ext/Rights.xml
```

> ВАЖНО: Пакет Java — `com.github._1c_syntax.mdclasses`, НЕ `io.github.1c_syntax`. Maven-координаты (`io.github.1c-syntax:mdclasses`) отличаются от пакета Java (`com.github._1c_syntax.mdclasses`).

---

## Ключевые технические решения

| Решение | Обоснование |
|---------|-------------|
| `XMLStreamWriter`, не XStream | Полный контроль над порядком элементов, namespace-ами, отступами. XStream хорош для десериализации, но для генерации нужна точность до байта |
| mdclasses как dependency | Enum-ы и модели не копируем, а берём из JAR. При обновлении платформы — bumping версии mdclasses |
| Промежуточная модель (DSL -> Model -> XML) | Модель нормализует данные, добавляет defaults (ExtendedTooltip, ContextMenu), разрешает типы. Writer работает с моделью, не с сырым DSL |
| Fat JAR (shadow plugin) | Единственный артефакт для распространения (~15-20MB с mdclasses). Пользователю нужен только JDK 17+ |
| Roundtrip-тесты обязательны | Без них open-source передача невозможна. Формат: read mdclasses fixture -> parse -> write -> compare |
| Java 17 + Gradle | Совместимость с mdclasses и стеком MCP-серверов (yaxunit-runner тоже Java 17 + Gradle) |
| Jackson для JSON | Стандарт в Java-экосистеме, хорошо работает с Lombok @Value/@Builder |

---

## Протокол качества (для open-source передачи)

1. **Roundtrip-тесты**: для каждого Writer — читаем фикстуру mdclasses через стандартный XML parser, генерируем свой XML, нормализуем оба (убираем whitespace differences), сравниваем
2. **Enum-синхронизация**: `FormElementType`, `RoleRight` берём из mdclasses dependency — при обновлении mdclasses тесты сломаются если формат изменился (это хорошо — значит нужна адаптация)
3. **Оба формата покрыты тестами**: каждый Writer тестируется и для Designer, и для EDT
4. **Валидация DSL**: каждый Writer имеет режим `--validate` — проверяет JSON DSL без генерации XML
5. **Документация**: JavaDoc на публичных классах, README в `tools/xml-gen/`
6. **Код-стиль**: совместим с mdclasses (Lombok, Java 17 features, Gradle)

---

## Оценка объёма

| Фаза | Java | Markdown | Что |
|------|------|----------|-----|
| Phase 0 | ~500 | — | Инфраструктура, CLI, base classes |
| Phase 1 | ~600 | ~300 | EPF |
| Phase 2 | ~500 | ~200 | Role/Rights |
| Phase 3 | ~1500 | ~500 | Form (самая большая) |
| Phase 4 | ~800 | ~250 | MXL |
| Phase 5 | ~700 | ~250 | SKD |
| Phase 6 | — | ~100 | Интеграция |
| **Итого** | **~4600** | **~1600** | |

---

## Контекст фреймворка (для Phase 6: интеграция)

Файлы, которые потребуется модифицировать на этапе интеграции:

| Файл | Что добавить |
|------|-------------|
| `framework/capabilities/registry.yaml` | Строки с xml-generation capabilities |
| `docs/SPEC-001-framework-architecture.md` | Отметить requirement #4 как выполненный |
| `README.md` | Добавить `xml-generation` в структуру каталогов и таблицу навыков |
| `framework/subagents/developer.md` | Добавить `xml-generation/*` в `skills` frontmatter |
| `framework/subagents/formatter.md` | Добавить `xml-generation/*` в `skills` frontmatter |
| `docs/install-guide.md` | Секция: сборка JAR (`./gradlew shadowJar`), путь к артефакту |

### Формат agent frontmatter (пример)

```yaml
---
name: developer
description: BSL-разработчик
model: sonnet
skills:
  - bsl-practices/coding-standards
  - tool-usage/syntax-checking
  - tool-usage/search-before-write
  - xml-generation                   # единый toolkit (обзорный, включает все под-skills)
---
```

### Формат registry.yaml (пример строки)

```yaml
xml_generate_form: { server: local, tool: "java -jar xml-gen.jar form compile" }
```

---

## Ссылки

- mdclasses: https://github.com/1c-syntax/mdclasses
- mdclasses docs: https://1c-syntax.github.io/mdclasses/
- mdclasses JavaDoc: https://1c-syntax.github.io/mdclasses/javadoc/index.html
- Nikolay-Shirokov/cc-1c-skills: https://github.com/Nikolay-Shirokov/cc-1c-skills
- BSL Language Server: https://github.com/1c-syntax/bsl-language-server
- MCP test-runner (yaxunit): https://github.com/alkoleft/mcp-onec-test-runner
- Фреймворк README: [../README.md](../README.md)
- Архитектура фреймворка: [SPEC-001-framework-architecture.md](SPEC-001-framework-architecture.md)
