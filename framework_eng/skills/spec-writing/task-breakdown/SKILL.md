---
name: task-breakdown
description: "Decompose a spec into Task Breakdown JSON"
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
metadata:
  category: spec-writing
---

# Task Breakdown Skill

---

## §1 When to use

| Trigger | Mode |
|---------|-------|
| FREE/linear execution without Reviewer-agent | **Linear** — self-check |
| Full-cycle process with Architect/Reviewer roles | **Subagent** — cross-review + BLOCK iterations |
| Breakdown of a spec is needed before implementation | Any mode — use template + example |
| Execution is performed by one agent step by step | **Linear** |
| Reviewer returned BLOCK | **Subagent** — run the correction loop |

If the orchestration context is unknown, use **Linear** by default.

---

## §2 JSON format for task breakdown

### Required artifact

The breakdown is prepared as a **separate JSON file** (next to the specification or in an agreed project folder).

Format requirements:
- use **template + example**;
- the format is fixed by a strict **JSON Schema** (`references/task-breakdown.schema.json`) - validation is mandatory (see §2a);
- keep the same fields:
  - `task_id`
  - `task_type`
  - `title`
  - `description`
  - `depends_on`
  - `spec_refs`
  - `deliverables`
  - `done_criteria` - an array of 1-3 verifiable task completion criteria

The specification itself must include:
- a link to this JSON file, and/or
- a short summary of stages and dependencies.

### JSON template (template)

```json
{
  "spec_id": "SPEC-NNN",
  "tasks": [
    {
      "task_id": "T1",
      "task_type": "analysis",
      "title": "Short task title",
      "description": "What must be done",
      "depends_on": [],
      "spec_refs": ["Requirements.MUST-1"],
      "deliverables": ["List of expected artifacts"],
      "done_criteria": ["Verifiable task completion criterion"]
    }
  ]
}
```

### Allowed `task_type` values

`analysis` | `design` | `implementation` | `test`

---

## §2a Validation by JSON Schema

The Task Breakdown format is fixed by the strict `references/task-breakdown.schema.json` schema (draft-07, `additionalProperties: false` at the task level). Validation of JSON against this schema is **mandatory**: Architect performs it before handing over the decomposition, and the orchestrator performs it on acceptance.

```bash
python3 -c "import json,jsonschema; jsonschema.validate(json.load(open('task-breakdown.json')), json.load(open('references/task-breakdown.schema.json'))); print('OK')"
```

If `jsonschema` is unavailable, validate by any available means (an online draft-07 validator or a manual check against the schema). It is forbidden to submit or accept invalid JSON.

---

## §3 Linear mode (self-check)

### JSON example

```json
{
  "spec_id": "SPEC-002",
  "tasks": [
    {
      "task_id": "T1",
      "task_type": "analysis",
      "title": "Validation against MUST requirements",
      "description": "Map MUST items from the specification to implementation tasks",
      "depends_on": [],
      "spec_refs": ["Requirements.MUST-1", "Requirements.MUST-2"],
      "deliverables": ["MUST coverage matrix", "List of gaps"],
      "done_criteria": ["Each MUST is matched to at least one implementation task", "Coverage gaps are listed explicitly or the list is empty"]
    },
    {
      "task_id": "T2",
      "task_type": "implementation",
      "title": "Implement core logic",
      "description": "Perform the implementation according to Technical Design",
      "depends_on": ["T1"],
      "spec_refs": ["Technical Design.Modules", "Requirements.MUST-3"],
      "deliverables": ["Code changes", "Local checks"],
      "done_criteria": ["The code matches the module structure of the Technical Design", "Local syntax checks pass without errors"]
    },
    {
      "task_id": "T3",
      "task_type": "test",
      "title": "Validate the test plan",
      "description": "Verify that MUST items are covered by tests from the Test Plan",
      "depends_on": ["T2"],
      "spec_refs": ["Test Plan (TDD)"],
      "deliverables": ["Test results", "List of deviations"],
      "done_criteria": ["All MUST requirements have a passing test", "Deviations are recorded or the list is empty"]
    }
  ]
}
```

### Process (self-check instead of review)

1. Based on the specification, create a separate Task Breakdown JSON.
2. Perform a **consistency self-check**:
   - all MUST items are reflected in the tasks;
   - `depends_on` forms a valid sequence;
   - `spec_refs` point to specific sections/items.
