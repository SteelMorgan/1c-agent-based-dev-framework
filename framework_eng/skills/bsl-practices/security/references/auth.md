# Authentication and External APIs

Reference for authentication schemes that appear in 1C code: OpenID/OIDC, OAuth 2.0, basic/token, client TLS certificate. Used by the `bsl-practices/security` skill.

---

## Scheme Map

| Scheme | Where used | Where secret is stored | Where the header is built |
|-------|-----------------|---------------------|--------------------------|
| OpenID / OpenID Connect | User authentication in the platform and БСП | Platform / БСП «ИнтернетПользователи» | Platform |
| OAuth 2.0 (client credentials) | 1C server → external API without user involvement | `БезопасноеХранилище` | 1C server |
| OAuth 2.0 (authorization code) | External API on behalf of the user | `БезопасноеХранилище` + refresh | 1C server |
| Basic auth | Legacy services, internal APIs | `БезопасноеХранилище` | 1C server |
| API token / API key | Simple REST APIs | `БезопасноеХранилище` | 1C server |
| Client TLS certificate (mTLS) | B2G, banks, secure integrations | OS CSP container | TLS stack, header not needed |

---

## OpenID / OpenID Connect

The platform supports OpenID as a way to authenticate information base users:

- Connection parameters are in the information base properties (via configurator/administration).
- In addition, БСП has the `ИнтернетПользователи` subsystem and related modules for authorization in published HTTP services.
- User identification is by `email`/`sub` from the ID token, mapped to the IB user.

Notes:

- OIDC discovery (`/.well-known/openid-configuration`) - check that the URL is stable and accessible from the 1C server.
- The time on the 1C server must be synchronized (NTP); drift greater than 5 minutes breaks token validation.
- Log `sub`/`email`, but **not** the ID token or the access token in full.

---

## OAuth 2.0 over `HTTPСоединение`

### Obtaining an access token (client credentials)

```bsl
Функция ПолучитьAccessТокен(УчётнаяЗапись)

    УстановитьПривилегированныйРежим(Истина);
    Попытка
        ClientId = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(УчётнаяЗапись, "client_id");
        ClientSecret = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(УчётнаяЗапись, "client_secret");
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

- The `ClientId` and `ClientSecret` variables live only in this function; do not return them outside.
- The request body with `client_secret` is **not** logged.
- The obtained `access_token` is written to `БезопасноеХранилище` with the same owner, the key `"access_token"`, and `expires_at` is written to the catalog attribute (this is public information).

### Refresh token

- When the access token expires, make a `POST /oauth2/token` with `grant_type=refresh_token` and the `refresh_token` from `БезопасноеХранилище`.
- Many providers **rotate the refresh token** on every request - be sure to overwrite it in `БезопасноеХранилище`.
- If the refresh token is revoked (`400 invalid_grant`) - this is **not** a retry, but a configuration / reauthorization error.

### Caching the access token

The token is reused until expiration (cache in session parameters or a common module - server context only), and is refreshed with a margin of about 60 seconds before `expires_at`. `expires_in` in the response is in seconds (usually 3600).

```bsl
Функция ПолучитьТокенИзКэшаИлиОбновить(УчётнаяЗапись)

    ПараметрКэша = "OAuthToken_" + Строка(УчётнаяЗапись);

    Кэш = ПараметрыСеанса[ПараметрКэша]; // или хранилище значений
    Если Кэш <> Неопределено И Кэш.СрокДействия > ТекущаяДата() + 60 Тогда
        Возврат Кэш.Токен;
    КонецЕсли;

    НовыйТокен = ПолучитьAccessТокен(УчётнаяЗапись);
    // expires_in из ответа — в секундах (обычно 3600)
    ПараметрыСеанса[ПараметрКэша] = Новый Структура(
        "Токен, СрокДействия", НовыйТокен, ТекущаяДата() + 3600);

    Возврат НовыйТокен;

