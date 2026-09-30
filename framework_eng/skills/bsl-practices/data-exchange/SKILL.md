---
name: data-exchange
description: "For 1C exchanges: РИБ, КД, EnterpriseData, БСП"
---

# 1C Data Exchange

**Key principle:** Data exchange is a distributed system. Each packet must be idempotent (reloading it does not corrupt data), each conflict must be explicitly resolved, and each error must be logged with a link to the node and message number.

---

## Rule 1: Exchange plan architecture — choosing the model

Before implementation, it is necessary to define the exchange model. The choice affects all subsequent code.

| Model | When to use | Mechanism |
|--------|-------------------|----------|
| RIB (distributed information base) | Full copy of the configuration on nodes, all data is transferred | `ПланыОбмена`, `ЗаписьСообщенияОбмена`/`ЧтениеСообщенияОбмена`, `СериализаторXDTO` |
| Selective exchange (БСП) | Rule-based exchange, object filtering, different configurations | БСП "Обмен данными" subsystem, EnterpriseData format |
| КД 2.0 | Complex conversion rules between different configurations | "Конвертация данных" processing, XML rules |
| КД 3.0 / EDT | Modern projects, rules in BSL, EnterpriseData support | "Конвертация данных 3" configuration |

### Exchange plan structure

```bsl
// Главный узел — это текущая ИБ
// Подчинённые узлы — внешние базы данных
УзелГлавный = ПланыОбмена.МойОбмен.ГлавныйУзел();
Если УзелГлавный = Неопределено Тогда
    // Это главный узел (центральная база)
КонецЕсли;

// Создание нового узла обмена
НовыйУзел = ПланыОбмена.МойОбмен.СоздатьУзел();
НовыйУзел.Код = "ФИЛИАЛ_001";
НовыйУзел.Наименование = "Филиал Москва";
НовыйУзел.Записать();
```

---

## Rule 2: Change registration — RegisterChanges

Change registration is the main mechanism for tracking what needs to be sent to a node. Incorrect registration is the main source of errors: either data does not get sent, or extra data does.

### Explicit registration

```bsl
// Register a specific object for a specific node
ПланыОбмена.ЗарегистрироватьИзменения(УзелОбмена, ОбъектСсылка);

// Register a set of objects (for example, during initial synchronization)
МассивОбъектов = Новый Массив;
МассивОбъектов.Добавить(НоменклатураСсылка1);
МассивОбъектов.Добавить(НоменклатураСсылка2);
ПланыОбмена.ЗарегистрироватьИзменения(УзелОбмена, МассивОбъектов);
```

### Event subscriptions for automatic registration

```bsl
// In the ПриЗаписи subscription handler — register changes explicitly
Процедура НоменклатураПриЗаписи(Источник, Отказ, РежимЗаписи, РежимПроведения)

    Если Источник.ОбменДанными.Загрузка Тогда
        Возврат; // Do not register changes when loading from exchange
    КонецЕсли;

    Для Каждого УзелОбмена Из ПланыОбмена.МойОбмен.Выбрать() Цикл
        Если УзелОбмена.Ссылка <> ПланыОбмена.МойОбмен.ЭтотУзел() Тогда
            ПланыОбмена.ЗарегистрироватьИзменения(УзелОбмена.Ссылка, Источник.Ссылка);
        КонецЕсли;
    КонецЦикла;

КонецПроцедуры
```

### Critical rule: check the Загрузка flag

```bsl
// REQUIRED at the beginning of every object handler
Если Источник.ОбменДанными.Загрузка Тогда
    Возврат; // Skip all checks and additional logic during loading
КонецЕсли;
```

Without this check: when loading data from an external database, the business rules of the current database will be triggered -> the data will be corrupted or the load will end with an error.

---

## Rule 3: Data export - forming the message package

### Canonical export pattern (РИБ)

