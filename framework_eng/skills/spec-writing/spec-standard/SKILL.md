---
name: spec-standard
description: "For SDD specifications with RFC 2119 and a checklist"
---

# Specification Writing Skill (SDD)

The skill **does not choose the execution mode** (subagent/linear) - only the structure, RFC 2119, and quality checklist.

---

## 2. When a specification is needed

| Task type | Spec needed | Rationale |
|------------|-------------|-------------|
| New functionality | MUST | Captures scope, requirements, alternatives, and the chosen solution. |
| Bug fix with architectural impact | MUST | Requires justification for changes to structure/behavior. |
| Simple local bug fix | MAY | A short description without a full spec is acceptable if the change is isolated. |
| Large refactoring | SHOULD | Transparency about boundaries and consequences of changes is needed. |

---

## 3. Specification language

The specification MUST be written in **Russian** - section headings, descriptions, requirements, scenarios. Exception - code and metadata identifiers (module names, attributes, variables) remain as-is.

## 4. Mandatory specification structure

```markdown
# SPEC-NNN: [Short title]
Status: Draft | Review | Approved | Implemented
Date: YYYY-MM-DD

## Context and problem statement

## Requirements (RFC 2119)
### MUST
### SHOULD
### MAY
### MUST NOT

## Boundaries
### In scope
### Out of scope

## Considered options

## Chosen solution

## Technical design
### Metadata objects (created by the user)
### Modules (written by the agent)
### Data flow

## Test plan (TDD)

### Test Users

If tests (unit / BDD / integration) depend on user roles, permissions, or context, the spec MUST include a "Test Users" section (or equivalent) with the following rules:

- List **only users that actually exist** in the target base (login + role set + source reference: preloaded profile, fixture, linked task final report, etc.).
- **Placeholder names are forbidden** ("User1", "TestUser", "Manager_NoRole"), as are invented full names without confirmed correspondence to a real account in the base ("Sidorov", "Ivanov" - if such a user does not exist in the base).
- For each test user, specify at minimum: login, role set, source, and test scenario usage.
- If a suitable user is **unknown** or **does not exist** - the Analyst asks the user `clarification_needed` in the clarification round instead of inventing a name. It is acceptable to suggest candidates for creation to the user (with roles specified), but the name must be confirmed.
- If a test user must be **created by an administrator** before execution (manual data prep) - this is explicitly recorded in a separate item in `manual-test-scenario.md` or an equivalent artifact, with a description of the creation steps.

**Why:** placeholder names in a spec lead to Vanessa scenarios like "Could not connect TestClient <Sidorov>" and break the entire Vanessa level. Tester / Scenario-Coder cannot "guess" a real user and lose hours on diagnostics.

## Acceptance scenarios (BDD)

## Open questions

## Decision log (ADR)
```

---

## 4a. No Duplication of Test Levels (MUST)

Each test in the "Test Plan" MUST add coverage that does not yet exist. It is forbidden to
plan a test (especially BDD/Vanessa or integration) that checks the same logic with the same
inputs and the same observable result as an already covered unit test - that is, it is 1-to-1.

**1-to-1 duplicate criterion (DO NOT plan):** the second test follows the same code path, with the same
Arrange and the same asserts as the first, and does not involve any new layer (UI/client,
posting into a real database, integration boundary, permissions/roles, multi-session behavior, concurrency).
BDD on top of full unit coverage of the same server-side calculation is a typical duplicate.

**When the second test is justified (plan it):** it EXTENDS coverage - it adds a layer or dimension
that is unavailable to the first:
- client/UI form behavior (visibility, availability, notifications, operator input);
- end-to-end posting through a real database write (while the unit mocked the engine);
- integration boundary (external API, exchange, HTTP service);
- permissions/roles/user context (if the mode is NOT unconditionally privileged - see
  [[test-writing]] about the privileged mode test anti-pattern);
- concurrency, restart idempotency, multi-session behavior.

**Why:** a 1-to-1 duplicate wastes resources and time (writing + execution + maintenance + diagnosis
of false failures) without adding a single line of new coverage. A "green" duplicate creates the illusion of greater
confidence, which is not there. The cost of the BDD level (Phase 3a/3c: executable steps, run profile,
iterations to GREEN, zero-residue teardown) is especially high - it must be justified by a new layer, not by
repeating server logic.

**Analyst action:** for each BDD/integration scenario in the spec, explicitly state WHICH layer it
covers beyond the unit plan (one line: "extends: <layer>"). If there is no extension and the scenario is
1-to-1 with the unit test - DO NOT include it in the plan; record in the ADR the decision "BDD not needed: covered by unit,
avoid duplication". Reviewer checks this as part of test plan acceptance.

---

## 4b. Coverage by the affected runtime layer (MUST)

The test plan MUST choose the test level according to the runtime layer that changes. You cannot
cover a client-side change only with syntax/unit tests, or a server-side change only with a UI click.