КонецФункции
```

### Authorization Code (delegated access)

Used when access is needed **on behalf of a user** (Google Workspace, Microsoft 365). Implemented through a browser redirect; **not used in background 1С tasks**.

1. Form the authorization URL: `response_type=code&client_id=...&redirect_uri=...`.
2. The user opens the URL in a browser and approves access.
3. The provider redirects to `redirect_uri` with `?code=...`.
4. Exchange `code` for `access_token`: `POST /oauth2/token` with `grant_type=authorization_code`.
5. Store `refresh_token` in `БезопасноеХранилище`; refresh `access_token` via `grant_type=refresh_token`.

---

## Basic / API token

```bsl
УстановитьПривилегированныйРежим(Истина);
Попытка
    Логин = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(УчётнаяЗапись, "login");
    Пароль = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(УчётнаяЗапись, "password");
Исключение
    УстановитьПривилегированныйРежим(Ложь);
    ВызватьИсключение;
КонецПопытки;
УстановитьПривилегированныйРежим(Ложь);

Учётка = Base64Строка(ПолучитьДвоичныеДанныеИзСтроки(Логин + ":" + Пароль));
Запрос.Заголовки.Вставить("Authorization", "Basic " + Учётка);
```

Stop rules:

- The `Authorization` header is built **only on the server** and is **not returned** to the client.
- `Пароль` is cleared (`Пароль = ""`) immediately after the header is assembled.
- The request is not logged in full; for diagnostics, log only the URL, method, response code, and time.

**Alternative (Basic):** if you pass the login/password directly to the constructor `Новый HTTPСоединение(Хост, , Логин, Пароль, ...)`, the platform will add the `Authorization: Basic <base64>` header itself. The password is still read on the server.

**Static Bearer token / API key** (GitHub, Telegram, SendGrid, internal microservices): the token is taken from `БезопасноеХранилище`, and the `Authorization: Bearer <token>` header is assembled on the server. It differs from OAuth in that it is **not refreshed automatically** (issued once, changed rarely and manually).

It is convenient to name secret settings in the format `SystemPrefix.SecretType`, for example `"ExternalERP.BasicAuth"`.

---

## Client TLS certificate (mTLS)

The platform provides three variants of "client certificate" objects for `HTTPСоединение`:

| Object | When to use | Where the key lives |
|--------|-------------|---------------------|
| `СертификатКлиентаФайл` | Certificate + key in a PFX file on the 1C server | File, password-protected |
| `СертификатКлиентаWindows` | Certificate in the Windows store under the rphost account | Windows registry |
| `СертификатКлиентаOpenSSL` | Linux server, key in a PEM file | File |

Example:

```bsl
СертификатКлиента = Новый СертификатКлиентаФайл(
    ПутьКPFX,                          // path on the 1C server, not on the client
    ПрочитатьПарольКонтейнераИзБХ());  // container password from БезопасноеХранилище

ЗащищённоеСоединение = Новый ЗащищённоеСоединениеOpenSSL(СертификатКлиента, Неопределено);

Соединение = Новый HTTPСоединение(Хост, 443, , , , 30, ЗащищённоеСоединение);
```

Notes:

- The PFX/PEM path is on the 1C server; only the rphost account (`USR1CV8`) has access.
- The `.p12`/`.pfx`/`.pem` file is placed in a protected folder on the 1C server, **not** in the infobase directory; the file itself is not stored in the database.
- The PFX container password is stored in `БезопасноеХранилище`.
- Rotation: update the certificate file and the container password in `БезопасноеХранилище`; a 1C restart is not required.
- On a Linux server, OpenSSL is used - `СертификатКлиентаWindows` will not work.
- The TLS version and cipher suite are determined by the OS/OpenSSL; if the external service requires TLS 1.2+ - this is a server configuration issue, not a 1C code issue.
- Typical scenarios: SMEV, FNS EDI, the Central Bank of the Russian Federation, banking and corporate PKI systems.

---

## HMAC / request signature

The request body is signed with a secret key (HMAC-SHA256). Used in some payment gateways and marketplaces. The secret key is stored in `БезопасноеХранилище`; the signature is calculated **on the server**, and the key is not returned outside.

```bsl
УстановитьПривилегированныйРежим(Истина);
СекретныйКлюч = ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(УчётнаяЗапись, "hmac_key");
УстановитьПривилегированныйРежим(Ложь);

