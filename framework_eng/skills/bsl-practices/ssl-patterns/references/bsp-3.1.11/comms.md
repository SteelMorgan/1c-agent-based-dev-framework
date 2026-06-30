# BSP communications: mail, SMS, message templates, discussions, interactions

Five tightly related subsystems: **РаботаСПочтовымиСообщениями** (sending/downloading
email via SMTP/IMAP/POP3), **ОтправкаSMS** (through an external provider),
**ШаблоныСообщений** (building emails/SMS from templates with field substitution),
**Обсуждения** (chat through the 1С:Диалог interaction system), **Взаимодействия**
(storing incoming/outgoing emails and SMS messages in the database, linked to a subject).
They are grouped together because all of them solve the task “send a message to a user”
through different channels.

## Modules

Suffix-based system (root = subsystem name + context):

- `РаботаСПочтовымиСообщениями` — stable server API (sending, downloading,
  checks, accounts).
- `РаботаСПочтовымиСообщениямиКлиент` — stable client API (opening forms,
  account check).
- `ОтправкаSMS` — stable server API (sending, statuses, settings).
- `ОтправкаSMSКлиент` — stable client API (send UI with settings validation).
- `ШаблоныСообщений` — stable server API (message generation,
  parameters, structure initialization).
- `ШаблоныСообщенийКлиент` — stable client API (opening forms, selecting a template).
- `Обсуждения` — stable server API (sending messages, description,
  connection checks, mapping interaction system users).
- `ОбсужденияКлиент` — stable client API (connect, disconnect,
  availability check).
- `Взаимодействия` — stable server API (storing emails/SMS, interaction subject,
  subsystem usage).

`*Переопределяемый` modules (`РаботаСПочтовымиСообщениямиПереопределяемый`,
`ОтправкаSMSПереопределяемый`, `ШаблоныСообщенийПереопределяемый`) are **hooks**: BSP
calls them, application code implements them (copies the override module and
overrides the body). They are not called directly from application code.

⚠️ **Do not exist:** `РаботаСПочтовымиСообщениямиСервер`, `ОтправкаSMSСервер`
(the base modules are named after the subsystem without a suffix). `ШаблоныСообщенийСервер`
does exist (a server-side wrapper variant of `ШаблоныСообщений`, for example
`ШаблоныСообщенийСервер.ТаблицаПараметров()`) — this is not an error.
`РаботаСПочтовымиСообщениями.ОтправитьПочтовоеСообщение` is ⚠️ obsolete (region
`УстаревшиеПроцедурыИФункции`); in new code use `ОтправитьПисьмо` /
`ОтправитьПисьма`. `ПодставитьПараметрыВШаблонСообщения` does not exist
(an “analogy-based” mistake); text generation from a template is done through
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
  read automatically from the secure storage.
- `Письмо` (`ИнтернетПочтовоеСообщение`) — a single message; `Письма` (`Массив` of
  `ИнтернетПочтовоеСообщение`) — a batch.
- `ТекстОшибки` (String) — output, error message if not everything was sent.
  With partial sending, an exception is **not** thrown - check the result.

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
- ❌ Call `ОтправитьПисьмо` without `Попытка / Исключение` - the method
  is documented to be able to throw on network error / invalid password /
  provider limit.
- ❌ `РаботаСПочтовымиСообщениями.ОтправитьПочтовоеСообщение(...)` is obsolete
  (region `УстаревшиеПроцедурыИФункции`), do not use it in new code.
- ❌ Store the account password in code or write it directly into a field
  (`Учетка.Пароль = "..."`) - this bypasses secure storage. Password is set via
  `ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(Учетка, Пароль, "Пароль")`.
- For mass mailings, wrap `ОтправитьПисьма` in a background job
  (`ДлительныеОперации.ВыполнитьФункцию`).

### 2. Check channel availability and get accounts

**Task:** before the UI command “Send email”, check that a configured
account exists; get the list of accounts filtered by purpose.

