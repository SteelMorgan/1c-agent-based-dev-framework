---
name: error-handling
description: Error handling, transactions, and locks. This skill teaches the agent how to properly handle exceptions, manage transactions, and work with locks in 1С.
---

# Error Handling, Transactions, and Locks

**Key principle:** 1С does not have automatic transaction management. The developer **manually** controls Begin/Commit/Rollback. Every unclosed transaction is a potential catastrophe.

---

## Rule 1: Try/Catch — always log, never swallow

A swallowed exception is the most dangerous anti-pattern: data is not persisted, the user is unaware of the problem, there is no trail in the log, and debugging becomes impossible.

ITS standard: “The exception handler must always record the error information in the registration log.”

### Canonical exception handling pattern

```bsl
Попытка
    ДокументОбъект.Записать(РежимЗаписиДокумента.Проведение);
Исключение
    ИнформацияОбОшибке = ИнформацияОбОшибке();

    ЗаписьЖурналаРегистрации(
        НСтр("ru = 'Проведение документа'"),
        УровеньЖурналаРегистрации.Ошибка,
        ДокументОбъект.Метаданные(),
        ДокументОбъект.Ссылка,
        ПодробноеПредставлениеОшибки(ИнформацияОбОшибке));

    ВызватьИсключение;
КонецПопытки;
```

### Two views of an error

| Function | When to use | Contents |
|---------|-------------------|-------------|
| `КраткоеПредставлениеОшибки()` | For displaying to the user | A readable text without technical details |
| `ПодробноеПредставлениеОшибки()` | For the registration log | Full call stack, line numbers, nested errors |

### Example: different exception handling layers

```bsl
// Lower level — logging + rethrow
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

// Upper layer (form) — show message to user
&НаКлиенте
Процедура ЗаписатьДокумент(Команда)
    Попытка
        ЗаписатьДокументНаСервере();
    Исключение
        ПоказатьПредупреждение(,
            НСтр("ru = 'Не удалось записать документ. Обратитесь к администратору.'"));
    КонецПопытки;
КонецПроцедуры
```

---

## Rule 2: Canonical transaction pattern

An open transaction blocks records in the DBMS. Other sessions wait (timeout ~20 seconds) and then fail.

ITS standard: “Transactions: usage rules” — `НачатьТранзакцию()` must ALWAYS appear immediately before `Попытка`.

### Canonical pattern (MANDATORY)

```bsl
НачатьТранзакцию();
Попытка

    // 1. Lock the data (if needed — see rule 5)
    Блокировка = Новый БлокировкаДанных;
    ЭлементБлокировки = Блокировка.Добавить("Документ.РеализацияТоваровУслуг");
    ЭлементБлокировки.УстановитьЗначение("Ссылка", ДокументСсылка);
    Блокировка.Заблокировать();

    // 2. Read and modify
    ДокументОбъект = ДокументСсылка.ПолучитьОбъект();
    ДокументОбъект.Статус = Перечисления.СтатусыДокументов.Согласован;

    // 3. Write
    ДокументОбъект.Записать();

    // === Commit — LAST operation before Исключение ===
    ЗафиксироватьТранзакцию();

Исключение
    // === Rollback — FIRST operation in Исключение ===
    ОтменитьТранзакцию();

    // Logging AFTER rollback (any log entry inside the rolled-back transaction is lost!)
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
| `НачатьТранзакцию()` immediately before `Попытка` | If an error occurs between them, the transaction will remain open |
| `ЗафиксироватьТранзакцию()` as the last statement before `Исключение` | Any statement after commit that throws will not be rolled back |
| `ОтменитьТранзакцию()` as the first statement in `Исключение` | Logging might also fail; if rollback hasn’t happened yet, it causes cascading issues |
| `ЗаписьЖурналаРегистрации()` AFTER `ОтменитьТранзакцию()` | Log entries inside a rolled-back transaction are lost |

### Wrong variants (platform traps)

```bsl
// BAD: code between НачатьТранзакцию and Попытка
НачатьТранзакцию();
ПодготовитьДанные(); // If this throws — the transaction hangs!
Попытка
    // ...
КонецПопытки;

// BAD: logging BEFORE ОтменитьТранзакцию
Исключение
    ЗаписьЖурналаРегистрации(...); // Might be lost during rollback!
    ОтменитьТранзакцию();
КонецПопытки;

// BAD: code after ЗафиксироватьТранзакцию but before the end of Попытка
    ЗафиксироватьТранзакцию();
    ОтправитьОповещение(); // An error here occurs after the transaction is already committed, but Исключение still runs!
Исключение
    ОтменитьТранзакцию(); // Error! The transaction is already committed!
