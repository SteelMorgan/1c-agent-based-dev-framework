# Cryptography in 1C

Reference for the platform and BSP cryptographic API: providers, certificates, CMS signatures, GOST, private key lifecycle. Used by the `bsl-practices/security` skill.

---

## Architecture Diagram

1C does not perform cryptography itself — it delegates it to the **external operating system cryptographic provider**:

```
1C (МенеджерКриптографии)  ─►  Cryptographic provider (CryptoPro CSP / ViPNet CSP / OpenSSL)  ─►  OS / key container
```

From this follows:

- The provider must be **installed and licensed on the 1C server** (rphost), not on the client.
- The private key lives in the **provider container** (Windows registry, file system, token/smart card), not inside 1C.
- The account under which the 1C server runs (`USR1CV8` / `usr1cv8` or domain account) must have access to the container.
- The provider version, GOST version, and container type are fixed in the deployment documentation; in code they **must be specified explicitly**.

---

## API: `МенеджерКриптографии`

```bsl
МенеджерКриптографии = Новый МенеджерКриптографии(ИмяПровайдера, ТипПровайдера, ПутьКМодулюПровайдера = "");
```

- `ИмяПровайдера` — provider CSP name string as the OS sees it:
  - CryptoPro CSP, GOST R 34.10-2012, 256 bits: `"Crypto-Pro GOST R 34.10-2012 Cryptographic Service Provider"`
  - CryptoPro CSP, GOST R 34.10-2012, 512 bits: `"Crypto-Pro GOST R 34.10-2012 Strong Cryptographic Service Provider"`
  - ViPNet CSP, GOST R 34.10-2012: `"Infotecs Cryptographic Service Provider"` (exact name depends on version)
- `ТипПровайдера` — numeric CSP type code. For GOST-2012 in CryptoPro — `80` (256) or `81` (512). Verify with the provider, do not guess.
- `ПутьКМодулюПровайдера` — for Linux: explicit path to `.so`. Usually not needed on Windows.

Stop rule: **never call `Новый МенеджерКриптографии()` without arguments**. The default OS provider may be MS Crypto on one machine and CryptoPro on another, so behavior will no longer be reproducible.

---

## Objects

| Object | What it models |
|--------|----------------|
| `МенеджерКриптографии` | Connected CSP. Context for all operations (signature, encryption, hash). |
| `СертификатКриптографии` | X.509 certificate (public part). Created from binary data or read from the store. |
| `ХранилищеСертификатовКриптографии` | OS system store (`Personal`, `Trusted Root`, etc.). |
| `ПараметрыПодписиCMS` | CMS signature settings: type (detached/attached), encoding, time. |
| `ПараметрыШифрованияCMS` | CMS encryption settings. |
| `МенеджерСертификатовКриптографии` (БСП) | High-level BSP wrapper "Electronic Signature". |

---

## Finding a Certificate

```bsl
МенеджерКриптографии = Новый МенеджерКриптографии(
    "Crypto-Pro GOST R 34.10-2012 Cryptographic Service Provider",
    80);

Хранилище = МенеджерКриптографии.ОткрытьХранилищеСертификатов(
    ТипХранилищаСертификатовКриптографии.ПерсональныеСертификаты);

ОтпечатокЦелевой = "0123456789ABCDEF0123456789ABCDEF01234567"; // 40 hex for SHA-1
Сертификат = Неопределено;
Для Каждого ЭлементХранилища Из Хранилище.ПолучитьВсе() Цикл
    Если ВРег(ЭлементХранилища.Сертификат.Отпечаток) = ВРег(ОтпечатокЦелевой) Тогда
        Сертификат = ЭлементХранилища.Сертификат;
        Прервать;
    КонецЕсли;
КонецЦикла;

Если Сертификат = Неопределено Тогда
    ВызватьИсключение НСтр("ru = 'Сертификат с заданным отпечатком не найден.'");
КонецЕсли;
```

Rules:

- **Search only by `Отпечаток`** (`Thumbprint`). Not by CN, not by "the first in the store", not by validity period.
- The thumbprint is stored in the account directory attribute - this is public information.
- Check `Сертификат.ДействителенДо` and `Сертификат.ДействителенС` before use.

---

## CMS Signature

