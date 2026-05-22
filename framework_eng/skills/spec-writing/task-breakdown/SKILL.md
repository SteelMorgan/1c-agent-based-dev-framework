---
name: task-breakdown
description: "Task decomposition in Task Breakdown JSON. Two modes: linear (self-check, single-agent) and subagent (cross-review + BLOCK iterations, architect/reviewer pipeline)."
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
metadata:
  category: spec-writing
---

# Task Decomposition Skill (Task Breakdown)

---

## §1 When to Apply

| Trigger | Mode |
|---------|-------|
| FREE/linear execution without Reviewer-agent | **Linear** — self-check |
| Full-cycle process with Architect/Reviewer roles | **Subagent** — cross-review + BLOCK iterations |
| Decomposition of the spec is needed before implementation | Either mode — use template + example |
| Execution is performed by one agent step by step | **Linear** |
| Reviewer returned BLOCK | **Subagent** — start the correction cycle |

If the orchestration context is unknown, use **Linear** by default.

---

## §2 JSON Format for Task Breakdown

### Required artifact

The decomposition is prepared as a **separate JSON file** (next to the specification or in an agreed project folder).

Format requirements:
- use **template + example**;
- **do not use JSON Schema**;
- keep the same fields:
  - `task_id`
  - `task_type`
  - `depends_on`
  - `spec_refs`

The specification itself must include:
- a link to this JSON file, and/or
- a brief summary of stages and dependencies.

### JSON Template (template)

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

### Allowed `task_type` values

`analysis` | `design` | `implementation` | `test`

---

## §3 Linear Mode (self-check)

### JSON Example

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

### Process (self-check instead of review)

1. Based on the specification, create a separate Task Breakdown JSON.
2. Perform a **consistency self-check**:
   - all MUST requirements are reflected in the tasks;
   - `depends_on` forms a valid sequence;
   - `spec_refs` point to specific sections/items.
3. Record assumptions if the specification contains ambiguities:
   - explicitly list assumptions;
   - specify how the assumptions affect task order.
4. Execute tasks linearly in dependency order (single-agent execution).
5. Before finishing, repeat the self-check for actual requirement coverage.

### Self-check checklist

- [ ] Each task has a unique `task_id`.
- [ ] `task_type` matches the actual work stage.
- [ ] `depends_on` defines an executable linear order without cycles.
- [ ] `spec_refs` are present and tied to the specification.
- [ ] All MUST requirements have implementation/test tasks.
- [ ] Assumptions are explicitly recorded and do not contradict Scope.
- [ ] The specification includes a link/summary for the separate JSON.

### Typical mistakes (Linear)

| Error | Consequence |
|--------|------------|
| No self-check before execution | Execution based on a defective plan |
| Unrecorded assumptions | Hidden mismatches with expectations |
| Incomplete `spec_refs` | Loss of traceability |
| Violation of `depends_on` order | Rework on later steps |

---

## §4 Subagent Mode (cross-review + BLOCK iterations)

### JSON Example

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

### Process (architecture + JSON → review → BLOCK loop)

1. Architect forms the work structure based on the specification.
2. The agent prepares a separate Task Breakdown JSON (template + example, without JSON Schema).
3. Reviewer performs a cross-review of the JSON against the specification and dependencies.
4. If the verdict is **BLOCK**:
   - return for rework;
   - maximum **3 return iterations**.
5. If after 3 returns the remarks are still critical:
   - the **BLOCK > 3** status is recorded;
   - **escalation** is performed (the architect/user decides whether to rebuild the decomposition or clarify the spec).

### JSON quality checklist (review mode)

- [ ] Each task has a unique `task_id`.
- [ ] `task_type` reflects the actual stage (analysis/design/implementation/test, etc.).
- [ ] `depends_on` does not contain cyclic dependencies.
- [ ] `spec_refs` exist for every task and point to specific sections/requirements of the spec.
- [ ] All critical MUST requirements of the specification are covered.
- [ ] The task order is feasible given the dependencies.
- [ ] The specification includes a link/summary for the separate JSON.

### Typical mistakes (Subagent)

| Error | Consequence |
|--------|-------------|
| `spec_refs` are missing | Loss of traceability |
| Inconsistent `depends_on` | Invalid execution order |
| Format changes between iterations | Growth of review defects |
| Ignoring the BLOCK limit | Endless iterations → escalation does not happen |

---

## §5 When to Choose Each Mode

| Criterion | Linear | Subagent |
|----------|--------|---------|
| Presence of Reviewer-agent | No | Yes |
| Presence of the Architect role | Optional | Yes |
| Quality control method | Self-check | Cross-review |
| Iterations on errors | No (fix independently) | Up to 3 BLOCK returns, then escalation |
| Typical context | Simple tasks, one-shot execution | Complex specs, full-cycle pipeline |
| Used by agents | analyst, developer-code | architect |
