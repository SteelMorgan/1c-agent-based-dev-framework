# Security Review Checklist

Stack-neutral checklist. The `reviewer` agent must follow it **without exception** if the pull request touches:

- secrets (register `БезопасноеХранилищеДанных` through `ОбщегоНазначения.*ДанныеВБезопасноеХранилище*`, passwords, tokens, API keys),
- cryptography (`МенеджерКриптографии`, `СертификатКриптографии`, digital signatures),
- authentication (OpenID/OAuth/basic/client certificate),
- rights, RLS, privileged mode,
- external integrations (`HTTPСоединение`, web services, data exchange).

If at least one item is violated, this is a blocking review comment.

---

## Secrets

- [ ] No secret (password, token, API key, refresh token, container password) is committed in code, configuration, template, constant, or catalog attribute.
- [ ] Secrets are stored via `ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище`/`ПрочитатьДанныеИзБезопасногоХранилища` with an owner link (not a string constant).
- [ ] Secret read/write operations are performed only on the server.
- [ ] `УстановитьПривилегированныйРежим(Истина)` is placed **surgically** around secret handling, not for the entire function.
- [ ] `УстановитьПривилегированныйРежим(Ложь)` is paired, inside `Попытка/Исключение`, within the same scope.
- [ ] The secret is not returned to the client (`НаКлиенте`, form, report).
- [ ] Variables holding secrets are cleared after use (`Пароль = ""`).
- [ ] Parameters of procedures that cross the client↔server boundary **do not contain** passwords/tokens/keys.

## Cryptography

- [ ] `МенеджерКриптографии` is created with explicit `ИмяПровайдера` and `ТипПровайдера` (not the default).
- [ ] The GOST version is fixed: GOST R 34.10-2012 (256 or 512); GOST-2001 is **only** for verifying old signatures.
- [ ] The certificate is searched **by fingerprint**, not as "the first one from the store", and not by SN.
- [ ] `ДействителенС` and `ДействителенДо` are checked before use.
- [ ] The private key **does not** appear in code, attribute, template, log, or export file.
- [ ] If the project includes the БСП "Electronic Signature" feature, use it instead of a custom wrapper.
- [ ] `ПараметрыПодписиCMS` are explicitly set (type, detached/attached, encoding, timestamp).
- [ ] Binary signature data is not logged in full.

## Authentication and External APIs

- [ ] The `Authorization` header is assembled on the server and is not returned to the client.
- [ ] The OAuth refresh token is overwritten via `ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище` on every access-token refresh.
- [ ] Error semantics are implemented: `missing credentials`, `expired token`, `invalid certificate`, `provider unavailable`, `denied rights`, `tenant mismatch` — each error has its own handling path.
- [ ] `401`/`403` from an external API do not become a "generic operation error" and do not enter an infinite retry loop.
- [ ] The client TLS certificate is connected via `СертификатКлиентаФайл`/`СертификатКлиентаWindows`/`СертификатКлиентаOpenSSL`, with the key path on the server.
- [ ] The 1С server time is synchronized (NTP) — critical for OIDC/JWT.

## Rights and Privileged Mode

- [ ] Privileged mode is not used as a "role switch" around business logic.
- [ ] Inside the privileged block, **only** secret/system-table handling is performed.
- [ ] The result of the privileged block does not leak into a form/report without an explicit check of the current user's rights.
- [ ] Role and RLS checks are not temporarily commented out without a ticket to restore them.
- [ ] Queries that bypass RLS (`РАЗРЕШЁННЫЕ` removed / privileged mode) are marked with a comment and justification.

## Logging and Observability

- [ ] The registration log, `Сообщить`, agent response, and exceptions **do not contain** passwords, tokens, private keys, or full `Authorization` headers.
- [ ] The HTTP request/response body is logged only after sanitizing sensitive fields (`password`, `token`, `secret`, `authorization`).
- [ ] Certificates in logs are logged as a fingerprint (this is public information), not as the full PEM/CER.
- [ ] The following are logged: correlation ID, external operation ID, response code, duration, business object ID.
- [ ] Personal data (`full name`, `SNILS`, `individual taxpayer ID`, passport details) are logged in accordance with the personal data policy or are masked.

## Temporary Data Storage

- [ ] Temporary files with sensitive data are created in the 1С server temporary files directory.
- [ ] A temporary file has an explicit deletion path (`Попытка/Исключение/КонецПопытки` with `УдалитьФайлы`).
- [ ] Size and lifetime are limited; there is no risk that the file will "stay forever" after an error.

## Tests and Fixtures

- [ ] Test data contains no real passwords, tokens, keys, or certificates.
- [ ] Test secrets are deliberately invalid synthetic values, clearly marked as test data.
- [ ] Test certificates are self-signed, not from production certification authorities.

## Final Filter Before the Agent Response

- [ ] The final message (reviewer/architect/developer response) **does not contain** secret values, tokens, or private keys.
- [ ] If the code in the PR demonstrates a secret leak, this is the **first** review comment, with all other comments after it.

---

## Related Topics

- [`secrets.md`](secrets.md), [`crypto.md`](crypto.md), [`auth.md`](auth.md) — substantive rules that this checklist relies on.