```bsl
Параметры = Новый ПараметрыПодписиCMS;
Параметры.ВключатьСертификаты = ВключениеСертификатовCMS.ПодписавшийСертификат;
Параметры.ТипПодписи = ТипПодписиCMS.СCMS;
Параметры.Откреплённая = Истина; // or Ложь — attached
Параметры.ВключатьВремяШтампа = Истина;

ДанныеПодписи = МенеджерКриптографии.ПодписатьCMS(
    ДвоичныеДанныеИсточника,
    Сертификат,
    Параметры);
```

Notes:

- A detached signature (`Откреплённая = Истина`) is an external `.sig`/`.p7s` file; the source remains unchanged. It is more often required in B2G exchanges.
- An attached signature stores data and signature in a single CMS container.
- Timestamping (`ВключатьВремяШтампа`) requires a TSA service; make sure the TSA URL is configured in the CSP.

---

## GOST Versions

| Standard | Key size | Status | When to use |
|----------|----------|--------|-------------|
| GOST R 34.10-2012, 256 bits | 256 | Current | Default for new signatures |
| GOST R 34.10-2012, 512 bits | 512 | Current | Stronger requirements, qualified electronic signatures in certified systems |
| GOST R 34.10-2001 | 256 | Deprecated | **Only** to verify previously created signatures. Do not issue new ones. |
| GOST R 34.11-2012 (Stribog) | 256/512 | Current | Hash function, used together with 34.10-2012 |

Stop rule: **do not mix the provider and the GOST version**. The provider "GOST R 34.10-2012 Cryptographic Service Provider" handles 256 bits; for 512 bits you need the "Strong" variant.

---

## BSP "Electronic Signature"

If the configuration has BSP, **prefer its modules** over direct work with `МенеджерКриптографии`:

- `ЭлектроннаяПодпись.СоздатьПодпись()` / `ЭлектроннаяПодпись.ПроверитьПодпись()`
- `ЭлектроннаяПодписьСлужебный.МенеджерКриптографии()` — centralized retrieval of the manager by settings.
- `ЭлектроннаяПодписьСлужебныйКлиент` — client-side part (certificate selection dialogs, PIN entry).

Benefits:

- Unified handling of provider errors, normalized codes.
- Ready-made UI for certificate selection and PIN entry.
- Support for multiple certificates and multiple providers without code duplication.
- Existing workarounds for known CSP bugs.

If BSP is not present in the project, treat it as technical debt; do not write your own "wrapper from scratch" in every module.

---

## Private Key Lifecycle

| Stage | Where | What is critical |
|------|-------|------------------|
| Generation | On the owner's workstation (HSM, token, laptop with CSP) | Never on a shared 1C application server |
| Storage | CSP container (Windows registry under the user, file, token) | Access is granted to **only** the 1C server account |
| Use | `МенеджерКриптографии.ПодписатьCMS` | On the server, never on the client via `НаКлиенте` |
| Backup | Container export, encryption, safe | Not done through 1C |
| Revocation | Request to the CA + deletion of the container + deletion of the thumbprint from the directory | Logged separately |

Stop rules:

- **The private key does not exist inside 1C as an object**. If the code contains `ДвоичныеДанные` with a private key, that is a vulnerability.
- The private key is not transferred through data exchange, not exported to a file from 1C, and not logged.

---

## Anti-Patterns

```bsl
// ПЛОХО: дефолтный провайдер.
МенеджерКриптографии = Новый МенеджерКриптографии();

// ПЛОХО: поиск сертификата «первый попавшийся».
Сертификат = Хранилище.ПолучитьВсе()[0].Сертификат;

// ПЛОХО: приватный ключ в виде строки в коде или в макете.
Перем PrivateKeyPEM Экспорт;
PrivateKeyPEM = "-----BEGIN PRIVATE KEY-----...";

// ПЛОХО: подпись на клиенте через НаКлиенте + передача результата на сервер.
// Корректно только если используется БСП с её клиентским UI выбора сертификата.

// ПЛОХО: логирование двоичных данных подписи целиком.
ЗаписьЖурналаРегистрации("ЭЦП", , , , "Подпись=" + Base64Строка(ДанныеПодписи));
```

---

## Related Topics

- [`secrets.md`](secrets.md) — certificate thumbprint in the account directory.
- [`auth.md`](auth.md) — client TLS certificate for HTTPS (a different branch of the crypto API).
- [`review-checklist.md`](review-checklist.md) — review checklist.
