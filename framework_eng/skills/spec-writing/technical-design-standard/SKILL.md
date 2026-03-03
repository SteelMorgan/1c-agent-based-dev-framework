---
name: technical-design-standard
description: Technical design standard for 1C development tasks. It defines the structure of technical-design.md, the rules for filling sections (MUST/SHOULD/MAY), a quality checklist, and guidance on the level of detail. Used by the architect (Phase 2) and the reviewer (scope=arch).
---

# Technical Design Standard (Technical Design)

## 1. Purpose

This skill defines the **technical design standard** (`technical-design.md`) — the Phase 2 artifact in the framework's phased workflow.

Technical design is the **bridge** between the specification (WHAT needs to be done) and the task decomposition (HOW to do it step by step). It records architectural decisions, module structure, interface contracts, and crosscutting concepts.

**Relation to spec-standard:** the specification contains a high-level Technical Design section (metadata, modules, data flow). `technical-design.md` is the **detailed Phase 2 design** that expands and specifies that section by adding contracts, ADRs, crosscutting concepts, and traceability. The specification = contract “what we do,” the design = contract “how it is arranged.”

**Foundation of the standard:**
- **Google Design Docs** — Goals/Non-goals, Alternatives Considered, pragmatism
- **arc42** — Building Blocks, Crosscutting Concepts, Solution Strategy
- **MADR 4.0** — Architecture Decision Records with Confirmation
- **Stripe RFC** — Drawbacks (honest self-critique of decisions)
- **C4 Model** — multi-level abstraction (System → Module → Procedure)

**Not used** (justification):
- IEEE 1016 / ISO 42010 — academic, low adoption; useful elements (viewpoints, constraints) are taken selectively
- TOGAF — enterprise-level, overkill for development tasks
- Amazon 6-pager — narrative is good for product decisions, not for technical design
- SAP/Oracle TDD — platform-specific; the useful WRICEF taxonomy idea is adapted in §4

---

## 2. When technical design is required

| Task Type | Design Needed | Rationale |
|-----------|---------------|-----------|
| New functionality (medium/complex) | MUST | Captures the architecture before development starts |
| Extension of a standard configuration with structural changes | MUST | Need to justify the choice of approach (extension vs configuration) |
| Integration with an external system | MUST | Contracts and data flow are critical |
| Simple bug fix | MAY | Only if the bug requires architectural changes |
| Refactoring that changes the module structure | SHOULD | Requires clarity on the boundaries of changes |
| External processing (EPF) with a form | SHOULD | Metadata structure and UI require design. MUST if the EPF includes background operations, access rights, or data exchange |

---

## 3. Mandatory structure of technical-design.md

### Header and metadata

```markdown
# Technical Design: [Brief Title]

| Field | Value |
|------|----------|
| Spec | [SPEC-NNN](link to spec.md) |
| Date | YYYY-MM-DD |
| Status | Draft / Review / Approved |
| Explorer | [explorer-context.md](link) |
| Task Breakdown | [task-breakdown.json](link) |
| ADR Directory | [task_dir/adr/](link) |
```

### Sections and mandatoriness rules

| § | Section | Mandatoriness | Condition |
|---|--------|---------------|---------|
| 1 | Overview | **MUST** | Always |
| 2 | Solution Strategy | **MUST** | Always |
| 3 | Building Blocks | **MUST** | Always |
| 4 | Data & Metadata | **MUST** | Always |
| 5 | Crosscutting Concepts | **SHOULD** | MUST if the task touches >2 modules or changes crosscutting behavior |
| 6 | Key Decisions | **MUST** | Always (at least 1 decision) |
| 7 | Risks & Drawbacks | **MUST** | Always |
| 8 | Assumptions & Open Questions | **SHOULD** | MUST if there are uncertainties blocking parts of the design |
| 9 | Migration & Rollback | **Conditional MUST** | MUST if existing metadata objects change or data migration is required |
| 10 | Traceability | **MUST** | Always |

**Rule:** if a section is not applicable to the task — write `N/A` with a brief reason. Do not remove the section.

---

## 4. Section descriptions

### § 1. Overview

> Source: Google Design Docs (Context + Goals + Non-goals)

#### 1.1 Goals

What the technical solution must achieve. Phrasing with RFC 2119 (MUST/SHOULD/MAY) is not required — thats already captured in the specification. Here are the technical goals of the design.

