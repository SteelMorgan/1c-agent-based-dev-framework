# BSP fix context (checkpoint)

## Confirmed against vendored BSP source
- ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(Владелец, Данные, Ключ = "Пароль") Экспорт
- ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(Владелец, Ключи = "Пароль", ОбщиеДанные = Неопределено) Экспорт
- ОбщегоНазначения.ПрочитатьДанныеВладельцевИзБезопасногоХранилища(Владельцы, Ключи = "Пароль", ОбщиеДанные = Неопределено) Экспорт
- ОбщегоНазначения.УдалитьДанныеИзБезопасногоХранилища(Владелец, Ключи = Неопределено) Экспорт
- Storage register: РегистрСведений.БезопасноеХранилищеДанных (privileged mode inside wrappers)
- No "#Область ПереопределяемыйИнтерфейс" region exists anywhere in BSP; *Переопределяемый modules use "#Область ПрограммныйИнтерфейс" (confirmed in ОбщегоНазначенияПереопределяемый, УправлениеДоступомПереопределяемый)
- УправлениеДоступом.ЕстьРоль(Знач Роль, Знач СсылкаНаОбъект = Неопределено, Знач Пользователь = Неопределено)
- УправлениеДоступом.ЕстьПраво(Право, СсылкаНаОбъект, Знач Пользователь = Неопределено)
- УправлениеДоступом.ЧтениеРазрешено/ИзменениеРазрешено/ПроверитьЧтениеРазрешено/ПроверитьИзменениеРазрешено
- УправлениеДоступом.НовоеОписаниеПрофиляГруппДоступа()
- УправлениеДоступом.ВключитьПрофильПользователю(Пользователь, Профиль) / ВыключитьПрофильПользователю(Пользователь, Профиль = Неопределено)
- No УправлениеДоступом.ОписаниеПрофиля() or ПроверитьДопустимостьДействия() exist.

## Task list status
1. integration-patterns/SKILL.md - DONE (synced, 11 chunks) - fixed lines 8,92,100-101,121,132-134,304,363,402,438,465
2. security/SKILL.md - DONE (synced, 3 chunks) - fixed lines 49,90,154 (90 added beyond task scope, same fictional-method class)
3. security/references/secrets.md - DONE (synced, 9 chunks) - full rewrite of API section + all call sites
4. security/references/review-checklist.md - DONE (synced, 3 chunks) - fixed lines 5,18,40
5. api-design/SKILL.md - DONE (synced, 6 chunks) - rewrote category 3 (ПереопределяемыйИнтерфейс -> Переопределяемые модули *Переопределяемый, methods in #Область ПрограммныйИнтерфейс), bsl example, compat table row, doc step, scenario 3, typical-errors table row
6. ssl-patterns/SKILL.md:128 - DONE (synced) - fixed lines 110,126,128 (УправлениеДоступом.ОписаниеПрофиля/ПроверитьДопустимостьДействия -> НовоеОписаниеПрофиляГруппДоступа/ВключитьПрофильПользователю/ЕстьПраво/ЕстьРоль); ALSO added item-12 "Версия БСП проекта" subsection (coordinator addendum) after "Версионированная карта" intro, before routing table
7. ssl-patterns/references/bsp-3.1.11/base-common.md:125 - DONE (synced) - РегистрыСведений.БезопасноеХранилище -> БезопасноеХранилищеДанных
8. ssl-patterns/references/bsp-3.1.11/prefixes.md:39-40 - DONE (synced) - typo ПрефиксУзлаРаспределеннойИнформацийБазы -> ПрефиксУзлаРаспределеннойИнформационнойБазы (matches lines 243/273 already correct)
9. ssl-patterns/references/bsp-3.1.11/forms-validation.md:133-134 - DONE (synced) - "есть право и есть право" -> "есть роль РедактированиеРеквизитовОбъектов и есть право на редактирование объекта" (confirmed against ЗапретРедактированияРеквизитовОбъектовСлужебный source: Пользователи.РолиДоступны("РедактированиеРеквизитовОбъектов") И ПравоДоступа("Редактирование", ...))
10. ssl-patterns/references/bsp-3.1.11/users-access.md:221-222 - DONE (synced) - "ошибка компиляции" -> "ошибка времени выполнения"
11. (ADDENDUM) security/references/auth.md lines 45,46,128,129,193 - DONE (synced) - БезопасноеХранилище.ПрочитатьДанные -> ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища; added privileged-mode wrap around hmac_key read
12. (ADDENDUM) ssl-patterns/SKILL.md "Версия БСП проекта" section - DONE, included in item 6's sync

## FINAL STATUS: ALL 12 ITEMS DONE AND SYNCED
`python3 tools/sync-skill.py --check` shows zero ✗ (dirty/error) entries for any file in this zone
(only unrelated pre-existing dirty entries elsewhere in the repo, e.g. framework/subagents/_template-agent.md,
belonging to other agents' work, not touched by this task).

Note: sync-skill.py uses a shared "codex slot" queue (max 4 concurrent) contended by other
parallel agents active in this repo (worktrees under /tmp/xmlgen-audit-*); some syncs took
multiple retries/backgrounding to land, but final --check confirms all synced.

Not touching: data-exchange/SKILL.md, coding-standards/SKILL.md, error-handling/SKILL.md, test-writing/**
