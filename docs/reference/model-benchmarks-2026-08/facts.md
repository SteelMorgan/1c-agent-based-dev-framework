# Фактура для консилиума по выбору LLM для агентов

**Дата сбора:** август 2026 (снимок на 3 августа 2026).
**Метод:** сбор веб-источников роем из 7 субагентов; сведение без переоценки, все числа — из исходных отчётов с указанием источника.
**Происхождение цифр:** смешанное — вендорские заявки (помечены), независимые измерители (Artificial Analysis, vals.ai, Stanford HAI, llm-stats, SEAL, академические), вторичные агрегаторы (BenchLM, morphllm, pricepertoken — качество методологии варьируется).

**Дисклеймер о достоверности:**
- Большинство цифр по новым моделям (Claude 5-го поколения, GPT-5.6, Kimi K3, DeepSeek V4) — **вендорские scaffold'ы** или пересказы агрегаторов «со слов вендора»; прямые сравнения между вендорами допустимы только по независимым harness'ам (SEAL, AA, vals.ai).
- Исторический разрыв «вендорский scaffold vs стандартизованный SEAL» — 15–30 п.п.; это надо учитывать при любом сопоставлении SWE-bench.
- Конфликты источников сведены в разделе «Пробелы и недостоверность»; в таблицах конфликтующие значения даны оба с пометкой.
- Официальные первоисточники OpenAI (system card GPT-5.6, developers.openai.com) напрямую не прочитаны — цены/бенчмарки пересказаны 2+ независимыми площадками и сверены между собой.

---

## 1. Сводная таблица по всем моделям

Цены в $/M токенов. Бенчмарки в %. «—» — данных нет. Пометки: (V) — вендорское, (N) — независимое, (A) — агрегатор без прозрачной методологии.

| Модель | $/M in | $/M out | Cache read | SWE-bench Verified | SWE-bench Pro | Terminal-Bench | AA Intelligence | Скорость tok/s | Контекст | Ключевые источники |
|---|---|---|---|---|---|---|---|---|---|---|
| **Claude Fable 5** | 10 | 50 | $1.00 | 95.0 (V) | 80.3 (V, scaffold оспаривается) | — | 60 (N, AA) | 75.1 (N, AA) | 1M / 128k out | morphllm.com, artificialanalysis.ai, siliconreport.com |
| **Claude Opus 5** | 5 | 25 | $0.50; Fast-режим ~2.5× за 2× цены | 96.0 (V, launch); у vals.ai-таблицы фигурирует как 97.0 (A) | 79.2 (V) | — | 61 (N, AA) / Agentic 55.3 | нет данных | 1M / 128k out | datanorth.ai, benchlm.ai, requesty.ai |
| **Claude Sonnet 5** | 2 до 31.08.2026, затем 3 | 10, затем 15 | $0.20 | 85.2 (A) | 63.2 (A) | 80.4 (TB 2.0, A) | 53.4 (N, AA); Coding Index 71.5 | ~60 out, TTFT 1.19с (N) | 1M | pricepertoken.com, benchlm.ai |
| **Claude Haiku 4.5** | 1 (есть версия 0.80 — конфликт) | 5 (версия 4) | $0.10 | 73.3 (V) / 51.4 (N, mini-SWE-agent) | 39.5 ±3.55 (N, SEAL) | — | — | 89–101 по провайдерам, TTFT ~0.69с (N, AA) | 200K / 64k out | morphllm.com, artificialanalysis.ai, contracollective.com |
| **GPT-5.5** | 5 (cached 0.50; long-context 10) | 30 (long-context 45) | $0.50 | 88.7 (V, агрегир.) | 58.6–59.4 (V) | 82.0–82.7 (v2.0) / 85.6 (v2.1) (V) | — | — | 1M (400K в Codex) | marc0.dev, theplanettools.ai, developers.openai.com |
| **GPT-5.6 Sol** | 5 | 30 | $0.50; cache-write ×1.25 | 96.2 (A, vals.ai/BenchLM) / 78.5 (A, hokai.io) — **сильный конфликт** | 64.6 (V) | 88.8 (TB 2.1; 91.9 ultra) (V) | 59 (N, AA); Coding Agent Index 80 — лидер | до 750 на Cerebras (V, select); TTFT 1.03с Bedrock | 1.05M / 128k out | artificialanalysis.ai, techjacksolutions.com, arkaiv.id |
| **GPT-5.6 Terra** | 2 после снижения 30.07 (было 2.50) | 12 (было 15) | $0.25 | — | 63.4 (V) | 87.4 (TB 2.1) (V) | 55 (N, AA); Coding Agent 77 | 289 avg (A, llm-stats); TTFT 0.57с Bedrock | 1.05M / 128k out | techjacksolutions.com, artificialanalysis.ai, llm-stats.com |
| **GPT-5.6 Luna** | 0.20 после снижения 30.07 (−80%; было 1) | 1.20 (было 6) | $0.10 | — | 62.7 (V) | 84.7 (TB 2.1) (V) | 51 (N, AA); Coding Agent 75 | 460 avg (A) / 155.6 (N, iamspeed) — конфликт; TTFT 0.44с Bedrock | 1.05M / 128k out | cloudybot.ai, llm-stats.com, qainsights.com |
| **Kimi K3** | 3.00 (агрегир. 2.90) | 15 (агрегир. 14) | $0.30 (cache-hit) | 93.40 ±1.11 (N, vals.ai, mini-SWE-agent) — 3-е место | — | 88.3 (TB 2.1, V, harness Kimi Code) | 57.1 (N, AA, 4-е из 187) | ~35 (N, AA, max effort) | 1M | kimi.com/blog, vals.ai, artificialanalysis.ai |
| **Kimi K2.7 Code** | 0.95 (HighSpeed 1.90) | 4.00 (HighSpeed 8.00) | $0.19 | нет независимых данных | нет | нет | — | ~180, до 260 HighSpeed (V) | 256K / ~33k out | platform.kimi.ai, felloai.com |
| **DeepSeek V4 Pro** (редакция = Preview 24.04.2026) | 0.435 с 22.05 (лист 1.74) | 0.87 (лист 3.48) | $0.0036 | 80.6 (V, Pro-Max) / 73.6 (A, BenchLM) — конфликт | — | 67.9 (TB 2.0, V) / 59.1 (A) | 52 max (N, AA); 43–44 в high/дефолт | 71 (N, AA); TTFT 1.57с | 1M / 384k out | benchlm.ai, artificialanalysis.ai, codersera.com |
| **DeepSeek V4 Flash 0731** (свежая редакция, GA 31.07.2026) | 0.14 | 0.28 | $0.0028–0.003 (скидка 98%) | нет для 0731 (Preview: 79.0 (A) / 73.7 (V)) | — | 82.7 (TB 2.1, V; было 61.8 у Preview) | 47 (codersera) / 50 (163.com) — конфликт; AA: #2 из 162 | ~103 (A); TTFT нет | 1M / 384k out | developersdigest.tech, api-docs.deepseek.com, benchlm.ai |

