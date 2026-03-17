---
name: task-breakdown-linear
description: Task decomposition for linear single-agent mode. Defines a separate Task Breakdown JSON and a self-check without a reviewer agent.
---

# Task decomposition skill (linear single-agent mode)

## Purpose

This skill establishes the task decomposition process for a linear execution mode where there is no separate Reviewer agent.

The skill describes:
- the same standalone decomposition JSON artifact as in the subagent mode;
- self-check as the quality control mechanism instead of cross-review;
- a linear execution strategy and documentation of assumptions.

---

## When to apply

| Trigger | Action |
|---------|--------|
| FREE/linear execution without a Reviewer-agent | Create a Task Breakdown JSON and perform a self-check |
| A spec decomposition is needed before implementation | Use the template + example for the JSON (without JSON Schema) |
| Execution is performed by one agent step by step | Build and follow the linear task order defined by `depends_on` |

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
- a short summary of the stages and dependencies.

---

## JSON template

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

## JSON example

```json
{
  "spec_id": "SPEC-002",
  "tasks": [
    {
      "task_id": "T1",
      "task_type": "analysis",
      "title": "Verification of compliance with MUST requirements",
      "description": "Map the MUST entries from the spec to implementation tasks",
      "depends_on": [],
      "spec_refs": ["Requirements.MUST-1", "Requirements.MUST-2"],
      "deliverables": ["MUST coverage matrix", "List of gaps"]
    },
    {
      "task_id": "T2",
      "task_type": "implementation",
      "title": "Implementation of core logic",
      "description": "Carry out the implementation according to the Technical Design",
      "depends_on": ["T1"],
      "spec_refs": ["Technical Design.Modules", "Requirements.MUST-3"],
      "deliverables": ["Code changes", "Local checks"]
    },
    {
      "task_id": "T3",
      "task_type": "test",
      "title": "Verification of the test plan",
      "description": "Ensure the MUST items are covered by tests from the Test Plan",
      "depends_on": ["T2"],
      "spec_refs": ["Test Plan (TDD)"],
      "deliverables": ["Test results", "List of deviations"]
    }
  ]
}
```

---

## Process (self-check instead of review)

1. Based on the spec, create the separate Task Breakdown JSON.
2. Perform a **self-check for consistency**:
   - all MUST entries are reflected in tasks;
   - `depends_on` forms a valid sequence;
   - `spec_refs` point to specific sections/items.
3. Document assumptions (if the spec contains uncertainties):
   - explicitly list the assumptions;
   - specify the impact of the assumptions on the task order.
4. Execute the tasks linearly according to the dependencies (single-agent execution).
5. Before completion, repeat the self-check to confirm actual coverage of requirements.

---

## Self-check checklist (mode without reviewer-agent)

- [ ] Each task has a unique `task_id`.
- [ ] `task_type` corresponds to the actual work stage.
- [ ] `depends_on` defines an executable linear order without cycles.
- [ ] `spec_refs` are present and linked to the spec.
- [ ] Every MUST requirement has implementation/verification tasks.
- [ ] Assumptions are explicitly documented and do not contradict the Scope.
- [ ] The spec includes a reference/summary for the separate JSON.

---

## Common mistakes

| Mistake | Consequence | How to avoid |
|---------|-------------|--------------|
| Skipping the self-check before execution | Linear execution based on a faulty plan | Always perform the self-check before starting implementation |
| Unrecorded assumptions | Hidden mismatches with user expectations | Explicitly document the assumptions and their impact |
| Incomplete `spec_refs` | Loss of traceability between tasks and requirements | Verify traceability for each task |
| Violating dependencies during linear execution | Rework and blockers at later stages | Strictly follow the order defined by `depends_on` |

---

## Related skills

- `spec-standard` — the basic universal specification standard.
- `task-breakdown-subagent` — the process for the cross-review mode.

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
