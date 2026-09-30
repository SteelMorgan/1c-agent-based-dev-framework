# Track record роя — `.swarm-track-record/` (FR-12, AC-12, TD §5.7, §8.2)

Durable-хранилище статистики роя на уровне репозитория. Переживает `close`
любой сессии, исключено из cleanup-checkpoint; НЕ смешивается с консилиумным
`.consilium-track-record/` (FR-01е: у инструментов разные observations-файлы).
Содержимое — runtime-данные, в git не коммитится.

```
.swarm-track-record/
├── observations.jsonl   # источник истины, append-only
├── strengths.json       # генерируемый кэш/отчёт (на чтении не доверяется)
├── gate-outcomes.jsonl  # durable след исходов блокирующих gate (F-006)
└── config.json          # счётчики: reviews_completed, light_reviews_completed,
                         # calibration_every (живёт в конфиге, не в коде, TD §8.2)
```

## Gate outcomes (`gate-outcomes.jsonl`, F-006 E2E-02)

Исход блокирующего gate-прохода записывается ядром на `close` (идемпотентно,
флаг `gate_outcome_recorded` в сессии) и переживает удаление эфемерного
каталога сессии — `report.md` с секцией «Gate-проход» удаляется вместе с
сессией, поэтому булева `gate_pass` в observations недостаточно. Запись:

```json
{
  "session_id": "swarm-...", "tier": "light", "mode": "acceptance",
  "caller_id": "claude-opus", "caller_family": "claude",
  "reviewer_id": "codex-gpt", "status": "approved",
  "iterations": 1, "max_iterations": 3,
  "dispositions": {"F-001": "agree"}, "verdicts": [...],
  "conditional": null, "escalation": null, "reassignments": [],
  "recorded_at": "2026-07-30T..."
}
```

`status` — финальный статус gate (`approved | blocked | escalated |
conditional | pending`) на момент close; `conditional` — условия ревьюера и
решение Оркестратора по `conditional_accept` (F-007); `reassignments` —
журнал переназначений gate-ревьюера (F-012). Пара `caller_id` /
`reviewer_id` сохраняет проверяемый после cleanup identity trace;
`caller_family` — compatibility metadata и не доказательство independence.
Файл не входит в strength-
проекции и не читается протоколом — аудиторский след.


Слой хранения — harness `review-harness/scripts/track_record.py`
(параметризован `storage_dir`); сборка observation и проекция — ядро роя
`swarm_core.py` (протокол инструмента). Поле порядкового номера сессии —
`review_seq` (= `reviews_completed + 1` на момент записи).

## Observation (одна на участника на сессию)

Пишется ядром на `report`/`close` (идемпотентно: флаг `track_record_written`
в сессии; retry `close` и `close` после `report` не дублируют записи).

```json
{
  "review_session_id": "swarm-...", "date": "2026-07-29", "review_seq": 7,
  "participant_id": "claude-opus", "family": "claude",
  "role": "security",
  "category": "security",
  "forced": false, "gate_pass": false,
  "findings_unique_confirmed": 2, "findings_unique_unconfirmed": 1,
  "findings_nonunique": 3, "nonunique_missed": 1,
  "upheld_as_author": 2, "overruled_as_author": 1, "unvalidated": 1,
  "attacks_made": 4, "attacks_confirmed_overrule": 1,
  "auto_confirmed_overridden": 0,
  "fixed": 0, "partially": 0, "not_fixed": 0,
  "nit_count": 1, "in_lens_count": 4, "out_of_lens_count": 1,
  "quota_mode": null, "quota_fallback_reason": null,
  "calibration_run": false
}
```

Сборка счётчиков из состояния сессии (`swarm_core.build_observations`):

- **Исход находки**: неуникальная автоподтверждённая (≥2 слепые модели, FR-06)
  — подтверждена без треда; уникальная — по треду: `confirmed`/`reclassified` →
  подтверждена; `withdrawn` → не подтверждена (атака состоялась, находка не
  пережила); `open` (деградация, TD §11) и находки без дедупа/треда (ранний
  close, routed из туров 2–4) → `unvalidated` (F-03, R-Final): атака НЕ
  состоялась — находка пишется отдельным счётчиком `unvalidated` и НЕ входит
  в `overruled_as_author`/`findings_unique_unconfirmed` и в знаменатели
  `accept_rate`/`upheld_rate_as_author` (FR-12: upheld — пережившие атаку);
  `contested` — решением арбитража (`upheld`/`reclassified` → подтверждена,
  `overruled` или отсутствие арбитража → неснятое disagreement → нет, FR-08).
- `nonunique_missed` — автоподтверждённые кластеры, где участник НЕ автор
  (пропущенная им подтверждённая находка; знаменатель охвата).
