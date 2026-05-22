---
name: background-jobs
description: "Background and scheduled jobs in 1C. Use when you need to design, verify, or fix background jobs: idempotency, retry policy, locks, checkpointing, and splitting retryable/permanent errors."
skills:
  - architect
  - developer-code
---

# Background and Scheduled Jobs

**Key principle:** A background job can be interrupted, restarted, or launched again at any time. The job code **must** tolerate this without data loss or duplicate work.

---

## When to Use

| Trigger | Action |
|---------|----------|
| A scheduled or background job is being designed | Define the contract: parameters, user, transaction, idempotency, locking, timeout |
| The job hangs, does not complete, or duplicates work | Diagnose via the event log: find the first failure, check active background jobs and stale locks |
| The job has an error and needs retry logic | Split retryable and permanent errors, implement backoff |
| The job processes a large volume of data | Apply checkpointing and batch processing with intermediate commits |
| Multiple instances may run in parallel | Implement a mutex via `БлокировкаДанных` or a flag constant |

---

## Scenario 1: Designing an Idempotent Job

**Context:** You need to create a scheduled job that can be safely restarted and does not duplicate work.

**Steps:**

1. Define the idempotent key: what uniquely identifies a unit of work (document, period, parameter hash).
2. Store the processing status in the infobase (catalog, information register, object attribute).
3. Read the status **inside the transaction** with locking before starting work.
4. Update the status to "In Progress" with a start timestamp - protection against parallel acquisition.
5. When finished, set it to "Processed".

```bsl
// Канонический паттерн идемпотентного захвата задачи
Функция ЗахватитьЗадачуДляОбработки(ЗадачаСсылка) Экспорт

    НачатьТранзакцию();
    Попытка

        Блокировка = Новый БлокировкаДанных;
        ЭлементБлокировки = Блокировка.Добавить("РегистрСведений.СостоянияЗадач");
        ЭлементБлокировки.УстановитьЗначение("Задача", ЗадачаСсылка);
        ЭлементБлокировки.Режим = РежимБлокировкиДанных.Исключительный;
        Блокировка.Заблокировать();

        // Читаем актуальный статус после блокировки
        Запрос = Новый Запрос;
        Запрос.Текст =
        "ВЫБРАТЬ
        |   СостоянияЗадач.Статус КАК Статус,
        |   СостоянияЗадач.НачалоОбработки КАК НачалоОбработки
        |ИЗ
        |   РегистрСведений.СостоянияЗадач КАК СостоянияЗадач
        |ГДЕ
        |   СостоянияЗадач.Задача = &Задача";
        Запрос.УстановитьПараметр("Задача", ЗадачаСсылка);
        Результат = Запрос.Выполнить();

        Если НЕ Результат.Пустой() Тогда
            Выборка = Результат.Выбрать();
            Выборка.Следующий();

            // Уже обработано — пропускаем
            Если Выборка.Статус = Перечисления.СтатусыЗадач.Обработано Тогда
                ОтменитьТранзакцию();
                Возврат Ложь;
            КонецЕсли;

            // Кто-то уже взял задачу (и не завис) — пропускаем
            Если Выборка.Статус = Перечисления.СтатусыЗадач.ВОбработке
                И (ТекущаяДата() - Выборка.НачалоОбработки) < 3600 Тогда
                ОтменитьТранзакцию();
                Возврат Ложь;
            КонецЕсли;
        КонецЕсли;

        // Захватываем задачу
        НаборЗаписей = РегистрыСведений.СостоянияЗадач.СоздатьНаборЗаписей();
        НаборЗаписей.Отбор.Задача.Установить(ЗадачаСсылка);
        Запись = НаборЗаписей.Добавить();
        Запись.Задача           = ЗадачаСсылка;
        Запись.Статус           = Перечисления.СтатусыЗадач.ВОбработке;
        Запись.НачалоОбработки  = ТекущаяДата();
        НаборЗаписей.Записать();

        ЗафиксироватьТранзакцию();
        Возврат Истина;

    Исключение
        ОтменитьТранзакцию();
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'ФоновоеЗадание.ЗахватЗадачи'"),
            УровеньЖурналаРегистрации.Ошибка,,,
            ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
        ВызватьИсключение;
    КонецПопытки;

КонецФункции
```

---

## Scenario 2: Protection Against Parallel Execution (mutex)

**Context:** A scheduled job must not run in two instances at the same time.

**Steps:**

1. At the start of the job, set an exclusive lock on a special key (a constant or a register entry).
2. If the lock is not acquired, exit with a warning in the event log, not an error.
3. Release the lock automatically when the transaction ends.

