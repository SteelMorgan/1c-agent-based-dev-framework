# Skills Consolidation Proposal

**Дата:** 2026-05-21
**Тип:** анализ + предложение (не план)
**Автор:** agent/analysis

---

## 1. TL;DR

- **Всего навыков:** 79 SKILL.md файлов
- **Кандидатов на группировку:** 8 групп (30 навыков → 10 навыков)
- **Ожидаемое сокращение:** с 79 до ~57 навыков (−22 файла), без потери content
- **Явные дубликаты (dup-candidates):** 1 пара + 2 пары частичных

**Top-3 самых очевидных слияния (preview):**
1. `xml-generation` + `xml-gen-cli` → единый entry-point в xml-gen кластере
2. `form-info` + `form-edit` + `form-validate` + `form-element-mapping` + `epf-validate` → один `forms-toolkit`
3. `task-breakdown-linear` + `task-breakdown-subagent` → один `task-breakdown` с двумя секциями

---

## 2. Текущая инвентаризация (по категориям)

### 2.1. По структуре дерева

| Раздел дерева | Кол-во SKILL.md | Краткий состав |
|---|---|---|
| `bsl-practices/` | 13 | api-design (334), background-jobs (361), coding-standards (410), data-exchange (473), error-handling (604), form-patterns (426), form-visual-requirements (52), integration-patterns (483), query-optimize (236), query-patterns (376), security (213+refs), ssl-patterns (329), test-writing (254+refs) |
| `framework-meta/` | 9 | 1c-ai-agent-cli (102), agent-development (112), agent-development-ext (150), brainstorm (310), critical-partner (210), skill-creator (361), skill-creator-ext (156), skill-drying (68), skill-editing-from-project (94), skills-i18n-sync (121) |
| `other/` | 2 | find-skills (46), infostart-kb (328) |
| `spec-writing/` | 4 | spec-standard (132), task-breakdown-linear (136), task-breakdown-subagent (126), technical-design-standard (391) |
| `tool-usage/browser-ui/` | 7 | gui-control (142), img-grid (93), playwright (89), playwright-interactive (158), screenshot (96), visual-check (63), web-test-1c (145) |
| `tool-usage/code-analysis/` | 5 | buddy-prompting (181), code-navigation (84), code-verification (127), search-before-write (62), syntax-checking (144) |
| `tool-usage/content-generation/` | 2 | codex-image-gen (109), docx-convert (81) |
| `tool-usage/diagnostics/` | 6 | agent-debug (121), bug-reporting (265), db-performance (174), event-log-analysis (122), runtime-investigation (325), tech-log-analysis (152+refs) |
| `tool-usage/epf/` | 1 | epf-validate (85) |
| `tool-usage/forms/` | 4 | form-edit (144), form-element-mapping (117), form-info (185), form-validate (147) |
| `tool-usage/platform-admin/` | 2 | rac-use (153), subsystem-update (199) |
| `tool-usage/platform-data/` | 3 | metadata-discovery (38), nav-link (32), query-execution (113) |
| `tool-usage/platform-data/xml-generation/` | 14 | config-operations (83), epf-bsp-operations (377), epf-operations (130), extension-operations (209), form-dsl (107), interface-operations (167), meta-operations (171), mxl-dsl (223+refs), role-dsl (91), skd-dsl (538+refs), skd-edit (153), subsystem-operations (102), template-operations (276), xml-gen-cli (144), xml-generation (103) |
| `tool-usage/review/` | 1 | cross-provider-review (197) |
| `tool-usage/v8-runner/` | 1 | v8-runner (189+8 refs) |
| `tool-usage/v8-session-manager/` | 1 | v8-session-manager (88) |
| `tool-usage/vanessa/` | 2 | vanessa-authoring (148+refs), vanessa-diagnostics (107) |

### 2.2. Размерные категории

