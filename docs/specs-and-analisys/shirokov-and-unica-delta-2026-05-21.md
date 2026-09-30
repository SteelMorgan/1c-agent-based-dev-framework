# Delta: новые навыки Широкова + Unica vs наш фреймворк

> Дата анализа: `2026-05-21`
> Источники:
> - `Nikolay-Shirokov/cc-1c-skills` @ `6e14f25` (main, 67 навыков)
> - `IngvarConsulting/unica` @ `db254e4` (main, плагин `plugins/unica/`, 65 навыков)
> Базовая точка для Широкова: предыдущая сверка — `xml-gen-gap-analysis-2026-03-16.md` (commit `86c8440`)
> Базовая точка для Unica: первое включение в анализ
>
> **Scope:** Документ покрывает навыки **за пределами xml-gen** (управление ИБ, web, БСП-обвязка, инструменты CFE-дева, регресс-движок и т.п.), а также 19 уникальных навыков Unica, отсутствующих у Широкова. XML-генерация уже разобрана в [shirokov-to-xmlgen-mapping-2026-03-09.md](shirokov-to-xmlgen-mapping-2026-03-09.md) и [xml-gen-gap-analysis-2026-03-16.md](xml-gen-gap-analysis-2026-03-16.md).
>
> **Методология:**
> - Список навыков снят через `gh api`, описания — из SKILL.md
> - Проверены последние ~25 коммитов upstream-репо, чтобы выявить новые направления
> - Каждый навык сопоставлен с нашим эквивалентом (skill / agent / правило)
> - Для каждого нового навыка указаны рекомендуемые целевые subagents

---

## 1. Архитектурные различия

| Аспект | Широков | Unica | Наш фреймворк |
|--------|---------|-------|---------------|
| Структура | `.claude/skills/<skill>/SKILL.md` + PS1/PY | `plugins/unica/skills/<skill>/SKILL.md` (фасады поверх MCP) | `framework/skills/<category>/<skill>/SKILL.md` |
| Реализация | PowerShell (master) + Python port (`port-claude-code-py`) | Тонкие фасады поверх MCP `unica.*` | Кросс-платформенные CLI (Python/Java/Rust) + чистые knowledge-skills |
| Кросс-платформенность | Частично (PS1 — Windows, Python — обе) | Зависит от MCP-сервера unica (закрытый) | Цель: Linux+Windows из коробки |
| Стиль skill-инструкций | Прозаический «что писать / как думать» | «Stop rules + contract gaps» (дисциплина) | Прозаический + checklists; stop rules локально |
| Модель агентов | Один Claude Code, без декомпозиции | Один агент, чёткие contract-обязательства | 10 subagents (analyst/architect/developer-code/…) |
| База расширений | Свой стек | Форк Широкова + надстройки | Заимствование через xml-gen + bsl-practices |

---

## 2. Совсем новые направления Широкова (вне xml-gen)

Эти направления **не покрыты** предыдущими сверками — они выходят за рамки XML-генерации.

### 2.1. DB-группа — CLI lifecycle информационной базы

| Навык | Что делает |
|-------|------------|
| `db-create` | Создаёт новую ИБ через DESIGNER |
| `db-list` | Перечисляет известные ИБ из реестра |
| `db-dump-cf` | Выгрузка ИБ → `.cf` |
| `db-dump-xml` | Выгрузка ИБ → XML (Designer формат) |
| `db-load-cf` | Загрузка `.cf` в ИБ |
| `db-load-xml` | Загрузка XML в ИБ |
| **`db-load-git`** | Частичная загрузка по `git diff` (Working/Staged/CommitRange) — **уникальный кейс** |
| `db-run` | Запуск 1С:Предприятие на ИБ |
| `db-update` | Обновление конфигурации БД |
| Реестр `.v8-project.json` | Маппинг «git-ветка → ИБ» — единая точка адресации баз |

**Зачем это нужно агенту:** автоматизация цикла «изменил XML → собрал в .cf → загрузил в ИБ → запустил тесты → удалил/откатил» без ручного управления через DESIGNER UI. `db-load-git` особенно ценен: позволяет агенту загружать в ИБ **только то, что изменилось в текущей ветке**, ускоряя цикл feedback.

