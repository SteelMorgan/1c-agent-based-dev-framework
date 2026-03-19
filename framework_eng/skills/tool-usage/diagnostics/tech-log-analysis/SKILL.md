---
name: tech-log-analysis
description: Working with the 1С Tech Log (Tech Log). The skill teaches the agent how to manage the full Tech Log lifecycle — setup, activation, collection, analysis, recovery — and how to diagnose technical issues like slow queries, locks, and exceptions.
---

# Working with the 1С Tech Log (Tech Log)

Tech Log puts load on the system. Enable it selectively with the minimum event set. Always restore the configuration after diagnostics.

For Vanessa, the Tech Log is the last resort for diagnostics; start with `event-log-analysis` and visually inspect UI blockers.

For platform errors and auditing user actions, rely on `event-log-analysis`.

---

## When to use it

| Trigger | Action |
|---------|----------|
| Slow query — need to find the SQL | `search_tech_log` with `name: "DBMSSQL"` / `"DBPOSTGRS"` |
| Locks, deadlock | `search_tech_log` with `name: "TLOCK"` / `"TDEADLOCK"` / `"TTIMEOUT"` |
| Platform exception not in the event log | `search_tech_log` with `name: "EXCP"` |
| Long server call | `search_tech_log` with `name: "CALL"` or `"SCALL"` |
| Tech Log status unknown | `logc_get_techlog_config` — read the configuration |
| Tech Log is not active | Full cycle (see algorithm below) |

---

## Full diagnostics cycle

Unified procedure for passive (waiting for reproduction) and active (executing a test) modes.

**1. Save the configuration**
```
logc_save_techlog()
```
Returns `backup_id` — keep it for step 7.

**2. Configure events**
```
logc_configure_techlog(
  location: "/var/log/1c/techlog",
  history: 24,
  events: ["EXCP", "DBMSSQL", "TLOCK", "TDEADLOCK"]
)
```

**3. Ensure collection is running**
```
logc_get_actual_log_timestamp()
```

**4. Reproduce the issue / wait for reproduction**

**5. Smart polling for log readiness**

Up to 10 attempts with 3–5 second pauses: `logc_get_actual_log_timestamp() >= target_time`. If the log does not reach that point, inform the user and do not draw conclusions from an incomplete window.

**6. Read the entries**
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

**8. Confirm the status to the user**: `Tech Log restored` / `Tech Log disabled` / `Minimum profile (events: ...)`.

Steps 1 and 7 are **mandatory**. Never leave the Tech Log enabled after diagnostics without explicit approval.

---

### Variants of the cycle

**Tech Log already configured by an administrator** — read-only:
1. `logc_get_techlog_config` → ensure the required events are present.
2. `logc_get_actual_log_timestamp` → verify that collection is current.
3. `search_tech_log` → read the entries.
4. DO NOT modify the configuration — the Tech Log is not ours.

**Immediate shutdown** (disk filling): `logc_disable_techlog()`. After resolving the issue — `logc_restore_techlog(backup_id)`.

**Minimal monitoring** (instead of full shutdown, only by agreement): events ` ["EXCP", "CONN"]`. Record the active events and responsible person in the response.

---

## Tech Log events

| Event | When to use |
|---------|--------------------|
| `EXCP` | Errors not visible in the event log |
| `DBMSSQL` | Slow MS SQL queries |
| `DBPOSTGRS` | Slow PostgreSQL queries |
| `TLOCK` | Lock contention |
| `TDEADLOCK` | Deadlocks |
| `TTIMEOUT` | Lock timeouts |
| `CALL` / `SCALL` | Slow server calls |
| `CONN` | Connection issues |
| `SDBL` | Query translation to SQL |

**Standard set:** `["EXCP", "DBMSSQL", "TLOCK", "TDEADLOCK"]`

---

## Capabilities

| Capability | Purpose |
|------------|------------|
| `search_tech_log` | Search Tech Log entries |
| `logc_get_techlog_config` | Read the current Tech Log configuration |
| `logc_save_techlog` | Save the configuration before changes |
| `logc_configure_techlog` | Configure events, path, retention period |
| `logc_get_actual_log_timestamp` | Check that collection is active |
| `logc_restore_techlog` | Restore the saved configuration |
| `logc_disable_techlog` | Disable the Tech Log |
| `navigate_symbol` | Jump to code referenced in a Tech Log entry |

---

## Common mistakes

| Mistake | Workaround |
|--------|---------------|
| Forgot `logc_save_techlog` before configuring | Ask the user about the current configuration |
| Tech Log not active after `configure` | The platform requires service restart |
| `logc_get_actual_log_timestamp` does not update | Services not restarted or incorrect `location` |
| Too many events — disk fills up | Limit the event set; reduce `history` |
| `search_tech_log` returns nothing | Verify the time window; the event must occur after Tech Log activation |

---
depends_on: []
---
