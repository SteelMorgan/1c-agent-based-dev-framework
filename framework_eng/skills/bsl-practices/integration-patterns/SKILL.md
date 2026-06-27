---
name: integration-patterns
description: "Use for 1C HTTP/REST/SOAP, auth, retry, webhooks"
---

# 1C Integration Patterns

**Key principle:** The contract is defined before the code. An HTTP service is a thin layer for parsing the request and forming the response; business logic lives in common modules. Secrets belong only in `БезопасноеХранилище`, never in code or constants.

---

## Rule 1: The contract is the first thing to define

Before writing code, fix the contract as a structure or comment:
- transport and URL (method, version in the path, Content-Type/Accept headers);
- authentication (scheme, where the secret is stored);
- request and response bodies (fields, types, required status, null semantics);
- idempotency key (if the operation changes data);
- retry policy and timeout;
- error shape (HTTP codes and error body structure).

Changing the contract without versioning breaks compatibility. Add new fields without removing old ones. Change the semantics of existing fields through a new version (`/v2/…`).

---

## Rule 2: 1C HTTP services are a thin handler

An HTTP service handler should do only three things: parse the request, call the business function, return the response. Do not put business logic directly into the handler.

### Canonical HTTP service pattern

```bsl
// Обработчик метода POST ресурса /orders
Функция ОбработатьПОСТ(Запрос)

    // 1. Разбор тела
    ТелоСтрокой = Запрос.ПолучитьТелоКакСтроку();
    ЧтениеJSON = Новый ЧтениеJSON;
    ЧтениеJSON.УстановитьСтроку(ТелоСтрокой);
    ПараметрыЗаказа = ПрочитатьJSON(ЧтениеJSON, Тип("Структура"));

    // 2. Валидация обязательных полей
    Если НЕ ЗначениеЗаполнено(ПараметрыЗаказа.НомерВнешнего) Тогда
        Возврат ОтветОшибки(400, "VALIDATION_ERROR",
            НСтр("ru = 'Поле НомерВнешнего обязательно'"));
    КонецЕсли;

    // 3. Бизнес-вызов
    Попытка
        РезультатСозданияЗаказа = ИнтеграцияЗаказов.СоздатьЗаказ(ПараметрыЗаказа);
    Исключение
        ЗаписьЖурналаРегистрации(
            НСтр("ru = 'ИнтеграцияЗаказов.СоздатьЗаказ'"),
            УровеньЖурналаРегистрации.Ошибка,,,
            ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
        Возврат ОтветОшибки(500, "INTERNAL_ERROR",
            НСтр("ru = 'Внутренняя ошибка сервера'"));
    КонецПопытки;

    // 4. Формирование ответа
    Возврат ОтветУспеха(201, РезультатСозданияЗаказа);

КонецФункции

// Вспомогательные функции формирования ответа
Функция ОтветОшибки(КодСостояния, КодОшибки, Сообщение)
    Ответ = Новый HTTPСервисОтвет(КодСостояния);
    Ответ.Заголовки.Вставить("Content-Type", "application/json; charset=utf-8");
    СтруктураОшибки = Новый Структура("error, message", КодОшибки, Сообщение);
    ЗаписьJSON = Новый ЗаписьJSON;
    ЗаписьJSON.УстановитьСтроку();
    ЗаписатьJSON(ЗаписьJSON, СтруктураОшибки);
    Ответ.УстановитьТелоИзСтроки(ЗаписьJSON.Закрыть());
    Возврат Ответ;
КонецФункции

Функция ОтветУспеха(КодСостояния, Данные)
    Ответ = Новый HTTPСервисОтвет(КодСостояния);
    Ответ.Заголовки.Вставить("Content-Type", "application/json; charset=utf-8");
    ЗаписьJSON = Новый ЗаписьJSON;
    ЗаписьJSON.УстановитьСтроку();
    ЗаписатьJSON(ЗаписьJSON, Данные);
    Ответ.УстановитьТелоИзСтроки(ЗаписьJSON.Закрыть());
    Возврат Ответ;
КонецФункции
```

---

