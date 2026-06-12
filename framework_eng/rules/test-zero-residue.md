---
name: test-zero-residue
description: Any test that writes to the DB leaves no run residue - all created objects are physically cleaned up, delta by catalogs/registers/documents before and after the run = 0. Apply the `test-writing` skill.
alwaysApply: true
---

# Tests Leave No Traces (zero-residue)

> **Trigger:** design, writing, or review of ANY test (YaxUnit unit, server-side YaxUnit helper "Vanessa interception", Vanessa `.feature`, integration, end-to-end) that creates/modifies/writes objects in the DB. Applies to ALL test types. When triggered, apply the `test-writing` skill (`framework/skills/bsl-practices/test-writing/SKILL.md`) and the corresponding isolation mechanism: `yaxunit-isolation` for YaxUnit, `vanessa-test-isolation-policy` for Vanessa.

**GUARD:** a test that leaves data behind in the production database after the run is NOT accepted (Reviewer BLOCK) - even if it is "green".

## Principle

A test that accumulates leftovers pollutes real data, breaks other tests (scheduled jobs pick test objects -> timeouts; idempotency treats other runs as duplicates) and masks database degradation. Post-fact cleanup is a symptom; the root cause is the absence of teardown in the test ARCHITECTURE. This is built into the architecture of every test, not done as a one-off cleanup.

## MUST (invariant, always)

| Requirement | Description |
|-----------|----------|
| Zero residue | Delta in count for EACH affected catalog / register / document / record set before and after the run = **0** |
| Isolation mechanism is mandatory | YaxUnit - `.ВТранзакции()` (rollback) OR explicit teardown `.После()` when an exception is allowed (see `yaxunit-isolation`) |
| Objects are trackable | Create Справочники via `ЮТест.Данные()` (auto-tracking + auto-deletion via `.УдалениеТестовыхДанных()`), not via `Справочники.X.СоздатьЭлемент()` outside tracking |
| Physical deletion of untracked objects | What is not cleaned by transaction/auto-tracking (documents via `Документы.X.СоздатьДокумент()`, objects from helpers) must be deleted physically: `ОбменДанными.Загрузка = Истина` + `Удалить()`, in dependent -> owner order; not marked for deletion |
| Resilience to failure | Created references are registered at creation time (accumulator), teardown runs in `.После()` / final scenario step EVEN if Act/Assert failed |
| End-to-end test - cleanup step | If data must live during the run (end-to-end) - this is the only exception, and the test MUST end with an explicit step that deletes everything created |
| Acceptance = delta-0 | Verification of the delta of key objects before/after is part of the acceptance criterion for the phase; non-zero delta = test not accepted |
| Band-aid forbidden | Hiding created objects from queries/scheduled jobs (object exclusion flag, special prefix filter) does NOT count as cleanup - the object must be physically deleted |

## Relation to mechanisms

- `yaxunit-isolation` - HOW to isolate a server-side YaxUnit test (`.ВТранзакции()`, allowed exceptions, teardown `.После()`). This rule defines the INVARIANT (delta-0), that rule defines the mechanism.
- `vanessa-test-isolation-policy` - isolation of Vanessa scenarios (creation of their own objects). Reinforced by the requirement for physical cleanup down to zero.

---
depends_on:
  - framework/skills/bsl-practices/test-writing/SKILL.md
  - framework/rules/yaxunit-isolation.md
  - framework/rules/vanessa-test-isolation-policy.md
  - framework/rules/tdd-policy.md
---
