# xml-gen

Java CLI для генерации, редактирования, инспекции и валидации XML-артефактов 1С из компактных JSON DSL и точечных команд.

Главная роль инструмента — дать AI-агентам и скриптам управляемый способ менять выгрузку 1С без ручной правки больших XML-файлов.

## Возможности

- Генерация конфигурации, EPF/ERF, объектов метаданных, форм, ролей, MXL, SKD, подсистем, шаблонов и справки.
- Точечные мутации существующих XML: формы, роли, SKD, Configuration.xml, подсистемы, CommandInterface, расширения, шаблоны.
- Инспекция и декомпиляция: `info` для основных типов, `form decompile`, `mxl decompile`, SKD query/fields/variant modes.
- Валидация XML собственными валидаторами `xml-gen`: structure/semantic checks, text/json output, autodetect по root element.
- Работа с расширениями CFE: `init`, `borrow`, `diff`, `patch-method`, `validate`.
- Guard-логика для конфигураций на поддержке поставщика: `support check|info`.
- Oracle-режимы для проверки поведения на каноническом корпусе XML и майнинга структурных правил.
- Byte-safe замена текста: сохраняет BOM и окончания строк, поддерживает dry-run/backup/validate.
- Форматы вывода Designer и частично EDT там, где это реализовано writers/layout.

## Требования

- JDK 17+
- Gradle wrapper из каталога `tools/xml-gen/`

## Сборка

```bash
cd tools/xml-gen
./gradlew build
```

Fat JAR создаётся в `build/libs/xml-gen-0.1.1-SNAPSHOT.jar`.

## Быстрый Старт

```bash
# Справка / версия
xml-gen --help
xml-gen --version

# Новая выгрузка конфигурации
xml-gen config init src/xml МояКонфигурация --compat Version8_3_24 --format-version 2.20

# Внешняя обработка / внешний отчёт
xml-gen epf init --format designer --name МояОбработка output/
xml-gen epf init --type report --with-skd --name МойОтчет output/

# Объект метаданных из JSON DSL с регистрацией в Configuration.xml
xml-gen meta compile catalog.json src/xml/

# Форма из JSON DSL или из метаобъекта
xml-gen form compile form.json Forms/Форма/Ext/Form.xml
xml-gen form compile --from-object --object Catalogs/Товары.xml Forms/Форма/Ext/Form.xml

# Валидация
xml-gen validate --type form Forms/Форма/Ext/Form.xml
xml-gen validate --output json --src-root src/xml src/xml/Configuration.xml
```

## Команды

### `config`

- `config init <outputDir> <name>` — создать `Configuration.xml`, `ConfigDumpInfo.xml`, язык.
- `config info <Configuration.xml> [--mode overview|brief|full]`.
- `config edit <Configuration.xml> --op <operation> --value <value>`:
  `modify-property`, `add-childObject`, `remove-childObject`, `add-defaultRole`,
  `remove-defaultRole`, `set-defaultRoles`.
- `config validate <Configuration.xml|configDir>`.

`config init` принимает `--synonym`, `--version`, `--vendor`, `--lang-name`, `--lang-code`,
`--compat <Version8_3_NN>`, `--format-version <2.NN>`.

### `meta`

- `meta compile <jsonPath> <configRoot>` — создать объект метаданных и зарегистрировать его в `Configuration.xml`.
- `meta edit <objectPath> --op <operation> --value <value>` — точечные операции.
- `meta edit <objectPath> --batch <file.json>` — пакет мутаций.
- `meta edit <objectPath> --op normalize-runtime-attributes` — нормализация реквизитов.
- `meta info <Object.xml> [--mode brief|overview|full]`.
- `meta validate <Object.xml>`.
- `meta remove <configDir> <Type.Name> [--dry-run] [--keep-files] [--force]`.

`meta compile` берёт версию формата из `<configRoot>/Configuration.xml`, пишет объект и связанные файлы вроде
`Ext/Predefined.xml`, затем добавляет объект в `ChildObjects`.

Поддерживаемые семейства операций `meta edit`: `add`, `remove`, `modify` для реквизитов, табличных частей,
измерений, ресурсов, значений перечислений, предопределённых элементов, колонок, свойств и связанных секций.

Пример:

```bash
xml-gen meta edit src/xml/Catalogs/Договоры.xml --op add-predefined \
  --value "Лизинг|Договор лизинга;;Субаренда"
```

### `epf`

- `epf init --name <name> [--type processor|report] [--with-skd] <outputDir>`.
- `epf add-form --epf <name> --name <formName> [--default] <outputDir>`.
- `epf add-template --epf <name> --name <templateName> --type <type> <outputDir>`.
- `epf add-attribute <file> --name <name> --type <type>`.
- `epf add-tabular-section <file> --name <name>`.
- `epf bsp-init <epfPath> --kind <kind> [--target <Type.Name>]`.
- `epf bsp-add-command <epfPath> --id <id> --label <label> [--type <type>] [--form <FormName>]`.

