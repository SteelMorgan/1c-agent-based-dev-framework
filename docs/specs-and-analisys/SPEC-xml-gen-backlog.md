# SPEC-xml-gen-backlog: Реализация всего инженерного долга xml-gen

**Статус:** Draft
**Дата:** 2026-05-21
**Адресат:** инженер `tools/xml-gen/` (Java 17 / Gradle 8 / mdclasses 0.17.4)
**Источник скоупа:** [`external-skills-mapping.md` §9](external-skills-mapping.md#9-инженерный-долг-в-xml-gen-)

---

## 0. Контекст и принципы

Skill-документы во `framework/skills/tool-usage/platform-data/xml-generation/**/SKILL.md` уже зафиксировали **полный контракт CLI** (что принимает, что возвращает, edge cases). Этот SPEC — карта реализации: для каждого пункта §9 указывает **где** в Java-коде дорабатывать, **какие** классы вводить или расширять, **как** тестировать.

### 0.1. Принципы

1. **Skill — источник правды поведения.** Если в SPEC и в SKILL.md расхождение — приоритет за SKILL.md. SPEC обязан явно отметить «отклонение от skill» с обоснованием.
2. **Не ломать существующие сценарии.** Все текущие тесты должны оставаться зелёными. Если канонический контракт Широкова требует поведения, не совместимого с текущим — внедрять под флагом или новой подкомандой.
3. **Edit + writer паттерн.** Новые CLI-операции следуют существующему разделению: `editor/*Editor.java` редактируют существующие XML (preserve formatting, BOM, LF/CRLF), `writer/*Writer.java` создают с нуля. `dsl/*Dsl.java` — POJO для JSON DSL.
4. **Атомарность.** Все edit-операции читают → меняют → валидируют well-formedness → пишут атомарно через `ByteSafeFileHandler`.
5. **Идемпотентность.** Где это имеет смысл — повторный запуск не меняет файл, не падает.
6. **i18n зеркало.** Любые правки skill-документов сопровождаются `python3 tools/sync-skill.py framework/...` — это правило проекта (см. `.claude/CLAUDE.md`).
7. **Тесты.** Каждая новая операция — отдельный `*Test.java` в `src/test/java/io/github/onec/xmlgen/...` с минимум: happy path + 2 edge cases + 1 negative.

### 0.2. Структура SPEC

| § | Пункт §9 | Приоритет | Объём | Где основной контракт |
|---|----------|-----------|-------|-----------------------|
| 1 | CFE CLI extensions | P1 | средний | [`SPEC-cfe-cli-extension.md`](SPEC-cfe-cli-extension.md) (полный текст). Тут — только delta. |
| 2 | EPF БСП-обвязка | **P0** | средний | [skill: epf-full §5](../../framework/skills/tool-usage/platform-data/xml-generation/epf-full/references/epf-bsp.md) |
| 3 | Template/Help operations | P1 | средний | [skill: epf-full §4](../../framework/skills/tool-usage/platform-data/xml-generation/epf-full/references/templates.md) |
| 4 | Interface operations | P2 | малый | [skill: interface-operations](../../framework/skills/tool-usage/platform-data/xml-generation/interface-operations/SKILL.md) |
| 5 | SKD edit patch ops | P1 | **крупный** | [skill: skd-edit](../../framework/skills/tool-usage/platform-data/xml-generation/skd-edit/SKILL.md) + 5 references |
| 6 | SKD DSL extension | P1 | **крупный** | [skill: skd-dsl](../../framework/skills/tool-usage/platform-data/xml-generation/skd-dsl/SKILL.md) + 2 references |
| 7 | SKD info 11 modes | P1 | средний | [skill: skd-dsl/references/info-modes](../../framework/skills/tool-usage/platform-data/xml-generation/skd-dsl/references/info-modes.md) |
| 8 | MXL full rewrite | P1 | **крупный** | [skill: mxl-dsl](../../framework/skills/tool-usage/platform-data/xml-generation/mxl-dsl/SKILL.md) + 3 references |
| 9 | Meta batch-patch | P2 | средний | [skill: meta-operations/references/batch-patch](../../framework/skills/tool-usage/platform-data/xml-generation/meta-operations/references/batch-patch.md) |
| 10 | Form validate DataPath | P2 | малый | [skill: forms-toolkit/references/validate](../../framework/skills/tool-usage/platform-data/xml-generation/forms-toolkit/references/validate.md) |

### 0.3. Зависимости между пунктами

```
#6 SKD DSL  ──►  #7 SKD info (режим trace требует расширенной модели)
#1 patch-method ──► (опционально) bsl-generation модуль для тела процедуры
```

Остальные пункты независимы и могут реализовываться параллельно.

### 0.4. Версионирование и релиз

- Версия `xml-gen` — bump `0.1.0-SNAPSHOT` → `0.2.0-SNAPSHOT` после мерджа всех P0+P1.
- Релизные ноты — в `tools/xml-gen/CHANGELOG.md` (создать, если нет). Группировать по пунктам §9.
- При мерджe пункта — в `external-skills-mapping.md` §9 поднять маркер 🔧 → ✅ (без 🔧), отметить дату в §8 changelog как rev.X.

---

## 1. CFE CLI extensions (P1) — `--borrow-main-attribute` + `extension patch-method`

**Контракт:** полностью описан в [`SPEC-cfe-cli-extension.md`](SPEC-cfe-cli-extension.md). Здесь — только маппинг в Java и тестовый план.

### 1.1. Затрагиваемые модули

| Файл | Изменение |
|------|-----------|
| `cli/Commands.java` | расширить кейс `case "extension"` → метод `runExtension(...)`. Добавить парсинг `--borrow-main-attribute <form\|all>` для подкоманды `borrow` и новую подкоманду `patch-method`. |
| `editor/ExtensionEditor.java` | добавить методы `borrowMainAttribute(extPath, configPath, objectSpec, mode)` и `patchMethod(extPath, modulePath, methodName, interceptorType, configPath, context, asFunction)`. |
| `model/MdoPathResolver.java` (новый, выделить из существующего маппинга) | маппинг `--module "Catalog.X.Form.Y"` → файловый путь. Используется и в borrow, и в patch-method. Текущая логика разнесена по `ExtensionEditor` и `ObjectContainerEditor` — извлечь в один утилитный класс. |
| `model/ConfigurationXmlReader.java` (если нет) | чтение `<NamePrefix>` из `Configuration.xml` расширения. |
| `model/BslMethodExtractor.java` (новый) | для `ModificationAndControl` — извлечь тело процедуры/функции по имени из BSL-файла базовой конфигурации. Парсинг текстовый (по `Процедура <name>(...) Экспорт` / `КонецПроцедуры` с учётом nested блоков). |

### 1.2. Тесты

`src/test/java/io/github/onec/xmlgen/editor/ExtensionEditorTest.java` — добавить:

- `testBorrowMainAttribute_FormMode_AddsReferencedAttributes` — фикстура: каталог с одним реквизитом, форма ссылается на него через `DataPath` → после операции XML расширения содержит этот реквизит.
- `testBorrowMainAttribute_AllMode_AddsAllAttributesAndTabularSections` — все реквизиты + табчасть скопированы.
- `testBorrowMainAttribute_ObjectAlreadyBorrowed_NoOverwrite` — повторный запуск не перезаписывает.
- `testBorrowMainAttribute_NoFormSpec_Errors` — `objectSpec` без `.Form.` → внятная ошибка.
- `testPatchMethod_BeforeAfterInstead_GeneratesCorrectAnnotation` — параметризованный тест по 3 типам.
- `testPatchMethod_ModificationAndControl_CopiesOriginalBody` — фикстура с BSL-методом в configPath, проверка что тело перенесено в расширение.
- `testPatchMethod_FunctionFlag_AddsReturnUndefined` — `--function` → `Функция/КонецФункции` + `Возврат Неопределено;`.
- `testPatchMethod_ExistingProcedureName_WarningSkip` — повторный вызов с тем же именем → файл не меняется.
- `testPatchMethod_NamePrefixMissing_Errors` — `Configuration.xml` расширения без `<NamePrefix>`.

### 1.3. Acceptance

- `./gradlew test` зелёный.
- Smoke-сценарии из §4 `SPEC-cfe-cli-extension.md` отрабатывают на e2e-фикстуре расширения.

---

## 2. EPF БСП-обвязка (P0) — `epf-bsp init` / `epf-bsp add-command`

**Контракт:** [skill: epf-full/references/epf-bsp.md](../../framework/skills/tool-usage/platform-data/xml-generation/epf-full/references/epf-bsp.md). Это **P0** — первый кандидат на старт работ.

### 2.1. Решение о неймспейсе CLI

Skill ссылается на «xml-gen CLI» без точной подкоманды. В существующем `Commands.java` есть кейс `case "epf"` (EPF generation). Добавляем под-подкоманду:

```
xml-gen epf bsp-init <epfPath> --kind <вид> [--target <Тип.Имя>...] [--command-type <тип>]
xml-gen epf bsp-add-command <epfPath> --id <идентификатор> --label "<представление>" --type <тип>
```

Альтернатива — отдельная команда `epf-bsp` — отвергнута: засоряет верхний уровень.

### 2.2. Параметры `epf bsp-init`

| Параметр | Тип | Обяз. | Значения |
|----------|-----|-------|----------|
| `<epfPath>` | path | да | путь к каталогу EPF/ERF (где `ObjectModule.bsl`) |
| `--kind` | enum | да | `ДополнительнаяОбработка`, `ДополнительныйОтчет`, `ЗаполнениеОбъекта`, `Отчет`, `ПечатнаяФорма`, `СозданиеСвязанныхОбъектов` + синонимы из skill §1 |
| `--target` | string, multi | для назначаемых | формат `Документ.СчетНаОплату`, `Справочник.Контрагенты` |
| `--command-type` | enum | нет | по умолчанию из таблицы skill §1 «Тип команды по умолчанию» |
| `--api-version` | string | нет | по умолчанию `"2.2.2.1"` |
| `--version` | string | нет | по умолчанию `"1.0"` |

### 2.3. Параметры `epf bsp-add-command`

| Параметр | Тип | Обяз. | Значения |
|----------|-----|-------|----------|
| `<epfPath>` | path | да | — |
| `--id` | string | да | `ЗаказПокупателя` |
| `--label` | string | да | представление команды |
| `--type` | enum | нет | определяется по существующему виду; см. skill §2 «Маппинг типов команд» |
| `--form` | string | для `ВызовКлиентскогоМетода` | имя формы (внутри `Forms/`), куда писать обработчик |

### 2.4. Затрагиваемые модули

| Файл | Изменение |
|------|-----------|
| `cli/Commands.java` | расширить кейс `case "epf"` — добавить ветки `case "bsp-init"`, `case "bsp-add-command"`. |
| `writer/EpfBspWriter.java` (новый) | методы: `String renderInfoFunction(BspKind kind, List<String> targets, BspCommandType cmdType, String version)`, `String renderHandlerProcedure(BspKind kind, BspCommandType cmdType, boolean isAssignable)`, `String renderCommandBlock(String id, String label, BspCommandType cmdType)`. |
| `editor/BslModuleEditor.java` (новый) | работа с BSL-модулем по областям: `insertIntoRegion(Path bsl, String regionName, String content, InsertPosition pos)`, `findFunction(Path bsl, String name)`, `appendBeforeReturn(Path bsl, String fnName, String content)`, `findOrCreateProcedure(...)`. Текстовый парсер по `#Область / #КонецОбласти`, `Процедура / КонецПроцедуры`. Сохраняет существующие отступы (табы) и кодировку. |
| `model/BspKind.java` (enum) | 6 видов + util: `requiresTarget()`, `defaultCommandType()`. |
| `model/BspCommandType.java` (enum) | 5 типов + util: `apiMethodName()`. |
| `model/BspTarget.java` (record) | `(String objectClass, String objectName)`, парсер `parse("Документ.СчетНаОплату")`. |

### 2.5. Шаблоны (вынести в `src/main/resources/templates/bsp/`)

- `info-function.bsl.template` — с плейсхолдерами `{{ВидОбработки}}`, `{{ТипКоманды}}`, `{{СЕКЦИЯ_НАЗНАЧЕНИЕ}}`, `{{СЕКЦИЯ_МОДИФИКАТОР}}`, `{{Версия}}`, `{{API_Версия}}`.
- `handler-server.bsl.template`, `handler-print.bsl.template`, `handler-client-global.bsl.template`, `handler-client-assignable.bsl.template`.
- `command-block.bsl.template` — для add-command.

Использовать `String.replace(...)` (не Velocity/Freemarker — лишняя зависимость).

### 2.6. Edge cases (из skill §1.4 и §2.4)

| Ситуация | Поведение |
|----------|-----------|
| `bsp-init` на модуль, где `СведенияОВнешнейОбработке` уже есть | Ошибка с подсказкой «используйте `bsp-add-command`». |
| `bsp-add-command` без существующей `СведенияОВнешнейОбработке` | Ошибка «сначала `bsp-init`». |
| Назначаемый вид без `--target` | Ошибка. |
| Глобальный вид с `--target` | Warning + игнорировать `target`. |
| Область `#Область ПрограммныйИнтерфейс` отсутствует | Создать в конце файла (с `#Область`/`#КонецОбласти` обёрткой). |
| Обработчик `ВыполнитьКоманду` уже есть | Добавить ветку `ИначеЕсли` перед `КонецЕсли` (см. skill §2). |
| `ВызовКлиентскогоМетода` без `--form` | Ошибка. |
| `ВызовКлиентскогоМетода` с `--form` для несуществующей формы | Ошибка с путём. |

### 2.7. Тесты

`src/test/java/io/github/onec/xmlgen/writer/EpfBspWriterTest.java` и `editor/BslModuleEditorTest.java`:

- `testRenderInfoFunction_PrintForm_HasNaznachenieAndModifier` — `ПечатнаяФорма` + один target → результат содержит `Назначение.Добавить("...")` + `Модификатор = "ПечатьMXL"`.
- `testRenderInfoFunction_GlobalProcessor_NoNaznachenie` — `ДополнительнаяОбработка` без `target` → нет секции назначения.
- `testBspInit_KindMappingFromSynonyms` — параметризованный по таблице skill §1.
- `testBspInit_OnAlreadyRegisteredModule_Errors`.
- `testBspAddCommand_ServerType_AppendsBeforeReturn` — новая команда вставлена перед `Возврат ПараметрыРегистрации`.
- `testBspAddCommand_ServerType_AppendsBranchToExistingHandler` — `ИначеЕсли` добавлена в существующую `ВыполнитьКоманду`.
- `testBslModuleEditor_PreservesIndentationAndBom` — табы и BOM сохранены.

### 2.8. Acceptance

- Сгенерированный BSL проходит синтаксическую проверку (smoke через `v8-runner syntax-check` — отдельный manual-тест в README).
- На реальной EPF (тест-фикстура) после `bsp-init` + 2× `bsp-add-command` функция `СведенияОВнешнейОбработке` имеет правильную структуру.

---

## 3. Template/Help operations (P1) — `template add` / `remove` / `add-help`

**Контракт:** [skill: epf-full/references/templates.md](../../framework/skills/tool-usage/platform-data/xml-generation/epf-full/references/templates.md).

### 3.1. Текущее состояние

В `Commands.java` уже есть `case "template"` (используется `epf add-template`). Универсальный `template add/remove/add-help` для произвольных объектов метаданных — отсутствует.

### 3.2. CLI

```
xml-gen template add    --object <Type.Name> --name <T> --type <TemplateType> [--synonym <S>] [--src <dir>] [--set-main-dcs] <configDir>
xml-gen template remove --object <Type.Name> --name <T> [--src <dir>] <configDir>
xml-gen template add-help --object <Type.Name> [--lang ru] [--src <dir>] <configDir>
```

### 3.3. Затрагиваемые модули

| Файл | Изменение |
|------|-----------|
| `cli/Commands.java` | расширить `case "template"` → подкоманды `add`, `remove`, `add-help`. |
| `writer/TemplateWriter.java` (новый или расширение `EpfWriter`'а) | методы: `addTemplate(Path configDir, MdoPath object, String name, TemplateType type, String synonym, boolean setMainDcs)`, `removeTemplate(...)`, `addHelp(Path configDir, MdoPath object, String lang)`. |
| `model/TemplateType.java` (enum, mdclasses может уже иметь — переиспользовать) | `HTMLDocument`, `TextDocument`, `SpreadsheetDocument`, `BinaryData`, `DataCompositionSchema`. |
| `model/MdoPath.java` | переиспользовать существующее (или создать), парсер `Document.ЗаказКлиента`. |
| `model/ObjectXmlEditor.java` (есть `ObjectContainerEditor.java` — переиспользовать/расширить) | методы: `addTemplateRegistration(Path objectXml, String templateName)`, `removeTemplateRegistration(...)`, `setMainDataCompositionSchema(Path reportXml, String templateName)`, `clearMainDataCompositionSchema(...)`, `addIncludeHelpInContents(Path formXml)`. |

### 3.4. Минимальные XML-шаблоны

В `src/main/resources/presets/template/`:

- `template-meta.xml.template` — `<Template>` метаданные (UUID, синоним, тип).
- `minimal-spreadsheet.xml` — пустой `SpreadsheetDocument`.
- `minimal-dcs.xml` — пустая `DataCompositionSchema`.
- `help-meta.xml.template` — `<Help>` с языками.
- `help.ru.html.template` — стартовый HTML.

### 3.5. Edge cases

| Ситуация | Поведение |
|----------|-----------|
| `template add` на несуществующий объект (нет `<Type>/<Name>.xml`) | Ошибка с путём, который ищется. |
| Тип не поддерживается объектом (например `DataCompositionSchema` не для `InformationRegister`) | Warning, операция продолжается (но это «не типично»). Идея: matrix supported types — см. skill §«Поддерживаемые типы». |
| `MainDataCompositionSchema` уже заполнен, `--set-main-dcs` не передан | Не перезаписывать (как написано в skill). |
| `remove` несуществующего макета | Warning + noop, success. |
| `add-help` на объект без форм | Файлы создать, в формы ничего не дописывать. |
| `add-help` повторно для того же языка | Idempotent: файл `<lang>.html` не перезаписывать (warning). |
| Имя макета без `ПФ_` префикса при `--type SpreadsheetDocument` | Warning «обычно для печатных форм используется префикс `ПФ_`», но операцию выполнить как указано. (Skill говорит «добавь автоматически и сообщи» — оставляем за пользователем, не магичим.) |

### 3.6. Тесты

`src/test/java/io/github/onec/xmlgen/writer/TemplateWriterTest.java`:

- `testAddTemplate_SpreadsheetForDocument_CreatesStructureAndRegisters`.
- `testAddTemplate_DcsForReport_SetsMainWhenFlag`.
- `testAddTemplate_DcsForReport_KeepsExistingMainWithoutFlag`.
- `testRemoveTemplate_ClearsRegistrationAndDeletesFiles`.
- `testRemoveTemplate_OnMainDcs_ClearsMainDcsAttribute`.
- `testAddHelp_CreatesHelpXmlAndHtml`.
- `testAddHelp_WithForms_AddsIncludeHelpInContents`.
- `testAddHelp_RepeatSameLang_Idempotent`.

---

## 4. Interface operations (P2) — `interface edit/validate` расширение

**Контракт:** [skill: interface-operations/SKILL.md](../../framework/skills/tool-usage/platform-data/xml-generation/interface-operations/SKILL.md).

### 4.1. Текущее состояние

`InterfaceEditor.java` уже есть (321 строка), в `Commands.java` уже `case "interface" → edit/validate` со sub-операциями `hide/show/place/order/subsystem-order/group-order`. Нужно проверить покрытие против skill и добить недостающее.

### 4.2. Маппинг операций skill ↔ существующих

| Skill-операция | Существующий код | Действие |
|----------------|------------------|----------|
| `hide <Command>` | `case "hide"` | ✅ Готово (проверить). |
| `show <Command>` | `case "show"` | ✅ Готово. |
| `place <Command> --to <Group>` | `case "place"` | Проверить, что поддерживает `--to`. |
| `set-order <Cmd1,Cmd2,...> --group <G>` | `case "order"` | Привести имя к канону: skill использует `set-order`, код — `order`. Добавить алиас. |
| `set-subsystem-order` | `case "subsystem-order"` | Добавить алиас `set-subsystem-order`. |
| `set-group-order` | `case "group-order"` | Добавить алиас `set-group-order`. |

### 4.3. Валидация

`InterfaceValidator.java` уже есть. Сверить классы ошибок против skill: must check команда из CommandInterface существует в метаданных, группа существует, нет циклов в `Order` и т.п.

### 4.4. Тесты

`src/test/java/io/github/onec/xmlgen/editor/InterfaceEditorTest.java` (создать если нет):

- happy paths по каждой операции.
- aliases работают.
- order на несуществующую команду → внятная ошибка.

### 4.5. Объём

Малый — основная работа уже сделана; нужны только проверка соответствия канону имён и недостающие негативные тесты.

---

## 5. SKD edit patch operations (P1, крупный) — полный набор `skd edit`

**Контракт:** [skill: skd-edit/SKILL.md](../../framework/skills/tool-usage/platform-data/xml-generation/skd-edit/SKILL.md) + 5 references (`fields.md`, `parameters.md`, `totals.md`, `structure.md`, `query.md`).

### 5.1. Текущее состояние

`SkdEditor.java` — 92 строки. Существенно меньше требуемого. По skill нужно реализовать ~20 операций:

| Группа | Операции |
|--------|----------|
| Поля | `add-field`, `modify-field`, `remove-field`, `set-field-role` |
| Параметры | `add-parameter`, `modify-parameter`, `remove-parameter`, `rename-parameter`, `reorder-parameters` |
| Итоги | `add-total`, `remove-total` |
| Структура | `modify-structure` |
| Запрос | `set-query`, `patch-query` (с `@once`) |
| CA | `clear-conditionalAppearance` |

### 5.2. CLI

```
xml-gen skd edit <SchemaPath> <operation> "<value>" [--dataSet <name>] [--variant <name>] [--no-selection]
```

Batch через `;;` для всех кроме `set-query`, `patch-query` (без `@once`), `modify-structure`.

### 5.3. Затрагиваемые модули

| Файл | Изменение |
|------|-----------|
| `cli/Commands.java` | расширить ветку `case "skd"` → `case "edit"`. Парсер диспатчит operation. |
| `editor/SkdEditor.java` | расширить — каждая операция отдельным методом. Использует DOM (preserve formatting через `XmlDocumentWriter` + `ByteSafeFileHandler`). |
| `editor/skd/SkdShorthandParser.java` (новый) | парсеры shorthand-форм:<br>– `parseField("Имя [Заголовок]: тип @роль #ограничение")` → `FieldDescriptor`<br>– `parseParameter("Имя [Заголовок]: тип = значение [availableValue=…] [@флаги]")`<br>– `parseTotal("dataPath: выражение")` — с автообёрткой агрегатов<br>– `parseFieldRole("dataPath [@флаги] [kv=значение]")`<br>– `parseStructureSpec("Поле1, Поле2 @name=Группа")`<br>– `splitBatch(value)` → List, учитывая что `set-query` не разбивается |
| `editor/skd/SkdTypeParser.java` (новый) | парсер типов: `string(N)`, `decimal(N,M)`, `decimal(N,M),nonneg`, `date`, `boolean`, `uuid`, `ref:Catalog.X`, составные типы через `\|`. |
| `editor/skd/PatchQueryEngine.java` (новый) | `replace(text, from, to, OnceMode)` — для `@once` падает если 0 или ≥2 вхождений. |
| `validator/SkdValidator.java` | расширить — после каждой operation проверять well-formedness. |

### 5.4. Грамматика shorthand-форм (формализованно)

Из skill (полная):

```ebnf
field             ::= name ws? ('[' label ']')? ws? ':' ws? typeSpec (ws role)? (ws constraint)?
typeSpec          ::= primitive ('(' digit+ (',' digit+)? ')')? (',' qualifier)? ('|' typeSpec)*
primitive         ::= 'string' | 'decimal' | 'date' | 'boolean' | 'uuid' | 'ref:' qualifiedName
qualifier         ::= 'nonneg'
role              ::= '@account' | '@balance' | '@period' | '@dimension' | '@resource'
constraint        ::= '#' propertyAssignmentList

parameter         ::= name ws? ('[' label ']')? ws? ':' ws? typeSpec (ws '=' ws value)? (ws kv)* (ws flag)*
flag              ::= '@hidden' | '@always' | '@autoDates' | '@valueList'
kv                ::= identifier '=' value

fieldRoleSpec     ::= dataPath (ws role)? (ws flag)* (ws kv)*
totalSpec         ::= dataPath ws? ':' ws? expression  // expression: 'Сумма', 'Среднее', or custom BSL/SKD expr
structureSpec     ::= fieldList ws '@name=' identifier  // group rename target
patchQuerySpec    ::= oldText ws? '=>' ws? newText (ws '@once')?
renameParamSpec   ::= oldName ws '=>' ws newName
```

Парсер должен возвращать структурированные ошибки с указанием позиции в строке (типа `unexpected token at column 12: expected ':'`).

### 5.5. Атомарность и backup

- Перед записью — резервная копия в `<file>.bak.<timestamp>` (опционально, через флаг `--backup`). По умолчанию off.
- При сбое (валидация после изменения упала) — не записывать, восстановить исходник из in-memory копии.

### 5.6. Idempotency и duplicates

| Операция | Идемпотентна | Поведение на дубль |
|----------|--------------|---------------------|
| `add-field "X"` | нет | warning + skip, exit 0 |
| `modify-field "X"` | по сути да | если нет — warning + skip |
| `remove-field "X"` | да | если нет — warning + noop |
| `set-field-role "X @balance ..."` | да | повторное применение тех же ролей — noop |
| `clear-conditionalAppearance "*"` | да | на пустом CA — noop |
| `rename-parameter` | нет | если нет старого имени — error |
| `reorder-parameters "A,B,C"` | да | если состав совпадает — noop |

### 5.7. Edge cases ключевые

| Ситуация | Поведение |
|----------|-----------|
| `--variant` не указан, в схеме несколько вариантов | Берём первый, warning «варианты: X, Y, Z; взят X». |
| `--dataSet` не указан, несколько наборов | Аналогично. |
| `add-field` без `--no-selection`, но `selection` не существует в варианте | Создать пустой `selection`. |
| `patch-query` без `@once`, 0 совпадений | error: «no matches». |
| `patch-query @once`, 2+ совпадений | error: «multiple matches: file unchanged». |
| `set-query "@queries/sales.sql"` | прочитать файл, проверить UTF-8, использовать как тело. Не trim trailing newline. |
| `add-total "Сумма: Среднее"` | автообёртка → `Среднее(Сумма)` в `Expression`. |
| `add-total "Сумма: Сумма(Цена * Количество)"` | если уже агрегат вокруг expression — не оборачивать. |
| `modify-structure "X,Y @name=Group"` без существующей группы с этим `@name` | error. |
| `availableValue=...` в `modify-parameter` | **полная замена** старого списка. |
| Batch с одной операцией упавшей в середине | rollback всего batch (transactional). |

### 5.8. Тесты

`src/test/java/io/github/onec/xmlgen/editor/SkdEditorTest.java` — расширить (есть `SkdEditorTest.java` по wc -l из изначального обзора):

- ≥ 1 happy + 1 negative + 1 idempotency на КАЖДУЮ из 17 операций.
- `testBatch_RollbackOnMidFailure`.
- `testPatchQuery_OnceFailsOnDoubleMatch`.
- `testShorthandParser_AllGrammarExamplesFromSkill`.
- `testSetFieldRole_BalanceWithKvArgs_ProducesCorrectXml`.

### 5.9. Acceptance

- Все примеры из skill `references/*.md` (там есть end-to-end сниппеты) выполняются на тест-фикстуре и дают ожидаемое XML.
- Существующие SKD-тесты остаются зелёными.

---

## 6. SKD DSL extension (P1, крупный) — расширение `skd compile`

**Контракт:** [skill: skd-dsl/SKILL.md](../../framework/skills/tool-usage/platform-data/xml-generation/skd-dsl/SKILL.md) + `references/templates-dsl.md`, `info-modes.md`.

### 6.1. Текущее состояние

- `SkdDsl.java` — 271 строка. Существующее покрытие (по README): DataSetQuery, parameters, totalFields, settingsVariants, Filter (11 операторов), Order, ConditionalAppearance, Structure.
- Из skill требуется ещё: **DataSetObject**, **DataSetUnion**, **calculatedFields**, **templates DSL** (`rows/style/widths/{param}/|/>`), **groupTemplates**, **drilldown**, **расширенная типизация** (`decimal(N,M)`, `,nonneg`, составные типы через массив), **роли полей** (`@account`/`@balance`/`@period` + `kv`), **`@autoDates`/`@hidden`/`@valueList`/`@always`**, **`availableValues`**, **`@file`-include**, **`dataSetLinks`**, **`presentationExpression`**, **`conditionalAppearance` с группами Or/And/Not**.

### 6.2. Затрагиваемые модули

| Файл | Изменение |
|------|-----------|
| `dsl/SkdDsl.java` | расширить — добавить POJO для всех новых концепций. Структура (примерно): `DataSet { type: query\|object\|union, name, query?, object?, sourceDataSets?, fields, calculatedFields }`, `Template { name, rows[], widths[], style? }`, `DrillDown { ... }`, `DataSetLink { sourceDataSet, destDataSet, sourceExpression, destExpression }`. |
| `dsl/SkdTypeSpec.java` (новый) | парсер расширенного type-spec для JSON-полей: `"decimal(15,2),nonneg"`, массивы для составных типов. |
| `writer/SkdWriter.java` | расширить — генерация XML для всех новых конструкций. Использовать `mdclasses` enum-ы `DataSetType.QUERY/OBJECT/UNION` (уже импортирован). |
| `writer/skd/SkdTemplateWriter.java` (новый) | сериализация templates DSL: `rows`, `widths` (с диапазонами), `groupTemplates`. Аналог `MxlWriter`'у по простоте. |
| `writer/skd/SkdFieldRoleWriter.java` (новый) | генерация `<Field>` с ролями (`@account`, `@balance balanceGroupName=...`, `@period`, `@dimension`, `@resource`). |
| `model/SkdInclude.java` (новый) | резолвер `@file:path` — для `query`, `presentationExpression`, можно для других строковых полей. |
| `validator/SkdValidator.java` | расширить — проверять ссылочную целостность (calculatedFields ссылаются на существующие поля, dataSetLinks указывают существующие наборы, drilldown ссылается на варианты). |

### 6.3. Грамматика расширений (JSON, не shorthand)

Пример (выдержка из skill):

```json
{
  "name": "Продажи",
  "dataSets": [
    {
      "type": "query",
      "name": "Основной",
      "query": "@queries/sales.sql",
      "fields": [
        { "name": "Сумма", "label": "Сумма", "type": "decimal(15,2),nonneg", "role": "@resource" },
        { "name": "Период", "type": "date", "role": "@period" }
      ],
      "calculatedFields": [
        { "name": "СуммаСНДС", "expression": "Сумма * 1.2", "type": "decimal(15,2)" }
      ]
    },
    {
      "type": "object",
      "name": "Доп",
      "objectName": "ВнешнийНабор",
      "fields": [...]
    },
    {
      "type": "union",
      "name": "Все",
      "sourceDataSets": ["Основной", "Доп"]
    }
  ],
  "dataSetLinks": [
    { "source": "Основной", "dest": "Доп",
      "sourceExpression": "Контрагент", "destExpression": "Контрагент" }
  ],
  "templates": [
    { "name": "ИтогПоГруппе",
      "type": "group",
      "rows": [
        { "cells": [ { "type": "param", "name": "Группировка" }, { "type": "text", "value": "Итого:" }, { "type": "param", "name": "Сумма", "format": "ЧДЦ=2" } ] }
      ],
      "widths": [15, 30, 12] }
  ]
}
```

### 6.4. `@file:`-include

В строковых полях (`query`, `presentationExpression` и др.) поддержать `"@queries/sales.sql"` → читать файл относительно директории JSON-файла (или `--include-base` если указан).

### 6.5. Edge cases

| Ситуация | Поведение |
|----------|-----------|
| `dataSetLink` ссылается на несуществующий `dataSet` | error |
| `calculatedField.type` отсутствует | error «type required» (в отличие от обычного поля, где можно вывести из запроса — у вычисляемых вывести нельзя). |
| Type-spec `decimal(15,2),nonneg` | qualifier добавляет `AllowedSign=Nonnegative`. |
| Type-spec `decimal(15,2)|string(50)` | составной тип, генерация `<Type>` с двумя дочерними. |
| `availableValues: ["А", "Б"]` для строкового параметра | список через `<AvailableValues>`. |
| `templates.widths: [15, "10-20", 30]` | средний — diapason (см. mxl-dsl канон). |
| Циклическая зависимость в `dataSetLinks` | error на этапе валидации. |

### 6.6. Тесты

`src/test/java/io/github/onec/xmlgen/writer/SkdWriterTest.java` — расширить:

- по 1 тесту на каждое новое концепт-поле.
- e2e тест: JSON из skill quickstart → ожидаемый Schema.xml.

### 6.7. Acceptance

- Все примеры JSON из `skd-dsl/SKILL.md` и `references/templates-dsl.md` компилируются успешно.
- `skd compile` + `skd info --mode full` (см. §7) на скомпилированном файле — никаких ошибок и предупреждений.

---

## 7. SKD info 11 modes (P1) — `skd info --mode <m>`

**Контракт:** [skill: skd-dsl/references/info-modes.md](../../framework/skills/tool-usage/platform-data/xml-generation/skd-dsl/references/info-modes.md).

### 7.1. Зависимость

Режим `trace` требует модели данных, расширенной в §6 (DataSets с типами Query/Object/Union, calculatedFields, ресурсы). Поэтому **строго после §6**.

### 7.2. CLI

```
xml-gen skd info <SchemaPath> [--mode <m>] [--dataSet <name>] [--variant <name>]
```

Если `--mode` не указан — `overview`.

### 7.3. Режимы (11)

| Режим | Что показывает |
|-------|----------------|
| `overview` | DataSets (тип, имя), параметры (имя/тип/required), вычисляемые поля, варианты — сводно |
| `query` | текст запроса по `--dataSet` (или всех, если не указан) |
| `fields` | все поля dataset-а: имя, тип, роль, источник (запрос/калькуляция) |
| `links` | dataSetLinks — связи между наборами |
| `calculated` | calculatedFields: имя, выражение, тип |
| `resources` | поля с ролью `@resource`, агрегаты, выражения totalFields |
| `params` | параметры: имя, тип, default, availableValues, флаги |
| `variant` | settingsVariants: имя, selection, filter, order, ca |
| `templates` | groupTemplates + customTemplates |
| `trace` | цепочка «набор → calculatedField → resource/total → вариант selection». Граф вывода. |
| `full` | все вышеперечисленное последовательно |

### 7.4. Затрагиваемые модули

| Файл | Изменение |
|------|-----------|
| `cli/Commands.java` | парсинг `--mode` в ветке `case "info"` для skd. |
| `info/SkdInfoPrinter.java` | расширить — добавить методы `printOverview()`, `printQuery(...)`, `printFields(...)`, …, `printTrace(...)`. Существующий код — 526 строк, в нём какой-то базовый info уже есть, рефакторим под mode-диспатч. |
| `info/skd/SkdTraceBuilder.java` (новый) | построение цепочки набор → calc → resource → variant. |

### 7.5. Формат вывода

Plain text (markdown-подобный, без таблиц HTML). По образцу существующих info-команд.

### 7.6. Тесты

`src/test/java/io/github/onec/xmlgen/info/SkdInfoPrinterTest.java`:

- 1 тест на каждый mode + snapshot expected output.

---

## 8. MXL full rewrite (P1, крупный) — переработка под канон Широкова

**Контракт:** [skill: mxl-dsl/SKILL.md](../../framework/skills/tool-usage/platform-data/xml-generation/mxl-dsl/SKILL.md) + `references/dsl-spec.md`, `info-modes.md`, `validate-classes.md`.

### 8.1. Текущее состояние и canon-диффы

Текущий `MxlWriter.java` (433 строки) и `MxlDsl.java` (197 строк) сделаны до канона Широкова и **не совместимы** с новой спецификацией по ряду пунктов:

| Текущее | Канон Широкова |
|---------|----------------|
| Ячейки задаются позиционно или через row/col | `col` 1-based позиционирование. Пустые ячейки явно через `empty: N`. |
| Стили задаются глобально/inline | `rowStyle` с автозаполнением: если у ячейки нет своего стиля — наследуется от `rowStyle`. |
| Объединение через `colspan` | Добавляется `rowspan`. |
| Ширины задаются числом | `columnWidths` с диапазонами и `"Nx"` (повторитель: `"3x15"` = 3 столбца по 15). |
| Page setup инлайн или отсутствует | `page A4-landscape|A4-portrait` с автошириной. |
| Типы ячеек по полю `text/parameter` | Каноны: `text`, `param`, `template` + флаг `detail` (детальная запись), `format` (`"ЧДЦ=2"`, `"ДФ=..."`), `wrap`, `underline`, `strikeout`. |

### 8.2. Стратегия миграции (не ломая существующее)

1. **Новый DSL парсер** — `MxlDslV2.java`, активируется при наличии маркера `"$schema": "mxl-v2"` в JSON-корне (или CLI-флагом `--canon shirokov`).
2. **Текущий парсер** остаётся для обратной совместимости. Помечается deprecated в README.
3. **Полная миграция** через 1 версию: после релиза `0.3.0` старый парсер можно удалить.

Этот пункт обсуждается с пользователем (см. §11.2 «Open questions»). Может оказаться, что нужен hard cut без back-compat.

### 8.3. Затрагиваемые модули

| Файл | Изменение |
|------|-----------|
| `cli/Commands.java` | `case "mxl"` → `compile`, `decompile`, `info`, `validate`. Парсинг `--canon`, `--format designer\|edt`. |
| `dsl/MxlDslV2.java` (новый или замена `MxlDsl.java`) | POJO под канон. |
| `writer/MxlWriterV2.java` (новый) или рефактор `MxlWriter.java` | сериализация под канон. |
| `model/mxl/ColumnWidthSpec.java` (новый) | парсер `[15, "3x10", "10-20"]`. |
| `model/mxl/RowStyle.java` (новый) | стиль строки + наследование. |
| `model/mxl/PageSetup.java` (новый) | enum `A4_LANDSCAPE` / `A4_PORTRAIT` + автоширина. |
| `info/MxlDecompiler.java` | переписать под канон (auto-naming стилей: `Style1`, `Style2`...). |
| `info/MxlInfoPrinter.java` | 4 типа областей (Rows/Columns/Rectangle/Drawing). |
| `validator/MxlValidator.java` | 7 классов ошибок (см. skill `references/validate-classes.md`). |

### 8.4. Грамматика канона (JSON, выдержка)

```json
{
  "page": "A4-landscape",
  "columnWidths": [15, "3x10", "10-20"],
  "rowStyle": { "font": "Arial 10", "border": "all", "wrap": true },
  "areas": [
    { "name": "Заголовок", "type": "rows", "rows": [
        { "cells": [
          { "col": 1, "type": "text", "value": "Отчёт", "bold": true, "underline": true },
          { "col": 2, "type": "param", "name": "Период", "format": "ДФ='dd.MM.yyyy'" }
        ]},
        { "empty": 1 },
        { "cells": [ { "col": 1, "type": "text", "value": "Колонка", "rowspan": 2 } ] }
      ]
    }
  ]
}
```

### 8.5. Validate — 7 классов ошибок

(из skill `references/validate-classes.md`)

1. **Out-of-bounds column** — `col` > длины `columnWidths`.
2. **Overlapping cells** — две ячейки на одной позиции.
3. **Rowspan beyond area** — `rowspan` уводит за пределы строк области.
4. **Unknown parameter name** — `type: param`, `name` без соответствия (через linked source).
5. **Format mismatch** — `format: "ЧДЦ=2"` на нечисловой ячейке.
6. **Page size impossible** — суммарная ширина `columnWidths` не помещается в выбранный `page`.
7. **Style reference broken** — ссылка на именованный стиль, которого нет.

### 8.6. Decompile auto-naming стилей

При декомпиляции существующего MXL — собрать уникальные стили, дать имена `Style1, Style2, ...`. Если стиль явно именован в исходнике — сохранить имя.

### 8.7. Тесты

`src/test/java/io/github/onec/xmlgen/writer/MxlWriterTest.java` + новые тесты на validator/info:

- по каждому канону-различию из таблицы §8.1 — отдельный тест.
- snapshot сравнение JSON ↔ XML round-trip (compile → decompile → compile дает идентичный XML).
- по каждому из 7 классов ошибок validator — happy + failure case.

### 8.8. Acceptance

- Реальные MXL-фикстуры из 1С-конфигураций (типовые ПФ) — декомпилируются и снова компилируются без потерь.
- Existing MXL tests — зелёные (старый парсер сохранён до next major).

---

## 9. Meta batch-patch (P2) — `meta edit --batch <file.json>`

**Контракт:** [skill: meta-operations/references/batch-patch.md](../../framework/skills/tool-usage/platform-data/xml-generation/meta-operations/references/batch-patch.md).

### 9.1. Текущее состояние

В `Commands.java` ветка `case "meta"` существует, операции inline (`--op`, `--value`, `;;`) для одиночных правок есть. Нет:
- `--batch <file.json>` — пакет операций из файла.
- composite types в inline syntax.
- MLText editing (Synonym ru/en).
- `modify-tabularSection` на табчасть.
- `add-property`/`modify-property`.

### 9.2. CLI

```
xml-gen meta edit <objectXml> --batch <file.json>
xml-gen meta edit <objectXml> --op <op> --value "<v>"  # existing
```

### 9.3. Формат batch JSON

```json
{
  "operations": [
    { "op": "modify-property", "name": "Synonym", "value": { "ru": "Контрагенты", "en": "Counterparties" } },
    { "op": "add-attribute", "name": "Комментарий", "type": "string(255)" },
    { "op": "modify-attribute", "name": "ИНН", "synonym": { "ru": "ИНН" }, "fillChecking": "ShowError" },
    { "op": "modify-tabularSection", "name": "Контакты",
      "operations": [
        { "op": "add-attribute", "name": "Тип", "type": "EnumRef.ТипКонтакта" }
      ]
    },
    { "op": "set-property", "name": "BasedOn", "value": ["Documents.СчётНаОплату"] }
  ]
}
```

### 9.4. Затрагиваемые модули

| Файл | Изменение |
|------|-----------|
| `cli/Commands.java` | парсинг `--batch`. |
| `writer/MetaEditor.java` | новые операции `modify-tabularSection`, `add-property`, MLText editing. |
| `dsl/MetaBatchDsl.java` (новый) | POJO для batch JSON. |
| `model/MlText.java` (новый) | работа с MLText: парсинг `{"ru":"...", "en":"..."}` в XML `<Content xml:lang="ru">...</Content>`. |
| `model/CompositeType.java` (новый) | парсер составных типов в shorthand `"string(50)|number(15,2)|DocumentRef.Order"` → `<Type>` XML. |

### 9.5. Транзакционность

Весь batch — atomic. Если operation #N упала — rollback всех предыдущих (in-memory copy объекта XML; пишем только в самом конце).

### 9.6. Тесты

`src/test/java/io/github/onec/xmlgen/writer/MetaEditorTest.java` — расширить:

- `testBatch_AllOperations_Succeeds`.
- `testBatch_FailureMidway_NoFileChange`.
- `testMlText_EditSynonym_PreservesOtherLangs`.
- `testCompositeType_Inline_GeneratesCorrectXml`.

---

## 10. Form validate DataPath (P2, малый) — резолв `Items.X.CurrentData.*`

**Контракт:** [skill: forms-toolkit/references/validate.md](../../framework/skills/tool-usage/platform-data/xml-generation/forms-toolkit/references/validate.md).

### 10.1. Текущее состояние

`FormValidator.java` — 928 строк, валидирует структуру формы и DataPath. Не резолвит:
- `Items.<TableName>.CurrentData.*` — должен резолвиться против реквизита таблицы.
- `~<Attr>.*` — относительная ссылка от текущего контекста (Item с DataPath = X).
- Числовые индексы (`[0]`, `[42]`) и UUID-индексы — silent skip (не считать ошибкой).

### 10.2. Затрагиваемые модули

| Файл | Изменение |
|------|-----------|
| `validator/FormValidator.java` | расширить резолвер DataPath. |
| `validator/form/DataPathResolver.java` (выделить из FormValidator) | методы: `resolve(String dataPath, FormContext ctx)`, `resolveItemsCurrentData(String tableName, String tail, FormContext ctx)`, `resolveTilde(String attrChain, FormContext ctx)`. |
| `validator/form/FormContext.java` (выделить) | контекст: формы attribute tree + items map. |

### 10.3. Алгоритм резолва

```
input: dataPath, ctx (form attributes, items)

1. Если dataPath.startsWith("Items.")
     parts = dataPath.split(".")
     if parts[2] == "CurrentData"
        tableName = parts[1]
        attrChain = parts[3:]
        ref = ctx.attributes.findCollectionAttr(tableName)
        if ref == null: ERROR "Unknown items table: " + tableName
        return resolveAgainstAttr(attrChain, ref)

2. Если dataPath.startsWith("~")
     attrName = dataPath[1..].split(".")[0]
     tail     = ...[1:]
     # для elements с DataPath = X.Y, "~Z" ≡ "X.Z" (относительно последнего реквизита)
     # contextAttr = currentItem.dataPath.split(".")[0]
     return resolve(contextAttr + "." + dataPath[1:], ctx)

3. Если segment numeric или UUID-shaped
     silent skip — не валидируем дальше; success.

4. Default — текущая логика FormValidator.
```

### 10.4. Тесты

`src/test/java/io/github/onec/xmlgen/validator/FormValidatorTest.java` — добавить:

- `testDataPath_ItemsCurrentData_ResolvesAgainstTableAttribute`.
- `testDataPath_Tilde_ResolvesRelativeToCurrentItem`.
- `testDataPath_NumericIndex_SilentSkip`.
- `testDataPath_UuidIndex_SilentSkip`.
- `testDataPath_ItemsCurrentDataOnNonTable_Errors`.

---

## 11. Тест-стратегия и acceptance gates

### 11.1. Per-pull-request gates

Каждый pull request с реализацией одного из пунктов §1–§10 этого SPEC обязан:

1. **Сборка** — `./gradlew clean build` зелёный.
2. **Тесты** — все юнит-тесты зелёные, новые тесты добавлены (см. требования по §-ам).
3. **README** — обновить `tools/xml-gen/README.md` (Status of implementation, новые команды в Usage).
4. **CHANGELOG** — `tools/xml-gen/CHANGELOG.md` (создать если нет, добавить запись `## [unreleased] - <дата>` → пункт §9 закрыт).
5. **mapping doc** — `docs/specs-and-analisys/external-skills-mapping.md`: соответствующая строка в §9 → 🔧 снят, статус `✅ Adopted`. Дата в §8 changelog (rev.X+1).
6. **Skill-документ** — если контракт меняется → обновить SKILL.md → `python3 tools/sync-skill.py <path>`.

### 11.2. Open questions (требуют решения пользователя)

1. **§8 MXL миграция** — hard cut (ломаем старый формат) или back-compat (`--canon shirokov` флаг + двойной кодекс)?
   - **Установлено (2026-05-21):** текущий парсер — наш собственный (Map-based `columnWidths: {"1": 15, "2-8": 40}`, именованные `fonts/styles` через Map, `columns` + `defaultWidth`). НЕ копия Широкова. Канон Широкова: array-based `columnWidths: [15, "3x10", "10-20"]`, positional `col` 1-based в ячейках, `rowStyle` с наследованием. Это **существенные расхождения**, перенос — переписывание.
   - **Решение пользователя:** перед стартом §8 — провести подробное сравнение «наш MXL DSL vs канон Широкова» (отдельный документ `docs/specs-and-analisys/mxl-canon-comparison.md`). Только после сравнения принимать решение hard cut vs back-compat.
   - **Статус §8 в этом SPEC:** отложен до comparison-документа. Не входит в скоуп «малые+средние».
2. **Версионирование xml-gen** — после P0+P1: `0.2.0` или `1.0.0`? Зависит от §8.1 (hard cut → bump major).
3. **Команда `epf-bsp` vs `epf bsp-*`** — текущий SPEC предлагает второе. Если пользователь хочет верхне-уровневую команду — изменить §2.1.
4. **`bsl-generation` модуль** для генерации BSL-тел в §1 patch-method — выделять в отдельный пакет или хватит текстовых шаблонов?
5. **Линтер shorthand-грамматик** (§5 + §6) — стоит ли вынести в общий `model/shorthand/` пакет или каждый редактор имеет свой парсер?

### 11.3. Порядок реализации (рекомендуемый)

| Шаг | Пункт | Зачем именно здесь |
|-----|-------|---------------------|
| 1 | §10 Form validate DataPath | Малый, изолированный, прогрев на тест-стиле проекта. |
| 2 | §4 Interface operations | Малый, в основном aliases + тесты. |
| 3 | §2 EPF БСП-обвязка **(P0)** | Принципиальный — разблокирует БСП-сценарии. |
| 4 | §3 Template/Help | Средний, переиспользует часть из EPF/MetaEditor. |
| 5 | §1 CFE CLI extensions | Средний, использует `BslModuleEditor` из §2. |
| 6 | §9 Meta batch-patch | Средний, на готовом `MetaEditor` + новый JSON DSL. |
| 7 | §5 SKD edit | Крупный, но **независим**, можно вести параллельно с §6. |
| 8 | §6 SKD DSL | Крупный, **разблокирует §7**. |
| 9 | §7 SKD info | Средний, после §6. |
| 10 | §8 MXL full rewrite | Крупный, **последним** — требует решения по миграции (§11.2 #1). |

Шаги 7 и 8 можно вести параллельно разными агентами/инженерами (один из них автор, другой — ревьюер). Остальные — последовательно.

### 11.4. Оценка трудоёмкости

| Пункт | Объём | Оценка (часов человеко-работы для senior Java + 1С knowledge) |
|-------|-------|----------------------------------------------------------------|
| §10 Form validate | малый | 4–6 |
| §4 Interface | малый | 4–6 |
| §2 EPF БСП | средний | 16–24 |
| §3 Template/Help | средний | 16–24 |
| §1 CFE CLI | средний | 20–28 |
| §9 Meta batch | средний | 16–24 |
| §5 SKD edit | крупный | 60–80 |
| §6 SKD DSL | крупный | 60–80 |
| §7 SKD info | средний | 16–24 |
| §8 MXL rewrite | крупный | 60–80 |
| **Итого** | | **~280–380 часов** (~7–10 человеко-недель) |

### 11.5. Что НЕ покрывает этот SPEC

- **Интеграционные e2e-тесты с реальной 1С:Предприятие** — пишутся отдельно, в `framework/skills/...` сценариях через `v8-runner`.
- **Производительность** — нагрузочное тестирование на крупных конфигурациях (>1000 объектов). Делается отдельно, если выявится регрессия.
- **Документация на русском** — README.md остаётся на русском, новые секции — на русском.
- **Покрытие через `xml-gen --help`** — не забыть обновить help-text каждой команды.

---

## 12. Связанные документы

- [external-skills-mapping.md](external-skills-mapping.md) — общий маппинг заимствованных навыков (§9 — источник этого SPEC).
- [SPEC-cfe-cli-extension.md](SPEC-cfe-cli-extension.md) — полная спека §1.
- Skill-документы во `framework/skills/tool-usage/platform-data/xml-generation/` — авторитетные контракты поведения.
- `tools/xml-gen/SPEC-003-VALIDATOR.md`, `SPEC-004-EDITOR.md`, `SPEC-005-VISUAL-VALIDATION.md`, `SPEC-006-FROM-OBJECT.md` — предыдущие spec-документы по xml-gen фазам.
