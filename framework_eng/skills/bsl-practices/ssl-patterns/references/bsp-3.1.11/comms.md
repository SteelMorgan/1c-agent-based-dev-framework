# БСП Communications: mail, SMS, message templates, discussions, interactions

Five closely related subsystems: **РаботаСПочтовымиСообщениями** (sending/loading
email via SMTP/IMAP/POP3), **ОтправкаSMS** (through an external provider),
**ШаблоныСообщений** (generating emails/SMS from templates with field substitution),
**Обсуждения** (chat through the 1С:Диалог interaction system), **Взаимодействия**
(storage of incoming/outgoing emails and SMS messages in the database, linking to the subject).
They are combined because they all solve the task of “sending a message to a user”
through different channels.

## Modules

Suffix system (root = subsystem name + context):

- `РаботаСПочтовымиСообщениями` — stable server API (sending, loading,
  checks, accounts).
- `РаботаСПочтовымиСообщениямиКлиент` — stable client-side (opening forms,
  checking an account).
- `ОтправкаSMS` — stable server API (sending, statuses, settings).
- `ОтправкаSMSКлиент` — stable client-side (sending UI with settings validation).
- `ШаблоныСообщений` — stable server API (message generation,
  parameters, structure initialization).
- `ШаблоныСообщенийКлиент` — stable client-side (opening forms, selecting a template).
- `Обсуждения` — stable server API (sending messages, description,
  connection checks, mapping users of the interaction system).
- `ОбсужденияКлиент` — stable client-side (connect, disconnect,
  availability check).
- `Взаимодействия` — stable server API (storage of emails/SMS, interaction
  subject, subsystem usage).

`*Переопределяемый` modules (`РаботаСПочтовымиСообщениямиПереопределяемый`,
`ОтправкаSMSПереопределяемый`, `ШаблоныСообщенийПереопределяемый`) — **hooks**: БСП
calls them, application code implements them (copies the override module and
overrides the body). They are not called directly from application code.

⚠️ **Do not exist:** `РаботаСПочтовымиСообщениямиСервер`, `ОтправкаSMSСервер`
(the base modules are named after the subsystem without a suffix). `ШаблоныСообщенийСервер`
exists (a server wrapper variant of `ШаблоныСообщений`, e.g.
`ШаблоныСообщенийСервер.ТаблицаПараметров()`) — this is not an error.
`РаботаСПочтовымиСообщениями.ОтправитьПочтовоеСообщение` — ⚠️ obsolete (region
`УстаревшиеПроцедурыИФункции`); in new code — `ОтправитьПисьмо` /
`ОтправитьПисьма`. `ПодставитьПараметрыВШаблонСообщения` — does not exist
(an error “by analogy”); generating text from a template is done via
`ШаблоныСообщений.СформироватьСообщение`.

## Scenarios

### 1. Send email through a configured account

**Task:** programmatically send one or more emails via SMTP,
handling network errors and recipient errors.

**Functions:**
`РаботаСПочтовымиСообщениями.ОтправитьПисьмо(УчетнаяЗаписьИлиСоединение, Письмо) Экспорт`
— Function → `Структура` (`ОшибочныеПолучатели` — `Соответствие` address→error
text), region `#Область ПрограммныйИнтерфейс` (stable). Server.
`РаботаСПочтовымиСообщениями.ОтправитьПисьма(УчетнаяЗаписьИлиСоединение, Письма, ТекстОшибки = Неопределено) Экспорт`
— Function → `Соответствие` (key — `ИнтернетПочтовоеСообщение`, value —
send result), region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `УчетнаяЗаписьИлиСоединение` (`СправочникСсылка.УчетныеЗаписиЭлектроннойПочты`
  / `ИнтернетПочта` — established connection). The account password is
  automatically read from secure storage.
- `Письмо` (`ИнтернетПочтовоеСообщение`) — single; `Письма` (`Массив` из
  `ИнтернетПочтовоеСообщение`) — batch.