**Дополнительные точки качества (вне таблицы):**
- Fable 5: FrontierCode Diamond 29.3% (V), AutomationBench 17.4% (V); blended-цена (7:2:1) $7.70 (AA).
- Opus 5: OSWorld 2.0 70.57%, Toolathlon-Verified 80.6%, ARC-AGI-3 30.2% (все V); effort-toggle low/med/high, thinking по умолчанию.
- GPT-5.6 Sol: DeepSWE v1.1 72.7% (V), Agents' Last Exam 53.6 (+13.1 к Fable 5) (V); ~$1.04 за задачу AA.
- GPT-5.6 Terra: DeepSWE 69.6%; ~$0.55/задачу AA. Luna: DeepSWE 67.2%; ~$0.21/задачу AA.
- Kimi K3: SWE-Marathon 42.0 — лидер лидерборда (N, llm-stats); FrontierSWE 81.2 / DeepSWE 67.5 (V); LMArena WebDev Elo 1679 — №1 (N); GDPval-AA Elo 1684 (4-е); AA-Briefcase Elo 1543–1545 (2-е).
- Kimi K2.7: все бенчмарки вендорские — Kimi Code Bench v2 62.0, Program Bench 53.6, MLS Bench Lite 35.1, MCP Mark Verified 81.1 (выше Opus 4.8 = 76.4); заявка −30% reasoning-токенов vs K2.6 (пользователи репортуют обратное).
- DeepSeek V4 Flash 0731: Toolathlon-verified 70.3, Cybergym 76.7, DeepSWE 54.4, NL2Repo 54.2 (все V); LiveCodeBench Preview 55.2% (V). V4 Pro: LiveCodeBench 93.5, Codeforces 3206 (V, Pro-Max).
- Вербозность: AA измерил у DeepSeek V4 Flash 210M токенов на eval-suite при медиане 62M (3×) — реальная стоимость выше стикерной.
- Haiku 4.5: tool-call accuracy 96.8% aggregate (N).
- Кэш-модель GPT-5.6 (вся линейка): явные cache breakpoints, минимум 30 мин, write ×1.25, read −90% — окупается с первого повторного чтения shared prefix.

