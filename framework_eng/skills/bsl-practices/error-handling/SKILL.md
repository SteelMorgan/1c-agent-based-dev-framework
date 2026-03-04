---
name: error-handling
description: Error handling, transactions, and locking. This skill teaches the agent how to properly handle errors, manage transactions, and locks in 1С.
---

# Error handling, transactions, and locking

## Purpose

This skill teaches the agent how to properly handle errors, manage transactions, and locks in 1С. Errors in these areas are the most dangerous: they lead to data loss, hung locks (deadlocks), users being unable to work, and hard-to-reproduce bugs. Unlike performance issues (slow but working), transaction and locking errors can **completely stop** the enterprise.

**Key principle:** There is no automatic transaction management in 1С (unlike some frameworks). The developer **manually** starts, commits, and rolls back transactions. Every unclosed transaction is a potential disaster.

**Sources:**
- [ITS Standards: “Exception handling”](https://its.1c.ru/db/v8std)
- [ITS Standards: “Transactions: usage rules”](https://its.1c.ru/db/v8std)
- [ITS Standards: “Managed locks”](https://its.1c.ru/db/v8std)
- Platform documentation: “Managed lock mechanism”

---

## Summary of rules

| # | Rule | Rationale |
|---|---------|-------------|
| 1 | Always log inside Исключение | Diagnostics, audit |
| 2 | Canonical transaction pattern | Prevent hung transactions |
| 3 | ТранзакцияАктивна() — defensive, not a substitute | Correct transaction nesting |
| 4 | Minimize transaction duration | Reduce contention |
| 5 | БлокировкаДанных before reading | Prevent race conditions |
| 6 | ЗаблокироватьДанныеДляРедактирования | Prevent lost update |
| 7 | ЗаписьЖурналаРегистрации follows standard | Structured diagnostics |
| 8 | User-friendly messages | UX when errors occur |
| 9 | ВызватьИсключение with/without parameter | Preserve stack vs clarity |
| 10 | Element-wise handling of bulk operations | Single error does not block everything |
| 11 | Consistent locking order | Prevent deadlock |
| 12 | Retry logic for external calls | External systems are unreliable |

---

## Rule 1: Try/Except — always log, never swallow

**Why:** A swallowed exception is the most dangerous anti-pattern. The system appears to work, but:
- Data is not written / processed
- The user has no error notification
- There are no traces in the log book
- Debugging is impossible — reproducing the issue without logs is not feasible

**ITS standard:** “In the exception handler, be sure to record error information in the log book.”

### Canonical exception handling pattern

```bsl
Попытка
    // Dangerous operation
    ДокументОбъект.Записать(РежимЗаписиДокумента.Проведение);
Исключение
    // 1. Obtain full error information
    ИнформацияОбОшибке = ИнформацияОбОшибке();
    
    // 2. Log to the registration log
    ЗаписьЖурналаРегистрации(
        НСтр("ru = 'Проведение документа'"),                     // Event name
        УровеньЖурналаРегистрации.Ошибка,                        // Level
        ДокументОбъект.Метаданные(),                              // Metadata of the object
        ДокументОбъект.Ссылка,                                    // Reference to the object
        ПодробноеПредставлениеОшибки(ИнформацияОбОшибке));       // Full error stack
    
    // 3. Decide: rethrow or handle
    ВызватьИсключение;  // Rethrow so the caller can handle it
КонецПопытки;
```

### Two error representations

| Function | When to use | What it contains |
|---------|-------------------|-------------|
| `КраткоеПредставлениеОшибки()` | To display to the user | Friendly text without technical details |
| `ПодробноеПредставлениеОшибки()` | For the registration log | Full call stack, line numbers, nested errors |

### Example: different handling layers

```bsl
// Lower layer — logging + rethrow
Функция ЗаписатьДокумент(ДокументОбъект)
    Попытка
        ДокументОбъект.Записать(РежимЗаписиДокумента.Проведение);
        Возврат Истина;
    Исключение
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'Проведение документа'"),
            УровеньЖурналаРегистрации.Ошибка,
            ДокументОбъект.Метаданные(),
            ДокументОбъект.Ссылка,
            ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
        ВызватьИсключение;
    КонецПопытки;
КонецФункции

// Upper layer (form handler) — show message to the user
&НаКлиенте
Процедура ЗаписатьДокумент(Команда)
    Попытка
        ЗаписатьДокументНаСервере();
    Исключение
        // User sees a short and clear message
        ПоказатьПредупреждение(,
            НСтр("ru = 'Не удалось записать документ. Обратитесь к администратору.'"));
    КонецПопытки;
КонецПроцедуры
```

---

## Rule 2: Canonical transaction pattern

**Why:** A transaction in 1С must be:
- **Atomic** — either all operations succeed or none do
- **Always closed** — `ЗафиксироватьТранзакцию()` or `ОтменитьТранзакцию()` must be called
- **As short as possible** — the longer the transaction, the longer data stays locked

An unclosed transaction blocks writes in the DBMS. Other users accessing the same data will wait (timeout usually 20 seconds) and receive a “Lock wait timeout exceeded” error.

**ITS standard:** “Transactions: usage rules” — `НачатьТранзакцию()` MUST be placed immediately before `Попытка`.

### Canonical pattern (MANDATORY)

```bsl
НачатьТранзакцию();
Попытка
    
    // === Operations inside the transaction ===
    
    // 1. Data locking (if needed — see rule 5)
    Блокировка = Новый БлокировкаДанных;
    ЭлементБлокировки = Блокировка.Добавить("Документ.РеализацияТоваровУслуг");
    ЭлементБлокировки.УстановитьЗначение("Ссылка", ДокументСсылка);
    Блокировка.Заблокировать();
    
    // 2. Read and modify data
    ДокументОбъект = ДокументСсылка.ПолучитьОбъект();
    ДокументОбъект.Статус = Перечисления.СтатусыДокументов.Согласован;
    
    // 3. Write
    ДокументОбъект.Записать();
    
    // === Commit — LAST operation before Исключение ===
    ЗафиксироватьТранзакцию();
    
Исключение
    // === Rollback — FIRST operation inside Исключение ===
    ОтменитьТранзакцию();
    
    // Logging AFTER rollback (writing to the log can also be inside a transaction!)
    ЗаписьЖурналаРегистрации(
        НСтр("ru = 'Согласование документа'"),
        УровеньЖурналаРегистрации.Ошибка,
        Метаданные.Документы.РеализацияТоваровУслуг,
        ДокументСсылка,
        ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
    
    ВызватьИсключение;
КонецПопытки;
```

### Critical ordering rules

| Requirement | Why |
|------------|--------|
| `НачатьТранзакцию()` immediately before `Попытка` | Any error between them leaves the transaction open |
| `ЗафиксироватьТранзакцию()` is the last statement before `Исключение` | Any statement after the commit will not roll back if it throws |
| `ОтменитьТранзакцию()` is the first statement in `Исключение` | Logging can also fail; if rollback has not been performed, cascading issues occur |
| `ЗаписьЖурналаРегистрации()` comes AFTER `ОтменитьТранзакцию()` | A log entry inside a rolled-back transaction will be lost |

### Incorrect variants

```bsl
// ❌ BAD: Begin transaction before Попытка with intermediate code
НачатьТранзакцию();
ПодготовитьДанные(); // If an error occurs here, the transaction remains open!
Попытка
    // ...
КонецПопытки;

// ❌ BAD: Log before ОтменитьТранзакцию
Исключение
    ЗаписьЖурналаРегистрации(...); // May be lost when rollback occurs!
    ОтменитьТранзакцию();
КонецПопытки;

// ❌ BAD: code after ЗафиксироватьТранзакцию but before the end of Попытка
    ЗафиксироватьТранзакцию();
    ОтправитьОповещение(); // If this fails, the transaction is already committed, but Исключение will run!
Исключение
    ОтменитьТранзакцию(); // Error! The transaction is already committed!
КонецПопытки;
```

---

## Rule 3: Nested transactions — check ТранзакцияАктивна()

**Why:** Transactions in 1С are **reference counted**: a nested `НачатьТранзакцию()` does not open a new transaction but increments a counter. `ОтменитьТранзакцию()` marks the transaction as “rolled back,” and **any subsequent** `ЗафиксироватьТранзакцию()` (even at an outer level) will throw an exception.

This means: if an inner procedure rolls back the transaction and the outer code does not know, the outer code will fail when it tries to commit.

### Correct approach — check before transactional operations

```bsl
// Procedure that might be called standalone or inside an outer transaction
Процедура ЗаписатьДанные(ДанныеДляЗаписи)
    
    НачатьТранзакцию();
    Попытка
        
        // ... write operations ...
        ДокументОбъект.Записать();
        
        ЗафиксироватьТранзакцию();
    Исключение
        ОтменитьТранзакцию();
        
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'Запись данных'"),
            УровеньЖурналаРегистрации.Ошибка,,,
            ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
        
        ВызватьИсключение; // MUST rethrow — outer code must know
    КонецПопытки;
    
КонецПроцедуры

// Outer code calling the procedure within its own transaction
Процедура ОбработатьПакетДокументов(МассивДокументов)
    
    НачатьТранзакцию();
    Попытка
        
        Для Каждого ДанныеДокумента Из МассивДокументов Цикл
            ЗаписатьДанные(ДанныеДокумента); // Nested transaction
        КонецЦикла;
        
        ЗафиксироватьТранзакцию();
    Исключение
        ОтменитьТранзакцию(); // Rolls back ALL writes, including nested ones
        
        ЗаписьЖурналаРегистрации(...);
        ВызватьИсключение;
    КонецПопытки;
    
КонецПроцедуры
```

### ТранзакцияАктивна() check for defensive coding

```bsl
// Defensive check — helpful when code does not know if rollback already happened
Если ТранзакцияАктивна() Тогда
    ОтменитьТранзакцию();
КонецЕсли;
```

### Rule: DO NOT use ТранзакцияАктивна() as a substitute for the correct pattern

```bsl
// ❌ BAD: ТранзакцияАктивна() as “protection” against double rollback
Попытка
    НачатьТранзакцию();
    // ...
    ЗафиксироватьТранзакцию();
Исключение
    Если ТранзакцияАктивна() Тогда  // Masks structural issues!
        ОтменитьТранзакцию();
    КонецЕсли;
КонецПопытки;

// ✅ CORRECT: proper structure makes the check unnecessary
НачатьТранзакцию();
Попытка
    // ...
    ЗафиксироватьТранзакцию();
Исключение
    ОтменитьТранзакцию();  // Always called exactly once
    ВызватьИсключение;
КонецПопытки;
```

---

## Rule 4: Minimize transaction duration

**Why:** While a transaction is open, the changed data is locked in the DBMS. Other sessions accessing the same data **wait** for transaction completion. The wait timeout is usually 20 seconds, after which an error occurs.

A long transaction = cascading locks = users cannot work.

### Correct approach — minimal transaction block

```bsl
// Data preparation — OUTSIDE the transaction
МассивДанных = ПодготовитьДанные();      // Queries, calculations — long but safe
ПроверитьКорректность(МассивДанных);      // Validation — also outside the transaction

// Transaction — only writes
НачатьТранзакцию();
Попытка
    // Only quick write operations
    Для Каждого ДанныеСтроки Из МассивДанных Цикл
        ЗаписатьСтроку(ДанныеСтроки);
    КонецЦикла;
    
    ЗафиксироватьТранзакцию();
Исключение
    ОтменитьТранзакцию();
    ЗаписьЖурналаРегистрации(...);
    ВызватьИсключение;
КонецПопытки;
```

### Incorrect — long operations inside the transaction

```bsl
// ❌ BAD: query + processing + external call inside the transaction
НачатьТранзакцию();
Попытка
    
    // Long query — locks data for its execution time
    Результат = ВыполнитьСложныйЗапрос(); // 5 seconds
    
    // Processing — more time
    Для Каждого Строка Из Результат Цикл
        ОбработатьСтроку(Строка); // Each row writes to the DB
    КонецЦикла;
    
    // External call — CATASTROPHE inside a transaction!
    ОтправитьHTTPЗапрос(Данные); // HTTP timeout = 30 sec → 30-second lock!
    
    ЗафиксироватьТранзакцию();
Исключение
    ОтменитьТранзакцию();
КонецПопытки;
```

---

## Rule 5: Managed locks — БлокировкаДанных

**Why:** Automatic 1С locks (the “Automatic” mode) block whole tables, causing conflicts during parallel user work. Managed locks allow you to block only the needed records, drastically reducing contention.

**ITS standard:** “Managed locks” — use `БлокировкаДанных` for granular locking before reading and modifying.

### Pattern: read-lock-modify-write

```bsl
НачатьТранзакцию();
Попытка
    
    // 1. LOCK FIRST — so no one can change the data between reading and writing
    Блокировка = Новый БлокировкаДанных;
    ЭлементБлокировки = Блокировка.Добавить("РегистрНакопления.ТоварыНаСкладах");
    ЭлементБлокировки.УстановитьЗначение("Номенклатура", НоменклатураСсылка);
    ЭлементБлокировки.УстановитьЗначение("Склад", СкладСсылка);
    ЭлементБлокировки.Режим = РежимБлокировкиДанных.Исключительный;
    Блокировка.Заблокировать();
    
    // 2. Now read — guaranteed current data
    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ
    |   Остатки.КоличествоОстаток КАК Остаток
    |ИЗ
    |   РегистрНакопления.ТоварыНаСкладах.Остатки(,
    |       Номенклатура = &Номенклатура И Склад = &Склад) КАК Остатки";
    Запрос.УстановитьПараметр("Номенклатура", НоменклатураСсылка);
    Запрос.УстановитьПараметр("Склад", СкладСсылка);
    
    Результат = Запрос.Выполнить();
    Если Результат.Пустой() Тогда
        ВызватьИсключение НСтр("ru = 'Нет остатков на складе.'");
    КонецЕсли;
    
    Выборка = Результат.Выбрать();
    Выборка.Следующий();
    
    // 3. Check and modify
    Если Выборка.Остаток < ТребуемоеКоличество Тогда
        ВызватьИсключение СтрШаблон(
            НСтр("ru = 'Недостаточно остатков. На складе: %1, требуется: %2.'"),
            Выборка.Остаток, ТребуемоеКоличество);
    КонецЕсли;
    
    // 4. Write
    // ... record movements ...
    
    ЗафиксироватьТранзакцию();
Исключение
    ОтменитьТранзакцию();
    ЗаписьЖурналаРегистрации(...);
    ВызватьИсключение;
КонецПопытки;
```

### Why lock BEFORE reading

```
Без блокировки (race condition):
  Сеанс A: Читает остаток = 10        | Сеанс B: Читает остаток = 10
  Сеанс A: 10 >= 8? Да, списываем 8   | Сеанс B: 10 >= 7? Да, списываем 7
  Итого: списано 15 единиц при остатке 10 → отрицательный остаток!

С блокировкой:
  Сеанс A: Блокирует → Читает 10 → Списывает 8 → Фиксирует → Разблокирует
  Сеанс B: Ждёт блокировку → Читает 2 → 2 < 7 → Ошибка (корректная!)
```

---

## Rule 6: ЗаблокироватьДанныеДляРедактирования — pessimistic object locking

**Why:** When two users open the same document simultaneously, both edit and save — the second user's data overwrites the first (lost update). `ЗаблокироватьДанныеДляРедактирования()` prevents this: the second user receives a “Object is locked by user X” error.

### Correct usage in a form module

```bsl
// The platform automatically locks the object when the form is opened.
// Manual locking is needed only when modifying programmatically.

// Programmatic modification — explicit lock
Процедура ИзменитьСтатусДокумента(ДокументСсылка, НовыйСтатус)
    
    НачатьТранзакцию();
    Попытка
        
        // Lock the object against concurrent edits
        ЗаблокироватьДанныеДляРедактирования(ДокументСсылка);
        
        ДокументОбъект = ДокументСсылка.ПолучитьОбъект();
        ДокументОбъект.Статус = НовыйСтатус;
        ДокументОбъект.Записать();
        
        ЗафиксироватьТранзакцию();
    Исключение
        ОтменитьТранзакцию();
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'Изменение статуса документа'"),
            УровеньЖурналаРегистрации.Ошибка,,,
            ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
        ВызватьИсключение;
    КонецПопытки;
    
КонецПроцедуры
```

### Difference between locks

| Lock type | Mechanism | Level | When to use |
|----------------|----------|---------|-------------------|
| `БлокировкаДанных` | DBMS (managed) | Register/table rows | Control balances, atomic operations |
| `ЗаблокироватьДанныеДляРедактирования` | 1С server (pessimistic) | Entire object | Prevent lost update during concurrent editing |
| Automatic lock | DBMS (upon write) | Depends on mode | Always works but is coarse-grained |

---

## Rule 7: ЗаписьЖурналаРегистрации — proper logging

**Why:** The registration log (ЖР) is the primary tool for diagnosing production issues. Without structured logs, incident investigation becomes guesswork. Proper logging:
- Lets you find the problem in minutes instead of hours
- Provides context: what happened, which object, who initiated it
- Is filterable by level, event, object, and user

### Full log entry format

```bsl
ЗаписьЖурналаРегистрации(
    ИмяСобытия,         // String — hierarchical name (dot-separated)
    УровеньСобытия,     // УровеньЖурналаРегистрации — Ошибка/Предупреждение/Информация/Примечание
    МетаданныеОбъекта,  // Metadata object — for filtering by type
    Данные,             // Reference to the object — for navigation from the log
    Комментарий);       // String — detailed description (up to 1024 characters)
```

### Levels and when to use them

| Level | When | Example |
|---------|-------|--------|
| `Ошибка` | Operation failed, data lost or invalid | Write error, transaction rollback |
| `Предупреждение` | Operation succeeded but with caveats | Document saved without posting, incomplete data |
| `Информация` | Significant events for audit | User deleted 100 items, data exchange completed |
| `Примечание` | Diagnostic details | Operation duration, call parameters |

### Example: structured logging

```bsl
// The event name is hierarchical for filtering
// Format: Subsystem.Event or Object.Action
ИмяСобытия = НСтр("ru = 'ОбменДанными.ОтправкаДанных.Ошибка'");

// Comment — maximum diagnostic detail
Комментарий = СтрШаблон(
    НСтр("ru = 'Ошибка при отправке данных в узел ""%1"".
    |Количество объектов: %2.
    |Текст ошибки:
    |%3'"),
    Строка(УзелОбмена),
    КоличествоОбъектов,
    ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));

ЗаписьЖурналаРегистрации(
    ИмяСобытия,
    УровеньЖурналаРегистрации.Ошибка,
    Метаданные.ПланыОбмена.ОбменСКонтрагентами,
    УзелОбмена,
    Комментарий);
```

---

## Rule 8: User-facing error messages

**Why:** The user is not a developer. A message such as “{ОбщийМодуль.МодульОбработкиДанных.Модуль(123)}: Ошибка при вызове метода контекста” is useless. The user needs to know:
1. **What happened** (in plain language)
2. **What to do** (retry, contact the administrator, check data)
3. **Where to go** for help

### Correct approach — human-readable text

```bsl
Попытка
    ДокументОбъект.Записать(РежимЗаписиДокумента.Проведение);
Исключение
    // Log technical information
    ЗаписьЖурналаРегистрации(
        НСтр("ru = 'Проведение документа'"),
        УровеньЖурналаРегистрации.Ошибка,
        ДокументОбъект.Метаданные(),
        ДокументОбъект.Ссылка,
        ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
    
    // Friendly message to the user
    ТекстДляПользователя = СтрШаблон(
        НСтр("ru = 'Не удалось провести документ "%1".
        |Попробуйте повторить операцию. Если ошибка повторяется, обратитесь к администратору.
        |
        |Техническая информация: %2'"),
        ДокументОбъект,
        КраткоеПредставлениеОшибки(ИнформацияОбОшибке()));
    
    ВызватьИсключение ТекстДляПользователя;
КонецПопытки;
```

### Incorrect — showing technical stack to the user

```bsl
// ❌ BAD: rethrow the technical error to the user
Попытка
    ДокументОбъект.Записать();
Исключение
    ВызватьИсключение; // The user will see the call stack with line numbers
КонецПопытки;
```

---

## Rule 9: Proper exception propagation — ВызватьИсключение

**Why:** In BSL there are two ways to rethrow exceptions:
1. `ВызватьИсключение;` (without parameters) — rethrows the **original** exception with the full call stack
2. `ВызватьИсключение ТекстСообщения;` (with a string) — creates a **new** exception, and the original stack is lost

### When to use each

| Way | When | Why |
|--------|-------|--------|
| `ВызватьИсключение;` | In intermediate code (object module, common module) | Keeps the original stack for easier diagnosis |
| `ВызватьИсключение "Текст";` | In form handlers or at the user boundary | Replaces the technical stack with a clear message |

### Example

```bsl
// Intermediate layer — rethrow the original
Процедура ОбработатьДанные(Данные)
    Попытка
        ЗаписатьДанные(Данные);
    Исключение
        ЗаписьЖурналаРегистрации(...);
        ВызватьИсключение; // Original stack is preserved
    КонецПопытки;
КонецПроцедуры

// User boundary — craft a friendly message
&НаСервере
Процедура ОбработатьНаСервере()
    Попытка
        ОбработатьДанные(ДанныеФормы);
    Исключение
        ВызватьИсключение СтрШаблон(
            НСтр("ru = 'Ошибка обработки данных: %1'"),
            КраткоеПредставлениеОшибки(ИнформацияОбОшибке()));
    КонецПопытки;
КонецПроцедуры
```

---

## Rule 10: Handling errors during bulk operations

**Why:** When processing an array of documents (batch posting, data exchange), an error in one document should not stop the rest. However, each error must be logged.

### Pattern: element-wise handling with aggregated errors

```bsl
Процедура ПровестиДокументыПакетно(МассивДокументов)
    
    МассивОшибок = Новый Массив;
    
    Для Каждого ДокументСсылка Из МассивДокументов Цикл
        
        НачатьТранзакцию();
        Попытка
            
            ДокументОбъект = ДокументСсылка.ПолучитьОбъект();
            ДокументОбъект.Записать(РежимЗаписиДокумента.Проведение);
            
            ЗафиксироватьТранзакцию();
        Исключение
            ОтменитьТранзакцию();
            
            ИнфоОшибки = ИнформацияОбОшибке();
            
            // Log each error separately
            ЗаписьЖурналаРегистрации(
                НСтр("ru = 'Пакетное проведение'"),
                УровеньЖурналаРегистрации.Ошибка,
                ДокументСсылка.Метаданные(),
                ДокументСсылка,
                ПодробноеПредставлениеОшибки(ИнфоОшибки));
            
            // Accumulate for final report
            МассивОшибок.Добавить(Новый Структура("Документ, Ошибка",
                ДокументСсылка,
                КраткоеПредставлениеОшибки(ИнфоОшибки)));
            
            // DO NOT break the loop — continue with other documents
        КонецПопытки;
        
    КонецЦикла;
    
    // Final summary
    Если МассивОшибок.Количество() > 0 Тогда
        ТекстИтога = СтрШаблон(
            НСтр("ru = 'Обработано документов: %1. Ошибок: %2.'"),
            МассивДокументов.Количество(),
            МассивОшибок.Количество());
        
        // Show the errors to the user
        Для Каждого ОписаниеОшибки Из МассивОшибок Цикл
            ОбщегоНазначения.СообщитьПользователю(
                СтрШаблон(НСтр("ru = 'Документ %1: %2'"),
                    ОписаниеОшибки.Документ, ОписаниеОшибки.Ошибка));
        КонецЦикла;
    КонецЕсли;
    
КонецПроцедуры
```

---

## Rule 11: Lock timeouts and deadlock

**Why:** When two sessions lock resources in different orders, deadlock occurs:
- Session A locks resource 1 and waits for resource 2
- Session B locks resource 2 and waits for resource 1
- Both wait indefinitely

The DBMS detects the deadlock and rolls back one of the transactions. This is normal but must be handled.

### Correct approach — consistent locking order

```bsl
// RULE: always lock resources in a predetermined order (for example, by reference)
МассивСсылок = ОбщегоНазначенияКлиентСервер.СвернутьМассив(МассивДокументов);
МассивСсылок.СортироватьПоЗначению(); // Fixed order — prevents deadlock

Для Каждого Ссылка Из МассивСсылок Цикл
    ЗаблокироватьДанныеДляРедактирования(Ссылка);
КонецЦикла;
```

### Handling lock errors

```bsl
НачатьТранзакцию();
Попытка
    
    Блокировка = Новый БлокировкаДанных;
    ЭлементБлокировки = Блокировка.Добавить("Справочник.Номенклатура");
    ЭлементБлокировки.УстановитьЗначение("Ссылка", НоменклатураСсылка);
    
    Попытка
        Блокировка.Заблокировать();
    Исключение
        // Could not lock — data is occupied by another user
        ОтменитьТранзакцию();
        ВызватьИсключение СтрШаблон(
            НСтр("ru = 'Не удалось заблокировать "%1". Данные редактируются другим пользователем. Повторите попытку позже.'"),
            НоменклатураСсылка);
    КонецПопытки;
    
    // Data is locked — safe to work
    НоменклатураОбъект = НоменклатураСсылка.ПолучитьОбъект();
    // ...
    НоменклатураОбъект.Записать();
    
    ЗафиксироватьТранзакцию();
Исключение
    Если ТранзакцияАктивна() Тогда
        ОтменитьТранзакцию();
    КонецЕсли;
    ЗаписьЖурналаРегистрации(...);
    ВызватьИсключение;
КонецПопытки;
```

---

## Rule 12: Try/Except for external calls

**Why:** External system calls (HTTP, mail, file operations) are unreliable by definition: the network might be unavailable, a file locked, the service overloaded. Such calls must **always** be wrapped in `Попытка/Исключение` with meaningful handling.

### Pattern: HTTP call with retries

```bsl
Функция ОтправитьДанныеВоВнешнююСистему(Данные)
    
    МаксимумПопыток = 3;
    
    Для НомерПопытки = 1 По МаксимумПопыток Цикл
        
        Попытка
            
            HTTPСоединение = Новый HTTPСоединение("api.example.com",,,,, 30); // timeout 30 sec
            Запрос = Новый HTTPЗапрос("/api/data");
            Запрос.УстановитьТелоИзСтроки(Данные);
            
            Ответ = HTTPСоединение.ОтправитьДляОбработки(Запрос);
            
            Если Ответ.КодСостояния = 200 Тогда
                Возврат Истина;
            Иначе
                ВызватьИсключение СтрШаблон(
                    НСтр("ru = 'Сервер вернул код %1: %2'"),
                    Ответ.КодСостояния,
                    Ответ.ПолучитьТелоКакСтроку());
            КонецЕсли;
            
        Исключение
            
            ЗаписьЖурналаРегистрации(
                НСтр("ru = 'Интеграция.ОтправкаДанных'"),
                ?(НомерПопытки < МаксимумПопыток,
                    УровеньЖурналаРегистрации.Предупреждение,
                    УровеньЖурналаРегистрации.Ошибка),,,
                СтрШаблон(НСтр("ru = 'Попытка %1 из %2. Ошибка: %3'"),
                    НомерПопытки, МаксимумПопыток,
                    ПодробноеПредставлениеОшибки(ИнформацияОбОшибке())));
            
            Если НомерПопытки = МаксимумПопыток Тогда
                ВызватьИсключение;
            КонецЕсли;
            
            // Pause before the retry (exponential backoff)
            // There is no built-in Sleep in 1С, but a wait handler can be used
            
        КонецПопытки;
        
    КонецЦикла;
    
    Возврат Ложь;
    
КонецФункции
```

---

depends_on: []
---