| Размер | Навыков | Примеры |
|---|---|---|
| Крупные (>300 строк) | 14 | error-handling (604), skd-dsl (538), data-exchange (473), integration-patterns (483), form-patterns (426), coding-standards (410), brainstorm (310+) |
| Средние (150–300 строк) | 28 | большинство xml-generation операций, diagnostics |
| Малые (<150 строк) | 37 | form-visual-requirements (52), nav-link (32), metadata-discovery (38), search-before-write (62), visual-check (63), skill-drying (68) |

---

## 3. Предлагаемые группы

### 3.1. Группа «xml-gen entry-point» — DUP-CANDIDATE HIGH

**Источник (текущие навыки):**
- `tool-usage/platform-data/xml-generation/xml-generation/SKILL.md` (103 строки)
- `tool-usage/platform-data/xml-generation/xml-gen-cli/SKILL.md` (144 строки)

**Проблема:** Два навыка описывают один и тот же инструмент (`xml-gen`). `xml-generation` — общий обзор + установка + список 45 операций. `xml-gen-cli` — подкоманды `validate`, `edit`, синтаксис флагов. Пересечение: оба объясняют «как вызвать `xml-gen`», оба упоминают одни и те же команды. Агент не знает, какой из двух читать первым — нет явного указания. `xml-generation` ссылается на операционные навыки, `xml-gen-cli` не упоминает `xml-generation`.

**Целевая структура:**
```
framework/skills/tool-usage/platform-data/xml-generation/xml-generation/SKILL.md
  — Назначение + установка + оглавление всех доменов
  — §1 Quick start (2–3 примера)
  — §2 Индекс операций (таблица: домен → навык → команды)
  — §3 Validate + edit синтаксис CLI (содержимое xml-gen-cli)
  — §4 Error codes

references/  (существующий)
```

`xml-gen-cli/` — удалить как отдельный навык, содержимое перенести в `xml-generation/SKILL.md`.

**Обоснование:** Агент, начинающий задачу xml-gen, открывает `xml-generation` как entry-point. Синтаксис CLI (validate, edit, replace-text) нужен в том же месте — это не отдельная тема, а «как вызвать». Сейчас агент вынужден читать два файла, чтобы понять одно. После слияния: один SKILL.md входа + операционные reference-навыки.

**Риски:**
- `xml-gen-cli` упоминается по имени в frontmatter subagent? Нет — в `developer-code.md` используется `xml-generation`, не `xml-gen-cli`. Значит, имя навыка не сломает subagent-ы.
- Файл может превысить 250 строк — допустимо, не 800.

**Эффект:** −1 файл, entry-point становится единым.

---

### 3.2. Группа «forms-toolkit» — STRONG CANDIDATE

**Источник (текущие навыки):**
- `tool-usage/forms/form-info/SKILL.md` (185 строк)
- `tool-usage/forms/form-edit/SKILL.md` (144 строки)
- `tool-usage/forms/form-validate/SKILL.md` (147 строк)
- `tool-usage/forms/form-element-mapping/SKILL.md` (117 строк)
- `tool-usage/epf/epf-validate/SKILL.md` (85 строк)

**Итого исходного контента:** 678 строк в 5 файлах.

**Проблема:** Все пять навыков работают с одним CLI (`xmlgen`) на одних и тех же файлах формы (`Form.xml`). Жизненный цикл: `form-info` → `form-edit` → `form-validate`. `form-element-mapping` — вспомогательный алгоритм, нужный только в контексте работы с формой. `epf-validate` — один-в-один аналог `form-validate` для EPF-объектов, те же параметры, те же коды ошибок (85 строк).

Агент `developer-code` перечисляет их все: `form-info`, `form-edit`, `form-validate`, `epf-validate` — четыре отдельных навыка для одного workflow.

**Целевая структура:**
```
framework/skills/tool-usage/forms/SKILL.md
  — §1 form-info: анализ Form.xml
  — §2 form-edit: добавление элементов
  — §3 form-validate: проверка Form.xml
  — §4 epf-validate: проверка EPF/ERF
  — §5 form-element-mapping: Title → Name lookup

references/  (пусто, всё влезет в один файл)
```

