---
name: data-exchange
description: "1C data exchange: exchange plans, RIB, change registration, EnterpriseData format, KD 2.0/3.0. Use when you need to implement or diagnose data exchange - `ПланыОбмена`, `ЗарегистрироватьИзменения`, package import/export, data conflicts, RIB nodes, the БСП \"Data exchange\" subsystem."
---

# 1C Data Exchange

**Key principle:** Data exchange is a distributed system. Every package must be idempotent (reloading it does not corrupt data), every conflict must be explicitly resolved, and every error must be logged with a link to the node and message number.

---

## Rule 1: Exchange plan architecture - model selection

Before implementation, you must define the exchange model. The choice affects all subsequent code.

| Model | When to use | Mechanism |
|--------|-------------|----------|
| RIB (distributed infobase) | A full copy of the configuration on each node, all data is transferred | `ПланыОбмена`, XML serialization, `ОбменДаннымиXML` |
| Selective exchange (БСП) | Rule-based exchange, object filtering, different configurations | БСП subsystem "Data exchange", EnterpriseData format |
| KD 2.0 | Complex conversion rules between different configurations | "Data Conversion" processing, XML rules |
| KD 3.0 / EDT | Modern projects, rules in BSL, EnterpriseData support | "Data Conversion 3" configuration |

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

## Rule 2: Change registration - `ЗарегистрироватьИзменения`

Change registration is the primary mechanism for tracking what needs to be sent to a node. Incorrect registration is the main source of errors: either data does not leave, or unnecessary data is sent.

### Explicit registration

```bsl
// Зарегистрировать конкретный объект для конкретного узла
ПланыОбмена.ЗарегистрироватьИзменения(УзелОбмена, ОбъектСсылка);

// Зарегистрировать набор объектов (например, при первичной синхронизации)
МассивОбъектов = Новый Массив;
МассивОбъектов.Добавить(НоменклатураСсылка1);
МассивОбъектов.Добавить(НоменклатураСсылка2);
ПланыОбмена.ЗарегистрироватьИзменения(УзелОбмена, МассивОбъектов);
```

### Event subscriptions for automatic registration

```bsl
// В обработчике подписки ПриЗаписи — регистрировать изменения явно
Процедура НоменклатураПриЗаписи(Источник, Отказ, РежимЗаписи, РежимПроведения)

    Если Источник.ОбменДанными.Загрузка Тогда
        Возврат; // Не регистрировать изменения при загрузке из обмена
    КонецЕсли;

    Для Каждого УзелОбмена Из ПланыОбмена.МойОбмен.Выбрать() Цикл
        Если УзелОбмена.Ссылка <> ПланыОбмена.МойОбмен.ЭтотУзел() Тогда
            ПланыОбмена.ЗарегистрироватьИзменения(УзелОбмена.Ссылка, Источник.Ссылка);
        КонецЕсли;
    КонецЦикла;

КонецПроцедуры
```

### Critical rule: check the `Загрузка` flag

```bsl
// ОБЯЗАТЕЛЬНО в начале каждого обработчика объекта
Если Источник.ОбменДанными.Загрузка Тогда
    Возврат; // Пропустить все проверки и дополнительную логику при загрузке
КонецЕсли;
```

Without this check: when data is loaded from an external database, the current database's business rules will run -> the data will be corrupted or the load will fail with an error.

---

## Rule 3: Data export - building the message package

### Canonical export pattern (RIB)