- `ТекстОшибки` (String) — output, error message if not all were sent. When
  partially sent, no exception is thrown — check the result.

**Example:**
```bsl
Письмо = Новый ИнтернетПочтовоеСообщение;
Письмо.Тема = НСтр("ru = 'Счёт на оплату'");
Письмо.Тексты.Добавить(НСтр("ru = 'Здравствуйте, высылаем счёт.'"));
Письмо.Получатели.Добавить(Новый ИнтернетПочтовыйАдрес("client@example.com"));

Попытка
    Результат = РаботаСПочтовымиСообщениями.ОтправитьПисьмо(УчетнаяЗапись, Письмо);
    Если Результат.ОшибочныеПолучатели.Количество() > 0 Тогда
        ОбщегоНазначения.СообщитьПользователю(
            НСтр("ru = 'Часть получателей отвергнута.'"), , , , Отказ);
    КонецЕсли;
Исключение
    ОбщегоНазначения.СообщитьПользователю(
        НСтр("ru = 'Не удалось отправить письмо.'"), , , , Отказ);
КонецПопытки;
```

**Nuances / anti-patterns:**
- ❌ Call `ОтправитьПисьмо` without `Попытка / Исключение` — the method
  is documented to potentially throw an exception on network error / invalid
  password / provider limit.
- ❌ `РаботаСПочтовымиСообщениями.ОтправитьПочтовоеСообщение(...)` — outdated
  (region `УстаревшиеПроцедурыИФункции`), do not use in new code.
- ❌ Store the account password in code or write it directly to a property
  (`Учетка.Пароль = "..."`) — this bypasses secure storage. Use
  `ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(Учетка, Пароль, "Пароль")`
  for the password.
- For mass mailings, wrap `ОтправитьПисьма` in a background job
  (`ДлительныеОперации.ВыполнитьФункцию`).

### 2. Check channel availability and get accounts

**Task:** before the UI command “Send email,” check that a configured account exists; get the list of accounts filtered by purpose.

**Functions:**
`РаботаСПочтовымиСообщениями.ДоступнаОтправкаПисем() Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`РаботаСПочтовымиСообщениями.ДоступныеУчетныеЗаписи(Знач ДляОтправки = Неопределено, Знач ДляПолучения = Неопределено, Знач ВключатьСистемнуюУчетнуюЗапись = Истина) Экспорт`
— Function → `ТаблицаЗначений` (`Ссылка / Наименование / Адрес`), region
`#Область ПрограммныйИнтерфейс` (stable). Server.
`ОтправкаSMS.ДоступнаОтправкаSMS() Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ОтправкаSMS.НастройкаОтправкиSMSВыполнена() Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ДляОтправки` (Boolean) — `Истина` — only for sending; `ДляПолучения`
  (Boolean) — `Истина` — only for receiving; both `Неопределено` — all.
- `ВключатьСистемнуюУчетнуюЗапись` (Boolean) — default `Истина`.

**Example:**
```bsl
// Before the UI command to send an email
Если РаботаСПочтовымиСообщениями.ДоступнаОтправкаПисем() Тогда
    // open the send form
КонецЕсли;

// List of accounts only for sending
ТЗ = РаботаСПочтовымиСообщениями.ДоступныеУчетныеЗаписи(Истина, Ложь);

// Before sending SMS — check provider readiness
Если ОтправкаSMS.НастройкаОтправкиSMSВыполнена() Тогда
    // can send
КонецЕсли;
```

**Nuances / anti-patterns:**
- `ДоступнаОтправкаПисем` / `ДоступнаОтправкаSMS` check both the presence of configuration
  and the current user's permissions — use them before the UI command.
- On the client, `СоздатьНовоеПисьмо` will open the setup wizard itself if there is no
  account — a separate check is not required.

### 3. Open the form for a new email on the client

**Task:** from a form command handler, open the form for a new email with
pre-filled recipients/subject/attachments, with a close notification.

