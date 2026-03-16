---
name: tech-log-analysis
description: Working with the 1С Tech Log (Tech Log). The skill teaches the agent to manage the full lifecycle of the Tech Log — configuring, enabling, collecting, analyzing, restoring — and to diagnose technical problems such as slow queries, locks, and exceptions.
---

# Working with the 1С Tech Log (Tech Log)

## Purpose

The skill teaches the agent to **manage the full lifecycle of the Tech Log (TL)** and analyze its contents. The TL is a low-level tool of the 1С platform for performance diagnostics: slow DB queries, locks, platform-level exceptions, interaction with external systems.

**Principle:** The Tech Log loads the system. Enable it sparingly, with the minimum required set of events. Always restore the original configuration after diagnostics.

For Vanessa Automation scenario tests, the Tech Log is not the first diagnostic source. First rely on the event log and the visual diagnosis of UI blockers.

For error hunting and auditing user actions, use the dedicated skill [`event-log-analysis`](../event-log-analysis/SKILL.md).

---

## Tech Log full lifecycle

```
[1. Save the current configuration]
        ↓
[2. Configure the required events]
        ↓
[3. Make sure collection is running]
        ↓
[4. Reproduce the problem]
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
| Slow query — need to find SQL | `search_tech_log` with `name: "DBMSSQL"` / `"DBPOSTGRS"` |
| Locks, deadlock | `search_tech_log` with `name: "TLOCK"` / `"TDEADLOCK"` / `"TTIMEOUT"` |
| Platform exception not in the event log | `search_tech_log` with `name: "EXCP"` |
| Long server call | `search_tech_log` with `name: "CALL"` or `"SCALL"` |
| Before analysis — Tech Log status unknown | `logc_get_techlog_config` — read the current configuration |
| Tech Log not running | Full cycle: save → configure → verify → analyze → restore |
| Vanessa scenario run yielded no answer via the event log | Use the Tech Log only after `event-log-analysis` and a visual check |

---

## Usage scenarios

### Operating modes: passive and active

- **Passive mode:** enable the Tech Log and wait for the user to reproduce the issue naturally.
- **Active mode:** enable the Tech Log and immediately run the reproducing scenario (e.g., an automated test or manual walkthrough).

In both modes, the cycle is the same: save → configure → reproduce/wait → read → restore.

### Scenario 1: Full diagnostic cycle (primary)

**Steps:**

**1. Save the current configuration**
```
logc_save_techlog()
```
Returns a `backup_id` — keep it for step 6.

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

**4. Confirm that collection is running**
```
logc_get_actual_log_timestamp()
```
If the timestamp advances — the Tech Log is active.

**5. Reproduce the problem** (the user performs the action in 1С)

**6. Wait for the log to be ready to read (smart polling)**

After reproducing the event, data might not appear instantly. Use a limited readiness check loop:

```bsl
// Pseudocode
TargetTime = <problem_reproduction_time>;

For Attempt = 1 To 10 Do
    ActualTime = logc_get_actual_log_timestamp();
    If ActualTime >= TargetTime Then
        Break;
    EndIf;

    // Short pause between checks
    // (for example, 3-5 sec; not an infinite loop)
