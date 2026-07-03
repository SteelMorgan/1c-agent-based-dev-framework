# Сабагенты фреймворка 1C Agent-Based Dev

Директория содержит описания специализированных сабагентов, используемых в рамках фреймворка разработки 1C:Enterprise на основе агентов.

---

## Сабагенты

Маппинг **роль → модель/tier** — канонический реестр в
[`agent-development-ext`](../skills/framework-meta/agent-development-ext/SKILL.md) §2
«Роли и модели фреймворка». Не дублируется здесь во избежание расхождения.

| Агент | Фаза | Роль | Чтение |
|---|:---:|---|:---:|
| [explorer](./explorer.md) | 0 | Исследует кодовую базу, строит графы вызовов, собирает данные для классификации задачи | ✅ |
| [analyst](./analyst.md) | 1 | Анализирует требования, пишет спецификацию MADR 4.0 + RFC 2119 | ✅ |
| [architect](./architect.md) | 2 | Технический дизайн, декомпозиция в Task Breakdown JSON | ✅ |
| [scenario-author](./scenario-author.md) | 3a | Конвертирует intent-сценарии из спецификации в исполняемые `.feature` Vanessa Automation | ❌ |
| [developer-tests](./developer-tests.md) | 3b | Пишет unit-тесты по спецификации до реализации (Red phase TDD) | ❌ |
| [scenario-coder](./scenario-coder.md) | 3c | Делает `.feature`-сценарии исполняемыми — подбирает/реализует шаги Vanessa, не трогая прод-код | ❌ |
| [developer-code](./developer-code.md) | 3d | Реализует BSL-код для прохождения тестов (Green phase TDD) | ❌ |
| [tester](./tester.md) | 4 | Дополняет покрытие edge-cases, запускает полный прогон, диагностирует причины падений | ❌ |
| [reviewer](./reviewer.md) | * | Ревьюит артефакты (BLOCK/WARN/INFO) — вызывается после каждой фазы | ✅ |
| [debugger](./debugger.md) | * | Расследует баги в рантайме по `bug-report.json`, чинит локально или возвращает вердикт | ❌ |

---

## Описание агентов

### 🔍 explorer
Исследует кодовую базу: находит затронутые модули, строит графы вызовов (входящие + исходящие зависимости), выявляет транзитивные зависимости. Возвращает фактические данные оркестратору — тот классифицирует задачу и передаёт артефакты Explorer аналитику и архитектору.

### 📋 analyst
Анализирует бизнес-требования, опираясь на данные Explorer (список модулей, графы вызовов). Формирует структурированную спецификацию в формате MADR 4.0 + RFC 2119 с разделом Test Plan. Если требуются уточнения — сохраняет вопросы в контекст-файл и останавливается; оркестратор спрашивает пользователя.

### 🏗️ architect
По утверждённой спецификации и данным Explorer (графы зависимостей) проектирует техническое решение. Описывает архитектурные решения, обоснование trade-offs, определяет границы модулей и интерфейсы. Декомпозирует задачу в Task Breakdown JSON.

### 📝 scenario-author
Конвертирует intent-сценарии из раздела Acceptance Scenarios спецификации в исполняемые `.feature`-файлы Vanessa Automation. Работает параллельно с developer-tests (Phase 3b). Использует существующую библиотеку шагов Vanessa, анализирует формы при необходимости. Не запускает сценарии — это ответственность Tester.

### 👨‍💻 developer-tests
Пишет YaxUnit unit-тесты строго по спецификации (все MUST-сценарии из Test Plan) **до** реализации кода — Red phase TDD. Не видит и не влияет на код реализации. Тесты должны падать на момент сдачи.

### 🧩 scenario-coder
Делает `.feature`-сценарии из Phase 3a исполняемыми: подбирает существующие шаги Vanessa или реализует новые через `@exportscenarios`-подсценарии (либо, как escape hatch, BSL-шаги в `vanessa-tests/support/`). Работает ПОСЛЕ приёмки scenario-author (3a) — developer-tests (3b) может идти параллельно, ДО developer-code (3d). Red-гейт: сценарии MUST падать из-за отсутствия прод-кода, а не из-за нерезолвящегося шага или мока. Не редактирует прод-BSL и не пишет unit-тесты.

