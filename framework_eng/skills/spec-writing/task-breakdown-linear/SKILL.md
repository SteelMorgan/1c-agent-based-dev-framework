---
name: task-breakdown-linear
description: "Task decomposition for linear single-agent mode. Defines a separate Task Breakdown JSON and conducts a self-check without a reviewer agent."
---

# Task decomposition skill (linear single-agent mode)

---

## When to apply

| Trigger | Action |
|---------|----------|
| FREE/linear execution without a Reviewer-agent | Create a Task Breakdown JSON and perform a self-check |
| A spec decomposition is needed before implementation | Use the template + example for the JSON (without JSON Schema) |
| Execution is carried out by a single agent step by step | Build and follow the linear task order defined by `depends_on` |

---

## Mandatory artifact

The decomposition is documented as a **separate JSON file**.

Format requirements:
- use the **template + example**;
- **do not use JSON Schema**;
- keep the consistent fields:
  - `task_id`
  - `task_type`
  - `depends_on`
  - `spec_refs`

Inside the spec there must be:
- a reference to that JSON file, and/or
- a brief summary of the stages and dependencies.

---

## JSON template (template)

```json
{
  "spec_id": "SPEC-NNN",
  "tasks": [
    {
      "task_id": "T1",
      "task_type": "analysis",
      "title": "Brief task title",
      "description": "What needs to be done",
      "depends_on": [],
      "spec_refs": ["Requirements.MUST-1"],
      "deliverables": ["List of expected artifacts"]
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
      "title": "Verification of compliance with MUST requirements",
      "description": "Match the MUST entries from the spec with implementation tasks",
      "depends_on": [],
      "spec_refs": ["Requirements.MUST-1", "Requirements.MUST-2"],
      "deliverables": ["MUST coverage matrix", "List of gaps"]
    },
    {
      "task_id": "T2",
      "task_type": "implementation",
      "title": "Implementation of core logic",
      "description": "Carry out the implementation in accordance with the Technical Design",
      "depends_on": ["T1"],
      "spec_refs": ["Technical Design.Modules", "Requirements.MUST-3"],
      "deliverables": ["Code changes", "Local checks"]
    },
    {
      "task_id": "T3",
      "task_type": "test",
      "title": "Verification of the test plan",
      "description": "Ensure that the MUST entries are covered by tests from the Test Plan",
      "depends_on": ["T2"],
      "spec_refs": ["Test Plan (TDD)"],
      "deliverables": ["Test results", "List of deviations"]
    }
  ]
}
```

---

## Process (self-check instead of review)

1. Based on the spec, create a separate Task Breakdown JSON.
2. Perform a **self-check for consistency**:
   - all MUST entries are reflected in the tasks;
   - `depends_on` forms a valid sequence;
   - `spec_refs` point to specific sections/items.
3. Record assumptions (if the spec contains uncertainties):
   - explicitly list the assumptions;
   - specify the impact of the assumptions on the task order.
4. Execute the tasks linearly in dependency order (single-agent execution).
5. Before completion, repeat the self-check for the actual coverage of requirements.

---

## Self-check checklist (mode without a reviewer agent)

- [ ] Each task has a unique `task_id`.
- [ ] `task_type` corresponds to the actual stage of work.
- [ ] `depends_on` defines an executable linear order without cycles.
- [ ] `spec_refs` are present and linked to the spec.
- [ ] All MUST requirements have implementation/verification tasks.
- [ ] Assumptions are explicitly documented and do not contradict the Scope.
- [ ] The spec includes a reference/summary for the separate JSON.

---

## Common mistakes

| Mistake | Consequence |
|--------|------------|
| No self-check before execution | Execution proceeds according to a faulty plan |
| Unrecorded assumptions | Hidden divergence from expectations |
| Incomplete `spec_refs` | Loss of traceability |
| Violation of the `depends_on` order | Rework on later steps |

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
