---
name: integration-patterns
description: "For 1C HTTP/REST/SOAP, auth, retry, webhooks"
---

# 1C Integration Patterns

**Core principle:** The contract is defined before the code. The HTTP service is a thin layer for parsing the request and forming the response; business logic lives in common modules. Secrets belong only in `БезопасноеХранилище`, never in code or constants.

---

## Rule 1: The contract is the first thing to define

Before writing code, fix the contract as a structure or a comment:
- transport and URL (method, version in the path, Content-Type/Accept headers);
- authentication (scheme, where the secret is stored);
- request and response body (fields, types, requiredness, null semantics);
- idempotency key (if the operation changes data);
- retry policy and timeout;
- error shape (HTTP codes and error body structure).

Changing the contract without versioning is a compatibility break. Add new fields without removing old ones. Change the semantics of existing fields through a new version (`/v2/…`).

---

## Rule 2: 1C HTTP services are a thin handler

The HTTP service handler should do only three things: parse the request, call the business function, and return a response. Do not put business logic directly into the handler.

### Canonical HTTP service pattern

```bsl
// POST handler for resource /orders
Функция ОбработатьПОСТ(Запрос)

    // 1. Body parsing
    ТелоСтрокой = Запрос.ПолучитьТелоКакСтроку();
    ЧтениеJSON = Новый ЧтениеJSON;
    ЧтениеJSON.УстановитьСтроку(ТелоСтрокой);
    ПараметрыЗаказа = ПрочитатьJSON(ЧтениеJSON, Тип("Структура"));

    // 2. Validation of required fields
    Если НЕ ЗначениеЗаполнено(ПараметрыЗаказа.НомерВнешнего) Тогда
        Возврат ОтветОшибки(400, "VALIDATION_ERROR",
            НСтр("ru = 'Поле НомерВнешнего обязательно'"));
    КонецЕсли;

    // 3. Business call
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

    // 4. Response formation
    Возврат ОтветУспеха(201, РезультатСозданияЗаказа);

КонецФункции

// Helper response functions
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

## Rule 3: Authentication is about schemes and secret storage

All secrets (tokens, passwords, keys) are stored exclusively in `БезопасноеХранилище`. Never in configuration constants, session parameters, catalog attributes, or module text.

See [references/auth-schemes.md](references/auth-schemes.md) for details on each scheme.

### Basic Auth

```bsl
// Getting credentials from secure storage
УчётныеДанные = БезопасноеХранилище.Прочитать("ИнтеграцияСВнешнимСервисом");

Логин    = УчётныеДанные.Логин;
Пароль   = УчётныеДанные.Пароль;

// Do not log: login/password, Authorization header, body with personal data

Соединение = Новый HTTPСоединение(
    "api.example.com",
    ,               // port - default 443 for HTTPS
    Логин,
    Пароль,
    ,               // proxy
    30,             // timeout (sec)
    Новый ЗащищённоеСоединение); // OpenSSL / CertificateAuth - see below
```

### Bearer Token (API key or OAuth 2.0 access_token)

```bsl
// Token from secure storage
ТокенДоступа = БезопасноеХранилище.Прочитать("ИнтеграцияСВнешнимСервисом").ТокенДоступа;

Запрос = Новый HTTPЗапрос("/api/v1/resource");
Запрос.Заголовки.Вставить("Authorization", "Bearer " + ТокенДоступа);
Запрос.Заголовки.Вставить("Content-Type", "application/json; charset=utf-8");
```

### Certificate authentication (TLS mutual auth / CertificateAuth)

```bsl
// Certificate path and password - from secure storage
ПараметрыCertAuth = БезопасноеХранилище.Прочитать("ИнтеграцияСертификат");

СертификатКлиента = Новый СертификатКлиентаФайл(
    ПараметрыCertAuth.ПутьКСертификату,
    ПараметрыCertAuth.Пароль);

ЗащИтоеСоединение = Новый ЗащищённоеСоединение(
    , , СертификатКлиента, ,
    Истина); // verify server certificate

Соединение = Новый HTTPСоединение(
    "api.example.com", , , , , 30, ЗащищённоеСоединение);
