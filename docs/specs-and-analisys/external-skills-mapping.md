# Сводный маппинг внешних навыков → наш фреймворк

> **Статус документа:** 🟢 живой (living document). Обновляется при каждой сверке с upstream и при каждом заимствовании.
> **Последнее обновление:** `2026-05-22` (rev.6 — **весь backlog xml-gen закрыт**: 9/10 реализовано, #8 MXL миграция → Won't fix; консолидация skills под единый `xml-generation/` + осушение −31%)
> **Ответственный:** orchestrator / архитектор фреймворка
>
> **Назначение:** единая таблица отслеживания того, какие навыки из внешних источников мы заимствовали, какие планируем взять, какие отвергли. Используется для:
> 1. Контроля долга на заимствования (что ещё не сделано)
> 2. Отслеживания апдейтов upstream-репо (что появилось нового с момента последней сверки)
> 3. Подтягивания патчей при появлении значимых изменений у соседей

---

## 1. Upstream-источники

| ID | Репозиторий | URL | Последний просмотренный коммит | Дата сверки | Метод снапшота |
|----|-------------|-----|--------------------------------|-------------|----------------|
| **SH** | `Nikolay-Shirokov/cc-1c-skills` | https://github.com/Nikolay-Shirokov/cc-1c-skills | `6e14f25` (main, 2026-05-21) | 2026-05-21 | `gh api repos/.../contents/.claude/skills` |
| **UN** | `IngvarConsulting/unica` | https://github.com/IngvarConsulting/unica | `db254e4` (main, 2026-05-21) | 2026-05-21 | `gh api repos/.../contents/plugins/unica/skills` |
| **CO** | `comol/cursor_rules_1c` | — | — (не отслеживается активно, см. sources-analysis.md §1) | 2026-02-12 | — |
| **AE** | `AndreevED/1c-ai-feature-dev-workflow` | — | — (концепция фаз уже взята, см. §2) | 2026-02-12 | — |
| **RM** | `rmartynenko/workflow-dev-1c-claude-code` | — | — (BSL standards взяты как база, см. §3) | 2026-02-12 | — |

### Команды для обновления снапшота

```bash
# Снимок коммита и списка навыков Широкова
gh api repos/Nikolay-Shirokov/cc-1c-skills/commits/main --jq '.sha'
gh api repos/Nikolay-Shirokov/cc-1c-skills/contents/.claude/skills --jq '.[].name' | sort

# Снимок Unica
gh api repos/IngvarConsulting/unica/commits/main --jq '.sha'
gh api repos/IngvarConsulting/unica/contents/plugins/unica/skills --jq '.[].name' | sort

# Список последних коммитов для отлова новых направлений
gh api repos/Nikolay-Shirokov/cc-1c-skills/commits?per_page=30 --jq '.[] | .commit.message' | head -50
gh api repos/IngvarConsulting/unica/commits?per_page=30 --jq '.[] | .commit.message' | head -50
```

---

## 2. Легенда статусов

| Статус | Значение |
|--------|----------|
| ✅ **Adopted** | Полностью заимствовано (концепция/код перенесены, может быть переписано) |
| 🔶 **Partial** | Частично заимствовано (часть функций или только концепция) |
| 📋 **Planned** | Запланировано к заимствованию, есть приоритет (P0/P1/P2) |
| 👁 **Watching** | Следим, решение об адаптации отложено |
| ❌ **Skipped** | Намеренно отвергнуто — не релевантно/дублируется/специфично для чужого стека |
| 🆕 **New** | Появилось в upstream после последней сверки, не классифицировано |
| **🔧 xml-gen** | **Дополнительный маркер.** Skill (контракт) готов, **требует Java-имплементации** в `tools/xml-gen/`. Используется вместе с основным статусом, например «✅ Adopted 🔧» = skill написан, CLI ждёт разработки. См. §9 «Инженерный долг в xml-gen». |

### Колонка «Целевые subagents»

Список наших subagents, для которых навык **должен быть включён в `skills:` фронтматтер** при заимствовании. Жирным выделен **основной потребитель** (для которого навык приоритетен).

---

## 3. Полная матрица: Широков (SH) → наш фреймворк

### 3.1. XML-Generation (детально — см. [shirokov-to-xmlgen-mapping-2026-03-09.md](shirokov-to-xmlgen-mapping-2026-03-09.md))

Сводно:

| Группа | Кол-во навыков | Статус | Наш модуль |
|--------|----------------|--------|------------|
| CF (cf-init/edit/info/validate) | 4 | ✅ Adopted | `xml-generation/config-operations` |
| CFE (cfe-init/validate/borrow/diff/patch-method) | 5 | ✅ Adopted 🔧 | `xml-generation/extension-operations` — skill дополнен `--borrow-main-attribute` + `patch-method` (контракт описан, **🔧 ждёт Java**, см. `SPEC-cfe-cli-extension.md`). init/validate/diff — уже работают |
| EPF (init/build/dump/validate + bsp-*) | 6 | ✅ Adopted 🔧 | `xml-generation/epf-full` (слияние #6: epf-operations + epf-bsp-operations + template-operations). build/dump требуют 1С платформу |
| ERF (init/build/dump/validate) | 4 | 🔶 Partial (аналогично EPF) | `xml-generation/epf-full --type report` |
| Form (compile/edit/info/validate/add/remove + patterns) | 7 | ✅ Adopted 🔧 | `xml-generation/form-dsl` + `xml-generation/forms-toolkit`. Глубокий DataPath-резолв (`Items.<Table>.CurrentData.*`, `~<Attr>.*`) описан, **🔧 ждёт Java** |
| Meta (compile/edit/info/validate/remove) | 5 | ✅ Adopted 🔧 | `xml-generation/meta-operations` + новый reference `batch-patch.md`. **🔧 ждёт Java** для `--batch <file.json>` режима |
| MXL (compile/decompile/info/validate) | 4 | ✅ Adopted 🔧 — **полная переработка** | `xml-generation/mxl-dsl` (rev.3, 2026-05-21): SKILL.md 223 строки + 3 references (dsl-spec, info-modes, validate-classes) — **746 строк RU** под канон Широкова: `col` 1-based, `rowStyle`, `rowspan`, `columnWidths` с диапазонами/`"Nx"`, `page: "A4-landscape"`, типы `text`/`param`/`template` + `detail`, `format`, `underline`/`strikeout`, `empty: N`. Сохранён наш `--format designer\|edt`. **🔧 ждёт Java-переписывания** compile/decompile/info/validate под новый DSL |
| Role (compile/info/validate) | 3 | ✅ Adopted | `xml-generation/role-dsl` |
| SKD (compile/edit/info/validate) | 4 | ✅ Adopted 🔧 — **rev.3: разделено на skill-семейство** | (1) расширенный **`skd-dsl`** (compile + 11 info-режимов): 538 строк SKILL.md + 2 references (templates-dsl, info-modes) — 952 строки RU. (2) новый **`skd-edit`**: SKILL.md + 5 references (fields, parameters, totals, structure, query) с полным набором patch-операций (`set-field-role` + kv, `modify-structure @name=`, `patch-query @once`, `add-total` агрегаты+identity, batch `;;`). Unica игнорируется (устаревший фасад). **🔧 ждёт крупной Java-работы** для skd-dsl расширения и skd-edit с нуля |
| Subsystem (compile/edit/info/validate) + Interface (edit/validate) | 6 | ✅ Adopted 🔧 | `xml-generation/subsystem-interface` (слияние #4). **🔧 ждёт Java** для `xml-gen interface edit\|validate` |
| Template (add/remove) + help-add | 3 | ✅ Adopted 🔧 | `xml-generation/epf-full` (слито в §4 templates). **🔧 ждёт Java** для `template add\|remove\|add-help` |

### 3.2. Не-XML навыки (вне xml-gen scope)

| # | Навык SH | Что делает | Наш эквивалент | Статус | Приоритет | Целевые subagents | Last sync | Заметка |
|---|----------|------------|----------------|--------|-----------|-------------------|-----------|---------|
| 1 | `db-create` | Создание ИБ через DESIGNER | `v8-runner` | ❌ Skipped | — | — | 2026-05-21 | **Решение пользователя 2026-05-21:** вся db-* группа покрывается нашим `v8-runner`; чего нет — не актуально |
| 2 | `db-list` | Список ИБ из реестра | `v8-runner` | ❌ Skipped | — | — | 2026-05-21 | См. строку 1 |
| 3 | `db-dump-cf` | Выгрузка ИБ → `.cf` | `v8-runner` | ❌ Skipped | — | — | 2026-05-21 | См. строку 1 |
| 4 | `db-dump-xml` | Выгрузка ИБ → XML | `v8-runner` | ❌ Skipped | — | — | 2026-05-21 | См. строку 1 |
| 5 | `db-load-cf` | Загрузка `.cf` в ИБ | `v8-runner` | ❌ Skipped | — | — | 2026-05-21 | См. строку 1 |
| 6 | `db-load-xml` | Загрузка XML в ИБ | `v8-runner` | ❌ Skipped | — | — | 2026-05-21 | См. строку 1 |
| 7 | `db-load-git` | Частичная загрузка по `git diff` | `v8-runner` | ❌ Skipped | — | — | 2026-05-21 | См. строку 1; реестр `.v8-project.json` тоже не берём |
| 8 | `db-run` | Запуск 1С:Предприятие на ИБ | `v8-runner` | ❌ Skipped | — | — | 2026-05-21 | См. строку 1 |
| 9 | `db-update` | Обновление конфигурации БД | `v8-runner` | ❌ Skipped | — | — | 2026-05-21 | См. строку 1 |
| 10 | `epf-bsp-init` | Генерация `СведенияОВнешнейОбработке()` | `xml-generation/epf-full/` (§5, references/epf-bsp.md) | ✅ Adopted 🔧 | — | **developer-code**, architect | 2026-05-22 | Слито в epf-full (слияние #6). **🔧 ждёт Java** для команды `xml-gen epf-bsp init` |
| 11 | `epf-bsp-add-command` | Добавление команды БСП с обработчиком | `xml-generation/epf-full/` (§5, references/epf-bsp.md) | ✅ Adopted 🔧 | — | **developer-code**, architect | 2026-05-22 | Слито в epf-full (слияние #6). **🔧 ждёт Java** для `xml-gen epf-bsp add-command` |
| 12 | `cfe-borrow` (базовый) | Заимствование объектов в расширение | `extension-operations` (`xml-gen extension borrow`) | ✅ Adopted | — | developer-code, architect | 2026-05-21 | Базовый borrow уже покрыт нашим CLI |
| 12a | **`cfe-borrow --borrow-main-attribute`** | Заимствование основного реквизита формы | `extension-operations` (skill дополнен) | ✅ Adopted 🔧 | — | **developer-code** | 2026-05-21 | Skill дополнен (rev.3) + создан **`SPEC-cfe-cli-extension.md`**. **🔧 ждёт Java** для опции `--borrow-main-attribute form\|all` |
| 13 | `cfe-diff` (Mode A+B) | Сравнение расширения и типовой | `extension-operations` (`xml-gen extension diff --mode`) | ❌ Skipped | — | — | 2026-05-21 | **Дубль**: наш CLI уже умеет оба режима, включая Mode B (проверка переноса вставок) |
| 13a | `cfe-init` | Scaffold расширения | `extension-operations` (`xml-gen extension init`) | ❌ Skipped | — | — | 2026-05-21 | **Дубль**: покрыто |
| 13b | `cfe-validate` | 9 структурных проверок CFE-специфики | `extension-operations` (`xml-gen extension validate`) | ❌ Skipped | — | — | 2026-05-21 | **Дубль**: 9 проверок один-в-один совпадают с нашим CLI |
| 14 | **`cfe-patch-method`** | Генерация `&Перед/&После/&ИзменениеИКонтроль` с автокопированием тела метода + расстановкой `#Вставка`/`#КонецВставки` | `extension-operations` (skill дополнен) | ✅ Adopted 🔧 | — | **developer-code**, reviewer | 2026-05-21 | Skill дополнен (rev.3) + `SPEC-cfe-cli-extension.md`. **🔧 ждёт Java** для под-команды `xml-gen extension patch-method`. Знает соответствие `Catalog.X.Form.Y` → `Catalogs/X/Forms/Y/Ext/Form/Module.bsl` |
| 15 | `template-add` | Регистрация макета у объекта метаданных | `xml-generation/epf-full/` (§4, references/templates.md) | ✅ Adopted 🔧 | — | **developer-code** | 2026-05-22 | Слито в epf-full (слияние #6, §4). **🔧 ждёт Java** для `xml-gen template add` |
| 16 | `template-remove` | Удаление макета | `xml-generation/epf-full/` (§4, references/templates.md) | ✅ Adopted 🔧 | — | developer-code | 2026-05-22 | Слито в epf-full (слияние #6, §4) |
| 17 | `help-add` | Регистрация встроенной справки | `xml-generation/epf-full/` (§4, references/templates.md) | ✅ Adopted 🔧 | — | developer-code | 2026-05-22 | Слито в epf-full (слияние #6, §4) |
| 18 | `interface-edit` | Правка `CommandInterface.xml` | `xml-generation/subsystem-interface/` | ✅ Adopted 🔧 | — | developer-code, architect | 2026-05-22 | Skill объединён в subsystem-interface (слияние #4). **🔧 ждёт Java** для `xml-gen interface edit` |
| 19 | `interface-validate` | Валидация командного интерфейса | `xml-generation/subsystem-interface/` | ✅ Adopted 🔧 | — | developer-code, reviewer | 2026-05-22 | Skill объединён в subsystem-interface (слияние #4). **🔧 ждёт Java** для `xml-gen interface validate` |
| 20 | `img-grid` | Сетка на скриншот для оценки MXL | `tool-usage/browser-ui/img-grid/` + `tools/img-grid/grid.py` | ✅ Adopted | — | developer-code, debugger | 2026-05-21 | **Skill + рабочий Python-скрипт (~80 строк, Pillow)** — реализация полная, не Java |
| 21 | `web-test` (регресс-движок) | Оркестратор регрессов Playwright | частично `web-test-1c` | 🔶 Partial | **P1** | **tester**, scenario-coder | 2026-05-21 | У нас базовый web-test, без оркестрации регрессов |
| 22 | `web-test` (видео-инструкции TTS) | Видео с субтитрами | — | 📋 Planned | P2 | tester, scenario-author | 2026-05-21 | **Решение пользователя 2026-05-21:** добавить как **references к нашему `web-test-1c`** (чтоб было). **Приоритетная реализация — через Vanessa Automation** (если доступна) — у неё есть запись видео + субтитры из коробки. Playwright-вариант Широкова — fallback |
| 23 | `web-publish` | Portable Apache + publish | — | ❌ Skipped | — | — | 2026-05-21 | Windows-паттерн; на Linux webinst напрямую |
| 24 | `web-info` | Список публикаций | — | ❌ Skipped | — | — | 2026-05-21 | |
| 25 | `web-stop` | Остановка веб-сервера | — | ❌ Skipped | — | — | 2026-05-21 | |
| 26 | `web-unpublish` | Снять публикацию | — | ❌ Skipped | — | — | 2026-05-21 | |
| 27 | `cf-init` | Scaffold пустой конфигурации | `config-operations` | ❌ Skipped | — | — | 2026-05-21 | Покрыто |
| 28 | `cf-edit` | Правки `Configuration.xml` | `config-operations` | ❌ Skipped | — | — | 2026-05-21 | Покрыто |

### 3.3. Изменения upstream с момента последней сверки (`86c8440` → `6e14f25`)

| Навык SH | Что добавилось | Влияние на нас |
|----------|----------------|----------------|
| `skd-edit` | Новые patch-операции: `set-field-role`, `modify-structure`, `availableValue` (replace), `clear-conditionalAppearance`, `add-total` (не-агрегатные), `patch-query @once`, флаги `@hidden`/`@always` | ✅ Adopted 🔧 (rev.3): создан skill `xml-generation/skd-edit/` (SKILL.md + 5 references). **🔧 ждёт Java** |
| `form-validate` | Резолв сложных DataPath: `Items.<Table>.CurrentData.*`, `~<Attr>.*`, silent-skip числовых индексов и UUID | ✅ Adopted 🔧 (rev.3): skill `forms/form-validate/` дополнен. **🔧 ждёт Java-расширения валидатора** |
| `meta-edit` | JSON batch mode через `DefinitionFile` | ✅ Adopted 🔧 (rev.3): создан reference `meta-operations/references/batch-patch.md`. **🔧 ждёт Java** для `--batch <file.json>` |
| `web-test` | Регресс-оркестратор (`f91b569`, `e93185c`) + видео с TTS | ✅ Adopted (rev.3): skill `browser-ui/web-test-1c/` дополнен references `regress.md` + `recording.md` |

---

## 4. Полная матрица: Unica (UN) → наш фреймворк

Базовые cf/cfe/form/meta/mxl/skd/role/subsystem — отзеркалены от Широкова (см. §3). Здесь — **только 19 уникальных навыков Unica**.

| # | Навык UN | Что делает | Наш эквивалент | Статус | Приоритет | Целевые subagents | Last sync | Заметка |
|---|----------|------------|----------------|--------|-----------|-------------------|-----------|---------|
| 1 | **`api-design`** | Классификация экспортных методов БСП + правила обратной совместимости | `bsl-practices/api-design/` | ✅ Adopted | — | **architect**, reviewer, developer-code | 2026-05-21 | Skill готов (rev.3). Knowledge-only, без xml-gen |
| 2 | `autonomous-server` | Локальный автономный контур: тонкий клиент 1С + клиентский MCP-сервер внутри сеанса (TCP, порт по умолчанию 1550) + расширение `tools.client_mcp.extension`. **НЕ** 1С debug-server, **НЕ** HTTP-публикация | частично `v8-runner`, `v8-session-manager`, `mcp-onec-test-runner` | 👁 Watching | P2/roadmap | (developer-code, debugger) | 2026-05-21 | **Уточнено rev.2:** под капотом — open-source `alkoleft/v8-runner-rust` (LGPL, тот же, что у нас). Единственный закрытый кусок — расширение БСП, поднимающее MCP-сервер из сеанса тонкого клиента. **Воспроизводимо** как отдельный проект (1С-расширение с TCP-сервером в сеансе), не как заимствование skill. См. также `docs/specs-and-analisys/shirokov-and-unica-delta-2026-05-21.md` §4.1 |
| 3 | **`background-jobs`** | Идемпотентность, retry, локи, checkpointing | `bsl-practices/background-jobs/` | ✅ Adopted | — | **architect**, developer-code | 2026-05-21 | Skill готов (rev.3). Knowledge-only |
| 4 | `bsp-patterns` | Длительные операции, профили, безопасное хранение | `ssl-patterns` (дополнен) | ✅ Adopted | — | architect, developer-code | 2026-05-21 | Дополнено 4 БСП-паттерна (rev.3) |
| 5 | `code-diagnostics` | АПК/EDT/BSL LS + интерпретация suppression-маркеров как evidence | `syntax-checking` (дополнен) | ✅ Adopted | — | reviewer, developer-code | 2026-05-21 | Дополнено разделом «Suppression-маркеры как evidence» (rev.3) |
| 6 | `code-review` | Findings-first BSL review с категориями high-risk patterns | `subagents/reviewer.md` (расширен) | ✅ Adopted | — | **reviewer** | 2026-05-21 | reviewer.md расширен (rev.3): 5 новых категорий high-risk (server/client context, broad rights, background jobs, external calls, temporary files) + обязательные pre-steps + правило `[UNVERIFIED]` |
| 7 | `code-search` | `unica.code.search` обёртка | `code-navigation` + `search-before-write` | ❌ Skipped | — | — | 2026-05-21 | Покрыто |
| 8 | **`data-exchange`** | Планы обмена, РИБ, идемпотентность, конфликты | `bsl-practices/data-exchange/` | ✅ Adopted | — | **architect**, developer-code | 2026-05-21 | Skill готов (rev.3) |
| 9 | `data-separation` | Tenant-boundaries, разделители, RLS | — | 👁 Watching | P2 | architect | 2026-05-21 | Узкая тема, нужно по запросу |
| 10 | **`db-auth-check`** | Guard перед v8-runner: hard-stop по license-паттернам, правило двух кандидатов | `v8-runner/references/auth-guard.md` (готов) | ✅ Adopted | — | все, кто использует v8-runner | 2026-05-21 | Reference готов (rev.3, ~80 строк), в `v8-runner/SKILL.md` добавлены защитное правило и ссылка. Credentials → `v8project.local.yaml` |
| 11 | **`db-performance`** | Evidence-first диагностика на стыке платформа↔СУБД (план/локи/ожидания/TEMPDB/WAL). Stop rule «не предлагать индекс без cost tradeoff». Раздельные модели PG/MSSQL/файловая | `tool-usage/diagnostics/db-performance/` | ✅ Adopted | — | **debugger**, developer-code, architect | 2026-05-21 | Skill готов (rev.3). Методологический, переписан под наш стек (ripgrep + `code-navigation` + `tech-log-analysis` + `v8-runner` вместо `unica.*`). Knowledge-only |
| 12 | **`integration-implement`** | HTTP/REST/SOAP сервисы, контракты, секреты, retry | `bsl-practices/integration-patterns/` + `auth-schemes.md` reference | ✅ Adopted | — | **architect**, developer-code | 2026-05-21 | Skill готов (rev.3), 10 правил + reference по auth-схемам (Basic/Bearer/OAuth/Certificate/HMAC). Knowledge-only |
| 13 | `log-analysis` (сценарные приёмы) | Классификация записи по типу инцидента, обязательные идентификаторы, шаблон вывода | `tech-log-analysis/references/scenarios.md` + дополнения в `event-log-analysis` | ✅ Adopted | — | debugger, tester | 2026-05-21 | Reference готов (rev.3, 8 разделов: startup/HTTP/background/auth/DBMS/lock + timeline + DBMS-улики единым блоком + missing evidence). В `event-log-analysis` добавлены связка ЖР→код + correlation id |
| 14 | `platform-help` | Справка по платформе, validation сигнатур | `buddy-prompting` (3 stop rules внесены) | ✅ Adopted | — | — | 2026-05-21 | Не отдельный skill: 3 stop rules + расширение pre-flight контекста внесены в `buddy-prompting/SKILL.md` (rev.3) |
| 15 | **`query-optimize`** | Оптимизация запросов и СКД (виртуальные/временные таблицы) | `bsl-practices/query-optimize/` | ✅ Adopted | — | **developer-code**, architect | 2026-05-21 | Skill готов (rev.3), 6 правил с BSL-примерами + чек-лист ревью. Knowledge-only |
| 16 | `release-support` | Поставка/сравнение-объединение/миграции/расширения | — | 👁 Watching | P2 | architect | 2026-05-21 | Завязан на unica.cfe.diff |
| 17 | **`security-auth-crypto`** | OpenID, сертификаты X.509, CryptoPro, TLS, ГОСТ, secret lifecycle | `bsl-practices/security/` + 4 references (secrets, crypto, auth, review-checklist) | ✅ Adopted | — | **architect**, developer-code, reviewer | 2026-05-21 | Skill готов (rev.3, ~888 строк RU). Конкретика 1С API дописана самостоятельно (МенеджерКриптографии, БезопасноеХранилище, БСП «Электронная подпись», ГОСТ Р 34.10-2012). Knowledge-only |
| 18 | `test-authoring` | Дизайн тестов YaXUnit + Vanessa | `test-writing` + `v8-runner/testing` + `vanessa-diagnostics` | ❌ Skipped | — | — | 2026-05-21 | У нас шире |
| 19 | `v8-runner` (Unica) | Обёртка `unica.runtime.execute` | `v8-runner` (свой) | ❌ Skipped | — | — | 2026-05-21 | У нас собственный |

### 4.1. Концептуальные заимствования из Unica (мета-уровень)

| # | Концепция | Что взять | Куда применить | Статус |
|---|-----------|-----------|----------------|--------|
| C1 | **Stop rules в каждом навыке** | «не делай X, даже если кажется уместным» как обязательная секция SKILL.md | Дополнить `framework-meta/skill-creator-ext` шаблоном секции | 👁 Watching (P2, low) |
| C2 | **Contract gaps reporting** | «если инструмент не даёт нужного — сообщи, не обходи» | Дополнить `framework-meta/skill-creator-ext` + ссылка на `rules/escalation-format.md` | 👁 Watching (P2, low) |

> **Критическая самооценка (rev.2, 2026-05-21):**
>
> Идея «у каждого skill должна быть секция Stop rules» — **умеренной ценности**. У нас уже формализован «не обходи, эскалируй» через `framework/rules/no-direct-db-access.md`, `no-manual-xml-edit.md`, `escalation-format.md`, `agent-context-protocol.md`. Реальная новизна Unica — **структурное требование к самому формату SKILL.md** (как `## Use when` или `## Procedure`).
>
> Это дёшево добавить в `skill-creator-ext` шаблоном, но **не критично** — у нас агент уже дисциплинирован существующими правилами. Эффект — упорядочение, а не закрытие функциональной дыры. Поэтому статус понижен до 👁 Watching: добавим, когда будет ближайший рефакторинг шаблона skill-creator. Отдельной задачей не открываем.

---

## 5. Сводка по приоритетам (rev.2, 2026-05-21)

### P0 (взять немедленно)

| Источник | Навык / артефакт | Целевой путь | Целевые subagents |
|----------|-------------------|--------------|-------------------|
| UN | `api-design` | `bsl-practices/api-design/` | architect, reviewer, developer-code |
| UN | `background-jobs` | `bsl-practices/background-jobs/` | architect, developer-code |
| UN | `integration-implement` | `bsl-practices/integration-patterns/` | architect, developer-code |
| UN | `data-exchange` | `bsl-practices/data-exchange/` | architect, developer-code |
| UN | `db-auth-check` → **reference внутри v8-runner** | `tool-usage/v8-runner/references/auth-guard.md` | все, кто использует v8-runner |
| SH | `epf-bsp-init` + `epf-bsp-add-command` | `xml-generation/epf-full/` (references/epf-bsp.md) | developer-code, architect |

### P1 (запланировать)

| Источник | Навык / артефакт | Целевой путь | Целевые subagents |
|----------|-------------------|--------------|-------------------|
| SH | `cfe-borrow --borrow-main-attribute` + `cfe-patch-method` → **расширение CLI**, не отдельные skill | `tools/xml-gen/` (новые ключи + под-команда `extension patch-method`) | developer-code, reviewer |
| SH | `template-add` + `template-remove` + `help-add` | `xml-generation/epf-full/` (references/templates.md) | developer-code |
| SH | `skd-edit` patch-операции (только у Широкова, Unica устарела) | `xml-generation/skd-edit/` | developer-code, reviewer |
| SH | расширенный `skd-dsl` (compile) до уровня Широкова | переработка `xml-generation/skd-dsl/` | developer-code |
| SH | `skd-info` 11 режимов (особенно `trace`) | расширение `xml-generation/skd-dsl/` | reviewer, architect |
| SH | **MXL — полная переработка под канон Широкова** (`docs/mxl-dsl-spec.md` + snapshot-тесты как приёмка) | переработка `xml-generation/mxl-dsl/` | developer-code, debugger, explorer, reviewer, tester, architect, analyst |
| UN | `code-review` → **дополнение reviewer**, не новый skill (5 категорий + pre-steps + `[UNVERIFIED]`) | `framework/subagents/reviewer.md` + точечно `bsl-practices/*` | reviewer |
| UN | `db-performance` + `query-optimize` | `tool-usage/diagnostics/db-performance/` + `bsl-practices/query-optimize/` | debugger, developer-code, architect |
| UN | **`security-auth-crypto`** (узкие технологические навыки) | `bsl-practices/security/` (поддиректория с references) | architect, developer-code, reviewer |
| SH | `web-test` регресс-движок | дополнение `browser-ui/web-test-1c/` | tester, scenario-coder |

### P2 (при случае)

| Источник | Что взять | Целевые subagents |
|----------|-----------|-------------------|
| SH | `interface-edit` + `interface-validate` | developer-code, reviewer |
| SH | `img-grid` (Pillow утилита) | developer-code |
| SH | `form-validate` глубокий DataPath-резолв (дополнение к нашему) | developer-code, reviewer |
| SH | `meta-edit` JSON batch mode (концепция patch-операций для meta) | developer-code |
| SH | `web-test` (видео+субтитры) — references к нашему `web-test-1c`, приоритет реализации **через Vanessa** | tester, scenario-author |
| UN | `code-diagnostics` suppression-маркеры → раздел в `syntax-checking/SKILL.md` | reviewer, developer-code |
| UN | `log-analysis` сценарные приёмы → `tech-log-analysis/references/scenarios.md` + 2 пункта в `event-log-analysis` | debugger, tester |
| UN | `bsp-patterns` сверить с нашим `ssl-patterns` | architect, developer-code |
| UN | `platform-help` 3 stop rules → дополнить `buddy-prompting/SKILL.md` | developer-code, architect |

### Watching (без приоритета, наблюдаем)

- UN `data-separation` — мультитенант (узкая тема, при запросе)
- UN `release-support` — поставка/миграции (узко)
- UN `autonomous-server` — концептуально воспроизводимо (`v8-runner-rust` + клиентский MCP в сеансе тонкого клиента), но требует разработки расширения БСП с TCP-сервером. **Отдельный трек в roadmap**, не skill-заимствование
- C1, C2 — Stop rules + contract gaps как обязательная секция SKILL.md (умеренной ценности, при ближайшем рефакторинге `skill-creator-ext`)

### Skipped (намеренно отвергнуто)

- **SH db-* группа (9 навыков)** — **решение пользователя 2026-05-21:** покрывается нашим `v8-runner`; чего нет — не актуально
- SH `cf-init`, `cf-edit`, `cfe-init`, `cfe-validate`, `cfe-diff` — все дубли нашего `extension-operations` / `config-operations`
- SH `web-publish` / `web-info` / `web-stop` / `web-unpublish` — Windows portable Apache; на Linux у нас webinst
- UN `code-search` — дублирует `code-navigation` + `search-before-write`
- UN `v8-runner` — у нас собственный
- UN `test-authoring` — наше покрытие шире
- UN **`platform-help`** — дублируется с `buddy-prompting` + MCP `1c-copilot-proxy`. Берём только 3 stop rules в `buddy-prompting`

---

## 6. Распределение P0/P1-навыков по subagents (target state, rev.2)

После выполнения P0+P1 фронтматтер агентов получит следующие новые `skills:` (только дополнения, существующие не показаны):

| Subagent | Новые skills (после P0+P1) |
|----------|----------------------------|
| `analyst` | `mxl-info` (для постановки задач по доработке ПФ) |
| **`architect`** | `api-design`, `integration-patterns`, `data-exchange`, `background-jobs`, `query-optimize`, `db-performance`, `epf-full` (для дизайна, §5 BSP), `security`, `skd-info` (режим `trace`) |
| **`developer-code`** | `api-design`, `integration-patterns`, `data-exchange`, `background-jobs`, `epf-full` (init + templates + BSP-регистрация), `skd-edit`, переработанные `mxl-dsl` + `skd-dsl`, `query-optimize`, `security`. **Без db-* (отвергнуты).** Использует расширенный `xml-gen extension` (с `--borrow-main-attribute` и `patch-method`) |
| `developer-tests` | (без новых; auth-guard приходит через reference в v8-runner) |
| **`reviewer`** | Дополнения в существующий `reviewer.md` (5 категорий high-risk: server/client context, broad rights, background jobs, external calls, temporary files + правило `[UNVERIFIED]` + pre-steps). Плюс `api-design`, `security`, `skd-validate`, `mxl-validate`, suppression-маркеры в `syntax-checking` |
| **`tester`** | `web-test` регресс-движок, `mxl-validate` (pre-condition печати) |
| **`debugger`** | `db-performance`, `mxl-decompile` + `mxl-info` (reverse-engineering чужих ПФ), `tech-log-analysis/references/scenarios.md` (новый reference) |
| **`explorer`** | `mxl-decompile` + `mxl-info` |
| `scenario-author` | `web-test` регресс-движок (включая video/субтитры через Vanessa) |
| `scenario-coder` | `web-test` регресс-движок |

**Прим.:** строка «всё, что использует v8-runner» (для `auth-guard`) — это не отдельный skill в `skills:`, а **reference внутри v8-runner**. Агенты, уже подключившие `v8-runner`, автоматически получат `auth-guard` при необходимости.

---

## 7. Процесс обновления документа

### При очередной сверке с upstream

1. Запустить команды из §1 для получения свежих коммитов и списков навыков.
2. Сравнить со списком в §3/§4:
   - **Новые навыки** → добавить со статусом 🆕 New + завести анализ
   - **Изменения в существующих** → дописать в §3.3 / соответствующую строку
   - **Удалённые upstream** → пометить и решить, оставлять ли у нас (если уже заимствовано)
3. Обновить `Last sync` в §1 и в строках затронутых навыков.
4. Обновить commit-хеши в §1.

### При заимствовании навыка

1. Перевести строку из 📋 Planned → ✅ Adopted (или 🔶 Partial при частичном).
2. Заполнить колонку «Наш эквивалент» точным путём.
3. Прописать новый skill в фронтматтер целевых subagents (`framework/subagents/<name>.md`, поле `skills:`).
4. Создать SPEC-документ в `docs/specs-and-analisys/` с описанием: что взято, что переписано, что осталось upstream-only.
5. Связать SPEC через ссылку в строке таблицы (колонка «Заметка»).

### При отказе от навыка

1. Перевести в ❌ Skipped с обоснованием в «Заметка».
2. Если ранее был 📋 Planned — пометить дату и причину передумывания.

### Каденс

- **Каждые 2 месяца** — полная сверка с upstream (команды из §1).
- **При завершении SPRINT** по заимствованию — точечный апдейт затронутых строк.
- **При появлении нового источника** — добавить ID в §1 и завести отдельный раздел в матрице.

---

## 8. История изменений документа

| Дата | Версия | Что изменилось |
|------|--------|----------------|
| 2026-05-21 | rev.1 | Документ создан. Базовый снапшот: SH @ `6e14f25`, UN @ `db254e4`. Все заимствования из ранее проанализированной xml-gen-группы перенесены сюда из [shirokov-to-xmlgen-mapping-2026-03-09.md](shirokov-to-xmlgen-mapping-2026-03-09.md) (сводно). Добавлены 19 уникальных навыков Unica и не-XML навыки Широкова (db-*, cfe-tools, template/help, web, БСП-обвязка, interface, img-grid, web-test). |
| 2026-05-21 | **rev.2** | Детальный разбор каждого спорного навыка отдельными сабагентами + решения пользователя. Изменения: **(1)** вся db-* группа Широкова → ❌ Skipped (покрыто `v8-runner`); **(2)** CFE-навыки уточнены — 3 из 5 — дубли (init/validate/diff), берём только `--borrow-main-attribute` и `patch-method` как расширение CLI; **(3)** MXL — переход на полную переработку под канон Широкова (`docs/mxl-dsl-spec.md`); **(4)** SKD — Unica оказалась устаревшим фасадом, берём только у Широкова, разделяем на 4 skill; **(5)** `code-review` — **не создаём новый skill**, дополняем reviewer 5 категориями; **(6)** `db-auth-check` → reference внутри v8-runner, не отдельный skill; **(7)** `platform-help` → ❌ Skipped, 3 stop rules в `buddy-prompting`; **(8)** `security-auth-crypto` → 📋 Planned P1 (по решению пользователя), путь `bsl-practices/security/`; **(9)** `autonomous-server` → 👁 Watching как отдельный roadmap-трек (open-source `v8-runner-rust` + кастомное расширение БСП с TCP-сервером); **(10)** `log-analysis` → не оверлей, а `scenarios.md` reference; **(11)** `web-test` recording — добавляем references с приоритетом реализации через Vanessa; **(12)** концептуальные C1/C2 (stop rules) → понижены до 👁 Watching после критической самооценки. **Источники rev.2:** 11 отдельных отчётов сабагентов (autonomous-server, code-diagnostics, code-review, db-auth-check, db-performance, log-analysis, platform-help, security-auth-crypto, CFE, MXL, SKD compare). |
| 2026-05-22 | **rev.6** | **Backlog xml-gen закрыт полностью.** #8 MXL миграция → ❌ **Won't fix** (функциональный паритет достигнут через 3 silent-loss фикса + 8 аддитивных заимствований из канона Широкова; оставшиеся отличия — только синтаксические, не функциональные; миграция оценена ~60-80ч за косметику). **Реализовано 9/10, Won't fix 1/10, осталось 0.** Параллельно — **консолидация skills**: все xml-gen-related skills (forms-toolkit, form-dsl, skd-dsl, skd-edit, mxl-dsl, role-dsl, config-operations, meta-operations, subsystem-interface, epf-full, extension-operations) объединены под единый router `xml-generation/SKILL.md` по принципу «1 CLI = 1 навык». Применено осушение по П1-П12 skill-drying: общий объём 2268 → 1561 строк (−31%), главный SKILL.md 239→160, skd-dsl 538→270 (−50%). Создан общий reference `references/universal-commands.md` (validate, edit replace-text, form/template/help add). Frontmatter всех 10 subagents обновлён (xml-generation вместо 13 отдельных подссылок). i18n зеркало `framework_eng/` пересинхронизировано (192/192 файла). |
| 2026-05-21 | **rev.5** | **SKD-трио (#5, #6, #7) реализовано.** Параллельно 3 сабагента (#5 opus, #6 opus, #7 sonnet) + 2 read-only аналитика по MXL. **#5 SKD edit (крупный):** 17 patch-операций, 4 новых модуля (`SkdShorthandParser` 655 строк, `SkdTypeParser`, `PatchQueryEngine`, `SkdParseException`), расширен `SkdEditor` до 920 строк. Batch atomic с rollback. **108 тестов** (58 SkdEditor + 28 ShorthandParser + 16 TypeParser + 6 PatchQuery). **#6 SKD DSL (крупный):** расширен `SkdDsl.java` до ~700 строк (DataSet Object/Union, CalculatedField, Template+drilldown, DataSetLink, FilterGroup Or/And/Not, расширенные Field/Parameter/Settings/Structure), новые модули `SkdTypeSpec`, `SkdInclude`, `SkdTemplateWriter`, `SkdFieldRoleWriter`. Поддержка `@file:`-include в строковых полях, `--include-base` в CLI. SkdValidator: новые правила SKD-108, SKD-109. **25 тестов** (14 SkdWriter + 11 TypeSpec). **#7 SKD info (средний):** 11 режимов (overview/query/fields/links/calculated/resources/params/variant/templates/trace/full), новый `info/skd/SkdTraceBuilder.java` строит граф «DataSet→CalculatedField→Resource/Total→Variant.Selection». **32 теста**. **#8 MXL отложен** — два гейт-документа готовы: `mxl-canon-comparison.md` (370 строк, рекомендация — гибрид через `"$schema":"mxl-v2"`) + `mxl-parser-provenance.md` (277 строк, парсер написан steelmorgan+AI с нуля, 0 заимствований кода Широкова, top-3 silent-loss бага: namedItem не генерируется (возможный P0), rowStyle и columnWidths молча игнорируются). **Полная сборка xml-gen: 489 тестов / 0 failures.** |
| 2026-05-21 | **rev.4** | **Скоуп «малые+средние» (6/10 пунктов) реализован в Java.** Параллельно 6 сабагентов (3 на opus, 3 на sonnet) закрыли пункты #1, #2 (P0), #3, #4, #9, #10. **Создан единый SPEC** [`SPEC-xml-gen-backlog.md`](SPEC-xml-gen-backlog.md) (12 секций, ~650 строк, маппинг в Java-модули + тесты + порядок реализации + open questions). **Новые Java-модули в `tools/xml-gen/`:** `model/BspKind/BspCommandType/BspTarget/MdoPath/MdoPathResolver/MlText/CompositeType/ConfigurationXmlReader/BslMethodExtractor`, `writer/EpfBspWriter/EpfBspApplier/TemplateWriter`, `editor/BslModuleEditor` (переиспользуется в #1 CFE patch-method). **Расширены:** `ExtensionEditor` (borrow-main-attribute + patch-method), `InterfaceEditor` + `InterfaceValidator` (алиасы set-order/set-subsystem-order/set-group-order + усиленная валидация ссылок групп), `MetaEditor` (batch JSON + modify-tabularSection + add/modify-property + MLText + composite types, транзакционно), `FormValidator` (резолв Items.X.CurrentData.*, ~Attr.*, silent-skip числовых/UUID индексов), `Commands.java` (новые подкоманды под существующими ветками, не ломая обратную совместимость). **Тесты:** 329 total (BUILD SUCCESSFUL, 0 failures). Покрытие: FormValidator +5, InterfaceEditor +21, EpfBspWriter +16, BslModuleEditor +7, TemplateWriter +14, ExtensionEditor +15, MetaEditor +16. **Skill-доки актуализированы**: убраны пометки «Java pending» в `template-operations/SKILL.md` и `meta-operations` (SKILL + batch-patch reference). **#8 MXL отложен** — установлено, что наш парсер не копия Широкова (Map-based vs array-based `columnWidths`), миграция требует отдельного сравнительного документа `mxl-canon-comparison.md`. **#5, #6, #7 SKD** — крупные эпики, не входили в скоуп текущей сессии. |
| 2026-05-21 | **rev.3** | **Массовое заимствование выполнено.** 22 сабагента в параллели + отдельный агент по форкам SteelMorgan = весь объём P0+P1+P2 перенесён. **Новые skill (12):** `api-design`, `background-jobs`, `integration-patterns` (+`auth-schemes.md`), `data-exchange`, `epf-bsp-operations`, `template-operations`, `interface-operations`, `skd-edit` (+5 references), `db-performance`, `query-optimize`, `security` (+4 references), `img-grid` (+ рабочий `tools/img-grid/grid.py`). **Переработаны skill (2):** `mxl-dsl` (+3 references, 746 строк), `skd-dsl` (+2 references, 952 строк). **Дополнения в существующих (10):** `v8-runner` (+`auth-guard.md` reference + блоки про форки SteelMorgan), `syntax-checking` (suppression-маркеры), `ssl-patterns` (4 БСП-паттерна + антидубляжная таблица), `buddy-prompting` (3 stop rules), `extension-operations` (`--borrow-main-attribute` + `patch-method`), `tech-log-analysis` (+`scenarios.md`), `event-log-analysis` (correlation id), `web-test-1c` (+`regress.md` + `recording.md`), `meta-operations` (+`batch-patch.md`), `form-validate` (DataPath-резолв). **Обновлён subagent:** `reviewer.md` (5 новых high-risk категорий + обязательные pre-steps + правило `[UNVERIFIED]`). **Создан SPEC:** `docs/specs-and-analisys/SPEC-cfe-cli-extension.md`. **Обновлены frontmatter всех 10 subagents** — новые skill прописаны в `skills:` (analyst+1, architect+10, developer-code+13, reviewer+10, tester+1, debugger+3, explorer+1). **i18n зеркало** `framework_eng/` синхронизировано через `tools/sync-skill.py --all`. **Форки SteelMorgan** (https://github.com/SteelMorgan/v8-runner-rust + https://github.com/SteelMorgan/onec-client-mcp-devkit) явно указаны в `v8-runner/SKILL.md` и в WS-разделах `project-workflows.md` / `testing.md` с пояснением «PR-ы в upstream не принимаются». **Известное замечание:** `tools/sync-skill.py` имеет фиксированный таймаут 180с — на крупных файлах (skd-dsl) приходилось временно повышать `CODEX_TIMEOUT`. Стоит вынести в опцию CLI. |

---

## 9. Инженерный долг в xml-gen 🔧

> Skill-документы написаны как **контракт ожидаемого CLI**. Реальная Java-имплементация в `tools/xml-gen/` — **отдельная инженерная работа**, не входила в скоуп rev.3.

### Сводный backlog

| # | Скоуп | Объём | Приоритет | Где описан контракт | SPEC |
|---|-------|-------|-----------|---------------------|------|
| 1 | **CFE CLI extensions** — `extension borrow --borrow-main-attribute form\|all` + новая под-команда `extension patch-method` (Before/After/Instead/ModificationAndControl с автокопированием тела метода и расстановкой `#Вставка`/`#КонецВставки`, NamePrefix) | средний | **P1** | `extension-operations/SKILL.md` | ✅ Реализовано (rev.4) — [`SPEC-cfe-cli-extension.md`](SPEC-cfe-cli-extension.md), §1 [`SPEC-xml-gen-backlog.md`](SPEC-xml-gen-backlog.md) |
| 2 | **EPF БСП-обвязка** — команды `xml-gen epf bsp-init` (генерация `СведенияОВнешнейОбработке()`) и `epf bsp-add-command` (с обработчиком под `ВидОбработки`) | средний | **P0** | `epf-full/references/epf-bsp.md` (слияние #6) | ✅ Реализовано (rev.4) — §2 [`SPEC-xml-gen-backlog.md`](SPEC-xml-gen-backlog.md) |
| 3 | **Template/Help operations** — `xml-gen template add\|remove\|add-help` (универсально для любого типа объекта метаданных: Catalog/Document/Report/...). HTML/Text/MXL/SKD/Binary типы | средний | **P1** | `epf-full/references/templates.md` (слияние #6) | ✅ Реализовано (rev.4) — §3 [`SPEC-xml-gen-backlog.md`](SPEC-xml-gen-backlog.md) |
| 4 | **Interface operations** — `xml-gen interface edit\|validate` для `CommandInterface.xml` (hide/show/place/set-order/set-subsystem-order/set-group-order) | малый | P2 | `subsystem-interface/SKILL.md` §2 | ✅ Реализовано (rev.4) — §4 [`SPEC-xml-gen-backlog.md`](SPEC-xml-gen-backlog.md). Skill слит в `subsystem-interface` (слияние #4) |
| 5 | **SKD edit (patch-операции)** — полный набор: `add-/modify-/remove-field`, `set-field-role` с kv (balanceGroupName, parentDimension, accountTypeExpression), `add-/modify-/remove-parameter` + флаги `@hidden`/`@always`/`@autoDates`, `availableValue=` (replace в modify), `add-/remove-total` (агрегаты + identity для не-агрегатов), `clear-conditionalAppearance`, `modify-structure @name=`, `set-query` (`@file.sql`), `patch-query @once`, `rename-parameter`, `reorder-parameters`, batch `;;` | **крупный** | **P1** | `skd-edit/SKILL.md` + 5 references | ✅ Реализовано (rev.5) — §5 [`SPEC-xml-gen-backlog.md`](SPEC-xml-gen-backlog.md), 17 операций, 108 тестов |
| 6 | **SKD DSL расширение (compile)** — DataSetObject, DataSetUnion, calculatedFields, templates DSL (`rows/style/widths/{param}/|/>`), groupTemplates, drilldown, ссылочные типы с `d5p1:`, расширенная типизация (`decimal(N,M)`, `,nonneg`, составные типы), роли полей `@account`/`@balance`/`@period`, `@autoDates`/`@hidden`/`@valueList`, `@always`, `availableValues`, `@file`-include, settingsVariants, dataSetLinks, presentationExpression, conditionalAppearance, группы фильтров Or/And/Not | **крупный** | **P1** | `skd-dsl/SKILL.md` | ✅ Реализовано (rev.5) — §6 [`SPEC-xml-gen-backlog.md`](SPEC-xml-gen-backlog.md), 25+ тестов (14 SkdWriter + 11 TypeSpec) |
| 7 | **SKD info** — 11 режимов: `overview` / `query` / `fields` / `links` / `calculated` / `resources` / `params` / `variant` / `templates` / `trace` / `full`. Особо `trace` — цепочка «набор → вычисление → ресурс» | средний | **P1** | `skd-dsl/references/info-modes.md` | ✅ Реализовано (rev.5) — §7 [`SPEC-xml-gen-backlog.md`](SPEC-xml-gen-backlog.md), 32 теста (26 InfoPrinter + 6 TraceBuilder) |
| 8 | **MXL полная переработка** (compile + decompile + info + validate) — новый канон Широкова: `col` 1-based позиционирование, `rowStyle` с автозаполнением, `rowspan`, `columnWidths` с диапазонами/`"Nx"`, `page A4-landscape\|A4-portrait` с автошириной, типы ячеек `text/param/template` + `detail`, `format` (`"ЧДЦ=2"`/`"ДФ=..."`), `wrap`, `underline`/`strikeout`, `empty: N`. Decompile с автоименованием стилей. Info по 4 типам областей (Rows/Columns/Rectangle/Drawing). Validate по 7 классам ошибок. Сохранить наш `--format designer\|edt` | **крупный** | **P1** | `mxl-dsl/SKILL.md` + 3 references | ❌ **Won't fix (rev.6)** — функциональный паритет достигнут через 3 silent-loss фикса + 8 аддитивных заимствований (rev.5 SKD-трио и далее). Оставшиеся отличия — чистый DSL-синтаксис (`col` 1-based vs row+col matrix, array `columnWidths` vs наш Map). Семантика та же, миграция не оправдана (~60-80ч работы за косметику). Анализ: [`mxl-canon-comparison.md`](mxl-canon-comparison.md), [`mxl-parser-provenance.md`](mxl-parser-provenance.md) |
| 9 | **Meta batch-patch** — `xml-gen meta edit --batch <file.json>` + расширенный inline `;;` (composite types, MLText editing, modify-tabularSection, add-property/modify-property) | средний | P2 | `meta-operations/references/batch-patch.md` | ✅ Реализовано (rev.4) — §9 [`SPEC-xml-gen-backlog.md`](SPEC-xml-gen-backlog.md) |
| 10 | **Form validate DataPath** — резолв `Items.<Table>.CurrentData.*`, `~<Attr>.*`, silent-skip числовых индексов и UUID | малый | P2 | `forms/form-validate/SKILL.md` | ✅ Реализовано (rev.4) — §10 [`SPEC-xml-gen-backlog.md`](SPEC-xml-gen-backlog.md) |

### Краткая статистика

| Метрика | Значение |
|---------|----------|
| Всего пунктов | 10 |
| **Крупных** (недели) | 3 — SKD edit, SKD DSL, MXL |
| **Средних** (дни) | 5 — CFE, EPF-BSP, templates, SKD info, meta batch |
| **Малых** (часы) | 2 — interface, form-validate DataPath |
| P0 | 1 — EPF-BSP |
| **P1** | **6** — CFE, templates, SKD edit, SKD DSL, SKD info, MXL |
| P2 | 3 — interface, meta batch, form-validate DataPath |
| SPEC-документов готово | Единый [`SPEC-xml-gen-backlog.md`](SPEC-xml-gen-backlog.md) (все 9 реализованных пунктов) + частный [`SPEC-cfe-cli-extension.md`](SPEC-cfe-cli-extension.md) + 2 MXL-аналитики ([canon-comparison](mxl-canon-comparison.md), [provenance](mxl-parser-provenance.md)) |
| **Реализовано в Java (rev.5+)** | **9 / 10** — #1 CFE, #2 EPF БСП, #3 Templates, #4 Interface, #5 SKD edit, #6 SKD DSL, #7 SKD info, #9 Meta batch, #10 Form validate DataPath |
| **Закрыто как Won't fix** | **1 / 10** — #8 MXL миграция (паритет через инкрементальные заимствования) |
| Осталось реализовать | **0** |

### Зависимости

- **SKD info (#7) зависит от SKD DSL (#6)** — режим `trace` требует расширенной модели набора данных
- **CFE patch-method (#1) частично зависит от bsl-generation модуля xml-gen** — для генерации BSL-кода обёртки
- **Все остальные пункты независимы** — можно делать параллельно

### Что НЕ требует Java-имплементации (✅ полностью готово)

| Skill | Реализация |
|-------|-----------|
| Knowledge-skills (api-design, background-jobs, integration-patterns, data-exchange, query-optimize, db-performance, security) | Только SKILL.md — готовы |
| Существующие skill-дополнения (syntax-checking, ssl-patterns, buddy-prompting, event-log-analysis, web-test-1c) | Только текст — готовы |
| `reviewer.md` — 5 high-risk категорий + pre-steps + `[UNVERIFIED]` | Только текст — готов |
| `auth-guard.md` reference в v8-runner | Только текст — готов |
| `img-grid` | **Skill + рабочий Python-скрипт** `tools/img-grid/grid.py` — готовы, smoke-тест пройден |
| Форки SteelMorgan в `v8-runner/SKILL.md` + WS-references | Только текст — готовы |

### Процесс продвижения backlog

1. Когда пункт берётся в работу — создать `docs/specs-and-analisys/SPEC-<scope>.md` по образцу `SPEC-cfe-cli-extension.md`
2. Поднять маркер 🔧 → 🔨 (в работе) в соответствующей строке таблиц §3/§4
3. После мерджа — снять маркер 🔧, оставить ✅ Adopted
4. Раз в квартал — снимок прогресса в §8 (changelog как rev.X)

---

## Связанные документы

- [shirokov-and-unica-delta-2026-05-21.md](shirokov-and-unica-delta-2026-05-21.md) — **анализ** новых навыков (с описаниями для агентов)
- [sources-analysis.md](sources-analysis.md) — критический анализ источников целиком
- [shirokov-to-xmlgen-mapping-2026-03-09.md](shirokov-to-xmlgen-mapping-2026-03-09.md) — детальный маппинг xml-gen группы
- [xml-gen-gap-analysis-2026-03-16.md](xml-gen-gap-analysis-2026-03-16.md) — gap-анализ xml-gen глубины
- [xml-gen-expansion-plan-2026-03-09.md](xml-gen-expansion-plan-2026-03-09.md) — план расширения xml-gen
- [xml-gen-delta-vs-shirokov-2026-03-05.md](xml-gen-delta-vs-shirokov-2026-03-05.md) — первичный анализ xml-gen
