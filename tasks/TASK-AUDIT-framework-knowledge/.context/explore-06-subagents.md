# Разведка: framework/subagents/ — полный аудит профилей

Дата: 2026-07-02. Файлов: 13 (12 профилей + README). Суммарно 2316 строк (см. `wc -l` в начале).

| Файл | Строк |
|---|---|
| orchestrator.md | 660 |
| reviewer.md | 276 |
| debugger.md | 183 |
| README.md | 222 |
| scenario-coder.md | 158 |
| tester.md | 153 |
| developer-code.md | 124 |
| analyst.md | 108 |
| architect.md | 106 |
| developer-tests.md | 96 |
| scenario-author.md | 87 |
| _template-agent.md | 69 |
| explorer.md | 74 |

---

## explorer.md
- path: `framework/subagents/explorer.md`, lines: 74
- role_purpose: Read-only исследование кодовой базы, графы вызовов, сводка для классификации сложности (Phase 0)
- structure: frontmatter (name/description/readonly/skills) → identity → Обязанности → Вход/Выход → Протокол (6 шагов) → Границы → критично-чтение-навыков блок → depends_on
- model_tier: только в README — `claude-4.5-haiku` (Economy). В самом профиле поля `model` нет.
- strengths: самый компактный и чистый профиль; чёткая граница "НЕ гадает — сообщает not found"; явный запрет на классификацию сложности (эта работа оркестратора)
- weaknesses:
  - `xml-generation` в `skills:` фронтматтере, но отсутствует в `depends_on` (см. Сквозной анализ — это не декоративный баг, а реальный разрыв в резолвинге зависимостей инсталлятора)
  - протокол не описывает формат "структурированных данных для оркестратора" — нет примера выходного `explorer-context.md`
  - не описан лимит глубины графа вызовов (сколько уровней транзитивных зависимостей — 2? 5? неограниченно) — недоопределённая ситуация для больших кодовых баз
- verbosity: low. Пример: обязанности — 3 буллета в одну строку каждый.
- пересечения: весь блок "КРИТИЧНО: Обязательное чтение навыков и правил" (строки 44-63, ~20 строк) — дословно идентичен во всех 10 профилях сабагентов (см. Сквозной анализ)

## analyst.md
- path: `framework/subagents/analyst.md`, lines: 108
- role_purpose: Анализ требований → спецификация MADR 4.0 + RFC 2119 + Test Plan + Acceptance Scenarios (Phase 1)
- structure: frontmatter → identity → Обязанности → Вход/Выход → Протокол (10 шагов) → таблица "Когда спрашивать" → Границы → делегирование Explorer → критично-блок → depends_on
- model_tier: README — `claude-4.6-opus-high-thinking` (High/Premium)
- strengths: явная таблица эскалации (clarification_needed / допущение / открытый вопрос) снимает неопределённость; конкретный пример делегирования Explorer с форматом промпта; явное разделение `platform-data-core` на Metadata Discovery vs Query Execution
- weaknesses:
  - шаг 7 "Coverage by runtime layer" вводит 4 категории (серверная/UI/процесс/интеграция), но не даёт критерия, как классифицировать пограничный случай (например, форма с серверным вызовом) — недоопределено, дублируется почти дословно в tester.md (см. пересечения)
  - "Self-review по чек-листу spec-standard" — чек-лист не инлайнится и не указано, что делать при незакрытых пунктах чек-листа (блокирует ли это `completed`?)
  - нет предела на количество раундов уточнений с Explorer (в отличие от лимита BLOCK-итераций у оркестратора)
- verbosity: med. Таблицы компактны, протокол — списком.
- пересечения: таблица "Когда спрашивать" почти идентична по структуре в architect.md; раздел "Coverage by runtime layer" почти дословно повторяется в tester.md шаг 3 (server/UI/process/integration классификация)

