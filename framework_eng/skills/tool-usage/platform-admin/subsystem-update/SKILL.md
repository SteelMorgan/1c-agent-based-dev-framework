---
name: subsystem-update
description: "BSP subsystem updates: run, verify, event log"
---

# Updating the БСП Subsystem

## When to Apply

| Trigger | Action |
|---------|----------|
| An update handler was added with a new version | Run the full update cycle |
| The handler needs to be run again | Reset the version in the register, then run the full cycle |
| The update did not work | Diagnose through the event log |

---

## Prerequisites

1. The handler is registered in the subsystem update module (for example `ОбновлениеИнформационнойБазыXXX`)
2. The subsystem module is registered in `ИнтеграцияПодсистемБСП.ПриДобавленииПодсистем`
3. The project is built (`v8-runner build`) - changes in code are loaded into the infobase

---

## Full Update Cycle

### Step 1. Check the Current Version

```
ВЫБРАТЬ ИмяПодсистемы, Версия
ИЗ РегистрСведений.ВерсииПодсистем
ГДЕ ИмяПодсистемы = "ИМЯ_ПОДСИСТЕМЫ"
```

БСП will execute the handler only if the version in the register is **<** the handler version.

### Step 2. Lock the Infobase

Handlers with `МонопольныйРежим = Истина` require no other sessions.

```bash
# Connection data: <project_root>/configs/yaxunit-runner.yml → app.connection
# cluster_uuid and infobase_uuid: <project_root>/configs/cluster_map.yaml

# Deny new sessions and scheduled jobs
rac infobase update \
  --cluster=<cluster_uuid> \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --sessions-deny=on \
  --scheduled-jobs-deny=on \
  --denied-message="Обновление ИБ" \
  --permission-code=UpdateIB \
  <ras_host>:<ras_port>

# Terminate all remaining sessions
rac session list --cluster=<cluster_uuid> --infobase=<infobase_uuid> <ras_host>:<ras_port>

# For each session:
rac session terminate --cluster=<cluster_uuid> --session=<session_uuid> <ras_host>:<ras_port>
```

### Step 3. Run the Update

```bash
/opt/1cv8/current/1cv8c ENTERPRISE \
  /S"<server>/<infobase>" \
  /N"<user>" /P"<password>" \
  /C"ЗапуститьОбновлениеИнформационнойБазы" \
  /UC"UpdateIB" \
  /DisableStartupDialogs
```

The `/UC"UpdateIB"` parameter is the permission code matching `--permission-code` from step 2.

Expected result: the process will finish automatically (30-120 seconds depending on data volume).

### Step 4. Remove the Lock

```bash
rac infobase update \
  --cluster=<cluster_uuid> \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --sessions-deny=off \
  --scheduled-jobs-deny=off \
  --denied-message="" \
  --permission-code="" \
  <ras_host>:<ras_port>
```

### Step 5. Check the Result

1. **Version in the register** - it should be updated to the target value:

```
ВЫБРАТЬ ИмяПодсистемы, Версия
ИЗ РегистрСведений.ВерсииПодсистем
ГДЕ ИмяПодсистемы = "ИМЯ_ПОДСИСТЕМЫ"
```

2. **Event log** - check for errors during the update:
   - `logc_get_event_log(level='Error', from=<start_time>)` - there should be no `infobase update` errors
   - `logc_get_event_log(level='Information', from=<start_time>)` - look for records from your handler

---

## Handler Rerun

If the handler has already run (the version in the register is >= the handler version), БСП will skip it.

Rerun options:
- **Increase the handler version** (1.0.0.1 -> 1.0.0.2) - recommended
- **Reset the version** in the register through direct SQL to the DBMS - for debugging only

---

## Typical Errors

| Error | Cause | Solution |
|--------|---------|---------|
| Cannot enter exclusive mode | Active sessions in the infobase | Step 2: lock + terminate all sessions |
| The update is already running | A stuck session from the previous attempt | Terminate the stuck session with `rac session terminate` |
| Object method not found (ПередОбновлениемИнформационнойБазы) | The subsystem module does not contain the required callback procedures | Add the 6 required stub procedures (see the template below) |
| The handler was not called (the version did not change) | The version in the register is >= the handler version | Check the current version with a query, increase it if needed |
| The configuration was updated, but the handler did not run | The handler is registered twice or in the wrong subsystem | Check `grep` for the procedure name across all modules |

---

## Subsystem Update Module Template

Minimum required procedure set for the `ОбновлениеИнформационнойБазыXXX` module:

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
