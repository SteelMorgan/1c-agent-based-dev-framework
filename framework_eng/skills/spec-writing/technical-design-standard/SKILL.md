---
name: technical-design-standard
description: "The technical design standard for 1С development tasks. Defines the structure of technical-design.md, the rules for filling sections (MUST/SHOULD/MAY), the quality checklist, and guidance on the level of detail. Used by the architect (Phase 2) and the reviewer (scope=arch)."
---

# Technical Design Standard (Technical Design)

Technical design (`technical-design.md`) is the bridge between the specification (WHAT) and the decomposition of tasks (HOW). It records architectural decisions, the modular structure, contracts, and cross-cutting concepts. It extends the high-level Technical Design section from the specification.

Foundation: Google Design Docs, arc42, MADR 4.0, Stripe RFC (Drawbacks), C4 Model.

---

## 2. Document language

Technical design MUST be written in **Russian** — section headings, descriptions, justifications, tables. Exceptions: code and metadata identifiers (module names, attributes, variables, BSL signatures) and well-established terms (ADR, RFC 2119, C4, MUST/SHOULD/MAY).

---

## 3. When a technical design is required

| Task type | TD required | Justification |
|------------|----------|-------------|
| New functionality (medium/complex) | MUST | Locks in the architecture before development begins |
| Modification of a standard configuration with structural changes | MUST | Need to justify the chosen approach (extension vs configuration) |
| Integration with an external system | MUST | Contracts and data flow are critical |
| Simple bug fix | MAY | Only if the bug requires architectural changes |
| Refactoring that changes the modular structure | SHOULD | Transparency about the boundaries of change is required |
| External processing (EPF) with a form | SHOULD | Metadata structure and UI require design. MUST if the EPF includes background operations, access rights, or data exchange |

---

## 4. Required structure of technical-design.md

### Title and metadata

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

### Sections and obligation rules

| § | Section | Requirement | Condition |
|---|--------|---------------|---------|
| 1 | Overview | **MUST** | Always |
| 2 | Solution strategy | **MUST** | Always |
| 3 | Structural blocks | **MUST** | Always |
| 4 | Data and metadata | **MUST** | Always |
| 5 | Cross-cutting concepts | **SHOULD** | MUST if the task touches >2 modules or changes transversal behavior |
| 6 | Key decisions | **MUST** | Always (minimum 1 decision) |
| 7 | Risks and drawbacks | **MUST** | Always |
| 8 | Assumptions and open questions | **SHOULD** | MUST if there are uncertainties that block part of the design |
| 9 | Migration and rollback | **Conditional MUST** | MUST if existing metadata objects are altered or data migration is required |
| 10 | Traceability | **MUST** | Always |

**Rule:** If a section is not applicable to the task — mark it as `N/A` with a brief reason. Do not remove the section.

---

## 5. Section descriptions

### § 1. Overview

#### 1.1 Goals

What the technical solution must achieve. RFC 2119 formulations (MUST/SHOULD/MAY) are not required — they already exist in the specification. This section describes the technical goals of the design.

#### 1.2 Non-goals

**What the design explicitly does NOT solve.** This is the most valuable section for preventing scope creep. Each non-goal is a conscious exclusion.

#### 1.3 Background

The current state of the system (TOGAF Baseline). Which modules/objects exist and how they currently operate. Reference `explorer-context.md` as the baseline source — do not duplicate it, only expand where the design requires additional detail.

#### 1.4 Constraints

Constraints that influence the architecture:
- Development mode: extension vs modifying the main configuration
- Platform 1С version and minimum БСП version
- xml-gen limitations (Designer format, not EDT; SKD 85%)
- Organizational constraints (timelines, server access, licenses)

---

### § 2. Solution strategy

High-level description of the chosen approach (2–3 paragraphs):
- Which key technological/architectural decisions were made
- Which patterns were chosen and why
- How the approach addresses the Goals from §1.1

This is the **strategy**, not the details. Details belong in §3 and §4.

---

### § 3. Structural blocks

#### 3.1 System context (C4 Level 1)

The system in the context of external systems and users. For integration tasks — a mandatory diagram (textual or ASCII).

For tasks inside a single configuration — MAY be a brief description of the affected subsystems.

#### 3.2 Module map (C4 Level 2–3)

Affected and new modules, their relationships:

