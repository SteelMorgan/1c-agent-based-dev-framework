---
name: technical-design-standard
description: "For technical-design.md with MUST/SHOULD/MAY for 1C"
---

# Technical Design Standard (Technical Design)

Technical design (`technical-design.md`) is a bridge between the specification (WHAT) and task decomposition (HOW). It records architectural decisions, module structure, contracts, and end-to-end concepts. It extends the high-level Technical Design section from the specification.

Basis: Google Design Docs, arc42, MADR 4.0, Stripe RFC (Drawbacks), C4 Model.

---

## 2. Document Language

Technical design MUST be written in **Russian** — section headings, descriptions, rationales, tables. The exception is code and metadata identifiers (module names, attributes, variables, BSL signatures), as well as established terms (ADR, RFC 2119, C4, MUST/SHOULD/MAY).

---

## 3. When Technical Design Is Needed

| Task type | TD needed | Rationale |
|------------|----------|-------------|
| New functionality (medium/complex) | MUST | Fixes the architecture before development starts |
| Enhancement of a standard configuration with structural changes | MUST | The approach choice must be justified (extension vs configuration) |
| Integration with an external system | MUST | Contracts and data flow are critical |
| Simple bug fix | MAY | Only if the bug requires architectural changes |
| Refactoring with changes to the module structure | SHOULD | Transparency about change boundaries is needed |
| External processing (EPF) with a form | SHOULD | Metadata structure and UI require design. MUST if the EPF includes background operations, access rights, or data exchange |

---

## 4. Mandatory Structure of technical-design.md

### Title and Metadata

```markdown
# Технический дизайн: [Краткое название]

| Поле | Значение |
|------|----------|
| Спецификация | [SPEC-NNN](ссылка на spec.md) |
| Дата | YYYY-MM-DD |
| Статус | Черновик / Ревью / Утверждён |
| Explorer | [explorer-context.md](ссылка) |
| Декомпозиция | [task-breakdown.json](ссылка) |
| Каталог ADR | [task_dir/adr/](ссылка) |
```

### Sections and mandatory rules

| § | Section | Mandatory | Condition |
|---|--------|---------------|---------|
| 1 | Overview | **MUST** | Always |
| 2 | Solution Strategy | **MUST** | Always |
| 3 | Structural Blocks | **MUST** | Always |
| 4 | Data and Metadata | **MUST** | Always |
| 5 | Cross-Cutting Concepts | **SHOULD** | MUST if the task affects >2 modules or changes cross-cutting behavior |
| 6 | Key Decisions | **MUST** | Always (minimum 1 decision) |
| 7 | Risks and Drawbacks | **MUST** | Always |
| 8 | Assumptions and Open Questions | **SHOULD** | MUST if there are uncertainties blocking part of the design |
| 9 | Migration and Rollback | **Conditional MUST** | MUST if existing metadata objects are changed or data migration is required |
| 10 | Traceability | **MUST** | Always |

**Rule:** if a section does not apply to the task, mark it as `N/A` with a brief reason. Do not remove the section.

---

## 5. Section Descriptions

### § 1. Overview

#### 1.1 Goals

What the technical solution should achieve. Formulations using RFC 2119 (MUST/SHOULD/MAY) are not needed - they are already in the specification. Here, these are the technical goals of the design.

#### 1.2 Non-goals

**What the design explicitly does NOT solve.** The most valuable section for preventing scope creep. Each non-goal is a deliberate exclusion.

#### 1.3 Background

The current state of the system (TOGAF Baseline). Which modules/objects exist, how they work now. Link to `explorer-context.md` as the primary source - do not duplicate it, only expand where needed for the design.

#### 1.4 Constraints

Constraints affecting the architecture:
- Development mode: extension vs changing the main configuration
- 1C platform version and minimum БСП version
- xml-gen limitations (Designer format, not EDT; SKD 85%)
- Organizational constraints (deadlines, server access, licenses)

---

### § 2. Solution Strategy

High-level description of the chosen approach (2–3 paragraphs):
- What key technological/architectural decisions were made
- What patterns were chosen and why
- How the approach addresses the Goals from §1.1

This is the **strategy**, not the details. The details are in §3 and §4.

---

### § 3. Structural Blocks

#### 3.1 System Context (C4 Level 1)

The system in the context of external systems and users. For integration tasks, an обязательная diagram (text or ASCII) is required.

For tasks within a single configuration, MAY be a brief description of the affected subsystems.

#### 3.2 Module Map (C4 Level 2–3)

Affected and new modules, their relationships:

```markdown
| Модуль | Тип | Новый/Существующий | Ответственность |
|--------|-----|--------------------|-----------------|
| ОМ.РаботаСКонтрагентами | Общий модуль | Существующий (модификация) | Валидация, получение данных |
| МодульОбъекта.Контрагенты | Модуль объекта | Существующий (модификация) | Обработчики записи |
```

For complex tasks, a textual call diagram between modules.

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
| Объект | Тип | Новый/Сущ. | Изменения | DSL |
|--------|-----|-----------|-----------|-----|
| Справочник.Контрагенты | Справочник | Сущ. | +Реквизит ИНН (Строка 12) | — |
| РС.ИсторияИзменений | Регистр сведений | Новый | Период, Объект, Автор, Описание | — |
| Форма.ФормаЭлемента | Управляемая форма | Новый | Поле ИНН, кнопка Проверить | [form-dsl.json](artifacts/form-dsl.json) |
| Роль.МенеджерПродаж | Роль | Новый | Права на справочник и регистр | [role-dsl.json](artifacts/role-dsl.json) |
```

**JSON DSL rule:**
- Complex objects (forms, SKD, roles): link to the DSL file in `task_dir/artifacts/` **MUST**; inline fragment in the design **MAY** (only key elements needed to understand the architecture)
- Simple objects (catalogs, documents, registers): textual description of the structure **MUST**

#### 4.2 Data Flow

How data moves through the system for key scenarios:

```
Пользователь → Форма.Контрагент
  → МодульОбъекта.ПриЗаписи()
    → ОМ.РаботаСКонтрагентами.ПроверитьИНН()
    → РС.ИсторияИзменений.Запись
