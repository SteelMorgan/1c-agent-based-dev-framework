# Схема transcript.jsonl и структурированного хода (FR-04, TD 3.3)

Транспорт консилиума — append-only `.consilium-sessions/<session_id>/transcript.jsonl`.
P2P-обмена между участниками нет: всё идёт через модератора. Одна строка = одна запись.

## Запись

```json
{
  "seq": 17,
  "ts": "2026-07-28T...",
  "session_id": "cons-...",
  "phase": "B",
  "round": 2,
  "wave": "attack",
  "author": "claude-opus",
  "anon_id": "M2",
  "type": "attack",
  "refs": [9, 12],
  "content": "полный текст хода",
  "structured": {
    "elements": ["E1", "E4"],
    "new_findings": [{"id": "F-01", "text": "..."}],
    "position_changes": [{"element": "M1:E2", "action": "disagree", "refs": [9]}],
    "borrowed": [{"element": "E7", "source_ref": 12}]
  }
}
```

| Поле | Правило |
| --- | --- |
| `seq` | сквозной, монотонный с 1; ядро при append проверяет `seq = last+1`; перезапись запрещена (no-rewrite). Криптография не применяется (RISK-10 — принятый остаточный риск) |
| `type` | enum: `proposal / attack / response / digest / kill_decision / final_statement / synthesis / redteam_attack / confirmation / verdict / system / error` |
| `author` | в transcript всегда реальный id (traceability); записи модератора — `author: "moderator"`; модератору запрещены типы участников (proposal/attack/response/redteam_attack/confirmation) — ядро отклоняет (FR-05) |
| `anon_id` | заполняется для записей фазы B; используется только в выдаче участникам (bundles); mapping — `anon_map.json`, только в каталоге сессии |
| `refs` | ссылки на `seq` предыдущих записей, на которые опирается ход |

## Структурированный ход (`structured`)

Участник декларирует поля в fenced-блоке `consilium-structured` в конце ответа.

- `elements` — стабильные id элементов текущей модели участника (сессия адаптера сохраняется,
  участник помнит свои id между ходами). Чужие элементы адресуются как `<anon_id>:<element_id>`.
- `new_findings` — новые findings хода `[{"id": "F-01", "text": "..."}]`.
- `position_changes` — по пунктам: `agree / disagree / refine / withdraw` + `refs`.
- `borrowed` — `[{"element": "E7", "source_ref": <seq>}]`.
- `risk_checklist_responses` (CONS-03 E-4, обязательно для ходов фаз A/B/D:
  proposal/attack/response/redteam/confirmation; `final_statement` не покрывается) —
  ответы на риск-чеклист роли: `[{"item_id": "<id пункта реестра>", "verdict": "hit|clear|na",
  "note": "<текст>"}]`. Fail-closed: все применимые к типу хода (`applies_to`) пункты покрыты;
  `item_id` из каталога роли; verdict строго lowercase; `hit`/`na` требуют валидный note
  (непустой после trim, не «-», ≥3 символов); дубликат с конфликтующими verdict — отклонение.
  Невалидный ход отклоняется ДО записи в transcript; ядро выполняет ровно ОДИН контент-retry
  через `ask` той же сессии адаптера, повторная невалидность → unresponsive. `hit` с валидным
  note засчитывается как новый finding в `round_has_new_findings` (вход предиката; механика
  стоп-условий неизменна). Ответы ИСКЛЮЧАЮТСЯ из payload `render_bundle` участников.

### Предикаты фазы B (FR-02)

- раунд имеет новые findings ⟺ ∃ запись раунда с `structured.new_findings ≠ ∅`;
- раунд имеет смену позиций ⟺ ∃ запись раунда с `structured.position_changes ≠ ∅`.

Оценка модератора в предикатах не участвует.

### Антинакрутка borrowed (FR-04)

`borrowed[].source_ref` засчитывается в kill-прокси ТОЛЬКО если ядро разрешает ссылку:
`source_ref` — существующий `seq` записи ДРУГОГО автора, содержащей объявление
заимствованного элемента в `structured.elements`. Неразрешимая ссылка → borrowed
отбрасывается с `system`-записью в transcript.

### Upheld critique (kill tie-break, track record)

Атака на элемент `X` модели `m` (чужой `disagree` по `m:X`) считается upheld, если
ближайший последующий `position_changes` автора `m` по `X` имеет `action ∈ {agree, withdraw}`.

## Консервативная семантика парсинга (TD 7.4)

Отсутствующий/битый structured-блок → ход принимается с пустыми `structured` +
`system`-запись-предупреждение. Предикаты FR-02 тогда считают ход «без findings/смен» —
это давит стоп-условия в сторону завершения (безопасная сторона).

## Служебные записи CONS-02

- `type: system` с черновиком дайджеста (OPT-2): экстрактивный черновик после волны,
  полезная нагрузка в fenced-блоках `participant-extract`; адресован модератору,
  в wave-bundle участников не включается.
- `type: system` «фаза D пропущена» (OPT-3): причина пропуска (модель-источник,
  число элементов, значения предиката) — фиксация условной фазы D.

## Записи human-critic режима (CONS-06, опционально)

При `convene --human-critic` в transcript допустимы ходы с внутренним id автора
`human-critic` (session-scoped сущность уровня transcript; в `session.participants`
не добавляется, в реестр adapters.yaml не входит):

- типы — только `attack` (фаза B) и `redteam_attack` (фаза D), максимум 1 ход на
  волну; подача — файлом модератора `round --human-turn-file` с обязательной
  аттестацией `human_approved: true` (audit, не access control);
- `anon_id` — из общего пространства `create_anon_map` (presentation-id);
  membership-инвариант: participant-visible запись с author вне anon_map →
  ProtocolError (fail-closed);
- human-записи исключены из кворума/family-разнообразия, track-record/strengths/
  observations, kill-кандидатов, стоп-предикатов nf/pc и upheld/metrics
  (политика 2: влияние на kill только через withdraw модели);
- `type: system` — события режима: вход в awaiting_human, принятие хода с
  аттестацией, исчерпание wait-cap (awaiting_moderator_decision) и каждое
  решение модератора (`--human-continue-wait` / `--human-skip-wave` /
  `--human-withdraw`), обязательность фазы D после human-хода в B (E8);
- пауза ожидания persisted в `session.json`: `wall_clock.paused_sec` (НЕ сдвиг
  started_at); `status` экспонирует `awaiting_human`, `paused_total_sec`,
  `real_elapsed_sec`, `active_elapsed_sec`.
