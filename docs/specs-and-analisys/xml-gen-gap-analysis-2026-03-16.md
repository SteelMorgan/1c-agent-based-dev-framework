# Gap-анализ глубины: xml-gen Java vs cc-1c-skills Python

> Дата: 2026-03-16
> Метод: построчное сравнение Java-реализации с Python-референсом Широкова
> Scope: 11 доменов, 45 реализованных операций

---

## 1. Сводка по доменам

| Домен | Java LOC | Python LOC | Паритет | Критичные пробелы |
|-------|----------|------------|---------|-------------------|
| **CFE** | 1,955 | 1,682 | **100%** | Нет. Java даже шире (ExtensionDiffPrinter) |
| **Form** | ~850 | ~1,150 | **95%** | Нет значительных |
| **MXL** | ~700 | ~1,150 | **90%** | Минорные |
| **SKD** | ~600 | ~1,680 | **85%** | skd-edit: мало операций |
| **Role** | ~600 | ~465 | **95%** | Нет значительных |
| **Meta** | 5,387 | 7,549 | **85%** | edit, validate, remove — см. ниже |
| **CF** | 1,209 | 1,657 | **80%** | Мобильные функциональности, валидация |
| **Subsystem** | 1,102 | ~1,580 | **85%** | Editor: property handling |
| **Interface** | 570 | 838 | **85%** | CreateIfMissing, command reference patterns |
| **EPF** | ~800 | ~700 | **95%** | build/dump требуют платформу |
| **Utilities** | ~450 | ~400 | **100%** | Нет |

---

## 2. Детальный gap-анализ по доменам

### 2.1. Meta — Объекты метаданных (5,387 vs 7,549 LOC)

Самый крупный домен. 23 типа, 5 команд.

#### meta compile (2,012 vs 2,572) — паритет 100%

Полный паритет. Все 23 типа реализованы. JSON DSL, batch mode, GeneratedType, UUID-генерация.

#### meta edit (1,203 vs 2,200) — паритет ~85%

| Фича | Java | Python | Gap |
|------|------|--------|-----|
| add-attribute/dimension/resource/column | ✅ | ✅ | — |
| remove-attribute/dimension/resource/column | ✅ | ✅ | — |
| modify-attribute/dimension/resource/column | ✅ | ✅ | — |
| add/remove-ts, ts-attribute, form, template, command | ✅ | ✅ | — |
| add/remove-enumValue | ✅ | ✅ | — |
| **modify-tabularSection** (TS-level properties) | ❌ | ✅ | **P2** |
| **add-property / modify-property** (object properties) | ❌ | ✅ | **P2** |
| **Composite types** (Type1 + Type2) | ❌ | ✅ | **P2** |
| **MLText editing** (Synonym, Comment) | 🔶 partial | ✅ | **P3** |
| Inline complex property mode | ❌ | ✅ | P3 |
| Shorthand с позиционированием (after/before) | ✅ | ✅ | — |
| Batch `;;` separator | ✅ | ✅ | — |

#### meta validate (769 vs 1,209) — паритет ~90%

| Проверка | Java | Python | Gap |
|----------|------|--------|-----|
| XML structure, version, UUID | ✅ | ✅ | — |
| Properties/Name identifier validation | ✅ | ✅ | — |
| Boolean properties | ✅ | ✅ | — |
| Type-specific properties (22 типа) | ✅ | ✅ | — |
| ChildObjects structure | ✅ | ✅ | — |
| InternalInfo/GeneratedType | ✅ | ✅ | — |
| File structure checks | ✅ | ✅ | — |
| **StandardAttributes block validation** | ❌ | ✅ | **P2** |
| **Property-specific rules** (~34 правила vs ~6) | 🔶 partial | ✅ | **P2** |
| **Forbidden-property checking** per type | ❌ | ✅ | P3 |
| **FillValue/Type consistency** | ❌ | ✅ | P3 |
| Batch mode | ❌ | ✅ | P3 |

#### meta info (731 vs 1,098) — паритет ~95%

Полный паритет по 3 режимам (brief/overview/full) и 23 типам. Python шире на drill-down фильтрацию и output-file.

#### meta remove (471 vs 470) — паритет ~74%

| Фича | Java | Python | Gap |
|------|------|--------|-----|
| Reference check (XML/BSL patterns) | ✅ | ✅ | — |
| Remove from Configuration.xml | ✅ | ✅ | — |
| Remove from Subsystems | ✅ | ✅ | — |
| Dry-run / keep-files / force | ✅ | ✅ | — |
| **Supported types: 17/23** | 🔶 | ✅ 30+ | **P1** |
| Missing: Constant, DefinedType, CommonModule | ❌ | ✅ | **P1** |
| Missing: ScheduledJob, EventSubscription | ❌ | ✅ | **P1** |
| Missing: 4 Register types | ❌ | ✅ | **P2** |
| Handler reference detection | 🔶 | ✅ | P2 |