```bsl
Функция СформироватьСообщениеОбмена(УзелОбмена) Экспорт

    СообщениеОбмена = ПланыОбмена.СоздатьСообщениеОбмена();
    СообщениеОбмена.Отправитель = ПланыОбмена.МойОбмен.ЭтотУзел();
    СообщениеОбмена.Получатель = УзелОбмена;

    ЗаписьXML = Новый ЗаписьXML;
    ЗаписьXML.УстановитьСтроку();

    Попытка
        ОбменДаннымиXML.НачатьЗаписьСообщения(ЗаписьXML, СообщениеОбмена);

        // Выгрузка изменений
        ВыборкаИзменений = ПланыОбмена.Выбрать(УзелОбмена, ТипПланаОбмена.ТолькоИзменения);
        Пока ВыборкаИзменений.Следующий() Цикл
            ОбменДаннымиXML.ЗаписатьИзменения(ЗаписьXML, ВыборкаИзменений.ПолучитьОбъект());
        КонецЦикла;

        ОбменДаннымиXML.ЗакончитьЗаписьСообщения(ЗаписьXML);

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

### Message number and confirmation

```bsl
// ВАЖНО: номер сообщения фиксируется только после успешной доставки
// Не вызывать УстановитьНомерОтправленного до подтверждения получения

// На стороне отправителя — после подтверждения доставки:
ПланыОбмена.УстановитьНомерОтправленного(УзелОбмена, НомерСообщения);

// На стороне получателя — после успешной загрузки:
ПланыОбмена.УстановитьНомерПринятого(УзелОбмена, НомерСообщения);
```

---

## Rule 4: Data loading - idempotency and duplicate protection

Package idempotency means: reloading the same message does not change the result. This is critical when network failures occur and when manually rerunning the process.

### Canonical loading pattern (RIB)

```bsl
Процедура ЗагрузитьСообщениеОбмена(ТекстСообщения) Экспорт

    ЧтениеXML = Новый ЧтениеXML;
    ЧтениеXML.УстановитьСтроку(ТекстСообщения);

    СообщениеОбмена = ПланыОбмена.СоздатьСообщениеОбмена();

    НачатьТранзакцию();
    Попытка

        ОбменДаннымиXML.НачатьЧтениеСообщения(ЧтениеXML, СообщениеОбмена);

        // Проверка: не загружать уже принятое сообщение (идемпотентность)
        Если СообщениеОбмена.НомерСообщения <= ПланыОбмена.МойОбмен
                .НайтиПоКоду(СообщениеОбмена.Отправитель.Код).НомерПринятого Тогда
            ЗаписьЖурналаРегистрации(
                НСтр("ru = 'ОбменДанными.Загрузка'"),
                УровеньЖурналаРегистрации.Предупреждение,,,
                СтрШаблон(НСтр("ru = 'Сообщение №%1 от узла %2 уже было принято. Пропускаем.'"),
                    СообщениеОбмена.НомерСообщения, СообщениеОбмена.Отправитель));
            ОтменитьТранзакцию();
            Возврат;
        КонецЕсли;

        // Загрузка объектов
        Пока ОбменДаннымиXML.ЧитатьИзменения(ЧтениеXML) Цикл
            Данные = ОбменДаннымиXML.ПрочитатьИзменения(ЧтениеXML);
            Данные.ОбменДанными.Загрузка = Истина;
            Данные.Записать();
        КонецЦикла;

        ОбменДаннымиXML.ЗакончитьЧтениеСообщения(ЧтениеXML);

        // Фиксируем номер принятого сообщения
        ПланыОбмена.УстановитьНомерПринятого(
            СообщениеОбмена.Отправитель, СообщениеОбмена.НомерСообщения);

        ЗафиксироватьТранзакцию();

    Исключение
        ОтменитьТранзакцию();
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'ОбменДанными.Загрузка'"),
            УровеньЖурналаРегистрации.Ошибка,
            Метаданные.ПланыОбмена.МойОбмен,
            СообщениеОбмена.Отправитель,
            СтрШаблон(НСтр("ru = 'Ошибка загрузки сообщения №%1.
            |%2'"), СообщениеОбмена.НомерСообщения,
                ПодробноеПредставлениеОшибки(ИнформацияОбОшибке())));
        ВызватьИсключение;
    КонецПопытки;

