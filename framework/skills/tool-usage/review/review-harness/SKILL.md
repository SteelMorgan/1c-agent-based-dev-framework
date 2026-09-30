---
name: review-harness
description: "Shared harness library for multi-model review tools (consilium, review-swarm): CLI adapters contract start/ask/fork/status/close/sync, native fork lineage, sandbox-lifecycle, structured-parsing, unified adapters registry v3, rotation/domains, track record, heartbeat/progress, quota-aware selection. Library, not a user-facing skill."
capabilities: agent-governance,multi-model-review,cross-provider,harness
---

# Review Harness — общая библиотека транспорта и учёта (RVSW-01, FR-01)

Это **библиотека, а не инструмент и не playbook вызывающего**. Harness — общий
слой переиспользуемого транспорта и учёта для инструментов мультимодельного
ревью; у него нет собственного CLI для вызывающего агента и нет протокола.
Потребители — ровно два инструмента:

- `agent-consilium` (`scripts/consilium.py` / `consilium_core.py`) —
  фазы A–E, digest-gates, вердикт модератора;
- `review-swarm` (`scripts/swarm.py` / `swarm_core.py`) — дивергентное
  код-ревью роем, туры 1–4, лёгкий тариф и gate-семантика.

Всё, что знает о фазах консилиума или турах роя, живёт в ядрах инструментов.
Протоколы инструментов документируются в их SKILL.md, не здесь.

## Правило границы (FR-01, AC-01)

> В библиотеке — всё, что **не знает о фазах/турах**; в инструментах — только
> протокол. Любая логика, специфичная для фаз A–E консилиума или туров 1–4
> роя, в harness **запрещена**.

Правило проверяется исполняемыми тестами `tests/unit/test_boundary.py`:

- **HU-B01** — скан импортов `scripts/**`: harness не импортирует модули
  инструментов (`consilium`, `consilium_core`, `swarm`, `swarm_core`);
- **HU-B02** — поиск запрещённых идентификаторов фаз/туров
  (`phase_a..e`, `tour1..4`, `convene`, `stalemate`, `kill_candidate` и пр.)
  в исходниках harness; исключения — только через явный whitelist с
  комментарием-указателем, неиспользованные записи whitelist — ошибка.

Практические следствия границы:

- `structured.py` знает только механизм fenced-блоков; **схемы полей ходов**
  (findings/verdicts/elements/…) определяются инструментами и передаются
  параметрами (`fence_tag`, `empty_schema`);
- `progress.py` не знает имён чекпоинтов инструментов — допустимое множество
  (`allowed_checkpoints`) передаёт вызывающая сторона, harness валидирует
  принадлежность fail-closed;
- `domains.py` не расширяется метками туров: `CHECKLIST_WAVE_TYPES` —
  разделяемый словарь `applies_to`; инструмент передаёт свой enum через
  `validate_domains(..., wave_types=...)` (рой добавляет метку `tour1`);
- `track_record.py` не знает, что записывать: сборка observation из состояния
  сессии — протокол инструмента и остаётся в ядрах; harness даёт формулы
  decay/проекции и слой хранения, параметризованный `storage_dir`;
- gate-адаптеры (`scripts/adapters/claude_opus_review.py`, `codex_review.py`)
  читают gate-промпт из `review-swarm/references/review-prompt.md` — reference
  исполнителя gate и носитель gate-семантики (RVSW-01 TD §3.2); это осознанная
  точка связности harness → инструмент, зафиксированная здесь явно. При
  отсутствии файла адаптер молчаливо переключается на встроенный
  `FALLBACK_REVIEW_PROMPT`/`FALLBACK_REVIEW_SYSTEM_PROMPT`: переименование или
  перенос reference равносильно незаметной замене gate-промпта заглушкой.
  Зависимость проверяется исполняемо — contract-тест HC-13
  (`tests/contract/test_adapter_contract.py`): кандидат обязан существовать,
  а загруженный промпт не должен совпадать с fallback.

## Состав библиотеки