**Functions:**
`РаботаСПочтовымиСообщениямиКлиент.СоздатьНовоеПисьмо(ПараметрыОтправкиПисьма = Неопределено, ОповещениеОЗакрытииФормы = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.
`РаботаСПочтовымиСообщениямиКлиент.ПараметрыОтправкиПисьма() Экспорт`
— Function → `Структура` (`Отправитель / Получатель / Тема / Текст /
Вложения / Копии / СкрытыеКопии / Предмет / …`), region `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `ПараметрыОтправкиПисьма` (Structure / `Неопределено`) — prefill the
  email.
- `ОповещениеОЗакрытииФормы` (`ОписаниеОповещения` / `Неопределено`) —
  handler for the result after the form is closed.

**Example:**
```bsl
&НаКлиенте
Процедура КомандаОтправитьПисьмо(Команда)
    Параметры = РаботаСПочтовымиСообщениямиКлиент.ПараметрыОтправкиПисьма();
    Параметры.Получатель = Контрагент;
    Параметры.Тема       = НСтр("ru = 'Документы по заказу %1'", Документ.Номер);
    РаботаСПочтовымиСообщениямиКлиент.СоздатьНовоеПисьмо(
        Параметры,
        Новый ОписаниеОповещения("ПослеОтправкиПисьма", ЭтаФорма));
КонецПроцедуры
```

**Nuances / anti-patterns:**
- If no account is configured, `СоздатьНовоеПисьмо` automatically opens the
  setup wizard - no separate check is needed.
- `ОповещениеОЗакрытииФормы` is the only way to learn the result (the form
  is asynchronous); do not block the thread waiting.

### 4. Send SMS and track delivery

**Task:** send an SMS through the configured provider (SMS4B / SMS.RU /
SMS-ЦЕНТР / Билайн / МТС) and, if needed, request delivery status.

**Functions:**
`ОтправкаSMS.ОтправитьSMS(НомераПолучателей, Знач Текст, ИмяОтправителя = Неопределено, ПеревестиВТранслит = Ложь) Экспорт`
— Function → `Структура` (`ОтправленныеСообщения` — `Массив` из `Структура`
(`НомерПолучателя / ИдентификаторСообщения`); `ОписаниеОшибки` — String, empty
= success), region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ОтправкаSMSКлиент.ОтправитьSMS(НомераПолучателей, Текст, ДополнительныеПараметры) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.
`ОтправкаSMS.СтатусДоставки(Знач ИдентификаторСообщения) Экспорт`
— Function → String (`"НеОтправлялось" / "НеОтправлено" / "Отправляется" / "Отправлено" /
"Доставлено" / "НеДоставлено" / "Ошибка"`), region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `НомераПолучателей` (`Массив из Строка`) — numbers in `+7XXXXXXXXXX` format.
- `Текст` (String) — SMS text (max length depends on the operator).
- `ИмяОтправителя` (String / `Неопределено`) — the name displayed instead of the number.
- `ПеревестиВТранслит` (Boolean) — transliterate the text before sending.
- `ДополнительныеПараметры` (Structure) — for the client variant: `ИмяОтправителя`,
  `ПеревестиВТранслит`.

