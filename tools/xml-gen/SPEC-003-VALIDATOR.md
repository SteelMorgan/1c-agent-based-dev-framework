# SPEC-003: XML Validator для объектов метаданных 1С

## Цель

Файловый линтер для XML-объектов 1С (Form, Role, SKD, MXL, EPF).
Проверяет корректность **отдельных файлов** без контекста конфигурации.

Cross-file валидация (Level 3) — ответственность BSL Language Server.

## Архитектура

```
CLI: xml-gen validate <type> <path> [--format designer|edt] [--level structure|semantic] [--output text|json]
                          │
                    ┌─────┴──────┐
                    │ Dispatcher  │  автодетект типа + формата
                    └─────┬──────┘
                          │
         XmlStructureReader.parse(file) → XmlDocument (StAX + номера строк)
                          │
              ┌───────────┼───────────┬───────────┬───────────┐
              │           │           │           │           │
        FormValidator  RoleValidator  SkdValidator  MxlValidator  EpfValidator
              │           │           │           │           │
         Level 1: Structure (XML-каркас, обязательные элементы, id, BOM)
         Level 2: Semantic  (enum-ы mdclasses, внутренние ссылки, значения)
                          │
                    ┌─────┴──────┐
                    │  Reporter   │  TextReporter / JsonReporter
                    └────────────┘
                          │
                    Exit code: 0=ok, 1=errors, 2=warnings
```

## Пакетная структура

```
io.github.onec.xmlgen/
├── validator/
│   ├── XmlValidator.java              интерфейс
│   ├── ValidationResult.java          результат валидации файла
│   ├── ValidationIssue.java           одна проблема
│   ├── Severity.java                  enum: ERROR, WARNING, INFO
│   ├── ValidationLevel.java           enum: STRUCTURE, SEMANTIC
│   ├── ValidatorFactory.java          фабрика: тип → валидатор
│   ├── XmlStructureReader.java        StAX reader → XmlDocument дерево
│   ├── FormValidator.java
│   ├── RoleValidator.java
│   ├── SkdValidator.java
│   ├── MxlValidator.java
│   ├── EpfValidator.java
│   └── report/
│       ├── TextReporter.java          вывод для консоли
│       └── JsonReporter.java          вывод для CI / агента
├── cli/
│   ├── Main.java                      + validate command
│   └── Commands.java                  + executeValidate()
└── (существующие пакеты: writer/, dsl/, model/, format/)
```

## CLI

```bash
# Базовое использование
xml-gen validate form Form.xml
xml-gen validate role ./Roles/МояРоль/Ext/Rights.xml
xml-gen validate skd Template.xml
xml-gen validate mxl Template.xml
xml-gen validate epf ./МояОбработка/

# С параметрами
xml-gen validate form Form.xml --format designer --level semantic
xml-gen validate form Form.xml --output json

# Exit codes
#   0 — валидация пройдена
#   1 — есть ERROR
#   2 — есть WARNING (но нет ERROR)
```

## Формат вывода

### Text (по умолчанию)

```
Validating: Rights.xml (Designer, Role)

✗ ROLE-101 [ERROR] line 15: Unknown right name 'Чтение', expected RoleRight enum value
  at: /Rights/object[1]/right[1]/name

⚠ ROLE-103 [WARNING] line 22: Right 'Posting' is not applicable to Catalog objects
  at: /Rights/object[1]/right[3]/name

Result: 1 error, 1 warning
```

### JSON (--output json)

```json
{
  "file": "Rights.xml",
  "format": "designer",
  "type": "role",
  "valid": false,
  "summary": { "errors": 1, "warnings": 1, "info": 0 },
  "issues": [
    {
      "severity": "error",
      "code": "ROLE-101",
      "message": "Unknown right name 'Чтение', expected RoleRight enum value",
      "line": 15,
      "element": "/Rights/object[1]/right[1]/name"
    }
  ]
}
```

## Модель данных

### XmlDocument (результат парсинга)

```java
@Value
public class XmlDocument {
    Path file;
    boolean hasBom;
    String rootElement;        // "Form", "Rights", "DataCompositionSchema", "document"
    String rootNamespace;
    Map<String, String> rootAttributes;  // version, uuid и т.д.
    List<XmlNode> children;
}

@Value
public class XmlNode {
    String name;
    String namespace;
    Map<String, String> attributes;
    String text;               // текст внутри элемента
    List<XmlNode> children;
    int line;                  // номер строки (для диагностик)
}
```