```markdown
| Модуль | Тип | Новый/Существующий | Ответственность |
|--------|-----|--------------------|-----------------|
| ОМ.РаботаСКонтрагентами | Общий модуль | Существующий (модификация) | Валидация, получение данных |
| МодульОбъекта.Контрагенты | Модуль объекта | Существующий (модификация) | Обработчики записи |
```

For complex tasks — textual diagrams of call flows between modules.

#### 3.3 Interfaces and contracts

Signatures of the key procedures/functions with contracts:

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

### § 4. Data and metadata

#### 4.1 Metadata objects

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
- Complex objects (forms, SKD, roles): link to the DSL file in `task_dir/artifacts/` **MUST**; inline fragments in the design **MAY** be included (only key elements required for understanding the architecture)
- Simple objects (catalogs, documents, registers): a textual description of the structure **MUST**

#### 4.2 Data flow

How data moves through the system for key scenarios:

```
Пользователь → Форма.Контрагент
  → МодульОбъекта.ПриЗаписи()
    → ОМ.РаботаСКонтрагентами.ПроверитьИНН()
    → РС.ИсторияИзменений.Запись
```

For integrations — the data flow between systems with protocols and formats. For each integration point SHOULD specify the NFR contract: timeout, retry policy, idempotency, authentication, and error mapping.

---

### § 5. Cross-cutting concepts

Cross-cutting solutions that span all modules. **SHOULD** document the decision for each applicable aspect:

| Aspect | Decision | Justification |
|--------|---------|-------------|
| **Error handling** | Попытка/Исключение с ЗаписьЖурналаРегистрации | coding-standards rule 18 |
| **Logging** | ЖР through БСП (ЗаписьЖурналаРегистрации) | ssl-patterns: standard mechanism |
| **Access rights** | Role via xml-gen, RLS is not required | Data does not contain organization-level segregation |
| **Transactions** | НачатьТранзакцию/Попытка for writing to registers | coding-standards rule 18 |
| **Client/Server** | &НаСервереБезКонтекста for business logic | coding-standards rule 3 |
| **Use of БСП** | ОбщегоНазначения.СообщитьПользователю for validation | ssl-patterns: validation of required fields |
| **Platform limitations** | [describe workarounds if there are any] | — |

If all aspects are standard and do not require special solutions — state: “Standard patterns are used; see coding-standards and ssl-patterns. There are no special solutions.”

---

### § 6. Key decisions

Brief table of architectural decisions:

```markdown
| # | Решение | Варианты | Выбор | Обоснование | ADR |
|---|---------|---------|-------|-------------|-----|
| 1 | Хранение истории | A) ЖР, B) Отдельный регистр | B | Нужны запросы и отчёты по истории | [ADR-001](adr/ADR-001.md) |
| 2 | Валидация ИНН | A) Свой алгоритм, B) Внешний сервис | A | Нет зависимости от сети | — (тривиальное) |
```

**Rule:** for every non-obvious decision (≥2 alternatives with different trade-offs) — a separate ADR file in `task_dir/adr/`.

**ADR format** (MADR 4.0 lean):

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
Chosen option [X].

### Consequences
- Good: [что улучшится]
- Bad: [что ухудшится]

