---
name: tech-log-analysis
description: Working with the 1С tech log (Tech Log). The skill teaches the agent to manage the full lifecycle of the Tech Log — configuration, enabling, collection, analysis, restoration — and to diagnose technical issues such as slow queries, locks, and exceptions.
---

# Working with the 1С Tech Log (Tech Log)

## Purpose

The skill teaches the agent to **manage the full lifecycle of the Tech Log** and analyze its contents. The Tech Log is a low-level 1С platform tool for diagnosing performance: slow DBMS queries, locks, platform-level exceptions, and interactions with external systems.

**Principle:** The Tech Log puts load on the system. Enable it selectively with the minimal required set of events. Always restore the original configuration after diagnostics.

For error hunting and auditing user actions, use the separate skill [`event-log-analysis`](../event-log-analysis/SKILL.md).

---

## Full Tech Log lifecycle

```
[1. Save the current configuration]
        ↓
[2. Configure the required events]
        ↓
[3. Make sure collection is running]
        ↓
[4. Reproduce the issue]
        ↓
[5. Read and analyze the entries]
        ↓
[6. Restore the original configuration / disable the Tech Log]
```

**Rule:** Steps 1 and 6 are mandatory. Never leave the Tech Log enabled after diagnostics.

---

## When to apply

| Trigger | Action |
|---------|--------|
| Slow query — need to locate the SQL | `search_tech_log` with `name: "DBMSSQL"` / `"DBPOSTGRS"` |
| Locks, deadlock | `search_tech_log` with `name: "TLOCK"` / `"TDEADLOCK"` / `"TTIMEOUT"` |
| Platform exception that is not in the event log | `search_tech_log` with `name: "EXCP"` |
| Long server call | `search_tech_log` with `name: "CALL"` or `"SCALL"` |
| Before analysis — Tech Log status unknown | `logc_get_techlog_config` — read the current configuration |
| Tech Log is not collecting | Full cycle: save → configure → verify → analyze → restore |

---

## Use cases

### Modes of operation: passive and active

- **Passive mode:** enable the Tech Log and wait for the user to reproduce the issue naturally.
- **Active mode:** enable the Tech Log and immediately run the reproducing scenario (for example, an automated test or manual procedure).

In both modes the cycle is the same: save → configure → reproduce/wait → read → restore.

### Use case 1: Full diagnostics cycle (primary)

**Steps:**

**1. Save the current configuration**
```
logc_save_techlog()
```
Returns `backup_id` — keep it for step 6.

**2. Read the current state (optional)**
```
logc_get_techlog_config()
```

**3. Configure the required events**
```
logc_configure_techlog(
  location: "/var/log/1c/techlog",
  history: 24,
  events: ["EXCP", "DBMSSQL", "TLOCK", "TDEADLOCK"]
)
```

**4. Verify that collection is running**
```
logc_get_actual_log_timestamp()
```
If the timestamp advances, the Tech Log is active.

**5. Reproduce the issue** (the user performs the action in 1С)

**6. Wait until the log is ready to be read (smart polling)**

After reproducing the event, the data may not appear immediately. Use a limited readiness check loop:

```bsl
// Псевдокод
ЦелевоеВремя = <время_воспроизведения_проблемы>;

Для Попытка = 1 По 10 Цикл
    ФактВремя = logc_get_actual_log_timestamp();
    Если ФактВремя >= ЦелевоеВремя Тогда
        Прервать;
    КонецЕсли;

    // Короткая пауза между проверками
    // (например, 3-5 сек; не бесконечный цикл)
КонецЦикла;
```

If the timestamp does not reach the target after the limit, inform the user about the log parsing/delivery issue and do not draw conclusions from an incomplete window.

**7. Read the entries**
```
search_tech_log(
  from: "2025-02-11T14:00:00Z",
  to:   "2025-02-11T14:15:00Z",
  name: "DBMSSQL",
  min_duration: 1000
)
```

**8. Restore the original configuration**
```
logc_restore_techlog(backup_id: "...")
```

**9. Final verification (mandatory)**

- Make sure the original configuration is really restored.
- Explicitly inform the user of the final status:
  - `Tech Log restored to the original state`, or
  - `Tech Log disabled` (if it was originally not configured).
- If further investigation is required — record which mode is currently active and who is responsible for subsequent shutdown/restoration.

This reduces the risk of leaving the Tech Log enabled for too long.

---

### Use case 1.1: Minimal safe mode (when full disabling is not possible)

Sometimes you need to keep basic monitoring after diagnostics. In this case, instead of fully disabling, enable a **minimal set of rare events**, for example:

- `EXCP` — platform exceptions
- `CONN` — connection events
- `PROC` — system processes (if available in your environment)

