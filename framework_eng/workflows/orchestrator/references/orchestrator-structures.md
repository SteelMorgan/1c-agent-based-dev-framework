# Reference structures of the orchestrator

## Structure `task_dir`

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
    │   ├── scenario-coder-context.md ← Scenario-Coder (Phase 3c)
    │   ├── developer-code-context.md ← Developer-Code (Phase 3d)
    │   ├── tester-context.md         ← Tester (Phase 4)
    │   ├── reviewer-context-spec.md  ← Reviewer (Phase 1)
    │   ├── reviewer-context-arch.md  ← Reviewer (Phase 2)
    │   ├── reviewer-context-bdd.md   ← Reviewer (Phase 3a)
    │   ├── reviewer-context-tests.md ← Reviewer (Phase 3b)
    │   ├── reviewer-context-bdd-steps.md ← Reviewer (Phase 3c)
    │   ├── reviewer-context-code.md  ← Reviewer (Phase 3d)
    │   ├── reviewer-context-tester.md← Reviewer (Phase 4)
    │   └── task-breakdown.json       ← Architect (Phase 2)
    └── .spec/                        ← Main specification artifacts and final reports
        ├── spec.md                   ← Analyst (Phase 1)
        ├── technical-design.md       ← Architect (Phase 2)
        ├── test-report.md            ← Tester (Phase 4)
        └── final-report.md           ← Orchestrator (final report)
```

## Structure `sessions.json`

```json
{
  "explorer":         "agent-xxx",
  "analyst":          "agent-yyy",
  "architect":        "agent-zzz",
  "scenario-author":  "agent-xxx",
  "developer-tests":  "agent-aaa",
  "scenario-coder":   "agent-bbb",
  "developer-code":   "agent-ccc",
  "tester":           "agent-ddd",
  "reviewer-spec":    "agent-eee",
  "reviewer-arch":    "agent-fff",
  "reviewer-bdd":     "agent-ggg",
  "reviewer-tests":   "agent-hhh",
  "reviewer-bdd-steps": "agent-iii",
  "reviewer-code":    "agent-jjj",
  "reviewer-tester":  "agent-kkk"
}
```

## Orchestrator Diagram

```
  ┌──────────┐
  │  Задача  │
  └─────┬────┘
        ▼
  ┌──────────────────────┐
  │ Explorer (Luna Max)  │
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
│ 1. Find │   │  Analyst-controller ─► Gate ─► User OK ─►│
│ 2. Fix  │   │  Architect-controller ─► Gate ─► User OK │
│ 3. Check │   │  ┌ Scenario-Author(3a) ─► Gate ────┐     │
│          │   │  └ Developer-Tests(3b) ─► Gate ────┘     │
│          │   │  ──► Scenario-Coder(3c) ─► Gate          │
│          │   │  ──► Developer-Code(3d) ──► Gate         │
│          │   │  ──► Tester ─► Gate ──► Formatter       │
└─────┬────┘   └───────────────────┬─────────────────────┘
      │                            │
      └────────────┬───────────────┘
                   ▼
            ┌────────────┐
            │  Результат │
            └────────────┘
```

## Parallel Phase 3 Launch Diagram

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
 Gate      Gate
 (bdd)     (tests)
    │         │
    ▼         │
 Phase 3c     │
 (Scenario    │
  -Coder)     │
    │         │
    ▼         │
 Gate         │
 (bdd-steps)  │
    │         │
    └────┬────┘
         │  (ждём 3b И 3c)
         ▼
      Phase 3d
   (Developer-Code)
         │
         ▼
      Gate (code)
```
