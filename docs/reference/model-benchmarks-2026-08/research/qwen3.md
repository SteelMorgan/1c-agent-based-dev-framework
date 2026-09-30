# Фактура: Alibaba Qwen3 — Qwen3-Max и Qwen3-Coder

Дата сбора: 2026-08-04. Источники помечены: (V) — вендор, (N) — независимый измеритель, (A) — агрегатор/вторичка.
ВАЖНО: на дату сбора актуальная линейка Max уже ушла вперёд (Qwen3.6-Max, Qwen3.7-Max — релизы апрель–май 2026), поэтому «Qwen3-Max» ниже — это базовый триллион-параметровый флагман сентября 2025 (Preview) и его GA/Thinking-версия января 2026. Для честного сравнения с Claude Opus 5 / GPT-5.6 нужно дополнительно собрать фактуру по Qwen3.7-Max — см. раздел «Потомки».

## 1. Цены API ($/M токенов)

### Qwen3-Max (GA, снапшот 2026-01-23, DashScope/Alibaba Cloud)
- Input $1.20, Output $6.00, cache read $0.24 (A — Portkey: https://portkey.ai/models/dashscope/qwen3-max и .../qwen3-max-preview)
- Ступенчатый тариф по длине входа: $1.2/$6 до 32K входа; $2.4/$12 для 32K–128K; $3/$15 для 128K–256K (N — Artificial Analysis: https://artificialanalysis.ai/articles/qwen3-max-thinking-everything-you-need-to-know)
- Preview-снапшот qwen3-max-2025-09-23: $0.86 / $3.44 (A — Portkey: https://portkey.ai/models/dashscope/qwen3-max-2025-09-23)
- КОНФЛИКТ цен: pricepertoken (A) даёт $0.78 input / $3.90 output — https://pricepertoken.com/pricing-page/model/qwen-qwen3-max; anotherwrapper (A) — $0.50 / $5.00 (blended $5.50) — https://anotherwrapper.com/tools/llm-pricing/gemini-31-pro/qwen3-max. Расхождение, вероятно, между промо-прайсом/сторонними провайдерами и официальным DashScope. Промо с датами: точные окна скидок не найдены.

### Qwen3-Coder (480B-A35B, открытые веса; сторонние провайдеры)
- OpenRouter: $0.22 input / $1.80 output, контекст 1M (A — llmrateradar: https://llmrateradar.com/compare/openai-openai-o3-openrouter-vs-qwen-qwen-qwen3-coder-openrouter)
- anotherwrapper: $0.18 blended (A): https://anotherwrapper.com/tools/llm-pricing/claude-sonnet-46/qwen3-coder
- Novita (для Qwen3-Max): $0.50 / $5.00 (A — llm-stats: https://llm-stats.com/models/compare/deepseek-v3.1-vs-qwen3-max)
- Дешевле Claude Sonnet 4.6 на ~98% blended (A, тот же anotherwrapper).

## 2. Кодинг-бенчмарки

### Qwen3-Max-Instruct (вендорские числа на релизе 23.09.2025, V)
- SWE-bench Verified: 69.6 (V — через Cybernews/DailyStar: https://cybernews.com/ai-news/alibaba-released-trillion-parameter-model-qwen3-max/; подтверждается агрегаторами llm-stats)
- LiveCodeBench v6: 69.0 (A — llm-stats: https://llm-stats.com/models/compare/claude-sonnet-4-6-vs-qwen3-max)
- τ2-bench: 74.8 — заявлено выше Claude Opus 4 и DeepSeek V3.1 (V)
- SWE-bench Pro: не найдено (вендор не публиковал; Pro появился позже)
- Terminal-Bench: не найдено для базового Qwen3-Max (Terminal-Bench 2.0 появился у потомков, см. ниже)

### Qwen3-Coder-480B-A35B-Instruct (V + A)
- SWE-bench Verified: 69.6 (V; подтверждение таблицей Kimi: https://modelengine.csdn.net/690b1d8d5511483559e27004.html и A llm-stats)
- SWE-bench Multilingual: 54.7 (A, таблица ModelEngine)
- Multi-SWE-bench: 32.7 (A, там же)
- Terminal-Bench (1.0, Terminus): 37.5 (A, та же таблица; сравнение: Kimi K2-0905 44.5, Claude Sonnet 4 36.4)
- Aider polyglot: 61.8 (A — llm-stats: https://llm-stats.com/models/compare/qwen3-coder-480b-a35b-instruct-vs-mimo-v2-5-pro)
- TAU-bench Retail 77.5 / Airline 60.0, BFCL-v3 68.7 (A — llm-stats)
- Оффициальный GA-провайдерский слепок на DashScope: qwen3-coder-flash $0.30/$1.50 (A — Portkey)

### Потомки (важно для честного сравнения 2026-08)
- Qwen3-Coder-Next (техотчёт arXiv 2603.00729, февраль 2026, V): SWE-bench (Verified) 70.6% (SWE-Agent) / 71.1% (MiniSWE-Agent) / 71.3% (OpenHands) — https://arxiv.org/html/2603.00729v1
- Qwen3.6-35B-A3B (открытый, Apache 2.0, 16.04.2026, A): SWE-bench Verified 73.4, Terminal-Bench 2.0 51.5 — https://awesomeagents.ai/news/qwen-3-6-35b-a3b-open-source-coder/
- Qwen3.6-Max-Preview: SWE-bench Pro 1-е место на момент анонса, Terminal-Bench 2.0 лидер; SWE-bench Verified вендором НЕ опубликован (A — tokencost: https://tokencost.app/blog/qwen3-6-max-preview-pricing)
- Qwen3.7-Max (20.05.2026, Alibaba Cloud Summit, A): Terminal-Bench 2.0-Terminus 69.7 (выше DeepSeek V4 Pro Max 67.9, Opus 4.6 Max 65.4), GPQA Diamond 92.4 — https://therouter.ai/news/qwen3-7-max-agent-benchmark-launch/ и https://wandb.ai/byyoung3/ml-news/reports/Qwen3-7-Max-Benchmark-Scores---VmlldzoxNjk1MzA1MQ; цена $1.25/$3.75 (A — puter: https://developer.puter.com/tutorials/qwen-api-pricing/)

## 3. Artificial Analysis (N)

### Qwen3-Max-Thinking (GA, январь 2026) — https://artificialanalysis.ai/articles/qwen3-max-thinking-everything-you-need-to-know
- Intelligence Index: 40 (Preview: 32; старая версия индекса v3). Контекст: DeepSeek V3.2 = 42, Kimi K2.5 = 47, MiniMax-M2.1 = 40
- HLE: 26% (Preview 13%); IFBench: 71%
- GDPval-AA Elo: 1170
- AA-Omniscience (индекс галлюцинаций, шкала -100..100): -34 — хуже Kimi K2.5 (-11), DeepSeek V3.2 (-23)
- Токены на полный Intelligence Index: 86M output (из них 79M reasoning) — вдвое вербознее Preview (30M)
- Текущий AA Intelligence Index v4.1: значение для Qwen3-Max — «estimate, independent evaluation forthcoming», точного числа на странице модели нет (https://artificialanalysis.ai/models/qwen3-max)
- Coding Index отдельно: не найдено
- TTFT: Alibaba Cloud 2.32s, Novita 3.05s (N — https://artificialanalysis.ai/models/qwen3-max/providers); для Preview: 4.14s у Alibaba Cloud
- Output speed tok/s: точное число AA не извлечено (страница отдаёт только описания графиков) — не найдено
- Метрика «не отказывается от ответа» (%): прямого значения для Qwen3-Max/Coder не найдено; прокси — AA-Omniscience -34 (Thinking)

## 4. Контекстное окно / max output

- Qwen3-Max: 262K (256K) контекст; max output 131K (A — llm-stats: https://llm-stats.com/models/compare/deepseek-v3.1-vs-qwen3-max); AA подтверждает 256K (N)
- Qwen3-Coder-480B: 256K нативно (V — Simon Willison по блогу Qwen: https://simonwillison.net/2025/Jul/22/qwen3-coder/); у провайдеров до 1M (OpenRouter, A)
- Qwen3-Next-80B-A3B: 262K input / 65K output (A — getmaxim)

## 5. Статус релиза

- Qwen3-Max-Preview: 5–6 сентября 2025, >1T параметров MoE, 36T токенов обучения, веса НЕ открыты (A — VentureBeat: https://venturebeat.com/technology/qwen3-max-arrives-in-preview-with-1-trillion-parameters-blazing-fast)
- Qwen3-Max (Instruct, GA-анонс): 23 сентября 2025, Apsara Conference (A — Cybernews/DailyStar)
- Qwen3-Max-Thinking / снапшот 2026-01-23: январь 2026, proprietary, text-only, без мультимодальности (N — AA)
- Qwen3-Coder-480B-A35B: 22 июля 2025, Apache 2.0, открытые веса, MoE 480B/35B active (A — Simon Willison; лицензия подтверждена llm-stats)
- Qwen3-Coder-Next: техотчёт arXiv февраль 2026 (V)

## 6. Не-кодинговые замеры

- LMArena Text: Qwen3-Max-Instruct preview — 3-е место глобально на релизе, ~1430 (A — topaiproduct: https://topaiproduct.com/2025/12/02/the-battle-for-ai-supremacy-deepseek-v3-2-vs-qwen-max-vs-kimi-k2-thinking/; qwq32). Текущие слепки (A, 2026-08): Qwen3-Max 1443, Qwen3-Max-Thinking 1439 — https://openlm.ai/chatbot-arena/ и https://metatext.io/benchmarks/lmarena-elo (конфликт мелкий, оба значения приведены)
- WebDev Arena: не найдено
- GPQA Diamond: Preview 76.4% (N — AA через Cybernews); llm-stats/vals дают 79.55±2.03 (N — vals.ai: https://www.vals.ai/models/alibaba_qwen3-max) — конфликт (разные версии/снапшоты)
- SuperGPQA: 65.1 (A — llm-stats) vs 81.4 (V через qwq32) — сильный конфликт, вероятно разные протоколы
- AIME 2025: 81.6 (V/A — llm-stats)
- HLE: 26% для Max-Thinking (N — AA); для базового — не найдено
- LiveBench: Qwen3-235B-A22B 77.1 — топ на старом слепке (A — llmdb: https://llmdb.com/benchmarks/livebench); для Qwen3-Max не найдено
- Финансовые/юридические (N — vals.ai): CorpFin v2 55.94, TaxEval v2 73.51
- BrowseComp/GAIA/τ2-bench полный: τ2 74.8 (V, см. выше); BrowseComp и GAIA для Qwen3-Max — не найдено (есть у потомка Qwen3.5-397B: BrowseComp 78.6, HLE 48.3 — A, arXiv 2603.15726)
- Русский язык: не найдено (отдельных русскоязычных бенчмарков по Qwen3-Max/Coder не обнаружено; у Qwen исторически есть MMMLU 89+ у Sonnet-класса, но по самим моделям данных нет)

## 7. Вербозность / стоимость на задачу

- AA: Qwen3-Max-Thinking — 86M output-токенов на Intelligence Index (79M reasoning), Preview — 30M; сопоставимо с Kimi K2.5 (89M), сильно меньше GLM-4.7 (167M) (N — AA)
- Cost to run AA Intelligence Index / cost per task: графики есть, числа не извлечены — частично не найдено
- Vibe Code Bench v1.1: 3.51±1.29 (N — vals.ai)

## Не найдено (сводка)

1. Метрика AA «не отказывается от ответа» в % для Qwen3-Max/Coder (есть только AA-Omniscience -34)
2. Output speed tok/s по AA для Qwen3-Max (только TTFT 2.32s)
3. AA Coding Index для Qwen3-Max
4. SWE-bench Verified для Qwen3.6/3.7-Max (вендор сознательно не публикует — A tokencost)
5. LiveCodeBench для актуальных Max-версий; LiveBench для Qwen3-Max
6. BrowseComp / GAIA для Qwen3-Max/Coder
7. Русскоязычные бенчмарки
8. Официальные промо-окна цен DashScope с датами
9. Стоимость на задачу в $ (кроме токенной вербозности)