Rules:
1. The minimal profile must be agreed with the user/administrator.
2. Clearly document in the response that a short/minimal log is enabled and list the active events.
3. Record who decided to keep the minimal profile and who will support it going forward.

For most tasks it is preferable to fully revert using `logc_restore_techlog`.

---

### Use case 2: Lock analysis

**Steps:**

1. Full cycle (Use case 1) with events: `["TLOCK", "TDEADLOCK", "TTIMEOUT"]`.
2. `search_tech_log` with `name: "TLOCK"` — find blocking and blocked sessions.
3. Analyze `records`: `context`, `message`, `timestamp`, `session`.
4. Match with the code via `navigate_symbol` using the call context.
5. `logc_restore_techlog`.

---

### Use case 3: Tech Log was already configured — read only

If the Tech Log is already running (for example, configured by an administrator):

1. `logc_get_techlog_config` — confirm that the required events are present.
2. `logc_get_actual_log_timestamp` — make sure data collection is current.
3. `search_tech_log` — read the entries.
4. **Do not change the configuration**, do not restore — the Tech Log is not yours.

---

### Use case 4: Quick Tech Log shutdown

If you urgently need to disable it (for example, the log is filling up the disk):

```
logc_disable_techlog()
```

After resolving the issue — `logc_restore_techlog(backup_id)` if you saved a backup.

---

### Use case 5: Active diagnostics via reproducing test

Suitable when a stable automated or manual scenario reproduces the issue.

1. Save the current configuration (`logc_save_techlog`).
2. Enable the Tech Log for specific events.
3. Run the reproducing scenario.
4. Perform smart polling on `logc_get_actual_log_timestamp`.
5. Read `search_tech_log` within a narrow time window around the test.
6. Restore the configuration (`logc_restore_techlog`).
7. Explicitly confirm the final Tech Log status to the user.

Advantage: less noise in the log and easier correlation of entries to specific test steps.

---

## Tech Log events — reference

| Event | Description | When to use |
|-------|-------------|-------------|
| `EXCP` | Platform exceptions | Errors not visible in the event log |
| `DBMSSQL` | Queries to MS SQL | Slow queries, SQL plan |
| `DBPOSTGRS` | Queries to PostgreSQL | Slow queries |
| `TLOCK` | Managed locks | Lock conflicts |
| `TDEADLOCK` | Deadlocks | Deadlock situations |
| `TTIMEOUT` | Lock timeouts | Hangs during locking |
| `CALL` | Server calls | Slow server procedures |
| `SCALL` | System calls | Internal platform calls |
| `CONN` | Connections | Connection issues |
| `SDBL` | Query language requests | Translation into SQL |

**Standard initial set for diagnostics:** `["EXCP", "DBMSSQL", "TLOCK", "TDEADLOCK"]`

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `search_tech_log` | Search through Tech Log records |
| `logc_get_techlog_config` | Read the current Tech Log configuration |
| `logc_save_techlog` | Save the configuration before changing it |
| `logc_configure_techlog` | Configure events, path, retention period |
| `logc_get_actual_log_timestamp` | Check that collection is running |
| `logc_restore_techlog` | Restore the saved configuration |
| `logc_disable_techlog` | Disable the Tech Log |
| `navigate_symbol` | Jump to code from the Tech Log entry context |

---

## Post-analysis checklist (mandatory)

1. Analysis is complete and conclusions are documented.
2. The Tech Log is returned to the expected state:
   - either restored via `logc_restore_techlog(backup_id)`,
   - or disabled via `logc_disable_techlog()` (if agreed).
3. The user is explicitly informed of the final Tech Log status.
4. If minimal monitoring is left enabled — record the duration and responsible person.

Suggested user messages:
- `✅ Analysis complete. Tech Log restored to the original state.`
- `✅ Analysis complete. Tech Log disabled.`
- `✅ Analysis complete. Minimal profile enabled (events: <list>).`
- `✅ Analysis complete. Minimal profile enabled; decision agreed with <who>.`

---

## Common mistakes

| Mistake | Workaround |
|---------|------------|
| Forgot `logc_save_techlog` before configuration | Ask the user about the current configuration; document the change |
| Tech Log not active after `configure` | The platform may require service restarts; notify the user |
| `logc_get_actual_log_timestamp` does not update | Service not restarted or wrong `location` |
| Too many events — the disk fills up quickly | Limit the set of events; reduce `history` |
| Forgot to restore the configuration | Always complete the `logc_restore_techlog` cycle |
| `search_tech_log` returns nothing | Check the time window; ensure the event occurred after enabling the Tech Log |

---
depends_on: []
---