### Confirmation
[Как проверить, что решение реализовано корректно]
```

---

### § 7. Risks and drawbacks

#### 7.1 Drawbacks

What becomes worse, more complex, or more expensive. If the drawbacks section is empty — the design has not been analyzed enough.

#### 7.2 Risks

```markdown
| # | Риск | Вероятность | Влияние | Mitigation |
|---|------|-------------|---------|------------|
| 1 | Производительность запроса при >100K записей | Средняя | High | Индекс + лимит выборки |
```

---

### § 8. Assumptions and open questions

**Assumptions** — assumptions accepted in the face of uncertainty. They do not block the design but may influence implementation:

```markdown
- Предполагаем, что максимальное кол-во контрагентов < 500K
- БСП версии 3.1+ (иначе нужен fallback для ДлительныеОперации)
```

**Open questions** — questions remaining unanswered. They do not block the architecture but require clarification before or during implementation.

---

### § 9. Migration and rollback (conditional)

**Condition:** the section is a MUST if existing metadata objects are changed or data migration is required. Otherwise — `N/A: new objects, migration is not required`.

#### 9.1 Migration plan
- Update order (configuration → data → rights)
- Data filling/conversion routines
- Staged rollout (if implementation is phased)

#### 9.2 Rollback strategy
- Whether the changes can be rolled back
- What happens to the data upon rollback
- Point of no return (if any)

---

### § 10. Traceability

Traceability matrix: requirement from the specification → design section → task from the decomposition.

```markdown
| Spec Requirement | Design Section | Task IDs |
|------------------|---------------|----------|
| MUST-1: Валидация ИНН | §3.3 Interfaces, §4.1 Metadata | T-001, T-003 |
| MUST-2: История изменений | §4.1 Metadata, §4.2 Data Flow | T-002 |
| SHOULD-1: Отчёт по истории | §4.1 Metadata (SKD) | T-005 |
```

**Rule:** every MUST from the specification MUST be covered by at least one design section and one task. SHOULD — SHOULD be covered.

---

## 6. Quality criteria for technical-design.md

Reviewer checklist (scope=arch):

### Structure and completeness
- [ ] All MUST sections are filled (or N/A with a reason)
- [ ] The header contains links to spec, explorer-context, and task-breakdown
- [ ] Status is correct (Draft when created)

### Overview (§1)
- [ ] Goals describe technical objectives without repeating the specification requirements
- [ ] Non-goals include at least one deliberate exclusion
- [ ] Background builds on explorer-context.md without duplication
- [ ] Constraints consider development mode (extension/configuration), the platform, and БСП version

### Solution strategy (§2)
- [ ] The strategy addresses each Goal from §1.1
- [ ] The description is at the approach level, not the code level

### Structural blocks (§3)
- [ ] The module map covers all modules in the specification scope
- [ ] Interfaces and contracts include signatures with parameters, returns, and compilation directives
- [ ] No implicit dependencies exist between modules

### Data and metadata (§4)
- [ ] All metadata objects are listed with their types and changes
- [ ] Complex objects (forms, SKD, roles) link to JSON DSL files
- [ ] The data flow covers key scenarios from the test plan

### Cross-cutting concepts (§5)
- [ ] Decisions on error handling, transactions, rights, and client/server boundary are documented
- [ ] The use or rejection of БСП mechanisms (ssl-patterns) is justified
- [ ] Platform limitations with workarounds (if any) are described

### Key decisions (§6)
- [ ] Each non-obvious decision (≥2 alternatives) has justification
- [ ] ADR files include consequences and confirmation
- [ ] No decisions contradict the specification

### Risks and drawbacks (§7)
- [ ] Drawbacks are not empty — every decision has a cost
- [ ] High risks have mitigation plans
- [ ] Trade-offs are described honestly (Good + Bad)

### Traceability (§10)
- [ ] Every MUST from the specification is traced to a design section and a task
- [ ] No requirements are left without a link to the design
- [ ] Task IDs match those in task-breakdown.json

### Task decomposition (JSON)
- [ ] All tasks have unique `task_id`
- [ ] `depends_on` entries are valid and cycle-free
- [ ] `spec_refs` refer to existing sections of the specification
- [ ] `task_type` is correct (code/test/migration/docs/analysis/architecture)
- [ ] `done_criteria` are verifiable and specific
- [ ] The JSON resides in a separate file, and the design contains the link

### Consistency with the framework
- [ ] The document is written in Russian (except for code identifiers and established terms)
- [ ] Compatibility with the existing configuration (coding-standards)
- [ ] The design is implementable within the specification scope
- [ ] The design does not contradict the decisions from the specification’s Decision Log

---

## 7. Common mistakes

| Mistake | Consequence |
|--------|------------|
| Empty non-goals | Scope creep |
| Empty drawbacks | The reviewer cannot assess trade-offs |
| Fully inline JSON DSL | The document bulks up and loses overview → keep DSL in artifacts/ |
| Duplicating the specification | Breaks the single source of truth |
| Missing traceability | Impossible to verify requirement coverage |
| Filling all sections for a simple task | Formal overhead → use N/A |
| Not specifying constraints | Incompatible approach (EDT vs Designer, БСП version) |

---

## 8. Related skills

Inputs: `spec-standard`. Outputs: `task-breakdown-*`. Criteria: `coding-standards`, `ssl-patterns`. Metadata generation: `xml-generation`.

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/skills/bsl-practices/ssl-patterns/SKILL.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
---