### ValidationResult

```java
@Value
public class ValidationResult {
    Path file;
    String type;               // "form", "role", "skd", "mxl", "epf"
    String format;             // "designer", "edt"
    List<ValidationIssue> issues;
    
    public boolean isValid() { ... }     // нет ERROR
    public int errorCount() { ... }
    public int warningCount() { ... }
}

@Value
public class ValidationIssue {
    Severity severity;
    String code;               // "FORM-001", "ROLE-101"
    String message;
    int line;                  // 0 = неизвестно
    String element;            // XPath-like путь
}
```

---

## Правила валидации

### Система кодов

- `GEN-0xx` — общие проверки (XML, BOM, declaration)
- `FORM-0xx` — Form Level 1 (структура)
- `FORM-1xx` — Form Level 2 (семантика)
- `ROLE-0xx` / `ROLE-1xx` — Role
- `SKD-0xx` / `SKD-1xx` — SKD
- `MXL-0xx` / `MXL-1xx` — MXL
- `EPF-0xx` — EPF

---

### GEN — общие (все типы)

| Код | Level | Severity | Проверка |
|-----|-------|----------|----------|
| `GEN-001` | 1 | ERROR | XML well-formed (StAX парсится без ошибок) |
| `GEN-002` | 1 | ERROR | XML declaration `<?xml version="1.0" encoding="UTF-8"?>` |
| `GEN-003` | 1 | ERROR | BOM-политика: Designer metadata → BOM, остальное → без BOM |
| `GEN-004` | 1 | ERROR | Root element соответствует типу объекта |
| `GEN-005` | 1 | ERROR | Namespace root-элемента соответствует формату (Designer/EDT) |
| `GEN-006` | 1 | WARNING | UUID-атрибуты — валидный формат `[0-9a-f]{8}-[0-9a-f]{4}-...` |

### FORM — структура (Level 1)

| Код | Severity | Проверка |
|-----|----------|----------|
| `FORM-001` | ERROR | `AutoCommandBar` присутствует, `name="ФормаКоманднаяПанель"`, `id="-1"` |
| `FORM-002` | WARNING | `version="2.17"` на root (Designer) |
| `FORM-003` | ERROR | Каждый `Attribute` имеет `name` и числовой `id` |
| `FORM-004` | ERROR | Нет дублей `id` среди элементов / атрибутов / команд |
| `FORM-005` | WARNING | ID последовательные ≥ 1 |
| `FORM-006` | WARNING | `ChildItems` присутствует |
| `FORM-007` | ERROR | Каждый UI-элемент имеет `name` и `id` |
| `FORM-008` | ERROR | Каждая `Command` имеет `name` и `id` |

### FORM — семантика (Level 2)

| Код | Severity | Проверка |
|-----|----------|----------|
| `FORM-101` | ERROR | Тип UI-элемента — известный `FormElementType` |
| `FORM-102` | ERROR | `DataPath` → существующий `Attribute.name` |
| `FORM-103` | WARNING | `Button.CommandName` → существующая `Command` (формат `Form.Command.<name>`) |
| `FORM-104` | WARNING | У `InputField` / `Table` есть `DataPath` |
| `FORM-105` | WARNING | Авто-элемент `КонтекстноеМеню` для InputField/Table/LabelField |
| `FORM-106` | WARNING | Авто-элемент `РасширеннаяПодсказка` для UI-элементов |
| `FORM-107` | ERROR | Тип атрибута — валидный (`xs:string`/`xs:decimal`/`xs:boolean`/`xs:dateTime`/`v8:*`/`cfg:*`) |
| `FORM-108` | ERROR | `StringQualifiers.AllowedLength` — `Variable` или `Fixed` (через `AllowedLength` enum) |
| `FORM-109` | ERROR | `NumberQualifiers.AllowedSign` — `Any` или `Nonnegative` |
| `FORM-110` | ERROR | `DateQualifiers.DateFractions` — `Date`/`Time`/`DateTime` (через `DateFractions` enum) |
| `FORM-111` | ERROR | `Event.name` непустой |
| `FORM-112` | WARNING | `Command.Action` непустой |
| `FORM-113` | WARNING | ValueTable-атрибут имеет ≥ 1 Column |

### ROLE — структура (Level 1)