### 👨‍💻 developer-code
Реализует BSL-код по утверждённой спецификации, техническому дизайну и Task Breakdown JSON так, чтобы тесты developer-tests прошли — Green phase TDD. Не пишет и не изменяет тест-модули. При падении тестов — сохраняет статус `test_failure` в контекст-файл и останавливается.

### 🧪 tester
Дополняет тестовое покрытие: edge-cases, негативные сценарии, интеграционные и регрессионные тесты. Запускает полный прогон, анализирует журнал регистрации. Диагностирует причины падений: `test_error` (исправляет сам) или `implementation_error` (сохраняет в контекст-файл, останавливается).

### 🔎 reviewer
Ревьюит любой артефакт относительно цели задачи. Каждый вызов — **изолированная сессия** для одного типа артефакта (scope: `spec` / `arch` / `bdd` / `bdd-steps` / `tests` / `code` / `tester` / `debug`). Классифицирует находки по уровням **BLOCK / WARN / INFO**. Не реализует исправления — только указывает направление.

### 🐞 debugger
Расследует баги в рантайме: принимает `bug-report.json` от любого сабагента, строит граф вызовов и трассу исполнения через DAP/MCP-отладчик или `agent-debug` (пробы через ЖР), проходит цикл гипотез (≤ 5, расширение до 8 при высокой уверенности). Либо чинит локально с верификацией (в границах лимита — см. `self-recovery-limits`), либо возвращает оркестратору вердикт для маршрутизации профильному агенту, либо эскалирует пользователю. Всегда завершает работу полной очисткой временных инструментальных вставок.

---

## Принципы взаимодействия

```
┌─────────────┐
│Пользователь │
└──────┬──────┘
       │ задача / ответы на вопросы
       ▼
┌─────────────┐   запускает агентов    ┌─────────────┐
│Оркестратор  │ ──────────────────────► │  Сабагент   │
│             │ ◄────────────────────── │             │
└─────────────┘   читает context-файл  └─────────────┘
```

- **Сабагенты не общаются напрямую** — только через файлы в `task_dir/.context/` и `task_dir/.spec/`
- **Только оркестратор** задаёт вопросы пользователю
- **Сабагент при неопределённости** — сохраняет статус в `task_dir/.context/{role}-context.md` и останавливается; оркестратор читает файл и решает следующий шаг
- **Контекст между запусками** — каждый агент при старте читает свой `task_dir/.context/{role}-context.md`, продолжает с места остановки

---

## Рабочий поток (Full Cycle)

