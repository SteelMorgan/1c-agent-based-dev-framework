---
name: subsystem-update
description: Initialization of the BSP subsystem update — blocking sessions, launching update handlers, checking the outcome via the event log and the ВерсииПодсистем register.
---

# Subsystem Update for БСП

## When to Apply

| Триггер | Действие |
|---------|----------|
| An update handler with a new version was added | Run the full update cycle |
| A handler needs to be rerun | Reset the version in the register, then run the full cycle |
| The update did not run successfully | Diagnose via the event log (ЖР) |

---

## Preconditions

1. The handler is registered in the subsystem update module (for example `ОбновлениеИнформационнойБазыXXX`)
2. The subsystem module is registered in `ИнтеграцияПодсистемБСП.ПриДобавленииПодсистем`
3. The project is built (`build_project`) — the code changes are loaded into the database

---

## Full Update Cycle

### Step 1. Check the current version

```
ВЫБРАТЬ ИмяПодсистемы, Версия
ИЗ РегистрСведений.ВерсииПодсистем
ГДЕ ИмяПодсистемы = "ИМЯ_ПОДСИСТЕМЫ"
```

БСП выполнит обработчик, только если версия в регистре **<** версии обработчика.

### Step 2. Lock the infobase

Handlers with `МонопольныйРежим = Истина` require that no other sessions are active.

```bash
# Данные подключения: <project_root>/configs/yaxunit-runner.yml → app.connection
# cluster_uuid и infobase_uuid: <project_root>/configs/cluster_map.yaml

# Заблокировать новые сеансы и регламентные задания
rac infobase update \
  --cluster=<cluster_uuid> \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --sessions-deny=on \
  --scheduled-jobs-deny=on \
  --denied-message="Обновление ИБ" \
  --permission-code=UpdateIB \
  <ras_host>:<ras_port>

# Завершить все оставшиеся сеансы
rac session list --cluster=<cluster_uuid> --infobase=<infobase_uuid> <ras_host>:<ras_port>

# Для каждого сеанса:
rac session terminate --cluster=<cluster_uuid> --session=<session_uuid> <ras_host>:<ras_port>
```

### Step 3. Run the update

```bash
/opt/1cv8/current/1cv8c ENTERPRISE \
  /S"<server>/<infobase>" \
  /N"<user>" /P"<password>" \
  /C"ЗапуститьОбновлениеИнформационнойБазы" \
  /UC"UpdateIB" \
  /DisableStartupDialogs
```

The `/UC"UpdateIB"` parameter is the permission code that matches the `--permission-code` from step 2.

Expect the process to finish automatically (30–120 seconds depending on the data volume).

### Step 4. Release the lock

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

### Step 5. Verify the result

1. **Version in the register** — must update to the target value:

```
ВЫБРАТЬ ИмяПодсистемы, Версия
ИЗ РегистрСведений.ВерсииПодсистем
ГДЕ ИмяПодсистемы = "ИМЯ_ПОДСИСТЕМЫ"
```

2. **Event log (ЖР)** — check for errors during the update:
   - `logc_get_event_log(level='Error', from=<время_запуска>)` — there should be no `Обновление информационной базы` errors
   - `logc_get_event_log(level='Information', from=<время_запуска>)` — look for entries from your handler

---

## Rerunning the Handler

If the handler has already run (the version in the register is >= the handler version), БСП will skip it.

Rerun options:
- **Increment the handler version** (1.0.0.1 → 1.0.0.2) — recommended approach
- **Reset the version** in the register via direct SQL to the database — for debugging only

---

## Common Errors

| Error | Reason | Solution |
|--------|---------|---------|
| Cannot set exclusive mode | Active sessions in the infobase | Step 2: block and terminate all sessions |
| Update is already running | A hung session from the previous attempt | Terminate the hung session via `rac session terminate` |
| Object method not found (ПередОбновлениемИнформационнойБазы) | The subsystem module lacks the mandatory callback procedures | Add the 6 required stub procedures (see the template below) |
| The handler did not run (the version did not change) | The version in the register is >= the handler version | Query the current version, and increase it if necessary |
| The configuration was updated but the handler did not run | The handler is registered twice or in the wrong subsystem | Check by grepping the procedure name across all modules |

---

## Subsystem Update Module Template

Minimal set of mandatory procedures for the `ОбновлениеИнформационнойБазыXXX` module:

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
