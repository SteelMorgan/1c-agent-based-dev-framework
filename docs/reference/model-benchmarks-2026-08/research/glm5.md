# GLM-5 (Zhipu / Z.ai) — фактура для бенчмарка LLM

Дата сбора: 2026-08-04. Пометки источников: (V) вендор, (N) независимый измеритель, (A) агрегатор без прозрачной методологии.

## 0. Семейство (что существует на август 2026)

| Модель | Релиз | Параметры | Контекст | Статус |
|---|---|---|---|---|
| GLM-5 | 11–13 февраля 2026 | ~744B MoE, ~44B active (A: felloai) | 200K (204 800) | GA, open weights |
| GLM-5-Code | февраль 2026 | — | — | кодинг-вариант, своя цена |
| GLM-5-Turbo | 2026 | — | 204 800 | ускоренный вариант GLM-5 |
| GLM-5.1 | 7 апреля 2026 | ~754B MoE (A) | 200K, output 128K | GA, MIT, open weights |
| GLM-5.2 | 13–16 июня 2026 | ~753B total / ~40B active | 1M (1 048 576), output ~128K | GA, MIT, open weights — текущий флагман |

- Вариантов «GLM-5-Air» / «GLM-5-Flash» в найденных источниках нет (Air/Flash остались в линейке 4.x: GLM-4.5-Air, GLM-4.7 Flash). (A)
- GLM-5.2: релиз 13 июня в GLM Coding Plan, standalone API — 16 июня 2026. (A: [OpsMatters](https://opsmatters.com/posts/glm-52-review-2026-zhipu-ais-open-weight-coding-model-honestly-assessed))
- Заявлено, что GLM-5.1/5.2 обучались на чипах Huawei без NVIDIA. (A: [Fungies.io](https://fungies.io/top-open-source-llms-local-inference-2026/))

## 1. Цены API ($/M токенов)

**GLM-5** (V, по docs.z.ai на 12.02.2026, пересказ [glm5.online](https://glm5.online/)):
- input $1.00, cached input $0.20, output $3.20
- GLM-5-Code: input $1.20, cached $0.30, output $5.00
- OpenRouter: $1.00 / $3.20 (A)
- (A: [llmcostcalc](https://llmcostcalc.com/cloud/zhipu), verified 06.06.2026): GLM-5 $1.0/$3.2; GLM-5-Turbo $1.2/$4.0; GLM-5.1 $1.4/$4.4
- (A: [pricepertoken](https://pricepertoken.com/pricing-page/model/z-ai-glm-5)) «от $0.60 input / $1.92 output» — сторонние провайдеры, конфликт с вендорской ценой; приводим обе.

**GLM-5.2**:
- Z.ai first-party, также Fireworks/Baseten: input $1.40, output $4.40, cached input $0.26 (N: Artificial Analysis, пересказ [AI/TLDR](https://ai-tldr.dev/models/glm-5-2/), [Layer3](https://www.layer3labs.io/guides/glm-5-2-pricing); V-совпадает)
- Morph: $1.10 / $4.10, cached $0.22, ~100 t/s (A: [morphllm.com](https://www.morphllm.com/glm-5.2-api))
- OpenRouter third-party: до $0.91 input (A: [Tech Times](https://www.techtimes.com/articles/320286/20260713/glm-52-captures-40-developer-tokens-open-weights-do-not-equal-sovereignty.htm))
- Подписка GLM Coding Plan: $18/мес (Lite) — $160/мес (Max), работает в Claude Code/Cline/Kilo (A: OpsMatters)
- Промо/временные скидки с датами — **не найдено** (только разброс цен между хостерами).

## 2. Кодинговые бенчмарки

**GLM-5:**
- SWE-bench Verified: 77.8% (V, пересказ [Fireworks](https://fireworks.ai/blog/best-open-source-llms-may-2026), [Surf](https://asksurf.ai/pulse/en/glm-5-2-open-weights-pricing-pressure)). Конфликт: 72.8% в независимых снапшотах (N/A: [BenchLM, 03.08.2026](https://benchlm.ai/benchmarks/sweverifiedarcee); [failingfast.io](https://failingfast.io/ai-coding-guide/benchmarks/)) — вероятно разные харнессы/даты; приводим оба.
- Terminal-Bench 2.0: лидер среди open models на релизе, точное число в пересказах не приведено (V/A: Surf)
- τ²-bench 89.7%, BrowseComp 75.9%, LiveCodeBench 83.0%, GPQA 78.0% (A: [llm-stats, 11.02.2026](https://llm-stats.com/models/compare/glm-5-vs-minimax-m2) — вероятно вендорские числа)
- SWE-bench Lite (SWE-agent): 53.33% (N: [LayerLens Stratix](https://layerlens.ai/blog/glm-5-benchmark-review), 11.03.2026)
- Aider polyglot: прямое число не найдено; косвенно GLM-5 — 14-е место с 42.1% в агрегированном топ-20 (A: [SpecWeave](https://spec-weave.com/docs/guides/ai-coding-benchmarks/)); на steel.dev у GLM-5 24.2% в каком-то агентном срезе (A). Методологии непрозрачны — помечать как шум.

**GLM-5.1:**
- SWE-bench Pro: 58.4 (V, [Morph](https://www.morphllm.com/glm-5-1), [Fungies.io](https://fungies.io/top-open-source-llms-local-inference-2026/)) — SOTA среди open на релиз
- Terminal-Bench: 63.5 (A: Fungies.io; версия трека не указана)

**GLM-5.2 (все основные числа — V, самоотчёт Z.ai, независимо не подтверждены):**
- SWE-bench Pro: 62.1 (V) — выше GPT-5.5 (58.6), ниже Claude Opus 4.8 (69.2) ([Layer3](https://www.layer3labs.io/guides/glm-5-2-benchmarks), [OpsMatters](https://opsmatters.com/posts/glm-52-review-2026-zhipu-ais-open-weight-coding-model-honestly-assessed))
- Terminal-Bench 2.1: 81.0 (V) — ниже GPT-5.5 (84.0) и Claude Opus 4.8 (85.0), выше Gemini 3.1 Pro (74.0)
- GPQA Diamond: 91.2 (V); AIME 2026: 99.2 (V)
- τ²-bench: 99.1% (A: [BenchLM](https://benchlm.ai/compare/glm-4-6-vs-glm-5-2))
- MCP Atlas: 76.8; Toolathlon: 48.2; GDPval-AA: 50.5; AA Agentic Index: 43.1; AA Briefcase Elo: 1254 (A: BenchLM; часть выглядит как AA-данные, но методология пересказа непрозрачна)
- Aider polyglot, LiveCodeBench, LiveBench для GLM-5.2 — **не найдено**.

## 3. Artificial Analysis (N)

- Intelligence Index v4.1: GLM-5.2 = 51, #1 среди open-weight моделей (медиана класса ~25). ([AI/TLDR](https://ai-tldr.dev/models/glm-5-2/), [OpsMatters](https://opsmatters.com/...))
- Скорость: ~141 tok/s output (AA, пересказ OpsMatters); Morph заявляет ~100 t/s на своём хостинге.
- Coding Index, TTFT, стоимость за задачу Intelligence Index, output tokens per task — страница AA отдаёт числа только через JS, **точные значения не извлечены** ([artificialanalysis.ai/models/glm-5-2](https://artificialanalysis.ai/models/glm-5-2)).
- Метрика галлюцинаций: AA использует **AA-Omniscience Index** (шкала −100…100; награда за верное, штраф за галлюцинацию, без штрафа за отказ). **Числовое значение Omniscience для GLM-5/5.2 не найдено** — ни % «не отказывается», ни индекс. Для сравнения с DeepSeek (94–96% non-refusal) прямого аналога у GLM-5 в открытом доступе не обнаружено.
- Косвенно: glm5.online (A) упоминает «record-low hallucination rates» без чисел — не использовать.

## 4. Контекст / max output

- GLM-5: 200K (204 800) input; max output — вендорская цифра не найдена (A-источники пишут «128K output», но путают с 5.1/5.2).
- GLM-5.1: 200K input / 128K output (A: [llm-stats](https://llm-stats.com/models/compare/glm-5.1-vs-hy3)).
- GLM-5.2: 1 048 576 (1M) input / ~128K output (V/A: OpsMatters, AI/TLDR).

## 5. Статус релиза

- GLM-5: GA, 11–13.02.2026; open weights. Лицензия: конфликт — fireworks.ai (A) пишет «Modified MIT», большинство других (MIT). Техрепорт: [arXiv:2602.15763](https://arxiv.org/abs/2602.15763) «GLM-5: from Vibe Coding to Agentic Engineering» (V).
- GLM-5.1: GA 07.04.2026, MIT (N/A: llm-stats).
- GLM-5.2: GA 13–16.06.2026, MIT, open weights на Hugging Face, без региональных ограничений (A: [digitalapplied](https://www.digitalapplied.com/blog/ai-vendor-resilience-open-weight-second-source-2026-playbook), OpsMatters).
- Только текст (text-only), мультимодальности нет (A: OpsMatters).

## 6. Не-кодинговые замеры GLM-5

Независимые (N: LayerLens Stratix, собственные прогоны, 3 батча за 24 дня, [источник](https://layerlens.ai/blog/glm-5-benchmark-review)):
- MMLU-Pro: 82.01% → 80.63% (перезамер)
- GPQA («General Purpose QA» в их терминологии): 80.30% → 84.34%
- HLE: 22.41% → **10.37%** на перезамере (регресс −12 п.п.; важный сигнал о нестабильности)
- AIME 2025: 86.67% → 93.33%
- MATH-500: 97.20–97.40%; BBH: 94.29%; AGIEval En: ~89%; ARC: 95.99%
- τ²-bench (airline+retail): 75.61%

Для GLM-5.2 (A: [iternal.ai](https://iternal.ai/llm-selection-guide)):
- HLE: 40.5% без инструментов / 54.7% с инструментами
- ARC-AGI-2: 22.8% — лучший среди open-weight

GAIA, финансовые/юридические бенчмарки — **не найдено** (есть только AA Harvey LAB 91.0% у GLM-5.2 в пересказе BenchLM (A) — юридический срез AA, методология нераскрыта).
Русский язык (mera, ruarena) — **не найдено**.

## 7. LMArena / арены

- glm-5: Arena Elo **1445.27** (A: [prompeteer.ai, 02.08.2026](https://prompeteer.ai/leaderboard) — агрегатор LMArena без раскрытой методологии)
- Swfte (A, [авг 2026](https://www.swfte.com/fr/lmarena)): топ арены — Anthropic 1525; GLM-5 в показанном топ-6 отсутствует.
- WebDev Arena: отдельного числа GLM-5/5.2 не найдено; GLM-4.7 входил в топ-10 Text и WebDev в марте 2026 (A: agileleadershipdayindia).
- BenchLM (A, [27.07.2026](https://benchlm.ai/llm-leaderboard-history)): лидер coding Elo — claude-fable-5 (1553); GLM позиция не извлечена.

## 8. Вербозность / стоимость на задачу

- AA публикует «Output Tokens per Intelligence Index Task» и «Cost per Intelligence Index Task» для GLM-5.2, но числа доступны только в JS-рендере — **не извлечено**.
- Практический кейс (A: Tech Times): GLM-5.2 в Claude Code 45 минут строил план багфиксов по Sentry/Vercel — без токенной статистики.
- Экономическая оценка (A: Tech Times): выводная экономия ~$7 500/мес против Claude Opus 4.8 при 1M output токенов/день.

## Не найдено (честный список)

1. AA Omniscience / % non-refusal для GLM-5 (аналог deepseek 94–96%).
2. AA Coding Index, TTFT, cost-per-task — есть на AA, но не извлечены (JS).
3. Aider polyglot и LiveCodeBench для GLM-5.2; LiveBench для всей линейки.
4. GAIA, финансовые бенчмарки; русскоязычные замеры (mera, ruarena).
5. Промо-цены/временные скидки с датами.
6. Варианты GLM-5-Air / GLM-5-Flash — не существуют в найденных источниках.
7. Независимое подтверждение вендорских чисел GLM-5.2 (SWE-bench Pro 62.1, TB 2.1 81.0) — на 04.08.2026 только самоотчёт Z.ai.
