---
name: ssl-patterns
description: "Before implementing logic on БСП, check the ready-made mechanisms; use versioned БСП 3.1.11 references for exact modules, signatures, stable/service/deprecated API, override hooks, connectable commands, printing, files, access, exchanges, background jobs, and other subsystems"
uses_capabilities:
  - get_signature_help
alwaysApply: false
metadata:
  bsp_reference_version: "БСП 3.1.11"
  borrowed_from: "https://github.com/brake71/1c-ssl-skills"
  borrowed_commit: "85783eececb3a658ea15fc793b095ac370b5339c"
  borrowed_at: "2026-06-30"
  borrowed_table: "references/bsp-borrowings.md"
---

# БСП patterns for working with БСП (Standard Subsystems Library)

БСП code has been tested on millions of installations, is updated centrally, and is familiar to other developers. Duplicating БСП is an anti-pattern.

> **БСП function signatures are available via `get_signature_help`.** Functions in `ОбщегоНазначения` and
> other БСП modules have many parameters and overloads; do not guess the order or composition of the arguments.
> At the call site, `get_signature_help(uri, line, character)` shows the parameters and overloads
> of the called method in place, without opening the БСП module definition. Use it when calling
> any function from the catalog below if you are unsure about the signature.

## Versioned БСП 3.1.11 map

This skill contains a borrowed reference layer for БСП 3.1.11:

- `references/bsp-3.1.11/*.md` — scenario-based reference guides for БСП subsystems: module, method, signature, API region, example, nuances, and anti-patterns.
- `scripts/bsp_api.py` — local search for a method/module in the `src/cf` configuration dump with `#Область` region detection.
- `references/bsp-borrowings.md` — borrowing table: upstream version, relationship to our skills, and the weight of the additional knowledge.

If the task concerns a specific БСП subsystem, first open the corresponding reference from the table below. If the reference and the current configuration differ, the source of truth is the configuration sources and validation via `get_signature_help` / `scripts/bsp_api.py`.

### БСП project version

The reference layer corresponds to БСП 3.1.11. The БСП version in a specific project may differ.

- Before using a method from the reference, determine the project's БСП version: `Описание.Версия` is filled in the procedure `ПриДобавленииПодсистемы(Описание)` of the `ОбновлениеИнформационнойБазыБСП` module (or grep through the vendored configuration sources, usually `src/xml/CommonModules/`).
- If the version is below 3.1.11, verify the existence of the method with grep in the project's `CommonModules` before writing code.
- For БСП generation 2.x, use the reference only as a conceptual guide (which subsystem is responsible for what); names and signatures of methods must be checked against the project sources **without fail**. Example of a real delta: `УправлениеДоступом.ВключитьПрофильПользователю` exists in 3.1.x, absent in 2.3.5.

| Task / subsystem | Reference |
|---|---|
| Module suffixes, stable/service/deprecated, `*Переопределяемый` hooks, subsystem map | `references/bsp-3.1.11/fundamentals.md` |
| `ОбщегоНазначения*`, strings, dates, attributes by reference, XML/JSON, secure storage | `references/bsp-3.1.11/base-common.md` |
| Long-running operations, background and scheduled jobs | `references/bsp-3.1.11/longs-and-jobs.md` |
| Users, RLS, access group profiles, external users | `references/bsp-3.1.11/users-access.md` |
| Pluggable commands, additional reports and data processors | `references/bsp-3.1.11/commands-external.md` |
| Printing, print manager, report variants, СКД | `references/bsp-3.1.11/print-reports.md` |
| Properties, edit restriction, change restriction dates | `references/bsp-3.1.11/forms-validation.md` |
| Files, volumes, object versions, exporting objects to files | `references/bsp-3.1.11/files-and-versions.md` |
| Data exchange, exchange plans, synchronization, SaaS areas | `references/bsp-3.1.11/data-exchange.md` |
| Mail, SMS, message templates, discussions, interactions | `references/bsp-3.1.11/comms.md` |
| Contact information, addresses, address classifier | `references/bsp-3.1.11/contact-info.md` |
| Currencies, exchange rates, banks, work schedules and calendars | `references/bsp-3.1.11/currencies-banks.md` |
| Number/code prefixes and ИБ prefix | `references/bsp-3.1.11/prefixes.md` |
| ИБ version update and update handlers | `references/bsp-3.1.11/update.md` |
| ЭП, МЧД, cryptography, DSS | `references/bsp-3.1.11/esign-mcd.md` |
| Administration, backup, monitoring, personal data, duplicates, classifiers, external components | see the other `references/bsp-3.1.11/*.md` |

---

## Rule 1: Strategy for finding БСП functions

### Algorithm: LSP -> grep -> AI

1. **LSP** (if available): `navigate_symbol("ЗначенияРеквизитовОбъекта")`
2. **Text search**: `grep -r "Функция.*КурсВалюты" src/CommonModules/`
3. **AI assistant**: “Is there a function in БСП for getting the exchange rate for a date?”

### When to write your own vs use БСП

| Situation | Decision |
|----------|---------|
| БСП has a suitable function | **Use БСП** |
| БСП has a similar one, but with extra functionality | **Use БСП** — the extra part does not hurt |
| The needed function is not in БСП | Write your own in the style of БСП |
| Configuration without БСП | Write your own |

Module suffixes (`Клиент` / `КлиентСервер` / `ПовтИсп` / `Служебный*` and combinations) — `references/bsp-3.1.11/fundamentals.md`. In practice: client-side form code — search first in `*КлиентСервер`, then in `*Клиент`; server-side — in the module without a suffix; `*ПовтИсп` — frequently requested reference data (session cache).

---