```

For integrations — data flow between systems with protocols and formats specified. For each integration point SHOULD specify an NFR contract: timeout, retry policy, idempotency, authentication, error mapping.

---

### § 5. Cross-Cutting Concepts

Cross-cutting solutions that permeate all modules. **SHOULD** specify a solution for each applicable aspect:

| Aspect | Solution | Justification |
|--------|---------|-------------|
| **Error handling** | Try/Exception with ЗаписьЖурналаРегистрации | coding-standards rule 18 |
| **Logging** | ЖР via БСП (ЗаписьЖурналаРегистрации) | ssl-patterns: standard mechanism |
| **Access rights** | Role via xml-gen, RLS not required | Data does not contain segregation by organization |
| **Transactions** | StartTransaction/Try for writing to the register | coding-standards rule 18 |
| **Client/Server** | &НаСервереБезКонтекста for business logic | coding-standards rule 3 |
| **Use of БСП** | ОбщегоНазначения.СообщитьПользователю for validation | ssl-patterns: filling check |
| **Platform limitations** | [describe if there are workarounds] | — |

If all aspects are standard and do not require special solutions, indicate: "Standard patterns are used, see coding-standards and ssl-patterns. There are no special solutions."

---

### § 6. Key Decisions

Brief table of architectural decisions:

```markdown
| # | Решение | Варианты | Выбор | Обоснование | ADR |
|---|---------|---------|-------|-------------|-----|
| 1 | Хранение истории | A) ЖР, B) Отдельный регистр | B | Нужны запросы и отчёты по истории | [ADR-001](adr/ADR-001.md) |
| 2 | Валидация ИНН | A) Свой алгоритм, B) Внешний сервис | A | Нет зависимости от сети | — (тривиальное) |
```

**Rule:** for each non-obvious decision (≥2 alternatives with different trade-offs) — a separate ADR file in `task_dir/adr/`.

**Separation from spec ADRs (MUST):** the file-based `task_dir/adr/*.md` (MADR) records **only technical design decisions** (Phase 2+): architecture, modular structure, contracts. Business decisions at the requirements level (choice of alternative, scope decisions, user answers in clarification) remain in the inline "Decision Log (ADR)" of the specification (`spec-standard`). Each file ADR that follows from a spec decision MUST REFER to its number. Duplicating the same decision in both places is PROHIBITED.

**ADR Format** (MADR 4.0 lean):

```markdown
# ADR-NNN: [Название решения]

Status: Accepted
Date: YYYY-MM-DD

## Context
[Почему возник вопрос]

## Decision Drivers
- [Фактор 1]
- [Фактор 2]

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

What will become worse, more complex, more expensive. If drawbacks is empty — the design has not been analyzed enough.

#### 7.2 Risks

```markdown
| # | Риск | Вероятность | Влияние | Mitigation |
|---|------|-------------|---------|------------|
| 1 | Производительность запроса при >100K записей | Средняя | High | Индекс + лимит выборки |
```

---

### § 8. Assumptions and Open Questions

**Assumptions** — accepted under uncertainty. They do not block the design, but may affect implementation:

```markdown
- Предполагаем, что максимальное кол-во контрагентов < 500K
- БСП версии 3.1+ (иначе нужен fallback для ДлительныеОперации)
```

**Open questions** — left unanswered. They do not block the architecture, but require clarification before or during implementation.

---

### § 9. Migration and Rollback (Conditional)

**Condition:** section MUST if existing metadata objects are changed or data migration is required. Otherwise — `N/A: new objects, no migration required`.

#### 9.1 Migration Plan
- Update order (configuration → data → permissions)
- Data population / conversion processes
- Phasing (if phased rollout)

#### 9.2 Rollback Strategy
- Whether the changes can be rolled back
- What will happen to the data on rollback
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

**Rule:** each MUST from the specification MUST be covered by at least one design section and one task. SHOULD — SHOULD be covered.

---

## 6. Quality Criteria for technical-design.md

Reviewer checklist (scope=arch):

### Structure and Completeness
- [ ] All MUST sections are filled in (or N/A with a reason)
- [ ] The header contains links to spec, explorer-context, task-breakdown
- [ ] Status is correct (Draft when created)

### Overview (§1)
- [ ] Goals describe technical goals, not a restatement of the specification requirements
- [ ] Non-goals contain at least 1 deliberate exclusion
- [ ] Background is based on explorer-context.md, not a duplicate of it
- [ ] Constraints take into account: development mode (extension/configuration), platform/БСП version

### Solution Strategy (§2)
- [ ] The strategy answers each Goal from §1.1
- [ ] The description is at the approach level, not the code level

### Structural Blocks (§3)
- [ ] The module map covers all modules in the specification scope
- [ ] Interfaces and contracts contain signatures with parameters, return value, compilation directives
- [ ] There are no implicit dependencies between modules

### Data and Metadata (§4)
- [ ] All metadata objects are listed with types and changes
- [ ] Complex objects (forms, SKD, roles) have a link to the JSON DSL file
- [ ] The data flow covers the key scenarios from the test plan

### Cross-Cutting Concepts (§5)
- [ ] Solutions for error handling, transactions, permissions, client/server boundary
- [ ] The use or rejection of БСП mechanisms (ssl-patterns) is justified
- [ ] Platform limitations with workarounds (if any)

### Key Decisions (§6)
- [ ] For each non-obvious decision (≥2 alternatives), there is justification
- [ ] ADR files contain consequences and confirmation
- [ ] There are no decisions that conflict with the specification

### Risks and Drawbacks (§7)
- [ ] Drawbacks are not empty - every decision has a cost
- [ ] High risks have a mitigation plan
- [ ] Trade-offs are described honestly (pros + cons)

### Traceability (§10)
- [ ] Every MUST from the specification is covered by a design section and a task
- [ ] No requirements are left without linkage to design
- [ ] task IDs match task-breakdown.json

### Task decomposition (JSON)
- [ ] All tasks have unique `task_id`
- [ ] `depends_on` entries are valid and contain no cycles
- [ ] `spec_refs` point to existing specification sections
- [ ] `task_type` is correct (code/test/migration/docs/analysis/architecture)
- [ ] `done_criteria` are testable and specific
- [ ] JSON is stored in a separate file, with a link in the design

### Framework consistency
- [ ] The document is written in Russian (except for code identifiers and established terms)
- [ ] Compatibility with the existing configuration (coding-standards)
- [ ] The design is implementable within the specification scope
- [ ] The design does not conflict with decisions from the specification Decision Log

---

## 7. Typical mistakes

| Mistake | Consequence |
|--------|------------|
| Non-goals are empty | Scope creep |
| Drawbacks are empty | Reviewer cannot assess trade-offs |
| JSON DSL is fully inline | The document becomes bloated, overview is lost → DSL in artifacts/ |
| Specification duplication | Violation of single source of truth |
| Traceability is absent | It is impossible to verify requirement coverage |
| All sections are filled in for a simple task | Formal overhead → use N/A |
| Constraints are not specified | Incompatible approach (EDT vs Designer, БСП version) |

---

## 8. Related skills

Inputs: `spec-standard`. Outputs: `task-breakdown-*`. Criteria: `coding-standards`, `ssl-patterns`. Metadata generation: `xml-generation`.

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/skills/bsl-practices/ssl-patterns/SKILL.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
---
