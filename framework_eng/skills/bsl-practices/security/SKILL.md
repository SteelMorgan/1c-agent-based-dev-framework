---
name: security
description: "1C security: secrets, cryptography, authentication. Use when you need to design or review password/token storage, digital signatures, certificates, OpenID/OAuth, TLS, privileged mode, and log masking."
alwaysApply: false
---

# 1C Security: secrets, authentication, cryptography

This skill is a map of topics related to the security of 1C application code. Platform API specifics are moved into `references/`. Here are the trust boundaries, typical mistakes, and stop rules that any agent (architect / developer-code / reviewer) must follow when working with sensitive data.

Security in 1C is not a separate subsystem, but an end-to-end property of the code:
- Secrets live in `БезопасноеХранилище`, not in code/configuration/logs.
- Cryptography goes through `МенеджерКриптографии` + an explicit provider (CryptoPro CSP / ViPNet) with an explicit GOST version.
- Authentication (OpenID / OAuth / basic / client certificate) is a contract with explicit error modes, not "it worked / it did not work".
- Privileged mode is a targeted tool, not a "permissions off" switch for an entire module.

---

## When to use

| Trigger | Action |
|---------|--------|
| You see work with a password/token/secret in a procedure parameter or in a code string | Read `references/secrets.md`, move it into `БезопасноеХранилище`, and check masking in logs |
| You see work with `МенеджерКриптографии`, `СертификатКриптографии`, `ХранилищеСертификатовКриптографии`, or `ПараметрыПодписиCMS` | Read `references/crypto.md`, verify the provider, GOST version, context (client/server), and the lifecycle of the private key |
| You see an HTTP call to an external API, OpenID/OAuth, basic-auth, or a client TLS certificate | Read `references/auth.md`, check error semantics and credential storage |
| You are reviewing code that touches rights, RLS, privileged mode, or external integrations | Use `references/review-checklist.md` as a mandatory filter before the final response |
| You need to design a new authenticated integration/service | First define the trust boundary (see below), then choose the API |

---

## Trust boundaries

Before you write or review code, explicitly define which boundary it crosses:

1. **User → 1C server** - platform authentication, roles, RLS, data separation.
2. **1C client → 1C server** - procedure parameters cross the network boundary (see the stop rule about passwords).
3. **1C server → external API** - `HTTPСоединение`, OpenID/OAuth, client certificate, TLS.
4. **1C server → OS / cryptography provider** - `МенеджерКриптографии`, CryptoPro CSP / ViPNet, access to the certificate store under the rphost/srv1cv8 account.
5. **1C server → persistent secret storage** - `БезопасноеХранилище`, or an external vault through a connector.

If the boundary is not named, the skill has been applied incorrectly: go back to the design stage.

---

## Topics and where to look

### 1. Secrets and `БезопасноеХранилище` → [`references/secrets.md`](references/secrets.md)

- `БезопасноеХранилище.УстановитьДанные(Владелец, Данные[, Ключ])` and `ПрочитатьДанные(Владелец[, Ключ])`.
- The owner is usually a reference to the accounts catalog item; never a string constant.
- All calls are on the server, with `УстановитьПривилегированныйРежим(Истина)` applied only where needed, not for the entire module.
- Secret lifecycle: source → write → read → rotation → revocation.
- Masking in logs, the registration log, and the agent response.

### 2. Cryptography and digital signatures → [`references/crypto.md`](references/crypto.md)

- `МенеджерКриптографии(ИмяПровайдера, ТипПровайдера)` - the provider is chosen explicitly (CryptoPro CSP, ViPNet CSP).
- `СертификатКриптографии`, `ХранилищеСертификатовКриптографии`, `ПараметрыПодписиCMS`.
- GOST R 34.10-2012 (256/512 bit) is the current standard; GOST R 34.10-2001 is only for verifying old signatures.
- Private key: container on the server, accessible under the rphost account; never in a common module, never in a catalog attribute, never in `Хранилище.Значение`.
- The БСП "Electronic Signature" (`ЭлектроннаяПодпись`, `ЭлектроннаяПодписьСлужебный`) is the preferred path if БСП is present in the configuration.

