# Контракт адаптера review-harness (RVSW-01, FR-01; наследует FR-07/TD консилиума)

> Каноническая локация контракта — этот файл. Переехал из
> `agent-consilium/references/adapter-contract.md` (T-14) с дополнениями
> RVSW-01: обязательный `sync`, канонизированные поля активности
> `last_activity_at`/`last_heartbeat_at`, материализация diff в sandbox.
> Консилиумная копия — указатель на этот документ.

Адаптер — тонкая обёртка над CLI модели, реализующая единый lifecycle.
Инструменты (консилиум, рой) общаются с участниками ТОЛЬКО через этот контракт
(обёртки — `scripts/adapter_contract.py`); новый [CLI+модель] подключается
одной записью в `adapters.yaml` + новым адаптером в `scripts/adapters/`,
без правок harness и инструментов (NFR-07).

## 1. Lifecycle (обязательные команды)

| Команда | Семантика |
| --- | --- |
| `start --question Q [--model M] [--review-id ID] [--timeout-sec N] [paths ...]` | Создаёт sandbox `.review-sandboxes/<review_id>/workspace`, копирует focused-paths (без paths — full context; ядра инструментов всегда передают focused-paths, NFR-05), выполняет первый вызов модели |
| `ask <review_id> --question Q [--timeout-sec N]` | Продолжает сохранённую сессию участника (resume): участник сохраняет контекст своей позиции между ходами |
| `status <review_id>` | JSON `{review_id, status, runtime, stats}`: heartbeat, pid, phase, progress counters |
| `close <review_id> [--keep-sandbox]` | Закрывает сессию и удаляет sandbox по умолчанию (парность start↔close) |
| `sync <review_id>` | **ОБЯЗАТЕЛЬНЫЙ элемент контракта (FR-01б)**: обновляет скопированные исходники/diff в sandbox участника до текущего состояния проекта. Необходим re-review фиксов (FR-09) и дельта-итерациям gate (rework → sync → повторный вызов). Реализован у всех трёх адаптеров |

Расширенный lifecycle существующих адаптеров (`debate`/`show`/`log`/`stats`)
остаётся доступен и не ломается.

## 2. IO-контракт

### Вход (подача материала)

- Участнику передаются focused-paths копии файлов (positional `paths` в
  `start`) — НЕ `--full-context`;
- текст хода — через `--question` (вопрос инструмента + роль + инструкция
  формата);
- схема дополнительных аргументов brief — по образцу существующих адаптеров
  (`--task`/`--goal`/`--requirements`/`--constraints`/`--primary-target`/
  `--changed-files`/`--open-concerns`/`--review-ask`);
- **материализация diff (FR-01к, FR-04)**: harness материализует diff
  ревью файлом внутри sandbox участника (`adapter_contract.materialize_diff()`,
  каноническое имя `review.diff`; для re-review — `fix.diff`). Участник читает
  diff как обычный файл в своём workspace, а не из промпта. Sandbox остаётся
  read-only для участника: запись выполняет сторона вызывающего — это канал
  доставки артефакта контракта, а не право участника на запись. Fail-closed:
  без `workspace_path` в meta участника — `RuntimeError`.

### Выход `start` (stdout)

Служебные строки в начале вывода (парсятся harness):

```
review_id: <id>
session_id: <session id CLI модели>
workspace: <абсолютный путь sandbox>
<статистическая строка>
<пустая строка>
<текст ответа участника>
```

### Сессия и resume

- `session_id` сессии адаптера хранится в `.review-sandboxes/<review_id>/review.json`
  (`session_id`, `last_response`, `workspace_path`);
- `ask` ОБЯЗАН продолжать именно сохранённую сессию (claude: `--resume`;
  codex: `codex exec resume <id>`; kimi: `-r <id>`, fallback `--session`).

### Маппинг ошибок и таймаута

| Исход вызова | Классификация | Действие |
| --- | --- | --- |
| exit 0 | ok | ход принимается |
| exit 1 + `runtime.json: phase=timeout` | timeout | участник `unresponsive` БЕЗ retry |
| иной exit 1 | error | ровно один retry → при повторе `unresponsive` |

Адаптер ОБЯЗАН при превышении `--timeout-sec` убить процесс CLI и зафиксировать
`runtime.json: {"state": "failed", "phase": "timeout"}` — это машиночитаемый
сигнал таймаута.

## 3. Канонические поля активности (FR-13)

Все адаптеры ОБЯЗАНЫ вести в `.review-sandboxes/<review_id>/runtime.json`
два поля активности (канонизируются контрактом; новый таймстамп не вводится):

| Поле | Семантика |
| --- | --- |
| `last_heartbeat_at` | «Процесс-адаптер жив и следит за CLI» — watcher-тик (каденс ~1 с) |
| `last_activity_at` | «CLI эмитит события» — последнее наблюденное событие модели |

Потребитель — `scripts/liveness.py` (классификация `active`/`quiet`/
`dead_watcher`, диагностика, не kill). Доступ к полям — только через
`adapter_contract.read_participant_activity()` (канонический читатель,
`ACTIVITY_FIELDS`).

## 4. Read-only граница (NFR-05)

- участник работает только с sandbox-копиями материалов; реальный проект
  менять запрещено;
- read-only инструкция обязательна в prompt/system-prompt адаптера;
- full-context копия (когда применяется) исключает `.git`, `.venv`,
  `.review-sandboxes`, `node_modules`, `__pycache__` и common build outputs;
- codex: read-only sandbox mode; claude: инструменты `Read,Grep,Glob,LS`;
- kimi: allowlist-флага у CLI нет; механическая граница — встроенный read-only
  agent profile через `--agent-file` (профиль встроен в адаптер и
  материализуется в каталог сессии участника; явный `--agent-file`
  переопределяет). Дополнительные контуры: sandbox-копии + read-only
  инструкция в prompt.

## 5. Структурированный ход

Механизм fenced-блоков — общий (`scripts/structured.py`): ответ участника
завершается fenced-блоком тега инструмента; отсутствующий/битый блок даёт
пустые structured + warning (консервативная семантика). **Схемы полей —
инструментов, не harness**: формат хода консилиума —
`agent-consilium/references/transcript-schema.md`
(```consilium-structured); схемы ходов роя —
`review-swarm/references/finding-schema.md` и `verdict-schemas.md`
(```swarm-structured, ```swarm-verdict и др.).

## 6. Контрактные тесты

`tests/contract/test_adapter_contract.py` — тесты библиотеки (переехали из
консилиума, NFR-04): схема аргументов, `session_id`/resume, маппинг ошибок и
таймаута, обязательный `sync`, канонизированные поля активности, материализация
diff в sandbox — для всех трёх CLI (stub-CLI: `tests/stubs/stub_cli.py`).
