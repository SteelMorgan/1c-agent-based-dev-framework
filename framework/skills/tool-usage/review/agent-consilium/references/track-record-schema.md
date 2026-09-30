# Схема track record консилиума — указатель + поля наблюдений

> **Каноническая локация общей схемы track record переехала (RVSW-01, T-14):**
> `review-harness/references/track-record-schema.md` — общая для обоих
> инструментов схема: storage_dir-параметризация, формулы decay (half-life 8),
> floor `n_eff ≥ 3`, веса score 0.6/0.4, exploration-бюджет, запрет
> самодекларации strengths. Этот файл сохраняет консилиум-специфичный состав
> полей наблюдения.

Durable-хранилище `.consilium-track-record/` на уровне репозитория: переживает
`close` любой сессии и исключено из cleanup-checkpoint (FR-10).

```
.consilium-track-record/
├── observations.jsonl   # по одной записи на (участник × роль × консилиум)
├── strengths.json       # СГЕНЕРИРОВАННАЯ read-only проекция (ручное редактирование запрещено)
└── config.json          # {"consiliums_completed": N} — счётчик для exploration-бюджета
```

## Запись наблюдения

Пишется ядром на `close` (и на аварийном завершении) из transcript:

```json
{
  "consilium_id": "cons-...", "consilium_seq": 7, "date": "2026-07-28",
  "participant_id": "claude-opus", "family": "claude",
  "role": "architecture",
  "moderator_id": "primary",
  "moderator_is_participant": false,
  "findings_accepted": 3, "findings_withdrawn": 1,
  "critiques_upheld": 2, "critiques_overruled": 1,
  "model_killed": false, "unresponsive_events": 0,
  "outcome": "verdict",
  "phase_d_skipped": false, "phase_d_saved_invocations": 0
}
```

- `phase_d_skipped` / `phase_d_saved_invocations` (CONS-02 OPT-3, метрика RISK-D06):
  факт пропуска фазы D и число сэкономленных вызовов; пишутся в КАЖДОЕ наблюдение
  сессии независимо от исхода предиката (skip=true/false) — измеряемая гипотеза
  частоты срабатывания условной фазы D.
- `domain` (CONS-03 E-2): id доменного пакета сессии (default `architecture`);
  наблюдения старых консилиумов без поля трактуются как `architecture`.
- `checklist_stats: {hit, clear, na}` (CONS-03 E-4): агрегат verdict'ов риск-чеклиста
  по ПРИНЯТЫМ ходам участника за сессию (отклонённые ходы не попадают в transcript и
  не считаются; `final_statement` исключён). Источник данных для пересмотра состава
  пакетов (RISK-E02); ключ strengths и схема проекции не меняются.

- `consilium_seq` — порядковый номер консилиума (база recency decay);
- `moderator_is_participant = true`, когда `moderator_id` совпадает с одним из участников
  (primary agent высказывался через свой адаптер, FR-05) — маркер сегрегации TBD-02;
- `outcome`: `verdict` | `closed_without_verdict` | `terminated:<причина>`.

## Проекция strengths

Формулы — общие, см. `review-harness/references/track-record-schema.md`
(decay `w = 0.5^(k/8)`, сегрегация tainted с дисконтом ×0.5, `n_eff`,
`accept_rate`/`upheld_rate`, `score = 0.6·accept + 0.4·upheld`, floor
`n_eff ≥ 3` → fallback round-robin, exploration каждый 4-й консилиум по
`config.json → consiliums_completed`). Консилиумная проекция —
`review-harness/scripts/track_record.py: compute_strengths` с
`seq_field="consilium_seq"`.
