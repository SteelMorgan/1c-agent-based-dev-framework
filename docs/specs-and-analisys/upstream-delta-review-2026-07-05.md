# Upstream delta review — 2026-07-05

Цель: отделить дельту, которую надо брать сразу, от изменений, требующих отдельного проектирования или проверки на нашем runtime.

## Источники

| Источник | Предыдущая точка в наших документах | Текущий upstream | Решение |
|---|---:|---:|---|
| `brake71/1c-ssl-skills` | `85783ee` (`v0.4`, 2026-06-29) | `415dc94` (`v0.5`, 2026-07-04) | **Adopted P0** |
| `Nikolay-Shirokov/cc-1c-skills` | `3a7f1c17` (`w-2026-06-28`) | `78b5b73` (2026-06-30) | Partial / needs focused transfer |
| `IngvarConsulting/unica` | `db254e4` (2026-05-21) | `10816c4` (2026-07-04) | Needs deep analysis |
| `yellow-hammer/dev-rules` | `fa48a57` | `ee195a6` | No useful docs delta |
| `yellow-hammer/skills-onescript` | `53ac561` | `53ac561` | No delta |
| `yellow-hammer/md-sparrow` | `5e49dc7` | `5e49dc7` | No delta |
| `yellow-hammer/namespace-forest` | SHA not fixed in table | `c0239fd` | Record SHA; no immediate transfer |

## Adopted now

### Brake71 BSP reference v0.5

Принято полностью в справочный слой `ssl-patterns`, потому что это correctness-долг, а не новая архитектура:

- обновлены `framework/skills/bsl-practices/ssl-patterns/references/bsp-3.1.11/*.md`;
- обновлены `borrowed_commit`, `borrowed_tag`, `borrowed_at` в `ssl-patterns/SKILL.md`;
- `scripts/bsp_api.py` не менялся: локальная версия уже совпадала с upstream.

Ключевая польза:

- `print-reports.md`: корректный сценарий регистрации печати через `ПриОпределенииНастроекПечати`, один параметр у `ДобавитьКомандыПечати`, автокомпоновка печатных форм.
- `longs-and-jobs.md`: уточнения регионов API, поведения ошибок и структуры параметров параллельного выполнения.
- `base-common.md`, `forms-validation.md`, `commands-external.md`, `comms.md`, `report-dedup.md` и другие: исправления сигнатур, типов возврата, служебных/стабильных границ и примеров.

## Needs focused transfer

### Shirokov delta after `w-2026-06-28`

Брать не массово, а точечно:

| Дельта | Решение | Причина |
|---|---|---|
| `form-remove`: очищать все `*Form` слоты, а не только `DefaultForm` | ✅ **Adopted** в `xml-gen form remove` | Исправлен `ObjectContainerEditor.clearDefaultFormIfMatches()`, добавлен CLI-регресс `formRemove_clearsAllMatchingFormSlots`. |
| `cfe-borrow`: `CommonPicture` refs в формах | ✅ **Adopted** в `xml-gen extension borrow` | `ExtensionEditor` теперь автозаимствует существующие `CommonPicture.*`, регистрирует их в `Configuration.xml` и strip'ит незаимствованные `CommonPicture` / `StdPicture.*` кроме `StdPicture.Print`. |
| `img-grid`: validation `rows/cols`, shebang, safe auto rows | **Adopt P2** | Малый риск, но не срочно. |
| `skd-edit.py`: Python 3.9 compatibility для autoDates | Skip for Java runtime / watch tests | У нас `skd-edit` не должен зависеть от Python-порта. Полезен только как тестовая идея. |
| `db-*` / `ibcmd` non-interactive / кириллические пути | Skip unless user reopens DB toolchain decision | Ранее принято решение не развивать `db-*` поверх ibcmd, основной контур — `v8-runner`. |
| Smoke fixtures для `form-decompile`, `form-remove`, `erf-validate` | **Adopt as inspiration P2** | Можно использовать как идеи регрессов, не как прямой импорт. |

### Unica delta after `db254e4`

Требует отдельной глубокой оценки, потому что большая часть изменений завязана на Unica MCP/runtime, но поведение полезно для нашего фреймворка.

Кандидаты на перенос:

| Кластер | Приоритет | Что проверить |
|---|---:|---|
| `web-test` modular runtime | P1 | Локальный `tools/web-test` уже содержит `test` command, JUnit/Allure, `selectValue`, `fillTableRow`, modal/error handling. Не импортировать runtime целиком. Нужен focused behavior diff по сценариям `selectValue(field, [...])`, headerless grids, modal row click и новым upstream regressions. |
| `code-diagnostics` graph workflow | P1 | Сопоставить с нашим BSL LS / RLM workflow. Брать правило impact analysis для exported/public/shared changes, если не дублирует уже имеющиеся subagent rules. |
| `support guard` в mutating skills | P0/P1 | Проверить, не покрыто ли уже `xml-gen support check/info` и `guardMutation()`. Если покрыто — только синхронизировать инструкции. |
| `cfe-borrow` preservation/enrichment fixes | ✅ Covered | `writeIfMissing()` для `Form.xml`/`Module.bsl`, main-attribute borrow, `DefinedType` Type XML, свежие data-binding strip tags и `CommonPicture` auto-borrow/strip покрыты локальными регрессами. |
| `meta compile` documented types / UUID fixes | P1 | Сверить с `MetaWriter`/`MetaEditor`: documented object types, UUID generation, subsystem child UUID. В observed skill delta явно добавлен только `choiceHistoryOnInput`; source-level Unica fixes нужно смотреть отдельно по runtime source, не по skill docs. |
| `form Type` bare value rejection | Already covered / verify by tests | Наш `FormValidator` уже содержит правило non-canonical type XML (`<ValueType>...</ValueType>`, nested bare `<Type>` без `v8:`) и тесты FORM-типов. Нужен только targeted regression, если upstream bare-type fixture отличается. |
| packaged MCP / Windows launcher fixes | Skip | Не относится к нашему runtime, если не используем Unica packaging. |