### `form`

- `form compile <form.json> <Form.xml>`.
- `form compile --from-object [--object <Object.xml>] [--preset <name>] <Form.xml>`.
- `form decompile <Form.xml> [output.json]`.
- `form info <Form.xml> [--limit N] [--offset N]`.
- `form add <objectXml> <formName> [--synonym <syn>] [--default]`.
- `form remove <objectXml> <formName>`.
- `form edit <Form.xml> --json <spec.json>` — JSON-спецификация мутаций с rollback для формы и BSL stub.
- `form add-attribute`, `form add-element`, `form add-command`, `form remove-element`, `form move-element`.

Мутации форм используют diff-gate: существующие ошибки формы не блокируют точечную правку, но новые ошибки после правки блокируются.

### `role`

- `role compile <role.json> <outputDir>`.
- `role info <Rights.xml> [--show-denied]`.
- `role add-object <Rights.xml> --name <Object> --rights Read,View`.
- `role add-right <Rights.xml> --object <Object> --name <Right> --value true|false`.

### `mxl`

- `mxl compile <mxl.json> <Template.xml>`.
- `mxl decompile <Template.xml> [output.json]`.
- `mxl info <Template.xml> [--with-text]`.

### `skd`

- `skd compile <skd.json> <Template.xml> [--include-base <dir>]`.
- `skd info <Template.xml> [--mode overview|query|fields|variant|...] [--raw] [--outfile <file>]`.
- `skd add-parameter`, `skd add-field`.
- `skd edit <SchemaPath> <operation> "<value>" [--dataSet <name>] [--variant <name>] [--no-selection]`.

`skd edit` поддерживает операции `add-field`, `modify-field`, `remove-field`, `set-field-role`,
`add-parameter`, `modify-parameter`, `remove-parameter`, `rename-parameter`, `reorder-parameters`,
`add-total`, `remove-total`, `modify-structure`, `set-query`, `patch-query`,
`clear-conditionalAppearance`. No-op операции не переписывают файл.

### `subsystem` и `interface`

- `subsystem compile <jsonPath> <outputDir> [--parent <Subsystem.xml>] [--no-stubs]`.
- `subsystem info <Subsystem.xml> [--mode brief|overview|full|tree|ci]`.
- `subsystem edit <Subsystem.xml> --op add-content|remove-content|add-child|remove-child|set-property --value <value>`.
- `subsystem validate <Subsystem.xml|subsystemDir>`.
- `interface edit <CommandInterface.xml> --op hide|show|place|set-order|set-subsystem-order|set-group-order --value <value>`.
- `interface validate <CommandInterface.xml>`.

`config edit` и `interface edit` сначала валидируют preview и не пишут файл, если правка добавляет новые ошибки.

### `template` и `help`

- `template add --object Type.Name --name T --type TemplateType [--synonym S] [--src dir] [--set-main-dcs] <configDir>`.
- `template remove --object Type.Name --name T [--src dir] <configDir>`.
- `template add-help --object Type.Name [--lang ru] [--src dir] <configDir>`.
- Legacy-форма: `template add|remove <objectXml> <templateName>`.
- `help add <objectXml> [--lang ru]`.

### `extension`

- `extension init <outputDir> <name> [--synonym S] [--prefix P] [--purpose P] [--compat V] [--version V] [--vendor V] [--config-path path] [--no-role]`.
- `extension validate <extensionPath>`.
- `extension borrow <extensionPath> <configPath> <objectSpec> [--borrow-main-attribute form|all]`.
- `extension diff <extensionPath> <configPath> [--mode A|B]`.
- `extension patch-method <extensionPath> --module <path> --method <name> --type Before|After|Instead|ModificationAndControl [--config <baseConfig>] [--context <ctx>] [--function]`.

### `validate`

Общая команда:

```bash
xml-gen validate [--type <type>] [--format designer|edt] [--level structure|semantic] \
  [--output text|json] [--src-root <path>] <file> [files...]
```

Поддерживаемые типы: `form`, `role`, `skd`, `mxl`, `epf`/`erf`, `meta`, `config`,
`extension`, `subsystem`, `interface`, `client-interface`, `template`, `xcf-body`,
`platform-xsd`. Если `--type` не указан, тип определяется по root element.

Важно: это собственные валидаторы `xml-gen`, а не XSD/JAXB-валидация платформенных схем.
`platform-xsd` — легкий слой подсказок по фактам, перенесенным из XSD-delta; он не заменяет
полную проверку платформенной XSD.

### `support`

- `support info <path> [--require editable|removed] [--output text|json]`.
- `support check <path> [--require editable|removed] [--output text|json]`.

Команда проверяет состояние поддержки поставщика и используется как guard перед мутациями.

### `edit`

```bash
xml-gen edit replace-text <file> --old "old" --new "new" [--all] [--dry-run] \
  [--backup] [--validate] [--encoding utf-8-sig|utf-8]
```

