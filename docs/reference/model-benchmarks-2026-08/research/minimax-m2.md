# MiniMax M2 → M3: фактура для бенчмарка (август 2026)

Исследование от 2026-08-04. Исходный запрос был по MiniMax M2, но линейка ушла далеко вперёд:
M2 (27.10.2025) → M2.1 (23.12.2025) → M2.5 (12.02.2026) → M2.7 (18.03.2026) → **M3 (01.06.2026)** — актуальный флагман.
Фактура собрана по **M3** (актуальная) с контекстом по M2.7/M2.5 как «дешёвым воркхорсам».
Источники: (V) вендор, (N) независимый измеритель, (A) агрегатор/вторичный обзор.

## 0. Линейка и статус релизов

| Модель | Дата | Статус | Веса / лицензия |
|---|---|---|---|
| MiniMax-M2 | 2025-10-27 | GA, open-weight | 230B MoE / 10B active (A: elvissaravia.com) |
| M2.1 | 2025-12-23 | GA (A: apisrouter.com) | — |
| M2.5 (+ Lightning) | 2026-02-12 | GA, open-weight, Modified MIT (A: chatforest.com) | 229B / 10B active, 200K ctx |
| M2.7 | 2026-03-18, веса на HF ~2026-04-12 | GA, open-weight, modified MIT (A: marktechpost, hokai.io) | 230B / 10B active, 204 800 ctx |
| **M3** | **2026-06-01**, веса на HF ~07.06 | GA, open-weight, **MiniMax Community License** (коммерчески ограниченная: атрибуция «Built with MiniMax M3», уведомление, письменное разрешение при выручке >$20M/год) (A: minimax-ai.chat, codersera) | ~428B total / ~23B active, MSA (MiniMax Sparse Attention), нативная мультимодальность (текст/изображения/видео → текст) |

- M3: API ID `MiniMax-M3`, endpoint'ы OpenAI-совместимый (`https://api.minimax.io/v1`) и Anthropic-совместимый (`https://api.minimax.io/anthropic`). Режимы thinking: adaptive / off; дефолты различаются между endpoint'ами (A: minimax-ai.chat).
- Внимание: MSA paper-замеры (28.4× меньше attention compute при 1M, 14.2× prefill / 7.6× decode speedup) делались на 109B экспериментальной модели, не на полном 428B чекпоинте (N-разбор: minimax-ai.chat).

## 1. Цены API ($/M токенов)

### M3 (V, по прайсингу MiniMax на 2026-07-10, через minimax-ai.chat; подтверждено aiflashreport.com 2026-07-20 и minimax-ai.chat/comparison 2026-07-27)

| Тир | Вход | Input | Output | Cache read |
|---|---|---|---|---|
| Standard | ≤512K | $0.30 | $1.20 | $0.06 |
| Standard | >512K | $0.60 | $2.40 | $0.12 |
| Priority (1.5×) | ≤512K | $0.45 | $1.80 | $0.09 |
| Priority | >512K | $0.90 | $3.60 | $0.18 |

- Прайсинг-страница MiniMax подаёт текущие цены как «постоянная скидка 50%» от зачёркнутых листовых — т.е. промо-рамка, но без даты окончания (V/A).
- Порог >512K считается по input включая cache-hit токены; переход через порог переводит ВЕСЬ запрос в дорогой тир (V).
- Fireworks: при open-weight релизе цена M3 снижена до паритета с M2.7; launch pricing был ~2× M2.7 (N-хостер: fireworks.ai).
- Агрегаторы с наценкой: Requesty $0.30/$1.20 (без наценки, заявляют), pricepertoken $0.24/$0.96 (?), NovAI $0.352/$1.41 — расхождения, считать first-party $0.30/$1.20 эталоном (A).
- Подписки Token Plans: Plus $20/мес, Max $50/мес, Ultra $120/мес (V).

### M2.7 / M2.5 (воркхорс-контекст)

- M2.7: $0.30 in / $1.20 out / $0.06 cache read, авто-кеш без конфигурации (V, через buildfastwithai 2026-04-13, tokencost 2026-05-15).
- M2.5: $0.15 / $1.15 (A: chatforest); Lightning $0.30/$2.40 при 100 tok/s, стандарт 50 tok/s; cache read $0.03 (A: nevercodealone.de).
- Пример стоимости (V-расчёт): 200K in + 20K out = $0.084; 700K in + 30K out = $0.492 (весь запрос в >512K-тире).

### Контекст сравнения с конкурентами (A: minimax-ai.chat/comparison, 2026-07-27)

- DeepSeek V4 Flash: $0.14 / $0.28 / $0.0028 — дешевле M3 в ~2–4×.
- DeepSeek V4 Pro: $0.435 / $0.87 / $0.0036 (промо до 31.05, потом рост — tokencost).

## 2. Бенчмарки

### M3