**Целевые subagents:**
- **`developer-code`** — после правок XML/кода обновляет ИБ, чтобы прогнать syntax-check и тесты
- **`developer-tests`** — изолированная ИБ под прогон unit-тестов
- **`tester`** — отдельные ИБ под YaxUnit/Vanessa регрессы (изолировано от dev-ИБ)
- **`debugger`** — копия ИБ для воспроизведения бага без риска для основной
- **`scenario-coder`** — ИБ под прогон Vanessa-сценариев

**Оценка востребованности:** 🔴 **Высокая**. У нас есть только `v8-runner` (запуск 1С:Предприятие) и `v8-session-manager` (управление сессиями), но нет реестра баз + git-биндинга. Это закрывает большую дыру в воркфлоу «много веток ↔ много баз».

---

### 2.2. БСП-обвязка для внешних обработок

| Навык | Что делает |
|-------|------------|
| `epf-bsp-init` | Генерирует `СведенияОВнешнейОбработке()` в модуле объекта EPF |
| `epf-bsp-add-command` | Добавляет команду БСП (ЗаполнениеОбъекта, ПечатнаяФорма, Отчёт, СозданиеСвязанныхОбъектов, …) с обработчиком |

**Зачем это нужно агенту:** при создании внешней обработки для конфигурации на БСП нужна стандартная обвязка — иначе обработка не подхватится подсистемой «Дополнительные отчёты и обработки». Сейчас агент должен помнить шаблон вручную; этот навык даёт **готовый scaffold**.

**Целевые subagents:**
- **`developer-code`** — основной потребитель: пишет EPF, добавляет команды
- **`architect`** — определяет, какие команды БСП нужны в техническом дизайне

**Оценка востребованности:** 🔴 **Высокая**. Чистый BSL, кросс-платформенно (никакой DESIGNER не нужен). Частая задача в проектах на типовых.

---

### 2.3. Инструменты CFE-разработчика

| Навык | Что делает |
|-------|------------|
| `cfe-borrow` | Автоматическое заимствование объектов из типовой в расширение (`ObjectBelonging=Adopted`, `ExtendedConfigurationObject`, проброс ChildObjects) |
| `cfe-diff` | Сравнение расширения и типовой. **Mode A:** что отличается; **Mode B:** проверка, что вставки переехали в новую версию типовой |
| `cfe-patch-method` | Генерация обёрток `&Перед` / `&После` / `&ИзменениеИКонтроль` / `&Вместо` с переносом контекста |

**Зачем это нужно агенту:** расширения 1С — это особая дисциплина. Заимствование объектов требует точного знания XML-разметки, а написание `&Перед/&После` — корректной передачи параметров. Эти навыки превращают агента из «писателя BSL» в **CFE-разработчика**.

**Особо: `cfe-diff Mode B`** — уникальная вещь. После обновления типовой расширение может «потерять» вставки в перепакованные методы. Mode B проверяет: «все ли мои &Вместо/&После всё ещё применяются к корректным точкам в новой версии типовой?»

**Целевые subagents:**
- **`developer-code`** — основной: пишет расширения
- **`architect`** — выбор стратегии (заимствование vs новый объект, &Перед vs &Вместо)
- **`reviewer`** — проверка корректности заимствований и обёрток
- **`debugger`** — при поиске «почему расширение перестало работать после обновления типовой» использует `cfe-diff Mode B`

**Оценка востребованности:** 🔴 **Высокая**. У нас есть `extension-operations` (xml-generation), но это **генератор низкого уровня**, а здесь — **инструменты CFE-дева верхнего уровня**.

---

### 2.4. Регистрация макетов и справки

| Навык | Что делает |
|-------|------------|
| `template-add` | Добавляет макет любого типа (HTML/Text/MXL/SKD/BinaryData) к произвольному объекту метаданных (Catalog/Document/Report/…) с регистрацией в корневом XML |
| `template-remove` | Удаляет макет из объекта (с очисткой ссылок) |
| `help-add` | Добавляет встроенную справку (HTM/MXL) с регистрацией |

**Зачем это нужно агенту:** у нас есть отдельные DSL для генерации MXL/SKD как файлов, но **регистрация макета у конкретного объекта** (т.е. правка корневого `Document.xml` + `ChildObjects` + содержимое макета) — отсутствует. Аналогично со справкой.