```
                    ┌─────────────────────────────────────────────────────────────┐
                    │                      ОРКЕСТРАТОР                            │
                    │            (task_dir/.context/sessions.json)               │
                    └──────────────────────────┬──────────────────────────────────┘
                                               │
                    ┌──────────────────────────▼──────────────────────────────────┐
                    │  Phase 0: Исследование кодовой базы                         │
                    │                                                              │
                    │  🔍 Explorer                                                 │
                    │     → список модулей, графы вызовов, глубина зависимостей   │
                    │     → explorer-context.md                                    │
                    │     → оркестратор классифицирует: простая / средняя / сложная│
                    └──────────┬───────────────────────────┬────────────────────── ┘
                               │ средняя / сложная         │ простая
                               ▼                           ▼
                          full-cycle                   quick-fix
                               │
          ┌────────────────────▼───────────────────────────────────┐
          │  Phase 1: Анализ и спецификация                        │
          │                                                         │
          │  📋 Analyst                                             │
          │     вход: задача + explorer-context.md                  │
          │     → task_dir/.spec/spec.md (MADR 4.0 + RFC 2119 + Test Plan) │
          │     → если clarification_needed: ⏸ спрашивает оркестр. │
          │                      │                                  │
          │                      ▼                                  │
          │  🔎 Reviewer [scope=spec]                               │
          │     → BLOCK? → возврат Analyst (макс. 3 итерации)      │
          │     → OK? → Phase 2                                     │
          └────────────────────┬───────────────────────────────────┘
                               │
          ┌────────────────────▼───────────────────────────────────┐
          │  Phase 2: Архитектура                                   │
          │                                                         │
          │  🏗️ Architect                                           │
          │     вход: task_dir/.spec/spec.md + task_dir/.context/explorer-context.md │
          │     → task_dir/.spec/technical-design.md + task_dir/.context/task-breakdown.json │
          │     → если clarification_needed: ⏸ спрашивает оркестр. │
          │                      │                                  │
          │                      ▼                                  │
          │  🔎 Reviewer [scope=arch]                               │
          │     → BLOCK? → возврат Architect (макс. 3 итерации)    │
          │     → OK? → ⏸ Ожидание подтверждения пользователя      │
          │     [опц.] 📡 Codex-review (сложная архитектура)       │
          └────────────────────┬───────────────────────────────────┘
                               │ ✅ Пользователь подтвердил
          ┌────────────────────▼───────────────────────────────────┐
          │  Phase 3a + 3b: ПАРАЛЛЕЛЬНО                            │
          │                                                         │
          │  ┌───────────────────────────────────────────────────┐  │
          │  │ 📝 Scenario-Author (Phase 3a: BDD, intent)         │  │
          │  │    вход: spec.md (Acceptance Scenarios)            │  │
          │  │    → .feature файлы (шаги-заглушки unknown_step)   │  │
          │  │    → 🔎 Reviewer [scope=bdd] → BLOCK? → возврат   │  │
          │  └───────────────────────────────────────────────────┘  │
          │  ┌───────────────────────────────────────────────────┐  │
          │  │ 👨‍💻 Developer-Tests (Phase 3b: Red TDD)            │  │
          │  │    вход: spec.md (Test Plan) + task-breakdown.json│  │
          │  │    → test-модули .bsl (ПАДАЮТ)                    │  │
          │  │    → 🔎 Reviewer [scope=tests] → BLOCK? → возврат │  │
          │  └───────────────────────────────────────────────────┘  │
          │  Оба MUST завершиться перед Phase 3c                    │
          └────────────────────┬───────────────────────────────────┘
                               │
          ┌────────────────────▼───────────────────────────────────┐
          │  Phase 3c: Реализация шагов Vanessa (Red-executable)   │
          │                                                         │
          │  🧩 Scenario-Coder                                      │
          │     вход: technical-design.md (контракты) + .feature 3a │
          │     → .feature со шагами (@exportscenarios), опц. support/ │
          │     → Red-гейт: сценарии падают на прод-коде, не на шаге│
          │                      │                                  │
          │                      ▼                                  │
          │  🔎 Reviewer [scope=bdd-steps]                          │
          │     → BLOCK? → возврат Scenario-Coder (макс. 3 итерации)│
          │     → clarification_needed (нет API в дизайне)? →      │
          │       возврат в Phase 2 к Architect                     │
          │     → OK? → Phase 3d                                    │
          └────────────────────┬───────────────────────────────────┘
                               │
          ┌────────────────────▼───────────────────────────────────┐
          │  Phase 3d: Реализация (Green TDD)                      │
          │                                                         │
          │  👨‍💻 Developer-Code                                     │
          │     вход: spec + technical-design + task-breakdown.json │
          │           + test-модули из 3b + Red-executable .feature из 3c │
          │     → BSL-модули + XML метаданных (тесты ПРОХОДЯТ)     │
          │     → test_failure? → есть bug-report.json? →          │
          │       🐞 Debugger → вердикт → маршрутизация автору      │
          │                      │                                  │
          │                      ▼                                  │
          │  🔎 Reviewer [scope=code]                               │
          │     → BLOCK? → возврат Developer-Code                   │
          │     [опц.] 📡 Codex-review (сложный код)               │
          │     → OK? → Phase 4                                     │
          └────────────────────┬───────────────────────────────────┘
                               │
          ┌────────────────────▼───────────────────────────────────┐
          │  Phase 4: Покрытие и регрессия                         │
          │                                                         │
          │  🧪 Tester                                              │
          │     вход: код из 3d + unit-тесты из 3b + .feature из 3a/3c + spec.md │
          │     → дополнительные тест-модули .bsl                  │
          │     → task_dir/.spec/test-report.md                     │
          │     → implementation_error? → ⏸ оркестратор →         │
          │       возврат Developer-Code с описанием бага           │
          │                      │                                  │
          │                      ▼                                  │
          │  🔎 Reviewer [scope=tester]                             │
          │     → BLOCK? → возврат Tester                           │
          │     → OK? → результат пользователю                     │
          └────────────────────────────────────────────────────────┘

   🐞 Debugger — вызывается вне линейного потока в любой фазе, когда сабагент
   создаёт task_dir/.context/bugs/<bug-id>.json (status: open):
      bug-report.json → 🐞 Debugger (расследование, ≤5 гипотез, +3 при уверенности)
         → fixed_locally → 🔎 Reviewer [scope=debug] (макс. 1 итерация) → OK? → возврат в поток
         → returned_to_author → оркестратор маршрутизирует профильному агенту
         → escalated_to_user → оркестратор спрашивает пользователя
```