### 3. Authentication and external APIs → [`references/auth.md`](references/auth.md)

- OpenID / OpenID Connect - built-in platform authentication and in БСП ("ИнтернетПользователи").
- OAuth 2.0 - on top of `HTTPСоединение`/`HTTPЗапрос`, the token and refresh-token are stored in `БезопасноеХранилище`.
- Basic / API-token - the token is stored in `БезопасноеХранилище`, and the header is assembled on the server.
- Client TLS certificate: `СертификатКлиентаФайл`, `СертификатКлиентаWindows`, `СертификатКлиентаOpenSSL` in `HTTPСоединение`.
- Error semantics: `missing credentials`, `expired token`, `invalid certificate`, `provider unavailable`, `denied rights`, `tenant mismatch`, `remote auth failure`.

### 4. Review checklist → [`references/review-checklist.md`](references/review-checklist.md)

A stack-neutral checklist that the reviewer agent must pass **before** the final response if the code touches any of the topics above.

---

## Stop rules (1C-specific)

These rules are **hard**. Violation = blocking review comment and code rewrite.

1. **A password/token/private key must not be passed through a procedure parameter that crosses a "client ↔ server" boundary**
   - Forbidden: an export procedure in a common module marked "Server, Server call" with a `Пароль` parameter.
   - Correct: on the client - only the secret owner (reference); secret reading - on the server inside privileged mode.

2. **A private key / cryptography container is not stored in a common module, catalog attribute, or constant**
   - Forbidden: a base64 key string in code, in `Константа.КлючШифрования`, or in a layout.
   - Correct: the container is in the OS store under the 1C server account, accessed through `МенеджерКриптографии`.

3. **`УстановитьПривилегированныйРежим(Истина)` around `БезопасноеХранилище` access is mandatory and must be targeted**
   - Without `УстановитьПривилегированныйРежим`, `БезопасноеХранилище.ПрочитатьДанные` will fail on permissions for a regular user.
   - `УстановитьПривилегированныйРежим(Ложь)` must be set immediately after reading, in the same `Попытка/Исключение` block.
   - Forbidden: wrapping an entire export procedure in privileged mode "just in case".

4. **Logs and the registration log do not contain tokens, passwords, private keys, or full `Authorization` headers**
   - In `ЗаписьЖурналаРегистрации`, `Сообщить`, and the agent response - only masked values (`***`, last 4 characters, certificate fingerprint).
   - HTTP request/response bodies are logged only after sanitization.

5. **An authentication error is different from a validation or business error**
   - A `401`/`403` from an external API is not turned into "operation failed". This is a separate error type with separate handling (refresh, re-login, escalation).

---

## Usage scenarios

### Scenario 1: A new integration module with OAuth

**Context:** You need to call an external REST API with OAuth 2.0 (client credentials).

**Steps:**
1. Name the trust boundary: "1C server → external API".
2. Create a catalog of credentials; `client_secret` and `refresh_token` go into `БезопасноеХранилище`, owner = a reference to the catalog item.
3. Read the secret on the server, targeted under `УстановитьПривилегированныйРежим`.
4. Build the `Authorization` header on the server; the header is not returned to the client.
5. Describe the error semantics: `expired token` → refresh, `invalid client` → configuration error (do not retry), `provider unavailable` → retry with backoff.
6. Logs: `client_id` is visible, `client_secret`/`access_token` are masked (last 4 characters).
7. Run `references/review-checklist.md`.

### Scenario 2: CMS document signing

**Context:** You need to sign XML/PDF with a qualified digital signature under GOST R 34.10-2012.

**Steps:**
1. Trust boundary: "1C server → CryptoPro CSP" (or ViPNet).
2. If the БСП "Electronic Signature" is present in the configuration, use its modules, do **not** write your own `МенеджерКриптографии` manually.
3. If БСП is absent - `МенеджерКриптографии("Crypto-Pro GOST R 34.10-2012 Cryptographic Service Provider", 80)` with an explicit `ТипПровайдера`.
4. Take the certificate by fingerprint (`Отпечаток`), not by "the first suitable one".
5. `ПараметрыПодписиCMS` (detached/attached, encoding).
6. The private key container is accessible under the rphost account - verify this in the deployment checklist, not in code.
7. Do not log `ДанныеПодписи` in full; log the certificate fingerprint and the business object identifier.

