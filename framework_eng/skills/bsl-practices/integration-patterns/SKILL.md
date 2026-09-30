---
name: integration-patterns
description: "For 1C HTTP/REST/SOAP, auth, retry, webhooks"
---

# 1C Integration Patterns

**Key principle:** The contract is defined before code. An HTTP service is a thin layer for parsing requests and forming responses; business logic lives in common modules. Secrets are only through `ОбщегоНазначения.*ДанныеВБезопасноеХранилище*`, never in code or constants.

---

## Rule 1: The contract is the first thing to define

Before writing code, fix the contract as a structure or comment:
- transport and URL (method, version in the path, Content-Type/Accept headers);
- authentication (scheme, where the secret is stored);
- request and response body (fields, types, requiredness, null semantics);
- idempotency key (if the operation changes data);
- retry policy and timeout;
- error shape (HTTP codes and error body structure).

Changing the contract without versioning is a compatibility break. Add new fields without removing old ones. Changes to the semantics of existing fields must be introduced through a new version (`/v2/…`).

---

## Rule 2: 1C HTTP services are a thin handler

An HTTP service handler should do only three things: parse the request, call a business function, and return a response. Do not put business logic directly in the handler.

### Canonical HTTP service pattern

```bsl
// Handler for the POST method of resource /orders
Функция ОбработатьПОСТ(Запрос)

    // 1. Parse body
    ТелоСтрокой = Запрос.ПолучитьТелоКакСтроку();
    ЧтениеJSON = Новый ЧтениеJSON;
    ЧтениеJSON.УстановитьСтроку(ТелоСтрокой);
    ПараметрыЗаказа = ПрочитатьJSON(ЧтениеJSON, Тип("Структура"));

    // 2. Validate required fields
    Если НЕ ЗначениеЗаполнено(ПараметрыЗаказа.НомерВнешнего) Тогда
        Возврат ОтветОшибки(400, "VALIDATION_ERROR",
            НСтр("ru = 'Field НомерВнешнего is required'"));
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
            НСтр("ru = 'Internal server error'"));
    КонецПопытки;

    // 4. Form response
    Возврат ОтветУспеха(201, РезультатСозданияЗаказа);

КонецФункции

// Helper functions for response formation
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

## Rule 3: Authentication — schemes and secret storage

All secrets (tokens, passwords, keys) are stored exclusively through the `ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище` / `ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища` wrappers (the `БезопасноеХранилищеДанных` information register). Never in configuration constants, session parameters, catalog attributes, or module text.

For details on each scheme, see [../security/references/auth.md](../security/references/auth.md).

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
УстановитьПривилегированныйРежим(Истина);
ТокенДоступа = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища("ИнтеграцияСВнешнимСервисом", "ТокенДоступа");
УстановитьПривилегированныйРежим(Ложь);

Запрос = Новый HTTPЗапрос("/api/v1/resource");
Запрос.Заголовки.Вставить("Authorization", "Bearer " + ТокенДоступа);
Запрос.Заголовки.Вставить("Content-Type", "application/json; charset=utf-8");
```

### Certificate authentication (TLS mutual auth / CertificateAuth)

```bsl
// Путь к сертификату и пароль — из безопасного хранилища
УстановитьПривилегированныйРежим(Истина);
ПараметрыCertAuth = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища("ИнтеграцияСертификат", "ПутьКСертификату,Пароль");
УстановитьПривилегированныйРежим(Ложь);

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

An external call is unreliable. Always wrap it in `Try/Except`. For modifying operations, use an idempotency key and protection against duplicate execution.

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

## Rule 5: Idempotency — protection against duplicates

State-changing operations (POST creation, state changes) must be protected against duplicate execution.

**Idempotency patterns:**
- `Idempotency-Key` in the header (UUID generated on the client side);
- external identifier (`НомерВнешнего`) in the body with a unique index on the 1С side;
- check whether the object exists before creating it.

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

## Rule 6: Error shape — a stable contract

The error response must be predictable. The client should be able to distinguish:

| HTTP code | Error type | Client behavior |
|----------|-----------|------------------|
| 400 | Input validation error | Do not retry; fix the request |
| 401 | Authentication error | Refresh the token; do not retry immediately |
| 409 | Conflict / duplicate | Do not retry; handle the duplicate |
| 422 | Business error (data is valid, but the rule is violated) | Do not retry; show the user |
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

Never return the 1С call stack, module names, or internal metadata object IDs in the external API.

---

## Rule 7: SOAP / WSПрокси

For working with SOAP services, use `WSПрокси`, created via `WSОпределения`. Pass authentication through the proxy parameters, not in the message body.

```bsl
// Создание WSПрокси с аутентификацией
Функция СоздатьПроксиПлатёжногоШлюза()

    УстановитьПривилегированныйРежим(Истина);
    УчётныеДанные = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища("ПлатёжныйШлюзSOAP", "Логин,Пароль");
    УстановитьПривилегированныйРежим(Ложь);

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