**Functions:**
`РаботаСПочтовымиСообщениями.ДоступнаОтправкаПисем() Экспорт`
— Function → Bool, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`РаботаСПочтовымиСообщениями.ДоступныеУчетныеЗаписи(Знач ДляОтправки = Неопределено, Знач ДляПолучения = Неопределено, Знач ВключатьСистемнуюУчетнуюЗапись = Истина) Экспорт`
— Function → `ТаблицаЗначений` (`Ссылка / Наименование / Адрес`), region
`#Область ПрограммныйИнтерфейс` (stable). Server.
`ОтправкаSMS.ДоступнаОтправкаSMS() Экспорт`
— Function → Bool, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ОтправкаSMS.НастройкаОтправкиSMSВыполнена() Экспорт`
— Function → Bool, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `ДляОтправки` (Bool) — `Истина` = only for sending; `ДляПолучения`
  (Bool) — `Истина` = only for receiving; both `Неопределено` = all.
- `ВключатьСистемнуюУчетнуюЗапись` (Bool) — default is `Истина`.

**Example:**
```bsl
// Перед UI-командой отправки письма
Если РаботаСПочтовымиСообщениями.ДоступнаОтправкаПисем() Тогда
    // открыть форму отправки
КонецЕсли;

// Список учётных записей только для отправки
ТЗ = РаботаСПочтовымиСообщениями.ДоступныеУчетныеЗаписи(Истина, Ложь);

// Перед отправкой SMS — проверить готовность провайдера
Если ОтправкаSMS.НастройкаОтправкиSMSВыполнена() Тогда
    // можно отправлять
