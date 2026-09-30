# Users and Access Management in БСП

Subsystems **Пользователи**, **УправлениеДоступом** (and the closely related
**ВнешниеПользователи**). Cover: current session user, role checks and "full rights", RLS checks for read/modify at the record level, object rights, access group profiles, external user (B2B portal).

## Modules

Suffix system (one root + context):

- `Пользователи` — server stable API (current user, roles,
  information base user properties, search).
- `ПользователиКлиент` — client stable API (the same "current
  user"/"full rights" — only for the current user).
- `ПользователиКлиентСервер` — ⚠️ **deprecated** entirely (region
  `УстаревшиеПроцедурыИФункции`): `ТекущийПользователь`, `АвторизованныйПользователь`,
  `ТекущийВнешнийПользователь`, `ЭтоСеансВнешнегоПользователя`. Use
  the server or client variant without the `КлиентСервер` suffix.
- `ВнешниеПользователи` — server stable API for external users.
- `УправлениеДоступом` — server stable API (RLS, rights, profiles, access
  groups, access value sets).
- `УправлениеДоступомПереопределяемый` — **hooks**: БСП calls this, application code
  implements it (copies the override module and overrides the body). Not
  called directly from application code.
- `ПользователиПереопределяемый` — **hooks** of the Пользователи subsystem.

⚠️ **Do not exist:** `УправлениеДоступомКлиент` (without `Служебный`) — for client
code of the "Access Management" subsystem, there is no service stable analogue;
`УправлениеДоступомСлужебныйКлиент` — ⚠️ service, without guarantees. Also
`Пользователи.СсылкаТекущегоПользователя`, `ПользователиКлиент.Авторизоваться`,
`ПользователиСлужебный.СоздатьПользователяИБ`, `УправлениеДоступом.НастройкиПрав` —
nonexistent (typical "by analogy" mistakes).

## Scenarios

### 1. Get the current session user

**Task:** in server code, get the user reference for substitution into the
`Ответственный`/`Автор` and similar fields, while correctly working with external
users as well.

**Functions:**
`Пользователи.АвторизованныйПользователь() Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Пользователи.ТекущийПользователь() Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Пользователи.ЭтоСеансВнешнегоПользователя() Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:** no parameters. `АвторизованныйПользователь` returns
`СправочникСсылка.Пользователи` or `СправочникСсылка.ВнешниеПользователи`
depending on who signed in. `ТекущийПользователь` always returns
`СправочникСсылка.Пользователи` and **throws an exception** if an external
user signed in.

**Example:**
```bsl
// Universal — for code that supports external users
ТекПользователь = Пользователи.АвторизованныйПользователь();
ДокументОбъект.Ответственный = ТекПользователь;

// Code that does NOT support external users — can call ТекущийПользователь
Если Не Пользователи.ЭтоСеансВнешнегоПользователя() Тогда
    Автор = Пользователи.ТекущийПользователь();
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `ДокументОбъект.Ответственный = ИмяПользователя();` — the platform method
  returns a string; when the name changes, references get out of sync. Use
  `АвторизованныйПользователь()` — returns a catalog reference.
- ❌ `Пользователи.СсылкаТекущегоПользователя()` — the method does **not exist**
  (compile error). `ТекущийПользователь()` returns the reference itself.
- For client code — `ПользователиКлиент.АвторизованныйПользователь()` or
  `ПользователиКлиент.ТекущийПользователь()` (stable, current user only). ⚠️ `ПользователиКлиентСервер.ТекущийПользователь` — deprecated.
- Cache the result at the start of the server call and do not call the function again.

### 2. Check the user's roles and "full rights"

**Task:** check whether the user has a configuration role (or full rights) before opening the administrative interface or performing a privileged operation.

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
  only the current user is checked.
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

// Client: only the current user, without specifying a user
Если ПользователиКлиент.ЭтоПолноправныйПользователь(Истина) Тогда
    // open the admin section
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `Если РольДоступна("ПолныеПрава") Тогда` — platform method, does not take into
  account privileged mode and full rights. Use
  `Пользователи.ЭтоПолноправныйПользователь` or `РолиДоступны`.
- ❌ Passing an array to `РолиДоступны` — the method expects a **string** separated
  by commas.
- `ЭтоПолноправныйПользователь` on the server accepts `Пользователь` (an arbitrary
  one can be checked), on the client — only the current one.

### 3. Check RLS access to an object (read/change)

**Task:** before running an expensive query or writing, check at the record level (RLS) that the current user is allowed to read/change the object, and if access is denied, raise an exception.

**Functions:**
`УправлениеДоступом.ЧтениеРазрешено(ОписаниеДанных, Пользователь = Неопределено) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеДоступом.ИзменениеРазрешено(ОписаниеДанных, Пользователь = Неопределено) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеДоступом.ПроверитьЧтениеРазрешено(ОписаниеДанных) Экспорт`
`УправлениеДоступом.ПроверитьИзменениеРазрешено(ОписаниеДанных) Экспорт`
— Procedures, raise an exception when access is denied, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ОписаниеДанных` (`СправочникСсылка` / `ДокументСсылка` / `ПланВидовХарактеристикСсылка`
  / `ПланСчетовСсылка` / `ПланВидовРасчетаСсылка` / `БизнесПроцессСсылка` /
  `ЗадачаСсылка` / `ПланОбменаСсылка` / record key / record set / object in
  memory) — for `ИзменениеРазрешено`, an object in memory is checked for a new
  object, and a database object is checked by reference.
- `Пользователь` (`СправочникСсылка.Пользователи` /
  `СправочникСсылка.ВнешниеПользователи` / `Неопределено` — current). In the
  standard (non-performance) variant, when a user other than the current one is
  specified, the method raises an exception.

**Example:**
```bsl
// Мягкая проверка — ветвление
Если Не УправлениеДоступом.ЧтениеРазрешено(СсылкаНаДокумент) Тогда
    ВызватьИсключение СтрШаблон("Чтение документа %1 запрещено", СсылкаНаДокумент);
КонецЕсли;

// Жёсткая проверка — автоматическое исключение при запрете
УправлениеДоступом.ПроверитьЧтениеРазрешено(СсылкаНаДокумент);

// Перед записью — проверка изменения
УправлениеДоступом.ПроверитьИзменениеРазрешено(ДокументОбъект);
```

**Nuances / anti-patterns:**
- ❌ Writing your own RLS filters with `Если РольДоступна("ЧтениеДокументов")
  Тогда` — this does not account for record-level restrictions and diverges from
  the БСП security model. Delegate the check to `УправлениеДоступом`.
- In the standard variant (`ПроизводительныйВариант()` = `Ложь`), when a user
  other than the current one is specified, the methods raise an exception; the
  check is performed only for the database object. Before extended use, check
  `УправлениеДоступом.ПроизводительныйВариант()`.
- `ИзменениеРазрешено` for a reference checks the Read right at the record
  level and Change for the table as a whole; for a new object, only the object in
  memory.
- Do not confuse this with `ЕстьПраво` (object permissions, see scenario 4) - `ЧтениеРазрешено`/
  `ИзменениеРазрешено` are about RLS for read/change.

### 4. Check object right and role in an access group profile

**Task:** verify that the user has an “object right” configured
(for example, “УправлениеПравами”, “Чтение”, “ИзменениеПапок” for a file folder)
with hierarchy taken into account, or that they have a role in one of the access
group profiles.

**Functions:**
`УправлениеДоступом.ЕстьПраво(Право, СсылкаНаОбъект, Знач Пользователь = Неопределено) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`УправлениеДоступом.ЕстьРоль(Знач Роль, Знач СсылкаНаОбъект = Неопределено, Знач Пользователь = Неопределено) Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Право` (String) — the name of the right, as specified in the hook
  `УправлениеДоступомПереопределяемый.ПриЗаполненииВозможныхПравДляНастройкиПравОбъектов`.
- `СсылкаНаОбъект` (`СправочникСсылка` / `ПланВидовХарактеристикСсылка`) —
  a reference to the specific object that owns the rights (for example, a file folder), **not**
  metadata.
- `Роль` (String) — role name; `СсылкаНаОбъект` (`ЛюбаяСсылка` /
  `ТаблицаЗначений` of access value sets / `Неопределено`) — for checking
  the Read right in access groups.
- `Пользователь` — for `ЕстьПраво`, any user can be passed; `ЕстьРоль`
  checks access group profiles taking RLS on read into account.

**Example:**
```bsl
// Право на конкретную папку файлов с учётом иерархии
Если УправлениеДоступом.ЕстьПраво("ИзменениеПапок", ПапкаФайлов) Тогда
    // ...
КонецЕсли;

// Роль в профиле групп доступа (для текущего пользователя)
Если УправлениеДоступом.ЕстьРоль("ДобавлениеИзменениеПапокФайлов", ПапкаФайлов) Тогда
    // ...
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ `УправлениеДоступом.ЕстьПраво("Чтение", Метаданные.Справочники.Файлы)` —
  compilation error: the second argument is an object reference, not metadata.
- ❌ `УправлениеДоступом.НастройкиПрав(...)` — the method **does not exist**. Rights
  are configured through the hook `УправлениеДоступомПереопределяемый`.
- `ЕстьРоль` checks the role in access group profiles taking RLS on read into account; to
  check the “bare” configuration role without RLS, use
  `Пользователи.РолиДоступны` (see scenario 2).

### 5. Handle an external user (B2B portal)

**Task:** in code intended for an external portal, distinguish the login of an external user, obtain their reference and the authorization owner object (counterparty).

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
- ❌ Call `ВнешниеПользователи.ТекущийВнешнийПользователь()` without first checking `ЭтоСеансВнешнегоПользователя()` — the method throws an exception if the login was performed by a regular user. First the check, then the call.
- `Пользователи.ТекущийПользователь()` in an external user session throws an exception — use `АвторизованныйПользователь()` (see scenario 1) if the code supports both variants.

### 6. Assign an access group profile / set user permissions

**Task:** programmatically enable a user access group profile (for simplified permission setup) or completely reassign their permissions by a list of access groups and user groups.

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
- `ГруппыДоступа` (`Массив` of `ГруппыДоступа` / `ПрофилиГруппДоступа`),
  `ГруппыПользователей` (`Массив` of `ГруппыПользователей`) — full
  reassignment of permissions.

**Example:**
```bsl
// Enable the profile by the name of the supplied profile
УправлениеДоступом.ВключитьПрофильПользователю(Пользователь, "ПрофильМенеджераПродаж");

// Full permission assignment: a set of access groups and user groups
МассивГрупп = Новый Массив;
МассивГрупп.Добавить(Справочники.ГруппыДоступа.НайтиПоНаименованию("Менеджеры"));
УправлениеДоступом.УстановитьПраваПользователя(Пользователь, МассивГрупп, Новый Массив);
```

**Nuances / anti-patterns:**
- ❌ Look for the nonexistent `УправлениеДоступом.ДобавлениеПользователейВГруппу` —
  there is no such method. Assigning a profile is done through `ВключитьПрофильПользователю`, and a full
  reassignment is done through `УстановитьПраваПользователя`.
- `ВключитьПрофильПользователю` works in the simplified rights setup mode
  (it creates a personal access group); for the non-simplified mode, use
  `УстановитьПраваПользователя` with access groups.

### 7. Access override hooks (implemented by application code)

**Task:** introduce custom access types, object rights,
supplied profiles into the configuration through override modules.

**Functions (hooks):**
`УправлениеДоступомПереопределяемый.ПриЗаполненииВидовДоступа(ВидыДоступа) Экспорт`
`УправлениеДоступомПереопределяемый.ПриЗаполненииВозможныхПравДляНастройкиПравОбъектов(ВозможныеПрава) Экспорт`
`УправлениеДоступомПереопределяемый.ПриЗаполнениеПоставляемыхПрофилейГруппДоступа(ОписанияПрофилей, ПараметрыОбновления) Экспорт`
`УправлениеДоступомПереопределяемый.ПриИзмененииНаборовЗначенийДоступа(Ссылка, СсылкиНаЗависимыеОбъекты) Экспорт`
— all Procedures, region `#Область ПрограммныйИнтерфейс`. **Hooks**: БСП calls
them at the moments of filling access types, rights, profiles, and when access
value sets change; application code copies the override module into the
configuration and implements the body.

**Example (implementation in a copy of the override module):**
```bsl
// В модуле УправлениеДоступомПереопределяемый, скопированном в конфигурацию
Процедура ПриЗаполнениеВозможныхПравДляНастройкиПравОбъектов(ВозможныеПрава) Экспорт
    // Добавить право "ИзменениеПапок" для папок файлов
    Право = ВозможныеПрава.Строки.Добавить();
    Право.Имя = "ИзменениеПапок";
    Право.Описание = НСтр("ru = 'Изменение папок файлов'");
КонецПроцедуры
```

**Nuances / anti-patterns:**
- ❌ Call hooks directly from application code (`УправлениеДоступомПереопределяемый.
  ПриЗаполнениеВидовДоступа(...)`) — these are hooks: they are implemented by the application
  configuration, and БСП calls them itself. A direct call makes no sense.
- Similarly, `ПользователиПереопределяемый.ПриОпределенииНазначенияРолей`,
  `ПриОпределенииНастроек`, and others — hooks implemented in the customization.

## Rare Methods

Other stable methods (region `ProgrammaticInterface`), full signatures —
via `python scripts/bsp_api.py method <Имя> [--module <М>] --src src/cf`:

- `Пользователи.НайтиПоИмени(ИмяДляВхода)` /
  `НайтиПоИдентификатору(ИдентификаторПользователяИБ)` /
  `НайтиПоСсылке(Пользователь)` — find a database user.
- `Пользователи.СвойстваПользователяИБ(ИмяИлиИдентификатор)` /
  `УстановитьСвойстваПользователяИБ(...)` / `УдалитьПользователяИБ(...)` —
  database user properties. ⚠️ `ПользователиСлужебный.СоздатьПользователяИБ`
  does not exist; programmatic user creation is an implementation-level task,
  via `ЗаписатьПользователяИБ(ПользовательОбъект, ПараметрыОбработки)` in the
  service module (the `ПараметрыОбработки` format is unstable).
- `УправлениеДоступом.ОграничиватьДоступНаУровнеЗаписей()` → Boolean — whether
  RLS is enabled; `ПроизводительныйВариант()` → Boolean — the RLS variant.
- `УправлениеДоступом.ОбновитьНаборыЗначенийДоступа(СсылкаИлиОбъект,
  ОбновлениеИБ = Ложь)` — recalculate access value sets after an object change.
- `УправлениеДоступом.ПраваДоступаКДанным(ОписаниеДанных,
  ДляВнешнихПользователей = Ложь, СоставПользователей = Неопределено)` —
  the composition of data access rights.