**Example:**
```bsl
МассивНомеров = Новый Массив;
МассивНомеров.Добавить("+79991234567");

Если ОтправкаSMS.НастройкаОтправкиSMSВыполнена() Тогда
    Результат = ОтправкаSMS.ОтправитьSMS(МассивНомеров, "Ваш заказ подтверждён.");
    Если Не ПустаяСтрока(Результат.ОписаниеОшибки) Тогда
        ОбщегоНазначения.СообщитьПользователю(
            СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(
                НСтр("ru = 'Ошибка отправки SMS: %1'"), Результат.ОписаниеОшибки));
    Иначе
        Идентификатор = Результат.ОтправленныеСообщения[0].ИдентификаторСообщения;
        // Позже: Статус = ОтправкаSMS.СтатусДоставки(Идентификатор);
    КонецЕсли;
Иначе
    // provider is not configured — open the settings form
    ОтправкаSMSКлиент.ОткрытьФормуНастроек(Новый ОписаниеОповещения("ПослеНастройкиSMS", ЭтаФорма));
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Parsing phone number strings manually (`СтрРазделить("+7 999...", ",")`) does not
  account for the `+7XXX...` format and spaces. Normalize to `+7XXXXXXXXXX` before calling
  or take the phone number through the contact information subsystem.
- `ОтправитьSMS` when the provider is not configured does **not** throw an exception, but
  returns `ОписаниеОшибки` — easy to miss while debugging. First check
  `НастройкаОтправкиSMSВыполнена()`.
- `ОтправкаSMSКлиент.ОтправитьSMS` checks the settings itself and opens the wizard
  if they are missing — use it on the client for UI commands.

### 5. Generate a message from a template and send it

**Task:** generate an email/SMS from a template with substitution of object attributes
(document, counterparty), or send it immediately; open an interactive
template selection form.

**Functions:**
`ШаблоныСообщений.СформироватьСообщение(Шаблон, Предмет, УникальныйИдентификатор, ДополнительныеПараметры = Неопределено) Экспорт`
— Function → `Структура` (`Тема / Текст / Получатель /
ДополнительныеПараметры / Вложения`), region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ШаблоныСообщений.СформироватьСообщениеИОтправить(Шаблон, Предмет, УникальныйИдентификатор, ДополнительныеПараметры = Неопределено) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ШаблоныСообщений.ПараметрыОтправкиПисьмаПоШаблону() Экспорт`
— Function → `Структура` (`УчетнаяЗапись / ОтправитьСразу /
ПреобразовыватьHTMLДляФорматированногоДокумента / ЗначенияПараметровСКД`), region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ШаблоныСообщенийКлиент.СформироватьСообщение(ПредметСообщения, ВидСообщения, ОписаниеОповещенияОЗакрытии = Неопределено, ВладелецШаблона = Неопределено, ПараметрыСообщения = Неопределено) Экспорт`
— Procedure (opens an interactive form), region `#Область ПрограммныйИнтерфейс` (stable). Client.
`ШаблоныСообщенийКлиент.ВыбратьШаблон(Оповещение, ВидСообщения = "Письмо", ПредметШаблона = Неопределено, ВладелецШаблона = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `Шаблон` (`СправочникСсылка.ШаблоныСообщений`).
- `Предмет` (Arbitrary — types from `ОпределяемыйТип.ПредметШаблонаСообщения`).
- `УникальныйИдентификатор` (`УникальныйИдентификатор`) — for placing attachments
  in temporary storage; when called on the server without a form — any identifier.
- `ДополнительныеПараметры` (Структура) — from `ПараметрыОтправкиПисьмаПоШаблону()`;
  `УчетнаяЗапись` — account for sending; `ОтправитьСразу` (Булево, default
  `Ложь`) — `Истина` send immediately, `Ложь` — to the Outbox folder.
- `ВидСообщения` (String) — `"Письмо"` (email) or `"СообщениеSMS"`.

**Example:**
```bsl
// Сервер: сформировать и сразу отправить
ДопПараметры = ШаблоныСообщений.ПараметрыОтправкиПисьмаПоШаблону();
ДопПараметры.УчетнаяЗапись  = УчетнаяЗапись;
ДопПараметры.ОтправитьСразу = Истина;

Результат = ШаблоныСообщений.СформироватьСообщениеИОтправить(
    ШаблонСсылка,              // ссылка на шаблон
    ЗаказКлиента,              // предмет — реквизиты подставляются из него
    УникальныйИдентификатор,   // ЭтаФорма.УникальныйИдентификатор или Новый УникальныйИдентификатор()
    ДопПараметры);

// Клиент: интерактивная форма формирования
ШаблоныСообщенийКлиент.СформироватьСообщение(ЗаказКлиента, "Письмо",
    Новый ОписаниеОповещения("ПослеФормирования", ЭтаФорма));