```
review-harness/
├── adapters.yaml            # единый реестр адаптеров, схема v3 (FR-02)
├── domains.yaml             # доменные пакеты ролей/линз/риск-чеклистов
├── scripts/
│   ├── adapter_contract.py  # единственное место, знающее о subprocess
│   ├── registry.py          # парсер/валидатор реестра v3 (stdlib-only)
│   ├── structured.py        # fenced-block parsing, анонимизация, оценка токенов
│   ├── domains.py           # парсер/валидатор domains.yaml, чеклисты
│   ├── track_record.py      # decay/strengths + хранение (storage_dir)
│   ├── liveness.py          # классификация «думает/повисла» (FR-13)
│   ├── progress.py          # progress.jsonl — семантические чекпоинты (FR-13)
│   ├── quota.py             # квотный выбор адаптера (FR-14)
│   └── adapters/
│       ├── claude_opus_review.py
│       ├── codex_review.py
│       └── kimi_review.py
├── references/
│   ├── adapter-contract.md      # канонический контракт адаптера
│   └── track-record-schema.md   # каноническая схема track record
└── tests/ (unit/ + contract/)
```

### `scripts/adapter_contract.py` — контракт адаптера и волны

Единственное место, знающее о subprocess: запуск адаптеров, парсинг stdout,
маппинг таймаута/ошибок, retry-политика, параллельные волны.

- Обёртки контракта: `start_participant()`, `ask_participant()`,
  `fork_participant()`, `read_participant_status()`, `close_participant()`,
  **`sync_participant()`**
  (обязательный элемент контракта, FR-01б: доставка обновлённых исходников/diff
  в sandbox участника — необходим re-review фиксов, FR-09).
- `materialize_diff()` — материализация diff файлом (`review.diff`) внутри
  sandbox участника (FR-01к, FR-04): участник читает diff как обычный файл,
  а не из промпта. Fail-closed без `workspace_path` в meta и при
  `workspace_path` вне `.review-sandboxes/<review_id>/` (R-Final F-05).
- `read_participant_activity()` — канонический доступ к полям активности
  `last_activity_at` / `last_heartbeat_at` (FR-13; новый таймстамп не
  вводится, оба поля обязательны у всех адаптеров).
- `probe_fork_capability()` проверяет runtime capability отдельно от декларации
  реестра. `fork_participant()` принимает обязательные неизменяемые
  `operation_id`, `snapshot_digest` и канонический parent
  `provider_checkpoint_id`; логический digest snapshot никогда не подменяет
  provider cursor. Успех требует нового child session, полного provider lineage,
  `provider_turn_id` и явного `prompt_consumed`. Synthetic `start`/prompt replay
  как fallback запрещён.
- Неуспешный fork и успешный subprocess с повреждённым stdout проходят
  reconciliation ожидаемого `child_review_id`; orphan child закрывается только
  при совпадении parent/operation lineage. Произвольная identity из stdout не
  используется как цель cleanup.
  Канонический postcondition close — не отсутствие каталога, а lock-only
  tombstone: каталог содержит ровно стабильный regular `invocation.lock`.
  Повтор deterministic fork может переиспользовать только такой tombstone через
  canonical prepare; каталог с meta/runtime/workspace считается live и не
  очищается/не переиспользуется автоматически.
  Общая классификация выполняется `is_lock_only_tombstone()`; повторный
  `close_participant()` над canonical tombstone идемпотентно успешен без запуска
  адаптера и без замены lock inode.
- Exact-route `ask_participant()` принимает парные `operation_id` и
  `parent_provider_turn_id`. До повторного model call адаптер обязан выполнить
  `reconcile`: `completed` возвращает уже существующий provider turn, `absent`
  разрешает ровно один повтор той же operation, `ambiguous`/uninspectable
  блокирует продолжение. Timeout никогда не ведёт к blind re-ask.
  Вызов без этой пары сохранён только для legacy non-shard consumers и не
  удовлетворяет exactly-once/native-shard acceptance contract.
- `reconcile_participant()` доверяет `completed`/`absent` только при
  `evidence_complete=true`, проверяет operation identity и provider turn
  lineage. Operation marker должен быть provider-visible, а pending record
  сохраняется до вызова provider.
- `run_shards()` исполняет разные shard-callable параллельно с bounded worker
  budget и сохраняет порядок результатов; последовательностью задач внутри
  shard владеет один callable.
- Маппинг исходов (наследуется): `exit 0` → ok; `exit 1 + runtime.json
  phase=timeout` → timeout → unresponsive **без retry**; иной exit 1 → error →
  **ровно один retry** → unresponsive.
- Константы: `DEFAULT_TIMEOUT_SEC = 900` (per-invocation), `WAVE_GRACE_SEC =
  120` (subprocess timeout = T + 120, волна = T + 240), `PARALLEL_CAP = 8`
  (потолок параллелизма волны, TD §6.4), `REVIEW_ROOT = .review-sandboxes`,
  `DIFF_FILENAME = "review.diff"`, `ACTIVITY_FIELDS`.