Старые каталоги (`forms/form-info/`, `forms/form-edit/`, `forms/form-validate/`, `forms/form-element-mapping/`, `epf/epf-validate/`) — оставить как forwarding-stub (одна строка с @import или просто note «moved to»), пока все subagent-ы не будут обновлены.

**Обоснование:** Все пять навыков активируются в один момент — когда агент работает с формой. Объединённый файл (~450–500 строк) даёт все команды сразу, без переключений. Каждая секция — самостоятельный workflow.

**Риски:**
- `form-info`, `form-edit`, `form-validate`, `epf-validate` перечислены по имени в `developer-code.md`, `reviewer.md`, `scenario-author.md`, `scenario-coder.md` — при переименовании надо обновить subagent frontmatter.
- `form-element-mapping` не перечислен ни в одном subagent — его потребители читают форму руками.
- Размер: 500 строк — в рамках допустимого.

**Эффект:** −4 файла (5 → 1), subagent skills-листы сокращаются на 3–4 строки.

---

### 3.3. Группа «task-breakdown» — DUP-CANDIDATE

**Источник (текущие навыки):**
- `spec-writing/task-breakdown-linear/SKILL.md` (136 строк)
- `spec-writing/task-breakdown-subagent/SKILL.md` (126 строк)

**Проблема:** Оба навыка делают одно — Task Breakdown JSON. Различие: linear = self-check, subagent = cross-review + BLOCK-итерации. Структура JSON одинакова: `task_id`, `task_type`, `depends_on`, `spec_refs`. 40%+ содержимого дублируется.

`architect.md` использует только `task-breakdown-subagent`. Ни один агент не использует оба одновременно.

**Целевая структура:**
```
framework/skills/spec-writing/task-breakdown/SKILL.md
  — §1 Общее: JSON-формат, поля, template
  — §2 Linear mode: self-check протокол
  — §3 Subagent mode: cross-review + BLOCK-итерации
```

**Обоснование:** Агент выбирает режим на основе контекста оркестрации — оба описания нужны как альтернативы, не как независимые навыки. Дублируемая часть (формат JSON, trigger table) должна существовать в одном экземпляре.

**Риски:**
- `architect.md` ссылается на `task-breakdown-subagent` по имени — потребует обновления.
- Новое имя (`task-breakdown`) — проверить нет ли конфликта с upstream-репо.

**Эффект:** −1 файл, −~50% дублирующегося контента.

---

### 3.4. Группа «epf-full» — STRONG CANDIDATE

**Источник (текущие навыки):**
- `tool-usage/platform-data/xml-generation/epf-operations/SKILL.md` (130 строк)
- `tool-usage/platform-data/xml-generation/epf-bsp-operations/SKILL.md` (377 строк)
- `tool-usage/platform-data/xml-generation/template-operations/SKILL.md` (276 строк)

**Итого:** 783 строки в 3 файлах.

**Проблема:** `epf-operations` явно ссылается на `template-operations` («для EPF смотри epf add-template»). `template-operations` описывает, что `epf-operations` это «EPF base». `epf-bsp-operations` — обвязка БСП для тех же EPF-файлов. Три навыка создают один workflow: init EPF → add form/template → register in BSP.

В subagent-е `developer-code` все три перечислены рядом: `epf-bsp-operations`, `template-operations`. В `architect.md` — `epf-bsp-operations`.

**Целевая структура:**
```
framework/skills/tool-usage/platform-data/xml-generation/epf-operations/SKILL.md
  — §1 EPF/ERF base: init, add-form, add-attribute, add-tabular-section
  — §2 Templates: template add/remove/add-help (для всех объектов)
  — §3 BSP-регистрация: СведенияОВнешнейОбработке() + add-command

references/
  bsp-commands.md  (детали команд БСП — типы, параметры)
```

`template-operations/` и `epf-bsp-operations/` → forwarding-stubs.

**Обоснование:** `template-operations` охватывает не только EPF, но и Catalog/Document. Это усложняет решение. Однако в субагентах `template-operations` используется только `developer-code` — и всегда вместе с `epf-bsp-operations`. Если `template-operations` оставить как reference внутри `epf-operations`, а в SKILL.md добавить раздел «Templates для объектов метаданных (non-EPF)» — это покрывает все случаи.

