# Схема track record и проекции strengths — общая (RVSW-01, FR-01е, FR-12)

> Каноническая локация общей схемы — этот файл. Переехала из
> `agent-consilium/references/track-record-schema.md` (T-14) с
> параметризацией под два инструмента: слой хранения harness
> (`scripts/track_record.py`) принимает `storage_dir`, у инструментов разные
> durable-каталоги и разные счётчики сессий.

Track record — durable-хранилище статистики на уровне репозитория: переживает
`close` любой сессии и исключено из cleanup-checkpoint. Содержимое —
runtime-данные, в git не коммитится.

## Каталоги инструментов (storage_dir-параметризация, FR-01е)

| Инструмент | `storage_dir` | Счётчик сессий (`config.json`) | Поле seq |
| --- | --- | --- | --- |
| Консилиум | `.consilium-track-record/` | `consiliums_completed` | `consilium_seq` |
| Рой | `.swarm-track-record/` | `reviews_completed`, `light_reviews_completed`, `calibration_every` | `review_seq` |

Структура каталога общая:

```
<storage_dir>/
├── observations.jsonl   # источник истины, append-only
├── strengths.json       # СГЕНЕРИРОВАННАЯ read-only проекция (ручное редактирование запрещено)
└── config.json          # счётчики инструмента
```

Источник истины — только `observations.jsonl`: при назначении ролей ядра НЕ
читают `strengths.json`, а каждый раз пересчитывают проекцию из observations;
подмена файла не влияет на назначение (наследуется F-04 консилиума).

## Observation

По одной записи на (участник × роль × сессия); пишется ядром инструмента на
закрытии/репорте сессии. Общие поля конверта:

- `<seq_field>` — порядковый номер сессии инструмента (база recency decay);
- `date` — дата записи;
- `participant_id`, `family`, `role` — ячейка проекции `(participant_id, role)`;
- счётчики исходов — специфичны для протокола инструмента (сборка из состояния
  сессии — ядро инструмента, не harness).

Составы полей наблюдений инструментов:

- консилиум — `agent-consilium/references/track-record-schema.md`
  (наследуемые поля: `findings_accepted/withdrawn`, `critiques_upheld/
  overruled`, `moderator_is_participant`, `outcome`, домен, checklist_stats);
- рой — `review-swarm/references/track-record-swarm.md` (поля FR-12:
  уникальные/неуникальные находки, атаки, fixed/partially/not_fixed,
  nit/in-lens/out-of-lens, маркеры `forced`/`gate_pass`/`calibration_run`).

## Формулы проекции strengths (наследуются CONS-01, решение TBD-02)

Для ячейки (participant `p`, role `r`):

1. **Вес наблюдения** (recency decay): `w_i = 0.5^(k_i / 8)`, где `k_i` —
   сколько сессий назад было наблюдение (half-life 8 сессий,
   `STRENGTHS_HALF_LIFE`).
2. **Сегрегация tainted-наблюдений** (защита измерителя): наблюдения,
     где выбор/роль не были свободным решением модели (у консилиума —
     `moderator_is_participant = true`), исключаются из проекции, ПОКА «чистых»
     наблюдений ≥ 3; иначе подключаются с дисконтом ×0.5
     (`TAINTED_DISCOUNT`). Эффективный объём: `n_eff = n_clean + 0.5 × n_tainted`.
     У роя сегрегация строже: наблюдения с маркерами `forced`/`gate_pass`
     исключаются ЦЕЛИКОМ (см. `review-swarm/references/track-record-swarm.md`).
3. **Метрики**: `accept_rate = Σ w·accepted / Σ w·(accepted+withdrawn)`;
   `upheld_rate = Σ w·upheld / Σ w·(upheld+overruled)`. Пустой знаменатель →
   компонента исключается (None), не деление на ноль.
4. **Score**: `score(p, r) = 0.6 · accept_rate + 0.4 · upheld_rate`
   (`SCORE_W_ACCEPT`/`SCORE_W_UPHELD`).
5. **Floor**: проекция применяется к назначению роли только при `n_eff ≥ 3`
   (`STRENGTHS_FLOOR`); иначе — fallback round-robin с запретом повторной роли.
6. **Exploration-бюджет**: `EXPLORATION_EVERY = 4` — каждая 4-я сессия
   инструмента назначает роли round-robin вразрез статистике (у консилиума —
   по счётчику `consiliums_completed`; у роя exploration — дефолт сбалансированной
   ротации линз до набора n_eff, FR-11).

`strengths.json` регенерируется ядром после каждой записи наблюдения
(`track_record.regenerate_strengths(storage_dir, seq_field=...)`; формат ключа
— `«pid|role»`); ручное редактирование запрещено (самодекларация strengths
запрещена — поле `strengths` в реестре `adapters.yaml` отклоняется fail-closed,
FR-02).