## Rule 2: Catalog of ready-made mechanisms - do not duplicate БСП

Before writing your own implementation, check the catalog. Detailed signatures, examples, and anti-patterns are in the subsystem reference (see the map above).

**`ОбщегоНазначения`** (server):

| Function | When to use |
|---------|-------------------|
| `ЗначениеРеквизитаОбъекта()` | Instead of `Ссылка.Реквизит` (avoid dot notation) |
| `ЗначенияРеквизитовОбъекта()` | Several attributes with one database access (`"Наименование, ИНН, ОсновнойМенеджер"`) |
| `СообщитьПользователю()` | Message tied to a field and a `Отказ` flag (instead of `Сообщить()`) |
| `МенеджерОбъектаПоСсылке()` | Instead of `Выполнить("Справочники." + Имя)` |
| `ПодсистемаСуществует()` / `ОбщийМодуль()` | Conditional/dynamic module invocation |
| `ЭтоСсылка()` / `СсылкаСуществует()` | Parameter validation / existence check before access |

**`СтроковыеФункцииКлиентСервер`**:

| Function | When to use |
|---------|-------------------|
| `ПодставитьПараметрыВСтроку()` | Analog of `СтрШаблон()`, with additional checks |
| `СтрокаСЧисломПредметов()` | Declension: `НСтр("ru = 'документ, документа, документов'")` → «1 документ», «5 документов» |
| `ЕстьНедопустимыеСимволы()` / `ТолькоЦифрыВСтроке()` | Input validation (ИНН, КПП) |
| `РазложитьСтрокуВМассивПодстрок()` | Parsing by delimiter |

**`ОбщегоНазначенияКлиентСервер`** (`&НаКлиентеНаСервереБезКонтекста` — both environments): `ДополнитьМассив()`, `ДополнитьСтруктуру()`, `СвойствоСтруктуры()` (safe read with default value), `ПроверитьПараметр()` (type validation), `УдалитьЗначениеИзМассива()`.

**Subsystems instead of homegrown code:**

| What people often write themselves | What exists in БСП | Reference |
|----------------------|----------------|-----------|
| Sending mail | `РаботаСПочтовымиСообщениями` | `comms.md` |
| Exchange rate | `РаботаСКурсамиВалют.ПолучитьКурсВалюты()` | `currencies-banks.md` |
| Long-running background operation | `ДлительныеОперации.ВыполнитьФункцию()` | `longs-and-jobs.md` |
| Storing secrets / passwords | Information register `БезопасноеХранилищеДанных` (only through `ОбщегоНазначения.*ДанныеВБезопасноеХранилище*` wrappers) | `base-common.md` |
| Access-right profiles | `ГруппыДоступаПользователей` / `ПрофилиГруппДоступа` | `users-access.md` |
| Registering an external processing | `СведенияОВнешнейОбработке()` | `commands-external.md` |
| Working with files / temporary files | `РаботаСФайлами`, `ПолучитьИмяВременногоФайла()` + explicit `УдалитьФайлы()` | `files-and-versions.md` |
| Validation filling | `ОбработкаПроверкиЗаполнения` + `СообщитьПользователю(..., Отказ)`; conditional exclusion of an attribute — `УдалитьЗначениеИзМассива(ПроверяемыеРеквизиты, ...)` | `forms-validation.md` |
| Printing | `УправлениеПечатью.НужноПечататьМакет()` / `ВывестиТабличныйДокументВКоллекцию()` | `print-reports.md` |
| Registration log | see `error-handling`, rule 7 | — |

---

## Rule 3: Mandatory subsystem nuances (MUST)

Full code examples are in the subsystem reference; here only the rules, violating which causes a defect.

**`ДлительныеОперации`** — for any server work longer than ~3 sec; do not block the UI with a homemade wait loop. Progress — via `ДлительныеОперации.СообщитьПрогресс()`; state between steps — in task parameters, not in global variables; restart is idempotent (a repeated call with the same parameters yields the same result). Handle a background job error on the client via `СтандартныеПодсистемыКлиент.ОбработатьОшибкуФоновогоЗадания()`.

**Secure storage (register `БезопасноеХранилищеДанных`)** — never store passwords/tokens/secrets in metadata attributes, constants, registration log, version control system; access to the register is only through wrappers `ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище`/`ПрочитатьДанныеИзБезопасногоХранилища`/`УдалитьДанныеИзБезопасногоХранилища`. In `ПередУдалением` of the owning object, always delete its records from storage — otherwise "orphaned" secrets accumulate.

**`ПрофилиГруппДоступа`** — when using role-based access, use БСП profiles (`УправлениеДоступом.НовоеОписаниеПрофиляГруппДоступа()`, `ВключитьПрофильПользователю()`), not direct role assignment. The profile identifier is a fixed UUID and does not change when renamed. `ПривилегированныйРежим()` — strictly local, turn it off immediately after the operation. Rights check — `УправлениеДоступом.ЕстьПраво()`/`ЕстьРоль()` (or `ЧтениеРазрешено()`/`ИзменениеРазрешено()` for RLS checks), not `РольДоступна()` (the latter does not take RLS into account).

**External processing** — registering in a БСП base requires the function `СведенияОВнешнейОбработке()` in the main module (Type, Name, Version, `БезопасныйРежим = Истина`, commands) and the entry point `ВыполнитьКоманду(Идентификатор, ...)`.

---

## Searching for analogues via Buddy

If `search_ssl_functions` did not produce a result, use `ask_ai_assistant` (VALIDATE_BSL template from `buddy-prompting`): pass the code fragment and get recommendations for replacing it with БСП methods. Also use `SEARCH_DOCS` for documentation on a specific БСП method.

---
depends_on: []
---
