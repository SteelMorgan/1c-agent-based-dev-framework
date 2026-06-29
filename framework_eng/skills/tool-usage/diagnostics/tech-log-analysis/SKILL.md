---
name: tech-log-analysis
description: "Diagnostics of 1C tech log: EXCP, SQL, locks"
uses_capabilities:
  - search_tech_log
  - configure_tech_log
  - logc_get_techlog_config
  - logc_save_techlog
  - logc_restore_techlog
  - logc_disable_techlog
---

# Working with the 1C technology log (Tech Log)

The Tech Log loads the system. Enable it selectively, with the minimum set of events. Always restore the configuration after diagnostics.

For Vanessa, the Tech Log is the last diagnostic source; first use `event-log-analysis` and visually check UI blockers.

For errors and auditing user actions, use `event-log-analysis`.

---

## When to use

| Trigger | Action |
|---------|----------|
| Slow query - need to find SQL | `search_tech_log` with `name: "DBMSSQL"` / `"DBPOSTGRS"` |
| Locks, deadlock | `search_tech_log` with `name: "TLOCK"` / `"TDEADLOCK"` / `"TTIMEOUT"` |
| Platform exception is not in the event log | `search_tech_log` with `name: "EXCP"` |
| Long server call | `search_tech_log` with `name: "CALL"` or `"SCALL"` |
| Tech Log status is unknown | `logc_get_techlog_config` - read the configuration |
| Tech Log is not running | Full cycle (see algorithm below) |

---

## Full diagnostic cycle

Unified algorithm for passive mode (waiting for reproduction) and active mode (running a test).

**1. Save the configuration**
```
logc_save_techlog()
```
Returns `backup_id` - keep it for step 7.

**2. Configure events**
```
logc_configure_techlog(
  location: "/var/log/1c/techlog",
  history: 24,
  events: ["EXCP", "DBMSSQL", "TLOCK", "TDEADLOCK"]
)
```

**3. Check that collection is running**
```
logc_get_actual_log_timestamp()
```

**4. Reproduce the problem / wait for reproduction**

**5. Smart polling for log readiness**

Up to 10 attempts with a 3-5 second pause: `logc_get_actual_log_timestamp() >= target_time`. If it does not advance, inform the user and do not draw conclusions from an incomplete window.

**6. Read the records**
```
search_tech_log(
  from: "2025-02-11T14:00:00Z",
  to:   "2025-02-11T14:15:00Z",
  name: "DBMSSQL",
  min_duration: 1000
)
```

**7. Restore the configuration**
```
logc_restore_techlog(backup_id: "...")
```

**8. Confirm the status to the user**: `Tech Log restored` / `Tech Log disabled` / `Minimal profile (events: ...)`.

Steps 1 and 7 are **mandatory**. Never leave the Tech Log enabled after diagnostics without explicit agreement.

---

### Cycle variants

**Tech Log already configured by an administrator** - read only:
1. `logc_get_techlog_config` -> make sure the required events are present.
2. `logc_get_actual_log_timestamp` -> collection is current.
3. `search_tech_log` -> read the records.
4. DO NOT change the configuration - the Tech Log is not ours.

**Urgent shutdown** (disk is filling up): `logc_disable_techlog()`. After the issue is resolved, use `logc_restore_techlog(backup_id)`.

**Minimal monitoring** (instead of full shutdown, only by agreement): events `["EXCP", "CONN"]`. Record the active events and the responsible person in the response.

---

## Tech Log events

| Event | When to use |
|---------|--------------------|
| `EXCP` | Errors not visible in the event log |
| `DBMSSQL` | Slow MS SQL queries |
| `DBPOSTGRS` | Slow PostgreSQL queries |
| `TLOCK` | Lock conflicts |
| `TDEADLOCK` | Deadlocks |
| `TTIMEOUT` | Lock timeouts |
| `CALL` / `SCALL` | Slow server calls |
| `CONN` | Connection issues |
| `SDBL` | Translating queries to SQL |

**Standard set:** `["EXCP", "DBMSSQL", "TLOCK", "TDEADLOCK"]`

---

## Capabilities

| Capability | Purpose |
|------------|------------|
| `search_tech_log` | Search Tech Log records |
| `logc_get_techlog_config` | Read the current Tech Log configuration |
| `logc_save_techlog` | Save the configuration before changing it |
| `logc_configure_techlog` | Configure events, path, retention period |
| `logc_get_actual_log_timestamp` | Check that collection is running |
| `logc_restore_techlog` | Restore the saved configuration |
| `logc_disable_techlog` | Disable the Tech Log |
| `navigate_symbol` | Navigate to code by context from a Tech Log record |

---

## Scenario techniques

See [`references/scenarios.md`](references/scenarios.md) for incident classification, required identifiers, timeline rules, and output template.

---

## Typical mistakes

| Mistake | Workaround |
|--------|---------------|
| Forgot `logc_save_techlog` before configuring | Ask the user for the current configuration |
| Tech Log is not active after `configure` | The platform requires restarting services |
| `logc_get_actual_log_timestamp` does not update | The service was not restarted or the `location` is wrong |
| Too many events - the disk fills up | Limit the event set; reduce `history` |
| `search_tech_log` returns nothing | Check the time window; the event must be after Tech Log was enabled |

---
depends_on: []
---
