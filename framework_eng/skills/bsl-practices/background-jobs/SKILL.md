---
name: background-jobs
description: "For designing and debugging 1C background jobs"
skills:
  - architect
  - developer-code
---

# Background and Scheduled Jobs

**Key principle:** A background job can be interrupted, restarted, or started again at any moment. The job code **must** withstand this without data loss or duplicate work.

---

## When to apply

| Trigger | Action |
|---------|----------|
| A scheduled or background job is being designed | Define the contract: parameters, user, transaction, idempotency, locking, timeout |
| A job hangs, does not finish, duplicates work | Diagnose via the Event Log: find the first failure, check active background jobs and stale locks |
| There is an error in the job and retry logic is needed | Separate retryable and permanent errors, implement backoff |
| The job processes a large volume of data | Apply checkpointing and batch processing with intermediate commits |
| Parallel execution of multiple instances is possible | Implement a mutex via `БлокировкаДанных` or a flag constant |

---

> All transaction examples below are the canonical `error-handling` pattern (Rule 2: `НачатьТранзакцию()` before `Попытка`, `ЗафиксироватьТранзакцию()` as the last operation, `ОтменитьТранзакцию()` first in `Исключение`, log after rollback) with task-specific context inside it; the pattern itself is not restated here.

> All transaction examples below are the canonical `error-handling` pattern (Rule 2: `НачатьТранзакцию()` before `Попытка`, `ЗафиксироватьТранзакцию()` as the last operation, `ОтменитьТранзакцию()` first in `Исключение`, log after rollback) with the job-specific context inside it; the pattern itself is not restated here.

## Scenario 1: Designing an idempotent job

**Context:** You need to create a scheduled job that can be safely restarted and does not duplicate work.

**Steps:**

1. Define the idempotent key: what uniquely identifies a unit of work (document, period, parameter hash).
2. Store the processing status in the information base (catalog, information register, object attribute).
3. Read the status **inside a transaction** with locking before starting work.
4. Update the status to "In progress" with a start timestamp - protection against parallel acquisition.
5. Upon completion, set it to "Processed".

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

## Scenario 2: Protection against parallel execution (mutex)

**Context:** The scheduled job must not run in two instances at the same time.

**Steps:**

1. At the start of the job, set an exclusive lock on a special key (a constant or an information register entry).
2. If the lock is not obtained, finish with a warning in the Event Log (not an error).
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
        // canonical Exception block (see scenario 1 / error-handling, Rule 2),
        // event name in the registration log: "РегламентноеЗадание.ИмяЗадания"
        ОтменитьТранзакцию();
        ЗаписьЖурналаРегистрации(...);
        ЗаписьЖурналаРегистрации(...);
        ВызватьИсключение;
    КонецПопытки;

КонецПроцедуры
```

---

## Scenario 3: Checkpointing during large-volume processing

**Context:** The job processes thousands of objects. Progress must be saved so that after a restart it does not begin from zero.

**Key rules:**
- A batch = one transaction. Do not open a transaction for the entire volume.
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

            // canonical Exception block (see scenario 1 / error-handling, Rule 2),
            // event name in the registration log: "ФоновоеЗадание.ПакетнаяОбработка"
            ОтменитьТранзакцию();
            ЗаписьЖурналаРегистрации(...);
            ВызватьИсключение;
        КонецПопытки;

        // Следующий батч
        НачальнаяПозиция = ПолучитьCheckpoint();
        МассивОбъектов   = ПолучитьОбъектыДляОбработки(НачальнаяПозиция, РазмерБатча);

    КонецЦикла;

КонецПроцедуры
```

---

## Scenario 4: Retry policy — retryable vs permanent errors

**Context:** The task calls an external service or works with resources that may be temporarily unavailable.

**Error classification:**

| Type | Examples | Action |
|-----|---------|----------|
| Retryable (temporary) | Network timeout, service unavailable (503), lock contention | Retry with backoff, record `Warning` |
| Permanent | Invalid data, business rule violated, 404/400 | Do not retry, record `Error`, move the task to status `Rejected` |

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

            // Retryable — логируем как предупреждение и продолжаем
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

## Scenario 5: Run and diagnostics via v8-runner

**Run the job manually** (for debugging and testing):

```bash
# Запустить конкретное регламентное задание через v8-runner
v8 run --ib <путь_к_ИБ> --execute "РегламентныеЗаданияСервер.ВыполнитьЗадание(<ИмяЗадания>)"
```

**Diagnose stuck jobs via the event log** (event-log-analysis):

```bash
# Посмотреть ошибки фоновых заданий за последние 2 часа
v8 run --ib <путь_к_ИБ> --event-log --filter "ФоновоеЗадание" --level Error --hours 2
```

**Checking active background jobs in the event log:**

Search for events named `Фоновое задание`. A stuck job is an event “Start” without a matching “Finish” and without “Error” - this is a candidate for a stale lock.

---

## Job design rules

### Forbidden patterns

| Anti-pattern | Consequence |
|-------------|-------------|
| HTTP/external call inside a transaction | 30 sec timeout = 30 sec blocking for the entire database |
| One transaction for the whole volume | Restart = rollback of all work |
| Lack of idempotency | Duplicate data on rerun |
| Silent error swallowing | Data is lost, there are no traces in the event log |
| Infinite retry without a limit | The job will block the queue forever |
| Stale lock without TTL | The job does not start after a crash, the lock is not released |

### Required event log entry structure

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

## Checklist (review checklist)

- [ ] The task can be safely restarted without duplicating work (idempotent).
- [ ] Long-running work is split into batches with intermediate checkpoint and transaction commits.
- [ ] The lock protects against parallel execution; the lock has a TTL (protection against hanging).
- [ ] ЖР contains: start, finish, task parameters, result (how many processed/errors), duration.
- [ ] There are no secrets in the logs (passwords, tokens, personal data).
- [ ] Errors are divided into retryable (retry) and permanent (do not retry).
- [ ] External calls (HTTP, COM, WS) are performed **outside** the transaction.
- [ ] The transaction is minimal in time: data preparation is outside the transaction, only writing is inside.

---

## Related Resources

- [error-handling](../error-handling/SKILL.md) — transactions, `БлокировкаДанных`, the canonical `НачатьТранзакцию/ЗафиксироватьТранзакцию/ОтменитьТранзакцию` pattern
- [v8-runner references/testing](../../tool-usage/v8-runner/references/testing.md) — running tasks manually and checking results
- [vanessa-diagnostics](../../tool-usage/vanessa/vanessa-diagnostics/SKILL.md) — diagnostics from logs and ЖР

---
depends_on:
  - bsl-practices/error-handling
---
