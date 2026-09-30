---
name: subsystem-update
description: "БСП subsystem updates: запуск, контроль, журнал"
---

# БСП subsystem update

## When to use

| Trigger | Action |
|---------|----------|
| An update handler with a new version has been added | Run the full update cycle |
| The handler needs to be run again | Reset the version in the register, then run the full cycle |
| The update did not work | Diagnose through ЖР |

---

## Preconditions

1. The handler is registered in the subsystem update module (for example `ОбновлениеИнформационнойБазыXXX`)
2. The subsystem module is registered in `ИнтеграцияПодсистемБСП.ПриДобавленииПодсистем`
3. The project is built (`v8-runner build`) - the code changes are loaded into the database

---

## Full update cycle

### Step 1. Check the current version

```
ВЫБРАТЬ ИмяПодсистемы, Версия
ИЗ РегистрСведений.ВерсииПодсистем
ГДЕ ИмяПодсистемы = "ИМЯ_ПОДСИСТЕМЫ"
```

БСП will run the handler only if the version in the register is **<** the handler version.

### Step 2. Lock the database

Handlers with `МонопольныйРежим = Истина` require no other sessions to be present. The exact `rac` command syntax is in the `rac-use` skill (§ Locking access to the database, § Viewing database sessions, § Forcefully terminating a session). Below is the order and parameters specific to updating the infobase.

Connection data: `<project_root>/configs/yaxunit-runner.yml → app.connection`. `cluster_uuid` and `infobase_uuid`: `<project_root>/configs/cluster_map.yaml`. The RAC agent address is the trailing argument `<ras_host>:<ras_port>`.

**Order (do not change):**

1. Lock new sessions **and** scheduled jobs with a single `rac infobase update` command using the flags:
   `--sessions-deny=on --scheduled-jobs-deny=on --denied-message="Обновление ИБ" --permission-code=UpdateIB`.
   The value of `--permission-code=UpdateIB` **must match** the `/UC` from Step 3.
2. Get the list of remaining sessions (`rac session list`) and terminate each one
   (`rac session terminate --session=<session_uuid>`) - otherwise exclusive mode will not be established.

### Step 3. Start the update

```bash
/opt/1cv8/current/1cv8c ENTERPRISE \
  /S"<server>/<infobase>" \
  /N"<user>" /P"<password>" \
  /C"ЗапуститьОбновлениеИнформационнойБазы" \
  /UC"UpdateIB" \
  /DisableStartupDialogs
```

The `/UC"UpdateIB"` parameter is the permission code, matching `--permission-code` from step 2.

Expected result: the process will finish automatically (30-120 sec depending on data volume).

### Step 4. Remove the lock

`rac infobase update` with the reverse flags: `--sessions-deny=off --scheduled-jobs-deny=off --denied-message="" --permission-code=""` (syntax is `rac-use` § Locking access to the infobase and § Managing scheduled jobs).

### Step 5. Check the result

1. **Version in the register** — it should be updated to the target value:

```
ВЫБРАТЬ ИмяПодсистемы, Версия
ИЗ РегистрСведений.ВерсииПодсистем
ГДЕ ИмяПодсистемы = "ИМЯ_ПОДСИСТЕМЫ"
```

2. **Event log** — check for errors during the update time:
   - `logc_get_event_log(level='Error', from=<start_time>)` — there should be no `Обновление информационной базы` errors
   - `logc_get_event_log(level='Information', from=<start_time>)` — look for records from your handler

---

## Re-running the handler

If the handler has already run (the version in the register is >= the handler version), БСП will skip it.

Options for rerunning:
- **Increase the handler version** (1.0.0.1 → 1.0.0.2) — recommended approach
- **Reset the version** in the register through direct SQL to the DBMS — only for debugging

---

## Common errors

| Error | Cause | Solution |
|--------|---------|---------|
| Unable to set exclusive mode | Active sessions in the infobase | Step 2: lock + terminate all sessions |
| Update is already in progress | A stuck session from the previous attempt | Terminate the stuck session via `rac session terminate` |
| Method not found (ПередОбновлениемИнформационнойБазы) | The subsystem module does not contain the required callback procedures | Add 6 required stub procedures (see the template below) |
| Handler was not called (version did not change) | The version in the register is >= the handler version | Check the current version with a query; increase it if necessary |
| Configuration updated, but the handler did not run | The handler is registered twice or in the wrong subsystem | Check grep for the procedure name in all modules |

---

## Subsystem update module template

Minimum required set of procedures for the `ОбновлениеИнформационнойБазыXXX` module:

```bsl
#Область ПрограммныйИнтерфейс

#Область ДляВызоваИзДругихПодсистем

// СтандартныеПодсистемы.БазоваяФункциональность
Процедура ПриДобавленииПодсистем(МодулиПодсистем) Экспорт
    МодулиПодсистем.Добавить("ОбновлениеИнформационнойБазыXXX");
КонецПроцедуры

// СтандартныеПодсистемы.ОбновлениеВерсииИБ
Процедура ПриДобавленииПодсистемы(Описание) Экспорт
    Описание.Имя    = "ИМЯ_ПОДСИСТЕМЫ";
    Описание.Версия = "1.0.0.1";
    Описание.ТребуемыеПодсистемы.Добавить("СтандартныеПодсистемы");
КонецПроцедуры

Процедура ПриДобавленииОбработчиковОбновления(Обработчики) Экспорт
    Обработчик = Обработчики.Добавить();
    Обработчик.Версия = "1.0.0.1";
    Обработчик.МонопольныйРежим = Истина;
    Обработчик.Процедура = "МодульОбработчика.ИмяПроцедуры";
КонецПроцедуры

//@skip-warning
Процедура ПередОбновлениемИнформационнойБазы() Экспорт
КонецПроцедуры

//@skip-warning
Процедура ПослеОбновленияИнформационнойБазы(Знач ПредыдущаяВерсияИБ, Знач ТекущаяВерсияИБ,
        Знач ВыполненныеОбработчики, ВыводитьОписаниеОбновлений, МонопольныйРежим) Экспорт
КонецПроцедуры

//@skip-warning
Процедура ПриПодготовкеМакетаОписанияОбновлений(Знач Макет) Экспорт
КонецПроцедуры

//@skip-warning
Процедура ПриОпределенииРежимаОбновленияДанных(РежимОбновленияДанных, СтандартнаяОбработка) Экспорт
КонецПроцедуры

//@skip-warning
Процедура ПриДобавленииОбработчиковПереходаСДругойПрограммы(Обработчики) Экспорт
КонецПроцедуры

//@skip-warning
Процедура ПриЗавершенииПереходаСДругойПрограммы(Знач ПредыдущееИмяКонфигурации,
        Знач ПредыдущаяВерсияКонфигурации, Параметры) Экспорт
КонецПроцедуры

#КонецОбласти
#КонецОбласти
```

Registration in `ИнтеграцияПодсистемБСП.ПриДобавленииПодсистем`:
```bsl
МодулиПодсистем.Добавить("ОбновлениеИнформационнойБазыXXX");
```

---
depends_on:
  - framework/skills/tool-usage/platform-admin/rac-use/SKILL.md
requires:
  - tools
---
