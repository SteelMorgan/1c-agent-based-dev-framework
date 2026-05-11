---
name: tech-log-analysis
description: "Working with the 1C technological log (Tech Log). The skill teaches the agent to manage the full TLog lifecycle - setup, enablement, collection, analysis, restoration - and diagnose technical problems such as slow queries, locks, exceptions."
uses_capabilities:
  - search_tech_log
  - configure_tech_log
  - logc_get_techlog_config
  - logc_save_techlog
  - logc_restore_techlog
  - logc_disable_techlog
---

# Working with the 1C technological log (Tech Log)

TLog loads the system. Enable it selectively, with the minimum set of events. Always restore the configuration after diagnostics.

For Vanessa - TLog is the last diagnostics source; first use `event-log-analysis` and visually check UI blockers.

For errors and auditing user actions - `event-log-analysis`.

---

## When to use

| Trigger | Action |
|---------|----------|
| Slow query - need to find SQL | `search_tech_log` with `name: "DBMSSQL"` / `"DBPOSTGRS"` |
| Locks, deadlock | `search_tech_log` with `name: "TLOCK"` / `"TDEADLOCK"` / `"TTIMEOUT"` |
| Platform exception not in the Event Log | `search_tech_log` with `name: "EXCP"` |
| Long server call | `search_tech_log` with `name: "CALL"` or `"SCALL"` |
| TLog status unknown | `logc_get_techlog_config` - read the configuration |
| TLog is not running | Full cycle (see algorithm below) |

---

## Full diagnostics cycle

Unified algorithm for passive (waiting for reproduction) and active (running a test) modes.

**1. Save the configuration**
```
logc_save_techlog()
```
Returns `backup_id` - save it for step 7.

**2. Configure events**
```
logc_configure_techlog(
  location: "/var/log/1c/techlog",
  history: 24,
  events: ["EXCP", "DBMSSQL", "TLOCK", "TDEADLOCK"]
)
```

**3. Verify that collection is running**
```
logc_get_actual_log_timestamp()
```

**4. Reproduce the problem / wait for reproduction**

**5. Smart polling for log readiness**

Up to 10 attempts with a 3-5 sec pause: `logc_get_actual_log_timestamp() >= target_time`. If it has not reached that point, inform the user and do not draw conclusions from an incomplete window.

**6. Read entries**
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

**8. Confirm the status to the user**: `TLog restored` / `TLog disabled` / `Minimum profile (events: ...)`.

Steps 1 and 7 are **mandatory**. Never leave TLog enabled after diagnostics without explicit agreement.

---

### Cycle variants

**TLog already configured by an administrator** - read-only:
1. `logc_get_techlog_config` -> make sure the required events are present.
2. `logc_get_actual_log_timestamp` -> collection is current.
3. `search_tech_log` -> read entries.
4. DO NOT change the configuration - TLog is not ours.

**Urgent disable** (disk is filling up): `logc_disable_techlog()`. After the issue is resolved - `logc_restore_techlog(backup_id)`.

**Minimum monitoring** (instead of full disable, only by agreement): events `["EXCP", "CONN"]`. Record the active events and the responsible person in the response.

---

## TLog events

| Event | When to use |
|---------|--------------------|
| `EXCP` | Errors not visible in the Event Log |
| `DBMSSQL` | Slow MS SQL queries |
| `DBPOSTGRS` | Slow PostgreSQL queries |
| `TLOCK` | Lock conflicts |
| `TDEADLOCK` | Deadlocks |
| `TTIMEOUT` | Lock timeouts |
| `CALL` / `SCALL` | Slow server calls |
| `CONN` | Connection problems |
| `SDBL` | Query translation to SQL |

**Standard set:** `["EXCP", "DBMSSQL", "TLOCK", "TDEADLOCK"]`

---

## Capabilities

| Capability | Purpose |
|------------|------------|
| `search_tech_log` | Search TLog entries |
| `logc_get_techlog_config` | Read the current TLog configuration |
| `logc_save_techlog` | Save the configuration before changing it |
| `logc_configure_techlog` | Configure events, path, retention period |
| `logc_get_actual_log_timestamp` | Check that collection is running |
| `logc_restore_techlog` | Restore the saved configuration |
| `logc_disable_techlog` | Disable TLog |
| `navigate_symbol` | Go to code by the context from a TLog entry |

---

## Common mistakes

| Error | Workaround |
|--------|---------------|
| Forgot `logc_save_techlog` before configuring | Ask the user about the current configuration |
| TLog is not active after `configure` | The platform requires service restarts |
| `logc_get_actual_log_timestamp` is not updating | The service was not restarted or the `location` is incorrect |
| Too many events - disk fills up | Limit the event set; reduce `history` |
| `search_tech_log` returns empty | Check the time window; the event must be after TLog is enabled |

---
depends_on: []
---
