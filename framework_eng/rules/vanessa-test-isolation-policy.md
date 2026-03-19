---
name: vanessa-test-isolation-policy
description: Test data isolation policy for Vanessa Automation scenario tests. Defines when a test must create its own data and when it may reuse existing ones.
---

# Test Data Isolation Policy

## Principle

The degree of isolation is determined by two criteria:
1. Does the test result in writing data to the DB?
2. Reproducibility — the test must run repeatedly without manual intervention

## MUST

### Full isolation — if the test writes data to the DB

If the scenario creates, modifies, or writes objects (документы, справочники, регистры):

- The test creates its own document/object at the beginning of the scenario
- Identifiers (document number) are passed between steps through Vanessa variables (`$VarName$`)
- When switching TestClient between users — the document is written and closed before the switch
- Do not post test documents unless necessary — the movements affect other tests

### Reference data when creating isolated objects

It is allowed to use existing reference objects, but with separation:

- **Structural** (organizations, warehouses, currencies, units of measurement) — safe, unchanged between runs
- **Business-critical** (nomenclature with discount categories, counterparties with contracts, user limit settings) — allowed, but the dependency must be recorded in the scenario comment with a reference to the specific object

## SHOULD

### Relaxed isolation — if the test does not write data

If the scenario only reads data or closes the form without saving:

- It is allowed to work with existing objects
- The data must be stable between runs (not created or deleted by other tests)
- Use the `@manual-data` tag — if the test depends on pre-prepared data that is not available in the baseline database

### Transferring data between TestClient

```gherkin
И я активизирую TestClient "Manager"
# ... создать документ ...
И Я запоминаю значение выражения "..." в переменную "DocNumber"
# ... записать, закрыть ...

И я активизирую TestClient "Director"
# ... найти документ по $DocNumber$ ...
```

Vanessa variables are global for the runner session — they are available in all TestClient instances.

## Selection criterion

| Тест записывает в СУБД? | Изоляция | Данные |
|---|---|---|
| Да | Полная | Тест создаёт свои объекты |
| Нет | Ослабленная | Существующие стабильные объекты |

The main question: **can the test be rerun** without intervention? If not — increase the isolation.

## Anti-patterns

| Антипаттерн | Последствие |
|---|---|
| The test writes a document but uses someone else’s existing one | The next run will encounter altered data |
| The test posts a document unnecessarily | The movements affect balances and break other tests |
| The test depends on the number/date of an existing document without `@manual-data` | It fails after the database is rebuilt |
| An immutable value is passed between TestClient instances without writing | The second user does not see the changes |

---
depends_on:
  - framework/rules/vanessa-scenario-policy.mdc
---