```

**Nuances / anti-patterns:**
- ❌ Own substitution `СтрЗаменить(Шаблон.Текст, "[Номер]", Документ.Номер)` —
  the BSP templates support SCD parameters, tabular section attributes, nested
  objects, conditional formatting; manual substitution will not provide that.
- ❌ Look for `ШаблоныСообщений.ПодставитьПараметрыВШаблонСообщения` — the method **does not
  exist**. Text generation is done through `СформироватьСообщение`.
- `ПараметрыОтправкиПисьмаПоШаблону()` — a factory method with the correct keys;
  do not create the parameter structure manually.
- The hook `ШаблоныСообщенийПереопределяемый.ПриФормированииСообщения(Сообщение,
  НазначениеШаблона, ПредметСообщения, ПараметрыШаблона)` — is implemented in the
  extension for custom generation logic; it is not called directly.

### 6. Send a message to a discussion (1С:Диалог)

**Task:** send a message to a user/group through the interaction system,
with a link to an object (contextual discussion) or without it.

**Functions:**
`Обсуждения.ОписаниеСообщения(Знач Текст) Экспорт`
— Function → `Структура` (`Текст` — `ФорматированнаяСтрока` / `Вложения` —
`Массив` / `Данные` / `Действия`), region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Обсуждения.ОписаниеВложения(Поток, Наименование) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Обсуждения.ОтправитьСообщение(Знач Автор, Знач Получатели, Сообщение, ОбсуждениеКонтекст = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Обсуждения.ОбсужденияДоступны() Экспорт`
— Function → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ОбсужденияКлиент.ПоказатьПодключение(ОписаниеЗавершения = Неопределено) Экспорт`
`ОбсужденияКлиент.ПоказатьОтключение() Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `Текст` (String / `ФорматированнаяСтрока`) — message text.
- `Автор` (`СправочникСсылка.Пользователи` /
  `ПользовательСистемыВзаимодействия`).
- `Получатели` (`Массив` of `СправочникСсылка.Пользователи` /
  `ПользовательСистемыВзаимодействия`).
- `Сообщение` (Structure — from `ОписаниеСообщения`).
- `ОбсуждениеКонтекст` (`ЛюбаяСсылка` — contextual discussion, linked to the
  object; `ИдентификаторОбсужденияСистемыВзаимодействия` — into an existing one;
  `Неопределено` — non-group 1:1 when there is one recipient, group when
  there are several).

**Example:**
```bsl
Если Не Обсуждения.ОбсужденияДоступны() Тогда
    Возврат;  // система взаимодействия не подключена
КонецЕсли;

Описание = Обсуждения.ОписаниеСообщения(
    НСтр("ru = 'Документ согласован, можно приступать к отгрузке.'"));
// Описание.Вложения.Добавить(Обсуждения.ОписаниеВложения(ПотокФайла, "akt.pdf"));

МассивПолучателей = Новый Массив;
МассивПолучателей.Добавить(Ответственный);

Обсуждения.ОтправитьСообщение(
    Пользователи.ТекущийПользователь(),  // автор
    МассивПолучателей,                    // получатели
    Описание,                             // сообщение (структура, не строка!)
    ЗаказКлиента);                        // контекст — обсуждение привязано к документу
```

**Nuances / antipatterns:**
- ❌ `Обсуждения.ОтправитьСообщение(Автор, Получатели, "Привет!")` — `Сообщение`
  has type `Структура` (from `ОписаниеСообщения`), a plain string will not work.
- `ОтправитьСообщение` throws an exception if sending fails —
  wrap it in `Попытка / Исключение` if needed.
- Before sending, check `ОбсужденияДоступны()` (the method checks
  `ИспользованиеДоступно` of the interaction system and whether it is not blocked
  by the administrator).
- Connecting/disconnecting discussions from the UI — `ОбсужденияКлиент.ПоказатьПодключение`
  / `ПоказатьОтключение`.

### 7. Interactions: storing emails and SMS in the database

**Task:** enable storing incoming/outgoing emails and SMS in the database as
interaction objects linked to the subject (document, counterparty).

