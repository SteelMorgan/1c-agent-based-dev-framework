# xml-gen

Java-модуль для генерации XML метаданных 1С из JSON DSL.

## Возможности

- Генерация внешних обработок (EPF)
- Генерация управляемых форм
- Генерация ролей (Rights.xml)
- Генерация макетов табличных документов (MXL)
- Генерация схем компоновки данных (SKD)
- Побайтовая замена текста в XML (edit replace-text) — сохраняет BOM, bare LF, CRLF
- Поддержка форматов Designer и EDT

## Требования

- JDK 17+
- Gradle 8.5+ (wrapper включён)

## Сборка

```bash
./gradlew build
```

Fat JAR будет создан в `build/libs/xml-gen-0.1.1-SNAPSHOT.jar` (~5.6MB).

## Использование

```bash
# Справка / версия
xml-gen --help
xml-gen --version

# Создать EPF
xml-gen epf init --format designer --name МояОбработка output/

# Скомпилировать форму
xml-gen form compile --format designer form.json output/

# Скомпилировать роль
xml-gen role compile --format designer role.json output/
```

### meta compile / meta edit — объекты метаданных (TASK-171)

`meta compile <json> <configRoot>` создаёт объект метаданных и:

- генерирует объект в каноничном формате (namespace `http://v8.1c.ru/8.3/xcf/...`);
- берёт **версию формата** (`version=...`) из `<configRoot>/Configuration.xml` (а не хардкодит) —
  и для объекта, и для `Ext/Predefined.xml`;
- **регистрирует** объект в `<ChildObjects>` `Configuration.xml` (если конфиг найден рядом);
- для `CommonModule`/`ScheduledJob`/`EventSubscription` НЕ пишет лишние `InternalInfo`/`ChildObjects`.

Поддерживаемые ключи DSL (примеры):

```jsonc
// Справочник с предопределёнными элементами (Ext/Predefined.xml):
{ "type": "Catalog", "name": "бигДоговоры", "codeLength": 9,
  "predefinedItems": [
    { "name": "Аренда", "description": "Договор аренды" },   // code авто: 000000001
    { "name": "Поставка", "code": "000000010" },
    "Прочее"                                                  // строка = только имя
  ] }

// Перечисление со значениями (ключ values ИЛИ алиас enumValues):
{ "type": "Enum", "name": "бигСтатусы", "enumValues": [ {"name":"Новый"}, {"name":"Закрыт"} ] }
```

```bash
# Скомпилировать объект и зарегистрировать его в Configuration.xml
xml-gen meta compile catalog.json src/xml/

# Добавить предопределённые элементы в существующий справочник (Ext/Predefined.xml):
#   shorthand элемента: Имя[|Описание[|Код[|folder]]]; батч через ;;
xml-gen meta edit src/xml/Catalogs/бигДоговоры.xml --op add-predefined \
    --value "Лизинг|Договор лизинга;;Субаренда"
```

Поддерживают предопределённые: `Catalog`, `ChartOfCharacteristicTypes`,
`ChartOfAccounts`, `ChartOfCalculationTypes`.

`config init` принимает `--compat <Version8_3_NN>` и `--format-version <2.NN>`
для соответствия целевой платформе.

## Архитектура

```
JSON DSL → DSL Models → Model Layer → Writers → XML + структура каталогов
```

### Основные компоненты

