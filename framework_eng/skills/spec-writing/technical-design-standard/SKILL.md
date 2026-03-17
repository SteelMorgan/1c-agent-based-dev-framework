---
name: technical-design-standard
description: Technical design standard for 1С development tasks. Defines the structure of technical-design.md, rules for filling sections (MUST/SHOULD/MAY), quality checklist, and guidance on level of detail. Used by the architect (Phase 2) and reviewer (scope=arch).
---

# Technical Design Standard (Technical Design)

Technical design (`technical-design.md`) is the bridge between the specification (WHAT) and task decomposition (HOW). It records architectural decisions, modular structure, contracts, and transversal concepts. It expands the high-level Technical Design section from the specification.

Foundation: Google Design Docs, arc42, MADR 4.0, Stripe RFC (Drawbacks), C4 Model.

---

## 2. When technical design is required

| Task type | TD required | Justification |
|------------|----------|-------------|
| New functionality (medium/complex) | MUST | Captures architecture before development begins |
| Modifying a standard configuration with structural changes | MUST | Need to justify approach (extension vs configuration) |
| Integration with an external system | MUST | Contracts and data flow are critical |
| Simple bug fix | MAY | Only if the bug requires architectural changes |
| Refactoring that changes modular structure | SHOULD | Requires transparency around change boundaries |
| External processing (EPF) with a form | SHOULD | Metadata structure and UI require design. MUST if EPF includes background operations, access rights, or data exchange |

---

## 3. Required structure of technical-design.md

### Title and metadata

```markdown
# Technical Design: [Short title]

| Field | Value |
|------|----------|
| Spec | [SPEC-NNN](link to spec.md) |
| Date | YYYY-MM-DD |
| Status | Draft / Review / Approved |
| Explorer | [explorer-context.md](link) |
| Task Breakdown | [task-breakdown.json](link) |
| ADR Directory | [task_dir/adr/](link) |
```

### Sections and obligation rules

| § | Section | Required | Condition |
|---|--------|---------------|---------|
| 1 | Overview | **MUST** | Always |
| 2 | Solution Strategy | **MUST** | Always |
| 3 | Building Blocks | **MUST** | Always |
| 4 | Data & Metadata | **MUST** | Always |
| 5 | Crosscutting Concepts | **SHOULD** | MUST if the task touches >2 modules or changes transversal behavior |
| 6 | Key Decisions | **MUST** | Always (at least one decision) |
| 7 | Risks & Drawbacks | **MUST** | Always |
| 8 | Assumptions & Open Questions | **SHOULD** | MUST if there are uncertainties blocking part of the design |
| 9 | Migration & Rollback | **Conditional MUST** | MUST if existing metadata objects are altered or data migration is required |
| 10 | Traceability | **MUST** | Always |

**Rule:** If a section is not applicable to the task — mark it `N/A` with a brief reason. Do not remove the section.

---

## 4. Section descriptions

### § 1. Overview

#### 1.1 Goals

What the technical solution must achieve. RFC 2119 formulations (MUST/SHOULD/MAY) are not needed — those already exist in the specification. Here focus on the technical goals of the design.

#### 1.2 Non-goals

**What the design explicitly does NOT address.** This is the most valuable section for preventing scope creep. Each non-goal should be a deliberate exclusion.

#### 1.3 Background

Current state of the system (TOGAF Baseline). Which modules/objects exist and how they currently operate. Reference `explorer-context.md` as the baseline source — do not duplicate it, only expand where the design requires additional detail.

#### 1.4 Constraints

Constraints affecting the architecture:
- Development mode: extension vs changing the main configuration
- 1С platform version and minimum BSP version
- xml-gen limitations (Designer format, not EDT; SKD 85%)
- Organizational constraints (deadlines, server access, licenses)

---

### § 2. Solution Strategy

High-level description of the chosen approach (2–3 paragraphs):
- Which key technological/architectural decisions are taken
- Which patterns are selected and why
- How the approach answers the Goals from §1.1

This is the **strategy**, not the details. Details belong in §3 and §4.

---

### § 3. Building Blocks

#### 3.1 System Context (C4 Level 1)

The system in the context of external systems and users. For integration tasks — a mandatory diagram (textual or ASCII).

For tasks within a single configuration — MAY be a brief description of the affected subsystems.

#### 3.2 Module Map (C4 Level 2–3)

Affected and new modules, their relationships:

```markdown
| Module | Type | New/Existing | Responsibility |
|--------|-----|--------------------|-----------------|
| OM.WorkWithCounterparties | Common module | Existing (modification) | Validation, data retrieval |
| ObjectModule.Counterparties | Object module | Existing (modification) | Write handlers |
```