- `start_participant(..., review_id=...)` принимает необязательный заранее
  назначенный идентификатор, валидирует его до запуска subprocess и передаёт
  адаптеру через `--review-id`; без параметра сохраняется generated-ID режим.
- `run_wave(tasks, ..., task_keys=...)` — ограниченная `PARALLEL_CAP`
  параллельная волна вызовов (ThreadPoolExecutor). При переданных ключах один
  ключ образует последовательную FIFO-lane, независимые ключи выполняются
  параллельно, а deadline возвращается только после quiescence уже запущенных
  callable. `keyed_wave_slots_bound()` даёт верхнюю границу scheduler slots.

### `scripts/registry.py` — единый реестр, схема v3 (FR-02, AC-02)

Один декларативный реестр `adapters.yaml` на оба инструмента; **два реестра
запрещены** (гарантированный рассинхрон). Парсер stdlib-only, fail-closed.

Поля записи (все обязательны, опциональных нет):

| Поле | Семантика |
| --- | --- |
| `id` | Уникальный id участника (ключ ссылок, observations, ротации) |
| `family` | Семейство модели (`claude`/`codex`/`kimi`): кворум разнообразия и наблюдаемая метаинформация |
| `model` | Alias модели → `--model` адаптера |
| `adapter` | Путь к адаптеру (проверяется на существование при `base_dir`) |
| `cli` | Бинарь CLI для healthcheck (`doctor`) |
| `context_budget` | Положительный int — бюджет контекста участника |
| `enabled` | bool — участвует в составе инструментов |
| `gate_legal` | bool — capability-флаг конкретного participant для gate-ролей (FR-14/FR-16); не кодирует пары или family-based selection |
| `quota_introspection` | Способ чтения квоты: `none` \| `codex-rollout` |
| `quota_status` | Статус доказательства доступности (TBD-01): `proven` \| `unavailable` |
| `native_fork` | Строго типизированная capability `{supported, route, min_cli_version, exact_checkpoint, automation_safe}` |

Инварианты (RISK-10, fail-closed в `validate_registry`):

- принимается только `version: 3` (молчаливого migration/fallback нет);
- неизвестные поля записи отклоняются с диагностикой;
- поле **`strengths` запрещено** (наследуется из CONS-01: strengths — read-only
  проекция из track record, самодекларация запрещена);
- единственная допустимая вложенная структура — `native_fork` с точным набором
  полей; прочие вложенные структуры и неизвестные capability-поля запрещены;
- дубликаты `id`, пустой `family`, несуществующий `adapter` — ошибки;
- квотный инвариант: `quota_introspection != "none"` ⟺ `quota_status ==
  "proven"` (иначе — ошибка схемы).

Расширение реестра новым [CLI+модель]: одна запись `adapters.yaml` + тонкий
адаптер в `scripts/adapters/` по контракту — harness и инструменты не
правятся (NFR-07).

### `scripts/structured.py` — fenced-блоки и анонимизация

- `parse_structured_block(text, fence_tag, empty_schema)` — извлечение fenced
  JSON-блока из ответа участника; отсутствующий/битый блок → пустые structured
  + warning (консервативная семантика; отвергает ли ход — решает предикат
  инструмента). Дефолтный тег `consilium-structured` — совместимость с
  форматом консилиума; рой передаёт свои теги (`swarm-structured`,
  `swarm-verdict`, …).
- `create_anon_map()` / `anonymize_text()` — случайная перестановка id →
  `M1..Mn` (стабильна при фиксированном seed) и подмена реальных id в текстах
  (длинные id первыми — защита от префиксов).
- `estimate_tokens()` — грубая оценка (~4 символа/токен) для `context_budget`.

### `scripts/domains.py` — доменные пакеты и риск-чеклисты

- Вложенный stdlib-парсер `domains.yaml` (схема E-1): `parse_domains_yaml`,
  `validate_domains`, `load_domains` (fail-closed при отсутствии/битом
  реестре), `domain_by_id`.
- Схема пакета: `id`, `description`, `roles` (≥2; у роли `id`, `title`,
  `lens`, `risk_checklist` с пунктами `{item_id, text, applies_to}`),
  `evidence_requirements`.
- `CHECKLIST_WAVE_TYPES = {proposal, attack, response, redteam,
  confirmation}` — разделяемый словарь `applies_to`; инструменты расширяют
  enum через параметр `wave_types` (рой: `+ tour1`), константа harness не
  меняется.