| Бенчмарк | Результат | Тип | Источник |
|---|---|---|---|
| SWE-bench Verified | 80.5% | V (provider exact) | benchlm.ai ledger |
| SWE-bench Pro | 59.0% | V (scaffold Claude Code, внутренняя инфраструктура) | benchlm, minimax-ai.chat; > GPT-5.5 (58.6) |
| Terminal-Bench 2.1 | 66.0% | V (sandbox 8-core/16GB, Terminus 2, 2h timeout) | benchlm, minimax-ai.chat (в ledger BenchLM записан как «Terminal-Bench 2.0» — расхождение в метке версии) |
| SWE-fficiency | 34.8% | V | minimax-ai.chat |
| KernelBench Hard | 28.8% | V | minimax-ai.chat |
| NL2Repo | 42.1% | V | benchlm |
| VIBE V2 | 50.1% | V | benchlm |
| OSWorld-Verified | 70.06% | V | benchlm, swfte |
| OSWorld 2.0 | 4.6% | N (paper leaderboard) | benchlm (сильное расхождение с OSWorld-Verified — разные версии теста) |
| BrowseComp | 83.5% | V | llm-stats, towardsai; Opus 4.7 = 79.3 |
| MCP Atlas | 74.2% | V (судья Gemini 2.5 Pro) | minimax-ai.chat, benchlm |
| τ²-Bench | 88.9% | A (без методологии) | requesty.ai |
| GPQA Diamond | 92.9% | V/A | aiflashreport, requesty |
| MMMU-Pro | 78.1% | V | benchlm |
| USAMO 2026 | 85.7% | V | benchlm |
| GDPval-AA ELO | M2.7: 1495 (топ open-weight, ниже Opus 4.6/Sonnet 4.6/GPT-5.4) | V | marktechpost |
| LMArena (Text, Thinking) | ~1440 ELO, #30 | N | metatext.io 2026-08-04 |
| LMArena WebDev | #4 | A | modelmatchbook |
| LMArena Document (org frontier) | 1435 | N | modelgauntlet |
| LMArena Code Arena (frontend) | «вышел в топ-7, сдвинул Pareto-фронт» (июнь 2026); позже GLM-5.2 обошёл M3 | N | lmarena.ai via traeai |
| Toolathon | M2.7: 46.3% | V | marktechpost |
| MLE Bench Lite | M2.7: avg medal rate 66.6% (наравне с Gemini-3.1; Opus-4.6 75.7%, GPT-5.4 71.2%) | V | marktechpost |

### M2.7 (контекст)