**Статусы релизов (важно для интерпретации):**
- Fable 5: GA 9.06.2026; приостановка 12.06–30.06 (экспортный контроль США после jailbreak-отчёта Amazon), редеплой 1.07 с новым классификатором — операционный риск.
- Opus 5: 24.07.2026, новый флагман Anthropic. Sonnet 5: 30.06.2026. Haiku 4.5: октябрь 2025.
- GPT-5.6: GA 9.07.2026 (preview с 25.06), cutoff февраль 2026.
- Kimi K3: релиз 16–17.07.2026, веса на HF 27.07.2026 (кастомная Kimi K3 License; AA классифицирует как «proprietary»). K2.7 Code: 12.06.2026.
- DeepSeek: V4 Preview 24.04.2026; Flash-0731 (re-post-training без смены архитектуры) 31.07.2026; **официальный GA V4-Pro ещё не вышел** («will follow soon»), веса 0731 не опубликованы.

---

## 2. Доменные бенчмарки (не кодинг)

Общая оговорка: покрытие новых моделей не-кодинговыми бенчмарками редкое; большая часть цифр — независимые агрегаторы и исследовательские обзоры, часть с пометкой «estimated».

### Финансы

| Бенчмарк / модель | Значение | Тип источника |
|---|---|---|
| FinanceBench — Claude Opus 4.7 | 82.7% | вторичный агрегатор (восходит к Anthropic) — studioglobal.ai |
| FinanceBench (апрель 2026) — o3 / GPT-5 / Claude 4 Opus / Gemini 2.5 Pro | ~90 / ~88 / ~82 / ~80% | независимая агрегация (частично Patronus, частично estimated) — awesomeagents.ai |
| FinQA EM — GPT-4.1 / DeepSeek-R2 / DeepSeek V3.2 | ~68 / ~65 / ~62 | агрегация — awesomeagents.ai |
| TAT-QA F1 — GPT-4.1 / DeepSeek-R2 / Gemini 2.5 Pro | ~75 / ~72 / ~70 | агрегация — awesomeagents.ai |
| FinanceComplexQA — DeepSeek V4 Flash | overall ≈ 48.8 (компоненты 59.4/45.9/59.2/36.5/60.0/33.8/64.7) | академическое (N) — arXiv:2607.19238, июль 2026 |

Прямых замеров FinanceBench/FinQA/ConvFinQA для Opus 5 / Sonnet 5 / Fable 5 / GPT-5.6 / Kimi K3 / DeepSeek V4 Pro **не найдено** — только предшественники.

### Юридические

| Бенчмарк / модель | Значение | Тип источника |
|---|---|---|
| LegalBench (Vals × Stanford CodeX) — Fable 5 | 88.56% — лидер из 129 моделей | независимый снапшот — benchlm.ai |
| LegalBench — Gemini 3.1 Pro Preview / GPT-5.6 Sol | 87.4 / 87.0%; топ-8 в пределах ~2.5 п.п. | независимое — benchlm.ai |
| LegalBench — насыщение | топ-15 выше 83%, спред ~4 п.п. | Stanford AI Index 2026 (офиц.) |
| HAQQ Legal AI (300 задач) — Opus 4.8 | выиграл 130/300 задач | независимое — haqq.ai |
| HAQQ Legal AI — GPT-5.5 | самый точный 8.41/10, 3% галлюцинированных цитат | независимое — haqq.ai |

### Универсальные агентные

