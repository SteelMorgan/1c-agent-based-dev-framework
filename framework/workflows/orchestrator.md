---
name: orchestrator
description: Оркестратор маршрутизирует задачи и управляет фазами воркфлоу.
---



# Оркестратор: Мета-воркфлоу

> **Оркестратор** — мета-воркфлоу, который маршрутизирует задачи, выбирает воркфлоу и управляет взаимодействием агентов.

---

## Назначение

Оркестратор не выполняет задачи сам. Он:
1. Классифицирует входящие задачи
2. **Инициализирует каталог задачи** (`task_dir`)
3. Выбирает воркфлоу (quick-fix или full-cycle)
4. Назначает tier модели каждому агенту
5. Управляет циклами ревью
6. Передаёт артефакты между агентами, явно указывая `task_dir`
7. Определяет точки взаимодействия с пользователем
8. **Ведёт реестр сессий агентов** (`task_dir/.context/sessions.json`) для возможного resume
9. **Запускает codex-review** для сложных артефактов как второе независимое мнение
10. **Ведёт лог контекста** (`task_dir/.context/orchestrator-context.md`) — минималистичный журнал ключевых событий для возобновления задачи
11. **Формирует итоговый отчёт** (`task_dir/.spec/final-report.md`) после завершения задачи

---

## Режим FREE: оркестратор отключён

**ВАЖНО:** В режиме FREE (без full-cycle) оркестратор **не активен**.

Агент работает напрямую, используя:
- Навыки (skills)
- Правила (rules)
- Tool-registry

Пользователь даёт задачу, агент решает её в свободном режиме. Кросс-ревью опционально. Фазы не принудительны.

---

## Обязанности оркестратора

### 1. Классификация задач