## Rule 3: Authentication - schemes and secret storage

All secrets (tokens, passwords, keys) are stored exclusively in `БезопасноеХранилище`. Never in configuration constants, session parameters, directory attributes, or module text.

For details on each scheme, see [references/auth-schemes.md](references/auth-schemes.md).

### Basic Auth

```bsl
// Получение учётных данных из безопасного хранилища
УчётныеДанные = БезопасноеХранилище.Прочитать("ИнтеграцияСВнешнимСервисом");

Логин    = УчётныеДанные.Логин;
Пароль   = УчётныеДанные.Пароль;

// Не логируйте: Логин/Пароль, Заголовок Authorization, тело с персданными

Соединение = Новый HTTPСоединение(
    "api.example.com",
    ,               // порт — по умолчанию 443 для HTTPS
    Логин,
    Пароль,
    ,               // прокси
    30,             // таймаут (сек)
    Новый ЗащищённоеСоединение); // OpenSSL / CertificateAuth — см. ниже
```

### Bearer Token (API key or OAuth 2.0 access_token)

```bsl
// Токен из безопасного хранилища
ТокенДоступа = БезопасноеХранилище.Прочитать("ИнтеграцияСВнешнимСервисом").ТокенДоступа;

Запрос = Новый HTTPЗапрос("/api/v1/resource");
Запрос.Заголовки.Вставить("Authorization", "Bearer " + ТокенДоступа);
Запрос.Заголовки.Вставить("Content-Type", "application/json; charset=utf-8");
```

### Certificate-based authentication (TLS mutual auth / CertificateAuth)

```bsl
// Путь к сертификату и пароль — из безопасного хранилища
ПараметрыCertAuth = БезопасноеХранилище.Прочитать("ИнтеграцияСертификат");

СертификатКлиента = Новый СертификатКлиентаФайл(
    ПараметрыCertAuth.ПутьКСертификату,
    ПараметрыCertAuth.Пароль);

ЗащИтоеСоединение = Новый ЗащищённоеСоединение(
    , , СертификатКлиента, ,
    Истина); // проверять серверный сертификат

Соединение = Новый HTTPСоединение(
    "api.example.com", , , , , 30, ЗащищённоеСоединение);
```

---

## Rule 4: HTTP client - retry and timeout

External calls are unreliable. Always wrap them in `try/except`. For mutating operations, use an idempotency key and protection against repeated execution.

```bsl
Функция ВызватьВнешнийAPIСПовтором(URLПуть, ТелоЗапросаJSON, КлючИдемпотентности = "")

    МаксимумПопыток = 3;
    ОжиданиеМеждуПопытками = 2; // секунды

    Для НомерПопытки = 1 По МаксимумПопыток Цикл

        Попытка

            Соединение = Новый HTTPСоединение(
                "api.example.com", , , , , 30,
                Новый ЗащищённоеСоединение);

            Запрос = Новый HTTPЗапрос(URLПуть);
            Запрос.Заголовки.Вставить("Content-Type", "application/json; charset=utf-8");

            // Ключ идемпотентности — безопасное повторение без дублирования
            Если ЗначениеЗаполнено(КлючИдемпотентности) Тогда
                Запрос.Заголовки.Вставить("Idempotency-Key", КлючИдемпотентности);
            КонецЕсли;

            Запрос.УстановитьТелоИзСтроки(ТелоЗапросаJSON, КодировкаТекста.UTF8);

            Ответ = Соединение.ОтправитьДляОбработки(Запрос);

            Если Ответ.КодСостояния >= 200 И Ответ.КодСостояния < 300 Тогда
                Возврат Ответ.ПолучитьТелоКакСтроку();
            ИначеЕсли Ответ.КодСостояния >= 400 И Ответ.КодСостояния < 500 Тогда
                // Клиентская ошибка — не ретраить
                ВызватьИсключение СтрШаблон(
                    НСтр("ru = 'Ошибка запроса (HTTP %1). Повтор нецелесообразен.'"),
                    Ответ.КодСостояния);
            ИначеЕсли Ответ.КодСостояния >= 500 Тогда
                // Серверная ошибка — ретраим
                ВызватьИсключение СтрШаблон(
                    НСтр("ru = 'Сервер вернул %1'"), Ответ.КодСостояния);
            КонецЕсли;

        Исключение

            ИнфоОшибки = ИнформацияОбОшибке();
            УровеньЖР = ?(НомерПопытки < МаксимумПопыток,
                УровеньЖурналаРегистрации.Предупреждение,
                УровеньЖурналаРегистрации.Ошибка);

            ЗаписьЖурналаРегистрации(
                НСтр("ru = 'ВнешняяИнтеграция.HTTPЗапрос'"), УровеньЖР, , ,
                СтрШаблон(НСтр("ru = 'Попытка %1/%2. Ошибка: %3'"),
                    НомерПопытки, МаксимумПопыток,
                    ПодробноеПредставлениеОшибки(ИнфоОшибки)));

            Если НомерПопытки = МаксимумПопыток Тогда
                ВызватьИсключение;
            КонецЕсли;

            // Пауза перед следующей попыткой
            ТекущаяДата = ТекущаяДата();
            Пока ТекущаяДата() < ТекущаяДата + ОжиданиеМеждуПопытками Цикл
            КонецЦикла;

        КонецПопытки;

    КонецЦикла;

    Возврат "";

КонецФункции
```