## architect.md
- path: `framework/subagents/architect.md`, lines: 106
- role_purpose: Технический дизайн по утверждённой спеке → technical-design.md + Task Breakdown JSON (Phase 2)
- structure: аналогична analyst.md
- model_tier: README — `claude-4.6-opus-high-thinking` (High/Premium)
- strengths: чёткое разграничение с analyst ("НЕ анализирует требования"); явный запрет ждать подтверждения пользователя (это оркестратор); "Explorer baseline" — не дублирует исследование, а базируется на explorer-context.md
- weaknesses:
  - **самое большое расхождение skills↔depends_on во всём наборе**: 8 навыков из фронтматтера (`integration-patterns`, `xml-generation`, `query-optimize`, `data-exchange`, `background-jobs`, `api-design`, `db-performance`, `security`) отсутствуют в `depends_on` — то есть при непустом `depends_on` инсталлятор (`tools/install.py:452-463`) НЕ достраивает зависимости из `skills:`, и половина продекларированных навыков архитектора физически не резолвится в `component_map`
  - "Build Task Breakdown JSON — формат «template + example» (без JSON Schema)" — отсутствие схемы означает, что разные архитекторы/сессии могут произвольно менять поля JSON, а developer-code/tests полагаются на этот файл как контракт
  - нет явного лимита на количество итераций "Explorer для углубления" (code-navigation) — можно уйти в дорогую рекурсию по графу вызовов
- verbosity: med
- пересечения: таблица "Когда спрашивать" (структура идентична analyst.md, но с другими критериями)

## developer-code.md
- path: `framework/subagents/developer-code.md`, lines: 124
- role_purpose: Green-фаза TDD — реализация BSL-кода под уже написанные тесты (Phase 3d, по факту — README называет её "3c")
- structure: frontmatter → identity → Обязанности → Вход/Выход → Протокол (9 шагов) → критическое ограничение (не работает в Designer/EDT) → Границы (10 пунктов) → критично-блок → depends_on
- model_tier: README — `gpt-5.2-xhigh` (Mid/High)
- strengths: чёткий self-fix лимит (2 попытки) с ветвлением по причине падения; явный запрет трогать `exts/YAXUNIT/**`; понятная эскалация в bug-report вместо бесконечного самовосстановления
- weaknesses:
  - **7 навыков из `skills:` отсутствуют в `depends_on`**: `integration-patterns`, `img-grid`, `query-optimize`, `data-exchange`, `background-jobs`, `api-design`, `security` — те же категории безопасности/интеграций/фоновых заданий, которые Reviewer жёстко проверяет (BLOCK-критерии по Background jobs/External calls/Broad rights) фактически недоступны разработчику как навык
  - `platform-data-core` явно исключён из работы текстом ("НЕ используется — architect уже исследовал"), но присутствует в `depends_on` как правило — избыточная запись, которая может сбить агента (зачем оно в зависимостях, если запрещено к использованию)
  - шаг 7 "If test unclear (hang/interactive error)" не определяет порог "unclear" — сколько ждать перед тем, как считать тест зависшим
  - "Запускает только тесты Phase 3b и сценарии текущей задачи" — не указано, что делать, если тесты Phase 3b сами имеют скрытые side-effects на другие модули (нет defensive-run полного regression даже частично)
- verbosity: med-high (Границы — 10 пунктов, самый длинный список ограничений среди TDD-агентов)
- пересечения: self-fix лимит (2 попытки) структурно похож на лимит tester.md (3 попытки) и scenario-coder.md (2 попытки Red-гейта) — три разных агента, три разных числа лимитов без единого источника правды на "сколько самовосстановлений допустимо на артефакт"

## developer-tests.md
- path: `framework/subagents/developer-tests.md`, lines: 96
- role_purpose: Red-фаза TDD — unit/интеграционные тесты по спецификации до реализации (Phase 3b)
- structure: аналогична предыдущим, но короче — нет отдельного раздела "критическое ограничение"
- model_tier: README — `gpt-5.2-xhigh` (Mid/High)
- strengths: чёткие префиксы именования (`unit-`/`integr-`); явный критерий "тесты ДОЛЖНЫ падать" как проверка Red-фазы; ясная граница с Tester (Phase 4) — "не покрывает edge cases сверх MUST/SHOULD"
- weaknesses:
  - skills↔depends_on совпадают полностью (нет расхождений) — единственный TDD-агент без этой проблемы
  - протокол не описывает, что делать, если написанный тест **не падает** (например, из-за уже существующей частичной реализации) — сценарий не по умолчанию Red, а developer-tests сам не запускает тесты ("НЕ запускает тесты (реализации нет)"), значит некому это заметить до Phase 3d
  - "Интеграционные тесты используют тот же YaxUnit... Юнит-тест проверяет один метод с мок-данными" — но нет описания, как в BSL/YaxUnit реализуются "мок-данные" (эта деталь молчаливо делегирована навыку `test-writing`, но неясно, знает ли модель, что мокать в 1С физически сложно/нестандартно)