- Чеклисты: `applicable_checklist_items`, `validate_checklist_responses`
  (fail-closed таблица вырожденных случаев: покрытие по `applies_to`, enum
  `hit/clear/na`, обязательный note для `hit`/`na`), `checklist_stats`.
- `domains.yaml` содержит пакеты консилиума (`architecture` — default,
  `security-compliance`, `data-schema-evolution`, `incident-postmortem`,
  `design-direction`) и пакет `code-review` роя (шесть линз: security,
  correctness, concurrency, performance, data-contracts, tests).

### `scripts/track_record.py` — decay, strengths, хранение

- Формулы (наследуются CONS-01, без изменения логики): `observation_weight` —
  recency decay `w = 0.5^(k / half_life)`, `STRENGTHS_HALF_LIFE = 8` сессий;
  `STRENGTHS_FLOOR = 3` (проекция применяется при `n_eff ≥ 3`);
  `TAINTED_DISCOUNT = 0.5`; веса score `SCORE_W_ACCEPT = 0.6`,
  `SCORE_W_UPHELD = 0.4`; `EXPLORATION_EVERY = 4`.
- `compute_strengths(observations, seq_field=...)` — read-only проекция по
  ячейкам `(participant_id, role)` с сегрегацией tainted-наблюдений.
  `seq_field` параметризован: у инструментов разные счётчики сессий
  (`consilium_seq` у консилиума, `review_seq` у роя).
- Слой хранения **параметризован `storage_dir`** (FR-01е — у инструментов
  разные каталоги): `read_track_config` / `write_track_config`,
  `bump_counter`, `read_observations` / `append_observations` (append-only),
  `regenerate_strengths`. Консилиум пишет в `.consilium-track-record/`, рой —
  в `.swarm-track-record/`; схема — `references/track-record-schema.md`.
- Источник истины — `observations.jsonl`; `strengths.json` — генерируемый
  кэш/отчёт (на чтении не доверяется).

### `scripts/liveness.py` — «думает» / «повисла» (FR-13, AC-13)

Чистая классификация `classify_liveness()` по каноническим полям активности
(значения ISO-8601 или epoch):

- heartbeat старше `HEARTBEAT_DEAD_THRESHOLD_SEC` (10 с; каденс heartbeat
  адаптеров — 1 с) → `dead_watcher` (жёсткий сигнал: процесс-адаптер не жив);
- активность свежее `silence_threshold_sec` (default 120 с — TBD-04,
  переопределяется параметром сессии) → `active` («модель думает»);
- активность старше порога → `quiet` (диагностический маркер тишины);
- отсутствующие поля — fail-safe: heartbeat → `dead_watcher`, активность →
  `quiet`.

**Heartbeat — сигнал диагностики, не автоматический kill (RISK-05)**:
модуль только классифицирует; вмешательство — решение вызывающего
Оркестратора. `classify_participant()` — thin IO поверх
`adapter_contract.read_participant_activity`.

### `scripts/progress.py` — семантический прогресс (FR-13, AC-13)

`progress.jsonl` в каталоге сессии, append-only; пишет ядро инструмента,
вызывающая модель читает по желанию. Чекпоинты — на границах этапов (туров/
фаз), не поток сознания.

- Схема записи: `{ts, session_id, tool ("swarm"|"consilium"), checkpoint,
  summary (непустая), counters (плоский dict неотрицательных int)}`.
- `append_checkpoint(session_dir, ..., allowed_checkpoints=...)` — fail-closed
  валидация: неизвестный tool/checkpoint, пустой summary, битые counters →
  `ValueError`, файл не трогается. Множество допустимых чекпоинтов передаёт
  инструмент (правило границы).
- `read_checkpoints()`, `last_checkpoint()` — чтение; последний чекпоинт
  дублируется в `status` инструмента.

### `scripts/quota.py` — квотный выбор адаптера (FR-14, AC-14)

Источник квот — usage-introspection CLI (локальная книга учёта запрещена).
Доказанный introspection (TBD-01, probe 2026-07-29, TD §10): только codex —
rollout-файлы `~/.codex/sessions/**/rollout-*.jsonl`, последняя непустая
запись `payload.rate_limits` (`read_codex_quota`); claude/kimi —
`unavailable`.

Алгоритм `select_adapter()` (чистый предикат + thin IO):

