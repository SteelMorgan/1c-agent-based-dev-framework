# Симметричное покрытие метрик бенчмарка LLM — исследование фактуры

Дата снимка: **4 августа 2026**. Автор: исследовательский субагент. Язык: русский.

## Контекст и постановка задачи

В прошлой итерации бенчмарка DeepSeek V4 был «обвинён в галлюцинациях» по метрике Artificial Analysis «доля ответов без отказа» (94–96%), но эта метрика была собрана **только для DeepSeek** — асимметрия, дающая остальным моделям нечестное преимущество. Задача этой итерации: собрать **одну и ту же метрику для всех 14 моделей** и определить, какие независимые бенчмарки покрывают весь список для fair-сравнения.

Пометки достоверности: **(N)** — новость/публичная сводка; **(V)** — верифицировано по первоисточнику (страница AA / официальный лидерборд); **(A)** — агрегатор/вторичный источник, требует осторожности.

## Методология сбора

1. `artificialanalysis.ai` — первоисточник. Страницы моделей JS-рендерятся (FetchURL возвращает только методологию), но **embedded JSON (RSC/ld+json payload) в HTML содержит все числовые поля** — извлекались прямым парсингом через `curl`. Это дало точные значения (V) для 13 из 14 моделей.
2. Официальные лидерборды SWE-bench (swebench.com, embedded JSON `leaderboard-data`) и Terminal-Bench (tbench.ai) — парсятся напрямую (V).
3. LMArena (lmarena.ai) и LiveBench (livebench.ai) — JS-рендерятся без embedded JSON; статусы через зеркала/агрегаторы и changelog (A).
4. WebSearch периодически падал с HTTP 500 — пробелы закрывались повторами и прямым парсингом.

### Ключевое методологическое замечание по метрике «галлюцинаций»

Выяснилось (V): метрика AA, о которой идёт речь в брифе («доля ответов без отказа», 94–96% у DeepSeek), — это **AA-Omniscience Hallucination Rate**: доля некорректных ответов среди всех не-верных реакций (incorrect / (incorrect + partial + not attempted)). Точной метрики «non-refusal rate» как отдельной публикуемой колонки у AA **нет** — есть Hallucination Rate, Accuracy и Omniscience Index. Значение «94–96%» из прошлой итерации с высокой вероятностью соответствует **Omniscience Hallucination Rate DeepSeek V4 Pro = 93.98%** (V, embedded JSON AA). То есть «обвинение» строилось на Hallucination Rate, и именно её нужно собрать симметрично.

Источник методологии: https://artificialanalysis.ai/evaluations/omniscience

## Таблица AA-метрик (снимок 2026-08-04, Intelligence Index v4.1)

Источник всех значений ниже — embedded JSON страниц `artificialanalysis.ai/models/<slug>`, если не указано иное. Вариант модели — максимальный effort (базовый slug).

| Модель | II | Coding | Omni-Index | Omni-HR | Цена in/out $/M | tok/s | Достоверность |
|---|---|---|---|---|---|---|---|
| Claude Opus 5 (max) | 60.7 | 78.0 | 31.3 | 50.1% | $5 / $25 | 55.1 | (V) |
| Claude Sonnet 5 (max) | 53.4 | 71.5 | 15.3 | 37.3% | $2 / $10* | ~80–85 | (V) |
| Claude Haiku 4.5 (non-reasoning) | 23.7 (est) | н/д | −7.7 | 24.7% | $1 / $5 | 89.9 | (V) |
| GPT-5.6 Sol (max) | 58.9 | 77.4 | 21.7 | ~88.8% (A) | $5 / $30 | 70.6 | (V)/(A) |
| GPT-5.6 Terra (max) | 55.0 | 76.7 | −0.2 | 85.2% | $2 / $12 | 146.3 | (V) |
| GPT-5.6 Luna (max) | 51.2 | 71.4 | −11.2 | 90.1% | $0.20 / $1.20 | 180.5 | (V) |
| Kimi K3 (max) | 57.1 | 76.2 | 18.4 | 50.9% | $3 / $15 | 35.9 | (V) |
| DeepSeek V4 Pro (max) | 44.3 | 59.4 | −10.0 | **94.0%** | $0.435 / $0.87 | 64.6 | (V) |
| DeepSeek V4 Flash 0731 (max) | 49.9 | 69.1 | −15.7 | 84.4% | $0.14 / $0.28 | 113.5 | (V) |
| GLM-5.2 (max) — представитель GLM-5 | 51.1 | 68.8 | 4.0 | 28.1% | $1.35 / $4.29 | 196.3 | (V) |
| Qwen3-Max | 24.0 | н/д | −43.1 | 89.4% | $1.2 / $6 | 64.2 | (V) |
| Gemini 3.1 Pro Preview | 46.5 | 68.8 | 32.9 | 49.9% | $2 / $12 | 131.8 | (V) |
| Grok 4.1 Fast (reasoning, deprecated) | 30.6 (est) | н/д | −28.7 | 72.4% | н/д на AA (стороннее: $0.20/$0.50) | н/д на AA | (V)/(A) |
| MiniMax M2 | 28.3 (est) | н/д | −46.9 | 88.4% | $0.30 / $1.20 | 124–144 | (V) |