```

---

## Rule 4: HTTP client - retry and timeout

External calls are unreliable. Always wrap them in `Попытка/Исключение`. For mutating operations, use an idempotency key and protection against duplicate execution.

```bsl
Функция ВызватьВнешнийAPIСПовтором(URLПуть, ТелоЗапросаJSON, КлючИдемпотентности = "")

    МаксимумПопыток = 3;
    ОжиданиеМеждуПопытками = 2; // seconds

    Для НомерПопытки = 1 По МаксимумПопыток Цикл

        Попытка

            Соединение = Новый HTTPСоединение(
                "api.example.com", , , , , 30,
                Новый ЗащищённоеСоединение);

            Запрос = Новый HTTPЗапрос(URLПуть);
            Запрос.Заголовки.Вставить("Content-Type", "application/json; charset=utf-8");

            // Idempotency key - safe repeat without duplication
            Если ЗначениеЗаполнено(КлючИдемпотентности) Тогда
                Запрос.Заголовки.Вставить("Idempotency-Key", КлючИдемпотентности);
            КонецЕсли;

            Запрос.УстановитьТелоИзСтроки(ТелоЗапросаJSON, КодировкаТекста.UTF8);

            Ответ = Соединение.ОтправитьДляОбработки(Запрос);

            Если Ответ.КодСостояния >= 200 И Ответ.КодСостояния < 300 Тогда
                Возврат Ответ.ПолучитьТелоКакСтроку();
            ИначеЕсли Ответ.КодСостояния >= 400 И Ответ.КодСостояния < 500 Тогда
                // Client error - do not retry
                ВызватьИсключение СтрШаблон(
                    НСтр("ru = 'Ошибка запроса (HTTP %1). Повтор нецелесообразен.'"),
                    Ответ.КодСостояния);
            ИначеЕсли Ответ.КодСостояния >= 500 Тогда
                // Server error - retry
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

            // Pause before the next attempt
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
- `Idempotency-Key` in a header (UUID generated on the client side);
- external identifier (`НомерВнешнего`) in the body with a unique index on the 1C side;
- check whether the object exists before creating it.

```bsl
// Duplicate check before write (server-side idempotency)
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

// In the POST handler:
СуществующийЗаказ = НайтиЗаказПоВнешнемуНомеру(ПараметрыЗаказа.НомерВнешнего);
Если СуществующийЗаказ <> Неопределено Тогда
    // Duplicate - return 200 with the existing order data (not 201)
    Возврат ОтветУспеха(200, ПолучитьДанныеЗаказа(СуществующийЗаказ));
КонецЕсли;
```

---

## Rule 6: Error shape is a stable contract

The error response must be predictable. The client must be able to distinguish:

| HTTP code | Error type | Client behavior |
|----------|-----------|------------------|
| 400 | Input validation error | Do not retry; fix the request |
| 401 | Authentication error | Refresh the token; do not retry immediately |
| 409 | Conflict / duplicate | Do not retry; handle the duplicate |
| 422 | Business error (data is valid, but a rule was violated) | Do not retry; show to the user |
| 500 | Internal error | Retry with backoff |
| 503 | Service temporarily unavailable | Retry with backoff |

```bsl
// Unified error body structure
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

Never return the 1C call stack, module names, or internal metadata object IDs in an external API.

---

## Rule 7: SOAP / WSПрокси

For SOAP services, use `WSПрокси`, created through `WSОпределения`. Pass authentication through proxy parameters, not in the message body.

```bsl
// Creating WSПрокси with authentication
Функция СоздатьПроксиПлатёжногоШлюза()

    УчётныеДанные = БезопасноеХранилище.Прочитать("ПлатёжныйШлюзSOAP");

    WSОпределения = Новый WSОпределения(
        "https://payment.example.com/service?wsdl",
        УчётныеДанные.Логин,
        УчётныеДанные.Пароль,
        ,       // proxy
        30);    // timeout

    Прокси = WSОпределения.СоздатьWSПрокси("PaymentService", "PaymentPort");
    Прокси.Пользователь = УчётныеДанные.Логин;
    Прокси.Пароль       = УчётныеДанные.Пароль;
    Прокси.Таймаут      = 30;

    Возврат Прокси;

КонецФункции

// Call with error handling
Попытка
    ПроксиWS = СоздатьПроксиПлатёжногоШлюза();

    // XDTO object for the request body
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

### Write to the event log

- Correlation identifier (`correlationId`, `requestId`, `X-Correlation-Id`);
- external business object identifier (order number, payment ID);
- HTTP response code and request execution time;
- retry attempt number;
- short description of the result (created/updated/rejected).

### Never write to the log