---

## Rule 5: Idempotency - protection against duplicates

Mutating operations (POST creation, state changes) must be protected against double execution.

**Idempotency patterns:**
- `Idempotency-Key` in the header (UUID generated on the client side);
- external identifier (`НомерВнешнего`) in the body with a unique index on the 1C side;
- check whether the object exists before creation.

```bsl
// Проверка дубля перед записью (серверная идемпотентность)
Функция НайтиЗаказПоВнешнемуНомеру(НомерВнешнего)
    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ ПЕРВЫЕ 1
    |   Заказы.Ссылка КАК Ссылка
    |ИЗ
    |   Документ.ЗаказКлиента КАК Заказы
    |ГДЕ
    |   Заказы.НомерВнешнего = &НомерВнешнего";
    Запрос.УстановитьПараметр("НомерВнешнего", НомерВнешнего);
    Выборка = Запрос.Выполнить().Выбрать();
    Возврат ?(Выборка.Следующий(), Выборка.Ссылка, Неопределено);
КонецФункции

// В обработчике POST:
СуществующийЗаказ = НайтиЗаказПоВнешнемуНомеру(ПараметрыЗаказа.НомерВнешнего);
Если СуществующийЗаказ <> Неопределено Тогда
    // Дубль — вернуть 200 с данными существующего заказа (не 201)
    Возврат ОтветУспеха(200, ПолучитьДанныеЗаказа(СуществующийЗаказ));
КонецЕсли;
```

---

## Rule 6: Error shape - a stable contract

The error response must be predictable. The client must be able to distinguish:

| HTTP code | Error type | Client behavior |
|----------|-----------|------------------|
| 400 | Input validation error | Do not retry; fix the request |
| 401 | Authentication error | Refresh the token; do not retry immediately |
| 409 | Conflict / duplicate | Do not retry; handle the duplicate |
| 422 | Business error (data is valid, but a rule is violated) | Do not retry; show to the user |
| 500 | Internal error | Retry with backoff |
| 503 | Service temporarily unavailable | Retry with backoff |

```bsl
// Единая структура тела ошибки
// {"error": "VALIDATION_ERROR", "message": "...", "correlationId": "..."}

Функция ОтветОшибкиС Корреляцией(КодСостояния, КодОшибки, Сообщение, КорреляцияИд)
    Ответ = Новый HTTPСервисОтвет(КодСостояния);
    Ответ.Заголовки.Вставить("Content-Type", "application/json; charset=utf-8");
    Ответ.Заголовки.Вставить("X-Correlation-Id", КорреляцияИд);
    ТелоОшибки = Новый Структура;
    ТелоОшибки.Вставить("error",         КодОшибки);
    ТелоОшибки.Вставить("message",       Сообщение);
    ТелоОшибки.Вставить("correlationId", КорреляцияИд);
    ЗаписьJSON = Новый ЗаписьJSON;
    ЗаписьJSON.УстановитьСтроку();
    ЗаписатьJSON(ЗаписьJSON, ТелоОшибки);
    Ответ.УстановитьТелоИзСтроки(ЗаписьJSON.Закрыть());
    Возврат Ответ;
КонецФункции
```