#### 1.2 Non-goals

**What the design explicitly does NOT solve.** This is the most valuable section for preventing scope creep. Every non-goal is a conscious exclusion.

#### 1.3 Background

The current state of the system (TOGAF Baseline). Which modules/objects exist and how they work today. Reference `explorer-context.md` as the baseline source — do not duplicate it, only expand where necessary for the design.

#### 1.4 Constraints

Constraints that influence the architecture:
- Development mode: extension vs modification of the main configuration
- Version of the 1C platform and the minimum version of БСП
- xml-gen limitations (Designer format, not EDT; SKD 85%)
- Organizational constraints (deadlines, server access, licenses)

---

### § 2. Solution Strategy

> Source: arc42 §4

A high-level description of the chosen approach (2–3 paragraphs):
- Which key technological/architectural decisions are taken
- Which patterns are selected and why
- How the approach addresses the Goals from §1.1

This is a **strategy**, not details. Details belong to §3 and §4.

---

### § 3. Building Blocks

> Source: arc42 §5 + C4 Model (zoom: System → Module → Procedure)

#### 3.1 System Context (C4 Level 1)

The system in the context of external systems and users. For integration tasks — a mandatory diagram (text or ASCII).

For tasks within a single configuration — MAY be a brief description of the affected subsystems.

#### 3.2 Module Map (C4 Level 2–3)

Affected and new modules, their connections:

```markdown
| Модуль | Тип | Новый/Существующий | Ответственность |
|--------|-----|--------------------|-----------------|
| ОМ.РаботаСКонтрагентами | Общий модуль | Существующий (модификация) | Валидация, получение данных |
| МодульОбъекта.Контрагенты | Модуль объекта | Существующий (модификация) | Обработчики записи |
```

For complex tasks — a textual scheme of calls between the modules.

#### 3.3 Interfaces & Contracts

Signatures of key procedures/functions with contracts:

```bsl
// Функция ПроверитьИНН(ИНН: Строка): Булево
//
// Параметры:
//   ИНН — Строка(10) или Строка(12), не пустая
// Возврат:
//   Истина — если ИНН корректен по алгоритму проверки контрольных разрядов
// Исключение:
//   Если ИНН пустая строка — ВызватьИсключение
// Директива: &НаСервереБезКонтекста
```

---

### § 4. Data & Metadata

> Source: SAP WRICEF idea (object taxonomy) + 1C specifics through xml-gen

#### 4.1 Metadata Objects

Table of all affected metadata objects:

```markdown
| Объект | Тип | Новый/Сущ. | Изменения | DSL |
|--------|-----|-----------|-----------|-----|
| Справочник.Контрагенты | Справочник | Сущ. | +Реквизит ИНН (Строка 12) | — |
| РС.ИсторияИзменений | Регистр сведений | Новый | Период, Объект, Автор, Описание | — |
| Форма.ФормаЭлемента | Управляемая форма | Новый | Поле ИНН, кнопка Проверить | [form-dsl.json](artifacts/form-dsl.json) |
| Роль.МенеджерПродаж | Роль | Новый | Права на справочник и регистр | [role-dsl.json](artifacts/role-dsl.json) |
```

**Rule for JSON DSL:**
- Complex objects (forms, SKD, roles): a link to the DSL file in `task_dir/artifacts/` is **MUST**; an inline fragment in the design is **MAY** (only for key elements to understand the architecture)
- Simple objects (directories, documents, registers): textual description of the structure is **MUST**

#### 4.2 Data Flow

How data flows through the system for key scenarios:

```
Пользователь → Форма.Контрагент
  → МодульОбъекта.ПриЗаписи()
    → ОМ.РаботаСКонтрагентами.ПроверитьИНН()
    → РС.ИсторияИзменений.Запись
```

For integrations — data flow between systems with protocols and formats specified. For each integration point SHOULD specify an NFR contract: timeout, retry policy, idempotency, authentication, error mapping.

---

### § 5. Crosscutting Concepts

> Source: arc42 §8

Crosscutting solutions that span all modules. **SHOULD** describe a solution for each applicable aspect:

| Aspect | Solution | Rationale |
|--------|---------|-------------|
| **Error handling** | Attempt/Exception with ЗаписьЖурналаРегистрации | coding-standards rule 18 |
| **Logging** | ЖР via БСП (ЗаписьЖурналаРегистрации) | ssl-patterns: standard mechanism |
| **Access rights** | Role via xml-gen, RLS not required | Data has no organization-based separation |
| **Transactions** | НачатьТранзакцию/Попытка for register writes | coding-standards rule 18 |
| **Client/Server** | &НаСервереБезКонтекста for business logic | coding-standards rule 3 |
| **Use of БСП** | ОбщегоНазначения.СообщитьПользователю for validation | ssl-patterns: completion check |
| **Platform constraints** | [describe if there are workarounds] | — |

If all aspects are standard and do not require special solutions — write: “Standard patterns are used, see coding-standards and ssl-patterns. No special solutions are required.”

---

### § 6. Key Decisions

> Source: MADR 4.0 (summary + links to ADR)

A short table of architectural decisions:

```markdown
| # | Decision | Options | Choice | Rationale | ADR |
|---|---------|---------|-------|-------------|-----|
| 1 | History storage | A) ЖР, B) Separate register | B | Queries and reports require history | [ADR-001](adr/ADR-001.md) |
| 2 | ИНН validation | A) Custom algorithm, B) External service | A | No dependency on the network | — (trivial) |
```

**Rule:** for each non-obvious decision (≥2 alternatives with different trade-offs) — a separate ADR file in `task_dir/adr/`.

**ADR format** (MADR 4.0 lean):

```markdown
# ADR-NNN: [Decision Name]

Status: Accepted
Date: YYYY-MM-DD

## Context
[Why the question arose]

## Decision Drivers
- [Factor 1]
- [Factor 2]

## Considered Options
1. [Option A] — description
2. [Option B] — description

## Decision Outcome
Option [X] is chosen.

### Consequences
- Good: [what improves]
- Bad: [what worsens]

### Confirmation
[How to verify that the decision is implemented correctly]
```

---

### § 7. Risks & Drawbacks

> Source: Stripe RFC (Drawbacks) + arc42 §11 (Risks)

#### 7.1 Drawbacks

**Why the chosen solution is bad.** Honest self-critique — what will become worse, more complex, more expensive.

Every design has a cost. If drawbacks are absent — the design has not been analyzed deeply enough.

#### 7.2 Risks

```markdown
| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-------------|---------|------------|
| 1 | Query performance with >100K records | Medium | High | Index + fetch limit |
```

---

### § 8. Assumptions & Open Questions

> Source: architect protocol (architect.md)

**Assumptions** — premises adopted under uncertainty. They do not block the design but may affect implementation:

```markdown
- Assume the maximum number of counterparties < 500K
- БСП version 3.1+ (otherwise fallback for ДлительныеОперации is required)
```

**Open Questions** — questions that remain unanswered. They do not block the architecture but require clarification before or during implementation.

---

### § 9. Migration & Rollback (conditional)

> Source: Uber Design Docs (migration strategy)

**Condition:** the section is MUST if existing metadata objects change or data migration is required. Otherwise — `N/A: new objects, migration not required`.

#### 9.1 Migration Plan
- Update order (configuration → data → rights)
- Processes for data filling / conversion
- Phasing (if rollout is staged)

#### 9.2 Rollback Strategy
- Whether the changes can be rolled back
- What happens to the data upon rollback
- Point of no return (if any)

---

### § 10. Traceability

> Source: IEEE 1016 (Traceability Matrix) + task-breakdown-template.json (spec_refs)

Traceability matrix: specification requirement → design section → task from decomposition.

```markdown
| Spec Requirement | Design Section | Task IDs |
|------------------|---------------|----------|
| MUST-1: ИНН validation | §3.3 Interfaces, §4.1 Metadata | T-001, T-003 |
| MUST-2: Change history | §4.1 Metadata, §4.2 Data Flow | T-002 |
| SHOULD-1: History report | §4.1 Metadata (SKD) | T-005 |
```

**Rule:** every MUST from the specification MUST be covered by at least one design section and one task. SHOULD — SHOULD be covered.

---

## 5. Quality criteria for technical-design.md

Checklist for the reviewer (scope=arch):

### Structure and completeness
- [ ] All MUST sections are filled (or N/A with a reason)
- [ ] The header contains links to the spec, explorer-context, and task breakdown
- [ ] Status is correct (Draft upon creation)