```bsl
Функция СформироватьСообщениеОбмена(УзелОбмена) Экспорт

    ЗаписьСообщения = ПланыОбмена.СоздатьЗаписьСообщения();

    ЗаписьXML = Новый ЗаписьXML;
    ЗаписьXML.УстановитьСтроку();

    Попытка
        // The recipient is set in НачатьЗапись(); the sent number is assigned automatically
        ЗаписьСообщения.НачатьЗапись(ЗаписьXML, УзелОбмена);

        // Export changes registered for this recipient
        ВыборкаИзменений = ПланыОбмена.ВыбратьИзменения(
            ЗаписьСообщения.Получатель, ЗаписьСообщения.НомерСообщения);
        Пока ВыборкаИзменений.Следующий() Цикл
            СериализаторXDTO.ЗаписатьXML(ЗаписьXML, ВыборкаИзменений.Получить());
        КонецЦикла;

        ЗаписьСообщения.ЗакончитьЗапись();

    Исключение
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'ОбменДанными.Выгрузка'"),
            УровеньЖурналаРегистрации.Ошибка,
            Метаданные.ПланыОбмена.МойОбмен,
            УзелОбмена,
            ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
        ВызватьИсключение;
    КонецПопытки;

    Возврат ЗаписьXML.Закрыть();

КонецФункции
```

### Message Number and Acknowledgement

```bsl
// ВАЖНО: у ПланыОбмена НЕТ методов УстановитьНомерОтправленного/УстановитьНомерПринятого.
// Номер отправленного увеличивается автоматически при ЗаписьСообщения.НачатьЗапись().
// Номера принятого/отправленного — это реквизиты узла, правятся через объект узла.

// На стороне отправителя — после подтверждения доставки снимаем регистрацию изменений
// вплоть до подтверждённого номера:
ПланыОбмена.УдалитьРегистрациюИзменений(УзелОбмена, НомерСообщения);

// На стороне получателя — фиксируем номер принятого через объект узла:
ОбъектУзла = УзелОбмена.ПолучитьОбъект();
ОбъектУзла.НомерПринятого = НомерСообщения;
ОбъектУзла.Записать();
```

---

## Rule 4: Data Loading - Idempotency and Duplicate Protection

Packet idempotency means that reloading the same message does not change the result. This is critical when network failures occur and when manual reruns are performed.

### Canonical Loading Pattern (РИБ)

The transactional skeleton is the `error-handling` canon (Rule 2); the specific point here is the idempotent check of the message number BEFORE rollback/commit.

```bsl
Процедура ЗагрузитьСообщениеОбмена(ТекстСообщения) Экспорт

    ЧтениеXML = Новый ЧтениеXML;
    ЧтениеXML.УстановитьСтроку(ТекстСообщения);

    ЧтениеСообщения = ПланыОбмена.СоздатьЧтениеСообщения();

    НачатьТранзакцию();
    Попытка

        ЧтениеСообщения.НачатьЧтение(ЧтениеXML);

        // Проверка: не загружать уже принятое сообщение (идемпотентность)
        Если ЧтениеСообщения.НомерСообщения <= ЧтениеСообщения.Отправитель.НомерПринятого Тогда
            ЗаписьЖурналаРегистрации(
                НСтр("ru = 'ОбменДанными.Загрузка'"),
                УровеньЖурналаРегистрации.Предупреждение,,,
                СтрШаблон(НСтр("ru = 'Сообщение №%1 от узла %2 уже было принято. Пропускаем.'"),
                    ЧтениеСообщения.НомерСообщения, ЧтениеСообщения.Отправитель));
            ОтменитьТранзакцию();
            Возврат;
        КонецЕсли;

        // Загрузка объектов
        Пока СериализаторXDTO.ВозможностьЧтенияXML(ЧтениеXML) Цикл
            Данные = СериализаторXDTO.ПрочитатьXML(ЧтениеXML);
            Данные.ОбменДанными.Загрузка = Истина;
            Данные.Записать();
        КонецЦикла;

        ЧтениеСообщения.ЗакончитьЧтение();

        // Фиксируем номер принятого сообщения через объект узла-отправителя
        ОбъектУзла = ЧтениеСообщения.Отправитель.ПолучитьОбъект();
        ОбъектУзла.НомерПринятого = ЧтениеСообщения.НомерСообщения;
        ОбъектУзла.Записать();

        ЗафиксироватьТранзакцию();

    Исключение
        ОтменитьТранзакцию();
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'ОбменДанными.Загрузка'"),
            УровеньЖурналаРегистрации.Ошибка,
            Метаданные.ПланыОбмена.МойОбмен,
            ЧтениеСообщения.Отправитель,
            СтрШаблон(НСтр("ru = 'Ошибка загрузки сообщения №%1.
            |%2'"), ЧтениеСообщения.НомерСообщения,
                ПодробноеПредставлениеОшибки(ИнформацияОбОшибке())));
        ВызватьИсключение;
    КонецПопытки;

КонецПроцедуры
```

