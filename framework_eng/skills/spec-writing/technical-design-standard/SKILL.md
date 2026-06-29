---
name: technical-design-standard
description: "For technical-design.md in 1C with MUST/SHOULD/MAY"
---

# Technical Design Standard (Technical Design)

Technical design (`technical-design.md`) is a bridge between the specification (WHAT) and task decomposition (HOW). It records architectural decisions, modular structure, contracts, and cross-cutting concepts. It extends the high-level Technical Design section from the specification.

Foundation: Google Design Docs, arc42, MADR 4.0, Stripe RFC (Drawbacks), C4 Model.

---

## 2. Document Language

The technical design MUST be written in **Russian** - section headings, descriptions, justifications, tables. Exception - code and metadata identifiers (module names, attributes, variables, BSL signatures), as well as established terms (ADR, RFC 2119, C4, MUST/SHOULD/MAY).

---

## 3. When Technical Design Is Needed

| Task Type | TD Required | Justification |
|------------|-------------|---------------|
| New functionality (medium/complex) | MUST | Fixes the architecture before development starts |
| Enhancing a standard configuration with structural changes | MUST | The approach choice must be justified (extension vs configuration) |
| Integration with an external system | MUST | Contracts and data flow are critical |
| Simple bug fix | MAY | Only if the bug requires architectural changes |
| Refactoring with changes to modular structure | SHOULD | Visibility into change boundaries is needed |
| External processing (EPF) with a form | SHOULD | Metadata and UI structure require design. MUST if the EPF includes background operations, access rights, or data exchange |

---

## 4. Mandatory Structure of technical-design.md

### Title and Metadata

```markdown
# Technical Design: [Short Name]

| Field | Value |
|------|----------|
| Specification | [SPEC-NNN](link to spec.md) |
| Date | YYYY-MM-DD |
| Status | Draft / Review / Approved |
| Explorer | [explorer-context.md](link) |
| Decomposition | [task-breakdown.json](link) |
| ADR Directory | [task_dir/adr/](link) |
```

### Sections and Mandatory Rules

| § | Section | Requirement | Condition |
|---|--------|-------------|----------|
| 1 | Overview | **MUST** | Always |
| 2 | Solution Strategy | **MUST** | Always |
| 3 | Structural Blocks | **MUST** | Always |
| 4 | Data and Metadata | **MUST** | Always |
| 5 | Cross-Cutting Concepts | **SHOULD** | MUST if the task affects >2 modules or changes cross-cutting behavior |
| 6 | Key Decisions | **MUST** | Always (at least 1 decision) |
| 7 | Risks and Drawbacks | **MUST** | Always |
| 8 | Assumptions and Open Questions | **SHOULD** | MUST if there are uncertainties blocking part of the design |
| 9 | Migration and Rollback | **Conditional MUST** | MUST if existing metadata objects are changed or data migration is required |
| 10 | Traceability | **MUST** | Always |

**Rule:** if a section is not applicable to the task - specify `N/A` with a brief reason. Do not remove the section.

---

## 5. Section Descriptions

### § 1. Overview

#### 1.1 Goals

What must be achieved by the technical solution. Wording through RFC 2119 (MUST/SHOULD/MAY) is not needed - they are already in the specification. Here - the technical goals of the design.

#### 1.2 Non-goals

**What the design explicitly does NOT solve.** The most valuable section for preventing scope creep. Each non-goal is an intentional exclusion.

#### 1.3 Background

The current state of the system (TOGAF Baseline). Which modules/objects exist, how they work now. Link to `explorer-context.md` as the source of truth - do not duplicate it, only expand where needed for the design.

#### 1.4 Constraints

Constraints that affect the architecture:
- Development mode: extension vs modification of the main configuration
- Version of the 1C platform and minimum version of БСП
- xml-gen limitations (Designer format, not EDT; SKD 85%)
- Organizational constraints (timelines, server access, licenses)

---

### § 2. Solution Strategy

High-level description of the chosen approach (2-3 paragraphs):
- Which key technological/architectural decisions were made
- Which patterns were chosen and why
- How the approach addresses the Goals from §1.1

This is a **strategy**, not details. Details are in §3 and §4.

---

### § 3. Structural Blocks

#### 3.1 System Context (C4 Level 1)

The system in the context of external systems and users. For integration tasks - a mandatory diagram (text or ASCII).

For tasks within a single configuration - MAY be a brief description of the affected subsystems.

#### 3.2 Module Map (C4 Level 2-3)

Affected and new modules, their relationships:

```markdown
| Module | Type | New/Existing | Responsibility |
|--------|-----|--------------|-----------------|
| ОМ.РаботаСКонтрагентами | Общий модуль | Существующий (модификация) | Валидация, получение данных |
| МодульОбъекта.Контрагенты | Модуль объекта | Существующий (модификация) | Обработчики записи |
```

For complex tasks - a text call diagram between modules.

#### 3.3 Interfaces and Contracts

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

### § 4. Data and Metadata

#### 4.1 Metadata Objects

Table of all affected metadata objects:

```markdown
| Object | Type | New/Ex. | Changes | DSL |
|--------|-----|--------|---------|-----|
| Справочник.Контрагенты | Справочник | Сущ. | +Реквизит ИНН (Строка 12) | — |
| РС.ИсторияИзменений | Регистр сведений | Новый | Период, Объект, Автор, Описание | — |
| Форма.ФормаЭлемента | Управляемая форма | Новый | Поле ИНН, кнопка Проверить | [form-dsl.json](artifacts/form-dsl.json) |
| Роль.МенеджерПродаж | Роль | Новый | Права на справочник и регистр | [role-dsl.json](artifacts/role-dsl.json) |
```

**Rule for JSON DSL:**
- Complex objects (forms, SKD, roles): link to the DSL file in `task_dir/artifacts/` **MUST**; inline fragment in the design **MAY** (only the key elements needed to understand the architecture)
- Simple objects (catalogs, documents, registers): textual description of the structure **MUST**

#### 4.2 Data Flow

How data moves through the system for key scenarios:

```
Пользователь → Форма.Контрагент
  → МодульОбъекта.ПриЗаписи()
    → ОМ.РаботаСКонтрагентами.ПроверитьИНН()
    → РС.ИсторияИзменений.Запись
```

For integrations - data flow between systems with protocols and formats specified. For each integration point SHOULD specify an NFR contract: timeout, retry policy, idempotency, authentication, error mapping.

---

### § 5. Cross-Cutting Concepts

Cross-cutting solutions that permeate all modules. **SHOULD** specify a solution for each applicable aspect:

| Aspect | Solution | Justification |
|--------|---------|---------------|
| **Error handling** | Try/Exception with WriteLogEvent | coding-standards rule 18 |
| **Logging** | Event log via БСП (WriteLogEvent) | ssl-patterns: standard mechanism |
| **Access rights** | Role via xml-gen, no RLS required | Data does not contain organization-based segregation |
| **Transactions** | BeginTransaction/Try for writing to the register | coding-standards rule 18 |
| **Client/Server** | &OnServerWithoutContext for business logic | coding-standards rule 3 |
| **Using БСП** | CommonPurpose.NotifyUser for validation | ssl-patterns: input validation |
| **Platform limitations** | [describe if there are workarounds] | — |

If all aspects are standard and do not require special solutions - specify: "Standard patterns are used, see coding-standards and ssl-patterns. No special solutions."

---

### § 6. Key Decisions

Brief table of architectural decisions:

```markdown
| # | Decision | Options | Choice | Justification | ADR |
|---|---------|---------|-------|-------------|-----|
| 1 | Хранение истории | A) ЖР, B) Отдельный регистр | B | Нужны запросы и отчёты по истории | [ADR-001](adr/ADR-001.md) |
| 2 | Валидация ИНН | A) Свой алгоритм, B) Внешний сервис | A | Нет зависимости от сети | — (тривиальное) |
```

**Rule:** for each non-obvious decision (≥2 alternatives with different trade-offs) - a separate ADR file in `task_dir/adr/`.

**ADR format** (MADR 4.0 lean):

```markdown
# ADR-NNN: [Decision Name]

Status: Accepted
Date: YYYY-MM-DD

## Context
[Почему возник вопрос]

## Decision Drivers
- [Factor 1]
- [Factor 2]

## Considered Options
1. [Вариант A] — описание
2. [Вариант B] — описание

## Decision Outcome
Выбран вариант [X].

### Consequences
- Good: [что улучшится]
- Bad: [что ухудшится]

### Confirmation
[Как проверить, что решение реализовано корректно]
```

---

### § 7. Risks and Drawbacks

#### 7.1 Drawbacks

What will get worse, harder, more expensive. If drawbacks are empty, the design has not been analyzed enough.

#### 7.2 Risks

```markdown
| # | Risk | Probability | Impact | Mitigation |
|---|------|-------------|---------|------------|
| 1 | Производительность запроса при >100K записей | Средняя | High | Индекс + лимит выборки |
```

---

### § 8. Assumptions and Open Questions

**Assumptions** - accepted under uncertainty. They do not block the design, but may affect implementation:

```markdown
- Предполагаем, что максимальное кол-во контрагентов < 500K
- БСП версии 3.1+ (иначе нужен fallback для ДлительныеОперации)
```