**Целевые subagents:**
- **`developer-code`** — основной (добавляет печатные формы, формы оплаты, справку)
- **`architect`** — планирует структуру отчётности/печатных форм

**Оценка востребованности:** 🔴 **Высокая**. Кросс-платформенно (только XML-edit), частая задача.

---

### 2.5. Web-test регресс-движок и видео-инструкции

| Навык | Что делает |
|-------|------------|
| `web-test` | Playwright-движок для веб-клиента 1С: `navigateSection`, `openCommand`, `fillFields`, `assertField`. Включает **регресс-оркестратор** (последние коммиты `f91b569`, `e93185c`) и запись видеоинструкций с TTS/субтитрами |

**Зачем это нужно агенту:** наш `tool-usage/browser-ui/web-test-1c` покрывает запуск Playwright, но не оркестрацию регрессов и не видеозапись.

**Целевые subagents:**
- **`tester`** — регресс-движок (прогон серии веб-тестов)
- **`scenario-author`** / **`scenario-coder`** — генерация .feature-сценариев, конвертируемых в web-test шаги
- **`developer-tests`** — авторинг отдельных тестов

**Оценка востребованности:** 🟡 **Высокая (регресс), Средняя (видео)**. Регресс-оркестратор — реальная дыра. Видео с TTS — нишево, отложить.

---

### 2.6. Утилиты

| Навык | Что делает | Целевые агенты | Оценка |
|-------|------------|----------------|--------|
| `interface-edit` / `interface-validate` | Правка `CommandInterface.xml` подсистем (hide/show/place/order команд в командном интерфейсе) | `developer-code`, `architect` | 🟡 Средняя |
| `img-grid` | Накладывает пронумерованную сетку на скриншот для оценки пропорций колонок при ручной генерации MXL по скриншоту | `developer-code` (при reverse-engineering печатных форм) | 🟡 Средняя (мелкая утилита, ~30 строк на Pillow) |
| `web-publish` / `web-info` / `web-stop` / `web-unpublish` | Portable Apache + idempotent публикация веб-клиента | — | 🟢 Низкая (Windows-паттерн; на Linux мы публикуем через webinst) |
| `cf-init` / `cf-edit` | Scaffold пустой конфигурации, базовые правки `Configuration.xml` | — | 🟢 Низкая (покрыто нашим `config-operations`) |

---

## 3. Изменения в существующих направлениях Широкова

С момента предыдущей сверки (`xml-gen-gap-analysis-2026-03-16.md`, commit `86c8440`) в upstream появились **новые операции** в уже известных навыках.

### 3.1. SKD: patch-операции — наиболее значимое концептуальное изменение

`skd-edit` обзавёлся атомарными операциями:

| Операция | Что делает |
|----------|------------|
| `set-field-role` | Меняет роль поля (Измерение/Ресурс/Реквизит) |
| `modify-structure` | Правит структуру вывода (группировки, поля) |
| `availableValue` | Управление доступными значениями параметра (replace-семантика) |
| `clear-conditionalAppearance` | Очищает условное оформление |
| `add-total` | Добавляет итог (в т.ч. для **не-агрегатных** функций) |
| `patch-query @once` | Точечная правка текста запроса с assert «ровно одно вхождение» (фейлит, если паттерн совпадает 0 или >1 раз) |
| Флаги `@hidden` / `@always` | Управление видимостью полей |

**Концептуальное:** Широков переходит от **«перегенерируй СКД из JSON DSL»** к **атомарным правкам существующего XML с assert-инвариантами**. Это даёт агенту дисциплину «вмешайся минимально, провалится явно».

**Оценка для нас:** 🔴 **Высокая ценность концепции**. Наш `skd-dsl` — генератор «с нуля». Нужен `skd-edit` как отдельный навык с patch-операциями.

**Целевые subagents:** `developer-code` (основной), `reviewer` (проверка через `@once` инвариант).

### 3.2. Form: глубокий резолв DataPath в `form-validate`

