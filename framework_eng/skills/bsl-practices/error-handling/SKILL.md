---
name: error-handling
description: "Use for BSL exceptions, transactions, rollback, locks"
alwaysApply: false
---

# Error handling, transactions, and locks

**Key principle:** In 1C there is no automatic transaction management. The developer **manually** controls start, commit, and rollback. Every unclosed transaction is a potential disaster.

---

## Rule 1: Try/Except - always log, never swallow

A swallowed exception is the most dangerous anti-pattern: data is not written, the user does not know about the error, there is no trace in the registration log, and debugging is impossible.

ITS standard: "In an exception handler, error information must be recorded in the registration log."

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

### Two error representations

| Function | When to use | What it contains |
|---------|-------------------|-------------|
| `КраткоеПредставлениеОшибки()` | For displaying to the user | Clear text without technical details |
| `ПодробноеПредставлениеОшибки()` | For the registration log | Full call stack, line numbers, nested errors |

### Example: different handling levels

```bsl
// Lower level - logging + rethrow
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

// Upper level (form) - show to the user
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

An unclosed transaction blocks writes in the DBMS. Other sessions wait (timeout ~20 sec) and receive an error.

ITS standard: "Transactions: usage rules" - `НачатьТранзакцию()` is ALWAYS placed immediately before `Попытка`.

### Canonical pattern (MANDATORY)

```bsl
НачатьТранзакцию();
Попытка

    // 1. Data locking (if needed - see rule 5)
    Блокировка = Новый БлокировкаДанных;
    ЭлементБлокировки = Блокировка.Добавить("Документ.РеализацияТоваровУслуг");
    ЭлементБлокировки.УстановитьЗначение("Ссылка", ДокументСсылка);
    Блокировка.Заблокировать();

    // 2. Read and modify data
    ДокументОбъект = ДокументСсылка.ПолучитьОбъект();
    ДокументОбъект.Статус = Перечисления.СтатусыДокументов.Согласован;

    // 3. Write
    ДокументОбъект.Записать();

    // === Commit is the LAST operation before Исключение ===
    ЗафиксироватьТранзакцию();

Исключение
    // === Rollback is the FIRST operation in the Исключение block ===
    ОтменитьТранзакцию();

    // Logging AFTER rollback (an entry in the registration log inside a rolled-back transaction will be lost!)
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
| `НачатьТранзакцию()` immediately before `Попытка` | If an error occurs between them, the transaction will not close |
| `ЗафиксироватьТранзакцию()` is the last statement before `Исключение` | An operation after commit will not roll back if it fails |
| `ОтменитьТранзакцию()` is the first statement in `Исключение` | Logging can also fail; if rollback has not happened yet, it becomes a cascading problem |
| `ЗаписьЖурналаРегистрации()` is AFTER `ОтменитьТранзакцию()` | An entry in the registration log inside a rolled-back transaction will be lost |

### Wrong variants (platform traps)

```bsl
// BAD: code between НачатьТранзакцию and Попытка
НачатьТранзакцию();
ПодготовитьДанные(); // If there is an error here, the transaction will hang!
Попытка
    // ...
КонецПопытки;

// BAD: registration log entry BEFORE ОтменитьТранзакцию
Исключение
    ЗаписьЖурналаРегистрации(...); // Can be lost during rollback!
    ОтменитьТранзакцию();
КонецПопытки;

// BAD: code after ЗафиксироватьТранзакцию, but before the end of Попытка
    ЗафиксироватьТранзакцию();
    ОтправитьОповещение(); // Error here - the transaction is already committed, but Исключение will run!
Исключение
    ОтменитьТранзакцию(); // Error! The transaction is already committed!
КонецПопытки;
```

---

## Rule 3: Nested transactions are counter-based

In 1C, a nested `НачатьТранзакцию()` does not create a new transaction; it increments a counter. `ОтменитьТранзакцию()` marks the transaction as "rolled back", and **any subsequent** `ЗафиксироватьТранзакцию()` (even at the outer level) will raise an exception.

### Correct - each level uses the canonical pattern and rethrows the exception

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
        ВызватьИсключение; // MUST rethrow - the outer code must know
    КонецПопытки;

КонецПроцедуры
```

### Rule: DO NOT use `ТранзакцияАктивна()` as a substitute for the correct pattern

```bsl
// BAD: ТранзакцияАктивна() masks an error in the code structure
Попытка
    НачатьТранзакцию();
    // ...
    ЗафиксироватьТранзакцию();
Исключение
    Если ТранзакцияАктивна() Тогда
        ОтменитьТранзакцию();
    КонецЕсли;
КонецПопытки;

// CORRECT: the proper structure makes the check unnecessary
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

## Rule 4: Minimize transaction time

While a transaction is open, modified data is locked in the DBMS. A long transaction = cascading locks = users cannot work.

