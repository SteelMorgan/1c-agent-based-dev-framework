# Gemini 3.1 Pro (Preview) — фактура для бенчмарка LLM

Дата сбора: август 2026. Метки источников: (V) вендор, (N) независимый измеритель, (A) агрегатор/медиа.

## 1. Цены API

- Стандарт (≤200K prompt): **$2.00 / $12.00** за 1M input/output (V, подтверждено несколькими агрегаторами).
  - [pricepertoken.com — Gemini 3.1 Pro Preview](https://pricepertoken.com/pricing-page/model/google-gemini-3.1-pro-preview) (A)
  - [smartchunks.com](https://smartchunks.com/gemini-3-1-pro-benchmarks-gpqa-hle-lmsys-frontiermath/) (A)
- Cache read: **$0.20/1M** (cache write $0.375) — pricepertoken (A).
- Long-context (>200K): **конфликт** —
  - $4 / $18: [BenchLM — Gemini API pricing July 2026](https://benchlm.ai/blog/posts/gemini-api-pricing) (A)
  - $4 / $24 («обе ставки удваиваются»): smartchunks (A). Оба значения приводим; первичный прайс-лист Google не выгружен.
- Google AI Studio (дешевле прямого API): **$1.00 / $6.00**, cache read $0.10 — pricepertoken (A). Вероятно, промо-тариф AI Studio; явных дат промо не найдено.
- Вариант «Custom Tools»: $2/$12, релиз 25.02.2026 — pricepertoken (A).
- Стоимость прогона полного Artificial Analysis Intelligence Index: **$892** (против $2 486 у Claude Opus 4.6 max reasoning, $2 304 у GPT-5.2) — AA via smartchunks (N).

## 2. Бенчмарки кодинга

| Бенчмарк | Значение | Источник |
|---|---|---|
| SWE-bench Verified | **80.6%** (модельная карта Google, V) | smartchunks / wentuo.ai |
| SWE-bench Verified | **78.80%** (независимо, 3-е место после GPT-5.5 и Opus 4.7) | [vals.ai](https://vals.ai/benchmarks/swebench) via inferredresearch (N) |
| SWE-bench Pro | **54.2%** | [claudefabel.com](https://claudefabel.com/blog/claude-opus-4-8-swe-bench-pro-vs-gpt-gemini) (A) |
| Terminal-Bench 2.0 | **68.5%** (V) | smartchunks; подтв. theplanettools, arte.itlibra |
| Terminal-Bench 2.1 | **70.3%** | claudefabel (A) — конфликт версии бенча, оба значения |
| LiveCodeBench Pro | **2887 Elo** (V; vs 2439 у Gemini 3 Pro) | smartchunks |
| Aider polyglot | **не найдено** | — |
| LiveBench | **не найдено** отдельным числом | — |
| MCP Atlas | 69.2% (V) | [wentuo.ai](https://blog.wentuo.ai/en/gemini-3-1-pro-vs-claude-opus-4-6-comparison-en.html) |
| AA-LCR | 72.7% (N) | [BenchLM compare](https://benchlm.ai/compare/gemini-3-1-pro-vs-grok-4) |

Независимый прогон SWE-bench Verified сторонней командой: 58.7% без скаффолдинга, ~65% с Gemini CLI — [contracollective.com](https://contracollective.com/blog/claude-sonnet-4-6-vs-gemini-3-1-pro-swe-bench-2026) (N, методология сомнительная, блог).

### LMArena
- Text Arena (апрель 2026): ~**1493 Elo, #3** (после Opus 4.6 Thinking ~1504, GPT-5.4 High ~1484) (A).
- Vision Arena: **#2, Elo 1334** — Gemini лидирует в vision у Gemini 3 Pro Preview по [nextomoro](https://nextomoro.com/google-deepmind/) (A, апрель 2026).
- Coding Arena: #4, 1276 (там же, A).
- WebDev Arena (теперь Text Arena Coding): Gemini 3.1 Pro вне топ-5 на июнь–июль 2026 — [lmcouncil.ai](https://lmcouncil.ai/benchmarks) (A); точное значение не найдено.

## 3. Artificial Analysis

- **Intelligence Index: конфликт по датам** —
  - 57 (тай с GPT-5.4) на релизе, февраль–апрель 2026 (smartchunks, A; DeepLearning.AI The Batch, N).
  - 46–46.5 по свежим сравнениям AA (июль 2026) — [artificialanalysis.ai comparisons](https://artificialanalysis.ai/models/comparisons/gemini-3-5-flash-vs-gemini-3-1-pro-preview) (N). Вероятно перекалибровка индекса; Gemini 3.5/3.6 Flash (high) уже выше (50).
- Coding Index: отдельным числом **не найдено**.
- Скорость: **~122–133 tok/s** output (N, AA; разные прогоны: 122, 125, 132, 133).
- TTFT: **~21–30 с** (N, AA; длинный из-за thinking: 21.3 / 24.8 / 28.9 / 30.2 с в разных прогонах).
- Галлюцинации/не-отказы: прямой аналог «94–96%» для Gemini 3.1 Pro **не найден**. Ближайшее:
  - AA-Omniscience — Gemini 3.1 Pro входит в Intelligence Index; отдельный Omniscience Index для 3.1 Pro не опубликован в найденных источниках (для старших моделей: Claude 4.1 Opus 4.8, Grok 4 и GPT-5 (high) accuracy 39% при hallucination 64%/81% — [arXiv 2511.13029](https://arxiv.org/html/2511.13029v1), N).
  - Калибровка на HLE: calibration error 51 (хуже, чем 38 у GPT-5.4 Pro) — чаще уверенно ошибается (Scale AI via smartchunks, N).
  - Медицинское извлечение: hallucination rate 7% (лучший среди протестированных), null-field accuracy 93% — [arXiv 2604.16504](https://arxiv.org/pdf/2604.16504) (N).

## 4. Контекст / мультимодальность

- Контекст: **1M input**, max output **64K (65.5K)** (V, подтв. galaxy.ai, theplanettools).
- Модальности: текст, vision, audio-in, video; reasoning с 3 уровнями thinking (Low/Medium/High) (V).
- MMMU-Pro: **80.5%** (V, модельная карта via smartchunks). MMMU отдельно не найдено.
- MMMLU: 92.6% (V). MMLU-Pro: 91.2% (V, Google blog via MDPI-таблица, A).
- OSWorld: **не найдено** для 3.1 Pro (Opus 4.6 = 72.7%; Sonnet 5 = 81.2% OSWorld-Verified — Gemini не публиковал).
- Vision Arena: #2, 1334 Elo (см. выше). Visual Logic: 40.4% — топ по [benchmarklist.com](https://benchmarklist.com/benchmarks/) (N/A).

## 5. Статус релиза

- **19 февраля 2026** — анонс Gemini 3.1 Pro Preview (Google blog) (V; подтв. sohu, o-mega.ai, smartchunks). Версия «Custom Tools» — 25.02.2026 (A).
- Статус на август 2026: **всё ещё Preview** (API ID `gemini-3.1-pro-preview`); GA заявлен «soon», дата не объявлена. Доступен в Gemini API, Vertex AI, Gemini app, NotebookLM. [tokenmix.ai (май 2026)](https://tokenmix.ai/blog/gemini-3-5-pro-status), [hidekazu-konishi timeline](https://hidekazu-konishi.com/entry/google_gemini_model_release_timeline.html) (A).
- Gemini 3.1 Flash (полноценный текстовый): **не найден** — в линейке 3.1 есть Flash-Lite ($0.25/$1.50, 363 tok/s, релиз 4 марта 2026, Preview) и Flash Live Preview (голос, 7 мая 2026, контекст 131K). Основной Flash-трек ушёл в Gemini 3.5 Flash (Stable, GA с Google I/O мая 2026). Gemini 3.1 Flash Image — GA 28 мая 2026.
- Gemini 3.5 Pro: «coming soon», не выпущен (на 22.05.2026; позже не подтверждено).

## 6. Не-кодинговые бенчмарки

| Бенчмарк | Значение | Источник |
|---|---|---|
| GPQA Diamond | **94.3%** (V); 94.1% (N, PricePerToken/AA) — лидер на момент релиза | smartchunks |
| HLE (no tools) | 44.4% (V) / 45.4% (BenchLM, A) — конфликт, оба значения | smartchunks, benchlm |
| HLE Thinking High | **46.44% ±1.96, #1** (Scale AI leaderboard, N) | smartchunks |
| ARC-AGI-2 | **77.1%** (V) | sohu, smartchunks |
| BrowseComp | **85.9%** (V) | theplanettools, furukama |
| τ2-bench | «strong tool-use results» (V), Retail — число не найдено; входит в AA Intelligence Index (τ²-Bench Telecom) | theplanettools |
| GAIA | **не найдено** | — |
| **LegalBench** | **87.4% — ПОДТВЕРЖДЕНО**: Vals AI LegalBench, лидер по Stanford AI Index 2026 (ранний снимок); на 17.06.2026 уже #2 после Claude Fable 5 (88.56%) | [AI Index 2026 перевод](https://www.gm7.org/archives/116929) (N), [Activant Capital](https://www.activantcapital.com/research/legal-ai/) (N), [AI Benchmark Digest](https://buttondown.com/doroshenko/archive/ai-benchmark-digest-2026-06-14/) (A) |
| FrontierMath | ~на уровне Gemini 3 Pro (38% T1–3 / 19% T4); решена новая T4-задача | Epoch AI via smartchunks (N) |
| MRCR v2 (128k) | 84.9% (V) | smartchunks |
| CritPt | 17.7% (N) | benchlm |
| HealthBench Hard | 20.6% (A) | benchlm |
| MedXpertQA (Text) | 71.5% (A) | benchlm |
| Финансовые бенчмарки | **не найдено** | — |
| Русский язык | прямых цифр **не найдено**; по «Evals for Every Language» Gemini 3.1 Pro уступает Opus 4.7 на ряде малых языков (chm 62.12), ru отдельно не публиковался в найденном | AI Benchmark Digest (A) |
| Mercor APEX-Agents | #1 на релизе (N) | TechCrunch via inferredresearch |

## 7. Вербозность / стоимость на задачу

- Полный прогон AA Intelligence Index: $892 (N) — самый дешёвый среди фронтир-моделей.
- ARC-AGI-2 стоимость на задачу: для сравнения Gemini 3 Deep Think $13.62/задачу; для 3.1 Pro отдельно не найдено (DeepLearning.AI, N).
- Вербозность (токены на ответ) отдельной метрикой **не найдена**; Flash-Lite позиционируется как «потребляет меньше всего токенов» (36kr, A).

## Не найдено (честно)

- Aider polyglot, LiveBench (точные значения).
- AA Coding Index отдельным числом; прямая метрика не-отказов/галлюцинаций в %, сопоставимая с 94–96% DeepSeek.
- OSWorld / скриншот-понимание (GUI grounding) для 3.1 Pro.
- GAIA, финансовые бенчмарки, русскоязычные бенчмарки.
- Даты и условия промо-тарифов (AI Studio $1/$6 — без дат).
- Текстовый Gemini 3.1 Flash (существует только Flash-Lite и Flash Live).
- Подтверждённая дата GA 3.1 Pro.