- verbosity: low-med
- пересечения: таблица покрытия почти идентична developer-code (MUST/SHOULD термины)

## scenario-author.md
- path: `framework/subagents/scenario-author.md`, lines: 87
- role_purpose: Конвертация intent-сценариев спецификации в исполняемые `.feature` Vanessa (Phase 3a)
- structure: компактная — Обязанности → Вход/Выход → Протокол (8 шагов) → Границы → критично-блок → depends_on
- model_tier: README — `claude-4.5-sonnet-thinking` (не указан явный tier-класс в таблице маршрутизации оркестратора §2, только Developer/Tester = Mid/High — двусмысленно, попадает ли scenario-author в этот tier)
- strengths: самый короткий протокол среди write-агентов; чёткое правило "один сценарий = одно наблюдаемое поведение"; явный маркер `unknown_step_candidate` для передачи scenario-coder
- weaknesses:
  - README (в общей табличке моделей) описывает scenario-author, но таблица маршрутизации моделей у оркестратора (`orchestrator.md` §2 "Маршрутизация моделей") НЕ содержит отдельной строки для scenario-author/scenario-coder/debugger вообще — только "Explorer / Developer,Tester / Architect,Analyst / Reviewer" — 3 из 12 сабагентов не имеют явного tier-маппинга в главном управляющем документе
  - шаг 6 "Analyze forms if needed" ссылается на навык `form-info`, которого нет в `skills:` фронтматтере и нет в `depends_on` — упоминание навыка, не объявленного нигде как зависимость (потенциально несуществующий/переименованный навык — см. Сквозной анализ)
  - не описан лимит на количество `.feature`-файлов на группу сценариев — "один файл на группу" не определяет размер группы
- verbosity: low
- пересечения: связка с scenario-coder (Phase 3a→3c) описана в обоих файлах по-разному: scenario-author говорит "не запускает сценарии — tester (Phase 4)", хотя по факту Red-гейт запускает scenario-coder (Phase 3c), а не только tester — неточная формулировка границ

## scenario-coder.md
- path: `framework/subagents/scenario-coder.md`, lines: 158
- role_purpose: Делает `.feature` из Phase 3a исполняемыми (шаги Vanessa), поддерживает Red-гейт до появления прод-кода (Phase 3c)
- structure: самый структурированный HARD-Границы блок среди всех профилей — отдельные секции "Иерархия поиска", "Размещение новых шагов", "Универсальность vs простота", "Red-гейт (MUST)", Протокол, критично-блок, depends_on
- model_tier: не в README вообще (README таблица создана до появления этого агента) — tier неизвестен
- strengths: детальная иерархия поиска шагов (3 уровня) с порогом 80% схожести; ясный критерий отличия "шаг" от "бизнес-логика"; конкретный протокол верификации Red-гейта (сценарий должен падать по прод-причине, не по инфраструктурной)
- weaknesses:
  - **отсутствует из README.md полностью** — ни в таблице агентов, ни в разделе "Описание агентов", ни в диаграмме Workflow (см. Сквозной анализ — самая заметная несостыковка)
  - Phase-нумерация в самом файле (3c) конфликтует с README, где "Phase 3c: Реализация (Green TDD)" называет так фазу Developer-Code, а не Scenario-Coder — двусмысленность в идентификаторе фазы между документами
  - "если за 2 попытки причина зелёного Red-гейта не найдена ИЛИ шаг падает с неочевидной причиной → bug-report" — лимит 2, отличный от developer-code (тоже 2, но разного смысла — self-fix vs Red-гейт-диагностика) и tester (3) — три разных агента, три пороговых числа без общего "лимит самовосстановления" правила
- verbosity: high — самый детализированный протокол среди Phase-3 write-агентов (158 строк, больше developer-code при том, что scenario-coder логически "уже" по scope)
- пересечения: Red-гейт критерии пересекаются с TDD-политикой из `tdd-policy` (в depends_on), а также с проверкой developer-code на "тесты должны падать до реализации" — концепция Red/Green TDD описана трижды (developer-tests, scenario-coder, developer-code) в разных формулировках без единого канонического определения

