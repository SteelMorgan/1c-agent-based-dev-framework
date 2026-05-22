# Secrets and `БезопасноеХранилище`

Reference for working with secrets in 1C: where to store them, how to read them, how to rotate them, and how to mask them in logs. Used by the `bsl-practices/security` skill.

---

## What is `БезопасноеХранилище`

Platform API for storing sensitive data (passwords, tokens, API keys, refresh tokens) **separately from configuration data**:

- Physically - separate tables in the cluster, not accessible through query language queries.
- Access - only through privileged mode, by owner.
- Secrets are backed up via a separate cluster export - account for that during migration.

Available starting with platform 8.3.9. On older versions, use BSP `Сохраняемые данные` as a fallback (not recommended for new code).

---

## API

```bsl
БезопасноеХранилище.УстановитьДанные(Владелец, Данные, Ключ = Неопределено)
БезопасноеХранилище.ПрочитатьДанные(Владелец, Ключ = Неопределено)
БезопасноеХранилище.УдалитьДанные(Владелец, Ключ = Неопределено)
```

- **Owner** - metadata object reference (usually a catalog item for accounts). Never a string constant: when the object is deleted, the secret is automatically cleaned up.
- **Data** - any serializable value (string, structure, map). For one owner, you can store several secrets under different keys.
- **Key** - a string that separates secrets for the same owner (`"access_token"`, `"refresh_token"`, `"client_secret"`).

All three methods are **server-side** and require **privileged mode** for a regular user.

---

## Storage structure: recommended pattern

For each type of external service, create a separate accounts catalog, for example:

- `Справочник.УчётныеЗаписиВнешнихСервисов` - common catalog of connections.
- Attributes: `URLСервиса`, `Логин` (open), `ИдентификаторКлиента` (open), `ТипАутентификации` (enumeration).
- Secrets: everything stored only in `БезопасноеХранилище` under these keys:
  - `password`
  - `client_secret`
  - `access_token`
  - `refresh_token`
  - `api_key`
  - `certificate_thumbprint` (thumbprint - not a secret, but convenient to store nearby)

Never create a `Пароль` or `Токен` attribute in the catalog - that is a typical leak through export, report, registration log, exchange.

---

## Writing a secret

```bsl
&НаСервере
Процедура СохранитьУчётныеДанныеНаСервере(СсылкаУчётнойЗаписи, ПарольПользователя)

    УстановитьПривилегированныйРежим(Истина);
    Попытка
        БезопасноеХранилище.УстановитьДанные(СсылкаУчётнойЗаписи, ПарольПользователя, "password");
    Исключение
        УстановитьПривилегированныйРежим(Ложь);
        ВызватьИсключение;
    КонецПопытки;
    УстановитьПривилегированныйРежим(Ложь);

КонецПроцедуры
```

Notes:

- The `ПарольПользователя` parameter comes from the client at configuration time. Immediately after `УстановитьДанные`, clear the variable (`ПарольПользователя = ""`).
- Never log the password at any step.
- Never call `УстановитьДанные` from client context - `БезопасноеХранилище` is available only on the server.

---

## Reading a secret

```bsl
&НаСервереБезКонтекста
Функция ПрочитатьПароль(СсылкаУчётнойЗаписи)

    УстановитьПривилегированныйРежим(Истина);
    Попытка
        Пароль = БезопасноеХранилище.ПрочитатьДанные(СсылкаУчётнойЗаписи, "password");
    Исключение
        УстановитьПривилегированныйРежим(Ложь);
        ВызватьИсключение;
    КонецПопытки;
    УстановитьПривилегированныйРежим(Ложь);

    Возврат Пароль; // May be Неопределено if the secret is not set

КонецФункции
```

Notes:

- Privileged mode is mandatory; otherwise, for a regular user `ПрочитатьДанные` will fail with a permissions error.
- `УстановитьПривилегированныйРежим(Ложь)` must be set **in the same scope** and handled through `Попытка/Исключение`, otherwise an exception will let the mode leak beyond the call.
- Never return the password to the client. Return the result of the business operation performed on the server with that password.

