# Orchestrator reference structures

## Structure `task_dir`

```
tasks/
└── TASK-001-название/
    ├── .context/                     ← Agent contexts and brief phase outcomes
    │   ├── sessions.json             ← Orchestrator (registry of agentId for all agents)
    │   ├── orchestrator-context.md   ← Orchestrator (context log, maintained continuously)
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
    └── .spec/                        ← Core specification artifacts and final reports
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

## Orchestrator diagram

```
  ┌──────────┐
  │  Task    │
  └─────┬────┘
        ▼
  ┌──────────────────────┐
  │ Explorer (Economy)   │
  │ task classification  │
  └──────────┬───────────┘
             │
     ┌───────┴────────┐
     ▼                ▼
 [Simple]     [Medium/Complex]
     │                │
     ▼                ▼
┌──────────┐   ┌─────────────────────────────────────────┐
│quick-fix │   │              full-cycle                  │
│          │   │                                          │
│ 1. Find  │   │  Analyst ──► Review ──► Architect ──►    │
│ 2. Fix   │   │  Review ──► ⏸ User OK? ──►              │
│ 3. Check │   │  ┌ Scenario-Author(3a) ─► Review ─┐     │
│          │   │  └ Developer-Tests(3b) ─► Review ──┘     │
│          │   │  ──► Developer-Code(3c) ──► Review       │
│          │   │  ──► Tester ──► Review ──► Formatter     │
└─────┬────┘   └───────────────────┬─────────────────────┘
      │                            │
      └────────────┬───────────────┘
                   ▼
            ┌────────────┐
            │  Result    │
            └────────────┘
```

## Parallel Phase 3 execution scheme

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
    ▼         │
 Phase 3c     │
 (Scenario    │
  -Coder)     │
    │         │
    ▼         │
 Review       │
 (bdd-steps)  │
    │         │
    └────┬────┘
         │  (waiting for 3b AND 3c)
         ▼
      Phase 3d
   (Developer-Code)
         │
         ▼
      Review (code)
```