**Альтернатива (если риск высок):** Объединить только `epf-operations` + `epf-bsp-operations` (230 строк + маленькое epf-operations = 500 строк с references), `template-operations` — оставить отдельно. Тогда: −1 файл, меньший риск.

**Риски:**
- `template-operations` упоминается отдельно от EPF (объекты Catalog/Document/Register) — объединение может создать ложное впечатление, что шаблоны только для EPF. **Обойти:** явный заголовок §2 «Templates для любых объектов метаданных».
- Размер объединённого файла: ~600 строк — крупно, но допустимо.

**Эффект:** −2 файла (3 → 1), workflow EPF → template → BSP в одном месте.

---

### 3.5. Группа «subsystem-interface» — MODERATE CANDIDATE

**Источник (текущие навыки):**
- `tool-usage/platform-data/xml-generation/subsystem-operations/SKILL.md` (102 строки)
- `tool-usage/platform-data/xml-generation/interface-operations/SKILL.md` (167 строки)

**Итого:** 269 строк в 2 файлах.

**Проблема:** `subsystem-operations` в своём названии указывает «Subsystem + Interface Operations». SKILL.md буквально называется «Subsystem + Interface Operations». `interface-operations` — редактирование `CommandInterface.xml` подсистемы. Это явно вложенная тема: подсистема владеет командным интерфейсом.

Ни один subagent не использует `interface-operations` отдельно — в `developer-code.md` и `reviewer.md` оба навыка стоят рядом.

**Целевая структура:**
```
framework/skills/tool-usage/platform-data/xml-generation/subsystem-operations/SKILL.md
  — §1 Subsystem: compile/info/edit/validate
  — §2 CommandInterface: edit/validate (содержимое interface-operations)
```

**Обоснование:** Командный интерфейс неотделим от подсистемы. Workflow: создать подсистему → настроить CommandInterface. 269 строк — небольшой файл даже без деления.

**Риски:**
- `interface-operations` перечислен по имени в `developer-code.md` и `reviewer.md` — обновить.
- Размер объединённого: ~270 строк — нормально.

**Эффект:** −1 файл.

---

### 3.6. Группа «platform-data» — MODERATE CANDIDATE

**Источник (текущие навыки):**
- `tool-usage/platform-data/metadata-discovery/SKILL.md` (38 строк)
- `tool-usage/platform-data/nav-link/SKILL.md` (32 строки)
- `tool-usage/platform-data/query-execution/SKILL.md` (113 строк)

**Итого:** 183 строки в 3 файлах.

**Проблема:** `metadata-discovery` (38 строк) и `nav-link` (32 строки) — аномально маленькие. Оба описывают работу с данными платформы через одни и те же capabilities (`list_metadata_objects`, `parse_nav_link`, `execute_query`). `query-execution` уже ссылается на `metadata-discovery` (первый шаг workflow). `nav-link` — почти всегда используется вместе с `query-execution` (разобрать ссылку → выполнить запрос).

В subagent-ах: `analyst.md` включает все три; `explorer.md` — `metadata-discovery`; `architect.md`, `debugger.md`, `tester.md` — `query-execution`; `scenario-author.md` — нет ни одного из этих трёх.

**Целевая структура:**
```
framework/skills/tool-usage/platform-data/platform-data/SKILL.md
  — §1 Metadata Discovery: list/get_metadata_structure, алгоритм
  — §2 Query Execution: validate → execute workflow, syntax cheatsheet ref
  — §3 Nav Link: parse/get_nav_link + связка с query

references/
  query-syntax-cheatsheet.md  (уже существует в query-execution/references/)
```

**Обоснование:** `metadata-discovery` (38 строк) — это скорее параграф, чем навык. `nav-link` (32 строки) — тоже параграф. Отдельные файлы для таких коротких описаний создают overhead: агент ищет три разных навыка вместо одного.

**Риски:**
- `metadata-discovery` используется отдельно в `explorer.md` и `analyst.md` — но это не проблема: объединённый навык можно называть `platform-data`, а старые имена сделать aliases/stubs.
- После объединения frontmatter `uses_capabilities` становится объединением трёх списков — ок.

