# Схема находки рой-ревью (FR-05, AC-05, TD §5.1)

Каждая находка тура 1 — structured-запись в fenced-блоке ` ```swarm-structured `
ответа участника (механизм `structured.py` harness; свободный текст — только в
`rationale`, FR-03). Схема полей — инструмента (рой), не harness.

```json
{
  "finding_id": "F-001",
  "author_id": "claude-opus",
  "location": {"path": "services/x/y.py", "line_start": 120, "line_end": 135},
  "category": "correctness | security | concurrency | data_integrity | api_contract | performance | tests",
  "severity": "P1 | P2 | P3 | P4 | P5",
  "in_lens": true,
  "claim": "что не так",
  "evidence": "фрагмент кода / наблюдение с file:line",
  "rationale": "свободный текст (единственное допустимое место)"
}
```

## Поля

- `location` — **обязательна**: находка без location отклоняется ядром
  (граница режимов: «спорное решение без location» → консилиум, RISK-09).
  `path` — относительный путь в проверяемом наборе (diff + focused paths);
  `line_start` — целое ≥ 1; `line_end` — опционален, по умолчанию равен
  `line_start` (точечная location нормализуется в диапазон `line..line`).
- `category` — таксономия (решение №8, образец баг-баунти — ориентир
  CWE/OWASP/Bugcrowd VRT): `correctness`, `security`, `concurrency`
  (вкл. reliability), `data_integrity` (вкл. миграции), `api_contract`,
  `performance`, `tests`. Ядро канонизирует регистр/разделители/синонимы;
  неизвестная категория — отклонение fail-closed.
- `severity` — полная таблица (см. ниже).
- `in_lens` — обязательный bool-тег: находки вне линзы принимаются (чеклист не
  ограничивает модель, решение №7) и тегируются `in_lens: false`; разделение
  in-lens / out-of-lens фиксируется в статистике (FR-12).
- `claim`, `evidence` — обязательные непустые строки.
- `rationale` — опциональный свободный текст.
- `finding_id` — присваивается ядром, сквозной внутри сессии (`F-NNN`);
  нумеруются только принятые находки.
- `author_id` — в транспорте ядра реальный id участника (traceability, привязка
  re-review к автору, FR-09); в payload участников туров 2–4 передаётся
  `anon_id` (TD §5.2). `review_id` спеки FR-05 = пара (`author_id`, adapter
  review id сессии участника).

## Таблица severity

| Код | Метка | В метрике ценности |
| --- | --- | --- |
| P1 | blocker | да |
| P2 | major | да |
| P3 | minor | да |
| P4 | nit | нет — отдельный счётчик (не запрещены) |
| P5 | note/question | нет — информационный, без метрики |

## Механическая валидация location (надзор precision)

Ядро резолвит `location` в реальный файл проверяемого набора
(sandbox-представление): путь обязан существовать в наборе, `line_start`/
`line_end` обязаны быть валидными (≥ 1, `line_end ≥ line_start`, `line_end` ≤
числа строк файла). Находка с невалидной location **отклоняется ДО учёта и
дедупа**, отклонение записывается в журнал сессии (запись: индекс, автор,
причина, деталь).

## Дедуп (FR-06) — кратко

Группировка по (location, category): нормализация location, окно перекрытия
строк/диапазонов (`OVERLAP_WINDOW_LINES = 4` по умолчанию), канонизация
category. Три группы: неуникальные (≥2 слепые модели — автоподтверждённые),
уникальные неподтверждённые (→ тур 2), пограничные (перекрытие location при
разных категориях — разрешает Оркестратор с записью в dedup-journal сессии,
TD §5.6). Override автоподтверждения — с маркером `auto_confirmed_overridden`
и обязательным обоснованием.

## Пример fenced-блока ответа участника (тур 1)

````markdown
```swarm-structured
{"findings": [
  {"location": {"path": "services/x/y.py", "line_start": 120, "line_end": 135},
   "category": "correctness", "severity": "P2", "in_lens": true,
   "claim": "деление на ноль при пустом списке",
   "evidence": "services/x/y.py:127 — total / len(items)",
   "rationale": "пустой items доходит сюда из ветки early-return"}
]}
```
````