### Ensuring Idempotency When Loading Catalogs

```bsl
// При загрузке справочников — искать по внешнему идентификатору, не создавать дубли
Функция НайтиИлиСоздатьНоменклатуру(ВнешнийКод, Наименование)

    // Поиск по внешнему коду (синхронизационному ключу)
    СуществующийЭлемент = Справочники.Номенклатура.НайтиПоКоду(ВнешнийКод);
    Если СуществующийЭлемент <> Справочники.Номенклатура.ПустаяСсылка() Тогда
        Возврат СуществующийЭлемент;
    КонецЕсли;

    // Создать новый только если не нашли
    НовыйЭлемент = Справочники.Номенклатура.СоздатьЭлемент();
    НовыйЭлемент.Код = ВнешнийКод;
    НовыйЭлемент.Наименование = Наименование;
    НовыйЭлемент.ОбменДанными.Загрузка = Истина;
    НовыйЭлемент.Записать();
    Возврат НовыйЭлемент.Ссылка;

КонецФункции
```

---

## Rule 5: Conflict Resolution

A conflict occurs when the same object is changed in two nodes at the same time. The resolution strategy must be documented and implemented explicitly - a "silent" last-write-wins approach is unacceptable in most business scenarios.

### Resolution Strategies

| Strategy | When to use | Implementation |
|-----------|----------------|------------|
| Main node wins | The main database is the source of truth | Reject changes from subordinate nodes on conflict |
| Last change wins | Non-critical data (settings, descriptions) | `МоментВремени()` - the newer one wins |
| Business rule wins | State machines, priorities | Explicit resolution function |
| Manual resolution | Critical data, cannot be automated | Record in a conflict register |

### Pattern: resolution by node priority

```bsl
// В обработчике ОбменДанными.ОбработкаКонфликта (БСП)
// или в ручной логике загрузки:

Функция РазрешитьКонфликт(ДанныеПриемника, ДанныеИсточника, УзелОбмена) Экспорт

    // Если это главный узел — наши данные побеждают всегда
    Если ПланыОбмена.МойОбмен.ГлавныйУзел() = Неопределено Тогда
        // Мы главный узел
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'ОбменДанными.Конфликт'"),
            УровеньЖурналаРегистрации.Предупреждение,
            ДанныеПриемника.Метаданные(),
            ДанныеПриемника.Ссылка,
            СтрШаблон(НСтр("ru = 'Конфликт с узлом %1. Победа главного узла.'"), УзелОбмена));
        Возврат Ложь; // Не загружать данные источника
    КонецЕсли;

    // Разрешение по дате последнего изменения
    Если ДанныеИсточника.МоментВремени() > ДанныеПриемника.МоментВремени() Тогда
        Возврат Истина; // Загрузить — источник новее
    Иначе
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'ОбменДанными.Конфликт'"),
            УровеньЖурналаРегистрации.Предупреждение,
            ДанныеПриемника.Метаданные(),
            ДанныеПриемника.Ссылка,
            НСтр("ru = 'Конфликт. Победа приёмника — локальная версия новее.'"));
        Возврат Ложь; // Не загружать — наши данные новее
    КонецЕсли;

КонецФункции
```