**Эффект:** −2 файла (3 → 1), меньше entry-points для одного уровня абстракции.

---

### 3.7. Группа «platform-admin» — WEAK CANDIDATE

**Источник (текущие навыки):**
- `tool-usage/platform-admin/rac-use/SKILL.md` (153 строки)
- `tool-usage/platform-admin/subsystem-update/SKILL.md` (199 строки)

**Итого:** 352 строки в 2 файлах.

**Проблема:** Оба навыка описывают административные операции над работающей ИБ. `subsystem-update` использует `rac` (из `rac-use`) для блокировки сеансов на шаге 2 обновления. Это прямая зависимость: понять `subsystem-update` невозможно без `rac-use`.

Ни `rac-use`, ни `subsystem-update` не перечислены ни в одном subagent frontmatter — используются ситуативно, не через subagent-pipeline.

**Целевая структура:**
```
framework/skills/tool-usage/platform-admin/SKILL.md
  — §1 RAC: cluster UUID, session/connection/infobase управление
  — §2 Subsystem Update: полный цикл (RAC block → update → verify)

references/  (пусто)
```

**Обоснование:** 352 строки — нормальный размер. Workflow `subsystem-update` включает RAC-команды внутри — сейчас приходится читать оба файла. После объединения: один файл, полный lifecycle.

**Риски:** Минимальные — ни один subagent по имени не ссылается на эти навыки. Риск: разные специалисты (DevOps vs разработчик) могут искать RAC и BSP-обновление по-разному.

**Эффект:** −1 файл.

---

### 3.8. Группа «vanessa» — SOFT CANDIDATE

**Источник (текущие навыки):**
- `tool-usage/vanessa/vanessa-authoring/SKILL.md` (148 строк + 3 references)
- `tool-usage/vanessa/vanessa-diagnostics/SKILL.md` (107 строк)

**Итого:** 255 строк основного контента, 5 файлов.

**Проблема:** Оба навыка существуют внутри одной директории `tool-usage/vanessa/`. `vanessa-diagnostics` явно ссылается на `v8-runner` (запуск делается через него). Оба используются в одном контексте (запуск/диагностика Vanessa).

**Однако:** В subagent-ах они разделены осознанно:
- `scenario-author.md` — только `vanessa-authoring`
- `scenario-coder.md` — `vanessa-authoring` + `vanessa-diagnostics`
- `debugger.md` — только `vanessa-diagnostics`
- `tester.md` — только `vanessa-diagnostics`

Разные агенты подключают разные части.

**Вывод:** **Не объединять полностью.** Но добавить явный cross-ref: в `vanessa-authoring` добавить ссылку на `vanessa-diagnostics` в секцию «Что делать если сценарий упал», и наоборот. Это снизит когнитивную нагрузку без нарушения независимости.

**Эффект:** 0 файлов удалено, добавлены cross-refs — не слияние, а связка.

---

## 4. Кандидаты, оставленные как есть (Won't merge)

### 4.1. `bsl-practices/` — все навыки оставить раздельными

**Навыки:** api-design, background-jobs, coding-standards, data-exchange, error-handling, form-patterns, integration-patterns, query-optimize, query-patterns, security, ssl-patterns, test-writing.

**Обоснование:** Каждый из них — отдельная методологическая тема. Subagent-ы подключают их избирательно: `developer-tests.md` берёт только `test-writing`+`coding-standards`+`error-handling`, не берёт `api-design`/`integration-patterns`. `explorer.md` не берёт ни одного. Независимость использования принципиальна.

**Исключение-кандидат:** `form-visual-requirements` (52 строки) — аномально мал. Единственный потребитель — `visual-check`, который явно зависит (`depends_on`). Логично перенести как reference внутрь `tool-usage/browser-ui/visual-check/SKILL.md`, но это другая категория (`bsl-practices` → `tool-usage`), что нарушает правило разных `metadata.category`. Оставить отдельно.

