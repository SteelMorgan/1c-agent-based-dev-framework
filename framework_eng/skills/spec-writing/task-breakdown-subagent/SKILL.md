---
name: task-breakdown-subagent
description: Task decomposition for subagent mode. Defines a standalone Task Breakdown JSON, cross-review, and BLOCK iterations.
---

# Task decomposition skill (subagent mode)

## Purpose

This skill defines the task decomposition process for a mode with specialized roles (for example, Architect/Reviewer) and applies cross-review.

The skill describes:
- a unified artifact format for decomposition (a separate JSON file);
- a quality check workflow via Reviewer;
- the BLOCK remark handling cycle (up to 3 returns, then escalation).

---

## When to apply

| Trigger | Action |
|---------|--------|
| Full-cycle workflow with Architect/Reviewer roles | Create a Task Breakdown JSON and send it for cross-review |
| Decomposition is needed before implementation | Use the template + example for the JSON (without JSON Schema) |
| Reviewer returned BLOCK | Start a fix cycle respecting the iteration limit |

---

## Mandatory artifact

The decomposition is documented as a **separate JSON file** (next to the specification or in the agreed project folder).

Format requirements:
- use the **template + example**;
- **do not use JSON Schema**;
- keep the common fields:
  - `task_id`
  - `task_type`
  - `depends_on`
  - `spec_refs`

The specification itself must include:
- a reference to this JSON file, and/or
- a brief summary of stages and dependencies.

---

## JSON template

```json
{
  "spec_id": "SPEC-NNN",
  "tasks": [
    {
      "task_id": "T1",
      "task_type": "analysis",
      "title": "Short task title",
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
      "title": "Metadata object review",
      "description": "Compare the set of objects with the Technical Design section",
      "depends_on": [],
      "spec_refs": ["Technical Design.Metadata Objects", "Requirements.MUST-1"],
      "deliverables": ["List of reviewed objects", "Discrepancy report"]
    },
    {
      "task_id": "T2",
      "task_type": "implementation",
      "title": "Implement document posting",
      "description": "Implement movements and balance checks",
      "depends_on": ["T1"],
      "spec_refs": ["Requirements.MUST-2", "Requirements.MUST-3"],
      "deliverables": ["Object module code", "Tests covering MUST requirements"]
    }
  ]
}
```

---

## Process (architecture + JSON → review → BLOCK loop)

1. The Architect forms the work structure based on the specification.
2. The agent prepares the separate Task Breakdown JSON (template + example, without JSON Schema).
3. The Reviewer performs a cross-review of the JSON against the specification and dependencies.
4. If the verdict is **BLOCK**:
   - return for refinement;
   - a maximum of **3 return iterations**.
5. If after 3 returns the remarks remain critical:
   - record the status **BLOCK > 3**;
   - perform **escalation** (the architect/user decides whether to rebuild the decomposition or clarify the specification).

---

## JSON quality checklist (with review)

- [ ] Every task has a unique `task_id`.
- [ ] `task_type` reflects the actual phase (analysis/design/implementation/test etc.).
- [ ] `depends_on` contains no cyclic dependencies.
- [ ] Each task has `spec_refs` pointing to specific sections/requirements of the specification.
- [ ] All critical MUST requirements of the specification are covered.
- [ ] Task ordering is feasible considering dependencies.
- [ ] The specification includes a reference/summary for the separate JSON.

---

## Common mistakes

| Mistake | Consequence | How to avoid |
|--------|------------|--------------|
| Missing `spec_refs` | Loss of traceability between tasks and requirements | Link each task to the relevant specification section/item |
| Uncoordinated `depends_on` | Inability to establish a correct execution order | Verify the dependency DAG before the review |
| Changing the format between iterations | Increased review defects and invalid integrations | Maintain a stable template + example format |
| Ignoring the BLOCK limit | Endless iterations without resolution | Enforce the rule of ≤3 returns, then escalate |

---

## Related skills

- `spec-standard` — base universal specification standard.
- `task-breakdown-linear` — alternative process for the linear single-agent mode.

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