| Код | Severity | Проверка |
|-----|----------|----------|
| `ROLE-001` | ERROR | `<Rights>` root с namespace `http://v8.1c.ru/8.2/roles` |
| `ROLE-002` | WARNING | `setForNewObjects`, `setForAttributesByDefault`, `independentRightsOfChildObjects` присутствуют |
| `ROLE-003` | ERROR | Каждый `<object>` имеет `<name>` |
| `ROLE-004` | ERROR | Каждый `<right>` имеет `<name>` и `<value>` |
| `ROLE-005` | ERROR | `<value>` — строго `"true"` или `"false"` |

### ROLE — семантика (Level 2)

| Код | Severity | Проверка |
|-----|----------|----------|
| `ROLE-101` | ERROR | `right.name` — известный `RoleRight` (через `RoleRight.valueByName`) |
| `ROLE-102` | WARNING | Тип объекта — известный `MDOType` (через `MDOType.fromValue`) |
| `ROLE-103` | WARNING | Право применимо к типу объекта (Posting только для Document) |
| `ROLE-104` | ERROR | Нет дублей: одно право не дважды для одного объекта |
| `ROLE-105` | ERROR | Формат `object.name`: `<MDOType>.<Name>` (одна точка) |
| `ROLE-106` | WARNING | `restrictionByCondition.condition` непустой |
| `ROLE-107` | ERROR | `restrictionTemplate.name` и `.condition` непустые |

### SKD — структура (Level 1)

| Код | Severity | Проверка |
|-----|----------|----------|
| `SKD-001` | ERROR | Root `<DataCompositionSchema>` |
| `SKD-002` | WARNING | ≥ 1 `<dataSource>` |
| `SKD-003` | ERROR | Каждый dataSet имеет `xsi:type` |
| `SKD-004` | ERROR | DataSetQuery содержит `<query>` |
| `SKD-005` | WARNING | ≥ 1 `<settingsVariant>` |

### SKD — семантика (Level 2)

| Код | Severity | Проверка |
|-----|----------|----------|
| `SKD-101` | ERROR | `DataSet.xsi:type` — известный `DataSetType` |
| `SKD-102` | ERROR | `comparisonType` — валидный (Equal/NotEqual/Greater/GreaterOrEqual/Less/LessOrEqual/InList/NotInList/Contains/Filled/NotFilled) |
| `SKD-103` | ERROR | `orderType` — `Asc` или `Desc` |
| `SKD-104` | WARNING | `xsi:type` значений — валидный (`xs:string`/`xs:decimal`/`xs:boolean`/`xs:dateTime`) |
| `SKD-105` | WARNING | Appearance-параметры — известные |
| `SKD-106` | ERROR | Поля в selection/order/filter непустые |
| `SKD-107` | WARNING | DataSetQuery имеет `<dataSource>` |

### MXL — структура (Level 1)

| Код | Severity | Проверка |
|-----|----------|----------|
| `MXL-001` | ERROR | Root `<document>` с namespace `http://v8.1c.ru/8.2/data/spreadsheet` |
| `MXL-002` | WARNING | `<templateMode>true</templateMode>` |
| `MXL-003` | WARNING | `<columns>` с `<size>` |
| `MXL-004` | WARNING | `<height>` = количество `<rowsItem>` |
| `MXL-005` | WARNING | Индексы `<rowsItem>` последовательные от 0 |

### MXL — семантика (Level 2)

| Код | Severity | Проверка |
|-----|----------|----------|
| `MXL-101` | ERROR | `horizontalAlignment` — `Left`/`Center`/`Right` |
| `MXL-102` | ERROR | `verticalAlignment` — `Top`/`Center`/`Bottom` |
| `MXL-103` | ERROR | `merge` ≥ 0 |
| `MXL-104` | ERROR | Ссылка на font → font существует |
| `MXL-105` | ERROR | Ссылка на format/style → существует |
| `MXL-106` | WARNING | Font `<height>` > 0 |

### EPF — структура (Level 1)

| Код | Severity | Проверка |
|-----|----------|----------|
| `EPF-001` | ERROR | `<ExternalDataProcessor uuid="...">` присутствует |
| `EPF-002` | ERROR | `<Name>` непустой |
| `EPF-003` | ERROR | `<xr:ClassId>` = `c3831ec8-d8d5-4f93-8a22-f9bfae07327f` |
| `EPF-004` | ERROR | `<ChildObjects>` присутствует |
| `EPF-005` | WARNING | Forms перед Templates в ChildObjects |
| `EPF-006` | ERROR | Файлы из ChildObjects существуют на диске |

