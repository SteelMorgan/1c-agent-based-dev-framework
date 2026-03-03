---
name: tech-log-analysis
description: Working with the 1С Tech Log (Tech Log). The skill teaches the agent to manage the full lifecycle of the Tech Log — configuration, enabling, collection, analysis, restoration — and to diagnose technical issues such as slow queries, locks, and exceptions.
---

# Working with the 1С Tech Log (Tech Log)

## Purpose

The skill teaches the agent to **manage the full lifecycle of the Tech Log (TL)** and analyze its contents. The Tech Log is a low-level 1С platform tool for performance diagnostics: slow database queries, locks, platform-level exceptions, and interactions with external systems.

**Principle:** The Tech Log stresses the system. Enable it selectively with the minimally necessary set of events. Always restore the original configuration after diagnostics.

For debugging errors and auditing user actions, see the separate skill [`event-log-analysis`](../event-log-analysis/SKILL.md).

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

**Rule:** steps 1 and 6 are mandatory. Never leave the Tech Log enabled after diagnostics.

---

## When to use

| Trigger | Action |
|---------|--------|
| Slow query — need to find SQL | `search_tech_log` with `name: "DBMSSQL"` / `"DBPOSTGRS"` |
| Locks, deadlock | `search_tech_log` with `name: "TLOCK"` / `"TDEADLOCK"` / `"TTIMEOUT"` |
| Platform exception not in ЖР | `search_tech_log` with `name: "EXCP"` |
| Long server call | `search_tech_log` with `name: "CALL"` or `"SCALL"` |
| Status of Tech Log unknown before analysis | `logc_get_techlog_config` — read the current configuration |
| Tech Log is not collecting | Full lifecycle: save → configure → check → analyze → restore |

---

## Usage scenarios

### Scenario 1: Full diagnostic cycle (main)

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
If the timestamp is updating — the Tech Log is active.

**5. Reproduce the issue** (the user performs an action in 1С)

**6. Read the entries**
```
search_tech_log(
  from: "2025-02-11T14:00:00Z",
  to:   "2025-02-11T14:15:00Z",
  name: "DBMSSQL",
  min_duration: 1000
)
```

**7. Restore the original configuration**
```
logc_restore_techlog(backup_id: "...")
```

---

### Scenario 2: Lock analysis

**Steps:**

1. Full cycle (Scenario 1), events: `["TLOCK", "TDEADLOCK", "TTIMEOUT"]`.
2. `search_tech_log` with `name: "TLOCK"` — find blocking and blocked sessions.
3. Analyze `records`: `context`, `message`, `timestamp`, `session`.
4. Correlate with code via `navigate_symbol` using the call context from the entry.
5. `logc_restore_techlog`.

---

### Scenario 3: Tech Log already configured — read only

If the Tech Log is already running (for example, configured by an administrator):

1. `logc_get_techlog_config` — make sure the required events are present.
2. `logc_get_actual_log_timestamp` — ensure the collection is up to date.
3. `search_tech_log` — read the entries.
4. **Do not change the configuration**, do not restore it — the Tech Log is not ours.

---

### Scenario 4: Quick Tech Log disable

If you need to disable it urgently (for example, the Tech Log is filling the disk):

```
logc_disable_techlog()
```

After resolving the issue — `logc_restore_techlog(backup_id)` if a backup was saved.

---

## Tech Log events — reference

| Event | Description | When to use |
|-------|-------------|-------------|
| `EXCP` | Platform exceptions | Errors not visible in ЖР |
| `DBMSSQL` | Queries to MS SQL | Slow queries, SQL plan |
| `DBPOSTGRS` | Queries to PostgreSQL | Slow queries |
| `TLOCK` | Managed locks | Lock conflicts |
| `TDEADLOCK` | Deadlocks | Deadlock situations |
| `TTIMEOUT` | Lock timeouts | Hangs during locking |
| `CALL` | Server calls | Slow server procedures |
| `SCALL` | System calls | Internal platform calls |
| `CONN` | Connections | Connection issues |
| `SDBL` | Query language queries | Translation to SQL |

**Standard set to start diagnostics:** `["EXCP", "DBMSSQL", "TLOCK", "TDEADLOCK"]`

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `search_tech_log` | Search Tech Log entries |
| `logc_get_techlog_config` | Read the current Tech Log configuration |
| `logc_save_techlog` | Save the configuration before changing it |
| `logc_configure_techlog` | Configure events, path, retention period |
| `logc_get_actual_log_timestamp` | Check that collection is running |
| `logc_restore_techlog` | Restore the saved configuration |
| `logc_disable_techlog` | Disable the Tech Log |
| `navigate_symbol` | Jump to code based on the context from a Tech Log entry |

---

## Common mistakes

| Mistake | Workaround |
|---------|------------|
| Forgot `logc_save_techlog` before configuring | Ask the user about the current configuration; document the change |
| Tech Log not active after `configure` | The platform requires a service restart; warn the user |
| `logc_get_actual_log_timestamp` does not update | Service not restarted or incorrect `location` |
| Too many events — disk fills up quickly | Limit the event set; decrease `history` |
| Forgot to restore the configuration | Always finish the cycle with `logc_restore_techlog` |
| `search_tech_log` returns nothing | Check the time window; ensure the event occurred after the Tech Log was enabled |

---
depends_on: []
---