---

## Коммуникация через context-файлы

| Статус в context-файле | Кто пишет | Действие оркестратора |
|------------------------|-----------|----------------------|
| `completed` | Любой агент | Перейти к следующей фазе |
| `clarification_needed` | Analyst, Architect, Scenario-Coder | Задать вопросы пользователю (или вернуть к Architect), записать ответы, перезапустить агента |
| `test_failure` | Developer-Code | Если есть `bug-report.json` → Debugger; иначе требовать bug-report |
| `test_error` | Tester | Tester исправляет сам, оркестратор не вмешивается |
| `implementation_error` | Tester | Вернуть Developer-Code с описанием бага |
| `block_issued` | Reviewer | Вернуть артефакт автору с замечаниями (макс. 3 итерации; для `scope=debug` — макс. 1 итерация) |
| `open` / `fixed_locally` / `returned_to_author` / `escalated_to_user` | Debugger (`bug-report.json`) | Запустить Debugger / принять фикс на ревью / маршрутизировать автору / эскалировать пользователю |

---

## Артефакты task_dir

```
tasks/TASK-001-название/
├── .context/
│   ├── sessions.json             ← реестр agentId всех агентов (оркестратор)
│   ├── orchestrator-context.md   ← лог оркестратора
│   ├── explorer-context.md       ← Phase 0
│   ├── analyst-context.md        ← Phase 1
│   ├── architect-context.md      ← Phase 2
│   ├── task-breakdown.json       ← Phase 2
│   ├── scenario-author-context.md← Phase 3a
│   ├── developer-tests-context.md← Phase 3b
│   ├── scenario-coder-context.md ← Phase 3c
│   ├── developer-code-context.md ← Phase 3d
│   ├── tester-context.md         ← Phase 4
│   ├── reviewer-context-spec.md  ← Reviewer Phase 1
│   ├── reviewer-context-arch.md  ← Reviewer Phase 2
│   ├── reviewer-context-bdd.md   ← Reviewer Phase 3a
│   ├── reviewer-context-tests.md ← Reviewer Phase 3b
│   ├── reviewer-context-bdd-steps.md ← Reviewer Phase 3c
│   ├── reviewer-context-code.md  ← Reviewer Phase 3d
│   ├── reviewer-context-tester.md← Reviewer Phase 4
│   ├── reviewer-context-debug.md ← Reviewer scope=debug (после fixed_locally)
│   ├── bugs/<bug-id>.json        ← bug-report(ы), заводятся любым сабагентом
│   └── debug/<bug-id>/           ← Debugger: debug-report.md, call-graph.md,
│                                    instrumentation-plan.md, trace-run-N.md
└── .spec/
    ├── spec.md                   ← Phase 1
    ├── technical-design.md       ← Phase 2
    ├── test-report.md            ← Phase 4
    └── final-report.md           ← финальный отчёт оркестратора
```

BSL-код, тест-модули и XML метаданных хранятся в **кодовой базе проекта** (не в task_dir).

---

## Шаблон

Файл [_template-agent.md](./_template-agent.md) содержит структуру и описание полей для создания новых сабагентов.