## tester.md
- path: `framework/subagents/tester.md`, lines: 153
- role_purpose: Дополнение покрытия edge-case/регрессия, полный прогон, диагностика причин падений (Phase 4)
- structure: Обязанности → Вход/Выход → Именование → Протокол (9 шагов, включая вложенный под-протокол 7a/7b) → Exit criteria → Границы → критично-блок → depends_on
- model_tier: README — `claude-4.5-sonnet-thinking`
- strengths: явная таблица классификации по сигналам (test_error/implementation_error/spec_mismatch) с конкретными критериями (стек в тестовом vs бизнес-модуле); детальный Exit criteria блок — редкость среди профилей, остальные агенты не формализуют критерий "точно завершено"
- weaknesses:
  - самый длинный и вложенный протокол (шаг 8 содержит два под-протокола 7a/7b с 3 под-шагами каждый — нумерация "7a/7b" внутри "шага 8" протокола сама по себе нелогична/сбивает, т.к. подписи под-шагов начинаются заново с "1")
  - "3 попытки исправить техническую ошибку" — но не описано, что считается "попыткой": один прогон теста или одно изменение файла — расплывчато по сравнению с developer-code, где лимит явно "self-fix попыток"
  - Exit criteria требует "Vanessa green — финальный gate перед final-report", но в оркестраторе (Phase 4 обработка) нет явного шага, где оркестратор ПРОВЕРЯЕТ этот gate отдельно от обычного `completed`-статуса — неявная зависимость
- verbosity: high, местами избыточно вложенная нумерация
- пересечения: таблица "Coverage by runtime layer / Check coverage matrix" дословно похожа на analyst.md шаг 7 — кандидат на вынос в общее правило "runtime-layer-coverage"

## reviewer.md
- path: `framework/subagents/reviewer.md`, lines: 276 (самый длинный профиль после orchestrator)
- role_purpose: Ревью любого артефакта (7 scope: spec/arch/bdd/tests/code/tester/debug), классификация BLOCK/WARN/INFO
- structure: identity → "Изоляция сессий" → "При вызове" (7 шагов) → 5 отдельных чек-листов по scope (bdd/debug/tests/code + pre-steps) → "Формат вывода" → "Сводка" → Принципы → Границы → критично-блок → depends_on
- model_tier: README — `gpt-5.3-codex-xhigh`; но orchestrator §2 говорит "Premium: Reviewer (spec, arch, JSON) / High: Reviewer (code, tests, bdd)" — **два источника с разными критериями tier-градации внутри одной роли** (README даёт один tier на всю роль, orchestrator расщепляет по scope)
- strengths: очень конкретные, проверяемые BLOCK-критерии для BSL-специфики (транзакции, блокировки, server/client context, привилегированный режим); маркер `[UNVERIFIED]` с чётким форматом принуждает к доказательности, а не голословным находкам; обязательные pre-steps для scope=code (git diff → code-navigation → syntax-checking → ручной анализ) — редкий пример принудительного инструментального порядка
- weaknesses:
  - **6 навыков отсутствуют в depends_on**: `integration-patterns`, `xml-generation`, `background-jobs`, `api-design`, `security`, **`syntax-checking`** — последнее критично, т.к. reviewer сам предписывает себе "Обязательные pre-steps" через `syntax-checking`, но навык не резолвится через инсталлятор при непустом `depends_on`
  - таблица `review_scope → файл контекста` не покрывает scope `arch` в общем маппинге пояснений качества (в README он есть, но в самом reviewer.md для scope=arch нет отдельного детального чек-листа BLOCK/WARN/INFO — есть только для bdd/debug/tests/code; для `spec` и `arch` чек-листы не выписаны вообще, хотя они и есть в списке маппинга) — это существенный пробел: два самых ранних (и самых дорогих в откате) scope не имеют явных критериев
  - "Не запускает независимое ревью через cross-provider-review — это ответственность оркестратора" — но сам reviewer нигде не описывает, как передать оркестратору сигнал "нужен tiebreaker", т.е. граница односторонняя
