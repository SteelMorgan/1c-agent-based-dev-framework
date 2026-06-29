# Справочные структуры оркестратора

## Структура `task_dir`

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

## Структура `sessions.json`

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

## Схема параллельного запуска Phase 3

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
