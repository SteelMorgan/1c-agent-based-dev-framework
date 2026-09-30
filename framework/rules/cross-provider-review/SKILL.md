---
name: cross-provider-review
description: Scope-aware review через Reviewer-controller и review-swarm для артефактов, влияющих на приёмку.
alwaysApply: false
---

# Cross-provider review

Применяй правило к артефактам, которые меняют поведение конфигурации, контракт, framework governance или доказательство приёмки.

## Термины и ответственность

- `agent-consilium` — advisory-обсуждение material trade-offs. Он не является gate.
- `review-swarm` без `--gate` — advisory findings.
- `review-swarm --gate acceptance|completion` — blocking gate.
- Blocking gate закрывает только eligible participant другого семейства
  относительно `--caller`; literal self-review и caller-family review запрещены.
- Reviewer-controller формирует focused evidence package, запускает gate и хранит provenance; он не исправляет артефакт.
- Оркестратор маршрутизирует rework и принимает управленческое решение.

## Scope-aware acceptance

| Scope | Обязательный пакет перед одним acceptance gate |
|---|---|
| `spec`, `arch` | durable Consilium verdict или evidence `CONSILIUM_NOT_APPLICABLE`; checklist; proposed artifact; разрешимые `artifact.md:<line>` для faithful materialization, структуры и MUST-traceability |
| `bdd`, `bdd-steps`, `tests`, `tester` | artifact, scope checklist и обязательные для фазы исполнимые результаты |
| `code` | revision-bound diff изменённых paths/hunks, сырой syntax output, caller map изменённых экспортных методов |
| `debug` | debug report, revision/debug-session identity, marker-search, DAP detach/targets status, cleanup временных артефактов и данных |

После полного Consilium для `spec/arch` не запускай повторную архитектурную делиберацию и отдельный standalone Reviewer. Gate проверяет материализацию и доказательства, а не заново выбирает решение. Standalone reviewer-context создаётся только при доказанном техническом fallback.

Для `code` Reviewer-controller остаётся evidence producer: он получает diff, syntax output и caller map до запуска внешнего gate. Удалять этот producer можно только после доказанного deterministic replacement.

## Минимизация внешнего пакета

1. Всегда используй focused paths; full context допускается только с явным обоснованием и разрешением владельца данных.
2. Caller map и full-repository marker search передаются как `path:line + symbol/marker`, без тел кода, соседних строк и полного списка нерелевантных путей.
3. Diff ограничивается изменёнными paths/hunks.
4. DAP evidence содержит статусы и session identity, но не connection strings, tokens, secrets или тестовые данные.
5. Отсутствие обязательного evidence, revision mismatch или превышение focused-package policy блокируют gate.

## Цикл

1. До запуска проверь identity и model floor, затем закрепи выбранного
   cross-family participant через `--gate-reviewer`; квотный автовыбор не
   заменяет это решение. Запусти один `review-swarm --gate acceptance` для
   артефакта фазы, дождись terminal gate outcome и наблюдай долгий вызов через
   `status`/heartbeat. Пока blocking gate выполняется, завис или содержит
   неразобранный BLOCK, downstream-фаза не стартует.
2. Разбери каждую находку по фактическим артефактам и зафиксируй disposition: `agree`, `partial`, `disagree`, `withdrawn`, `out_of_scope`.
3. После существенной правки запроси delta review. Неразрешённый BLOCK не принимай; после трёх итераций эскалируй с обеими позициями и evidence.
4. Перед claim завершения Оркестратор запускает отдельный `--gate completion` по final evidence package. Завершение допустимо только при `APPROVE_COMPLETION`, явном user override или документированной эскалации после трёх итераций.
5. Всегда закрой сессию. В trace сохрани session id, reviewer/family, exact model/effort, revision, findings/dispositions, iterations и cleanup status.

## Model floor и fallback

Default controller Reviewer — Opus 5 Medium. Фактический blocking reviewer должен быть не слабее фактического автора по capability и effort; при авторе High Reviewer повышается минимум до High.

Findings, disagreement, `BLOCK_COMPLETION`, cost/latency и exhausted iterations не являются технической недоступностью. Standalone fallback разрешается только reviewer-controller по machine-readable allowlist и после успешного cleanup; неизвестная причина даёт BLOCK.

## Scope применения

Review обязателен для BSL, metadata/XML, запросов, ролей/RLS, интеграций, фоновых заданий, миграций, spec/design/task breakdown, Vanessa/YaxUnit, final report и изменений framework rules/skills/workflows/scripts. Локальный рефакторинг без изменения поведения может быть освобождён от blocking gate только с явной записью в review trace.

---
depends_on:
  - framework/skills/agent-process/reviewer-controller/SKILL.md
  - framework/skills/tool-usage/review/review-swarm/SKILL.md
  - framework/skills/tool-usage/review/agent-consilium/SKILL.md
---