- verbosity: high — детальные примеры (пример `[UNVERIFIED]` на 8 строк), но нет примеров для scope=spec/arch вообще
- пересечения: BLOCK-критерии по "Background jobs/External calls/Broad rights" дословно пересекаются по терминологии с "Границы" developer-code (оба знают про `УстановитьПривилегированныйРежим`, идемпотентные ключи фоновых заданий и т.д.) — специфика BSL-безопасности продублирована в двух профилях вместо общего skill/rule

## debugger.md
- path: `framework/subagents/debugger.md`, lines: 183
- role_purpose: Расследование багов в рантайме через DAP/ЖР, цикл гипотез ≤5(+3), локальный фикс или возврат/эскалация
- structure: identity → Обязанности → Вход/Выход → Протокол (11 шагов) → "Выбор DAP vs trace" → "Tech-log policy" → "Стандарты качества" → Границы (HARD, 9 пунктов) → критично-блок → depends_on
- model_tier: README не содержит debugger вообще; orchestrator.md указывает явно `model: claude-4.6-opus-high-thinking` при запуске (§3a п.3) — единственный агент, где конкретная модель прописана прямо в тексте оркестратора, а не через общий tier
- strengths: очень чёткий hypothesis loop с лимитом и условием расширения (+3 при высокой уверенности, max 8); жёсткий Cleanup-протокол (grep `AGENTDEBUG-`, `detach`/`force_detach`) — предотвращает "грязные" runtime-артефакты; ясный выбор между DAP и agent-debug по критериям безопасности остановки потока
- weaknesses:
  - **отсутствует из README.md** — как и scenario-coder, не описан ни в таблице, ни в диаграмме workflow, ни в разделе "Артефакты task_dir" (README перечисляет только `reviewer-context-{spec,arch,bdd,tests,code,tester}.md`, но не `reviewer-context-debug.md`, не `debugger-context.md`, не `task_dir/.context/debug/<bug-id>/*`, не `task_dir/.context/bugs/<bug-id>.json`)
  - 3 навыка (`xml-generation`, `db-performance`, `img-grid`) в skills-фронтматтере отсутствуют в depends_on — при этом `img-grid`/`screenshot` для дебаггера логичны (визуальная проверка UI-состояния при runtime-расследовании), поэтому пропажа из зависимостей особенно вредна именно для этого агента
  - "Локальный фикс ≤ 2 файла, ≤ 30 строк" — тот же порог, что у developer-code self-fix, но здесь считается по-другому (файлы продкода/теста раздельно) — see Сквозной анализ про разные пороги
  - Tech-log policy требует "явного согласия пользователя" для L8, но сам debugger не общается с пользователем напрямую (только через оркестратора) — процедура получения согласия (сколько ждать, что если оркестратор недоступен) не описана в этом файле
- verbosity: high, один из самых формализованных протоколов (много вложенных условий по шагам 6-9)
- пересечения: цикл гипотез (`evidence_from_trace`) и "H<N>"-нотация пробы концептуально пересекаются с `agent-debug`/`runtime-investigation` навыками (ожидаемо, т.к. это ссылочные skill, но сам протокол дублирует часть их содержания вместо чистой отсылки)