For complex tasks — textual diagrams of call flows between modules.

#### 3.3 Interfaces & Contracts

Signatures of key procedures/functions with their contracts:

```bsl
// Function CheckTIN(TIN: String): Boolean
//
// Parameters:
//   TIN — String(10) or String(12), not empty
// Return:
//   True — if the TIN is valid according to the control-digit algorithm
// Exception:
//   If the TIN is an empty string — ThrowException
// Directive: &ServerSideWithoutContext
```

---

### § 4. Data & Metadata

#### 4.1 Metadata Objects

Table of all impacted metadata objects:

```markdown
| Object | Type | New/Existing | Changes | DSL |
|--------|-----|-----------|-----------|-----|
| Справочник.Counterparties | Catalog | Existing | +Attribute TIN (String 12) | — |
| РС.ChangeHistory | Information register | New | Period, Object, Author, Description | — |
| Form.ElementForm | Managed form | New | Field TIN, button Validate | [form-dsl.json](artifacts/form-dsl.json) |
| Role.SalesManager | Role | New | Rights to catalog and register | [role-dsl.json](artifacts/role-dsl.json) |
```

**JSON DSL rule:**
- Complex objects (forms, SKD, roles): link to the DSL file in `task_dir/artifacts/` **MUST**; inline fragment in the design **MAY** be included (only key elements needed to understand the architecture)
- Simple objects (catalogs, documents, registers): textual description of the structure **MUST**

#### 4.2 Data Flow

How data moves through the system for key scenarios:

```
User → Form.Counterparty
  → ObjectModule.OnWrite()
    → OM.WorkWithCounterparties.CheckTIN()
    → RS.ChangeHistory.Record
```

For integrations — data flow between systems specifying protocols and formats. For each integration point SHOULD document the NFR contract: timeout, retry policy, idempotency, authentication, error mapping.

---

### § 5. Crosscutting Concepts

Transversal solutions spanning all modules. **SHOULD** document the decision for each applicable aspect:

| Aspect | Decision | Justification |
|--------|---------|-------------|
| **Error handling** | Try/Catch with WriteLog | coding-standards rule 18 |
| **Logging** | Section log via BSP (WriteLog) | ssl-patterns: standard mechanism |
| **Access rights** | Role via xml-gen, RLS not required | Data does not contain organization-level segregation |
| **Transactions** | BeginTransaction/Try for register writes | coding-standards rule 18 |
| **Client/Server** | &ServerSideWithoutContext for business logic | coding-standards rule 3 |
| **BSP usage** | CommonPurpose.NotifyUser for validation | ssl-patterns: validation of required fields |
| **Platform limitations** | [describe workarounds if any] | — |

If all aspects are standard and do not require special decisions — state: “Standard patterns are used; see coding-standards and ssl-patterns. No special solutions.”

---

### § 6. Key Decisions

Brief table of architectural decisions:

```markdown
| # | Decision | Options | Chosen | Justification | ADR |
|---|---------|---------|-------|-------------|-----|
| 1 | History storage | A) Section log, B) Separate register | B | Queries and reports over history are required | [ADR-001](adr/ADR-001.md) |
| 2 | TIN validation | A) Custom algorithm, B) External service | A | No dependency on network | — (trivial) |
```
```

**Rule:** each non-obvious decision (≥2 alternatives with different trade-offs) requires a separate ADR file in `task_dir/adr/`.

**ADR format** (MADR 4.0 lean):

```markdown
# ADR-NNN: [Decision title]

Status: Accepted
Date: YYYY-MM-DD

## Context
[Why the question arose]

## Decision Drivers
- [Driver 1]
- [Driver 2]

## Considered Options
1. [Option A] — description
2. [Option B] — description

## Decision Outcome
Chosen option [X].

### Consequences
- Good: [what improves]
- Bad: [what worsens]

