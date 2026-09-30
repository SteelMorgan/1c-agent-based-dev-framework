# Фактура: xAI Grok (флагман) — август 2026

**Важно об уточнении объекта.** Актуальный флагман xAI на август 2026 — **Grok 4.5**, а не Grok 4.1.
Grok 4.1 вышел 17.11.2025 и уже не является топовой моделью линейки (далее были Grok 4.20 — март 2026,
Grok 4.3 — апрель 2026, Grok 4.5 — июль 2026). xAI после поглощения SpaceX работает под брендом
**«SpaceXAI»**. Фактура ниже собрана по Grok 4.5; краткая справка по Grok 4.1 — в конце.

Типы источников: (V) вендор, (N) независимый измеритель, (A) агрегатор/вторичная пресса.

---

## 1. Цены API

| Параметр | Значение | Источник |
|---|---|---|
| Input | $2.00 / 1M токенов | (V) xAI pricing docs, подтв. [ai-tldr.dev](https://ai-tldr.dev/models/grok-4-5/), [MarkTechPost](https://www.marktechpost.com/2026/07/08/spacexai-releases-grok-4-5/) |
| Output | $6.00 / 1M токенов | (V) там же |
| Cache read | $0.50 / 1M (скидка 75%) | (V)/(N) [techjacksolutions](https://techjacksolutions.com/ai-tools/grok/what-is-grok-4-5/), [JQ AI](https://www.ai.joaoqueiros.com/blog/grok-4-5-xai-coding-agent-model) |
| Cache read — конфликт | $0.30 / 1M | (A) [BenchLM](https://benchlm.ai/models/grok-4-5) — расходится с $0.50, у BenchLM прямой ссылки на первоисточник нет; вероятнее $0.50 |
| Long-context надбавка | входы >200K токенов тарифицируются по более высокой (непубличной) ставке | (A) [techjacksolutions](https://techjacksolutions.com/ai-tools/grok/what-is-grok-4-5/), [rapidevelopers](https://www.rapidevelopers.com/ai-api-limits-performance-matrix/grok-4) |
| Cursor-вариант (быстрый SKU) | $4 / $18 за 1M — **не подтверждено** первоисточником | (A) [techjacksolutions](https://techjacksolutions.com/ai-tools/grok/what-is-grok-4-5/) |
| Промо | бесплатное использование «на ограниченное время» в Grok Build и Cursor на старте (с 8–9 июля 2026; точной даты окончания нет) | (V)/(A) [MarkTechPost](https://www.marktechpost.com/2026/07/08/spacexai-releases-grok-4-5/) |

Для сравнения: Grok 4.3 стоил $1.25/$2.50 — т.е. Grok 4.5 **дороже за токен**, весь тезис «дешевле» строится на меньшем числе токенов на задачу.

## 2. Кодинговые бенчмарки

| Бенчмарк | Grok 4.5 | Источник |
|---|---|---|
| SWE-bench Verified | **86.60%** | (N) [vals.ai SWE-bench leaderboard](https://vals.ai/benchmarks/swebench), (A) [bleap.finance](https://www.bleap.finance/en-us/blog/grok-4-5-vs-claude-opus-4-8-vs-chatgpt-4o) |
| SWE-bench Verified — конфликт | «high-50s» | (A) [mindstudio.ai](https://www.mindstudio.ai/blog/grok-4-5-vs-gpt-5-6-sol-agentic-coding-comparison) — резкое расхождение, вероятно другая конфигурация/effort; доверия больше к vals.ai |
| SWE-bench Pro (resolve rate) | **64.7%** (~15 954 output-токенов на задачу) | (V) launch chart xAI, подтв. [ai-tldr](https://ai-tldr.dev/models/grok-4-5/), [contracollective](https://contracollective.com/blog/grok-4-5-vs-opus-4-8-swe-bench-pro-terminal-bench-agentic-coding-2026) |
| Terminal-Bench **2.1** | **83.3%** | (V) launch chart; (A) [llm-stats](https://llm-stats.com/models/compare/gpt-5.4-nano-vs-grok-4.5) |
| Terminal-Bench **2.1** — конфликт | **79.3% ± 1.5** (harness Cursor, стоимость прогона $134.09) | (N) [tbench.ai leaderboard](https://www.tbench.ai/leaderboard/terminal-bench/2.1), [Snorkel](https://snorkel.ai/leaderboard/terminal-bench-2-1/) — независимый прогон команды Terminal-Bench, ниже вендорского 83.3% |
| DeepSWE 1.0 (pass@1, harness вендора) | 62.0% | (V) launch chart |
| DeepSWE 1.1 (mini-swe-agent, DataCurve) | 53% | (V) launch chart |
| AA Coding Agent Index v1.1 | **76.0** — на уровне GPT-5.5 (Codex), чуть ниже Claude Code | (N) [Artificial Analysis](https://artificialanalysis.ai/models/grok-4-5), (A) [llm-stats](https://llm-stats.com/models/compare/gpt-5.4-nano-vs-grok-4.5) |
| AA Coding Index (композит LiveCodeBench+SciCode+Terminal-Bench) | 72.4% | (A) [Requesty](https://www.requesty.ai/models/xai/grok-4.5) |
| CursorBench v3.2 High | есть строка, но **display-only**: Cursor признал, что снапшот кодовой базы Cursor случайно попал в обучение | (N)/(A) [BenchLM](https://benchlm.ai/models/grok-4-5) |
| SWE-bench Multilingual | есть строка от Cursor (launch-row), без числа в открытых сниппетах | (A) [BenchLM](https://benchlm.ai/models/grok-4-5) |
| Aider polyglot | **не найдено** для Grok 4.5 | — |
| LiveCodeBench (отдельное число) | **не найдено** (участвует только в композите AA Coding Index) | — |
| LiveBench (avg) | **75.8** (#12) | (N) LiveBench via [Koda consensus board](https://www.koda.community/benchmarks/), scrape 02.08.2026 |
| LMArena (Arena Elo) | **не найдено** — Grok 4.5 отсутствует в топ-12 LMArena General (срез Koda 02.08.2026, топ ~1485+); точный Elo не опубликован в найденных источниках | (N) [Koda](https://www.koda.community/benchmarks/) |

## 3. Artificial Analysis

| Метрика | Значение | Источник |
|---|---|---|
| Intelligence Index | **54** (high), #6–#8 из 186+ моделей | (N) [AA](https://artificialanalysis.ai/models/grok-4-5) via [techjacksolutions](https://techjacksolutions.com/ai-tools/grok/what-is-grok-4-5/) и [Koda](https://www.koda.community/benchmarks/); (A) Requesty даёт 53.8 — расхождение в десятых, версия индекса/дата среза |
| Coding Agent Index v1.1 | 76.0 | (N) AA |
| Скорость output | **~80 tok/s** (вендор); **58 tok/s** (независимый замер, ниже медианы поля 88) | (V) xAI launch; (A) [BenchLM](https://benchlm.ai/models/grok-4-5) — конфликт V vs N |
| TTFT | **9.92 с** | (A) [BenchLM](https://benchlm.ai/models/grok-4-5); отдельного независимого TTFT от AA в текстовом виде не извлечено (графики) |
| Галлюцинации / надёжность знаний (AA-Omniscience) | Omniscience Index **26** (Grok 4.3: 18); точность 35% → **52%**; **confident-hallucination rate 54%** (Grok 4.3: 25%) — т.е. не-отказ/уверенная ошибка у Grok 4.5 = 54%, против DeepSeek-аналога 94–96% по шкале из задания: Grok существенно хуже, галлюцинирует уверенно вдвое чаще предшественника | (N) AA Omniscience via [techjacksolutions](https://techjacksolutions.com/ai-tools/grok/what-is-grok-4-5/) |
| AA-Omniscience Index — конфликт | 63.0 | (A) [llm-stats](https://llm-stats.com/models/compare/gpt-5.4-nano-vs-grok-4.5) — вероятно другая версия/дата индекса; оба значения приведены |

## 4. Контекст / max output

- Контекстное окно: **500K токенов** (уменьшение против 1M у Grok 4.3) — (V) xAI docs, подтв. (A) [Requesty](https://www.requesty.ai/models/xai/grok-4.5), [BenchLM](https://benchlm.ai/models/grok-4-5).
  - Конфликт: один сравнительный блог заявлял 256K ([pricepertoken](https://pricepertoken.com/pricing-page/model/xai-grok-4) — но та страница про Grok 4); Musk говорил об апгрейде до 1M «в будущем» ([baidu baike](https://baike.baidu.com/en/item/Grok%204.5/3063775)) — не реализовано.
- Max output: **не найдено / не опубликовано** ([rapidevelopers](https://www.rapidevelopers.com/ai-api-limits-performance-matrix/grok-4): «Max output not published»; Requesty: N/A; сторонний реселлер Atlas Cloud показывает 262K — не первоисточник, не подтверждено).
- Rate limits: 150 req/s, 50M tokens/min; регионы us-east-1/us-west-2 — (A) [BenchLM](https://benchlm.ai/models/grok-4-5) со ссылкой на xAI docs.
- Модальности: текст + изображение на входе, текст на выходе; reasoning mode, function calling, structured outputs. Model ID: `grok-4.5` (алиасы `grok-4.5-latest`, `grok-build-latest`).

## 5. Статус релиза

- Private beta: 28.06.2026 (внутри SpaceX/Tesla). Публичный анонс: **08.07.2026**, **GA с 09.07.2026** в Grok Build (модель по умолчанию), Cursor (все планы), SpaceXAI API console.
- EU: на старте недоступен, обещали середина–конец июля 2026; статус EU на август в найденных источниках не зафиксирован.
- Архитектура: MoE, «V9», ~1.5 трлн параметров — **только по словам Маска, официально не подтверждено**. Обучение на десятках тысяч NVIDIA GB300; co-training с Cursor (триллионы токенов реальных dev-сессий).
- Источники: (V)/(A) [ai-tldr](https://ai-tldr.dev/models/grok-4-5/), [techjacksolutions](https://techjacksolutions.com/ai-tools/grok/what-is-grok-4-5/), [MarkTechPost](https://www.marktechpost.com/2026/07/08/spacexai-releases-grok-4-5/).

## 6. Не-кодинговые бенчмарки

| Бенчмарк | Значение | Источник |
|---|---|---|
| GPQA Diamond | **93.1%** | (A) [Requesty](https://www.requesty.ai/models/xai/grok-4.5); [llm-stats](https://llm-stats.com/models/compare/gpt-5.4-nano-vs-grok-4.5) даёт 93.0% — расхождение 0.1 п.п. |
| HLE | отдельное число **не найдено** (HLE входит в AA Intelligence Index v4.1 как компонент, но самостоятельный скор Grok 4.5 в найденных источниках не опубликован) | — |
| BrowseComp | **не найдено** | — |
| τ2-bench | **не найдено** для Grok 4.5 (AA использует собственный τ³-Banking внутри Intelligence Index; отдельного τ2/τ3 числа нет) | — |
| GAIA | **не найдено** | — |
| Юридические | **#1 на Harvey Legal Agent Benchmark** (>1200 практических юридических задач) | (N) Harvey, репортed [MarkTechPost](https://www.marktechpost.com/2026/07/08/spacexai-releases-grok-4-5/), [techjacksolutions](https://techjacksolutions.com/ai-tools/grok/what-is-grok-4-5/) |
| Профессиональная работа (GDPval+) | лидер: 29% mean pass vs 22% (GPT-5.5) и 21% (Opus 4.8); legal 40% vs 27–28%, healthcare 35% vs 23–25% | (N) Snorkel AI via [techjacksolutions](https://techjacksolutions.com/ai-tools/grok/what-is-grok-4-5/) |
| Финансовые | отдельного финансового бенчмарка **не найдено** (только legal/healthcare в GDPval+) | — |
| Русский язык | **не найдено** — ни русскоязычных бенчмарков, ни multilingual-строк; BenchLM прямо показывает категорию Multilingual как «Not measured» | (A) [BenchLM](https://benchlm.ai/models/grok-4-5) |
| ARC-AGI-2 | 52.6% | (N) ARC Prize leaderboard via [BenchLM](https://benchlm.ai/models/grok-4-5) |
| ARC-AGI-3 | 0.3% | (N) там же |
| SciCode | 54.1% | (A) [Requesty](https://www.requesty.ai/models/xai/grok-4.5) |
| AIME 2025 | ~94% (для Grok 4.1; для 4.5 отдельного числа не найдено) | (A) [chatforest](https://chatforest.com/reviews/xai-grok-4-1-post-training-llm-review/) |

## 7. Вербозность / стоимость на задачу

- SWE-bench Pro: **~15 954 output-токенов на задачу** против ~67 020 у Claude Opus 4.8 (max) — **~4.2× меньше output-токенов** при сопоставимом resolve rate. (V) launch chart; независимо не перепроверено. [ai-tldr](https://ai-tldr.dev/models/grok-4-5/), [MarkTechPost](https://www.marktechpost.com/2026/07/08/spacexai-releases-grok-4-5/).
- Terminal-Bench 2.1 независимый прогон: стоимость **$134.09** за полный бенч (Cursor harness). (N) [tbench.ai](https://www.tbench.ai/leaderboard/terminal-bench/2.1).
- AA «cost per Intelligence Index task» и «output tokens per task» для Grok 4.5 существуют на сайте AA, но в текстовом виде не извлеклись (рендерятся графиками) — **точные числа не найдены**, есть только качественная оценка AA: результат Coding Agent Index достигнут «за долю токен-стоимости конкурентов».
- Оценка стоимости SWE-bench Pro задачи из найденных чисел (не опубликованный расчёт): ~16K output × $6/M ≈ $0.10 output на задачу — порядок величины, не измеренный факт.

## Справка: Grok 4.1 (предыдущий флагман, на случай сравнения)

- Релиз 17–19.11.2025 (GA); фокус — пост-тренинг, EQ-Bench #1, снижение галлюцинаций на 65%, Agent Tools API. (A) [chatforest](https://chatforest.com/reviews/xai-grok-4-1-post-training-llm-review/), [vals.ai](https://www.vals.ai/models/grok_grok-4-1-fast-reasoning).
- Grok 4.1 Fast: $0.20/$0.50 за 1M; SWE-bench ~41.4% (vals.ai, reasoning). (N)/(A).
- Кодинг у 4.1 слабый: не в топе SWE-bench Verified (лидировал Claude Opus 4.5 с 80.9% на тот момент).

## Общая оговорка о качестве источников

- Большинство кодинговых чисел (SWE-bench Pro, Terminal-Bench 83.3, DeepSWE) — с launch-чарта вендора; независимый прогон Terminal-Bench 2.1 дал на 4 п.п. ниже (79.3%).
- Число параметров (1.5T) — только заявление Маска.
- CursorBench v3.2 скомпрометирован утечкой кодовой базы Cursor в обучение.
- Данные собраны 04.08.2026; модель живёт ~4 недели, независимое покрытие ещё тонкое.