## orchestrator.md
- path: `framework/subagents/orchestrator.md`, lines: 660 (в 2.4 раза больше следующего по размеру профиля)
- role_purpose: Главный поток (Lead) — классификация задачи, выбор short/full цикла, дисциплина оркестрации (routing/gates/review/bug-routing/cross-provider/Infostart-аудит)
- structure: сильно многослойная — YAML-комментарий "способ запуска" → Слой 1 (диспетчер) → Слой 2 (дисциплина, 9 пронумерованных обязанностей + подпункты 2a/2b/2c/3a/7.1-7.4) → Протокол оркестратора (числовой скелет) → формат лога (таблица событий) → шаблон final-report → "Связанные процедуры" → depends_on. НЕ является "сабагентным" профилем — явно об этом сказано в frontmatter description.
- model_tier: N/A (главный агент, не сабагент; про модель — вариант A/B запуска через `--agent`/`--append-system-prompt`)
- strengths: детальнейший health-check протокол для фоновых сабагентов (эскалирующая шкала 5/10/15 мин, конкретный анти-паттерн из истории TASK-173); хорошо формализованный "фильтр перед эскалацией на пользователя" (принцип «делегируй, не спрашивай») с явными критериями что эскалировать/что решать самому; gate-режим cross-provider-review с evidence pack (8 обязательных пунктов) — сильная защита от "фиктивного" завершения задачи
- weaknesses:
  - **самый большой профиль — 660 строк**, при этом сам документ признаёт, что "детальные процедуры... в профиль НЕ инлайнятся" (лениво читаются через Skill/ссылку), однако Слой 2 (дисциплина) всё равно инлайнит очень много процедурной механики (весь §3a bug-routing на ~35 строк, весь §7 cross-provider на ~95 строк) — граница "durable профиль vs read-on-choice skill" на практике смещена в сторону durable сильнее, чем декларируется во вступлении
  - таблица маршрутизации моделей (§2) покрывает только 4 роли из 12 сабагентов (Explorer/Developer,Tester/Architect,Analyst/Reviewer) — scenario-author, scenario-coder, tester (отдельно от "Developer,Tester"? неясно, входит ли tester в "Developer, Tester" пару или в отдельную категорию), debugger не имеют явного tier
  - "Лимит цикла bug→fix→bug = 2" (§3a) и "макс. 1 итерация на debug-fix" (§3a п.4) — два разных по смыслу, но похожих по формулировке лимита в одном разделе; риск, что агент перепутает, какой лимит к какому событию относится
  - depends_on ссылается на `framework/skills/tool-usage/review/cross-provider-review/SKILL.md` — существует (проверено), но упомянутый в тексте `references/orchestrator-structures.md` (§5 "Управление артефактами") — путь дан БЕЗ префикса `framework/workflows/orchestrator/`, что делает его неоднозначным без знания реальной структуры (фактический путь: `framework/workflows/orchestrator/references/orchestrator-structures.md`) — потенциально запутывающая относительная ссылка (не строго "битая", т.к. файл существует, но путь в тексте неполный/неявный)
  - depends_on также ссылается на `escalation-format.md` косвенно через упоминания в тексте (`escalation-format.md`) без явного добавления в сам блок `depends_on` — файл существует (`framework/rules/escalation-format/SKILL.md`), но путь в тексте (`escalation-format.md`) не совпадает по форме с остальными путями (`.../SKILL.md`) и не выведен как формальная зависимость
- verbosity: очень high — детальные примеры логов, YAML-примеры (`cross_provider_review:` блоки), исторический анти-паттерн (TASK-173) как обоснование
- пересечения: н/п (не сабагентный профиль, но именно он ссылается на путь `framework/subagents/scenario-author.md`, `scenario-coder.md`, `debugger.md` в своём depends_on — все три существуют)

## README.md
- path: `framework/subagents/README.md`, lines: 222
- role_purpose: Индекс + диаграммы workflow для набора сабагентов
- structure: таблица агентов → описания → диаграмма "Принципы взаимодействия" → диаграмма "Рабочий поток (Full Cycle)" → таблица статусов context-файла → структура task_dir → ссылка на шаблон
- weaknesses (главные, см. Сквозной анализ):
  - **устарел относительно фактического набора файлов**: не упоминает `scenario-coder.md` и `debugger.md` — 2 из 12 актуальных сабагентов отсутствуют полностью (ни в таблице, ни в описаниях, ни в диаграмме, ни в артефактах task_dir)
  - диаграмма Full Cycle показывает фазы 0/1/2/3a+3b/3c/4, где "Phase 3c" = Developer-Code (Green TDD); но по факту (orchestrator.md, scenario-coder.md) фазы: 3a=scenario-author, 3b=developer-tests, 3c=scenario-coder, 3d=developer-code — README использует устаревшую 4-фазную схему Phase-3, а актуальная схема — 4-подфазная (3a/3b/3c/3d)
  - таблица "Артефакты task_dir" не содержит `task_dir/.context/bugs/<bug-id>.json`, `task_dir/.context/debug/<bug-id>/*`, `reviewer-context-debug.md` — весь bug-report/debugger workflow, детально описанный в orchestrator.md и debugger.md, не отражён в индексном README

---

## Сквозной анализ

### Матрица ролей (входы/выходы, кратко)