| What is affected | Required coverage |
|---------------|------------------------|
| Server logic, common module, manager/object module, form server method, query, register/document write operations | YaxUnit unit/integration. If the test already exists, update it and rerun it; if there is no test, add one. |
| UI or client context: form, command, button, command interface, client handler, `ОткрытьФорму`, notification, visibility/accessibility, rights to open the UI | Scenario test through Vanessa/TestClient: open the user entrypoint, perform the action, and verify the observable result without an error. For UI/UX form acceptance, plan a PNG screenshot through VA MCP (`connect_test_client -> get_window_list_os -> get_window_screenshot_os`) with a check that the screenshot is not empty/black. Plan the web client only for the browser-specific layer (DOM/CSS/JS console/network/web-auth/viewport/browser extension), explicitly stating which function is fundamentally unavailable in VA MCP. For a targeted command, the minimal scenario clicks the command and confirms successful start/completion. |
| Related user process that goes through several forms/objects | End-to-end process scenario. First reuse the existing scenario and update it for the change; write a new scenario only if there is no existing coverage. |
| Integration boundary, HTTP/API, background or scheduled execution | Integration/YaxUnit or scenario test with a verifiable external/register effect; for background jobs, check idempotence and rerun behavior if this applies to the change. |

Every MUST in the spec must have an explicit traceability line in the "Test Plan":
`requirement → affected layer → test type → existing test is updated or a new one is created`.

If a required UI/VA test is technically impossible in the current environment, the spec MUST NOT silently reduce
coverage: apply the `va-visual-check` fallback rules and record the performed VA steps, the reason for the fallback, and the residual risk. If the fallback does not provide enough signal for the requirement, record a blocker. The reviewer checks not only the presence of tests, but also the match between the test level and the affected runtime layer.

---

## 4c. ADR Boundary (MUST)

The process contains two ADR mechanisms, and their scopes do NOT overlap:

- Inline "Decision Log (ADR)" in this specification — **business decisions at the requirements level only**: choosing an alternative from "Considered Options", scope decisions (what is / is not included in scope), user answers in the clarification round.
- File-based `task_dir/adr/*.md` (MADR, maintained in technical-design, see `technical-design-standard`) — **technical design decisions** (Phase 2+): architecture, module structure, contracts.

Each file-based ADR that arises from a spec decision MUST REFER to its number in the "Decision Log". Duplicating the same decision in both places is PROHIBITED.

---

## 5. RFC 2119 Rules

| Keyword | Meaning | Usage rule |
|----------------|----------|------------------------|
| MUST | Mandatory | Without this, the requirement is considered unmet. |
| SHOULD | Strongly recommended | Deviation is allowed only with explicit justification. |
| MAY | Optional | An improvement that does not block acceptance. |
| MUST NOT | Prohibited | An explicit restriction; violation is not allowed. |

Requirements must be:
- atomic (one requirement — one verifiable thought);
- verifiable (can be confirmed by a test/scenario);
- non-contradictory across sections.

---

## 6. Task Decomposition

For spec tasks, decomposition is **mandatory** (a separate Task Breakdown JSON file). In the specification — a link to the JSON and/or a short summary.

The quality control process is outside this skill: `task-breakdown` (§3 Linear — self-check, §4 Subagent — cross-review).

---

## 7. Specification Quality Criteria

Review checklist:

- [ ] "Context" describes who has the problem and what is not working.
- [ ] Each MUST is covered by an item in "Test Plan".
- [ ] For each MUST, the affected runtime layer is specified and the appropriate test type is selected:
  server → YaxUnit, UI/client → scenario UI/BDD, process → end-to-end, integration/background → integration/job.
- [ ] "Boundaries" clearly separate "In scope" and "Out of scope".
- [ ] "Considered Options" contains at least 2 alternatives.
- [ ] "Chosen Solution" includes justification and consequences.
- [ ] "Technical Design" separates user tasks (metadata) and agent tasks (code).
- [ ] There are no contradictions between sections.
- [ ] Requirements are formulated using RFC 2119 (MUST/SHOULD/MAY/MUST NOT).
- [ ] There is a link/summary for a separate Task Breakdown JSON.
- [ ] "Acceptance Scenarios" contain business-level Gherkin scenarios (Given/When/Then) for MUST requirements.
- [ ] If the change affects UI/client context, there is a scenario that opens the user entrypoint and performs the changed action.
- [ ] If the change affects a server method/logic, there is YaxUnit coverage: an existing test has been updated or a new one created.
- [ ] The document is written in Russian (except code identifiers).

---

## 8. Common Mistakes

| Error | Consequence |
|--------|------------|
| Mixing the problem and solution in Context | It is unclear what needs to be fixed |
| Vague requirements without RFC 2119 | It is impossible to unambiguously accept the work |
| Empty Out of scope | Scope creep |
| Lack of task decomposition | Weak traceability |
| Contradictions Requirements ↔ Technical Design | Implementation errors |

---
depends_on: []
---