- **cli/** — CLI интерфейс (Main, Commands)
- **dsl/** — Jackson POJO для JSON DSL
- **model/** — Нормализация данных, TypeResolver, IdGenerator, UuidGenerator
- **writer/** — Генераторы XML (XmlWriter, EpfWriter, FormWriter, RoleWriter, MxlWriter, DcsWriter)
- **editor/** — Редакторы XML (FormEditor, RoleEditor, SkdEditor, EpfEditor, ConfigEditor, SubsystemEditor, ObjectContainerEditor, ExtensionEditor, ReplaceTextEditor, ByteSafeFileHandler)
- **format/** — OutputFormat, DesignerLayout, EdtLayout

## Зависимости

- **mdclasses** (io.github.1c-syntax:mdclasses:0.17.4) — enum-ы и модели метаданных 1С (RoleRight, FormElementType, TemplateType, DataSetType)
- **bsl-common-library** (io.github.1c-syntax:bsl-common-library:0.9.2) — типы и квалификаторы 1С (MDOType, AllowedLength, DateFractions)
- **jackson-databind** — парсинг JSON DSL
- **lombok** — @Value, @Builder
- **junit5 + assertj** — тесты

## Статус реализации

- [x] Phase 0: Инфраструктура
- [x] Phase 1: EPF (Designer формат)
  - [x] epf init
  - [x] epf add-form
  - [x] epf add-template
- [x] Phase 2: Role/Rights (Designer формат)
  - [x] role compile
  - [x] Presets (view, edit, full)
- [x] Phase 3: Form (Designer формат, полная реализация UI-элементов)
  - [x] form compile
  - [x] Реквизиты, команды, события, свойства
  - [x] Поддержка коллекций (ValueTable/ValueTree)
  - [x] UI-элементы (топ-15): InputField, UsualGroup, Table, Button, Label, CheckBox, Pages, Picture, Calendar, CommandBar, Popup
  - [x] Автоматические ContextMenu и ExtendedTooltip
  - [x] Вложенность элементов (children)
- [x] Phase 4: MXL (Designer формат, полная реализация)
  - [x] mxl compile
  - [x] Области, текст, параметры, объединение ячеек
  - [x] Шрифты (fonts) — face, size, bold, italic, underline, strikeout
  - [x] Стили (styles) — align, valign, border, wrap, format
  - [x] Применение стилей к ячейкам
  - [x] Парсинг рамок (all, top,bottom, left,right)
- [x] Phase 5: SKD (Designer формат, полная реализация — 100%)
  - [x] skd compile
  - [x] DataSets (DataSetQuery, DataSetObject, DataSetUnion)
  - [x] Parameters, totalFields, settingsVariants
  - [x] Filter (11 операторов: =, <>, >, >=, <, <=, in, notIn, contains, filled, notFilled)
  - [x] Order (сортировка asc/desc)
  - [x] ConditionalAppearance (selection, filter, appearance с автоопределением типов)
  - [x] Structure (группировки)
- [ ] Phase 6: Интеграция

## Тесты

```bash
./gradlew test
```

**Test Coverage:**
- ✅ `TypeResolverTest` (10+ тестов)
  - Примитивные типы (string, number, boolean, date, uuid)
  - Коллекции (ValueTable, ValueTree)
  - Ссылочные типы (ref:Catalog.Name)
  - Объектные типы (ExternalDataProcessorObject.Name, DocumentObject.Name, CatalogObject.Name)
- ✅ `EpfWriterTest` (6 тестов)
  - testInitCreatesValidStructure
  - testAddFormCreatesValidStructure
  - testAddTemplateSpreadsheetDocument
  - testAddTemplateHTMLDocument
  - testCompleteEpfWithFormAndTemplates
  - testBomInMetadataFiles
- ✅ `FormWriterTest` (8 тестов)
  - testMinimalForm
  - testFormWithAttributes
  - testFormWithCommands
  - testFormWithEvents
  - testFormWithValueTable
  - testCompleteForm
  - testFormWithUIElements (новый — проверка UI-элементов)
  - testJsonDslRoundtrip
- ✅ `MxlWriterTest` (6 тестов)
  - testMinimalMxl
  - testMxlWithParameters
  - testMxlWithSpan
  - testMxlWithMultipleAreas
  - testMxlWithFontsAndStyles (новый — проверка шрифтов и стилей)
  - testJsonDslRoundtrip
- ✅ `SkdWriterTest` (7 тестов)
  - testMinimalSkd
  - testSkdWithParameters
  - testSkdWithTotalFields
  - testSkdWithSettingsVariant
  - testSkdWithFilterAndOrder (проверка filter и order)
  - testSkdWithConditionalAppearance (новый — проверка условного оформления)
  - testJsonDslRoundtrip

- ✅ `ByteSafeFileHandlerTest` (7 тестов)
  - readFileWithBom, readFileWithoutBom
  - writeBackPreservesBom, writeBackNoBomWhenOriginalHadNone
  - utf8EncodingIgnoresBom, backupCreatesFile, computeSizeWithBom
- ✅ `ReplaceTextEditorTest` (10 тестов)
  - bomPreservation — BOM сохраняется после замены
  - bareLfPreservation — bare LF (0x0A) не превращается в CRLF
  - mixedLineEndingsWithBom — интеграционный: BOM + mixed LF/CRLF
  - notFoundReturnsZeroReplacements
  - replaceAllFlag — --all vs первое вхождение
  - dryRunDoesNotModifyFile
  - multipleReplacementPairs — несколько --old/--new
  - backupCreatesFile
  - resultContainsByteSizes
  - onlyReplacedBytesChange — побайтовое сравнение

**Всего тестов:** 53 (все проходят)

**Roundtrip-тесты:**
- Проверка структуры файлов
- Проверка содержимого XML
- Проверка BOM (UTF-8 BOM в метаданных, без BOM в Form.xml/Template.xml)
- Проверка порядка элементов в ChildObjects
- Проверка UUID генерации
- Проверка DefaultForm установки
- Проверка типов реквизитов и квалификаторов
- Проверка областей и параметров в MXL
- Проверка dataSets и settingsVariants в SKD
- Проверка UI-элементов форм (InputField, UsualGroup, Table, Button, Pages)
- Проверка шрифтов и стилей в MXL (fonts, styles, border, align)
- Проверка filter и order в SKD (операторы сравнения, сортировка)
- Проверка conditionalAppearance в SKD (selection, filter, appearance, типы значений)

Все тесты используют временные каталоги (@TempDir) и проверяют корректность генерируемых файлов.

## Exit codes (TASK-155, 2026-05-22)

| Code | Meaning | When it occurs |
|------|---------|----------------|
| `0`  | Success | The operation completed without errors |
| `1`  | Business / domain error | Invalid input, missing required field, duplicate entity, unknown enum value, target object not found, boundary violation. `stderr` contains `ERROR: <message>`. |
| `2`  | JVM / infrastructure failure | The JVM itself crashed (OOM, missing JAR, etc.). The JVM writes its own diagnostics to `stderr`. |

**Debug mode:** pass `--debug` as a flag (in any position of the command line) **or** set the environment variable `XML_GEN_DEBUG=1` to print the full Java stack trace to `stderr` on exit `1`. Both are equivalent and useful when diagnosing unexpected exceptions during development.

```bash
# Normal use — clean error message, exit=1
xml-gen skd compile bad.json out.xml
# → stderr: ERROR: dataSets field is required in SKD DSL

# Debug mode (CLI flag, recommended for one-off):
xml-gen --debug skd compile bad.json out.xml
# Flag may appear in any position:
xml-gen skd compile --debug bad.json out.xml

# Debug mode (env var, useful for batch / scripts):
XML_GEN_DEBUG=1 xml-gen skd compile bad.json out.xml
```

## Лицензия

LGPL-3.0 (совместимо с mdclasses)