| Бенчмарк / модель | Значение | Тип источника |
|---|---|---|
| BrowseComp — GPT-5.6 Sol / Kimi K3 / Claude Opus 5 | 92.2 / 91.2 / 90.8% (Sol лидер) | независимый агрегатор — benchlm.ai, 3.08.2026 |
| HLE — Fable 5 / Opus 5 / GPT-5.6 Sol | 53.3 / 52.6 / 47.2% | независимое (AA) — pricepertoken.com |
| HLE — Opus 5 (другой вариант/поднабор) | 0.647, лидер | независимое — llm-stats.com (конфликт с AA-снимком) |
| GAIA | лидер снапшота Claude Mythos 5 52.3% (display-only); HAL Princeton ~74.6% (Sonnet 4.5, scaffold) | независимые — benchlm.ai, hal.cs.princeton.edu |
| τ2-bench Airline/Retail/Telecom — Nova 2 Pro | 65.2 / 77.7 / 92.7 (для Opus 5 значений нет) | независимое — llm-stats.com |
| τ2-bench Telecom — GPT-5.5 | 98.0% | восходит к OpenAI (V) |
| τ-bench-подобные задачи — Kimi K3 | composite pass^5 33.4%; pass^k-рейтинг 50, 4-е в мире, топ open-weight | независимое — toloka.ai |
| AA-Briefcase — Kimi K3 | Elo 1543/1545, 2-е место, позади только Fable 5, впереди GPT-5.6 | независимое (AA) |
| GDPval — GPT-5.5 | 84.9% (лидер на апрель); GPT-5.6 Sol назван лидером professional knowledge work | вендор OpenAI + вторичные |
| AA Intelligence Index (июль 2026) | Opus 5 — 61, Fable 5 — 60, GPT-5.6 Sol — 59, Kimi K3 — 57 | независимое (AA) — fenxi.fr |

### Выводы по доменам

1. **Единого лидера нет — задачи раскололись по доменам.** BrowseComp (web research) ведёт GPT-5.6 Sol (92.2%), HLE (глубокое рассуждение) — Fable 5 / Opus 5 (53.3/52.6%), агентная knowledge-work (AA-Briefcase) — Fable 5, за ним Kimi K3.
2. **LegalBench насыщен** — топ-8 в пределах ~2.5 п.п.; как дифференциатор бесполезен. Реальную разницу показывают прикладные прогоны (HAQQ: точность и % галлюцинированных цитат — там лучше GPT-5.5).
3. **Финансы для новых флагманов почти не измерены.** Лучшая документированная линейка — у GPT-семейства. Единственный свежий академический замер — DeepSeek V4 Flash на FinanceComplexQA (~48.8 overall): deep-research по финдокументам тяжёлый для всех.
4. **τ2-bench retail/airline почти не покрыт** для моделей из списка (точки: GPT-5.5 Telecom 98.0% (V), Kimi K3 pass^5 33.4% (N)).
5. **Kimi K3 — самый сильный open-weight на универсальных агентных задачах** (2-е на BrowseComp, 2-е на AA-Briefcase, 4-е на AA Index). DeepSeek V4 Pro/Flash на не-кодинговых бенчмарках почти не измерены.

---

## 3. Каталог агентов репозитория

