# Authentication Schemes in 1C Integrations

Reference for implementing each scheme. Read together with `SKILL.md` (rule 3).

---

## General rule: secret storage

All credentials (passwords, tokens, keys, certificate paths) are stored in `БезопасноеХранилище`.

```bsl
// Запись (выполняется администратором один раз через обработку настройки)
БезопасноеХранилище.Записать("ИмяНастройки", Новый Структура("Логин, Пароль", "user", "secret"));

// Чтение в коде интеграции
УчётныеДанные = БезопасноеХранилище.Прочитать("ИмяНастройки");
```

Setting names are strings in the format `SystemPrefix.SecretType`, for example `"ExternalERP.BasicAuth"`.

---

## 1. Basic Authentication

Login and password are encoded in Base64 and sent in the `Authorization` header.

```bsl
Функция СоздатьСоединениеBasicAuth(Хост, ИмяНастройки, Таймаут = 30)
    УД = БезопасноеХранилище.Прочитать(ИмяНастройки);
    Возврат Новый HTTPСоединение(
        Хост, , УД.Логин, УД.Пароль, , Таймаут,
        Новый ЗащищённоеСоединение);
КонецФункции
```

The 1C platform automatically adds the `Authorization: Basic <base64>` header when creating a connection with a login and password.

**When to use:** internal or legacy APIs where OAuth is not supported.

**Limitations:** credentials are sent with every request; HTTPS is mandatory.

---

## 2. Bearer Token (static API key)

The token is issued by the provider once and does not change (or changes rarely, manually).

```bsl
Функция ДобавитьBearerToken(Запрос, ИмяНастройки)
    Токен = БезопасноеХранилище.Прочитать(ИмяНастройки).ТокенДоступа;
    Запрос.Заголовки.Вставить("Authorization", "Bearer " + Токен);
    Возврат Запрос;
КонецФункции
```

**When to use:** external APIs (GitHub, Telegram, SendGrid, etc.), internal microservices.

---

## 3. OAuth 2.0 Client Credentials

Used for machine integrations (service-to-service). The token is obtained automatically and refreshed when it expires.

### Obtaining the token

```bsl
Функция ПолучитьОAuthТокен(ИмяНастройки)

    УД = БезопасноеХранилище.Прочитать(ИмяНастройки);
    // УД.TokenURL, УД.ClientId, УД.ClientSecret, УД.Scope

    Соединение = Новый HTTPСоединение(
        УД.ТокенХост, , , , , 30, Новый ЗащищённоеСоединение);

    Запрос = Новый HTTPЗапрос(УД.ТокенПуть);
    Запрос.Заголовки.Вставить("Content-Type", "application/x-www-form-urlencoded");

    ТелоЗапроса = СтрШаблон(
        "grant_type=client_credentials&client_id=%1&client_secret=%2&scope=%3",
        УД.ClientId, УД.ClientSecret, УД.Scope);
    Запрос.УстановитьТелоИзСтроки(ТелоЗапроса, КодировкаТекста.UTF8);

    Ответ = Соединение.ОтправитьДляОбработки(Запрос);

    Если Ответ.КодСостояния <> 200 Тогда
        ВызватьИсключение СтрШаблон(
            НСтр("ru = 'Не удалось получить OAuth токен. HTTP %1'"),
            Ответ.КодСостояния);
    КонецЕсли;

    ЧтениеJSON = Новый ЧтениеJSON;
    ЧтениеJSON.УстановитьСтроку(Ответ.ПолучитьТелоКакСтроку());
    ОтветJSON = ПрочитатьJSON(ЧтениеJSON, Тип("Структура"));

    Возврат ОтветJSON.access_token;

КонецФункции
```

### Caching and refreshing the token

The token is cached in session parameters or a common module (reused until it expires).

```bsl
// Кэш токена в параметрах сеанса (только для серверного контекста)
Функция ПолучитьТокенИзКэшаИлиОбновить(ИмяНастройки)

    ПараметрКэша = "OAuthToken_" + ИмяНастройки;

    КэшированныйТокен = ПараметрыСеанса[ПараметрКэша]; // или хранилище значений
    Если КэшированныйТокен <> Неопределено
        И КэшированныйТокен.СрокДействия > ТекущаяДата() + 60 Тогда
        Возврат КэшированныйТокен.Токен;
    КонецЕсли;

    НовыйТокен = ПолучитьОAuthТокен(ИмяНастройки);
    // expires_in из ответа — в секундах (обычно 3600)
    ПараметрыСеанса[ПараметрКэша] = Новый Структура(
        "Токен, СрокДействия",
        НовыйТокен,
        ТекущаяДата() + 3600);

    Возврат НовыйТокен;

КонецФункции
```

