# Схемы вердиктов туров 2–4, арбитража и re-review (FR-07..FR-09, TD §5.2–5.5)

Все обмены роя — structured (FR-03): каждый ход — fenced-блок своего тега,
свободный текст только в `rationale`. Невалидный ход отклоняется ядром
fail-closed (`MoveRejected` с машинным `reason`); ровно один retry — на уровне
CLI (NFR-01). Предикаты схем §5.2–5.3 реализованы чистыми функциями
`scripts/swarm_core.py` (T-09); схемы §5.4–5.5 задокументированы здесь, их
исполнение — арбитраж-вызовы (T-10) и re-review (T-12); схема §5.9 —
gate-вердикт лёгкого тарифа и двойной роли (T-13).

## 5.2 Вердикт валидации, туры 2 и 4 (FR-07, AC-07)

Fenced-тег: ` ```swarm-verdict `.

```json
{
  "finding_id": "F-001",
  "verdict": "upheld | overruled | reclassify | uncertain",
  "reclassify": {"category": "...", "severity": "P2"},
  "evidence": {"path": "services/x/y.py", "line": 123, "quote": "дословный фрагмент"},
  "rationale": "..."
}
```

- `reclassify` — обязателен только при `verdict = reclassify` (хотя бы одно из
  полей `category`/`severity`); значения канонизируются таксономией TD §5.1.
- `evidence` обязателен всегда; ядро проверяет резолвимость `path`/`line` в
  проверяемом наборе и **новизну evidence** в треде находки: повтор
  `(path, line, нормализованный quote)` отклоняется (`stale_evidence`) —
  стоп-правило «каждый ход несёт новое evidence» (FR-07, аналог
  stalemate-предиката). Нормализация quote — схлопывание пробельных
  последовательностей: переформатирование не обходит предикат.
- Голосуют соседние модели (не автор); ровно один вотум на участника в волне.
- Анонимность автора: в payload атакующего передаётся находка с `anon_id`
  вместо `author_id` (`tour_payload`); `anon_map` стабилен внутри сессии,
  хранится в каталоге сессии, доступен только ядру/Оркестратору (наследуется
  честная граница анонимизации CONS-01 RISK-07).
- Все вердикты тура 2 `upheld` → находка подтверждена досрочно, туры 3–4 не
  планируются (экономия вызовов, NFR-02).

## 5.3 Ответ автора, тур 3 (FR-07)

Fenced-тег: ` ```swarm-author-response `. Ровно один ход на находку — второй
отклоняется ядром (`move_limit`).

```json
{
  "finding_id": "F-001",
  "response": "maintain | withdraw | accept_reclassify",
  "counter_evidence": {"path": "...", "line": 120, "quote": "..."},
  "rationale": "..."
}
```

- `counter_evidence` обязателен при `maintain` и подчинён тому же предикату
  новизны; при `withdraw`/`accept_reclassify` — опционален.
- Автор видит агрегированные возражения под `anon_id` голосующих
  (`objections_payload`) — реальные id не утекают.
- `withdraw` → статус `withdrawn`; `accept_reclassify` → `reclassified`
  (тред закрывается без тура 4); `maintain` → финальный вотум (тур 4).

## Стоп-правила и статусы треда (решение №5, TD §6.3.5–6.3.6)

- **Потолок 2 обмена** после первичной атаки на находку: ответ автора —
  обмен 1, финальный вотум — обмен 2; третий обмен невозможен (fail-closed,
  `thread_closed`).
- **Запрет новых находок в турах 2–4**: structured-блок `swarm-structured` с
  находками внутри ответа туров 2–4 в тред не принимается; находки
  маршрутизируются в общий пул (сырые записи для `validate_findings`,
  нумерация — сквозная пула). Только тур 1 и re-review порождают находки.
- **Статусы находки по итогу треда**: `confirmed` (все `upheld` тура 2 или
  тура 4), `withdrawn`, `reclassified`, `contested` — неснятое disagreement
  после потолка; contested severity ≥ major (P1–P2) — на арбитраж Оркестратора
  (FR-08), `report` без такого арбитража отклоняется (TD §6.3.7).

## 5.4 Арбитраж Оркестратора (FR-08, AC-08) — исполнение в T-10

Аргумент стороны (ядро собирает из треда; при необходимости — один
дополнительный structured-вызов стороне):

```json
{"finding_id": "F-001", "side": "author | attacker", "claim": "...", "evidence": {"path": "...", "line": 0, "quote": "..."}}
```

Решение Оркестратора (подаётся через `swarm.py arbitrate --decision-file`):

```json
{
  "finding_id": "F-001",
  "decision": "upheld | overruled | reclassified",
  "reclassified": {"category": "...", "severity": "..."},
  "evidence_quote": "дословная цитата спорного места кода из файла location.path",
  "location": {"path": "...", "line": 0},
  "rationale": "..."
}
```