- `upheld_as_author`/`overruled_as_author` — уникальные находки автора,
  пережившие / не пережившие атаку туров 2–4.
- Атака — `overruled`-вотум в волнах туров 2/4 (`upheld` — не атака);
  `attacks_confirmed_overrule` — атаки по находкам с итогом «не подтверждена».
- `auto_confirmed_overridden` — находки автора в кластерах со снятым
  Оркестратором автоподтверждением (FR-06).
- `fixed`/`partially`/`not_fixed` — диспозиции re-review (заготовка T-12:
  читаются из `session["rereview"]`, `introduced_new_issue` → `not_fixed`).
- `nit_count` — находки severity P4; `in_lens_count`/`out_of_lens_count` —
  разделение in-lens/out-of-lens (показывает, работают ли линзы, решение №7).
- `category` — модальная категория находок участника (атрибут отчётности).
- `quota_mode`/`quota_fallback_reason` — маркировка квотного выбора (FR-14;
  квотно-слепая ротация маркируется, не молчаливая оценка).

## Проекция strengths (`swarm_core.compute_swarm_strengths`, read-only)

- Ячейка — `(participant_id × role)`; `category` — атрибут наблюдения, НЕ
  ячейка формулы (FR-12).
- Decay: `w = 0.5^(k/8)`, `k = max_seq − review_seq` (harness
  `observation_weight`, half-life 8 наследуется).
- `n_eff` — число чистых наблюдений ячейки; `n_eff < 3` (`STRENGTHS_FLOOR`) →
  round-robin на назначении (ротация T-07 читает `n_eff` через
  `lens_history`, argmax-by-strengths отсутствует — RISK-06).
- Метрики score:
  - `accept_rate = Σw·unique_confirmed / Σw·(unique_confirmed + unique_unconfirmed)`;
  - `upheld_rate_as_author = Σw·upheld / Σw·(upheld + overruled)`;
  - `score = 0.6·accept_rate + 0.4·upheld_rate_as_author` (веса `SCORE_W_*`
    harness наследуются; при одном None — другая метрика, при обоих — 0).
- Отчётные метрики (в score НЕ входят):
  - `overrule_precision_as_attacker = Σw·attacks_confirmed_overrule / Σw·attacks_made`
    (качество атаки — отдельная ось от авторства, TD §5.7);
  - `coverage_nonunique = Σw·nonunique / Σw·(nonunique + nonunique_missed)`;
  - `precision = Σw·(unique_confirmed + nonunique) / Σw·всего выдвинутых`;
  - `fixed_rate = Σw·fixed / Σw·(fixed + partially + not_fixed)`;
  - `nit_count`, `in_lens_count`, `out_of_lens_count` — decay-взвешенные суммы.
- Пустой знаменатель → `None` (нет данных), не деление на ноль.

## Маркеры вне strength-формул

- `forced` (FR-11), `gate_pass` (FR-16/T-13): наблюдение исключается из
  проекции ЦЕЛИКОМ (TD §5.7 — назначение/роль не были свободным выбором
  модели; без подключения с дисконтом, в отличие от tainted консилиума).
- `auto_confirmed_overridden` (FR-06), `calibration_run` (TD §8.2): пишутся в
  observations, но не входы формул и НЕ основание исключения наблюдения.
  Калибровочные наблюдения обязаны питать статистику — иначе выборка смещена
  (FR-15: квота «калибрует и триаж, и статистику», RISK-07).

## Калибровочная квота (FR-15, TD §8.2, N = 4)

- `config.json → light_reviews_completed = c`; если
  `(c + 1) % calibration_every == 0` — ревью исполняется роем независимо от
  триажа (`swarm.calibration_due`, `triage` → `reason: calibration_quota`).
- `calibration_every = 4` засевается в `config.json` при первой записи track
  record и далее пересматривается правкой конфига, не кода (checkpoint
  пересмотра — после ≥ 20 лёгких ревью, решение Оркестратора с записью причины).
- Калибровочный прогон: `convene --calibration-run` → `calibration_run: true`
  в observations сессии.
- Счётчики: `reviews_completed` инкрементируется на запись track record
  (close/report полного роя); `light_reviews_completed` — на close лёгкого
  ревью (T-13, тот же `bump_counter`).

## История линз для ротации (`swarm_core.lens_history` → `assign_lenses`)

`convene` читает историю ячеек из `observations.jsonl`
(`swarm.load_lens_history`; strengths.json на чтении не доверяется —
наследуется F-04 консилиума):

- `lenses_used` — текущий цикл запрета повтора: различный суффикс ролей
  свободных (не forced/gate_pass) наблюдений по возрастанию `review_seq`
  (повтор роли = граница цикла: ротация не назначает линзу до исчерпания пула);
- `n_eff` — из проекции strengths; пустые ячейки (< 3) → round-robin.
