---
name: cross-provider-review
description: "Для второго ревью другой моделью и debate-сессий"
capabilities: review,agent-governance,cross-provider
---

# Cross-Provider Review

Единый навык для cross-family второго мнения. Reviewer является advisory-слоем, не финальным authority, и не должен
редактировать реальный проект.

AI governance classification: `advice-only`. Owner: orchestrator/primary agent. HITL требуется там, где это требуют
workflow, продуктовые или архитектурные approval gates. Quality signal: evidence-backed findings, явная позиция primary
agent, review trace и наблюдаемый lifecycle/cleanup.

## Routing

- Если primary agent относится к GPT/Codex-family, используй Claude/Opus adapter:
  `.agents/skills/cross-provider-review/scripts/claude_opus_review.py`
- Если primary agent относится к Claude/Opus/Sonnet-family, используй Codex/GPT adapter:
  `.agents/skills/cross-provider-review/scripts/codex_review.py`
- Same-family/self-review не закрывает cross-family gate.

## Режимы

Навык работает в двух режимах с разной семантикой приговора:

- **advisory** (default) — per-artifact review внутри фазы. Последнее слово за primary agent / оркестратором; reviewer даёт второе мнение, которое обрабатывается как обычный feedback. Все per-artifact запуски в workflow — advisory.
- **gate** — финальный ревью перед закрытием задачи. Приговор reviewer'а blocking: `verdict: PASS` — обязательное условие для завершения. Этот режим используется оркестратором ровно один раз в конце задачи, вместо advisory-финала.

Режим фиксируется в вводном промпте (via `--constraints` / `--review-ask`) — reviewer должен явно знать, blocking у него приговор или advisory.

## Prompts

- `references/review-prompt.md` — default shape для advisory-ревью (task artifacts и acceptance-bound reviews). Может использоваться упрощённо для free-form opinion review / idea critique, пока read-only и evidence boundaries явные.
- `references/finalization-prompt.md` — шаблон для **gate**-режима (финал задачи). Включает жёсткую структуру: bidirectional rule compliance check, goal verification с traceability-таблицей, anti-deception checklist, итерационный протокол с эскалацией пользователю после 3 раундов.

## Session Lifecycle

Оба adapter поддерживают одинаковый lifecycle:

- `start`: создаёт `.review-sandboxes/<review_id>/workspace`, материализует focused paths или full context (по умолчанию через hardlink — почти мгновенно и без расхода диска) и запускает reviewer.
- `ask`: продолжает сохранённую сессию.
- `debate`: обсуждает одну конкретную finding.
- `sync`: обновляет sandbox из реальных source paths.
- `status`: показывает phase, heartbeat, pid, logs, timeout, result preview и live progress counters.
- `log`: показывает prompt/response history.
- `stats`: показывает доступные token/cost stats, raw event stats и tool-call counters.
- `show`: показывает review metadata, cumulative stats и runtime state одним JSON payload.
- `close`: закрывает и по умолчанию удаляет sandbox; `--keep-sandbox` использовать только для forensic/debug.

Status interpretation: движущийся heartbeat означает, что процесс жив; stale heartbeat без stdout/stderr growth является
практическим сигналом stuck-состояния; `phase=timeout` означает, что один invocation превысил timeout.

## 🔴 КРИТИЧНО: обязательная очистка sandbox (`close`)

> **Уровень: CRITICAL / MUST.** Очистка sandbox привязана ИСКЛЮЧИТЕЛЬНО к явному вызову `close`. В адаптерах НЕТ
> автоматической уборки: ни `atexit`, ни обработчика сигналов, ни TTL/age-sweep, ни сборки осиротевших sandbox при `start`.
> Если агент не дошёл до `close` (краш, yield, ветка ошибки/FAIL, эскалация, забывчивость) — каталог
> `.review-sandboxes/<review_id>/` вместе с full-context зеркалом источника остаётся на диске **навсегда**. На практике это
> уже приводило к десяткам осиротевших каталогов, удалённых вручную.

**MUST для каждого `start`:**

| Требование | Описание |
|-----------|----------|
| Парность start↔close | Любой `start` ОБЯЗАН иметь парный `close` в том же сеансе работы агента. `start` без гарантированного `close` запрещён |
| close во всех ветках | `close` вызывается на ЛЮБОМ пути завершения: PASS, FAIL, эскалация пользователю, отказ от ревью, ошибка адаптера. Не только на happy path |
| close в финализации | Перед записью `final-report.md` агент ОБЯЗАН убедиться, что все открытые review закрыты (см. чекпоинт ниже) |
| Отчёт о cleanup | В итоговый отчёт/контекст записывается `cleanup status` каждого review_id: `closed` или (редко) `kept --keep-sandbox: <причина forensic>` |
| `--keep-sandbox` только обоснованно | Применять ТОЛЬКО для forensic/debug с явной письменной причиной. По умолчанию — обычный `close` с удалением |