### Scenario 3: Reviewing a pull request that touches rights

**Context:** A `УстановитьПривилегированныйРежим(Истина)` appeared in the PR.

**Steps:**
1. Find the paired `УстановитьПривилегированныйРежим(Ложь)` in the same scope and inside `Попытка/Исключение`.
2. Verify that inside the privileged block there is **only** access to the secret/system table, not the whole business logic.
3. Verify that the result does not leak into the form/report without an explicit check of the current user's rights.
4. Run `references/review-checklist.md`.

---

## Examples

### Correct

```bsl
// Сервер. Чтение токена внешнего API из безопасного хранилища.
// Привилегированный режим — точечный, только вокруг чтения секрета.
Функция ПолучитьТокенДоступа(УчётнаяЗапись) Экспорт

    УстановитьПривилегированныйРежим(Истина);
    Попытка
        ДанныеСекрета = БезопасноеХранилище.ПрочитатьДанные(УчётнаяЗапись, "access_token");
    Исключение
        УстановитьПривилегированныйРежим(Ложь);
        ВызватьИсключение;
    КонецПопытки;
    УстановитьПривилегированныйРежим(Ложь);

    Если ДанныеСекрета = Неопределено Тогда
        ВызватьИсключение НСтр("ru = 'Токен не настроен для учётной записи.'");
    КонецЕсли;

    Возврат ДанныеСекрета;

КонецФункции
```

### Incorrect

```bsl
// АНТИПАТТЕРН: пароль пересекает границу клиент/сервер в открытом параметре.
// АНТИПАТТЕРН: пароль попадает в журнал регистрации.
&НаСервереБезКонтекста
Функция ОтправитьДокумент(URL, Логин, Пароль, ТелоЗапроса) Экспорт

    ЗаписьЖурналаРегистрации("Интеграция",
        УровеньЖурналаРегистрации.Информация,
        ,
        ,
        "Отправка: URL=" + URL + ", Логин=" + Логин + ", Пароль=" + Пароль);

    // ... вызов внешнего API ...

КонецФункции
```

---

## Typical mistakes

| Mistake | Consequence | How to avoid |
|--------|-------------|--------------|
| Password/token in the parameter of an export server procedure | Leakage over the network, in dumps, in client logs | Pass only the secret owner (reference), read the secret on the server |
| `УстановитьПривилегированныйРежим(Истина)` for the whole module/function | Bypassing RLS and role checks across the entire business logic | A targeted block only around `БезопасноеХранилище` access |
| Storing `access_token` in a catalog attribute / constant / layout | Any user with read access to the catalog gets the token | `БезопасноеХранилище` + owner = reference to the catalog item |
| `МенеджерКриптографии()` without an explicit provider and type | The code works on the test environment, fails in production with a different provider | Explicit `ИмяПровайдера` + `ТипПровайдера`, GOST version fixed |
| Certificate chosen as "the first one in the store" | Signature with the wrong certificate at rotation time | Search strictly by `Отпечаток` |
| `401` from an external API → `Сообщить("Ошибка при сохранении")` | It is impossible to tell an expired token from a data error | Separate authentication error type + refresh handling |
| Logging the entire HTTP request/response body | Passwords/tokens/PII are stored in the registration log | Sanitization before logging, masking sensitive fields |

---

## Related resources

- [`error-handling`](../error-handling/SKILL.md) - the general error model, including distinguishing technical and business errors.
- [`integration-patterns`](../integration-patterns/SKILL.md) - patterns for HTTP services and external integrations that authentication is attached to.
- [`ssl-patterns`](../ssl-patterns/SKILL.md) - БСП modules (including "Electronic Signature" and "ИнтернетПользователи").
- [`coding-standards`](../coding-standards/SKILL.md) - general rules for server-side code formatting.

---
depends_on: []
---