## No action

- `yellow-hammer/dev-rules`: свежая дельта — dependency/CI, `docs/**/*.md` не изменились.
- `yellow-hammer/skills-onescript`: нет дельты.
- `yellow-hammer/md-sparrow`: нет дельты.
- `namespace-forest`: HEAD `c0239fd`; полезно зафиксировать SHA в `yellow-hammer-borrowings.md`, но новый transfer без повторного `xsd_coverage_delta.py` не делать.

## Focused web-test diff

Прямой импорт Unica/Shirokov web-test runtime не нужен: у нас уже есть единый `tools/web-test` с `test` command, JUnit/Allure, `selectValue`, `fillTableRow`, screenshots, hooks и modal/error handling. Upstream сейчас модульнее, но это архитектурная форма, не самостоятельная ценность для переноса.

| Сценарий | Локальный статус | Решение |
|---|---|---|
| `test` runner, JUnit/Allure, screenshots, hooks | Covered | Не переносить. У нас runner компактнее, но функционально закрывает основной контур. |
| `fillTableRow` по header text, checkbox retry, choice/ref cells, commit row | Covered | Не переносить целиком; upstream и локальный код уже содержат одинаковые ключевые ветки. |
| Headerless grid synthesized columns | Covered | Локальный `fillTableRow` уже содержит fallback на `headerText` и checkbox handling; upstream `dom/grid-edit.mjs` подтверждает сценарий как regression source. |
| Modal row click / narrow selection form row center | Covered enough / verify on live UI | Локальный `pickFromSelectionForm` и table click paths уже используют координатный click; нужен только живой regression, если всплывет конкретная форма. |
| `selectValue(field, [..])` / value-list multi-select surfaces | **Gap / deeper analysis** | В Shirokov/Unica есть явная ветка `Array.isArray(searchText)` и отдельный multi-select handler для value-list/catalog multi-row. У нас публичный `selectValue` массив не принимает. Это полезная P1-кандидатура, но требует отдельного порта с UI fixture, потому что upstream покрывает 4 разных surface. |
| Multi-context test execution (`config.contexts`, `createContext`) | Gap / P2 | Unica runner умеет несколько contexts; у нас single-context runner. Полезно только для сценариев multi-user/browser-session. Не брать без отдельного требования. |

Итог: focused transfer для web-test сейчас один P1-кандидат — `selectValue(field, [...])` для value-list/multi-row selection. Остальное либо уже покрыто, либо требует конкретного живого regression вместо импорта модульной структуры.

## Meta compile source-level analysis

Проверена runtime-дельта Unica `crates/unica-coder/src/infrastructure/native_operations/meta.rs` после `db254e4`, включая commits `e38b273`, `136ead9`, `b0f0a9d`, `6655f45`. Сверка с нашим `tools/xml-gen/src/main/java/io/github/onec/xmlgen/writer/MetaWriter.java`:

| Unica runtime fix | Локальный статус | Вывод |
|---|---|---|
| `fresh_meta_compile_uuid()` вместо timestamp/fixed UUID | Covered | Наш `UuidGenerator.generate()` использует `UUID.randomUUID()`; `MetaWriter` применяет его для объекта и `GeneratedType` ids. |
| Поддержка documented types: Catalog, Document, Enum, registers, plans, BP/Task, ExchangePlan, DocumentJournal, Report, DataProcessor, CommonModule, ScheduledJob, EventSubscription, HTTPService, WebService, DefinedType | Covered | `SUPPORTED_TYPES` в `MetaWriter` содержит тот же набор 23 типов. |
| Правильные каталоги/модули: ObjectModule, ManagerModule, RecordSetModule, Module.bsl | Covered | `createDirStructure()` создает соответствующие `Ext/*.bsl`; простые типы без object subdir обработаны отдельно. |
| `ExchangePlan/Ext/Content.xml`, `BusinessProcess/Ext/Flowchart.xml` | Covered | Есть `writeExchangePlanContent()` и `writeFlowchartStub()`, тесты в `MetaWriterTask171Test`. |
| `DefinedType` GeneratedType и type XML | Covered | `MetadataTypeRegistry` задает category `DefinedType`, writer эмитит `DefinedType.<Name>` и типы значений. |
| `ChoiceHistoryOnInput` в свойствах | Covered | Уже есть для Catalog/Document/Enum и других reference-like веток; `MetaEditorXg50Test` отдельно фиксирует добавление. |
| Subsystem compile UUID fixes | Separate toolchain | В Unica это не `meta compile`, а subsystem native op. Для нашего Java runtime отдельного срочного transfer из этого diff нет. |

Вывод: срочной дельты для `MetaWriter` из свежего Unica runtime не найдено. Следующий полезный шаг не импорт, а targeted regression sweep по `MetaWriterTask171Test`: по одному smoke на каждый из 23 типов с проверкой загрузочных артефактов и отсутствия fixed UUID.

## Следующие шаги

1. Спроектировать порт `selectValue(field, [...])` для web-test как отдельную P1-задачу с fixture/live regression по value-list и catalog multi-row surfaces.
2. Расширить `MetaWriterTask171Test` smoke matrix по всем 23 supported types, если хотим формально закрыть parity с Unica documented types.
3. Проверить Shirokov smoke fixtures для `form-decompile` и `erf-validate` как источник дополнительных регрессов.
