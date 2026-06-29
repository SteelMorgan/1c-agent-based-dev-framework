---
name: error-handling
description: "For BSL exceptions, transactions, rollbacks, and locks"
alwaysApply: false
---

# Error handling, transactions, and locking

**Key principle:** In 1С there is no automatic transaction management. The developer manages the start, commit, and rollback **manually**. Every unclosed transaction is a potential catastrophe.

---

## Rule 1: Try/Exception - always log, never swallow

A swallowed exception is the most dangerous antipattern: data is not written, the user does not know about the error, there are no traces in the Event Log, and debugging is impossible.

ITS standard: "In the exception handler, it is mandatory to record error information in the registration log."

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

// Upper level (form) — show to the user
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

ITS standard: "Transactions: rules of use" - `НачатьТранзакцию()` ALWAYS immediately before `Попытка`.

### Canonical pattern (MANDATORY)

```bsl
НачатьТранзакцию();
Попытка

    // 1. Data locking (if needed - see rule 5)
    Блокировка = Новый БлокировкаДанных;
    ЭлементБлокировки = Блокировка.Добавить("Документ.РеализацияТоваровУслуг");
    ЭлементБлокировки.УстановитьЗначение("Ссылка", ДокументСсылка);
    Блокировка.Заблокировать();

    // 2. Reading and modifying data
    ДокументОбъект = ДокументСсылка.ПолучитьОбъект();
    ДокументОбъект.Статус = Перечисления.СтатусыДокументов.Согласован;

    // 3. Writing
    ДокументОбъект.Записать();

    // === Commit - the LAST operation before Исключение ===
    ЗафиксироватьТранзакцию();

Исключение
    // === Rollback - the FIRST operation in the Исключение block ===
    ОтменитьТранзакцию();

    // Logging AFTER rollback (a write to the Event Log inside a rolled-back transaction will be lost!)
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
| `ЗафиксироватьТранзакцию()` is the last statement before `Исключение` | Any operation after commit will not be rolled back if it fails |
| `ОтменитьТранзакцию()` is the first statement in `Исключение` | Logging can also fail; if rollback has not been done yet, it becomes a cascading problem |
| `ЗаписьЖурналаРегистрации()` is AFTER `ОтменитьТранзакцию()` | A write to the Event Log inside a rolled-back transaction will be lost |

### Incorrect variants (platform traps)

```bsl
// BAD: code between НачатьТранзакцию and Попытка
НачатьТранзакцию();
ПодготовитьДанные(); // If there is an error here, the transaction will hang!
Попытка
    // ...
КонецПопытки;

// BAD: ЗаписьЖурнала before ОтменитьТранзакцию
Исключение
    ЗаписьЖурналаРегистрации(...); // May be lost on rollback!
    ОтменитьТранзакцию();
КонецПопытки;

// BAD: code after ЗафиксироватьТранзакцию, but before the end of Попытка
    ЗафиксироватьТранзакцию();
    ОтправитьОповещение(); // Error here - transaction is already committed, but Исключение will execute!
Исключение
    ОтменитьТранзакцию(); // Error! The transaction is already committed!
КонецПопытки;
```

---

## Rule 3: Nested transactions are counter-based

In 1С, a nested `НачатьТранзакцию()` does not create a new transaction, but increments the counter. `ОтменитьТранзакцию()` marks the transaction as "cancelled", and **any subsequent** `ЗафиксироватьТранзакцию()` (even at an outer level) will raise an exception.

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
        ВызватьИсключение; // REQUIRED: rethrow - outer code must know
    КонецПопытки;

КонецПроцедуры
```

### Rule: DO NOT use ТранзакцияАктивна() as a substitute for the correct pattern

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

External HTTP calls inside a transaction are a catastrophe: HTTP timeout = 30 sec = 30 sec lock.

---

## Rule 5: Managed locks - БлокировкаДанных

Without granular locking before read-modify, a race condition occurs: two sessions read the same value, both modify it - one update is lost.

ITS standard: "Managed locks".

### Pattern: lock-read-modify-write

```bsl
НачатьТранзакцию();
Попытка

    // 1. FIRST we lock
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

    // 3. Validate
    Если Выборка.Остаток < ТребуемоеКоличество Тогда
        ВызватьИсключение СтрШаблон(
            НСтр("ru = 'Недостаточно остатков. На складе: %1, требуется: %2.'"),
            Выборка.Остаток, ТребуемоеКоличество);
    КонецЕсли;

    // 4. Write
    // ... writing movements ...

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
  Session A: Reads balance = 10        | Session B: Reads balance = 10
  Session A: 10 >= 8? Yes, write off 8 | Session B: 10 >= 7? Yes, write off 7
  Total: 15 units written off with balance 10 -> negative balance!

With locking:
  Session A: Locks -> Reads 10 -> Writes off 8 -> Commits -> Unlocks
  Session B: Waits for lock -> Reads 2 -> 2 < 7 -> Error (correct!)
```

---

## Rule 6: ЗаблокироватьДанныеДляРедактирования - pessimistic object locking

Prevents lost update: the second user receives the error "Object locked by user X".

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
| `БлокировкаДанных` | DBMS (managed), record level | Balance control, atomic operations |
| `ЗаблокироватьДанныеДляРедактирования` | 1С server (pessimistic), whole object | Preventing lost update |

---

## Rule 7: ЗаписьЖурналаРегистрации - correct logging

### Full record format

```bsl
ЗаписьЖурналаРегистрации(
    ИмяСобытия,         // String - hierarchical name (with dots)
    УровеньСобытия,     // УровеньЖурналаРегистрации - Ошибка/Предупреждение/Информация/Примечание
    МетаданныеОбъекта,  // Metadata object - for type filtering
    Данные,             // Object reference - for navigation from the Event Log
    Комментарий);       // String - detailed description (up to 1024 characters)
```

### Levels

| Level | When |
|---------|-------|
| `Ошибка` | The operation was not completed, data was lost or invalid |
| `Предупреждение` | The operation was completed, but with limitations |
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

## Rule 8: Building error messages for the user

For the user - **what happened** and **what to do**. In the Event Log - technical information.

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

## Rule 9: Correct rethrowing of exceptions - ВызватьИсключение

| Method | When | Why |
|--------|-------|--------|
| `ВызватьИсключение;` | In intermediate code (object module, common module) | Preserves the original stack |
| `ВызватьИсключение "Текст";` | At the user boundary (form) | Replaces the technical stack with a readable message |

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

An error in one document should not stop processing of the others. Each transaction is per item.

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

## Rule 11: Consistent lock order - preventing deadlock

When two sessions lock data in different orders, a deadlock occurs. The DBMS rolls back one of the transactions.

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

## Rule 12: Try/Exception for external calls

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