// Конкретная HMAC-функция зависит от платформы / подключённого компонента
ПодписьHex = КриптографическийМодуль.HMACSHA256(
    ПолучитьДвоичныеДанныеИзСтроки(ТелоЗапросаСтрокой, "UTF-8"),
    ПолучитьДвоичныеДанныеИзСтроки(СекретныйКлюч, "UTF-8"));

Запрос.Заголовки.Вставить("X-Signature", ПодписьHex);
```

---

## Error semantics

Authentication errors **must** be distinguished from validation and business errors. Minimum set:

| Code | Semantics | Response |
|-----|-----------|---------|
| `missing credentials` | Secret is not set in `БезопасноеХранилище` | Configuration error, escalate to administrator, **do not retry** |
| `expired token` | Access token has expired, refresh is available | Automatic refresh + repeat the operation |
| `invalid refresh` | Refresh was revoked/is invalid | Escalation: the owner must reauthorize |
| `invalid certificate` | Certificate expired, not found, incorrect PFX password | Configuration error, **do not retry** |
| `provider unavailable` | OIDC/OAuth/API unavailable (5xx, timeout, network) | Retry with backoff (3 attempts, 2/4/8 seconds), then error |
| `denied rights` | 403 from external API | Business rights error, escalate, **do not retry** |
| `tenant mismatch` | `aud`/`tenant_id` in the token does not match the expected value | Configuration error, **do not retry** |
| `remote auth failure` | 401 after successful login (strange case) | Clear the token cache, try to refresh, then escalate |

Stop rule: **`401` from an external API must not be turned into "failed to perform the operation"**. This is a separate error class with separate handling.

---

## Header Masking

When logging HTTP exchange, sanitize headers:

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
// BAD: password and login in parameters on Client → Server.
&НаКлиенте
Процедура Подключиться(Команда)
    ПодключитьсяНаСервере(Объект.Логин, ПарольИзПоляФормы);
КонецПроцедуры
// Correct: an account reference is passed to the server; the secret is read on the server.

// BAD: 401 is treated as "temporarily unavailable".
Если Ответ.КодСостояния <> 200 Тогда
    // retry after one minute — on 401 this is a useless loop
КонецЕсли;

// BAD: logging the full Authorization header.
ЗаписьЖурналаРегистрации("API", , , , "Authorization=" + Запрос.Заголовки["Authorization"]);

// BAD: the path to the PFX is in a form attribute or passed from the client.
// Correct: the path is a server-side constant/IB attribute, the file is stored on the 1C server.
```

---

## Comparison Table of Schemes

| Scheme | Complexity | Security | Typical scenario |
|-------|-----------|-------------|------------------|
| Basic | Low | Medium (HTTPS only) | Internal / legacy APIs |
| Bearer Token | Low | High | SaaS APIs (GitHub, Telegram) |
| OAuth 2.0 Client Credentials | Medium | High | Modern B2B REST APIs (Keycloak, Azure AD, Yandex.Cloud, 1C:Shina) |
| OAuth 2.0 Authorization Code | High | High | Access on behalf of a user (Google Workspace, M365) |
| Certificate (mTLS) | High | Very high | Government APIs, SMEV, FNS, CBR RF |
| HMAC | Medium | High | Payment gateways, marketplaces |

---

## Related topics

- [`secrets.md`](secrets.md) — where client_id/secret, refresh tokens, container passwords live.
- [`crypto.md`](crypto.md) — certificates, GOST, CryptoPro (for signing; for TLS — here).
- [`review-checklist.md`](review-checklist.md) — review checklist.