---

## Rule 6: Exchange via the БСП subsystem “Data Exchange”

БСП provides ready-made infrastructure: node settings, exchange rules, transport (file, FTP, e-mail, WS), message queue, conflict register.

### Key objects of the subsystem

```bsl
// Основной модуль подсистемы
// ОбменДаннымиСервер — серверный модуль
// ОбменДаннымиКлиент — клиентский модуль
// ОбменДанными — общий модуль (клиент-сервер)

// Регистрация объекта для обмена через БСП
ОбменДаннымиСервер.ЗарегистрироватьОбъектДляОбмена(УзелОбмена, ОбъектСсылка);

// Запуск синхронизации через БСП
НастройкиОбмена = ОбменДаннымиСервер.НастройкиОбменаДляУзла(УзелОбмена);
ОбменДаннымиСервер.СинхронизироватьДанные(НастройкиОбмена);
```

### EnterpriseData format

EnterpriseData is a standardized XML format for exchange between 1С configurations. It is used in modern БСП exchange plans.

```bsl
// Structure of the EnterpriseData package
// <Файл>
//   <ТипФайла>ОбменДанными</ТипФайла>
//   <ФорматВерсии>1.0</ФорматВерсии>
//   <Отправитель>
//     <Наименование>УТ 11.5</Наименование>
//     <Идентификатор>GUID_узла</Идентификатор>
//   </Отправитель>
//   <Данные>
//     <Объект ТипОбъекта="Справочник.Номенклатура">
//       ...поля объекта...
//     </Объект>
//   </Данные>
// </Файл>

// Reading an EnterpriseData package
ЧтениеXML = Новый ЧтениеXML;
ЧтениеXML.ОткрытьФайл(ПутьКФайлу);

Десериализатор = Новый СериализаторXDTO(ФабрикаXDTO);
Пока ЧтениеXML.Прочитать() Цикл
    Если ЧтениеXML.ТипУзла = ТипУзлаXML.НачалоЭлемента
            И ЧтениеXML.ЛокальноеИмя = "Объект" Тогда
        ОбъектXDTO = Десериализатор.ПрочитатьXML(ЧтениеXML);
        ОбработатьОбъектXDTO(ОбъектXDTO);
    КонецЕсли;
КонецЦикла;
```

---

## Rule 7: КД 2.0 — conversion rules

КД 2.0 is used for exchange between different configurations using a conversion rules file (XML).

### Loading pattern via Data Conversion 2.0

```bsl
// Загрузка данных с использованием обработки «Конвертация данных»
Процедура ЗагрузитьДанныеПоПравилам(ПутьКФайлу, ПутьКПравилам) Экспорт

    ОбработкаЗагрузки = ВнешниеОбработки.Создать(ПутьКПравилам);

    ОбработкаЗагрузки.ИмяФайлаОбмена = ПутьКФайлу;
    ОбработкаЗагрузки.ПутьКПравиламОбмена = ПутьКПравилам;
    ОбработкаЗагрузки.ЗагрузитьПравилаОбмена();

    Попытка
        ОбработкаЗагрузки.ВыполнитьЗагрузку();
    Исключение
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'ОбменДанными.КД2.Загрузка'"),
            УровеньЖурналаРегистрации.Ошибка,,,
            ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
        ВызватьИсключение;
    КонецПопытки;

КонецПроцедуры
```

### Differences between Data Conversion 2.0 and Data Conversion 3.0

| Aspect | Data Conversion 2.0 | Data Conversion 3.0 |
|--------|---------------------|---------------------|
| Rules | XML file | BSL modules in the Data Conversion 3 configuration |
| EDT support | No | Yes |
| Format | Proprietary XML | EnterpriseData (optional) |
| Handlers | In rules (code strings) | Full BSL, debugging |
| Recommendation | Existing projects | New projects |

---

## Rule 8: Diagnostics and monitoring of data exchange

### Registration log event table

When diagnosing data exchange, look for these events:

| Registration log event | Meaning |
|-----------|-------------|
| `ОбменДанными` | General events of the БСП subsystem |
| `ОбменДанными.Выгрузка` | Packet generation |
| `ОбменДанными.Загрузка` | Packet reception and processing |
| `ОбменДанными.Конфликт` | Recorded conflicts |
| `ОбменДанными.Транспорт` | Delivery errors |

### Checking the state of exchange nodes

```bsl
// Диагностический запрос — состояние узлов и счётчиков сообщений
Запрос = Новый Запрос;
Запрос.Текст =
"ВЫБРАТЬ
|   Узлы.Ссылка КАК Узел,
|   Узлы.Код,
|   Узлы.НомерОтправленного,
|   Узлы.НомерПринятого,
|   КОЛИЧЕСТВО(Изменения.Узел) КАК КоличествоИзменений
|ИЗ
|   ПланОбмена.МойОбмен КАК Узлы
|       ЛЕВОЕ СОЕДИНЕНИЕ РегистрСведений.ИзменениеПоПланамОбмена КАК Изменения
|       ПО Изменения.Узел = Узлы.Ссылка
|ГДЕ
|   Узлы.Ссылка <> Узлы.ЭтотУзел
|СГРУППИРОВАТЬ ПО
|   Узлы.Ссылка, Узлы.Код, Узлы.НомерОтправленного, Узлы.НомерПринятого";

РезультатЗапроса = Запрос.Выполнить();

### Manual Re-registration After Failures

```bsl
// Сброс счётчика и перерегистрация всех данных для узла
// ВНИМАНИЕ: использовать только при полной ресинхронизации
Процедура ПеререгистрироватьВсеДанные(УзелОбмена) Экспорт

    // Сбросить счётчики
    УзелОбменаОбъект = УзелОбмена.ПолучитьОбъект();
    УзелОбменаОбъект.НомерОтправленного = 0;
    УзелОбменаОбъект.НомерПринятого = 0;
    УзелОбменаОбъект.Записать();

    // Удалить накопленные изменения
    ПланыОбмена.УдалитьРегистрациюИзменений(УзелОбмена, 0);

    // Зарегистрировать заново
    // (зависит от состава плана обмена — перебрать все типы объектов)

    ЗаписьЖурналаРегистрации(
        НСтр("ru = 'ОбменДанными.Перерегистрация'"),
        УровеньЖурналаРегистрации.Предупреждение,
        Метаданные.ПланыОбмена.МойОбмен,
        УзелОбмена,
        НСтр("ru = 'Выполнена полная перерегистрация данных для узла.'"));

КонецПроцедуры
```

---

## Typical Mistakes

| Error | Consequence | How to Avoid |
|--------|------------|--------------|
| No check for `ОбменДанными.Загрузка` | Business logic runs during loading, data gets corrupted, or exchange enters a loop | In every ПриЗаписи handler, in the first lines |
| Committing `НомерОтправленного` before delivery is confirmed | Changes are marked as sent, but they never reach the node; they will not be unloaded in the next session | Commit the number only after receiver confirmation |
| Loading without checking idempotency | Duplicate documents, duplicate register movements | Check `НомерСообщения <= НомерПринятого` before loading |
| Conflict without logging | Data is silently overwritten, user changes are lost | Always write to the ЖР on conflict with Warning level |
| Registering changes inside the loading handler | Infinite exchange: A→B→A→B | The `Загрузка = Истина` flag disables registration |
| Long transaction when loading a large batch | Locks, timeouts, rollback of the entire batch | Load object by object with separate transactions or batches |
| Not deleting change register entries after unloading | Accumulation of millions of entries, performance degradation | `ПланыОбмена.УдалитьРегистрациюИзменений` after confirmation |

---

## Related resources

- [error-handling](../error-handling/SKILL.md) — canonical transaction pattern, required for import/export
- [background-jobs](../background-jobs/SKILL.md) — data exchange is often performed in background jobs

---
depends_on:
  - bsl-practices/error-handling
  - bsl-practices/background-jobs
---
