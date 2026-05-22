# Authentication and external APIs

Reference guide to authentication schemes that appear in 1C code: OpenID/OIDC, OAuth 2.0, basic/token, client TLS certificate. Used by the `bsl-practices/security` skill.

---

## Scheme map

| Scheme | Where used | Where the secret is stored | Where the header is assembled |
|-------|-----------------|---------------------|--------------------------|
| OpenID / OpenID Connect | User authentication in the platform and БСП | Platform / БСП `ИнтернетПользователи` | Platform |
| OAuth 2.0 (client credentials) | 1C server → external API without user involvement | `БезопасноеХранилище` | 1C server |
| OAuth 2.0 (authorization code) | External API on behalf of a user | `БезопасноеХранилище` + refresh | 1C server |
| Basic auth | Legacy services, internal APIs | `БезопасноеХранилище` | 1C server |
| API token / API key | Simple REST APIs | `БезопасноеХранилище` | 1C server |
| Client TLS certificate (mTLS) | B2G, banks, secure integrations | OS CSP container | TLS stack, no header needed |

---

## OpenID / OpenID Connect

The platform supports OpenID as a way to authenticate infobase users:

- Connection parameters are in the infobase properties (through the configurator/administration).
- Additionally, БСП includes the `ИнтернетПользователи` subsystem and related modules for authorization in published HTTP services.
- User identification is based on `email`/`sub` from the ID token, mapped to an infobase user.

Notes:

- OIDC discovery (`/.well-known/openid-configuration`) - check that the URL is stable and accessible from the 1C server.
- The time on the 1C server must be synchronized (NTP); a drift of more than 5 minutes breaks token validation.
- Log `sub`/`email`, but **do not** log the ID token or access token in full.

---

## OAuth 2.0 over `HTTPСоединение`

### Getting an access token (client credentials)

```bsl
Функция ПолучитьAccessТокен(УчётнаяЗапись)

    УстановитьПривилегированныйРежим(Истина);
    Попытка
        ClientId = БезопасноеХранилище.ПрочитатьДанные(УчётнаяЗапись, "client_id");
        ClientSecret = БезопасноеХранилище.ПрочитатьДанные(УчётнаяЗапись, "client_secret");
    Исключение
        УстановитьПривилегированныйРежим(Ложь);
        ВызватьИсключение;
    КонецПопытки;
    УстановитьПривилегированныйРежим(Ложь);

    Тело = "grant_type=client_credentials"
        + "&client_id=" + ClientId
        + "&client_secret=" + ClientSecret;

    Запрос = Новый HTTPЗапрос("/oauth2/token");
    Запрос.Заголовки.Вставить("Content-Type", "application/x-www-form-urlencoded");
    Запрос.УстановитьТелоИзСтроки(Тело, КодировкаТекста.UTF8);

    Соединение = Новый HTTPСоединение(УчётнаяЗапись.Хост, 443, , , , 30, Новый ЗащищённоеСоединениеOpenSSL);
    Ответ = Соединение.ОтправитьДляОбработки(Запрос);

    Если Ответ.КодСостояния = 200 Тогда
        Возврат РазобратьТокен(Ответ.ПолучитьТелоКакСтроку());
    ИначеЕсли Ответ.КодСостояния = 401 ИЛИ Ответ.КодСостояния = 400 Тогда
        ВызватьИсключение НСтр("ru = 'Ошибка аутентификации во внешнем сервисе.'");
    Иначе
        ВызватьИсключение НСтр("ru = 'Сервис аутентификации временно недоступен.'");
    КонецЕсли;

КонецФункции
```

Notes:

- Variables `ClientId` and `ClientSecret` live only in this function; do not return them outside.
- The request body with `client_secret` is **not** logged.
- The received `access_token` is written to `БезопасноеХранилище` with the same owner, key `"access_token"`, and `expires_at` goes into the catalog attribute (this is public information).

### Refresh token

- When the access token expires, send `POST /oauth2/token` with `grant_type=refresh_token` and the `refresh_token` from `БезопасноеХранилище`.
- Many providers **rotate the refresh token** on every request - make sure to rewrite it in `БезопасноеХранилище`.
- If the refresh token is revoked (`400 invalid_grant`), this is **not** a retry, but a configuration/reauthorization error.

---

## Basic / API-token

```bsl
УстановитьПривилегированныйРежим(Истина);
Попытка
    Логин = БезопасноеХранилище.ПрочитатьДанные(УчётнаяЗапись, "login");
    Пароль = БезопасноеХранилище.ПрочитатьДанные(УчётнаяЗапись, "password");
Исключение
    УстановитьПривилегированныйРежим(Ложь);
    ВызватьИсключение;
КонецПопытки;
УстановитьПривилегированныйРежим(Ложь);

Учётка = Base64Строка(ПолучитьДвоичныеДанныеИзСтроки(Логин + ":" + Пароль));
Запрос.Заголовки.Вставить("Authorization", "Basic " + Учётка);
```