Never return the 1C stack trace, module names, or internal metadata object IDs to the external API.

---

## Rule 7: SOAP / WSПрокси

For SOAP services, use `WSПрокси`, created through `WSОпределения`. Pass authentication through the proxy parameters, not in the message body.

```bsl
// Создание WSПрокси с аутентификацией
Функция СоздатьПроксиПлатёжногоШлюза()

    УчётныеДанные = БезопасноеХранилище.Прочитать("ПлатёжныйШлюзSOAP");

    WSОпределения = Новый WSОпределения(
        "https://payment.example.com/service?wsdl",
        УчётныеДанные.Логин,
        УчётныеДанные.Пароль,
        ,       // прокси
        30);    // таймаут

    Прокси = WSОпределения.СоздатьWSПрокси("PaymentService", "PaymentPort");
    Прокси.Пользователь = УчётныеДанные.Логин;
    Прокси.Пароль       = УчётныеДанные.Пароль;
    Прокси.Таймаут      = 30;

    Возврат Прокси;

КонецФункции

// Вызов с обработкой ошибок
Попытка
    ПроксиWS = СоздатьПроксиПлатёжногоШлюза();

    // XDTO-объект для тела запроса
    ФабрикаXDTO = ПроксиWS.ФабрикаXDTO;
    ЗапросXDTO  = ФабрикаXDTO.Создать(ФабрикаXDTO.Тип("http://payment.example.com/", "PayRequest"));
    ЗапросXDTO.Amount    = СуммаПлатежа;
    ЗапросXDTO.OrderId   = Строка(ИдентификаторЗаказа);
    ЗапросXDTO.Currency  = "RUB";

    ОтветXDTO = ПроксиWS.Pay(ЗапросXDTO);

    Если ОтветXDTO.Status <> "OK" Тогда
        ВызватьИсключение СтрШаблон(НСтр("ru = 'Платёжный шлюз вернул: %1'"), ОтветXDTO.Status);
    КонецЕсли;

Исключение
    ЗаписьЖурналаРегистрации(
        НСтр("ru = 'ПлатёжныйШлюз.Оплата'"),
        УровеньЖурналаРегистрации.Ошибка,,,
        ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
    ВызватьИсключение;
КонецПопытки;
```

---

## Rule 8: Logging - what to write and what to hide

### Write to the registration log

- Correlation identifier (`correlationId`, `requestId`, `X-Correlation-Id`);
- external identifier of the business object (order number, payment ID);
- HTTP response code and request execution time;
- attempt number during retry;
- brief description of the result (created/updated/rejected).

### Never write to the log

- Values of the `Authorization` header (Bearer token, Basic credentials);
- passwords, API keys, secrets from `БезопасноеХранилище`;
- full request/response body if it contains personal data;
- internal call stacks in responses to the client (only in the registration log).

```bsl
// Корреляция через запрос
КорреляцияИд = Запрос.Заголовки.Получить("X-Correlation-Id");
Если НЕ ЗначениеЗаполнено(КорреляцияИд) Тогда
    КорреляцияИд = Строка(Новый УникальныйИдентификатор);
КонецЕсли;

ЗаписьЖурналаРегистрации(
    НСтр("ru = 'ИнтеграцияЗаказов.ПолучитьЗаказ'"),
    УровеньЖурналаРегистрации.Информация,,,
    СтрШаблон(НСтр("ru = 'correlationId=%1 result=OK orderId=%2'"),
        КорреляцияИд, НомерЗаказа));
```

---

## Rule 9: 1C HTTP service - authentication of incoming requests

Check authentication first, before accessing business data.