**When to use:** modern REST APIs (Keycloak, Azure AD, Yandex Cloud, 1С:Шина).

---

## 4. OAuth 2.0 Authorization Code (delegated access)

Used when access is needed on behalf of a user. Implemented through a browser redirect; not used in 1C background jobs.

Main steps:
1. Build the authorization URL with `response_type=code&client_id=...&redirect_uri=...`.
2. The user opens the URL in a browser and confirms access.
3. The provider redirects to `redirect_uri` with `?code=...`.
4. Exchange `code` for `access_token` (POST `/token`).
5. Store `refresh_token` in `БезопасноеХранилище`; refresh `access_token` via `grant_type=refresh_token`.

**When to use:** integration with Google Workspace, Microsoft 365 on behalf of a specific user.

---

## 5. Certificate Authentication (TLS mTLS)

The client presents a TLS certificate instead of a password. Used in government and banking APIs (Federal Tax Service, Bank of Russia, SMEV).

```bsl
Функция СоздатьСоединениеСертификат(Хост, ИмяНастройки, Таймаут = 60)

    УД = БезопасноеХранилище.Прочитать(ИмяНастройки);
    // УД.ПутьКСертификату — абсолютный путь к файлу .p12 / .pfx
    // УД.ПарольСертификата — пароль от контейнера

    СертификатКлиента = Новый СертификатКлиентаФайл(
        УД.ПутьКСертификату,
        УД.ПарольСертификата);

    ЗащИтоеСоединение = Новый ЗащищённоеСоединение(
        ,                    // CA-сертификат (Неопределено = системное хранилище)
        ,                    // CA-каталог
        СертификатКлиента,   // клиентский сертификат
        ,                    // хранилище сертификатов
        Истина);             // проверять серверный сертификат

    Возврат Новый HTTPСоединение(
        Хост, , , , , Таймаут, ЗащищённоеСоединение);

КонецФункции
```

**Storage specifics:**
- The certificate file `.p12` / `.pfx` is placed in a protected folder on the 1C server (not in the information base directory).
- `БезопасноеХранилище` stores the path and password; the file itself is not stored in the database.
- When rotating the certificate, update the file and password in the storage; no 1C restart is required.

**When to use:** SMEV, Federal Tax Service EDI, Bank of Russia, corporate PKI systems.

---

## 6. HMAC / request signature

The request body is signed with a secret key (HMAC-SHA256). Used in some payment gateways and marketplaces.

```bsl
// Пример: подпись тела запроса через HMAC-SHA256
// Требует подключённого компонента или встроенных функций криптографии

Функция ПодписатьЗапросHMAC(ТелоЗапросаСтрокой, ИмяНастройки)

    СекретныйКлюч = БезопасноеХранилище.Прочитать(ИмяНастройки).СекретныйКлюч;

    // Использование компонента или ВнешняяФункция — зависит от платформы
    // Пример: ОбщегоНазначения.HMACSHA256(ТелоЗапросаСтрокой, СекретныйКлюч)
    ПодписьHex = КриптографическийМодуль.HMACSHA256(
        ПолучитьДвоичныеДанныеИзСтроки(ТелоЗапросаСтрокой, "UTF-8"),
        ПолучитьДвоичныеДанныеИзСтроки(СекретныйКлюч, "UTF-8"));

    Возврат ПодписьHex;

КонецФункции
```

---

## Comparison table of schemes

| Scheme | Complexity | Security | Typical scenario |
|-------|-----------|-------------|------------------|
| Basic | Low | Medium (HTTPS only) | Internal / legacy APIs |
| Bearer Token | Low | High | SaaS APIs (GitHub, Telegram) |
| OAuth 2.0 CC | Medium | High | Modern B2B REST APIs |
| OAuth 2.0 AC | High | High | Access on behalf of a user |
| Certificate | High | Very high | Government APIs, SMEV, Federal Tax Service |
| HMAC | Medium | High | Payment gateways, marketplaces |