### Overview (§1)
- [ ] Goals describe the technical aims without duplicating specification requirements
- [ ] Non-goals contain at least 1 conscious exclusion
- [ ] Background relies on explorer-context.md without duplicating it
- [ ] Constraints include: development mode (extension/configuration), platform/БСП version

### Solution Strategy (§2)
- [ ] The strategy answers each Goal from §1.1
- [ ] Description is at the approach level, not code level

### Building Blocks (§3)
- [ ] Module Map covers all modules in the specification scope
- [ ] Interfaces & Contracts include signatures with parameters, return values, and compilation directives
- [ ] There are no implicit dependencies between modules

### Data & Metadata (§4)
- [ ] All metadata objects are listed with types and changes
- [ ] Complex objects (forms, SKD, roles) link to JSON DSL files
- [ ] Data Flow covers key scenarios from the Test Plan of the specification

### Crosscutting Concepts (§5)
- [ ] Decisions on error handling, transactions, rights, client/server boundary
- [ ] Justified use or rejection of БСП mechanisms (ssl-patterns)
- [ ] Platform constraints with workarounds (if any)

### Key Decisions (§6)
- [ ] Each non-obvious decision has rationale
- [ ] ADR files contain Consequences and Confirmation
- [ ] No decisions contradict the specification

### Risks & Drawbacks (§7)
- [ ] Drawbacks are not empty — every decision has a cost
- [ ] High risks have mitigation
- [ ] Trade-offs are described honestly (Good + Bad)

### Traceability (§10)
- [ ] Every MUST from the specification is covered by a design section and a task
- [ ] There are no requirements without a design link
- [ ] Task IDs match those in task-breakdown.json

### Task Breakdown JSON
- [ ] All tasks have unique `task_id`
- [ ] `depends_on` entries are valid and cycle-free
- [ ] `spec_refs` reference existing specification sections
- [ ] `task_type` is correct (code/test/migration/docs/analysis/architecture)
- [ ] `done_criteria` are verifiable and specific
- [ ] JSON is stored in a separate file, referenced from the design

### Alignment with the framework
- [ ] Compatibility with the existing configuration (coding-standards)
- [ ] The design is implementable within the specification scope
- [ ] The design does not contradict decisions in the specification's Decision Log

---

## 6. Common mistakes

| Mistake | Consequence | How to avoid |
|--------|------------|-------------|
| Empty Non-goals | Scope creep — developers implement “bonuses” not covered by the design | Before finalizing §1.2 ask: “what is NOT included in this solution?” |
| Empty Drawbacks | Illusion of no alternatives; the reviewer cannot assess trade-offs | For each decision ask: “what will become worse/more expensive?” |
| Entire JSON DSL inline | Design doc swells to 100+ lines of JSON; architectural overview is lost | Store the DSL file in artifacts/, keeping only key fragments in the design |
| Duplicating the specification | Violates the single source of truth; the design becomes outdated when the spec changes | Provide a summary + link to spec.md; do not copy requirements |
| Missing Traceability | Reviewer cannot verify requirement coverage | Fill §10 as work progresses, not at the end |
| Filling all sections for a simple task | Overhead; design feels formal and not useful | Use N/A with a reason for non-applicable sections |
| Missing Constraints | Developers choose an incompatible approach (EDT instead of Designer, old БСП version) | Fill §1.4 first — constraints shape the solution space |

---

## 7. Related resources

- [spec-standard](../spec-standard/SKILL.md) — specification standard (the input artifact for the architect)
- [task-breakdown-template](../../../../docs/task-breakdown-template.json) — task decomposition template (the architect's output artifact)
- [architect](../../../subagents/architect.md) — subagent that uses this standard
- [reviewer](../../../subagents/reviewer.md) — subagent reviewing the artifact against the §5 checklist
- [xml-generation](../../tool-usage/xml-generation/xml-generation/SKILL.md) — XML metadata generation module
- [ssl-patterns](../../bsl-practices/ssl-patterns/SKILL.md) — БСП patterns (criteria for §5 Crosscutting)
- [coding-standards](../../bsl-practices/coding-standards/SKILL.md) — coding standards (criteria for §3.3 Contracts)
- [cross-review-policy](../../../rules/cross-review-policy.md) — policy for cross-reviewing artifacts

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/skills/bsl-practices/ssl-patterns/SKILL.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
---