---

## Фазы разработки

### Phase 1 — Инфраструктура

**Scope:** модели данных, интерфейсы, StAX reader, CLI, reporters

**Файлы:**

```
validator/
├── XmlValidator.java
├── ValidationResult.java
├── ValidationIssue.java
├── Severity.java
├── ValidationLevel.java
├── ValidatorFactory.java
├── XmlStructureReader.java
└── report/
    ├── TextReporter.java
    └── JsonReporter.java
cli/
├── Main.java          (+ validate)
└── Commands.java      (+ executeValidate)
```

**Тесты:**

```
validator/
├── XmlStructureReaderTest.java    — парсинг XML в дерево, BOM-детекция, номера строк
└── report/
    └── TextReporterTest.java      — форматирование вывода
```

**Критерий готовности:**
- `xml-gen validate --help` работает
- `XmlStructureReader` парсит XML в `XmlDocument`
- BOM-детекция работает
- Репортеры форматируют `ValidationResult`

---

### Phase 2 — RoleValidator

**Scope:** самый простой XML, быстрый feedback loop

**Правила:** ROLE-001..005 (struct) + ROLE-101..107 (semantic) = 12 правил

**Тесты:**

```
validator/
└── RoleValidatorTest.java
    ├── testValidRightsXmlPassesValidation()         — roundtrip с RoleWriter
    ├── testValidEdtRightsPassesValidation()          — EDT формат
    ├── testMissingRootNamespace()                     — ROLE-001
    ├── testMissingObjectName()                        — ROLE-003
    ├── testInvalidRightValue()                        — ROLE-005
    ├── testUnknownRoleRight()                         — ROLE-101
    ├── testUnknownMdoType()                           — ROLE-102
    ├── testPostingForCatalog()                        — ROLE-103
    ├── testDuplicateRight()                           — ROLE-104
    └── testInvalidObjectNameFormat()                  — ROLE-105
```

**Критерий готовности:**
- `xml-gen validate role Rights.xml` выводит результат
- Writer-generated XML проходит валидацию (0 ошибок)
- Negative tests ловят конкретные ошибки

---

### Phase 3 — FormValidator

**Scope:** самый сложный XML

**Правила:** FORM-001..008 (struct) + FORM-101..113 (semantic) = 21 правило

**Тесты:**

```
validator/
└── FormValidatorTest.java
    ├── testValidFormPassesValidation()                — roundtrip
    ├── testMissingAutoCommandBar()                    — FORM-001
    ├── testDuplicateId()                              — FORM-004
    ├── testUnknownElementType()                       — FORM-101
    ├── testDataPathToMissingAttribute()               — FORM-102
    ├── testButtonToMissingCommand()                   — FORM-103
    ├── testInvalidAllowedLength()                     — FORM-108
    └── testValueTableWithoutColumns()                 — FORM-113
```

**Критерий готовности:**
- `xml-gen validate form Form.xml` работает
- Roundtrip: FormWriter → validate = 0 ошибок

---

### Phase 4 — SkdValidator

**Правила:** SKD-001..005 + SKD-101..107 = 12 правил

**Тесты:**

```
validator/
└── SkdValidatorTest.java
    ├── testValidSkdPassesValidation()
    ├── testMissingDataSource()                        — SKD-002
    ├── testInvalidDataSetType()                       — SKD-101
    ├── testInvalidComparisonType()                    — SKD-102
    └── testEmptyFilterField()                         — SKD-106
```

---

### Phase 5 — MxlValidator

**Правила:** MXL-001..005 + MXL-101..106 = 11 правил

**Тесты:**

```
validator/
└── MxlValidatorTest.java
    ├── testValidMxlPassesValidation()
    ├── testInvalidAlignment()                         — MXL-101
    ├── testFontReferenceToMissing()                   — MXL-104
    └── testNegativeMerge()                            — MXL-103
```

---

### Phase 6 — EpfValidator

**Scope:** multi-file валидация (корневой XML + дочерние файлы)

**Правила:** EPF-001..006 = 6 правил

**Тесты:**

```
validator/
└── EpfValidatorTest.java
    ├── testValidEpfPassesValidation()                 — roundtrip с EpfWriter
    ├── testWrongClassId()                             — EPF-003
    ├── testChildObjectsMissing()                      — EPF-004
    ├── testFormsAfterTemplates()                      — EPF-005
    └── testChildFileNotExists()                       — EPF-006
```