---

## Secret lifecycle

| Stage | Who does it | Where | What is critical |
|------|------------|-----|--------------|
| Source | Integration administrator | Account setup form | Masked input field, without `ОтправляемоеЗначение` in the form |
| Write | Server | `БезопасноеХранилище.УстановитьДанные` | Privileged mode, clearing the variable afterward |
| Read | Server | `БезопасноеХранилище.ПрочитатьДанные` | Privileged mode, targeted use, no leakage to the client |
| Use | Server | `HTTPСоединение`, `МенеджерКриптографии` | Header is assembled on the server and not returned to the client |
| Rotation | Scheduled job / admin manually | See below | Atomic replacement, without a window with an empty secret |
| Revocation | Delete the account | `БезопасноеХранилище.УдалитьДанные` | Automatic when the owner is deleted, but better explicitly |

---

## Secret rotation

Scenarios:

1. **Scheduled rotation of an external service password** - usually every N days.
2. **OAuth refresh token** - the refresh token changes every time a new access token is obtained, so it must also be rewritten.
3. **Compromise** - unscheduled rotation due to an incident.

Recommendations:

- The new value is written **over** the old one (`УстановитьДанные` with the same key). There should be no window with an empty secret.
- For OAuth refresh - the operation `received new access + new refresh → УстановитьДанные both keys` in one `Попытка/Исключение`; if the refresh write failed, there is no need to roll back the old access, but mark the desynchronization in the log.
- Rotation entry in the registration log: owner, key (`access_token`), `success/fail`, **without the value**.

---

## Masking in logs

What goes into logs (registration log, `Сообщить`, agent response) **only in masked form**:

- Passwords - never, even masked; do not mention the value at all.
- Tokens (`access_token`, `refresh_token`, `api_key`) - last 4 characters, everything else `***`.
- Full `Authorization` header - never; log only the scheme type (`Bearer`, `Basic`).
- HTTP request/response body - only after sanitization (fields `password`, `token`, `secret`, `authorization` are replaced with `***`).
- Certificates - thumbprint (this is public information), not the full PEM/CER.

Masking utility template:

```bsl
Функция ЗамаскироватьСекрет(Значение)
    Если ТипЗнч(Значение) <> Тип("Строка") ИЛИ ПустаяСтрока(Значение) Тогда
        Возврат "***";
    КонецЕсли;
    Если СтрДлина(Значение) <= 4 Тогда
        Возврат "***";
    КонецЕсли;
    Возврат "***" + Прав(Значение, 4);
КонецФункции
```

---

## Anti-patterns

```bsl
// ПЛОХО: пароль в реквизите справочника.
УчётнаяЗапись.Пароль = ПарольПользователя;
УчётнаяЗапись.Записать();

// ПЛОХО: пароль в константе.
Константы.ПарольИнтеграции.Установить(ПарольПользователя);

// ПЛОХО: привилегированный режим на всю функцию.
Функция ВыполнитьБизнесОперацию(Параметры) Экспорт
    УстановитьПривилегированныйРежим(Истина); // охватывает всю логику, не только секрет
    // ... 200 строк бизнес-логики ...
КонецФункции

// ПЛОХО: возврат пароля на клиент.
&НаСервереБезКонтекста
Функция ПолучитьПароль(УчётнаяЗапись) Экспорт // вызывается с клиента
    УстановитьПривилегированныйРежим(Истина);
    Возврат БезопасноеХранилище.ПрочитатьДанные(УчётнаяЗапись, "password");
КонецФункции

// ПЛОХО: пароль в журнале регистрации.
ЗаписьЖурналаРегистрации("Интеграция", , , , "Логин=" + Логин + ", пароль=" + Пароль);
```

---

## Related topics

- [`crypto.md`](crypto.md) - private keys as a special class of secrets.
- [`auth.md`](auth.md) - using secrets in authentication schemes.
- [`review-checklist.md`](review-checklist.md) - review checklist.