**Open questions** - left unanswered. They do not block the architecture, but require clarification before or during implementation.

---

### § 9. Migration and Rollback (conditional)

**Condition:** the section MUST if existing metadata objects are changed or data migration is required. Otherwise - `N/A: new objects, no migration required`.

#### 9.1 Migration Plan
- Update order (configuration → data → rights)
- Fill / data conversion processing
- Phasing (if phased rollout is used)

#### 9.2 Rollback Strategy
- Whether the changes can be rolled back
- What happens to the data on rollback
- Point of no return (if any)

---

### § 10. Traceability

Traceability matrix: requirement from the specification → design section → task from decomposition.

```markdown
| Spec Requirement | Design Section | Task IDs |
|------------------|---------------|----------|
| MUST-1: Валидация ИНН | §3.3 Interfaces, §4.1 Metadata | T-001, T-003 |
| MUST-2: История изменений | §4.1 Metadata, §4.2 Data Flow | T-002 |
| SHOULD-1: Отчёт по истории | §4.1 Metadata (SKD) | T-005 |
```

**Rule:** each MUST from the specification MUST be covered by at least one design section and one task. SHOULD - SHOULD be covered.

---

## 6. Quality Criteria for technical-design.md

Reviewer checklist (scope=arch):

### Structure and Completeness
- [ ] All MUST sections are filled in (or N/A with a reason)
- [ ] The title contains links to spec, explorer-context, task-breakdown
- [ ] Status is correct (Draft when created)

### Overview (§1)
- [ ] Goals describe technical goals, do not duplicate specification requirements
- [ ] Non-goals contain at least 1 intentional exclusion
- [ ] Background relies on explorer-context.md, does not duplicate it
- [ ] Constraints take into account: development mode (extension/configuration), platform/БСП version

### Solution Strategy (§2)
- [ ] The strategy answers each Goal from §1.1
- [ ] The description is at the approach level, not at the code level

### Structural Blocks (§3)
- [ ] The module map covers all modules from the specification scope
- [ ] Interfaces and contracts contain signatures with parameters, return value, compile directives
- [ ] There are no implicit dependencies between modules

### Data and Metadata (§4)
- [ ] All metadata objects are listed with types and changes
- [ ] Complex objects (forms, SKD, roles) have a link to the JSON DSL file
- [ ] The data flow covers key scenarios from the test plan

### Cross-Cutting Concepts (§5)
- [ ] Solutions for error handling, transactions, rights, client/server boundary
- [ ] The use or refusal of БСП mechanisms is justified (ssl-patterns)
- [ ] Platform limitations with workarounds (if any)

### Key Decisions (§6)
- [ ] For each non-obvious decision (≥2 alternatives) there is justification
- [ ] ADR files contain consequences and confirmation
- [ ] There are no decisions that contradict the specification

### Risks and Drawbacks (§7)
- [ ] Drawbacks are not empty - every decision has a cost
- [ ] High risks have a mitigation plan
- [ ] Trade-offs are described honestly (pros + cons)

### Traceability (§10)
- [ ] Each MUST from the specification is covered by a design section and a task
- [ ] There are no requirements without a design reference
- [ ] Task IDs match task-breakdown.json

### Task Decomposition (JSON)
- [ ] All tasks have unique `task_id`
- [ ] `depends_on` values are valid and contain no cycles
- [ ] `spec_refs` point to existing specification sections
- [ ] `task_type` is correct (code/test/migration/docs/analysis/architecture)
- [ ] `done_criteria` are verifiable and concrete
- [ ] JSON is stored in a separate file, and the design contains a link

### Consistency with the Framework
- [ ] The document is written in Russian (except for code identifiers and established terms)
- [ ] Compatibility with the existing configuration (coding-standards)
- [ ] The design is implementable within the specification scope
- [ ] The design does not contradict decisions from the specification Decision Log

---

## 7. Typical Mistakes

| Mistake | Consequence |
|--------|------------|
| Non-goals empty | Scope creep |
| Drawbacks empty | Reviewer cannot assess trade-offs |
| JSON DSL fully inline | Document balloons, overview is lost → DSL in artifacts/ |
| Duplication of the specification | Violation of single source of truth |
| Traceability missing | Requirement coverage cannot be verified |
| All sections filled out for a simple task | Formal overhead → use N/A |
| Constraints not specified | Incompatible approach (EDT vs Designer, БСП version) |

---

## 8. Related Skills

Inputs: `spec-standard`. Outputs: `task-breakdown-*`. Criteria: `coding-standards`, `ssl-patterns`. Metadata generation: `xml-generation`.

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/skills/bsl-practices/ssl-patterns/SKILL.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
---
