---
name: yaxunit-isolation
description: You are writing a server-side YaxUnit test that writes to the DB -> mandatory transactional isolation via .ВТранзакции(). Apply the test-writing skill.
alwaysApply: false
---

# YaxUnit test isolation (transaction rollback)

> **Trigger:** a server test (`ДобавитьСерверныйТест`) writes to the DB - creates, posts, modifies, or deletes objects. When this happens, apply the `test-writing` skill (`framework/skills/bsl-practices/test-writing/SKILL.md`), section "Isolating test data".

## Principle

A test that writes to the DB **must roll back its changes** - otherwise every run leaves garbage in the database, tests become non-idempotent, and the database gradually degrades. YaxUnit solves this with the built-in `.ВТранзакции()` mechanism: a transaction opens before each test and rolls back after the test.

## MUST

| Requirement | Description |
|---|---|
| `.ВТранзакции()` by default | Any set of server tests that write to the DB is registered with `.ВТранзакции()` immediately after `ДобавитьТестовыйНабор()` |
| Catalogs - via `ЮТест.Данные()` | Create catalog items only through `ЮТест.Данные().СоздатьЭлемент(...)` or `КонструкторОбъекта(...).Записать()` - then they are tracked and deleted automatically |
| Exception -> explicit justification | A set without `.ВТранзакции()` MUST contain a comment before `ДобавитьТестовыйНабор()` stating the reason for the exception (one of the three below) + teardown via `.После("ИмяПроцедурыОчистки")` |
| Documents without `.ВТранзакции()` -> teardown | Documents created through `Документы.X.СоздатьДокумент()` (not through `ЮТест.Данные()`) are NOT tracked by auto-cleanup - they require explicit teardown in `.После()` |
| Client tests - without `.ВТранзакции()` | `ДобавитьКлиентскийТест` runs in the client context, where transactional rollback is unavailable |

## Permitted exceptions to `.ВТранзакции()`

### (a) Negative posting tests (expected Denial)

The test verifies that document posting is **forbidden** (`Отказ = Истина` is set in the handler). A nested write transaction that ends in an error **poisons** the outer one: subsequent reads return "Errors have already occurred in this transaction!". The solution is not to wrap such sets in `.ВТранзакции()`, to create objects through `ЮТест.Данные()` (they will be removed by `.УдалениеТестовыхДанных()`), and to additionally clean up the document in teardown.

```bsl
// Exception (a): negative posting test - the expected Denial poisons the outer transaction.
// Isolation via ЮТест.Данные() + .УдалениеТестовыхДанных() + teardown in .После().
ЮТТесты
    .ДобавитьТестовыйНабор("Запрет проведения без договора")
        .УдалениеТестовыхДанных()
        .После("ОчиститьДокументыЗапретПроведения")
        .ДобавитьСерверныйТест("ТестЗапретБезДоговора");
```

### (b) The test calls production code with a `ТранзакцияАктивна()` guard

Some production procedures explicitly check for the absence of an active transaction (two-phase commits, real external API calls, writes to information registers with a unique key). Running such code inside `.ВТранзакции()` causes an error or unexpected behavior in the production code itself.

```bsl
// Exception (b): production code contains a ТранзакцияАктивна() guard - it cannot run in a transaction.
// Teardown performs manual cleanup via ЮТест.Данные().УстановитьЗначениеРеквизита().
ЮТТесты
    .ДобавитьТестовыйНабор("Двухфазная фиксация позиции")
        .После("ОчиститьДанныеДвухфазнойФиксации")
        .ДобавитьСерверныйТест("ТестФиксацияПозиции");
```

### (c) Client context

`ДобавитьКлиентскийТест` - transactional rollback on the client is unavailable by platform architecture. Test data under client tests is created and cleaned up through `Перед`/`После` handlers in the server context.

## Object re-read pattern when reposting

A test that changes a document's write mode (posting -> unposting -> reposting) **must re-read the object** between transitions via `ДокОбъект = Ссылка.ПолучитьОбъект()` - this models form behavior:

```bsl
// Провести
ДокОбъект = ДокСсылка.ПолучитьОбъект();
ДокОбъект.Записать(РежимЗаписиДокумента.Проведение);

// Отменить проведение — перечитываем, как это делает форма
ДокОбъект = ДокСсылка.ПолучитьОбъект();
ДокОбъект.Записать(РежимЗаписиДокумента.ОтменаПроведения);

// Снова провести — перечитываем ещё раз
ДокОбъект = ДокСсылка.ПолучитьОбъект();
ДокОбъект.Записать(РежимЗаписиДокумента.Проведение);
```

**Important (platform 8.3.27):** programmatic reposting in a server session can trigger the platform error `[ОшибкаХранимыхДанных]` (a stack with no application frames). Neither re-reading nor `.ВТранзакции()` fixes it - this is a platform limitation. In that case, reposting idempotence is verified at the **scenario layer** (Vanessa, the path through the form), and the unit test is written with `ЮТест.Пропустить()` and an explicit justification.

## Example of correct registration with `.ВТранзакции()`

```bsl
Процедура ИсполняемыеСценарии() Экспорт

    ЮТТесты
        // Стандартный пишущий набор — транзакционная изоляция по умолчанию
        .ДобавитьТестовыйНабор("Проведение документа")
            .ВТранзакции()
            .ДобавитьСерверныйТест("ТестПроведениеСДоговором")
            .ДобавитьСерверныйТест("ТестПроведениеСКорректнойСуммой")

        // Исключение (а): негативный тест — ожидаемый Отказ
        // Исключение (а): вложенная транзакция с Отказ отравляет внешнюю.
        .ДобавитьТестовыйНабор("Запрет проведения")
            .УдалениеТестовыхДанных()
            .После("ОчиститьЗапретПроведения")
            .ДобавитьСерверныйТест("ТестЗапретБезДоговора");

КонецПроцедуры
```

---
depends_on:
  - framework/skills/bsl-practices/test-writing/SKILL.md
---