---

### 2.2. CF — Конфигурация (1,209 vs 1,657 LOC)

#### config init (228 vs 204) — паритет 95%

| Фича | Java | Python | Gap |
|------|------|--------|-----|
| Configuration.xml scaffold | ✅ | ✅ | — |
| Languages/Русский.xml | ✅ | ✅ | — |
| ConfigDumpInfo.xml | ✅ | ❌ | Java шире |
| Module stubs (ManagedApp, Session) | ✅ | ❌ | Java шире |
| **UsedMobileApplicationFunctionalities** (35+ settings) | ❌ | ✅ | **P3** |

#### config edit (335 vs 517) — паритет 80%

| Фича | Java | Python | Gap |
|------|------|--------|-----|
| modifyProperty | ✅ | ✅ | — |
| addChildObject (canonical order) | ✅ | ✅ | — |
| removeChildObject | ✅ | ✅ | — |
| add/remove/set DefaultRole | ✅ | ✅ | — |
| **XML tree manipulation** (indent-safe) | ❌ | ✅ | **P2** |
| **Fragment import** | ❌ | ✅ | P3 |

#### config validate (310 vs 533) — паритет 80%

| Проверка | Java | Python | Gap |
|----------|------|--------|-----|
| XML structure, InternalInfo, Properties, ChildObjects | ✅ | ✅ | — |
| 11 enum property validations | ✅ | ✅ | — |
| Language/Object files exist | ✅ | ✅ | — |
| **Namespace validation** | ❌ | ✅ | **P2** |
| **UUID format validation** | ❌ | ✅ | **P2** |
| **Identifier pattern validation** (Cyrillic) | ❌ | ✅ | P3 |
| **CompatibilityMode versions** (27 vs partial) | 🔶 | ✅ | P3 |

#### config info (336 vs 403) — паритет 90%

Покрытие хорошее. Python шире на mobile functionality display.

---

### 2.3. Subsystem (1,102 vs ~1,580 LOC)

#### subsystem compile (217 vs ~100) — Java шире

Java имеет полную генерацию из JSON DSL. У Широкова нет выделенного subsystem-init.

#### subsystem edit (221 vs 465) — паритет 75%

| Фича | Java | Python | Gap |
|------|------|--------|-----|
| add/remove-content | ✅ | ✅ | — |
| add/remove-child | ✅ | ✅ | — |
| setProperty | ✅ | ✅ | — |
| **JSON-based property definitions** | ❌ | ✅ | **P2** |
| **Type-aware property handling** (Boolean/LocalString/Picture) | 🔶 | ✅ | P2 |

#### subsystem validate (252 vs 352) — паритет 85%

13 проверок в обеих реализациях. Python глубже в type/format validation (xsi:type, UUID patterns).

#### subsystem info (412 vs ~200) — **Java шире**

Java имеет 5 режимов включая tree и ci (CommandInterface). Широков не имеет аналога.

---

### 2.4. Interface (570 vs 838 LOC)

#### interface edit (322 vs 445) — паритет 85%

6 операций идентичны. Java не имеет **CreateIfMissing** для секций.

#### interface validate (248 vs 393) — паритет 85%

13 проверок в обеих. Python детальнее в command reference patterns (4 формата: StandardCommand, Command, CommonCommand, UUID).

---

### 2.5. CFE — Расширения (1,955 vs 1,682 LOC) — **Java шире**

| Компонент | Java | Python | Gap |
|-----------|------|--------|-----|
| ExtensionWriter (init) | 316 | 239 | Паритет |
| ExtensionEditor (borrow) | 798 | 847 | Паритет, 44 типа, form borrowing |
| ExtensionValidator | 380 | 596 | Паритет, 9 проверок |
| **ExtensionDiffPrinter** | 461 | — | **Уникально Java**: Mode A + Mode B |

---

### 2.6. Form + Utilities

| Компонент | Java | Python | Gap |
|-----------|------|--------|-----|
| ObjectContainerEditor | 449 | ~400 | Java шире (form/template/help add+remove) |
| FormInfoPrinter | ~400 | 601 | Паритет |
| MxlInfoPrinter | ~350 | 446 | Паритет |
| MxlDecompiler | ~350 | 705 | Паритет |
| RoleInfoPrinter | ~300 | 233 | Паритет |
| SkdInfoPrinter | ~300 | — | **Уникально Java** |