**✅ CHECKPOINT перед завершением задачи (выполнить ОБЯЗАТЕЛЬНО):**

```bash
# 1. Показать все НЕзакрытые sandbox в проекте:
ls -1 .review-sandboxes/ 2>/dev/null
# 2. Для каждого оставшегося <review_id> — закрыть:
<adapter-script> close <review_id>
# 3. Подтвердить, что каталог пуст (ожидается 0):
ls -1 .review-sandboxes/ 2>/dev/null | wc -l
```

Если шаг 3 вернул не 0 — задача НЕ считается завершённой по части cleanup: закрыть оставшиеся review и только потом
закрывать задачу. Непустой `.review-sandboxes/` на момент финального отчёта = нарушение этого навыка.

## Claude / Opus Adapter

Start:

```bash
.agents/skills/cross-provider-review/scripts/claude_opus_review.py start \
  --full-context \
  --task "<task>" \
  --goal "<review focus>" \
  --requirements "<requirements>" \
  --constraints "Second-opinion review only. Do not implement fixes." \
  --primary-target "<file>" \
  --changed-files <file1> <file2> \
  --open-concerns "<concerns>" \
  --review-ask "Review this artifact as a second opinion. Order findings by severity." \
  --question "Perform a second-opinion review of the current work."
```

Focused/free-form:

```bash
.agents/skills/cross-provider-review/scripts/claude_opus_review.py start \
  --question "Review this idea and identify the strongest counterarguments." path/to/file.md
```

## Codex / GPT Adapter

Start:

```bash
.agents/skills/cross-provider-review/scripts/codex_review.py start \
  --full-context \
  --task "<task>" \
  --goal "<review focus>" \
  --artifact-type "<code|tests|architecture|policy|prompt>" \
  --requirements "<requirements>" \
  --constraints "Second-opinion review only. Do not implement fixes." \
  --primary-target "<file>" \
  --changed-files <file1> <file2> \
  --open-concerns "<concerns>" \
  --review-ask "Review this artifact as a second opinion. Order findings by severity." \
  --question "Perform a second-opinion review of the current work."
```

Focused/free-form:

```bash
.agents/skills/cross-provider-review/scripts/codex_review.py start \
  --question "Review this idea and identify the strongest counterarguments." path/to/file.md
```

## Common Session Commands

После `start` используй один и тот же lifecycle для обоих adapters. В примерах ниже `<adapter-script>` означает выбранный
по routing script:

- `.agents/skills/cross-provider-review/scripts/claude_opus_review.py`
- `.agents/skills/cross-provider-review/scripts/codex_review.py`

```bash
<adapter-script> ask REVIEW_ID --question "..."
<adapter-script> debate REVIEW_ID --issue "F-01" --finding "..." --position "..."
<adapter-script> sync REVIEW_ID
<adapter-script> status REVIEW_ID
<adapter-script> log REVIEW_ID
<adapter-script> stats REVIEW_ID
<adapter-script> show REVIEW_ID
<adapter-script> close REVIEW_ID
```

Используй `status`, пока blocking review долго выполняется. Используй `sync` после изменения source artifacts и перед
follow-up или delta review. Используй `log`, `stats` и `show` для trace/debug; это не обязательные команды каждого
happy path. `close --keep-sandbox` используй только для редких forensic/debug cases.

`status.runtime.progress` и `stats` включают adapter-observable activity для Claude и Codex reviews:

- `raw_events`: количество JSON-событий CLI;
- `event_types`: счётчики событий по типам;
- `tool_calls_total`: общее число уникальных observed tool/function calls;
- `tool_calls_by_name`: счётчики tool/function calls по имени инструмента;
- `unique_tool_call_ids`: число уникальных tool/function call ids, если CLI отдаёт ids;
- `tool_result_events`: observed tool/function result events;
- `permission_denials`: observed permission-denial events;
- `server_tool_use`: provider-reported server-side tool counters, если доступны.

Это runtime observability, а не замена выводам reviewer. Счётчики помогают отличить реально активное review от процесса,
у которого меняется только heartbeat.

## Useful Options

- `--review-id`: задать стабильный ID для task traceability.
- `--timeout-sec`: изменить timeout одного reviewer invocation.
- `--copy-mode {hardlink,copy}`: способ материализации sandbox. `hardlink` (default) — почти мгновенно, ~0 байт на диске; `copy` — полный байт-копий. Hardlink автоматически падает в copy при cross-device или unsupported FS.
- `--keep-sandbox`: сохранить review files при `close` только для forensic/debug.
- Только Codex: `--artifact-type`, `--skills`, `--reasoning-effort`.
- Только Claude: `--model`.

## Acceptance-Bound Protocol (advisory)

Для per-artifact acceptance-bound ревью (advisory-режим):

