---
name: codex-subagent-orchestration
description: Техническое правило запуска сабагентов multi_agent_v2, fork_turns, model/reasoning args, handoff context и recovery при недоступном runtime.
alwaysApply: false
---

# Subagent Orchestration

## Назначение

Техническое orchestrator-only rule: runtime contract запуска сабагентов (каким tool'ом, какие параметры, как передавать контекст, что делать если runtime недоступен). **Каких** сабагентов и **когда** запускать — решает orchestrator по своему skill'у, routing matrix, workflow rules, owner profiles и task/risk profile. Правило применяется после того, как решение о делегации уже принято.

## Explicit authorization

Постоянное пользовательское указание репозитория: работа, меняющая проект, выполняется через multi-agent/subagent execution — это явная авторизация на запуск сабагентов и удовлетворяет runtime-требование explicit user request. Если policy-слой более высокого приоритета всё равно блокирует запуск — не имитируй delegation в solo mode: запиши blocker/deviation и эскалируй.

## Runtime contract: `multi_agent_v2`

Запускай сабагентов через namespaced tool `agents.spawn_agent` (`spawn_agent` в runtime namespace `agents`). Не используй верхнеуровневый legacy `spawn_agent`, если namespace `agents` доступен.

Каждый вызов передаёт реальные runtime-аргументы:

| Аргумент | Статус | Значение |
|----------|--------|----------|
| `task_name` | обязательный | lowercase имя задачи |
| `message` | обязательный | полный handoff без опоры на историю треда |
| `fork_turns` | обязательный | только `"none"` (см. Fork policy) |
| `model` | обязательный | выбран по task/risk profile |
| `reasoning_effort` | обязательный | `low` \| `medium` \| `high` \| `xhigh` |
| `agent_type` | опциональный | профиль/роль сабагента, если нужен и есть в schema |
| `service_tier` | опциональный | только если tier выбран явно |

Не передавай в `agents.spawn_agent` поля handoff-template `spawn_settings`, `selection_rationale`, `scope`, `constraints`, `inputs`, `expected_output` — они живут внутри `message` / handoff text или в orchestration trace.

Каноническая форма runtime-вызова:

```json
{
  "task_name": "<lowercase_task_name>",
  "message": "<полный handoff без опоры на историю треда>",
  "fork_turns": "none",
  "model": "<chosen model>",
  "reasoning_effort": "<low|medium|high|xhigh>",
  "agent_type": "<profile role, если нужен>",
  "service_tier": "<если явно выбран>"
}
```

## Fork policy

Не полагайся на дефолт `fork_turns`: в `multi_agent_v2` пустое значение трактуется как full-history fork (`all`). По умолчанию запрещены:

- omitted / empty `fork_turns`;
- `fork_turns: "all"`;
- числовой partial fork;
- `fork_context` (в `multi_agent_v2` не поддерживается).

Для отключения форка истории используй только `fork_turns: "none"`. Сабагент не должен получать старую историю треда как скрытый source of truth — нужный контекст orchestrator собирает явным handoff'ом в `message`.

## Handoff context

`message` содержит полный handoff, достаточный для выполнения без доступа к истории родительского треда. Минимальный handoff:

- роль / профиль сабагента; задачу; scope и non-goals;
- write/read boundaries; relevant paths;
- confirmed decisions и constraints; expected output;
- required skills/rules/docs; escalation triggers;
- требования к append-only context / handoff-back, если нужны активному workflow.

Блок `spawn_settings` (если используется handoff-template orchestrator'а) — локальная запись выбранных runtime-параметров и rationale, не является параметром `agents.spawn_agent`.

### Self-check в handoff сабагента

Каждый handoff обязан содержать явную инструкцию по самоконтролю — защита от зависаний на упавших командах, недостигнутом pre-run gate, отсутствии cleanup после failure и ложного ожидания «ещё немного». Минимальная формулировка в `message`:

- не ждать бесконечно команду, процесс, GUI, build, тест или внешний сервис;
- если команда завершилась ошибкой или pre-run gate не пройден — классифицировать результат (`test_error`, `implementation_error`, `environment_error`, `blocked_pre_run` и т.п.), выполнить обязательный cleanup и вернуть отчёт;
- после каждого существенного шага перепроверять: «есть ли уже достаточный результат или blocker для возврата orchestrator'у?»;
- не начинать новый обходной путь после failure без явной проверки, что он остаётся в scope;
- при отсутствии прогресса до собственного time budget — остановиться с partial result, а не висеть.

Для команд, способных зависнуть, handoff задаёт конкретный time budget и признаки прогресса: допустимые процессы, ожидаемые файлы/логи, какой report/статус считается завершением, обязательный cleanup при failure. Создав результат и выполнив cleanup, сабагент сразу возвращает `FINAL_ANSWER` с классификацией результата; дополнительные «проверю ещё» шаги после достаточного результата запрещены, если не были частью handoff'а.

## Model и reasoning

Orchestrator выбирает `model` и `reasoning_effort` по task/risk profile и передаёт их аргументами, фиксируя rationale в handoff/trace (`spawn_settings.selection_rationale` — локальное поле handoff'а, не runtime-параметр). Правило выбора:

- `low` — простой поиск, механическая сверка, bounded вспомогательные проверки;
- `medium` — обычные bounded engineering задачи;
- `high` / `xhigh` — architecture, security/compliance, сложная отладка, acceptance-bound review и финальные решения.

Модели класса `mini` допустимы только для exploration, bounded discovery и sidecar-задач, где результат не закрывает acceptance gate и не является финальным owner-output по фазе. Capability floor для blocking review и acceptance-bound gates задаётся активными routing / reviewer rules; если runtime не позволяет его соблюсти — запиши deviation/blocker, а не подменяй модель молча. При указанном `agent_type` профиль может иметь role-locked настройки модели и reasoning: переданные `model` / `reasoning_effort` не гарантируют effective settings, если профиль их переопределяет. Если override нарушает capability floor — запиши deviation/blocker.

## Health-check запущенных сабагентов

Шкала контроля: **5 → 10 → 15 → 15… минут** — первый health-check через 5 минут после запуска; второй — через 10 минут после предыдущего check; третий — через 15 минут после предыдущего; далее каждые 15 минут, пока сабагент не завершён. Health-check — не пассивное ожидание `wait_agent`; на каждом check проверяй внешние признаки движения:

- жив ли сабагент, есть ли queued/final message;
- какие процессы запустил и соответствуют ли handoff'у;
- появились ли ожидаемые артефакты: context, report, build/test logs, temp files, cleanup markers;
- растут ли логи или процесс завис без вывода;
- не завершена ли фактически работа по файлам/логам, даже если нет `FINAL_ANSWER`;
- не оставлены ли temp объекты, locks, deny flags, GUI/session processes или другие cleanup obligations;
- не ушёл ли сабагент в обходной путь вне scope.

**Правило двух проверок / эскалация.** Если по артефактам работа завершена, но отчёта нет — прерви сабагента, зафиксируй результат по source-of-truth артефактам и продолжи routing. Если два consecutive health-check не показывают прогресса, процесс делает не то, что в handoff, или time budget превышен примерно в 1.5 раза — прерви сабагента и перезапусти более узкую задачу с фактами из файлов/логов. Третий «подождём ещё» запрещён. При запуске укажи в handoff time budget, expected progress artifacts и cleanup obligations; при anomaly — запиши в orchestration trace `HEALTHCHECK_ANOMALY`, `INTERRUPT`, `RESTART` или `SCOPE_CORRECTION`.

## Если multi-agent tools недоступны

Если нет `agents.spawn_agent`, `agents.list_agents`, `agents.wait_agent` или schema `agents.spawn_agent` не позволяет передать `model` / `reasoning_effort` — не подменяй это legacy-вызовами и не продолжай medium/full-cycle задачу как solo execution. Порядок:

1. Зафиксируй blocker/deviation: multi-agent runtime не соответствует требованиям этого rule.
2. Сообщи пользователю, что для этого репозитория нужен включённый Codex `multi_agent_v2`, и попроси явное подтверждение на изменение пользовательской runtime-конфигурации. Менять настройки агента молча категорически запрещено.
3. После явного подтверждения предложи или внеси в `~/.codex/config.toml` (`/home/vscode/.codex/config.toml`):

```toml
[features.multi_agent_v2]
enabled = true
tool_namespace = "agents"
hide_spawn_agent_metadata = false
max_concurrent_threads_per_session = 8
```

- `hide_spawn_agent_metadata = false` обязателен, если agent должен видеть и передавать `model`, `reasoning_effort`, `service_tier` и `agent_type` в `agents.spawn_agent`.
- `max_concurrent_threads_per_session = 8` — явный safety cap для `multi_agent_v2` (не только на сабагентов): Codex считает все активные thread'ы в session tree, включая root-agent, поэтому `8` = `1` root + до `7` одновременных resident/active subagent thread'ов. Cap защищает сессию от неконтролируемого роста loaded threads, параллельных model turns, tool calls, token/usage расхода и шума в trace. Нужно больше параллельных сабагентов — увеличивай осознанно: желаемое число + `1` за root (для `10` сабагентов — `max_concurrent_threads_per_session = 11`). Не используй legacy `[agents].max_threads` вместе с `multi_agent_v2` — такой конфиг конфликтует с v2 runtime.

После изменения `config.toml` сообщи, что для появления `multi_agent_v2` tools и обновлённой schema нужен запуск новой Codex-сессии; текущая сессия может их не получить. Если пользователь не подтвердил изменение или новая сессия невозможна — останови medium/full-cycle flow и оставь явный blocker вместо снижения требований к multi-agent execution.

## Trace expectations

Orchestrator фиксирует в orchestration trace / `.context/orchestrator-context.md`:

- workstream / task name;
- owner profile / `agent_type`, если использовался;
- `agent/session id`;
- фактически переданные `fork_turns`, `model`, `reasoning_effort`, `service_tier`;
- rationale выбора модели/reasoning;
- факт `fork_turns: "none"`;
- blockers/deviations, включая недоступный runtime или нарушение capability floor.