### 4.2. `skd-dsl` + `skd-edit` — оставить раздельными

**Навыки:** skd-dsl (538 строк + 2 refs = ~952 строки), skd-edit (153 строки).

**Обоснование:** Оба крупные. `skd-dsl` — compile (создание с нуля) + 11 info-режимов, `skd-edit` — атомарные patch-операции существующей схемы. Разные сценарии: developer делает compile, reviewer делает edit-check. Оба уже в `reviewer.md` и `developer-code.md`, но описаны как «дополняют, не пересекаются» — это осознанное разделение. Объединение создало бы файл >700 строк.

### 4.3. `mxl-dsl` + `form-dsl` + `role-dsl` — не объединять

**Навыки:** mxl-dsl (223+3 refs), form-dsl (107), role-dsl (91).

**Обоснование:** Разные DSL с разными командами, разными структурами JSON. `mxl-dsl` — tabular documents (печатные формы). `form-dsl` — управляемые формы. `role-dsl` — права доступа. Ни одного общего паттерна кроме «это JSON DSL». Subagent-ы подключают их по-разному: `explorer.md` берёт только `mxl-dsl`, `developer-code.md` берёт все три. Объединение разрушит независимость выборки.

### 4.4. `v8-runner` + `v8-session-manager` — не объединять

**Навыки:** v8-runner (189+8 refs = ~900 строк), v8-session-manager (88).

**Обоснование:** `v8-runner` — CI-автоматизация: сборка, синтаксис, тесты. `v8-session-manager` — управление интерактивными сессиями через MCP. Разные инструменты (CLI vs MCP-сервер), разные provides_capabilities. Используются вместе большинством агентов, но это не причина объединять — они работают в разных контекстах (batch vs interactive).

### 4.5. `playwright` + `playwright-interactive` — не объединять

**Навыки:** playwright (89), playwright-interactive (158).

**Обоснование:** `playwright` — CLI-first batch automation. `playwright-interactive` — persistent `js_repl` сессия для iterative debugging (Electron + web). Разный toolchain (скрипт vs REPL), разные preconditions (`js_repl` require). `tester.md` использует только `playwright`, не `playwright-interactive`. Разделение осознанное.

### 4.6. `framework-meta/` — не трогать

**Навыки:** все 9 навыков (agent-development, agent-development-ext, skill-creator, skill-creator-ext, brainstorm, critical-partner, skill-drying, skill-editing-from-project, skills-i18n-sync).

**Обоснование:** Пары base+ext (`agent-development`+`agent-development-ext`, `skill-creator`+`skill-creator-ext`) — intentional split: base = generic (Anthropic upstream), ext = 1C-специфика. Это паттерн расширения без редактирования upstream. Слияние потребует отказа от паттерна. Остальные — самостоятельные workflow (brainstorm, critical-partner, skill-drying как standalone slash-commands).

### 4.7. `diagnostics/` — не объединять все, но есть частичный candidate

**Навыки:** agent-debug (121), bug-reporting (265), db-performance (174), event-log-analysis (122), runtime-investigation (325), tech-log-analysis (152+refs).

**Рассматривалось:** `event-log-analysis` + `tech-log-analysis` (122 + 152 = 274 строки). Оба описывают 1С-логи. Оба используются вместе в `debugger.md` и `developer-code.md`.

**Решение: НЕ объединять.** `event-log-analysis` — ЖР через ClickHouse (MCP capability `search_event_log`). `tech-log-analysis` — технологический журнал (настройка ТЖ через 5 capabilities). Разные tools, разные пользователи (ЖР читает любой агент, ТЖ настраивает только debugger/architect). `explorer.md` не использует ни один из них. Разделение по tool-возможностям принципиальное.

### 4.8. `spec-writing/spec-standard` + `technical-design-standard` — не объединять

**Обоснование:** `spec-standard` (132) — структура спецификации (ЧТО). `technical-design-standard` (391) — структура технического дизайна (КАК). Разные артефакты, разные фазы пайплайна, разные агенты: `analyst.md` использует только `spec-standard`, `architect.md` — только `technical-design-standard`. Объединение сломает выборку.

