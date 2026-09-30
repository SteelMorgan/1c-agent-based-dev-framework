# Контракт адаптера консилиума — указатель

> **Каноническая локация контракта адаптера переехала (RVSW-01, T-14):**
> `review-harness/references/adapter-contract.md` — единый контракт harness
> для обоих инструментов (консилиум и рой): lifecycle
> `start/ask/status/close` + обязательный `sync`, IO-контракт, материализация
> diff в sandbox, канонические поля активности
> `last_activity_at`/`last_heartbeat_at` (FR-13), read-only граница,
> контрактные тесты. Этот файл сохраняет только консилиум-специфичные
> дополнения.

## Консилиум-специфичные дополнения к контракту harness

- Схема структурированного хода консилиума — fenced-блок
  ```consilium-structured: `references/transcript-schema.md`
  (`elements`/`new_findings`/`position_changes`/`borrowed`/
  `risk_checklist_responses`). Отсутствующий/битый блок не отвергает ход: ядро
  принимает его с пустыми `structured` и пишет предупреждение в transcript
  (консервативная семантика).
- Ядро консилиума дополнительно сохраняет `session_id` адаптера в
  `.consilium-sessions/<id>/participants/<participant>.json`.
- Текст хода консилиума в `--question`: вопрос консилиума + роль + дайджест
  модератора + инструкция формата (промпты — `references/consilium-prompts.md`).
- Классификация исходов вызова (ok/timeout/error) и retry-политика — общие,
  см. канонический контракт; запись `error` дополнительно идёт в transcript.
