---
name: codex-subagent-orchestration
description: Техническое правило запуска Codex-сабагентов, выбора model/reasoning_effort, изоляции handoff, bounded consultation и recovery при недоступном runtime.
alwaysApply: false
---

# Оркестрация Codex-сабагентов

## Назначение

Применяй этот runtime contract к spawn'ам root orchestrator и к каждому явно разрешённому spawn'у
first-level owner: профильной support-задаче или bounded consultation. Решение о фазах и их owners
остаётся за orchestrator и workflow. Правило не даёт owner права маршрутизировать фазы,
координировать peer owners или передавать ownership.

Постоянное пользовательское указание репозитория на multi-agent execution считается явной
авторизацией. Если policy более высокого приоритета блокирует spawn, зафиксируй blocker/deviation;
не имитируй делегирование в solo mode.

## Runtime contract: `multi_agent_v2`

Перед каждым spawn сначала прочитай фактическую schema `agents.spawn_agent`: проверь доступные
модели, поддерживаемые выбранной моделью уровни effort и optional arguments. Не выдумывай enum и
не используй legacy tool при доступном namespace `agents`.

Передай реальные runtime-аргументы:

| Аргумент | Статус | Значение |
|---|---|---|
| `task_name` | MUST | lowercase имя задачи |
| `message` | MUST | self-contained handoff без опоры на историю thread |
| `fork_turns` | MUST | только `"none"` |
| `model` | MUST | отдельно выбранная поддерживаемая модель |
| `reasoning_effort` | MUST | отдельно выбранный поддерживаемый effort |
| `agent_type` | MAY | профиль/роль, если есть в schema |
| `service_tier` | MAY | только при явном выборе |

Не передавай как runtime arguments поля handoff-template: `spawn_settings`,
`selection_rationale`, `scope`, `constraints`, `inputs`, `expected_output`. Храни их в `message`
или orchestration trace.

## Model и reasoning

Выбирай `model` и `reasoning_effort` независимо. Для каждой оси запиши краткое rationale по
объёму, сложности, риску, стоимости и требуемой глубине. Название family не определяет effort,
а effort не заменяет выбор family.

В Codex-harness используй следующий binding канонической role-matrix. Он не наследуется от
модели текущего main-сеанса и не является самостоятельным источником продуктовой политики:

| Роль / тип работы | Model | Effort |
|---|---|---|
| `explorer` | `gpt-5.6-luna` | `max` |
| `scenario-coder`, `developer-tests`, `developer-code` | `gpt-5.6-luna` | `max` |
| `scenario-author`, `tester` | `gpt-5.6-sol` | `medium` |
| `analyst`, `architect`, `debugger` | `gpt-5.6-sol` | `high` |
| acceptance-bound `reviewer` | `gpt-5.6-sol` | не ниже фактического effort автора; default `high` |

Для Explorer default равен `Luna/max`; понижение допустимо только как явный effort override
Оркестратора для механической, ограниченной и независимо проверяемой sidecar-задачи, но не для
phase owner. Фактическую пару и rationale всегда записывай в trace.
Ранги обычной маршрутизации: `Luna < Terra < Sol` и
`low < medium < high < xhigh < max`.

### Risk override и capability floor

- Повышай family и/или effort для архитектуры, security/compliance, сложной диагностики,
  неоднозначного source-of-truth и решения, закрывающего gate.
- Понижай baseline только для механической, ограниченной и независимо проверяемой sidecar-задачи,
  которая не владеет фазовым артефактом и не закрывает gate.
- Сохраняй capability floor активной роли после всех overrides.
- Для blocking/acceptance-bound Reviewer сравни фактически выбранные после overrides пары автора и
  Reviewer. Ранг Reviewer MUST быть не ниже автора по обеим осям. Если schema не позволяет это
  обеспечить, останови review launch с blocker; не понижай автора или Reviewer.
- Если `agent_type` переопределяет переданные значения, по возможности проверь effective pair. При
  нарушении floor зафиксируй deviation/blocker.

### GPT-only guard против оверинжиниринга

Это ограничение применяется только к моделям GPT/Codex family, включая Sol, Terra и Luna. Не
переноси его автоматически на Claude, Kimi или другие семейства.

Перед фиксацией спецификации, архитектуры, декомпозиции или реализации GPT-агент обязан скептически
проверить собственное решение: каждое новое звено, абстракция, сервис, реестр, DSL, слой или
технология должны закрывать прямое требование либо доказанный риск. Если тот же результат достигается
существующим механизмом с меньшей сложностью и без ухудшения критериев приёмки, выбирается более
простое решение. «Возможное развитие в будущем» без текущего требования не является обоснованием.

Этот guard не разрешает понижать качество, пропускать обязательные проверки или игнорировать
архитектурную границу; он запрещает только неподтверждённое усложнение.

### Schema fallback: monotonic и fail-closed

Если точная пара недоступна, разрешено только явно безопасное монотонное отображение внутри
GPT-5.6 family: `Luna → Terra → Sol` при том же или более высоком поддерживаемом effort либо переход
к следующему поддерживаемому уровню обычной ladder в том же или более высоком family. Понижение по
любой оси и субъективная «ближайшая» модель запрещены.

`gpt-5.5`, `gpt-5.4` и другие поколения не ранжируй относительно GPT-5.6 и не используй как fallback
без отдельного заранее утверждённого mapping. Если mapping не даёт пару не ниже capability floor, а
для Reviewer — также фактической пары автора, верни blocker.