Stop rules:

- The `Authorization` header is assembled **only on the server** and is **not returned** to the client.
- `Пароль` is cleared (`Пароль = ""`) immediately after the header is assembled.
- The request as a whole is not logged; for diagnostics, log only the URL, method, response code, and time.

---

## Client TLS certificate (mTLS)

The platform provides three variants of client certificate objects for `HTTPСоединение`:

| Object | When to use | Where the key lives |
|--------|-----------------|----------------|
| `СертификатКлиентаФайл` | Certificate + key in a PFX file on the 1C server | Password-protected file |
| `СертификатКлиентаWindows` | Certificate in the Windows store under the rphost account | Windows registry |
| `СертификатКлиентаOpenSSL` | Linux server, key in a PEM file | File |

Example:

```bsl
СертификатКлиента = Новый СертификатКлиентаФайл(
    ПутьКPFX,                          // путь на сервере 1С, не на клиенте
    ПрочитатьПарольКонтейнераИзБХ());  // пароль контейнера из БезопасноеХранилище

ЗащищённоеСоединение = Новый ЗащищённоеСоединениеOpenSSL(СертификатКлиента, Неопределено);

Соединение = Новый HTTPСоединение(Хост, 443, , , , 30, ЗащищённоеСоединение);
```

Notes:

- The PFX/PEM path is on the 1C server, and only the rphost account (`USR1CV8`) has access.
- The PFX container password is in `БезопасноеХранилище`.
- On a Linux server, OpenSSL is used - `СертификатКлиентаWindows` will not work.
- TLS version and cipher suite are determined by the OS/OpenSSL; if an external service requires TLS 1.2+, that is a server configuration issue, not a 1C code issue.

---

## Error semantics

Authentication errors **must** be distinct from validation and business errors. Minimum set:

| Code | Semantics | Reaction |
|-----|-----------|---------|
| `missing credentials` | Secret is not set in `БезопасноеХранилище` | Configuration error, escalate to the administrator, **no retry** |
| `expired token` | Access token expired, refresh exists | Automatic refresh + repeat the operation |
| `invalid refresh` | Refresh revoked/invalid | Escalate: the owner must reauthorize |
| `invalid certificate` | Certificate expired, not found, wrong PFX password | Configuration error, **no retry** |
| `provider unavailable` | OIDC/OAuth/API unavailable (5xx, timeout, network) | Retry with backoff (3 attempts, 2/4/8 seconds), then error |
| `denied rights` | 403 from the external API | Rights business error, escalate, **no retry** |
| `tenant mismatch` | `aud`/`tenant_id` in the token does not match the expected value | Configuration error, **no retry** |
| `remote auth failure` | 401 after a successful login (odd case) | Clear the token cache, try to refresh, then escalate |

Stop rule: **`401` from an external API must not be turned into “failed to perform the operation.”** This is a separate error class with separate handling.

---

## Header masking

When logging HTTP traffic, sanitize headers:

```bsl
Функция ЗаголовкиДляЛога(Заголовки)
    Копия = Новый Соответствие;
    ЧувствительныеИмена = "authorization,x-api-key,cookie,set-cookie,proxy-authorization";
    Для Каждого КЗ Из Заголовки Цикл
        Если СтрНайти(ЧувствительныеИмена, НРег(КЗ.Ключ)) > 0 Тогда
            Копия.Вставить(КЗ.Ключ, "***");
        Иначе
            Копия.Вставить(КЗ.Ключ, КЗ.Значение);
        КонецЕсли;
    КонецЦикла;
    Возврат Копия;
КонецФункции
```

---

## Anti-patterns

```bsl
// ПЛОХО: пароль и логин в параметрах НаКлиенте → НаСервере.
&НаКлиенте
Процедура Подключиться(Команда)
    ПодключитьсяНаСервере(Объект.Логин, ПарольИзПоляФормы);
КонецПроцедуры
// Правильно: на сервер передаётся ссылка учётной записи; секрет читается на сервере.

// ПЛОХО: 401 трактуется как «временно недоступно».
Если Ответ.КодСостояния <> 200 Тогда
    // retry через минуту — на 401 это бесполезный цикл
КонецЕсли;

// ПЛОХО: логирование полного заголовка Authorization.
ЗаписьЖурналаРегистрации("API", , , , "Authorization=" + Запрос.Заголовки["Authorization"]);

// ПЛОХО: путь к PFX в реквизите формы или передаётся с клиента.
// Корректно: путь — серверная константа/реквизит ИБ, файл лежит на сервере 1С.
```

---

## Related topics

- [`secrets.md`](secrets.md) — where client_id/secret, refresh tokens, and container passwords live.
- [`crypto.md`](crypto.md) — certificates, GOST, CryptoPro (for signatures; for TLS - here).
- [`review-checklist.md`](review-checklist.md) — review checklist.