Коммиты `8b0f55f`, `54cbc69` добавили резолв сложных DataPath:
- `Items.<Table>.CurrentData.*` (с проверкой существования колонки в табличной части)
- `~<Attr>.*` (короткая запись)
- Silent-skip числовых индексов и UUID

**Оценка:** 🟡 **Средняя**. Дополнить наш `form-validate`.

### 3.3. Meta: JSON batch mode в `meta-edit`

Комбинированные операции через `DefinitionFile` (JSON со списком операций). У нас `meta-operations` — генератор «с нуля», не патчер.

**Оценка:** 🟡 **Средняя**. Концептуально те же patch-операции, что и в SKD — стоит спроектировать единообразный механизм.

### 3.4. Новых направлений за последние ~25 коммитов нет

Все недавние коммиты — итерации внутри SKD-группы и регресс-движка `web-test`. Никаких новых skill-папок.

---

## 4. Уникальные навыки Unica (которых нет у Широкова)

Unica — форк/расширение Широкова с собственным MCP `unica.*`. Базовые cf/cfe/form/meta/mxl/skd взяты у Широкова. **19 уникальных навыков** — это надстройка с фокусом на BSP, аналитику, ревью, диагностику.

### 4.1. Архитектурные / API skills

#### `api-design` — Классификация экспортных методов и совместимость

**Что делает:** регламентирует, какие методы относятся к **Программному / Служебному / Переопределяемому интерфейсу** (5 категорий БСП), и какие правила обратной совместимости применяются (что ломает build, что — version bump, как помечать deprecated, как добавлять обязательные параметры).

**Целевые subagents:**
- **`architect`** — основной: проектирует API при техническом дизайне
- **`reviewer`** — проверяет ревью на нарушение классификации и совместимости
- **`developer-code`** — соблюдает классификацию при реализации

**Оценка:** 🔴 **Высокая**. Чистая инструкция, нулевая зависимость от MCP unica.

#### `integration-implement` — HTTP/REST/SOAP-сервисы

**Что делает:** контракты endpoint, аутентификация (basic/token/OAuth), идемпотентность, retry, error shape, секреты, версионирование интерфейса.

**Целевые subagents:**
- **`architect`** — дизайн контракта
- **`developer-code`** — реализация HTTP/REST/SOAP

**Оценка:** 🔴 **Высокая**. Серьёзный пробел нашего фреймворка.

#### `data-exchange` — Планы обмена, РИБ, конфликты

**Что делает:** планы обмена, регистрация изменений, идемпотентность пакетов, разрешение конфликтов, узлы РИБ.

**Целевые subagents:**
- **`architect`** — выбор стратегии обмена
- **`developer-code`** — реализация процедур обмена

**Оценка:** 🔴 **Высокая**. Закрывает реальную дыру.

#### `background-jobs` — Регламентные и фоновые задания

**Что делает:** идемпотентность, retry-policy, локи, checkpointing, restart-safety, разделение retryable vs permanent errors.

**Целевые subagents:**
- **`architect`** — дизайн фонового задания
- **`developer-code`** — реализация с учётом restart-safety

**Оценка:** 🔴 **Высокая**. Самодостаточный чеклист, MCP-нейтральный.

#### `bsp-patterns` — Паттерны БСП

**Что делает:** длительные операции, профили доступа, безопасное хранение (HasswordStorage), вариантная типовая обвязка.

**Целевые subagents:**
- **`architect`**, **`developer-code`**

**Оценка:** 🟡 **Средняя**. Пересекается с нашим `bsl-practices/ssl-patterns`, можно сверить и дополнить.

### 4.2. Аналитика и ревью

#### `code-review` — Single-agent BSL code-review

**Что делает:** методика **findings-first** с severity (blocker/major/minor) и file/line refs. Single-agent, в отличие от нашего `cross-provider-review` (мультиагент).

**Целевые subagents:**
- **`reviewer`** — основной

**Оценка:** 🔴 **Высокая**. У нас нет single-agent BSL review skill — `cross-provider-review` это другая операционная модель.

#### `code-diagnostics` — АПК / EDT / BSL LS + suppression-маркеры

**Что делает:** запуск диагностик и трактовка inline-disable комментариев (`// BSL-LS-ignore`, `// АПК:1`) как **evidence** (агент должен учесть, что разработчик сознательно подавил, и не дублировать findings).