## Rule 8: Logging — what to write and what to hide

### Write to the registration log

- Correlation identifier (`correlationId`, `requestId`, `X-Correlation-Id`);
- external identifier of the business object (order number, payment ID);
- HTTP response code and request execution time;
- attempt number during retry;
- brief description of the result (created/updated/rejected).

### Never write to the log

- `Authorization` header values (Bearer token, Basic credentials);
- passwords, API keys, secrets from the storage (`ОбщегоНазначения.*ДанныеВБезопасноеХранилище*`);
- the full request/response body if it contains personal data;
- internal call stacks in responses to the client (only in ЖР).

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

## Rule 9: 1С HTTP service - authentication of incoming requests

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

    УстановитьПривилегированныйРежим(Истина);
    ОжидаемыйТокен = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища("ВходящийAPIТокен", "Токен");
    УстановитьПривилегированныйРежим(Ложь);

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
| Remove or rename a field | Incompatible | Create `/v2/…`, support `/v1/…` |
| Change a field type | Incompatible | Create `/v2/…`, support `/v1/…` |
| Change the semantics of an existing field | Incompatible | Create `/v2/…` |

Specify the version in the URL: `/api/v1/orders`, `/api/v2/orders`.

---

## Typical Mistakes

| Mistake | Consequence | How to Avoid |
|--------|------------|--------------|
| Secret in a configuration constant or attribute | Leakage during configuration / database export | Only through `ОбщегоНазначения.*ДанныеВБезопасноеХранилище*` |
| HTTP call inside a transaction | Timeout (30 s) = blocking all related records | Move HTTP calls outside the transaction |
| No idempotency key on retries | Data duplication on a network error | Use `Idempotency-Key` or an external ID with a unique index |
| Returning the 1C stack in the error body | Exposes the internal system structure | Return only a stable error code and message |
| Retry on 4xx errors | Useless load, possible repetition of the conflict | Retry only 5xx and network errors |
| Logging tokens/passwords | Secrets in the registration log | Mask before writing to the ЖР |
| Business logic in the HTTP service handler | Impossible to reuse and test | Thin handler + separate common module |
| No input validation | Invalid data written to the database | Check all required fields before the business operation |

---

## When to Apply

| Trigger | Action |
|---------|----------|
| A 1C HTTP service is being created (incoming) | Apply rules 2, 6, 9, 10 |
| An HTTP client is being created (outgoing REST) | Apply rules 3, 4, 5, 8 |
| SOAP/Web Service is being configured | Apply rule 7, 3 (secrets) |
| A contract is being designed or changed | Start with rule 1, apply rule 10 |
| Any authentication is being added | Apply rule 3 + ../security/references/auth.md |
| Reviewing integration code | Go through the checklist below |

---

## Integration Review Checklist

- [ ] The contract is defined: endpoint, method, version, auth scheme, payload, error shape
- [ ] Secrets only through `ОбщегоНазначения.*ДанныеВБезопасноеХранилище*`; nowhere are they logged
- [ ] HTTP calls are moved outside 1C transactions
- [ ] Mutating operations are protected against duplicates (idempotency key or external ID)
- [ ] The HTTP service handler is thin: parse → validate → call → respond
- [ ] The error response is stable; the call stack is not returned to the client
- [ ] Retry only on 5xx and network errors
- [ ] `correlationId` is logged, but not the token/password
- [ ] Incompatible contract changes are delivered as a new version (`/v2/…`)

---

## Related resources

- [../security/references/auth.md](../security/references/auth.md) — details for each authentication scheme
- [bsl-practices/error-handling/SKILL.md](../error-handling/SKILL.md) — transactions and exception handling

---
depends_on: []
---