| Роль | Phase | Вход | Выход | readonly |
|---|---|---|---|---|
| explorer | 0 | задача + task_dir | explorer-context.md | true |
| analyst | 1 | задача + explorer-context.md | spec.md | true |
| architect | 2 | spec.md (утв.) + explorer-context.md | technical-design.md + task-breakdown.json | true |
| scenario-author | 3a | spec.md (Acceptance Scenarios) | .feature (intent) | false |
| developer-tests | 3b | spec.md (Test Plan) | тест-модули .bsl (RED) | false |
| scenario-coder | 3c | .feature 3a + technical-design.md | .feature (executable steps, RED) | false |
| developer-code | 3d | spec+design+breakdown+тесты 3b+.feature 3c | BSL-модули, XML (GREEN) | false |
| tester | 4 | код 3d + тесты 3b + .feature 3c + spec | доп. тесты + test-report.md | false |
| reviewer | * | артефакт + review_scope | BLOCK/WARN/INFO вердикт | true |
| debugger | * | bug-report.json (open) | debug-report.md + фикс/вердикт | false |
| orchestrator | — | задача пользователя | маршрутизация + final-report.md | n/a (main) |

### Несостыковки между профилями (подтверждено)

1. **README.md не описывает scenario-coder.md и debugger.md** — 2 из 12 актуальных сабагентов полностью отсутствуют в индексном документе (таблица, описания, диаграмма, артефакты task_dir).
2. **Нумерация фаз расходится**: README называет "Phase 3c" фазу Developer-Code (Green TDD); orchestrator.md и scenario-coder.md используют "Phase 3c" = Scenario-Coder, "Phase 3d" = Developer-Code.
3. **Массовый разрыв skills↔depends_on** (подтверждено кодом `tools/install.py:452-463`: если backmatter `depends_on` непустой — авто-достройка из `skills:` НЕ выполняется, значит навыки, отсутствующие в `depends_on`, физически не резолвятся в `component_map` при установке):
   - architect.md: 8 навыков потеряны (`integration-patterns`, `xml-generation`, `query-optimize`, `data-exchange`, `background-jobs`, `api-design`, `db-performance`, `security`)
   - developer-code.md: 7 навыков потеряны (`integration-patterns`, `img-grid`, `query-optimize`, `data-exchange`, `background-jobs`, `api-design`, `security`)
   - debugger.md: 3 навыка потеряны (`xml-generation`, `db-performance`, `img-grid`)
   - reviewer.md: 6 навыков потеряны, включая критичный `syntax-checking`, который сам reviewer предписывает себе как обязательный pre-step для scope=code
   - explorer.md, tester.md: по 1 навыку потеряно (`xml-generation` в обоих)
   - developer-tests.md, analyst.md, scenario-author.md, scenario-coder.md: расхождений нет (skills полностью покрыты depends_on)
4. **Модельный tier покрывает не всех агентов**: README даёт конкретные модельные строки (например `claude-4.6-opus-high-thinking`) для 8 агентов, но не для scenario-coder и debugger. orchestrator.md §2 использует абстрактные категории (Economy/Mid/High/Premium), но таблица там ещё уже — покрывает явно только 4 из 12 ролей (Explorer / Developer,Tester / Architect,Analyst / Reviewer), при этом debugger получает модель напрямую в тексте протокола (`model: claude-4.6-opus-high-thinking`, §3a), а не через общий механизм. Три параллельных, не полностью синхронизированных источника модельной маршрутизации.
5. **Разные пороги self-fix/лимитов без единого источника**: developer-code (2 self-fix попытки в своём коде), scenario-coder (2 попытки на Red-гейт), tester (3 попытки на тех. ошибку теста), debugger (5 гипотез, +3 расширение, max 8), reviewer/orchestrator BLOCK-итерации (max 3), debug-fix review (max 1 итерация), bug→fix→bug цикл (max 2). Семь разных числовых лимитов в семи разных смысловых контекстах — нет общего "лимиты самовосстановления" правила/таблицы, из которой они выводятся.
6. **`form-info`** упомянут в теле scenario-author.md (шаг 6, "form-info, web-test-1c для UI-сценариев"), но не значится ни в `skills:` фронтматтере, ни в `depends_on` этого файла — похоже на навык, который существовал раньше или был переименован (сам файл не найден при выборочной проверке путей).
7. **orchestrator.md** ссылается в тексте (не в depends_on) на `references/orchestrator-structures.md` без указания родительского пути; фактический путь — `framework/workflows/orchestrator/references/orchestrator-structures.md` (существует, но ссылка в тексте неполная/относительная без контекста). Аналогично `escalation-format.md` упоминается по короткому имени файла (не как `.../SKILL.md`), фактически соответствует `framework/rules/escalation-format/SKILL.md` — не занесён в формальный `depends_on`.
8. **Reviewer не имеет явных BLOCK/WARN/INFO чек-листов для scope=spec и scope=arch** — это два самых ранних (и дорогих для отката) типа артефактов, для которых существуют только общие "Принципы" и упомянутые (но не инлайненные) навыки `spec-standard`/`technical-design-standard`.