1. Запусти opposite-family adapter.
2. Мониторь процесс через `status`, если review выполняется долго.
3. Назначь finding IDs (`F-01...`), если reviewer этого не сделал.
4. Проверь каждую finding по реальным artifacts и отметь `agree`, `partial`, `disagree`, `withdrawn` или `out_of_scope`.
5. Добавь primary-agent findings как `C-01...`, если нужно.
6. Если source artifacts изменились после rework, выполни `sync` перед follow-up или delta review.
7. Используй `ask` для follow-up/delta review и `debate` только для конкретных спорных finding IDs.
8. Используй `log`, `stats` или `show`, когда нужны trace/debug evidence.
9. Остановись на consensus, unchanged stalemate for two rounds или max round count.
10. **🔴 MUST — `close` REVIEW_ID, как только review больше не нужен** (см. раздел «КРИТИЧНО: обязательная очистка sandbox»). Это не «когда удобно», а обязательный шаг закрытия: без него sandbox остаётся на диске навсегда. `close` вызывается даже если ревью завершилось отказом/ошибкой.
11. Зафиксируй final report: unified findings, disagreements with both positions, iteration count, recommendation,
    review id, **cleanup status (`closed` для каждого review_id)** и relevant status/log evidence. Перед закрытием задачи прогони CHECKPOINT из раздела «КРИТИЧНО»: `.review-sandboxes/` должен быть пуст.

## Finalization Gate Protocol (blocking)

Используется оркестратором один раз в конце задачи. В отличие от advisory-протокола, здесь последнее слово за reviewer'ом.

**Предусловие:** оркестратор обязан собрать полный evidence pack (см. `references/finalization-prompt.md` раздел «Входные данные»). Без одного из пунктов reviewer отвечает `verdict: FAIL` на первом же раунде.

**Шаги:**

1. Запусти opposite-family adapter с промптом из `references/finalization-prompt.md`. В `--constraints` укажи: «Finalization gate mode. Verdict is blocking, not advisory. Use bidirectional rule compliance check.»
2. Передай полный evidence pack (пути к файлам + git-diff + stdout тестов).
3. Получи ответ: findings + `verdict: PASS | FAIL` + `iteration: N of 3`.
4. Если `verdict: PASS` — задача может закрываться. Зафиксируй review_id в `final-report.md` в блоке `cross_provider_review`.
5. Если `verdict: FAIL` — обработай findings evidence-based правками (diff, новый stdout, уточнённый лог). Используй `ask` для следующего раунда.
6. Если `iteration: 3` и приговор не `PASS` — reviewer выдаёт `escalate_to_user: true` с `dispute_summary`. Оркестратор обязан эскалировать пользователю, передав dispute_summary дословно. Решение пользователя — финальное.
7. **🔴 MUST — `close` review после задокументированного приговора PASS или user override'а** (см. раздел «КРИТИЧНО: обязательная очистка sandbox»). Закрытие gate-review обязательно ВО ВСЕХ исходах, включая эскалацию после 3 раундов: после фиксации приговора/override в `final-report.md` sandbox должен быть удалён через `close`. Затем прогони CHECKPOINT: `.review-sandboxes/` пуст.

**Запрещено:**
- Закрывать задачу (`final-report.md` + отчёт пользователю «готово») без `verdict: PASS` или пользовательского override'а.
- Закрывать задачу с непустым `.review-sandboxes/` — каждый review_id ОБЯЗАН быть `close`-нут (см. CHECKPOINT в разделе «КРИТИЧНО»).
- Деградировать findings раунд за раундом — reviewer не обязан смягчаться.
- Запускать gate-режим в same-family (Claude→Claude или Codex→Codex) — это нарушает cross-family gate requirement.

## Safety

- Reviewers работают в изолированном sandbox workspace, а не в реальном проекте. По умолчанию sandbox — это hardlink-зеркало источника: writes от primary-agent в реальные файлы создают новый inode, и reviewer продолжает видеть замороженный снимок до явного `sync`. Сами reviewers строго read-only (см. ниже), поэтому хардлинки безопасны: запись через них невозможна.
- Full-context материализация исключает `.git`, `.venv`, `.review-sandboxes`, `node_modules`, `__pycache__`, common build outputs, а также `.claude`, `.codex`, `.cursor`, `.windsurf`, `.idea` — чтобы reviewer не подхватывал hooks/permissions/MCP-конфиги реального проекта.
- Reviewer prompts и adapter prompts включают read-only instructions.
- **Codex** запускается с `--sandbox read-only` — kernel-level sandbox блокирует любые записи независимо от того, что хочет модель.
- **Claude** запускается с `--tools=Read,Grep,Glob,LS`, `--permission-mode plan` (plan-only mode без write/edit) и `--strict-mcp-config` (без `--mcp-config` это означает «никаких MCP-серверов вообще»). Это permission-level гарантия в три слоя.
- Primary agent остаётся responsible за acceptance, rework и final synthesis.