**Целевые subagents:**
- **`reviewer`** — учёт suppression-маркеров при ревью
- **`developer-code`** — корректное использование подавлений (с обоснованием)

**Оценка:** 🟡 **Средняя**. Дополнить наш `syntax-checking` обработкой suppression.

#### `code-search` — Поиск кода через `unica.code.search`

**Целевые subagents:** —
**Оценка:** 🟢 **Низкая**. Полностью завязан на MCP unica. Наши `code-navigation` + `search-before-write` покрывают идею.

#### `platform-help` — Справка по платформе с validation сигнатур

**Что делает:** stop rule «не выдумывай сигнатуры платформенных методов — спроси справку».

**Целевые subagents:**
- **`developer-code`**, **`architect`**

**Оценка:** 🟡 **Средняя**. Зависит от MCP, но stop rule переносим. Пересекается с нашим `buddy-prompting`.

### 4.3. Диагностика производительности и логов

#### `db-performance` — Evidence-first диагностика медленных запросов

**Что делает:** разделение PG/MSSQL/файловой как **разных моделей доказательств**. Stop rules: «не рекомендуй индекс без predicate + join + sort + cost tradeoff». Сбор SQL/DBMS evidence, locks, deadlocks.

**Целевые subagents:**
- **`debugger`** — основной (расследование тормозов)
- **`developer-code`** — применение рекомендаций
- **`architect`** — выбор стратегии (виртуальная таблица vs материализация)

**Оценка:** 🔴 **Высокая**. Дополняет наш `query-execution` (он только про запуск запроса).

#### `query-optimize` — Оптимизация запросов и СКД

**Что делает:** виртуальные таблицы, временные таблицы, индексы, антипаттерны.

**Целевые subagents:**
- **`developer-code`**, **`architect`**

**Оценка:** 🔴 **Высокая**. Дополняет наш `query-patterns` (он про **написание**, а не оптимизацию существующих).

#### `log-analysis` — Объединённый ЖР + ТЖ timeline

**Что делает:** root-cause-first анализ с **общей лентой** ЖР + ТЖ, привязка к коду через поиск.

**Целевые subagents:**
- **`debugger`** — основной
- **`tester`** — анализ упавших регрессов

**Оценка:** 🟡 **Средняя**. У нас `event-log-analysis` и `tech-log-analysis` — раздельно. Стоит дополнить или сделать оверлей.

#### `db-auth-check` — Guard перед v8-runner

**Что делает:** проверка credentials, лицензионных stop-паттернов в выводе DESIGNER (HASP, программная лицензия, истёкший доступ), пустых паролей.

**Целевые subagents:**
- **`developer-code`**, **`developer-tests`**, **`tester`**, **`debugger`**, **`scenario-coder`** — все, кто запускает `v8-runner`

**Оценка:** 🔴 **Высокая**. Чистая инструкция без MCP-завязки. Реальный guard от тихих фейлов лицензии.

### 4.4. Узкоспециализированные

| Навык | Что делает | Целевые агенты | Оценка |
|-------|------------|----------------|--------|
| `data-separation` | Tenant-boundaries, разделители, RLS в мультитенанте | `architect` (если применимо) | 🟡 Средняя |
| `security-auth-crypto` | OpenID, сертификаты, CryptoPro, TLS, secret lifecycle | `architect` | 🟡 Средняя |
| `release-support` | Поставка / сравнение-объединение / миграции / расширения | `architect` | 🟡 Средняя |
| `autonomous-server` | Запуск 1С debug-сервера через `unica.runtime.execute clientMode=mcp` | — | 🟢 Низкая (MCP-завязка) |
| `test-authoring` | Дизайн тестов YaXUnit + Vanessa | — | 🟢 Низкая (у нас шире покрытие) |
| `v8-runner` (Unica) | Обёртка `unica.runtime.execute` | — | 🟢 Низкая (у нас собственный CLI) |

---

## 5. Концептуальный урок Unica для нас

Unica дисциплинирует агента двумя приёмами, которых нет у Широкова и у нас:

