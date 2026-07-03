# Users and BSP access management

Subsystems **Пользователи**, **УправлениеДоступом** (and the closely related
**ВнешниеПользователи**). Cover: the current session user, role checks and
"full rights", RLS checks for reading/modifying at the record level, object
rights, access group profiles, external user (B2B portal).

## Modules

Suffix system (one root + context):

- `Пользователи` — server stable API (current user, roles,
  information base user properties, search).
- `ПользователиКлиент` — client stable API (the same "current
  user"/"full-rights user" — only for the current user).
- `ПользователиКлиентСервер` — ⚠️ **obsolete** entirely (region
  `УстаревшиеПроцедурыИФункции`): `ТекущийПользователь`, `АвторизованныйПользователь`,
  `ТекущийВнешнийПользователь`, `ЭтоСеансВнешнегоПользователя`. Use
  the server or client variant without the `КлиентСервер` suffix.
- `ВнешниеПользователи` — server stable API for external users.
- `УправлениеДоступом` — server stable API (RLS, rights, profiles, access
  groups, access value sets).
- `УправлениеДоступомПереопределяемый` — **hooks**: BSP calls it, application code
  implements it (copies the override module and overrides the body). Not
  called directly from application code.
- `ПользователиПереопределяемый` — **hooks** for the Пользователи subsystem.

⚠️ **Do not exist:** `УправлениеДоступомКлиент` (without `Служебный`) — there is no
service stable counterpart for client code of the "Access Management"
subsystem; `УправлениеДоступомСлужебныйКлиент` — ⚠️ service, without guarantees. Also
`Пользователи.СсылкаТекущегоПользователя`, `ПользователиКлиент.Авторизоваться`,
`ПользователиСлужебный.СоздатьПользователяИБ`, `УправлениеДоступом.НастройкиПрав` —
do not exist (typical "by analogy" mistakes).

## Scenarios

### 1. Get the current session user

**Task:** in server code, get the user reference to fill the «Responsible»/«Author» etc. attribute, working correctly with external users as well.

**Functions:**
`Пользователи.АвторизованныйПользователь() Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Пользователи.ТекущийПользователь() Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Пользователи.ЭтоСеансВнешнегоПользователя() Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:** no parameters. `АвторизованныйПользователь` returns
`СправочникСсылка.Пользователи` or `СправочникСсылка.ВнешниеПользователи` depending on who logged in. `ТекущийПользователь` always returns
`СправочникСсылка.Пользователи` and **throws an exception** if the login was performed by
an external user.

**Example:**
```bsl
// Universal — for code that supports external users
ТекПользователь = Пользователи.АвторизованныйПользователь();
ДокументОбъект.Ответственный = ТекПользователь;

// Code that does NOT support external users — you can call ТекущийПользователь
Если Не Пользователи.ЭтоСеансВнешнегоПользователя() Тогда
    Автор = Пользователи.ТекущийПользователь();
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `ДокументОбъект.Ответственный = ИмяПользователя();` — the platform method
  returns a string; when the name changes, references “drift away”. Use
  `АвторизованныйПользователь()` — returns a catalog reference.
- ❌ `Пользователи.СсылкаТекущегоПользователя()` — the method does **not exist**
  (compilation error). `ТекущийПользователь()` returns the reference itself.
- For client code — `ПользователиКлиент.АвторизованныйПользователь()` or
  `ПользователиКлиент.ТекущийПользователь()` (stable, current user only). ⚠️ `ПользователиКлиентСервер.ТекущийПользователь` — deprecated.
- Cache the result at the beginning of the server call and do not call the function repeatedly.

### 2. Check the user's roles and "full rights"

**Task:** check whether the user has a configuration role (or
full rights) before opening the administrative interface or performing
a privileged operation.

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
  `РолиДоступны` and server-side `ЭтоПолноправныйПользователь`. On the client,
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
    // open the admin section
КонецЕсли;
```

**Nuances / antipatterns:**
- ❌ `Если РольДоступна("ПолныеПрава") Тогда` — platform method, does not take into account
  privileged mode and full rights. Use
  `Пользователи.ЭтоПолноправныйПользователь` or `РолиДоступны`.
- ❌ Passing an array to `РолиДоступны` — the method expects a **string** separated by commas.
- `ЭтоПолноправныйПользователь` on the server accepts `Пользователь` (you can
  check an arbitrary one), on the client — only the current one.

### 3. Check RLS access to an object (read/edit)

**Task:** before executing a heavy query or writing, check at the record level
(RLS) that the current user is allowed to read/edit the object, and
if access is denied, generate an exception.

**Functions:**
`УправлениеДоступом.ЧтениеРазрешено(ОписаниеДанных, Пользователь = Неопределено) Экспорт`
— Function → Bool, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеДоступом.ИзменениеРазрешено(ОписаниеДанных, Пользователь = Неопределено) Экспорт`
— Function → Bool, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеДоступом.ПроверитьЧтениеРазрешено(ОписаниеДанных) Экспорт`
`УправлениеДоступом.ПроверитьИзменениеРазрешено(ОписаниеДанных) Экспорт`
— Procedures, throw an exception when access is denied, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ОписаниеДанных` (`СправочникСсылка` / `ДокументСсылка` / `ПланВидовХарактеристикСсылка`
  / `ПланСчетовСсылка` / `ПланВидовРасчетаСсылка` / `БизнесПроцессСсылка` / record
  key / record set / in-memory object) — for `ИзменениеРазрешено`, the in-memory
  object is checked for a new object, the DB object — for a reference.
- `Пользователь` (`СправочникСсылка.Пользователи` /
  `СправочникСсылка.ВнешниеПользователи` / `Неопределено` — current). In the
  standard (non-performance) variant, when a non-current user is specified, the
  method throws an exception.

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
- ❌ Write your own RLS filters with `Если РольДоступна("ЧтениеДокументов")
  Тогда` — this does not take record-level restrictions into account and
  diverges from the БСП security model. Delegate the check to `УправлениеДоступом`.
- In the standard variant (`ПроизводительныйВариант()` = `Ложь`), when a user
  other than the current one is specified, the methods throw an exception; the
  check applies only to the DB object. Before extended use, check
  `УправлениеДоступом.ПроизводительныйВариант()`.
- `ИзменениеРазрешено` for a reference checks the Read right at the record level and
  Change on the table as a whole; for a new object — only the in-memory object.
- Do not confuse with `ЕстьПраво` (rights on the object, see scenario 4) —
  `ЧтениеРазрешено`/`ИзменениеРазрешено` are about RLS for reading/changing.

### 4. Check object permission and role in the access group profile

**Task:** verify that the user has the configured "object permission"
(for example, "УправлениеПравами", "Чтение", "ИзменениеПапок" for a file folder),
with hierarchy taken into account, or that the user has a role in one of the
access group profiles.

**Functions:**
`УправлениеДоступом.ЕстьПраво(Право, СсылкаНаОбъект, Знач Пользователь = Неопределено) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеДоступом.ЕстьРоль(Знач Роль, Знач СсылкаНаОбъект = Неопределено, Знач Пользователь = Неопределено) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Право` (String) — the permission name as defined in the hook
  `УправлениеДоступомПереопределяемый.ПриЗаполненииВозможныхПравДляНастройкиПравОбъектов`.
- `СсылкаНаОбъект` (`СправочникСсылка` / `ПланВидовХарактеристикСсылка`) —
  a reference to the specific permission owner object (for example, a file folder), **not**
  metadata.
- `Роль` (String) — role name; `СсылкаНаОбъект` (`ЛюбаяСсылка` /
  `ТаблицаЗначений` of access value sets / `Неопределено`) — for checking the
  Read permission in access groups.
- `Пользователь` — for `ЕстьПраво`, you can pass any user; `ЕстьРоль`
  checks by access group profiles taking RLS on read into account.

**Example:**
```bsl
// Permission for a specific file folder with hierarchy taken into account
Если УправлениеДоступом.ЕстьПраво("ИзменениеПапок", ПапкаФайлов) Тогда
    // ...
КонецЕсли;

// Role in the access group profile (for the current user)
Если УправлениеДоступом.ЕстьРоль("ДобавлениеИзменениеПапокФайлов", ПапкаФайлов) Тогда
    // ...
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `УправлениеДоступом.ЕстьПраво("Чтение", Метаданные.Справочники.Файлы)` —
  runtime error: the second argument is an object reference, not metadata
  (BSL does not check argument types at compile time).
- ❌ `УправлениеДоступом.НастройкиПрав(...)` — the method does **not exist**. Permissions
  are configured through the `УправлениеДоступомПереопределяемый` hook.
- `ЕстьРоль` checks the role in access group profiles taking read RLS into account; to
  check the plain configuration role without RLS, use
  `Пользователи.РолиДоступны` (see scenario 2).

### 5. Handle an external user (B2B portal)

**Task:** in code intended for an external portal, distinguish the login of an external
user, get their reference and the authentication owner object (counterparty).

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
- ❌ Call `ВнешниеПользователи.ТекущийВнешнийПользователь()` without first checking
  `ЭтоСеансВнешнегоПользователя()` — the method throws an exception if a regular user
  logged in. First check, then call.
- `Пользователи.ТекущийПользователь()` in an external user session throws
  an exception — use `АвторизованныйПользователь()` (see scenario 1) if the
  code supports both variants.

### 6. Assign an access group profile / set user rights

**Task:** programmatically enable an access group profile for a user (for
simplified rights setup) or completely reassign their rights by the list of access
groups and user groups.

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
  of the supplied profile / `Строка` — name of the supplied profile) — for
  `ВключитьПрофильПользователю` creates/finds a personal access group and
  adds the user to it. `Неопределено` in `ВыключитьПрофильПользователю`
  — disable all profiles.
- `ГруппыДоступа` (`Массив` из `ГруппыДоступа` / `ПрофилиГруппДоступа`),
  `ГруппыПользователей` (`Массив` из `ГруппыПользователей`) — full
  reassignment of rights.

**Example:**
```bsl
// Включить профиль по имени поставляемого профиля
УправлениеДоступом.ВключитьПрофильПользователю(Пользователь, "ПрофильМенеджераПродаж");

// Полная установка прав: набор групп доступа и групп пользователей
МассивГрупп = Новый Массив;
МассивГрупп.Добавить(Справочники.ГруппыДоступа.НайтиПоНаименованию("Менеджеры"));
УправлениеДоступом.УстановитьПраваПользователя(Пользователь, МассивГрупп, Новый Массив);
```

**Nuances / anti-patterns:**
- ❌ Looking for the nonexistent `УправлениеДоступом.ДобавлениеПользователейВГруппу` —
  there is no such method. Assigning a profile is done via `ВключитьПрофильПользователю`, and a full
  reinstallation is done via `УстановитьПраваПользователя`.
- `ВключитьПрофильПользователю` works in simplified rights setup mode
  (it creates a personal access group); for non-simplified mode use
  `УстановитьПраваПользователя` with access groups.

### 7. Access override hooks (implemented by application code)

**Task:** introduce custom access types, object permissions,
shipped profiles into the configuration - through override modules.

**Functions (hooks):**
`УправлениеДоступомПереопределяемый.ПриЗаполненииВидовДоступа(ВидыДоступа) Экспорт`
`УправлениеДоступомПереопределяемый.ПриЗаполненииВозможныхПравДляНастройкиПравОбъектов(ВозможныеПрава) Экспорт`
`УправлениеДоступомПереопределяемый.ПриЗаполнениеПоставляемыхПрофилейГруппДоступа(ОписанияПрофилей, ПараметрыОбновления) Экспорт`
`УправлениеДоступомПереопределяемый.ПриИзмененииНаборовЗначенийДоступа(Ссылка, СсылкиНаЗависимыеОбъекты) Экспорт`
— all Procedures, region `#Область ПрограммныйИнтерфейс`. **Hooks**: БСП calls
them at the moments of filling access types, rights, profiles, and when access
value sets change; application code copies the override module into the
configuration and implements the body.

**Example (implementation in a copy of the override):**
```bsl
// В модуле УправлениеДоступомПереопределяемый, скопированном в конфигурацию
Процедура ПриЗаполнениеВозможныхПравДляНастройкиПравОбъектов(ВозможныеПрава) Экспорт
    // Add the "ИзменениеПапок" right for file folders
    Право = ВозможныеПрава.Строки.Добавить();
    Право.Имя = "ИзменениеПапок";
    Право.Описание = НСтр("ru = 'Изменение папок файлов'");
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Calling hooks directly from application code (`УправлениеДоступомПереопределяемый.
  ПриЗаполнениеВидовДоступа(...)`) — these are hooks: they are implemented by the application
  configuration, and БСП calls them itself. A direct call makes no sense.
- Likewise `ПользователиПереопределяемый.ПриОпределенииНазначенияРолей`,
  `ПриОпределенииНастроек` and others — hooks implemented in the integration.

## Rare methods

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures are
available via `python scripts/bsp_api.py method <Имя> [--module <М>] --src src/cf`:

- `Пользователи.НайтиПоИмени(ИмяДляВхода)` /
  `НайтиПоИдентификатору(ИдентификаторПользователяИБ)` /
  `НайтиПоСсылке(Пользователь)` — search for the information base user.
- `Пользователи.СвойстваПользователяИБ(ИмяИлиИдентификатор)` /
  `УстановитьСвойстваПользователяИБ(...)` / `УдалитьПользователяИБ(...)` —
  information base user properties. ⚠️ `ПользователиСлужебный.СоздатьПользователяИБ`
  does not exist; programmatic user creation is an implementation-level task,
  via `ЗаписатьПользователяИБ(ПользовательОбъект, ПараметрыОбработки)` in the
  service module (the `ПараметрыОбработки` format is unstable).
- `УправлениеДоступом.ОграничиватьДоступНаУровнеЗаписей()` → Boolean — whether
  RLS is enabled; `ПроизводительныйВариант()` → Boolean — the RLS variant.
- `УправлениеДоступом.ОбновитьНаборыЗначенийДоступа(СсылкаИлиОбъект,
  ОбновлениеИБ = Ложь)` — recalculation of access value sets after an object
  change.
- `УправлениеДоступом.ПраваДоступаКДанным(ОписаниеДанных,
  ДляВнешнихПользователей = Ложь, СоставПользователей = Неопределено)` —
  access rights composition for data.