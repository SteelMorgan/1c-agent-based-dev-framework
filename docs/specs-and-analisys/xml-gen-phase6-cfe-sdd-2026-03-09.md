# SDD: Phase 6 — CFE (Расширения конфигурации)

> Дата: 2026-03-09
> Основание: [xml-gen-expansion-plan-2026-03-09.md](xml-gen-expansion-plan-2026-03-09.md), Phase 6
> Спецификация: [1c-extension-spec.md](../reference/cc-1c-skills/1c-extension-spec.md)
> Python-референсы: cfe-init.py (239), cfe-borrow.py (846), cfe-diff.py (540), cfe-validate.py (596)

---

## 1. Обзор

Phase 6 добавляет домен `extension` в xml-gen CLI — CRUD для расширений конфигурации 1С (CFE).

### Команды

| # | Команда | Описание | Оценка |
|---|---------|----------|--------|
| 6.1 | `xml-gen extension init` | Scaffold расширения | M |
| 6.2 | `xml-gen extension borrow` | Заимствование объекта из конфигурации | L |
| 6.3 | `xml-gen extension diff` | Анализ расширения (overview + transfer check) | L |
| 6.4 | `xml-gen extension validate` | 9-шаговая валидация расширения | M |

---

## 2. Архитектура

### 2.1. Новые классы

```
io.github.onec.xmlgen/
├── writer/
│   └── ExtensionWriter.java        # 6.1 — extension init
├── editor/
│   └── ExtensionEditor.java        # 6.2 — extension borrow
├── info/
│   └── ExtensionDiffPrinter.java   # 6.3 — extension diff (Mode A + Mode B)
└── validator/
    └── ExtensionValidator.java      # 6.4 — extension validate
```

### 2.2. Изменения в существующих классах

- **`Commands.java`** — новый case `"extension"` в `execute()`, dispatch на `init/borrow/diff/validate`
- **`MetadataTypeRegistry`** — уже содержит 23 типа с directory mapping; переиспользуется для `CHILD_TYPE_DIR_MAP`

### 2.3. Общие константы

Вынести в `ExtensionConstants.java` или inline:

- **44 типа ChildObjects** в каноническом порядке (уже есть частично в ConfigValidator)
- **7 ClassId** для ContainedObject в InternalInfo
- **GENERATED_TYPES** маппинг (тип → категории GeneratedType) — уже есть в MetadataTypeRegistry
- **Enum values** для extension-specific properties

---

## 3. Детальный дизайн

### 3.1. `extension init` (ExtensionWriter)

**CLI:**
```
xml-gen extension init <outputDir> <name>
    [--synonym <syn>]
    [--prefix <prefix>]          # default: <name>_
    [--purpose <Patch|Customization|AddOn>]  # default: Customization
    [--compat <Version8_3_24>]
    [--version <ver>]
    [--vendor <vendor>]
    [--config-path <path>]       # auto-resolve compat + Language UUID
    [--no-role]
```

**Генерируемые файлы:**
1. `Configuration.xml` — полный XML с extension-specific properties
2. `Languages/Русский.xml` — заимствованный язык (ObjectBelonging=Adopted)
3. `Roles/<prefix>ОсновнаяРоль.xml` — собственная роль (если не `--no-role`)

**Особенности:**
- `--config-path`: если указан, считать UUID языка из `Languages/Русский.xml` основной конфигурации и `CompatibilityMode` из `Configuration.xml`
- InternalInfo: 7 ContainedObject с фиксированными ClassId
- Configuration.xml Properties: порядок по спецификации (ObjectBelonging → Name → ... → InterfaceCompatibilityMode)
- BOM (UTF-8 BOM) для всех XML файлов

**Подход:** text-based template (как в cfe-init.py) — без DOM, строковые шаблоны с подстановкой. Аналогично ConfigWriter.

### 3.2. `extension borrow` (ExtensionEditor)