EndDo;
```

If the timestamp does not reach the target after the attempts limit — inform the user about the parsing/delivery issue and avoid drawing conclusions from an incomplete window.

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

- Confirm that the original configuration is indeed restored.
- Clearly report the final status to the user:
  - `Tech Log restored to its original state`, or
  - `Tech Log disabled` (if it was not configured initially).
- If further investigation is required — note which mode is currently active and who is responsible for the next disable/restore action.

This reduces the risk of leaving the Tech Log enabled for too long.

---

### Scenario 1.1: Minimal safe mode (if you cannot fully disable)

Sometimes you need to leave basic monitoring enabled after diagnostics. In this case, instead of completely disabling, enable a **minimal set of infrequent events**, for example:

- `EXCP` — platform exceptions
- `CONN` — connection events
- `PROC` — system processes (if available in your environment)

Rules:
1. The minimal profile must be agreed with the user/administrator.
2. Clearly state in the response that a short/minimal log remains active and list the active events.
3. Record who decided to keep the minimal profile and who will maintain it going forward.

For most cases, prefer a full return via `logc_restore_techlog`.

---

### Scenario 2: Lock analysis

**Steps:**

1. Full cycle (Scenario 1), events: `["TLOCK", "TDEADLOCK", "TTIMEOUT"]`.
2. `search_tech_log` with `name: "TLOCK"` — find blocking and blocked sessions.
3. Analyze `records`: `context`, `message`, `timestamp`, `session`.
4. Correlate with code via `navigate_symbol` from the call context.
5. `logc_restore_techlog`.

---

### Scenario 3: Tech Log already configured — read only

If the Tech Log is already running (for example, configured by an administrator):

1. `logc_get_techlog_config` — confirm the required events are enabled.
2. `logc_get_actual_log_timestamp` — ensure collection is up to date.
3. `search_tech_log` — read the entries.
4. **Do not change the configuration**, do not restore — the Tech Log is not ours.

---

### Scenario 4: Quick Tech Log disable

If you urgently need to disable it (for example, the Tech Log is filling the disk):

```
logc_disable_techlog()
```

After the issue is resolved — `logc_restore_techlog(backup_id)` if a backup was saved.

---

### Scenario 5: Active diagnostics via a reproducing test

Suitable when there is a stable automated test/manual scenario that reproduces the problem.

1. Save the current configuration (`logc_save_techlog`).
2. Enable the Tech Log for specific events.
3. Run the reproducing scenario.
4. Perform smart polling using `logc_get_actual_log_timestamp`.
5. Read `search_tech_log` in a narrow time window around the test.
6. Restore the configuration (`logc_restore_techlog`).
7. Explicitly confirm the final Tech Log status to the user.

The benefit: less noise in the log and easier mapping of entries to a specific test step.

---

## Tech Log events reference

| Event | Description | When to use |
|-------|-------------|-------------|
| `EXCP` | Platform exceptions | Errors not visible in the event log |
| `DBMSSQL` | MS SQL queries | Slow queries, SQL plan analysis |
| `DBPOSTGRS` | PostgreSQL queries | Slow queries |
| `TLOCK` | Managed locks | Lock conflicts |
| `TDEADLOCK` | Deadlocks | Deadlock situations |
| `TTIMEOUT` | Lock timeouts | Hang-ups during locking |
| `CALL` | Server calls | Slow server procedures |
| `SCALL` | System calls | Internal platform calls |
| `CONN` | Connections | Connection issues |
| `SDBL` | Query language requests | Translation to SQL |

**Standard starting set for diagnostics:** `["EXCP", "DBMSSQL", "TLOCK", "TDEADLOCK"]`

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `search_tech_log` | Search Tech Log entries |
| `logc_get_techlog_config` | Read the current Tech Log configuration |
| `logc_save_techlog` | Save configuration before making changes |
| `logc_configure_techlog` | Configure events, path, retention |
| `logc_get_actual_log_timestamp` | Check that collection is running |
| `logc_restore_techlog` | Restore the saved configuration |
| `logc_disable_techlog` | Disable the Tech Log |
| `navigate_symbol` | Jump to code via the Tech Log context |

---

## Post-analysis checklist (mandatory)

1. Analysis is complete and findings are documented.
2. Tech Log is returned to the expected state:
   - either restored via `logc_restore_techlog(backup_id)`,
   - or disabled via `logc_disable_techlog()` (if agreed).
3. The user has been clearly told the final Tech Log status.
4. If minimal monitoring remains, the duration and responsible person are recorded.

Template message to the user:
- `✅ Analysis complete. Tech Log restored to its original state.`
- `✅ Analysis complete. Tech Log disabled.`
- `✅ Analysis complete. Minimal profile enabled (events: <list>).`
- `✅ Analysis complete. Minimal profile enabled; decision coordinated with <who>.`

---

## Common mistakes

| Mistake | Workaround |
|---------|------------|
| Forgot `logc_save_techlog` before configuring | Ask the user for the current configuration; document the change |
| Tech Log not active after `configure` | The platform may require restarting services; notify the user |
| `logc_get_actual_log_timestamp` does not update | Service not restarted or incorrect `location` |
| Too many events — disk fills quickly | Limit the event set; reduce `history` |
| Forgot to restore the configuration | Always finish the cycle with `logc_restore_techlog` |
| `search_tech_log` returns empty | Check the time window; ensure the event happened after enabling the Tech Log |

---
depends_on: []
---