Примечания к таблице:
- \* Sonnet 5: промо-цена $2/$10 до 31.08.2026, далее стандарт $3/$15 (A, developersdigest).
- GPT-5.6 Sol: Hallucination Rate **не извлекается из embedded JSON** страницы модели (рендерится только на клиенте); значение 88.8% — из агрегатора BenchLM, синхронизированного с AA (A). Для полной симметрии помечаем как (A), остальные 13 — (V).
- Claude Haiku 4.5: взят non-reasoning вариант (slug `claude-4-5-haiku`); у reasoning-варианта значения иные, не снимались.
- GLM-5 (базовый, февраль 2026) в AA-индексах не замерен; взят GLM-5.2 как актуальный представитель линейки (II=51.1 подтверждает и FAQ лидерборда AA).
- Grok 4.1: на AA существует только как «Grok 4.1 Fast» (reasoning/non-reasoning), помечен deprecated (заменён на Grok 4.3); цены и скорости на AA нет.
- Qwen3-Coder взят не был: условие брифа «Qwen3-Max и/или Qwen3-Coder» — выбран Qwen3-Max.
- «est» — Intelligence Index помечен AA как estimate («independent evaluation forthcoming»).

### Главный вывод по симметрии метрики галлюцинаций

DeepSeek V4 Pro (94.0%) — **не выброс**: GPT-5.6 Luna 90.1%, Qwen3-Max 89.4%, MiniMax M2 88.4%, GPT-5.6 Sol ~88.8%, GPT-5.6 Terra 85.2%, DeepSeek V4 Flash 0731 84.4%. Асимметрия прошлой итерации (метрика собрана только для DeepSeek) создавала ложную картину уникальной проблемы. При этом у Open-weight/дешёвых моделей HR системно выше; лучшие — GLM-5.2 (28.1%) и Haiku 4.5 (24.7%, но и accuracy 13.7%).

## Матрица покрытия независимых бенчмарков (14 моделей × 7 бенчмарков)

Обозначения: ✅ есть · ⚠️ есть с оговоркой · ❌ нет

| Модель | AA II | LMArena | LiveBench | SWE-bench V. | Terminal-Bench | vals.ai | Aider |
|---|---|---|---|---|---|---|---|
| Claude Opus 5 | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| Claude Sonnet 5 | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| Claude Haiku 4.5 | ✅ | ✅ | ✅ | ✅ (66.6%) | ✅ (28.3–41.8%) | ✅ | ❌ |
| GPT-5.6 Sol | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| GPT-5.6 Terra | ✅ | ⚠️ только Agent Arena | ✅ | ❌ | ❌ | ⚠️ только SkillsBench | ❌ |
| GPT-5.6 Luna | ✅ | ⚠️ только Agent Arena | ✅ | ❌ | ❌ | ✅ | ❌ |
| Kimi K3 | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| DeepSeek V4 Pro | ✅ | ✅ | ✅ | ❌ | ❌ | ⚠️ «DeepSeek V4» без уточнения | ❌ |
| DeepSeek V4 Flash 0731 | ✅ | ✅ | ⚠️ ревизия 0731 не подтверждена | ❌ | ❌ | ❌ | ❌ |
| GLM-5 / 5.2 | ✅ (5.2) | ✅ (glm-5) | ✅ (GLM-5) | ✅ (GLM-5, 72.8%) | ✅ (GLM 5, 52.4%) | ⚠️ GLM 5.2 | ❌ |
| Qwen3-Max/Coder | ✅ (Max) | ✅ (Max preview) | ✅ (Max) | ✅ (Coder, 69.6%) | ✅ (Coder, 27.2%) | ❌ | ❌ |
| Gemini 3.1 Pro | ✅ | ✅ | ✅ | ❌ (есть 3 Pro) | ✅ (80.2%) | ✅ | ❌ |
| Grok 4.1 | ✅ (Fast) | ✅ (4.1-thinking) | ✅ (4.1 Fast) | ❌ | ❌ | ❌ | ❌ |
| MiniMax M2 | ✅ | ❌ (только M2.1 на Code Arena) | ❌ (есть M2.5/M2.7) | ✅ (61.0%) | ✅ (30.0–42.0%) | ❌ | ❌ |