1. **Floor-фильтр ПЕРЕД взвешиванием**: `enabled`, `id != id` вызывающего,
   для gate-ролей — `gate_legal`. Пустой пул → fail-closed
   `QuotaSelectionError`; literal self-review запрещён (NFR-01), но другой
   участник того же семейства остаётся eligible.
2. **Weighted** (детерминированный argmax `remaining_percent`, запрет повтора
   последнего выбора) — только если у **всех** кандидатов introspection proven
   и данные свежие (staleness ≤ 24 ч по mtime ЗАПИСИ rollout-файла —
   `recorded_at`, R-Final F-07; не по `resets_at` — моменту сброса окна).
3. Иначе — **blind** (равномерная ротация по порядку реестра, запрет повтора)
   с записью `quota_fallback_reason` в observations: `introspection
   unavailable: <cli>` / `stale: <id>` (протухшая запись) / `no data: <id>`
   (записи нет) — не молчаливая оценка и не блокировка.

Возврат: `{adapter_id, quota_mode, quota_fallback_reason, candidates,
remaining_percent}`.

### `scripts/adapters/` — три адаптера CLI

`claude_opus_review.py`, `codex_review.py`, `kimi_review.py` — тонкие обёртки
над CLI моделей, реализующие контракт `start/ask/fork/capabilities/status/close` + **обязательный
`sync`** (расширенный lifecycle `debate`/`log`/`stats`/`show` сохранён и не
ломается). Все три ведут канонические поля активности
`last_activity_at`/`last_heartbeat_at` в `runtime.json`. Read-only граница:
sandbox-копии, read-only инструкции в промптах; codex — read-only sandbox
mode; claude — инструменты `Read,Grep,Glob,LS`; kimi — встроенный read-only
agent profile через `--agent-file` (материализуется адаптером). Канонический
контракт — `references/adapter-contract.md`; контрактные тесты —
`tests/contract/test_adapter_contract.py` (все три CLI: схема аргументов,
`session_id`/resume, маппинг ошибок/таймаута, обязательный `sync`, поля
активности, материализация diff).

Native fork доступен только после runtime probe и точного совпадения с
`native_fork` из registry v3. Fork-команда обязана принять и сохранить один
`operation_id`, canonical `snapshot_digest` строго вида
`sha256:<64 lowercase hex>` и parent `provider_checkpoint_id`; stdout обязан
доказать эти значения, child session/turn lineage и `prompt_consumed`.
`prompt_consumed=false` — валидный zero-turn fork, а не доказательство
выполнения первой задачи.

Граница невозможности явная: если provider не предоставляет полного
неизменяющего history/inventory API и локальный provider stream/transcript тоже
утрачен, harness не может доказать ни completion, ни absence. Такое состояние
остаётся `ambiguous` и требует внешнего решения; повторный model call, synthetic
replay или предположение «скорее всего не выполнилось» запрещены. Для Claude
доказательство ограничено локальным stream/transcript; для Codex используется
app-server thread history/inventory; для Kimi — authenticated REST history и
session inventory. Недоступность соответствующего канала означает fail-closed.

## Как подключать библиотеку (потребителям)

Пакетной установки нет (без оверинжиниринга, FR-01): инструмент добавляет
`scripts/` harness в `sys.path` от собственного файла и импортирует модули
напрямую. Канонический паттерн (одинаков в `consilium.py` и `swarm.py`):

```python
HARNESS_DIR = Path(__file__).resolve().parents[2] / "review-harness"
_HARNESS_SCRIPTS = HARNESS_DIR / "scripts"
if str(_HARNESS_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(_HARNESS_SCRIPTS))

import adapter_contract as ac
import liveness
import progress as progress_tracker
import quota
import registry as registry_mod
import structured
import track_record
# + domains — где нужны доменные пакеты
```

Реестр и домены по умолчанию: `HARNESS_DIR / "adapters.yaml"`,
`HARNESS_DIR / "domains.yaml"`. Второй реестр/копия модулей в инструменте
запрещены — только тонкая секция инструмента «кого отбирать для этого
запуска» (FR-02).

## Тесты

```bash
python3 -m pytest framework/skills/tool-usage/review/review-harness/tests -q --tb=short
```

- `tests/unit/` — чистые предикаты модулей + boundary-тест HU-B01/B02;
- `tests/contract/` — контрактные тесты адаптеров (переехали из консилиума,
  стали тестами библиотеки, NFR-04); stub-CLI — `tests/stubs/stub_cli.py`.
