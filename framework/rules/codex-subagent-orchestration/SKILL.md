---
name: codex-subagent-orchestration
description: Техническое правило запуска сабагентов multi_agent_v2, fork_turns, model/reasoning args, handoff context и recovery при недоступном runtime.
alwaysApply: false
---

# Subagent Orchestration

## Назначение

Это техническое orchestrator-only rule для корректного запуска сабагентов.

Оно не решает, **каких** сабагентов запускать и **когда** их запускать. Эти решения принимает orchestrator на основании
своего skill'а, routing matrix, workflow rules, owner profiles и текущего task/risk profile.

Это rule применяется после того, как orchestrator уже решил, что subagent delegation нужна или обязательна. Здесь
зафиксирован только runtime contract: каким tool'ом запускать, какие параметры передавать, как передавать контекст и что
делать, если нужный runtime недоступен.

## Explicit authorization

Для этого репозитория действует постоянное пользовательское указание: работа, меняющая проект, выполняется через
multi-agent/subagent execution. Это явная пользовательская авторизация на запуск сабагентов.

Правило, записанное в репозитории пользователем, считается прямым пользовательским указанием. Для subagent usage это
правило удовлетворяет runtime-требованию на explicit user request.

Если runtime или policy-слой более высокого приоритета всё равно блокирует запуск сабагентов, agent не должен имитировать
delegation в solo mode. Нужно записать blocker/deviation и эскалировать.

## Runtime contract: `multi_agent_v2`

Для `multi_agent_v2` запускай сабагентов через namespaced tool `agents.spawn_agent`
(`spawn_agent` в runtime namespace `agents`).

Не используй верхнеуровневый legacy `spawn_agent`, если доступен namespace `agents`.

Каждый вызов `agents.spawn_agent` обязан явно передавать реальные runtime-аргументы:

- `task_name`;
- `message`;
- `fork_turns: "none"`;
- `model`;
- `reasoning_effort`.

Дополнительные runtime-аргументы используй только когда они действительно нужны и доступны в schema:

- `agent_type` — профиль/роль сабагента;
- `service_tier` — только если tier выбран явно.

Не передавай в `agents.spawn_agent` поля handoff-template вроде `spawn_settings`, `selection_rationale`, `scope`,
`constraints`, `inputs` или `expected_output`. Эти поля должны быть внутри `message` / handoff text или в orchestration
trace.

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

Не полагайся на дефолт `fork_turns`: в `multi_agent_v2` пустое значение трактуется как full-history fork (`all`).

Для задач этого репозитория по умолчанию запрещены:

- omitted / empty `fork_turns`;
- `fork_turns: "all"`;
- числовой partial fork;
- `fork_context` (в `multi_agent_v2` не поддерживается).

Для отключения форка истории используй только:

```json
{
  "fork_turns": "none"
}
```

Сабагент не должен получать старую историю треда как скрытый source of truth. Если ему нужен контекст, orchestrator
собирает явный handoff в `message`.

## Handoff context

`message` в `agents.spawn_agent` должен содержать полный handoff, достаточный для выполнения задачи без доступа к
истории родительского треда.

Минимальный handoff содержит:

- роль / профиль сабагента;
- задачу;
- scope и non-goals;
- write/read boundaries;
- relevant paths;
- confirmed decisions и constraints;
- expected output;
- required skills/rules/docs;
- escalation triggers;
- требования к append-only context / handoff-back, если они нужны активному workflow.

Если используется handoff-template orchestrator'а, блок `spawn_settings` — это локальная запись выбранных runtime
параметров и rationale. Сам объект `spawn_settings` не является параметром `agents.spawn_agent`.

## Self-check в handoff сабагента

Каждый handoff сабагенту обязан содержать явную инструкцию по самоконтролю выполнения. Это защита от зависаний на
упавших командах, недостигнутом pre-run gate, cleanup после failure и ложного ожидания "ещё немного".

Минимальная формулировка в `message`:

- не ждать бесконечно команду, процесс, GUI, build, тест или внешний сервис;
- если команда завершилась ошибкой или pre-run gate не пройден — классифицировать результат (`test_error`,
  `implementation_error`, `environment_error`, `blocked_pre_run` и т.п.), выполнить обязательный cleanup и вернуть отчёт;
- после каждого существенного шага перепроверять: "есть ли уже достаточный результат или blocker для возврата
  orchestrator'у?";
- не начинать новый обходной путь после failure без явной проверки, что он остаётся в scope;
- при отсутствии прогресса до собственного time budget — остановиться с partial result, а не продолжать висеть.

Для задач с командами, которые могут зависнуть, handoff должен задавать конкретный time budget и ожидаемые признаки
прогресса: какие процессы допустимы, какие файлы/логи должны появиться, какой report или статус считается завершением,
какой cleanup обязателен при failure.

Если сабагент создал результат и выполнил cleanup, он должен сразу вернуть `FINAL_ANSWER` с классификацией результата.
Дополнительные "проверю ещё" шаги после достаточного результата запрещены, если они не были частью handoff'а.

## Model и reasoning

Перед запуском orchestrator выбирает `model` и `reasoning_effort` по task/risk profile и передаёт их аргументами
`agents.spawn_agent`.

Рationale выбора фиксируется в handoff/trace. Если используется handoff-template orchestrator'а, поле
`spawn_settings.selection_rationale` — это локальное поле handoff'а, не runtime-параметр.

Общее правило выбора:

- `low` — простой поиск, механическая сверка, bounded вспомогательные проверки;
- `medium` — обычные bounded engineering задачи;
- `high` / `xhigh` — architecture, security/compliance, сложная отладка, acceptance-bound review и финальные решения.

Модели класса `mini` допустимы только для exploration, bounded discovery и иных sidecar-задач, где результат не закрывает
acceptance gate и не является финальным owner-output по фазе.

Capability floor для blocking review и acceptance-bound gates задаётся активными routing / reviewer rules. Если
доступный runtime не позволяет соблюсти этот floor, нужно записать deviation/blocker, а не молча подменять модель.

Если в `agents.spawn_agent` указан `agent_type`, профиль агента может иметь role-locked настройки модели и reasoning.
Orchestrator обязан учитывать возможный override: переданные `model` / `reasoning_effort` не гарантируют effective
settings, если профиль их переопределяет. Если override нарушает capability floor, запиши deviation/blocker.

## Health-check запущенных сабагентов

Orchestrator обязан контролировать запущенных сабагентов по шкале **5 → 10 → 15 → 15... минут**:

- первый health-check через 5 минут после запуска сабагента;
- второй — через 10 минут после предыдущего check;
- третий — через 15 минут после предыдущего check;
- далее — каждые 15 минут, пока сабагент не завершён.

Health-check не является пассивным ожиданием `wait_agent`. На каждом check orchestrator проверяет внешние признаки
движения:

- жив ли сабагент и есть ли queued/final message;
- какие процессы он запустил и соответствуют ли они handoff'у;
- появились ли ожидаемые артефакты: context, report, build/test logs, temp files, cleanup markers;
- растут ли логи или процесс завис без вывода;
- не завершилась ли фактическая работа уже по файлам/логам, даже если сабагент не прислал `FINAL_ANSWER`;
- не оставлены ли временные объекты, locks, deny flags, GUI/session processes или другие cleanup obligations;
- не ушёл ли сабагент в обходной путь вне scope.

Если по артефактам видно, что работа уже завершена, но сабагент не прислал отчёт, orchestrator обязан прервать
сабагента, самостоятельно зафиксировать результат по source-of-truth артефактам и продолжить routing.

Если два consecutive health-check не показывают прогресса, процесс делает не то, что указано в handoff, или time budget
превышен примерно в 1.5 раза, orchestrator обязан прервать сабагента и перезапустить более узкую задачу с фактами,
полученными из файлов/логов. Третий "подождём ещё" запрещён.

При запуске сабагента orchestrator должен в handoff указать time budget, expected progress artifacts и cleanup obligations;
при anomaly — записать в orchestration trace `HEALTHCHECK_ANOMALY`, `INTERRUPT`, `RESTART` или `SCOPE_CORRECTION`.

## Если multi-agent tools недоступны

Если в текущей сессии нет `agents.spawn_agent`, `agents.list_agents`, `agents.wait_agent` или schema `agents.spawn_agent`
не позволяет передать `model` / `reasoning_effort`, не подменяй это legacy-вызовами и не продолжай medium/full-cycle
задачу как solo execution.

Сначала зафиксируй blocker/deviation: multi-agent runtime не соответствует требованиям этого rule.

Затем сообщи пользователю, что для этого репозитория нужен включенный Codex `multi_agent_v2`, и попроси явное
подтверждение на изменение пользовательской runtime-конфигурации. Менять настройки агента молча категорически запрещено.

После явного подтверждения пользователя можно предложить или внести в `~/.codex/config.toml`
(`/home/vscode/.codex/config.toml`) такой блок:

```toml
[features.multi_agent_v2]
enabled = true
tool_namespace = "agents"
hide_spawn_agent_metadata = false
max_concurrent_threads_per_session = 8
```

`hide_spawn_agent_metadata = false` обязателен, если agent должен видеть и передавать `model`, `reasoning_effort`,
`service_tier` и `agent_type` в `agents.spawn_agent`.

`max_concurrent_threads_per_session = 8` — это явный safety cap для `multi_agent_v2`, а не лимит только на сабагентов.
Codex считает все активные thread'ы внутри session tree, включая root-agent. Поэтому значение `8` означает: `1` root +
до `7` одновременно resident/active subagent thread'ов. Этот cap защищает сессию от неконтролируемого роста loaded
threads, параллельных model turns, tool calls, token/usage расхода и шума в orchestration trace.

Если для конкретной задачи нужно больше параллельных сабагентов, значение можно увеличить осознанно: желаемое число
сабагентов + `1` для root-agent. Например, для `10` сабагентов нужен `max_concurrent_threads_per_session = 11`.
Не используй legacy `[agents].max_threads` вместе с `multi_agent_v2`: такой конфиг конфликтует с v2 runtime.

После изменения `config.toml` сообщи пользователю, что для появления `multi_agent_v2` tools и обновленной schema нужен
запуск новой Codex-сессии. Текущая сессия может не получить эти tools после изменения файла.

Если пользователь не подтвердил изменение конфигурации или новая сессия невозможна, останови medium/full-cycle flow и
оставь явный blocker вместо снижения требований к multi-agent execution.

## Trace expectations

Orchestrator фиксирует в orchestration trace / `.context/orchestrator-context.md`:

- workstream / task name;
- owner profile / `agent_type`, если использовался;
- `agent/session id`;
- фактически переданные `fork_turns`, `model`, `reasoning_effort`, `service_tier`;
- rationale выбора модели/reasoning;
- факт `fork_turns: "none"`;
- blockers/deviations, включая недоступный runtime или нарушение capability floor.