1. **Stop rules в каждом навыке** — явное «**не делай X**, даже если кажется уместным» (например: «не рекомендуй индекс без cost tradeoff», «не выдумывай сигнатуры»).
2. **Contract gaps reporting** — «если нужный инструмент не даёт нужного результата, **не обходи** — сообщи о gap».

**Рекомендация:** добавить в наш `framework-meta/skill-creator-ext` секцию «Stop rules and contract gaps». Это убирает класс ошибок «агент молча обошёл проблему».

---

## 6. Приоритизированный план заимствований

### P0 — Взять немедленно (высокая ценность, низкая стоимость портирования)

| # | Источник | Что взять | Целевой путь у нас | Целевые subagents |
|---|----------|-----------|--------------------|-------------------|
| 1 | Unica `api-design` | Skill целиком (knowledge) | `framework/skills/bsl-practices/api-design/` | architect, reviewer, developer-code |
| 2 | Unica `background-jobs` | Skill целиком (knowledge) | `framework/skills/bsl-practices/background-jobs/` | architect, developer-code |
| 3 | Unica `integration-implement` | Skill целиком (knowledge) | `framework/skills/bsl-practices/integration-patterns/` | architect, developer-code |
| 4 | Unica `data-exchange` | Skill целиком (knowledge) | `framework/skills/bsl-practices/data-exchange/` | architect, developer-code |
| 5 | Unica `db-auth-check` | Skill целиком (knowledge) | `framework/skills/tool-usage/v8-runner/references/auth-guard.md` | все, кто использует v8-runner |
| 6 | Unica `code-review` | Skill (single-agent) | `framework/skills/tool-usage/review/bsl-code-review/` | reviewer |
| 7 | Широков `epf-bsp-init`+`epf-bsp-add-command` | Портировать BSL-генератор (Python) | `framework/skills/tool-usage/platform-data/xml-generation/epf-full/references/epf-bsp.md` | developer-code, architect |

### P1 — Запланировать (высокая ценность, средняя стоимость)

| # | Источник | Что взять | Целевой путь у нас | Целевые subagents |
|---|----------|-----------|--------------------|-------------------|
| 8 | Широков `db-*` группа | Портировать (Python-ветка `port-claude-code-py`) | `framework/skills/tool-usage/platform-admin/db-*/` + реестр `.v8-project.json` | developer-code, tester, debugger, scenario-coder |
| 9 | Широков `cfe-borrow`+`cfe-diff`+`cfe-patch-method` | Портировать (Python) | `framework/skills/tool-usage/platform-data/xml-generation/cfe-tools/` | developer-code, architect, reviewer |
| 10 | Широков `template-add`+`help-add` | Портировать (XML edit, Python) | `framework/skills/tool-usage/platform-data/xml-generation/epf-full/references/templates.md` | developer-code |
| 11 | Широков `skd-edit` patch-операции | Концепция + операции в наш `skd-dsl` или новый `skd-edit` | `framework/skills/tool-usage/platform-data/xml-generation/skd-edit/` | developer-code |
| 12 | Unica `db-performance`+`query-optimize` | Skills (knowledge + integration с нашим v8-runner) | `framework/skills/tool-usage/diagnostics/db-performance/` + `bsl-practices/query-optimize/` | debugger, developer-code, architect |
| 13 | Широков `web-test` регресс-движок | Портировать оркестратор | дополнение к `framework/skills/tool-usage/browser-ui/web-test-1c/` | tester, scenario-coder |

### P2 — Сделать при случае

| # | Источник | Что взять | Целевые subagents |
|---|----------|-----------|-------------------|
| 14 | Широков `interface-edit`+`interface-validate` | Skills | developer-code |
| 15 | Широков `img-grid` | Утилита на Pillow | developer-code |
| 16 | Широков `form-validate` глубокий DataPath-резолв | Дополнить наш `form-validate` | developer-code, reviewer |
| 17 | Широков `meta-edit` JSON batch mode | Концепция patch-операций для meta | developer-code |
| 18 | Unica `code-diagnostics` suppression-маркеры | Дополнить наш `syntax-checking` | reviewer, developer-code |
| 19 | Unica `log-analysis` объединённый timeline | Дополнить наши `event-log-analysis` + `tech-log-analysis` или сделать оверлей | debugger, tester |
| 20 | Unica `bsp-patterns` | Сверить с нашим `ssl-patterns` | architect, developer-code |
| 21 | Концепция «Stop rules + contract gaps» | Дополнить `framework-meta/skill-creator-ext` | — (meta) |