Можно указывать несколько пар `--old/--new`. Алиасы: `--search/--replace`.

### `oracle`

- `oracle mxl --source <src> --out <dir> [--mode dsl|cli|both] [--include-all]`.
- `oracle demo --source <src> --out <dir> [--threads N] [--include-mxl]`.
- `oracle predefined-data --source <src> --out <dir>`.
- `oracle exchange-plan-content --source <src> --out <dir>`.
- `oracle mine-rules --source <src> --out <dir> [--min-support N] [--digest-limit N] [--disposition rules.json]`.

Oracle-режимы нужны для регрессий на реальных выгрузках: сравнение канонических XML, классификация расхождений,
поиск структурных правил и пробелов покрытия.

### XSD coverage delta

В `scripts/xsd_coverage_delta.py` есть отдельный audit-helper для сравнения платформенных XSD из
`namespace-forest` с тем, что реально видно в исходниках `xml-gen`.

```bash
tools/xml-gen/scripts/xsd_coverage_delta.py \
  --forest /path/to/namespace-forest \
  --xmlgen-root tools/xml-gen \
  --version latest \
  --out-dir tools/xml-gen/build/xsd-coverage-delta
```

Опционально можно добавить произвольный корпус XML-выгрузок, чтобы отделить теоретические пробелы XSD
от форматов, которые реально встречаются в проектах:

```bash
tools/xml-gen/scripts/xsd_coverage_delta.py \
  --forest /path/to/namespace-forest \
  --xmlgen-root tools/xml-gen \
  --version latest \
  --corpus-root /path/to/src/xml \
  --out-dir tools/xml-gen/build/xsd-coverage-delta
```

Отчет показывает namespace delta, глобальные элементы/типы/enum-ы и required-атрибуты, которые не найдены
в литералах, валидаторах, writer-ах и oracle-коде `xml-gen`. При `--corpus-root` добавляется corpus delta:
что из XSD отсутствует в `xml-gen`, но наблюдается в реальных XML-файлах.

Выявленные XSD/corpus gaps перенесены в валидаторный слой:

- `client-interface` валидирует `Ext/ClientApplicationInterface.xml`.
- `platform-xsd` распознает XSD-only namespaces и CMI root `section`/`group`/`command`,
  включая required-атрибуты, найденные в `namespace-forest`.

## Архитектура

```text
JSON DSL / CLI operation
  -> DSL models / command parser
  -> model helpers / editors / writers
  -> XML files + directory layout
  -> validators / oracle reports
```

Основные пакеты:

- `cli/` — entry point и диспетчер команд.
- `dsl/` — Jackson POJO для JSON DSL.
- `writer/` — генерация XML и scaffold файлов.
- `editor/` — точечные мутации существующих XML и byte-safe операции.
- `validator/` — DOM-парсер, типовые валидаторы, text/json reporters.
- `info/` — инспекция и декомпиляция XML в краткие/полные представления.
- `form/` — JSON-мутации форм, генерация формы по объекту, presets.
- `oracle/` — поведенческие проверки на каноническом корпусе.
- `support/` — guard для объектов на поддержке поставщика.
- `model/` — резолвинг типов, путей метаданных, UUID/ID, служебные модели.
- `format/` — Designer/EDT layout.

## Тесты

```bash
cd tools/xml-gen
./gradlew test
```

В проекте 115 test-файлов: CLI-contract тесты, writer/editor/validator тесты, info/decompile тесты,
oracle тесты, support guard и round-trip проверки. Все тесты используют временные каталоги и не должны
оставлять следы в рабочем дереве.

## Зависимости

- `io.github.1c-syntax:mdclasses:0.17.4` — enum-ы и модели 1С.
- `io.github.1c-syntax:bsl-common-library:0.9.2` — типы и квалификаторы 1С.
- `jackson-databind` — JSON DSL.
- `lombok` — boilerplate в моделях.
- `junit5`, `assertj` — тесты.

## Exit Codes

| Code | Meaning | When |
|------|---------|------|
| `0` | Success | Операция выполнена без ошибок. |
| `1` | Business/domain error | Невалидный ввод, дубль, неизвестное право, отсутствующий объект, ошибка валидации. |
| `2` | JVM/infrastructure failure | Сбой JVM, OOM, отсутствующий jar и т.п. |

По умолчанию ошибки печатаются как `ERROR: <message>`. Для stack trace используйте `--debug`
в любом месте командной строки или `XML_GEN_DEBUG=1`.

## Ограничения

- `xml-gen` не генерирует JAXB-модели из XSD и не выполняет XSD-валидацию платформенных схем.
- Часть XML всё ещё создаётся/редактируется writer-ами и текстовыми splice-операциями; риск снижается
  локальными валидаторами, preview/diff-gate, rollback и oracle-тестами, но это не эквивалент полной
  проверки платформой 1С.
- EDT поддерживается неравномерно: перед использованием проверяйте конкретную команду и тесты.

## Лицензия

LGPL-3.0.