**Functions:**
`Взаимодействия.ИспользуетсяПочтовыйКлиент() Экспорт`
`Взаимодействия.ИспользуютсяПрочиеВзаимодействия() Экспорт`
— Functions → Boolean, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Взаимодействия.УстановитьИспользованиеПочтовогоКлиента(Знач Значение) Экспорт`
`Взаимодействия.УстановитьИспользованиеПрочегоВзаимодействия(Знач Значение) Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Взаимодействия.ПредметВзаимодействия(Взаимодействие) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Значение` (Булево) — enable/disable subsystem usage.
- `Взаимодействие` (`СправочникСсылка` / `ДокументСсылка` interaction) — for
  `ПредметВзаимодействия` returns the subject to which it is linked.

**Example:**
```bsl
// Проверить, хранятся ли письма в базе
Если Взаимодействия.ИспользуетсяПочтовыйКлиент() Тогда
    // отправленные через РаботаСПочтовымиСообщениями письма
    // сохраняются как объекты взаимодействия и привязываются к предмету
КонецЕсли;

// Предмет, к которому привязано письмо-взаимодействие
Предмет = Взаимодействия.ПредметВзаимодействия(ПисьмоСсылка);
```

**Nuances / anti-patterns:**
- ⚠️ Most low-level `Взаимодействия` methods (creating emails, SMS,
  contacts, email search) are in the `СлужебныйПрограммныйИнтерфейс` /
  `СлужебныеПроцедурыИФункции` region; backward compatibility is not guaranteed. For
  sending, use the stable `РаботаСПочтовымиСообщениями.ОтправитьПисьмо`
  (when the mail client is enabled, the email will be saved as an interaction automatically).
- `Взаимодействия.СоздатьПисьмо` / `СоздатьИОтправитьСообщениеSMS` — service
  (`СлужебныйПрограммныйИнтерфейс`); do not use as the primary way —
  stable equivalent: `ШаблоныСообщений.СформироватьСообщениеИОтправить` or
  `РаботаСПочтовымиСообщениями.ОтправитьПисьмо`.

## Rare Methods

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures are available via
`python scripts/bsp_api.py method <Имя> [--module <М>] --src src/cf`:

- `РаботаСПочтовымиСообщениями.УчетнаяЗаписьНастроена(УчетнаяЗапись,
  Знач ДляОтправки = Неопределено, Знач ДляПолучения = Неопределено)` — whether the specified account is configured.
- `РаботаСПочтовымиСообщениями.ПодготовитьПисьмо(УчетнаяЗапись,
  ПараметрыПисьма)` / `ПодключениеКПочте(Знач УчетнаяЗапись, Знач ДляПолучения = Ложь)` —
  low-level preparation of the message/connection.
- `ОтправкаSMS.СтатусыДоставки(Знач ИдентификаторыСообщений)` — ⚠️
  `СлужебныйПрограммныйИнтерфейс`; batch status request (stable equivalent —
  `СтатусДоставки` one by one).
- `ШаблоныСообщений.ОписаниеПараметровШаблона()` / `ПараметрыШаблона(Шаблон)` /
  `ТаблицаПараметров()` — template description and parameters.
- `ШаблоныСообщений.ИнициализироватьСтруктуруСообщения()` /
  `ИнициализироватьСтруктуруПолучатели()` — factory structures for manual
  composition.
- `Обсуждения.ПользовательСистемыВзаимодействия(Пользователь,
  ТолькоИдентификатор = Ложь)` / `ПользовательИнформационнойБазы(...)` /
  `ПользователиСистемыВзаимодействия(...)` — mapping between IB users and
  Interaction System users.
- Hooks `*Переопределяемый` (`РаботаСПочтовымиСообщениямиПереопределяемый.ПослеОтправкиПисьма`,
  `ОтправкаSMSПереопределяемый.ОтправитьSMS`,
  `ШаблоныСообщенийПереопределяемый.ПриФормированииСообщения`) — implemented in
  the implementation, called by БСП itself; do not call directly.