КонецЕсли;
```

**Nuances / anti-patterns:**
- `ДоступнаОтправкаПисем` / `ДоступнаОтправкаSMS` check both whether settings are
  present and whether the current user has rights - use them before a UI command.
- On the client, `СоздатьНовоеПисьмо` will open the setup wizard automatically if
  no account is available - a separate check is not required.

### 3. Open the form for a new email on the client

**Task:** from a form command handler, open the new email form with
pre-filled recipients/subject/attachments, and a close notification.

**Functions:**
`РаботаСПочтовымиСообщениямиКлиент.СоздатьНовоеПисьмо(ПараметрыОтправкиПисьма = Неопределено, ОповещениеОЗакрытииФормы = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.
`РаботаСПочтовымиСообщениямиКлиент.ПараметрыОтправкиПисьма() Экспорт`
— Function → `Структура` (`Отправитель / Получатель / Тема / Текст /
Вложения / Копии / СкрытыеКопии / Предмет / …`), region `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `ПараметрыОтправкиПисьма` (Structure / `Неопределено`) — pre-fill
  the message.
- `ОповещениеОЗакрытииФормы` (`ОписаниеОповещения` / `Неопределено`) —
  result handler after the form closes.

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
- `ОповещениеОЗакрытииФормы` is the only way to learn the result (the form is
  asynchronous); do not block the thread waiting.

### 4. Send SMS and track delivery

**Task:** send SMS through a configured provider (SMS4B / SMS.RU /
SMS-ЦЕНТР / Beeline / MTS) and, if necessary, request delivery status.

**Functions:**
`ОтправкаSMS.ОтправитьSMS(НомераПолучателей, Знач Текст, ИмяОтправителя = Неопределено, ПеревестиВТранслит = Ложь) Экспорт`
— Function → `Структура` (`ОтправленныеСообщения` — `Массив` of `Структура`
(`НомерПолучателя / ИдентификаторСообщения`); `ОписаниеОшибки` — String, empty
= success), region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ОтправкаSMSКлиент.ОтправитьSMS(НомераПолучателей, Текст, ДополнительныеПараметры) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.
`ОтправкаSMS.СтатусДоставки(Знач ИдентификаторСообщения) Экспорт`
— Function → String (`"НеОтправлялось" / "Отправляется" / "Отправлено" /
"Доставлено" / "НеДоставлено" / "Ошибка"`), region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `НомераПолучателей` (`Массив` of String) — numbers in `+7XXXXXXXXXX` format.
- `Текст` (String) — SMS text (max length depends on the carrier).
- `ИмяОтправителя` (String / `Неопределено`) — name displayed instead of the number.
- `ПеревестиВТранслит` (Bool) — transliterate the text before sending.
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
    // провайдер не настроен — открыть форму настроек
    ОтправкаSMSКлиент.ОткрытьФормуНастроек(Новый ОписаниеОповещения("ПослеНастройкиSMS", ЭтаФорма));
КонецЕсли;
```

**Nuances / anti-patterns:**
- ❌ Parse phone numbers manually (`СтрРазделить("+7 999...", ",")`) - this does not
  account for `+7XXX...` format and spaces. Normalize to `+7XXXXXXXXXX` before the call
  or take the phone via the contact information subsystem.
- `ОтправитьSMS` does **not** throw an exception when the provider is not configured,
  but returns `ОписаниеОшибки` - easy to miss while debugging. First check
  `НастройкаОтправкиSMSВыполнена()`.
- `ОтправкаSMSКлиент.ОтправитьSMS` checks settings itself and opens the wizard if
  none are available - use it on the client for UI commands.

### 5. Generate a message from a template and send it

**Task:** generate an email/SMS from a template with field substitution for a
subject (document, counterparty), or send it immediately; open the interactive
template selection form.

**Functions:**
`ШаблоныСообщений.СформироватьСообщение(Шаблон, Предмет, УникальныйИдентификатор, ДополнительныеПараметры = Неопределено) Экспорт`
— Function → `Структура` (`Тема / Текст / Получатель / Вложения /
СообщенияПользователю`), region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ШаблоныСообщений.СформироватьСообщениеИОтправить(Шаблон, Предмет, УникальныйИдентификатор, ДополнительныеПараметры = Неопределено) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ШаблоныСообщений.ПараметрыОтправкиПисьмаПоШаблону() Экспорт`
— Function → `Структура` (`УчетнаяЗапись / ОтправитьСразу /
ПреобразовыватьHTMLДляФорматированногоДокумента`), region `#Область ПрограммныйИнтерфейс` (stable). Server.
`ШаблоныСообщенийКлиент.СформироватьСообщение(ПредметСообщения, ВидСообщения, ОписаниеОповещенияОЗакрытии = Неопределено, ВладелецШаблона = Неопределено, ПараметрыСообщения = Неопределено) Экспорт`
— Procedure (opens the interactive form), region `#Область ПрограммныйИнтерфейс` (stable). Client.
`ШаблоныСообщенийКлиент.ВыбратьШаблон(Оповещение, ВидСообщения = "Письмо", ПредметШаблона = Неопределено, ВладелецШаблона = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Client.

**Parameters:**
- `Шаблон` (`СправочникСсылка.ШаблоныСообщений`).
- `Предмет` (Arbitrary - types from `ОпределяемыйТип.ПредметШаблонаСообщения`).
- `УникальныйИдентификатор` (`УникальныйИдентификатор`) — for placing attachments
  in temporary storage; when called on the server without a form, any identifier.
- `ДополнительныеПараметры` (Structure) — from `ПараметрыОтправкиПисьмаПоШаблону()`;
  `УчетнаяЗапись` — account for sending; `ОтправитьСразу` (Bool, default
  `Ложь`) — `Истина` sends immediately, `Ложь` — to the Outbox folder.
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
- ❌ Your own substitution `СтрЗаменить(Шаблон.Текст, "[Номер]", Документ.Номер)` —
  BSP templates support SKD parameters, tabular section fields, nested
  objects, conditional formatting; manual substitution will not provide that.
- ❌ Search for `ШаблоныСообщений.ПодставитьПараметрыВШаблонСообщения` — the method
  does **not** exist. Text generation is done through `СформироватьСообщение`.
- `ПараметрыОтправкиПисьмаПоШаблону()` is a factory method with the correct keys;
  do not create the parameter structure manually.
- The hook `ШаблоныСообщенийПереопределяемый.ПриФормированииСообщения(Сообщение,
  НазначениеШаблона, ПредметСообщения, ПараметрыШаблона)` is implemented in the
  application for custom generation logic; it is not called directly.

### 6. Send a message in discussions (1С:Диалог)

**Task:** send a message to a user/group through the interaction system, linked
to an object (context discussion) or without that link.

**Functions:**
`Обсуждения.ОписаниеСообщения(Знач Текст) Экспорт`
— Function → `Структура` (`Текст` — `ФорматированнаяСтрока` / `Вложения` —
`Массив` / `Данные` / `Действия`), region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Обсуждения.ОписаниеВложения(Поток, Наименование) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Обсуждения.ОтправитьСообщение(Знач Автор, Знач Получатели, Сообщение, ОбсуждениеКонтекст = Неопределено) Экспорт`
— Procedure, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Обсуждения.ОбсужденияДоступны() Экспорт`
— Function → Bool, region `#Область ПрограммныйИнтерфейс` (stable). Server.
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
- `ОбсуждениеКонтекст` (`ЛюбаяСсылка` — context discussion, linked to an
  object; `ИдентификаторОбсужденияСистемыВзаимодействия` — into an existing one;
  `Неопределено` — non-group 1:1 with one recipient, group with several).

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

**Nuances / anti-patterns:**
- ❌ `Обсуждения.ОтправитьСообщение(Автор, Получатели, "Привет!")` — `Сообщение`
  must be a `Структура` (from `ОписаниеСообщения`), a plain string will not pass.
- `ОтправитьСообщение` throws an exception if sending fails —
  wrap it in `Попытка / Исключение` when needed.
- Before sending, check `ОбсужденияДоступны()` (the method checks
  `ИспользованиеДоступно` of the interaction system and the absence of an
  administrator block).
- Connection/disconnection of discussions from the UI is via
  `ОбсужденияКлиент.ПоказатьПодключение` / `ПоказатьОтключение`.

### 7. Interactions: store emails and SMS in the database

**Task:** enable storage of incoming/outgoing emails and SMS in the database as
interaction objects linked to a subject (document, counterparty).

**Functions:**
`Взаимодействия.ИспользуетсяПочтовыйКлиент() Экспорт`
`Взаимодействия.ИспользуютсяПрочиеВзаимодействия() Экспорт`
— Functions → Bool, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Взаимодействия.УстановитьИспользованиеПочтовогоКлиента(Знач Значение) Экспорт`
`Взаимодействия.УстановитьИспользованиеПрочегоВзаимодействия(Знач Значение) Экспорт`
— Procedures, region `#Область ПрограммныйИнтерфейс` (stable). Server.
`Взаимодействия.ПредметВзаимодействия(Взаимодействие) Экспорт`
— Function, region `#Область ПрограммныйИнтерфейс` (stable). Server.

**Parameters:**
- `Значение` (Bool) — enable/disable subsystem usage.
- `Взаимодействие` (`СправочникСсылка` / `ДокументСсылка` interaction) — for
  `ПредметВзаимодействия` returns the linked subject.

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
- ⚠️ Most low-level `Взаимодействия` methods (creating email, SMS,
  contacts, email search) are in the `СлужебныйПрограммныйИнтерфейс` /
  `СлужебныеПроцедурыИФункции` region; backward compatibility is not guaranteed.
  For sending, use the stable `РаботаСПочтовымиСообщениями.ОтправитьПисьмо`
  (when the mail client is enabled, the message will be saved as an interaction automatically).
- `Взаимодействия.СоздатьПисьмо` / `СоздатьИОтправитьСообщениеSMS` are service
  methods (`СлужебныйПрограммныйИнтерфейс`); do not use them as the main way —
  stable alternatives are `ШаблоныСообщений.СформироватьСообщениеИОтправить` or
  `РаботаСПочтовымиСообщениями.ОтправитьПисьмо`.

## Rare methods

Other stable methods (region `ПрограммныйИнтерфейс`), full signatures are
available through `python scripts/bsp_api.py method <Имя> [--module <М>] --src src/cf`:

- `РаботаСПочтовымиСообщениями.УчетнаяЗаписьНастроена(УчетнаяЗапись,
  ДляОтправки, ДляПолучения)` — whether a specific account is configured.
- `РаботаСПочтовымиСообщениями.ПодготовитьПисьмо(УчетнаяЗапись,
  ПараметрыПисьма)` / `ПодключениеКПочте(УчетнаяЗапись, ДляПолучения = Ложь)` —
  low-level preparation of a message/connection.
- `ОтправкаSMS.СтатусыДоставки(ИдентификаторыСообщений)` — ⚠️
  `СлужебныйПрограммныйИнтерфейс`; batch status request (stable alternative —
  `СтатусДоставки` one by one).
- `ШаблоныСообщений.ОписаниеПараметровШаблона()` / `ПараметрыШаблона(Шаблон)` /
  `ТаблицаПараметров()` — template description and parameters.
- `ШаблоныСообщений.ИнициализироватьСтруктуруСообщения()` /
  `ИнициализироватьСтруктуруПолучатели()` — structure factories for manual
  generation.
- `Обсуждения.ПользовательСистемыВзаимодействия(Пользователь,
  ТолькоИдентификатор = Ложь)` / `ПользовательИнформационнойБазы(...)` /
  `ПользователиСистемыВзаимодействия(...)` — mapping between information base users and
  interaction system users.
- Hooks `*Переопределяемый` (`РаботаСПочтовымиСообщениямиПереопределяемый.ПослеОтправкиПисьма`,
  `ОтправкаSMSПереопределяемый.ОтправитьSMS`,
  `ШаблоныСообщенийПереопределяемый.ПриФормированииСообщения`) — implemented in the
  application, BSP calls them itself; do not call directly.