### Полнота покрытия по бенчмаркам

- **AA Intelligence Index (вкл. Omniscience Hallucination Rate, цену, скорость): 14/14** — единственный источник с полным покрытием (с оговорками: Grok 4.1 только как Fast/deprecated, GLM — как 5.2, Sol HR через агрегатор). https://artificialanalysis.ai/leaderboards/models
- **LiveBench: 12–13/14** (MiniMax M2 нет; Flash 0731 без подтверждения ревизии). https://livebench.ai/ (данные через агрегаторы, A)
- **LMArena Text: 11/14** (Terra/Luna только Agent Arena; MiniMax M2 нет). https://lmarena.ai/leaderboard
- **vals.ai: 8–9/14** (нет Flash 0731, Qwen3, Grok 4.1, MiniMax M2; DeepSeek неатрибутирован). https://www.vals.ai/
- **SWE-bench Verified (офиц.): 5/14** — борд сильно отстаёт от рынка (топ ~79%, нет Opus 5/Sonnet 5/GPT-5.6/K3/V4/Gemini 3.1/Grok). https://www.swebench.com/
- **Terminal-Bench (офиц.): 5/14** — аналогично отстаёт. https://www.tbench.ai/leaderboard/terminal-bench/2.0
- **Aider polyglot: 0/14** — лидерборд заморожен на ~августе 2025 (топ: gpt-5 high 88.0%). Для этого списка непригоден. https://aider.chat/docs/leaderboards/

### Вывод для fair-сравнения

1. **Единственный источник с полным (14/14) симметричным покрытием — Artificial Analysis** (Intelligence Index + Omniscience Hallucination Rate + цена + скорость). Именно AA-таблица выше и должна заменить асимметричную метрику прошлой итерации.
2. Второй слой (12+/14): **LiveBench**. Третий: **LMArena** (11/14).
3. Официальные SWE-bench Verified и Terminal-Bench как fair-инструмент для этого списка **непригодны** (отстают на 1–2 поколения моделей); годятся только как точечные проверки для покрытых моделей.
4. Aider polyglot — исключить из брифа (0/14).

## Дополнение 04.08: актуальные версии (замена устаревших строк)

Другие исследователи установили актуальные на 04.08.2026 версии: **Grok 4.5** (GA 09.07.2026, вместо Grok 4.1), **MiniMax M3** (GA 01.06.2026, вместо M2), **Qwen3.7-Max** (20.05.2026, вместо базового Qwen3-Max сентября 2025). GLM-5.2 уже актуален. Все значения ниже — (V), embedded RSC/ld+json JSON страниц AA, снимок 04.08.2026, Intelligence Index v4.1. Пометок estimated у новых строк нет.

| Модель | II | Coding | Omni-Index | Omni-HR | Цена in/out $/M | tok/s | TTFT | Достоверность |
|---|---|---|---|---|---|---|---|---|
| Grok 4.5 (high) — **заменяет Grok 4.1** | 53.8 | 72.5 | 26.4 | 53.5% | $2 / $6 | 59.7 | 10.4 с (reasoning) | (V) |
| MiniMax M3 — **заменяет MiniMax M2** | 44.4 | 58.6 | 1.4 | 16.1% | $0.30 / $1.20 | 84.3 | 1.47 с (TTFC; до answer-токена 25.2 с, reasoning) | (V) |
| Qwen3.7-Max — **заменяет Qwen3-Max** | 46.0 | 66.0 | 14.1 | 22.9% | $2.50 / $7.50 | 207.2 | 2.31 с | (V) |

Источники: https://artificialanalysis.ai/models/grok-4-5 (+ /providers; единственный вариант на AA — «high», slug `grok-4-5`), https://artificialanalysis.ai/models/minimax-m3 (releaseDate 2026-06-01, open weights 428B/23B active; скорость/TTFT — first-party MiniMax), https://artificialanalysis.ai/models/qwen3-7-max (релиз по AA: May 19, 2026; цена/скорость — по API Alibaba).

Замечания к новым строкам:
- MiniMax M3 показывает **лучший Omniscience Hallucination Rate всего списка — 16.1%** (но и низкий accuracy 15.0% — модель много отказывается). Это меняет картину: Qwen3.7-Max (22.9%) и M3 (16.1%) резко лучше устаревших Qwen3-Max (89.4%) и M2 (88.4%) — вывод «китайские/дешёвые модели системно хуже по галлюцинациям» на актуальных версиях не держится.
- Grok 4.5 (53.5% HR) существенно лучше deprecated Grok 4.1 Fast (72.4%) и по II (53.8 vs 30.6 est).

### Закрытые пробелы из первой итерации