КонецПроцедуры
```

### Ensuring idempotency when loading catalogs

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

## Rule 5: Conflict resolution

A conflict occurs when the same object is changed in two nodes at the same time. The resolution strategy must be documented and implemented explicitly. A "silent" last-write-wins policy is unacceptable in most business scenarios.

### Resolution strategies

| Strategy | When to use | Implementation |
|----------|-------------|----------------|
| Master node wins | The main database is the source of truth | Reject changes from subordinate nodes during a conflict |
| Last change wins | Non-critical data (settings, descriptions) | `МоментВремени()` - the newer one wins |
| Business-rule-based win | Status machines, priorities | Explicit resolution function |
| Manual resolution | Critical data, cannot be automated | Record it in a conflict log |

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

## Rule 6: Exchange through the БСП "Data exchange" subsystem

БСП provides ready-made infrastructure: node settings, exchange rules, transports (file, FTP, e-mail, WS), message queue, and conflict log.

### Key subsystem objects

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

EnterpriseData is a standardized XML format for exchange between 1C configurations. It is used in modern БСП exchange plans.

```bsl
// Структура пакета EnterpriseData
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

// Чтение пакета EnterpriseData
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

## Rule 7: KD 2.0 - conversion rules

KD 2.0 is used for exchange between different configurations using a conversion rules file (XML).

### Loading pattern through KD 2.0

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

### Differences between KD 2.0 and KD 3.0

| Aspect | KD 2.0 | KD 3.0 |
|--------|--------|--------|
| Rules | XML file | BSL modules in the KD 3 configuration |
| EDT support | No | Yes |
| Format | Proprietary XML | EnterpriseData (optional) |
| Handlers | In rules (code strings) | Full BSL, debugging |
| Recommendation | Existing projects | New projects |

---

## Rule 8: Diagnostics and exchange monitoring

### Registration log event table

When diagnosing exchange, look for these events:

| JR event | Meaning |
|----------|---------|
| `ОбменДанными` | General events of the БСП subsystem |
| `ОбменДанными.Выгрузка` | Package generation |
| `ОбменДанными.Загрузка` | Package reception and processing |
| `ОбменДанными.Конфликт` | Recorded conflicts |
| `ОбменДанными.Транспорт` | Delivery errors |

### Checking exchange node state

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
```

### Manual re-registration after failures

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
    // (depends on the composition of the exchange plan - iterate over all object types)

    ЗаписьЖурналаРегистрации(
        НСтр("ru = 'ОбменДанными.Перерегистрация'"),
        УровеньЖурналаРегистрации.Предупреждение,
        Метаданные.ПланыОбмена.МойОбмен,
        УзелОбмена,
        НСтр("ru = 'Выполнена полная перерегистрация данных для узла.'"));

КонецПроцедуры
```

---

## Typical mistakes

| Error | Consequence | How to avoid |
|-------|-------------|--------------|
| No `ОбменДанными.Загрузка` check | Business logic runs during loading, data gets corrupted or exchange loops | In every `ПриЗаписи` handler in the first lines |
| Fixing `НомерОтправленного` before delivery confirmation | Changes are marked as sent but never reached the node; they will not be exported in the next session | Fix the number only after recipient confirmation |
| Loading without idempotency check | Duplicate documents, duplicate register movements | Check `НомерСообщения <= НомерПринятого` before loading |
| Conflict without logging | Data is silently overwritten, user edits are lost | Always write to the JR on conflict with Warning level |
| Registering changes inside the load handler | Infinite exchange: A->B->A->B | The `Загрузка = Истина` flag disables registration |
| Long transaction while loading a large package | Locks, timeouts, rollback of the entire package | Load per object with separate transactions or batches |
| Not deleting change register entries after export | Millions of records accumulate, performance degrades | `ПланыОбмена.УдалитьРегистрациюИзменений` after confirmation |

---

## Related resources

- [error-handling](../error-handling/SKILL.md) - canonical transaction pattern, mandatory for loading/exporting
- [background-jobs](../background-jobs/SKILL.md) - data exchange is often performed in background jobs

---
depends_on:
  - bsl-practices/error-handling
  - bsl-practices/background-jobs
---