- Values of the `Authorization` header (Bearer token, Basic credentials);
- passwords, API keys, secrets from `БезопасноеХранилище`;
- the full request/response body if it contains personal data;
- internal call stacks in responses to the client (only in the event log).

```bsl
// Correlation via request
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
// Checking the Bearer token in an incoming request
Функция ПроверитьАутентификациюЗапроса(Запрос)

    ЗаголовокAuth = Запрос.Заголовки.Получить("Authorization");
    Если НЕ ЗначениеЗаполнено(ЗаголовокAuth) Тогда
        Возврат Ложь;
    КонецЕсли;

    Если НЕ СтрНачинаетсяС(ЗаголовокAuth, "Bearer ") Тогда
        Возврат Ложь;
    КонецЕсли;

    ПолученныйТокен = Сред(ЗаголовокAuth, 8); // remove "Bearer "

    ОжидаемыйТокен = БезопасноеХранилище.Прочитать("ВходящийAPIТокен").Токен;

    // Constant-time comparison (protection against timing attacks)
    // For simple cases, a direct string comparison is acceptable
    Возврат (ПолученныйТокен = ОжидаемыйТокен);

КонецФункции

// At the start of the handler:
Если НЕ ПроверитьАутентификациюЗапроса(Запрос) Тогда
    Возврат ОтветОшибки(401, "UNAUTHORIZED", НСтр("ru = 'Аутентификация не прошла'"));
КонецЕсли;
```

---

## Rule 10: Interface versioning

Contract changes without backward compatibility require a new version.

| Change | Compatibility | Action |
|-----------|--------------|---------|
| Add a new field to the response | Compatible | Add; document |
| Add an optional field to the request | Compatible | Add with defaults |
| Remove or rename a field | Incompatible | Create `/v2/…`, support `/v1/…` |
| Change a field type | Incompatible | Create `/v2/…`, support `/v1/…` |
| Change the semantics of an existing field | Incompatible | Create `/v2/…` |

Specify the version in the URL: `/api/v1/orders`, `/api/v2/orders`.

---

## Typical mistakes

| Mistake | Consequence | How to avoid |
|--------|------------|--------------|
| Secret in a configuration constant or attribute | Leak when exporting the configuration / database | Only `БезопасноеХранилище` |
| HTTP call inside a transaction | Timeout (30 s) = lock on all related records | Move HTTP calls outside the transaction |
| No idempotency key on retries | Duplicate data on a network error | Use `Idempotency-Key` or an external ID with a unique index |
| Returning the 1C stack in the error body | Exposing the internal structure of the system | Return only a stable error code and message |
| Retry on 4xx errors | Useless load, possible repeated conflict | Retry only 5xx and network errors |
| Logging tokens/passwords | Secrets in the event log | Mask before writing to the event log |
| Business logic in the HTTP service handler | Cannot be reused or tested | Thin handler + separate common module |
| No input validation | Writing incorrect data to the database | Check all required fields before the business operation |

---

## When to apply

| Trigger | Action |
|---------|----------|
| A 1C HTTP service is being created (incoming) | Apply rules 2, 6, 9, 10 |
| An HTTP client is being created (outgoing REST) | Apply rules 3, 4, 5, 8 |
| A SOAP/Web Service is being configured | Apply rule 7, 3 (secrets) |
| A contract is being designed or changed | Start with rule 1, apply rule 10 |
| Any authentication is being added | Apply rule 3 + references/auth-schemes.md |
| Reviewing integration code | Go through the checklist below |

---

## Integration review checklist

- [ ] The contract is described: endpoint, method, version, auth scheme, payload, error shape
- [ ] Secrets are only in `БезопасноеХранилище`; they are not logged anywhere
- [ ] HTTP calls are moved outside 1C transactions
- [ ] Mutating operations are protected against duplicates (idempotency key or external ID)
- [ ] The HTTP service handler is thin: parse → validate → call → respond
- [ ] The error response is stable; the call stack is not returned to the client
- [ ] Retry only on 5xx and network errors
- [ ] correlationId is logged, but not the token/password
- [ ] Incompatible contract changes are introduced as a new version (`/v2/…`)

---

## Related resources

- [references/auth-schemes.md](references/auth-schemes.md) - details on each authentication scheme
- [bsl-practices/error-handling/SKILL.md](../error-handling/SKILL.md) - transactions and exception handling

---
depends_on: []
---