3. Record assumptions if the specification contains ambiguities:
   - list assumptions explicitly;
   - indicate how assumptions affect task order.
4. Execute tasks linearly in dependency order (single-agent execution).
5. Before finishing, repeat the self-check against the actual requirement coverage.

### Self-check checklist

- [ ] Each task has a unique `task_id`.
- [ ] `task_type` matches the real work stage.
- [ ] `depends_on` defines an executable linear order without cycles.
- [ ] `spec_refs` are present and linked to the specification.
- [ ] Each task has `done_criteria` (1-3 verifiable and specific criteria).
- [ ] All MUST requirements have implementation/verification tasks.
- [ ] Assumptions are explicitly recorded and do not conflict with Scope.
- [ ] The JSON validates against `references/task-breakdown.schema.json` (§2a).
- [ ] The specification includes a link/summary to the separate JSON.

### Typical mistakes (Linear)

| Mistake | Consequence |
|--------|------------|
| No self-check before execution | Execution based on a defective plan |
| Unrecorded assumptions | Hidden mismatches with expectations |
| Incomplete `spec_refs` | Loss of traceability |
| Violation of `depends_on` order | Rework in later stages |

---

## §4 Subagent mode (cross-review + BLOCK iterations)

### JSON example

```json
{
  "spec_id": "SPEC-002",
  "tasks": [
    {
      "task_id": "T1",
      "task_type": "analysis",
      "title": "Check metadata objects",
      "description": "Compare the object set with the Technical Design section",
      "depends_on": [],
      "spec_refs": ["Technical Design.Metadata Objects", "Requirements.MUST-1"],
      "deliverables": ["List of verified objects", "List of discrepancies"],
      "done_criteria": ["The object set matches the Technical Design section", "All discrepancies are recorded or the list is empty"]
    },
    {
      "task_id": "T2",
      "task_type": "implementation",
      "title": "Implement document posting",
      "description": "Implement movements and balance checks",
      "depends_on": ["T1"],
      "spec_refs": ["Requirements.MUST-2", "Requirements.MUST-3"],
      "deliverables": ["Object module code", "Tests for MUST requirements"],
      "done_criteria": ["The posting creates movements across all registers from the design", "The balance check is covered by a passing test"]
    }
  ]
}
```

### Process (architecture + JSON → review → BLOCK loop)

1. The Architect forms the work structure based on the specification.
2. The agent prepares a separate Task Breakdown JSON (template + example) and validates it against the JSON Schema (§2a).
3. The Reviewer performs a cross-review of the JSON against the specification and dependencies.
4. If the verdict is **BLOCK**:
   - return for rework;
   - maximum **3 return iterations**.
5. If after 3 returns the comments remain critical:
   - set status **BLOCK > 3**;
   - perform **escalation** (the architect/user decides whether to rebuild the breakdown or clarify the spec).

### JSON quality checklist (review mode)

- [ ] Each task has a unique `task_id`.
- [ ] `task_type` reflects the actual stage (analysis/design/implementation/test, etc.).
- [ ] `depends_on` does not contain cyclic dependencies.
- [ ] `spec_refs` are present for every task and point to specific sections/requirements of the spec.
- [ ] Each task has `done_criteria` (1-3 verifiable and specific criteria).
- [ ] All critical MUST requirements of the specification are covered.
- [ ] The task order is executable considering the dependencies.
- [ ] The JSON validates against `references/task-breakdown.schema.json` (§2a).
- [ ] The specification includes a link/summary to the separate JSON.

### Typical mistakes (Subagent)

| Mistake | Consequence |
|--------|------------|
| `spec_refs` omitted | Loss of traceability |
| Inconsistent `depends_on` | Invalid execution order |
| Format changes between iterations | Increase in review defects |
| Ignoring the BLOCK limit | Endless iterations → escalation does not happen |

---

## §5 When to choose each mode

| Criterion | Linear | Subagent |
|----------|--------|---------|
| Reviewer-agent present | No | Yes |
| Architect role present | Optional | Yes |
| Quality control method | Self-check | Cross-review |
| Iterations on errors | No (fix independently) | Up to 3 BLOCK returns, then escalation |
| Typical context | Simple tasks, one-shot execution | Complex specs, full-cycle pipeline |
| Used by agents | analyst, developer-code | architect |