### Проверка путей — результат (выборка ~55 упомянутых depends_on/текстовых ссылок)
Все пути `framework/rules/*/SKILL.md`, `framework/skills/**/SKILL.md`, `framework/workflows/full-cycle/SKILL.md`, `framework/skills/tool-usage/review/cross-provider-review/{SKILL.md,references/finalization-prompt.md}` — **существуют**. Единственные "битые" по буквальному тексту (не по depends_on-путям) — п.7 выше (два случая неполной/сокращённой ссылки в теле orchestrator.md, файлы по факту существуют по другому полному пути).

### Дубли-кандидаты на вынос в общий протокол

1. **Блок "КРИТИЧНО: Обязательное чтение навыков и правил"** (~20 строк) — дословно идентичен в 10 из 11 сабагентных профилей (все, кроме orchestrator и README). Уже сейчас держится синхронно только вручную — явный кандидат на вынос в `_template-agent.md`-инклюд или общий rule-файл с единой точкой правки.
2. **Таблица "Когда спрашивать"** (clarification_needed / допущение / открытый вопрос) — повторяется в analyst.md и architect.md почти дословно.
3. **"Coverage by runtime layer" / "Check coverage matrix"** — почти идентичный текст (server/UI/process/integration → тип теста) в analyst.md (шаг 7) и tester.md (шаг 3).
4. **BSL-специфичные BLOCK-критерии безопасности** (Background jobs, External calls, Broad rights, привилегированный режим) — пересекаются по терминологии между reviewer.md (чек-лист) и developer-code.md (Границы), нет единого правила-источника.
5. **Red/Green TDD концепция** — переформулируется трижды (developer-tests, scenario-coder, developer-code) вместо ссылки на единое определение (`tdd-policy` есть в depends_on только у scenario-coder и reviewer, не у developer-tests/developer-code, хотя оба явно про TDD).

### Объём контекста, загружаемого в каждого сабагента (приблизительно, строк)

Базовые 5 правил (agent-context-protocol 20 + capability-resolution 38 + no-direct-db-access 50 + skill-learning-policy 39 + source-of-truth 15 = **162 строки**) читаются ПОЛНОСТЬЮ durable у всех 10 сабагентов при старте (кроме orchestrator, у которого свой набор). Профиль + durable-правила:

| Агент | Профиль | + durable rules | Итого (без ленивых skill-тел) |
|---|---|---|---|
| explorer | 74 | 162 | ~236 |
| developer-tests | 96 | 162 | ~258 |
| architect | 106 | 162 | ~268 |
| analyst | 108 | 162 | ~270 |
| developer-code | 124 | 162+30 (protected-paths) | ~316 |
| scenario-author | 87 | 162+106 (3×vanessa-rules) | ~355 |
| debugger | 183 | 162+30+17 (protected-paths, dap-bsl-debugger) | ~392 |
| scenario-coder | 158 | 162+106+41+31 (vanessa×3, tdd-policy, run-loop) | ~498 |
| tester | 153 | 162+106+31+26+26 (vanessa×5) | ~504 |
| reviewer | 276 | 162+41+42+32 (tdd-policy, vanessa×2) | ~553 |

Поверх этого — ленивое чтение frontmatter (`name`+`description`) каждого навыка из `skills:` (дёшево) и полное тело SKILL.md только в момент применения (по декларации протокола, не всегда соблюдается на практике — не проверялось в рамках этого разведочного файла). Reviewer и scenario-coder/tester — самые "тяжёлые" по durable-объёму профили, при этом reviewer вызывается чаще всех остальных (после каждой фазы + возможный debug-scope).
