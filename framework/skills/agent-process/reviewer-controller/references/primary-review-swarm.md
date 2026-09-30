# Primary-ветка Reviewer через review-swarm

Reviewer — controller/provenance owner проверки. Он не заменяет автора артефакта и не исправляет findings.

## Общий протокол

1. Получи `review_scope`, artifact paths, исходную задачу/spec/design, revision identity и checklist. Не принимай пакет без точного scope.
2. Подготовь focused evidence package. Не передавай полный workspace; секреты и connection strings удаляются.
3. Запусти `review-swarm --gate acceptance` для фазового артефакта. Swarm без `--gate` даёт только advisory findings и не закрывает фазу.
4. Сопоставь findings с фактическими артефактами и зафиксируй disposition. После существенной правки запроси delta review; общий лимит — три BLOCK-итерации.
5. Сохрани session id, reviewer/family, exact model/effort, revision, findings/dispositions и cleanup status. Всегда выполни `close`.
6. Итог `BLOCK_COMPLETION` или содержательный BLOCK возвращается автору через Оркестратора и никогда не активирует standalone fallback.

## Evidence по scope

`scope checklist` не является содержимым fallback-ветки. Его канонические
источники primary-ветки:

| Scope | Обязательный источник критериев |
|---|---|
| `spec` | `spec-standard` + MUST/test-plan/runtime-layer/границы/RFC 2119/intent-Gherkin |
| `arch` | `technical-design-standard` + цели/модули/контракты/metadata/ADR/MUST traceability/Task Breakdown |
| `bdd` | `vanessa-scenario-policy` + полнота intent-сценариев, Gherkin и допустимое размещение |
| `bdd-steps` | `vanessa-scenario-policy` + Red-gate, отсутствие моков/бизнес-логики в шагах и reuse-first |
| `tests`, `tester` | `test-writing`, `tdd-policy`, `vanessa-test-isolation-policy` + MUST coverage и zero-residue evidence |
| `code` | `coding-standards`, `syntax-checking`, `code-navigation` + diff/caller-map/API/spec-design conformance |
| `debug` | `self-recovery-limits`, `agent-debug`, `dap-bsl-debugger` + trace evidence, fix limits и cleanup |

Пакет без применимого checklist-source — BLOCK до `convene`; подменять его
чтением `fallback-standalone.md` запрещено.

- `spec` / `arch`: durable Consilium verdict, scope checklist, proposed artifact и разрешимые ссылки `artifact.md:<line>` для `verdict → artifact`, структуры и трассировки MUST. Повторную архитектурную делиберацию не проводить.
- `bdd`, `bdd-steps`, `tests`, `tester`: artifact + scope checklist + результаты исполнимых проверок, если они уже обязательны для этой фазы.
- `code`: revision-bound diff только изменённых paths/hunks, сырой syntax output и caller map изменённых экспортных методов. Caller map наружу передаётся как `path:line + symbol`, без тел и соседних строк.
- `debug`: `debug-report.md`, revision/debug-session identity, marker-search как `path:line + marker`, DAP detach/targets status и cleanup временных артефактов/данных без секретов.

Отсутствующий evidence, несовпадение revision или нарушение focused-package policy блокируют запуск/gate.

## Model floor

`Opus 5 Medium` — default controller Reviewer. До `convene` Оркестратор
сопоставляет exact model/effort автора с канонической матрицей, выбирает
зарегистрированного eligible participant не ниже floor и обязательно передаёт
его через `--gate-reviewer`. Фактический blocking gate reviewer должен быть не
слабее фактического автора по capability и effort; при авторе High Reviewer
повышается минимум до High. Если реестр/адаптер не позволяет доказать и
закрепить exact tuple, результат — `BLOCKED_REVIEWER_FLOOR`, а не квотный
автовыбор и не standalone fallback. Effort override Оркестратора разрешён и
трассируется.

`--gate completion` запускается только Оркестратором по final evidence package; фазовый Reviewer сам не объявляет завершение всей задачи.