Ядро fail-closed валидирует: непустой `evidence_quote`; `location` резолвится
в реальный файл проверяемого набора; `decision` согласована с тредом (при
`reclassified` заполнен блок). **`evidence_quote` обязан быть дословной
цитатой из файла, указанного в `location.path` решения** (FR-08 «решение по
коду»): цитата из соседнего/иного файла отклоняется fail-closed — арбитраж
обязан опираться на спорное место кода, а не на косвенный контекст. Решение
финальное; эскалация к человеку — при material-разногласии (наследуемая
семантика).

## 5.5 Re-review вердикт (FR-09, AC-09) — исполнение в T-12

```json
{
  "finding_id": "F-001",
  "verdict": "fixed | partially | not_fixed | introduced_new_issue",
  "new_issue": { "...": "полная находка по схеме finding-schema.md" },
  "evidence": {"path": "...", "line": 0, "quote": "..."},
  "rationale": "..."
}
```

- Вход: исходная находка + diff исправления через `sync_participant()`
  контракта harness (новая focused-сессия того же адаптера автора; старая
  сессия не сохраняется — retention не нарушается).
- `new_issue` обязателен при `introduced_new_issue`; новая находка уходит в
  общий пул (не в тред) — то же правило маршрутизации, что и для туров 2–4.
- Конфликт с позицией разработчика → арбитраж §5.4.

## 5.9 Gate-вердикт (FR-16, AC-16/AC-24, TD §7) — исполнение в T-13

Fenced-тег: ` ```swarm-gate-verdict `. Наследуемая gate-семантика правила
`cross-provider-review.md`: отдельный structured-вызов gate-ревьюеру
(enabled/healthy, не exact объявленный caller, `gate_legal`) **ПОСЛЕ** репорта и
диспозиций Оркестратора —
`swarm.py gate-verdict`, ≤ 3 итераций review/rework/delta (дельта-итерация:
rework → `sync` sandbox → повторный вызов), далее эскалация пользователю с
обеими позициями и evidence (Hard Rule 16). Два режима (`GATE_MODES`).

### Режим `completion` (finalization gate, блокирующий)

```json
{
  "decision": "APPROVE_COMPLETION | BLOCK_COMPLETION",
  "findings": [{"id": "F-01", "severity": "BLOCK | WARN | INFO",
                "claim": "что не так", "evidence": "file:line / цитата"}],
  "rationale": "...",
  "escalation_needed": false
}
```

- `decision` строго из `GATE_COMPLETION_DECISIONS`; `BLOCK_COMPLETION`
  блокирует completion (Hard Rule 15): Оркестратор не объявляет задачу
  завершённой без `APPROVE_COMPLETION`, user override или зафиксированной
  эскалации после 3 итераций.
- `escalation_needed: true` — только при material-разногласии на последней
  итерации.
- Каноническая форма ядра: `{decision, findings, rationale,
  escalation_needed}`; промпт — `final-orchestrator-completion-review-prompt.md`.

### Режим `acceptance` (acceptance-bound review)

```json
{
  "verdict": "accept | conditional_accept | reject | re-review",
  "positions": [{"finding_id": "F-001",
                 "position": "agree | partial | disagree | withdrawn"}],
  "rationale": "..."
}
```

- `verdict` строго из `GATE_ACCEPTANCE_VERDICTS`; `accept` — положительный
  исход (терминальный `approved`); `reject`/`re-review` — разногласие →
  rework и дельта-итерация. `conditional_accept` (F-007, E2E-02) — НЕ
  положительный исход: промежуточный статус `conditional`, терминальный
  `approved` выставляет только явное решение Оркестратора
  (`gate-verdict --conditional-decision confirm|reject`).
- `positions` — позиции ревьюера по находкам сессии, где диспозиция
  Оркестратора расходится с его оценкой. Ядро fail-closed проверяет:
  `finding_id` — только известные находки сессии (`unknown_finding`),
  `position` — только из `GATE_POSITIONS` (`invalid_position`).
  `out_of_scope` — диспозиция Оркестратора, НЕ позиция ревьюера.
- Каноническая форма ядра: `{verdict, positions, rationale}`.

Общие правила: схема строгая, fail-closed — невалидный ход → ровно один retry
→ участник `unresponsive`; вердикт gate-ревьюера и его находки помечаются
`gate_pass` и выводятся из advisory-статистики track record (двойная роль,
AC-24); факт и форма gate-прохода фиксируются в review trace репорта (секция
«Gate-проход»). Предикаты — `swarm_core.parse_gate_verdict_move` /
`validate_gate_verdict`; статусы `approved | blocked | escalated` —
`swarm.resolve_gate_status` (`gate_verdict_positive`).