КонецПопытки;
```

---

## Rule 3: Nested transactions are reference-counted

In 1С a nested `НачатьТранзакцию()` does not create a new transaction; it increments a counter. `ОтменитьТранзакцию()` marks the transaction as “rolled back”, and **any subsequent** `ЗафиксироватьТранзакцию()` (even at an outer level) will raise an exception.

### Correct approach — each layer follows the canonical pattern and rethrows

```bsl
Процедура ЗаписатьДанные(ДанныеДляЗаписи)

    НачатьТранзакцию();
    Попытка
        ДокументОбъект.Записать();
        ЗафиксироватьТранзакцию();
    Исключение
        ОтменитьТранзакцию();
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'Запись данных'"),
            УровеньЖурналаРегистрации.Ошибка,,,
            ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
        ВызватьИсключение; // MUST rethrow — outer code needs to know
    КонецПопытки;

КонецПроцедуры
```

### Rule: DO NOT rely on ТранзакцияАктивна() as a substitute for the proper pattern

```bsl
// BAD: ТранзакцияАктивна() hides structural issues
Попытка
    НачатьТранзакцию();
    // ...
    ЗафиксироватьТранзакцию();
Исключение
    Если ТранзакцияАктивна() Тогда
        ОтменитьТранзакцию();
    КонецЕсли;
КонецПопытки;

// CORRECT: a proper structure makes the check unnecessary
НачатьТранзакцию();
Попытка
    // ...
    ЗафиксироватьТранзакцию();
Исключение
    ОтменитьТранзакцию();
    ВызватьИсключение;
КонецПопытки;
```

---

## Rule 4: Minimize transaction duration

While a transaction is open, modified rows stay locked in the DBMS. A long transaction equals cascading locks and blocked users.

```bsl
// Prepare data OUTSIDE the transaction
МассивДанных = ПодготовитьДанные();
ПроверитьКорректность(МассивДанных);

// Transaction — only fast write operations
НачатьТранзакцию();
Попытка
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

External HTTP calls inside a transaction are a disaster: HTTP timeout is 30 seconds, which means the lock lasts at least that long.

---

## Rule 5: Controlled locking — БлокировкаДанных

Without a granular lock before read-and-modify, race conditions appear: two sessions read the same value, both modify it, and one update is lost.

ITS standard: “Controlled locks”.

### Pattern: lock-read-change-write

```bsl
НачатьТранзакцию();
Попытка

    // 1. Lock first
    Блокировка = Новый БлокировкаДанных;
    ЭлементБлокировки = Блокировка.Добавить("РегистрНакопления.ТоварыНаСкладах");
    ЭлементБлокировки.УстановитьЗначение("Номенклатура", НоменклатураСсылка);
    ЭлементБлокировки.УстановитьЗначение("Склад", СкладСсылка);
    ЭлементБлокировки.Режим = РежимБлокировкиДанных.Исключительный;
    Блокировка.Заблокировать();

    // 2. Read — guaranteed current data
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

    // 3. Validate
    Если Выборка.Остаток < ТребуемоеКоличество Тогда
        ВызватьИсключение СтрШаблон(
            НСтр("ru = 'Недостаточно остатков. На складе: %1, требуется: %2.'"),
            Выборка.Остаток, ТребуемоеКоличество);
    КонецЕсли;

    // 4. Write the movements
    // ... запись движений ...

    ЗафиксироватьТранзакцию();
Исключение
    ОтменитьТранзакцию();
    ЗаписьЖурналаРегистрации(...);
    ВызватьИсключение;
КонецПопытки;
```

### Why lock before reading

```
Without locking (race condition):
  Session A: reads balance = 10        | Session B: reads balance = 10
  Session A: 10 >= 8? Yes, subtract 8  | Session B: 10 >= 7? Yes, subtract 7
  Total: 15 units removed with only 10 in stock → negative balance!

With locking:
  Session A: lock → read 10 → subtract 8 → commit → unlock
  Session B: waits for the lock → reads 2 → 2 < 7 → error (correct behavior!)
```

---

## Rule 6: ЗаблокироватьДанныеДляРедактирования — pessimistic locking of objects

Prevents lost updates: a second user receives the error “Object locked by user X.”

```bsl
Процедура ИзменитьСтатусДокумента(ДокументСсылка, НовыйСтатус)

    НачатьТранзакцию();
    Попытка
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

### Difference between lock types

| Lock type | Mechanism | When to use |
|----------------|----------|-------------------|
| `БлокировкаДанных` | DBMS (controlled) at the record level | Inventory control, atomic operations |
| `ЗаблокироватьДанныеДляРедактирования` | 1С server (pessimistic) on the full object | Preventing lost updates |

---

## Rule 7: ЗаписьЖурналаРегистрации — correct logging

### Full log entry format

```bsl
ЗаписьЖурналаРегистрации(
    ИмяСобытия,         // String — hierarchical name (dot notation)
    УровеньСобытия,     // УровеньЖурналаРегистрации — Ошибка/Предупреждение/Информация/Примечание
    МетаданныеОбъекта,  // Metadata object — for filtering by type
    Данные,             // Reference to the object — for jumping from the log
    Комментарий);       // String — detailed description (up to 1024 characters)
