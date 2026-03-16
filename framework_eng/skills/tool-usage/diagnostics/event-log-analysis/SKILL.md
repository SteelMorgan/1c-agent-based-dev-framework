---
name: event-log-analysis
description: Analysis of the 1С event log (Event Log). The skill teaches the agent to find errors, events, and user actions in the event log (ЖР) via ClickHouse.
---

# Event Log Analysis (Event Log)

## Purpose

The skill trains the agent to **search for and analyze events in the 1С event log** (ЖР). The event log records every significant platform event: errors, user actions, data changes, session start/stop.

**Principle:** The event log is a read-only source. Do not include raw entries in the output without masking personal data. Use `mode: "minimal"` by default.

For performance and technical event analysis (DB queries, locks, exceptions) use the separate skill [`tech-log-analysis`](../tech-log-analysis/SKILL.md).

---

## When to use

| Trigger | Action |
|---------|--------|
| User reports an error | First check the latest `Error` entries, then narrow the time window if needed |
| Need to find an error for a specific period | `search_event_log` with `from`/`to` |
| Audit of user actions | `search_event_log` with a filter by user |
| Checking changes of a specific object | `search_event_log` with a metadata filter |
| Tests are running — need to confirm the base is operational | `search_event_log` with `from: test_start_time`, `limit: 20` |
| Vanessa scenario tests failed | First check the latest `Error`, if empty — include latest entries without level filter |

---

## Use Cases

### Scenario 1: Finding errors over a period

**Steps:**

1. Obtain `cluster_guid` and `infobase_guid` from `cluster_map.yaml`.
2. Define the period: `from`/`to` in ISO 8601 format.
3. `search_event_log` with `level: "Error"`.
4. Analyze `records`: `event_time`, `event_presentation`, `comment`, `metadata_presentation`.
5. When a metadata object is mentioned — use `navigate_symbol` to jump to the code.

```
search_event_log(
  cluster_guid: "...",
  infobase_guid: "...",
  from: "2025-02-11T13:00:00Z",
  to:   "2025-02-11T14:00:00Z",
  level: "Error",
  mode: "minimal",
  limit: 100
)
```

### Scenario 2: Auditing user actions

**Steps:**

1. Get the GUIDs from `cluster_map.yaml`.
2. `search_event_log` with a filter by user and/or event (for example `_$Data$.Write`).
3. Analyze the sequence of events to spot suspicious or anomalous activity.

### Scenario 3: Assessing the base state after test execution

**Goal:** determine if the base is operational after tests start — check if events are flowing and whether there are errors.

**Steps:**

1. **Before the tests start** — remember the current time (`test_start_time` in ISO 8601).
2. After the run — `search_event_log` with `from: test_start_time`, `limit: 20`, no level filter.
3. Evaluate the results:
   - **No entries at all** → the base did not start or the event log is unavailable.
   - **Entries exist, no `Error`** → the base is alive and the tests are running.
   - **`Error` entries exist** → move to step 4.
4. When errors appear — repeat the query with `level: "Error"` for focused analysis.

```
# Шаг 2: общая картина (последние 20 событий с момента старта)
search_event_log(
  cluster_guid: "...",
  infobase_guid: "...",
  from: "2025-03-01T10:00:00Z",   # test_start_time
  mode: "minimal",
  limit: 20
)

# Шаг 4: только ошибки (если нужно)
search_event_log(
  cluster_guid: "...",
  infobase_guid: "...",
  from: "2025-03-01T10:00:00Z",
  level: "Error",
  mode: "minimal",
  limit: 20
)
```

**Interpretation of the latest 20 entries:**

| Situation | Conclusion |
|-----------|------------|
| No entries | The base did not start / the event log is unavailable |
| Entries exist and `Error` is absent | The base is alive, tests are running |
| The last event is a session termination without errors | Tests finished successfully |
| `Error` appears in `event_presentation` / `comment` | Tests failed — analyze `comment` and `metadata_presentation` |

### Scenario 4: Error “at 15:00 yesterday”

**Steps:**

1. Narrow the window to ±15 minutes from the reported time.
2. `search_event_log` with `level: "Error"` or without a level filter.
3. If no errors are found — expand the window or check the Technical Log via `tech-log-analysis`.

### Scenario 5: Diagnosing a Vanessa scenario run

**Goal:** quickly determine whether the event log holds any useful signal after an unsuccessful Vanessa run.

**Steps:**

1. First request the **latest entries at level `Error` without `from/to`**.
2. If none are found — repeat the query **without a level filter**.
3. Only if that still falls short — move to a narrow time window.
4. If the latest entries show a `Предупреждение безопасности` — proceed to visual diagnostics via GUI/screenshot.

**Important:** this order is more reliable for Vanessa than blindly tying to local execution time, since local time and ClickHouse can differ by timezone.

---

## Security and Personal Data

| Rule | Description |
|------|-------------|
| **Masking** | The event log contains user full names, INN, and contact details. Do not include raw entries in the output without masking. |
| **mode: minimal** | Default to `mode: "minimal"` — fewer fields, lower risk. |
| **Narrow window** | Restrict `from`/`to` to the minimally required period. |
| **Limit** | Do not request more than 1000 entries per query during initial investigation. |
| **Timezone drift** | ClickHouse and local time may drift; first inspect the latest records without a time filter. |

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `search_event_log` | Search the event log (via ClickHouse) |
| `navigate_symbol` | Jump to code by metadata referenced in the event log entry |

---

## Common Mistakes

| Issue | Workaround |
|-------|--------------|
| GUIDs not taken from `cluster_map.yaml` | Read the config file; if it is missing — request it from the user |
| ClickHouse is unavailable | Record the reason; suggest the Technical Log as an alternative |
| Window too wide — timeout | Narrow to 15–30 minutes; reduce `limit` |
| No errors in the event log | Repeat the search without a level filter; only after that narrow the time window or move to the Technical Log |
