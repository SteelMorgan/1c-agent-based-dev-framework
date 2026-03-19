---
name: task-breakdown-subagent
description: Task breakdown for subagent mode. Defines a separate Task Breakdown JSON, cross-review, and BLOCK iterations.
---

# Task breakdown skill (subagent mode)

---

## When to apply

| Trigger | Action |
|---------|----------|
| Full-cycle process with Architect/Reviewer roles | Create a Task Breakdown JSON and submit it for a cross-review |
| Need to break down the spec before implementation | Use the template + example for JSON (without JSON Schema) |
| Reviewer returned BLOCK | Start a remediation loop respecting the iteration limit |

---

## Mandatory artifact

The breakdown should be formatted as a **separate JSON file** (located next to the specification or in an agreed project folder).

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
- a brief extract outlining stages and dependencies.

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
      "title": "Metadata object review",
      "description": "Compare the object set with the Technical Design section",
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

1. Architect forms the work structure based on the specification.
2. The agent prepares a separate Task Breakdown JSON (template + example, without JSON Schema).
3. Reviewer performs a cross-review of the JSON against the spec and dependencies.
4. If the verdict is **BLOCK**:
   - return for revision;
   - up to **3 return iterations** maximum.
5. If critical comments remain after 3 returns:
   - record status **BLOCK > 3**;
   - perform **escalation** (architect/user decides whether to rebuild the breakdown or clarify the spec).

---

## JSON quality checklist (review mode)

- [ ] Each task has a unique `task_id`.
- [ ] `task_type` reflects the actual stage (analysis/design/implementation/test etc.).
- [ ] `depends_on` contains no cyclic dependencies.
- [ ] Every task has `spec_refs` pointing to specific sections/requirements of the spec.
- [ ] All critical MUST requirements of the specification are covered.
- [ ] The task order is feasible given the dependencies.
- [ ] The specification includes a reference/extract about the separate JSON.

---

## Common mistakes

| Mistake | Consequence |
|--------|------------|
| Missing `spec_refs` | Loss of traceability |
| Inconsistent `depends_on` | Invalid execution order |
| Changing the format between iterations | Increase in review defects |
| Ignoring the BLOCK limit | Endless iterations → escalation does not happen |

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
