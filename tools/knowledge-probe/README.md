# knowledge-probe

Обвязка для тестирования «знаний в весах» LLM-моделей по банку вопросов о 1С/BSL.
Опрашивает каждую модель адаптивно (2 повтора; третий — только если первые два
семантически разошлись), семантически оценивает ответы против эталона и
строит матрицу решений о навыках фреймворка.

Методология и правила интерпретации: см. [METHODOLOGY.md](./METHODOLOGY.md).

## Состав

| Файл | Назначение |
|---|---|
| `probe.py` | Раннер: опрос моделей, адаптивные повторы (2+тай-брейкер), resume, dry-run |
| `grader.py` | Семантическая оценка ответов грейдером (GPT-5.5, reasoning=high) → вердикты |
| `report.py` | Отчёты: `matrix.csv`, `summary.md`, `per-file.md` |
| `config.py` | Реестр моделей (alias→адаптер+id), промпты, таймауты |
| `adapters/claude_cli.py` | Claude (haiku/sonnet/opus) через `claude -p` |
| `adapters/codex_cli.py` | GPT (gpt-5.5/gpt-5.4-mini) через `codex exec` |
| `adapters/deepseek_http.py` | DeepSeek через HTTP `/chat/completions` |
| `samples.jsonl` | 5 вопросов для smoke-теста |
| `METHODOLOGY.md` | Методология (зачем 3 повтора, шкала вердиктов, ограничения) |

## Требования

- Python 3 (stdlib; `requests` опционален — есть fallback на urllib).
- CLI `claude` авторизован (`claude -p ...` работает).
- CLI `codex` авторизован (`codex exec ...` работает), `~/.codex/config.toml`.
- Для DeepSeek: `export DEEPSEEK_API_KEY=<ключ>` (в файлы репо ключ не пишем).

## Изоляция контекста (важно)

Опрашиваемые CLI (`claude`, `codex`) запускаются с рабочим каталогом = **пустой
временный каталог вне репозитория**, чтобы НЕ подтянулись проектный
`CLAUDE.md`, правила и скиллы — иначе измерялось бы «знание + подсказка», а не
«знание в весах». Флаг `claude --bare` НЕ используется намеренно: в этой среде он
ломает OAuth-авторизацию.

## Формат банка вопросов (JSONL, одна строка = один вопрос)

```json
{"id":"Q-BSL-001","category":"BSL","source_file":"framework/.../SKILL.md",
 "source_section":"...","knowledge_type":"...","expected_in_weights":"да|частично|нет",
 "tier":1,"question":"...","reference_answer":"...","grading_notes":"..."}
```

Файлы генерируются другими агентами в
`tasks/TASK-AUDIT-framework-knowledge/questions/questions-*.jsonl`. Их может ещё не
быть — работайте по `samples.jsonl`, схема совпадает.

## Быстрый старт (smoke)

```bash
cd tools/knowledge-probe

# 1. Один прогон одной модели, 2 вопроса, 2 повтора
python3 probe.py --questions samples.jsonl --models haiku --repeats 2 --limit 2

# 2. Оценка полученных ответов
python3 grader.py --questions samples.jsonl --answers results/answers.jsonl

# 3. Отчёты
python3 report.py --grades results/grades.jsonl --out results/
```

## Ярусный прогон Tier 1 (боевой)

Когда появятся `questions-*.jsonl`:

```bash
cd tools/knowledge-probe

# Полный набор моделей, только ярус 1, адаптивные повторы (2 + тай-брейкер), с resume
python3 probe.py \
  --questions '../../tasks/TASK-AUDIT-framework-knowledge/questions/questions-*.jsonl' \
  --models haiku,sonnet,opus,gpt-5.5,gpt-5.4-mini,deepseek-pro,deepseek-flash \
  --tier 1 --repeats 3 --resume --out results/

# Оценка всех ответов
python3 grader.py \
  --questions '../../tasks/TASK-AUDIT-framework-knowledge/questions/questions-*.jsonl' \
  --answers results/answers.jsonl --resume --out results/

# Отчёты
python3 report.py --grades results/grades.jsonl --out results/
```

Прогон последовательный, каждый ответ сохраняется сразу (append). Прервали —
запустите ту же команду с `--resume`, продолжит с места остановки.

### Полезные флаги probe.py

- `--models a,b,c` — подмножество моделей (см. алиасы в `config.py`).
- `--tier N` — только вопросы яруса N.
- `--limit N` — не более N вопросов (после фильтра tier).
- `--repeats N` — макс. число повторов (по умолчанию 3). Адаптивный режим (по
  умолчанию): если два первых ответа семантически совпали (дословно — без вызова
  судьи; иначе судья `EQUIV_MODEL` одним словом SAME/DIFF), третий не задаётся —
  в `answers.jsonl` пишется skip-маркер. `--no-adaptive` — безусловные N повторов.
- `--resume` — пропустить уже полученные `(id, model, repeat)` и skip-маркеры.
- `--dry-run` — показать план без вызовов.

## Модели и их id

Реестр — в `config.py` → `MODELS`. При ошибке «unknown model» правьте ТОЛЬКО этот
словарь. Пометки `ПРОВЕРИТЬ` стоят на id, которые стоит сверить с документацией
провайдера (особенно DeepSeek: `deepseek-chat` / `deepseek-reasoner`).

## Результаты верификации (2026-07-02)

| # | Проверка | Итог |
|---|---|---|
| 1 | `samples.jsonl` (5 вопросов) | создан |
| 2 | probe: haiku, 2×2 — реальный прогон | 4/4 OK; resume пропускает 4/4 |
| 3 | grader на этих ответах | валидный JSON; вердикты `knows` и `unstable` (грейдер поймал галлюцинацию `СеансПроведенПо()`) |
| 4 | codex `gpt-5.5` и `gpt-5.4-mini` — по 1 вызову | оба OK |
| 5 | deepseek без ключа | понятная ошибка `export DEEPSEEK_API_KEY=...`, запись `ok:false` |

## Известные ограничения

- Оценка LLM-грейдером не безупречна — нужна выборочная ручная сверка 5–10%
  вердиктов (особенно `hallucinates` и пограничных `partial`).
- Temperature CLI (`claude`/`codex`) не управляется — отсюда требование ≥3 повторов.
- «knows» в диалоге ≠ применение знания в агентном цикле (см. METHODOLOGY.md).
- DeepSeek id — предположительные (`ПРОВЕРИТЬ` в config), проверялись только без
  ключа; сетевой путь с ключом не гонялся.