**CLI:**
```
xml-gen extension borrow <extensionPath> <configPath> <objectSpec>
    # objectSpec: "Catalog.Контрагенты" или "Catalog.Контрагенты.Form.ФормаСписка"
    # batch: "Catalog.Контрагенты ;; CommonModule.ОбщийМодуль"
```

**Алгоритм:**
1. Resolve paths (extension dir, config dir)
2. Parse extension Configuration.xml (lxml → text-based для Java)
3. Для каждого item из objectSpec:
   a. Parse `Type.Name[.Form.FormName]`
   b. Russian synonyms → English type (Справочник → Catalog)
   c. Read source object XML → extract UUID
   d. Generate borrowed object XML:
      - New UUID, InternalInfo с GeneratedTypes
      - Properties: ObjectBelonging=Adopted, Name, Comment/, ExtendedConfigurationObject=<sourceUUID>
      - CommonModule: копировать флаги (Global, Server, etc.)
      - Types with ChildObjects: `<ChildObjects/>`
   e. Write to `<extDir>/<DirName>/<Name>.xml`
   f. Register in extension Configuration.xml ChildObjects (canonical order)
   g. Если form — дополнительно:
      - Borrow parent object first (если ещё нет)
      - Generate form metadata (ObjectBelonging=Adopted, ExtendedConfigurationObject)
      - Generate Form.xml with BaseForm (Part1 + BaseForm snapshot)
      - Create empty Module.bsl
      - Register form in parent's ChildObjects

**Подход:** text-based XML manipulation для записи файлов. Чтение source объектов через StAX (только uuid + properties). Редактирование Configuration.xml — text-based regex (аналогично ConfigEditor).

**Ключевые отличия от meta compile:**
- Borrowed object = минимальный XML (не полный)
- ExtendedConfigurationObject содержит UUID из source config
- Form borrowing = самая сложная часть (BaseForm с обнулёнными CommandName)

### 3.3. `extension diff` (ExtensionDiffPrinter)

**CLI:**
```
xml-gen extension diff <extensionPath> <configPath>
    [--mode A|B]     # A=overview (default), B=transfer check
```

**Mode A — Overview:**
1. Parse extension Configuration.xml → ChildObjects
2. Для каждого объекта (кроме Language):
   - Прочитать XML → определить borrowed/own
   - Для borrowed: найти .bsl файлы, парсить декораторы (&Перед/&После/&Вместо/&ИзменениеИКонтроль)
   - Для borrowed с ChildObjects: посчитать own attrs/forms/TS, borrowed items
   - Для форм: определить borrowed/own, найти callType interceptors
3. Вывод: `[BORROWED]`/`[OWN]` с деталями

**Mode B — Transfer Check:**
1. Найти все &ИзменениеИКонтроль декораторы
2. Для каждого: найти #Вставка/#КонецВставки блоки
3. Проверить наличие кода из #Вставка в соответствующем модуле конфигурации
4. Вывод: `[TRANSFERRED]`/`[NOT_TRANSFERRED]`/`[NEEDS_REVIEW]`

**Подход:** StAX для парсинга XML, regex для BSL-анализа. Read-only операция.

### 3.4. `extension validate` (ExtensionValidator)

**CLI:**
```
xml-gen extension validate <extensionPath>
    [--max-errors <N>]    # default: 30
```

**9 проверок (по Python-референсу):**

| # | Проверка | Severity |
|---|----------|----------|
| 1 | Root structure: MetaDataObject/Configuration, version 2.17/2.20 | ERROR |
| 2 | InternalInfo: 7 ContainedObject, valid ClassIds | ERROR/WARN |
| 3 | Extension properties: ObjectBelonging=Adopted, Name, Purpose, NamePrefix | ERROR/WARN |
| 4 | Enum property values: CompatibilityMode, RunMode, ScriptVariant, InterfaceCompat | ERROR |
| 5 | ChildObjects: valid types (44), no duplicates, canonical order | ERROR/WARN |
| 6 | DefaultLanguage references existing Language in ChildObjects | ERROR |
| 7 | Language files exist on disk | WARN |
| 8 | Object directories exist on disk | WARN |
| 9 | Borrowed objects: ObjectBelonging=Adopted + valid ExtendedConfigurationObject UUID | ERROR |

