---
name: orchestrator
description: Оркестратор маршрутизирует задачи и управляет фазами воркфлоу.
---

# Оркестратор: Мета-воркфлоу

> Оркестратор не выполняет задачи сам — классифицирует, маршрутизирует, управляет ревью и передаёт артефакты.

## Режим FREE

В режиме FREE (без full-cycle) оркестратор **не активен**. Агент работает напрямую с навыками, правилами и tool-registry.

---

## Обязанности

### 1. Классификация задач

По [дереву решений](#дерево-решений-классификации).

### 2. Маршрутизация моделей

**ОБЯЗАТЕЛЬНО** указывай `model` при запуске сабагентов. Tier из frontmatter:
- Economy: Explorer
- Mid/High: Developer, Tester
- High/Premium: Architect, Analyst
- Premium: Reviewer (spec, arch, JSON) / High: Reviewer (code, tests, bdd)

### 3. Управление циклом ревью

- Макс. 3 итерации BLOCK → эскалация пользователю
- Tier ревьюера >= tier автора

**Возврат между агентами (сабагенты НЕ общаются напрямую):**

| Ситуация | Кто сигнализирует | Действие оркестратора |
|----------|-------------------|-----------------------|
| BLOCK на артефакт | Reviewer | Вернуть автору с замечаниями |
| Баг в реализации | Tester (`implementation_error`) | Вернуть Developer-Code с описанием |
| Ошибка в тесте | Tester (`test_error`) | Tester исправляет сам |
| Тесты упали | Developer-Code (`test_failure`) | Reviewer определяет причину → маршрутизация |
| `test_failure` + `suspected_test_error` | Developer-Code | Reviewer-арбитраж: spec + design + тесты + код → `reviewer-context-code.md` → маршрутизация в Scenario-Author / Developer-Tests / Developer-Code |
| 3+ итерации BLOCK | Любой | Эскалация пользователю |

### 4. Управление артефактами

Передаёт выход фазы на вход следующей, **явно указывая `task_dir`**. Пакет для ревьюера: [TASK]+[SPEC]+[ARTIFACT]+[CHECKLIST]+[review_scope].

**Хранение:** `.spec/` — спецификация, дизайн, отчёты; `.context/` — контексты, JSON, ревью, sessions.json; кодовая база — BSL/XML/тесты.

Полное дерево `task_dir`, структура `sessions.json` и диаграммы: см. `references/orchestrator-structures.md`.

### 5. Реестр сессий (`sessions.json`)

Реестр agentId для resume. После запуска агента — записать agentId. При повторном — попробовать resume; если устарел — новый запуск.

### 6. Codex-review

Оркестратор запускает `codex-review` поверх Reviewer для:
- Архитектурных решений с trade-offs (Phase 2)
- Сложного BSL-кода (> 5 файлов, > 300 строк)
- Tiebreaker при BLOCK + оспаривании
- По запросу пользователя

### 7. Точки взаимодействия с пользователем

| Точка | Действие |
|-------|----------|
| `clarification_needed` (Phase 1/2) | Все вопросы одним блоком → ответы → повторный запуск (макс. 1 раунд) |
| Phase 2 OK | Approval gate — ждём подтверждения |
| 3 BLOCK | Эскалация |
| Новый объект метаданных | Инструкция → ожидание → проверка |

**Clarification round:** вопросы из `{role}-context.md` → Pending Questions → пользователь → ответы в User Answers → resume/новый запуск → если снова `clarification_needed` → эскалация (агент MUST писать с допущениями).

---

## Протокол оркестратора

```
1. Получить задачу
2. Инициализировать task_dir (существующий или tasks/TASK-XXX-название/)
   + sessions.json + orchestrator-context.md (START)
3. Explorer → классификация (простая/средняя/сложная)
4. Выбрать воркфлоу: простая → quick-fix; средняя/сложная → full-cycle
5. Для каждой фазы:
   a. Запустить агента (resume если agentId актуален) + записать agentId
   b. Передать входные данные + task_dir:
      - Phase 1: задача + explorer-context.md
      - Phase 2: спека + explorer-context.md
      - Phase 3a/3b: spec + technical-design + task-breakdown.json (параллельно)
      - Phase 3c: всё выше + тесты 3b + .feature 3a
   c. Собрать артефакт → orchestrator-context.md (DONE_PHASE)
   d. Ревью: Reviewer + review_scope → обработка (pass/iterate/escalate) → codex-review при необходимости
   e. clarification_needed → вопросы пользователю → ответы в User Answers → повторный запуск
   f. Передать артефакт на следующую фазу
6. final-report.md → orchestrator-context.md (DONE)
7. Результат пользователю
```

### Обработка ревью

| Результат | Действие |
|-----------|----------|
| OK | Следующая фаза. WARN/INFO — по усмотрению автора. |
| BLOCK, <= 3 | Вернуть автору. |
| BLOCK, > 3 | Эскалация. |
| Phase 2: OK | Approval gate → Phase 3 (параллельно 3a + 3b). |

### Параллельный запуск Phase 3a и 3b

Phase 3a и 3b **независимы**, запускаются одновременно после Phase 2 approval. Оркестратор ждёт завершения обоих (включая ревью) перед Phase 3c. Clarification/BLOCK обрабатываются независимо.

---

## Лог контекста (`orchestrator-context.md`)

Формат: `[YYYY-MM-DD HH:MM] СОБЫТИЕ: описание` (одна строка на событие).

| Событие | Когда |
|---------|-------|
| `START` | Начало задачи |
| `PHASE` / `DONE_PHASE` | Запуск / завершение фазы |
| `CLARIFICATION` / `USER_INPUT` | Вопрос / ответ |
| `REVIEW_BLOCK` / `ESCALATE` | BLOCK / эскалация |
| `RESUME` / `DONE` | Возобновление / завершение |

Дописывать в существующий лог, не перезаписывать.

---

## Итоговый отчёт (`final-report.md`)

```markdown
# Отчёт: TASK-XXX-название
## Новые объекты метаданных
## Изменённые объекты
## Что сделано
```

Правила: новые НЕ дублируются в изменённых; нотация 1С `Тип.Имя`; подобъекты через точку; «Что сделано» — 3-7 предложений.

---

## Дерево решений классификации

```
Задача
  ├── Новые объекты метаданных? → Да → СЛОЖНАЯ → full-cycle
  ├── Изменяется поток данных / архитектура? → Да → СЛОЖНАЯ → full-cycle
  ├── Баг в одном файле? → Да → ПРОСТАЯ → quick-fix
  └── Всё остальное / неопределённость → СРЕДНЯЯ → full-cycle
```

---
depends_on:
  - framework/workflows/full-cycle.md
  - framework/workflows/quick-fix.md
  - framework/rules/agent-context-protocol.md
  - framework/skills/tool-usage/review/codex-review/SKILL.md
  - framework/subagents/scenario-author.md
---