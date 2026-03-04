---
name: task-breakdown-linear
description: Task decomposition for linear single-agent mode. Defines a separate Task Breakdown JSON and a self-check without a review agent.
---

# Task decomposition skill (linear single-agent mode)

## Purpose

This skill defines the process of decomposing tasks for the linear execution mode where there is no dedicated Reviewer-agent.

The skill describes:
- the same separate Task Breakdown JSON artifact as in the subagent mode;
- self-check as the quality control mechanism instead of cross-review;
- a linear execution strategy and documentation of assumptions.

---

## When to apply

| Trigger | Action |
|---------|--------|
| FREE/linear execution without Reviewer-agent | Create Task Breakdown JSON and perform a self-check |
| Need to decompose the spec before implementation | Use the template + example for the JSON (without JSON Schema) |
| Execution proceeds by a single agent step-by-step | Build and execute the linear task order according to `depends_on` |

---

## Mandatory artifact

Task decomposition is documented as a **separate JSON file**.

Format requirements:
- use **template + example**;
- **do not use JSON Schema**;
- keep the standard fields:
  - `task_id`
  - `task_type`
  - `depends_on`
  - `spec_refs`

The specification itself should include:
- a link to this JSON file, and/or
- a short summary of the stages and dependencies.

---

## JSON template (template)

```json
{
  "spec_id": "SPEC-NNN",
  "tasks": [
    {
      "task_id": "T1",
      "task_type": "analysis",
      "title": "Краткое название задачи",
      "description": "Что должно быть сделано",
      "depends_on": [],
      "spec_refs": ["Requirements.MUST-1"],
      "deliverables": ["Список ожидаемых артефактов"]
    }
  ]
}
```

## Example JSON (example)

```json
{
  "spec_id": "SPEC-002",
  "tasks": [
    {
      "task_id": "T1",
      "task_type": "analysis",
      "title": "Проверка соответствия MUST-требованиям",
      "description": "Сопоставить MUST из спецификации с задачами реализации",
      "depends_on": [],
      "spec_refs": ["Requirements.MUST-1", "Requirements.MUST-2"],
      "deliverables": ["Матрица покрытия MUST", "Список пробелов"]
    },
    {
      "task_id": "T2",
      "task_type": "implementation",
      "title": "Реализация основной логики",
      "description": "Выполнить реализацию в соответствии с Technical Design",
      "depends_on": ["T1"],
      "spec_refs": ["Technical Design.Modules", "Requirements.MUST-3"],
      "deliverables": ["Изменения кода", "Локальные проверки"]
    },
    {
      "task_id": "T3",
      "task_type": "test",
      "title": "Проверка тест-плана",
      "description": "Проверить, что MUST покрыты тестами из Test Plan",
      "depends_on": ["T2"],
      "spec_refs": ["Test Plan (TDD)"],
      "deliverables": ["Результаты тестов", "Список отклонений"]
    }
  ]
}
```

---

## Process (self-check instead of review)

1. Based on the specification, create a separate Task Breakdown JSON.
2. Perform a **self-check of consistency**:
   - all MUST items are reflected in the tasks;
   - `depends_on` forms a valid sequence;
   - `spec_refs` point to specific sections/items.
3. Record assumptions (if there are uncertainties in the specification):
   - explicitly list the assumptions;
   - indicate the impact of the assumptions on the task order.
4. Execute the tasks linearly in dependency order (single-agent execution).
5. Before completion, repeat the self-check to confirm actual coverage of the requirements.

---

## Checklist for self-check (mode without review agent)

- [ ] Each task has a unique `task_id`.
- [ ] `task_type` corresponds to the actual work stage.
- [ ] `depends_on` defines an executable linear order without cycles.
- [ ] `spec_refs` are present and linked to the specification.
- [ ] All MUST requirements have implementation/verification tasks.
- [ ] Assumptions are explicitly recorded and do not contradict the Scope.
- [ ] The specification includes a link/summary for the separate JSON.

---

## Common mistakes

| Mistake | Consequence | How to avoid |
|--------|-------------|--------------|
| Skipping the self-check before execution | Linearly executing according to a defective plan | Always perform the self-check before starting implementation |
| Unrecorded assumptions | Hidden discrepancies with user expectations | Explicitly document the assumptions and their impact |
| Incomplete `spec_refs` | Loss of traceability between tasks and requirements | Verify the traceability of each task |
| Violating dependencies during linear execution | Rework and blockers on later steps | Strictly follow the order defined by `depends_on` |

---

## Related skills

- `framework/skills/spec-writing/spec-standard/SKILL.md` — the basic universal specification standard.
- `framework/skills/spec-writing/task-breakdown-subagent/SKILL.md` — the process for the mode with cross-review.

---
depends_on: []
---