### Confirmation
[How to verify the decision is implemented correctly]
```

---

### § 7. Risks & Drawbacks

#### 7.1 Drawbacks

What will get worse, more complex, or more expensive. If drawbacks are empty — the design has not been analyzed sufficiently.

#### 7.2 Risks

```markdown
| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-------------|---------|------------|
| 1 | Query performance with >100K records | Medium | High | Index + fetch limit |
```

---

### § 8. Assumptions & Open Questions

**Assumptions** — assumptions made when uncertainty exists. They do not block the design but may influence implementation:

```markdown
- Assume the maximum number of counterparties is < 500K
- BSP version 3.1+ (otherwise fallback for LongOperations is required)
```

**Open Questions** — questions left unanswered. They do not block the architecture but require clarification before or during implementation.

---

### § 9. Migration & Rollback (conditional)

**Condition:** section MUST if existing metadata objects are altered or data migration is required. Otherwise — `N/A: new objects, migration not required`.

#### 9.1 Migration Plan
- Update order (configuration → data → rights)
- Data filling/conversion routines
- Phased rollout (if staged deployment is planned)

#### 9.2 Rollback Strategy
- Whether the changes can be rolled back
- What happens to data on rollback
- Point of no return (if any)

---

### § 10. Traceability

Traceability matrix: requirement from the specification → design section → decomposition task.

```markdown
| Spec Requirement | Design Section | Task IDs |
|------------------|---------------|----------|
| MUST-1: TIN validation | §3.3 Interfaces, §4.1 Metadata | T-001, T-003 |
| MUST-2: Change history | §4.1 Metadata, §4.2 Data Flow | T-002 |
| SHOULD-1: History report | §4.1 Metadata (SKD) | T-005 |
```

**Rule:** every MUST from the specification MUST be covered by at least one design section and one task. SHOULD SHOULD be covered.

---

## 5. Technical-design.md quality criteria

Checklist for the reviewer (scope=arch):

### Structure and completeness
- [ ] All MUST sections are filled (or N/A with a reason)
- [ ] Title contains links to spec, explorer-context, task-breakdown
- [ ] Status is correct (Draft when created)

### Overview (§1)
- [ ] Goals describe technical objectives without duplicating specification requirements
- [ ] Non-goals include at least one deliberate exclusion
- [ ] Background builds on explorer-context.md without duplicating it
- [ ] Constraints account for development mode (extension/configuration), platform/BSP version

### Solution Strategy (§2)
- [ ] Strategy addresses every Goal from §1.1
- [ ] Description is at the approach level, not the code level

### Building Blocks (§3)
- [ ] Module Map covers all modules from the specification's scope
- [ ] Interfaces & Contracts include signatures with parameters, return values, and compilation directives
- [ ] There are no implicit dependencies between modules

### Data & Metadata (§4)
- [ ] All metadata objects are listed with types and changes
- [ ] Complex objects (forms, SKD, roles) link to JSON DSL files
- [ ] Data Flow covers key scenarios from the Test Plan in the specification

### Crosscutting Concepts (§5)
- [ ] Decisions for error handling, transactions, rights, client/server boundary
- [ ] BSP usage or rejection is justified (ssl-patterns)
- [ ] Platform limitations with workarounds (if any)

### Key Decisions (§6)
- [ ] Each non-obvious decision has justification
- [ ] ADR files include Consequences and Confirmation
- [ ] No decisions contradict the specification

### Risks & Drawbacks (§7)
- [ ] Drawbacks are not empty — every decision has a cost
- [ ] High risks have mitigation
- [ ] Trade-offs are described honestly (Good + Bad)

### Traceability (§10)
- [ ] Every MUST from the specification is tied to a design section and a task
- [ ] No requirements lack mapping to the design
- [ ] Task IDs match task-breakdown.json

### Task Breakdown JSON
- [ ] All tasks have unique `task_id`
- [ ] `depends_on` are valid and cycle-free
- [ ] `spec_refs` point to existing sections of the specification
- [ ] `task_type` is correct (code/test/migration/docs/analysis/architecture)
- [ ] `done_criteria` are verifiable and specific
- [ ] JSON is stored in a separate file; the design includes the link

### Framework consistency
- [ ] Compatible with the existing configuration (coding-standards)
- [ ] Design is implementable within the specification scope
- [ ] Design does not contradict decisions from the specification's Decision Log

---

## 6. Common mistakes

| Mistake | Consequence |
|--------|------------|
| Empty non-goals | Scope creep |
| Empty drawbacks | Reviewer cannot assess trade-offs |
| Fully inline JSON DSL | Document swells, loses overview → place DSL in artifacts/ |
| Duplicating the specification | Breaks single source of truth |
| Missing traceability | Impossible to verify requirement coverage |
| Filling every section for a simple task | Formal overhead → use N/A |
| Missing constraints | Incompatible approach (EDT vs Designer, BSP version) |

---

## 7. Related skills

Inputs: `spec-standard`. Outputs: `task-breakdown-*`. Criteria: `coding-standards`, `ssl-patterns`. Metadata generation: `xml-generation`.

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/skills/bsl-practices/ssl-patterns/SKILL.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
---