---

## Сводка

| Фаза | Правил | Новых файлов | Тестов | Зависимости |
|------|--------|-------------|--------|-------------|
| 1 — Infra | 6 (GEN) | 10 | ~8 | StAX (JDK) |
| 2 — Role | 12 | 2 | ~10 | RoleRight, MDOType |
| 3 — Form | 21 | 2 | ~10 | FormElementType, AllowedLength, DateFractions |
| 4 — SKD | 12 | 2 | ~6 | DataSetType |
| 5 — MXL | 11 | 2 | ~5 | — (нет mdclasses для MXL) |
| 6 — EPF | 6 | 2 | ~5 | — |
| **Итого** | **68** | **20** | **~44** | |

## Тестовая стратегия

### Roundtrip-тесты (главный принцип)

```
Writer.create(dsl, file)  →  Validator.validate(file)  ==  0 ошибок
```

Гарантирует: то что Writer генерит — валидно по нашим правилам.

### Negative-тесты

Берём валидный XML (из Writer), **ломаем** конкретным способом, проверяем что валидатор выдаёт конкретный код ошибки.

### Фикстуры

Используем `@TempDir` + Writer для генерации.
Не храним XML-файлы как ресурсы — генерим на лету.

## Не входит в scope

- Cross-file валидация (Level 3) — BSL Language Server
- XSD-валидация — нет публичных XSD от 1С
- Валидация BSL-кода модулей — BSL LS
- Автоисправление ошибок (fix) — отдельная фича, потом
- Плагинная система правил — overengineering для MVP

---

## Exit codes (TASK-155, 2026-05-22)

Контракт, действующий после патча TASK-155 (A1/A2/A3).

| Code | Meaning |
|------|---------|
| `0`  | Успешно — ни одного ERROR |
| `1`  | Есть ERROR (domain/business) **или** WARNING (exit=2 в прежней спеке SPEC-003 переопределён — см. ниже) |
| `2`  | JVM/инфраструктурный сбой (OOM, missing JAR) |

> **Уточнение к старой спеке.** Изначальный SPEC-003 предлагал `exit=2` для "есть WARNING, нет ERROR". После TASK-155 A1 весь домен exceptions нормализован: любая бизнес-ошибка → `exit=1`; `exit=2` зарезервирован исключительно для JVM-сбоев. Правило "WARNING → exit=2" остаётся в силе **только для `validate`**, если реализован как отдельный режим; в общем CLI-контракте exit=2 = JVM failure.

### Специфика validate-команды

```bash
xml-gen validate <type> <path>
# 0 — нет ни ERROR, ни WARNING
# 1 — есть хотя бы один ERROR
# 2 — есть WARNING (нет ERROR) — специфика validate, не общий контракт
```

### Типовые ситуации exit=1 (не JVM)

- `--type` не из whitelist `{skd, mxl, form, role, epf, meta, config, extension, subsystem, interface}` — CLI отклоняет неизвестный тип → `exit=1`
- Info-команды (`mxlInfo`, `skdInfo`, `formInfo`, `configInfo`, `subsystemInfo`, `metaInfo`, `roleInfo`, `helpAdd`) проверяют root-element XML и возвращают `exit=1` при mismatch (например, передан файл Form.xml команде `role info`)
- `subsystem validate` проверяет существование объектов в `<content>` → `exit=1` если объект не существует
- `interface validate` проверяет корректность command-ref → `exit=1` при broken ref
- `config validate` проверяет broken refs (Languages/, TypeDir/) → `exit=1` (ERROR, не WARNING)

### Debug-режим (stack trace при exit=1)

Чтобы при разработке/диагностике увидеть полный Java stack trace вместо однострочного `ERROR:` — используй один из двух равнозначных способов:

| Способ | Когда применять | Пример |
|--------|-----------------|--------|
| CLI-флаг `--debug` (любая позиция в args) | Разовый запуск, скрипты, ad-hoc debug | `xml-gen --debug validate --type skd schema.xml`<br>`xml-gen validate --debug --type skd schema.xml` |
| Env `XML_GEN_DEBUG=1` | Batch-прогоны, CI, harness, отладка нескольких подряд команд | `XML_GEN_DEBUG=1 ./run-tests.sh` |

Без debug-режима — `exit=1` + одна строка `ERROR: <msg>`. С debug-режимом дополнительно к строке `ERROR:` печатается полный stack trace в stderr. На `exit=0` режим не влияет.
