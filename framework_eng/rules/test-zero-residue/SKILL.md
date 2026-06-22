---
name: test-zero-residue
description: Any test that writes to the DB leaves no run residue - all created objects are physically cleaned up, delta across catalogs/registers/documents before and after the run = 0. Apply the `test-writing` skill.
alwaysApply: true
---

# Tests Leave No Traces (zero-residue)

> **Trigger:** design, writing, or review of ANY test (YaxUnit unit, server-side YaxUnit helper "Vanessa interception", Vanessa `.feature`, integration, end-to-end) that creates/modifies/writes objects in the DB. Applies to ALL test types. When triggered, apply the `test-writing` skill (`framework/skills/bsl-practices/test-writing/SKILL.md`) and the corresponding isolation mechanism: `yaxunit-isolation` for YaxUnit, `vanessa-test-isolation-policy` for Vanessa.

**GUARD:** a test that leaves data behind in the production database after the run is NOT accepted (Reviewer BLOCK) - even if it is "green".

## Principle

A test that accumulates leftovers pollutes real data, breaks other tests (scheduled jobs pick test objects -> timeouts; idempotency sees other runs as duplicates) and masks database degradation. Post-fact cleanup is a symptom; the root cause is the absence of teardown in the test ARCHITECTURE. This is built into the architecture of every test, not done as a one-off cleanup.

## MUST (invariant, always)

| Requirement | Description |
|-----------|----------|
| Zero residue | Delta in count for EACH affected catalog / register / document / record set before and after the run = **0** |
| Isolation mechanism is mandatory | YaxUnit - `.ВТранзакции()` (rollback) OR explicit teardown `.После()` when a permitted exception applies (see `yaxunit-isolation`) |
| Objects are trackable | Create catalogs via `ЮТест.Данные()` (auto-tracking + auto-deletion via `.УдалениеТестовыхДанных()`), not via `Справочники.X.СоздатьЭлемент()` outside tracking |
| Physical deletion of untracked objects | What is not cleaned by transaction/auto-tracking (documents via `Документы.X.СоздатьДокумент()`, objects from helpers, objects from `КонструкторОбъекта().Записать()`) must be deleted physically: `ОбменДанными.Загрузка = Истина` + `Удалить()`, in dependent -> owner order; not marked for deletion |
| **Collector is mandatory** | A test that generates data MUST register EVERY created object in the test object collector AT THE MOMENT of creation; teardown (`.После()` / final scenario step / `ПослеВсехТестов`) walks the collector and physically deletes everything that survived transaction rollback. Works EVEN if Act/Assert failed. Mechanism detail - the `test-writing` skill ("Test Object Collector") |
| **Silent swallowing of delete errors is forbidden** | Collector drain/teardown is NOT allowed to silently swallow a `Удалить()` error. Deletion can legitimately fail on a version conflict (optimistic-lock: the object was concurrently rewritten by a subscription/background job between read and delete) -> it MUST retry with a fresh `ПолучитьОбъект()` (limited number of attempts), retrying ONLY on a version conflict. If after retries the object is still not deleted OR the error is non-conflict, write an Error-level record to the registration log (NOT Warning) with the type + reference of the residue that could not be deleted and continue draining the remaining references (do not abort the drain with an exception - otherwise the rest will be orphaned). A deletion error swallowed in `Попытке` without a visible Error-level trace is an invisible leak that delta-0 acceptance in the registration log will not catch |
| End-to-end test - cleanup step | If data must live during the run (end-to-end) - this is the only exception, and the test MUST end with an explicit step that deletes everything created (via the collector) |
| Acceptance = delta-0 | Verification of the delta of key objects before/after is part of the phase acceptance criterion; non-zero delta = test not accepted |
| Band-aid forbidden | Hiding created objects from queries/scheduled jobs (object exclusion flag, special prefix filter) does NOT count as cleanup - the object must be physically deleted |
| Fragile selectors forbidden | Cleanup by scanning the database by NAME/prefix/regex ("delete everything `LIKE \"Test%\"`", mixed alphabets) is NOT the primary teardown mechanism: a false-wide selection risks deleting production data, a false-narrow one leaves residue. It is allowed ONLY as a one-off sweep of already accumulated historical garbage, not as the standard test teardown. Standard teardown - the collector (exact references) |

## Relation to mechanisms

- `yaxunit-isolation` - HOW to isolate a server-side YaxUnit test (`.ВТранзакции()`, allowed exceptions, teardown `.После()`). This rule defines the INVARIANT (delta-0), that rule defines the mechanism.
- `vanessa-test-isolation-policy` - isolation of Vanessa scenarios (creation of their own objects). Reinforced by the requirement for physical cleanup down to zero.

---
depends_on:
  - framework/skills/bsl-practices/test-writing/SKILL.md
  - framework/rules/yaxunit-isolation/SKILL.md
  - framework/rules/vanessa-test-isolation-policy/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
---