### `max` и `ultra`

`max` — верхняя ступень обычной ladder, если её поддерживает выбранная модель. `ultra` не входит в
baseline или обычную ladder, не является fallback либо автоматическим усилением owner/Reviewer/
consultation. Применяй `ultra` только для отдельно и явно одобренного broad read-only multi-agent
spike: зафиксируй цель, slot/token budget, schema support и запрет artifact ownership и gate verdict.
Без такого approval используй обычную ladder либо верни blocker; auto-ultra запрещён.

## Support child и bounded consultation

Support child выполняет узкую вспомогательную задачу, прямо предусмотренную профилем, например
`analyst → Explore`. Он не принимает owner decision, не получает ownership и не считается
consultation, но соблюдает весь runtime contract этого правила.

First-level owner MAY запустить одну bounded read-only consultation только при проверяемом триггере:

1. две правдоподобные трактовки source-of-truth меняют итог;
2. решение затрагивает архитектуру, security/compliance или acceptance gate;
3. после исчерпания штатной проверки сохраняется высокорисковая гипотеза;
4. owner обязан принять решение вне своей основной специализации.

Общая просьба «проверь всё», экономия времени и передача собственной фазовой работы не являются
триггерами. Допустима только глубина `root → owner → consultant`, то есть `depth=1` относительно
owner. Consultant не spawn'ит других агентов.

Consultant должен быть сильнее для конкретного вопроса: для owner на Luna — Terra или Sol; для
owner на Terra — Sol; для owner на Sol — любой поддерживаемый effort обычной ladder, строго более
высокий, чем фактический effort owner, вплоть до `max`. Не понижай вторую ось. Если усиление
невозможно, consultation не запускается и возвращает blocker. `ultra` допускается только по
отдельному broad-spike approval из предыдущего раздела.

## Fork policy и nested handoff

Всегда передавай `fork_turns: "none"`. Запрещены omitted/empty, `"all"`, числовой partial fork и
неподдерживаемый `fork_context`. Hidden thread history не является source of truth.

Handoff для любого child должен содержать роль, задачу, scope/non-goals, read/write boundaries,
confirmed facts и решения, relevant paths, expected output, required rules, escalation triggers,
time budget, признаки прогресса и cleanup obligations.

Для nested child дополнительно обязательно укажи конкретный вопрос, допустимые read paths,
write prohibition, запрет дальнейшего spawn и условия возврата. Peer-to-peer phase routing через
handoff запрещён.

## Ownership и gates

Consultant возвращает рекомендацию только своему parent owner и остаётся read-only относительно
owner artifact. Он не редактирует фазовый артефакт, не принимает финальное решение, не закрывает
единый acceptance gate через Reviewer-controller/review-swarm, не получает ownership и не
координирует независимых owners.

Parent owner проверяет ответ по source-of-truth, принимает или отклоняет рекомендацию и остаётся
ответственным автором результата.

## Slot budget

Перед nested spawn посчитай root и все active/resident threads в
`max_concurrent_threads_per_session`. У одного owner одновременно допускается не более одного
consultant. Consultation не вытесняет обязательного phase owner/Reviewer; при конкуренции сохраняй
один свободный slot для gate/recovery. При исчерпании бюджета отложи или отмени consultation либо
верни blocker. Safety cap автоматически не повышай.

## Health-check

Регулярно контролируй запущенных исполнителей: сообщения, процессы, артефакты, логи, progress и
cleanup. Механизм и периодичность задаёт навык среды запуска (для Herdr — навык `herdr` / сторож
исполнителей); собственные таймеры поверх него не ставятся. Инициатор отвечает за запущенного child: parent owner — за support child или
consultant, root orchestrator — за owner и видимость consultation через trace. Не создавай для
consultation отдельный бесконечный wait-loop.

Если работа завершена по артефактам без ответа — interrupt и зафиксируй результат. После двух
последовательных проверок без прогресса, выхода за scope или превышения time budget примерно в 1,5
раза — interrupt и один более узкий restart с фактами. Третий цикл ожидания запрещён. Запиши
`HEALTHCHECK_ANOMALY`, `INTERRUPT`, `RESTART` или `SCOPE_CORRECTION`.

## Self-check и recovery

Потребуй от child не ждать бесконечно; при ошибке или непройденном pre-run gate классифицировать
результат, выполнить cleanup и вернуться; после существенного шага проверять, достаточно ли уже
результата или blocker; не начинать внеплановый обход после failure; по исчерпании бюджета вернуть
partial result.

Если нет `agents.spawn_agent`, `agents.list_agents`, `agents.wait_agent` или schema не позволяет
явно передать `model` и `reasoning_effort`, не переходи на legacy-вызов и не продолжай medium/full
flow в solo mode. Зафиксируй blocker и запроси отдельное подтверждение на изменение пользовательской
runtime-конфигурации. Без подтверждения не меняй `~/.codex/config.toml`; после согласованного
изменения предупреди, что schema обновится только в новой Codex-сессии.

## Trace expectations

Для каждого spawn, включая owner, support child и consultant, зафиксируй:

- parent/owner, workstream/task name, task/session id и depth;
- цель, а для consultation — объективный trigger;
- переданные и, когда доступно, effective `model` и `reasoning_effort`, а также `fork_turns`;
- отдельное rationale обеих осей и фактический slot count;
- result consultant, решение owner и краткую проверку по source-of-truth;
- deviations/blockers и события health-check/recovery.