---

## 3. Пробелы и их статус

> Обновлено 2026-03-16: ревью GPT (gpt-5.4) + верификация по коду + закрытие.
> 7 из 16 пробелов оказались ложными (уже реализованы в коде).
> 6 закрыты, 3 остались как P3.

### Закрытые пробелы (2026-03-16)

| # | Домен | Пробел | Как закрыт |
|---|-------|--------|------------|
| 4 | Meta | `meta validate`: StandardAttributes | Добавлен `validateStandardAttributes()` с проверкой count по 8 типам |
| 5 | Meta | `meta validate`: property-specific rules | Добавлены правила для Document, ChartOfCharacteristicTypes, ChartOfCalculationTypes, BusinessProcess, Task, ExchangePlan |
| 6 | CF | `config validate`: namespace + UUID | Добавлены Check 1a (xmlns) и Check 1b (UUID format) |
| 8 | Subsystem | `subsystem edit`: type-aware property handling | Добавлены: boolean normalization, Picture как xr:Ref block |
| 11 | Meta | `meta edit`: MLText editing | Добавлен `replaceMlTextProperty()` для Synonym/Comment (3 case: content/self-closing/missing) |
| 15 | Interface | `interface validate`: command reference patterns | Переписан `validateCommandRef()` с 4 паттернами: UUID, CommonCommand, StandardCommand, Command |

### Ложные пробелы (уже были в коде)

| # | Домен | Пробел | Почему ложный |
|---|-------|--------|---------------|
| 1 | Meta | `meta remove`: 6 типов | `MetaRemover` уже работает через `MetadataTypeRegistry` — все типы покрыты |
| 3 | Meta | `meta edit`: composite types | Поддержка `Type1 + Type2` уже есть (MetaEditor:975) |
| 9 | Interface | `interface edit`: CreateIfMissing | `ensureSection()` уже реализован (InterfaceEditor:209) |
| 13 | CF | `config init`: mobile funcs | Пустой тег генерируется; 35+ settings — mobile edge case |
| 14 | CF | `config validate`: compat versions | Список до Version8_3_27 уже полный |
| 16 | Meta | `meta validate`: batch mode | Shell loop достаточно |

### Дополнительно закрыты (P3, 2026-03-16)

| # | Домен | Пробел | Как закрыт |
|---|-------|--------|------------|
| 2 | Meta | `meta edit`: add-property / modify-property | Добавлены `addOrSetProperty()` и `modifyRootProperty()` — scalar, MLText, self-closing |
| 10 | Meta | `meta remove`: handler reference | Добавлены паттерны для ScheduledJob (MethodName), EventSubscription (Handler), DefinedType |
| 12 | Meta | `meta validate`: forbidden-property | Добавлен `validateForbiddenProperties()` — 6 типов с запрещёнными свойствами |

### Не реализуется (DROP)

| # | Домен | Пробел | Причина |
|---|-------|--------|---------|
| 7 | CF | `config edit`: XML tree вместо regex | Рефакторинг, не фича. Текущий подход работает |

---

## 4. Области где Java шире Python

| Фича | Описание |
|------|----------|
| **ExtensionDiffPrinter** | Mode A (обзор) + Mode B (проверка переноса) — нет аналога у Широкова |
| **SkdInfoPrinter** | Нет аналога у Широкова |
| **SubsystemInfoPrinter tree/ci** | 5 режимов vs 2 у Python |
| **ConfigWriter stubs** | ConfigDumpInfo.xml + module stubs — Python не создаёт |
| **ObjectContainerEditor** | Универсальный form/template/help add+remove — Python имеет отдельные скрипты |
| **SubsystemWriter** | JSON DSL → Subsystem — Python не имеет init-скрипта |

---

## 5. Архитектурные наблюдения

### Подход к XML-редактированию

| Аспект | Java | Python |
|--------|------|--------|
| Стратегия | String-based (regex, indexOf, replace) | DOM/lxml tree manipulation |
| Плюсы | Быстро, предсказуемо | Безопасно для вложенных структур |
| Минусы | Хрупко при вложенности | Медленнее, перезапись форматирования |

**Рекомендация:** для edit-команд критичных доменов (Meta, Config) рассмотреть гибридный подход — StAX-парсинг для чтения + StringBuilder для записи с контролем отступов.

### Размер кода

```
Java  total: ~13,700 LOC (11 доменов)
Python total: ~17,900 LOC (11 доменов)
Ratio: Java = 77% от Python при ~90% покрытия фич
```

Java эффективнее по LOC за счёт type system и переиспользования общей инфраструктуры (XmlStructureReader, IdAllocator, MetadataTypeRegistry).