1. **GPT-5.6 Sol — Hallucination Rate теперь (V): 88.8%** (`omniscienceBreakdown.hallucinationRate: 0.88826` в embedded JSON страницы https://artificialanalysis.ai/models/gpt-5-6-sol; в прошлый раз искали плоское поле, а оно вложено в `omniscienceBreakdown`). Значение агрегатора подтвердилось первоисточником. По вариантам effort: low 87.3%, medium 87.1%, high 88.0%, xhigh 89.0%, non-reasoning 91.3%.
2. **Цена GPT-5.6 Luna: актуальна $0.20 / $1.20** (V, embedded JSON страницы модели на 04.08.2026; внутренне консистентна: cache hit $0.02 = −90%, cache write $0.25 = 1.25×). Значения $1/$6 — из неизменявшейся с 09.07.2026 релизной статьи AA (list-цены на анонс), оперативная запись модели в 5 раз ниже. В таблицу брифа брать **$0.20/$1.20**.

## Пробелы и незакрытые вопросы (честный список, обновлённый 04.08)

- ~~GPT-5.6 Sol HR только через агрегатор~~ — **закрыто, 88.8% (V)**.
- ~~Цена Luna расходится со статьёй~~ — **закрыто: актуальна $0.20/$1.20 (V)**.
- ~~Grok 4.1 / MiniMax M2 / Qwen3-Max устарели~~ — **заменены на Grok 4.5 / M3 / Qwen3.7-Max (V)**.
- GLM-5 базовый: AA-индексы не замерены, используется GLM-5.2 (это соответствует актуальной версии линейки).
- Claude Haiku 4.5: только non-reasoning снимок; Coding Index отсутствует на AA.
- Покрытие LMArena/LiveBench/SWE-bench/Terminal-Bench/vals.ai проверялось для СТАРЫХ версий (Grok 4.1, M2, Qwen3-Max) — для новых версий матрицу покрытия нужно переснять отдельно, если бенчмарк перейдёт на них.
- «Non-refusal rate» как отдельная метрика AA не публикует — в брифе следует явно писать «AA-Omniscience Hallucination Rate», чтобы не смешивать с refusal.

- GPT-5.6 Sol: Omniscience Hallucination Rate не в embedded JSON AA; значение 88.8% — агрегатор (A). Стоит перепроверить вручную в браузере на странице модели.
- Grok 4.1 (не Fast): на AA нет как самостоятельной модели; цена/скорость отсутствуют. Возможная замена в бенчмарке — Grok 4.3/4.5, но это решение владельца списка.
- GLM-5 базовый: AA-индексы не замерены, взят GLM-5.2.
- Claude Haiku 4.5: только non-reasoning снимок; Coding Index отсутствует на AA.
- MiniMax M2: II помечен estimate; в LMArena и LiveBench отсутствует.
- DeepSeek V4 Flash 0731: в LiveBench запись «DeepSeek-V4-Flash» без ревизии 0731.
- «Non-refusal rate» как отдельная метрика AA не публикует — в брифе следует явно писать «AA-Omniscience Hallucination Rate», чтобы не смешивать с refusal.
- Цена Luna: на AA embedded JSON $0.20/$1.20, тогда как статья AA о релизе указывала $1/$6 (A) — расхождение, вероятно смена тарифа; в таблице — актуальный снимок страницы модели (V), но стоит перепроверить.

## Ключевые URL-источники

- https://artificialanalysis.ai/leaderboards/models — лидерборд AA (FAQ с топ-значениями)
- https://artificialanalysis.ai/evaluations/omniscience — методология Omniscience / Hallucination Rate
- https://artificialanalysis.ai/models/claude-opus-5, /claude-sonnet-5, /claude-4-5-haiku, /gpt-5-6-sol, /gpt-5-6-terra, /gpt-5-6-luna, /kimi-k3, /deepseek-v4-pro, /deepseek-v4-flash, /glm-5-2, /qwen3-max, /gemini-3-1-pro-preview, /grok-4-1-fast-reasoning, /minimax-m2 — embedded JSON моделей
- https://artificialanalysis.ai/articles/opus-5, /articles/gpt-5-6-has-landed — релизные статьи AA
- https://www.swebench.com/ — официальный SWE-bench Verified (180 записей)
- https://www.tbench.ai/leaderboard/terminal-bench/2.0 — официальный Terminal-Bench 2.0
- https://lmarena.ai/leaderboard (+ https://arena.ai/blog/leaderboard-changelog) — LMArena
- https://livebench.ai/ (+ агрегаторы metatext.io, llmapicosts.com, benchlm.ai) — LiveBench
- https://www.vals.ai/benchmarks/swebench, /skillsbench — vals.ai
- https://aider.chat/docs/leaderboards/ — Aider (заморожен)