```

### Levels

| Level | When |
|---------|-------|
| `Ошибка` | Operation failed, data lost or incorrect |
| `Предупреждение` | Operation succeeded, but with limitations |
| `Информация` | Significant events for audit |
| `Примечание` | Diagnostic information |

### Example: structured logging

```bsl
ИмяСобытия = НСтр("ru = 'ОбменДанными.ОтправкаДанных.Ошибка'");

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

## Rule 8: Crafting user-facing error messages

For the user — **what happened** and **what to do**. In the log — the technical details.

```bsl
Попытка
    ДокументОбъект.Записать(РежимЗаписиДокумента.Проведение);
Исключение
    ЗаписьЖурналаРегистрации(
        НСтр("ru = 'Проведение документа'"),
        УровеньЖурналаРегистрации.Ошибка,
        ДокументОбъект.Метаданные(),
        ДокументОбъект.Ссылка,
        ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));

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

---

## Rule 9: Proper exception propagation — ВызватьИсключение

| Method | When | Why |
|--------|-------|--------|
| `ВызватьИсключение;` | In intermediate code (object module, common module) | Preserves the original stack |
| `ВызватьИсключение "Text";` | At the user boundary (form) | Replaces the technical stack with a readable message |

```bsl
// Intermediate layer — rethrow the original
Процедура ОбработатьДанные(Данные)
    Попытка
        ЗаписатьДанные(Данные);
    Исключение
        ЗаписьЖурналаРегистрации(...);
        ВызватьИсключение; // Original stack preserved
    КонецПопытки;
КонецПроцедуры

// User boundary
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

## Rule 10: Error handling in bulk operations

An error in one document should not stop processing the others. Each document gets its own transaction.

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
            ЗаписьЖурналаРегистрации(
                НСтр("ru = 'Пакетное проведение'"),
                УровеньЖурналаРегистрации.Ошибка,
                ДокументСсылка.Метаданные(),
                ДокументСсылка,
                ПодробноеПредставлениеОшибки(ИнфоОшибки));

            МассивОшибок.Добавить(Новый Структура("Документ, Ошибка",
                ДокументСсылка,
                КраткоеПредставлениеОшибки(ИнфоОшибки)));
        КонецПопытки;

    КонецЦикла;

    Если МассивОшибок.Количество() > 0 Тогда
        Для Каждого ОписаниеОшибки Из МассивОшибок Цикл
            ОбщегоНазначения.СообщитьПользователю(
                СтрШаблон(НСтр("ru = 'Документ %1: %2'"),
                    ОписаниеОшибки.Документ, ОписаниеОшибки.Ошибка));
        КонецЦикла;
    КонецЕсли;

КонецПроцедуры
```

---

## Rule 11: Consistent locking order — prevent deadlocks

When two sessions lock data in different orders, a deadlock occurs. The DBMS rolls back one of the transactions.

```bsl
// Always lock resources in a fixed order (by reference)
МассивСсылок = ОбщегоНазначенияКлиентСервер.СвернутьМассив(МассивДокументов);
МассивСсылок.СортироватьПоЗначению();

Для Каждого Ссылка Из МассивСсылок Цикл
    ЗаблокироватьДанныеДляРедактирования(Ссылка);
КонецЦикла;
```

### Handling locking errors

```bsl
НачатьТранзакцию();
Попытка

    Блокировка = Новый БлокировкаДанных;
    ЭлементБлокировки = Блокировка.Добавить("Справочник.Номенклатура");
    ЭлементБлокировки.УстановитьЗначение("Ссылка", НоменклатураСсылка);

    Попытка
        Блокировка.Заблокировать();
    Исключение
        ОтменитьТранзакцию();
        ВызватьИсключение СтрШаблон(
            НСтр("ru = 'Не удалось заблокировать "%1". Данные редактируются другим пользователем.'"),
            НоменклатураСсылка);
    КонецПопытки;

    НоменклатураОбъект = НоменклатураСсылка.ПолучитьОбъект();
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

## Rule 12: Try/Catch for external calls

Calls to external systems are unreliable. **Always** wrap them in `Попытка/Исключение`.

### Pattern: HTTP call with retries

```bsl
Функция ОтправитьДанныеВоВнешнююСистему(Данные)

    МаксимумПопыток = 3;

    Для НомерПопытки = 1 По МаксимумПопыток Цикл

        Попытка

            HTTPСоединение = Новый HTTPСоединение("api.example.com",,,,, 30);
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

        КонецПопытки;

    КонецЦикла;

    Возврат Ложь;

КонецФункции
```

---

depends_on: []
---
