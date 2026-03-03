---
name: orchestrator
description: The orchestrator routes tasks and manages the workflow phases.
---



# Orchestrator: Meta-workflow

> **The Orchestrator** is a meta-workflow that routes tasks, selects workflows, and coordinates agent interactions.

---

## Purpose

The orchestrator does not complete tasks itself. It:
1. Classifies incoming tasks
2. **Initializes the task directory** (`task_dir`)
3. Selects a workflow (quick-fix or full-cycle)
4. Assigns a model tier to each agent
5. Manages review loops
6. Passes artifacts between agents while explicitly providing `task_dir`
7. Identifies user interaction points
8. **Maintains the agent session registry** (`task_dir/sessions.json`) for possible resumption
9. **Runs codex-review** for complex artifacts as an independent second opinion
10. **Maintains a context log** (`task_dir/orchestrator-context.log`) — a minimalist record of key events to resume the task
11. **Generates the final report** (`task_dir/report.md`) after the task completes

---

## FREE mode: orchestrator disabled

**IMPORTANT:** In FREE mode (without full-cycle) the orchestrator is **not active**.

The agent works directly using:
- Skills
- Rules
- Tool-registry

The user presents a task, and the agent solves it in free mode. Cross-review is optional. The phases are not enforced.

---

## Orchestrator responsibilities

### 1. Task classification

