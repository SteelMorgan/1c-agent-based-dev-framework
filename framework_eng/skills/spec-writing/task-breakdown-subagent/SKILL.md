---
name: task-breakdown-subagent
description: Task decomposition for subagent mode. Defines a separate Task Breakdown JSON, cross-review, and BLOCK iterations.
---

# Task decomposition skill (subagent mode)

## Purpose

This skill lays out the task decomposition process for scenarios where specialized roles (for example, Architect/Reviewer) operate and cross-review is applied.

The skill describes:
- a unified artifact format for the decomposition (a separate JSON file);
- the quality check process via a Reviewer;
- the BLOCK issue handling loop (up to 3 returns, then escalation).

---

## When to apply

| Trigger | Action |
|---------|--------|
| Full-cycle process with Architect/Reviewer roles | Create the Task Breakdown JSON and send it for cross-review |
| Decomposition of the spec is needed before implementation | Use the template + example for the JSON (without JSON Schema) |
| Reviewer returns BLOCK | Start the correction cycle considering the iteration limit |

---

## Required artifact

The decomposition is captured as a **separate JSON file** (located next to the specification or in an agreed project folder).

Format requirements:
- use **template + example**;
- **do not use JSON Schema**;
- keep the shared fields:
  - `task_id`
  - `task_type`
  - `depends_on`
  - `spec_refs`

The specification itself must contain:
- a reference to this JSON file, and/or
- a concise summary of the stages and dependencies.

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

## JSON example (example)

```json
{
  "spec_id": "SPEC-002",
  "tasks": [
    {
      "task_id": "T1",
      "task_type": "analysis",
      "title": "Проверка metadata-объектов",
      "description": "Сверить состав объектов с разделом Technical Design",
      "depends_on": [],
      "spec_refs": ["Technical Design.Metadata Objects", "Requirements.MUST-1"],
      "deliverables": ["Список проверенных объектов", "Перечень расхождений"]
    },
    {
      "task_id": "T2",
      "task_type": "implementation",
      "title": "Реализация проведения документа",
      "description": "Реализовать движения и проверки остатков",
      "depends_on": ["T1"],
      "spec_refs": ["Requirements.MUST-2", "Requirements.MUST-3"],
      "deliverables": ["Код модуля объекта", "Тесты по MUST-требованиям"]
    }
  ]
}
```

---

## Process (architecture + JSON → review → BLOCK loop)

1. The Architect structures the work based on the specification.
2. The agent prepares the separate Task Breakdown JSON (template + example, without JSON Schema).
3. The Reviewer performs a cross-review of the JSON against the specification and dependencies.
4. If the verdict is **BLOCK**:
   - return for revision;
   - allow at most **3 return iterations**.
5. If critical issues remain after 3 returns:
   - mark the status as **BLOCK > 3**;
   - perform **escalation** (the architect/user decides whether to rebuild the decomposition or clarify the spec).

---

## JSON quality checklist (review mode)

- [ ] Each task has a unique `task_id`.
- [ ] `task_type` reflects the actual stage (analysis/design/implementation/test, etc.).
- [ ] `depends_on` is free of cyclic dependencies.
- [ ] Each task has `spec_refs` that link to specific sections/requirements of the spec.
- [ ] All critical MUST requirements of the specification are covered.
- [ ] Task ordering is feasible considering the dependencies.
- [ ] The specification includes a link/summary of the separate JSON.

---

## Common mistakes

| Mistake | Consequence | How to avoid |
|--------|------------|-------------|
| Missing `spec_refs` | Loss of traceability between tasks and requirements | Link every task to a section/item of the specification |
| Misaligned `depends_on` | Unable to sequence execution correctly | Validate the dependency DAG before review |
| Changing the format between iterations | Growing review defects and invalid integrations | Keep a stable template + example format |
| Ignoring the BLOCK limit | Endless iterations without resolution | Apply the ≤3 return rule, then escalate |

---

## Related skills

- `framework/skills/spec-writing/spec-standard/SKILL.md` — the base universal specification standard.
- `framework/skills/spec-writing/task-breakdown-linear/SKILL.md` — the alternative process for linear single-agent mode.

---
depends_on: []
---