Определяет сложность задачи и выбирает воркфлоу по [дереву решений](#дерево-решений-классификации).

### 2. Маршрутизация моделей

ВАЖНО!!! При запуске сабагентов через Task **ОБЯЗАТЕЛЬНО** указывай параметр `model`. 
Каждый агент имеет предустановленную модель (field `model` в frontmatter):
- Economy — Explorer
- Mid/High — Developer, Tester
- High/Premium — Architect, Analyst
- Premium — Reviewer (спека, Task Breakdown JSON-декомпозиция, архитектура)
- High — Reviewer (код, тесты, BDD)
**НИКОГДА** не запускай сабагента без явного `model`.

### 3. Управление циклом ревью

- Отслеживает итерации (макс. 3)
- При BLOCK → возвращает автору с замечаниями
- При 3+ BLOCK без решения → эскалация пользователю
- Обеспечивает tier ревьюера ≥ tier автора

**Возврат между агентами через оркестратора:**

Сабагенты не общаются напрямую — любой возврат идёт через оркестратора.

| Ситуация | Кто сигнализирует | Действие оркестратора |
|----------|-------------------|-----------------------|
| Ревьюер поставил BLOCK на артефакт | Reviewer | Вернуть артефакт автору фазы с замечаниями |
| Tester обнаружил баг в реализации | Tester (метка `implementation_error`) | Вернуть Developer-Code с описанием: какой тест, что ожидалось, что получено |
| Tester обнаружил ошибку в своём тесте | Tester (метка `test_error`) | Tester исправляет сам, оркестратор не вмешивается |
| Developer-Code: тесты упали (метка `test_failure`) | Developer-Code | Запустить Reviewer для определения причины: баг в тесте → вернуть Developer-Tests; баг в коде → вернуть Developer-Code |
| Developer-Code: `test_failure` + `suspected_test_error` | Developer-Code | Запустить Reviewer-арбитраж: сопоставить spec + technical-design + тесты + код, зафиксировать в `reviewer-context-code.md` какой артефакт ошибочен (`tests` или `code` или `bdd`). Далее оркестратор по резюме Reviewer маршрутизирует задачу: в Scenario-Author, Developer-Tests или в Developer-Code; решение фиксируется в `orchestrator-context.md`. |
| 3+ итерации без снятия BLOCK | Reviewer / любой агент | Эскалация пользователю, остановка |

### 4. Управление артефактами

- Передаёт выход одной фазы на вход следующей, **явно указывая `task_dir`** каждому сабагенту
- Хранит/восстанавливает контекст между сессиями (если поддерживается адаптером)
- Формирует пакет [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST] для ревьюера, где для Phase 2 [ARTIFACT] включает и Technical Design, и Task Breakdown JSON-декомпозицию

**Разделение хранения:**

| Тип данных | Где хранится |
|------------|--------------|
| Спецификация, тех. дизайн, test-report, final-report | `task_dir/.spec/` |
| Task Breakdown JSON | `task_dir/.context/` |
| Результаты ревью | `task_dir/.context/` |
| Реестр сессий агентов | `task_dir/.context/sessions.json` |
| Контекст-файлы агентов | `task_dir/.context/` |
| BSL-код, тесты, XML метаданных | Кодовая база проекта (отдельный каталог) |

**Структура `task_dir`:**

```
tasks/
└── TASK-001-название/
    ├── .context/                     ← Контексты агентов и краткие результаты по фазам
    │   ├── sessions.json             ← Оркестратор (реестр agentId всех агентов)
    │   ├── orchestrator-context.md   ← Оркестратор (лог контекста, ведётся непрерывно)
    │   ├── explorer-context.md       ← Explorer (Phase 0)
    │   ├── analyst-context.md        ← Analyst (Phase 1)
    │   ├── architect-context.md      ← Architect (Phase 2)
    │   ├── scenario-author-context.md ← Scenario-Author (Phase 3a)
    │   ├── developer-tests-context.md← Developer-Tests (Phase 3b)
    │   ├── developer-code-context.md ← Developer-Code (Phase 3c)
    │   ├── tester-context.md         ← Tester (Phase 4)
    │   ├── reviewer-context-spec.md  ← Reviewer (Phase 1)
    │   ├── reviewer-context-arch.md  ← Reviewer (Phase 2)
    │   ├── reviewer-context-bdd.md   ← Reviewer (Phase 3a)
    │   ├── reviewer-context-tests.md ← Reviewer (Phase 3b)
    │   ├── reviewer-context-code.md  ← Reviewer (Phase 3c)
    │   ├── reviewer-context-tester.md← Reviewer (Phase 4)
    │   └── task-breakdown.json       ← Architect (Phase 2)
    └── .spec/                        ← Основные артефакты спецификации и итоговые отчёты
        ├── spec.md                   ← Analyst (Phase 1)
        ├── technical-design.md       ← Architect (Phase 2)
        ├── test-report.md            ← Tester (Phase 4)
        └── final-report.md           ← Оркестратор (итоговый отчёт)
```

### 5. Реестр сессий агентов (`task_dir/.context/sessions.json`)

Оркестратор ведёт `task_dir/.context/sessions.json` — реестр agentId всех запущенных агентов.
Используется для `resume` при повторном запуске того же агента (BLOCK → fix → re-review, clarification round и т.д.).

**Структура:**

```json
{
  "explorer":         "agent-xxx",
  "analyst":          "agent-yyy",
  "architect":        "agent-zzz",
  "scenario-author":  "agent-xxx",
  "developer-tests":  "agent-aaa",
  "developer-code":   "agent-bbb",
  "tester":           "agent-ccc",
  "reviewer-spec":    "agent-ddd",
  "reviewer-arch":    "agent-eee",
  "reviewer-bdd":     "agent-xxx",
  "reviewer-tests":   "agent-fff",
  "reviewer-code":    "agent-ggg",
  "reviewer-tester":  "agent-hhh"
}
```

**Протокол:**
- После каждого запуска агента — записать agentId в соответствующий ключ
- При повторном запуске — прочитать `task_dir/.context/sessions.json`, попробовать `resume agentId`; если agentId устарел — новый запуск, обновить запись
- Reviewer запускается отдельно для каждого scope (`reviewer-spec`, `reviewer-arch`, `reviewer-bdd`, `reviewer-tests`, `reviewer-code`, `reviewer-tester`) — у каждого свой ключ

### 6. Codex-review как второе независимое мнение

Оркестратор запускает `codex-review` (CLI) **поверх основного ревью Reviewer** для сложных артефактов. Reviewer не запускает его сам — это ответственность оркестратора.

**Когда запускать:**

| Условие | Действие |
|---------|----------|
| Архитектурное решение с trade-offs (Phase 2) | Запустить codex-review после Reviewer |
| Сложный BSL-код (> 5 файлов, > 300 строк) | Запустить codex-review после Reviewer |
| Reviewer поставил BLOCK, автор оспаривает | Запустить codex-review как tiebreaker |
| По запросу пользователя `/review-gpt`, `/review-all` | Немедленно запустить |

**Как запускать:** см. навык `codex-review`.

### 5. Точки взаимодействия с пользователем

| Точка | Действие |
|-------|----------|
| Phase 1: Аналитик вернул `clarification_needed` | Задать пользователю все вопросы одним блоком, собрать ответы, повторно запустить Аналитика с уточнениями (макс. 1 раунд) |
| Phase 2: Архитектор вернул `clarification_needed` | Задать пользователю все вопросы одним блоком, собрать ответы, повторно запустить Архитектора с уточнениями (макс. 1 раунд) |
| Phase 2 (архитектура) | Approval gate — ждём подтверждения пользователя |
| Эскалация (3 BLOCK) | Запрос решения у пользователя |
| Новый объект метаданных | Протокол «агент → пользователь»: инструкция → ожидание создания → проверка |

**Протокол уточнений (clarification round):**

```
Агент → clarification_needed
  │  (вопросы записаны в task_dir/.context/{role}-context.md → Pending Questions)
  ▼
Оркестратор читает task_dir/.context/{role}-context.md → задаёт вопросы пользователю
  │
  ▼
Пользователь отвечает
  │
  ▼
Оркестратор записывает ответы в task_dir/.context/{role}-context.md → User Answers
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

## Протокол оркестратора

### Последовательность действий

```
1. Получить задачу от пользователя
   ↓
2. Инициализировать task_dir:
   - Если передан номер/путь задачи → использовать существующий каталог
   - Иначе → создать tasks/TASK-XXX-название/
   - Создать/прочитать task_dir/.context/sessions.json
   - Создать/дополнить task_dir/.context/orchestrator-context.md: записать событие START с датой-временем и текстом задачи
   ↓
3. Запустить Explorer для исследования кодовой базы
   - Explorer возвращает: список затронутых модулей, графы вызовов (входящие + исходящие),
     глубину зависимостей, количество точек вызова
   - Сохранить артефакт Explorer в `task_dir/.context/explorer-context.md`
   - Записать agentId Explorer в `task_dir/.context/sessions.json` → ключ "explorer"
   - На основе этих данных классифицировать задачу (простая / средняя / сложная)
   ↓
4. По результату классификации выбрать воркфлоу:
   - Простая → quick-fix.md
   - Средняя/Сложная → full-cycle.md
   ↓
5. Для каждой фазы выбранного воркфлоу:
   a. Запустить агента (модель задана в agent frontmatter)
      - Оптимизация: прочитать `task_dir/.context/sessions.json`; если agentId для этой роли есть → попробовать resume
      - После запуска: записать agentId в `task_dir/.context/sessions.json` → ключ роли агента
   b. Передать входные данные + явно task_dir
      - **Для Phase 1 (Analyst):** задача + `task_dir/.context/explorer-context.md` (список модулей, графы вызовов)
      - **Для Phase 2 (Architect):** утверждённая спека + `task_dir/.context/explorer-context.md` (графы вызовов, зависимости)
      - **Для Phase 3a (Scenario-Author):** spec + technical-design + task-breakdown.json (запуск параллельно с Phase 3b)
      - **Для Phase 3b (Developer-Tests):** spec + technical-design + task-breakdown.json (запуск параллельно с Phase 3a)
      - **Для Phase 3c (Developer-Code):** spec + technical-design + task-breakdown.json + тест-модули из Phase 3b + `.feature`-файлы из Phase 3a
   c. Собрать выходной артефакт → сохранить в task_dir
      - Записать в `task_dir/.context/orchestrator-context.md`: событие завершения фазы (агент, результат — OK / BLOCK / clarification_needed)
   d. Если требуется ревью:
      - Запустить Reviewer с [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST]+[review_scope]
      - Передать `review_scope` явно: "spec" | "arch" | "bdd" | "tests" | "code" | "tester"
      - Для Phase 2 в [ARTIFACT] обязательно включить `task_dir/.spec/technical-design.md` + `task_dir/.context/task-breakdown.json`
      - Записать agentId Reviewer в `task_dir/.context/sessions.json` → ключ "reviewer-{scope}"
      - Сохранить результат ревью в `task_dir/.context/reviewer-context-{scope}.md`
      - Обработать результат (pass / iterate / escalate)
      - При необходимости: запустить codex-review как второе мнение (см. раздел 6)
   e. Если агент вернул `clarification_needed` (Phase 1 — Analyst, Phase 2 — Architect):
      - Прочитать `task_dir/.context/{role}-context.md` — там список вопросов
      - Задать ВСЕ вопросы пользователю одним блоком
      - Дождаться ответов
      - Записать ответы в секцию `User Answers` файла `task_dir/.context/{role}-context.md`
      - Повторно запустить агента с исходной задачей + task_dir
        (агент сам прочитает контекст и ответы при старте)
      - Оптимизация: если agentId предыдущего запуска актуален —
        использовать resume вместо нового запуска
      - Если снова clarification_needed → эскалация пользователю (не повторять)
   f. Передать артефакт на следующую фазу
   ↓
6. Сформировать итоговый отчёт `task_dir/.spec/final-report.md` (см. формат ниже)
   - Записать в `task_dir/.context/orchestrator-context.md`: событие DONE
   ↓
7. Передать результат пользователю
```

### Детализация шага 4d (обработка ревью)

| Результат ревью | Действие |
|-----------------|----------|
| OK (нет BLOCK) | Перейти к следующей фазе. WARN/INFO — по усмотрению автора (можно исправить позже). |
| BLOCK, итерация ≤ 3 | Вернуть артефакт автору с замечаниями. Повторный цикл. Для Task Breakdown JSON применяются те же правила итераций. |
| BLOCK, итерация > 3 | Эскалация пользователю. Остановка. Для Task Breakdown JSON: >3 итераций запрещено, требуется решение пользователя. |
| Phase 2: OK | Остановка. Запрос подтверждения пользователя. После подтверждения — Phase 3 (параллельный запуск 3a + 3b). |

### Параллельный запуск Phase 3a и Phase 3b

После подтверждения пользователем результатов Phase 2 оркестратор запускает **одновременно**:

- **Phase 3a — Scenario-Author (BDD):** пишет `.feature`-файлы сценариев на основе spec + technical-design + task-breakdown.json
- **Phase 3b — Developer-Tests:** пишет unit-тест-модули на основе spec + technical-design + task-breakdown.json

**Правила параллельного запуска:**

1. Phase 3a и Phase 3b **независимы** — им не нужны артефакты друг друга, оба получают на вход: spec + technical-design + task-breakdown.json.
2. Оркестратор **ожидает завершения обоих** (включая прохождение ревью каждого) перед запуском Phase 3c.
3. **Phase 3c (Developer-Code)** получает полный набор: spec + technical-design + task-breakdown.json + тест-модули из Phase 3b + `.feature`-файлы из Phase 3a.
4. Если один из агентов (3a или 3b) вернул `clarification_needed` или получил BLOCK от ревьюера — обрабатывать **независимо**, не блокируя второй параллельный агент.
5. Если один завершился раньше — ждать второго; результат первого сохраняется в `task_dir/.context/`.

**Схема:**

```
Phase 2 OK + User Approval
         │
    ┌────┴────┐
    ▼         ▼
 Phase 3a  Phase 3b
 (Scenario  (Developer
  -Author)   -Tests)
    │         │
    ▼         ▼
 Review    Review
 (bdd)     (tests)
    │         │
    └────┬────┘
         │  (ждём обоих)
         ▼
      Phase 3c
   (Developer-Code)
         │
         ▼
      Review (code)
```

---

## Лог контекста (`task_dir/.context/orchestrator-context.md`)

Минималистичный журнал ключевых событий. Ведётся непрерывно — позволяет возобновить задачу с той же точки при остановке оркестратора.

**Формат записи:**
```
[YYYY-MM-DD HH:MM] СОБЫТИЕ: описание
```

**Ключевые события для фиксации:**

| Событие | Когда писать |
|---------|-------------|
| `START` | Начало задачи, текст задачи одной строкой |
| `PHASE` | Запуск каждой фазы (Explorer, Analyst, Architect, Scenario-Author, Developer, Tester, Reviewer) |
| `DONE_PHASE` | Завершение фазы, результат (OK / BLOCK / clarification_needed) |
| `CLARIFICATION` | Запрос уточнений у пользователя |
| `USER_INPUT` | Получен ответ пользователя |
| `REVIEW_BLOCK` | Reviewer поставил BLOCK, номер итерации |
| `ESCALATE` | Эскалация пользователю |
| `RESUME` | Возобновление задачи после остановки |
| `DONE` | Задача завершена |

**Пример:**
```
[2026-03-02 10:15] START: Добавить реквизит "ДатаОтгрузки" в документ Реализация
[2026-03-02 10:16] PHASE: Explorer — исследование кодовой базы
[2026-03-02 10:18] DONE_PHASE: Explorer — OK, задача классифицирована как СРЕДНЯЯ
[2026-03-02 10:18] PHASE: Analyst — формирование спецификации
[2026-03-02 10:22] DONE_PHASE: Analyst — clarification_needed
[2026-03-02 10:23] CLARIFICATION: Задан вопрос пользователю: тип реквизита Дата или ДатаВремя?
[2026-03-02 10:25] USER_INPUT: Дата
[2026-03-02 10:28] DONE_PHASE: Analyst — OK, task_dir/.spec/spec.md сформирован
[2026-03-02 10:29] PHASE: Reviewer (scope: spec)
[2026-03-02 10:31] DONE_PHASE: Reviewer spec — OK
...
[2026-03-02 11:45] DONE
```

**Правила:**
- Не дублировать содержимое артефактов — только факт события.
- Максимум одна строка на событие.
- При возобновлении задачи — дописывать в существующий лог, не перезаписывать.

---

## Итоговый отчёт (`task_dir/.spec/final-report.md`)

Формируется оркестратором после завершения всех фаз задачи.

**Формат:**

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

**Правила:**
- Если объект попал в «Новые» — он **не дублируется** в «Изменённые».
- Объекты метаданных указывать в нотации 1С: `Тип.Имя` (например `Справочник.Контрагенты`).
- Подобъекты (формы, реквизиты, табличные части) указывать через точку: `Документ.Реализация.Форма.ФормаДокумента`.
- Раздел «Что сделано» — свободный текст, 3–7 предложений.

---

## Дерево решений классификации

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

### Правила дерева

| Вопрос | Ответ «Да» | Сложность |
|--------|------------|------------|
| Требуются ли новые объекты метаданных (справочники, документы, регистры, формы)? | Да | Сложная |
| Изменяется ли поток данных или архитектура решения? | Да | Сложная |
| Это исправление бага в одном файле? | Да | Простая |
| Всё остальное | — | Средняя |

**При неопределённости:** трактовать как среднюю, использовать full-cycle.

---

## Диаграмма оркестратора

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
│ 2. Fixить│   │  Review ──► ⏸ User OK? ──►              │
│ 3. Check │   │  ┌ Scenario-Author(3a) ─► Review ─┐     │
│          │   │  └ Developer-Tests(3b) ─► Review ──┘     │
│          │   │  ──► Developer-Code(3c) ──► Review       │
│          │   │  ──► Tester ──► Review ──► Formatter     │
└─────┬────┘   └───────────────────┬─────────────────────┘
      │                            │
      └────────────┬───────────────┘
                   ▼
            ┌────────────┐
            │  Результат │
            └────────────┘
```

---

## Связанные ресурсы

| Ресурс | Связь |
|--------|-------|
| [full-cycle.md](./full-cycle.md) | Детерминированный воркфлоу |
| [quick-fix.md](./quick-fix.md) | Облегчённый воркфлоу |
| [cross-review-policy.md](../rules/cross-review-policy.md) | Протокол ревью |
| [docs/SPEC-001-framework-architecture.md](../../docs/SPEC-001-framework-architecture.md) | Архитектура фреймворка |

---
depends_on:
  - framework/workflows/full-cycle.md
  - framework/workflows/quick-fix.md
  - framework/rules/agent-context-protocol.md
  - framework/skills/tool-usage/review/codex-review/SKILL.md
  - framework/subagents/scenario-author.md
---