```bsl
// Проверка Bearer-токена во входящем запросе
Функция ПроверитьАутентификациюЗапроса(Запрос)

    ЗаголовокAuth = Запрос.Заголовки.Получить("Authorization");
    Если НЕ ЗначениеЗаполнено(ЗаголовокAuth) Тогда
        Возврат Ложь;
    КонецЕсли;

    Если НЕ СтрНачинаетсяС(ЗаголовокAuth, "Bearer ") Тогда
        Возврат Ложь;
    КонецЕсли;

    ПолученныйТокен = Сред(ЗаголовокAuth, 8); // убираем "Bearer "

    ОжидаемыйТокен = БезопасноеХранилище.Прочитать("ВходящийAPIТокен").Токен;

    // Сравнение в постоянное время (защита от timing attack)
    // Для простых случаев допустимо прямое сравнение строк
    Возврат (ПолученныйТокен = ОжидаемыйТокен);

КонецФункции

// В начале обработчика:
Если НЕ ПроверитьАутентификациюЗапроса(Запрос) Тогда
    Возврат ОтветОшибки(401, "UNAUTHORIZED", НСтр("ru = 'Аутентификация не прошла'"));
КонецЕсли;
```

---

## Rule 10: Interface versioning

Changes to the contract without backward compatibility require a new version.

| Change | Compatibility | Action |
|-----------|--------------|---------|
| Add a new field to the response | Compatible | Add; document |
| Add an optional field to the request | Compatible | Add with defaults |
| Remove or rename a field | Incompatible | Create `/v2/…`, keep `/v1/…` |
| Change a field type | Incompatible | Create `/v2/…`, keep `/v1/…` |
| Change the semantics of an existing field | Incompatible | Create `/v2/…` |

Specify the version in the URL: `/api/v1/orders`, `/api/v2/orders`.

---

## Typical mistakes

| Mistake | Consequence | How to avoid |
|--------|------------|--------------|
| Secret in a configuration constant or attribute | Leak during configuration / database export | Only `БезопасноеХранилище` |
| HTTP call inside a transaction | Timeout (30 s) = lock on all related records | Move HTTP calls outside the transaction |
| No idempotency key on retries | Duplicate data after a network error | Use `Idempotency-Key` or an external ID with a unique index |
| Returning the 1C stack trace in the error body | Exposure of the internal system structure | Return only a stable error code and message |
| Retry on 4xx errors | Useless load, possible repetition of the conflict | Retry only 5xx and network errors |
| Logging tokens/passwords | Secrets in the registration log | Mask before writing to the registration log |
| Business logic in the HTTP service handler | No reuse and no testing | Thin handler + separate common module |
| No input validation | Incorrect data written to the database | Check all required fields before the business operation |

---

## When to use

| Trigger | Action |
|---------|----------|
| An incoming 1C HTTP service is being created | Apply rules 2, 6, 9, 10 |
| An HTTP client is being created (outgoing REST) | Apply rules 3, 4, 5, 8 |
| SOAP / Web Service is being configured | Apply rule 7, 3 (secrets) |
| A contract is being designed or changed | Start with rule 1, apply rule 10 |
| Any authentication is being added | Apply rule 3 + references/auth-schemes.md |
| Reviewing integration code | Go through the checklist below |

---

## Integration review checklist

- [ ] The contract is described: endpoint, method, version, auth scheme, payload, error shape
- [ ] Secrets are only in `БезопасноеХранилище`; nothing is logged anywhere
- [ ] HTTP calls are moved outside 1C transactions
- [ ] Mutating operations are protected against duplicates (idempotency key or external ID)
- [ ] The HTTP service handler is thin: parse → validate → call → respond
- [ ] The error response is stable; no stack trace is returned to the client
- [ ] Retry is used only for 5xx and network errors
- [ ] `correlationId` is logged, but not the token/password
- [ ] Incompatible contract changes are released as a new version (`/v2/…`)

---

## Related resources

- [references/auth-schemes.md](references/auth-schemes.md) - details for each authentication scheme
- [bsl-practices/error-handling/SKILL.md](../error-handling/SKILL.md) - transactions and exception handling

---
depends_on: []
---