Determines task difficulty and selects a workflow via the [decision tree](#decision-tree-for-classification).

### 2. Model routing

IMPORTANT!!! When launching subagents through Task, always specify the `model` parameter. Each agent has a predefined model (the `model` field in the frontmatter):
- Economy — Explorer
- Mid/High — Developer, Tester
- High/Premium — Architect, Analyst
- Premium — Reviewer (spec, Task Breakdown JSON decomposition, architecture)
- High — Reviewer (code, tests)
**NEVER** launch a subagent without an explicit `model`.

### 3. Review loop management

- Tracks iterations (max. 3)
- When BLOCK occurs → returns to the author with comments
- If 3+ BLOCKs without resolution → escalate to the user
- Ensures reviewer tier ≥ author tier

**Returns between agents through the orchestrator:**

Subagents never communicate directly — every return goes through the orchestrator.

| Situation | Who signals | Orchestrator action |
|----------|-------------------|-----------------------|
| Reviewer placed BLOCK on an artifact | Reviewer | Return the artifact to the phase author with comments |
| Tester found an implementation bug | Tester (tag `implementation_error`) | Return to Developer-Code with details: which test, expected result, actual result |
| Tester found an error in their own test | Tester (tag `test_error`) | Tester fixes it themselves; orchestrator does not intervene |
| Developer-Code: tests failed (tag `test_failure`) | Developer-Code | Launch Reviewer to determine cause: if a test bug → return to Developer-Tests; if code bug → return to Developer-Code |
| Developer-Code: `test_failure` + `suspected_test_error` | Developer-Code | Launch Reviewer arbitration: compare spec + technical design + tests + code, record in `reviewer-context-code.md` which artifact is faulty (`tests` or `code`). Then orchestrator routes the task per the Reviewer summary: either to Developer-Tests or Developer-Code; the decision is recorded in `orchestrator-context.log`. |
| 3+ iterations without clearing BLOCK | Reviewer / any agent | Escalate to user, stop |

### 4. Artifact handling

- Passes the output of one phase to the next, **explicitly giving `task_dir`** to each subagent
- Stores/restores context between sessions (if supported by the adapter)
- Packages [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST] for the reviewer, where for Phase 2 [ARTIFACT] includes the Technical Design and the Task Breakdown JSON decomposition

**Storage separation:**

| Data type | Storage location |
|------------|--------------|
| Specification, technical design, Task Breakdown JSON | `task_dir/` |
| Review results | `task_dir/` |
| Test reports | `task_dir/` |
| Agent session registry | `task_dir/sessions.json` |
| BSL code, tests, XML metadata | The project code base (a separate directory) |

**`task_dir` structure:**

```
tasks/
└── TASK-001-название/
    ├── orchestrator-context.log   ← Orchestrator (context log, maintained continuously)
    ├── report.md                  ← Orchestrator (final report, generated after completion)
    ├── sessions.json              ← Orchestrator (registry of all agentIds)
    ├── explorer-context.md        ← Explorer (Phase 0)
    ├── analyst-context.md         ← Analyst (Phase 1)
    ├── spec.md                    ← Analyst (Phase 1)
    ├── architect-context.md       ← Architect (Phase 2)
    ├── technical-design.md        ← Architect (Phase 2)
    ├── task-breakdown.json        ← Architect (Phase 2)
    ├── developer-tests-context.md ← Developer-Tests (Phase 3a)
    ├── developer-code-context.md  ← Developer-Code (Phase 3b)
    ├── tester-context.md          ← Tester (Phase 4)
    ├── reviewer-context-spec.md   ← Reviewer (Phase 1)
    ├── reviewer-context-arch.md   ← Reviewer (Phase 2)
    ├── reviewer-context-tests.md  ← Reviewer (Phase 3a)
    ├── reviewer-context-code.md   ← Reviewer (Phase 3b)
    ├── reviewer-context-tester.md ← Reviewer (Phase 4)
    └── test-report.md             ← Tester (Phase 4)
```

### 5. Agent session registry (`sessions.json`)

The orchestrator maintains `task_dir/sessions.json` — a registry of all launched agentIds. It is used to `resume` when rerunning the same agent (BLOCK → fix → re-review, clarification round, etc.).

**Structure:**

```json
{
  "explorer":         "agent-xxx",
  "analyst":          "agent-yyy",
  "architect":        "agent-zzz",
  "developer-tests":  "agent-aaa",
  "developer-code":   "agent-bbb",
  "tester":           "agent-ccc",
  "reviewer-spec":    "agent-ddd",
  "reviewer-arch":    "agent-eee",
  "reviewer-tests":   "agent-fff",
  "reviewer-code":    "agent-ggg",
  "reviewer-tester":  "agent-hhh"
}
```

**Protocol:**
- After each agent launch — record the agentId under the appropriate key
- When rerunning — read `sessions.json`, attempt `resume agentId`; if the agentId is stale — start a new run and update the entry
- Reviewer is launched separately for each scope (`reviewer-spec`, `reviewer-arch`, etc.) — each scope has its own key

### 6. Codex-review as an independent second opinion

The orchestrator launches `codex-review` (CLI) **over the main Reviewer** for complex artifacts. The Reviewer does not launch it themselves — it is the orchestrator's responsibility.

**When to launch:**

| Condition | Action |
|---------|----------|
| Architectural decision with trade-offs (Phase 2) | Run codex-review after the Reviewer |
| Complex BSL code (> 5 files, > 300 lines) | Run codex-review after the Reviewer |
| Reviewer issued BLOCK and the author disputes it | Run codex-review as a tiebreaker |
| Upon user request `/review-gpt`, `/review-all` | Launch immediately |

**How to launch:** see the `codex-review` skill.

### 5. User interaction points

| Point | Action |
|-------|----------|
| Phase 1: Analyst returned `clarification_needed` | Ask the user all questions at once, collect answers, rerun the Analyst with clarifications (max. 1 round) |
| Phase 2: Architect returned `clarification_needed` | Ask the user all questions at once, collect answers, rerun the Architect with clarifications (max. 1 round) |
| Phase 2 (architecture) | Approval gate — wait for user confirmation |
| Escalation (3 BLOCKs) | Request a decision from the user |
| New metadata object | Protocol “agent → user”: instruction → wait for creation → verification |

**Clarification round protocol:**

```
Агент → clarification_needed
  │  (вопросы записаны в {role}-context.md → Pending Questions)
  ▼
Оркестратор читает {role}-context.md → задаёт вопросы пользователю
  │
  ▼
Пользователь отвечает
  │
  ▼
Оркестратор записывает ответы в {role}-context.md → User Answers
  │
  ├── agentId актуален? → resume (оптимизация, та же сессия)
  └── agentId устарел?  → новый запуск агента с task_dir
                           (агент читает контекст сам на шаге 1)
  │
  ▼
Агент продолжает с сохранённым контекстом, не повторяя исследование
  │
  ▼
Спецификация / тех. дизайн готовы
(если снова clarification_needed → эскалация пользователю,
 не третий раунд — агент MUST писать артефакт с допущениями)
```

---

## Orchestrator protocol

### Sequence of steps

```
1. Получить задачу от пользователя
   ↓
2. Инициализировать task_dir:
   - Если передан номер/путь задачи → использовать существующий каталог
   - Иначе → создать tasks/TASK-XXX-название/
   - Создать/прочитать task_dir/sessions.json
   - Создать/дополнить task_dir/orchestrator-context.log: записать событие START с датой-временем и текстом задачи
   ↓
3. Запустить Explorer для исследования кодовой базы
   - Explorer возвращает: список затронутых модулей, графы вызовов (входящие + исходящие),
     глубину зависимостей, количество точек вызова
   - Сохранить артефакт Explorer в task_dir (explorer-context.md)
   - Записать agentId Explorer в sessions.json → ключ "explorer"
   - На основе этих данных классифицировать задачу (простая / средняя / сложная)
   ↓
4. По результату классификации выбрать воркфлоу:
   - Простая → quick-fix.md
   - Средняя/Сложная → full-cycle.md
   ↓
5. Для каждой фазы выбранного воркфлоу:
   a. Запустить агента (модель задана в agent frontmatter)
      - Оптимизация: прочитать sessions.json; если agentId для этой роли есть → попробовать resume
      - После запуска: записать agentId в sessions.json → ключ роли агента
   b. Передать входные данные + явно task_dir
      - **Для Phase 1 (Analyst):** задача + `explorer-context.md` (список модулей, графы вызовов)
      - **Для Phase 2 (Architect):** утверждённая спека + `explorer-context.md` (графы вызовов, зависимости)
   c. Собрать выходной артефакт → сохранить в task_dir
      - Записать в orchestrator-context.log: событие завершения фазы (агент, результат — OK / BLOCK / clarification_needed)
   d. Если требуется ревью:
      - Запустить Reviewer с [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST]+[review_scope]
      - Передать `review_scope` явно: "spec" | "arch" | "tests" | "code" | "tester"
      - Для Phase 2 в [ARTIFACT] обязательно включить Technical Design + Task Breakdown JSON-декомпозицию
      - Записать agentId Reviewer в sessions.json → ключ "reviewer-{scope}"
      - Сохранить результат ревью в task_dir (reviewer-context-{scope}.md)
      - Обработать результат (pass / iterate / escalate)
      - При необходимости: запустить codex-review как второе мнение (см. раздел 6)
   e. Если агент вернул `clarification_needed` (Phase 1 — Analyst, Phase 2 — Architect):
      - Прочитать `{role}-context.md` из task_dir — там список вопросов
      - Задать ВСЕ вопросы пользователю одним блоком
      - Дождаться ответов
      - Записать ответы в секцию `User Answers` файла `{role}-context.md`
      - Повторно запустить агента с исходной задачей + task_dir
        (агент сам прочитает контекст и ответы при старте)
      - Оптимизация: если agentId предыдущего запуска актуален —
        использовать resume вместо нового запуска
      - Если снова clarification_needed → эскалация пользователю (не повторять)
   f. Передать артефакт на следующую фазу
   ↓
6. Сформировать итоговый отчёт task_dir/report.md (см. формат ниже)
   - Записать в orchestrator-context.log: событие DONE
   ↓
7. Передать результат пользователю
```

### Review handling detail for step 4d

| Review result | Action |
|-----------------|----------|
| OK (no BLOCK) | Move to the next phase. WARN/INFO — optional per author (can fix later). |
| BLOCK, iteration ≤ 3 | Return the artifact to the author with comments. Repeat the cycle. Task Breakdown JSON follows the same iteration rules. |
| BLOCK, iteration > 3 | Escalate to the user. Stop. For Task Breakdown JSON: >3 iterations are forbidden, a user decision is required. |
| Phase 2: OK | Stop. Request user confirmation. After confirmation — proceed to Phase 3. |

---

## Context log (orchestrator-context.log)

A minimalist log of key events. It is maintained continuously — enabling resuming the task from the same point if orchestration stops.

**Entry format:**
```
[YYYY-MM-DD HH:MM] EVENT: description
```

**Key events to record:**

| Event | When to write |
|---------|-------------|
| `START` | Task start, task text in one line |
| `PHASE` | Start of every phase (Explorer, Analyst, Architect, Developer, Tester, Reviewer) |
| `DONE_PHASE` | Phase completion with result (OK / BLOCK / clarification_needed) |
| `CLARIFICATION` | Request for user clarification |
| `USER_INPUT` | Received user answer |
| `REVIEW_BLOCK` | Reviewer issued BLOCK, iteration number |
| `ESCALATE` | Escalation to the user |
| `RESUME` | Resuming the task after a stop |
| `DONE` | Task completed |

**Example:**
```
[2026-03-02 10:15] START: Добавить реквизит "ДатаОтгрузки" в документ Реализация
[2026-03-02 10:16] PHASE: Explorer — исследование кодовой базы
[2026-03-02 10:18] DONE_PHASE: Explorer — OK, задача классифицирована как СРЕДНЯЯ
[2026-03-02 10:18] PHASE: Analyst — формирование спецификации
[2026-03-02 10:22] DONE_PHASE: Analyst — clarification_needed
[2026-03-02 10:23] CLARIFICATION: Задан вопрос пользователю: тип реквизита Дата или ДатаВремя?
[2026-03-02 10:25] USER_INPUT: Дата
[2026-03-02 10:28] DONE_PHASE: Analyst — OK, spec.md сформирован
[2026-03-02 10:29] PHASE: Reviewer (scope: spec)
[2026-03-02 10:31] DONE_PHASE: Reviewer spec — OK
...
[2026-03-02 11:45] DONE
```

**Rules:**
- Do not duplicate artifact content — log only the event.
- One line per event maximum.
- When resuming a task — append to the existing log rather than overwrite.

---

## Final report (report.md)

Created by the orchestrator after all task phases complete.

**Format:**

```markdown
# Отчёт: TASK-XXX-название

## Новые объекты метаданных
- Справочник.НовыйСправочник
- Документ.НовыйДокумент.Форма.ФормаДокумента

## Изменённые объекты
<!-- Объекты из раздела "Новые" сюда не включаются -->
- Документ.Реализация — добавлен реквизит ДатаОтгрузки
- РегистрНакопления.ТоварыНаСкладах — добавлен новый отбор
- ОбщийМодуль.РаботаСДокументами — изменена процедура ПровестиДокумент

## Что сделано
Краткое семантическое описание в произвольной форме — что было реализовано,
какую бизнес-задачу решает, какие ключевые решения приняты.
```

**Rules:**
- If an object appears in "New" — do not duplicate it in "Changed".
- List metadata objects using 1С notation `Type.Name` (e.g., `Справочник.Контрагенты`).
- Subobjects (forms, attributes, tabular sections) are indicated with a dot: `Документ.Реализация.Форма.ФормаДокумента`.
- The "What was done" section is free-form text, 3–7 sentences.

---

## Decision tree for classification

```
Задача от пользователя
         │
         ├─► Требуются новые объекты метаданных?
         │        Да → СЛОЖНАЯ → full-cycle
         │
         ├─► Изменяется поток данных / архитектура?
         │        Да → СЛОЖНАЯ → full-cycle
         │
         ├─► Исправление бага в одном файле?
         │        Да → ПРОСТАЯ → quick-fix
         │
         ├─► Всё остальное
         │        → СРЕДНЯЯ → full-cycle
         │
         └─► (По умолчанию при неопределённости)
                   → СРЕДНЯЯ → full-cycle
```

### Tree rules

| Question | "Yes" answer | Difficulty |
|--------|------------|------------|
| Are new metadata objects required (directories, documents, registers, forms)? | Yes | Difficult |
| Is the data flow or solution architecture changing? | Yes | Difficult |
| Is this a bug fix in a single file? | Yes | Simple |
| Everything else | — | Medium |

**When uncertain:** treat as Medium and use full-cycle.

---

## Orchestrator diagram

```
  ┌──────────┐
  │  Задача  │
  └─────┬────┘
        ▼
  ┌──────────────────────┐
  │ Explorer (Economy)   │
  │ классификация задачи │
  └──────────┬───────────┘
             │
     ┌───────┴────────┐
     ▼                ▼
 [Простая]     [Средняя/Сложная]
     │                │
     ▼                ▼
┌──────────┐   ┌─────────────────────────────────────────┐
│quick-fix │   │              full-cycle                  │
│          │   │                                          │
│ 1. Найти │   │  Analyst ──► Review ──► Architect ──►    │
│ 2. Fixить│   │  Review ──► ⏸ User OK? ──► Developer    │
│ 3. Check │   │  ──► Review ──► Tester ──► Review ──►   │
│          │   │  Formatter                               │
└─────┬────┘   └───────────────────┬─────────────────────┘
      │                            │
      └────────────┬───────────────┘
                   ▼
            ┌────────────┐
            │  Результат │
            └────────────┘
```

---

## Related resources

| Resource | Relation |
|--------|-------|
| [full-cycle.md](./full-cycle.md) | Deterministic workflow |
| [quick-fix.md](./quick-fix.md) | Light workflow |
| [cross-review-policy.md](../rules/cross-review-policy.md) | Review protocol |
| [docs/SPEC-001-framework-architecture.md](../../docs/SPEC-001-framework-architecture.md) | Framework architecture |

---
depends_on:
  - framework/workflows/full-cycle.md
  - framework/workflows/quick-fix.md
  - framework/rules/cross-review-policy.md
  - framework/rules/agent-context-protocol.md
  - framework/skills/tool-usage/codex-review/SKILL.md
---
