# Users and access management of БСП

Subsystems **Пользователи**, **УправлениеДоступом** (and the closely related
**ВнешниеПользователи**). Cover: current session user, role checks and "full rights", RLS read/modify checks at the record level, object permissions, access group profiles, external user (B2B portal).

## Modules

Suffix system (one root + context):

- `Пользователи` — server stable API (current user, roles,
  IB user properties, search).
- `ПользователиКлиент` — client stable API (the same "current
  user"/"full-rights" — only for the current user).
- `ПользователиКлиентСервер` — ⚠️ **deprecated** entirely (region
  `УстаревшиеПроцедурыИФункции`): `ТекущийПользователь`, `АвторизованныйПользователь`,
  `ТекущийВнешнийПользователь`, `ЭтоСеансВнешнегоПользователя`. Use the
  server or client variant without the `КлиентСервер` suffix.
- `ВнешниеПользователи` — server stable API for external users.
- `УправлениеДоступом` — server stable API (RLS, permissions, profiles, access
  groups, access value sets).
- `УправлениеДоступомПереопределяемый` — **hooks**: БСП calls it, application code
  implements it (copies the override module and overrides the body). Not
  called directly from application code.
- `ПользователиПереопределяемый` — **hooks** for the Пользователи subsystem.

⚠️ **Do not exist:** `УправлениеДоступомКлиент` (without `Служебный`) — for client
code of the "Управление доступом" subsystem, there is no service stable analogue;
`УправлениеДоступомСлужебныйКлиент` — ⚠️ service, without guarantees. Also
`Пользователи.СсылкаТекущегоПользователя`, `ПользователиКлиент.Авторизоваться`,
`ПользователиСлужебный.СоздатьПользователяИБ`, `УправлениеДоступом.НастройкиПрав` —
do not exist (typical "by analogy" mistakes).

## Scenarios

### 1. Get the current session user

**Task:** in server-side code, get the user reference to populate the
“Responsible”/“Author” field, etc., while correctly working with external
users as well.

**Functions:**
`Пользователи.АвторизованныйПользователь() Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Пользователи.ТекущийПользователь() Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Пользователи.ЭтоСеансВнешнегоПользователя() Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:** no parameters. `АвторизованныйПользователь` returns
`СправочникСсылка.Пользователи` or `СправочникСсылка.ВнешниеПользователи` depending on who signed in. `ТекущийПользователь` always returns
`СправочникСсылка.Пользователи` and **throws an exception** if an external
user signed in.

**Example:**
```bsl
// Универсально — для кода, поддерживающего внешних пользователей
ТекПользователь = Пользователи.АвторизованныйПользователь();
ДокументОбъект.Ответственный = ТекПользователь;

// Код, который НЕ поддерживает внешних пользователей — можно звать ТекущийПользователь
Если Не Пользователи.ЭтоСеансВнешнегоПользователя() Тогда
    Автор = Пользователи.ТекущийПользователь();
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `ДокументОбъект.Ответственный = ИмяПользователя();` — the platform method
  returns a string; when the name changes, references “move away”. Use
  `АвторизованныйПользователь()` — returns a catalog reference.
- ❌ `Пользователи.СсылкаТекущегоПользователя()` — the method **does not exist**
  (compilation error). `ТекущийПользователь()` returns a reference itself.
- For client code - `ПользователиКлиент.АвторизованныйПользователь()` or
  `ПользователиКлиент.ТекущийПользователь()` (stable, current user only). ⚠️ `ПользователиКлиентСервер.ТекущийПользователь` - obsolete.
- Cache the result at the beginning of the server call and do not call the function again.

### 2. Check user roles and "full rights"

**Task:** check whether a user has a configuration role (or full rights) before opening the administrative interface or performing a privileged operation.

**Functions:**
`Пользователи.РолиДоступны(ИменаРолей, Пользователь = Неопределено, УчитыватьПривилегированныйРежим = Истина) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Пользователи.ЭтоПолноправныйПользователь(Пользователь = Неопределено, ПроверятьПраваАдминистрированияСистемы = Ложь, УчитыватьПривилегированныйРежим = Истина) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ПользователиКлиент.ЭтоПолноправныйПользователь(ПроверятьПраваАдминистрированияСистемы = Ложь) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `ИменаРолей` (String) — role names separated by commas (not an array). Returns
  `Истина` if at least one is available; for a full-rights user — `Истина`
  when `УчитыватьПривилегированныйРежим = Истина`.
- `Пользователь` (`СправочникСсылка.Пользователи` / `ВнешниеПользователи` /
  `ПользовательИнформационнойБазы` / `Неопределено` — current) — for
  `РолиДоступны` and the server-side `ЭтоПолноправныйПользователь`. On the client,
  only the current one is checked.
- `ПроверятьПраваАдминистрированияСистемы` (Boolean) — `Истина` — check not
  only `ПолныеПрава`, but also `АдминистраторСистемы`.

**Example:**
```bsl
// Server: check several roles for an arbitrary user
Если Пользователи.РолиДоступны("ДобавлениеИзменениеСправочников,ЧтениеКадровыхДанных", ПользовательСсылка) Тогда
    // ...
КонецЕсли;

// Server: full rights + system administrator
Если Пользователи.ЭтоПолноправныйПользователь(, Истина) Тогда
    ОткрытьФорму("Обработка.НастройкиПрограммы.Форма");
КонецЕсли;

// Client: current only, without specifying a user
Если ПользователиКлиент.ЭтоПолноправныйПользователь(Истина) Тогда
    // open admin section
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `Если РольДоступна("ПолныеПрава") Тогда` — platform method, does not account for
  privileged mode and full rights. Use
  `Пользователи.ЭтоПолноправныйПользователь` or `РолиДоступны`.
- ❌ Passing an array to `РолиДоступны` — the method expects a **string** with commas.
- `ЭтоПолноправныйПользователь` on the server accepts `Пользователь` (you can
  check an arbitrary one), on the client — only the current one.

### 3. Check RLS access to an object (read/change)

**Task:** before executing a heavy query or writing, check at the record level
(RLS) that the current user is allowed to read/change the object, and
if denied, raise an exception.

**Functions:**
`УправлениеДоступом.ЧтениеРазрешено(ОписаниеДанных, Пользователь = Неопределено) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеДоступом.ИзменениеРазрешено(ОписаниеДанных, Пользователь = Неопределено) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеДоступом.ПроверитьЧтениеРазрешено(ОписаниеДанных) Экспорт`
`УправлениеДоступом.ПроверитьИзменениеРазрешено(ОписаниеДанных) Экспорт`
— Procedures, raise an exception if denied, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ОписаниеДанных` (`СправочникСсылка` / `ДокументСсылка` / `ПланВидовХарактеристикСсылка`
  / `ПланСчетовСсылка` / `ПланВидовРасчетаСсылка` / `БизнесПроцессСсылка` / key
  record / record set / in-memory object) — for `ИзменениеРазрешено`, the in-memory object is checked for a new object, the DB object — for the reference.
- `Пользователь` (`СправочникСсылка.Пользователи` /
  `СправочникСсылка.ВнешниеПользователи` / `Неопределено` — current). In the
  standard (non-performance) variant, when specifying a user other than the current
  one, the method raises an exception.

**Example:**
```bsl
// Soft check — branching
Если Не УправлениеДоступом.ЧтениеРазрешено(СсылкаНаДокумент) Тогда
    ВызватьИсключение СтрШаблон("Чтение документа %1 запрещено", СсылкаНаДокумент);
КонецЕсли;

// Hard check — automatic exception when denied
УправлениеДоступом.ПроверитьЧтениеРазрешено(СсылкаНаДокумент);

// Before writing — change check
УправлениеДоступом.ПроверитьИзменениеРазрешено(ДокументОбъект);
```

**Nuances / anti-patterns:**
- ❌ Writing your own RLS filters via `Если РольДоступна("ЧтениеДокументов")
  Тогда` — this does not take record-level restrictions into account and diverges from the
  БСП security model. Delegate the check to `УправлениеДоступом`.
- In the standard variant (`ПроизводительныйВариант()` = `Ложь`) when specifying
  a user other than the current one, the methods raise an exception; the check
  applies only to the DB object. Before advanced use, check
  `УправлениеДоступом.ПроизводительныйВариант()`.
- `ИзменениеРазрешено` for a reference checks the Read right at the record level and
  Change right for the table as a whole; for a new object, only the in-memory object.
- Do not confuse this with `ЕстьПраво` (object rights, see scenario 4) — `ЧтениеРазрешено`/
  `ИзменениеРазрешено` are about RLS for read/change.

### 4. Check object permission and role in an access group profile

**Task:** check that the user has an configured "object permission"
(for example, "УправлениеПравами", "Чтение", "ИзменениеПапок" for a file folder) taking
hierarchy into account, or that they have a role in one of the access group profiles.

**Functions:**
`УправлениеДоступом.ЕстьПраво(Право, СсылкаНаОбъект, Знач Пользователь = Неопределено) Экспорт`
— Function → Bool, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеДоступом.ЕстьРоль(Знач Роль, Знач СсылкаНаОбъект = Неопределено, Знач Пользователь = Неопределено) Экспорт`
— Function → Bool, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Право` (String) — the permission name as specified in the hook
  `УправлениеДоступомПереопределяемый.ПриЗаполненииВозможныхПравДляНастройкиПравОбъектов`.
- `СсылкаНаОбъект` (`СправочникСсылка` / `ПланВидовХарактеристикСсылка`) —
  a reference to the specific object that owns the permissions (for example, a file folder), **not**
  metadata.
- `Роль` (String) — role name; `СсылкаНаОбъект` (`ЛюбаяСсылка` /
  `ТаблицаЗначений` of access value sets / `Неопределено`) — for checking
  the Read permission in access groups.
- `Пользователь` — for `ЕстьПраво` you can pass any user; `ЕстьРоль`
  checks access group profiles taking RLS into account for reading.

**Example:**
```bsl
// Permission on a specific file folder taking hierarchy into account
Если УправлениеДоступом.ЕстьПраво("ИзменениеПапок", ПапкаФайлов) Тогда
    // ...
КонецЕсли;

// Role in an access group profile (for the current user)
Если УправлениеДоступом.ЕстьРоль("ДобавлениеИзменениеПапокФайлов", ПапкаФайлов) Тогда
    // ...
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `УправлениеДоступом.ЕстьПраво("Чтение", Метаданные.Справочники.Файлы)` —
  compilation error: the second argument is an object reference, not metadata.
- ❌ `УправлениеДоступом.НастройкиПрав(...)` — the method does **not exist**. Permissions
  are configured through the `УправлениеДоступомПереопределяемый` hook.
- `ЕстьРоль` checks the role in access group profiles taking RLS into account for reading; to
  check the "bare" configuration role without RLS, use
  `Пользователи.РолиДоступны` (see scenario 2).

### 5. Handle an external user (B2B portal)

**Task:** in code intended for an external portal, distinguish an external
user login, obtain their reference and the authorization owner object (counterparty).

**Functions:**
`ВнешниеПользователи.ИспользоватьВнешнихПользователей() Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ВнешниеПользователи.ТекущийВнешнийПользователь() Экспорт`
— Function → `СправочникСсылка.ВнешниеПользователи`, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ВнешниеПользователи.ПолучитьОбъектАвторизацииВнешнегоПользователя(ВнешнийПользователь = Неопределено) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ВнешнийПользователь` (`СправочникСсылка.ВнешниеПользователи` /
  `Неопределено` — current) — for `ПолучитьОбъектАвторизацииВнешнегоПользователя`.

**Example:**
```bsl
Если ВнешниеПользователи.ИспользоватьВнешнихПользователей()
   И Пользователи.ЭтоСеансВнешнегоПользователя() Тогда
    ТекВнешний = ВнешниеПользователи.ТекущийВнешнийПользователь();
    Контрагент  = ВнешниеПользователи.ПолучитьОбъектАвторизацииВнешнегоПользователя(ТекВнешний);
    // ... работаем с Контрагентом
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Call `ВнешниеПользователи.ТекущийВнешнийПользователь()` without first
  checking `ЭтоСеансВнешнегоПользователя()` — the method throws an exception if the login
  was performed by a regular user. First check, then call.
- `Пользователи.ТекущийПользователь()` in an external user session throws
  an exception — use `АвторизованныйПользователь()` (see scenario 1) if the
  code supports both variants.

### 6. Assign an access group profile / set user rights

**Task:** programmatically enable a user access group profile (for
simplified rights configuration) or completely reassign their rights by a list
of access groups and user groups.

**Functions:**
`УправлениеДоступом.ВключитьПрофильПользователю(Пользователь, Профиль) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеДоступом.ВыключитьПрофильПользователю(Пользователь, Профиль = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеДоступом.УстановитьПраваПользователя(Пользователь, ГруппыДоступа, ГруппыПользователей) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Пользователь` (`СправочникСсылка.Пользователи` /
  `СправочникСсылка.ВнешниеПользователи`).
- `Профиль` (`СправочникСсылка.ПрофилиГруппДоступа` / `УникальныйИдентификатор`
  of a supplied profile / `Строка` — name of a supplied profile) — for
  `ВключитьПрофильПользователю` creates/finds a personal access group and
  adds the user to it. `Неопределено` in `ВыключитьПрофильПользователю`
  means disable all profiles.
- `ГруппыДоступа` (`Массив` of `ГруппыДоступа` / `ПрофилиГруппДоступа`),
  `ГруппыПользователей` (`Массив` of `ГруппыПользователей`) — complete
  rights reassignment.

**Example:**
```bsl
// Enable a profile by the name of a supplied profile
УправлениеДоступом.ВключитьПрофильПользователю(Пользователь, "ПрофильМенеджераПродаж");

// Full rights setup: a set of access groups and user groups
МассивГрупп = Новый Массив;
МассивГрупп.Добавить(Справочники.ГруппыДоступа.НайтиПоНаименованию("Менеджеры"));
УправлениеДоступом.УстановитьПраваПользователя(Пользователь, МассивГрупп, Новый Массив);
```

**Nuances / anti-patterns:**
- ❌ Look for a non-existent `УправлениеДоступом.ДобавлениеПользователейВГруппу` —
  there is no such method. Assigning a profile is through `ВключитьПрофильПользователю`, a full
  reassignment is through `УстановитьПраваПользователя`.
- `ВключитьПрофильПользователю` works in the simplified rights setup mode
  (it creates a personal access group); for the non-simplified mode use
  `УстановитьПраваПользователя` with access groups.

### 7. Access override hooks (implemented by application code)

**Task:** extend the configuration with custom access types, object rights,
shipped profiles - via override modules.

**Functions (hooks):**
`УправлениеДоступомПереопределяемый.ПриЗаполненииВидовДоступа(ВидыДоступа) Экспорт`
`УправлениеДоступомПереопределяемый.ПриЗаполненииВозможныхПравДляНастройкиПравОбъектов(ВозможныеПрава) Экспорт`
`УправлениеДоступомПереопределяемый.ПриЗаполнениеПоставляемыхПрофилейГруппДоступа(ОписанияПрофилей, ПараметрыОбновления) Экспорт`
`УправлениеДоступомПереопределяемый.ПриИзмененииНаборовЗначенийДоступа(Ссылка, СсылкиНаЗависимыеОбъекты) Экспорт`
— all Procedures, region `#Область ПрограммныйИнтерфейс`. **Hooks**: БСП calls
them at the moments of filling access types, rights, profiles, and when access value
sets change; application code copies the override module into the
configuration and implements the body.

**Example (implementation in a copy of the override):**
```bsl
// In the module УправлениеДоступомПереопределяемый, copied into the configuration
Процедура ПриЗаполнениеВозможныхПравДляНастройкиПравОбъектов(ВозможныеПрава) Экспорт
    // Add the right "ИзменениеПапок" for file folders
    Право = ВозможныеПрава.Строки.Добавить();
    Право.Имя = "ИзменениеПапок";
    Право.Описание = НСтр("ru = 'Изменение папок файлов'");
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Call hooks directly from application code (`УправлениеДоступомПереопределяемый.
  ПриЗаполнениеВидовДоступа(...)`) - these are hooks: they are implemented by the application
  configuration, БСП calls them itself. A direct call makes no sense.
- Likewise `ПользователиПереопределяемый.ПриОпределенииНазначенияРолей`,
  `ПриОпределенииНастроек` and others - hooks implemented in the customization.

## Rare methods

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures are available
via `python scripts/bsp_api.py method <Имя> [--module <М>] --src src/cf`:

- `Пользователи.НайтиПоИмени(ИмяДляВхода)` /
  `НайтиПоИдентификатору(ИдентификаторПользователяИБ)` /
  `НайтиПоСсылке(Пользователь)` — find an IB user.
- `Пользователи.СвойстваПользователяИБ(ИмяИлиИдентификатор)` /
  `УстановитьСвойстваПользователяИБ(...)` / `УдалитьПользователяИБ(...)` —
  IB user properties. ⚠️ `ПользователиСлужебный.СоздатьПользователяИБ`
  does not exist; programmatic user creation is an integration-level task,
  via `ЗаписатьПользователяИБ(ПользовательОбъект, ПараметрыОбработки)` in the
  service module (the `ПараметрыОбработки` format is unstable).
- `УправлениеДоступом.ОграничиватьДоступНаУровнеЗаписей()` → Boolean — whether
  RLS is enabled; `ПроизводительныйВариант()` → Boolean — the RLS variant.
- `УправлениеДоступом.ОбновитьНаборыЗначенийДоступа(СсылкаИлиОбъект,
  ОбновлениеИБ = Ложь)` — recalculate access value sets after an object change.
- `УправлениеДоступом.ПраваДоступаКДанным(ОписаниеДанных,
  ДляВнешнихПользователей = Ложь, СоставПользователей = Неопределено)` —
  access rights composition for data.