---

## 5. Process Plan

Если предложение одобрено пользователем — рекомендуемый порядок действий:

### Шаг 1 — Быстрые слияния (low-risk)
1. `xml-generation` ← `xml-gen-cli`: merge, обновить subagent frontmatter (нет изменений — `xml-gen-cli` не в subagents).
2. `task-breakdown`: новый файл, old stubs, обновить `architect.md` (заменить `task-breakdown-subagent` → `task-breakdown`).
3. `subsystem-operations` ← `interface-operations`: merge, обновить `developer-code.md`, `reviewer.md`.

### Шаг 2 — Слияния с обновлением subagents
4. `platform-data` ← `metadata-discovery` + `nav-link` + `query-execution`: новый dir, stubs, обновить `analyst.md`, `architect.md`, `debugger.md`, `tester.md`.
5. `platform-admin` ← `rac-use` + `subsystem-update`: merge, нет subagent-изменений.

### Шаг 3 — Крупные слияния (требуют review)
6. `forms-toolkit` ← `form-info` + `form-edit` + `form-validate` + `form-element-mapping` + `epf-validate`: обновить `developer-code.md`, `reviewer.md`, `scenario-author.md`, `scenario-coder.md`.
7. `epf-operations` ← `epf-operations` + `epf-bsp-operations` + `template-operations`: обновить `developer-code.md`, `architect.md`.

### После каждого шага
- Запустить `python3 tools/sync-skill.py <path>` для sync RU→EN зеркала.
- Проверить `.skills-sync-state.json` на статус `synced`.
- Обновить `external-skills-mapping.md` колонку «Наш модуль» где нужно.

---

## 6. Сводная таблица кандидатов

| Группа | Исходных навыков | После | Delta | Приоритет | Риск |
|---|---|---|---|---|---|
| xml-gen entry-point | 2 (xml-generation + xml-gen-cli) | 1 | −1 | High | Low |
| forms-toolkit | 5 (form-info/edit/validate/elem-mapping + epf-validate) | 1 | −4 | High | Medium |
| task-breakdown | 2 (linear + subagent) | 1 | −1 | High | Low |
| epf-full | 3 (epf-ops + epf-bsp + template) | 1 | −2 | Medium | Medium |
| subsystem-interface | 2 (subsystem + interface ops) | 1 | −1 | High | Low |
| platform-data | 3 (metadata + nav-link + query) | 1 | −2 | Medium | Low |
| platform-admin | 2 (rac-use + subsystem-update) | 1 | −1 | Low | Low |
| vanessa cross-refs | 2 | 2 | 0 | Low | None |
| **Итого** | **21** | **9** | **−12** | | |

*Плюс к этому навыки, которые точно не трогаем: 58 штук остаются как есть.*

---

## 7. Open Questions

1. **Forwarding-stubs:** Как обрабатывать старые пути после слияния? Оставить `SKILL.md` с одной строкой `> Этот навык перемещён в [new-path]`? Или обновить все subagent frontmatter и удалить stubs?

2. **`form-element-mapping` в groups vs standalone:** Алгоритм используется в Vanessa-сценариях (`scenario-author.md` не включает этот навык, хотя должен). Возможно, перед слиянием стоит сначала добавить его в `scenario-author.md`, чтобы не потерять при переносе.

3. **`template-operations` для non-EPF объектов:** При слиянии в `epf-full` секция «Templates для Catalog/Document/Register» не должна теряться. Нужно явно проверить что все примеры из `template-operations/SKILL.md` перенесены.

4. **`bsl-practices/form-visual-requirements` (52 строки):** Самый маленький навык. Используется только через `visual-check`. Можно добавить как reference в `visual-check`, не меняя категорию — просто скопировать содержимое в `tool-usage/browser-ui/visual-check/references/visual-checklist.md` и обновить `visual-check/SKILL.md`.

5. **`content-generation/` кластер:** Два навыка (`codex-image-gen`, `docx-convert`) — не рассмотрены для слияния, т.к. разные инструменты. Оставить как есть.
