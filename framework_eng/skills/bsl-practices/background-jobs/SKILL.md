---
name: background-jobs
description: "For designing and debugging 1C background jobs"
skills:
  - architect
  - developer-code
---

# Background and Scheduled Jobs

**Key principle:** A background job can be interrupted, restarted, or run again at any moment. The job code **must** tolerate this without data loss or duplicated work.

---

## When to Use

| Trigger | Action |
|---------|----------|
| A scheduled or background job is being designed | Define the contract: parameters, user, transaction, idempotency, locking, timeout |
| The job hangs, does not finish, duplicates work | Diagnose via the Registration Log: find the first failure, check active background jobs and stale locks |
| There is an error in the job and retry logic is needed | Separate retryable and permanent errors, implement backoff |
| The job processes a large volume of data | Apply checkpointing and batch processing with intermediate commits |
| Parallel execution of multiple instances is possible | Implement a mutex through `БлокировкаДанных` or a flag constant |

---

## Scenario 1: Designing an Idempotent Job

**Context:** A scheduled job needs to be created so that it can be safely restarted and does not duplicate work.

**Steps:**

1. Define the idempotent key: what uniquely identifies a unit of work (document, period, parameter hash).
2. Store the processing status in the information base (catalog, information register, object attribute).
3. Read the status **inside the transaction** with locking before starting the work.
4. Update the status to "In progress" with a start timestamp - protection against parallel acquisition.
5. On completion, set "Processed".

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
2. If the lock is not obtained, finish with a warning in the Registration Log (not an error).
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

**Context:** The job processes thousands of objects. It needs to persist progress so that a restart does not begin from scratch.

**Key rules:**
- A batch = one transaction. Do not open a transaction for the entire volume.
- Save the checkpoint in the same transaction as the batch's useful work.
- On restart, read the checkpoint and continue from it.

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

## Scenario 4: Retry Policy - retryable vs permanent errors

**Context:** The job calls an external service or works with resources that may be temporarily unavailable.

**Error classification:**

| Type | Examples | Action |
|-----|---------|----------|
| Retryable (temporary) | Network timeout, service unavailable (503), lock acquisition | Retry with backoff, write `Warning` |
| Permanent | Invalid data, business rule violation, 404/400 | Do not retry, write `Error`, move the task to the `Rejected` status |

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

## Scenario 5: Running and Diagnostics via v8-runner

**Manual job run** (for debugging and testing):

```bash
# Run a specific scheduled job through v8-runner
v8 run --ib <путь_к_ИБ> --execute "РегламентныеЗаданияСервер.ВыполнитьЗадание(<ИмяЗадания>)"
```

**Diagnose hanging jobs via the Registration Log** (event-log-analysis):

```bash
# View background job errors for the last 2 hours
v8 run --ib <путь_к_ИБ> --event-log --filter "ФоновоеЗадание" --level Error --hours 2
```

**Check active background jobs in the Registration Log:**

Look for events named `background job`. A hanging job = a `Start` event without a matching `Finish` and without `Error` - this is a stale-lock candidate.

---

## Job Design Rules

### Forbidden Patterns

| Anti-pattern | Consequence |
|-------------|-------------|
| HTTP/external call inside a transaction | A 30-second timeout = a 30-second lock on the entire information base |
| One transaction for the entire volume | Restart = rollback of all work |
| No idempotency | Data duplication on rerun |
| Silent error swallowing | Data is lost, and there is no trace in the Registration Log |
| Infinite retry without a limit | The job will block the queue forever |
| Stale lock without TTL | The job does not start after a crash, the lock is not released |

### Required Registration Log Entry Structure

Each job must write to the Registration Log at start and completion:

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

- [ ] The job can be safely restarted without duplicated work (idempotent).
- [ ] Long-running work is split into batches with intermediate checkpoints and transaction commits.
- [ ] The lock protects against parallel execution; the lock has a TTL (protection against hanging).
- [ ] The Registration Log contains: start, finish, job parameters, result (how many processed/errors), duration.
- [ ] Logs do not contain secrets (passwords, tokens, personal data).
- [ ] Errors are divided into retryable (retry) and permanent (do not retry).
- [ ] External calls (HTTP, COM, WS) are performed **outside** the transaction.
- [ ] The transaction is minimal in time: data preparation is outside the transaction, only the write is inside.

## Related Resources

- [error-handling](../error-handling/SKILL.md) — transactions, `БлокировкаДанных`, the canonical `НачатьТранзакцию/ЗафиксироватьТранзакцию/ОтменитьТранзакцию` pattern
- [v8-runner references/testing](../../tool-usage/v8-runner/references/testing.md) — manual job execution and result verification
- [vanessa-diagnostics](../../tool-usage/vanessa/vanessa-diagnostics/SKILL.md) — diagnostics via logs and the Registration Log

---
depends_on:
  - bsl-practices/error-handling
---