```bsl
Процедура ВыполнитьРегламентноеЗадание() Экспорт

    // Попытка получить эксклюзивный лок
    НачатьТранзакцию();
    Попытка

        Блокировка = Новый БлокировкаДанных;
        ЭлементБлокировки = Блокировка.Добавить("Константа.ФлагЗапускаЗадания");
        ЭлементБлокировки.Режим = РежимБлокировкиДанных.Исключительный;

        Попытка
            Блокировка.Заблокировать();
        Исключение
            // Другой экземпляр уже работает — нормальная ситуация
            ОтменитьТранзакцию();
            ЗаписьЖурналаРегистрации(
                НСтр("ru = 'РегламентноеЗадание.ИмяЗадания'"),
                УровеньЖурналаРегистрации.Предупреждение,,,
                НСтр("ru = 'Пропущен запуск: задание уже выполняется.'"));
            Возврат;
        КонецПопытки;

        // Основная логика задания — выполняется только в одном экземпляре
        ВыполнитьОсновнуюЛогику();

        ЗафиксироватьТранзакцию();

    Исключение
        ОтменитьТранзакцию();
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'РегламентноеЗадание.ИмяЗадания'"),
            УровеньЖурналаРегистрации.Ошибка,,,
            ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
        ВызватьИсключение;
    КонецПопытки;

КонецПроцедуры
```

---

## Scenario 3: Checkpointing When Processing a Large Volume

**Context:** The job processes thousands of objects. You need to save progress so that a restart does not start from zero.

**Key rules:**
- Batch = one transaction. Do not open a transaction for the whole volume.
- Save the checkpoint in the same transaction as the batch's useful work.
- On restart, read the checkpoint and start from it.

```bsl
Процедура ОбработатьОбъектыСCheckpoint(РазмерБатча = 100) Экспорт

    // Читаем checkpoint (откуда продолжать)
    НачальнаяПозиция = ПолучитьCheckpoint();

    МассивОбъектов = ПолучитьОбъектыДляОбработки(НачальнаяПозиция, РазмерБатча);

    Пока МассивОбъектов.Количество() > 0 Цикл

        НачатьТранзакцию();
        Попытка

            Для Каждого Объект Из МассивОбъектов Цикл
                ОбработатьОдинОбъект(Объект);
            КонецЦикла;

            // Checkpoint и данные фиксируются атомарно
            СохранитьCheckpoint(МассивОбъектов[МассивОбъектов.ВГраница()]);

            ЗафиксироватьТранзакцию();

        Исключение
            ОтменитьТранзакцию();
            ЗаписьЖурналаРегистрации(
                НСтр("ru = 'ФоновоеЗадание.ПакетнаяОбработка'"),
                УровеньЖурналаРегистрации.Ошибка,,,
                ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
            ВызватьИсключение;
        КонецПопытки;

        // Следующий батч
        НачальнаяПозиция = ПолучитьCheckpoint();
        МассивОбъектов   = ПолучитьОбъектыДляОбработки(НачальнаяПозиция, РазмерБатча);

    КонецЦикла;

КонецПроцедуры
```

---

## Scenario 4: Retry Policy - Retryable vs Permanent Errors

**Context:** The job calls an external service or works with resources that may be temporarily unavailable.

**Error classification:**

| Type | Examples | Action |
|-----|---------|----------|
| Retryable (temporary) | Network timeout, service unavailable (503), lock acquisition | Retry with backoff, log `Предупреждение` |
| Permanent | Invalid data, business rule violated, 404/400 | Do not retry, log `Ошибка`, move the task to status `Отклонено` |