**Подход:** аналогично ConfigValidator — StAX парсинг, отдельная проверка каждого критерия. Переиспользовать Reporter из validator package.

---

## 4. Порядок реализации

### Batch 1: init + validate (независимые)
1. **ExtensionWriter** (init) — самый простой, template-based
2. **ExtensionValidator** (validate) — проверка результата init

Верификация: `extension init` → `extension validate` → 0 errors.

### Batch 2: borrow
3. **ExtensionEditor** (borrow) — основная сложность: object + form borrowing

Верификация: init → borrow → validate.

### Batch 3: diff
4. **ExtensionDiffPrinter** (diff) — read-only анализ

Верификация: init → borrow → diff (Mode A показывает borrowed/own).

---

## 5. Данные и маппинги

### 5.1. 44 типа ChildObjects (canonical order)

```
Language, Subsystem, StyleItem, Style, CommonPicture, SessionParameter, Role,
CommonTemplate, FilterCriterion, CommonModule, CommonAttribute, ExchangePlan,
XDTOPackage, WebService, HTTPService, WSReference, EventSubscription, ScheduledJob,
SettingsStorage, FunctionalOption, FunctionalOptionsParameter, DefinedType,
CommonCommand, CommandGroup, Constant, CommonForm, Catalog, Document,
DocumentNumerator, Sequence, DocumentJournal, Enum, Report, DataProcessor,
InformationRegister, AccumulationRegister, ChartOfCharacteristicTypes,
ChartOfAccounts, AccountingRegister, ChartOfCalculationTypes, CalculationRegister,
BusinessProcess, Task, IntegrationService
```

### 5.2. 7 ClassId для InternalInfo

```
9cd510cd-abfc-11d4-9434-004095e12fc7
9fcd25a0-4822-11d4-9414-008048da11f9
e3687481-0a87-462c-a166-9f34594f9bba
9de14907-ec23-4a07-96f0-85521cb6b53b
51f2d5d8-ea4d-4064-8892-82951750031e
e68182ea-4237-4383-967f-90c1e3370bc7
fb282519-d103-4dd3-bc12-cb271d631dfc
```

### 5.3. Переиспользование из MetadataTypeRegistry

- `get(type) → TypeDescriptor` (dirName, xmlElement, generatedTypes)
- 23 зарегистрированных типа уже покрывают основные объекты
- Для extension-only типов (Style, StyleItem, SessionParameter и т.д.) — дополнить или использовать inline map

### 5.4. Russian type synonyms (для borrow)

```
Справочник → Catalog, Документ → Document, Перечисление → Enum,
ОбщийМодуль → CommonModule, ОбщаяКартинка → CommonPicture, ...
```

---

## 6. Риски и ограничения

| Риск | Митигация |
|------|-----------|
| Form borrowing: BaseForm — сложная двухчастная структура | Текстовый подход: читаем source Form.xml, обнуляем CommandName, дублируем как BaseForm |
| 44 типа в ChildObjects vs 23 в MetadataTypeRegistry | Дополнительный inline map для типов без полной поддержки meta compile |
| Extension diff: BSL парсинг декораторов | Regex — проверенный подход (в Python работает) |
| Transfer check Mode B: нормализация whitespace | `replaceAll("\\s+", " ")` для сравнения |

---

## 7. Definition of Done

- [ ] `extension init` создаёт валидное расширение (проходит `extension validate` с 0 errors)
- [ ] `extension borrow` корректно копирует Properties + устанавливает ObjectBelonging=Adopted + ExtendedConfigurationObject
- [ ] `extension borrow` с формой создаёт Form.xml с BaseForm
- [ ] `extension diff` Mode A показывает borrowed/own объекты с декораторами
- [ ] `extension diff` Mode B проверяет перенос #Вставка блоков
- [ ] `extension validate` выполняет все 9 проверок
- [ ] Все 36+ существующих тестов продолжают проходить