```bsl
// Data preparation - OUTSIDE the transaction
МассивДанных = ПодготовитьДанные();
ПроверитьКорректность(МассивДанных);

// Transaction - only fast write operations
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

External HTTP calls inside a transaction are a disaster: HTTP timeout = 30 sec = 30 sec lock.

---

## Rule 5: Managed locks - `БлокировкаДанных`

Without granular locking before read-modify, a race condition occurs: two sessions read the same value, both modify it - one update is lost.

ITS standard: "Managed locks".

### Pattern: lock-read-modify-write

```bsl
НачатьТранзакцию();
Попытка

    // 1. FIRST lock
    Блокировка = Новый БлокировкаДанных;
    ЭлементБлокировки = Блокировка.Добавить("РегистрНакопления.ТоварыНаСкладах");
    ЭлементБлокировки.УстановитьЗначение("Номенклатура", НоменклатураСсылка);
    ЭлементБлокировки.УстановитьЗначение("Склад", СкладСсылка);
    ЭлементБлокировки.Режим = РежимБлокировкиДанных.Исключительный;
    Блокировка.Заблокировать();

    // 2. Read - guaranteed up-to-date data
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

    // 3. Check
    Если Выборка.Остаток < ТребуемоеКоличество Тогда
        ВызватьИсключение СтрШаблон(
            НСтр("ru = 'Недостаточно остатков. На складе: %1, требуется: %2.'"),
            Выборка.Остаток, ТребуемоеКоличество);
    КонецЕсли;

    // 4. Write
    // ... movement posting ...

    ЗафиксироватьТранзакцию();
Исключение
    ОтменитьТранзакцию();
    ЗаписьЖурналаРегистрации(...);
    ВызватьИсключение;
КонецПопытки;
```

### Why lock BEFORE reading

```
Without locking (race condition):
  Session A: Reads balance = 10      | Session B: Reads balance = 10
  Session A: 10 >= 8? Yes, write off 8   | Session B: 10 >= 7? Yes, write off 7
  Total: 15 units written off with a balance of 10 -> negative balance!

With locking:
  Session A: Locks -> Reads 10 -> Writes off 8 -> Commits -> Unlocks
  Session B: Waits for lock -> Reads 2 -> 2 < 7 -> Error (correct!)
```

---

## Rule 6: `ЗаблокироватьДанныеДляРедактирования` - pessimistic object locking

Prevents lost update: the second user will get the error "Object locked by user X".

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

### Difference between locks

| Lock type | Mechanism | When to use |
|----------------|----------|-------------------|
| `БлокировкаДанных` | DBMS (managed), row level | Balance control, atomic operations |
| `ЗаблокироватьДанныеДляРедактирования` | 1C server (pessimistic), whole object | Preventing lost update |

---

## Rule 7: `ЗаписьЖурналаРегистрации` - proper logging

### Full entry format

```bsl
ЗаписьЖурналаРегистрации(
    ИмяСобытия,         // String - hierarchical name (with dots)
    УровеньСобытия,     // УровеньЖурналаРегистрации - Ошибка/Предупреждение/Информация/Примечание
    МетаданныеОбъекта,  // Metadata object - for filtering by type
    Данные,             // Object reference - for navigation from the registration log
    Комментарий);       // String - detailed description (up to 1024 characters)
```

### Levels

| Level | When |
|---------|-------|
| `Ошибка` | The operation did not complete, data is lost or invalid |
| `Предупреждение` | The operation completed, but with limitations |
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

## Rule 8: Generating error messages for the user

For the user - **what happened** and **what to do**. In the registration log - technical information.

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
        НСтр("ru = 'Не удалось провести документ ""%1"".
        |Попробуйте повторить операцию. Если ошибка повторяется, обратитесь к администратору.
        |
        |Техническая информация: %2'"),
        ДокументОбъект,
        КраткоеПредставлениеОшибки(ИнформацияОбОшибке()));

    ВызватьИсключение ТекстДляПользователя;
КонецПопытки;
```

---

## Rule 9: Proper exception propagation - `ВызватьИсключение`

| Method | When | Why |
|--------|-------|--------|
| `ВызватьИсключение;` | In intermediate code (object module, common module) | Preserves the original stack |
| `ВызватьИсключение "Text";` | At the user boundary (form) | Replaces the technical stack with a clear message |

```bsl
// Intermediate layer - rethrow the original
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

## Rule 10: Error handling for bulk operations

An error in one document must not stop the processing of the others. Each transaction is item-by-item.

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

## Rule 11: Unified lock order - deadlock prevention

When two sessions lock data in different orders - deadlock. The DBMS rolls back one of the transactions.

```bsl
// Always lock resources in a fixed order (by reference)
МассивСсылок = ОбщегоНазначенияКлиентСервер.СвернутьМассив(МассивДокументов);
МассивСсылок.СортироватьПоЗначению();

Для Каждого Ссылка Из МассивСсылок Цикл
    ЗаблокироватьДанныеДляРедактирования(Ссылка);
КонецЦикла;
```

### Handling a lock error

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
            НСтр("ru = 'Не удалось заблокировать ""%1"". Данные редактируются другим пользователем.'"),
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

## Rule 12: Try/Except for external calls

Calls to external systems are unreliable. **Always** wrap them in `Try/Except`.

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