```bsl
Функция ВыполнитьСRetry(ПараметрыЗадачи) Экспорт

    МаксПопыток     = 3;
    ЗадержкаСекунд  = 30; // для ФоновогоЗадания — через повторный запуск планировщиком

    Для НомерПопытки = 1 По МаксПопыток Цикл
        Попытка

            Результат = ВызватьВнешнийСервис(ПараметрыЗадачи);
            ЗафиксироватьУспех(ПараметрыЗадачи, Результат);
            Возврат Истина;

        Исключение
            ИнфОшибки = ИнформацияОбОшибке();

            Если ЭтоPermanentОшибка(ИнфОшибки) Тогда
                // Повтор бессмысленен
                ЗаписьЖурналаРегистрации(
                    НСтр("ru = 'ФоновоеЗадание.ВнешнийСервис'"),
                    УровеньЖурналаРегистрации.Ошибка,,,
                    СтрШаблон(НСтр("ru = 'Постоянная ошибка (повтор не поможет). %1'"),
                        ПодробноеПредставлениеОшибки(ИнфОшибки)));
                ЗафиксироватьОтклонение(ПараметрыЗадачи, КраткоеПредставлениеОшибки(ИнфОшибки));
                Возврат Ложь;
            КонецЕсли;

            // Retryable — log as a warning and continue
            ЗаписьЖурналаРегистрации(
                НСтр("ru = 'ФоновоеЗадание.ВнешнийСервис'"),
                ?(НомерПопытки < МаксПопыток,
                    УровеньЖурналаРегистрации.Предупреждение,
                    УровеньЖурналаРегистрации.Ошибка),,,
                СтрШаблон(НСтр("ru = 'Попытка %1/%2. %3'"),
                    НомерПопытки, МаксПопыток,
                    ПодробноеПредставлениеОшибки(ИнфОшибки)));

            Если НомерПопытки = МаксПопыток Тогда
                ВызватьИсключение;
            КонецЕсли;

        КонецПопытки;
    КонецЦикла;

    Возврат Ложь;

КонецФункции

Функция ЭтоPermanentОшибка(ИнфОшибки)
    ТекстОшибки = КраткоеПредставлениеОшибки(ИнфОшибки);
    // Признак permanent: HTTP 4xx, бизнес-ошибки, невалидные данные
    Возврат СтрНайти(ТекстОшибки, "400") > 0
        ИЛИ СтрНайти(ТекстОшибки, "404") > 0
        ИЛИ СтрНайти(ТекстОшибки, "422") > 0;
КонецФункции
```

---

## Scenario 5: Manual Launch and Diagnostics via v8-runner

**Manual job launch** (for debugging and testing):

```bash
# Запустить конкретное регламентное задание через v8-runner
v8 run --ib <путь_к_ИБ> --execute "РегламентныеЗаданияСервер.ВыполнитьЗадание(<ИмяЗадания>)"
```

**Diagnosing hung jobs via the event log** (event-log-analysis):

```bash
# Посмотреть ошибки фоновых заданий за последние 2 часа
v8 run --ib <путь_к_ИБ> --event-log --filter "ФоновоеЗадание" --level Error --hours 2
```

**Checking active background jobs in the event log:**

Search for events named `Фоновое задание`. A hung job is an event with a "Start" and no matching "Finish" and no "Error" - that is a candidate for a stale lock.

---

## Job Design Rules

### Forbidden Patterns

| Anti-pattern | Consequence |
|-------------|-------------|
| HTTP/external call inside a transaction | 30-second timeout = 30-second lock on the whole infobase |
| One transaction for the entire volume | Restart = rollback of all work |
| No idempotency | Data duplication on rerun |
| Silently swallowing errors | Data is lost, there are no traces in the event log |
| Infinite retry without a limit | The job will block the queue forever |
| Stale lock without TTL | The job does not start after a crash, the lock is not released |

### Required Event Log Record Structure

Each job must write to the event log at start and finish:

```bsl
// Старт задания
ЗаписьЖурналаРегистрации(
    НСтр("ru = 'РегламентноеЗадание.<ИмяЗадания>.Старт'"),
    УровеньЖурналаРегистрации.Информация,,,
    СтрШаблон(НСтр("ru = 'Задание запущено. Параметры: %1'"),
        <КраткоеОписаниеПараметров>));

// Финиш задания
ЗаписьЖурналаРегистрации(
    НСтр("ru = 'РегламентноеЗадание.<ИмяЗадания>.Финиш'"),
    УровеньЖурналаРегистрации.Информация,,,
    СтрШаблон(НСтр("ru = 'Задание завершено. Обработано: %1, Ошибок: %2, Время: %3 сек'"),
        КоличествоОбработано, КоличествоОшибок, Длительность));
```

---

## Review Checklist

- [ ] The job can be safely restarted without duplicate work (idempotent).
- [ ] Long-running work is split into batches with intermediate checkpoints and transaction commits.
- [ ] A lock protects against parallel execution; the lock has a TTL (protection against hangs).
- [ ] The event log contains: start, finish, job parameters, result summary (how many processed/errors), duration.
- [ ] There are no secrets in logs (passwords, tokens, personal data).
- [ ] Errors are split into retryable (retry) and permanent (do not retry).
- [ ] External calls (HTTP, COM, WS) are performed **outside** the transaction.
- [ ] The transaction is minimal in duration: data preparation is outside the transaction, only writes are inside.

---

## Related Resources

- [error-handling](../error-handling/SKILL.md) — transactions, `БлокировкаДанных`, canonical pattern `НачатьТранзакцию/ЗафиксироватьТранзакцию/ОтменитьТранзакцию`
- [v8-runner references/testing](../../tool-usage/v8-runner/references/testing.md) — manual job launch and result verification
- [vanessa-diagnostics](../../tool-usage/vanessa/vanessa-diagnostics/SKILL.md) — diagnostics via logs and the event log

---
depends_on:
  - bsl-practices/error-handling
---
