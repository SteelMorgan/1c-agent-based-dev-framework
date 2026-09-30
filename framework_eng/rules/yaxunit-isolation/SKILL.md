---
name: yaxunit-isolation
description: "For DB-writing YaxUnit tests, use transaction"
alwaysApply: false
---

# YaxUnit Test Isolation (transaction rollback)

> **Trigger:** a server test (`ДобавитьСерверныйТест`) writes to the DB — it creates, posts, modifies, and deletes objects. When this happens, apply the `test-writing` skill (`framework/skills/bsl-practices/test-writing/SKILL.md`), section "Isolating test data".

## Principle

A test that writes to the DB **must roll back its changes** — otherwise each run leaves garbage in the database, tests become non-idempotent, and the database gradually degrades. YaxUnit solves this with the built-in `.ВТранзакции()` mechanism: before each test, a transaction is opened; after the test, it is rolled back.

## MUST

| Requirement | Description |
|---|---|
| `.ВТранзакции()` by default | Any set of server tests that write to the DB is registered with `.ВТранзакции()` immediately after `ДобавитьТестовыйНабор()` |
| Catalogs via `ЮТест.Данные()` | Create catalog items only through `ЮТест.Данные().СоздатьЭлемент(...)` or `КонструкторОбъекта(...).Записать()` — then they are tracked and removed automatically |
| Exception -> explicit rationale | A set without `.ВТранзакции()` MUST contain a comment before `ДобавитьТестовыйНабор()` stating the reason for the exception (one of the three below) + teardown via `.После("ИмяПроцедурыОчистки")` |
| Documents without `.ВТранзакции()` -> teardown | Documents created via `Документы.X.СоздатьДокумент()` (not via `ЮТест.Данные()`) are NOT tracked by auto-cleanup — they require explicit teardown in `.После()` |
| Client tests - without `.ВТранзакции()` | `ДобавитьКлиентскийТест` runs in the client context, where transactional rollback is unavailable |

## Allowed Exceptions from `.ВТранзакции()`

### (a) Negative posting tests (expected Refusal)

The test checks that posting a document is **forbidden** (`Отказ = Истина` is set in the handler). A nested write transaction with an error **poisons** the outer one: subsequent reads return "Errors have already occurred in this transaction!". The solution is not to wrap such sets in `.ВТранзакции()`, to create objects via `ЮТест.Данные()` (they will be removed by `.УдалениеТестовыхДанных()`), and to additionally clean up the document in teardown.

```bsl
// Исключение (а): негативный тест проведения — ожидаемый Отказ отравляет внешнюю транзакцию.
// Изоляция через ЮТест.Данные() + .УдалениеТестовыхДанных() + teardown в .После().
ЮТТесты
    .ДобавитьТестовыйНабор("Запрет проведения без договора")
        .УдалениеТестовыхДанных()
        .После("ОчиститьДокументыЗапретПроведения")
        .ДобавитьСерверныйТест("ТестЗапретБезДоговора");
```

### (b) The test calls production code with a `ТранзакцияАктивна()` guard

Some production procedures explicitly check for the absence of an active transaction (two-phase commits, real external API calls, writes to information registers with a unique key). Running such code inside `.ВТранзакции()` causes an error or unexpected behavior in the production code itself.

```bsl
// Исключение (б): прод-код содержит гвард ТранзакцияАктивна() — нельзя запускать в транзакции.
// Teardown выполняет ручную очистку через ЮТест.Данные().УстановитьЗначениеРеквизита().
ЮТТесты
    .ДобавитьТестовыйНабор("Двухфазная фиксация позиции")
        .После("ОчиститьДанныеДвухфазнойФиксации")
        .ДобавитьСерверныйТест("ТестФиксацияПозиции");
```

### (c) Client context

`ДобавитьКлиентскийТест` — transactional rollback on the client is unavailable by platform architecture. Test data under client tests is created and cleaned through `Перед`/`После` handlers in the server context.

## Object Re-reading Pattern for Reposting

A test that changes the document write mode (posting -> unposting -> reposting) **must reread the object** between mode changes via `ДокОбъект = Ссылка.ПолучитьОбъект()` — this mirrors form behavior:

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

**Important (platform 8.3.27):** programmatic reposting in a server session can trigger a platform error `[ОшибкаХранимыхДанных]` (stack without application frames). Neither rereading nor `.ВТранзакции()` fixes it — this is a platform limitation. In this case, reposting idempotence is checked at the **scenario layer** (Vanessa, path through the form), and the unit test is written with `ЮТест.Пропустить()` and an explicit rationale.

## Example of Correct Registration with `.ВТранзакции()`

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