Источники: `.codex/agents/*.toml` (19 файлов), `agents-fleet/fleet-index.json` (4 домена, 39 профилей, 92 вида задач; tier'ы: light 3 / primary 13 / reasoning 58 / writing-critical 18), `agents-fleet/conventions.md` §6–6.1, `agents-fleet/*/profiles/README.md`.

### 3.1. Ядро фреймворка (`.codex/agents`, 19 профилей)

| Агент | Назначение | Тип задач | Требование к модели | Контекст | Скорость/цена | Блокирующая роль |
|---|---|---|---|---|---|---|
| orchestrator | Классификация, маршрутизация, гейты, арбитраж, финальный синтез | оркестрация | reasoning (сильная) | да (workflow-context целиком) | скорость вторична; 1 вызов на задачу | да (гейты, completion claim) |
| explorer | Read-only исследование кода/документов, file:line-доказательства | исследование | быстрая дешёвая достаточна (retrieval) | да (много файлов) | очень чувствителен (массовый вызов) | нет |
| spec-author | Владелец spec.md: discovery, goal-gap, критерии приёмки | архитектура/спека | reasoning (сильная) | да | низкая (1 артефакт) | да (spec — вход для всех) |
| platform-architect | Эскалационный архитектор (5 под-доменов) | архитектура | reasoning (сильная) | да | нечувствителен (редкие эскалации) | advice-only, фактически gate |
| public-web-architect | Структурный ревьюер PW (маршруты/canonical/redirect) | ревью-gate | reasoning | средний | низкая | да (Phase 1 ревью) |
| workspace-runtime-architect | WRKSP runtime-контракт, agent-access контракт (PLAN mode) | архитектура | reasoning (сильная) | да | низкая | да (контракт — gate) |
| cms-content-architect | Strapi-схема и контент-модель, миграции | архитектура | reasoning | средний | низкая | частично |
| product-designer | design.md, приёмочный React/HTML-мок, handoff | дизайн/контент | сильная (visual+code), идеально мультимодальная | да | низкая | да (design.md — gate Phase 2) |
| pw-author | Черновик + polish текстов PW по voice/AEO | контент | writing-critical (сильная пишущая) | средний (brandbook, claims) | низкая | да (приёмочный артефакт) |
| frontend-builder | Продакшн UI-код PW/WRKSP по tech-design + design brief | кодинг | сильная кодинг-модель | да (дизайн-контракты) | средняя | нет (выход верифицируется) |
| code-test-author | Unit/integration тесты до имплементации (TDD) | кодинг (тесты) | кодинг-модель среднего класса достаточно | средний | средняя/высокая (массовая генерация) | нет |
| runtime-test-author | Playwright e2e + visual-тесты, браузер, runtime bootstrap | кодинг (тесты) | кодинг-модель + tool-use | средний | средняя | нет |
| qa-verifier | Владелец Phase 3 верификации + whole-page judge pre-pass | ревью-gate | reasoning + мультимодальность (PNG) | да | низкая | да (post-build приёмка) |
| reviewer | Независимый ревьюер приёмочных артефактов по SoT-цепочке | ревью-gate | reasoning (сильная, adversarial) | да (SoT + правила домена) | низкая | да |
| cross-provider-reviewer | Блокирующее кросс-семейное второе мнение | ревью-gate | сильная reasoning **ИНОГО вендора** (суть роли) | да | нечувствителен | **да, жёстко блокирующая** |
| ui-visual-reviewer | Структурно-визуальный ревьюер Phase 2b (DOM-контракт + PNG) | ревью-gate | reasoning + мультимодальность | средний | низкая | да (Phase 2b gate) |
| seo-growth-reviewer | SEO/AEO/structured data/атрибуции | ревью-gate | reasoning среднего класса | средний | низкая | да (доменный gate) |
| trust-compliance-reviewer | Trust-документы, disclosures, compliance-поверхности | ревью-gate | reasoning (высокая точность, низкая толерантность к галлюцинациям) | средний | низкая | да (доменный gate) |
| ai-governance-reviewer | AI-поведение: HITL, permissions, guardrails, prompt-injection | ревью-gate | reasoning (сильная) | средний | низкая | да (доменный gate) |

### 3.2. Флот доменов (`agents-fleet`, 39 профилей, 92 вида задач)

Все ревьюверы флота — `aiMode: advice-only`, но входят в обязательный трёхэшелонный review-loop (script-validator → agent-reviewer → human-gate).

**orchestration (2):**

| Агент | Назначение | Tier | Контекст | Блок. |
|---|---|---|---|---|
| orch-fleet-orchestrator | Декомпозиция интента, gap-репорты (authoring-time) | reasoning | да (fleet-index целиком) | да (authoring-gate) |
| orch-definition-reviewer | Ревью черновиков process definition | reasoning | да | advice-only в review-loop |

**crypto (7):**

| Агент | Назначение | Tier |
|---|---|---|
| crypto-token-researcher | Исследование токенов/секторов, research-note | reasoning + writing-critical (note) |
| crypto-protocol-revenue-analyst | Экономика протоколов, устойчивость доходов | reasoning |
| crypto-security-screener | Скрининг контрактов/ликвидности, rug-pull пакет | reasoning |
| crypto-yield-strategist | DeFi-доходность: скрининг и стратегии | reasoning |
| crypto-numbers-reviewer | Ревью чисел/on-chain метрик | reasoning (advice-only) |
| crypto-memo-reviewer | Ревью нарратива и дисклеймеров | writing-critical (advice-only) |
| crypto-security-reviewer | Adversarial-ревью security-скрининга | reasoning (advice-only) |

**finance (11):**

| Агент | Назначение | Tier |
|---|---|---|
| fin-close-accountant | Бухгалтер закрытия периода (механические отчёты — light) | reasoning + light |
| fin-gl-reconciler | Сверка ГК, root-cause расхождений | reasoning |
| fin-kyc-screener | KYC: парсинг документов (primary), rules-скрининг | primary/reasoning |
| fin-earnings-analyst | Анализ отчётности за период | reasoning |
| fin-model-builder | Финмодели, comps | reasoning |
| fin-pitch-builder | Прецедентные сделки, pitch-deck | reasoning + writing-critical |
| fin-portfolio-valuation-reviewer | Оценка портфеля фонда, waterfall (исполнитель, несмотря на имя) | reasoning |
| fin-sector-researcher | Секторные исследования, idea-shortlist | reasoning |
| fin-numbers-reviewer | Ревью чисел и моделей | reasoning (advice-only) |
| fin-memo-reviewer | Ревью аналитических текстов | writing-critical (advice-only) |
| fin-compliance-reviewer | Ревью комплаенса и KYC | reasoning (advice-only) |

**marketing (19):**

| Агент | Назначение | Tier |
|---|---|---|
| mkt-post-writer / mkt-article-writer / mkt-email-author / mkt-voice-profiler | Посты, статьи, email-выпуски, voice-профили | writing-critical |
| mkt-content-planner / mkt-scenario-author / mkt-program-author | Контент-план, сценарные цепочки, квартальные программы | reasoning |
| mkt-cjm-author / mkt-audience-researcher / mkt-deep-researcher | CJM, исследование ЦА, deep-research | reasoning |
| mkt-analytics-reporter | Отчёты петли аналитики | reasoning |
| mkt-seo-distributor | SEO/AEO-пакеты дистрибуции | reasoning |
| mkt-watch-analyst | Репутация/конкуренты (competitor-scan — primary) | primary/reasoning |
| mkt-source-monitor | Монитор источников, news-digest | primary |
| mkt-media-designer | Оформление постов/медиа | primary |
| mkt-community-agent | Ответы в комьюнити (delegated-execution) | primary |
| mkt-content-reviewer | Ревью текстов и оформления | writing-critical (advice-only) |
| mkt-research-reviewer | Ревью исследований/CJM/программ | reasoning (advice-only) |
| mkt-community-reviewer | Выборочный аудит комьюнити-ответов | reasoning (advice-only) |

### 3.3. Шесть кластеров требований к модели

1. **Оркестрация и архитектура** (сильная reasoning, большой контекст, цена не важна): orchestrator, platform-architect, workspace-runtime-architect, public-web-architect, cms-content-architect, spec-author, orch-fleet-orchestrator. Редкие вызовы, высокая цена ошибки.
2. **Блокирующие ревью-гейты** (сильная reasoning, adversarial): reviewer, cross-provider-reviewer (жёстко — другая семья моделей), qa-verifier, ui-visual-reviewer (оба — мультимодальность PNG), seo-growth-reviewer, trust-compliance-reviewer, ai-governance-reviewer, orch-definition-reviewer. Скорость вторична; точность и доказательность критичны.
3. **Кодинг и тесты** (сильная кодинг-модель, tool-use, средняя чувствительность к цене — объём большой): frontend-builder, code-test-author, runtime-test-author. Контекст средний-большой.
4. **Writing-critical флот** (сильная пишущая модель pro/Opus-класс, 18 видов задач): pw-author, mkt-writers, mkt-voice-profiler, ревьюверы текста (mkt-content-reviewer, crypto-memo-reviewer, fin-memo-reviewer), fin-pitch-builder (частично), crypto-token-researcher (research-note).
5. **Reasoning-доменный анализ флота** (pro/Sonnet-класс, 58 видов задач — самый массовый tier): все crypto/finance аналитики и ревьюверы, mkt-planner/scenario/program/cjm/researchers, orch-профили. Большой контекст (master-data, справочники), цена средней важности.
6. **Дешёвые механические/дайджест-задачи** (primary flash-класс и light nano/haiku, 16 видов задач): mkt-source-monitor, mkt-media-designer, mkt-community-agent, mkt-watch-analyst (competitor-scan), fin-kyc-screener (kyc-doc-parse), fin-close-accountant (light-отчёты), explorer (ядро). Максимальная чувствительность к цене/скорости, рассуждение не нужно.

### 3.4. Существующая tier-политика (conventions.md §6.1)

- 5 tier'ов: **light** = nano/haiku-класс; **primary** = flash-класс (DeepSeek v4 flash+); **reasoning** = pro/Sonnet-класс; **writing-critical** = pro/Opus-класс; **coding** = flash+ (зарезервирован).
- Конкретные модели каталог принципиально не называет (DEC-017 — enforcement в model-policy движка, engine-spec §4.11).
- Распределение 92 видов задач: 58 reasoning / 18 writing-critical / 13 primary / 3 light / 0 coding — центр масс в reasoning-tier; дешёвые tier'ы маргинальны (16%).
- Две роли несут явные мультимодальные требования (PNG-верификация): qa-verifier и ui-visual-reviewer.
- cross-provider-reviewer — жёсткое архитектурное требование минимум к двум провайдерам LLM (блокирующее ревью моделью другой семьи, чем автор и оркестратор).
- Документ о выборе LLM должен отталкиваться от этой политики, а не вводить параллельную классификацию.

---

## 4. Пробелы и недостоверность

### Не найдено (сведение по всем секциям)

- **τ2-bench** для Fable 5 / Opus 5 / Sonnet 5 / Haiku 4.5 (только устаревший Opus 4.5 = 86%); τ2-bench retail/airline для Opus 5 / Sonnet 5 / Fable 5 / всех GPT-5.6 / Kimi K3 / DeepSeek V4.
- **Скорость tok/s (независимая)**: Fable 5 и Opus 5 («not measured»); Sonnet 5 / Haiku 4.5 в сводке AA не извлеклись; GPT-5.5; Terra/Luna TTFT независимый; Kimi K2.7; Kimi K3 TTFT; DeepSeek V4 Flash TTFT; DeepSeek обе модели API (только self-host NVIDIA >150 tok/s/user на GB200 NVL72).
- **SWE-bench Verified**: для GPT-5.6 Terra и Luna; для Kimi K2.7 Code (независимый — подтверждено отсутствие); для DeepSeek V4 Flash-0731 отдельно (есть только Preview).
- **SWE-rebench** — ни по одной модели ни одного семейства.
- **Aider polyglot / LiveCodeBench / LMArena** свежие цифры по моделям 5-го поколения Anthropic.
- **FinanceBench / FinQA / ConvFinQA** прямые замеры: Opus 5, Sonnet 5, Fable 5, GPT-5.5/5.6 (все), Kimi K2.7/K3, DeepSeek V4 Pro.
- **GAIA** прямые замеры: Opus 5 / GPT-5.6 / Kimi K3 (лидер-снапшоты: Mythos 5, Sonnet 4.5).
- **Отдельные не-кодинговые замеры**: Sonnet 5, GPT-5.6 Terra, GPT-5.6 Luna, Kimi K2.7 — либо только кодинг, либо ничего.
- **Deep research бенчмарки** (DeepResearch Bench, ResearchBench) — не попались, кроме FinanceComplexQA.
- **AA Intelligence Index и скорость** для Claude Opus 5, Sonnet 5, Haiku 4.5 (сводный агент не извлёк; при этом семейственный агент привёл AA-числа Opus 5: 61/55.3 и Sonnet 5: 53.4/Coding 71.5 — требуется перепроверка первоисточника).
- **Индекс качества** для GPT-5.5 и Kimi K2.7 Code.
- **Точная max output длина Kimi K3** (Requesty: 1.0M — похоже на ошибку агрегатора; CloudPrice: 131K; официального числа нет).
- **Официальная GA-версия DeepSeek V4-Pro** с пост-тренировкой — анонсирована «скоро», бенчмарков нет; веса Flash-0731 не опубликованы.
- **Официальные первоисточники OpenAI** (system card GPT-5.6, developers.openai.com) напрямую не прочитаны; вендорские не-кодинговые таблицы Anthropic/OpenAI/Moonshot/DeepSeek напрямую не получены.
- **Цена GPT-5.5 Pro** ($30/$180 у CloudZero) — не верифицирована.
- **Репозиторий**: конкретные привязки tier→модель в model-policy движка (engine §4.11) не извлекались; квант-требования к контексту нигде не зафиксированы («большой/средний» — вывод из характера входов); большинство профилей флота в статусе `draft` — tier-назначения могут меняться.

### Конфликты источников

| Метрика | Значения | Комментарий |
|---|---|---|
| Fable 5 SWE-bench Pro | 80.3% (секция 0) vs 80.0% (в строке Opus 5) | оба вендорские scaffold; сопоставимость оспаривается независимыми |
| Opus 5 SWE-bench Verified | 96.0% (datanorth) vs 97.0% (vals.ai-таблица у агента GPT) | разные harness'ы |
| GPT-5.6 Sol SWE-bench Verified | 96.2% (vals.ai/BenchLM) vs 78.5% (hokai.io) | огромный разброс, вероятно разные scaffold'ы; зона неопределённости |
| GPT-5.5 Terminal-Bench | 82.0 vs 82.7 vs 85.6 | разные версии бенчмарка (2.0 vs 2.1) |
| Haiku 4.5 цена | $1/$5 (morphllm, AA) vs $0.80/$4 (contracollective) | вероятно разные даты снимка; каноничен официальный $1/$5 |
| Haiku 4.5 SWE-bench Verified | 73.3% (вендор) vs 51.4% (независимый mini-SWE-agent) | типичный разрыв vendor vs стандартизованный harness |
| DeepSeek V4 Pro SWE-bench Verified | 80.6% (вендор, Pro-Max) vs 73.6% (BenchLM) | вероятна путаница конфигураций, contamination-риск |
| DeepSeek V4 Pro Terminal-Bench | 67.9% (вендор) vs 59.1% (BenchLM) | — |
| DeepSeek V4 Flash AA Intelligence | 47 (codersera) vs 50 (163.com) | вероятно разные режимы reasoning |
| GPT-5.6 Luna скорость | 460 avg (llm-stats) vs 155.6 (незав. замер iamspeed) | разные провайдеры/методики |
| HLE лидер | Fable 5 53.3% (AA-данные) vs Opus 5 0.647 (llm-stats) | вероятно разные поднаборы/инструменты |
| Kimi K2.7 эффективность | −30% reasoning-токенов (вендор) vs расход кредитов выше (пользователи) | — |
| Terra vs Парето-фронт | вендорская градация Sol/Terra/Luna vs AA: Terra вне Парето-фронта цена/интеллект (доминируют Luna и Sol) | аргумент против Terra как дефолта по модели стоимости задачи AA |
| OpenAI vs SWE-bench Pro | OpenAI публично оспаривал проигрыш Fable 5 (64.6 vs ~80) критикой бенчмарка; METR, по пересказу, отметила benchmark-gaming | конфликт интересов зафиксирован независимыми обозревателями |

### Только вендорские цифры (независимого подтверждения нет)

- Fable 5: SWE-bench Pro 80.3%, FrontierCode Diamond 29.3%, AutomationBench 17.4%.
- Opus 5: SWE-bench Verified 96.0%, SWE-bench Pro 79.2%, OSWorld 70.57%, Toolathlon 80.6%, ARC-AGI-3 30.2%, Fast-режим ~2.5×.
- Sonnet 5: SWE-bench Verified 85.2% / Pro 63.2% / Terminal-Bench 80.4% (агрегаторы, sourced, не прямые независимые).
- GPT-5.6 Sol: SWE-bench Pro 64.6%, Terminal-Bench 88.8/91.9%, DeepSWE 72.7%, Agents' Last Exam 53.6, скорость 750 tok/s (Cerebras, select customers — не стандартный API).
- GPT-5.6 Terra/Luna: все бенчмарки (SWE-bench Pro 63.4/62.7, Terminal-Bench 87.4/84.7, DeepSWE 69.6/67.2) — OpenAI-reported. Примечание: AA проводил pre-release evaluation при поддержке OpenAI.
- Kimi K2.7 Code: **все** бенчмарки (Kimi Code Bench v2 62.0, Program Bench 53.6, MLS Bench Lite 35.1, MCP Mark 81.1) и скорость ~180/260 tok/s.
- Kimi K3: Terminal-Bench 88.3, FrontierSWE 81.2, DeepSWE 67.5 (harness Kimi Code).
- DeepSeek V4 Flash 0731: Terminal-Bench 82.7, Toolathlon 70.3, Cybergym 76.7, DeepSWE 54.4, NL2Repo 54.2; цены rate card.
- DeepSeek V4 Pro: SWE-bench 80.6%, LiveCodeBench 93.5, Codeforces 3206 (Pro-Max-вариант).
- τ2-bench Telecom GPT-5.5 98.0%; GDPval GPT-5.5 84.9%.

### Прочие оговорки о достоверности

- Скорость сильно зависит от провайдера и режима reasoning (Sol 750 t/s — хостинг Cerebras).
- У Kimi и DeepSeek эффективная цена критично зависит от cache-hit rate (K3: $0.30/M при hit; DeepSeek: скидка 98% до $0.003/M).
- У GPT-5.6 есть тариф на cache-write (×1.25 входа).
- Реальная стоимость DeepSeek Flash выше стикерной из-за 3× вербозности (AA).
- AA отмечает высокую склонность DeepSeek V4 Pro/Flash к галлюцинациям (94–96% случаев не отказываются от ответа).
- Промо-цена Sonnet 5 ($2/$10) действует до 31.08.2026; снижения цен Terra/Luna — с 30.07.2026; экономику надо пересчитывать на дату решения.