- SWE-bench Verified 75.4% (A: benchlm leaderboard, #2 среди open-weight после ? — «MiniMax M2.7, open weight, 75.4%»).
- SWE-Pro 56.22% (= GPT-5.3-Codex) (V: marktechpost).
- Terminal-Bench 2: 57.0% (V).
- SWE Multilingual 76.5, Multi-SWE-Bench 52.7, VIBE-Pro 55.6 (~Opus 4.6), NL2Repo 39.8 (V).

### Artificial Analysis (N)

- Intelligence Index v4.1: **M3 = 44** (open-weight лидер GLM-5.2 = 51; DeepSeek V4 Pro = 44; Kimi K2.6 = 44; M2.7 = 38) — howaiworks.ai, minimax-ai.chat.
- Конфликт версий: июньская статья AA (08.06.2026) давала M3 = **55** на старой версии индекса; 15.06 AA перешёл на v4.1 с пересборкой suite (9 evals: GDPval-AA v2, τ³-Banking, Terminal-Bench v2.1, SciCode, HLE, GPQA, CritPt, AA-Omniscience, AA-LCR). Сравнивать только внутри одной версии (N).
- Coding Index: 58.6% (A: requesty — вероятно ссылка на AA Coding Index, без даты версии).
- Скорость: 96.8 tok/s output (N, first-party API, minimax-ai.chat); 94.7 tok/s (N, howaiworks); ~80–82 tok/s у агрегаторов (A: swfte 80, benchlm 82). BenchLM TTFT 25.78 s — вероятно, включает reasoning; AA-замер TTFT 1.71 s (N, minimax-ai.chat). Конфликт объясним разницей «first token» vs «first answer token с thinking».
- Метрика галлюцинаций/не-отказов (аналог DeepSeek 94–96%): **не найдена** в открытом доступе. AA измеряет это теперь через AA-Omniscience Index (шкала −100…100), но конкретное значение для M3 извлечь не удалось (страница AA рендерится JS).

## 3. Контекстное окно / max output

- M3: 1 000 000 токенов суммарно (input+output), «гарантированный минимум 512K» на продуктовой странице (V). На старте open-weights — 500K у хостеров, 1M «в ближайшие дни» (N: fireworks).
- Max output: hard max 524 288; рекомендуемый 131 072 (V). Агрегаторы дают 64K (swfte) и 128K/511K (requesty/aiflashreport) — расхождения агрегаторов, first-party: 128K рекомендовано / 512K hard (V).
- M2.7: 204 800 (A: hokai). M2.5: 200K (A: chatforest).
- MSA: per-token compute −95%, 9× prefill, 15× decode при 1M vs M2.7 (V).

## 4. Агентные / tool-use замеры

- τ²-Bench 88.9% (A: requesty); в AA v4.1 используется τ³-Banking, значение не опубликовано в доступных выдержках.
- Toolathlon (M2.7): 46.3% (V).
- MCP Atlas: 74.2% (V, судья Gemini 2.5 Pro).
- BrowseComp: 83.5 (V; N-подтверждение llm-stats: дешевле всех в ±10% от лидера).
- OSWorld-Verified 70.06% (V) / OSWorld 2.0 4.6% (N — конфликт версий теста, оба приведены).
- MM Claw (собственный тест MiniMax по OpenClaw): 97% skill compliance на 40 скиллах >2000 токенов, общая точность 62.7% ≈ Sonnet 4.6 (V).
- Долгие автономные прогоны (V, first-party демо): ~12ч воспроизведение ICLR-статьи; ~24ч CUDA-оптимизация (147 сабмишенов, 1959 tool calls, 9.4× speedup); 12ч post-training эксперимент.
- GAIA: **не найдено**.

## 5. Вербозность / стоимость на задачу

- AA отслеживает «Output tokens per Intelligence Index task» и «Cost per Intelligence Index task» для M3, но конкретные значения извлечь не удалось (JS-рендеринг) — **не найдено**.
- Для исходного M2 (11.2025): «#1 token usage among [open models]» на Artificial Analysis — т.е. самый экономичный по токенам на задачу среди open-weight на тот момент (A: elvissaravia.com).
- minimax-ai.chat прямо рекомендует мерить cost per successful task и предупреждает, что verbose reasoning раздувает output-бюджет; GLM-5.2 описан AA как «более токеноёмкий», чем M3 (A/N).
- BenchLM blended cost M3: $0.75/M (A).

## 6. Позиционирование vs конкуренты (для сравнительной таблицы)

- AA Intelligence v4.1 open-weight: GLM-5.2 (51) > M3 (44) = DeepSeek V4 Pro (44) = Kimi K2.6 (44) > M2.7 (38) (N).
- Цена/качество: M3 при $0.30/$1.20 — дешевле GLM-5.2, дороже DeepSeek V4 Flash ($0.14/$0.28) в ~2× по input и ~4× по output.
- Уникальное сочетание M3: 1M ctx + нативная мультимодальность (видео!) + open weights + дешёвый first-party API (A-консенсус).
- Лицензия M3 строже, чем у M2.5/M2.7 (Modified MIT) и GLM-5.2 (MIT): MiniMax Community License с атрибуцией и revenue-порогом $20M (A: minimax-ai.chat).

## 7. Не найдено (честно)

1. AA-метрика галлюцинаций/не-отказов в % (аналог DeepSeek 94–96%) — не найдена; есть только AA-Omniscience Index в составе v4.1 без публичного числа.
2. Aider polyglot — не найдено ни для M3, ни для M2.7.
3. LiveCodeBench — не найдено (упоминается только в общем контексте BenchmarkList, без числа).
4. LiveBench (общий индекс) — не найдено число для M3/M2.7.
5. GAIA — не найдено.
6. Точные значения AA «output tokens per task» / «cost per Intelligence Index task» — не извлечены (JS-страница).
7. Промо с явными датами окончания для M3 — нет; «постоянная скидка 50%» без дедлайна. (Для DeepSeek V4 Pro, наоборот, промо с дедлайном 31.05 было.)

## Источники

- https://www.marktechpost.com/2026/04/12/minimax-just-open-sourced-minimax-m2-7-... (V-цифры M2.7)
- https://fireworks.ai/blog/minimax-m3-launch (N-хостер)
- https://minimax-ai.chat/models/minimax-m3/ (A, глубокий разбор, цены V-проверены 10.07.2026)
- https://minimax-ai.chat/comparison/deepseek/ (A, сравнение цен 27.07.2026)
- https://benchlm.ai/models/minimax-m3 (A-ledger с пометками provider/benchmark exact, 04.08.2026)
- https://howaiworks.ai/models/minimax-m3 (A, AA v4.1 = 44 и история «55»)
- https://artificialanalysis.ai/models/minimax-m3 (N, методология v4.1; значения не извлечены)
- https://www.requesty.ai/models/minimaxi/minimax-m3 (A: τ²-Bench 88.9, Coding Index 58.6)
- https://metatext.io/benchmarks/lmarena-elo (N: LMArena ~1440, #30)
- https://llm-stats.com/benchmarks/browsecomp (N-агрегатор BrowseComp)
- https://mcpplaygroundonline.com/blog/testing-mcp-with-minimax (A: TB2.1 66, BrowseComp 83.5)
- https://www.buildfastwithai.com/blogs/minimax-m2-7-review, https://tokencost.app/blog/minimax-m2-7-pricing (A: цены M2.7)
- https://chatforest.com/reviews/minimax-m2-5-... (A: M2.5)
- https://nlp.elvissaravia.com/p/ai-agents-weekly-minimax-m2-cursor (A: исходный M2, AA #5 overall / #1 token usage)
- https://apisrouter.com/minimax-m2-7-release-date (A: даты линейки)
- https://hokai.io/hub/companies/minimax (A: профиль компании, HKEX IPO 01.2026)