### Отвергнуть

| # | Источник | Причина |
|---|----------|---------|
| — | Широков `web-publish/info/stop/unpublish` | Windows-паттерн portable Apache; на Linux у нас webinst |
| — | Широков `cf-init`/`cf-edit` | Покрыто нашим `config-operations` |
| — | Unica `autonomous-server` | Глубокая завязка на MCP `unica.runtime.execute` |
| — | Unica `code-search` | Дублирует наши `code-navigation` + `search-before-write` |
| — | Unica `v8-runner` | У нас собственный кросс-платформенный |
| — | Unica `test-authoring` | Наше покрытие шире (`test-writing` + `v8-runner/testing` + `vanessa-diagnostics`) |
| — | Широков `web-test` recording с TTS | Нишево, нет ROI |

---

## 7. Влияние на subagents — сводка

| Subagent | Получит skills | Прирост возможностей |
|----------|----------------|----------------------|
| **`analyst`** | — | Без изменений (P0 не задевает анализ требований) |
| **`architect`** | `api-design`, `integration-patterns`, `data-exchange`, `background-jobs`, `query-optimize`, `db-performance`, `bsp-patterns`, `cfe-tools` | API-дизайн, дизайн интеграций, дизайн обмена, дизайн фоновых, выбор стратегии оптимизации |
| **`developer-code`** | `api-design`, `integration-patterns`, `data-exchange`, `background-jobs`, `epf-full`, `cfe-tools`, `skd-edit`, `db-*`, `interface-edit`, `img-grid`, `query-optimize` | Реализация типовых элементов БСП, расширения, печатные формы, справка, точечные правки СКД, lifecycle ИБ |
| **`developer-tests`** | `db-*` (использование), `db-auth-check` | Изолированные ИБ под тесты |
| **`reviewer`** | `bsl-code-review`, `api-design`, `cfe-tools` (валидация заимствований), suppression-маркеры | Single-agent BSL review, контроль API-совместимости |
| **`tester`** | `db-*`, `web-test` регресс-движок, `db-auth-check`, `log-analysis` | Изолированные ИБ под YaxUnit/Vanessa регрессы, оркестрация регрессов |
| **`debugger`** | `db-performance`, `log-analysis`, `db-*` (для копии ИБ), `cfe-diff Mode B` | Диагностика тормозов, объединённый ЖР+ТЖ timeline, расследование «почему расширение перестало работать» |
| **`explorer`** | — | Без изменений |
| **`scenario-author`** | `web-test` регресс-движок | Сценарии с прицелом на оркестрацию |
| **`scenario-coder`** | `db-*`, `db-auth-check`, `web-test` регресс-движок | Прогон Vanessa на изолированной ИБ |

---

## 8. Следующие шаги

1. **Подтвердить P0-список** с пользователем.
2. Для каждого пункта P0 создать SPEC-доку в `docs/specs-and-analisys/` (формат: что заимствуем, что переписываем, какие subagents апдейтятся).
3. Параллельно — поддерживать актуальность [external-skills-mapping.md](external-skills-mapping.md) (живой документ с привязкой к коммитам upstream).
4. Раз в ~2 месяца — `gh api repos/Nikolay-Shirokov/cc-1c-skills/commits` и `gh api repos/IngvarConsulting/unica/commits` для отлова новых направлений.

---

## Связанные документы

- [sources-analysis.md](sources-analysis.md) — общий критический анализ источников
- [shirokov-to-xmlgen-mapping-2026-03-09.md](shirokov-to-xmlgen-mapping-2026-03-09.md) — маппинг xml-gen
- [xml-gen-gap-analysis-2026-03-16.md](xml-gen-gap-analysis-2026-03-16.md) — gap-анализ глубины xml-gen
- [xml-gen-expansion-plan-2026-03-09.md](xml-gen-expansion-plan-2026-03-09.md) — план расширения xml-gen
- [external-skills-mapping.md](external-skills-mapping.md) — **живой** сводный документ маппинга (создаётся параллельно с этим)
